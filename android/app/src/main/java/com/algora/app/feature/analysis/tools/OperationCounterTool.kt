package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import com.algora.app.feature.analysis.tools.common.InstrumentedAlgos

private enum class SearchAlgo(val label: String, val complexityLabel: String) {
    LINEAR("Linear Search", "≈ O(n) comparisons, worst-case"),
    BINARY("Binary Search", "≈ O(log n) comparisons, worst-case"),
}

private data class RunResult(val n: Int, val comparisons: Int)

@Composable
fun OperationCounterTool() {
    var algo by remember { mutableStateOf(SearchAlgo.LINEAR) }
    var n by remember { mutableStateOf(16f) }
    var lastComparisons by remember { mutableStateOf<Int?>(null) }
    val history = remember { mutableStateListOf<RunResult>() }

    AnalysisToolCard(
        intro = "Runs real Kotlin search code on a sorted array and counts every comparison it " +
            "makes — not an estimate.",
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            SearchAlgo.entries.forEachIndexed { index, option ->
                if (index > 0) Box(modifier = Modifier.padding(start = 8.dp))
                val selected = algo == option
                if (selected) {
                    Button(
                        onClick = { algo = option },
                        colors = ButtonDefaults.buttonColors(containerColor = SimColors.Blue, contentColor = Color.White),
                        modifier = Modifier.weight(1f),
                    ) { Text(option.label) }
                } else {
                    OutlinedButton(onClick = { algo = option }, modifier = Modifier.weight(1f)) { Text(option.label) }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                "Array size · n = ${n.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = n, onValueChange = { n = it }, valueRange = 4f..64f, steps = 59)
        }

        Button(
            onClick = {
                val size = n.toInt()
                val sortedArray = IntArray(size) { it * 2 }
                val missingTarget = -1
                val comparisons = when (algo) {
                    SearchAlgo.LINEAR -> InstrumentedAlgos.linearSearch(sortedArray, missingTarget)
                    SearchAlgo.BINARY -> InstrumentedAlgos.binarySearch(sortedArray, missingTarget)
                }
                lastComparisons = comparisons
                history.add(0, RunResult(size, comparisons))
                while (history.size > 6) history.removeAt(history.lastIndex)
            },
            colors = ButtonDefaults.buttonColors(containerColor = SimColors.Green, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text("▸ Run") }

        lastComparisons?.let { comparisons ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = comparisons.toString(),
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "comparisons",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    algo.complexityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (history.isNotEmpty()) {
            Text(
                "Recent runs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                history.forEach { run ->
                    Text(
                        "n = ${run.n} → ${run.comparisons} comparisons",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
