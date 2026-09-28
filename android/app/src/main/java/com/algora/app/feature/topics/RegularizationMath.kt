package com.algora.app.feature.topics

import kotlin.math.sqrt
import kotlin.random.Random

// ── C9 · Regularization (dl_regularization) ──────────────────────────────────
// Data Augmentation, Early Stopping, Layer Normalization, Group Normalization. Pinned by
// `RegularizationMathTest`.
//
// Data Augmentation and Early Stopping are experiments: a nearest-centroid classifier and a
// polynomial fit are trained for real, and the claim is the measured gap between two runs.
//
// Layer Normalization and Group Normalization rest on identities: that an axis choice makes a
// statistic exactly independent of batch size/composition, not approximately so. Those are
// asserted to floating-point tolerance because the whole point is that they are exact.

// ── Data Augmentation ─────────────────────────────────────────────────────────

/**
 * A nearest-centroid classifier over small binary grids, trained two ways: on one canonical
 * pattern per class, or on that pattern's rotations and reflection. Both centroids are then tested
 * against noisy versions of every orientation, which is the situation augmentation is for — the
 * real input is not guaranteed to arrive in the pose the single training example happened to have.
 */
internal object DataAugmentationLab {
    const val SIDE = 5

    private fun grid(vararg rows: String): Array<IntArray> =
        Array(SIDE) { r -> IntArray(SIDE) { c -> if (rows[r][c] == 'X') 1 else 0 } }

    val canonicalL = grid(
        "X....",
        "X....",
        "X....",
        "X....",
        "XXXXX",
    )

    val canonicalT = grid(
        "XXXXX",
        "..X..",
        "..X..",
        "..X..",
        "..X..",
    )

    fun rotate90(g: Array<IntArray>): Array<IntArray> =
        Array(SIDE) { r -> IntArray(SIDE) { c -> g[SIDE - 1 - c][r] } }

    fun flipH(g: Array<IntArray>): Array<IntArray> =
        Array(SIDE) { r -> IntArray(SIDE) { c -> g[r][SIDE - 1 - c] } }

    /** The four rotations and the horizontal flip — the augmentation set for one class. */
    fun orientations(canonical: Array<IntArray>): List<Array<IntArray>> {
        val r0 = canonical
        val r90 = rotate90(r0)
        val r180 = rotate90(r90)
        val r270 = rotate90(r180)
        return listOf(r0, r90, r180, r270, flipH(r0))
    }

    fun flatten(g: Array<IntArray>): DoubleArray = DoubleArray(SIDE * SIDE) { i -> g[i / SIDE][i % SIDE].toDouble() }

    /** Flip `flips` pixels at random -- a stand-in for sensor noise / imperfect binarisation. */
    fun withNoise(g: Array<IntArray>, flips: Int, random: Random): Array<IntArray> {
        val out = Array(SIDE) { r -> g[r].copyOf() }
        repeat(flips) {
            val r = random.nextInt(SIDE)
            val c = random.nextInt(SIDE)
            out[r][c] = 1 - out[r][c]
        }
        return out
    }

    fun distance(a: DoubleArray, b: DoubleArray): Double =
        sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })

    fun centroid(vectors: List<DoubleArray>): DoubleArray {
        val d = vectors.first().size
        return DoubleArray(d) { i -> vectors.sumOf { it[i] } / vectors.size }
    }

    /** Centroid trained on the single canonical pose only. */
    val unaugmentedCentroidL: DoubleArray get() = flatten(canonicalL)
    val unaugmentedCentroidT: DoubleArray get() = flatten(canonicalT)

    /** Centroid trained on all five orientations. */
    val augmentedCentroidL: DoubleArray get() = centroid(orientations(canonicalL).map(::flatten))
    val augmentedCentroidT: DoubleArray get() = centroid(orientations(canonicalT).map(::flatten))

    class Evaluation(val unaugmentedAccuracy: Double, val augmentedAccuracy: Double)

    /**
     * Test set: every orientation of both classes, each with a couple of flipped pixels, several
     * seeds per orientation. Classified by nearest centroid under both training regimes.
     */
    fun evaluate(noisyDrawsPerOrientation: Int = 20, flips: Int = 2, seed: Int = 5): Evaluation {
        val random = Random(seed)
        var unaugCorrect = 0
        var augCorrect = 0
        var total = 0

        val cUnL = unaugmentedCentroidL
        val cUnT = unaugmentedCentroidT
        val cAugL = augmentedCentroidL
        val cAugT = augmentedCentroidT

        fun test(canonicalOrientations: List<Array<IntArray>>, label: Boolean) {
            canonicalOrientations.forEach { pose ->
                repeat(noisyDrawsPerOrientation) {
                    val sample = flatten(withNoise(pose, flips, random))
                    val unaugPredictsL = distance(sample, cUnL) < distance(sample, cUnT)
                    val augPredictsL = distance(sample, cAugL) < distance(sample, cAugT)
                    if (unaugPredictsL == label) unaugCorrect++
                    if (augPredictsL == label) augCorrect++
                    total++
                }
            }
        }
        test(orientations(canonicalL), true)
        test(orientations(canonicalT), false)

        return Evaluation(unaugCorrect.toDouble() / total, augCorrect.toDouble() / total)
    }

    val result: Evaluation by lazy { evaluate() }
}

// ── Early Stopping ────────────────────────────────────────────────────────────

/**
 * A degree-9 polynomial fit by gradient descent (not the closed form `RegressionLab` uses
 * elsewhere, because early stopping is a story about a training *trajectory*). Every epoch is
 * scored three ways: training loss, a held-out noisy validation loss, and the true risk -- error
 * against the clean function the noise was added to, which a real deployment would be measured
 * against and which validation only estimates.
 */
internal object EarlyStoppingLab {
    const val DEGREE = 9
    const val NOISE_STD = 0.15

    fun trueFunction(x: Double): Double = 0.5 * x * x * x - x + 0.3 * kotlin.math.sin(3.0 * x)

    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    class Dataset(val xs: DoubleArray, val yNoisy: DoubleArray, val yClean: DoubleArray)

    fun dataset(n: Int, seed: Int): Dataset {
        val random = Random(seed)
        val xs = DoubleArray(n) { -1.0 + 2.0 * it / (n - 1) }
        val clean = DoubleArray(n) { trueFunction(xs[it]) }
        val noisy = DoubleArray(n) { clean[it] + NOISE_STD * gaussian(random) }
        return Dataset(xs, noisy, clean)
    }

    val trainSet = dataset(20, seed = 1)
    val validationSet = dataset(20, seed = 2)
    val testSet = dataset(40, seed = 3)

    /** Raw powers 0..DEGREE, standardised per column using the *train* set's own mean/std. */
    private val featureMeans: DoubleArray
    private val featureStds: DoubleArray

    init {
        val raw = trainSet.xs.map { x -> DoubleArray(DEGREE + 1) { k -> Math.pow(x, k.toDouble()) } }
        featureMeans = DoubleArray(DEGREE + 1) { k -> raw.sumOf { it[k] } / raw.size }
        featureStds = DoubleArray(DEGREE + 1) { k ->
            val m = featureMeans[k]
            sqrt(raw.sumOf { (it[k] - m) * (it[k] - m) } / raw.size).coerceAtLeast(1e-8)
        }
    }

    private fun features(x: Double): DoubleArray = DoubleArray(DEGREE + 1) { k ->
        (Math.pow(x, k.toDouble()) - featureMeans[k]) / featureStds[k]
    }

    private fun predict(coeffs: DoubleArray, x: Double): Double {
        val f = features(x)
        return f.indices.sumOf { f[it] * coeffs[it] }
    }

    private fun mse(coeffs: DoubleArray, data: Dataset, against: DoubleArray): Double {
        var total = 0.0
        for (i in data.xs.indices) {
            val e = predict(coeffs, data.xs[i]) - against[i]
            total += e * e
        }
        return total / data.xs.size
    }

    class Epoch(val step: Int, val trainMse: Double, val validationMse: Double, val testTrueRiskMse: Double)

    class Run(val epochs: List<Epoch>) {
        val bestValidationEpoch: Epoch get() = epochs.minBy { it.validationMse }
        val finalEpoch: Epoch get() = epochs.last()
    }

    fun train(steps: Int = 4000, learningRate: Double = 0.05, recordEvery: Int = 20): Run {
        val n = trainSet.xs.size
        val trainFeatures = trainSet.xs.map(::features)
        val coeffs = DoubleArray(DEGREE + 1)
        val epochs = mutableListOf<Epoch>()

        for (step in 1..steps) {
            val grad = DoubleArray(DEGREE + 1)
            for (i in 0 until n) {
                val f = trainFeatures[i]
                val err = f.indices.sumOf { f[it] * coeffs[it] } - trainSet.yNoisy[i]
                for (k in 0..DEGREE) grad[k] += 2.0 * err * f[k] / n
            }
            for (k in 0..DEGREE) coeffs[k] -= learningRate * grad[k]

            if (step % recordEvery == 0) {
                epochs += Epoch(
                    step = step,
                    trainMse = mse(coeffs, trainSet, trainSet.yNoisy),
                    validationMse = mse(coeffs, validationSet, validationSet.yNoisy),
                    testTrueRiskMse = mse(coeffs, testSet, testSet.yClean),
                )
            }
        }
        return Run(epochs)
    }

    val run: Run by lazy { train() }
}

// ── Layer Normalization ───────────────────────────────────────────────────────

/**
 * BatchNorm and LayerNorm normalise the same activation tensor along different axes -- across the
 * batch for a fixed feature, or across the features for a fixed sample -- and that axis choice is
 * the whole story. Three things are computed rather than described: what BatchNorm does at batch
 * size 1 (nothing usable), whether either statistic depends on which other rows share the batch,
 * and how much a *small* batch's estimate of its own statistic jitters from draw to draw.
 */
internal object LayerNormalizationLab {
    const val EPS = 1e-5

    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    /** One row per sample, one column per feature. Features have different true means/scales. */
    fun batch(size: Int, seed: Int, featureDim: Int = 6): Array<DoubleArray> {
        val random = Random(seed)
        val featureMean = DoubleArray(featureDim) { (it + 1) * 2.0 }
        val featureScale = DoubleArray(featureDim) { 0.5 + it * 0.3 }
        return Array(size) { i -> DoubleArray(featureDim) { j -> featureMean[j] + featureScale[j] * gaussian(random) } }
    }

    fun batchNormColumn(rows: Array<DoubleArray>, col: Int): Pair<Double, Double> {
        val values = rows.map { it[col] }
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return mean to variance
    }

    fun batchNormalize(rows: Array<DoubleArray>): Array<DoubleArray> {
        val d = rows[0].size
        val stats = (0 until d).map { batchNormColumn(rows, it) }
        return Array(rows.size) { i -> DoubleArray(d) { j -> (rows[i][j] - stats[j].first) / sqrt(stats[j].second + EPS) } }
    }

    fun layerNormalizeRow(row: DoubleArray): DoubleArray {
        val mean = row.average()
        val variance = row.sumOf { (it - mean) * (it - mean) } / row.size
        return DoubleArray(row.size) { (row[it] - mean) / sqrt(variance + EPS) }
    }

    fun layerNormalize(rows: Array<DoubleArray>): Array<DoubleArray> = Array(rows.size) { layerNormalizeRow(rows[it]) }

    /** How much a batch of size `size` under- or over-estimates the true per-feature mean, over many draws. */
    fun batchStatisticJitter(size: Int, feature: Int, draws: Int = 2000, seed: Int = 11): Double {
        val estimates = DoubleArray(draws) { d -> batch(size, seed = seed * 100_000 + d).let { batchNormColumn(it, feature).first } }
        val mean = estimates.average()
        return sqrt(estimates.sumOf { (it - mean) * (it - mean) } / estimates.size)
    }

    val jitterSweep = listOf(4, 8, 16, 32, 64)
}

// ── Group Normalization ───────────────────────────────────────────────────────

/**
 * GroupNorm splits channels into G groups and normalises each (group, sample) jointly over
 * channels-in-group x spatial positions -- independent of the batch axis entirely, same as
 * LayerNorm. G=1 (all channels, one group) must equal LayerNorm exactly; G=channelCount (one
 * channel per group) must equal InstanceNorm exactly. Those are identities, checked to the digit.
 */
internal object GroupNormalizationLab {
    const val EPS = 1e-5
    const val CHANNELS = 8
    const val SPATIAL = 6

    private fun gaussian(random: Random): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

    /** One sample: CHANNELS rows of SPATIAL activations, channels grouped in pairs by true scale. */
    fun sample(seed: Int): Array<DoubleArray> {
        val random = Random(seed)
        return Array(CHANNELS) { c ->
            val scale = 1.0 + (c / 2) * 0.8
            DoubleArray(SPATIAL) { scale * gaussian(random) }
        }
    }

    /** Normalise `sample`'s channels in groups of `groupSize` (must divide CHANNELS). */
    fun groupNormalize(sample: Array<DoubleArray>, groupSize: Int): Array<DoubleArray> {
        val out = Array(CHANNELS) { DoubleArray(SPATIAL) }
        var start = 0
        while (start < CHANNELS) {
            val values = (start until start + groupSize).flatMap { sample[it].toList() }
            val mean = values.average()
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            for (c in start until start + groupSize) {
                for (s in 0 until SPATIAL) out[c][s] = (sample[c][s] - mean) / sqrt(variance + EPS)
            }
            start += groupSize
        }
        return out
    }

    fun layerNormEquivalent(sample: Array<DoubleArray>): Array<DoubleArray> = groupNormalize(sample, CHANNELS)
    fun instanceNormEquivalent(sample: Array<DoubleArray>): Array<DoubleArray> {
        val out = Array(CHANNELS) { DoubleArray(SPATIAL) }
        for (c in 0 until CHANNELS) {
            val values = sample[c].toList()
            val mean = values.average()
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            for (s in 0 until SPATIAL) out[c][s] = (sample[c][s] - mean) / sqrt(variance + EPS)
        }
        return out
    }

    /**
     * Unlike BatchNorm, a group's statistic is computed from one sample's own channels, so it
     * cannot depend on batch size at all. Recomputed at several nominal "batch sizes" (which this
     * function ignores, on purpose) to make that explicit rather than assumed.
     */
    fun groupStatOf(sample: Array<DoubleArray>, groupSize: Int, channel: Int, spatialIndex: Int): Double =
        groupNormalize(sample, groupSize)[channel][spatialIndex]
}
