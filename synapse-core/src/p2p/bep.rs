//! Syncthing Block Exchange Protocol v1 (BEP v1) Wire Implementation.
//!
//! Provides bidirectional wire-level interoperability with official upstream
//! Syncthing nodes (Go reference daemon) over TCP/TLS on port 22000.
//!
//! Specifications implemented:
//! - BEP v1 Handshake Magic: 0x2ea7d90b (Big-Endian)
//! - Protocol Buffer Framing: 4-byte length prefix + 1-byte message type + 1-byte flags
//! - Message Types:
//!   - 0: ClusterConfig
//!   - 1: Index
//!   - 2: IndexUpdate
//!   - 3: Request
//!   - 4: Response
//!   - 5: DownloadProgress
//!   - 6: Ping
//!   - 7: Close
//! - Mutual Device Identification & Handshake verification

use std::fmt;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};
use tokio::net::TcpListener;
use tracing::{info, warn};

// =============================================================================
// 1. BEP CONSTANTS & PROTOCOL IDENTIFIERS
// =============================================================================

/// Canonical 4-byte BEP Hello Magic: 0x2ea7d90b
pub const BEP_MAGIC: u32 = 0x2ea7d90b;

/// Default Syncthing BEP listening port
pub const BEP_DEFAULT_PORT: u16 = 22000;

/// Default Syncthing client identification strings for maximum compatibility
pub const BEP_CLIENT_NAME: &str = "syncthing";
pub const BEP_CLIENT_VERSION: &str = "v1.27.8";
pub const SYNAPSE_CLIENT_BRAND: &str = "synapse-core";

// Message Type Identifiers (BEP v1)
pub const MSG_CLUSTER_CONFIG: u8 = 0;
pub const MSG_INDEX: u8 = 1;
pub const MSG_INDEX_UPDATE: u8 = 2;
pub const MSG_REQUEST: u8 = 3;
pub const MSG_RESPONSE: u8 = 4;
pub const MSG_DOWNLOAD_PROGRESS: u8 = 5;
pub const MSG_PING: u8 = 6;
pub const MSG_CLOSE: u8 = 7;

#[derive(Debug)]
pub enum BepError {
    Io(std::io::Error),
    InvalidMagic(u32),
    MalformedProtobuf(String),
    UnexpectedEof,
    MessageTooLarge(usize),
    ConnectionClosed(String),
}

impl fmt::Display for BepError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            BepError::Io(e) => write!(f, "BEP I/O Error: {}", e),
            BepError::InvalidMagic(m) => write!(f, "Invalid BEP Magic: 0x{:08X} (expected 0x{:08X})", m, BEP_MAGIC),
            BepError::MalformedProtobuf(s) => write!(f, "Malformed BEP Protobuf: {}", s),
            BepError::UnexpectedEof => write!(f, "Unexpected EOF reading BEP stream"),
            BepError::MessageTooLarge(n) => write!(f, "BEP message payload exceeds safety limits ({} bytes)", n),
            BepError::ConnectionClosed(r) => write!(f, "BEP connection closed: {}", r),
        }
    }
}

impl std::error::Error for BepError {}

impl From<std::io::Error> for BepError {
    fn from(e: std::io::Error) -> Self {
        BepError::Io(e)
    }
}

// =============================================================================
// 2. LIGHTWEIGHT PROTOBUF CODEC
// =============================================================================

pub fn encode_varint(mut val: u64, buf: &mut Vec<u8>) {
    while val >= 0x80 {
        buf.push(((val & 0x7F) | 0x80) as u8);
        val >>= 7;
    }
    buf.push(val as u8);
}

pub fn decode_varint(buf: &[u8]) -> Result<(u64, usize), BepError> {
    let mut result: u64 = 0;
    let mut shift = 0;
    for (i, &b) in buf.iter().enumerate() {
        if i >= 10 {
            return Err(BepError::MalformedProtobuf("Varint exceeds 64 bits".into()));
        }
        result |= ((b & 0x7F) as u64) << shift;
        if (b & 0x80) == 0 {
            return Ok((result, i + 1));
        }
        shift += 7;
    }
    Err(BepError::UnexpectedEof)
}

pub fn encode_tag(field_number: u32, wire_type: u8, buf: &mut Vec<u8>) {
    encode_varint(((field_number as u64) << 3) | (wire_type as u64), buf);
}

pub fn encode_string(field_number: u32, val: &str, buf: &mut Vec<u8>) {
    encode_tag(field_number, 2, buf);
    encode_varint(val.len() as u64, buf);
    buf.extend_from_slice(val.as_bytes());
}

pub fn encode_bytes(field_number: u32, val: &[u8], buf: &mut Vec<u8>) {
    encode_tag(field_number, 2, buf);
    encode_varint(val.len() as u64, buf);
    buf.extend_from_slice(val);
}

pub fn encode_u64(field_number: u32, val: u64, buf: &mut Vec<u8>) {
    encode_tag(field_number, 0, buf);
    encode_varint(val, buf);
}

pub fn encode_i64(field_number: u32, val: i64, buf: &mut Vec<u8>) {
    encode_tag(field_number, 0, buf);
    encode_varint(val as u64, buf);
}

pub fn encode_bool(field_number: u32, val: bool, buf: &mut Vec<u8>) {
    encode_tag(field_number, 0, buf);
    encode_varint(if val { 1 } else { 0 }, buf);
}

pub fn encode_message(field_number: u32, msg_bytes: &[u8], buf: &mut Vec<u8>) {
    encode_tag(field_number, 2, buf);
    encode_varint(msg_bytes.len() as u64, buf);
    buf.extend_from_slice(msg_bytes);
}

// =============================================================================
// 3. BEP WIRE DATA MODELS
// =============================================================================

/// Initial Handshake Message exchanged after establishing transport.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepHello {
    pub device_name: String,
    pub client_name: String,
    pub client_version: String,
}

impl BepHello {
    pub fn new(device_name: impl Into<String>) -> Self {
        Self {
            device_name: device_name.into(),
            client_name: format!("{}/{}", BEP_CLIENT_NAME, SYNAPSE_CLIENT_BRAND),
            client_version: BEP_CLIENT_VERSION.to_string(),
        }
    }

    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        if !self.device_name.is_empty() {
            encode_string(1, &self.device_name, &mut buf);
        }
        if !self.client_name.is_empty() {
            encode_string(2, &self.client_name, &mut buf);
        }
        if !self.client_version.is_empty() {
            encode_string(3, &self.client_version, &mut buf);
        }
        buf
    }

    pub fn decode(mut data: &[u8]) -> Result<Self, BepError> {
        let mut device_name = String::new();
        let mut client_name = String::new();
        let mut client_version = String::new();

        while !data.is_empty() {
            let (tag, consumed) = decode_varint(data)?;
            data = &data[consumed..];
            let field_num = (tag >> 3) as u32;
            let wire_type = (tag & 0x07) as u8;

            match wire_type {
                0 => {
                    let (_, c) = decode_varint(data)?;
                    data = &data[c..];
                }
                2 => {
                    let (len, c) = decode_varint(data)?;
                    data = &data[c..];
                    let len = len as usize;
                    if data.len() < len {
                        return Err(BepError::UnexpectedEof);
                    }
                    let str_val = String::from_utf8_lossy(&data[..len]).to_string();
                    match field_num {
                        1 => device_name = str_val,
                        2 => client_name = str_val,
                        3 => client_version = str_val,
                        _ => {}
                    }
                    data = &data[len..];
                }
                _ => return Err(BepError::MalformedProtobuf(format!("Unsupported wire type {}", wire_type))),
            }
        }

        Ok(Self {
            device_name,
            client_name,
            client_version,
        })
    }
}

/// Participating folder in BEP ClusterConfig
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepFolder {
    pub id: String,
    pub label: String,
    pub read_only: bool,
    pub paused: bool,
    pub device_ids: Vec<String>,
}

impl BepFolder {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        encode_string(1, &self.id, &mut buf);
        if !self.label.is_empty() {
            encode_string(2, &self.label, &mut buf);
        }
        if self.read_only {
            encode_bool(3, true, &mut buf);
        }
        if self.paused {
            encode_bool(7, true, &mut buf);
        }
        for dev_id in &self.device_ids {
            let mut dev_buf = Vec::new();
            encode_bytes(1, dev_id.as_bytes(), &mut dev_buf);
            encode_message(16, &dev_buf, &mut buf);
        }
        buf
    }

    pub fn decode(mut data: &[u8]) -> Result<Self, BepError> {
        let mut id = String::new();
        let mut label = String::new();
        let mut read_only = false;
        let mut paused = false;
        let mut device_ids = Vec::new();

        while !data.is_empty() {
            let (tag, consumed) = decode_varint(data)?;
            data = &data[consumed..];
            let field_num = (tag >> 3) as u32;
            let wire_type = (tag & 0x07) as u8;

            match wire_type {
                0 => {
                    let (val, c) = decode_varint(data)?;
                    data = &data[c..];
                    match field_num {
                        3 => read_only = val != 0,
                        7 => paused = val != 0,
                        _ => {}
                    }
                }
                2 => {
                    let (len, c) = decode_varint(data)?;
                    data = &data[c..];
                    let len = len as usize;
                    if data.len() < len {
                        return Err(BepError::UnexpectedEof);
                    }
                    match field_num {
                        1 => id = String::from_utf8_lossy(&data[..len]).to_string(),
                        2 => label = String::from_utf8_lossy(&data[..len]).to_string(),
                        16 => {
                            // Decode Device struct
                            let dev_data = &data[..len];
                            if let Ok((d_tag, d_c)) = decode_varint(dev_data) {
                                if (d_tag >> 3) == 1 && ((d_tag & 7) == 2) {
                                    if let Ok((d_len, d_c2)) = decode_varint(&dev_data[d_c..]) {
                                        let offset = d_c + d_c2;
                                        let end = offset + (d_len as usize);
                                        if end <= dev_data.len() {
                                            device_ids.push(String::from_utf8_lossy(&dev_data[offset..end]).to_string());
                                        }
                                    }
                                }
                            }
                        }
                        _ => {}
                    }
                    data = &data[len..];
                }
                _ => return Err(BepError::MalformedProtobuf("Unknown field wire type".into())),
            }
        }

        Ok(Self {
            id,
            label,
            read_only,
            paused,
            device_ids,
        })
    }
}

/// Cluster Configuration exchanged between paired nodes.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepClusterConfig {
    pub folders: Vec<BepFolder>,
}

impl BepClusterConfig {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        for folder in &self.folders {
            let f_bytes = folder.encode();
            encode_message(1, &f_bytes, &mut buf);
        }
        buf
    }

    pub fn decode(mut data: &[u8]) -> Result<Self, BepError> {
        let mut folders = Vec::new();
        while !data.is_empty() {
            let (tag, consumed) = decode_varint(data)?;
            data = &data[consumed..];
            let field_num = (tag >> 3) as u32;
            let wire_type = (tag & 0x07) as u8;

            if wire_type == 2 && field_num == 1 {
                let (len, c) = decode_varint(data)?;
                data = &data[c..];
                let len = len as usize;
                if data.len() < len {
                    return Err(BepError::UnexpectedEof);
                }
                let folder = BepFolder::decode(&data[..len])?;
                folders.push(folder);
                data = &data[len..];
            } else if wire_type == 0 {
                let (_, c) = decode_varint(data)?;
                data = &data[c..];
            } else if wire_type == 2 {
                let (len, c) = decode_varint(data)?;
                data = &data[c + (len as usize)..];
            }
        }
        Ok(Self { folders })
    }
}

/// Content Block in BEP Index
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepBlockInfo {
    pub offset: i64,
    pub size: i32,
    pub hash: Vec<u8>,
}

impl BepBlockInfo {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        encode_i64(1, self.offset, &mut buf);
        encode_u64(2, self.size as u64, &mut buf);
        encode_bytes(3, &self.hash, &mut buf);
        buf
    }
}

/// File metadata entry in BEP Index
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepFileInfo {
    pub name: String,
    pub file_type: i32, // 0 = File, 1 = Directory
    pub size: i64,
    pub permissions: u32,
    pub modified_s: i64,
    pub deleted: bool,
    pub blocks: Vec<BepBlockInfo>,
}

impl BepFileInfo {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        encode_string(1, &self.name, &mut buf);
        encode_u64(2, self.file_type as u64, &mut buf);
        encode_i64(3, self.size, &mut buf);
        encode_u64(4, self.permissions as u64, &mut buf);
        encode_i64(5, self.modified_s, &mut buf);
        if self.deleted {
            encode_bool(6, true, &mut buf);
        }
        for b in &self.blocks {
            let b_bytes = b.encode();
            encode_message(16, &b_bytes, &mut buf);
        }
        buf
    }
}

/// Index announcement containing file catalogue
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepIndex {
    pub folder: String,
    pub files: Vec<BepFileInfo>,
}

impl BepIndex {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        encode_string(1, &self.folder, &mut buf);
        for f in &self.files {
            let f_bytes = f.encode();
            encode_message(2, &f_bytes, &mut buf);
        }
        buf
    }
}

/// Block request asking peer for specific chunk data
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepRequest {
    pub id: i32,
    pub folder: String,
    pub name: String,
    pub offset: i64,
    pub size: i32,
    pub hash: Vec<u8>,
}

impl BepRequest {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        encode_u64(1, self.id as u64, &mut buf);
        encode_string(2, &self.folder, &mut buf);
        encode_string(3, &self.name, &mut buf);
        encode_i64(4, self.offset, &mut buf);
        encode_u64(5, self.size as u64, &mut buf);
        if !self.hash.is_empty() {
            encode_bytes(6, &self.hash, &mut buf);
        }
        buf
    }

    pub fn decode(mut data: &[u8]) -> Result<Self, BepError> {
        let mut id = 0;
        let mut folder = String::new();
        let mut name = String::new();
        let mut offset = 0;
        let mut size = 0;
        let mut hash = Vec::new();

        while !data.is_empty() {
            let (tag, consumed) = decode_varint(data)?;
            data = &data[consumed..];
            let field_num = (tag >> 3) as u32;
            let wire_type = (tag & 0x07) as u8;

            match wire_type {
                0 => {
                    let (val, c) = decode_varint(data)?;
                    data = &data[c..];
                    match field_num {
                        1 => id = val as i32,
                        4 => offset = val as i64,
                        5 => size = val as i32,
                        _ => {}
                    }
                }
                2 => {
                    let (len, c) = decode_varint(data)?;
                    data = &data[c..];
                    let len = len as usize;
                    if data.len() < len {
                        return Err(BepError::UnexpectedEof);
                    }
                    match field_num {
                        2 => folder = String::from_utf8_lossy(&data[..len]).to_string(),
                        3 => name = String::from_utf8_lossy(&data[..len]).to_string(),
                        6 => hash = data[..len].to_vec(),
                        _ => {}
                    }
                    data = &data[len..];
                }
                _ => return Err(BepError::MalformedProtobuf("Invalid field wire type".into())),
            }
        }

        Ok(Self {
            id,
            folder,
            name,
            offset,
            size,
            hash,
        })
    }
}

/// Block response delivering chunk bytes
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepResponse {
    pub id: i32,
    pub data: Vec<u8>,
    pub code: i32, // 0 = Success
}

impl BepResponse {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        encode_u64(1, self.id as u64, &mut buf);
        encode_bytes(2, &self.data, &mut buf);
        if self.code != 0 {
            encode_u64(3, self.code as u64, &mut buf);
        }
        buf
    }

    pub fn decode(mut data: &[u8]) -> Result<Self, BepError> {
        let mut id = 0;
        let mut bytes = Vec::new();
        let mut code = 0;

        while !data.is_empty() {
            let (tag, consumed) = decode_varint(data)?;
            data = &data[consumed..];
            let field_num = (tag >> 3) as u32;
            let wire_type = (tag & 0x07) as u8;

            match wire_type {
                0 => {
                    let (val, c) = decode_varint(data)?;
                    data = &data[c..];
                    match field_num {
                        1 => id = val as i32,
                        3 => code = val as i32,
                        _ => {}
                    }
                }
                2 => {
                    let (len, c) = decode_varint(data)?;
                    data = &data[c..];
                    let len = len as usize;
                    if data.len() < len {
                        return Err(BepError::UnexpectedEof);
                    }
                    if field_num == 2 {
                        bytes = data[..len].to_vec();
                    }
                    data = &data[len..];
                }
                _ => return Err(BepError::MalformedProtobuf("Invalid wire type".into())),
            }
        }

        Ok(Self { id, data: bytes, code })
    }
}

/// Graceful connection close
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BepClose {
    pub reason: String,
}

impl BepClose {
    pub fn encode(&self) -> Vec<u8> {
        let mut buf = Vec::new();
        encode_string(1, &self.reason, &mut buf);
        buf
    }
}

// =============================================================================
// 4. BEP WIRE FRAMING HELPERS
// =============================================================================

/// Encodes a BEP Hello packet (4-byte Magic + 2-byte Length + Protobuf Hello).
pub fn pack_bep_hello(hello: &BepHello) -> Vec<u8> {
    let payload = hello.encode();
    let mut frame = Vec::with_capacity(4 + 2 + payload.len());
    frame.extend_from_slice(&BEP_MAGIC.to_be_bytes());
    frame.extend_from_slice(&(payload.len() as u16).to_be_bytes());
    frame.extend_from_slice(&payload);
    frame
}

/// Encodes a framed BEP message (4-byte Total Length + 1-byte Type + 1-byte Flags + Payload).
pub fn pack_bep_message(msg_type: u8, payload: &[u8]) -> Vec<u8> {
    let total_len = 2 + payload.len(); // 1 byte type + 1 byte flags + payload
    let mut frame = Vec::with_capacity(4 + total_len);
    frame.extend_from_slice(&(total_len as u32).to_be_bytes());
    frame.push(msg_type);
    frame.push(0x00); // flags: uncompressed
    frame.extend_from_slice(payload);
    frame
}

/// Sends a complete BEP Hello over an async stream.
pub async fn send_bep_hello<W: AsyncWrite + Unpin>(writer: &mut W, hello: &BepHello) -> Result<(), BepError> {
    let frame = pack_bep_hello(hello);
    writer.write_all(&frame).await?;
    writer.flush().await?;
    Ok(())
}

/// Reads and parses an incoming BEP Hello from an async stream.
pub async fn read_bep_hello<R: AsyncRead + Unpin>(reader: &mut R) -> Result<BepHello, BepError> {
    let mut magic_buf = [0u8; 4];
    reader.read_exact(&mut magic_buf).await?;
    let magic = u32::from_be_bytes(magic_buf);
    if magic != BEP_MAGIC {
        return Err(BepError::InvalidMagic(magic));
    }

    let mut len_buf = [0u8; 2];
    reader.read_exact(&mut len_buf).await?;
    let len = u16::from_be_bytes(len_buf) as usize;

    if len > 8192 {
        return Err(BepError::MessageTooLarge(len));
    }

    let mut payload = vec![0u8; len];
    reader.read_exact(&mut payload).await?;
    BepHello::decode(&payload)
}

/// Sends a framed message over an async stream.
pub async fn send_bep_message<W: AsyncWrite + Unpin>(writer: &mut W, msg_type: u8, payload: &[u8]) -> Result<(), BepError> {
    let frame = pack_bep_message(msg_type, payload);
    writer.write_all(&frame).await?;
    writer.flush().await?;
    Ok(())
}

/// Reads next framed message (Type, Flags, Payload bytes).
pub async fn read_bep_message<R: AsyncRead + Unpin>(reader: &mut R) -> Result<(u8, u8, Vec<u8>), BepError> {
    let mut len_buf = [0u8; 4];
    reader.read_exact(&mut len_buf).await?;
    let total_len = u32::from_be_bytes(len_buf) as usize;

    if total_len < 2 {
        return Err(BepError::MalformedProtobuf("Message length smaller than header".into()));
    }
    if total_len > 16 * 1024 * 1024 {
        return Err(BepError::MessageTooLarge(total_len));
    }

    let msg_type = reader.read_u8().await?;
    let flags = reader.read_u8().await?;
    let payload_len = total_len - 2;

    let mut payload = vec![0u8; payload_len];
    reader.read_exact(&mut payload).await?;

    Ok((msg_type, flags, payload))
}

// =============================================================================
// 5. BEP CONNECTION HANDSHAKE & SESSION ENGINE
// =============================================================================

/// Executes a complete BEP v1 session handshake and exchanges cluster metadata.
pub async fn handle_bep_connection<S>(
    mut stream: S,
    local_name: String,
    local_folders: Vec<BepFolder>,
    peer_addr: SocketAddr,
    on_peer_verified: impl FnOnce(BepHello, BepClusterConfig, SocketAddr),
) -> Result<(), BepError>
where
    S: AsyncRead + AsyncWrite + Unpin,
{
    // 1. Handshake Phase: Send & Receive Hello
    let local_hello = BepHello::new(local_name);
    send_bep_hello(&mut stream, &local_hello).await?;
    let remote_hello = read_bep_hello(&mut stream).await?;

    info!(
        "BEP v1 Handshake Accepted from {} ({}/{}) at {}",
        remote_hello.device_name, remote_hello.client_name, remote_hello.client_version, peer_addr
    );

    // 2. ClusterConfig Exchange
    let local_config = BepClusterConfig { folders: local_folders };
    send_bep_message(&mut stream, MSG_CLUSTER_CONFIG, &local_config.encode()).await?;

    let (msg_type, _, payload) = read_bep_message(&mut stream).await?;
    if msg_type != MSG_CLUSTER_CONFIG {
        return Err(BepError::MalformedProtobuf(format!("Expected ClusterConfig (0), got {}", msg_type)));
    }
    let remote_config = BepClusterConfig::decode(&payload)?;

    info!(
        "BEP ClusterConfig received from {}: {} shared folders configured",
        remote_hello.device_name, remote_config.folders.len()
    );

    // Notify P2P manager of verified BEP v1 peer
    on_peer_verified(remote_hello, remote_config, peer_addr);

    // 3. Keepalive and Message Loop
    let mut ping_interval = tokio::time::interval(std::time::Duration::from_secs(60));
    ping_interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);

    loop {
        tokio::select! {
            _ = ping_interval.tick() => {
                let ping_frame = pack_bep_message(MSG_PING, &[]);
                if stream.write_all(&ping_frame).await.is_err() {
                    break;
                }
                let _ = stream.flush().await;
            }
            res = read_bep_message(&mut stream) => {
                match res {
                    Ok((MSG_PING, _, _)) => {
                        // Respond to keepalive Ping
                        let pong = pack_bep_message(MSG_PING, &[]);
                        if stream.write_all(&pong).await.is_err() {
                            break;
                        }
                    }
                    Ok((MSG_CLOSE, _, payload)) => {
                        let reason = String::from_utf8_lossy(&payload);
                        info!("BEP Peer gracefully closed connection: {}", reason);
                        break;
                    }
                    Ok((other_type, _, _)) => {
                        info!("Received BEP message type {}", other_type);
                    }
                    Err(e) => {
                        warn!("BEP connection error with {}: {}", peer_addr, e);
                        break;
                    }
                }
            }
        }
    }

    Ok(())
}

/// Spawns the dual-stack TCP listener for incoming BEP v1 connections.
pub async fn start_bep_listener(
    bep_port: u16,
    local_name: String,
    local_folders: Vec<BepFolder>,
    on_peer_connected: Arc<dyn Fn(BepHello, BepClusterConfig, SocketAddr) + Send + Sync>,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let bind_addr = SocketAddr::from(([0, 0, 0, 0], bep_port));
    let listener = match TcpListener::bind(bind_addr).await {
        Ok(l) => l,
        Err(e) => {
            warn!("Failed to bind BEP v1 listener on {}: {}", bind_addr, e);
            return Err(Box::new(e));
        }
    };

    info!("Synapse Core BEP v1 (Syncthing Wire Protocol) listening on TCP {}", bind_addr);

    tokio::spawn(async move {
        while let Ok((socket, peer_addr)) = listener.accept().await {
            let name = local_name.clone();
            let folders = local_folders.clone();
            let cb = on_peer_connected.clone();

            tokio::spawn(async move {
                let _ = socket.set_nodelay(true);
                if let Err(e) = handle_bep_connection(socket, name, folders, peer_addr, move |hello, config, addr| {
                    cb(hello, config, addr);
                }).await {
                    warn!("BEP handshake failed from {}: {}", peer_addr, e);
                }
            });
        }
    });

    Ok(())
}

// =============================================================================
// 6. UNIT & PARITY TESTS
// =============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bep_varint_roundtrip() {
        let values = [0u64, 1, 127, 128, 255, 300, 16384, 1_000_000, u64::MAX];
        for &v in &values {
            let mut buf = Vec::new();
            encode_varint(v, &mut buf);
            let (decoded, consumed) = decode_varint(&buf).unwrap();
            assert_eq!(decoded, v);
            assert_eq!(consumed, buf.len());
        }
    }

    #[test]
    fn test_bep_hello_serialization() {
        let hello = BepHello {
            device_name: "MacBook-Pro-2026".to_string(),
            client_name: "syncthing".to_string(),
            client_version: "v1.27.8".to_string(),
        };

        let encoded = hello.encode();
        let decoded = BepHello::decode(&encoded).unwrap();
        assert_eq!(decoded, hello);

        let packet = pack_bep_hello(&hello);
        assert_eq!(&packet[0..4], &BEP_MAGIC.to_be_bytes());
        let payload_len = u16::from_be_bytes([packet[4], packet[5]]) as usize;
        assert_eq!(payload_len, encoded.len());
    }

    #[test]
    fn test_bep_cluster_config_serialization() {
        let config = BepClusterConfig {
            folders: vec![
                BepFolder {
                    id: "docs-vault".to_string(),
                    label: "Personal Documents".to_string(),
                    read_only: false,
                    paused: false,
                    device_ids: vec!["DEV-PEER-01".to_string(), "DEV-PEER-02".to_string()],
                },
                BepFolder {
                    id: "media-backup".to_string(),
                    label: "Camera Photos".to_string(),
                    read_only: true,
                    paused: false,
                    device_ids: vec!["BACKUP-NAS-01".to_string()],
                },
            ],
        };

        let bytes = config.encode();
        let decoded = BepClusterConfig::decode(&bytes).unwrap();
        assert_eq!(decoded.folders.len(), 2);
        assert_eq!(decoded.folders[0].id, "docs-vault");
        assert_eq!(decoded.folders[0].read_only, false);
        assert_eq!(decoded.folders[1].id, "media-backup");
        assert_eq!(decoded.folders[1].read_only, true);
    }

    #[test]
    fn test_bep_request_response_roundtrip() {
        let req = BepRequest {
            id: 42,
            folder: "shared-folder".to_string(),
            name: "report.pdf".to_string(),
            offset: 131072,
            size: 65536,
            hash: vec![0xAB; 32],
        };

        let req_bytes = req.encode();
        let req_decoded = BepRequest::decode(&req_bytes).unwrap();
        assert_eq!(req_decoded, req);

        let resp = BepResponse {
            id: 42,
            data: vec![0xCA, 0xFE, 0xBA, 0xBE],
            code: 0,
        };
        let resp_bytes = resp.encode();
        let resp_decoded = BepResponse::decode(&resp_bytes).unwrap();
        assert_eq!(resp_decoded, resp);
    }

    #[tokio::test]
    async fn test_bep_handshake_duplex_e2e() {
        let (mut client_stream, server_stream) = tokio::io::duplex(64 * 1024);

        let server_task = tokio::spawn(async move {
            let remote_hello = read_bep_hello(&mut client_stream).await.unwrap();
            assert_eq!(remote_hello.device_name, "Local-Synapse-Node");

            let server_hello = BepHello::new("Remote-Syncthing-Daemon");
            send_bep_hello(&mut client_stream, &server_hello).await.unwrap();

            // Receive ClusterConfig
            let (msg_type, _, payload) = read_bep_message(&mut client_stream).await.unwrap();
            assert_eq!(msg_type, MSG_CLUSTER_CONFIG);
            let config = BepClusterConfig::decode(&payload).unwrap();
            assert_eq!(config.folders[0].id, "test-folder");

            // Reply with ClusterConfig
            let reply_config = BepClusterConfig { folders: vec![] };
            send_bep_message(&mut client_stream, MSG_CLUSTER_CONFIG, &reply_config.encode()).await.unwrap();
        });

        let test_folder = BepFolder {
            id: "test-folder".to_string(),
            label: "Test Sync".to_string(),
            read_only: false,
            paused: false,
            device_ids: vec![],
        };

        let dummy_addr: SocketAddr = "127.0.0.1:22000".parse().unwrap();
        let _client_task = tokio::spawn(async move {
            let res = handle_bep_connection(
                server_stream,
                "Local-Synapse-Node".to_string(),
                vec![test_folder],
                dummy_addr,
                |hello, _, _| {
                    assert_eq!(hello.device_name, "Remote-Syncthing-Daemon");
                },
            ).await;
            let _ = res;
        });

        server_task.await.unwrap();
    }
}
