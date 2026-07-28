package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.AlgoraCodeStyle
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Decision-surface lab ─────────────────────────────────────────────────────
// ClassifierPlaygroundSection draws a straight line because its model IS a straight line. These
// topics need curved boundaries (RBF kernels, QDA's conic), class-conditional ellipses, support
// vectors and a scrubbable online update, so this renders an arbitrary decision function instead:
// evaluate it on a grid, tint each cell by predicted class with alpha from confidence, and let the
// boundary emerge where the tint flips. Everything shown is computed in DecisionSurfaceMath.kt.

private val NegativeFill = Color(0xFF6366F1)
private val PositiveFill = Color(0xFF16A34A)
private val SupportRing = Color(0xFFF59E0B)
private val ErrorRing = Color(0xFFEF4444)
private val EllipseColor = Color(0xFF94A3B8)

private class SurfaceReadout(val value: String, val label: String)

private class SurfaceResult(
    val decision: (Float, Float) -> Double,
    val readouts: List<SurfaceReadout>,
    val note: String,
    // Circled in amber — support vectors, or the example currently being processed.
    val ringed: Set<Int> = emptySet(),
    // Circled in red — misclassified under the current fit.
    val errors: Set<Int> = emptySet(),
    val ellipses: List<List<Pair<Float, Float>>> = emptyList(),
    // Extra iso-contours to shade, e.g. the ±1 margin band of an SVM.
    val marginBand: Double? = null,
)

private class SurfaceSlider(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val initial: Float,
    val steps: Int = 0,
    val format: (Float) -> String = { "%.2f".format(it) },
)

private class SurfaceVariant(val label: String, val index: Int)

private class SurfaceConfig(
    val intro: String,
    val data: (Int) -> List<ClassPoint>,
    val sliders: List<SurfaceSlider>,
    val variants: List<String> = emptyList(),
    val legend: List<Pair<Color, String>> = emptyList(),
    val evaluate: (List<ClassPoint>, FloatArray, Int) -> SurfaceResult,
)

// ── Configs ──────────────────────────────────────────────────────────────────

private fun svmRbfConfig() = SurfaceConfig(
    intro = "Two concentric rings — not linearly separable by any line at all. γ controls how far each support vector's influence reaches; C controls how much margin violation is tolerated.",
    data = { seed -> ringData(seed) },
    sliders = listOf(
        SurfaceSlider("log₁₀ γ", -2f..1.2f, -0.4f) { "1e${"%.1f".format(it)}" },
        SurfaceSlider("log₁₀ C", -1f..3f, 1f) { "1e${"%.1f".format(it)}" },
    ),
    legend = listOf(SupportRing to "Support vector", PositiveFill to "Inner", NegativeFill to "Outer"),
    evaluate = { points, values, _ ->
        val gamma = Math.pow(10.0, values[0].toDouble())
        val c = Math.pow(10.0, values[1].toDouble())
        val fit = trainSvm(points, rbfKernel(gamma), c)
        val wrong = fit.misclassified()
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readouts = listOf(
                SurfaceReadout("${fit.supportVectors.size}/${points.size}", "Support vectors"),
                SurfaceReadout("${points.size - wrong.size}/${points.size}", "Correct"),
                SurfaceReadout("%.2f".format(gamma), "γ"),
            ),
            note = when {
                gamma > 3.0 -> "γ is large, so each support vector's influence is nearly a point. The boundary is islands around individual training points — memorization, and it will generalize badly."
                gamma < 0.05 -> "γ is small, so every point influences everywhere and the kernel behaves almost linearly. Two concentric rings cannot be split by something this smooth."
                else -> "The boundary is a closed curve, which no linear model can produce. The kernel trick got it without ever computing coordinates in the higher-dimensional space — only inner products between pairs of points."
            },
            ringed = fit.supportVectors,
            errors = wrong,
            marginBand = 1.0,
        )
    },
)

private fun nuSvcConfig() = SurfaceConfig(
    intro = "ν replaces C with something you can actually reason about: it simultaneously upper-bounds the fraction of margin errors and lower-bounds the fraction of support vectors. Both are measured below.",
    data = { seed -> overlappingBlobs(seed, separation = 1.15f) },
    sliders = listOf(SurfaceSlider("ν", 0.05f..0.8f, 0.3f)),
    legend = listOf(SupportRing to "Support vector", ErrorRing to "Misclassified"),
    evaluate = { points, values, _ ->
        val nu = values[0].toDouble()
        val result = trainNuSvc(points, linearKernel(), nu)
        val errorFraction = result.marginErrorFraction
        val svFraction = result.svFraction
        val boundHolds = errorFraction <= nu + 0.06 && svFraction >= nu - 0.06
        SurfaceResult(
            decision = { x, y -> result.fit.decision(x, y) },
            readouts = listOf(
                SurfaceReadout("%.2f".format(nu), "Requested ν"),
                SurfaceReadout("%.2f".format(errorFraction), "Margin errors"),
                SurfaceReadout("%.2f".format(svFraction), "Support vectors"),
            ),
            note = if (boundHolds) {
                "The bound holds: margin errors ${"%.2f".format(errorFraction)} ≤ ν ${"%.2f".format(nu)} ≤ support vectors ${"%.2f".format(svFraction)}. That sandwich is exactly what ν buys — C tells you nothing comparable, which is why it always has to be found by grid search."
            } else {
                "Measured margin errors ${"%.2f".format(errorFraction)} and support vectors ${"%.2f".format(svFraction)} against ν = ${"%.2f".format(nu)}. The bound is asymptotic, so on ${points.size} points it can sit slightly outside — the equivalent C found here was ${"%.3f".format(result.c)}."
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
    sliders = listOf(SurfaceSlider("Shrinkage", 0f..0.95f, 0f)),
    legend = listOf(EllipseColor to "Pooled covariance", PositiveFill to "Class +1", NegativeFill to "Class −1"),
    evaluate = { points, values, _ ->
        val shrink = values[0].toDouble()
        val fit = fitLda(points, shrink)
        val wrong = points.indices.filter {
            val d = fit.decision(points[it].x, points[it].y)
            (d >= 0 && points[it].label < 0) || (d < 0 && points[it].label > 0)
        }.toSet()
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readouts = listOf(
                SurfaceReadout("${points.size - wrong.size}/${points.size}", "Correct"),
                SurfaceReadout("%.2f".format(shrink), "Shrinkage"),
                SurfaceReadout("5", "Parameters"),
            ),
            note = "Both ellipses are identical because both classes are forced to share the pooled covariance — that is the assumption, and it is what makes the quadratic terms cancel and leave a line. " +
                if (wrong.isEmpty()) {
                    "On this sample the classes are separated well enough that the wrong shape costs nothing; press New Data until it does."
                } else {
                    "The pooled shape is visibly wrong for the wide class here, and the ${wrong.size} circled point${if (wrong.size == 1) "" else "s"} are what that costs."
                },
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
        )
    },
)

private fun qdaConfig() = SurfaceConfig(
    intro = "The identical data as LDA, with the shared-covariance assumption dropped. Each class estimates its own, and the boundary stops being a line.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("Shrinkage", 0f..0.95f, 0f)),
    legend = listOf(EllipseColor to "Per-class covariance", PositiveFill to "Class +1", NegativeFill to "Class −1"),
    evaluate = { points, values, _ ->
        val shrink = values[0].toDouble()
        val fit = fitQda(points, shrink)
        val lda = fitLda(points)
        val wrong = points.indices.filter {
            val d = fit.decision(points[it].x, points[it].y)
            (d >= 0 && points[it].label < 0) || (d < 0 && points[it].label > 0)
        }.toSet()
        val ldaWrong = points.indices.count {
            val d = lda.decision(points[it].x, points[it].y)
            (d >= 0 && points[it].label < 0) || (d < 0 && points[it].label > 0)
        }
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readouts = listOf(
                SurfaceReadout("${points.size - wrong.size}/${points.size}", "QDA correct"),
                SurfaceReadout("${points.size - ldaWrong}/${points.size}", "LDA correct"),
                SurfaceReadout("11", "Parameters"),
            ),
            note = "The ellipses now differ in size and orientation, and the boundary is a conic rather than a line. QDA fits 11 parameters against LDA's 5, and on this sample " +
                when {
                    wrong.size < ldaWrong -> "that buys accuracy — ${ldaWrong - wrong.size} fewer error${if (ldaWrong - wrong.size == 1) "" else "s"} than LDA."
                    wrong.size > ldaWrong -> "it does not pay: QDA is ${wrong.size - ldaWrong} error${if (wrong.size - ldaWrong == 1) "" else "s"} worse than LDA, because two covariances estimated from ${points.size / 2} points each are noisier than one pooled estimate that is merely biased."
                    else -> "the two tie, so the extra six parameters bought nothing here."
                } +
                " That crossover is the bias-variance tradeoff itself, and shrinkage is the dial between them.",
            errors = wrong,
            ellipses = listOf(fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)),
        )
    },
)

private fun passiveAggressiveConfig() = SurfaceConfig(
    intro = "One pass over a stream, one example at a time. Example 45 is deliberately mislabelled — scrub past it and watch what each variant does to a boundary that was already correct.",
    data = { seed -> streamData(seed) },
    sliders = listOf(
        SurfaceSlider("Examples seen", 0f..60f, 60f, steps = 59) { it.roundToInt().toString() },
        SurfaceSlider("Aggressiveness C", 0.05f..3f, 1f),
    ),
    variants = listOf("Hard", "PA-I", "PA-II"),
    legend = listOf(SupportRing to "Current example", ErrorRing to "Misclassified"),
    evaluate = { points, values, variantIndex ->
        val seen = values[0].roundToInt().coerceIn(0, points.size)
        val aggressiveness = values[1].toDouble()
        val variant = PaVariant.entries[variantIndex.coerceIn(0, 2)]
        val path = passiveAggressivePath(points, variant, aggressiveness)
        val state = path[seen.coerceIn(0, path.lastIndex)]
        val visible = points.take(seen)
        val wrong = visible.indices.filter {
            val d = state.w1 * points[it].x + state.w2 * points[it].y + state.bias
            (d >= 0 && points[it].label < 0) || (d < 0 && points[it].label > 0)
        }.toSet()
        SurfaceResult(
            decision = { x, y -> state.w1 * x + state.w2 * y + state.bias },
            readouts = listOf(
                SurfaceReadout("${state.updates}", "Updates"),
                SurfaceReadout("%.3f".format(state.lastTau), "Last step τ"),
                SurfaceReadout("%.2f".format(state.lastLoss), "Last hinge loss"),
            ),
            note = when {
                state.lastLoss == 0.0 && seen > 0 ->
                    "Hinge loss is zero on this example, so τ = 0 and the weights did not move at all. That is the \"passive\" half — a correctly classified example outside the margin teaches nothing."
                state.lastIndex == 44 -> when (variant) {
                    PaVariant.HARD -> "The mislabelled example. With no cap, τ = ${"%.3f".format(state.lastTau)} — the update is whatever size it takes to classify this one point correctly, and it wrecks a boundary that was already right."
                    PaVariant.PA_I -> "The mislabelled example, with τ capped at C = ${"%.2f".format(aggressiveness)}. The step is clipped to ${"%.3f".format(state.lastTau)}, so one bad label can only do bounded damage."
                    PaVariant.PA_II -> "The mislabelled example. PA-II softens rather than clips — the extra 1/2C in the denominator shrinks τ to ${"%.3f".format(state.lastTau)} smoothly, with no hard cutoff."
                }
                else ->
                    "Loss ${"%.2f".format(state.lastLoss)} > 0, so the update is exactly large enough to push this example to the margin — that is the \"aggressive\" half. ${state.updates} updates over $seen examples, ${state.mistakes} outright mistakes."
            },
            ringed = if (seen in 1..points.size) setOf(seen - 1) else emptySet(),
            errors = wrong,
            marginBand = 1.0,
        )
    },
)

private fun gaussianNbConfig() = SurfaceConfig(
    intro = "Gaussian naive Bayes is QDA with the off-diagonal covariance terms forced to zero. \"Features are independent given the class\" is not an abstraction here — it is visible as ellipses that cannot tilt.",
    data = { seed -> unequalCovarianceBlobs(seed) },
    sliders = listOf(SurfaceSlider("var_smoothing", 0f..1.5f, 0f)),
    legend = listOf(EllipseColor to "Axis-aligned covariance", PositiveFill to "Class +1", NegativeFill to "Class −1"),
    evaluate = { points, values, _ ->
        val smoothing = values[0].toDouble()
        val fit = fitGaussianNb(points, smoothing)
        val qda = fitQda(points)
        fun errorsOf(model: DiscriminantFit) = points.indices.filter {
            val d = model.decision(points[it].x, points[it].y)
            (d >= 0 && points[it].label < 0) || (d < 0 && points[it].label > 0)
        }.toSet()
        val wrong = errorsOf(fit)
        val qdaWrong = errorsOf(qda).size
        SurfaceResult(
            decision = { x, y -> fit.decision(x, y) },
            readouts = listOf(
                SurfaceReadout("${points.size - wrong.size}/${points.size}", "GaussianNB correct"),
                SurfaceReadout("${points.size - qdaWrong}/${points.size}", "QDA correct"),
                SurfaceReadout("6", "Parameters"),
            ),
            note = "The positive class is genuinely tilted, but naive Bayes has no parameter that can represent a tilt — every ellipse is locked to the axes. It fits 6 parameters against QDA's 11, and here that costs " +
                when {
                    wrong.size > qdaWrong -> "${wrong.size - qdaWrong} extra error${if (wrong.size - qdaWrong == 1) "" else "s"}. The independence assumption is false and you can see exactly where."
                    wrong.size < qdaWrong -> "nothing — it is ${qdaWrong - wrong.size} error${if (qdaWrong - wrong.size == 1) "" else "s"} ahead of QDA, because fewer parameters estimated from the same data is often the better trade even when the assumption is wrong."
                    else -> "nothing on this sample: the assumption is false, yet the classifier is unaffected. That gap between \"wrong model\" and \"wrong prediction\" is why naive Bayes keeps working."
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
    var seed by remember(config) { mutableStateOf(5) }
    var variantIndex by remember(config) { mutableStateOf(0) }
    val points = remember(config, seed) { config.data(seed) }
    var values by remember(config) { mutableStateOf(FloatArray(config.sliders.size) { config.sliders[it].initial }) }
    val result = remember(points, values, variantIndex) { config.evaluate(points, values, variantIndex) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (config.variants.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.variants.forEachIndexed { i, label ->
                        val selected = i == variantIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { variantIndex = i }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                SurfaceCanvas(points = points, result = result)
            }

            if (config.legend.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    config.legend.forEach { (color, label) -> SurfaceLegend(color, label) }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                result.readouts.forEach { readout ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            readout.value,
                            style = AlgoraCodeStyle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            readout.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            config.sliders.forEachIndexed { index, slider ->
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(slider.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(slider.format(values[index]), style = AlgoraCodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = values[index],
                        onValueChange = { v -> values = values.copyOf().also { it[index] = v } },
                        valueRange = slider.range,
                        steps = slider.steps,
                    )
                }
            }

            Text(
                result.note,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Box(modifier = Modifier.height(10.dp))
            SimButtonRow(
                buttons = listOf(Triple("↻ New Data", SimColors.Grey) { seed += 1 }),
            )
        }
    }
}

@Composable
private fun SurfaceLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private const val GRID = 42

@Composable
private fun SurfaceCanvas(points: List<ClassPoint>, result: SurfaceResult) {
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        val pad = 8f
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val margin = 0.6f
        val xMin = (xs.minOrNull() ?: -1f) - margin
        val xMax = (xs.maxOrNull() ?: 1f) + margin
        val yMin = (ys.minOrNull() ?: -1f) - margin
        val yMax = (ys.maxOrNull() ?: 1f) + margin

        fun sx(x: Float) = pad + (x - xMin) / (xMax - xMin) * (size.width - 2 * pad)
        fun sy(y: Float) = size.height - pad - (y - yMin) / (yMax - yMin) * (size.height - 2 * pad)

        val cellW = (size.width - 2 * pad) / GRID
        val cellH = (size.height - 2 * pad) / GRID

        // Sample the decision function once per cell. Alpha scales with confidence, so the boundary
        // reads as the seam where the two tints meet rather than needing a contour tracer.
        val values = Array(GRID) { gx ->
            DoubleArray(GRID) { gy ->
                val x = xMin + (xMax - xMin) * (gx + 0.5f) / GRID
                val y = yMin + (yMax - yMin) * (gy + 0.5f) / GRID
                result.decision(x, y)
            }
        }
        val peak = values.flatMap { it.toList() }.maxOfOrNull { abs(it) }?.takeIf { it > 1e-9 } ?: 1.0

        for (gx in 0 until GRID) {
            for (gy in 0 until GRID) {
                val v = values[gx][gy]
                val strength = (abs(v) / peak).coerceIn(0.0, 1.0)
                val band = result.marginBand
                val inBand = band != null && abs(v) < band
                val color = if (v >= 0) PositiveFill else NegativeFill
                drawRect(
                    color = color,
                    topLeft = Offset(pad + gx * cellW, size.height - pad - (gy + 1) * cellH),
                    size = Size(cellW + 0.5f, cellH + 0.5f),
                    // Inside the margin band the tint is deliberately washed out, so the band shows
                    // up as a pale corridor around the boundary.
                    alpha = if (inBand) 0.07f else (0.10f + 0.42f * strength).toFloat(),
                )
            }
        }

        result.ellipses.forEach { ellipse ->
            ellipse.zipWithNext().forEach { (a, b) ->
                drawLine(
                    color = EllipseColor,
                    start = Offset(sx(a.first), sy(a.second)),
                    end = Offset(sx(b.first), sy(b.second)),
                    strokeWidth = 3f,
                )
            }
        }

        points.forEachIndexed { i, p ->
            val cx = sx(p.x)
            val cy = sy(p.y)
            drawCircle(
                color = if (p.label > 0) PositiveFill else NegativeFill,
                radius = 8f,
                center = Offset(cx, cy),
            )
            drawCircle(color = Color.White, radius = 8f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
            if (i in result.ringed) {
                drawCircle(color = SupportRing, radius = 13f, center = Offset(cx, cy), style = Stroke(width = 3f))
            }
            if (i in result.errors) {
                drawCircle(color = ErrorRing, radius = 17f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
            }
        }
    }
}
