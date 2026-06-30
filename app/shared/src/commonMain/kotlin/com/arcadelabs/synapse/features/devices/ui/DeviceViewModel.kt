package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.*
import com.arcadelabs.synapse.core.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadInitial()
        startPolling()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                updateDeviceStates()
            } catch (e: Exception) {
                _error.value = when (e) {
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
                            msg.ifEmpty { "Failed to load devices" }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    updateDeviceStates()
                    _error.value = null
                } catch (e: Exception) {
                    // Ignore background network transient errors
                }
                delay(3000)
            }
        }
    }

    private suspend fun updateDeviceStates() {
        val config = apiClient.systemConfig()
        val connectionsResp = apiClient.systemConnections()
        val connections = connectionsResp.connections

        _folders.value = config.folders

        try {
            val status = apiClient.systemStatus()
            _myId.value = status.myID
        } catch (_: Exception) {}

        try {
            _pendingDevices.value = apiClient.getPendingDevices()
        } catch (e: Exception) {
            _pendingDevices.value = emptyMap()
        }

        try {
            _pendingFolders.value = apiClient.getPendingFolders()
        } catch (e: Exception) {
            _pendingFolders.value = emptyMap()
        }

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
        _devices.value = uiDevices
    }

    fun toggleDevicePause(deviceId: String, currentlyPaused: Boolean) {
        viewModelScope.launch {
            try {
                if (currentlyPaused) {
                    apiClient.resumeDevice(deviceId)
                } else {
                    apiClient.pauseDevice(deviceId)
                }
                updateDeviceStates()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                _error.value = if (
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("127.0.0.1") ||
                    msg.contains("refused", ignoreCase = true) ||
                    msg.contains("cert", ignoreCase = true) ||
                    msg.contains("trust", ignoreCase = true)
                ) {
                    "Synapse is not started"
                } else {
                    "Failed to update device state: $msg"
                }
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
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val currentConfig = apiClient.systemConfig()
                
                val normalizedId = id.normalizeDeviceId()
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
                try {
                    updateDeviceStates()
                } catch (_: Exception) {}
                onSuccess()
            } catch (e: Exception) {
                _error.value = when (e) {
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
                            msg.ifEmpty { "Failed to add device" }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
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
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val currentConfig = apiClient.systemConfig()
                val normalizedId = id.normalizeDeviceId()
                
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
                try {
                    updateDeviceStates()
                } catch (_: Exception) {}
                onSuccess()
            } catch (e: Exception) {
                _error.value = when (e) {
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
                            msg.ifEmpty { "Failed to update device" }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissPendingDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                apiClient.dismissPendingDevice(deviceId)
                updateDeviceStates()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                _error.value = if (
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("127.0.0.1") ||
                    msg.contains("refused", ignoreCase = true) ||
                    msg.contains("cert", ignoreCase = true) ||
                    msg.contains("trust", ignoreCase = true)
                ) {
                    "Synapse is not started"
                } else {
                    "Failed to dismiss pending device: $msg"
                }
            }
        }
    }

    fun dismissPendingFolder(folderId: String, deviceId: String, label: String, time: String) {
        viewModelScope.launch {
            try {
                apiClient.dismissPendingFolder(folderId, deviceId, label, time)
                updateDeviceStates()
            } catch (e: Exception) {
                val msg = e.message ?: ""
                _error.value = if (
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("127.0.0.1") ||
                    msg.contains("refused", ignoreCase = true) ||
                    msg.contains("cert", ignoreCase = true) ||
                    msg.contains("trust", ignoreCase = true)
                ) {
                    "Synapse is not started"
                } else {
                    "Failed to dismiss pending folder: $msg"
                }
            }
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val normalizedId = deviceId.normalizeDeviceId()
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
                updateDeviceStates()
            } catch (e: Exception) {
                _error.value = when (e) {
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
                            msg.ifEmpty { "Failed to delete device" }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
