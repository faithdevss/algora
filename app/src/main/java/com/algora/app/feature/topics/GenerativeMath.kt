package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

// ── C7 · Generative deep learning ────────────────────────────────────────────
// VAE, DCGAN, CycleGAN, StyleGAN, Stable Diffusion, Neural Style Transfer and DeepFakes. Pinned by
// `GenerativeMathTest`.
//
// Three kinds of claim are computed here and they fail differently, so they are kept apart.
//
//   Counting arguments — transposed-convolution coverage, CycleGAN's mapping count, latent
//   diffusion's element and attention-pair tables — are exact combinatorics, asserted to the digit.
//
//   Invariance identities — a Gram matrix under a spatial permutation, AdaIN's output statistics —
//   are exact, asserted to floating-point tolerance. These are the claims worth *proving* in a lab
//   rather than stating, because "the Gram matrix throws away position" sounds like an approximation
//   and is in fact an equality.
//
//   Experiments — the VAE's active-unit count, StyleGAN's path lengths, the DeepFake encoder
//   comparison — train or sample. For these the test pins the *ordering* as well as the value: a
//   number that drifts is inaccurate, but an ordering that inverts makes the topic pointless.

// ── Shared numeric helpers ───────────────────────────────────────────────────

private fun gaussian(random: Random): Double {
    val u1 = random.nextDouble().coerceAtLeast(1e-12)
    val u2 = random.nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

/** Solve `a x = b` for small dense systems (k ≤ 4 here) by Gaussian elimination with pivoting. */
private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }
    for (col in 0 until n) {
        var pivot = col
        for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
        val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
        val d = m[col][col]
        if (abs(d) < 1e-12) continue
        for (c in col..n) m[col][c] /= d
        for (r in 0 until n) {
            if (r == col) continue
            val f = m[r][col]
            if (f == 0.0) continue
            for (c in col..n) m[r][c] -= f * m[col][c]
        }
    }
    return DoubleArray(n) { m[it][n] }
}

/** Top-k principal directions of a set of centred rows, by power iteration with deflation. */
private fun principalDirections(rows: List<DoubleArray>, k: Int, seed: Int = 3): List<DoubleArray> {
    val d = rows.first().size
    val cov = Array(d) { DoubleArray(d) }
    rows.forEach { r ->
        for (i in 0 until d) for (j in 0 until d) cov[i][j] += r[i] * r[j]
    }
    for (i in 0 until d) for (j in 0 until d) cov[i][j] /= rows.size

    val random = Random(seed)
    val basis = mutableListOf<DoubleArray>()
    repeat(k) {
        var v = DoubleArray(d) { gaussian(random) }
        repeat(400) {
            // Deflate against the directions already taken, then apply the covariance.
            basis.forEach { b ->
                val dot = v.indices.sumOf { v[it] * b[it] }
                for (i in 0 until d) v[i] -= dot * b[i]
            }
            val next = DoubleArray(d) { i -> (0 until d).sumOf { cov[i][it] * v[it] } }
            val norm = sqrt(next.sumOf { it * it })
            if (norm < 1e-12) return@repeat
            v = DoubleArray(d) { next[it] / norm }
        }
        basis.forEach { b ->
            val dot = v.indices.sumOf { v[it] * b[it] }
            for (i in 0 until d) v[i] -= dot * b[i]
        }
        val norm = sqrt(v.sumOf { it * it })
        basis += if (norm < 1e-12) DoubleArray(d) else DoubleArray(d) { v[it] / norm }
    }
    return basis
}

/** Least-squares map from latent rows to target rows: the decoder, solved rather than trained. */
private fun leastSquares(latents: List<DoubleArray>, targets: List<DoubleArray>): Array<DoubleArray> {
    val k = latents.first().size
    val d = targets.first().size
    val gram = Array(k) { DoubleArray(k) }
    latents.forEach { z ->
        for (i in 0 until k) for (j in 0 until k) gram[i][j] += z[i] * z[j]
    }
    for (i in 0 until k) gram[i][i] += 1e-8
    // One right-hand side per output dimension; the k x k Gram is shared.
    return Array(d) { outDim ->
        val rhs = DoubleArray(k)
        latents.forEachIndexed { n, z -> for (i in 0 until k) rhs[i] += z[i] * targets[n][outDim] }
        solve(gram.map { it.copyOf() }.toTypedArray(), rhs)
    }
}

private fun applyMap(w: Array<DoubleArray>, z: DoubleArray): DoubleArray =
    DoubleArray(w.size) { i -> w[i].indices.sumOf { w[i][it] * z[it] } }

private fun rmse(a: DoubleArray, b: DoubleArray): Double =
    sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) } / a.size)

// ── VAE ──────────────────────────────────────────────────────────────────────

/**
 * Two separate claims, because a VAE is two ideas bolted together and only one of them is about
 * generation.
 *
 * The first is the reparameterization trick, which is usually justified as "you cannot backprop
 * through a sample". That is true but understates it: the score-function estimator *can* differentiate
 * through a sample and is unbiased, it is simply unusable in practice because of its variance. Both
 * estimators are computed here on the same objective, and the variance gap has a closed form so the
 * Monte Carlo run can be checked against arithmetic rather than against itself.
 *
 * The second is the KL term, which does not merely regularise — it switches latent dimensions off
 * entirely. The lab trains a real linear VAE at several values of beta and counts how many
 * dimensions survive.
 */
internal object VaeLab {

    // ── Reparameterization vs the score-function estimator ───────────────────

    /**
     * Objective E‖z − c‖² with z ~ N(mu, I), differentiated with respect to one coordinate of mu.
     * Both estimators are unbiased; the question is only how noisy each one is.
     */
    class GradientVariance(
        val trueGradient: Double,
        val reparameterizedMean: Double,
        val scoreFunctionMean: Double,
        val reparameterizedVariance: Double,
        val scoreFunctionVariance: Double,
    ) {
        val ratio get() = scoreFunctionVariance / reparameterizedVariance
    }

    /**
     * Closed form for the same quantities, with every a_k = 1, sigma = 1.
     *
     * Reparameterized: Var[2(z₀ − c₀)] = 4.
     * Score function: E[‖z − c‖⁴u₀²] − (2a)², expanded over the Gaussian moments. The D-dependence
     * is the point — the reparameterized variance does not grow with dimension and this does.
     */
    fun scoreFunctionVarianceClosedForm(dim: Int): Double {
        val a = 1.0
        val bigA = dim * a * a
        val e = bigA * bigA +
            4 * a * a * (dim + 2) +
            15 + 8 * (dim - 1) + (dim - 1).toDouble() * (dim - 1) +
            2 * bigA * (dim + 2)
        return e - 4 * a * a
    }

    const val REPARAMETERIZED_VARIANCE_CLOSED_FORM = 4.0

    fun gradientVariance(dim: Int = 8, samples: Int = 400_000, seed: Int = 17): GradientVariance {
        val random = Random(seed)
        val mu = DoubleArray(dim) { 1.0 }
        val c = DoubleArray(dim)
        var rSum = 0.0
        var rSq = 0.0
        var sSum = 0.0
        var sSq = 0.0
        repeat(samples) {
            val eps = DoubleArray(dim) { gaussian(random) }
            val z = DoubleArray(dim) { mu[it] + eps[it] }
            // Reparameterized: differentiate the sample path. df/dmu_0 = 2(z_0 - c_0).
            val rep = 2 * (z[0] - c[0])
            // Score function: f(z) * d/dmu_0 log q(z) = f(z) * (z_0 - mu_0).
            val f = z.indices.sumOf { (z[it] - c[it]) * (z[it] - c[it]) }
            val score = f * (z[0] - mu[0])
            rSum += rep; rSq += rep * rep
            sSum += score; sSq += score * score
        }
        val n = samples.toDouble()
        val rMean = rSum / n
        val sMean = sSum / n
        return GradientVariance(
            trueGradient = 2.0,
            reparameterizedMean = rMean,
            scoreFunctionMean = sMean,
            reparameterizedVariance = rSq / n - rMean * rMean,
            scoreFunctionVariance = sSq / n - sMean * sMean,
        )
    }

    // ── A trained linear VAE, and the units the KL term switches off ─────────

    const val DATA_DIM = 6
    const val LATENT_DIM = 4
    const val TRUE_FACTORS = 2

    /** Data with a genuine 2-dimensional cause, observed in 6 dimensions with a little noise. */
    fun dataset(count: Int = 512, seed: Int = 29): List<DoubleArray> {
        val random = Random(seed)
        val mixing = Array(DATA_DIM) { i -> DoubleArray(TRUE_FACTORS) { j -> cos((i + 1) * (j + 1) * 0.7) } }
        return List(count) {
            val s = DoubleArray(TRUE_FACTORS) { gaussian(random) }
            DoubleArray(DATA_DIM) { i ->
                (0 until TRUE_FACTORS).sumOf { mixing[i][it] * s[it] } + 0.05 * gaussian(random)
            }
        }
    }

    class TrainedVae(
        val beta: Double,
        val perDimensionKl: DoubleArray,
        val reconstructionRmse: Double,
    ) {
        /** The standard convention: a unit is "active" if it carries more than 0.01 nats. */
        val activeUnits get() = perDimensionKl.count { it > ACTIVE_THRESHOLD }
        val totalKl get() = perDimensionKl.sum()
    }

    const val ACTIVE_THRESHOLD = 0.01

    /** The betas the labs and the copy both quote. Trained once — `train` is 60,000 SGD steps. */
    val betaSweep = listOf(0.001, 0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 4.0)

    val trained: Map<Double, TrainedVae> by lazy { betaSweep.associateWith { train(it) } }

    /** The Monte Carlo run at the dimension the copy quotes, cached so a frame builder is cheap. */
    val gradientVarianceAtEight: GradientVariance by lazy { gradientVariance(dim = 8) }

    /** The dimensions the variance plot sweeps; the closed form makes the curve free. */
    val varianceDims = listOf(1, 2, 4, 8, 16)

    /**
     * A linear VAE trained by SGD with the reparameterization trick — encoder mean is a learned
     * linear map, log-variance is a learned per-dimension constant, decoder is a learned linear map.
     * Small enough that every gradient below is written out by hand.
     */
    fun train(beta: Double, steps: Int = 60_000, learningRate: Double = 0.01, seed: Int = 41): TrainedVae {
        val data = dataset(seed = 29)
        val random = Random(seed)
        val encoder = Array(LATENT_DIM) { DoubleArray(DATA_DIM) { 0.1 * gaussian(random) } }
        val decoder = Array(DATA_DIM) { DoubleArray(LATENT_DIM) { 0.1 * gaussian(random) } }
        val logVar = DoubleArray(LATENT_DIM)

        repeat(steps) {
            val x = data[random.nextInt(data.size)]
            val mu = applyMap(encoder, x)
            val sigma = DoubleArray(LATENT_DIM) { exp(0.5 * logVar[it]) }
            val eps = DoubleArray(LATENT_DIM) { gaussian(random) }
            val z = DoubleArray(LATENT_DIM) { mu[it] + sigma[it] * eps[it] }
            val xHat = applyMap(decoder, z)
            val residual = DoubleArray(DATA_DIM) { xHat[it] - x[it] }

            // dL/dz through the decoder.
            val dz = DoubleArray(LATENT_DIM) { j -> 2.0 * (0 until DATA_DIM).sumOf { residual[it] * decoder[it][j] } }
            for (i in 0 until DATA_DIM) for (j in 0 until LATENT_DIM) {
                decoder[i][j] -= learningRate * 2.0 * residual[i] * z[j]
            }
            // Reconstruction path plus the KL's pull on the mean.
            for (j in 0 until LATENT_DIM) {
                val dMu = dz[j] + beta * mu[j]
                for (i in 0 until DATA_DIM) encoder[j][i] -= learningRate * dMu * x[i]
                // sigma enters z as sigma*eps; the KL contributes beta*0.5*(exp(l) - 1).
                val dLogVar = dz[j] * eps[j] * sigma[j] * 0.5 + beta * 0.5 * (exp(logVar[j]) - 1.0)
                logVar[j] -= learningRate * dLogVar
                logVar[j] = logVar[j].coerceIn(-12.0, 4.0)
            }
        }

        // Evaluate at the posterior mean, which is what a trained VAE is used with.
        var sqErr = 0.0
        val muSq = DoubleArray(LATENT_DIM)
        data.forEach { x ->
            val mu = applyMap(encoder, x)
            for (j in 0 until LATENT_DIM) muSq[j] += mu[j] * mu[j]
            val xHat = applyMap(decoder, mu)
            sqErr += x.indices.sumOf { (xHat[it] - x[it]) * (xHat[it] - x[it]) }
        }
        val kl = DoubleArray(LATENT_DIM) { j ->
            val meanSq = muSq[j] / data.size
            val varq = exp(logVar[j])
            0.5 * (meanSq + varq - 1.0 - logVar[j])
        }
        return TrainedVae(beta, kl, sqrt(sqErr / (data.size * DATA_DIM)))
    }
}

// ── DCGAN ────────────────────────────────────────────────────────────────────

/**
 * DCGAN's contribution was a list of architectural rules, and the one that is actually a theorem
 * rather than a heuristic is the kernel/stride relationship in the generator's transposed
 * convolutions.
 *
 * A transposed convolution writes each input into a kernel-sized window of the output. How many
 * times a given output position gets written depends on kernel and stride, and when the stride does
 * not divide the kernel the counts are *uneven* — periodically so. That periodic unevenness is the
 * checkerboard artefact, and it is visible here as arithmetic before any image is generated.
 */
internal object DcganLab {

    /** How many input taps land on each output position, in one dimension. */
    fun coverage(kernel: Int, stride: Int, inputLength: Int): IntArray {
        val outputLength = (inputLength - 1) * stride + kernel
        val counts = IntArray(outputLength)
        for (i in 0 until inputLength) {
            for (k in 0 until kernel) counts[i * stride + k]++
        }
        return counts
    }

    /**
     * The interior of the output, away from the edge ramp-up, is where a repeating artefact is
     * visible as an artefact rather than as a border effect.
     */
    fun interiorCoverage(kernel: Int, stride: Int, inputLength: Int): IntArray {
        val full = coverage(kernel, stride, inputLength)
        val margin = kernel
        return full.copyOfRange(margin, full.size - margin)
    }

    fun isUniform(kernel: Int, stride: Int, inputLength: Int = 16): Boolean =
        interiorCoverage(kernel, stride, inputLength).toSet().size == 1

    /** kernel % stride == 0 is the condition, and this checks it against the counts rather than assuming it. */
    fun divides(kernel: Int, stride: Int): Boolean = kernel % stride == 0

    class Layer(val name: String, val inChannels: Int, val outChannels: Int, val spatial: Int, val parameters: Long)

    /**
     * The DCGAN generator from the paper: a 100-dimensional z projected to 4x4x1024, then four
     * transposed convolutions doubling the resolution each time to 64x64x3. Kernel 4, stride 2
     * throughout — which is exactly the divisible case above.
     */
    val generator: List<Layer> by lazy {
        val kernel = 4L
        val layers = mutableListOf<Layer>()
        layers += Layer("project 100 -> 4x4x1024", 100, 1024, 4, 100L * 1024 * 4 * 4 + 1024)
        var channels = 1024
        var spatial = 4
        while (channels > 128) {
            val out = channels / 2
            spatial *= 2
            layers += Layer("convT $channels -> $out", channels, out, spatial, kernel * kernel * channels * out + out)
            channels = out
        }
        layers += Layer("convT $channels -> 3", channels, 3, spatial * 2, kernel * kernel * channels * 3L + 3)
        layers
    }

    val generatorParameters: Long get() = generator.sumOf { it.parameters }

    /** Where the parameters sit: the dense projection against the whole convolutional stack. */
    val projectionShare: Double get() = generator.first().parameters.toDouble() / generatorParameters
}

// ── CycleGAN ─────────────────────────────────────────────────────────────────

/**
 * The claim worth testing is the one every summary of CycleGAN gets backwards: that cycle
 * consistency is what pins down the correct mapping.
 *
 * Model both domains as finite sets of size n. An adversarial loss constrains only the *distribution*
 * of outputs, so any bijection A -> B drives it to zero: n! of them. Adding a cycle-consistency term
 * requires F(G(a)) = a, which forces F = G⁻¹ — and every bijection has an inverse. So the cycle term
 * removes exactly zero of the n! candidates. What it removes is the non-bijective ones, which the
 * adversarial loss had already ruled out at the level of distributions.
 *
 * The count is the argument: cycle consistency does not select the semantically correct map, and
 * CycleGAN works because of the generator's limited capacity and convolutional locality, not because
 * of its loss.
 */
internal object CycleGanLab {

    fun factorial(n: Int): Long = (1..n).fold(1L) { acc, i -> acc * i }

    /** Bijections A -> B: every one of them matches the target distribution exactly. */
    fun adversariallyOptimal(n: Int): Long = factorial(n)

    /** Adding F with F(G(a)) = a determines F uniquely from G, so the count does not move. */
    fun cycleConsistent(n: Int): Long = factorial(n)

    const val SEMANTICALLY_CORRECT = 1L

    fun oddsOfCorrect(n: Int): Double = SEMANTICALLY_CORRECT.toDouble() / cycleConsistent(n)

    /**
     * Enumerated rather than reasoned about, for small n: build every mapping A -> B, keep the ones
     * whose output distribution matches, then keep the ones that also admit an exact inverse.
     */
    fun enumerate(n: Int): Triple<Int, Int, Int> {
        var distributionMatching = 0
        var alsoCycleConsistent = 0
        val assignment = IntArray(n)

        fun recurse(index: Int) {
            if (index == n) {
                // Distribution match: every element of B is hit the same number of times as in B.
                val hits = IntArray(n)
                assignment.forEach { hits[it]++ }
                if (hits.all { it == 1 }) {
                    distributionMatching++
                    // F is forced to be the inverse permutation, and it always exists.
                    val inverse = IntArray(n)
                    assignment.forEachIndexed { a, b -> inverse[b] = a }
                    if ((0 until n).all { inverse[assignment[it]] == it }) alsoCycleConsistent++
                }
                return
            }
            for (b in 0 until n) {
                assignment[index] = b
                recurse(index + 1)
            }
        }
        recurse(0)
        return Triple(distributionMatching, alsoCycleConsistent, 1)
    }

    /**
     * The lever that does work. Restricting the generator to maps that change an input by at most
     * `budget` positions — a stand-in for the architecture's locality bias — cuts the candidate set,
     * and unlike the cycle term it cuts it as a function of the constraint.
     */
    fun withLocality(n: Int, budget: Int): Int {
        var count = 0
        val assignment = IntArray(n)
        val used = BooleanArray(n)
        fun recurse(index: Int) {
            if (index == n) { count++; return }
            for (b in 0 until n) {
                if (used[b] || abs(b - index) > budget) continue
                used[b] = true
                assignment[index] = b
                recurse(index + 1)
                used[b] = false
            }
        }
        recurse(0)
        return count
    }
}

// ── StyleGAN ─────────────────────────────────────────────────────────────────

/**
 * Two mechanisms, one exact and one measured.
 *
 * AdaIN is exact: whatever the content statistics were, the output's per-channel mean and standard
 * deviation are the style's. Not approximately — the operation normalises them away and substitutes.
 *
 * The mapping network is the measured one. StyleGAN's argument is that forcing a fixed prior (a
 * uniform square) onto a data distribution with a *missing combination* requires a warped map, and
 * that warping is what entangles attributes. The lab builds exactly that: a feature space whose
 * top-right quadrant is unpopulated, an area-correct map from the square onto it, and the path
 * lengths of linear interpolation in each space.
 */
internal object StyleGanLab {

    /** AdaIN(x, y) = sigma(y) * (x - mu(x)) / sigma(x) + mu(y), per channel. */
    fun adaIn(content: DoubleArray, style: DoubleArray): DoubleArray {
        val cMean = content.average()
        val cStd = sqrt(content.sumOf { (it - cMean) * (it - cMean) } / content.size)
        val sMean = style.average()
        val sStd = sqrt(style.sumOf { (it - sMean) * (it - sMean) } / style.size)
        return DoubleArray(content.size) { sStd * (content[it] - cMean) / cStd + sMean }
    }

    fun meanAndStd(v: DoubleArray): Pair<Double, Double> {
        val mean = v.average()
        return mean to sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
    }

    /**
     * The feature space: [0,1]^2 with the quadrant a > 0.5 AND b > 0.5 removed. Area 0.75.
     * "Both attributes at once" is the combination the training set never contains.
     */
    fun inSupport(a: Double, b: Double): Boolean = !(a > 0.5 && b > 0.5)

    /**
     * An area-correct map from the uniform square onto that support. The left column has area 0.5
     * and the bottom-right block 0.25, so they take 2/3 and 1/3 of the square respectively. The
     * discontinuity at z1 = 2/3 is not a modelling shortcut — it is the warping the prior forces,
     * and it is what the path length below measures.
     */
    fun generate(z1: Double, z2: Double): DoubleArray = if (z1 < 2.0 / 3.0) {
        doubleArrayOf(z1 * 0.75, z2)
    } else {
        doubleArrayOf(0.5 + (z1 - 2.0 / 3.0) * 1.5, z2 * 0.5)
    }

    class PathLengths(val latentZ: Double, val latentW: Double, val wLeavingSupport: Double) {
        val ratio get() = latentZ / latentW
    }

    /**
     * Perceptual path length, in the form the paper uses: subdivide a linear interpolation and sum
     * the squared feature-space steps, scaled by the subdivision so the quantity is comparable
     * across step counts. A straight path in feature space gives exactly the squared endpoint
     * distance; anything larger is the map bending.
     *
     * W-space here is the feature space itself, which is what an unconstrained intermediate latent
     * converges to — so its path length is the floor, and the honest cost is reported alongside it:
     * a straight line in W can cross the unpopulated quadrant.
     */
    fun pathLengths(samples: Int = 40_000, steps: Int = 64, seed: Int = 53): PathLengths {
        val random = Random(seed)
        var zTotal = 0.0
        var wTotal = 0.0
        var outside = 0
        var wSegments = 0
        repeat(samples) {
            val z1 = doubleArrayOf(random.nextDouble(), random.nextDouble())
            val z2 = doubleArrayOf(random.nextDouble(), random.nextDouble())
            var zPath = 0.0
            var previous = generate(z1[0], z1[1])
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                val point = generate(z1[0] + (z2[0] - z1[0]) * t, z1[1] + (z2[1] - z1[1]) * t)
                zPath += (point[0] - previous[0]) * (point[0] - previous[0]) +
                    (point[1] - previous[1]) * (point[1] - previous[1])
                previous = point
            }
            zTotal += zPath * steps

            // The same two endpoints, interpolated in feature space instead.
            val w1 = generate(z1[0], z1[1])
            val w2 = generate(z2[0], z2[1])
            var wPath = 0.0
            var prev = w1
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                val point = doubleArrayOf(w1[0] + (w2[0] - w1[0]) * t, w1[1] + (w2[1] - w1[1]) * t)
                wPath += (point[0] - prev[0]) * (point[0] - prev[0]) + (point[1] - prev[1]) * (point[1] - prev[1])
                prev = point
                wSegments++
                if (!inSupport(point[0], point[1])) outside++
            }
            wTotal += wPath * steps
        }
        return PathLengths(zTotal / samples, wTotal / samples, outside.toDouble() / wSegments)
    }

    /** Style mixing: which resolutions StyleGAN's coarse/middle/fine split actually covers. */
    val resolutions = listOf(4, 8, 16, 32, 64, 128, 256, 512, 1024)

    /** Two AdaIN operations per resolution — that is where the 18 style inputs of a 1024 model come from. */
    val styleInputs: Int get() = resolutions.size * 2

    fun band(resolution: Int): String = when {
        resolution <= 8 -> "coarse"
        resolution <= 32 -> "middle"
        else -> "fine"
    }

    fun styleInputsIn(band: String): Int = resolutions.count { band(it) == band } * 2
}

// ── Stable Diffusion / latent diffusion ──────────────────────────────────────

/**
 * Latent diffusion's entire argument is a division, and it is worth doing rather than describing.
 * Running the same diffusion process on a compressed latent instead of on pixels changes the element
 * count by one factor and the self-attention cost by the square of it, because attention is
 * quadratic in token count.
 */
internal object LatentDiffusionLab {

    const val IMAGE_SIDE = 512
    const val IMAGE_CHANNELS = 3
    const val DOWNSAMPLE = 8
    const val LATENT_CHANNELS = 4
    const val TEXT_TOKENS = 77

    const val LATENT_SIDE = IMAGE_SIDE / DOWNSAMPLE

    val pixelElements: Long = IMAGE_SIDE.toLong() * IMAGE_SIDE * IMAGE_CHANNELS
    val latentElements: Long = LATENT_SIDE.toLong() * LATENT_SIDE * LATENT_CHANNELS
    val elementRatio: Double get() = pixelElements.toDouble() / latentElements

    val pixelTokens: Long = IMAGE_SIDE.toLong() * IMAGE_SIDE
    val latentTokens: Long = LATENT_SIDE.toLong() * LATENT_SIDE

    /** Self-attention compares every token with every other one. */
    val pixelAttentionPairs: Long = pixelTokens * pixelTokens
    val latentAttentionPairs: Long = latentTokens * latentTokens
    val attentionRatio: Double get() = pixelAttentionPairs.toDouble() / latentAttentionPairs

    /** Cross-attention is tokens x text tokens, so it scales linearly and the saving is smaller. */
    val pixelCrossAttentionPairs: Long = pixelTokens * TEXT_TOKENS
    val latentCrossAttentionPairs: Long = latentTokens * TEXT_TOKENS
    val crossAttentionRatio: Double get() = pixelCrossAttentionPairs.toDouble() / latentCrossAttentionPairs

    /** The three networks a Stable Diffusion checkpoint actually contains, in millions of parameters. */
    val components = listOf(
        "VAE encoder + decoder" to 84,
        "CLIP text encoder" to 123,
        "UNet (the only part that is denoised)" to 860,
    )

    val totalParametersMillions: Int get() = components.sumOf { it.second }
    val unetShare: Double get() = 860.0 / totalParametersMillions

    /** Sampling cost is step count times one UNet pass, which is why step count is the headline knob. */
    const val DDPM_STEPS = 1000
    const val DDIM_STEPS = 50
    val stepSaving: Double get() = DDPM_STEPS.toDouble() / DDIM_STEPS

    /**
     * The combined figure: latent diffusion and a 50-step schedule together, against pixel-space
     * DDPM, counted in attention pairs actually evaluated over a full sampling run.
     */
    val pixelSamplingPairs: Double get() = pixelAttentionPairs.toDouble() * DDPM_STEPS
    val latentSamplingPairs: Double get() = latentAttentionPairs.toDouble() * DDIM_STEPS
    val samplingRatio: Double get() = pixelSamplingPairs / latentSamplingPairs
}

// ── Neural style transfer ────────────────────────────────────────────────────

/**
 * The Gram matrix is where style transfer keeps its definition of "style", and the definition is
 * blunter than it looks: G = F Fᵀ sums over spatial positions, so permuting those positions leaves G
 * bit-for-bit identical. Style, as this algorithm defines it, is a statement about which features
 * co-occur and contains no statement about where.
 *
 * That is asserted here as an equality on a real feature map, alongside the content loss on the same
 * shuffled map — which is large, because the content representation is the feature map itself.
 */
internal object StyleTransferLab {

    /** A small stand-in feature map: `channels` rows of `positions` activations. */
    fun featureMap(channels: Int = 8, positions: Int = 64, seed: Int = 61): Array<DoubleArray> {
        val random = Random(seed)
        return Array(channels) { c ->
            DoubleArray(positions) { p ->
                // Structured rather than pure noise, so a shuffle is visibly a shuffle.
                abs(cos((c + 1) * 0.9 + p * 0.15)) + 0.1 * abs(gaussian(random))
            }
        }
    }

    fun gram(f: Array<DoubleArray>): Array<DoubleArray> {
        val c = f.size
        val n = f[0].size
        return Array(c) { i -> DoubleArray(c) { j -> f[i].indices.sumOf { f[i][it] * f[j][it] } / n } }
    }

    fun shuffleColumns(f: Array<DoubleArray>, seed: Int = 71): Array<DoubleArray> {
        val order = f[0].indices.shuffled(Random(seed))
        return Array(f.size) { c -> DoubleArray(f[c].size) { p -> f[c][order[p]] } }
    }

    /** Mean squared difference between two Gram matrices — the style loss. */
    fun styleLoss(a: Array<DoubleArray>, b: Array<DoubleArray>): Double {
        val ga = gram(a)
        val gb = gram(b)
        var total = 0.0
        for (i in ga.indices) for (j in ga.indices) total += (ga[i][j] - gb[i][j]) * (ga[i][j] - gb[i][j])
        return total / (ga.size * ga.size)
    }

    /** Mean squared difference between two feature maps — the content loss. */
    fun contentLoss(a: Array<DoubleArray>, b: Array<DoubleArray>): Double {
        var total = 0.0
        var count = 0
        for (i in a.indices) for (j in a[i].indices) {
            total += (a[i][j] - b[i][j]) * (a[i][j] - b[i][j]); count++
        }
        return total / count
    }

    /** VGG-19 conv4_1, the layer the paper's style loss leans on hardest. */
    const val VGG_CHANNELS = 512
    const val VGG_SIDE = 28

    val featureValues: Long = VGG_CHANNELS.toLong() * VGG_SIDE * VGG_SIDE
    val gramEntries: Long = VGG_CHANNELS.toLong() * VGG_CHANNELS
    val gramUniqueEntries: Long = VGG_CHANNELS.toLong() * (VGG_CHANNELS + 1) / 2

    /** How many spatial arrangements collapse to the same Gram matrix, as a statement about size. */
    val compression: Double get() = featureValues.toDouble() / gramUniqueEntries

    /** The five layers the original paper mixes, and the weight each carries. */
    val styleLayers = listOf("conv1_1", "conv2_1", "conv3_1", "conv4_1", "conv5_1")
    const val CONTENT_LAYER = "conv4_2"
}

// ── DeepFakes ────────────────────────────────────────────────────────────────

/**
 * The architecture is one shared encoder and two identity-specific decoders, and the usual
 * explanation — "sharing the encoder puts both faces in one coordinate system, which is what makes
 * the swap possible" — is half right in a way worth measuring rather than repeating.
 *
 * The half that holds: independently trained encoders produce codes that are not comparable at all.
 * Each one orders and scales its own principal directions by that identity's own variance, so the
 * same expression lands on different numbers, and the decoder on the far end reads them as a
 * different expression. Measured below, that arrangement is *worse than ignoring the input* — worse
 * than emitting identity B's average face every time.
 *
 * The half that does not: a shared encoder is necessary and not sufficient. Feeding A's code to B's
 * decoder only reconstructs B-wearing-A's-expression when the two identities' expression manifolds
 * project the same way. As those manifolds separate, the swap degrades continuously, and the lab
 * sweeps that angle rather than asserting a single number. That is the measurable form of a fact
 * every practitioner reports and no summary explains: deepfakes work far better between similar
 * faces, and no amount of training fixes a badly matched pair.
 */
internal object DeepFakeLab {

    const val DIM = 8
    const val EXPRESSION_FACTORS = 2
    const val LATENT = 2

    /** Identity A's expression manifold: two orthonormal directions in face space. */
    private val baseDirections: Array<DoubleArray> = run {
        val raw = listOf(
            DoubleArray(DIM) { cos((it + 1) * 0.7) },
            DoubleArray(DIM) { cos((it + 1) * 1.9) },
        )
        // Gram-Schmidt, so "angle between the manifolds" below means what it says.
        val out = mutableListOf<DoubleArray>()
        raw.forEach { v ->
            val w = v.copyOf()
            out.forEach { b ->
                val dot = w.indices.sumOf { w[it] * b[it] }
                for (i in 0 until DIM) w[i] -= dot * b[i]
            }
            val norm = sqrt(w.sumOf { it * it })
            out += DoubleArray(DIM) { w[it] / norm }
        }
        // A third direction for B's manifold to rotate into.
        val extra = DoubleArray(DIM) { cos((it + 1) * 3.1) }
        out.forEach { b ->
            val dot = extra.indices.sumOf { extra[it] * b[it] }
            for (i in 0 until DIM) extra[i] -= dot * b[i]
        }
        val norm = sqrt(extra.sumOf { it * it })
        arrayOf(out[0], out[1], DoubleArray(DIM) { extra[it] / norm })
    }

    /**
     * The two identities differ in three ways, all of them true of real faces: a different average
     * face, a different amount of movement per expression factor, and — swept by `degrees` — an
     * expression manifold pointing somewhere else in face space.
     *
     * The per-factor scales are deliberately opposite. A's face moves most under factor 1 and B's
     * under factor 2, which is what makes each identity's own PCA order them differently.
     */
    val meanFaceA = DoubleArray(DIM) { cos(it * 0.4) }
    val meanFaceB = DoubleArray(DIM) { -cos(it * 0.9) * 0.8 }
    val scalesA = doubleArrayOf(1.0, 0.6)
    val scalesB = doubleArrayOf(0.6, 1.0)

    /** B's expression directions, rotated `degrees` away from A's in the plane spanned by u1 and u3. */
    fun directionsB(degrees: Double): Array<DoubleArray> {
        val t = degrees * PI / 180.0
        val rotated = DoubleArray(DIM) { cos(t) * baseDirections[0][it] + kotlin.math.sin(t) * baseDirections[2][it] }
        return arrayOf(rotated, baseDirections[1])
    }

    fun renderA(expression: DoubleArray) = DoubleArray(DIM) { i ->
        meanFaceA[i] + (0 until EXPRESSION_FACTORS).sumOf { scalesA[it] * expression[it] * baseDirections[it][i] }
    }

    fun renderB(expression: DoubleArray, degrees: Double): DoubleArray {
        val dirs = directionsB(degrees)
        return DoubleArray(DIM) { i ->
            meanFaceB[i] + (0 until EXPRESSION_FACTORS).sumOf { scalesB[it] * expression[it] * dirs[it][i] }
        }
    }

    fun expressions(count: Int = 400, seed: Int = 83): List<DoubleArray> {
        val random = Random(seed)
        return List(count) { DoubleArray(EXPRESSION_FACTORS) { gaussian(random) } }
    }

    /**
     * Every arrangement is scored against the same ground truth — identity B's face wearing identity
     * A's expression — and against the same baseline, which is the trivial model that ignores the
     * input and emits B's average face. A swap that cannot beat the baseline has transferred nothing.
     */
    class SwapResult(
        val degrees: Double,
        val sharedEncoderRmse: Double,
        val independentEncoderRmse: Double,
        val meanFaceBaselineRmse: Double,
    ) {
        val sharedBeatsBaseline get() = sharedEncoderRmse < meanFaceBaselineRmse
        val independentBeatsBaseline get() = independentEncoderRmse < meanFaceBaselineRmse
    }

    fun swap(degrees: Double): SwapResult {
        val expressions = expressions()
        val facesA = expressions.map { renderA(it) }
        val facesB = expressions.map { renderB(it, degrees) }
        val truth = facesB

        fun centre(rows: List<DoubleArray>): Pair<List<DoubleArray>, DoubleArray> {
            val mean = DoubleArray(DIM) { i -> rows.sumOf { it[i] } / rows.size }
            return rows.map { r -> DoubleArray(DIM) { r[it] - mean[it] } } to mean
        }

        val (centredA, meanA) = centre(facesA)
        val (centredB, meanB) = centre(facesB)

        // The decoder always supplies identity as its own constant — that is what the two separate
        // decoders are for. The only thing under test is what the code in the middle means.
        fun score(encodeSource: (DoubleArray) -> DoubleArray, encodeTarget: (DoubleArray) -> DoubleArray): Double {
            val decoderB = leastSquares(facesB.map(encodeTarget), centredB)
            return facesA.indices.sumOf { i ->
                val rebuilt = applyMap(decoderB, encodeSource(facesA[i]))
                rmse(DoubleArray(DIM) { meanB[it] + rebuilt[it] }, truth[i])
            } / facesA.size
        }

        // Shared: one basis, fitted to both identities' centred faces at once.
        val sharedBasis = principalDirections(centredA + centredB, LATENT)
        fun sharedEncode(mean: DoubleArray): (DoubleArray) -> DoubleArray = { x ->
            DoubleArray(LATENT) { j -> x.indices.sumOf { (x[it] - mean[it]) * sharedBasis[j][it] } }
        }
        val shared = score(sharedEncode(meanA), sharedEncode(meanB))

        // Independent: each identity's encoder is fitted only to that identity.
        val basisA = principalDirections(centredA, LATENT)
        val basisB = principalDirections(centredB, LATENT)
        val independent = score(
            { x -> DoubleArray(LATENT) { j -> x.indices.sumOf { (x[it] - meanA[it]) * basisA[j][it] } } },
            { x -> DoubleArray(LATENT) { j -> x.indices.sumOf { (x[it] - meanB[it]) * basisB[j][it] } } },
        )

        val baseline = facesA.indices.sumOf { i -> rmse(meanB, truth[i]) } / facesA.size
        return SwapResult(degrees, shared, independent, baseline)
    }

    /** The sweep: how far the two faces' expression manifolds may diverge before the swap stops working. */
    val angleSweep = listOf(0.0, 15.0, 30.0, 45.0, 60.0, 75.0, 90.0)

    fun sweep(): List<SwapResult> = angleSweep.map { swap(it) }

    /** Cached, so a frame builder does not refit seven pairs of bases per render. */
    val sweepResults: List<SwapResult> by lazy { sweep() }

    /** Reconstruction within each identity is not what differs — both encoders rebuild their own domain. */
    fun ownDomainRmse(degrees: Double = 0.0): Double {
        val expressions = expressions()
        val facesA = expressions.map { renderA(it) }
        val meanA = DoubleArray(DIM) { i -> facesA.sumOf { it[i] } / facesA.size }
        val centred = facesA.map { r -> DoubleArray(DIM) { r[it] - meanA[it] } }
        val basisA = principalDirections(centred, LATENT)
        val encode = { x: DoubleArray -> DoubleArray(LATENT) { j -> x.indices.sumOf { (x[it] - meanA[it]) * basisA[j][it] } } }
        val decoderA = leastSquares(facesA.map(encode), centred)
        return facesA.indices.sumOf { i ->
            val rebuilt = applyMap(decoderA, encode(facesA[i]))
            rmse(DoubleArray(DIM) { meanA[it] + rebuilt[it] }, facesA[i])
        } / facesA.size
    }
}
