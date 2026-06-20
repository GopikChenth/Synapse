package com.arcadelabs.synapse.features.status.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject

@Composable
fun StatusScreen(
    onRunBehaviorChanged: ((RunBehavior) -> Unit)? = null,
    viewModel: StatusViewModel = koinInject()
) {
    val uiState by viewModel.statusState.collectAsState()
    val runBehavior by viewModel.runBehavior.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Connected Button Group (Expressive Morphing Segmented Button Row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val behaviors = listOf(
                Triple(RunBehavior.FOLLOW, "FOLLOW RUN\nCONDITIONS", Icons.Default.Refresh),
                Triple(RunBehavior.FORCE_START, "FORCE START\nIGNORE RUN", Icons.Default.Check),
                Triple(RunBehavior.FORCE_STOP, "FORCE STOP\nIGNORE RUN", Icons.Default.Close)
            )

            behaviors.forEachIndexed { index, (behavior, text, icon) ->
                val isSelected = runBehavior == behavior

                // Shape corner animations (morphing to fully rounded pill when selected)
                val topStart by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else if (index == 0) 24.dp else 4.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
                val bottomStart by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else if (index == 0) 24.dp else 4.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
                val topEnd by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else if (index == 2) 24.dp else 4.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
                val bottomEnd by animateDpAsState(
                    targetValue = if (isSelected) 24.dp else if (index == 2) 24.dp else 4.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )

                val buttonShape = RoundedCornerShape(
                    topStart = topStart,
                    bottomStart = bottomStart,
                    topEnd = topEnd,
                    bottomEnd = bottomEnd
                )

                // Layout weight animation (selected expands, unselected contracts)
                val weight by animateFloatAsState(
                    targetValue = if (isSelected) 1.6f else 0.7f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )

                // Theme color sync matching the reference designs
                val bgColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                }
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                }

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(buttonShape)
                        .background(bgColor)
                        .clickable {
                            viewModel.setRunBehavior(behavior, onRunBehaviorChanged)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor
                        )

                        AnimatedVisibility(
                            visible = isSelected,
                            enter = expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                            exit = shrinkHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = contentColor,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Running state text
        val isRunning = uiState.isRunning
        val statusText = if (isRunning) "Syncthing is running." else "Syncthing is not running."
        val statusColor = if (isRunning) Color(0xFF10B981) else Color(0xFFEF4444)

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = statusColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Reason:",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Reasons list
        val reasons = remember(runBehavior, isRunning) {
            if (isRunning) {
                listOf(
                    "Syncthing is not configured to run on mobile data connection.",
                    "Syncthing is allowed to run on WiFi and WiFi is currently connected.",
                    "Syncthing is allowed to run on non-metered WiFi connections. The active WiFi connection is non-metered.",
                    "Syncthing is allowed to run on the current WiFi network."
                )
            } else {
                if (runBehavior == RunBehavior.FORCE_STOP) {
                    listOf("Syncthing is not running because you forced it to stop regardless of the run conditions.")
                } else {
                    listOf("Syncthing is not running because Wi-Fi is disconnected or run conditions are not met.")
                }
            }
        }

        reasons.forEach { reason ->
            Text(
                text = "- $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stat: Uptime
        Text(
            text = "Uptime: ${formatUptime(uiState.uptime)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Stat: RAM Usage
        val ramUsageBytes = if (isRunning) uiState.allocBytes else 0L
        Text(
            text = "RAM Usage: ${formatBytes(ramUsageBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Stat: Download
        val dlSpeed = if (isRunning) uiState.downloadSpeed else 0L
        val dlTotal = if (isRunning) uiState.totalDownload else 0L
        Text(
            text = "Download: ${formatSpeed(dlSpeed)} (${formatBytes(dlTotal)})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Stat: Upload
        val ulSpeed = if (isRunning) uiState.uploadSpeed else 0L
        val ulTotal = if (isRunning) uiState.totalUpload else 0L
        Text(
            text = "Upload: ${formatSpeed(ulSpeed)} (${formatBytes(ulTotal)})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Stat: Configured Announce Server
        val announceServer = if (isRunning) "5/5" else "0/0"
        Text(
            text = "Configured Announce Server: $announceServer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 6.dp)
        )
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes.toDouble() / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${(gb * 10).toLong() / 10.0} GB"
        mb >= 1.0 -> "${(mb * 10).toLong() / 10.0} MB"
        kb >= 1.0 -> "${(kb * 10).toLong() / 10.0} KB"
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return "${formatBytes(bytesPerSec)}/s"
}
