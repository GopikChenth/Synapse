package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeaderStatusCard(
    isRunning: Boolean,
    uptime: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    com.arcadelabs.synapse.core.designsystem.SynapseLogo(
                        size = 32.dp,
                        cornerRadius = 8.dp
                    )
                    Text(
                        text = "Synapse Sync",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                com.arcadelabs.synapse.core.designsystem.components.SyncStateBadge(
                    state = if (isRunning) com.arcadelabs.synapse.core.designsystem.components.SyncState.Connected
                        else com.arcadelabs.synapse.core.designsystem.components.SyncState.Disconnected,
                    label = if (isRunning) "Running" else "Stopped"
                )
            }
            
            Text(
                text = if (isRunning) {
                    "The Synapse engine is running. Uptime: ${formatUptime(uptime)}"
                } else {
                    "The Synapse daemon is currently offline."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
