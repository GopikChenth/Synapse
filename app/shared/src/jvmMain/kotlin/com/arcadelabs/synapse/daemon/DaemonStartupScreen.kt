package com.arcadelabs.synapse.daemon

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DaemonStartupScreen(
    state: DaemonState,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Synapse",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connecting your node...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            when (state) {
                is DaemonState.Idle, is DaemonState.Starting -> {
                    FlowerLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Starting Syncthing Daemon...",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp
                    )
                }
                is DaemonState.Downloading -> {
                    val progressPercent = (state.progress * 100).toInt()
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.width(240.dp).height(6.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Downloading Syncthing Daemon... $progressPercent%",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp
                    )
                }
                is DaemonState.Error -> {
                    Text(
                        text = "Failed to Start Daemon",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Retry")
                    }
                }
                is DaemonState.Ready -> {
                    FlowerLoadingIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun FlowerLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    petalCount: Int = 8,
    size: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Spin the entire group of petals
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Breathing effect (pulsing)
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = angle
                scaleX = scale
                scaleY = scale
            }
    ) {
        val center = this.center
        val radius = this.size.minDimension / 2
        val petalWidth = radius * 0.28f
        val petalHeight = radius * 0.65f

        for (i in 0 until petalCount) {
            val angleDegrees = i * (360f / petalCount)
            rotate(degrees = angleDegrees, pivot = center) {
                // Dynamic gradient/fade to look like a rotation trail
                val alpha = 0.2f + 0.8f * (i.toFloat() / (petalCount - 1))
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = center.x - petalWidth / 2,
                        y = center.y - radius
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = petalWidth,
                        height = petalHeight
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        x = petalWidth / 2,
                        y = petalWidth / 2
                    )
                )
            }
        }
    }
}
