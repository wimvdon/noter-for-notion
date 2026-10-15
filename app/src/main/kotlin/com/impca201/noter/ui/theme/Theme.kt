package com.impca201.noter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.impca201.noter.data.model.ThemeMode

// Fallback palette (dynamic color off): Material 3 tonal spot scheme from the icon's middle gradient
// stop, sticky-note amber #F5A623.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF7E571E),
        onPrimary = Color(0xFFFFF7F3),
        primaryContainer = Color(0xFFFAC681),
        onPrimaryContainer = Color(0xFF613E04),
        secondary = Color(0xFF705B41),
        onSecondary = Color(0xFFFFF7F3),
        secondaryContainer = Color(0xFFFBDEBC),
        onSecondaryContainer = Color(0xFF624E35),
        tertiary = Color(0xFF86523B),
        onTertiary = Color(0xFFFFF7F5),
        tertiaryContainer = Color(0xFFFEB99D),
        onTertiaryContainer = Color(0xFF643621),
        error = Color(0xFFA73B21),
        onError = Color(0xFFFFF7F6),
        errorContainer = Color(0xFFFD795A),
        onErrorContainer = Color(0xFF6E1400),
        background = Color(0xFFFFF8F4),
        onBackground = Color(0xFF3B3125),
        surface = Color(0xFFFFF8F4),
        onSurface = Color(0xFF3B3125),
        surfaceVariant = Color(0xFFF1E0CE),
        onSurfaceVariant = Color(0xFF695D4F),
        outline = Color(0xFF86796A),
        outlineVariant = Color(0xFFBFAF9F),
        inverseSurface = Color(0xFF120D08),
        inverseOnSurface = Color(0xFFA59B92),
        inversePrimary = Color(0xFFFAC681),
        surfaceDim = Color(0xFFE8D7C6),
        surfaceBright = Color(0xFFFFF8F4),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF1E4),
        surfaceContainer = Color(0xFFFAEBDD),
        surfaceContainerHigh = Color(0xFFF5E6D6),
        surfaceContainerHighest = Color(0xFFF1E0CE),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFEABF89),
        onPrimary = Color(0xFF573B10),
        primaryContainer = Color(0xFF6B4D21),
        onPrimaryContainer = Color(0xFFFFDEB6),
        secondary = Color(0xFFDEC2A1),
        onSecondary = Color(0xFF4F3D25),
        secondaryContainer = Color(0xFF4A3820),
        onSecondaryContainer = Color(0xFFD6BB9B),
        tertiary = Color(0xFFFFCCB8),
        onTertiary = Color(0xFF6F3E29),
        tertiaryContainer = Color(0xFFFEB99D),
        onTertiaryContainer = Color(0xFF643621),
        error = Color(0xFFF97758),
        onError = Color(0xFF450900),
        errorContainer = Color(0xFF85230A),
        onErrorContainer = Color(0xFFFF9B82),
        background = Color(0xFF120D08),
        onBackground = Color(0xFFF4E3D1),
        surface = Color(0xFF120D08),
        onSurface = Color(0xFFF4E3D1),
        surfaceVariant = Color(0xFF2D2419),
        onSurfaceVariant = Color(0xFFB8A999),
        outline = Color(0xFF807365),
        outlineVariant = Color(0xFF514639),
        inverseSurface = Color(0xFFFFF8F4),
        inverseOnSurface = Color(0xFF5B544C),
        inversePrimary = Color(0xFF79592C),
        surfaceDim = Color(0xFF120D08),
        surfaceBright = Color(0xFF342B1F),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF18120B),
        surfaceContainer = Color(0xFF1F1810),
        surfaceContainerHigh = Color(0xFF261E15),
        surfaceContainerHighest = Color(0xFF2D2419),
    )
/** True when the phone's theme (or the user's choice) is dark. */
@Composable
fun isAppInDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(themeMode)
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    SystemBarsFollowTheme(dark)
    MaterialTheme(colorScheme = colorScheme, content = content)
}
