package com.arcadelabs.synapse.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.SynapseStatusColors

/** Connection state shown across folders and devices. */
enum class SyncState { Connected, Paused, Disconnected }

data class SyncStateColors(val color: Color, val container: Color, val onContainer: Color)

@Composable
fun SyncState.colors(): SyncStateColors = when (this) {
    SyncState.Connected -> SyncStateColors(
        SynapseStatusColors.Connected, SynapseStatusColors.ConnectedContainer, SynapseStatusColors.OnConnectedContainer
    )
    SyncState.Paused -> SyncStateColors(
        SynapseStatusColors.Paused, SynapseStatusColors.PausedContainer, SynapseStatusColors.OnPausedContainer
    )
    SyncState.Disconnected -> SyncStateColors(
        SynapseStatusColors.Disconnected, SynapseStatusColors.DisconnectedContainer, SynapseStatusColors.OnDisconnectedContainer
    )
}

/** Small status dot (8dp), colored with the status role. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier = modifier.size(size).background(color, CircleShape))
}

/**
 * Compact label badge (labelSmall on a tonal container, full-rounded), used for
 * states and protocol tags. Pass container/on-container roles, never alpha tints.
 */
@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    showDot: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                StatusDot(contentColor, size = 6.dp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Status badge for a [SyncState] with the default label. */
@Composable
fun SyncStateBadge(state: SyncState, modifier: Modifier = Modifier, label: String = state.name) {
    val c = state.colors()
    StatusBadge(text = label, containerColor = c.container, contentColor = c.onContainer, modifier = modifier, showDot = true)
}

/** 40dp tonal circle holding an icon — the M3 list-item leading element. */
@Composable
fun LeadingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/** Centered empty state: icon, headline, supporting text and an optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(8.dp))
            action()
        }
    }
}

/** Centered error state with a retry action. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = SynapseIcons.Error,
        title = "Something went wrong",
        body = message,
        modifier = modifier.fillMaxWidth(),
        action = {
            FilledTonalButton(onClick = onRetry) { Text("Retry") }
        }
    )
}
