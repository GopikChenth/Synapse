package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.*
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import com.arcadelabs.synapse.core.network.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class DeviceUiModel(
    val id: String,
    val name: String,
    val addresses: List<String>,
    val paused: Boolean,
    val connected: Boolean = false,
    val addressConnected: String = "",
    val clientVersion: String = "",
    val connectionType: String = "",
    val inBytesTotal: Long = 0,
    val outBytesTotal: Long = 0,
    val introducer: Boolean = false,
    val autoAcceptFolders: Boolean = false,
    val untrusted: Boolean = false,
    val sharedFolders: List<String> = emptyList()
)

class DeviceViewModel(
    private val apiClient: SyncthingApiClient
) : ViewModel() {

    // ── Explicit State Machine (Primary Source of Truth) ─────────────────────
    private val _state = MutableStateFlow(DeviceCollectionState(resource = AsyncResource.Loading))
    val state: StateFlow<DeviceCollectionState> = _state.asStateFlow()

    // ── Backwards-Compatible Derived State Flows ─────────────────────────────
    private val _devices = MutableStateFlow<List<DeviceUiModel>>(emptyList())
    val devices: StateFlow<List<DeviceUiModel>> = _devices

    private val _myId = MutableStateFlow("")
    val myId: StateFlow<String> = _myId

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders

    private val _pendingDevices = MutableStateFlow<Map<String, PendingDevice>>(emptyMap())
    val pendingDevices: StateFlow<Map<String, PendingDevice>> = _pendingDevices

    private val _pendingFolders = MutableStateFlow<Map<String, PendingFolderOffer>>(emptyMap())
    val pendingFolders: StateFlow<Map<String, PendingFolderOffer>> = _pendingFolders

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val activeOperationsMap = mutableMapOf<String, DeviceOperation>()

    private fun getActiveOperations(): Map<String, DeviceOperation> = synchronized(activeOperationsMap) {
        activeOperationsMap.toMap()
    }

    private fun putOperation(op: DeviceOperation) = synchronized(activeOperationsMap) {
        activeOperationsMap[op.deviceId] = op
    }

    private fun removeOperation(deviceId: String) = synchronized(activeOperationsMap) {
        activeOperationsMap.remove(deviceId)
    }

    private fun commitState(
        resource: AsyncResource<List<DeviceUiModel>> = _state.value.resource,
        myId: String = _state.value.myId,
        folders: List<Folder> = _state.value.folders,
        pendingDevices: Map<String, PendingDevice> = _state.value.pendingDevices,
        pendingFolders: Map<String, PendingFolderOffer> = _state.value.pendingFolders
    ) {
        val ops = getActiveOperations()
        val newState = DeviceCollectionState(
            resource = resource,
            myId = myId,
            folders = folders,
            pendingDevices = pendingDevices,
            pendingFolders = pendingFolders,
            activeOperations = ops
        )
        _state.value = newState
        _devices.value = newState.devices
        _myId.value = newState.myId
        _folders.value = newState.folders
        _pendingDevices.value = newState.pendingDevices
        _pendingFolders.value = newState.pendingFolders
        _isLoading.value = newState.isLoading
        _error.value = newState.error
    }

    init {
        loadInitial()
        startPolling()
    }

    private fun mapExceptionToUserMessage(e: Exception, defaultMsg: String): String {
        return when (e) {
            is ApiKeyNotConfiguredException -> "Syncthing API key is missing or not configured."
            is SyncthingUnauthorizedException -> "Unauthorized: API key is invalid or rejected."
            is SyncthingNotFoundException -> "Endpoint not found. Check the base URL."
            is SyncthingTimeoutException -> "Connection timed out. Is Syncthing running?"
            is SyncthingApiException -> "API Error: ${e.message}"
            else -> {
                val msg = e.message ?: ""
                if (
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("127.0.0.1") ||
                    msg.contains("refused", ignoreCase = true) ||
                    msg.contains("cert", ignoreCase = true) ||
                    msg.contains("trust", ignoreCase = true)
                ) {
                    "Synapse is not started"
                } else {
                    msg.ifEmpty { defaultMsg }
                }
            }
        }
    }

    fun loadInitial() {
        viewModelScope.launch {
            val previousData = _state.value.devices
            commitState(resource = if (previousData.isEmpty()) AsyncResource.Loading else AsyncResource.Success(previousData, isRefreshing = true))
            try {
                updateDeviceStates()
            } catch (e: Exception) {
                val errMsg = mapExceptionToUserMessage(e, "Failed to load devices")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    updateDeviceStates()
                } catch (e: Exception) {
                    // Ignore background network transient errors
                }
                delay(3000)
            }
        }
    }

    private suspend fun updateDeviceStates() = coroutineScope {
        val configDeferred = async { apiClient.systemConfig() }
        val connectionsDeferred = async { apiClient.systemConnections() }
        val statusDeferred = async { runCatching { apiClient.systemStatus() }.getOrNull() }
        val pendingDevicesDeferred = async { runCatching { apiClient.getPendingDevices() }.getOrNull() }
        val pendingFoldersDeferred = async { runCatching { apiClient.getPendingFolders() }.getOrNull() }

        val config = configDeferred.await()
        val connectionsResp = connectionsDeferred.await()
        val status = statusDeferred.await()
        val pendingDevices = pendingDevicesDeferred.await()
        val pendingFolders = pendingFoldersDeferred.await()

        val connections = connectionsResp.connections

        val uiDevices = config.devices.map { device ->
            val conn = connections[device.deviceID]
            val deviceNormId = device.deviceID.normalizeDeviceId()
            val sharedFolders = config.folders.filter { folder ->
                folder.devices.any { it.deviceID.normalizeDeviceId() == deviceNormId }
            }.map { it.label.ifEmpty { it.id } }
            
            DeviceUiModel(
                id = device.deviceID,
                name = device.name,
                addresses = device.addresses,
                paused = device.paused,
                connected = conn?.connected ?: false,
                addressConnected = conn?.address ?: "",
                clientVersion = conn?.clientVersion ?: "",
                connectionType = conn?.type ?: "",
                inBytesTotal = conn?.inBytesTotal ?: 0,
                outBytesTotal = conn?.outBytesTotal ?: 0,
                introducer = device.introducer,
                autoAcceptFolders = device.autoAcceptFolders,
                untrusted = device.untrusted,
                sharedFolders = sharedFolders
            )
        }

        val res = if (uiDevices.isEmpty()) AsyncResource.Empty("No devices connected") else AsyncResource.Success(uiDevices)
        commitState(
            resource = res,
            myId = status?.myID ?: _state.value.myId,
            folders = config.folders,
            pendingDevices = pendingDevices ?: emptyMap(),
            pendingFolders = pendingFolders ?: emptyMap()
        )
    }

    fun toggleDevicePause(deviceId: String, currentlyPaused: Boolean) {
        putOperation(DeviceOperation.TogglingPause(deviceId, !currentlyPaused))
        commitState()
        viewModelScope.launch {
            try {
                if (currentlyPaused) {
                    apiClient.resumeDevice(deviceId)
                } else {
                    apiClient.pauseDevice(deviceId)
                }
                removeOperation(deviceId)
                updateDeviceStates()
            } catch (e: Exception) {
                removeOperation(deviceId)
                val errMsg = mapExceptionToUserMessage(e, "Failed to update device state")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }

    fun createDevice(
        id: String,
        name: String,
        addresses: List<String>,
        introducer: Boolean,
        autoAccept: Boolean,
        paused: Boolean,
        untrusted: Boolean,
        sharedFolders: List<String>,
        onSuccess: () -> Unit
    ) {
        val normalizedId = id.normalizeDeviceId()
        putOperation(DeviceOperation.Creating(normalizedId, name))
        commitState()

        viewModelScope.launch {
            try {
                val currentConfig = apiClient.systemConfig()
                
                val newDevice = Device(
                    deviceID = normalizedId,
                    name = name,
                    addresses = addresses,
                    paused = paused,
                    introducer = introducer,
                    autoAcceptFolders = autoAccept,
                    untrusted = untrusted
                )
                
                val updatedFolders = currentConfig.folders.map { folder ->
                    if (sharedFolders.contains(folder.id)) {
                        if (folder.devices.none { it.deviceID.normalizeDeviceId() == normalizedId }) {
                            folder.copy(devices = folder.devices + FolderDeviceReference(normalizedId))
                        } else {
                            folder
                        }
                    } else {
                        folder.copy(devices = folder.devices.filter { it.deviceID.normalizeDeviceId() != normalizedId })
                    }
                }
                
                val updatedDevices = currentConfig.devices.filter { it.deviceID.normalizeDeviceId() != normalizedId } + newDevice
                val updatedConfig = currentConfig.copy(
                    devices = updatedDevices,
                    folders = updatedFolders
                )
                
                apiClient.updateSystemConfig(updatedConfig)
                removeOperation(normalizedId)
                try {
                    updateDeviceStates()
                } catch (_: Exception) {}
                onSuccess()
            } catch (e: Exception) {
                removeOperation(normalizedId)
                val errMsg = mapExceptionToUserMessage(e, "Failed to add device")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }

    fun updateDevice(
        id: String,
        name: String,
        addresses: List<String>,
        introducer: Boolean,
        autoAccept: Boolean,
        paused: Boolean,
        untrusted: Boolean,
        sharedFolders: Map<String, String>,
        onSuccess: () -> Unit
    ) {
        val normalizedId = id.normalizeDeviceId()
        putOperation(DeviceOperation.Updating(normalizedId))
        commitState()

        viewModelScope.launch {
            try {
                val currentConfig = apiClient.systemConfig()
                
                val existingDevice = currentConfig.devices.firstOrNull { it.deviceID.normalizeDeviceId() == normalizedId }
                val updatedDevice = (existingDevice ?: Device(deviceID = normalizedId)).copy(
                    name = name,
                    addresses = addresses,
                    paused = paused,
                    introducer = introducer,
                    autoAcceptFolders = autoAccept,
                    untrusted = untrusted
                )
                
                val updatedFolders = currentConfig.folders.map { folder ->
                    if (sharedFolders.containsKey(folder.id)) {
                        val password = sharedFolders[folder.id] ?: ""
                        val existingRef = folder.devices.firstOrNull { it.deviceID.normalizeDeviceId() == normalizedId }
                        val newRef = FolderDeviceReference(
                            deviceID = normalizedId,
                            encryptionPassword = if (untrusted) password else ""
                        )
                        if (existingRef != null) {
                            folder.copy(devices = folder.devices.map {
                                if (it.deviceID.normalizeDeviceId() == normalizedId) newRef else it
                            })
                        } else {
                            folder.copy(devices = folder.devices + newRef)
                        }
                    } else {
                        folder.copy(devices = folder.devices.filter { it.deviceID.normalizeDeviceId() != normalizedId })
                    }
                }
                
                val updatedDevices = currentConfig.devices.map {
                    if (it.deviceID.normalizeDeviceId() == normalizedId) updatedDevice else it
                }
                
                val updatedConfig = currentConfig.copy(
                    devices = updatedDevices,
                    folders = updatedFolders
                )
                
                apiClient.updateSystemConfig(updatedConfig)
                removeOperation(normalizedId)
                try {
                    updateDeviceStates()
                } catch (_: Exception) {}
                onSuccess()
            } catch (e: Exception) {
                removeOperation(normalizedId)
                val errMsg = mapExceptionToUserMessage(e, "Failed to update device")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }

    fun dismissPendingDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                apiClient.dismissPendingDevice(deviceId)
                updateDeviceStates()
            } catch (e: Exception) {
                val errMsg = mapExceptionToUserMessage(e, "Failed to dismiss pending device")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }

    fun dismissPendingFolder(folderId: String, deviceId: String, label: String, time: String) {
        viewModelScope.launch {
            try {
                apiClient.dismissPendingFolder(folderId, deviceId, label, time)
                updateDeviceStates()
            } catch (e: Exception) {
                val errMsg = mapExceptionToUserMessage(e, "Failed to dismiss pending folder")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }

    fun deleteDevice(deviceId: String) {
        val normalizedId = deviceId.normalizeDeviceId()
        putOperation(DeviceOperation.Deleting(normalizedId))
        commitState()

        viewModelScope.launch {
            try {
                val currentConfig = apiClient.systemConfig()
                val updatedDevices = currentConfig.devices.filter { it.deviceID.normalizeDeviceId() != normalizedId }
                val updatedFolders = currentConfig.folders.map { folder ->
                    folder.copy(devices = folder.devices.filter { it.deviceID.normalizeDeviceId() != normalizedId })
                }
                val updatedConfig = currentConfig.copy(
                    devices = updatedDevices,
                    folders = updatedFolders
                )
                apiClient.updateSystemConfig(updatedConfig)
                removeOperation(normalizedId)
                updateDeviceStates()
            } catch (e: Exception) {
                removeOperation(normalizedId)
                val errMsg = mapExceptionToUserMessage(e, "Failed to delete device")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }
}
