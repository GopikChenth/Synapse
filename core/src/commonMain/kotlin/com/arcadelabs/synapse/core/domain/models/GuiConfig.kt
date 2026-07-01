package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models the `gui` section of the Syncthing configuration.
 * Corresponds to the JSON returned by `GET /rest/config/gui`.
 */
@Serializable
data class GuiConfig(
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("address") val address: String = "127.0.0.1:8384",
    @SerialName("user") val user: String = "",
    @SerialName("password") val password: String = "",
    @SerialName("useTLS") val useTLS: Boolean = false,
    @SerialName("apiKey") val apiKey: String = "",
    @SerialName("theme") val theme: String = "default"
)
