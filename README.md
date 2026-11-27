<!-- Hero Banner -->
<p align="center">
  <img src="assets/readme/hero.svg?v=3" alt="Synapse Hero Banner" width="100%">
</p>

<p align="center">
  <strong>Cross-platform, peer-to-peer file sync built with Compose Multiplatform and a Rust engine.</strong>
</p>

<p align="center">
  <a href="#running-the-apps">Get Started</a> •
  <a href="#architecture--workflow">Architecture</a> •
  <a href="#project-structure">Project Structure</a> •
  <a href="#web-deployment-constraints">Web Deployment Constraints</a>
</p>

---

**Synapse** keeps folders in sync directly between your devices, with no cloud in between. Its engine, **Synapse Core** (Rust, in [`/synapse-core`](./synapse-core)), handles indexing, content-defined chunking, end-to-end encrypted peer connections and three-way conflict merging. The app, built with Kotlin Multiplatform and Compose Multiplatform, provides a Material 3 interface across Android, Desktop (JVM), and Web browsers.

---

### 🖥️ Architecture & Workflow

<p align="center">
  <img src="assets/readme/workflow.svg?v=3" alt="Synapse Architecture & Workflow" width="100%">
</p>

* **Synapse GUI:** Communicates with the daemon REST API and opens WebSockets to receive real-time sync state updates.
* **Synapse Core engine:** Runs in the background, indexing folders and exchanging files with paired devices over mutually authenticated QUIC connections.

---

### ✨ Features

* **Material 3 Theme:** Styling for dark and light modes with status indicators (green for connected, amber for paused, red for disconnected).
* **Tray Integration (Desktop):** Minimize to system tray on Windows/macOS with background operation and desktop notifications.
* **Auto-Start on Boot:** Support for automatic startup registration on Windows target.
* **Wasm & JS Web Targets:** Kotlin/Wasm web client with JS fallback.

---

### 📁 Project Structure

* **[`/app/shared`](./app/shared/src)**: The core UI and business logic shared across all platforms using Compose Multiplatform.
  * **[`commonMain`](./app/shared/src/commonMain/kotlin)**: Shared layouts, controllers, and resources.
  * **`androidMain`**, **`jvmMain`**: Target-specific hooks for native APIs.
* **[`/app/androidApp`](./app/androidApp)**: Native entry point and services for the Android application.
* **[`/app/desktopApp`](./app/desktopApp)**: Entry point for the JVM Desktop target, system tray configurations, and native Windows bindings.
* **[`/app/webApp`](./app/webApp)**: Builds the WASM and JS browser packages.
* **[`/core`](./core/src)**: Shared networking, configuration databases, and utility logic.
* **[`/server`](./server/src/main/kotlin)**: Built-in Ktor API server.

---

### 🚀 Running the Apps

You can run these configurations directly from Android Studio / IntelliJ IDEA's run widget, or run them from the terminal:

#### 📱 Android Target
* **Android App:**
  ```bash
  ./gradlew :app:androidApp:assembleDebug
  ```

#### 💻 Desktop Target
* **Development (Hot Reload):**
  ```bash
  ./gradlew :app:desktopApp:hotRun --auto
  ```
* **Standard Execution:**
  ```bash
  ./gradlew :app:desktopApp:run
  ```

#### 🐧 Linux Packaging (Arch Linux & CachyOS)
* **Arch Linux Build (`PKGBUILD`):**
  ```bash
  cd packaging/arch
  makepkg -si
  ```
* **CachyOS Build (`PKGBUILD`):**
  ```bash
  cd packaging/cachyos
  ./build-cachyos.sh
  ```

#### 🌐 Web Target
* **Web (WasmJs):**
  ```bash
  ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun
  ```
* **Web (JS):**
  ```bash
  ./gradlew :app:webApp:jsBrowserDevelopmentRun
  ```

#### 🔌 Backend Server
* **Ktor Server:**
  ```bash
  ./gradlew :server:run
  ```

---

### 🧪 Running Tests

Run tests inside your IDE gutter or run them from the command line:

* **Android:** `./gradlew :app:shared:testAndroidHostTest`
* **Desktop:** `./gradlew :app:shared:jvmTest`
* **Web (Wasm):** `./gradlew :app:shared:wasmJsTest`
* **Web (JS):** `./gradlew :app:shared:jsTest`
* **Server:** `./gradlew :server:test`

---

### ⚠️ Web Deployment Constraints

When compiling or deploying the Web (WasmJs/JS) targets, browsers enforce strict security rules that affect communication with the Synapse Core engine:

> [!WARNING]
> **CORS (Cross-Origin Resource Sharing)**
> By default, the Synapse Core engine rejects requests from browser origins it has not been told to trust. This prevents browser applications hosted on a different origin (e.g., Synapse on `http://localhost:8080`) from making REST calls to the daemon.
> 
> * **Workaround:** Start the engine with `--allow-origin <origin>` for the web app's origin, run the Synapse Web App as a browser extension with host permissions, or serve both the Synapse assets and the daemon through a reverse proxy.

> [!IMPORTANT]
> **Mixed-Content Restrictions**
> Modern web browsers block insecure HTTP requests (like the local engine on `http://127.0.0.1:8384`) from sites served securely over HTTPS.
> 
> * **Workaround:** If hosting Synapse on a secure HTTPS server, you must serve the engine's API over HTTPS, e.g. through a reverse proxy (and ensure you accept its certificate in your browser).