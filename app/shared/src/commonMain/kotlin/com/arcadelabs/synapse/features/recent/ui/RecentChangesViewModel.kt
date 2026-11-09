package com.arcadelabs.synapse.features.recent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.Event
import com.arcadelabs.synapse.core.domain.models.ItemFinishedEventData
import com.arcadelabs.synapse.core.domain.models.toItemFinishedData
import com.arcadelabs.synapse.core.domain.state.AsyncResource
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class RecentChangeUiModel(
    val id: Int,
    val itemName: String,
    val folderId: String,
    val action: String, // "update", "delete", "metadata"
    val type: String,   // "file", "dir"
    val time: String,   // formatted or raw time
    val error: String? = null
)

class RecentChangesViewModel(
    private val apiClient: SyncthingApiClient
) : ViewModel() {

    // ── Explicit State Machine (Primary Source of Truth) ─────────────────────
    private val _state = MutableStateFlow(RecentChangesState())
    val state: StateFlow<RecentChangesState> = _state.asStateFlow()

    // ── Backwards-Compatible Derived State Flows ─────────────────────────────
    private val _changes = MutableStateFlow<List<RecentChangeUiModel>>(emptyList())
    val changes: StateFlow<List<RecentChangeUiModel>> = _changes

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val json = Json { ignoreUnknownKeys = true }

    private fun commitState(resource: AsyncResource<List<RecentChangeUiModel>>) {
        val newState = RecentChangesState(resource = resource)
        _state.value = newState
        _changes.value = newState.changes
        _isLoading.value = newState.isLoading
        _error.value = newState.error
    }

    fun loadRecentChanges() {
        viewModelScope.launch {
            val previousData = _state.value.changes
            commitState(if (previousData.isEmpty()) AsyncResource.Loading else AsyncResource.Success(previousData, isRefreshing = true))
            try {
                // Fetch events from the last 1000 events
                val events = apiClient.getEvents(since = 0, limit = 1000)
                
                // Filter for "ItemFinished" and map to UI models
                val mapped = events
                    .filter { it.type == "ItemFinished" }
                    .mapNotNull { event ->
                        val data = event.toItemFinishedData(json) ?: return@mapNotNull null
                        RecentChangeUiModel(
                            id = event.globalID,
                            itemName = data.item,
                            folderId = data.folder,
                            action = data.action,
                            type = data.type,
                            time = formatTime(event.time),
                            error = data.error
                        )
                    }
                    // Show most recent changes first
                    .reversed()
                    .take(20)
                
                val res = if (mapped.isEmpty()) AsyncResource.Empty("No recent changes recorded") else AsyncResource.Success(mapped)
                commitState(res)
            } catch (e: Exception) {
                commitState(AsyncResource.Error(e.message ?: "Failed to load recent changes", e))
            }
        }
    }

    private fun formatTime(isoTime: String): String {
        // Simple parser to extract date and time in a friendly way
        // Example input: "2014-07-13T21:22:03.414609034+02:00"
        return try {
            val tIndex = isoTime.indexOf('T')
            if (tIndex != -1) {
                val datePart = isoTime.substring(0, tIndex)
                val timePart = isoTime.substring(tIndex + 1, tIndex + 9) // hh:mm:ss
                "$datePart $timePart"
            } else {
                isoTime
            }
        } catch (_: Exception) {
            isoTime
        }
    }
}
