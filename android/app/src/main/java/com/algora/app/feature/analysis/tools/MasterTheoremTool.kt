package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

// Simplified master theorem for T(n) = a·T(n/b) + Θ(n^d).
// Compare d against log_b(a): d < → case 1, d = → case 2, d > → case 3.
private data class Preset(val label: String, val a: Int, val b: Int, val d: Int)

private val presets = listOf(
    Preset("Merge sort", 2, 2, 1),
    Preset("Binary search", 1, 2, 0),
    Preset("Karatsuba", 3, 2, 1),
    Preset("Naive matrix mult", 8, 2, 2),
)

// Polynomial term for an exponent: n^0 → "1", n^1 → "n", else "n^k".
private fun poly(exp: Double): String {
    val rounded = Math.round(exp).toDouble()
    val label = if (abs(exp - rounded) < 1e-9) rounded.toInt().toString() else String.format(Locale.US, "%.2f", exp)
    return when (label) {
        "0" -> "1"
        "1" -> "n"
        else -> "n^$label"
    }
}

@Composable
fun MasterTheoremTool() {
    var a by remember { mutableStateOf(2) }
    var b by remember { mutableStateOf(2) }
    var d by remember { mutableStateOf(1) }

    val logBA = ln(a.toDouble()) / ln(b.toDouble())
    val (caseNum, caseDesc, result) = when {
        d < logBA - 1e-9 -> Triple(1, "d < log_b(a) — the leaves dominate.", "Θ(${poly(logBA)})")
        d > logBA + 1e-9 -> Triple(3, "d > log_b(a) — the root work dominates.", "Θ(${poly(d.toDouble())})")
        else -> {
            val p = poly(d.toDouble())
            Triple(
                2,
                "d = log_b(a) — work is even across levels.",
                if (p == "1") "Θ(log n)" else "Θ($p log n)",
            )
        }
    }

    AnalysisToolCard(
        intro = "Solves divide-and-conquer recurrences of the form T(n) = a·T(n/b) + Θ(n^d). " +
            "Set a, b, d and it picks the case by comparing d to log_b(a).",
    ) {
        Text(
            "T(n) = a·T(n/b) + Θ(n^d)",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        )

        Stepper("a — subproblems", a, 1, 16) { a = it }
        Stepper("b — shrink factor", b, 2, 16) { b = it }
        Stepper("d — exponent of f(n)", d, 0, 5) { d = it }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SimColors.Blue.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "log_b(a) = ${String.format(Locale.US, "%.3f", logBA)}   vs   d = $d",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    result,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = SimColors.Blue,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Case $caseNum · $caseDesc",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Text(
            "Examples",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.take(2).forEach { preset ->
                OutlinedButton(
                    onClick = { a = preset.a; b = preset.b; d = preset.d },
                    modifier = Modifier.weight(1f),
                ) { Text(preset.label, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.drop(2).forEach { preset ->
                OutlinedButton(
                    onClick = { a = preset.a; b = preset.b; d = preset.d },
                    modifier = Modifier.weight(1f),
                ) { Text(preset.label, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (value > min) onChange(value - 1) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) { Text("−") }
        Box(modifier = Modifier.size(width = 40.dp, height = 40.dp), contentAlignment = Alignment.Center) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = { if (value < max) onChange(value + 1) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) { Text("+") }
    }
}
