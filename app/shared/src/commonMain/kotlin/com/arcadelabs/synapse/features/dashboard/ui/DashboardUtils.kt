package com.arcadelabs.synapse.features.dashboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun borderForTheme(): BorderStroke {
    return BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

fun formatBytesKmp(bytes: Long): String {
    val kb = bytes.toDouble() / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "${((gb * 100).toLong() / 100.0)} GiB"
        mb >= 1.0 -> "${((mb * 100).toLong() / 100.0)} MiB"
        kb >= 1.0 -> "${((kb * 100).toLong() / 100.0)} KiB"
        else -> "$bytes B"
    }
}

fun formatSpeedKmp(bytesPerSec: Long): String {
    return "${formatBytesKmp(bytesPerSec)}/s"
}

fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        if (minutes > 0) append("${minutes}m ")
        if (secs > 0 && days == 0L) append("${secs}s")
    }.trim()
}
