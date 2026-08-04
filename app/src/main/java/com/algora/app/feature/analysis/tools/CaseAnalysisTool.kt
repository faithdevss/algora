package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import com.algora.app.feature.analysis.tools.common.InstrumentedAlgos
import kotlin.random.Random

enum class CaseMode(
    val label: String,
    val accent: Color,
    val blurb: String,
    val formula: String,
) {
    BEST("Best", SimColors.Green, "Already-sorted input — the inner loop breaks on its first check.", "≈ n − 1 comparisons"),
    WORST("Worst", SimColors.Red, "Reverse-sorted input — every element shifts all the way left.", "≈ n(n − 1) / 2 comparisons"),
    AVERAGE("Average", SimColors.Amber, "Randomly shuffled input — expected halfway shifting.", "≈ n² / 4 comparisons"),
}

// Deterministic input for each case, so the reported comparison count is reproducible.
private fun buildInput(mode: CaseMode, n: Int): IntArray = when (mode) {
    CaseMode.BEST -> IntArray(n) { it }
    CaseMode.WORST -> IntArray(n) { n - 1 - it }
    CaseMode.AVERAGE -> (0 until n).shuffled(Random(n * 31 + 7)).toIntArray()
}

private fun comparisonsFor(mode: CaseMode, n: Int): Int =
    InstrumentedAlgos.insertionSort(buildInput(mode, n))

@Composable
fun CaseAnalysisTool(defaultMode: CaseMode) {
    var mode by remember { mutableStateOf(defaultMode) }
    var n by remember { mutableStateOf(8f) }
    val nInt = n.toInt()

    AnalysisToolCard(
        intro = "Insertion sort runs on a crafted input for each case. The comparison count is what " +
            "the real sort executed — sorted input is fastest, reverse-sorted is the true worst case.",
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            CaseMode.entries.forEachIndexed { index, option ->
                if (index > 0) Box(modifier = Modifier.padding(start = 8.dp))
                val selected = mode == option
                if (selected) {
                    Button(
                        onClick = { mode = option },
                        colors = ButtonDefaults.buttonColors(containerColor = option.accent, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) { Text(option.label) }
                } else {
                    OutlinedButton(onClick = { mode = option }, modifier = Modifier.weight(1f)) { Text(option.label) }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                "Array size · n = $nInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = n, onValueChange = { n = it }, valueRange = 4f..32f, steps = 27)
        }

        // Sort once per n, not once per frame — the panel below shows all three cases anyway, and a
        // slider drag would otherwise re-run four sorts on every recomposition.
        val counts = remember(nInt) { CaseMode.entries.associateWith { comparisonsFor(it, nInt) } }
        val comparisons = counts.getValue(mode)
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = comparisons.toString(),
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = mode.accent,
            )
            Text(
                "comparisons · ${mode.label} case",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                mode.formula,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                mode.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // All three cases at the current n, so the contrast is visible at a glance.
        Text(
            "Same n = $nInt, all three cases",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CaseMode.entries.forEach { option ->
                val count = counts.getValue(option)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = option.accent.copy(alpha = if (option == mode) 0.16f else 0.07f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${option.label} case",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$count comparisons",
                            style = MaterialTheme.typography.bodyMedium,
                            color = option.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
