package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SystemVersion(
    val version: String = "",
    val arch: String = "",
    val os: String = ""
)
