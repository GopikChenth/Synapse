package com.arcadelabs.synapse.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Synapse theme — Material 3 only.
 *
 * Every color scheme below is generated with the Material Color Utilities
 * algorithm (the same one behind Material Theme Builder and Android's
 * dynamic color), so every M3 color role — including all surface
 * containers — is defined and contrast-checked for light and dark.
 *
 *   Theme key        Seed        Scheme variant
 *   "Default"        #6750A4     Tonal spot (M3 baseline)
 *   "DeepSpace"      #00E5FF     Fidelity
 *   "MidnightGreen"  #52A435     Fidelity
 *   "TacticalHUD"    #24FCDE     Fidelity
 *   "Dynamic"        wallpaper   Android 12+ dynamic color (falls back to Default)
 *
 * Success / warning are M3 "custom colors": the brand status hues are
 * harmonized toward each theme's seed and expanded into the standard
 * color / onColor / colorContainer / onColorContainer tones. Errors use
 * the scheme's own error roles.
 *
 * Typography and shapes are the unmodified M3 type scale and shape scale.
 */

/** Theme keys stored in preferences. */
object SynapseThemes {
    const val Default = "Default"
    const val Dynamic = "Dynamic"
    const val DeepSpace = "DeepSpace"
    const val MidnightGreen = "MidnightGreen"
    const val TacticalHUD = "TacticalHUD"
}

/** M3 custom-color roles for sync status. */
@Immutable
data class SynapseStatusScheme(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color
)

internal val LocalSynapseStatusScheme = staticCompositionLocalOf { StandardPurpleStatusLight }

/** True when the platform can supply a dynamic (wallpaper-based) color scheme. */
val LocalDynamicColorAvailable = staticCompositionLocalOf { false }

/** True when the active color scheme is dark. */
val LocalSynapseDarkTheme = staticCompositionLocalOf { false }

/** Access the status roles of the active theme: `MaterialTheme.statusColors.success`. */
val MaterialTheme.statusColors: SynapseStatusScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalSynapseStatusScheme.current


@Composable
fun SynapseTheme(
    selectedTheme: String,
    themeMode: String,
    /** Platform hook for Material You. Return null when unsupported. */
    dynamicColorScheme: ((darkTheme: Boolean) -> ColorScheme)? = null,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme() // "System"
    }

    val dynamicScheme = if (selectedTheme == SynapseThemes.Dynamic) dynamicColorScheme?.invoke(darkTheme) else null

    val colorScheme = dynamicScheme ?: when (selectedTheme) {
        SynapseThemes.DeepSpace -> if (darkTheme) DeepSpaceDark else DeepSpaceLight
        SynapseThemes.MidnightGreen -> if (darkTheme) MidnightGreenDark else MidnightGreenLight
        SynapseThemes.TacticalHUD -> if (darkTheme) TacticalHUDDark else TacticalHUDLight
        else -> if (darkTheme) StandardPurpleDark else StandardPurpleLight
    }

    val statusScheme = when (selectedTheme) {
        SynapseThemes.DeepSpace -> if (darkTheme) DeepSpaceStatusDark else DeepSpaceStatusLight
        SynapseThemes.MidnightGreen -> if (darkTheme) MidnightGreenStatusDark else MidnightGreenStatusLight
        SynapseThemes.TacticalHUD -> if (darkTheme) TacticalHUDStatusDark else TacticalHUDStatusLight
        else -> if (darkTheme) StandardPurpleStatusDark else StandardPurpleStatusLight
    }

    CompositionLocalProvider(
        LocalSynapseStatusScheme provides statusScheme,
        LocalDynamicColorAvailable provides (dynamicColorScheme != null),
        LocalSynapseDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SynapseTypography,
            shapes = SynapseShapes,
            content = content
        )
    }
}

// =====================================================================
// Generated color schemes
// =====================================================================

// GENERATED with the Material Color Utilities algorithm (spec 2021).
// Do not hand-edit; regenerate from the seed colors listed below.

internal val StandardPurpleLight = lightColorScheme(
    primary = Color(0xFF65558F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF4D3D75),
    inversePrimary = Color(0xFFCFBDFE),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF4A4458),
    tertiary = Color(0xFF7E5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF633B48),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    surfaceTint = Color(0xFF65558F),
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDF7FF),
    surfaceContainer = Color(0xFFF2ECF4),
    surfaceContainerHigh = Color(0xFFECE6EE),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    surfaceContainerLow = Color(0xFFF8F2FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDED8E0)
)

internal val StandardPurpleDark = darkColorScheme(
    primary = Color(0xFFCFBDFE),
    onPrimary = Color(0xFF36275D),
    primaryContainer = Color(0xFF4D3D75),
    onPrimaryContainer = Color(0xFFE9DDFF),
    inversePrimary = Color(0xFF65558F),
    secondary = Color(0xFFCBC2DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    surfaceTint = Color(0xFFCFBDFE),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainer = Color(0xFF211F24),
    surfaceContainerHigh = Color(0xFF2B292F),
    surfaceContainerHighest = Color(0xFF36343A),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceDim = Color(0xFF141218)
)

internal val StandardPurpleStatusLight = SynapseStatusScheme(
    success = Color(0xFF006B5B),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFF6EF9DC),
    onSuccessContainer = Color(0xFF00201A),
    warning = Color(0xFF934B00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDCC5),
    onWarningContainer = Color(0xFF301400)
)

internal val StandardPurpleStatusDark = SynapseStatusScheme(
    success = Color(0xFF4CDCC1),
    onSuccess = Color(0xFF00382E),
    successContainer = Color(0xFF005144),
    onSuccessContainer = Color(0xFF6EF9DC),
    warning = Color(0xFFFFB782),
    onWarning = Color(0xFF4F2500),
    warningContainer = Color(0xFF703800),
    onWarningContainer = Color(0xFFFFDCC5)
)

internal val DeepSpaceLight = lightColorScheme(
    primary = Color(0xFF006875),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF00E5FF),
    onPrimaryContainer = Color(0xFF00626E),
    inversePrimary = Color(0xFF00DAF3),
    secondary = Color(0xFF2D6670),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB3ECF7),
    onSecondaryContainer = Color(0xFF346C76),
    tertiary = Color(0xFF765A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEC931),
    onTertiaryContainer = Color(0xFF6F5500),
    background = Color(0xFFF3FBFC),
    onBackground = Color(0xFF151D1E),
    surface = Color(0xFFF3FBFC),
    onSurface = Color(0xFF151D1E),
    surfaceVariant = Color(0xFFD6E5E9),
    onSurfaceVariant = Color(0xFF3B494C),
    surfaceTint = Color(0xFF006875),
    inverseSurface = Color(0xFF2A3233),
    inverseOnSurface = Color(0xFFEAF2F4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6B7A7D),
    outlineVariant = Color(0xFFBAC9CC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF3FBFC),
    surfaceContainer = Color(0xFFE8EFF1),
    surfaceContainerHigh = Color(0xFFE2E9EB),
    surfaceContainerHighest = Color(0xFFDCE4E5),
    surfaceContainerLow = Color(0xFFEDF5F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD4DBDD)
)

internal val DeepSpaceDark = darkColorScheme(
    primary = Color(0xFFC3F5FF),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF00E5FF),
    onPrimaryContainer = Color(0xFF00626E),
    inversePrimary = Color(0xFF006875),
    secondary = Color(0xFF98D0DA),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF11505A),
    onSecondaryContainer = Color(0xFF8AC1CC),
    tertiary = Color(0xFFFFEAC0),
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Color(0xFFFEC931),
    onTertiaryContainer = Color(0xFF6F5500),
    background = Color(0xFF0D1516),
    onBackground = Color(0xFFDCE4E5),
    surface = Color(0xFF0D1516),
    onSurface = Color(0xFFDCE4E5),
    surfaceVariant = Color(0xFF3B494C),
    onSurfaceVariant = Color(0xFFBAC9CC),
    surfaceTint = Color(0xFF00DAF3),
    inverseSurface = Color(0xFFDCE4E5),
    inverseOnSurface = Color(0xFF2A3233),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF849396),
    outlineVariant = Color(0xFF3B494C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF333A3C),
    surfaceContainer = Color(0xFF192122),
    surfaceContainerHigh = Color(0xFF242B2D),
    surfaceContainerHighest = Color(0xFF2E3638),
    surfaceContainerLow = Color(0xFF151D1E),
    surfaceContainerLowest = Color(0xFF080F11),
    surfaceDim = Color(0xFF0D1516)
)

internal val DeepSpaceStatusLight = SynapseStatusScheme(
    success = Color(0xFF006B5B),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFF6EF9DC),
    onSuccessContainer = Color(0xFF00201A),
    warning = Color(0xFF795900),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDF9F),
    onWarningContainer = Color(0xFF261A00)
)

internal val DeepSpaceStatusDark = SynapseStatusScheme(
    success = Color(0xFF4CDCC1),
    onSuccess = Color(0xFF00382E),
    successContainer = Color(0xFF005144),
    onSuccessContainer = Color(0xFF6EF9DC),
    warning = Color(0xFFF8BD26),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF5B4300),
    onWarningContainer = Color(0xFFFFDF9F)
)

internal val MidnightGreenLight = lightColorScheme(
    primary = Color(0xFF1F6D00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF52A435),
    onPrimaryContainer = Color(0xFF0A3200),
    inversePrimary = Color(0xFF85DB65),
    secondary = Color(0xFF476739),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC5EBB0),
    onSecondaryContainer = Color(0xFF4B6B3C),
    tertiary = Color(0xFFA52F72),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE564A8),
    onTertiaryContainer = Color(0xFF580039),
    background = Color(0xFFF6FBED),
    onBackground = Color(0xFF181D15),
    surface = Color(0xFFF6FBED),
    onSurface = Color(0xFF181D15),
    surfaceVariant = Color(0xFFDCE6D1),
    onSurfaceVariant = Color(0xFF40493A),
    surfaceTint = Color(0xFF1F6D00),
    inverseSurface = Color(0xFF2D3229),
    inverseOnSurface = Color(0xFFEEF3E5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF707A69),
    outlineVariant = Color(0xFFC0CAB6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FBED),
    surfaceContainer = Color(0xFFEBF0E2),
    surfaceContainerHigh = Color(0xFFE5EADC),
    surfaceContainerHighest = Color(0xFFDFE4D7),
    surfaceContainerLow = Color(0xFFF0F5E8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD7DCCF)
)

internal val MidnightGreenDark = darkColorScheme(
    primary = Color(0xFF85DB65),
    onPrimary = Color(0xFF0C3900),
    primaryContainer = Color(0xFF52A435),
    onPrimaryContainer = Color(0xFF0A3200),
    inversePrimary = Color(0xFF1F6D00),
    secondary = Color(0xFFACD199),
    onSecondary = Color(0xFF19370F),
    secondaryContainer = Color(0xFF325125),
    onSecondaryContainer = Color(0xFF9EC38C),
    tertiary = Color(0xFFFFAFD3),
    onTertiary = Color(0xFF620040),
    tertiaryContainer = Color(0xFFE564A8),
    onTertiaryContainer = Color(0xFF580039),
    background = Color(0xFF10150D),
    onBackground = Color(0xFFDFE4D7),
    surface = Color(0xFF10150D),
    onSurface = Color(0xFFDFE4D7),
    surfaceVariant = Color(0xFF40493A),
    onSurfaceVariant = Color(0xFFC0CAB6),
    surfaceTint = Color(0xFF85DB65),
    inverseSurface = Color(0xFFDFE4D7),
    inverseOnSurface = Color(0xFF2D3229),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A9482),
    outlineVariant = Color(0xFF40493A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353B31),
    surfaceContainer = Color(0xFF1C2119),
    surfaceContainerHigh = Color(0xFF262C23),
    surfaceContainerHighest = Color(0xFF31362D),
    surfaceContainerLow = Color(0xFF181D15),
    surfaceContainerLowest = Color(0xFF0B1008),
    surfaceDim = Color(0xFF10150D)
)

internal val MidnightGreenStatusLight = SynapseStatusScheme(
    success = Color(0xFF006D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFF8DF9A5),
    onSuccessContainer = Color(0xFF00210B),
    warning = Color(0xFF795900),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDF9F),
    onWarningContainer = Color(0xFF261A00)
)

internal val MidnightGreenStatusDark = SynapseStatusScheme(
    success = Color(0xFF71DC8B),
    onSuccess = Color(0xFF003917),
    successContainer = Color(0xFF005224),
    onSuccessContainer = Color(0xFF8DF9A5),
    warning = Color(0xFFF8BD26),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF5B4300),
    onWarningContainer = Color(0xFFFFDF9F)
)

internal val TacticalHUDLight = lightColorScheme(
    primary = Color(0xFF006B5D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF24FCDE),
    onPrimaryContainer = Color(0xFF007062),
    inversePrimary = Color(0xFF00DFC4),
    secondary = Color(0xFF29685D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFAFEFE0),
    onSecondaryContainer = Color(0xFF306F63),
    tertiary = Color(0xFF6F5D00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE055),
    onTertiaryContainer = Color(0xFF746200),
    background = Color(0xFFF2FBF7),
    onBackground = Color(0xFF151D1B),
    surface = Color(0xFFF2FBF7),
    onSurface = Color(0xFF151D1B),
    surfaceVariant = Color(0xFFD5E6E1),
    onSurfaceVariant = Color(0xFF3A4A46),
    surfaceTint = Color(0xFF006B5D),
    inverseSurface = Color(0xFF2A3230),
    inverseOnSurface = Color(0xFFEAF3EF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF6A7B76),
    outlineVariant = Color(0xFFB9CAC5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF2FBF7),
    surfaceContainer = Color(0xFFE7F0EC),
    surfaceContainerHigh = Color(0xFFE1EAE6),
    surfaceContainerHighest = Color(0xFFDBE5E1),
    surfaceContainerLow = Color(0xFFECF6F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD3DCD8)
)

internal val TacticalHUDDark = darkColorScheme(
    primary = Color(0xFFF9FFFC),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF24FCDE),
    onPrimaryContainer = Color(0xFF007062),
    inversePrimary = Color(0xFF006B5D),
    secondary = Color(0xFF94D3C4),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF0B5348),
    onSecondaryContainer = Color(0xFF86C4B6),
    tertiary = Color(0xFFFFFDFF),
    onTertiary = Color(0xFF3A3000),
    tertiaryContainer = Color(0xFFFFE055),
    onTertiaryContainer = Color(0xFF746200),
    background = Color(0xFF0D1513),
    onBackground = Color(0xFFDBE5E1),
    surface = Color(0xFF0D1513),
    onSurface = Color(0xFFDBE5E1),
    surfaceVariant = Color(0xFF3A4A46),
    onSurfaceVariant = Color(0xFFB9CAC5),
    surfaceTint = Color(0xFF00DFC4),
    inverseSurface = Color(0xFFDBE5E1),
    inverseOnSurface = Color(0xFF2A3230),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF84948F),
    outlineVariant = Color(0xFF3A4A46),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF323B39),
    surfaceContainer = Color(0xFF19211F),
    surfaceContainerHigh = Color(0xFF232C29),
    surfaceContainerHighest = Color(0xFF2E3734),
    surfaceContainerLow = Color(0xFF151D1B),
    surfaceContainerLowest = Color(0xFF08100E),
    surfaceDim = Color(0xFF0D1513)
)

internal val TacticalHUDStatusLight = SynapseStatusScheme(
    success = Color(0xFF006B54),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFF6DFAD0),
    onSuccessContainer = Color(0xFF002118),
    warning = Color(0xFF795900),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDF9F),
    onWarningContainer = Color(0xFF261A00)
)

internal val TacticalHUDStatusDark = SynapseStatusScheme(
    success = Color(0xFF4BDDB5),
    onSuccess = Color(0xFF00382A),
    successContainer = Color(0xFF00513F),
    onSuccessContainer = Color(0xFF6DFAD0),
    warning = Color(0xFFF8BD26),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF5B4300),
    onWarningContainer = Color(0xFFFFDF9F)
)

