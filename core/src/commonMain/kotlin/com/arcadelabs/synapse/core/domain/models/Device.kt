package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    @SerialName("deviceID") val deviceID: String,
    @SerialName("name") val name: String = "",
    @SerialName("addresses") val addresses: List<String> = emptyList(),
    @SerialName("paused") val paused: Boolean = false,
    @SerialName("introducer") val introducer: Boolean = false,
    @SerialName("autoAcceptFolders") val autoAcceptFolders: Boolean = false,
    @SerialName("untrusted") val untrusted: Boolean = false
)

fun String.normalizeDeviceId(): String {
    return this.replace("-", "").replace(" ", "").uppercase()
}

/**
 * Formats a device ID for display in the canonical grouped form (`XXXXXXX-XXXXXXX-…`).
 * Normalizes first, so an ID that already contains dashes is not grouped twice.
 * Users compare these visually when pairing, so every screen must show the same form.
 */
fun String.formatDeviceId(): String = normalizeDeviceId().chunked(7).joinToString("-")

fun String.parseDeviceQr(): String {
    val cleaned = this.trim()
    if (cleaned.startsWith("syncthing://", ignoreCase = true)) {
        val uriPart = cleaned.substring("syncthing://".length)
        return uriPart.substringBefore("?").substringBefore("/").normalizeDeviceId()
    }
    return cleaned.normalizeDeviceId()
}

private fun decodeUrlComponent(input: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '%') {
            if (i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3)
                try {
                    sb.append(hex.toInt(16).toChar())
                } catch (_: Exception) {
                    sb.append(c)
                    sb.append(hex)
                }
                i += 3
            } else {
                sb.append(c)
                i++
            }
        } else if (c == '+') {
            sb.append(' ')
            i++
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

fun String.parseDeviceQrDetails(): Pair<String, String> {
    val cleaned = this.trim()
    if (cleaned.startsWith("syncthing://", ignoreCase = true)) {
        val uriPart = cleaned.substring("syncthing://".length)
        val deviceId = uriPart.substringBefore("?").substringBefore("/").normalizeDeviceId()
        
        val query = uriPart.substringAfter("?", "")
        val nameParam = if (query.isNotEmpty()) {
            query.split("&")
                .firstOrNull { it.startsWith("name=", ignoreCase = true) || it.startsWith("device=", ignoreCase = true) }
                ?.substringAfter("=")
                ?: ""
        } else {
            ""
        }
        
        val decodedName = try {
            decodeUrlComponent(nameParam)
        } catch (_: Exception) {
            nameParam.replace("+", " ").replace("%20", " ")
        }
        
        return Pair(deviceId, decodedName)
    }
    return Pair(cleaned.normalizeDeviceId(), "")
}

