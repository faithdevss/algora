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
import kotlin.math.roundToInt
import com.algora.app.core.ui.theme.IBMPlexMono
import androidx.compose.ui.text.style.TextAlign
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
private val UnseenColor = Color(0xFF6B7280)

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
    // The storyboard layout's chips; the older layout reads [readout].
    val chips: List<LabChip> = emptyList(),
    // Another model's boundary drawn thin and grey for comparison (LDA's line under QDA, the boundary
    // before a Passive-Aggressive update).
    val reference: ((Float, Float) -> Double)? = null,
    // The arithmetic of the step, in a strip under the plane.
    val formula: String? = null,
)

private class SurfaceSlider(
    val name: String,
    val symbol: String,
    val range: ClosedFloatingPointRange<Float>,
    val initial: Float,
    val format: (Float) -> String = { "%.2f".format(it) },
    // Storyboard layout: the stepper's increment and the picker label.
    val step: Float = 0.05f,
    val tab: String = name,
)

private class SurfaceConfig(
    val intro: String,
    val data: (Int) -> List<ClassPoint>,
    val sliders: List<SurfaceSlider>,
    val variants: List<String> = emptyList(),
    val legend: List<LegendMark> = emptyList(),
    /** Streams the points in one at a time; the step is how many have been seen. */
    val stream: Boolean = false,
    // The redesigned layout: chips and a story headline over a picker and one stepper, in place of
    // the readout and sliders. Opt-in, so the other surface labs render unchanged.
    val story: Boolean = false,
    // Story options for QDA, Gaussian NB and Passive-Aggressive; the defaults keep the other story labs as
    // they are. [storyLegend] replaces the class-dot legend; [solidEllipses] draws covariances as solid
    // lines; [greyUnseen] draws not-yet-streamed points grey; [solidMargins] draws ±1 as thin solid lines;
    // [startStep] is where a stream opens; [navReset] makes the nav button a reset instead of new data.
    val storyLegend: List<Triple<Color, SwatchStyle, String>>? = null,
    val solidEllipses: Boolean = false,
    val greyUnseen: Boolean = false,
    val solidMargins: Boolean = false,
    val startStep: Int? = null,
    val initialVariant: Int = 0,
    val navReset: Boolean = false,
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
        SurfaceSlider("Reach", "log₁₀ γ", -2f..1.2f, -0.4f, { "%.1f".format(it) }, step = 0.2f, tab = "Reach γ"),
        SurfaceSlider("Penalty", "log₁₀ C", -1f..3f, 1f, { "%.1f".format(it) }, step = 0.5f, tab = "Penalty C"),
    ),
    legend = listOf(supportLegend, errorLegend),
    story = true,
    evaluate = { points, values, _, _ ->
        val gamma = Math.pow(10.0, values[0].toDouble())
        val c = Math.pow(10.0, values[1].toDouble())
        val fit = trainSvm(points, rbfKernel(gamma), c)
        val wrong = fit.misclassified()
        val svs = fit.supportVectors.size
        val chips = listOf(
            LabChip("correct", "${points.size - wrong.size}/${points.size}", if (wrong.isEmpty()) StoryTone.Idle else StoryTone.Warn, good = wrong.isEmpty()),
            LabChip("SVs", "$svs"),
            LabChip("γ", f2(gamma)),
        )
        val (headline, detail) = when {
            gamma > 3.0 -> "γ is large, so the boundary breaks into {w:islands} around single points." to
                "Each support vector's reach has shrunk to almost nothing. That is memorization, and it will generalize badly."
            gamma < 0.05 -> "γ is small, so the kernel behaves {almost linearly}." to
                "Every point influences everywhere, and nothing that smooth can split two concentric rings."
            else -> "The boundary is a {closed curve}, something no linear model can draw." to
                "Only the $svs ringed points define it. The kernel compares pairs of points and never computes the higher-dimensional coordinates."
        }
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) }, readout = "", headline = headline, detail = detail,
            ringed = fit.supportVectors, errors = wrong, marginBand = 1.0, chips = chips,
        )
    },
)

private fun nuSvcConfig() = SurfaceConfig(
    intro = "ν replaces C with something you can actually reason about: it simultaneously upper-bounds the fraction of margin errors and lower-bounds the fraction of support vectors. Both are measured below.",
    data = { seed -> overlappingBlobs(seed, separation = 1.15f) },
    sliders = listOf(SurfaceSlider("Budget", "ν", 0.05f..0.8f, 0.3f, step = 0.05f)),
    legend = listOf(supportLegend, errorLegend),
    story = true,
    evaluate = { points, values, _, _ ->
        val nu = values[0].toDouble()
        val result = trainNuSvc(points, linearKernel(), nu)
        val errorFraction = result.marginErrorFraction
        val svFraction = result.svFraction
        val holds = errorFraction <= nu + 0.06 && svFraction >= nu - 0.06
        SurfaceResult(
            decision = { x, y -> result.fit.decision(x, y) },
            readout = "",
            headline = if (holds) {
                "{The bound holds}: margin errors ${f2(errorFraction)} ≤ ν ${f2(nu)} ≤ support vectors ${f2(svFraction)}."
            } else {
                "Margin errors ${f2(errorFraction)} and support vectors ${f2(svFraction)} sit {w:just outside} ν = ${f2(nu)}."
            },
            detail = if (holds) {
                "That guarantee is what ν gives you. C gives nothing comparable, which is why it needs a grid search."
            } else {
                "The bound is asymptotic, so on ${points.size} points it can miss slightly. The equivalent C here is ${f3(result.c)}."
            },
            ringed = result.fit.supportVectors,
            errors = result.fit.misclassified(),
            marginBand = 1.0,
            chips = listOf(
                LabChip("margin err", f2(errorFraction)),
                LabChip("ν", f2(nu), dot = SupportRing),
                LabChip("SVs", f2(svFraction)),
            ),
        )
    },
)

private fun ldaConfig() = SurfaceConfig(
    intro = "Two classes with genuinely different spreads. LDA assumes they share one covariance, so it pools them — and the boundary it produces is always a straight line.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("Shrinkage", "", 0f..0.95f, 0f, step = 0.05f)),
    legend = listOf(LegendMark(EllipseColor, "Shared covariance", MarkKind.Ring), errorLegend),
    story = true,
    evaluate = { points, values, _, _ ->
        val shrink = values[0].toDouble()
        val fit = fitLda(points, shrink)
        val wrong = errorsOf(points) { x, y -> fit.decision(x, y) }
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readout = "",
            headline = "Both ellipses are {the same shape}, so the boundary is a straight line.",
            detail = if (wrong.isEmpty()) {
                "On this sample the shared shape costs nothing. Tap new data until it does."
            } else {
                "The shared shape is too narrow for the stretched pink class. The ${plural(wrong.size, "red-ringed point")} are the cost."
            } + if (shrink > 0) " Shrinkage pulls the shared shape toward a circle." else "",
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
            chips = listOf(
                LabChip("correct", "${points.size - wrong.size}/${points.size}", good = true),
                LabChip("misclassified", "${wrong.size}", dot = ErrorRing),
            ),
        )
    },
)

private fun qdaConfig() = SurfaceConfig(
    intro = "The identical data as LDA, with the shared-covariance assumption dropped. Each class estimates its own, and the boundary stops being a line. λ blends each class's shape toward the shared one: 0 is QDA, 1 is LDA.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("Shrinkage", "λ", 0f..1f, 0.32f, step = 0.04f)),
    story = true,
    storyLegend = listOf(
        Triple(EllipseColor, SwatchStyle.Line, "Class covariance"),
        Triple(EllipseColor, SwatchStyle.DashedLine, "LDA boundary"),
        Triple(ErrorRing, SwatchStyle.Ring, "Misclassified"),
    ),
    solidEllipses = true,
    navReset = true,
    evaluate = { points, values, _, _ ->
        val lambda = values[0].toDouble()
        val fit = fitRda(points, lambda)
        val lda = fitLda(points)
        val wrong = errorsOf(points) { x, y -> fit.decision(x, y) }
        val n = points.size
        val qdaRight = n - errorsOf(points) { x, y -> fitQda(points).decision(x, y) }.size
        val ldaRight = n - errorsOf(points) { x, y -> lda.decision(x, y) }.size
        val right = n - wrong.size
        val l = f2(lambda)
        val (headline, detail) = when {
            lambda < 1e-4 -> "At λ = 0 each class keeps {its own shape}: this is plain QDA." to
                "LDA shares one shape and gets $ldaRight/$n. Raise λ when a class has too few points to trust its own covariance."
            lambda > 1 - 1e-4 -> "At λ = 1 both classes share {one shape}, so the boundary is LDA's straight line." to
                "That gets $ldaRight/$n against QDA's $qdaRight. Lower λ to let each class keep its own covariance."
            lambda < 0.5 -> "At λ = $l each class keeps most of its {own shape}, so the boundary still curves." to
                "LDA shares one shape and gets $ldaRight/$n. Raise λ when a class has too few points to trust its own covariance."
            else -> "At λ = $l both shapes are pulled toward {one shared shape}, so the boundary straightens." to
                "This blend gets $right/$n, QDA $qdaRight and LDA $ldaRight. λ trades a flexible boundary for a steadier estimate."
        }
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readout = "",
            headline = headline,
            detail = detail,
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
            chips = listOf(
                LabChip("correct", "$right/$n", good = true),
                LabChip("QDA λ=0", "$qdaRight/$n"),
                LabChip("LDA λ=1", "$ldaRight/$n"),
            ),
            reference = { x, y -> lda.decision(x, y) },
        )
    },
)

private fun passiveAggressiveConfig() = SurfaceConfig(
    intro = "One pass over a stream, one example at a time. Example 45 is deliberately mislabelled — step past it and watch what each variant does to a boundary that was already correct.",
    data = { seed -> streamData(seed) },
    sliders = listOf(SurfaceSlider("Aggressiveness", "C", 0.05f..3f, 0.25f, step = 0.05f)),
    variants = listOf("Hard", "PA-I", "PA-II"),
    stream = true,
    story = true,
    storyLegend = listOf(
        Triple(SupportRing, SwatchStyle.Ring, "Current"),
        Triple(EllipseColor, SwatchStyle.DashedLine, "Before update"),
        Triple(UnseenColor, SwatchStyle.Dot, "Not seen yet"),
    ),
    greyUnseen = true,
    solidMargins = true,
    startStep = 23,
    initialVariant = 1,
    navReset = true,
    evaluate = { points, values, variantIndex, step ->
        val seen = step.coerceIn(0, points.size)
        val c = values[0].toDouble()
        val variant = PaVariant.entries[variantIndex.coerceIn(0, 2)]
        val path = passiveAggressivePath(points, variant, c)
        val state = path[seen.coerceIn(0, path.lastIndex)]
        val before = path[(seen - 1).coerceIn(0, path.lastIndex)]
        val decision = { x: Float, y: Float -> state.w1 * x + state.w2 * y + state.bias }
        val loss = state.lastLoss
        val p = points.getOrNull(seen - 1)
        val norm = p?.let { it.x * it.x + it.y * it.y + 1.0 } ?: 1.0
        val raw = loss / norm
        val tau = state.lastTau
        val formula = when {
            seen == 0 -> null
            loss == 0.0 -> "ℓ = max(0, 1 − y·f(x)) = 0, so τ = {v:0}"
            variant == PaVariant.HARD -> "τ = ℓ / ‖x‖² = ${f2(loss)} / ${f2(norm)} = {v:${f2(tau)}}"
            variant == PaVariant.PA_I -> "τ = min(C, ℓ / ‖x‖²) = min(${f2(c)}, ${f2(loss)} / ${f2(norm)}) = {v:${f2(tau)}}"
            else -> "τ = ℓ / (‖x‖² + 1/2C) = ${f2(loss)} / (${f2(norm)} + ${f2(1 / (2 * c))}) = {v:${f2(tau)}}"
        }
        val where = if (p != null && p.label * (before.w1 * p.x + before.w2 * p.y + before.bias) <= 0) "On the wrong side" else "Inside the margin"
        val (headline, detail) = when {
            seen == 0 -> "No examples yet, so the line {hasn't been placed}." to
                "Next Example streams the ${points.size} examples in one at a time."
            loss == 0.0 -> "This example is {outside the margin}, so τ = 0 and the line stays put." to
                "That is the passive half: a confident, correct example teaches nothing."
            state.lastIndex == 44 -> when (variant) {
                PaVariant.HARD -> "The {mislabelled} example forces a step of τ = ${f2(tau)}." to
                    "Hard PA has no cap, so it moves as far as it takes to fit this one point and wrecks a boundary that was already right."
                PaVariant.PA_I -> "The {mislabelled} example is capped at τ = ${f2(tau)}." to
                    "PA-I clips the step at C = ${f2(c)}, so one bad label can only do bounded damage."
                PaVariant.PA_II -> "The {mislabelled} example is softened to τ = ${f2(tau)}." to
                    "PA-II adds 1/2C to the denominator, shrinking the step smoothly with no hard cutoff."
            }
            variant == PaVariant.PA_I && raw > c + 1e-9 -> {
                val share = tau / raw
                val far = if (share in 0.45..0.55) "half as far" else "${(share * 100).roundToInt()}% as far"
                "$where, the loss asks for τ = ${f2(raw)}, but PA-I {caps it at C}." to
                    "So the line moves $far as Hard PA would. A lower C lets noisy examples move it less."
            }
            variant == PaVariant.PA_II -> "$where, PA-II softens the step to τ = ${f2(tau)} {instead of ${f2(raw)}}." to
                "The 1/2C term shrinks every step smoothly. A lower C shrinks it more."
            else -> "$where, so the line moves {just far enough} to put this example on the margin." to
                "That is the aggressive half: τ = ${f2(tau)}. ${plural(state.updates, "update")} over $seen examples, ${plural(state.mistakes, "outright mistake")}."
        }
        SurfaceResult(
            decision = decision,
            readout = "",
            headline = headline,
            detail = detail,
            ringed = if (seen in 1..points.size) setOf(seen - 1) else emptySet(),
            marginBand = 1.0,
            unseenFrom = seen,
            chips = listOf(
                LabChip("example", "$seen / ${points.size}"),
                LabChip("hinge", f2(loss), tint = StoryTone.Active),
                LabChip("τ", f2(tau), tint = StoryTone.Answer),
            ),
            reference = if (seen >= 1 && loss > 0.0) { x, y -> before.w1 * x + before.w2 * y + before.bias } else null,
            formula = formula,
        )
    },
)

/** var_smoothing as sklearn has it: 10^e times the largest feature variance, added to every variance. */
private fun smoothingLabel(e: Float): String = e.roundToInt().let { if (it == 0) "1" else "1e$it" }

private fun gaussianNbConfig() = SurfaceConfig(
    intro = "Gaussian naive Bayes is QDA with the off-diagonal covariance terms forced to zero. \"Features are independent given the class\" is not an abstraction here — it is visible as ellipses that cannot tilt.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("Smoothing", "var", -9f..0f, -9f, { smoothingLabel(it) }, step = 1f)),
    story = true,
    storyLegend = listOf(
        Triple(EllipseColor, SwatchStyle.Line, "Axis-aligned covariance"),
        Triple(EllipseColor, SwatchStyle.DashedLine, "QDA boundary"),
        Triple(ErrorRing, SwatchStyle.Ring, "Misclassified"),
    ),
    solidEllipses = true,
    navReset = true,
    evaluate = { points, values, _, _ ->
        val e = values[0].roundToInt()
        val maxVar = fitQda(points).let { q -> listOf(q.negative, q.positive).maxOf { maxOf(it.covariance[0][0], it.covariance[1][1]) } }
        val fit = fitGaussianNb(points, Math.pow(10.0, e.toDouble()) * maxVar)
        val qda = fitQda(points)
        val n = points.size
        val wrong = errorsOf(points) { x, y -> fit.decision(x, y) }
        val right = n - wrong.size
        val qdaRight = n - errorsOf(points) { x, y -> qda.decision(x, y) }.size
        val cost = when {
            right < qdaRight -> "That costs ${if (qdaRight - right == 1) "one point" else "${qdaRight - right} points"} against QDA: $right/$n against $qdaRight. The independence assumption is wrong here, but it barely moves the boundary."
            right > qdaRight -> "It even beats QDA, $right/$n against $qdaRight: fewer parameters from the same data can be the better trade."
            else -> "Here it costs nothing: both get $right/$n. The independence assumption is wrong, yet the predictions survive."
        }
        val (headline, detail) = if (e >= -2) {
            "Smoothing of ${smoothingLabel(e.toFloat())} {swells both ellipses}, so the boundary drifts." to
                "Variance smoothing only guards against a zero variance. This much reshapes the classes: $right/$n against QDA's $qdaRight."
        } else {
            "The pink ellipse {can't tilt}, because naive Bayes has no parameter for it." to cost
        }
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readout = "",
            headline = headline,
            detail = detail,
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
            chips = listOf(
                LabChip("NB", "$right/$n", good = true),
                LabChip("QDA", "$qdaRight/$n"),
                LabChip("params", "6 vs 11"),
            ),
            reference = { x, y -> qda.decision(x, y) },
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
    var variantIndex by remember(config) { mutableIntStateOf(config.initialVariant) }
    val points = remember(config, seed) { config.data(seed) }
    var values by remember(config) { mutableStateOf(FloatArray(config.sliders.size) { config.sliders[it].initial }) }
    // A stream lab opens on the finished pass; play restarts it from the first example.
    val playback = remember(config, points) {
        PlaybackState(points.size + 1, 450f).also { it.index = config.startStep?.coerceIn(0, it.lastIndex) ?: it.lastIndex }
    }
    val step = if (config.stream) playback.index else 0
    val result = remember(points, values, variantIndex, step) { config.evaluate(points, values, variantIndex, step) }

    val dock = LocalLabDock.current
    LabIntro(config.intro)
    if (config.story) {
        StorySurfaceLayout(
            config, points, result, values,
            onValues = { values = it },
            onNewData = { seed += 1 },
            variantIndex = variantIndex,
            onVariant = { variantIndex = it },
            playback = playback,
            onReset = {
                seed = 5
                variantIndex = config.initialVariant
                values = FloatArray(config.sliders.size) { config.sliders[it].initial }
                playback.jump(config.startStep ?: playback.lastIndex)
            },
        )
        return
    }

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

// The redesigned layout: the plane and a legend of what is on it, chips, a story headline, then a
// picker over one stepper (docked in thumb reach when the screen has a dock).
@Composable
private fun StorySurfaceLayout(
    config: SurfaceConfig,
    points: List<ClassPoint>,
    result: SurfaceResult,
    values: FloatArray,
    onValues: (FloatArray) -> Unit,
    onNewData: () -> Unit,
    variantIndex: Int,
    onVariant: (Int) -> Unit,
    playback: PlaybackState,
    onReset: () -> Unit,
) {
    val dock = LocalLabDock.current
    var selected by remember(config) { mutableIntStateOf(0) }
    val legend = config.storyLegend
        ?.filter { it.third != errorLegend.label || result.errors.isNotEmpty() }
        ?: (
            listOf(
                Triple(NegativeFill, SwatchStyle.Dot, "Class 0"),
                Triple(PositiveFill, SwatchStyle.Dot, "Class 1"),
            ) + config.legend.filter { it !== errorLegend || result.errors.isNotEmpty() }.map { Triple(it.color, SwatchStyle.Ring, it.label) }
            )
    val controls: @Composable () -> Unit = {
        Column {
        LabParamControls(
            params = config.sliders.mapIndexed { i, slider ->
                LabParam(slider.tab, slider.name, slider.symbol, slider.format(values[i]), values[i] > slider.range.start + 1e-4f, values[i] < slider.range.endInclusive - 1e-4f)
            },
            selected = selected,
            onSelect = { selected = it },
            onStep = { i, delta ->
                val slider = config.sliders[i]
                val next = (Math.round((values[i] + delta * slider.step) / slider.step) * slider.step).coerceIn(slider.range.start, slider.range.endInclusive)
                onValues(values.copyOf().also { it[i] = next })
            },
        )
        // A stream steps one example at a time with a labelled action in place of a transport.
        if (config.stream) {
            LabBackActionRow(
                action = if (playback.atEnd) "Start Over" else "Next Example",
                backEnabled = playback.index > 0,
                onBack = { playback.stepBack() },
                onAction = { if (playback.atEnd) playback.jump(0) else playback.stepForward() },
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (config.variants.isNotEmpty()) {
            LabSegments(config.variants, variantIndex, Modifier.padding(bottom = 14.dp), onVariant)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SurfaceCanvas(
                    points = points,
                    result = result,
                    dottedEllipses = !config.solidEllipses,
                    greyUnseen = config.greyUnseen,
                    solidMargins = config.solidMargins,
                )
                result.formula?.let { StoryFormulaStrip(it, Modifier.padding(top = 12.dp)) }
                StoryLegendRow(legend, Modifier.padding(top = 14.dp))
            }
        }
        LabChips(result.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(result.headline, result.detail, Modifier.padding(top = 16.dp))
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
            SimButtonRow(
                buttons = listOf(
                    if (config.navReset) Triple("↻ Reset", SimColors.Grey) { onReset() } else Triple("↻ New Data", SimColors.Grey) { onNewData() },
                ),
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = if (config.navReset) LabNavAction(Icons.Filled.Refresh, "Reset") { onReset() } else LabNavAction(Icons.Filled.Refresh, "New data") { onNewData() }
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

/** The step's arithmetic, centred in a tinted strip; wraps to two lines when it has to. */
@Composable
private fun StoryFormulaStrip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            storyAnnotated(text),
            fontFamily = IBMPlexMono,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
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
private fun SurfaceCanvas(
    points: List<ClassPoint>,
    result: SurfaceResult,
    dottedEllipses: Boolean = false,
    greyUnseen: Boolean = false,
    solidMargins: Boolean = false,
) {
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
            val dash = if (solidMargins) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            for (level in listOf(band, -band)) {
                drawPath(
                    contourPath(nodes, level, ::nodeOffset),
                    color = accent.copy(alpha = 0.6f),
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dash),
                )
            }
        }
        result.reference?.let { reference ->
            val refNodes = Array(GRID_X + 1) { gx ->
                DoubleArray(GRID_Y + 1) { gy -> reference(xMin + (xMax - xMin) * gx / GRID_X, yMin + (yMax - yMin) * gy / GRID_Y) }
            }
            drawPath(contourPath(refNodes, 0.0, ::nodeOffset), color = EllipseColor.copy(alpha = 0.85f), style = Stroke(width = 1.5.dp.toPx()))
        }
        drawPath(contourPath(nodes, 0.0, ::nodeOffset), color = accent, style = Stroke(width = 3.dp.toPx()))

        result.ellipses.forEach { ellipse ->
            val path = Path()
            ellipse.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(sx(x), sy(y)) else path.lineTo(sx(x), sy(y)) }
            drawPath(
                path,
                if (dottedEllipses) EllipseColor.copy(alpha = 0.7f) else EllipseColor,
                style = if (dottedEllipses) {
                    Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
                } else {
                    Stroke(width = 2.dp.toPx())
                },
            )
        }

        val r = 6.5.dp.toPx()
        val unseenFrom = result.unseenFrom ?: points.size
        points.forEachIndexed { i, p ->
            val c = Offset(sx(p.x), sy(p.y))
            val fill = if (p.label > 0) PositiveFill else NegativeFill
            if (i >= unseenFrom) {
                if (greyUnseen) {
                    drawCircle(UnseenColor, radius = r, center = c)
                    drawCircle(ring, radius = r, center = c, style = Stroke(width = 1.5.dp.toPx()))
                } else {
                    drawCircle(fill.copy(alpha = 0.3f), radius = r, center = c)
                }
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

