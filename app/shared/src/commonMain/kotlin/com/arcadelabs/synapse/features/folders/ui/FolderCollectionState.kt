package com.arcadelabs.synapse.features.folders.ui

import com.arcadelabs.synapse.core.domain.models.Device
import com.arcadelabs.synapse.core.domain.models.Folder
import com.arcadelabs.synapse.core.domain.models.PendingDevice
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import com.arcadelabs.synapse.core.domain.state.dataOrNull
import com.arcadelabs.synapse.core.domain.state.errorMessageOrNull
import com.arcadelabs.synapse.core.domain.state.isLoading

/**
 * Models an in-flight mutation operation for a folder.
 *
 * UX: Allows individual folder cards to display specific operation badges (e.g. "Creating...", "Scanning...")
 *     and disable rapid double-clicks.
 * DX: Removes the need for raw synchronized mutable collections.
 * AX: Enables AI agents and tests to query in-flight mutation lifecycles directly.
 */
sealed interface FolderOperation {
    val folderId: String

    data class Creating(val folder: Folder) : FolderOperation {
        override val folderId: String get() = folder.id
    }
    data class Deleting(override val folderId: String) : FolderOperation
    data class Pausing(override val folderId: String, val targetPaused: Boolean) : FolderOperation
    data class Rescanning(override val folderId: String) : FolderOperation
}

/**
 * Unified state model for the Folders screen, serving as the single source of truth.
 */
data class FolderCollectionState(
    val resource: AsyncResource<List<Folder>> = AsyncResource.Idle,
    val devices: List<Device> = emptyList(),
    val pendingDevices: Map<String, PendingDevice> = emptyMap(),
    val activeOperations: Map<String, FolderOperation> = emptyMap()
) {
    val folders: List<Folder> get() = resource.dataOrNull ?: emptyList()
    val isLoading: Boolean get() = resource.isLoading
    val error: String? get() = resource.errorMessageOrNull

    fun operationFor(folderId: String): FolderOperation? = activeOperations[folderId]
}
