package com.arcadelabs.synapse.core.network

import android.content.Context
import java.io.File

actual fun getApiKey(context: Any?): String {
    val ctx = context as? Context ?: throw IllegalArgumentException("Android Context required")
    val configFile = File(ctx.filesDir, "config.xml")
    if (!configFile.exists()) return ""
    val content = configFile.readText()
    val regex = "<apikey>([^<]+)</apikey>".toRegex()
    val match = regex.find(content)
    return match?.groupValues?.get(1) ?: ""
}
