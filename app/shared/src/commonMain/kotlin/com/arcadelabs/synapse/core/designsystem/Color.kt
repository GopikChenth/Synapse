package com.arcadelabs.synapse.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Synapse Design Specification Status Colors
 * Conforms strictly to design.md Section 2:
 * - Connected: Safe emerald tint (Color(0xFF10B981))
 * - Paused: Warning amber tint (Color(0xFFF59E0B))
 * - Disconnected: Alert crimson tint (Color(0xFFEF4444))
 */
object SynapseStatusColors {
    val Connected = Color(0xFF10B981)
    val Paused = Color(0xFFF59E0B)
    val Disconnected = Color(0xFFEF4444)
}
