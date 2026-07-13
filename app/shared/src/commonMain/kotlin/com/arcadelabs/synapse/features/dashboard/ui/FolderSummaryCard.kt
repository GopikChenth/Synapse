package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.FolderIcon

@Composable
fun FolderSummaryCard(
    foldersCount: Int,
    pausedCount: Int,
    onNavigateToFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    var targetFolders by remember { mutableStateOf(0f) }
    var targetPaused by remember { mutableStateOf(0f) }
    
    LaunchedEffect(foldersCount, pausedCount) {
        targetFolders = foldersCount.toFloat()
        targetPaused = pausedCount.toFloat()
    }
    
    val animatedFolders by animateFloatAsState(
        targetValue = targetFolders,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedPaused by animateFloatAsState(
        targetValue = targetPaused,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Card(
        modifier = modifier.clickable { onNavigateToFolders() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = FolderIcon,
                    contentDescription = "Folders",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "${animatedFolders.toInt()}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = if (pausedCount > 0) "${animatedPaused.toInt()} Paused" else "All Synced",
                style = MaterialTheme.typography.bodySmall,
                color = if (pausedCount > 0) MaterialTheme.colorScheme.secondary else Color(0xFF10B981)
            )
        }
    }
}
