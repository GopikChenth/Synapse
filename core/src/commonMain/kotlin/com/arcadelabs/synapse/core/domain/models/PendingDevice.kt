package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PendingDevice(
    @SerialName("time") val time: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("address") val address: String = ""
)
