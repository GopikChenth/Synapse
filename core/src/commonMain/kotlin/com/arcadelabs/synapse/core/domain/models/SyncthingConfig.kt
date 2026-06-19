package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SyncthingConfig(
    val version: Int = 0,
    val folders: List<Folder> = emptyList(),
    val devices: List<Device> = emptyList()
)
