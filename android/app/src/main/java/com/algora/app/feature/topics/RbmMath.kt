package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.random.Random

// ── B10 · Restricted Boltzmann Machines + Deep Belief Networks ──────────────
// Pinned by `RbmMathTest`.
//
// An RBM has no visible-visible or hidden-hidden connections, only a weight between every visible
// unit and every hidden one, so both conditionals are simple sigmoids and Gibbs sampling alternates
// cleanly between the two layers. Contrastive Divergence (CD-1) approximates the log-likelihood
// gradient with a single up-down-up pass instead of running the chain to equilibrium — trained here
// for real, on data with two genuine underlying categories the model is never told about.
//
// A DBN stacks RBMs and trains them greedily, one at a time: the first RBM's hidden-layer
// activations become the second RBM's visible data. The claim worth measuring is whether that
// unsupervised, layer-by-layer pretraining buys separation at the top layer before any supervised
// signal has touched the network at all.

private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

internal class Rbm(val visibleDim: Int, val hiddenDim: Int, seed: Int) {
    private val random = Random(seed)
    val w: Array<DoubleArray> = Array(hiddenDim) { DoubleArray(visibleDim) { 0.1 * gaussian(random) } }
    val visibleBias: DoubleArray = DoubleArray(visibleDim)
    val hiddenBias: DoubleArray = DoubleArray(hiddenDim)

    private fun gaussian(r: Random): Double {
        val u1 = r.nextDouble().coerceAtLeast(1e-12)
        val u2 = r.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    fun hiddenProbs(v: DoubleArray): DoubleArray = DoubleArray(hiddenDim) { j ->
        sigmoid(hiddenBias[j] + (0 until visibleDim).sumOf { w[j][it] * v[it] })
    }

    fun visibleProbs(h: DoubleArray): DoubleArray = DoubleArray(visibleDim) { i ->
        sigmoid(visibleBias[i] + (0 until hiddenDim).sumOf { w[it][i] * h[it] })
    }

    fun sampleBernoulli(p: DoubleArray, random: Random): DoubleArray = DoubleArray(p.size) { if (random.nextDouble() < p[it]) 1.0 else 0.0 }

    /** One CD-1 update on a single visible sample. */
    fun cd1Step(v0: DoubleArray, learningRate: Double, random: Random) {
        val h0Probs = hiddenProbs(v0)
        val h0Sample = sampleBernoulli(h0Probs, random)
        val v1Probs = visibleProbs(h0Sample)
        val h1Probs = hiddenProbs(v1Probs)

        for (j in 0 until hiddenDim) {
            for (i in 0 until visibleDim) {
                w[j][i] += learningRate * (h0Probs[j] * v0[i] - h1Probs[j] * v1Probs[i])
            }
            hiddenBias[j] += learningRate * (h0Probs[j] - h1Probs[j])
        }
        for (i in 0 until visibleDim) {
            visibleBias[i] += learningRate * (v0[i] - v1Probs[i])
        }
    }

    fun reconstruction(v0: DoubleArray, random: Random): DoubleArray = visibleProbs(sampleBernoulli(hiddenProbs(v0), random))
}

internal object RbmLab {
    const val VISIBLE_DIM = 6
    const val HIDDEN_DIM = 3

    // Two prototype patterns, six bits each, visibly different.
    val prototypeA = doubleArrayOf(1.0, 1.0, 1.0, 0.0, 0.0, 0.0)
    val prototypeB = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

    /** A noisy draw from one of the two prototypes -- each bit flipped independently with `flipProb`. */
    fun sample(prototype: DoubleArray, flipProb: Double, random: Random): DoubleArray =
        DoubleArray(prototype.size) { i -> if (random.nextDouble() < flipProb) 1.0 - prototype[i] else prototype[i] }

    fun dataset(perClass: Int, seed: Int): List<Pair<DoubleArray, Boolean>> {
        val random = Random(seed)
        val a = List(perClass) { sample(prototypeA, 0.15, random) to true }
        val b = List(perClass) { sample(prototypeB, 0.15, random) to false }
        return (a + b).shuffled(random)
    }

    val trainData = dataset(perClass = 60, seed = 3)

    fun train(epochs: Int = 60, learningRate: Double = 0.1, seed: Int = 41): Rbm {
        val rbm = Rbm(VISIBLE_DIM, HIDDEN_DIM, seed = seed)
        val random = Random(seed + 1)
        repeat(epochs) {
            trainData.forEach { (v, _) -> rbm.cd1Step(v, learningRate, random) }
        }
        return rbm
    }

    val trainedRbm: Rbm by lazy { train() }
    val untrainedRbm: Rbm by lazy { Rbm(VISIBLE_DIM, HIDDEN_DIM, seed = 41) }

    fun reconstructionError(rbm: Rbm, seed: Int = 99): Double {
        val random = Random(seed)
        return trainData.map { (v, _) ->
            val recon = rbm.reconstruction(v, random)
            v.indices.sumOf { kotlin.math.abs(v[it] - recon[it]) } / v.size
        }.average()
    }

    /** Mean hidden-activation vector for one class, using probabilities (no sampling noise). */
    fun meanHiddenActivation(rbm: Rbm, isClassA: Boolean): DoubleArray {
        val rows = trainData.filter { it.second == isClassA }.map { rbm.hiddenProbs(it.first) }
        return DoubleArray(HIDDEN_DIM) { j -> rows.sumOf { it[j] } / rows.size }
    }

    fun separation(rbm: Rbm): Double {
        val a = meanHiddenActivation(rbm, true)
        val b = meanHiddenActivation(rbm, false)
        return kotlin.math.sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })
    }
}

internal object DbnLab {
    const val LAYER2_DIM = 2

    /** Layer 2, greedily pretrained: RBM1 on the raw data, RBM2 on RBM1's hidden probabilities. */
    fun greedyStack(seed: Int = 7): Pair<Rbm, Rbm> {
        val rbm1 = RbmLab.train(epochs = 60, seed = seed)
        val layer1Data = RbmLab.trainData.map { (v, label) -> rbm1.hiddenProbs(v) to label }
        val rbm2 = Rbm(RbmLab.HIDDEN_DIM, LAYER2_DIM, seed = seed + 100)
        val random = Random(seed + 200)
        repeat(60) {
            layer1Data.forEach { (h, _) -> rbm2.cd1Step(h, learningRate = 0.1, random) }
        }
        return rbm1 to rbm2
    }

    fun randomStack(seed: Int = 7): Pair<Rbm, Rbm> =
        Rbm(RbmLab.VISIBLE_DIM, RbmLab.HIDDEN_DIM, seed = seed) to Rbm(RbmLab.HIDDEN_DIM, LAYER2_DIM, seed = seed + 100)

    /** Top-layer (layer 2) separation between the two classes, for a given two-RBM stack. */
    fun topLayerSeparation(rbm1: Rbm, rbm2: Rbm): Double {
        val hiddenA = RbmLab.trainData.filter { it.second }.map { rbm2.hiddenProbs(rbm1.hiddenProbs(it.first)) }
        val hiddenB = RbmLab.trainData.filter { !it.second }.map { rbm2.hiddenProbs(rbm1.hiddenProbs(it.first)) }
        fun mean(rows: List<DoubleArray>) = DoubleArray(LAYER2_DIM) { j -> rows.sumOf { it[j] } / rows.size }
        val a = mean(hiddenA)
        val b = mean(hiddenB)
        return kotlin.math.sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })
    }

    val greedy: Pair<Rbm, Rbm> by lazy { greedyStack() }
    val random: Pair<Rbm, Rbm> by lazy { randomStack() }

    val greedySeparation: Double by lazy { topLayerSeparation(greedy.first, greedy.second) }
    val randomSeparation: Double by lazy { topLayerSeparation(random.first, random.second) }
}
