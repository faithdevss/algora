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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import com.algora.app.feature.analysis.tools.common.Bar
import com.algora.app.feature.analysis.tools.common.InstrumentedAlgos
import com.algora.app.feature.analysis.tools.common.MiniBarChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class BenchVariant(val intro: String) {
    BENCHMARK(
        "Measures real wall-clock time of an insertion sort across growing inputs. Each point is the " +
            "average of several timed runs after a warmup — on-device timing is noisy, so read the " +
            "curve's shape, not the absolute microseconds.",
    ),
    REAL_VS_PREDICTED(
        "Compares measured runtime against the O(n²) prediction. The predicted column scales the " +
            "first measured point by (n / n₀)² — if the algorithm really is quadratic, measured and " +
            "predicted track each other as n grows.",
    ),
    SCALING(
        "Shows how measured runtime responds to input size. For an O(n²) algorithm, doubling n should " +
            "roughly quadruple the time — watch the ratio column settle near 4× as inputs grow.",
    ),
}

private data class BenchPoint(val n: Int, val micros: Double)

// Sizes double, so the quadratic story is legible: every step should land near 4× the previous
// time, and the predicted column diverges visibly across a 16× span. Linear steps would show a
// ratio column that falls away from 4× — the opposite of the lesson.
private val benchSizes = listOf(250, 500, 1000, 2000, 4000)

// Real timing: warmup run, then average several trials on reverse-sorted input (insertion sort's
// worst case). Runs off the main thread — the caller launches it on Dispatchers.Default.
private fun runBenchmark(): List<BenchPoint> = benchSizes.map { n ->
    val base = IntArray(n) { n - 1 - it }
    InstrumentedAlgos.insertionSort(base.copyOf()) // warmup
    val trials = 4
    var totalNs = 0L
    repeat(trials) {
        val arr = base.copyOf()
        val start = System.nanoTime()
        InstrumentedAlgos.insertionSort(arr)
        totalNs += System.nanoTime() - start
    }
    BenchPoint(n, (totalNs / trials) / 1000.0)
}

@Composable
fun BenchmarkTool(variant: BenchVariant) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<BenchPoint>?>(null) }
    var running by remember { mutableStateOf(false) }

    AnalysisToolCard(intro = variant.intro) {
        Button(
            onClick = {
                if (running) return@Button
                running = true
                scope.launch {
                    val measured = withContext(Dispatchers.Default) { runBenchmark() }
                    results = measured
                    running = false
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SimColors.Green, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) { Text(if (running) "Running…" else "▸ Run benchmark") }

        val data = results
        if (data == null) {
            Text(
                "Tap run to time real insertion sorts at n = ${benchSizes.first()}…${benchSizes.last()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
            return@AnalysisToolCard
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(6.dp),
        ) {
            MiniBarChart(bars = data.map { Bar(it.n.toString(), it.micros, SimColors.Green) })
        }

        when (variant) {
            BenchVariant.BENCHMARK -> MeasuredTable(data)
            BenchVariant.REAL_VS_PREDICTED -> PredictedTable(data)
            BenchVariant.SCALING -> ScalingTable(data)
        }

        Text(
            "Timing is measured on this device and varies run to run — the trend is the signal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun MeasuredTable(data: List<BenchPoint>) {
    TableHeader("n", "measured")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEach { p ->
            TableRow(p.n.toString(), "${format(p.micros)} µs")
        }
    }
}

@Composable
private fun PredictedTable(data: List<BenchPoint>) {
    val first = data.first()
    TableHeader("n", "measured  ·  predicted")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEach { p ->
            val ratio = p.n.toDouble() / first.n
            val predicted = first.micros * ratio * ratio
            TableRow(p.n.toString(), "${format(p.micros)}  ·  ${format(predicted)} µs")
        }
    }
}

@Composable
private fun ScalingTable(data: List<BenchPoint>) {
    TableHeader("n", "measured  ·  ×prev")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEachIndexed { i, p ->
            val ratio = if (i == 0) null else p.micros / data[i - 1].micros
            val ratioText = ratio?.let { "${format(it)}×" } ?: "—"
            TableRow(p.n.toString(), "${format(p.micros)}  ·  $ratioText")
        }
    }
}

@Composable
private fun TableHeader(left: String, right: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(left, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TableRow(left: String, right: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(left, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            right,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun format(v: Double): String = String.format(Locale.US, "%.1f", v)
