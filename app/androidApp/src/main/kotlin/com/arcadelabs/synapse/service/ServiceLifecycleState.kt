package com.arcadelabs.synapse.service

/**
 * Explicit state machine for Android Syncthing/Synapse foreground service lifecycle.
 * Replaces scattered boolean flags (isInstanceRunning, null runnable, etc.).
 *
 * UX: Clear notifications with accurate dynamic descriptions and progress.
 * DX: Impossible to enter unhandled service states; explicit callback triggers.
 * AX: Agents can inspect service state via binder or tests.
 */
sealed interface ServiceLifecycleState {
    data object Stopped : ServiceLifecycleState
    data object WaitingForConditions : ServiceLifecycleState
    data object Starting : ServiceLifecycleState
    data class Running(
        val progressPercent: Int? = null,
        val downloadSpeedBps: Long = 0,
        val uploadSpeedBps: Long = 0
    ) : ServiceLifecycleState
    data class Error(val message: String, val exitCode: Int? = null) : ServiceLifecycleState
}
