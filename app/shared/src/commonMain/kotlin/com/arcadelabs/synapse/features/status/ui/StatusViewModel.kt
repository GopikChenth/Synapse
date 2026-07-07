package com.arcadelabs.synapse.features.status.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.*
import com.arcadelabs.synapse.core.network.*
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import kotlin.time.TimeSource
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
    val myId: String = "",
    val uptime: Long = 0,
    val allocBytes: Long = 0,
    val sysBytes: Long = 0,
    val guiAddress: String = "",
    val version: String = "",
    val downloadSpeed: Long = 0, // bytes/sec
    val uploadSpeed: Long = 0,   // bytes/sec
    val totalDownload: Long = 0,
    val totalUpload: Long = 0
)

class StatusViewModel(
    private val apiClient: SyncthingApiClient,
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

    private val timeSource = TimeSource.Monotonic
    private var lastMark = timeSource.markNow()
    private var lastInBytes: Long = 0
    private var lastOutBytes: Long = 0
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
                try {
                    updateStatus()
                    consecutiveFailures = 0
                    _error.value = null
                } catch (e: Exception) {
                    consecutiveFailures++
                    if (consecutiveFailures >= failureThreshold) {
                        _statusState.value = _statusState.value.copy(isRunning = false)
                    }
                }
                delay(2000)
            }
        }
    }

    private suspend fun updateStatus() {
        try {
            val systemStatus = apiClient.systemStatus()
            val version = apiClient.systemVersion()
            val connectionsResp = apiClient.systemConnections()
            
            val elapsed = lastMark.elapsedNow()
            lastMark = timeSource.markNow()
            val elapsedSeconds = elapsed.inWholeMilliseconds.toDouble() / 1000.0

            val total = connectionsResp.total
            val currentIn = total.inBytesTotal
            val currentOut = total.outBytesTotal

            val dlSpeed = if (lastInBytes > 0 && elapsedSeconds > 0) {
                ((currentIn - lastInBytes) / elapsedSeconds).toLong().coerceAtLeast(0)
            } else 0
            val ulSpeed = if (lastOutBytes > 0 && elapsedSeconds > 0) {
                ((currentOut - lastOutBytes) / elapsedSeconds).toLong().coerceAtLeast(0)
            } else 0

            lastInBytes = currentIn
            lastOutBytes = currentOut

            _statusState.value = StatusUiState(
                isRunning = true,
                myId = systemStatus.myID,
                uptime = systemStatus.uptime,
                allocBytes = systemStatus.alloc,
                sysBytes = systemStatus.sys,
                guiAddress = systemStatus.guiAddressUsed,
                version = version.currentVersion,
                downloadSpeed = dlSpeed,
                uploadSpeed = ulSpeed,
                totalDownload = currentIn,
                totalUpload = currentOut
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
