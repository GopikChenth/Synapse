package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SyncConflictsCard(
    conflictCount: Long,
    modifier: Modifier = Modifier
) {
    var targetConflicts by remember { mutableStateOf(0f) }
    LaunchedEffect(conflictCount) {
        targetConflicts = conflictCount.toFloat()
    }
    
    val animatedConflicts by animateFloatAsState(
        targetValue = targetConflicts,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = borderForTheme()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Failed / Conflict Files",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = if (conflictCount > 0) "${animatedConflicts.toInt()} issues" else "No issues",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (conflictCount > 0) MaterialTheme.colorScheme.error else Color(0xFF10B981)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = if (conflictCount > 0) "Requires attention" else "All files healthy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
