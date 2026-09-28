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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

// ── Regression lab ───────────────────────────────────────────────────────────
// The mock's regression lab (RegressionSimulationSection) is a hand-tuned port for Linear Regression
// and is left alone. This is the same chrome — canvas, readout row, sliders, action button — driven
// by a per-topic config, so each of the eleven Regression topics gets its own estimator, its own
// dataset shape, and its own thing to notice. Every curve here is computed by the real algorithm in
// RegressionLabMath.kt.

private val LabFit = Color(0xFF4F46E5)
// Legend key for the data points; drawn in the theme-aware point colour.
private val LabPointColor = Color(0xFF7C3AED)
private val LabReference = SimColors.Grey
private val LabHighlight = SimColors.Red
private val LabBand = Color(0xFF10B981)

private class LabCurve(
    val points: List<Pair<Float, Float>>,
    val color: Color,
    val dashed: Boolean = false,
    val width: Float = 6f,
)

private class LabReadout(val value: String, val label: String)

private class LabResult(
    val curves: List<LabCurve>,
    // Rendered as chips, label then value; the first is the lab's headline number and is accented.
    val readouts: List<LabReadout>,
    // Narration. Topics that author [headline] + [detail] get them as written; the rest split [note]
    // after its first sentence (what happened, then why it matters).
    val note: String = "",
    // Points drawn in the highlight colour — outliers for RANSAC, held-out points elsewhere.
    val highlighted: Set<Int> = emptySet(),
    val headline: String? = null,
    val detail: String? = null,
    // A phrase of [headline] to tint, in [highlightColor] (the key-term yellow when null).
    val highlight: String? = null,
    val highlightColor: Color? = null,
    // A share chip after the readouts: a bar filled to this fraction, with its percentage.
    val share: Double? = null,
)

private class LabSlider(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val initial: Float,
    val steps: Int = 0,
    // Set in mono after the label ("Slope m").
    val symbol: String = "",
    val format: (Float) -> String = { it.round2() },
)

private class LabConfig(
    val intro: String,
    val data: (Int) -> List<LabPoint>,
    val sliders: List<LabSlider>,
    val solveLabel: String?,
    // Given the data and current slider values, return the slider values the "solve" button should
    // jump to. Null when the topic has no meaningful closed-form answer to jump to.
    val solve: ((List<LabPoint>, FloatArray) -> FloatArray)?,
    val evaluate: (List<LabPoint>, FloatArray) -> LabResult,
    val legend: List<Pair<Color, String>> = emptyList(),
    // False when [data] ignores its seed, so New Data would do nothing and is hidden.
    val reseedable: Boolean = true,
)

// ── Datasets ─────────────────────────────────────────────────────────────────

private fun wavyData(seed: Int): List<LabPoint> {
    val random = Random(seed)
    return (0 until 24).map { i ->
        val x = i * 0.4f
        val y = 3f + 1.6f * kotlin.math.sin(x * 0.75f) + 0.18f * x + (random.nextFloat() - 0.5f) * 1.1f
        LabPoint(x, y)
    }
}

private fun outlierData(seed: Int): List<LabPoint> {
    val random = Random(seed)
    val clean = (0 until 26).map { i ->
        val x = i * 0.36f
        LabPoint(x, 1.2f + 0.85f * x + (random.nextFloat() - 0.5f) * 0.7f)
    }
    // Six points from a different process entirely — not fat-tailed noise, contamination.
    val bad = (0 until 6).map { i ->
        val x = 1.2f + i * 1.3f
        LabPoint(x, 9.5f - random.nextFloat() * 1.4f)
    }
    return clean + bad
}

private fun fanData(seed: Int): List<LabPoint> {
    val random = Random(seed)
    // Spread grows with x, so the mean line and the quantile lines genuinely diverge.
    return (0 until 40).map { i ->
        val x = i * 0.24f
        val spread = 0.4f + 0.42f * x
        LabPoint(x, 2f + 0.7f * x + (random.nextFloat() - 0.5f) * 2f * spread)
    }
}

private fun countData(seed: Int): List<LabPoint> {
    val random = Random(seed)
    return (0 until 30).map { i ->
        val x = i * 0.3f
        val mean = exp(-0.4 + 0.42 * x)
        // Knuth's sampler — small means here, so the loop is short.
        var k = 0
        var p = 1.0
        val limit = exp(-mean)
        do { k++; p *= random.nextDouble() } while (p > limit)
        LabPoint(x, (k - 1).toFloat())
    }
}

private fun monotoneData(seed: Int): List<LabPoint> {
    val random = Random(seed)
    return (0 until 28).map { i ->
        val x = i * 0.32f
        // Underlying trend is monotone but flat in stretches — where isotonic's steps come from.
        val trend = when {
            x < 2.5f -> 1.2f + 0.15f * x
            x < 5.5f -> 2.6f + 0.9f * (x - 2.5f)
            else -> 5.3f + 0.1f * (x - 5.5f)
        }
        LabPoint(x, trend + (random.nextFloat() - 0.5f) * 1.3f)
    }
}

// Shared helper: split into train/test by index parity, so a held-out score is available anywhere.
private fun heldOut(points: List<LabPoint>): Set<Int> = points.indices.filter { it % 4 == 3 }.toSet()

private fun sampleCurve(
    xMin: Float,
    xMax: Float,
    color: Color,
    dashed: Boolean = false,
    width: Float = 6f,
    f: (Float) -> Double,
): LabCurve {
    val n = 80
    val pts = (0..n).map { i ->
        val x = xMin + (xMax - xMin) * i / n
        x to f(x).toFloat()
    }
    return LabCurve(pts, color, dashed, width)
}

// ── Per-topic configs ────────────────────────────────────────────────────────

private fun polynomialConfig() = LabConfig(
    intro = "One slider: the degree of the polynomial. Training error can only fall as it rises — the held-out error is the one that tells the truth.",
    data = ::wavyData,
    sliders = listOf(LabSlider("Degree", 1f..9f, 3f, steps = 7) { it.roundToInt().toString() }),
    solveLabel = "✦ Best by held-out",
    solve = { points, _ ->
        val test = heldOut(points)
        val train = points.filterIndexed { i, _ -> i !in test }
        val holdout = points.filterIndexed { i, _ -> i in test }
        val best = (1..9).minByOrNull { d ->
            val (xMin, xMax) = points.xRange()
            val beta = ridgeSolve(designMatrix(train.map { it.x }, d, xMin, xMax), train.map { it.y.toDouble() }.toDoubleArray(), 0.0)
            mse(holdout) { x -> polyValue(beta, scaleX(x, xMin, xMax)) }
        } ?: 3
        floatArrayOf(best.toFloat())
    },
    legend = listOf(LabPointColor to "Train", LabHighlight to "Held out"),
    evaluate = { points, values ->
        val degree = values[0].roundToInt()
        val (xMin, xMax) = points.xRange()
        val test = heldOut(points)
        val train = points.filterIndexed { i, _ -> i !in test }
        val holdout = points.filterIndexed { i, _ -> i in test }
        val beta = ridgeSolve(designMatrix(train.map { it.x }, degree, xMin, xMax), train.map { it.y.toDouble() }.toDoubleArray(), 0.0)
        val predict = { x: Float -> polyValue(beta, scaleX(x, xMin, xMax)) }
        val trainMse = mse(train, predict)
        val testMse = mse(holdout, predict)
        LabResult(
            curves = listOf(sampleCurve(xMin, xMax, LabFit, f = predict)),
            readouts = listOf(
                LabReadout(trainMse.fmt(), "Train MSE"),
                LabReadout(testMse.fmt(), "Held-out MSE"),
                LabReadout("${degree + 1}", "Coefficients"),
            ),
            note = if (testMse > trainMse * 2.2 && degree > 4) {
                "Held-out error is now more than double the training error. The curve is fitting noise it will never see again — this is overfitting, not a better model."
            } else {
                "Degree $degree fits ${degree + 1} coefficients to ${train.size} training points."
            },
            highlighted = test,
        )
    },
)

private fun ridgeConfig() = LabConfig(
    intro = "A degree-9 polynomial — far more flexible than the data justifies — with the L2 penalty as the only defence. λ is on a log scale.",
    data = ::wavyData,
    sliders = listOf(LabSlider("log₁₀ λ", -6f..2f, -6f) { "1e${it.roundToInt()}" }),
    solveLabel = "✦ Best by held-out",
    solve = { points, _ ->
        val test = heldOut(points)
        val train = points.filterIndexed { i, _ -> i !in test }
        val holdout = points.filterIndexed { i, _ -> i in test }
        val (xMin, xMax) = points.xRange()
        val best = (-6..2).minByOrNull { e ->
            val beta = ridgeSolve(designMatrix(train.map { it.x }, 9, xMin, xMax), train.map { it.y.toDouble() }.toDoubleArray(), Math.pow(10.0, e.toDouble()))
            mse(holdout) { x -> polyValue(beta, scaleX(x, xMin, xMax)) }
        } ?: -3
        floatArrayOf(best.toFloat())
    },
    legend = listOf(LabPointColor to "Train", LabHighlight to "Held out", LabReference to "λ = 0"),
    evaluate = { points, values ->
        val lambda = Math.pow(10.0, values[0].toDouble())
        val (xMin, xMax) = points.xRange()
        val test = heldOut(points)
        val train = points.filterIndexed { i, _ -> i !in test }
        val holdout = points.filterIndexed { i, _ -> i in test }
        val x = designMatrix(train.map { it.x }, 9, xMin, xMax)
        val y = train.map { it.y.toDouble() }.toDoubleArray()
        val beta = ridgeSolve(x, y, lambda)
        val plain = ridgeSolve(x, y, 0.0)
        val norm = kotlin.math.sqrt(beta.drop(1).sumOf { it * it })
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) { polyValue(plain, scaleX(it, xMin, xMax)) },
                sampleCurve(xMin, xMax, LabFit) { polyValue(beta, scaleX(it, xMin, xMax)) },
            ),
            readouts = listOf(
                LabReadout(mse(train) { polyValue(beta, scaleX(it, xMin, xMax)) }.fmt(), "Train MSE"),
                LabReadout(mse(holdout) { polyValue(beta, scaleX(it, xMin, xMax)) }.fmt(), "Held-out MSE"),
                LabReadout(norm.fmt(), "‖β‖₂"),
            ),
            note = "Ridge shrinks every coefficient toward zero but sets none of them to zero — the norm falls smoothly and all 10 terms stay in the model.",
            highlighted = test,
        )
    },
)

private fun lassoConfig() = LabConfig(
    intro = "The identical setup as Ridge, with L1 in place of L2. Watch the coefficient count, not just the curve.",
    data = ::wavyData,
    sliders = listOf(LabSlider("log₁₀ λ", -5f..0f, -5f) { "1e${it.roundToInt()}" }),
    solveLabel = null,
    solve = null,
    legend = listOf(LabPointColor to "Train", LabReference to "Ridge, same λ"),
    evaluate = { points, values ->
        val lambda = Math.pow(10.0, values[0].toDouble())
        val (xMin, xMax) = points.xRange()
        val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
        val y = points.map { it.y.toDouble() }.toDoubleArray()
        val lasso = coordinateDescent(x, y, lambda, l1Ratio = 1.0)
        val ridge = ridgeSolve(x, y, lambda * points.size)
        val nonZero = lasso.drop(1).count { abs(it) > 1e-6 }
        val ridgeNonZero = ridge.drop(1).count { abs(it) > 1e-6 }
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) { polyValue(ridge, scaleX(it, xMin, xMax)) },
                sampleCurve(xMin, xMax, LabFit) { polyValue(lasso, scaleX(it, xMin, xMax)) },
            ),
            readouts = listOf(
                LabReadout(mse(points) { polyValue(lasso, scaleX(it, xMin, xMax)) }.fmt(), "Train MSE"),
                LabReadout("$nonZero of 9", "Non-zero β"),
                LabReadout("$ridgeNonZero of 9", "Ridge non-zero"),
            ),
            note = "At this λ lasso keeps $nonZero of the 9 polynomial terms; ridge at the same penalty keeps $ridgeNonZero. L1's corner at zero is what makes coefficients land exactly on it — L2's smooth bowl never does.",
        )
    },
)

private fun elasticNetConfig() = LabConfig(
    intro = "Two dials. λ sets how much penalty, and the mix sets how much of it is L1 — 0 is pure ridge, 1 is pure lasso.",
    data = ::wavyData,
    sliders = listOf(
        LabSlider("log₁₀ λ", -5f..0f, -3f) { "1e${it.roundToInt()}" },
        LabSlider("L1 ratio", 0f..1f, 0.5f),
    ),
    solveLabel = null,
    solve = null,
    evaluate = { points, values ->
        val lambda = Math.pow(10.0, values[0].toDouble())
        val ratio = values[1].toDouble()
        val (xMin, xMax) = points.xRange()
        val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
        val y = points.map { it.y.toDouble() }.toDoubleArray()
        val beta = coordinateDescent(x, y, lambda, ratio)
        val nonZero = beta.drop(1).count { abs(it) > 1e-6 }
        val norm = kotlin.math.sqrt(beta.drop(1).sumOf { it * it })
        LabResult(
            curves = listOf(sampleCurve(xMin, xMax, LabFit) { polyValue(beta, scaleX(it, xMin, xMax)) }),
            readouts = listOf(
                LabReadout(mse(points) { polyValue(beta, scaleX(it, xMin, xMax)) }.fmt(), "Train MSE"),
                LabReadout("$nonZero of 9", "Non-zero β"),
                LabReadout(norm.fmt(), "‖β‖₂"),
            ),
            note = when {
                ratio < 0.05 -> "Pure ridge: everything shrinks, nothing is eliminated."
                ratio > 0.95 -> "Pure lasso: sparse, but among correlated terms it picks one arbitrarily and drops the rest."
                else -> "Mixed: the L2 part keeps correlated terms in together rather than letting L1 pick a winner at random, while the L1 part still zeroes what is useless."
            },
        )
    },
)

private fun stepwiseConfig() = LabConfig(
    intro = "Forward selection over the same nine polynomial terms: at each step add whichever remaining term cuts residual error most.",
    data = ::wavyData,
    sliders = listOf(LabSlider("Terms kept", 1f..9f, 3f, steps = 7) { it.roundToInt().toString() }),
    solveLabel = null,
    solve = null,
    evaluate = { points, values ->
        val terms = values[0].roundToInt()
        val (xMin, xMax) = points.xRange()
        val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
        val y = points.map { it.y.toDouble() }.toDoubleArray()
        val result = forwardStepwise(x, y, terms)
        LabResult(
            curves = listOf(sampleCurve(xMin, xMax, LabFit) { polyValue(result.beta, scaleX(it, xMin, xMax)) }),
            readouts = listOf(
                LabReadout(mse(points) { polyValue(result.beta, scaleX(it, xMin, xMax)) }.fmt(), "Train MSE"),
                LabReadout(result.adjustedR2.fmt(), "Adjusted R²"),
                LabReadout(result.selected.joinToString(",") { "x^$it" }.ifEmpty { "—" }, "Selected"),
            ),
            note = "Each term was chosen by looking at this data, so the reported p-values and R² are optimistically biased — the selection step is never accounted for. Adjusted R² helps a little; honest evaluation needs a held-out set.",
        )
    },
)

private fun robustConfig() = LabConfig(
    intro = "Twenty-six points from one process and six from another. The slider is RANSAC's inlier threshold — how far off the line a point may sit and still count.",
    data = ::outlierData,
    sliders = listOf(LabSlider("Inlier threshold", 0.2f..4f, 0.9f)),
    solveLabel = null,
    solve = null,
    legend = listOf(LabFit to "RANSAC", LabReference to "OLS", LabHighlight to "Outlier"),
    evaluate = { points, values ->
        val threshold = values[0].toDouble()
        val (xMin, xMax) = points.xRange()
        val fit = ransac(points, threshold)
        val (olsM, olsC) = ordinaryLeastSquares(points)
        val outliers = points.indices.filter { it !in fit.inliers }.toSet()
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) { olsM * it + olsC },
                sampleCurve(xMin, xMax, LabFit) { fit.slope * it + fit.intercept },
            ),
            readouts = listOf(
                LabReadout(fit.slope.fmt(), "RANSAC slope"),
                LabReadout(olsM.fmt(), "OLS slope"),
                LabReadout("${fit.inliers.size}/${points.size}", "Inliers"),
            ),
            note = "Squared error grows with the square of the residual, so the six contaminating points dominate the OLS fit and drag it away from the ${fit.inliers.size} points that share a trend. RANSAC never averages them in — it finds the largest consensus set and fits only that.",
            highlighted = outliers,
        )
    },
)

private fun quantileConfig() = LabConfig(
    intro = "Spread grows with x here, so \"the average response\" and \"the 90th percentile response\" are genuinely different lines. τ picks which one to fit.",
    data = ::fanData,
    sliders = listOf(LabSlider("τ (quantile)", 0.05f..0.95f, 0.5f)),
    solveLabel = null,
    solve = null,
    legend = listOf(LabFit to "τ fit", LabBand to "τ = 0.1 / 0.9", LabReference to "OLS mean"),
    evaluate = { points, values ->
        val tau = values[0].toDouble()
        val (xMin, xMax) = points.xRange()
        val (m, c) = quantileFit(points, tau)
        val (lowM, lowC) = quantileFit(points, 0.1)
        val (highM, highC) = quantileFit(points, 0.9)
        val (meanM, meanC) = ordinaryLeastSquares(points)
        val below = points.count { it.y < m * it.x + c }
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabBand, dashed = true, width = 3f) { lowM * it + lowC },
                sampleCurve(xMin, xMax, LabBand, dashed = true, width = 3f) { highM * it + highC },
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) { meanM * it + meanC },
                sampleCurve(xMin, xMax, LabFit) { m * it + c },
            ),
            readouts = listOf(
                LabReadout("${(below * 100f / points.size).roundToInt()}%", "Points below"),
                LabReadout("${(tau * 100).roundToInt()}%", "Target τ"),
                LabReadout(pinballLoss(points, tau, m, c).fmt(), "Pinball loss"),
            ),
            note = "The check: a correct τ-quantile fit should leave about τ of the data beneath it — measured ${(below * 100f / points.size).roundToInt()}% against a target of ${(tau * 100).roundToInt()}%. Note also that the τ=0.1 and τ=0.9 lines are not parallel; they fan out because the spread does.",
        )
    },
)

private fun bayesianConfig() = LabConfig(
    intro = "A degree-5 fit that returns a distribution rather than a line. The shaded band is ±2 predictive standard deviations.",
    data = { seed ->
        // A deliberate gap in the middle of the x range — the interesting thing about a posterior is
        // what it does where there is no data.
        wavyData(seed).filterNot { it.x > 3.2f && it.x < 5.6f }
    },
    sliders = listOf(
        LabSlider("Prior precision α", 0.01f..8f, 1f),
        LabSlider("Noise variance", 0.05f..2f, 0.4f),
    ),
    solveLabel = null,
    solve = null,
    legend = listOf(LabFit to "Posterior mean", LabBand to "±2σ"),
    evaluate = { points, values ->
        val alpha = values[0].toDouble()
        val noise = values[1].toDouble()
        val (xMin, xMax) = points.xRange()
        val x = designMatrix(points.map { it.x }, 5, xMin, xMax)
        val y = points.map { it.y.toDouble() }.toDoubleArray()
        val fit = bayesianRidge(x, y, alpha, noise)
        val phiAt = { v: Float ->
            val t = scaleX(v, xMin, xMax)
            DoubleArray(6) { p -> if (p == 0) 1.0 else Math.pow(t, p.toDouble()) }
        }
        val mean = { v: Float -> polyValue(fit.mean, scaleX(v, xMin, xMax)) }
        val gapStd = predictiveStd(fit, phiAt(4.4f))
        val denseStd = predictiveStd(fit, phiAt(1.2f))
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabBand, dashed = true, width = 3f) { mean(it) + 2 * predictiveStd(fit, phiAt(it)) },
                sampleCurve(xMin, xMax, LabBand, dashed = true, width = 3f) { mean(it) - 2 * predictiveStd(fit, phiAt(it)) },
                sampleCurve(xMin, xMax, LabFit, f = mean),
            ),
            readouts = listOf(
                LabReadout(denseStd.fmt(), "σ where dense"),
                LabReadout(gapStd.fmt(), "σ in the gap"),
                LabReadout((gapStd / denseStd.coerceAtLeast(1e-6)).fmt() + "×", "Ratio"),
            ),
            note = "The band widens across the gap because the posterior covariance term grows where no data constrains it. A plain ridge fit produces the same mean curve and no way at all to know that middle stretch is a guess.",
        )
    },
)

private fun poissonConfig() = LabConfig(
    intro = "Counts, not measurements: integers, never negative, and with variance that grows alongside the mean. Sliders are the log-linear coefficients.",
    data = ::countData,
    sliders = listOf(
        LabSlider("β₀ (intercept)", -2f..2f, -0.4f),
        LabSlider("β₁ (slope)", -0.2f..0.8f, 0.42f),
    ),
    solveLabel = "✦ Fit by IRLS",
    solve = { points, _ ->
        val (b0, b1) = poissonIrls(points)
        floatArrayOf(b0.toFloat().coerceIn(-2f, 2f), b1.toFloat().coerceIn(-0.2f, 0.8f))
    },
    legend = listOf(LabFit to "exp(β₀+β₁x)", LabReference to "OLS line"),
    evaluate = { points, values ->
        val b0 = values[0].toDouble()
        val b1 = values[1].toDouble()
        val (xMin, xMax) = points.xRange()
        val (olsM, olsC) = ordinaryLeastSquares(points)
        val predict = { v: Float -> exp((b0 + b1 * v).coerceIn(-20.0, 20.0)) }
        val olsNegativeFrom = (0..80).map { xMin + (xMax - xMin) * it / 80 }.firstOrNull { olsM * it + olsC < 0 }
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) { olsM * it + olsC },
                sampleCurve(xMin, xMax, LabFit, f = predict),
            ),
            readouts = listOf(
                LabReadout(poissonDeviance(points, b0, b1).fmt(), "Deviance"),
                LabReadout(mse(points) { predict(it) }.fmt(), "MSE"),
                LabReadout(exp(b1).fmt() + "×", "Per unit x"),
            ),
            note = if (olsNegativeFrom != null) {
                "The dashed OLS line crosses zero at x ≈ ${olsNegativeFrom.round2()} and predicts negative counts below it — impossible for the data it is modelling. The log link makes that unrepresentable: exp is positive everywhere."
            } else {
                "The log link means β₁ is multiplicative: each unit of x multiplies the expected count by exp(β₁) = ${exp(b1).fmt()}, rather than adding a constant."
            },
        )
    },
)

private fun isotonicConfig() = LabConfig(
    intro = "No functional form assumed at all — only that the fit must never decrease. The slider adds noise to show when the constraint binds.",
    data = ::monotoneData,
    sliders = listOf(LabSlider("Extra noise", 0f..2f, 0f)),
    solveLabel = null,
    solve = null,
    legend = listOf(LabFit to "Isotonic (PAVA)", LabReference to "Linear fit"),
    evaluate = { points, values ->
        val extra = values[0]
        val random = Random(9)
        val noisy = points.map { LabPoint(it.x, it.y + (random.nextFloat() - 0.5f) * 2f * extra) }
        val fit = pava(noisy)
        val (m, c) = ordinaryLeastSquares(noisy)
        val (xMin, xMax) = noisy.xRange()
        val stepCurve = buildList {
            fit.xs.forEachIndexed { i, x ->
                if (i > 0) add(x to fit.ys[i - 1].toFloat()) // vertical riser: the step shape
                add(x to fit.ys[i].toFloat())
            }
        }
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) { m * it + c },
                LabCurve(stepCurve, LabFit),
            ),
            readouts = listOf(
                LabReadout("${fit.blocks}", "Blocks"),
                LabReadout("${noisy.size}", "Points"),
                LabReadout(mse(noisy) { v -> fit.ys[fit.xs.indexOfLast { it <= v }.coerceAtLeast(0)] }.fmt(), "MSE"),
            ),
            note = "PAVA merged ${noisy.size} points into ${fit.blocks} flat blocks. Every merge is a place the raw data went down and monotonicity said it may not — more noise means more violations, so fewer and wider blocks.",
        )
    },
)

private fun larsConfig() = LabConfig(
    intro = "The coefficient path, one step at a time. Each step admits the predictor most correlated with the current residual, then moves in the direction that keeps the active correlations equal.",
    data = ::wavyData,
    sliders = listOf(LabSlider("Path steps", 1f..8f, 2f, steps = 6) { it.roundToInt().toString() }),
    solveLabel = null,
    solve = null,
    evaluate = { points, values ->
        val steps = values[0].roundToInt()
        val (xMin, xMax) = points.xRange()
        val x = designMatrix(points.map { it.x }, 9, xMin, xMax)
        val y = points.map { it.y.toDouble() }.toDoubleArray()
        val path = larsPath(x, y, steps)
        val current = path.lastOrNull()
        val beta = current?.beta ?: DoubleArray(10)
        LabResult(
            curves = listOf(sampleCurve(xMin, xMax, LabFit) { polyValue(beta, scaleX(it, xMin, xMax)) }),
            readouts = listOf(
                LabReadout(current?.active?.joinToString(",") { "x^$it" } ?: "—", "Active set"),
                LabReadout("${current?.active?.size ?: 0}", "Size"),
                LabReadout(current?.maxCorrelation?.fmt() ?: "—", "Max |corr|"),
            ),
            note = "Step $steps admitted x^${current?.entered ?: 0}. LARS moves partway rather than all the way, so a predictor is never fully fitted before the next one is considered — that is the difference from forward stepwise, and it is what makes the path piecewise-linear and cheap to compute in full.",
        )
    },
)

// ── Time series (phase 9, batch B7) ──────────────────────────────────────────
// Six forecasting topics on one monthly series, so the methods are comparable rather than each
// flattered by its own data. The last twelve points are held out of every fit and drawn in the
// highlight colour; every note scores the forecast against them and against the seasonal-naive
// benchmark, because an in-sample fit can be made arbitrarily good and proves nothing.
//
// The estimators are in TimeSeriesMath.kt and are the real thing — least squares on the lagged
// design for AR, Hannan-Rissanen for ARIMA's MA terms, the three Holt-Winters recursions, a
// piecewise-linear trend on changepoint basis functions plus a Fourier seasonality for Prophet.

private val SeriesForecast = SimColors.Red
private val SeriesComponent = SimColors.Amber

private fun seriesData(seed: Int): List<LabPoint> =
    retailSeries(seed).mapIndexed { t, v -> LabPoint(t.toFloat(), v.toFloat()) }

private fun heldOutIndices(points: List<LabPoint>): Set<Int> =
    points.indices.filter { it >= TrainLength }.toSet()

private fun seriesCurve(values: List<Double?>, startAt: Int, color: Color, dashed: Boolean = false, width: Float = 5f): LabCurve =
    LabCurve(
        values.mapIndexedNotNull { i, v -> v?.let { (startAt + i).toFloat() to it.toFloat() } },
        color, dashed, width,
    )

private fun seriesTrain(points: List<LabPoint>) = points.take(TrainLength).map { it.y.toDouble() }
private fun seriesActual(points: List<LabPoint>) = points.drop(TrainLength).map { it.y.toDouble() }

/** The comparison every forecasting note here makes, computed rather than asserted. */
private fun benchmarkNote(name: String, rmse: Double, benchmark: Double): String =
    if (rmse < benchmark) {
        "$name beats the seasonal-naive benchmark (${rmse.fmt()} against ${benchmark.fmt()})."
    } else {
        "$name loses to the seasonal-naive benchmark — ${rmse.fmt()} against ${benchmark.fmt()}. " +
            "A method that cannot beat \"next year looks like last year\" is not yet earning its complexity."
    }

private fun movingAverageConfig() = LabConfig(
    intro = "One slider: the window. Watch two things move in opposite directions — how much jitter is removed, and how far behind the series the smoothed line falls.",
    data = ::seriesData,
    sliders = listOf(LabSlider("Window", 2f..12f, 3f, steps = 9) { "${it.roundToInt()} months" }),
    solveLabel = null,
    solve = null,
    legend = listOf(LabPointColor to "Observed", LabFit to "Moving average", LabHighlight to "Held out"),
    evaluate = { points, values ->
        val window = values[0].roundToInt()
        val train = seriesTrain(points)
        val smoothed = movingAverage(train, window)
        val rawRoughness = roughness(train)
        val smoothRoughness = roughness(smoothed.filterNotNull())
        // A trailing window of w has its centre of mass (w−1)/2 periods back, which is exactly the
        // lag it introduces — the cost that buys the smoothing.
        val lag = (window - 1) / 2.0
        LabResult(
            curves = listOf(seriesCurve(smoothed, 0, LabFit)),
            readouts = listOf(
                LabReadout(smoothRoughness.fmt(), "Roughness"),
                LabReadout("−${(100 * (1 - smoothRoughness / rawRoughness)).fmt()}%", "vs raw ${rawRoughness.fmt()}"),
                LabReadout("${lag.fmt()} mo", "Lag introduced"),
            ),
            note = "A $window-month trailing window cut the month-to-month jitter from ${rawRoughness.fmt()} to " +
                "${smoothRoughness.fmt()}, dropped the first ${window - 1} months entirely, and put the line " +
                "${lag.fmt()} months behind the series. At a window of ${SeasonPeriod} the seasonal cycle is averaged " +
                "away completely, which is how a moving average is used as a trend estimate rather than as a forecast.",
            highlighted = heldOutIndices(points),
        )
    },
)

private fun autoregressionConfig() = LabConfig(
    intro = "Regress the series on its own past. p is how many lags it may use; the forecast past month 48 is recursive — each prediction becomes the next input.",
    data = ::seriesData,
    sliders = listOf(LabSlider("Lags (p)", 1f..12f, 2f, steps = 10) { "p = ${it.roundToInt()}" }),
    solveLabel = "✦ Best by held-out",
    solve = { points, _ ->
        val train = seriesTrain(points)
        val actual = seriesActual(points)
        val best = (1..12).minByOrNull { p ->
            forecastRmse(arForecast(fitAr(train, p), train, actual.size), actual)
        } ?: 2
        floatArrayOf(best.toFloat())
    },
    legend = listOf(LabPointColor to "Observed", LabFit to "In-sample fit", SeriesForecast to "Forecast"),
    evaluate = { points, values ->
        val p = values[0].roundToInt()
        val train = seriesTrain(points)
        val actual = seriesActual(points)
        val fit = fitAr(train, p)
        val forecast = arForecast(fit, train, actual.size)
        val fitted = train.indices.map { i ->
            if (i < p) null else train[i] - fit.residuals[i - p]
        }
        val outRmse = forecastRmse(forecast, actual)
        val benchmark = forecastRmse(seasonalNaiveForecast(train, actual.size), actual)
        LabResult(
            curves = listOf(
                seriesCurve(fitted, 0, LabFit),
                seriesCurve(forecast.map { it as Double? }, TrainLength, SeriesForecast, width = 6f),
            ),
            readouts = listOf(
                LabReadout(fit.rmse.fmt(), "In-sample RMSE"),
                LabReadout(outRmse.fmt(), "Forecast RMSE"),
                LabReadout(fit.coefficients.getOrElse(1) { 0.0 }.fmt(), "φ₁"),
            ),
            note = "AR($p): ${fit.coefficients.drop(1).joinToString { it.fmt() }}. " +
                benchmarkNote("It", outRmse, benchmark) +
                " An AR model has no seasonal term at all, so the only way it can reach twelve months back is to " +
                "spend twelve lags getting there — which is what p = 12 is doing, and why the seasonal models that " +
                "follow exist.",
            highlighted = heldOutIndices(points),
        )
    },
)

private fun arimaConfig() = LabConfig(
    intro = "The three letters as three sliders. d differences the series until it is stationary, p regresses on its own lags, q regresses on past errors.",
    data = ::seriesData,
    sliders = listOf(
        LabSlider("AR order (p)", 0f..4f, 2f, steps = 3) { "p = ${it.roundToInt()}" },
        LabSlider("Differencing (d)", 0f..2f, 1f, steps = 1) { "d = ${it.roundToInt()}" },
        LabSlider("MA order (q)", 0f..3f, 1f, steps = 2) { "q = ${it.roundToInt()}" },
    ),
    solveLabel = null,
    solve = null,
    legend = listOf(LabPointColor to "Observed", SeriesForecast to "Forecast", LabHighlight to "Held out"),
    evaluate = { points, values ->
        val p = values[0].roundToInt()
        val d = values[1].roundToInt()
        val q = values[2].roundToInt()
        val train = seriesTrain(points)
        val actual = seriesActual(points)
        val fit = fitArima(train, p, d, q, actual.size)
        val outRmse = forecastRmse(fit.forecast, actual)
        val benchmark = forecastRmse(seasonalNaiveForecast(train, actual.size), actual)

        var differenced = train
        repeat(d) { differenced = difference(differenced) }
        val acfRaw = lag1Autocorrelation(train)
        val acfNow = lag1Autocorrelation(differenced)

        LabResult(
            curves = listOf(seriesCurve(fit.forecast.map { it as Double? }, TrainLength, SeriesForecast, width = 6f)),
            readouts = listOf(
                LabReadout(acfNow.fmt(), "Lag-1 ACF"),
                LabReadout(outRmse.fmt(), "Forecast RMSE"),
                LabReadout("${train.size - d}", "Usable rows"),
            ),
            note = buildString {
                append(
                    if (d == 0) {
                        "Undifferenced, the lag-1 autocorrelation is ${acfRaw.fmt()} — close to 1, which is what a " +
                            "trending series looks like and what \"non-stationary\" means in practice. "
                    } else {
                        "After $d round${if (d > 1) "s" else ""} of differencing the lag-1 autocorrelation is " +
                            "${acfNow.fmt()}, down from ${acfRaw.fmt()}. Each round costs one row and removes one " +
                            "order of trend; over-differencing is a real failure and shows up as an ACF driven negative. "
                    },
                )
                append(benchmarkNote("ARIMA($p,$d,$q)", outRmse, benchmark))
                append(
                    " The MA terms are fitted by Hannan-Rissanen — a long AR first, then a regression on its own " +
                        "residuals — and they decay out of the forecast after q steps, because future errors are zero " +
                        "in expectation.",
                )
            },
            highlighted = heldOutIndices(points),
        )
    },
)

private fun sarimaConfig() = LabConfig(
    intro = "The same machinery with one addition: a difference at lag 12 rather than lag 1. Toggle it and watch both the lag-12 autocorrelation and the forecast error.",
    data = ::seriesData,
    sliders = listOf(
        LabSlider("AR order (p)", 1f..6f, 2f, steps = 4) { "p = ${it.roundToInt()}" },
        LabSlider("Seasonal difference", 0f..1f, 1f, steps = 1) { if (it.roundToInt() == 1) "on (lag 12)" else "off" },
    ),
    solveLabel = null,
    solve = null,
    legend = listOf(LabPointColor to "Observed", SeriesForecast to "Forecast", LabHighlight to "Held out"),
    evaluate = { points, values ->
        val p = values[0].roundToInt()
        val seasonal = values[1].roundToInt() == 1
        val train = seriesTrain(points)
        val actual = seriesActual(points)
        val fit = fitSarima(train, p, seasonal, actual.size)
        val outRmse = forecastRmse(fit.forecast, actual)
        val benchmark = forecastRmse(seasonalNaiveForecast(train, actual.size), actual)
        val without = forecastRmse(fitSarima(train, p, false, actual.size).forecast, actual)

        LabResult(
            curves = listOf(seriesCurve(fit.forecast.map { it as Double? }, TrainLength, SeriesForecast, width = 6f)),
            readouts = listOf(
                LabReadout(fit.acfAfter.fmt(), "Lag-12 ACF"),
                LabReadout(outRmse.fmt(), "Forecast RMSE"),
                LabReadout("${fit.seasonalDifferenced.size}", "Usable rows"),
            ),
            note = if (seasonal) {
                "Differencing at lag 12 — yₜ − yₜ₋₁₂ — dropped the lag-12 autocorrelation from " +
                    "${fit.acfBefore.fmt()} to ${fit.acfAfter.fmt()} and cost ${SeasonPeriod} rows. Forecast error " +
                    "${outRmse.fmt()} against ${without.fmt()} with the seasonal difference off. " +
                    benchmarkNote("It", outRmse, benchmark) +
                    " That single subtraction is the whole seasonal idea: compare each month with the same month a " +
                    "year ago rather than with last month."
            } else {
                "With no seasonal difference the lag-12 autocorrelation stays at ${fit.acfBefore.fmt()}, and the " +
                    "annual cycle is left for the AR lags to reconstruct one month at a time. Forecast error " +
                    "${outRmse.fmt()}. " + benchmarkNote("It", outRmse, benchmark) +
                    " Turn the seasonal difference on and compare."
            },
            highlighted = heldOutIndices(points),
        )
    },
)

private fun exponentialSmoothingConfig() = LabConfig(
    intro = "Three recursions, three sliders. α smooths the level, β the trend, γ the seasonal figures — each one an exponentially-weighted compromise between the newest observation and everything before it.",
    data = ::seriesData,
    sliders = listOf(
        LabSlider("α (level)", 0.05f..0.95f, 0.3f),
        LabSlider("β (trend)", 0.01f..0.6f, 0.1f),
        LabSlider("γ (seasonal)", 0.05f..0.95f, 0.3f),
    ),
    solveLabel = "✦ Best by held-out",
    solve = { points, _ ->
        val train = seriesTrain(points)
        val actual = seriesActual(points)
        var best = floatArrayOf(0.3f, 0.1f, 0.3f)
        var bestRmse = Double.MAX_VALUE
        listOf(0.1f, 0.2f, 0.3f, 0.5f, 0.7f, 0.9f).forEach { a ->
            listOf(0.02f, 0.05f, 0.1f, 0.3f).forEach { b ->
                listOf(0.1f, 0.3f, 0.5f, 0.8f).forEach { g ->
                    val e = forecastRmse(
                        holtWinters(train, a.toDouble(), b.toDouble(), g.toDouble(), actual.size).forecast,
                        actual,
                    )
                    if (e < bestRmse) { bestRmse = e; best = floatArrayOf(a, b, g) }
                }
            }
        }
        best
    },
    legend = listOf(LabPointColor to "Observed", LabFit to "Fitted", SeriesComponent to "Level", SeriesForecast to "Forecast"),
    evaluate = { points, values ->
        val train = seriesTrain(points)
        val actual = seriesActual(points)
        val hw = holtWinters(train, values[0].toDouble(), values[1].toDouble(), values[2].toDouble(), actual.size)
        val inRmse = rmseOf(train.indices.map { train[it] - hw.fitted[it] })
        val outRmse = forecastRmse(hw.forecast, actual)
        val benchmark = forecastRmse(seasonalNaiveForecast(train, actual.size), actual)
        LabResult(
            curves = listOf(
                seriesCurve(hw.level.map { it as Double? }, 0, SeriesComponent, dashed = true, width = 4f),
                seriesCurve(hw.fitted.map { it as Double? }, 0, LabFit),
                seriesCurve(hw.forecast.map { it as Double? }, TrainLength, SeriesForecast, width = 6f),
            ),
            readouts = listOf(
                LabReadout(inRmse.fmt(), "In-sample RMSE"),
                LabReadout(outRmse.fmt(), "Forecast RMSE"),
                LabReadout(hw.trend.last().fmt(), "Final trend/mo"),
            ),
            note = buildString {
                append(
                    "The dashed line is the level component with the seasonal figures removed — the series as " +
                        "Holt-Winters believes it would be without its annual cycle. ",
                )
                append(benchmarkNote("Holt-Winters", outRmse, benchmark))
                if (outRmse > inRmse * 2.5) {
                    append(
                        " Note the gap between in-sample ${inRmse.fmt()} and forecast ${outRmse.fmt()}: with these " +
                            "smoothing parameters the model tracks every wobble as if it were signal, so it fits the " +
                            "past well and extrapolates badly. High α, β and γ are not \"more responsive\", they are " +
                            "less smoothed.",
                    )
                }
            },
            highlighted = heldOutIndices(points),
        )
    },
)

private fun prophetConfig() = LabConfig(
    intro = "Prophet's model form, fitted as one least-squares problem: a piecewise-linear trend with candidate changepoints, plus a Fourier seasonality. The penalty slider is its sparse prior on the slope changes.",
    data = ::seriesData,
    sliders = listOf(
        LabSlider("Changepoints", 0f..8f, 4f, steps = 8) { it.roundToInt().toString() },
        LabSlider("Fourier order", 1f..4f, 2f, steps = 2) { it.roundToInt().toString() },
        LabSlider("log₁₀ changepoint penalty", -2f..2f, 0f) { "1e${it.roundToInt()}" },
    ),
    solveLabel = null,
    solve = null,
    legend = listOf(LabPointColor to "Observed", SeriesComponent to "Trend g(t)", LabFit to "g(t) + s(t)", SeriesForecast to "Forecast"),
    evaluate = { points, values ->
        val changepoints = values[0].roundToInt()
        val order = values[1].roundToInt()
        val penalty = Math.pow(10.0, values[2].toDouble())
        val train = seriesTrain(points)
        val actual = seriesActual(points)
        val fit = fitProphet(train, changepoints, order, penalty, actual.size)
        val outRmse = forecastRmse(fit.forecast, actual)
        val benchmark = forecastRmse(seasonalNaiveForecast(train, actual.size), actual)
        val used = fit.deltas.count { abs(it) > 0.15 }
        LabResult(
            curves = listOf(
                seriesCurve(fit.trend.map { it as Double? }, 0, SeriesComponent, dashed = true, width = 4f),
                seriesCurve(fit.forecastTrend.map { it as Double? }, TrainLength, SeriesComponent, dashed = true, width = 4f),
                seriesCurve(fit.fitted.map { it as Double? }, 0, LabFit),
                seriesCurve(fit.forecast.map { it as Double? }, TrainLength, SeriesForecast, width = 6f),
            ),
            readouts = listOf(
                LabReadout(fit.trainRmse.fmt(), "In-sample RMSE"),
                LabReadout(outRmse.fmt(), "Forecast RMSE"),
                LabReadout("$used of $changepoints", "Slopes used"),
            ),
            note = buildString {
                append("y(t) = g(t) + s(t): the dashed line is the trend, the solid one adds the Fourier seasonality " +
                    "of order $order (${2 * order} terms). ")
                if (changepoints == 0) {
                    append(
                        "With no changepoints the trend is a single straight line for the whole history — and this " +
                            "series changes slope partway through, so one line cannot describe both halves. " +
                            "Forecast error ${outRmse.fmt()}. Raise the changepoint count and watch it fall.",
                    )
                } else {
                    append(
                        "$changepoints candidate changepoints at months ${fit.changepoints.joinToString()}; " +
                            "$used of them took a slope change larger than 0.15. The penalty is applied to those " +
                            "slope changes alone — the intercept, the global slope and the seasonal terms are free — " +
                            "which is what stops the model putting a kink at every candidate. ",
                    )
                    append(benchmarkNote("It", outRmse, benchmark))
                }
            },
            highlighted = heldOutIndices(points),
        )
    },
)

// ── Evaluation metrics (phase 9, batch B9) ───────────────────────────────────
// The five regression metrics are the batch's one genuinely interactive group: every one of them is a
// function of the residuals, so a slider that moves the line moves all five at once and the
// disagreements between them are something to be found rather than read. The dataset is the one from
// MetricsMath.kt — 40 clean points and 4 contaminating ones — so the numbers here are the numbers
// that file's tests pin.

private fun metricPoints(seed: Int): List<LabPoint> =
    RegressionMetricsLab.points.map { LabPoint(it.x.toFloat(), it.y.toFloat()) }
        .let { if (seed == 4) it else it }   // fixed dataset: the metric comparison needs one picture

private fun contaminatedIndices(): Set<Int> =
    RegressionMetricsLab.points.indices.filter { RegressionMetricsLab.points[it].contaminated }.toSet()

private fun metricResiduals(points: List<LabPoint>, slope: Double, intercept: Double) =
    points.map { it.y - (intercept + slope * it.x) }

private fun metricMse(points: List<LabPoint>, slope: Double, intercept: Double) =
    metricResiduals(points, slope, intercept).sumOf { it * it } / points.size

private fun metricMae(points: List<LabPoint>, slope: Double, intercept: Double) =
    metricResiduals(points, slope, intercept).sumOf { kotlin.math.abs(it) } / points.size

private fun metricR2(points: List<LabPoint>, slope: Double, intercept: Double): Double {
    val mean = points.map { it.y.toDouble() }.average()
    val residual = metricResiduals(points, slope, intercept).sumOf { it * it }
    val total = points.sumOf { (it.y - mean) * (it.y - mean) }
    return 1 - residual / total
}

private fun lineSliders(): List<LabSlider> = listOf(
    LabSlider("Slope", 0.5f..2.6f, 1.86f, symbol = "m"),
    LabSlider("Intercept", -2f..6f, 0.98f, symbol = "c"),
)

private fun solveToLeastSquares(points: List<LabPoint>, current: FloatArray): FloatArray {
    val (m, c) = ordinaryLeastSquares(points)
    return floatArrayOf(m.toFloat(), c.toFloat())
}

private fun mseConfig() = LabConfig(
    intro = "Forty points on a line plus four from somewhere else. Move the line and watch squared error respond — then press Least Squares to jump to the line that minimises it.",
    data = ::metricPoints,
    reseedable = false,
    sliders = lineSliders(),
    solveLabel = "Least Squares",
    solve = ::solveToLeastSquares,
    legend = listOf(LabFit to "Your line", LabReference to "MAE-optimal", LabHighlight to "Outlier"),
    evaluate = { points, values ->
        val slope = values[0].toDouble()
        val intercept = values[1].toDouble()
        val (xMin, xMax) = points.xRange()
        val squared = metricResiduals(points, slope, intercept).map { it * it }
        val bad = contaminatedIndices()
        val badShare = bad.sumOf { squared[it] } / squared.sum()
        val dataShare = (bad.size * 100.0 / points.size).roundToInt()
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) {
                    RegressionMetricsLab.absoluteLossFit.predict(it.toDouble())
                },
                sampleCurve(xMin, xMax, LabFit) { intercept + slope * it },
            ),
            readouts = listOf(
                LabReadout(metricMse(points, slope, intercept).fmt(), "MSE"),
                LabReadout(metricMae(points, slope, intercept).fmt(), "MAE"),
            ),
            share = badShare,
            headline = "${bad.size} outliers, $dataShare% of the data, cause ${(badShare * 100).roundToInt()}% of the squared error.",
            highlight = "${bad.size} outliers",
            highlightColor = LabHighlight,
            detail = "Squaring rewards the worst points, so least squares tilts toward them. MAE does not.",
            highlighted = bad,
        )
    },
)

private fun rmseConfig() = LabConfig(
    intro = "The same fit scored two ways. RMSE is the square root of MSE, which changes nothing about the ranking and everything about whether the number means anything.",
    data = ::metricPoints,
    reseedable = false,
    sliders = lineSliders(),
    solveLabel = "Least Squares",
    solve = ::solveToLeastSquares,
    legend = listOf(LabFit to "Your line", LabBand to "±1 RMSE", LabHighlight to "Outlier"),
    evaluate = { points, values ->
        val slope = values[0].toDouble()
        val intercept = values[1].toDouble()
        val (xMin, xMax) = points.xRange()
        val mse = metricMse(points, slope, intercept)
        val rmse = kotlin.math.sqrt(mse)
        val mae = metricMae(points, slope, intercept)
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabBand, dashed = true, width = 3f) { intercept + slope * it - rmse },
                sampleCurve(xMin, xMax, LabBand, dashed = true, width = 3f) { intercept + slope * it + rmse },
                sampleCurve(xMin, xMax, LabFit) { intercept + slope * it },
            ),
            readouts = listOf(
                LabReadout(rmse.fmt(), "RMSE"),
                LabReadout(mse.fmt(), "MSE"),
                LabReadout(mae.fmt(), "MAE"),
            ),
            headline = "RMSE is ${rmse.fmt()} in the units of y, so the band is a distance you can judge.",
            highlight = "units of y",
            detail = "MSE is ${mse.fmt()} in squared units, which nobody has intuition for. RMSE / MAE is ${(rmse / mae).fmt()}: the further above 1, the more the error sits in a few points.",
            highlighted = contaminatedIndices(),
        )
    },
)

private fun maeConfig() = LabConfig(
    intro = "Two objectives, two different lines on the same data. Solve jumps to the MAE-optimal fit; the dashed line is where least squares ends up.",
    data = ::metricPoints,
    reseedable = false,
    sliders = lineSliders(),
    solveLabel = "Minimise MAE",
    solve = { _, _ ->
        floatArrayOf(
            RegressionMetricsLab.absoluteLossFit.slope.toFloat(),
            RegressionMetricsLab.absoluteLossFit.intercept.toFloat(),
        )
    },
    legend = listOf(LabFit to "Your line", LabReference to "Least squares", LabHighlight to "Outlier"),
    evaluate = { points, values ->
        val slope = values[0].toDouble()
        val intercept = values[1].toDouble()
        val (xMin, xMax) = points.xRange()
        val mae = metricMae(points, slope, intercept)
        val mse = metricMse(points, slope, intercept)
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) {
                    RegressionMetricsLab.squaredLossFit.predict(it.toDouble())
                },
                sampleCurve(xMin, xMax, LabFit) { intercept + slope * it },
            ),
            readouts = listOf(
                LabReadout(mae.fmt(), "MAE"),
                LabReadout(mse.fmt(), "MSE"),
                LabReadout(slope.fmt(), "Your slope"),
            ),
            headline = "Minimising MAE gives slope ${RegressionMetricsLab.absoluteLossFit.slope.fmt()}; least squares gives ${RegressionMetricsLab.squaredLossFit.slope.fmt()}.",
            highlight = "Minimising MAE",
            detail = "The data was generated with slope ${RegressionMetricsLab.trueSlope}. Four outliers dominate a squared penalty but barely move an absolute one, so MAE picks a different, more honest line.",
            highlighted = contaminatedIndices(),
        )
    },
)

private fun rSquaredConfig() = LabConfig(
    intro = "R² compares your line against one specific rival: the horizontal line at the mean of y. Move the line below that baseline and R² goes negative.",
    data = ::metricPoints,
    reseedable = false,
    sliders = lineSliders(),
    solveLabel = "Least Squares",
    solve = ::solveToLeastSquares,
    legend = listOf(LabFit to "Your line", LabReference to "Mean of y", LabHighlight to "Outlier"),
    evaluate = { points, values ->
        val slope = values[0].toDouble()
        val intercept = values[1].toDouble()
        val (xMin, xMax) = points.xRange()
        val mean = points.map { it.y.toDouble() }.average()
        val r2 = metricR2(points, slope, intercept)
        val residual = metricResiduals(points, slope, intercept).sumOf { it * it }
        val total = points.sumOf { (it.y - mean) * (it.y - mean) }
        LabResult(
            curves = listOf(
                sampleCurve(xMin, xMax, LabReference, dashed = true, width = 4f) { mean },
                sampleCurve(xMin, xMax, LabFit) { intercept + slope * it },
            ),
            readouts = listOf(
                LabReadout(r2.fmt(), "R²"),
                LabReadout(residual.fmt(), "SS residual"),
                LabReadout(total.fmt(), "SS total"),
            ),
            headline = if (r2 < 0) {
                "R² is ${r2.fmt()}: your line does worse than predicting the mean."
            } else {
                "R² is ${r2.fmt()}: your line explains ${(r2 * 100).roundToInt()}% of the variance around the mean."
            },
            highlight = if (r2 < 0) "worse than predicting the mean" else "${(r2 * 100).roundToInt()}%",
            highlightColor = if (r2 < 0) LabHighlight else null,
            detail = "R² only compares against the dashed line at the mean of y, so a high score says you beat a flat line, not that the model is useful.",
            highlighted = contaminatedIndices(),
        )
    },
)

private fun adjustedRSquaredConfig() = LabConfig(
    intro = "One slider: how many columns of pure random noise to add to the fit. R² can only rise. Adjusted R² does not have to.",
    data = ::metricPoints,
    reseedable = false,
    sliders = listOf(LabSlider("Noise columns", 0f..8f, 0f, steps = 8, format = { it.roundToInt().toString() })),
    solveLabel = null,
    solve = null,
    legend = listOf(LabFit to "R²", LabReference to "Adjusted R²"),
    evaluate = { points, values ->
        val extra = values[0].roundToInt()
        val steps = RegressionMetricsLab.noiseColumns
        val here = steps.first { it.extraColumns == extra }
        val first = steps.first()
        LabResult(
            curves = listOf(
                LabCurve(steps.map { it.extraColumns.toFloat() to it.adjusted.toFloat() }, LabReference, dashed = true, width = 4f),
                LabCurve(steps.map { it.extraColumns.toFloat() to it.rSquared.toFloat() }, LabFit),
            ),
            readouts = listOf(
                LabReadout(here.rSquared.fmt(), "R²"),
                LabReadout(here.adjusted.fmt(), "Adjusted R²"),
                LabReadout("${extra + 1}", "Predictors"),
            ),
            headline = if (extra == 0) {
                "With no noise columns, R² is ${here.rSquared.fmt()} and adjusted R² is ${here.adjusted.fmt()}."
            } else {
                "${if (extra == 1) "One noise column lifts" else "$extra noise columns lift"} R² to ${here.rSquared.fmt()}, while adjusted R² falls to ${here.adjusted.fmt()}."
            },
            highlight = if (extra == 0) null else "adjusted R² falls",
            detail = "Least squares can always use one more column to fit noise, so R² never falls. Adjusted R² charges for each column with (n−1)/(n−p−1); across the sweep it goes from ${first.adjusted.fmt()} to ${steps.last().adjusted.fmt()}.",
        )
    },
)

private val regressionLabConfigs: Map<String, () -> LabConfig> = mapOf(
    "mse" to ::mseConfig,
    "rmse" to ::rmseConfig,
    "mae" to ::maeConfig,
    "r_squared" to ::rSquaredConfig,
    "adjusted_r_squared" to ::adjustedRSquaredConfig,
    "moving_average" to ::movingAverageConfig,
    "autoregression" to ::autoregressionConfig,
    "arima" to ::arimaConfig,
    "sarima" to ::sarimaConfig,
    "exponential_smoothing" to ::exponentialSmoothingConfig,
    "prophet" to ::prophetConfig,
    "polynomial_regression" to ::polynomialConfig,
    "ridge_regression" to ::ridgeConfig,
    "lasso_regression" to ::lassoConfig,
    "elasticnet_regression" to ::elasticNetConfig,
    "stepwise_regression" to ::stepwiseConfig,
    "robust_regression" to ::robustConfig,
    "quantile_regression" to ::quantileConfig,
    "bayesian_ridge" to ::bayesianConfig,
    "poisson_regression" to ::poissonConfig,
    "isotonic_regression" to ::isotonicConfig,
    "lars" to ::larsConfig,
)

internal val regressionLabTopicIds: Set<String> get() = regressionLabConfigs.keys

/**
 * Frame guard, added with B9 — this widget carries seventeen topics and had none. It is interactive
 * rather than frame-based, so "does it build" means "does `evaluate` run and return finite curves at
 * every slider setting", which is what silently produced an empty canvas before.
 */
internal fun regressionLabProbe(topicId: String): Int {
    val config = (regressionLabConfigs[topicId] ?: error("no config for $topicId"))()
    val points = config.data(4)
    require(points.isNotEmpty()) { "$topicId produced no data" }
    var evaluations = 0
    val settings = mutableListOf(FloatArray(config.sliders.size) { config.sliders[it].initial })
    config.sliders.forEachIndexed { index, slider ->
        listOf(slider.range.start, slider.range.endInclusive, (slider.range.start + slider.range.endInclusive) / 2)
            .forEach { value ->
                settings += FloatArray(config.sliders.size) { if (it == index) value else config.sliders[it].initial }
            }
    }
    settings.forEach { values ->
        val result = config.evaluate(points, values)
        result.curves.forEach { curve ->
            require(curve.points.isNotEmpty()) { "$topicId drew an empty curve" }
            require(curve.points.all { it.first.isFinite() && it.second.isFinite() }) {
                "$topicId drew a non-finite point at ${values.toList()}"
            }
        }
        require(result.readouts.all { it.value.isNotBlank() }) { "$topicId has a blank readout" }
        require((result.headline ?: result.note).isNotBlank()) { "$topicId has no note" }
        config.solve?.let { solve ->
            val solved = solve(points, values)
            require(solved.size == config.sliders.size) { "$topicId solved to the wrong slider count" }
            require(solved.all { it.isFinite() }) { "$topicId solved to a non-finite slider value" }
        }
        evaluations += 1
    }
    return evaluations
}

// ── UI ───────────────────────────────────────────────────────────────────────
// Parameter-lab layout: a stage card (plot + legend), readout chips, narration, then the sliders
// and the New Data / solve buttons — pinned in thumb reach when docked.

@Composable
fun RegressionLabSection(topicId: String) {
    val config = remember(topicId) { (regressionLabConfigs[topicId] ?: ::polynomialConfig)() }
    var seed by remember(config) { mutableStateOf(4) }
    val points = remember(config, seed) { config.data(seed) }
    var values by remember(config) { mutableStateOf(FloatArray(config.sliders.size) { config.sliders[it].initial }) }
    val result = remember(points, values) { config.evaluate(points, values) }

    val dock = LocalLabDock.current
    LabIntro(config.intro)

    val controls: @Composable () -> Unit = {
        Column {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                config.sliders.forEachIndexed { index, slider ->
                    LabParamSlider(slider.label, slider.symbol, values[index], slider.range, slider.format) { raw ->
                        // Stepped sliders snap to their grid, as Material's `steps` did.
                        val v = if (slider.steps > 0) {
                            val span = slider.range.endInclusive - slider.range.start
                            val unit = span / (slider.steps + 1)
                            slider.range.start + ((raw - slider.range.start) / unit).roundToInt() * unit
                        } else {
                            raw
                        }
                        values = values.copyOf().also { it[index] = v }
                    }
                }
            }
            val solve = config.solve
            val solveLabel = config.solveLabel
            if (config.reseedable || (solve != null && solveLabel != null)) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (config.reseedable) {
                        LabButton("New Data", primary = false, modifier = Modifier.weight(1f)) { seed += 1 }
                    }
                    if (solve != null && solveLabel != null) {
                        LabButton(solveLabel, primary = true, modifier = Modifier.weight(1f)) { values = solve(points, values) }
                    }
                }
            }
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
                LabCanvas(points = points, result = result)
                if (config.legend.isNotEmpty()) LabPlotLegend(config.legend, result, Modifier.padding(top = 12.dp))
                if (dock == null) {
                    LabReadoutRow(result, Modifier.padding(top = 16.dp))
                    LabNarration(result, Modifier.padding(top = 14.dp))
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
                    controls()
                }
            }
        }
        if (dock != null) {
            LabReadoutRow(result, Modifier.padding(top = 14.dp))
            LabNarration(result, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }

    if (dock != null) {
        // Re-handed every composition: the controls close over this composition's values.
        SideEffect { dock.controls = controls }
        DisposableEffect(dock) { onDispose { dock.controls = null } }
    }
}

@Composable
private fun LabReadoutRow(result: LabResult, modifier: Modifier) {
    ReadoutChips(
        parts = result.readouts.map { it.label to it.value },
        modifier = modifier,
        accented = setOf(0),
        trailing = result.share?.let { share -> { ShareChip(share) } },
    )
}

/** A bar filled to [share] in the highlight colour, then its percentage. */
@Composable
private fun ShareChip(share: Double) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .background(SimColors.Tint, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.width(96.dp).height(6.dp).clip(CircleShape).background(SimColors.Tint)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(LabHighlight),
            )
        }
        Text("${(share * 100).roundToInt()}%", fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
private fun LabNarration(result: LabResult, modifier: Modifier) {
    val (head, tail) = if (result.headline != null) {
        result.headline to result.detail
    } else {
        LabCaptionText.split(result.note)
    }
    val key = result.highlightColor ?: if (LocalDarkTheme.current) SimColors.Active else Color(0xFFB45309)
    val headline = buildAnnotatedString {
        val at = result.highlight?.let { head.indexOf(it) } ?: -1
        if (at < 0) {
            append(head)
        } else {
            append(head.substring(0, at))
            withStyle(SpanStyle(color = key)) { append(result.highlight!!) }
            append(head.substring(at + result.highlight!!.length))
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        if (tail != null) {
            Text(
                tail,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A curve's colour as drawn: the fit takes the accent, everything else keeps its own. */
@Composable
private fun drawnColor(color: Color): Color = if (color == LabFit) MaterialTheme.colorScheme.primary else color

/** Data points: pale on dark, a mid grey on light so they don't wash out. */
@Composable
private fun labPointColor(): Color = if (LocalDarkTheme.current) SimColors.Idle else SimColors.Grey

/**
 * Legend swatches follow what the entry is on the plot: a line (dashed when its curve is) for a
 * curve's colour, a ring for highlighted points, a dot otherwise.
 */
@Composable
private fun LabPlotLegend(items: List<Pair<Color, String>>, result: LabResult, modifier: Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (color, label) ->
            val curve = result.curves.firstOrNull { it.color == color }
            val drawn = if (color == LabPointColor) labPointColor() else drawnColor(color)
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    curve != null -> Canvas(modifier = Modifier.width(16.dp).height(3.dp)) {
                        val y = size.height / 2
                        drawLine(
                            drawn,
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = size.height,
                            pathEffect = if (curve.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                        )
                    }
                    color == LabHighlight -> Box(modifier = Modifier.size(11.dp).border(2.dp, drawn, CircleShape))
                    else -> Box(modifier = Modifier.size(11.dp).background(drawn, CircleShape))
                }
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
private fun LabCanvas(points: List<LabPoint>, result: LabResult) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val panel = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)
    val accent = MaterialTheme.colorScheme.primary
    val pointColor = labPointColor()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.45f)
            .clip(RoundedCornerShape(14.dp))
            .background(panel),
    ) {
        val pad = 14.dp.toPx()
        // Curve x's are included, not just the points'. The regression topics never needed this —
        // their curves are sampled across the data's own range — but a forecast is by definition
        // drawn past the last observation, and without this the whole forecast fell off the canvas.
        val xs = points.map { it.x } + result.curves.flatMap { c -> c.points.map { it.first } }
        val xMin = (xs.minOrNull() ?: 0f) - 0.3f
        val xMax = (xs.maxOrNull() ?: 1f) + 0.3f

        // Clamp the curve's contribution to the y range: an unpenalized degree-9 fit can shoot to
        // ±1e4 between points, and letting that set the scale would flatten the data to a line.
        val dataYs = points.map { it.y }
        val dataMin = dataYs.minOrNull() ?: 0f
        val dataMax = dataYs.maxOrNull() ?: 1f
        val slack = (dataMax - dataMin).coerceAtLeast(1f) * 0.6f
        val curveYs = result.curves.flatMap { it.points.map { p -> p.second } }
            .filter { it.isFinite() && it in (dataMin - slack)..(dataMax + slack) }
        var yMin = (dataYs + curveYs).min()
        var yMax = (dataYs + curveYs).max()
        if (yMax - yMin < 1f) yMax = yMin + 1f
        val yr = yMax - yMin
        yMin -= yr * 0.08f
        yMax += yr * 0.08f

        fun xOf(x: Float): Float = pad + (x - xMin) / (xMax - xMin) * (size.width - 2 * pad)
        fun yOf(y: Float): Float = size.height - pad - (y - yMin) / (yMax - yMin) * (size.height - 2 * pad)

        for (i in 0..4) {
            val gy = pad + i * (size.height - 2 * pad) / 4
            drawLine(gridColor, Offset(pad, gy), Offset(size.width - pad, gy), strokeWidth = 1.dp.toPx())
        }

        result.curves.forEach { curve ->
            // Config widths are the old raw-pixel values (6 for a fit, 3–4 for a reference); half of
            // that in dp gives the 3dp fit and 1.5–2dp dashed references the design uses.
            val width = (curve.width / 2).dp.toPx()
            val color = if (curve.color == LabFit) accent else curve.color
            val effect = if (curve.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
            // Built as one path so dashes flow along the curve. Anything off-scale is skipped rather
            // than clipped to the edge — a clipped spike would read as a real feature of the fit.
            val path = Path()
            var open = false
            curve.points.zipWithNext().forEach { (a, b) ->
                if (!a.second.isFinite() || !b.second.isFinite()) { open = false; return@forEach }
                val ay = a.second.coerceIn(yMin, yMax)
                val by = b.second.coerceIn(yMin, yMax)
                if (ay != a.second && by != b.second) { open = false; return@forEach }
                if (!open) { path.moveTo(xOf(a.first), yOf(ay)); open = true }
                path.lineTo(xOf(b.first), yOf(by))
            }
            drawPath(path, color, style = Stroke(width = width, pathEffect = effect))
        }

        points.forEachIndexed { i, p ->
            val c = Offset(xOf(p.x), yOf(p.y))
            if (i in result.highlighted) {
                drawCircle(LabHighlight.copy(alpha = 0.55f), radius = 7.dp.toPx(), center = c)
                drawCircle(LabHighlight, radius = 8.dp.toPx(), center = c, style = Stroke(width = 2.dp.toPx()))
            } else {
                drawCircle(pointColor, radius = 3.5.dp.toPx(), center = c)
            }
        }
    }
}

private fun List<LabPoint>.xRange(): Pair<Float, Float> {
    val xs = map { it.x }
    return (xs.minOrNull() ?: 0f) to (xs.maxOrNull() ?: 1f)
}

private fun Double.fmt(): String = when {
    !isFinite() -> "∞"
    abs(this) >= 1000 -> "%.0f".format(this)
    abs(this) >= 10 -> "%.1f".format(this)
    abs(this) >= 0.01 || this == 0.0 -> "%.3f".format(this)
    else -> "%.1e".format(this)
}

private fun Float.round2(): String = ((this * 100).roundToInt() / 100f).toString()
