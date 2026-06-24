package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FolderDeviceReference(
    @SerialName("deviceID") val deviceID: String,
    @SerialName("encryptionPassword") val encryptionPassword: String = ""
)

@Serializable
data class FolderVersioning(
    @SerialName("type") val type: String = "none",
    @SerialName("params") val params: Map<String, String> = emptyMap()
)

@Serializable
data class Folder(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String = "",
    @SerialName("path") val path: String = "",
    @SerialName("type") val type: String = "sendreceive",
    @SerialName("paused") val paused: Boolean = false,
    @SerialName("rescanIntervalS") val rescanIntervalS: Int = 60,
    @SerialName("fsWatcherEnabled") val fsWatcherEnabled: Boolean = true,
    @SerialName("devices") val devices: List<FolderDeviceReference> = emptyList(),
    @SerialName("versioning") val versioning: FolderVersioning = FolderVersioning()
)
