package com.arcadelabs.synapse.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Synapse Design Specification Typography System
 * Conforms strictly to design.md Section 1:
 * - titleLarge: TopAppBar title, major dialog header (Bold, 22sp, 28sp)
 * - titleMedium: Cards title, section header, drawer app title (Bold, 16sp, 24sp)
 * - bodyMedium: General form labels, main settings text, input fields (Medium, 14sp, 20sp)
 * - bodySmall: Secondary list labels, file paths, status details, timestamps (Regular, 12sp, 16sp)
 * - labelSmall: Input field helper labels, tiny categories (Medium, 11sp, 16sp)
 */
val SynapseTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
