package com.arcadelabs.synapse.features.status.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.components.LeadingIcon
import com.arcadelabs.synapse.core.designsystem.components.SyncState
import com.arcadelabs.synapse.core.designsystem.components.SyncStateBadge
import com.arcadelabs.synapse.core.domain.state.EngineConnectionState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StatusScreen(
    onRunBehaviorChanged: ((RunBehavior) -> Unit)? = null,
    viewModel: StatusViewModel = koinViewModel()
) {
    StatusScreenContent(
        onRunBehaviorChanged = onRunBehaviorChanged,
        viewModel = viewModel
    )
}

/**
 * Shared M3 status page (mobile and desktop): run-behaviour segmented buttons,
 * engine status card, and statistics as list items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusScreenContent(
    onRunBehaviorChanged: ((RunBehavior) -> Unit)?,
    viewModel: StatusViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.statusState.collectAsState()
    val runBehavior by viewModel.runBehavior.collectAsState()
    val isRunning = uiState.isRunning

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Run behaviour
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Run behaviour", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            val options = listOf(
                Triple(RunBehavior.FOLLOW, "Automatic", SynapseIcons.Sync),
                Triple(RunBehavior.FORCE_START, "Always on", SynapseIcons.PlayArrow),
                Triple(RunBehavior.FORCE_STOP, "Off", SynapseIcons.Pause)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (behavior, label, icon) ->
                    val selected = runBehavior == behavior
                    SegmentedButton(
                        selected = selected,
                        onClick = { viewModel.setRunBehavior(behavior, onRunBehaviorChanged) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = selected) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                            }
                        }
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            Text(
                text = when (runBehavior) {
                    RunBehavior.FOLLOW -> "Follows the run conditions in Settings."
                    RunBehavior.FORCE_START -> "Keeps syncing regardless of run conditions."
                    RunBehavior.FORCE_STOP -> "Syncing is stopped regardless of run conditions."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Engine status
        val statusText = when (val es = uiState.engineState) {
            is EngineConnectionState.Connected -> "${es.engine.displayName} is running"
            is EngineConnectionState.Reconnecting -> "Reconnecting (attempt ${es.consecutiveFailures} of ${es.threshold})…"
            is EngineConnectionState.Connecting -> "Connecting…"
            is EngineConnectionState.Failed -> "Connection failed: ${es.message}"
            is EngineConnectionState.Disconnected -> if (isRunning) "Running" else "Synapse isn't running"
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(statusText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SyncStateBadge(
                        state = if (isRunning) SyncState.Connected else SyncState.Disconnected,
                        label = if (isRunning) "Running" else "Stopped"
                    )
                }
            }
        }

        // Statistics
        Text("Statistics", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(modifier = Modifier.fillMaxWidth()) {
            val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(
                leadingContent = { LeadingIcon(SynapseIcons.History) },
                headlineContent = { Text("Uptime") },
                trailingContent = { Text(formatUptime(uiState.uptime), style = MaterialTheme.typography.bodyLarge) },
                colors = transparent
            )
        }
    }
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val mins = seconds / 60
    val hours = mins / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d ${hours % 24}h ${mins % 60}m"
        hours > 0 -> "${hours}h ${mins % 60}m"
        else -> "${mins}m"
    }
}
