@file:OptIn(ExperimentalSerializationApi::class)

package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class SystemVersion(
    @SerialName("currentVersion") @JsonNames("version") val currentVersion: String = "",
    @SerialName("longVersion") val longVersion: String = "",
    @SerialName("latestVersion") val latestVersion: String = "",
    @SerialName("upgradeAvailable") val upgradeAvailable: Boolean = false,
    @SerialName("arch") val arch: String = "",
    @SerialName("os") val os: String = ""
)

/**
 * True when the connected engine is Synapse Core rather than the legacy engine fallback.
 * Some settings (engine options, Web GUI, renaming this device) only exist on the legacy engine.
 */
fun SystemVersion.isSynapseCore(): Boolean =
    longVersion.contains("Native Rust", ignoreCase = true) ||
        longVersion.contains("synapse", ignoreCase = true) ||
        currentVersion.contains("synapse", ignoreCase = true)
