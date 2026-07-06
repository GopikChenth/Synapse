package com.arcadelabs.synapse.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ==========================================
// 1. STANDARD PURPLE THEME
// ==========================================
private val StandardPurpleDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF2B2930),
    surfaceContainerHigh = Color(0xFF36343B),
    outline = Color(0xFF938F99)
)

private val StandardPurpleLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    outline = Color(0xFF79747E)
)

// ==========================================
// 2. DEEP SPACE THEME
// ==========================================
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
    surfaceContainer = Color(0xFF1A2230),
    surfaceContainerHigh = Color(0xFF243040),
    outline = Color(0xFF45A29E)
)

private val DeepSpaceLightColorScheme = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF00272C),
    secondary = Color(0xFF45A29E),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF1F2833),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2833),
    surfaceVariant = Color(0xFFE9ECEF),
    onSurfaceVariant = Color(0xFF495057),
    surfaceContainer = Color(0xFFF0F2F5),
    surfaceContainerHigh = Color(0xFFE9ECEF),
    outline = Color(0xFF45A29E)
)

// ==========================================
// 3. MIDNIGHT GREEN THEME
// ==========================================
private val MidnightGreenColorScheme = darkColorScheme(
    primary = Color(0xFF52A435),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8CCF73),
    onPrimaryContainer = Color(0xFF171615),
    secondary = Color(0xFF8CCF73),
    onSecondary = Color(0xFF171615),
    secondaryContainer = Color(0xFF3F7F29),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFE6B84A),
    onTertiary = Color(0xFF171615),
    tertiaryContainer = Color(0x33E6B84A),
    onTertiaryContainer = Color(0xFFE6B84A),
    background = Color(0xFF171615),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF232120),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2B2928),
    onSurfaceVariant = Color(0xFFB7B7B7),
    surfaceContainer = Color(0xFF2B2928),
    surfaceContainerHigh = Color(0xFF353332),
    outline = Color(0xFF353332),
    outlineVariant = Color(0xFF353332),
    error = Color(0xFFE97152),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF351C18),
    onErrorContainer = Color(0xFFE97152)
)

private val MidnightGreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF3F7F29),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6F5C9),
    onPrimaryContainer = Color(0xFF102A07),
    secondary = Color(0xFF52A435),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF8CCF73),
    onSecondaryContainer = Color(0xFF171615),
    tertiary = Color(0xFFB38600),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF2CC),
    onTertiaryContainer = Color(0xFFB38600),
    background = Color(0xFFF7F8F6),
    onBackground = Color(0xFF171615),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171615),
    surfaceVariant = Color(0xFFEAECE7),
    onSurfaceVariant = Color(0xFF5C5E5A),
    surfaceContainer = Color(0xFFF0F2ED),
    surfaceContainerHigh = Color(0xFFEAECE7),
    outline = Color(0xFFB7B7B7),
    outlineVariant = Color(0xFFE0E0E0),
    error = Color(0xFFD84A26),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFFD84A26)
)

// ==========================================
// 4. TACTICAL HUD THEME
// ==========================================
private val TacticalHUDColorScheme = darkColorScheme(
    primary = Color(0xFF24FCDE),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0E3835),
    onPrimaryContainer = Color(0xFFA2FEF2),
    secondary = Color(0xFF588094),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF1B2931),
    onSecondaryContainer = Color(0xFFB4CCD9),
    tertiary = Color(0xFFFFD60A),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF3A3000),
    onTertiaryContainer = Color(0xFFFFE66D),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0B0F13),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A222C),
    onSurfaceVariant = Color(0xFF9CB4C2),
    surfaceContainer = Color(0xFF131A21),
    surfaceContainerHigh = Color(0xFF1A222C),
    outline = Color(0xFF2C3B47),
    outlineVariant = Color(0xFF1F2B34),
    error = Color(0xFFFF4328),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3D120D),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val TacticalHUDLightColorScheme = lightColorScheme(
    primary = Color(0xFF007568),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0F8F3),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF3C5E6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E6ED),
    onSecondaryContainer = Color(0xFF1B2931),
    tertiary = Color(0xFF756200),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFF1C6),
    onTertiaryContainer = Color(0xFF756200),
    background = Color(0xFFEAEFF2),
    onBackground = Color(0xFF05070A),
    surface = Color(0xFFF0F4F7),
    onSurface = Color(0xFF05070A),
    surfaceVariant = Color(0xFFE2E7EC),
    onSurfaceVariant = Color(0xFF3C4858),
    surfaceContainer = Color(0xFFE8ECF0),
    surfaceContainerHigh = Color(0xFFE2E7EC),
    outline = Color(0xFF718294),
    outlineVariant = Color(0xFFB4C5D6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun SynapseTheme(
    selectedTheme: String,
    themeMode: String,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme() // "System"
    }

    val colorScheme = when (selectedTheme) {
        "DeepSpace" -> if (darkTheme) DeepSpaceColorScheme else DeepSpaceLightColorScheme
        "TacticalHUD" -> TacticalHUDColorScheme
        "MidnightGreen" -> if (darkTheme) MidnightGreenColorScheme else MidnightGreenLightColorScheme
        else -> if (darkTheme) StandardPurpleDarkColorScheme else StandardPurpleLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
