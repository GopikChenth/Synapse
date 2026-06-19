package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val deviceID: String,
    val name: String = "",
    val addresses: List<String> = emptyList(),
    val paused: Boolean = false
)
