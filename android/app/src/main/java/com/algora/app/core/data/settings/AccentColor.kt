package com.algora.app.core.data.settings

/**
 * User-selectable accent, matching the `accent` prop options in docs/design/Algora.dc.html
 * (#4f46e5, #7c3aed, #0ea5e9, #059669, #db2777).
 *
 * Colors are plain ARGB longs so the data layer stays free of Compose types; the theme wraps them.
 * [gradientEnd] is the mock's `accent2` role — the second stop of accent-tinted gradients.
 */
enum class AccentColor(
    val id: String,
    val label: String,
    val argb: Long,
    val gradientEnd: Long,
    val topbarStart: Long = argb,
    val topbarEnd: Long = gradientEnd,
) {
    // INDIGO and PINK carry the mock's own topbar ramps (`TopbarDsa` / `TopbarAi`) verbatim, because
    // those two are what the Auto setting resolves to — so Auto reproduces the mock exactly, and a
    // deliberately picked accent gets a header derived from that accent instead of a stale indigo.
    INDIGO("indigo", "Indigo", 0xFF4F46E5, 0xFF7C3AED, topbarEnd = 0xFF6D28D9),
    VIOLET("violet", "Violet", 0xFF7C3AED, 0xFFA78BFA),
    SKY("sky", "Sky", 0xFF0EA5E9, 0xFF4F46E5),
    EMERALD("emerald", "Emerald", 0xFF059669, 0xFF34D399),
    PINK("pink", "Pink", 0xFFDB2777, 0xFFF97316);

    companion object {
        val DEFAULT = INDIGO

        /**
         * Stored id for "let the accent follow the active [com.algora.app.core.nav.AppMode]".
         * Not an enum entry — it resolves to a real accent only once a mode is known, so the data
         * layer represents it as a null [AccentColor] rather than a sixth colour.
         */
        const val AUTO_ID = "auto"

        fun fromId(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
