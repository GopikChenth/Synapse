//! Mutually-authenticated QUIC transport bound to device identity.
//!
//! Every node presents a self-signed X.509 certificate whose key **is** its Ed25519 node
//! identity key, so the device ID (hex of the Ed25519 public key) is proven by the TLS 1.3
//! handshake itself — never taken from a message field. Both sides require a certificate
//! (mutual TLS); the dialing side additionally pins the identity it expects, so a
//! man-in-the-middle cannot answer for another device.
//!
//! Trust (is this device paired? which folders may it see?) is decided above this layer,
//! from the authenticated device ID.

use std::sync::Arc;
use std::time::Duration;

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::crypto::{verify_tls12_signature, verify_tls13_signature, WebPkiSupportedAlgorithms};
use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer, ServerName, UnixTime};
use rustls::server::danger::{ClientCertVerified, ClientCertVerifier};
use rustls::{DigitallySignedStruct, DistinguishedName, SignatureScheme};

use super::identity::NodeIdentity;

/// TLS server name used by every Synapse node (identity comes from the key, not the name).
pub const SERVER_NAME: &str = "synapse.p2p";
/// ALPN protocol id for the Synapse peer protocol.
pub const ALPN: &[u8] = b"synapse/1";

/// DER prefix of an Ed25519 SubjectPublicKeyInfo: SEQ{ SEQ{ OID 1.3.101.112 }, BIT STRING(33) 0x00 … }.
const ED25519_SPKI_PREFIX: [u8; 12] = [0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00];
/// DER prefix of a PKCS#8 v1 Ed25519 private key wrapping a 32-byte seed.
const ED25519_PKCS8_PREFIX: [u8; 16] = [
    0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20,
];

type BoxError = Box<dyn std::error::Error + Send + Sync>;

/// Certificate + private key derived from a node's Ed25519 identity.
pub struct TlsIdentity {
    cert: CertificateDer<'static>,
    key: Vec<u8>,
    /// Hex device ID proven by this certificate.
    pub device_id: String,
}

impl TlsIdentity {
    /// Builds a self-signed certificate whose public key is the node's identity key.
    pub fn from_identity(identity: &NodeIdentity) -> Result<Self, BoxError> {
        let mut pkcs8 = ED25519_PKCS8_PREFIX.to_vec();
        pkcs8.extend_from_slice(&identity.signing_key.to_bytes());
        let key_der = PrivatePkcs8KeyDer::from(pkcs8.clone());
        let key_pair = rcgen::KeyPair::from_pkcs8_der_and_sign_algo(&key_der, &rcgen::PKCS_ED25519)?;
        let params = rcgen::CertificateParams::new(vec![SERVER_NAME.to_string()])?;
        let cert = params.self_signed(&key_pair)?;
        let cert_der = cert.der().clone();
        let device_id = device_id_from_cert(&cert_der).ok_or("generated certificate has no Ed25519 key")?;
        if device_id != identity.device_id {
            return Err("certificate key does not match node identity".into());
        }
        Ok(Self { cert: cert_der, key: pkcs8, device_id })
    }

    fn key_der(&self) -> PrivateKeyDer<'static> {
        PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(self.key.clone()))
    }

    /// QUIC server config: TLS 1.3, client certificate required.
    pub fn server_config(&self) -> Result<quinn::ServerConfig, BoxError> {
        let provider = Arc::new(rustls::crypto::ring::default_provider());
        let mut tls = rustls::ServerConfig::builder_with_provider(provider.clone())
            .with_protocol_versions(&[&rustls::version::TLS13])?
            .with_client_cert_verifier(Arc::new(PeerVerifier::new(&provider, None)))
            .with_single_cert(vec![self.cert.clone()], self.key_der())?;
        tls.alpn_protocols = vec![ALPN.to_vec()];
        let crypto = quinn::crypto::rustls::QuicServerConfig::try_from(tls)?;
        let mut config = quinn::ServerConfig::with_crypto(Arc::new(crypto));
        let transport = Arc::get_mut(&mut config.transport).ok_or("transport config shared")?;
        transport.max_idle_timeout(Some(Duration::from_secs(30).try_into()?));
        transport.keep_alive_interval(Some(Duration::from_secs(10)));
        Ok(config)
    }

    /// QUIC client config that only accepts the peer whose device ID is `expected_device_id`.
    pub fn client_config(&self, expected_device_id: &str) -> Result<quinn::ClientConfig, BoxError> {
        let provider = Arc::new(rustls::crypto::ring::default_provider());
        let mut tls = rustls::ClientConfig::builder_with_provider(provider.clone())
            .with_protocol_versions(&[&rustls::version::TLS13])?
            .dangerous()
            .with_custom_certificate_verifier(Arc::new(PeerVerifier::new(
                &provider,
                Some(expected_device_id.to_ascii_lowercase()),
            )))
            .with_client_auth_cert(vec![self.cert.clone()], self.key_der())?;
        tls.alpn_protocols = vec![ALPN.to_vec()];
        let crypto = quinn::crypto::rustls::QuicClientConfig::try_from(tls)?;
        let mut config = quinn::ClientConfig::new(Arc::new(crypto));
        let mut transport = quinn::TransportConfig::default();
        transport.max_idle_timeout(Some(Duration::from_secs(30).try_into()?));
        transport.keep_alive_interval(Some(Duration::from_secs(10)));
        config.transport_config(Arc::new(transport));
        Ok(config)
    }
}

/// Device ID (hex Ed25519 public key) proven by an established QUIC connection.
pub fn peer_device_id(conn: &quinn::Connection) -> Option<String> {
    let identity = conn.peer_identity()?;
    let certs = identity.downcast::<Vec<CertificateDer<'static>>>().ok()?;
    certs.first().and_then(|c| device_id_from_cert(c))
}

// -----------------------------------------------------------------------------
// Strict X.509 parsing: locate tbsCertificate.subjectPublicKeyInfo by structure
// (never by searching bytes, which a crafted extension could spoof).
// -----------------------------------------------------------------------------

/// Reads one DER TLV at `pos`; returns (tag, content_start, content_end).
fn der_tlv(buf: &[u8], pos: usize) -> Option<(u8, usize, usize)> {
    let tag = *buf.get(pos)?;
    let first = *buf.get(pos + 1)? as usize;
    let (len, header) = if first < 0x80 {
        (first, 2)
    } else {
        let n = first & 0x7f;
        if n == 0 || n > 4 {
            return None;
        }
        let mut len = 0usize;
        for i in 0..n {
            len = (len << 8) | *buf.get(pos + 2 + i)? as usize;
        }
        (len, 2 + n)
    };
    let start = pos + header;
    let end = start.checked_add(len)?;
    if end > buf.len() {
        return None;
    }
    Some((tag, start, end))
}

/// Extracts the hex device ID from an Ed25519 X.509 certificate, or `None` if the
/// certificate is malformed or not Ed25519.
pub fn device_id_from_cert(cert: &[u8]) -> Option<String> {
    // Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signature }
    let (tag, cert_start, cert_end) = der_tlv(cert, 0)?;
    if tag != 0x30 || cert_end != cert.len() {
        return None;
    }
    // tbsCertificate ::= SEQUENCE { [0] version OPTIONAL, serial, signature, issuer, validity, subject, spki, ... }
    let (tag, tbs_start, tbs_end) = der_tlv(cert, cert_start)?;
    if tag != 0x30 {
        return None;
    }
    let mut pos = tbs_start;
    let (tag, _, end) = der_tlv(cert, pos)?;
    if tag == 0xa0 {
        pos = end; // explicit version
    }
    // serialNumber INTEGER, signature SEQ, issuer SEQ, validity SEQ, subject SEQ
    for expected in [0x02u8, 0x30, 0x30, 0x30, 0x30] {
        let (tag, _, end) = der_tlv(cert, pos)?;
        if tag != expected || end > tbs_end {
            return None;
        }
        pos = end;
    }
    // subjectPublicKeyInfo
    let (tag, _, spki_end) = der_tlv(cert, pos)?;
    if tag != 0x30 || spki_end > tbs_end {
        return None;
    }
    let spki = &cert[pos..spki_end];
    if spki.len() != ED25519_SPKI_PREFIX.len() + 32 || !spki.starts_with(&ED25519_SPKI_PREFIX) {
        return None;
    }
    Some(hex::encode(&spki[ED25519_SPKI_PREFIX.len()..]))
}

// -----------------------------------------------------------------------------
// Verifiers
// -----------------------------------------------------------------------------

/// Accepts self-signed Ed25519 node certificates. The TLS 1.3 handshake signature is
/// verified against the certificate key, proving possession of the identity key. When
/// `expected` is set (dialing side), the certificate must belong to that device.
#[derive(Debug)]
struct PeerVerifier {
    algorithms: WebPkiSupportedAlgorithms,
    expected: Option<String>,
}

impl PeerVerifier {
    fn new(provider: &rustls::crypto::CryptoProvider, expected: Option<String>) -> Self {
        Self { algorithms: provider.signature_verification_algorithms, expected }
    }

    fn check(&self, end_entity: &CertificateDer<'_>) -> Result<(), rustls::Error> {
        let id = device_id_from_cert(end_entity)
            .ok_or_else(|| rustls::Error::General("peer certificate is not a Synapse Ed25519 identity".into()))?;
        if let Some(expected) = &self.expected {
            if &id != expected {
                return Err(rustls::Error::General(format!(
                    "peer identity mismatch: expected {}, got {}",
                    expected, id
                )));
            }
        }
        Ok(())
    }
}

impl ServerCertVerifier for PeerVerifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _server_name: &ServerName<'_>,
        _ocsp_response: &[u8],
        _now: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        self.check(end_entity)?;
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        verify_tls12_signature(message, cert, dss, &self.algorithms)
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        verify_tls13_signature(message, cert, dss, &self.algorithms)
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        vec![SignatureScheme::ED25519]
    }
}

impl ClientCertVerifier for PeerVerifier {
    fn root_hint_subjects(&self) -> &[DistinguishedName] {
        &[]
    }

    fn verify_client_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        _intermediates: &[CertificateDer<'_>],
        _now: UnixTime,
    ) -> Result<ClientCertVerified, rustls::Error> {
        self.check(end_entity)?;
        Ok(ClientCertVerified::assertion())
    }

    fn client_auth_mandatory(&self) -> bool {
        true
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        verify_tls12_signature(message, cert, dss, &self.algorithms)
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        verify_tls13_signature(message, cert, dss, &self.algorithms)
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        vec![SignatureScheme::ED25519]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn identity() -> NodeIdentity {
        let dir = std::env::temp_dir().join(format!("synapse_tls_{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&dir).unwrap();
        NodeIdentity::load_or_generate(&dir).unwrap()
    }

    #[test]
    fn test_certificate_proves_node_identity() {
        let id = identity();
        let tls = TlsIdentity::from_identity(&id).unwrap();
        assert_eq!(tls.device_id, id.device_id);
        assert_eq!(device_id_from_cert(&tls.cert).unwrap(), id.device_id);
        assert!(tls.server_config().is_ok());
        assert!(tls.client_config(&id.device_id).is_ok());
    }

    #[test]
    fn test_spki_extraction_rejects_spoofed_or_malformed_certs() {
        let id = identity();
        let tls = TlsIdentity::from_identity(&id).unwrap();
        // Not a certificate at all, or truncated
        assert!(device_id_from_cert(&[]).is_none());
        assert!(device_id_from_cert(&tls.cert[..tls.cert.len() - 1]).is_none());
        // Trailing junk after the certificate
        let mut padded = tls.cert.to_vec();
        padded.push(0);
        assert!(device_id_from_cert(&padded).is_none());
        // A blob that merely *contains* an Ed25519 SPKI pattern is not accepted
        let mut fake = vec![0x30, 0x2c];
        fake.extend_from_slice(&ED25519_SPKI_PREFIX);
        fake.extend_from_slice(&[7u8; 32]);
        assert!(device_id_from_cert(&fake).is_none());
    }
}
