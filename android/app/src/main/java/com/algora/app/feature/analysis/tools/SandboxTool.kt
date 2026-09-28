package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import kotlin.random.Random

private enum class SandAlgo(val label: String, val short: String) {
    LINEAR_SEARCH("Linear Search", "lin"),
    INSERTION_SORT("Insertion Sort", "ins"),
    BUBBLE_SORT("Bubble Sort", "bub"),
    QUICKSORT("Quicksort", "qck"),
}

private enum class Shape(val label: String) { SORTED("Sorted"), REVERSED("Reversed"), RANDOM("Random") }

private data class SandRun(val algo: SandAlgo, val shape: Shape, val n: Int, val ops: Int)

private fun buildArray(shape: Shape, n: Int): IntArray = when (shape) {
    Shape.SORTED -> IntArray(n) { it }
    Shape.REVERSED -> IntArray(n) { n - 1 - it }
    Shape.RANDOM -> (0 until n).shuffled(Random(n * 31 + 7)).toIntArray()
}

private fun run(algo: SandAlgo, shape: Shape, n: Int): Int {
    val arr = buildArray(shape, n)
    return when (algo) {
        SandAlgo.LINEAR_SEARCH -> InstrumentedAlgos.linearSearch(arr, -1)
        SandAlgo.INSERTION_SORT -> InstrumentedAlgos.insertionSort(arr)
        SandAlgo.BUBBLE_SORT -> InstrumentedAlgos.bubbleSort(arr)
        SandAlgo.QUICKSORT -> InstrumentedAlgos.quicksort(arr)
    }
}

@Composable
fun SandboxTool() {
    var algo by remember { mutableStateOf(SandAlgo.INSERTION_SORT) }
    var shape by remember { mutableStateOf(Shape.REVERSED) }
    var n by remember { mutableStateOf(16f) }
    val nInt = n.toInt()
    val runs = remember { mutableStateListOf<SandRun>() }

    AnalysisToolCard(
        intro = "Free-form playground. Pick an algorithm, an input shape, and a size, then run it — " +
            "each run's real operation count stacks onto the chart so you can compare anything against " +
            "anything.",
    ) {
        // Algorithm chips.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp),
        ) {
            SandAlgo.entries.forEachIndexed { index, option ->
                if (index > 0) Box(modifier = Modifier.padding(start = 8.dp))
                val selected = algo == option
                if (selected) {
                    Button(
                        onClick = { algo = option },
                        colors = ButtonDefaults.buttonColors(containerColor = SimColors.Blue, contentColor = Color.White),
                    ) { Text(option.label, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { algo = option }) { Text(option.label, maxLines = 1) }
                }
            }
        }

        // Input-shape chips.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Shape.entries.forEachIndexed { index, option ->
                if (index > 0) Box(modifier = Modifier.padding(start = 8.dp))
                val selected = shape == option
                if (selected) {
                    Button(
                        onClick = { shape = option },
                        colors = ButtonDefaults.buttonColors(containerColor = SimColors.Violet, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) { Text(option.label, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { shape = option }, modifier = Modifier.weight(1f)) { Text(option.label, maxLines = 1) }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                "Input size · n = $nInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = n, onValueChange = { n = it }, valueRange = 4f..48f, steps = 43)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    runs.add(0, SandRun(algo, shape, nInt, run(algo, shape, nInt)))
                    while (runs.size > 8) runs.removeAt(runs.lastIndex)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SimColors.Green, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) { Text("▸ Run") }
            if (runs.isNotEmpty()) {
                TextButton(onClick = { runs.clear() }) { Text("Clear") }
            }
        }

        if (runs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(6.dp),
            ) {
                MiniBarChart(bars = runs.asReversed().map { Bar(it.n.toString(), it.ops.toDouble(), SimColors.Green) })
            }

            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                runs.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${r.algo.short} · ${r.shape.label.lowercase()} · n=${r.n}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${r.ops} ops",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
