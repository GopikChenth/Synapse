//! Local Control Plane: Embedded REST and WebSocket API Server.
//!
//! Exposes IPC interfaces for the Compose Multiplatform UI:
//! - System health & status
//! - Cluster device management and pairing handshakes
//! - Folder management, sharing matrix, status, completion, and ignore rules
//! - Real-time connection speeds and metrics
//! - Active conflict inspection (base, local, remote, 3-way diff)
//! - User conflict resolution commands
//! - Real-time WebSocket event streaming
//! - Full compatibility route aliases for REST API clients (`/rest/*`)

use std::collections::{HashMap, VecDeque};
use std::fs;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::{Duration, Instant};

use axum::{
    extract::{
        path::Path as AxumPath,
        ws::{Message, WebSocket, WebSocketUpgrade},
        Query, Request, State,
    },
    http::{header, HeaderName, HeaderValue, Method, StatusCode},
    middleware::{self, Next},
    response::{IntoResponse, Response},
    routing::{delete, get, patch, post},
    Json, Router,
};
use futures::StreamExt;
use serde::{Deserialize, Serialize};
use tokio::sync::broadcast;
use tower_http::cors::{AllowOrigin, CorsLayer};
use tracing::{info, warn};

use crate::conflict::diff3::{self, DiffHunk};
use crate::conflict::{CoreEvent, ResolveRequest};
use crate::engine::SyncEngine;
use crate::storage::{
    DeviceRecord, FolderRecord, PeerConnectionMetrics,
};

/// Application shared state injected into Axum handlers.
#[derive(Clone)]
pub struct AppState {
    pub start_time: Instant,
    pub sync_engine: SyncEngine,
    /// API token every request must present. `None` (or empty) means the API
    /// fails closed and rejects every request.
    pub auth_token: Option<String>,
    pub peer_count: Arc<std::sync::atomic::AtomicUsize>,
    pub p2p_manager: Arc<crate::p2p::P2PManager>,
    /// Browser origins allowed to call the API (e.g. `http://localhost:8080` for the
    /// web UI). Empty = no cross-origin access at all.
    pub allowed_origins: Vec<String>,
    /// Recent events, served to REST-style long-poll clients (`GET /rest/events`).
    pub event_log: Arc<EventLog>,
}

// =============================================================================
// EVENT LOG (REST-compatible long-poll for GET /rest/events)
// =============================================================================

/// Number of recent events kept in memory for long-poll clients.
pub const EVENT_LOG_CAPACITY: usize = 1000;

/// Ring buffer of recent events in the REST event format:
/// `{"id", "globalID", "type", "time", "data"}`.
pub struct EventLog {
    inner: std::sync::Mutex<EventLogInner>,
    latest_id: tokio::sync::watch::Sender<u64>,
}

struct EventLogInner {
    next_id: u64,
    events: VecDeque<(u64, String, serde_json::Value)>,
}

impl EventLog {
    pub fn new() -> Arc<Self> {
        let (latest_id, _) = tokio::sync::watch::channel(0);
        Arc::new(Self {
            inner: std::sync::Mutex::new(EventLogInner { next_id: 1, events: VecDeque::new() }),
            latest_id,
        })
    }

    /// Records an engine event, converting it to the REST event shape.
    pub fn record(&self, event: &CoreEvent) {
        let (event_type, data) = to_rest_event(event);
        let id = {
            let mut inner = self.inner.lock().unwrap_or_else(|e| e.into_inner());
            let id = inner.next_id;
            inner.next_id += 1;
            let json = serde_json::json!({
                "id": id,
                "globalID": id,
                "type": event_type,
                "time": chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true),
                "data": data,
            });
            inner.events.push_back((id, event_type, json));
            while inner.events.len() > EVENT_LOG_CAPACITY {
                inner.events.pop_front();
            }
            id
        };
        self.latest_id.send_replace(id);
    }

    /// Events with `id > since`, optionally filtered by type, keeping the newest `limit`
    /// (0 = no limit). Oldest first, as in the REST API.
    pub fn since(&self, since: u64, limit: usize, types: Option<&[String]>) -> Vec<serde_json::Value> {
        let inner = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let mut out: Vec<serde_json::Value> = inner
            .events
            .iter()
            .filter(|(id, ty, _)| *id > since && types.is_none_or(|t| t.iter().any(|x| x == ty)))
            .map(|(_, _, json)| json.clone())
            .collect();
        if limit > 0 && out.len() > limit {
            out.drain(..out.len() - limit);
        }
        out
    }

    fn subscribe(&self) -> tokio::sync::watch::Receiver<u64> {
        self.latest_id.subscribe()
    }

    /// Spawns a task that records every event published on `sender`.
    pub fn spawn_recorder(self: &Arc<Self>, sender: &broadcast::Sender<CoreEvent>) {
        let mut rx = sender.subscribe();
        let log = self.clone();
        tokio::spawn(async move {
            loop {
                match rx.recv().await {
                    Ok(event) => log.record(&event),
                    Err(broadcast::error::RecvError::Lagged(n)) => {
                        warn!("Event log lagged; {} events were not recorded", n);
                    }
                    Err(broadcast::error::RecvError::Closed) => break,
                }
            }
        });
    }
}

/// Maps an engine event to a REST event type and `data` object. File activity uses
/// the standard names (`ItemStarted`, `ItemFinished`, `LocalChangeDetected`) so existing
/// REST clients understand it; everything else keeps its Synapse name.
fn to_rest_event(event: &CoreEvent) -> (String, serde_json::Value) {
    use serde_json::json;
    match event {
        CoreEvent::ItemStarted { folder_id, relative_path, action } => (
            "ItemStarted".into(),
            json!({ "folder": folder_id, "item": relative_path, "action": action, "type": "file" }),
        ),
        CoreEvent::ItemFinished { folder_id, relative_path, action, error } => (
            "ItemFinished".into(),
            json!({ "folder": folder_id, "item": relative_path, "action": action, "type": "file", "error": error }),
        ),
        CoreEvent::FileChanged { folder_id, relative_path, revision } => (
            "LocalChangeDetected".into(),
            json!({
                "folder": folder_id, "folderID": folder_id, "path": relative_path,
                "action": "modified", "type": "file", "revision": revision,
            }),
        ),
        other => {
            let value = serde_json::to_value(other).unwrap_or_default();
            let event_type = value
                .get("event")
                .and_then(|v| v.as_str())
                .unwrap_or("UNKNOWN")
                .to_string();
            let data = match value.get("data") {
                Some(d) if d.is_object() => d.clone(),
                Some(d) if !d.is_null() => json!({ "value": d }),
                _ => json!({}),
            };
            (event_type, data)
        }
    }
}

#[derive(Debug, Deserialize)]
struct EventsQuery {
    since: Option<u64>,
    limit: Option<usize>,
    /// Seconds to wait for new events (default 60, max 300).
    timeout: Option<u64>,
    /// Comma-separated event types to include.
    events: Option<String>,
}

// =============================================================================
// AUTHENTICATION & ORIGIN CHECKS
// =============================================================================

/// Header name accepted as an alternative to `Authorization: Bearer <token>`.
pub const API_KEY_HEADER: &str = "x-api-key";

/// Compares two byte strings without short-circuiting on the first mismatch.
fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    a.iter().zip(b).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

/// Pulls the API token from `Authorization: Bearer`, `X-API-Key`, or — for the
/// WebSocket event stream only, because browsers cannot set headers on upgrades —
/// a `token` / `api_key` query parameter.
fn extract_token(req: &Request) -> Option<String> {
    let headers = req.headers();
    if let Some(value) = headers.get(header::AUTHORIZATION).and_then(|v| v.to_str().ok()) {
        let value = value.trim();
        if value.len() > 7 && value[..7].eq_ignore_ascii_case("bearer ") {
            return Some(value[7..].trim().to_string());
        }
    }
    if let Some(value) = headers.get(API_KEY_HEADER).and_then(|v| v.to_str().ok()) {
        return Some(value.trim().to_string());
    }
    if req.uri().path().ends_with("/events") {
        if let Some(query) = req.uri().query() {
            for pair in query.split('&') {
                if let Some((key, value)) = pair.split_once('=') {
                    if key == "token" || key == "api_key" {
                        return Some(value.to_string());
                    }
                }
            }
        }
    }
    None
}

/// Middleware: rejects requests from disallowed browser origins and requests
/// without a valid API token.
async fn require_api_token(State(state): State<AppState>, req: Request, next: Next) -> Response {
    if let Some(origin) = req.headers().get(header::ORIGIN).and_then(|v| v.to_str().ok()) {
        if !state.allowed_origins.iter().any(|o| o == origin) {
            warn!("Rejected API request from disallowed origin {}", origin);
            return (StatusCode::FORBIDDEN, "origin not allowed").into_response();
        }
    }

    let expected = match state.auth_token.as_deref() {
        Some(t) if !t.is_empty() => t,
        _ => {
            warn!("API request rejected: no API token is configured (failing closed)");
            return (StatusCode::UNAUTHORIZED, "API token not configured").into_response();
        }
    };

    match extract_token(&req) {
        Some(supplied) if constant_time_eq(supplied.as_bytes(), expected.as_bytes()) => next.run(req).await,
        _ => (
            StatusCode::UNAUTHORIZED,
            [(header::WWW_AUTHENTICATE, "Bearer")],
            "missing or invalid API token",
        )
            .into_response(),
    }
}

/// HTTP status returned for an illegal lifecycle transition. Kept at 400 for compatibility
/// with existing clients and tests; change it here (e.g. to 409 CONFLICT) to switch globally.
pub const TRANSITION_ERROR_STATUS: StatusCode = StatusCode::BAD_REQUEST;

/// Converts an engine error into a response. Lifecycle violations become a structured JSON
/// body — `{"error":"invalid_transition","message":..., "current_state":...,
/// "allowed_transitions":[...], "available_actions":[...]}` — so UIs can show what *is*
/// possible and agents can self-correct. Anything else keeps `fallback` + plain text.
fn error_response(err: &(dyn std::error::Error + Send + Sync + 'static), fallback: StatusCode) -> Response {
    if let Some(t) = err.downcast_ref::<crate::state::StateTransitionError>() {
        return transition_error_response(t);
    }
    if let Some(nf) = err.downcast_ref::<crate::state::EntityNotFound>() {
        return (StatusCode::NOT_FOUND, nf.to_string()).into_response();
    }
    (fallback, err.to_string()).into_response()
}

/// `{"error": code, "message": message}` with the given status.
fn error_json(status: StatusCode, code: &str, message: &str) -> Response {
    (status, Json(serde_json::json!({ "error": code, "message": message }))).into_response()
}

fn transition_error_response(t: &crate::state::StateTransitionError) -> Response {
    let mut body = serde_json::to_value(t).unwrap_or_else(|_| serde_json::json!({}));
    body["error"] = "invalid_transition".into();
    body["message"] = t.to_string().into();
    (TRANSITION_ERROR_STATUS, Json(body)).into_response()
}

/// Builds a CORS layer for the configured origins, or `None` when no browser
/// origin is allowed.
fn build_cors_layer(allowed_origins: &[String]) -> Option<CorsLayer> {
    let origins: Vec<HeaderValue> = allowed_origins
        .iter()
        .filter_map(|o| match o.parse::<HeaderValue>() {
            Ok(v) => Some(v),
            Err(_) => {
                warn!("Ignoring invalid allowed origin '{}'", o);
                None
            }
        })
        .collect();
    if origins.is_empty() {
        return None;
    }
    Some(
        CorsLayer::new()
            .allow_origin(AllowOrigin::list(origins))
            .allow_methods([Method::GET, Method::POST, Method::PATCH, Method::DELETE, Method::OPTIONS])
            .allow_headers([
                header::AUTHORIZATION,
                header::CONTENT_TYPE,
                HeaderName::from_static(API_KEY_HEADER),
            ]),
    )
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SystemStatusResponse {
    pub device_id: String,
    pub uptime_seconds: u64,
    pub peer_count: usize,
    pub sync_state: String,
    pub version: String,
    pub monitored_folders_count: usize,
    pub pending_conflicts_count: usize,
    pub memory_footprint_bytes: usize,
    #[serde(rename = "myID", default)]
    pub my_id: String,
    #[serde(default)]
    pub uptime: u64,
    #[serde(default)]
    pub alloc: usize,
    #[serde(default)]
    pub sys: usize,
    /// Engine lifecycle state (`engine_lifecycle`): starting, running, stopping, stopped, failed.
    #[serde(default)]
    pub engine_state: String,
    /// Number of folders in each `folder_sync_lifecycle` state, e.g. `{"idle": 2, "error": 1}`.
    #[serde(default)]
    pub folders_by_state: std::collections::BTreeMap<String, usize>,
    #[serde(rename = "guiAddressUsed", default)]
    pub gui_address_used: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RestFolderDeviceRef {
    #[serde(rename = "deviceID")]
    pub device_id: String,
    #[serde(rename = "encryptionPassword", default)]
    pub encryption_password: String,
}

#[derive(Debug, Default, Serialize, Deserialize)]
pub struct RestFolderVersioning {
    #[serde(rename = "type", default = "default_versioning_type")]
    pub versioning_type: String,
    #[serde(default)]
    pub params: HashMap<String, String>,
}

fn default_versioning_type() -> String {
    "none".to_string()
}

fn default_folder_type() -> String {
    "sendreceive".to_string()
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RestFolderDto {
    pub id: String,
    #[serde(default)]
    pub label: String,
    #[serde(default)]
    pub path: String,
    #[serde(rename = "type", default = "default_folder_type")]
    pub folder_type: String,
    #[serde(default)]
    pub paused: bool,
    #[serde(rename = "rescanIntervalS", default = "default_rescan_interval")]
    pub rescan_interval_s: i32,
    #[serde(rename = "fsWatcherEnabled", default = "default_true")]
    pub fs_watcher_enabled: bool,
    #[serde(default)]
    pub devices: Vec<RestFolderDeviceRef>,
    #[serde(default)]
    pub versioning: RestFolderVersioning,
}

fn default_rescan_interval() -> i32 {
    60
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RestDeviceDto {
    #[serde(rename = "deviceID")]
    pub device_id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub addresses: Vec<String>,
    #[serde(default)]
    pub paused: bool,
    #[serde(default)]
    pub introducer: bool,
    #[serde(rename = "autoAcceptFolders", default)]
    pub auto_accept_folders: bool,
    #[serde(default)]
    pub untrusted: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RestConfigDto {
    #[serde(default = "default_config_version")]
    pub version: i32,
    #[serde(default)]
    pub folders: Vec<RestFolderDto>,
    #[serde(default)]
    pub devices: Vec<RestDeviceDto>,
}

fn default_config_version() -> i32 {
    1
}

#[derive(Debug, Deserialize)]
pub struct PatchFolderRequest {
    pub paused: Option<bool>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct FolderDto {
    pub folder_id: String,
    pub local_path: String,
    pub conflict_policy: String,
    pub is_paused: bool,
    pub sync_mode: String,
    pub pull_order: String,
    pub versioning_type: String,
    pub versioning_keep_days: i64,
    pub marker_name: String,
    pub versioning_command: Option<String>,
    pub files_count: usize,
    /// Current lifecycle state (`idle`, `scanning`, `syncing`, `paused`, `error`).
    #[serde(default)]
    pub state: String,
    /// Why the folder is in `error`, if it is.
    #[serde(default)]
    pub error: Option<crate::engine::FolderError>,
    /// Full lifecycle snapshot including `available_actions`.
    #[serde(default)]
    pub lifecycle: Option<crate::state::LifecycleView>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RegisterFolderRequest {
    pub folder_id: String,
    pub local_path: String,
    #[serde(default = "default_policy")]
    pub conflict_policy: String,
    pub sync_mode: Option<String>,
    pub pull_order: Option<String>,
    pub versioning_type: Option<String>,
    pub versioning_keep_days: Option<i64>,
    pub marker_name: Option<String>,
    pub versioning_command: Option<String>,
}

fn default_policy() -> String {
    "THREE_WAY_AUTO".to_string()
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ConflictDetailsDto {
    pub conflict_id: String,
    pub folder_id: String,
    pub relative_path: String,
    pub base_revision: Option<String>,
    pub local_revision: String,
    pub remote_revision: String,
    pub staged_payload_path: String,
    pub status: String,
    pub detected_at: i64,
    pub available_actions: Vec<String>,
    pub is_terminal: bool,
    pub state_description: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DiffResponse {
    pub conflict_id: String,
    pub folder_id: String,
    pub relative_path: String,
    pub base: String,
    pub local: String,
    pub remote: String,
    pub hunks: Vec<DiffHunk>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct IgnoresRequest {
    pub ignore: Vec<String>,
}

pub struct ApiServer {
    state: AppState,
    bind_addr: SocketAddr,
}

impl ApiServer {
    pub fn new(
        sync_engine: SyncEngine,
        bind_addr: SocketAddr,
        auth_token: Option<String>,
        peer_count: Arc<std::sync::atomic::AtomicUsize>,
        p2p_manager: Arc<crate::p2p::P2PManager>,
        allowed_origins: Vec<String>,
    ) -> Self {
        let event_log = EventLog::new();
        event_log.spawn_recorder(sync_engine.event_sender());
        Self {
            state: AppState {
                start_time: Instant::now(),
                sync_engine,
                auth_token,
                peer_count,
                p2p_manager,
                allowed_origins,
                event_log,
            },
            bind_addr,
        }
    }

    /// Builds Axum router with security, CORS, and endpoint handlers.
    ///
    /// Every route — including the WebSocket event stream — requires the API token.
    /// Cross-origin browser access is only granted to `state.allowed_origins`.
    pub fn create_router(state: AppState) -> Router {
        let cors = build_cors_layer(&state.allowed_origins);

        let router = Router::new()
            .route("/api/v1/system/status", get(get_system_status))
            .route("/rest/system/status", get(get_system_status))
            .route("/api/v1/system/version", get(get_system_version))
            .route("/rest/system/version", get(get_system_version))
            .route("/api/v1/system/ping", get(get_system_ping))
            .route("/rest/system/ping", get(get_system_ping))
            .route("/api/v1/system/shutdown", post(system_shutdown))
            .route("/rest/system/shutdown", post(system_shutdown))
            .route("/api/v1/system/restart", post(system_restart))
            .route("/rest/system/restart", post(system_restart))
            .route("/api/v1/system/statemachines", get(get_system_statemachines))
            .route("/rest/system/statemachines", get(get_system_statemachines))
            .route("/api/v1/connections", get(get_connections))
            .route("/rest/system/connections", get(get_connections))
            .route("/rest/system/config", get(get_rest_config).post(set_rest_config))
            .route("/rest/system/pause", post(pause_device_query))
            .route("/rest/system/resume", post(resume_device_query))

            .route("/api/v1/folders", get(list_folders).post(register_folder))
            .route("/rest/system/folders", get(list_folders).post(register_folder))
            .route("/api/v1/folders/:id", delete(delete_folder))
            .route("/rest/config/folders/:id", patch(patch_folder_config).delete(delete_folder))
            .route("/api/v1/folders/:id/pause", post(pause_folder))
            .route("/api/v1/folders/:id/resume", post(resume_folder))
            .route("/api/v1/folders/:id/rescan", post(rescan_folder))
            .route("/rest/db/scan", post(rescan_folder_query))
            .route("/api/v1/folders/:id/status", get(get_folder_status))
            .route("/rest/db/status", get(get_folder_status_query))
            .route("/api/v1/folders/:id/completion", get(get_folder_completion))
            .route("/rest/db/completion", get(get_folder_completion_query))
            .route("/api/v1/folders/:id/devices", get(list_folder_devices))
            .route("/api/v1/folders/:id/devices/:device_id", post(share_folder_device).delete(unshare_folder_device))
            .route("/api/v1/folders/:id/ignores", get(get_folder_ignores).post(set_folder_ignores))
            .route("/rest/db/ignores", get(get_folder_ignores_query).post(set_folder_ignores_query))

            .route("/api/v1/devices", get(list_devices).post(add_device))
            .route("/rest/system/devices", get(list_devices).post(add_device))
            .route("/api/v1/devices/:id", get(get_device).delete(remove_device))
            .route("/rest/config/devices/:id", delete(remove_device))
            .route("/api/v1/devices/:id/pause", post(pause_device))
            .route("/api/v1/devices/:id/resume", post(resume_device))

            .route("/api/v1/cluster/pending/devices", get(list_pending_devices))
            .route("/rest/cluster/pending/devices", get(list_pending_devices).delete(reject_pending_device_query))
            .route("/api/v1/cluster/pending/devices/:id/accept", post(accept_pending_device))
            .route("/rest/cluster/pending/devices/:id/accept", post(accept_pending_device))
            .route("/api/v1/cluster/pending/devices/:id/reject", post(reject_pending_device))
            .route("/rest/cluster/pending/devices/:id/reject", post(reject_pending_device))

            .route("/api/v1/cluster/rejected/devices", get(list_rejected_devices))
            .route("/api/v1/cluster/rejected/devices/:id/unreject", post(unreject_device))
            .route("/api/v1/cluster/pending/folders", get(list_pending_folders))
            .route("/rest/cluster/pending/folders", get(list_pending_folders).delete(reject_pending_folder_query))
            .route("/api/v1/cluster/pending/folders/:id/accept", post(accept_pending_folder))
            .route("/rest/cluster/pending/folders/:id/accept", post(accept_pending_folder))
            .route("/api/v1/cluster/pending/folders/:id/reject", post(reject_pending_folder))
            .route("/rest/cluster/pending/folders/:id/reject", post(reject_pending_folder))

            .route("/api/v1/conflicts", get(list_conflicts))
            .route("/api/v1/conflicts/:id", get(get_conflict_details))
            .route("/api/v1/conflicts/:id/base", get(get_conflict_base))
            .route("/api/v1/conflicts/:id/local", get(get_conflict_local))
            .route("/api/v1/conflicts/:id/remote", get(get_conflict_remote))
            .route("/api/v1/conflicts/:id/diff", get(get_conflict_diff))
            .route("/api/v1/conflicts/:id/resolve", post(resolve_conflict))
            .route("/api/v1/conflicts/:id/ignore", post(ignore_conflict))
            .route("/rest/conflicts/:id/ignore", post(ignore_conflict))
            .route("/api/v1/conflicts/:id/reopen", post(reopen_conflict))
            .route("/rest/conflicts/:id/reopen", post(reopen_conflict))

            .route("/api/v1/events", get(events_handler))
            .route("/rest/events", get(events_handler))
            .layer(middleware::from_fn_with_state(state.clone(), require_api_token));

        // CORS is the outermost layer so browser preflights are answered before auth.
        let router = match cors {
            Some(cors) => router.layer(cors),
            None => router,
        };
        router.with_state(state)
    }

    /// Runs the server until `shutdown` resolves, then stops accepting connections and lets
    /// in-flight requests finish. `on_ready` is called once the listener is bound.
    pub async fn run_until(
        self,
        shutdown: impl std::future::Future<Output = ()> + Send + 'static,
        on_ready: impl FnOnce(),
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let app = Self::create_router(self.state);
        let listener = tokio::net::TcpListener::bind(&self.bind_addr).await?;
        info!("Synapse Core REST & WebSocket API listening on http://{}", self.bind_addr);
        on_ready();
        axum::serve(listener, app).with_graceful_shutdown(shutdown).await?;
        Ok(())
    }

    /// Runs the Axum server asynchronously.
    pub async fn run(self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let app = Self::create_router(self.state);
        info!("Synapse Core REST & WebSocket API listening on http://{}", self.bind_addr);
        let listener = tokio::net::TcpListener::bind(&self.bind_addr).await?;
        axum::serve(listener, app).await?;
        Ok(())
    }
}

// =============================================================================
// ROUTE HANDLERS: SYSTEM & CONNECTIONS
// =============================================================================

async fn get_system_status(State(state): State<AppState>) -> impl IntoResponse {
    let uptime = state.start_time.elapsed().as_secs();
    let folders = state.sync_engine.storage().list_folders().unwrap_or_default();
    let conflicts = state.sync_engine.storage().list_pending_conflicts().unwrap_or_default();
    let peers = state.peer_count.load(std::sync::atomic::Ordering::Relaxed);
    let mem = get_resident_memory_bytes();

    let mut is_scanning = false;
    let mut is_syncing = false;
    let mut all_paused = !folders.is_empty();

    let mut folders_by_state = std::collections::BTreeMap::new();
    for f in &folders {
        if !f.is_paused {
            all_paused = false;
        }
        let folder_state = state.sync_engine.get_folder_state(&f.folder_id);
        *folders_by_state.entry(folder_state.as_str().to_string()).or_insert(0) += 1;
        match folder_state {
            crate::state::FolderSyncState::Scanning => is_scanning = true,
            crate::state::FolderSyncState::Syncing => is_syncing = true,
            _ => {}
        }
    }

    let sync_state = if is_scanning {
        "SCANNING".to_string()
    } else if is_syncing {
        "SYNCING".to_string()
    } else if all_paused {
        "PAUSED".to_string()
    } else {
        "IDLE".to_string()
    };

    let dev_id = state.sync_engine.device_id().to_string();
    Json(SystemStatusResponse {
        device_id: dev_id.clone(),
        uptime_seconds: uptime,
        peer_count: peers,
        sync_state,
        version: env!("CARGO_PKG_VERSION").into(),
        monitored_folders_count: folders.len(),
        pending_conflicts_count: conflicts.len(),
        memory_footprint_bytes: mem,
        my_id: dev_id,
        uptime,
        alloc: mem,
        sys: mem,
        gui_address_used: "http://127.0.0.1:8384".into(),
        engine_state: {
            use crate::state::StateMachine;
            crate::engine_control().state().name().to_string()
        },
        folders_by_state,
    })
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SystemVersionResponse {
    pub version: String,
    #[serde(rename = "longVersion", default)]
    pub long_version: String,
    pub codename: String,
    pub os: String,
    pub arch: String,
}

async fn get_system_version() -> impl IntoResponse {
    Json(SystemVersionResponse {
        version: "v1.0.0".into(),
        long_version: "Synapse Core v1.0.0 (Native Rust Engine)".into(),
        codename: "Synapse-SynapticCore".into(),
        os: std::env::consts::OS.into(),
        arch: std::env::consts::ARCH.into(),
    })
}

async fn get_system_ping() -> impl IntoResponse {
    Json(serde_json::json!({ "ping": "pong" }))
}

/// Requests a graceful engine shutdown (`engine_lifecycle`: running → stopping → stopped).
/// Idempotent; responds with the resulting state.
async fn system_shutdown() -> impl IntoResponse {
    use crate::state::StateMachine;
    info!("System shutdown requested via API");
    let state = crate::engine_control().request_shutdown();
    (StatusCode::OK, Json(serde_json::json!({ "engine_state": state.name() }))).into_response()
}

async fn system_restart() -> impl IntoResponse {
    info!("System restart requested via API");
    StatusCode::OK.into_response()
}

async fn get_connections(State(state): State<AppState>) -> impl IntoResponse {
    use crate::state::{LifecycleView, PeerConnectionState};
    let storage = state.sync_engine.storage();
    let devices = storage.list_devices().unwrap_or_default();
    let known = state.p2p_manager.list_known_peers().await;
    let window = crate::p2p::PEER_PRESENCE_WINDOW.as_secs();
    let mut map = serde_json::Map::new();

    for dev in devices {
        let peer = known.get(&dev.device_id);
        let seen_secs = peer.map(|p| p.last_seen.elapsed().as_secs());
        // Truthful presence: connected only if heard from recently (previously this was
        // `!is_paused`, which reported never-seen devices as connected).
        let conn_state = PeerConnectionState::derive(dev.is_paused, seen_secs, window);
        let (addr_str, client_type) = match peer {
            Some(p) if conn_state == PeerConnectionState::Online => (p.addr.to_string(), p.client_type.clone()),
            _ => (
                dev.addresses.first().cloned().unwrap_or_else(|| "dynamic".into()),
                "synapse-quic (FastCDC)".into(),
            ),
        };
        let metrics = PeerConnectionMetrics {
            device_id: dev.device_id.clone(),
            address: addr_str,
            is_connected: conn_state.is_connected(),
            in_bps: 0,
            out_bps: 0,
            total_in_bytes: 0,
            total_out_bytes: 0,
            latency_ms: 5,
            client_type,
        };
        let mut v = serde_json::to_value(metrics).unwrap_or_default();
        v["connection_state"] = conn_state.to_string().into();
        v["last_seen_secs_ago"] = serde_json::to_value(seen_secs).unwrap_or_default();
        v["lifecycle"] = serde_json::to_value(LifecycleView::of(conn_state)).unwrap_or_default();
        map.insert(dev.device_id, v);
    }

    // Online peers that are not configured devices (e.g. pending pairing requests).
    // Rejected devices are left out.
    let rejected: std::collections::HashSet<String> = storage
        .list_rejected_devices()
        .unwrap_or_default()
        .into_iter()
        .map(|r| r.device_id)
        .collect();
    for (peer_id, peer) in known {
        let fresh = peer.last_seen.elapsed() <= crate::p2p::PEER_PRESENCE_WINDOW;
        if !fresh || map.contains_key(&peer_id) || rejected.contains(&peer_id) {
            continue;
        }
        let metrics = PeerConnectionMetrics {
            device_id: peer_id.clone(),
            address: peer.addr.to_string(),
            is_connected: true,
            in_bps: 0,
            out_bps: 0,
            total_in_bytes: 0,
            total_out_bytes: 0,
            latency_ms: 5,
            client_type: peer.client_type,
        };
        let mut v = serde_json::to_value(metrics).unwrap_or_default();
        v["connection_state"] = "online".into();
        v["last_seen_secs_ago"] = peer.last_seen.elapsed().as_secs().into();
        v["lifecycle"] = serde_json::to_value(LifecycleView::of(PeerConnectionState::Online)).unwrap_or_default();
        map.insert(peer_id, v);
    }

    Json(serde_json::Value::Object(map))
}

async fn get_system_statemachines() -> impl IntoResponse {
    Json(crate::state::get_all_state_machines())
}

async fn get_rest_config(State(state): State<AppState>) -> impl IntoResponse {
    let folders = state.sync_engine.storage().list_folders().unwrap_or_default();
    let devices = state.sync_engine.storage().list_devices().unwrap_or_default();

    let mapped_folders: Vec<RestFolderDto> = folders
        .into_iter()
        .map(|f| {
            let devs = state
                .sync_engine
                .storage()
                .list_folder_devices(&f.folder_id)
                .unwrap_or_default()
                .into_iter()
                .map(|device_id| RestFolderDeviceRef {
                    device_id,
                    encryption_password: String::new(),
                })
                .collect();

            let mut params = HashMap::new();
            if let Some(cmd) = &f.versioning_command {
                params.insert("command".to_string(), cmd.clone());
            }
            params.insert("cleanoutDays".to_string(), f.versioning_keep_days.to_string());

            RestFolderDto {
                id: f.folder_id.clone(),
                label: f.folder_id.clone(),
                path: f.local_path,
                folder_type: f.sync_mode,
                paused: f.is_paused,
                rescan_interval_s: 60,
                fs_watcher_enabled: true,
                devices: devs,
                versioning: RestFolderVersioning {
                    versioning_type: f.versioning_type.to_lowercase(),
                    params,
                },
            }
        })
        .collect();

    let mapped_devices: Vec<RestDeviceDto> = devices
        .into_iter()
        .map(|d| RestDeviceDto {
            device_id: d.device_id,
            name: d.name,
            addresses: d.addresses,
            paused: d.is_paused,
            introducer: d.introducer,
            auto_accept_folders: false,
            untrusted: !d.is_trusted,
        })
        .collect();

    Json(RestConfigDto {
        version: 1,
        folders: mapped_folders,
        devices: mapped_devices,
    })
}

async fn set_rest_config(
    State(state): State<AppState>,
    Json(raw): Json<serde_json::Value>,
) -> impl IntoResponse {
    use std::collections::HashSet;
    use crate::p2p::identity::canonical_device_id;

    let config: RestConfigDto = match serde_json::from_value(raw.clone()) {
        Ok(c) => c,
        Err(e) => return error_json(StatusCode::UNPROCESSABLE_ENTITY, "invalid_config", &e.to_string()),
    };
    let storage = state.sync_engine.storage();
    let own_id = canonical_device_id(state.p2p_manager.device_id());

    // REST semantics: a posted config is the complete desired state. Clients (including
    // the Synapse apps) delete a device by posting the config without it, so devices and
    // folder shares missing from the post are removed. Only lists actually present in the
    // body are treated that way, so a partial body never wipes anything.
    let devices_listed = raw.get("devices").map(|v| v.is_array()).unwrap_or(false);
    let folders_with_device_lists: HashSet<String> = raw
        .get("folders")
        .and_then(|f| f.as_array())
        .map(|list| {
            list.iter()
                .filter(|f| f.get("devices").map(|d| d.is_array()).unwrap_or(false))
                .filter_map(|f| f.get("id").and_then(|i| i.as_str()).map(str::to_string))
                .collect()
        })
        .unwrap_or_default();
    let wanted_devices: HashSet<String> = config
        .devices
        .iter()
        .map(|d| canonical_device_id(&d.device_id))
        .filter(|d| *d != own_id)
        .collect();

    for f in config.folders {
        let v_cmd = f.versioning.params.get("command").cloned();
        let v_days = f
            .versioning
            .params
            .get("cleanoutDays")
            .and_then(|s| s.parse::<i64>().ok())
            .unwrap_or(30);

        let mut rec = FolderRecord::new(f.id.clone(), f.path.clone());
        rec.is_paused = f.paused;
        rec.sync_mode = f.folder_type;
        rec.versioning_type = f.versioning.versioning_type;
        rec.versioning_keep_days = v_days;
        rec.versioning_command = v_cmd;
        let _ = storage.add_folder(&rec);

        let wanted: HashSet<String> = f
            .devices
            .iter()
            .map(|d| canonical_device_id(&d.device_id))
            .filter(|d| *d != own_id)
            .collect();
        for d in &wanted {
            let _ = storage.share_folder_with_device(&f.id, d);
        }
        if folders_with_device_lists.contains(&f.id) {
            for d in storage.list_folder_devices(&f.id).unwrap_or_default() {
                if !wanted.contains(&d) {
                    let _ = storage.unshare_folder_from_device(&f.id, &d);
                }
            }
        }
    }

    for d in config.devices {
        let device_id = canonical_device_id(&d.device_id);
        if device_id == own_id {
            continue; // never pair with ourselves
        }
        let rec = DeviceRecord {
            device_id,
            name: d.name,
            addresses: d.addresses,
            is_paused: d.paused,
            is_trusted: !d.untrusted,
            introducer: d.introducer,
            cert_name: None,
            created_at: chrono::Utc::now().timestamp(),
        };
        let _ = storage.add_device(&rec);
    }

    if devices_listed {
        for existing in storage.list_devices().unwrap_or_default() {
            if !wanted_devices.contains(&existing.device_id) {
                if storage.remove_device(&existing.device_id).is_ok() {
                    info!("Device {} removed (absent from posted config)", existing.device_id);
                    state.p2p_manager.forget_device(&existing.device_id).await;
                }
            }
        }
    }

    announce_folder_shares(&state);
    StatusCode::OK.into_response()
}

// =============================================================================
// ROUTE HANDLERS: FOLDERS
// =============================================================================

async fn list_folders(State(state): State<AppState>) -> impl IntoResponse {
    let folders = match state.sync_engine.storage().list_folders() {
        Ok(f) => f,
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    };

    let mut dtos = Vec::new();
    for f in folders {
        let files = state.sync_engine.storage().list_files_in_folder(&f.folder_id).unwrap_or_default();
        let folder_state = state.sync_engine.get_folder_state(&f.folder_id).as_str().to_string();
        let folder_error = state.sync_engine.folder_error(&f.folder_id);
        let folder_lifecycle = state.sync_engine.folder_lifecycle(&f.folder_id);
        dtos.push(FolderDto {
            folder_id: f.folder_id,
            local_path: f.local_path,
            conflict_policy: f.conflict_policy,
            is_paused: f.is_paused,
            sync_mode: f.sync_mode,
            pull_order: f.pull_order,
            versioning_type: f.versioning_type,
            versioning_keep_days: f.versioning_keep_days,
            marker_name: f.marker_name,
            versioning_command: f.versioning_command,
            files_count: files.len(),
            state: folder_state,
            error: folder_error,
            lifecycle: Some(folder_lifecycle),
        });
    }

    Json(dtos).into_response()
}

async fn register_folder(
    State(state): State<AppState>,
    Json(payload): Json<RegisterFolderRequest>,
) -> impl IntoResponse {
    let mut folder = FolderRecord::new(payload.folder_id.clone(), payload.local_path.clone());
    folder.conflict_policy = payload.conflict_policy;
    if let Some(sm) = payload.sync_mode { folder.sync_mode = sm; }
    if let Some(po) = payload.pull_order { folder.pull_order = po; }
    if let Some(vt) = payload.versioning_type { folder.versioning_type = vt; }
    if let Some(kd) = payload.versioning_keep_days { folder.versioning_keep_days = kd; }
    if let Some(mn) = payload.marker_name { folder.marker_name = mn; }
    if let Some(vc) = payload.versioning_command { folder.versioning_command = Some(vc); }

    if let Err(e) = state.sync_engine.storage().add_folder(&folder) {
        return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response();
    }

    // Trigger initial index scan
    // Initial scan, then watch for changes (previously new folders were only watched
    // after an engine restart).
    let engine = state.sync_engine.clone();
    let fid = payload.folder_id;
    let p2p = state.p2p_manager.clone();
    tokio::spawn(async move {
        let _ = tokio::task::spawn_blocking(move || {
            let _ = engine.index_folder(&fid);
            engine.start_watching(&fid);
        })
        .await;
        p2p.announce_folders().await;
    });

    (StatusCode::CREATED, Json(folder)).into_response()
}

async fn delete_folder(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    state.sync_engine.stop_watching(&id);
    match state.sync_engine.storage().delete_folder(&id) {
        Ok(_) => StatusCode::NO_CONTENT.into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn patch_folder_config(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
    Json(payload): Json<PatchFolderRequest>,
) -> impl IntoResponse {
    if let Some(paused) = payload.paused {
        match state.sync_engine.set_folder_paused(&id, paused) {
            Ok(_) => StatusCode::OK.into_response(),
            Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
        }
    } else {
        StatusCode::OK.into_response()
    }
}

async fn pause_folder(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.set_folder_paused(&id, true) {
        Ok(_) => StatusCode::OK.into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn resume_folder(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.set_folder_paused(&id, false) {
        Ok(_) => StatusCode::OK.into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn rescan_folder(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let engine = state.sync_engine.clone();
    let fid = id.clone();
    tokio::task::spawn_blocking(move || {
        let _ = engine.index_folder(&fid);
    });
    StatusCode::OK.into_response()
}

async fn rescan_folder_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    if let Some(fid) = params.get("folder") {
        let engine = state.sync_engine.clone();
        let f = fid.clone();
        tokio::task::spawn_blocking(move || {
            let _ = engine.index_folder(&f);
        });
        StatusCode::OK.into_response()
    } else {
        (StatusCode::BAD_REQUEST, "Missing folder query parameter").into_response()
    }
}

/// Adds lifecycle fields (`error`, `lifecycle`) to a folder status response without
/// changing any existing field.
fn folder_status_with_lifecycle(state: &AppState, folder_id: &str, st: crate::storage::FolderStatusMetrics) -> Response {
    let mut value = serde_json::to_value(&st).unwrap_or_else(|_| serde_json::json!({}));
    value["error"] = serde_json::to_value(state.sync_engine.folder_error(folder_id)).unwrap_or_default();
    value["lifecycle"] = serde_json::to_value(state.sync_engine.folder_lifecycle(folder_id)).unwrap_or_default();
    Json(value).into_response()
}

async fn get_folder_status(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.storage().get_folder_status(&id) {
        Ok(mut st) => {
            st.state = state.sync_engine.get_folder_state(&id).as_str().to_string();
            folder_status_with_lifecycle(&state, &id, st)
        }
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn get_folder_status_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    if let Some(fid) = params.get("folder") {
        match state.sync_engine.storage().get_folder_status(fid) {
            Ok(mut st) => {
                st.state = state.sync_engine.get_folder_state(fid).as_str().to_string();
                folder_status_with_lifecycle(&state, fid, st)
            }
            Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
        }
    } else {
        (StatusCode::BAD_REQUEST, "Missing folder query parameter").into_response()
    }
}

async fn get_folder_completion(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let dev_id = state.sync_engine.device_id();
    match state.sync_engine.storage().get_device_completion(dev_id, &id) {
        Ok(comp) => Json(comp).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn get_folder_completion_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let fid = match params.get("folder") {
        Some(f) => f,
        None => return (StatusCode::BAD_REQUEST, "Missing folder parameter").into_response(),
    };
    let dev_id = params.get("device").cloned().unwrap_or_else(|| state.sync_engine.device_id().to_string());
    match state.sync_engine.storage().get_device_completion(&dev_id, fid) {
        Ok(comp) => Json(comp).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn list_folder_devices(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.storage().list_folder_devices(&id) {
        Ok(devs) => Json(devs).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn share_folder_device(
    AxumPath((id, device_id)): AxumPath<(String, String)>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let storage = state.sync_engine.storage();
    let (stored_id, _) = crate::p2p::resolve_device(storage, &device_id);
    match storage.share_folder_with_device(&id, &stored_id) {
        Ok(_) => {
            // Without this the other device only heard about the share after reconnecting.
            announce_folder_shares(&state);
            StatusCode::OK.into_response()
        }
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn unshare_folder_device(
    AxumPath((id, device_id)): AxumPath<(String, String)>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.storage().unshare_folder_from_device(&id, &device_id) {
        Ok(_) => StatusCode::OK.into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn get_folder_ignores(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.storage().get_folder_ignores(&id) {
        Ok(patterns) => Json(IgnoresRequest { ignore: patterns }).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn set_folder_ignores(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
    Json(payload): Json<IgnoresRequest>,
) -> impl IntoResponse {
    match state.sync_engine.storage().set_folder_ignores(&id, &payload.ignore) {
        Ok(_) => StatusCode::OK.into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn get_folder_ignores_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    if let Some(fid) = params.get("folder") {
        match state.sync_engine.storage().get_folder_ignores(fid) {
            Ok(patterns) => Json(IgnoresRequest { ignore: patterns }).into_response(),
            Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
        }
    } else {
        (StatusCode::BAD_REQUEST, "Missing folder query parameter").into_response()
    }
}

async fn set_folder_ignores_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
    Json(payload): Json<IgnoresRequest>,
) -> impl IntoResponse {
    if let Some(fid) = params.get("folder") {
        match state.sync_engine.storage().set_folder_ignores(fid, &payload.ignore) {
            Ok(_) => StatusCode::OK.into_response(),
            Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
        }
    } else {
        (StatusCode::BAD_REQUEST, "Missing folder query parameter").into_response()
    }
}

// =============================================================================
// ROUTE HANDLERS: DEVICES & CLUSTERING
// =============================================================================

/// Serializes a paired device with its lifecycle fields: `pairing_state`,
/// `connection_state`, `last_seen_secs_ago`, and `lifecycle` (pairing machine, including
/// `available_actions`). Existing fields are unchanged.
async fn device_json(state: &AppState, dev: &DeviceRecord) -> serde_json::Value {
    use crate::state::{DevicePairingState, LifecycleView, StateMachine};
    let pairing = if dev.is_paused { DevicePairingState::Paused } else { DevicePairingState::Paired };
    let connection = state.p2p_manager.peer_connection_state(&dev.device_id, dev.is_paused).await;
    let mut v = serde_json::to_value(dev).unwrap_or_default();
    v["pairing_state"] = pairing.name().into();
    v["connection_state"] = connection.to_string().into();
    v["last_seen_secs_ago"] =
        serde_json::to_value(state.p2p_manager.peer_seen_secs_ago(&dev.device_id).await).unwrap_or_default();
    v["lifecycle"] = serde_json::to_value(LifecycleView::of(pairing)).unwrap_or_default();
    v
}

async fn list_devices(State(state): State<AppState>) -> impl IntoResponse {
    match state.sync_engine.storage().list_devices() {
        Ok(devs) => {
            let mut out = Vec::with_capacity(devs.len());
            for d in &devs {
                out.push(device_json(&state, d).await);
            }
            Json(out).into_response()
        }
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn add_device(
    State(state): State<AppState>,
    Json(payload): Json<DeviceRecord>,
) -> impl IntoResponse {
    match state.sync_engine.storage().add_device(&payload) {
        Ok(_) => {
            let _ = state.sync_engine.event_sender().send(CoreEvent::DevicePaired {
                device_id: payload.device_id.clone(),
                name: payload.name.clone(),
            });
            (StatusCode::CREATED, Json(payload)).into_response()
        }
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn get_device(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.storage().get_device(&id) {
        Ok(Some(d)) => Json(device_json(&state, &d).await).into_response(),
        Ok(None) => (StatusCode::NOT_FOUND, "Device not found").into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn remove_device(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let storage = state.sync_engine.storage();
    // The id as stored (callers may send a differently formatted id for the same device).
    let (stored_id, _) = crate::p2p::resolve_device(storage, &id);
    match storage.remove_device(&stored_id).and_then(|_| {
        if stored_id != id { storage.remove_device(&id) } else { Ok(()) }
    }) {
        Ok(_) => {
            // Without this the open connection kept the removed device "online" in
            // /api/v1/connections, so clients showed it as still connected.
            state.p2p_manager.forget_device(&stored_id).await;
            if stored_id != id {
                state.p2p_manager.forget_device(&id).await;
            }
            StatusCode::NO_CONTENT.into_response()
        }
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

/// Tells connected peers about the current folder shares (folder invitations) in the
/// background, after an API call changed which folders are shared with which devices.
fn announce_folder_shares(state: &AppState) {
    let p2p = state.p2p_manager.clone();
    tokio::spawn(async move { p2p.announce_folders().await });
}

/// Runs a guarded device pairing transition and publishes the resulting events.
fn device_transition_response(state: &AppState, device_id: &str, action: crate::state::DeviceAction) -> Response {
    use crate::state::{DevicePairingState, StateMachine};
    let storage = state.sync_engine.storage();
    let before = storage.device_pairing_state(device_id).ok().flatten();
    let pending = storage.get_pending_device(device_id).ok().flatten();
    match storage.apply_device_action(device_id, action) {
        Ok(after) => {
            // Legacy event first: existing clients expect DEVICE_PAIRED immediately after accept.
            if before == Some(DevicePairingState::Pending) && after == DevicePairingState::Paired {
                if let Some(p) = pending {
                    let _ = state.sync_engine.event_sender().send(CoreEvent::DevicePaired {
                        device_id: p.device_id,
                        name: p.name,
                    });
                }
            }
            if let Some(before) = before {
                if before != after {
                    let _ = state.sync_engine.event_sender().send(CoreEvent::DeviceStateChanged {
                        device_id: device_id.to_string(),
                        from: before.name().to_string(),
                        to: after.name().to_string(),
                    });
                }
            }
            StatusCode::OK.into_response()
        }
        Err(e) => error_response(&*e, StatusCode::INTERNAL_SERVER_ERROR),
    }
}

async fn pause_device(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    device_transition_response(&state, &id, crate::state::DeviceAction::Pause)
}

async fn resume_device(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    device_transition_response(&state, &id, crate::state::DeviceAction::Resume)
}

async fn pause_device_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    if let Some(device_id) = params.get("device") {
        device_transition_response(&state, device_id, crate::state::DeviceAction::Pause)
    } else {
        (StatusCode::BAD_REQUEST, "Missing device query parameter").into_response()
    }
}

async fn resume_device_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    if let Some(device_id) = params.get("device") {
        device_transition_response(&state, device_id, crate::state::DeviceAction::Resume)
    } else {
        (StatusCode::BAD_REQUEST, "Missing device query parameter").into_response()
    }
}

async fn list_pending_devices(State(state): State<AppState>) -> impl IntoResponse {
    match state.sync_engine.storage().list_pending_devices() {
        Ok(p) => {
            let view = serde_json::to_value(crate::state::LifecycleView::of(
                crate::state::DevicePairingState::Pending,
            ))
            .unwrap_or_default();
            let items: Vec<serde_json::Value> = p
                .into_iter()
                .map(|r| {
                    let mut v = serde_json::to_value(r).unwrap_or_default();
                    v["pairing_state"] = "pending".into();
                    v["lifecycle"] = view.clone();
                    v
                })
                .collect();
            Json(items).into_response()
        }
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn accept_pending_device(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    device_transition_response(&state, &id, crate::state::DeviceAction::Accept)
}

async fn reject_pending_device(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    device_transition_response(&state, &id, crate::state::DeviceAction::Reject)
}

/// `GET /api/v1/cluster/rejected/devices`: pairing requests that were rejected.
async fn list_rejected_devices(State(state): State<AppState>) -> impl IntoResponse {
    match state.sync_engine.storage().list_rejected_devices() {
        Ok(list) => {
            let view = crate::state::LifecycleView::of(crate::state::DevicePairingState::Rejected);
            let items: Vec<serde_json::Value> = list
                .into_iter()
                .map(|r| {
                    let mut v = serde_json::to_value(r).unwrap_or_default();
                    v["pairing_state"] = "rejected".into();
                    v["lifecycle"] = serde_json::to_value(&view).unwrap_or_default();
                    v
                })
                .collect();
            Json(items).into_response()
        }
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

/// `POST /api/v1/cluster/rejected/devices/:id/unreject`: moves a rejected device back to
/// pending so it can be accepted.
async fn unreject_device(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    device_transition_response(&state, &id, crate::state::DeviceAction::Unreject)
}

async fn reject_pending_device_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    if let Some(device_id) = params.get("device") {
        device_transition_response(&state, device_id, crate::state::DeviceAction::Reject)
    } else {
        (StatusCode::BAD_REQUEST, "Missing device query parameter").into_response()
    }
}

async fn list_pending_folders(State(state): State<AppState>) -> impl IntoResponse {
    match state.sync_engine.storage().list_pending_folders() {
        Ok(p) => Json(p).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AcceptFolderPayload {
    /// Which device's invitation to accept; optional (clients may send only `local_path`),
    /// then the most recent invitation for the folder is used.
    #[serde(default)]
    pub device_id: Option<String>,
    pub local_path: String,
}

async fn accept_pending_folder(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
    Json(payload): Json<AcceptFolderPayload>,
) -> impl IntoResponse {
    let storage = state.sync_engine.storage();
    let offers: Vec<_> = storage
        .list_pending_folders()
        .unwrap_or_default()
        .into_iter()
        .filter(|p| p.folder_id == id)
        .collect();
    let device_id = match &payload.device_id {
        Some(d) => {
            let (stored, _) = crate::p2p::resolve_device(storage, d);
            match offers.iter().find(|p| p.device_id == stored || p.device_id == *d) {
                Some(p) => p.device_id.clone(),
                None => return error_json(StatusCode::NOT_FOUND, "not_found", "no pending invitation for this folder from that device"),
            }
        }
        // list_pending_folders is newest first
        None => match offers.first() {
            Some(p) => p.device_id.clone(),
            None => return error_json(StatusCode::NOT_FOUND, "not_found", "no pending invitation for this folder"),
        },
    };
    let local_path = payload.local_path.trim().to_string();
    if local_path.is_empty() {
        return error_json(StatusCode::BAD_REQUEST, "invalid_request", "local_path is required");
    }
    if let Err(e) = std::fs::create_dir_all(&local_path) {
        return error_json(StatusCode::BAD_REQUEST, "invalid_path", &format!("cannot create {}: {}", local_path, e));
    }
    if let Err(e) = storage.accept_pending_folder(&id, &device_id, &local_path) {
        return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response();
    }
    // Same as adding a folder: index, watch, then tell the peer and pull its files
    // (previously accepting only wrote database rows and nothing synced until a restart).
    let engine = state.sync_engine.clone();
    let p2p = state.p2p_manager.clone();
    let fid = id.clone();
    tokio::spawn(async move {
        let f2 = fid.clone();
        let _ = tokio::task::spawn_blocking(move || {
            let _ = engine.index_folder(&f2);
            engine.start_watching(&f2);
        })
        .await;
        p2p.announce_folders().await;
        if let Err(e) = p2p.sync_folder_from(&device_id, &fid).await {
            tracing::warn!("Initial sync of accepted folder {} from {} failed: {}", fid, device_id, e);
        }
    });
    StatusCode::OK.into_response()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RejectFolderPayload {
    /// Optional: without it every pending invitation for the folder is rejected.
    #[serde(default)]
    pub device_id: Option<String>,
}

async fn reject_pending_folder(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
    Json(payload): Json<RejectFolderPayload>,
) -> impl IntoResponse {
    let storage = state.sync_engine.storage();
    let targets: Vec<String> = match &payload.device_id {
        Some(d) => vec![crate::p2p::resolve_device(storage, d).0, d.clone()],
        None => storage
            .list_pending_folders()
            .unwrap_or_default()
            .into_iter()
            .filter(|p| p.folder_id == id)
            .map(|p| p.device_id)
            .collect(),
    };
    for dev in targets {
        if let Err(e) = storage.reject_pending_folder(&id, &dev) {
            return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response();
        }
    }
    StatusCode::OK.into_response()
}

async fn reject_pending_folder_query(
    Query(params): Query<HashMap<String, String>>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    if let Some(folder_id) = params.get("folder") {
        let dev_id = params.get("device").cloned().unwrap_or_default();
        match state.sync_engine.storage().reject_pending_folder(folder_id, &dev_id) {
            Ok(_) => StatusCode::OK.into_response(),
            Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
        }
    } else {
        (StatusCode::BAD_REQUEST, "Missing folder query parameter").into_response()
    }
}

// =============================================================================
// ROUTE HANDLERS: CONFLICTS
// =============================================================================

async fn list_conflicts(State(state): State<AppState>) -> impl IntoResponse {
    match state.sync_engine.storage().list_pending_conflicts() {
        Ok(list) => Json(list).into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn get_conflict_details(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.storage().get_conflict(&id) {
        Ok(Some(c)) => {
            let status = c.parsed_status();
            let dto = ConflictDetailsDto {
                conflict_id: c.conflict_id,
                folder_id: c.folder_id,
                relative_path: c.relative_path,
                base_revision: c.base_revision,
                local_revision: c.local_revision,
                remote_revision: c.remote_revision,
                staged_payload_path: c.staged_payload_path,
                status: c.status,
                detected_at: c.detected_at,
                available_actions: status
                    .available_actions()
                    .into_iter()
                    .map(String::from)
                    .collect(),
                is_terminal: status.is_terminal(),
                state_description: status.description().to_string(),
            };
            Json(dto).into_response()
        }
        Ok(None) => (StatusCode::NOT_FOUND, "Conflict not found").into_response(),
        Err(e) => (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    }
}

async fn get_conflict_base(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let conflict = match state.sync_engine.storage().get_conflict(&id) {
        Ok(Some(c)) => c,
        Ok(None) => return (StatusCode::NOT_FOUND, "Conflict not found").into_response(),
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    };

    let base_bytes = match conflict.base_revision {
        Some(rev) => state.sync_engine.storage().read_file_from_revision(&rev).unwrap_or_default(),
        None => Vec::new(),
    };

    Response::builder()
        .header("Content-Type", "text/plain; charset=utf-8")
        .body(axum::body::Body::from(base_bytes))
        .unwrap()
        .into_response()
}

async fn get_conflict_local(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let conflict = match state.sync_engine.storage().get_conflict(&id) {
        Ok(Some(c)) => c,
        Ok(None) => return (StatusCode::NOT_FOUND, "Conflict not found").into_response(),
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    };

    let folder = match state.sync_engine.storage().get_folder(&conflict.folder_id) {
        Ok(Some(f)) => f,
        _ => return (StatusCode::NOT_FOUND, "Folder missing").into_response(),
    };

    let local_file_path = PathBuf::from(&folder.local_path).join(&conflict.relative_path);
    let bytes = fs::read(&local_file_path).unwrap_or_default();

    Response::builder()
        .header("Content-Type", "text/plain; charset=utf-8")
        .body(axum::body::Body::from(bytes))
        .unwrap()
        .into_response()
}

async fn get_conflict_remote(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let conflict = match state.sync_engine.storage().get_conflict(&id) {
        Ok(Some(c)) => c,
        Ok(None) => return (StatusCode::NOT_FOUND, "Conflict not found").into_response(),
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    };

    let bytes = fs::read(&conflict.staged_payload_path).unwrap_or_default();

    Response::builder()
        .header("Content-Type", "text/plain; charset=utf-8")
        .body(axum::body::Body::from(bytes))
        .unwrap()
        .into_response()
}

async fn get_conflict_diff(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    let conflict = match state.sync_engine.storage().get_conflict(&id) {
        Ok(Some(c)) => c,
        Ok(None) => return (StatusCode::NOT_FOUND, "Conflict not found").into_response(),
        Err(e) => return (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()).into_response(),
    };

    let folder = match state.sync_engine.storage().get_folder(&conflict.folder_id) {
        Ok(Some(f)) => f,
        _ => return (StatusCode::NOT_FOUND, "Folder missing").into_response(),
    };

    let local_bytes = fs::read(PathBuf::from(&folder.local_path).join(&conflict.relative_path)).unwrap_or_default();
    let remote_bytes = fs::read(&conflict.staged_payload_path).unwrap_or_default();
    let base_bytes = match &conflict.base_revision {
        Some(rev) => state.sync_engine.storage().read_file_from_revision(rev).unwrap_or_default(),
        None => Vec::new(),
    };

    let base_str = String::from_utf8_lossy(&base_bytes).to_string();
    let local_str = String::from_utf8_lossy(&local_bytes).to_string();
    let remote_str = String::from_utf8_lossy(&remote_bytes).to_string();

    let hunks = match diff3::merge3(&base_str, &local_str, &remote_str) {
        diff3::MergeResult::Conflict { hunks, .. } => hunks,
        diff3::MergeResult::Clean(_) => vec![DiffHunk {
            hunk_type: diff3::HunkType::Unchanged,
            base_lines: base_str.lines().map(|s| s.to_string()).collect(),
            local_lines: local_str.lines().map(|s| s.to_string()).collect(),
            remote_lines: remote_str.lines().map(|s| s.to_string()).collect(),
        }],
    };

    Json(DiffResponse {
        conflict_id: conflict.conflict_id,
        folder_id: conflict.folder_id,
        relative_path: conflict.relative_path,
        base: base_str,
        local: local_str,
        remote: remote_str,
        hunks,
    })
    .into_response()
}

async fn resolve_conflict(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
    Json(payload): Json<ResolveRequest>,
) -> impl IntoResponse {
    match state.sync_engine.conflict_engine().resolve(&id, &payload) {
        Ok(_) => StatusCode::OK.into_response(),
        Err(e) => error_response(&*e, StatusCode::BAD_REQUEST),
    }
}

async fn ignore_conflict(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.conflict_engine().ignore(&id) {
        Ok(_) => StatusCode::OK.into_response(),
        Err(e) => error_response(&*e, StatusCode::BAD_REQUEST),
    }
}

async fn reopen_conflict(
    AxumPath(id): AxumPath<String>,
    State(state): State<AppState>,
) -> impl IntoResponse {
    match state.sync_engine.conflict_engine().reopen(&id) {
        Ok(_) => StatusCode::OK.into_response(),
        Err(e) => error_response(&*e, StatusCode::BAD_REQUEST),
    }
}

// =============================================================================
// ROUTE HANDLERS: WEBSOCKET EVENT STREAM
// =============================================================================

/// `GET /api/v1/events`, `GET /rest/events`.
///
/// - With WebSocket upgrade headers: live event stream (Synapse native).
/// - Plain GET: REST-compatible long-poll. Returns events with `id > since`
///   (newest `limit`), waiting up to `timeout` seconds for new ones. With `since=0`
///   (first load) it returns immediately unless `timeout` is given.
async fn events_handler(
    ws: Option<WebSocketUpgrade>,
    headers: axum::http::HeaderMap,
    Query(query): Query<EventsQuery>,
    State(state): State<AppState>,
) -> Response {
    if let Some(ws) = ws {
        return ws.on_upgrade(move |socket| handle_websocket(socket, state)).into_response();
    }
    let wants_websocket = headers
        .get(header::UPGRADE)
        .and_then(|v| v.to_str().ok())
        .is_some_and(|v| v.eq_ignore_ascii_case("websocket"));
    if wants_websocket {
        // Upgrade requested but not possible on this connection.
        return StatusCode::UPGRADE_REQUIRED.into_response();
    }

    let since = query.since.unwrap_or(0);
    let limit = query.limit.unwrap_or(0);
    let types: Option<Vec<String>> = query.events.as_deref().map(|list| {
        list.split(',').map(|t| t.trim().to_string()).filter(|t| !t.is_empty()).collect()
    });
    let default_timeout = if since == 0 { 0 } else { 60 };
    let timeout = Duration::from_secs(query.timeout.unwrap_or(default_timeout).min(300));
    let deadline = tokio::time::Instant::now() + timeout;

    let mut changes = state.event_log.subscribe();
    loop {
        let events = state.event_log.since(since, limit, types.as_deref());
        if !events.is_empty() || tokio::time::Instant::now() >= deadline {
            return Json(events).into_response();
        }
        match tokio::time::timeout_at(deadline, changes.changed()).await {
            Ok(Ok(())) => continue,
            _ => return Json(Vec::<serde_json::Value>::new()).into_response(),
        }
    }
}

async fn handle_websocket(mut socket: WebSocket, state: AppState) {
    let mut rx = state.sync_engine.event_sender().subscribe();

    loop {
        tokio::select! {
            event = rx.recv() => {
                match event {
                    Ok(evt) => {
                        if let Ok(json_str) = serde_json::to_string(&evt) {
                            if socket.send(Message::Text(json_str)).await.is_err() {
                                break;
                            }
                        }
                    }
                    Err(broadcast::error::RecvError::Lagged(n)) => {
                        warn!("WebSocket client lagged by {} events", n);
                    }
                    Err(broadcast::error::RecvError::Closed) => {
                        break;
                    }
                }
            }
            msg = socket.next() => {
                match msg {
                    Some(Ok(Message::Text(text))) => {
                        if text == "ping" {
                            let _ = socket.send(Message::Text("pong".into())).await;
                        }
                    }
                    Some(Ok(Message::Close(_))) | None => break,
                    _ => {}
                }
            }
        }
    }
}

/// Estimates resident memory in bytes (Linux /proc/self/statm or fallback).
fn get_resident_memory_bytes() -> usize {
    #[cfg(target_os = "linux")]
    {
        if let Ok(content) = fs::read_to_string("/proc/self/statm") {
            let parts: Vec<&str> = content.split_whitespace().collect();
            if parts.len() >= 2 {
                if let Ok(resident_pages) = parts[1].parse::<usize>() {
                    return resident_pages * 4096;
                }
            }
        }
    }
    16 * 1024 * 1024
}
