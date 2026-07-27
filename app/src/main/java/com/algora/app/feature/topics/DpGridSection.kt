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

private val dpConfigs = mapOf(
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
    "subset_sum" to DpConfig(
        rows = 5, cols = 10,
        rowHeader = { if (it == 0) "ε" else listOf(3, 4, 5, 2)[it - 1].toString() },
        colHeader = { it.toString() },
        corner = "item",
        intro = "Can any subset of {3, 4, 5, 2} total exactly 9? Each cell is a yes/no rather than a number, and " +
            "the traceback recovers which items were actually chosen.",
        build = ::subsetSumFrames,
    ),
)

private fun dpConfigFor(topicId: String): DpConfig =
    dpConfigs[topicId] ?: dpConfigs.getValue("fibonacci_dp")

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
