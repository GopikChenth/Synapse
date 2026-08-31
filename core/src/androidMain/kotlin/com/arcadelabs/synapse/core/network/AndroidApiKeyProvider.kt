package com.arcadelabs.synapse.core.network

import android.content.Context
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val API_KEY_REGEX = "(?i)<apikey(?:\\s+[^>]*)?>\\s*([^<\\s]+)\\s*</apikey>".toRegex()

class AndroidApiKeyProvider(
    private val context: Context,
    private val preferencesHelper: PreferencesHelper
) : ApiKeyProvider {
    override suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        val customPath = preferencesHelper.configFilePath.trim()
        val configFile = if (customPath.isNotEmpty()) {
            File(customPath)
        } else {
            File(context.filesDir, "config.xml")
        }

        repeat(10) {
            if (configFile.exists()) {
                val content = try { configFile.readText() } catch (_: Exception) { "" }
                val match = API_KEY_REGEX.find(content)
                val key = match?.groupValues?.get(1)?.trim()
                if (!key.isNullOrEmpty()) {
                    return@withContext key
                }
            }
            delay(500)
        }
        null
    }
}
