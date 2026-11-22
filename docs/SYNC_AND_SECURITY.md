# Device sync, peer security, and release pipeline: 22 November 2026

## What changed, in one paragraph

**Devices now actually sync, and only with devices that can prove who they are.** Every engine presents a TLS certificate made from its own Ed25519 identity key, and both sides require one (mutual TLS 1.3 over QUIC). A device's ID is therefore *proven by the connection*, never taken from a message. Paired devices exchange file indexes, pull what they're missing (every chunk and manifest verified), and push local changes as they happen. Concurrent edits go through the existing merge/quarantine pipeline, so nothing is silently lost.

Alongside that:
- The SQLite vulnerability is fixed.
- Folders added at runtime are watched immediately.
- CI runs on every push.
- Releases build the engine from source.
- Android releases can be signed with a real key.

---

## 1. Peer authentication (`src/p2p/secure.rs`, `src/p2p/mod.rs`)

| Before | After |
|---|---|
| QUIC used a fresh random certificate each run, unrelated to the device identity, with no client auth | The certificate key **is** the device identity key; client certificates are mandatory |
| `device_id` in `Hello` was taken at face value | Device ID is read from the peer's certificate by strict X.509 parsing; a `Hello` claiming another ID is ignored |
| The dialer accepted whoever answered | The dialer pins the expected device ID; a different identity at that address is refused |
| Any connected peer could request any chunk | Only **paired, unpaused** peers can request anything, and only for **folders shared with them** (`chunk_visible_to_device`) |
| Unsolicited `ChunkResponse` was written to the block store | Responses are only accepted as replies to our own requests |
| Introducer lists were trusted from any claimed `introducer_id`, including over UDP broadcast | Only from authenticated peers that **you** marked as introducers, attributed to their proven ID |
| UDP discovery marked devices "online" and broadcast folder IDs and device lists to the LAN | Discovery only starts pairing and tells us where to dial. Presence comes from authenticated connections; folders and device lists are no longer broadcast |
| BEP (plain TCP) peers were matched to paired devices **by name** | BEP peers are listed under a derived `BEP-…` id and never impersonate a paired device |

## 2. Sync protocol

- **Connect:**
  - **When:** a paired device is dialled when its discovery broadcast is heard, and every 30 s at any configured address (`ip:port`, `quic://host:port`, `host:port`), which works across subnets and VPNs.
  - **What happens:** both sides exchange `Hello` and then **pull** every shared folder.
- **Pull:** `IndexRequest` → compare revisions (skip files where we're equal or ahead) → `FileRequest` → `FileNotice` (revision + chunk list + DAG ancestry).
- **Push:** every local change (`FILE_CHANGED`) sends a `FileNotice` to each connected peer sharing that folder.
- **Apply:** before anything is applied:
  - the manifest must match the chunk list;
  - missing chunks are fetched from the sender, and each must hash to its ID;
  - the assembled size must match.

  The engine then fast-forwards, auto-merges, or quarantines using the shared ancestry.
- **No echo:** re-indexing a received file whose content hasn't changed refreshes its metadata without creating a new revision. This prevents ping-pong on filesystems with coarser timestamp precision (e.g. NTFS).
- **Not yet:** deletions and renames don't sync yet (they're skipped safely); there's no bandwidth throttling or relay/NAT traversal on this path; and transfer is whole-file in memory (fine for typical files, not for multi-GB ones).

## 3. Other fixes

- **SQLite vulnerability RUSTSEC-2026-0459:** `rusqlite` 0.32 → **0.43** (`libsqlite3-sys` 0.40). `cargo audit` now reports **0 vulnerabilities**; the only notice left is that `chrono` is unmaintained.
- **Test-only code out of production:** `tokio`'s `test-util` feature moved to dev-dependencies.
- **Folder watching:** folders added through the API are watched immediately (previously only after a restart). Pause stops watching, resume restarts it, delete stops it, and shutdown clears all watchers. No more parked threads.
- **Version numbers:** the CLI `--version` and the startup log come from `Cargo.toml`.

## 4. CI and release

- **New `.github/workflows/ci.yml`** runs on every push and PR:
  - engine build, all tests, lint gate (bug-class clippy lints are errors), release build, `cargo audit`;
  - app compile (shared and desktop) and Android debug assemble.
- **`release.yml`:**
  - builds the engine **from source** for Linux, Windows, and Android (arm64-v8a, armeabi-v7a, x86_64) and runs the engine tests;
  - drops the fresh binaries into `bin/`, `app/desktopApp/bin/` and `jniLibs/` before packaging, so the stale committed binaries no longer reach users;
  - attaches `synapse-core-linux-x86_64`, `synapse-core-windows-x64.exe` and `SHA256SUMS` to the release.
- **Android signing:** `app/androidApp/build.gradle.kts` uses a release key from CI secrets or local properties, and falls back to debug with a visible warning. Setup: `docs/RELEASE_SIGNING.md`.
- **Android manifest:**
  - `allowBackup="false"`, so the device identity key isn't copied into backups (a restored backup would otherwise clone the device's identity);
  - the foreground-service text now names Synapse Core.
- **Note:** `synapse-core/.github/workflows/build-binaries.yml` has **never run**. GitHub only runs workflows in the repo-root `.github/workflows`. Its job is now done by `release.yml`, so you can delete it.

## 5. Verification

| Check | Result |
|---|---|
| Tests | **180 pass** (157 unit, 20 integration, 1 lifecycle, 2 two-node sync) |
| Two engines over real mutual-TLS QUIC | Initial pull (3 MB multi-chunk file, nested folders), live push both ways, no ping-pong, concurrent edits merged or quarantined with nothing lost. **10/10 repeated runs passed** |
| Spoofing | Wrong identity at a dialled address refused; forged `Hello` ignored; unpaired node gets no index or chunks and is recorded as pending under its proven ID |
| Two real daemon processes, configured only via the HTTP API | Found each other by configured address, synced existing files, then live watcher-detected edits both ways; peer shown `online` via `synapse-quic (mTLS)`; no warnings; graceful shutdown |
| Lint gate (`-D clippy::correctness -D clippy::suspicious`) | Passes. Style warnings unchanged at 16 |
| `cargo audit` | 0 vulnerabilities |
| CI and release YAML | Parsed and job graph checked. **Not yet run on GitHub**: the first push is their first real run |
| Gradle signing change | Standard Android Gradle Plugin APIs, but **not compiled here** (Android/Maven repositories are blocked in this environment). CI's `assembleDebug` job will be its first build |

### Compatibility
- **Peer protocol:** engines from *before* today can't talk to new engines over QUIC (they never did mutual TLS). Update all devices together.
- **API:** unchanged. The desktop app keeps working with the new engine (verified earlier today).

## 6. What still needs you

1. **Kotlin app changes** (the 5 UI blockers, the token wiring, replacing the hardcoded key, and the app-side state machines). I can't reach the source from here. Either free about 17 GB of disk so the local shell works, or add these folders in the Claude desktop app:
   - `app\shared\src\commonMain\kotlin\com\arcadelabs\synapse`
   - `app\shared\src\jvmMain\kotlin\com\arcadelabs\synapse`
   - `core\src\commonMain\kotlin\com\arcadelabs\synapse\core`

   **Ship this before rebuilding the engine binaries**, or fresh installs lose conflicts, pairing and Settings data (`ui-review/UI_BLOCKER_FIXES.md` #5).
2. **Commit** (git needs the local shell, which needs disk space). From the project folder:
   ```bash
   git checkout -b synapse-hardening
   git add synapse-core .github app/androidApp/build.gradle.kts app/androidApp/src/main/AndroidManifest.xml docs
   git commit -m "Engine: auth, state machines, mTLS peer sync, CI/release hardening"
   ```
3. **(Optional) Stop tracking committed binaries.** Releases no longer use them.
   - **Desktop:**
     ```bash
     git rm --cached bin/synapse-core app/desktopApp/bin/synapse-core
     ```
     Then remove the `!/bin/synapse-core` and `!/app/desktopApp/bin/synapse-core` lines from `.gitignore`. Locally, `./gradlew buildSynapseCore` rebuilds them.
   - **Android `.so` files:** untrack them only if every developer has the Android NDK. Local Android builds would then need `synapse-core/scripts/build-android.sh` first.
4. **Create the Android release key and add the 4 GitHub secrets** (`docs/RELEASE_SIGNING.md`).
5. **Push**, and check that the first CI run is green.
