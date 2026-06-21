package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemVersion(
    @SerialName("currentVersion") val currentVersion: String = "",
    @SerialName("longVersion") val longVersion: String = "",
    @SerialName("latestVersion") val latestVersion: String = "",
    @SerialName("upgradeAvailable") val upgradeAvailable: Boolean = false,
    @SerialName("arch") val arch: String = "",
    @SerialName("os") val os: String = ""
)
