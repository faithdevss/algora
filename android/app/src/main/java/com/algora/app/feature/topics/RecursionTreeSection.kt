package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

// results holds what each call returned, keyed by node id — the tree shows the short ones on the node.
private class RecTrace(val nodes: List<RecNode>, val frames: List<RecFrame>, val results: Map<Int, String>)

// Instruments a recursion: call() on entry (pushes), ret() on exit (pops), snapshotting each time.
private class Tracer {
    val nodes = mutableListOf<RecNode>()
    val frames = mutableListOf<RecFrame>()
    private val stack = ArrayDeque<Int>()
    private val returned = mutableSetOf<Int>()
    private val results = mutableMapOf<Int, String>()

    fun trace() = RecTrace(nodes, frames, results)

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
        results[id] = result
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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
    return t.trace()
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

private val ActiveColor = SimColors.Active
private val ReturnedColor = SimColors.Green

// Dark text on the yellow active pill: white on #F5C542 is unreadable.
private val OnActiveColor = Color(0xFF1F1A0A)

// The badge under a returned node: the value itself, not the sentence. Long results (Strassen's
// tally, meet-in-the-middle's verdict) stay in the caption only.
private fun badgeFor(result: String): String? =
    result.substringBefore(" (").substringBefore(" · ").trim().takeIf { it.length <= 12 }

private fun displayLabel(label: String) = label.removePrefix("fact").removePrefix("fib")

@Composable
fun RecursionTreeSection(topicId: String) {
    // Hanoi and N-Queens have their own designs in docs/ios-design/Simulations iOS.html — pegs above a
    // call tree, and a board with the placement path. Every other recursion is the tree alone.
    when (topicId) {
        "tower_of_hanoi" -> HanoiLab()
        "n_queens" -> NQueensLab()
        else -> RecursionTreeLab(topicId)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecursionTreeLab(topicId: String) {
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

            RecursionCanvas(trace = trace, frame = frame)

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendSwatch(ActiveColor, "Active")
                LegendSwatch(notCalledFill(), "On the stack", border = ActiveColor)
                LegendSwatch(ReturnedColor, "Returned")
                LegendSwatch(notCalledFill(), "Not called yet")
            }

            LabCaption(frame.status, Modifier.padding(top = 14.dp))
            Text(
                "Call stack: " + if (frame.stack.isEmpty()) "(empty)" else frame.stack.joinToString("  ▸  "),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            PlaybackTransport(playback, captions = trace.frames.map { it.status })
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String, border: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .background(color, RoundedCornerShape(3.dp))
                .then(if (border != null) Modifier.border(1.5.dp, border, RoundedCornerShape(3.dp)) else Modifier),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

// ── Call-tree canvas ─────────────────────────────────────────────────────────
// Nodes are pills sized to their label, never discs the label spills out of. Every leaf gets the
// same slot — wide enough for the widest label in the tree — so no two nodes on a row can overlap;
// a tree too wide for the card scrolls sideways and follows the active call instead of shrinking.

private val PillHeight = 22.dp
private val PillPadding = 9.dp
private val SlotGap = 10.dp
private val RowGap = 42.dp
private val TreeEdge = 12.dp
private val BadgeRoom = 16.dp
private val MaxLabelWidth = 96.dp

// Depth → row; leaves take sequential slots, internal nodes center over their children.
private class CallTreeLayout(val xById: Map<Int, Float>, val leafCount: Int, val maxDepth: Int)

private fun layoutTree(nodes: List<RecNode>): CallTreeLayout {
    val childrenOf = nodes.groupBy { it.parent }
    val xById = HashMap<Int, Float>()
    var leafCursor = 0
    fun assignX(id: Int) {
        val kids = childrenOf[id].orEmpty()
        if (kids.isEmpty()) {
            xById[id] = leafCursor.toFloat()
            leafCursor++
        } else {
            kids.forEach { assignX(it.id) }
            xById[id] = kids.map { xById.getValue(it.id) }.average().toFloat()
        }
    }
    nodes.firstOrNull { it.parent == null }?.let { assignX(it.id) }
    return CallTreeLayout(xById, leafCursor.coerceAtLeast(1), nodes.maxOfOrNull { it.depth } ?: 0)
}

// "Not called yet" pill and swatch: a flat neutral, one step off the card.
@Composable
private fun notCalledFill() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)

// framed = its own tinted box, for the labs that are only a tree; Hanoi sets the tree straight onto
// its card under a "CALL TREE" heading, so it passes false. showBadges prints each returned call's
// value under its pill; Hanoi's calls return nothing, so it passes false.
@Composable
private fun RecursionCanvas(trace: RecTrace, frame: RecFrame, framed: Boolean = true, showBadges: Boolean = true) {
    val nodes = trace.nodes
    val stateById = frame.stateById
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val idleFill = notCalledFill()
    val labelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val badgeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Medium)

    val layout = remember(trace) { layoutTree(nodes) }
    val labels = remember(trace, density) {
        val maxWidth = with(density) { MaxLabelWidth.roundToPx() }
        nodes.map { node ->
            textMeasurer.measure(
                displayLabel(node.label),
                labelStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxWidth),
            )
        }
    }
    val badges = remember(trace) {
        trace.results.mapNotNull { (id, result) -> badgeFor(result)?.let { id to textMeasurer.measure(it, badgeStyle) } }.toMap()
    }

    val treeHeight = TreeEdge * 2 + PillHeight + RowGap * layout.maxDepth + if (showBadges) BadgeRoom else 0.dp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (framed) {
                    Modifier
                        .padding(top = 14.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            ),
    ) {
        val viewport = constraints.maxWidth.toFloat()
        val edgePx = with(density) { TreeEdge.toPx() }
        val padPx = with(density) { PillPadding.toPx() }
        val widest = labels.maxOfOrNull { it.size.width }?.toFloat() ?: 0f
        val minSlot = widest + 2 * padPx + with(density) { SlotGap.toPx() }
        // A narrow tree stretches to fill the card; a wide one keeps its slots and scrolls.
        val contentPx = maxOf(viewport, minSlot * layout.leafCount + 2 * edgePx)
        val slot = (contentPx - 2 * edgePx) / layout.leafCount
        fun cx(id: Int) = edgePx + (layout.xById.getValue(id) + 0.5f) * slot

        val scroll = rememberScrollState()
        val activeId = stateById.entries.firstOrNull { it.value == RecState.Active }?.key
        LaunchedEffect(activeId, contentPx) {
            activeId?.let { scroll.animateScrollTo((cx(it) - viewport / 2f).roundToInt().coerceAtLeast(0)) }
        }

        Canvas(
            modifier = Modifier
                .horizontalScroll(scroll)
                .width(with(density) { contentPx.toDp() })
                .height(treeHeight),
        ) {
            val pillH = PillHeight.toPx()
            fun cy(depth: Int) = edgePx + pillH / 2f + depth * RowGap.toPx()
            // Plain connectors, one colour: the pills carry the state (docs/ios-design mock).
            nodes.forEach { node ->
                val p = node.parent ?: return@forEach
                drawLine(
                    muted.copy(alpha = 0.45f),
                    Offset(cx(p), cy(nodes[p].depth) + pillH / 2f),
                    Offset(cx(node.id), cy(node.depth) - pillH / 2f),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            nodes.forEach { node ->
                val label = labels[node.id]
                val w = label.size.width + 2 * padPx
                val center = Offset(cx(node.id), cy(node.depth))
                val topLeft = Offset(center.x - w / 2f, center.y - pillH / 2f)
                val pill = Size(w, pillH)
                val corner = CornerRadius(pillH / 2f)
                val textColor = when (stateById[node.id]) {
                    RecState.Active -> {
                        drawRoundRect(ActiveColor, topLeft, pill, corner)
                        OnActiveColor
                    }
                    // Called and waiting on a child: neutral like an idle call, ringed in the active
                    // yellow because it is part of the chain that leads to the active one.
                    RecState.Waiting -> {
                        drawRoundRect(idleFill, topLeft, pill, corner)
                        drawRoundRect(ActiveColor, topLeft, pill, corner, style = Stroke(width = 1.5.dp.toPx()))
                        onSurface
                    }
                    RecState.Returned -> {
                        drawRoundRect(ReturnedColor, topLeft, pill, corner)
                        if (showBadges) {
                            badges[node.id]?.let { badge ->
                                drawText(
                                    badge,
                                    color = ReturnedColor,
                                    topLeft = Offset(center.x - badge.size.width / 2f, center.y + pillH / 2f + 2.dp.toPx()),
                                )
                            }
                        }
                        Color.White
                    }
                    null -> {
                        drawRoundRect(idleFill, topLeft, pill, corner)
                        muted.copy(alpha = 0.7f)
                    }
                }
                drawText(
                    label,
                    color = textColor,
                    topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f),
                )
            }
        }
    }
}

// ── Tower of Hanoi ───────────────────────────────────────────────────────────
// Built to docs/ios-design/Simulations iOS.html: disk-count picker, pegs with the disk that just moved
// in yellow and a dashed arc from its old peg, the call tree underneath, then step chips and a
// headline + explanation. One step per disk move rather than per call/return — the pegs are what is
// being taught, and h(n) always moves disk n, so the tree and the pegs never disagree.

private val HanoiPegNames = listOf("A", "B", "C")
private val HanoiDiskCounts = listOf(3, 4, 5)

private class HanoiMove(val disk: Int, val from: Int, val to: Int, val caller: Int)

// first/last are each call's first and last move index across its whole subtree: a call is "not
// called yet" before its first move and "returned" after its last.
private class HanoiPlan(
    val disks: Int,
    val trace: RecTrace,
    val moves: List<HanoiMove>,
    private val first: IntArray,
    private val last: IntArray,
) {
    fun frame(m: Int): RecFrame {
        val mover = moves[m].caller
        val states = trace.nodes.mapNotNull { n ->
            when {
                n.id == mover -> n.id to RecState.Active
                last[n.id] < m -> n.id to RecState.Returned
                first[n.id] <= m -> n.id to RecState.Waiting
                else -> null
            }
        }.toMap()
        val stack = generateSequence(mover) { trace.nodes[it].parent }.map { trace.nodes[it].label }.toList().reversed()
        return RecFrame(states, stack, headline(m))
    }

    fun pegsAfter(m: Int): List<List<Int>> {
        val pegs = List(3) { mutableListOf<Int>() }
        pegs[0].addAll(disks downTo 1)
        for (i in 0..m) {
            val mv = moves[i]
            pegs[mv.to].add(pegs[mv.from].removeAt(pegs[mv.from].lastIndex))
        }
        return pegs
    }

    fun headline(m: Int): String = moves[m].let {
        "h(${it.disk}) moves disk ${it.disk} from ${HanoiPegNames[it.from]} to ${HanoiPegNames[it.to]}."
    }

    fun body(m: Int): String {
        val mv = moves[m]
        val via = HanoiPegNames[3 - mv.from - mv.to]
        val to = HanoiPegNames[mv.to]
        val k = mv.disk - 1
        val text = when (k) {
            0 -> "Base case: nothing sits on disk 1, so h(1) moves it straight across and returns."
            1 -> "The first h(1) already parked disk 1 on $via. The second h(1) moves it onto $to."
            2 -> "The first h(2) already parked disks 1 and 2 on $via. The second h(2) moves them onto $to."
            else -> "The first h($k) already parked disks 1–$k on $via. The second h($k) moves them onto $to."
        }
        return if (m == moves.lastIndex) {
            "$text That completes the puzzle: $disks disks in ${moves.size} = 2^$disks − 1 moves, the fewest possible."
        } else {
            text
        }
    }
}

private fun hanoiPlan(disks: Int): HanoiPlan {
    val nodes = mutableListOf<RecNode>()
    val moves = mutableListOf<HanoiMove>()
    val first = mutableListOf<Int>()
    val last = mutableListOf<Int>()
    fun solve(parent: Int?, n: Int, from: Int, to: Int, via: Int) {
        val id = nodes.size
        nodes += RecNode(id, parent, "h($n)", parent?.let { nodes[it].depth + 1 } ?: 0)
        first += moves.size
        last += -1
        if (n > 1) solve(id, n - 1, from, via, to)
        moves += HanoiMove(n, from, to, id)
        if (n > 1) solve(id, n - 1, via, to, from)
        last[id] = moves.size - 1
    }
    solve(null, disks, 0, 2, 1)
    return HanoiPlan(disks, RecTrace(nodes, emptyList(), emptyMap()), moves, first.toIntArray(), last.toIntArray())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HanoiLab() {
    var disks by remember { mutableStateOf(HanoiDiskCounts.first()) }
    val plan = remember(disks) { hanoiPlan(disks) }
    val playback = rememberPlaybackState(key = plan, stepCount = plan.moves.size)
    val m = playback.index.coerceIn(0, plan.moves.lastIndex)
    val move = plan.moves[m]
    val frame = remember(plan, m) { plan.frame(m) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
    val highlight = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) ActiveColor else Color(0xFFB7791F)

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SegmentPicker(HanoiDiskCounts, disks) { disks = it }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("disks · ${plan.moves.size} moves", style = MaterialTheme.typography.bodyMedium, color = muted)
                }

                HanoiPegs(plan.pegsAfter(m), move, disks, Modifier.padding(top = 16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
                Text(
                    "CALL TREE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = muted,
                    letterSpacing = 1.sp,
                )
                RecursionCanvas(trace = plan.trace, frame = frame, framed = false, showBadges = false)

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LegendSwatch(ActiveColor, "Moving / active")
                    LegendSwatch(ReturnedColor, "Returned")
                    LegendSwatch(notCalledFill(), "Not called yet")
                }
            }
        }

        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepChip("move", "${m + 1} / ${plan.moves.size}")
            StepChip("stack", frame.stack.joinToString(" › "))
        }

        Text(
            buildAnnotatedString {
                append("h(${move.disk}) moves disk ")
                withStyle(SpanStyle(color = highlight)) { append("${move.disk}") }
                append(" from ${HanoiPegNames[move.from]} to ${HanoiPegNames[move.to]}.")
            },
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            plan.body(m),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        PlaybackTransport(playback, captions = plan.moves.indices.map { plan.headline(it) })
    }
}

// The mock's segmented size control (Hanoi's disk count, N-Queens' board size).
@Composable
private fun SegmentPicker(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .background(muted.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        options.forEach { count ->
            val on = count == selected
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) muted.copy(alpha = 0.3f) else Color.Transparent)
                    .clickable { onSelect(count) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$count",
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else muted,
                )
            }
        }
    }
}

// alert tints the chip red — N-Queens' "safe 0 / n" when a row is a dead end.
@Composable
private fun StepChip(label: String, value: String, alert: Boolean = false) {
    Row(
        modifier = Modifier
            .background(
                if (alert) SimColors.Red.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private val DiskHeight = 22.dp
private val DiskGap = 3.dp
private val ArcRoom = 30.dp
private val RodOverhang = 14.dp

@Composable
private fun HanoiPegs(pegs: List<List<Int>>, move: HanoiMove, disks: Int, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val diskFill = muted.copy(alpha = 0.32f)
    val rodColor = muted.copy(alpha = 0.3f)
    val diskStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val pegStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val stackHeight = (DiskHeight + DiskGap) * disks

    Canvas(modifier = modifier.fillMaxWidth().height(ArcRoom + RodOverhang + stackHeight + 36.dp)) {
        val colW = size.width / 3f
        val rodTop = ArcRoom.toPx()
        val baseY = rodTop + RodOverhang.toPx() + stackHeight.toPx()
        val dh = DiskHeight.toPx()
        val gap = DiskGap.toPx()
        val inset = 4.dp.toPx()
        val rodW = 6.dp.toPx()
        fun cx(peg: Int) = colW * (peg + 0.5f)

        for (peg in 0..2) {
            drawRoundRect(rodColor, Offset(cx(peg) - rodW / 2f, rodTop), Size(rodW, baseY - rodTop), CornerRadius(rodW / 2f))
            drawRoundRect(
                rodColor,
                Offset(colW * peg + inset, baseY),
                Size(colW - 2 * inset, 4.dp.toPx()),
                CornerRadius(2.dp.toPx()),
            )
            val name = textMeasurer.measure(HanoiPegNames[peg], pegStyle)
            drawText(name, color = onSurface, topLeft = Offset(cx(peg) - name.size.width / 2f, baseY + 14.dp.toPx()))

            // Widths scale from 42% of the peg's column for disk 1 up to the full column for the
            // largest, so every disk count uses the same footprint.
            val maxW = colW - 2 * inset
            val minW = maxW * 0.42f
            pegs[peg].forEachIndexed { level, disk ->
                val w = if (disks == 1) maxW else minW + (maxW - minW) * (disk - 1) / (disks - 1)
                val top = baseY - (level + 1) * (dh + gap)
                val moved = disk == move.disk
                drawRoundRect(
                    if (moved) ActiveColor else diskFill,
                    Offset(cx(peg) - w / 2f, top),
                    Size(w, dh),
                    CornerRadius(6.dp.toPx()),
                )
                val number = textMeasurer.measure("$disk", diskStyle)
                drawText(
                    number,
                    color = if (moved) OnActiveColor else onSurface,
                    topLeft = Offset(cx(peg) - number.size.width / 2f, top + (dh - number.size.height) / 2f),
                )
            }
        }

        // The move just made: a dashed arc from beside the old peg's top to the new one's, peaking
        // just under the canvas top.
        val dir = if (move.to > move.from) 1f else -1f
        val start = Offset(cx(move.from) + dir * 8.dp.toPx(), rodTop + 20.dp.toPx())
        val end = Offset(cx(move.to) - dir * 12.dp.toPx(), rodTop + 12.dp.toPx())
        val control = Offset((start.x + end.x) / 2f, rodTop - 60.dp.toPx())
        val arc = Path().apply {
            moveTo(start.x, start.y)
            quadraticBezierTo(control.x, control.y, end.x, end.y)
        }
        drawPath(
            arc,
            ActiveColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
            ),
        )
        val angle = atan2(end.y - control.y, end.x - control.x)
        val head = 9.dp.toPx()
        listOf(0.5f, -0.5f).forEach { spread ->
            drawLine(
                ActiveColor,
                end,
                Offset(end.x - head * cos(angle + spread), end.y - head * sin(angle + spread)),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

// ── N-Queens ─────────────────────────────────────────────────────────────────
// Built to docs/ios-design/Simulations iOS.html: a board with the row being tried outlined in yellow
// and its attacked squares crossed out, the placements so far as a path of chips, then row / safe /
// backtracks chips and a headline. One step per decision — a queen placed, or a row found dead — and
// a dead end jumps straight to the row the search resumes from, naming every row it unwound past.

private val QueensSizes = listOf(4, 5, 6, 8)
private const val QueenGlyph = "♛"

private enum class QueensTone { PLACED, ATTACKED, SOLVED }

private class QueensFrame(
    // queens[r] is the column of the queen in row r, for every row placed so far.
    val queens: List<Int>,
    // The row being tried; null once every row holds a queen.
    val row: Int?,
    // Columns of `row` that the queens above it attack.
    val attacked: Set<Int>,
    val deadEnd: Boolean,
    val backtracks: Int,
    // Headline in three parts so the middle word can take the tone's colour.
    val lead: String,
    val emphasis: String,
    val tail: String,
    val tone: QueensTone,
    val body: String,
) {
    val headline: String get() = lead + emphasis + tail
}

private fun queensFrames(n: Int): List<QueensFrame> {
    val cols = mutableListOf<Int>()
    val frames = mutableListOf<QueensFrame>()
    var backtracks = 0
    fun attackedIn(row: Int, placed: List<Int>): Set<Int> =
        (0 until n).filter { c -> placed.withIndex().any { (r, qc) -> qc == c || abs(qc - c) == row - r } }.toSet()

    var row = 0
    var startCol = 0
    // The column this row's queen sat in before a backtrack slid it right.
    var resumedFrom: Int? = null
    while (row < n) {
        val attacked = attackedIn(row, cols)
        val col = (startCol until n).firstOrNull { it !in attacked }
        if (col != null) {
            cols += col
            val safe = n - attacked.size
            val body = when {
                resumedFrom != null ->
                    "c$resumedFrom led nowhere, so row $row moves its queen to c$col, the next column nothing attacks."
                attacked.isEmpty() -> "Nothing attacks row $row yet, so the queen goes in the leftmost square."
                else -> "$safe of $n squares in row $row are safe. Take the leftmost and move on to row ${row + 1}."
            }
            frames += QueensFrame(
                cols.toList(), row, attacked, deadEnd = false, backtracks = backtracks,
                lead = "c$col is ", emphasis = "safe", tail = " in row $row. Place a queen.",
                tone = QueensTone.PLACED, body = body,
            )
            row++
            startCol = 0
            resumedFrom = null
            continue
        }

        backtracks++
        // Unwind: lift queens from the bottom up until one can slide right to a column nothing attacks.
        val trial = cols.toMutableList()
        var k = row - 1
        var next: Int? = null
        var lifted = -1
        while (k >= 0) {
            lifted = trial.removeAt(k)
            next = (lifted + 1 until n).firstOrNull { it !in attackedIn(k, trial) }
            if (next != null) break
            k--
        }
        val exhausted = (k + 1) until row
        val body = when {
            k < 0 -> "No row above has another safe column, so there is no solution for n = $n."
            exhausted.isEmpty() -> "The search backs up to row $k and tries c$next instead."
            exhausted.first == exhausted.last ->
                "Row ${exhausted.first} has no other safe column, so the search unwinds to row $k and tries c$next."
            else -> "Rows ${exhausted.first}–${exhausted.last} have no other safe column, so the search unwinds " +
                "to row $k and tries c$next."
        }
        frames += QueensFrame(
            cols.toList(), row, attacked, deadEnd = true, backtracks = backtracks,
            lead = "Every square in row $row is ", emphasis = "attacked", tail = ". Backtrack.",
            tone = QueensTone.ATTACKED, body = body,
        )
        if (k < 0 || next == null) return frames
        cols.clear()
        cols += trial
        row = k
        startCol = next
        resumedFrom = lifted
    }

    frames += QueensFrame(
        cols.toList(), null, emptySet(), deadEnd = false, backtracks = backtracks,
        lead = "All $n queens are ", emphasis = "placed", tail = ".",
        tone = QueensTone.SOLVED,
        body = "No two share a row, column or diagonal. Found after $backtracks " +
            "${if (backtracks == 1) "backtrack" else "backtracks"} — the search stops at the first solution.",
    )
    return frames
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NQueensLab() {
    var n by remember { mutableStateOf(QueensSizes.first()) }
    val frames = remember(n) { queensFrames(n) }
    val playback = rememberPlaybackState(key = frames, stepCount = frames.size)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val tones = queensTones()

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SegmentPicker(QueensSizes, n) { n = it }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("board size", style = MaterialTheme.typography.bodyMedium, color = muted)
                }

                QueensBoard(n, frame, Modifier.padding(top = 14.dp))

                Text(
                    "PATH",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = muted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 18.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    frame.queens.forEachIndexed { r, c ->
                        if (r > 0) PathSeparator()
                        PathChip("r$r c$c", SimColors.Blue.copy(alpha = 0.28f), onSurface)
                    }
                    if (frame.deadEnd) {
                        if (frame.queens.isNotEmpty()) PathSeparator()
                        PathChip("r${frame.row} ×", SimColors.Red.copy(alpha = 0.18f), tones.getValue(QueensTone.ATTACKED))
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LegendSwatch(SimColors.Blue, "Placed")
                    LegendSwatch(Color.Transparent, "Trying row", border = ActiveColor)
                    LegendSwatch(SimColors.Red, "Attacked")
                }
            }
        }

        FlowRow(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val row = frame.row
            if (row != null) {
                StepChip("row", "$row")
                StepChip("safe", "${n - frame.attacked.size} / $n", alert = frame.deadEnd)
            } else {
                StepChip("queens", "$n / $n")
            }
            StepChip("backtracks", "${frame.backtracks}")
        }

        Text(
            buildAnnotatedString {
                append(frame.lead)
                withStyle(SpanStyle(color = tones.getValue(frame.tone))) { append(frame.emphasis) }
                append(frame.tail)
            },
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            frame.body,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Dead ends are the steps worth finding again, so they get red ticks on the track.
        val backtracks = remember(frames) {
            TrackMarks(frames.indices.filter { frames[it].deadEnd }.toSet(), "backtrack", SimColors.Red)
        }
        PlaybackTransport(playback, captions = frames.map { it.headline }, marks = backtracks)
    }
}

// Text colours for the headline word and the dead-end chip: the mock's pale red and blue are for a
// dark card, and would wash out on white.
@Composable
private fun queensTones(): Map<QueensTone, Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return mapOf(
        QueensTone.PLACED to if (dark) Color(0xFF93B8FF) else Color(0xFF2563EB),
        QueensTone.ATTACKED to if (dark) Color(0xFFF28B82) else SimColors.Red,
        QueensTone.SOLVED to ReturnedColor,
    )
}

@Composable
private fun QueensBoard(n: Int, frame: QueensFrame, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tryingLabel = if (dark) ActiveColor else Color(0xFFB7791F)
    val attackedMark = queensTones().getValue(QueensTone.ATTACKED)
    val queenColor = if (dark) Color.White else Color(0xFF1E3A8A)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val labelWidth = 34.dp
        val gap = if (n >= 8) 4.dp else 6.dp
        val cell = minOf((maxWidth - labelWidth - gap * (n - 1)) / n, 58.dp)
        val shape = RoundedCornerShape(if (n >= 8) 6.dp else 8.dp)

        Column(modifier = Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(modifier = Modifier.width(labelWidth - gap))
                (0 until n).forEach { c ->
                    Text(
                        "c$c",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(cell),
                    )
                }
            }
            (0 until n).forEach { r ->
                val trying = r == frame.row
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "r$r",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = if (trying) FontWeight.Bold else FontWeight.Medium,
                        color = if (trying) tryingLabel else muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(labelWidth - gap),
                    )
                    (0 until n).forEach { c ->
                        val queenHere = frame.queens.getOrNull(r) == c
                        val attacked = trying && c in frame.attacked
                        val fill = when {
                            queenHere -> SimColors.Blue.copy(alpha = 0.3f)
                            attacked -> SimColors.Red.copy(alpha = 0.18f)
                            else -> muted.copy(alpha = 0.16f)
                        }
                        val border = when {
                            queenHere -> SimColors.Blue
                            trying -> ActiveColor
                            else -> null
                        }
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .background(fill, shape)
                                .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                queenHere -> Text(QueenGlyph, fontSize = (cell.value * 0.42f).sp, color = queenColor)
                                attacked -> Text("×", fontSize = (cell.value * 0.5f).sp, color = attackedMark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PathChip(text: String, background: Color, content: Color) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = content,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun PathSeparator() {
    Text(
        "›",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
