package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceConnection(
    val address: String = "",
    val at: String = "",
    val clientVersion: String = "",
    val connected: Boolean = false,
    val type: String = "",
    val inBytesTotal: Long = 0,
    val outBytesTotal: Long = 0
)

@Serializable
data class TotalConnection(
    val inBytesTotal: Long = 0,
    val outBytesTotal: Long = 0
)

@Serializable
data class ConnectionsResponse(
    val connections: Map<String, DeviceConnection> = emptyMap(),
    val total: TotalConnection = TotalConnection()
)
