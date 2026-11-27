@file:OptIn(ExperimentalSerializationApi::class)

package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Folder database status. The legacy engine sends camelCase keys; Synapse Core
 * (FolderStatusMetrics) sends snake_case keys, accepted via [JsonNames].
 */
@Serializable
data class FolderDbStatus(
    @SerialName("globalBytes") @JsonNames("global_bytes") val globalBytes: Long = 0,
    @SerialName("globalFiles") @JsonNames("global_files") val globalFiles: Long = 0,
    @SerialName("globalDirectories") @JsonNames("global_directories") val globalDirectories: Long = 0,
    @SerialName("localBytes") @JsonNames("local_bytes") val localBytes: Long = 0,
    @SerialName("localFiles") @JsonNames("local_files") val localFiles: Long = 0,
    @SerialName("localDirectories") @JsonNames("local_directories") val localDirectories: Long = 0,
    @SerialName("needBytes") @JsonNames("need_bytes") val needBytes: Long = 0,
    @SerialName("state") val state: String = "idle",
    @SerialName("inSyncBytes") @JsonNames("in_sync_bytes") val inSyncBytes: Long = 0,
    @SerialName("needDeletes") @JsonNames("need_deletes") val needDeletes: Long = 0,
    @SerialName("sequence") val sequence: Long = 0,
    @SerialName("stateChanged") @JsonNames("state_changed") val stateChanged: String = "",
    @SerialName("version") val version: Long = 0,
    @SerialName("pullErrors") @JsonNames("pull_errors") val pullErrors: Long = 0
)
