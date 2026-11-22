//! Formal State Machine Architecture for Synapse Core.
//!
//! Replaces implicit, stringly-typed state variables and primitive obsession
//! with type-safe, introspectable, and verifiable state machines.
//!
//! Elevates:
//! - **Developer Experience (DX)**: Strong compile-time exhaustiveness, typed transition guards,
//!   explicit error types, and self-documenting lifecycle models.
//! - **User Experience (UX)**: Guards against double-execution and race conditions, delivers
//!   accurate real-time operational status (no stuck "idle" states), and prevents data corruption.
//! - **Agent Experience (AX)**: Machine-readable state metadata, explicit action affordances
//!   (HATEOAS / capabilities), self-healing transition error diagnostics, and a runtime
//!   introspection schema endpoint (`/api/v1/system/statemachines`).

use std::fmt;
use std::str::FromStr;
use serde::{Deserialize, Serialize};

// =============================================================================
// 1. CORE INTROSPECTION & ERROR TYPES
// =============================================================================

/// Machine-readable metadata for a state within a state machine.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct StateMetadata {
    /// Canonical state identifier string
    pub state: String,
    /// Human and agent-readable description of what this state represents
    pub description: String,
    /// Whether this is a terminal (final) state from which no further transitions occur
    pub is_terminal: bool,
    /// List of canonical state names that can be legally transitioned to from here
    pub allowed_transitions: Vec<String>,
    /// Permitted user or agent action commands valid when in this state
    pub available_actions: Vec<String>,
}

/// Machine-readable definition of an entire state machine in Synapse Core.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct StateMachineDef {
    /// Unique identifier for this state machine
    pub machine_id: String,
    /// Human-friendly display title
    pub display_name: String,
    /// Subsystem or domain (e.g. "conflict", "sync", "p2p", "cluster")
    pub subsystem: String,
    /// High-level description of the workflow or entity managed by this machine
    pub description: String,
    /// Initial state of the entity
    pub initial_state: String,
    /// Complete catalog of states, transitions, and action capabilities
    pub states: Vec<StateMetadata>,
}

/// Structured error returned when an illegal state transition is attempted.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct StateTransitionError {
    /// Entity kind (e.g. "conflict", "folder", "device", "session")
    pub entity: String,
    /// Identifier of the specific entity instance
    pub entity_id: String,
    /// Current state of the entity
    pub current_state: String,
    /// State that was attempted
    pub attempted_transition: String,
    /// Valid target states from current state
    pub allowed_transitions: Vec<String>,
    /// Actions permitted in the current state
    pub available_actions: Vec<String>,
    /// Whether the entity is in a terminal state
    pub is_terminal: bool,
}

impl fmt::Display for StateTransitionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Invalid state transition for {} '{}': cannot move from '{}' to '{}'. Allowed transitions: {:?}",
            self.entity, self.entity_id, self.current_state, self.attempted_transition, self.allowed_transitions
        )
    }
}

impl std::error::Error for StateTransitionError {}

// =============================================================================
// 1b. SHARED STATE MACHINE TRAIT
// =============================================================================

/// Common contract for every lifecycle in Synapse Core.
///
/// Implement the handful of required items and every machine gets — for free and
/// identically — guarded transitions with structured errors, introspection metadata,
/// and a registry definition. This is the one place to look when adding a lifecycle:
/// no hand-written `StateTransitionError`s, no hand-written registry JSON.
pub trait StateMachine: Copy + Eq + fmt::Debug + Send + Sync + 'static {
    /// Stable registry id, e.g. `"folder_sync_lifecycle"`.
    const MACHINE_ID: &'static str;
    /// Human-friendly title.
    const DISPLAY_NAME: &'static str;
    /// Subsystem the machine belongs to (`"engine"`, `"cluster"`, `"p2p"`, ...).
    const SUBSYSTEM: &'static str;
    /// What the machine governs.
    const DESCRIPTION: &'static str;
    /// Entity kind reported in transition errors (`"folder"`, `"device"`, ...).
    const ENTITY: &'static str;

    /// Every state, in display order.
    fn states() -> &'static [Self];
    /// State a new entity starts in.
    fn initial() -> Self;
    /// Canonical wire name of the state (stable; used in JSON and errors).
    fn name(&self) -> &'static str;
    /// Human/agent-readable explanation of the state.
    fn describe(&self) -> &'static str;
    /// Terminal states have no way out.
    fn terminal(&self) -> bool {
        false
    }
    /// Whether moving to `next` is legal. Staying in the same state should be allowed
    /// when the operation is idempotent.
    fn permits(&self, next: Self) -> bool;
    /// User/agent actions that make sense in this state.
    fn actions(&self) -> Vec<&'static str>;

    /// States reachable in one step (excluding staying put).
    fn successors(&self) -> Vec<Self> {
        Self::states().iter().copied().filter(|n| *n != *self && self.permits(*n)).collect()
    }

    /// Whether an action name is currently available.
    fn allows_action(&self, action: &str) -> bool {
        self.actions().iter().any(|a| a.eq_ignore_ascii_case(action))
    }

    /// Introspection metadata for this state.
    fn meta(&self) -> StateMetadata {
        StateMetadata {
            state: self.name().to_string(),
            description: self.describe().to_string(),
            is_terminal: self.terminal(),
            allowed_transitions: self.successors().iter().map(|s| s.name().to_string()).collect(),
            available_actions: self.actions().into_iter().map(String::from).collect(),
        }
    }

    /// Structured error describing why `attempted` is not possible from this state.
    fn rejection(&self, entity_id: &str, attempted: &str) -> StateTransitionError {
        StateTransitionError {
            entity: Self::ENTITY.to_string(),
            entity_id: entity_id.to_string(),
            current_state: self.name().to_string(),
            attempted_transition: attempted.to_string(),
            allowed_transitions: self.successors().iter().map(|s| s.name().to_string()).collect(),
            available_actions: self.actions().into_iter().map(String::from).collect(),
            is_terminal: self.terminal(),
        }
    }

    /// Guarded transition: `Ok(next)` if legal, otherwise a structured error that tells
    /// the caller (UI or agent) what *is* possible.
    #[allow(clippy::result_large_err)] // rich, rarely-constructed public error type by design
    fn transition(&self, entity_id: &str, next: Self) -> Result<Self, StateTransitionError> {
        if self.permits(next) {
            Ok(next)
        } else {
            Err(self.rejection(entity_id, next.name()))
        }
    }

    /// Registry definition, generated from the items above.
    fn definition() -> StateMachineDef {
        StateMachineDef {
            machine_id: Self::MACHINE_ID.to_string(),
            display_name: Self::DISPLAY_NAME.to_string(),
            subsystem: Self::SUBSYSTEM.to_string(),
            description: Self::DESCRIPTION.to_string(),
            initial_state: Self::initial().name().to_string(),
            states: Self::states().iter().map(|s| s.meta()).collect(),
        }
    }
}

/// Snapshot of one entity's lifecycle, suitable for embedding in API responses so
/// clients render state and enable actions from the server instead of re-deriving them.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct LifecycleView {
    pub machine_id: String,
    pub state: String,
    pub description: String,
    pub is_terminal: bool,
    pub allowed_transitions: Vec<String>,
    pub available_actions: Vec<String>,
}

impl LifecycleView {
    pub fn of<S: StateMachine>(state: S) -> Self {
        let meta = state.meta();
        Self {
            machine_id: S::MACHINE_ID.to_string(),
            state: meta.state,
            description: meta.description,
            is_terminal: meta.is_terminal,
            allowed_transitions: meta.allowed_transitions,
            available_actions: meta.available_actions,
        }
    }
}

/// Returned when an operation targets an entity that does not exist (maps to HTTP 404).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct EntityNotFound {
    pub entity: String,
    pub entity_id: String,
}

impl fmt::Display for EntityNotFound {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} '{}' not found", self.entity, self.entity_id)
    }
}

impl std::error::Error for EntityNotFound {}

// =============================================================================
// 2. CONFLICT LIFECYCLE STATE MACHINE
// =============================================================================

/// Lifecycle status of a quarantined divergence conflict in Tier 2 / Tier 3.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[serde(rename_all = "UPPERCASE")]
pub enum ConflictStatus {
    /// Staged in `.synapse/quarantine/<conflict_id>.<ext>`. Awaiting human or agent decision.
    #[default]
    Pending,
    /// Resolved via `ACCEPT_LOCAL`, `ACCEPT_REMOTE`, `CUSTOM_MERGE`, or `KEEP_BOTH`.
    /// Staged file is unlinked; local disk and DAG revision are committed. Terminal state.
    Resolved,
    /// Dismissed or postponed by user/agent. Quarantined payload preserved.
    Ignored,
}

impl ConflictStatus {
    pub const ALL: [ConflictStatus; 3] = [
        ConflictStatus::Pending,
        ConflictStatus::Resolved,
        ConflictStatus::Ignored,
    ];

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Pending => "PENDING",
            Self::Resolved => "RESOLVED",
            Self::Ignored => "IGNORED",
        }
    }

    pub fn description(&self) -> &'static str {
        match self {
            Self::Pending => "Conflict is actively quarantined awaiting resolution decision",
            Self::Resolved => "Conflict has been reconciled and applied; working copy is up to date",
            Self::Ignored => "Conflict resolution was postponed or dismissed",
        }
    }

    pub fn is_terminal(&self) -> bool {
        matches!(self, Self::Resolved)
    }

    pub fn can_transition_to(&self, next: ConflictStatus) -> bool {
        match (self, next) {
            (Self::Pending, Self::Resolved) => true,
            (Self::Pending, Self::Ignored) => true,
            (Self::Ignored, Self::Pending) => true,
            (Self::Ignored, Self::Resolved) => true,
            // Resolved is terminal: cannot be re-resolved or reopened
            (Self::Resolved, _) => false,
            // Idempotent transitions
            (s1, s2) if *s1 == s2 => true,
            _ => false,
        }
    }

    pub fn allowed_transitions(&self) -> Vec<ConflictStatus> {
        match self {
            Self::Pending => vec![Self::Resolved, Self::Ignored],
            Self::Ignored => vec![Self::Pending, Self::Resolved],
            Self::Resolved => vec![],
        }
    }

    pub fn available_actions(&self) -> Vec<&'static str> {
        match self {
            Self::Pending => vec![
                "ACCEPT_LOCAL",
                "ACCEPT_REMOTE",
                "CUSTOM_MERGE",
                "KEEP_BOTH",
                "IGNORE",
            ],
            Self::Ignored => vec![
                "REOPEN",
                "ACCEPT_LOCAL",
                "ACCEPT_REMOTE",
                "CUSTOM_MERGE",
                "KEEP_BOTH",
            ],
            Self::Resolved => vec![],
        }
    }

    pub fn metadata(&self) -> StateMetadata {
        StateMetadata {
            state: self.as_str().to_string(),
            description: self.description().to_string(),
            is_terminal: self.is_terminal(),
            allowed_transitions: self
                .allowed_transitions()
                .into_iter()
                .map(|s| s.as_str().to_string())
                .collect(),
            available_actions: self
                .available_actions()
                .into_iter()
                .map(String::from)
                .collect(),
        }
    }
}

impl fmt::Display for ConflictStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

impl FromStr for ConflictStatus {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.trim().to_uppercase().as_str() {
            "PENDING" => Ok(ConflictStatus::Pending),
            "RESOLVED" => Ok(ConflictStatus::Resolved),
            "IGNORED" => Ok(ConflictStatus::Ignored),
            other => Err(format!("Unknown conflict status: '{}'", other)),
        }
    }
}

// =============================================================================
// 3. FOLDER SYNC LIFECYCLE STATE MACHINE
// =============================================================================

/// Dynamic operational synchronization state of a monitored folder.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum FolderSyncState {
    /// Folder is in steady state, watching for local filesystem modifications.
    #[default]
    Idle,
    /// Active recursive directory traversal, metadata hashing, and delta chunking in progress.
    Scanning,
    /// Active block transfer, remote revision download, or 3-way reconciliation in progress.
    Syncing,
    /// Folder synchronization is manually suspended by user. Watchers and indexing skipped.
    Paused,
    /// Folder encountered an unrecoverable fault (e.g. missing mount marker, permissions).
    Error,
}

impl FolderSyncState {
    pub const ALL: [FolderSyncState; 5] = [
        FolderSyncState::Idle,
        FolderSyncState::Scanning,
        FolderSyncState::Syncing,
        FolderSyncState::Paused,
        FolderSyncState::Error,
    ];

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Idle => "idle",
            Self::Scanning => "scanning",
            Self::Syncing => "syncing",
            Self::Paused => "paused",
            Self::Error => "error",
        }
    }

    pub fn description(&self) -> &'static str {
        match self {
            Self::Idle => "Folder is synchronized and actively monitoring filesystem events",
            Self::Scanning => "Folder is scanning directory structure and hashing local file deltas",
            Self::Syncing => "Folder is transferring blocks or reconciling incoming remote revisions",
            Self::Paused => "Folder synchronization is paused; no watcher or sync tasks run",
            Self::Error => "Folder encountered a filesystem error or mount safety marker check failure",
        }
    }

    pub fn is_terminal(&self) -> bool {
        false
    }

    pub fn can_transition_to(&self, next: FolderSyncState) -> bool {
        match (self, next) {
            // From Idle: can start scan, start sync, be paused, or encounter error
            (Self::Idle, Self::Scanning) => true,
            (Self::Idle, Self::Syncing) => true,
            (Self::Idle, Self::Paused) => true,
            (Self::Idle, Self::Error) => true,

            // From Scanning: returns to Idle, enters Syncing, can be Paused or Error
            (Self::Scanning, Self::Idle) => true,
            (Self::Scanning, Self::Syncing) => true,
            (Self::Scanning, Self::Paused) => true,
            (Self::Scanning, Self::Error) => true,

            // From Syncing: returns to Idle, can start another Scan, Paused, or Error
            (Self::Syncing, Self::Idle) => true,
            (Self::Syncing, Self::Scanning) => true,
            (Self::Syncing, Self::Paused) => true,
            (Self::Syncing, Self::Error) => true,

            // From Paused: can only be resumed to Idle or Scanning
            (Self::Paused, Self::Idle) => true,
            (Self::Paused, Self::Scanning) => true,

            // From Error: can be recovered to Idle or rescanned
            (Self::Error, Self::Idle) => true,
            (Self::Error, Self::Scanning) => true,

            (s1, s2) if *s1 == s2 => true,
            _ => false,
        }
    }

    pub fn allowed_transitions(&self) -> Vec<FolderSyncState> {
        Self::ALL
            .iter()
            .copied()
            .filter(|&target| self.can_transition_to(target))
            .collect()
    }

    pub fn available_actions(&self) -> Vec<&'static str> {
        match self {
            Self::Idle => vec!["RESCAN", "PAUSE", "DELETE", "SET_IGNORES", "SHARE"],
            Self::Scanning => vec!["CANCEL", "PAUSE"],
            Self::Syncing => vec!["PAUSE"],
            Self::Paused => vec!["RESUME", "DELETE", "SET_IGNORES"],
            Self::Error => vec!["RESCAN", "RETRY_MOUNT", "DELETE"],
        }
    }

    pub fn metadata(&self) -> StateMetadata {
        StateMetadata {
            state: self.as_str().to_string(),
            description: self.description().to_string(),
            is_terminal: self.is_terminal(),
            allowed_transitions: self
                .allowed_transitions()
                .into_iter()
                .map(|s| s.as_str().to_string())
                .collect(),
            available_actions: self
                .available_actions()
                .into_iter()
                .map(String::from)
                .collect(),
        }
    }
}

impl fmt::Display for FolderSyncState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

impl FromStr for FolderSyncState {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.trim().to_lowercase().as_str() {
            "idle" => Ok(FolderSyncState::Idle),
            "scanning" => Ok(FolderSyncState::Scanning),
            "syncing" => Ok(FolderSyncState::Syncing),
            "paused" => Ok(FolderSyncState::Paused),
            "error" => Ok(FolderSyncState::Error),
            other => Err(format!("Unknown folder sync state: '{}'", other)),
        }
    }
}

// =============================================================================
// 4. FOLDER SYNC MODE POLICY
// =============================================================================

/// Synchronization directionality constraint for a folder.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
pub enum SyncMode {
    /// Bi-directional: propagates local edits and accepts remote edits.
    #[default]
    SendReceive,
    /// Unidirectional upload: propagates local changes to peers, rejects incoming remote modifications.
    SendOnly,
    /// Unidirectional download: accepts remote peer changes, ignores/discards local disk edits.
    ReceiveOnly,
}

impl SyncMode {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::SendReceive => "SendReceive",
            Self::SendOnly => "SendOnly",
            Self::ReceiveOnly => "ReceiveOnly",
        }
    }

    pub fn can_send(&self) -> bool {
        matches!(self, Self::SendReceive | Self::SendOnly)
    }

    pub fn can_receive(&self) -> bool {
        matches!(self, Self::SendReceive | Self::ReceiveOnly)
    }
}

impl fmt::Display for SyncMode {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

impl FromStr for SyncMode {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.trim().to_lowercase().as_str() {
            "sendreceive" | "send_receive" => Ok(SyncMode::SendReceive),
            "sendonly" | "send_only" => Ok(SyncMode::SendOnly),
            "receiveonly" | "receive_only" => Ok(SyncMode::ReceiveOnly),
            other => Err(format!("Unknown sync mode: '{}'", other)),
        }
    }
}

// =============================================================================
// 5. CONFLICT POLICY
// =============================================================================

/// Strategy used when concurrent divergent revisions occur.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
pub enum ConflictPolicy {
    /// Tier 1 3-way semantic diff3 merge $\to$ Tier 2 virtual quarantine. Zero pollution.
    #[default]
    ThreeWayAuto,
    /// Last-Write-Wins: revision with largest modification timestamp supersedes cleanly.
    Lww,
    /// Manual: bypass Tier 1 automated merge and always stage in virtual quarantine.
    Manual,
}

impl ConflictPolicy {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::ThreeWayAuto => "THREE_WAY_AUTO",
            Self::Lww => "LWW",
            Self::Manual => "MANUAL",
        }
    }
}

impl fmt::Display for ConflictPolicy {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

impl FromStr for ConflictPolicy {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.trim().to_uppercase().as_str() {
            "THREE_WAY_AUTO" | "THREEWAYAUTO" | "AUTO" => Ok(ConflictPolicy::ThreeWayAuto),
            "LWW" | "LAST_WRITE_WINS" => Ok(ConflictPolicy::Lww),
            "MANUAL" => Ok(ConflictPolicy::Manual),
            other => Err(format!("Unknown conflict policy: '{}'", other)),
        }
    }
}

// =============================================================================
// 6. CLUSTER DEVICE PAIRING STATE MACHINE
// =============================================================================

/// Pairing and cluster membership lifecycle for a remote device.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum DevicePairingState {
    /// Discovered over UDP broadcast or announced by introducer, awaiting explicit pairing approval.
    #[default]
    Pending,
    /// Accepted into cluster configuration; eligible for block transfer and QUIC connection.
    Paired,
    /// Paired but communication temporarily suspended.
    Paused,
    /// Pairing request explicitly rejected and removed from pending queue.
    Rejected,
}

impl DevicePairingState {
    pub const ALL: [DevicePairingState; 4] = [
        DevicePairingState::Pending,
        DevicePairingState::Paired,
        DevicePairingState::Paused,
        DevicePairingState::Rejected,
    ];

    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Pending => "pending",
            Self::Paired => "paired",
            Self::Paused => "paused",
            Self::Rejected => "rejected",
        }
    }

    pub fn description(&self) -> &'static str {
        match self {
            Self::Pending => "Device pairing request is pending user or agent confirmation",
            Self::Paired => "Device is mutually paired and participating in the cluster",
            Self::Paused => "Device communication is temporarily paused",
            Self::Rejected => "Device pairing request was rejected; further requests from it are ignored until un-rejected",
        }
    }

    pub fn can_transition_to(&self, next: DevicePairingState) -> bool {
        match (self, next) {
            (Self::Pending, Self::Paired) => true,
            (Self::Pending, Self::Rejected) => true,
            (Self::Paired, Self::Paused) => true,
            (Self::Paused, Self::Paired) => true,
            // Un-reject: the device may ask to pair again
            (Self::Rejected, Self::Pending) => true,
            (s1, s2) if *s1 == s2 => true,
            _ => false,
        }
    }

    pub fn allowed_transitions(&self) -> Vec<DevicePairingState> {
        Self::ALL
            .iter()
            .copied()
            .filter(|&target| self.can_transition_to(target))
            .collect()
    }

    pub fn available_actions(&self) -> Vec<&'static str> {
        match self {
            Self::Pending => vec!["ACCEPT", "REJECT"],
            Self::Paired => vec!["PAUSE", "REMOVE", "SHARE_FOLDER", "UNSHARE_FOLDER"],
            Self::Paused => vec!["RESUME", "REMOVE"],
            Self::Rejected => vec!["UNREJECT"],
        }
    }

    pub fn metadata(&self) -> StateMetadata {
        StateMetadata {
            state: self.as_str().to_string(),
            description: self.description().to_string(),
            is_terminal: false,
            allowed_transitions: self
                .allowed_transitions()
                .into_iter()
                .map(|s| s.as_str().to_string())
                .collect(),
            available_actions: self
                .available_actions()
                .into_iter()
                .map(String::from)
                .collect(),
        }
    }
}

impl fmt::Display for DevicePairingState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

impl FromStr for DevicePairingState {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        match s.trim().to_lowercase().as_str() {
            "pending" => Ok(DevicePairingState::Pending),
            "paired" => Ok(DevicePairingState::Paired),
            "paused" => Ok(DevicePairingState::Paused),
            "rejected" => Ok(DevicePairingState::Rejected),
            other => Err(format!("Unknown device pairing state: '{}'", other)),
        }
    }
}

// =============================================================================
// 7. SWARM CHUNK REPLICATION STATE MACHINE
// =============================================================================

/// Download lifecycle of an individual Content-Addressable chunk in the Swarm Scheduler.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "status", content = "details")]
pub enum ChunkDownloadState {
    /// Chunk is needed for a queued file but has not yet been assigned to a peer.
    Pending,
    /// Chunk has been requested from a specific peer over QUIC stream and is awaiting data.
    InFlight {
        peer_id: String,
        elapsed_millis: u64,
    },
    /// Chunk has been fetched, verified by BLAKE3 hash, and committed to CAS block store.
    Completed,
    /// Chunk download failed or timed out; eligible for requeueing or terminal failure.
    Failed {
        reason: String,
        attempts: usize,
    },
}

impl ChunkDownloadState {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Pending => "PENDING",
            Self::InFlight { .. } => "IN_FLIGHT",
            Self::Completed => "COMPLETED",
            Self::Failed { .. } => "FAILED",
        }
    }

    pub fn description(&self) -> &'static str {
        match self {
            Self::Pending => "Chunk is queued in scheduler awaiting peer assignment",
            Self::InFlight { .. } => "Chunk request is actively in-flight to a swarm peer",
            Self::Completed => "Chunk was successfully downloaded, verified, and saved to CAS",
            Self::Failed { .. } => "Chunk transfer failed and requires retry or peer escalation",
        }
    }

    pub fn is_terminal(&self) -> bool {
        matches!(self, Self::Completed)
    }
}

// =============================================================================
// 8. 3-TIER CONFLICT RESOLUTION PIPELINE MODEL
// =============================================================================

/// Tier classification within the Multi-Strategy Conflict Resolution Pipeline.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum ConflictPipelineTier {
    /// Tier 1: Automated 3-way diff3 merge for text files with non-overlapping edits.
    Tier1AutoMerge,
    /// Tier 2: Non-polluting virtual quarantine into `.synapse/quarantine/<id>.<ext>`.
    Tier2VirtualQuarantine,
    /// Tier 3: Interactive UI visual diff inspection and user decision execution.
    Tier3Interactive,
}

impl ConflictPipelineTier {
    pub fn name(&self) -> &'static str {
        match self {
            Self::Tier1AutoMerge => "Tier 1: Automated 3-Way Semantic Merge",
            Self::Tier2VirtualQuarantine => "Tier 2: Zero-Pollution Virtual Quarantine",
            Self::Tier3Interactive => "Tier 3: Interactive Visual Resolution",
        }
    }
}


// =============================================================================
// 8b. StateMachine IMPLEMENTATIONS (existing enums keep their inherent API)
// =============================================================================

impl StateMachine for ConflictStatus {
    const MACHINE_ID: &'static str = "conflict_lifecycle";
    const DISPLAY_NAME: &'static str = "Quarantine Conflict Resolution State Machine";
    const SUBSYSTEM: &'static str = "conflict";
    const DESCRIPTION: &'static str = "Controls the staging, inspection, and 3-way resolution of divergent concurrent edits without filesystem pollution.";
    const ENTITY: &'static str = "conflict";
    fn states() -> &'static [Self] { &Self::ALL }
    fn initial() -> Self { Self::Pending }
    fn name(&self) -> &'static str { self.as_str() }
    fn describe(&self) -> &'static str { self.description() }
    fn terminal(&self) -> bool { self.is_terminal() }
    fn permits(&self, next: Self) -> bool { self.can_transition_to(next) }
    fn actions(&self) -> Vec<&'static str> { self.available_actions() }
    fn successors(&self) -> Vec<Self> { self.allowed_transitions() }
}

impl StateMachine for FolderSyncState {
    const MACHINE_ID: &'static str = "folder_sync_lifecycle";
    const DISPLAY_NAME: &'static str = "Folder Synchronization Lifecycle State Machine";
    const SUBSYSTEM: &'static str = "engine";
    const DESCRIPTION: &'static str = "Monitors the continuous indexing, delta chunking, and block transfer phases for registered synchronization roots.";
    const ENTITY: &'static str = "folder";
    fn states() -> &'static [Self] { &Self::ALL }
    fn initial() -> Self { Self::Idle }
    fn name(&self) -> &'static str { self.as_str() }
    fn describe(&self) -> &'static str { self.description() }
    fn terminal(&self) -> bool { self.is_terminal() }
    fn permits(&self, next: Self) -> bool { self.can_transition_to(next) }
    fn actions(&self) -> Vec<&'static str> { self.available_actions() }
    fn successors(&self) -> Vec<Self> { self.allowed_transitions() }
}

/// User/agent commands on a device. Each is only valid from specific states, so e.g.
/// "accept" cannot be used to resume a paused device even though both end in `paired`.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeviceAction {
    Accept,
    Reject,
    Pause,
    Resume,
    Unreject,
}

impl DeviceAction {
    /// Action name as listed in `available_actions`.
    pub fn name(&self) -> &'static str {
        match self {
            Self::Accept => "ACCEPT",
            Self::Reject => "REJECT",
            Self::Pause => "PAUSE",
            Self::Resume => "RESUME",
            Self::Unreject => "UNREJECT",
        }
    }

    /// State the device ends up in.
    pub fn target(&self) -> DevicePairingState {
        match self {
            Self::Accept | Self::Resume => DevicePairingState::Paired,
            Self::Reject => DevicePairingState::Rejected,
            Self::Pause => DevicePairingState::Paused,
            Self::Unreject => DevicePairingState::Pending,
        }
    }

    /// Validates the action from `current`. Repeating an action whose result already holds
    /// (pausing a paused device, accepting a paired one) is an idempotent no-op.
    #[allow(clippy::result_large_err)]
    pub fn check(&self, device_id: &str, current: DevicePairingState) -> Result<DevicePairingState, StateTransitionError> {
        let target = self.target();
        if current == target {
            return Ok(target);
        }
        if !current.allows_action(self.name()) {
            return Err(current.rejection(device_id, target.name()));
        }
        current.transition(device_id, target)
    }
}

impl StateMachine for DevicePairingState {
    const MACHINE_ID: &'static str = "device_pairing_lifecycle";
    const DISPLAY_NAME: &'static str = "Cluster Device Pairing State Machine";
    const SUBSYSTEM: &'static str = "cluster";
    const DESCRIPTION: &'static str = "Governs node discovery, mutual cryptographic handshake, trust verification, and cluster participation.";
    const ENTITY: &'static str = "device";
    fn states() -> &'static [Self] { &Self::ALL }
    fn initial() -> Self { Self::Pending }
    fn name(&self) -> &'static str { self.as_str() }
    fn describe(&self) -> &'static str { self.description() }
    fn permits(&self, next: Self) -> bool { self.can_transition_to(next) }
    fn actions(&self) -> Vec<&'static str> { self.available_actions() }
    fn successors(&self) -> Vec<Self> { self.allowed_transitions() }
}

// =============================================================================
// 10. PEER CONNECTION STATE MACHINE
// =============================================================================

/// Live network presence of a paired device, derived from when it was last heard from.
///
/// Replaces the old implicit rule `is_connected = !is_paused`, which reported every
/// unpaused device as connected even if it had never been seen.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum PeerConnectionState {
    /// Paired, but not heard from since this engine started.
    #[default]
    Unknown,
    /// Heard from within the presence window.
    Online,
    /// Was online, but has not been heard from within the presence window.
    Offline,
    /// Communication with the device is paused by the user.
    Paused,
}

impl PeerConnectionState {
    pub const ALL: [PeerConnectionState; 4] = [Self::Unknown, Self::Online, Self::Offline, Self::Paused];

    /// Derives the state from facts. `seen_secs_ago` is `None` if never seen.
    pub fn derive(paused: bool, seen_secs_ago: Option<u64>, presence_window_secs: u64) -> Self {
        if paused {
            return Self::Paused;
        }
        match seen_secs_ago {
            None => Self::Unknown,
            Some(age) if age <= presence_window_secs => Self::Online,
            Some(_) => Self::Offline,
        }
    }

    pub fn is_connected(&self) -> bool {
        matches!(self, Self::Online)
    }
}

impl StateMachine for PeerConnectionState {
    const MACHINE_ID: &'static str = "peer_connection_lifecycle";
    const DISPLAY_NAME: &'static str = "Peer Connection Presence State Machine";
    const SUBSYSTEM: &'static str = "p2p";
    const DESCRIPTION: &'static str = "Tracks whether a paired device is currently reachable, based on discovery and protocol traffic within a presence window.";
    const ENTITY: &'static str = "peer";
    fn states() -> &'static [Self] { &Self::ALL }
    fn initial() -> Self { Self::Unknown }
    fn name(&self) -> &'static str {
        match self {
            Self::Unknown => "unknown",
            Self::Online => "online",
            Self::Offline => "offline",
            Self::Paused => "paused",
        }
    }
    fn describe(&self) -> &'static str {
        match self {
            Self::Unknown => "Device is paired but has not been seen on the network since the engine started",
            Self::Online => "Device was heard from within the presence window and is reachable",
            Self::Offline => "Device was seen before but has not been heard from within the presence window",
            Self::Paused => "Communication with this device is paused",
        }
    }
    fn permits(&self, next: Self) -> bool {
        use PeerConnectionState::*;
        matches!(
            (self, next),
            (Unknown, Online) | (Unknown, Paused)
                | (Online, Offline) | (Online, Paused)
                | (Offline, Online) | (Offline, Paused)
                | (Paused, Unknown) | (Paused, Online) | (Paused, Offline)
        ) || *self == next
    }
    fn actions(&self) -> Vec<&'static str> {
        match self {
            Self::Paused => vec!["RESUME"],
            _ => vec!["PAUSE"],
        }
    }
}

impl fmt::Display for PeerConnectionState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.name())
    }
}

// =============================================================================
// 11. ENGINE LIFECYCLE STATE MACHINE
// =============================================================================

/// Lifecycle of the Synapse Core engine process / library instance.
///
/// Replaces the implicit `RUNNING: AtomicBool` and the no-op shutdown endpoint.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum EngineLifecycleState {
    /// Not running.
    #[default]
    Stopped,
    /// Opening storage, loading identity, binding listeners.
    Starting,
    /// Serving the API and syncing.
    Running,
    /// Graceful shutdown requested; finishing in-flight work.
    Stopping,
    /// Startup or runtime failure; can be started again.
    Failed,
}

impl EngineLifecycleState {
    pub const ALL: [EngineLifecycleState; 5] =
        [Self::Stopped, Self::Starting, Self::Running, Self::Stopping, Self::Failed];

    pub fn as_u8(self) -> u8 {
        match self {
            Self::Stopped => 0,
            Self::Starting => 1,
            Self::Running => 2,
            Self::Stopping => 3,
            Self::Failed => 4,
        }
    }

    pub fn from_u8(v: u8) -> Self {
        match v {
            1 => Self::Starting,
            2 => Self::Running,
            3 => Self::Stopping,
            4 => Self::Failed,
            _ => Self::Stopped,
        }
    }
}

impl StateMachine for EngineLifecycleState {
    const MACHINE_ID: &'static str = "engine_lifecycle";
    const DISPLAY_NAME: &'static str = "Engine Process Lifecycle State Machine";
    const SUBSYSTEM: &'static str = "engine";
    const DESCRIPTION: &'static str = "Start-up, steady operation, and graceful shutdown of the Synapse Core engine (daemon or embedded library).";
    const ENTITY: &'static str = "engine";
    fn states() -> &'static [Self] { &Self::ALL }
    fn initial() -> Self { Self::Stopped }
    fn name(&self) -> &'static str {
        match self {
            Self::Stopped => "stopped",
            Self::Starting => "starting",
            Self::Running => "running",
            Self::Stopping => "stopping",
            Self::Failed => "failed",
        }
    }
    fn describe(&self) -> &'static str {
        match self {
            Self::Stopped => "Engine is not running",
            Self::Starting => "Engine is opening storage, loading identity, and binding listeners",
            Self::Running => "Engine is serving the control API and synchronizing folders",
            Self::Stopping => "Graceful shutdown in progress; in-flight work is finishing",
            Self::Failed => "Engine failed to start or stopped unexpectedly",
        }
    }
    fn permits(&self, next: Self) -> bool {
        use EngineLifecycleState::*;
        matches!(
            (self, next),
            (Stopped, Starting)
                | (Starting, Running) | (Starting, Failed) | (Starting, Stopping)
                | (Running, Stopping) | (Running, Failed)
                | (Stopping, Stopped)
                | (Failed, Starting)
        ) || *self == next
    }
    fn actions(&self) -> Vec<&'static str> {
        match self {
            Self::Stopped | Self::Failed => vec!["START"],
            Self::Starting | Self::Running => vec!["SHUTDOWN"],
            Self::Stopping => vec![],
        }
    }
}

impl fmt::Display for EngineLifecycleState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.name())
    }
}

// =============================================================================
// 9. COMPLETE STATE MACHINE REGISTRY & INTROSPECTION
// =============================================================================

/// Returns the comprehensive catalog of all formal state machines in Synapse Core.
///
/// Designed specifically for AI agent discovery (AX) and dynamic runtime verification.
pub fn get_all_state_machines() -> Vec<StateMachineDef> {
    vec![
        ConflictStatus::definition(),
        FolderSyncState::definition(),
        DevicePairingState::definition(),
        StateMachineDef {
            machine_id: "relay_session_lifecycle".to_string(),
            display_name: "Encrypted Relay Fallback Session State Machine".to_string(),
            subsystem: "p2p".to_string(),
            description: "Sequences zero-trust end-to-end encrypted relay rendezvous and tunnel data transmission across symmetric NATs.".to_string(),
            initial_state: "disconnected".to_string(),
            states: vec![
                StateMetadata {
                    state: "disconnected".to_string(),
                    description: "No active connection to fallback relay".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["connecting".to_string()],
                    available_actions: vec!["CONNECT".to_string()],
                },
                StateMetadata {
                    state: "connecting".to_string(),
                    description: "TCP/TLS handshake to relay server in progress".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["joined".to_string(), "closed".to_string()],
                    available_actions: vec!["ABORT".to_string()],
                },
                StateMetadata {
                    state: "joined".to_string(),
                    description: "Authenticated with relay server via signed RelayJoin".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["invited".to_string(), "active".to_string(), "closed".to_string()],
                    available_actions: vec!["INVITE_PEER".to_string(), "DISCONNECT".to_string()],
                },
                StateMetadata {
                    state: "invited".to_string(),
                    description: "RelayInvitation dispatched to target peer; awaiting rendezvous".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["active".to_string(), "closed".to_string()],
                    available_actions: vec!["CANCEL_INVITATION".to_string()],
                },
                StateMetadata {
                    state: "active".to_string(),
                    description: "Encrypted tunnel established; bidirectional blind forwarding active".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["closed".to_string()],
                    available_actions: vec!["SEND_TUNNEL_DATA".to_string(), "CLOSE_SESSION".to_string()],
                },
                StateMetadata {
                    state: "closed".to_string(),
                    description: "Relay tunnel cleanly torn down".to_string(),
                    is_terminal: true,
                    allowed_transitions: vec![],
                    available_actions: vec![],
                },
            ],
        },
        StateMachineDef {
            machine_id: "swarm_chunk_lifecycle".to_string(),
            display_name: "Swarm Chunk Replication State Machine".to_string(),
            subsystem: "swarm".to_string(),
            description: "Tracks individual CAS chunk replication across multiple parallel QUIC swarm peers with rarest-first scheduling.".to_string(),
            initial_state: "PENDING".to_string(),
            states: vec![
                StateMetadata {
                    state: "PENDING".to_string(),
                    description: "Chunk needed for file reconstruction; awaiting peer assignment".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["IN_FLIGHT".to_string()],
                    available_actions: vec!["SCHEDULE".to_string()],
                },
                StateMetadata {
                    state: "IN_FLIGHT".to_string(),
                    description: "Chunk requested from target peer over QUIC stream".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["COMPLETED".to_string(), "FAILED".to_string(), "PENDING".to_string()],
                    available_actions: vec!["CANCEL".to_string(), "TIMEOUT".to_string()],
                },
                StateMetadata {
                    state: "COMPLETED".to_string(),
                    description: "Chunk received, BLAKE3 verified, and written to CAS".to_string(),
                    is_terminal: true,
                    allowed_transitions: vec![],
                    available_actions: vec![],
                },
                StateMetadata {
                    state: "FAILED".to_string(),
                    description: "Chunk request timed out or received corrupt data; requeued".to_string(),
                    is_terminal: false,
                    allowed_transitions: vec!["PENDING".to_string()],
                    available_actions: vec!["RETRY".to_string()],
                },
            ],
        },
        PeerConnectionState::definition(),
        EngineLifecycleState::definition(),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_conflict_status_transitions_and_guards() {
        let pending = ConflictStatus::Pending;
        assert!(pending.can_transition_to(ConflictStatus::Resolved));
        assert!(pending.can_transition_to(ConflictStatus::Ignored));
        assert!(!pending.is_terminal());

        let resolved = ConflictStatus::Resolved;
        assert!(resolved.is_terminal());
        // Resolved cannot transition anywhere
        assert!(!resolved.can_transition_to(ConflictStatus::Pending));
        assert!(!resolved.can_transition_to(ConflictStatus::Ignored));

        let ignored = ConflictStatus::Ignored;
        assert!(ignored.can_transition_to(ConflictStatus::Pending));
        assert!(ignored.can_transition_to(ConflictStatus::Resolved));
    }

    #[test]
    fn test_conflict_status_string_and_serde_roundtrip() {
        assert_eq!(ConflictStatus::Pending.to_string(), "PENDING");
        assert_eq!(ConflictStatus::Resolved.to_string(), "RESOLVED");
        assert_eq!(ConflictStatus::Ignored.to_string(), "IGNORED");

        assert_eq!(ConflictStatus::from_str("pending").unwrap(), ConflictStatus::Pending);
        assert_eq!(ConflictStatus::from_str("RESOLVED").unwrap(), ConflictStatus::Resolved);
        assert_eq!(ConflictStatus::from_str("ignored").unwrap(), ConflictStatus::Ignored);

        let json = serde_json::to_string(&ConflictStatus::Pending).unwrap();
        assert_eq!(json, "\"PENDING\"");
        let deserialized: ConflictStatus = serde_json::from_str(&json).unwrap();
        assert_eq!(deserialized, ConflictStatus::Pending);
    }

    #[test]
    fn test_folder_sync_state_transitions() {
        let idle = FolderSyncState::Idle;
        assert!(idle.can_transition_to(FolderSyncState::Scanning));
        assert!(idle.can_transition_to(FolderSyncState::Syncing));
        assert!(idle.can_transition_to(FolderSyncState::Paused));
        assert!(idle.can_transition_to(FolderSyncState::Error));

        let scanning = FolderSyncState::Scanning;
        assert!(scanning.can_transition_to(FolderSyncState::Idle));
        assert!(scanning.can_transition_to(FolderSyncState::Syncing));

        let paused = FolderSyncState::Paused;
        assert!(paused.can_transition_to(FolderSyncState::Idle));
        assert!(!paused.can_transition_to(FolderSyncState::Syncing));

        let json = serde_json::to_string(&FolderSyncState::Scanning).unwrap();
        assert_eq!(json, "\"scanning\"");
    }

    #[test]
    fn test_all_state_machines_registry_intact() {
        let registry = get_all_state_machines();
        assert_eq!(registry.len(), 7);

        let conflict_sm = registry.iter().find(|m| m.machine_id == "conflict_lifecycle").unwrap();
        assert_eq!(conflict_sm.initial_state, "PENDING");
        assert_eq!(conflict_sm.states.len(), 3);

        let folder_sm = registry.iter().find(|m| m.machine_id == "folder_sync_lifecycle").unwrap();
        assert_eq!(folder_sm.initial_state, "idle");
        assert_eq!(folder_sm.states.len(), 5);
    }

    // ---------------------------------------------------------------------
    // Shared StateMachine trait: invariants that every machine must satisfy
    // ---------------------------------------------------------------------

    fn check_machine<S: StateMachine>() {
        let states = S::states();
        assert!(states.contains(&S::initial()), "{}: initial state not in states()", S::MACHINE_ID);
        let mut names: Vec<&str> = states.iter().map(|s| s.name()).collect();
        names.sort();
        names.dedup();
        assert_eq!(names.len(), states.len(), "{}: duplicate state names", S::MACHINE_ID);
        for s in states {
            assert!(s.permits(*s) || s.terminal(), "{}: '{}' should allow idempotent re-entry", S::MACHINE_ID, s.name());
            for n in s.successors() {
                assert!(s.permits(n), "{}: successor '{}' of '{}' not permitted", S::MACHINE_ID, n.name(), s.name());
                assert!(s.transition("x", n).is_ok());
            }
            for n in states {
                if !s.permits(*n) {
                    let err = s.transition("id-1", *n).unwrap_err();
                    assert_eq!(err.entity, S::ENTITY);
                    assert_eq!(err.entity_id, "id-1");
                    assert_eq!(err.current_state, s.name());
                    assert_eq!(err.attempted_transition, n.name());
                    assert_eq!(err.available_actions, s.meta().available_actions);
                }
            }
            if s.terminal() {
                assert!(s.successors().is_empty(), "{}: terminal '{}' has successors", S::MACHINE_ID, s.name());
            }
        }
        let def = S::definition();
        assert_eq!(def.machine_id, S::MACHINE_ID);
        assert_eq!(def.states.len(), states.len());
        assert!(
            get_all_state_machines().iter().any(|m| *m == def),
            "{}: registry entry differs from the generated definition",
            S::MACHINE_ID
        );
    }

    #[test]
    fn test_every_state_machine_satisfies_trait_invariants() {
        check_machine::<ConflictStatus>();
        check_machine::<FolderSyncState>();
        check_machine::<DevicePairingState>();
        check_machine::<PeerConnectionState>();
        check_machine::<EngineLifecycleState>();
    }

    #[test]
    fn test_trait_metadata_matches_legacy_inherent_metadata() {
        for s in ConflictStatus::ALL { assert_eq!(StateMachine::meta(&s), s.metadata()); }
        for s in FolderSyncState::ALL { assert_eq!(StateMachine::meta(&s), s.metadata()); }
        for s in DevicePairingState::ALL { assert_eq!(StateMachine::meta(&s), s.metadata()); }
    }

    #[test]
    fn test_registry_ids_unique_and_complete() {
        let reg = get_all_state_machines();
        let mut ids: Vec<&str> = reg.iter().map(|m| m.machine_id.as_str()).collect();
        ids.sort();
        let before = ids.len();
        ids.dedup();
        assert_eq!(before, ids.len(), "duplicate machine ids");
        for id in ["conflict_lifecycle", "folder_sync_lifecycle", "device_pairing_lifecycle",
                   "relay_session_lifecycle", "swarm_chunk_lifecycle",
                   "peer_connection_lifecycle", "engine_lifecycle"] {
            assert!(ids.contains(&id), "missing {}", id);
        }
    }

    #[test]
    fn test_device_rejection_can_be_undone() {
        let r = DevicePairingState::Rejected;
        assert!(!StateMachine::terminal(&r));
        assert!(r.successors().contains(&DevicePairingState::Pending));
        assert!(!r.successors().contains(&DevicePairingState::Paired));
        assert!(r.allows_action("unreject"));
        assert!(r.transition("d", DevicePairingState::Paired).is_err());
    }

    #[test]
    fn test_peer_connection_state_derivation() {
        use PeerConnectionState::*;
        assert_eq!(PeerConnectionState::derive(false, None, 30), Unknown);
        assert_eq!(PeerConnectionState::derive(false, Some(0), 30), Online);
        assert_eq!(PeerConnectionState::derive(false, Some(30), 30), Online);
        assert_eq!(PeerConnectionState::derive(false, Some(31), 30), Offline);
        assert_eq!(PeerConnectionState::derive(true, Some(0), 30), Paused);
        assert_eq!(PeerConnectionState::derive(true, None, 30), Paused);
        assert!(Online.is_connected());
        assert!(!Unknown.is_connected() && !Offline.is_connected() && !Paused.is_connected());
        assert_eq!(serde_json::to_string(&Offline).unwrap(), "\"offline\"");
    }

    #[test]
    fn test_engine_lifecycle_transitions() {
        use EngineLifecycleState::*;
        assert!(Stopped.transition("e", Starting).is_ok());
        assert!(Starting.transition("e", Running).is_ok());
        assert!(Running.transition("e", Stopping).is_ok());
        assert!(Stopping.transition("e", Stopped).is_ok());
        assert!(Failed.transition("e", Starting).is_ok());
        // Can't skip graceful shutdown or start twice
        assert!(Running.transition("e", Stopped).is_err());
        assert!(Running.transition("e", Starting).is_err());
        assert!(Stopping.transition("e", Running).is_err());
        for s in EngineLifecycleState::ALL {
            assert_eq!(EngineLifecycleState::from_u8(s.as_u8()), s);
        }
    }

    #[test]
    fn test_lifecycle_view_serialization() {
        let v = LifecycleView::of(FolderSyncState::Error);
        assert_eq!(v.machine_id, "folder_sync_lifecycle");
        assert_eq!(v.state, "error");
        assert!(v.available_actions.contains(&"RESCAN".to_string()));
        let json = serde_json::to_value(&v).unwrap();
        assert_eq!(json["state"], "error");
    }
}
