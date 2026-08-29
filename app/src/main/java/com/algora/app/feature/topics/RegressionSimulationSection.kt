package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.AlgoraCodeStyle
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.roundToInt
import kotlin.random.Random

private data class RegPoint(val x: Float, val y: Float)

private fun generateData(): List<RegPoint> {
    val m = Random.nextFloat() * 3f - 0.5f
    val c = Random.nextFloat() * 4f + 1f
    return (0 until 12).map { i ->
        val x = i * 0.9f + Random.nextFloat() * 0.4f
        RegPoint(x, m * x + c + (Random.nextFloat() * 3f - 1.5f))
    }
}

private val FitColor = Color(0xFF4F46E5)
private val PointColor = Color(0xFF7C3AED)

// Full port of docs/design/Algora.dc.html's regression lab (regSvg / regMseVal / regFit / genData):
// slope & intercept sliders drive a live best-fit line over a scatter, with MSE cost read-out and a
// least-squares "Best Fit" solver. This is the slider-explorer sim widget applied to Linear Regression.
@Composable
fun RegressionSimulationSection() {
    var points by remember { mutableStateOf(generateData()) }
    var slope by remember { mutableStateOf(1f) }
    var intercept by remember { mutableStateOf(1f) }

    val mse = remember(points, slope, intercept) {
        points.map { val e = slope * it.x + intercept - it.y; e * e }.average().toFloat()
    }
    val equation = "y=${slope.round2()}x${if (intercept >= 0) "+" else ""}${intercept.round2()}"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                RegressionCanvas(points = points, slope = slope, intercept = intercept)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(equation, style = AlgoraCodeStyle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(mse.round2(), style = AlgoraCodeStyle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Cost (MSE)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SliderRow(
                label = "Slope · m",
                value = slope,
                display = slope.round2(),
                valueRange = -3f..3f,
                onValueChange = { slope = it },
            )
            SliderRow(
                label = "Intercept · c",
                value = intercept,
                display = intercept.round2(),
                valueRange = -4f..8f,
                onValueChange = { intercept = it },
            )

            Spacer8()
            SimButtonRow(
                buttons = listOf(
                    Triple("↻ New Data", SimColors.Grey) {
                        points = generateData()
                    },
                    Triple("✦ Best Fit", SimColors.Green) {
                        val (m, c) = leastSquares(points)
                        slope = m
                        intercept = c
                    },
                ),
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    display: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(display, style = AlgoraCodeStyle, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun Spacer8() {
    Box(modifier = Modifier.height(8.dp))
}

@Composable
private fun RegressionCanvas(points: List<RegPoint>, slope: Float, intercept: Float) {
    val gridColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val pad = 14.dp.toPx()
        val xs = points.map { it.x }
        val xMin = (xs.minOrNull() ?: 0f) - 0.3f
        val xMax = (xs.maxOrNull() ?: 1f) + 0.3f

        val lineYs = listOf(slope * xMin + intercept, slope * xMax + intercept)
        val allYs = points.map { it.y } + lineYs
        var yMin = allYs.min()
        var yMax = allYs.max()
        if (yMax - yMin < 1f) yMax = yMin + 1f
        val yr = yMax - yMin
        yMin -= yr * 0.12f
        yMax += yr * 0.12f

        fun xOf(x: Float): Float = pad + (x - xMin) / (xMax - xMin) * (size.width - 2 * pad)
        fun yOf(y: Float): Float = size.height - pad - (y - yMin) / (yMax - yMin) * (size.height - 2 * pad)

        for (i in 0..4) {
            val gy = pad + i * (size.height - 2 * pad) / 4
            drawLine(gridColor, Offset(pad, gy), Offset(size.width - pad, gy), strokeWidth = 1.dp.toPx())
        }

        drawLine(
            color = FitColor,
            start = Offset(xOf(xMin), yOf(slope * xMin + intercept)),
            end = Offset(xOf(xMax), yOf(slope * xMax + intercept)),
            strokeWidth = 6.dp.toPx(),
        )

        points.forEach { p ->
            drawCircle(color = PointColor, radius = 7.dp.toPx(), center = Offset(xOf(p.x), yOf(p.y)), alpha = 0.85f)
        }
    }
}

private fun leastSquares(points: List<RegPoint>): Pair<Float, Float> {
    val n = points.size
    if (n == 0) return 0f to 0f
    var sx = 0f; var sy = 0f; var sxy = 0f; var sxx = 0f
    points.forEach { (x, y) -> sx += x; sy += y; sxy += x * y; sxx += x * x }
    val denom = n * sxx - sx * sx
    if (denom == 0f) return 0f to (sy / n)
    val m = (n * sxy - sx * sy) / denom
    val c = (sy - m * sx) / n
    return (m * 100).roundToInt() / 100f to (c * 100).roundToInt() / 100f
}

private fun Float.round2(): String {
    val r = (this * 100).roundToInt() / 100f
    return r.toString()
}
