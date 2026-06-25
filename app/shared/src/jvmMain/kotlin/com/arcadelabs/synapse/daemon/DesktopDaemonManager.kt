package com.arcadelabs.synapse.daemon

import com.arcadelabs.synapse.core.domain.models.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

sealed class DaemonState {
    object Idle : DaemonState()
    data class Downloading(val progress: Float) : DaemonState()
    object Starting : DaemonState()
    data class Ready(val apiKey: String, val apiBaseUrl: String) : DaemonState()
    data class Error(val message: String) : DaemonState()
}

class DesktopDaemonManager {
    private val _state = MutableStateFlow<DaemonState>(DaemonState.Idle)
    val state: StateFlow<DaemonState> = _state

    private var process: Process? = null
    @Volatile
    private var isStopped = false

    companion object {
        private const val VERSION      = "1.27.8"
        private const val DOWNLOAD_URL = "https://github.com/syncthing/syncthing/releases/download/v$VERSION/syncthing-windows-amd64-v$VERSION.zip"

        // Fixed credentials used when Synapse manages the daemon itself.
        // Because we inject these at launch, we never need to parse config.xml.
        const val MANAGED_API_KEY  = "synapse-managed-api-key-2025"
        const val MANAGED_BASE_URL = "http://127.0.0.1:8384"
    }

    fun start() {
        isStopped = false
        Thread {
            try {
                runDaemonLifecycle()
            } catch (e: Exception) {
                _state.value = DaemonState.Error("Unexpected daemon failure: ${e.message}")
            }
        }.start()
    }

    fun stop() {
        isStopped = true
        try {
            process?.destroy()
            process = null
        } catch (_: Exception) {}
    }

    private fun runDaemonLifecycle() {
        val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val synapseDir = File(localAppData, "Synapse")
        val binDir = File(synapseDir, "bin")
        val binFile = File(binDir, "syncthing.exe")
        val homeDir = File(synapseDir, "syncthing-home")
        homeDir.mkdirs()

        // ── Step 1: Check if Syncthing is ALREADY running on 8384 ──────────────
        // If so, find its API key and connect directly — no new process needed.
        val existingResult = tryConnectToRunning(localAppData)
        if (existingResult != null) {
            println("[Synapse] Connected to existing Syncthing instance at ${existingResult.second}")
            _state.value = DaemonState.Ready(existingResult.first, existingResult.second)
            return
        }

        // ── Step 2: Download binary if missing ─────────────────────────────────
        if (!binFile.exists()) {
            _state.value = DaemonState.Downloading(0f)
            val tempZip = File(System.getProperty("java.io.tmpdir"), "syncthing.zip")
            try {
                downloadZip(DOWNLOAD_URL, tempZip) { progress ->
                    _state.value = DaemonState.Downloading(progress)
                }
                _state.value = DaemonState.Starting
                extractExeFromZip(tempZip, binFile)
                tempZip.delete()
            } catch (e: Exception) {
                tempZip.delete()
                _state.value = DaemonState.Error("Failed to download Syncthing: ${e.message}")
                return
            }
        }

        // ── Step 3: Start our own managed daemon ───────────────────────────────
        try {
            val pb = ProcessBuilder(
                binFile.absolutePath,
                "serve",
                "--home",        homeDir.absolutePath,
                "--no-browser",
                "--no-restart",
                "--no-upgrade",
                "--gui-apikey",  MANAGED_API_KEY,
                "--gui-address", "127.0.0.1:8384"
            )
            pb.environment()["STNOUPGRADE"] = "1"
            pb.redirectErrorStream(true)

            while (!isStopped) {
                _state.value = DaemonState.Starting
                val proc = pb.start()
                process = proc

                Thread {
                    try {
                        proc.inputStream.bufferedReader().use { reader ->
                            var line = reader.readLine()
                            while (line != null) {
                                println("[Syncthing] $line")
                                line = reader.readLine()
                            }
                        }
                    } catch (_: Exception) {}
                }.start()

                Runtime.getRuntime().addShutdownHook(Thread { proc.destroy() })

                // ── Step 4: Ping until the REST API is up (no config parsing needed) ──
                var ready = false
                repeat(60) { // up to 30 seconds
                    if (!ready && pingApi(MANAGED_BASE_URL, MANAGED_API_KEY)) {
                        ready = true
                    }
                    if (!ready) Thread.sleep(500)
                }

                if (!ready) {
                    _state.value = DaemonState.Error("Syncthing did not respond after 30 seconds.")
                    return
                }

                _state.value = DaemonState.Ready(MANAGED_API_KEY, MANAGED_BASE_URL)

                val exitCode = proc.waitFor()
                println("[Syncthing] Daemon process exited with code $exitCode")

                if (exitCode != 3 || isStopped) {
                    break
                }

                // Sleep briefly before restarting
                Thread.sleep(1000)
            }

        } catch (e: Exception) {
            _state.value = DaemonState.Error("Failed to start Syncthing daemon: ${e.message}")
        }
    }


    /**
     * Checks if Syncthing is already running on localhost. Searches several
     * well-known config locations for the API key, then verifies connectivity.
     * Returns Pair(apiKey, baseUrl) on success, null otherwise.
     */
    private fun tryConnectToRunning(localAppData: String): Pair<String, String>? {
        val candidateConfigs = listOf(
            // Standard Syncthing install on Windows
            File(localAppData, "Syncthing${File.separator}config.xml"),
            // Synapse-managed Syncthing
            File(localAppData, "Synapse${File.separator}syncthing-home${File.separator}config.xml"),
            // Roaming AppData fallback
            File(System.getenv("APPDATA") ?: "", "Syncthing${File.separator}config.xml")
        )

        for (configFile in candidateConfigs) {
            if (!configFile.exists()) continue
            val parsed = parseApiKeyAndAddress(configFile) ?: continue
            val (apiKey, baseUrl) = parsed
            // Verify the running instance responds with this key
            if (pingApi(baseUrl, apiKey)) {
                return Pair(apiKey, baseUrl)
            }
        }
        return null
    }

    /** Parses the first <gui> block in config.xml for apikey + address. */
    private fun parseApiKeyAndAddress(configFile: File): Pair<String, String>? {
        return try {
            val content = configFile.readText()
            val guiContent = "(?s)<gui\\b[^>]*>(.*?)</gui>".toRegex()
                .find(content)?.groupValues?.get(1) ?: return null
            val apiKey = "(?i)<apikey[^>]*>\\s*([^<\\s]+)\\s*</apikey>".toRegex()
                .find(guiContent)?.groupValues?.get(1)?.trim() ?: return null
            val address = "(?i)<address[^>]*>\\s*([^<\\s]+)\\s*</address>".toRegex()
                .find(guiContent)?.groupValues?.get(1)?.trim() ?: return null
            if (apiKey.isEmpty() || address.isEmpty()) return null
            val baseUrl = if (address.startsWith("http")) address else "http://$address"
            Pair(apiKey, baseUrl)
        } catch (_: Exception) { null }
    }

    /** Returns true if the Syncthing REST API responds to a ping with this key. */
    private fun pingApi(baseUrl: String, apiKey: String): Boolean {
        return try {
            val url = java.net.URI.create("$baseUrl/rest/system/ping").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.responseCode == 200
        } catch (_: Exception) { false }
    }


    private fun downloadZip(urlStr: String, destFile: File, onProgress: (Float) -> Unit) {
        val url = java.net.URI.create(urlStr).toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 25000
        connection.readTimeout = 25000
        val fileLength = connection.contentLength
        
        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val data = ByteArray(16384)
                var total = 0L
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)
                    if (fileLength > 0) {
                        onProgress(total.toFloat() / fileLength.toFloat())
                    }
                }
            }
        }
    }

    private fun extractExeFromZip(zipFile: File, destFile: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith("syncthing.exe")) {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { out ->
                        val buffer = ByteArray(16384)
                        var len: Int
                        while (zipIn.read(buffer).also { len = it } != -1) {
                            out.write(buffer, 0, len)
                        }
                    }
                    zipIn.closeEntry()
                    return
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        throw IOException("Could not locate 'syncthing.exe' in the downloaded release archive.")
    }
}
