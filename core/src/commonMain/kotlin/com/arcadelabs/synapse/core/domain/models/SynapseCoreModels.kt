package com.arcadelabs.synapse.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoreSystemStatus(
    @SerialName("device_id") val deviceId: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Long,
    @SerialName("peer_count") val peerCount: Int,
    @SerialName("sync_state") val syncState: String,
    @SerialName("version") val version: String,
    @SerialName("monitored_folders_count") val monitoredFoldersCount: Int,
    @SerialName("pending_conflicts_count") val pendingConflictsCount: Int,
    @SerialName("memory_footprint_bytes") val memoryFootprintBytes: Long
)

@Serializable
data class CoreFolder(
    @SerialName("folder_id") val folderId: String,
    @SerialName("local_path") val localPath: String,
    @SerialName("conflict_policy") val conflictPolicy: String = "THREE_WAY_AUTO",
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("files_count") val filesCount: Int = 0
)

@Serializable
data class CoreRegisterFolderRequest(
    @SerialName("folder_id") val folderId: String,
    @SerialName("local_path") val localPath: String,
    @SerialName("conflict_policy") val conflictPolicy: String = "THREE_WAY_AUTO"
)

@Serializable
data class CoreQuarantineConflict(
    @SerialName("conflict_id") val conflictId: String,
    @SerialName("folder_id") val folderId: String,
    @SerialName("relative_path") val relativePath: String,
    @SerialName("base_revision") val baseRevision: String? = null,
    @SerialName("local_revision") val localRevision: String,
    @SerialName("remote_revision") val remoteRevision: String,
    @SerialName("staged_payload_path") val stagedPayloadPath: String,
    @SerialName("status") val status: String = "PENDING",
    @SerialName("detected_at") val detectedAt: Long
)

@Serializable
enum class CoreHunkType {
    @SerialName("UNCHANGED") UNCHANGED,
    @SerialName("LOCAL_CHANGE") LOCAL_CHANGE,
    @SerialName("REMOTE_CHANGE") REMOTE_CHANGE,
    @SerialName("IDENTICAL_CHANGE") IDENTICAL_CHANGE,
    @SerialName("CONFLICT") CONFLICT
}

@Serializable
data class CoreDiffHunk(
    @SerialName("hunk_type") val hunkType: CoreHunkType,
    @SerialName("base_lines") val baseLines: List<String> = emptyList(),
    @SerialName("local_lines") val localLines: List<String> = emptyList(),
    @SerialName("remote_lines") val remoteLines: List<String> = emptyList()
)

@Serializable
data class CoreDiffResponse(
    @SerialName("conflict_id") val conflictId: String,
    @SerialName("folder_id") val folderId: String,
    @SerialName("relative_path") val relativePath: String,
    @SerialName("base") val base: String,
    @SerialName("local") val local: String,
    @SerialName("remote") val remote: String,
    @SerialName("hunks") val hunks: List<CoreDiffHunk> = emptyList()
)

@Serializable
enum class CoreResolveAction {
    @SerialName("ACCEPT_LOCAL") ACCEPT_LOCAL,
    @SerialName("ACCEPT_REMOTE") ACCEPT_REMOTE,
    @SerialName("CUSTOM_MERGE") CUSTOM_MERGE,
    @SerialName("KEEP_BOTH") KEEP_BOTH
}

@Serializable
data class CoreResolveRequest(
    @SerialName("action") val action: CoreResolveAction,
    @SerialName("merged_content") val mergedContent: String? = null
)
