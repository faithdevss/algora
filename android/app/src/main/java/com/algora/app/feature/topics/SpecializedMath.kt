package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

// ── C9 · Specialized & Graph Networks (dl_specialized) ───────────────────────
// Siamese Networks, GCN, GAT, Capsule Networks, Neural ODEs, KAN. Pinned by `SpecializedMathTest`.
//
// Two counting arguments (Neural ODE's memory account), several experiments that train or route
// for real (Siamese's contrastive embedding, capsule routing, the KAN/MLP fit), and one classical
// numerical-analysis identity (Euler vs RK4 convergence order) that holds for any smooth ODE.

private fun gaussian(random: Random): Double {
    val u1 = random.nextDouble().coerceAtLeast(1e-12)
    val u2 = random.nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
}

// ── Siamese Networks ──────────────────────────────────────────────────────────

/**
 * A learned embedding trained with a contrastive loss on three classes, then tested one-shot on a
 * fourth class it never saw. The point worth measuring rather than asserting: raw-feature nearest
 * neighbor is dominated by a high-variance nuisance dimension carrying no class information, and
 * the embedding -- trained only to make same/different-class pairs behave, on different classes
 * entirely -- learns to suppress that dimension anyway, because suppressing it is what the loss
 * needed on every class it saw.
 */
internal object SiameseLab {
    const val SIGNAL_DIM = 2
    const val FEATURE_DIM = 3 // 2 signal dims + 1 nuisance dim
    const val EMBED_DIM = 2
    const val MARGIN = 2.0

    val classPrototypes = listOf(
        doubleArrayOf(2.0, 2.0),
        doubleArrayOf(2.0, -2.0),
        doubleArrayOf(-2.0, 2.0),
        doubleArrayOf(-2.0, -2.0), // held out -- never used in training
    )
    const val NUISANCE_STD = 5.0
    const val SIGNAL_NOISE_STD = 0.3

    fun sample(classId: Int, random: Random): DoubleArray {
        val p = classPrototypes[classId]
        return doubleArrayOf(
            p[0] + SIGNAL_NOISE_STD * gaussian(random),
            p[1] + SIGNAL_NOISE_STD * gaussian(random),
            NUISANCE_STD * gaussian(random),
        )
    }

    private fun tanh(x: Double) = kotlin.math.tanh(x)

    class Embedder(val w: Array<DoubleArray>, val b: DoubleArray) {
        fun embed(x: DoubleArray): DoubleArray {
            val z = DoubleArray(EMBED_DIM) { j -> b[j] + (0 until FEATURE_DIM).sumOf { w[j][it] * x[it] } }
            return DoubleArray(EMBED_DIM) { tanh(z[it]) }
        }
    }

    fun distance(a: DoubleArray, b: DoubleArray): Double = sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })

    /** Trains only on classes 0, 1, 2 -- class 3 is never seen until evaluation. */
    fun train(steps: Int = 20_000, learningRate: Double = 0.05, seed: Int = 13): Embedder {
        val random = Random(seed)
        val w = Array(EMBED_DIM) { j -> DoubleArray(FEATURE_DIM) { 0.1 * gaussian(random) } }
        val b = DoubleArray(EMBED_DIM)
        val trainClasses = listOf(0, 1, 2)

        repeat(steps) {
            val same = random.nextBoolean()
            val classA = trainClasses.random(random)
            val classB = if (same) classA else trainClasses.filter { it != classA }.random(random)
            val xa = sample(classA, random)
            val xb = sample(classB, random)

            val za = DoubleArray(EMBED_DIM) { j -> b[j] + (0 until FEATURE_DIM).sumOf { w[j][it] * xa[it] } }
            val zb = DoubleArray(EMBED_DIM) { j -> b[j] + (0 until FEATURE_DIM).sumOf { w[j][it] * xb[it] } }
            val fa = DoubleArray(EMBED_DIM) { tanh(za[it]) }
            val fb = DoubleArray(EMBED_DIM) { tanh(zb[it]) }
            val diff = DoubleArray(EMBED_DIM) { fa[it] - fb[it] }
            val d = sqrt(diff.sumOf { it * it }).coerceAtLeast(1e-9)

            val dLdFa: DoubleArray
            val dLdFb: DoubleArray
            if (same) {
                dLdFa = DoubleArray(EMBED_DIM) { 2.0 * diff[it] }
                dLdFb = DoubleArray(EMBED_DIM) { -2.0 * diff[it] }
            } else if (d < MARGIN) {
                val coeff = -2.0 * (MARGIN - d) / d
                dLdFa = DoubleArray(EMBED_DIM) { coeff * diff[it] }
                dLdFb = DoubleArray(EMBED_DIM) { -coeff * diff[it] }
            } else {
                dLdFa = DoubleArray(EMBED_DIM)
                dLdFb = DoubleArray(EMBED_DIM)
            }
            val dLdZa = DoubleArray(EMBED_DIM) { dLdFa[it] * (1 - fa[it] * fa[it]) }
            val dLdZb = DoubleArray(EMBED_DIM) { dLdFb[it] * (1 - fb[it] * fb[it]) }

            for (j in 0 until EMBED_DIM) {
                for (k in 0 until FEATURE_DIM) {
                    w[j][k] -= learningRate * (dLdZa[j] * xa[k] + dLdZb[j] * xb[k])
                }
                b[j] -= learningRate * (dLdZa[j] + dLdZb[j])
            }
        }
        return Embedder(w, b)
    }

    val trainedEmbedder: Embedder by lazy { train() }

    class OneShotResult(val rawAccuracy: Double, val embeddedAccuracy: Double)

    /**
     * One support example per class (all four, including the held-out class 3), several queries
     * per class, classified by nearest support -- once in raw feature space, once through the
     * trained embedding.
     */
    fun oneShotEvaluate(queriesPerClass: Int = 60, seed: Int = 71): OneShotResult {
        val random = Random(seed)
        val embedder = trainedEmbedder
        val supports = classPrototypes.indices.map { sample(it, random) }
        val embeddedSupports = supports.map { embedder.embed(it) }

        var rawCorrect = 0
        var embeddedCorrect = 0
        var total = 0
        for (classId in classPrototypes.indices) {
            repeat(queriesPerClass) {
                val query = sample(classId, random)
                val embeddedQuery = embedder.embed(query)
                val rawPrediction = supports.indices.minBy { distance(query, supports[it]) }
                val embeddedPrediction = embeddedSupports.indices.minBy { distance(embeddedQuery, embeddedSupports[it]) }
                if (rawPrediction == classId) rawCorrect++
                if (embeddedPrediction == classId) embeddedCorrect++
                total++
            }
        }
        return OneShotResult(rawCorrect.toDouble() / total, embeddedCorrect.toDouble() / total)
    }

    val result: OneShotResult by lazy { oneShotEvaluate() }
}

// ── Shared small graph for GCN and GAT ────────────────────────────────────────

/** Two triangles (0,1,2) and (3,4,5), bridged by the single edge 2–3. */
internal object SmallGraph {
    val edges = listOf(0 to 1, 1 to 2, 0 to 2, 3 to 4, 4 to 5, 3 to 5, 2 to 3)
    const val NODES = 6

    val adjacency: Array<DoubleArray> by lazy {
        val a = Array(NODES) { DoubleArray(NODES) }
        edges.forEach { (u, v) -> a[u][v] = 1.0; a[v][u] = 1.0 }
        a
    }

    /** A + I. */
    val adjacencyWithSelfLoops: Array<DoubleArray> by lazy {
        Array(NODES) { i -> DoubleArray(NODES) { j -> adjacency[i][j] + if (i == j) 1.0 else 0.0 } }
    }

    val degrees: DoubleArray by lazy { DoubleArray(NODES) { i -> adjacencyWithSelfLoops[i].sum() } }

    /** D^-1/2 A_hat D^-1/2. */
    val normalizedAdjacency: Array<DoubleArray> by lazy {
        Array(NODES) { i -> DoubleArray(NODES) { j -> adjacencyWithSelfLoops[i][j] / sqrt(degrees[i] * degrees[j]) } }
    }

    fun neighborsOf(node: Int): List<Int> = (0 until NODES).filter { it != node && adjacency[node][it] > 0.0 }
}

// ── Graph Convolutional Networks (GCN) ────────────────────────────────────────

/**
 * Repeated normalized-adjacency propagation -- a GCN layer with an identity weight and no
 * nonlinearity -- is exactly graph Laplacian smoothing. For a connected graph, `A_norm` has a
 * unique top eigenvalue of 1 with eigenvector proportional to sqrt(degree); every other eigenvalue
 * has magnitude below 1. So the component of the features that actually distinguishes the two
 * triangles decays geometrically with depth, and what survives is the one direction every node
 * shares -- oversmoothing is this decay, computed rather than described.
 */
internal object GcnLab {
    const val FEATURE_DIM = 2

    /** Two visibly different groups, plus per-node noise so no two nodes start identical. */
    fun initialFeatures(seed: Int = 5): Array<DoubleArray> {
        val random = Random(seed)
        return Array(SmallGraph.NODES) { i ->
            val groupSign = if (i < 3) 1.0 else -1.0
            doubleArrayOf(groupSign * 2.0 + 0.2 * gaussian(random), 0.2 * gaussian(random))
        }
    }

    fun propagate(features: Array<DoubleArray>): Array<DoubleArray> {
        val a = SmallGraph.normalizedAdjacency
        return Array(SmallGraph.NODES) { i ->
            DoubleArray(FEATURE_DIM) { d -> (0 until SmallGraph.NODES).sumOf { a[i][it] * features[it][d] } }
        }
    }

    fun layers(initial: Array<DoubleArray>, depth: Int): List<Array<DoubleArray>> {
        val out = mutableListOf(initial)
        repeat(depth) { out += propagate(out.last()) }
        return out
    }

    fun distance(a: DoubleArray, b: DoubleArray): Double = sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })

    /** Mean cross-triangle distance divided by mean within-triangle distance -- 1.0 is "indistinguishable". */
    fun separation(features: Array<DoubleArray>): Double {
        val groupA = (0..2).toList()
        val groupB = (3..5).toList()
        fun meanPairDistance(indices: List<Int>): Double {
            val pairs = indices.indices.flatMap { i -> (i + 1 until indices.size).map { j -> indices[i] to indices[j] } }
            return pairs.map { (u, v) -> distance(features[u], features[v]) }.average()
        }
        val within = (meanPairDistance(groupA) + meanPairDistance(groupB)) / 2.0
        val across = groupA.flatMap { u -> groupB.map { v -> distance(features[u], features[v]) } }.average()
        return across / within
    }

    val propagationTrace: List<Array<DoubleArray>> by lazy { layers(initialFeatures(), depth = 20) }
    val separationByDepth: List<Double> by lazy { propagationTrace.map(::separation) }
}

// ── Graph Attention Networks (GAT) ────────────────────────────────────────────

/**
 * The same node's neighborhood, scored two ways. GCN's weight for edge (i, j) is
 * `1 / sqrt(deg(i) deg(j))` -- fixed the moment the graph is built, blind to what the features
 * are. GAT's is `softmax_j(LeakyReLU(a^T [Wh_i || Wh_j]))` -- a function of the features, which
 * this computes for real on a hand-set W and attention vector `a`, then perturbs one neighbor's
 * features to show only one of the two weightings moves.
 */
internal object GatLab {
    const val LEAKY_SLOPE = 0.2

    val centerNode = 2
    val neighbors = SmallGraph.neighborsOf(centerNode) // [0, 1, 3]

    val baseFeatures: Map<Int, DoubleArray> = mapOf(
        2 to doubleArrayOf(0.9, 0.1),
        0 to doubleArrayOf(1.0, 0.3),
        1 to doubleArrayOf(0.8, -0.2),
        3 to doubleArrayOf(-1.1, 0.4),
    )

    /** a^T [h_i || h_j], i.e. simply the sum of every coordinate of both (a = [1,1,1,1], W = I). */
    private fun leakyRelu(x: Double): Double = if (x >= 0) x else LEAKY_SLOPE * x

    fun attentionScore(center: DoubleArray, neighbor: DoubleArray): Double =
        leakyRelu(center[0] + center[1] + neighbor[0] + neighbor[1])

    fun softmax(scores: List<Double>): List<Double> {
        val m = scores.max()
        val exps = scores.map { exp(it - m) }
        val total = exps.sum()
        return exps.map { it / total }
    }

    fun attentionWeights(features: Map<Int, DoubleArray>): Map<Int, Double> {
        val center = features.getValue(centerNode)
        val scores = neighbors.map { attentionScore(center, features.getValue(it)) }
        val weights = softmax(scores)
        return neighbors.zip(weights).toMap()
    }

    fun gcnWeights(): Map<Int, Double> = neighbors.associateWith { SmallGraph.normalizedAdjacency[centerNode][it] }

    val baseAttention: Map<Int, Double> by lazy { attentionWeights(baseFeatures) }
    val baseGcn: Map<Int, Double> by lazy { gcnWeights() }

    /** Node 3's features moved to match the center exactly -- the sensitivity check. */
    val perturbedFeatures: Map<Int, DoubleArray> by lazy { baseFeatures + (3 to baseFeatures.getValue(centerNode).copyOf()) }
    val perturbedAttention: Map<Int, Double> by lazy { attentionWeights(perturbedFeatures) }
}

// ── Capsule Networks ───────────────────────────────────────────────────────────

/**
 * Dynamic routing-by-agreement over three primary capsules voting for two digit capsules. Two of
 * the three votes for "A" point the same direction; the third does not. Routing-by-agreement is
 * this loop: an agreeing vote raises its own logit, which raises its share of the next round's
 * weighted sum, which is exactly the mechanism a fixed pooling or averaging cannot do.
 */
internal object CapsuleLab {
    // u_hat_{j|i}: three votes for digit capsule A, three for digit capsule B.
    val votesA = listOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.9, 0.2), doubleArrayOf(-0.8, 0.3))
    val votesB = listOf(doubleArrayOf(0.1, 0.9), doubleArrayOf(-0.9, 0.1), doubleArrayOf(0.85, -0.3))

    fun squash(v: DoubleArray): DoubleArray {
        val normSq = v.sumOf { it * it }
        val norm = sqrt(normSq).coerceAtLeast(1e-9)
        val scale = normSq / (1.0 + normSq)
        return DoubleArray(v.size) { scale * v[it] / norm }
    }

    fun norm(v: DoubleArray): Double = sqrt(v.sumOf { it * it })

    fun dot(a: DoubleArray, b: DoubleArray): Double = a.indices.sumOf { a[it] * b[it] }

    class Round(val cA: List<Double>, val cB: List<Double>, val vA: DoubleArray, val vB: DoubleArray)

    /** Three routing iterations, exactly as the algorithm specifies -- logits start at zero. */
    fun route(iterations: Int = 3): List<Round> {
        val bA = DoubleArray(votesA.size)
        val bB = DoubleArray(votesA.size)
        val rounds = mutableListOf<Round>()
        repeat(iterations) {
            val cA = bA.indices.map { i -> exp(bA[i]) / (exp(bA[i]) + exp(bB[i])) }
            val cB = cA.map { 1.0 - it }

            val sA = DoubleArray(2) { d -> votesA.indices.sumOf { cA[it] * votesA[it][d] } }
            val sB = DoubleArray(2) { d -> votesB.indices.sumOf { cB[it] * votesB[it][d] } }
            val vA = squash(sA)
            val vB = squash(sB)

            for (i in votesA.indices) {
                bA[i] += dot(votesA[i], vA)
                bB[i] += dot(votesB[i], vB)
            }
            rounds += Round(cA, cB, vA, vB)
        }
        return rounds
    }

    val rounds: List<Round> by lazy { route() }

    /** What a plain unweighted average (equivalent to max/average pooling's "everyone counts equally") would give instead. */
    val naiveAverageA: DoubleArray get() = DoubleArray(2) { d -> votesA.sumOf { it[d] } / votesA.size }
    val naiveAverageLength: Double get() = norm(naiveAverageA)
}

// ── Neural ODEs ────────────────────────────────────────────────────────────────

/**
 * `dz/dt = -k z` has a closed-form solution, so Euler's and RK4's error against it is exact rather
 * than estimated against another numerical run. Their convergence orders -- error halving for
 * Euler when the step count doubles, error dropping by 16x for RK4 -- are textbook facts, verified
 * here on this specific system rather than cited. Alongside it: a plain counting argument for why
 * the adjoint method needs O(1) memory where a discrete ResNet of the same depth needs O(depth).
 */
internal object NeuralOdeLab {
    const val K = 1.0
    const val Z0 = 1.0
    const val T = 2.0

    fun exact(t: Double): Double = Z0 * exp(-K * t)

    fun f(z: Double): Double = -K * z

    fun euler(steps: Int): Double {
        var z = Z0
        val h = T / steps
        repeat(steps) { z += h * f(z) }
        return z
    }

    fun rk4(steps: Int): Double {
        var z = Z0
        val h = T / steps
        repeat(steps) {
            val k1 = f(z)
            val k2 = f(z + h / 2 * k1)
            val k3 = f(z + h / 2 * k2)
            val k4 = f(z + h * k3)
            z += h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
        }
        return z
    }

    val stepCounts = listOf(5, 10, 20, 40, 80)

    val eulerErrors: List<Double> by lazy { stepCounts.map { abs(euler(it) - exact(T)) } }
    val rk4Errors: List<Double> by lazy { stepCounts.map { abs(rk4(it) - exact(T)) } }

    /** For display: the full path, not just the endpoint. */
    fun eulerTrajectory(steps: Int): List<Pair<Double, Double>> {
        var z = Z0
        val h = T / steps
        val points = mutableListOf(0.0 to z)
        repeat(steps) { i -> z += h * f(z); points += (i + 1) * h to z }
        return points
    }

    fun exactTrajectory(points: Int): List<Pair<Double, Double>> {
        val h = T / points
        return (0..points).map { i -> i * h to exact(i * h) }
    }

    // ── The memory account ──────────────────────────────────────────────────
    const val RESNET_LAYERS = 50
    const val STATE_DIM = 64

    /** A discrete ResNet keeps every layer's activation around for the backward pass. */
    val resNetStoredFloats: Long get() = RESNET_LAYERS.toLong() * STATE_DIM

    /** The adjoint method integrates a second ODE backward, carrying only the state and the adjoint. */
    val adjointStoredFloats: Long get() = 2L * STATE_DIM

    val memoryRatio: Double get() = resNetStoredFloats.toDouble() / adjointStoredFloats
}

// ── Kolmogorov-Arnold Networks (KAN) ───────────────────────────────────────────

/**
 * The same wiggly target fitted two ways at a matched parameter budget: an MLP (fixed tanh units,
 * learnable weights) against a single learnable piecewise-linear function (KAN's edge function, in
 * its simplest form -- no fixed nonlinearity at all, the *shape* itself is what training moves).
 */
internal object KanLab {
    fun target(x: Double): Double = kotlin.math.sin(5.0 * x) * exp(-x * x / 2.0)

    fun xs(n: Int, seed: Int): DoubleArray {
        val random = Random(seed)
        return DoubleArray(n) { -2.0 + 4.0 * random.nextDouble() }
    }

    val trainXs = xs(40, seed = 21)
    val trainYs = DoubleArray(trainXs.size) { target(trainXs[it]) }
    val testXs = xs(40, seed = 22)
    val testYs = DoubleArray(testXs.size) { target(testXs[it]) }

    // ── MLP: H hidden tanh units, 3H+1 parameters ───────────────────────────
    class Mlp(val w1: DoubleArray, val b1: DoubleArray, val w2: DoubleArray, val b2: Double) {
        val paramCount get() = w1.size + b1.size + w2.size + 1
        fun predict(x: Double): Double {
            var out = b2
            for (h in w1.indices) out += w2[h] * kotlin.math.tanh(w1[h] * x + b1[h])
            return out
        }
    }

    fun trainMlp(hidden: Int, steps: Int = 30_000, lr: Double = 0.02, seed: Int = 31): Mlp {
        val random = Random(seed)
        val w1 = DoubleArray(hidden) { 1.0 * gaussian(random) }
        val b1 = DoubleArray(hidden) { 0.1 * gaussian(random) }
        val w2 = DoubleArray(hidden) { 0.1 * gaussian(random) }
        var b2 = 0.0
        val n = trainXs.size
        repeat(steps) {
            val i = random.nextInt(n)
            val x = trainXs[i]
            val y = trainYs[i]
            val pre = DoubleArray(hidden) { w1[it] * x + b1[it] }
            val act = DoubleArray(hidden) { kotlin.math.tanh(pre[it]) }
            var pred = b2
            for (h in 0 until hidden) pred += w2[h] * act[h]
            val err = pred - y
            for (h in 0 until hidden) {
                val dAct = err * w2[h] * (1 - act[h] * act[h])
                w1[h] -= lr * dAct * x
                b1[h] -= lr * dAct
                w2[h] -= lr * err * act[h]
            }
            b2 -= lr * err
        }
        return Mlp(w1, b1, w2, b2)
    }

    // ── KAN: a single learnable piecewise-linear function, M knots over [-2, 2] ─
    class Kan(val knotValues: DoubleArray, val xMin: Double = -2.0, val xMax: Double = 2.0) {
        val paramCount get() = knotValues.size
        private val knotSpacing get() = (xMax - xMin) / (knotValues.size - 1)
        fun predict(x: Double): Double {
            val clamped = x.coerceIn(xMin, xMax)
            val position = (clamped - xMin) / knotSpacing
            val lower = position.toInt().coerceIn(0, knotValues.size - 2)
            val t = position - lower
            return knotValues[lower] * (1 - t) + knotValues[lower + 1] * t
        }
    }

    fun trainKan(knots: Int, steps: Int = 30_000, lr: Double = 0.3, seed: Int = 31): Kan {
        val random = Random(seed)
        val knotValues = DoubleArray(knots)
        val kan = Kan(knotValues)
        val spacing = 4.0 / (knots - 1)
        val n = trainXs.size
        repeat(steps) {
            val i = random.nextInt(n)
            val x = trainXs[i]
            val y = trainYs[i]
            val clamped = x.coerceIn(-2.0, 2.0)
            val position = (clamped + 2.0) / spacing
            val lower = position.toInt().coerceIn(0, knots - 2)
            val t = position - lower
            val pred = knotValues[lower] * (1 - t) + knotValues[lower + 1] * t
            val err = pred - y
            knotValues[lower] -= lr * err * (1 - t)
            knotValues[lower + 1] -= lr * err * t
        }
        return kan
    }

    fun mse(predict: (Double) -> Double, xs: DoubleArray, ys: DoubleArray): Double {
        var total = 0.0
        for (i in xs.indices) { val e = predict(xs[i]) - ys[i]; total += e * e }
        return total / xs.size
    }

    const val HIDDEN = 5 // 3*5+1 = 16 parameters
    const val KNOTS = 16 // 16 parameters -- matched budget

    val trainedMlp: Mlp by lazy { trainMlp(HIDDEN) }
    val trainedKan: Kan by lazy { trainKan(KNOTS) }

    val mlpTestMse: Double by lazy { mse(trainedMlp::predict, testXs, testYs) }
    val kanTestMse: Double by lazy { mse(trainedKan::predict, testXs, testYs) }
}
