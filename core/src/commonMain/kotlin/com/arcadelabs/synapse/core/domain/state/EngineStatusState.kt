package com.arcadelabs.synapse.core.domain.state

/**
 * Identifies the active backend daemon engine powering Synapse.
 */
enum class BackendEngineType(val displayName: String, val isNativeRust: Boolean) {
    SYNAPSE_CORE("Synapse Core (Native Rust Engine)", isNativeRust = true),
    SYNCTHING("Syncthing (Legacy Go Engine)", isNativeRust = false),
    UNKNOWN("Auto-Detecting", isNativeRust = false)
}

/**
 * Explicit state machine for Synapse daemon connection and node discovery.
 * Replaces simple disconnected boolean flags (like `isRunning: Boolean`) with
 * precise, inspectable connection lifecycles.
 *
 * UX: Clear badge display (Connected to Native Rust vs Syncthing, Reconnecting with retry counts).
 * DX: Exhaustive handling of connection lifecycle in UI and CLI layers.
 * AX: Agents can inspect connection state with a single property and know the exact engine type and uptime.
 */
sealed interface EngineConnectionState {

    /** Daemon process is not running or stopped by the user */
    data object Disconnected : EngineConnectionState

    /** Actively attempting to establish HTTP/REST communication with the daemon */
    data object Connecting : EngineConnectionState

    /** Daemon is healthy, responding, and communicating with peers */
    data class Connected(
        val engine: BackendEngineType,
        val baseUrl: String,
        val deviceId: String,
        val version: String,
        val uptimeSeconds: Long
    ) : EngineConnectionState

    /** Daemon temporarily failed to respond to polls; performing graceful backoff retries */
    data class Reconnecting(
        val consecutiveFailures: Int,
        val threshold: Int,
        val lastKnownEngine: BackendEngineType = BackendEngineType.UNKNOWN
    ) : EngineConnectionState

    /** Unrecoverable daemon communication failure or process exit */
    data class Failed(
        val message: String,
        val exitCode: Int? = null
    ) : EngineConnectionState
}

val EngineConnectionState.isConnected: Boolean
    get() = this is EngineConnectionState.Connected

val EngineConnectionState.activeEngineType: BackendEngineType
    get() = when (this) {
        is EngineConnectionState.Connected -> engine
        is EngineConnectionState.Reconnecting -> lastKnownEngine
        else -> BackendEngineType.UNKNOWN
    }
