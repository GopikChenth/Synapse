package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceCompletion(
    @SerialName("completion") val completion: Double = 0.0,
    @SerialName("globalBytes") val globalBytes: Long = 0,
    @SerialName("needBytes") val needBytes: Long = 0,
    @SerialName("device") val device: String = "",
    @SerialName("folder") val folder: String = "",
    @SerialName("remoteState") val remoteState: String = ""
)
