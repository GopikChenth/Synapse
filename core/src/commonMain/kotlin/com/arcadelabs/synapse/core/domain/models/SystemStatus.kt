package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionServiceEntry(
    @SerialName("error") val error: String? = null,
    @SerialName("lanAddresses") val lanAddresses: List<String> = emptyList(),
    @SerialName("wanAddresses") val wanAddresses: List<String> = emptyList()
)

@Serializable
data class SystemStatus(
    @SerialName("alloc") val alloc: Long = 0,
    @SerialName("sys") val sys: Long = 0,
    @SerialName("myID") val myID: String = "",
    @SerialName("uptime") val uptime: Long = 0,
    @SerialName("guiAddressUsed") val guiAddressUsed: String = "",
    @SerialName("startTime") val startTime: String = "",
    @SerialName("cpuPercent") val cpuPercent: Double = 0.0,
    @SerialName("numCPU") val numCPU: Int = 0,
    @SerialName("numFolders") val numFolders: Int = 0,
    @SerialName("numDevices") val numDevices: Int = 0,
    @SerialName("numConnected") val numConnected: Int = 0,
    @SerialName("goroutines") val goroutines: Int = 0,
    @SerialName("discoveryErrors") val discoveryErrors: Map<String, String> = emptyMap(),
    @SerialName("listenAddresses") val listenAddresses: List<String> = emptyList(),
    @SerialName("connectionServiceStatus") val connectionServiceStatus: Map<String, ConnectionServiceEntry> = emptyMap(),
    @SerialName("relaysEnabled") val relaysEnabled: Boolean = false,
    @SerialName("tilde") val tilde: String = "",
    @SerialName("pathSeparator") val pathSeparator: String = ""
)
