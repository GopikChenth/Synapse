package com.arcadelabs.synapse.cli

import com.arcadelabs.synapse.daemon.DesktopDaemonManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI

/**
 * Headless CLI and Hyprland actions handler for Synapse on Linux.
 *
 * Provides ultra-fast command dispatch for Hyprland hotkeys, Desktop Actions,
 * scratchpad management via hyprctl, notifications via notify-send, and Waybar status JSON.
 */
object LinuxHyprlandCli {

    fun handle(args: Array<String>): Boolean {
        if (args.isEmpty()) return false

        val command = args[0].lowercase().trimStart('-')

        when (command) {
            "help", "h", "?" -> {
                showHelp()
                return true
            }
            "toggle", "t" -> {
                handleToggle()
                return true
            }
            "pause", "p" -> {
                handlePause()
                return true
            }
            "resume", "r" -> {
                handleResume()
                return true
            }
            "toggle-sync" -> {
                handleToggleSync()
                return true
            }
            "rescan", "scan", "s" -> {
                val folder = args.getOrNull(1)
                handleRescan(folder)
                return true
            }
            "status" -> {
                handleStatus()
                return true
            }
            "waybar" -> {
                handleWaybar()
                return true
            }
            "setup-hyprland" -> {
                handleSetupHyprland()
                return true
            }
            else -> {
                // If the flag is not a recognized CLI action, let the GUI application handle it
                return false
            }
        }
    }

    private fun showHelp() {
        println(
            """
            Synapse Linux & Hyprland CLI Helper
            ===================================

            Usage:
              synapse [command] [options]

            Actions:
              --toggle, -t             Toggle Synapse scratchpad in Hyprland (or launch if not running)
              --pause, -p              Pause syncing with every device
              --resume, -r             Resume syncing with every device
              --toggle-sync            Resume all devices if all are paused, otherwise pause all
              --rescan, -s [folder]    Trigger a rescan for all folders (or a specific folder ID)
              --status                 Display current daemon and sync status in terminal
              --waybar                 Output JSON status formatted for Waybar / eww status bars
              --setup-hyprland         Install window rules and hotkey templates to ~/.config/hypr/synapse.conf
              --help, -h               Show this help message

            Hyprland Hotkey Example (hyprland.conf):
              bind = ${'$'}mainMod, S, exec, synapse --toggle
              bind = ${'$'}mainMod SHIFT, P, exec, synapse --pause
              bind = ${'$'}mainMod SHIFT, R, exec, synapse --resume
              bind = ${'$'}mainMod SHIFT, S, exec, synapse --rescan

            Waybar Custom Module (config.jsonc):
              "custom/synapse": {
                  "format": "{icon} {}",
                  "return-type": "json",
                  "exec": "synapse --waybar",
                  "interval": 3,
                  "on-click": "synapse --toggle",
                  "on-click-right": "synapse --toggle-sync",
                  "on-click-middle": "synapse --rescan"
              }
            """.trimIndent()
        )
    }

    private fun handleToggle() {
        val isHyprland = System.getenv("HYPRLAND_INSTANCE_SIGNATURE") != null || isCommandAvailable("hyprctl")
        if (!isHyprland) {
            println("[Synapse] Hyprland session not detected. Launching Synapse...")
            launchAppInBackground()
            return
        }

        // Query active windows in Hyprland
        val clientsJson = runShellCommand("hyprctl", "clients", "-j")
        val isAppRunning = clientsJson.contains("\"class\": \"Synapse\"", ignoreCase = true) ||
                clientsJson.contains("\"class\": \"com-arcadelabs-synapse-MainKt\"", ignoreCase = true) ||
                clientsJson.contains("\"initialClass\": \"Synapse\"", ignoreCase = true)

        if (isAppRunning) {
            // Check if it is currently in the special workspace 'special:synapse'
            val onSpecialWorkspace = clientsJson.contains("\"special:synapse\"", ignoreCase = true) ||
                    clientsJson.contains("\"name\": \"special:synapse\"", ignoreCase = true)

            if (onSpecialWorkspace) {
                runShellCommand("hyprctl", "dispatch", "togglespecialworkspace", "synapse")
            } else {
                // Focus window or bring it to front
                runShellCommand("hyprctl", "dispatch", "focuswindow", "class:Synapse")
            }
        } else {
            notify("Synapse", "Starting Synapse in scratchpad...")
            launchAppInBackground()
            // Allow JVM window to map into special workspace
            Thread {
                try {
                    Thread.sleep(600)
                    runShellCommand("hyprctl", "dispatch", "togglespecialworkspace", "synapse")
                } catch (_: Exception) {}
            }.start()
        }
    }

    private fun handlePause() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            notify("Synapse", "Synapse daemon is offline.", urgency = "critical")
            println("Error: Synapse daemon is offline.")
            return
        }
        val (baseUrl, apiKey) = conn
        setAllDevicesPaused(baseUrl, apiKey, pause = true)
    }

    private fun handleResume() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            notify("Synapse", "Synapse daemon is offline.", urgency = "critical")
            println("Error: Synapse daemon is offline.")
            return
        }
        val (baseUrl, apiKey) = conn
        setAllDevicesPaused(baseUrl, apiKey, pause = false)
    }

    private fun handleToggleSync() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            notify("Synapse", "Synapse daemon is offline.", urgency = "critical")
            println("Error: Synapse daemon is offline.")
            return
        }

        val (baseUrl, apiKey) = conn
        val devices = fetchRemoteDevices(baseUrl, apiKey)
        if (devices == null) {
            notify("Synapse", "Could not read the device list.", urgency = "critical")
            println("Error: Could not read the device list from the daemon.")
            return
        }
        val allPaused = devices.isNotEmpty() && devices.all { it.paused }
        setAllDevicesPaused(baseUrl, apiKey, pause = !allPaused, devices = devices)
    }

    /**
     * The engine pauses and resumes per device (`?device=<id>` is required), so this walks the
     * configured remote devices and sends one request for each device not yet in the target state.
     */
    private fun setAllDevicesPaused(
        baseUrl: String,
        apiKey: String,
        pause: Boolean,
        devices: List<DeviceEntry>? = fetchRemoteDevices(baseUrl, apiKey)
    ) {
        val verb = if (pause) "pause" else "resume"
        if (devices == null) {
            notify("Synapse", "Failed to $verb: could not read the device list.", urgency = "critical")
            println("Error: Failed to $verb synchronization (could not read the device list).")
            return
        }
        if (devices.isEmpty()) {
            notify("Synapse", "No devices to $verb.")
            println("Synapse: No devices to $verb.")
            return
        }

        val targets = devices.filter { it.paused != pause }
        val failed = targets.filter { device ->
            val res = sendHttpRequest("POST", "$baseUrl/rest/system/$verb?device=${urlEncode(device.id)}", apiKey)
            res.code !in 200..299
        }

        if (failed.isEmpty()) {
            val msg = if (pause) "All devices paused." else "All devices resumed."
            notify("Synapse", msg)
            println("Synapse: $msg")
        } else {
            val msg = "Failed to $verb ${failed.size} of ${targets.size} device(s)."
            notify("Synapse", msg, urgency = "critical")
            println("Error: $msg")
        }
    }

    private fun handleRescan(folderId: String?) {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            notify("Synapse", "Synapse daemon is offline.", urgency = "critical")
            println("Error: Synapse daemon is offline.")
            return
        }

        val (baseUrl, apiKey) = conn
        // The engine requires a folder per scan request, so "all folders" means one request each.
        val folderIds = if (folderId != null) {
            listOf(folderId)
        } else {
            fetchFolderIds(baseUrl, apiKey) ?: run {
                notify("Synapse", "Rescan failed: could not read the folder list.", urgency = "critical")
                println("Error: Rescan failed (could not read the folder list).")
                return
            }
        }
        if (folderIds.isEmpty()) {
            notify("Synapse", "No folders to rescan.")
            println("Synapse: No folders to rescan.")
            return
        }

        val failed = folderIds.filter { id ->
            val res = sendHttpRequest("POST", "$baseUrl/rest/db/scan?folder=${urlEncode(id)}", apiKey)
            res.code !in 200..299
        }

        if (failed.isEmpty()) {
            val msg = if (folderId != null) "Rescan triggered for folder '$folderId'." else "Rescan triggered for all folders."
            notify("Synapse", msg)
            println("Synapse: $msg")
        } else {
            val msg = if (folderId != null) {
                "Rescan failed for folder '$folderId'."
            } else {
                "Rescan failed for ${failed.size} of ${folderIds.size} folder(s): ${failed.joinToString()}"
            }
            notify("Synapse", msg, urgency = "critical")
            println("Error: $msg")
        }
    }

    private fun handleStatus() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            println("Synapse Status: Offline")
            println("Daemon: Not running (no active Synapse instance responding on port 8384)")
            return
        }

        val (baseUrl, apiKey) = conn
        val connRes = sendHttpRequest("GET", "$baseUrl/rest/system/connections", apiKey)

        println("Synapse Status: Active")
        println("Daemon Address: $baseUrl")
        println("HTTP Ping: OK")

        println("Connected Peers: ${countConnectedPeers(connRes.body)}")
        val devices = fetchRemoteDevices(baseUrl, apiKey)
        println("Paused Peers: ${devices?.count { it.paused }?.toString() ?: "unknown"}")
    }

    private fun handleWaybar() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            println("""{"text":"󰅚 Offline","alt":"error","tooltip":"Synapse: Daemon is offline","class":"offline"}""")
            return
        }

        val (baseUrl, apiKey) = conn
        val connRes = sendHttpRequest("GET", "$baseUrl/rest/system/connections", apiKey)

        val connectedCount = countConnectedPeers(connRes.body)
        val devices = fetchRemoteDevices(baseUrl, apiKey).orEmpty()
        val isPaused = devices.isNotEmpty() && devices.all { it.paused }

        val (text, alt, cls, tooltip) = when {
            isPaused -> {
                Quad("󰏤 Paused", "paused", "paused", "Synapse: Synchronization is paused\nConnected peers: $connectedCount")
            }
            connectedCount > 0 -> {
                Quad("󰄬 Synced", "idle", "synced", "Synapse: Active and connected\nConnected peers: $connectedCount")
            }
            else -> {
                Quad("󰄬 Idle", "idle", "idle", "Synapse: Daemon running (waiting for peers)")
            }
        }

        println("""{"text":"$text","alt":"$alt","tooltip":"$tooltip","class":"$cls"}""")
    }

    private fun handleSetupHyprland() {
        val userHome = System.getProperty("user.home") ?: return
        val hyprDir = File(userHome, ".config/hypr")
        if (!hyprDir.exists()) {
            hyprDir.mkdirs()
        }

        val targetConf = File(hyprDir, "synapse.conf")
        val configContent = """
            # ==============================================================================
            # Synapse Hyprland Window Rules & Keybindings
            # Generated by Synapse
            # ==============================================================================

            # --- Window Rules (Scratchpad / Special Workspace) ---
            windowrulev2 = float, class:^(Synapse|com-arcadelabs-synapse-MainKt)$
            windowrulev2 = size 1050 720, class:^(Synapse|com-arcadelabs-synapse-MainKt)$
            windowrulev2 = center, class:^(Synapse|com-arcadelabs-synapse-MainKt)$
            windowrulev2 = workspace special:synapse silent, class:^(Synapse|com-arcadelabs-synapse-MainKt)$
            windowrulev2 = opacity 0.95 0.90, class:^(Synapse|com-arcadelabs-synapse-MainKt)$
            windowrulev2 = rounding 14, class:^(Synapse|com-arcadelabs-synapse-MainKt)$

            # --- Quick Actions & Hotkeys ---
            # Toggle Synapse scratchpad overlay with Super + S
            bind = ${'$'}mainMod, S, exec, synapse --toggle

            # Sync control hotkeys
            bind = ${'$'}mainMod SHIFT, P, exec, synapse --pause
            bind = ${'$'}mainMod SHIFT, R, exec, synapse --resume
            bind = ${'$'}mainMod SHIFT, S, exec, synapse --rescan
        """.trimIndent()

        targetConf.writeText(configContent + "\n")
        println("[Synapse] Created Hyprland configuration at: ${targetConf.absolutePath}")

        // Check if hyprland.conf exists and include our file
        val mainConf = File(hyprDir, "hyprland.conf")
        if (mainConf.exists()) {
            val content = mainConf.readText()
            if (!content.contains("synapse.conf")) {
                mainConf.appendText("\n# Synapse Integration\nsource = ~/.config/hypr/synapse.conf\n")
                println("[Synapse] Appended 'source = ~/.config/hypr/synapse.conf' to ${mainConf.absolutePath}")
            } else {
                println("[Synapse] 'synapse.conf' is already referenced in ${mainConf.absolutePath}")
            }
        }

        // Check if Caelestia / Hyprlua user config exists
        val caelestiaConf = File(userHome, ".config/caelestia/hypr-user.lua")
        if (caelestiaConf.exists()) {
            val luaContent = caelestiaConf.readText()
            if (!luaContent.contains("synapse")) {
                val luaSnippet = """

                    -- Synapse Scratchpad and Hotkeys
                    hl.bind("SUPER, S", hl.dsp.exec_cmd("synapse --toggle"))
                    hl.bind("SUPER + SHIFT, P", hl.dsp.exec_cmd("synapse --pause"))
                    hl.bind("SUPER + SHIFT, R", hl.dsp.exec_cmd("synapse --resume"))
                    hl.bind("SUPER + SHIFT, S", hl.dsp.exec_cmd("synapse --rescan"))

                    -- Synapse Window Rules
                    hl.window_rule({
                        match = { class = "Synapse" },
                        float = true,
                        size = "(monitor_w*0.6) (monitor_h*0.7)",
                        center = true,
                        workspace = "special:synapse",
                    })
                """.trimIndent()
                caelestiaConf.appendText("\n$luaSnippet\n")
                println("[Synapse] Appended Lua bindings to ${caelestiaConf.absolutePath}")
            }
        }

        notify("Synapse", "Hyprland configuration ready in ~/.config/hypr/synapse.conf")
        println("Hyprland setup complete! Reload your Hyprland configuration (hyprctl reload) to apply.")
    }

    private fun launchAppInBackground() {
        try {
            val candidates = listOf("synapse", "/usr/bin/synapse", "/usr/local/bin/synapse")
            for (cmd in candidates) {
                if (isCommandAvailable(cmd) || File(cmd).canExecute()) {
                    ProcessBuilder(cmd).start()
                    return
                }
            }

            // Fallback: spawn current java runtime with current jar/classpath
            val javaCmd = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
            val classPath = System.getProperty("java.class.path")
            ProcessBuilder(javaCmd, "-cp", classPath, "com.arcadelabs.synapse.MainKt").start()
        } catch (e: Exception) {
            println("[Synapse] Error launching app in background: ${e.message}")
        }
    }

    private fun notify(title: String, message: String, urgency: String = "normal") {
        if (!isCommandAvailable("notify-send")) return
        try {
            ProcessBuilder("notify-send", "-a", "Synapse", "-i", "synapse", "-u", urgency, title, message)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        } catch (_: Exception) {}
    }

    private fun isCommandAvailable(command: String): Boolean {
        return try {
            val p = ProcessBuilder("which", command).start()
            p.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun runShellCommand(vararg command: String): String {
        return try {
            val process = ProcessBuilder(*command).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            ""
        }
    }

    private fun resolveDaemonConnection(): Pair<String, String>? {
        val userHome = System.getProperty("user.home") ?: ""
        val localAppData = System.getenv("LOCALAPPDATA") ?: userHome
        val synapseDir = File(localAppData, "Synapse")

        val candidateConfigs = listOf(
            File(userHome, ".config/syncthing/config.xml"),
            File(synapseDir, "syncthing-home${File.separator}config.xml"),
            File(localAppData, "Syncthing${File.separator}config.xml"),
            File(System.getenv("APPDATA") ?: "", "Syncthing${File.separator}config.xml"),
            File(userHome, ".local/share/syncthing/config.xml")
        )

        for (configFile in candidateConfigs) {
            if (!configFile.exists()) continue
            val parsed = parseApiKeyAndAddress(configFile) ?: continue
            val (apiKey, baseUrl) = parsed
            if (pingApi(baseUrl, apiKey)) {
                return Pair(baseUrl, apiKey)
            }
        }

        // Fallback: Test managed defaults (check default 8384 and 9090)
        for (candidateUrl in listOf(DesktopDaemonManager.MANAGED_BASE_URL, "http://127.0.0.1:9090")) {
            if (pingApi(candidateUrl, DesktopDaemonManager.MANAGED_API_KEY)) {
                return Pair(candidateUrl, DesktopDaemonManager.MANAGED_API_KEY)
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
        } catch (_: Exception) {
            null
        }
    }

    private fun pingApi(baseUrl: String, apiKey: String): Boolean {
        return pingUrl("$baseUrl/api/v1/system/status", apiKey) ||
               pingUrl("$baseUrl/rest/system/ping", apiKey)
    }

    private fun pingUrl(urlStr: String, apiKey: String): Boolean {
        return try {
            val url = URI.create(urlStr).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun sendHttpRequest(method: String, urlStr: String, apiKey: String): HttpResponseData {
        return try {
            val url = URI.create(urlStr).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("X-API-Key", apiKey)
            if (method == "POST" || method == "PUT" || method == "PATCH") {
                conn.doOutput = true
                conn.outputStream.use { os ->
                    OutputStreamWriter(os).apply {
                        write("")
                        flush()
                    }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResponseData(code, body)
        } catch (e: Exception) {
            HttpResponseData(-1, e.message ?: "Connection error")
        }
    }

    /** Synapse Core sends `"is_connected"`; the legacy engine sends `"connected"`. */
    private fun countConnectedPeers(connectionsJson: String): Int =
        "\"(?:is_)?connected\"\\s*:\\s*true".toRegex().findAll(connectionsJson).count()

    /** Configured devices other than this one, with their paused flag, from GET /rest/system/config. */
    private fun fetchRemoteDevices(baseUrl: String, apiKey: String): List<DeviceEntry>? {
        val configRes = sendHttpRequest("GET", "$baseUrl/rest/system/config", apiKey)
        if (configRes.code !in 200..299) return null
        val statusRes = sendHttpRequest("GET", "$baseUrl/rest/system/status", apiKey)
        val myId = if (statusRes.code in 200..299) {
            "\"myID\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(statusRes.body)?.groupValues?.get(1)
        } else {
            null
        }
        val idRegex = "\"deviceID\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val pausedRegex = "\"paused\"\\s*:\\s*true".toRegex()
        return topLevelArrayObjects(configRes.body, "devices").mapNotNull { obj ->
            val id = idRegex.find(obj)?.groupValues?.get(1) ?: return@mapNotNull null
            if (myId != null && id.equals(myId, ignoreCase = true)) return@mapNotNull null
            DeviceEntry(id, pausedRegex.containsMatchIn(obj))
        }
    }

    /** Folder IDs from GET /rest/system/config. */
    private fun fetchFolderIds(baseUrl: String, apiKey: String): List<String>? {
        val configRes = sendHttpRequest("GET", "$baseUrl/rest/system/config", apiKey)
        if (configRes.code !in 200..299) return null
        val idRegex = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return topLevelArrayObjects(configRes.body, "folders").mapNotNull { obj ->
            idRegex.find(obj)?.groupValues?.get(1)
        }
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    /**
     * Raw JSON text of each object in the root object's [key] array. Only the root level is
     * matched, so nested arrays with the same name (e.g. folders[].devices) are skipped.
     * A small scanner because no JSON library is on this module's classpath.
     */
    private fun topLevelArrayObjects(json: String, key: String): List<String> {
        var depth = 0
        var i = 0
        while (i < json.length) {
            when (json[i]) {
                '"' -> {
                    val end = stringEnd(json, i)
                    if (depth == 1 && json.substring(i + 1, end) == key) {
                        var j = end + 1
                        while (j < json.length && json[j].isWhitespace()) j++
                        if (j < json.length && json[j] == ':') {
                            j++
                            while (j < json.length && json[j].isWhitespace()) j++
                            return if (j < json.length && json[j] == '[') arrayObjects(json, j) else emptyList()
                        }
                    }
                    i = end
                }
                '{', '[' -> depth++
                '}', ']' -> depth--
            }
            i++
        }
        return emptyList()
    }

    /** Objects directly inside the array that opens at [start]. */
    private fun arrayObjects(json: String, start: Int): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var objStart = -1
        var i = start + 1
        while (i < json.length) {
            when (json[i]) {
                '"' -> i = stringEnd(json, i)
                '{', '[' -> {
                    if (depth == 0 && json[i] == '{') objStart = i
                    depth++
                }
                '}', ']' -> {
                    if (depth == 0) return objects // closing bracket of the array itself
                    depth--
                    if (depth == 0 && json[i] == '}' && objStart >= 0) {
                        objects.add(json.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
            }
            i++
        }
        return objects
    }

    /** Index of the closing quote of the string literal that opens at [start]. */
    private fun stringEnd(json: String, start: Int): Int {
        var i = start + 1
        while (i < json.length) {
            when (json[i]) {
                '\\' -> i++
                '"' -> return i
            }
            i++
        }
        return json.length // unterminated: treat the rest as the string
    }

    private data class DeviceEntry(val id: String, val paused: Boolean)
    private data class HttpResponseData(val code: Int, val body: String)
    private data class Quad(val first: String, val second: String, val third: String, val fourth: String)
}
