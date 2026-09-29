package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.min

// ── Text labs ────────────────────────────────────────────────────────────────
// Naive search, KMP, Z algorithm, Rabin-Karp, Manacher and longest palindrome as storyboards over rows
// of characters: the text under an index header, patterns and arrays aligned beneath it, small labels
// under the cells that matter (i, C, R, the Z-box), then the comparison or recurrence, chips and a
// headline. Every value is computed.

// Struck is a value ruled out for good (two pointers, Kadane's dropped prefix), drawn dim and crossed out.
// Outline is a navy tile with a blue border: still possible, in the heap, waiting on a stack.
// Green is a solid green tile (a two-heaps upper half, a matched prefix).
// Stack is a dark tile with a yellow border (a call waiting on the stack); Memo a violet-bordered memo hit.
// Frontier is an empty tile with a blue border (the next BFS wave); Rose a solid pink (a bipartite side).
// Plain is bare text with no tile (the "= 7" after a row of bits, a spacer).
private enum class TTone { Idle, Active, Blue, Matched, Mismatch, Mirror, Answer, Pending, Ghost, GhostMatched, Struck, Outline, Green, Stack, Memo, Frontier, Rose, Plain }

// [top] is a small line above the value (a hash tile's window, "415").
private class TCell(val text: String, val tone: TTone, val top: String? = null)

private class TRow(
    val cells: List<TCell>,
    val label: String = "",
    // A caps title over the row ("P (RADIUS)").
    val title: String? = null,
    val offset: Int = 0,
    // Labels under cells, keyed by column: "i", "C", "window".
    val tags: Map<Int, Pair<String, TTone>> = emptyMap(),
    // Tiles spread across the full width instead of sitting on the column grid (Rabin-Karp's hashes).
    val spread: Boolean = false,
    // Smaller spread tiles for long rows; opt-in so existing labs keep their size.
    val compact: Boolean = false,
    // Spread rows only: cells from [split] on form a second group with its own title (two heaps, merge halves).
    val split: Int? = null,
    val splitTitle: String? = null,
    // A thin rule between the two groups instead of a gap (meet in the middle's halves).
    val divider: Boolean = false,
    // Tints the row label (the row being filled).
    val labelTone: TTone? = null,
    // Spread rows only: a glyph between neighbouring tiles (a doubly linked list's ⇄).
    val joiner: String? = null,
)

private class TFrame(
    val columns: Int,
    val rows: List<TRow>,
    val headline: String,
    val body: String,
    val lit: Map<Int, TTone> = emptyMap(),
    val formula: List<String> = emptyList(),
    val formulaRows: List<StoryFormulaRow> = emptyList(),
    val chips: List<StoryChip> = emptyList(),
    // Queries or updates as labelled bars (Mo's, difference array), above the grid when [listFirst].
    val list: List<TListRow> = emptyList(),
    val listFirst: Boolean = false,
    // Monte Carlo's samples in the unit square; drawn instead of the grid.
    val scatter: List<Pair<Double, Double>> = emptyList(),
    // A linked list drawn as nodes and arrows (fast and slow pointers, in-place reversal).
    val chain: TChain? = null,
    // Intervals as bars on a number line (merge intervals, greedy scheduling).
    val timeline: TTimeline? = null,
    // Values as vertical bars (in-place partitioning).
    val bars: TBars? = null,
    // A caps title over the index header ("ROTATED ARRAY · TARGET 8").
    val heading: String? = null,
    // A caps title over the list ("PROBES").
    val listTitle: String? = null,
    // Draw the timeline above the rows (heap scheduling's heap sits under its meetings).
    val timelineFirst: Boolean = false,
    // A call tree or rooted tree, drawn above the rows.
    val tree: TTree? = null,
    // Jobs as proportional bars, one lane per row (the exchange argument).
    val gantt: List<TGanttRow> = emptyList(),
    // Column labels for the index header instead of 0, 1, 2 (binary lifting's A…G).
    val headers: List<String>? = null,
    // Nodes as circles joined by (weighted, directed) edges, drawn above the rows.
    val graph: TGraph? = null,
    // Grid rows with no index header: a map of cells (multi-source BFS, islands).
    val noHeader: Boolean = false,
    // Spread rows drawn above the heading (game theory's piles over its table).
    val topRows: List<TRow> = emptyList(),
    // Lines of state chips and transition labels under the formula (rest → buy → hold …); true marks a chip.
    val flow: List<List<Pair<String, Boolean>>> = emptyList(),
    // With [topColumns], [topRows] form a headed grid of their own (2D prefix sums' input matrix).
    val topColumns: Int = 0,
    val topHeaders: List<String>? = null,
    val topHeading: String? = null,
)

private class TGNode(val label: String, val x: Double, val y: Double)
private class TGEdge(val a: Int, val b: Int, val weight: String? = null, val tone: TTone? = null, val dashed: Boolean = false)

/** Circles at fractional positions; [captions] sit under a node ("15 → 17", "1st"); an edge's tone colours it. */
private class TGraph(
    val nodes: List<TGNode>,
    val edges: List<TGEdge>,
    val directed: Boolean = true,
    val tones: Map<Int, TTone> = emptyMap(),
    val captions: Map<Int, Pair<String, TTone>> = emptyMap(),
    val base: TTone = TTone.Pending,
)

private class TNode(val label: String, val parent: Int?, val sub: String? = null)

/** A curved arrow beside the tree from one node to another (a binary-lifting jump), on the right or left. */
private class TArc(val from: Int, val to: Int, val label: String, val tone: TTone, val right: Boolean)

/**
 * Nodes in a tidy layout (or at fixed [pos]: x as a fraction of the width, and a row). [tones] default to
 * [base]; [edges] colours the edge into a node; [badges] sit to a node's right; [labels] rename a node.
 */
private class TTree(
    val nodes: List<TNode>,
    val tones: Map<Int, TTone> = emptyMap(),
    val labels: Map<Int, String> = emptyMap(),
    val badges: Map<Int, Pair<String, TTone>> = emptyMap(),
    val edges: Map<Int, TTone> = emptyMap(),
    val arcs: List<TArc> = emptyList(),
    val pos: List<Pair<Double, Int>>? = null,
    val base: TTone = TTone.Pending,
)

private class TGanttRow(val title: String, val note: String, val jobs: List<Pair<Int, TTone>>)

/** A size or target the lab rebuilds its tabs for ("n 6", "Items · target 15  6"). */
private class TParam(val label: (Int) -> String, val values: List<Int>, val start: Int, val build: (Int) -> List<TTab>)

/** One bar per value with its index underneath and a tag row below that; [arc] is a pending swap, dashed. */
private class TBars(
    val values: List<Int>,
    val tones: List<TTone>,
    val tags: Map<Int, Pair<String, TTone>> = emptyMap(),
    val arc: Pair<Int, Int>? = null,
)

/**
 * Nodes in a row. `next[i]` is the node i points at (null for null); neighbours get a straight arrow,
 * anything else an arc under the row. [special] restyles one node's pointer as an arc in a tone with an
 * optional label (the cycle edge, the pointer being rewired); [cut] is a link being broken, dashed red.
 */
private class TChain(
    val nodes: List<TCell>,
    val next: List<Int?>,
    val special: Map<Int, Pair<TTone, String?>> = emptyMap(),
    val cut: Pair<Int, Int>? = null,
    val above: Map<Int, Pair<String, TTone>> = emptyMap(),
    val below: Map<Int, Pair<String, TTone>> = emptyMap(),
    // Tone for the straight arrow leaving node i; idle when absent.
    val arrowTones: Map<Int, TTone> = emptyMap(),
)

// [side] is a small label right of the bar (the room a meeting landed in).
private class TBar(val start: Double, val end: Double, val tone: TTone, val label: String? = null, val side: String? = null)

/** One bar per row, then an output lane under a rule; [marker] is a dashed vertical line (lastEnd). */
private class TTimeline(
    val maxX: Double,
    val step: Double,
    val bars: List<TBar>,
    val output: List<TBar> = emptyList(),
    val marker: Double? = null,
    val markerTone: TTone = TTone.Blue,
)

/** One query or update: "Q2  [0, 5]  block 0  26". [tone] is Matched (done), Active (current) or Pending. */
private class TListRow(val name: String, val range: String, val note: String, val value: String, val tone: TTone)

private class TTab(
    val label: String,
    val frames: List<TFrame>,
    val legend: List<Triple<Color, SwatchStyle, String>>,
    // A stepper over the card whose value names each step ("Samples 300"); − and + move between steps.
    val stepper: Pair<String, List<Int>>? = null,
    // The step the tab opens on (Top-K opens in its select phase).
    val start: Int = 0,
)

private fun chars(s: String) = s.map { "$it" }
private fun q(s: String) = "'$s'"

// ── Naive search ──

private fun naiveTabs(): List<TTab> {
    val t = chars("ABCABCABD")
    val p = chars("ABCABD")
    val n = t.size
    val m = p.size
    val frames = mutableListOf<TFrame>()
    var comparisons = 0
    val found = (0..n - m).first { s -> (0 until m).all { t[s + it] == p[it] } }
    val summary = listOf(StoryFormulaRow("match found at", "s = $found, after $found shift${if (found == 1) "" else "s"}"))
    fun textRow(col: Int?) = TRow(t.mapIndexed { i, c -> TCell(c, if (i == col) TTone.Active else TTone.Idle) }, "T")
    fun patRow(s: Int, j: Int, bad: Boolean) = TRow(
        p.mapIndexed { k, c -> TCell(c, if (k < j) TTone.Matched else if (k == j) (if (bad) TTone.Mismatch else TTone.Matched) else TTone.Pending) },
        "s=$s", offset = s,
    )
    frames += TFrame(
        n, listOf(textRow(null), TRow(p.map { TCell(it, TTone.Pending) }, "s=0")),
        "Try the pattern at every shift s, comparing character by character.",
        "A mismatch moves the pattern one place right and starts over from its first character.",
        formula = listOf("slide P along T, comparing left to right"), formulaRows = summary,
        chips = listOf(StoryChip("s", "0"), StoryChip("comparisons", "0")),
    )
    loop@ for (s in 0..found) {
        for (j in 0 until m) {
            comparisons++
            val ok = t[s + j] == p[j]
            val chips = listOf(StoryChip("s", "$s"), StoryChip("comparisons", "$comparisons"))
            if (ok && j == m - 1) {
                frames += TFrame(
                    n, listOf(textRow(null), TRow(p.map { TCell(it, TTone.Answer) }, "s=$s", offset = s)),
                    "All $m characters match: the pattern is at {v:s = $s}.",
                    "$comparisons comparisons in all. In the worst case naive search costs O(n · m).",
                    lit = mapOf(s + j to TTone.Active), formula = listOf("T[$s..${s + m - 1}] = P → {v:match at s = $s}"), formulaRows = summary,
                    chips = listOf(StoryChip("s", "$s", StoryTone.Answer), StoryChip("comparisons", "$comparisons")),
                )
                break@loop
            }
            if (ok) {
                frames += TFrame(
                    n, listOf(textRow(s + j), patRow(s, j, false)),
                    "T[${s + j}] = P[$j] = ${q(p[j])}, so j = $j matches.",
                    "Compare left to right until a character differs or the whole pattern matches.",
                    lit = mapOf(s + j to TTone.Active), formula = listOf("T[${s + j}] = ${q(t[s + j])} = P[$j] → next"), formulaRows = summary, chips = chips,
                )
            } else {
                frames += TFrame(
                    n, listOf(textRow(s + j), patRow(s, j, true), TRow(p.map { TCell(it, TTone.Ghost) }, "s=${s + 1}", offset = s + 1)),
                    if (j > 0) "Mismatch at j = $j after $j match${if (j == 1) "" else "es"}, so shift by one." else "${q(t[s + j])} ≠ ${q(p[0])} at once, so shift by one.",
                    if (j > 0) "The $j matched character${if (j == 1) " is" else "s are"} compared again at s = ${s + 1}. KMP avoids that by reusing them."
                    else "Nothing matched, so this shift cost a single comparison.",
                    lit = mapOf(s + j to TTone.Active),
                    formula = listOf("T[${s + j}] = {w:${q(t[s + j])}}", "≠ P[$j] = ${q(p[j])} → shift to s = ${s + 1}"), formulaRows = summary, chips = chips,
                )
                break
            }
        }
    }
    return listOf(
        TTab(
            "Search", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Comparing"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Matched this alignment"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Mismatch"),
            ),
        ),
    )
}

// ── KMP ──

private fun lpsOf(p: List<String>): List<Int> {
    val lps = MutableList(p.size) { 0 }
    var len = 0
    var i = 1
    while (i < p.size) {
        when {
            p[i] == p[len] -> { len++; lps[i] = len; i++ }
            len > 0 -> len = lps[len - 1]
            else -> { lps[i] = 0; i++ }
        }
    }
    return lps
}

private fun kmpTabs(): List<TTab> {
    val t = chars("ABABABC")
    val p = chars("ABABC")
    val n = t.size
    val m = p.size
    val lps = lpsOf(p)
    val frames = mutableListOf<TFrame>()
    var i = 0
    var j = 0
    fun textRow(col: Int?) = TRow(t.mapIndexed { k, c -> TCell(c, if (k == col) TTone.Active else TTone.Idle) }, "T")
    fun patRow(offset: Int, upTo: Int, bad: Boolean) = TRow(
        p.mapIndexed { k, c -> TCell(c, if (k < upTo) TTone.Matched else if (k == upTo) (if (bad) TTone.Mismatch else TTone.Matched) else TTone.Pending) },
        "P", offset = offset,
    )
    fun lpsRow(offset: Int, lit: Int?) = TRow(lps.mapIndexed { k, v -> TCell("$v", if (k == lit) TTone.Active else TTone.Pending) }, "lps", offset = offset)
    frames += TFrame(
        n, listOf(textRow(null), TRow(p.map { TCell(it, TTone.Pending) }, "P"), lpsRow(0, null)),
        "KMP never moves i backwards. On a mismatch, lps says how much of the pattern still matches.",
        "The lps table depends only on the pattern, so it is built once, in O(m).",
        formula = listOf("lps[j] = longest proper prefix of P[0..j] that is also its suffix"), chips = listOf(StoryChip("i", "0"), StoryChip("j", "0")),
    )
    while (i < n) {
        val offset = i - j
        if (t[i] == p[j]) {
            if (j == m - 1) {
                frames += TFrame(
                    n, listOf(textRow(null), TRow(p.map { TCell(it, TTone.Answer) }, "P", offset = offset), lpsRow(offset, null)),
                    "The last character matches: the pattern is at {v:$offset}.",
                    "i visited each text character once, so the search is O(n) on top of O(m) for lps.",
                    lit = mapOf(i to TTone.Active), formula = listOf("${q(t[i])} = ${q(p[j])} → {v:match at $offset}"),
                    chips = listOf(StoryChip("i", "$i"), StoryChip("found", "$offset", StoryTone.Answer)),
                )
                break
            }
            frames += TFrame(
                n, listOf(textRow(i), patRow(offset, j, false), lpsRow(offset, null)),
                "T[$i] = P[$j] = ${q(p[j])}, so both pointers move on.",
                "Every matched character is a prefix of P that KMP can fall back into later.",
                lit = mapOf(i to TTone.Active), formula = listOf("T[$i] = ${q(t[i])} = P[$j] → i, j advance"), chips = listOf(StoryChip("i", "$i"), StoryChip("j", "$j")),
            )
            i++
            j++
        } else if (j > 0) {
            val back = lps[j - 1]
            val next = TRow(p.mapIndexed { k, c -> TCell(c, if (k < back) TTone.GhostMatched else TTone.Ghost) }, "next", offset = i - back)
            frames += TFrame(
                n, listOf(textRow(i), patRow(offset, j, true), lpsRow(offset, j - 1), next),
                "T[$i] = ${q(t[i])} ≠ ${q(p[j])}, so j falls back to lps[${j - 1}] = {$back}, not 0.",
                if (back > 0) "\"${p.take(back).joinToString("")}\" already matches the end of \"${p.take(j).joinToString("")}\", so i never moves back."
                else "No prefix of the matched part is also its suffix, so j restarts at 0, but i still stays put.",
                lit = mapOf(i to TTone.Active), formula = listOf("{w:${q(t[i])} ≠ ${q(p[j])}} → j = lps[${j - 1}] = {$back}"),
                chips = listOf(StoryChip("i", "$i"), StoryChip("j", "$j → $back")),
            )
            j = back
        } else {
            frames += TFrame(
                n, listOf(textRow(i), patRow(offset, 0, true), lpsRow(offset, null)),
                "Nothing to fall back on at j = 0, so only i moves.", "This is the one case where a comparison advances i without a match.",
                lit = mapOf(i to TTone.Active), formula = listOf("{w:${q(t[i])} ≠ ${q(p[0])}} with j = 0 → i advances"),
                chips = listOf(StoryChip("i", "$i"), StoryChip("j", "0")),
            )
            i++
        }
    }
    val build = mutableListOf<TFrame>()
    val table = MutableList<Int?>(m) { null }
    table[0] = 0
    fun lpsCells(lit: Int?) = table.mapIndexed { k, v -> TCell(v?.toString() ?: "·", if (k == lit) TTone.Active else if (v == null) TTone.Pending else TTone.Idle) }
    build += TFrame(
        m, listOf(TRow(p.map { TCell(it, TTone.Idle) }, "P"), TRow(lpsCells(0), "lps")),
        "lps[0] is always {0}: a single character has no proper prefix.",
        "A second pointer, len, tracks how long the current matching prefix is.",
        formula = listOf("lps[0] = {0}"), chips = listOf(StoryChip("len", "0")),
    )
    var len = 0
    var k = 1
    while (k < m) {
        val pRow = TRow(
            p.mapIndexed { idx, c ->
                TCell(
                    c,
                    when {
                        idx == k -> TTone.Active
                        idx == len -> TTone.Blue
                        idx < len -> TTone.Matched
                        else -> TTone.Idle
                    },
                )
            },
            "P",
        )
        when {
            p[k] == p[len] -> {
                len++
                table[k] = len
                build += TFrame(
                    m, listOf(pRow, TRow(lpsCells(k), "lps")),
                    "P[$k] extends the matching prefix, so lps[$k] = {$len}.",
                    "The prefix \"${p.take(len).joinToString("")}\" is also a suffix of \"${p.take(k + 1).joinToString("")}\".",
                    formula = listOf("P[$k] = ${q(p[k])} = P[${len - 1}] → lps[$k] = {$len}"), chips = listOf(StoryChip("len", "$len")),
                )
                k++
            }
            len > 0 -> {
                val back = table[len - 1]!!
                build += TFrame(
                    m, listOf(pRow, TRow(lpsCells(len - 1), "lps")),
                    "${q(p[k])} ≠ ${q(p[len])}, so len falls back to lps[${len - 1}] = {$back}.",
                    "The table is built with the same fallback it will be used for.",
                    formula = listOf("{w:${q(p[k])} ≠ ${q(p[len])}} → len = lps[${len - 1}] = {$back}"), chips = listOf(StoryChip("len", "$len → $back")),
                )
                len = back
            }
            else -> {
                table[k] = 0
                build += TFrame(
                    m, listOf(pRow, TRow(lpsCells(k), "lps")),
                    "${q(p[k])} matches no prefix, so lps[$k] = {0}.",
                    "A mismatch here means the search will restart the pattern from its first character.",
                    formula = listOf("{w:${q(p[k])} ≠ ${q(p[0])}} with len = 0 → lps[$k] = {0}"), chips = listOf(StoryChip("len", "0")),
                )
                k++
            }
        }
    }
    val lpsText = table.joinToString(" ") { "$it" }
    build += TFrame(
        m, listOf(TRow(p.map { TCell(it, TTone.Idle) }, "P"), TRow(table.map { TCell("$it", TTone.Answer) }, "lps")),
        "The lps table is {v:$lpsText}.",
        "len only grows by one per step and never falls further than it rose, so building is O(m).",
        formula = listOf("lps = {v:$lpsText}"), chips = listOf(StoryChip("cost", "O(m)", StoryTone.Answer)),
    )
    val legend = listOf(
        Triple(SimColors.Active, SwatchStyle.Fill, "Reading"),
        Triple(SimColors.Green, SwatchStyle.Fill, "Matched"),
        Triple(SimColors.Red, SwatchStyle.Fill, "Mismatch"),
    )
    return listOf(TTab("Search", frames, legend), TTab("Build LPS", build, legend + Triple(SimColors.Blue, SwatchStyle.Fill, "Prefix end")))
}

// ── Z algorithm ──

private fun zTabs(): List<TTab> {
    val s = chars("aabcaabxaaz")
    val n = s.size
    val z = MutableList<Int?>(n) { null }
    val frames = mutableListOf<TFrame>()
    var l = 0
    var r = 0
    fun zRow(lit: Int?, mirror: Int?) = TRow(
        (0 until n).map { k ->
            val v = z[k]
            when {
                k == 0 -> TCell("-", TTone.Idle)
                v == null -> TCell("·", TTone.Pending)
                k == lit -> TCell("$v", TTone.Active)
                k == mirror -> TCell("$v", TTone.Mirror)
                else -> TCell("$v", TTone.Idle)
            }
        },
        "Z",
    )
    fun sRow(i: Int?, box: IntRange?, k: Int?, len: Int): TRow {
        val tags = mutableMapOf<Int, Pair<String, TTone>>()
        if (box != null) {
            tags[box.first] = "l" to TTone.Blue
            tags[box.last] = "r" to TTone.Blue
        }
        if (i != null) tags[i] = "i" to TTone.Active
        if (k != null) tags[k] = "k" to TTone.Matched
        return TRow(
            s.mapIndexed { idx, c ->
                TCell(
                    c,
                    when {
                        idx == i -> TTone.Active
                        box != null && idx in box -> TTone.Blue
                        idx == k -> TTone.Mirror
                        len > 0 && idx < len -> TTone.Matched
                        else -> TTone.Idle
                    },
                )
            },
            "s", tags = tags,
        )
    }
    frames += TFrame(
        n, listOf(sRow(null, null, null, 0), zRow(null, null)),
        "Z[i] counts how far s[i..] agrees with the start of s.",
        "A Z-box [l, r] remembers the rightmost match, so later positions can copy instead of compare.",
        formula = listOf("Z[i] = length of the longest prefix of s starting at i"), chips = listOf(StoryChip("l", "0"), StoryChip("r", "0")),
    )
    for (i in 1 until n) {
        val inBox = i <= r
        val k = i - l
        if (inBox && z[k]!! < r - i + 1) {
            val v = z[k]!!
            z[i] = v
            frames += TFrame(
                n, listOf(sRow(i, l..r, k, r - l + 1), zRow(i, k)),
                "i = $i is inside the Z-box, so Z[$i] copies Z[$k] = {$v} without comparing.",
                "The box [$l, $r] equals the prefix \"${s.take(r - l + 1).joinToString("")}\", so position $i mirrors position $k.",
                lit = mapOf(i to TTone.Active), formula = listOf("Z[$i] = min(Z[$k], r − i + 1) = min($v, ${r - i + 1}) = {$v}"),
                chips = listOf(StoryChip("l", "$l"), StoryChip("r", "$r"), StoryChip("k", "$k")),
            )
            continue
        }
        var v = if (inBox) r - i + 1 else 0
        val start = v
        while (i + v < n && s[v] == s[i + v]) v++
        z[i] = v
        if (v > 0 && i + v - 1 > r) {
            l = i
            r = i + v - 1
        }
        val (formula, headline, body) = when {
            inBox -> Triple(
                "Z[$k] reaches the box edge → start at $start, compare on → {$v}",
                "i = $i runs into the box edge, so Z[$i] starts at $start and compares on: {$v}.",
                "Only the characters beyond r cost comparisons, which is what keeps Z linear.",
            )
            v == 0 -> Triple(
                "s[$i] = ${q(s[i])} ≠ s[0] = ${q(s[0])} → Z[$i] = {0}",
                "${q(s[i])} ≠ ${q(s[0])}, so Z[$i] = {0}.",
                "Outside any Z-box there is nothing to copy, so it compares from scratch.",
            )
            else -> Triple(
                "s[$i..${i + v - 1}] = s[0..${v - 1}] → Z[$i] = {$v}",
                "i = $i is outside the box, so compare from scratch: {$v} character${if (v == 1) "" else "s"} match the prefix.",
                "A match opens a new Z-box [$l, $r] for the positions after it.",
            )
        }
        frames += TFrame(
            n, listOf(sRow(i, if (r > 0) l..r else null, null, v), zRow(i, null)), headline, body,
            lit = mapOf(i to TTone.Active), formula = listOf(formula), chips = listOf(StoryChip("l", "$l"), StoryChip("r", "$r")),
        )
    }
    frames += TFrame(
        n, listOf(sRow(null, null, null, 0), TRow((0 until n).map { if (it == 0) TCell("-", TTone.Idle) else TCell("${z[it]}", TTone.Answer) }, "Z")),
        "The Z array is complete in {v:O(n)}.",
        "To search, run it on pattern + \"$\" + text: every Z equal to the pattern's length is a match.",
        formula = listOf("Z = {v:" + (1 until n).joinToString(" ") { "${z[it]}" } + "}"), chips = listOf(StoryChip("cost", "O(n)", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Z array", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current index"),
                Triple(SimColors.Blue, SwatchStyle.Fill, "Z-box [l, r]"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Prefix it matches"),
            ),
        ),
    )
}

// ── Rabin-Karp ──

private fun rabinTabs(): List<TTab> {
    val text = chars("31415926")
    val pattern = "415"
    val m = pattern.length
    val mod = 13
    val base = 10
    val n = text.size
    val windows = n - m + 1
    val pHash = pattern.toInt() % mod
    var high = 1
    repeat(m - 1) { high = high * base % mod }
    val hashes = mutableListOf<Int>()
    var h = 0
    for (k in 0 until m) h = (h * base + text[k].toInt()) % mod
    hashes += h
    for (s in 1 until windows) {
        h = ((h - text[s - 1].toInt() * high % mod + mod) * base + text[s + m - 1].toInt()) % mod
        hashes += h
    }
    fun digits(s: Int?) = TRow(
        text.mapIndexed { k, c -> TCell(c, if (s != null && k >= s && k < s + m) TTone.Blue else TTone.Idle) },
        tags = if (s != null) mapOf(s + 1 to ("window" to TTone.Blue)) else emptyMap(),
    )
    fun tiles(current: Int?, hit: Boolean = false) = TRow(
        (0 until windows).map { s ->
            val win = text.subList(s, s + m).joinToString("")
            when {
                current == null || s > current -> TCell("·", TTone.Pending, win)
                s == current -> TCell("${hashes[s]}", if (hit) TTone.Answer else TTone.Active, win)
                else -> TCell("${hashes[s]}", TTone.Matched, win)
            }
        },
        title = "WINDOW HASH MOD $mod · PATTERN $pattern → $pHash", spread = true,
    )
    fun lit(s: Int) = (s until s + m).associateWith { TTone.Active }
    val frames = mutableListOf(
        TFrame(
            n, listOf(digits(null), tiles(null)),
            "Hash the pattern once: $pattern mod $mod = {$pHash}.",
            "Each window of $m digits gets the same kind of hash, and only equal hashes are compared character by character.",
            formula = listOf("hash($pattern) = $pattern mod $mod = {$pHash}"), chips = listOf(StoryChip("pattern", "$pHash")),
        ),
    )
    for (s in 0 until windows) {
        val win = text.subList(s, s + m).joinToString("")
        val hit = hashes[s] == pHash
        val formula = if (s == 0) {
            "$win mod $mod = {${hashes[0]}}"
        } else {
            val prev = hashes[s - 1]
            val lead = text[s - 1].toInt()
            val add = text[s + m - 1].toInt()
            "($prev − $lead·$high)·$base + $add = ${(prev - lead * high) * base + add} ≡ {${hashes[s]}} (mod $mod)"
        }
        frames += TFrame(
            n, listOf(digits(s), tiles(s)),
            if (hit) "Window $win hashes to {${hashes[s]}}, same as the pattern, so compare the characters."
            else "Window $win hashes to {${hashes[s]}}, not $pHash, so skip it without comparing.",
            if (s == 0) "The first window is hashed in full. Every later one rolls in O(1)."
            else "Rolling costs O(1): drop the leading ${text[s - 1]}, shift, add ${text[s + m - 1]}. Equal hashes still need a character check.",
            lit = lit(s), formula = listOf(formula),
            chips = listOf(StoryChip("pattern", "$pHash"), StoryChip("window", "${hashes[s]}", if (hit) StoryTone.Active else StoryTone.Idle)),
        )
        if (hit) {
            frames += TFrame(
                n, listOf(digits(s), tiles(s, hit = true)),
                "The characters agree too: {v:$pattern} is at index $s.",
                "Without the check, a different window with the same hash would be reported as a false match.",
                lit = lit(s), formula = listOf("$win = $pattern → {v:match at $s}"), chips = listOf(StoryChip("found", "$s", StoryTone.Answer)),
            )
        }
    }
    frames += TFrame(
        n, listOf(digits(null), tiles(windows - 1)),
        "{v:$windows} windows hashed, and only one needed a character check.",
        "Expected O(n + m); a bad hash that collides often degrades it to O(n · m).",
        formula = listOf("$windows windows, 1 character check"),
        chips = listOf(StoryChip("windows", "$windows"), StoryChip("checks", "1", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Search", frames,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Window"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Hash being checked"),
                Triple(SimColors.Green, SwatchStyle.Fill, "No hit"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Match"),
            ),
        ),
    )
}

// ── Palindromes ──

/** Manacher over "#a#b#a#c#a#b#a#": radius per centre, reusing the mirror inside the rightmost palindrome. */
private fun manacherFrames(word: String): List<TFrame> {
    val t = listOf("#") + chars(word).flatMap { listOf(it, "#") }
    val n = t.size
    val p = MutableList<Int?>(n) { null }
    var c = 0
    var r = 0
    var best = 0
    var bestAt = 0
    val frames = mutableListOf<TFrame>()
    for (i in 0 until n) {
        val mirror = 2 * c - i
        var start = 0
        var fromMirror = false
        if (i < r) {
            start = min(r - i, p[mirror]!!)
            fromMirror = true
        }
        var k = start
        while (i - k - 1 >= 0 && i + k + 1 < n && t[i - k - 1] == t[i + k + 1]) k++
        p[i] = k
        val expanded = k - start
        val oldC = c
        val oldR = r
        if (i + k > r) {
            c = i
            r = i + k
        }
        if (k > best) {
            best = k
            bestAt = i
        }
        val tags = mutableMapOf<Int, Pair<String, TTone>>()
        if (fromMirror) tags[mirror] = "mir" to TTone.Matched
        tags[oldC] = "C" to TTone.Blue
        tags[i] = "i" to TTone.Active
        if (oldR < n && oldR !in tags) tags[oldR] = "R" to TTone.Blue
        val row = TRow(
            t.mapIndexed { idx, ch ->
                TCell(
                    ch,
                    when {
                        idx == i -> TTone.Active
                        fromMirror && idx == mirror -> TTone.Mirror
                        oldR > 0 && abs(idx - oldC) <= (p[oldC] ?: 0) -> TTone.Blue
                        else -> TTone.Idle
                    },
                )
            },
            tags = tags,
        )
        val pRow = TRow(
            (0 until n).map { idx ->
                val v = p[idx]
                when {
                    v == null -> TCell("·", TTone.Pending)
                    idx == i -> TCell("$v", TTone.Active)
                    fromMirror && idx == mirror -> TCell("$v", TTone.Mirror)
                    idx == bestAt && v == best -> TCell("$v", TTone.Answer)
                    else -> TCell("$v", TTone.Idle)
                }
            },
            title = "P (RADIUS)",
        )
        val formula = if (fromMirror) {
            listOf("mirror = 2·$oldC − $i = $mirror → P[$i] =", "min(P[$mirror], R − i) = {$start}" + if (expanded > 0) " → expand to {$k}" else "")
        } else {
            listOf("i ≥ R, so start at 0 → expand → P[$i] = {$k}")
        }
        val headline: String
        val body: String
        if (fromMirror) {
            headline = "i = $i mirrors $mirror across centre $oldC, so P[$i] starts at {$start} for free."
            body = when {
                expanded > 0 -> "Past the right edge R it expands by $expanded more with direct checks."
                i + start >= oldR && i + start + 1 < n -> "It reaches the right edge R, so one direct check is tried, and it fails."
                i + start >= oldR -> "It reaches the right edge R, so one direct check is tried, but the string ends there."
                else -> "The mirror's palindrome sits wholly inside C's, so no check is needed at all."
            }
        } else {
            headline = if (k == 0) "t[$i] is outside every palindrome so far, and it cannot grow: P[$i] = {0}." else "t[$i] starts from 0 and expands to P[$i] = {$k}."
            body = if (k > 0 && i + k > oldR) "It pushes past R, so it becomes the new centre C = $i, R = ${i + k}."
            else "Each '#' stands for a gap, so even and odd palindromes are handled alike."
        }
        frames += TFrame(
            n, listOf(row, pRow), headline, body, lit = mapOf(i to TTone.Active), formula = formula,
            chips = listOf(StoryChip("C", "$c"), StoryChip("R", "$r"), StoryChip("longest", "$best", StoryTone.Answer)),
        )
    }
    val startIdx = (bestAt - best) / 2
    val answer = word.substring(startIdx, startIdx + best)
    frames += TFrame(
        n,
        listOf(
            TRow(t.mapIndexed { idx, ch -> TCell(ch, if (abs(idx - bestAt) <= best) TTone.Answer else TTone.Idle) }),
            TRow(p.mapIndexed { idx, v -> TCell("$v", if (idx == bestAt) TTone.Answer else TTone.Idle) }, title = "P (RADIUS)"),
        ),
        "The longest palindrome is {v:$answer}, length $best.",
        "R only ever moves right, so every character is expanded past at most once: O(n).",
        formula = listOf("max P = $best at $bestAt → {v:$answer}"), chips = listOf(StoryChip("longest", answer, StoryTone.Answer)),
    )
    return frames
}

private fun expandFrames(word: String): List<TFrame> {
    val s = chars(word)
    val n = s.size
    val frames = mutableListOf<TFrame>()
    var best = 0 to 0
    val total = 2 * n - 1
    fun bestText() = s.subList(best.first, best.second + 1).joinToString("")
    for (c in 0 until total) {
        var l = c / 2
        var r = (c + 1) / 2
        if (s[l] != s[r]) {
            frames += TFrame(
                n, listOf(TRow(s.mapIndexed { idx, ch -> TCell(ch, if (idx == l || idx == r) TTone.Mismatch else TTone.Idle) })),
                "The gap between ${q(s[l])} and ${q(s[r])} has different sides, so nothing grows there.",
                "Every character and every gap is a centre ($total here). Each one expands until its ends differ.",
                lit = mapOf(l to TTone.Mismatch, r to TTone.Mismatch),
                formula = listOf("s[$l] = ${q(s[l])} ≠ s[$r] = ${q(s[r])} → length 0"),
                formulaRows = listOf(StoryFormulaRow("best so far", "${q(bestText())}, length ${best.second - best.first + 1}")),
                chips = listOf(StoryChip("centre", "${c + 1} of $total"), StoryChip("best", bestText(), StoryTone.Answer)),
            )
            continue
        }
        while (l > 0 && r < n - 1 && s[l - 1] == s[r + 1]) {
            l--
            r++
        }
        val len = r - l + 1
        val before = best
        val beforeText = bestText()
        val newBest = len > before.second - before.first + 1
        if (newBest) best = l to r
        val centre = if (c % 2 == 0) q(s[c / 2]) else "the gap"
        val tags = mutableMapOf<Int, Pair<String, TTone>>()
        if (len > 1) {
            tags[l] = "L" to TTone.Active
            tags[r] = "R" to TTone.Active
        }
        if (c / 2 !in tags) tags[c / 2] = "centre" to TTone.Blue
        val row = TRow(
            s.mapIndexed { idx, ch ->
                TCell(
                    ch,
                    when {
                        len > 1 && (idx == l || idx == r) -> TTone.Active
                        idx in l..r -> if (len == 1) TTone.Active else TTone.Blue
                        else -> TTone.Idle
                    },
                )
            },
            tags = tags,
        )
        val d = (r - l) / 2
        frames += TFrame(
            n, listOf(row),
            if (len > 1) "s[$l] = s[$r], so the palindrome around $centre grows to {v:$len}." else "$centre alone is a palindrome of length {1}.",
            if (newBest) "Every character and gap is a centre ($total here). Each one expands until its ends differ."
            else "Not longer than the best so far, so the best stays ${q(bestText())}.",
            lit = if (len > 1) mapOf(l to TTone.Active, r to TTone.Active) else mapOf(c / 2 to TTone.Active),
            formula = listOf(if (len > 1) "s[${c / 2}−$d] = s[${(c + 1) / 2}+$d] → ${q(s[l])} = ${q(s[r])} → length {v:$len}" else "a single character → length {$len}"),
            formulaRows = listOf(StoryFormulaRow("best before", "${q(beforeText)}, length ${before.second - before.first + 1}")),
            chips = listOf(StoryChip("centre", "${c + 1} of $total"), StoryChip("best", bestText(), StoryTone.Answer)),
        )
    }
    val answer = bestText()
    frames += TFrame(
        n, listOf(TRow(s.mapIndexed { idx, ch -> TCell(ch, if (idx in best.first..best.second) TTone.Answer else TTone.Idle) })),
        "The longest palindrome is {v:$answer}, length ${answer.length}.",
        "$total centres, each expanding up to n: O(n²) worst case, which Manacher brings down to O(n).",
        formula = listOf("longest = {v:$answer}"), chips = listOf(StoryChip("best", answer, StoryTone.Answer)),
    )
    return frames
}

private val PalindromeLegend = listOf(
    Triple(SimColors.Active, SwatchStyle.Fill, "Comparing"),
    Triple(SimColors.Blue, SwatchStyle.Fill, "Already mirrored"),
    Triple(SimColors.Answer, SwatchStyle.Fill, "New best"),
)
private val ManacherLegend = listOf(
    Triple(SimColors.Active, SwatchStyle.Fill, "Current"),
    Triple(SimColors.Blue, SwatchStyle.Fill, "Inside palindrome at C"),
    Triple(SimColors.Green, SwatchStyle.Fill, "Mirror"),
    Triple(SimColors.Answer, SwatchStyle.Fill, "Longest so far"),
)

// ── Lab ──

internal val textStoryTopicIds = setOf(
    "naive_string_search", "kmp", "z_algorithm", "rabin_karp", "manacher", "longest_palindromic_substring",
    "two_pointer", "sliding_window", "prefix_sum", "kadanes_algorithm", "monte_carlo_method", "reservoir_sampling",
    "mos_algorithm", "difference_array", "two_pointer_pattern", "sliding_window_pattern", "prefix_sum_pattern",
    "running_best_pattern", "sweep_line_pattern", "rolling_hash_pattern", "prefix_function_pattern",
    "palindrome_expansion_pattern", "randomized_pattern", "fast_slow_pointers", "merge_intervals_pattern", "binary_search_answer",
    "top_k_pattern", "monotonic_stack_pattern", "cyclic_sort_pattern", "k_way_merge_pattern", "in_place_reversal_pattern",
    "greedy_intervals_pattern", "dutch_flag_pattern", "modified_binary_search_pattern", "divide_conquer_pattern", "lis_patience_pattern",
    "heap_scheduling_pattern", "hash_counting_pattern", "expression_stack_pattern", "monotonic_deque_pattern", "two_heaps_pattern",
    "memo_recursion_pattern", "meet_in_middle_pattern", "subsets_pattern", "backtracking_pattern", "bst_inorder_pattern",
    "greedy_exchange_pattern", "binary_lifting_pattern", "tree_dfs_pattern", "multi_source_bfs_pattern", "game_theory_dp_pattern",
    "matrix_islands_pattern", "dag_dp_pattern", "graph_coloring_pattern", "topological_sort_pattern", "shortest_path_pattern",
    "state_machine_dp_pattern", "grid_dp_pattern", "interval_dp_pattern", "matrix_transform_pattern", "bit_manipulation_pattern",
    "prefix_2d_pattern", "bit_trie_pattern", "composite_design_pattern",
)

private fun textTabs(topicId: String): List<TTab> = when (topicId) {
    "naive_string_search" -> naiveTabs()
    "kmp" -> kmpTabs()
    "z_algorithm" -> zTabs()
    "rabin_karp" -> rabinTabs()
    "manacher" -> listOf(TTab("Manacher", manacherFrames("abacaba"), ManacherLegend))
    // The interview-prep pattern topics teach the same technique, so they share its storyboard.
    "two_pointer", "two_pointer_pattern" -> twoPointerTabs()
    "sliding_window", "sliding_window_pattern" -> slidingWindowTabs()
    "prefix_sum", "prefix_sum_pattern" -> prefixSumTabs()
    "running_best_pattern" -> kadaneTabs()
    "sweep_line_pattern" -> differenceTabs()
    "rolling_hash_pattern" -> rabinTabs()
    "randomized_pattern" -> reservoirTabs()
    "palindrome_expansion_pattern" -> textTabs("longest_palindromic_substring")
    // The prefix function is KMP's lps table, so that tab comes first here.
    "prefix_function_pattern" -> kmpTabs().reversed()
    "fast_slow_pointers" -> fastSlowTabs()
    "merge_intervals_pattern" -> mergeIntervalTabs()
    "binary_search_answer" -> binaryAnswerTabs()
    "top_k_pattern" -> topKTabs()
    "monotonic_stack_pattern" -> monoStackTabs()
    "cyclic_sort_pattern" -> cyclicSortTabs()
    "k_way_merge_pattern" -> kWayMergeTabs()
    "in_place_reversal_pattern" -> reversalTabs()
    "greedy_intervals_pattern" -> greedyIntervalTabs()
    "dutch_flag_pattern" -> partitionTabs()
    "modified_binary_search_pattern" -> rotatedSearchTabs()
    "divide_conquer_pattern" -> divideConquerTabs()
    "lis_patience_pattern" -> lisTabs()
    "heap_scheduling_pattern" -> heapSchedulingTabs()
    "hash_counting_pattern" -> hashCountingTabs()
    "expression_stack_pattern" -> parsingStackTabs()
    "monotonic_deque_pattern" -> monoDequeTabs()
    "two_heaps_pattern" -> twoHeapsTabs()
    "memo_recursion_pattern", "meet_in_middle_pattern", "subsets_pattern", "backtracking_pattern" -> textParam(topicId)!!.let { it.build(it.start) }
    "bst_inorder_pattern" -> bstInorderTabs()
    "greedy_exchange_pattern" -> exchangeTabs()
    "binary_lifting_pattern" -> liftingTabs()
    "tree_dfs_pattern" -> pathSumTabs()
    "multi_source_bfs_pattern" -> multiSourceTabs()
    "game_theory_dp_pattern" -> gameTheoryTabs()
    "matrix_islands_pattern" -> islandsTabs()
    "dag_dp_pattern" -> dagDpTabs()
    "graph_coloring_pattern" -> twoColourTabs()
    "topological_sort_pattern" -> topoTabs()
    "shortest_path_pattern" -> weightedPathTabs()
    "state_machine_dp_pattern" -> stateMachineTabs()
    "grid_dp_pattern" -> gridDpTabs()
    "interval_dp_pattern" -> intervalDpTabs()
    "matrix_transform_pattern" -> matrixRotateTabs()
    "bit_manipulation_pattern" -> xorSplitTabs()
    "prefix_2d_pattern" -> prefix2dTabs()
    "bit_trie_pattern" -> bitTrieTabs()
    "composite_design_pattern" -> pairedDesignTabs()
    "kadanes_algorithm" -> kadaneTabs()
    "monte_carlo_method" -> monteCarloTabs()
    "reservoir_sampling" -> reservoirTabs()
    "mos_algorithm" -> mosTabs()
    "difference_array" -> differenceTabs()
    else -> listOf(TTab("Expand centres", expandFrames("abacabad"), PalindromeLegend), TTab("Manacher", manacherFrames("abacabad"), ManacherLegend))
}

// Also checks every row fits the frame's column grid, which the renderer assumes.
internal fun textStoryFrameCount(topicId: String): Int = textTabs(topicId).sumOf { tab ->
    tab.frames.forEach { f -> f.rows.filter { !it.spread }.forEach { require(it.offset + it.cells.size <= f.columns) { "$topicId: row ${it.label} overflows" } } }
    tab.frames.size
}

@Composable
internal fun TextStorySection(topicId: String) {
    val param = remember(topicId) { textParam(topicId) }
    var value by rememberSaveable(topicId) { mutableIntStateOf(param?.start ?: 0) }
    val tabs = remember(topicId, value) { param?.build?.invoke(value) ?: textTabs(topicId) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(0) }
    if (tab > tabs.lastIndex) tab = 0
    val frames = tabs[tab].frames
    val playback = rememberPlaybackState(key = Triple(topicId, tab, value), stepCount = frames.size, initialSpeedMs = 1000f)
    remember(playback) { if (tabs[tab].start > 0) playback.index = tabs[tab].start; true }
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (tabs.size > 1) LabSegments(tabs.map { it.label }, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
                if (param != null) {
                    val at = param.values.indexOf(value)
                    Box(Modifier.padding(bottom = 12.dp)) {
                        StoryStepperRow(
                            StoryStepper(param.label(value), value, at > 0, at < param.values.lastIndex) { d ->
                                value = param.values[(at + d).coerceIn(0, param.values.lastIndex)]
                            },
                        )
                    }
                }
                tabs[tab].stepper?.let { (label, values) ->
                    val i = playback.index.coerceIn(0, frames.lastIndex)
                    Box(Modifier.padding(bottom = 12.dp)) {
                        StoryStepperRow(StoryStepper(label, values[i], i > 0, i < frames.lastIndex) { playback.jump(i + it) })
                    }
                }
                if (frame.listFirst && frame.list.isNotEmpty()) TListView(frame.list, Modifier.padding(bottom = 14.dp), frame.listTitle)
                frame.bars?.let {
                    TBarsView(it, Modifier.fillMaxWidth().height(190.dp).background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)))
                }
                frame.tree?.let {
                    TTreeView(
                        it,
                        Modifier.padding(bottom = if (frame.rows.isEmpty()) 0.dp else 14.dp).fillMaxWidth().height(tTreeHeight(it))
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    )
                }
                if (frame.gantt.isNotEmpty()) {
                    TGanttView(
                        frame.gantt,
                        Modifier.fillMaxWidth().height((frame.gantt.size * 68 + 8).dp).background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    )
                }
                frame.graph?.let {
                    TGraphView(
                        it,
                        Modifier.padding(bottom = if (frame.rows.isEmpty()) 0.dp else 14.dp).fillMaxWidth().height(220.dp)
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    )
                }
                frame.topHeading?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (frame.topRows.isNotEmpty()) {
                    Box(Modifier.padding(bottom = 12.dp)) {
                        TGrid(TFrame(frame.topColumns, frame.topRows, "", "", headers = frame.topHeaders, noHeader = frame.topColumns == 0))
                    }
                }
                frame.heading?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (frame.scatter.isNotEmpty()) {
                    TScatterView(
                        frame.scatter,
                        Modifier.fillMaxWidth().height(250.dp).background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    )
                }
                if (frame.timelineFirst) frame.timeline?.let {
                    TTimelineView(
                        it,
                        Modifier.padding(bottom = 12.dp).fillMaxWidth()
                            .height((it.bars.size * 24 + (if (it.output.isEmpty()) 0 else 36) + 44).dp)
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    )
                }
                if (frame.rows.isNotEmpty()) Box(Modifier.padding(top = if (frame.bars == null) 0.dp else 14.dp)) { TGrid(frame) }
                frame.chain?.let {
                    TChainView(
                        it,
                        Modifier.padding(top = if (frame.rows.isEmpty()) 0.dp else 12.dp).fillMaxWidth().height(150.dp)
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    )
                }
                if (!frame.timelineFirst) frame.timeline?.let {
                    TTimelineView(
                        it,
                        Modifier.padding(top = if (frame.rows.isEmpty()) 0.dp else 12.dp).fillMaxWidth()
                            .height((it.bars.size * 24 + (if (it.output.isEmpty()) 0 else 36) + 44).dp)
                            .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    )
                }
                if (!frame.listFirst && frame.list.isNotEmpty()) TListView(frame.list, Modifier.padding(top = 14.dp), frame.listTitle)
                if (frame.formula.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(SimColors.Tint, RoundedCornerShape(10.dp))
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        frame.formula.forEach { line ->
                            Text(storyAnnotated(line), fontFamily = IBMPlexMono, fontSize = 14.sp, color = onSurface.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                        }
                    }
                }
                if (frame.formulaRows.isNotEmpty()) StoryFormulaRows(frame.formulaRows, Modifier.padding(top = 8.dp))
                if (frame.flow.isNotEmpty()) {
                    Column(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        frame.flow.forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                line.forEach { (t, chip) ->
                                    if (chip) {
                                        Text(
                                            t, fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onSurface, maxLines = 1, softWrap = false,
                                            modifier = Modifier.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                        )
                                    } else {
                                        Text(t, fontFamily = IBMPlexMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
                StoryLegendRow(tabs[tab].legend, Modifier.padding(top = 14.dp))
            }
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

@Composable
private fun tColors(tone: TTone): Pair<Color, Color> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val soft = if (LocalDarkTheme.current) 0.22f else 0.16f
    return when (tone) {
        TTone.Idle -> muted.copy(alpha = 0.2f) to MaterialTheme.colorScheme.onSurface
        TTone.Active -> SimColors.Active to Color(0xFF1F1A0A)
        TTone.Blue -> SimColors.Blue to Color.White
        TTone.Matched, TTone.Mirror, TTone.GhostMatched -> SimColors.Green.copy(alpha = soft) to StoryTone.Done.ink()
        TTone.Mismatch -> SimColors.Red.copy(alpha = 0.16f) to StoryTone.Warn.ink()
        TTone.Answer -> SimColors.Answer to Color.White
        TTone.Pending -> muted.copy(alpha = 0.08f) to muted.copy(alpha = 0.55f)
        TTone.Ghost -> Color.Transparent to muted.copy(alpha = 0.6f)
        TTone.Struck -> muted.copy(alpha = 0.06f) to muted.copy(alpha = 0.45f)
        TTone.Outline -> SimColors.Blue.copy(alpha = 0.28f) to MaterialTheme.colorScheme.onSurface
        TTone.Green -> SimColors.Green to Color.White
        TTone.Stack -> muted.copy(alpha = 0.18f) to MaterialTheme.colorScheme.onSurface
        TTone.Memo -> SimColors.Answer.copy(alpha = 0.14f) to Color(0xFFB9A5FF)
        TTone.Frontier -> muted.copy(alpha = 0.06f) to MaterialTheme.colorScheme.onSurface
        TTone.Rose -> RoseColor to Color.White
        TTone.Plain -> Color.Transparent to muted
    }
}

@Composable
private fun tTagInk(tone: TTone): Color = when (tone) {
    TTone.Active -> StoryTone.Active.ink()
    TTone.Blue -> StoryTone.Path.ink()
    TTone.Matched, TTone.Mirror, TTone.Green -> StoryTone.Done.ink()
    TTone.Mismatch -> StoryTone.Warn.ink()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun TGrid(frame: TFrame) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hasLabels = frame.rows.any { it.label.isNotEmpty() }
    val gutter = if (hasLabels) 40.dp else 0.dp
    val gap = if (frame.columns > 11) 3.dp else 5.dp
    val height = when {
        frame.columns > 11 -> 30.dp
        frame.columns > 9 -> 36.dp
        else -> 42.dp
    }
    val font = when {
        frame.columns > 11 -> 12.sp
        frame.columns > 9 -> 14.sp
        else -> 16.sp
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!frame.noHeader) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            if (hasLabels) Spacer(Modifier.width(gutter))
            for (c in 0 until frame.columns) {
                val tone = frame.lit[c]
                Text(
                    frame.headers?.get(c) ?: "$c", fontFamily = IBMPlexMono, fontSize = if (frame.columns > 11) 10.sp else 12.sp,
                    fontWeight = if (tone == null) FontWeight.Normal else FontWeight.Bold,
                    color = tone?.let { tTagInk(it) } ?: muted,
                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
                )
            }
        }
        frame.rows.forEach { row ->
            if (row.split == null) row.title?.let { title ->
                Text(
                    title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
                    color = muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
            val split = row.split
            if (split != null) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.cells.indices.forEach { c ->
                        if (c == split && c > 0) Spacer(Modifier.width(if (row.divider) 7.5.dp else 8.dp))
                        Box(Modifier.weight(1f)) {
                            val t = if (c == 0) row.title else if (c == split) row.splitTitle else null
                            if (t != null) {
                                Text(
                                    t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = muted,
                                    maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.cells.forEachIndexed { c, cell ->
                        if (c == split && c > 0) {
                            if (row.divider) Box(Modifier.padding(horizontal = 3.dp).width(1.5.dp).height(30.dp).background(muted.copy(alpha = 0.4f)).align(Alignment.CenterVertically))
                            else Spacer(Modifier.width(8.dp))
                        }
                        TCellView(cell, 44.dp, 15.sp, Modifier.weight(1f))
                    }
                }
            } else if (row.spread) {
                val many = row.compact
                val spreadGap = if (many) 4.dp else 6.dp
                Row(horizontalArrangement = Arrangement.spacedBy(spreadGap)) {
                    row.cells.forEachIndexed { c, it ->
                        if (c > 0) row.joiner?.let { j -> Text(j, fontFamily = IBMPlexMono, fontSize = 13.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.width(12.dp).align(Alignment.CenterVertically)) }
                        TCellView(it, if (many) 32.dp else if (it.top == null) 44.dp else 50.dp, if (many) 12.sp else 15.sp, Modifier.weight(1f))
                    }
                }
                if (row.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spreadGap)) {
                        row.cells.indices.forEach { c ->
                            val tag = row.tags[c]
                            Text(
                                tag?.first ?: " ", fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = tag?.let { tTagInk(it.second) } ?: Color.Transparent, textAlign = TextAlign.Center,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    if (hasLabels) {
                        Text(
                            row.label, fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = if (row.labelTone == null) FontWeight.Normal else FontWeight.Bold,
                            color = row.labelTone?.let { tTagInk(it) } ?: muted, maxLines = 1, modifier = Modifier.width(gutter),
                        )
                    }
                    for (c in 0 until frame.columns) {
                        val cell = row.cells.getOrNull(c - row.offset)
                        if (cell == null || c < row.offset) Spacer(Modifier.weight(1f).height(height)) else TCellView(cell, height, font, Modifier.weight(1f))
                    }
                }
                if (row.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        if (hasLabels) Spacer(Modifier.width(gutter))
                        for (c in 0 until frame.columns) {
                            val tag = row.tags[c]
                            Text(
                                tag?.first ?: " ", fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = tag?.let { tTagInk(it.second) } ?: Color.Transparent, textAlign = TextAlign.Center,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TCellView(cell: TCell, height: Dp, font: TextUnit, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (fill, ink) = tColors(cell.tone)
    val shape = RoundedCornerShape(7.dp)
    val outline = when (cell.tone) {
        TTone.Mismatch -> SimColors.Red
        TTone.Mirror, TTone.GhostMatched -> SimColors.Green
        TTone.Ghost -> muted.copy(alpha = 0.35f)
        TTone.Outline, TTone.Frontier -> SimColors.Blue
        else -> null
    }
    Box(
        modifier = modifier
            .height(height)
            .background(fill, shape)
            .then(if (outline != null) Modifier.border(1.5.dp, outline, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            cell.top?.let { Text(it, fontFamily = IBMPlexMono, fontSize = 11.sp, color = ink.copy(alpha = 0.8f), maxLines = 1) }
            Text(
                cell.text, fontFamily = IBMPlexMono, fontSize = font, fontWeight = FontWeight.Bold, color = ink, maxLines = 1,
                textDecoration = if (cell.tone == TTone.Struck) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
        }
    }
}

@Composable
private fun TListView(rows: List<TListRow>, modifier: Modifier = Modifier, title: String? = null) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = LocalDarkTheme.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        title?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = muted, modifier = Modifier.padding(bottom = 1.dp))
        }
        rows.forEach { row ->
            val (fill, ink) = when (row.tone) {
                TTone.Active -> SimColors.Active to Color(0xFF1F1A0A)
                TTone.Matched -> SimColors.Green.copy(alpha = if (dark) 0.22f else 0.16f) to StoryTone.Done.ink()
                TTone.Outline -> SimColors.Active.copy(alpha = 0.1f) to StoryTone.Active.ink()
                else -> muted.copy(alpha = 0.06f) to muted.copy(alpha = 0.55f)
            }
            // An empty value (the rotated-search probes) drops that column so the text gets the room.
            val bare = row.value.isEmpty()
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).background(fill, RoundedCornerShape(8.dp))
                    .then(if (row.tone == TTone.Outline) Modifier.border(1.5.dp, SimColors.Active, RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.name, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.width(if (bare) 16.dp else 30.dp))
                Text(
                    row.range, fontFamily = IBMPlexMono, fontSize = 14.sp, color = ink,
                    maxLines = if (bare) 1 else Int.MAX_VALUE, overflow = if (bare) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                Text(row.note, fontFamily = IBMPlexMono, fontSize = 13.sp, color = ink.copy(alpha = 0.85f), softWrap = !bare)
                if (!bare) {
                    Text(
                        row.value, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 10.dp).width(30.dp),
                    )
                }
            }
        }
    }
}

/** Samples in the unit square, blue inside the quarter circle and grey outside it, with the arc dashed. */
@Composable
private fun TScatterView(points: List<Pair<Double, Double>>, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted)
    androidx.compose.foundation.Canvas(modifier) {
        val side = minOf(size.width - 60.dp.toPx(), size.height - 36.dp.toPx())
        val ox = (size.width - side) / 2
        val oy = (size.height + side) / 2
        fun at(x: Double, y: Double) = androidx.compose.ui.geometry.Offset((ox + x * side).toFloat(), (oy - y * side).toFloat())
        drawRect(
            muted.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(ox, oy - side), androidx.compose.ui.geometry.Size(side, side),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
        )
        val dot = when {
            points.size > 1500 -> 1.6.dp.toPx()
            points.size > 400 -> 2.4.dp.toPx()
            else -> 3.4.dp.toPx()
        }
        points.forEach { (x, y) -> drawCircle(if (x * x + y * y <= 1) SimColors.Blue else muted.copy(alpha = 0.55f), dot, at(x, y)) }
        drawArc(
            SimColors.Answer, startAngle = -90f, sweepAngle = 90f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(ox - side, oy - side), size = androidx.compose.ui.geometry.Size(2 * side, 2 * side),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            ),
        )
        listOf(
            "0" to androidx.compose.ui.geometry.Offset(ox - 14.dp.toPx(), oy - 12.dp.toPx()),
            "1" to androidx.compose.ui.geometry.Offset(ox - 14.dp.toPx(), oy - side - 4.dp.toPx()),
            "1" to androidx.compose.ui.geometry.Offset(ox + side - 3.dp.toPx(), oy + 3.dp.toPx()),
        ).forEach { (t, p) -> drawText(measurer.measure(t, labelStyle), topLeft = p) }
    }
}

// ── Array technique storyboards ──────────────────────────────────────────────
// Two pointers, sliding window, prefix sums, Kadane, Monte Carlo, reservoir sampling, Mo's algorithm and
// the difference array, on the same rows-of-cells renderer.

private val TextGrey = Color.Gray.copy(alpha = 0.4f)

private fun twoPointerTabs(): List<TTab> {
    val a = listOf(1, 3, 4, 6, 8, 10, 13)
    val target = 18
    val n = a.size
    var lo = 0
    var hi = n - 1
    val trace = mutableListOf<Triple<Int, Int, Int>>()
    while (lo < hi) {
        val sum = a[lo] + a[hi]
        trace += Triple(lo, hi, sum)
        if (sum == target) break
        if (sum < target) lo++ else hi--
    }
    val found = trace.last()
    val tried = mutableListOf<Triple<String, String, TTone>>()
    fun row(l: Int, h: Int, done: Boolean = false) = TRow(
        a.mapIndexed { i, v ->
            TCell("$v", if (i == l || i == h) (if (done) TTone.Answer else TTone.Active) else if (i < l || i > h) TTone.Struck else TTone.Idle)
        },
        tags = mapOf(l to ("lo" to TTone.Active), h to ("hi" to TTone.Active)),
    )
    fun tiles() = TRow(tried.map { TCell(it.second, it.third, it.first) }, title = "PAIRS TRIED · TARGET $target", spread = true)
    val frames = mutableListOf(
        TFrame(
            n, listOf(row(0, n - 1)),
            "Find two values that add to $target, starting from both ends of the sorted array.",
            "Sorting is what lets one comparison decide which pointer to move.",
            formula = listOf("sorted, so a bigger left or a smaller right moves the sum"),
            chips = listOf(StoryChip("lo", "0"), StoryChip("hi", "${n - 1}")),
        ),
    )
    trace.forEachIndexed { k, (l, h, sum) ->
        for (t in tried.indices) tried[t] = Triple(tried[t].first, tried[t].second, TTone.Matched)
        val cmp = if (sum < target) "<" else if (sum > target) ">" else "="
        val hit = sum == target
        tried += Triple("${a[l]}+${a[h]}", "$sum $cmp", if (hit) TTone.Answer else TTone.Active)
        val left = trace.size - 1 - k
        frames += TFrame(
            n, listOf(row(l, h, hit), tiles()),
            when {
                hit -> "${a[l]} + ${a[h]} = {v:$sum}: the pair is found."
                sum < target -> "{$sum} is under $target. Only a bigger left value helps, so lo moves right."
                else -> "{$sum} is over $target. Only a smaller right value helps, so hi moves left."
            },
            if (hit) "${trace.size} sums checked instead of all ${n * (n - 1) / 2} pairs: O(n) after sorting."
            else "Each step rules out one element for good, so the scan is O(n). The pair ${a[found.first]} + ${a[found.second]} is found $left step${if (left == 1) "" else "s"} later.",
            lit = mapOf(l to TTone.Active, h to TTone.Active),
            formula = listOf(
                if (hit) "${a[l]} + ${a[h]} = {v:$sum} = $target → found"
                else "${a[l]} + ${a[h]} = {$sum} $cmp $target → ${if (sum < target) "lo moves right" else "hi moves left"}",
            ),
            chips = listOf(StoryChip("lo", "$l"), StoryChip("hi", "$h")) + if (hit) listOf(StoryChip("pair", "${a[l]} + ${a[h]}", StoryTone.Answer)) else emptyList(),
        )
    }
    return listOf(
        TTab(
            "Pair sum", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "lo and hi"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Tried"),
                Triple(TextGrey, SwatchStyle.Fill, "Ruled out"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Found"),
            ),
        ),
    )
}

private fun slidingWindowTabs(): List<TTab> {
    val a = listOf(2, 1, 5, 1, 3, 2, 7, 1)
    val n = a.size
    val k = 3
    val windows = n - k + 1
    val sums = mutableListOf<Int>()
    var best = Int.MIN_VALUE
    fun tiles(current: Int?) = TRow(
        (0 until windows).map { s ->
            if (current == null || s > current) TCell("·", TTone.Pending, "[$s,${s + k - 1}]")
            else TCell("${sums[s]}", if (s == current) TTone.Active else TTone.Matched, "[$s,${s + k - 1}]")
        },
        title = "WINDOW SUMS", spread = true,
    )
    val fixed = mutableListOf(
        TFrame(
            n, listOf(TRow(a.map { TCell("$it", TTone.Idle) }), tiles(null)),
            "Find the largest sum of $k consecutive values.",
            "Recomputing each window from scratch costs k per window. Sliding reuses the last sum.",
            formula = listOf("window size k = $k"), chips = listOf(StoryChip("k", "$k")),
        ),
    )
    for (s in 0 until windows) {
        val sum = if (s == 0) a.take(k).sum() else sums[s - 1] - a[s - 1] + a[s + k - 1]
        sums += sum
        val newBest = sum > best
        if (newBest) best = sum
        val tags = if (s > 0) mapOf(s - 1 to ("out" to TTone.Mismatch), s + k - 1 to ("in" to TTone.Active), s to ("start" to TTone.Blue)) else emptyMap()
        val row = TRow(
            a.mapIndexed { i, v ->
                TCell(
                    "$v",
                    when {
                        s > 0 && i == s - 1 -> TTone.Mismatch
                        s > 0 && i == s + k - 1 -> TTone.Active
                        i >= s && i < s + k -> TTone.Blue
                        else -> TTone.Idle
                    },
                )
            },
            tags = tags,
        )
        val mark = if (newBest) "{v:$sum}" else "{$sum}"
        fixed += TFrame(
            n, listOf(row, tiles(s)),
            when {
                s == 0 -> "The first window [0, ${k - 1}] sums to {$sum}."
                newBest -> "Drop ${a[s - 1]}, add ${a[s + k - 1]}: the window sum jumps to {v:$sum}, a new best."
                else -> "Drop ${a[s - 1]}, add ${a[s + k - 1]}: $sum, and the best stays {v:$best}."
            },
            if (s == 0) "Build the first window once; every later one reuses it." else "Each slide changes two elements, so all windows cost O(n), not O(n·k).",
            lit = if (s > 0) mapOf(s - 1 to TTone.Mismatch, s + k - 1 to TTone.Active) else emptyMap(),
            formula = listOf(
                if (s == 0) a.take(k).joinToString(" + ") + " = $mark"
                else "${sums[s - 1]} − {w:a[${s - 1}]} + {a[${s + k - 1}]} = ${sums[s - 1]} − ${a[s - 1]} + ${a[s + k - 1]} = $mark",
            ),
            chips = listOf(StoryChip("sum", "$sum"), StoryChip("best", "$best", StoryTone.Answer)),
        )
    }
    // Variable size: shortest window with sum ≥ target.
    val target = 8
    val variable = mutableListOf(
        TFrame(
            n, listOf(TRow(a.map { TCell("$it", TTone.Idle) })),
            "Now the window grows and shrinks: find the shortest one summing to at least $target.",
            "R grows the window until it is big enough; L then shrinks it while it still is.",
            formula = listOf("shortest window with sum ≥ $target"), chips = listOf(StoryChip("target", "$target")),
        ),
    )
    var l = 0
    var sum = 0
    var shortest = Int.MAX_VALUE
    var shortRange = 0 to 0
    for (r in 0 until n) {
        sum += a[r]
        fun row(entering: Int?, leaving: Int?): TRow {
            val tags = mutableMapOf<Int, Pair<String, TTone>>()
            tags[l] = "L" to TTone.Blue
            tags[r] = "R" to TTone.Blue
            entering?.let { tags[it] = "in" to TTone.Active }
            leaving?.let { tags[it] = "out" to TTone.Mismatch }
            return TRow(
                a.mapIndexed { i, v ->
                    TCell(
                        "$v",
                        when (i) {
                            entering -> TTone.Active
                            leaving -> TTone.Mismatch
                            in l..r -> TTone.Blue
                            else -> TTone.Idle
                        },
                    )
                },
                tags = tags,
            )
        }
        variable += TFrame(
            n, listOf(row(r, null)),
            if (sum >= target) "Adding ${a[r]} brings the sum to {$sum}, enough." else "Adding ${a[r]} brings the sum to {$sum}, still under $target.",
            "R only moves right, so each element enters the window once.",
            lit = mapOf(r to TTone.Active), formula = listOf("sum + a[$r] = ${sum - a[r]} + ${a[r]} = {$sum}"),
            chips = listOf(StoryChip("sum", "$sum"), StoryChip("shortest", if (shortest == Int.MAX_VALUE) "–" else "$shortest", StoryTone.Answer)),
        )
        while (sum >= target) {
            val len = r - l + 1
            val better = len < shortest
            if (better) {
                shortest = len
                shortRange = l to r
            }
            val gone = a[l]
            sum -= gone
            variable += TFrame(
                n, listOf(row(null, l)),
                if (better) "[$l, $r] reaches $target in {v:$len} values, the shortest yet. Try dropping $gone." else "[$l, $r] still works but is not shorter. Drop $gone.",
                "L also only moves right, so the whole scan is O(n) even though the window changes size.",
                lit = mapOf(l to TTone.Mismatch),
                formula = listOf("[$l, $r] has length ${if (better) "{v:$len}" else "$len"}; drop {w:a[$l]} → $sum"),
                chips = listOf(StoryChip("sum", "$sum"), StoryChip("shortest", "$shortest", StoryTone.Answer)),
            )
            l++
        }
    }
    variable += TFrame(
        n, listOf(TRow(a.mapIndexed { i, v -> TCell("$v", if (i in shortRange.first..shortRange.second) TTone.Answer else TTone.Idle) })),
        "The shortest window with sum ≥ $target is [${shortRange.first}, ${shortRange.second}], length {v:$shortest}.",
        "Two pointers that never move back: O(n) for a question that looks like it needs every subarray.",
        formula = listOf("shortest = [${shortRange.first}, ${shortRange.second}], length {v:$shortest}"),
        chips = listOf(StoryChip("shortest", "$shortest", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Fixed size", fixed,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "In window"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Entering"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Leaving"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "New best"),
            ),
        ),
        TTab(
            "Variable size", variable,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "In window"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Entering"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Leaving"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Shortest"),
            ),
        ),
    )
}

private fun prefixSumTabs(): List<TTab> {
    val a = listOf(3, 1, 4, 1, 5, 9, 2, 6)
    val n = a.size
    val p = a.runningReduce { acc, v -> acc + v }
    val build = mutableListOf<TFrame>()
    for (i in 0 until n) {
        build += TFrame(
            n,
            listOf(
                TRow(a.mapIndexed { k, v -> TCell("$v", if (k == i) TTone.Active else TTone.Idle) }, "a"),
                TRow(
                    (0 until n).map { k ->
                        if (k > i) TCell("·", TTone.Pending) else TCell("${p[k]}", if (k == i) TTone.Active else if (k == i - 1) TTone.Blue else TTone.Idle)
                    },
                    "P",
                ),
            ),
            if (i == 0) "P[0] is just a[0] = {${p[0]}}." else "P[$i] adds a[$i] to the running total: {${p[i]}}.",
            "P[i] is the sum of a[0] through a[i], built in one pass of n additions.",
            lit = mapOf(i to TTone.Active),
            formula = listOf(if (i == 0) "P[0] = a[0] = {${p[0]}}" else "P[$i] = P[${i - 1}] + a[$i] = {p:${p[i - 1]}} + ${a[i]} = {${p[i]}}"),
            chips = listOf(StoryChip("i", "$i"), StoryChip("P[$i]", "${p[i]}")),
        )
    }
    build += TFrame(
        n, listOf(TRow(a.map { TCell("$it", TTone.Idle) }, "a"), TRow(p.map { TCell("$it", TTone.Answer) }, "P")),
        "P is built: {v:$n} additions, once.", "Every range sum is now two lookups and a subtraction.",
        formula = listOf("P = {v:" + p.joinToString(" ") + "}"), chips = listOf(StoryChip("cost", "O(n)", StoryTone.Answer)),
    )
    val queries = listOf(2 to 5, 0 to 3, 4 to 7)
    val query = mutableListOf(
        TFrame(
            n, listOf(TRow(a.map { TCell("$it", TTone.Idle) }, "a"), TRow(p.map { TCell("$it", TTone.Idle) }, "P")),
            "A range sum is the total up to R minus the total before L.", "When L is 0 there is nothing to subtract.",
            formula = listOf("sum(L..R) = P[R] − P[L−1]"), chips = listOf(StoryChip("queries", "${queries.size}")),
        ),
    )
    for ((l, r) in queries) {
        val v = p[r] - if (l > 0) p[l - 1] else 0
        val tags = mutableMapOf(r to ("R" to TTone.Active))
        if (l > 0) tags[l - 1] = "L−1" to TTone.Active
        query += TFrame(
            n,
            listOf(
                TRow(a.mapIndexed { k, x -> TCell("$x", if (k in l..r) TTone.Blue else TTone.Idle) }, "a"),
                TRow(p.mapIndexed { k, x -> TCell("$x", if (k == r || (l > 0 && k == l - 1)) TTone.Active else TTone.Idle) }, "P", tags = tags),
            ),
            if (l > 0) "sum($l..$r) = P[$r] − P[${l - 1}] = {v:$v}." else "sum(0..$r) is just P[$r] = {v:$v}.",
            "P is built in one pass. After that, every range sum is a single subtraction.",
            formula = listOf(if (l > 0) "P[$r] − P[${l - 1}] = {${p[r]}} − {${p[l - 1]}} = {v:$v}" else "P[$r] = {v:$v} (L = 0)"),
            formulaRows = listOf(StoryFormulaRow("check", a.subList(l, r + 1).joinToString(" + ") + " = $v")),
            chips = listOf(StoryChip("L", "$l"), StoryChip("R", "$r"), StoryChip("sum", "$v", StoryTone.Answer)),
        )
    }
    return listOf(
        TTab(
            "Build", build,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current"), Triple(SimColors.Blue, SwatchStyle.Fill, "Read"), Triple(SimColors.Answer, SwatchStyle.Fill, "Built")),
        ),
        TTab(
            "Query", query,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Query range"),
                Triple(SimColors.Active, SwatchStyle.Fill, "P values read"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"),
            ),
        ),
    )
}

private fun kadaneTabs(): List<TTab> {
    val a = listOf(-2, 1, -3, 4, -1, 2, 1, -5, 4)
    val n = a.size
    fun fmt(v: Int) = if (v < 0) "−${-v}" else "$v"
    // The answer, known up front so every step's body can point at it.
    var run = 0
    var top = Int.MIN_VALUE
    var topRange = 0 to 0
    var from = 0
    a.forEachIndexed { i, v ->
        if (i == 0 || v > run + v) {
            run = v
            from = i
        } else {
            run += v
        }
        if (run > top) {
            top = run
            topRange = from to i
        }
    }
    val cur = mutableListOf<Int>()
    var start = 0
    var best = Int.MIN_VALUE
    var bestRange = 0 to 0
    val frames = mutableListOf<TFrame>()
    for (i in 0 until n) {
        val prev = cur.lastOrNull()
        val extend = (prev ?: 0) + a[i]
        val restart = prev == null || a[i] > extend
        if (restart) start = i
        val c = if (restart) a[i] else extend
        cur += c
        if (c > best) {
            best = c
            bestRange = start to i
        }
        val aRow = TRow(a.mapIndexed { k, v -> TCell(fmt(v), if (k == i) TTone.Active else if (k < start) TTone.Struck else TTone.Idle) }, "a")
        val curRow = TRow(
            (0 until n).map { k -> if (k > i) TCell("·", TTone.Pending) else TCell(fmt(cur[k]), if (k == i) TTone.Active else TTone.Idle) },
            "cur", tags = mapOf(i to ("i" to TTone.Active)),
        )
        val formula = if (prev == null) "cur = a[0] = {${fmt(c)}}"
        else "cur = max(${fmt(a[i])}, ${if (prev < 0) "{w:${fmt(prev)}}" else fmt(prev)} + ${fmt(a[i])}) = {${fmt(c)}} → ${if (restart) "restart" else "extend"}"
        frames += TFrame(
            n, listOf(aRow, curRow),
            when {
                prev == null -> "The first run is just a[0] = {${fmt(c)}}."
                restart -> "Carrying ${fmt(prev)} would only lower the sum, so the subarray restarts at i = $i."
                c == best && bestRange.second == i -> "Adding ${fmt(a[i])} keeps the run going: cur = {${fmt(c)}}, a new best."
                else -> "Adding ${fmt(a[i])} still beats starting over, so the run extends: cur = {${fmt(c)}}."
            },
            "cur is the best sum ending exactly at i. The final best is ${fmt(top)}, from [${topRange.first}, ${topRange.second}].",
            lit = mapOf(i to TTone.Active), formula = listOf(formula),
            chips = listOf(StoryChip("cur", fmt(c)), StoryChip("best", fmt(best))),
        )
    }
    frames += TFrame(
        n,
        listOf(
            TRow(a.mapIndexed { k, v -> TCell(fmt(v), if (k in bestRange.first..bestRange.second) TTone.Answer else TTone.Idle) }, "a"),
            TRow(cur.map { TCell(fmt(it), TTone.Idle) }, "cur"),
        ),
        "The maximum subarray is [${bestRange.first}, ${bestRange.second}], summing to {v:${fmt(best)}}.",
        "One pass, two variables: O(n) time and O(1) space.",
        formula = listOf("best = {v:${fmt(best)}}, from [${bestRange.first}, ${bestRange.second}]"), chips = listOf(StoryChip("best", fmt(best), StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Kadane", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current"), Triple(TextGrey, SwatchStyle.Fill, "Dropped prefix"), Triple(SimColors.Answer, SwatchStyle.Fill, "Best")),
        ),
    )
}

private fun monteCarloTabs(): List<TTab> {
    // A seed whose 300-sample estimate lands on the classic 237 inside; it converges to 3.14 by 4000.
    var seed = 509L
    fun rand(): Double {
        seed = (seed * 1103515245L + 12345L) % 2147483648L
        return seed / 2147483648.0
    }
    val counts = listOf(25, 50, 100, 300, 600, 1000, 2000, 4000)
    val pts = mutableListOf<Pair<Double, Double>>()
    val frames = counts.map { n ->
        while (pts.size < n) pts += rand() to rand()
        val inside = pts.count { (x, y) -> x * x + y * y <= 1 }
        val est = 4.0 * inside / n
        val err = kotlin.math.abs(est - Math.PI)
        val e4 = String.format(java.util.Locale.US, "%.4f", est)
        val e3 = String.format(java.util.Locale.US, "%.3f", est)
        TFrame(
            0, emptyList(),
            "$inside of $n samples land inside, so π ≈ {v:$e3}.",
            "The inside fraction estimates π/4. Error shrinks like 1/√n, so 4× the samples roughly halves it.",
            formula = listOf("4 × {p:$inside} / $n = {v:$e4}"),
            chips = listOf(StoryChip("π ≈", e3, StoryTone.Answer), StoryChip("error", String.format(java.util.Locale.US, "%.3f", err))),
            scatter = pts.toList(),
        )
    }
    return listOf(
        TTab(
            "π", frames,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Inside the arc"),
                Triple(Color.Gray.copy(alpha = 0.5f), SwatchStyle.Fill, "Outside"),
                Triple(SimColors.Answer, SwatchStyle.Dashed, "Quarter circle"),
            ),
            stepper = "Samples" to counts,
        ),
    )
}

private fun reservoirTabs(): List<TTab> {
    val stream = listOf(41, 17, 63, 8, 92, 25, 54, 39, 71, 6, 88, 30)
    val k = 3
    val n = stream.size
    // The draws of one seeded run, j = rand(0..i) for i ≥ k.
    val draws = mapOf(3 to 3, 4 to 1, 5 to 5, 6 to 0, 7 to 6, 8 to 2, 9 to 7, 10 to 9, 11 to 1)
    val slots = mutableListOf<Int>()
    val slotOwner = mutableListOf<Int>()
    val evicted = mutableSetOf<Int>()
    val skipped = mutableSetOf<Int>()
    fun streamRow(i: Int) = TRow(
        stream.mapIndexed { idx, v ->
            TCell(
                "$v",
                when {
                    idx == i -> TTone.Active
                    idx > i -> TTone.Idle
                    idx in evicted -> TTone.Mismatch
                    idx in skipped -> TTone.Pending
                    else -> TTone.Answer
                },
            )
        },
        tags = mapOf(i to ("i" to TTone.Active)),
    )
    val frames = mutableListOf(
        TFrame(
            n, listOf(TRow(stream.map { TCell("$it", TTone.Idle) }), TRow(List(k) { TCell("·", TTone.Pending) }, title = "RESERVOIR", spread = true)),
            "Keep $k items from a stream, each equally likely, without knowing how long it is.",
            "Only the reservoir is stored; each item is looked at once and then forgotten.",
            formula = listOf("keep a uniform sample of k = $k from a stream of unknown length"),
            chips = listOf(StoryChip("seen", "0"), StoryChip("k", "$k")),
        ),
    )
    for (i in 0 until n) {
        if (i < k) {
            slots += stream[i]
            slotOwner += i
            frames += TFrame(
                n,
                listOf(
                    streamRow(i),
                    TRow((0 until k).map { if (it < slots.size) TCell("${slots[it]}", if (it == i) TTone.Active else TTone.Answer) else TCell("·", TTone.Pending) }, title = "RESERVOIR", spread = true),
                ),
                "The first $k items fill the reservoir directly: slot $i = {${stream[i]}}.",
                "Until the reservoir is full there is nothing to choose.",
                lit = mapOf(i to TTone.Active), formula = listOf("i = $i < k → slot $i = {${stream[i]}}"),
                chips = listOf(StoryChip("seen", "${i + 1}"), StoryChip("k", "$k")),
            )
            continue
        }
        val j = draws.getValue(i)
        val kept = j < k
        val tiles = slots.map { TCell("$it", TTone.Answer) }.toMutableList()
        var old = 0
        if (kept) {
            old = slots[j]
            evicted += slotOwner[j]
            tiles[j] = TCell("$old → ${stream[i]}", TTone.Active)
            slots[j] = stream[i]
            slotOwner[j] = i
        } else {
            skipped += i
        }
        frames += TFrame(
            n, listOf(streamRow(i), TRow(tiles, title = "RESERVOIR", spread = true)),
            if (kept) "Item ${stream[i]} draws j = $j, so it evicts $old from slot $j." else "Item ${stream[i]} draws j = $j, not below $k, so it is skipped.",
            "Item i is kept with probability k/(i+1). That keeps every item seen so far equally likely.",
            lit = mapOf(i to TTone.Active),
            formula = listOf(if (kept) "j = rand(0…$i) = {$j} < $k → slot $j = ${stream[i]}" else "j = rand(0…$i) = {$j} ≥ $k → skip"),
            formulaRows = listOf(StoryFormulaRow("P(item $i kept)", "$k / ${i + 1}")),
            chips = listOf(StoryChip("seen", "${i + 1}"), StoryChip("k", "$k")),
        )
    }
    return listOf(
        TTab(
            "Algorithm R", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current item"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "In reservoir"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Evicted"),
                Triple(TextGrey, SwatchStyle.Fill, "Skipped"),
            ),
        ),
    )
}

private fun mosTabs(): List<TTab> {
    val a = listOf(4, 1, 7, 2, 9, 3, 6, 5, 8, 2, 4, 1)
    val n = a.size
    val block = 4
    val queries = listOf(1 to 3, 0 to 5, 4 to 7, 5 to 10, 8 to 11)
    val order = queries.indices.sortedWith(compareBy({ queries[it].first / block }, { queries[it].second }))
    val answers = MutableList<Int?>(queries.size) { null }
    var l = queries[order[0]].first
    var r = l - 1
    var sum = 0
    var moves = 0
    val frames = mutableListOf<TFrame>()
    val body = "Queries are sorted by block of L, then by R, so the window moves a little each time and is never rebuilt."
    fun list(current: Int?) = order.map { qi ->
        val (ql, qr) = queries[qi]
        TListRow(
            "Q${qi + 1}", "[$ql, $qr]", "block ${ql / block}", answers[qi]?.toString() ?: "·",
            if (qi == current) TTone.Active else if (answers[qi] != null) TTone.Matched else TTone.Pending,
        )
    }
    fun row(moved: Int?): TRow {
        val tags = mutableMapOf<Int, Pair<String, TTone>>()
        if (r >= l) {
            tags[l] = "L" to TTone.Blue
            tags[r] = "R" to (if (r == l) TTone.Blue else TTone.Active)
        }
        return TRow(a.mapIndexed { i, v -> TCell("$v", if (i == moved) TTone.Active else if (i in l..r) TTone.Blue else TTone.Idle) }, tags = tags)
    }
    frames += TFrame(
        n, listOf(row(null)), "Answer ${queries.size} range-sum queries offline by reordering them.", body,
        formula = listOf("sort queries by (block of L, R), block size $block"), chips = listOf(StoryChip("sum", "0"), StoryChip("moves", "0")), list = list(null),
    )
    order.forEachIndexed { pos, qi ->
        val (ql, qr) = queries[qi]
        var count = 0
        // The second query is shown one pointer move at a time, so the cost of a move is visible.
        fun moved(idx: Int, adds: Boolean, what: String) {
            count++
            moves++
            if (pos != 1) return
            val last = l == ql && r == qr
            if (last) answers[qi] = sum
            val before = if (adds) sum - a[idx] else sum + a[idx]
            frames += TFrame(
                n, listOf(row(idx)), what + if (last) " Q${qi + 1}'s sum is {$sum}." else " Running sum {$sum}.", body,
                lit = mapOf(idx to TTone.Active),
                formula = listOf(if (adds) "sum += a[$idx] → $before + ${a[idx]} = {$sum}" else "sum −= a[$idx] → $before − ${a[idx]} = {$sum}"),
                chips = listOf(StoryChip("sum", "$sum"), StoryChip("moves", "$moves")), list = list(qi),
            )
        }
        while (l > ql) { l--; sum += a[l]; moved(l, true, "L steps to $l and adds ${a[l]}.") }
        while (r < qr) { r++; sum += a[r]; moved(r, true, "R steps to $r and adds ${a[r]}.") }
        while (r > qr) { sum -= a[r]; r--; moved(r + 1, false, "R drops ${a[r + 1]}.") }
        while (l < ql) { sum -= a[l]; l++; moved(l - 1, false, "L drops ${a[l - 1]}.") }
        answers[qi] = sum
        if (pos != 1) {
            frames += TFrame(
                n, listOf(row(null)),
                if (pos == 0) "Q${qi + 1} builds the first window [$ql, $qr] in $count moves: {$sum}." else "Q${qi + 1} needs $count pointer moves from the last window: {$sum}.",
                body, formula = listOf("$count pointer moves → sum = {$sum}"),
                chips = listOf(StoryChip("sum", "$sum"), StoryChip("moves", "$moves")), list = list(qi),
            )
        }
    }
    frames += TFrame(
        n, listOf(row(null)), "All ${queries.size} queries answered with {v:$moves} pointer moves in total.",
        "Answering them in the given order would drag the window back and forth. The sort makes the total O((n + q)·√n).",
        formula = listOf("${queries.size} queries, {v:$moves} pointer moves"), chips = listOf(StoryChip("moves", "$moves", StoryTone.Answer)), list = list(null),
    )
    return listOf(
        TTab(
            "Queries", frames,
            listOf(Triple(SimColors.Blue, SwatchStyle.Fill, "Window"), Triple(SimColors.Active, SwatchStyle.Fill, "Pointer move"), Triple(SimColors.Green, SwatchStyle.Fill, "Answered")),
        ),
    )
}

private fun differenceTabs(): List<TTab> {
    val n = 8
    val updates = listOf(Triple(1, 4, 3), Triple(3, 6, 2), Triple(0, 2, -1))
    fun fmt(v: Int) = if (v < 0) "−${-v}" else "$v"
    fun sfmt(v: Int) = if (v < 0) "−${-v}" else "+$v"
    val d = MutableList(n) { 0 }
    val touched = mutableSetOf<Int>()
    fun list(current: Int?) = updates.mapIndexed { i, u ->
        TListRow(
            "U${i + 1}", "[${u.first}, ${u.second}]", "", sfmt(u.third),
            when {
                current == null || i < current -> TTone.Matched
                i == current -> TTone.Active
                else -> TTone.Pending
            },
        )
    }
    fun dRow(now: Set<Int>) = TRow(
        d.mapIndexed { i, v -> TCell(fmt(v), if (i in now) TTone.Active else if (i in touched && v != 0) TTone.Matched else TTone.Idle) }, "D",
    )
    val pendingA = TRow(List(n) { TCell("·", TTone.Pending) }, "a")
    val frames = mutableListOf(
        TFrame(
            n, listOf(dRow(emptySet()), pendingA),
            "Record each range update at its two ends instead of in every cell.", "D holds the changes; a is only rebuilt once, at the end.",
            formula = listOf("add v to a[l..r]: D[l] += v, D[r + 1] −= v"), chips = listOf(StoryChip("updates", "${updates.size}")),
            list = list(0), listFirst = true,
        ),
    )
    updates.forEachIndexed { i, (lo, hi, v) ->
        d[lo] += v
        val now = mutableSetOf(lo)
        var formula = "D[$lo] += ${fmt(v)}"
        if (hi + 1 < n) {
            d[hi + 1] -= v
            now += hi + 1
            formula += ", D[$hi + 1] −= ${fmt(v)}"
        }
        val span = hi - lo + 1
        frames += TFrame(
            n, listOf(dRow(now), pendingA),
            "Update ${i + 1} writes only " + now.sorted().joinToString(" and ") { "D[$it]" } + ", not the $span cells in between.",
            "Once all updates are in, one prefix-sum pass over D produces a.",
            lit = now.associateWith { TTone.Active }, formula = listOf(formula),
            chips = listOf(StoryChip("cells touched", "${now.size}"), StoryChip("naive", "$span")), list = list(i), listFirst = true,
        )
        touched += now
    }
    val built = d.runningReduce { acc, x -> acc + x }
    frames += TFrame(
        n, listOf(dRow(emptySet()), TRow(built.map { TCell(fmt(it), TTone.Answer) }, "a")),
        "One prefix-sum pass over D gives {v:a}.", "q range updates cost O(q + n) instead of O(q · n).",
        formula = listOf("a[i] = D[0] + … + D[i] → {v:" + built.joinToString(" ") { fmt(it) } + "}"),
        chips = listOf(StoryChip("writes", "${updates.size * 2}"), StoryChip("pass", "O(n)", StoryTone.Answer)), list = list(null), listFirst = true,
    )
    return listOf(
        TTab(
            "Updates", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Written now"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Earlier updates"),
                Triple(TextGrey, SwatchStyle.Fill, "Not built yet"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Built"),
            ),
        ),
    )
}

@Composable
private fun tStroke(tone: TTone): Color = when (tone) {
    TTone.Active, TTone.Stack -> SimColors.Active
    TTone.Blue, TTone.Outline -> SimColors.Blue
    TTone.Matched, TTone.Mirror, TTone.Green -> SimColors.Green
    TTone.Mismatch -> SimColors.Red
    TTone.Answer, TTone.Memo -> SimColors.Answer
    TTone.Rose -> RoseColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
}

@Composable
private fun TChainView(chain: TChain, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val surface = MaterialTheme.colorScheme.surface
    val strokes = TTone.entries.associateWith { tStroke(it) }
    val fills = TTone.entries.associateWith { tColors(it) }
    val inks = TTone.entries.associateWith { tTagInk(it) }
    val nodeStyle = androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val labelStyle = androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val smallStyle = androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    androidx.compose.foundation.Canvas(modifier) {
        val n = chain.nodes.size
        val pad = 12.dp.toPx()
        val slot = (size.width - 2 * pad) / n
        val w = minOf(46.dp.toPx(), slot * 0.72f)
        val h = 42.dp.toPx()
        val cy = 62.dp.toPx()
        fun cx(i: Int) = pad + slot * (i + 0.5f)
        fun arrowHead(tip: androidx.compose.ui.geometry.Offset, angle: Float, c: Color) {
            val len = 7.dp.toPx()
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - len * kotlin.math.cos(angle - 0.45f), tip.y - len * kotlin.math.sin(angle - 0.45f))
                lineTo(tip.x - len * kotlin.math.cos(angle + 0.45f), tip.y - len * kotlin.math.sin(angle + 0.45f))
                close()
            }
            drawPath(path, c)
        }
        fun straight(from: Int, to: Int, c: Color, dashed: Boolean) {
            val dir = if (to > from) 1f else -1f
            val a = androidx.compose.ui.geometry.Offset(cx(from) + dir * (w / 2 + 3.dp.toPx()), cy)
            val b = androidx.compose.ui.geometry.Offset(cx(to) - dir * (w / 2 + 3.dp.toPx()), cy)
            drawLine(
                c, a, b, if (dashed) 1.8.dp.toPx() else 1.5.dp.toPx(),
                pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
            arrowHead(b, if (dir > 0) 0f else Math.PI.toFloat(), c)
        }
        chain.cut?.let { (a, b) -> straight(a, b, SimColors.Red, true) }
        chain.next.forEachIndexed { i, target ->
            val j = target ?: return@forEachIndexed
            val special = chain.special[i]
            if (special == null && kotlin.math.abs(i - j) == 1) {
                straight(i, j, strokes.getValue(chain.arrowTones[i] ?: TTone.Idle), false)
                return@forEachIndexed
            }
            val c = strokes.getValue(special?.first ?: TTone.Idle)
            val a = androidx.compose.ui.geometry.Offset(cx(i), cy + h / 2 + 2.dp.toPx())
            val b = androidx.compose.ui.geometry.Offset(cx(j), cy + h / 2 + 2.dp.toPx())
            val ctrl = androidx.compose.ui.geometry.Offset((a.x + b.x) / 2, cy + h / 2 + 40.dp.toPx())
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(a.x, a.y); quadraticTo(ctrl.x, ctrl.y, b.x, b.y) }
            drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            arrowHead(b, kotlin.math.atan2(b.y - ctrl.y, b.x - ctrl.x), c)
            special?.second?.let { label ->
                val layout = measurer.measure(label, smallStyle.copy(color = inks.getValue(if (special.first == TTone.Answer) TTone.Blue else special.first)))
                val y = (a.y + 2 * ctrl.y + b.y) / 4 + 10.dp.toPx()
                drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(ctrl.x - layout.size.width / 2f, y - layout.size.height / 2f))
            }
        }
        chain.nodes.forEachIndexed { i, node ->
            val tl = androidx.compose.ui.geometry.Offset(cx(i) - w / 2, cy - h / 2)
            val sz = androidx.compose.ui.geometry.Size(w, h)
            val r = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            val (fill, ink) = fills.getValue(node.tone)
            drawRoundRect(surface, tl, sz, r)
            drawRoundRect(fill, tl, sz, r)
            if (node.tone == TTone.Outline) drawRoundRect(SimColors.Blue, tl, sz, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            if (node.tone == TTone.Mismatch) drawRoundRect(SimColors.Red, tl, sz, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            val text = measurer.measure(node.text, nodeStyle.copy(color = ink))
            drawText(text, topLeft = androidx.compose.ui.geometry.Offset(cx(i) - text.size.width / 2f, cy - text.size.height / 2f))
            chain.above[i]?.let { (t, tone) ->
                val layout = measurer.measure(t, labelStyle.copy(color = inks.getValue(tone)))
                drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(cx(i) - layout.size.width / 2f, tl.y - 12.dp.toPx() - layout.size.height / 2f))
            }
            chain.below[i]?.let { (t, tone) ->
                val layout = measurer.measure(t, smallStyle.copy(color = inks.getValue(tone)))
                drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(cx(i) - layout.size.width / 2f, tl.y + h + 12.dp.toPx() - layout.size.height / 2f))
            }
        }
    }
}

@Composable
private fun TTimelineView(timeline: TTimeline, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val strokes = TTone.entries.associateWith { tStroke(it) }
    val pathInk = if (timeline.markerTone == TTone.Active) StoryTone.Active.ink() else StoryTone.Path.ink()
    val barStyle = androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    androidx.compose.foundation.Canvas(modifier) {
        val pad = 20.dp.toPx()
        fun x(v: Double) = (pad + v / timeline.maxX * (size.width - 2 * pad)).toFloat()
        val top = 14.dp.toPx()
        val rowH = 24.dp.toPx()
        fun bar(b: TBar, y: Float) {
            val (fill, ink) = when (b.tone) {
                TTone.Idle -> muted.copy(alpha = 0.3f) to onSurface
                TTone.Mismatch -> SimColors.Red.copy(alpha = 0.45f) to Color.White
                TTone.Matched -> SimColors.Green to Color.White
                else -> strokes.getValue(b.tone) to if (b.tone == TTone.Active) Color(0xFF1F1A0A) else Color.White
            }
            val left = x(b.start)
            val width = maxOf(x(b.end) - left, 4.dp.toPx())
            drawRoundRect(fill, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Size(width, 15.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            b.label?.let {
                val layout = measurer.measure(it, barStyle.copy(color = ink))
                drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(left + width / 2 - layout.size.width / 2f, y + 7.5.dp.toPx() - layout.size.height / 2f))
            }
            b.side?.let {
                val layout = measurer.measure(it, androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted))
                drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(left + width + 5.dp.toPx(), y + 7.5.dp.toPx() - layout.size.height / 2f))
            }
        }
        timeline.bars.forEachIndexed { i, b -> bar(b, top + i * rowH) }
        var axisY = top + timeline.bars.size * rowH + 4.dp.toPx()
        if (timeline.output.isNotEmpty()) {
            drawLine(muted.copy(alpha = 0.3f), androidx.compose.ui.geometry.Offset(pad, axisY), androidx.compose.ui.geometry.Offset(size.width - pad, axisY), 1.dp.toPx())
            timeline.output.forEach { bar(it, axisY + 10.dp.toPx()) }
            axisY += 36.dp.toPx()
        }
        timeline.marker?.let { m ->
            drawLine(
                strokes.getValue(timeline.markerTone).copy(alpha = 0.8f), androidx.compose.ui.geometry.Offset(x(m), 6.dp.toPx()), androidx.compose.ui.geometry.Offset(x(m), axisY), 1.2.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
        var v = 0.0
        while (v <= timeline.maxX + 0.001) {
            val lit = timeline.marker == v
            val layout = measurer.measure(
                if (v == kotlin.math.round(v)) "${v.toInt()}" else "$v",
                androidx.compose.ui.text.TextStyle(
                    fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = if (lit) FontWeight.Bold else FontWeight.Normal,
                    color = if (lit) pathInk else muted,
                ),
            )
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(x(v) - layout.size.width / 2f, axisY + 12.dp.toPx() - layout.size.height / 2f))
            v += timeline.step
        }
    }
}

@Composable
private fun TBarsView(bars: TBars, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val strokes = TTone.entries.associateWith { tStroke(it) }
    androidx.compose.foundation.Canvas(modifier) {
        val n = bars.values.size
        val pad = 8.dp.toPx()
        val slot = (size.width - 2 * pad) / n
        val w = slot * 0.84f
        val base = size.height - 46.dp.toPx()
        val top = 34.dp.toPx()
        val maxV = maxOf(bars.values.maxOrNull() ?: 1, 1).toFloat()
        fun cx(i: Int) = pad + slot * (i + 0.5f)
        fun h(i: Int) = maxOf(10.dp.toPx(), (base - top) * bars.values[i] / maxV)
        fun label(text: String, x: Float, y: Float, style: androidx.compose.ui.text.TextStyle) {
            val layout = measurer.measure(text, style)
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
        }
        for (i in 0 until n) {
            val fill = when (bars.tones[i]) {
                TTone.Idle -> muted.copy(alpha = 0.3f)
                TTone.Pending -> muted.copy(alpha = 0.14f)
                else -> strokes.getValue(bars.tones[i])
            }
            drawRoundRect(
                fill, androidx.compose.ui.geometry.Offset(cx(i) - w / 2, base - h(i)), androidx.compose.ui.geometry.Size(w, h(i)),
                androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            )
            label("${bars.values[i]}", cx(i), base - h(i) - 12.dp.toPx(), androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = onSurface))
            label("$i", cx(i), base + 14.dp.toPx(), androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted))
            bars.tags[i]?.let { (t, tone) ->
                label(
                    t, cx(i), base + 34.dp.toPx(),
                    androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (tone == TTone.Idle) muted else strokes.getValue(tone)),
                )
            }
        }
        bars.arc?.let { (a, b) ->
            if (a != b) {
                val p1 = androidx.compose.ui.geometry.Offset(cx(a), base - h(a) - 26.dp.toPx())
                val p2 = androidx.compose.ui.geometry.Offset(cx(b), base - h(b) - 26.dp.toPx())
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p1.x, p1.y)
                    quadraticTo((p1.x + p2.x) / 2, minOf(p1.y, p2.y) - 14.dp.toPx(), p2.x, p2.y)
                }
                drawPath(
                    path, SimColors.Active,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
        }
    }
}

@Composable
private fun tNodeInk(tone: TTone): Color = when (tone) {
    TTone.Blue -> StoryTone.Path.ink()
    TTone.Answer, TTone.Memo -> Color(0xFFB9A5FF)
    TTone.Mismatch -> StoryTone.Warn.ink()
    TTone.Active, TTone.Stack -> StoryTone.Active.ink()
    TTone.Mirror, TTone.Green, TTone.Matched -> StoryTone.Done.ink()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun tTreeDepths(nodes: List<TNode>): List<Int> {
    val depth = MutableList(nodes.size) { 0 }
    for (i in nodes.indices) {
        var d = 0
        var p = nodes[i].parent
        while (p != null) { d++; p = nodes[p].parent }
        depth[i] = d
    }
    return depth
}

private fun tTreeHeight(tree: TTree): Dp {
    val two = tree.nodes.any { it.sub != null }
    val rows = tree.pos?.maxOf { it.second } ?: (tTreeDepths(tree.nodes).maxOrNull() ?: 0)
    return (rows * (if (two) 66 else if (tree.pos == null) 47 else 44) + (if (two) 40 else 28) + 32).dp
}

@Composable
private fun TTreeView(tree: TTree, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val colors = TTone.entries.associateWith { tColors(it) }
    val strokes = TTone.entries.associateWith { tStroke(it) }
    val inks = TTone.entries.associateWith { tNodeInk(it) }
    androidx.compose.foundation.Canvas(modifier) {
        val two = tree.nodes.any { it.sub != null }
        val rowGap = (if (two) 66 else if (tree.pos == null) 47 else 44).dp.toPx()
        val h = (if (two) 40 else 28).dp.toPx()
        val pad = 16.dp.toPx()
        val depth = tTreeDepths(tree.nodes)
        val kids = tree.nodes.indices.map { i -> tree.nodes.indices.filter { tree.nodes[it].parent == i } }
        fun label(i: Int) = tree.labels[i] ?: tree.nodes[i].label
        fun style(size: Float, bold: Boolean, color: Color) = androidx.compose.ui.text.TextStyle(
            fontFamily = IBMPlexMono, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color,
        )
        fun measure(font: Float) = tree.nodes.indices.map { i ->
            val t = measurer.measure(label(i), style(font, true, Color.White)).size.width.toFloat()
            val sub = tree.nodes[i].sub?.let { measurer.measure(it, style(font - 3, false, Color.White)).size.width.toFloat() } ?: 0f
            maxOf(t, sub) + 18.dp.toPx()
        }
        // A tidy layout: each row packs left to right, a parent centres over its children, and a subtree
        // moves right only as far as its row needs. Too wide for the card, the font shrinks.
        val avail = size.width - 2 * pad
        val gap = 10.dp.toPx()
        var font = 13f
        var widths = emptyList<Float>()
        val xs = MutableList(tree.nodes.size) { 0f }
        var total = 1f
        for (attempt in 0 until 5) {
            widths = measure(font)
            val next = MutableList((depth.maxOrNull() ?: 0) + 1) { 0f }
            fun shift(id: Int, d: Float) { xs[id] += d; kids[id].forEach { shift(it, d) } }
            fun bump(id: Int) { next[depth[id]] = maxOf(next[depth[id]], xs[id] + widths[id] / 2 + gap); kids[id].forEach { bump(it) } }
            fun place(id: Int) {
                val cs = kids[id]
                if (cs.isEmpty()) xs[id] = next[depth[id]] + widths[id] / 2 else {
                    cs.forEach { place(it) }
                    xs[id] = (xs[cs.first()] + xs[cs.last()]) / 2
                    val least = next[depth[id]] + widths[id] / 2
                    if (xs[id] < least) { shift(id, least - xs[id]); bump(id) }
                }
                next[depth[id]] = maxOf(next[depth[id]], xs[id] + widths[id] / 2 + gap)
            }
            place(0)
            total = tree.nodes.indices.maxOf { xs[it] + widths[it] / 2 }
            if (tree.pos != null || total <= avail || font <= 9f) break
            font -= 1f
        }
        val spread = avail / maxOf(total, 1f)
        fun at(i: Int): androidx.compose.ui.geometry.Offset {
            tree.pos?.let { return androidx.compose.ui.geometry.Offset(pad + it[i].first.toFloat() * avail, 16.dp.toPx() + h / 2 + it[i].second * rowGap) }
            return androidx.compose.ui.geometry.Offset(pad + xs[i] * spread, 16.dp.toPx() + h / 2 + depth[i] * rowGap)
        }
        fun text(t: String, st: androidx.compose.ui.text.TextStyle, x: Float, y: Float, anchor: Int = 0) {
            val layout = measurer.measure(t, st)
            val left = when (anchor) {
                -1 -> x
                1 -> x - layout.size.width
                else -> x - layout.size.width / 2f
            }
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(left, y - layout.size.height / 2f))
        }
        tree.nodes.forEachIndexed { i, n ->
            val p = n.parent ?: return@forEachIndexed
            val a = at(p)
            val b = at(i)
            val tone = tree.edges[i]
            drawLine(
                tone?.let { strokes.getValue(it) } ?: muted.copy(alpha = 0.4f),
                androidx.compose.ui.geometry.Offset(a.x, a.y + h / 2), androidx.compose.ui.geometry.Offset(b.x, b.y - h / 2),
                (if (tone != null) 2f else 1.2f).dp.toPx(),
            )
        }
        tree.arcs.forEach { arc ->
            val a = at(arc.from)
            val b = at(arc.to)
            val side = if (arc.right) 1f else -1f
            val start = androidx.compose.ui.geometry.Offset(a.x + side * widths[arc.from] / 2, a.y)
            val end = androidx.compose.ui.geometry.Offset(b.x + side * widths[arc.to] / 2, b.y)
            val cx = maxOf(start.x * side, end.x * side) * side + side * 40.dp.toPx()
            val cy = (a.y + b.y) / 2
            val c = strokes.getValue(arc.tone)
            drawPath(
                androidx.compose.ui.graphics.Path().apply { moveTo(start.x, start.y); quadraticTo(cx, cy, end.x, end.y) },
                c, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
            )
            val ang = kotlin.math.atan2(end.y - cy, end.x - cx)
            val l = 8.dp.toPx()
            drawPath(
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(end.x - l * kotlin.math.cos(ang - 0.45f), end.y - l * kotlin.math.sin(ang - 0.45f))
                    lineTo(end.x - l * kotlin.math.cos(ang + 0.45f), end.y - l * kotlin.math.sin(ang + 0.45f))
                    close()
                },
                c,
            )
            text(arc.label, style(12f, true, inks.getValue(arc.tone)), cx + side * 4.dp.toPx(), cy, if (arc.right) -1 else 1)
        }
        tree.nodes.indices.forEach { i ->
            val c = at(i)
            val tone = tree.tones[i] ?: tree.base
            val (fill, ink) = colors.getValue(tone)
            val topLeft = androidx.compose.ui.geometry.Offset(c.x - widths[i] / 2, c.y - h / 2)
            val sz = androidx.compose.ui.geometry.Size(widths[i], h)
            val r = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            drawRoundRect(surface, topLeft, sz, r)
            drawRoundRect(fill, topLeft, sz, r)
            val border = when (tone) {
                TTone.Stack -> SimColors.Active
                TTone.Memo -> SimColors.Answer
                TTone.Mirror -> SimColors.Green
                TTone.Mismatch -> SimColors.Red
                else -> null
            }
            border?.let { drawRoundRect(it, topLeft, sz, r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) }
            val sub = tree.nodes[i].sub
            if (sub != null) {
                text(label(i), style(font, true, ink), c.x, c.y - 7.dp.toPx())
                text(sub, style(font - 3, false, ink.copy(alpha = 0.85f)), c.x, c.y + 9.dp.toPx())
            } else {
                text(label(i), style(font, true, ink), c.x, c.y)
            }
            tree.badges[i]?.let { (t, bt) -> text(t, style(11f, true, inks.getValue(bt)), c.x + widths[i] / 2 + 5.dp.toPx(), c.y, -1) }
        }
    }
}

private val RoseColor = Color(0xFFE0457B)

@Composable
private fun TGraphView(graph: TGraph, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val colors = TTone.entries.associateWith { tColors(it) }
    val strokes = TTone.entries.associateWith { tStroke(it) }
    val inks = TTone.entries.associateWith { tNodeInk(it) }
    androidx.compose.foundation.Canvas(modifier) {
        val r = 20.dp.toPx()
        val padX = 30.dp.toPx()
        val padTop = 30.dp.toPx()
        val padBottom = 38.dp.toPx()
        fun at(i: Int) = androidx.compose.ui.geometry.Offset(
            padX + graph.nodes[i].x.toFloat() * (size.width - 2 * padX),
            padTop + graph.nodes[i].y.toFloat() * (size.height - padTop - padBottom),
        )
        fun text(t: String, sz: Float, color: Color, x: Float, y: Float) {
            val layout = measurer.measure(t, androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = sz.sp, color = color))
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(x - layout.size.width / 2f, y - layout.size.height / 2f))
        }
        graph.edges.forEach { e ->
            val a = at(e.a)
            val b = at(e.b)
            val len = maxOf(kotlin.math.hypot(b.x - a.x, b.y - a.y), 1f)
            val ux = (b.x - a.x) / len
            val uy = (b.y - a.y) / len
            val start = androidx.compose.ui.geometry.Offset(a.x + ux * r, a.y + uy * r)
            val end = androidx.compose.ui.geometry.Offset(b.x - ux * (r + 2.dp.toPx()), b.y - uy * (r + 2.dp.toPx()))
            val c = e.tone?.let { if (it == TTone.Mismatch) SimColors.Red else strokes.getValue(it) } ?: muted.copy(alpha = 0.45f)
            drawLine(
                c, start, end, (if (e.tone == null) 1.4f else 2.2f).dp.toPx(),
                pathEffect = if (e.dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
            )
            if (graph.directed) {
                val l = 9.dp.toPx()
                val w = 5.dp.toPx()
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(end.x - l * ux + w * uy, end.y - l * uy - w * ux)
                        lineTo(end.x - l * ux - w * uy, end.y - l * uy + w * ux)
                        close()
                    },
                    c,
                )
            }
            e.weight?.let { wt ->
                // Beside the edge's midpoint, on the side facing up (or left for a vertical edge).
                var nx = -uy
                var ny = ux
                if (ny > 0 || (ny == 0f && nx > 0)) { nx = -nx; ny = -ny }
                text(wt, 12f, e.tone?.let { inks.getValue(it) } ?: muted, (a.x + b.x) / 2 + nx * 11.dp.toPx(), (a.y + b.y) / 2 + ny * 11.dp.toPx())
            }
        }
        graph.nodes.indices.forEach { i ->
            val c = at(i)
            val tone = graph.tones[i] ?: graph.base
            val (fill, ink) = colors.getValue(tone)
            drawCircle(surface, r, c)
            drawCircle(fill, r, c)
            val border = when (tone) {
                TTone.Outline, TTone.Frontier -> SimColors.Blue
                TTone.Pending, TTone.Idle -> muted.copy(alpha = 0.4f)
                TTone.Stack -> SimColors.Active
                else -> null
            }
            border?.let { drawCircle(it, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke((if (tone == TTone.Pending || tone == TTone.Idle) 1.2f else 1.5f).dp.toPx())) }
            text(graph.nodes[i].label, if (graph.nodes[i].label.length > 2) 12f else 14f, ink, c.x, c.y)
            graph.captions[i]?.let { (t, ct) -> text(t, 12f, inks.getValue(ct), c.x, c.y + r + 11.dp.toPx()) }
        }
    }
}

/** Each row: a caps title with a note on the right, the jobs as bars sized by duration, end times underneath. */
@Composable
private fun TGanttView(rows: List<TGanttRow>, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.Canvas(modifier) {
        val pad = 18.dp.toPx()
        val total = rows.maxOf { r -> r.jobs.sumOf { it.first } }.toFloat()
        val scale = (size.width - 2 * pad) / total
        fun text(t: String, size: Float, weight: FontWeight, color: Color, x: Float, y: Float, anchor: Int) {
            val layout = measurer.measure(t, androidx.compose.ui.text.TextStyle(fontFamily = IBMPlexMono, fontSize = size.sp, fontWeight = weight, color = color))
            val left = when (anchor) {
                -1 -> x
                1 -> x - layout.size.width
                else -> x - layout.size.width / 2f
            }
            drawText(layout, topLeft = androidx.compose.ui.geometry.Offset(left, y - layout.size.height / 2f))
        }
        rows.forEachIndexed { r, row ->
            val y0 = (12 + r * 68).dp.toPx()
            text(row.title, 12f, FontWeight.SemiBold, muted, pad, y0 + 6.dp.toPx(), -1)
            text(row.note, 13f, FontWeight.Bold, onSurface, size.width - pad, y0 + 6.dp.toPx(), 1)
            var x = pad
            var t = 0
            row.jobs.forEach { (d, tone) ->
                val w = d * scale
                val (fill, ink) = when (tone) {
                    TTone.Active -> SimColors.Active to Color(0xFF1F1A0A)
                    TTone.Green, TTone.Matched -> SimColors.Green to Color.White
                    else -> muted.copy(alpha = 0.3f) to onSurface
                }
                val top = y0 + 16.dp.toPx()
                drawRoundRect(
                    fill, androidx.compose.ui.geometry.Offset(x + 1.5.dp.toPx(), top), androidx.compose.ui.geometry.Size(w - 3.dp.toPx(), 30.dp.toPx()),
                    androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                )
                text("$d", 13f, FontWeight.Bold, ink, x + w / 2, top + 15.dp.toPx(), 0)
                t += d
                x += w
                text("$t", 10f, FontWeight.Normal, muted, x - 4.dp.toPx(), y0 + 56.dp.toPx(), 1)
            }
        }
    }
}

// ── Coding pattern storyboards ───────────────────────────────────────────────
// Fast and slow pointers, merge intervals, binary search on the answer, top-k, monotonic stack, cyclic
// sort, k-way merge, in-place reversal and greedy interval scheduling.

private fun fastSlowTabs(): List<TTab> {
    val names = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val n = names.size
    val entry = 3
    fun nxt(i: Int) = if (i == n - 1) entry else i + 1
    val next: List<Int?> = (0 until n).map { nxt(it) }
    val cycleLen = n - entry
    // Two pointers on the list; on the same node they share it, violet only once they have met in the loop.
    fun chain(a: Int, aName: String, aTone: TTone, b: Int, bName: String, bTone: TTone, dim: Int = 0, met: Boolean = false): TChain {
        val above = mutableMapOf<Int, Pair<String, TTone>>()
        above[b] = bName to if (bTone == TTone.Active) TTone.Active else TTone.Blue
        above[a] = if (a == b) (if (met) "meet" else "$aName · $bName") to (if (met) TTone.Answer else TTone.Active)
        else aName to if (aTone == TTone.Active) TTone.Active else TTone.Blue
        return TChain(
            names.mapIndexed { i, c ->
                TCell(
                    c,
                    when {
                        i == a && i == b -> if (met) TTone.Answer else TTone.Active
                        i == a -> aTone
                        i == b -> bTone
                        i < dim -> TTone.Struck
                        else -> TTone.Idle
                    },
                )
            },
            next, special = mapOf(n - 1 to (TTone.Answer to "${names[n - 1]}.next = ${names[entry]}")), above = above,
        )
    }
    var s = 0
    var f = 0
    val detect = mutableListOf(
        TFrame(
            0, emptyList(), "Both pointers start at A. slow takes one hop per step, fast takes two.",
            "If the list ends, fast reaches null first. If it loops, fast laps slow and they meet.",
            formula = listOf("slow += 1, fast += 2 each step"), chips = listOf(StoryChip("slow", names[s]), StoryChip("fast", names[f])),
            chain = chain(f, "fast", TTone.Active, s, "slow", TTone.Outline),
        ),
    )
    var step = 0
    while (true) {
        s = nxt(s)
        f = nxt(nxt(f))
        step++
        val bothIn = s >= entry && f >= entry
        val gap = ((s - f) % cycleLen + cycleLen) % cycleLen
        if (s == f) {
            detect += TFrame(
                0, emptyList(), "slow and fast meet at {v:${names[s]}}: the list has a cycle.",
                "Only a loop lets the faster pointer come round behind the slower one. O(n) time, O(1) space.",
                formula = listOf("slow = fast = {v:${names[s]}} → cycle"),
                chips = listOf(StoryChip("slow", names[s]), StoryChip("fast", names[f]), StoryChip("steps", "$step", StoryTone.Answer)),
                chain = chain(f, "fast", TTone.Active, s, "slow", TTone.Outline, entry, met = true),
            )
            break
        }
        val wrapped = f < s && bothIn
        detect += TFrame(
            0, emptyList(),
            if (wrapped) "Fast has wrapped past ${names[n - 1]} and is now ${if (gap == 1) "one node" else "$gap nodes"} behind slow."
            else "slow moves to ${names[s]}, fast jumps to {${names[f]}}.",
            if (wrapped) "Inside the loop fast closes the gap by 1 each step, so it can't jump over slow. They meet at ${names[nxt(s)]}."
            else if (f >= entry) "fast is already inside the loop; slow enters it at ${names[entry]}." else "Still on the straight part: fast pulls ahead by one node per step.",
            formula = if (bothIn) listOf("gap = {p:slow} − {fast}", "= $gap → fast gains 1 per step → meet ${if (gap == 1) "next step" else "in $gap steps"}")
            else listOf("slow = ${names[s]}, fast = ${names[f]}"),
            chips = listOf(StoryChip("slow", names[s]), StoryChip("fast", names[f])) + if (bothIn) listOf(StoryChip("gap", "$gap")) else emptyList(),
            chain = chain(f, "fast", TTone.Active, s, "slow", TTone.Outline, if (bothIn) entry else 0),
        )
    }
    detect += TFrame(
        0, emptyList(), "The meeting point proves the cycle but is not where it starts.", "Find start uses one more trick to locate ${names[entry]}.",
        formula = listOf("meeting point = {v:${names[s]}}"),
        chips = listOf(StoryChip("cycle length", "$cycleLen"), StoryChip("meet", names[s], StoryTone.Answer)),
        chain = chain(s, "meet", TTone.Answer, s, "meet", TTone.Answer, met = true),
    )
    val meet = s
    var p = 0
    var q = meet
    val start = mutableListOf(
        TFrame(
            0, emptyList(), "Put p back at the head and leave q at ${names[meet]}. Now both take one hop.",
            "The head is as far from the cycle's start as the meeting point is, going round.",
            formula = listOf("p = head, q = meeting point, both += 1"), chips = listOf(StoryChip("p", names[p]), StoryChip("q", names[q])),
            chain = chain(q, "q", TTone.Outline, p, "p", TTone.Active),
        ),
    )
    while (p != q) {
        p = nxt(p)
        q = nxt(q)
        val met = p == q
        start += TFrame(
            0, emptyList(),
            if (met) "p and q meet at {v:${names[p]}}: that is where the cycle begins." else "p moves to ${names[p]}, q to ${names[q]}.",
            if (met) "Floyd's second phase: the distance from head to start equals meeting point to start, mod the cycle."
            else "Same speed now, so the gap between them never changes.",
            formula = listOf(if (met) "p = q = {v:${names[p]}} → cycle starts here" else "p = ${names[p]}, q = ${names[q]}"),
            chips = listOf(StoryChip("p", names[p]), StoryChip("q", names[q])) + if (met) listOf(StoryChip("start", names[p], StoryTone.Answer)) else emptyList(),
            chain = chain(q, "q", TTone.Outline, p, "p", TTone.Active, met = met),
        )
    }
    return listOf(
        TTab(
            "Detect cycle", detect,
            listOf(Triple(SimColors.Blue, SwatchStyle.Fill, "slow · 1 hop"), Triple(SimColors.Active, SwatchStyle.Fill, "fast · 2 hops"), Triple(SimColors.Answer, SwatchStyle.Fill, "Cycle")),
        ),
        TTab(
            "Find start", start,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "p from head"), Triple(SimColors.Blue, SwatchStyle.Fill, "q from meeting point"), Triple(SimColors.Answer, SwatchStyle.Fill, "Cycle")),
        ),
    )
}

private fun mergeIntervalTabs(): List<TTab> {
    val iv = listOf(1 to 3, 2 to 6, 8 to 10, 9 to 12, 15 to 18)
    val out = mutableListOf<Pair<Int, Int>>()
    val fate = MutableList(iv.size) { TTone.Idle }
    var lastIdx: Int? = null
    fun tone(i: Int, current: Int?) = when {
        i == current -> TTone.Active
        i == lastIdx -> TTone.Outline
        fate[i] == TTone.Matched -> TTone.Matched
        else -> TTone.Idle
    }
    fun tiles(current: Int?) = TRow(iv.mapIndexed { i, v -> TCell("${v.first}–${v.second}", tone(i, current)) }, title = "SORTED BY START", spread = true)
    fun timeline(current: Int?) = TTimeline(
        18.0, 3.0,
        iv.mapIndexed { i, v -> TBar(v.first.toDouble(), v.second.toDouble(), tone(i, current).let { if (it == TTone.Outline) TTone.Blue else it }) },
        out.map { TBar(it.first.toDouble(), it.second.toDouble(), TTone.Answer) },
    )
    fun outText() = if (out.isEmpty()) "–" else out.joinToString(" ") { "[${it.first},${it.second}]" }
    val frames = mutableListOf(
        TFrame(
            0, listOf(tiles(null)), "Sort the intervals by start, then walk them once.",
            "Once sorted by start, only the last merged interval can overlap the next one.",
            formula = listOf("sort by start, then sweep once"), chips = listOf(StoryChip("output", "–")), timeline = timeline(null),
        ),
    )
    iv.forEachIndexed { i, v ->
        val last = out.lastOrNull()
        if (last != null && v.first <= last.second) {
            val merged = last.first to maxOf(last.second, v.second)
            out[out.lastIndex] = merged
            frames += TFrame(
                0, listOf(tiles(i)),
                if (v.second > last.second) "${v.first}–${v.second} starts before ${last.first}–${last.second} ends, so they fuse into ${merged.first}–${merged.second}."
                else "${v.first}–${v.second} sits inside ${last.first}–${last.second}, so nothing changes.",
                "Once sorted by start, only the last merged interval can overlap the next one.",
                formula = listOf("{${v.first}} ≤ last.end {p:${last.second}} → end = max(${last.second}, ${v.second}) = {v:${merged.second}}"),
                chips = listOf(StoryChip("output", outText())), timeline = timeline(i),
            )
            fate[i] = TTone.Matched
        } else {
            out += v
            frames += TFrame(
                0, listOf(tiles(i)),
                if (out.size == 1) "${v.first}–${v.second} opens the output." else "${v.first}–${v.second} starts after the last one ends, so it opens a new interval.",
                "A gap means no later interval can bridge back: everything after starts even later.",
                formula = listOf(if (out.size == 1) "first interval → output" else "{${v.first}} > last.end {p:${out[out.size - 2].second}} → new interval"),
                chips = listOf(StoryChip("output", outText())), timeline = timeline(i),
            )
        }
        lastIdx?.let { fate[it] = TTone.Matched }
        lastIdx = i
    }
    lastIdx = null
    for (k in fate.indices) fate[k] = TTone.Matched
    frames += TFrame(
        0, listOf(tiles(null)), "${iv.size} intervals collapse into {v:${out.size}}: ${outText()}.",
        "The sort is O(n log n); the sweep that follows is a single O(n) pass.",
        formula = listOf("output = {v:${outText()}}"), chips = listOf(StoryChip("output", outText(), StoryTone.Answer)), timeline = timeline(null),
    )
    return listOf(
        TTab(
            "Merge", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Considering"),
                Triple(SimColors.Blue, SwatchStyle.Fill, "Last merged"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Absorbed"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Output"),
            ),
        ),
    )
}

private fun binaryAnswerTabs(): List<TTab> {
    val w = listOf(3, 2, 2, 4, 1, 4)
    val days = 3
    val lo0 = w.max()
    val hi0 = w.sum()
    fun split(cap: Int): List<Int> {
        var d = 1
        var load = 0
        return w.map { x ->
            if (load + x > cap) { d++; load = 0 }
            load += x
            d
        }
    }
    fun packages(cap: Int?): TRow {
        if (cap == null) return TRow(w.map { TCell("$it", TTone.Idle) }, title = "PACKAGES · $days DAYS", spread = true)
        val d = split(cap)
        return TRow(
            w.mapIndexed { i, x -> TCell("$x", if (d[i] > days) TTone.Mismatch else TTone.Outline) },
            title = "PACKAGES · $days DAYS", spread = true,
            tags = d.mapIndexed { i, day -> i to ("day $day" to if (day > days) TTone.Mismatch else TTone.Idle) }.toMap(),
        )
    }
    fun capacities(lo: Int, hi: Int, mid: Int?): TRow {
        val tags = mutableMapOf<Int, Pair<String, TTone>>()
        tags[lo - lo0] = "lo" to TTone.Blue
        tags[hi - lo0] = if (lo == hi) "lo = hi" to TTone.Answer else "hi" to TTone.Blue
        mid?.let { tags[it - lo0] = "mid" to TTone.Active }
        return TRow(
            (lo0..hi0).map { c ->
                TCell(
                    "$c",
                    when {
                        c == mid -> TTone.Active
                        c == lo || c == hi -> TTone.Outline
                        c in (lo + 1) until hi -> TTone.Idle
                        else -> TTone.Pending
                    },
                )
            },
            title = "CAPACITY", spread = true, tags = tags, compact = true,
        )
    }
    var lo = lo0
    var hi = hi0
    val frames = mutableListOf(
        TFrame(
            0, listOf(packages(null), capacities(lo, hi, null)),
            "The smallest ship capacity that moves everything in $days days lies between $lo0 and $hi0.",
            "Below the heaviest package nothing ships; at the total everything ships in one day.",
            formula = listOf("lo = max = $lo0, hi = sum = $hi0"), chips = listOf(StoryChip("lo", "$lo"), StoryChip("hi", "$hi")),
        ),
    )
    while (lo < hi) {
        val mid = (lo + hi) / 2
        val used = split(mid).last()
        val ok = used <= days
        val oldLo = lo
        val oldHi = hi
        if (ok) hi = mid else lo = mid + 1
        frames += TFrame(
            0, listOf(packages(mid), capacities(oldLo, oldHi, mid)),
            if (ok) "At capacity $mid everything ships in $used day${if (used == 1) "" else "s"}, so try smaller."
            else "At capacity $mid the last package spills into a ${used}th day, so $mid is too small.",
            if (ok) "Every capacity above $mid works too, so the answer is $mid or below."
            else "Every capacity below $mid fails too. The array is never sorted; the yes/no answer is.",
            formula = listOf(
                if (ok) "cap {$mid} → $used day${if (used == 1) "" else "s"} ≤ $days → fits → hi = {p:$mid}"
                else "cap {$mid} → $used days > $days → too small → lo = {p:${mid + 1}}",
            ),
            chips = listOf(StoryChip("lo", "$oldLo"), StoryChip("mid", "$mid"), StoryChip("hi", "$oldHi")),
        )
    }
    val checks = frames.size - 1
    frames += TFrame(
        0, listOf(packages(lo), capacities(lo, lo, null)), "lo and hi meet: the smallest capacity is {v:$lo}.",
        "$checks feasibility checks instead of ${hi0 - lo0 + 1}. Each check is O(n), so O(n log(sum)).",
        formula = listOf("lo = hi = {v:$lo}"), chips = listOf(StoryChip("capacity", "$lo", StoryTone.Answer), StoryChip("checks", "$checks")),
    )
    return listOf(
        TTab(
            "Ship packages", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "mid"), Triple(SimColors.Blue, SwatchStyle.Fill, "Still possible"), Triple(SimColors.Red, SwatchStyle.Fill, "Overflow day")),
        ),
    )
}

private fun topKTabs(): List<TTab> {
    val nums = listOf(1, 3, 1, 5, 3, 1, 7, 3, 5)
    val k = 2
    val order = nums.distinct()
    val counts = nums.groupingBy { it }.eachCount()
    val cmp = compareBy<Int>({ counts.getValue(it) }, { it })
    val numsRow = TRow(nums.map { TCell("$it", TTone.Pending) }, title = "NUMS · K = $k", spread = true, compact = true)
    fun countRow(tones: Map<Int, TTone>) = TRow(order.map { TCell("$it ×${counts[it]}", tones[it] ?: TTone.Idle) }, title = "COUNTS", spread = true)
    val heap = mutableListOf<Int>()
    fun heapRow(popped: Int?) = TRow(
        (heap + listOfNotNull(popped)).sortedWith(cmp).map { TCell("$it ×${counts[it]}", if (it == popped) TTone.Active else TTone.Outline) },
        title = "MIN-HEAP BY COUNT", spread = true,
    )
    fun times(c: Int) = when (c) {
        1 -> "once"
        2 -> "twice"
        else -> "$c times"
    }
    val tones = mutableMapOf<Int, TTone>()
    val heapFrames = mutableListOf(
        TFrame(
            0, listOf(numsRow, countRow(emptyMap())), "Find the $k most frequent values.",
            "Counting is one pass. The question is how to pick the top k without sorting every count.",
            formula = listOf("count each value, then keep the k most frequent"), chips = listOf(StoryChip("k", "$k")),
        ),
        TFrame(
            0, listOf(numsRow, countRow(emptyMap())), "One pass counts every value: ${order.size} distinct.", "A hash map makes each count O(1).",
            formula = listOf(order.joinToString(", ") { "$it ×${counts[it]}" }), chips = listOf(StoryChip("distinct", "${order.size}")),
        ),
    )
    for (x in order) {
        heap += x
        tones[x] = TTone.Outline
        if (heap.size > k) {
            val minX = heap.minWith(cmp)
            heap.remove(minX)
            tones[minX] = TTone.Mismatch
            heap.forEach { tones[it] = TTone.Matched }
            heapFrames += TFrame(
                0, listOf(numsRow, countRow(tones), heapRow(minX)),
                if (minX == x) "$x appears ${times(counts.getValue(x))}, fewer than ${heap.joinToString(" and ")}, so the heap pops it straight back out."
                else "$x pushes out $minX, the least frequent in the heap.",
                "The heap never holds more than k, so selection is O(n log k).",
                formula = listOf("size ${k + 1} > k = $k → pop min → {w:$minX ×${counts[minX]}} out"), chips = listOf(StoryChip("heap", "${heap.size} / $k")),
            )
        } else {
            heapFrames += TFrame(
                0, listOf(numsRow, countRow(tones), heapRow(null)),
                "Push {$x} (×${counts[x]}). The heap has room for $k.", "A min-heap keeps the least frequent kept value on top, ready to be evicted.",
                formula = listOf("push $x ×${counts[x]} → size {${heap.size}}"), chips = listOf(StoryChip("heap", "${heap.size} / $k")),
            )
        }
    }
    heap.forEach { tones[it] = TTone.Matched }
    val top = heap.sorted()
    heapFrames += TFrame(
        0, listOf(numsRow, countRow(tones), heapRow(null)), "The heap holds the answer: {v:${top.joinToString(" and ")}}.",
        "O(n) to count plus O(m log k) over m distinct values, far less than sorting when k is small.",
        formula = listOf("top $k = {v:${top.joinToString(", ")}}"), chips = listOf(StoryChip("top $k", top.joinToString(", "), StoryTone.Answer)),
    )
    val maxF = nums.size
    val buckets = List(maxF + 1) { f -> order.filter { counts[it] == f } }
    val used = (1..maxF).filter { buckets[it].isNotEmpty() }
    fun bucketRow(lit: Int?) = TRow(
        used.reversed().map { f -> TCell(buckets[f].joinToString(" "), if (f == lit) TTone.Active else TTone.Outline, "f = $f") },
        title = "BUCKETS BY FREQUENCY", spread = true,
    )
    val taken = mutableListOf<Int>()
    val bucket = mutableListOf(
        TFrame(
            0, listOf(numsRow, countRow(emptyMap()), bucketRow(null)), "Put each value in the bucket for its count.",
            "A count can never exceed n, so an array of n buckets replaces the heap.",
            formula = listOf("bucket[f] = values seen f times, f ≤ n = $maxF"), chips = listOf(StoryChip("buckets", "$maxF")),
        ),
    )
    for (f in used.reversed()) {
        if (taken.size >= k) break
        taken += buckets[f].take(k - taken.size)
        bucket += TFrame(
            0, listOf(numsRow, countRow(taken.associateWith { TTone.Matched }), bucketRow(f)),
            "Scanning from the top, bucket $f gives {${buckets[f].joinToString(" and ")}}.",
            if (taken.size == k) "k values found, so the scan stops: O(n) overall, no heap at all." else "Keep going down until k values are taken.",
            formula = listOf("bucket[$f] → take ${buckets[f].joinToString(", ")} → {${taken.size}} of $k"),
            chips = listOf(StoryChip("taken", "${taken.size} / $k", if (taken.size == k) StoryTone.Answer else StoryTone.Idle)),
        )
    }
    return listOf(
        TTab(
            "Min-heap", heapFrames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Popped"),
                Triple(SimColors.Blue, SwatchStyle.Fill, "In heap"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Kept"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Discarded"),
            ),
            start = minOf(4, heapFrames.lastIndex),
        ),
        TTab(
            "Bucket sort", bucket,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Bucket read"), Triple(SimColors.Blue, SwatchStyle.Fill, "Bucket"), Triple(SimColors.Green, SwatchStyle.Fill, "Taken")),
        ),
    )
}

private fun monoStackTabs(): List<TTab> {
    val a = listOf(2, 1, 5, 6, 2, 3)
    val n = a.size
    fun run(greater: Boolean): List<TFrame> {
        val stack = mutableListOf<Int>()
        val ans = MutableList<Int?>(n) { null }
        var pops = 0
        fun row(i: Int?) = TRow(
            a.mapIndexed { k, v -> TCell("$v", if (k == i) TTone.Active else if (ans[k] != null) TTone.Matched else TTone.Idle) },
            tags = ans.mapIndexedNotNull { k, v -> v?.let { k to ("→${a[it]}" to TTone.Matched) } }.toMap(),
        )
        fun stackRow() = TRow(
            (0 until n).map { k -> if (k < stack.size) TCell("${a[stack[k]]} · i${stack[k]}", TTone.Outline) else TCell("", TTone.Ghost) },
            title = "STACK · VALUES ${if (greater) "DECREASING" else "INCREASING"}", spread = true,
        )
        fun ansText() = "[" + ans.joinToString(",") { v -> v?.let { "${a[it]}" } ?: "·" } + "]"
        val word = if (greater) "bigger" else "smaller"
        val frames = mutableListOf(
            TFrame(
                n, listOf(row(null), stackRow()), "For each value, find the next one to its right that is $word.",
                "The stack holds indices still waiting, their values kept in ${if (greater) "decreasing" else "increasing"} order.",
                formula = listOf("stack keeps indices still waiting for a ${if (greater) "greater" else "smaller"} value"),
                chips = listOf(StoryChip("pops", "0"), StoryChip("answer", ansText())),
            ),
        )
        for (i in 0 until n) {
            val popped = mutableListOf<Int>()
            while (stack.isNotEmpty() && (if (greater) a[i] > a[stack.last()] else a[i] < a[stack.last()])) {
                val top = stack.removeAt(stack.lastIndex)
                ans[top] = i
                popped += top
                pops++
            }
            stack += i
            val sym = if (greater) ">" else "<"
            val formula = if (popped.isEmpty()) listOf("{${a[i]}} → nothing to pop · push i$i")
            else popped.mapIndexed { k, t -> (if (k == 0) "{${a[i]}} " else "") + "$sym ${a[t]} → pop i$t" + if (k == popped.lastIndex) " · push i$i" else " · {${a[i]}}" }
            frames += TFrame(
                n, listOf(row(i), stackRow()),
                if (popped.isEmpty()) "${a[i]} answers nothing, so it waits on the stack."
                else "${a[i]} is the first value $word than ${popped.joinToString(" and ") { "${a[it]}" }}, so it answers ${if (popped.size == 1) "it" else "both"}.",
                "Each index is pushed once and popped once, so the whole pass is O(n).",
                lit = mapOf(i to TTone.Active), formula = formula, chips = listOf(StoryChip("pops", "$pops"), StoryChip("answer", ansText())),
            )
        }
        frames += TFrame(
            n, listOf(row(null), stackRow()), "The ${stack.size} indices left on the stack have no $word value to their right.",
            "$n pushes and $pops pops: linear, although the inner loop looks quadratic.",
            formula = listOf("answer = {v:${ansText()}}"), chips = listOf(StoryChip("answer", ansText(), StoryTone.Answer)),
        )
        return frames
    }
    val legend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Incoming"), Triple(SimColors.Blue, SwatchStyle.Fill, "On stack"), Triple(SimColors.Green, SwatchStyle.Fill, "Answered"))
    return listOf(TTab("Next greater", run(true), legend), TTab("Next smaller", run(false), legend))
}

private fun cyclicSortTabs(): List<TTab> {
    fun run(start: List<Int>, duplicate: Boolean): List<TFrame> {
        val a = start.toMutableList()
        val n = a.size
        var i = 0
        var swaps = 0
        val frames = mutableListOf<TFrame>()
        fun rows(cur: Int?, dup: Int? = null) = listOf(
            TRow(a.mapIndexed { k, v -> TCell("$v", if (k == cur) TTone.Active else if (k == dup) TTone.Mismatch else if (v == k + 1) TTone.Matched else TTone.Idle) }),
            TRow((0 until n).map { TCell("${it + 1}", if (a[it] == it + 1) TTone.Matched else TTone.Pending) }, title = "HOME · INDEX I HOLDS I + 1"),
        )
        frames += TFrame(
            n, rows(null), "Values 1 to $n each have a home: v belongs at index v − 1.",
            "Swap each value straight into its home; whatever lands at i is placed next.",
            formula = listOf("value v belongs at index v − 1"), chips = listOf(StoryChip("i", "0"), StoryChip("swaps", "0")),
        )
        while (i < n) {
            val v = a[i]
            val j = v - 1
            when {
                v < 1 || v > n -> {
                    frames += TFrame(
                        n, rows(i), "$v has no home in 1…$n, so leave it and move on.", "Its slot is where the missing number would go.",
                        lit = mapOf(i to TTone.Active), formula = listOf("$v is out of range → i++"), chips = listOf(StoryChip("i", "$i"), StoryChip("swaps", "$swaps")),
                    )
                    i++
                }
                j == i -> i++
                a[j] == v -> {
                    frames += TFrame(
                        n, rows(i, j), "$v wants index $j, but index $j already holds a $v. That is the duplicate.",
                        "Swapping would loop forever, so leave it and move on.",
                        lit = mapOf(i to TTone.Active, j to TTone.Mismatch),
                        formula = listOf("home( {$v} ) = $v − 1 = $j · nums[$j] = {w:$v}", "→ duplicate, i++"),
                        chips = listOf(StoryChip("i", "$i"), StoryChip("swaps", "$swaps")),
                    )
                    i++
                }
                else -> {
                    a[i] = a[j].also { a[j] = a[i] }
                    swaps++
                    frames += TFrame(
                        n, rows(i), "$v goes home to index $j; ${a[i]} comes back to index $i.", "i stays put: the value that just arrived still needs placing.",
                        lit = mapOf(i to TTone.Active, j to TTone.Matched), formula = listOf("home( {$v} ) = $j → swap nums[$i] ↔ nums[$j]"),
                        chips = listOf(StoryChip("i", "$i"), StoryChip("swaps", "$swaps")),
                    )
                }
            }
        }
        val bad = (0 until n).firstOrNull { a[it] != it + 1 }
        val answer = bad?.let { if (duplicate) "duplicate ${a[it]}, missing ${it + 1}" else "missing ${it + 1}" } ?: "none"
        frames += TFrame(
            n, rows(null, bad),
            bad?.let { "Index $it holds ${a[it]}, not ${it + 1}: " + if (duplicate) "${a[it]} is doubled and ${it + 1} is missing." else "${it + 1} is missing." } ?: "Every value is home.",
            "At most n swaps and one scan: O(n) time and no extra space.",
            lit = bad?.let { mapOf(it to TTone.Mismatch) } ?: emptyMap(), formula = listOf("first index not holding i + 1 → {v:$answer}"),
            chips = listOf(StoryChip("swaps", "$swaps"), StoryChip("answer", answer, StoryTone.Answer)),
        )
        return frames
    }
    val legend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Being placed"), Triple(SimColors.Green, SwatchStyle.Fill, "Home"), Triple(SimColors.Red, SwatchStyle.Fill, "Duplicate / gap"))
    return listOf(TTab("Find missing", run(listOf(6, 2, 4, 1, 5), false), legend), TTab("Find duplicate", run(listOf(5, 1, 3, 3, 4), true), legend))
}

private fun kWayMergeTabs(): List<TTab> {
    val lists = listOf(listOf(2, 6, 8), listOf(3, 6, 7), listOf(1, 3, 4))
    val pos = mutableListOf(0, 0, 0)
    val output = mutableListOf<Int>()
    val cmp = compareBy<Pair<Int, Int>>({ it.first }, { it.second })
    val heap = lists.indices.map { lists[it][0] to it }.sortedWith(cmp).toMutableList()
    fun rows(popped: Pair<Int, Int>?) = lists.mapIndexed { li, list ->
        TRow(
            list.mapIndexed { k, v ->
                TCell(
                    "$v",
                    when {
                        popped != null && popped.second == li && k == pos[li] - 1 -> TTone.Active
                        k < pos[li] -> TTone.Pending
                        k == pos[li] -> TTone.Outline
                        else -> TTone.Idle
                    },
                )
            },
            "L${li + 1}",
        )
    }
    fun heapRow() = TRow(heap.map { TCell("${it.first} · L${it.second + 1}", TTone.Outline) }, title = "MIN-HEAP · ONE HEAD PER LIST", spread = true)
    val frames = mutableListOf(
        TFrame(
            3, rows(null) + heapRow(), "Seed a min-heap with the head of each of the ${lists.size} lists.", "The smallest remaining value is always one of the heads.",
            formula = listOf("heap = first value of each list"), chips = listOf(StoryChip("output", "–"), StoryChip("heap", "${heap.size}")),
        ),
    )
    while (heap.isNotEmpty()) {
        val top = heap.removeAt(0)
        output += top.first
        pos[top.second]++
        val li = top.second
        val refill = pos[li] < lists[li].size
        var formula = "pop {${top.first}} from L${li + 1}"
        if (refill) {
            heap += lists[li][pos[li]] to li
            heap.sortWith(cmp)
            formula += " → push L${li + 1}[${pos[li]}] = {p:${lists[li][pos[li]]}}"
        } else {
            formula += " → L${li + 1} is empty"
        }
        frames += TFrame(
            3, rows(top) + heapRow(),
            if (refill) "${top.first} came from L${li + 1}, so L${li + 1}'s next value, ${lists[li][pos[li]]}, takes its place in the heap."
            else "${top.first} was L${li + 1}'s last value, so the heap shrinks to ${heap.size}.",
            "The heap stays at k items, so each of the n values costs O(log k).",
            formula = listOf(formula), chips = listOf(StoryChip("output", output.joinToString(" ")), StoryChip("heap", "${heap.size}")),
        )
    }
    frames += TFrame(
        3, rows(null), "All ${output.size} values are out, in order.", "O(n log k) for n values across k lists, with only k values held at once.",
        formula = listOf("merged = {v:${output.joinToString(" ")}}"), chips = listOf(StoryChip("output", output.joinToString(" "), StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Merge", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Popped"), Triple(SimColors.Blue, SwatchStyle.Fill, "In heap"), Triple(TextGrey, SwatchStyle.Fill, "Merged")),
        ),
    )
}

/** Reverses nodes [from, to] of A…E in place, one node per step; [groupSize] repeats it over fixed-size blocks. */
private fun reversalFrames(from: Int, to: Int, groupSize: Int? = null): List<TFrame> {
    val names = listOf("A", "B", "C", "D", "E")
    val n = names.size
    val next = MutableList<Int?>(n) { if (it + 1 < n) it + 1 else null }
    var head = 0
    val reversed = mutableSetOf<Int>()
    val frames = mutableListOf<TFrame>()
    val ranges = groupSize?.let { k -> (0 until n step k).mapNotNull { if (it + k - 1 < n) it to it + k - 1 else null } } ?: listOf(from to to)
    fun chain(prev: Int?, cur: Int?, save: Int?, cut: Pair<Int, Int>?): TChain {
        val above = mutableMapOf<Int, Pair<String, TTone>>()
        prev?.let { above[it] = "prev" to TTone.Matched }
        cur?.let { above[it] = "cur" to TTone.Active }
        save?.let { if (it !in above) above[it] = "next" to TTone.Blue }
        val below = mutableMapOf<Int, Pair<String, TTone>>()
        for (i in 0 until n) if (next[i] == null && i in reversed) below[i] = "next: null" to TTone.Matched
        if (head != 0 && head !in below) below[head] = "head" to TTone.Answer
        val special = mutableMapOf<Int, Pair<TTone, String?>>()
        if (cur != null) next[cur]?.let { t -> if (kotlin.math.abs(t - cur) == 1 && t < cur) special[cur] = TTone.Active to null }
        return TChain(
            names.mapIndexed { i, c -> TCell(c, if (i == cur) TTone.Active else if (i == save) TTone.Outline else if (i in reversed) TTone.Matched else TTone.Idle) },
            next.toList(), special, cut, above, below, reversed.associateWith { TTone.Matched },
        )
    }
    frames += TFrame(
        0, emptyList(),
        if (groupSize == null && from == 0) "Reverse the whole list by flipping one arrow at a time." else "Reverse only part of the list, then stitch it back in.",
        "Three pointers do all of it: prev, cur, and next saved before each flip.",
        formula = listOf(groupSize?.let { "reverse every $it nodes" } ?: "reverse nodes ${names[from]}…${names[to]}"),
        chips = listOf(StoryChip("prev", if (from == 0) "null" else names[from - 1]), StoryChip("cur", names[from])),
        chain = chain(null, from, null, null),
    )
    for ((lo, hi) in ranges) {
        val before = if (lo > 0) lo - 1 else null
        val after = next[hi]
        var prev: Int? = after
        var cur: Int? = lo
        while (cur != null && cur in lo..hi) {
            val c = cur
            val save = next[c]
            next[c] = prev
            reversed += c
            val prevName = prev?.let { names[it] } ?: "null"
            val saveName = save?.let { names[it] } ?: "null"
            frames += TFrame(
                0, emptyList(),
                "${names[c]}'s arrow flips from $saveName to $prevName" + when {
                    prev == after && after == null -> ", becoming the new tail."
                    prev == after -> ", pointing past the reversed block."
                    else -> ", joining the reversed part."
                },
                "next is saved first; without it, the rest of the list would be lost. No second list, O(1) space.",
                formula = listOf("next = {p:$saveName} · {${names[c]}}.next = {m:$prevName}", "· prev = ${names[c]} · cur = $saveName"),
                chips = listOf(StoryChip("prev", prevName), StoryChip("cur", names[c]), StoryChip("next", saveName)),
                chain = chain(prev, c, save?.takeIf { it <= hi }, save?.let { c to it }),
            )
            prev = c
            cur = save
        }
        if (before != null) next[before] = hi else head = hi
        frames += TFrame(
            0, emptyList(),
            before?.let { "${names[it]} now points at ${names[hi]}, the block's new first node." } ?: "${names[hi]} is the new head.",
            "The block's old first node, ${names[lo]}, already points at whatever followed the block.",
            formula = listOf(before?.let { "${names[it]}.next = {v:${names[hi]}}" } ?: "head = {v:${names[hi]}}"),
            chips = listOf(StoryChip("head", names[head])), chain = chain(null, null, null, null),
        )
    }
    val order = mutableListOf<String>()
    var at: Int? = head
    while (at != null && order.size <= n) {
        order += names[at]
        at = next[at]
    }
    frames += TFrame(
        0, emptyList(), "The list now reads {v:${order.joinToString(" → ")}}.", "Every node was visited once: O(n) time, O(1) space.",
        formula = listOf("list = {v:${order.joinToString(" → ")}}"), chips = listOf(StoryChip("head", names[head], StoryTone.Answer)),
        chain = chain(null, null, null, null),
    )
    return frames
}

private fun reversalTabs(): List<TTab> {
    val legend = listOf(
        Triple(SimColors.Active, SwatchStyle.Fill, "cur"),
        Triple(SimColors.Blue, SwatchStyle.Fill, "next (saved)"),
        Triple(SimColors.Green, SwatchStyle.Fill, "Reversed"),
        Triple(SimColors.Red, SwatchStyle.Fill, "Link cut"),
    )
    return listOf(
        TTab("Whole list", reversalFrames(0, 4), legend),
        TTab("Sublist", reversalFrames(1, 3), legend),
        TTab("K-group", reversalFrames(0, 4, groupSize = 2), legend),
    )
}

private fun greedyIntervalTabs(): List<TTab> {
    val meetings = listOf(1 to 4, 2 to 3, 3 to 5, 0 to 7, 6 to 8, 5 to 9)
    fun run(label: String, sorted: List<Pair<Int, Int>>, key: String): TTab {
        val fate = MutableList(sorted.size) { TTone.Idle }
        val kept = mutableListOf<Pair<Int, Int>>()
        var lastEnd = Int.MIN_VALUE
        fun tl(cur: Int?) = TTimeline(
            9.0, 1.0,
            sorted.mapIndexed { i, m -> TBar(m.first.toDouble(), m.second.toDouble(), if (i == cur) TTone.Active else fate[i], "${m.first}–${m.second}") },
            marker = if (lastEnd == Int.MIN_VALUE) null else lastEnd.toDouble(),
        )
        fun conflict(m: Pair<Int, Int>) = if (key == "length") kept.any { m.first < it.second && it.first < m.second } else m.first < lastEnd
        val frames = mutableListOf(
            TFrame(
                0, emptyList(), "Fit the most meetings into one room, sorted by $key.",
                if (key == "end") "Sorted by end, the meeting that finishes first always leaves the most room." else "Watch whether this order finds the best answer.",
                formula = listOf("sort by $key, keep each meeting that fits"), chips = listOf(StoryChip("kept", "0")), timeline = tl(null),
            ),
        )
        sorted.forEachIndexed { i, m ->
            val clash = conflict(m)
            val lastText = if (lastEnd == Int.MIN_VALUE) "–" else "$lastEnd"
            if (clash) {
                fate[i] = TTone.Mismatch
            } else {
                fate[i] = TTone.Matched
                kept += m
                lastEnd = maxOf(lastEnd, m.second)
            }
            frames += TFrame(
                0, emptyList(),
                if (clash) "${m.first}–${m.second} overlaps what is already kept, so it is skipped." else "${m.first}–${m.second} fits after everything kept, so it is kept.",
                when (key) {
                    "end" -> "Sorted by end, the meeting that finishes first always leaves the most room. Try By start to see it fail."
                    "start" -> "An early start can still run long and block everything after it."
                    else -> "Short meetings can still straddle two that would both fit."
                },
                formula = listOf(if (clash) "start {${m.first}} < lastEnd {p:$lastText} → overlaps → skip" else "start {${m.first}} ≥ lastEnd {p:$lastText} → keep"),
                chips = listOf(StoryChip("kept", "${kept.size}"), StoryChip("lastEnd", "$lastEnd")), timeline = tl(i),
            )
        }
        frames += TFrame(
            0, emptyList(), "Sorted by $key, {v:${kept.size}} meeting${if (kept.size == 1) "" else "s"} fit.",
            if (key == "end") "The exchange argument: swapping any kept meeting for the earliest-ending one never makes things worse, so this is optimal."
            else "Sorting by end gets 3 here. The order is the whole algorithm.",
            formula = listOf("kept = {v:${kept.size}}"), chips = listOf(StoryChip("kept", "${kept.size}", StoryTone.Answer)), timeline = tl(null),
        )
        return TTab(
            label, frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Considering"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Kept"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Skipped"),
                Triple(SimColors.Blue, SwatchStyle.Fill, "lastEnd"),
            ),
        )
    }
    return listOf(
        run("By end", meetings.sortedWith(compareBy({ it.second }, { it.first })), "end"),
        run("By start", meetings.sortedWith(compareBy({ it.first }, { it.second })), "start"),
        run("By length", meetings.sortedWith(compareBy({ it.second - it.first }, { it.first })), "length"),
    )
}

// ── More coding pattern storyboards ──────────────────────────────────────────
// In-place partitioning, modified binary search, divide and conquer, LIS, heap scheduling, hashing,
// parsing with a stack, monotonic deque and two heaps.

private fun partitionTabs(): List<TTab> {
    val start = listOf(3, 2, 3, 2, 2, 1, 3, 1, 1)
    val pivot = 2
    fun threeWay(): List<TFrame> {
        val a = start.toMutableList()
        var low = 0
        var mid = 0
        var high = a.lastIndex
        fun tags(): Map<Int, Pair<String, TTone>> {
            val t = mutableMapOf<Int, Pair<String, TTone>>()
            t[high] = "high" to TTone.Blue
            t[mid] = if (mid == high) "mid·high" to TTone.Active else "mid" to TTone.Active
            if (low != mid) t[low] = "low" to TTone.Answer else t[low] = (if (t.getValue(low).first == "mid") "low·mid" else t.getValue(low).first) to TTone.Active
            return t
        }
        fun bars(arc: Pair<Int, Int>?, done: Boolean = false) = TBars(
            a.toList(),
            a.indices.map { i ->
                when {
                    done -> if (a[i] == pivot) TTone.Answer else TTone.Green
                    i < low -> TTone.Green
                    i < mid -> TTone.Answer
                    i == mid -> TTone.Active
                    i == high -> TTone.Blue
                    i > high -> TTone.Green
                    else -> TTone.Idle
                }
            },
            if (done) emptyMap() else tags(), arc,
        )
        fun chips() = listOf(StoryChip("low", "$low"), StoryChip("mid", "$mid"), StoryChip("high", "$high"))
        val frames = mutableListOf(
            TFrame(
                0, emptyList(), "Sort 1s, 2s and 3s in one pass by partitioning around $pivot.",
                "Everything left of low is settled small, low to mid equals $pivot, right of high is settled large. mid scans the unknown part.",
                formula = listOf("< $pivot to the front · = $pivot in the middle · > $pivot to the back"), chips = chips(), bars = bars(null),
            ),
        )
        while (mid <= high) {
            val v = a[mid]
            if (v < pivot) {
                frames += TFrame(
                    0, emptyList(),
                    if (low == mid) "$v is small and already at low, so both pointers step forward." else "$v is small, so it swaps down to low and both pointers step forward.",
                    "Swaps with low bring in a value already seen, so mid can advance. Swaps with high don't.",
                    formula = listOf("a[mid] {$v}", "< $pivot → swap($low, $mid) · low = ${low + 1} · mid = ${mid + 1}"), chips = chips(),
                    bars = bars(if (low == mid) null else mid to low),
                )
                a[low] = a[mid].also { a[mid] = a[low] }
                low++
                mid++
            } else if (v == pivot) {
                frames += TFrame(
                    0, emptyList(), "$v equals the pivot, so it stays and mid moves on.", "The run from low to mid grows by one without a swap.",
                    formula = listOf("a[mid] {$v}", "= $pivot → already in the middle · mid = ${mid + 1}"), chips = chips(), bars = bars(null),
                )
                mid++
            } else {
                frames += TFrame(
                    0, emptyList(),
                    if (mid == high) "$v is large and already at high, so high steps back."
                    else "$v goes to the back, but mid doesn't move: the ${a[high]} that came in from index $high hasn't been checked.",
                    "Swaps with low bring in a value already seen, so mid can advance. Swaps with high don't.",
                    formula = listOf("a[mid] {$v}", "> $pivot → swap($mid, $high) · high = ${high - 1} · mid stays $mid"), chips = chips(),
                    bars = bars(if (mid == high) null else mid to high),
                )
                a[mid] = a[high].also { a[high] = a[mid] }
                high--
            }
        }
        frames += TFrame(
            0, emptyList(), "mid has passed high: three regions in {v:one pass}.", "Every element is looked at once and swapped at most once. O(n) time, O(1) space.",
            formula = listOf("mid $mid > high $high → {v:done}"), chips = chips(), bars = bars(null, done = true),
        )
        return frames
    }
    fun twoWay(): List<TFrame> {
        val a = start.toMutableList()
        var b = 0
        fun bars(i: Int?, arc: Pair<Int, Int>? = null): TBars {
            val tags = mutableMapOf<Int, Pair<String, TTone>>()
            if (b < a.size) tags[b] = "b" to TTone.Blue
            if (i != null) tags[i] = if (i == b) "b·i" to TTone.Active else "i" to TTone.Active
            return TBars(
                a.toList(),
                a.indices.map { k ->
                    when {
                        k == i -> TTone.Active
                        k < b -> TTone.Green
                        k == b && i != null -> TTone.Blue
                        i == null || k < i -> TTone.Answer
                        else -> TTone.Idle
                    }
                },
                if (i == null) emptyMap() else tags, arc,
            )
        }
        val frames = mutableListOf(
            TFrame(
                0, emptyList(), "Two regions only: move every value below $pivot to the front.", "b marks where the next small value goes. i scans left to right.",
                formula = listOf("values < $pivot go left of b, everything else stays right"), chips = listOf(StoryChip("b", "0"), StoryChip("i", "0")), bars = bars(0),
            ),
        )
        for (i in a.indices) {
            if (a[i] < pivot) {
                frames += TFrame(
                    0, emptyList(), if (b == i) "${a[i]} is small and already at b." else "${a[i]} is small, so it swaps with the ${a[b]} at b.",
                    "Everything left of b is below $pivot; from b to i is $pivot or more.",
                    formula = listOf("a[$i] {${a[i]}} < $pivot → swap($b, $i) · b = ${b + 1}"), chips = listOf(StoryChip("b", "$b"), StoryChip("i", "$i")),
                    bars = bars(i, if (b == i) null else i to b),
                )
                a[b] = a[i].also { a[i] = a[b] }
                b++
            } else {
                frames += TFrame(
                    0, emptyList(), "${a[i]} is not below $pivot, so it stays where it is.", "Only small values move; large ones drift right as small ones swap past them.",
                    formula = listOf("a[$i] {${a[i]}} ≥ $pivot → leave it"), chips = listOf(StoryChip("b", "$b"), StoryChip("i", "$i")), bars = bars(i),
                )
            }
        }
        frames += TFrame(
            0, emptyList(), "The first {v:$b} slots hold every value below $pivot.", "One pass, like quicksort's Lomuto partition. The order inside each region is not kept.",
            formula = listOf("{v:$b} values < $pivot, then the rest"), chips = listOf(StoryChip("b", "$b", StoryTone.Answer)), bars = bars(null),
        )
        return frames
    }
    return listOf(
        TTab(
            "3-way", threeWay(),
            listOf(Triple(SimColors.Green, SwatchStyle.Fill, "Settled"), Triple(SimColors.Answer, SwatchStyle.Fill, "Equal to 2"), Triple(SimColors.Active, SwatchStyle.Fill, "mid"), Triple(SimColors.Blue, SwatchStyle.Fill, "high")),
        ),
        TTab(
            "2-way", twoWay(),
            listOf(Triple(SimColors.Green, SwatchStyle.Fill, "Below 2"), Triple(SimColors.Answer, SwatchStyle.Fill, "2 or more"), Triple(SimColors.Active, SwatchStyle.Fill, "i"), Triple(SimColors.Blue, SwatchStyle.Fill, "b")),
        ),
    )
}

private fun rotatedSearchTabs(): List<TTab> {
    val a = listOf(27, 34, 42, 50, 61, 73, 88, 3, 8, 15)
    val target = 8
    val n = a.size
    var lo = 0
    var hi = n - 1
    val probes = mutableListOf<Pair<String, String>>()
    val frames = mutableListOf<TFrame>()
    fun row(l: Int, m: Int, h: Int, found: Boolean): TRow {
        val tags = mutableMapOf<Int, Pair<String, TTone>>()
        tags[l] = "lo" to TTone.Blue
        tags[h] = if (l == h) "lo·hi" to TTone.Blue else "hi" to TTone.Blue
        tags[m] = when {
            m == l && m == h -> "lo·mid·hi"
            m == l -> "lo·mid"
            m == h -> "mid·hi"
            else -> "mid"
        } to TTone.Active
        return TRow(a.mapIndexed { i, v -> TCell("$v", if (i == m) (if (found) TTone.Answer else TTone.Active) else if (i in l..h) TTone.Outline else TTone.Pending) }, tags = tags)
    }
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val l = lo
        val h = hi
        val chips = listOf(StoryChip("lo", "$l"), StoryChip("mid", "$mid"), StoryChip("hi", "$h"))
        if (a[mid] == target) {
            probes += "a[$mid] = ${a[mid]}, found" to "i = $mid"
            frames += TFrame(
                n, listOf(row(l, mid, h, true)), "a[$mid] = $target: found at {v:index $mid} after ${probes.size} probes.",
                "The rotation never cost an extra probe; each step still halved the window.",
                lit = mapOf(mid to TTone.Active), formula = listOf("a[$mid] {${a[mid]}} = target → {v:found at $mid}"), chips = chips,
                heading = "ROTATED ARRAY · TARGET $target", listTitle = "PROBES",
            )
            break
        }
        val leftSorted = a[lo] <= a[mid]
        val formula: List<String>
        val headline: String
        val move: String
        if (leftSorted) {
            val inside = a[lo] <= target && target < a[mid]
            if (inside) { hi = mid - 1; move = "hi = $hi" } else { lo = mid + 1; move = "lo = $lo" }
            formula = listOf("a[$mid] {${a[mid]}}", "≥ a[$l] ${a[l]} → left sorted · ${if (inside) "" else "not "}${a[l]} ≤ $target < ${a[mid]} → $move")
            headline = "${a[l]}…${a[mid]} is in order, and $target ${if (inside) "falls inside it" else "isn't in it"}, so ${if (inside) "keep the left half" else "drop the left half"}."
        } else {
            val inside = a[mid] < target && target <= a[hi]
            if (inside) { lo = mid + 1; move = "lo = $lo" } else { hi = mid - 1; move = "hi = $hi" }
            formula = listOf("a[$mid] {${a[mid]}}", "≤ a[$h] ${a[h]} → right sorted · ${a[mid]} < $target ≤ ${a[h]} → $move")
            headline = "${a.subList(l, mid + 1).joinToString(", ")} isn't in order, so the right half ${a[mid]}…${a[h]} must be. $target ${if (inside) "falls inside it" else "isn't in it"}."
        }
        probes += "a[$mid] = ${a[mid]}, ${if (leftSorted) "left" else "right"} sorted" to move
        frames += TFrame(
            n, listOf(row(l, mid, h, false)), headline, "Every probe, one half is sorted. Range-check that half and discard the other; still O(log n).",
            lit = mapOf(mid to TTone.Active), formula = formula, chips = chips, heading = "ROTATED ARRAY · TARGET $target", listTitle = "PROBES",
        )
    }
    val withLists = frames.mapIndexed { k, f ->
        TFrame(
            f.columns, f.rows, f.headline, f.body, lit = f.lit, formula = f.formula, chips = f.chips, heading = f.heading, listTitle = f.listTitle,
            list = probes.take(k + 1).mapIndexed { j, p -> TListRow("${j + 1}", p.first, p.second, "", if (j == k) TTone.Outline else TTone.Pending) },
        )
    }
    return listOf(
        TTab(
            "Rotated", withLists,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Probe"), Triple(SimColors.Blue, SwatchStyle.Fill, "Live window"), Triple(Color.Gray.copy(alpha = 0.25f), SwatchStyle.Fill, "Ruled out")),
        ),
    )
}

private fun divideConquerTabs(): List<TTab> {
    val input = listOf(5, 3, 8, 2, 1, 9)
    fun run(count: Boolean): List<TFrame> {
        val frames = mutableListOf(
            TFrame(
                0, listOf(TRow(input.map { TCell("$it", TTone.Idle) }, title = "INPUT", spread = true)),
                if (count) "Count pairs i < j with a[i] > a[j] by piggybacking on merge sort." else "Split in half, sort each half, merge the two sorted runs.",
                if (count) "Pairs inside a half are counted by recursion; only pairs across the middle need the merge." else "Halving gives log n levels, and each level's merges touch n values.",
                formula = listOf(if (count) "inversions = left + right + cross" else "sort each half, then merge"),
                chips = if (count) listOf(StoryChip("inversions", "0")) else emptyList(),
            ),
        )
        fun sort(a: List<Int>): Pair<List<Int>, Int> {
            if (a.size < 2) return a to 0
            val (l, li) = sort(a.subList(0, a.size / 2))
            val (r, ri) = sort(a.subList(a.size / 2, a.size))
            var i = 0
            var j = 0
            var cross = 0
            val merged = mutableListOf<Int>()
            fun rows(ci: Int?, cj: Int?): List<TRow> {
                val cells = l.mapIndexed { k, v -> TCell("$v", if (k < i) TTone.Pending else if (k == ci) TTone.Active else TTone.Outline) } +
                    r.mapIndexed { k, v -> TCell("$v", if (k < j) TTone.Pending else if (k == cj) TTone.Active else TTone.Idle) }
                return listOf(
                    TRow(cells, title = "LEFT · SORTED", spread = true, split = l.size, splitTitle = "RIGHT · SORTED"),
                    TRow((0 until l.size + r.size).map { k -> if (k < merged.size) TCell("${merged[k]}", TTone.Answer) else TCell("", TTone.Ghost) }, title = "MERGED", spread = true),
                )
            }
            fun chips() = if (count) listOf(StoryChip("left", "$li"), StoryChip("right", "$ri"), StoryChip("cross", "$cross so far"))
            else listOf(StoryChip("merged", "${merged.size} / ${l.size + r.size}"))
            while (i < l.size || j < r.size) {
                if (i < l.size && j < r.size) {
                    val ci = i
                    val cj = j
                    if (r[j] < l[i]) {
                        val beats = l.size - i
                        merged += r[j]
                        j++
                        cross += beats
                        frames += TFrame(
                            0, rows(ci, cj),
                            if (count) {
                                if (beats == 1) "${r[cj]} is smaller than ${l[ci]}: one inversion."
                                else "${r[cj]} is smaller than ${l[ci]}, so it's also smaller than ${l.subList(ci + 1, l.size).joinToString(" and ")}. That is $beats inversions in one step."
                            } else "${r[cj]} is smaller than ${l[ci]}, so it goes next.",
                            if (count) "Both halves are sorted, so one comparison counts a whole run. The count comes free with an O(n log n) merge sort."
                            else "Only the two front values can be the smallest left, so one comparison picks it.",
                            formula = if (count) listOf("{${r[cj]}} < {${l[ci]}}", "→ ${r[cj]} beats all $beats left value${if (beats == 1) "" else "s"} → count += {v:$beats}")
                            else listOf("{${r[cj]}} < {${l[ci]}} → take ${r[cj]} from the right"),
                            chips = chips(),
                        )
                    } else {
                        merged += l[i]
                        i++
                        frames += TFrame(
                            0, rows(ci, cj), "${l[ci]} is not bigger than ${r[cj]}, so it goes next${if (count) " and adds nothing" else ""}.",
                            if (count) "A left value taken first is smaller than everything left in the right half, so it forms no cross pair."
                            else "Taking the left one on ties keeps merge sort stable.",
                            formula = listOf("{${l[ci]}} ≤ {${r[cj]}} → take ${l[ci]} from the left" + if (count) " · no inversion" else ""),
                            chips = chips(),
                        )
                    }
                } else {
                    val fromLeft = i < l.size
                    val rest = if (fromLeft) l.subList(i, l.size) else r.subList(j, r.size)
                    merged += rest
                    if (fromLeft) i = l.size else j = r.size
                    frames += TFrame(
                        0, rows(null, null),
                        "The ${if (fromLeft) "right" else "left"} half is used up, so ${rest.joinToString(", ")} follow${if (rest.size == 1) "s" else ""} as they are.",
                        if (count) "Leftover values cross nothing new: every pair they form was already counted." else "The leftovers are already sorted, so they are copied in one go.",
                        formula = listOf("${if (fromLeft) "right" else "left"} is empty → copy ${rest.joinToString(", ")}"), chips = chips(),
                    )
                }
            }
            return merged to li + ri + cross
        }
        val (sorted, total) = sort(input)
        frames += TFrame(
            0, listOf(TRow(sorted.map { TCell("$it", TTone.Answer) }, title = "SORTED", spread = true)),
            if (count) "The array has {v:$total} inversions." else "Every merge done: the array is sorted.",
            if (count) "Checking every pair would be O(n²); counting during merges keeps it O(n log n)." else "log n levels of merging, each O(n): O(n log n) always.",
            formula = listOf(if (count) "inversions = {v:$total}" else "sorted = {v:${sorted.joinToString(" ")}}"),
            chips = if (count) listOf(StoryChip("inversions", "$total", StoryTone.Answer)) else listOf(StoryChip("sorted", "${sorted.size}", StoryTone.Answer)),
        )
        return frames
    }
    val legend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Comparing"), Triple(SimColors.Blue, SwatchStyle.Fill, "Still in left"), Triple(SimColors.Answer, SwatchStyle.Fill, "Merged"))
    return listOf(TTab("Count inversions", run(true), legend), TTab("Merge sort", run(false), legend))
}

private fun lisTabs(): List<TTab> {
    val a = listOf(10, 9, 2, 5, 3, 7, 101, 18)
    val n = a.size
    fun numsRow(i: Int?, done: Boolean = false) = TRow(
        a.mapIndexed { k, v -> TCell("$v", if (k == i) TTone.Active else if (if (i != null) k < i else done) TTone.Pending else TTone.Idle) },
    )
    val tails = mutableListOf<Int>()
    val slots = mutableListOf<Int>().also { t ->
        for (x in a) {
            val p = t.indexOfFirst { it >= x }.let { if (it < 0) t.size else it }
            if (p == t.size) t += x else t[p] = x
        }
    }.size
    fun tailsRow(changed: Int?, note: String?, answer: Boolean = false): TRow {
        val tags = tails.indices.associateWith { k ->
            if (k == changed) "len ${k + 1}${note?.let { " · $it" } ?: ""}" to TTone.Active else "len ${k + 1}" to TTone.Idle
        }
        return TRow(
            (0 until slots).map { k ->
                if (k < tails.size) TCell("${tails[k]}", if (answer) TTone.Answer else if (k == changed) TTone.Active else TTone.Outline) else TCell("", TTone.Ghost)
            },
            title = "TAILS · INDEX K = LENGTH K + 1", tags = tags, spread = true,
        )
    }
    fun tailsText() = "[" + tails.joinToString(", ") + "]"
    val frames = mutableListOf(
        TFrame(
            n, listOf(numsRow(null), tailsRow(null, null)), "Find the longest strictly increasing subsequence.",
            "Keep, for every length, the smallest value a run of that length can end with.",
            formula = listOf("tails[k] = smallest end of any increasing run of length k + 1"),
            chips = listOf(StoryChip("length", "0"), StoryChip("tails", "[]")),
        ),
    )
    a.forEachIndexed { i, x ->
        val p = tails.indexOfFirst { it >= x }.let { if (it < 0) tails.size else it }
        if (p == tails.size) {
            tails += x
            frames += TFrame(
                n, listOf(numsRow(i), tailsRow(p, "new")),
                if (p == 0) "$x starts the first run." else "$x is bigger than every tail, so the longest run grows to ${p + 1}.",
                "A new length only appears when a value beats every tail.",
                lit = mapOf(i to TTone.Active), formula = listOf("lower_bound(tails, {$x}) = $p = size → append {$x}"),
                chips = listOf(StoryChip("length", "${tails.size}"), StoryChip("tails", tailsText())),
            )
        } else {
            val old = tails[p]
            tails[p] = x
            frames += TFrame(
                n, listOf(numsRow(i), tailsRow(p, "was $old")), "$x replaces $old: a length-${p + 1} run can now end lower.",
                "The length doesn't change, but a smaller tail lets more later values extend it. Binary search makes each step O(log n).",
                lit = mapOf(i to TTone.Active), formula = listOf("lower_bound(tails, {$x}) = $p → tails[$p]: $old → {$x}"),
                chips = listOf(StoryChip("length", "${tails.size}"), StoryChip("tails", tailsText())),
            )
        }
    }
    frames += TFrame(
        n, listOf(numsRow(null, done = true), tailsRow(null, null, answer = true)), "The longest increasing subsequence has length {v:${tails.size}}.",
        "tails is not itself a subsequence, only its length is the answer. O(n log n) overall.",
        formula = listOf("LIS length = size of tails = {v:${tails.size}}"), chips = listOf(StoryChip("length", "${tails.size}", StoryTone.Answer)),
    )
    val dp = MutableList<Int?>(n) { null }
    fun dpRows(i: Int?, from: List<Int>, best: Int?) = listOf(
        TRow(a.mapIndexed { k, v -> TCell("$v", if (k == i) TTone.Active else if (k == best) TTone.Green else if (k in from) TTone.Outline else TTone.Idle) }, "a"),
        TRow(dp.mapIndexed { k, v -> v?.let { TCell("$it", if (k == i) TTone.Active else if (k == best) TTone.Green else TTone.Idle) } ?: TCell("·", TTone.Pending) }, "dp"),
    )
    val dpFrames = mutableListOf(
        TFrame(
            n, dpRows(null, emptyList(), null), "dp[i] is the longest increasing run ending at a[i].", "Each i looks back at every earlier j, so this version is O(n²).",
            formula = listOf("dp[i] = 1 + max(dp[j]) over j < i with a[j] < a[i]"), chips = listOf(StoryChip("best", "0")),
        ),
    )
    for (i in 0 until n) {
        val from = (0 until i).filter { a[it] < a[i] }
        val best = from.maxByOrNull { dp[it]!! }
        dp[i] = 1 + (best?.let { dp[it]!! } ?: 0)
        val overall = dp.filterNotNull().max()
        dpFrames += TFrame(
            n, dpRows(i, from, best),
            best?.let { "${a[i]} extends the run ending at ${a[it]}, reaching length ${dp[i]}." } ?: "Nothing before ${a[i]} is smaller, so it starts a run of 1.",
            "${from.size} earlier value${if (from.size == 1) " is" else "s are"} smaller; the one with the longest run wins.",
            lit = mapOf(i to TTone.Active),
            formula = listOf(best?.let { "dp[$i] = 1 + dp[$it] ${dp[it]} = {${dp[i]}}" } ?: "no smaller value before {${a[i]}} → dp[$i] = {1}"),
            chips = listOf(StoryChip("best", "$overall")),
        )
    }
    val overall = dp.filterNotNull().max()
    dpFrames += TFrame(
        n, dpRows(null, emptyList(), null), "The largest dp value, {v:$overall}, is the answer.", "Same answer as tails, but n² comparisons instead of n log n.",
        formula = listOf("max(dp) = {v:$overall}"), chips = listOf(StoryChip("best", "$overall", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Tails + binary search", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current / replaced"), Triple(SimColors.Blue, SwatchStyle.Fill, "tails"), Triple(SimColors.Answer, SwatchStyle.Fill, "Answer")),
        ),
        TTab(
            "DP O(n²)", dpFrames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current"), Triple(SimColors.Blue, SwatchStyle.Fill, "Smaller earlier"), Triple(SimColors.Green, SwatchStyle.Fill, "Best to extend")),
        ),
    )
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

private fun heapSchedulingTabs(): List<TTab> {
    val meetings = listOf(0 to 30, 5 to 10, 6 to 12, 15 to 20, 25 to 35)
    val heap = mutableListOf<Pair<Int, Int>>() // end to room
    val room = MutableList<Int?>(meetings.size) { null }
    val ended = mutableSetOf<Int>()
    var rooms = 0
    fun timeline(cur: Int?) = TTimeline(
        35.0, 5.0,
        meetings.mapIndexed { i, m ->
            TBar(
                m.first.toDouble(), m.second.toDouble(),
                if (i == cur) TTone.Active else if (i in ended) TTone.Mismatch else if (room[i] != null) TTone.Blue else TTone.Idle,
                "${m.first}–${m.second}", room[i]?.let { "R$it" },
            )
        },
        marker = cur?.let { meetings[it].first.toDouble() }, markerTone = TTone.Active,
    )
    fun heapRow(popped: Int?, pushed: Int?): TRow {
        val cells = mutableListOf<TCell>()
        popped?.let { cells += TCell("$it", TTone.Mismatch) }
        cells += heap.map { it.first }.filter { pushed == null || it != pushed }.sorted().map { TCell("$it", TTone.Outline) }
        pushed?.let { cells += TCell("+$it", TTone.Active) }
        return TRow(cells.ifEmpty { listOf(TCell("", TTone.Ghost)) }, title = "MIN-HEAP OF END TIMES", spread = true)
    }
    val frames = mutableListOf(
        TFrame(
            0, listOf(heapRow(null, null)), "How many rooms do these meetings need?",
            "Walk the meetings by start time. The heap's top is the room that frees up first.",
            formula = listOf("sort by start · heap holds the end time of every busy room"),
            chips = listOf(StoryChip("rooms", "0"), StoryChip("heap", "0")), timeline = timeline(null), timelineFirst = true,
        ),
    )
    var peak = 0
    meetings.forEachIndexed { i, m ->
        var popped: Int? = null
        val formula: String
        val headline: String
        val top = heap.minByOrNull { it.first }
        if (top != null && top.first <= m.first) {
            heap.remove(top)
            popped = top.first
            val freed = meetings.indices.first { room[it] == top.second && meetings[it].second == top.first }
            ended += freed
            room[i] = top.second
            formula = "heap.min {w:${top.first}} ≤ start {${m.first}} → pop ${top.first}, push {${m.second}} · rooms stay $rooms"
            headline = "${m.first}–${m.second} starts after ${meetings[freed].first}–${meetings[freed].second} ended, so it takes that room instead of opening a ${ordinal(rooms + 1)}."
        } else {
            rooms++
            room[i] = rooms
            formula = top?.let { "heap.min {p:${it.first}} > start {${m.first}} → push {${m.second}} · rooms = $rooms" } ?: "heap empty → push {${m.second}} · rooms = 1"
            headline = top?.let { "Every room is busy until at least ${it.first}, so ${m.first}–${m.second} opens room $rooms." } ?: "${m.first}–${m.second} is first, so it opens room 1."
        }
        heap += m.second to room[i]!!
        peak = maxOf(peak, heap.size)
        frames += TFrame(
            0, listOf(heapRow(popped, m.second)), headline, "Only the earliest end time matters, and the heap keeps it on top. The peak heap size is the answer.",
            formula = listOf(formula), chips = listOf(StoryChip("rooms", "$rooms"), StoryChip("heap", "${heap.size}")), timeline = timeline(i), timelineFirst = true,
        )
    }
    frames += TFrame(
        0, listOf(heapRow(null, null)), "{v:$peak} rooms are enough for all ${meetings.size} meetings.",
        "Sorting is O(n log n) and each meeting does one push and at most one pop, O(log n) each.",
        formula = listOf("peak heap size = {v:$peak}"), chips = listOf(StoryChip("rooms", "$peak", StoryTone.Answer)), timeline = timeline(null), timelineFirst = true,
    )
    return listOf(
        TTab(
            "Rooms", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Starting now"), Triple(SimColors.Blue, SwatchStyle.Fill, "Room in use"), Triple(SimColors.Red, SwatchStyle.Fill, "Ended")),
        ),
    )
}

private fun hashCountingTabs(): List<TTab> {
    val nums = listOf(3, 4, 7, 2, -3, 1, 4, 2)
    val k = 7
    fun subarray(): List<TFrame> {
        val seen = mutableListOf(0 to 1)
        val lastAt = mutableMapOf(0 to -1)
        val prefixes = mutableListOf<Int>()
        var found = 0
        fun numsRow(i: Int?, slice: IntRange?) = TRow(
            nums.mapIndexed { j, v ->
                TCell(
                    "$v",
                    when {
                        j == i -> TTone.Active
                        slice != null && j in slice -> TTone.Outline
                        if (i != null) j < i else prefixes.isNotEmpty() -> TTone.Pending
                        else -> TTone.Idle
                    },
                )
            },
            title = "NUMS · K = $k", tags = prefixes.mapIndexed { j, p -> j to ("$p" to if (j == i) TTone.Active else TTone.Idle) }.toMap(), spread = true,
        )
        fun seenRow(matched: Int?, stored: Int?) = TRow(
            seen.map { (p, c) -> TCell("$p ×$c", if (p == matched) TTone.Green else if (p == stored) TTone.Outline else TTone.Idle) },
            title = "SEEN PREFIX SUMS · COUNT", spread = true, compact = seen.size > 6,
        )
        val frames = mutableListOf(
            TFrame(
                0, listOf(numsRow(null, null), seenRow(null, null)), "Count the slices that sum to $k in one pass.",
                "Store every prefix sum seen so far. A slice ending here sums to k when prefix − k was seen before.",
                formula = listOf("slice (j, i] sums to k ⇔ prefix[i] − k = prefix[j]"), chips = listOf(StoryChip("found", "0")),
            ),
        )
        var sum = 0
        nums.forEachIndexed { i, v ->
            sum += v
            prefixes += sum
            val need = sum - k
            val hits = seen.firstOrNull { it.first == need }?.second ?: 0
            val from = lastAt[need]?.let { it + 1 }
            found += hits
            val idx = seen.indexOfFirst { it.first == sum }
            if (idx >= 0) seen[idx] = sum to seen[idx].second + 1 else seen += sum to 1
            val slice = if (hits > 0) from!!..i else null
            val sliceText = slice?.let { r -> r.mapIndexed { j, x -> nums[x].let { if (j == 0) "$it" else if (it < 0) "− ${-it}" else "+ $it" } }.joinToString(" ") }
            frames += TFrame(
                0, listOf(numsRow(i, slice?.let { if (it.first < i) it.first until i else null }), seenRow(if (hits > 0) need else null, sum)),
                if (hits > 0) "Prefix $sum minus $k is $need, a prefix seen ${if (from == 0) "before the start" else "at index ${from!! - 1}"}. The slice between them sums to $k."
                else "Prefix $sum minus $k is $need, never seen, so no slice ending here sums to $k.",
                "One lookup replaces scanning back through every start, so the whole pass is O(n).",
                formula = listOf(
                    "prefix {$sum} − k $k = ${if (hits > 0) "{m:$need}" else "$need"}",
                    if (hits > 0) "· seen[$need] = $hits → found += $hits" else "· $need not seen → found stays $found",
                ),
                chips = listOf(StoryChip("found", "$found")) + (sliceText?.let { listOf(StoryChip("sum", it)) } ?: emptyList()),
            )
            lastAt[sum] = i
        }
        frames += TFrame(
            0, listOf(numsRow(null, null), seenRow(null, null)), "{v:$found} slices sum to $k.", "O(n) time and O(n) space, and negatives are fine, unlike a sliding window.",
            formula = listOf("found = {v:$found}"), chips = listOf(StoryChip("found", "$found", StoryTone.Answer)),
        )
        return frames
    }
    fun twoSum(): List<TFrame> {
        val a = listOf(3, 8, 4, 11, 6, 2)
        val target = 10
        val seen = mutableListOf<Pair<Int, Int>>()
        fun rows(i: Int?, hit: Int?) = listOf(
            TRow(
                a.mapIndexed { j, v -> TCell("$v", if (j == i) TTone.Active else if (j == hit) TTone.Green else if (i != null && j < i) TTone.Pending else TTone.Idle) },
                title = "NUMS · TARGET $target", tags = a.indices.associateWith { "i$it" to TTone.Idle }, spread = true,
            ),
            TRow(
                if (seen.isEmpty()) listOf(TCell("", TTone.Ghost)) else seen.map { (v, j) -> TCell("$v → i$j", if (j == hit) TTone.Green else TTone.Outline) },
                title = "SEEN · VALUE → INDEX", spread = true, compact = seen.size > 4,
            ),
        )
        val frames = mutableListOf(
            TFrame(
                0, rows(null, null), "Find two values that add to $target.", "Instead of trying every pair, remember each value's index and look up its partner.",
                formula = listOf("for each x: is target − x already seen?"), chips = listOf(StoryChip("seen", "0")),
            ),
        )
        for ((i, x) in a.withIndex()) {
            val need = target - x
            val j = seen.firstOrNull { it.first == need }?.second
            if (j != null) {
                frames += TFrame(
                    0, rows(i, j), "$need was seen at index $j, so {v:$need + $x = $target}.", "One pass and one lookup per value: O(n) instead of O(n²).",
                    formula = listOf("target $target − {$x} = {m:$need}", "· seen[$need] = i$j → pair ({v:$j, $i})"), chips = listOf(StoryChip("pair", "($j, $i)", StoryTone.Answer)),
                )
                return frames
            }
            seen += x to i
            frames += TFrame(
                0, rows(i, null), "$x needs $need, which hasn't appeared yet, so remember $x.", "A later value may need $x; the map answers that in O(1).",
                formula = listOf("target $target − {$x} = $need · not seen → store $x → i$i"), chips = listOf(StoryChip("seen", "${seen.size}")),
            )
        }
        return frames
    }
    return listOf(
        TTab(
            "Subarray sum = k", subarray(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current prefix"), Triple(SimColors.Green, SwatchStyle.Fill, "Matched"), Triple(SimColors.Blue, SwatchStyle.Fill, "Stored")),
        ),
        TTab(
            "Two sum", twoSum(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current"), Triple(SimColors.Green, SwatchStyle.Fill, "Partner"), Triple(SimColors.Blue, SwatchStyle.Fill, "Stored")),
        ),
    )
}

private fun parsingStackTabs(): List<TTab> {
    fun qt(x: String) = "\"$x\""
    fun decode(): List<TFrame> {
        val s = chars("3[a2[bc]]")
        val n = s.size
        val stack = mutableListOf<Pair<Int, String>>()
        var cur = ""
        var num = 0
        var maxDepth = 0
        run { var d = 0; for (c in s) { if (c == "[") { d++; maxDepth = maxOf(maxDepth, d) } else if (c == "]") d-- } }
        fun rows(i: Int?, hot: Int?, popped: Pair<Int, String>? = null): List<TRow> {
            val cells = stack.mapIndexed { k, f -> TCell("${f.first} × ${qt(f.second)}", if (k == hot) TTone.Active else TTone.Outline) }.toMutableList()
            popped?.let { cells += TCell("${it.first} × ${qt(it.second)}", TTone.Active) }
            while (cells.size < maxDepth + 1) cells += TCell("", TTone.Ghost)
            return listOf(
                TRow(s.mapIndexed { k, c -> TCell(c, if (k == i) TTone.Active else if (i != null && k < i) TTone.Pending else TTone.Idle) }),
                TRow(cells, title = "STACK · COUNT × TEXT BEFORE", spread = true),
            )
        }
        val frames = mutableListOf(
            TFrame(
                n, rows(null, null), "Decode 3[a2[bc]] with a stack instead of recursion.", "[ suspends the text built so far, ] folds the inner text back into it.",
                formula = listOf("k[text] → text repeated k times"), chips = listOf(StoryChip("cur", qt("")), StoryChip("depth", "0")),
            ),
        )
        s.forEachIndexed { i, c ->
            val formula: String
            val headline: String
            val body: String
            var popped: Pair<Int, String>? = null
            var hot: Int? = null
            val d = c.toIntOrNull()
            if (d != null) {
                num = num * 10 + d
                formula = "num = {$num}"
                headline = "$c is a count; hold it until the bracket opens."
                body = "Multi-digit counts build up digit by digit."
            } else if (c == "[") {
                stack += num to cur
                hot = stack.lastIndex
                formula = "push ($num, ${qt(cur)}) · cur = ${qt("")}"
                headline = "[ saves $num × ${qt(cur)} and starts a fresh inner text."
                body = "Each frame saves exactly what a recursive call would, the count and the text before it."
                cur = ""
                num = 0
            } else if (c == "]") {
                val (k, before) = stack.removeAt(stack.lastIndex)
                popped = k to before
                val inner = cur
                cur = before + inner.repeat(k)
                formula = "cur = ${qt(before)} + {${qt(inner)}} × $k = {v:${qt(cur)}}"
                headline = "] pops $k × ${qt(before)}: repeat ${qt(inner)} ${when (k) { 2 -> "twice"; 3 -> "three times"; else -> "$k times" }}" +
                    (if (before.isEmpty()) "" else " and prepend ${qt(before)}") + "."
                body = if (stack.isEmpty()) "The stack is empty again, so cur is the whole decoded string."
                else "Each frame saves exactly what a recursive call would, the count and the text before it. The outer ${stack.last().first} is still waiting."
            } else {
                val was = cur
                cur += c
                formula = "cur = ${qt(was)} + {$c} = ${qt(cur)}"
                headline = "$c is plain text, so it joins the current text."
                body = "Letters only ever append to the innermost text being built."
            }
            frames += TFrame(
                n, rows(i, hot, popped), headline, body,
                lit = mapOf(i to TTone.Active), formula = listOf(formula), chips = listOf(StoryChip("cur", qt(cur)), StoryChip("depth", "${stack.size}")),
            )
        }
        frames += TFrame(
            n, rows(null, null), "Decoded: {v:$cur}.", "One pass, and the stack never grows deeper than the nesting: O(output) time.",
            formula = listOf("result = {v:${qt(cur)}}"), chips = listOf(StoryChip("result", qt(cur), StoryTone.Answer)),
        )
        return frames
    }
    fun brackets(): List<TFrame> {
        val s = chars("([()[]])(]")
        val n = s.size
        val pair = mapOf(")" to "(", "]" to "[")
        val stack = mutableListOf<Pair<String, Int>>()
        var maxDepth = 0
        run { var d = 0; for (c in s) { if (c !in pair) { d++; maxDepth = maxOf(maxDepth, d) } else d-- } }
        fun rows(i: Int?, hot: Int?, bad: Boolean = false): List<TRow> {
            val cells = stack.mapIndexed { k, f -> TCell("${f.first} · i${f.second}", if (k == hot) (if (bad) TTone.Mismatch else TTone.Active) else TTone.Outline) }.toMutableList()
            while (cells.size < maxDepth + 1) cells += TCell("", TTone.Ghost)
            return listOf(
                TRow(s.mapIndexed { k, c -> TCell(c, if (k == i) (if (bad) TTone.Mismatch else TTone.Active) else if (i != null && k < i) TTone.Pending else TTone.Idle) }),
                TRow(cells, title = "STACK · OPEN BRACKETS", spread = true),
            )
        }
        val frames = mutableListOf(
            TFrame(
                n, rows(null, null), "Check that every bracket closes in the right order.",
                "The most recent unclosed bracket must be the first to close, which is exactly a stack.",
                formula = listOf("open → push · close → top must be its partner"), chips = listOf(StoryChip("depth", "0")),
            ),
        )
        for ((i, c) in s.withIndex()) {
            val open = pair[c]
            if (open != null) {
                val top = stack.lastOrNull()
                if (top == null || top.first != open) {
                    val got = top?.first
                    frames += TFrame(
                        n, rows(i, stack.lastIndex, bad = true), "$c at index $i meets ${got ?: "an empty stack"}: the string is {w:invalid}.",
                        "Counting brackets alone would pass this string; only the stack sees the wrong order.",
                        lit = mapOf(i to TTone.Mismatch), formula = listOf("$c needs $open, top is {w:${got ?: "empty"}} → invalid"),
                        chips = listOf(StoryChip("valid", "no", StoryTone.Warn)),
                    )
                    return frames
                }
                stack.removeAt(stack.lastIndex)
                frames += TFrame(
                    n, rows(i, null), "$c closes the $open from index ${top.second}.", "A match pops, uncovering the bracket that must close next.",
                    lit = mapOf(i to TTone.Active), formula = listOf("$c matches top {$open} at i${top.second} → pop"), chips = listOf(StoryChip("depth", "${stack.size}")),
                )
            } else {
                stack += c to i
                frames += TFrame(
                    n, rows(i, stack.lastIndex), "$c opens a new level and waits on the stack.", "Whatever closes next must match this one first.",
                    lit = mapOf(i to TTone.Active), formula = listOf("$c opens → push · depth {${stack.size}}"), chips = listOf(StoryChip("depth", "${stack.size}")),
                )
            }
        }
        return frames
    }
    return listOf(
        TTab(
            "Decode", decode(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current / popping"), Triple(SimColors.Blue, SwatchStyle.Fill, "Suspended"), Triple(SimColors.Answer, SwatchStyle.Fill, "Built text")),
        ),
        TTab(
            "Valid brackets", brackets(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current"), Triple(SimColors.Blue, SwatchStyle.Fill, "Waiting to close"), Triple(SimColors.Red, SwatchStyle.Fill, "Mismatch")),
        ),
    )
}

private fun monoDequeTabs(): List<TTab> {
    val a = listOf(1, 3, -1, -3, 5, 3, 6, 7)
    val k = 3
    val n = a.size
    fun run(isMax: Boolean): List<TFrame> {
        val dq = mutableListOf<Int>()
        val frames = mutableListOf<TFrame>()
        val word = if (isMax) "max" else "min"
        fun m(v: Int) = if (v < 0) "−${-v}" else "$v"
        for (i in 0 until n) {
            var expired: Int? = null
            dq.firstOrNull()?.let { f -> if (f <= i - k) { expired = f; dq.removeAt(0) } }
            val popped = mutableListOf<Int>()
            while (dq.isNotEmpty() && (if (isMax) a[dq.last()] <= a[i] else a[dq.last()] >= a[i])) popped += dq.removeAt(dq.lastIndex)
            dq += i
            val lo = maxOf(0, i - k + 1)
            val full = i >= k - 1
            val lit = (lo until i).associateWith { TTone.Blue } + (i to TTone.Active)
            val row = TRow(
                a.mapIndexed { j, v ->
                    TCell(
                        "$v",
                        when {
                            j == i -> TTone.Active
                            j == expired -> TTone.Mismatch
                            j in lo until i -> TTone.Outline
                            j < lo -> TTone.Pending
                            else -> TTone.Idle
                        },
                    )
                },
            )
            val deque = TRow(
                (0..k).map { s ->
                    if (s < dq.size) TCell("${a[dq[s]]} · i${dq[s]}", if (s == 0 && full) TTone.Answer else if (dq[s] == i) TTone.Active else TTone.Outline) else TCell("", TTone.Ghost)
                },
                title = "DEQUE · FRONT TO BACK", spread = true,
            )
            val parts = mutableListOf<String>()
            expired?.let { parts += "front i$it ≤ $i − $k → {w:expire}" }
            val cmp = if (isMax) ">" else "<"
            parts += if (popped.isEmpty()) "{${m(a[i])}} → push back" else "{${m(a[i])}} $cmp ${popped.joinToString(", ") { m(a[it]) }} → pop back ×${popped.size}"
            val formula = if (parts.size == 2) listOf(parts[0] + " ·", parts[1]) else parts
            val front = a[dq[0]]
            val e = expired
            val headline = when {
                dq.size == 1 && (e != null || popped.isNotEmpty()) && i > 0 ->
                    "${m(a[i])} clears the whole deque" + (e?.let { ": ${m(a[it])} left the window" } ?: "") +
                        (if (popped.isEmpty()) "." else (if (e == null) ": " else ", and ") + "${popped.reversed().joinToString(", ") { m(a[it]) }} can never be a $word again.")
                popped.isNotEmpty() -> "${m(a[i])} outvotes ${popped.joinToString(" and ") { m(a[it]) }}, which can never be a $word while ${m(a[i])} is in the window."
                e != null -> "${m(a[e])} slides out of the window; ${m(a[i])} waits behind ${m(front)}."
                i == 0 -> "${m(a[i])} is the first value, so it goes straight in."
                else -> "${m(a[i])} is ${if (isMax) "smaller" else "bigger"} than the back, so it waits: it could be the $word later."
            }
            frames += TFrame(
                n, listOf(row, deque), headline,
                "The front drops what's out of range, the back drops what's outvoted. Every index enters and leaves once, so O(n).",
                lit = lit, formula = formula,
                chips = listOf(StoryChip("window", "[$lo, $i]"), StoryChip(word, if (full) m(front) else "–", if (full) StoryTone.Answer else StoryTone.Idle)),
            )
        }
        return frames
    }
    val legend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Incoming"), Triple(SimColors.Blue, SwatchStyle.Fill, "In window"), Triple(SimColors.Red, SwatchStyle.Fill, "Expired"))
    return listOf(
        TTab("Window max", run(true), legend + Triple(SimColors.Answer, SwatchStyle.Fill, "Window max")),
        TTab("Window min", run(false), legend + Triple(SimColors.Answer, SwatchStyle.Fill, "Window min")),
    )
}

private fun twoHeapsTabs(): List<TTab> {
    val stream = listOf(5, 15, 1, 3, 8, 7, 9, 10)
    fun streamRow(i: Int?, gone: Int = -1) = TRow(
        stream.mapIndexed { j, v -> TCell("$v", if (j == i) TTone.Active else if (j == gone) TTone.Mismatch else if (i != null && j < i) TTone.Pending else TTone.Idle) },
        title = "STREAM", spread = true,
    )
    fun heapRow(lo: List<Int>, hi: List<Int>, moving: Int?): TRow {
        val l = lo.sortedDescending()
        val h = hi.sorted()
        var movedOnce = false
        fun tone(v: Int, base: TTone): TTone {
            if (v == moving && !movedOnce) { movedOnce = true; return TTone.Active }
            return base
        }
        val cells = l.map { TCell("$it", tone(it, TTone.Outline)) } + h.map { TCell("$it", tone(it, TTone.Green)) }
        return TRow(cells, title = "LO · MAX-HEAP", spread = true, split = l.size, splitTitle = "HI · MIN-HEAP")
    }
    fun median(lo: List<Int>, hi: List<Int>): String {
        if (lo.size > hi.size) return "lo.top = ${lo.max()}"
        val s = lo.max() + hi.min()
        return "(${lo.max()} + ${hi.min()}) / 2 = ${if (s % 2 == 0) "${s / 2}" else "%.1f".format(s / 2.0)}"
    }
    fun running(): List<TFrame> {
        val lo = mutableListOf<Int>()
        val hi = mutableListOf<Int>()
        val frames = mutableListOf(
            TFrame(
                0, listOf(streamRow(null)), "Keep a running median as values stream in.",
                "A max-heap of the smaller half and a min-heap of the larger half put the middle one peek away.",
                formula = listOf("lo holds the smaller half, hi the larger · |lo| = |hi| or |hi| + 1"), chips = listOf(StoryChip("median", "–")),
            ),
        )
        stream.forEachIndexed { i, x ->
            lo += x
            val up = lo.max()
            lo.remove(up)
            hi += up
            var formula = "{$x} → lo → top $up → hi"
            var moving = up
            var headline = if (up == x) "$x lands in hi" else "$x goes to lo, which hands its top $up to hi"
            if (hi.size > lo.size) {
                val down = hi.min()
                hi.remove(down)
                lo += down
                formula += " · hi ${hi.size + 1} > lo ${lo.size - 1} → {$down} back to lo"
                moving = down
                headline += if (down == x) ", and comes straight back to lo to keep the sizes even." else ", which pushes $down back to lo to keep the sizes even."
            } else {
                formula += " · sizes ${lo.size} | ${hi.size}"
                headline += "; the sizes are even."
            }
            frames += TFrame(
                0, listOf(streamRow(i), heapRow(lo, hi, moving)), headline,
                if (lo.size > hi.size) "With an odd count, lo holds the extra value, so the median is always lo's root. Each insert is O(log n)."
                else "With an even count, the median is the average of the two roots. Each insert is O(log n).",
                formula = listOf(formula), chips = listOf(StoryChip("median", median(lo, hi)), StoryChip("sizes", "${lo.size} | ${hi.size}")),
            )
        }
        return frames
    }
    fun sliding(): List<TFrame> {
        val k = 3
        val lo = mutableListOf<Int>()
        val hi = mutableListOf<Int>()
        val frames = mutableListOf(
            TFrame(
                0, listOf(streamRow(null)), "The median of every window of $k.",
                "Same two heaps, but a value also leaves each step. Real code deletes lazily; here it is removed at once.",
                formula = listOf("window k = $k · add the new value, drop the one leaving, rebalance"), chips = listOf(StoryChip("median", "–")),
            ),
        )
        stream.forEachIndexed { i, x ->
            val parts = mutableListOf<String>()
            if (lo.isEmpty() || x <= lo.max()) { lo += x; parts += "{$x} → lo" } else { hi += x; parts += "{$x} → hi" }
            var gone = -1
            if (i >= k) {
                gone = i - k
                val y = stream[gone]
                if (y in lo) { lo.remove(y); parts += "drop {w:$y} from lo" } else { hi.remove(y); parts += "drop {w:$y} from hi" }
            }
            var moving: Int? = x
            if (lo.size > hi.size + 1) { val v = lo.max(); lo.remove(v); hi += v; moving = v; parts += "$v → hi" }
            else if (hi.size > lo.size) { val v = hi.min(); hi.remove(v); lo += v; moving = v; parts += "$v → lo" }
            val full = i >= k - 1
            frames += TFrame(
                0, listOf(streamRow(i, gone), heapRow(lo, hi, moving)),
                if (gone >= 0) "$x enters and ${stream[gone]} leaves; the median of the window is ${lo.max()}."
                else if (full) "The first window is full: its median is ${lo.max()}." else "$x joins; the window isn't full yet.",
                "Each add, remove and rebalance is O(log k), so the whole pass is O(n log k).",
                formula = listOf(parts.joinToString(" · ")),
                chips = listOf(StoryChip("window", "[${maxOf(0, i - k + 1)}, $i]"), StoryChip("median", if (full) "${lo.max()}" else "–", if (full) StoryTone.Answer else StoryTone.Idle)),
            )
        }
        return frames
    }
    val legend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Moving"), Triple(SimColors.Blue, SwatchStyle.Fill, "lo · smaller half"), Triple(SimColors.Green, SwatchStyle.Fill, "hi · larger half"))
    return listOf(TTab("Median", running(), legend), TTab("Sliding median", sliding(), legend))
}

// ── Tree and search pattern storyboards ──────────────────────────────────────
// Top-down memoization, meet in the middle, subsets, backtracking, BST in-order, the exchange argument,
// binary lifting and tree DFS path sums.

/** Braces in a set like {a,b} would read as a highlight mark, so escape them for headlines. */
private fun esc(s: String) = s.replace("{", "\\{")

private val Supers = listOf("⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹")
private fun sup(n: Int) = "$n".map { Supers[it - '0'] }.joinToString("")

private val CallLegend = listOf(
    Triple(SimColors.Active, SwatchStyle.Fill, "Active"),
    Triple(SimColors.Active, SwatchStyle.Ring, "On the stack"),
    Triple(SimColors.Green, SwatchStyle.Fill, "Returned"),
)
private val NotCalledGrey = Color.Gray.copy(alpha = 0.25f)

private fun textParam(topicId: String): TParam? = when (topicId) {
    "memo_recursion_pattern" -> TParam({ "n" }, (3..7).toList(), 6, ::memoTabs)
    "meet_in_middle_pattern" -> TParam({ "Items · target 15" }, listOf(4, 6), 6, ::mitmTabs)
    "subsets_pattern" -> TParam({ "Elements" }, listOf(2, 3), 3, ::subsetsTabs)
    "backtracking_pattern" -> TParam({ "Candidates 3, 4, 5 · target" }, listOf(7, 8, 9), 8, ::backtrackTabs)
    else -> null
}

private fun fibValue(k: Int): Int {
    var a = 0
    var b = 1
    repeat(k) { val t = a + b; a = b; b = t }
    return a
}

private fun memoTabs(n: Int): List<TTab> {
    val nodes = mutableListOf<TNode>()
    val arg = mutableListOf<Int>()
    // push(id), base(id), hit(id), ret(id, a, b)
    val events = mutableListOf<IntArray>()
    val seen = BooleanArray(n + 1)
    fun call(k: Int, parent: Int?): Int {
        val id = nodes.size
        nodes += TNode("f($k)", parent)
        arg += k
        if (k < 2) { events += intArrayOf(1, id); seen[k] = true; return k }
        if (seen[k]) { events += intArrayOf(2, id); return fibValue(k) }
        events += intArrayOf(0, id)
        val a = call(k - 1, id)
        val b = call(k - 2, id)
        seen[k] = true
        events += intArrayOf(3, id, a, b)
        return a + b
    }
    val answer = call(n, null)
    val tones = mutableMapOf<Int, TTone>()
    val labels = mutableMapOf<Int, String>()
    val stack = mutableListOf<Int>()
    val memo = MutableList<Int?>(n + 1) { null }
    val frames = mutableListOf<TFrame>()
    fun frame(cur: Int?, read: Int?, headline: String, body: String) {
        val edges = (stack + listOfNotNull(cur)).filter { nodes[it].parent != null }.associateWith { TTone.Active }
        val memoRow = TRow((0..n).map { k -> memo[k]?.let { TCell("$it", if (k == read) TTone.Active else TTone.Outline) } ?: TCell("", TTone.Ghost) })
        frames += TFrame(
            n + 1, listOf(memoRow), headline, body, lit = read?.let { mapOf(it to TTone.Active) } ?: emptyMap(),
            heading = "MEMO", tree = TTree(nodes.toList(), tones.toMap(), labels.toMap(), edges = edges),
        )
    }
    for (ev in events) {
        val id = ev[1]
        val k = arg[id]
        when (ev[0]) {
            0 -> {
                tones[id] = TTone.Active
                frame(id, null, "f($k) isn't in the memo yet, so it calls f(${k - 1}) first.", "The stack grows one call per level until a base case answers.")
                tones[id] = TTone.Stack
                stack += id
            }
            1 -> {
                memo[k] = k
                tones[id] = TTone.Active
                frame(id, k, "f($k) is a base case and returns $k; the memo records it.", "Base cases seed the table every other entry is built from.")
                tones[id] = TTone.Mirror
            }
            2 -> {
                val p = nodes[id].parent?.let { arg[it] } ?: n
                labels[id] = "f($k) memo"
                tones[id] = TTone.Active
                frame(id, k, "f($p) asks for f($k) again. The memo already has ${memo[k]}, so no subtree is built.", "Each f(n) is computed once and then read back, so 2ⁿ calls become n + 1.")
                tones[id] = TTone.Memo
            }
            else -> {
                val a = ev[2]
                val b = ev[3]
                memo[k] = a + b
                stack.removeAt(stack.lastIndex)
                labels[id] = "f($k)=${a + b}"
                tones[id] = TTone.Active
                frame(id, k, "f($k) = $a + $b = ${a + b}, written to memo[$k].", "Every later call to f($k) is now a single lookup.")
                tones[id] = if (id == 0) TTone.Answer else TTone.Mirror
            }
        }
    }
    val plain = 2 * fibValue(n + 1) - 1
    frame(null, n, "f($n) = {v:$answer} with ${nodes.size} calls instead of $plain.", "Top-down memoization is the recursion plus a table: O(n) time, O(n) space.")
    return listOf(TTab("Memo", frames, CallLegend + Triple(SimColors.Answer, SwatchStyle.Fill, "Memo hit")))
}

private fun mitmTabs(n: Int): List<TTab> {
    val items = listOf(3, 7, 9, 4, 6, 11).take(n)
    val target = 15
    val h = n / 2
    val left = items.take(h)
    val right = items.drop(h)
    fun sums(a: List<Int>) = (0 until (1 shl a.size)).map { m ->
        val pick = a.indices.filter { (m shr it) and 1 == 1 }
        pick.sumOf { a[it] } to pick
    }.sortedWith(compareBy({ it.first }, { it.second.size }))
    val ls = sums(left)
    val rs = sums(right)
    fun itemsRow(l: List<Int>, r: List<Int>) = TRow(
        left.indices.map { TCell("${left[it]}", if (it in l) TTone.Active else TTone.Outline) } +
            right.indices.map { TCell("${right[it]}", if (it in r) TTone.Answer else TTone.Idle) },
        title = "ITEMS", spread = true, split = h, divider = true,
    )
    fun leftRow(i: Int?) = TRow(
        ls.mapIndexed { j, v -> TCell("${v.first}", if (j == i) TTone.Active else if (i != null && j < i) TTone.Pending else TTone.Idle) },
        title = "LEFT SUBSET SUMS · 2${sup(h)}", spread = true,
    )
    fun rightRow(lo: Int?, mid: Int?, hi: Int?, found: Boolean): TRow {
        val tags = mutableMapOf<Int, Pair<String, TTone>>()
        lo?.let { tags[it] = "lo" to TTone.Blue }
        hi?.let { tags[it] = (if (tags[it] == null) "hi" else "lo·hi") to TTone.Blue }
        mid?.let { tags[it] = (tags[it]?.let { t -> t.first + "·mid" } ?: "mid") to TTone.Blue }
        return TRow(
            rs.mapIndexed { j, v ->
                TCell(
                    "${v.first}",
                    when {
                        j == mid -> if (found) TTone.Answer else TTone.Mismatch
                        lo != null && hi != null && j in lo..hi -> TTone.Outline
                        lo == null && mid == null -> TTone.Idle
                        else -> TTone.Pending
                    },
                )
            },
            title = "RIGHT SUBSET SUMS · SORTED", tags = tags, spread = true,
        )
    }
    val pow = "2 × 2${sup(h)} = ${2 shl (h - 1)} sums instead of 2${sup(n)} = ${1 shl n}"
    val frames = mutableListOf(
        TFrame(
            0, listOf(itemsRow(emptyList(), emptyList())), "Pick items that sum to $target. Trying all 2${sup(n)} subsets is what this avoids.",
            "Split the items in half and enumerate each half on its own.", formula = listOf("2${sup(n)} = ${1 shl n} subsets → split into two halves of $h"),
        ),
        TFrame(
            0, listOf(itemsRow(emptyList(), emptyList()), leftRow(null)), "The left half's ${ls.size} subsets give these sums.",
            "Enumerating one half is 2^(n/2) work, not 2^n.", formula = listOf("left half ${left.joinToString(", ")} → 2${sup(h)} sums"),
        ),
        TFrame(
            0, listOf(itemsRow(emptyList(), emptyList()), leftRow(null), rightRow(null, null, null, false)),
            "The right half's sums are sorted, so each partner is a binary search away.", "Sorting costs O(2^(n/2) · n/2) once.",
            formula = listOf("right half ${right.joinToString(", ")} → sort the sums"),
        ),
    )
    var found = 0
    ls.forEachIndexed { i, l ->
        val need = target - l.first
        if (need < 0) {
            frames += TFrame(
                0, listOf(itemsRow(l.second, emptyList()), leftRow(i), rightRow(null, null, null, false)),
                "Left sum ${l.first} already overshoots $target, so there is nothing to search for.", "$pow.",
                formula = listOf("$target − {${l.first}} < 0 → skip"), chips = listOf(StoryChip("found", "$found")),
            )
            return@forEachIndexed
        }
        var lo = 0
        var hi = rs.lastIndex
        var mid = 0
        var hit = false
        while (lo <= hi) {
            mid = (lo + hi) / 2
            if (rs[mid].first == need) { hit = true; break }
            if (rs[mid].first < need) lo = mid + 1 else hi = mid - 1
        }
        if (hit) found++
        frames += TFrame(
            0,
            listOf(itemsRow(l.second, if (hit) rs[mid].second else emptyList()), leftRow(i), rightRow(if (hit) lo else null, mid, if (hit) hi else null, hit)),
            if (hit) "Left sum ${l.first} needs $need from the right half, and binary search finds it: ${l.first} + $need = $target."
            else "Left sum ${l.first} needs $need, which no right subset makes.",
            "$pow, and sorting one side turns pairing into a lookup.",
            formula = listOf(
                if (hit) "$target − {${l.first}} = $need → binary search right → {v:$need} found"
                else "$target − {${l.first}} = $need → binary search right → {w:$need missing}",
            ),
            chips = listOf(StoryChip("found", "$found", if (hit) StoryTone.Answer else StoryTone.Idle)),
        )
    }
    frames += TFrame(
        0, listOf(itemsRow(emptyList(), emptyList()), leftRow(ls.size), rightRow(null, null, null, false)),
        if (found == 0) "No subset reaches $target." else "{v:$found} subset${if (found == 1) "" else "s"} reach $target.",
        "O(2^(n/2) · n) instead of O(2^n): n = 40 goes from a trillion subsets to about a million.",
        formula = listOf("matches = {v:$found}"), chips = listOf(StoryChip("found", "$found", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Split", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current left sum"), Triple(SimColors.Blue, SwatchStyle.Fill, "Search range"), Triple(SimColors.Answer, SwatchStyle.Fill, "Match")),
        ),
    )
}

private fun subsetsTabs(n: Int): List<TTab> {
    val letters = "abcd".take(n).map { "$it" }
    val nodes = mutableListOf<TNode>()
    val picks = mutableListOf<List<Int>>()
    fun build(pick: List<Int>, parent: Int?) {
        val id = nodes.size
        nodes += TNode("{" + pick.joinToString(",") { letters[it] } + "}", parent)
        picks += pick
        for (j in (pick.lastOrNull() ?: -1) + 1 until n) build(pick + j, id)
    }
    build(emptyList(), null)
    fun kids(id: Int) = nodes.indices.filter { nodes[it].parent == id }
    val tones = mutableMapOf<Int, TTone>()
    val stack = mutableListOf<Int>()
    val out = mutableListOf<Int>()
    val frames = mutableListOf<TFrame>()
    val total = 1 shl n
    fun frame(cur: Int?, headline: String, body: String) {
        val edges = (stack + listOfNotNull(cur)).filter { nodes[it].parent != null }.associateWith { TTone.Active }
        val cells = out.map { TCell(nodes[it].label, if (it == cur) TTone.Active else TTone.Answer) }
        val rows = if (cells.isEmpty()) listOf(TRow(listOf(TCell("", TTone.Ghost)), title = "OUTPUT · 0 OF $total", spread = true))
        else cells.chunked(8).mapIndexed { k, chunk -> TRow(chunk, title = if (k == 0) "OUTPUT · ${out.size} OF $total" else null, spread = true, compact = cells.size > 5) }
        frames += TFrame(0, rows, headline, body, tree = TTree(nodes.toList(), tones.toMap(), edges = edges))
    }
    fun visit(id: Int) {
        out += id
        tones[id] = TTone.Active
        val cs = kids(id)
        val label = esc(nodes[id].label)
        val later = cs.map { letters[picks[it].last()] }
        when {
            id == 0 -> frame(id, "$label is the first subset: record it, then branch on ${later.joinToString(", ")}.", "Every call records its subset before it branches.")
            cs.isEmpty() -> frame(
                id, "$label has no children: after ${letters[picks[id].last()]}, there is nothing left to add.",
                "Calls only add elements after the last pick, so {${letters.last()},${letters[0]}} never appears.",
            )
            else -> frame(id, "Record $label, then try adding each later element: ${later.joinToString(", ")}.", "Each branch is one more element, never an earlier one.")
        }
        tones[id] = if (cs.isEmpty()) TTone.Mirror else TTone.Stack
        if (cs.isNotEmpty()) stack += id
        cs.forEachIndexed { k, c ->
            if (k > 0 && kids(cs[k - 1]).isNotEmpty()) {
                frame(null, "${esc(nodes[cs[k - 1]].label)} has tried every later element, so it returns to $label.", "Returning undoes the last pick; the parent then tries its next element.")
            }
            visit(c)
        }
        if (cs.isNotEmpty()) { stack.removeAt(stack.lastIndex); tones[id] = TTone.Mirror }
    }
    visit(0)
    frame(null, "All {v:$total} subsets are out, each exactly once.", "2ⁿ calls, one per subset, each copying up to n elements: O(n · 2ⁿ).")
    return listOf(TTab("Subsets", frames, CallLegend + Triple(NotCalledGrey, SwatchStyle.Fill, "Not called") + Triple(SimColors.Answer, SwatchStyle.Fill, "Recorded")))
}

private fun backtrackTabs(target: Int): List<TTab> {
    val cands = listOf(3, 4, 5)
    val nodes = mutableListOf<TNode>()
    val paths = mutableListOf<List<Int>>()
    val needs = mutableListOf<Int>()
    val events = mutableListOf<Pair<Int, Int>>() // 0 visit, 1 prune, 2 return
    fun fmt(p: List<Int>) = if (p.isEmpty()) "[ ]" else "[" + p.joinToString(",") + "]"
    fun minus(v: Int) = if (v < 0) "−${-v}" else "$v"
    fun dfs(path: List<Int>, need: Int, start: Int, parent: Int?) {
        val id = nodes.size
        nodes += TNode(fmt(path), parent, "need $need")
        paths += path
        needs += need
        events += 0 to id
        if (need == 0) return
        for (j in start until cands.size) {
            val v = cands[j]
            if (v > need) {
                val p = nodes.size
                nodes += TNode("+$v → ${minus(need - v)}", id)
                paths += path + v
                needs += need - v
                events += 1 to p
                break
            }
            dfs(path + v, need - v, j, id)
        }
        events += 2 to id
    }
    dfs(emptyList(), target, 0, null)
    val tones = mutableMapOf<Int, TTone>()
    val stack = mutableListOf<Int>()
    val visited = mutableSetOf<Int>()
    val frames = mutableListOf<TFrame>()
    val answers = mutableListOf<String>()
    fun frame(cur: Int?, formula: List<String>, headline: String, body: String) {
        val visible = nodes.indices.filter { i -> nodes[i].parent?.let { it in visited } ?: true }
        val map = visible.withIndex().associate { (k, v) -> v to k }
        val edges = (stack + listOfNotNull(cur)).filter { nodes[it].parent != null }.associate { map.getValue(it) to TTone.Active }
        val tree = TTree(
            visible.map { TNode(nodes[it].label, nodes[it].parent?.let { p -> map.getValue(p) }, nodes[it].sub) },
            tones.mapNotNull { (k, v) -> map[k]?.let { it to v } }.toMap(), edges = edges,
        )
        frames += TFrame(
            0, emptyList(), headline, body, formula = formula,
            chips = listOf(StoryChip("found", if (answers.isEmpty()) "–" else answers.joinToString(" "), if (answers.isEmpty()) StoryTone.Idle else StoryTone.Answer)),
            tree = tree,
        )
    }
    fun stackText(top: Int, mark: String) = "stack: " + (stack.map { fmt(paths[it]) } + "{$mark${fmt(paths[top])}}").joinToString(" → ")
    val pruneBody = "Candidates are sorted, so once one overshoots 0 the rest of that branch is skipped."
    for ((kind, id) in events) {
        when (kind) {
            0 -> {
                visited += id
                val need = needs[id]
                if (need == 0) {
                    answers += fmt(paths[id])
                    tones[id] = TTone.Answer
                    frame(id, listOf(stackText(id, "v:"), "· need 0 → record, return"), "${fmt(paths[id])} brings need to 0, so it is recorded and the call returns.", pruneBody)
                } else {
                    tones[id] = TTone.Active
                    val from = paths[id].lastOrNull()?.let { cands.indexOf(it) } ?: 0
                    frame(
                        id, listOf(stackText(id, ""), "· need $need → try ${cands.drop(from).joinToString(", ")}"),
                        if (id == 0) "Start with an empty combination that still needs $target." else "Choose ${paths[id].last()}: ${fmt(paths[id])} still needs $need.",
                        "Each call only reuses candidates from its own on, so [4,3] never repeats [3,4].",
                    )
                    tones[id] = TTone.Stack
                    stack += id
                }
            }
            1 -> {
                visited += id
                tones[id] = TTone.Mismatch
                val parent = nodes[id].parent!!
                frame(
                    null, listOf("{w:${nodes[id].label}} → below 0 → prune" + if (paths[id].last() < cands.last()) ", skip larger" else ""),
                    "Adding ${paths[id].last()} to ${fmt(paths[parent])} overshoots, so this branch and every larger candidate are cut.", pruneBody,
                )
            }
            else -> {
                stack.removeAt(stack.lastIndex)
                tones[id] = TTone.Mirror
                frame(
                    null, listOf("${fmt(paths[id])} done → pop"),
                    if (id == 0) "The root has tried every candidate: the search is over." else "${fmt(paths[id])} has nothing left to try, so it returns.",
                    "Undoing the choice on return is what makes this backtracking: one path, rewound and reused.",
                )
            }
        }
    }
    frame(
        null, listOf("combinations = {v:${answers.size}}"),
        "{v:${answers.size}} combination${if (answers.size == 1) "" else "s"} sum to $target: ${answers.joinToString(" and ")}.",
        "Pruning on sorted candidates keeps the tree far smaller than every sequence of picks.",
    )
    return listOf(
        TTab(
            "Combination sum", frames,
            CallLegend + Triple(SimColors.Answer, SwatchStyle.Fill, "Answer") + Triple(SimColors.Red, SwatchStyle.Fill, "Pruned") + Triple(NotCalledGrey, SwatchStyle.Fill, "Not called"),
        ),
    )
}

private fun bstInorderTabs(): List<TTab> {
    val keys = listOf(8, 4, 2, 1, 3, 6, 12, 10, 14)
    val parents = listOf(null, 0, 1, 2, 2, 1, 0, 6, 6)
    val left = mapOf(0 to 1, 1 to 2, 2 to 3, 6 to 7)
    val right = mapOf(0 to 6, 1 to 5, 2 to 4, 6 to 8)
    val nodes = keys.mapIndexed { i, k -> TNode("$k", parents[i]) }
    val k = 4
    val tones = mutableMapOf<Int, TTone>()
    val stack = mutableListOf<Int>()
    val emitted = mutableListOf<Int>()
    val frames = mutableListOf<TFrame>()
    fun frame(cur: Int?, formula: List<String>, headline: String, body: String, answer: Boolean = false) {
        val edges = mutableMapOf<Int, TTone>()
        stack.filter { id -> parents[id]?.let { it in stack } ?: false }.forEach { edges[it] = TTone.Active }
        if (cur != null && parents[cur]?.let { it in stack } == true) edges[cur] = TTone.Active
        val cells = keys.indices.map { j ->
            if (j < emitted.size) TCell("${keys[emitted[j]]}", if (answer && j == emitted.lastIndex) TTone.Answer else TTone.Green) else TCell("", TTone.Ghost)
        }
        frames += TFrame(
            0, listOf(TRow(cells, title = "EMITTED · K = $k", spread = true, compact = true)), headline, body, formula = formula,
            chips = listOf(StoryChip("stack", if (stack.isEmpty()) "–" else stack.joinToString(" ") { "${keys[it]}" })),
            tree = TTree(nodes, tones.toMap(), edges = edges),
        )
    }
    fun ord(i: Int) = when (i) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${i}th"
    }
    frame(
        null, listOf("push left spine · pop = next key in order"), "Find the ${k}th smallest key without visiting the whole tree.",
        "In-order is left, node, right. An explicit stack lets the walk stop the moment it has k keys.",
    )
    var cur: Int? = 0
    val words = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven")
    outer@ while (cur != null || stack.isNotEmpty()) {
        while (cur != null) {
            val c: Int = cur
            tones[c] = TTone.Active
            frame(
                c, listOf("push {${keys[c]}} · go left"),
                if (left[c] != null) "Push ${keys[c]} and keep going left: smaller keys are further down." else "Push ${keys[c]}; it has no left child.",
                "The stack holds every ancestor still waiting for its turn.",
            )
            tones[c] = TTone.Stack
            stack += c
            cur = left[c]
        }
        val top = stack.removeAt(stack.lastIndex)
        emitted += top
        if (emitted.size == k) {
            tones[top] = TTone.Answer
            val never = keys.size - emitted.size - stack.size
            frame(
                top, listOf("${ord(k)} key emitted → {v:${keys[top]}}", "· stack still holds ${stack.joinToString(", ") { "${keys[it]}" }} → stop"),
                "Left, node, right emits keys in sorted order, so the ${ord(k)} emitted key is the answer.",
                "The walk stops here. ${if (never == 1) "One node is" else "${words[never]} nodes are"} never visited.", answer = true,
            )
            break@outer
        }
        tones[top] = TTone.Green
        frame(
            top, listOf("pop → emit {${keys[top]}} · ${emitted.size} of $k"),
            "${keys[top]} has no unvisited left subtree, so pop it: the ${ord(emitted.size)} key in order.",
            right[top]?.let { "Next the walk turns to its right subtree, ${keys[it]}." } ?: "No right child, so the next pop is its waiting ancestor.",
        )
        cur = right[top]
    }
    frame(
        null, listOf("O(h + k) with the stack · recursion can't easily stop"), "k = $k needed only ${emitted.size + stack.size} of ${keys.size} nodes.",
        "A recursive in-order walk would unwind through every frame; the explicit stack just stops.",
    )
    return listOf(
        TTab(
            "Kth smallest", frames,
            listOf(
                Triple(SimColors.Green, SwatchStyle.Fill, "Emitted"), Triple(SimColors.Active, SwatchStyle.Ring, "On the stack"),
                Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"), Triple(NotCalledGrey, SwatchStyle.Fill, "Never visited"),
            ),
        ),
    )
}

private fun exchangeTabs(): List<TTab> {
    val jobs = mutableListOf(4, 1, 7, 2)
    fun wait(a: List<Int>): Int { var t = 0; var s = 0; for (d in a) { t += d; s += t }; return s }
    fun finishes(a: List<Int>): String { var t = 0; return a.joinToString(" + ") { t += it; "$t" } }
    fun order(a: List<Int>) = a.joinToString(" ")
    val frames = mutableListOf(
        TFrame(
            0, emptyList(), "Jobs run back to back, and each one waits for everything before it.", "The greedy guess: shortest first. The exchange argument proves it.",
            formula = listOf("total wait = ${finishes(jobs)} = {${wait(jobs)}}"), chips = listOf(StoryChip("total wait", "${wait(jobs)}")),
            gantt = listOf(TGanttRow("ORDER · ${order(jobs)}", "Σ ${wait(jobs)}", jobs.map { it to TTone.Idle })),
        ),
    )
    var swapped = true
    while (swapped) {
        swapped = false
        for (i in 0 until jobs.lastIndex) {
            if (jobs[i] <= jobs[i + 1]) continue
            val before = jobs.toList()
            val a = jobs[i]
            val b = jobs[i + 1]
            jobs[i] = b
            jobs[i + 1] = a
            swapped = true
            val sorted = jobs.sorted()
            frames += TFrame(
                0, emptyList(), "Putting the shorter job first saves $a − $b = ${a - b}, and no other job's wait changes.",
                "Any longer-before-shorter pair can be swapped for a gain, so shortest-first is optimal: ${order(sorted)} gives ${wait(sorted)}.",
                formula = listOf("swap ( {$a} , {$b} ): $a delays $b by $a, $b delays $a by $b → saves {m:${a - b}}"),
                chips = listOf(StoryChip("total wait", "${wait(before)} → ${wait(jobs)}")),
                gantt = listOf(
                    TGanttRow("BEFORE · ${order(before)}", "Σ ${wait(before)}", before.mapIndexed { j, d -> d to if (j == i || j == i + 1) TTone.Active else TTone.Idle }),
                    TGanttRow("AFTER SWAP · ${order(jobs)}", "Σ ${wait(jobs)}", jobs.mapIndexed { j, d -> d to if (j == i || j == i + 1) TTone.Green else TTone.Idle }),
                ),
            )
        }
    }
    frames += TFrame(
        0, emptyList(), "No pair can be improved, so shortest-first, total {v:${wait(jobs)}}, is optimal.",
        "The exchange argument: start from any order, swap toward the greedy one, never lose. Sorting is O(n log n).",
        formula = listOf("no longer-before-shorter pair left → {v:${wait(jobs)}}"), chips = listOf(StoryChip("total wait", "${wait(jobs)}", StoryTone.Answer)),
        gantt = listOf(TGanttRow("SHORTEST FIRST · ${order(jobs)}", "Σ ${wait(jobs)}", jobs.map { it to TTone.Green })),
    )
    return listOf(
        TTab(
            "Exchange", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Swapped pair"), Triple(SimColors.Green, SwatchStyle.Fill, "After swap"), Triple(Color.Gray.copy(alpha = 0.35f), SwatchStyle.Fill, "Untouched")),
        ),
    )
}

private fun liftingTabs(): List<TTab> {
    val names = listOf("A", "B", "C", "D", "E", "F", "G")
    val parent = listOf(null, 0, 1, 2, 3, 0, 5)
    val nodes = names.mapIndexed { i, n -> TNode(n, parent[i]) }
    val pos = listOf(0.3 to 0, 0.3 to 1, 0.3 to 2, 0.3 to 3, 0.3 to 4, 0.75 to 1, 0.75 to 2)
    val levels = 3
    val up = MutableList(levels) { MutableList<Int?>(names.size) { null } }
    up[0] = parent.toMutableList()
    for (k in 1 until levels) for (v in names.indices) up[k][v] = up[k - 1][v]?.let { up[k - 1][it] }
    fun table(filled: Int, marks: Map<String, TTone> = emptyMap()) = (0 until levels).map { k ->
        TRow(
            names.indices.map { v ->
                if (k >= filled) TCell("", TTone.Ghost)
                else up[k][v]?.let { TCell(names[it], marks["$k,$v"] ?: TTone.Idle) } ?: TCell("–", TTone.Pending)
            },
            "2${sup(k)}",
        )
    }
    val heading = "UP[K][V] · 2ᴷ-TH ANCESTOR"
    fun f(rows: List<TRow>, tree: TTree, formula: List<String>, headline: String, body: String) =
        TFrame(names.size, rows, headline, body, formula = formula, heading = heading, tree = tree, headers = names)
    fun tree(tones: Map<Int, TTone> = emptyMap(), arcs: List<TArc> = emptyList()) = TTree(nodes, tones, arcs = arcs, pos = pos, base = TTone.Idle)
    val a = 0
    val b = 1
    val c = 2
    val d = 3
    val e = 4
    val fN = 5
    val g = 6
    val frames = listOf(
        f(table(0), tree(), listOf("up[k][v] = the 2ᵏ-th ancestor of v"), "Store each node's 1st, 2nd and 4th ancestor.",
            "Any jump of k levels is then a handful of power-of-two jumps, one per set bit of k."),
        f(table(1), tree(), listOf("up[0][v] = parent(v)"), "Row 2⁰ is each node's parent.", "The root has none, so its entry is empty."),
        f(table(2, mapOf("0,$e" to TTone.Active, "0,$d" to TTone.Active, "1,$e" to TTone.Green)), tree(mapOf(e to TTone.Blue, d to TTone.Active, c to TTone.Green)),
            listOf("up[1][v] = up[0][up[0][v]]", "up[1][E] = up[0][D] = {m:C}"), "Row 2¹ doubles row 2⁰: two parent hops.", "Each row is built from the one above it in O(n)."),
        f(table(3, mapOf("1,$e" to TTone.Active, "1,$c" to TTone.Active, "2,$e" to TTone.Green)), tree(mapOf(e to TTone.Blue, c to TTone.Active, a to TTone.Green)),
            listOf("up[2][v] = up[1][up[1][v]]", "up[2][E] = up[1][C] = {m:A}"), "Row 2² jumps four levels by chaining two 2¹ jumps.", "log n rows in all, so the table is O(n log n)."),
        f(
            table(3, mapOf("1,$e" to TTone.Green, "0,$c" to TTone.Active)),
            tree(
                mapOf(e to TTone.Blue, d to TTone.Pending, c to TTone.Active, b to TTone.Answer, fN to TTone.Pending, g to TTone.Pending),
                listOf(TArc(e, c, "2¹", TTone.Green, true), TArc(c, b, "2⁰", TTone.Active, false)),
            ),
            listOf("k = 3 = 0b11 → up[1][E] = {m:C}", "→ up[0][C] = {v:B}"), "The 3rd ancestor of E takes two table reads, not three steps.",
            "Each set bit of k is one jump. Building the table costs O(n log n) once; every query after is O(log n).",
        ),
        f(
            table(3, mapOf("1,$e" to TTone.Green)), tree(mapOf(e to TTone.Blue, d to TTone.Pending, c to TTone.Active, g to TTone.Blue), listOf(TArc(e, c, "2¹", TTone.Green, true))),
            listOf("LCA(E, G): depth 4 vs 2 → lift E by 2 = 0b10", "up[1][E] = {m:C}"), "For LCA(E, G), first lift E to G's depth.",
            "Both nodes must sit on the same level before they can climb together.",
        ),
        f(
            table(3, mapOf("1,$c" to TTone.Pending, "1,$g" to TTone.Pending, "0,$c" to TTone.Active, "0,$g" to TTone.Active)),
            tree(
                mapOf(c to TTone.Blue, g to TTone.Blue, b to TTone.Active, fN to TTone.Active, d to TTone.Pending, e to TTone.Pending),
                listOf(TArc(c, b, "2⁰", TTone.Active, false), TArc(g, fN, "2⁰", TTone.Active, true)),
            ),
            listOf("up[1]: A = A → skip", "up[0]: B ≠ F → jump both"),
            "Try big jumps first: 2¹ lands both on A, too far, so skip it. 2⁰ lands on B and F, still different, so jump.",
            "Jump only while the ancestors differ; the nodes stop just below the LCA.",
        ),
        f(
            table(3, mapOf("0,$b" to TTone.Answer)), tree(mapOf(b to TTone.Active, fN to TTone.Active, a to TTone.Answer, c to TTone.Pending, d to TTone.Pending, e to TTone.Pending, g to TTone.Pending)),
            listOf("LCA(E, G) = up[0][B] = {v:A}"), "B and F are children of the same node, so LCA(E, G) = {v:A}.", "O(log n) jumps per query after the O(n log n) table.",
        ),
        f(table(3), tree(mapOf(a to TTone.Answer)), listOf("build O(n log n) · query O(log n)"), "Binary lifting answers k-th ancestor and LCA with power-of-two jumps.",
            "For a static tree with many queries, the table pays for itself quickly."),
    )
    return listOf(
        TTab(
            "Lifting", frames,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Start"), Triple(SimColors.Green, SwatchStyle.Fill, "Jump 2¹"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Jump 2⁰"), Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"),
            ),
        ),
    )
}

private fun pathSumTabs(): List<TTab> {
    val vals = listOf(5, 4, 11, 7, 2, 8, 13, 4, 5, 1)
    val parent = listOf(null, 0, 1, 2, 2, 0, 5, 5, 7, 7)
    val nodes = vals.mapIndexed { i, v -> TNode("$v", parent[i]) }
    val target = 22
    fun kids(i: Int) = nodes.indices.filter { parent[it] == i }
    fun minus(v: Int) = if (v < 0) "−${-v}" else "$v"
    fun run(all: Boolean): List<TFrame> {
        val tones = mutableMapOf<Int, TTone>()
        val badges = mutableMapOf<Int, Pair<String, TTone>>()
        val path = mutableListOf<Int>()
        val frames = mutableListOf<TFrame>()
        val found = mutableListOf<List<Int>>()
        fun pathText(p: List<Int>) = p.joinToString(" → ") { "${vals[it]}" }
        fun frame(formula: List<String>, headline: String, body: String) {
            val edges = path.filter { parent[it] != null }.associateWith { if (tones[it] == TTone.Answer) TTone.Answer else TTone.Blue }
            val rows = if (all) listOf(
                TRow(
                    if (found.isEmpty()) listOf(TCell("", TTone.Ghost)) else found.map { p -> TCell(p.joinToString("·") { "${vals[it]}" }, TTone.Answer) },
                    title = "FOUND PATHS", spread = true,
                ),
            ) else emptyList()
            frames += TFrame(0, rows, headline, body, formula = formula, tree = TTree(nodes, tones.toMap(), badges = badges.toMap(), edges = edges))
        }
        frame(
            listOf("target $target · pass what's left down to each child"),
            if (all) "Collect every root-to-leaf path that sums to $target." else "Is there a root-to-leaf path that sums to $target?",
            "Each node subtracts its value and hands the rest to its children.",
        )
        fun dfs(id: Int, left: Int): Boolean {
            val rem = left - vals[id]
            path += id
            val cs = kids(id)
            if (cs.isEmpty()) {
                if (rem == 0) {
                    tones[id] = TTone.Answer
                    badges[id] = "0" to TTone.Answer
                    found += path.toList()
                    frame(
                        listOf("target $target · at leaf {v:${vals[id]}} : $left − ${vals[id]} = {v:0}", "→ path ${pathText(path)}"),
                        "Leaf ${vals[id]} uses up exactly what was left, so ${pathText(path)} sums to $target.",
                        if (all) "Record a copy of the path and keep searching the other branches." else "Each node passes what's left down; the rest of the tree is never touched.",
                    )
                    if (!all) return true
                } else {
                    tones[id] = TTone.Mismatch
                    badges[id] = minus(rem) to TTone.Mismatch
                    frame(
                        listOf("leaf {w:${vals[id]}} : $left − ${vals[id]} = {w:${minus(rem)}} ≠ 0 → return"),
                        "Leaf ${vals[id]} leaves ${minus(rem)}, not 0, so this path fails.", "A leaf is the only place a path can succeed or fail.",
                    )
                }
                path.removeAt(path.lastIndex)
                return false
            }
            tones[id] = TTone.Blue
            badges[id] = "$rem" to TTone.Blue
            frame(
                listOf("$left − {${vals[id]}} = {p:$rem} left for the rest"),
                if (id == 0) "Start at ${vals[id]}: $rem is left for the rest of the path." else "Go down to ${vals[id]}: $rem is left.",
                "The remaining sum travels down with the call, so no path is summed twice.",
            )
            for (c in cs) if (dfs(c, rem)) return true
            path.removeAt(path.lastIndex)
            tones[id] = TTone.Idle
            badges.remove(id)
            parent[id]?.let { p ->
                frame(
                    listOf("${vals[id]} done → back to {${vals[p]}}"), "Both branches under ${vals[id]} are done, so return to ${vals[p]}.",
                    "Backtracking pops the node off the path before trying the next branch.",
                )
            }
            return false
        }
        val hit = dfs(0, target)
        if (all) {
            frame(
                listOf("paths = {v:${found.size}}"), "{v:${found.size}} paths sum to $target: ${found.joinToString(" and ") { pathText(it) }}.",
                "Every node is visited once, but copying each found path costs O(h), so O(n · h) in the worst case.",
            )
        } else {
            frame(listOf("hasPathSum = {v:$hit}"), "true travels back up the stack and the search stops at once.", "The right subtree is never touched. O(n) in the worst case, O(h) stack.")
        }
        return frames
    }
    val legend = listOf(
        Triple(SimColors.Blue, SwatchStyle.Fill, "Path"), Triple(SimColors.Answer, SwatchStyle.Fill, "Found"),
        Triple(SimColors.Red, SwatchStyle.Fill, "Dead end"), Triple(NotCalledGrey, SwatchStyle.Fill, "Not visited"),
    )
    return listOf(TTab("Has path", run(false), legend), TTab("All paths", run(true), legend))
}

// ── Graph and grid pattern storyboards ───────────────────────────────────────
// Multi-source BFS, game theory DP, islands, DAG DP, two-colouring, topological sort and weighted shortest paths.

private val GridDirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

private fun multiSourceTabs(): List<TTab> {
    val rows = 6
    val cols = 8
    val walls = setOf(3, cols + 3, 3 * cols + 5, 4 * cols + 5, 5 * cols + 5)
    val sources = listOf(Triple(2, 0, "S"), Triple(2, 7, "G"))
    val dist = MutableList(rows * cols) { -1 }
    val queue = mutableListOf<Int>()
    sources.forEach { (r, c, _) -> dist[r * cols + c] = 0; queue += r * cols + c }
    var head = 0
    while (head < queue.size) {
        val cur = queue[head++]
        for ((dr, dc) in GridDirs) {
            val r = cur / cols + dr
            val c = cur % cols + dc
            if (r !in 0 until rows || c !in 0 until cols) continue
            val k = r * cols + c
            if (k in walls || dist[k] >= 0) continue
            dist[k] = dist[cur] + 1
            queue += k
        }
    }
    val open = rows * cols - walls.size
    val maxD = dist.max()
    fun grid(minute: Int) = (0 until rows).map { r ->
        TRow(
            (0 until cols).map { c ->
                val k = r * cols + c
                val s = sources.firstOrNull { it.first == r && it.second == c }
                val d = dist[k]
                when {
                    k in walls -> TCell("", TTone.Pending)
                    s != null -> TCell(s.third, TTone.Answer)
                    d == minute -> TCell("$d", TTone.Active)
                    d < minute -> TCell("$d", TTone.Outline)
                    d == minute + 1 -> TCell("", TTone.Frontier)
                    else -> TCell("", TTone.Idle)
                }
            },
        )
    }
    fun reached(m: Int) = dist.count { it in 0..m }
    fun count(m: Int) = dist.count { it == m }
    val body = "Seeding the queue with both sources is the only change. The loop is plain BFS."
    val frames = mutableListOf(
        TFrame(
            cols, grid(0), "Both sources go into the queue before the loop starts.", body,
            formula = listOf("queue starts [ {v:S} , {v:G} ] at 0"), chips = listOf(StoryChip("minute", "0"), StoryChip("reached", "2 / $open")), noHeader = true,
        ),
    )
    for (m in 1..maxD) {
        frames += TFrame(
            cols, grid(m),
            when (m) {
                1 -> "Minute 1: every open neighbour of S or G joins the wave."
                maxD -> "Minute $m reaches the last open cells: every cell is filled."
                else -> "Both waves grow at once, and each cell keeps the minute the nearer source reached it."
            },
            if (m == maxD) "The answer is $maxD minutes, the largest distance from the nearest source." else body,
            formula = listOf("queue starts [ {v:S} , {v:G} ] at 0 · minute $m reaches {${count(m)}} cells"),
            chips = listOf(StoryChip("minute", "$m"), StoryChip("reached", "${reached(m)} / $open", if (reached(m) == open) StoryTone.Answer else StoryTone.Idle)),
            noHeader = true,
        )
    }
    frames += TFrame(
        cols, grid(maxD), "Every cell is reached after {v:$maxD} minutes.", "One BFS from all sources is O(rows × cols), not one BFS per source.",
        formula = listOf("max distance to nearest source = {v:$maxD}"), chips = listOf(StoryChip("minutes", "$maxD", StoryTone.Answer)), noHeader = true,
    )
    return listOf(
        TTab(
            "Waves", frames,
            listOf(
                Triple(SimColors.Answer, SwatchStyle.Fill, "Source"), Triple(SimColors.Blue, SwatchStyle.Fill, "Earlier minute"),
                Triple(SimColors.Active, SwatchStyle.Fill, "This minute"), Triple(SimColors.Blue, SwatchStyle.Ring, "Next wave"),
                Triple(Color.Gray.copy(alpha = 0.15f), SwatchStyle.Fill, "Wall"),
            ),
        ),
    )
}

private fun gameTheoryTabs(): List<TTab> {
    val piles = listOf(3, 9, 1, 2)
    val n = piles.size
    val dp = MutableList(n) { MutableList<Int?>(n) { null } }
    fun m(v: Int) = if (v < 0) "−${-v}" else "$v"
    fun raw(v: Int) = if (v < 0) "-${-v}" else "$v"
    fun table(reads: List<Pair<Int, Int>> = emptyList(), answer: Pair<Int, Int>? = null) = (0 until n).map { i ->
        TRow(
            (0 until n).map { j ->
                val v = dp[i][j]
                when {
                    v == null -> TCell("", TTone.Pending)
                    answer == (i to j) -> TCell(raw(v), TTone.Answer)
                    (i to j) in reads -> TCell(raw(v), TTone.Active)
                    else -> TCell(raw(v), if (v >= 0) TTone.Green else TTone.Mismatch)
                }
            },
            "i $i",
        )
    }
    fun pileRow(lo: Int?, hi: Int?, taken: Int?) = TRow(
        piles.mapIndexed { k, p -> TCell("$p", if (k == taken) TTone.Active else if (lo == null || k in lo..hi!!) TTone.Idle else TTone.Pending) },
        title = "PILES", spread = true,
    )
    val heading = "DP[I][J] · MOVER'S LEAD ON PILES I..J"
    val headers = (0 until n).map { "j $it" }
    fun f(rows: List<TRow>, pile: TRow, formula: List<String>, chips: List<StoryChip>, headline: String, body: String) =
        TFrame(n, rows, headline, body, formula = formula, chips = chips, heading = heading, headers = headers, topRows = listOf(pile))
    val lead = "Each cell is the lead the player to move can force, so the opponent's best reply is subtracted."
    val frames = mutableListOf(
        f(
            table(), pileRow(null, null, null), listOf("dp[i][j] = max(a[i] − dp[i+1][j], a[j] − dp[i][j−1])"), emptyList(),
            "Two players take a pile from either end. How far ahead can the first player finish?", "dp[i][j] is the lead the player to move can force on piles i..j.",
        ),
    )
    for (i in 0 until n) dp[i][i] = piles[i]
    frames += f(
        table(), pileRow(null, null, null), listOf("dp[i][i] = a[i]"), emptyList(), "With one pile left, the mover takes it: the lead is the pile itself.",
        "The diagonal is the base case; longer ranges build on shorter ones.",
    )
    for (i in 0 until n - 1) dp[i][i + 1] = maxOf(piles[i] - piles[i + 1], piles[i + 1] - piles[i])
    frames += f(table(), pileRow(null, null, null), listOf("dp[i][i+1] = |a[i] − a[i+1]|"), emptyList(), "With two piles, take the bigger one; the lead is the difference.", lead)
    for (len in 3..n) {
        for (i in 0..n - len) {
            val j = i + len - 1
            val left = piles[i] - dp[i + 1][j]!!
            val right = piles[j] - dp[i][j - 1]!!
            val best = maxOf(left, right)
            val takeLeft = left >= right
            val done = len == n
            val base = "take ${piles[i]}: ${piles[i]} − {${m(dp[i + 1][j]!!)}} = ${m(left)} · take ${piles[j]}: ${piles[j]} − ( {${m(dp[i][j - 1]!!)}} ) = "
            val formula = if (takeLeft) base + "${m(right)} → {v:${m(best)}}" else base + "{v:${m(right)}}"
            val chosen = if (takeLeft) piles[i] else piles[j]
            val other = if (takeLeft) piles[j] else piles[i]
            val worth = if (takeLeft) dp[i + 1][j]!! else dp[i][j - 1]!!
            dp[i][j] = best
            frames += f(
                table(listOf(i + 1 to j, i to j - 1), if (done) i to j else null), pileRow(i, j, if (takeLeft) i else j), listOf(formula),
                if (done) listOf(StoryChip("first player wins by", m(best), StoryTone.Answer)) else emptyList(),
                if (best >= 0) "Taking $chosen, not $other, ${if (done) "wins by" else "leads by"} ${m(best)}: it leaves the opponent a position worth ${m(worth)}."
                else "Piles $i..$j: even the better take, $chosen, trails by ${m(-best)}.",
                lead,
            )
        }
    }
    return listOf(
        TTab(
            "Piles", frames,
            listOf(
                Triple(SimColors.Green, SwatchStyle.Fill, "Mover ahead"), Triple(SimColors.Red, SwatchStyle.Fill, "Mover behind"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Read now"), Triple(SimColors.Answer, SwatchStyle.Fill, "Answer"),
            ),
        ),
    )
}

private fun islandsTabs(): List<TTab> {
    val map = listOf("11000110", "10010110", "00110000", "00100011", "10010011", "11001100").map { r -> r.map { it == '1' } }
    val rows = map.size
    val cols = map[0].size
    val island = MutableList(rows * cols) { 0 }
    var count = 0
    val frames = mutableListOf<TFrame>()
    val totalLand = map.sumOf { r -> r.count { it } }
    fun grid(cur: Int?, queued: Set<Int>, current: Int) = (0 until rows).map { r ->
        TRow(
            (0 until cols).map { c ->
                val k = r * cols + c
                when {
                    !map[r][c] -> TCell("", TTone.Pending)
                    k == cur -> TCell("${island[k]}", TTone.Active)
                    k in queued -> TCell("", TTone.Frontier)
                    island[k] == 0 -> TCell("", TTone.Idle)
                    else -> TCell("${island[k]}", if (island[k] == current) TTone.Outline else TTone.Green)
                }
            },
        )
    }
    fun left() = totalLand - island.count { it > 0 }
    val body = "Every cell is visited once, so the count is O(rows × cols)."
    frames += TFrame(
        cols, grid(null, emptySet(), 0), "Count the islands: groups of land joined up, down, left or right.",
        "Scan every cell; the first unvisited land cell of each island starts a flood fill.",
        formula = listOf("scan cells · unvisited land starts a flood"), chips = listOf(StoryChip("islands", "0"), StoryChip("land left", "$totalLand")), noHeader = true,
    )
    for (start in 0 until rows * cols) {
        if (!map[start / cols][start % cols] || island[start] != 0) continue
        count++
        island[start] = count
        val queue = mutableListOf(start)
        val queued = mutableSetOf(start)
        var head = 0
        while (head < queue.size) {
            val cur = queue[head++]
            queued -= cur
            val added = mutableListOf<Int>()
            for ((dr, dc) in GridDirs) {
                val r = cur / cols + dr
                val c = cur % cols + dc
                if (r !in 0 until rows || c !in 0 until cols || !map[r][c]) continue
                val k = r * cols + c
                if (island[k] != 0) continue
                island[k] = count
                queue += k
                queued += k
                added += k
            }
            val pos = "(${cur / cols},${cur % cols})"
            val addText = if (added.isEmpty()) "no new land" else added.joinToString(" ") { "(${it / cols},${it % cols})" } + " {p:queued}"
            frames += TFrame(
                cols, grid(cur, queued.toSet(), count),
                if (cur == start) "$pos is unvisited land, so island $count starts here. The flood marks everything connected to it."
                else "$pos belongs to island $count${if (added.isEmpty()) "; nothing new around it." else ", and its land neighbours join the queue."}",
                body, formula = listOf("$pos land → mark {$count} · neighbour $addText"),
                chips = listOf(StoryChip("islands", "$count"), StoryChip("land left", "${left()}")), noHeader = true,
            )
        }
    }
    frames += TFrame(
        cols, grid(null, emptySet(), 0), "The scan is done: {v:$count} islands.", "Marking cells as visited is what stops a flood from counting an island twice.",
        formula = listOf("islands = {v:$count}"), chips = listOf(StoryChip("islands", "$count", StoryTone.Answer)), noHeader = true,
    )
    return listOf(
        TTab(
            "Flood fill", frames,
            listOf(
                Triple(SimColors.Green, SwatchStyle.Fill, "Counted"), Triple(SimColors.Blue, SwatchStyle.Fill, "This island"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Visiting"), Triple(SimColors.Blue, SwatchStyle.Ring, "Queued"),
                Triple(Color.Gray.copy(alpha = 0.35f), SwatchStyle.Fill, "Unvisited land"),
            ),
        ),
    )
}

private fun dagDpTabs(): List<TTab> {
    val nodes = listOf(TGNode("A", 0.05, 0.05), TGNode("B", 0.05, 0.95), TGNode("C", 0.4, 0.5), TGNode("D", 0.7, 0.05), TGNode("E", 0.7, 0.95), TGNode("F", 0.97, 0.5))
    val edges = listOf(Triple(0, 2, 3), Triple(1, 2, 6), Triple(2, 3, 4), Triple(2, 4, 2), Triple(3, 5, 5), Triple(4, 5, 9))
    val names = nodes.map { it.label }
    fun run(longest: Boolean): List<TFrame> {
        val dp = MutableList<Int?>(nodes.size) { null }
        val from = MutableList<Int?>(nodes.size) { null }
        dp[0] = 0
        dp[1] = 0
        val done = mutableSetOf<Int>()
        val inf = if (longest) "−∞" else "∞"
        fun graph(cur: Int?, improved: Map<Int, Int?>): TGraph {
            val tones = mutableMapOf<Int, TTone>()
            val caps = mutableMapOf<Int, Pair<String, TTone>>()
            for (i in nodes.indices) {
                when {
                    i == cur -> { tones[i] = TTone.Active; caps[i] = "${dp[i]}" to TTone.Active }
                    i in improved -> { tones[i] = TTone.Outline; caps[i] = "${improved[i]?.toString() ?: inf} → ${dp[i]}" to TTone.Blue }
                    i in done -> { tones[i] = TTone.Green; caps[i] = "${dp[i]}" to TTone.Green }
                    else -> caps[i] = (dp[i]?.toString() ?: inf) to TTone.Idle
                }
            }
            val es = edges.map { e -> TGEdge(e.first, e.second, "${e.third}", if (e.first == cur) TTone.Active else if (from[e.second] == e.first) TTone.Blue else null) }
            return TGraph(nodes, es, tones = tones, captions = caps)
        }
        fun order(cur: Int?, improved: Map<Int, Int?>) = TRow(
            names.indices.map { i -> TCell(names[i], if (i == cur) TTone.Active else if (i in done) TTone.Green else if (i in improved) TTone.Outline else TTone.Idle) },
            title = "TOPOLOGICAL ORDER", spread = true,
        )
        val body = "In topological order every predecessor is final before a node is read, so one pass is enough."
        val frames = mutableListOf(
            TFrame(
                0, listOf(order(null, emptyMap())), "The ${if (longest) "longest" else "shortest"} path to every node, reading nodes in topological order.",
                "A and B have no incoming edges, so they start at 0.", formula = listOf("dp[v] = ${if (longest) "max" else "min"} over edges u → v of dp[u] + w"),
                graph = graph(null, emptyMap()),
            ),
        )
        for (u in nodes.indices) {
            val improved = mutableMapOf<Int, Int?>()
            val lines = mutableListOf<String>()
            val beaten = mutableMapOf<Int, Int?>()
            for (e in edges.filter { it.first == u }) {
                val cand = dp[u]!! + e.third
                val old = dp[e.second]
                val better = old?.let { if (longest) cand > it else cand < it } ?: true
                if (better) { improved[e.second] = old; beaten[e.second] = from[e.second]; dp[e.second] = cand; from[e.second] = u }
                val cmp = old?.let { if (better) (if (longest) " > $it" else " < $it") else (if (longest) " ≤ $it" else " ≥ $it") } ?: ""
                lines += "dp[${names[u]}] {${dp[u]}} + ${e.third} = $cand$cmp → " + if (better) "dp[${names[e.second]}] = {p:$cand}" else "keep $old"
            }
            val outs = edges.filter { it.first == u }
            val replaced = improved.entries.firstOrNull { it.value != null }
            val headline = when {
                outs.isEmpty() -> "${names[u]} has no outgoing edges; its value ${dp[u]} is final."
                replaced != null -> "Through ${names[u]}, ${names[replaced.key]} reaches ${dp[replaced.key]}, ${if (longest) "beating" else "undercutting"} the ${replaced.value} it got through ${names[beaten[replaced.key]!!]}."
                improved.isEmpty() -> "Nothing through ${names[u]} is ${if (longest) "longer" else "shorter"}; every neighbour keeps its value."
                else -> "${names[u]} = ${dp[u]} is final, so it passes ${improved.keys.sorted().joinToString(" and ") { "${names[it]} = ${dp[it]}" }} along."
            }
            frames += TFrame(
                0, listOf(order(u, improved)), headline, body, formula = if (lines.isEmpty()) listOf("${names[u]} is a sink → final") else lines, graph = graph(u, improved),
            )
            done += u
        }
        val path = mutableListOf(5)
        while (true) { val p = from[path[0]] ?: break; path.add(0, p) }
        val route = path.joinToString(" → ") { names[it] }
        frames += TFrame(
            0, listOf(order(null, emptyMap())), "The ${if (longest) "longest" else "shortest"} path to F is {v:${dp[5]}}: $route.",
            "O(V + E): each edge is relaxed exactly once. Longest path is only this easy on a DAG.",
            formula = listOf("dp[F] = {v:${dp[5]}} via $route"), graph = graph(null, emptyMap()),
        )
        return frames
    }
    val legend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Relaxing from"), Triple(SimColors.Blue, SwatchStyle.Fill, "Improved"), Triple(SimColors.Green, SwatchStyle.Fill, "Final"))
    return listOf(TTab("Longest", run(true), legend), TTab("Shortest", run(false), legend))
}

private fun twoColourTabs(): List<TTab> {
    val nodes = listOf(TGNode("A", 0.05, 0.05), TGNode("B", 0.05, 0.95), TGNode("C", 0.42, 0.5), TGNode("D", 0.72, 0.05), TGNode("E", 0.72, 0.95), TGNode("F", 0.97, 0.5))
    val names = nodes.map { it.label }
    val all = listOf(0 to 1, 0 to 2, 1 to 2, 2 to 3, 2 to 4, 3 to 5, 4 to 5)
    val frames = mutableListOf<TFrame>()
    fun frame(side: Map<Int, Int>, edges: List<Pair<Int, Int>>, conflict: Pair<Int, Int>?, dropped: Pair<Int, Int>?, formula: List<String>, headline: String, body: String) {
        val es = edges.map { e -> TGEdge(e.first, e.second, tone = if (e == conflict) TTone.Mismatch else null, dashed = e == conflict) } +
            listOfNotNull(dropped?.let { TGEdge(it.first, it.second, dashed = true) })
        frames += TFrame(
            0, emptyList(), headline, body, formula = formula,
            graph = TGraph(nodes, es, directed = false, tones = side.mapValues { if (it.value == 1) TTone.Blue else TTone.Rose }),
        )
    }
    val bfsBody = "BFS gives each neighbour the opposite side."
    fun bfs(edges: List<Pair<Int, Int>>, dropped: Pair<Int, Int>?): Boolean {
        val side = mutableMapOf(0 to 1)
        frame(
            side.toMap(), edges, null, dropped, listOf(if (dropped == null) "BFS from A · neighbours get the other side" else "without B – C · BFS from A again"),
            if (dropped == null) "Start BFS at A and put it on side 1." else "Drop the edge B – C and run the same BFS.", bfsBody,
        )
        val queue = mutableListOf(0)
        var head = 0
        while (head < queue.size) {
            val u = queue[head++]
            val newly = mutableListOf<Int>()
            for ((a, b) in edges) {
                if (a != u && b != u) continue
                val v = if (a == u) b else a
                if (v !in side) { side[v] = 3 - side.getValue(u); queue += v; newly += v }
            }
            if (newly.isNotEmpty()) {
                frame(
                    side.toMap(), edges, null, dropped, listOf("${names[u]} side ${side[u]} → ${newly.joinToString(", ") { names[it] }} side ${3 - side.getValue(u)}"),
                    "${newly.joinToString(" and ") { names[it] }} ${if (newly.size == 1) "is" else "are"} next to ${names[u]}, so ${if (newly.size == 1) "it goes" else "they go"} on side ${3 - side.getValue(u)}.",
                    bfsBody,
                )
            }
            for ((a, b) in edges) {
                if ((a == u || b == u) && side[a] != null && side[a] == side[b]) {
                    frame(
                        side.toMap(), edges, a to b, dropped,
                        listOf("edge {w:${names[a]} – ${names[b]}}", ": both side ${side[a]} → odd cycle A → ${names[a]} → ${names[b]} → A"),
                        "${names[a]} and ${names[b]} both got side ${side[a]}, and they share an edge, so the graph is not bipartite.",
                        "BFS gives each neighbour the opposite side. A conflict only happens on an odd cycle, here the triangle A–B–C.",
                    )
                    return false
                }
            }
        }
        frame(
            side.toMap(), edges, null, dropped, listOf("every edge joins side 1 to side 2 → {v:bipartite}"),
            "Every edge now joins the two sides: without B – C the graph is {v:bipartite}.", "The remaining cycle C–D–F–E–C has even length, so it can alternate. O(V + E).",
        )
        return true
    }
    bfs(all, null)
    bfs(all.filter { it != (1 to 2) }, 1 to 2)
    return listOf(
        TTab(
            "Two-colour", frames,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Side 1"), Triple(RoseColor, SwatchStyle.Fill, "Side 2"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Conflict edge"), Triple(NotCalledGrey, SwatchStyle.Fill, "Not reached"),
            ),
        ),
    )
}

private fun topoTabs(): List<TTab> {
    val nodes = listOf(TGNode("101", 0.0, 0.5), TGNode("201", 0.38, 0.05), TGNode("210", 0.38, 0.95), TGNode("301", 0.66, 0.5), TGNode("330", 1.0, 0.05), TGNode("401", 1.0, 0.95))
    val names = nodes.map { it.label }
    val edges = listOf(0 to 1, 0 to 2, 1 to 3, 2 to 3, 3 to 4, 3 to 5)
    fun ord(i: Int) = when (i) {
        1 -> "1st"
        2 -> "2nd"
        3 -> "3rd"
        else -> "${i}th"
    }
    fun kahn(): List<TFrame> {
        val indeg = nodes.indices.map { v -> edges.count { it.second == v } }.toMutableList()
        val order = mutableListOf<Int>()
        val queue = nodes.indices.filter { indeg[it] == 0 }.toMutableList()
        val frames = mutableListOf<TFrame>()
        fun graph(cur: Int?, dropped: Map<Int, Int>): TGraph {
            val tones = mutableMapOf<Int, TTone>()
            val caps = mutableMapOf<Int, Pair<String, TTone>>()
            for (v in nodes.indices) {
                val k = order.indexOf(v)
                when {
                    k >= 0 -> { tones[v] = if (v == cur) TTone.Active else TTone.Green; caps[v] = ord(k + 1) to if (v == cur) TTone.Active else TTone.Green }
                    v in dropped -> { tones[v] = if (indeg[v] == 0) TTone.Outline else TTone.Idle; caps[v] = "in ${dropped[v]} → ${indeg[v]}" to TTone.Blue }
                    v in queue -> { tones[v] = TTone.Outline; caps[v] = "in 0" to TTone.Blue }
                    else -> caps[v] = "in ${indeg[v]}" to TTone.Idle
                }
            }
            val es = edges.map { e -> TGEdge(e.first, e.second, tone = if (e.first == cur) TTone.Active else if (e.first in order) TTone.Green else null) }
            return TGraph(nodes, es, tones = tones, captions = caps)
        }
        fun text(cur: Int?) = "order " + order.joinToString(" ") { if (it == cur) "{${names[it]}}" else names[it] } +
            " · queue {p:${if (queue.isEmpty()) "–" else queue.joinToString(" ") { names[it] }}}"
        val body = "A course is only queued once every prerequisite is taken. If the queue empties before every course is taken, there is a cycle."
        frames += TFrame(
            0, emptyList(), "Count each course's prerequisites: 101 has none, so it starts in the queue.", "A course is only queued once every prerequisite is taken.",
            formula = listOf(text(null)), graph = graph(null, emptyMap()),
        )
        while (queue.isNotEmpty()) {
            val u = queue.removeAt(0)
            order += u
            val dropped = mutableMapOf<Int, Int>()
            val ready = mutableListOf<Int>()
            for (e in edges.filter { it.first == u }) {
                dropped[e.second] = indeg[e.second]
                indeg[e.second]--
                if (indeg[e.second] == 0) { queue += e.second; ready += e.second }
            }
            val headline = when {
                dropped.isEmpty() -> "${names[u]} unlocks nothing; it is the ${ord(order.size)} course taken."
                ready.isEmpty() -> "Taking ${names[u]} drops ${dropped.keys.sorted().joinToString(" and ") { names[it] }} to in-degree ${dropped.keys.sorted().joinToString(", ") { "${indeg[it]}" }}; still waiting."
                else -> "Taking ${names[u]} drops the in-degree of ${ready.joinToString(" and ") { names[it] }} to 0, so ${if (ready.size == 1) "it joins" else "both join"} the queue."
            }
            frames += TFrame(0, emptyList(), headline, body, formula = listOf(text(u)), graph = graph(u, dropped))
        }
        val route = order.joinToString(" → ") { names[it] }
        frames += TFrame(
            0, emptyList(), "All ${order.size} courses taken: {v:$route}.", "O(V + E): every course is queued once and every prerequisite edge is removed once.",
            formula = listOf("order = {v:$route}"), graph = graph(null, emptyMap()),
        )
        return frames
    }
    fun dfsOrder(): List<TFrame> {
        val state = MutableList(nodes.size) { 0 }
        val finish = mutableListOf<Int>()
        val frames = mutableListOf<TFrame>()
        fun graph(cur: Int?): TGraph {
            val tones = mutableMapOf<Int, TTone>()
            val caps = mutableMapOf<Int, Pair<String, TTone>>()
            for (v in nodes.indices) {
                when {
                    v == cur -> tones[v] = TTone.Active
                    state[v] == 1 -> tones[v] = TTone.Stack
                    state[v] == 2 -> tones[v] = TTone.Green
                }
                val k = finish.indexOf(v)
                if (k >= 0) caps[v] = "done ${k + 1}" to TTone.Green
            }
            val es = edges.map { e ->
                TGEdge(e.first, e.second, tone = if (state[e.first] >= 1 && state[e.second] >= 1) (if (state[e.second] == 2) TTone.Green else TTone.Active) else null)
            }
            return TGraph(nodes, es, tones = tones, captions = caps)
        }
        fun text() = "finish " + if (finish.isEmpty()) "–" else finish.joinToString(" ") { names[it] }
        fun visit(u: Int) {
            state[u] = 1
            frames += TFrame(
                0, emptyList(), "Enter ${names[u]} and follow its edges before finishing it.", "A course finishes only after everything that depends on it has finished.",
                formula = listOf(text()), graph = graph(u),
            )
            for (e in edges) if (e.first == u && state[e.second] == 0) visit(e.second)
            state[u] = 2
            finish += u
            frames += TFrame(
                0, emptyList(), "${names[u]} has nothing unvisited left, so it finishes ${ord(finish.size)}.",
                "Reversed finish order puts every course after its prerequisites. Meeting a node still on the stack means a cycle.",
                formula = listOf(text()), graph = graph(u),
            )
        }
        visit(0)
        val route = finish.reversed().joinToString(" → ") { names[it] }
        frames += TFrame(
            0, emptyList(), "Reverse the finish order: {v:$route}.", "Also O(V + E). Kahn's version is easier to stop early; the DFS one finds cycles as back edges.",
            formula = listOf(text() + " → reverse → {v:$route}"), graph = graph(null),
        )
        return frames
    }
    return listOf(
        TTab(
            "Kahn · in-degree", kahn(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Taken now"), Triple(SimColors.Blue, SwatchStyle.Fill, "Ready · in-degree 0"), Triple(SimColors.Green, SwatchStyle.Fill, "Taken")),
        ),
        TTab(
            "DFS · finish order", dfsOrder(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Active"), Triple(SimColors.Active, SwatchStyle.Ring, "On the stack"), Triple(SimColors.Green, SwatchStyle.Fill, "Finished")),
        ),
    )
}

private fun weightedPathTabs(): List<TTab> {
    val nodes = listOf(TGNode("S", 0.0, 0.5), TGNode("A", 0.36, 0.05), TGNode("B", 0.36, 0.95), TGNode("C", 0.68, 0.5), TGNode("T", 1.0, 0.05))
    val names = nodes.map { it.label }
    val edges = listOf(Triple(0, 1, 2), Triple(0, 2, 5), Triple(1, 3, 1), Triple(1, 4, 6), Triple(2, 3, 2), Triple(3, 4, 2))
    fun dijkstra(): List<TFrame> {
        val dist = MutableList<Int?>(nodes.size) { null }
        val from = MutableList<Int?>(nodes.size) { null }
        dist[0] = 0
        val heap = mutableListOf(0 to 0)
        val done = mutableSetOf<Int>()
        val frames = mutableListOf<TFrame>()
        fun graph(cur: Int?, improved: Map<Int, Int?>): TGraph {
            val tones = mutableMapOf<Int, TTone>()
            val caps = mutableMapOf<Int, Pair<String, TTone>>()
            for (v in nodes.indices) {
                val d = dist[v]
                when {
                    v == cur -> { tones[v] = TTone.Active; caps[v] = "$d" to TTone.Active }
                    v in done -> { tones[v] = TTone.Green; caps[v] = "$d" to TTone.Green }
                    d != null -> { tones[v] = TTone.Outline; caps[v] = (improved[v]?.let { "$it → $d" } ?: "$d") to TTone.Blue }
                    else -> caps[v] = "∞" to TTone.Idle
                }
            }
            val es = edges.map { e ->
                TGEdge(e.first, e.second, "${e.third}", if (e.first == cur) TTone.Active else if (from[e.second] == e.first && e.first in done) TTone.Green else null)
            }
            return TGraph(nodes, es, tones = tones, captions = caps)
        }
        fun heapRow(): TRow {
            val cells = heap.sortedWith(compareBy({ it.first }, { it.second })).map { (d, v) ->
                TCell("$d · ${names[v]}", if (dist[v] != null && dist[v]!! < d) TTone.Mismatch else TTone.Outline)
            }.toMutableList()
            while (cells.size < 4) cells += TCell("", TTone.Ghost)
            return TRow(cells, title = "MIN-HEAP · DIST, NODE", spread = true)
        }
        frames += TFrame(
            0, listOf(heapRow()), "Find the cheapest route from S to every node.", "The heap always hands back the closest node not yet settled.",
            formula = listOf("dist[S] = 0 · every other dist = ∞"), graph = graph(null, emptyMap()),
        )
        while (heap.isNotEmpty()) {
            heap.sortWith(compareBy({ it.first }, { it.second }))
            val (d, u) = heap.removeAt(0)
            if (u in done || d > dist[u]!!) {
                frames += TFrame(
                    0, listOf(heapRow()), "The old ($d, ${names[u]}) entry comes out, but ${names[u]} is already settled at ${dist[u]}, so it is skipped.",
                    "Instead of decreasing a key, push a new entry and ignore the stale one later.",
                    formula = listOf("pop ({w:$d, ${names[u]}}) → $d > dist[${names[u]}] ${dist[u]} → skip"), graph = graph(null, emptyMap()),
                )
                continue
            }
            val improved = mutableMapOf<Int, Int?>()
            val lines = mutableListOf<String>()
            for (e in edges.filter { it.first == u && it.second !in done }) {
                val cand = d + e.third
                val old = dist[e.second]
                if (old == null || cand < old) {
                    improved[e.second] = old
                    dist[e.second] = cand
                    from[e.second] = u
                    heap += cand to e.second
                    lines += "dist[${names[u]}] {$d} + ${e.third} = $cand${old?.let { " < $it" } ?: ""} → dist[${names[e.second]}] = {p:$cand}"
                } else {
                    lines += "dist[${names[u]}] {$d} + ${e.third} = $cand ≥ $old → keep"
                }
            }
            done += u
            val drop = improved.entries.firstOrNull { it.value != null }
            val headline = drop?.let { "Through ${names[u]}, ${names[it.key]} costs ${dist[it.key]} instead of ${it.value}, so its distance drops." }
                ?: if (improved.isEmpty()) "${names[u]} is settled at $d; it improves nothing."
                else "Settle ${names[u]} at $d and reach ${improved.keys.sorted().joinToString(", ") { "${names[it]} = ${dist[it]}" }}."
            frames += TFrame(
                0, listOf(heapRow()), headline,
                drop?.let { "The old (${it.value}, ${names[it.key]}) entry is skipped when popped." } ?: "The smallest dist in the heap can't get any smaller, so popping it settles it.",
                formula = if (lines.isEmpty()) listOf("settle ${names[u]} at {$d}") else lines, graph = graph(u, improved),
            )
        }
        val path = mutableListOf(4)
        while (true) { val p = from[path[0]] ?: break; path.add(0, p) }
        val route = path.joinToString(" → ") { names[it] }
        frames += TFrame(
            0, listOf(heapRow()), "The cheapest route to T costs {v:${dist[4]}}: $route.",
            "O((V + E) log V) with a binary heap. Negative edges would break it; use Bellman-Ford then.",
            formula = listOf("dist[T] = {v:${dist[4]}} via $route"), graph = graph(null, emptyMap()),
        )
        return frames
    }
    fun bfs(): List<TFrame> {
        val dist = MutableList<Int?>(nodes.size) { null }
        val from = MutableList<Int?>(nodes.size) { null }
        dist[0] = 0
        val queue = mutableListOf(0)
        val done = mutableSetOf<Int>()
        val frames = mutableListOf<TFrame>()
        fun graph(cur: Int?): TGraph {
            val tones = mutableMapOf<Int, TTone>()
            val caps = mutableMapOf<Int, Pair<String, TTone>>()
            for (v in nodes.indices) {
                when {
                    v == cur -> tones[v] = TTone.Active
                    v in done -> tones[v] = TTone.Green
                    dist[v] != null -> tones[v] = TTone.Outline
                }
                caps[v] = (dist[v]?.toString() ?: "∞") to when {
                    v == cur -> TTone.Active
                    v in done -> TTone.Green
                    dist[v] != null -> TTone.Blue
                    else -> TTone.Idle
                }
            }
            val es = edges.map { e -> TGEdge(e.first, e.second, "1", if (e.first == cur) TTone.Active else if (from[e.second] == e.first && e.first in done) TTone.Green else null) }
            return TGraph(nodes, es, tones = tones, captions = caps)
        }
        fun queueRow(): TRow {
            val cells = queue.map { TCell("${dist[it]} · ${names[it]}", TTone.Outline) }.toMutableList()
            while (cells.size < 4) cells += TCell("", TTone.Ghost)
            return TRow(cells, title = "QUEUE · FIFO", spread = true)
        }
        frames += TFrame(
            0, listOf(queueRow()), "With unit edges, the fewest hops is the shortest path.", "BFS reaches nodes in order of distance, so no heap is needed.",
            formula = listOf("every edge costs 1 → a plain queue is enough"), graph = graph(null),
        )
        while (queue.isNotEmpty()) {
            val u = queue.removeAt(0)
            val reached = mutableListOf<Int>()
            for (e in edges) if (e.first == u && dist[e.second] == null) { dist[e.second] = dist[u]!! + 1; from[e.second] = u; queue += e.second; reached += e.second }
            done += u
            val du = dist[u]!!
            frames += TFrame(
                0, listOf(queueRow()),
                if (reached.isEmpty()) "${names[u]} is $du hop${if (du == 1) "" else "s"} away and reaches nothing new."
                else "${names[u]} reaches ${reached.joinToString(" and ") { names[it] }} at ${du + 1} hop${if (du + 1 == 1) "" else "s"}.",
                "The first time BFS reaches a node is already its shortest distance.",
                formula = listOf("dequeue {${names[u]}} at $du" + if (reached.isEmpty()) " · nothing new" else " → ${reached.joinToString(", ") { names[it] }} at {p:${du + 1}}"),
                graph = graph(u),
            )
        }
        frames += TFrame(
            0, listOf(queueRow()), "T is {v:${dist[4]}} hops away, via A.",
            "O(V + E). Once edges have different weights, fewest hops is no longer cheapest: that is when Dijkstra is needed.",
            formula = listOf("hops to T = {v:${dist[4]}}"), graph = graph(null),
        )
        return frames
    }
    return listOf(
        TTab(
            "Dijkstra", dijkstra(),
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Settling"), Triple(SimColors.Blue, SwatchStyle.Fill, "Reached"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Final"), Triple(SimColors.Red, SwatchStyle.Fill, "Stale entry"),
            ),
        ),
        TTab(
            "BFS · unit edges", bfs(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Dequeued"), Triple(SimColors.Blue, SwatchStyle.Fill, "Queued"), Triple(SimColors.Green, SwatchStyle.Fill, "Done")),
        ),
    )
}

// ── Table and design pattern storyboards ─────────────────────────────────────
// State machine DP, grid DP, interval DP, matrix rotation, XOR splitting, 2D prefix sums, the bit trie and LRU/LFU.

private fun sgn(v: Int) = if (v < 0) "−${-v}" else "$v"
private fun dash(v: Int) = if (v < 0) "-${-v}" else "$v"
private val FillLegend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current"), Triple(SimColors.Blue, SwatchStyle.Fill, "Read"), Triple(SimColors.Green, SwatchStyle.Fill, "Filled"))

private fun stateMachineTabs(): List<TTab> {
    val prices = listOf(1, 2, 3, 0, 2)
    val n = prices.size
    val modes = listOf("hold", "sold", "rest")
    val v = List(3) { MutableList<Int?>(n) { null } }
    val set = List(3) { MutableList(n) { false } }
    val headers = prices.mapIndexed { d, p -> "d$d · $p" }
    val flow = listOf(
        listOf("rest" to true, "buy →" to false, "hold" to true, "sell →" to false, "sold" to true, "cooldown →" to false),
        listOf("rest" to true),
    )
    fun grid(cur: Pair<Int, Int>?, reads: List<Pair<Int, Int>>, answer: Pair<Int, Int>?) = (0 until 3).map { m ->
        TRow(
            (0 until n).map { d ->
                if (!set[m][d]) return@map TCell("", TTone.Ghost)
                val text = v[m][d]?.let { dash(it) } ?: "–"
                when {
                    answer == (m to d) -> TCell(text, TTone.Answer)
                    cur == (m to d) -> TCell(text, TTone.Active)
                    (m to d) in reads -> TCell(text, TTone.Outline)
                    else -> TCell(text, TTone.Green)
                }
            },
            modes[m], labelTone = if (cur?.first == m) TTone.Active else null,
        )
    }
    fun f(cur: Pair<Int, Int>?, reads: List<Pair<Int, Int>>, formula: List<String>, headline: String, body: String, answer: Pair<Int, Int>? = null, chips: List<StoryChip> = emptyList()) =
        TFrame(
            n, grid(cur, reads, answer), headline, body, lit = cur?.let { mapOf(it.second to TTone.Active) } ?: emptyMap(),
            formula = formula, chips = chips, headers = headers, flow = flow,
        )
    val frames = mutableListOf(
        f(
            null, emptyList(), listOf("hold = own a share · sold = sold today · rest = free to buy"), "Buy and sell with a one-day cooldown after each sale.",
            "Track three states per day. Each state's best balance depends only on yesterday's.",
        ),
    )
    v[0][0] = -prices[0]
    v[2][0] = 0
    for (m in 0 until 3) set[m][0] = true
    frames += f(null, emptyList(), listOf("hold[0] = −${prices[0]} · sold[0] = – · rest[0] = 0"), "Day 0: buy for ${prices[0]} or do nothing. Nothing can be sold yet.", "The first column seeds the table.")
    for (d in 1 until n) {
        val p = prices[d]
        val keep = v[0][d - 1]!!
        val restY = v[2][d - 1]!!
        val buy = restY - p
        v[0][d] = maxOf(keep, buy)
        set[0][d] = true
        frames += f(
            0 to d, listOf(0 to d - 1, 2 to d - 1), listOf("hold[$d] = max(hold {p:${dash(keep)}} , rest {p:${dash(restY)}} − price $p) = {${dash(v[0][d]!!)}}"),
            if (buy > keep) "Buying on day $d reads yesterday's rest, never sold, which is how the cooldown is enforced."
            else "Keeping yesterday's share (${sgn(keep)}) beats buying at $p from a rest of ${sgn(restY)}.",
            if (buy > keep) "Price $p turns a rest balance of ${sgn(restY)} into a hold of ${sgn(buy)}, better than keeping the ${sgn(keep)} share."
            else "A buy always comes from rest, never from sold, so the day after a sale can't buy.",
        )
        v[1][d] = v[0][d - 1]!! + p
        set[1][d] = true
        frames += f(
            1 to d, listOf(0 to d - 1), listOf("sold[$d] = hold {p:${dash(v[0][d - 1]!!)}} + price $p = {${dash(v[1][d]!!)}}"),
            "Selling on day $d: yesterday's hold ${sgn(v[0][d - 1]!!)} plus price $p gives ${sgn(v[1][d]!!)}.", "Only a share held yesterday can be sold today.",
        )
        v[2][d] = maxOf(restY, v[1][d - 1] ?: Int.MIN_VALUE)
        set[2][d] = true
        frames += f(
            2 to d, listOf(2 to d - 1) + if (v[1][d - 1] != null) listOf(1 to d - 1) else emptyList(),
            listOf("rest[$d] = max(rest {p:${dash(restY)}} , sold {p:${v[1][d - 1]?.let { dash(it) } ?: "–"}}) = {${dash(v[2][d]!!)}}"),
            "Resting keeps the best of yesterday's rest and yesterday's sale: ${sgn(v[2][d]!!)}.", "Yesterday's sale lands here, in rest, one day later: that is the cooldown.",
        )
    }
    val best = maxOf(v[1][n - 1]!!, v[2][n - 1]!!)
    val bestMode = if (v[1][n - 1]!! >= v[2][n - 1]!!) 1 else 2
    frames += f(
        null, emptyList(), listOf("profit = max(sold, rest) on day ${n - 1} = {v:$best}"), "The best profit is {v:$best}: end not holding a share.",
        "Three states × n days, each O(1): O(n) time, and only yesterday's column is needed.", bestMode to n - 1, listOf(StoryChip("profit", "$best", StoryTone.Answer)),
    )
    return listOf(TTab("Cooldown", frames, FillLegend))
}

private fun gridDpTabs(): List<TTab> {
    val cost = listOf(listOf(1, 3, 1, 2), listOf(1, 5, 1, 3), listOf(4, 2, 1, 1))
    val rows = cost.size
    val cols = cost[0].size
    val headers = (0 until cols).map { "c$it" }
    val body = "Each cell reads only two finished neighbours, so the table fills row by row in O(rows × cols)."
    fun run(paths: Boolean): List<TFrame> {
        val blocked = if (paths) setOf(cols + 1) else emptySet()
        val dp = List(rows) { MutableList<Int?>(cols) { null } }
        fun grid(cur: Pair<Int, Int>?, reads: List<Pair<Int, Int>>, answer: Boolean = false) = (0 until rows).map { r ->
            TRow(
                (0 until cols).map { c ->
                    val v = dp[r][c]
                    when {
                        r * cols + c in blocked -> TCell("×", TTone.Mismatch)
                        v == null -> if (paths) TCell("", TTone.Ghost) else TCell("${cost[r][c]}", TTone.Pending)
                        answer && r == rows - 1 && c == cols - 1 -> TCell("$v", TTone.Answer)
                        cur == (r to c) -> TCell("$v", TTone.Active)
                        (r to c) in reads -> TCell("$v", TTone.Outline)
                        else -> TCell("$v", TTone.Green)
                    }
                },
                "r$r", labelTone = if (cur?.first == r) TTone.Active else null,
            )
        }
        val frames = mutableListOf(
            TFrame(
                cols, grid(null, emptyList()),
                if (paths) "Count the paths from the top-left to the bottom-right, moving only right or down."
                else "Find the cheapest path from the top-left to the bottom-right, moving only right or down.",
                body, formula = listOf(if (paths) "dp[r][c] = ↑ + ← · a blocked cell is 0" else "dp[r][c] = cost + min(↑, ←)"), headers = headers,
            ),
        )
        for (r in 0 until rows) for (c in 0 until cols) {
            if (r * cols + c in blocked) { dp[r][c] = 0; continue }
            val up = if (r > 0) dp[r - 1][c] else null
            val left = if (c > 0) dp[r][c - 1] else null
            val reads = mutableListOf<Pair<Int, Int>>()
            if (up != null && (r - 1) * cols + c !in blocked) reads += r - 1 to c
            if (left != null && r * cols + c - 1 !in blocked) reads += r to c - 1
            val formula: String
            val headline: String
            if (paths) {
                val v = if (r == 0 && c == 0) 1 else (up ?: 0) + (left ?: 0)
                dp[r][c] = v
                formula = if (r == 0 && c == 0) "dp[0][0] = {1}" else "dp[$r][$c] = ↑ {p:${up ?: 0}} + ← {p:${left ?: 0}} = {$v}"
                headline = when {
                    r == 0 && c == 0 -> "One way to stand at the start."
                    up == 0 || left == 0 -> "The blocked cell adds nothing, so $v path${if (v == 1) "" else "s"} arrive."
                    else -> "Paths arrive from above or from the left: ${up ?: 0} + ${left ?: 0} = $v."
                }
            } else {
                val best = listOfNotNull(up, left).minOrNull()
                val v = cost[r][c] + (best ?: 0)
                dp[r][c] = v
                if (up != null && left != null) {
                    formula = "dp[$r][$c] = cost ${cost[r][c]} + min(↑ {p:$up} , ← {p:$left} ) = {$v}"
                    headline = "Coming from above costs $up, from the left $left. Take the cheaper and add this cell's ${cost[r][c]}."
                } else if (up != null || left != null) {
                    val only = up ?: left
                    formula = "dp[$r][$c] = cost ${cost[r][c]} + ${if (up != null) "↑" else "←"} {p:$only} = {$v}"
                    headline = "On the ${if (up != null) "left edge" else "top row"} there is only one way in, so add ${cost[r][c]} to $only."
                } else {
                    formula = "dp[0][0] = cost {$v}"
                    headline = "The start costs its own $v."
                }
            }
            frames += TFrame(cols, grid(r to c, reads), headline, body, lit = mapOf(c to TTone.Active), formula = listOf(formula), headers = headers)
        }
        val ans = dp[rows - 1][cols - 1]!!
        frames += TFrame(
            cols, grid(null, emptyList(), answer = true), if (paths) "{v:$ans} paths reach the corner." else "The cheapest path costs {v:$ans}.",
            "One row of the table is enough if memory matters: O(cols) space.", formula = listOf("dp[${rows - 1}][${cols - 1}] = {v:$ans}"),
            chips = listOf(StoryChip(if (paths) "paths" else "min cost", "$ans", StoryTone.Answer)), headers = headers,
        )
        return frames
    }
    return listOf(
        TTab("Min path sum", run(false), FillLegend + Triple(Color.Gray.copy(alpha = 0.2f), SwatchStyle.Fill, "Cost, not yet filled")),
        TTab("Unique paths", run(true), FillLegend + Triple(SimColors.Red, SwatchStyle.Fill, "Blocked")),
    )
}

private fun intervalDpTabs(): List<TTab> {
    val a = listOf(1, 3, 1, 5, 8, 1)
    val n = a.size
    val rowsI = (0..n - 3).toList()
    val colsJ = (2 until n).toList()
    val dp = List(n) { MutableList<Int?>(n) { null } }
    for (i in 0 until n - 1) dp[i][i + 1] = 0
    val headers = colsJ.map { "j $it" }
    fun table(cur: Pair<Int, Int>?, reads: List<Pair<Int, Int>>, answer: Boolean) = rowsI.map { i ->
        TRow(
            colsJ.map { j ->
                val v = dp[i][j]
                when {
                    j < i + 2 -> TCell("", TTone.Pending)
                    v == null -> TCell("", TTone.Ghost)
                    answer && i == 0 && j == n - 1 -> TCell("$v", TTone.Answer)
                    cur == (i to j) -> TCell("$v", TTone.Active)
                    (i to j) in reads -> TCell("$v", TTone.Outline)
                    else -> TCell("$v", TTone.Green)
                }
            },
            "i $i", labelTone = if (cur?.first == i) TTone.Active else null,
        )
    }
    fun balloons(i: Int?, j: Int?, k: Int?) = TRow(
        a.mapIndexed { x, v ->
            TCell(
                "$v",
                when {
                    x == k -> TTone.Active
                    x == i || x == j -> TTone.Outline
                    i != null && j != null && x > i && x < j -> TTone.Pending
                    else -> TTone.Idle
                },
            )
        },
        title = "BALLOONS · PADDED WITH 1", spread = true,
    )
    fun f(cur: Pair<Int, Int>?, reads: List<Pair<Int, Int>>, k: Int?, formula: List<String>, headline: String, body: String, answer: Boolean = false, chips: List<StoryChip> = emptyList()) =
        TFrame(
            colsJ.size, table(cur, reads, answer), headline, body, lit = cur?.let { mapOf(colsJ.indexOf(it.second) to TTone.Active) } ?: emptyMap(),
            formula = formula, chips = chips, headers = headers, topRows = listOf(balloons(cur?.first, cur?.second, k)),
        )
    val frames = mutableListOf(
        f(
            null, emptyList(), null, listOf("dp[i][j] = max over i < k < j of dp[i][k] + dp[k][j] + a[i]·a[k]·a[j]"),
            "Burst every balloon for the most coins. Bursting k earns a[left]·a[k]·a[right].",
            "dp[i][j] is the best score for the balloons strictly between i and j, with i and j still standing.",
        ),
    )
    for (len in 2 until n) for (i in 0 until n - len) {
        val j = i + len
        var best = -1
        var k = i + 1
        for (kk in i + 1 until j) {
            val v = dp[i][kk]!! + dp[kk][j]!! + a[i] * a[kk] * a[j]
            if (v > best) { best = v; k = kk }
        }
        dp[i][j] = best
        val reads = listOf(i to k, k to j).filter { it.second - it.first >= 2 }
        frames += f(
            i to j, reads, k, listOf("last k = $k: dp[$i][$k] {p:${dp[i][k]}}", "+ dp[$k][$j] ${dp[k][j]} + ${a[i]}·{${a[k]}}·${a[j]} = {$best}"),
            "Burst ${a[k]} last between ${a[i]} and ${a[j]}: its neighbours are then exactly the two ends.",
            if (len == 2) "One balloon between the ends: it is the last one by default."
            else "Choosing the last balloon, not the first, keeps the two sides independent. ${j - i - 1} choices of k tried.",
        )
    }
    val ans = dp[0][n - 1]!!
    frames += f(
        null, emptyList(), null, listOf("dp[0][${n - 1}] = {v:$ans}"), "The most coins for all four balloons is {v:$ans}.",
        "O(n³): n² ranges, each trying up to n last balloons.", answer = true, chips = listOf(StoryChip("coins", "$ans", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Burst balloons", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Current / last burst"), Triple(SimColors.Blue, SwatchStyle.Fill, "Read · ends"), Triple(SimColors.Green, SwatchStyle.Fill, "Filled")),
        ),
    )
}

private fun matrixRotateTabs(): List<TTab> {
    val n = 4
    val headers = (0 until n).map { "c$it" }
    val body = "Two simple passes do the rotation in place."
    fun rows(m: List<List<Int>>, lit: Int?, tone: (Int, Int) -> TTone) = (0 until n).map { r ->
        TRow((0 until n).map { c -> TCell("${m[r][c]}", tone(r, c)) }, "r$r", labelTone = if (r == lit) TTone.Active else null)
    }
    fun fresh() = List(n) { r -> MutableList(n) { r * n + it + 1 } }
    fun transposeReverse(): List<TFrame> {
        val m = fresh()
        val transposed = mutableSetOf<Int>()
        val rotatedRows = mutableSetOf<Int>()
        val frames = mutableListOf(
            TFrame(
                n, rows(m, null) { _, _ -> TTone.Idle }, "Rotate the matrix 90° clockwise without a second matrix.", "Transpose across the diagonal, then reverse every row.",
                formula = listOf("(r, c) → transpose (c, r) → reverse (c, 3 − r)"), headers = headers,
            ),
        )
        for (r in 0 until n) {
            transposed += r * n + r
            for (c in r + 1 until n) {
                val x = m[r][c]
                val y = m[c][r]
                m[r][c] = y
                m[c][r] = x
                transposed += r * n + c
                transposed += c * n + r
                frames += TFrame(
                    n, rows(m, null) { rr, cc -> if ((rr == r && cc == c) || (rr == c && cc == r)) TTone.Active else if (rr * n + cc in transposed) TTone.Outline else TTone.Idle },
                    "Transpose: $x and $y trade places across the diagonal.", "Only pairs above the diagonal are swapped, so each pair moves once.",
                    formula = listOf("swap a[$r][$c] ↔ a[$c][$r]: {$x} ↔ {$y}"), headers = headers,
                )
            }
        }
        for (r in 0 until n) for (c in 0 until n / 2) {
            val x = m[r][c]
            val y = m[r][n - 1 - c]
            m[r][c] = y
            m[r][n - 1 - c] = x
            frames += TFrame(
                n, rows(m, r) { rr, cc -> if (rr == r && (cc == c || cc == n - 1 - c)) TTone.Active else if (rr in rotatedRows) TTone.Green else TTone.Outline },
                if (c == 0) (if (r == 0) "The transpose is done. Row 0 now swaps its ends: $y and $x trade places." else "Row $r now swaps its ends: $y and $x trade places.")
                else "Then the middle pair of row $r: $y and $x.",
                if (r > 0) "$body Row ${r - 1} already reads ${m[r - 1].joinToString(" ")}." else body,
                formula = listOf("(r, c) → transpose (c, r) → reverse (c, 3 − r)"), headers = headers,
            )
            if (c == n / 2 - 1) rotatedRows += r
        }
        frames += TFrame(
            n, rows(m, null) { _, _ -> TTone.Green }, "Every row is reversed: the matrix is rotated 90° clockwise.",
            "Counter-clockwise is the same two steps with the reverse on columns instead.", formula = listOf("rotated · O(n²) time, O(1) extra space"), headers = headers,
        )
        return frames
    }
    fun fourWay(): List<TFrame> {
        val m = fresh()
        val done = mutableSetOf<Int>()
        val frames = mutableListOf(
            TFrame(
                n, rows(m, null) { _, _ -> TTone.Idle }, "Rotate ring by ring: each step moves four cells at once.", "One temporary value per cycle, n²/4 cycles in all.",
                formula = listOf("top ← left ← bottom ← right ← top"), headers = headers,
            ),
        )
        for (layer in 0 until n / 2) {
            val first = layer
            val last = n - 1 - layer
            for (i in first until last) {
                val off = i - first
                val cells = listOf(first to i, last - off to first, last to last - off, i to last)
                val top = m[first][i]
                m[first][i] = m[last - off][first]
                m[last - off][first] = m[last][last - off]
                m[last][last - off] = m[i][last]
                m[i][last] = top
                frames += TFrame(
                    n, rows(m, null) { r, c -> if ((r to c) in cells) TTone.Active else if (r * n + c in done) TTone.Green else TTone.Idle },
                    "Four cells, one on each side of ring $layer, move a quarter turn together.",
                    "The saved top value lands on the right; the other three shift from their neighbour.",
                    formula = listOf("ring $layer: " + cells.joinToString(" → ") { "(${it.first},${it.second})" } + " rotate"), headers = headers,
                )
                cells.forEach { done += it.first * n + it.second }
            }
        }
        frames += TFrame(
            n, rows(m, null) { _, _ -> TTone.Green }, "All rings turned: the same rotation as transpose + reverse.", "Harder to get right, but it touches every cell exactly once.",
            formula = listOf("same result, one pass over each ring"), headers = headers,
        )
        return frames
    }
    return listOf(
        TTab(
            "Transpose + reverse", transposeReverse(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Swapping"), Triple(SimColors.Blue, SwatchStyle.Fill, "Transposed"), Triple(SimColors.Green, SwatchStyle.Fill, "Rotated")),
        ),
        TTab("Four-way swap", fourWay(), listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Moving"), Triple(SimColors.Green, SwatchStyle.Fill, "Rotated"))),
    )
}

private fun xorSplitTabs(): List<TTab> {
    val nums = listOf(4, 1, 2, 1, 3, 2)
    val x = nums.reduce { p, q -> p xor q }
    val low = x and -x
    val bitIndex = Integer.numberOfTrailingZeros(low)
    val bits = 3
    fun xorRow(value: Int?, decide: Boolean): TRow {
        val cells = (bits - 1 downTo 0).map { b ->
            if (value == null) TCell("", TTone.Ghost) else {
                val on = (value shr b) and 1 == 1
                TCell(if (on) "1" else "0", if (decide && b == bitIndex) TTone.Active else if (on) TTone.Outline else TTone.Pending)
            }
        }
        return TRow(cells + TCell(value?.let { "= $it" } ?: "", TTone.Plain), title = "XOR OF ALL · ${nums.joinToString(" ")}", spread = true)
    }
    val ones = nums.filter { it and low != 0 }
    val zeros = nums.filter { it and low == 0 }
    val a = ones.reduce { p, q -> p xor q }
    val b = zeros.reduce { p, q -> p xor q }
    fun groups(results: Boolean): List<TRow> {
        val rs = mutableListOf(
            TRow(
                ones.map { TCell("$it", if (it == a) TTone.Active else TTone.Idle) } + zeros.map { TCell("$it", if (it == b) TTone.Active else TTone.Idle) },
                title = "BIT $bitIndex = 1", spread = true, split = ones.size, splitTitle = "BIT $bitIndex = 0",
            ),
        )
        if (results) rs += TRow(listOf(TCell("→ $a", TTone.Answer), TCell("→ $b", TTone.Answer)), spread = true, split = 1)
        return rs
    }
    val splitBody = "Pairs land in the same group and cancel, leaving one unique value per group. No hash set needed."
    var running = 0
    val frames = mutableListOf(
        TFrame(
            0, listOf(TRow(nums.map { TCell("$it", TTone.Idle) }, title = "NUMS", spread = true)),
            "Find the two values that appear only once, in O(n) time and O(1) space.", "XOR cancels pairs: v ⊕ v = 0 and v ⊕ 0 = v.",
            formula = listOf("every value appears twice except two"),
        ),
    )
    for (i in 1 until nums.size step 2) {
        running = running xor nums[i - 1] xor nums[i]
        frames += TFrame(
            0, listOf(xorRow(running, false)), "XOR in ${nums[i - 1]} and ${nums[i]}: the running value is $running.", "Order doesn't matter; every pair cancels eventually.",
            formula = listOf("… ⊕ ${nums[i - 1]} ⊕ ${nums[i]} = {$running}"),
        )
    }
    frames += TFrame(
        0, listOf(xorRow(x, false)), "The pairs are gone. What is left, $x, is the two singles XORed together.", "A set bit in $x is a bit where the two singles differ.",
        formula = listOf("xor of all = $a ⊕ $b = {$x}"),
    )
    frames += TFrame(
        0, listOf(xorRow(x, true)) + groups(false), "$a and $b differ at bit $bitIndex, so splitting on it puts one in each group.", splitBody,
        formula = listOf("$x & −$x = {$low}", "→ split on bit $bitIndex · XOR each group"),
    )
    frames += TFrame(
        0, listOf(xorRow(x, true)) + groups(true), "XOR each group: {v:$a} and {v:$b} are the singles.", splitBody,
        formula = listOf("${ones.joinToString(" ⊕ ")} = {v:$a} · ${zeros.joinToString(" ⊕ ")} = {v:$b}"),
    )
    frames += TFrame(
        0, listOf(xorRow(x, true)) + groups(true), "Two passes over the array, no extra memory.", "The same lowest-set-bit trick, x & −x, powers Fenwick trees.",
        formula = listOf("two passes · O(n) time · O(1) space"), chips = listOf(StoryChip("singles", "$a, $b", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Single numbers", frames,
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Deciding bit"), Triple(SimColors.Blue, SwatchStyle.Fill, "Set bit"), Triple(SimColors.Answer, SwatchStyle.Fill, "Result")),
        ),
    )
}

private fun prefix2dTabs(): List<TTab> {
    val a = listOf(listOf(3, 0, 1, 4), listOf(5, 6, 3, 2), listOf(1, 2, 0, 1))
    val rows = a.size
    val cols = a[0].size
    val p = List(rows + 1) { MutableList<Int?>(cols + 1) { null } }
    for (i in 0..rows) p[i][0] = 0
    for (j in 0..cols) p[0][j] = 0
    val colHeaders = (0 until cols).map { "c$it" }
    fun matrix(cur: Pair<Int, Int>?, rect: List<Int>?) = (0 until rows).map { r ->
        TRow(
            (0 until cols).map { c ->
                if (rect != null && r in rect[0]..rect[2] && c in rect[1]..rect[3]) TCell("${a[r][c]}", TTone.Outline)
                else TCell("${a[r][c]}", if (cur == (r to c)) TTone.Active else TTone.Idle)
            },
            "r$r",
        )
    }
    fun table(cur: Pair<Int, Int>?, marks: Map<Int, TTone>) = (0..rows).map { i ->
        TRow(
            (0..cols).map { j ->
                val v = p[i][j]
                when {
                    i == 0 || j == 0 -> TCell("0", marks[i * 100 + j] ?: TTone.Pending)
                    v == null -> TCell("", TTone.Ghost)
                    cur == (i to j) -> TCell("$v", TTone.Active)
                    else -> TCell("$v", marks[i * 100 + j] ?: TTone.Green)
                }
            },
            if (i == 0) "" else "r${i - 1}", labelTone = if (cur?.first == i) TTone.Active else null,
        )
    }
    fun f(cur: Pair<Int, Int>?, marks: Map<Int, TTone>, formula: List<String>, headline: String, body: String, rect: List<Int>? = null, chips: List<StoryChip> = emptyList()) =
        TFrame(
            cols + 1, table(cur, marks), headline, body, formula = formula, chips = chips, heading = "P · BORDER OF ZEROS", headers = listOf("") + colHeaders,
            topRows = matrix(cur?.let { it.first - 1 to it.second - 1 }, rect), topColumns = cols, topHeaders = colHeaders, topHeading = "MATRIX",
        )
    val frames = mutableListOf(
        f(
            null, emptyMap(), listOf("P[i][j] = sum of the block above and left of (i, j)"), "Precompute block sums so any rectangle sum is O(1).",
            "A border row and column of zeros means the first row and column need no special case.",
        ),
    )
    for (i in 1..rows) for (j in 1..cols) {
        val v = a[i - 1][j - 1]
        val up = p[i - 1][j]!!
        val left = p[i][j - 1]!!
        val diag = p[i - 1][j - 1]!!
        p[i][j] = v + up + left - diag
        frames += f(
            i to j, mapOf((i - 1) * 100 + j to TTone.Outline, i * 100 + j - 1 to TTone.Outline, (i - 1) * 100 + j - 1 to TTone.Mismatch),
            listOf("{$v} + ↑ {p:$up} + ← {p:$left} − ↖ {w:$diag} = {${p[i][j]}}"),
            if (i == 1 || j == 1) "Along the ${if (i == 1) "top row" else "left column"} the zero border stands in for the missing block."
            else "Up and left both include the top-left block, so it's subtracted once.",
            "After this one pass, any rectangle sum is four lookups.",
        )
    }
    val r1 = 1
    val c1 = 1
    val r2 = 2
    val c2 = 2
    val sum = p[r2 + 1][c2 + 1]!! - p[r1][c2 + 1]!! - p[r2 + 1][c1]!! + p[r1][c1]!!
    frames += f(
        null,
        mapOf((r2 + 1) * 100 + c2 + 1 to TTone.Outline, r1 * 100 + c2 + 1 to TTone.Mismatch, (r2 + 1) * 100 + c1 to TTone.Mismatch, r1 * 100 + c1 to TTone.Outline),
        listOf("sum r$r1..r$r2, c$c1..c$c2 = ${p[r2 + 1][c2 + 1]} − ${p[r1][c2 + 1]} − ${p[r2 + 1][c1]} + ${p[r1][c1]} = {v:$sum}"),
        "Any rectangle is one big block minus two strips plus the corner they both removed: {v:$sum}.",
        "O(rows × cols) to build once, then O(1) per query.", listOf(r1, c1, r2, c2), listOf(StoryChip("rectangle sum", "$sum", StoryTone.Answer)),
    )
    return listOf(
        TTab(
            "Build", frames,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current"), Triple(SimColors.Blue, SwatchStyle.Fill, "Added"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Subtracted"), Triple(SimColors.Green, SwatchStyle.Fill, "Filled"),
            ),
        ),
    )
}

private fun bitTrieTabs(): List<TTab> {
    val values = listOf(3, 10, 5, 25, 2, 8)
    val q = 5
    val bits = 5
    val headers = (0 until bits).map { "b${bits - 1 - it}" }
    fun bit(v: Int, col: Int) = (v shr (bits - 1 - col)) and 1
    var candidates = values
    val took = mutableListOf<Int>()
    val forced = mutableListOf<Boolean>()
    val frames = mutableListOf<TFrame>()
    fun rowsFor(cur: Int?): List<TRow> = listOf(
        TRow((0 until bits).map { TCell("${bit(q, it)}", if (bit(q, it) == 1) TTone.Outline else TTone.Pending) }, "$q"),
        TRow(
            (0 until bits).map { c ->
                val w = 1 - bit(q, c)
                when {
                    c == cur -> TCell("$w", TTone.Active)
                    c < took.size -> TCell("$w", if (forced[c]) TTone.Pending else TTone.Green)
                    else -> TCell("$w", TTone.Ghost)
                }
            },
            "want",
        ),
        TRow((0 until bits).map { c -> if (c < took.size) TCell("${took[c]}", if (forced[c]) TTone.Mismatch else TTone.Green) else TCell("", TTone.Ghost) }, "trie"),
        TRow(
            (0 until bits).map { c ->
                if (c < took.size) { val xb = took[c] xor bit(q, c); TCell("$xb", if (xb == 1) TTone.Answer else TTone.Pending) } else TCell("·", TTone.Ghost)
            },
            "xor",
        ),
    )
    val heading = "QUERY $q · VALUES ${values.joinToString(" ")}"
    frames += TFrame(
        bits, rowsFor(null), "Find the value that XORs with $q to the largest result.", "Every value is stored in a binary trie by its bits, most significant first.",
        formula = listOf("walk from b${bits - 1} down · want the opposite of each query bit"), heading = heading, headers = headers,
    )
    for (c in 0 until bits) {
        val w = 1 - bit(q, c)
        val has = candidates.filter { bit(it, c) == w }
        val got = has.isNotEmpty()
        val t = if (got) w else 1 - w
        took += t
        forced += !got
        if (got) candidates = has
        val partial = (0 until bits).joinToString("") { if (it < took.size) "${took[it] xor bit(q, it)}" else "·" }
        frames += TFrame(
            bits, rowsFor(if (got) null else c),
            if (got) "At bit ${bits - 1 - c} the trie has a $w below this path, so the walk takes it and that XOR bit is 1."
            else "At bit ${bits - 1 - c} the trie has no $w below this path, so the walk takes $t and loses that bit.",
            "Higher bits are worth more than all lower ones combined, so greedy from the top is safe. $q ⊕ ${candidates[0]} = ${q xor candidates[0]}.",
            lit = mapOf(c to TTone.Active),
            formula = if (got) listOf("b${bits - 1 - c}: want {$w} → trie has it → xor bit {v:1}", "· xor = $partial")
            else listOf("b${bits - 1 - c}: want {$w} , trie only has {w:$t}", "→ take $t · xor = $partial"),
            heading = heading, headers = headers,
        )
    }
    val best = candidates[0]
    frames += TFrame(
        bits, rowsFor(null), "The best partner for $q is $best: {v:${q xor best}}.", "O(bits) per query after O(n · bits) to build, instead of trying every value.",
        formula = listOf("$q ⊕ $best = {v:${q xor best}}"), chips = listOf(StoryChip("max xor", "${q xor best}", StoryTone.Answer)), heading = heading, headers = headers,
    )
    return listOf(
        TTab(
            "Max XOR", frames,
            listOf(
                Triple(SimColors.Green, SwatchStyle.Fill, "Got opposite bit"), Triple(SimColors.Active, SwatchStyle.Fill, "Deciding"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Forced"), Triple(SimColors.Answer, SwatchStyle.Fill, "XOR bit"),
            ),
        ),
    )
}

private fun pairedDesignTabs(): List<TTab> {
    fun lru(): List<TFrame> {
        val cap = 3
        val list = mutableListOf<Pair<Int, String>>()
        val mapOrder = mutableListOf<Int>()
        val frames = mutableListOf<TFrame>()
        fun f(written: Int?, hit: Int?, evicted: Pair<Int, String>?, formula: List<String>, headline: String, body: String) {
            val rows = mutableListOf(
                TRow(
                    listOf(TCell("head", TTone.Pending)) + list.map { (k, v) -> TCell("$k:$v", if (k == written) TTone.Active else if (k == hit) TTone.Answer else TTone.Idle) } +
                        TCell("tail", TTone.Pending),
                    title = "LIST · MOST RECENT FIRST", spread = true, joiner = "⇄",
                ),
            )
            if (evicted != null) rows += TRow(List(list.size + 1) { TCell("", TTone.Plain) } + TCell("${evicted.first}:${evicted.second}", TTone.Mismatch), spread = true, joiner = " ")
            val keys = mapOrder + listOfNotNull(evicted?.first)
            rows += TRow(
                if (keys.isEmpty()) listOf(TCell("", TTone.Ghost))
                else keys.map { k -> TCell("$k", if (k == evicted?.first) TTone.Mismatch else if (k == written) TTone.Active else if (k == hit) TTone.Answer else TTone.Idle) },
                title = "MAP · KEY → NODE", spread = true,
            )
            frames += TFrame(0, rows, headline, body, formula = formula, chips = listOf(StoryChip("size", "${list.size} / $cap")))
        }
        f(
            null, null, null, listOf("capacity $cap · map for lookup, list for recency"), "An LRU cache: O(1) get and put, evicting the least recently used key.",
            "A hash map finds a node in O(1); a doubly linked list keeps them in recency order.",
        )
        fun put(k: Int, v: String, hit: Int? = null) {
            var evicted: Pair<Int, String>? = null
            if (list.size == cap) {
                evicted = list.removeAt(list.lastIndex)
                mapOrder.remove(evicted.first)
            }
            list.add(0, k to v)
            mapOrder += k
            if (evicted != null) {
                f(
                    k, hit, evicted, listOf("put($k): size $cap = cap → evict tail.prev {w:${evicted.first}} · delete map[${evicted.first}]"),
                    "${evicted.first} was used least recently, so put($k) removes it from both the list and the map.",
                    "The map finds a node in O(1); the list gives the order." + (hit?.let { " $it is safe because get($it) moved it to the front." } ?: ""),
                )
            } else {
                f(k, null, null, listOf("put($k) → new node at head · map[$k] = node"), "put($k, $v) goes to the front of the list.", "New and recently used keys live at the head; the tail is the next to go.")
            }
        }
        put(1, "a")
        put(2, "b")
        put(3, "c")
        val node = list.removeAt(list.indexOfFirst { it.first == 1 })
        list.add(0, node)
        f(
            null, 1, null, listOf("get(1) → map hit → unlink, move to head → {v:a}"), "get(1) finds its node through the map and moves it to the head.",
            "Unlinking and relinking a doubly linked node is O(1) once the map has handed it over.",
        )
        put(4, "d", hit = 1)
        f(null, null, null, listOf("get(2) → map miss → {w:−1}"), "get(2) misses: it was evicted a step ago.", "A miss costs one map lookup and nothing else.")
        f(
            null, null, null, listOf("get O(1) · put O(1) · space O(capacity)"), "Two structures, each doing the one job it is fast at.",
            "This hash map + linked list pairing is the standard answer; many languages ship it as an ordered map.",
        )
        return frames
    }
    fun lfu(): List<TFrame> {
        val cap = 2
        val freq = mutableMapOf<Int, Int>()
        val buckets = mutableMapOf<Int, MutableList<Int>>()
        val frames = mutableListOf<TFrame>()
        fun f(cur: Int?, evicted: Int?, formula: List<String>, headline: String, body: String) {
            val minF = buckets.filterValues { it.isNotEmpty() }.keys.minOrNull()
            val rows = (1..3).map { fq ->
                val b = buckets[fq].orEmpty()
                TRow(
                    if (b.isEmpty()) listOf(TCell("", TTone.Ghost)) else b.map { k -> TCell("$k", if (k == cur) TTone.Active else TTone.Idle) },
                    title = "FREQ $fq${if (fq == minF) " · MIN" else ""}", spread = true, joiner = if (b.size > 1) "⇄" else null,
                )
            }
            val cells = freq.keys.sorted().map { k -> TCell("$k · f${freq[k]}", if (k == cur) TTone.Active else TTone.Idle) } + listOfNotNull(evicted?.let { TCell("$it", TTone.Mismatch) })
            frames += TFrame(
                0, rows + TRow(cells.ifEmpty { listOf(TCell("", TTone.Ghost)) }, title = "MAP · KEY → FREQ", spread = true), headline, body, formula = formula,
            )
        }
        fun touch(k: Int) {
            val fq = freq.getValue(k)
            buckets.getValue(fq).remove(k)
            freq[k] = fq + 1
            buckets.getOrPut(fq + 1) { mutableListOf() }.add(0, k)
        }
        f(
            null, null, listOf("capacity $cap · evict the least frequent, oldest first"), "An LFU cache evicts the key used the fewest times.",
            "Keys sit in one list per frequency; a map from key to node keeps every move O(1).",
        )
        for (k in listOf(1, 2)) {
            freq[k] = 1
            buckets.getOrPut(1) { mutableListOf() }.add(0, k)
            f(k, null, listOf("put($k) → freq 1 list"), "put($k) starts at frequency 1.", "New keys always join the frequency-1 list.")
        }
        touch(1)
        f(1, null, listOf("get(1) → freq 1 → 2"), "get(1) moves key 1 up to the frequency-2 list.", "Each use moves a key one list up, still O(1).")
        val victim = buckets.getValue(1).removeAt(buckets.getValue(1).lastIndex)
        freq.remove(victim)
        freq[3] = 1
        buckets.getOrPut(1) { mutableListOf() }.add(0, 3)
        f(
            3, victim, listOf("put(3): full → min freq 1 → evict {w:$victim}"), "The cache is full, so the least frequent key, $victim, is evicted for 3.",
            "Within the lowest frequency, the least recently used key goes first.",
        )
        touch(3)
        touch(3)
        f(3, null, listOf("get(3) ×2 → freq 3"), "Two gets lift key 3 to frequency 3; key 1 is now the least frequent.", "The minimum frequency is tracked so eviction never scans.")
        f(null, null, listOf("get · put O(1) with a min-freq pointer"), "LFU pairs a map with a list per frequency.", "More bookkeeping than LRU, but still O(1) per operation.")
        return frames
    }
    return listOf(
        TTab(
            "LRU", lru(),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Just written"), Triple(SimColors.Answer, SwatchStyle.Fill, "Hit, moved to head"), Triple(SimColors.Red, SwatchStyle.Fill, "Evicted")),
        ),
        TTab("LFU", lfu(), listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Touched"), Triple(SimColors.Red, SwatchStyle.Fill, "Evicted"))),
    )
}
