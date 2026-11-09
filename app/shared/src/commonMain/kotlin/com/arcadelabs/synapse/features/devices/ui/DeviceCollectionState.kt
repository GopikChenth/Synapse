package com.arcadelabs.synapse.features.devices.ui

import com.arcadelabs.synapse.core.domain.models.Folder
import com.arcadelabs.synapse.core.domain.models.PendingDevice
import com.arcadelabs.synapse.core.domain.models.PendingFolderOffer
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import com.arcadelabs.synapse.core.domain.state.dataOrNull
import com.arcadelabs.synapse.core.domain.state.errorMessageOrNull
import com.arcadelabs.synapse.core.domain.state.isLoading

/**
 * Models an in-flight mutation operation on a remote device.
 */
sealed interface DeviceOperation {
    val deviceId: String

    data class Creating(val candidateId: String, val name: String) : DeviceOperation {
        override val deviceId: String get() = candidateId
    }
    data class Updating(override val deviceId: String) : DeviceOperation
    data class Deleting(override val deviceId: String) : DeviceOperation
    data class TogglingPause(override val deviceId: String, val targetPaused: Boolean) : DeviceOperation
}

/**
 * Unified state model for the Devices screen, serving as the single source of truth.
 */
data class DeviceCollectionState(
    val resource: AsyncResource<List<DeviceUiModel>> = AsyncResource.Idle,
    val myId: String = "",
    val folders: List<Folder> = emptyList(),
    val pendingDevices: Map<String, PendingDevice> = emptyMap(),
    val pendingFolders: Map<String, PendingFolderOffer> = emptyMap(),
    val activeOperations: Map<String, DeviceOperation> = emptyMap()
) {
    val devices: List<DeviceUiModel> get() = resource.dataOrNull ?: emptyList()
    val isLoading: Boolean get() = resource.isLoading
    val error: String? get() = resource.errorMessageOrNull

    fun operationFor(deviceId: String): DeviceOperation? = activeOperations[deviceId]
}
