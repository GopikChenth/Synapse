package com.arcadelabs.synapse.daemon

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DaemonStartupScreen(
    state: DaemonState,
    bootProgress: Float,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Contained loading card
        Card(
            modifier = Modifier
                .width(380.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 24.dp)
            ) {
                Text(
                    text = "Synapse",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connecting your node...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))

                // Fixed height container to prevent layout shifts/jumps
                Box(
                    modifier = Modifier.height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (state) {
                            is DaemonState.Idle, is DaemonState.Starting -> {
                                WavyCircularProgressIndicator(
                                    progress = bootProgress,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp),
                                    waveCount = 8
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Starting Syncthing Daemon...",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }
                            is DaemonState.Downloading -> {
                                val progressPercent = (state.progress * 100).toInt()
                                WavyCircularProgressIndicator(
                                    progress = state.progress,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Downloading Daemon... $progressPercent%",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }
                            is DaemonState.Error -> {
                                Text(
                                    text = "Failed to Start Daemon",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp)
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            is DaemonState.Ready -> {
                                WavyCircularProgressIndicator(
                                    progress = bootProgress,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Ready!",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WavyCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 1.5.dp,
    waveCount: Int = 10
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Animate phase shift to make waves flow/run around the circle
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier) {
        val center = this.center
        val strokeWidthPx = strokeWidth.toPx()
        val amplitudePx = amplitude.toPx()
        
        // Ensure standard fitting bounds
        val baseRadius = (this.size.minDimension - strokeWidthPx - 2 * amplitudePx) / 2

        // 1. Draw static background circular track
        drawCircle(
            color = trackColor,
            radius = baseRadius,
            center = center,
            style = Stroke(width = strokeWidthPx)
        )

        // 2. Draw sine-wave modulated active progress arc
        if (progress > 0f) {
            val progressAngleRad = progress * 2f * Math.PI.toFloat()
            val stepAngleRad = (Math.PI / 180).toFloat() // 1 degree intervals for high path resolution
            val steps = (progressAngleRad / stepAngleRad).toInt()

            if (steps > 0) {
                val path = Path()
                
                // Start drawing from the top (-90 degrees)
                val startAngleRad = -Math.PI.toFloat() / 2f
                val startRadius = baseRadius + amplitudePx * kotlin.math.sin(waveCount * startAngleRad + phaseShift)
                val startX = center.x + startRadius * kotlin.math.cos(startAngleRad)
                val startY = center.y + startRadius * kotlin.math.sin(startAngleRad)
                path.moveTo(startX, startY)

                for (step in 1..steps) {
                    val angleRad = startAngleRad + (step * stepAngleRad)
                    val radius = baseRadius + amplitudePx * kotlin.math.sin(waveCount * angleRad + phaseShift)
                    val x = center.x + radius * kotlin.math.cos(angleRad)
                    val y = center.y + radius * kotlin.math.sin(angleRad)
                    path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
