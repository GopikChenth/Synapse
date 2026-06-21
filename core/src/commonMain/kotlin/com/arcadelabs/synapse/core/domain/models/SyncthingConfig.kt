package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncthingConfig(
    @SerialName("version") val version: Int = 0,
    @SerialName("folders") val folders: List<Folder> = emptyList(),
    @SerialName("devices") val devices: List<Device> = emptyList()
)
