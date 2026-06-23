package com.arcadelabs.synapse.core.network

import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val API_KEY_REGEX = "(?i)<apikey(?:\\s+[^>]*)?>\\s*([^<\\s]+)\\s*</apikey>".toRegex()

class JvmApiKeyProvider(
    private val preferencesHelper: PreferencesHelper
) : ApiKeyProvider {
    override suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        val customPath = preferencesHelper.configFilePath.trim()
        val configFile = if (customPath.isNotEmpty()) {
            File(customPath)
        } else {
            val localAppData = System.getenv("LOCALAPPDATA") ?: return@withContext null
            // Primary: Synapse-managed Syncthing home (set by DesktopDaemonManager)
            val synapseConfig = File(localAppData, "Synapse/syncthing-home/config.xml")
            if (synapseConfig.exists()) synapseConfig
            // Fallback: standalone Syncthing installation
            else File(localAppData, "Syncthing/config.xml")
        }
        if (!configFile.exists()) return@withContext null
        val content = configFile.readText()
        val match = API_KEY_REGEX.find(content)
        match?.groupValues?.get(1)?.trim()
    }
}
