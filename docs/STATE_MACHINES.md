# Synapse: state machines (inventory, decision, changes)

**Date:** 22 November 2026
**Scope:** `synapse-core` (Rust engine, changed and tested) and the Kotlin app (inventoried from the compiled desktop build, v1.0.6; the source wasn't readable in this session).

---

## 1. The decision

**The engine is the single source of truth for every lifecycle, and every lifecycle follows one pattern.**

1. **One `StateMachine` trait** (`src/state/mod.rs`). A machine implements a handful of items: states, a transition table, actions, and descriptions. In return it gets, identically:
   - guarded `transition()` with a structured `StateTransitionError`;
   - introspection metadata;
   - its registry entry (`GET /api/v1/system/statemachines`).

   No more hand-built errors or hand-written registry JSON.
2. **Every entity in the API carries its lifecycle**, as *additive* fields: `state` / `pairing_state` / `connection_state` / `engine_state`, plus a `lifecycle` object with `allowed_transitions` and `available_actions`. UIs render buttons from `available_actions` instead of re-deriving state from booleans; agents read the same fields.
3. **Every illegal move returns the same structured error:** `{"error":"invalid_transition", "message", "current_state", "attempted_transition", "allowed_transitions", "available_actions"}`. A UI can say "you can't do that, here's what you can do", and an agent can self-correct.
4. **Every state change is published as an event** (`FOLDER_STATE_CHANGED`, `DEVICE_STATE_CHANGED`, `PEER_DISCONNECTED`). These are available over the WebSocket and the REST-style `GET /rest/events`, so clients can react instead of polling (the app currently makes about 8 requests a second while idle).
5. **No breaking changes.** The app decodes with `ignoreUnknownKeys = true`, so new fields are safe. No existing field, value, route or status code changed, except where the old value was a bug (§4).

**Why this elevates all three:**

| | |
|---|---|
| **UX** | No stuck "Scanning", no fake "Connected", no endless pairing prompts, real error reasons, a working shutdown. |
| **DX** | One trait, one guard, one error type, one registry; adding a lifecycle is about 40 lines and the tests check its invariants automatically. Drift between docs and code is caught by tests. |
| **AX** | Machine-readable machines (`/system/statemachines`), per-entity `available_actions`, structured transition errors, and events. |

---

## 2. Inventory: engine (`synapse-core`)

| # | Lifecycle | Before | After | Machine id |
|---|---|---|---|---|
| 1 | **Conflict** | Explicit enum, but each guard hand-built its error (3 copies) | Same enum, on the shared trait, `transition()` guard | `conflict_lifecycle` |
| 2 | **Folder sync** | Enum existed, but state was set by hand. **Stuck "scanning"/"syncing"** on any error, no error reason, overlapping scans allowed, pause/resume emitted nothing | State **derived from running work** (RAII guards, panic-safe). Error **reason** recorded and cleared on success or resume. Duplicate scans skipped. `FOLDER_STATE_CHANGED` events. Illegal derived transitions are logged | `folder_sync_lifecycle` |
| 3 | **Device pairing** | Enum defined and registered but **never used**. Real state lived in a pending table plus `is_paused`/`is_trusted` booleans. **Rejection deleted the request**, so the device re-appeared forever. Accept was non-transactional, and accepting a non-pending device gave a SQL error (500). A *paused* device saying Hello became a pairing request again | Enum is the source of truth (`device_pairing_state()`). **Rejections persisted** (`rejected_devices` table) and **undoable**. Accept, reject and un-reject are **transactional**. **Action-level guards** (e.g. accept only from `pending`). Unknown device → 404. `DEVICE_STATE_CHANGED` events | `device_pairing_lifecycle` |
| 4 | **Peer presence** *(new)* | Implicit: `is_connected = !is_paused`, so **never-seen devices showed as "Connected"**. Peers never expired. `peer_count` counted stale peers | `unknown / online / offline / paused`, derived from last-heard time with a 30 s presence window. A sweep emits **one** `PEER_DISCONNECTED` per peer that goes quiet. `peer_count` counts only live peers | `peer_connection_lifecycle` |
| 5 | **Engine** *(new)* | `RUNNING: AtomicBool`. **Shutdown endpoint was a no-op.** JNI `synapse_stop` only flipped a flag, so the engine kept running. Watcher threads `park()`ed forever. No SIGTERM handling | `stopped → starting → running → stopping → stopped` (or `failed`), with compare-and-swap transitions. **Graceful shutdown** from the API, Ctrl-C, SIGTERM and JNI. Strict single start. Watchers exit on shutdown. In-process restart works | `engine_lifecycle` |
| 6 | Relay session | Explicit enum; registry entry hand-written | Unchanged, plus a **drift test** that the registry matches the enforced transitions | `relay_session_lifecycle` |
| 7 | Swarm chunk | Explicit enum; registry entry hand-written | Unchanged (dormant until sync is wired) | `swarm_chunk_lifecycle` |
| 8 | Long-poll events | Did not exist | Event ring buffer: `since` / `limit` / `timeout` / type filter (done earlier today) | n/a |

### Found but left as-is (documented, not changed)
- **Pending folder invitations** have the same "reject deletes, so it re-appears" pattern. Nothing creates invitations at runtime yet (only tests), so it's dormant. When folder sharing is wired up, give it the device-pairing treatment.
- **`ConflictPipelineTier`** is defined but unused.
- **`POST /system/restart`** is still a no-op that returns 200. A true in-process restart needs a supervisor. It was left unchanged on purpose so the app's behaviour doesn't change.
- **Config enums** (`SyncMode`, `ConflictPolicy`, versioning type) are stored as free strings, and unknown values silently fall back. That's configuration, not lifecycle. Validation was left out because the app's exact values couldn't be checked without its source.
- **`latency_ms`** in `/connections` is hardcoded to `5`.

---

## 3. Inventory: app (Kotlin; from the compiled desktop build)

Explicit already (good): `AsyncResource`, `EngineConnectionState`, `FormLoadState`, `FormSaveState`, `DeviceOperation`, `FolderOperation`, `AppModalState`, `ServiceLifecycleState`.

Still implicit (recommended changes, which need source access):

| Where | Implicit state today | Problem | Recommendation |
|---|---|---|---|
| `QuarantineUiState` | `isLoading`, `isDiffLoading`, `error`, `successMessage`, `selectedConflictId` | 2⁵ combinations, most invalid ("loading" and "error" at once) | `sealed interface QuarantineState { Loading; Loaded(list, diff: DiffState); Failed(msg) }` with `DiffState { Closed, Loading(id), Shown(id, diff), Failed(id, msg) }`. Enable actions from each conflict's `available_actions` |
| `SettingsUiState` | Legacy `isLoading` + `error` **next to** `loadState`/`saveState` | Two sources of truth, which is how "Daemon is not running" got shown | Delete `isLoading`/`error`; derive from `loadState`/`saveState` |
| `StatusUiState` | `isRunning: Boolean` next to `engineState` | Duplicate | Delete `isRunning`; map from `engineState`, and on Synapse Core from `engine_state` |
| `DeviceUiModel` | `paused`, `connected`, `untrusted` booleans | `connected` was the engine's lie; UI derives words itself | Add `pairingState` / `connectionState` from `/devices` and `/connections`; show `connection_state` words; enable buttons from `lifecycle.available_actions` |
| Folder rows (dashboard / Folders) | "Up to Date" vs "Active", computed in the UI | Two vocabularies, ignores errors | Show `folder.state` and `folder.error.message` from `/api/v1/folders`; actions from `lifecycle.available_actions` |
| Dashboard `ThisDeviceSection` | `syncConflictsCount` accumulated from fake events | Grows every poll (2 → 38) | Use `pending_conflicts_count` from status; see `ui-review/UI_BLOCKER_FIXES.md` #3 |
| Pairing popup | Shown or dismissed locally | Re-shows; "Dismiss" is ambiguous | Drive from `/cluster/pending/devices`; buttons from `available_actions` (`ACCEPT`, `REJECT`); add an "Undo reject" list from `/cluster/rejected/devices` |
| `DesktopDaemonManager` | `pingApi()` / `pingUrl()` booleans | Can't tell starting from stopping from crashed | Read `engine_state` from `/system/status`; stop with `POST /system/shutdown`, then wait for exit, instead of killing the process |
| Polling everywhere | ~8 req/s idle | Battery and CPU | Subscribe to `/api/v1/events` (or long-poll `/rest/events?since=`) and update on `*_STATE_CHANGED` events; poll every 30 s as a fallback |

---

## 4. Intentional behaviour changes (all are bug fixes)

| Change | Before | After | Who notices |
|---|---|---|---|
| `GET /connections` `is_connected` for a never-seen device | `true` (whenever not paused) | `false`; `connection_state: "unknown"` | The app now shows **Disconnected** for offline devices, which is correct. One existing test asserted the old value and was updated |
| `POST /cluster/pending/devices/:id/accept` on a non-pending device | 500, SQL error text | 400 `invalid_transition` JSON (paired → idempotent 200) | Only error paths |
| Pause, resume or reject on an **unknown** device | 200 (silently did nothing) | 404 | Only error paths |
| Accept on a **paused** device | Would have resumed it | 400 `invalid_transition` (use resume) | Only misuse |
| Rejecting a device | Request deleted; re-appears on next announcement | Remembered; `GET /cluster/rejected/devices`, `POST …/:id/unreject` | Users stop seeing repeat prompts |
| `POST /system/shutdown` | 200, did nothing | 200 `{"engine_state":"stopping"}`, engine stops gracefully | Nothing in the desktop app calls it (checked in the compiled build) |
| SIGTERM to the daemon | Immediate kill | Graceful stop (≤10 s) | Desktop app closing the engine. Verified: `running → stopping → stopped` |
| JNI `synapse_stop()` | Flipped a flag; engine kept running | Engine actually stops | Android |
| JNI `synapse_is_running()` | `0` right after stop (while still running) | `1` until fully stopped | Android |
| JNI `synapse_start()` while stopping | Would start a 2nd engine and fail on ports | Returns `-5` (retry shortly) | Android |
| Conflict transition errors | 400 + plain text | 400 + JSON that **includes the same text** in `message` | The existing test still passes unchanged |

**Event order is preserved:** after accepting a device, `DEVICE_PAIRED` is still the first event (an existing test checks this). `DEVICE_STATE_CHANGED` follows.

New: JNI `synapse_state()` returns `0` stopped, `1` starting, `2` running, `3` stopping, `4` failed.

---

## 5. API reference (additions only)

| Endpoint | New fields |
|---|---|
| `GET /api/v1/system/status`, `/rest/system/status` | `engine_state`, `folders_by_state` (e.g. `{"idle":2,"error":1}`) |
| `GET /api/v1/folders` | `state`, `error {message, since}`, `lifecycle` |
| `GET /api/v1/folders/:id/status`, `/rest/db/status` | `error`, `lifecycle` |
| `GET /api/v1/devices`, `/api/v1/devices/:id` | `pairing_state`, `connection_state`, `last_seen_secs_ago`, `lifecycle` |
| `GET /api/v1/connections`, `/rest/system/connections` | `connection_state`, `last_seen_secs_ago`, `lifecycle` (and a truthful `is_connected`) |
| `GET /api/v1/cluster/pending/devices` | `pairing_state`, `lifecycle` |
| **New** `GET /api/v1/cluster/rejected/devices` | Rejected requests with `lifecycle` (`UNREJECT`) |
| **New** `POST /api/v1/cluster/rejected/devices/:id/unreject` | Moves the device back to pending |
| `POST /api/v1/system/shutdown` | Body `{"engine_state": "..."}` |
| `GET /api/v1/system/statemachines` | Now 7 machines: + `peer_connection_lifecycle`, `engine_lifecycle`; device `rejected` gains `UNREJECT` |
| Events | `FOLDER_STATE_CHANGED {folder_id, from, to, error}`, `DEVICE_STATE_CHANGED {device_id, from, to}`; `PEER_DISCONNECTED` now actually fires |

`lifecycle` object shape:
```json
{"machine_id":"folder_sync_lifecycle","state":"error","description":"…","is_terminal":false,
 "allowed_transitions":["idle","scanning"],"available_actions":["RESCAN","RETRY_MOUNT","DELETE"]}
```

---

## 6. For agents (AX)

1. **Discover:** `GET /api/v1/system/statemachines` lists every machine, state, transition and action.
2. **Read:** every entity response carries `lifecycle.state` and `lifecycle.available_actions`. Only offer or perform listed actions.
3. **Act, then self-correct:** on an illegal action you get `400 {"error":"invalid_transition", "current_state", "allowed_transitions", "available_actions", "message"}`. Pick from `available_actions` and retry.
4. **Wait without polling:** `GET /rest/events?since=<last id>&timeout=60&events=FOLDER_STATE_CHANGED,DEVICE_STATE_CHANGED,PEER_DISCONNECTED`.
5. **Engine:** `engine_state` in `/system/status`; `POST /system/shutdown` is idempotent.

---

## 7. For developers (DX): adding a lifecycle

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum InvitationState { #[default] Pending, Accepted, Rejected }

impl StateMachine for InvitationState {
    const MACHINE_ID: &'static str = "folder_invitation_lifecycle";
    const DISPLAY_NAME: &'static str = "Folder Invitation";
    const SUBSYSTEM: &'static str = "cluster";
    const DESCRIPTION: &'static str = "…";
    const ENTITY: &'static str = "folder_invitation";
    fn states() -> &'static [Self] { &[Self::Pending, Self::Accepted, Self::Rejected] }
    fn initial() -> Self { Self::Pending }
    fn name(&self) -> &'static str { match self { Self::Pending => "pending", Self::Accepted => "accepted", Self::Rejected => "rejected" } }
    fn describe(&self) -> &'static str { "…" }
    fn permits(&self, next: Self) -> bool { matches!((self, next), (Self::Pending, Self::Accepted) | (Self::Pending, Self::Rejected) | (Self::Rejected, Self::Pending)) || *self == next }
    fn actions(&self) -> Vec<&'static str> { match self { Self::Pending => vec!["ACCEPT", "REJECT"], Self::Rejected => vec!["UNREJECT"], Self::Accepted => vec![] } }
}
```

Then:
1. Add `InvitationState::definition()` to `get_all_state_machines()`.
2. Add `check_machine::<InvitationState>()` to `test_every_state_machine_satisfies_trait_invariants`.
3. Guard with `current.transition(id, next)?`.
4. Return errors through `error_response()` in the API.
5. Embed `LifecycleView::of(state)` in responses.

---

## 8. Verification

| Check | Result |
|---|---|
| Tests | **175 pass**: 155 unit + 19 integration + 1 lifecycle (previously 162). New tests cover the trait invariants for all machines, legacy-metadata equality, registry completeness, relay registry drift, folder stuck-state and error recovery, duplicate scans and panic safety, the full device pairing flow (including the paused-Hello regression), peer presence going offline, and a real engine start → API shutdown → restart, twice |
| Registry | The 5 existing machines are **byte-identical** to before, except the intended device `rejected` change. 2 new machines added |
| API compatibility | All 15 GET endpoints compared before and after on the same data: **status codes identical; every existing field identical**, except the intended `is_connected` fix. Everything else is additive |
| Real app | Packaged desktop app v1.0.6 run against the new engine on an existing database (schema upgrade): dashboard, Folders, Devices and Settings behave as before; Pixel 8 now correctly shows *Disconnected*; no new errors |
| SIGTERM | Graceful: `running → stopping → stopped` |
| Clippy | No new warnings (16, same as before). `result_large_err` is allowed explicitly on the rich transition error |

### Files changed
- `synapse-core/src/state/mod.rs`: trait, `LifecycleView`, `EntityNotFound`, `DeviceAction`, 2 new machines, tests
- `synapse-core/src/lib.rs`: `EngineControl`, lifecycle-aware `init`/`run`, JNI exports
- `synapse-core/src/main.rs`: SIGTERM and graceful shutdown
- `synapse-core/src/engine/mod.rs`: folder work guards, error reasons, events
- `synapse-core/src/storage/mod.rs`: `rejected_devices`, transactional pairing, `apply_device_action`
- `synapse-core/src/p2p/mod.rs`: presence window, sweep, Hello pairing fix
- `synapse-core/src/p2p/relay.rs`: registry drift test
- `synapse-core/src/conflict/mod.rs`: shared guard, new event variants
- `synapse-core/src/api/mod.rs`: lifecycle fields, structured errors, new endpoints, real shutdown
- `synapse-core/tests/integration_test.rs`: new lifecycle tests; one assertion updated (§4)
- `synapse-core/tests/lifecycle_test.rs` (new)

Patch: `docs/synapse-state-machines.patch`.
