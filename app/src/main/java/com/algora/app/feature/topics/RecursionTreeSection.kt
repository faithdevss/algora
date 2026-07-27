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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors

// ── Recursion-tree / call-stack player ───────────────────────────────────────
// Runs a real recursive function under a tracer, capturing a frame at every call and every return.
// PlaybackTransport steps through those frames; nodes light up as active → waiting → returned.

private sealed interface RecState {
    data object Active : RecState        // top of the call stack
    data object Waiting : RecState       // on the stack, waiting for a child to return
    data object Returned : RecState      // popped
}

private class RecNode(val id: Int, val parent: Int?, val label: String, val depth: Int)

private class RecFrame(val stateById: Map<Int, RecState>, val stack: List<String>, val status: String)

private class RecTrace(val nodes: List<RecNode>, val frames: List<RecFrame>)

// Instruments a recursion: call() on entry (pushes), ret() on exit (pops), snapshotting each time.
private class Tracer {
    val nodes = mutableListOf<RecNode>()
    val frames = mutableListOf<RecFrame>()
    private val stack = ArrayDeque<Int>()
    private val returned = mutableSetOf<Int>()

    fun call(parent: Int?, label: String): Int {
        val depth = parent?.let { nodes[it].depth + 1 } ?: 0
        val id = nodes.size
        nodes.add(RecNode(id, parent, label, depth))
        stack.addLast(id)
        frame("call $label")
        return id
    }

    // result is any short string ("= 120", "✓ solved", "move A→C") — recursions needn't be numeric.
    fun ret(id: Int, result: String) {
        returned.add(id)
        stack.removeLast()
        frame("return ${nodes[id].label} $result")
    }

    private fun frame(status: String) {
        val top = stack.lastOrNull()
        val map = nodes.associate { n ->
            n.id to when {
                n.id in returned -> RecState.Returned
                n.id == top -> RecState.Active
                stack.contains(n.id) -> RecState.Waiting
                else -> RecState.Waiting
            }
        }
        frames.add(RecFrame(map, stack.map { nodes[it].label }, status))
    }
}

private class RecursionConfig(
    val nRange: ClosedFloatingPointRange<Float>,
    val defaultN: Int,
    val nLabel: String,
    val build: (Int) -> RecTrace,
)

private fun factorialTrace(n: Int): RecTrace {
    val t = Tracer()
    fun fact(parent: Int?, k: Int): Long {
        val id = t.call(parent, "fact($k)")
        val result = if (k <= 1) 1L else k * fact(id, k - 1)
        t.ret(id, "= $result")
        return result
    }
    fact(null, n)
    return RecTrace(t.nodes, t.frames)
}

private fun fibonacciTrace(n: Int): RecTrace {
    val t = Tracer()
    fun fib(parent: Int?, k: Int): Long {
        val id = t.call(parent, "fib($k)")
        val result = if (k < 2) k.toLong() else fib(id, k - 1) + fib(id, k - 2)
        t.ret(id, "= $result")
        return result
    }
    fib(null, n)
    return RecTrace(t.nodes, t.frames)
}

// Tower of Hanoi: hanoi(n) makes two hanoi(n-1) calls — a small binary tree (2^n - 1 nodes).
private fun hanoiTrace(disks: Int): RecTrace {
    val t = Tracer()
    fun hanoi(parent: Int?, n: Int, from: Char, to: Char, via: Char) {
        val id = t.call(parent, "h($n)")
        if (n == 1) {
            t.ret(id, "move $from→$to")
        } else {
            hanoi(id, n - 1, from, via, to)
            hanoi(id, n - 1, via, to, from)
            t.ret(id, "move $from→$to + done")
        }
    }
    hanoi(null, disks, 'A', 'C', 'B')
    return RecTrace(t.nodes, t.frames)
}

// N-Queens: place one queen per row, backtracking. Capped small so the search tree stays legible.
private fun nQueensTrace(boardN: Int): RecTrace {
    val t = Tracer()
    val cols = IntArray(boardN) { -1 }
    fun safe(row: Int, col: Int): Boolean {
        for (r in 0 until row) {
            val c = cols[r]
            if (c == col || kotlin.math.abs(c - col) == row - r) return false
        }
        return true
    }
    fun solve(parent: Int?, row: Int): Boolean {
        val id = t.call(parent, "r$row")
        if (row == boardN) { t.ret(id, "✓ solved"); return true }
        for (col in 0 until boardN) {
            if (safe(row, col)) {
                cols[row] = col
                if (solve(id, row + 1)) { t.ret(id, "✓ col $col"); cols[row] = -1; return true }
                cols[row] = -1
            }
        }
        t.ret(id, "✗ backtrack")
        return false
    }
    solve(null, 0)
    return RecTrace(t.nodes, t.frames)
}

/**
 * Permutations of the first [n] letters. Every leaf is a complete arrangement and no branch is ever
 * abandoned, which makes this the backtracking tree without any pruning in it — the contrast the
 * sudoku trace needs.
 */
private fun permutationTrace(n: Int): RecTrace {
    val t = Tracer()
    val letters = "abcd".take(n).toList()
    val used = BooleanArray(n)
    val current = mutableListOf<Char>()
    var leaves = 0
    fun permute(parent: Int?): Int {
        val label = if (current.isEmpty()) "·" else current.joinToString("")
        val id = t.call(parent, label)
        if (current.size == n) {
            leaves++
            t.ret(id, "✓ ${current.joinToString("")}")
            return id
        }
        for (i in 0 until n) {
            if (used[i]) continue
            used[i] = true
            current.add(letters[i])
            permute(id)
            current.removeAt(current.lastIndex)
            used[i] = false
        }
        t.ret(id, "$leaves so far")
        return id
    }
    permute(null)
    return RecTrace(t.nodes, t.frames)
}

/**
 * A 4x4 Latin-square sudoku, solved cell by cell. Capped hard: the full 9x9 tree is far too large to
 * render, and the lesson — a candidate that violates a constraint is abandoned before its subtree is
 * ever built — is identical at this size.
 */
private fun sudokuTrace(size: Int): RecTrace {
    val t = Tracer()
    val n = size
    // A few givens, so the search has real constraints to run into.
    val grid = Array(n) { IntArray(n) }
    if (n == 4) {
        grid[0][0] = 1; grid[1][2] = 1; grid[3][3] = 4
    }
    val boxH = if (n == 4) 2 else 1
    val boxW = if (n == 4) 2 else 1

    fun legal(r: Int, c: Int, v: Int): Boolean {
        for (i in 0 until n) if (grid[r][i] == v || grid[i][c] == v) return false
        val r0 = r / boxH * boxH
        val c0 = c / boxW * boxW
        for (i in r0 until r0 + boxH) for (j in c0 until c0 + boxW) if (grid[i][j] == v) return false
        return true
    }

    var rejected = 0
    fun solve(parent: Int?, pos: Int): Boolean {
        if (pos == n * n) {
            val id = t.call(parent, "done")
            t.ret(id, "✓ solved")
            return true
        }
        val r = pos / n
        val c = pos % n
        if (grid[r][c] != 0) return solve(parent, pos + 1)
        val id = t.call(parent, "r${r}c$c")
        for (v in 1..n) {
            if (!legal(r, c, v)) { rejected++; continue }
            grid[r][c] = v
            if (solve(id, pos + 1)) { t.ret(id, "✓ = $v"); return true }
            grid[r][c] = 0
        }
        t.ret(id, "✗ dead end")
        return false
    }
    solve(null, 0)
    return RecTrace(t.nodes, t.frames)
}

private val recursionConfigs = mapOf(
    "factorial" to RecursionConfig(1f..8f, 5, "n") { factorialTrace(it) },
    "permutation_generation" to RecursionConfig(2f..4f, 3, "letters") { permutationTrace(it) },
    "sudoku_solver" to RecursionConfig(4f..4f, 4, "grid size") { sudokuTrace(it) },
    "fibonacci_recursive" to RecursionConfig(1f..6f, 4, "n") { fibonacciTrace(it) },
    "tower_of_hanoi" to RecursionConfig(1f..4f, 3, "disks") { hanoiTrace(it) },
    "n_queens" to RecursionConfig(4f..6f, 4, "board size") { nQueensTrace(it) },
)

private fun recursionConfigFor(topicId: String): RecursionConfig =
    recursionConfigs[topicId] ?: recursionConfigs.getValue("factorial")

private val ActiveColor = Color(0xFFFACC15)
private val WaitingColor = Color(0xFF3B82F6)
private val ReturnedColor = SimColors.Green
private val PendingColor = Color(0xFF7C3AED)

@Composable
fun RecursionTreeSection(topicId: String) {
    val config = remember(topicId) { recursionConfigFor(topicId) }
    var n by remember(config) { mutableStateOf(config.defaultN.toFloat()) }
    val nInt = n.toInt()
    val trace = remember(config, nInt) { config.build(nInt) }
    val playback = rememberPlaybackState(key = trace, stepCount = trace.frames.size)
    val frame = trace.frames[playback.index.coerceIn(0, trace.frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${config.nLabel} = $nInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = n,
                onValueChange = { n = it },
                valueRange = config.nRange,
                steps = (config.nRange.endInclusive - config.nRange.start).toInt() - 1,
            )

            RecursionCanvas(nodes = trace.nodes, stateById = frame.stateById)

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Call stack: " + if (frame.stack.isEmpty()) "(empty)" else frame.stack.joinToString("  ▸  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LegendSwatch(ActiveColor, "Active")
                LegendSwatch(WaitingColor, "Waiting")
                LegendSwatch(ReturnedColor, "Returned")
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun RecursionCanvas(nodes: List<RecNode>, stateById: Map<Int, RecState>) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

    // Layout: depth → row; leaves take sequential x slots, internal nodes center over their children.
    val maxDepth = (nodes.maxOfOrNull { it.depth } ?: 0)
    val childrenOf = nodes.groupBy { it.parent }
    val xById = HashMap<Int, Float>()
    var leafCursor = 0f
    fun assignX(id: Int) {
        val kids = childrenOf[id].orEmpty()
        if (kids.isEmpty()) {
            xById[id] = leafCursor
            leafCursor += 1f
        } else {
            kids.forEach { assignX(it.id) }
            xById[id] = kids.map { xById.getValue(it.id) }.average().toFloat()
        }
    }
    nodes.firstOrNull { it.parent == null }?.let { assignX(it.id) }
    val leafCount = leafCursor.coerceAtLeast(1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(230.dp)) {
            val padX = 24f
            val padY = 22f
            fun px(x: Float): Float = padX + (x + 0.5f) / leafCount * (size.width - 2 * padX)
            fun py(depth: Int): Float =
                if (maxDepth == 0) size.height / 2f else padY + depth.toFloat() / maxDepth * (size.height - 2 * padY)

            // edges
            nodes.forEach { node ->
                node.parent?.let { p ->
                    drawLine(
                        Color(0xFFCBD0DA),
                        Offset(px(xById.getValue(p)), py(nodes[p].depth)),
                        Offset(px(xById.getValue(node.id)), py(node.depth)),
                        strokeWidth = 2f,
                    )
                }
            }

            val radius = if (nodes.size > 14) 13f else 17f
            nodes.forEach { node ->
                val center = Offset(px(xById.getValue(node.id)), py(node.depth))
                val color = when (stateById[node.id]) {
                    RecState.Active -> ActiveColor
                    RecState.Waiting -> WaitingColor
                    RecState.Returned -> ReturnedColor
                    else -> PendingColor
                }
                drawCircle(color, radius = radius, center = center)
                val layout = textMeasurer.measure(node.label.removePrefix("fact").removePrefix("fib"), labelStyle)
                drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
            }
        }
    }
}
