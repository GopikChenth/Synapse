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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.arcadelabs.synapse.core.designsystem.DevicesIcon

@Composable
fun DeviceSummaryCard(
    devicesCount: Int,
    activeCount: Int,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    var targetDevices by remember { mutableStateOf(0f) }
    var targetActive by remember { mutableStateOf(0f) }
    
    LaunchedEffect(devicesCount, activeCount) {
        targetDevices = devicesCount.toFloat()
        targetActive = activeCount.toFloat()
    }
    
    val animatedDevices by animateFloatAsState(
        targetValue = targetDevices,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )
    val animatedActive by animateFloatAsState(
        targetValue = targetActive,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Card(
        modifier = modifier.clickable { onNavigateToDevices() },
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
                    imageVector = DevicesIcon,
                    contentDescription = "Devices",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "${animatedDevices.toInt()}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "${animatedActive.toInt()} Active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
