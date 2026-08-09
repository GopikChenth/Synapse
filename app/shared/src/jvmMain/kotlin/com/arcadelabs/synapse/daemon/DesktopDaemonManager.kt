package com.arcadelabs.synapse.daemon

import com.arcadelabs.synapse.core.domain.models.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.zip.GZIPInputStream
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
        private const val VERSION = "1.27.8"

        private val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isMac = osName.contains("mac")
        val isLinux = !isWindows && !isMac
        val exeName = if (isWindows) "syncthing.exe" else "syncthing"

        const val MANAGED_API_KEY  = "synapse-managed-api-key-2025"
        const val MANAGED_BASE_URL = "http://127.0.0.1:8384"

        private fun getDownloadUrl(): String {
            return when {
                isWindows -> "https://github.com/syncthing/syncthing/releases/download/v$VERSION/syncthing-windows-amd64-v$VERSION.zip"
                isMac -> "https://github.com/syncthing/syncthing/releases/download/v$VERSION/syncthing-macos-amd64-v$VERSION.zip"
                else -> "https://github.com/syncthing/syncthing/releases/download/v$VERSION/syncthing-linux-amd64-v$VERSION.tar.gz"
            }
        }
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

    fun getProcessId(): Int? {
        return try {
            process?.pid()?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    private fun runDaemonLifecycle() {
        val userHome = System.getProperty("user.home") ?: "."
        val localAppData = System.getenv("LOCALAPPDATA") ?: userHome
        val synapseDir = File(localAppData, "Synapse")
        val binDir = File(synapseDir, "bin")
        binDir.mkdirs()

        val homeDir = File(synapseDir, "syncthing-home")
        homeDir.mkdirs()

        // ── Step 1: Check if Syncthing is ALREADY running on 8384 ──────────────
        val existingResult = tryConnectToRunning(localAppData, synapseDir)
        if (existingResult != null) {
            println("[Synapse] Connected to existing Syncthing instance at ${existingResult.second}")
            _state.value = DaemonState.Ready(existingResult.first, existingResult.second)
            return
        }

        // ── Step 2: Resolve daemon binary (System PATH -> Local bin -> Download) ──
        var binFile = resolveExecutableFile(binDir)

        if (!binFile.exists() || binFile.isDirectory) {
            _state.value = DaemonState.Downloading(0f)
            val downloadUrl = getDownloadUrl()
            val tempArchive = File(System.getProperty("java.io.tmpdir"), if (downloadUrl.endsWith(".tar.gz")) "syncthing.tar.gz" else "syncthing.zip")
            try {
                downloadFile(downloadUrl, tempArchive) { progress ->
                    _state.value = DaemonState.Downloading(progress)
                }
                _state.value = DaemonState.Starting
                val targetFile = File(binDir, exeName)
                extractBinaryFromArchive(tempArchive, targetFile)
                tempArchive.delete()
                targetFile.setExecutable(true, false)
                binFile = targetFile
            } catch (e: Exception) {
                tempArchive.delete()
                _state.value = DaemonState.Error("Failed to download Syncthing: ${e.message}")
                return
            }
        }

        if (!binFile.exists() || binFile.isDirectory) {
            _state.value = DaemonState.Error("Invalid Syncthing executable path: ${binFile.absolutePath}")
            return
        }

        if (!isWindows) {
            binFile.setExecutable(true, false)
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

                // ── Step 4: Ping until the REST API is up ─────────────────────
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

    private fun resolveExecutableFile(binDir: File): File {
        val localExe = File(binDir, exeName)
        if (localExe.exists() && localExe.isFile) return localExe

        val localAltExe = File(binDir, if (isWindows) "syncthing" else "syncthing.exe")
        if (localAltExe.exists() && localAltExe.isFile) return localAltExe

        // Check system installation paths on Linux/macOS
        val systemCandidates = listOf(
            "/usr/bin/syncthing",
            "/usr/local/bin/syncthing",
            "/opt/homebrew/bin/syncthing"
        )
        for (path in systemCandidates) {
            val f = File(path)
            if (f.exists() && f.isFile && f.canExecute()) return f
        }

        // Check PATH environment
        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) continue
            val f = File(dir, exeName)
            if (f.exists() && f.isFile && f.canExecute()) return f
        }

        return localExe
    }

    private fun tryConnectToRunning(localAppData: String, synapseDir: File): Pair<String, String>? {
        val userHome = System.getProperty("user.home") ?: ""
        val candidateConfigs = listOf(
            File(localAppData, "Syncthing${File.separator}config.xml"),
            File(synapseDir, "syncthing-home${File.separator}config.xml"),
            File(System.getenv("APPDATA") ?: "", "Syncthing${File.separator}config.xml"),
            File(userHome, ".config/syncthing/config.xml"),
            File(userHome, "Library/Application Support/Syncthing/config.xml")
        )

        for (configFile in candidateConfigs) {
            if (!configFile.exists()) continue
            val parsed = parseApiKeyAndAddress(configFile) ?: continue
            val (apiKey, baseUrl) = parsed
            if (pingApi(baseUrl, apiKey)) {
                return Pair(apiKey, baseUrl)
            }
        }
        return null
    }

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

    private fun downloadFile(urlStr: String, destFile: File, onProgress: (Float) -> Unit) {
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

    private fun extractBinaryFromArchive(archiveFile: File, destFile: File) {
        destFile.parentFile?.mkdirs()
        if (archiveFile.name.endsWith(".tar.gz")) {
            extractBinaryFromTarGz(archiveFile, destFile)
        } else {
            extractBinaryFromZip(archiveFile, destFile)
        }
    }

    private fun extractBinaryFromZip(zipFile: File, destFile: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val entryName = entry.name.substringAfterLast("/")
                if (!entry.isDirectory && (entryName == exeName || entryName == "syncthing.exe" || entryName == "syncthing")) {
                    FileOutputStream(destFile).use { out ->
                        zipIn.copyTo(out)
                    }
                    zipIn.closeEntry()
                    return
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        throw IOException("Could not locate '$exeName' in downloaded zip archive.")
    }

    private fun extractBinaryFromTarGz(tarGzFile: File, destFile: File) {
        GZIPInputStream(BufferedInputStream(tarGzFile.inputStream())).use { gzipIn ->
            val buffer = ByteArray(512)
            while (true) {
                var bytesRead = 0
                while (bytesRead < 512) {
                    val count = gzipIn.read(buffer, bytesRead, 512 - bytesRead)
                    if (count == -1) break
                    bytesRead += count
                }
                if (bytesRead < 512) break

                val headerName = String(buffer, 0, 100, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                if (headerName.isBlank()) break

                val sizeStr = String(buffer, 124, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val fileSize = try { sizeStr.toLong(8) } catch (_: Exception) { 0L }
                val typeFlag = buffer[156]

                val fileName = headerName.substringAfterLast("/")
                val isTarget = (typeFlag == '0'.code.toByte() || typeFlag == 0.toByte()) &&
                        (fileName == exeName || fileName == "syncthing")

                if (isTarget && fileSize > 0) {
                    FileOutputStream(destFile).use { out ->
                        var remaining = fileSize
                        val readBuffer = ByteArray(8192)
                        while (remaining > 0) {
                            val toRead = minOf(remaining, readBuffer.size.toLong()).toInt()
                            val numRead = gzipIn.read(readBuffer, 0, toRead)
                            if (numRead == -1) break
                            out.write(readBuffer, 0, numRead)
                            remaining -= numRead
                        }
                    }
                    return
                } else {
                    var remaining = fileSize
                    val padding = if (fileSize % 512 != 0L) 512 - (fileSize % 512) else 0L
                    remaining += padding
                    val skipBuffer = ByteArray(8192)
                    while (remaining > 0) {
                        val toSkip = minOf(remaining, skipBuffer.size.toLong()).toInt()
                        val numRead = gzipIn.read(skipBuffer, 0, toSkip)
                        if (numRead == -1) break
                        remaining -= numRead
                    }
                }
            }
        }
        throw IOException("Could not locate '$exeName' in downloaded tar.gz archive.")
    }
}
