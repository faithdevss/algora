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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.CategoryAccents
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors

// ── Decision-surface lab ─────────────────────────────────────────────────────
// ClassifierPlaygroundSection draws a straight line because its model IS a straight line. These
// topics need curved boundaries (RBF kernels, QDA's conic), class-conditional ellipses, support
// vectors and a scrubbable online update, so this renders an arbitrary decision function instead:
// evaluate it on a grid, tint each cell by predicted class, and trace the zero contour (and the ±1
// margins where the model has them) with marching squares. Everything shown is computed in
// DecisionSurfaceMath.kt.
//
// Layout follows the parameter-lab pattern: variant segments, a stage card (plane + legend),
// readout chips, narration, then the sliders — pinned in thumb reach when docked, with the stream
// transport under them for Passive-Aggressive.

private val NegativeFill = SimColors.Blue
private val PositiveFill = CategoryAccents.Pink
private val SupportRing = SimColors.Active
private val ErrorRing = SimColors.Red
private val EllipseColor = SimColors.Grey

private enum class MarkKind { Ring, Dot, Line }

private class LegendMark(val color: Color, val label: String, val kind: MarkKind)

private class SurfaceResult(
    val decision: (Float, Float) -> Double,
    /** "updates = 14 · τ = 0.000", rendered as ReadoutChips. */
    val readout: String,
    /** What happened. [highlight], if it occurs in it, is tinted. */
    val headline: String,
    val detail: String,
    val highlight: String? = null,
    // Circled in yellow — support vectors, or the example currently being processed.
    val ringed: Set<Int> = emptySet(),
    // Circled in red — misclassified under the current fit.
    val errors: Set<Int> = emptySet(),
    val ellipses: List<List<Pair<Float, Float>>> = emptyList(),
    // Draw the ±level contours dashed, e.g. an SVM's margin.
    val marginBand: Double? = null,
    // Points from this index on haven't streamed in yet; drawn dim.
    val unseenFrom: Int? = null,
)

private class SurfaceSlider(
    val name: String,
    val symbol: String,
    val range: ClosedFloatingPointRange<Float>,
    val initial: Float,
    val format: (Float) -> String = { "%.2f".format(it) },
)

private class SurfaceConfig(
    val intro: String,
    val data: (Int) -> List<ClassPoint>,
    val sliders: List<SurfaceSlider>,
    val variants: List<String> = emptyList(),
    val legend: List<LegendMark> = emptyList(),
    /** Streams the points in one at a time; the step is how many have been seen. */
    val stream: Boolean = false,
    val evaluate: (points: List<ClassPoint>, values: FloatArray, variant: Int, step: Int) -> SurfaceResult,
)

private fun f2(v: Double) = "%.2f".format(v)
private fun f3(v: Double) = "%.3f".format(v)
private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

private fun errorsOf(points: List<ClassPoint>, decision: (Float, Float) -> Double): Set<Int> =
    points.indices.filter {
        val d = decision(points[it].x, points[it].y)
        (d >= 0 && points[it].label < 0) || (d < 0 && points[it].label > 0)
    }.toSet()

private val supportLegend = LegendMark(SupportRing, "Support vector", MarkKind.Ring)
private val errorLegend = LegendMark(ErrorRing, "Misclassified", MarkKind.Ring)

// ── Configs ──────────────────────────────────────────────────────────────────

private fun svmRbfConfig() = SurfaceConfig(
    intro = "Two concentric rings — not linearly separable by any line at all. γ controls how far each support vector's influence reaches; C controls how much margin violation is tolerated.",
    data = { seed -> ringData(seed) },
    sliders = listOf(
        SurfaceSlider("Reach", "log₁₀ γ", -2f..1.2f, -0.4f) { "%.1f".format(it) },
        SurfaceSlider("Penalty", "log₁₀ C", -1f..3f, 1f) { "%.1f".format(it) },
    ),
    legend = listOf(supportLegend, errorLegend),
    evaluate = { points, values, _, _ ->
        val gamma = Math.pow(10.0, values[0].toDouble())
        val c = Math.pow(10.0, values[1].toDouble())
        val fit = trainSvm(points, rbfKernel(gamma), c)
        val wrong = fit.misclassified()
        val readout = "SVs = ${fit.supportVectors.size} · correct = ${points.size - wrong.size}/${points.size} · γ = ${f2(gamma)}"
        when {
            gamma > 3.0 -> SurfaceResult(
                decision = { x, y -> fit.decision(x, y) }, readout = readout,
                headline = "γ is large, so the boundary breaks into islands around single points.",
                highlight = "islands",
                detail = "Each support vector's influence has shrunk to nearly a point. That is memorization, and it will generalize badly.",
                ringed = fit.supportVectors, errors = wrong, marginBand = 1.0,
            )
            gamma < 0.05 -> SurfaceResult(
                decision = { x, y -> fit.decision(x, y) }, readout = readout,
                headline = "γ is small, so the kernel behaves almost linearly.",
                highlight = "almost linearly",
                detail = "Every point influences everywhere, and nothing that smooth can split two concentric rings.",
                ringed = fit.supportVectors, errors = wrong, marginBand = 1.0,
            )
            else -> SurfaceResult(
                decision = { x, y -> fit.decision(x, y) }, readout = readout,
                headline = "The boundary is a closed curve, which no linear model can produce.",
                highlight = "closed curve",
                detail = "The kernel trick got there using only inner products between pairs of points, never coordinates in the higher-dimensional space.",
                ringed = fit.supportVectors, errors = wrong, marginBand = 1.0,
            )
        }
    },
)

private fun nuSvcConfig() = SurfaceConfig(
    intro = "ν replaces C with something you can actually reason about: it simultaneously upper-bounds the fraction of margin errors and lower-bounds the fraction of support vectors. Both are measured below.",
    data = { seed -> overlappingBlobs(seed, separation = 1.15f) },
    sliders = listOf(SurfaceSlider("Budget", "ν", 0.05f..0.8f, 0.3f)),
    legend = listOf(supportLegend, errorLegend),
    evaluate = { points, values, _, _ ->
        val nu = values[0].toDouble()
        val result = trainNuSvc(points, linearKernel(), nu)
        val errorFraction = result.marginErrorFraction
        val svFraction = result.svFraction
        val holds = errorFraction <= nu + 0.06 && svFraction >= nu - 0.06
        SurfaceResult(
            decision = { x, y -> result.fit.decision(x, y) },
            readout = "ν = ${f2(nu)} · margin err = ${f2(errorFraction)} · SVs = ${f2(svFraction)}",
            headline = if (holds) {
                "The bound holds: margin errors ${f2(errorFraction)} ≤ ν ${f2(nu)} ≤ support vectors ${f2(svFraction)}."
            } else {
                "Margin errors ${f2(errorFraction)} and support vectors ${f2(svFraction)} sit just outside ν = ${f2(nu)}."
            },
            highlight = if (holds) "The bound holds" else null,
            detail = if (holds) {
                "That sandwich is what ν buys. C gives you nothing comparable, which is why it always needs a grid search."
            } else {
                "The bound is asymptotic, so on ${points.size} points it can miss slightly. The equivalent C here is ${f3(result.c)}."
            },
            ringed = result.fit.supportVectors,
            errors = result.fit.misclassified(),
            marginBand = 1.0,
        )
    },
)

private fun ldaConfig() = SurfaceConfig(
    intro = "Two classes with genuinely different spreads. LDA assumes they share one covariance, so it pools them — and the boundary it produces is always a straight line.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("Shrinkage", "", 0f..0.95f, 0f)),
    legend = listOf(LegendMark(EllipseColor, "Pooled covariance", MarkKind.Line), errorLegend),
    evaluate = { points, values, _, _ ->
        val shrink = values[0].toDouble()
        val fit = fitLda(points, shrink)
        val wrong = errorsOf(points) { x, y -> fit.decision(x, y) }
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readout = "correct = ${points.size - wrong.size}/${points.size} · params = 5",
            headline = "Both ellipses are identical, so the boundary is a straight line.",
            highlight = "identical",
            detail = "LDA forces both classes to share one pooled covariance, which cancels the quadratic terms. " +
                if (wrong.isEmpty()) {
                    "On this sample the wrong shape costs nothing; tap New Data until it does."
                } else {
                    "The pooled shape is wrong for the wide class, and the ${plural(wrong.size, "circled point")} are what that costs."
                },
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
        )
    },
)

private fun qdaConfig() = SurfaceConfig(
    intro = "The identical data as LDA, with the shared-covariance assumption dropped. Each class estimates its own, and the boundary stops being a line.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("Shrinkage", "", 0f..0.95f, 0f)),
    legend = listOf(LegendMark(EllipseColor, "Per-class covariance", MarkKind.Line), errorLegend),
    evaluate = { points, values, _, _ ->
        val fit = fitQda(points, values[0].toDouble())
        val lda = fitLda(points)
        val wrong = errorsOf(points) { x, y -> fit.decision(x, y) }
        val ldaWrong = errorsOf(points) { x, y -> lda.decision(x, y) }.size
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readout = "QDA = ${points.size - wrong.size}/${points.size} · LDA = ${points.size - ldaWrong}/${points.size} · params = 11",
            headline = "Each class gets its own ellipse, so the boundary bends into a conic.",
            highlight = "conic",
            detail = "QDA fits 11 parameters to LDA's 5, and on this sample " +
                when {
                    wrong.size < ldaWrong -> "that buys ${plural(ldaWrong - wrong.size, "fewer error")}."
                    wrong.size > ldaWrong -> "it costs ${plural(wrong.size - ldaWrong, "extra error")}: two covariances from ${points.size / 2} points each are noisier than one pooled estimate that is merely biased."
                    else -> "the two tie, so the extra six parameters bought nothing."
                } +
                " Shrinkage is the dial between them.",
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
        )
    },
)

private fun passiveAggressiveConfig() = SurfaceConfig(
    intro = "One pass over a stream, one example at a time. Example 45 is deliberately mislabelled — scrub past it and watch what each variant does to a boundary that was already correct.",
    data = { seed -> streamData(seed) },
    sliders = listOf(SurfaceSlider("Aggressiveness", "C", 0.05f..3f, 1f)),
    variants = listOf("Hard", "PA-I", "PA-II"),
    legend = listOf(
        LegendMark(SupportRing, "Current", MarkKind.Ring),
        errorLegend,
        LegendMark(SimColors.Grey.copy(alpha = 0.45f), "Not seen yet", MarkKind.Dot),
    ),
    stream = true,
    evaluate = { points, values, variantIndex, step ->
        val seen = step.coerceIn(0, points.size)
        val aggressiveness = values[0].toDouble()
        val variant = PaVariant.entries[variantIndex.coerceIn(0, 2)]
        val path = passiveAggressivePath(points, variant, aggressiveness)
        val state = path[seen.coerceIn(0, path.lastIndex)]
        val decision = { x: Float, y: Float -> state.w1 * x + state.w2 * y + state.bias }
        val wrong = errorsOf(points.take(seen), decision)
        val tau = f3(state.lastTau)
        val headline: String
        val highlight: String?
        val detail: String
        when {
            seen == 0 -> {
                headline = "No examples yet, so the line hasn't been placed."
                highlight = null
                detail = "Press play to stream the ${points.size} examples in one at a time."
            }
            state.lastLoss == 0.0 -> {
                headline = "This example is outside the margin, so τ = 0 and the line stays put."
                highlight = "outside the margin"
                detail = "That is the passive half: a confident, correct example teaches nothing."
            }
            state.lastIndex == 44 -> {
                highlight = "mislabelled"
                when (variant) {
                    PaVariant.HARD -> {
                        headline = "The mislabelled example forces a step of τ = $tau."
                        detail = "Hard PA has no cap, so it moves as far as it takes to fit this one point and wrecks a boundary that was already right."
                    }
                    PaVariant.PA_I -> {
                        headline = "The mislabelled example is capped at τ = $tau."
                        detail = "PA-I clips the step at C = ${f2(aggressiveness)}, so one bad label can only do bounded damage."
                    }
                    PaVariant.PA_II -> {
                        headline = "The mislabelled example is softened to τ = $tau."
                        detail = "PA-II adds 1/2C to the denominator, shrinking the step smoothly with no hard cutoff."
                    }
                }
            }
            else -> {
                headline = "Hinge loss is ${f2(state.lastLoss)}, so the line moves just far enough to put this example on the margin."
                highlight = "on the margin"
                detail = "That is the aggressive half. ${plural(state.updates, "update")} over $seen examples, ${plural(state.mistakes, "outright mistake")}."
            }
        }
        SurfaceResult(
            decision = decision,
            readout = "updates = ${state.updates} · τ = $tau · hinge = ${f2(state.lastLoss)}",
            headline = headline,
            highlight = highlight,
            detail = detail,
            ringed = if (seen in 1..points.size) setOf(seen - 1) else emptySet(),
            errors = wrong,
            marginBand = 1.0,
            unseenFrom = seen,
        )
    },
)

private fun gaussianNbConfig() = SurfaceConfig(
    intro = "Gaussian naive Bayes is QDA with the off-diagonal covariance terms forced to zero. \"Features are independent given the class\" is not an abstraction here — it is visible as ellipses that cannot tilt.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("Smoothing", "var", 0f..1.5f, 0f)),
    legend = listOf(LegendMark(EllipseColor, "Axis-aligned covariance", MarkKind.Line), errorLegend),
    evaluate = { points, values, _, _ ->
        val fit = fitGaussianNb(points, values[0].toDouble())
        val qda = fitQda(points)
        val wrong = errorsOf(points) { x, y -> fit.decision(x, y) }
        val qdaWrong = errorsOf(points) { x, y -> qda.decision(x, y) }.size
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readout = "NB = ${points.size - wrong.size}/${points.size} · QDA = ${points.size - qdaWrong}/${points.size} · params = 6",
            headline = "The ellipses can't tilt, because naive Bayes has no parameter for it.",
            highlight = "can't tilt",
            detail = "It fits 6 parameters to QDA's 11, and here that costs " +
                when {
                    wrong.size > qdaWrong -> "${plural(wrong.size - qdaWrong, "extra error")}. The independence assumption is false and you can see exactly where."
                    wrong.size < qdaWrong -> "nothing: it is ${plural(qdaWrong - wrong.size, "error")} ahead of QDA, because fewer parameters from the same data is often the better trade."
                    else -> "nothing. The assumption is false, yet the predictions are unaffected, which is why naive Bayes keeps working."
                },
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
        )
    },
)

private val surfaceConfigs: Map<String, () -> SurfaceConfig> = mapOf(
    "gaussian_nb" to ::gaussianNbConfig,
    "svm_rbf" to ::svmRbfConfig,
    "nu_svc" to ::nuSvcConfig,
    "lda" to ::ldaConfig,
    "qda" to ::qdaConfig,
    "passive_aggressive" to ::passiveAggressiveConfig,
)

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun DecisionSurfaceSection(topicId: String) {
    val config = remember(topicId) { (surfaceConfigs[topicId] ?: ::svmRbfConfig)() }
    var seed by remember(config) { mutableIntStateOf(5) }
    var variantIndex by remember(config) { mutableIntStateOf(0) }
    val points = remember(config, seed) { config.data(seed) }
    var values by remember(config) { mutableStateOf(FloatArray(config.sliders.size) { config.sliders[it].initial }) }
    // A stream lab opens on the finished pass; play restarts it from the first example.
    val playback = remember(config, points) {
        PlaybackState(points.size + 1, 450f).also { it.index = it.lastIndex }
    }
    val step = if (config.stream) playback.index else 0
    val result = remember(points, values, variantIndex, step) { config.evaluate(points, values, variantIndex, step) }

    val dock = LocalLabDock.current
    LabIntro(config.intro)

    val controls: @Composable () -> Unit = {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                config.sliders.forEachIndexed { index, slider ->
                    LabParamSlider(slider.name, slider.symbol, values[index], slider.range, slider.format) { v ->
                        values = values.copyOf().also { it[index] = v }
                    }
                }
            }
            if (config.stream) {
                HorizontalDivider(modifier = Modifier.padding(top = 14.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                LabTransportBar(
                    playback,
                    captions = null,
                    stepLabel = { "Example $it of ${points.size}" },
                    trailing = "New Data" to { seed += 1 },
                )
            } else if (dock == null) {
                SimButtonRow(
                    buttons = listOf(Triple("↻ New Data", SimColors.Grey) { seed += 1 }),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (config.variants.isNotEmpty()) {
            LabSegments(config.variants, variantIndex, Modifier.padding(bottom = 14.dp)) { variantIndex = it }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SurfaceCanvas(points = points, result = result)
                if (config.legend.isNotEmpty()) SurfaceLegend(config.legend, Modifier.padding(top = 12.dp))
                if (dock == null) {
                    ReadoutChips(result.readout, Modifier.padding(top = 16.dp))
                    SurfaceNarration(result, Modifier.padding(top = 14.dp))
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
                    controls()
                }
            }
        }
        if (dock != null) {
            ReadoutChips(result.readout, Modifier.padding(top = 14.dp))
            SurfaceNarration(result, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }

    if (dock != null) {
        // Re-handed every composition: the controls close over this composition's values.
        SideEffect {
            dock.controls = controls
            dock.navAction = if (config.stream) null else LabNavAction(Icons.Filled.Refresh, "New data") { seed += 1 }
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
private fun SurfaceNarration(result: SurfaceResult, modifier: Modifier) {
    val key = if (LocalDarkTheme.current) SimColors.Active else Color(0xFFB45309)
    val headline = buildAnnotatedString {
        val h = result.headline
        val at = result.highlight?.let { h.indexOf(it) } ?: -1
        if (at < 0) {
            append(h)
        } else {
            append(h.substring(0, at))
            withStyle(SpanStyle(color = key)) { append(result.highlight!!) }
            append(h.substring(at + result.highlight!!.length))
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(
            result.detail,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SurfaceLegend(items: List<LegendMark>, modifier: Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { mark ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val swatch = when (mark.kind) {
                    MarkKind.Ring -> Modifier.size(11.dp).border(2.dp, mark.color, CircleShape)
                    MarkKind.Dot -> Modifier.size(11.dp).background(mark.color, CircleShape)
                    MarkKind.Line -> Modifier.width(14.dp).height(3.dp).clip(CircleShape).background(mark.color)
                }
                Box(modifier = swatch)
                Text(
                    mark.label,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private const val ASPECT = 1.5f
private const val GRID_X = 60
private const val GRID_Y = 40

@Composable
private fun SurfaceCanvas(points: List<ClassPoint>, result: SurfaceResult) {
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ASPECT)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        // Data bounds plus a margin, then widened on one axis so x and y share a scale — rings stay round.
        val margin = 0.6f
        var xMin = (points.minOfOrNull { it.x } ?: -1f) - margin
        var xMax = (points.maxOfOrNull { it.x } ?: 1f) + margin
        var yMin = (points.minOfOrNull { it.y } ?: -1f) - margin
        var yMax = (points.maxOfOrNull { it.y } ?: 1f) + margin
        val xr = xMax - xMin
        val yr = yMax - yMin
        if (xr / yr < ASPECT) {
            val grow = (yr * ASPECT - xr) / 2; xMin -= grow; xMax += grow
        } else {
            val grow = (xr / ASPECT - yr) / 2; yMin -= grow; yMax += grow
        }
        fun sx(x: Float) = (x - xMin) / (xMax - xMin) * size.width
        fun sy(y: Float) = size.height - (y - yMin) / (yMax - yMin) * size.height

        // The decision function at every grid node; cells are tinted by the sign at their centre.
        val nodes = Array(GRID_X + 1) { gx ->
            DoubleArray(GRID_Y + 1) { gy ->
                result.decision(xMin + (xMax - xMin) * gx / GRID_X, yMin + (yMax - yMin) * gy / GRID_Y)
            }
        }
        val cw = size.width / GRID_X
        val ch = size.height / GRID_Y
        for (gx in 0 until GRID_X) {
            for (gy in 0 until GRID_Y) {
                val centre = nodes[gx][gy] + nodes[gx + 1][gy] + nodes[gx][gy + 1] + nodes[gx + 1][gy + 1]
                drawRect(
                    color = (if (centre >= 0) PositiveFill else NegativeFill).copy(alpha = 0.16f),
                    topLeft = Offset(gx * cw, size.height - (gy + 1) * ch),
                    size = Size(cw + 0.5f, ch + 0.5f),
                )
            }
        }

        fun nodeOffset(gx: Float, gy: Float) = Offset(gx * cw, size.height - gy * ch)
        result.marginBand?.let { band ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            for (level in listOf(band, -band)) {
                drawPath(
                    contourPath(nodes, level, ::nodeOffset),
                    color = accent.copy(alpha = 0.6f),
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dash),
                )
            }
        }
        drawPath(contourPath(nodes, 0.0, ::nodeOffset), color = accent, style = Stroke(width = 3.dp.toPx()))

        result.ellipses.forEach { ellipse ->
            val path = Path()
            ellipse.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(sx(x), sy(y)) else path.lineTo(sx(x), sy(y)) }
            drawPath(path, EllipseColor, style = Stroke(width = 2.dp.toPx()))
        }

        val r = 6.5.dp.toPx()
        val unseenFrom = result.unseenFrom ?: points.size
        points.forEachIndexed { i, p ->
            val c = Offset(sx(p.x), sy(p.y))
            val fill = if (p.label > 0) PositiveFill else NegativeFill
            if (i >= unseenFrom) {
                drawCircle(fill.copy(alpha = 0.3f), radius = r, center = c)
                return@forEachIndexed
            }
            drawCircle(fill, radius = r, center = c)
            drawCircle(ring, radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
            when {
                i in result.ringed -> drawCircle(SupportRing, radius = r + 4.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
                i in result.errors -> drawCircle(ErrorRing, radius = r + 4.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
    }
}

/**
 * Marching squares: the [level] contour of [nodes] as polylines. Segments are chained end to end so
 * a dashed stroke flows along the curve instead of restarting in every cell.
 */
private fun contourPath(nodes: Array<DoubleArray>, level: Double, at: (Float, Float) -> Offset): Path {
    val gx = nodes.size - 1
    val gy = nodes[0].size - 1
    // Crossings keyed by edge, so neighbouring cells share an endpoint exactly.
    fun lerp(a: Double, b: Double) = ((level - a) / (b - a)).toFloat().coerceIn(0f, 1f)
    val segments = ArrayList<Pair<Long, Long>>()
    val points = HashMap<Long, Offset>()
    fun hEdge(x: Int, y: Int): Long { // (x,y)→(x+1,y)
        val key = (x.toLong() shl 32) or (y.toLong() shl 1)
        points.getOrPut(key) { at(x + lerp(nodes[x][y], nodes[x + 1][y]), y.toFloat()) }
        return key
    }
    fun vEdge(x: Int, y: Int): Long { // (x,y)→(x,y+1)
        val key = (x.toLong() shl 32) or (y.toLong() shl 1) or 1L
        points.getOrPut(key) { at(x.toFloat(), y + lerp(nodes[x][y], nodes[x][y + 1])) }
        return key
    }
    for (x in 0 until gx) {
        for (y in 0 until gy) {
            val a = nodes[x][y] >= level // bottom-left
            val b = nodes[x + 1][y] >= level // bottom-right
            val c = nodes[x + 1][y + 1] >= level // top-right
            val d = nodes[x][y + 1] >= level // top-left
            val crossed = ArrayList<Long>(4)
            if (a != b) crossed += hEdge(x, y)
            if (b != c) crossed += vEdge(x + 1, y)
            if (c != d) crossed += hEdge(x, y + 1)
            if (d != a) crossed += vEdge(x, y)
            if (crossed.size >= 2) segments += crossed[0] to crossed[1]
            if (crossed.size == 4) segments += crossed[2] to crossed[3]
        }
    }
    val byEnd = HashMap<Long, MutableList<Int>>()
    segments.forEachIndexed { i, (p, q) ->
        byEnd.getOrPut(p) { mutableListOf() } += i
        byEnd.getOrPut(q) { mutableListOf() } += i
    }
    val used = BooleanArray(segments.size)
    val path = Path()
    fun walk(from: Long): List<Long> {
        val line = mutableListOf(from)
        var end = from
        while (true) {
            val next = byEnd[end]?.firstOrNull { !used[it] } ?: break
            used[next] = true
            val (p, q) = segments[next]
            end = if (p == end) q else p
            line += end
        }
        return line
    }
    for (i in segments.indices) {
        if (used[i]) continue
        used[i] = true
        val (p, q) = segments[i]
        // Extend both ways from this segment, then stitch into one polyline.
        val forward = walk(q)
        val backward = walk(p)
        val line = backward.reversed() + forward
        path.moveTo(points.getValue(line[0]).x, points.getValue(line[0]).y)
        for (k in 1 until line.size) path.lineTo(points.getValue(line[k]).x, points.getValue(line[k]).y)
    }
    return path
}

