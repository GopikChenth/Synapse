package com.arcadelabs.synapse.features.quarantine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadelabs.synapse.core.domain.models.CoreDiffResponse
import com.arcadelabs.synapse.core.domain.models.CoreQuarantineConflict
import com.arcadelabs.synapse.core.domain.models.CoreResolveAction
import com.arcadelabs.synapse.core.domain.models.CoreResolveRequest
import com.arcadelabs.synapse.core.network.SyncthingApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuarantineUiState(
    val conflicts: List<CoreQuarantineConflict> = emptyList(),
    val selectedConflictId: String? = null,
    val selectedDiff: CoreDiffResponse? = null,
    val isLoading: Boolean = false,
    val isDiffLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class QuarantineViewModel(
    private val apiClient: SyncthingApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuarantineUiState())
    val uiState: StateFlow<QuarantineUiState> = _uiState.asStateFlow()

    init {
        loadConflicts()
    }

    fun loadConflicts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val list = apiClient.getConflicts()
                _uiState.value = _uiState.value.copy(
                    conflicts = list,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load conflicts"
                )
            }
        }
    }

    fun inspectDiff(conflictId: String) {
        if (_uiState.value.selectedConflictId == conflictId && _uiState.value.selectedDiff != null) {
            // Toggle off if already opened
            _uiState.value = _uiState.value.copy(
                selectedConflictId = null,
                selectedDiff = null
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedConflictId = conflictId,
                isDiffLoading = true,
                error = null
            )
            try {
                val diff = apiClient.getConflictDiff(conflictId)
                _uiState.value = _uiState.value.copy(
                    selectedDiff = diff,
                    isDiffLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDiffLoading = false,
                    error = e.message ?: "Failed to inspect diff for conflict $conflictId"
                )
            }
        }
    }

    fun resolve(conflictId: String, action: CoreResolveAction, customContent: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val req = CoreResolveRequest(action = action, mergedContent = customContent)
                val success = apiClient.resolveConflict(conflictId, req)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Resolved conflict with action $action",
                        selectedConflictId = null,
                        selectedDiff = null
                    )
                    loadConflicts()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Server could not resolve conflict $conflictId"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to resolve conflict"
                )
            }
        }
    }

    fun ignore(conflictId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val success = apiClient.ignoreConflict(conflictId)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Ignored conflict $conflictId",
                        selectedConflictId = null,
                        selectedDiff = null
                    )
                    loadConflicts()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Server could not ignore conflict $conflictId"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to ignore conflict"
                )
            }
        }
    }

    fun reopen(conflictId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val success = apiClient.reopenConflict(conflictId)
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Reopened conflict $conflictId",
                        selectedConflictId = null,
                        selectedDiff = null
                    )
                    loadConflicts()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Server could not reopen conflict $conflictId"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to reopen conflict"
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}
