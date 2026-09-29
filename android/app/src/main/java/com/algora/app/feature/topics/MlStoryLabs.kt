package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.CategoryAccents
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

// ── Classic ML storyboards ───────────────────────────────────────────────────
// Bias-Variance, Regularization (L1 / L2), k-Nearest Neighbors and Decision Trees as step-by-step
// storyboards: a figure in the card (fitted curves over an error-by-degree strip, a fit over its data,
// a growing neighbour circle over distance tiles, or a partitioned plane over the tree that cuts it),
// a formula, a legend, then chips and a headline. The fits are real least-squares, ridge and lasso
// solutions, the tree is grown greedily on gini, and the neighbours are ranked by distance.

internal val mlStoryTopicIds = setOf("bias_variance", "regularization", "knn", "decision_trees")

private val MlPink = CategoryAccents.Pink

private class MlLegend(val color: Color, val style: SwatchStyle, val label: String)

private sealed interface MlScene

/** Bias-variance: fitted curves over the truth, and train / test error by degree underneath. */
private class FitScene(
    val truth: List<Pair<Double, Double>>,
    val fits: List<List<Pair<Double, Double>>>,
    val dots: List<Pair<Double, Double>>,
    val train: List<Double>,
    val test: List<Double>,
    val marker: Int,
) : MlScene

/** Regularization: the data, the unpenalized fit dashed, and the penalized fit. */
private class PenaltyScene(
    val dots: List<Pair<Double, Double>>,
    val overfit: List<Pair<Double, Double>>?,
    val fit: List<Pair<Double, Double>>,
) : MlScene

private enum class TileTone { Done, Current, Pending }

private class KnnTile(val distance: String, val label: Int?, val tone: TileTone)

private class KnnScene(
    val points: List<Triple<Double, Double, Int>>,
    val query: Pair<Double, Double>,
    val queryLabel: Int?,
    val radius: Double?,
    // Neighbour indices into [points], nearest first; the last is the current one while growing.
    val neighbours: List<Int>,
    val growing: Boolean,
    val tiles: List<KnnTile>,
) : MlScene

private enum class TreeBoxTone { Pending, Question, Leaf0, Leaf1, Current }

private class TreeBox(val title: String, val caption: String, val tone: TreeBoxTone)

private class TreeRegion(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val label: Int?)

private class TreeScene(
    val points: List<Triple<Double, Double, Int>>,
    val regions: List<TreeRegion>,
    val splits: List<Pair<Pair<Double, Double>, Pair<Double, Double>>>,
    val current: TreeRegion?,
    // Root, its yes and no children, then the yes child's yes and no children; null is not grown yet.
    val boxes: List<TreeBox?>,
) : MlScene

private class MlFrame(
    val headline: String,
    val body: String,
    val scene: MlScene,
    val formula: String? = null,
    val legend: List<MlLegend> = emptyList(),
    val chips: List<LabChip> = emptyList(),
)

private class MlTab(val label: String, val frames: List<MlFrame>)

// ── Shared math and formatting ──

/** Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−". */
private fun fx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = floor(abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

/** λ as written: 0, 0.0001, 0.01, 1. */
private fun lam(v: Double): String = if (v == 0.0) "0" else fx(v, 4).trimEnd('0').trimEnd('.')

/** The linear congruential generator the iOS port shares, so both draw the same samples. */
private class MlRandom(seed: Long) {
    private var state = seed
    fun next(): Double {
        state = (state * 1103515245L + 12345L) % 2147483648L
        return state / 2147483648.0
    }
    fun gauss(): Double {
        val u = maxOf(next(), 1e-12)
        val v = next()
        return sqrt(-2 * ln(u)) * cos(2 * Math.PI * v)
    }
}

/** Gaussian elimination with partial pivoting. */
private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
    for (c in 0 until n) {
        var p = c
        for (r in c + 1 until n) if (abs(m[r][c]) > abs(m[p][c])) p = r
        val t = m[c]; m[c] = m[p]; m[p] = t
        for (r in 0 until n) {
            if (r == c) continue
            val k = m[r][c] / m[c][c]
            for (j in c..n) m[r][j] -= k * m[c][j]
        }
    }
    return DoubleArray(n) { m[it][n] / m[it][it] }
}

private fun features(x: Double, degree: Int): DoubleArray {
    val t = 2 * x - 1
    val out = DoubleArray(degree + 1)
    var p = 1.0
    for (k in 0..degree) { out[k] = p; p *= t }
    return out
}

/** Least squares on polynomial features, with an L2 penalty [lambda] on every weight but the intercept. */
private fun polyFit(xs: List<Double>, ys: List<Double>, degree: Int, lambda: Double = 0.0): DoubleArray {
    val n = xs.size
    val x = xs.map { features(it, degree) }
    val a = Array(degree + 1) { i ->
        DoubleArray(degree + 1) { j ->
            var s = 0.0
            for (r in 0 until n) s += x[r][i] * x[r][j]
            s / n + (if (i == j && i > 0) lambda else 0.0) + (if (i == j) 1e-10 else 0.0)
        }
    }
    val b = DoubleArray(degree + 1) { i ->
        var s = 0.0
        for (r in 0 until n) s += x[r][i] * ys[r]
        s / n
    }
    return solve(a, b)
}

/** Lasso by cyclic coordinate descent: minimizes MSE + λ‖w‖₁, the intercept unpenalized. */
private fun lassoFit(xs: List<Double>, ys: List<Double>, degree: Int, lambda: Double, sweeps: Int = 1500): DoubleArray {
    val n = xs.size
    val x = xs.map { features(it, degree) }
    val w = DoubleArray(degree + 1)
    repeat(sweeps) {
        for (j in 0..degree) {
            var rho = 0.0
            var z = 0.0
            for (r in 0 until n) {
                var others = 0.0
                for (k in 0..degree) if (k != j) others += w[k] * x[r][k]
                rho += x[r][j] * (ys[r] - others)
                z += x[r][j] * x[r][j]
            }
            rho /= n
            z /= n
            w[j] = if (j == 0) rho / z else Math.copySign(maxOf(abs(rho) - lambda / 2, 0.0), rho) / z
        }
    }
    return w
}

private fun predict(w: DoubleArray, x: Double): Double {
    val f = features(x, w.size - 1)
    var s = 0.0
    for (k in w.indices) s += w[k] * f[k]
    return s
}

private val grid = (0..100).map { it / 100.0 }

private fun curve(f: (Double) -> Double) = grid.map { it to f(it) }

// ── Bias-variance ──

private fun biasTruth(x: Double) = 0.8 * sin(2 * Math.PI * x) + 0.3 * x
private const val BIAS_NOISE = 0.25
private const val BIAS_N = 12
private const val BIAS_SAMPLES = 40
private val biasDegrees = listOf(0, 3, 9)

private fun biasVarianceTabs(): List<MlTab> {
    val rng = MlRandom(7)
    val samples = List(BIAS_SAMPLES) {
        val xs = (0 until BIAS_N).map { i -> (i + 0.5) / BIAS_N + (rng.next() - 0.5) * 0.6 / BIAS_N }
        xs to xs.map { biasTruth(it) + BIAS_NOISE * rng.gauss() }
    }
    class Stats(val fits: List<DoubleArray>, val train: Double, val bias2: Double, val variance: Double) {
        val test get() = bias2 + variance + BIAS_NOISE * BIAS_NOISE
    }
    val stats = (0..9).map { d ->
        val fits = samples.map { (xs, ys) -> polyFit(xs, ys, d) }
        val train = samples.indices.sumOf { s ->
            val (xs, ys) = samples[s]
            xs.indices.sumOf { (predict(fits[s], xs[it]) - ys[it]).let { e -> e * e } } / BIAS_N
        } / BIAS_SAMPLES
        val mean = grid.map { x -> fits.sumOf { predict(it, x) } / fits.size }
        val bias2 = grid.indices.sumOf { (mean[it] - biasTruth(grid[it])).let { e -> e * e } } / grid.size
        val variance = grid.indices.sumOf { i -> fits.sumOf { (predict(it, grid[i]) - mean[i]).let { e -> e * e } } / fits.size } / grid.size
        Stats(fits, train, bias2, variance)
    }
    val train = stats.map { it.train }
    val test = stats.map { it.test }
    val best = test.indices.minBy { test[it] }
    val truth = curve(::biasTruth)
    fun scene(d: Int, many: Boolean, marker: Int = d) = FitScene(
        truth,
        stats[d].fits.take(if (many) 5 else 1).map { w -> curve { predict(w, it) } },
        if (many) emptyList() else samples[0].first.zip(samples[0].second),
        train, test, marker,
    )
    val legend = listOf(
        MlLegend(Color.Unspecified, SwatchStyle.Dashed, "True f"),
        MlLegend(SimColors.Blue, SwatchStyle.Fill, "Fit per sample"),
        MlLegend(SimColors.Green, SwatchStyle.Fill, "Train error"),
        MlLegend(SimColors.Red, SwatchStyle.Fill, "Test error"),
    )
    fun chips(d: Int) = listOf(
        LabChip("bias²", fx(stats[d].bias2, 3)),
        LabChip("variance", fx(stats[d].variance, 3)),
    )
    val s0 = stats[0]
    val s3 = stats[3]
    val s9 = stats[9]
    val frames = listOf(
        MlFrame(
            "Degree 0 can only draw a flat line: {the mean of its sample}.",
            "It misses the rise and fall of f entirely, whatever sample it is given.",
            scene(0, many = false), legend = legend, chips = chips(0),
        ),
        MlFrame(
            "Five samples, five flat lines, all {nearly the same}.",
            "Bias² is ${fx(s0.bias2, 3)} and variance only ${fx(s0.variance, 3)}. The error is the model's fault, not the data's.",
            scene(0, many = true), legend = legend, chips = chips(0),
        ),
        MlFrame(
            "Degree 3 has enough bends to {follow f}.",
            "Training error falls from ${fx(s0.train, 3)} at degree 0 to ${fx(s3.train, 3)}.",
            scene(3, many = false), legend = legend, chips = chips(3),
        ),
        MlFrame(
            "Five samples give five cubics that {agree with each other} and with f.",
            "Bias² ${fx(s3.bias2, 3)}, variance ${fx(s3.variance, 3)}: both small, so test error sits near its lowest.",
            scene(3, many = true), legend = legend, chips = chips(3),
        ),
        MlFrame(
            "Degree 9 bends to every sample, so {five samples give five different curves}.",
            "Bias² is only ${fx(s9.bias2, 3)} but variance is ${fx(s9.variance, 2)}. Test error climbs while training error keeps falling.",
            scene(9, many = true), legend = legend, chips = chips(9),
        ),
        MlFrame(
            "On its own sample degree 9 looks {nearly perfect}: training error ${fx(s9.train, 3)}.",
            "That is the trap. Training error rewards the very capacity that test error punishes.",
            scene(9, many = false), legend = legend, chips = chips(9),
        ),
        MlFrame(
            "The best capacity is {m:degree $best}, where bias² + variance is smallest.",
            "Too simple and bias dominates; too flexible and variance does. Test error = bias² + variance + noise.",
            scene(best, many = true), legend = legend,
            chips = listOf(LabChip("best degree", "$best", StoryTone.Done), LabChip("test error", fx(test[best], 3))),
        ),
    )
    return listOf(MlTab("", frames))
}

/** The degree a bias-variance frame is showing, for its picker. */
private fun biasDegreeOf(frame: MlFrame) = (frame.scene as FitScene).marker

// ── Regularization ──

private val penaltyLambdas = listOf(0.0, 0.0001, 0.001, 0.01, 1.0)
private const val PENALTY_DEGREE = 9

private fun regularizationTabs(): List<MlTab> {
    val rng = MlRandom(11)
    val xs = (0 until 12).map { (it + 0.5) / 12 }
    val ys = xs.map { 0.15 + 0.7 * it + 0.07 * rng.gauss() }
    val dots = xs.zip(ys)
    fun mse(w: DoubleArray) = xs.indices.sumOf { (predict(w, xs[it]) - ys[it]).let { e -> e * e } } / xs.size
    val exact = polyFit(xs, ys, PENALTY_DEGREE)
    val overfit = curve { predict(exact, it) }

    fun tab(l1: Boolean): MlTab {
        val fits = penaltyLambdas.map { l ->
            when {
                l == 0.0 -> exact
                l1 -> lassoFit(xs, ys, PENALTY_DEGREE, l)
                else -> polyFit(xs, ys, PENALTY_DEGREE, l)
            }
        }
        fun norm(w: DoubleArray) = if (l1) (1..PENALTY_DEGREE).sumOf { abs(w[it]) } else (1..PENALTY_DEGREE).sumOf { w[it] * w[it] }
        fun size(w: DoubleArray) = if (l1) norm(w) else sqrt(norm(w))
        fun zeros(w: DoubleArray) = (1..PENALTY_DEGREE).count { abs(w[it]) < 1e-9 }
        val normName = if (l1) "‖w‖₁" else "‖w‖²"
        val sizeName = if (l1) "‖w‖₁" else "‖w‖"
        val frames = penaltyLambdas.mapIndexed { i, l ->
            val w = fits[i]
            val m = mse(w)
            val start = size(fits[0])
            val headline: String
            val body: String
            when (i) {
                0 -> {
                    headline = "With λ = 0, degree 9 {threads every point}: MSE ${fx(m, 4)}."
                    body = "Nothing stops the weights from growing, so $sizeName is ${fx(start, 1)}."
                }
                1 -> {
                    headline = "A tiny penalty, λ = ${lam(l)}, already shrinks $sizeName to {${fx(size(w), 2)}}."
                    body = if (l1) "L1 has set ${zeros(w)} of the 9 weights to exactly zero." else "The largest weights paid for the sharpest wiggles, so those shrink first."
                }
                2 -> {
                    headline = "At λ = ${lam(l)} the wiggles {flatten out}, and MSE only rises to ${fx(m, 4)}."
                    body = if (l1) "${zeros(w)} of 9 weights are now exactly zero: L1 drops features instead of shrinking them." else "Every weight is smaller, none is zero. That is how L2 behaves."
                }
                3 -> {
                    headline = "λ = ${lam(l)} keeps {the trend} and drops the wiggles."
                    body = "Wiggles need large weights, so they are the first thing the penalty removes." +
                        if (l1) " Only ${PENALTY_DEGREE - zeros(w)} weights survive." else ""
                }
                else -> {
                    headline = "λ = ${lam(l)} {w:over-penalizes}: the fit sags toward a flat line."
                    body = "MSE climbs to ${fx(m, 4)}. Too much λ is underfitting by another route."
                }
            }
            val penalty = l * norm(w)
            MlFrame(
                headline, body,
                PenaltyScene(dots, if (i == 0) null else overfit, curve { predict(w, it) }),
                "loss = MSE {p:${fx(m, 4)}} + λ {p:${lam(l)}} × $normName {p:${fx(norm(w), 2)}} = {${fx(m + penalty, 4)}}",
                listOfNotNull(
                    MlLegend(SimColors.Blue, SwatchStyle.Dot, "Data"),
                    if (i == 0) null else MlLegend(SimColors.Red, SwatchStyle.Dashed, "λ = 0"),
                    MlLegend(SimColors.Answer, SwatchStyle.Fill, "λ = ${lam(l)}"),
                ),
                listOfNotNull(
                    LabChip(sizeName, if (i == 0) fx(start, 1) else "${fx(start, 1)} → ${fx(size(w), 2)}"),
                    if (l1) LabChip("zero weights", "${zeros(w)}/9", if (zeros(w) > 0) StoryTone.Done else StoryTone.Idle) else null,
                ),
            )
        }
        return MlTab(if (l1) "L1 · lasso" else "L2 · ridge", frames)
    }
    return listOf(tab(l1 = true), tab(l1 = false))
}

// ── k-nearest neighbours ──

private val knnPoints: List<Triple<Double, Double, Int>> = listOf(
    Triple(0.12, 0.62, 0), Triple(0.22, 0.78, 0), Triple(0.30, 0.70, 0), Triple(0.33, 0.74, 0), Triple(0.34, 0.60, 0),
    Triple(0.40, 0.82, 0), Triple(0.42, 0.68, 0), Triple(0.46, 0.72, 0), Triple(0.44, 0.58, 0), Triple(0.28, 0.52, 0),
    Triple(0.60, 0.40, 1), Triple(0.64, 0.44, 1), Triple(0.66, 0.38, 1), Triple(0.70, 0.47, 1), Triple(0.72, 0.30, 1),
    Triple(0.80, 0.36, 1), Triple(0.84, 0.46, 1), Triple(0.86, 0.28, 1), Triple(0.76, 0.22, 1), Triple(0.56, 0.50, 1),
)
private val knnQuery = 0.50 to 0.55
private const val KNN_K = 5

private fun knnTabs(): List<MlTab> {
    fun dist(i: Int) = sqrt((knnPoints[i].first - knnQuery.first).let { it * it } + (knnPoints[i].second - knnQuery.second).let { it * it })
    val ranked = knnPoints.indices.sortedBy { dist(it) }
    val ordinal = listOf("one", "two", "three", "four", "five")
    fun votes(n: Int) = (0..1).map { c -> ranked.take(n).count { knnPoints[it].third == c } }
    fun tiles(n: Int, growing: Boolean) = (0 until KNN_K).map { i ->
        when {
            i < n - 1 || (i == n - 1 && !growing) -> KnnTile(fx(dist(ranked[i])), knnPoints[ranked[i]].third, TileTone.Done)
            i == n - 1 -> KnnTile(fx(dist(ranked[i])), knnPoints[ranked[i]].third, TileTone.Current)
            else -> KnnTile("—", null, TileTone.Pending)
        }
    }
    fun chips(n: Int) = votes(n).let { v ->
        listOf(
            LabChip("k", "$KNN_K"),
            LabChip("class 0", "${v[0]}", dot = SimColors.Blue),
            LabChip("class 1", "${v[1]}", dot = MlPink),
        )
    }
    val legend = listOf(
        MlLegend(SimColors.Blue, SwatchStyle.Fill, "Class 0"),
        MlLegend(MlPink, SwatchStyle.Fill, "Class 1"),
        MlLegend(SimColors.Active, SwatchStyle.Fill, "Query · current"),
    )
    val frames = mutableListOf(
        MlFrame(
            "The query has no label. Its {k = $KNN_K} nearest neighbours will vote on one.",
            "k-NN stores the data and does nothing else. All the work happens now, at prediction time.",
            KnnScene(knnPoints, knnQuery, null, null, emptyList(), false, tiles(0, false)),
            legend = legend, chips = chips(0),
        ),
    )
    for (n in 1..KNN_K) {
        val i = ranked[n - 1]
        val v = votes(n)
        frames += MlFrame(
            "Neighbour $n is ${fx(dist(i))} away, class ${knnPoints[i].third}. {The circle grows} to reach it.",
            if (n < KNN_K) "After ${ordinal[KNN_K - 1]} neighbours the majority class is the prediction. No model is trained at all."
            else "That makes ${ordinal[KNN_K - 1]}. The vote stands at ${v[0]} to ${v[1]}.",
            KnnScene(knnPoints, knnQuery, null, dist(i), ranked.take(n), true, tiles(n, true)),
            legend = legend, chips = chips(n),
        )
    }
    val v = votes(KNN_K)
    val winner = if (v[1] > v[0]) 1 else 0
    frames += MlFrame(
        "Class $winner wins the vote ${maxOf(v[0], v[1])} to ${minOf(v[0], v[1])}, so the query is {${if (winner == 0) "p" else "w"}:class $winner}.",
        "Every prediction scans the stored points. That is the price of skipping training.",
        KnnScene(knnPoints, knnQuery, winner, dist(ranked[KNN_K - 1]), ranked.take(KNN_K), false, tiles(KNN_K, false)),
        legend = legend.dropLast(1) + MlLegend(SimColors.Active, SwatchStyle.Fill, "Query"),
        chips = listOf(LabChip("prediction", "class $winner", StoryTone.Answer)) + chips(KNN_K).drop(1),
    )
    return listOf(MlTab("", frames))
}

// ── Decision tree ──

private val treePoints: List<Triple<Double, Double, Int>> = listOf(
    Triple(0.20, 0.80, 1), Triple(0.12, 0.68, 1), Triple(0.30, 0.70, 1), Triple(0.18, 0.58, 1),
    Triple(0.10, 0.20, 0), Triple(0.26, 0.30, 0),
    Triple(0.45, 0.20, 0), Triple(0.55, 0.15, 0), Triple(0.66, 0.12, 0), Triple(0.78, 0.18, 0), Triple(0.90, 0.10, 0),
    Triple(0.62, 0.75, 0), Triple(0.75, 0.88, 0), Triple(0.85, 0.82, 0), Triple(0.90, 0.65, 0),
)

private fun gini(items: List<Triple<Double, Double, Int>>): Double {
    if (items.isEmpty()) return 0.0
    val k = items.count { it.third == 1 }.toDouble() / items.size
    return 1 - k * k - (1 - k) * (1 - k)
}

private class TreeSplit(val onX: Boolean, val threshold: Double, val score: Double)

/** The threshold on either axis that lowers weighted gini the most; the first wins a tie. */
private fun bestSplit(items: List<Triple<Double, Double, Int>>): TreeSplit {
    var best: TreeSplit? = null
    for (onX in listOf(true, false)) {
        val values = items.map { if (onX) it.first else it.second }.distinct().sorted()
        for (i in 0 until values.size - 1) {
            val t = (values[i] + values[i + 1]) / 2
            val left = items.filter { (if (onX) it.first else it.second) < t }
            val right = items.filter { (if (onX) it.first else it.second) >= t }
            val score = (left.size * gini(left) + right.size * gini(right)) / items.size
            if (best == null || score < best.score - 1e-12) best = TreeSplit(onX, t, score)
        }
    }
    return best!!
}

private fun giniText(items: List<Triple<Double, Double, Int>>): String {
    val n = items.size
    val a = items.count { it.third == 0 }
    val b = n - a
    return "gini = 1 − ( {p:$a} /$n)² − ( {p:$b} /$n)² = {${fx(gini(items))}}"
}

internal fun treeStoryShapeHolds(): Boolean {
    val root = bestSplit(treePoints)
    val (yes, no) = treePoints.partition { (if (root.onX) it.first else it.second) < root.threshold }
    return gini(no) == 0.0 && gini(yes) > 0 && bestSplit(yes).score == 0.0
}

private fun decisionTreeTabs(): List<MlTab> {
    val pts = treePoints
    val root = bestSplit(pts)
    fun side(t: Triple<Double, Double, Int>, s: TreeSplit) = (if (s.onX) t.first else t.second) < s.threshold
    val yes = pts.filter { side(it, root) }
    val no = pts.filter { !side(it, root) }
    val inner = bestSplit(yes)
    val yesYes = yes.filter { side(it, inner) }
    val yesNo = yes.filter { !side(it, inner) }
    fun axis(s: TreeSplit) = if (s.onX) "x" else "y"
    fun question(s: TreeSplit) = "${axis(s)} < ${fx(s.threshold)}"
    fun majority(items: List<Triple<Double, Double, Int>>) = if (items.count { it.third == 1 } * 2 > items.size) 1 else 0
    fun leafTone(items: List<Triple<Double, Double, Int>>) = if (majority(items) == 1) TreeBoxTone.Leaf1 else TreeBoxTone.Leaf0
    fun leaf(items: List<Triple<Double, Double, Int>>, current: Boolean = false) =
        TreeBox("leaf → ${majority(items)}", "${items.size} pts" + if (current) " · gini 0" else "", if (current) TreeBoxTone.Current else leafTone(items))

    // Regions in the unit square: the root cut, then the cut inside its yes side.
    val all = TreeRegion(0.0, 0.0, 1.0, 1.0, null)
    val yesBox = if (root.onX) TreeRegion(0.0, 0.0, root.threshold, 1.0, null) else TreeRegion(0.0, 0.0, 1.0, root.threshold, null)
    val noBox = if (root.onX) TreeRegion(root.threshold, 0.0, 1.0, 1.0, null) else TreeRegion(0.0, root.threshold, 1.0, 1.0, null)
    fun cut(r: TreeRegion, s: TreeSplit) =
        if (s.onX) (s.threshold to r.y0) to (s.threshold to r.y1) else (r.x0 to s.threshold) to (r.x1 to s.threshold)
    fun part(r: TreeRegion, s: TreeSplit, lower: Boolean, label: Int?) = when {
        s.onX && lower -> TreeRegion(r.x0, r.y0, s.threshold, r.y1, label)
        s.onX -> TreeRegion(s.threshold, r.y0, r.x1, r.y1, label)
        lower -> TreeRegion(r.x0, r.y0, r.x1, s.threshold, label)
        else -> TreeRegion(r.x0, s.threshold, r.x1, r.y1, label)
    }
    val rootCut = cut(all, root)
    val innerCut = cut(yesBox, inner)
    val leafYY = part(yesBox, inner, true, majority(yesYes))
    val leafYN = part(yesBox, inner, false, majority(yesNo))
    val leafNo = TreeRegion(noBox.x0, noBox.y0, noBox.x1, noBox.y1, majority(no))

    val rootQ = TreeBox(question(root), "${pts.size} pts", TreeBoxTone.Question)
    val innerQ = TreeBox(question(inner), "${yes.size} pts", TreeBoxTone.Question)
    fun pending(items: List<Triple<Double, Double, Int>>) = TreeBox("?", "${items.size} pts", TreeBoxTone.Pending)
    val legend = listOf(
        MlLegend(SimColors.Blue, SwatchStyle.Fill, "Class 0"),
        MlLegend(MlPink, SwatchStyle.Fill, "Class 1"),
        MlLegend(SimColors.Answer, SwatchStyle.Fill, "Split"),
        MlLegend(SimColors.Active, SwatchStyle.Fill, "Current region"),
    )
    val ones = pts.count { it.third == 1 }
    val frames = listOf(
        MlFrame(
            "${pts.size} points, {two classes}. Gini impurity measures how mixed they are: ${fx(gini(pts))}.",
            "0 would mean one class only. The tree looks for the cut that lowers it the most.",
            TreeScene(pts, emptyList(), emptyList(), all, listOf(TreeBox("root", "${pts.size} pts", TreeBoxTone.Current), null, null, null, null)),
            giniText(pts), legend,
        ),
        MlFrame(
            "The best cut is {${question(root)}}: weighted impurity falls from ${fx(gini(pts))} to ${fx(root.score)}.",
            "Every threshold on both features was tried. This one leaves the other side pure.",
            TreeScene(pts, emptyList(), listOf(rootCut), null, listOf(rootQ, pending(yes), pending(no), null, null)),
            "weighted = {p:${yes.size}}/${pts.size} × ${fx(gini(yes))} + {p:${no.size}}/${pts.size} × ${fx(gini(no))} = {${fx(root.score)}}", legend,
        ),
        MlFrame(
            "The ${if (root.onX) "left" else "lower"} region still mixes {${yes.count { it.third == 1 }} pink and ${yes.count { it.third == 0 }} blue}, so it splits again.",
            "Recursion: the same search, run on these ${yes.size} points only.",
            TreeScene(pts, emptyList(), listOf(rootCut), yesBox, listOf(rootQ, TreeBox("?", "${yes.size} pts", TreeBoxTone.Current), pending(no), null, null)),
            giniText(yes), legend,
        ),
        MlFrame(
            "{${question(inner)}} splits it perfectly: both sides are pure.",
            "Weighted impurity drops from ${fx(gini(yes))} to ${fx(inner.score)}, the lowest it can go.",
            TreeScene(pts, emptyList(), listOf(rootCut, innerCut), yesBox, listOf(rootQ, innerQ, pending(no), pending(yesYes), pending(yesNo))),
            "weighted = {p:${yesYes.size}}/${yes.size} × ${fx(gini(yesYes))} + {p:${yesNo.size}}/${yes.size} × ${fx(gini(yesNo))} = {${fx(inner.score)}}", legend,
        ),
        MlFrame(
            "Pure regions become {leaves}: ${yesYes.size} points → class ${majority(yesYes)}, ${yesNo.size} points → class ${majority(yesNo)}.",
            "A leaf predicts its majority class. Here there is no minority to overrule.",
            TreeScene(pts, listOf(leafYY, leafYN), listOf(rootCut, innerCut), null, listOf(rootQ, innerQ, pending(no), leaf(yesYes), leaf(yesNo))),
            "gini = {m:0.00} on both sides", legend,
        ),
        MlFrame(
            "The ${if (root.onX) "right" else "upper"} region is pure: {${no.size} points, all class ${majority(no)}}.",
            "So it becomes a leaf and stops splitting.",
            TreeScene(pts, listOf(leafYY, leafYN, leafNo), listOf(rootCut, innerCut), noBox, listOf(rootQ, innerQ, leaf(no, current = true), leaf(yesYes), leaf(yesNo))),
            giniText(no), legend,
        ),
        MlFrame(
            "Three leaves from two questions. Any new point needs {at most two comparisons}.",
            "Grown deeper on noisy data, the same greedy search would carve a leaf around every point.",
            TreeScene(pts, listOf(leafYY, leafYN, leafNo), listOf(rootCut, innerCut), null, listOf(rootQ, innerQ, leaf(no), leaf(yesYes), leaf(yesNo))),
            "depth 2 · 3 leaves · {m:${pts.size}/${pts.size}} correct", legend.dropLast(1),
            listOf(LabChip("class 1", "$ones", dot = MlPink), LabChip("class 0", "${pts.size - ones}", dot = SimColors.Blue)),
        ),
    )
    return listOf(MlTab("", frames))
}

// ── Lab ──

private fun mlStoryTabs(topicId: String): List<MlTab> = when (topicId) {
    "bias_variance" -> biasVarianceTabs()
    "regularization" -> regularizationTabs()
    "knn" -> knnTabs()
    else -> decisionTreeTabs()
}

internal fun mlStoryFrameCount(topicId: String): Int = mlStoryTabs(topicId).sumOf { it.frames.size }

@Composable
internal fun MlStorySection(topicId: String) {
    val tabs = remember(topicId) { mlStoryTabs(topicId) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(0) }
    val frames = tabs[tab.coerceIn(0, tabs.lastIndex)].frames
    val playback = rememberPlaybackState(key = topicId to tab, stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (tabs.size > 1) {
                    LabSegments(tabs.map { it.label }, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
                }
                if (topicId == "bias_variance") {
                    // The picker follows the story's degree; a tap jumps to where that degree starts.
                    val degree = biasDegreeOf(frame)
                    LabSegments(biasDegrees.map { "Degree $it" }, biasDegrees.indexOf(degree), Modifier.padding(bottom = 14.dp)) { i ->
                        playback.jump(frames.indexOfFirst { biasDegreeOf(it) == biasDegrees[i] })
                    }
                }
                when (val scene = frame.scene) {
                    is FitScene -> FitView(scene)
                    is PenaltyScene -> PenaltyView(scene)
                    is KnnScene -> KnnView(scene)
                    is TreeScene -> TreeView(scene)
                }
                frame.formula?.let { MlFormula(it, Modifier.padding(top = 12.dp)) }
                val onSurface = MaterialTheme.colorScheme.onSurface
                StoryLegendRow(
                    frame.legend.map { Triple(if (it.color == Color.Unspecified) onSurface else it.color, it.style, it.label) },
                    Modifier.padding(top = 14.dp),
                )
            }
        }
        LabChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

// ── Rendering ──

@Composable
private fun MlFormula(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            storyAnnotated(text),
            fontFamily = IBMPlexMono,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, style: TextStyle, color: Color, at: Offset, anchor: Int = 0) {
    val layout = measurer.measure(text, style)
    val x = when (anchor) {
        -1 -> at.x - layout.size.width
        1 -> at.x
        else -> at.x - layout.size.width / 2f
    }
    drawText(layout, color = color, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

private fun Modifier.stage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

private fun DrawScope.polyline(points: List<Offset>, color: Color, width: Float, dash: PathEffect? = null) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash))
}

@Composable
private fun FitView(scene: FitScene) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val activeInk = StoryTone.Active.ink()
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).stage()) {
        val yLo = -1.6
        val yHi = 1.6
        fun at(x: Double, y: Double) = Offset((x * size.width).toFloat(), ((yHi - y) / (yHi - yLo) * size.height).toFloat())
        drawLine(muted.copy(alpha = 0.3f), at(0.0, 0.0), at(1.0, 0.0), 1.dp.toPx())
        scene.fits.forEach { fit -> polyline(fit.map { at(it.first, it.second) }, SimColors.Blue, 2.dp.toPx()) }
        polyline(
            scene.truth.map { at(it.first, it.second) }, onSurface, 2.5.dp.toPx(),
            PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        scene.dots.forEach { (x, y) ->
            drawCircle(SimColors.Blue, 4.5.dp.toPx(), at(x, y))
            drawCircle(Color.White.copy(alpha = 0.8f), 4.5.dp.toPx(), at(x, y), style = Stroke(1.dp.toPx()))
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(96.dp)) {
        val small = TextStyle(fontSize = 12.sp)
        val mono = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp)
        label(measurer, "error by degree · log scale", small, muted, Offset(0f, 8.dp.toPx()), anchor = 1)
        val top = 22.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val lo = log10(0.005)
        val hi = log10(2.0)
        fun px(d: Int) = 8.dp.toPx() + d * (size.width - 16.dp.toPx()) / 9f
        fun py(e: Double) = bottom - ((log10(e.coerceIn(0.005, 2.0)) - lo) / (hi - lo)).toFloat() * (bottom - top)
        drawLine(muted.copy(alpha = 0.35f), Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        polyline(scene.train.indices.map { Offset(px(it), py(scene.train[it])) }, SimColors.Green, 2.dp.toPx())
        polyline(scene.test.indices.map { Offset(px(it), py(scene.test[it])) }, SimColors.Red, 2.dp.toPx())
        drawLine(
            SimColors.Active, Offset(px(scene.marker), top - 4.dp.toPx()), Offset(px(scene.marker), bottom), 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
        (0..9).filter { it % 3 == 0 || it == scene.marker }.forEach { d ->
            label(measurer, "$d", mono, if (d == scene.marker) activeInk else muted, Offset(px(d), bottom + 10.dp.toPx()))
        }
    }
}

@Composable
private fun PenaltyView(scene: PenaltyScene) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().height(230.dp).stage()) {
        val yLo = -0.1
        val yHi = 1.1
        val pad = 10.dp.toPx()
        fun at(x: Double, y: Double) = Offset(pad + (x * (size.width - 2 * pad)).toFloat(), ((yHi - y) / (yHi - yLo) * size.height).toFloat())
        drawLine(muted.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        clipRect {
            scene.overfit?.let {
                polyline(it.map { p -> at(p.first, p.second) }, SimColors.Red, 1.5.dp.toPx(), PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            polyline(scene.fit.map { at(it.first, it.second) }, SimColors.Answer, 3.dp.toPx())
        }
        scene.dots.forEach { (x, y) ->
            drawCircle(SimColors.Blue, 6.dp.toPx(), at(x, y))
            drawCircle(surface, 6.dp.toPx(), at(x, y), style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun KnnView(scene: KnnScene) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().height(230.dp).stage()) {
        // Equal scale on both axes, so the neighbour circle stays round.
        val unit = size.height / 0.8f
        fun at(x: Double, y: Double) = Offset(size.width / 2 + ((x - 0.5) * unit).toFloat(), size.height / 2 - ((y - 0.5) * unit).toFloat())
        val q = at(scene.query.first, scene.query.second)
        scene.radius?.let { r ->
            val rp = (r * unit).toFloat()
            drawCircle(Color.White.copy(alpha = 0.05f), rp, q)
            drawCircle(SimColors.Active, rp, q, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
        }
        scene.neighbours.forEach { i ->
            drawLine(muted.copy(alpha = 0.6f), q, at(knnPoints[i].first, knnPoints[i].second), 1.5.dp.toPx())
        }
        val r = 6.dp.toPx()
        scene.points.forEach { (x, y, c) ->
            drawCircle(if (c == 1) MlPink else SimColors.Blue, r, at(x, y))
        }
        scene.neighbours.forEachIndexed { n, i ->
            val current = scene.growing && n == scene.neighbours.lastIndex
            drawCircle(
                if (current) SimColors.Active else muted.copy(alpha = 0.85f), r + 4.dp.toPx(), at(knnPoints[i].first, knnPoints[i].second),
                style = Stroke(2.dp.toPx()),
            )
        }
        val d = 8.dp.toPx()
        val diamond = Path().apply {
            moveTo(q.x, q.y - d); lineTo(q.x + d, q.y); lineTo(q.x, q.y + d); lineTo(q.x - d, q.y); close()
        }
        val fill = when (scene.queryLabel) {
            0 -> SimColors.Blue
            1 -> MlPink
            else -> SimColors.Active
        }
        drawPath(diamond, fill)
        drawPath(diamond, if (scene.queryLabel == null) surface else SimColors.Active, style = Stroke(1.5.dp.toPx()))
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        scene.tiles.forEachIndexed { i, tile -> KnnTileView(i + 1, tile, Modifier.weight(1f)) }
    }
}

@Composable
private fun KnnTileView(rank: Int, tile: KnnTile, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(10.dp)
    val (bg, ink) = when (tile.tone) {
        TileTone.Current -> SimColors.Active to Color(0xFF1F1A0A)
        TileTone.Done -> muted.copy(alpha = 0.16f) to MaterialTheme.colorScheme.onSurface
        TileTone.Pending -> Color.Transparent to muted.copy(alpha = 0.6f)
    }
    Column(
        modifier = modifier
            .height(66.dp)
            .then(if (tile.tone == TileTone.Pending) Modifier.dashedOutline(muted.copy(alpha = 0.4f), 10.dp) else Modifier.background(bg, shape)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("#$rank", fontFamily = IBMPlexMono, fontSize = 11.sp, color = ink.copy(alpha = 0.8f))
        Text(tile.distance, fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.padding(top = 2.dp))
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .background(
                    when (tile.label) {
                        0 -> SimColors.Blue
                        1 -> MlPink
                        else -> muted.copy(alpha = 0.4f)
                    },
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun TreeView(scene: TreeScene) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val pinkInk = if (com.algora.app.core.ui.theme.LocalDarkTheme.current) Color(0xFFF9A8D4) else Color(0xFFBE185D)
    val blueInk = StoryTone.Path.ink()
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).stage()) {
        fun at(x: Double, y: Double) = Offset((x * size.width).toFloat(), ((1 - y) * size.height).toFloat())
        fun rect(r: TreeRegion): Pair<Offset, Size> {
            val a = at(r.x0, r.y1)
            val b = at(r.x1, r.y0)
            return a to Size(b.x - a.x, b.y - a.y)
        }
        scene.regions.forEach { r ->
            val (o, s) = rect(r)
            drawRect((if (r.label == 1) MlPink else SimColors.Blue).copy(alpha = 0.16f), o, s)
        }
        scene.splits.forEach { (a, b) -> drawLine(SimColors.Answer, at(a.first, a.second), at(b.first, b.second), 2.5.dp.toPx()) }
        scene.current?.let { r ->
            val (o, s) = rect(r)
            val inset = 2.dp.toPx()
            drawRoundRect(
                SimColors.Active, Offset(o.x + inset, o.y + inset), Size(s.width - 2 * inset, s.height - 2 * inset),
                CornerRadius(10.dp.toPx()), style = Stroke(2.5.dp.toPx()),
            )
        }
        scene.points.forEach { (x, y, c) ->
            drawCircle(if (c == 1) MlPink else SimColors.Blue, 6.dp.toPx(), at(x, y))
            drawCircle(surface, 6.dp.toPx(), at(x, y), style = Stroke(1.5.dp.toPx()))
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(176.dp)) {
        val bw = 118.dp.toPx()
        val bh = 42.dp.toPx()
        val centres = listOf(
            Offset(size.width * 0.5f, bh / 2),
            Offset(size.width * 0.33f, bh / 2 + 66.dp.toPx()),
            Offset(size.width * 0.78f, bh / 2 + 66.dp.toPx()),
            Offset(size.width * 0.17f, bh / 2 + 132.dp.toPx()),
            Offset(size.width * 0.5f, bh / 2 + 132.dp.toPx()),
        )
        val w = minOf(bw, size.width * 0.3f)
        val tiny = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp)
        listOf(Triple(0, 1, "yes"), Triple(0, 2, "no"), Triple(1, 3, "yes"), Triple(1, 4, "no")).forEach { (from, to, text) ->
            if (scene.boxes[to] == null) return@forEach
            val a = Offset(centres[from].x, centres[from].y + bh / 2)
            val b = Offset(centres[to].x, centres[to].y - bh / 2)
            drawLine(muted.copy(alpha = 0.5f), a, b, 1.5.dp.toPx())
            label(measurer, text, tiny, muted, Offset((a.x + b.x) / 2 + if (to % 2 == 1) -14.dp.toPx() else 14.dp.toPx(), (a.y + b.y) / 2 - 4.dp.toPx()))
        }
        scene.boxes.forEachIndexed { i, box ->
            box ?: return@forEachIndexed
            val c = centres[i]
            val topLeft = Offset(c.x - w / 2, c.y - bh / 2)
            val corner = CornerRadius(8.dp.toPx())
            val (fill, border, ink) = when (box.tone) {
                TreeBoxTone.Current -> Triple(SimColors.Active, SimColors.Active, Color(0xFF1F1A0A))
                TreeBoxTone.Leaf1 -> Triple(MlPink.copy(alpha = 0.18f), MlPink, pinkInk)
                TreeBoxTone.Leaf0 -> Triple(SimColors.Blue.copy(alpha = 0.18f), SimColors.Blue, blueInk)
                TreeBoxTone.Question -> Triple(SimColors.Answer.copy(alpha = 0.12f), SimColors.Answer.copy(alpha = 0.7f), onSurface)
                TreeBoxTone.Pending -> Triple(Color.Transparent, muted.copy(alpha = 0.45f), muted)
            }
            drawRoundRect(fill, topLeft, Size(w, bh), corner)
            drawRoundRect(
                border, topLeft, Size(w, bh), corner,
                style = Stroke(1.5.dp.toPx(), pathEffect = if (box.tone == TreeBoxTone.Pending) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null),
            )
            label(measurer, box.title, TextStyle(fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.Bold), ink, Offset(c.x, c.y - 8.dp.toPx()))
            label(measurer, box.caption, TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp), ink.copy(alpha = 0.8f), Offset(c.x, c.y + 9.dp.toPx()))
        }
    }
}

