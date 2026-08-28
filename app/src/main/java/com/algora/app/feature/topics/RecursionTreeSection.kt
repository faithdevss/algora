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

// Karatsuba: three recursive products instead of four. The base case is a one-digit multiply, so the
// leaf count IS the multiplication count — no separate instrumentation needed, just count leaves.
private fun karatsubaTrace(digits: Int): RecTrace {
    val t = Tracer()
    val operands = mapOf(2 to (47L to 82L), 3 to (471L to 823L), 4 to (1234L to 5678L))
    val (x, y) = operands[digits] ?: operands.getValue(4)
    var oneDigitMults = 0

    fun pow10(k: Int): Long {
        var r = 1L
        repeat(k) { r *= 10 }
        return r
    }

    fun kara(parent: Int?, a: Long, b: Long, width: Int): Long {
        val id = t.call(parent, "$a×$b")
        if (a < 10 || b < 10) {
            oneDigitMults++
            val r = a * b
            t.ret(id, "= $r")
            return r
        }
        val half = width / 2
        val p = pow10(half)
        val a1 = a / p
        val a0 = a % p
        val b1 = b / p
        val b0 = b % p
        val z2 = kara(id, a1, b1, width - half)
        val z0 = kara(id, a0, b0, half)
        // The middle term is (a1+a0)(b1+b0) − z2 − z0: one product where the schoolbook split needs two.
        val z1 = kara(id, a1 + a0, b1 + b0, maxOf(width - half, half) + 1) - z2 - z0
        val r = z2 * p * p + z1 * p + z0
        val schoolbook = width * width
        t.ret(
            id,
            if (parent == null) {
                "= $r · $oneDigitMults one-digit mults vs $schoolbook schoolbook" +
                    if (oneDigitMults >= schoolbook) " — Karatsuba loses at this size" else ""
            } else {
                "= $r"
            },
        )
        return r
    }
    kara(null, x, y, digits)
    return RecTrace(t.nodes, t.frames)
}

// Strassen: seven block products instead of eight. Recursion stops at 2×2 blocks multiplied the plain
// way, which keeps the tree eight nodes wide instead of fifty-seven.
private fun strassenTrace(size: Int): RecTrace {
    val t = Tracer()
    val n = if (size >= 4) 4 else 2
    val a = Array(n) { i -> IntArray(n) { j -> (i * n + j) % 7 + 1 } }
    val b = Array(n) { i -> IntArray(n) { j -> (i + 2 * j) % 5 + 1 } }

    fun addM(x: Array<IntArray>, y: Array<IntArray>, sign: Int): Array<IntArray> =
        Array(x.size) { i -> IntArray(x.size) { j -> x[i][j] + sign * y[i][j] } }

    fun naive(x: Array<IntArray>, y: Array<IntArray>, count: IntArray): Array<IntArray> {
        val s = x.size
        val c = Array(s) { IntArray(s) }
        for (i in 0 until s) for (j in 0 until s) for (k in 0 until s) {
            c[i][j] += x[i][k] * y[k][j]
            count[0]++
        }
        return c
    }

    fun quad(m: Array<IntArray>, r: Int, c: Int): Array<IntArray> {
        val h = m.size / 2
        return Array(h) { i -> IntArray(h) { j -> m[r + i][c + j] } }
    }

    val strassenMults = IntArray(1)
    val blockAdds = IntArray(1)

    fun strassen(parent: Int?, x: Array<IntArray>, y: Array<IntArray>, label: String): Array<IntArray> {
        val s = x.size
        val id = t.call(parent, label)
        if (s <= 2) {
            val c = naive(x, y, strassenMults)
            t.ret(id, "= 2×2 block, 8 scalar mults")
            return c
        }
        val h = s / 2
        val a11 = quad(x, 0, 0); val a12 = quad(x, 0, h); val a21 = quad(x, h, 0); val a22 = quad(x, h, h)
        val b11 = quad(y, 0, 0); val b12 = quad(y, 0, h); val b21 = quad(y, h, 0); val b22 = quad(y, h, h)
        blockAdds[0] += 10
        val m1 = strassen(id, addM(a11, a22, 1), addM(b11, b22, 1), "M1")
        val m2 = strassen(id, addM(a21, a22, 1), b11, "M2")
        val m3 = strassen(id, a11, addM(b12, b22, -1), "M3")
        val m4 = strassen(id, a22, addM(b21, b11, -1), "M4")
        val m5 = strassen(id, addM(a11, a12, 1), b22, "M5")
        val m6 = strassen(id, addM(a21, a11, -1), addM(b11, b12, 1), "M6")
        val m7 = strassen(id, addM(a12, a22, -1), addM(b21, b22, 1), "M7")
        blockAdds[0] += 8
        val c11 = addM(addM(addM(m1, m4, 1), m5, -1), m7, 1)
        val c12 = addM(m3, m5, 1)
        val c21 = addM(m2, m4, 1)
        val c22 = addM(addM(addM(m1, m3, 1), m2, -1), m6, 1)
        val c = Array(s) { IntArray(s) }
        for (i in 0 until h) for (j in 0 until h) {
            c[i][j] = c11[i][j]
            c[i][j + h] = c12[i][j]
            c[i + h][j] = c21[i][j]
            c[i + h][j + h] = c22[i][j]
        }
        val naiveCount = IntArray(1)
        val reference = naive(x, y, naiveCount)
        val matches = (0 until s).all { i -> (0 until s).all { j -> c[i][j] == reference[i][j] } }
        t.ret(
            id,
            "= ${if (matches) "matches" else "DIFFERS from"} the plain product · ${strassenMults[0]} scalar mults " +
                "vs ${naiveCount[0]}, paid for with ${blockAdds[0]} block additions",
        )
        return c
    }
    strassen(null, a, b, "${n}×$n")
    return RecTrace(t.nodes, t.frames)
}

// Exponentiation by squaring. The tree is the complexity argument: one child per level, and the
// level count is ⌊log₂ n⌋ + 1 — against the n-deep chain a naive loop would draw.
private fun fastPowerTrace(exponent: Int): RecTrace {
    val t = Tracer()
    val base = 3L

    fun power(parent: Int?, e: Int): Long {
        val id = t.call(parent, "3^$e")
        val result = if (e == 0) {
            1L
        } else {
            // Computed once and squared. Two recursive calls on e/2 would be the same answer
            // in O(n) time, which is the mistake this shape exists to rule out.
            val half = power(id, e / 2)
            val squared = half * half
            if (e % 2 == 0) squared else squared * base
        }
        t.ret(id, "= $result")
        return result
    }

    power(null, exponent)
    return RecTrace(t.nodes, t.frames)
}

// The same recursion with a reduction after every multiply, so nothing ever exceeds mod².
private fun modularPowerTrace(exponent: Int): RecTrace {
    val t = Tracer()
    val base = 3L
    val mod = 17L

    fun power(parent: Int?, e: Int): Long {
        val id = t.call(parent, "3^$e mod 17")
        val result = if (e == 0) {
            1L
        } else {
            val half = power(id, e / 2)
            val squared = half * half % mod
            if (e % 2 == 0) squared else squared * base % mod
        }
        t.ret(id, "= $result")
        return result
    }

    power(null, exponent)
    return RecTrace(t.nodes, t.frames)
}

// ── Interview-prep pattern traces ────────────────────────────────────────────

// Combination sum: choose / recurse / un-choose, with the two prunes an interviewer asks about —
// `start` (no earlier element may be reused, which is what stops duplicate combinations) and the
// sorted break that kills a whole subtree the moment a candidate exceeds what is left.
private fun combinationSumTrace(itemCount: Int): RecTrace {
    val t = Tracer()
    val nums = listOf(2, 3, 5, 6, 7).take(itemCount)
    val target = 8
    val path = mutableListOf<Int>()
    var solutions = 0

    fun walk(parent: Int?, start: Int, remaining: Int) {
        val label = if (path.isEmpty()) "[] need $remaining" else "[${path.joinToString(",")}] need $remaining"
        val id = t.call(parent, label)
        if (remaining == 0) {
            solutions++
            t.ret(id, "✓ solution #$solutions")
            return
        }
        var explored = 0
        for (i in start until nums.size) {
            // Sorted input, so every later candidate is worse — the whole rest of the loop dies here.
            if (nums[i] > remaining) break
            explored++
            path.add(nums[i])
            walk(id, i, remaining - nums[i])
            path.removeAt(path.lastIndex)
        }
        t.ret(id, if (explored == 0) "dead end — every candidate overshoots" else "tried $explored candidate(s)")
    }

    walk(null, 0, target)
    return RecTrace(t.nodes, t.frames)
}

// The power set as a decision tree: every node is itself a valid subset, and `start` is what keeps
// {2,3} and {3,2} from both appearing.
private fun subsetsTrace(itemCount: Int): RecTrace {
    val t = Tracer()
    val items = listOf("a", "b", "c", "d").take(itemCount)
    val path = mutableListOf<String>()
    var emitted = 0

    fun walk(parent: Int?, start: Int) {
        val index = ++emitted
        val label = if (path.isEmpty()) "{}" else "{${path.joinToString(",")}}"
        val id = t.call(parent, label)
        for (i in start until items.size) {
            path.add(items[i])
            walk(id, i + 1)
            path.removeAt(path.lastIndex)
        }
        t.ret(id, "subset $index of ${1 shl items.size}")
    }

    walk(null, 0)
    return RecTrace(t.nodes, t.frames)
}

// ── Interview-prep pattern guides ────────────────────────────────────────────

// The naive fib tree with a cache in front of it: the second call for any k returns immediately, so
// the whole right-hand subtree that would have been rebuilt never appears.
private fun memoFibTrace(n: Int): RecTrace {
    val t = Tracer()
    val memo = HashMap<Int, Long>()

    fun fib(parent: Int?, k: Int): Long {
        val id = t.call(parent, "fib($k)")
        val cached = memo[k]
        if (cached != null) {
            t.ret(id, "= $cached (memo hit — the whole subtree below this call is skipped)")
            return cached
        }
        val value = if (k < 2) k.toLong() else fib(id, k - 1) + fib(id, k - 2)
        memo[k] = value
        t.ret(id, "= $value (stored: k=$k will never be recomputed)")
        return value
    }

    fib(null, n)
    return RecTrace(t.nodes, t.frames)
}

// Subset sums of each half enumerated separately: 2^(n/2) + 2^(n/2) leaves instead of 2^n.
private val meetItems = listOf(3, 34, 4, 12, 5, 2)

private fun meetInMiddleTrace(n: Int): RecTrace {
    val t = Tracer()
    val items = meetItems.take(n)
    val half = items.size / 2
    val target = 15
    val root = t.call(null, "target $target")

    fun enumerate(parent: Int, label: String, part: List<Int>, index: Int, sum: Int, sums: MutableList<Int>) {
        if (index == part.size) {
            val leaf = t.call(parent, "sum $sum")
            sums += sum
            t.ret(leaf, "= $sum")
            return
        }
        val id = t.call(parent, "$label${part[index]}?")
        enumerate(id, label, part, index + 1, sum, sums)
        enumerate(id, label, part, index + 1, sum + part[index], sums)
        t.ret(id, "both branches enumerated")
    }

    val leftSums = mutableListOf<Int>()
    val leftId = t.call(root, "left ${items.take(half).joinToString(",")}")
    enumerate(leftId, "L", items.take(half), 0, 0, leftSums)
    t.ret(leftId, "= ${leftSums.size} sums")

    val rightSums = mutableListOf<Int>()
    val rightId = t.call(root, "right ${items.drop(half).joinToString(",")}")
    enumerate(rightId, "R", items.drop(half), 0, 0, rightSums)
    t.ret(rightId, "= ${rightSums.size} sums")

    val hit = leftSums.any { l -> rightSums.any { r -> l + r == target } }
    t.ret(
        root,
        "${leftSums.size} + ${rightSums.size} = ${leftSums.size + rightSums.size} sums enumerated instead of " +
            "2^${items.size} = ${1 shl items.size}. Sort one side and binary-search it for target − s: " +
            if (hit) "$target is reachable." else "$target is not reachable.",
    )
    return RecTrace(t.nodes, t.frames)
}

private val recursionConfigs = mapOf(
    "memo_recursion_pattern" to RecursionConfig(3f..7f, 6, "n") { memoFibTrace(it) },
    "meet_in_middle_pattern" to RecursionConfig(4f..6f, 6, "items") { meetInMiddleTrace(it) },
    "fast_power" to RecursionConfig(1f..20f, 13, "exponent") { fastPowerTrace(it) },
    "modular_exponentiation" to RecursionConfig(1f..20f, 13, "exponent") { modularPowerTrace(it) },
    "factorial" to RecursionConfig(1f..8f, 5, "n") { factorialTrace(it) },
    "karatsubas_algorithm" to RecursionConfig(2f..4f, 4, "digits per operand") { karatsubaTrace(it) },
    "strassens_algorithm" to RecursionConfig(4f..4f, 4, "matrix size") { strassenTrace(it) },
    "permutation_generation" to RecursionConfig(2f..4f, 3, "letters") { permutationTrace(it) },
    "sudoku_solver" to RecursionConfig(4f..4f, 4, "grid size") { sudokuTrace(it) },
    "fibonacci_recursive" to RecursionConfig(1f..6f, 4, "n") { fibonacciTrace(it) },
    "tower_of_hanoi" to RecursionConfig(1f..4f, 3, "disks") { hanoiTrace(it) },
    "n_queens" to RecursionConfig(4f..6f, 4, "board size") { nQueensTrace(it) },
    "backtracking_pattern" to RecursionConfig(2f..5f, 4, "candidate values") { combinationSumTrace(it) },
    "subsets_pattern" to RecursionConfig(2f..4f, 3, "elements") { subsetsTrace(it) },
)

private fun recursionConfigFor(topicId: String): RecursionConfig =
    recursionConfigs[topicId] ?: recursionConfigs.getValue("factorial")

internal val recursionTreeTopicIds: Set<String> get() = recursionConfigs.keys

// Traces are rebuilt whenever the slider moves, so the whole declared range has to work — not just
// the default. A deep n is also where an unguarded trace overflows the stack.
internal fun recursionTreeFrameCount(topicId: String): Int {
    val config = recursionConfigFor(topicId)
    var total = 0
    for (n in config.nRange.start.toInt()..config.nRange.endInclusive.toInt()) {
        val trace = config.build(n)
        require(trace.frames.isNotEmpty()) { "$topicId at n = $n produced no frames" }
        val ids = trace.nodes.map { it.id }.toSet()
        val orphans = trace.nodes.mapNotNull { it.parent }.filter { it !in ids }
        require(orphans.isEmpty()) { "$topicId at n = $n has nodes with unresolvable parents: $orphans" }
        trace.frames.forEach { frame ->
            require(frame.stateById.keys.all { it in ids }) { "$topicId at n = $n states a node not in the trace" }
        }
        total += trace.frames.size
    }
    return total
}

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
                // Single-value ranges (sudoku, Strassen) would compute -1 here, which Slider rejects.
                steps = ((config.nRange.endInclusive - config.nRange.start).toInt() - 1).coerceAtLeast(0),
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
    val labelStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val denseLabelStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

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
        Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            // dp, not raw canvas pixels: as constants in pixels the padding and the node radius
            // shrank by the screen density, so on a 3x device a 17px node was a ~6dp dot.
            val padX = 10.dp.toPx()
            val padY = 14.dp.toPx()
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
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            // Size the node off the space each one actually gets — the leaf slot across and the row
            // gap down — capped so a shallow tree does not blow up into overlapping discs.
            val slot = (size.width - 2 * padX) / leafCount
            val rowGap = if (maxDepth == 0) size.height else (size.height - 2 * padY) / maxDepth
            // 16.dp is the shared node size across the sims — see the graph-algorithm canvas.
            val radius = minOf(slot * 0.40f, rowGap * 0.36f, 16.dp.toPx()).coerceAtLeast(9.dp.toPx())
            val style = if (radius < 13.dp.toPx()) denseLabelStyle else labelStyle
            nodes.forEach { node ->
                val center = Offset(px(xById.getValue(node.id)), py(node.depth))
                val color = when (stateById[node.id]) {
                    RecState.Active -> ActiveColor
                    RecState.Waiting -> WaitingColor
                    RecState.Returned -> ReturnedColor
                    else -> PendingColor
                }
                drawCircle(color, radius = radius, center = center)
                val layout = textMeasurer.measure(node.label.removePrefix("fact").removePrefix("fib"), style)
                drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
            }
        }
    }
}
