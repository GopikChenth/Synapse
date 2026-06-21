package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FolderDbStatus(
    @SerialName("globalBytes") val globalBytes: Long = 0,
    @SerialName("localBytes") val localBytes: Long = 0,
    @SerialName("needBytes") val needBytes: Long = 0,
    @SerialName("state") val state: String = "idle",
    @SerialName("inSyncBytes") val inSyncBytes: Long = 0,
    @SerialName("needDeletes") val needDeletes: Long = 0,
    @SerialName("sequence") val sequence: Long = 0,
    @SerialName("stateChanged") val stateChanged: String = "",
    @SerialName("version") val version: Long = 0
)
