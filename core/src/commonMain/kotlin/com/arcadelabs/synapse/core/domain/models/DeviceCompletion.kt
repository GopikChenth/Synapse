package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceCompletion(
    val completion: Double = 0.0,
    val globalBytes: Long = 0,
    val needBytes: Long = 0
)
