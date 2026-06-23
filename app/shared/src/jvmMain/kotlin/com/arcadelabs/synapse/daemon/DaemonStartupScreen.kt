package com.arcadelabs.synapse.daemon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
            .background(Color(0xFF0F172A)), // Sleek Dark Slate
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
                color = Color(0xFFC084FC) // Purple Accent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connecting your node...",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(32.dp))

            when (state) {
                is DaemonState.Idle, is DaemonState.Starting -> {
                    CircularProgressIndicator(
                        color = Color(0xFFC084FC)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Starting Syncthing Daemon...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                is DaemonState.Downloading -> {
                    val progressPercent = (state.progress * 100).toInt()
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = Color(0xFFC084FC),
                        trackColor = Color(0xFF334155),
                        modifier = Modifier.width(240.dp).height(6.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Downloading Syncthing Daemon... $progressPercent%",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                is DaemonState.Error -> {
                    Text(
                        text = "Failed to Start Daemon",
                        color = Color(0xFFF87171), // Pastel Red
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC084FC))
                    ) {
                        Text("Retry", color = Color.White)
                    }
                }
                is DaemonState.Ready -> {
                    CircularProgressIndicator(
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}
