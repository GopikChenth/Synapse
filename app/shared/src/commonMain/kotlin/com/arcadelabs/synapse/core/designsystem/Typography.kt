package com.arcadelabs.synapse.core.designsystem

import androidx.compose.material3.Typography

/**
 * The unmodified Material 3 type scale.
 *
 * Use the roles directly (`MaterialTheme.typography.titleMedium`, …) and
 * never set `fontSize` in UI code. Typical mapping in Synapse:
 *  - titleLarge   – top app bar titles, dialog headlines
 *  - titleMedium  – card titles, section headers
 *  - bodyLarge    – list-item headlines, settings labels
 *  - bodyMedium   – supporting text, form content
 *  - bodySmall    – paths, timestamps, metadata
 *  - labelLarge   – buttons, tabs
 *  - labelMedium / labelSmall – badges, chips, helper labels
 */
val SynapseTypography = Typography()
