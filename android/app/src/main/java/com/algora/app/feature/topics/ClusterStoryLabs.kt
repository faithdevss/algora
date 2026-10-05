package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ── Clustering storyboards ───────────────────────────────────────────────────
// K-means, k-medians, k-modes, agglomerative and divisive hierarchies, DBSCAN, HDBSCAN, OPTICS, mean
// shift, BIRCH, affinity propagation, spectral clustering and GMM. Each is one card (a scatter, bars, a
// dendrogram, a tree or a table), the arithmetic of the current step, chips and a headline, then a
// stepper with a back-and-action row, a button, or a step track. Every number is computed from the
// fixed, seeded data below.

internal val clusterStoryTopicIds = setOf(
    "kmeans", "k_medians", "k_modes", "hierarchical_clustering", "hierarchical_divisive", "dbscan", "hdbscan",
    "optics", "mean_shift", "birch", "affinity_propagation", "spectral_clustering", "gmm",
)

private val CBlue = Color(0xFF4F7FE0)
private val COrange = Color(0xFFF08A3C)
private val CGreen = Color(0xFF3F9A62)
private val ClusterPalette = listOf(CBlue, COrange, CGreen, Color(0xFF8B5CF6), Color(0xFFD6457A))
private val NoiseGrey = Color(0xFF6B7280)
private val IdleDot = Color(0xFFCBD0DA)
private val Unvisited = Color(0xFF4B5160)

private fun cColor(label: Int) = if (label < 0) NoiseGrey else ClusterPalette[label % ClusterPalette.size]

// ── Formatting and small math ──

private fun cx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun ladderOf(from: Double, to: Double, step: Double) = (0..((to - from) / step).roundToInt()).map { from + it * step }

private class CsRng(seed: Int) {
    private val random = Random(seed)
    fun u() = random.nextDouble()
    fun normal(): Double {
        val a = max(random.nextDouble(), 1e-12)
        val b = random.nextDouble()
        return sqrt(-2 * ln(a)) * cos(2 * PI * b)
    }
}

private class CsP(val x: Double, val y: Double)

private fun d2(a: CsP, b: CsP) = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)

private fun dist(a: CsP, b: CsP) = sqrt(d2(a, b))

private fun blob(r: CsRng, n: Int, cx: Double, cy: Double, sx: Double, sy: Double) = List(n) {
    val x = cx + r.normal() * sx
    val y = cy + r.normal() * sy
    CsP(x, y)
}

private fun meanOf(pts: List<CsP>) = CsP(pts.sumOf { it.x } / pts.size, pts.sumOf { it.y } / pts.size)

private fun medianOf(v: List<Double>): Double {
    val s = v.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

private fun boundsOf(pts: List<CsP>, pad: Double) =
    (pts.minOf { it.x } - pad to pts.maxOf { it.x } + pad) to (pts.minOf { it.y } - pad to pts.maxOf { it.y } + pad)

// ── Model ──

private class CsDot(
    val p: CsP,
    val color: Color,
    val r: Float = 3.5f,
    val ring: Color? = null,
    val ringR: Float = 0f,
    val ringWidth: Float = 2f,
    val ringDashed: Boolean = false,
    val filled: Boolean = true,
    val top: Boolean = false,
)

private class CsSeg(val a: CsP, val b: CsP, val color: Color, val dashed: Boolean = false, val width: Float = 1.5f)

private class CsCircle(val c: CsP, val radius: Double, val color: Color, val dashed: Boolean = true, val fill: Color? = null, val width: Float = 1.5f)

private class CsEllipse(val c: CsP, val a: Double, val b: Double, val angle: Double, val color: Color)

private class CsLabel(val p: CsP, val text: String)

private sealed interface CsBlock

private class CsPlot(
    val dots: List<CsDot>,
    val xr: Pair<Double, Double>,
    val yr: Pair<Double, Double>,
    val segs: List<CsSeg> = emptyList(),
    val circles: List<CsCircle> = emptyList(),
    val ellipses: List<CsEllipse> = emptyList(),
    val labels: List<CsLabel> = emptyList(),
    val aspect: Float = 1.75f,
    /** Equal scale on both axes, so circles stay round. */
    val uniform: Boolean = true,
    val baseline: Double? = null,
) : CsBlock

private class CsBars(val values: List<Double>, val colors: List<Color>, val highlight: Int? = null, val cut: Double? = null, val caption: String? = null) : CsBlock

private class CsRow(val cells: List<String>, val ink: StoryTone? = null, val fill: StoryTone? = null, val muted: Boolean = false)

private class CsTable(val header: List<String>, val rows: List<CsRow>, val weights: List<Float>) : CsBlock

private class CsFormula(val lines: List<String>) : CsBlock

private enum class CsLinkState { Merged, Now, Later }

private class CsMerge(val a: Int, val b: Int, val height: Double, val state: CsLinkState)

private class CsDendro(val leafCount: Int, val leafNames: List<String>, val merges: List<CsMerge>) : CsBlock

private class CsNode(val full: String, val short: String, val parent: Int, val tone: StoryTone, val dashed: Boolean)

private class CsTree(val nodes: List<CsNode>) : CsBlock

private enum class CsRowState { Done, Current, Future }

private class CsModeRow(val name: String, val cells: List<String>, val state: CsRowState, val mismatch: Set<Int>, val score: String)

private class CsModes(val header: List<String>, val centres: List<List<String>>, val changed: Set<Pair<Int, Int>>, val rows: List<CsModeRow>) : CsBlock

private class CsFrame(
    val headline: String,
    val body: String,
    val blocks: List<CsBlock>,
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
    val action: String = "",
)

private class CsParam(val name: String, val symbol: String, val values: List<Double>, val initial: Int, val format: (Double) -> String)

private data class CsState(val tab: Int = 0, val param: Int = 0, val flag: Int = 0, val index: Int = 0)

private enum class CsControl { Track, Steps, Button }

private class CsLab(
    val control: CsControl,
    val tabs: List<String> = emptyList(),
    val startTab: Int = 0,
    val param: CsParam? = null,
    val startFlag: Int = 0,
    val button: ((CsState) -> String)? = null,
    val onButton: (CsState) -> CsState = { it },
    val frames: (CsState) -> List<CsFrame>,
) {
    val initial get() = CsState(startTab, param?.initial ?: 0, startFlag, 0)
}

private val clusterLegendLine = Triple(SimColors.Active, SwatchStyle.Line, "Move")

private fun clusterLegend(k: Int) = (0 until k).map { Triple(cColor(it), SwatchStyle.Dot, "Cluster ${it + 1}") }

// ── K-means ──

private val kmeansData: List<CsP> by lazy {
    val r = CsRng(71)
    blob(r, 7, 1.6, 2.0, 0.55, 0.5) + blob(r, 10, 4.2, 4.6, 0.5, 0.45) + blob(r, 13, 6.3, 2.2, 0.8, 0.5)
}

private val kmeansStarts = listOf(CsP(1.2, 1.4), CsP(2.4, 2.8), CsP(3.2, 1.3), CsP(0.8, 3.0), CsP(2.8, 3.8))

private fun nearestIndex(p: CsP, centres: List<CsP>) = centres.indices.minBy { d2(p, centres[it]) }

private fun inertia(pts: List<CsP>, assign: IntArray, centres: List<CsP>) = pts.indices.sumOf { d2(pts[it], centres[assign[it]]) }

private fun kmeansLab(): CsLab {
    val ks = listOf(2.0, 3.0, 4.0, 5.0)
    val pts = kmeansData
    val (xr, yr) = boundsOf(pts + kmeansStarts, 0.5)
    return CsLab(
        control = CsControl.Steps,
        param = CsParam("Clusters", "k", ks, 1) { "${it.roundToInt()}" },
        frames = { s ->
            val k = ks[s.param].roundToInt()
            var centres = kmeansStarts.take(k)
            val frames = mutableListOf<CsFrame>()
            fun spokes(assign: IntArray, cs: List<CsP>) = pts.indices.map { CsSeg(pts[it], cs[assign[it]], cColor(assign[it]).copy(alpha = 0.45f), width = 1f) }
            fun rings(cs: List<CsP>) = cs.indices.map { CsDot(cs[it], Color.Transparent, 0f, ring = cColor(it), ringR = 9f, ringWidth = 3f) }
            fun sizes(assign: IntArray) = (0 until k).joinToString(" / ") { c -> "${assign.count { it == c }}" }
            frames += CsFrame(
                "k = $k centres start at {arbitrary spots}.",
                "k-means alternates two moves: assign each point to its nearest centre, then move each centre to its points' mean.",
                listOf(CsPlot(pts.map { CsDot(it, IdleDot) } + rings(centres), xr, yr), CsFormula(listOf("minimise inertia = Σ ‖x − centre‖²"))),
                clusterLegend(k),
                listOf(LabChip("iteration", "0")),
                "Assign Points",
            )
            var previous: IntArray? = null
            var round = 0
            while (round < 12) {
                val assign = IntArray(pts.size) { nearestIndex(pts[it], centres) }
                if (previous != null && previous.contentEquals(assign)) {
                    frames += CsFrame(
                        "No point changed cluster: k-means has {converged} after $round rounds.",
                        "Inertia ${cx(inertia(pts, assign, centres), 1)}. A different start can land in a worse minimum; k-means++ spreads the starts out.",
                        listOf(CsPlot(pts.indices.map { CsDot(pts[it], cColor(assign[it])) } + rings(centres), xr, yr, spokes(assign, centres)),
                            CsFormula(listOf("inertia {v:${cx(inertia(pts, assign, centres), 1)}}, no reassignments"))),
                        clusterLegend(k),
                        listOf(LabChip("iteration", "$round"), LabChip("sizes", sizes(assign))),
                        "Start Over",
                    )
                    break
                }
                round++
                val before = inertia(pts, assign, centres)
                frames += CsFrame(
                    "Each point joins its {nearest centre}.",
                    "Inertia, the summed squared distance to the centres, is now ${cx(before, 1)}.",
                    listOf(CsPlot(pts.indices.map { CsDot(pts[it], cColor(assign[it])) } + rings(centres), xr, yr, spokes(assign, centres)),
                        CsFormula(listOf("inertia {v:${cx(before, 1)}} with ${sizes(assign)} points"))),
                    clusterLegend(k),
                    listOf(LabChip("iteration", "$round"), LabChip("sizes", sizes(assign))),
                    "Update Centres",
                )
                val moved = centres.indices.map { c -> pts.indices.filter { assign[it] == c }.let { m -> if (m.isEmpty()) centres[c] else meanOf(m.map { pts[it] }) } }
                val after = inertia(pts, assign, moved)
                val next = IntArray(pts.size) { nearestIndex(pts[it], moved) }
                val largest = centres.indices.maxBy { dist(centres[it], moved[it]) }
                val stable = next.contentEquals(assign)
                frames += CsFrame(
                    "Each centre {jumps to the mean} of the points assigned to it.",
                    if (stable) "Next, no point will change its nearest centre, so the loop is done."
                    else "Next, points re-pick their nearest centre; inertia falls again to ${cx(inertia(pts, next, moved), 1)}.",
                    listOf(
                        CsPlot(
                            pts.indices.map { CsDot(pts[it], cColor(assign[it])) } +
                                centres.indices.map { CsDot(centres[it], Color.Transparent, 0f, ring = cColor(it), ringR = 7f, ringDashed = true) } +
                                rings(moved),
                            xr, yr,
                            spokes(assign, moved) + centres.indices.map { CsSeg(centres[it], moved[it], SimColors.Active, width = 2.5f) },
                        ),
                        CsFormula(listOf("inertia ${cx(before, 1)} → {v:${cx(after, 1)}} after this update", "largest move: centre ${largest + 1}, ${cx(dist(centres[largest], moved[largest]))}")),
                    ),
                    listOf(Triple(SimColors.Grey, SwatchStyle.Ring, "Old centre"), clusterLegendLine) + clusterLegend(k),
                    listOf(LabChip("iteration", "$round"), LabChip("sizes", sizes(assign))),
                    "Assign Points",
                )
                centres = moved
                previous = assign
            }
            frames
        },
    )
}

// ── K-medians ──

private val mediansData: Pair<List<CsP>, List<CsP>> by lazy {
    val r = CsRng(73)
    val base = blob(r, 8, 2.5, 3.0, 0.35, 0.3) + blob(r, 7, 6.0, 5.8, 0.45, 0.3)
    val outliers = listOf(CsP(5.6, 1.0), CsP(6.0, 1.3), CsP(6.3, 0.8), CsP(5.9, 0.6), CsP(6.5, 1.1))
    base to outliers
}

/** Lloyd's loop from a farthest-point start at point 0: means with squared error, or medians with absolute error. */
private fun lloyd(pts: List<CsP>, k: Int, medians: Boolean, starts: List<Int> = listOf(0)): Pair<IntArray, List<CsP>> {
    val centres = starts.take(k).map { pts[it] }.toMutableList()
    while (centres.size < k) centres += pts.maxBy { p -> centres.minOf { d2(p, it) } }
    val assign = IntArray(pts.size)
    repeat(30) {
        pts.forEachIndexed { i, p ->
            assign[i] = centres.indices.minBy { c -> if (medians) abs(p.x - centres[c].x) + abs(p.y - centres[c].y) else d2(p, centres[c]) }
        }
        for (c in 0 until k) {
            val m = pts.indices.filter { assign[it] == c }.map { pts[it] }
            if (m.isNotEmpty()) centres[c] = if (medians) CsP(medianOf(m.map { it.x }), medianOf(m.map { it.y })) else meanOf(m)
        }
    }
    return assign to centres
}

private fun kMediansLab(): CsLab {
    val ks = listOf(2.0, 3.0, 4.0)
    val (base, extra) = mediansData
    return CsLab(
        control = CsControl.Button,
        tabs = listOf("K-means", "K-medians"),
        startTab = 1,
        param = CsParam("Clusters", "k", ks, 0) { "${it.roundToInt()}" },
        startFlag = 2,
        button = { "Add Outlier" },
        onButton = { it.copy(flag = (it.flag + 1) % extra.size) },
        frames = { s ->
            val k = ks[s.param].roundToInt()
            val n = s.flag + 1
            val pts = base + extra.take(n)
            val medians = s.tab == 1
            val (assign, centres) = lloyd(pts, k, medians, listOf(0, 8))
            val outlierIdx = (base.size until pts.size).toList()
            val home = outlierIdx.map { assign[it] }.groupingBy { it }.eachCount().maxBy { it.value }.key
            val members = pts.indices.filter { assign[it] == home }.map { pts[it] }
            val regular = pts.indices.filter { assign[it] == home && it < base.size }
            val mean = meanOf(members)
            val median = CsP(medianOf(members.map { it.x }), medianOf(members.map { it.y }))
            val own = regular.isEmpty()
            val coreMean = if (regular.isEmpty()) mean else meanOf(regular.map { pts[it] })
            val drag = dist(mean, coreMean)
            val (xr, yr) = boundsOf(base + extra, 0.5)
            val (shown, other) = if (medians) median to mean else mean to median
            val dots = pts.indices.map { i -> if (i >= base.size) CsDot(pts[i], SimColors.Active, 3.5f) else CsDot(pts[i], cColor(assign[i])) } +
                centres.indices.map { CsDot(if (it == home) shown else centres[it], Color.Transparent, 0f, ring = cColor(it), ringR = 9f, ringWidth = 3f) } +
                (if (own) emptyList() else listOf(CsDot(other, Color.Transparent, 0f, ring = SimColors.Grey, ringR = 8f, ringDashed = true)))
            val word = listOf("One", "Two", "Three", "Four", "Five")[n - 1]
            val (headline, body) = when {
                own -> "With k = $k the outliers get {their own cluster}." to
                    "A spare centre is spent on the strays, so no real cluster is dragged; fewer clusters would force a choice."
                medians -> "$word far ${if (n == 1) "point drags" else "points drag"} the mean {${cx(drag)}} away; the median stays in the cluster." to
                    "A median moves with how many outliers there are, not how far away they sit."
                else -> "The mean follows the ${word.lowercase()} far ${if (n == 1) "point" else "points"} {${cx(drag)}} out of the cluster." to
                    "Squared error rewards chasing outliers; k-medians' absolute error doesn't care how far away they sit."
            }
            listOf(
                CsFrame(
                    headline,
                    body,
                    listOf(
                        CsPlot(dots, xr, yr, if (own) emptyList() else listOf(CsSeg(mean, median, SimColors.Grey, width = 1.2f))),
                        CsFormula(listOf("x: mean ${cx(mean.x)} median {v:${cx(median.x)}}", "y: mean ${cx(mean.y)} median {v:${cx(median.y)}}")),
                    ),
                    listOf(
                        Triple(CBlue, SwatchStyle.Ring, if (medians) "Median centre" else "Mean centre"),
                        Triple(SimColors.Grey, SwatchStyle.Dashed, if (medians) "Mean centre" else "Median centre"),
                        Triple(SimColors.Active, SwatchStyle.Dot, "Outlier"),
                    ),
                ),
            )
        },
    )
}

// ── K-modes ──

private val modeHeader = listOf("colour", "size", "shape", "finish")
private val modeRows = listOf(
    listOf("red", "small", "round", "matte"),
    listOf("red", "small", "square", "matte"),
    listOf("red", "large", "round", "matte"),
    listOf("blue", "large", "square", "gloss"),
    listOf("blue", "large", "square", "matte"),
    listOf("blue", "large", "round", "gloss"),
    listOf("green", "large", "round", "gloss"),
    listOf("blue", "large", "round", "gloss"),
)
private val modeStarts = listOf(listOf("red", "small", "round", "matte"), listOf("blue", "large", "square", "gloss"))

private fun mismatches(a: List<String>, b: List<String>) = a.indices.count { a[it] != b[it] }

private fun kModesLab(): CsLab = CsLab(
    control = CsControl.Track,
    frames = {
        val assign = modeRows.map { row -> if (mismatches(row, modeStarts[1]) < mismatches(row, modeStarts[0])) 1 else 0 }
        val legend = listOf(
            Triple(SimColors.Active, SwatchStyle.Fill, "Current row"),
            Triple(Color(0xFFB4A2FF), SwatchStyle.Fill, "Centre modes"),
            Triple(Color(0xFFF08A8A), SwatchStyle.Fill, "Mismatch (underlined)"),
        )
        fun rows(upTo: Int) = modeRows.indices.map { i ->
            val c = assign[i]
            val state = when {
                i < upTo -> CsRowState.Done
                i == upTo -> CsRowState.Current
                else -> CsRowState.Future
            }
            val m = if (state == CsRowState.Future) emptySet() else modeRows[i].indices.filter { modeRows[i][it] != modeStarts[c][it] }.toSet()
            CsModeRow("${i + 1}", modeRows[i], state, if (state == CsRowState.Current) m else emptySet(),
                if (state == CsRowState.Future) "—" else "${mismatches(modeRows[i], modeStarts[0])} · ${mismatches(modeRows[i], modeStarts[1])}")
        }
        val assignFrames = modeRows.indices.map { i ->
            val a = mismatches(modeRows[i], modeStarts[0])
            val b = mismatches(modeRows[i], modeStarts[1])
            val c = assign[i]
            val best = if (c == 0) a else b
            CsFrame(
                if (a == b) "Row ${i + 1} ties at {$a} mismatches each, so it joins c1, the first centre."
                else if (best == 0) "Row ${i + 1} matches c${c + 1} {exactly}, so it joins c${c + 1}."
                else "Row ${i + 1} differs from c${c + 1} on only {$best} attribute${if (best == 1) "" else "s"}, so it joins c${c + 1}.",
                "No distances or means: k-modes counts mismatches and uses the most common value as the centre.",
                listOf(CsModes(modeHeader, modeStarts, emptySet(), rows(i)), CsFormula(listOf("row ${i + 1}: $a mismatches to c1, $b to c2 → {v:c${c + 1}}"))),
                legend,
                action = if (i < modeRows.lastIndex) "Assign Row ${i + 2}" else "Update Modes",
            )
        }
        val newCentres = (0..1).map { c ->
            val members = modeRows.indices.filter { assign[it] == c }.map { modeRows[it] }
            modeHeader.indices.map { col ->
                val values = members.map { it[col] }
                values.distinct().maxBy { v -> values.count { it == v } }
            }
        }
        val changed = (0..1).flatMap { c -> modeHeader.indices.filter { newCentres[c][it] != modeStarts[c][it] }.map { c to it } }.toSet()
        val update = CsFrame(
            "New centres take the {most common value} in each column of their rows.",
            if (changed.isEmpty()) "No mode changed, so no row will move: k-modes has converged."
            else "${changed.size} centre value${if (changed.size == 1) "" else "s"} changed, so rows are reassigned and the loop repeats.",
            listOf(
                CsModes(modeHeader, newCentres, changed, modeRows.indices.map { i ->
                    CsModeRow("${i + 1}", modeRows[i], CsRowState.Done, emptySet(), "c${assign[i] + 1}")
                }),
                CsFormula(listOf("c1 = ${newCentres[0].joinToString(" ")}", "c2 = ${newCentres[1].joinToString(" ")}")),
            ),
            legend.drop(1).take(1),
            action = "Start Over",
        )
        assignFrames + update
    },
)

// ── Agglomerative ──

private val aggPoints = listOf(
    CsP(1.0, 2.0), CsP(1.6, 2.35), CsP(3.2, 1.7), CsP(5.3, 2.05), CsP(6.0, 4.3), CsP(6.7, 4.45), CsP(7.1, 3.8), CsP(8.7, 1.9),
)

private class CsLinkStep(val a: List<Int>, val b: List<Int>, val height: Double, val pair: Pair<Int, Int>, val nodeA: Int, val nodeB: Int)

private fun linkage(pts: List<CsP>, kind: Int): List<CsLinkStep> {
    val clusters = pts.indices.map { listOf(it) to it }.toMutableList()
    val steps = mutableListOf<CsLinkStep>()
    var next = pts.size
    while (clusters.size > 1) {
        var best = Double.MAX_VALUE
        var bi = 0
        var bj = 1
        var pair = 0 to 0
        for (i in clusters.indices) for (j in i + 1 until clusters.size) {
            val ds = clusters[i].first.flatMap { a -> clusters[j].first.map { b -> Triple(a, b, dist(pts[a], pts[b])) } }
            val (d, p) = when (kind) {
                0 -> ds.minBy { it.third }.let { it.third to (it.first to it.second) }
                1 -> ds.maxBy { it.third }.let { it.third to (it.first to it.second) }
                else -> ds.sumOf { it.third } / ds.size to ds.minBy { it.third }.let { it.first to it.second }
            }
            if (d < best - 1e-12) { best = d; bi = i; bj = j; pair = p }
        }
        steps += CsLinkStep(clusters[bi].first, clusters[bj].first, best, pair, clusters[bi].second, clusters[bj].second)
        val merged = (clusters[bi].first + clusters[bj].first).sorted() to next++
        clusters.removeAt(bj)
        clusters[bi] = merged
    }
    return steps
}

private fun aggLab(): CsLab {
    val names = listOf("Single", "Complete", "Average")
    return CsLab(
        control = CsControl.Track,
        tabs = names,
        frames = { s ->
            val steps = linkage(aggPoints, s.tab)
            val (xr, yr) = boundsOf(aggPoints, 0.6)
            val word = listOf("single link: closest pair", "complete link: farthest pair", "average link: mean over pairs")[s.tab]
            steps.indices.map { i ->
                val st = steps[i]
                val now = (st.a + st.b).toSet()
                val dots = aggPoints.indices.map { CsDot(aggPoints[it], if (it in now) SimColors.Active else IdleDot, 4f) }
                val segs = (0 until i).map { CsSeg(aggPoints[steps[it].pair.first], aggPoints[steps[it].pair.second], SimColors.Answer, width = 2.5f) } +
                    CsSeg(aggPoints[st.pair.first], aggPoints[st.pair.second], SimColors.Active, dashed = true, width = 2f)
                fun set(c: List<Int>) = c.joinToString(", ") { "${it + 1}" }
                val pairText = if (s.tab == 2) "${cx(st.height)} over ${st.a.size * st.b.size} pairs" else "${st.pair.first + 1}-${st.pair.second + 1} = {v:${cx(st.height)}}"
                CsFrame(
                    "Merge ${i + 1} joins {[${set(st.a)}]} and [${set(st.b)}] at ${cx(st.height)}.",
                    when {
                        i == 0 -> "Every point starts alone; the two closest clusters merge first."
                        i == steps.lastIndex -> "All ${aggPoints.size} points are one cluster now. Cut the tree at any height to pick k."
                        s.tab == 0 -> "Single link uses the closest pair, so clusters can chain into long shapes."
                        s.tab == 1 -> "Complete link uses the farthest pair, so it prefers tight, round clusters."
                        else -> "Average link uses the mean of all pairs, a middle ground between single and complete."
                    },
                    listOf(
                        CsPlot(dots, xr, yr, segs, labels = aggPoints.indices.map { CsLabel(aggPoints[it], "${it + 1}") }, aspect = 2.1f),
                        CsDendro(aggPoints.size, aggPoints.indices.map { "${it + 1}" }, steps.indices.map { j ->
                            CsMerge(steps[j].nodeA, steps[j].nodeB, steps[j].height, if (j < i) CsLinkState.Merged else if (j == i) CsLinkState.Now else CsLinkState.Later)
                        }),
                        CsFormula(listOf(if (s.tab == 2) "$word = {v:$pairText}" else "$word $pairText")),
                    ),
                    listOf(Triple(SimColors.Answer, SwatchStyle.Line, "Merged"), Triple(SimColors.Active, SwatchStyle.DashedLine, "Merging now"), Triple(SimColors.Grey, SwatchStyle.Line, "Later")),
                    action = if (i < steps.lastIndex) "Merge Next Pair" else "Start Over",
                )
            }
        },
    )
}

// ── Divisive ──

private val divisiveData: List<CsP> by lazy {
    val r = CsRng(79)
    blob(r, 10, 0.2, 0.3, 0.05, 0.05) + blob(r, 10, 0.46, 0.36, 0.1, 0.08) + blob(r, 10, 0.82, 0.8, 0.04, 0.04)
}

private fun diameter(pts: List<CsP>, members: List<Int>): Double {
    var best = 0.0
    for (i in members.indices) for (j in i + 1 until members.size) best = max(best, dist(pts[members[i]], pts[members[j]]))
    return best
}

/** 2-means from the farthest pair. */
private fun bisect(pts: List<CsP>, members: List<Int>): Pair<List<Int>, List<Int>> {
    var a = members[0]
    var b = members[1]
    var far = -1.0
    for (i in members.indices) for (j in i + 1 until members.size) {
        val d = d2(pts[members[i]], pts[members[j]])
        if (d > far) { far = d; a = members[i]; b = members[j] }
    }
    var ca = pts[a]
    var cb = pts[b]
    var left = listOf(a)
    var right = listOf(b)
    repeat(10) {
        left = members.filter { d2(pts[it], ca) <= d2(pts[it], cb) }
        right = members.filter { d2(pts[it], ca) > d2(pts[it], cb) }
        if (left.isNotEmpty()) ca = meanOf(left.map { pts[it] })
        if (right.isNotEmpty()) cb = meanOf(right.map { pts[it] })
    }
    return left to right
}

private fun divisiveLab(): CsLab = CsLab(
    control = CsControl.Track,
    frames = {
        val pts = divisiveData
        class N(val members: List<Int>, val parent: Int, val diameter: Double)
        val nodes = mutableListOf(N(pts.indices.toList(), -1, diameter(pts, pts.indices.toList())))
        val split = mutableListOf<Int>()
        fun label(n: N) = "${n.members.size} · ⌀ ${cx(n.diameter)}"
        fun tree(splitting: Int?): CsTree = CsTree(nodes.mapIndexed { i, n ->
            val tone = when {
                i == splitting || (splitting != null && n.parent == splitting) -> StoryTone.Active
                i in split -> StoryTone.Done
                else -> StoryTone.Idle
            }
            CsNode(label(n), "${n.members.size}", n.parent, tone, splitting != null && n.parent == splitting)
        })
        val frames = mutableListOf(
            CsFrame(
                "All {${pts.size}} points start as one cluster, diameter ${cx(nodes[0].diameter)}.",
                "Divisive clustering works top-down: split a cluster in two, then pick the next leaf to split.",
                listOf(tree(null), CsFormula(listOf("⌀ = largest distance inside a cluster = {v:${cx(nodes[0].diameter)}}"))),
                listOf(Triple(SimColors.Grey, SwatchStyle.Fill, "Open leaf")),
                action = "Split Largest",
            ),
        )
        for (round in 1..6) {
            val open = nodes.indices.filter { it !in split && nodes[it].members.size > 1 }
            val pick = open.maxBy { nodes[it].diameter }
            val listed = open.sortedByDescending { nodes[it].diameter }
            val (l, r) = bisect(pts, nodes[pick].members)
            split += pick
            nodes += N(l, pick, diameter(pts, l))
            nodes += N(r, pick, diameter(pts, r))
            frames += CsFrame(
                "Split $round takes the leaf with the {largest diameter}, ${cx(nodes[pick].diameter)}.",
                if (round == 1) "Each split is a 2-means bisection of the chosen leaf, seeded from its two farthest points."
                else "Divisive works top-down: it keeps splitting the least cohesive cluster.",
                listOf(
                    tree(pick),
                    CsFormula(listOf("open leaves ⌀: " + listed.mapIndexed { j, n -> if (j == 0) "{${cx(nodes[n].diameter)}}" else cx(nodes[n].diameter) }.joinToString(", ") + " → split the largest")),
                ),
                listOf(Triple(SimColors.Green, SwatchStyle.Fill, "Already split"), Triple(SimColors.Active, SwatchStyle.Fill, "Splitting now"), Triple(SimColors.Grey, SwatchStyle.Fill, "Open leaf")),
                action = if (round < 6) "Split Next" else "Start Over",
            )
        }
        frames
    },
)

// ── DBSCAN ──

private val dbscanData: List<CsP> by lazy {
    val r = CsRng(83)
    val core = blob(r, 11, 2.2, 3.0, 0.38, 0.3)
    val chain = List(9) { CsP(4.6 + it * 0.42, 4.1 - it * 0.08 + r.normal() * 0.05) }
    val noise = listOf(CsP(0.6, 5.2), CsP(3.5, 2.2), CsP(3.0, 0.9), CsP(6.4, 1.4), CsP(5.2, 5.5), CsP(0.9, 1.2), CsP(8.2, 2.6))
    core + chain + noise
}

private const val MIN_PTS = 4

private fun dbscanLab(): CsLab {
    val eps = ladderOf(0.4, 1.4, 0.1)
    val pts = dbscanData
    val (xr, yr) = boundsOf(pts, 0.6)
    return CsLab(
        control = CsControl.Steps,
        param = CsParam("Radius", "ε", eps, 5) { cx(it, 1) },
        frames = { s ->
            val e = eps[s.param]
            val neighbours = pts.indices.map { i -> pts.indices.filter { j -> j != i && dist(pts[i], pts[j]) <= e } }
            val label = IntArray(pts.size) { -2 } // −2 unvisited, −1 noise
            val visited = BooleanArray(pts.size)
            val frames = mutableListOf<CsFrame>()
            var clusters = 0
            var count = 0
            fun frame(i: Int) {
                count++
                val n = neighbours[i].size + 1
                val core = n >= MIN_PTS
                val kind = when {
                    core -> "core"
                    label[i] >= 0 -> "border"
                    else -> "noise"
                }
                val dots = pts.indices.map { j ->
                    when {
                        j == i -> CsDot(pts[j], SimColors.Active, 5f, top = true)
                        !visited[j] && label[j] < 0 -> CsDot(pts[j], Unvisited, 3.5f, ring = if (j in neighbours[i]) Color.White else null, ringR = 3f)
                        else -> CsDot(pts[j], cColor(label[j]), 4f, ring = if (j in neighbours[i]) Color.White else null, ringR = 3f)
                    }
                }
                val (headline, body) = when (kind) {
                    "core" -> "This point is {core}, so the cluster expands through it." to "Chaining core points lets DBSCAN follow shapes that aren't round."
                    "border" -> "This point is a {border} point: reachable from a core, too sparse to expand." to "It joins the cluster, but the expansion stops here."
                    else -> "Only $n point${if (n == 1) "" else "s"} within ε: this one is {w:noise} for now." to "A core point found later can still claim it as a border point."
                }
                frames += CsFrame(
                    headline,
                    body,
                    listOf(
                        CsPlot(dots, xr, yr, circles = listOf(CsCircle(pts[i], e, SimColors.Active, fill = SimColors.Active.copy(alpha = 0.08f)))),
                        CsFormula(listOf("neighbours within ε = $n ${if (core) "≥" else "<"} minPts $MIN_PTS → {${if (kind == "noise") "w" else "v"}:$kind}")),
                    ),
                    listOf(
                        Triple(SimColors.Active, SwatchStyle.Dot, "Visiting"),
                        Triple(Color.White, SwatchStyle.Ring, "In ε"),
                        Triple(CBlue, SwatchStyle.Dot, "Cluster 1"),
                        Triple(Unvisited, SwatchStyle.Dot, "Not visited"),
                    ),
                    listOf(LabChip("clusters", "$clusters"), LabChip("visited", "$count / ${pts.size}")),
                    "Visit Next Point",
                )
            }
            for (p in pts.indices) {
                if (visited[p]) continue
                visited[p] = true
                if (neighbours[p].size + 1 < MIN_PTS) {
                    if (label[p] < 0) label[p] = -1
                    frame(p)
                    continue
                }
                val c = clusters++
                label[p] = c
                frame(p)
                val queue = ArrayDeque(neighbours[p])
                while (queue.isNotEmpty()) {
                    val q = queue.removeFirst()
                    if (label[q] < 0) label[q] = c
                    if (visited[q]) continue
                    visited[q] = true
                    if (neighbours[q].size + 1 >= MIN_PTS) queue.addAll(neighbours[q].filter { !visited[it] || label[it] < 0 })
                    frame(q)
                }
            }
            val noise = label.count { it < 0 }
            frames += CsFrame(
                "ε = ${cx(e, 1)} finds {$clusters cluster${if (clusters == 1) "" else "s"}} and $noise noise points.",
                "A smaller ε splits the chain and drops more noise; a larger one merges everything into one blob.",
                listOf(CsPlot(pts.indices.map { CsDot(pts[it], cColor(label[it]), 4f) }, xr, yr), CsFormula(listOf("minPts $MIN_PTS, ε ${cx(e, 1)} → {v:$clusters} clusters"))),
                clusterLegend(clusters) + Triple(NoiseGrey, SwatchStyle.Dot, "Noise"),
                listOf(LabChip("clusters", "$clusters"), LabChip("visited", "${pts.size} / ${pts.size}")),
                "Start Over",
            )
            frames.mapIndexed { i, f -> if (i == frames.lastIndex - 1) CsFrame(f.headline, f.body, f.blocks, f.legend, f.chips, "Show Clusters") else f }
        },
    )
}

// ── HDBSCAN and OPTICS share one dataset: a dense blob, a sparse blob and scattered noise ──

private val densityData: List<CsP> by lazy {
    val r = CsRng(89)
    blob(r, 8, 1.8, 4.4, 0.3, 0.12) + blob(r, 10, 5.0, 3.1, 0.55, 0.45) +
        listOf(CsP(0.9, 2.2), CsP(3.5, 5.3), CsP(6.9, 4.9), CsP(6.6, 1.8))
}

private fun coreDistances(pts: List<CsP>, minPts: Int) = pts.indices.map { i -> pts.indices.filter { it != i }.map { dist(pts[i], pts[it]) }.sorted()[minPts - 1] }

private class CsHdb(val labels: IntArray, val core: List<Double>, val mst: List<Triple<Int, Int, Double>>)

private fun hdbscan(pts: List<CsP>, minPts: Int): CsHdb {
    val n = pts.size
    val core = coreDistances(pts, minPts)
    fun mr(a: Int, b: Int) = maxOf(core[a], core[b], dist(pts[a], pts[b]))
    // Prim's minimum spanning tree over mutual reachability.
    val inTree = BooleanArray(n)
    val best = DoubleArray(n) { Double.MAX_VALUE }
    val from = IntArray(n) { -1 }
    best[0] = 0.0
    val mst = mutableListOf<Triple<Int, Int, Double>>()
    repeat(n) {
        val u = (0 until n).filter { !inTree[it] }.minBy { best[it] }
        inTree[u] = true
        if (from[u] >= 0) mst += Triple(from[u], u, best[u])
        for (v in 0 until n) if (!inTree[v] && mr(u, v) < best[v]) { best[v] = mr(u, v); from[v] = u }
    }
    // Single-linkage tree from the sorted edges: node ids ≥ n are merges.
    val parent = IntArray(2 * n) { it }
    fun find(x: Int): Int { var a = x; while (parent[a] != a) a = parent[a]; return a }
    val left = IntArray(2 * n) { -1 }
    val right = IntArray(2 * n) { -1 }
    val height = DoubleArray(2 * n)
    val size = IntArray(2 * n) { if (it < n) 1 else 0 }
    var next = n
    mst.sortedBy { it.third }.forEach { (a, b, w) ->
        val ra = find(a)
        val rb = find(b)
        left[next] = ra; right[next] = rb; height[next] = w; size[next] = size[ra] + size[rb]
        parent[ra] = next; parent[rb] = next
        next++
    }
    val root = next - 1
    fun leaves(node: Int): List<Int> = if (node < n) listOf(node) else leaves(left[node]) + leaves(right[node])
    // Condensed tree: clusters with their start node, birth λ, stability and children.
    class C(val node: Int, val birth: Double, var stability: Double = 0.0, val children: MutableList<Int> = mutableListOf())
    val cs = mutableListOf<C>()
    fun walk(node: Int, c: Int) {
        if (node < n) return
        val lam = 1 / max(height[node], 1e-9)
        val a = left[node]
        val b = right[node]
        val big = size[a] >= minPts && size[b] >= minPts
        when {
            big -> {
                cs[c].stability += size[node] * (lam - cs[c].birth)
                for (child in listOf(a, b)) {
                    cs += C(child, lam)
                    cs[c].children += cs.lastIndex
                    walk(child, cs.lastIndex)
                }
            }
            size[a] < minPts && size[b] < minPts -> cs[c].stability += size[node] * (lam - cs[c].birth)
            else -> {
                val (small, large) = if (size[a] < minPts) a to b else b to a
                cs[c].stability += size[small] * (lam - cs[c].birth)
                walk(large, c)
            }
        }
    }
    cs += C(root, 0.0)
    walk(root, 0)
    val selected = BooleanArray(cs.size)
    val total = DoubleArray(cs.size)
    for (c in cs.indices.reversed()) {
        val childSum = cs[c].children.sumOf { total[it] }
        if (cs[c].children.isEmpty() || (c != 0 && cs[c].stability >= childSum)) {
            selected[c] = c != 0
            total[c] = cs[c].stability
            fun clear(x: Int) { cs[x].children.forEach { selected[it] = false; clear(it) } }
            if (c != 0) clear(c)
        } else total[c] = childSum
    }
    val labels = IntArray(n) { -1 }
    var id = 0
    // Order clusters by their smallest point index, so the dense blob (points 0…) is cluster 1.
    cs.indices.filter { selected[it] }.sortedBy { leaves(cs[it].node).min() }.forEach { c -> leaves(cs[c].node).forEach { labels[it] = id }; id++ }
    return CsHdb(labels, core, mst)
}

private fun densityLegend(labels: IntArray) = (0 until (labels.maxOrNull() ?: -1) + 1).map {
    Triple(cColor(it), SwatchStyle.Dot, if (it == 0) "Dense" else if (it == 1) "Sparse" else "Cluster ${it + 1}")
} + Triple(NoiseGrey, SwatchStyle.Dot, "Noise")

private fun hdbscanLab(): CsLab {
    val mins = listOf(3.0, 4.0, 5.0, 6.0)
    val pts = densityData
    val (xr, yr) = boundsOf(pts, 0.8)
    return CsLab(
        control = CsControl.Steps,
        param = CsParam("Min points", "minPts", mins, 1) { "${it.roundToInt()}" },
        frames = { s ->
            val m = mins[s.param].roundToInt()
            val h = hdbscan(pts, m)
            val order = pts.indices.sortedBy { h.core[it] }
            fun bars(highlight: Int?) = CsBars(order.map { h.core[it] }, order.map { if (it == highlight) SimColors.Active else cColor(h.labels[it]) }, order.indexOf(highlight).takeIf { it >= 0 }, caption = "core distance, sorted")
            fun dots(focus: Int?) = pts.indices.map { if (it == focus) CsDot(pts[it], SimColors.Active, 5f, top = true) else CsDot(pts[it], cColor(h.labels[it])) }
            val dense = (0 until 8).sortedBy { h.core[it] }[4]
            val sparseIdx = (8 until 18).sortedBy { h.core[it] }
            val sparse = sparseIdx[sparseIdx.size / 2]
            val nb = pts.indices.filter { it != sparse }.minBy { dist(pts[sparse], pts[it]) }
            val ratio = h.core[sparse] / h.core[dense]
            val legend = densityLegend(h.labels)
            val clusters = (h.labels.maxOrNull() ?: -1) + 1
            val noise = h.labels.count { it < 0 }
            val longest = h.mst.maxOf { it.third }
            listOf(
                CsFrame(
                    "A dense point reaches $m neighbours within {${cx(h.core[dense])}}.",
                    "That radius is its core distance: small where points crowd, large where they thin out.",
                    listOf(CsPlot(dots(dense), xr, yr, circles = listOf(CsCircle(pts[dense], h.core[dense], SimColors.Active, fill = SimColors.Active.copy(alpha = 0.08f)))), bars(dense),
                        CsFormula(listOf("core(a) = distance to its ${m}th neighbour = {v:${cx(h.core[dense])}}"))),
                    legend + Triple(SimColors.Active, SwatchStyle.DashedLine, "Core radius"),
                    action = "Check a Sparse Point",
                ),
                CsFrame(
                    "A sparse point needs a {${cx(ratio, 1)}×} wider circle to reach $m neighbours.",
                    "HDBSCAN keeps every density level instead of one ε, so both blobs survive.",
                    listOf(CsPlot(dots(sparse), xr, yr, circles = listOf(CsCircle(pts[sparse], h.core[sparse], SimColors.Active, fill = SimColors.Active.copy(alpha = 0.08f)))), bars(sparse),
                        CsFormula(listOf("mreach = max(core a, core b, d)", "= max(${cx(h.core[sparse])}, ${cx(h.core[nb])}, ${cx(dist(pts[sparse], pts[nb]))}) = {v:${cx(maxOf(h.core[sparse], h.core[nb], dist(pts[sparse], pts[nb])))}}"))),
                    legend + Triple(SimColors.Active, SwatchStyle.DashedLine, "Core radius"),
                    action = "Build the Tree",
                ),
                CsFrame(
                    "One tree over {mutual reachability} links every point.",
                    "Long edges bridge sparse gaps; cutting them at every height at once gives the whole hierarchy.",
                    listOf(CsPlot(dots(null), xr, yr, h.mst.map { CsSeg(pts[it.first], pts[it.second], SimColors.Grey.copy(alpha = 0.7f), width = 1.2f) }), bars(null),
                        CsFormula(listOf("minimum spanning tree: ${pts.size - 1} edges, longest {v:${cx(longest)}}"))),
                    legend + Triple(SimColors.Grey, SwatchStyle.Line, "Tree edge"),
                    action = "Pick Stable Clusters",
                ),
                CsFrame(
                    "The {$clusters most stable} cluster${if (clusters == 1) "" else "s"} survive${if (clusters == 1) "s" else ""}; $noise ${if (noise == 1) "point is" else "points are"} noise.",
                    "A single ε would have to pick between the dense and the sparse blob. HDBSCAN keeps what persists longest.",
                    listOf(CsPlot(dots(null), xr, yr), bars(null), CsFormula(listOf("minPts $m → {v:$clusters} clusters, $noise noise"))),
                    legend,
                    action = "Start Over",
                ),
            )
        },
    )
}

private class CsOptics(val order: List<Int>, val reach: List<Double>, val core: List<Double>)

private fun optics(pts: List<CsP>, minPts: Int): CsOptics {
    val core = coreDistances(pts, minPts)
    val reach = DoubleArray(pts.size) { Double.POSITIVE_INFINITY }
    val done = BooleanArray(pts.size)
    val order = mutableListOf<Int>()
    for (start in pts.indices) {
        if (done[start]) continue
        var p = start
        while (true) {
            done[p] = true
            order += p
            for (o in pts.indices) if (!done[o]) reach[o] = min(reach[o], max(core[p], dist(pts[p], pts[o])))
            val open = pts.indices.filter { !done[it] }
            if (open.isEmpty()) break
            p = open.minBy { reach[it] }
            if (reach[p].isInfinite()) break
        }
    }
    return CsOptics(order, order.map { reach[it] }, core)
}

private fun opticsLab(): CsLab {
    val cuts = ladderOf(0.4, 2.4, 0.2)
    val pts = densityData
    val o = optics(pts, 4)
    val (xr, yr) = boundsOf(pts, 0.8)
    return CsLab(
        control = CsControl.Button,
        param = CsParam("Cut height", "ε′", cuts, 3) { cx(it, 1) },
        button = { if (it.flag == 0) "Show Processing Order" else "Hide Processing Order" },
        onButton = { it.copy(flag = 1 - it.flag) },
        frames = { s ->
            val cut = cuts[s.param]
            val labels = IntArray(pts.size) { -1 }
            var c = -1
            o.order.forEachIndexed { i, p ->
                if (o.reach[i] > cut) {
                    if (o.core[p] <= cut) { c++; labels[p] = c } else labels[p] = -1
                } else labels[p] = c
            }
            val clusters = c + 1
            val dots = pts.indices.map { CsDot(pts[it], cColor(labels[it])) }
            val path = if (s.flag == 1) o.order.zipWithNext { a, b -> CsSeg(pts[a], pts[b], SimColors.Grey.copy(alpha = 0.8f), width = 1.2f) } else emptyList()
            val legend = (0 until clusters).map { Triple(cColor(it), SwatchStyle.Dot, "Cluster ${it + 1}") } + Triple(NoiseGrey, SwatchStyle.Dot, "Noise") +
                Triple(SimColors.Active, SwatchStyle.DashedLine, "Cut")
            listOf(
                CsFrame(
                    if (s.flag == 0) "Valleys are dense runs; {peaks} are the jumps between groups."
                    else "OPTICS always walks to the {nearest reachable} point next.",
                    if (s.flag == 0) "OPTICS orders points once. Any ε cut can then be read off without rerunning."
                    else "So each group is visited in one unbroken run, which is why the plot's bars form valleys.",
                    listOf(
                        CsPlot(dots, xr, yr, path),
                        CsBars(o.reach, o.order.map { cColor(labels[it]) }, cut = cut),
                        CsFormula(listOf("cut at ${cx(cut, 1)}: each valley below the line is a cluster → {v:$clusters}")),
                    ),
                    legend + (if (s.flag == 1) listOf(Triple(SimColors.Grey, SwatchStyle.Line, "Processing order")) else emptyList()),
                ),
            )
        },
    )
}

// ── Mean shift ──

private val shiftData: List<CsP> by lazy {
    val r = CsRng(97)
    blob(r, 9, 2.8, 3.0, 0.35, 0.3) + blob(r, 9, 4.2, 5.0, 0.3, 0.3) + blob(r, 10, 7.0, 3.3, 0.4, 0.25) +
        listOf(CsP(0.9, 2.6), CsP(3.6, 4.3), CsP(5.4, 1.5))
}

private fun shiftOnce(pts: List<CsP>, x: CsP, h: Double): CsP? {
    val inside = pts.filter { dist(it, x) <= h }
    return if (inside.isEmpty()) null else meanOf(inside)
}

private fun meanShiftLab(): CsLab {
    val hs = ladderOf(0.8, 2.0, 0.1)
    val pts = shiftData
    val (xr, yr) = boundsOf(pts, 0.7)
    val seed = CsP(3.9, 3.9)
    return CsLab(
        control = CsControl.Steps,
        param = CsParam("Bandwidth", "h", hs, 5) { cx(it, 1) },
        frames = { s ->
            val h = hs[s.param]
            val path = mutableListOf(seed)
            val frames = mutableListOf<CsFrame>()
            var x = seed
            for (it in 1..15) {
                val m = shiftOnce(pts, x, h) ?: break
                val shift = dist(x, m)
                val settled = shift < 0.005
                frames += CsFrame(
                    if (settled) "The shift is {${cx(shift)}}: the seed sits on a peak." else "The seed moves {${cx(shift)}} toward the mean of its window.",
                    when {
                        settled -> "Every seed that climbs to this peak joins the same cluster."
                        it == 1 -> "m(x) averages every point within h of the seed. The seed jumps there, then repeats."
                        else -> "Steps shrink as it nears a peak. Seeds that stop at the same peak form one cluster."
                    },
                    listOf(
                        CsPlot(
                            pts.map { CsDot(it, SimColors.Grey) } + path.dropLast(1).map { CsDot(it, SimColors.Answer, 2.5f) } +
                                CsDot(x, SimColors.Answer, 3.5f) + CsDot(m, SimColors.Active, 5f, top = true),
                            xr, yr,
                            path.zipWithNext { a, b -> CsSeg(a, b, SimColors.Answer, width = 2.5f) } + CsSeg(x, m, SimColors.Answer, dashed = true, width = 2f),
                            circles = listOf(CsCircle(x, h, SimColors.Active, fill = Color.Black.copy(alpha = 0.25f))),
                        ),
                        CsFormula(listOf("m(x) = Σ w·p / Σ w, bandwidth ${cx(h, 1)}", "(${cx(x.x)}, ${cx(x.y)}) → {v:(${cx(m.x)}, ${cx(m.y)})}")),
                    ),
                    listOf(Triple(SimColors.Answer, SwatchStyle.Line, "Path so far"), Triple(SimColors.Active, SwatchStyle.Dot, "Next position"), Triple(SimColors.Active, SwatchStyle.DashedLine, "Window")),
                    listOf(LabChip("shift", cx(shift, 3), tint = StoryTone.Active), LabChip("iteration", "$it")),
                    if (settled) "Shift Every Point" else "Shift Seed",
                )
                path += m
                x = m
                if (settled) break
            }
            // Every point climbs; peaks within h/2 of each other are one mode.
            val peaks = mutableListOf<CsP>()
            val labels = pts.map { p ->
                var y = p
                repeat(60) { y = shiftOnce(pts, y, h) ?: y }
                val known = peaks.indexOfFirst { dist(it, y) < h / 2 }
                if (known >= 0) known else { peaks += y; peaks.lastIndex }
            }
            frames += CsFrame(
                "Every point climbs to a peak: {${peaks.size} peaks}, ${peaks.size} clusters.",
                "Bandwidth h sets the scale. A smaller h finds more peaks; a larger one merges them.",
                listOf(
                    CsPlot(pts.indices.map { CsDot(pts[it], cColor(labels[it])) } + peaks.map { CsDot(it, SimColors.Active, 5f, ring = Color.White, ringR = 3f, top = true) }, xr, yr),
                    CsFormula(listOf("bandwidth ${cx(h, 1)} → {v:${peaks.size}} peaks")),
                ),
                clusterLegend(min(peaks.size, 5)) + Triple(SimColors.Active, SwatchStyle.Dot, "Peak"),
                listOf(LabChip("peaks", "${peaks.size}")),
                "Start Over",
            )
            frames
        },
    )
}

// ── BIRCH ──

private val birchData: List<CsP> by lazy {
    val r = CsRng(101)
    val clumps = listOf(CsP(3.4, 2.2), CsP(7.5, 2.2), CsP(0.9, 3.4), CsP(6.4, 6.8))
    val pts = clumps.map { c -> blob(r, 4, c.x, c.y, 0.6, 0.5) }
    (0 until 4).flatMap { i -> clumps.indices.map { pts[it][i] } }
}

private class CsCf(var n: Int, var lx: Double, var ly: Double, var ss: Double) {
    fun centroid() = CsP(lx / n, ly / n)
    fun radiusWith(p: CsP?): Double {
        val nn = n + (if (p == null) 0 else 1)
        val x = lx + (p?.x ?: 0.0)
        val y = ly + (p?.y ?: 0.0)
        val s = ss + (p?.let { it.x * it.x + it.y * it.y } ?: 0.0)
        return sqrt(max(s / nn - (x / nn) * (x / nn) - (y / nn) * (y / nn), 0.0))
    }
}

private fun birchLab(): CsLab {
    val ts = ladderOf(0.4, 1.6, 0.2)
    val pts = birchData
    val (xr, yr) = boundsOf(pts, 1.0)
    return CsLab(
        control = CsControl.Steps,
        param = CsParam("Threshold", "T", ts, 3) { cx(it, 1) },
        frames = { s ->
            val t = ts[s.param]
            val cfs = mutableListOf<CsCf>()
            val frames = mutableListOf<CsFrame>()
            fun table(target: Int) = CsTable(
                listOf("entry", "N", "LS", "SS"),
                cfs.mapIndexed { i, cf -> CsRow(listOf("CF${i + 1}", "${cf.n}", "(${cx(cf.lx, 1)}, ${cx(cf.ly, 1)})", cx(cf.ss, 1)), if (i == target) StoryTone.Active else null, if (i == target) StoryTone.Active else null) },
                listOf(0.8f, 0.5f, 1.5f, 0.9f),
            )
            fun circles(target: Int) = cfs.mapIndexed { i, cf -> CsCircle(cf.centroid(), max(cf.radiusWith(null), 0.15) + 0.2, if (i == target) SimColors.Active else SimColors.Answer) }
            pts.forEachIndexed { i, p ->
                val near = cfs.indices.minByOrNull { d2(cfs[it].centroid(), p) }
                val r = near?.let { cfs[it].radiusWith(p) }
                val absorb = r != null && r <= t
                val target: Int
                if (absorb) {
                    val cf = cfs[near!!]
                    cf.n++; cf.lx += p.x; cf.ly += p.y; cf.ss += p.x * p.x + p.y * p.y
                    target = near
                } else {
                    cfs += CsCf(1, p.x, p.y, p.x * p.x + p.y * p.y)
                    target = cfs.lastIndex
                }
                val seen = pts.take(i)
                frames += CsFrame(
                    when {
                        absorb -> "Point ${i + 1} {is absorbed} by CF${target + 1}. Only N, LS and SS change."
                        near == null -> "Point 1 starts {CF1}."
                        else -> "Point ${i + 1} is too far: it {starts CF${target + 1}}."
                    },
                    when {
                        near == null -> "A CF entry stores only N, LS and SS, enough to recover its centroid and radius."
                        absorb -> "Points are discarded, so BIRCH needs one pass."
                        else -> "A lower threshold T makes more, tighter entries."
                    },
                    listOf(
                        CsPlot(seen.map { CsDot(it, IdleDot, 3f) } + CsDot(p, SimColors.Active, 4.5f, top = true), xr, yr, circles = circles(target), aspect = 2.1f),
                        table(target),
                        CsFormula(listOf(when {
                            r == null -> "no entries yet → {v:new CF}"
                            absorb -> "new radius ${cx(r)} ≤ threshold ${cx(t, 1)} → {v:absorb}"
                            else -> "new radius ${cx(r)} > threshold ${cx(t, 1)} → {w:new CF}"
                        })),
                    ),
                    listOf(Triple(SimColors.Active, SwatchStyle.Dot, "Incoming"), Triple(SimColors.Answer, SwatchStyle.DashedLine, "CF radius")),
                    action = if (i < pts.lastIndex) "Insert Point ${i + 2}" else "Show Summary",
                )
            }
            frames += CsFrame(
                "${pts.size} points became {${cfs.size} CF entries}.",
                "A global step, such as agglomerative clustering on the CF centroids, turns entries into final clusters.",
                listOf(CsPlot(pts.map { CsDot(it, IdleDot, 3f) }, xr, yr, circles = circles(-1), aspect = 2.1f), table(-1),
                    CsFormula(listOf("threshold ${cx(t, 1)} → {v:${cfs.size}} entries"))),
                listOf(Triple(SimColors.Answer, SwatchStyle.DashedLine, "CF radius")),
                action = "Start Over",
            )
            frames
        },
    )
}

// ── Affinity propagation ──

private val affinityData: List<CsP> by lazy {
    val r = CsRng(103)
    blob(r, 6, 2.6, 3.0, 0.35, 0.25) + blob(r, 6, 4.8, 6.0, 0.3, 0.25) + blob(r, 6, 7.4, 2.8, 0.45, 0.45) + listOf(CsP(0.2, 3.2))
}

private const val FOCUS = 2

private fun affinityLab(): CsLab {
    val pts = affinityData
    val n = pts.size
    val sims = pts.indices.flatMap { i -> pts.indices.filter { it != i }.map { -d2(pts[i], pts[it]) } }
    val med = medianOf(sims)
    val prefs = listOf(4.0, 2.0, 1.0, 0.5, 0.25).map { med * it }
    val checkpoints = listOf(1, 2, 3, 5, 10, 20, 40, 80)
    val (xr, yr) = boundsOf(pts, 0.6)
    return CsLab(
        control = CsControl.Steps,
        param = CsParam("Preference", "p", prefs, 2) { cx(it, 1) },
        frames = { s ->
            val p = prefs[s.param]
            val sm = Array(n) { i -> DoubleArray(n) { k -> if (i == k) p else -d2(pts[i], pts[k]) } }
            val r = Array(n) { DoubleArray(n) }
            val a = Array(n) { DoubleArray(n) }
            val frames = mutableListOf<CsFrame>()
            for (round in 1..checkpoints.last()) {
                for (i in 0 until n) for (k in 0 until n) {
                    var m = -Double.MAX_VALUE
                    for (k2 in 0 until n) if (k2 != k) m = max(m, a[i][k2] + sm[i][k2])
                    r[i][k] = 0.5 * r[i][k] + 0.5 * (sm[i][k] - m)
                }
                for (i in 0 until n) for (k in 0 until n) {
                    var sum = 0.0
                    for (i2 in 0 until n) if (i2 != i && i2 != k) sum += max(0.0, r[i2][k])
                    val v = if (i == k) sum else min(0.0, r[k][k] + sum)
                    a[i][k] = 0.5 * a[i][k] + 0.5 * v
                }
                if (round !in checkpoints) continue
                val choice = IntArray(n) { i -> (0 until n).maxBy { a[i][it] + r[i][it] } }
                val exemplars = (0 until n).filter { choice[it] == it }
                val colourOf = { i: Int -> exemplars.indexOf(choice[i]).let { if (it < 0) -1 else it } }
                val top = (0 until n).sortedByDescending { a[FOCUS][it] + r[FOCUS][it] }.take(3)
                val pick = top[0]
                frames += CsFrame(
                    "Point ${FOCUS + 1} picks {point ${pick + 1}} as its exemplar: highest r + a.",
                    if (round == 1) "r says how well k suits i; a says how much support k has from others. They alternate until choices settle."
                    else "After $round rounds there ${if (exemplars.size == 1) "is 1 exemplar" else "are ${exemplars.size} exemplars"}. A higher preference p lets more points volunteer.",
                    listOf(
                        CsPlot(
                            pts.indices.map { i ->
                                CsDot(pts[i], cColor(colourOf(i)), 3.5f,
                                    ring = if (i == FOCUS) SimColors.Active else if (i in exemplars) Color.White else null,
                                    ringR = if (i == FOCUS) 7f else 4f, ringWidth = 2.5f)
                            },
                            xr, yr,
                            pts.indices.filter { choice[it] != it }.map { CsSeg(pts[it], pts[choice[it]], cColor(colourOf(it)).copy(alpha = 0.6f), width = 1f) },
                        ),
                        CsTable(
                            listOf("candidate k", "r(i,k)", "a(i,k)", "sum"),
                            top.mapIndexed { j, k -> CsRow(listOf("point ${k + 1}", cx(r[FOCUS][k]), cx(a[FOCUS][k]), cx(r[FOCUS][k] + a[FOCUS][k])), if (j == 0) StoryTone.Answer else null, if (j == 0) StoryTone.Answer else null) },
                            listOf(1.3f, 1f, 1f, 0.9f),
                        ),
                    ),
                    listOf(Triple(SimColors.Active, SwatchStyle.Ring, "Point ${FOCUS + 1} (i)"), Triple(Color.White, SwatchStyle.Ring, "Exemplar")),
                    listOf(LabChip("round", "$round"), LabChip("exemplars", "${exemplars.size}")),
                    if (round < checkpoints.last()) "Pass Messages" else "Start Over",
                )
            }
            frames
        },
    )
}

// ── Spectral clustering ──

private val moons: Pair<List<CsP>, List<Int>> by lazy {
    val r = CsRng(107)
    val outer = List(30) { val t = PI * it / 29; CsP(cos(t) + r.normal() * 0.05, sin(t) + r.normal() * 0.05) }
    val inner = List(30) { val t = PI * it / 29; CsP(1 - cos(t) + r.normal() * 0.05, 0.5 - sin(t) + r.normal() * 0.05) }
    (outer + inner) to (List(30) { 1 } + List(30) { 0 })
}

private class CsSpectral(val labels: IntArray, val vector: DoubleArray, val weights: Array<DoubleArray>)

private fun spectral(pts: List<CsP>, sigma: Double): CsSpectral {
    val n = pts.size
    val w = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 0.0 else exp(-d2(pts[i], pts[j]) / (2 * sigma * sigma)) } }
    val deg = DoubleArray(n) { i -> max(w[i].sum(), 1e-12) }
    val m = Array(n) { i -> DoubleArray(n) { j -> w[i][j] / sqrt(deg[i] * deg[j]) } }
    val u1 = DoubleArray(n) { sqrt(deg[it]) }.let { v -> val norm = sqrt(v.sumOf { it * it }); DoubleArray(n) { v[it] / norm } }
    var x = DoubleArray(n) { it.toDouble() / n - 0.5 }
    repeat(600) {
        val y = DoubleArray(n) { i -> x[i] + (0 until n).sumOf { j -> m[i][j] * x[j] } }
        val dot = (0 until n).sumOf { y[it] * u1[it] }
        for (i in 0 until n) y[i] -= dot * u1[i]
        val norm = sqrt(y.sumOf { it * it })
        x = DoubleArray(n) { y[it] / norm }
    }
    val v = DoubleArray(n) { x[it] / sqrt(deg[it]) }
    // Orient so the first point is on the positive side.
    val sign = if (v[0] < 0) -1.0 else 1.0
    val vs = DoubleArray(n) { v[it] * sign }
    return CsSpectral(IntArray(n) { if (vs[it] > 0) 1 else 0 }, vs, w)
}

private fun accuracy(labels: IntArray, truth: List<Int>): Double {
    val match = labels.indices.count { labels[it] == truth[it] }.toDouble() / labels.size
    return max(match, 1 - match)
}

private fun spectralLab(): CsLab {
    val sigmas = ladderOf(0.1, 0.8, 0.05)
    val (pts, truth) = moons
    val (km, kc) = lloyd(pts, 2, false)
    val kmAcc = accuracy(km, truth)
    val (xr, yr) = boundsOf(pts, 0.2)
    return CsLab(
        control = CsControl.Button,
        tabs = listOf("K-means", "Spectral"),
        startTab = 1,
        param = CsParam("Kernel width", "σ", sigmas, sigmas.indices.minBy { abs(sigmas[it] - 0.15) }) { cx(it) },
        button = { if (it.tab == 1) "Show K-means Cut" else "Show Spectral Cut" },
        onButton = { it.copy(tab = 1 - it.tab) },
        frames = { s ->
            val sg = sigmas[s.param]
            val sp = spectral(pts, sg)
            val acc = accuracy(sp.labels, truth)
            // Colour predictions so moon 1 (the upper one) is blue whenever the cut gets it right.
            fun colours(l: IntArray): List<Color> {
                val flip = l.indices.count { l[it] == truth[it] } < l.size / 2
                return l.map { v -> if ((v == 1) != flip) CBlue else COrange }
            }
            if (s.tab == 0) {
                val cols = colours(km)
                listOf(
                    CsFrame(
                        "K-means cuts the moons with a {straight line}.",
                        "It assumes round clusters, so accuracy is only ${cx(kmAcc)}; the spectral cut reaches ${cx(acc)} at σ = ${cx(sg)}.",
                        listOf(
                            CsPlot(pts.indices.map { CsDot(pts[it], cols[it]) } + kc.map { CsDot(it, Color.Transparent, 0f, ring = Color.White, ringR = 8f, ringWidth = 2.5f) }, xr, yr, aspect = 1.8f),
                            CsFormula(listOf("nearest of 2 centres → accuracy {v:${cx(kmAcc)}}")),
                        ),
                        listOf(Triple(Color.White, SwatchStyle.Ring, "Centre"), Triple(CBlue, SwatchStyle.Dot, "Moon 1"), Triple(COrange, SwatchStyle.Dot, "Moon 2")),
                    ),
                )
            } else {
                val cols = colours(sp.labels)
                val edges = mutableListOf<CsSeg>()
                for (i in pts.indices) for (j in i + 1 until pts.size) if (sp.weights[i][j] > 0.5) edges += CsSeg(pts[i], pts[j], SimColors.Grey.copy(alpha = 0.55f), width = 1f)
                val order = pts.indices.sortedBy { sp.vector[it] }
                val top = sp.vector.maxOf { abs(it) }
                val good = acc > 0.95
                listOf(
                    CsFrame(
                        if (good) "The graph's 2nd eigenvector {separates the moons} with one threshold."
                        else if (sg > 0.3) "At σ = ${cx(sg)} the graph links the moons, and the eigenvector {blurs} them."
                        else "At σ = ${cx(sg)} the graph falls apart, and the eigenvector {splits a moon}.",
                        if (good) "Each moon is one connected piece; k-means scores ${cx(kmAcc)}."
                        else "Accuracy drops to ${cx(acc)}. Too wide a kernel connects everything; too narrow cuts a moon into pieces.",
                        listOf(
                            CsPlot(pts.indices.map { CsDot(pts[it], cols[it]) }, xr, yr, edges, aspect = 1.8f),
                            CsPlot(order.mapIndexed { rank, i -> CsDot(CsP(rank.toDouble(), sp.vector[i] / top), cols[i], 2.8f) }, -1.0 to pts.size.toDouble(), -1.3 to 1.3, aspect = 7f, uniform = false, baseline = 0.0),
                            CsFormula(listOf("split the 2nd eigenvector at 0 → accuracy {v:${cx(acc)}}")),
                        ),
                        listOf(
                            Triple(SimColors.Grey, SwatchStyle.Line, "Affinity edge"), Triple(SimColors.Active, SwatchStyle.DashedLine, "Split at 0"),
                            Triple(CBlue, SwatchStyle.Dot, "Moon 1"), Triple(COrange, SwatchStyle.Dot, "Moon 2"),
                        ),
                    ),
                )
            }
        },
    )
}

// ── GMM ──

private val gmmData: List<CsP> by lazy {
    val r = CsRng(109)
    val angle = 50 * PI / 180
    val tilted = List(20) {
        val t = r.normal() * 0.9
        val q = r.normal() * 0.15
        CsP(2.0 + t * cos(angle) - q * sin(angle), 2.2 + t * sin(angle) + q * cos(angle))
    }
    tilted + blob(r, 16, 5.3, 3.6, 0.6, 0.18)
}

private class CsGauss(val mx: Double, val my: Double, val a: Double, val b: Double, val c: Double, val w: Double) {
    fun pdf(p: CsP): Double {
        val det = a * c - b * b
        val dx = p.x - mx
        val dy = p.y - my
        val q = (c * dx * dx - 2 * b * dx * dy + a * dy * dy) / det
        return exp(-q / 2) / (2 * PI * sqrt(det))
    }
    fun ellipse(color: Color): CsEllipse {
        val tr = (a + c) / 2
        val dd = sqrt(((a - c) / 2) * ((a - c) / 2) + b * b)
        val l1 = tr + dd
        val l2 = max(tr - dd, 1e-9)
        return CsEllipse(CsP(mx, my), 2 * sqrt(l1), 2 * sqrt(l2), 0.5 * atan2(2 * b, a - c), color)
    }
}

private fun emStep(pts: List<CsP>, g: List<CsGauss>): List<CsGauss> {
    val resp = pts.map { p -> val v = g.map { it.w * it.pdf(p) }; val t = v.sum(); v.map { it / t } }
    return g.indices.map { k ->
        val nk = resp.sumOf { it[k] }
        val mx = pts.indices.sumOf { resp[it][k] * pts[it].x } / nk
        val my = pts.indices.sumOf { resp[it][k] * pts[it].y } / nk
        val a = pts.indices.sumOf { resp[it][k] * (pts[it].x - mx) * (pts[it].x - mx) } / nk + 1e-4
        val b = pts.indices.sumOf { resp[it][k] * (pts[it].x - mx) * (pts[it].y - my) } / nk
        val c = pts.indices.sumOf { resp[it][k] * (pts[it].y - my) * (pts[it].y - my) } / nk + 1e-4
        CsGauss(mx, my, a, b, c, nk / pts.size)
    }
}

private fun gmmLab(): CsLab = CsLab(
    control = CsControl.Track,
    frames = {
        val pts = gmmData
        val (xr, yr) = boundsOf(pts, 0.9)
        var g = listOf(CsGauss(2.8, 3.0, 0.5, 0.0, 0.5, 0.5), CsGauss(4.4, 3.0, 0.5, 0.0, 0.5, 0.5))
        val rounds = listOf(0, 1, 2, 3, 4, 40)
        var done = 0
        rounds.mapIndexed { step, target ->
            while (done < target) { g = emStep(pts, g); done++ }
            val resp = pts.map { p -> val v = g.map { it.w * it.pdf(p) }; val t = v.sum(); v.map { it / t } }
            val loglik = pts.sumOf { p -> ln(g.sumOf { it.w * it.pdf(p) }) } / pts.size
            val unsure = pts.indices.minBy { abs(resp[it][0] - 0.5) }
            val gamma = resp[unsure][0]
            val dots = pts.indices.map { CsDot(pts[it], if (resp[it][0] >= 0.5) CBlue else COrange, ring = if (it == unsure) SimColors.Active else null, ringR = 4f) }
            val blue = cx(gamma * 100, 0)
            val (headline, body) = when {
                step == 0 -> "Two round components start {side by side}." to
                    "E-step: each point gets a responsibility per component. M-step: means, covariances and weights refit to them."
                step == rounds.lastIndex -> "Converged: the log-likelihood {stops rising} at ${cx(loglik, 3)} per point." to
                    "k-means is the special case with round, equal, hard-assigned components."
                target == 1 -> "One EM round: each component {moves toward} the points it claims." to
                    "The ringed point is $blue% blue: GMM keeps soft memberships where k-means would force a choice."
                else -> "After $target EM rounds the blue component has {tilted} to fit its long cluster." to
                    "The ringed point is $blue% blue: GMM keeps soft memberships where k-means would force a choice."
            }
            CsFrame(
                headline,
                body,
                listOf(
                    CsPlot(dots, xr, yr, ellipses = listOf(g[0].ellipse(CBlue), g[1].ellipse(COrange))),
                    CsFormula(listOf("γ(point) = π₁N₁ / (π₁N₁ + π₂N₂) = {v:${cx(gamma)}}")),
                ),
                listOf(Triple(CBlue, SwatchStyle.Line, "Component 1"), Triple(COrange, SwatchStyle.Line, "Component 2"), Triple(SimColors.Active, SwatchStyle.Ring, "Uncertain point")),
                listOf(LabChip("log-lik / pt", cx(loglik, 3), tint = StoryTone.Path), LabChip("weights", "${cx(g[0].w)} / ${cx(g[1].w)}")),
                if (step < rounds.lastIndex - 1) "Run EM Step" else if (step == rounds.lastIndex - 1) "Run to Convergence" else "Start Over",
            )
        }
    },
)

private fun clusterLab(topicId: String): CsLab = when (topicId) {
    "k_medians" -> kMediansLab()
    "k_modes" -> kModesLab()
    "hierarchical_clustering" -> aggLab()
    "hierarchical_divisive" -> divisiveLab()
    "dbscan" -> dbscanLab()
    "hdbscan" -> hdbscanLab()
    "optics" -> opticsLab()
    "mean_shift" -> meanShiftLab()
    "birch" -> birchLab()
    "affinity_propagation" -> affinityLab()
    "spectral_clustering" -> spectralLab()
    "gmm" -> gmmLab()
    else -> kmeansLab()
}

/** Builds every frame each control can reach (every tab, stepper value and press of the action). */
internal fun clusterStoryFrameCount(topicId: String): Int {
    val lab = clusterLab(topicId)
    var total = 0
    for (t in 0 until max(lab.tabs.size, 1)) for (p in 0 until (lab.param?.values?.size ?: 1)) {
        var st = CsState(t, p, lab.startFlag)
        repeat(if (lab.control == CsControl.Button) 6 else 1) {
            val frames = lab.frames(st)
            require(frames.isNotEmpty()) { "$topicId built no frames at $st" }
            frames.forEach { f ->
                require(f.headline.isNotBlank() && f.body.isNotBlank()) { "$topicId has no narration at $st" }
                require(f.blocks.isNotEmpty()) { "$topicId drew nothing at $st" }
                if (lab.control != CsControl.Button) require(f.action.isNotBlank()) { "$topicId has a step without an action at $st" }
                f.blocks.filterIsInstance<CsPlot>().forEach { plot ->
                    require(plot.dots.all { it.p.x.isFinite() && it.p.y.isFinite() }) { "$topicId drew a non-finite dot at $st" }
                }
            }
            total += frames.size
            st = lab.onButton(st)
        }
    }
    return total
}

// ── Lab ──

@Composable
internal fun ClusterStorySection(topicId: String) {
    val lab = remember(topicId) { clusterLab(topicId) }
    var state by remember(topicId) { mutableStateOf(lab.initial) }
    val frames = remember(state.tab, state.param, state.flag) { lab.frames(state) }
    val dock = LocalLabDock.current

    if (lab.control == CsControl.Track) {
        val playback = rememberPlaybackState(key = topicId to state.tab, stepCount = frames.size, initialSpeedMs = 1000f)
        Column(modifier = Modifier.fillMaxWidth()) {
            if (lab.tabs.isNotEmpty()) LabSegments(lab.tabs, state.tab, Modifier.padding(bottom = 14.dp)) { state = state.copy(tab = it) }
            CsBody(frames[playback.index.coerceIn(0, frames.lastIndex)])
            PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) }, action = { frames[it.coerceIn(0, frames.lastIndex)].action })
        }
        return
    }

    val index = state.index.coerceIn(0, frames.lastIndex)
    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            lab.param?.let { p ->
                LabParamStepper(LabParam(p.name, p.name, p.symbol, p.format(p.values[state.param]), state.param > 0, state.param < p.values.lastIndex)) { d ->
                    state = state.copy(param = (state.param + d).coerceIn(0, p.values.lastIndex), index = 0)
                }
            }
            if (lab.control == CsControl.Steps) {
                LabBackActionRow(
                    frames[index].action,
                    backEnabled = index > 0,
                    onBack = { state = state.copy(index = index - 1) },
                    onAction = { state = state.copy(index = if (index >= frames.lastIndex) 0 else index + 1) },
                )
            } else {
                lab.button?.let { label -> LabButton(label(state), primary = true, modifier = Modifier.fillMaxWidth()) { state = lab.onButton(state) } }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (lab.tabs.isNotEmpty()) LabSegments(lab.tabs, state.tab, Modifier.padding(bottom = 14.dp)) { state = state.copy(tab = it, index = 0) }
        CsBody(frames[index])
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") { state = lab.initial }
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

@Composable
private fun CsBody(frame: CsFrame) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            frame.blocks.forEach { block ->
                when (block) {
                    is CsPlot -> CsPlotView(block)
                    is CsBars -> CsBarsView(block)
                    is CsTable -> CsTableView(block)
                    is CsFormula -> CsFormulaView(block.lines)
                    is CsDendro -> CsDendroView(block)
                    is CsTree -> CsTreeView(block)
                    is CsModes -> CsModesView(block)
                }
            }
            if (frame.legend.isNotEmpty()) StoryLegendRow(frame.legend, Modifier.padding(top = 2.dp))
        }
    }
    LabChips(frame.chips, Modifier.padding(top = 16.dp))
    LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
}

// ── Rendering ──

private fun Modifier.csStage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

@Composable
private fun CsFormulaView(lines: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SimColors.Tint, RoundedCornerShape(10.dp)).padding(vertical = 10.dp, horizontal = 12.dp),
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
private fun CsPlotView(plot: CsPlot) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(plot.aspect).csStage()) {
        val pad = 14.dp.toPx()
        val (xLo, xHi) = plot.xr
        val (yLo, yHi) = plot.yr
        val sx = ((size.width - 2 * pad) / (xHi - xLo)).toFloat()
        val sy = ((size.height - 2 * pad) / (yHi - yLo)).toFloat()
        val s = min(sx, sy)
        val kx = if (plot.uniform) s else sx
        val ky = if (plot.uniform) s else sy
        val ox = size.width / 2 - ((xLo + xHi) / 2).toFloat() * kx
        val oy = size.height / 2 + ((yLo + yHi) / 2).toFloat() * ky
        fun at(p: CsP) = Offset(ox + p.x.toFloat() * kx, oy - p.y.toFloat() * ky)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        plot.baseline?.let { y -> drawLine(SimColors.Active, Offset(pad / 2, oy - y.toFloat() * ky), Offset(size.width - pad / 2, oy - y.toFloat() * ky), 1.5.dp.toPx(), pathEffect = dash) }
        plot.circles.forEach { c ->
            val r = (c.radius * kx).toFloat()
            c.fill?.let { drawCircle(it, r, at(c.c)) }
            drawCircle(c.color, r, at(c.c), style = Stroke(c.width.dp.toPx(), pathEffect = if (c.dashed) dash else null))
        }
        plot.ellipses.forEach { e ->
            val c = at(e.c)
            rotate(-(e.angle * 180 / PI).toFloat(), c) {
                val w = (e.a * kx).toFloat()
                val h = (e.b * ky).toFloat()
                drawOval(e.color.copy(alpha = 0.12f), Offset(c.x - w, c.y - h), Size(2 * w, 2 * h))
                drawOval(e.color, Offset(c.x - w, c.y - h), Size(2 * w, 2 * h), style = Stroke(2.dp.toPx()))
            }
        }
        plot.segs.forEach { sg ->
            drawLine(sg.color, at(sg.a), at(sg.b), sg.width.dp.toPx(), cap = if (sg.dashed) StrokeCap.Butt else StrokeCap.Round, pathEffect = if (sg.dashed) dash else null)
        }
        plot.dots.sortedBy { it.top }.forEach { d ->
            val c = at(d.p)
            if (d.r > 0) drawCircle(d.color, d.r.dp.toPx(), c)
            d.ring?.let { drawCircle(it, (d.r + d.ringR).dp.toPx(), c, style = Stroke(d.ringWidth.dp.toPx(), pathEffect = if (d.ringDashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null)) }
        }
        plot.labels.forEach { l ->
            val c = at(l.p)
            drawText(measurer, l.text, Offset(c.x + 5.dp.toPx(), c.y - 18.dp.toPx()), TextStyle(color = muted, fontSize = 11.sp))
        }
    }
}

@Composable
private fun CsBarsView(bars: CsBars) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.fillMaxWidth().height(96.dp).csStage()) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
            val finite = bars.values.filter { it.isFinite() }
            val top = max(finite.maxOrNull() ?: 1.0, bars.cut ?: 0.0) * 1.08
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (bars.values.size - 1)) / bars.values.size
            bars.values.forEachIndexed { i, v ->
                val h = if (v.isFinite()) (v / top).toFloat() * size.height else size.height
                val x = i * (w + gap)
                drawRect(bars.colors[i], Offset(x, size.height - max(h, 2.dp.toPx())), Size(w, max(h, 2.dp.toPx())))
            }
            bars.cut?.let { c ->
                val y = size.height - (c / top).toFloat() * size.height
                drawLine(SimColors.Active, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            }
        }
        bars.caption?.let { Text(it, fontSize = 11.sp, color = muted, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 2.dp)) }
    }
}

@Composable
private fun CsTableView(table: CsTable) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    fun align(i: Int) = if (i == 0) TextAlign.Start else TextAlign.End
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            table.header.forEachIndexed { i, h -> Text(h, fontSize = 13.sp, color = muted, textAlign = align(i), maxLines = 1, modifier = Modifier.weight(table.weights[i])) }
        }
        table.rows.forEach { row ->
            val ink = when {
                row.muted -> muted
                row.ink != null -> row.ink.ink()
                else -> onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        when (row.fill) {
                            null -> Color.Transparent
                            StoryTone.Answer -> SimColors.Answer.copy(alpha = 0.28f)
                            else -> row.fill.color().copy(alpha = 0.18f)
                        },
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.cells.forEachIndexed { i, cell ->
                    Text(
                        cell,
                        fontFamily = if (i == 0) null else IBMPlexMono,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (i == 0) 16.sp else 15.sp,
                        color = ink,
                        textAlign = align(i),
                        maxLines = 1,
                        modifier = Modifier.weight(table.weights[i]),
                    )
                }
            }
        }
    }
}

@Composable
private fun CsDendroView(d: CsDendro) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp).csStage()) {
        val n = d.leafCount
        val children = HashMap<Int, Pair<Int, Int>>()
        d.merges.forEachIndexed { i, m -> children[n + i] = m.a to m.b }
        val order = mutableListOf<Int>()
        fun visit(node: Int) { if (node < n) order += node else children.getValue(node).let { visit(it.first); visit(it.second) } }
        visit(n + d.merges.lastIndex)
        val pad = 16.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val top = 10.dp.toPx()
        val slot = (size.width - 2 * pad) / (n - 1)
        val maxH = d.merges.maxOf { it.height }
        val xs = HashMap<Int, Float>()
        val hs = HashMap<Int, Float>()
        order.forEachIndexed { i, leaf -> xs[leaf] = pad + i * slot; hs[leaf] = bottom }
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        d.merges.forEachIndexed { i, m ->
            val y = bottom - (m.height / maxH).toFloat() * (bottom - top)
            val xa = xs.getValue(m.a)
            val xb = xs.getValue(m.b)
            val color = when (m.state) {
                CsLinkState.Merged -> SimColors.Answer
                CsLinkState.Now -> SimColors.Active
                CsLinkState.Later -> SimColors.Grey.copy(alpha = 0.55f)
            }
            val effect = if (m.state == CsLinkState.Now) dash else null
            val width = if (m.state == CsLinkState.Later) 1.2.dp.toPx() else 2.5.dp.toPx()
            drawLine(color, Offset(xa, hs.getValue(m.a)), Offset(xa, y), width, pathEffect = effect)
            drawLine(color, Offset(xb, hs.getValue(m.b)), Offset(xb, y), width, pathEffect = effect)
            drawLine(color, Offset(xa, y), Offset(xb, y), width, pathEffect = effect)
            xs[n + i] = (xa + xb) / 2
            hs[n + i] = y
        }
        order.forEachIndexed { i, leaf ->
            val layout = measurer.measure(d.leafNames[leaf], TextStyle(color = muted, fontSize = 11.sp, fontFamily = IBMPlexMono))
            drawText(layout, topLeft = Offset(pad + i * slot - layout.size.width / 2, bottom + 4.dp.toPx()))
        }
    }
}

@Composable
private fun CsTreeView(tree: CsTree) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val depth = IntArray(tree.nodes.size)
    tree.nodes.forEachIndexed { i, n -> if (n.parent >= 0) depth[i] = depth[n.parent] + 1 }
    val kids = tree.nodes.indices.map { i -> tree.nodes.indices.filter { tree.nodes[it].parent == i } }
    // Leaves take equal slots left to right; a parent sits over its children.
    val leafOrder = mutableListOf<Int>()
    fun visit(i: Int) { if (kids[i].isEmpty()) leafOrder += i else kids[i].forEach(::visit) }
    visit(0)
    val span = IntArray(tree.nodes.size)
    fun count(i: Int): Int = (if (kids[i].isEmpty()) 1 else kids[i].sumOf { count(it) }).also { span[i] = it }
    count(0)
    val rows = depth.max() + 1
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height((rows * 52 + 8).dp).csStage()) {
        val width = maxWidth
        val slot = width / leafOrder.size
        val xs = FloatArray(tree.nodes.size)
        fun place(i: Int): Float {
            xs[i] = if (kids[i].isEmpty()) (leafOrder.indexOf(i) + 0.5f) * slot.value else kids[i].map { place(it) }.average().toFloat()
            return xs[i]
        }
        place(0)
        Canvas(modifier = Modifier.fillMaxSize()) {
            tree.nodes.forEachIndexed { i, n ->
                if (n.parent < 0) return@forEachIndexed
                val from = Offset(xs[n.parent].dp.toPx(), (depth[n.parent] * 52 + 30).dp.toPx())
                val to = Offset(xs[i].dp.toPx(), (depth[i] * 52 + 12).dp.toPx())
                drawLine(
                    if (n.dashed) SimColors.Active else SimColors.Grey.copy(alpha = 0.6f), from, to, 1.2.dp.toPx(),
                    pathEffect = if (n.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                )
            }
        }
        tree.nodes.forEachIndexed { i, n ->
            val room = span[i] * slot.value
            val text = if (room >= n.full.length * 8.2f + 22) n.full else n.short
            val w = text.length * 8.2f + 18
            val (fill, ink, edge) = when (n.tone) {
                StoryTone.Active -> Triple(SimColors.Active.copy(alpha = 0.16f), StoryTone.Active.ink(), SimColors.Active)
                StoryTone.Done -> Triple(SimColors.Green.copy(alpha = 0.14f), StoryTone.Done.ink(), SimColors.Green)
                else -> Triple(SimColors.Tint, onSurface, SimColors.Grey.copy(alpha = 0.5f))
            }
            Box(
                modifier = Modifier
                    .offset(x = (xs[i] - w / 2).dp, y = (depth[i] * 52 + 8).dp)
                    .width(w.dp)
                    .height(28.dp)
                    .background(fill, RoundedCornerShape(7.dp))
                    .border(1.5.dp, edge, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CsModesView(m: CsModes) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val centreInk = StoryTone.Answer.ink()
    val activeInk = StoryTone.Active.ink()
    val warnInk = StoryTone.Warn.ink()
    val weights = listOf(0.5f, 1f, 1f, 1f, 1f, 0.9f)
    @Composable
    fun line(name: String, cells: List<String>, score: String, color: (Int) -> Color, bold: Boolean, underline: Set<Int> = emptySet(), nameColor: Color = muted) {
        Row(modifier = Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontFamily = IBMPlexMono, fontSize = 13.sp, color = nameColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(weights[0]))
            cells.forEachIndexed { i, c ->
                Text(
                    c,
                    fontSize = 14.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    color = color(i),
                    textDecoration = if (i in underline) TextDecoration.Underline else null,
                    maxLines = 1,
                    modifier = Modifier.weight(weights[i + 1]),
                )
            }
            Text(score, fontFamily = IBMPlexMono, fontSize = 13.sp, color = nameColor, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(weights[5]))
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        line("", m.header, "c1 · c2", { muted }, false)
        m.centres.forEachIndexed { c, cells ->
            line("c${c + 1}", cells, "", { i -> if ((c to i) in m.changed) activeInk else centreInk }, true, nameColor = centreInk)
        }
        m.rows.forEach { row ->
            val current = row.state == CsRowState.Current
            val color: (Int) -> Color = { i ->
                when {
                    i in row.mismatch -> warnInk
                    current -> activeInk
                    row.state == CsRowState.Future -> muted.copy(alpha = 0.6f)
                    else -> onSurface
                }
            }
            line(row.name, row.cells, row.score, color, current, row.mismatch, if (current) activeInk else muted)
        }
    }
}

