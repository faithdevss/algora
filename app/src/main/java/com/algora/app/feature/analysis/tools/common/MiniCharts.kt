package com.algora.app.feature.analysis.tools.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import kotlin.math.ceil
import kotlin.math.ln

// Line chart over n = 1..maxN for a set of curves. logScale keeps steep curves (e.g. O(n²)) from
// flattening the gentle ones; linear scale shows true relative magnitude. Generalized from the
// original GrowthCurveChart canvas so every growth/complexity tool shares one renderer.
@Composable
fun MiniLineChart(
    curves: List<Curve>,
    maxN: Int,
    modifier: Modifier = Modifier,
    logScale: Boolean = true,
    height: Dp = 220.dp,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (curves.isEmpty() || maxN < 1) return@Canvas
        val maxValue = curves.maxOf { it.fn(maxN.toDouble()) }.coerceAtLeast(1.0)
        val logMax = ln(maxValue + 1)
        val padding = 8f

        fun xOf(n: Int): Float = padding + (n.toFloat() / maxN) * (size.width - 2 * padding)
        fun yOf(value: Double): Float {
            val normalized = if (logScale) (ln(value + 1) / logMax).toFloat()
            else (value / maxValue).toFloat()
            return size.height - padding - normalized.coerceIn(0f, 1f) * (size.height - 2 * padding)
        }

        drawLine(gridColor, Offset(padding, size.height - padding), Offset(size.width - padding, size.height - padding), strokeWidth = 2f)
        drawLine(gridColor, Offset(padding, padding), Offset(padding, size.height - padding), strokeWidth = 2f)

        val sampleStep = (maxN / 60).coerceAtLeast(1)
        curves.forEach { curve ->
            val path = Path()
            var first = true
            var n = 1
            while (n <= maxN) {
                val x = xOf(n)
                val y = yOf(curve.fn(n.toDouble()))
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                n += sampleStep
            }
            drawPath(path, color = curve.color, style = Stroke(width = 3f))
        }
    }
}

class Bar(val label: String, val value: Double, val color: Color)

// Vertical bar chart. logScale is available for cost data spanning several orders of magnitude
// (e.g. amortized resize spikes). Heights normalize to the tallest bar. Every bar's label is drawn
// under the axis; when the labels are wider than their slots only every k-th one is drawn, so a
// crowded chart thins out instead of overlapping into mush.
@Composable
fun MiniBarChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    logScale: Boolean = false,
    height: Dp = 180.dp,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp, textAlign = TextAlign.Center)

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (bars.isEmpty()) return@Canvas
        val maxValue = bars.maxOf { it.value }.coerceAtLeast(1.0)
        val logMax = ln(maxValue + 1)
        val padding = 8f
        val slot = (size.width - 2 * padding) / bars.size
        val barWidth = slot * 0.6f

        val labels = bars.map { textMeasurer.measure(it.label, labelStyle) }
        val labelHeight = labels.maxOf { it.size.height }.toFloat()
        // Bars sit above the axis; the label strip is carved out of the bottom of the canvas.
        val axisY = size.height - padding - labelHeight - 4f
        val plotHeight = axisY - padding

        drawLine(axisColor, Offset(padding, axisY), Offset(size.width - padding, axisY), strokeWidth = 2f)

        val widest = labels.maxOf { it.size.width }.toFloat()
        val stride = ceil((widest + 6f) / slot).toInt().coerceAtLeast(1)

        bars.forEachIndexed { index, bar ->
            val normalized = if (logScale) (ln(bar.value + 1) / logMax).toFloat()
            else (bar.value / maxValue).toFloat()
            val barHeight = normalized.coerceIn(0f, 1f) * plotHeight
            val left = padding + index * slot + (slot - barWidth) / 2f
            drawRect(
                color = bar.color,
                topLeft = Offset(left, axisY - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            )

            if (index % stride == 0) {
                val layout = labels[index]
                drawText(
                    layout,
                    color = labelColor,
                    topLeft = Offset(padding + index * slot + (slot - layout.size.width) / 2f, axisY + 4f),
                )
            }
        }
    }
}
