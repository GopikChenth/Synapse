package com.arcadelabs.synapse.features.settings.ui

/**
 * Explicit state machine for form loading lifecycles.
 */
sealed interface FormLoadState {
    data object Idle : FormLoadState
    data object Loading : FormLoadState
    data object Ready : FormLoadState
    data class Error(val message: String) : FormLoadState
}

/**
 * Explicit state machine for form saving/persistence lifecycles.
 */
sealed interface FormSaveState {
    data object Idle : FormSaveState
    data object Saving : FormSaveState
    data object Success : FormSaveState
    data class Error(val message: String) : FormSaveState
}
