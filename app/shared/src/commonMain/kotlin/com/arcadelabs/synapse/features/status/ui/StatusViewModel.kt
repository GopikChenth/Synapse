package com.arcadelabs.synapse.features.status.ui

import com.arcadelabs.synapse.core.domain.models.isSynapseCore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.*
import com.arcadelabs.synapse.core.domain.state.BackendEngineType
import com.arcadelabs.synapse.core.domain.state.EngineConnectionState
import com.arcadelabs.synapse.core.network.*
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RunBehavior {
    FOLLOW,
    FORCE_START,
    FORCE_STOP
}

data class StatusUiState(
    val isRunning: Boolean = false,
    val engineState: EngineConnectionState = EngineConnectionState.Disconnected,
    val engineType: BackendEngineType = BackendEngineType.UNKNOWN,
    val myId: String = "",
    val uptime: Long = 0,
    val guiAddress: String = "",
    val version: String = ""
)

class StatusViewModel(
    private val apiClient: SynapseApiClient,
    private val preferencesHelper: PreferencesHelper
) : ViewModel() {

    private val _statusState = MutableStateFlow(StatusUiState())
    val statusState: StateFlow<StatusUiState> = _statusState

    private val _runBehavior = MutableStateFlow(RunBehavior.FOLLOW)
    val runBehavior: StateFlow<RunBehavior> = _runBehavior

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Only mark disconnected after this many consecutive poll failures (~6s at 2s interval)
    private var consecutiveFailures = 0
    private val failureThreshold = 3

    init {
        val saved = try {
            RunBehavior.valueOf(preferencesHelper.runBehavior)
        } catch (_: Exception) {
            RunBehavior.FOLLOW
        }
        _runBehavior.value = saved

        loadInitial()
        startPolling()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                updateStatus()
                loadLogs()
            } catch (e: Exception) {
                _error.value = when (e) {
                    is ApiKeyNotConfiguredException -> "Synapse API key is missing or not configured."
                    is SynapseUnauthorizedException -> "Unauthorized: API key is invalid or rejected."
                    is SynapseNotFoundException -> "Endpoint not found. Check the base URL."
                    is SynapseTimeoutException -> "Connection timed out. Is Synapse running?"
                    is SynapseApiException -> "API Error: ${e.message}"
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
                            msg.ifEmpty { "Failed to load system status" }
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
                var succeeded = false
                try {
                    updateStatus()
                    consecutiveFailures = 0
                    _error.value = null
                    succeeded = true
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= failureThreshold) {
                        _statusState.value = _statusState.value.copy(
                            isRunning = false,
                            engineState = EngineConnectionState.Disconnected
                        )
                    } else {
                        _statusState.value = _statusState.value.copy(
                            engineState = EngineConnectionState.Reconnecting(
                                consecutiveFailures = consecutiveFailures,
                                threshold = failureThreshold,
                                lastKnownEngine = _statusState.value.engineType
                            )
                        )
                    }
                }
                // Adaptive polling: 2s when running, 4s backoff when disconnected/idle to save resources
                val pollDelay = if (succeeded && _statusState.value.isRunning) 2000L else 4000L
                delay(pollDelay)
            }
        }
    }

    private suspend fun updateStatus() {
        try {
            val systemStatus = apiClient.systemStatus()
            val version = apiClient.systemVersion()

            val isSynapse = version.isSynapseCore()
            val engine = if (isSynapse) BackendEngineType.SYNAPSE_CORE else BackendEngineType.LEGACY_ENGINE

            val engineConn = EngineConnectionState.Connected(
                engine = engine,
                baseUrl = systemStatus.guiAddressUsed.ifEmpty { "http://127.0.0.1:8384" },
                deviceId = systemStatus.myID,
                version = version.currentVersion,
                uptimeSeconds = systemStatus.uptime
            )

            _statusState.value = StatusUiState(
                isRunning = true,
                engineState = engineConn,
                engineType = engine,
                myId = systemStatus.myID,
                uptime = systemStatus.uptime,
                guiAddress = systemStatus.guiAddressUsed,
                version = version.currentVersion
            )
        } catch (e: Exception) {
            // Don't flip isRunning here — let the polling failure counter handle it
            throw e
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            try {
                val systemLog = apiClient.systemLog()
                _logs.value = systemLog.messages
            } catch (e: Exception) {
                // Ignore log load issues
            }
        }
    }

    fun restartDaemon() {
        viewModelScope.launch {
            try {
                apiClient.restart()
            } catch (e: Exception) {
                _error.value = when (e) {
                    is ApiKeyNotConfiguredException -> "Synapse API key is missing or not configured."
                    is SynapseUnauthorizedException -> "Unauthorized: API key is invalid or rejected."
                    is SynapseNotFoundException -> "Endpoint not found. Check the base URL."
                    is SynapseTimeoutException -> "Connection timed out. Is Synapse running?"
                    is SynapseApiException -> "API Error: ${e.message}"
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
                            "Failed to restart: $msg"
                        }
                    }
                }
            }
        }
    }

    fun shutdownDaemon() {
        viewModelScope.launch {
            try {
                apiClient.shutdown()
            } catch (e: Exception) {
                _error.value = when (e) {
                    is ApiKeyNotConfiguredException -> "Synapse API key is missing or not configured."
                    is SynapseUnauthorizedException -> "Unauthorized: API key is invalid or rejected."
                    is SynapseNotFoundException -> "Endpoint not found. Check the base URL."
                    is SynapseTimeoutException -> "Connection timed out. Is Synapse running?"
                    is SynapseApiException -> "API Error: ${e.message}"
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
                            "Failed to shutdown: $msg"
                        }
                    }
                }
            }
        }
    }

    fun setRunBehavior(behavior: RunBehavior, onPlatformChange: ((RunBehavior) -> Unit)? = null) {
        _runBehavior.value = behavior
        preferencesHelper.runBehavior = behavior.name
        onPlatformChange?.invoke(behavior)
        
        if (behavior == RunBehavior.FORCE_STOP) {
            shutdownDaemon()
        }
    }
}
