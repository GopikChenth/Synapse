package com.arcadelabs.synapse.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The unmodified Material 3 shape scale
 * (extraSmall 4, small 8, medium 12, large 16, extraLarge 28).
 *
 * Prefer `MaterialTheme.shapes.*` or a component's default shape. The
 * aliases below exist for non-composable call sites and map 1:1 to tokens.
 */
val SynapseShapes = Shapes()

/** = MaterialTheme.shapes.medium — cards and list containers. */
val SynapseCardShape = RoundedCornerShape(12.dp)

/** = MaterialTheme.shapes.extraLarge — dialogs (already the AlertDialog default). */
val SynapseDialogShape = RoundedCornerShape(28.dp)

/** = ShapeDefaults "full" — buttons, chips, segmented indicators (already the Button default). */
val SynapsePillShape = CircleShape

/** = extraLarge, top corners only — bottom sheets. */
val SynapseBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
