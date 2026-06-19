package com.arcadelabs.synapse.core.network

import java.io.File

actual fun getApiKey(context: Any?): String {
    val localAppData = System.getenv("LOCALAPPDATA") ?: return ""
    val configFile = File(localAppData, "Syncthing/config.xml")
    if (!configFile.exists()) return ""
    val content = configFile.readText()
    val regex = "<apikey>([^<]+)</apikey>".toRegex()
    val match = regex.find(content)
    return match?.groupValues?.get(1) ?: ""
}
