package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val alloc: Long = 0,
    val sys: Long = 0,
    val myID: String = "",
    val uptime: Long = 0,
    val guiAddressUsed: String = ""
)
