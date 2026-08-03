package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors

// ── DP tabulation grid ───────────────────────────────────────────────────────
// Fills a DP table cell-by-cell (one PlaybackTransport step each), then, for 2-D problems, walks the
// traceback path. Each frame snapshots the values written so far + the active/traced cells.

private class DpFrame(
    val values: Map<Int, String>,   // cellKey -> written value
    val active: Int?,               // cell being written or traced now
    val traced: Set<Int>,           // traceback cells revealed so far
    val status: String,
)

private class DpConfig(
    val rows: Int,
    val cols: Int,
    val rowHeader: (Int) -> String,
    val colHeader: (Int) -> String,
    val corner: String,
    val intro: String,
    val build: () -> List<DpFrame>,
)

private val ActiveCell = Color(0xFFFACC15)
private val FilledCell = Color(0xFF3B82F6)
private val TracedCell = SimColors.Green

private class DpBuilder(val rows: Int, val cols: Int) {
    val frames = mutableListOf<DpFrame>()
    val values = LinkedHashMap<Int, String>()
    fun key(r: Int, c: Int) = r * cols + c
    fun fill(r: Int, c: Int, value: String, status: String) {
        values[key(r, c)] = value
        frames.add(DpFrame(values.toMap(), key(r, c), emptySet(), status))
    }
    fun trace(cells: List<Int>, statusFor: (Int) -> String) {
        val traced = mutableSetOf<Int>()
        cells.forEach { cell ->
            traced.add(cell)
            frames.add(DpFrame(values.toMap(), cell, traced.toSet(), statusFor(cell)))
        }
    }
}

// dp[i] = dp[i-1] + dp[i-2], filled left to right. 1-D, no traceback.
private fun fibonacciDpFrames(): List<DpFrame> {
    val n = 9
    val b = DpBuilder(rows = 1, cols = n + 1)
    val dp = LongArray(n + 1)
    for (i in 0..n) {
        dp[i] = if (i < 2) i.toLong() else dp[i - 1] + dp[i - 2]
        val status = if (i < 2) "dp[$i] = $i  (base case)"
        else "dp[$i] = dp[${i - 1}] + dp[${i - 2}] = ${dp[i]}"
        b.fill(0, i, dp[i].toString(), status)
    }
    return b.frames
}

private fun editDistanceFrames(): List<DpFrame> {
    val a = "cat"
    val bWord = "cut"
    val rows = a.length + 1
    val cols = bWord.length + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val status: String
            dp[i][j] = when {
                i == 0 -> { status = "dp[0][$j] = $j  (turn ε into first $j chars)"; j }
                j == 0 -> { status = "dp[$i][0] = $i  (delete $i chars)"; i }
                a[i - 1] == bWord[j - 1] -> {
                    status = "'${a[i - 1]}' == '${bWord[j - 1]}' → dp[${i - 1}][${j - 1}] = ${dp[i - 1][j - 1]}"
                    dp[i - 1][j - 1]
                }
                else -> {
                    val v = 1 + minOf(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1])
                    status = "'${a[i - 1]}' ≠ '${bWord[j - 1]}' → 1 + min(diag, up, left) = $v"
                    v
                }
            }
            bld.fill(i, j, dp[i][j].toString(), status)
        }
    }
    // traceback from bottom-right
    var i = rows - 1
    var j = cols - 1
    val path = mutableListOf(bld.key(i, j))
    while (i > 0 || j > 0) {
        val match = i > 0 && j > 0 && a[i - 1] == bWord[j - 1]
        when {
            i > 0 && j > 0 && (match || dp[i][j] == dp[i - 1][j - 1] + 1) -> { i--; j-- }
            i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> i--
            else -> j--
        }
        path.add(bld.key(i, j))
    }
    bld.trace(path.reversed()) { "Traceback — the edit path. Distance = ${dp[rows - 1][cols - 1]}." }
    return bld.frames
}

private fun lcsFrames(): List<DpFrame> {
    val a = "abcbd"
    val bWord = "acbd"
    val rows = a.length + 1
    val cols = bWord.length + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val status: String
            dp[i][j] = when {
                i == 0 || j == 0 -> { status = "dp[$i][$j] = 0  (empty prefix)"; 0 }
                a[i - 1] == bWord[j - 1] -> {
                    status = "'${a[i - 1]}' matches → dp[${i - 1}][${j - 1}] + 1 = ${dp[i - 1][j - 1] + 1}"
                    dp[i - 1][j - 1] + 1
                }
                else -> {
                    val v = maxOf(dp[i - 1][j], dp[i][j - 1])
                    status = "no match → max(up, left) = $v"
                    v
                }
            }
            bld.fill(i, j, dp[i][j].toString(), status)
        }
    }
    var i = rows - 1
    var j = cols - 1
    val path = mutableListOf(bld.key(i, j))
    while (i > 0 && j > 0) {
        when {
            a[i - 1] == bWord[j - 1] -> { i--; j-- }
            dp[i - 1][j] >= dp[i][j - 1] -> i--
            else -> j--
        }
        path.add(bld.key(i, j))
    }
    bld.trace(path.reversed()) { "Traceback — matched characters. LCS length = ${dp[rows - 1][cols - 1]}." }
    return bld.frames
}

private val coinChangeCoins = intArrayOf(1, 3, 4)
private const val COIN_CHANGE_AMOUNT = 6
private const val INF = Int.MAX_VALUE / 2

private fun coinFmt(x: Int): String = if (x >= INF) "∞" else x.toString()

// dp[i][a] = fewest coins for amount a using the first i denominations (unbounded).
private fun coinChangeFrames(): List<DpFrame> {
    val coins = coinChangeCoins
    val amount = COIN_CHANGE_AMOUNT
    val rows = coins.size + 1
    val cols = amount + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) { INF } }
    for (i in 0 until rows) {
        for (a in 0 until cols) {
            val status: String
            dp[i][a] = when {
                a == 0 -> { status = "dp[$i][0] = 0  (amount 0 needs no coins)"; 0 }
                i == 0 -> { status = "dp[0][$a] = ∞  (no coins available)"; INF }
                else -> {
                    val skip = dp[i - 1][a]
                    val coin = coins[i - 1]
                    val use = if (coin <= a && dp[i][a - coin] < INF) dp[i][a - coin] + 1 else INF
                    val v = minOf(skip, use)
                    status = "coin ${coins[i - 1]}: min(skip ${coinFmt(skip)}, use ${coinFmt(use)}) = ${coinFmt(v)}"
                    v
                }
            }
            bld.fill(i, a, coinFmt(dp[i][a]), status)
        }
    }
    if (dp[rows - 1][cols - 1] < INF) {
        var i = rows - 1
        var a = cols - 1
        val path = mutableListOf(bld.key(i, a))
        while (a > 0 && i > 0) {
            if (dp[i][a] == dp[i - 1][a]) i-- else a -= coins[i - 1]
            path.add(bld.key(i, a))
        }
        bld.trace(path.reversed()) { "Traceback — coins used. Minimum = ${dp[rows - 1][cols - 1]}." }
    }
    return bld.frames
}

private val knapWeights = intArrayOf(1, 2, 3)
private val knapValues = intArrayOf(6, 10, 12)
private const val KNAP_CAPACITY = 5

// dp[i][w] = best value from the first i items within capacity w.
private fun knapsackFrames(): List<DpFrame> {
    val rows = knapWeights.size + 1
    val cols = KNAP_CAPACITY + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }
    for (i in 0 until rows) {
        for (w in 0 until cols) {
            val status: String
            dp[i][w] = when {
                i == 0 || w == 0 -> { status = "dp[$i][$w] = 0  (no items or no capacity)"; 0 }
                knapWeights[i - 1] <= w -> {
                    val skip = dp[i - 1][w]
                    val take = dp[i - 1][w - knapWeights[i - 1]] + knapValues[i - 1]
                    val v = maxOf(skip, take)
                    status = "item $i (wt ${knapWeights[i - 1]}, val ${knapValues[i - 1]}): max(skip $skip, take $take) = $v"
                    v
                }
                else -> { status = "item $i too heavy → carry dp[${i - 1}][$w] = ${dp[i - 1][w]}"; dp[i - 1][w] }
            }
            bld.fill(i, w, dp[i][w].toString(), status)
        }
    }
    var i = rows - 1
    var w = cols - 1
    val path = mutableListOf(bld.key(i, w))
    while (i > 0 && w >= 0) {
        if (dp[i][w] == dp[i - 1][w]) {
            i--
        } else {
            w -= knapWeights[i - 1]
            i--
        }
        if (w < 0) break
        path.add(bld.key(i, w))
    }
    bld.trace(path.reversed()) { "Traceback — items chosen. Max value = ${dp[rows - 1][cols - 1]}." }
    return bld.frames
}

private val rodPrices = intArrayOf(1, 5, 8, 9, 10)
private const val ROD_LENGTH = 5

// dp[i][l] = best revenue for a rod of length l cutting only pieces of length <= i.
private fun rodCuttingFrames(): List<DpFrame> {
    val rows = rodPrices.size + 1
    val cols = ROD_LENGTH + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }
    for (i in 0 until rows) {
        for (l in 0 until cols) {
            val status: String
            dp[i][l] = when {
                i == 0 || l == 0 -> { status = "dp[$i][$l] = 0  (no piece length, or no rod left)"; 0 }
                i <= l -> {
                    val skip = dp[i - 1][l]
                    val cut = dp[i][l - i] + rodPrices[i - 1]
                    val v = maxOf(skip, cut)
                    status = "length $i (price ${rodPrices[i - 1]}): max(skip $skip, cut $cut) = $v"
                    v
                }
                else -> { status = "piece $i longer than rod $l → carry dp[${i - 1}][$l] = ${dp[i - 1][l]}"; dp[i - 1][l] }
            }
            bld.fill(i, l, dp[i][l].toString(), status)
        }
    }
    var i = rows - 1
    var l = cols - 1
    val path = mutableListOf(bld.key(i, l))
    while (i > 0 && l > 0) {
        if (dp[i][l] == dp[i - 1][l]) i-- else l -= i
        path.add(bld.key(i, l))
    }
    bld.trace(path.reversed()) { "Traceback — the cuts chosen. Best revenue = ${dp[rows - 1][cols - 1]}." }
    return bld.frames
}

private val lisInput = intArrayOf(3, 1, 4, 2, 6, 5)

// 1-D O(n^2) LIS: dp[i] = longest increasing subsequence ending at i.
private fun lisFrames(): List<DpFrame> {
    val n = lisInput.size
    val bld = DpBuilder(rows = 1, cols = n)
    val dp = IntArray(n) { 1 }
    for (i in 0 until n) {
        var best = 1
        var from = -1
        for (j in 0 until i) {
            if (lisInput[j] < lisInput[i] && dp[j] + 1 > best) {
                best = dp[j] + 1
                from = j
            }
        }
        dp[i] = best
        val status = if (from < 0) "dp[$i] = 1  (${lisInput[i]} starts its own subsequence)"
        else "dp[$i] = dp[$from] + 1 = $best  (extend the run ending at ${lisInput[from]})"
        bld.fill(0, i, dp[i].toString(), status)
    }
    return bld.frames
}

private val chainDims = intArrayOf(10, 30, 5, 60)

// dp[i][j] = fewest scalar multiplications to multiply matrices i..j. Filled by chain length,
// so the table populates diagonally rather than row by row.
private fun matrixChainFrames(): List<DpFrame> {
    val n = chainDims.size - 1
    val bld = DpBuilder(rows = n, cols = n)
    val dp = Array(n) { IntArray(n) }
    for (i in 0 until n) bld.fill(i, i, "0", "dp[$i][$i] = 0  (a single matrix needs no multiplication)")
    for (len in 2..n) {
        for (i in 0..n - len) {
            val j = i + len - 1
            var best = Int.MAX_VALUE
            var split = i
            for (k in i until j) {
                val cost = dp[i][k] + dp[k + 1][j] + chainDims[i] * chainDims[k + 1] * chainDims[j + 1]
                if (cost < best) { best = cost; split = k }
            }
            dp[i][j] = best
            bld.fill(i, j, best.toString(), "dp[$i][$j] = $best  (best split after matrix $split)")
        }
    }
    return bld.frames
}

// Subset sum: dp[i][t] is true when some subset of the first i items totals exactly t. Boolean
// rather than numeric, and the traceback recovers which items were chosen.
private fun subsetSumFrames(): List<DpFrame> {
    val items = listOf(3, 4, 5, 2)
    val target = 9
    val b = DpBuilder(rows = items.size + 1, cols = target + 1)
    val dp = Array(items.size + 1) { BooleanArray(target + 1) }

    dp[0][0] = true
    b.fill(0, 0, "T", "dp[0][0] = true: the empty subset sums to 0. Every other total is unreachable with no items.")
    for (t in 1..target) {
        b.fill(0, t, "·", "dp[0][$t] = false — no items, so no way to reach $t.")
    }

    for (i in 1..items.size) {
        val item = items[i - 1]
        for (t in 0..target) {
            val skip = dp[i - 1][t]
            val take = t >= item && dp[i - 1][t - item]
            dp[i][t] = skip || take
            val status = when {
                take && skip -> "dp[$i][$t]: reachable either way — skip ${item}, or take it and reach ${t - item} first."
                take -> "dp[$i][$t] = true by taking $item: dp[${i - 1}][${t - item}] was already reachable."
                skip -> "dp[$i][$t] = true by skipping $item — it was reachable without this item."
                else -> "dp[$i][$t] = false: unreachable with the first $i item(s)."
            }
            b.fill(i, t, if (dp[i][t]) "T" else "·", status)
        }
    }

    // Walk back from dp[n][target] recovering the chosen items.
    val chosen = mutableListOf<Int>()
    val path = mutableListOf<Int>()
    var t = target
    for (i in items.size downTo 1) {
        path += b.key(i, t)
        if (!dp[i - 1][t]) {
            chosen += items[i - 1]
            t -= items[i - 1]
        }
    }
    path += b.key(0, t)
    val picked = chosen.reversed()

    b.trace(path) { cell ->
        val r = cell / (target + 1)
        val c = cell % (target + 1)
        if (r == 0) "Back at dp[0][0] — the subset is complete: ${picked.joinToString(" + ")} = $target."
        else "At dp[$r][$c]: " + (
            if (!dp[r - 1][c]) "this total was only reachable by taking ${items[r - 1]}, so it is in the subset."
            else "reachable without item ${items[r - 1]}, so skip it and move up."
            )
    }
    return b.frames
}

// ── Bitmask DP: Held-Karp travelling salesman over 4 cities ──────────────────

// Only masks that contain the start city 0 are reachable, so the grid shows those 8 rows.
private val tspMasks = (0 until 16).filter { it and 1 == 1 }

private fun tspMaskLabel(row: Int): String {
    val mask = tspMasks[row]
    val members = (0 until 4).filter { mask and (1 shl it) != 0 }
    return members.joinToString(",", prefix = "{", postfix = "}")
}

private fun bitmaskDpFrames(): List<DpFrame> {
    val n = 4
    val cost = arrayOf(
        intArrayOf(0, 10, 15, 20),
        intArrayOf(10, 0, 35, 25),
        intArrayOf(15, 35, 0, 30),
        intArrayOf(20, 25, 30, 0),
    )
    val inf = Int.MAX_VALUE / 2
    val full = (1 shl n) - 1
    val b = DpBuilder(rows = tspMasks.size, cols = n)
    val dp = Array(1 shl n) { IntArray(n) { inf } }
    val from = Array(1 shl n) { IntArray(n) { -1 } }

    fun row(mask: Int) = tspMasks.indexOf(mask)

    dp[1][0] = 0
    b.fill(row(1), 0, "0", "Start at city 0 with only city 0 visited: dp[{0}][0] = 0. Every tour begins here.")

    // Masks only grow, so ascending numeric order is already a valid processing order.
    for (mask in tspMasks) {
        for (last in 0 until n) {
            if (mask and (1 shl last) == 0) continue
            if (mask == 1 && last == 0) continue
            if (last == 0) continue                      // city 0 is only re-entered when closing the tour

            val without = mask and (1 shl last).inv()
            var best = inf
            var bestPrev = -1
            for (prev in 0 until n) {
                if (without and (1 shl prev) == 0) continue
                if (dp[without][prev] >= inf) continue
                val candidate = dp[without][prev] + cost[prev][last]
                if (candidate < best) {
                    best = candidate
                    bestPrev = prev
                }
            }
            if (best >= inf) continue

            dp[mask][last] = best
            from[mask][last] = bestPrev
            b.fill(
                row(mask), last, best.toString(),
                "dp[${tspMaskLabel(row(mask))}][$last] = dp[${tspMaskLabel(row(without))}][$bestPrev] + " +
                    "cost[$bestPrev][$last] = ${dp[without][bestPrev]} + ${cost[bestPrev][last]} = $best. " +
                    "The subset is one bit larger than the row it read from.",
            )
        }
    }

    var bestLast = 1
    var bestTotal = inf
    for (last in 1 until n) {
        val total = dp[full][last] + cost[last][0]
        if (total < bestTotal) {
            bestTotal = total
            bestLast = last
        }
    }

    // Walk the choices back to recover the tour, tracing the cells that produced it.
    val tour = mutableListOf<Int>()
    var mask = full
    var last = bestLast
    val traced = mutableListOf<Int>()
    while (last != -1 && mask != 0) {
        traced += b.key(row(mask), last)
        tour += last
        val prev = from[mask][last]
        mask = mask and (1 shl last).inv()
        last = prev
        if (last == 0) {
            traced += b.key(row(mask), 0)
            tour += 0
            break
        }
    }
    tour.reverse()
    traced.reverse()

    val tourText = (tour + 0).joinToString(" → ")
    b.trace(traced) { cell ->
        val cellMask = tspMasks[cell / 4]
        val cellLast = cell % 4
        "Traceback: dp[${tspMaskLabel(tspMasks.indexOf(cellMask))}][$cellLast]. Closing the tour costs " +
            "cost[$bestLast][0] = ${cost[bestLast][0]}, giving $tourText for a total of $bestTotal — " +
            "found by filling 20 cells instead of enumerating 6 tours, a gap that becomes 2ⁿ·n² vs n! as n grows."
    }

    return b.frames
}

private val partitionItems = listOf(1, 5, 11, 5)
private const val PARTITION_TOTAL = 22
private const val PARTITION_HALF = PARTITION_TOTAL / 2

// Partition reduces to subset-sum at half the total, so the table is the subset-sum table with the
// target derived rather than given. The status text carries the reduction, not just the recurrence.
private fun partitionFrames(): List<DpFrame> {
    val target = PARTITION_HALF
    val b = DpBuilder(rows = partitionItems.size + 1, cols = target + 1)
    val dp = Array(partitionItems.size + 1) { BooleanArray(target + 1) }

    dp[0][0] = true
    b.fill(0, 0, "T", "Total is $PARTITION_TOTAL — even, so a split is not ruled out. Each half must sum to $target. dp[0][0] = true: the empty subset makes 0.")
    for (t in 1..target) {
        b.fill(0, t, "·", "dp[0][$t] = false — nothing chosen yet, so $t is out of reach.")
    }

    for (i in 1..partitionItems.size) {
        val item = partitionItems[i - 1]
        for (t in 0..target) {
            val skip = dp[i - 1][t]
            val take = t >= item && dp[i - 1][t - item]
            dp[i][t] = skip || take
            val status = when {
                take && skip -> "dp[$i][$t]: reachable both ways — without the $item, or by taking it on top of ${t - item}."
                take -> "dp[$i][$t] = true by taking the $item: ${t - item} was already reachable."
                skip -> "dp[$i][$t] = true without the $item — the earlier items already reach $t."
                else -> "dp[$i][$t] = false: $t is unreachable from the first $i item(s)."
            }
            b.fill(i, t, if (dp[i][t]) "T" else "·", status)
        }
    }

    val chosen = mutableListOf<Int>()
    val path = mutableListOf<Int>()
    var t = target
    for (i in partitionItems.size downTo 1) {
        path += b.key(i, t)
        if (!dp[i - 1][t]) {
            chosen += partitionItems[i - 1]
            t -= partitionItems[i - 1]
        }
    }
    path += b.key(0, t)
    val first = chosen.reversed()
    val second = partitionItems.toMutableList().also { rest -> first.forEach { rest.remove(it) } }

    b.trace(path) { cell ->
        val r = cell / (target + 1)
        val c = cell % (target + 1)
        if (r == 0) {
            "Split found: {${first.joinToString(", ")}} = $target and {${second.joinToString(", ")}} = $target. " +
                "The table is O(n·T/2) cells — pseudo-polynomial, which is why this stays NP-complete."
        } else {
            "At dp[$r][$c]: " + (
                if (!dp[r - 1][c]) "unreachable without the ${partitionItems[r - 1]}, so it goes in the first half."
                else "still reachable without the ${partitionItems[r - 1]}, so it goes in the second half."
                )
        }
    }
    return b.frames
}

private const val LCSUB_A = "abcdxy"
private const val LCSUB_B = "zabcdw"

// The same table shape as LCS with one clause changed: a mismatch writes 0 instead of carrying the
// best neighbour forward. Every zero in this grid is the contiguity requirement being enforced.
private fun longestCommonSubstringFrames(): List<DpFrame> {
    val a = LCSUB_A
    val b = LCSUB_B
    val rows = a.length + 1
    val cols = b.length + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }
    var best = 0
    var endI = 0
    var endJ = 0

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val status: String
            if (i == 0 || j == 0) {
                status = "dp[$i][$j] = 0 — an empty prefix shares no suffix with anything."
            } else if (a[i - 1] == b[j - 1]) {
                dp[i][j] = dp[i - 1][j - 1] + 1
                status = "'${a[i - 1]}' == '${b[j - 1]}' → dp[${i - 1}][${j - 1}] + 1 = ${dp[i][j]}. The run grows by one."
                if (dp[i][j] > best) {
                    best = dp[i][j]
                    endI = i
                    endJ = j
                }
            } else {
                status = "'${a[i - 1]}' ≠ '${b[j - 1]}' → 0. Not max(up, left) — that would be LCS, and it would let the " +
                    "run survive a gap. Zero here is what makes the answer contiguous."
            }
            bld.fill(i, j, dp[i][j].toString(), status)
        }
    }

    // The answer sits wherever the maximum landed, so the traceback starts there rather than at the
    // bottom-right corner — reading the corner is the standard mistake and returns the common suffix.
    val path = mutableListOf<Int>()
    var i = endI
    var j = endJ
    repeat(best) {
        path += bld.key(i, j)
        i--
        j--
    }
    bld.trace(path) { cell ->
        val r = cell / cols
        val c = cell % cols
        val remaining = dp[r][c]
        if (remaining == 1) {
            "Back to the start of the run: \"${a.substring(endI - best, endI)}\", length $best. The maximum was at " +
                "dp[$endI][$endJ], not at dp[${rows - 1}][${cols - 1}] — the corner only ever holds the common suffix."
        } else {
            "dp[$r][$c] = $remaining, so '${a[r - 1]}' is part of the run; step diagonally back."
        }
    }
    return bld.frames
}

// ── Interview-prep pattern guides ────────────────────────────────────────────
// The algorithm topics already own one table each (edit distance, coin change, matrix chain). These
// four are the pattern-level framings: the take-it-or-leave-it decision, the grid proper, ranges
// filled by length, and a table whose rows are modes rather than positions.

private val knapsackItems = listOf(2 to 3, 3 to 4, 4 to 5, 5 to 6)   // (weight, value)
private const val KNAPSACK_CAPACITY = 5

private fun knapsackDpPatternFrames(): List<DpFrame> {
    val rows = knapsackItems.size + 1
    val cols = KNAPSACK_CAPACITY + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }

    for (i in 0 until rows) {
        for (c in 0 until cols) {
            val status: String
            dp[i][c] = when {
                i == 0 -> {
                    status = "dp[0][$c] = 0 — no items considered yet, so no value at any capacity."
                    0
                }
                else -> {
                    val (weight, value) = knapsackItems[i - 1]
                    val skip = dp[i - 1][c]
                    if (weight > c) {
                        status = "Item $i weighs $weight, more than capacity $c — the take branch is illegal, so " +
                            "dp[$i][$c] = dp[${i - 1}][$c] = $skip."
                        skip
                    } else {
                        val take = value + dp[i - 1][c - weight]
                        status = "Item $i (w$weight, v$value) at capacity $c: skip = $skip, take = $value + " +
                            "dp[${i - 1}][${c - weight}] = $take. " +
                            if (take > skip) "Taking wins." else "Skipping wins — the capacity it costs is worth more elsewhere."
                        maxOf(skip, take)
                    }
                }
            }
            bld.fill(i, c, dp[i][c].toString(), status)
        }
    }

    var i = knapsackItems.size
    var c = KNAPSACK_CAPACITY
    val path = mutableListOf(bld.key(i, c))
    val chosen = mutableListOf<Int>()
    while (i > 0) {
        val weight = knapsackItems[i - 1].first
        if (dp[i][c] == dp[i - 1][c]) {
            i--
        } else {
            chosen += i
            i--
            c -= weight
        }
        path.add(bld.key(i, c))
    }
    bld.trace(path) {
        "Traceback: a cell equal to the one above it means that item was skipped; a drop of its weight means it was " +
            "taken. Items ${chosen.reversed().joinToString(" and ")} give value ${dp[knapsackItems.size][KNAPSACK_CAPACITY]}."
    }
    return bld.frames
}

private val gridDpCosts = listOf(
    listOf(1, 3, 1, 2),
    listOf(1, 5, 1, 3),
    listOf(4, 2, 1, 1),
)

private fun gridDpPatternFrames(): List<DpFrame> {
    val rows = gridDpCosts.size
    val cols = gridDpCosts[0].size
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val cost = gridDpCosts[r][c]
            val status: String
            dp[r][c] = when {
                r == 0 && c == 0 -> {
                    status = "dp[0][0] = $cost — the start cell is its own cost, nothing to choose."
                    cost
                }
                r == 0 -> {
                    status = "Top row: the only way in is from the left, so dp[0][$c] = dp[0][${c - 1}] + $cost = " +
                        "${dp[0][c - 1] + cost}. No max or min appears until a cell has two ways in."
                    dp[0][c - 1] + cost
                }
                c == 0 -> {
                    status = "Left column: only reachable from above — dp[$r][0] = dp[${r - 1}][0] + $cost = " +
                        "${dp[r - 1][0] + cost}."
                    dp[r - 1][0] + cost
                }
                else -> {
                    val up = dp[r - 1][c]
                    val leftCell = dp[r][c - 1]
                    status = "dp[$r][$c] = $cost + min(up $up, left $leftCell) = ${cost + minOf(up, leftCell)}. Both " +
                        "dependencies are already written, which is the whole reason for sweeping rows left to right."
                    cost + minOf(up, leftCell)
                }
            }
            bld.fill(r, c, dp[r][c].toString(), status)
        }
    }

    var r = rows - 1
    var c = cols - 1
    val path = mutableListOf(bld.key(r, c))
    while (r > 0 || c > 0) {
        when {
            r == 0 -> c--
            c == 0 -> r--
            dp[r - 1][c] <= dp[r][c - 1] -> r--
            else -> c--
        }
        path.add(bld.key(r, c))
    }
    bld.trace(path.reversed()) {
        "Traceback from the corner: at each step, the neighbour whose value the cell was built from. Cheapest path " +
            "costs ${dp[rows - 1][cols - 1]}."
    }
    return bld.frames
}

private val balloonNums = listOf(3, 1, 5, 8)
private val balloonPadded = listOf(1) + balloonNums + listOf(1)

private fun intervalDpPatternFrames(): List<DpFrame> {
    val n = balloonPadded.size
    val bld = DpBuilder(n, n)
    val dp = Array(n) { IntArray(n) }

    bld.fill(0, 0, "0", "Burst balloons: dp[i][j] is the best score from the balloons strictly *between* i and j, " +
        "with i and j still standing. Ranges of width 1 hold nothing, so they score 0.")

    for (len in 2 until n) {
        for (i in 0..n - 1 - len) {
            val j = i + len
            var best = 0
            var bestK = i + 1
            for (k in i + 1 until j) {
                val candidate = dp[i][k] + dp[k][j] + balloonPadded[i] * balloonPadded[k] * balloonPadded[j]
                if (candidate > best) {
                    best = candidate
                    bestK = k
                }
            }
            dp[i][j] = best
            bld.fill(
                i, j, best.toString(),
                "Range ($i, $j), width $len: the balloon that bursts *last* is $bestK. Its neighbours are then i and " +
                    "j themselves — ${balloonPadded[i]} × ${balloonPadded[bestK]} × ${balloonPadded[j]} = " +
                    "${balloonPadded[i] * balloonPadded[bestK] * balloonPadded[j]} — plus the two sub-ranges " +
                    "dp[$i][$bestK] = ${dp[i][bestK]} and dp[$bestK][$j] = ${dp[bestK][j]}, both already filled " +
                    "because they are shorter. Total $best.",
            )
        }
    }

    bld.fill(
        0, n - 1, dp[0][n - 1].toString(),
        "dp[0][${n - 1}] = ${dp[0][n - 1]} over the whole padded array. Choosing what bursts *first* would leave two " +
            "halves whose neighbours are not yet known — choosing what bursts *last* is what makes the split " +
            "independent. Filling by length, shortest first, is the only order that has the sub-ranges ready.",
    )
    return bld.frames
}

private val stockPrices = listOf(1, 2, 3, 0, 2)
private val stockModes = listOf("hold", "sold", "rest")

private fun stateMachineDpPatternFrames(): List<DpFrame> {
    val days = stockPrices.size
    val bld = DpBuilder(rows = stockModes.size, cols = days)
    val hold = IntArray(days)
    val sold = IntArray(days)
    val rest = IntArray(days)

    hold[0] = -stockPrices[0]
    bld.fill(0, 0, hold[0].toString(), "Three modes after each day: holding a share, having just sold (which forces " +
        "tomorrow's cooldown), or resting free to buy. Day 0 holding means having bought at ${stockPrices[0]}, so " +
        "the balance is ${hold[0]}.")
    bld.fill(1, 0, "0", "Selling on day 0 is meaningless with nothing held — 0.")
    bld.fill(2, 0, "0", "Resting on day 0 costs nothing — 0. Every later cell is one of these three plus a move.")

    for (d in 1 until days) {
        val price = stockPrices[d]
        hold[d] = maxOf(hold[d - 1], rest[d - 1] - price)
        bld.fill(
            0, d, hold[d].toString(),
            "Day $d, price $price. hold = max(keep holding ${hold[d - 1]}, buy today from rest " +
                "${rest[d - 1]} − $price = ${rest[d - 1] - price}) = ${hold[d]}. Buying is only legal from *rest* — " +
                "that edge is the cooldown rule, and it is the entire difference from the unrestricted version.",
        )
        sold[d] = hold[d - 1] + price
        bld.fill(
            1, d, sold[d].toString(),
            "sold = hold(yesterday) + $price = ${hold[d - 1]} + $price = ${sold[d]}. There is no choice here: the " +
                "only way to be in *sold* is to have been holding and sold today.",
        )
        rest[d] = maxOf(rest[d - 1], sold[d - 1])
        bld.fill(
            2, d, rest[d].toString(),
            "rest = max(stay resting ${rest[d - 1]}, yesterday's sold ${sold[d - 1]}) = ${rest[d]}. Coming from sold " +
                "is what serves the cooldown day.",
        )
    }

    val answer = maxOf(sold[days - 1], rest[days - 1])
    bld.trace(listOf(bld.key(1, days - 1), bld.key(2, days - 1))) {
        "Answer = max(sold, rest) on the last day = $answer — never *hold*, since ending with an unsold share is " +
            "money left on the table. Three modes × ${days} days, each cell O(1): O(n) time, and rolling each row " +
            "to a scalar makes it O(1) space."
    }
    return bld.frames
}

private val prefix2dMatrix = listOf(
    listOf(3, 0, 1, 4),
    listOf(5, 6, 3, 2),
    listOf(1, 2, 0, 1),
)

// The table is one row and column bigger than the matrix: the zero border removes every bounds check
// from the inclusion-exclusion, which is most of what makes the query one line.
private fun prefix2dPatternFrames(): List<DpFrame> {
    val rows = prefix2dMatrix.size + 1
    val cols = prefix2dMatrix[0].size + 1
    val bld = DpBuilder(rows, cols)
    val p = Array(rows) { IntArray(cols) }

    for (i in 0 until rows) {
        for (j in 0 until cols) {
            if (i == 0 || j == 0) {
                bld.fill(i, j, "0", "The border stays 0. It is not part of the matrix — it exists so the recurrence " +
                    "below never has to test whether a neighbour is off the edge.")
                continue
            }
            val v = prefix2dMatrix[i - 1][j - 1]
            p[i][j] = v + p[i - 1][j] + p[i][j - 1] - p[i - 1][j - 1]
            bld.fill(
                i, j, p[i][j].toString(),
                "P[$i][$j] = the whole rectangle from the origin to here: cell $v + above ${p[i - 1][j]} + left " +
                    "${p[i][j - 1]} − corner ${p[i - 1][j - 1]} = ${p[i][j]}. The corner is subtracted because the " +
                    "strip above and the strip to the left both already contain it.",
            )
        }
    }

    // Query: rows 1..2, cols 1..3 of the original matrix (1-based in the padded table: 2..3, 2..4).
    val r1 = 2
    val c1 = 2
    val r2 = 3
    val c2 = 4
    val total = p[r2][c2] - p[r1 - 1][c2] - p[r2][c1 - 1] + p[r1 - 1][c1 - 1]
    bld.trace(
        listOf(bld.key(r2, c2), bld.key(r1 - 1, c2), bld.key(r2, c1 - 1), bld.key(r1 - 1, c1 - 1)),
    ) { cell ->
        when (cell) {
            bld.key(r2, c2) -> "Query the submatrix rows 1–2, cols 1–3. Start with the big rectangle P[$r2][$c2] = ${p[r2][c2]}."
            bld.key(r1 - 1, c2) -> "Subtract the strip above it, P[${r1 - 1}][$c2] = ${p[r1 - 1][c2]}."
            bld.key(r2, c1 - 1) -> "Subtract the strip to its left, P[$r2][${c1 - 1}] = ${p[r2][c1 - 1]}."
            else -> "Add back the corner P[${r1 - 1}][${c1 - 1}] = ${p[r1 - 1][c1 - 1]}, subtracted twice. Sum = $total " +
                "— four lookups, and the size of the rectangle never entered the cost."
        }
    }
    return bld.frames
}

private val rotateMatrix = listOf(
    listOf(1, 2, 3, 4),
    listOf(5, 6, 7, 8),
    listOf(9, 10, 11, 12),
    listOf(13, 14, 15, 16),
)

// Rotate 90° clockwise = transpose, then reverse each row. Both halves are pure index arithmetic, and
// writing them as two named steps is the habit that makes the coordinate mapping checkable.
private fun matrixTransformPatternFrames(): List<DpFrame> {
    val n = rotateMatrix.size
    val bld = DpBuilder(n, n)
    val m = Array(n) { r -> IntArray(n) { c -> rotateMatrix[r][c] } }

    for (r in 0 until n) {
        for (c in 0 until n) {
            bld.values[bld.key(r, c)] = m[r][c].toString()
        }
    }
    bld.fill(0, 0, m[0][0].toString(), "Rotate this ${n}×${n} matrix 90° clockwise in place. Element (r, c) must end " +
        "up at (c, ${n - 1}−r) — one mapping, applied as two simpler ones rather than juggled at once.")

    for (r in 0 until n) {
        for (c in r + 1 until n) {
            val a = m[r][c]
            val b = m[c][r]
            m[r][c] = b
            m[c][r] = a
            bld.values[bld.key(r, c)] = b.toString()
            bld.fill(
                c, r, a.toString(),
                "Transpose step: swap ($r, $c) with ($c, $r) — $a and $b trade places. Only the upper triangle is " +
                    "iterated; running over the whole matrix swaps every pair twice and leaves it unchanged.",
            )
        }
    }

    for (r in 0 until n) {
        var lo = 0
        var hi = n - 1
        while (lo < hi) {
            val a = m[r][lo]
            val b = m[r][hi]
            m[r][lo] = b
            m[r][hi] = a
            bld.values[bld.key(r, lo)] = b.toString()
            bld.fill(
                r, hi, a.toString(),
                "Reverse row $r: swap columns $lo and $hi. After the transpose, the columns are in the right order " +
                    "but backwards — reversing each row finishes the rotation.",
            )
            lo++
            hi--
        }
    }

    bld.trace((0 until n).map { bld.key(0, it) }) {
        "Top row is now ${(0 until n).joinToString(", ") { m[0][it].toString() }} — the old first *column*, bottom to " +
            "top. Transpose then reverse, O(n²) reads and writes, no second matrix. Anti-clockwise is the same two " +
            "steps with the reversal applied to columns instead."
    }
    return bld.frames
}

private val dpConfigs = mapOf(
    "prefix_2d_pattern" to DpConfig(
        rows = prefix2dMatrix.size + 1, cols = prefix2dMatrix[0].size + 1,
        rowHeader = { if (it == 0) "0" else "r${it - 1}" },
        colHeader = { if (it == 0) "0" else "c${it - 1}" },
        corner = "P",
        intro = "A 2D prefix table over a ${prefix2dMatrix.size}×${prefix2dMatrix[0].size} matrix, then one submatrix " +
            "query answered by the four highlighted lookups — big rectangle, two strips, and the corner added back.",
        build = ::prefix2dPatternFrames,
    ),
    "matrix_transform_pattern" to DpConfig(
        rows = rotateMatrix.size, cols = rotateMatrix.size,
        rowHeader = { "r$it" },
        colHeader = { "c$it" },
        corner = "",
        intro = "Rotating a 4×4 matrix 90° clockwise in place, as transpose-then-reverse. The cells change under the " +
            "same coordinates, which is what \"in place\" costs you in readability.",
        build = ::matrixTransformPatternFrames,
    ),
    "knapsack_dp_pattern" to DpConfig(
        rows = knapsackItems.size + 1, cols = KNAPSACK_CAPACITY + 1,
        rowHeader = { if (it == 0) "ε" else "w${knapsackItems[it - 1].first}·v${knapsackItems[it - 1].second}" },
        colHeader = { it.toString() },
        corner = "cap",
        intro = "0/1 knapsack, capacity ${KNAPSACK_CAPACITY}. Every cell is one skip-or-take decision, and the " +
            "traceback reads the chosen items back out of the table.",
        build = ::knapsackDpPatternFrames,
    ),
    "grid_dp_pattern" to DpConfig(
        rows = gridDpCosts.size, cols = gridDpCosts[0].size,
        rowHeader = { "r$it" },
        colHeader = { "c$it" },
        corner = "min",
        intro = "Minimum path sum through a ${gridDpCosts.size}×${gridDpCosts[0].size} grid, moving only right or " +
            "down. The first row and column have one way in; every other cell picks the cheaper of two.",
        build = ::gridDpPatternFrames,
    ),
    "interval_dp_pattern" to DpConfig(
        rows = balloonPadded.size, cols = balloonPadded.size,
        rowHeader = { balloonPadded[it].toString() },
        colHeader = { balloonPadded[it].toString() },
        corner = "i\\j",
        intro = "Burst balloons ${balloonNums.joinToString(", ")}, padded with 1s. Only the upper triangle fills, and " +
            "it fills by range length — every range needs the shorter ones inside it first.",
        build = ::intervalDpPatternFrames,
    ),
    "state_machine_dp_pattern" to DpConfig(
        rows = stockModes.size, cols = stockPrices.size,
        rowHeader = { stockModes[it] },
        colHeader = { stockPrices[it].toString() },
        corner = "mode",
        intro = "Stock trading with a cooldown, prices ${stockPrices.joinToString(", ")}. The rows are modes rather " +
            "than positions — the table *is* the state machine, one column per day.",
        build = ::stateMachineDpPatternFrames,
    ),
    "fibonacci_dp" to DpConfig(
        rows = 1, cols = 10,
        rowHeader = { "dp" }, colHeader = { it.toString() }, corner = "i",
        intro = "Bottom-up Fibonacci: each cell is the sum of the two before it — computed once, left to right. No recursion, no repeated work.",
        build = ::fibonacciDpFrames,
    ),
    "edit_distance" to DpConfig(
        rows = 4, cols = 4,
        rowHeader = { if (it == 0) "ε" else "cat"[it - 1].toString() },
        colHeader = { if (it == 0) "ε" else "cut"[it - 1].toString() },
        corner = "",
        intro = "Edit distance between \"cat\" and \"cut\". Each cell is the cheapest way to turn one prefix into the other; the traceback shows the actual edits.",
        build = ::editDistanceFrames,
    ),
    "longest_common_subsequence" to DpConfig(
        rows = 6, cols = 5,
        rowHeader = { if (it == 0) "ε" else "abcbd"[it - 1].toString() },
        colHeader = { if (it == 0) "ε" else "acbd"[it - 1].toString() },
        corner = "",
        intro = "Longest common subsequence of \"abcbd\" and \"acbd\". On a match the diagonal grows; otherwise carry the best neighbour. Traceback recovers the subsequence.",
        build = ::lcsFrames,
    ),
    "coin_change" to DpConfig(
        rows = coinChangeCoins.size + 1, cols = COIN_CHANGE_AMOUNT + 1,
        rowHeader = { if (it == 0) "ε" else coinChangeCoins[it - 1].toString() },
        colHeader = { it.toString() },
        corner = "¢",
        intro = "Fewest coins to make each amount, using denominations {1, 3, 4}. Each row adds a coin type; ∞ means unreachable. Traceback shows which coins make the target.",
        build = ::coinChangeFrames,
    ),
    "rod_cutting" to DpConfig(
        rows = rodPrices.size + 1, cols = ROD_LENGTH + 1,
        rowHeader = { if (it == 0) "ε" else it.toString() },
        colHeader = { it.toString() },
        corner = "len",
        intro = "Rod cutting — prices (1,5,8,9,10) for lengths 1..5. Each row allows one more piece length; the traceback shows which cuts produce the best revenue.",
        build = ::rodCuttingFrames,
    ),
    "longest_increasing_subsequence" to DpConfig(
        rows = 1, cols = lisInput.size,
        rowHeader = { "dp" },
        colHeader = { lisInput[it].toString() },
        corner = "a[i]",
        intro = "Longest increasing subsequence of (3,1,4,2,6,5). Each cell is the longest run ending at that element — the answer is the largest cell, not the last one.",
        build = ::lisFrames,
    ),
    "matrix_chain_multiplication" to DpConfig(
        rows = chainDims.size - 1, cols = chainDims.size - 1,
        rowHeader = { "A${it + 1}" },
        colHeader = { "A${it + 1}" },
        corner = "i\\j",
        intro = "Matrix chain with dimensions 10×30, 30×5, 5×60. The table fills along diagonals — by chain length — so every sub-chain is solved before the chains that contain it. Cells below the diagonal stay empty.",
        build = ::matrixChainFrames,
    ),
    "knapsack_01" to DpConfig(
        rows = knapWeights.size + 1, cols = KNAP_CAPACITY + 1,
        rowHeader = { if (it == 0) "ε" else knapWeights[it - 1].toString() },
        colHeader = { it.toString() },
        corner = "wt",
        intro = "0/1 knapsack — items (wt, val) = (1,6), (2,10), (3,12), capacity 5. Each cell is the best value achievable; the traceback marks the items chosen.",
        build = ::knapsackFrames,
    ),
    "bitmask_dp" to DpConfig(
        rows = 8, cols = 4,
        rowHeader = { tspMaskLabel(it) },
        colHeader = { "at $it" },
        corner = "visited",
        intro = "Held-Karp TSP over four cities. A row is a subset of visited cities encoded as a bitmask, a " +
            "column is the city you are standing on — 2ⁿ·n states instead of n! tours.",
        build = ::bitmaskDpFrames,
    ),
    "subset_sum" to DpConfig(
        rows = 5, cols = 10,
        rowHeader = { if (it == 0) "ε" else listOf(3, 4, 5, 2)[it - 1].toString() },
        colHeader = { it.toString() },
        corner = "item",
        intro = "Can any subset of {3, 4, 5, 2} total exactly 9? Each cell is a yes/no rather than a number, and " +
            "the traceback recovers which items were actually chosen.",
        build = ::subsetSumFrames,
    ),
    "partition_problem" to DpConfig(
        rows = partitionItems.size + 1, cols = PARTITION_HALF + 1,
        rowHeader = { if (it == 0) "ε" else partitionItems[it - 1].toString() },
        colHeader = { it.toString() },
        corner = "item",
        intro = "Can {1, 5, 11, 5} be split into two equal halves? The total is 22, so the question becomes whether " +
            "any subset reaches exactly 11 — subset-sum with the target derived from the input rather than given.",
        build = ::partitionFrames,
    ),
    "longest_common_substring" to DpConfig(
        rows = LCSUB_A.length + 1, cols = LCSUB_B.length + 1,
        rowHeader = { if (it == 0) "ε" else LCSUB_A[it - 1].toString() },
        colHeader = { if (it == 0) "ε" else LCSUB_B[it - 1].toString() },
        corner = "",
        intro = "Longest common substring of \"$LCSUB_A\" and \"$LCSUB_B\". A cell is the longest common *suffix* of the two " +
            "prefixes, so a mismatch resets it to zero — and the answer is the largest cell anywhere, not the corner.",
        build = ::longestCommonSubstringFrames,
    ),
)

private fun dpConfigFor(topicId: String): DpConfig =
    dpConfigs[topicId] ?: dpConfigs.getValue("fibonacci_dp")

internal val dpGridTopicIds: Set<String> get() = dpConfigs.keys

// Also checks the declared table shape against what the builder actually wrote — a cols mismatch
// silently reshapes every cell key, which is invisible until the grid renders wrong.
internal fun dpGridFrameCount(topicId: String): Int {
    val config = dpConfigFor(topicId)
    val frames = config.build()
    val maxKey = frames.flatMap { it.values.keys }.maxOrNull() ?: 0
    require(maxKey < config.rows * config.cols) {
        "$topicId writes cell key $maxKey, outside a ${config.rows}×${config.cols} table"
    }
    return frames.size
}

@Composable
fun DpGridSection(topicId: String) {
    val config = remember(topicId) { dpConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    // Not every table has a traceback pass (1-D tables, and interval DP like matrix chain), so the
    // legend follows what the frames actually contain rather than the table's shape.
    val hasTraceback = remember(frames) { frames.any { it.traced.isNotEmpty() } }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DpGrid(config = config, frame = frame, modifier = Modifier.padding(top = 14.dp))

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DpLegend(ActiveCell, "Current")
                DpLegend(FilledCell, "Filled")
                if (hasTraceback) DpLegend(TracedCell, "Traceback")
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun DpLegend(color: Color, label: String) {
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
private fun DpGrid(config: DpConfig, frame: DpFrame, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header row: corner + column headers.
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell(config.corner)
            for (c in 0 until config.cols) HeaderCell(config.colHeader(c))
        }
        for (r in 0 until config.rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderCell(config.rowHeader(r))
                for (c in 0 until config.cols) {
                    val key = r * config.cols + c
                    DataCell(
                        value = frame.values[key],
                        color = when {
                            frame.active == key -> ActiveCell
                            key in frame.traced -> TracedCell
                            frame.values.containsKey(key) -> FilledCell.copy(alpha = 0.30f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DataCell(value: String?, color: Color) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .padding(1.dp)
            .background(color, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value ?: "",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
