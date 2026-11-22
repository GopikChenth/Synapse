//! Engine lifecycle (`engine_lifecycle` state machine), end to end.
//!
//! Lives in its own test binary because the engine lifecycle is process-wide and must not
//! race the API tests in `integration_test.rs`.

use std::net::{SocketAddr, TcpListener};
use std::time::{Duration, Instant};

use synapse_core::state::{EngineLifecycleState, StateMachine};
use synapse_core::{engine_control, SynapseConfig, SynapseCore};

const TOKEN: &str = "lifecycle-test-token-0123456789abcdef";

fn free_port() -> u16 {
    TcpListener::bind("127.0.0.1:0").unwrap().local_addr().unwrap().port()
}

fn config(home: &std::path::Path) -> SynapseConfig {
    SynapseConfig {
        home_dir: home.to_path_buf(),
        api_addr: SocketAddr::from(([127, 0, 0, 1], free_port())),
        p2p_port: free_port(),
        bep_port: free_port(),
        discovery_port: free_port(),
        auth_token: Some(TOKEN.into()),
        device_name: "lifecycle-test".into(),
        allowed_origins: Vec::new(),
    }
}

/// Minimal HTTP/1.1 request over a raw socket (keeps the test free of extra dependencies).
async fn http(addr: SocketAddr, method: &str, path: &str) -> (u16, String) {
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    let mut stream = tokio::net::TcpStream::connect(addr).await.unwrap();
    let req = format!(
        "{} {} HTTP/1.1\r\nHost: {}\r\nAuthorization: Bearer {}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
        method, path, addr, TOKEN
    );
    stream.write_all(req.as_bytes()).await.unwrap();
    let mut buf = String::new();
    stream.read_to_string(&mut buf).await.unwrap();
    let code = buf.split_whitespace().nth(1).unwrap_or("0").parse().unwrap_or(0);
    let body = buf.split("\r\n\r\n").nth(1).unwrap_or("").to_string();
    (code, body)
}

async fn wait_for(state: EngineLifecycleState, timeout: Duration) {
    let start = Instant::now();
    while engine_control().state() != state {
        assert!(start.elapsed() < timeout, "engine never reached '{}' (is '{}')", state, engine_control().state());
        tokio::time::sleep(Duration::from_millis(20)).await;
    }
}

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn test_engine_lifecycle_start_shutdown_restart() {
    let home = std::env::temp_dir().join(format!("synapse_lifecycle_{}", uuid::Uuid::new_v4()));
    let ctl = engine_control();
    assert_eq!(ctl.state(), EngineLifecycleState::Stopped);
    // Shutdown of a stopped engine is a harmless no-op
    assert_eq!(ctl.request_shutdown(), EngineLifecycleState::Stopped);

    for round in 0..2 {
        let cfg = config(&home);
        let api = cfg.api_addr;

        let core = SynapseCore::init(cfg).unwrap();
        assert_eq!(ctl.state(), EngineLifecycleState::Starting);
        // A second engine cannot start while one is starting/running
        assert!(SynapseCore::init(config(&home)).is_err());

        let run = tokio::spawn(core.run());
        wait_for(EngineLifecycleState::Running, Duration::from_secs(5)).await;

        let (code, body) = http(api, "GET", "/api/v1/system/status").await;
        assert_eq!(code, 200, "round {}: {}", round, body);
        let status: serde_json::Value = serde_json::from_str(&body).unwrap();
        assert_eq!(status["engine_state"], "running");

        // Graceful shutdown through the API (previously a no-op that returned 200)
        let (code, body) = http(api, "POST", "/api/v1/system/shutdown").await;
        assert_eq!(code, 200);
        let resp: serde_json::Value = serde_json::from_str(&body).unwrap();
        assert_eq!(resp["engine_state"], "stopping");

        let result = tokio::time::timeout(Duration::from_secs(5), run).await;
        assert!(result.is_ok(), "round {}: engine did not stop within 5s", round);
        result.unwrap().unwrap().unwrap();
        assert_eq!(ctl.state(), EngineLifecycleState::Stopped);
        assert_eq!(ctl.state().name(), "stopped");

        // The listener is gone
        assert!(tokio::net::TcpStream::connect(api).await.is_err());
    }

    let _ = std::fs::remove_dir_all(&home);
}
