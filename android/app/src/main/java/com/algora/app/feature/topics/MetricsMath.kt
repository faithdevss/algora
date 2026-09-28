package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

// ── B9 · Evaluation metric math ──────────────────────────────────────────────
// Seventeen metrics, five substrates, and one rule: a metric earns its topic by *disagreeing* with
// the others somewhere measurable. Pinned by `MetricsMathTest`.
//
// The substrates are shared on purpose. Every classification metric below reads the same score
// vector from the same trained model, so when accuracy says 0.90 and Cohen's kappa says 0.00 those
// are two readings of one situation rather than two anecdotes. Same for the regression group, which
// all read one fit, and the two clustering indices, which read one clustering.

// ── Classification: one model, one score vector ──────────────────────────────

/**
 * An imbalanced binary problem with a real fitted model — 10% positives, two informative features,
 * and logistic scores produced by gradient descent rather than sampled from a distribution chosen to
 * make a point. Every threshold metric in this batch is computed from this one vector.
 */
internal object ScoredLab {

    const val POSITIVE_RATE = 0.10
    const val COUNT = 1000

    class Scored(val score: Double, val label: Int)

    private val random = Random(73)

    /** Features → label with a logistic link, then a real fit on top of them. */
    private val data: List<Sample> = List(COUNT) {
        val positive = random.nextDouble() < POSITIVE_RATE
        val centre = if (positive) 1.1 else -0.2
        Sample(
            doubleArrayOf(
                centre + random.nextDouble() * 2 - 1,
                centre * 0.7 + random.nextDouble() * 2 - 1,
            ),
            if (positive) 1 else 0,
        )
    }

    /** Logistic regression by gradient descent — the scores are a model's, not a generator's. */
    private val weights: DoubleArray by lazy {
        val w = DoubleArray(3)
        repeat(400) {
            val gradient = DoubleArray(3)
            data.forEach { sample ->
                val z = w[0] + w[1] * sample.features[0] + w[2] * sample.features[1]
                val p = 1 / (1 + exp(-z))
                val error = p - sample.label
                gradient[0] += error
                gradient[1] += error * sample.features[0]
                gradient[2] += error * sample.features[1]
            }
            for (i in w.indices) w[i] -= 0.05 * gradient[i] / data.size
        }
        w
    }

    val scored: List<Scored> by lazy {
        data.map { sample ->
            val z = weights[0] + weights[1] * sample.features[0] + weights[2] * sample.features[1]
            Scored(1 / (1 + exp(-z)), sample.label)
        }
    }

    /** The feature space itself, so a lab can draw the boundary the threshold actually moves. */
    val samples: List<Sample> get() = data

    val coefficients: DoubleArray get() = weights

    /**
     * The decision boundary in feature space at a given threshold. A logistic model's boundary is the
     * line w₀ + w₁x + w₂y = logit(t), so raising the threshold slides the same line rather than
     * bending it — which is what makes "one model, many operating points" literal.
     */
    fun boundaryFor(threshold: Double): Pair<Double, Double> {
        val logit = ln(threshold / (1 - threshold))
        val slope = -weights[1] / weights[2]
        val intercept = (logit - weights[0]) / weights[2]
        return slope to intercept
    }

    val positives get() = scored.count { it.label == 1 }
    val negatives get() = scored.count { it.label == 0 }

    /** The accuracy of predicting "negative" for everything — the number imbalance hands you free. */
    val majorityAccuracy get() = negatives.toDouble() / scored.size

    class Cells(val tp: Int, val fp: Int, val fn: Int, val tn: Int) {
        val total get() = tp + fp + fn + tn
        val accuracy get() = (tp + tn).toDouble() / total
        val precision get() = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val recall get() = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        val specificity get() = if (tn + fp == 0) 0.0 else tn.toDouble() / (tn + fp)
        val falsePositiveRate get() = 1 - specificity
        val f1 get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)

        fun fBeta(beta: Double): Double {
            val b2 = beta * beta
            val denominator = b2 * precision + recall
            return if (denominator == 0.0) 0.0 else (1 + b2) * precision * recall / denominator
        }

        /** Cohen's kappa: agreement past what the two marginals would produce by chance. */
        val kappa: Double
            get() {
                val n = total.toDouble()
                val observed = (tp + tn) / n
                val expected = ((tp + fp) / n) * ((tp + fn) / n) + ((tn + fn) / n) * ((tn + fp) / n)
                return if (expected == 1.0) 0.0 else (observed - expected) / (1 - expected)
            }

        /** Matthews correlation, for the frame that says what kappa is a cousin of. */
        val matthews: Double
            get() {
                val denominator = sqrt(
                    (tp.toDouble() + fp) * (tp.toDouble() + fn) * (tn.toDouble() + fp) * (tn.toDouble() + fn),
                )
                return if (denominator == 0.0) 0.0 else (tp.toDouble() * tn - fp.toDouble() * fn) / denominator
            }
    }

    fun cellsAt(threshold: Double, data: List<Scored> = scored): Cells {
        var tp = 0; var fp = 0; var fn = 0; var tn = 0
        data.forEach {
            val predicted = it.score >= threshold
            when {
                predicted && it.label == 1 -> tp += 1
                predicted && it.label == 0 -> fp += 1
                !predicted && it.label == 1 -> fn += 1
                else -> tn += 1
            }
        }
        return Cells(tp, fp, fn, tn)
    }

    val thresholds = listOf(0.05, 0.10, 0.20, 0.30, 0.50, 0.70, 0.90)

    /** The threshold that maximises F1 — measured, because it is not 0.5. */
    val bestF1Threshold: Double by lazy {
        (1..99).map { it / 100.0 }.maxBy { cellsAt(it).f1 }
    }

    val bestF1 get() = cellsAt(bestF1Threshold).f1

    /** ROC: (FPR, TPR) over every distinct score, plus the trapezoid area. */
    val rocCurve: List<Pair<Double, Double>> by lazy {
        val points = mutableListOf(0.0 to 0.0)
        scored.map { it.score }.distinct().sortedDescending().forEach { threshold ->
            val cells = cellsAt(threshold)
            points += cells.falsePositiveRate to cells.recall
        }
        points += 1.0 to 1.0
        points.sortedBy { it.first }
    }

    /** Precision-recall curve, which is the one that moves when the base rate does. */
    val prCurve: List<Pair<Double, Double>> by lazy {
        scored.map { it.score }.distinct().sortedDescending().map { threshold ->
            val cells = cellsAt(threshold)
            cells.recall to cells.precision
        }
    }

    private fun trapezoid(curve: List<Pair<Double, Double>>) =
        curve.zipWithNext().sumOf { (a, b) -> (b.first - a.first) * (a.second + b.second) / 2 }

    val auc: Double by lazy { trapezoid(rocCurve) }

    /** Average precision — the PR curve's summary, and the one imbalance is visible in. */
    val averagePrecision: Double by lazy {
        val sorted = scored.sortedByDescending { it.score }
        var hits = 0
        var sum = 0.0
        sorted.forEachIndexed { index, item ->
            if (item.label == 1) {
                hits += 1
                sum += hits.toDouble() / (index + 1)
            }
        }
        sum / positives
    }

    /** AUC computed the other way — as the probability a random positive outranks a random negative. */
    val aucByRanking: Double by lazy {
        val positiveScores = scored.filter { it.label == 1 }.map { it.score }
        val negativeScores = scored.filter { it.label == 0 }.map { it.score }
        var wins = 0.0
        positiveScores.forEach { p ->
            negativeScores.forEach { n ->
                wins += when {
                    p > n -> 1.0
                    p == n -> 0.5
                    else -> 0.0
                }
            }
        }
        wins / (positiveScores.size * negativeScores.size)
    }

    fun logLoss(data: List<Scored> = scored): Double =
        -data.sumOf { item ->
            val p = item.score.coerceIn(1e-15, 1 - 1e-15)
            if (item.label == 1) ln(p) else ln(1 - p)
        } / data.size

    /**
     * A miscalibrated copy of the same model: the scores are pushed towards 0 and 1 by a *monotone*
     * transform, so the ranking — and therefore the AUC — is unchanged to the digit, while the
     * probabilities are now wrong. This is the pair that separates a ranking metric from a
     * probability metric.
     */
    val overconfident: List<Scored> by lazy {
        scored.map { Scored(if (it.score >= 0.5) 0.5 + (it.score - 0.5) * 1.98 else it.score * 0.02, it.label) }
    }

    val overconfidentAuc: Double by lazy {
        val positiveScores = overconfident.filter { it.label == 1 }.map { it.score }
        val negativeScores = overconfident.filter { it.label == 0 }.map { it.score }
        var wins = 0.0
        positiveScores.forEach { p -> negativeScores.forEach { n -> wins += if (p > n) 1.0 else if (p == n) 0.5 else 0.0 } }
        wins / (positiveScores.size * negativeScores.size)
    }

    /** What one confidently wrong prediction costs, against the mean over everything else. */
    class ConfidentError(val worstContribution: Double, val meanContribution: Double, val worstScore: Double)

    val confidentError: ConfidentError by lazy {
        val contributions = scored.map { item ->
            val p = item.score.coerceIn(1e-15, 1 - 1e-15)
            if (item.label == 1) -ln(p) else -ln(1 - p)
        }
        val worst = contributions.indices.maxBy { contributions[it] }
        ConfidentError(contributions[worst], contributions.average(), scored[worst].score)
    }

    /** Brier score, for the frame that names log loss's gentler cousin. */
    fun brier(data: List<Scored> = scored) =
        data.sumOf { (it.score - it.label) * (it.score - it.label) } / data.size

    /** Calibration: mean predicted probability against observed rate, in ten bins. */
    class Bin(val lower: Double, val predicted: Double, val observed: Double, val count: Int)

    fun calibration(data: List<Scored> = scored, bins: Int = 10): List<Bin> =
        (0 until bins).mapNotNull { index ->
            val lower = index.toDouble() / bins
            val upper = (index + 1.0) / bins
            val inBin = data.filter { it.score >= lower && (it.score < upper || (index == bins - 1 && it.score <= 1.0)) }
            if (inBin.isEmpty()) null
            else Bin(lower, inBin.map { it.score }.average(), inBin.count { it.label == 1 } / inBin.size.toDouble(), inBin.size)
        }
}

// ── Regression: one fit, two loss functions, and noise columns ───────────────

/**
 * Clean linear data with a handful of contaminating points, fitted twice: once by least squares
 * (which minimises squared error) and once by iteratively reweighted least absolute deviations
 * (which minimises absolute error). The two fits are different lines, and that is the MAE-versus-MSE
 * argument stated as a model rather than as a table of numbers.
 */
internal object RegressionMetricsLab {

    const val CLEAN = 40
    const val CONTAMINATED = 4

    private val random = Random(79)

    class Point(val x: Double, val y: Double, val contaminated: Boolean)

    val points: List<Point> = buildList {
        repeat(CLEAN) {
            val x = it * 0.25
            add(Point(x, 2.0 + 1.4 * x + random.nextDouble() * 2 - 1, contaminated = false))
        }
        repeat(CONTAMINATED) {
            val x = 7.0 + it * 0.6
            add(Point(x, 2.0 + 1.4 * x + 14 + random.nextDouble() * 3, contaminated = true))
        }
    }

    class Fit(val name: String, val intercept: Double, val slope: Double) {
        fun predict(x: Double) = intercept + slope * x
    }

    private fun residuals(fit: Fit, data: List<Point> = points) = data.map { it.y - fit.predict(it.x) }

    val squaredLossFit: Fit by lazy {
        val coefficients = leastSquares(points.map { doubleArrayOf(it.x) }, points.map { it.y })
        Fit("least squares (MSE)", coefficients[0], coefficients[1])
    }

    /** IRLS with weights 1/|residual| converges to the least-absolute-deviations line. */
    val absoluteLossFit: Fit by lazy {
        var intercept = squaredLossFit.intercept
        var slope = squaredLossFit.slope
        repeat(60) {
            val weights = points.map { 1.0 / (abs(it.y - (intercept + slope * it.x)) + 1e-6) }
            var sw = 0.0; var swx = 0.0; var swy = 0.0; var swxx = 0.0; var swxy = 0.0
            points.forEachIndexed { index, point ->
                val w = weights[index]
                sw += w; swx += w * point.x; swy += w * point.y
                swxx += w * point.x * point.x; swxy += w * point.x * point.y
            }
            val denominator = sw * swxx - swx * swx
            slope = (sw * swxy - swx * swy) / denominator
            intercept = (swy - slope * swx) / sw
        }
        Fit("least absolute deviations (MAE)", intercept, slope)
    }

    val trueSlope = 1.4

    fun mse(fit: Fit, data: List<Point> = points) = residuals(fit, data).sumOf { it * it } / data.size

    fun rmse(fit: Fit, data: List<Point> = points) = sqrt(mse(fit, data))

    fun mae(fit: Fit, data: List<Point> = points) = residuals(fit, data).sumOf { abs(it) } / data.size

    fun rSquared(fit: Fit, data: List<Point> = points): Double {
        val mean = data.map { it.y }.average()
        val residual = residuals(fit, data).sumOf { it * it }
        val total = data.sumOf { (it.y - mean) * (it.y - mean) }
        return 1 - residual / total
    }

    /** The share of total squared error that the contaminating points alone account for. */
    val outlierErrorShare: Double by lazy {
        val all = residuals(squaredLossFit).map { it * it }
        val bad = points.indices.filter { points[it].contaminated }.sumOf { all[it] }
        bad / all.sum()
    }

    val outlierCountShare: Double get() = CONTAMINATED.toDouble() / points.size

    /** A model bad enough to make R² negative: the mean is a better predictor than this line is. */
    val worseThanMean: Fit by lazy { Fit("a plausible-looking wrong model", 14.0, -0.6) }

    /**
     * R² never falls when a column is added, even a column of noise. Adjusted R² can, and does. Both
     * are computed here on the same fits, with columns of pure random numbers appended one at a time.
     */
    class NoiseStep(val extraColumns: Int, val rSquared: Double, val adjusted: Double)

    val noiseColumns: List<NoiseStep> by lazy {
        val rng = Random(83)
        val y = points.map { it.y }
        val noise = List(10) { List(points.size) { rng.nextDouble() * 2 - 1 } }
        (0..8).map { extra ->
            val x = points.indices.map { row ->
                DoubleArray(1 + extra) { column -> if (column == 0) points[row].x else noise[column - 1][row] }
            }
            val coefficients = leastSquares(x, y)
            val predictions = x.map { predict(coefficients, it) }
            val mean = y.average()
            val residual = y.indices.sumOf { (y[it] - predictions[it]) * (y[it] - predictions[it]) }
            val total = y.sumOf { (it - mean) * (it - mean) }
            val r2 = 1 - residual / total
            val n = y.size
            val p = 1 + extra
            NoiseStep(extra, r2, 1 - (1 - r2) * (n - 1) / (n - p - 1))
        }
    }

    fun adjustedRSquared(r2: Double, n: Int, p: Int) = 1 - (1 - r2) * (n - 1) / (n - p - 1)
}

// ── Split impurity ───────────────────────────────────────────────────────────

/**
 * Gini impurity, entropy and misclassification rate, scored on the same candidate splits. The first
 * two almost always agree; the third is the one that famously does not move, and the lab measures a
 * split where it does not while the other two do.
 */
internal object ImpurityLab {

    class Split(val name: String, val leftPositive: Int, val leftNegative: Int, val rightPositive: Int, val rightNegative: Int) {
        val leftTotal get() = leftPositive + leftNegative
        val rightTotal get() = rightPositive + rightNegative
        val total get() = leftTotal + rightTotal
    }

    /** A parent node of 400 with 200 of each class, split four ways. */
    val splits = listOf(
        Split("A: 300/100 · 100/300", 300, 100, 100, 300),
        Split("B: 200/0 · 0/200", 200, 0, 0, 200),
        Split("C: 200/100 · 0/100", 200, 100, 0, 100),
        Split("D: 190/210 · 10/0", 190, 210, 10, 0),
    )

    fun gini(positive: Int, negative: Int): Double {
        val n = (positive + negative).toDouble()
        if (n == 0.0) return 0.0
        val p = positive / n
        return 1 - p * p - (1 - p) * (1 - p)
    }

    fun entropy(positive: Int, negative: Int): Double {
        val n = (positive + negative).toDouble()
        if (n == 0.0) return 0.0
        val p = positive / n
        if (p == 0.0 || p == 1.0) return 0.0
        return -(p * ln(p) + (1 - p) * ln(1 - p)) / ln(2.0)
    }

    fun misclassification(positive: Int, negative: Int): Double {
        val n = (positive + negative).toDouble()
        if (n == 0.0) return 0.0
        return 1 - kotlin.math.max(positive, negative) / n
    }

    private fun weighted(split: Split, impurity: (Int, Int) -> Double): Double {
        val n = split.total.toDouble()
        return split.leftTotal / n * impurity(split.leftPositive, split.leftNegative) +
            split.rightTotal / n * impurity(split.rightPositive, split.rightNegative)
    }

    class Scores(val split: Split, val giniGain: Double, val entropyGain: Double, val errorGain: Double)

    val parentPositive = 200
    val parentNegative = 200

    val scores: List<Scores> by lazy {
        val giniParent = gini(parentPositive, parentNegative)
        val entropyParent = entropy(parentPositive, parentNegative)
        val errorParent = misclassification(parentPositive, parentNegative)
        splits.map { split ->
            Scores(
                split,
                giniParent - weighted(split) { p, n -> gini(p, n) },
                entropyParent - weighted(split) { p, n -> entropy(p, n) },
                errorParent - weighted(split) { p, n -> misclassification(p, n) },
            )
        }
    }

    /**
     * The pair misclassification rate cannot separate. Splits A and C produce *identical* error gain
     * while Gini and entropy both prefer C — which is the reason trees are not grown on error rate.
     * Found by search rather than asserted, so a change to the split table cannot leave the frame
     * narrating a tie that is no longer there.
     */
    val tiedForError: Pair<Scores, Scores> by lazy {
        val pair = scores.indices.flatMap { i -> scores.indices.filter { it > i }.map { scores[i] to scores[it] } }
            .first { (a, b) -> abs(a.errorGain - b.errorGain) < 1e-9 && abs(a.giniGain - b.giniGain) > 1e-9 }
        pair
    }

    /** Gini's range on a binary problem is [0, 0.5]; entropy's is [0, 1]. */
    val giniMaximum = 0.5
    val entropyMaximum = 1.0

    /** Curve data: impurity against class balance, for all three criteria. */
    fun impurityCurve(steps: Int = 51): List<Triple<Double, Double, Double>> =
        (0 until steps).map { index ->
            val p = index.toDouble() / (steps - 1)
            val n = 1000
            val positive = (p * n).toInt()
            Triple(gini(positive, n - positive), entropy(positive, n - positive), misclassification(positive, n - positive))
        }
}

// ── Margin losses ────────────────────────────────────────────────────────────

/**
 * Hinge loss against logistic loss on the same margins. The difference that matters is not the shape
 * of the curves — it is that hinge is exactly zero past the margin, so the examples on the safe side
 * contribute no gradient at all, while logistic loss never stops asking for more.
 */
internal object MarginLab {

    private val random = Random(89)

    /** Margins y·f(x) from a real fit: the logistic model in ScoredLab, on its own training data. */
    val margins: List<Double> by lazy {
        ScoredLab.scored.map { item ->
            val sign = if (item.label == 1) 1.0 else -1.0
            // Recover the decision value from the probability: f(x) = logit(p).
            sign * ln(item.score.coerceIn(1e-9, 1 - 1e-9) / (1 - item.score.coerceIn(1e-9, 1 - 1e-9)))
        }
    }

    fun hinge(margin: Double) = kotlin.math.max(0.0, 1 - margin)

    fun logistic(margin: Double) = ln(1 + exp(-margin))

    fun zeroOne(margin: Double) = if (margin <= 0) 1.0 else 0.0

    fun squaredHinge(margin: Double) = hinge(margin) * hinge(margin)

    /** How many examples each loss still has an opinion about. */
    class Active(val hingeActive: Int, val logisticActive: Int, val total: Int, val tolerance: Double)

    val active: Active by lazy {
        val tolerance = 1e-9
        Active(
            margins.count { hinge(it) > tolerance },
            margins.count { logistic(it) > tolerance },
            margins.size,
            tolerance,
        )
    }

    /** Gradient magnitude per example, which is what "contributes nothing" means precisely. */
    fun hingeGradient(margin: Double) = if (margin < 1) -1.0 else 0.0

    fun logisticGradient(margin: Double) = -1 / (1 + exp(margin))

    /** The logistic gradient never reaches zero — measured at large margins. */
    fun logisticGradientAt(margin: Double) = abs(logisticGradient(margin))

    fun curve(loss: (Double) -> Double, from: Double = -2.0, to: Double = 3.0, steps: Int = 60) =
        (0 until steps).map { index ->
            val margin = from + (to - from) * index / (steps - 1)
            margin to loss(margin)
        }
}

// ── Clustering indices ───────────────────────────────────────────────────────

/**
 * Silhouette and Davies-Bouldin over the same clusterings, on two datasets: three well-separated
 * blobs, where both indices find the right k, and two concentric rings, where both are confidently
 * wrong. Neither index knows what a cluster is supposed to look like — they both assume compact and
 * roughly spherical — and the ring dataset is where that assumption is the answer.
 */
internal object ClusterMetricsLab {

    class Point2(val x: Double, val y: Double)

    private val random = Random(97)

    val blobs: List<Point2> by lazy {
        val centres = listOf(Point2(0.2, 0.2), Point2(0.8, 0.25), Point2(0.5, 0.8))
        centres.flatMap { centre ->
            List(40) { Point2(centre.x + (random.nextDouble() - 0.5) * 0.18, centre.y + (random.nextDouble() - 0.5) * 0.18) }
        }
    }

    val rings: List<Point2> by lazy {
        val inner = List(60) {
            val angle = random.nextDouble() * 2 * Math.PI
            val radius = 0.12 + random.nextDouble() * 0.04
            Point2(0.5 + radius * kotlin.math.cos(angle), 0.5 + radius * kotlin.math.sin(angle))
        }
        val outer = List(90) {
            val angle = random.nextDouble() * 2 * Math.PI
            val radius = 0.38 + random.nextDouble() * 0.04
            Point2(0.5 + radius * kotlin.math.cos(angle), 0.5 + radius * kotlin.math.sin(angle))
        }
        inner + outer
    }

    private fun distance(a: Point2, b: Point2) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    /**
     * k-means with restarts, because a single random start is not what any real implementation does
     * and scoring a badly-converged clustering measures the initialization rather than the index. The
     * first version of this lab ran one start and reported silhouette 0.42 at k = 3 against 0.69 at
     * k = 4 on three well-separated blobs — the index was fine; the clustering was not.
     */
    const val RESTARTS = 10

    fun kMeans(data: List<Point2>, k: Int, seed: Int = 101): List<Int> =
        (0 until RESTARTS).map { attempt -> runKMeans(data, k, seed + attempt * 17) }
            .minBy { assignment -> inertia(data, assignment) }

    private fun runKMeans(data: List<Point2>, k: Int, seed: Int): List<Int> {
        val rng = Random(seed)
        var centres = data.shuffled(rng).take(k)
        var assignment = List(data.size) { 0 }
        repeat(40) {
            assignment = data.map { point -> centres.indices.minBy { distance(point, centres[it]) } }
            centres = (0 until k).map { cluster ->
                val members = data.filterIndexed { index, _ -> assignment[index] == cluster }
                if (members.isEmpty()) centres[cluster]
                else Point2(members.map { it.x }.average(), members.map { it.y }.average())
            }
        }
        return assignment
    }

    /** Within-cluster sum of squares — what k-means minimises, and what picks between restarts. */
    fun inertia(data: List<Point2>, assignment: List<Int>): Double {
        val clusters = assignment.distinct()
        return clusters.sumOf { cluster ->
            val members = data.filterIndexed { index, _ -> assignment[index] == cluster }
            if (members.isEmpty()) 0.0 else {
                val centre = Point2(members.map { it.x }.average(), members.map { it.y }.average())
                members.sumOf { val d = distance(it, centre); d * d }
            }
        }
    }

    /** Per-point silhouette: (b − a) / max(a, b), with a the own-cluster mean and b the best other. */
    fun silhouettes(data: List<Point2>, assignment: List<Int>): List<Double> {
        val clusters = assignment.distinct()
        return data.indices.map { i ->
            val own = assignment[i]
            val same = data.indices.filter { assignment[it] == own && it != i }
            if (same.isEmpty()) return@map 0.0
            val a = same.sumOf { distance(data[i], data[it]) } / same.size
            val b = clusters.filter { it != own }.minOf { other ->
                val members = data.indices.filter { assignment[it] == other }
                members.sumOf { distance(data[i], data[it]) } / members.size
            }
            (b - a) / kotlin.math.max(a, b)
        }
    }

    fun silhouetteScore(data: List<Point2>, assignment: List<Int>) = silhouettes(data, assignment).average()

    /** Davies-Bouldin: mean over clusters of the worst (spread + spread) / separation ratio. Lower is better. */
    fun daviesBouldin(data: List<Point2>, assignment: List<Int>): Double {
        val clusters = assignment.distinct().sorted()
        val centroids = clusters.map { cluster ->
            val members = data.filterIndexed { index, _ -> assignment[index] == cluster }
            Point2(members.map { it.x }.average(), members.map { it.y }.average())
        }
        val spreads = clusters.mapIndexed { index, cluster ->
            val members = data.filterIndexed { i, _ -> assignment[i] == cluster }
            members.sumOf { distance(it, centroids[index]) } / members.size
        }
        return clusters.indices.map { i ->
            clusters.indices.filter { it != i }.maxOf { j ->
                (spreads[i] + spreads[j]) / distance(centroids[i], centroids[j])
            }
        }.average()
    }

    class Sweep(val k: Int, val silhouette: Double, val daviesBouldin: Double)

    fun sweep(data: List<Point2>, range: IntRange = 2..6): List<Sweep> =
        range.map { k ->
            val assignment = kMeans(data, k)
            Sweep(k, silhouetteScore(data, assignment), daviesBouldin(data, assignment))
        }

    val blobSweep: List<Sweep> by lazy { sweep(blobs) }
    val ringSweep: List<Sweep> by lazy { sweep(rings) }

    fun bestBySilhouette(sweep: List<Sweep>) = sweep.maxBy { it.silhouette }.k

    fun bestByDaviesBouldin(sweep: List<Sweep>) = sweep.minBy { it.daviesBouldin }.k

    /** The true structure of the ring dataset, which neither index recovers. */
    const val RING_TRUTH = 2

    /** Points a clustering placed in the wrong cluster by its own measure — negative silhouette. */
    fun negativeCount(data: List<Point2>, assignment: List<Int>) = silhouettes(data, assignment).count { it < 0 }
}
