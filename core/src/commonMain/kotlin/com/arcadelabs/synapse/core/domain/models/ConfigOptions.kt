package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models the `options` section of the Syncthing configuration.
 * Corresponds to the JSON returned by `GET /rest/config/options`.
 */
@Serializable
data class ConfigOptions(
    @SerialName("listenAddresses") val listenAddresses: List<String> = listOf("default"),
    @SerialName("globalAnnounceServers") val globalAnnounceServers: List<String> = listOf("default"),
    @SerialName("globalAnnounceEnabled") val globalAnnounceEnabled: Boolean = true,
    @SerialName("localAnnounceEnabled") val localAnnounceEnabled: Boolean = true,
    @SerialName("localAnnouncePort") val localAnnouncePort: Int = 21027,
    @SerialName("maxSendKbps") val maxSendKbps: Int = 0,
    @SerialName("maxRecvKbps") val maxRecvKbps: Int = 0,
    @SerialName("reconnectionIntervalS") val reconnectionIntervalS: Int = 60,
    @SerialName("relaysEnabled") val relaysEnabled: Boolean = true,
    @SerialName("startBrowser") val startBrowser: Boolean = true,
    @SerialName("natEnabled") val natEnabled: Boolean = true,
    @SerialName("natLeaseMinutes") val natLeaseMinutes: Int = 60,
    @SerialName("natRenewalMinutes") val natRenewalMinutes: Int = 30,
    @SerialName("natTimeoutSeconds") val natTimeoutSeconds: Int = 10,
    @SerialName("urAccepted") val urAccepted: Int = 0,
    @SerialName("urSeen") val urSeen: Int = 0,
    @SerialName("urUniqueID") val urUniqueID: String = "",
    @SerialName("autoUpgradeIntervalH") val autoUpgradeIntervalH: Int = 12,
    @SerialName("limitBandwidthInLan") val limitBandwidthInLan: Boolean = false,
    @SerialName("setLowPriority") val setLowPriority: Boolean = true,
    @SerialName("crashReportingEnabled") val crashReportingEnabled: Boolean = true,
    @SerialName("maxFolderConcurrency") val maxFolderConcurrency: Int = 0
)
