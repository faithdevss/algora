package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ── Dimensionality reduction ─────────────────────────────────────────────────
// The estimators behind the eight Dimensionality Reduction labs (phase 9, batch B6). Everything
// here is the real algorithm rather than a scripted animation: kernel PCA eigendecomposes an actual
// centered Gram matrix, t-SNE runs a perplexity binary search and gradient descent on the KL
// divergence, FastICA runs the tanh fixed-point iteration after whitening, LLE solves the
// constrained reconstruction weights and takes the bottom eigenvectors of (I−W)ᵀ(I−W).
//
// The reason for the discipline is the phase's recurring lesson: every claim a frame makes about
// what the method achieved is computed from these functions, so it cannot drift from what the code
// did. `DimReductionMathTest` pins the properties the copy leans on.
//
// Nothing that forms an n×n matrix is given more than ~50 points, which is what makes a dense
// O(n³) Jacobi eigensolver the right choice — it is short, needs no pivoting strategy, and returns
// every eigenvector at once, which both kernel PCA (top of the spectrum) and LLE (bottom of the
// spectrum) need. The two datasets that are larger (the ICA mixtures, the factor-analysis samples)
// only ever go through 2×2 and 3×3 decompositions.

// ── Linear algebra ───────────────────────────────────────────────────────────

internal class EigenResult(
    /** Eigenvalues, descending. */
    val values: DoubleArray,
    /** `vectors[k]` is the unit eigenvector for `values[k]`. */
    val vectors: List<DoubleArray>,
)

/**
 * Cyclic Jacobi eigendecomposition of a real symmetric matrix. Returns eigenpairs sorted by
 * descending eigenvalue.
 */
internal fun jacobiEigen(input: Array<DoubleArray>, maxSweeps: Int = 60): EigenResult {
    val n = input.size
    val a = Array(n) { input[it].copyOf() }
    val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

    var sweep = 0
    while (sweep < maxSweeps) {
        var off = 0.0
        for (p in 0 until n) for (q in p + 1 until n) off += a[p][q] * a[p][q]
        if (off < 1e-22) break

        for (p in 0 until n) {
            for (q in p + 1 until n) {
                val apq = a[p][q]
                if (abs(apq) < 1e-18) continue
                val theta = (a[q][q] - a[p][p]) / (2.0 * apq)
                val t = if (theta >= 0) 1.0 / (theta + sqrt(theta * theta + 1.0))
                else -1.0 / (-theta + sqrt(theta * theta + 1.0))
                val c = 1.0 / sqrt(t * t + 1.0)
                val s = t * c

                for (k in 0 until n) {
                    val akp = a[k][p]
                    val akq = a[k][q]
                    a[k][p] = c * akp - s * akq
                    a[k][q] = s * akp + c * akq
                }
                for (k in 0 until n) {
                    val apk = a[p][k]
                    val aqk = a[q][k]
                    a[p][k] = c * apk - s * aqk
                    a[q][k] = s * apk + c * aqk
                }
                for (k in 0 until n) {
                    val vkp = v[k][p]
                    val vkq = v[k][q]
                    v[k][p] = c * vkp - s * vkq
                    v[k][q] = s * vkp + c * vkq
                }
            }
        }
        sweep++
    }

    val order = (0 until n).sortedByDescending { a[it][it] }
    return EigenResult(
        values = DoubleArray(n) { a[order[it]][order[it]] },
        vectors = order.map { col -> DoubleArray(n) { row -> v[row][col] } },
    )
}

/** Covariance of a 2-column dataset, as `[[cxx, cxy], [cxy, cyy]]`, about the sample mean. */
internal fun covariance2(points: List<Pt>): Array<DoubleArray> {
    val mx = points.map { it.x.toDouble() }.average()
    val my = points.map { it.y.toDouble() }.average()
    var cxx = 0.0
    var cyy = 0.0
    var cxy = 0.0
    points.forEach {
        val dx = it.x - mx
        val dy = it.y - my
        cxx += dx * dx
        cyy += dy * dy
        cxy += dx * dy
    }
    val n = points.size.toDouble()
    return arrayOf(doubleArrayOf(cxx / n, cxy / n), doubleArrayOf(cxy / n, cyy / n))
}

internal fun meanOf(points: List<Pt>): Pt =
    Pt(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())

/** Angle of a direction in degrees, folded to [0°, 180°) — a direction and its negation are one axis. */
internal fun axisAngleDegrees(dx: Double, dy: Double): Double {
    var deg = Math.toDegrees(kotlin.math.atan2(dy, dx))
    while (deg < 0) deg += 180.0
    while (deg >= 180.0) deg -= 180.0
    return deg
}

/** Smallest angle between two undirected axes, in degrees — always in [0°, 90°]. */
internal fun axisSeparation(a: Double, b: Double): Double {
    val d = abs(a - b) % 180.0
    return min(d, 180.0 - d)
}

/**
 * Rescale a layout into the drawable unit square, preserving aspect ratio so a genuinely elongated
 * embedding still reads as elongated. The canvas maps [0,1] onto the plot area, so every builder
 * has to pass its output through here.
 */
internal fun fitToUnit(points: List<Pt>, margin: Float = 0.08f): List<Pt> {
    if (points.isEmpty()) return points
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val span = max(max(maxX - minX, maxY - minY), 1e-6f)
    val scale = (1f - 2f * margin) / span
    val offX = margin + ((1f - 2f * margin) - (maxX - minX) * scale) / 2f
    val offY = margin + ((1f - 2f * margin) - (maxY - minY) * scale) / 2f
    return points.map { Pt(offX + (it.x - minX) * scale, offY + (it.y - minY) * scale) }
}

// ── Datasets ─────────────────────────────────────────────────────────────────

private class DimRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767.0
    }

    fun uniform(lo: Double, hi: Double) = lo + next() * (hi - lo)

    /** Box-Muller, so the noise in the factor-analysis and PCA datasets is actually Gaussian. */
    fun gaussian(): Double {
        val u1 = next().coerceAtLeast(1e-9)
        val u2 = next()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}

/**
 * The same correlated cloud PCA's own lab uses, as [Pt]. Incremental PCA and SVD are both told as
 * "the PCA answer, reached differently", so they have to be looking at the identical points — the
 * comparison is meaningless on data PCA never saw. Reproduces `correlatedCloud`'s construction from
 * the same LCG seed.
 */
internal val correlatedCloudPts: List<Pt> = DimRng(31).let { rng ->
    List(18) {
        val t = 0.10 + 0.80 * (it / 17.0)
        Pt(
            (t + (rng.next() - 0.5) * 0.10).coerceIn(0.04, 0.96).toFloat(),
            (0.15 + 0.72 * t + (rng.next() - 0.5) * 0.14).coerceIn(0.04, 0.96).toFloat(),
        )
    }
}

/**
 * Two concentric rings. Linearly inseparable *and* linearly un-projectable: no straight axis
 * separates them, which is exactly the case kernel PCA exists for.
 */
// scikit-learn's `make_circles(factor=0.35, noise=0.05)` geometry, in its own coordinates rather
// than pre-squeezed into the unit square — the RBF bandwidth γ has to be quoted against a real
// scale, and drawing goes through `fitToUnit`. Angles are sampled, not evenly spaced: a perfectly
// regular ring is rotationally symmetric, and its leading kernel components are then the angular
// harmonics (in degenerate pairs) which carry no radius information at all.
internal val concentricRings: List<Pt> = DimRng(101).let { rng ->
    val inner = List(22) {
        val a = rng.uniform(0.0, 2.0 * Math.PI)
        val r = 0.35 + rng.gaussian() * 0.05
        Pt((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
    }
    val outer = List(26) {
        val a = rng.uniform(0.0, 2.0 * Math.PI)
        val r = 1.0 + rng.gaussian() * 0.05
        Pt((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
    }
    inner + outer
}

/** First 20 points of [concentricRings] are the inner ring — the ground truth the labs score against. */
internal const val InnerRingCount = 22

/**
 * Three clusters that differ in both spread and separation: two tight ones close together and one
 * loose one far away. t-SNE's best-known distortion — equalising cluster sizes and discarding the
 * relative distances between clusters — is only visible on data that has those differences.
 */
internal val unevenClusters: List<Pt> = DimRng(211).let { rng ->
    val a = List(15) { Pt((0.22 + rng.gaussian() * 0.030).toFloat(), (0.30 + rng.gaussian() * 0.030).toFloat()) }
    val b = List(15) { Pt((0.34 + rng.gaussian() * 0.030).toFloat(), (0.28 + rng.gaussian() * 0.030).toFloat()) }
    val c = List(15) { Pt((0.78 + rng.gaussian() * 0.105).toFloat(), (0.76 + rng.gaussian() * 0.105).toFloat()) }
    // The loose cluster's Gaussian tail reaches past 1.0, and this dataset is drawn directly rather
    // than only as an embedding. Fitting preserves the aspect ratio, so every ratio the labs report
    // — separation, spread against spread — is unchanged by it.
    fitToUnit(a + b + c)
}

/** Cluster membership for [unevenClusters]: 15 tight, 15 tight, 15 loose. */
internal fun unevenClusterLabel(index: Int) = index / 15

/**
 * Two independent uniform sources put through a non-orthogonal mixing matrix. Uniform (sub-Gaussian)
 * sources make the mixed cloud a parallelogram, so the answer ICA is looking for — the two mixing
 * directions — is visible as the edges, and PCA's orthogonal axes visibly are not them.
 */
internal val mixingMatrix = arrayOf(doubleArrayOf(1.0, 0.62), doubleArrayOf(0.35, 1.0))

internal val mixedSources: List<Pt> = DimRng(307).let { rng ->
    List(120) {
        val s1 = rng.uniform(-1.0, 1.0)
        val s2 = rng.uniform(-1.0, 1.0)
        Pt(
            (mixingMatrix[0][0] * s1 + mixingMatrix[0][1] * s2).toFloat(),
            (mixingMatrix[1][0] * s1 + mixingMatrix[1][1] * s2).toFloat(),
        )
    }
}

/**
 * A one-factor model over three variables: `xᵢ = λᵢ·f + εᵢ`, with variable 2 given a large private
 * noise. Factor analysis is supposed to charge that variance to ε and leave the loading direction
 * alone; PCA has no ε term, so it has to absorb it into the component.
 */
internal class FactorSample(val x1: Double, val x2: Double, val x3: Double)

internal val trueLoadings = doubleArrayOf(0.90, 0.55, 0.80)
internal val trueUniquenesses = doubleArrayOf(0.10, 0.95, 0.16)

internal val factorSamples: List<FactorSample> = DimRng(419).let { rng ->
    List(240) {
        val f = rng.gaussian()
        FactorSample(
            trueLoadings[0] * f + rng.gaussian() * sqrt(trueUniquenesses[0]),
            trueLoadings[1] * f + rng.gaussian() * sqrt(trueUniquenesses[1]),
            trueLoadings[2] * f + rng.gaussian() * sqrt(trueUniquenesses[2]),
        )
    }
}

/**
 * A spiral: a one-dimensional curve embedded in the plane, sampled in order of arc length. Its
 * intrinsic coordinate is the parameter t, and no straight-line projection can recover it — which is
 * the whole argument for a manifold method.
 */
internal val spiralCurve: List<Pt> = DimRng(523).let { rng ->
    List(40) {
        val t = it / 39.0
        val a = 0.6 + 2.0 * Math.PI * t
        val r = 0.10 + 0.40 * t
        Pt(
            (0.5 + r * cos(a) + rng.gaussian() * 0.006).toFloat(),
            (0.5 + r * sin(a) + rng.gaussian() * 0.006).toFloat(),
        )
    }
}

// ── Kernel PCA ───────────────────────────────────────────────────────────────

internal class KernelPcaResult(
    val gram: Array<DoubleArray>,
    val centered: Array<DoubleArray>,
    val eigenvalues: DoubleArray,
    /** `components[k][i]` is point i's coordinate on kernel principal component k. */
    val components: List<DoubleArray>,
)

internal fun kernelPca(points: List<Pt>, gamma: Double, components: Int = 2): KernelPcaResult {
    val n = points.size
    val k = Array(n) { i ->
        DoubleArray(n) { j ->
            val dx = (points[i].x - points[j].x).toDouble()
            val dy = (points[i].y - points[j].y).toDouble()
            exp(-gamma * (dx * dx + dy * dy))
        }
    }

    // Double centering in feature space: K̃ = K − 1ₙK − K1ₙ + 1ₙK1ₙ. Skipping it is the classic
    // kernel-PCA bug — the components then describe the offset of the data from the feature-space
    // origin rather than its spread.
    val rowMean = DoubleArray(n) { i -> k[i].average() }
    val grand = rowMean.average()
    val centered = Array(n) { i -> DoubleArray(n) { j -> k[i][j] - rowMean[i] - rowMean[j] + grand } }

    val eigen = jacobiEigen(centered)
    val comps = (0 until components).map { c ->
        val lambda = max(eigen.values[c], 1e-12)
        // Projection of point i onto component c is √λ times the eigenvector entry.
        DoubleArray(n) { i -> eigen.vectors[c][i] * sqrt(lambda) }
    }
    return KernelPcaResult(k, centered, eigen.values, comps)
}

/**
 * Best accuracy achievable by thresholding a single coordinate, taking whichever side is the inner
 * ring. A one-number summary of "does this axis separate the two rings at all".
 */
internal fun bestThresholdAccuracy(scores: DoubleArray, positiveCount: Int): Double {
    val n = scores.size
    val sorted = scores.sorted()
    var best = 0.0
    val cuts = sorted.indices.map { i -> if (i == 0) sorted[0] - 1.0 else (sorted[i - 1] + sorted[i]) / 2.0 } + (sorted.last() + 1.0)
    cuts.forEach { cut ->
        var correct = 0
        for (i in 0 until n) {
            val isInner = i < positiveCount
            if ((scores[i] < cut) == isInner) correct++
        }
        best = max(best, max(correct, n - correct).toDouble() / n)
    }
    return best
}

// ── Incremental PCA ──────────────────────────────────────────────────────────

internal class IncrementalPcaStep(
    val seen: Int,
    val mean: Pt,
    /** Leading component direction from the data seen so far. */
    val axis: Pt,
    val explained: Double,
    /** Angle in degrees between this axis and the axis a full-batch fit would have found. */
    val degreesFromBatch: Double,
)

/**
 * Streams `points` in chunks, maintaining a running mean and second-moment matrix. Nothing but the
 * 2×2 accumulator and a count is retained between chunks, which is the memory claim the lab makes.
 */
internal fun incrementalPca(points: List<Pt>, chunk: Int): List<IncrementalPcaStep> {
    val batchAxis = jacobiEigen(covariance2(points)).vectors[0]
    val batchAngle = axisAngleDegrees(batchAxis[0], batchAxis[1])

    var count = 0
    var sx = 0.0
    var sy = 0.0
    var sxx = 0.0
    var syy = 0.0
    var sxy = 0.0
    val steps = mutableListOf<IncrementalPcaStep>()

    points.chunked(chunk).forEach { batch ->
        batch.forEach { p ->
            count++
            sx += p.x
            sy += p.y
            sxx += p.x.toDouble() * p.x
            syy += p.y.toDouble() * p.y
            sxy += p.x.toDouble() * p.y
        }
        val mx = sx / count
        val my = sy / count
        val cov = arrayOf(
            doubleArrayOf(sxx / count - mx * mx, sxy / count - mx * my),
            doubleArrayOf(sxy / count - mx * my, syy / count - my * my),
        )
        val eigen = jacobiEigen(cov)
        val axis = eigen.vectors[0]
        val total = eigen.values[0] + eigen.values[1]
        steps += IncrementalPcaStep(
            seen = count,
            mean = Pt(mx.toFloat(), my.toFloat()),
            axis = Pt(axis[0].toFloat(), axis[1].toFloat()),
            explained = if (total > 1e-12) eigen.values[0] / total else 1.0,
            degreesFromBatch = axisSeparation(axisAngleDegrees(axis[0], axis[1]), batchAngle),
        )
    }
    return steps
}

// ── SVD of an n×2 matrix ─────────────────────────────────────────────────────

internal class Svd2Result(
    val singularValues: DoubleArray,
    /** Right singular vectors — the axes in data space. */
    val rightVectors: List<DoubleArray>,
    /** Rank-1 reconstruction of each row. */
    val rank1: List<Pt>,
    val rank1Error: Double,
)

/**
 * SVD via the eigendecomposition of XᵀX. Correct for a 2-column matrix and short enough to read,
 * which matters more here than the numerical care a general implementation would need.
 */
internal fun svd2(rows: List<Pt>): Svd2Result {
    val xtx = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(0.0, 0.0))
    rows.forEach {
        xtx[0][0] += it.x.toDouble() * it.x
        xtx[0][1] += it.x.toDouble() * it.y
        xtx[1][0] += it.x.toDouble() * it.y
        xtx[1][1] += it.y.toDouble() * it.y
    }
    val eigen = jacobiEigen(xtx)
    val sigma = DoubleArray(2) { sqrt(max(eigen.values[it], 0.0)) }
    val v1 = eigen.vectors[0]
    val rank1 = rows.map { p ->
        val t = p.x * v1[0] + p.y * v1[1]
        Pt((t * v1[0]).toFloat(), (t * v1[1]).toFloat())
    }
    var err = 0.0
    rows.indices.forEach { i ->
        val dx = rows[i].x - rank1[i].x
        val dy = rows[i].y - rank1[i].y
        err += dx.toDouble() * dx + dy.toDouble() * dy
    }
    return Svd2Result(sigma, eigen.vectors, rank1, sqrt(err))
}

// ── FastICA ──────────────────────────────────────────────────────────────────

internal class IcaResult(
    /** Whitened data — the intermediate PCA step every ICA implementation starts from. */
    val whitened: List<Pt>,
    /** Recovered independent components, one per sample. */
    val sources: List<Pt>,
    /** Recovered mixing directions in the original data space, as unit vectors. */
    val mixingDirections: List<DoubleArray>,
    val iterations: Int,
)

/**
 * FastICA with the tanh nonlinearity, deflation for the second component. Two components only,
 * which is what makes the second one free: after the first, orthogonality in whitened space fixes it.
 */
internal fun fastIca(points: List<Pt>, maxIterations: Int = 200): IcaResult {
    val mean = meanOf(points)
    val centered = points.map { Pt(it.x - mean.x, it.y - mean.y) }
    val cov = covariance2(centered)
    val eigen = jacobiEigen(cov)

    // Whitening matrix W = Λ^(−½)Eᵀ. After it the data has identity covariance, so all that is left
    // to find is a rotation — which is why ICA is often described as "PCA plus a rotation".
    val e = eigen.vectors
    val scale = DoubleArray(2) { 1.0 / sqrt(max(eigen.values[it], 1e-12)) }
    fun whiten(p: Pt) = doubleArrayOf(
        scale[0] * (e[0][0] * p.x + e[0][1] * p.y),
        scale[1] * (e[1][0] * p.x + e[1][1] * p.y),
    )
    val z = centered.map { whiten(it) }

    var w = doubleArrayOf(0.6, 0.8)
    var used = 0
    for (iter in 1..maxIterations) {
        used = iter
        var g0 = 0.0
        var g1 = 0.0
        var gPrime = 0.0
        z.forEach { zi ->
            val u = w[0] * zi[0] + w[1] * zi[1]
            val t = kotlin.math.tanh(u)
            g0 += zi[0] * t
            g1 += zi[1] * t
            gPrime += 1.0 - t * t
        }
        val n = z.size.toDouble()
        var nx = g0 / n - (gPrime / n) * w[0]
        var ny = g1 / n - (gPrime / n) * w[1]
        val norm = hypot(nx, ny)
        if (norm < 1e-12) break
        nx /= norm
        ny /= norm
        val converged = abs(abs(nx * w[0] + ny * w[1]) - 1.0) < 1e-10
        w = doubleArrayOf(nx, ny)
        if (converged) break
    }
    val w2 = doubleArrayOf(-w[1], w[0])

    val sources = z.map { zi ->
        Pt((w[0] * zi[0] + w[1] * zi[1]).toFloat(), (w2[0] * zi[0] + w2[1] * zi[1]).toFloat())
    }

    // A recovered source direction maps back to data space through the inverse of the whitening
    // transform: x = EΛ^(½)wᵀ, up to the sign and scale ICA cannot determine.
    fun unwhiten(v: DoubleArray): DoubleArray {
        val a = v[0] / scale[0]
        val b = v[1] / scale[1]
        val x = e[0][0] * a + e[1][0] * b
        val y = e[0][1] * a + e[1][1] * b
        val n = hypot(x, y)
        return doubleArrayOf(x / n, y / n)
    }
    return IcaResult(
        whitened = z.map { Pt(it[0].toFloat(), it[1].toFloat()) },
        sources = sources,
        mixingDirections = listOf(unwhiten(w), unwhiten(w2)),
        iterations = used,
    )
}

/** Excess kurtosis. Zero for a Gaussian, negative for the uniform sources this lab mixes. */
internal fun excessKurtosis(values: List<Double>): Double {
    val m = values.average()
    val v = values.sumOf { (it - m) * (it - m) } / values.size
    if (v < 1e-12) return 0.0
    val m4 = values.sumOf { val d = it - m; d * d * d * d } / values.size
    return m4 / (v * v) - 3.0
}

// ── Factor analysis ──────────────────────────────────────────────────────────

internal class FactorAnalysisResult(
    val correlation: Array<DoubleArray>,
    val loadings: DoubleArray,
    val uniquenesses: DoubleArray,
    /** PCA's first-component loadings on the same correlation matrix, for the comparison. */
    val pcaLoadings: DoubleArray,
)

/**
 * One-factor analysis of three standardized variables. With p = 3 and one factor the model is
 * exactly identified, so the loadings come out of the three off-diagonal correlations in closed
 * form — no EM, and no question of whether it converged.
 */
internal fun oneFactorAnalysis(samples: List<FactorSample>): FactorAnalysisResult {
    val cols = listOf(samples.map { it.x1 }, samples.map { it.x2 }, samples.map { it.x3 })
    val means = cols.map { it.average() }
    val sds = cols.indices.map { c ->
        sqrt(cols[c].sumOf { val d = it - means[c]; d * d } / cols[c].size)
    }
    val r = Array(3) { i ->
        DoubleArray(3) { j ->
            val cov = samples.indices.sumOf { k ->
                (cols[i][k] - means[i]) * (cols[j][k] - means[j])
            } / samples.size
            cov / (sds[i] * sds[j])
        }
    }

    // λ₁² = r₁₂r₁₃/r₂₃ and its two rotations. Signs are taken positive: with one factor the whole
    // solution is only determined up to a global sign anyway.
    val l1 = sqrt(max(r[0][1] * r[0][2] / r[1][2], 1e-9))
    val l2 = sqrt(max(r[0][1] * r[1][2] / r[0][2], 1e-9))
    val l3 = sqrt(max(r[0][2] * r[1][2] / r[0][1], 1e-9))
    val loadings = doubleArrayOf(l1, l2, l3)
    val uniquenesses = DoubleArray(3) { max(1.0 - loadings[it] * loadings[it], 0.0) }

    val pca = jacobiEigen(r)
    val pc1 = pca.vectors[0]
    // Scaled to loadings (eigenvector times √eigenvalue) so the two are on the same footing.
    val sqrtLambda = sqrt(max(pca.values[0], 0.0))
    val sign = if (pc1.sum() < 0) -1.0 else 1.0
    val pcaLoadings = DoubleArray(3) { sign * pc1[it] * sqrtLambda }

    return FactorAnalysisResult(r, loadings, uniquenesses, pcaLoadings)
}

// ── t-SNE ────────────────────────────────────────────────────────────────────

internal class TsneStep(val iteration: Int, val embedding: List<Pt>, val klDivergence: Double)

private fun squaredDistances(points: List<Pt>): Array<DoubleArray> {
    val n = points.size
    return Array(n) { i ->
        DoubleArray(n) { j ->
            val dx = (points[i].x - points[j].x).toDouble()
            val dy = (points[i].y - points[j].y).toDouble()
            dx * dx + dy * dy
        }
    }
}

/**
 * Conditional probabilities with a per-point bandwidth chosen by binary search so every point's
 * neighbourhood has the same entropy. This is the step that gives t-SNE its one real parameter and
 * its adaptability to varying density — a dense cluster gets a small σ, a sparse one a large σ.
 */
internal fun tsneAffinities(points: List<Pt>, perplexity: Double): Array<DoubleArray> {
    val n = points.size
    val d2 = squaredDistances(points)
    val target = ln(perplexity)
    val p = Array(n) { DoubleArray(n) }

    for (i in 0 until n) {
        var lo = 1e-6
        var hi = 1e6
        var beta = 1.0
        var row = DoubleArray(n)
        repeat(60) {
            var sum = 0.0
            for (j in 0 until n) {
                row[j] = if (i == j) 0.0 else exp(-beta * d2[i][j])
                sum += row[j]
            }
            if (sum < 1e-12) sum = 1e-12
            var h = 0.0
            for (j in 0 until n) {
                val v = row[j] / sum
                if (v > 1e-12) h -= v * ln(v)
            }
            if (h > target) {
                lo = beta
                beta = if (hi > 9e5) beta * 2 else (beta + hi) / 2
            } else {
                hi = beta
                beta = (beta + lo) / 2
            }
            row = DoubleArray(n) { j -> if (i == j) 0.0 else row[j] / sum }
        }
        for (j in 0 until n) p[i][j] = row[j]
    }

    // Symmetrize and normalize to a joint distribution.
    val joint = Array(n) { DoubleArray(n) }
    for (i in 0 until n) for (j in 0 until n) joint[i][j] = (p[i][j] + p[j][i]) / (2.0 * n)
    return joint
}

internal fun tsne(
    points: List<Pt>,
    perplexity: Double = 8.0,
    iterations: Int = 300,
    snapshotsAt: List<Int> = listOf(0, 40, 120, 300),
): List<TsneStep> {
    val n = points.size
    val p = tsneAffinities(points, perplexity)
    val rng = DimRng(661)
    var y = Array(n) { doubleArrayOf(rng.gaussian() * 1e-2, rng.gaussian() * 1e-2) }
    val velocity = Array(n) { doubleArrayOf(0.0, 0.0) }
    val steps = mutableListOf<TsneStep>()

    fun qMatrix(): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        val num = Array(n) { DoubleArray(n) }
        var sum = 0.0
        for (i in 0 until n) for (j in 0 until n) {
            if (i == j) continue
            val dx = y[i][0] - y[j][0]
            val dy = y[i][1] - y[j][1]
            num[i][j] = 1.0 / (1.0 + dx * dx + dy * dy)
            sum += num[i][j]
        }
        if (sum < 1e-12) sum = 1e-12
        val q = Array(n) { i -> DoubleArray(n) { j -> num[i][j] / sum } }
        return num to q
    }

    fun kl(q: Array<DoubleArray>): Double {
        var total = 0.0
        for (i in 0 until n) for (j in 0 until n) {
            if (i == j) continue
            if (p[i][j] > 1e-12) total += p[i][j] * ln(p[i][j] / max(q[i][j], 1e-12))
        }
        return total
    }

    fun snapshot(iteration: Int) {
        val (_, q) = qMatrix()
        steps += TsneStep(iteration, fitToUnit(y.map { Pt(it[0].toFloat(), it[1].toFloat()) }), kl(q))
    }

    if (0 in snapshotsAt) snapshot(0)

    for (iter in 1..iterations) {
        // Early exaggeration: inflating P for the first stretch forces the clusters apart before the
        // repulsion has anything to push against. Without it the embedding collapses into one lump.
        val exaggeration = if (iter <= 80) 4.0 else 1.0
        val momentum = if (iter <= 80) 0.5 else 0.8
        val lr = 220.0

        val (num, q) = qMatrix()
        for (i in 0 until n) {
            var gx = 0.0
            var gy = 0.0
            for (j in 0 until n) {
                if (i == j) continue
                val mult = (exaggeration * p[i][j] - q[i][j]) * num[i][j]
                gx += mult * (y[i][0] - y[j][0])
                gy += mult * (y[i][1] - y[j][1])
            }
            velocity[i][0] = momentum * velocity[i][0] - lr * 4.0 * gx
            velocity[i][1] = momentum * velocity[i][1] - lr * 4.0 * gy
        }
        y = Array(n) { i -> doubleArrayOf(y[i][0] + velocity[i][0], y[i][1] + velocity[i][1]) }
        // Re-centre, so the whole cloud does not drift off while the shape is what matters.
        val cx = y.sumOf { it[0] } / n
        val cy = y.sumOf { it[1] } / n
        y.forEach { it[0] -= cx; it[1] -= cy }

        if (iter in snapshotsAt) snapshot(iter)
    }
    return steps
}

// ── UMAP ─────────────────────────────────────────────────────────────────────

internal class UmapResult(
    val graph: Array<DoubleArray>,
    /** Layout at each requested epoch, in order; the last one is the finished embedding. */
    val snapshots: List<Pair<Int, List<Pt>>>,
) {
    val embedding: List<Pt> get() = snapshots.last().second
}

/**
 * The fuzzy simplicial set UMAP builds: for each point, ρ is the distance to its nearest neighbour
 * (so every point is connected to *something* with weight 1) and σ is solved so the neighbourhood
 * weights sum to log₂k. Then the two directed weights are combined probabilistically.
 */
internal fun umapGraph(points: List<Pt>, k: Int = 8): Array<DoubleArray> {
    val n = points.size
    val d = Array(n) { i -> DoubleArray(n) { j -> dist(points[i], points[j]).toDouble() } }
    val target = ln(k.toDouble()) / ln(2.0)
    val directed = Array(n) { DoubleArray(n) }

    for (i in 0 until n) {
        val neighbours = (0 until n).filter { it != i }.sortedBy { d[i][it] }.take(k)
        val rho = d[i][neighbours.first()]
        var lo = 1e-6
        var hi = 1e6
        var sigma = 1.0
        repeat(64) {
            val sum = neighbours.sumOf { j -> exp(-max(d[i][j] - rho, 0.0) / sigma) }
            if (sum > target) {
                hi = sigma
                sigma = (sigma + lo) / 2
            } else {
                lo = sigma
                sigma = if (hi > 9e5) sigma * 2 else (sigma + hi) / 2
            }
        }
        neighbours.forEach { j -> directed[i][j] = exp(-max(d[i][j] - rho, 0.0) / sigma) }
    }

    // Probabilistic t-conorm: a + b − ab. An edge survives if *either* endpoint considers the other
    // a neighbour, which is what keeps the graph connected across a density change.
    return Array(n) { i -> DoubleArray(n) { j -> directed[i][j] + directed[j][i] - directed[i][j] * directed[j][i] } }
}

/**
 * UMAP's layout step. The published implementation approximates the repulsive term with negative
 * sampling; with n ≤ 60 the exact sum is cheap, so this computes it directly — same objective,
 * without the sampling noise.
 */
internal fun umapLayout(
    points: List<Pt>,
    k: Int = 8,
    epochs: Int = 400,
    snapshotsAt: List<Int> = listOf(1, 40, 400),
): UmapResult {
    val n = points.size
    val graph = umapGraph(points, k)

    // Spectral-ish initialisation from PCA of the input, which is what UMAP falls back to when the
    // spectral layout is unavailable.
    val mean = meanOf(points)
    val eigen = jacobiEigen(covariance2(points))
    var y = Array(n) { i ->
        val dx = (points[i].x - mean.x).toDouble()
        val dy = (points[i].y - mean.y).toDouble()
        doubleArrayOf(
            (eigen.vectors[0][0] * dx + eigen.vectors[0][1] * dy) * 10,
            (eigen.vectors[1][0] * dx + eigen.vectors[1][1] * dy) * 10,
        )
    }

    // The a, b that fit UMAP's smooth approximation to the min_dist = 0.1 curve.
    val a = 1.577
    val b = 0.895
    val gamma = 1.0

    val snapshots = mutableListOf<Pair<Int, List<Pt>>>()
    for (epoch in 1..epochs) {
        val alpha = 1.0 * (1.0 - (epoch - 1.0) / epochs)
        val grad = Array(n) { doubleArrayOf(0.0, 0.0) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) continue
                val dx = y[i][0] - y[j][0]
                val dy = y[i][1] - y[j][1]
                val d2 = max(dx * dx + dy * dy, 1e-9)
                val pow = Math.pow(d2, b)

                val attract = -2.0 * a * b * Math.pow(d2, b - 1.0) / (1.0 + a * pow) * graph[i][j]
                val repel = gamma * 2.0 * b / ((0.001 + d2) * (1.0 + a * pow)) * (1.0 - graph[i][j])

                grad[i][0] += (attract + repel) * dx
                grad[i][1] += (attract + repel) * dy
            }
        }
        for (i in 0 until n) {
            y[i][0] += alpha * grad[i][0].coerceIn(-4.0, 4.0)
            y[i][1] += alpha * grad[i][1].coerceIn(-4.0, 4.0)
        }
        if (epoch in snapshotsAt) {
            snapshots += epoch to fitToUnit(y.map { Pt(it[0].toFloat(), it[1].toFloat()) })
        }
    }
    return UmapResult(graph, snapshots)
}

// ── Locally linear embedding ─────────────────────────────────────────────────

internal class LleResult(
    val neighbours: List<List<Int>>,
    val weights: Array<DoubleArray>,
    /** One coordinate per point — the recovered intrinsic parameter. */
    val embedding: DoubleArray,
)

/**
 * LLE in its three published steps: k nearest neighbours, the reconstruction weights that rebuild
 * each point from them under a sum-to-one constraint, then the bottom eigenvectors of
 * M = (I−W)ᵀ(I−W). The bottom eigenvector is the constant one and is discarded; the next carries
 * the embedding.
 */
internal fun lle(points: List<Pt>, k: Int = 4, dimensions: Int = 1): LleResult {
    val n = points.size
    val neighbours = (0 until n).map { i ->
        (0 until n).filter { it != i }.sortedBy { dist(points[i], points[it]) }.take(k)
    }

    val w = Array(n) { DoubleArray(n) }
    for (i in 0 until n) {
        val nb = neighbours[i]
        val m = nb.size
        // Local Gram matrix of the neighbour offsets, with the standard regularisation for the
        // k > d case where it is singular.
        val g = Array(m) { r ->
            DoubleArray(m) { c ->
                val dr = doubleArrayOf((points[nb[r]].x - points[i].x).toDouble(), (points[nb[r]].y - points[i].y).toDouble())
                val dc = doubleArrayOf((points[nb[c]].x - points[i].x).toDouble(), (points[nb[c]].y - points[i].y).toDouble())
                dr[0] * dc[0] + dr[1] * dc[1]
            }
        }
        val trace = (0 until m).sumOf { g[it][it] }
        for (r in 0 until m) g[r][r] += 1e-3 * (if (trace > 0) trace else 1.0)

        val solved = solveSymmetric(g, DoubleArray(m) { 1.0 })
        val sum = solved.sum().let { if (abs(it) < 1e-12) 1e-12 else it }
        nb.forEachIndexed { idx, j -> w[i][j] = solved[idx] / sum }
    }

    val m = Array(n) { i ->
        DoubleArray(n) { j ->
            var v = (if (i == j) 1.0 else 0.0) - w[i][j] - w[j][i]
            for (t in 0 until n) v += w[t][i] * w[t][j]
            v
        }
    }
    val eigen = jacobiEigen(m)
    // Ascending: the smallest eigenvalue is ~0 with the constant eigenvector, which carries nothing.
    val bottom = eigen.values.indices.sortedBy { eigen.values[it] }
    val chosen = bottom[1]
    return LleResult(neighbours, w, DoubleArray(n) { eigen.vectors[chosen][it] }.let {
        if (dimensions == 1) it else it
    })
}

/** Gaussian elimination with partial pivoting — small systems only, which is all LLE needs. */
private fun solveSymmetric(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { i -> a[i].copyOf() + doubleArrayOf(b[i]) }
    for (col in 0 until n) {
        var pivot = col
        for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
        val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
        val p = if (abs(m[col][col]) < 1e-12) 1e-12 else m[col][col]
        for (r in 0 until n) {
            if (r == col) continue
            val f = m[r][col] / p
            for (c in col..n) m[r][c] -= f * m[col][c]
        }
    }
    return DoubleArray(n) { m[it][n] / (if (abs(m[it][it]) < 1e-12) 1e-12 else m[it][it]) }
}

// ── Scoring ──────────────────────────────────────────────────────────────────

/** Fraction of each point's k input-space neighbours that are still neighbours in the embedding. */
internal fun neighbourPreservation(input: List<Pt>, embedded: List<Pt>, k: Int = 5): Double {
    val n = input.size
    var kept = 0
    for (i in 0 until n) {
        val a = (0 until n).filter { it != i }.sortedBy { dist(input[i], input[it]) }.take(k).toSet()
        val b = (0 until n).filter { it != i }.sortedBy { dist(embedded[i], embedded[it]) }.take(k).toSet()
        kept += a.count { it in b }
    }
    return kept.toDouble() / (n * k)
}

/** Ratio of mean between-cluster distance to mean within-cluster distance. */
internal fun separationRatio(points: List<Pt>, label: (Int) -> Int): Double {
    var within = 0.0
    var withinN = 0
    var between = 0.0
    var betweenN = 0
    for (i in points.indices) {
        for (j in i + 1 until points.size) {
            val d = dist(points[i], points[j]).toDouble()
            if (label(i) == label(j)) {
                within += d
                withinN++
            } else {
                between += d
                betweenN++
            }
        }
    }
    val w = if (withinN > 0) within / withinN else 1e-9
    val b = if (betweenN > 0) between / betweenN else 0.0
    return b / max(w, 1e-9)
}

/** Spearman rank correlation, folded to its absolute value — a reversed ordering is still recovered. */
internal fun absSpearman(a: DoubleArray, b: DoubleArray): Double {
    fun ranks(v: DoubleArray): DoubleArray {
        val order = v.indices.sortedBy { v[it] }
        val r = DoubleArray(v.size)
        order.forEachIndexed { rank, idx -> r[idx] = rank.toDouble() }
        return r
    }
    val ra = ranks(a)
    val rb = ranks(b)
    val ma = ra.average()
    val mb = rb.average()
    var num = 0.0
    var da = 0.0
    var db = 0.0
    for (i in ra.indices) {
        num += (ra[i] - ma) * (rb[i] - mb)
        da += (ra[i] - ma) * (ra[i] - ma)
        db += (rb[i] - mb) * (rb[i] - mb)
    }
    return abs(num / sqrt(max(da * db, 1e-12)))
}
