package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// ── Estimators behind the regression lab ─────────────────────────────────────
// Every fit shown on the Regression topics is computed here by the real algorithm — no pre-baked
// curves. Everything is small (n ≈ 40, at most 10 basis terms) so a dense solve per recomposition
// costs nothing, and keeping the math in one file keeps the Compose section readable.

internal data class LabPoint(val x: Float, val y: Float)

// Polynomial design matrix with the intercept in column 0. x is rescaled to [-1,1] first: raw x up
// to 9 gives x^9 ≈ 4e8 in the same matrix as a 1, and the normal equations lose all precision.
internal fun designMatrix(xs: List<Float>, degree: Int, xMin: Float, xMax: Float): Array<DoubleArray> {
    val span = max(1e-6f, xMax - xMin)
    return Array(xs.size) { i ->
        val t = (2f * (xs[i] - xMin) / span - 1f).toDouble()
        DoubleArray(degree + 1) { p -> if (p == 0) 1.0 else Math.pow(t, p.toDouble()) }
    }
}

internal fun scaleX(x: Float, xMin: Float, xMax: Float): Double {
    val span = max(1e-6f, xMax - xMin)
    return (2f * (x - xMin) / span - 1f).toDouble()
}

internal fun polyValue(coefficients: DoubleArray, t: Double): Double {
    var sum = 0.0
    var power = 1.0
    for (c in coefficients) {
        sum += c * power
        power *= t
    }
    return sum
}

// Solve (XᵀX + λI)β = Xᵀy by Gaussian elimination with partial pivoting. λ = 0 is ordinary least
// squares; the intercept is deliberately left unpenalized, as every real implementation does.
internal fun ridgeSolve(
    x: Array<DoubleArray>,
    y: DoubleArray,
    lambda: Double,
    penalizeIntercept: Boolean = false,
): DoubleArray {
    val p = if (x.isEmpty()) 0 else x[0].size
    if (p == 0) return DoubleArray(0)
    val a = Array(p) { DoubleArray(p + 1) }

    for (i in 0 until p) {
        for (j in 0 until p) {
            var sum = 0.0
            for (r in x.indices) sum += x[r][i] * x[r][j]
            a[i][j] = sum
        }
        if (lambda > 0.0 && (penalizeIntercept || i > 0)) a[i][i] += lambda
        var rhs = 0.0
        for (r in x.indices) rhs += x[r][i] * y[r]
        a[i][p] = rhs
    }

    for (col in 0 until p) {
        var pivot = col
        for (r in col + 1 until p) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
        val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
        if (abs(a[col][col]) < 1e-12) { a[col][col] = 1e-12 }
        for (r in 0 until p) {
            if (r == col) continue
            val f = a[r][col] / a[col][col]
            if (f == 0.0) continue
            for (c in col..p) a[r][c] -= f * a[col][c]
        }
    }
    return DoubleArray(p) { a[it][p] / a[it][it] }
}

// Coordinate descent with soft-thresholding — the standard lasso/elastic-net solver. l1Ratio = 1 is
// pure lasso, 0 is pure ridge. Columns are assumed already on a comparable scale (the polynomial
// basis above is), so no extra standardization step is needed.
internal fun coordinateDescent(
    x: Array<DoubleArray>,
    y: DoubleArray,
    lambda: Double,
    l1Ratio: Double,
    iterations: Int = 300,
): DoubleArray {
    val n = x.size
    val p = if (n == 0) 0 else x[0].size
    if (p == 0) return DoubleArray(0)
    val beta = DoubleArray(p)
    val norms = DoubleArray(p) { j ->
        var s = 0.0
        for (r in 0 until n) s += x[r][j] * x[r][j]
        if (s < 1e-12) 1e-12 else s
    }
    val residual = DoubleArray(n) { y[it] }

    val l1 = lambda * l1Ratio * n
    val l2 = lambda * (1.0 - l1Ratio) * n

    repeat(iterations) {
        for (j in 0 until p) {
            // Add this coordinate's current contribution back into the residual.
            var rho = 0.0
            for (r in 0 until n) {
                residual[r] += beta[j] * x[r][j]
                rho += x[r][j] * residual[r]
            }
            val updated = if (j == 0) {
                rho / norms[j] // intercept: unpenalized
            } else {
                softThreshold(rho, l1) / (norms[j] + l2)
            }
            beta[j] = updated
            for (r in 0 until n) residual[r] -= beta[j] * x[r][j]
        }
    }
    return beta
}

private fun softThreshold(value: Double, threshold: Double): Double = when {
    value > threshold -> value - threshold
    value < -threshold -> value + threshold
    else -> 0.0
}

internal fun mse(points: List<LabPoint>, predict: (Float) -> Double): Double {
    if (points.isEmpty()) return 0.0
    var sum = 0.0
    points.forEach { val e = predict(it.x) - it.y; sum += e * e }
    return sum / points.size
}

// ── RANSAC ───────────────────────────────────────────────────────────────────
internal class RansacFit(val slope: Double, val intercept: Double, val inliers: Set<Int>)

internal fun ransac(points: List<LabPoint>, threshold: Double, trials: Int = 200, seed: Int = 3): RansacFit {
    if (points.size < 2) return RansacFit(0.0, 0.0, emptySet())
    val random = Random(seed)
    var best = RansacFit(0.0, 0.0, emptySet())

    repeat(trials) {
        val i = random.nextInt(points.size)
        var j = random.nextInt(points.size)
        if (j == i) j = (j + 1) % points.size
        val (x1, y1) = points[i]
        val (x2, y2) = points[j]
        if (abs(x2 - x1) < 1e-6f) return@repeat
        val m = ((y2 - y1) / (x2 - x1)).toDouble()
        val c = y1 - m * x1
        val inliers = points.indices.filter { abs(m * points[it].x + c - points[it].y) <= threshold }.toSet()
        if (inliers.size > best.inliers.size) best = RansacFit(m, c, inliers)
    }

    // Refit on the consensus set — the sampled pair only nominates the inliers, it is not the answer.
    if (best.inliers.size >= 2) {
        val subset = best.inliers.map { points[it] }
        val (m, c) = ordinaryLeastSquares(subset)
        return RansacFit(m, c, best.inliers)
    }
    return best
}

internal fun ordinaryLeastSquares(points: List<LabPoint>): Pair<Double, Double> {
    val n = points.size
    if (n == 0) return 0.0 to 0.0
    var sx = 0.0; var sy = 0.0; var sxy = 0.0; var sxx = 0.0
    points.forEach { (x, y) -> sx += x; sy += y; sxy += x * y; sxx += x.toDouble() * x }
    val denom = n * sxx - sx * sx
    if (abs(denom) < 1e-12) return 0.0 to (sy / n)
    val m = (n * sxy - sx * sy) / denom
    return m to (sy - m * sx) / n
}

// ── Quantile regression: pinball loss, minimized by subgradient descent ──────
internal fun quantileFit(points: List<LabPoint>, tau: Double, steps: Int = 4000): Pair<Double, Double> {
    if (points.isEmpty()) return 0.0 to 0.0
    var (m, c) = ordinaryLeastSquares(points) // start from the mean fit
    var lr = 0.05
    repeat(steps) {
        var gm = 0.0
        var gc = 0.0
        points.forEach { p ->
            val residual = p.y - (m * p.x + c)
            // Subgradient of the pinball loss: tau above the line, tau-1 below it.
            val g = if (residual >= 0) -tau else (1.0 - tau)
            gm += g * p.x
            gc += g
        }
        m -= lr * gm / points.size
        c -= lr * gc / points.size
        lr *= 0.9995
    }
    return m to c
}

internal fun pinballLoss(points: List<LabPoint>, tau: Double, m: Double, c: Double): Double {
    if (points.isEmpty()) return 0.0
    var sum = 0.0
    points.forEach { p ->
        val r = p.y - (m * p.x + c)
        sum += if (r >= 0) tau * r else (tau - 1.0) * r
    }
    return sum / points.size
}

// ── Bayesian ridge: posterior over coefficients, and predictive variance ─────
internal class BayesianFit(
    val mean: DoubleArray,
    val covariance: Array<DoubleArray>,
    val noiseVariance: Double,
)

// Posterior for w ~ N(0, alpha⁻¹I), y ~ N(Xw, beta⁻¹): S = (alpha·I + beta·XᵀX)⁻¹, mu = beta·S·Xᵀy.
internal fun bayesianRidge(
    x: Array<DoubleArray>,
    y: DoubleArray,
    alpha: Double,
    noiseVariance: Double,
): BayesianFit {
    val p = if (x.isEmpty()) 0 else x[0].size
    if (p == 0) return BayesianFit(DoubleArray(0), emptyArray(), noiseVariance)
    val beta = 1.0 / max(1e-6, noiseVariance)

    val precision = Array(p) { i ->
        DoubleArray(p) { j ->
            var s = 0.0
            for (r in x.indices) s += x[r][i] * x[r][j]
            beta * s + if (i == j) alpha else 0.0
        }
    }
    val covariance = invert(precision)
    val xty = DoubleArray(p) { i ->
        var s = 0.0
        for (r in x.indices) s += x[r][i] * y[r]
        s
    }
    val mean = DoubleArray(p) { i ->
        var s = 0.0
        for (j in 0 until p) s += covariance[i][j] * xty[j]
        beta * s
    }
    return BayesianFit(mean, covariance, noiseVariance)
}

// Predictive std at a point: sqrt(noise + phiᵀ S phi). The second term is what grows where data is
// sparse — the whole reason to bother with the posterior.
internal fun predictiveStd(fit: BayesianFit, phi: DoubleArray): Double {
    if (fit.covariance.isEmpty()) return sqrt(fit.noiseVariance)
    var quad = 0.0
    for (i in phi.indices) {
        for (j in phi.indices) quad += phi[i] * fit.covariance[i][j] * phi[j]
    }
    return sqrt(max(0.0, fit.noiseVariance + quad))
}

private fun invert(matrix: Array<DoubleArray>): Array<DoubleArray> {
    val n = matrix.size
    val a = Array(n) { r -> DoubleArray(2 * n) { c -> if (c < n) matrix[r][c] else if (c - n == r) 1.0 else 0.0 } }
    for (col in 0 until n) {
        var pivot = col
        for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
        val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
        if (abs(a[col][col]) < 1e-12) a[col][col] = 1e-12
        val d = a[col][col]
        for (c in 0 until 2 * n) a[col][c] /= d
        for (r in 0 until n) {
            if (r == col) continue
            val f = a[r][col]
            if (f == 0.0) continue
            for (c in 0 until 2 * n) a[r][c] -= f * a[col][c]
        }
    }
    return Array(n) { r -> DoubleArray(n) { c -> a[r][c + n] } }
}

// ── Poisson regression by IRLS ───────────────────────────────────────────────
internal fun poissonIrls(points: List<LabPoint>, iterations: Int = 40): Pair<Double, Double> {
    var b0 = ln(max(0.5, points.map { it.y }.average()))
    var b1 = 0.0
    repeat(iterations) {
        // Weighted least squares on the working response, weights = the fitted mean.
        var sw = 0.0; var swx = 0.0; var swxx = 0.0; var swz = 0.0; var swxz = 0.0
        points.forEach { p ->
            val eta = b0 + b1 * p.x
            val mu = exp(eta.coerceIn(-20.0, 20.0))
            val w = max(1e-6, mu)
            val z = eta + (p.y - mu) / w
            sw += w; swx += w * p.x; swxx += w * p.x * p.x; swz += w * z; swxz += w * p.x * z
        }
        val denom = sw * swxx - swx * swx
        if (abs(denom) < 1e-12) return@repeat
        b1 = (sw * swxz - swx * swz) / denom
        b0 = (swz - b1 * swx) / sw
    }
    return b0 to b1
}

internal fun poissonDeviance(points: List<LabPoint>, b0: Double, b1: Double): Double {
    var sum = 0.0
    points.forEach { p ->
        val mu = max(1e-9, exp((b0 + b1 * p.x).coerceIn(-20.0, 20.0)))
        val y = p.y.toDouble()
        sum += 2.0 * ((if (y > 0) y * ln(y / mu) else 0.0) - (y - mu))
    }
    return sum / max(1, points.size)
}

// ── Isotonic regression: pool adjacent violators ─────────────────────────────
internal class IsotonicFit(val xs: List<Float>, val ys: List<Double>, val blocks: Int)

internal fun pava(points: List<LabPoint>): IsotonicFit {
    if (points.isEmpty()) return IsotonicFit(emptyList(), emptyList(), 0)
    val sorted = points.sortedBy { it.x }
    val values = ArrayDeque<Double>()
    val weights = ArrayDeque<Int>()

    sorted.forEach { p ->
        var value = p.y.toDouble()
        var weight = 1
        // Merge backwards while the previous block is higher — the monotonicity violation.
        while (values.isNotEmpty() && values.last() > value) {
            val v = values.removeLast()
            val w = weights.removeLast()
            value = (value * weight + v * w) / (weight + w)
            weight += w
        }
        values.addLast(value)
        weights.addLast(weight)
    }

    val out = ArrayList<Double>(sorted.size)
    values.forEachIndexed { i, v -> repeat(weights.elementAt(i)) { out.add(v) } }
    return IsotonicFit(sorted.map { it.x }, out, values.size)
}

// ── LARS: the equiangular coefficient path ───────────────────────────────────
internal class LarsStep(
    val beta: DoubleArray,
    val active: List<Int>,
    val maxCorrelation: Double,
    val entered: Int?,
)

// Least Angle Regression, in the plain (non-lasso-modified) form. Each step moves along the
// equiangular direction until a new predictor ties the active set's correlation with the residual.
internal fun larsPath(x: Array<DoubleArray>, y: DoubleArray, maxSteps: Int): List<LarsStep> {
    val n = x.size
    val p = if (n == 0) 0 else x[0].size
    if (p <= 1) return emptyList()

    val mean = y.average()
    val residual = DoubleArray(n) { y[it] - mean }
    val beta = DoubleArray(p)
    beta[0] = mean // column 0 is the intercept and sits outside the path
    val active = mutableListOf<Int>()
    val steps = mutableListOf<LarsStep>()

    fun correlation(j: Int): Double {
        var s = 0.0
        for (r in 0 until n) s += x[r][j] * residual[r]
        return s
    }

    repeat(minOf(maxSteps, p - 1)) {
        val candidate = (1 until p).filter { it !in active }
            .maxByOrNull { abs(correlation(it)) } ?: return@repeat
        active.add(candidate)

        // Direction that keeps correlations of all active predictors equal: least squares of the
        // residual on the active columns only.
        val sub = Array(n) { r -> DoubleArray(active.size) { k -> x[r][active[k]] } }
        val direction = ridgeSolve(sub, residual, 0.0, penalizeIntercept = true)

        // Step partway, not all the way — going the full distance is plain forward selection.
        val gamma = 0.5
        active.forEachIndexed { k, j -> beta[j] += gamma * direction[k] }
        for (r in 0 until n) {
            var delta = 0.0
            direction.forEachIndexed { k, d -> delta += gamma * d * x[r][active[k]] }
            residual[r] -= delta
        }

        steps.add(
            LarsStep(
                beta = beta.copyOf(),
                active = active.toList(),
                maxCorrelation = (1 until p).maxOfOrNull { abs(correlation(it)) } ?: 0.0,
                entered = candidate,
            ),
        )
    }
    return steps
}

// ── Forward stepwise selection ───────────────────────────────────────────────
internal class StepwiseResult(val beta: DoubleArray, val selected: List<Int>, val adjustedR2: Double)

internal fun forwardStepwise(x: Array<DoubleArray>, y: DoubleArray, terms: Int): StepwiseResult {
    val n = x.size
    val p = if (n == 0) 0 else x[0].size
    val selected = mutableListOf(0) // intercept always in
    val mean = y.average()
    var totalSs = 0.0
    y.forEach { totalSs += (it - mean) * (it - mean) }

    repeat(minOf(terms, p - 1)) {
        var bestJ = -1
        var bestRss = Double.MAX_VALUE
        for (j in 1 until p) {
            if (j in selected) continue
            val trial = selected + j
            val rss = rssFor(x, y, trial)
            if (rss < bestRss) { bestRss = rss; bestJ = j }
        }
        if (bestJ >= 0) selected.add(bestJ)
    }

    val beta = DoubleArray(p)
    val sub = Array(n) { r -> DoubleArray(selected.size) { k -> x[r][selected[k]] } }
    val fitted = ridgeSolve(sub, y, 0.0, penalizeIntercept = true)
    selected.forEachIndexed { k, j -> beta[j] = fitted[k] }

    val rss = rssFor(x, y, selected)
    val k = selected.size - 1
    val adjusted = if (n - k - 1 > 0 && totalSs > 0) {
        1.0 - (rss / (n - k - 1)) / (totalSs / (n - 1))
    } else 0.0
    return StepwiseResult(beta, selected.drop(1).sorted(), adjusted)
}

private fun rssFor(x: Array<DoubleArray>, y: DoubleArray, columns: List<Int>): Double {
    val n = x.size
    val sub = Array(n) { r -> DoubleArray(columns.size) { k -> x[r][columns[k]] } }
    val beta = ridgeSolve(sub, y, 0.0, penalizeIntercept = true)
    var rss = 0.0
    for (r in 0 until n) {
        var pred = 0.0
        for (k in columns.indices) pred += beta[k] * sub[r][k]
        val e = y[r] - pred
        rss += e * e
    }
    return rss
}

internal fun clampF(value: Float, lo: Float, hi: Float) = min(hi, max(lo, value))
