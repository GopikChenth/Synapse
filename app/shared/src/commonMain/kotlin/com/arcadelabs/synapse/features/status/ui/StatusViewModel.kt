package com.arcadelabs.synapse.features.status.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.*
import com.arcadelabs.synapse.core.network.SyncthingApiClient
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
    private val apiClient: SyncthingApiClient
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

    init {
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
                _error.value = e.message ?: "Failed to load system status"
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
                    _error.value = null
                } catch (e: Exception) {
                    // Ignore background transience
                }
                delay(2000)
            }
        }
    }

    private suspend fun updateStatus() {
        try {
            val systemStatus = apiClient.getSystemStatus()
            val version = apiClient.getSystemVersion()
            val connectionsResp = apiClient.getConnections()
            
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
                version = version.version,
                downloadSpeed = dlSpeed,
                uploadSpeed = ulSpeed,
                totalDownload = currentIn,
                totalUpload = currentOut
            )
        } catch (e: Exception) {
            _statusState.value = _statusState.value.copy(isRunning = false)
            throw e
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            try {
                val systemLog = apiClient.getSystemLog()
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
                _error.value = "Failed to restart: ${e.message}"
            }
        }
    }

    fun shutdownDaemon() {
        viewModelScope.launch {
            try {
                apiClient.shutdown()
            } catch (e: Exception) {
                _error.value = "Failed to shutdown: ${e.message}"
            }
        }
    }

    fun setRunBehavior(behavior: RunBehavior, onPlatformChange: ((RunBehavior) -> Unit)? = null) {
        _runBehavior.value = behavior
        onPlatformChange?.invoke(behavior)
        
        if (behavior == RunBehavior.FORCE_STOP) {
            shutdownDaemon()
        }
    }
}
