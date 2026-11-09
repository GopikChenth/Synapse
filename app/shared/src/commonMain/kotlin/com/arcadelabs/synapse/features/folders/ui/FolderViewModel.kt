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
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FolderViewModel(
    private val apiClient: SyncthingApiClient
) : ViewModel() {

    // ── Explicit State Machine (Primary Source of Truth) ─────────────────────
    private val _state = MutableStateFlow(FolderCollectionState(resource = AsyncResource.Loading))
    val state: StateFlow<FolderCollectionState> = _state.asStateFlow()

    // ── Backwards-Compatible Derived State Flows ─────────────────────────────
    private val _foldersState = MutableStateFlow<List<Folder>>(emptyList())
    val foldersState: StateFlow<List<Folder>> = _foldersState

    private val _devicesState = MutableStateFlow<List<Device>>(emptyList())
    val devicesState: StateFlow<List<Device>> = _devicesState

    private val _pendingDevices = MutableStateFlow<Map<String, PendingDevice>>(emptyMap())
    val pendingDevices: StateFlow<Map<String, PendingDevice>> = _pendingDevices

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var cachedMyId: String = ""
    private var cachedConnections: Map<String, DeviceConnection> = emptyMap()

    // Track active operations explicitly in a thread-safe map
    private val activeOperationsMap = mutableMapOf<String, FolderOperation>()

    private fun getActiveOperations(): Map<String, FolderOperation> = synchronized(activeOperationsMap) {
        activeOperationsMap.toMap()
    }

    private fun putOperation(op: FolderOperation) = synchronized(activeOperationsMap) {
        activeOperationsMap[op.folderId] = op
    }

    private fun removeOperation(folderId: String) = synchronized(activeOperationsMap) {
        activeOperationsMap.remove(folderId)
    }

    private fun commitState(
        resource: AsyncResource<List<Folder>> = _state.value.resource,
        devices: List<Device> = _state.value.devices,
        pending: Map<String, PendingDevice> = _state.value.pendingDevices
    ) {
        val ops = getActiveOperations()
        val newState = FolderCollectionState(
            resource = resource,
            devices = devices,
            pendingDevices = pending,
            activeOperations = ops
        )
        _state.value = newState
        _foldersState.value = newState.folders
        _devicesState.value = newState.devices
        _pendingDevices.value = newState.pendingDevices
        _isLoading.value = newState.isLoading
        _error.value = newState.error
    }

    init {
        loadFolders()
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val config = apiClient.systemConfig()
                    val currentOps = getActiveOperations()

                    // Filter out folders pending deletion so polls can't resurrect them
                    val deletedIds = currentOps.values.filterIsInstance<FolderOperation.Deleting>().map { it.folderId }.toSet()
                    val filtered = config.folders.filter { it.id !in deletedIds }

                    // Merge in any pending creations not yet confirmed by server
                    val pendingCreations = currentOps.values.filterIsInstance<FolderOperation.Creating>().map { it.folder }
                    val serverIds = filtered.map { it.id }.toSet()
                    val unconfirmedCreations = pendingCreations.filter { it.id !in serverIds }

                    // Protect in-flight pause/resume from being overwritten by stale poll data
                    val pauseChanges = currentOps.values.filterIsInstance<FolderOperation.Pausing>().associate { it.folderId to it.targetPaused }
                    val merged = (filtered + unconfirmedCreations).map { folder ->
                        val pendingPaused = pauseChanges[folder.id]
                        if (pendingPaused != null) folder.copy(paused = pendingPaused) else folder
                    }

                    fetchMyIdAndConnections()
                    val remoteDevices = filterRemotePairedDevices(config.devices)
                    val pendingDevs = try { apiClient.getPendingDevices() } catch (_: Exception) { emptyMap() }

                    val res = if (merged.isEmpty()) AsyncResource.Empty("No folders shared") else AsyncResource.Success(merged)
                    commitState(resource = res, devices = remoteDevices, pending = pendingDevs)
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            val previousData = _state.value.folders
            commitState(resource = if (previousData.isEmpty()) AsyncResource.Loading else AsyncResource.Success(previousData, isRefreshing = true))
            try {
                val config = apiClient.systemConfig()
                fetchMyIdAndConnections()
                val remoteDevices = filterRemotePairedDevices(config.devices)
                val pendingDevs = try { apiClient.getPendingDevices() } catch (_: Exception) { emptyMap() }

                val res = if (config.folders.isEmpty()) AsyncResource.Empty("No folders shared") else AsyncResource.Success(config.folders)
                commitState(resource = res, devices = remoteDevices, pending = pendingDevs)
            } catch (e: Exception) {
                val errMsg = mapExceptionToUserMessage(e, "Failed to load configuration")
                commitState(resource = AsyncResource.Error(errMsg, e))
            }
        }
    }

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

    private fun filterRemotePairedDevices(devices: List<Device>): List<Device> {
        return devices.filter { device ->
            val devId = device.deviceID.normalizeDeviceId()
            val isSelf = devId == cachedMyId.normalizeDeviceId()
            val conn = cachedConnections[device.deviceID]
            val isPaired = conn != null && (conn.connected || conn.clientVersion.isNotEmpty() || conn.inBytesTotal > 0 || conn.outBytesTotal > 0)
            !isSelf && isPaired
        }
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

        putOperation(FolderOperation.Creating(newFolder))
        val snapshot = _state.value.folders
        val optimisticList = snapshot + newFolder
        commitState(resource = AsyncResource.Success(optimisticList, isRefreshing = true))

        viewModelScope.launch {
            try {
                val currentConfig = apiClient.systemConfig()
                val updatedConfig = currentConfig.copy(folders = currentConfig.folders + newFolder)
                apiClient.updateSystemConfig(updatedConfig)

                val confirmed = apiClient.systemConfig()
                removeOperation(newFolder.id)
                fetchMyIdAndConnections()
                val remoteDevices = filterRemotePairedDevices(confirmed.devices)

                commitState(
                    resource = AsyncResource.Success(confirmed.folders),
                    devices = remoteDevices
                )
                onSuccess()
            } catch (e: Exception) {
                removeOperation(newFolder.id)
                val errMsg = mapExceptionToUserMessage(e, "Failed to create folder")
                commitState(resource = AsyncResource.Error(errMsg, e))
                // Restore snapshot if error
                _foldersState.value = snapshot
            }
        }
    }

    fun deleteFolder(folderId: String) {
        putOperation(FolderOperation.Deleting(folderId))
        val snapshot = _state.value.folders
        val optimisticList = snapshot.filter { it.id != folderId }
        commitState(resource = if (optimisticList.isEmpty()) AsyncResource.Empty("No folders shared") else AsyncResource.Success(optimisticList, isRefreshing = true))

        viewModelScope.launch {
            try {
                apiClient.deleteFolder(folderId)
                val confirmed = apiClient.systemConfig()
                removeOperation(folderId)
                fetchMyIdAndConnections()
                val remoteDevices = filterRemotePairedDevices(confirmed.devices)

                val res = if (confirmed.folders.isEmpty()) AsyncResource.Empty("No folders shared") else AsyncResource.Success(confirmed.folders)
                commitState(resource = res, devices = remoteDevices)
            } catch (e: Exception) {
                removeOperation(folderId)
                val errMsg = e.message ?: "Failed to delete folder"
                commitState(resource = AsyncResource.Error(errMsg, e))
                _foldersState.value = snapshot
            }
        }
    }

    fun pauseFolder(folderId: String, paused: Boolean) {
        putOperation(FolderOperation.Pausing(folderId, paused))
        val snapshot = _state.value.folders
        val optimisticList = snapshot.map { if (it.id == folderId) it.copy(paused = paused) else it }
        commitState(resource = AsyncResource.Success(optimisticList, isRefreshing = true))

        viewModelScope.launch {
            try {
                apiClient.setFolderPaused(folderId, paused)
                removeOperation(folderId)
                commitState(resource = AsyncResource.Success(optimisticList))
            } catch (e: Exception) {
                removeOperation(folderId)
                val errMsg = e.message ?: "Failed to update folder"
                commitState(resource = AsyncResource.Error(errMsg, e))
                _foldersState.value = snapshot
            }
        }
    }

    fun rescanFolder(folderId: String) {
        putOperation(FolderOperation.Rescanning(folderId))
        commitState()
        viewModelScope.launch {
            try {
                apiClient.scan(folderId)
                removeOperation(folderId)
                commitState()
            } catch (e: Exception) {
                removeOperation(folderId)
                commitState(resource = AsyncResource.Error(e.message ?: "Rescan failed", e))
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

        val snapshot = _state.value.folders
        val optimisticList = snapshot.map { if (it.id == id) updatedFolder else it }
        commitState(resource = AsyncResource.Success(optimisticList, isRefreshing = true))

        viewModelScope.launch {
            try {
                val currentConfig = apiClient.systemConfig()
                val updatedFolders = currentConfig.folders.map {
                    if (it.id == id) updatedFolder else it
                }
                val updatedConfig = currentConfig.copy(folders = updatedFolders)
                apiClient.updateSystemConfig(updatedConfig)

                val confirmed = apiClient.systemConfig()
                fetchMyIdAndConnections()
                val remoteDevices = filterRemotePairedDevices(confirmed.devices)

                commitState(resource = AsyncResource.Success(confirmed.folders), devices = remoteDevices)
                onSuccess()
            } catch (e: Exception) {
                commitState(resource = AsyncResource.Error(e.message ?: "Failed to update folder", e))
                _foldersState.value = snapshot
            }
        }
    }

    fun dismissPendingDevice(deviceId: String) {
        viewModelScope.launch {
            try {
                apiClient.dismissPendingDevice(deviceId)
                try {
                    val pendingDevs = apiClient.getPendingDevices()
                    commitState(pending = pendingDevs)
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }
}
