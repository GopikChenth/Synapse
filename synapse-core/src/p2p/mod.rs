//! P2P QUIC Transport, Peer Discovery, and Block Exchange Protocol.
//!
//! Handles:
//! - Ed25519 node identities and public key fingerprinting
//! - Local UDP peer discovery broadcasting
//! - High-performance QUIC multiplexed streams via Quinn
//! - Content-addressed block exchange and file revision replication

pub mod bep;
pub mod crypto;
pub mod identity;
pub mod nat;
pub mod relay;
pub mod secure;
pub mod swarm;
pub mod throttle;

pub use bep::{start_bep_listener, BEP_DEFAULT_PORT, BEP_MAGIC, BepClusterConfig, BepFolder, BepHello};
pub use crypto::{CryptoError, EncryptedChunkEnvelope, EncryptionAlgorithm};
pub use swarm::{ChunkAssignment, FilePullItem, PullOrder, SchedulingStrategy, SwarmScheduler};
pub use throttle::{BandwidthLimiter, RateLimiter};

use std::collections::{HashMap, HashSet};
use std::net::SocketAddr;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;

use quinn::Endpoint;
use serde::{Deserialize, Serialize};
use tokio::net::UdpSocket;
use tokio::sync::{broadcast, Mutex};
use tracing::{info, warn};

use crate::conflict::CoreEvent;
use crate::dag::RevisionNode;
use crate::engine::SyncEngine;
pub use crate::p2p::identity::NodeIdentity;
use crate::storage::{
    FileRecord, IntroducedDevice, IntroducerPropagationResult, PendingDeviceRecord, RevisionRecord,
};

pub const DEFAULT_P2P_PORT: u16 = 9190;
pub const DEFAULT_DISCOVERY_PORT: u16 = 21027;

/// P2P wire message exchanged over QUIC streams.
#[derive(Debug, Serialize, Deserialize)]
pub enum P2PMessage {
    Hello {
        device_id: String,
        device_name: String,
        folders: Vec<String>,
        #[serde(default)]
        cluster_devices: Vec<IntroducedDevice>,
    },
    IndexRequest {
        folder_id: String,
    },
    IndexResponse {
        folder_id: String,
        files: Vec<FileRecord>,
        revisions: Vec<RevisionRecord>,
    },
    ChunkRequest {
        chunk_hash: String,
    },
    ChunkResponse {
        chunk_hash: String,
        data: Vec<u8>,
    },
    /// A file's current revision. Sent unprompted when a file changes locally (push), or in
    /// reply to `FileRequest` (pull). `ancestry` carries the revision's DAG ancestors so the
    /// receiver can find the common ancestor and fast-forward or merge correctly.
    FileNotice {
        folder_id: String,
        relative_path: String,
        revision: RevisionNode,
        chunk_hashes: Vec<String>,
        #[serde(default)]
        ancestry: Vec<RevisionRecord>,
    },
    /// Asks for the `FileNotice` of one file (pull).
    FileRequest {
        folder_id: String,
        relative_path: String,
    },
    /// Cluster device announcement sent by an introducer node
    ClusterDevicesAnnouncement {
        introducer_id: String,
        devices: Vec<IntroducedDevice>,
        #[serde(default)]
        auto_trust: bool,
    },
    /// Request an encrypted chunk envelope from an untrusted storage node
    EncryptedChunkRequest {
        chunk_hash: String,
    },
    /// Encrypted chunk envelope exchanged with an untrusted storage node
    EncryptedChunkResponse {
        chunk_hash: String,
        envelope: EncryptedChunkEnvelope,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DiscoveredPeer {
    pub device_id: String,
    pub device_name: String,
    pub p2p_port: u16,
    pub folders: Vec<String>,
    #[serde(default)]
    pub introduced_devices: Vec<IntroducedDevice>,
}

#[derive(Debug, Clone)]
pub struct PeerInfo {
    pub device_id: String,
    pub device_name: String,
    pub addr: SocketAddr,
    pub last_seen: std::time::Instant,
    pub client_type: String,
}

/// How long a peer counts as online after we last heard from it (discovery broadcast,
/// QUIC Hello, or BEP handshake). Discovery announces every 5 s, so 30 s tolerates several
/// lost broadcasts. See `peer_connection_lifecycle`.
pub const PEER_PRESENCE_WINDOW: Duration = Duration::from_secs(30);

/// Number of peers heard from within the presence window.
fn fresh_peer_count(map: &HashMap<String, PeerInfo>) -> usize {
    map.values().filter(|p| p.last_seen.elapsed() <= PEER_PRESENCE_WINDOW).count()
}

#[derive(Clone)]
pub struct P2PManager {
    identity: NodeIdentity,
    device_name: String,
    p2p_port: u16,
    bep_port: u16,
    sync_engine: SyncEngine,
    peers: Arc<Mutex<HashMap<String, PeerInfo>>>,
    /// Peers last reported online by the presence sweep (to emit one PEER_DISCONNECTED each).
    online_peers: Arc<Mutex<HashSet<String>>>,
    /// TLS identity derived from the node key (mutual TLS); `None` disables QUIC.
    tls: Option<Arc<secure::TlsIdentity>>,
    /// Shared QUIC endpoint (server + client) once `run_server` has bound it.
    endpoint: Arc<Mutex<Option<Endpoint>>>,
    /// Authenticated live connections, keyed by the device ID as stored in `devices`.
    connections: Arc<Mutex<HashMap<String, quinn::Connection>>>,
    peer_count: Arc<AtomicUsize>,
    event_sender: broadcast::Sender<CoreEvent>,
}

fn bind_discovery_socket(port: u16) -> Result<UdpSocket, Box<dyn std::error::Error + Send + Sync>> {
    use socket2::{Domain, Protocol, Socket, Type};
    let addr = SocketAddr::from(([0, 0, 0, 0], port));
    let socket = Socket::new(Domain::IPV4, Type::DGRAM, Some(Protocol::UDP))?;
    let _ = socket.set_reuse_address(true);
    #[cfg(unix)]
    let _ = socket.set_reuse_port(true);
    let _ = socket.set_nonblocking(true);
    socket.bind(&addr.into())?;
    let std_sock: std::net::UdpSocket = socket.into();
    let tokio_sock = UdpSocket::from_std(std_sock)?;
    Ok(tokio_sock)
}

impl P2PManager {
    pub fn new(
        identity: NodeIdentity,
        device_name: String,
        p2p_port: u16,
        bep_port: u16,
        sync_engine: SyncEngine,
        peer_count: Arc<AtomicUsize>,
        event_sender: broadcast::Sender<CoreEvent>,
    ) -> Self {
        let tls = match secure::TlsIdentity::from_identity(&identity) {
            Ok(t) => Some(Arc::new(t)),
            Err(e) => {
                warn!("Could not derive TLS identity; QUIC transport disabled: {}", e);
                None
            }
        };
        Self {
            identity,
            device_name,
            p2p_port,
            bep_port,
            sync_engine,
            peers: Arc::new(Mutex::new(HashMap::new())),
            online_peers: Arc::new(Mutex::new(HashSet::new())),
            tls,
            endpoint: Arc::new(Mutex::new(None)),
            connections: Arc::new(Mutex::new(HashMap::new())),
            peer_count,
            event_sender,
        }
    }

    pub fn device_id(&self) -> &str {
        &self.identity.device_id
    }

    pub async fn register_peer(&self, peer: PeerInfo) {
        let mut map = self.peers.lock().await;
        map.insert(peer.device_id.clone(), peer);
        self.peer_count.store(fresh_peer_count(&map), Ordering::Relaxed);
    }

    pub async fn get_peer_info(&self, device_id: &str) -> Option<PeerInfo> {
        let map = self.peers.lock().await;
        map.get(device_id).cloned()
    }

    /// Peers heard from within [`PEER_PRESENCE_WINDOW`] (i.e. currently online).
    pub async fn list_connected_peers(&self) -> HashMap<String, PeerInfo> {
        let map = self.peers.lock().await;
        map.iter()
            .filter(|(_, p)| p.last_seen.elapsed() <= PEER_PRESENCE_WINDOW)
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect()
    }

    /// Every peer heard from since start-up, including ones that have since gone quiet.
    pub async fn list_known_peers(&self) -> HashMap<String, PeerInfo> {
        self.peers.lock().await.clone()
    }

    /// Seconds since the device was last heard from, or `None` if never seen.
    pub async fn peer_seen_secs_ago(&self, device_id: &str) -> Option<u64> {
        self.peers.lock().await.get(device_id).map(|p| p.last_seen.elapsed().as_secs())
    }

    /// Presence state of a device (see `peer_connection_lifecycle`).
    pub async fn peer_connection_state(&self, device_id: &str, paused: bool) -> crate::state::PeerConnectionState {
        let seen = self.peer_seen_secs_ago(device_id).await;
        crate::state::PeerConnectionState::derive(paused, seen, PEER_PRESENCE_WINDOW.as_secs())
    }

    /// Re-evaluates presence: peers that went quiet get exactly one `PEER_DISCONNECTED`, and
    /// `peer_count` is refreshed. Runs every few seconds from `start_discovery`.
    pub async fn sweep_presence(&self) {
        // A live authenticated connection keeps its peer present between messages.
        let live: Vec<String> = self
            .connections
            .lock()
            .await
            .iter()
            .filter(|(_, c)| c.close_reason().is_none())
            .map(|(id, _)| id.clone())
            .collect();
        for id in live {
            self.touch_presence(&id).await;
        }
        let (fresh, stale): (HashSet<String>, HashSet<String>) = {
            let map = self.peers.lock().await;
            self.peer_count.store(fresh_peer_count(&map), Ordering::Relaxed);
            let mut fresh = HashSet::new();
            let mut stale = HashSet::new();
            for (id, p) in map.iter() {
                if p.last_seen.elapsed() <= PEER_PRESENCE_WINDOW {
                    fresh.insert(id.clone());
                } else {
                    stale.insert(id.clone());
                }
            }
            (fresh, stale)
        };
        let mut online = self.online_peers.lock().await;
        for id in stale {
            if online.remove(&id) {
                info!("Peer {} has not been heard from in {:?}; marking offline", id, PEER_PRESENCE_WINDOW);
                let _ = self.event_sender.send(CoreEvent::PeerDisconnected { peer_id: id });
            }
        }
        online.extend(fresh);
    }

    /// Spawns UDP broadcast discovery listener and periodic announcer.
    pub fn start_discovery(&self, discovery_port: u16) {
        // 0. Presence sweep (online → offline transitions) and, every 30 s, dialing paired
        //    devices that have a configured address (works across subnets/VPNs where UDP
        //    broadcast discovery does not reach).
        let sweeper = self.clone();
        tokio::spawn(async move {
            let mut tick: u64 = 0;
            loop {
                tokio::time::sleep(Duration::from_secs(5)).await;
                sweeper.sweep_presence().await;
                if tick.is_multiple_of(6) {
                    sweeper.dial_configured_peers().await;
                }
                tick += 1;
            }
        });

        let device_id = self.identity.device_id.clone();
        let p2p_port = self.p2p_port;
        let dialer = self.clone();
        let event_sender = self.event_sender.clone();
        let storage_listener = self.sync_engine.storage().clone();

        // 1. Discovery listener task
        tokio::spawn(async move {
            let socket = match bind_discovery_socket(discovery_port) {
                Ok(s) => s,
                Err(e) => {
                    warn!("Failed to bind UDP discovery listener on port {}: {}", discovery_port, e);
                    return;
                }
            };

            let mut buf = [0u8; 2048];
            loop {
                if let Ok((len, src_addr)) = socket.recv_from(&mut buf).await {
                    if let Ok(peer) = serde_json::from_slice::<DiscoveredPeer>(&buf[..len]) {
                        if peer.device_id != device_id {
                            let mut peer_addr = src_addr;
                            peer_addr.set_port(peer.p2p_port);

                            // UDP announcements are unauthenticated: they only (a) start pairing
                            // for unknown devices and (b) tell us where to dial paired devices.
                            // Presence and introducer data come from authenticated QUIC sessions.
                            let (stored_id, pairing) = resolve_device(&storage_listener, &peer.device_id);
                            match pairing {
                                None => {
                                    let _ = storage_listener.add_pending_device(&PendingDeviceRecord {
                                        device_id: peer.device_id.clone(),
                                        name: peer.device_name.clone(),
                                        address: peer_addr.to_string(),
                                        received_at: chrono::Utc::now().timestamp_millis(),
                                    });
                                    info!(
                                        "New incoming pairing request discovered from {} ({}) at {}",
                                        peer.device_name, peer.device_id, peer_addr
                                    );
                                    let _ = event_sender.send(CoreEvent::DevicePending {
                                        device_id: peer.device_id.clone(),
                                        name: peer.device_name.clone(),
                                        address: peer_addr.to_string(),
                                    });
                                }
                                Some(crate::state::DevicePairingState::Pending) => {
                                    // Refresh name/address of the existing request
                                    let _ = storage_listener.add_pending_device(&PendingDeviceRecord {
                                        device_id: stored_id.clone(),
                                        name: peer.device_name.clone(),
                                        address: peer_addr.to_string(),
                                        received_at: chrono::Utc::now().timestamp_millis(),
                                    });
                                }
                                Some(crate::state::DevicePairingState::Paired) => {
                                    let dialer = dialer.clone();
                                    let hex_id = peer.device_id.clone();
                                    tokio::spawn(async move {
                                        if let Err(e) = dialer.ensure_connected(&hex_id, peer_addr).await {
                                            warn!("Could not connect to paired peer {} at {}: {}", hex_id, peer_addr, e);
                                        }
                                    });
                                }
                                // Paused or rejected: ignore
                                Some(_) => {}
                            }
                        }
                    }
                }
            }
        });

        // 2. Periodic discovery broadcast task
        let dev_id = self.identity.device_id.clone();
        let dev_name = self.device_name.clone();
        tokio::spawn(async move {
            let bind_addr = SocketAddr::from(([0, 0, 0, 0], 0));
            let socket = match UdpSocket::bind(bind_addr).await {
                Ok(s) => s,
                Err(e) => {
                    warn!("Failed to bind UDP broadcast sender: {}", e);
                    return;
                }
            };
            let _ = socket.set_broadcast(true);
            let target_addr = SocketAddr::from(([255, 255, 255, 255], discovery_port));

            loop {
                // Folder IDs and device lists are no longer broadcast to the whole LAN;
                // paired peers exchange them over authenticated sessions.
                let folders: Vec<String> = Vec::new();
                let introduced_devices: Vec<IntroducedDevice> = Vec::new();

                let announcement = DiscoveredPeer {
                    device_id: dev_id.clone(),
                    device_name: dev_name.clone(),
                    p2p_port,
                    folders,
                    introduced_devices,
                };

                if let Ok(payload) = serde_json::to_vec(&announcement) {
                    let _ = socket.send_to(&payload, target_addr).await;
                }

                tokio::time::sleep(Duration::from_secs(5)).await;
            }
        });
    }

    /// Processes introduced device announcements from a peer.
    /// If the peer is marked as an introducer in SQLite (introducer = 1),
    /// the announced devices are automatically propagated to pending or trusted devices.
    pub fn handle_cluster_announcement(
        &self,
        introducer_id: &str,
        devices: &[IntroducedDevice],
        auto_trust: bool,
    ) -> Result<IntroducerPropagationResult, Box<dyn std::error::Error + Send + Sync>> {
        let storage = self.sync_engine.storage();
        let report = storage.propagate_introducer_devices(introducer_id, devices, auto_trust)?;

        for trusted_id in &report.added_trusted {
            if let Ok(Some(dev)) = storage.get_device(trusted_id) {
                let _ = self.event_sender.send(CoreEvent::DevicePaired {
                    device_id: dev.device_id,
                    name: dev.name,
                });
            }
        }

        Ok(report)
    }

    // =====================================================================
    // AUTHENTICATED PEER PROTOCOL
    // =====================================================================

    /// Our Hello for a given peer. The cluster device list is only shared with paired peers.
    fn hello_for(&self, pairing: Option<crate::state::DevicePairingState>) -> P2PMessage {
        let cluster_devices = if pairing == Some(crate::state::DevicePairingState::Paired) {
            self.sync_engine
                .storage()
                .list_devices()
                .map(|list| {
                    list.into_iter()
                        .map(|d| IntroducedDevice {
                            device_id: d.device_id,
                            name: d.name,
                            addresses: d.addresses,
                            is_trusted: d.is_trusted,
                            cert_name: d.cert_name,
                        })
                        .collect()
                })
                .unwrap_or_default()
        } else {
            Vec::new()
        };
        P2PMessage::Hello {
            device_id: self.identity.device_id.clone(),
            device_name: self.device_name.clone(),
            folders: Vec::new(),
            cluster_devices,
        }
    }

    /// Handles one message from a peer whose device ID (`peer_hex`) was proven by mutual TLS.
    ///
    /// Rules: `Hello` is accepted from anyone (it starts pairing); every other message requires
    /// the peer to be **paired** (not paused, pending, or rejected); folder data is only served
    /// for folders shared with that peer; responses (`*Response`) are only accepted as replies
    /// to our own requests, never unsolicited; introducer announcements are attributed to the
    /// authenticated sender, never to a claimed id.
    pub async fn handle_peer_message(
        &self,
        peer_hex: &str,
        peer_addr: Option<SocketAddr>,
        msg: P2PMessage,
    ) -> Result<Option<P2PMessage>, Box<dyn std::error::Error + Send + Sync>> {
        use crate::state::DevicePairingState as Pairing;
        let storage = self.sync_engine.storage().clone();
        let (stored_id, pairing) = resolve_device(&storage, peer_hex);

        if let P2PMessage::Hello { device_id, device_name, folders: _, cluster_devices } = msg {
            if !same_device(&device_id, peer_hex) {
                warn!("Hello from {} claims to be {}; ignoring", peer_hex, device_id);
                return Ok(None);
            }
            match pairing {
                None => {
                    let address = peer_addr.map(|a| a.to_string()).unwrap_or_else(|| "dynamic".into());
                    let _ = storage.add_pending_device(&PendingDeviceRecord {
                        device_id: peer_hex.to_string(),
                        name: device_name.clone(),
                        address: address.clone(),
                        received_at: chrono::Utc::now().timestamp_millis(),
                    });
                    info!("Incoming pairing request from {} ({}) at {}", device_name, peer_hex, address);
                    let _ = self.event_sender.send(CoreEvent::DevicePending {
                        device_id: peer_hex.to_string(),
                        name: device_name,
                        address,
                    });
                }
                Some(Pairing::Pending) => {
                    let _ = storage.add_pending_device(&PendingDeviceRecord {
                        device_id: stored_id.clone(),
                        name: device_name,
                        address: peer_addr.map(|a| a.to_string()).unwrap_or_else(|| "dynamic".into()),
                        received_at: chrono::Utc::now().timestamp_millis(),
                    });
                }
                Some(Pairing::Paired) => {
                    if let Some(addr) = peer_addr {
                        self.mark_present(&stored_id, &device_name, addr).await;
                    }
                    if !cluster_devices.is_empty() && storage.is_device_introducer(&stored_id).unwrap_or(false) {
                        // Only from peers the user marked as introducers, attributed to the
                        // *authenticated* sender.
                        let _ = self.handle_cluster_announcement(&stored_id, &cluster_devices, false);
                    }
                }
                Some(_) => {} // paused or rejected: no state changes
            }
            return Ok(Some(self.hello_for(pairing)));
        }

        if pairing != Some(Pairing::Paired) {
            warn!("Ignoring message from unpaired/paused peer {}", peer_hex);
            return Ok(None);
        }
        self.touch_presence(&stored_id).await;

        match msg {
            P2PMessage::ClusterDevicesAnnouncement { introducer_id: _, devices, auto_trust } => {
                let report = self.handle_cluster_announcement(&stored_id, &devices, auto_trust)?;
                info!(
                    "Introducer {} announced {} devices: {} trusted, {} pending",
                    stored_id,
                    devices.len(),
                    report.added_trusted.len(),
                    report.added_pending.len()
                );
                Ok(None)
            }
            P2PMessage::IndexRequest { folder_id } => {
                if !folder_shared_with(&storage, &folder_id, &stored_id) {
                    return Ok(None);
                }
                let files = storage.list_files_in_folder(&folder_id)?;
                Ok(Some(P2PMessage::IndexResponse { folder_id, files, revisions: Vec::new() }))
            }
            P2PMessage::FileRequest { folder_id, relative_path } => {
                if !folder_shared_with(&storage, &folder_id, &stored_id) {
                    return Ok(None);
                }
                self.build_file_notice(&folder_id, &relative_path)
            }
            P2PMessage::FileNotice { folder_id, relative_path, revision, chunk_hashes, ancestry } => {
                // Applied in the background: we fetch missing chunks back from the sender.
                let me = self.clone();
                tokio::spawn(async move {
                    if let Err(e) = me
                        .apply_file_notice(&stored_id, &folder_id, &relative_path, revision, chunk_hashes, ancestry)
                        .await
                    {
                        warn!("Could not apply {}/{} from {}: {}", folder_id, relative_path, stored_id, e);
                    }
                });
                Ok(None)
            }
            P2PMessage::ChunkRequest { chunk_hash } => {
                if !storage.chunk_visible_to_device(&chunk_hash, &stored_id)? {
                    return Ok(None);
                }
                Ok(storage.get_chunk(&chunk_hash)?.map(|data| P2PMessage::ChunkResponse { chunk_hash, data }))
            }
            P2PMessage::EncryptedChunkRequest { chunk_hash } => {
                if !storage.chunk_visible_to_device(&chunk_hash, &stored_id)? {
                    return Ok(None);
                }
                Ok(storage
                    .get_encrypted_chunk(&chunk_hash)?
                    .map(|envelope| P2PMessage::EncryptedChunkResponse { chunk_hash, envelope }))
            }
            // Responses are only meaningful as replies to our own requests.
            P2PMessage::Hello { .. }
            | P2PMessage::IndexResponse { .. }
            | P2PMessage::ChunkResponse { .. }
            | P2PMessage::EncryptedChunkResponse { .. } => Ok(None),
        }
    }

    // =====================================================================
    // PRESENCE
    // =====================================================================

    async fn mark_present(&self, stored_id: &str, name: &str, addr: SocketAddr) {
        self.register_peer(PeerInfo {
            device_id: stored_id.to_string(),
            device_name: name.to_string(),
            addr,
            last_seen: std::time::Instant::now(),
            client_type: "synapse-quic (mTLS)".into(),
        })
        .await;
    }

    async fn touch_presence(&self, stored_id: &str) {
        if let Some(p) = self.peers.lock().await.get_mut(stored_id) {
            p.last_seen = std::time::Instant::now();
        }
    }

    async fn mark_absent(&self, stored_id: &str) {
        let mut map = self.peers.lock().await;
        if let Some(p) = map.get_mut(stored_id) {
            if let Some(past) = std::time::Instant::now().checked_sub(PEER_PRESENCE_WINDOW + Duration::from_secs(1)) {
                p.last_seen = past;
            }
        }
        self.peer_count.store(fresh_peer_count(&map), Ordering::Relaxed);
    }

    // =====================================================================
    // QUIC TRANSPORT (mutual TLS)
    // =====================================================================

    /// Address the QUIC endpoint is bound to (useful when bound to port 0).
    pub async fn local_quic_addr(&self) -> Option<SocketAddr> {
        self.endpoint.lock().await.as_ref().and_then(|e| e.local_addr().ok())
    }

    /// Whether an authenticated connection to the device is open.
    pub async fn is_connected_to(&self, stored_id: &str) -> bool {
        self.connections
            .lock()
            .await
            .get(stored_id)
            .map(|c| c.close_reason().is_none())
            .unwrap_or(false)
    }

    /// Connects (if not already connected) to the paired device `device_hex` at `addr`,
    /// proving both identities with mutual TLS, then exchanges Hello and pulls every folder
    /// shared with it.
    pub async fn ensure_connected(
        &self,
        device_hex: &str,
        addr: SocketAddr,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let storage = self.sync_engine.storage().clone();
        let (stored_id, pairing) = resolve_device(&storage, device_hex);
        if pairing != Some(crate::state::DevicePairingState::Paired) {
            return Err(format!("device {} is not paired", device_hex).into());
        }
        if self.is_connected_to(&stored_id).await {
            return Ok(());
        }
        let expected = normalize_hex(device_hex).ok_or("device id is not a public-key id")?;
        let tls = self.tls.clone().ok_or("QUIC transport disabled")?;
        let endpoint = self.endpoint.lock().await.clone().ok_or("QUIC endpoint is not running")?;
        let conn = endpoint
            .connect_with(tls.client_config(&expected)?, addr, secure::SERVER_NAME)?
            .await?;
        let proven = secure::peer_device_id(&conn).ok_or("peer presented no identity")?;
        if proven != expected {
            conn.close(1u32.into(), b"identity mismatch");
            return Err("peer identity mismatch".into());
        }
        self.adopt_connection(stored_id.clone(), proven, conn, addr).await;

        if let Some(P2PMessage::Hello { device_name, .. }) =
            self.request(&stored_id, self.hello_for(pairing)).await?
        {
            self.mark_present(&stored_id, &device_name, addr).await;
        }
        self.sync_all_folders_with(&stored_id).await;
        Ok(())
    }

    /// Dials every paired, unpaused device that is not connected and has a configured
    /// address (`ip:port`, `quic://host:port`, or `host:port`).
    pub async fn dial_configured_peers(&self) {
        let devices = self.sync_engine.storage().list_devices().unwrap_or_default();
        for dev in devices {
            if dev.is_paused || self.is_connected_to(&dev.device_id).await {
                continue;
            }
            let Some(hex) = normalize_hex(&dev.device_id) else { continue };
            for addr in dev.addresses.iter().filter_map(|a| parse_peer_address(a)) {
                if self.ensure_connected(&hex, addr).await.is_ok() {
                    break;
                }
            }
        }
    }

    /// Registers an authenticated connection and starts serving the peer's requests on it.
    async fn adopt_connection(&self, stored_id: String, peer_hex: String, conn: quinn::Connection, addr: SocketAddr) {
        self.connections.lock().await.insert(stored_id.clone(), conn.clone());
        let (_, pairing) = resolve_device(self.sync_engine.storage(), &peer_hex);
        if pairing == Some(crate::state::DevicePairingState::Paired) {
            self.mark_present(&stored_id, &stored_id, addr).await;
            info!("Authenticated connection with {} at {}", stored_id, addr);
            let _ = self.event_sender.send(CoreEvent::PeerConnected {
                peer_id: stored_id.clone(),
                addr: addr.to_string(),
            });
        }
        let me = self.clone();
        tokio::spawn(async move {
            me.serve_connection(conn, stored_id, peer_hex, addr).await;
        });
    }

    /// Serves request streams opened by the peer until the connection closes.
    async fn serve_connection(&self, conn: quinn::Connection, stored_id: String, peer_hex: String, addr: SocketAddr) {
        while let Ok((mut send, mut recv)) = conn.accept_bi().await {
            let me = self.clone();
            let peer_hex = peer_hex.clone();
            tokio::spawn(async move {
                if let Ok(buf) = recv.read_to_end(MAX_REQUEST_BYTES).await {
                    if let Ok(msg) = serde_json::from_slice::<P2PMessage>(&buf) {
                        match me.handle_peer_message(&peer_hex, Some(addr), msg).await {
                            Ok(Some(reply)) => {
                                if let Ok(bytes) = serde_json::to_vec(&reply) {
                                    let _ = send.write_all(&bytes).await;
                                }
                            }
                            Ok(None) => {}
                            Err(e) => warn!("Error handling message from {}: {}", peer_hex, e),
                        }
                    }
                }
                // Always finish so the requester's read completes (empty reply = no answer).
                let _ = send.finish();
            });
        }
        // Connection ended: forget it (unless replaced by a newer one) and mark the peer offline.
        let mut map = self.connections.lock().await;
        if map.get(&stored_id).map(|c| c.stable_id()) == Some(conn.stable_id()) {
            map.remove(&stored_id);
            drop(map);
            self.mark_absent(&stored_id).await;
        }
    }

    /// Sends a request to a connected peer and waits for its reply (`None` = no answer).
    pub async fn request(
        &self,
        stored_id: &str,
        msg: P2PMessage,
    ) -> Result<Option<P2PMessage>, Box<dyn std::error::Error + Send + Sync>> {
        let conn = self
            .connections
            .lock()
            .await
            .get(stored_id)
            .cloned()
            .ok_or_else(|| format!("not connected to {}", stored_id))?;
        let (mut send, mut recv) = conn.open_bi().await?;
        send.write_all(&serde_json::to_vec(&msg)?).await?;
        send.finish()?;
        let buf = recv.read_to_end(MAX_RESPONSE_BYTES).await?;
        if buf.is_empty() {
            return Ok(None);
        }
        Ok(Some(serde_json::from_slice(&buf)?))
    }

    // =====================================================================
    // SYNC: pull (index exchange), push (file notices), apply
    // =====================================================================

    /// Builds the `FileNotice` for a local file's current revision, including DAG ancestry.
    pub fn build_file_notice(
        &self,
        folder_id: &str,
        relative_path: &str,
    ) -> Result<Option<P2PMessage>, Box<dyn std::error::Error + Send + Sync>> {
        let storage = self.sync_engine.storage();
        let Some(file) = storage.get_file(folder_id, relative_path)? else {
            return Ok(None);
        };
        if file.is_deleted {
            return Ok(None);
        }
        let Some(rev) = storage.get_revision(&file.current_revision)? else {
            return Ok(None);
        };
        let chunk_hashes = storage.get_revision_chunks(&rev.revision_hash)?;

        let mut ancestry = Vec::new();
        let mut seen = HashSet::new();
        let mut queue: std::collections::VecDeque<String> =
            rev.parent_1.iter().chain(rev.parent_2.iter()).cloned().collect();
        while let Some(hash) = queue.pop_front() {
            if ancestry.len() >= MAX_ANCESTRY || !seen.insert(hash.clone()) {
                continue;
            }
            if let Some(r) = storage.get_revision(&hash)? {
                queue.extend(r.parent_1.iter().chain(r.parent_2.iter()).cloned());
                ancestry.push(r);
            }
        }

        let revision = RevisionNode {
            revision_hash: rev.revision_hash.clone(),
            parent_hashes: rev.parent_1.iter().chain(rev.parent_2.iter()).cloned().collect(),
            manifest_hash: rev.manifest_hash.clone(),
            author_device_id: rev.author_device.clone(),
            sequence_number: 0,
            mtime_nanos: file.mtime_nanos,
            file_size: file.size,
        };
        Ok(Some(P2PMessage::FileNotice {
            folder_id: folder_id.to_string(),
            relative_path: relative_path.to_string(),
            revision,
            chunk_hashes,
            ancestry,
        }))
    }

    /// Verifies and applies a remote file revision from `peer` (stored id): checks the
    /// manifest against the chunk list, fetches missing chunks from the peer (each verified by
    /// BLAKE3), then hands it to the engine, which fast-forwards, merges, or quarantines.
    pub async fn apply_file_notice(
        &self,
        peer: &str,
        folder_id: &str,
        relative_path: &str,
        revision: RevisionNode,
        chunk_hashes: Vec<String>,
        ancestry: Vec<RevisionRecord>,
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let storage = self.sync_engine.storage().clone();
        let folder = storage
            .get_folder(folder_id)?
            .ok_or_else(|| format!("folder {} does not exist here", folder_id))?;
        if !folder_shared_with(&storage, folder_id, peer) {
            return Err(format!("folder {} is not shared with {}", folder_id, peer).into());
        }
        if folder.is_paused || !folder.parsed_sync_mode().can_receive() {
            return Ok(());
        }
        if chunk_hashes.iter().any(|h| !crate::storage::is_valid_chunk_hash(h)) {
            return Err("notice contains an invalid chunk hash".into());
        }
        if crate::chunker::Chunker::compute_manifest_hash(&chunk_hashes) != revision.manifest_hash {
            return Err("notice manifest does not match its chunk list".into());
        }

        let rel = crate::engine::fs_utils::sanitize_relative_path(relative_path);
        let local = storage.get_file(folder_id, &rel)?;
        if let Some(l) = &local {
            if l.current_revision == revision.revision_hash {
                return Ok(()); // already up to date
            }
        }
        for r in ancestry.iter().take(MAX_ANCESTRY) {
            storage.insert_revision(r, &[])?;
        }
        if let Some(l) = &local {
            if storage.get_revision(&revision.revision_hash)?.is_some()
                && crate::dag::MerkleDag::new(&storage).find_lca(&l.current_revision, &revision.revision_hash)
                    == crate::dag::LcaResult::FastForwardLocal
            {
                return Ok(()); // we already have newer content
            }
        }

        let mut cache: HashMap<String, Vec<u8>> = HashMap::new();
        let mut payload = Vec::new();
        for h in &chunk_hashes {
            if !cache.contains_key(h) {
                let data = match storage.get_chunk(h)? {
                    Some(d) => d,
                    None => {
                        // Verified by fetch_chunk; stored now so the revision's chunk list
                        // (file_chunks → chunks foreign key) can be recorded.
                        let d = self.fetch_chunk(peer, h).await?;
                        storage.put_chunk(h, &d)?;
                        d
                    }
                };
                cache.insert(h.clone(), data);
            }
            payload.extend_from_slice(&cache[h]);
        }
        if payload.len() as u64 != revision.file_size {
            return Err("assembled file size does not match the notice".into());
        }

        let engine = self.sync_engine.clone();
        let folder_id = folder_id.to_string();
        tokio::task::spawn_blocking(move || {
            engine.reconcile_remote_revision(&folder_id, &rel, &revision, &chunk_hashes, &payload)
        })
        .await??;
        Ok(())
    }

    /// Fetches one chunk from a peer and verifies it hashes to `chunk_hash`.
    async fn fetch_chunk(&self, peer: &str, chunk_hash: &str) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
        match self.request(peer, P2PMessage::ChunkRequest { chunk_hash: chunk_hash.to_string() }).await? {
            Some(P2PMessage::ChunkResponse { chunk_hash: h, data })
                if h == chunk_hash && blake3::hash(&data).to_hex().as_str() == chunk_hash =>
            {
                Ok(data)
            }
            Some(_) => Err(format!("peer {} sent a corrupt chunk {}", peer, chunk_hash).into()),
            None => Err(format!("peer {} does not have chunk {}", peer, chunk_hash).into()),
        }
    }

    /// Pulls one folder from a connected peer: compares indexes and applies every file whose
    /// remote revision we don't already have. Returns how many files were requested.
    pub async fn sync_folder_from(
        &self,
        peer: &str,
        folder_id: &str,
    ) -> Result<usize, Box<dyn std::error::Error + Send + Sync>> {
        let storage = self.sync_engine.storage().clone();
        let files = match self.request(peer, P2PMessage::IndexRequest { folder_id: folder_id.to_string() }).await? {
            Some(P2PMessage::IndexResponse { files, .. }) => files,
            _ => return Ok(0),
        };
        let mut requested = 0;
        for remote in files.into_iter().filter(|f| !f.is_deleted) {
            if let Some(local) = storage.get_file(folder_id, &remote.relative_path)? {
                if local.current_revision == remote.current_revision {
                    continue;
                }
                if storage.get_revision(&remote.current_revision)?.is_some()
                    && crate::dag::MerkleDag::new(&storage).find_lca(&local.current_revision, &remote.current_revision)
                        == crate::dag::LcaResult::FastForwardLocal
                {
                    continue;
                }
            }
            requested += 1;
            let request = P2PMessage::FileRequest {
                folder_id: folder_id.to_string(),
                relative_path: remote.relative_path.clone(),
            };
            if let Some(P2PMessage::FileNotice { relative_path, revision, chunk_hashes, ancestry, .. }) =
                self.request(peer, request).await?
            {
                if let Err(e) = self
                    .apply_file_notice(peer, folder_id, &relative_path, revision, chunk_hashes, ancestry)
                    .await
                {
                    warn!("Could not sync {}/{} from {}: {}", folder_id, relative_path, peer, e);
                }
            }
        }
        Ok(requested)
    }

    /// Pulls every unpaused folder shared with the peer.
    pub async fn sync_all_folders_with(&self, peer: &str) {
        let storage = self.sync_engine.storage().clone();
        for folder in storage.list_folders().unwrap_or_default() {
            if folder.is_paused || !folder_shared_with(&storage, &folder.folder_id, peer) {
                continue;
            }
            if let Err(e) = self.sync_folder_from(peer, &folder.folder_id).await {
                warn!("Sync of folder {} with {} failed: {}", folder.folder_id, peer, e);
            }
        }
    }

    /// Pushes local changes: on every `FILE_CHANGED`, sends a `FileNotice` to each connected
    /// peer the folder is shared with.
    fn spawn_change_pusher(&self) {
        let me = self.clone();
        let mut rx = self.event_sender.subscribe();
        tokio::spawn(async move {
            loop {
                match rx.recv().await {
                    Ok(CoreEvent::FileChanged { folder_id, relative_path, .. }) => {
                        me.push_file(&folder_id, &relative_path).await;
                    }
                    Ok(_) => {}
                    Err(broadcast::error::RecvError::Lagged(n)) => {
                        warn!("Change pusher lagged by {} events; peers will catch up on next index sync", n);
                    }
                    Err(broadcast::error::RecvError::Closed) => break,
                }
            }
        });
    }

    /// Sends the current revision of one file to every connected peer sharing its folder.
    pub async fn push_file(&self, folder_id: &str, relative_path: &str) {
        let notice = match self.build_file_notice(folder_id, relative_path) {
            Ok(Some(n)) => n,
            _ => return,
        };
        let payload = match serde_json::to_value(&notice) {
            Ok(v) => v,
            Err(_) => return,
        };
        let storage = self.sync_engine.storage().clone();
        let peers: Vec<String> = self.connections.lock().await.keys().cloned().collect();
        for peer in peers {
            if !folder_shared_with(&storage, folder_id, &peer) {
                continue;
            }
            let Ok(msg) = serde_json::from_value::<P2PMessage>(payload.clone()) else { continue };
            let me = self.clone();
            tokio::spawn(async move {
                if let Err(e) = me.request(&peer, msg).await {
                    warn!("Could not push change to {}: {}", peer, e);
                }
            });
        }
    }

    /// Binds the mutually-authenticated QUIC endpoint (server and client on one socket).
    pub fn create_quic_endpoint(&self, bind_addr: SocketAddr) -> Result<Endpoint, Box<dyn std::error::Error + Send + Sync>> {
        let tls = self.tls.clone().ok_or("QUIC transport disabled (no TLS identity)")?;
        Ok(Endpoint::server(tls.server_config()?, bind_addr)?)
    }

    /// Spawns the BEP v1 listener and the authenticated QUIC server, and accepts peers.
    pub async fn run_server(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        // 1. BEP v1 TCP listener (Syncthing wire protocol). Unauthenticated: peers are listed
        //    under a derived "BEP-…" id and are never matched to paired devices by name.
        let bep_port = self.bep_port;
        let local_name = self.device_name.clone();
        let storage = self.sync_engine.storage().clone();
        let folders = storage
            .list_folders()
            .map(|list| {
                list.into_iter()
                    .map(|f| BepFolder {
                        label: f.folder_id.clone(),
                        id: f.folder_id,
                        read_only: false,
                        paused: f.is_paused,
                        device_ids: Vec::new(),
                    })
                    .collect()
            })
            .unwrap_or_default();

        let self_clone = self.clone();
        let on_peer_connected = Arc::new(move |hello: BepHello, _config: BepClusterConfig, addr: SocketAddr| {
            let p2p = self_clone.clone();
            tokio::spawn(async move {
                let bep_id = format!(
                    "BEP-{}",
                    blake3::hash(hello.device_name.as_bytes()).to_hex()[..16].to_ascii_uppercase()
                );
                let client_str = format!("syncthing-bep-v1 ({}/{})", hello.client_name, hello.client_version);
                p2p.register_peer(PeerInfo {
                    device_id: bep_id.clone(),
                    device_name: hello.device_name.clone(),
                    addr,
                    last_seen: std::time::Instant::now(),
                    client_type: client_str,
                })
                .await;
                let _ = p2p.event_sender.send(CoreEvent::PeerConnected { peer_id: bep_id, addr: addr.to_string() });
            });
        });

        if let Err(e) = start_bep_listener(bep_port, local_name, folders, on_peer_connected).await {
            warn!("Unable to start BEP v1 listener on port {}: {}", bep_port, e);
        }

        // 2. Mutually-authenticated QUIC endpoint for the Synapse mesh
        let bind_addr = SocketAddr::from(([0, 0, 0, 0], self.p2p_port));
        let endpoint = match self.create_quic_endpoint(bind_addr) {
            Ok(ep) => ep,
            Err(e) => {
                warn!("Unable to start QUIC server on {}: {}", bind_addr, e);
                return Ok(());
            }
        };
        info!(
            "Synapse Core QUIC P2P (mutual TLS) listening on {}",
            endpoint.local_addr().map(|a| a.to_string()).unwrap_or_default()
        );
        *self.endpoint.lock().await = Some(endpoint.clone());
        self.spawn_change_pusher();

        while let Some(incoming) = endpoint.accept().await {
            let me = self.clone();
            tokio::spawn(async move {
                let Ok(conn) = incoming.await else { return };
                let Some(peer_hex) = secure::peer_device_id(&conn) else {
                    conn.close(1u32.into(), b"no identity");
                    return;
                };
                let addr = conn.remote_address();
                let (stored_id, pairing) = resolve_device(me.sync_engine.storage(), &peer_hex);
                use crate::state::DevicePairingState as Pairing;
                match pairing {
                    Some(Pairing::Paused) | Some(Pairing::Rejected) => {
                        conn.close(2u32.into(), b"not accepted");
                    }
                    Some(Pairing::Paired) => {
                        me.adopt_connection(stored_id.clone(), peer_hex, conn, addr).await;
                        me.sync_all_folders_with(&stored_id).await;
                    }
                    // Unknown or pending: allowed to say Hello (pairing request) only.
                    _ => me.adopt_connection(stored_id, peer_hex, conn, addr).await,
                }
            });
        }

        Ok(())
    }
}

/// Largest request we accept from a peer.
const MAX_REQUEST_BYTES: usize = 16 * 1024 * 1024;
/// Largest reply we accept (index responses and JSON-encoded chunks).
const MAX_RESPONSE_BYTES: usize = 64 * 1024 * 1024;
/// Most ancestor revisions sent with a file notice.
const MAX_ANCESTRY: usize = 256;

/// Lowercase hex form of a device id given as hex or grouped Base32, if it is a public-key id.
fn normalize_hex(device_id: &str) -> Option<String> {
    let n = identity::normalize_device_id(device_id).ok()?;
    (n.len() == 64 && n.chars().all(|c| c.is_ascii_hexdigit())).then_some(n)
}

/// Parses a configured peer address into a socket address. Accepts `ip:port`,
/// `quic://host:port`, and `host:port`; ignores `dynamic` and other schemes (e.g. BEP's
/// `tcp://`, which is a different port).
pub fn parse_peer_address(address: &str) -> Option<SocketAddr> {
    let a = address.trim();
    let rest = a.strip_prefix("quic://").unwrap_or(a);
    if rest.contains("://") || rest.eq_ignore_ascii_case("dynamic") {
        return None;
    }
    if let Ok(sa) = rest.parse::<SocketAddr>() {
        return Some(sa);
    }
    use std::net::ToSocketAddrs;
    rest.to_socket_addrs().ok()?.next()
}

/// Whether two device ids (hex or grouped Base32) refer to the same device.
fn same_device(a: &str, b: &str) -> bool {
    match (normalize_hex(a), normalize_hex(b)) {
        (Some(x), Some(y)) => x == y,
        _ => a == b,
    }
}

/// Finds how a device is stored locally (it may have been paired by hex id or by its grouped
/// Base32 form) and its pairing state. Returns `(stored_id, state)`; `stored_id` falls back
/// to the given id when the device is unknown.
pub fn resolve_device(
    storage: &crate::storage::StorageEngine,
    device_id: &str,
) -> (String, Option<crate::state::DevicePairingState>) {
    let mut candidates = vec![device_id.to_string()];
    if let Some(hex) = normalize_hex(device_id) {
        if let Ok(bytes) = hex::decode(&hex) {
            candidates.push(identity::format_device_id(&bytes));
        }
        candidates.push(hex);
    }
    for c in &candidates {
        if let Ok(Some(state)) = storage.device_pairing_state(c) {
            return (c.clone(), Some(state));
        }
    }
    (device_id.to_string(), None)
}

fn folder_shared_with(storage: &crate::storage::StorageEngine, folder_id: &str, stored_device_id: &str) -> bool {
    storage
        .list_folder_devices(folder_id)
        .map(|devs| devs.iter().any(|d| d == stored_device_id))
        .unwrap_or(false)
}
