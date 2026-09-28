package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

private enum class Distribution(val label: String, val blurb: String) {
    SORTED("Sorted", "Best case — one comparison per element."),
    REVERSED("Reversed", "Worst case — every element shifts fully left."),
    RANDOM("Random", "Average case — expected halfway shifting."),
    NEARLY_SORTED("Nearly sorted", "A few swaps from sorted — near best case."),
    DUPLICATES("Duplicates", "Few distinct values, repeated."),
}

private fun generate(dist: Distribution, n: Int): IntArray = when (dist) {
    Distribution.SORTED -> IntArray(n) { it }
    Distribution.REVERSED -> IntArray(n) { n - 1 - it }
    Distribution.RANDOM -> (0 until n).shuffled(Random(n * 31 + 7)).toIntArray()
    Distribution.NEARLY_SORTED -> IntArray(n) { it }.also { arr ->
        val rng = Random(n * 17 + 3)
        repeat((n / 8).coerceAtLeast(1)) {
            val i = rng.nextInt(n - 1)
            val tmp = arr[i]; arr[i] = arr[i + 1]; arr[i + 1] = tmp
        }
    }
    Distribution.DUPLICATES -> IntArray(n) { it % 3 }
}

@Composable
fun InputGeneratorTool() {
    var dist by remember { mutableStateOf(Distribution.RANDOM) }
    var n by remember { mutableStateOf(12f) }
    val nInt = n.toInt()

    // Generate + sort once per (shape, n) rather than on every recomposition.
    val (array, comparisons) = remember(dist, nInt) {
        val generated = generate(dist, nInt)
        // Sort a copy so the preview keeps showing the generated (unsorted) input.
        generated to InstrumentedAlgos.insertionSort(generated.copyOf())
    }

    AnalysisToolCard(
        intro = "Generate inputs of a chosen size and shape, then feed them to a real insertion sort. " +
            "Same n, same algorithm — only the input shape changes, and the comparison count moves with it.",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 14.dp),
        ) {
            Distribution.entries.forEachIndexed { index, option ->
                if (index > 0) Box(modifier = Modifier.padding(start = 8.dp))
                val selected = dist == option
                if (selected) {
                    Button(
                        onClick = { dist = option },
                        colors = ButtonDefaults.buttonColors(containerColor = SimColors.Violet, contentColor = Color.White),
                    ) { Text(option.label, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { dist = option }) { Text(option.label, maxLines = 1) }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                "Array size · n = $nInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = n, onValueChange = { n = it }, valueRange = 6f..24f, steps = 17)
        }

        // Generated-input preview.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            array.forEach { value ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SimColors.Violet.copy(alpha = 0.12f),
                ) {
                    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        Text(value.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = comparisons.toString(),
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = SimColors.Violet,
            )
            Text(
                "comparisons to sort this ${dist.label.lowercase()} input",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                dist.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
