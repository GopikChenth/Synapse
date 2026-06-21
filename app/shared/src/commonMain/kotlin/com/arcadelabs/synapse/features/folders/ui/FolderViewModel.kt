package com.arcadelabs.synapse.features.folders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.network.*
import com.arcadelabs.synapse.core.domain.models.Folder
import com.arcadelabs.synapse.core.domain.models.Device
import com.arcadelabs.synapse.core.domain.models.FolderDeviceReference
import com.arcadelabs.synapse.core.domain.models.FolderVersioning
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadFolders()
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val config = apiClient.systemConfig()
                    _foldersState.value = config.folders
                    _devicesState.value = config.devices
                    _error.value = null
                } catch (_: Exception) {
                    // Ignore background network transient errors during startup
                }
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
                _devicesState.value = config.devices
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
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val currentConfig = apiClient.systemConfig()
                val newFolder = Folder(
                    id = id,
                    label = label,
                    path = path,
                    type = type,
                    paused = paused,
                    fsWatcherEnabled = watchForChanges,
                    devices = sharedDevices.map { FolderDeviceReference(it) },
                    versioning = FolderVersioning(type = versioningType)
                )
                val updatedFolders = currentConfig.folders + newFolder
                val updatedConfig = currentConfig.copy(folders = updatedFolders)
                
                apiClient.updateSystemConfig(updatedConfig)
                loadFolders() // Refresh our flows
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
                            msg.ifEmpty { "Failed to create folder" }
                        }
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
