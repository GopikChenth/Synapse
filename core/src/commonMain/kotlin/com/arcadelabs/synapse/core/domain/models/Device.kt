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

fun String.parseSyncthingQr(): String {
    val cleaned = this.trim()
    if (cleaned.startsWith("syncthing://", ignoreCase = true)) {
        val uriPart = cleaned.substring("syncthing://".length)
        return uriPart.substringBefore("?").substringBefore("/").normalizeDeviceId()
    }
    return cleaned.normalizeDeviceId()
}

