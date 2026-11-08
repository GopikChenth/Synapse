# Synapse Core Control Plane: API & WebSocket Specification

**Version:** 1.0.0  
**Engine:** Synapse Core (Native Rust Daemon)  
**Default Bind Address:** `http://127.0.0.1:9090` (Configurable via `-a` / `-p`)  
**Authentication:** Optional Bearer Token (`--token <token>` or `env: SYNAPSE_TOKEN`)

---

## 1. Overview & Architecture

The Synapse Core local control plane exposes an embedded HTTP REST and WebSocket server powered by Axum. It provides high-performance Inter-Process Communication (IPC) for Desktop (Compose Multiplatform) and Android clients (`libsynapse.so`).

### Two API Planes:
1. **Native Synapse API (`/api/v1/*`):** Rich, typed API designed for modern peer-to-peer file synchronization, FastCDC content-defined chunking, Merkle Revision DAG causality, and 3-Way interactive conflict resolution.
2. **Syncthing Compatibility Layer (`/rest/*`):** Drop-in route aliases and query mappings matching the Syncthing REST API standard. Allows existing Syncthing-compatible GUI frontends, CLI tools, and scripts to control Synapse Core seamlessly.

---

## 2. Global Headers & Authentication

When the daemon is started with a security token, all incoming HTTP requests must include the Bearer token authorization header:

```http
Authorization: Bearer <token>
Content-Type: application/json
```

All endpoints support Cross-Origin Resource Sharing (`CORS`) with wildcard origins to facilitate web management dashboards.

---

## 3. System & Connectivity Endpoints

### 3.1 Get System Status
Returns daemon uptime, memory footprint, active peer count, and synchronization status.

* **Endpoints:**
  * Native: `GET /api/v1/system/status`
  * Syncthing Alias: `GET /rest/system/status`
* **Response:** `200 OK`
```json
{
  "device_id": "b724d6276df096facf829470ebfffcb945d56a086c700dfb3a16d192f6fb2263",
  "uptime_seconds": 3840,
  "peer_count": 3,
  "sync_state": "IDLE",
  "version": "1.0.0",
  "monitored_folders_count": 4,
  "pending_conflicts_count": 1,
  "memory_footprint_bytes": 16777216
}
```

### 3.2 Get Engine Version
* **Endpoints:**
  * Native: `GET /api/v1/system/version`
  * Syncthing Alias: `GET /rest/system/version`
* **Response:** `200 OK`
```json
{
  "version": "v1.0.0",
  "codename": "Synapse-SynapticCore",
  "os": "linux",
  "arch": "x86_64"
}
```

### 3.3 Health Ping
* **Endpoints:**
  * Native: `GET /api/v1/system/ping`
  * Syncthing Alias: `GET /rest/system/ping`
* **Response:** `200 OK`
```json
{
  "ping": "pong"
}
```

### 3.4 Restart / Shutdown Daemon
* **Restart:** `POST /api/v1/system/restart` | `POST /rest/system/restart`
* **Shutdown:** `POST /api/v1/system/shutdown` | `POST /rest/system/shutdown`
* **Response:** `200 OK`

### 3.5 Peer Connection Metrics
Retrieves real-time throughput, latency, and connection states for all cluster peers.

* **Endpoints:**
  * Native: `GET /api/v1/connections`
  * Syncthing Alias: `GET /rest/system/connections`
* **Response:** `200 OK`
```json
{
  "dev_laptop_a1b2c3d4": {
    "device_id": "dev_laptop_a1b2c3d4",
    "address": "192.168.1.150:9190",
    "is_connected": true,
    "in_bps": 2457600,
    "out_bps": 1228800,
    "total_in_bytes": 104857600,
    "total_out_bytes": 52428800,
    "latency_ms": 4,
    "client_type": "synapse-core"
  }
}
```

---

## 4. Folders & Synchronization Management

### 4.1 List Configured Folders
* **Endpoints:**
  * Native: `GET /api/v1/folders`
  * Syncthing Alias: `GET /rest/system/folders`
* **Response:** `200 OK`
```json
[
  {
    "folder_id": "documents",
    "local_path": "/home/user/Documents",
    "conflict_policy": "THREE_WAY_AUTO",
    "is_paused": false,
    "sync_mode": "SendReceive",
    "pull_order": "Alphabetical",
    "versioning_type": "Simple",
    "versioning_keep_days": 30,
    "marker_name": ".synapse-folder",
    "files_count": 284
  }
]
```

### 4.2 Register / Add New Folder
Registers a directory for continuous synchronization and starts the inotify/cross-platform filesystem watcher.

* **Endpoints:**
  * Native: `POST /api/v1/folders`
  * Syncthing Alias: `POST /rest/system/folders`
* **Request Body:**
```json
{
  "folder_id": "work_vault",
  "local_path": "/home/user/Work",
  "conflict_policy": "THREE_WAY_AUTO",
  "sync_mode": "SendReceive",
  "pull_order": "Alphabetical",
  "versioning_type": "Simple",
  "versioning_keep_days": 30,
  "marker_name": ".synapse-folder"
}
```
* **Supported Conflict Policies:**
  * `THREE_WAY_AUTO`: Attempt automatic line-level diff3 merge for text files; quarantine on overlapping collision.
  * `LWW`: Last-Write-Wins based on file modification timestamp (`mtime`).
  * `MANUAL`: Immediately quarantine every concurrent divergence without auto-merging.
* **Response:** `201 Created`

### 4.3 Delete Folder
* **Endpoint:** `DELETE /api/v1/folders/:id`
* **Response:** `204 No Content`

### 4.4 Pause / Resume Folder
* **Pause:** `POST /api/v1/folders/:id/pause` $\to$ `200 OK`
* **Resume:** `POST /api/v1/folders/:id/resume` $\to$ `200 OK`

### 4.5 Rescan Folder (Trigger Index Scan)
* **Endpoints:**
  * Native: `POST /api/v1/folders/:id/rescan`
  * Syncthing Alias: `POST /rest/db/scan?folder=:id`
* **Response:** `200 OK`

### 4.6 Folder Status & Completion
* **Status:**
  * Native: `GET /api/v1/folders/:id/status`
  * Syncthing Alias: `GET /rest/db/status?folder=:id`
* **Completion:**
  * Native: `GET /api/v1/folders/:id/completion`
  * Syncthing Alias: `GET /rest/db/completion?folder=:id&device=:device_id`

```json
{
  "completion": 100.0,
  "global_bytes": 48239014,
  "need_bytes": 0,
  "global_files": 284,
  "need_files": 0,
  "state": "idle"
}
```

### 4.7 Folder Device Sharing Matrix
* **List Devices with Access:** `GET /api/v1/folders/:id/devices`
* **Share with Device:** `POST /api/v1/folders/:id/devices/:device_id`
* **Unshare from Device:** `DELETE /api/v1/folders/:id/devices/:device_id`

### 4.8 Folder Ignore Rules
* **Fetch Patterns:**
  * Native: `GET /api/v1/folders/:id/ignores`
  * Syncthing Alias: `GET /rest/db/ignores?folder=:id`
* **Save Patterns:**
  * Native: `POST /api/v1/folders/:id/ignores`
  * Syncthing Alias: `POST /rest/db/ignores?folder=:id`
* **Payload:**
```json
{
  "ignore": [
    ".git",
    "*.tmp",
    "target/",
    "build/"
  ]
}
```

---

## 5. Device & Cluster Management

### 5.1 List Configured Devices
* **Endpoints:**
  * Native: `GET /api/v1/devices`
  * Syncthing Alias: `GET /rest/system/devices`
* **Response:** `200 OK`
```json
[
  {
    "device_id": "b724d6276df096facf829470ebfffcb945d56a086c700dfb3a16d192f6fb2263",
    "name": "Desktop Workstation",
    "addresses": ["dynamic", "tcp://192.168.1.100:9190"],
    "is_paused": false,
    "is_trusted": true,
    "introducer": false,
    "created_at": 1730560000000000000
  }
]
```

### 5.2 Add / Register Device
* **Endpoints:**
  * Native: `POST /api/v1/devices`
  * Syncthing Alias: `POST /rest/system/devices`
* **Request Body:**
```json
{
  "device_id": "c983ef112...",
  "name": "Pixel 8 Mobile",
  "addresses": ["dynamic"]
}
```

### 5.3 Device Operations
* **Fetch Details:** `GET /api/v1/devices/:id`
* **Remove Device:** `DELETE /api/v1/devices/:id`
* **Pause Communication:** `POST /api/v1/devices/:id/pause`
* **Resume Communication:** `POST /api/v1/devices/:id/resume`

---

## 6. Mutual Pairing Handshake (Pending Queues)

Synapse Core implements a zero-trust mutual discovery handshake. Unrecognized nodes and folder shares are staged until user approval.

### 6.1 Pending Devices
* **List Pending:** `GET /api/v1/cluster/pending/devices` | `GET /rest/cluster/pending/devices`
* **Accept Device:** `POST /api/v1/cluster/pending/devices/:id/accept` | `POST /rest/cluster/pending/devices/:id/accept`
* **Reject Device:** `POST /api/v1/cluster/pending/devices/:id/reject` | `POST /rest/cluster/pending/devices/:id/reject`

```json
[
  {
    "device_id": "e812fbc90...",
    "name": "Living Room Tablet",
    "address": "192.168.1.42:9190",
    "received_at": 1730560120000000000
  }
]
```

### 6.2 Pending Folders
* **List Pending:** `GET /api/v1/cluster/pending/folders` | `GET /rest/cluster/pending/folders`
* **Accept Folder:** `POST /api/v1/cluster/pending/folders/:id/accept` | `POST /rest/cluster/pending/folders/:id/accept`
* **Reject Folder:** `POST /api/v1/cluster/pending/folders/:id/reject` | `POST /rest/cluster/pending/folders/:id/reject`

---

## 7. 3-Way Semantic Conflict & Virtual Quarantine Engine

Unlike traditional engines that pollute folders with `.sync-conflict-*` files, Synapse Core routes divergent edits into an internal virtual quarantine and serves structured diff hunks.

### 7.1 List Active Quarantined Conflicts
* **Endpoint:** `GET /api/v1/conflicts`
* **Response:** `200 OK`
```json
[
  {
    "conflict_id": "cnf_8f93e1a0",
    "folder_id": "work_vault",
    "relative_path": "projects/spec.md",
    "base_revision": "rev_root_001",
    "local_revision": "rev_local_002",
    "remote_revision": "rev_remote_003",
    "staged_payload_path": "/home/user/.synapse/quarantine/cnf_8f93e1a0.md",
    "status": "PENDING",
    "detected_at": 1730560120000000000
  }
]
```

### 7.2 Fetch Conflict Raw Versions
* `GET /api/v1/conflicts/:id/base` $\to$ Returns content of Lowest Common Ancestor (LCA).
* `GET /api/v1/conflicts/:id/local` $\to$ Returns current local file content on disk.
* `GET /api/v1/conflicts/:id/remote` $\to$ Returns quarantined remote payload.

### 7.3 Fetch Structured 3-Way Diff Hunks
Returns pre-computed diff hunks ready for immediate side-by-side visual rendering in UI merge dialogs.

* **Endpoint:** `GET /api/v1/conflicts/:id/diff`
* **Response:** `200 OK`
```json
{
  "conflict_id": "cnf_8f93e1a0",
  "folder_id": "work_vault",
  "relative_path": "projects/spec.md",
  "base": "# Spec\nTimeout: 30\n",
  "local": "# Spec\nTimeout: 60\n",
  "remote": "# Spec\nTimeout: 120\n",
  "hunks": [
    {
      "hunk_type": "UNCHANGED",
      "base_lines": ["# Spec"],
      "local_lines": ["# Spec"],
      "remote_lines": ["# Spec"]
    },
    {
      "hunk_type": "CONFLICT",
      "base_lines": ["Timeout: 30"],
      "local_lines": ["Timeout: 60"],
      "remote_lines": ["Timeout: 120"]
    }
  ]
}
```
* **Hunk Types:** `UNCHANGED`, `LOCAL_CHANGE`, `REMOTE_CHANGE`, `IDENTICAL_CHANGE`, `CONFLICT`.

### 7.4 Submit Conflict Resolution
* **Endpoint:** `POST /api/v1/conflicts/:id/resolve`
* **Request Options:**

#### Option 1: Keep Local
```json
{
  "action": "ACCEPT_LOCAL"
}
```

#### Option 2: Accept Remote (Overwrite Disk Atomically)
```json
{
  "action": "ACCEPT_REMOTE"
}
```

#### Option 3: Custom Merge (UI Combined Draft)
Writes user-edited merged content to disk and creates a 2-parent Merkle DAG merge revision.
```json
{
  "action": "CUSTOM_MERGE",
  "merged_content": "# Spec\nTimeout: 90\n"
}
```

#### Option 4: Keep Both
Preserves the local working file and saves the remote version alongside non-destructively (`spec.conflict-cnf_8f93e1a0.md`).
```json
{
  "action": "KEEP_BOTH"
}
```

---

## 8. Real-Time WebSocket Event Stream

Clients connect to stream live notifications and daemon state events.

* **Endpoints:**
  * Native: `ws://127.0.0.1:9090/api/v1/events`
  * Syncthing Alias: `ws://127.0.0.1:9090/rest/events`

### 8.1 Event Types & Payloads

#### `CONFLICT_DETECTED`
Emitted immediately when an overlapping edit or binary collision enters quarantine:
```json
{
  "event": "CONFLICT_DETECTED",
  "data": {
    "conflict_id": "cnf_8f93e1a0",
    "folder_id": "work_vault",
    "relative_path": "projects/spec.md",
    "mime_type": "text/markdown",
    "local_version": {
      "author": "Desktop-Workstation",
      "mtime": 1730560000,
      "size": 4210
    },
    "remote_version": {
      "author": "Pixel-8-Mobile",
      "mtime": 1730560120,
      "size": 4350
    },
    "auto_merge_attempted": true,
    "overlapping_lines_count": 1
  }
}
```

#### `FILE_CHANGED`
Emitted when local or remote files are updated and indexed:
```json
{
  "event": "FILE_CHANGED",
  "data": {
    "folder_id": "work_vault",
    "relative_path": "notes.md",
    "revision": "rev_7f39a0..."
  }
}
```

#### `CONFLICT_RESOLVED`
Emitted upon resolution decision:
```json
{
  "event": "CONFLICT_RESOLVED",
  "data": {
    "conflict_id": "cnf_8f93e1a0",
    "action": "CUSTOM_MERGE"
  }
}
```

#### Lifecycle & Peer Events
* `SYNC_STARTED`: `{ "folder_id": "work_vault" }`
* `SYNC_COMPLETED`: `{ "folder_id": "work_vault", "files_synced": 4 }`
* `PEER_CONNECTED`: `{ "peer_id": "c983ef112...", "addr": "192.168.1.150:9190" }`
* `PEER_DISCONNECTED`: `{ "peer_id": "c983ef112..." }`

### 8.2 Heartbeat Ping / Pong
Clients can send a text WebSocket frame with `"ping"`; the server will immediately reply with `"pong"`.

---

## 9. Error Format & Status Codes

All non-2xx responses return a standardized JSON error message or descriptive text:

```json
{
  "error": "Conflict not found",
  "status": 404
}
```

| HTTP Status | Description |
| :--- | :--- |
| `200 OK` | Request completed successfully |
| `201 Created` | Resource registered or created |
| `204 No Content` | Resource deleted successfully |
| `400 Bad Request` | Invalid JSON syntax or missing query parameters |
| `401 Unauthorized` | Missing or invalid Bearer security token |
| `404 Not Found` | Requested folder, device, or conflict not found |
| `500 Internal Error` | Database failure, I/O lock, or engine error |
