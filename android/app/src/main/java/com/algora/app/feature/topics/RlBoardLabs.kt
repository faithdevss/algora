package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ── Reinforcement-learning storyboards ───────────────────────────────────────
// Fifty-five RL labs (agent & environment to Dota 2) as one card of stacked blocks — a caption, chips,
// labelled bars, value grids, columns of bars, a scene of dots or key/value stats — over a legend, the
// step's arithmetic in a formula box and a headline. The dock is a picker over the play transport; the
// picker re-runs the same steps on another setting. Colours are the design's own strings so the frames
// read like the mock; the dark-only neutrals are mapped onto the theme when drawn. Frames:
// RlBasicsFrames.kt (foundations, tabular), RlDeepQFrames.kt (DQN family), RlModelFrames.kt
// (model-based), RlExploreFrames.kt (exploration, multi-agent), RlFrontierFrames.kt (imitation, offline,
// meta) and RlBenchFrames.kt (benchmarks); the iOS port mirrors each number for number.

internal val rlBoardTopicIds: Set<String> =
    rlBasicsTopicIds + rlDeepQTopicIds + rlModelTopicIds + rlExploreTopicIds + rlFrontierTopicIds + rlBenchTopicIds

internal fun rlBoardLab(topicId: String): RbLab =
    rlBasicsLab(topicId) ?: rlDeepQLab(topicId) ?: rlModelLab(topicId) ?: rlExploreLab(topicId)
        ?: rlFrontierLab(topicId) ?: rlBenchLab(topicId) ?: error("No RL storyboard for $topicId")

/** A chip: text, an optional mono line under it, its fill, text colour and ring. */
internal class RbTok(val t: String, val sub: String, val bg: String, val color: String, val ring: String = "none")

/** A labelled bar over [from, from + width] of the track (fractions); [mid] draws a zero line at the centre. */
internal class RbRow(
    val w: String,
    val v: String,
    val from: Double,
    val width: Double,
    var bar: String,
    val wc: String,
    val mid: Boolean,
)

/** One bar of a column, [h] in points of the 62pt column. */
internal class RbVBar(val h: Double, val bg: String)

internal class RbVCol(val v: String, val label: String, val lc: String, val bars: List<RbVBar>)

internal class RbCell(val t: String, val bg: String, val color: String, val ring: String = "none")

internal class RbGRow(val label: String, val lc: String, val cells: List<RbCell>)

/** A dot at [x], [y] percent of the scene from the top left, [sz] points across. */
internal class RbPt(val x: Double, val y: Double, val sz: Double, val bg: String, val ring: String = "none")

internal class RbStat(val k: String, val v: String, val c: String)

internal sealed interface RbBlock {
    class Label(val text: String) : RbBlock
    class Chips(val items: List<RbTok>) : RbBlock
    class Bars(val rows: List<RbRow>) : RbBlock
    class Cols(val cols: List<RbVCol>) : RbBlock
    class Grid(val ch: Int, val cols: List<String>, val rows: List<RbGRow>) : RbBlock
    class Plot(val h: Int, val pts: List<RbPt>) : RbBlock
    class Stats(val rows: List<RbStat>) : RbBlock
}

/** A formula line: [a] muted, then [b] bold in [c] (the line's result). */
internal class RbFx(val a: String, val b: String, val c: String)

internal class RbFrame(
    val blocks: List<RbBlock>,
    val fx: List<RbFx>,
    val capT: String,
    val capB: String,
    val legend: List<Pair<String, String>>,
)

/** [frames] builds every step for one picker option; the step count may differ between options. */
internal class RbLab(val tabs: List<String>, val initialTab: Int, val frames: (opt: Int) -> List<RbFrame>)

/** Builds every frame of every option. */
internal fun rlBoardFrameCount(topicId: String): Int {
    val lab = rlBoardLab(topicId)
    var total = 0
    for (t in 0 until max(lab.tabs.size, 1)) {
        val frames = lab.frames(t)
        require(frames.isNotEmpty()) { "$topicId built no frames at option $t" }
        frames.forEachIndexed { i, f ->
            require(f.capT.isNotBlank() && f.capB.isNotBlank()) { "$topicId has an empty caption at option $t, step $i" }
            f.blocks.forEach { b ->
                when (b) {
                    is RbBlock.Bars -> b.rows.forEach { require(it.from.isFinite() && it.width.isFinite()) { "$topicId drew a non-finite bar at option $t, step $i" } }
                    is RbBlock.Plot -> b.pts.forEach { require(it.x.isFinite() && it.y.isFinite()) { "$topicId drew a non-finite point at option $t, step $i" } }
                    is RbBlock.Cols -> b.cols.forEach { c -> c.bars.forEach { require(it.h.isFinite()) { "$topicId drew a non-finite column at option $t, step $i" } } }
                    else -> Unit
                }
            }
        }
        total += frames.size
    }
    return total
}

// ── The design's helpers, shared by the frame files ──

internal object Rb {
    /**
     * JavaScript's `toFixed` for a non-negative number: the exact binary value rounded half up, so
     * 1.805 (stored as 1.80499…) prints 1.80 and 0.125 prints 0.13, as the design does.
     */
    fun fixed(x: Double, d: Int): String = java.math.BigDecimal(x).setScale(d, java.math.RoundingMode.HALF_UP).toPlainString()

    /** The design's f(): fixed decimals with a real minus sign; a value that rounds to zero prints unsigned. */
    fun f(x: Double, d: Int = 2): String {
        val v = if (abs(x) < 0.5 * Math.pow(10.0, -d.toDouble())) 0.0 else x
        return (if (v < 0) "−" else "") + fixed(abs(v), d)
    }

    fun pct(p: Double): String = if (p == 0.0) "0%" else fixed(p * 100, if (p < 0.1) 1 else 0) + "%"

    fun num(x: Double): String = lsNum(x)

    /** A number as JavaScript prints it: 0.1, 2, 0.25. */
    fun js(x: Double): String = lsBetaText(x)

    fun L(text: String) = RbBlock.Label(text)
    fun C(items: List<RbTok>) = RbBlock.Chips(items)
    fun B(rows: List<RbRow>) = RbBlock.Bars(rows)
    fun S(vararg rows: Triple<String, String, String?>) = RbBlock.Stats(rows.map { RbStat(it.first, it.second, it.third ?: "#f2f3f7") })
    fun S(rows: List<Triple<String, String, String?>>) = RbBlock.Stats(rows.map { RbStat(it.first, it.second, it.third ?: "#f2f3f7") })
    fun st(k: String, v: String, c: String? = null) = Triple(k, v, c)
    fun F(a: String, b: String = "", c: String? = null) = RbFx(a, b, c ?: "#f2f3f7")

    fun tok(t: String, k: String = "plain", sub: String = ""): RbTok {
        val (bg, color) = when (k) {
            "cur", "mask" -> "#f5c542" to "#1b1d24"
            "done" -> "rgba(34,160,107,.18)" to "#5fd49b"
            "fut" -> "#1f232d" to "#6b7180"
            "ans" -> "#6d5dfc" to "#fff"
            "err" -> "rgba(229,72,77,.2)" to "#ff8a8d"
            else -> "#2a2e39" to "#f2f3f7"
        }
        return RbTok(t, sub, bg, color)
    }

    fun row(w: String, v: String, frac: Double, bar: String, wc: String? = null) =
        RbRow(w, v, 0.0, max(0.0, min(frac, 1.0)), bar, wc ?: "#c3c7d1", false)

    fun brow(w: String, x: Double, sc: Double, v: String? = null, wc: String? = null): RbRow {
        val a = min(abs(x) / sc, 1.0) * 0.5
        return RbRow(w, v ?: f(x), if (x >= 0) 0.5 else 0.5 - a, a, if (x >= 0) "#3b82f6" else "#e5337a", wc ?: "#c3c7d1", true)
    }

    fun softmax(z: List<Double>): List<Double> {
        val m = z.max()
        val e = z.map { kotlin.math.exp(it - m) }
        val s = e.sum()
        return e.map { it / s }
    }

    /** The design's `rng(seed)`: Park–Miller, `x = x·16807 mod (2³¹ − 1)`. */
    fun rng(seed: Long): () -> Double {
        var x = seed
        return {
            x = (x * 16807L) % 2147483647L
            x / 2147483647.0
        }
    }

    /** A Box–Muller normal drawn from [r], as the design's `g()`. */
    fun gauss(r: () -> Double): Double {
        val u = r().let { if (it == 0.0) 1e-9 else it }
        val v = r()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    /** `toFixed(2)` as the alpha in an rgba() string. */
    fun a2(x: Double): String = fixed(x, 2)

    /** JavaScript's `Math.round`: halves round up. */
    fun round(x: Double): Double = floor(x + 0.5)

    /** The index of the first maximum, as `q.indexOf(Math.max(...q))`. */
    fun argmax(q: List<Double>): Int {
        var b = 0
        for (i in 1 until q.size) if (q[i] > q[b]) b = i
        return b
    }

    fun argmax(q: DoubleArray): Int {
        var b = 0
        for (i in 1 until q.size) if (q[i] > q[b]) b = i
        return b
    }

    fun frame(blocks: List<RbBlock>, fx: List<RbFx>, cap: Pair<String, String>, legend: List<Pair<String, String>>) =
        RbFrame(blocks, fx, cap.first, cap.second, legend)

    /** The dots of a scene, from the design's `[x, y, size, colour, ring]` tuples. */
    fun scene(h: Int, pts: List<RbPt>) = RbBlock.Plot(h, pts)

    fun pt(x: Double, y: Double, sz: Double = 4.0, bg: String = "#3a3f4c", ring: String = "none") = RbPt(x, y, sz, bg, ring)

    fun dline(x0: Double, y0: Double, x1: Double, y1: Double, n: Int, sz: Double, bg: String): List<RbPt> =
        (0..n).map { i -> RbPt(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, sz, bg) }

    class Series(val vals: List<Double>, val c: String, val sz: Double = 4.0)

    /** A line chart drawn as dots: a baseline, dashed reference levels, then each series. */
    fun chart(series: List<Series>, hp: Int, lo: Double, hi: Double, refs: List<Pair<Double, String>> = emptyList()): RbBlock.Plot {
        val pts = ArrayList<RbPt>()
        for (i in 0..40) pts += RbPt(6 + 88.0 * i / 40, 92.0, 2.0, "#3a3f4c")
        refs.forEach { (v, c) -> for (i in 0..40 step 2) pts += RbPt(6 + 88.0 * i / 40, 92 - 82 * (v - lo) / (hi - lo), 2.0, c) }
        series.forEach { s ->
            val n = s.vals.size
            s.vals.forEachIndexed { i, v ->
                pts += RbPt(6 + 88.0 * i / max(1, n - 1), 92 - 82 * (max(lo, min(hi, v)) - lo) / (hi - lo), s.sz, s.c)
            }
        }
        return RbBlock.Plot(hp, pts)
    }
}

// ── The shared 4×4 grid world: goal (0,3) +1, pit (1,3) −1, wall (1,1), start (3,0) ──

internal object RbGw {
    val AC = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
    val AR = listOf("↑", "↓", "←", "→")
    const val GOAL = "0,3"
    const val PIT = "1,3"
    const val WALL = "1,1"
    val START = 3 to 0

    fun key(s: Pair<Int, Int>) = "${s.first},${s.second}"

    fun term(s: Pair<Int, Int>): Boolean = key(s).let { it == GOAL || it == PIT }

    fun free(s: Pair<Int, Int>) = s.first in 0..3 && s.second in 0..3 && key(s) != WALL

    class Mv(val ns: Pair<Int, Int>, val r: Double)

    fun mv(s: Pair<Int, Int>, a: Int, c: Double): Mv {
        val n = (s.first + AC[a][0]) to (s.second + AC[a][1])
        val ns = if (free(n)) n else s
        val k = key(ns)
        return Mv(ns, if (k == GOAL) 1.0 else if (k == PIT) -1.0 else c)
    }

    val states: List<Pair<Int, Int>> = buildList {
        for (r in 0..3) for (c in 0..3) {
            val s = r to c
            if (free(s) && !term(s)) add(s)
        }
    }

    /** A policy: for a state, the (action, probability) pairs. */
    fun interface Pi {
        fun of(s: Pair<Int, Int>): List<Pair<Int, Double>>
    }

    fun evalPi(pi: Pi, g: Double, c: Double, sw: Int): Map<String, Double> {
        var v = HashMap<String, Double>()
        repeat(sw) {
            val n = HashMap<String, Double>()
            states.forEach { s ->
                n[key(s)] = pi.of(s).fold(0.0) { a, (ac, p) ->
                    val m = mv(s, ac, c)
                    a + p * (m.r + if (term(m.ns)) 0.0 else g * (v[key(m.ns)] ?: 0.0))
                }
            }
            v = n
        }
        return v
    }

    class Vi(val V: Map<String, Double>, val P: Map<String, Int>)

    private val viCache = HashMap<Pair<Double, Double>, Vi>()

    fun vi(g: Double, c: Double): Vi = synchronized(viCache) {
        viCache.getOrPut(g to c) {
            var v = HashMap<String, Double>()
            repeat(300) {
                val n = HashMap<String, Double>()
                states.forEach { s ->
                    n[key(s)] = (0..3).maxOf { a ->
                        val m = mv(s, a, c)
                        m.r + if (term(m.ns)) 0.0 else g * (v[key(m.ns)] ?: 0.0)
                    }
                }
                v = n
            }
            val p = HashMap<String, Int>()
            states.forEach { s ->
                var b = 0
                var bv = -1e9
                for (a in 0..3) {
                    val m = mv(s, a, c)
                    val x = m.r + if (term(m.ns)) 0.0 else g * (v[key(m.ns)] ?: 0.0)
                    if (x > bv + 1e-9) { bv = x; b = a }
                }
                p[key(s)] = b
            }
            Vi(v, p)
        }
    }

    fun vcol(v: Double): String {
        val a = min(0.15 + 0.75 * abs(v), 0.9)
        return if (v >= 0) "rgba(59,130,246,${Rb.js(a)})" else "rgba(229,72,77,${Rb.js(a)})"
    }

    fun grid(ch: Int = 44, fn: (Pair<Int, Int>, String) -> RbCell): RbBlock.Grid = RbBlock.Grid(
        ch, listOf("c0", "c1", "c2", "c3"),
        (0..3).map { r ->
            RbGRow("r$r", "#9aa0ae", (0..3).map { c ->
                val k = "$r,$c"
                when (k) {
                    WALL -> RbCell("", "#3a3f4c", "")
                    GOAL -> RbCell("+1", "#22a06b", "#fff")
                    PIT -> RbCell("−1", "#e5484d", "#fff")
                    else -> fn(r to c, k)
                }
            })
        },
    )

    fun plainCell(t: String = "", ring: String = "none") = RbCell(t, "#1f232d", "#c3c7d1", ring)

    fun pol(name: String): Pi = when (name) {
        "right, then up" -> Pi { s -> listOf((if (free(s.first to s.second + 1)) 3 else 0) to 1.0) }
        "up, then right" -> Pi { s -> listOf((if (free(s.first - 1 to s.second)) 0 else 3) to 1.0) }
        else -> Pi { listOf(0 to .25, 1 to .25, 2 to .25, 3 to .25) }
    }

    fun perp(a: Int) = if (a < 2) intArrayOf(2, 3) else intArrayOf(0, 1)

    fun evalSlip(pi: Pi, slip: Double, sw: Int = 300): Map<String, Double> {
        var v = HashMap<String, Double>()
        repeat(sw) {
            val n = HashMap<String, Double>()
            states.forEach { s ->
                val a = pi.of(s)[0].first
                val outs = listOf(a to 1 - slip, perp(a)[0] to slip / 2, perp(a)[1] to slip / 2)
                n[key(s)] = outs.fold(0.0) { acc, (b, p) ->
                    val m = mv(s, b, -0.04)
                    acc + p * (m.r + if (term(m.ns)) 0.0 else .9 * (v[key(m.ns)] ?: 0.0))
                }
            }
            v = n
        }
        return v
    }

    class Backup(val a: Int, val ns: Pair<Int, Int>, val r: Double, val q: Double)

    fun backups(v: Map<String, Double>, s: Pair<Int, Int>): List<Backup> = (0..3).map { a ->
        val m = mv(s, a, -0.04)
        Backup(a, m.ns, m.r, m.r + if (term(m.ns)) 0.0 else .9 * (v[key(m.ns)] ?: 0.0))
    }

    fun vgrid(v: Map<String, Double>, p: Map<String, Int>?, hl: String? = null): RbBlock.Grid = grid { _, k ->
        val x = v[k] ?: 0.0
        val f2 = (if (x < -0.005) "−" else "") + Rb.fixed(abs(x), 2)
        RbCell(
            f2 + (if (p != null) " " + AR[p.getValue(k)] else ""),
            if (abs(x) < 1e-9) "#1f232d" else vcol(x), "#fff",
            if (k == hl) "inset 0 0 0 2px #f5c542" else "none",
        )
    }

    fun greedy(v: Map<String, Double>): Map<String, Int> {
        val p = LinkedHashMap<String, Int>()
        states.forEach { s ->
            val b = backups(v, s)
            var bi = 0
            b.forEachIndexed { i, x -> if (x.q > b[bi].q + 1e-9) bi = i }
            p[key(s)] = bi
        }
        return p
    }

    fun show(s: Pair<Int, Int>) = "${s.first},${s.second}"
}

// ── Lab ──

@Composable
internal fun RlBoardSection(topicId: String) {
    val lab = remember(topicId) { rlBoardLab(topicId) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(lab.initialTab) }
    // Several labs train or search for real, so each option's frames are built off the main thread.
    val built by produceState<List<RbFrame>?>(null, topicId, tab) {
        value = withContext(Dispatchers.Default) { lab.frames(tab) }
    }
    val frames = built
    // The step count can differ between options; the transport restarts only when it does.
    val playback = rememberPlaybackState(key = topicId to (frames?.size ?: 0), stepCount = frames?.size ?: 1, initialSpeedMs = 1000f)
    val dock = LocalLabDock.current

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (lab.tabs.isNotEmpty()) LabSegments(lab.tabs, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
            LabTransportBar(playback, frames?.map { storyPlain(it.capT) })
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (frames == null) {
            Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        } else {
            RbBody(frames[playback.index.coerceIn(0, frames.lastIndex)])
        }
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") {
                tab = lab.initialTab
                playback.reset()
            }
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
private fun RbBody(frame: RbFrame) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            frame.blocks.forEach { block ->
                when (block) {
                    is RbBlock.Label -> Text(block.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is RbBlock.Chips -> RbChipsView(block.items)
                    is RbBlock.Bars -> RbBarsView(block.rows)
                    is RbBlock.Cols -> RbColsView(block.cols)
                    is RbBlock.Grid -> RbGridView(block)
                    is RbBlock.Plot -> RbPlotView(block)
                    is RbBlock.Stats -> RbStatsView(block.rows)
                }
            }
            if (frame.legend.isNotEmpty()) StoryLegendRow(frame.legend.map { Triple(rbColor(it.first), SwatchStyle.Fill, it.second) })
        }
    }
    if (frame.fx.isNotEmpty()) RbFormula(frame.fx, Modifier.padding(top = 12.dp))
    // The captions are plain text; a brace in one is literal, not a colour mark.
    LabStoryNarration(frame.capT.replace("{", "\\{"), frame.capB.replace("{", "\\{"), Modifier.padding(top = 16.dp))
}

@Composable
private fun RbFormula(lines: List<RbFx>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { l ->
            val ink = rbColor(l.c)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = muted)) { append(l.a) }
                    if (l.b.isNotEmpty()) {
                        append(" ")
                        withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(l.b) }
                    }
                },
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                lineHeight = 19.5.sp,
            )
        }
    }
}

// ── Colours ──

private class RbRgba(val r: Int, val g: Int, val b: Int, val a: Float)

private val rbParsed = HashMap<String, RbRgba?>()

private fun rbParse(s: String): RbRgba? = synchronized(rbParsed) {
    rbParsed.getOrPut(s) {
        val t = s.trim()
        when {
            t.startsWith("#") && t.length == 4 -> {
                val h = t.substring(1).map { "$it$it".toInt(16) }
                RbRgba(h[0], h[1], h[2], 1f)
            }
            t.startsWith("#") && t.length == 7 -> RbRgba(t.substring(1, 3).toInt(16), t.substring(3, 5).toInt(16), t.substring(5, 7).toInt(16), 1f)
            t.startsWith("rgba(") -> {
                val p = t.removePrefix("rgba(").removeSuffix(")").split(",").map { it.trim() }
                RbRgba(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toFloat())
            }
            else -> null
        }
    }
}

/** The design's colour, with its dark-only neutrals mapped onto the theme. */
@Composable
internal fun rbColor(s: String): Color {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (s) {
        "", "none", "transparent" -> Color.Transparent
        "#1f232d", "#2a2e39" -> SimColors.Tint
        "#14171f" -> Color.Black.copy(alpha = 0.16f)
        "#f2f3f7" -> onSurface
        "#c3c7d1" -> onSurface.copy(alpha = 0.8f)
        "#9aa0ae" -> muted
        "#6b7180" -> muted.copy(alpha = 0.7f)
        "#3a3f4c", "#4a4f5c" -> Color(0xFF6B7180).copy(alpha = 0.55f)
        else -> rbParse(s)?.let { Color(it.r, it.g, it.b, (it.a * 255).toInt()) } ?: onSurface
    }
}

/** Text on a fill: white on a strong fill, the theme's text colour on a pale or neutral one. */
@Composable
private fun rbTextOn(bg: String, color: String): Color {
    if (color != "#fff") return rbColor(color)
    val p = rbParse(bg)
    val neutral = bg == "#1f232d" || bg == "#2a2e39" || bg == "#14171f" || bg == "transparent" || bg.isEmpty()
    return if (neutral || (p != null && p.a < 0.45f)) MaterialTheme.colorScheme.onSurface else Color.White
}

/** A ring: `inset 0 0 0 2px #f5c542` or `0 0 0 2px #fff`, drawn as a border; null for none. */
@Composable
private fun rbRing(ring: String): Pair<Float, Color>? {
    if (ring == "none" || ring.isEmpty()) return null
    val parts = ring.split(" ").filter { it.isNotEmpty() }
    val w = parts.firstOrNull { it.endsWith("px") && it != "0px" }?.removeSuffix("px")?.toFloatOrNull() ?: return null
    return w to rbColor(parts.last())
}

private val rbTrack @Composable get() = Color.Black.copy(alpha = 0.16f)

// ── Blocks ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RbChipsView(items: List<RbTok>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { t ->
            val fg = rbTextOn(t.bg, t.color)
            val ring = rbRing(t.ring)
            Column(
                modifier = Modifier
                    .heightIn(min = 30.dp)
                    .background(rbColor(t.bg), RoundedCornerShape(8.dp))
                    .then(if (ring != null) Modifier.border(ring.first.dp, ring.second, RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(t.t, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
                if (t.sub.isNotEmpty()) Text(t.sub, fontFamily = IBMPlexMono, fontSize = 10.sp, lineHeight = 12.sp, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RbBarsView(rows: List<RbRow>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = rbTrack
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.w, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = rbColor(r.wc), modifier = Modifier.width(84.dp),
                )
                BoxWithConstraints(modifier = Modifier.padding(horizontal = 8.dp).weight(1f).height(14.dp)) {
                    val w = maxWidth
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).background(track, RoundedCornerShape(4.dp)))
                    if (r.mid) Box(Modifier.offset(x = w / 2).width(1.dp).fillMaxHeight().background(muted.copy(alpha = 0.5f)))
                    if (r.width > 0) Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = w * r.from.toFloat())
                            .width(w * r.width.toFloat())
                            .height(8.dp)
                            .background(rbColor(r.bar), RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    r.v, fontFamily = IBMPlexMono, fontSize = 12.sp, color = onSurface.copy(alpha = 0.8f), maxLines = 1,
                    textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 60.dp),
                )
            }
        }
    }
}

@Composable
private fun RbColsView(cols: List<RbVCol>) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = rbTrack
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cols.forEach { c ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.v, fontFamily = IBMPlexMono, fontSize = 11.sp, lineHeight = 14.sp, color = onSurface.copy(alpha = 0.8f), maxLines = 1, modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp).background(track, RoundedCornerShape(6.dp)).padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    c.bars.forEach { b ->
                        Box(
                            Modifier.width(9.dp).height(max(0.0, min(b.h, 62.0)).dp)
                                .background(rbColor(b.bg), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)),
                        )
                    }
                }
                Text(c.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = rbColor(c.lc), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun RbGridView(grid: RbBlock.Grid) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 55.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            grid.cols.forEach { h ->
                Text(h, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = muted, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        grid.rows.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = rbColor(r.lc), maxLines = 1, modifier = Modifier.width(52.dp))
                r.cells.forEach { c ->
                    val ring = rbRing(c.ring)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(grid.ch.dp)
                            .background(rbColor(c.bg), RoundedCornerShape(4.dp))
                            .then(if (ring != null) Modifier.border(ring.first.dp, ring.second, RoundedCornerShape(4.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c.t.isNotEmpty()) Text(
                            c.t, fontFamily = IBMPlexMono, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = rbTextOn(c.bg, c.color), maxLines = 2, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RbPlotView(plot: RbBlock.Plot) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(plot.h.dp).background(rbTrack, RoundedCornerShape(12.dp))) {
        val w = maxWidth
        val h = maxHeight
        plot.pts.forEach { p ->
            val sz = p.sz.dp
            val ring = rbRing(p.ring)
            Box(
                modifier = Modifier
                    .offset(x = w * (p.x / 100).toFloat() - sz / 2, y = h * (p.y / 100).toFloat() - sz / 2)
                    .size(sz)
                    .background(rbColor(p.bg), CircleShape)
                    .then(if (ring != null) Modifier.border(ring.first.dp, ring.second, CircleShape) else Modifier),
            )
        }
    }
}

@Composable
private fun RbStatsView(rows: List<RbStat>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        rows.forEach { s ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(s.k, fontSize = 14.sp, lineHeight = 22.sp, color = muted, modifier = Modifier.width(130.dp).padding(end = 8.dp))
                Text(s.v, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = rbColor(s.c), modifier = Modifier.weight(1f))
            }
        }
    }
}
