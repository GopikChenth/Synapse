package com.arcadelabs.synapse.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.arcadelabs.synapse.core.prefs.PreferencesHelper
import org.koin.compose.koinInject

// --- 1. Deep Space (Sleek Dark) ---
private val DeepSpaceColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF004D56),
    onPrimaryContainer = Color(0xFF80F7FF),
    secondary = Color(0xFF45A29E),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF0B0C10),
    onBackground = Color(0xFFC5C6C7),
    surface = Color(0xFF1F2833),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2D3B4E),
    onSurfaceVariant = Color(0xFFC5C6C7),
    outline = Color(0xFF45A29E)
)

// --- 2. Sunset Glow (Warm Amber) ---
private val SunsetGlowColorScheme = darkColorScheme(
    primary = Color(0xFFE5BA73),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF5C4314),
    onPrimaryContainer = Color(0xFFFFE3B3),
    secondary = Color(0xFFC5A880),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF1A120B),
    onBackground = Color(0xFFF5EBE0),
    surface = Color(0xFF3F2E21),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF533E2D),
    onSurfaceVariant = Color(0xFFF5EBE0),
    outline = Color(0xFFC5A880)
)

// --- 3. Nordic Forest (Emerald Mint) ---
private val NordicForestColorScheme = darkColorScheme(
    primary = Color(0xFF00B4D8),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF00495C),
    onPrimaryContainer = Color(0xFF90E0EF),
    secondary = Color(0xFF90E0EF),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF0F171A),
    onBackground = Color(0xFFE0F2F1),
    surface = Color(0xFF1B2A30),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF263D47),
    onSurfaceVariant = Color(0xFFE0F2F1),
    outline = Color(0xFF90E0EF)
)

@Composable
fun SynapseTheme(
    selectedTheme: String,
    content: @Composable () -> Unit
) {
    val colorScheme = when (selectedTheme) {
        "DeepSpace" -> DeepSpaceColorScheme
        "SunsetGlow" -> SunsetGlowColorScheme
        "NordicForest" -> NordicForestColorScheme
        else -> MaterialTheme.colorScheme // Fallback to system default/original
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
