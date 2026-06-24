package com.arcadelabs.synapse.features.folders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.network.*
import com.arcadelabs.synapse.core.domain.models.Folder
import com.arcadelabs.synapse.core.domain.models.Device
import com.arcadelabs.synapse.core.domain.models.FolderDeviceReference
import com.arcadelabs.synapse.core.domain.models.FolderVersioning
import com.arcadelabs.synapse.core.domain.models.PendingDevice
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.core.domain.models.DeviceConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FolderViewModel(
    private val apiClient: SyncthingApiClient
) : ViewModel() {

    private val _foldersState = MutableStateFlow<List<Folder>>(emptyList())
    val foldersState: StateFlow<List<Folder>> = _foldersState

    private val _devicesState = MutableStateFlow<List<Device>>(emptyList())
    val devicesState: StateFlow<List<Device>> = _devicesState

    private var cachedMyId: String = ""
    private var cachedConnections: Map<String, DeviceConnection> = emptyMap()

    private suspend fun fetchMyIdAndConnections() {
        try {
            if (cachedMyId.isEmpty()) {
                val status = apiClient.systemStatus()
                cachedMyId = status.myID
            }
        } catch (_: Exception) {}
        try {
            val connResp = apiClient.systemConnections()
            cachedConnections = connResp.connections
        } catch (_: Exception) {}
    }

    private fun updateDevicesState(devices: List<Device>) {
        val remotePairedDevices = devices.filter { device ->
            val devId = device.deviceID.normalizeDeviceId()
            val isSelf = devId == cachedMyId.normalizeDeviceId()
            val conn = cachedConnections[device.deviceID]
            val isPaired = conn != null && (conn.connected || conn.clientVersion.isNotEmpty() || conn.inBytesTotal > 0 || conn.outBytesTotal > 0)
            !isSelf && isPaired
        }
        _devicesState.value = remotePairedDevices
    }

    private val _pendingDevices = MutableStateFlow<Map<String, PendingDevice>>(emptyMap())
    val pendingDevices: StateFlow<Map<String, PendingDevice>> = _pendingDevices

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Folder IDs being deleted — excluded from poll results until server confirms removal
    private val pendingDeletions = mutableSetOf<String>()
    // Folders being added — kept in state even if a poll runs before server confirms
    private val pendingCreations = mutableListOf<Folder>()

    init {
        loadFolders()
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val config = apiClient.systemConfig()
                    // Filter out folders pending deletion so polls can't resurrect them
                    val filtered = config.folders.filter { it.id !in pendingDeletions }
                    // Merge in any pending creations not yet confirmed by server
                    val serverIds = filtered.map { it.id }.toSet()
                    val unconfirmedCreations = pendingCreations.filter { it.id !in serverIds }
                    _foldersState.value = filtered + unconfirmedCreations
                    fetchMyIdAndConnections()
                    updateDevicesState(config.devices)
                    try {
                        _pendingDevices.value = apiClient.getPendingDevices()
                    } catch (_: Exception) {}
                    _error.value = null
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val config = apiClient.systemConfig()
                _foldersState.value = config.folders
                fetchMyIdAndConnections()
                updateDevicesState(config.devices)
                try {
                    _pendingDevices.value = apiClient.getPendingDevices()
                } catch (_: Exception) {
                    _pendingDevices.value = emptyMap()
                }
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
                            msg.ifEmpty { "Failed to load configuration" }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createFolder(
        id: String,
        label: String,
        path: String,
        type: String,
        paused: Boolean,
        watchForChanges: Boolean,
        sharedDevices: List<String>,
        versioningType: String,
        onSuccess: () -> Unit
    ) {
        val newFolder = Folder(
            id = id,
            label = label,
            path = path,
            type = type,
            paused = paused,
            fsWatcherEnabled = watchForChanges,
            devices = sharedDevices.map { FolderDeviceReference(it.normalizeDeviceId()) },
            versioning = FolderVersioning(type = versioningType)
        )

        // Track creation so polling can't lose it before server confirms
        pendingCreations.add(newFolder)
        // Optimistic update — show immediately in UI
        val snapshot = _foldersState.value
        _foldersState.value = snapshot + newFolder

        viewModelScope.launch {
            _error.value = null
            try {
                val currentConfig = apiClient.systemConfig()
                val updatedConfig = currentConfig.copy(folders = currentConfig.folders + newFolder)
                apiClient.updateSystemConfig(updatedConfig)

                // Confirm from server
                val confirmed = apiClient.systemConfig()
                pendingCreations.removeAll { it.id == newFolder.id }
                _foldersState.value = confirmed.folders.filter { it.id !in pendingDeletions }
                fetchMyIdAndConnections()
                updateDevicesState(confirmed.devices)

                onSuccess()
            } catch (e: Exception) {
                // Rollback on failure
                pendingCreations.removeAll { it.id == newFolder.id }
                _foldersState.value = snapshot
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
                            msg.contains("refused", ignoreCase = true)
                        ) "Synapse is not started"
                        else msg.ifEmpty { "Failed to create folder" }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun deleteFolder(folderId: String) {
        // Track the deletion so polling can't resurrect it
        pendingDeletions.add(folderId)
        // Optimistic update — remove immediately from UI
        val snapshot = _foldersState.value
        _foldersState.value = snapshot.filter { it.id != folderId }

        viewModelScope.launch {
            try {
                // Use Syncthing's targeted DELETE endpoint — no full config replace, no restart
                apiClient.deleteFolder(folderId)
                // Confirm from server
                val confirmed = apiClient.systemConfig()
                _foldersState.value = confirmed.folders.filter { it.id !in pendingDeletions }
                fetchMyIdAndConnections()
                updateDevicesState(confirmed.devices)
            } catch (e: Exception) {
                // Rollback on failure
                _foldersState.value = snapshot
                _error.value = e.message ?: "Failed to delete folder"
            } finally {
                pendingDeletions.remove(folderId)
            }
        }
    }
    fun pauseFolder(folderId: String, paused: Boolean) {
        // Optimistic update
        _foldersState.value = _foldersState.value.map {
            if (it.id == folderId) it.copy(paused = paused) else it
        }
        viewModelScope.launch {
            try {
                val current = apiClient.systemConfig()
                val updated = current.copy(folders = current.folders.map {
                    if (it.id == folderId) it.copy(paused = paused) else it
                })
                apiClient.updateSystemConfig(updated)
            } catch (e: Exception) {
                // Rollback
                _foldersState.value = _foldersState.value.map {
                    if (it.id == folderId) it.copy(paused = !paused) else it
                }
                _error.value = e.message ?: "Failed to update folder"
            }
        }
    }

    fun rescanFolder(folderId: String) {
        println("[FolderViewModel] Triggering rescan for folder: $folderId")
        viewModelScope.launch {
            try {
                val response = apiClient.scan(folderId)
                println("[FolderViewModel] Rescan succeeded with status: ${response.status}")
                _error.value = null
            } catch (e: Exception) {
                println("[FolderViewModel] Rescan failed with exception: ${e.message}")
                _error.value = e.message ?: "Rescan failed"
            }
        }
    }

    fun updateFolder(
        id: String,
        label: String,
        path: String,
        type: String,
        paused: Boolean,
        watchForChanges: Boolean,
        sharedDevices: List<String>,
        versioningType: String,
        cleanoutAfter: Int,
        onSuccess: () -> Unit
    ) {
        val updatedFolder = Folder(
            id = id,
            label = label,
            path = path,
            type = type,
            paused = paused,
            fsWatcherEnabled = watchForChanges,
            devices = sharedDevices.map { FolderDeviceReference(it.normalizeDeviceId()) },
            versioning = FolderVersioning(
                type = versioningType,
                params = if (versioningType == "trashcan" || versioningType == "staggered") {
                    mapOf("cleanoutAfter" to cleanoutAfter.toString())
                } else {
                    emptyMap()
                }
            )
        )

        val snapshot = _foldersState.value
        _foldersState.value = snapshot.map { if (it.id == id) updatedFolder else it }

        viewModelScope.launch {
            _error.value = null
            try {
                val currentConfig = apiClient.systemConfig()
                val updatedFolders = currentConfig.folders.map {
                    if (it.id == id) updatedFolder else it
                }
                val updatedConfig = currentConfig.copy(folders = updatedFolders)
                apiClient.updateSystemConfig(updatedConfig)

                // Confirm from server
                val confirmed = apiClient.systemConfig()
                _foldersState.value = confirmed.folders.filter { it.id !in pendingDeletions }
                fetchMyIdAndConnections()
                updateDevicesState(confirmed.devices)

                onSuccess()
            } catch (e: Exception) {
                _foldersState.value = snapshot
                _error.value = e.message ?: "Failed to update folder"
            }
        }
    }

    fun dismissPendingDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                apiClient.dismissPendingDevice(deviceId)
                try {
                    _pendingDevices.value = apiClient.getPendingDevices()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }
}
