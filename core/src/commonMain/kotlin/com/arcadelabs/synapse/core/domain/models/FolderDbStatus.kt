package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class FolderDbStatus(
    val globalBytes: Long = 0,
    val localBytes: Long = 0,
    val needBytes: Long = 0,
    val state: String = "idle",
    val inSyncBytes: Long = 0
)
