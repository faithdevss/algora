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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    if (config.margins) SvmStoryLab(config) else LogisticStoryLab(config)
}

// ── Logistic regression storyboard ──────────────────────────────────────────
// The plane and a legend, chips (correct · log loss), a headline naming what is on the wrong side and
// how far the bias has to move, then a w₁ / w₂ / b picker, the value's stepper and Fit Line.

// Opens with one point just across the line (the first bias from 0.4 up that leaves exactly one wrong),
// so the first thing to fix is visible.
private val logisticStart: FloatArray by lazy {
    val points = logisticClassifierConfig.points
    val bias = (4..20).map { it / 10f }.firstOrNull { b -> points.count { predict(floatArrayOf(1f, 0.6f, b), it) != it.target } == 1 } ?: 0.4f
    floatArrayOf(1f, 0.6f, bias)
}

/** Gradient descent on the mean log loss (with a touch of L2, so separable data can't run off). */
private fun fitLogistic(start: FloatArray, points: List<DataPoint>): FloatArray {
    val w = DoubleArray(3) { start[it].toDouble() }
    repeat(3000) {
        val g = DoubleArray(3)
        points.forEach { p ->
            val z = w[0] * p.x + w[1] * p.y + w[2]
            val err = 1.0 / (1.0 + exp(-z)) - p.target
            g[0] += err * p.x; g[1] += err * p.y; g[2] += err
        }
        for (k in 0..2) w[k] -= 0.5 * (g[k] / points.size + if (k < 2) 0.02 * w[k] else 0.0)
    }
    return FloatArray(3) { snap(w[it].toFloat().coerceIn(-3f, 3f)) }
}

private val paramSymbols = listOf("w₁", "w₂", "b")

private fun logisticStory(points: List<DataPoint>, w: FloatArray, wrong: Set<Int>): Pair<String, String> {
    val n = points.size
    val lead = "The line is where the model is exactly 50% sure."
    if (wrong.isEmpty()) {
        return "{m:All $n correct.} The line splits the two classes." to
            "$lead Fit Line keeps lowering log loss by pushing points further from it."
    }
    if (wrong.size == 1) {
        val p = points[wrong.first()]
        // How far the bias must move for this point's score to change sign, to one decimal.
        val gap = kotlin.math.ceil((kotlin.math.abs(score(w, p)) + 0.05f) * 10f) / 10f
        return "${n - 1} of $n correct. {w:One ${if (p.target == 1) "pink" else "blue"} point} sits just past the line." to
            "$lead ${if (p.target == 1) "Raising" else "Lowering"} b by about ${"%.1f".format(gap)} moves it back across."
    }
    val nudge = bestNudge(w, points) { logLoss(it, points) }
    return "${n - wrong.size} of $n correct. {w:${wrong.size} points} sit on the wrong side." to
        (nudge?.let { "$lead ${if (it.up) "Raise" else "Lower"} ${paramSymbols[it.param]} to ${if (it.param == 2) "slide" else "tilt"} it toward them." }
            ?: "$lead Fit Line finds the weights with the lowest log loss.")
}

@Composable
private fun LogisticStoryLab(config: ClassifierConfig) {
    val params = remember(config) { logisticStart.map { mutableFloatStateOf(it) } }
    val selected = remember(config) { mutableIntStateOf(2) }
    val w = floatArrayOf(params[0].floatValue, params[1].floatValue, params[2].floatValue)
    val points = config.points
    val wrong = points.indices.filter { predict(w, points[it]) != points[it].target }.toSet()
    val (headline, body) = logisticStory(points, w, wrong)
    val chips = listOf(
        LabChip("correct", "${points.size - wrong.size} / ${points.size}", tint = StoryTone.Path),
        LabChip("log loss", "%.2f".format(logLoss(w, points))),
    )
    val legend = listOfNotNull(
        Triple(ZeroColor, SwatchStyle.Dot, "Class 0"),
        Triple(OneColor, SwatchStyle.Dot, "Class 1"),
        if (wrong.isNotEmpty()) Triple(ErrorRing, SwatchStyle.Ring, "Misclassified") else null,
    )
    val controls: @Composable () -> Unit = {
        LabParamActionControls(
            params = listOf("Weight", "Weight", "Bias").mapIndexed { i, name ->
                val v = params[i].floatValue
                LabParam("$name ${paramSymbols[i]}", name, paramSymbols[i], "%.1f".format(v), v > -3f + 1e-4f, v < 3f - 1e-4f)
            },
            selected = selected.intValue,
            onSelect = { selected.intValue = it },
            onStep = { i, delta -> params[i].floatValue = snap((params[i].floatValue + delta * 0.1f).coerceIn(-3f, 3f)) },
            action = "Fit Line",
            onAction = {
                val fit = fitLogistic(w, points)
                params.forEachIndexed { i, p -> p.floatValue = fit[i] }
            },
        )
    }
    val dock = LocalLabDock.current
    LabIntro(
        "Logistic regression scores each point with w₁·x + w₂·y + b and squashes the score into a probability. " +
            "The line is where that probability is exactly 50%; tilt it with the weights and slide it with the bias.",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ClassifierCanvas(config, w, wrong)
                StoryLegendRow(legend, Modifier.padding(top = 14.dp))
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
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") {
                params.forEachIndexed { i, p -> p.floatValue = logisticStart[i] }
                selected.intValue = 2
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

@Composable
private fun ClassifierCanvas(config: ClassifierConfig, w: FloatArray, wrong: Set<Int>, ringed: Set<Int> = emptySet(), outlined: Boolean = false) {
    val surface = MaterialTheme.colorScheme.surface
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
            if (outlined) drawCircle(surface, radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
            if (i in wrong) {
                drawCircle(ErrorRing, radius = r + 4.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
            } else if (i in ringed) {
                drawCircle(SimColors.Active, radius = r + 4.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
    }
}

// ── Linear SVM storyboard ───────────────────────────────────────────────────
// The plane, a legend of what is ringed, chips (correct · margin · hinge), a story headline, then a
// w₁ / w₂ / b picker over one stepper in place of three sliders.

private val svmStart = floatArrayOf(1f, 0.6f, 0.4f)

@Composable
private fun SvmStoryLab(config: ClassifierConfig) {
    val params = remember(config) { svmStart.map { mutableFloatStateOf(it) } }
    val selected = remember(config) { mutableIntStateOf(0) }
    val w = floatArrayOf(params[0].floatValue, params[1].floatValue, params[2].floatValue)
    val points = config.points
    val wrong = points.indices.filter { predict(w, points[it]) != points[it].target }.toSet()
    val inside = points.indices.filter { it !in wrong && (if (points[it].target == 1) 1f else -1f) * score(w, points[it]) < 1f }.toSet()
    val norm = sqrt(w[0] * w[0] + w[1] * w[1])
    val margin = if (norm > 1e-3f) "%.2f".format(2f / norm) else "—"
    val hinge = hingeLoss(w, points)

    val chips = listOf(
        LabChip("correct", "${points.size - wrong.size}/${points.size}", if (wrong.isEmpty()) StoryTone.Idle else StoryTone.Warn, good = wrong.isEmpty()),
        LabChip("margin", margin),
        LabChip("hinge", "%.2f".format(hinge)),
    )
    val symbols = listOf("w₁", "w₂", "b")
    val headline: String
    val body: String
    when {
        norm < 1e-3f -> {
            headline = "With both weights at {w:zero} there is no line."
            body = "Raise w₁ or w₂ to give the boundary a direction."
        }
        wrong.isEmpty() -> {
            headline = "All ${points.size} points are on the right side. The margin between the dashed lines is {$margin} wide."
            body = if (inside.isEmpty()) {
                "No point sits inside it, so hinge loss is 0. Shrink both weights to widen it until one does."
            } else {
                "${inside.size} point${if (inside.size == 1) " sits" else "s sit"} inside it and add hinge loss. Shrink both weights to widen the margin."
            }
        }
        else -> {
            val nudge = bestNudge(w, points) { hingeLoss(it, points) }
            headline = "{w:${if (wrong.size == 1) "One point is" else "${wrong.size} points are"}} on the wrong side." +
                (nudge?.let { " ${if (it.up) "Raise" else "Lower"} ${symbols[it.param]} to ${if (it.param == 2) "slide" else "tilt"} the boundary." } ?: "")
            body = "Each misclassified point adds more than 1 to the hinge loss. The margin is $margin wide."
        }
    }
    val legend = listOfNotNull(
        Triple(ZeroColor, SwatchStyle.Dot, "Class 0"),
        Triple(OneColor, SwatchStyle.Dot, "Class 1"),
        Triple(SimColors.Active, SwatchStyle.Ring, "Inside margin"),
        if (wrong.isNotEmpty()) Triple(ErrorRing, SwatchStyle.Ring, "Misclassified") else null,
    )
    val controls: @Composable () -> Unit = {
        LabParamControls(
            params = listOf("Weight", "Weight", "Bias").mapIndexed { i, name ->
                val v = params[i].floatValue
                LabParam(symbols[i], name, symbols[i], "%.1f".format(v), v > -3f + 1e-4f, v < 3f - 1e-4f)
            },
            selected = selected.intValue,
            onSelect = { selected.intValue = it },
            onStep = { i, delta -> params[i].floatValue = snap((params[i].floatValue + delta * 0.1f).coerceIn(-3f, 3f)) },
        )
    }
    val dock = LocalLabDock.current
    LabIntro(
        "A linear SVM draws the same line as logistic regression, then asks for the widest empty band around it. " +
            "The dashed lines sit where w₁·x + w₂·y + b = ±1, so shrinking the weights widens the band.",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ClassifierCanvas(config, w, wrong, ringed = inside, outlined = true)
                StoryLegendRow(legend, Modifier.padding(top = 14.dp))
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
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") {
                params.forEachIndexed { i, p -> p.floatValue = svmStart[i] }
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

