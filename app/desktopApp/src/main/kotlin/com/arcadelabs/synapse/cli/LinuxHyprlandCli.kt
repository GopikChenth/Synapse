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
              --pause, -p              Pause all synchronization and peer connections
              --resume, -r             Resume all synchronization and peer connections
              --toggle-sync            Toggle between pause and resume states
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
            notify("Synapse", "Syncthing daemon is offline.", urgency = "critical")
            println("Error: Syncthing daemon is offline.")
            return
        }

        val (baseUrl, apiKey) = conn
        val res = sendHttpRequest("POST", "$baseUrl/rest/system/pause", apiKey)
        if (res.code in 200..299) {
            notify("Synapse", "All synchronization paused.")
            println("Synapse: All synchronization paused.")
        } else {
            notify("Synapse", "Failed to pause: HTTP ${res.code}", urgency = "critical")
            println("Error: Failed to pause synchronization (HTTP ${res.code})")
        }
    }

    private fun handleResume() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            notify("Synapse", "Syncthing daemon is offline.", urgency = "critical")
            println("Error: Syncthing daemon is offline.")
            return
        }

        val (baseUrl, apiKey) = conn
        val res = sendHttpRequest("POST", "$baseUrl/rest/system/resume", apiKey)
        if (res.code in 200..299) {
            notify("Synapse", "Synchronization resumed.")
            println("Synapse: Synchronization resumed.")
        } else {
            notify("Synapse", "Failed to resume: HTTP ${res.code}", urgency = "critical")
            println("Error: Failed to resume synchronization (HTTP ${res.code})")
        }
    }

    private fun handleToggleSync() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            notify("Synapse", "Syncthing daemon is offline.", urgency = "critical")
            println("Error: Syncthing daemon is offline.")
            return
        }

        val (baseUrl, apiKey) = conn
        val connectionsRes = sendHttpRequest("GET", "$baseUrl/rest/system/connections", apiKey)

        // Check if connections indicate paused
        val isPaused = connectionsRes.body.contains("\"paused\": true") ||
                connectionsRes.body.contains("\"paused\":true")

        if (isPaused) {
            handleResume()
        } else {
            handlePause()
        }
    }

    private fun handleRescan(folderId: String?) {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            notify("Synapse", "Syncthing daemon is offline.", urgency = "critical")
            println("Error: Syncthing daemon is offline.")
            return
        }

        val (baseUrl, apiKey) = conn
        val url = if (folderId != null) "$baseUrl/rest/db/scan?folder=$folderId" else "$baseUrl/rest/db/scan"
        val res = sendHttpRequest("POST", url, apiKey)

        if (res.code in 200..299) {
            val msg = if (folderId != null) "Rescan triggered for folder '$folderId'." else "Rescan triggered for all folders."
            notify("Synapse", msg)
            println("Synapse: $msg")
        } else {
            notify("Synapse", "Rescan failed: HTTP ${res.code}", urgency = "critical")
            println("Error: Rescan failed (HTTP ${res.code})")
        }
    }

    private fun handleStatus() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            println("Synapse Status: Offline")
            println("Daemon: Not running (no active Syncthing instance responding on port 8384)")
            return
        }

        val (baseUrl, apiKey) = conn
        val statusRes = sendHttpRequest("GET", "$baseUrl/rest/system/status", apiKey)
        val connRes = sendHttpRequest("GET", "$baseUrl/rest/system/connections", apiKey)

        println("Synapse Status: Active")
        println("Daemon Address: $baseUrl")
        println("HTTP Ping: OK")

        val connectedMatches = "\"connected\":\\s*true".toRegex().findAll(connRes.body).count()
        val pausedMatches = "\"paused\":\\s*true".toRegex().findAll(connRes.body).count()

        println("Connected Peers: $connectedMatches")
        println("Paused Peers: $pausedMatches")
    }

    private fun handleWaybar() {
        val conn = resolveDaemonConnection()
        if (conn == null) {
            println("""{"text":"󰅚 Offline","alt":"error","tooltip":"Synapse: Daemon is offline","class":"offline"}""")
            return
        }

        val (baseUrl, apiKey) = conn
        val connRes = sendHttpRequest("GET", "$baseUrl/rest/system/connections", apiKey)

        val connectedCount = "\"connected\":\\s*true".toRegex().findAll(connRes.body).count()
        val isPaused = connRes.body.contains("\"paused\": true") || connRes.body.contains("\"paused\":true")

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

        // Fallback: Test managed defaults
        if (pingApi(DesktopDaemonManager.MANAGED_BASE_URL, DesktopDaemonManager.MANAGED_API_KEY)) {
            return Pair(DesktopDaemonManager.MANAGED_BASE_URL, DesktopDaemonManager.MANAGED_API_KEY)
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
        return try {
            val url = URI.create("$baseUrl/rest/system/ping").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout = 1500
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.responseCode == 200
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

    private data class HttpResponseData(val code: Int, val body: String)
    private data class Quad(val first: String, val second: String, val third: String, val fourth: String)
}
