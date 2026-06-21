package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceConnection(
    @SerialName("address") val address: String = "",
    /**
     * ISO-8601 formatted timestamp indicating when the connection status was last updated.
     */
    @SerialName("at") val at: String = "",
    @SerialName("clientVersion") val clientVersion: String = "",
    @SerialName("connected") val connected: Boolean = false,
    @SerialName("type") val type: String = "",
    @SerialName("inBytesTotal") val inBytesTotal: Long = 0,
    @SerialName("outBytesTotal") val outBytesTotal: Long = 0
)

@Serializable
data class TotalConnection(
    @SerialName("inBytesTotal") val inBytesTotal: Long = 0,
    @SerialName("outBytesTotal") val outBytesTotal: Long = 0
)

@Serializable
data class ConnectionsResponse(
    @SerialName("connections") val connections: Map<String, DeviceConnection> = emptyMap(),
    @SerialName("total") val total: TotalConnection = TotalConnection()
)
