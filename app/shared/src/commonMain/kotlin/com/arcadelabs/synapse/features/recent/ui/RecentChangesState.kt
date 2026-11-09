package com.arcadelabs.synapse.features.recent.ui

import com.arcadelabs.synapse.core.domain.state.AsyncResource
import com.arcadelabs.synapse.core.domain.state.dataOrNull
import com.arcadelabs.synapse.core.domain.state.errorMessageOrNull
import com.arcadelabs.synapse.core.domain.state.isLoading

data class RecentChangesState(
    val resource: AsyncResource<List<RecentChangeUiModel>> = AsyncResource.Idle
) {
    val changes: List<RecentChangeUiModel> get() = resource.dataOrNull ?: emptyList()
    val isLoading: Boolean get() = resource.isLoading
    val error: String? get() = resource.errorMessageOrNull
}
