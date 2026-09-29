package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ── Evaluation storyboards ───────────────────────────────────────────────────
// Davies-Bouldin, Silhouette, Hinge Loss, Gini Impurity, Adjusted R², MSE and R², each one figure (a
// clustering with a score per k, a loss curve, a node of samples or a fitted line), the arithmetic of the
// current setting, chips and a headline, then a stepper (or a picker over two) and one action. Every
// number is computed from the fixed data below.

internal val evalStoryTopicIds = setOf(
    "davies_bouldin", "silhouette_score", "hinge_loss", "gini_impurity", "adjusted_r_squared", "mse", "r_squared",
)

private val ClusterColors = listOf(
    SimColors.Blue, CategoryAccents.Pink, SimColors.Green, Color(0xFFF97316), Color(0xFF8B5CF6), Color(0xFF14B8A6),
)

// ── Formatting and small math ──

private fun vx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun ladder(from: Double, to: Double, step: Double) = (0..((to - from) / step).roundToInt()).map { from + it * step }

private fun nearest(values: List<Double>, v: Double) = values.indices.minBy { abs(values[it] - v) }

private class EvRng(seed: Int) {
    private val random = Random(seed)
    fun next() = random.nextDouble()
    fun normal(): Double {
        val u = max(random.nextDouble(), 1e-12)
        return sqrt(-2 * ln(u)) * cos(2 * PI * random.nextDouble())
    }
}

private class EvV2(val x: Double, val y: Double)

private fun dist(a: EvV2, b: EvV2) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

/** Lloyd's k-means from a farthest-point start at point 0: deterministic, so both platforms agree. */
private fun kMeans(pts: List<EvV2>, k: Int): IntArray {
    val centres = mutableListOf(pts[0])
    while (centres.size < k) centres += pts.maxBy { p -> centres.minOf { dist(p, it) } }
    val labels = IntArray(pts.size)
    repeat(60) {
        pts.forEachIndexed { i, p -> labels[i] = centres.indices.minBy { dist(p, centres[it]) } }
        for (c in 0 until k) {
            val members = pts.indices.filter { labels[it] == c }
            if (members.isNotEmpty()) centres[c] = EvV2(members.sumOf { pts[it].x } / members.size, members.sumOf { pts[it].y } / members.size)
        }
    }
    return labels
}

private fun centroids(pts: List<EvV2>, labels: IntArray, k: Int) = (0 until k).map { c ->
    val m = pts.indices.filter { labels[it] == c }
    if (m.isEmpty()) EvV2(0.0, 0.0) else EvV2(m.sumOf { pts[it].x } / m.size, m.sumOf { pts[it].y } / m.size)
}

private class EvDbResult(val score: Double, val worst: Pair<Int, Int>, val spread: List<Double>, val gap: Double)

private fun daviesBouldin(pts: List<EvV2>, labels: IntArray, k: Int): EvDbResult {
    val cs = centroids(pts, labels, k)
    val s = (0 until k).map { c -> pts.indices.filter { labels[it] == c }.map { dist(pts[it], cs[c]) }.average() }
    var worst = 0 to 1
    var worstR = -1.0
    val total = (0 until k).sumOf { i ->
        (0 until k).filter { it != i }.maxOf { j ->
            val r = (s[i] + s[j]) / max(dist(cs[i], cs[j]), 1e-9)
            if (r > worstR) { worstR = r; worst = i to j }
            r
        }
    }
    return EvDbResult(total / k, worst, s, dist(cs[worst.first], cs[worst.second]))
}

private fun silhouetteOf(pts: List<EvV2>, labels: IntArray, k: Int, i: Int): Triple<Double, Double, Int> {
    fun meanTo(c: Int) = pts.indices.filter { labels[it] == c && it != i }.map { dist(pts[i], pts[it]) }.let { if (it.isEmpty()) 0.0 else it.average() }
    val a = meanTo(labels[i])
    val others = (0 until k).filter { it != labels[i] && labels.contains(it) }
    val nearest = others.minBy { meanTo(it) }
    return Triple(a, meanTo(nearest), nearest)
}

private fun meanSilhouette(pts: List<EvV2>, labels: IntArray, k: Int) = pts.indices.map { i ->
    val (a, b, _) = silhouetteOf(pts, labels, k, i)
    if (max(a, b) == 0.0) 0.0 else (b - a) / max(a, b)
}.average()

/** Gaussian elimination with partial pivoting and a hair of ridge, for the normal equations. */
private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] + (if (i == j) 1e-9 else 0.0) else b[i] } }
    for (c in 0 until n) {
        val p = (c until n).maxBy { abs(m[it][c]) }
        val t = m[c]; m[c] = m[p]; m[p] = t
        for (r in 0 until n) if (r != c) {
            val f = m[r][c] / m[c][c]
            for (j in c..n) m[r][j] -= f * m[c][j]
        }
    }
    return DoubleArray(n) { m[it][n] / m[it][it] }
}

private fun rSquaredOf(cols: List<DoubleArray>, y: DoubleArray): Double {
    val n = y.size
    val x = (0 until n).map { i -> DoubleArray(cols.size + 1) { j -> if (j == 0) 1.0 else cols[j - 1][i] } }
    val d = cols.size + 1
    val xtx = Array(d) { r -> DoubleArray(d) { c -> x.sumOf { it[r] * it[c] } } }
    val xty = DoubleArray(d) { r -> (0 until n).sumOf { x[it][r] * y[it] } }
    val beta = solve(xtx, xty)
    val mean = y.average()
    val res = (0 until n).sumOf { i -> (y[i] - x[i].indices.sumOf { beta[it] * x[i][it] }).let { e -> e * e } }
    val tot = y.sumOf { (it - mean) * (it - mean) }
    return 1 - res / tot
}

// ── Data ──

/** Two concentric rings: a small inner one and a wide outer one. */
private val ringData: Pair<List<EvV2>, IntArray> by lazy {
    val r = EvRng(12)
    val inner = (0 until 50).map { val t = r.next() * 2 * PI; val rad = 1.0 + r.normal() * 0.12; EvV2(rad * cos(t), rad * sin(t)) }
    val outer = (0 until 90).map { val t = r.next() * 2 * PI; val rad = 3.0 + r.normal() * 0.12; EvV2(rad * cos(t), rad * sin(t)) }
    (inner + outer) to IntArray(140) { if (it < 50) 0 else 1 }
}

/** Three blobs: one on top, two below. */
private val blobData: List<EvV2> by lazy {
    val r = EvRng(4)
    val centres = listOf(EvV2(0.0, 2.6) to EvV2(0.55, 0.3), EvV2(-3.2, -1.2) to EvV2(0.65, 0.3), EvV2(3.0, -1.4) to EvV2(0.5, 0.4))
    centres.flatMap { (c, s) -> (0 until 22).map { EvV2(c.x + r.normal() * s.x, c.y + r.normal() * s.y) } }
}

private const val REG_N = 44
private val outlierIdx = setOf(33, 36, 39, 42)

/** A straight trend with four points far above it at the right end. */
private val fitData: Pair<DoubleArray, DoubleArray> by lazy {
    val r = EvRng(9)
    val xs = DoubleArray(REG_N) { it * 0.23 }
    val ys = DoubleArray(REG_N) { i -> 1.2 * xs[i] + 2 + (r.next() - 0.5) * 2.0 + if (i in outlierIdx) 14.0 else 0.0 }
    xs to ys
}

private fun leastSquaresLine(xs: DoubleArray, ys: DoubleArray, w: DoubleArray? = null): Pair<Double, Double> {
    val ww = w ?: DoubleArray(xs.size) { 1.0 }
    val sw = ww.sum()
    val mx = xs.indices.sumOf { ww[it] * xs[it] } / sw
    val my = xs.indices.sumOf { ww[it] * ys[it] } / sw
    val m = xs.indices.sumOf { ww[it] * (xs[it] - mx) * (ys[it] - my) } / xs.indices.sumOf { ww[it] * (xs[it] - mx) * (xs[it] - mx) }
    return m to my - m * mx
}

/** Least absolute deviations by iteratively reweighted least squares. */
private val maeLine: Pair<Double, Double> by lazy {
    val (xs, ys) = fitData
    var line = leastSquaresLine(xs, ys)
    repeat(80) {
        val w = DoubleArray(xs.size) { 1 / max(abs(ys[it] - (line.first * xs[it] + line.second)), 1e-3) }
        line = leastSquaresLine(xs, ys, w)
    }
    line
}

// ── Scenes ──

private sealed interface EvScene

private class EvClusterScene(
    val points: List<EvV2>,
    val labels: IntArray,
    val centres: List<EvV2>,
    val link: Pair<EvV2, EvV2>? = null,
    val scored: Int? = null,
    val own: EvV2? = null,
    val other: EvV2? = null,
    val bars: List<Double> = emptyList(),
    val selected: Int = -1,
    val barBelow: Boolean = true,
) : EvScene

private class EvSeries(val points: List<Pair<Double, Double>>, val color: Color?, val dashed: Boolean = false, val width: Float = 2.5f)

private class EvMarker(val x: Double, val y: Double, val color: Color)

private class EvCurve(
    val series: List<EvSeries>,
    val markers: List<EvMarker>,
    val xRange: Pair<Double, Double>,
    val yRange: Pair<Double, Double>,
    val xLabels: Triple<String, String, String>,
    val yLabels: Pair<String, String>,
    val yTitle: String? = null,
    val vline: Double? = null,
    val band: Pair<Double, Double>? = null,
    val tiles: List<Int>? = null,
    val splitAt: Int? = null,
) : EvScene

private class EvFitScene(
    val slope: Double,
    val intercept: Double,
    val showMae: Boolean,
    val toMean: Boolean,
    val outlierShare: Double? = null,
    val ss: Pair<Double, Double>? = null,
) : EvScene

private class EvFrame(
    val headline: String,
    val body: String,
    val scene: EvScene,
    val formula: List<String> = emptyList(),
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
)

private class EvParam(val tab: String, val name: String, val symbol: String, val values: List<Double>, val format: (Double) -> String)

/** idx: each parameter's index; sel: the picker; flag: the action's own state. */
private data class EvState(val idx: List<Int>, val sel: Int = 0, val flag: Int = 0)

private class EvLab(
    val initial: EvState,
    val params: List<EvParam>,
    val frame: (EvState) -> EvFrame,
    val action: ((EvState) -> String)? = null,
    val onAction: (EvState) -> EvState = { it },
    /** Two parameters and the action share one row under a picker (Slope m / Intercept c). */
    val paired: Boolean = false,
    val navReset: Boolean = false,
)

// ── Labs ──

private val ks = (2..6).toList()

private fun daviesBouldinLab(): EvLab {
    val (pts, truth) = ringData
    val fits = ks.map { k -> kMeans(pts, k) }
    val scores = ks.indices.map { daviesBouldin(pts, fits[it], ks[it]) }
    val best = scores.indices.minBy { scores[it].score }
    val trueDb = daviesBouldin(pts, truth, 2)
    return EvLab(
        initial = EvState(listOf(best)),
        params = listOf(EvParam("Clusters", "Clusters", "k", ks.map { it.toDouble() }) { "${it.roundToInt()}" }),
        action = { if (it.flag == 0) "Show True Rings" else "Back to k-means" },
        onAction = { it.copy(flag = 1 - it.flag) },
        navReset = true,
        frame = { s ->
            val i = s.idx[0]
            val k = ks[i]
            if (s.flag == 1) {
                val cs = centroids(pts, truth, 2)
                EvFrame(
                    "The true rings score DB {w:${vx(trueDb.score)}}: both centroids sit near the middle.",
                    "DB rewards compact, well-separated blobs. Rings are neither, so it prefers any cut into arcs.",
                    EvClusterScene(pts, truth, cs, link = cs[0] to cs[1], bars = scores.map { it.score }, selected = -1),
                    formula = listOf("rings: (${vx(trueDb.spread[0])} + ${vx(trueDb.spread[1])}) / ${vx(trueDb.gap)} = {w:${vx(trueDb.score)}}"),
                    legend = listOf(Triple(Color.White, SwatchStyle.Ring, "Centroid"), Triple(SimColors.Active, SwatchStyle.DashedLine, "Centroid gap")),
                )
            } else {
                val r = scores[i]
                val cs = centroids(pts, fits[i], k)
                val (a, b) = r.worst
                EvFrame(
                    if (i == best) "Lowest DB is at {w:k = $k}, but the data is two rings."
                    else "At k = $k DB is ${vx(r.score)}; the lowest is {w:k = ${ks[best]}}.",
                    "DB measures spread around centroids, and a ring's centroid sits in empty space.",
                    EvClusterScene(pts, fits[i], cs, link = cs[a] to cs[b], bars = scores.map { it.score }, selected = i),
                    formula = listOf("worst pair: (${vx(r.spread[a])} + ${vx(r.spread[b])}) / ${vx(r.gap)} = {v:${vx((r.spread[a] + r.spread[b]) / r.gap)}}"),
                    legend = listOf(Triple(Color.White, SwatchStyle.Ring, "Centroid"), Triple(SimColors.Active, SwatchStyle.DashedLine, "Most similar pair")),
                )
            }
        },
    )
}

private val scoredPoints = listOf(8, 30, 50, 14, 60)

private fun silhouetteLab(): EvLab {
    val pts = blobData
    val fits = ks.map { k -> kMeans(pts, k) }
    val means = ks.indices.map { meanSilhouette(pts, fits[it], ks[it]) }
    val best = means.indices.maxBy { means[it] }
    return EvLab(
        initial = EvState(listOf(best)),
        params = listOf(EvParam("Clusters", "Clusters", "k", ks.map { it.toDouble() }) { "${it.roundToInt()}" }),
        action = { "Score Another Point" },
        onAction = { it.copy(flag = (it.flag + 1) % scoredPoints.size) },
        navReset = true,
        frame = { s ->
            val i = s.idx[0]
            val k = ks[i]
            val labels = fits[i]
            val cs = centroids(pts, labels, k)
            val p = scoredPoints[s.flag]
            val (a, b, near) = silhouetteOf(pts, labels, k, p)
            val sil = (b - a) / max(a, b)
            val (headline, body) = if (sil >= 0) {
                "This point is {${vx(b / max(a, 1e-9), 1)}×} closer to its own cluster than to the next." to
                    "It needs no labels, so it can be swept over k; it peaks at k = ${ks[best]}."
            } else {
                "This point sits {w:closer to another cluster}: s = ${vx(sil)}." to
                    "A negative silhouette means k-means put it on the wrong side. The average over all points peaks at k = ${ks[best]}."
            }
            EvFrame(
                headline,
                body,
                EvClusterScene(pts, labels, emptyList(), scored = p, own = cs[labels[p]], other = cs[near], bars = means, selected = i),
                formula = listOf("s = (b − a) / max(a, b) = (${vx(b)} − ${vx(a)}) / ${vx(max(a, b))} = {v:${vx(sil)}}"),
                legend = listOf(
                    Triple(SimColors.Active, SwatchStyle.Dot, "Scored point"),
                    Triple(Color(0xFFB8A27A), SwatchStyle.Line, "a: own cluster"),
                    Triple(SimColors.Grey, SwatchStyle.DashedLine, "b: nearest other"),
                ),
            )
        },
    )
}

private val margins = ladder(-2.0, 3.0, 0.1)

private fun logistic(m: Double) = ln(1 + exp(-m)) / ln(2.0)

private fun hingeLossLab() = EvLab(
    initial = EvState(listOf(nearest(margins, 0.4))),
    params = listOf(EvParam("Margin", "Margin", "y·f(x)", margins) { vx(it) }),
    frame = { s ->
        val m = margins[s.idx[0]]
        val hinge = max(0.0, 1 - m)
        val zeroOne = if (m < 0) 1.0 else 0.0
        val xs = ladder(-2.0, 3.0, 0.05)
        val (headline, body) = when {
            m < 0 -> "This point is {w:misclassified} and costs ${vx(hinge)}." to
                "Hinge grows linearly with the violation, so one bad point can't dominate the way a squared loss would."
            m < 1 -> "This point is {m:classified correctly} but still costs ${vx(hinge)}." to
                "It sits inside the margin, so hinge keeps pushing. 0-1 loss is flat here and gives no gradient."
            else -> "This point is {m:outside the margin}, so hinge is exactly 0." to
                "Logistic still charges ${vx(logistic(m))} and never quite reaches zero; hinge stops pushing once the margin is met."
        }
        EvFrame(
            headline,
            body,
            EvCurve(
                listOf(
                    EvSeries(xs.map { it to (if (it < 0) 1.0 else 0.0) }, SimColors.Grey, width = 2f),
                    EvSeries(xs.map { it to logistic(it) }, SimColors.Blue),
                    EvSeries(xs.map { it to max(0.0, 1 - it) }, null),
                ),
                markers = listOf(EvMarker(m, zeroOne, SimColors.Grey), EvMarker(m, logistic(m), SimColors.Blue), EvMarker(m, hinge, SimColors.Active)),
                xRange = -2.0 to 3.0,
                yRange = 0.0 to 3.0,
                xLabels = Triple("−2", "margin y · f(x)", "3"),
                yLabels = "0" to "3",
                yTitle = "loss",
                vline = m,
                band = 0.0 to 1.0,
            ),
            formula = listOf("hinge = max(0, 1 − ${vx(m)}) = {v:${vx(hinge)}}   0-1 = ${zeroOne.roundToInt()}"),
            legend = listOf(
                Triple(Color.Unspecified, SwatchStyle.Line, "Hinge"),
                Triple(SimColors.Blue, SwatchStyle.Line, "Logistic"),
                Triple(SimColors.Grey, SwatchStyle.Line, "0-1 loss"),
                Triple(Color(0xFF6B5E3C), SwatchStyle.Fill, "Margin"),
            ),
            chips = listOf(LabChip("margin", vx(m), tint = StoryTone.Active), LabChip("logistic", vx(logistic(m)), tint = StoryTone.Path)),
        )
    },
)

private val shares = ladder(0.0, 1.0, 0.1)

private fun entropy(p: Double) = if (p <= 0 || p >= 1) 0.0 else -(p * ln(p) + (1 - p) * ln(1 - p)) / ln(2.0)

private fun gini(p: Double) = 1 - p * p - (1 - p) * (1 - p)

private fun giniLab() = EvLab(
    initial = EvState(listOf(3)),
    params = listOf(EvParam("Class 1 share", "Class 1 share", "p", shares) { vx(it) }),
    action = { if (it.flag == 0) "Score a Split" else "Back to One Node" },
    onAction = { it.copy(flag = 1 - it.flag) },
    frame = { s ->
        val p = shares[s.idx[0]]
        val pink = (p * 10).roundToInt()
        val g = gini(p)
        val xs = ladder(0.0, 1.0, 0.01)
        val curve = EvCurve(
            listOf(
                EvSeries(xs.map { it to min(it, 1 - it) }, SimColors.Grey, width = 2f),
                EvSeries(xs.map { it to entropy(it) }, SimColors.Blue),
                EvSeries(xs.map { it to gini(it) }, null),
            ),
            markers = listOf(EvMarker(p, min(p, 1 - p), SimColors.Grey), EvMarker(p, entropy(p), SimColors.Blue), EvMarker(p, g, SimColors.Active)),
            xRange = 0.0 to 1.0,
            yRange = 0.0 to 1.0,
            xLabels = Triple("0", "share of class 1 in the node", "1"),
            yLabels = "0" to "1",
            yTitle = "impurity",
            vline = p,
            tiles = List(10) { if (it < pink) 1 else 0 },
            splitAt = if (s.flag == 1 && pink in 1..9) pink + 1 else null,
        )
        val legend = listOf(
            Triple(Color.Unspecified, SwatchStyle.Line, "Gini"),
            Triple(SimColors.Blue, SwatchStyle.Line, "Entropy (bits)"),
            Triple(SimColors.Grey, SwatchStyle.Line, "Misclassification"),
        )
        val chips = listOf(LabChip("entropy", vx(entropy(p), 3), tint = StoryTone.Path), LabChip("misclass", vx(min(p, 1 - p))))
        if (s.flag == 1 && pink in 1..9) {
            // The split sends every class-1 sample and one class-0 sample left, the rest right.
            val left = pink + 1
            val right = 10 - left
            val gl = gini(pink.toDouble() / left)
            val weighted = left / 10.0 * gl
            EvFrame(
                "The split drops Gini from ${vx(g)} to {m:${vx(weighted)}}.",
                "A tree tries every split and keeps the one with the largest drop, weighting each child by its size. The right child is pure, so it adds nothing.",
                curve,
                formula = listOf("$left/10 × ${vx(gl)} + $right/10 × 0.00 = {v:${vx(weighted)}}", "gain = ${vx(g)} − ${vx(weighted)} = ${vx(g - weighted)}"),
                legend = legend,
                chips = chips,
            )
        } else if (pink == 0 || pink == 10) {
            EvFrame(
                "A pure node: Gini is {0}.",
                "Every sample carries the same label, so there is nothing left to split. Both curves bottom out at the edges.",
                curve,
                formula = listOf("Gini = 1 − (${vx(p, 1)}² + ${vx(1 - p, 1)}²) = {v:0.00}"),
                legend = legend,
                chips = chips,
            )
        } else {
            EvFrame(
                "Two samples from this node carry {different labels} ${(g * 100).roundToInt()}% of the time.",
                "Gini peaks at 0.5 and entropy at 1.0. Both curve, so they reward purer splits more than misclassification does.",
                curve,
                formula = listOf("Gini = 1 − (${vx(p, 1)}² + ${vx(1 - p, 1)}²) = {v:${vx(g)}}"),
                legend = legend,
                chips = chips,
            )
        }
    },
)

private const val NOISE_MAX = 20

private val adjustedCurve: List<Pair<Double, Double>> by lazy {
    val r = EvRng(3)
    val x = DoubleArray(REG_N) { r.next() }
    val y = DoubleArray(REG_N) { x[it] + r.normal() * 0.2 }
    val noise = (0 until NOISE_MAX).map { DoubleArray(REG_N) { r.normal() } }
    (0..NOISE_MAX).map { q ->
        val r2 = rSquaredOf(listOf(x) + noise.take(q), y)
        val p = q + 1
        r2 to 1 - (1 - r2) * (REG_N - 1) / (REG_N - p - 1)
    }
}

private fun adjustedLab() = EvLab(
    initial = EvState(listOf(12)),
    params = listOf(EvParam("Noise columns", "Noise columns", "p", (0..NOISE_MAX).map { it.toDouble() }) { "${it.roundToInt()}" }),
    navReset = true,
    frame = { s ->
        val q = s.idx[0]
        val (r2, adj) = adjustedCurve[q]
        val (r20, adj0) = adjustedCurve[0]
        val p = q + 1
        val lo = (adjustedCurve.minOf { min(it.first, it.second) } * 20).toInt() / 20.0
        val hi = ((adjustedCurve.maxOf { max(it.first, it.second) } * 20).toInt() + 1) / 20.0
        val (headline, body) = if (q == 0) {
            "With only the real feature, R² is {${vx(r2, 3)}} and adjusted R² ${vx(adj, 3)}." to
                "Add noise columns and watch which one notices."
        } else {
            "$q column${if (q == 1) "" else "s"} of pure noise push R² {up} to ${vx(r2, 3)}." to
                "Least squares can always use a new column to fit noise. Adjusted R² charges for each one, and falls to ${vx(adj, 3)}."
        }
        EvFrame(
            headline,
            body,
            EvCurve(
                listOf(
                    EvSeries(adjustedCurve.mapIndexed { i, v -> i.toDouble() to v.first }, null),
                    EvSeries(adjustedCurve.mapIndexed { i, v -> i.toDouble() to v.second }, SimColors.Grey, dashed = true),
                ),
                markers = listOf(EvMarker(q.toDouble(), r2, Color.Unspecified), EvMarker(q.toDouble(), adj, SimColors.Active)),
                xRange = 0.0 to NOISE_MAX.toDouble(),
                yRange = lo to hi,
                xLabels = Triple("0", "noise columns added", "$NOISE_MAX"),
                yLabels = vx(lo) to vx(hi),
                vline = q.toDouble(),
            ),
            formula = listOf(
                "adj = 1 − (1 − R²)(n − 1)/(n − p − 1)",
                "= 1 − (1 − ${vx(r2, 3)}) × ${REG_N - 1}/${REG_N - p - 1} = {v:${vx(adj, 3)}}",
            ),
            legend = listOf(
                Triple(Color.Unspecified, SwatchStyle.Line, "R²"),
                Triple(SimColors.Grey, SwatchStyle.DashedLine, "Adjusted R²"),
                Triple(SimColors.Active, SwatchStyle.DashedLine, "Current"),
            ),
            chips = listOf(
                LabChip("R²", "${vx(r20, 3)} → ${vx(r2, 3)}", tint = StoryTone.Answer),
                LabChip("adj", "${vx(adj0, 3)} → ${vx(adj, 3)}", tint = StoryTone.Active),
            ),
        )
    },
)

private val slopes = ladder(0.0, 3.0, 0.01)
private val intercepts = ladder(-5.0, 10.0, 0.1)

private val lsLine: Pair<Double, Double> by lazy { leastSquaresLine(fitData.first, fitData.second) }

private fun lineLab(rSquared: Boolean) = EvLab(
    initial = EvState(listOf(nearest(slopes, lsLine.first), nearest(intercepts, lsLine.second))),
    params = listOf(
        EvParam("Slope m", "Slope", "m", slopes) { vx(it) },
        EvParam("Intercept c", "Intercept", "c", intercepts) { vx(it) },
    ),
    action = { "Least Squares" },
    onAction = { it.copy(idx = listOf(nearest(slopes, lsLine.first), nearest(intercepts, lsLine.second))) },
    paired = true,
    navReset = true,
    frame = { s ->
        val m = slopes[s.idx[0]]
        val c = intercepts[s.idx[1]]
        val (xs, ys) = fitData
        val res = DoubleArray(REG_N) { ys[it] - (m * xs[it] + c) }
        val sq = res.sumOf { it * it }
        val mse = sq / REG_N
        val mae = res.sumOf { abs(it) } / REG_N
        val outSq = outlierIdx.sumOf { res[it] * res[it] }
        val share = outSq / sq
        val mean = ys.average()
        val ssTot = ys.sumOf { (it - mean) * (it - mean) }
        val r2 = 1 - sq / ssTot
        if (!rSquared) {
            EvFrame(
                "{w:${outlierIdx.size} outliers}, ${(outlierIdx.size * 100.0 / REG_N).roundToInt()}% of the data, cause ${(share * 100).roundToInt()}% of the squared error.",
                "Squaring makes the worst points count most, so least squares tilts toward them. The MAE fit stays with the bulk.",
                EvFitScene(m, c, showMae = true, toMean = false, outlierShare = share),
                legend = listOf(
                    Triple(Color.Unspecified, SwatchStyle.Line, "Least squares"),
                    Triple(SimColors.Grey, SwatchStyle.DashedLine, "MAE fit"),
                    Triple(SimColors.Red, SwatchStyle.Ring, "Outlier"),
                ),
                chips = listOf(LabChip("MSE", vx(mse), tint = StoryTone.Answer), LabChip("MAE", vx(mae))),
            )
        } else {
            val (headline, body) = if (r2 < 0) {
                "Your line is {w:worse than the mean}: R² is ${vx(r2, 3)}." to
                    "A flat line at the mean scores 0. Anything below that fits worse than not modelling at all."
            } else {
                "Your line removes {${(r2 * 100).roundToInt()}%} of the spread around the mean." to
                    "R² only compares against a flat line. A high score says you beat the mean, not that the model is useful."
            }
            EvFrame(
                headline,
                body,
                EvFitScene(m, c, showMae = false, toMean = true, ss = ssTot to sq),
                formula = listOf("R² = 1 − ${sq.roundToInt()} / ${ssTot.roundToInt()} = {v:${vx(r2, 3)}}"),
                legend = listOf(Triple(Color.Unspecified, SwatchStyle.Line, "Your line"), Triple(SimColors.Grey, SwatchStyle.DashedLine, "Mean of y")),
            )
        }
    },
)

private fun evalLab(topicId: String): EvLab = when (topicId) {
    "silhouette_score" -> silhouetteLab()
    "hinge_loss" -> hingeLossLab()
    "gini_impurity" -> giniLab()
    "adjusted_r_squared" -> adjustedLab()
    "mse" -> lineLab(rSquared = false)
    "r_squared" -> lineLab(rSquared = true)
    else -> daviesBouldinLab()
}

// ── Lab ──

@Composable
internal fun EvalStorySection(topicId: String) {
    val lab = remember(topicId) { evalLab(topicId) }
    var state by remember(topicId) { mutableStateOf(lab.initial) }
    val frame = remember(state) { lab.frame(state) }
    val dock = LocalLabDock.current

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val params = lab.params.mapIndexed { i, p ->
                val at = state.idx[i]
                LabParam(p.tab, p.name, p.symbol, p.format(p.values[at]), at > 0, at < p.values.lastIndex)
            }
            fun step(i: Int, d: Int) {
                state = state.copy(idx = state.idx.toMutableList().also { it[i] = (it[i] + d).coerceIn(0, lab.params[i].values.lastIndex) })
            }
            if (lab.paired && lab.action != null) {
                LabParamActionControls(
                    params,
                    state.sel,
                    onSelect = { state = state.copy(sel = it) },
                    onStep = ::step,
                    action = lab.action.invoke(state),
                    onAction = { state = lab.onAction(state) },
                )
            } else {
                LabParamControls(params, state.sel, onSelect = { state = state.copy(sel = it) }, onStep = ::step)
                lab.action?.let { label ->
                    LabButton(label(state), primary = true, modifier = Modifier.fillMaxWidth()) { state = lab.onAction(state) }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val scene = frame.scene
                when (scene) {
                    is EvClusterScene -> {
                        ClusterView(scene)
                        if (scene.scored != null) {
                            if (frame.formula.isNotEmpty()) EvFormula(frame.formula, Modifier.padding(top = 12.dp))
                            KBars(scene.bars, scene.selected, Modifier.padding(top = 14.dp))
                        } else {
                            KBars(scene.bars, scene.selected, Modifier.padding(top = 14.dp))
                            if (frame.formula.isNotEmpty()) EvFormula(frame.formula, Modifier.padding(top = 12.dp))
                        }
                    }
                    is EvCurve -> {
                        scene.tiles?.let { NodeTiles(it, scene.splitAt, Modifier.padding(bottom = 12.dp)) }
                        EvCurveView(scene)
                        if (frame.formula.isNotEmpty()) EvFormula(frame.formula, Modifier.padding(top = 12.dp))
                    }
                    is EvFitScene -> {
                        FitView(scene)
                        scene.outlierShare?.let { ShareBar(it, Modifier.padding(top = 12.dp)) }
                        scene.ss?.let { SsBars(it, Modifier.padding(top = 12.dp)) }
                        if (frame.formula.isNotEmpty()) EvFormula(frame.formula, Modifier.padding(top = 12.dp))
                    }
                }
                val accent = MaterialTheme.colorScheme.primary
                StoryLegendRow(frame.legend.map { Triple(if (it.first == Color.Unspecified) accent else it.first, it.second, it.third) }, Modifier.padding(top = 14.dp))
            }
        }
        LabChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = if (lab.navReset) LabNavAction(Icons.Filled.Refresh, "Reset") { state = lab.initial } else null
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

// ── Rendering ──

private fun Modifier.stage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

@Composable
private fun EvFormula(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            Text(
                storyAnnotated(line),
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ClusterView(scene: EvClusterScene) {
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).stage()) {
        val pts = scene.points
        val xLo = pts.minOf { it.x }
        val xHi = pts.maxOf { it.x }
        val yLo = pts.minOf { it.y }
        val yHi = pts.maxOf { it.y }
        val pad = 18.dp.toPx()
        val scale = min((size.width - 2 * pad) / (xHi - xLo), (size.height - 2 * pad) / (yHi - yLo)).toFloat()
        val cx = size.width / 2 - ((xLo + xHi) / 2).toFloat() * scale
        val cy = size.height / 2 + ((yLo + yHi) / 2).toFloat() * scale
        fun at(p: EvV2) = Offset(cx + p.x.toFloat() * scale, cy - p.y.toFloat() * scale)
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        scene.scored?.let { i ->
            scene.own?.let { drawLine(Color(0xFFB8A27A), at(pts[i]), at(it), strokeWidth = 1.5.dp.toPx()) }
            scene.other?.let { drawLine(SimColors.Grey, at(pts[i]), at(it), strokeWidth = 1.5.dp.toPx(), pathEffect = dash) }
        }
        pts.forEachIndexed { i, p ->
            drawCircle(ClusterColors[scene.labels[i] % ClusterColors.size], 3.5.dp.toPx(), at(p))
        }
        scene.link?.let { (a, b) -> drawLine(SimColors.Active, at(a), at(b), strokeWidth = 1.5.dp.toPx(), pathEffect = dash) }
        scene.centres.forEach { c ->
            drawCircle(surface, 6.dp.toPx(), at(c))
            drawCircle(Color.White, 6.dp.toPx(), at(c), style = Stroke(width = 2.dp.toPx()))
        }
        scene.scored?.let { i ->
            drawCircle(SimColors.Active, 5.dp.toPx(), at(pts[i]))
            drawCircle(SimColors.Active, 9.dp.toPx(), at(pts[i]), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun KBars(values: List<Double>, selected: Int, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val top = values.maxOrNull() ?: 1.0
    Row(modifier = modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val on = i == selected
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vx(v), fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Color(0xFFB4A2FF) else MaterialTheme.colorScheme.onSurface)
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height((64f * (v / top).toFloat()).coerceIn(6f, 64f).dp)
                        .background(if (on) accent else SimColors.Tint, RoundedCornerShape(8.dp))
                        .then(if (on) Modifier.border(2.dp, SimColors.Active, RoundedCornerShape(8.dp)) else Modifier),
                )
                Text("k=${ks[i]}", fontFamily = IBMPlexMono, fontSize = 12.sp, color = if (on) StoryTone.Active.ink() else muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun NodeTiles(tiles: List<Int>, splitAt: Int?, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Node · ${tiles.size} samples", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            tiles.forEachIndexed { i, t ->
                if (splitAt != null && i == splitAt) Box(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(26.dp).background(if (t == 1) CategoryAccents.Pink else SimColors.Blue, RoundedCornerShape(6.dp)))
            }
        }
    }
}

@Composable
private fun EvCurveView(scene: EvCurve) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).stage()) {
        val left = (if (scene.yTitle != null) 44 else 38).dp.toPx()
        val right = size.width - 14.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        val (x0, x1) = scene.xRange
        val (y0, y1) = scene.yRange
        fun px(x: Double) = left + ((x - x0) / (x1 - x0)).toFloat() * (right - left)
        fun py(y: Double) = bottom - ((y.coerceIn(y0, y1) - y0) / (y1 - y0)).toFloat() * (bottom - top)
        scene.band?.let { (a, b) -> drawRect(Color(0xFF6B5E3C).copy(alpha = 0.35f), Offset(px(a), top), Size(px(b) - px(a), bottom - top)) }
        drawLine(outline, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
        drawLine(outline, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        scene.vline?.let { drawLine(SimColors.Active, Offset(px(it), top), Offset(px(it), bottom), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))) }
        scene.series.forEach { s ->
            val path = Path()
            s.points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
            drawPath(path, s.color ?: accent, style = Stroke(width = s.width.dp.toPx(), pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null))
        }
        scene.markers.forEach { m ->
            val c = Offset(px(m.x), py(m.y))
            val color = if (m.color == Color.Unspecified) accent else m.color
            drawCircle(color, 6.dp.toPx(), c)
            drawCircle(surface, 6.dp.toPx(), c, style = Stroke(width = 1.5.dp.toPx()))
        }
        val label = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted)
        fun text(s: String, at: Offset) {
            val l = measurer.measure(s, label)
            drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f))
        }
        text(scene.yLabels.second, Offset(left - 18.dp.toPx(), top))
        text(scene.yLabels.first, Offset(left - 18.dp.toPx(), bottom))
        text(scene.xLabels.first, Offset(left, bottom + 13.dp.toPx()))
        text(scene.xLabels.third, Offset(right, bottom + 13.dp.toPx()))
        text(scene.xLabels.second, Offset((left + right) / 2, bottom + 13.dp.toPx()))
        scene.yTitle?.let { t -> rotate(-90f, Offset(12.dp.toPx(), (top + bottom) / 2)) { text(t, Offset(12.dp.toPx(), (top + bottom) / 2)) } }
    }
}

@Composable
private fun FitView(scene: EvFitScene) {
    val accent = MaterialTheme.colorScheme.primary
    val (xs, ys) = fitData
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.3f).stage()) {
        val pad = 16.dp.toPx()
        val xLo = xs.min() - 0.3
        val xHi = xs.max() + 0.3
        val yLo = min(ys.min(), 0.0) - 1
        val yHi = ys.max() + 2
        fun px(x: Double) = pad + ((x - xLo) / (xHi - xLo)).toFloat() * (size.width - 2 * pad)
        fun py(y: Double) = size.height - pad - ((y - yLo) / (yHi - yLo)).toFloat() * (size.height - 2 * pad)
        val mean = ys.average()
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        if (scene.toMean) {
            drawLine(SimColors.Grey, Offset(px(xLo), py(mean)), Offset(px(xHi), py(mean)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            xs.indices.forEach { i -> drawLine(SimColors.Grey.copy(alpha = 0.6f), Offset(px(xs[i]), py(ys[i])), Offset(px(xs[i]), py(mean)), strokeWidth = 1.dp.toPx()) }
        } else {
            xs.indices.forEach { i ->
                val out = i in outlierIdx
                drawLine(
                    SimColors.Red.copy(alpha = if (out) 0.9f else 0.5f),
                    Offset(px(xs[i]), py(ys[i])),
                    Offset(px(xs[i]), py(scene.slope * xs[i] + scene.intercept)),
                    strokeWidth = (if (out) 1.5f else 1f).dp.toPx(),
                )
            }
        }
        if (scene.showMae) {
            val (m, c) = maeLine
            drawLine(SimColors.Grey, Offset(px(xLo), py(m * xLo + c)), Offset(px(xHi), py(m * xHi + c)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
        }
        drawLine(accent, Offset(px(xLo), py(scene.slope * xLo + scene.intercept)), Offset(px(xHi), py(scene.slope * xHi + scene.intercept)), strokeWidth = 3.dp.toPx())
        xs.indices.forEach { i ->
            val c = Offset(px(xs[i]), py(ys[i]))
            if (i in outlierIdx) {
                drawCircle(SimColors.Red, 5.dp.toPx(), c)
                drawCircle(SimColors.Red, 9.dp.toPx(), c, style = Stroke(width = 2.dp.toPx()))
            } else {
                drawCircle(SimColors.Idle, 4.5.dp.toPx(), c)
            }
        }
    }
}

@Composable
private fun ShareBar(share: Double, modifier: Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Squared error from the ${outlierIdx.size} outliers", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${(share * 100).roundToInt()}%", fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(SimColors.Tint)) {
            Box(Modifier.fillMaxWidth(share.toFloat().coerceIn(0f, 1f)).height(12.dp).background(SimColors.Red, RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun SsBars(ss: Pair<Double, Double>, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val top = max(ss.first, ss.second)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("SS total", ss.first, SimColors.Grey), Triple("SS residual", ss.second, accent)).forEach { (label, v, color) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(SimColors.Tint)) {
                    Box(Modifier.fillMaxWidth((v / top).toFloat().coerceIn(0f, 1f)).height(12.dp).background(color, RoundedCornerShape(6.dp)))
                }
                Text("${v.roundToInt()}", fontFamily = IBMPlexMono, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
            }
        }
    }
}
