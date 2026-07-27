package com.algora.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// Brand
val Indigo = Color(0xFF4F46E5)
val Blue = Color(0xFF3B82F6)
val Violet = Color(0xFF7C3AED)

// Category accent palette (design mock, docs/design/Algora.dc.html)
object CategoryAccents {
    val Purple = Color(0xFF8B5CF6)
    val Green = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Pink = Color(0xFFEC4899)
    val DarkGreen = Color(0xFF16A34A)
    val Orange = Color(0xFFF97316)
    val Blue = Color(0xFF6366F1)
    val LightBlue = Color(0xFF3B82F6)

    val all = listOf(Purple, Green, Amber, Pink, DarkGreen, Orange, Blue, LightBlue)
}

// Simulation button/action palette (design mock's btnBlue/btnViolet/btnRed/btnAmber/btnGrey/btnGreen)
object SimColors {
    val Blue = Color(0xFF3B82F6)
    val Violet = Color(0xFF8B5CF6)
    val Red = Color(0xFFEF4444)
    val Amber = Color(0xFFF59E0B)
    val Grey = Color(0xFF94A3B8)
    val Green = Color(0xFF16A34A)
}

// Named gradients from the design mock's `grads` map + hero/topbar gradients.
object Gradients {
    // Brand tile (also the launcher icon): indigo -> violet -> fuchsia.
    val Brand = listOf(Color(0xFF6366F1), Color(0xFF7C3AED), Color(0xFFC026D3))

    val Green = listOf(Color(0xFF34D399), Color(0xFF059669))
    val Blue = listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
    val Amber = listOf(Color(0xFFFBBF24), Color(0xFFF97316))
    val Violet = listOf(Color(0xFFC084FC), Color(0xFF7C3AED))
    val Indigo = listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
    val Pink = listOf(Color(0xFFF472B6), Color(0xFFDB2777))
    val Teal = listOf(Color(0xFF5EEAD4), Color(0xFF0D9488))
    val Orange = listOf(Color(0xFFFDBA74), Color(0xFFEA580C))

    // Paywall chrome from the mock's isPremium block: the CTA/upsell button uses the two-stop
    // 100deg ramp, the hero card the three-stop 150deg one (#4f46e5 → #7c3aed 55% → #c026d3).
    val PremiumCta = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
    val PremiumHero = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFC026D3))

    val TopbarDsa = listOf(Color(0xFF4F46E5), Color(0xFF6D28D9))
    val TopbarAi = listOf(Color(0xFFDB2777), Color(0xFFF97316))
    val FeaturedDsa = listOf(Color(0xFF7C3AED), Color(0xFF4338CA))
    val FeaturedAi = listOf(Color(0xFF4F46E5), Color(0xFF0EA5E9))
}

// Light neutrals
val LightBackground = Color(0xFFF7F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightMutedText = Color(0xFF6B7280)
val LightBorder = Color(0xFFE8EAEF)
val LightBorderSubtle = Color(0xFFEEF0F4)

// Dark neutrals
val DarkBackground = Color(0xFF161A22)
val DarkSurface = Color(0xFF1C212B)
val DarkMutedText = Color(0xFF9AA1B1)
val DarkBorder = Color(0xFF262B36)
