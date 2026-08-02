package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

// ── C8 · Optimizers & Training (dl_optimizers) ───────────────────────────────
// Momentum, AdaGrad, RMSprop, Adam, AdamW, Learning Rate Schedulers, Cross-Entropy Loss, KL
// Divergence. The phase's last batch. Pinned by `OptimizerMathTest`.
//
// Six labs run real optimizer update rules on small fixed problems rather than describing them:
// the same ill-conditioned quadratic `gradient_descent_variants` already uses (Momentum, most of
// LrScheduler), a synthetic dense/sparse gradient stream that isolates what "adaptive" actually
// buys (AdaGrad, RMSprop), a single constant gradient run long enough to expose an exact identity
// (Adam's bias correction), and a two-parameter weight-decay comparison (AdamW). The two loss labs
// (CrossEntropy, KlDivergence) check identities instead: that softmax+cross-entropy's gradient is
// exactly prediction-minus-label, and that cross-entropy equals entropy plus KL divergence.

// ── Momentum ───────────────────────────────────────────────────────────────────

/**
 * Heavy-ball momentum on the same ill-conditioned quadratic `gradient_descent_variants` runs (20x
 * steeper in w1 than w2), swept over beta at a fixed learning rate. The question isn't "does
 * momentum help" -- `gradient_descent_variants` already answers that -- it's how much of beta is
 * free lunch and where it stops being one.
 */
internal object MomentumLab {
    private val curvature = doubleArrayOf(20.0, 0.4)
    private val start = doubleArrayOf(1.0, 1.6)
    const val LR = 0.012
    const val STEPS = 60
    val BETAS = listOf(0.0, 0.5, 0.9, 0.99)

    fun loss(w: DoubleArray): Double = 0.5 * (curvature[0] * w[0] * w[0] + curvature[1] * w[1] * w[1])
    private fun gradient(w: DoubleArray) = doubleArrayOf(curvature[0] * w[0], curvature[1] * w[1])

    class Run(val path: List<DoubleArray>) {
        val finalLoss: Double get() = loss(path.last())
        // The steep w1 axis collapses first, then oscillates -- the overshoot that matters is
        // after it has had a few steps to get there, not the monotonic first descent.
        val maxAbsW1AfterStep5: Double get() = path.drop(5).maxOf { abs(it[0]) }
    }

    private fun run(beta: Double): Run {
        var w = start.copyOf()
        val v = doubleArrayOf(0.0, 0.0)
        val path = mutableListOf(w.copyOf())
        repeat(STEPS) {
            val g = gradient(w)
            v[0] = beta * v[0] + g[0]
            v[1] = beta * v[1] + g[1]
            w = doubleArrayOf(w[0] - LR * v[0], w[1] - LR * v[1])
            path += w.copyOf()
        }
        return Run(path)
    }

    val runs: Map<Double, Run> by lazy { BETAS.associateWith { run(it) } }
}

// ── AdaGrad ────────────────────────────────────────────────────────────────────

/**
 * A dense feature (gradient magnitude 1, every step) beside a sparse one (magnitude 2, one step in
 * ten): AdaGrad's per-parameter rate divides by the square root of every squared gradient the
 * parameter has ever received, so a rare-but-large gradient and a frequent-but-small one accumulate
 * differently. Run long enough (2,000 steps) to also expose the failure the accumulation causes --
 * it never resets, so the rate keeps shrinking even after the loss has flattened out.
 */
internal object AdaGradLab {
    const val LR = 0.5
    const val EPS = 1e-8
    const val TOTAL_STEPS = 2000

    private fun denseGrad(t: Int) = 1.0
    private fun sparseGrad(t: Int) = if (t % 10 == 0) 2.0 else 0.0

    class Step(val t: Int, val gAccumDense: Double, val gAccumSparse: Double, val effRateDense: Double, val effRateSparse: Double)

    val history: List<Step> by lazy {
        var gA = 0.0
        var gB = 0.0
        (1..TOTAL_STEPS).map { t ->
            val ga = denseGrad(t)
            val gb = sparseGrad(t)
            gA += ga * ga
            if (gb != 0.0) gB += gb * gb
            Step(
                t = t,
                gAccumDense = gA,
                gAccumSparse = gB,
                effRateDense = LR / sqrt(gA + EPS),
                effRateSparse = if (gB > 0.0) LR / sqrt(gB + EPS) else LR,
            )
        }
    }

    fun at(t: Int): Step = history[t - 1]
    val sparseFirings: List<Step> get() = history.filter { it.t % 10 == 0 }
}

// ── RMSprop ────────────────────────────────────────────────────────────────────

/**
 * The identical dense/sparse stream AdaGrad runs, but the accumulator is an exponential moving
 * average instead of a running sum -- so it can go back down. That fixes AdaGrad's stall, at a real
 * cost checked here rather than waved at: the sparse feature's rate boost decays between firings
 * instead of compounding forever, so RMSprop keeps less of AdaGrad's benefit for rare features.
 */
internal object RmsPropLab {
    const val LR = 0.5
    const val EPS = 1e-8
    const val GAMMA = 0.9
    const val TOTAL_STEPS = 2000

    private fun denseGrad(t: Int) = 1.0
    private fun sparseGrad(t: Int) = if (t % 10 == 0) 2.0 else 0.0

    class Step(val t: Int, val eDense: Double, val eSparse: Double, val effRateDense: Double, val effRateSparse: Double)

    val history: List<Step> by lazy {
        var eA = 0.0
        var eB = 0.0
        (1..TOTAL_STEPS).map { t ->
            val ga = denseGrad(t)
            val gb = sparseGrad(t)
            eA = GAMMA * eA + (1 - GAMMA) * ga * ga
            eB = GAMMA * eB + (1 - GAMMA) * gb * gb
            Step(
                t = t,
                eDense = eA,
                eSparse = eB,
                effRateDense = LR / sqrt(eA + EPS),
                effRateSparse = LR / sqrt(eB + EPS),
            )
        }
    }

    fun at(t: Int): Step = history[t - 1]
}

// ── Adam ───────────────────────────────────────────────────────────────────────

/**
 * A single constant gradient, run through Adam's first- and second-moment EMAs. With a truly
 * constant input, the bias-corrected estimates recover the exact gradient at every step (not just
 * asymptotically) -- an identity, checked to floating-point tolerance -- while the uncorrected ones
 * wander well away from it before slowly settling to the same place correction gives for free from
 * step 1.
 */
internal object AdamLab {
    const val BETA1 = 0.9
    const val BETA2 = 0.999
    const val G = 2.0
    const val TOTAL_STEPS = 100

    class Step(val t: Int, val correctedRatio: Double, val uncorrectedRatio: Double)

    val history: List<Step> by lazy {
        var m = 0.0
        var v = 0.0
        (1..TOTAL_STEPS).map { t ->
            m = BETA1 * m + (1 - BETA1) * G
            v = BETA2 * v + (1 - BETA2) * G * G
            val mHat = m / (1 - Math.pow(BETA1, t.toDouble()))
            val vHat = v / (1 - Math.pow(BETA2, t.toDouble()))
            Step(
                t = t,
                correctedRatio = mHat / sqrt(vHat),
                uncorrectedRatio = m / sqrt(v),
            )
        }
    }

    fun at(t: Int): Step = history[t - 1]
}

// ── AdamW ──────────────────────────────────────────────────────────────────────

/**
 * Two parameters, warmed up with different gradient histories (magnitude 5 vs 0.5 for 300 steps,
 * so their second-moment accumulators land two orders of magnitude apart), then one weight-decay-
 * only step under each method. L2-in-Adam folds the decay into the gradient, so it gets divided by
 * the same per-parameter sqrt(v) as any gradient would -- decaying the low-variance parameter far
 * more than the high-variance one. AdamW's decay bypasses v entirely, so both parameters shrink by
 * exactly the same fraction.
 */
internal object AdamWLab {
    const val BETA1 = 0.9
    const val BETA2 = 0.999
    const val EPS = 1e-8
    const val LR = 0.1
    const val WD = 0.1
    const val WARM_STEPS = 300

    private fun warmupV(gradValue: Double): Double {
        var m = 0.0
        var v = 0.0
        repeat(WARM_STEPS) {
            m = BETA1 * m + (1 - BETA1) * gradValue
            v = BETA2 * v + (1 - BETA2) * gradValue * gradValue
        }
        return v
    }

    val vLargeHistory: Double by lazy { warmupV(5.0) }
    val vSmallHistory: Double by lazy { warmupV(0.5) }

    class DecayResult(val stepL2: Double, val stepDecoupled: Double)

    // A fresh decay-only step: no new data gradient (g = 0), so the only input is the decay term
    // itself. `vPrev` carries the warm-up history; `m` restarts at 0 because the data gradient that
    // built it up has stopped.
    fun decayStep(vPrev: Double, w: Double, t: Int = WARM_STEPS + 1): DecayResult {
        val gEff = WD * w
        val m = (1 - BETA1) * gEff
        val v = BETA2 * vPrev + (1 - BETA2) * gEff * gEff
        val mHat = m / (1 - Math.pow(BETA1, t.toDouble()))
        val vHat = v / (1 - Math.pow(BETA2, t.toDouble()))
        val stepL2 = LR * mHat / (sqrt(vHat) + EPS)
        val stepDecoupled = LR * WD * w
        return DecayResult(stepL2, stepDecoupled)
    }

    val resultLargeV: DecayResult by lazy { decayStep(vLargeHistory, 1.0) }
    val resultSmallV: DecayResult by lazy { decayStep(vSmallHistory, 1.0) }
}

// ── Learning Rate Schedulers ─────────────────────────────────────────────────

/**
 * Two experiments, because a schedule's case rests on two different claims. First: on the same
 * noise-free quadratic `gradient_descent_variants` and `momentum` use, decaying the rate is not
 * free -- it can leave the flat axis under-trained relative to a rate that never changes. Second: a
 * fixed disturbance added every step (standing in for gradient noise) gives a rate that never
 * decays a steady-state loss floor that scales as lr^2, which a decaying schedule can shrink and a
 * constant one cannot.
 */
internal object LrSchedulerLab {
    private val curvature = doubleArrayOf(20.0, 0.4)
    private val start = doubleArrayOf(1.0, 1.6)
    const val STEPS = 60
    const val BASE_LR = 0.09
    const val CONSTANT_LR = 0.05

    fun loss(w: DoubleArray): Double = 0.5 * (curvature[0] * w[0] * w[0] + curvature[1] * w[1] * w[1])
    private fun gradient(w: DoubleArray) = doubleArrayOf(curvature[0] * w[0], curvature[1] * w[1])

    private fun run(lrAt: (Int) -> Double): List<DoubleArray> {
        var w = start.copyOf()
        val path = mutableListOf(w.copyOf())
        for (t in 1..STEPS) {
            val g = gradient(w)
            val lr = lrAt(t)
            w = doubleArrayOf(w[0] - lr * g[0], w[1] - lr * g[1])
            path += w.copyOf()
        }
        return path
    }

    private fun constantSchedule(@Suppress("UNUSED_PARAMETER") t: Int) = CONSTANT_LR
    private fun stepDecaySchedule(t: Int) = BASE_LR * Math.pow(0.5, ((t - 1) / 20).toDouble())
    private fun cosineSchedule(t: Int) = BASE_LR * 0.5 * (1 + cos(Math.PI * (t - 1) / STEPS))
    private fun warmupCosineSchedule(t: Int): Double {
        val warm = 10
        return if (t <= warm) {
            BASE_LR * t / warm
        } else {
            val tt = t - warm
            val total = STEPS - warm
            BASE_LR * 0.5 * (1 + cos(Math.PI * tt / total))
        }
    }

    val constantPath: List<DoubleArray> by lazy { run(::constantSchedule) }
    val stepDecayPath: List<DoubleArray> by lazy { run(::stepDecaySchedule) }
    val cosinePath: List<DoubleArray> by lazy { run(::cosineSchedule) }
    val warmupCosinePath: List<DoubleArray> by lazy { run(::warmupCosineSchedule) }

    // Persistent-perturbation half: PAMP*sin(0.9t)*cos(0.31t) is a fixed, deterministic disturbance
    // added to the true gradient every step -- bounded and non-repeating over the run, standing in
    // for noise without depending on a random seed for a reproducible result.
    const val PERTURBED_STEPS = 300
    const val PERTURBED_START = 2.0
    const val PERTURBED_AMPLITUDE = 0.6
    const val PERTURBED_LR_HIGH = 0.05
    const val PERTURBED_LR_LOW = 0.02

    private fun perturbation(t: Int) = PERTURBED_AMPLITUDE * sin(t * 0.9) * cos(t * 0.31)

    private fun runPerturbed(lrAt: (Int) -> Double): List<Double> {
        var w = PERTURBED_START
        val losses = mutableListOf<Double>()
        for (t in 1..PERTURBED_STEPS) {
            val g = w + perturbation(t)
            w -= lrAt(t) * g
            losses += 0.5 * w * w
        }
        return losses
    }

    private fun perturbedConstantSchedule(@Suppress("UNUSED_PARAMETER") t: Int) = PERTURBED_LR_HIGH
    private fun perturbedCosineSchedule(t: Int) =
        PERTURBED_LR_LOW + (PERTURBED_LR_HIGH - PERTURBED_LR_LOW) * 0.5 * (1 + cos(Math.PI * (t - 1) / PERTURBED_STEPS))

    val perturbedConstantLosses: List<Double> by lazy { runPerturbed(::perturbedConstantSchedule) }
    val perturbedCosineLosses: List<Double> by lazy { runPerturbed(::perturbedCosineSchedule) }
    val constantTailAvg: Double get() = perturbedConstantLosses.takeLast(40).average()
    val cosineTailAvg: Double get() = perturbedCosineLosses.takeLast(40).average()
}

// ── Cross-Entropy Loss ───────────────────────────────────────────────────────

/**
 * Softmax feeding cross-entropy has a famously clean gradient -- prediction minus one-hot label --
 * checked here two ways on the same logits: once by the direct formula, once by multiplying the
 * loss gradient through softmax's actual Jacobian by hand. They agree to floating-point tolerance,
 * which is the whole reason the combination is used everywhere instead of computing the softmax
 * Jacobian at every training step.
 */
internal object CrossEntropyLab {
    private fun softmax(z: DoubleArray): DoubleArray {
        val m = z.max()
        val exps = z.map { exp(it - m) }
        val sum = exps.sum()
        return DoubleArray(z.size) { exps[it] / sum }
    }

    class Case(val z: DoubleArray, val trueClass: Int) {
        val p: DoubleArray = softmax(z)
        val loss: Double = -ln(p[trueClass])
        val oneHot: DoubleArray = DoubleArray(z.size) { if (it == trueClass) 1.0 else 0.0 }

        /** The known-clean formula: dL/dz = p - y. */
        val gradDirect: DoubleArray = DoubleArray(z.size) { p[it] - oneHot[it] }

        /** The same gradient the long way: dL/dp (only nonzero at the true class) times softmax's Jacobian. */
        val gradViaJacobian: DoubleArray by lazy {
            val dLdP = DoubleArray(z.size) { if (it == trueClass) -1.0 / p[trueClass] else 0.0 }
            DoubleArray(z.size) { j ->
                (z.indices).sumOf { i -> dLdP[i] * p[i] * ((if (i == j) 1.0 else 0.0) - p[j]) }
            }
        }

        val maxGradDiff: Double get() = z.indices.maxOf { abs(gradDirect[it] - gradViaJacobian[it]) }
    }

    // Same logits both times -- only which class is "true" changes, isolating what confidence
    // costs when it turns out to be pointed the wrong way.
    val confidentCorrect = Case(doubleArrayOf(2.0, 1.0, 0.1), trueClass = 0)
    val misclassified = Case(doubleArrayOf(2.0, 1.0, 0.1), trueClass = 2)
}

// ── KL Divergence ──────────────────────────────────────────────────────────────

/**
 * Two experiments. First, a fixed four-outcome P and Q: KL(P||Q) and KL(Q||P) computed directly,
 * plus the identity cross-entropy(P,Q) = entropy(P) + KL(P||Q), checked rather than quoted. Second,
 * a bimodal target and a single Gaussian fit to it by grid search in each direction -- forward KL
 * (P||Q) is minimized by a wide Gaussian that covers both modes at once; reverse KL (Q||P) is
 * minimized by a narrow one that locks onto a single mode exactly.
 */
internal object KlDivergenceLab {
    val distP = doubleArrayOf(0.5, 0.3, 0.15, 0.05)
    val distQ = doubleArrayOf(0.25, 0.25, 0.25, 0.25)

    fun kl(a: DoubleArray, b: DoubleArray): Double =
        a.indices.sumOf { i -> if (a[i] > 0.0) a[i] * ln(a[i] / b[i]) else 0.0 }

    fun entropy(a: DoubleArray): Double = -a.sumOf { if (it > 0.0) it * ln(it) else 0.0 }
    fun crossEntropy(a: DoubleArray, b: DoubleArray): Double = -a.indices.sumOf { i -> a[i] * ln(b[i]) }

    val klPQ: Double by lazy { kl(distP, distQ) }
    val klQP: Double by lazy { kl(distQ, distP) }
    val entropyP: Double by lazy { entropy(distP) }
    val crossEntropyPQ: Double by lazy { crossEntropy(distP, distQ) }

    private fun normalPdf(x: Double, mu: Double, sigma: Double): Double {
        val z = (x - mu) / sigma
        return (1.0 / (sigma * sqrt(2.0 * Math.PI))) * exp(-0.5 * z * z)
    }

    private const val GRID_LO = -6.0
    private const val GRID_HI = 6.0
    private const val GRID_N = 201
    private val xs = DoubleArray(GRID_N) { GRID_LO + (GRID_HI - GRID_LO) * it / (GRID_N - 1) }
    private val dx = xs[1] - xs[0]

    private fun bimodal(x: Double) = 0.5 * normalPdf(x, -2.5, 0.8) + 0.5 * normalPdf(x, 2.5, 0.8)

    val targetP: DoubleArray by lazy {
        val raw = DoubleArray(GRID_N) { bimodal(xs[it]) * dx }
        val s = raw.sum()
        DoubleArray(GRID_N) { raw[it] / s }
    }

    private fun gaussianQ(mu: Double, sigma: Double): DoubleArray {
        val raw = DoubleArray(GRID_N) { normalPdf(xs[it], mu, sigma) * dx }
        val s = raw.sum()
        return DoubleArray(GRID_N) { raw[it] / s }
    }

    class FitResult(val mu: Double, val sigma: Double, val divergence: Double)

    private fun bestFit(direction: (DoubleArray, DoubleArray) -> Double): FitResult {
        var best: FitResult? = null
        var mu10 = -50
        while (mu10 <= 50) {
            val mu = mu10 / 10.0
            var sigma10 = 3
            while (sigma10 <= 40) {
                val sigma = sigma10 / 10.0
                val q = gaussianQ(mu, sigma)
                val d = direction(targetP, q)
                if (best == null || d < best!!.divergence) best = FitResult(mu, sigma, d)
                sigma10++
            }
            mu10++
        }
        return best!!
    }

    /** Minimizing KL(P||Q) over Q -- mode-covering: the winner has to put mass everywhere P does. */
    val forwardFit: FitResult by lazy { bestFit { p, q -> kl(p, q) } }

    /** Minimizing KL(Q||P) over Q -- mode-seeking: the winner can ignore whichever mode it drops. */
    val reverseFit: FitResult by lazy { bestFit { p, q -> kl(q, p) } }
}
