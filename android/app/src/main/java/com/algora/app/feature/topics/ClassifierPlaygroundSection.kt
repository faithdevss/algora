package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.CategoryAccents
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── 2D linear-classifier playground ─────────────────────────────────────────
// Logistic regression and the linear SVM: the decision boundary is the zero-contour of
// w₁·x + w₂·y + b, params = [w₁, w₂, b]. The two share that exact boundary and differ only in
// readout (log loss vs margin + hinge loss), driven by config — not new geometry.
//
// Layout follows the parameter-lab pattern: a stage card (plane + legend), readout chips,
// narration, then the sliders — pinned in thumb reach when docked.

class DataPoint(val x: Float, val y: Float, val target: Int)

class ClassifierConfig(
    val points: List<DataPoint>,
    val axisRange: ClosedFloatingPointRange<Float>,
    /** SVM: draw the ±1 margins dashed and report margin + hinge loss instead of log loss. */
    val margins: Boolean,
)

private val ZeroColor = SimColors.Blue
private val OneColor = CategoryAccents.Pink
private val ErrorRing = SimColors.Red

private const val ASPECT = 1.45f
private const val NUDGE = 0.2f

private fun score(w: FloatArray, p: DataPoint) = w[0] * p.x + w[1] * p.y + w[2]
private fun predict(w: FloatArray, p: DataPoint) = if (score(w, p) >= 0f) 1 else 0

private fun logLoss(w: FloatArray, points: List<DataPoint>): Double = points.sumOf { p ->
    val prob = (1.0 / (1.0 + exp(-score(w, p).toDouble()))).coerceIn(1e-9, 1 - 1e-9)
    -(if (p.target == 1) ln(prob) else ln(1 - prob))
} / points.size

private fun hingeLoss(w: FloatArray, points: List<DataPoint>): Double = points.sumOf { p ->
    val y = if (p.target == 1) 1.0 else -1.0
    maxOf(0.0, 1 - y * score(w, p))
} / points.size

private fun snap(v: Float) = (v * 10f).roundToInt() / 10f

private class Nudge(val param: Int, val up: Boolean)

/** The single ±0.2 slider move that fixes the most points, or null when none fixes any. */
private fun bestNudge(w: FloatArray, points: List<DataPoint>, loss: (FloatArray) -> Double): Nudge? {
    val wrong = points.count { predict(w, it) != it.target }
    var best: Nudge? = null
    var bestWrong = wrong
    var bestLoss = Double.MAX_VALUE
    for (param in 0..2) {
        for (up in listOf(true, false)) {
            val t = w.copyOf().also { it[param] = snap(it[param] + if (up) NUDGE else -NUDGE) }
            val n = points.count { predict(t, it) != it.target }
            val l = loss(t)
            if (n < bestWrong || (n == bestWrong && best != null && l < bestLoss)) {
                best = Nudge(param, up); bestWrong = n; bestLoss = l
            }
        }
    }
    return best
}

@Composable
fun ClassifierPlaygroundSection(config: ClassifierConfig) {
    val params = remember(config) { listOf(mutableFloatStateOf(1f), mutableFloatStateOf(0.6f), mutableFloatStateOf(0.4f)) }
    val w = floatArrayOf(params[0].floatValue, params[1].floatValue, params[2].floatValue)
    val points = config.points
    val wrong = points.indices.filter { predict(w, points[it]) != points[it].target }.toSet()
    val lossFn: (FloatArray) -> Double = if (config.margins) { t -> hingeLoss(t, points) } else { t -> logLoss(t, points) }
    val norm = sqrt(w[0] * w[0] + w[1] * w[1])

    val readout = buildString {
        append("correct = ${points.size - wrong.size} / ${points.size}")
        if (config.margins) {
            if (norm > 1e-3f) append(" · margin = ${"%.2f".format(2f / norm)}")
            append(" · hinge = ${"%.2f".format(hingeLoss(w, points))}")
        } else {
            append(" · log loss = ${"%.2f".format(logLoss(w, points))}")
        }
    }

    val dock = LocalLabDock.current
    LabIntro(
        if (config.margins) {
            "A linear SVM draws the same line as logistic regression, then asks for the widest empty band around it. " +
                "The dashed lines sit where w₁·x + w₂·y + b = ±1, so shrinking the weights widens the band."
        } else {
            "Logistic regression scores each point with w₁·x + w₂·y + b and squashes the score into a probability. " +
                "The line is where that probability is exactly 50%; tilt it with the weights and slide it with the bias."
        },
    )

    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LabParamSlider("Weight", "w₁", params[0].floatValue, -3f..3f) { params[0].floatValue = snap(it) }
            LabParamSlider("Weight", "w₂", params[1].floatValue, -3f..3f) { params[1].floatValue = snap(it) }
            LabParamSlider("Bias", "b", params[2].floatValue, -3f..3f) { params[2].floatValue = snap(it) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ClassifierCanvas(config, w, wrong)
                ClassifierLegend(Modifier.padding(top = 12.dp))
                if (dock == null) {
                    ReadoutChips(readout, Modifier.padding(top = 16.dp), accented = setOf(0))
                    ClassifierNarration(config, points, w, wrong, lossFn, Modifier.padding(top = 14.dp))
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
                    controls()
                }
            }
        }
        if (dock != null) {
            ReadoutChips(readout, Modifier.padding(top = 14.dp), accented = setOf(0))
            ClassifierNarration(config, points, w, wrong, lossFn, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }

    if (dock != null) {
        // Re-handed every composition: the controls close over this composition's slider values.
        SideEffect { dock.controls = controls }
        DisposableEffect(dock) { onDispose { dock.controls = null } }
    }
}

@Composable
private fun ClassifierNarration(
    config: ClassifierConfig,
    points: List<DataPoint>,
    w: FloatArray,
    wrong: Set<Int>,
    loss: (FloatArray) -> Double,
    modifier: Modifier,
) {
    val symbols = listOf("w₁", "w₂", "b")
    val headline = buildAnnotatedString {
        if (wrong.isEmpty()) {
            withStyle(SpanStyle(color = SimColors.Green)) { append("All ${points.size} points") }
            append(" are on the right side.")
        } else {
            val classes = wrong.map { points[it].target }.toSet()
            val n = wrong.size
            val count = if (n == 1) "One" else "$n"
            val verb = if (n == 1) "point is" else "points are"
            if (classes.size == 1) {
                val k = classes.first()
                append("$count ")
                withStyle(SpanStyle(color = if (k == 1) OneColor else ZeroColor)) { append("class $k") }
                append(" $verb on the wrong side.")
            } else {
                append("$count $verb on the wrong side.")
            }
            bestNudge(w, points, loss)?.let { nudge ->
                val verbUp = if (nudge.up) "Raise" else "Lower"
                val what = if (nudge.param == 2) "slide" else "tilt"
                append(" $verbUp ${symbols[nudge.param]} to $what the boundary.")
            }
        }
    }
    val norm = sqrt(w[0] * w[0] + w[1] * w[1])
    val detail = when {
        !config.margins -> "The line is where the model is exactly 50% sure."
        norm < 1e-3f -> "With both weights at zero there is no line, and no margin to measure."
        else -> "The margin is ${"%.2f".format(2f / norm)} wide, between the dashed lines. Shrink both weights to widen it while every point stays outside."
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
private fun ClassifierLegend(modifier: Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(ZeroColor to "Class 0", OneColor to "Class 1", ErrorRing to "Misclassified").forEachIndexed { i, (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = if (i == 2) {
                        Modifier.size(11.dp).border(2.dp, color, CircleShape)
                    } else {
                        Modifier.size(11.dp).background(color, CircleShape)
                    },
                )
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
private fun ClassifierCanvas(config: ClassifierConfig, w: FloatArray, wrong: Set<Int>) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ASPECT)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        // y spans the configured range; x is widened to the canvas aspect so both share a scale.
        val yLo = config.axisRange.start
        val yHi = config.axisRange.endInclusive
        val xHalf = (yHi - yLo) * ASPECT / 2
        val xMid = (yLo + yHi) / 2
        val xLo = xMid - xHalf
        val xHi = xMid + xHalf
        fun px(x: Float) = (x - xLo) / (xHi - xLo) * size.width
        fun py(y: Float) = size.height - (y - yLo) / (yHi - yLo) * size.height

        drawRect(ZeroColor.copy(alpha = 0.16f))
        val plane = linearHalfPlane(w[0], w[1], w[2], xLo, xHi, yLo, yHi)
        plane.region?.let { drawPath(it, OneColor.copy(alpha = 0.16f)) }
        if (config.margins) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            for (level in listOf(1f, -1f)) {
                linearHalfPlane(w[0], w[1], w[2] - level, xLo, xHi, yLo, yHi).line?.let { (a, b) ->
                    drawLine(accent.copy(alpha = 0.6f), a, b, strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
                }
            }
        }
        plane.line?.let { (a, b) -> drawLine(accent, a, b, strokeWidth = 3.dp.toPx()) }

        val r = 6.5.dp.toPx()
        config.points.forEachIndexed { i, p ->
            val c = Offset(px(p.x), py(p.y))
            drawCircle(if (p.target == 1) OneColor else ZeroColor, radius = r, center = c)
            if (i in wrong) drawCircle(ErrorRing, radius = r + 4.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** The side of w₁·x + w₂·y + b ≥ 0 clipped to the canvas, and where its edge line crosses the canvas. */
internal class HalfPlane(val region: Path?, val line: Pair<Offset, Offset>?)

/**
 * Clips the half-plane w₁·x + w₂·y + b ≥ 0 to the canvas, whose corners sit at data coordinates
 * [xLo]..[xHi] × [yLo]..[yHi] (y up). Shared by the Perceptron and classifier labs.
 */
internal fun DrawScope.linearHalfPlane(w1: Float, w2: Float, b: Float, xLo: Float, xHi: Float, yLo: Float, yHi: Float): HalfPlane {
    val corners = listOf(
        Offset(0f, 0f) to (xLo to yHi),
        Offset(size.width, 0f) to (xHi to yHi),
        Offset(size.width, size.height) to (xHi to yLo),
        Offset(0f, size.height) to (xLo to yLo),
    )
    val sums = corners.map { (_, p) -> w1 * p.first + w2 * p.second + b }
    val region = Path()
    var started = false
    fun add(o: Offset) {
        if (!started) { region.moveTo(o.x, o.y); started = true } else region.lineTo(o.x, o.y)
    }
    val crossings = mutableListOf<Offset>()
    for (i in corners.indices) {
        val j = (i + 1) % corners.size
        if (sums[i] >= 0f) add(corners[i].first)
        if ((sums[i] >= 0f) != (sums[j] >= 0f)) {
            val t = sums[i] / (sums[i] - sums[j])
            val a = corners[i].first
            val c = corners[j].first
            val cross = Offset(a.x + (c.x - a.x) * t, a.y + (c.y - a.y) * t)
            add(cross)
            crossings += cross
        }
    }
    if (started) region.close()
    // Fewer than two edge crossings means the line misses the canvas (or w₁ = w₂ = 0).
    return HalfPlane(
        region = if (started) region else null,
        line = if (crossings.size >= 2) crossings.first() to crossings.last() else null,
    )
}

// ── Configs ─────────────────────────────────────────────────────────────────

// Two gaussian blobs — a deterministic, seeded scatter.
private fun blobs(seed: Int): List<DataPoint> {
    val rng = Random(seed)
    fun cluster(cx: Float, cy: Float, target: Int) = List(8) {
        DataPoint(cx + (rng.nextFloat() - 0.5f) * 0.9f, cy + (rng.nextFloat() - 0.5f) * 0.9f, target)
    }
    return cluster(-0.7f, -0.7f, 0) + cluster(0.7f, 0.7f, 1)
}

val logisticClassifierConfig = ClassifierConfig(points = blobs(seed = 7), axisRange = -1.5f..1.5f, margins = false)

val svmClassifierConfig = ClassifierConfig(points = blobs(seed = 7), axisRange = -1.5f..1.5f, margins = true)

// SimulationType is a param-free data object, so config is resolved by topic id (mirrors
// AnalysisToolRegistry's id→content pattern). Keeps the param-free convention the exhaustive
// `when` relies on.
fun classifierConfigFor(topicId: String): ClassifierConfig = when (topicId) {
    "svm" -> svmClassifierConfig
    else -> logisticClassifierConfig
}

