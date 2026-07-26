package com.algora.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.algora.app.core.data.settings.AccentColor

private fun lightColors(accent: Color, accent2: Color) = lightColorScheme(
    primary = accent,
    onPrimary = LightSurface,
    secondary = Blue,
    onSecondary = LightSurface,
    tertiary = accent2,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = Color(0xFF161A22),
    surface = LightSurface,
    onSurface = Color(0xFF161A22),
    onSurfaceVariant = LightMutedText,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
)

private fun darkColors(accent: Color, accent2: Color) = darkColorScheme(
    primary = accent,
    onPrimary = DarkBackground,
    secondary = Blue,
    onSecondary = DarkBackground,
    tertiary = accent2,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = LightSurface,
    surface = DarkSurface,
    onSurface = LightSurface,
    onSurfaceVariant = DarkMutedText,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
)

val LocalCategoryAccents = staticCompositionLocalOf { CategoryAccents.all }

/**
 * Effective dark state. Reads the user's theme override, not just the system setting, so screens
 * that tune alpha/backgrounds per mode stay in sync with a manual toggle.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The user's chosen accent, for surfaces that need the raw pair rather than the M3 roles. */
val LocalAccent = staticCompositionLocalOf { AccentColor.DEFAULT }

@Composable
fun AlgoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.DEFAULT,
    content: @Composable () -> Unit,
) {
    val accentColor = Color(accent.argb)
    val accent2 = Color(accent.gradientEnd)
    val colorScheme = if (darkTheme) darkColors(accentColor, accent2) else lightColors(accentColor, accent2)

    CompositionLocalProvider(
        LocalCategoryAccents provides CategoryAccents.all,
        LocalDarkTheme provides darkTheme,
        LocalAccent provides accent,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlgoraTypography,
            content = content,
        )
    }
}
