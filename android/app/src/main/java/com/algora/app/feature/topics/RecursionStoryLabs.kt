package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors

// ── Recursion story labs ─────────────────────────────────────────────────────
// Factorial as its call stack, Fibonacci and Permutations as call trees with the stack (or the output)
// under them, and the Sudoku solver as a grid with the candidates for the cell being tried. Yellow is
// the call returning or the digit being tried, blue what waits on the stack, green what has returned.

private enum class RTone { Idle, Stack, Current, Returned }

private class RNode(val label: String, val parent: Int?)

private class RFrame(
    val headline: String,
    val body: String,
    val tones: Map<Int, RTone> = emptyMap(),
    val captions: Map<Int, String> = emptyMap(),
    // Call stack as labels, bottom first; the last is drawn yellow when [topCurrent].
    val stack: List<String> = emptyList(),
    val topCurrent: Boolean = false,
    val output: List<Pair<String, RTone>> = emptyList(),
    val chips: List<StoryChip> = emptyList(),
)

private class RStory(val nodes: List<RNode>, val frames: List<RFrame>)

@Composable
private fun rtColors(tone: RTone): Pair<Color, Color> = when (tone) {
    RTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f) to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    RTone.Stack -> SimColors.Blue to Color.White
    RTone.Current -> SimColors.Active to Color(0xFF1F1A0A)
    RTone.Returned -> SimColors.Green to Color.White
}

// ── Fibonacci ──

private fun fibStory(n: Int): RStory {
    val nodes = mutableListOf<RNode>()
    val arg = mutableListOf<Int>()
    fun build(k: Int, parent: Int?) {
        val id = nodes.size
        nodes += RNode("f($k)", parent)
        arg += k
        if (k >= 2) {
            build(k - 1, id)
            build(k - 2, id)
        }
    }
    build(n, null)
    val total = nodes.size
    val counts = arg.groupingBy { it }.eachCount()
    val tones = mutableMapOf<Int, RTone>()
    val captions = mutableMapOf<Int, String>()
    val stack = mutableListOf<Int>()
    val frames = mutableListOf<RFrame>()
    var calls = 0
    val computedOnce = mutableSetOf<Int>()
    fun chips(id: Int): List<StoryChip> {
        val c = counts[arg[id]] ?: 1
        return listOf(StoryChip("calls", "$calls of $total"), StoryChip("${nodes[id].label} computed", "$c×", if (c > 1) StoryTone.Warn else StoryTone.Idle))
    }
    fun snapshot(headline: String, body: String, top: Boolean, id: Int) {
        frames += RFrame(headline, body, tones.toMap(), captions.toMap(), stack.map { nodes[it].label }, top, chips = chips(id))
    }
    fun visit(id: Int): Int {
        calls++
        val k = arg[id]
        stack += id
        if (k < 2) {
            tones[id] = RTone.Current
            captions[id] = "$k"
            snapshot("{${nodes[id].label}} is a base case, so it returns $k at once.", "Base cases stop the recursion; every other call is built from them.", true, id)
            tones[id] = RTone.Returned
            stack.removeAt(stack.lastIndex)
            return k
        }
        val kids = nodes.indices.filter { nodes[it].parent == id }
        tones[id] = RTone.Stack
        snapshot(
            "${nodes[id].label} calls ${nodes[kids[0]].label} first; ${nodes[kids[1]].label} waits its turn.",
            "Each call waits on the stack until both of its children have returned.", false, id,
        )
        val a = visit(kids[0])
        val b = visit(kids[1])
        val v = a + b
        tones[id] = RTone.Current
        captions[id] = "= $v"
        val body = when {
            (counts[k] ?: 1) > 1 && k !in computedOnce -> "The other branch calls ${nodes[id].label} again, which is why plain recursion is exponential."
            k in computedOnce -> "${nodes[id].label} was already worked out once. Plain recursion recomputes it anyway."
            else -> "Each call adds its two children's answers."
        }
        computedOnce += k
        snapshot("${nodes[kids[0]].label} and ${nodes[kids[1]].label} returned, so {${nodes[id].label}} = $a + $b = $v.", body, true, id)
        tones[id] = RTone.Returned
        captions[id] = "$v"
        stack.removeAt(stack.lastIndex)
        return v
    }
    val answer = visit(0)
    frames += RFrame(
        "f($n) = {v:$answer} after $total calls.", "A memo table would need only ${n + 1} calls, one per distinct argument.",
        tones.toMap(), captions.toMap(), chips = listOf(StoryChip("calls", "$total"), StoryChip("f($n)", "$answer", StoryTone.Answer)),
    )
    return RStory(nodes, frames)
}

// ── Permutations ──

private fun permStory(n: Int): RStory {
    val letters = "abcd".take(n).map { "$it" }
    val nodes = mutableListOf(RNode("·", null))
    val prefixOf = mutableListOf("")
    fun build(id: Int, prefix: String, left: List<String>) {
        left.forEach { l ->
            val c = nodes.size
            nodes += RNode(prefix + l, id)
            prefixOf += prefix + l
            build(c, prefix + l, left - l)
        }
    }
    build(0, "", letters)
    val slots = (1..n).fold(1) { a, b -> a * b }
    val product = (n downTo 1).joinToString(" × ")
    val tones = mutableMapOf(0 to RTone.Stack)
    val out = mutableListOf<String>()
    val frames = mutableListOf<RFrame>()
    fun left(p: String) = letters.filter { it !in p }
    fun output(current: Boolean) =
        out.mapIndexed { i, s -> s to if (current && i == out.lastIndex) RTone.Current else RTone.Returned } +
            List(slots - out.size) { "" to RTone.Idle }
    fun chips(p: String) = listOf(StoryChip("prefix", "\"$p\""), StoryChip("left", left(p).ifEmpty { listOf("–") }.joinToString(" ")))
    frames += RFrame(
        "Start with an empty prefix and all $n letters left.", "Each call fixes one letter, then recurses on the letters that remain.",
        tones.toMap(), output = output(false), chips = chips(""),
    )
    fun visit(id: Int) {
        val p = prefixOf[id]
        if (left(p).isEmpty()) {
            out += p
            tones[id] = RTone.Current
            val parent = nodes[id].parent?.let { prefixOf[it] }.orEmpty()
            frames += RFrame(
                "{$p} is complete. Emit it and return to ${parent.ifEmpty { "the root" }}.",
                "Each level fixes one more letter from the ones left, so $n letters give $slots leaves.",
                tones.toMap(), output = output(true), chips = chips(parent),
            )
            tones[id] = RTone.Returned
            return
        }
        if (id != 0) {
            tones[id] = RTone.Stack
            frames += RFrame(
                "Fix {${p.last()}} next: the prefix is \"$p\", with ${left(p).joinToString(", ")} left.",
                "The call stays on the stack until every ordering that starts with \"$p\" is out.",
                tones.toMap(), output = output(false), chips = chips(p),
            )
        }
        nodes.indices.filter { nodes[it].parent == id }.forEach { visit(it) }
        tones[id] = RTone.Returned
        if (id != 0 && p.length == 1 && n > 2) {
            frames += RFrame(
                "Every ordering starting with {$p} is out, so return to the root.", "The root then tries the next first letter.",
                tones.toMap(), output = output(false), chips = chips(""),
            )
        }
    }
    visit(0)
    frames += RFrame(
        "All {v:$slots} orderings are out: $product.",
        "n! leaves, each reached by one root-to-leaf path, so the work grows as O(n · n!).",
        tones.toMap(), output = output(false), chips = listOf(StoryChip("orderings", "$slots", StoryTone.Answer)),
    )
    return RStory(nodes, frames)
}

/** Leaf-slot layout: leaves take consecutive slots, a parent centres over its children. */
private class TreeLayout(val x: List<Double>, val depth: List<Int>, val leaves: Int, val maxDepth: Int)

private fun treeLayout(nodes: List<RNode>): TreeLayout {
    val kids = nodes.indices.filter { nodes[it].parent != null }.groupBy { nodes[it].parent!! }
    val x = MutableList(nodes.size) { 0.0 }
    val depth = MutableList(nodes.size) { 0 }
    var slot = 0
    fun place(id: Int, d: Int) {
        depth[id] = d
        val cs = kids[id].orEmpty()
        if (cs.isEmpty()) {
            x[id] = slot++.toDouble()
            return
        }
        cs.forEach { place(it, d + 1) }
        x[id] = (x[cs.first()] + x[cs.last()]) / 2
    }
    place(0, 0)
    return TreeLayout(x, depth, slot, depth.maxOrNull() ?: 0)
}

@Composable
private fun RTreeView(nodes: List<RNode>, frame: RFrame, modifier: Modifier = Modifier) {
    val layout = remember(nodes) { treeLayout(nodes) }
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val colors = RTone.entries.associateWith { rtColors(it) }
    val activeInk = StoryTone.Active.ink()
    val doneInk = StoryTone.Done.ink()
    val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val capStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    val rowGap = 60.dp
    Canvas(modifier.fillMaxWidth().height(54.dp + rowGap * layout.maxDepth)) {
        val slotW = size.width / maxOf(layout.leaves, 1)
        fun at(i: Int) = Offset(((layout.x[i] + 0.5) * slotW).toFloat(), 18.dp.toPx() + layout.depth[i] * rowGap.toPx())
        nodes.forEachIndexed { i, node ->
            val p = node.parent ?: return@forEachIndexed
            val tone = frame.tones[i] ?: RTone.Idle
            val color = when (tone) {
                RTone.Returned -> SimColors.Green
                RTone.Idle -> muted.copy(alpha = 0.45f)
                else -> SimColors.Blue
            }
            drawLine(color, at(p), at(i), if (tone == RTone.Idle) 1.2.dp.toPx() else 2.dp.toPx())
        }
        nodes.forEachIndexed { i, node ->
            val c = at(i)
            val tone = frame.tones[i] ?: RTone.Idle
            val (fill, ink) = colors.getValue(tone)
            val text = measurer.measure(node.label, labelStyle.copy(color = ink))
            val w = minOf(maxOf(text.size.width + 14.dp.toPx(), 32.dp.toPx()), slotW - 4.dp.toPx())
            val h = 28.dp.toPx()
            val tl = Offset(c.x - w / 2, c.y - h / 2)
            drawRoundRect(surface, tl, Size(w, h), CornerRadius(7.dp.toPx()))
            drawRoundRect(fill, tl, Size(w, h), CornerRadius(7.dp.toPx()))
            drawText(text, topLeft = Offset(c.x - text.size.width / 2f, c.y - text.size.height / 2f))
            frame.captions[i]?.let { cap ->
                val layoutCap = measurer.measure(cap, capStyle.copy(color = if (tone == RTone.Current) activeInk else doneInk))
                drawText(layoutCap, topLeft = Offset(c.x - layoutCap.size.width / 2f, c.y + h / 2 + 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun RSlotRow(slots: List<Pair<String, RTone>>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        slots.forEach { (text, tone) ->
            if (text.isEmpty()) {
                Box(Modifier.weight(1f).height(40.dp).dashedOutline(muted.copy(alpha = 0.35f), 9.dp))
            } else {
                val (fill, ink) = rtColors(tone)
                Box(Modifier.weight(1f).height(40.dp).background(fill, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Text(text, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RSectionLabel(text: String, note: String? = null, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = muted, modifier = Modifier.weight(1f))
        note?.let { Text(it, fontSize = 13.sp, color = muted, maxLines = 1) }
    }
}

@Composable
private fun StoryCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

private val RecursionLegend = listOf(
    Triple(SimColors.Active, SwatchStyle.Fill, "Returning"),
    Triple(SimColors.Blue, SwatchStyle.Fill, "On stack"),
    Triple(SimColors.Green, SwatchStyle.Fill, "Returned"),
)

/** Fibonacci and Permutations: a size stepper, the call tree, then the stack or the output. */
@Composable
private fun RecursionTreeStory(fib: Boolean) {
    val range = if (fib) 1..5 else 2..3
    var n by rememberSaveable(fib) { mutableIntStateOf(if (fib) 4 else 3) }
    val story = remember(fib, n) { if (fib) fibStory(n) else permStory(n) }
    val playback = rememberPlaybackState(key = fib to n, stepCount = story.frames.size, initialSpeedMs = 900f)
    val frame = story.frames[playback.index.coerceIn(0, story.frames.lastIndex)]
    Column(modifier = Modifier.fillMaxWidth()) {
        StoryCard {
            StoryStepperRow(
                StoryStepper(if (fib) "n" else "letters", n, n > range.first, n < range.last) { n = (n + it).coerceIn(range) },
            )
            RTreeView(story.nodes, frame, Modifier.padding(top = 12.dp))
            if (fib) {
                RSectionLabel("CALL STACK", modifier = Modifier.padding(top = 10.dp))
                RSlotRow(
                    frame.stack.mapIndexed { i, s -> s to if (frame.topCurrent && i == frame.stack.lastIndex) RTone.Current else RTone.Stack } +
                        List(maxOf(n, 1) - frame.stack.size) { "" to RTone.Idle },
                    Modifier.padding(top = 8.dp),
                )
            } else {
                RSectionLabel("OUTPUT", (n downTo 1).joinToString(" × ") + " = ${(1..n).fold(1) { a, b -> a * b }}", Modifier.padding(top = 10.dp))
                RSlotRow(frame.output, Modifier.padding(top = 8.dp))
            }
            StoryLegendRow(
                if (fib) RecursionLegend
                else listOf(
                    Triple(SimColors.Active, SwatchStyle.Fill, "Emitting"),
                    Triple(SimColors.Blue, SwatchStyle.Fill, "On stack"),
                    Triple(SimColors.Green, SwatchStyle.Fill, "Returned"),
                ),
                Modifier.padding(top = 14.dp),
            )
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = story.frames.map { storyPlain(it.headline) })
    }
}

// ── Factorial ──

private class FactRow(val call: String, val expr: String, val value: String, val tone: RTone)
private class FactFrame(val rows: List<FactRow>, val chips: List<StoryChip>, val headline: String, val body: String)

private fun factorialStory(n: Int): List<FactFrame> {
    val frames = mutableListOf<FactFrame>()
    val value = IntArray(n + 1)
    // Rows from fact(n) down to [deepest]. Frames below [returning] have returned; [returning] itself is
    // handing its value up; null means the calls are still going down.
    fun rows(deepest: Int, returning: Int?) = (n downTo deepest).map { k ->
        val expr = if (k == 1) "base case" else "$k × ${value[k - 1]}"
        when {
            returning != null && k < returning -> FactRow("fact($k)", expr, "${value[k]}", RTone.Returned)
            returning != null && k == returning -> FactRow("fact($k)", expr, "${value[k]}", RTone.Current)
            else -> FactRow("fact($k)", if (k == 1) "base case" else "$k × fact(${k - 1})", "…", RTone.Stack)
        }
    }
    for (k in n downTo 2) {
        frames += FactFrame(
            rows(k, null), listOf(StoryChip("depth", "${n - k + 1}")),
            "fact($k) needs {fact(${k - 1})} first, so it waits.",
            "Nothing is multiplied on the way down. Each frame parks with its own k.",
        )
    }
    value[1] = 1
    frames += FactFrame(
        rows(1, 1), listOf(StoryChip("depth", "$n"), StoryChip("fact(1)", "1")),
        "{fact(1)} is the base case: it returns 1 without recursing.",
        "The stack is now $n frames deep, one per pending multiplication.",
    )
    val total = (1..n).fold(1) { a, b -> a * b }
    for (k in 2..n) {
        value[k] = k * value[k - 1]
        val steps = (k + 1..n).map { j -> "$j × ${(1 until j).fold(1) { a, b -> a * b }}" }
        val rest = if (steps.size > 1) steps.dropLast(1).joinToString(", ") + " and " + steps.last() else steps.firstOrNull().orEmpty()
        frames += FactFrame(
            rows(1, k), listOf(StoryChip("depth", "${n - k + 1}"), StoryChip("fact($k)", "${value[k]}")),
            "fact(${k - 1}) returned ${value[k - 1]}, so {fact($k)} returns $k × ${value[k - 1]} = ${value[k]}.",
            if (rest.isEmpty()) "That was the last frame waiting." else "Each frame waits on the one below it. Next, $rest give $total.",
        )
    }
    frames += FactFrame(
        rows(1, n + 1), listOf(StoryChip("fact($n)", "${value[n]}", StoryTone.Answer), StoryChip("frames", "$n")),
        "fact($n) = {v:${value[n]}}.",
        "n frames on the way down, n multiplications on the way up: O(n) time and O(n) stack.",
    )
    return frames
}

@Composable
private fun FactorialStory() {
    var n by rememberSaveable { mutableIntStateOf(5) }
    val frames = remember(n) { factorialStory(n) }
    val playback = rememberPlaybackState(key = n, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val dark = LocalDarkTheme.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        StoryCard {
            StoryStepperRow(StoryStepper("n", n, n > 1, n < 8) { n = (n + it).coerceIn(1, 8) })
            RSectionLabel("CALL STACK", "deepest call last", Modifier.padding(top = 16.dp))
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                frame.rows.forEach { row ->
                    val (base, ink) = when (row.tone) {
                        RTone.Current -> SimColors.Active to StoryTone.Active.ink()
                        RTone.Returned -> SimColors.Green to StoryTone.Done.ink()
                        else -> SimColors.Blue to StoryTone.Path.ink()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(base.copy(alpha = if (dark) 0.2f else 0.14f), RoundedCornerShape(9.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.call, fontFamily = IBMPlexMono, fontSize = 14.sp, color = ink, modifier = Modifier.width(92.dp))
                        Text(row.expr, fontFamily = IBMPlexMono, fontSize = 14.sp, color = onSurface.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                        Text(row.value, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
                    }
                }
            }
            StoryLegendRow(
                listOf(
                    Triple(SimColors.Active, SwatchStyle.Fill, "Returning"),
                    Triple(SimColors.Blue, SwatchStyle.Fill, "Waiting"),
                    Triple(SimColors.Green, SwatchStyle.Fill, "Returned"),
                ),
                Modifier.padding(top = 14.dp),
            )
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

// ── Sudoku ──

private enum class SudTone { Given, Placed, Trying, Dead }
private enum class CandTone { Untried, Conflict, Fits }
private class Cand(val digit: Int, val tone: CandTone, val why: String?)

private class SudFrame(
    val grid: List<List<Int>>,
    val tones: List<List<SudTone>>,
    val cell: Pair<Int, Int>?,
    val cands: List<Cand>,
    val chips: List<StoryChip>,
    val headline: String,
    val body: String,
)

private fun sudokuStory(puzzle: List<List<Int>>): List<SudFrame> {
    val n = puzzle.size
    val box = if (n == 4) 2 else 3
    val g = puzzle.map { it.toMutableList() }
    val empties = (0 until n).flatMap { r -> (0 until n).filter { puzzle[r][it] == 0 }.map { r to it } }
    val frames = mutableListOf<SudFrame>()
    var backtracks = 0
    fun tones(current: Pair<Int, Int>?, dead: Boolean = false) = (0 until n).map { r ->
        (0 until n).map { c ->
            when {
                current == r to c -> if (dead) SudTone.Dead else SudTone.Trying
                puzzle[r][c] != 0 -> SudTone.Given
                else -> SudTone.Placed
            }
        }
    }
    fun clash(r: Int, c: Int, d: Int): String? {
        if ((0 until n).any { it != c && g[r][it] == d }) return "row $r"
        if ((0 until n).any { it != r && g[it][c] == d }) return "col $c"
        val br = r / box * box
        val bc = c / box * box
        for (i in br until br + box) for (j in bc until bc + box) if ((i != r || j != c) && g[i][j] == d) return "box"
        return null
    }
    fun depth() = empties.count { (r, c) -> g[r][c] != 0 }
    fun name(r: Int, c: Int) = "r${r}c$c"
    fun chips() = listOf(StoryChip("depth", "${depth()}"), StoryChip("backtracks", "$backtracks"))
    fun snapshot() = g.map { it.toList() }
    frames += SudFrame(
        snapshot(), tones(null), null, emptyList(), chips(),
        "Fill the empty cells left to right, top to bottom, trying 1 to $n in each.",
        "A digit fits if its row, column and box do not already have it.",
    )
    fun solve(k: Int): Boolean {
        if (k == empties.size) return true
        val (r, c) = empties[k]
        val cands = (1..n).map { Cand(it, CandTone.Untried, null) }.toMutableList()
        val failed = mutableListOf<Int>()
        for (d in 1..n) {
            val why = clash(r, c, d)
            if (why != null) {
                cands[d - 1] = Cand(d, CandTone.Conflict, why)
                continue
            }
            g[r][c] = d
            cands[d - 1] = Cand(d, CandTone.Fits, "fits")
            val taken = cands.filter { it.tone == CandTone.Conflict }
            val lead = when {
                failed.isNotEmpty() -> "${failed.joinToString(" and ")} led to a dead end, so "
                taken.size == 1 -> {
                    val where = taken[0].why.orEmpty()
                    "${taken[0].digit} is already in ${if (where == "box") "the box" else if (where.startsWith("row")) "row $r" else "column $c"}, so "
                }
                taken.size > 1 -> "${taken.joinToString(" and ") { "${it.digit}" }} are taken, so "
                else -> ""
            }
            frames += SudFrame(
                snapshot(), tones(r to c), r to c, cands.toList(), chips(),
                "$lead{${name(r, c)}} tries $d, which fits.", "If a later cell runs out of digits, it backtracks here.",
            )
            if (solve(k + 1)) return true
            g[r][c] = 0
            failed += d
            cands[d - 1] = Cand(d, CandTone.Conflict, "dead end")
        }
        backtracks++
        frames += SudFrame(
            snapshot(), tones(r to c, dead = true), r to c, cands.toList(), chips(),
            "No digit fits at {w:${name(r, c)}}, so the solver backtracks.",
            "The most recent guess is undone and moves on to its next digit.",
        )
        return false
    }
    solve(0)
    frames += SudFrame(
        snapshot(), tones(null), null, emptyList(),
        listOf(StoryChip("placed", "${empties.size}"), StoryChip("backtracks", "$backtracks", StoryTone.Answer)),
        "Solved: {v:${empties.size}} cells filled after $backtracks backtrack${if (backtracks == 1) "" else "s"}.",
        "Backtracking is depth-first search over choices: guess, check the constraints, and undo only the most recent guess when stuck.",
    )
    return frames
}

private val Sudoku4 = listOf(listOf(1, 0, 0, 4), listOf(0, 4, 0, 0), listOf(0, 0, 0, 0), listOf(4, 0, 2, 3))
private val Sudoku9: List<List<Int>> = run {
    val s = listOf("534678912", "672195348", "198342567", "859761423", "426853791", "713924856", "961537284", "287419635", "345286179")
    val g = s.map { row -> row.map { it.digitToInt() }.toMutableList() }
    listOf(0 to 2, 1 to 4, 2 to 6, 3 to 3, 4 to 4, 5 to 5, 6 to 2, 7 to 7, 8 to 0).forEach { (r, c) -> g[r][c] = 0 }
    g
}

@Composable
private fun SudokuStory() {
    val tabs = remember { listOf("4 × 4" to sudokuStory(Sudoku4), "9 × 9" to sudokuStory(Sudoku9)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val frames = tabs[tab].second
    val playback = rememberPlaybackState(key = tab, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val n = frame.grid.size
    val gap = if (n == 4) 8.dp else 3.dp
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val warnInk = StoryTone.Warn.ink()
    Column(modifier = Modifier.fillMaxWidth()) {
        StoryCard {
            LabSegments(tabs.map { it.first }, tab) { tab = it }
            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until n) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until n) {
                            val v = frame.grid[r][c]
                            val tone = frame.tones[r][c]
                            val empty = v == 0 && tone == SudTone.Placed
                            val (fill, ink) = when {
                                empty -> muted.copy(alpha = 0.12f) to Color.Transparent
                                tone == SudTone.Given -> muted.copy(alpha = 0.2f) to onSurface
                                tone == SudTone.Placed -> SimColors.Blue to Color.White
                                tone == SudTone.Trying -> SimColors.Active to Color(0xFF1F1A0A)
                                else -> SimColors.Red.copy(alpha = 0.2f) to warnInk
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (n == 4) 52.dp else 30.dp)
                                    .background(fill, RoundedCornerShape(if (n == 4) 9.dp else 5.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (v != 0) Text("$v", fontSize = if (n == 4) 20.sp else 14.sp, fontWeight = FontWeight.Bold, color = ink)
                            }
                            if (n == 9 && c % 3 == 2 && c < 8) Spacer(Modifier.width(3.dp))
                        }
                    }
                    if (n == 9 && r % 3 == 2 && r < 8) Spacer(Modifier.height(3.dp))
                }
            }
            frame.cell?.let { (r, c) ->
                RSectionLabel("CANDIDATES FOR r${r}c$c", "row · column · box", Modifier.padding(top = 14.dp))
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (n == 4) 6.dp else 3.dp)) {
                    frame.cands.forEach { cand ->
                        val shape = RoundedCornerShape(8.dp)
                        val (fill, ink) = when (cand.tone) {
                            CandTone.Fits -> SimColors.Active to Color(0xFF1F1A0A)
                            CandTone.Conflict -> SimColors.Red.copy(alpha = 0.16f) to warnInk
                            CandTone.Untried -> muted.copy(alpha = 0.2f) to onSurface
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(fill, shape)
                                .then(if (cand.tone == CandTone.Conflict) Modifier.border(1.2.dp, SimColors.Red.copy(alpha = 0.8f), shape) else Modifier),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${cand.digit}", fontFamily = IBMPlexMono, fontSize = if (n == 4) 16.sp else 13.sp, fontWeight = FontWeight.Bold, color = ink)
                            if (n == 4 && cand.why != null) {
                                Text(cand.why, fontFamily = IBMPlexMono, fontSize = 11.sp, color = ink, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
                            }
                        }
                    }
                }
            }
            StoryLegendRow(
                listOf(
                    Triple(SimColors.Active, SwatchStyle.Fill, "Trying"),
                    Triple(SimColors.Blue, SwatchStyle.Fill, "Placed"),
                    Triple(SimColors.Red, SwatchStyle.Fill, "Conflict"),
                ),
                Modifier.padding(top = 14.dp),
            )
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

// ── Entry ──

internal val recursionStoryTopicIds = setOf("factorial", "fibonacci_recursive", "permutation_generation", "sudoku_solver")

internal fun recursionStoryFrameCount(topicId: String): Int = when (topicId) {
    "factorial" -> (1..8).sumOf { factorialStory(it).size }
    "fibonacci_recursive" -> (1..5).sumOf { fibStory(it).frames.size }
    "permutation_generation" -> (2..3).sumOf { permStory(it).frames.size }
    else -> sudokuStory(Sudoku4).size + sudokuStory(Sudoku9).size
}

@Composable
internal fun RecursionStorySection(topicId: String) {
    when (topicId) {
        "factorial" -> FactorialStory()
        "fibonacci_recursive" -> RecursionTreeStory(fib = true)
        "permutation_generation" -> RecursionTreeStory(fib = false)
        else -> SudokuStory()
    }
}
