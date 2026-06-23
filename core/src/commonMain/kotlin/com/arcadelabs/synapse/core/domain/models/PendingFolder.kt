package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PendingFolder(
    @SerialName("label") val label: String = "",
    @SerialName("time") val time: String = "",
    @SerialName("receiveEncrypted") val receiveEncrypted: Boolean = false,
    @SerialName("remoteType") val remoteType: String = ""
)

@Serializable
data class PendingFolderOffer(
    @SerialName("offeredBy") val offeredBy: Map<String, PendingFolder>
)
