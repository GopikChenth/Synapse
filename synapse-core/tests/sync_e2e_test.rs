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
