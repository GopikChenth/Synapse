package com.arcadelabs.synapse.features.devices.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.*
import com.arcadelabs.synapse.core.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val outBytesTotal: Long = 0
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Device IDs being deleted — excluded from poll results so they can't be resurrected
    private val pendingDeletions = mutableSetOf<String>()

    // Pending device IDs the user has dismissed locally — filtered from all future polls
    private val localDismissed = mutableSetOf<String>()

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
            val all = apiClient.getPendingDevices()
            _pendingDevices.value = all.filter { (id, _) -> id !in localDismissed }
        } catch (e: Exception) {
            _pendingDevices.value = emptyMap()
        }

        val uiDevices = config.devices.map { device ->
            val conn = connections[device.deviceID]
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
                outBytesTotal = conn?.outBytesTotal ?: 0
            )
        }
        _devices.value = uiDevices.filter { it.id !in pendingDeletions }
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
        // Optimistic update — show immediately in UI
        val optimistic = DeviceUiModel(id = id, name = name.ifEmpty { id.take(7) }, addresses = addresses, paused = paused)
        _devices.value = _devices.value.filter { it.id != id } + optimistic

        viewModelScope.launch {
            _error.value = null
            try {
                val currentConfig = apiClient.systemConfig()
                val newDevice = Device(
                    deviceID = id, name = name, addresses = addresses,
                    paused = paused, introducer = introducer,
                    autoAcceptFolders = autoAccept, untrusted = untrusted
                )
                val updatedFolders = currentConfig.folders.map { folder ->
                    if (sharedFolders.contains(folder.id)) {
                        if (folder.devices.none { it.deviceID == id })
                            folder.copy(devices = folder.devices + FolderDeviceReference(id))
                        else folder
                    } else {
                        folder.copy(devices = folder.devices.filter { it.deviceID != id })
                    }
                }
                val updatedDevices = currentConfig.devices.filter { it.deviceID != id } + newDevice
                val updatedConfig = currentConfig.copy(devices = updatedDevices, folders = updatedFolders)
                apiClient.updateSystemConfig(updatedConfig)
                updateDeviceStates()
                onSuccess()
            } catch (e: Exception) {
                // Rollback optimistic add
                _devices.value = _devices.value.filter { it.id != id }
                _error.value = when (e) {
                    is ApiKeyNotConfiguredException  -> "Syncthing API key is missing or not configured."
                    is SyncthingUnauthorizedException -> "Unauthorized: API key is invalid or rejected."
                    is SyncthingNotFoundException    -> "Endpoint not found. Check the base URL."
                    is SyncthingTimeoutException     -> "Connection timed out. Is Syncthing running?"
                    is SyncthingApiException         -> "API Error: ${e.message}"
                    else -> {
                        val msg = e.message ?: ""
                        if (msg.contains("connect", ignoreCase = true) ||
                            msg.contains("127.0.0.1") ||
                            msg.contains("refused", ignoreCase = true))
                            "Synapse is not started"
                        else msg.ifEmpty { "Failed to add device" }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissPendingDevice(deviceId: String) {
        // Immediately hide from UI and block from future polls
        localDismissed.add(deviceId)
        _pendingDevices.value = _pendingDevices.value.filter { (id, _) -> id !in localDismissed }
        // Also tell the API (best-effort)
        viewModelScope.launch {
            try {
                apiClient.dismissPendingDevice(deviceId)
            } catch (_: Exception) { /* silent — local dismiss already applied */ }
        }
    }

    fun deleteDevice(deviceId: String) {
        // Optimistic update + block polling from resurrecting it
        pendingDeletions.add(deviceId)
        _devices.value = _devices.value.filter { it.id != deviceId }

        viewModelScope.launch {
            _error.value = null
            try {
                // Use targeted DELETE endpoint — no full config replace, no restart
                apiClient.deleteDevice(deviceId)
                updateDeviceStates()
            } catch (e: Exception) {
                // Rollback
                pendingDeletions.remove(deviceId)
                updateDeviceStates()
                _error.value = when (e) {
                    is ApiKeyNotConfiguredException  -> "Syncthing API key is missing or not configured."
                    is SyncthingUnauthorizedException -> "Unauthorized: API key is invalid or rejected."
                    is SyncthingNotFoundException    -> "Endpoint not found. Check the base URL."
                    is SyncthingTimeoutException     -> "Connection timed out. Is Syncthing running?"
                    is SyncthingApiException         -> "API Error: ${e.message}"
                    else -> e.message?.ifEmpty { "Failed to delete device" } ?: "Failed to delete device"
                }
            } finally {
                pendingDeletions.remove(deviceId)
                _isLoading.value = false
            }
        }
    }
}
