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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
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

// A fixed, seeded scatter that climbs faster than the starting line, so the lab opens on "the slope is
// too flat" and Reset always returns to the same story.
private fun regressionData(): List<RegPoint> {
    val random = Random(3)
    return (0 until 12).map { i ->
        val x = i * 0.9f + random.nextFloat() * 0.4f
        RegPoint(x, 0.55f * x + 0.9f + (random.nextFloat() * 2f - 1f) * 0.9f)
    }
}

private val ResidualColor = SimColors.Red

private const val START_SLOPE = 0.35f
private const val START_INTERCEPT = 1.5f

/** Data points: pale on dark, a mid grey on light so they don't wash out. */
@Composable
private fun pointColor() = if (LocalDarkTheme.current) SimColors.Idle else SimColors.Grey

private fun f2(v: Float) = "%.2f".format(v)

/** Steps [value] by [delta] × [step] on the step grid, within [range]. */
private fun stepped(value: Float, delta: Int, step: Float, range: ClosedFloatingPointRange<Float>) =
    ((value + delta * step) / step).roundToInt().times(step).coerceIn(range.start, range.endInclusive)

private val SlopeRange = -3f..3f
private val InterceptRange = -4f..8f

// The Linear Regression lab: slope and intercept drive a line over a scatter, each residual drawn as a
// stub. Chips read the line, its MSE and the best MSE; the headline reads the residuals to say what is
// wrong with the line; a Slope / Intercept picker over one stepper and Show Best Fit sit in thumb reach.
@Composable
fun RegressionSimulationSection() {
    val points = remember { regressionData() }
    var slope by remember { mutableFloatStateOf(START_SLOPE) }
    var intercept by remember { mutableFloatStateOf(START_INTERCEPT) }
    var selected by remember { mutableIntStateOf(0) }

    val mse = mse(points, slope, intercept)
    val best = remember(points) { leastSquares(points) }
    val bestMse = remember(points) { mse(points, best.first, best.second) }
    val (headline, body) = regressionStory(points, slope, intercept, mse, bestMse)

    val dock = LocalLabDock.current
    LabIntro(
        "Each residual is the vertical gap between a point and your line. Least squares picks the one line that " +
            "makes the average squared residual, the MSE, as small as it can be.",
    )

    val controls: @Composable () -> Unit = {
        LabParamActionControls(
            params = listOf(
                LabParam("Slope m", "Slope", "m", f2(slope), slope > SlopeRange.start + 1e-4f, slope < SlopeRange.endInclusive - 1e-4f),
                LabParam("Intercept c", "Intercept", "c", f2(intercept), intercept > InterceptRange.start + 1e-4f, intercept < InterceptRange.endInclusive - 1e-4f),
            ),
            selected = selected,
            onSelect = { selected = it },
            onStep = { i, delta ->
                if (i == 0) slope = stepped(slope, delta, 0.05f, SlopeRange) else intercept = stepped(intercept, delta, 0.1f, InterceptRange)
            },
            action = "Show Best Fit",
            onAction = {
                slope = best.first
                intercept = best.second
            },
        )
    }

    val chips = listOf(
        LabChip("y =", "${f2(slope)}x ${if (intercept < 0) "−" else "+"} ${f2(abs(intercept))}"),
        LabChip("MSE", f2(mse), tint = StoryTone.Path),
        LabChip("best", f2(bestMse)),
    )

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
            }
        }
        LabChips(chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(headline, body, Modifier.padding(top = 16.dp))
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        // Re-handed every composition: the controls close over this composition's values.
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") {
                slope = START_SLOPE
                intercept = START_INTERCEPT
                selected = 0
            }
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

private fun mse(points: List<RegPoint>, m: Float, c: Float): Float =
    points.map { val e = m * it.x + c - it.y; e * e }.average().toFloat()

/** What the residuals say about the line: its headline (with the key word marked) and the why. */
private fun regressionStoryText(xs: List<Float>, ys: List<Float>, slope: Float, intercept: Float, mse: Float, bestMse: Float): Pair<String, String> {
    val residuals = xs.indices.map { ys[it] - (slope * xs[it] + intercept) }
    val meanX = xs.average().toFloat()
    // How residuals trend with x: positive means the points climb away from the line to the right.
    val drift = xs.indices.sumOf { ((xs[it] - meanX) * residuals[it]).toDouble() } / xs.sumOf { ((it - meanX) * (it - meanX)).toDouble() }
    val right = xs.indices.filter { xs[it] > meanX }
    val rightAbove = right.count { residuals[it] > 0f }
    val rightBelow = right.size - rightAbove
    val body = "Each red stub is one residual, and MSE averages their squares. The best line reaches ${f2(bestMse)}."
    return when {
        mse <= bestMse * 1.02f + 1e-3f ->
            "This is the {m:best fit}: no other line has a lower MSE." to
                "Its MSE is ${f2(bestMse)}. Tilt or shift the line from here and the squared residuals grow."
        residuals.all { it > 0f } -> "Every point sits {above} your line, so the intercept is too low." to body
        residuals.all { it < 0f } -> "Every point sits {below} your line, so the intercept is too high." to body
        drift > 0.15 && rightAbove * 2 > right.size ->
            "$rightAbove of ${right.size} points on the right sit {above} your line, so the slope is too flat." to body
        drift < -0.15 && rightBelow * 2 > right.size ->
            "$rightBelow of ${right.size} points on the right sit {below} your line, so the slope is too steep." to body
        residuals.average() > 0 -> "Most points sit {above} your line, so the intercept is too low." to body
        else -> "Most points sit {below} your line, so the intercept is too high." to body
    }
}

private fun regressionStory(points: List<RegPoint>, slope: Float, intercept: Float, mse: Float, bestMse: Float) =
    regressionStoryText(points.map { it.x }, points.map { it.y }, slope, intercept, mse, bestMse)

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
