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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.AlgoraCodeStyle
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

// ── Regression lab ───────────────────────────────────────────────────────────
// The mock's regression lab (RegressionSimulationSection) is a hand-tuned port for Linear Regression
// and is left alone. This is the same chrome — canvas, readout row, sliders, action button — driven
// by a per-topic config, so each of the eleven Regression topics gets its own estimator, its own
// dataset shape, and its own thing to notice. Every curve here is computed by the real algorithm in
// RegressionLabMath.kt.

private val LabFit = Color(0xFF4F46E5)
private val LabPointColor = Color(0xFF7C3AED)
private val LabReference = Color(0xFF94A3B8)
private val LabHighlight = Color(0xFFEF4444)
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
    val readouts: List<LabReadout>,
    val note: String,
    // Points drawn in the highlight colour — outliers for RANSAC, held-out points elsewhere.
    val highlighted: Set<Int> = emptySet(),
)

private class LabSlider(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val initial: Float,
    val steps: Int = 0,
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

private val regressionLabConfigs: Map<String, () -> LabConfig> = mapOf(
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

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun RegressionLabSection(topicId: String) {
    val config = remember(topicId) { (regressionLabConfigs[topicId] ?: ::polynomialConfig)() }
    var seed by remember(config) { mutableStateOf(4) }
    val points = remember(config, seed) { config.data(seed) }
    var values by remember(config) { mutableStateOf(FloatArray(config.sliders.size) { config.sliders[it].initial }) }
    val result = remember(points, values) { config.evaluate(points, values) }

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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                LabCanvas(points = points, result = result)
            }

            if (config.legend.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    config.legend.forEach { (color, label) -> LabLegend(color, label) }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
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
                buttons = buildList {
                    add(Triple("↻ New Data", SimColors.Grey) { seed += 1 })
                    val solve = config.solve
                    val label = config.solveLabel
                    if (solve != null && label != null) {
                        add(Triple(label, SimColors.Green) { values = solve(points, values) })
                    }
                },
            )
        }
    }
}

@Composable
private fun LabLegend(color: Color, label: String) {
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

@Composable
private fun LabCanvas(points: List<LabPoint>, result: LabResult) {
    val gridColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = Modifier.fillMaxWidth().height(210.dp)) {
        val pad = 14f
        val xs = points.map { it.x }
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
        yMin -= yr * 0.12f
        yMax += yr * 0.12f

        fun xOf(x: Float): Float = pad + (x - xMin) / (xMax - xMin) * (size.width - 2 * pad)
        fun yOf(y: Float): Float = size.height - pad - (y - yMin) / (yMax - yMin) * (size.height - 2 * pad)

        for (i in 0..4) {
            val gy = pad + i * (size.height - 2 * pad) / 4
            drawLine(gridColor, Offset(pad, gy), Offset(size.width - pad, gy), strokeWidth = 1f)
        }

        result.curves.forEach { curve ->
            val effect = if (curve.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null
            // Drawn segment by segment, with anything off-scale skipped rather than clipped to the
            // edge — a clipped spike would read as a real feature of the fit.
            curve.points.zipWithNext().forEach { (a, b) ->
                if (!a.second.isFinite() || !b.second.isFinite()) return@forEach
                val ay = a.second.coerceIn(yMin, yMax)
                val by = b.second.coerceIn(yMin, yMax)
                if (ay != a.second && by != b.second) return@forEach
                drawLine(
                    color = curve.color,
                    start = Offset(xOf(a.first), yOf(ay)),
                    end = Offset(xOf(b.first), yOf(by)),
                    strokeWidth = curve.width,
                    pathEffect = effect,
                )
            }
        }

        points.forEachIndexed { i, p ->
            val highlighted = i in result.highlighted
            drawCircle(
                color = if (highlighted) LabHighlight else LabPointColor,
                radius = if (highlighted) 8f else 7f,
                center = Offset(xOf(p.x), yOf(p.y)),
                alpha = 0.9f,
            )
            if (highlighted) {
                drawCircle(
                    color = LabHighlight,
                    radius = 12f,
                    center = Offset(xOf(p.x), yOf(p.y)),
                    style = Stroke(width = 2f),
                )
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
