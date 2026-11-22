# Synapse UI blockers: root causes and fixes

**Date:** 22 November 2026
**Scope:** issues #1–#5 from `SYNAPSE_UI_REVIEW.md` §3.

| # | Issue | Root cause found in | Fix status |
|---|---|---|---|
| 1 | "Inspect Diff" crashes the app | `QuarantineDialog.kt` → `DiffViewer` | ⏳ App change below |
| 2 | Edit Folder share switches always off | `SyncthingApiClient.kt` → `systemConfig()` (Synapse branch) | ⏳ App change below |
| 3 | Conflict counter grows every poll | `SyncthingApiClient.kt` → `getEvents()` + `DesktopDashboardScreen.kt` → `ThisDeviceSection` | ⏳ App change below |
| 4 | Raw exception on Recent Changes | Engine had no `GET /rest/events`; app shows `e.message` | ✅ **Engine fixed** · ⏳ small app change |
| 5 | False "Daemon is not running" in Settings | `CoreDiModule.kt` token wiring + generic error text | ⏳ App change below |

## How these were found
I couldn't open the Kotlin source in this session; it's nested too deep in the folder tree to copy. Instead I:
- ran the packaged app (`packaging/cachyos/.../Synapse.jar`, v1.0.6) against a logging build of the engine and recorded every request;
- read the compiled bytecode (`javap`) of the relevant classes.

Each root cause below is confirmed in the bytecode. The code snippets are written from that, so variable names may differ slightly from your source.

---

## ✅ Already done: engine side of #4

`GET /rest/events` and `GET /api/v1/events` now work two ways:
- **WebSocket upgrade:** a live stream, the same as before.
- **Plain GET:** a **Syncthing-compatible long-poll** with `since`, `limit`, `timeout` and `events` (a comma-separated type filter).
  - The engine keeps the last 1,000 events in Syncthing's shape: `{id, globalID, type, time, data}`.
  - File events use Syncthing names:
    - `ItemFinished`: `data.folder`, `data.item`, `data.action`, `data.error`
    - `ItemStarted`
    - `LocalChangeDetected`: `data.folder`, `data.path`, `data.action`
  - Other events keep their Synapse names (`CONFLICT_DETECTED`, `PEER_CONNECTED`, …).
  - `since=0` returns immediately. `since>0` waits up to `timeout` seconds (default 60, max 300) for new events.
  - A WebSocket request that can't be upgraded still gets `426`.

**Verified in the real app:** the raw exception is gone, and the screen loads. `curl` shows `LocalChangeDetected` events for files I edited.

**Tests:** a new integration test, `test_rest_events_long_poll_syncthing_format`. Totals: 147 unit + 15 integration, all passing.

**File changed:** `synapse-core/src/api/mod.rs` (+ `tests/integration_test.rs`).

---

## #1 "Inspect Diff" crashes: `features/quarantine/QuarantineDialog.kt`

**Cause.** `DiffViewer` is rendered *inside a `LazyColumn` item* (the conflict card) with this modifier order:

```kotlin
Modifier
    .padding(12.dp)
    .verticalScroll(rememberScrollState())
    .heightIn(max = 260.dp)        // ← too late: the scroll container is already measured unbounded
```

Modifiers apply in order. The `verticalScroll` node sees the LazyColumn's **infinite** max height before `heightIn` can cap it, so Compose throws *"Vertically scrollable component was measured with an infinity maximum height constraints"*. On desktop that exception kills the window.

**Fix.** Cap the height *before* the scroll:

```kotlin
Modifier
    .padding(12.dp)
    .heightIn(max = 260.dp)        // bound first
    .verticalScroll(rememberScrollState())
```

**Also fix "Ignore" wrapping one letter per line.** In the same card, the action `Row` has five buttons and fixed weights. Use a `FlowRow` (or give "Ignore" `Modifier.wrapContentWidth()` with `softWrap = false`):

```kotlin
FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    /* Keep Local, Keep Remote, Keep Both, Inspect Diff, Ignore */
}
```

---

## #2 Edit Folder share switches: `core/network/SyncthingApiClient.kt`, `systemConfig()`

**Cause.** In the Synapse Core branch, folders are built from `GET /api/v1/folders` (`CoreFolder`, which has no device list), and each `Folder` is created with **`devices = emptyList()`**. `DesktopEditFolderDialog` / `EditFolderDialog` seed their switch state from `folder.devices`, so every switch starts off. Pressing **Save** then sends that empty list back, **unsharing the folder from everyone**.

The engine already returns correct shares in Syncthing format at `GET /rest/system/config`:
```json
{"id":"obsidian", "devices":[{"deviceID":"MFZWI3D-…","encryptionPassword":""}], …}
```

**Fix (option A, simplest).** For Synapse Core, decode `/rest/system/config` directly, the same as the Syncthing branch:

```kotlin
override suspend fun systemConfig(): SyncthingConfig =
    client.get("$baseUrl/rest/system/config") { authHeaders() }.body()
```

**Fix (option B, keep the native API).** Fill in each folder's devices:

```kotlin
val folders = core.getFolders().map { cf ->
    val shared = client.get("$baseUrl/api/v1/folders/${cf.folderId.encodeURLPath()}/devices") { authHeaders() }
        .body<List<String>>()
    Folder(
        id = cf.folderId, label = cf.folderId, path = cf.localPath, type = cf.syncMode,
        paused = cf.isPaused,
        devices = shared.map { FolderDeviceReference(deviceID = it) },   // ← was emptyList()
        /* … */
    )
}
```

**Guard on save.** In `FolderViewModel`, before `updateSystemConfig`, refuse to save if the config was never loaded (`devices` empty *and* the folder is shared according to `/api/v1/folders/{id}/devices`), so a load failure can never wipe the shares.

**Add Device:** the switches starting off is correct for a *new* device. In **Edit Device**, seed them from the folders whose `devices` contain this device ID, which follows once `systemConfig()` is fixed.

---

## #3 Conflict counter: `SyncthingApiClient.kt` `getEvents()` + `DesktopDashboardScreen.kt`

**Cause (two bugs together).**

1. **`SyncthingApiClientImpl.getEvents(since, limit)`**, Synapse branch: it doesn't call any events endpoint. It calls `GET /api/v1/conflicts` and turns **every current conflict** into a fake `Event(type = "ItemFinished", data = {item: "…sync-conflict…"})`, ignoring `since`. So every call returns the same N "new" events.
2. **`ThisDeviceSection`** (`DesktopDashboardScreen.kt`) polls `getEvents(lastId, …)` and does:
   ```kotlin
   syncConflictsCount = syncConflictsCount + events.count { it.type == "ItemFinished" && it.data["item"]…contains("sync-conflict") }
   ```
   It **accumulates**, so 2 conflicts add 2 more every poll: 4, then 22, then 38.

**Fix.**

In `ThisDeviceSection`, read the real number and don't accumulate:

```kotlin
// remove syncConflictsCount + the events loop
val conflicts by quarantineViewModel.uiState.collectAsState()   // or poll core.getConflicts()
val conflictCount = conflicts.conflicts.count { it.status == "PENDING" }
// or: statusUiState.pendingConflictsCount from GET /api/v1/system/status ("pending_conflicts_count")
```

In `SyncthingApiClientImpl.getEvents()`, delete the synthetic-conflict branch and call the real endpoint for **both** backends (the engine now supports it):

```kotlin
override suspend fun getEvents(since: Int, limit: Int): List<Event> =
    client.get("$baseUrl/rest/events") {
        authHeaders()
        parameter("since", since)
        parameter("limit", limit)
        parameter("timeout", 0)        // or omit for long-poll in a background loop
    }.body()
```

---

## #4 Recent Changes: `features/recent/ui/RecentChangesViewModel.kt` + `RecentChangesScreen.kt`

The engine side is fixed (above). Two small app changes remain:

1. **Show local changes too.** The ViewModel only keeps `type == "ItemFinished"`, which is a file pulled *from another device*. Local edits arrive as `LocalChangeDetected` (its path is in `data.path`, not `data.item`):
   ```kotlin
   private val shownTypes = setOf("ItemFinished", "LocalChangeDetected", "RemoteChangeDetected")

   events.filter { it.type in shownTypes }.map { e ->
       val path = e.data["item"]?.jsonPrimitive?.contentOrNull
           ?: e.data["path"]?.jsonPrimitive?.contentOrNull ?: "?"
       RecentChangeUiModel(
           folder = e.data["folder"]?.jsonPrimitive?.contentOrNull ?: "",
           path = path,
           action = e.data["action"]?.jsonPrimitive?.contentOrNull ?: "update",
           isLocal = e.type == "LocalChangeDetected",
           time = e.time,
       )
   }
   ```
2. **Never show raw exception text.** Replace `state.error = e.message` with a friendly message, and log the detail:
   ```kotlin
   catch (e: Exception) {
       logger.warn("Recent changes failed", e)
       _state.update { it.copy(error = "Couldn't load recent changes. Check that Synapse is running, then retry.") }
   }
   ```

---

## #5 "Daemon is not running" in Settings: `core/di/CoreDiModule.kt` (+ `SettingsViewModel.kt`)

**Cause.**
1. `SynapseCoreClientImpl` is built with `tokenProvider = { preferencesHelper.getApiKey() }`. That's empty on a fresh install, so every native call (`/api/v1/system/status`, conflicts, pending devices, version) goes **without a token** and gets `401` from the fixed engine. Verified: 145 unauthenticated calls in 25 s. `SyncthingApiClientImpl`, by contrast, resolves the key through `ApiKeyProvider` (the managed key), so the folder and device lists still load. That's why the dashboard looks "Connected" while Settings fails.
2. `SettingsViewModel` turns **any** failure into the text "Daemon is not running".

**Fix: use the same key source for both clients** (`CoreDiModule.kt`):

```kotlin
single<SynapseCoreClient> {
    val apiKeyProvider: ApiKeyProvider = get()
    val prefs: PreferencesHelper = get()
    SynapseCoreClientImpl(
        httpClient = get(),
        baseUrlProvider = { prefs.getApiBaseUrl() },
        tokenProvider = { runBlocking { apiKeyProvider.getApiKey() } ?: prefs.getApiKey() },  // ← was prefs.getApiKey()
    )
}
```

(If `ApiKeyProvider.getApiKey()` is already non-suspending, drop the `runBlocking`. A cleaner option is to make `tokenProvider` a `suspend () -> String?`.)

**Fix: specific error messages** (`SettingsViewModel.loadSettings` / `saveSettings`):

```kotlin
catch (e: SyncthingUnauthorizedException) { error = "Synapse rejected the app's API key. Restart Synapse to refresh it." }
catch (e: ConnectException)               { error = "Synapse isn't running." }
catch (e: Exception)                      { error = "Couldn't load settings." ; logger.warn("settings", e) }
```

**Also (security, see `SYNAPSE_AUDIT.md`):** replace the hardcoded `MANAGED_API_KEY = "synapse-managed-api-key-2025"`. Let the engine generate `<home>/api.token`, and have `JvmApiKeyProvider` / `AndroidApiKeyProvider` read it, so each install has its own secret.

---

## After applying: quick test list

1. **#1:** open conflicts and click **Inspect Diff**. The diff appears and scrolls inside the card, with no crash. "Ignore" reads on one line.
2. **#2:** share a folder with a device, then open Edit Folder. The switch is **on**. Press Save without changes, and the folder is still shared (`curl /api/v1/folders/<id>/devices`).
3. **#3:** with 2 conflicts, leave the dashboard open 2 minutes. It stays at **2**. Resolve one, and it shows **1**.
4. **#4:** edit a file in a synced folder. It appears in Recent Changes within a few seconds. Stop the engine: a friendly error appears, not a stack trace.
5. **#5:** start from a **fresh** profile (delete the saved API-key preference). Settings shows the device ID, with no "Daemon is not running".
