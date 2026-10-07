package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.background
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
import com.algora.app.feature.analysis.tools.common.Bar
import com.algora.app.feature.analysis.tools.common.InstrumentedAlgos
import com.algora.app.feature.analysis.tools.common.MiniBarChart

private enum class ProfAlgo(val label: String, val accent: Color, val complexity: String) {
    LINEAR_SEARCH("Linear Search", SimColors.Blue, "O(n)"),
    INSERTION_SORT("Insertion Sort", SimColors.Amber, "O(n²)"),
    BUBBLE_SORT("Bubble Sort", SimColors.Red, "O(n²)"),
}

// Worst-case run for the chosen algo at size n — the count is what the real code executed.
private fun profile(algo: ProfAlgo, n: Int): Int = when (algo) {
    ProfAlgo.LINEAR_SEARCH -> InstrumentedAlgos.linearSearch(IntArray(n) { it }, -1)
    ProfAlgo.INSERTION_SORT -> InstrumentedAlgos.insertionSort(IntArray(n) { n - 1 - it })
    ProfAlgo.BUBBLE_SORT -> InstrumentedAlgos.bubbleSort(IntArray(n) { n - 1 - it })
}

@Composable
fun CostProfilerTool() {
    var algo by remember { mutableStateOf(ProfAlgo.INSERTION_SORT) }
    var maxN by remember { mutableStateOf(32f) }
    val maxNInt = maxN.toInt().coerceAtLeast(8)

    AnalysisToolCard(
        intro = "Sweeps input size and runs the real algorithm at each step, plotting the operations " +
            "it actually executed. The bar shape is the algorithm's growth curve, measured — not drawn.",
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            ProfAlgo.entries.forEachIndexed { index, option ->
                if (index > 0) Box(modifier = Modifier.padding(start = 8.dp))
                val selected = algo == option
                if (selected) {
                    Button(
                        onClick = { algo = option },
                        colors = ButtonDefaults.buttonColors(containerColor = option.accent, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) { Text(option.label, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { algo = option }, modifier = Modifier.weight(1f)) { Text(option.label, maxLines = 1) }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) {
            Text(
                "Max n = $maxNInt · ${algo.complexity}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = maxN, onValueChange = { maxN = it }, valueRange = 8f..64f, steps = 6)
        }

        // Eight real runs per sweep — keyed so dragging the slider doesn't redo them every frame.
        val points = remember(algo, maxNInt) {
            val step = (maxNInt / 8).coerceAtLeast(1)
            (step..maxNInt step step).map { it to profile(algo, it) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(6.dp),
        ) {
            MiniBarChart(bars = points.map { Bar(it.first.toString(), it.second.toDouble(), algo.accent) })
        }

        Text(
            "Measured operations",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            points.forEach { (n, ops) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "n = $n",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$ops ops",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
