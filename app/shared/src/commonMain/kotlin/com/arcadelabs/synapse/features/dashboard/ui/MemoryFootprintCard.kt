package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MemoryFootprintCard(
    allocBytes: Long,
    sysBytes: Long,
    modifier: Modifier = Modifier
) {
    var targetAlloc by remember { mutableStateOf(0f) }
    var targetSys by remember { mutableStateOf(0f) }
    LaunchedEffect(allocBytes, sysBytes) {
        targetAlloc = allocBytes.toFloat()
        targetSys = sysBytes.toFloat()
    }
    
    val animatedAlloc by animateFloatAsState(
        targetValue = targetAlloc,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedSys by animateFloatAsState(
        targetValue = targetSys,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = borderForTheme()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RAM Footprint",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = formatBytesKmp(animatedAlloc.toLong()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Sys: ${formatBytesKmp(animatedSys.toLong())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
