@file:OptIn(ExperimentalSerializationApi::class)

package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EngineConfig(
    @SerialName("version") val version: Int = 0,
    @SerialName("folders") val folders: List<Folder> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) @SerialName("devices") val devices: List<Device> = emptyList()
)
