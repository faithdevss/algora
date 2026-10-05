package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// ── B8 · Feature scaling math ───────────────────────────────────────────────
// The scaling rule ("scale your features") run against a model that can be scored: k-NN on two
// features in mismatched units. Read by the z-score and min-max storyboards; pinned by
// `PreprocessMathTest`.

// ── Shared helpers ───────────────────────────────────────────────────────────

internal class Sample(val features: DoubleArray, val label: Int)

private fun mean(values: List<Double>) = values.average()

private fun variance(values: List<Double>): Double {
    val m = mean(values)
    return values.sumOf { (it - m) * (it - m) } / (values.size - 1)
}

private fun standardDeviation(values: List<Double>) = sqrt(variance(values))

/** k-NN, so the distance-based claims are scored by something that actually uses distance. */
internal fun knnPredict(train: List<Sample>, query: DoubleArray, k: Int = 5): Int {
    val nearest = train.sortedBy { sample ->
        sample.features.indices.sumOf { i ->
            val d = sample.features[i] - query[i]
            d * d
        }
    }.take(k)
    return nearest.groupingBy { it.label }.eachCount().maxByOrNull { it.value }!!.key
}

internal fun knnAccuracy(train: List<Sample>, test: List<Sample>, k: Int = 5) =
    test.count { knnPredict(train, it.features, k) == it.label } / test.size.toDouble()

/** Least squares by Gaussian elimination on the normal equations, with an intercept column. */
internal fun leastSquares(x: List<DoubleArray>, y: List<Double>): DoubleArray {
    val p = x.first().size + 1
    val design = x.map { row -> DoubleArray(p) { if (it == 0) 1.0 else row[it - 1] } }
    val a = Array(p) { i -> DoubleArray(p + 1) }
    for (i in 0 until p) {
        for (j in 0 until p) a[i][j] = design.indices.sumOf { design[it][i] * design[it][j] }
        a[i][p] = design.indices.sumOf { design[it][i] * y[it] }
    }
    // Ridge-free elimination with a tiny diagonal nudge, so a collinear design (which one-hot
    // encoding without a dropped level produces on purpose) still returns something finite.
    for (i in 0 until p) a[i][i] += 1e-8
    for (col in 0 until p) {
        val pivot = (col until p).maxBy { abs(a[it][col]) }
        val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
        for (row in 0 until p) {
            if (row == col || a[col][col] == 0.0) continue
            val factor = a[row][col] / a[col][col]
            for (k in col..p) a[row][k] -= factor * a[col][k]
        }
    }
    return DoubleArray(p) { a[it][p] / a[it][it] }
}

internal fun predict(coefficients: DoubleArray, row: DoubleArray) =
    coefficients[0] + row.indices.sumOf { coefficients[it + 1] * row[it] }

// ── Scaling ──────────────────────────────────────────────────────────────────

/**
 * The "scale your features" rule, scored by a model that is affected by it. Two features on
 * deliberately mismatched units — age in years, income in dollars — and a label that depends on
 * both. Unscaled, one squared difference in income is worth thousands in age, so k-NN is really a
 * one-feature classifier.
 */
internal object FeatureScalingLab {

    val featureNames = listOf("age (years)", "income ($)")

    private val random = Random(17)

    /** Label 1 when the person is both older and richer than the midpoint, with noise. */
    private fun generate(count: Int): List<Sample> = List(count) {
        val age = 20 + random.nextDouble() * 50
        val income = 20_000 + random.nextDouble() * 130_000
        val score = (age - 45) / 25.0 + (income - 85_000) / 65_000.0 + random.nextDouble() * 0.4 - 0.2
        Sample(doubleArrayOf(age, income), if (score > 0) 1 else 0)
    }

    val train: List<Sample> = generate(120)
    val test: List<Sample> = generate(80)

    private fun columns(data: List<Sample>, index: Int) = data.map { it.features[index] }

    fun minMax(fit: List<Sample>, apply: List<Sample>): List<Sample> {
        val lo = fit.first().features.indices.map { i -> columns(fit, i).min() }
        val hi = fit.first().features.indices.map { i -> columns(fit, i).max() }
        return apply.map { sample ->
            Sample(
                DoubleArray(sample.features.size) { i -> (sample.features[i] - lo[i]) / (hi[i] - lo[i]) },
                sample.label,
            )
        }
    }

    fun zScore(fit: List<Sample>, apply: List<Sample>): List<Sample> {
        val mu = fit.first().features.indices.map { i -> mean(columns(fit, i)) }
        val sigma = fit.first().features.indices.map { i -> standardDeviation(columns(fit, i)) }
        return apply.map { sample ->
            Sample(
                DoubleArray(sample.features.size) { i -> (sample.features[i] - mu[i]) / sigma[i] },
                sample.label,
            )
        }
    }

    class Result(val name: String, val accuracy: Double, val ranges: List<Double>)

    private fun ranges(data: List<Sample>) =
        data.first().features.indices.map { i -> columns(data, i).max() - columns(data, i).min() }

    val results: List<Result> by lazy {
        listOf(
            Result("raw", knnAccuracy(train, test), ranges(train)),
            Result("min-max", knnAccuracy(minMax(train, train), minMax(train, test)), ranges(minMax(train, train))),
            Result("z-score", knnAccuracy(zScore(train, train), zScore(train, test)), ranges(zScore(train, train))),
        )
    }

    /**
     * Which feature the unscaled distance is actually made of: the mean share of squared distance
     * contributed by each column, over every train/test pair.
     */
    fun distanceShare(reference: List<Sample>, queries: List<Sample>): List<Double> {
        val totals = DoubleArray(2)
        queries.take(20).forEach { query ->
            reference.forEach { sample ->
                sample.features.indices.forEach { i ->
                    val d = sample.features[i] - query.features[i]
                    totals[i] += d * d
                }
            }
        }
        val sum = totals.sum()
        return totals.map { it / sum }
    }

    /** The three splits the frames draw, with the queries scaled the same way as the reference set. */
    val distanceShares: List<Pair<String, List<Double>>> by lazy {
        listOf(
            "raw" to distanceShare(train, test),
            "min-max" to distanceShare(minMax(train, train), minMax(train, test)),
            "z-score" to distanceShare(zScore(train, train), zScore(train, test)),
        )
    }

    /**
     * Min-max's failure mode, priced. One extreme value in the fitting set pushes every real point
     * into a fraction of the [0,1] range; z-score moves too, but by nothing like as much.
     */
    class OutlierEffect(val minMaxSpan: Double, val zScoreSpan: Double, val outlierIncome: Double)

    val outlierEffect: OutlierEffect by lazy {
        val extreme = Sample(doubleArrayOf(45.0, 5_000_000.0), 1)
        val contaminated = train + extreme
        val scaledMinMax = minMax(contaminated, train)
        val scaledZ = zScore(contaminated, train)
        OutlierEffect(
            columns(scaledMinMax, 1).max() - columns(scaledMinMax, 1).min(),
            columns(scaledZ, 1).max() - columns(scaledZ, 1).min(),
            extreme.features[1],
        )
    }
}
