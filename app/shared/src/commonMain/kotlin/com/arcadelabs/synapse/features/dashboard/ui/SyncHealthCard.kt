package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun SyncHealthCard(
    globalCompletionPercentage: Double,
    modifier: Modifier = Modifier
) {
    var targetPercent by remember { mutableStateOf(0f) }
    LaunchedEffect(globalCompletionPercentage) {
        targetPercent = globalCompletionPercentage.toFloat()
    }
    
    val animatedPercent by animateFloatAsState(
        targetValue = targetPercent,
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
                text = "Sync Health",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            val completionStr = if (animatedPercent >= 99.9f) "100%" else "${((animatedPercent * 10).toLong() / 10.0)}%"
            Text(
                text = completionStr,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            WavyProgressIndicator(
                progress = animatedPercent / 100f,
                targetProgress = targetPercent / 100f,
                color = if (globalCompletionPercentage == 100.0) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = if (globalCompletionPercentage == 100.0) "In Sync" else "Syncing...",
                style = MaterialTheme.typography.labelSmall,
                color = if (globalCompletionPercentage == 100.0) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WavyProgressIndicator(
    progress: Float,
    targetProgress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 4.dp,
    waveHeight: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Curvy while progress is actively animating, and flat when it settles/stops
    val isAnimating = kotlin.math.abs(progress - targetProgress) > 0.001f
    val targetAmplitudeFactor = if (isAnimating) 1f - progress.coerceIn(0f, 1f) else 0f
    
    val animatedAmplitudeFactor by animateFloatAsState(
        targetValue = targetAmplitudeFactor,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
    )

    Canvas(modifier = modifier.height(waveHeight * 2 + strokeWidth)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokeWidthPx = strokeWidth.toPx()
        val waveHeightPx = waveHeight.toPx()
        
        // Draw background track as a straight line
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        // Draw progress wavy line
        if (progress > 0f) {
            val endX = width * progress.coerceIn(0f, 1f)
            val currentAmplitude = waveHeightPx * animatedAmplitudeFactor
            
            val path = Path()
            
            // Frequency of waves
            val frequency = 0.04f
            
            val startY = centerY + sin(phase) * currentAmplitude
            path.moveTo(0f, startY)
            
            var x = 2f
            while (x <= endX) {
                val y = centerY + sin(x * frequency + phase) * currentAmplitude
                path.lineTo(x, y)
                x += 2f
            }
            
            if (endX > 0f) {
                val yEnd = centerY + sin(endX * frequency + phase) * currentAmplitude
                path.lineTo(endX, yEnd)
            }
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}
