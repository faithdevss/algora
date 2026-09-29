package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── Regression storyboards ───────────────────────────────────────────────────
// The eleven regression estimators as one-figure labs: a plot in the card (with a coefficient strip
// under it for the sparse fits), a legend of what is on it, chips, a headline that says what the
// current setting shows, then a picker over one stepper, docked in thumb reach. Every fit is computed
// by the real algorithm in RegressionLabMath.kt, on data sized so the numbers read in unit scale.

internal val regressionStoryTopicIds = setOf(
    "polynomial_regression",
    "ridge_regression",
    "lasso_regression",
    "elasticnet_regression",
    "stepwise_regression",
    "robust_regression",
    "quantile_regression",
    "bayesian_ridge",
    "poisson_regression",
    "isotonic_regression",
    "lars",
)

private enum class RegDotKind { Plain, Below, HeldOut, Discarded }

private class RegDot(val x: Double, val y: Double, val kind: RegDotKind = RegDotKind.Plain)

private class RegLine(val points: List<Pair<Double, Double>>, val fit: Boolean)

private class RegBand(val lower: List<Pair<Double, Double>>, val upper: List<Pair<Double, Double>>)

/** A shaded x-range: a block PAVA pooled, or a stretch with no data (labelled). */
private class RegColumn(val x0: Double, val x1: Double, val gap: Boolean)

/** One coefficient in the strip under the plot; [size] is |β| over the largest, 0 when zeroed. */
private class RegBar(val label: String, val size: Double)

private enum class RegSwatch { Line, Dashed, Fill, Dot, Ring }

private enum class RegInk { Fit, Reference, Point, Blue, Red, Band, Pool }

private class RegKey(val swatch: RegSwatch, val ink: RegInk, val label: String)

private class RegFrame(
    val dots: List<RegDot>,
    val lines: List<RegLine>,
    val headline: String,
    val body: String,
    val chips: List<Pair<String, String>>,
    val legend: List<RegKey> = emptyList(),
    val band: RegBand? = null,
    val columns: List<RegColumn> = emptyList(),
    val negativeZone: Boolean = false,
    val bars: List<RegBar> = emptyList(),
)

/** A stepper over a fixed ladder of values; [initial] is an index into [values]. */
private class RegParam(
    val tab: String,
    val name: String,
    val symbol: String,
    val values: List<Double>,
    val initial: Int,
    val format: (Double) -> String,
)

/** A solve button: beside the picker ([inline]) or full width under the stepper. */
private class RegAction(val label: String, val inline: Boolean, val solve: (List<Int>) -> List<Int>)

private class RegStoryConfig(
    val params: List<RegParam>,
    val evaluate: (List<Int>) -> RegFrame,
    val initialTab: Int = 0,
    val action: RegAction? = null,
) {
    val initial: List<Int> get() = params.map { it.initial }
}

// ── Formatting ──

/** Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−". */
private fun rfx(v: Double, d: Int = 3): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = floor(abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

/** Two significant-ish digits for a norm that runs from tens down to fractions. */
private fun rShort(v: Double): String = if (abs(v) >= 10) rfx(v, 0) else rfx(v, 1)

private val superscripts = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻')

private fun power(j: Int): String = "x" + j.toString().map { superscripts[it] ?: it }.joinToString("")

/** "x², x⁹, x¹"; past [limit] terms, the first three and a count. */
private fun powers(terms: List<Int>, limit: Int = 4): String =
    if (terms.size <= limit) terms.joinToString(", ") { power(it) }
    else terms.take(3).joinToString(", ") { power(it) } + " +${terms.size - 3}"

/** 10 to a log step: "10⁻²" on whole steps, "10^-1.4" between them. */
private fun tenTo(e: Double): String {
    val whole = abs(e - e.roundToInt()) < 1e-6
    return if (whole) "10" + e.roundToInt().toString().map { superscripts[it] ?: it }.joinToString("")
    else "10^" + rfx(e, 1).replace('−', '-')
}

private val numberWords = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

private fun countWord(n: Int) = numberWords.getOrElse(n) { "$n" }

private fun ladder(from: Double, to: Double, step: Double): List<Double> {
    val count = ((to - from) / step).roundToInt()
    return (0..count).map { from + it * step }
}

private fun List<Double>.nearest(v: Double): Int = indices.minBy { abs(this[it] - v) }

// ── Data ──
// Fixed seeds: the headlines quote these exact numbers, and Reset returns to them.

/** Rise, plateau, dip, rise: a shape a low-degree polynomial cannot follow and degree 9 overfits. */
private fun wavyPoints(): List<LabPoint> {
    val random = Random(4)
    return (0 until 24).map { i ->
        val x = i * 0.4f
        val y = 3f + 1.6f * kotlin.math.sin(x * 0.75f) + 0.18f * x + (random.nextFloat() - 0.5f) * 1.6f
        LabPoint(x / 9.2f, (y - 1f) / 5f)
    }
}

/** Every fourth point is held out of the fit, none at the ends, so held-out error never extrapolates. */
private fun heldOutOf(points: List<LabPoint>): Set<Int> = points.indices.filter { it % 4 == 2 }.toSet()

/** Twenty-seven points on a line and five from somewhere else, the last five. */
private fun contaminatedPoints(): List<LabPoint> {
    val random = Random(4)
    val clean = (0 until 27).map { i ->
        val x = (i + 0.5f) / 27f
        LabPoint(x, 0.12f + 0.85f * x + (random.nextFloat() - 0.5f) * 0.12f)
    }
    val far = (0 until 5).map { i -> LabPoint(0.12f + i * 0.12f, 1.12f + random.nextFloat() * 0.1f) }
    return clean + far
}

/** Spread grows with x, so quantile lines fan out. */
private fun fanPoints(): List<LabPoint> {
    val random = Random(4)
    return (0 until 32).map { i ->
        val x = i / 31f
        val spread = 0.05f + 0.3f * x
        LabPoint(x, 0.2f + 0.55f * x + (random.nextFloat() - 0.5f) * 2f * spread)
    }
}

/** Poisson counts with a log-linear mean. */
private fun countPoints(): List<LabPoint> {
    val random = Random(4)
    return (0 until 30).map { i ->
        val x = i * 0.3f
        val mean = exp(-0.4 + 0.42 * x)
        // Knuth's sampler; the means are small, so the loop is short.
        var k = 0
        var p = 1.0
        val limit = exp(-mean)
        do { k++; p *= random.nextDouble() } while (p > limit)
        LabPoint(x, (k - 1).toFloat())
    }
}

/** A trend that never falls but is flat in stretches, where isotonic's steps come from. */
private fun monotonePoints(): List<LabPoint> {
    val random = Random(4)
    return (0 until 28).map { i ->
        val x = i * 0.32f
        val trend = when {
            x < 2.5f -> 1.2f + 0.15f * x
            x < 5.5f -> 2.6f + 0.9f * (x - 2.5f)
            else -> 5.3f + 0.1f * (x - 5.5f)
        }
        LabPoint(x / 8.64f, (trend + (random.nextFloat() - 0.5f) * 1.3f - 0.5f) / 5.5f)
    }
}

private fun List<LabPoint>.span(): Pair<Float, Float> = (minOf { it.x }) to (maxOf { it.x })

private fun curve(xMin: Float, xMax: Float, f: (Double) -> Double): List<Pair<Double, Double>> =
    (0..80).map { i -> val x = xMin + (xMax - xMin) * i / 80.0; x to f(x) }

private fun polyCurve(beta: DoubleArray, xMin: Float, xMax: Float) =
    curve(xMin, xMax) { polyValue(beta, scaleX(it.toFloat(), xMin, xMax)) }

private fun polyMse(points: List<LabPoint>, beta: DoubleArray, xMin: Float, xMax: Float) =
    mse(points) { polyValue(beta, scaleX(it, xMin, xMax)) }

private fun coefficientBars(beta: DoubleArray): List<RegBar> {
    val sizes = (1..9).map { abs(beta.getOrElse(it) { 0.0 }) }
    val top = sizes.max().coerceAtLeast(1e-9)
    return sizes.mapIndexed { i, s -> RegBar(power(i + 1), if (s > 1e-6) s / top else 0.0) }
}

private fun nonZero(beta: DoubleArray) = (1 until beta.size).count { abs(beta[it]) > 1e-6 }

// ── Topics ──

private fun polynomialStory(): RegStoryConfig {
    val points = wavyPoints()
    val (xMin, xMax) = points.span()
    val test = heldOutOf(points)
    val train = points.filterIndexed { i, _ -> i !in test }
    val holdout = points.filterIndexed { i, _ -> i in test }
    val fits = (1..9).map { d -> ridgeSolve(designMatrix(train.map { it.x }, d, xMin, xMax), train.map { it.y.toDouble() }.toDoubleArray(), 0.0) }
    val testErrors = fits.map { polyMse(holdout, it, xMin, xMax) }
    val best = testErrors.indices.minBy { testErrors[it] }
    val degrees = (1..9).map { it.toDouble() }
    return RegStoryConfig(
        params = listOf(RegParam("Degree", "Degree", "", degrees, 8) { it.roundToInt().toString() }),
        action = RegAction("Best by held-out", inline = false) { listOf(best) },
        evaluate = { idx ->
            val d = idx[0] + 1
            val beta = fits[idx[0]]
            val trainMse = polyMse(train, beta, xMin, xMax)
            val testMse = testErrors[idx[0]]
            val ratio = testMse / trainMse.coerceAtLeast(1e-9)
            RegFrame(
                dots = points.mapIndexed { i, p -> RegDot(p.x.toDouble(), p.y.toDouble(), if (i in test) RegDotKind.HeldOut else RegDotKind.Plain) },
                lines = listOf(RegLine(polyCurve(beta, xMin, xMax), fit = true)),
                legend = listOf(RegKey(RegSwatch.Dot, RegInk.Point, "Train"), RegKey(RegSwatch.Ring, RegInk.Blue, "Held out")),
                chips = listOf("train MSE" to rfx(trainMse), "held-out" to rfx(testMse), "coefs" to "${d + 1}"),
                headline = "Degree $d fits ${d + 1} coefficients to ${train.size} points: training error ${rfx(trainMse)}, held-out ${rfx(testMse)}.",
                body = when {
                    idx[0] == best -> "Held-out error is lowest at this degree, so this is the one to keep."
                    idx[0] < best -> "Both errors still fall with more degrees: this curve is too stiff for the data."
                    ratio >= 1.5 -> "Held-out error is ${rfx(ratio, 1)}× the training error: the extra terms fit noise."
                    else -> "Held-out error has started to rise: the extra terms fit noise."
                },
            )
        },
    )
}

private fun ridgeStory(): RegStoryConfig {
    val points = wavyPoints()
    val (xMin, xMax) = points.span()
    val test = heldOutOf(points)
    val train = points.filterIndexed { i, _ -> i !in test }
    val holdout = points.filterIndexed { i, _ -> i in test }
    val x = designMatrix(train.map { it.x }, 9, xMin, xMax)
    val y = train.map { it.y.toDouble() }.toDoubleArray()
    val plain = ridgeSolve(x, y, 0.0)
    val plainTest = polyMse(holdout, plain, xMin, xMax)
    val plainNorm = sqrt((1..9).sumOf { plain[it] * plain[it] })
    val exponents = ladder(-6.0, 1.0, 0.5)
    val fits = exponents.map { ridgeSolve(x, y, 10.0.pow(it)) }
    val testErrors = fits.map { polyMse(holdout, it, xMin, xMax) }
    val best = testErrors.indices.minBy { testErrors[it] }
    return RegStoryConfig(
        params = listOf(RegParam("Penalty", "Penalty", "log₁₀ λ", exponents, exponents.nearest(-2.0)) { rfx(it, 1) }),
        action = RegAction("Best by held-out", inline = false) { listOf(best) },
        evaluate = { idx ->
            val e = exponents[idx[0]]
            val beta = fits[idx[0]]
            val testNow = testErrors[idx[0]]
            val norm = sqrt((1..9).sumOf { beta[it] * beta[it] })
            val tooFar = testNow > testErrors[best] * 1.5 && idx[0] > best
            RegFrame(
                dots = points.mapIndexed { i, p -> RegDot(p.x.toDouble(), p.y.toDouble(), if (i in test) RegDotKind.HeldOut else RegDotKind.Plain) },
                lines = listOf(RegLine(polyCurve(plain, xMin, xMax), fit = false), RegLine(polyCurve(beta, xMin, xMax), fit = true)),
                legend = listOf(
                    RegKey(RegSwatch.Line, RegInk.Fit, "λ = ${tenTo(e)}"),
                    RegKey(RegSwatch.Dashed, RegInk.Reference, "λ = 0"),
                    RegKey(RegSwatch.Ring, RegInk.Blue, "Held out"),
                ),
                chips = listOf("held-out" to "${rfx(plainTest)} → ${rfx(testNow)}", "‖β‖" to "${rShort(plainNorm)} → ${rShort(norm)}"),
                headline = if (tooFar) {
                    "At λ = ${tenTo(e)} ridge shrinks too far: held-out error rises to ${rfx(testNow)}."
                } else {
                    "Ridge shrinks all 9 weights and zeroes none."
                },
                body = if (tooFar) {
                    "Every weight is still non-zero, but the curve has gone too flat to follow the real shape."
                } else {
                    "Smaller weights mean fewer wiggles, so the curve stops chasing training noise."
                },
            )
        },
    )
}

private fun lassoStory(): RegStoryConfig {
    val points = wavyPoints()
    val (xMin, xMax) = points.span()
    val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
    val y = points.map { it.y.toDouble() }.toDoubleArray()
    val exponents = ladder(-4.0, -1.0, 0.2)
    return RegStoryConfig(
        params = listOf(RegParam("Penalty", "Penalty", "log₁₀ λ", exponents, exponents.nearest(-2.4)) { rfx(it, 1) }),
        evaluate = { idx ->
            val e = exponents[idx[0]]
            val lambda = 10.0.pow(e)
            val lasso = coordinateDescent(x, y, lambda, l1Ratio = 1.0)
            val ridge = ridgeSolve(x, y, lambda * points.size)
            val kept = nonZero(lasso)
            val ridgeKept = nonZero(ridge)
            val zeroed = 9 - kept
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble()) },
                lines = listOf(RegLine(polyCurve(ridge, xMin, xMax), fit = false), RegLine(polyCurve(lasso, xMin, xMax), fit = true)),
                bars = coefficientBars(lasso),
                legend = listOf(RegKey(RegSwatch.Line, RegInk.Fit, "Lasso"), RegKey(RegSwatch.Dashed, RegInk.Reference, "Ridge, same λ")),
                chips = listOf("non-zero" to "$kept of 9", "ridge" to "$ridgeKept of 9", "MSE" to rfx(polyMse(points, lasso, xMin, xMax))),
                headline = when (zeroed) {
                    0 -> "At λ = ${tenTo(e)} lasso still keeps all 9 terms, like ridge."
                    9 -> "At λ = ${tenTo(e)} lasso zeroes all 9 terms, leaving a flat line at the mean."
                    else -> "At λ = ${tenTo(e)} lasso zeroes $zeroed of 9 terms. Ridge keeps ${if (ridgeKept == 9) "all 9" else "$ridgeKept"}."
                },
                body = when (zeroed) {
                    0 -> "The penalty is too weak to reach L1's corner at zero. Raise λ and weights start landing on it."
                    9 -> "No term earns back its penalty any more. Lower λ and the strongest ones return first."
                    else -> "L1 has a corner at zero, so weights can land exactly on it. L2 only shrinks them."
                },
            )
        },
    )
}

private fun elasticNetStory(): RegStoryConfig {
    val points = wavyPoints()
    val (xMin, xMax) = points.span()
    val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
    val y = points.map { it.y.toDouble() }.toDoubleArray()
    val exponents = ladder(-4.0, -1.0, 0.2)
    val ratios = ladder(0.0, 1.0, 0.1)
    return RegStoryConfig(
        params = listOf(
            RegParam("Penalty λ", "Penalty", "log₁₀ λ", exponents, exponents.nearest(-2.2)) { rfx(it, 1) },
            RegParam("L1 ratio", "L1 ratio", "", ratios, 5) { rfx(it, 1) },
        ),
        initialTab = 1,
        evaluate = { idx ->
            val lambda = 10.0.pow(exponents[idx[0]])
            val ratio = ratios[idx[1]]
            val beta = coordinateDescent(x, y, lambda, ratio)
            val lasso = coordinateDescent(x, y, lambda, 1.0)
            val kept = nonZero(beta)
            val lassoKept = nonZero(lasso)
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble()) },
                lines = listOf(RegLine(polyCurve(beta, xMin, xMax), fit = true)),
                bars = coefficientBars(beta),
                chips = listOf("non-zero" to "$kept of 9", "lasso alone" to "$lassoKept of 9", "MSE" to rfx(polyMse(points, beta, xMin, xMax))),
                headline = when {
                    kept == 0 -> "With L1 ratio ${rfx(ratio, 1)}, every term is zeroed: λ is too strong."
                    idx[1] == 0 -> "With L1 ratio 0 this is pure ridge: $kept of 9 terms stay."
                    idx[1] == ratios.lastIndex -> "With L1 ratio 1 this is pure lasso: $kept of 9 terms survive."
                    kept == lassoKept -> "With L1 ratio ${rfx(ratio, 1)}, $kept of 9 terms survive, the same as pure lasso."
                    else -> "With L1 ratio ${rfx(ratio, 1)}, $kept of 9 terms survive, against $lassoKept for pure lasso."
                },
                body = when {
                    kept == 0 -> "No term earns back its penalty. Lower λ to let the strongest ones return."
                    idx[1] == 0 -> "Nothing reaches zero without the L1 half. Raise the ratio to start pruning."
                    idx[1] == ratios.lastIndex -> "Among correlated powers lasso keeps one and drops the rest, somewhat arbitrarily."
                    else -> "The L2 half keeps correlated powers together instead of picking one at random. The L1 half still zeroes the rest."
                },
            )
        },
    )
}

private fun stepwiseStory(): RegStoryConfig {
    val points = wavyPoints()
    val (xMin, xMax) = points.span()
    val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
    val y = points.map { it.y.toDouble() }.toDoubleArray()
    val results = (1..9).map { forwardStepwise(x, y, it) }
    return RegStoryConfig(
        params = listOf(RegParam("Terms kept", "Terms kept", "", (1..9).map { it.toDouble() }, 2) { it.roundToInt().toString() }),
        evaluate = { idx ->
            val result = results[idx[0]]
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble()) },
                lines = listOf(RegLine(polyCurve(result.beta, xMin, xMax), fit = true)),
                chips = listOf("kept" to powers(result.selected), "adj R²" to rfx(result.adjustedR2), "MSE" to rfx(polyMse(points, result.beta, xMin, xMax))),
                headline = "Forward selection kept ${result.selected.joinToString(", ") { power(it) }}, picked by this same data.",
                body = "So R² and p-values look better than they are. Only a held-out set gives an honest score.",
            )
        },
    )
}

private fun robustStory(): RegStoryConfig {
    val points = contaminatedPoints()
    val clean = 27
    val (xMin, xMax) = points.span()
    val (olsM, olsC) = ordinaryLeastSquares(points)
    val thresholds = ladder(0.02, 0.3, 0.02)
    return RegStoryConfig(
        params = listOf(RegParam("Inlier threshold", "Inlier threshold", "", thresholds, thresholds.nearest(0.1)) { rfx(it, 2) }),
        evaluate = { idx ->
            val t = thresholds[idx[0]]
            val fit = ransac(points, t)
            val kept = fit.inliers.size
            val goodOut = (0 until clean).count { it !in fit.inliers }
            val badIn = (clean until points.size).count { it in fit.inliers }
            val far = points.size - clean
            val line = { v: Double -> fit.slope * v + fit.intercept }
            RegFrame(
                dots = points.mapIndexed { i, p -> RegDot(p.x.toDouble(), p.y.toDouble(), if (i in fit.inliers) RegDotKind.Plain else RegDotKind.Discarded) },
                lines = listOf(RegLine(curve(xMin, xMax) { olsM * it + olsC }, fit = false), RegLine(curve(xMin, xMax, line), fit = true)),
                band = RegBand(curve(xMin, xMax) { line(it) - t }, curve(xMin, xMax) { line(it) + t }),
                legend = listOf(
                    RegKey(RegSwatch.Line, RegInk.Fit, "RANSAC"),
                    RegKey(RegSwatch.Fill, RegInk.Band, "± threshold"),
                    RegKey(RegSwatch.Dashed, RegInk.Reference, "OLS"),
                    RegKey(RegSwatch.Ring, RegInk.Red, "Discarded"),
                ),
                chips = listOf("slope" to rfx(fit.slope, 2), "OLS slope" to rfx(olsM, 2), "inliers" to "$kept/${points.size}"),
                headline = when {
                    badIn > 0 -> "A band of ±${rfx(t, 2)} is wide enough to let $badIn outlier${if (badIn == 1) "" else "s"} back in."
                    goodOut > 0 -> "A band of ±${rfx(t, 2)} also throws out $goodOut good point${if (goodOut == 1) "" else "s"}."
                    else -> "${countWord(far)} far-off points pull the OLS slope ${if (olsM < fit.slope) "down" else "up"} to ${rfx(olsM, 2)}."
                },
                body = when {
                    badIn > 0 -> "Once inside, they are averaged like any other point, and the slope moves to ${rfx(fit.slope, 2)}."
                    goodOut > 0 -> "RANSAC still fits the $kept it keeps, but a band tighter than the noise wastes real data."
                    else -> "RANSAC keeps the $kept points inside the band and fits only those, so the outliers never enter the average."
                },
            )
        },
    )
}

private fun quantileStory(): RegStoryConfig {
    val points = fanPoints()
    val (xMin, xMax) = points.span()
    val taus = ladder(0.1, 0.9, 0.1)
    val fits = taus.map { quantileFit(points, it) }
    val references = listOf(0, 4, 8)
    return RegStoryConfig(
        params = listOf(RegParam("Quantile", "Quantile", "τ", taus, 8) { rfx(it, 1) }),
        evaluate = { idx ->
            val tau = taus[idx[0]]
            val (m, c) = fits[idx[0]]
            val below = points.count { it.y < m * it.x + c }
            val target = (tau * 100).roundToInt()
            val others = references.filter { it != idx[0] }
            val close = abs(below.toDouble() / points.size - tau) <= 0.07
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble(), if (it.y < m * it.x + c) RegDotKind.Below else RegDotKind.Plain) },
                lines = others.map { r -> RegLine(curve(xMin, xMax) { fits[r].first * it + fits[r].second }, fit = false) } +
                    RegLine(curve(xMin, xMax) { m * it + c }, fit = true),
                legend = listOf(
                    RegKey(RegSwatch.Line, RegInk.Fit, "τ = ${rfx(tau, 1)} fit"),
                    RegKey(RegSwatch.Dot, RegInk.Blue, "Below the line"),
                    RegKey(RegSwatch.Dashed, RegInk.Reference, "τ = " + others.joinToString(", ") { rfx(taus[it], 1) }),
                ),
                chips = listOf("below" to "$below/${points.size}", "target" to "$target%", "pinball" to rfx(pinballLoss(points, tau, m, c))),
                headline = "$below of ${points.size} points sit below the τ = ${rfx(tau, 1)} line, ${if (close) "close to" else "against"} the $target% it aims for.",
                body = "The lines fan out because the spread grows with x. One mean line would hide that.",
            )
        },
    )
}

private const val BayesDegree = 7

private fun bayesianStory(): RegStoryConfig {
    val all = wavyPoints()
    val gapFrom = 0.3f
    val gapTo = 0.68f
    val points = all.filterNot { it.x > gapFrom && it.x < gapTo }
    val (xMin, xMax) = points.span()
    val x = designMatrix(points.map { it.x }, BayesDegree, xMin, xMax)
    val y = points.map { it.y.toDouble() }.toDoubleArray()
    val left = points.filter { it.x <= gapFrom }.maxOf { it.x }.toDouble()
    val right = points.filter { it.x >= gapTo }.minOf { it.x }.toDouble()
    val phiAt = { v: Double ->
        val t = scaleX(v.toFloat(), xMin, xMax)
        DoubleArray(BayesDegree + 1) { p -> t.pow(p) }
    }
    val alphas = listOf(0.1, 0.3, 1.0, 3.0, 10.0)
    val noises = listOf(0.005, 0.01, 0.02, 0.04, 0.08)
    return RegStoryConfig(
        params = listOf(
            RegParam("Prior precision α", "Prior precision", "α", alphas, 2) { rfx(it, 1) },
            RegParam("Noise variance", "Noise variance", "σ²", noises, 1) { if (it < 0.01) rfx(it, 3) else rfx(it, 2) },
        ),
        evaluate = { idx ->
            val fit = bayesianRidge(x, y, alphas[idx[0]], noises[idx[1]])
            val mean = { v: Double -> polyValue(fit.mean, scaleX(v.toFloat(), xMin, xMax)) }
            val sd = { v: Double -> predictiveStd(fit, phiAt(v)) }
            val gapSd = sd((left + right) / 2)
            val denseSd = sd(0.15)
            val ratio = gapSd / denseSd.coerceAtLeast(1e-9)
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble()) },
                lines = listOf(RegLine(curve(xMin, xMax, mean), fit = true)),
                band = RegBand(curve(xMin, xMax) { mean(it) - 2 * sd(it) }, curve(xMin, xMax) { mean(it) + 2 * sd(it) }),
                columns = listOf(RegColumn(left, right, gap = true)),
                legend = listOf(RegKey(RegSwatch.Line, RegInk.Fit, "Posterior mean"), RegKey(RegSwatch.Fill, RegInk.Band, "± 2σ")),
                chips = listOf("σ in the gap" to rfx(gapSd, 2), "σ where dense" to rfx(denseSd, 2), "ratio" to rfx(ratio, 1) + "×"),
                headline = if (ratio >= 1.15) {
                    "The band is ${rfx(ratio, 1)}× wider in the gap, where no data pins the curve down."
                } else {
                    "The band is barely wider in the gap than where the data is dense."
                },
                body = if (ratio >= 1.15) {
                    "Plain ridge draws the same mean curve with no sign that the middle is a guess."
                } else {
                    "A strong prior speaks for the gap instead of the data. Lower α and the gap's uncertainty shows."
                },
            )
        },
    )
}

private fun poissonStory(): RegStoryConfig {
    val points = countPoints()
    val (xMin, xMax) = points.span()
    val (olsM, olsC) = ordinaryLeastSquares(points)
    val (fitB0, fitB1) = poissonIrls(points)
    val bestDeviance = poissonDeviance(points, fitB0, fitB1)
    val intercepts = ladder(-2.0, 2.0, 0.1)
    val slopes = ladder(-0.2, 0.8, 0.02)
    return RegStoryConfig(
        params = listOf(
            RegParam("β₀ intercept", "Intercept", "β₀", intercepts, intercepts.nearest(-0.4)) { rfx(it, 1) },
            RegParam("β₁ slope", "Slope", "β₁", slopes, slopes.nearest(0.42)) { rfx(it, 2) },
        ),
        initialTab = 1,
        action = RegAction("Fit by IRLS", inline = true) { listOf(intercepts.nearest(fitB0), slopes.nearest(fitB1)) },
        evaluate = { idx ->
            val b0 = intercepts[idx[0]]
            val b1 = slopes[idx[1]]
            val deviance = poissonDeviance(points, b0, b1)
            val crossing = if (olsM > 0) -olsC / olsM else Double.NaN
            val negative = crossing.isFinite() && crossing > xMin
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble()) },
                lines = listOf(
                    RegLine(curve(xMin, xMax) { olsM * it + olsC }, fit = false),
                    RegLine(curve(xMin, xMax) { exp((b0 + b1 * it).coerceIn(-20.0, 20.0)) }, fit = true),
                ),
                negativeZone = true,
                legend = listOf(
                    RegKey(RegSwatch.Line, RegInk.Fit, "exp(β₀ + β₁x)"),
                    RegKey(RegSwatch.Dashed, RegInk.Reference, "OLS line"),
                    RegKey(RegSwatch.Fill, RegInk.Red, "Negative counts"),
                ),
                chips = listOf("deviance" to rfx(deviance), "OLS at x=0" to rfx(olsC, 2), "per unit x" to rfx(exp(b1), 2) + "×"),
                headline = if (negative) {
                    "The OLS line drops below zero for x < ${rfx(crossing, 1)}, predicting negative counts."
                } else {
                    "Each unit of x multiplies the expected count by ${rfx(exp(b1), 2)}."
                },
                body = when {
                    deviance > bestDeviance * 1.25 ->
                        "Deviance is ${rfx(deviance)} here against ${rfx(bestDeviance)} at the best fit. Fit by IRLS jumps there."
                    negative -> "The log link rules that out: exp is positive everywhere, and each unit of x multiplies the count."
                    else -> "exp is positive everywhere, so unlike a straight line the curve can never predict a negative count."
                },
            )
        },
    )
}

private fun isotonicStory(): RegStoryConfig {
    val base = monotonePoints()
    val noises = ladder(0.0, 0.5, 0.1)
    return RegStoryConfig(
        params = listOf(RegParam("Extra noise", "Extra noise", "", noises, 0) { rfx(it, 1) }),
        evaluate = { idx ->
            val extra = noises[idx[0]]
            val random = Random(9)
            val points = base.map { LabPoint(it.x, it.y + ((random.nextFloat() - 0.5f) * 2.0 * extra).toFloat()) }
            val fit = pava(points)
            val (m, c) = ordinaryLeastSquares(points)
            val (xMin, xMax) = points.span()
            val steps = buildList {
                fit.xs.forEachIndexed { i, x ->
                    if (i > 0) add(x.toDouble() to fit.ys[i - 1]) // the riser
                    add(x.toDouble() to fit.ys[i])
                }
            }
            // Each pooled block spans its points, widened a little either side; short of half the
            // spacing, so two pools next to each other still read as two.
            val half = if (fit.xs.size > 1) (fit.xs[1] - fit.xs[0]) * 0.35 else 0.0
            var start = 0
            val pools = buildList {
                fit.sizes.forEach { size ->
                    if (size > 1) add(RegColumn(fit.xs[start] - half, fit.xs[start + size - 1] + half, gap = false))
                    start += size
                }
            }
            val error = mse(points) { v -> fit.ys[fit.xs.indexOfLast { it <= v }.coerceAtLeast(0)] }
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble()) },
                lines = listOf(RegLine(curve(xMin, xMax) { m * it + c }, fit = false), RegLine(steps, fit = true)),
                columns = pools,
                legend = listOf(
                    RegKey(RegSwatch.Line, RegInk.Fit, "Isotonic fit"),
                    RegKey(RegSwatch.Fill, RegInk.Pool, "Pooled block"),
                    RegKey(RegSwatch.Dashed, RegInk.Reference, "Linear fit"),
                ),
                chips = listOf("blocks" to "${fit.blocks}", "points" to "${points.size}", "MSE" to rfx(error)),
                headline = "PAVA turned ${points.size} points into ${fit.blocks} flat steps.",
                body = when (pools.size) {
                    0 -> "The data never went down, so no points had to be pooled."
                    1 -> "The one shaded pool is a place where the data went down. The fit uses its mean, so it never decreases."
                    else -> "Each of the ${pools.size} shaded pools is a place where the data went down. The fit uses their mean, so it never decreases."
                },
            )
        },
    )
}

private fun larsStory(): RegStoryConfig {
    val points = wavyPoints()
    val (xMin, xMax) = points.span()
    val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
    val y = points.map { it.y.toDouble() }.toDoubleArray()
    val steps = larsSteps(x, y)
    val total = steps.size
    return RegStoryConfig(
        params = listOf(RegParam("Path step", "Path step", "", (1..total).map { it.toDouble() }, 1) { "${it.roundToInt()} of $total" }),
        evaluate = { idx ->
            val k = idx[0] + 1
            val step = steps[idx[0]]
            val joined = power(step.entered)
            RegFrame(
                dots = points.map { RegDot(it.x.toDouble(), it.y.toDouble()) },
                lines = listOf(RegLine(polyCurve(step.beta, xMin, xMax), fit = true)),
                chips = listOf("active" to powers(step.active), (if (k == 1) "|corr|" else "|corr| tie") to rfx(step.tie)),
                headline = when (k) {
                    1 -> "Step 1: $joined is the most correlated with the residual, so it enters first."
                    2 -> "Step 2: $joined is now as correlated with the residual as ${power(step.active[0])}, so it joins."
                    else -> "Step $k: $joined is now as correlated with the residual as the other ${k - 1}, so it joins."
                },
                body = when (k) {
                    1 -> "Its coefficient grows only until another predictor is just as correlated."
                    2 -> "Both coefficients move together from here. Neither is fully fitted before the next predictor is considered."
                    else -> "All $k coefficients move together from here. None is fully fitted before the next predictor is considered."
                },
            )
        },
    )
}

private val regressionStories: Map<String, () -> RegStoryConfig> = mapOf(
    "polynomial_regression" to ::polynomialStory,
    "ridge_regression" to ::ridgeStory,
    "lasso_regression" to ::lassoStory,
    "elasticnet_regression" to ::elasticNetStory,
    "stepwise_regression" to ::stepwiseStory,
    "robust_regression" to ::robustStory,
    "quantile_regression" to ::quantileStory,
    "bayesian_ridge" to ::bayesianStory,
    "poisson_regression" to ::poissonStory,
    "isotonic_regression" to ::isotonicStory,
    "lars" to ::larsStory,
)

/**
 * Frame guard: every stepper value of every parameter (the others at their start) must build a
 * frame with finite geometry, chips and a headline, and a solve button must land on the ladder.
 */
internal fun regressionStoryProbe(topicId: String): Int {
    val config = (regressionStories[topicId] ?: error("no story for $topicId"))()
    var frames = 0
    val settings = listOf(config.initial) + config.params.flatMapIndexed { p, param ->
        param.values.indices.map { v -> config.initial.toMutableList().also { it[p] = v } }
    }
    settings.forEach { idx ->
        val frame = config.evaluate(idx)
        require(frame.dots.isNotEmpty()) { "$topicId drew no data" }
        val coords = frame.dots.flatMap { listOf(it.x, it.y) } +
            frame.lines.flatMap { l -> l.points.map { it.first } } +
            (frame.band?.let { b -> (b.lower + b.upper).flatMap { listOf(it.first, it.second) } } ?: emptyList()) +
            frame.columns.flatMap { listOf(it.x0, it.x1) } + frame.bars.map { it.size }
        require(coords.all { it.isFinite() }) { "$topicId drew a non-finite coordinate at $idx" }
        require(frame.lines.all { it.points.isNotEmpty() }) { "$topicId drew an empty line at $idx" }
        require(frame.chips.all { it.second.isNotBlank() }) { "$topicId has a blank chip at $idx" }
        require(frame.headline.isNotBlank() && frame.body.isNotBlank()) { "$topicId has no narration at $idx" }
        config.action?.let { action ->
            val solved = action.solve(idx)
            require(solved.size == config.params.size && solved.indices.all { solved[it] in config.params[it].values.indices }) {
                "$topicId solved off its ladder"
            }
        }
        frames += 1
    }
    return frames
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
internal fun RegressionStorySection(topicId: String) {
    val config = remember(topicId) { (regressionStories[topicId] ?: ::polynomialStory)() }
    var indices by remember(config) { mutableStateOf(config.initial) }
    var selected by remember(config) { mutableIntStateOf(config.initialTab) }
    val frame = remember(config, indices) { config.evaluate(indices) }
    val dock = LocalLabDock.current

    val controls: @Composable () -> Unit = {
        RegControls(config, indices, selected, onSelect = { selected = it }, onIndices = { indices = it })
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                RegPlot(frame)
                if (frame.bars.isNotEmpty()) RegBars(frame.bars, Modifier.padding(top = 10.dp))
                if (frame.legend.isNotEmpty()) RegLegend(frame.legend, Modifier.padding(top = 14.dp))
            }
        }
        ReadoutChips(parts = frame.chips.map { it.first to it.second }, modifier = Modifier.padding(top = 16.dp), accented = setOf(0))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
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
                indices = config.initial
                selected = config.initialTab
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
private fun RegControls(
    config: RegStoryConfig,
    indices: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onIndices: (List<Int>) -> Unit,
) {
    val index = selected.coerceIn(0, config.params.lastIndex)
    val param = config.params[index]
    val at = indices[index]
    val action = config.action
    Column(modifier = Modifier.fillMaxWidth()) {
        if (config.params.size > 1 || action?.inline == true) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (config.params.size > 1) {
                    LabSegments(config.params.map { it.tab }, index, Modifier.weight(1f), onSelect)
                } else {
                    Box(Modifier.weight(1f))
                }
                if (action?.inline == true) {
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onIndices(action.solve(indices)) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(action.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
        LabParamStepper(
            LabParam(param.tab, param.name, param.symbol, param.format(param.values[at]), at > 0, at < param.values.lastIndex),
        ) { delta ->
            onIndices(indices.toMutableList().also { it[index] = (at + delta).coerceIn(0, param.values.lastIndex) })
        }
        if (action != null && !action.inline) {
            LabButton(action.label, primary = true, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                onIndices(action.solve(indices))
            }
        }
    }
}

@Composable
private fun regInk(ink: RegInk): Color = when (ink) {
    RegInk.Fit -> MaterialTheme.colorScheme.primary
    RegInk.Reference -> SimColors.Grey
    RegInk.Point -> if (LocalDarkTheme.current) SimColors.Idle else SimColors.Grey
    RegInk.Blue -> SimColors.Blue
    RegInk.Red -> SimColors.Red
    RegInk.Band -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    RegInk.Pool -> SimColors.Blue.copy(alpha = 0.35f)
}

@Composable
private fun RegLegend(items: List<RegKey>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            val color = regInk(item.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (item.swatch) {
                    RegSwatch.Line, RegSwatch.Dashed -> Canvas(Modifier.width(16.dp).height(3.dp)) {
                        val y = size.height / 2
                        drawLine(
                            color, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height,
                            pathEffect = if (item.swatch == RegSwatch.Dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                        )
                    }
                    // Red marks a shaded zone here, so its swatch is as faint as the zone.
                    RegSwatch.Fill -> Box(
                        Modifier.size(width = 14.dp, height = 10.dp)
                            .background(if (item.ink == RegInk.Red) color.copy(alpha = 0.4f) else color, RoundedCornerShape(3.dp)),
                    )
                    RegSwatch.Dot -> Box(Modifier.size(9.dp).background(color, CircleShape))
                    RegSwatch.Ring -> Box(Modifier.size(10.dp).border(2.dp, color, CircleShape))
                }
                Text(
                    item.label,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RegPlot(frame: RegFrame) {
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val pointColor = regInk(RegInk.Point)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (frame.bars.isEmpty()) 1.32f else 1.75f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.16f)),
    ) {
        val pad = 16.dp.toPx()
        val xs = frame.dots.map { it.x }
        val xLo = xs.min()
        val xHi = xs.max()
        val xSpan = (xHi - xLo).coerceAtLeast(1e-6)
        val xMin = xLo - xSpan * 0.03
        val xMax = xHi + xSpan * 0.03

        // Curves may shoot far past the data between points (an unpenalized degree 9); only the part
        // near the data sets the scale, or it would flatten the points to a line.
        val dataYs = frame.dots.map { it.y }
        val dMin = dataYs.min()
        val dMax = dataYs.max()
        val slack = (dMax - dMin).coerceAtLeast(1e-6) * 0.35
        val extraYs = (frame.lines.flatMap { l -> l.points.map { it.second } } +
            (frame.band?.let { b -> (b.lower + b.upper).map { it.second } } ?: emptyList()))
            .filter { it.isFinite() && it in (dMin - slack)..(dMax + slack) }
        var yMin = (dataYs + extraYs).min()
        var yMax = (dataYs + extraYs).max()
        if (frame.negativeZone) yMin = minOf(yMin, -(yMax - yMin) * 0.12)
        val yPad = (yMax - yMin).coerceAtLeast(1e-6) * 0.06
        yMin -= yPad
        yMax += yPad

        fun px(x: Double) = (pad + (x - xMin) / (xMax - xMin) * (size.width - 2 * pad)).toFloat()
        fun py(y: Double) = (size.height - pad - (y - yMin) / (yMax - yMin) * (size.height - 2 * pad)).toFloat()

        frame.columns.forEach { col ->
            val left = px(col.x0).coerceAtLeast(0f)
            val right = px(col.x1).coerceAtMost(size.width)
            drawRect(
                if (col.gap) onSurface.copy(alpha = 0.06f) else SimColors.Blue.copy(alpha = 0.16f),
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height),
            )
            if (col.gap) {
                val layout = measurer.measure("no data", TextStyle(fontSize = 12.sp))
                drawText(layout, color = muted, topLeft = Offset((left + right - layout.size.width) / 2, 8.dp.toPx()))
            }
        }
        if (frame.negativeZone) {
            val zero = py(0.0)
            drawRect(SimColors.Red.copy(alpha = 0.14f), topLeft = Offset(0f, zero), size = Size(size.width, size.height - zero))
            drawLine(muted.copy(alpha = 0.4f), Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
        }

        clipRect {
            frame.band?.let { band ->
                val path = Path()
                band.upper.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
                band.lower.asReversed().forEach { (x, y) -> path.lineTo(px(x), py(y)) }
                path.close()
                drawPath(path, primary.copy(alpha = 0.24f))
            }
            (frame.lines.filter { !it.fit } + frame.lines.filter { it.fit }).forEach { line ->
                // One path so dashes flow along the curve; stretches far off-scale are skipped rather
                // than clipped, so a spike doesn't read as a feature of the fit.
                val path = Path()
                var open = false
                val lo = yMin - (yMax - yMin)
                val hi = yMax + (yMax - yMin)
                line.points.zipWithNext().forEach { (a, b) ->
                    if (a.second !in lo..hi || b.second !in lo..hi) { open = false; return@forEach }
                    if (!open) { path.moveTo(px(a.first), py(a.second)); open = true }
                    path.lineTo(px(b.first), py(b.second))
                }
                drawPath(
                    path,
                    if (line.fit) primary else SimColors.Grey,
                    style = Stroke(
                        width = (if (line.fit) 3.dp else 1.5.dp).toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (line.fit) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
        }

        frame.dots.forEach { dot ->
            val c = Offset(px(dot.x), py(dot.y))
            when (dot.kind) {
                RegDotKind.Plain -> {
                    drawCircle(pointColor, 4.dp.toPx(), c)
                    drawCircle(Color.Black.copy(alpha = 0.3f), 4.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                }
                RegDotKind.Below -> drawCircle(SimColors.Blue, 4.5.dp.toPx(), c)
                RegDotKind.HeldOut -> {
                    drawCircle(SimColors.Blue.copy(alpha = 0.3f), 7.dp.toPx(), c)
                    drawCircle(SimColors.Blue, 7.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
                RegDotKind.Discarded -> {
                    drawCircle(SimColors.Red.copy(alpha = 0.4f), 7.dp.toPx(), c)
                    drawCircle(SimColors.Red, 7.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}

/** The coefficient strip: a bar per power, a flat dash where the penalty zeroed it. */
@Composable
private fun RegBars(bars: List<RegBar>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.fillMaxWidth().height(58.dp)) {
        val labelHeight = 16.dp.toPx()
        val base = size.height - labelHeight - 4.dp.toPx()
        val tall = base - 2.dp.toPx()
        val slot = size.width / bars.size
        val barWidth = 16.dp.toPx()
        bars.forEachIndexed { i, bar ->
            val cx = slot * (i + 0.5f)
            if (bar.size > 0) {
                val h = (tall * bar.size).toFloat().coerceAtLeast(4.dp.toPx())
                drawRoundRect(primary, Offset(cx - barWidth / 2, base - h), Size(barWidth, h), CornerRadius(3.dp.toPx()))
            } else {
                drawLine(muted.copy(alpha = 0.5f), Offset(cx - 6.dp.toPx(), base - 1.dp.toPx()), Offset(cx + 6.dp.toPx(), base - 1.dp.toPx()), 2.dp.toPx())
            }
            val layout = measurer.measure(bar.label, TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp))
            drawText(
                layout,
                color = if (bar.size > 0) muted else muted.copy(alpha = 0.45f),
                topLeft = Offset(cx - layout.size.width / 2, size.height - labelHeight),
            )
        }
    }
}
