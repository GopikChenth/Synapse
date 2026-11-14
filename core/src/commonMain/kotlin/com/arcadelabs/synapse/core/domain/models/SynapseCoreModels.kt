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
    @SerialName("sync_mode") val syncMode: String = "SendReceive",
    @SerialName("pull_order") val pullOrder: String = "Alphabetical",
    @SerialName("versioning_type") val versioningType: String = "None",
    @SerialName("versioning_keep_days") val versioningKeepDays: Long = 30,
    @SerialName("marker_name") val markerName: String = ".synapse-folder",
    @SerialName("versioning_command") val versioningCommand: String? = null,
    @SerialName("files_count") val filesCount: Int = 0
)

@Serializable
data class CoreRegisterFolderRequest(
    @SerialName("folder_id") val folderId: String,
    @SerialName("local_path") val localPath: String,
    @SerialName("conflict_policy") val conflictPolicy: String = "THREE_WAY_AUTO",
    @SerialName("sync_mode") val syncMode: String? = null,
    @SerialName("pull_order") val pullOrder: String? = null,
    @SerialName("versioning_type") val versioningType: String? = null,
    @SerialName("versioning_keep_days") val versioningKeepDays: Long? = null,
    @SerialName("marker_name") val markerName: String? = null,
    @SerialName("versioning_command") val versioningCommand: String? = null
)

@Serializable
data class CoreDevice(
    @SerialName("device_id") val deviceId: String,
    @SerialName("name") val name: String,
    @SerialName("addresses") val addresses: List<String> = emptyList(),
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("is_trusted") val isTrusted: Boolean = false,
    @SerialName("introducer") val introducer: Boolean = false,
    @SerialName("cert_name") val certName: String? = null,
    @SerialName("created_at") val createdAt: Long = 0
)

@Serializable
data class CorePendingDevice(
    @SerialName("device_id") val deviceId: String,
    @SerialName("name") val name: String,
    @SerialName("address") val address: String,
    @SerialName("received_at") val receivedAt: Long
)

@Serializable
data class CorePendingFolder(
    @SerialName("folder_id") val folderId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("folder_label") val folderLabel: String,
    @SerialName("offered_at") val offeredAt: Long
)

@Serializable
data class CorePeerConnectionMetrics(
    @SerialName("device_id") val deviceId: String,
    @SerialName("address") val address: String,
    @SerialName("is_connected") val isConnected: Boolean,
    @SerialName("in_bps") val inBps: Long = 0,
    @SerialName("out_bps") val outBps: Long = 0,
    @SerialName("total_in_bytes") val totalInBytes: Long = 0,
    @SerialName("total_out_bytes") val totalOutBytes: Long = 0,
    @SerialName("latency_ms") val latencyMs: Long = 0,
    @SerialName("client_type") val clientType: String = "synapse-core"
)

@Serializable
data class CoreSystemVersion(
    @SerialName("version") val version: String,
    @SerialName("codename") val codename: String,
    @SerialName("os") val os: String,
    @SerialName("arch") val arch: String
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
    @SerialName("detected_at") val detectedAt: Long,
    @SerialName("available_actions") val availableActions: List<String> = emptyList(),
    @SerialName("is_terminal") val isTerminal: Boolean = false,
    @SerialName("state_description") val stateDescription: String = ""
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
