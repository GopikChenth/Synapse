package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val API_KEY_REGEX = "(?i)<apikey(?:\\s+[^>]*)?>\\s*([^<\\s]+)\\s*</apikey>".toRegex()
private const val MANAGED_API_KEY = "synapse-managed-api-key-2025"

class JvmApiKeyProvider(
    private val preferencesHelper: PreferencesHelper
) : ApiKeyProvider {
    override suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        val customPath = preferencesHelper.configFilePath.trim()
        if (customPath.isNotEmpty()) {
            val file = File(customPath)
            if (file.exists()) {
                val key = parseApiKey(file)
                if (!key.isNullOrEmpty()) return@withContext key
            }
        }

        val userHome = System.getProperty("user.home") ?: "."
        val localAppData = System.getenv("LOCALAPPDATA") ?: System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share"
        val appData = System.getenv("APPDATA") ?: "$userHome/.config"

        val candidateConfigs = listOf(
            File(localAppData, "Synapse/syncthing-home/config.xml"),
            File(userHome, "Synapse/syncthing-home/config.xml"),
            File(localAppData, "Syncthing/config.xml"),
            File(appData, "Syncthing/config.xml"),
            File(userHome, ".config/syncthing/config.xml"),
            File(userHome, "Library/Application Support/Syncthing/config.xml")
        )

        for (configFile in candidateConfigs) {
            if (configFile.exists()) {
                val key = parseApiKey(configFile)
                if (!key.isNullOrEmpty()) {
                    return@withContext key
                }
            }
        }

        // Default fallback for Synapse-managed daemon
        MANAGED_API_KEY
    }

    private fun parseApiKey(configFile: File): String? {
        return try {
            val content = configFile.readText()
            val match = API_KEY_REGEX.find(content)
            match?.groupValues?.get(1)?.trim()
        } catch (_: Exception) {
            null
        }
    }
}
