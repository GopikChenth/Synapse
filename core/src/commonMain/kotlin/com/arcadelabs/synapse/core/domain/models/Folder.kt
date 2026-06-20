package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class FolderDeviceReference(
    val deviceID: String
)

@Serializable
data class FolderVersioning(
    val type: String = "none",
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class Folder(
    val id: String,
    val label: String = "",
    val path: String = "",
    val type: String = "sendreceive",
    val paused: Boolean = false,
    val rescanIntervalS: Long = 3600,
    val fsWatcherEnabled: Boolean = true,
    val devices: List<FolderDeviceReference> = emptyList(),
    val versioning: FolderVersioning = FolderVersioning()
)
