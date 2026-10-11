package com.algora.app.core.data.settings

/**
 * The app themes from docs/Icon and Themes (standalone).html, minus Violet (Ink is the default).
 * A theme is a palette *and* the launcher icon that goes with it, so picking one in Settings
 * recolours the app and swaps the icon together (see `AppIconSwitcher`).
 *
 * Colours are plain ARGB longs so the data layer stays free of Compose types; the theme wraps them.
 * [dark] is the doc's palette verbatim. [light] is what the same theme wears when the user forces
 * (or the system asks for) light mode: the doc only designs Paper as a light theme, so the others
 * keep the app's neutral light surfaces and swap in a [light] primary dark enough to carry white text.
 */
enum class AppTheme(
    val id: String,
    val label: String,
    /** The mode the doc shows this theme in; picking the theme moves the mode there. */
    val preferredMode: ThemeMode,
    val dark: ThemeColors,
    val light: ThemeColors,
    /** Home header ramp (white text sits on it), which is the icon gradient where one reads well. */
    val headerStart: Long,
    val headerEnd: Long,
    /** Home Featured Lab card ramp: a deeper cut of the theme so it reads apart from the header. */
    val featuredStart: Long,
    val featuredEnd: Long,
    val icon: ThemeIcon,
) {
    INK(
        "ink", "Ink", ThemeMode.DARK,
        dark = ThemeColors(0xFF0C0E13, 0xFF171A22, 0xFFB3ABFF, 0xFF8F84FF, 0xFFF2F3F7, 0xFF8A90A0, 0xFF12151C),
        light = ThemeColors.neutralLight(primary = 0xFF5B4DF0, accent = 0xFF8B3FF0),
        headerStart = 0xFF5B4DF0, headerEnd = 0xFF8B3FF0,
        featuredStart = 0xFF4C3FD6, featuredEnd = 0xFF231E5C,
        icon = ThemeIcon(0xFF1C202B, 0xFF0C0E13, glyph = 0xFFB3ABFF, apex = 0xFFB3ABFF, sheen = 0.06f),
    ),
    PAPER(
        "paper", "Paper", ThemeMode.LIGHT,
        // Warm cream paper with a brick-red primary and apricot accent, so it reads apart from Ink's
        // violet. Dark mode keeps the warmth: espresso surfaces and a lighter coral primary.
        dark = ThemeColors(0xFF14100D, 0xFF211A15, 0xFFF0766F, 0xFFF4A37F, 0xFFF7F0EA, 0xFFA8998E, 0xFF1D1714),
        light = ThemeColors(0xFFF8F4EE, 0xFFFFFFFF, 0xFFD63A3F, 0xFFF08A5D, 0xFF1D1714, 0xFF6B615A, 0xFFFFFFFF),
        headerStart = 0xFFD63A3F, headerEnd = 0xFFF08A5D,
        featuredStart = 0xFFD63A3F, featuredEnd = 0xFF8E2328,
        icon = ThemeIcon(0xFFFFFFFF, 0xFFF3E9E1, glyph = 0xFFD63A3F, apex = 0xFFD63A3F, sheen = 0f),
    ),
    MINT(
        "mint", "Mint", ThemeMode.DARK,
        dark = ThemeColors(0xFF0B1A16, 0xFF13261F, 0xFF27C486, 0xFF7FE0B6, 0xFFEEFAF4, 0xFF8FB3A5, 0xFF06221A),
        light = ThemeColors.neutralLight(primary = 0xFF0F9C68, accent = 0xFF27C486),
        headerStart = 0xFF27C486, headerEnd = 0xFF0F9C68,
        featuredStart = 0xFF0F9C68, featuredEnd = 0xFF065F46,
        icon = ThemeIcon(0xFF27C486, 0xFF0F9C68, glyph = 0xFFFFFFFF, apex = 0xFFFFFFFF),
    ),
    OCEAN(
        "ocean", "Ocean", ThemeMode.DARK,
        dark = ThemeColors(0xFF0B1324, 0xFF142036, 0xFF4F94FF, 0xFF93BCFF, 0xFFEEF3FF, 0xFF91A0BD, 0xFF06132B),
        light = ThemeColors.neutralLight(primary = 0xFF2563EB, accent = 0xFF4F94FF),
        headerStart = 0xFF4F94FF, headerEnd = 0xFF2563EB,
        featuredStart = 0xFF2563EB, featuredEnd = 0xFF1E3A8A,
        icon = ThemeIcon(0xFF4F94FF, 0xFF2563EB, glyph = 0xFFFFFFFF, apex = 0xFFFFFFFF),
    ),
    SKY(
        "sky", "Sky", ThemeMode.DARK,
        dark = ThemeColors(0xFF08161F, 0xFF10242F, 0xFF1BA1E6, 0xFF7CD0F7, 0xFFEEF8FD, 0xFF8EAEBF, 0xFF04131C),
        light = ThemeColors.neutralLight(primary = 0xFF1497DB, accent = 0xFF3DB3F0),
        headerStart = 0xFF3DB3F0, headerEnd = 0xFF1497DB,
        featuredStart = 0xFF1497DB, featuredEnd = 0xFF075985,
        icon = ThemeIcon(0xFF3DB3F0, 0xFF1497DB, glyph = 0xFFFFFFFF, apex = 0xFFFFFFFF),
    );

    companion object {
        val DEFAULT = INK

        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The doc's six palette roles plus the colour for text on a primary-filled button. */
data class ThemeColors(
    val background: Long,
    val surface: Long,
    val primary: Long,
    val accent: Long,
    val text: Long,
    val muted: Long,
    val onPrimary: Long,
) {
    companion object {
        /** The app's standing light neutrals, wearing a theme's own primary and accent. */
        fun neutralLight(primary: Long, accent: Long) =
            ThemeColors(0xFFF7F8FA, 0xFFFFFFFF, primary, accent, 0xFF161A22, 0xFF6B7280, 0xFFFFFFFF)
    }
}

/** The launcher tile: a 145° gradient, the five-node glyph and its apex node. */
data class ThemeIcon(
    val bgStart: Long,
    val bgEnd: Long,
    val glyph: Long,
    val apex: Long,
    val sheen: Float = 0.28f,
)
