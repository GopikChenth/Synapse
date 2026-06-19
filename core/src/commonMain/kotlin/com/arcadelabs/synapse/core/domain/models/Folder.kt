package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
    val id: String,
    val label: String = "",
    val path: String = "",
    val type: String = "sendreceive",
    val paused: Boolean = false
)
