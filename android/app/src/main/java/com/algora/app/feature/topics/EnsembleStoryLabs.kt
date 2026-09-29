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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── Ensemble storyboards ─────────────────────────────────────────────────────
// Random Forests, Gradient Boosting, Extra Trees, Bagging, Voting, Stacking, XGBoost, AdaBoost, LightGBM
// and Isolation Forest as step-by-step storyboards: one figure in the card (a plane, a fitted step curve,
// a tree or a fold grid), the step's arithmetic under it, chips and a headline, then the lab's controls:
// a step track with a labelled next action, a parameter stepper beside it, or a vote picker. Every
// number is computed from the small fixed data below.

internal val ensembleStoryTopicIds = setOf(
    "random_forest", "gradient_boosting", "extra_trees", "bagging", "voting",
    "stacking", "xgboost", "adaboost", "lightgbm", "isolation_forest",
)

private val C0 = SimColors.Blue
private val C1 = CategoryAccents.Pink
private val MutedGrey = Color(0xFF6B7280)

// ── Scenes ──

private sealed interface EnsScene

private enum class EMark { Normal, Faded, Hollow, Grey }

private class EPPoint(
    val x: Double,
    val y: Double,
    val cls: Int,
    val mark: EMark = EMark.Normal,
    val scale: Float = 1f,
    val ring: Color? = null,
    val tag: String? = null,
)

/** A segment in data units; a null colour is the accent. */
private class EPLine(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val color: Color? = null, val width: Float = 2.5f, val dash: Boolean = false)

private class EPRect(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val cls: Int)

private class EBounds(val x0: Double, val x1: Double, val y0: Double, val y1: Double)

private class EVoteTile(val label: String, val value: Int?, val current: Boolean)

private class EModelRow(val name: String, val p1: Double)

private class EPlaneScene(
    val bounds: EBounds,
    val points: List<EPPoint>,
    val regions: List<EPRect> = emptyList(),
    val field: ((Double, Double) -> Int)? = null,
    val lines: List<EPLine> = emptyList(),
    val curve: List<Pair<Double, Double>> = emptyList(),
    val query: Pair<Double, Double>? = null,
    val tiles: List<EVoteTile>? = null,
    val models: List<EModelRow>? = null,
) : EnsScene

private class ECurveScene(
    val xs: List<Double>,
    val ys: List<Double>,
    val f: (Double) -> Double,
    val prev: ((Double) -> Double)?,
    val split: Double?,
) : EnsScene

private enum class ENodeTone { Current, Kept, Pruned, Open }

private class ETNode(val text: String, val x: Float, val y: Float, val tone: ENodeTone, val dashed: Boolean = false, val caption: String? = null, val captionTone: ENodeTone? = null)

private class ETreeScene(val nodes: List<ETNode>, val edges: List<Triple<Int, Int, Boolean>>) : EnsScene

private enum class ECell { Idle, Held, Train, Predicted, Diagonal }

private class EFoldRow(val label: String, val cells: List<ECell>, val result: String, val resultTone: StoryTone?, val current: Boolean)

private class EFoldScene(val rows: List<EFoldRow>) : EnsScene

private class EnsFrame(
    val headline: String,
    val body: String,
    val scene: EnsScene,
    val action: String,
    val formula: List<String> = emptyList(),
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
)

private class EnsParam(val name: String, val symbol: String, val values: List<Double>, val initial: Int, val format: (Double) -> String)

/** Track: a step track with the labelled action. Stepper: a parameter stepper over back + action. Query: a picker over "New Query". */
private enum class EControl { Track, Stepper, Query }

private class EnsLab(
    val frames: (param: Int, tab: Int) -> List<EnsFrame>,
    val control: EControl = EControl.Track,
    val param: EnsParam? = null,
    val tabs: List<String> = emptyList(),
    val startTab: Int = 0,
    val navReset: Boolean = false,
)

// ── Formatting and small math ──

/** Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−". */
private fun ex(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private val subDigits = "₀₁₂₃₄₅₆₇₈₉"

private fun sub(n: Int) = n.toString().map { subDigits[it - '0'] }.joinToString("")

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

private typealias EPt = Triple<Double, Double, Int>

private fun giniOf(ys: List<Int>): Double {
    if (ys.isEmpty()) return 0.0
    val p = ys.count { it == 1 }.toDouble() / ys.size
    return 1 - p * p - (1 - p) * (1 - p)
}

private fun splitGini(pts: List<EPt>, feature: Int, t: Double): Double {
    val left = pts.filter { (if (feature == 0) it.first else it.second) < t }.map { it.third }
    val right = pts.filter { (if (feature == 0) it.first else it.second) >= t }.map { it.third }
    return (left.size * giniOf(left) + right.size * giniOf(right)) / pts.size
}

private fun majority(ys: List<Int>) = if (ys.count { it == 1 } * 2 > ys.size) 1 else 0

private class ESplit(val feature: Int, val t: Double, val gini: Double)

private fun bestSplit(pts: List<EPt>, features: List<Int>): ESplit? {
    var best: ESplit? = null
    for (f in features) {
        val vs = pts.map { if (f == 0) it.first else it.second }.distinct().sorted()
        for (i in 0 until vs.size - 1) {
            val t = (vs[i] + vs[i + 1]) / 2
            val g = splitGini(pts, f, t)
            if (best == null || g < best.gini - 1e-12) best = ESplit(f, t, g)
        }
    }
    return best
}

/** A small CART: a leaf's class, or a cut on one feature. */
private sealed interface ETreeNodeFit

private class ELeaf(val cls: Int) : ETreeNodeFit

private class ECut(val feature: Int, val t: Double, val left: ETreeNodeFit, val right: ETreeNodeFit) : ETreeNodeFit

private fun grow(pts: List<EPt>, depth: Int, pick: () -> List<Int>): ETreeNodeFit {
    val ys = pts.map { it.third }
    if (depth == 0 || ys.distinct().size < 2) return ELeaf(majority(ys))
    val s = bestSplit(pts, pick()) ?: return ELeaf(majority(ys))
    val left = pts.filter { (if (s.feature == 0) it.first else it.second) < s.t }
    val right = pts.filter { (if (s.feature == 0) it.first else it.second) >= s.t }
    if (left.isEmpty() || right.isEmpty()) return ELeaf(majority(ys))
    return ECut(s.feature, s.t, grow(left, depth - 1, pick), grow(right, depth - 1, pick))
}

private fun predictTree(n: ETreeNodeFit, x: Double, y: Double): Int = when (n) {
    is ELeaf -> n.cls
    is ECut -> if ((if (n.feature == 0) x else y) < n.t) predictTree(n.left, x, y) else predictTree(n.right, x, y)
}

/** The tree's leaves as rectangles, and its cuts as segments, inside [b]. */
private fun treeRegions(n: ETreeNodeFit, b: EBounds, rects: MutableList<EPRect>, lines: MutableList<EPLine>) {
    when (n) {
        is ELeaf -> rects += EPRect(b.x0, b.y0, b.x1, b.y1, n.cls)
        is ECut -> if (n.feature == 0) {
            lines += EPLine(n.t, b.y0, n.t, b.y1)
            treeRegions(n.left, EBounds(b.x0, n.t, b.y0, b.y1), rects, lines)
            treeRegions(n.right, EBounds(n.t, b.x1, b.y0, b.y1), rects, lines)
        } else {
            lines += EPLine(b.x0, n.t, b.x1, n.t)
            treeRegions(n.left, EBounds(b.x0, b.x1, b.y0, n.t), rects, lines)
            treeRegions(n.right, EBounds(b.x0, b.x1, n.t, b.y1), rects, lines)
        }
    }
}

// ── Shared data ──

private val planeBounds = EBounds(0.0, 10.0, 0.0, 6.0)

/** Five pink points up and to the left, eleven blue ones along the bottom and up to the right. */
private val forestData: List<EPt> = listOf(
    EPt(1.5, 4.3, 1), EPt(2.0, 3.7, 1), EPt(2.4, 4.9, 1), EPt(3.1, 4.4, 1), EPt(5.4, 1.9, 1),
    EPt(1.2, 1.1, 0), EPt(2.7, 1.4, 0), EPt(3.8, 0.9, 0), EPt(4.9, 1.5, 0), EPt(6.1, 1.0, 0), EPt(7.2, 1.3, 0),
    EPt(8.4, 0.9, 0), EPt(6.7, 4.1, 0), EPt(7.3, 4.9, 0), EPt(8.0, 4.6, 0), EPt(8.8, 3.8, 0),
)

/** A blue arc to the upper left and a pink spread to the lower right, one pink straying into the arc. */
private val arcData: List<EPt> = listOf(
    EPt(1.3, 3.5, 0), EPt(1.8, 3.9, 0), EPt(2.0, 4.6, 0), EPt(2.5, 4.7, 0), EPt(3.0, 4.6, 0), EPt(3.5, 4.3, 0),
    EPt(4.0, 3.9, 0), EPt(6.3, 3.6, 0),
    EPt(4.7, 3.7, 1), EPt(5.7, 2.8, 1), EPt(6.0, 3.2, 1), EPt(5.9, 2.4, 1), EPt(7.1, 2.6, 1), EPt(7.3, 1.6, 1),
    EPt(8.4, 1.9, 1), EPt(9.2, 2.3, 1),
)

private fun classPoints(pts: List<EPt>) = pts.map { EPPoint(it.first, it.second, it.third) }

private val classLegend = listOf(Triple(C0, SwatchStyle.Dot, "Class 0"), Triple(C1, SwatchStyle.Dot, "Class 1"))

// ── Random forest ──

private val rfQuery = 4.6 to 3.1
private const val RF_TREES = 8

private class EForestTree(val drawn: List<Int>, val fit: ETreeNodeFit)

private val forestTrees: List<EForestTree> by lazy {
    (1..RF_TREES).map { t ->
        val random = Random(100 + t)
        val drawn = List(forestData.size) { random.nextInt(forestData.size) }
        // One random feature per split: the other half of what makes the trees disagree.
        EForestTree(drawn, grow(drawn.map { forestData[it] }, 2) { listOf(random.nextInt(2)) })
    }
}

private fun randomForestLab(): EnsLab = EnsLab(frames = { _, _ ->
    val votes = forestTrees.map { predictTree(it.fit, rfQuery.first, rfQuery.second) }
    (1..RF_TREES).map { k ->
        val tree = forestTrees[k - 1]
        val rects = mutableListOf<EPRect>()
        val lines = mutableListOf<EPLine>()
        treeRegions(tree.fit, planeBounds, rects, lines)
        val inBag = tree.drawn.toSet()
        val zero = votes.take(k).count { it == 0 }
        val one = k - zero
        val v = votes[k - 1]
        val lead = if (zero == one) "The forest is tied $zero–$one." else "The forest stands ${max(zero, one)}–${min(zero, one)}."
        EnsFrame(
            headline = if (k < RF_TREES) "Tree $k votes class $v. $lead"
            else "Tree $k votes class $v. The forest says {class ${if (one > zero) 1 else 0}}, ${max(zero, one)}–${min(zero, one)}.",
            body = if (k < RF_TREES) {
                "Each tree trains on a bootstrap sample (faded points were left out) and checks one random feature per split, so the trees disagree near the query."
            } else {
                "No single tree has to be right. Their errors differ, so the majority is steadier than any one of them."
            },
            scene = EPlaneScene(
                planeBounds,
                forestData.mapIndexed { i, p -> EPPoint(p.first, p.second, p.third, if (i in inBag) EMark.Normal else EMark.Faded) },
                regions = rects,
                lines = lines,
                query = rfQuery,
                tiles = (0 until RF_TREES).map { EVoteTile("T${it + 1}", if (it < k) votes[it] else null, it == k - 1) },
            ),
            action = if (k < RF_TREES) "Grow Tree ${k + 1}" else "Start Over",
            legend = listOf(
                Triple(SimColors.Active, SwatchStyle.Dot, "Query"),
                Triple(MutedGrey, SwatchStyle.Dot, "Not in this sample"),
                Triple(Color.Unspecified, SwatchStyle.Line, "Tree $k boundary"),
            ),
            chips = listOf(LabChip("class 0", "$zero", tint = StoryTone.Path), LabChip("class 1", "$one", tintColor = C1)),
        )
    }
}, navReset = true)

// ── Gradient boosting ──

private val gbData: Pair<List<Double>, List<Double>> by lazy {
    val random = Random(5)
    val xs = (0 until 20).map { it / 19.0 }
    val ys = xs.map { x ->
        0.2 + (if (x > 0.3) 0.25 else 0.0) + (if (x > 0.55) 0.35 else 0.0) + (if (x > 0.75) 0.18 else 0.0) + (random.nextDouble() - 0.5) * 0.08
    }
    xs to ys
}

private class EStump(val t: Double, val left: Double, val right: Double) {
    fun at(x: Double) = if (x < t) left else right
}

private fun fitStump(xs: List<Double>, r: List<Double>): EStump {
    var best: EStump? = null
    var bestSse = Double.MAX_VALUE
    for (i in 0 until xs.size - 1) {
        val t = (xs[i] + xs[i + 1]) / 2
        val l = xs.indices.filter { xs[it] < t }.map { r[it] }
        val rr = xs.indices.filter { xs[it] >= t }.map { r[it] }
        val lm = l.average()
        val rm = rr.average()
        val sse = l.sumOf { (it - lm) * (it - lm) } + rr.sumOf { (it - rm) * (it - rm) }
        if (sse < bestSse) { bestSse = sse; best = EStump(t, lm, rm) }
    }
    return best!!
}

private const val GB_ROUNDS = 6

private fun etaWords(eta: Double) = when {
    abs(eta - 1.0) < 1e-9 -> "all of it"
    abs(eta - 0.5) < 1e-9 -> "half of it"
    abs(eta - 0.1) < 1e-9 -> "a tenth of it"
    else -> "${ex(eta)} of it"
}

private fun gradientBoostingLab(): EnsLab {
    val etas = (1..10).map { it / 10.0 }
    return EnsLab(
        control = EControl.Stepper,
        param = EnsParam("Learning rate", "η", etas, 4) { ex(it) },
        navReset = true,
        frames = { p, _ ->
            val eta = etas[p]
            val (xs, ys) = gbData
            val mean = ys.average()
            val stumps = mutableListOf<EStump>()
            val models = mutableListOf<(Double) -> Double>({ _ -> mean })
            repeat(GB_ROUNDS) {
                val prev = models.last()
                val s = fitStump(xs, xs.indices.map { i -> ys[i] - prev(xs[i]) })
                stumps += s
                models += { x -> prev(x) + eta * s.at(x) }
            }
            fun mse(f: (Double) -> Double) = xs.indices.sumOf { (ys[it] - f(xs[it])).let { e -> e * e } } / xs.size
            (0..GB_ROUNDS).map { r ->
                val f = models[r]
                val legend = if (r == 0) {
                    listOf(Triple(Color.Unspecified, SwatchStyle.Line, "Ensemble F₀"), Triple(SimColors.Red, SwatchStyle.Line, "Residual"))
                } else {
                    listOf(
                        Triple(Color.Unspecified, SwatchStyle.Line, "Ensemble F${sub(r)}"),
                        Triple(SimColors.Grey, SwatchStyle.DashedLine, "F${sub(r - 1)}"),
                        Triple(SimColors.Red, SwatchStyle.Line, "Residual"),
                        Triple(SimColors.Active, SwatchStyle.DashedLine, "Split h${sub(r)}"),
                    )
                }
                if (r == 0) {
                    EnsFrame(
                        "F₀ is just the mean: {one flat line}.",
                        "Every round from here fits a small tree to what the ensemble still gets wrong: the red residuals.",
                        ECurveScene(xs, ys, f, null, null),
                        "Fit Round 1",
                        formula = listOf("F₀ = mean(y) = ${ex(mean, 3)}"),
                        legend = legend,
                        chips = listOf(LabChip("round", "0"), LabChip("MSE", ex(mse(f), 4), tint = StoryTone.Path)),
                    )
                } else {
                    val s = stumps[r - 1]
                    EnsFrame(
                        "Tree $r fits only what F${sub(r - 1)} {still gets wrong}, and adds ${etaWords(eta)}.",
                        "The red stubs are the next round's targets. A smaller η takes more rounds but overfits less.",
                        ECurveScene(xs, ys, f, models[r - 1], s.t),
                        if (r < GB_ROUNDS) "Fit Round ${r + 1}" else "Start Over",
                        formula = listOf(
                            "F${sub(r)} = F${sub(r - 1)} + ${ex(eta, 1)} × h${sub(r)}   h${sub(r)} = ${ex(s.left, 3)} | ${ex(s.right, 3)}",
                            "split at x = ${ex(s.t)}",
                        ),
                        legend = legend,
                        chips = listOf(LabChip("round", "$r"), LabChip("MSE", "${ex(mse(models[r - 1]), 4)} → ${ex(mse(f), 4)}", tint = StoryTone.Path)),
                    )
                }
            }
        },
    )
}

// ── Extra trees ──

private fun randomCut(random: Random, feature: Int): Double {
    val vs = forestData.map { if (feature == 0) it.first else it.second }
    return vs.min() + random.nextDouble() * (vs.max() - vs.min())
}

private fun cutLine(feature: Int, t: Double, color: Color?, dash: Boolean = false, width: Float = 2.5f) =
    if (feature == 0) EPLine(t, planeBounds.y0, t, planeBounds.y1, color, width, dash) else EPLine(planeBounds.x0, t, planeBounds.x1, t, color, width, dash)

private fun extraTreesLab(): EnsLab = EnsLab(frames = { _, _ ->
    val best = bestSplit(forestData, listOf(0, 1))!!
    val axis = listOf("x", "y")
    fun draw(seed: Int): Triple<Double, Double, Int> {
        val random = Random(seed)
        val tx = randomCut(random, 0)
        val ty = randomCut(random, 1)
        val kept = if (splitGini(forestData, 0, tx) <= splitGini(forestData, 1, ty)) 0 else 1
        return Triple(tx, ty, kept)
    }
    val (tx, ty, kept) = draw(33)
    val gx = splitGini(forestData, 0, tx)
    val gy = splitGini(forestData, 1, ty)
    val gk = if (kept == 0) gx else gy
    val (tx2, ty2, kept2) = draw(34)
    val gk2 = if (kept2 == 0) splitGini(forestData, 0, tx2) else splitGini(forestData, 1, ty2)
    val bestLine = cutLine(best.feature, best.t, SimColors.Grey, dash = true, width = 1.5f)
    val pts = classPoints(forestData)
    val cutLegend = listOf(
        Triple(SimColors.Active, SwatchStyle.Line, "Random cut"),
        Triple(Color.Unspecified, SwatchStyle.Line, "Kept"),
        Triple(SimColors.Grey, SwatchStyle.DashedLine, "Best possible"),
    )
    fun formula(a: Double, b: Double, k: Int) = listOf(
        "x cut ${ex(a)} → ${if (k == 0) "{v:${ex(splitGini(forestData, 0, a), 3)}}" else ex(splitGini(forestData, 0, a), 3)}   " +
            "y cut ${ex(b)} → ${if (k == 1) "{v:${ex(splitGini(forestData, 1, b), 3)}}" else ex(splitGini(forestData, 1, b), 3)}",
    )
    val keptT = if (kept == 0) tx else ty
    val keptT2 = if (kept2 == 0) tx2 else ty2
    val forestCuts = (35..46).map { draw(it) }.map { (a, b, k) -> cutLine(k, if (k == 0) a else b, C0.copy(alpha = 0.5f), width = 1.5f) }
    listOf(
        EnsFrame(
            "A normal tree tries {every threshold} on both features and keeps the best: gini ${ex(best.gini, 3)}.",
            "That search is what makes a tree expensive, and what makes a forest's trees alike.",
            EPlaneScene(planeBounds, pts, lines = listOf(bestLine)),
            "Draw Random Cuts",
            formula = listOf("best: ${axis[best.feature]} < ${ex(best.t)} → gini ${ex(best.gini, 3)}"),
            legend = listOf(Triple(SimColors.Grey, SwatchStyle.DashedLine, "Best possible")),
            chips = listOf(LabChip("best", ex(best.gini, 3))),
        ),
        EnsFrame(
            "Extra Trees draws {one random threshold} per feature: x at ${ex(tx)}, y at ${ex(ty)}.",
            "No search: each cut is uniform between the feature's smallest and largest value.",
            EPlaneScene(planeBounds, pts, lines = listOf(bestLine, cutLine(0, tx, SimColors.Active), cutLine(1, ty, SimColors.Active))),
            "Keep the Better",
            formula = formula(tx, ty, -1),
            legend = cutLegend,
            chips = listOf(LabChip("best", ex(best.gini, 3))),
        ),
        EnsFrame(
            "Extra Trees draws {one random threshold} per feature and keeps the better one.",
            "This time the ${axis[kept]} cut scores ${ex(gk, 3)} against the best split's ${ex(best.gini, 3)}; the ${axis[1 - kept]} cut scores ${ex(if (kept == 0) gy else gx, 3)}. " +
                "With no search, trees are cheaper and differ more, so their average generalises better.",
            EPlaneScene(planeBounds, pts, lines = listOf(bestLine, cutLine(1 - kept, if (kept == 0) ty else tx, SimColors.Active), cutLine(kept, keptT, null))),
            "Draw New Cuts",
            formula = formula(tx, ty, kept),
            legend = cutLegend,
            chips = listOf(LabChip("kept", ex(gk, 3), tint = StoryTone.Answer), LabChip("best", ex(best.gini, 3))),
        ),
        EnsFrame(
            "A new draw keeps the {${axis[kept2]} cut at ${ex(keptT2)}}, scoring ${ex(gk2, 3)}.",
            "Each tree in the forest gets its own draws, so no two trees split alike.",
            EPlaneScene(planeBounds, pts, lines = listOf(bestLine, cutLine(1 - kept2, if (kept2 == 0) ty2 else tx2, SimColors.Active), cutLine(kept2, keptT2, null))),
            "Grow the Forest",
            formula = formula(tx2, ty2, kept2),
            legend = cutLegend,
            chips = listOf(LabChip("kept", ex(gk2, 3), tint = StoryTone.Answer), LabChip("best", ex(best.gini, 3))),
        ),
        EnsFrame(
            "Twelve trees, twelve different root cuts: their {average} is smoother than any one.",
            "Extra Trees trades a little bias in each tree for much less variance across them.",
            EPlaneScene(planeBounds, pts, lines = forestCuts + bestLine),
            "Start Over",
            legend = listOf(Triple(C0, SwatchStyle.Line, "Root cut of each tree"), Triple(SimColors.Grey, SwatchStyle.DashedLine, "Best possible")),
            chips = listOf(LabChip("trees", "12"), LabChip("best", ex(best.gini, 3))),
        ),
    )
}, navReset = true)

// ── Bagging ──

private const val BAGS = 4

private val bags: List<EForestTree> by lazy {
    (1..BAGS).map { k ->
        val random = Random(200 + k)
        val drawn = List(forestData.size) { random.nextInt(forestData.size) }
        EForestTree(drawn, grow(drawn.map { forestData[it] }, 2) { listOf(0, 1) })
    }
}

private fun oobAccuracy(upTo: Int): Pair<Int, Int> {
    var right = 0
    var seen = 0
    forestData.forEachIndexed { i, p ->
        val votes = bags.take(upTo).filter { i !in it.drawn }.map { predictTree(it.fit, p.first, p.second) }
        if (votes.isEmpty()) return@forEachIndexed
        seen++
        if (majority(votes) == p.third) right++
    }
    return right to seen
}

private fun baggingLab(): EnsLab = EnsLab(frames = { _, _ ->
    val n = forestData.size
    val pOut = (1 - 1.0 / n).pow(n)
    val frames = (1..BAGS).map { k ->
        val bag = bags[k - 1]
        val counts = bag.drawn.groupingBy { it }.eachCount()
        val rects = mutableListOf<EPRect>()
        val lines = mutableListOf<EPLine>()
        treeRegions(bag.fit, planeBounds, rects, lines)
        val (right, seen) = oobAccuracy(k)
        val distinct = counts.size
        EnsFrame(
            "Sample $k drew $distinct distinct rows and left ${n - distinct} out.",
            "The left-out rows test trees that never saw them, so bagging gets a validation score for free. Averaging trees cuts variance, not bias.",
            EPlaneScene(
                planeBounds,
                forestData.mapIndexed { i, p ->
                    val c = counts[i] ?: 0
                    EPPoint(p.first, p.second, p.third, if (c == 0) EMark.Hollow else EMark.Normal, tag = if (c > 1) "×$c" else null)
                },
                regions = rects,
                lines = lines,
            ),
            if (k < BAGS) "Draw Sample ${k + 1}" else "Vote",
            formula = listOf("P(row left out) = (1 − 1/$n)${"$n".map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")} = {v:${ex(pOut)}}"),
            legend = listOf(
                Triple(SimColors.Idle, SwatchStyle.Dot, "Drawn (×n = repeats)"),
                Triple(SimColors.Grey, SwatchStyle.Ring, "Out of bag"),
                Triple(Color.Unspecified, SwatchStyle.Line, "Tree $k"),
            ),
            chips = listOf(LabChip("in bag", "$distinct/$n"), LabChip("out of bag", "${n - distinct}"), LabChip("OOB acc", "$right/$seen", good = true)),
        )
    }
    val (right, seen) = oobAccuracy(BAGS)
    frames + EnsFrame(
        "$BAGS trees vote, and the out-of-bag accuracy is {m:$right/$seen}.",
        "Each row was scored only by trees that never trained on it, so no hold-out set was needed.",
        EPlaneScene(planeBounds, classPoints(forestData), field = { x, y -> majority(bags.map { predictTree(it.fit, x, y) }) }),
        "Start Over",
        legend = classLegend,
        chips = listOf(LabChip("trees", "$BAGS"), LabChip("OOB acc", "$right/$seen", good = true)),
    )
}, navReset = true)

// ── Voting ──

private fun sigmoid(z: Double) = 1 / (1 + exp(-z))

private val voteModels: List<Pair<String, (Double, Double) -> Double>> = listOf(
    "Tree" to { x, _ -> if (x >= 5.0) 1.0 else 0.0 },
    "SVM" to { x, y -> sigmoid(1.5 * ((x - 5.3) - 0.8 * (y - 3.2))) },
    "LogReg" to { x, y -> sigmoid(1.2 * ((x - 5.35) - 0.3 * (y - 3.2))) },
)

private val voteQueries = listOf(5.1 to 3.2, 2.5 to 4.5, 7.5 to 2.0, 5.6 to 4.6)

private val voteData: List<EPt> = listOf(
    EPt(1.4, 4.0, 0), EPt(1.8, 4.8, 0), EPt(2.2, 5.0, 0), EPt(2.6, 4.9, 0), EPt(3.1, 4.6, 0), EPt(3.6, 4.2, 0), EPt(2.0, 3.6, 0),
    EPt(5.6, 2.9, 1), EPt(5.9, 2.4, 1), EPt(6.5, 2.8, 1), EPt(6.5, 1.4, 1), EPt(7.4, 1.8, 1), EPt(8.1, 1.8, 1), EPt(8.1, 2.3, 1),
)

private fun softBoundary(): List<Pair<Double, Double>> = (0..60).mapNotNull { j ->
    val y = planeBounds.y0 + (planeBounds.y1 - planeBounds.y0) * j / 60
    fun avg(x: Double) = voteModels.sumOf { it.second(x, y) } / voteModels.size
    var lo = planeBounds.x0
    var hi = planeBounds.x1
    if (avg(lo) >= 0.5 || avg(hi) < 0.5) return@mapNotNull null
    repeat(40) { val mid = (lo + hi) / 2; if (avg(mid) >= 0.5) hi = mid else lo = mid }
    hi to y
}

/** A linear model's p = 0.5 line clipped to the plane, found along its two ends. */
private fun halfLine(p: (Double, Double) -> Double): EPLine? {
    val pts = listOf(planeBounds.y0, planeBounds.y1).mapNotNull { y ->
        var lo = -20.0
        var hi = 30.0
        if ((p(lo, y) - 0.5) * (p(hi, y) - 0.5) > 0) return@mapNotNull null
        repeat(50) { val mid = (lo + hi) / 2; if (p(mid, y) >= 0.5) hi = mid else lo = mid }
        hi to y
    }
    return if (pts.size == 2) EPLine(pts[0].first, pts[0].second, pts[1].first, pts[1].second, SimColors.Grey, 1.5f, dash = true) else null
}

private fun votingLab(): EnsLab = EnsLab(
    control = EControl.Query,
    tabs = listOf("Hard vote", "Soft vote"),
    startTab = 1,
    navReset = true,
    frames = { _, tab ->
        val soft = tab == 1
        val boundary = softBoundary()
        val modelLines = listOfNotNull(
            EPLine(5.0, planeBounds.y0, 5.0, planeBounds.y1, SimColors.Grey, 1.5f, dash = true),
            halfLine(voteModels[1].second),
            halfLine(voteModels[2].second),
        )
        val field: (Double, Double) -> Int = if (soft) {
            { x, y -> if (voteModels.sumOf { it.second(x, y) } / 3 >= 0.5) 1 else 0 }
        } else {
            { x, y -> majority(voteModels.map { if (it.second(x, y) >= 0.5) 1 else 0 }) }
        }
        voteQueries.mapIndexed { qi, (qx, qy) ->
            val ps = voteModels.map { it.second(qx, qy) }
            val hardVotes = ps.map { if (it >= 0.5) 1 else 0 }
            val hard = majority(hardVotes)
            val ones = hardVotes.count { it == 1 }
            val avg = ps.average()
            val softCls = if (avg >= 0.5) 1 else 0
            val agree = hard == softCls
            val (headline, body) = when {
                !agree && soft -> "Hard voting says class $hard, ${max(ones, 3 - ones)} to ${min(ones, 3 - ones)}. Soft voting says {v:class $softCls}." to
                    "The ${voteModels[hardVotes.indexOfFirst { it == softCls }].first.let { if (it == "Tree") "tree" else it }} is certain; the others only lean the other way. Averaging probabilities lets confidence count."
                !agree -> "Hard voting says {v:class $hard}, ${max(ones, 3 - ones)} to ${min(ones, 3 - ones)}. Soft voting would say class $softCls." to
                    "Each model gets one vote however sure it is, so two weak leanings outvote one certainty."
                else -> "Both votes agree: {class $hard}." to
                    "Away from the boundary the models agree, and so do the two ways of counting them."
            }
            EnsFrame(
                headline,
                body,
                EPlaneScene(
                    planeBounds,
                    classPoints(voteData),
                    field = field,
                    lines = modelLines,
                    curve = if (soft) boundary else emptyList(),
                    query = qx to qy,
                    models = voteModels.mapIndexed { i, m -> EModelRow(m.first, ps[i]) },
                ),
                if (qi < voteQueries.lastIndex) "New Query" else "New Query",
                formula = if (soft) {
                    listOf("soft = (${ps.joinToString(" + ") { ex(it) }}) / 3 = {v:${ex(avg)}} → $softCls")
                } else {
                    listOf("hard = votes ${hardVotes.joinToString(", ")} → {v:$hard} ($ones to ${3 - ones})")
                },
                legend = listOf(
                    Triple(Color.Unspecified, SwatchStyle.Line, if (soft) "Soft-vote boundary" else "Hard-vote regions"),
                    Triple(SimColors.Grey, SwatchStyle.DashedLine, "Each model"),
                    Triple(SimColors.Active, SwatchStyle.Dot, "Query"),
                ),
            )
        }
    },
)

// ── Stacking ──

private val stackFolds = listOf(listOf(0, 7, 12, 3), listOf(5, 10, 14), listOf(1, 8, 13), listOf(6, 11, 2), listOf(9, 4, 15))

private fun nearestLabel(i: Int, pool: List<Int>): Int {
    val p = forestData[i]
    return forestData[pool.minBy { j -> (forestData[j].first - p.first).pow(2) + (forestData[j].second - p.second).pow(2) }].third
}

private fun stackingLab(): EnsLab = EnsLab(frames = { _, _ ->
    val all = forestData.indices.toList()
    val foldCorrect = stackFolds.map { fold -> fold.count { i -> nearestLabel(i, all - fold.toSet()) == forestData[i].third } }
    fun rows(done: Int, current: Int?) = stackFolds.indices.map { f ->
        val cells = stackFolds.indices.map { c ->
            when {
                f < done -> if (c == f) ECell.Predicted else ECell.Idle
                f == current -> if (c == f) ECell.Held else ECell.Train
                else -> if (c == f) ECell.Diagonal else ECell.Idle
            }
        }
        EFoldRow(
            "Fold ${f + 1}",
            cells,
            if (f < done || f == current) "${foldCorrect[f]}/${stackFolds[f].size}" else "—",
            if (f < done) StoryTone.Done else if (f == current) StoryTone.Active else null,
            f == current,
        )
    }
    fun soFar(k: Int) = foldCorrect.take(k).sum() to stackFolds.take(k).sumOf { it.size }
    val legend = listOf(
        Triple(SimColors.Active, SwatchStyle.Fill, "Held out"),
        Triple(SimColors.Blue, SwatchStyle.Fill, "Trains the base model"),
        Triple(SimColors.Green, SwatchStyle.Fill, "Predicted"),
    )
    val n = forestData.size
    val frames = mutableListOf(
        EnsFrame(
            "Stacking trains a {meta-learner} on the base models' predictions.",
            "Those predictions have to come from rows the base model never trained on, or the meta-learner learns from a lie.",
            EFoldScene(rows(0, null)),
            "Try In-Sample",
            formula = listOf("$n rows in 5 folds"),
            legend = legend,
            chips = listOf(LabChip("meta-features", "0/$n", tint = StoryTone.Path)),
        ),
        EnsFrame(
            "In-sample, 1-NN scores {w:$n/$n}: every row is its own nearest neighbour.",
            "A meta-learner trained on that would learn to trust a perfect score that isn't real.",
            EFoldScene(rows(0, null)),
            "Predict Fold 1",
            formula = listOf("in-sample {w:$n/$n}"),
            legend = legend,
            chips = listOf(LabChip("meta-features", "0/$n", tint = StoryTone.Path)),
        ),
    )
    stackFolds.indices.forEach { f ->
        val (c, m) = soFar(f + 1)
        frames += EnsFrame(
            "Fold ${f + 1} is predicted by a model that {never saw it}.",
            if (f == 2) {
                "1-NN scores $n/$n on its own training rows, since every row is its own nearest neighbour. Training the meta-learner on that would teach it to trust a score that doesn't exist."
            } else {
                "The other four folds train 1-NN; its predictions on fold ${f + 1} become the meta-features for those rows."
            },
            EFoldScene(rows(f, f)),
            if (f < stackFolds.lastIndex) "Predict Fold ${f + 2}" else "Assemble",
            formula = listOf("in-sample {w:$n/$n} · out-of-fold so far {m:$c/$m}"),
            legend = legend,
            chips = listOf(LabChip("meta-features", "$m/$n", tint = StoryTone.Path)),
        )
    }
    val (c, _) = soFar(stackFolds.size)
    frames += EnsFrame(
        "Every row now has an honest prediction: out-of-fold accuracy {m:$c/$n}.",
        "These out-of-fold predictions are the meta-learner's training features, one column per base model.",
        EFoldScene(rows(stackFolds.size, null)),
        "Blend Instead",
        formula = listOf("in-sample {w:$n/$n} · out-of-fold {m:$c/$n}"),
        legend = legend,
        chips = listOf(LabChip("meta-features", "$n/$n", tint = StoryTone.Path)),
    )
    frames += EnsFrame(
        "Blending skips the folds: it holds out {one set} once.",
        "Simpler and faster, but the meta-learner then trains on only the held-out rows.",
        EFoldScene(rows(stackFolds.size, null)),
        "Start Over",
        formula = listOf("stacking: every row, out of fold · blending: one hold-out set"),
        legend = legend,
        chips = listOf(LabChip("meta-features", "$n/$n", tint = StoryTone.Path)),
    )
    frames
})

// ── XGBoost ──

private const val XGB_LAMBDA = 1.0

private fun xgbGain(gl: Double, hl: Double, gr: Double, hr: Double, gamma: Double) =
    0.5 * (gl * gl / (hl + XGB_LAMBDA) + gr * gr / (hr + XGB_LAMBDA) - (gl + gr).pow(2) / (hl + hr + XGB_LAMBDA)) - gamma

private fun xgboostLab(): EnsLab {
    val gammas = (0..10).map { it / 10.0 }
    return EnsLab(
        control = EControl.Stepper,
        param = EnsParam("Split penalty", "γ", gammas, 5) { ex(it) },
        frames = { p, _ ->
            val gamma = gammas[p]
            val root = xgbGain(-6.0, 4.0, -1.0, 4.0, gamma)
            val raw = xgbGain(-0.6, 2.0, -0.4, 2.0, 0.0)
            val split = raw - gamma
            val rootKept = root > 0
            val splitKept = split > 0
            val legend = listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Kept"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Pruned"),
            )
            val rootTone = if (rootKept) ENodeTone.Kept else ENodeTone.Pruned
            val chipsBase = listOf(LabChip("λ", "1"), LabChip("root gain", ex(root), good = rootKept, tint = if (rootKept) null else StoryTone.Warn))
            fun rootNode(tone: ENodeTone) = ETNode("gain ${ex(root)}", 0.3f, 0.14f, tone)
            val left = ETNode("G −6.0 H 4.0", 0.0f, 0.5f, ENodeTone.Open)
            val splitTone = if (splitKept) ENodeTone.Kept else ENodeTone.Pruned
            listOf(
                EnsFrame(
                    "Each leaf keeps two sums: gradients {G} and Hessians H.",
                    "Its best output is −G / (H + λ). A split is worth making only if it lowers the loss by more than γ.",
                    ETreeScene(listOf(ETNode("G −7.0 H 8.0", 0.3f, 0.14f, ENodeTone.Current)), emptyList()),
                    "Split Root",
                    formula = listOf("w* = −G / (H + λ) = 7.0 / 9 = {v:${ex(7.0 / 9)}}"),
                    legend = legend,
                    chips = listOf(LabChip("λ", "1")),
                ),
                EnsFrame(
                    if (rootKept) "Splitting the root gains {m:${ex(root)}} after γ, so it stays."
                    else "Splitting the root gains {w:${ex(root)}} after γ, so even the root split is pruned.",
                    "Gain is how much the split lowers the loss, minus γ for the extra leaf.",
                    ETreeScene(
                        listOf(rootNode(rootTone), left, ETNode("G −1.0 H 4.0", 0.72f, 0.5f, ENodeTone.Open)),
                        listOf(Triple(0, 1, false), Triple(0, 2, false)),
                    ),
                    "Try Right Child",
                    formula = listOf("½[6.0²/5 + 1.0²/5 − 7.0²/9] − γ", "= ${ex(root + gamma)} − ${ex(gamma)} = ${if (rootKept) "{m:" else "{w:"}${ex(root)}}"),
                    legend = legend,
                    chips = chipsBase,
                ),
                EnsFrame(
                    if (splitKept) "Splitting the right child scores {m:${ex(split)}}, so XGBoost keeps it."
                    else "Splitting the right child scores {w:${ex(split)}}, so XGBoost prunes it.",
                    if (splitKept) "The split helps by ${ex(raw)} before γ, more than the ${ex(gamma)} that γ charges for a new split."
                    else "The split barely helps (${ex(raw)} before γ), and γ charges ${ex(gamma)} for each new split. Pruning runs after the tree reaches max depth.",
                    ETreeScene(
                        listOf(
                            rootNode(rootTone), left, ETNode("G −1.0 H 4.0", 0.72f, 0.5f, ENodeTone.Current),
                            ETNode("G −0.6 H 2", 0.44f, 0.86f, splitTone, dashed = true), ETNode("G −0.4 H 2", 1.0f, 0.86f, splitTone, dashed = true),
                        ),
                        listOf(Triple(0, 1, false), Triple(0, 2, false), Triple(2, 3, true), Triple(2, 4, true)),
                    ),
                    if (splitKept) "Keep Split" else "Prune Split",
                    formula = listOf("½[0.6²/3 + 0.4²/3 − 1.0²/5] − γ", "= ${ex(raw)} − ${ex(gamma)} = ${if (splitKept) "{m:" else "{w:"}${ex(split)}}"),
                    legend = legend,
                    chips = chipsBase + LabChip("split", ex(split), good = splitKept, tint = if (splitKept) null else StoryTone.Warn),
                ),
                run {
                    val leaves = when {
                        !rootKept -> 1
                        splitKept -> 3
                        else -> 2
                    }
                    val nodes = mutableListOf(
                        if (rootKept) rootNode(ENodeTone.Kept) else ETNode("G −7.0 H 8.0", 0.3f, 0.14f, ENodeTone.Kept),
                    )
                    val edges = mutableListOf<Triple<Int, Int, Boolean>>()
                    if (rootKept) {
                        nodes += ETNode("w ${ex(6.0 / 5)}", 0.0f, 0.5f, ENodeTone.Kept)
                        nodes += if (splitKept) ETNode("gain ${ex(split)}", 0.72f, 0.5f, ENodeTone.Kept) else ETNode("w ${ex(1.0 / 5)}", 0.72f, 0.5f, ENodeTone.Kept)
                        edges += Triple(0, 1, false); edges += Triple(0, 2, false)
                        if (splitKept) {
                            nodes += ETNode("w ${ex(0.6 / 3)}", 0.44f, 0.86f, ENodeTone.Kept)
                            nodes += ETNode("w ${ex(0.4 / 3)}", 1.0f, 0.86f, ENodeTone.Kept)
                            edges += Triple(2, 3, false); edges += Triple(2, 4, false)
                        }
                    }
                    EnsFrame(
                        "The finished tree has {${plural(leaves, "leaf")}}.".replace("leafs", "leaves"),
                        "Each leaf outputs w = −G / (H + λ). Raise γ and weak splits go first; lower it and the tree grows until only λ holds it back.",
                        ETreeScene(nodes, edges),
                        "Start Over",
                        formula = listOf("w = −G / (H + λ) at every leaf"),
                        legend = legend,
                        chips = chipsBase,
                    )
                },
            )
        },
    )
}

// ── AdaBoost ──

private const val ADA_ROUNDS = 9

private class EAdaStump(val feature: Int, val t: Double, val sign: Int) {
    fun h(x: Double, y: Double): Int = if (((if (feature == 0) x else y) >= t) == (sign > 0)) 1 else -1
}

private class EAdaRound(val stump: EAdaStump, val weights: List<Double>, val eps: Double, val alpha: Double, val missed: Set<Int>)

private val adaRounds: List<EAdaRound> by lazy {
    val n = arcData.size
    var w = List(n) { 1.0 / n }
    val labels = arcData.map { if (it.third == 1) 1 else -1 }
    (1..ADA_ROUNDS).map {
        var best: EAdaStump? = null
        var bestErr = Double.MAX_VALUE
        for (f in 0..1) {
            val vs = arcData.map { if (f == 0) it.first else it.second }.distinct().sorted()
            for (i in 0 until vs.size - 1) {
                val t = (vs[i] + vs[i + 1]) / 2
                for (sign in listOf(1, -1)) {
                    val s = EAdaStump(f, t, sign)
                    val err = arcData.indices.sumOf { j -> if (s.h(arcData[j].first, arcData[j].second) != labels[j]) w[j] else 0.0 }
                    if (err < bestErr - 1e-12) { bestErr = err; best = s }
                }
            }
        }
        val s = best!!
        val eps = bestErr.coerceIn(1e-6, 1 - 1e-6)
        val alpha = 0.5 * ln((1 - eps) / eps)
        val missed = arcData.indices.filter { j -> s.h(arcData[j].first, arcData[j].second) != labels[j] }.toSet()
        val round = EAdaRound(s, w, eps, alpha, missed)
        val next = arcData.indices.map { j -> w[j] * exp(-alpha * labels[j] * s.h(arcData[j].first, arcData[j].second)) }
        val z = next.sum()
        w = next.map { it / z }
        round
    }
}

private fun stumpLine(s: EAdaStump, color: Color?, dash: Boolean) = cutLine(s.feature, s.t, color, dash, if (dash) 1.5f else 2.5f)

private fun adaBoostLab(): EnsLab = EnsLab(frames = { _, _ ->
    val rounds = adaRounds
    val n = arcData.size
    fun ensemble(k: Int): (Double, Double) -> Int = { x, y -> if (rounds.take(k).sumOf { it.alpha * it.stump.h(x, y) } >= 0) 1 else 0 }
    fun weighted(ws: List<Double>, ring: Set<Int>) = arcData.mapIndexed { i, p ->
        EPPoint(p.first, p.second, p.third, scale = sqrt(ws[i] * n).toFloat().coerceIn(0.6f, 3.2f), ring = if (i in ring) SimColors.Red else null)
    }
    val first = EnsFrame(
        "Every point starts with {equal weight}: 1/$n each.",
        "Each round fits one stump, a single cut, to the weighted points. Dot size is the weight.",
        EPlaneScene(planeBounds, classPoints(arcData)),
        "Fit Round 1",
        legend = classLegend,
        chips = listOf(LabChip("round", "0 / $ADA_ROUNDS")),
    )
    val later = (1..ADA_ROUNDS).map { k ->
        val r = rounds[k - 1]
        val prev = rounds.getOrNull(k - 2)
        val final = arcData.indices.count { i -> ensemble(k)(arcData[i].first, arcData[i].second) == arcData[i].third }
        val headline: String
        val body: String
        if (prev == null) {
            headline = "Stump 1 is the best single cut: it misses {${plural(r.missed.size, "point")}}."
            body = "Its weighted error is ε₁ = ${ex(r.eps, 3)}, so its vote counts α₁ = ${ex(r.alpha)}."
        } else {
            val ratio = exp(2 * prev.alpha)
            val m = prev.missed.size
            headline = if (m == 1) "The one point stump ${k - 1} missed is now {${ratio.roundToInt()}× heavier}, so stump $k cuts for it."
            else "The $m points stump ${k - 1} missed are now {${ratio.roundToInt()}× heavier}, so stump $k weighs them most."
            body = "Dot size is the weight. Each stump votes with its α, so more accurate stumps count for more." +
                if (k == ADA_ROUNDS) " After $ADA_ROUNDS rounds the weighted vote gets $final/$n right." else ""
        }
        val shown = prev ?: r
        val kk = if (prev == null) k else k - 1
        EnsFrame(
            headline,
            body,
            EPlaneScene(
                planeBounds,
                weighted(r.weights, prev?.missed ?: emptySet()),
                field = ensemble(k),
                lines = listOfNotNull(prev?.let { stumpLine(it.stump, SimColors.Grey, dash = true) }, stumpLine(r.stump, null, dash = false)),
            ),
            if (k < ADA_ROUNDS) "Fit Round ${k + 1}" else "Start Over",
            formula = listOf(
                "ε${sub(kk)} = ${ex(shown.eps, 3)}   α${sub(kk)} = ½ ln((1 − ε${sub(kk)})/ε${sub(kk)}) = {v:${ex(shown.alpha)}}",
                "wrong × e^α${sub(kk)}, right × e^−α${sub(kk)} → ×${exp(2 * shown.alpha).roundToInt()} heavier",
            ),
            legend = if (prev == null) {
                listOf(Triple(Color.Unspecified, SwatchStyle.Line, "Stump 1")) + classLegend
            } else {
                listOf(
                    Triple(SimColors.Red, SwatchStyle.Ring, "Missed by stump ${k - 1}"),
                    Triple(SimColors.Grey, SwatchStyle.DashedLine, "Stump ${k - 1}"),
                    Triple(Color.Unspecified, SwatchStyle.Line, "Stump $k"),
                )
            },
            chips = listOf(
                LabChip("round", "$k / $ADA_ROUNDS"),
                LabChip("ε${sub(k)}", ex(r.eps, 3), tint = StoryTone.Active),
                LabChip("α${sub(k)}", ex(r.alpha), tint = StoryTone.Answer),
            ),
        )
    }
    listOf(first) + later
}, navReset = true)

// ── LightGBM ──

private class EGNode(val id: String, val parent: String?, val gain: Double, val depth: Int)

private val gbmNodes = listOf(
    EGNode("A", null, 5.0, 0), EGNode("B", "A", 4.0, 1), EGNode("C", "A", 0.3, 1),
    EGNode("D", "B", 2.1, 2), EGNode("E", "B", 1.8, 2), EGNode("J", "C", 0.15, 2), EGNode("K", "C", 0.1, 2),
    EGNode("F", "D", 1.2, 3), EGNode("G", "D", 0.4, 3), EGNode("H", "E", 0.9, 3), EGNode("I", "E", 0.2, 3),
)

private val leafOrder = listOf("A", "B", "D", "E")
private val levelOrder = listOf("A", "B", "C", "D")

private fun gainOf(id: String) = gbmNodes.first { it.id == id }.gain

/** The visible tree: the root, and the children of every split node, laid out by in-order leaf slots. */
private fun gbmScene(done: List<String>, current: String?, otherNext: String?, mode: String, other: String): ETreeScene {
    val visible = gbmNodes.filter { it.parent == null || it.parent in done }
    val children = visible.groupBy { it.parent }
    val xs = HashMap<String, Float>()
    var slot = 0
    fun place(n: EGNode) {
        val kids = children[n.id].orEmpty()
        if (kids.isEmpty()) { xs[n.id] = slot++.toFloat(); return }
        kids.forEach(::place)
        xs[n.id] = kids.map { xs.getValue(it.id) }.average().toFloat()
    }
    place(visible.first())
    val slots = max(slot - 1, 1).toFloat()
    val nodes = visible.map { n ->
        val tone = when {
            n.id in done -> ENodeTone.Kept
            n.id == current -> ENodeTone.Current
            else -> ENodeTone.Open
        }
        ETNode(
            "gain ${ex(n.gain, 1)}",
            if (slot == 1) 0.5f else xs.getValue(n.id) / slots,
            0.12f + n.depth * 0.27f,
            tone,
            caption = when (n.id) {
                current -> "$mode takes"
                otherNext -> "$other next"
                else -> null
            },
            captionTone = if (n.id == current) ENodeTone.Current else ENodeTone.Open,
        )
    }
    val index = visible.mapIndexed { i, n -> n.id to i }.toMap()
    return ETreeScene(nodes, visible.filter { it.parent != null }.map { Triple(index.getValue(it.parent!!), index.getValue(it.id), false) })
}

private fun lightGbmLab(): EnsLab = EnsLab(
    tabs = listOf("Leaf-wise", "Level-wise"),
    frames = { _, tab ->
        val leaf = tab == 0
        val order = if (leaf) leafOrder else levelOrder
        val otherOrder = if (leaf) levelOrder else leafOrder
        val mode = if (leaf) "leaf-wise" else "level-wise"
        val other = if (leaf) "level-wise" else "leaf-wise"
        fun loss(o: List<String>, k: Int) = o.take(k).sumOf { gainOf(it) }
        fun chips(k: Int) = listOf(
            LabChip("loss ↓ leaf-wise", ex(loss(leafOrder, k), 1), tint = if (leaf) StoryTone.Answer else null),
            LabChip("level-wise", ex(loss(levelOrder, k), 1), tint = if (leaf) null else StoryTone.Answer),
        )
        val legend = listOf(
            Triple(SimColors.Active, SwatchStyle.Fill, "Split now"),
            Triple(SimColors.Green, SwatchStyle.Fill, "Split"),
            Triple(SimColors.Grey, SwatchStyle.Fill, "Open leaf"),
        )
        val frames = order.indices.map { i ->
            val s = i + 1
            val cur = order[i]
            val alt = otherOrder[i]
            val g = gainOf(cur)
            val ga = gainOf(alt)
            val (headline, body) = when {
                cur == alt && i == 0 -> "Split 1: both strategies start at the {root}, gain ${ex(g, 1)}." to
                    "A leaf's gain is how much loss its best split would remove."
                cur == alt -> "Split $s: the {${ex(g, 1)}} leaf is next either way." to
                    "While the tree is shallow the two strategies agree."
                leaf -> {
                    val deeper = gbmNodes.first { it.id == cur }.depth > gbmNodes.first { it.id == alt }.depth
                    "Split $s: leaf-wise takes the {${ex(g, 1)}} leaf${if (deeper) ", one level deeper" else ""}." to
                        "Level-wise would split the ${ex(ga, 1)} leaf first and remove ${ex(g - ga, 1)} less loss."
                }
                else -> "Split $s: level-wise takes the {${ex(g, 1)}} leaf to finish depth ${gbmNodes.first { it.id == cur }.depth}." to
                    "Leaf-wise would split the ${ex(ga, 1)} leaf here and remove ${ex(ga - g, 1)} more loss."
            }
            EnsFrame(
                headline,
                body,
                gbmScene(order.take(i), cur, alt.takeIf { it != cur && it !in order.take(i) }, mode, other),
                if (s < order.size) (if (leaf) "Split Best Leaf" else "Split Next Leaf") else "Compare",
                legend = legend,
                chips = chips(s),
            )
        }
        val leafLoss = loss(leafOrder, 4)
        val levelLoss = loss(levelOrder, 4)
        frames + listOf(
            EnsFrame(
                "With 5 leaves each, leaf-wise removes {${ex(leafLoss, 1)}} loss, level-wise ${ex(levelLoss, 1)}.",
                "Same leaf budget, more loss removed: that is why LightGBM grows leaf-wise and caps trees with num_leaves.",
                gbmScene(order, null, null, mode, other),
                "Why It Overfits",
                legend = legend,
                chips = chips(4),
            ),
            EnsFrame(
                "Deeper leaves hold {fewer rows}, so leaf-wise trees fit noise faster.",
                "On small data keep num_leaves low, set min_data_in_leaf, or cap max_depth. Level-wise trees are safer there.",
                gbmScene(order, null, null, mode, other),
                "Start Over",
                legend = legend,
                chips = chips(4),
            ),
        )
    },
)

// ── Isolation forest ──

private val isoData: List<EPt> by lazy {
    val random = Random(8)
    val cluster = (0 until 18).map { EPt(5.0 + (random.nextDouble() - 0.5) * 2.2, 3.0 + (random.nextDouble() - 0.5) * 1.6, 0) }
    cluster + listOf(EPt(1.2, 5.3, 1), EPt(9.2, 1.1, 1))
}

/** Random x cuts until [target] is alone; the cuts in order. */
private fun isolate(target: Int, random: Random): List<Double> {
    var pool = isoData.indices.toList()
    val cuts = mutableListOf<Double>()
    while (pool.size > 1 && cuts.size < 40) {
        val xs = pool.map { isoData[it].first }
        val lo = xs.min()
        val hi = xs.max()
        if (hi - lo < 1e-9) break
        val t = lo + random.nextDouble() * (hi - lo)
        cuts += t
        val tx = isoData[target].first
        pool = pool.filter { (isoData[it].first < t) == (tx < t) }
    }
    return cuts
}

private fun cOf(n: Int): Double = 2 * (ln(n - 1.0) + 0.5772156649) - 2.0 * (n - 1) / n

private fun isolationForestLab(): EnsLab = EnsLab(frames = { _, _ ->
    val n = isoData.size
    val outlier = n - 2
    val mx = isoData.take(18).map { it.first }.average()
    val my = isoData.take(18).map { it.second }.average()
    val centre = (0 until 18).minBy { (isoData[it].first - mx).pow(2) + (isoData[it].second - my).pow(2) }
    val outCuts = isolate(outlier, Random(3))
    val inCuts = isolate(centre, Random(4))
    val c = cOf(n)
    val depth = isoData.indices.map { i -> (0 until 100).map { t -> isolate(i, Random(1000 + t)).size }.average() }
    val scores = depth.map { 2.0.pow(-it / c) }
    fun pts(active: Int?) = isoData.mapIndexed { i, p -> EPPoint(p.first, p.second, p.third, ring = if (i == active) SimColors.Active else null) }
    fun cutLines(cuts: List<Double>) = cuts.map { EPLine(it, planeBounds.y0, it, planeBounds.y1, null, 1.5f) }
    val legend = listOf(
        Triple(SimColors.Active, SwatchStyle.Dot, "Being isolated"),
        Triple(C1, SwatchStyle.Dot, "Outlier"),
        Triple(Color.Unspecified, SwatchStyle.Line, "Random cut"),
    )
    val formula = listOf(
        "s = 2^(−E[h] / c(n))   c($n) = ${ex(c)}",
        "outlier 2^(−${ex(depth[outlier], 1)}/${ex(c)}) = {w:${ex(scores[outlier])}}   point 2^(−${ex(depth[centre], 1)}/${ex(c)}) = {m:${ex(scores[centre])}}",
    )
    val clusterMax = scores.take(18).max()
    listOf(
        EnsFrame(
            "Isolation Forest cuts at {random} places until a point stands alone.",
            "No distances, no densities: anomalies are simply the points that are easy to cut off.",
            EPlaneScene(planeBounds, pts(null)),
            "Isolate an Outlier",
            legend = legend.take(2),
        ),
        EnsFrame(
            if (outCuts.size == 1) "One random cut isolates the {w:outlier}." else "${outCuts.size} random cuts isolate the {w:outlier}.",
            "It sits far from everything, so almost any cut separates it.",
            EPlaneScene(planeBounds, pts(outlier), lines = cutLines(outCuts)),
            "Isolate a Cluster Point",
            legend = legend,
            chips = listOf(LabChip("outlier", "${outCuts.size}", tintColor = C1)),
        ),
        EnsFrame(
            "This tree needs {${inCuts.size} cuts} to isolate a point from the middle of the cluster.",
            "The outlier fell out after ${outCuts.size}. Averaged over 100 trees, short paths give scores near 1 and long paths push scores below 0.5.",
            EPlaneScene(planeBounds, pts(centre), lines = cutLines(inCuts)),
            "Score All Points",
            formula = formula,
            legend = legend,
            chips = listOf(LabChip("cuts here", "${inCuts.size}", tint = StoryTone.Active), LabChip("outlier", "${outCuts.size}", tintColor = C1)),
        ),
        EnsFrame(
            "Averaged over 100 trees the outliers score {w:${ex(min(scores[outlier], scores[n - 1]))}}+, the cluster at most ${ex(clusterMax)}.",
            "Anything well above 0.5 is flagged. In scikit-learn, contamination sets where that threshold falls.",
            EPlaneScene(planeBounds, isoData.mapIndexed { i, p -> EPPoint(p.first, p.second, if (scores[i] > 0.6) 1 else 0, tag = if (scores[i] > 0.6) ex(scores[i]) else null) }),
            "Start Over",
            formula = formula,
            legend = listOf(Triple(C1, SwatchStyle.Dot, "Score above 0.6"), Triple(C0, SwatchStyle.Dot, "Normal")),
            chips = listOf(LabChip("trees", "100"), LabChip("flagged", "${scores.count { it > 0.6 }}", tintColor = C1)),
        ),
    )
})

// ── Lab ──

private fun ensembleLab(topicId: String): EnsLab = when (topicId) {
    "gradient_boosting" -> gradientBoostingLab()
    "extra_trees" -> extraTreesLab()
    "bagging" -> baggingLab()
    "voting" -> votingLab()
    "stacking" -> stackingLab()
    "xgboost" -> xgboostLab()
    "adaboost" -> adaBoostLab()
    "lightgbm" -> lightGbmLab()
    "isolation_forest" -> isolationForestLab()
    else -> randomForestLab()
}

@Composable
internal fun EnsembleStorySection(topicId: String) {
    val lab = remember(topicId) { ensembleLab(topicId) }
    var tab by remember(topicId) { mutableIntStateOf(lab.startTab) }
    var param by remember(topicId) { mutableIntStateOf(lab.param?.initial ?: 0) }
    val frames = remember(topicId, tab, param) { lab.frames(param, tab) }
    val playback = rememberPlaybackState(key = if (lab.control == EControl.Track) topicId to tab else topicId, stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val dock = LocalLabDock.current
    val reset = {
        tab = lab.startTab
        param = lab.param?.initial ?: 0
        playback.jump(0)
    }

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            when (lab.control) {
                EControl.Stepper -> {
                    val p = lab.param!!
                    LabParamStepper(LabParam(p.name, p.name, p.symbol, p.format(p.values[param]), param > 0, param < p.values.lastIndex)) { d ->
                        param = (param + d).coerceIn(0, p.values.lastIndex)
                    }
                    LabBackActionRow(
                        frame.action,
                        backEnabled = playback.index > 0,
                        onBack = { playback.stepBack() },
                        onAction = { if (playback.atEnd) playback.jump(0) else playback.stepForward() },
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                EControl.Query -> {
                    LabSegments(lab.tabs, tab) { tab = it }
                    LabButton(frame.action, primary = true, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        playback.jump((playback.index + 1) % frames.size)
                    }
                }
                EControl.Track -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (lab.tabs.isNotEmpty() && lab.control != EControl.Query) {
            LabSegments(lab.tabs, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (val scene = frame.scene) {
                    is EPlaneScene -> PlaneView(scene)
                    is ECurveScene -> CurveView(scene)
                    is ETreeScene -> EnsTreeView(scene)
                    is EFoldScene -> FoldView(scene)
                }
                if (frame.formula.isNotEmpty()) EnsFormula(frame.formula, Modifier.padding(top = 12.dp))
                val accent = MaterialTheme.colorScheme.primary
                StoryLegendRow(frame.legend.map { Triple(if (it.first == Color.Unspecified) accent else it.first, it.second, it.third) }, Modifier.padding(top = 14.dp))
            }
        }
        LabChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        if (lab.control == EControl.Track) {
            PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) }, action = { frames[it.coerceIn(0, frames.lastIndex)].action })
        } else if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            if (lab.control != EControl.Track) dock.controls = controls
            dock.navAction = if (lab.navReset) LabNavAction(Icons.Filled.Refresh, "Reset") { reset() } else null
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
private fun EnsFormula(lines: List<String>, modifier: Modifier = Modifier) {
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
private fun PlaneView(scene: EPlaneScene) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val tagStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val b = scene.bounds
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).stage()) {
        fun px(x: Double) = ((x - b.x0) / (b.x1 - b.x0) * size.width).toFloat()
        fun py(y: Double) = (size.height - (y - b.y0) / (b.y1 - b.y0) * size.height).toFloat()
        fun tint(cls: Int) = (if (cls == 1) C1 else C0).copy(alpha = 0.16f)
        scene.regions.forEach { r -> drawRect(tint(r.cls), Offset(px(r.x0), py(r.y1)), Size(px(r.x1) - px(r.x0), py(r.y0) - py(r.y1))) }
        scene.field?.let { f ->
            val cols = 48
            val rows = 30
            val cw = size.width / cols
            val ch = size.height / rows
            for (i in 0 until cols) for (j in 0 until rows) {
                val x = b.x0 + (b.x1 - b.x0) * (i + 0.5) / cols
                val y = b.y1 - (b.y1 - b.y0) * (j + 0.5) / rows
                drawRect(tint(f(x, y)), Offset(i * cw, j * ch), Size(cw + 0.5f, ch + 0.5f))
            }
        }
        scene.lines.forEach { l ->
            drawLine(
                l.color ?: accent,
                Offset(px(l.x0), py(l.y0)),
                Offset(px(l.x1), py(l.y1)),
                strokeWidth = l.width.dp.toPx(),
                pathEffect = if (l.dash) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null,
            )
        }
        if (scene.curve.size > 1) {
            val path = Path()
            scene.curve.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
            drawPath(path, accent, style = Stroke(width = 3.dp.toPx()))
        }
        scene.points.forEach { p ->
            val c = Offset(px(p.x), py(p.y))
            val r = 5.5.dp.toPx() * p.scale
            val color = if (p.cls == 1) C1 else C0
            when (p.mark) {
                EMark.Normal -> {
                    drawCircle(color, r, c)
                    drawCircle(surface, r, c, style = Stroke(width = 1.dp.toPx()))
                }
                EMark.Faded -> drawCircle(color.copy(alpha = 0.3f), r, c)
                EMark.Hollow -> drawCircle(SimColors.Grey, r, c, style = Stroke(width = 1.5.dp.toPx()))
                EMark.Grey -> drawCircle(MutedGrey, r, c)
            }
            p.ring?.let { drawCircle(it, r + 4.dp.toPx(), c, style = Stroke(width = 2.dp.toPx())) }
            p.tag?.let { tag ->
                val layout = measurer.measure(tag, tagStyle)
                drawText(layout, topLeft = Offset(c.x + r, c.y - r - layout.size.height * 0.7f))
            }
        }
        scene.query?.let { (qx, qy) ->
            val c = Offset(px(qx), py(qy))
            drawCircle(SimColors.Active, 7.dp.toPx(), c)
            drawCircle(SimColors.Active, 12.dp.toPx(), c, style = Stroke(width = 2.dp.toPx()))
        }
    }
    scene.tiles?.let { tiles ->
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tiles.forEach { t -> VoteTileView(t, Modifier.weight(1f)) }
        }
    }
    scene.models?.let { rows ->
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { ModelRowView(it) }
        }
    }
}

@Composable
private fun VoteTileView(t: EVoteTile, modifier: Modifier) {
    val fill = when (t.value) {
        null -> SimColors.Tint
        1 -> C1.copy(alpha = 0.2f)
        else -> C0.copy(alpha = 0.2f)
    }
    val ink = when (t.value) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        1 -> Color(0xFFF472B6)
        else -> StoryTone.Path.ink()
    }
    Column(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(fill)
            .then(if (t.current) Modifier.border(1.5.dp, SimColors.Active, RoundedCornerShape(9.dp)) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(t.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(t.value?.toString() ?: "—", fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

@Composable
private fun ModelRowView(m: EModelRow) {
    val cls = if (m.p1 >= 0.5) 1 else 0
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(m.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
        Box(modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(C0.copy(alpha = 0.35f))) {
            Box(Modifier.fillMaxWidth(m.p1.toFloat().coerceIn(0f, 1f)).height(12.dp).background(C1))
            Box(Modifier.fillMaxWidth(0.5f).height(12.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(2.dp).height(12.dp).background(Color.White.copy(alpha = 0.85f)))
            }
        }
        Text(ex(m.p1), fontFamily = IBMPlexMono, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
        Text(
            "→$cls",
            fontFamily = IBMPlexMono,
            fontSize = 13.sp,
            color = if (cls == 1) Color(0xFFF472B6) else StoryTone.Path.ink(),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun CurveView(scene: ECurveScene) {
    val accent = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    val pointColor = SimColors.Idle
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).stage()) {
        val pad = 14.dp.toPx()
        val yLo = -0.05
        val yHi = 1.1
        fun px(x: Double) = (pad + x * (size.width - 2 * pad)).toFloat()
        fun py(y: Double) = (size.height - pad - (y - yLo) / (yHi - yLo) * (size.height - 2 * pad)).toFloat()
        drawLine(axis, Offset(0f, py(0.5)), Offset(size.width, py(0.5)), strokeWidth = 1.dp.toPx())
        fun step(f: (Double) -> Double): Path {
            val path = Path()
            (0..200).forEach { i ->
                val x = i / 200.0
                if (i == 0) path.moveTo(px(x), py(f(x))) else path.lineTo(px(x), py(f(x)))
            }
            return path
        }
        scene.split?.let { t ->
            drawLine(SimColors.Active, Offset(px(t), 0f), Offset(px(t), size.height), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        }
        scene.xs.indices.forEach { i ->
            val x = scene.xs[i]
            drawLine(SimColors.Red, Offset(px(x), py(scene.ys[i])), Offset(px(x), py(scene.f(x))), strokeWidth = 1.5.dp.toPx())
        }
        scene.prev?.let { drawPath(step(it), SimColors.Grey, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))) }
        drawPath(step(scene.f), accent, style = Stroke(width = 3.dp.toPx()))
        scene.xs.indices.forEach { i -> drawCircle(pointColor, 4.5.dp.toPx(), Offset(px(scene.xs[i]), py(scene.ys[i]))) }
    }
}

@Composable
private fun EnsTreeView(scene: ETreeScene) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val activeInk = StoryTone.Active.ink()
    val doneInk = StoryTone.Done.ink()
    val warnInk = StoryTone.Warn.ink()
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.45f).stage()) {
        val style = TextStyle(fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        val layouts = scene.nodes.map { measurer.measure(it.text, style) }
        val h = 34.dp.toPx()
        val widths = layouts.map { it.size.width + 24.dp.toPx() }
        val maxW = widths.maxOrNull() ?: 0f
        val padX = maxW / 2 + 8.dp.toPx()
        val centres = scene.nodes.map { Offset(padX + it.x * (size.width - 2 * padX), it.y * size.height) }
        scene.edges.forEach { (a, b, dashed) ->
            drawLine(
                muted.copy(alpha = 0.7f),
                centres[a] + Offset(0f, h / 2),
                centres[b] - Offset(0f, h / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null,
            )
        }
        scene.nodes.forEachIndexed { i, n ->
            val (fill, stroke, ink) = when (n.tone) {
                ENodeTone.Current -> Triple(SimColors.Active.copy(alpha = 0.18f), SimColors.Active, activeInk)
                ENodeTone.Kept -> Triple(SimColors.Green.copy(alpha = 0.18f), SimColors.Green, doneInk)
                ENodeTone.Pruned -> Triple(SimColors.Red.copy(alpha = 0.15f), SimColors.Red, warnInk)
                ENodeTone.Open -> Triple(SimColors.Tint, muted.copy(alpha = 0.5f), onSurface.copy(alpha = 0.85f))
            }
            val w = widths[i]
            val tl = centres[i] - Offset(w / 2, h / 2)
            drawRoundRect(fill, tl, Size(w, h), CornerRadius(8.dp.toPx()))
            drawRoundRect(
                stroke,
                tl,
                Size(w, h),
                CornerRadius(8.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = if (n.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null),
            )
            drawText(measurer.measure(n.text, style.copy(color = ink)), topLeft = centres[i] - Offset(layouts[i].size.width / 2f, layouts[i].size.height / 2f))
            n.caption?.let { cap ->
                val capLayout = measurer.measure(cap, TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = if (n.captionTone == ENodeTone.Current) activeInk else muted))
                drawText(capLayout, topLeft = centres[i] + Offset(-capLayout.size.width / 2f, h / 2 + 3.dp.toPx()))
            }
        }
    }
}

@Composable
private fun FoldView(scene: EFoldScene) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("5-fold out-of-fold predictions", fontSize = 13.sp, color = muted, modifier = Modifier.weight(1f))
            Text("1-NN base model", fontSize = 13.sp, color = muted)
        }
        scene.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    fontSize = 14.sp,
                    fontWeight = if (row.current) FontWeight.Bold else FontWeight.Normal,
                    color = if (row.current) StoryTone.Active.ink() else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.width(62.dp),
                )
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.cells.forEach { cell ->
                        val color = when (cell) {
                            ECell.Held -> SimColors.Active
                            ECell.Train -> SimColors.Blue.copy(alpha = 0.55f)
                            ECell.Predicted -> SimColors.Green.copy(alpha = 0.75f)
                            ECell.Diagonal -> muted.copy(alpha = 0.3f)
                            ECell.Idle -> SimColors.Tint
                        }
                        Box(Modifier.weight(1f).height(18.dp).background(color, RoundedCornerShape(4.dp)))
                    }
                }
                Text(
                    row.result,
                    fontFamily = IBMPlexMono,
                    fontSize = 14.sp,
                    color = row.resultTone?.ink() ?: muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}
