package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
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

private val ResidualColor = SimColors.Red

/** Data points: pale on dark, a mid grey on light so they don't wash out. */
@Composable
private fun pointColor() = if (LocalDarkTheme.current) SimColors.Idle else SimColors.Grey

// The slider-explorer lab for Linear Regression: slope and intercept drive a line over a scatter,
// each residual drawn as a stub, with MSE read out and a least-squares "Show Best Fit". Layout
// follows the parameter-lab pattern: stage card (plot + legend), readout chips, narration, then the
// sliders and buttons — pinned in thumb reach when docked. The narration reads the residuals to say
// which slider to move next.
@Composable
fun RegressionSimulationSection() {
    var points by remember { mutableStateOf(generateData()) }
    var slope by remember { mutableFloatStateOf(0.35f) }
    var intercept by remember { mutableFloatStateOf(1.5f) }

    val mse = mse(points, slope, intercept)
    val best = remember(points) { leastSquares(points) }
    val bestMse = remember(points) { mse(points, best.first, best.second) }

    val dock = LocalLabDock.current
    LabIntro(
        "Each residual is the vertical gap between a point and your line. Least squares picks the one line that " +
            "makes the average squared residual, the MSE, as small as it can be.",
    )

    val controls: @Composable () -> Unit = {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LabParamSlider("Slope", "m", slope, -3f..3f, { "%.2f".format(it) }) { slope = it }
                LabParamSlider("Intercept", "c", intercept, -4f..8f, { "%.2f".format(it) }) { intercept = it }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabButton("New Data", primary = false, modifier = Modifier.weight(1f)) { points = generateData() }
                LabButton("Show Best Fit", primary = true, modifier = Modifier.weight(1f)) {
                    slope = best.first
                    intercept = best.second
                }
            }
        }
    }

    val chips = listOf<Pair<String?, String>>(null to equation(slope, intercept), "MSE" to "%.2f".format(mse))

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                RegressionCanvas(points, slope, intercept)
                RegressionLegend(Modifier.padding(top = 12.dp))
                if (dock == null) {
                    ReadoutChips(chips, Modifier.padding(top = 16.dp), accented = setOf(1))
                    RegressionNarration(points, slope, intercept, mse, bestMse, Modifier.padding(top = 14.dp))
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
                    controls()
                }
            }
        }
        if (dock != null) {
            ReadoutChips(chips, Modifier.padding(top = 14.dp), accented = setOf(1))
            RegressionNarration(points, slope, intercept, mse, bestMse, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }

    if (dock != null) {
        // Re-handed every composition: the controls close over this composition's values.
        SideEffect { dock.controls = controls }
        DisposableEffect(dock) { onDispose { dock.controls = null } }
    }
}

private fun mse(points: List<RegPoint>, m: Float, c: Float): Float =
    points.map { val e = m * it.x + c - it.y; e * e }.average().toFloat()

/** "y = 0.35x + 1.5": two decimals at most, trailing zeros dropped. */
private fun equation(m: Float, c: Float): String {
    fun short(v: Float) = "%.2f".format(v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    val sign = if (c < 0) "−" else "+"
    return "y = ${short(m)}x $sign ${short(abs(c))}"
}

@Composable
private fun RegressionNarration(
    points: List<RegPoint>,
    slope: Float,
    intercept: Float,
    mse: Float,
    bestMse: Float,
    modifier: Modifier,
) {
    val residuals = points.map { it.y - (slope * it.x + intercept) }
    val meanX = points.map { it.x }.average().toFloat()
    // How residuals trend with x: positive means the points climb away from the line to the right.
    val drift = points.indices.sumOf { ((points[it].x - meanX) * residuals[it]).toDouble() } /
        points.sumOf { ((it.x - meanX) * (it.x - meanX)).toDouble() }
    val meanResidual = residuals.average()
    val atBest = mse <= bestMse * 1.02f + 1e-3f

    val headline = buildAnnotatedString {
        when {
            atBest -> {
                append("This is the ")
                withStyle(SpanStyle(color = SimColors.Green)) { append("best fit") }
                append(": no other line has a lower MSE.")
            }
            residuals.all { it > 0f } -> append("Every point sits above your line, so raise the intercept first.")
            residuals.all { it < 0f } -> append("Every point sits below your line, so lower the intercept first.")
            abs(drift) > 0.15 -> append(
                if (drift > 0) "Points climb away from your line on the right, so steepen the slope."
                else "Points fall away from your line on the right, so flatten the slope.",
            )
            meanResidual > 0 -> append("Your line runs a little low overall, so raise the intercept.")
            else -> append("Your line runs a little high overall, so lower the intercept.")
        }
    }
    val detail = if (atBest) {
        "Its MSE is ${"%.2f".format(bestMse)}. Any tilt or shift from here makes the squared residuals grow."
    } else {
        "Each red stub is one residual. MSE is the average of their squares."
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun RegressionLegend(modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val pointColor = pointColor()
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(
            Modifier.size(11.dp).background(pointColor, CircleShape) to "Data",
            Modifier.width(14.dp).height(3.dp).clip(CircleShape).background(accent) to "Your line",
            Modifier.width(2.dp).height(12.dp).background(ResidualColor) to "Residual",
        ).forEach { (swatch, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = swatch)
                Text(
                    label,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RegressionCanvas(points: List<RegPoint>, slope: Float, intercept: Float) {
    val gridColor = MaterialTheme.colorScheme.outline
    val panel = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
    val accent = MaterialTheme.colorScheme.primary
    val pointColor = pointColor()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(panel),
    ) {
        val pad = 18.dp.toPx()
        val xs = points.map { it.x }
        val xMin = (xs.minOrNull() ?: 0f) - 0.3f
        val xMax = (xs.maxOrNull() ?: 1f) + 0.3f

        // The line always stays in view, so its endpoints count toward the y range.
        val allYs = points.map { it.y } + listOf(slope * xMin + intercept, slope * xMax + intercept)
        var yMin = allYs.min()
        var yMax = allYs.max()
        if (yMax - yMin < 1f) yMax = yMin + 1f
        val yr = yMax - yMin
        yMin -= yr * 0.08f
        yMax += yr * 0.08f

        fun xOf(x: Float): Float = pad + (x - xMin) / (xMax - xMin) * (size.width - 2 * pad)
        fun yOf(y: Float): Float = size.height - pad - (y - yMin) / (yMax - yMin) * (size.height - 2 * pad)

        for (i in 0..4) {
            val gy = pad + i * (size.height - 2 * pad) / 4
            drawLine(gridColor.copy(alpha = 0.6f), Offset(pad, gy), Offset(size.width - pad, gy), strokeWidth = 1.dp.toPx())
        }

        points.forEach { p ->
            val onLine = slope * p.x + intercept
            drawLine(ResidualColor.copy(alpha = 0.75f), Offset(xOf(p.x), yOf(p.y)), Offset(xOf(p.x), yOf(onLine)), strokeWidth = 1.5.dp.toPx())
        }

        drawLine(
            color = accent,
            start = Offset(xOf(xMin), yOf(slope * xMin + intercept)),
            end = Offset(xOf(xMax), yOf(slope * xMax + intercept)),
            strokeWidth = 3.dp.toPx(),
        )

        points.forEach { p -> drawCircle(pointColor, radius = 6.dp.toPx(), center = Offset(xOf(p.x), yOf(p.y))) }
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
