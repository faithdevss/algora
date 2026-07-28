package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ── Classifiers behind the decision-surface lab ──────────────────────────────
// Every boundary drawn on the Classification topics is produced by the real algorithm here — a
// working SMO for the SVM dual, closed-form discriminant analysis, and the actual online update for
// Passive-Aggressive. Datasets are ~80 points in 2D, so an O(n²) kernel matrix is free.

internal class ClassPoint(val x: Float, val y: Float, val label: Int) // label is -1 or +1

// ── Kernels ──────────────────────────────────────────────────────────────────

internal fun interface Kernel {
    fun compute(ax: Float, ay: Float, bx: Float, by: Float): Double
}

internal fun linearKernel() = Kernel { ax, ay, bx, by -> (ax * bx + ay * by).toDouble() }

internal fun rbfKernel(gamma: Double) = Kernel { ax, ay, bx, by ->
    val dx = (ax - bx).toDouble()
    val dy = (ay - by).toDouble()
    exp(-gamma * (dx * dx + dy * dy))
}

// ── Simplified SMO for the C-SVM dual ────────────────────────────────────────
// Platt's SMO, in the simplified form: pick one alpha violating the KKT conditions, pick a second at
// random, and jointly optimize the pair in closed form. Slower to converge than the full heuristic
// pair selection, but it is the genuine dual problem and it produces genuinely sparse support
// vectors, which is the thing these topics need to show.

internal class SvmFit(
    val alphas: DoubleArray,
    val bias: Double,
    val points: List<ClassPoint>,
    val kernel: Kernel,
) {
    val supportVectors: Set<Int> = alphas.indices.filter { alphas[it] > 1e-5 }.toSet()

    fun decision(x: Float, y: Float): Double {
        var sum = 0.0
        for (i in points.indices) {
            if (alphas[i] <= 1e-8) continue
            sum += alphas[i] * points[i].label * kernel.compute(points[i].x, points[i].y, x, y)
        }
        return sum + bias
    }

    // Points inside the margin or misclassified — the ones a nu-SVM bounds from above.
    fun marginErrors(): Set<Int> =
        points.indices.filter { points[it].label * decision(points[it].x, points[it].y) < 1.0 - 1e-6 }.toSet()

    fun misclassified(): Set<Int> =
        points.indices.filter { points[it].label * decision(points[it].x, points[it].y) <= 0.0 }.toSet()
}

internal fun trainSvm(
    points: List<ClassPoint>,
    kernel: Kernel,
    c: Double,
    passes: Int = 12,
    tolerance: Double = 1e-3,
    seed: Int = 7,
): SvmFit {
    val n = points.size
    val alphas = DoubleArray(n)
    var bias = 0.0
    if (n == 0) return SvmFit(alphas, bias, points, kernel)
    val random = Random(seed)

    // Cache the kernel matrix — n is small and this is evaluated repeatedly.
    val k = Array(n) { i -> DoubleArray(n) { j -> kernel.compute(points[i].x, points[i].y, points[j].x, points[j].y) } }

    fun f(i: Int): Double {
        var sum = bias
        for (j in 0 until n) if (alphas[j] > 1e-12) sum += alphas[j] * points[j].label * k[j][i]
        return sum
    }

    var passesWithoutChange = 0
    var iterations = 0
    while (passesWithoutChange < passes && iterations < 4000) {
        iterations++
        var changed = 0
        for (i in 0 until n) {
            val yi = points[i].label.toDouble()
            val ei = f(i) - yi
            // KKT violation: alpha below C but the point is inside the margin, or alpha above 0 but
            // the point is outside it.
            val violates = (yi * ei < -tolerance && alphas[i] < c) || (yi * ei > tolerance && alphas[i] > 0)
            if (!violates) continue

            var j = random.nextInt(n)
            if (j == i) j = (j + 1) % n
            val yj = points[j].label.toDouble()
            val ej = f(j) - yj

            val oldI = alphas[i]
            val oldJ = alphas[j]

            // The pair must stay on the line y_i·a_i + y_j·a_j = const, inside the box [0,C]².
            val (low, high) = if (points[i].label != points[j].label) {
                max(0.0, oldJ - oldI) to min(c, c + oldJ - oldI)
            } else {
                max(0.0, oldI + oldJ - c) to min(c, oldI + oldJ)
            }
            if (high - low < 1e-12) continue

            val eta = 2.0 * k[i][j] - k[i][i] - k[j][j]
            if (eta >= -1e-12) continue // non-positive curvature: skip rather than guess

            var newJ = oldJ - yj * (ei - ej) / eta
            newJ = newJ.coerceIn(low, high)
            if (abs(newJ - oldJ) < 1e-7) continue
            val newI = oldI + points[i].label * points[j].label * (oldJ - newJ)

            alphas[i] = newI
            alphas[j] = newJ

            val b1 = bias - ei - yi * (newI - oldI) * k[i][i] - yj * (newJ - oldJ) * k[i][j]
            val b2 = bias - ej - yi * (newI - oldI) * k[i][j] - yj * (newJ - oldJ) * k[j][j]
            bias = when {
                newI > 0 && newI < c -> b1
                newJ > 0 && newJ < c -> b2
                else -> (b1 + b2) / 2.0
            }
            changed++
        }
        passesWithoutChange = if (changed == 0) passesWithoutChange + 1 else 0
    }
    return SvmFit(alphas, bias, points, kernel)
}

// nu-SVC and C-SVC solve equivalent problems under a correspondence between nu and C. Rather than
// implement a second QP, search C for the value whose support-vector fraction best matches the
// requested nu — then the lab can report the achieved fractions and let the nu bound be checked
// rather than asserted.
internal class NuFit(val fit: SvmFit, val c: Double, val svFraction: Double, val marginErrorFraction: Double)

internal fun trainNuSvc(points: List<ClassPoint>, kernel: Kernel, nu: Double): NuFit {
    var best: NuFit? = null
    // Log-spaced sweep: the nu -> C map is smooth but strongly non-linear.
    for (step in 0..22) {
        val c = Math.pow(10.0, -2.0 + step * 0.2)
        val fit = trainSvm(points, kernel, c)
        val sv = fit.supportVectors.size.toDouble() / points.size
        val err = fit.marginErrors().size.toDouble() / points.size
        val candidate = NuFit(fit, c, sv, err)
        if (best == null || abs(sv - nu) < abs(best.svFraction - nu)) best = candidate
    }
    return best ?: NuFit(trainSvm(points, kernel, 1.0), 1.0, 0.0, 0.0)
}

// ── Discriminant analysis ────────────────────────────────────────────────────

internal class Gaussian2D(
    val meanX: Double,
    val meanY: Double,
    val covariance: Array<DoubleArray>,
    val prior: Double,
) {
    private val inverse: Array<DoubleArray>
    private val logDet: Double

    init {
        val a = covariance[0][0]
        val b = covariance[0][1]
        val d = covariance[1][1]
        val det = (a * d - b * b).coerceAtLeast(1e-9)
        inverse = arrayOf(doubleArrayOf(d / det, -b / det), doubleArrayOf(-b / det, a / det))
        logDet = ln(det)
    }

    // Log of the class-conditional density times the prior, dropping the shared constant.
    fun logScore(x: Double, y: Double): Double {
        val dx = x - meanX
        val dy = y - meanY
        val quad = dx * (inverse[0][0] * dx + inverse[0][1] * dy) + dy * (inverse[1][0] * dx + inverse[1][1] * dy)
        return -0.5 * quad - 0.5 * logDet + ln(prior)
    }

    // Ellipse at a given Mahalanobis radius, as a closed polyline. Uses the closed-form 2x2
    // eigendecomposition — no iterative solver needed at this size.
    fun ellipse(radius: Double, segments: Int = 60): List<Pair<Float, Float>> {
        val a = covariance[0][0]
        val b = covariance[0][1]
        val d = covariance[1][1]
        val trace = a + d
        val det = a * d - b * b
        val disc = sqrt(max(0.0, trace * trace / 4.0 - det))
        val l1 = trace / 2.0 + disc
        val l2 = trace / 2.0 - disc
        val angle = if (abs(b) < 1e-12) 0.0 else atan2(l1 - a, b)
        val rx = radius * sqrt(max(1e-9, l1))
        val ry = radius * sqrt(max(1e-9, l2))
        return (0..segments).map { i ->
            val t = 2.0 * Math.PI * i / segments
            val px = rx * cos(t)
            val py = ry * sin(t)
            ((meanX + px * cos(angle) - py * sin(angle)).toFloat() to
                (meanY + px * sin(angle) + py * cos(angle)).toFloat())
        }
    }
}

internal class DiscriminantFit(val negative: Gaussian2D, val positive: Gaussian2D) {
    fun decision(x: Float, y: Float): Double =
        positive.logScore(x.toDouble(), y.toDouble()) - negative.logScore(x.toDouble(), y.toDouble())
}

private fun covarianceOf(points: List<ClassPoint>, meanX: Double, meanY: Double, shrink: Double = 0.0): Array<DoubleArray> {
    var sxx = 0.0; var sxy = 0.0; var syy = 0.0
    points.forEach { p ->
        val dx = p.x - meanX
        val dy = p.y - meanY
        sxx += dx * dx; sxy += dx * dy; syy += dy * dy
    }
    val n = max(1, points.size - 1).toDouble()
    val cxx = sxx / n
    val cxy = sxy / n
    val cyy = syy / n
    // Shrinkage toward a scaled identity — the standard fix when a class has too few points to
    // estimate a full covariance, and what regularized discriminant analysis dials.
    val meanVar = (cxx + cyy) / 2.0
    return arrayOf(
        doubleArrayOf((1 - shrink) * cxx + shrink * meanVar, (1 - shrink) * cxy),
        doubleArrayOf((1 - shrink) * cxy, (1 - shrink) * cyy + shrink * meanVar),
    )
}

// LDA: one pooled covariance shared by both classes, which is what makes the boundary linear —
// the quadratic terms cancel when the two log-densities are subtracted.
internal fun fitLda(points: List<ClassPoint>, shrink: Double = 0.0): DiscriminantFit {
    val neg = points.filter { it.label < 0 }
    val pos = points.filter { it.label > 0 }
    val negMean = neg.map { it.x.toDouble() }.averageOr(0.0) to neg.map { it.y.toDouble() }.averageOr(0.0)
    val posMean = pos.map { it.x.toDouble() }.averageOr(0.0) to pos.map { it.y.toDouble() }.averageOr(0.0)

    val negCov = covarianceOf(neg, negMean.first, negMean.second)
    val posCov = covarianceOf(pos, posMean.first, posMean.second)
    val wn = max(1, neg.size - 1).toDouble()
    val wp = max(1, pos.size - 1).toDouble()
    val total = wn + wp
    val pooledRaw = Array(2) { i -> DoubleArray(2) { j -> (wn * negCov[i][j] + wp * posCov[i][j]) / total } }
    val meanVar = (pooledRaw[0][0] + pooledRaw[1][1]) / 2.0
    val pooled = Array(2) { i ->
        DoubleArray(2) { j ->
            (1 - shrink) * pooledRaw[i][j] + if (i == j) shrink * meanVar else 0.0
        }
    }

    return DiscriminantFit(
        Gaussian2D(negMean.first, negMean.second, pooled, neg.size.toDouble() / points.size),
        Gaussian2D(posMean.first, posMean.second, pooled, pos.size.toDouble() / points.size),
    )
}

// QDA: each class keeps its own covariance, so the quadratic terms survive the subtraction and the
// boundary becomes a conic — ellipse, parabola or hyperbola depending on the two shapes.
internal fun fitQda(points: List<ClassPoint>, shrink: Double = 0.0): DiscriminantFit {
    val neg = points.filter { it.label < 0 }
    val pos = points.filter { it.label > 0 }
    val negMean = neg.map { it.x.toDouble() }.averageOr(0.0) to neg.map { it.y.toDouble() }.averageOr(0.0)
    val posMean = pos.map { it.x.toDouble() }.averageOr(0.0) to pos.map { it.y.toDouble() }.averageOr(0.0)
    return DiscriminantFit(
        Gaussian2D(negMean.first, negMean.second, covarianceOf(neg, negMean.first, negMean.second, shrink), neg.size.toDouble() / points.size),
        Gaussian2D(posMean.first, posMean.second, covarianceOf(pos, posMean.first, posMean.second, shrink), pos.size.toDouble() / points.size),
    )
}

// Gaussian naive Bayes is QDA with the off-diagonal covariance terms forced to zero: features are
// assumed conditionally independent given the class, which is exactly what a diagonal covariance
// says. Keeping it in the same DiscriminantFit type makes the three directly comparable.
internal fun fitGaussianNb(points: List<ClassPoint>, smoothing: Double = 0.0): DiscriminantFit {
    val full = fitQda(points)
    fun diagonalize(g: Gaussian2D): Gaussian2D {
        val varX = g.covariance[0][0] + smoothing
        val varY = g.covariance[1][1] + smoothing
        return Gaussian2D(
            g.meanX,
            g.meanY,
            arrayOf(doubleArrayOf(varX, 0.0), doubleArrayOf(0.0, varY)),
            g.prior,
        )
    }
    return DiscriminantFit(diagonalize(full.negative), diagonalize(full.positive))
}

private fun List<Double>.averageOr(fallback: Double) = if (isEmpty()) fallback else average()

// ── Passive-Aggressive: the online update, step by step ──────────────────────

internal class PaState(
    val w1: Double,
    val w2: Double,
    val bias: Double,
    val updates: Int,
    val mistakes: Int,
    val lastLoss: Double,
    val lastTau: Double,
    val lastIndex: Int,
)

internal enum class PaVariant { HARD, PA_I, PA_II }

// One pass over the stream, snapshotting after every example so a slider can scrub the history.
internal fun passiveAggressivePath(
    points: List<ClassPoint>,
    variant: PaVariant,
    aggressiveness: Double,
): List<PaState> {
    var w1 = 0.0
    var w2 = 0.0
    var bias = 0.0
    var updates = 0
    var mistakes = 0
    val states = mutableListOf(PaState(0.0, 0.0, 0.0, 0, 0, 0.0, 0.0, -1))

    points.forEachIndexed { index, p ->
        val margin = w1 * p.x + w2 * p.y + bias
        val loss = max(0.0, 1.0 - p.label * margin) // hinge
        if (p.label * margin <= 0) mistakes++

        var tau = 0.0
        if (loss > 0) {
            val normSquared = (p.x * p.x + p.y * p.y + 1.0) // +1 for the bias feature
            tau = when (variant) {
                // Passive when the loss is zero; aggressive enough to fix the example exactly when
                // it is not. PA-I caps the step, PA-II softens it — both bound the damage a single
                // mislabelled example can do.
                PaVariant.HARD -> loss / normSquared
                PaVariant.PA_I -> min(aggressiveness, loss / normSquared)
                PaVariant.PA_II -> loss / (normSquared + 1.0 / (2.0 * aggressiveness))
            }
            w1 += tau * p.label * p.x
            w2 += tau * p.label * p.y
            bias += tau * p.label
            updates++
        }
        states.add(PaState(w1, w2, bias, updates, mistakes, loss, tau, index))
    }
    return states
}

// ── Shared datasets ──────────────────────────────────────────────────────────

internal fun ringData(seed: Int, n: Int = 90): List<ClassPoint> {
    val random = Random(seed)
    return (0 until n).map { i ->
        val inner = i % 2 == 0
        val radius = if (inner) 0.9f + random.nextFloat() * 0.7f else 2.5f + random.nextFloat() * 0.8f
        val angle = random.nextFloat() * 2f * Math.PI.toFloat()
        ClassPoint(radius * cos(angle.toDouble()).toFloat(), radius * sin(angle.toDouble()).toFloat(), if (inner) 1 else -1)
    }
}

internal fun overlappingBlobs(seed: Int, n: Int = 80, separation: Float = 1.7f): List<ClassPoint> {
    val random = Random(seed)
    return (0 until n).map { i ->
        val positive = i % 2 == 0
        val cx = if (positive) separation else -separation
        ClassPoint(
            cx + (random.nextFloat() + random.nextFloat() - 1f) * 1.6f,
            (random.nextFloat() + random.nextFloat() - 1f) * 1.6f,
            if (positive) 1 else -1,
        )
    }
}

// Two classes with genuinely different covariance shapes: one long and tilted, one compact. LDA is
// forced to average them into a single pooled shape; QDA is not.
internal fun unequalCovarianceBlobs(seed: Int, n: Int = 80): List<ClassPoint> {
    val random = Random(seed)
    return (0 until n).map { i ->
        val positive = i % 2 == 0
        val u = random.nextFloat() + random.nextFloat() - 1f
        val v = random.nextFloat() + random.nextFloat() - 1f
        if (positive) {
            // Wide, rotated 45°.
            val a = u * 2.6f
            val b = v * 0.5f
            ClassPoint(1.1f + (a - b) * 0.707f, 1.1f + (a + b) * 0.707f, 1)
        } else {
            ClassPoint(-1.0f + u * 1.0f, -0.9f + v * 1.0f, -1)
        }
    }
}

internal fun streamData(seed: Int, n: Int = 60): List<ClassPoint> {
    val random = Random(seed)
    return (0 until n).map { i ->
        val positive = i % 2 == 0
        // One deliberately mislabelled example late in the stream, to show what an unbounded step
        // size would do to a boundary that was already correct.
        val flip = i == 44
        val cx = if (positive) 1.6f else -1.6f
        ClassPoint(
            cx + (random.nextFloat() - 0.5f) * 1.8f,
            (random.nextFloat() - 0.5f) * 3f,
            if (positive != flip) 1 else -1,
        )
    }
}
