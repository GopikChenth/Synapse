package com.arcadelabs.synapse.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Synapse Design Specification Corner Shapes & Elevation
 * Conforms strictly to design.md Section 4:
 * - Cards & List Containers: RoundedCornerShape(12.dp)
 * - Overlays / Bottom Sheets / Dialogs: RoundedCornerShape(28.dp)
 * - Buttons / Segmented Active: Fully rounded pill shape (24.dp / 50%)
 */
val SynapseShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),      // Cards & List Containers
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)   // Overlays / Bottom Sheets / Dialogs
)

val SynapseCardShape = RoundedCornerShape(12.dp)
val SynapseDialogShape = RoundedCornerShape(28.dp)
val SynapsePillShape = RoundedCornerShape(24.dp)
val SynapseBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
