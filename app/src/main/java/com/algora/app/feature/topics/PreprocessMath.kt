package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// ── B8 · Data preprocessing math ─────────────────────────────────────────────
// Scaling, encoding, imputation, resampling, outlier rules and two feature selectors. Pinned by
// `PreprocessMathTest`.
//
// Preprocessing is the part of a pipeline where claims are usually stated as rules of thumb ("scale
// your features", "never label-encode", "SMOTE fixes imbalance"). Every rule below is instead run
// against a model that can be scored: k-NN for the distance-based claims, least squares and a
// one-split tree for the encoding claims, and honest versus leaky cross-validation for SMOTE. Where
// a rule turns out to be conditional, the condition is measured rather than hedged.

// ── Shared helpers ───────────────────────────────────────────────────────────

internal class Sample(val features: DoubleArray, val label: Int)

private fun mean(values: List<Double>) = values.average()

private fun variance(values: List<Double>): Double {
    val m = mean(values)
    return values.sumOf { (it - m) * (it - m) } / (values.size - 1)
}

private fun standardDeviation(values: List<Double>) = sqrt(variance(values))

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val n = sorted.size
    return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
}

private fun quantile(values: List<Double>, q: Double): Double {
    val sorted = values.sorted()
    val position = q * (sorted.size - 1)
    val lower = position.toInt()
    val upper = min(lower + 1, sorted.size - 1)
    return sorted[lower] + (position - lower) * (sorted[upper] - sorted[lower])
}

private fun correlation(a: List<Double>, b: List<Double>): Double {
    val ma = mean(a)
    val mb = mean(b)
    val cov = a.indices.sumOf { (a[it] - ma) * (b[it] - mb) }
    return cov / sqrt(a.sumOf { (it - ma) * (it - ma) } * b.sumOf { (it - mb) * (it - mb) })
}

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

internal fun meanSquaredError(coefficients: DoubleArray, x: List<DoubleArray>, y: List<Double>) =
    x.indices.sumOf { val e = predict(coefficients, x[it]) - y[it]; e * e } / x.size

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

// ── Categorical encoding ─────────────────────────────────────────────────────

/**
 * "Never label-encode" is the rule; the measurement says it is conditional. A label code imposes an
 * ordering and a spacing that the categories do not have, which a linear model reads literally and a
 * tree does not read at all — so the same encoding is harmful in one model and free in the other.
 *
 * The target here is deliberately non-monotone in the code order, which is the case the rule is
 * really about.
 */
internal object EncodingLab {

    val categories = listOf("red", "green", "blue", "yellow")

    /** True effect per category — no order, and not monotone in the code. */
    val effects = mapOf("red" to 10.0, "green" to 2.0, "blue" to 9.0, "yellow" to 1.0)

    private val random = Random(23)

    class Row(val category: String, val target: Double)

    val rows: List<Row> = List(160) {
        val category = categories[random.nextInt(categories.size)]
        Row(category, effects.getValue(category) + random.nextDouble() * 2 - 1)
    }

    val labelCodes = categories.withIndex().associate { (index, name) -> name to index.toDouble() }

    fun labelEncoded(row: Row) = doubleArrayOf(labelCodes.getValue(row.category))

    fun oneHotEncoded(row: Row, dropFirst: Boolean = false) =
        DoubleArray(if (dropFirst) categories.size - 1 else categories.size) { i ->
            val index = if (dropFirst) i + 1 else i
            if (categories[index] == row.category) 1.0 else 0.0
        }

    class Fit(val name: String, val error: Double, val columns: Int)

    val linearFits: List<Fit> by lazy {
        val y = rows.map { it.target }
        val label = leastSquares(rows.map { labelEncoded(it) }, y)
        val oneHot = leastSquares(rows.map { oneHotEncoded(it) }, y)
        val dropped = leastSquares(rows.map { oneHotEncoded(it, dropFirst = true) }, y)
        listOf(
            Fit("label encoding", meanSquaredError(label, rows.map { labelEncoded(it) }, y), 1),
            Fit("one-hot", meanSquaredError(oneHot, rows.map { oneHotEncoded(it) }, y), categories.size),
            Fit(
                "one-hot, first level dropped",
                meanSquaredError(dropped, rows.map { oneHotEncoded(it, dropFirst = true) }, y),
                categories.size - 1,
            ),
        )
    }

    /**
     * A tree does not read the code as a number, only as a place to split, so with enough splits it
     * recovers the categories exactly. Grown here to full depth on the single label-coded column.
     */
    fun treeError(maxDepth: Int): Double {
        val y = rows.map { it.target }
        val x = rows.map { labelCodes.getValue(it.category) }

        fun fit(indices: List<Int>, depth: Int): Double {
            val values = indices.map { y[it] }
            if (depth == 0 || indices.size < 2 || values.distinct().size == 1) {
                val m = mean(values)
                return values.sumOf { (it - m) * (it - m) }
            }
            val candidates = indices.map { x[it] }.distinct().sorted().zipWithNext { a, b -> (a + b) / 2 }
            if (candidates.isEmpty()) {
                val m = mean(values)
                return values.sumOf { (it - m) * (it - m) }
            }
            return candidates.minOf { threshold ->
                val left = indices.filter { x[it] <= threshold }
                val right = indices.filter { x[it] > threshold }
                if (left.isEmpty() || right.isEmpty()) Double.MAX_VALUE
                else fit(left, depth - 1) + fit(right, depth - 1)
            }
        }
        return fit(rows.indices.toList(), maxDepth) / rows.size
    }

    /** The false geometry a label code introduces: red-to-yellow is 3, red-to-green is 1. */
    fun codeDistance(a: String, b: String) = abs(labelCodes.getValue(a) - labelCodes.getValue(b))

    /** One-hot's geometry: every pair of categories is √2 apart, which is the truth here. */
    fun oneHotDistance() = sqrt(2.0)

    /** Columns added per categorical feature, which is what one-hot costs on wide data. */
    fun columnsFor(levels: Int, dropFirst: Boolean = false) = if (dropFirst) levels - 1 else levels
}

// ── Missing values ───────────────────────────────────────────────────────────

/**
 * Mean imputation does not merely "fill the gaps" — it changes the column. The variance falls by a
 * factor that can be predicted from the missing rate alone, and any correlation the column had is
 * attenuated by the same mechanism. Both are measured here against the complete data they came from.
 */
internal object ImputationLab {

    const val MISSING_RATE = 0.30

    private val random = Random(29)

    val complete: List<Double>
    val partner: List<Double>
    val missing: List<Boolean>

    init {
        val n = 200
        val x = MutableList(n) { 0.0 }
        val y = MutableList(n) { 0.0 }
        for (i in 0 until n) {
            val base = random.nextDouble() * 10
            x[i] = base
            y[i] = 2.0 * base + random.nextDouble() * 4 - 2
        }
        complete = x
        partner = y
        missing = List(n) { random.nextDouble() < MISSING_RATE }
    }

    val observedCount = missing.count { !it }

    private val observed = complete.indices.filter { !missing[it] }.map { complete[it] }

    val meanFill = mean(observed)
    val medianFill = median(observed)

    fun imputed(fill: Double) = complete.indices.map { if (missing[it]) fill else complete[it] }

    class Effect(val name: String, val variance: Double, val correlation: Double, val rows: Int)

    val effects: List<Effect> by lazy {
        val kept = complete.indices.filter { !missing[it] }
        listOf(
            Effect("complete data", variance(complete), correlation(complete, partner), complete.size),
            Effect(
                "drop the rows",
                variance(kept.map { complete[it] }),
                correlation(kept.map { complete[it] }, kept.map { partner[it] }),
                kept.size,
            ),
            Effect("mean imputation", variance(imputed(meanFill)), correlation(imputed(meanFill), partner), complete.size),
            Effect("median imputation", variance(imputed(medianFill)), correlation(imputed(medianFill), partner), complete.size),
        )
    }

    /** Predicted variance ratio under mean imputation: the filled values contribute nothing. */
    fun predictedVarianceRatio(rate: Double = MISSING_RATE) = 1 - rate

    /** A skewed column, where the mean is the wrong constant and the median is not. */
    val skewed: List<Double> by lazy {
        val rng = Random(31)
        List(200) { if (rng.nextDouble() < 0.9) rng.nextDouble() * 10 else 200 + rng.nextDouble() * 300 }
    }

    class Centre(val mean: Double, val median: Double)

    val skewedCentre: Centre by lazy { Centre(mean(skewed), median(skewed)) }
}

// ── SMOTE ────────────────────────────────────────────────────────────────────

/**
 * SMOTE interpolates new minority points along segments to their minority neighbours. It does raise
 * minority recall — and applied in the wrong place it produces a cross-validation score that cannot
 * be reproduced on held-out data, because a synthetic point built from a neighbour that ends up in
 * the validation fold is a copy of that fold's information.
 *
 * Both are measured: the resampling itself, and then the same resampling done before and inside the
 * split.
 */
internal object SmoteLab {

    const val K = 5

    /**
     * The leak is measured at 1-NN rather than 5-NN on purpose: a synthetic point interpolated
     * towards a validation-fold neighbour sits *next to* that neighbour, and 1-NN is the classifier
     * that reads it. At k = 5 the same leak is diluted by four other votes, which understates a real
     * failure mode rather than measuring it.
     */
    const val LEAK_K = 1

    private val random = Random(37)

    val majority: List<Sample>
    val minority: List<Sample>

    init {
        majority = List(120) {
            Sample(doubleArrayOf(random.nextDouble() * 4 + 1, random.nextDouble() * 4 + 1), 0)
        }
        minority = List(12) {
            Sample(doubleArrayOf(random.nextDouble() * 1.6 + 3.4, random.nextDouble() * 1.6 + 3.4), 1)
        }
    }

    val imbalanceRatio = majority.size.toDouble() / minority.size

    /** One synthetic point: pick a minority sample, a random one of its k minority neighbours, interpolate. */
    fun synthesise(pool: List<Sample>, count: Int, seed: Int = 41): List<Sample> {
        val rng = Random(seed)
        return List(count) {
            val origin = pool[rng.nextInt(pool.size)]
            val neighbours = pool.filter { it !== origin }
                .sortedBy { candidate ->
                    candidate.features.indices.sumOf { i ->
                        val d = candidate.features[i] - origin.features[i]
                        d * d
                    }
                }
                .take(K)
            val neighbour = neighbours[rng.nextInt(neighbours.size)]
            val gap = rng.nextDouble()
            Sample(
                DoubleArray(origin.features.size) { i ->
                    origin.features[i] + gap * (neighbour.features[i] - origin.features[i])
                },
                1,
            )
        }
    }

    class Scores(val name: String, val accuracy: Double, val minorityRecall: Double, val precision: Double)

    private fun score(name: String, train: List<Sample>, test: List<Sample>): Scores {
        val predictions = test.map { knnPredict(train, it.features, K) }
        val truePositive = test.indices.count { predictions[it] == 1 && test[it].label == 1 }
        val falsePositive = test.indices.count { predictions[it] == 1 && test[it].label == 0 }
        val actualPositive = test.count { it.label == 1 }
        return Scores(
            name,
            test.indices.count { predictions[it] == test[it].label } / test.size.toDouble(),
            if (actualPositive == 0) 0.0 else truePositive.toDouble() / actualPositive,
            if (truePositive + falsePositive == 0) 0.0 else truePositive.toDouble() / (truePositive + falsePositive),
        )
    }

    // A held-out split of the *real* data, made once and never resampled.
    private val split: Pair<List<Sample>, List<Sample>> by lazy {
        val rng = Random(43)
        val majorityShuffled = majority.shuffled(rng)
        val minorityShuffled = minority.shuffled(rng)
        val trainPart = majorityShuffled.take(90) + minorityShuffled.take(9)
        val testPart = majorityShuffled.drop(90) + minorityShuffled.drop(9)
        trainPart to testPart
    }

    val trainSet get() = split.first
    val testSet get() = split.second

    val resampled: List<Sample> by lazy {
        trainSet + synthesise(trainSet.filter { it.label == 1 }, trainSet.count { it.label == 0 } - trainSet.count { it.label == 1 })
    }

    val scores: List<Scores> by lazy {
        listOf(
            score("imbalanced", trainSet, testSet),
            score("SMOTE on the training fold", resampled, testSet),
        )
    }

    /**
     * The leak. Resampling before the split puts synthetic points built from validation-fold
     * neighbours into the training data, so the fold is scoring on information it was trained on.
     */
    class Leak(val leakyScore: Double, val honestScore: Double, val folds: Int, val neighbours: Int)

    val leak: Leak by lazy {
        val folds = 5
        val everything = majority + minority
        val inflated = everything + synthesise(minority, majority.size - minority.size, seed = 47)
        val shuffledLeaky = inflated.shuffled(Random(53))
        val shuffledHonest = everything.shuffled(Random(53))

        fun crossValidate(data: List<Sample>, resampleInside: Boolean): Double {
            val size = data.size / folds
            return (0 until folds).sumOf { fold ->
                val validation = data.drop(fold * size).take(size)
                var training = data - validation.toSet()
                if (resampleInside) {
                    val minorityTrain = training.filter { it.label == 1 }
                    val need = training.count { it.label == 0 } - minorityTrain.size
                    if (minorityTrain.size > K && need > 0) {
                        training = training + synthesise(minorityTrain, need, seed = 59 + fold)
                    }
                }
                val predictions = validation.map { knnPredict(training, it.features, LEAK_K) }
                validation.indices.count { predictions[it] == validation[it].label } / validation.size.toDouble()
            } / folds
        }

        Leak(
            crossValidate(shuffledLeaky, resampleInside = false),
            crossValidate(shuffledHonest, resampleInside = true),
            folds,
            LEAK_K,
        )
    }
}

// ── Outlier detection ────────────────────────────────────────────────────────

/**
 * The z-score rule and the IQR rule disagree in a way that is not a matter of taste. A z-score is
 * computed from the mean and standard deviation, both of which the outlier itself moves — so one
 * sufficiently extreme point inflates σ until its own |z| falls under the threshold and it is
 * declared normal. That is the masking effect, and it is arithmetic rather than bad luck.
 */
internal object OutlierLab {

    const val Z_THRESHOLD = 3.0
    const val IQR_FACTOR = 1.5

    private val random = Random(61)

    val clean: List<Double> = List(60) { 50 + random.nextDouble() * 20 }

    /** One extreme value. It does *not* hide itself — see `maxPossibleZ`. */
    val contaminated: List<Double> = clean + listOf(4000.0)

    const val EXTREME = 4000.0

    /**
     * Masking needs the outliers to hide *each other*: each one inflates σ for the rest, and once
     * there are enough of them no single one stands far enough from the (now much larger) mean to be
     * flagged. How many is "enough" is not a guess — it is solved for below by adding extremes until
     * the largest z-score falls under the threshold.
     */
    val maskingCount: Int by lazy {
        (1..clean.size).first { k ->
            selfZScore(clean + List(k) { EXTREME }) <= Z_THRESHOLD
        }
    }

    /** The sample the z-score rule cannot see at all, at the size it takes to blind it. */
    val masked: List<Double> by lazy { clean + List(maskingCount) { EXTREME } }

    /** What that count is as a share of the contaminated sample. */
    val maskingShare: Double by lazy { maskingCount.toDouble() / masked.size }

    class Verdict(val rule: String, val flagged: List<Double>, val threshold: String)

    fun zScoreFlags(data: List<Double>): Verdict {
        val mu = mean(data)
        val sigma = standardDeviation(data)
        return Verdict(
            "z-score",
            data.filter { abs(it - mu) / sigma > Z_THRESHOLD },
            "|x − ${"%.1f".format(mu)}| / ${"%.1f".format(sigma)} > $Z_THRESHOLD",
        )
    }

    fun iqrFlags(data: List<Double>): Verdict {
        val q1 = quantile(data, 0.25)
        val q3 = quantile(data, 0.75)
        val iqr = q3 - q1
        val lower = q1 - IQR_FACTOR * iqr
        val upper = q3 + IQR_FACTOR * iqr
        return Verdict("IQR", data.filter { it < lower || it > upper }, "outside [${"%.1f".format(lower)}, ${"%.1f".format(upper)}]")
    }

    /** The MAD-based modified z-score, which is the robust version of the same idea. */
    fun modifiedZFlags(data: List<Double>, threshold: Double = 3.5): Verdict {
        val med = median(data)
        val mad = median(data.map { abs(it - med) })
        return Verdict(
            "modified z (MAD)",
            data.filter { 0.6745 * abs(it - med) / mad > threshold },
            "0.6745·|x − ${"%.1f".format(med)}| / ${"%.1f".format(mad)} > $threshold",
        )
    }

    /** The largest value's own z-score in a sample — the number masking is about. */
    fun selfZScore(data: List<Double> = contaminated): Double {
        val extreme = data.max()
        return abs(extreme - mean(data)) / standardDeviation(data)
    }

    /**
     * The bound that decides whether the z-score rule can work at all. A single point in a sample of
     * n can never have |z| above (n−1)/√n, however extreme it is, because it is inside the mean and
     * the σ being used to judge it. Below n = 11 that ceiling is under 3, so the usual threshold can
     * never fire — the rule is not strict on small samples, it is inert.
     */
    fun maxPossibleZ(n: Int) = (n - 1) / sqrt(n.toDouble())

    /** The smallest sample in which |z| > 3 is reachable at all. */
    val smallestUsableSample: Int by lazy { (2..200).first { maxPossibleZ(it) > Z_THRESHOLD } }

    fun fences(data: List<Double>): Pair<Double, Double> {
        val q1 = quantile(data, 0.25)
        val q3 = quantile(data, 0.75)
        return (q1 - IQR_FACTOR * (q3 - q1)) to (q3 + IQR_FACTOR * (q3 - q1))
    }

    /** How many outliers each rule tolerates before it stops working, as a fraction. */
    val breakdownPoints = mapOf("mean/σ (z-score)" to 0.0, "median/MAD" to 0.5, "quartiles (IQR)" to 0.25)
}

// ── Feature selection ────────────────────────────────────────────────────────

/**
 * Two selectors with different blind spots, run on one dataset so the difference is visible rather
 * than asserted. Chi-square scores each feature against the target one at a time, which makes it
 * fast and makes it blind to any signal that only exists in a pair. RFE refits a model p times and
 * drops the weakest coefficient each round, which sees interactions the model can express and costs
 * p fits.
 */
internal object FeatureSelectionLab {

    val featureNames = listOf("useful", "duplicate", "noise", "xorA", "xorB")

    private val random = Random(67)

    class Instance(val binary: IntArray, val label: Int)

    /**
     * `useful` predicts the label directly; `duplicate` is a near-copy of it; `noise` is unrelated;
     * `xorA` and `xorB` predict it only through their XOR.
     */
    val data: List<Instance> = List(400) {
        val useful = random.nextInt(2)
        val xorA = random.nextInt(2)
        val xorB = random.nextInt(2)
        val flip = random.nextDouble() < 0.1
        val label = if (flip) 1 - useful else useful
        val duplicate = if (random.nextDouble() < 0.9) useful else 1 - useful
        Instance(intArrayOf(useful, duplicate, random.nextInt(2), xorA, xorB), label)
    }

    /** The XOR-labelled variant, where the pair is the only signal that exists. */
    val xorData: List<Instance> = List(400) {
        val a = random.nextInt(2)
        val b = random.nextInt(2)
        Instance(intArrayOf(random.nextInt(2), random.nextInt(2), random.nextInt(2), a, b), a xor b)
    }

    class Contingency(val counts: Array<IntArray>, val chiSquare: Double)

    /** χ² = Σ (observed − expected)² / expected over the 2×2 table, computed for real. */
    fun chiSquare(data: List<Instance>, feature: Int): Contingency {
        val counts = Array(2) { IntArray(2) }
        data.forEach { counts[it.binary[feature]][it.label] += 1 }
        val total = data.size.toDouble()
        var chi = 0.0
        for (i in 0..1) {
            for (j in 0..1) {
                val rowSum = counts[i].sum()
                val colSum = counts[0][j] + counts[1][j]
                val expected = rowSum * colSum / total
                if (expected > 0) {
                    val diff = counts[i][j] - expected
                    chi += diff * diff / expected
                }
            }
        }
        return Contingency(counts, chi)
    }

    fun chiSquareRanking(data: List<Instance> = this.data) =
        featureNames.indices.map { featureNames[it] to chiSquare(data, it).chiSquare }
            .sortedByDescending { it.second }

    class Round(val dropped: String, val remaining: List<String>, val error: Double, val coefficients: Map<String, Double>)

    /**
     * Recursive feature elimination with a least-squares model: fit, drop the smallest absolute
     * coefficient, repeat. Features are 0/1 here so the coefficients are already comparable.
     */
    fun recursiveElimination(data: List<Instance> = this.data): List<Round> {
        var remaining = featureNames.indices.toList()
        val y = data.map { it.label.toDouble() }
        val rounds = mutableListOf<Round>()
        while (remaining.size > 1) {
            val x = data.map { instance -> DoubleArray(remaining.size) { instance.binary[remaining[it]].toDouble() } }
            val fit = leastSquares(x, y)
            val coefficients = remaining.indices.associate { featureNames[remaining[it]] to fit[it + 1] }
            val weakest = remaining.indices.minBy { abs(fit[it + 1]) }
            val dropped = featureNames[remaining[weakest]]
            rounds += Round(dropped, remaining.map { featureNames[it] }, meanSquaredError(fit, x, y), coefficients)
            remaining = remaining.filterIndexed { index, _ -> index != weakest }
        }
        return rounds
    }

    /** Model fits each selector costs, which is the reason chi-square is still used. */
    fun fitsRequired(features: Int) = features - 1

    fun chiSquareFits() = 0
}
