package com.algora.app.feature.analysis.tools.common

import androidx.compose.ui.graphics.Color
import kotlin.math.ln
import com.algora.app.core.ui.theme.SimColors

// A labeled, colored function of n — the shared unit consumed by the growth/complexity tools and
// MiniLineChart. Colors follow the app's simulation palette (blue/green/amber/violet/red).
class Curve(val label: String, val color: Color, val fn: (Double) -> Double)

// Base-2, matching how the rest of the app teaches logarithmic work (binary search halves the
// range, so n = 16 costs 4). The tools print these values as live "≈ x ops" legends, so the base
// has to agree with OperationCounterTool's real measured counts — a natural log would read 3.
private fun log2(n: Double): Double = ln(n) / ln(2.0)

// The five complexity classes shown throughout the app.
val complexityCurves = listOf(
    Curve("O(1)", SimColors.Blue) { 1.0 },
    Curve("O(log n)", Color(0xFF10B981)) { n -> log2(n) },
    Curve("O(n)", SimColors.Amber) { n -> n },
    Curve("O(n log n)", SimColors.Violet) { n -> n * log2(n) },
    Curve("O(n²)", SimColors.Red) { n -> n * n },
)
