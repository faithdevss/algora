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
) {
    INDIGO("indigo", "Indigo", 0xFF4F46E5, 0xFF7C3AED),
    VIOLET("violet", "Violet", 0xFF7C3AED, 0xFFA78BFA),
    SKY("sky", "Sky", 0xFF0EA5E9, 0xFF4F46E5),
    EMERALD("emerald", "Emerald", 0xFF059669, 0xFF34D399),
    PINK("pink", "Pink", 0xFFDB2777, 0xFFF97316);

    companion object {
        val DEFAULT = INDIGO

        fun fromId(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
