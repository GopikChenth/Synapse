//! Two (and three) real engines syncing over mutually-authenticated QUIC on loopback.

use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::atomic::AtomicUsize;
use std::sync::Arc;
use std::time::{Duration, Instant};

use synapse_core::conflict::ConflictEngine;
use synapse_core::engine::SyncEngine;
use synapse_core::p2p::{NodeIdentity, P2PManager, P2PMessage};
use synapse_core::storage::{DeviceRecord, FolderRecord, StorageEngine};

struct Node {
    home: PathBuf,
    folder_dir: PathBuf,
    storage: StorageEngine,
    engine: SyncEngine,
    p2p: Arc<P2PManager>,
    id: String,
    addr: SocketAddr,
}

impl Node {
    async fn start(name: &str) -> Node {
        let home = std::env::temp_dir().join(format!("synapse_e2e_{}_{}", name, uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&home).unwrap();
        let storage = StorageEngine::new(&home).unwrap();
        let identity = NodeIdentity::load_or_generate(&home).unwrap();
        let id = identity.device_id.clone();
        let (tx, _) = tokio::sync::broadcast::channel(1024);
        let conflicts = Arc::new(ConflictEngine::new(storage.clone(), tx.clone(), id.clone()));
        let engine = SyncEngine::new(storage.clone(), conflicts, tx.clone(), id.clone());
        let p2p = Arc::new(P2PManager::new(
            identity,
            name.to_string(),
            0,
            0,
            engine.clone(),
            Arc::new(AtomicUsize::new(0)),
            tx,
        ));
        let server = p2p.clone();
        tokio::spawn(async move {
            let _ = server.run_server().await;
        });
        let start = Instant::now();
        let port = loop {
            if let Some(a) = p2p.local_quic_addr().await {
                break a.port();
            }
            assert!(start.elapsed() < Duration::from_secs(5), "QUIC endpoint never started");
            tokio::time::sleep(Duration::from_millis(20)).await;
        };
        let folder_dir = home.join("docs");
        std::fs::create_dir_all(&folder_dir).unwrap();
        storage.add_folder(&FolderRecord::new("docs", folder_dir.to_string_lossy())).unwrap();
        Node { home, folder_dir, storage, engine, p2p, id, addr: SocketAddr::from(([127, 0, 0, 1], port)) }
    }

    /// Pairs with `other` and shares the "docs" folder with it.
    fn pair_and_share(&self, other: &Node) {
        self.storage
            .add_device(&DeviceRecord {
                device_id: other.id.clone(),
                name: "peer".into(),
                addresses: vec![other.addr.to_string()],
                is_paused: false,
                is_trusted: true,
                introducer: false,
                cert_name: None,
                created_at: 0,
            })
            .unwrap();
        self.storage.share_folder_with_device("docs", &other.id).unwrap();
    }

    fn write(&self, rel: &str, data: &[u8]) {
        let p = self.folder_dir.join(rel);
        std::fs::create_dir_all(p.parent().unwrap()).unwrap();
        std::fs::write(&p, data).unwrap();
    }

    fn read(&self, rel: &str) -> Option<Vec<u8>> {
        std::fs::read(self.folder_dir.join(rel)).ok()
    }
}

impl Drop for Node {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.home);
    }
}

async fn eventually(what: &str, mut check: impl FnMut() -> bool) {
    let start = Instant::now();
    while !check() {
        assert!(start.elapsed() < Duration::from_secs(15), "timed out waiting for: {}", what);
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}

fn pseudo_random(len: usize, seed: u64) -> Vec<u8> {
    let mut x = seed.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
    (0..len)
        .map(|_| {
            x ^= x << 13;
            x ^= x >> 7;
            x ^= x << 17;
            (x >> 24) as u8
        })
        .collect()
}

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn test_two_nodes_sync_end_to_end() {
    let _ = tracing_subscriber::fmt().with_test_writer().with_env_filter("info").try_init();
    let a = Node::start("alice").await;
    let b = Node::start("bob").await;
    a.pair_and_share(&b);
    b.pair_and_share(&a);

    // Content on A before the devices ever talk: small, multi-chunk, and nested files
    let big = pseudo_random(3 * 1024 * 1024 + 12345, 7);
    a.write("hello.txt", b"hello from alice\n");
    a.write("media/big.bin", &big);
    a.write("notes/deep/plan.md", b"# Plan\n- one\n- two\n");
    a.engine.index_folder("docs").unwrap();

    // 1. Connect (mutual TLS) -> both sides pull what they are missing
    a.p2p.ensure_connected(&b.id, b.addr).await.unwrap();
    assert!(a.p2p.is_connected_to(&b.id).await);
    eventually("initial files on bob", || {
        b.read("hello.txt").as_deref() == Some(b"hello from alice\n".as_slice())
            && b.read("media/big.bin").as_deref() == Some(big.as_slice())
            && b.read("notes/deep/plan.md").is_some()
    })
    .await;
    // Same revision ids on both sides (no re-revisioning on receipt)
    for rel in ["hello.txt", "media/big.bin", "notes/deep/plan.md"] {
        let ra = a.storage.get_file("docs", rel).unwrap().unwrap().current_revision;
        let rb = b.storage.get_file("docs", rel).unwrap().unwrap().current_revision;
        assert_eq!(ra, rb, "{} revision differs", rel);
    }

    // 2. Live push A -> B on local change
    a.write("hello.txt", b"hello again from alice\n");
    a.engine.index_single_file("docs", "hello.txt").unwrap();
    eventually("alice's edit on bob", || {
        b.read("hello.txt").as_deref() == Some(b"hello again from alice\n".as_slice())
    })
    .await;

    // 3. Live push B -> A (same connection, other direction)
    b.write("from-bob.txt", b"bob was here\n");
    b.engine.index_single_file("docs", "from-bob.txt").unwrap();
    eventually("bob's file on alice", || a.read("from-bob.txt").as_deref() == Some(b"bob was here\n".as_slice()))
        .await;

    // 4. Re-indexing received files must not create new revisions (no ping-pong)
    let before = b.storage.get_file("docs", "hello.txt").unwrap().unwrap().current_revision;
    assert!(!b.engine.index_single_file("docs", "hello.txt").unwrap());
    assert_eq!(b.storage.get_file("docs", "hello.txt").unwrap().unwrap().current_revision, before);

    // 5. Concurrent edits while B ignores A (paused), then catch up: no edit may be lost
    b.storage.set_device_paused(&a.id, true).unwrap();
    a.write("notes/deep/plan.md", b"# Plan\n- one (alice)\n- two\n");
    a.engine.index_single_file("docs", "notes/deep/plan.md").unwrap();
    b.write("notes/deep/plan.md", b"# Plan\n- one\n- two (bob)\n");
    b.engine.index_single_file("docs", "notes/deep/plan.md").unwrap();
    tokio::time::sleep(Duration::from_millis(300)).await;
    assert_eq!(b.read("notes/deep/plan.md").unwrap(), b"# Plan\n- one\n- two (bob)\n", "paused peer applied a push");
    b.storage.set_device_paused(&a.id, false).unwrap();
    b.p2p.sync_folder_from(&a.id, "docs").await.unwrap();

    let merged = String::from_utf8(b.read("notes/deep/plan.md").unwrap()).unwrap();
    let quarantined = !b.storage.list_pending_conflicts().unwrap().is_empty();
    assert!(merged.contains("two (bob)"), "bob's edit was lost: {}", merged);
    assert!(
        merged.contains("one (alice)") || quarantined,
        "alice's edit was neither merged nor quarantined: {}",
        merged
    );
}

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn test_peer_identity_cannot_be_spoofed() {
    let a = Node::start("alice").await;
    let b = Node::start("bob").await;
    let m = Node::start("mallory").await;
    a.pair_and_share(&b);
    b.pair_and_share(&a);
    a.write("secret.txt", b"for bob only\n");
    a.engine.index_folder("docs").unwrap();

    // Dialing "bob" but reaching mallory's address: the pinned identity check refuses it.
    let err = a.p2p.ensure_connected(&b.id, m.addr).await;
    assert!(err.is_err(), "connected to the wrong identity");
    assert!(!a.p2p.is_connected_to(&b.id).await);

    // Mallory connects to alice with her own (genuine) identity...
    m.pair_and_share(&a); // mallory trusts alice locally; alice does NOT trust mallory
    m.p2p.ensure_connected(&a.id, a.addr).await.unwrap();

    // ...claims to be bob in a Hello: ignored, no pairing request is recorded for bob's id
    let reply = m
        .p2p
        .request(
            &a.id,
            P2PMessage::Hello { device_id: b.id.clone(), device_name: "bob".into(), folders: vec![], cluster_devices: vec![] },
        )
        .await
        .unwrap();
    assert!(reply.is_none());

    // ...asks for alice's folder and chunks: nothing, she is not paired or shared
    let idx = m.p2p.request(&a.id, P2PMessage::IndexRequest { folder_id: "docs".into() }).await.unwrap();
    assert!(idx.is_none());
    let file = a.storage.get_file("docs", "secret.txt").unwrap().unwrap();
    let chunk = a.storage.get_revision_chunks(&file.current_revision).unwrap()[0].clone();
    let got = m.p2p.request(&a.id, P2PMessage::ChunkRequest { chunk_hash: chunk }).await.unwrap();
    assert!(got.is_none());

    // Alice recorded mallory as a pending request under her *proven* id, nothing else
    let pending = a.storage.list_pending_devices().unwrap();
    assert_eq!(pending.len(), 1);
    assert_eq!(pending[0].device_id, m.id);
    assert!(m.read("secret.txt").is_none());
}

/// Regression: removing a connected device must drop its live connection and presence.
/// Previously the connection stayed open and the device kept being reported online, so
/// apps showed a deleted device as still connected.
#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn test_removed_device_is_disconnected_and_forgotten() {
    let _ = tracing_subscriber::fmt().with_test_writer().with_env_filter("info").try_init();
    let a = Node::start("alice").await;
    let b = Node::start("bob").await;
    a.pair_and_share(&b);
    b.pair_and_share(&a);

    a.p2p.ensure_connected(&b.id, b.addr).await.unwrap();
    assert!(a.p2p.is_connected_to(&b.id).await);
    assert!(a.p2p.list_connected_peers().await.contains_key(&b.id));

    // What DELETE /api/v1/devices/:id does.
    a.storage.remove_device(&b.id).unwrap();
    a.p2p.forget_device(&b.id).await;

    assert!(!a.p2p.is_connected_to(&b.id).await, "connection must be closed");
    assert!(!a.p2p.list_known_peers().await.contains_key(&b.id), "presence must be forgotten");
    // The presence sweep must not bring it back from a lingering connection.
    a.p2p.sweep_presence().await;
    assert!(!a.p2p.list_connected_peers().await.contains_key(&b.id));

    // Bob notices the closed connection.
    let start = Instant::now();
    while b.p2p.is_connected_to(&a.id).await {
        assert!(start.elapsed() < Duration::from_secs(15), "peer should see the connection closed");
        tokio::time::sleep(Duration::from_millis(50)).await;
    }

    // Bob still has Alice configured; when he dials again he is only a pairing request.
    b.p2p.ensure_connected(&a.id, a.addr).await.unwrap();
    let start = Instant::now();
    while a.storage.list_pending_devices().unwrap().is_empty() {
        assert!(start.elapsed() < Duration::from_secs(15), "re-dial should create a pending request");
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    assert!(a.storage.list_devices().unwrap().is_empty(), "removed device must not be re-added");
    assert!(!a.p2p.list_connected_peers().await.contains_key(&b.id), "unpaired peer is not online");
}

/// Regression: sharing a folder must reach the other device as a pending invitation (the
/// "device X wants to share folder Y" prompt), and accepting it must pull the files.
/// Previously Hello always carried an empty folder list, so no invitation ever arrived.
#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn test_folder_invitation_reaches_peer_and_accepting_syncs_it() {
    let _ = tracing_subscriber::fmt().with_test_writer().with_env_filter("info").try_init();
    let a = Node::start("alice").await;
    let b = Node::start("bob").await;
    a.pair_and_share(&b);
    b.pair_and_share(&a);
    a.p2p.ensure_connected(&b.id, b.addr).await.unwrap();

    // Alice shares a folder Bob does not have, while already connected.
    let photos = a.home.join("photos");
    std::fs::create_dir_all(photos.join("2026")).unwrap();
    std::fs::write(photos.join("2026/beach.txt"), b"sand").unwrap();
    a.storage.add_folder(&FolderRecord::new("photos", photos.to_string_lossy())).unwrap();
    a.engine.index_folder("photos").unwrap();
    a.storage.share_folder_with_device("photos", &b.id).unwrap();
    a.p2p.announce_folders().await;

    eventually("invitation on bob", || {
        b.storage.list_pending_folders().unwrap().iter().any(|p| p.folder_id == "photos" && p.device_id == a.id)
    })
    .await;
    // Folders already shared both ways are not offered again.
    assert!(!b.storage.list_pending_folders().unwrap().iter().any(|p| p.folder_id == "docs"));
    // Announcing again does not duplicate the invitation.
    a.p2p.announce_folders().await;
    assert_eq!(b.storage.list_pending_folders().unwrap().len(), 1);

    // Changes pushed for a folder Bob has not accepted are not applied anywhere.
    a.storage.share_folder_with_device("photos", &b.id).unwrap();
    let inbox = b.home.join("photos-in");
    assert!(!inbox.exists());

    // Bob accepts into his own directory and pulls Alice's files.
    std::fs::create_dir_all(&inbox).unwrap();
    b.storage.accept_pending_folder("photos", &a.id, &inbox.to_string_lossy()).unwrap();
    b.p2p.sync_folder_from(&a.id, "photos").await.unwrap();
    eventually("photo on bob", || std::fs::read(inbox.join("2026/beach.txt")).ok().as_deref() == Some(b"sand".as_slice()))
        .await;
    assert!(b.storage.list_pending_folders().unwrap().is_empty());
}
