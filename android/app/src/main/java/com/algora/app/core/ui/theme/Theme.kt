package com.algora.app.core.ui.theme

import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.algora.app.core.data.settings.AccentColor

private val accentSpec = tween<Color>(durationMillis = 320)

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

/**
 * The deeper accents (indigo, violet, pink) sink into the dark surfaces as text and icons, so the dark
 * scheme's `primary` is the accent eased toward white. Gradients and swatches keep the raw accent.
 */
private fun onDarkAccent(accent: Color) = lerp(accent, Color.White, 0.25f)

private fun darkColors(accent: Color, accent2: Color) = darkColorScheme(
    primary = onDarkAccent(accent),
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

/**
 * The accent in effect, for surfaces that need the raw pair rather than the M3 roles. Already
 * resolved: on the Auto setting this is the active mode's accent, not the user's stored choice.
 */
val LocalAccent = staticCompositionLocalOf { AccentColor.DEFAULT }

@Composable
fun AlgoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.DEFAULT,
    content: @Composable () -> Unit,
) {
    // Animated so flipping DSA ↔ AI (or picking a swatch) slides the whole shell to the new accent
    // instead of snapping it a frame after the header gradient has already crossfaded.
    val accentColor by animateColorAsState(Color(accent.argb), accentSpec, label = "accent")
    val accent2 by animateColorAsState(Color(accent.gradientEnd), accentSpec, label = "accent2")
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
