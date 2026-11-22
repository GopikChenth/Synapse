//! Synapse Core: Decentralized File Synchronization & Conflict Resolution Engine.
//!
//! Exposes both a Rust API and C-compatible ABI exports for cross-platform deployment
//! across Linux, macOS, Windows, and Android (`libsynapse.so`).

pub mod api;
pub mod chunker;
pub mod conflict;
pub mod dag;
pub mod engine;
pub mod p2p;
pub mod state;
pub mod storage;

use std::ffi::CStr;
use std::net::SocketAddr;
use std::os::raw::{c_char, c_int};
use std::path::PathBuf;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;

use tokio::runtime::Runtime;
use tokio::sync::broadcast;
use tracing::{error, info};

use crate::api::ApiServer;
use crate::conflict::{ConflictEngine, CoreEvent};
use crate::engine::SyncEngine;
use crate::p2p::identity::NodeIdentity;
use crate::p2p::P2PManager;
use crate::storage::StorageEngine;

/// Master configuration for starting Synapse Core.
#[derive(Debug, Clone)]
pub struct SynapseConfig {
    pub home_dir: PathBuf,
    pub api_addr: SocketAddr,
    pub p2p_port: u16,
    pub bep_port: u16,
    pub discovery_port: u16,
    /// API token. When `None`, a random token is loaded from (or created in)
    /// `<home_dir>/api.token` at startup. The API never runs unauthenticated.
    pub auth_token: Option<String>,
    pub device_name: String,
    /// Browser origins allowed to call the local API (empty = none).
    pub allowed_origins: Vec<String>,
}

impl Default for SynapseConfig {
    fn default() -> Self {
        let home = std::env::var("SYNAPSE_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|_| {
                dirs_fallback().join(".synapse")
            });

        Self {
            home_dir: home,
            api_addr: SocketAddr::from(([127, 0, 0, 1], 9090)),
            p2p_port: 9190,
            bep_port: 22000,
            discovery_port: 21027,
            auth_token: None,
            device_name: get_default_device_name(),
            allowed_origins: Vec::new(),
        }
    }
}

fn dirs_fallback() -> PathBuf {
    std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .map(PathBuf::from)
        .unwrap_or_else(|_| PathBuf::from("."))
}

fn get_default_device_name() -> String {
    hostname::get()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|_| "SynapseNode".into())
}

mod hostname {
    pub fn get() -> Result<std::ffi::OsString, std::io::Error> {
        #[cfg(unix)]
        {
            let mut buf = [0u8; 256];
            let res = unsafe { libc_gethostname(buf.as_mut_ptr(), buf.len()) };
            if res == 0 {
                let len = buf.iter().position(|&b| b == 0).unwrap_or(buf.len());
                return Ok(std::ffi::OsString::from(String::from_utf8_lossy(&buf[..len]).to_string()));
            }
        }
        Ok(std::ffi::OsString::from("SynapseDevice"))
    }

    #[cfg(unix)]
    unsafe fn libc_gethostname(name: *mut u8, len: usize) -> i32 {
        extern "C" {
            fn gethostname(name: *mut u8, len: usize) -> i32;
        }
        gethostname(name, len)
    }
}

/// File name (inside the Synapse home directory) holding the local API token.
pub const API_TOKEN_FILE: &str = "api.token";

/// Returns the path of the API token file for a given home directory.
pub fn api_token_path(home_dir: &std::path::Path) -> PathBuf {
    home_dir.join(API_TOKEN_FILE)
}

/// Loads the API token from `<home_dir>/api.token`, creating it with a fresh
/// random 256-bit hex token (file mode 0600 on Unix) if it does not exist yet.
pub fn load_or_create_api_token(home_dir: &std::path::Path) -> std::io::Result<String> {
    use std::io::Write;

    let path = api_token_path(home_dir);
    if let Ok(existing) = std::fs::read_to_string(&path) {
        let token = existing.trim().to_string();
        if !token.is_empty() {
            return Ok(token);
        }
    }

    std::fs::create_dir_all(home_dir)?;
    let mut bytes = [0u8; 32];
    rand::RngCore::fill_bytes(&mut rand::rngs::OsRng, &mut bytes);
    let token = hex::encode(bytes);

    let mut options = std::fs::OpenOptions::new();
    options.write(true).create(true).truncate(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options.open(&path)?;
    file.write_all(token.as_bytes())?;
    file.sync_all()?;
    Ok(token)
}

/// Reads an existing API token without creating one (used by CLI clients).
pub fn read_api_token(home_dir: &std::path::Path) -> Option<String> {
    std::fs::read_to_string(api_token_path(home_dir))
        .ok()
        .map(|t| t.trim().to_string())
        .filter(|t| !t.is_empty())
}

// =============================================================================
// ENGINE LIFECYCLE (engine_lifecycle state machine)
// =============================================================================

/// Process-wide engine lifecycle and shutdown signal. One engine runs per process; the
/// daemon, the control API, and the JNI exports all go through this.
pub struct EngineControl {
    state: std::sync::atomic::AtomicU8,
    shutdown: tokio::sync::watch::Sender<bool>,
}

static ENGINE_CONTROL: std::sync::OnceLock<EngineControl> = std::sync::OnceLock::new();

/// The process-wide [`EngineControl`].
pub fn engine_control() -> &'static EngineControl {
    ENGINE_CONTROL.get_or_init(|| EngineControl {
        state: std::sync::atomic::AtomicU8::new(state::EngineLifecycleState::Stopped.as_u8()),
        shutdown: tokio::sync::watch::channel(false).0,
    })
}

impl EngineControl {
    /// Current lifecycle state.
    pub fn state(&self) -> state::EngineLifecycleState {
        state::EngineLifecycleState::from_u8(self.state.load(Ordering::SeqCst))
    }

    /// Guarded, race-free transition (compare-and-swap).
    #[allow(clippy::result_large_err)]
    pub fn transition(
        &self,
        next: state::EngineLifecycleState,
    ) -> Result<state::EngineLifecycleState, state::StateTransitionError> {
        use state::StateMachine;
        loop {
            let current = self.state();
            current.transition("engine", next)?;
            if self
                .state
                .compare_exchange(current.as_u8(), next.as_u8(), Ordering::SeqCst, Ordering::SeqCst)
                .is_ok()
            {
                if current != next {
                    info!("Engine lifecycle: {} -> {}", current, next);
                }
                return Ok(next);
            }
        }
    }

    /// Requests a graceful shutdown. Idempotent: returns the resulting state, and is a no-op
    /// when the engine is already stopping, stopped, or failed.
    pub fn request_shutdown(&self) -> state::EngineLifecycleState {
        use state::EngineLifecycleState as S;
        match self.state() {
            S::Starting | S::Running => {
                let _ = self.transition(S::Stopping);
                self.shutdown.send_replace(true);
                S::Stopping
            }
            other => other,
        }
    }

    /// `true` once shutdown has been requested for the current run.
    pub fn is_shutdown_requested(&self) -> bool {
        *self.shutdown.borrow()
    }

    /// Resolves when shutdown is requested.
    pub async fn shutdown_requested(&self) {
        let mut rx = self.shutdown.subscribe();
        while !*rx.borrow_and_update() {
            if rx.changed().await.is_err() {
                return;
            }
        }
    }

    /// Claims the engine for a new run. Strict (not idempotent): only one engine may be
    /// starting or running per process.
    #[allow(clippy::result_large_err)]
    fn begin_start(&self) -> Result<(), state::StateTransitionError> {
        use state::{EngineLifecycleState as S, StateMachine};
        for from in [S::Stopped, S::Failed] {
            if self
                .state
                .compare_exchange(from.as_u8(), S::Starting.as_u8(), Ordering::SeqCst, Ordering::SeqCst)
                .is_ok()
            {
                info!("Engine lifecycle: {} -> {}", from, S::Starting);
                self.shutdown.send_replace(false);
                return Ok(());
            }
        }
        Err(self.state().rejection("engine", S::Starting.name()))
    }
}

/// Active instance of Synapse Core engine.
pub struct SynapseCore {
    config: SynapseConfig,
    storage: StorageEngine,
    sync_engine: SyncEngine,
    p2p_manager: Arc<P2PManager>,
    event_sender: broadcast::Sender<CoreEvent>,
    peer_count: Arc<AtomicUsize>,
}

impl SynapseCore {
    /// Initializes and prepares the Synapse Core environment.
    pub fn init(config: SynapseConfig) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        let control = engine_control();
        control.begin_start()?;
        match Self::init_inner(config) {
            Ok(core) => Ok(core),
            Err(e) => {
                let _ = control.transition(state::EngineLifecycleState::Failed);
                Err(e)
            }
        }
    }

    fn init_inner(mut config: SynapseConfig) -> Result<Self, Box<dyn std::error::Error + Send + Sync>> {
        std::fs::create_dir_all(&config.home_dir)?;

        // The control API always requires a token. Use the one supplied, or load/create
        // `<home>/api.token` so the UI (desktop or Android) can read it from disk.
        match config.auth_token.as_deref().map(str::trim) {
            Some(t) if !t.is_empty() => {
                if t.len() < 16 {
                    tracing::warn!("Configured API token is shorter than 16 characters; use a longer random token");
                }
            }
            _ => {
                config.auth_token = Some(load_or_create_api_token(&config.home_dir)?);
                info!("API token file: {}", api_token_path(&config.home_dir).display());
            }
        }

        let identity = NodeIdentity::load_or_generate(&config.home_dir)?;
        info!("Synapse Core Device ID: {}", identity.device_id);

        let storage = StorageEngine::new(&config.home_dir)?;
        let (event_sender, _) = broadcast::channel(1024);
        let peer_count = Arc::new(AtomicUsize::new(0));

        let conflict_engine = Arc::new(ConflictEngine::new(
            storage.clone(),
            event_sender.clone(),
            identity.device_id.clone(),
        ));

        let sync_engine = SyncEngine::new(
            storage.clone(),
            conflict_engine,
            event_sender.clone(),
            identity.device_id.clone(),
        );

        let p2p_manager = Arc::new(P2PManager::new(
            identity,
            config.device_name.clone(),
            config.p2p_port,
            config.bep_port,
            sync_engine.clone(),
            peer_count.clone(),
            event_sender.clone(),
        ));

        Ok(Self {
            config,
            storage,
            sync_engine,
            p2p_manager,
            event_sender,
            peer_count,
        })
    }

    pub fn event_sender(&self) -> &broadcast::Sender<CoreEvent> {
        &self.event_sender
    }

    /// Spawns API server, discovery, watchers, and QUIC listener on Tokio runtime, and runs
    /// until [`EngineControl::request_shutdown`] is called (API, signal, or JNI).
    ///
    /// Lifecycle: `starting → running → stopping → stopped`, or `→ failed` on error.
    pub async fn run(self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let control = engine_control();
        let result = self.run_inner().await;
        let _ = match &result {
            Ok(()) => {
                if control.state() != state::EngineLifecycleState::Stopping {
                    let _ = control.transition(state::EngineLifecycleState::Stopping);
                }
                control.transition(state::EngineLifecycleState::Stopped)
            }
            Err(_) => control.transition(state::EngineLifecycleState::Failed),
        };
        result
    }

    async fn run_inner(self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        info!("Starting Synapse Core engine...");
        let control = engine_control();

        // 1. Start P2P local discovery
        self.p2p_manager.start_discovery(self.config.discovery_port);

        // 2. Scan and watch existing folders. Watchers live in the engine's registry (started,
        //    stopped on pause/delete, cleared on shutdown); no threads are parked.
        if let Ok(folders) = self.storage.list_folders() {
            for f in folders {
                if !f.is_paused {
                    let engine = self.sync_engine.clone();
                    tokio::task::spawn_blocking(move || {
                        let _ = engine.index_folder(&f.folder_id);
                        engine.start_watching(&f.folder_id);
                    });
                }
            }
        }

        // 3. Spawn QUIC server
        let p2p = self.p2p_manager.clone();
        tokio::spawn(async move {
            if let Err(e) = p2p.run_server().await {
                error!("QUIC P2P error: {}", e);
            }
        });

        // 4. Start local API server; serve until shutdown is requested
        let api_server = ApiServer::new(
            self.sync_engine.clone(),
            self.config.api_addr,
            self.config.auth_token.clone(),
            self.peer_count.clone(),
            self.p2p_manager.clone(),
            self.config.allowed_origins.clone(),
        );

        api_server
            .run_until(control.shutdown_requested(), || {
                let _ = engine_control().transition(state::EngineLifecycleState::Running);
            })
            .await?;
        self.sync_engine.stop_all_watchers();
        info!("Synapse Core stopped");
        Ok(())
    }
}

// =============================================================================
// C / JNI ABI EXPORTS FOR ANDROID (libsynapse.so)
// =============================================================================

/// Starts the Synapse Core daemon from C / JNI.
///
/// Returns `0` on success or if already starting/running, `-1` null `home_dir`, `-2` invalid
/// UTF-8, `-3` runtime creation failed, `-4` engine init failed, `-5` a previous instance is
/// still stopping (retry shortly).
///
/// # Safety
/// `home_dir` must be a valid, null-terminated UTF-8 C string.
#[no_mangle]
pub unsafe extern "C" fn synapse_start(
    home_dir: *const c_char,
    api_port: c_int,
    p2p_port: c_int,
) -> c_int {
    use state::EngineLifecycleState as S;
    match engine_control().state() {
        S::Starting | S::Running => return 0, // Already running
        S::Stopping => return -5,
        S::Stopped | S::Failed => {}
    }

    if home_dir.is_null() {
        return -1;
    }

    let home_str = match CStr::from_ptr(home_dir).to_str() {
        Ok(s) => s,
        Err(_) => return -2,
    };

    let mut config = SynapseConfig::default();
    config.home_dir = PathBuf::from(home_str);
    if api_port > 0 {
        config.api_addr = SocketAddr::from(([127, 0, 0, 1], api_port as u16));
    }
    if p2p_port > 0 {
        config.p2p_port = p2p_port as u16;
    }

    let rt = match Runtime::new() {
        Ok(r) => r,
        Err(_) => return -3,
    };

    match SynapseCore::init(config) {
        Ok(core) => {
            std::thread::spawn(move || {
                let _ = rt.block_on(core.run());
                // Background tasks (discovery, QUIC) are cancelled here; don't wait forever.
                rt.shutdown_timeout(std::time::Duration::from_secs(2));
            });
            0
        }
        Err(_) => -4,
    }
}

/// Checks if Synapse Core is active (starting, running, or still stopping).
#[no_mangle]
pub extern "C" fn synapse_is_running() -> c_int {
    use state::EngineLifecycleState as S;
    match engine_control().state() {
        S::Starting | S::Running | S::Stopping => 1,
        S::Stopped | S::Failed => 0,
    }
}

/// Current engine lifecycle state: `0` stopped, `1` starting, `2` running, `3` stopping,
/// `4` failed (see the `engine_lifecycle` state machine).
#[no_mangle]
pub extern "C" fn synapse_state() -> c_int {
    engine_control().state().as_u8() as c_int
}

/// Requests a graceful stop of the Synapse Core daemon. Returns immediately; poll
/// `synapse_is_running()` / `synapse_state()` to observe completion.
#[no_mangle]
pub extern "C" fn synapse_stop() -> c_int {
    engine_control().request_shutdown();
    0
}
