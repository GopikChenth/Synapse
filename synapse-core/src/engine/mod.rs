//! Core Synchronization Engine, File Indexer, and Filesystem Watcher.
//!
//! Orchestrates Content-Defined Chunking, CAS persistence, Merkle DAG updates,
//! and continuous filesystem change tracking via `notify`.

pub mod fs_utils;
pub mod ignore;
pub mod versioning;

use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use notify::{Event, EventKind, RecommendedWatcher, RecursiveMode, Watcher};
use tokio::sync::broadcast;
use tracing::{info, warn};

use crate::chunker::Chunker;
use crate::conflict::{ConflictEngine, CoreEvent};
use std::collections::HashMap;
use std::sync::RwLock;

use crate::dag::{LcaResult, MerkleDag, RevisionNode};
use crate::engine::fs_utils::{
    atomic_write_file, ensure_folder_marker, sanitize_relative_path,
    verify_folder_marker,
};
use crate::engine::ignore::IgnoreEngine;
use crate::state::{FolderSyncState, LifecycleView, StateMachine};
use crate::storage::{FileRecord, RevisionRecord, StorageEngine};

/// The kinds of work a folder can be doing. Each running unit of work holds a
/// [`FolderWorkGuard`]; the folder's lifecycle state is *derived* from what is running,
/// so it can never get stuck in `scanning`/`syncing` after an error or panic.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FolderWork {
    Scan,
    Sync,
}

/// Why a folder is in the `error` state.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct FolderError {
    pub message: String,
    /// Unix seconds when the error was recorded.
    pub since: i64,
}

#[derive(Debug, Default, Clone)]
struct FolderActivity {
    scans: u32,
    syncs: u32,
    last_error: Option<FolderError>,
}

/// RAII handle for running folder work. Dropping it (normally, on `?`, or during a panic)
/// ends the work and re-derives the folder state.
pub struct FolderWorkGuard {
    engine: SyncEngine,
    folder_id: String,
    work: FolderWork,
}

impl Drop for FolderWorkGuard {
    fn drop(&mut self) {
        self.engine.end_work(&self.folder_id, self.work);
    }
}

#[derive(Clone)]
pub struct SyncEngine {
    storage: StorageEngine,
    chunker: Arc<Chunker>,
    conflict_engine: Arc<ConflictEngine>,
    event_sender: broadcast::Sender<CoreEvent>,
    device_id: String,
    /// Manual state overrides (tools/tests). The engine itself never writes here.
    folder_states: Arc<RwLock<HashMap<String, FolderSyncState>>>,
    folder_activity: Arc<std::sync::Mutex<HashMap<String, FolderActivity>>>,
    /// Live filesystem watchers per folder. Dropping a watcher stops it.
    watchers: Arc<std::sync::Mutex<HashMap<String, RecommendedWatcher>>>,
}

impl SyncEngine {
    pub fn new(
        storage: StorageEngine,
        conflict_engine: Arc<ConflictEngine>,
        event_sender: broadcast::Sender<CoreEvent>,
        device_id: String,
    ) -> Self {
        Self {
            storage,
            chunker: Arc::new(Chunker::default()),
            conflict_engine,
            event_sender,
            device_id,
            folder_states: Arc::new(RwLock::new(HashMap::new())),
            folder_activity: Arc::new(std::sync::Mutex::new(HashMap::new())),
            watchers: Arc::new(std::sync::Mutex::new(HashMap::new())),
        }
    }

    /// Current lifecycle state of a folder, derived (in priority order) from:
    /// paused flag → running sync → running scan → last recorded error → manual override → idle.
    pub fn get_folder_state(&self, folder_id: &str) -> FolderSyncState {
        let activity = self
            .folder_activity
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .get(folder_id)
            .cloned();
        self.derive_folder_state(folder_id, activity.as_ref())
    }

    fn derive_folder_state(&self, folder_id: &str, activity: Option<&FolderActivity>) -> FolderSyncState {
        if let Ok(Some(folder)) = self.storage.get_folder(folder_id) {
            if folder.is_paused {
                return FolderSyncState::Paused;
            }
        }
        if let Some(a) = activity {
            if a.syncs > 0 {
                return FolderSyncState::Syncing;
            }
            if a.scans > 0 {
                return FolderSyncState::Scanning;
            }
            if a.last_error.is_some() {
                return FolderSyncState::Error;
            }
        }
        if let Ok(states) = self.folder_states.read() {
            if let Some(&state) = states.get(folder_id) {
                return state;
            }
        }
        FolderSyncState::Idle
    }

    /// Why the folder is in `error`, if it is.
    pub fn folder_error(&self, folder_id: &str) -> Option<FolderError> {
        self.folder_activity
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .get(folder_id)
            .and_then(|a| a.last_error.clone())
    }

    /// Lifecycle snapshot (state, allowed transitions, available actions) for API responses.
    pub fn folder_lifecycle(&self, folder_id: &str) -> LifecycleView {
        LifecycleView::of(self.get_folder_state(folder_id))
    }

    /// Manually overrides a folder's reported state when no work is running and no error is
    /// recorded. Intended for tools and tests; the engine derives state from running work.
    pub fn set_folder_state(&self, folder_id: &str, state: FolderSyncState) {
        let before = self.get_folder_state(folder_id);
        if let Ok(mut states) = self.folder_states.write() {
            states.insert(folder_id.to_string(), state);
        }
        self.publish_folder_change(folder_id, before);
    }

    /// Starts a unit of folder work. Returns `None` for a scan when one is already running
    /// on this folder (duplicate scans are skipped instead of racing each other).
    pub fn begin_work(&self, folder_id: &str, work: FolderWork) -> Option<FolderWorkGuard> {
        let before = self.get_folder_state(folder_id);
        {
            let mut map = self.folder_activity.lock().unwrap_or_else(|e| e.into_inner());
            let a = map.entry(folder_id.to_string()).or_default();
            match work {
                FolderWork::Scan if a.scans > 0 => return None,
                FolderWork::Scan => a.scans += 1,
                FolderWork::Sync => a.syncs += 1,
            }
        }
        self.publish_folder_change(folder_id, before);
        Some(FolderWorkGuard { engine: self.clone(), folder_id: folder_id.to_string(), work })
    }

    fn end_work(&self, folder_id: &str, work: FolderWork) {
        let before = self.get_folder_state(folder_id);
        {
            let mut map = self.folder_activity.lock().unwrap_or_else(|e| e.into_inner());
            if let Some(a) = map.get_mut(folder_id) {
                match work {
                    FolderWork::Scan => a.scans = a.scans.saturating_sub(1),
                    FolderWork::Sync => a.syncs = a.syncs.saturating_sub(1),
                }
            }
        }
        self.publish_folder_change(folder_id, before);
    }

    /// Records the outcome of folder work: success clears a previous error, failure records why.
    pub fn record_folder_outcome(&self, folder_id: &str, outcome: Result<(), String>) {
        let mut map = self.folder_activity.lock().unwrap_or_else(|e| e.into_inner());
        let a = map.entry(folder_id.to_string()).or_default();
        a.last_error = match outcome {
            Ok(()) => None,
            Err(message) => Some(FolderError { message, since: chrono::Utc::now().timestamp() }),
        };
    }

    /// Clears a recorded error (e.g. after the user resumes or fixes the folder).
    pub fn clear_folder_error(&self, folder_id: &str) {
        let before = self.get_folder_state(folder_id);
        if let Some(a) = self.folder_activity.lock().unwrap_or_else(|e| e.into_inner()).get_mut(folder_id) {
            a.last_error = None;
        }
        self.publish_folder_change(folder_id, before);
    }

    /// Pauses or resumes a folder through its lifecycle: persists the flag, publishes
    /// `FOLDER_STATE_CHANGED`, and on resume clears a previously recorded error so the next
    /// scan starts clean.
    pub fn set_folder_paused(&self, folder_id: &str, paused: bool) -> rusqlite::Result<()> {
        let before = self.get_folder_state(folder_id);
        self.storage.set_folder_paused(folder_id, paused)?;
        if paused {
            self.stop_watching(folder_id);
        } else {
            self.start_watching(folder_id);
            if let Some(a) = self.folder_activity.lock().unwrap_or_else(|e| e.into_inner()).get_mut(folder_id) {
                a.last_error = None;
            }
        }
        self.publish_folder_change(folder_id, before);
        Ok(())
    }

    /// Re-derives the folder state and, if it changed, validates the transition against the
    /// folder state machine and publishes `FOLDER_STATE_CHANGED`.
    fn publish_folder_change(&self, folder_id: &str, before: FolderSyncState) {
        let after = self.get_folder_state(folder_id);
        if after == before {
            return;
        }
        if !before.permits(after) {
            warn!(
                "Folder {} moved {} -> {}, which the folder state machine does not list as a legal transition",
                folder_id, before, after
            );
        }
        let error = if after == FolderSyncState::Error {
            self.folder_error(folder_id).map(|e| e.message)
        } else {
            None
        };
        let _ = self.event_sender.send(CoreEvent::FolderStateChanged {
            folder_id: folder_id.to_string(),
            from: before.as_str().to_string(),
            to: after.as_str().to_string(),
            error,
        });
    }

    pub fn storage(&self) -> &StorageEngine {
        &self.storage
    }

    pub fn conflict_engine(&self) -> &ConflictEngine {
        &self.conflict_engine
    }

    pub fn event_sender(&self) -> &broadcast::Sender<CoreEvent> {
        &self.event_sender
    }

    pub fn device_id(&self) -> &str {
        &self.device_id
    }

    /// Builds the IgnoreEngine for a folder by loading `.synapseignore`, `.stignore`, and DB rules.
    pub fn get_folder_ignore_engine(&self, folder_id: &str, local_path: &Path) -> IgnoreEngine {
        let mut engine = IgnoreEngine::new();
        let syn_ignore = local_path.join(".synapseignore");
        if syn_ignore.exists() {
            if let Ok(c) = fs::read_to_string(&syn_ignore) {
                engine.parse_rules(&c);
            }
        }
        let st_ignore = local_path.join(".stignore");
        if st_ignore.exists() {
            if let Ok(c) = fs::read_to_string(&st_ignore) {
                engine.parse_rules(&c);
            }
        }
        if let Ok(pats) = self.storage.get_folder_ignores(folder_id) {
            for p in pats {
                engine.parse_rules(&p);
            }
        }
        engine
    }

    /// Indexes an entire folder recursively from scratch or incremental delta.
    ///
    /// Lifecycle: `idle|error → scanning → idle` on success, `→ error` (with reason) on failure.
    /// A scan requested while one is already running on the folder is skipped (returns `Ok(0)`).
    pub fn index_folder(&self, folder_id: &str) -> Result<usize, Box<dyn std::error::Error + Send + Sync>> {
        if let Some(folder) = self.storage.get_folder(folder_id)? {
            if folder.is_paused {
                info!("Folder {} is paused; skipping indexing", folder_id);
                return Ok(0);
            }
        }
        let Some(_work) = self.begin_work(folder_id, FolderWork::Scan) else {
            info!("Folder {} is already being scanned; skipping duplicate scan", folder_id);
            return Ok(0);
        };
        let result = self.index_folder_inner(folder_id);
        self.record_folder_outcome(
            folder_id,
            result.as_ref().map(|_| ()).map_err(|e| format!("Scan failed: {}", e)),
        );
        result
    }

    fn index_folder_inner(&self, folder_id: &str) -> Result<usize, Box<dyn std::error::Error + Send + Sync>> {
        let folder = match self.storage.get_folder(folder_id)? {
            Some(f) => f,
            None => return Err(format!("Folder not registered: {}", folder_id).into()),
        };

        if folder.is_paused {
            info!("Folder {} is paused; skipping indexing", folder_id);
            return Ok(0);
        }

        let base_path = PathBuf::from(&folder.local_path);
        if !base_path.exists() {
            fs::create_dir_all(&base_path)?;
        }

        // Mount Safety Marker verification: prevent cascading deletes if volume unmounted
        if let Err(_e) = verify_folder_marker(&base_path, &folder.marker_name) {
            let _ = ensure_folder_marker(&base_path, &folder.marker_name);
            if let Err(e2) = verify_folder_marker(&base_path, &folder.marker_name) {
                warn!("Mount safety check failed for folder {}: {}", folder_id, e2);
                return Err(Box::new(e2));
            }
        }

        let ignore_engine = self.get_folder_ignore_engine(folder_id, &base_path);
        let mut indexed_count = 0;
        let mut stack = vec![base_path.clone()];

        while let Some(current_dir) = stack.pop() {
            for entry in fs::read_dir(&current_dir)? {
                let entry = entry?;
                let path = entry.path();
                let file_name = entry.file_name();
                let name_str = file_name.to_string_lossy();

                // Skip internal metadata folders and hidden control files
                if name_str.starts_with(".synapse")
                    || name_str.starts_with(".git")
                    || name_str.ends_with(".tmp_synapse")
                    || name_str.starts_with(".stfolder")
                    || name_str == folder.marker_name
                {
                    continue;
                }

                let rel_path = sanitize_relative_path(&path.strip_prefix(&base_path)?.to_string_lossy());
                if ignore_engine.is_ignored(&rel_path, path.is_dir()) {
                    continue;
                }

                if path.is_dir() {
                    stack.push(path);
                } else if path.is_file() {
                    let _ = self.event_sender.send(CoreEvent::FolderScanProgress {
                        folder_id: folder_id.to_string(),
                        current_file: rel_path.clone(),
                        scanned_files: indexed_count + 1,
                    });

                    if self.index_single_file(folder_id, &rel_path)? {
                        indexed_count += 1;
                    }
                }
            }
        }

        Ok(indexed_count)
    }

    /// Indexes a single local file: chunks with FastCDC, computes BLAKE3, writes to CAS,
    /// updates Merkle DAG revision and SQLite file record.
    pub fn index_single_file(
        &self,
        folder_id: &str,
        relative_path: &str,
    ) -> Result<bool, Box<dyn std::error::Error + Send + Sync>> {
        let folder = match self.storage.get_folder(folder_id)? {
            Some(f) => f,
            None => return Err(format!("Folder not registered: {}", folder_id).into()),
        };

        // If folder is ReceiveOnly, do NOT index or propagate local changes to peers
        if !folder.parsed_sync_mode().can_send() {
            info!("Folder {} is ReceiveOnly; ignoring local changes in {}", folder_id, relative_path);
            return Ok(false);
        }

        let rel_clean = sanitize_relative_path(relative_path);
        let full_path = PathBuf::from(&folder.local_path).join(&rel_clean);
        if !full_path.exists() {
            return Ok(false);
        }

        let meta = fs::metadata(&full_path)?;
        let file_size = meta.len();
        let mtime_nanos = meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_nanos() as i64)
            .unwrap_or(0);

        #[cfg(unix)]
        let file_permissions = {
            use std::os::unix::fs::PermissionsExt;
            meta.permissions().mode() & 0o7777
        };
        #[cfg(not(unix))]
        let file_permissions = if meta.permissions().readonly() { 0o444 } else { 0o644 };

        // Check if file is already up to date in database (including permissions/executable bit)
        let existing = self.storage.get_file(folder_id, &rel_clean)?;
        if let Some(record) = &existing {
            if record.size == file_size
                && record.mtime_nanos == mtime_nanos
                && record.permissions == file_permissions
                && !record.is_deleted
            {
                return Ok(false); // No changes detected
            }
        }

        let data = fs::read(&full_path)?;
        let (chunks, manifest_hash) = self.chunker.chunk_slice(&data);

        // Content unchanged (only timestamps differ, e.g. after receiving the file from a peer
        // on a filesystem with coarser mtime precision): refresh metadata, keep the revision.
        // Creating a new revision here would bounce the file between devices forever.
        if let Some(record) = &existing {
            if !record.is_deleted && record.permissions == file_permissions {
                if let Some(current) = self.storage.get_revision(&record.current_revision)? {
                    if current.manifest_hash == manifest_hash {
                        self.storage.upsert_file(&FileRecord {
                            size: file_size,
                            mtime_nanos,
                            ..record.clone()
                        })?;
                        return Ok(false);
                    }
                }
            }
        }

        // Store new chunks in CAS
        for (info, chunk_data) in &chunks {
            self.storage.put_chunk(&info.chunk_hash, chunk_data)?;
        }

        let chunk_hashes: Vec<String> = chunks.into_iter().map(|(c, _)| c.chunk_hash).collect();

        let parent_hashes = existing
            .as_ref()
            .map(|e| vec![e.current_revision.clone()])
            .unwrap_or_default();

        let seq = chrono::Utc::now().timestamp_millis() as u64;
        let rev_node = RevisionNode::new(
            parent_hashes.clone(),
            manifest_hash.clone(),
            self.device_id.clone(),
            seq,
            mtime_nanos,
            file_size,
        );

        let rev_record = RevisionRecord {
            revision_hash: rev_node.revision_hash.clone(),
            parent_1: parent_hashes.get(0).cloned(),
            parent_2: parent_hashes.get(1).cloned(),
            manifest_hash,
            author_device: self.device_id.clone(),
            created_at: mtime_nanos,
        };

        self.storage.insert_revision(&rev_record, &chunk_hashes)?;

        self.storage.upsert_file(&FileRecord {
            folder_id: folder_id.to_string(),
            relative_path: rel_clean.clone(),
            size: file_size,
            mtime_nanos,
            current_revision: rev_node.revision_hash.clone(),
            is_deleted: false,
            permissions: file_permissions,
        })?;

        let _ = self.event_sender.send(CoreEvent::FileChanged {
            folder_id: folder_id.to_string(),
            relative_path: rel_clean,
            revision: rev_node.revision_hash,
        });

        Ok(true)
    }

    /// Handles an incoming remote file revision during sync.
    /// Traverses the Merkle DAG to evaluate ancestry and invokes the 3-Way Conflict Pipeline if divergent.
    ///
    /// Lifecycle: the folder is `syncing` while this runs; failure records an `error` reason.
    pub fn reconcile_remote_revision(
        &self,
        folder_id: &str,
        relative_path: &str,
        remote_rev: &RevisionNode,
        remote_chunk_hashes: &[String],
        remote_payload: &[u8],
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let _work = self.begin_work(folder_id, FolderWork::Sync);
        let result = self.reconcile_remote_revision_inner(
            folder_id,
            relative_path,
            remote_rev,
            remote_chunk_hashes,
            remote_payload,
        );
        if let Err(e) = &result {
            self.record_folder_outcome(folder_id, Err(format!("Applying remote change to '{}' failed: {}", relative_path, e)));
        }
        result
    }

    fn reconcile_remote_revision_inner(
        &self,
        folder_id: &str,
        relative_path: &str,
        remote_rev: &RevisionNode,
        remote_chunk_hashes: &[String],
        remote_payload: &[u8],
    ) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let folder = match self.storage.get_folder(folder_id)? {
            Some(f) => f,
            None => return Err(format!("Folder not registered: {}", folder_id).into()),
        };

        // If folder is SendOnly, reject incoming modifications from remote peers
        if !folder.parsed_sync_mode().can_receive() {
            warn!("Folder {} is SendOnly; rejecting incoming remote changes for {}", folder_id, relative_path);
            return Ok(());
        }

        let rel_clean = sanitize_relative_path(relative_path);
        let base_path = PathBuf::from(&folder.local_path);
        let local_path = base_path.join(&rel_clean);

        let _ = self.event_sender.send(CoreEvent::ItemStarted {
            folder_id: folder_id.to_string(),
            relative_path: rel_clean.clone(),
            action: "SYNC_INCOMING".to_string(),
        });

        // Ensure all remote chunks and revision are inserted in storage
        self.storage.insert_revision(
            &RevisionRecord {
                revision_hash: remote_rev.revision_hash.clone(),
                parent_1: remote_rev.parent_hashes.get(0).cloned(),
                parent_2: remote_rev.parent_hashes.get(1).cloned(),
                manifest_hash: remote_rev.manifest_hash.clone(),
                author_device: remote_rev.author_device_id.clone(),
                created_at: remote_rev.mtime_nanos,
            },
            remote_chunk_hashes,
        )?;

        let local_file = self.storage.get_file(folder_id, &rel_clean)?;

        match local_file {
            None => {
                // Completely new file remotely: fast-forward accept remote with atomic write
                atomic_write_file(&local_path, remote_payload, Some(remote_rev.mtime_nanos), Some(0o644))?;

                // Save chunks in CAS
                let (chunks, _) = self.chunker.chunk_slice(remote_payload);
                for (info, data) in &chunks {
                    self.storage.put_chunk(&info.chunk_hash, data)?;
                }

                self.storage.upsert_file(&FileRecord {
                    folder_id: folder_id.to_string(),
                    relative_path: rel_clean.clone(),
                    size: remote_rev.file_size,
                    mtime_nanos: remote_rev.mtime_nanos,
                    current_revision: remote_rev.revision_hash.clone(),
                    is_deleted: false,
                    permissions: 0o644,
                })?;

                let _ = self.event_sender.send(CoreEvent::ItemFinished {
                    folder_id: folder_id.to_string(),
                    relative_path: rel_clean.clone(),
                    action: "CREATED".to_string(),
                    error: None,
                });
            }
            Some(existing) => {
                let dag = MerkleDag::new(&self.storage);
                let lca = dag.find_lca(&existing.current_revision, &remote_rev.revision_hash);

                match lca {
                    LcaResult::Identical => {
                        // Already identical; do nothing
                    }
                    LcaResult::FastForwardLocal => {
                        // Local has already superseded remote; keep local
                        info!(
                            "Local revision {} supersedes remote {}; keeping local",
                            existing.current_revision, remote_rev.revision_hash
                        );
                    }
                    LcaResult::FastForwardRemote => {
                        // Remote is clean descendant of local; fast-forward remote
                        info!(
                            "Fast-forwarding remote revision {} over local {}",
                            remote_rev.revision_hash, existing.current_revision
                        );

                        // File versioning archive if configured (supports TrashCan, Simple, Staggered, and External)
                        let policy = versioning::VersioningPolicy::from_folder_config_with_command(
                            &folder.versioning_type,
                            folder.versioning_keep_days,
                            folder.versioning_command.as_deref(),
                        );
                        if local_path.exists() && policy != versioning::VersioningPolicy::None {
                            let _ = versioning::archive_file(&base_path, &rel_clean, &policy);
                        }

                        // Atomic staging write with timestamp & permission preservation
                        let target_perms = existing.permissions;
                        atomic_write_file(&local_path, remote_payload, Some(remote_rev.mtime_nanos), Some(target_perms))?;

                        let (chunks, _) = self.chunker.chunk_slice(remote_payload);
                        for (info, data) in &chunks {
                            self.storage.put_chunk(&info.chunk_hash, data)?;
                        }

                        self.storage.upsert_file(&FileRecord {
                            folder_id: folder_id.to_string(),
                            relative_path: rel_clean.clone(),
                            size: remote_rev.file_size,
                            mtime_nanos: remote_rev.mtime_nanos,
                            current_revision: remote_rev.revision_hash.clone(),
                            is_deleted: false,
                            permissions: target_perms,
                        })?;

                        let _ = self.event_sender.send(CoreEvent::ItemFinished {
                            folder_id: folder_id.to_string(),
                            relative_path: rel_clean.clone(),
                            action: "UPDATED".to_string(),
                            error: None,
                        });
                    }
                    LcaResult::Diverged { base_revision } => {
                        // Branches diverged: trigger Multi-Strategy Conflict Resolution Pipeline
                        self.conflict_engine.handle_divergence(
                            folder_id,
                            &rel_clean,
                            base_revision.as_deref(),
                            &existing.current_revision,
                            &remote_rev.revision_hash,
                            remote_payload,
                            &remote_rev.author_device_id,
                            remote_rev.mtime_nanos,
                        )?;
                    }
                }
            }
        }

        Ok(())
    }

    /// Starts watching a folder for local changes (no-op if already watching or paused).
    /// Returns `true` if a watcher was started.
    pub fn start_watching(&self, folder_id: &str) -> bool {
        let Ok(Some(folder)) = self.storage.get_folder(folder_id) else { return false };
        if folder.is_paused {
            return false;
        }
        let mut map = self.watchers.lock().unwrap_or_else(|e| e.into_inner());
        if map.contains_key(folder_id) {
            return false;
        }
        match self.try_spawn_watcher(folder_id.to_string(), folder.local_path) {
            Ok(w) => {
                map.insert(folder_id.to_string(), w);
                true
            }
            Err(e) => {
                warn!("Could not watch folder {}: {}", folder_id, e);
                false
            }
        }
    }

    /// Stops watching a folder (on pause or delete).
    pub fn stop_watching(&self, folder_id: &str) {
        self.watchers.lock().unwrap_or_else(|e| e.into_inner()).remove(folder_id);
    }

    /// Whether a folder currently has a live watcher.
    pub fn is_watching(&self, folder_id: &str) -> bool {
        self.watchers.lock().unwrap_or_else(|e| e.into_inner()).contains_key(folder_id)
    }

    /// Stops every watcher (engine shutdown).
    pub fn stop_all_watchers(&self) {
        self.watchers.lock().unwrap_or_else(|e| e.into_inner()).clear();
    }

    /// Spawns a background filesystem watcher for a monitored folder.
    pub fn spawn_watcher(&self, folder_id: String, local_path: String) -> RecommendedWatcher {
        self.try_spawn_watcher(folder_id, local_path)
            .expect("Failed to initialize notify watcher")
    }

    fn try_spawn_watcher(&self, folder_id: String, local_path: String) -> notify::Result<RecommendedWatcher> {
        let engine = self.clone();
        let fid = folder_id.clone();
        let root_path = PathBuf::from(&local_path);

        let mut watcher = notify::recommended_watcher(move |res: Result<Event, notify::Error>| {
            if let Ok(event) = res {
                match event.kind {
                    EventKind::Create(_) | EventKind::Modify(_) => {
                        for path in event.paths {
                            if path.is_file() {
                                let name = path.file_name().unwrap_or_default().to_string_lossy();
                                if name.starts_with(".synapse") || name.ends_with(".tmp_synapse") {
                                    continue;
                                }
                                if let Ok(rel) = path.strip_prefix(&root_path) {
                                    let rel_str = rel.to_string_lossy().to_string();
                                    let _ = engine.index_single_file(&fid, &rel_str);
                                }
                            }
                        }
                    }
                    _ => {}
                }
            }
        })?;

        watcher.watch(Path::new(&local_path), RecursiveMode::Recursive)?;
        Ok(watcher)
    }
}
