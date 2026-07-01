package com.arcadelabs.synapse.features.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.ConfigOptions
import com.arcadelabs.synapse.core.domain.models.GuiConfig
import com.arcadelabs.synapse.core.domain.models.normalizeDeviceId
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // Device identity
    val deviceName: String = "",
    val deviceId: String = "",

    // Options — General
    val urAccepted: Int = 0,

    // Options — Connections
    val listenAddresses: String = "default",
    val maxSendKbps: String = "0",
    val maxRecvKbps: String = "0",
    val natEnabled: Boolean = true,
    val localAnnounceEnabled: Boolean = true,
    val globalAnnounceEnabled: Boolean = true,
    val relaysEnabled: Boolean = true,
    val globalAnnounceServers: String = "default",

    // Options — Advanced
    val crashReportingEnabled: Boolean = true,

    // GUI
    val guiAddress: String = "127.0.0.1:8384",
    val guiUser: String = "",
    val guiUseTLS: Boolean = false,
    val guiApiKey: String = "",

    // Save state
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val saveError: String? = null,

    // Internal originals for diffing
    val originalOptions: ConfigOptions? = null,
    val originalGui: GuiConfig? = null,
    val originalDeviceName: String = ""
)

class SettingsViewModel(
    private val apiClient: SyncthingApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, saveSuccess = false, saveError = null) }
            try {
                val options = apiClient.getConfigOptions()
                val gui = apiClient.getConfigGui()
                val status = apiClient.systemStatus()
                val config = apiClient.systemConfig()

                val localDevice = config.devices.find {
                    it.deviceID.normalizeDeviceId() == status.myID.normalizeDeviceId()
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        deviceName = localDevice?.name ?: "",
                        deviceId = status.myID,
                        // Options
                        urAccepted = options.urAccepted,
                        listenAddresses = options.listenAddresses.joinToString(", "),
                        maxSendKbps = options.maxSendKbps.toString(),
                        maxRecvKbps = options.maxRecvKbps.toString(),
                        natEnabled = options.natEnabled,
                        localAnnounceEnabled = options.localAnnounceEnabled,
                        globalAnnounceEnabled = options.globalAnnounceEnabled,
                        relaysEnabled = options.relaysEnabled,
                        globalAnnounceServers = options.globalAnnounceServers.joinToString(", "),
                        crashReportingEnabled = options.crashReportingEnabled,
                        // GUI
                        guiAddress = gui.address,
                        guiUser = gui.user,
                        guiUseTLS = gui.useTLS,
                        guiApiKey = gui.apiKey,
                        // Store originals
                        originalOptions = options,
                        originalGui = gui,
                        originalDeviceName = localDevice?.name ?: ""
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                val userMsg = if (
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("refused", ignoreCase = true) ||
                    msg.contains("127.0.0.1")
                ) "Daemon is not running" else msg
                _uiState.update { it.copy(isLoading = false, error = userMsg) }
            }
        }
    }

    // --- Field updaters ---

    fun updateDeviceName(value: String) = _uiState.update { it.copy(deviceName = value) }
    fun updateUsageReporting(accepted: Boolean) = _uiState.update { it.copy(urAccepted = if (accepted) 3 else 0) }
    fun updateListenAddresses(value: String) = _uiState.update { it.copy(listenAddresses = value) }
    fun updateMaxSendKbps(value: String) = _uiState.update { it.copy(maxSendKbps = value) }
    fun updateMaxRecvKbps(value: String) = _uiState.update { it.copy(maxRecvKbps = value) }
    fun updateNatEnabled(value: Boolean) = _uiState.update { it.copy(natEnabled = value) }
    fun updateLocalAnnounceEnabled(value: Boolean) = _uiState.update { it.copy(localAnnounceEnabled = value) }
    fun updateGlobalAnnounceEnabled(value: Boolean) = _uiState.update { it.copy(globalAnnounceEnabled = value) }
    fun updateRelaysEnabled(value: Boolean) = _uiState.update { it.copy(relaysEnabled = value) }
    fun updateGlobalAnnounceServers(value: String) = _uiState.update { it.copy(globalAnnounceServers = value) }
    fun updateCrashReportingEnabled(value: Boolean) = _uiState.update { it.copy(crashReportingEnabled = value) }
    fun updateGuiAddress(value: String) = _uiState.update { it.copy(guiAddress = value) }
    fun updateGuiUser(value: String) = _uiState.update { it.copy(guiUser = value) }
    fun updateGuiUseTLS(value: Boolean) = _uiState.update { it.copy(guiUseTLS = value) }

    fun clearSaveStatus() = _uiState.update { it.copy(saveSuccess = false, saveError = null) }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = false, saveError = null) }
            try {
                val state = _uiState.value
                val baseOptions = state.originalOptions ?: ConfigOptions()

                // Parse list fields
                val listenList = state.listenAddresses.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val serversList = state.globalAnnounceServers.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                val updatedOptions = baseOptions.copy(
                    listenAddresses = listenList.ifEmpty { listOf("default") },
                    globalAnnounceServers = serversList.ifEmpty { listOf("default") },
                    globalAnnounceEnabled = state.globalAnnounceEnabled,
                    localAnnounceEnabled = state.localAnnounceEnabled,
                    maxSendKbps = state.maxSendKbps.toIntOrNull() ?: 0,
                    maxRecvKbps = state.maxRecvKbps.toIntOrNull() ?: 0,
                    relaysEnabled = state.relaysEnabled,
                    natEnabled = state.natEnabled,
                    urAccepted = state.urAccepted,
                    crashReportingEnabled = state.crashReportingEnabled
                )

                val baseGui = state.originalGui ?: GuiConfig()
                val updatedGui = baseGui.copy(
                    address = state.guiAddress,
                    user = state.guiUser,
                    useTLS = state.guiUseTLS
                )

                apiClient.updateConfigOptions(updatedOptions)
                apiClient.updateConfigGui(updatedGui)

                // Update device name if changed
                if (state.deviceName != state.originalDeviceName) {
                    val config = apiClient.systemConfig()
                    val updatedDevices = config.devices.map { device ->
                        if (device.deviceID.normalizeDeviceId() == state.deviceId.normalizeDeviceId()) {
                            device.copy(name = state.deviceName)
                        } else device
                    }
                    apiClient.updateSystemConfig(config.copy(devices = updatedDevices))
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        originalOptions = updatedOptions,
                        originalGui = updatedGui,
                        originalDeviceName = state.deviceName
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                val userMsg = if (
                    msg.contains("connect", ignoreCase = true) ||
                    msg.contains("refused", ignoreCase = true) ||
                    msg.contains("127.0.0.1")
                ) "Daemon is not running" else msg
                _uiState.update { it.copy(isSaving = false, saveError = userMsg) }
            }
        }
    }
}
