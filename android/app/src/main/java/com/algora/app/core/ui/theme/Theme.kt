package com.algora.app.core.ui.theme

import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.algora.app.core.data.settings.AppTheme

private val themeSpec = tween<Color>(durationMillis = 320)

private fun colorScheme(
    background: Color,
    surface: Color,
    primary: Color,
    accent: Color,
    text: Color,
    muted: Color,
    onPrimary: Color,
    dark: Boolean,
): ColorScheme {
    // Hairlines are the surface nudged toward the text colour, so each theme's borders stay tinted
    // to its own surfaces instead of a fixed grey.
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        secondary = Blue,
        onSecondary = onPrimary,
        tertiary = accent,
        onTertiary = onPrimary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        onSurfaceVariant = muted,
        outline = lerp(surface, text, if (dark) 0.10f else 0.08f),
        outlineVariant = lerp(surface, text, if (dark) 0.10f else 0.05f),
    )
}

val LocalCategoryAccents = staticCompositionLocalOf { CategoryAccents.all }

/**
 * Effective dark state. Reads the user's theme override, not just the system setting, so screens
 * that tune alpha/backgrounds per mode stay in sync with a manual toggle.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The theme in effect, for surfaces that need more than the M3 roles (the Home header ramp). */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.DEFAULT }

@Composable
fun AlgoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: AppTheme = AppTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    // Every role is animated so picking a theme slides the whole shell across instead of snapping
    // it a frame after the header gradient has already crossfaded.
    val target = if (darkTheme) theme.dark else theme.light
    val background by animateColorAsState(Color(target.background), themeSpec, label = "background")
    val surface by animateColorAsState(Color(target.surface), themeSpec, label = "surface")
    val primary by animateColorAsState(Color(target.primary), themeSpec, label = "primary")
    val accent by animateColorAsState(Color(target.accent), themeSpec, label = "accent")
    val text by animateColorAsState(Color(target.text), themeSpec, label = "text")
    val muted by animateColorAsState(Color(target.muted), themeSpec, label = "muted")
    val onPrimary by animateColorAsState(Color(target.onPrimary), themeSpec, label = "onPrimary")
    val scheme = colorScheme(background, surface, primary, accent, text, muted, onPrimary, darkTheme)

    CompositionLocalProvider(
        LocalCategoryAccents provides CategoryAccents.all,
        LocalDarkTheme provides darkTheme,
        LocalAppTheme provides theme,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AlgoraTypography,
            content = content,
        )
    }
}
