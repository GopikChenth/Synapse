package com.arcadelabs.synapse.core.domain.state

/**
 * Universal, explicit, type-safe state machine modeling any asynchronous data lifecycle.
 * Replaces scattered and disconnected boolean/error flags (isLoading, error, data).
 *
 * UX: Distinguishes between first-time loading, empty collections, active content,
 *     in-flight background refresh, and errors with graceful offline caching.
 * DX: Enables exhaustive `when` expressions, impossible to represent illegal states
 *     (such as isLoading=true and error!=null), and provides functional transforms (map, fold).
 * AX: Agents can inspect `.resource` or pattern match on the exact lifecycle phase
 *     deterministically without permutations across multi-flag variables.
 */
sealed interface AsyncResource<out T> {

    /**
     * Initial cold state before any fetch operation has started.
     */
    data object Idle : AsyncResource<Nothing>

    /**
     * Initial fetch in progress (no data exists in memory yet).
     */
    data object Loading : AsyncResource<Nothing>

    /**
     * Fetch succeeded and data is available.
     * @param data The active data payload.
     * @param isRefreshing True if a background re-sync/polling cycle is in-flight while preserving existing data.
     */
    data class Success<out T>(
        val data: T,
        val isRefreshing: Boolean = false
    ) : AsyncResource<T>

    /**
     * Fetch succeeded cleanly, but the resulting collection or data is logically empty.
     */
    data class Empty(
        val message: String = ""
    ) : AsyncResource<Nothing>

    /**
     * Fetch operation failed with an error.
     * @param message Human-readable error description.
     * @param cause Underlying exception, if any.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : AsyncResource<Nothing>
}

// ── Functional & Ergonomic Extensions ────────────────────────────────────────

val <T> AsyncResource<T>.dataOrNull: T?
    get() = (this as? AsyncResource.Success<T>)?.data

val <T> AsyncResource<T>.isLoading: Boolean
    get() = this is AsyncResource.Loading

val <T> AsyncResource<T>.isRefreshing: Boolean
    get() = (this as? AsyncResource.Success<T>)?.isRefreshing == true

val <T> AsyncResource<T>.isError: Boolean
    get() = this is AsyncResource.Error

val <T> AsyncResource<T>.errorMessageOrNull: String?
    get() = (this as? AsyncResource.Error)?.message

inline fun <T, R> AsyncResource<T>.map(transform: (T) -> R): AsyncResource<R> {
    return when (this) {
        is AsyncResource.Idle -> AsyncResource.Idle
        is AsyncResource.Loading -> AsyncResource.Loading
        is AsyncResource.Empty -> AsyncResource.Empty(message)
        is AsyncResource.Error -> AsyncResource.Error(message, cause)
        is AsyncResource.Success -> AsyncResource.Success(transform(data), isRefreshing)
    }
}
