package com.arcadelabs.synapse.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Sync-status colors, resolved from the active Material 3 theme.
 *
 * Connected → success role, Paused → warning role, Disconnected → error role.
 * Each has a matching container / on-container pair for badges and chips,
 * so status UI never uses alpha-tinted hex colors.
 */
object SynapseStatusColors {
    val Connected: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.statusColors.success
    val ConnectedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.statusColors.successContainer
    val OnConnectedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.statusColors.onSuccessContainer

    val Paused: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.statusColors.warning
    val PausedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.statusColors.warningContainer
    val OnPausedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.statusColors.onWarningContainer

    val Disconnected: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
    val DisconnectedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.errorContainer
    val OnDisconnectedContainer: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onErrorContainer
}
