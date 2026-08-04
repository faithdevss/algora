package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import com.algora.app.feature.analysis.tools.common.InstrumentedAlgos

@Composable
fun SpaceTimeTradeoffTool() {
    var n by remember { mutableStateOf(24f) }
    val nInt = n.toInt()

    // Absent target → both approaches run their full worst case, exposing the tradeoff cleanly.
    // Keyed on n so a slider drag doesn't re-run the O(n²) pass on every frame.
    val (bruteOps, hashOps, hashMem) = remember(nInt) {
        val array = IntArray(nInt) { it }
        val (ops, mem) = InstrumentedAlgos.twoSumHash(array, -1)
        Triple(InstrumentedAlgos.twoSumBrute(array, -1), ops, mem)
    }

    AnalysisToolCard(
        intro = "Two ways to solve two-sum. Brute force checks every pair — O(n²) time, O(1) extra " +
            "space. The hash approach trades memory for speed — O(n) time, O(n) extra space. Same " +
            "problem, opposite ends of the space-time tradeoff.",
    ) {
        Column(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            Text(
                "Array size · n = $nInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = n, onValueChange = { n = it }, valueRange = 8f..48f, steps = 39)
        }

        ApproachCard(
            name = "Brute force",
            accent = SimColors.Red,
            timeLabel = "$bruteOps pair checks",
            timeComplexity = "O(n²) time",
            spaceLabel = "0 extra cells",
            spaceComplexity = "O(1) space",
        )
        ApproachCard(
            name = "Hash set",
            accent = SimColors.Green,
            timeLabel = "$hashOps lookups",
            timeComplexity = "O(n) time",
            spaceLabel = "$hashMem extra cells",
            spaceComplexity = "O(n) space",
            modifier = Modifier.padding(top = 10.dp),
        )

        Text(
            "At n = $nInt the hash approach does ${bruteOps - hashOps} fewer operations, paying " +
                "$hashMem cells of memory for it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ApproachCard(
    name: String,
    accent: Color,
    timeLabel: String,
    timeComplexity: String,
    spaceLabel: String,
    spaceComplexity: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.08f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = accent)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Metric(label = "Time", value = timeLabel, complexity = timeComplexity, modifier = Modifier.weight(1f))
                Metric(label = "Space", value = spaceLabel, complexity = spaceComplexity, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, complexity: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(complexity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
