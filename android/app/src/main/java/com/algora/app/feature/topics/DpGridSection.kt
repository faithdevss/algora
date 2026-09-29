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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors

// ── DP tabulation grid ───────────────────────────────────────────────────────
// Fills a DP table cell-by-cell (one PlaybackTransport step each), then, for 2-D problems, walks the
// traceback path. Each frame snapshots the values written so far + the active/traced cells.

// One candidate in a cell's recurrence — edit distance's replace/delete/insert — shown as a card under
// the grid, the chosen one outlined.
private class DpOption(val arrow: String, val name: String, val formula: String, val chosen: Boolean)

private class DpFrame(
    val values: Map<Int, String>,   // cellKey -> written value
    val active: Int?,               // cell being written or traced now
    val traced: Set<Int>,           // traceback cells revealed so far
    val status: String,
    // The rest is the narrated design (docs mocks). A table that leaves it empty still gets the grid;
    // the headline falls back to `status`.
    val reads: Set<Int> = emptySet(),
    val options: List<DpOption> = emptyList(),
    val chips: List<StoryChip> = emptyList(),
    // `{…}` marks the term drawn in its tone's colour (see LabStory).
    val headline: String? = null,
    val body: String? = null,
    // The recurrence with this cell's numbers, in a strip under the grid.
    val formula: String? = null,
    // Labelled lines of arithmetic under the grid: matrix chain's splits, Fibonacci's call count.
    val formulaRows: List<StoryFormulaRow> = emptyList(),
    // Cells holding the answer, in violet.
    val answer: Set<Int> = emptySet(),
    // Tiles under the grid spelling out the result (LCS's letters).
    val strip: List<StoryCell> = emptyList(),
    // Replaces the config's header note for this step ("d(1,3) = 25").
    val note: String? = null,
    // Several cells written in one step (matrix chain's diagonal of zeros).
    val actives: Set<Int> = emptySet(),
)

private class DpConfig(
    val rows: Int,
    val cols: Int,
    val rowHeader: (Int) -> String,
    val colHeader: (Int) -> String,
    val corner: String,
    val intro: String,
    val build: () -> List<DpFrame>,
    // Card heading, e.g. "CAT → CUT". A table with one shows it in place of the intro paragraph.
    val title: String? = null,
    // Right of the heading; defaults to dp[i] / dp[i][j].
    val note: String? = null,
    // Equal tiles over the grid naming the inputs (matrix chain's "A1 10×30").
    val pills: List<String> = emptyList(),
    // In display order; an entry shows only while its tone is on screen.
    val legend: List<Pair<StoryTone, String>> = DpLegend,
)

private val DpLegend = listOf(
    StoryTone.Active to "Current",
    StoryTone.Path to "Reads from",
    StoryTone.Done to "Traceback",
    StoryTone.Answer to "Answer",
)

private class DpBuilder(val rows: Int, val cols: Int) {
    val frames = mutableListOf<DpFrame>()
    val values = LinkedHashMap<Int, String>()
    fun key(r: Int, c: Int) = r * cols + c
    fun fill(
        r: Int,
        c: Int,
        value: String,
        status: String = "",
        reads: Set<Int> = emptySet(),
        options: List<DpOption> = emptyList(),
        chips: List<StoryChip> = emptyList(),
        headline: String? = null,
        body: String? = null,
        formula: String? = null,
        formulaRows: List<StoryFormulaRow> = emptyList(),
        note: String? = null,
    ) {
        values[key(r, c)] = value
        frames.add(
            DpFrame(
                values.toMap(), key(r, c), emptySet(), status.ifEmpty { storyPlain(headline.orEmpty()) },
                reads, options, chips, headline, body, formula, formulaRows, note = note,
            ),
        )
    }
    // A step that writes nothing new: an intro, a closing answer, a whole traceback at once.
    fun frame(
        headline: String,
        body: String,
        active: Int? = null,
        actives: Set<Int> = emptySet(),
        traced: Set<Int> = emptySet(),
        reads: Set<Int> = emptySet(),
        answer: Set<Int> = emptySet(),
        chips: List<StoryChip> = emptyList(),
        formula: String? = null,
        formulaRows: List<StoryFormulaRow> = emptyList(),
        strip: List<StoryCell> = emptyList(),
        note: String? = null,
    ) {
        frames.add(
            DpFrame(
                values.toMap(), active, traced, storyPlain(headline), reads, emptyList(), chips, headline, body,
                formula, formulaRows, answer, strip, note, actives,
            ),
        )
    }
    fun trace(cells: List<Int>, statusFor: (Int) -> String) {
        val traced = mutableSetOf<Int>()
        cells.forEach { cell ->
            traced.add(cell)
            frames.add(DpFrame(values.toMap(), cell, traced.toSet(), statusFor(cell)))
        }
    }
}

// dp[i] = dp[i-1] + dp[i-2], filled left to right. 1-D, no traceback. The call count is what the naive
// recursion would spend on the same f(i), the thing the table saves.
private fun fibonacciDpFrames(): List<DpFrame> {
    val n = 9
    val b = DpBuilder(rows = 1, cols = n + 1)
    val dp = LongArray(n + 1)
    val calls = IntArray(n + 1)
    for (i in 0..n) {
        dp[i] = if (i < 2) i.toLong() else dp[i - 1] + dp[i - 2]
        calls[i] = if (i < 2) 1 else calls[i - 1] + calls[i - 2] + 1
        if (i < 2) {
            b.fill(
                0, i, "${dp[i]}",
                chips = listOf(StoryChip("i", "$i")),
                formula = "dp[$i] = {${dp[i]}}",
                headline = "dp[$i] = {${dp[i]}} is a base case, written without looking anything up.",
                body = if (i == 0) "The recurrence needs two earlier cells, so the first two are given."
                else "With dp[0] and dp[1] in place, every later cell has both of its inputs.",
            )
        } else {
            b.fill(
                0, i, "${dp[i]}",
                reads = setOf(i - 1, i - 2),
                chips = listOf(StoryChip("i", "$i")),
                formula = "dp[$i] = {p:${dp[i - 1]}} + {p:${dp[i - 2]}} = {${dp[i]}}",
                formulaRows = listOf(
                    StoryFormulaRow("recursive f($i)", "${calls[i]} calls"),
                    StoryFormulaRow("table", "${i + 1} cells, each once"),
                ),
                headline = "dp[$i] adds the two cells before it: ${dp[i - 1]} + ${dp[i - 2]} = {${dp[i]}}.",
                body = "Each value is computed once and reused, compared with ${calls[i]} calls for the recursive version.",
            )
        }
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
    fun edits(n: Int) = when (n) { 0 -> "no edits"; 1 -> "one edit"; else -> "$n edits" }
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val status: String
            val from = "\"${a.take(i)}\""
            val to = "\"${bWord.take(j)}\""
            val chips = mutableListOf(StoryChip("i", "$i"), StoryChip("j", "$j"))
            var reads = emptySet<Int>()
            var options = emptyList<DpOption>()
            val body: String
            dp[i][j] = when {
                i == 0 && j == 0 -> {
                    status = "dp[0][0] = 0  (two empty strings)"
                    body = "Two empty strings already match, so the table starts from zero."
                    0
                }
                i == 0 -> {
                    status = "dp[0][$j] = $j  (turn ε into first $j chars)"
                    reads = setOf(bld.key(0, j - 1))
                    body = "Starting from the empty string, every letter has to be inserted — one more than the cell to the left."
                    j
                }
                j == 0 -> {
                    status = "dp[$i][0] = $i  (delete $i chars)"
                    reads = setOf(bld.key(i - 1, 0))
                    body = "Reaching the empty string means deleting every letter — one more than the cell above."
                    i
                }
                else -> {
                    val match = a[i - 1] == bWord[j - 1]
                    val cost = if (match) 0 else 1
                    val diag = dp[i - 1][j - 1] + cost
                    val up = dp[i - 1][j] + 1
                    val left = dp[i][j - 1] + 1
                    val v = minOf(diag, up, left)
                    // First minimum in replace → delete → insert order is the one the traceback takes.
                    val pick = listOf(diag, up, left).indexOf(v)
                    options = listOf(
                        DpOption("↖", if (match) "keep" else "replace", "${dp[i - 1][j - 1]} + $cost = $diag", pick == 0),
                        DpOption("↑", "delete", "${dp[i - 1][j]} + 1 = $up", pick == 1),
                        DpOption("←", "insert", "${dp[i][j - 1]} + 1 = $left", pick == 2),
                    )
                    reads = setOf(bld.key(i - 1, j - 1), bld.key(i - 1, j), bld.key(i, j - 1))
                    chips += StoryChip(
                        "${a[i - 1]} vs ${bWord[j - 1]}",
                        if (match) "match" else "differ",
                        if (match) StoryTone.Done else StoryTone.Warn,
                    )
                    val ties = listOf(diag, up, left).count { it == v }
                    val moveName = listOf(if (match) "Keeping the letter" else "Replacing", "Deleting", "Inserting")[pick]
                    val source = listOf("the diagonal", "the cell above", "the cell to the left")[pick]
                    body = if (match) {
                        "The letters match, so the diagonal carries over for free. $moveName from $source is cheapest."
                    } else {
                        "The letters differ, so every move costs 1. $moveName from $source is " +
                            if (ties > 1) "tied for cheapest." else "cheapest."
                    }
                    status = if (match) {
                        "'${a[i - 1]}' == '${bWord[j - 1]}' → dp[${i - 1}][${j - 1}] = ${dp[i - 1][j - 1]}"
                    } else {
                        "'${a[i - 1]}' ≠ '${bWord[j - 1]}' → 1 + min(diag, up, left) = $v"
                    }
                    v
                }
            }
            bld.fill(
                i, j, dp[i][j].toString(), status,
                reads = reads,
                options = options,
                chips = chips,
                headline = "dp[$i][$j] = {${dp[i][j]}}: turning ${if (i == 0) "\"\"" else from} into " +
                    "${if (j == 0) "\"\"" else to} takes ${edits(dp[i][j])}.",
                body = body,
            )
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
            if (i == 0 || j == 0) {
                bld.fill(
                    i, j, "0",
                    chips = listOf(StoryChip("prefix", if (i == 0) "ε" else "\"${a.take(i)}\""), StoryChip("vs", if (j == 0) "ε" else "\"${bWord.take(j)}\"")),
                    headline = "An empty prefix shares {0} letters with anything.",
                    body = "Row ε and column ε stay zero, so every other cell has a neighbour to read.",
                )
                continue
            }
            val x = a[i - 1]
            val y = bWord[j - 1]
            val pair = StoryChip("$x vs $y", if (x == y) "match" else "differ", if (x == y) StoryTone.Done else StoryTone.Warn)
            val prefixes = StoryChip("prefixes", "${a.take(i)} · ${bWord.take(j)}")
            if (x == y) {
                dp[i][j] = dp[i - 1][j - 1] + 1
                bld.fill(
                    i, j, "${dp[i][j]}",
                    reads = setOf(bld.key(i - 1, j - 1)),
                    chips = listOf(pair, prefixes),
                    formula = "diagonal {p:${dp[i - 1][j - 1]}} + 1 = {${dp[i][j]}}",
                    headline = "'$x' ends both prefixes, so it extends the diagonal: {${dp[i][j]}}.",
                    body = "A match joins the best subsequence of everything before both letters.",
                )
            } else {
                val up = dp[i - 1][j]
                val left = dp[i][j - 1]
                dp[i][j] = maxOf(up, left)
                bld.fill(
                    i, j, "${dp[i][j]}",
                    reads = setOf(bld.key(i - 1, j), bld.key(i, j - 1)),
                    chips = listOf(pair, prefixes),
                    formula = "max( up {p:$up} , left {p:$left} ) = {${dp[i][j]}}",
                    headline = "'$x' and '$y' differ, so the better neighbour carries: {${dp[i][j]}}.",
                    body = "One of the two letters is not in the subsequence. Keep whichever drop leaves more.",
                )
            }
        }
    }
    // Every step back from the corner: diagonals are matched letters, the rest skip one.
    var i = rows - 1
    var j = cols - 1
    val path = mutableListOf<Int>()
    val letters = StringBuilder()
    while (i > 0 && j > 0) {
        path += bld.key(i, j)
        when {
            a[i - 1] == bWord[j - 1] -> { letters.append(a[i - 1]); i--; j-- }
            dp[i - 1][j] >= dp[i][j - 1] -> i--
            else -> j--
        }
    }
    val lcs = letters.reverse().toString().uppercase()
    val corner = bld.key(rows - 1, cols - 1)
    bld.frame(
        headline = "Following the diagonals back spells out $lcs.",
        body = "Each diagonal step is a matched letter.",
        traced = path.toSet() - corner,
        answer = setOf(corner),
        chips = listOf(StoryChip("LCS", lcs), StoryChip("length", "${lcs.length}", StoryTone.Answer)),
        strip = lcs.map { StoryCell(it.toString(), StoryTone.Done) },
    )
    return bld.frames
}

private val coinChangeCoins = intArrayOf(1, 3, 4)
private const val COIN_CHANGE_AMOUNT = 6
private const val INF = Int.MAX_VALUE / 2

private fun coinFmt(x: Int): String = if (x >= INF) "∞" else x.toString()

private fun countWord(n: Int): String = if (n < 6) listOf("zero", "one", "two", "three", "four", "five")[n] else "$n"

// dp[i][a] = fewest coins for amount a using the first i denominations (unbounded).
private fun coinChangeFrames(): List<DpFrame> {
    val coins = coinChangeCoins
    val amount = COIN_CHANGE_AMOUNT
    val rows = coins.size + 1
    val cols = amount + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) { INF } }
    // What greedy (largest coin first) spends on the target, for the closing contrast.
    var greedyLeft = amount
    val greedy = mutableListOf<Int>()
    for (coin in coins.sortedDescending()) while (greedyLeft >= coin) { greedy += coin; greedyLeft -= coin }
    // The coins the table settles on, known up front so the last cell can name them.
    fun used(): List<Int> {
        var i = rows - 1
        var a = cols - 1
        val out = mutableListOf<Int>()
        while (a > 0 && i > 0) if (dp[i][a] == dp[i - 1][a]) i-- else { out += coins[i - 1]; a -= coins[i - 1] }
        return out
    }
    for (i in 0 until rows) {
        for (a in 0 until cols) {
            val chips = listOf(StoryChip("coin", if (i == 0) "none" else "${coins[i - 1]}"), StoryChip("amount", "$a"))
            if (a == 0) {
                dp[i][0] = 0
                bld.fill(
                    i, 0, "0", chips = chips,
                    headline = "Making 0 takes {0} coins.",
                    body = if (i == 0) "Row ε has no coins at all, so every other amount in it is out of reach."
                    else "Every row starts from zero: no coins make nothing.",
                )
                continue
            }
            if (i == 0) {
                bld.fill(
                    0, a, "∞", chips = chips,
                    headline = "With no coins, $a is out of reach: {∞}.",
                    body = "∞ marks an amount this row cannot make.",
                )
                continue
            }
            val coin = coins[i - 1]
            val skip = dp[i - 1][a]
            if (coin > a) {
                dp[i][a] = skip
                bld.fill(
                    i, a, coinFmt(skip), chips = chips,
                    reads = setOf(bld.key(i - 1, a)),
                    formula = "$coin > $a, keep {p:${coinFmt(skip)}}",
                    headline = "A $coin is too big for $a, so the row above carries down: {${coinFmt(skip)}}.",
                    body = "Without room for this coin, the answer is whatever the smaller coins managed.",
                )
                continue
            }
            val rest = dp[i][a - coin]
            val use = if (rest < INF) rest + 1 else INF
            dp[i][a] = minOf(skip, use)
            val v = coinFmt(dp[i][a])
            val last = i == rows - 1 && a == cols - 1
            val headline = when {
                use < skip && skip >= INF -> "A $coin reaches $a: 1 + ${coinFmt(rest)} = {$v} coins."
                use < skip -> "Using a $coin: 1 + ${coinFmt(rest)} = {$v} coins, fewer than ${coinFmt(skip)}."
                use == skip -> "Using a $coin ties with skipping it: {$v} either way."
                else -> "Using a $coin costs 1 + ${coinFmt(rest)} = ${coinFmt(use)} coins. Skipping it keeps {$v}."
            }
            val body = if (last) {
                val coinsUsed = used()
                "So $a takes ${countWord(coinsUsed.size)} coins, ${coinsUsed.joinToString(" + ")}. That is the answer greedy missed."
            } else {
                "Each cell is the fewer of skipping this coin or using one more of it. The use reads from the same row, since coins repeat."
            }
            bld.fill(
                i, a, v, chips = chips,
                reads = setOf(bld.key(i - 1, a), bld.key(i, a - coin)),
                formula = "min( skip {p:${coinFmt(skip)}} , use 1 + {p:${coinFmt(rest)}} ) = {$v}",
                headline = headline,
                body = body,
            )
        }
    }
    val corner = bld.key(rows - 1, cols - 1)
    if (dp[rows - 1][cols - 1] < INF) {
        var i = rows - 1
        var a = cols - 1
        val path = mutableSetOf<Int>()
        while (a > 0 && i > 0) {
            path += bld.key(i, a)
            if (dp[i][a] == dp[i - 1][a]) i-- else a -= coins[i - 1]
        }
        path += bld.key(i, a)
        val coinsUsed = used()
        bld.frame(
            headline = "${coinsUsed.joinToString(" + ")} makes $amount with {v:${coinsUsed.size}} coins.",
            body = "Greedy grabs the ${greedy.first()} first and needs ${greedy.joinToString(" + ")}, ${greedy.size} coins. " +
                "The table tried every coin at every amount.",
            traced = path - corner,
            answer = setOf(corner),
            chips = listOf(StoryChip("coins", coinsUsed.joinToString(" + ")), StoryChip("count", "${coinsUsed.size}", StoryTone.Answer)),
        )
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
            val chips = listOf(StoryChip("item", if (i == 0) "none" else "$i"), StoryChip("capacity", "$w"))
            if (i == 0 || w == 0) {
                bld.fill(
                    i, w, "0", chips = chips,
                    headline = if (i == 0) "No items yet: capacity $w holds {0}." else "Capacity 0 holds nothing: {0}.",
                    body = if (i == 0) "Row ε is the baseline every item is measured against."
                    else "Column 0 stays zero, so a take that uses up the whole room reads a real cell.",
                )
                continue
            }
            val weight = knapWeights[i - 1]
            val value = knapValues[i - 1]
            val skip = dp[i - 1][w]
            if (weight > w) {
                dp[i][w] = skip
                bld.fill(
                    i, w, "$skip", chips = chips,
                    reads = setOf(bld.key(i - 1, w)),
                    formula = "item $i too heavy, keep {p:$skip}",
                    headline = "Item $i weighs $weight, more than $w. The row above carries: {$skip}.",
                    body = "An item that does not fit leaves the answer to the items before it.",
                )
                continue
            }
            val room = dp[i - 1][w - weight]
            val take = value + room
            dp[i][w] = maxOf(skip, take)
            val v = dp[i][w]
            bld.fill(
                i, w, "$v", chips = chips,
                reads = setOf(bld.key(i - 1, w), bld.key(i - 1, w - weight)),
                formula = "max( skip {p:$skip} , take $value + {p:$room} ) = {$v}",
                headline = when {
                    take > skip -> "Taking item $i leaves room ${w - weight}, worth $room. $value + $room = {$v} beats $skip."
                    take == skip -> "Taking item $i ties with skipping it: {$v}."
                    else -> "Skipping item $i keeps {$v}. Taking it only makes $take."
                },
                body = "Each cell picks the better of skipping the item or taking it once.",
            )
        }
    }
    var i = rows - 1
    var w = cols - 1
    val path = mutableSetOf<Int>()
    val taken = mutableListOf<Int>()
    while (i > 0) {
        path += bld.key(i, w)
        if (dp[i][w] != dp[i - 1][w]) { taken += i; w -= knapWeights[i - 1] }
        i--
    }
    val corner = bld.key(rows - 1, cols - 1)
    val best = dp[rows - 1][cols - 1]
    val names = taken.reversed()
    bld.frame(
        headline = "Items ${names.joinToString(" and ")} fill the sack for {v:$best}.",
        body = "Where a cell differs from the one above it, that item was taken; step left by its weight.",
        traced = path - corner,
        answer = setOf(corner),
        chips = listOf(StoryChip("take", names.joinToString(" + ")), StoryChip("value", "$best", StoryTone.Answer)),
    )
    return bld.frames
}

private val rodPrices = intArrayOf(1, 5, 8, 9, 10)
private const val ROD_LENGTH = 5

// "1", "1 and 2", "1 to 3": the piece lengths a row allows.
private fun rodPieces(upTo: Int) = when (upTo) {
    1 -> "1"
    2 -> "1 and 2"
    else -> "1 to $upTo"
}

// dp[i][l] = best revenue for a rod of length l cutting only pieces of length <= i.
private fun rodCuttingFrames(): List<DpFrame> {
    val rows = rodPrices.size + 1
    val cols = ROD_LENGTH + 1
    val bld = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }
    for (i in 0 until rows) {
        for (l in 0 until cols) {
            val chips = listOf(StoryChip("piece", if (i == 0) "none" else "$i"), StoryChip("rod", "$l"))
            if (i == 0 || l == 0) {
                bld.fill(
                    i, l, "0", chips = chips,
                    headline = if (i == 0) "No pieces allowed: a rod of $l sells for {0}." else "A rod of 0 sells for {0}.",
                    body = "Each row allows one more piece length than the row above it.",
                )
                continue
            }
            val price = rodPrices[i - 1]
            val skip = dp[i - 1][l]
            if (i > l) {
                dp[i][l] = skip
                bld.fill(
                    i, l, "$skip", chips = chips,
                    reads = setOf(bld.key(i - 1, l)),
                    formula = "piece $i > rod $l, keep {p:$skip}",
                    headline = "A $i is longer than the rod, so the row above carries: {$skip}.",
                    body = "Only pieces that fit can change the answer.",
                )
                continue
            }
            val rest = dp[i][l - i]
            val cut = price + rest
            dp[i][l] = maxOf(skip, cut)
            val v = dp[i][l]
            bld.fill(
                i, l, "$v", chips = chips,
                reads = setOf(bld.key(i - 1, l), bld.key(i, l - i)),
                formula = "max( skip {p:$skip} , cut $price + {p:$rest} ) = {$v}",
                headline = when {
                    cut > skip && l == i -> "Selling the whole $l as one piece earns \$$price: {$v}."
                    cut > skip -> "Cut a $i for \$$price and sell the leftover ${l - i} for \$$rest: {$v}."
                    cut == skip -> "Cutting a $i ties with skipping it: {$v}."
                    else -> "Skipping keeps {$v}. Cutting a $i only makes $cut."
                },
                body = if (cut > skip && i > 1) {
                    "That beats $skip from pieces of ${rodPieces(i - 1)} only. Pieces can repeat, so the take reads from the same row."
                } else {
                    "Pieces can repeat, so the take reads from the same row."
                },
            )
        }
    }
    var i = rows - 1
    var l = cols - 1
    val path = mutableSetOf<Int>()
    val cuts = mutableListOf<Int>()
    while (i > 0 && l > 0) {
        path += bld.key(i, l)
        if (dp[i][l] == dp[i - 1][l]) i-- else { cuts += i; l -= i }
    }
    val corner = bld.key(rows - 1, cols - 1)
    val best = dp[rows - 1][cols - 1]
    val pieces = cuts.sorted()
    bld.frame(
        headline = "Pieces of ${pieces.joinToString(" and ")} sell for {v:\$$best}.",
        body = "Where a cell beats the one above it, that piece was cut; step left by its length.",
        traced = path - corner,
        answer = setOf(corner),
        chips = listOf(StoryChip("cuts", pieces.joinToString(" + ")), StoryChip("revenue", "\$$best", StoryTone.Answer)),
    )
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

private fun chainName(i: Int, j: Int) = (i..j).joinToString(" ") { "A${it + 1}" }

// A split written as parentheses: "(A1 A2) A3". A lone matrix needs none.
private fun chainGroup(i: Int, j: Int) = if (i == j) "A${i + 1}" else "(${chainName(i, j)})"

// dp[i][j] = fewest scalar multiplications to multiply matrices i..j. Filled by chain length, so the
// table populates diagonally rather than row by row; a chain of three tries its splits one at a time.
private fun matrixChainFrames(): List<DpFrame> {
    val n = chainDims.size - 1
    val p = chainDims
    val bld = DpBuilder(rows = n, cols = n)
    val dp = Array(n) { IntArray(n) }
    for (i in 0 until n) bld.values[bld.key(i, i)] = "0"
    bld.frame(
        headline = "A single matrix costs {0}: there is nothing to multiply.",
        body = "The table fills by chain length, so every shorter chain is ready before a longer one needs it.",
        actives = (0 until n).map { bld.key(it, it) }.toSet(),
        chips = listOf(StoryChip("length", "1")),
    )
    for (len in 2..n) {
        for (i in 0..n - len) {
            val j = i + len - 1
            val tried = mutableListOf<Triple<Int, Int, Int>>()   // split k, join cost, total
            var best = Int.MAX_VALUE
            var split = i
            for (k in i until j) {
                val join = p[i] * p[k + 1] * p[j + 1]
                val total = dp[i][k] + dp[k + 1][j] + join
                tried += Triple(k, join, total)
                // Every split after the first gets its own step, so the comparison is visible.
                val better = total < best
                if (better) { best = total; split = k }
                val rows = tried.map { (tk, tj, tt) ->
                    val current = tk == k
                    val left = if (current) "{p:${dp[i][tk]}}" else "${dp[i][tk]}"
                    val right = if (current) "{p:${dp[tk + 1][j]}}" else "${dp[tk + 1][j]}"
                    StoryFormulaRow("k = ${tk + 1}", "$left + $right + $tj = ${if (current) "{$tt}" else "$tt"}")
                }
                val chips = mutableListOf(StoryChip("length", "$len"))
                val headline: String
                val body: String
                if (len == 2) {
                    headline = "${chainName(i, j)} has one split: ${p[i]} × ${p[k + 1]} × ${p[j + 1]} = {$total}."
                    body = "m[i][j] is the cheapest way to multiply Ai through Aj."
                } else if (k == i) {
                    headline = "Splitting after A${k + 1} costs {$total}."
                    body = "${chainGroup(i, k)} ${chainGroup(k + 1, j)}: the two sides' ${dp[i][k]} and ${dp[k + 1][j]}, plus " +
                        "${p[i]} × ${p[k + 1]} × ${p[j + 1]} = $join to join them."
                } else {
                    val times = tried.first().third / total
                    headline = if (better) {
                        "Splitting after A${k + 1} costs {$total}" + if (times == 6) ", six times cheaper." else "."
                    } else {
                        "Splitting after A${k + 1} costs $total, more than {$best}."
                    }
                    body = "Only the upper triangle is used: m[i][j] is the cheapest way to multiply Ai through Aj."
                    chips[0] = StoryChip("best split", "${chainGroup(i, split)} ${chainGroup(split + 1, j)}")
                }
                dp[i][j] = best
                bld.fill(
                    i, j, "$total",
                    reads = setOf(bld.key(i, k), bld.key(k + 1, j)),
                    chips = chips,
                    formulaRows = rows,
                    headline = headline,
                    body = body,
                )
            }
            bld.values[bld.key(i, j)] = "$best"
        }
    }
    val corner = bld.key(0, n - 1)
    var split = 0
    for (k in 0 until n - 1) if (dp[0][k] + dp[k + 1][n - 1] + p[0] * p[k + 1] * p[n] == dp[0][n - 1]) { split = k; break }
    val order = "${chainGroup(0, split)} ${chainGroup(split + 1, n - 1)}"
    bld.frame(
        headline = "$order takes {v:${dp[0][n - 1]}} multiplications.",
        body = "The corner cell covers the whole chain. The order of the multiplications changes the cost, never the product.",
        answer = setOf(corner),
        chips = listOf(StoryChip("best split", order), StoryChip("cost", "${dp[0][n - 1]}", StoryTone.Answer)),
    )
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
// Rows in the order the table fills them: by how many cities are visited, then by the set.
private val tspMasks = (0 until 16).filter { it and 1 == 1 }.sortedWith(compareBy({ Integer.bitCount(it) }, { it }))

private fun tspSet(mask: Int): String = (0 until 4).filter { mask and (1 shl it) != 0 }.joinToString(",", prefix = "{", postfix = "}")

private fun tspMaskLabel(row: Int): String = if (tspMasks[row] == 15) "all" else tspSet(tspMasks[row])

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
    // A set inside a headline, its braces escaped so they are not read as a colour mark.
    fun set(mask: Int) = "\\" + tspSet(mask)

    b.frame(
        headline = "Four cities, starting from 0. A row is the set visited so far, a column the city the route ends at.",
        body = "That is 2ⁿ·n cells to fill instead of n! whole tours to compare.",
        note = "4 cities",
    )
    dp[1][0] = 0
    b.fill(
        row(1), 0, "0",
        chips = listOf(StoryChip("dp", "0")),
        headline = "The route starts at city 0, having travelled {0}.",
        body = "Every other cell extends a shorter route by one city.",
        note = "start",
    )

    for (mask in tspMasks) {
        for (last in 1 until n) {
            if (mask and (1 shl last) == 0) continue
            val without = mask and (1 shl last).inv()
            // Every city the shorter route could have ended at, cheapest first.
            val options = (0 until n)
                .filter { without and (1 shl it) != 0 && dp[without][it] < inf }
                .map { prev -> prev to dp[without][prev] + cost[prev][last] }
                .sortedBy { it.second }
            if (options.isEmpty()) continue
            val (prev, best) = options.first()
            dp[mask][last] = best
            from[mask][last] = prev
            val body = if (options.size > 1) {
                val (other, alt) = options[1]
                "That beats ending at $other first: ${dp[without][other]} + ${cost[other][last]} = $alt."
            } else {
                "Each cell is the shortest route through that set, ending at that city."
            }
            b.fill(
                row(mask), last, "$best",
                reads = setOf(b.key(row(without), prev)),
                chips = listOf(StoryChip("dp", "${dp[without][prev]} + ${cost[prev][last]} = $best")),
                headline = "Reach $last from ${set(without)} ending at $prev: ${dp[without][prev]} + ${cost[prev][last]} = {$best}.",
                body = body,
                note = "d($prev,$last) = ${cost[prev][last]}",
            )
        }
    }

    // Closing the tour: every end city of the full set, plus the road home.
    val closes = (1 until n).map { last -> last to dp[full][last] + cost[last][0] }
    val (bestLast, bestTotal) = closes.minBy { it.second }
    b.frame(
        headline = "Back to 0 from each end of the full set. The cheapest tour costs {v:$bestTotal}.",
        body = "The last row holds every city; adding the road home to 0 turns each cell into a whole tour.",
        reads = (1 until n).map { b.key(row(full), it) }.toSet(),
        formulaRows = closes.map { (last, total) ->
            val won = last == bestLast
            StoryFormulaRow(
                "end at $last",
                if (won) "{p:${dp[full][last]}} + ${cost[last][0]} = {v:$total}" else "${dp[full][last]} + ${cost[last][0]} = $total",
            )
        },
        note = "back to 0",
    )

    // Walk the choices back to recover the tour, tracing the cells that produced it.
    val tour = mutableListOf<Int>()
    val traced = mutableSetOf<Int>()
    var mask = full
    var last = bestLast
    while (last != -1) {
        traced += b.key(row(mask), last)
        tour += last
        val prev = from[mask][last]
        mask = mask and (1 shl last).inv()
        last = prev
    }
    tour.reverse()
    val tourText = (tour + 0).joinToString(" → ")
    val corner = b.key(row(full), bestLast)
    b.frame(
        headline = "Tour $tourText costs {v:$bestTotal}.",
        body = "Each cell points back at the one it read from. ${b.frames.size - 2} cells stood in for 6 tours, and the gap grows as 2ⁿ·n² against n!.",
        traced = traced - corner,
        answer = setOf(corner),
        chips = listOf(StoryChip("tour", tourText), StoryChip("cost", "$bestTotal", StoryTone.Answer)),
        note = "traceback",
    )

    return b.frames
}

private val partitionItems = listOf(1, 5, 11, 5)
private const val PARTITION_TOTAL = 22
private const val PARTITION_HALF = PARTITION_TOTAL / 2

// Partition reduces to subset-sum at half the total, so the table is the subset-sum table with the
// target derived rather than given.
private fun partitionFrames(): List<DpFrame> {
    val items = partitionItems
    val target = PARTITION_HALF
    val b = DpBuilder(rows = items.size + 1, cols = target + 1)
    val dp = Array(items.size + 1) { BooleanArray(target + 1) }
    fun mark(x: Boolean) = if (x) "T" else "–"
    // The subset behind a true cell, read back up the table, and the items it leaves out.
    fun split(row: Int, sum: Int): Pair<List<Int>, List<Int>> {
        val chosen = mutableListOf<Int>()
        var t = sum
        for (i in row downTo 1) if (!dp[i - 1][t]) { chosen += items[i - 1]; t -= items[i - 1] }
        val picked = chosen.reversed()
        val rest = items.toMutableList().also { r -> picked.forEach { r.remove(it) } }
        return picked to rest
    }
    fun braces(xs: List<Int>) = "{${xs.joinToString(", ")}}"

    for (i in 0..items.size) {
        for (t in 0..target) {
            val chips = listOf(StoryChip("item", if (i == 0) "none" else "${items[i - 1]}"), StoryChip("sum", "$t"))
            if (i == 0) {
                dp[0][t] = t == 0
                b.fill(
                    0, t, mark(t == 0), chips = chips,
                    headline = if (t == 0) "The empty subset makes 0: {T}." else "With no items, $t is out of reach: {–}.",
                    body = if (t == 0) "The total is $PARTITION_TOTAL, even, so each half must reach $target."
                    else "Each cell asks whether some of the items so far add up to exactly that sum.",
                )
                continue
            }
            val item = items[i - 1]
            val skip = dp[i - 1][t]
            val fits = t >= item
            val take = fits && dp[i - 1][t - item]
            dp[i][t] = skip || take
            val headline = when {
                !fits && skip -> "The $item is too big for $t, and the row above already reaches it: {T}."
                !fits -> "The $item is too big for $t, and nothing smaller reaches it: {–}."
                skip -> "$t was already reachable without the $item: {T}."
                take && t == item -> "dp[${i - 1}][0] is true, so {$item} on its own reaches $t."
                take -> "dp[${i - 1}][${t - item}] is true, so adding the {$item} reaches $t."
                else -> "Neither skipping nor taking the $item reaches $t: {–}."
            }
            val firstHit = t == target && dp[i][t] && !skip
            val body = if (firstHit) {
                val (half, rest) = split(i, t)
                "A subset sums to half of $PARTITION_TOTAL, so the array splits evenly: ${braces(half)} and ${braces(rest)}."
            } else {
                "A cell is true if the row above is, or if taking this item lands on a true cell."
            }
            b.fill(
                i, t, mark(dp[i][t]), chips = chips,
                reads = if (fits) setOf(b.key(i - 1, t), b.key(i - 1, t - item)) else setOf(b.key(i - 1, t)),
                formula = if (fits) "skip {p:${mark(skip)}} or take {p:${mark(dp[i - 1][t - item])}} → {${mark(dp[i][t])}}"
                else "$item > $t, keep {p:${mark(skip)}}",
                headline = headline,
                body = body,
            )
        }
    }

    // Read the chosen items back: a true cell whose row above is false must have taken its item.
    val path = mutableSetOf<Int>()
    var t = target
    for (i in items.size downTo 1) {
        path += b.key(i, t)
        if (!dp[i - 1][t]) t -= items[i - 1]
    }
    path += b.key(0, t)
    val (half, rest) = split(items.size, target)
    val corner = b.key(items.size, target)
    b.frame(
        headline = "\\${braces(half)} and \\${braces(rest)} both sum to {v:$target}.",
        body = "The table has n × ${target + 1} cells. That is pseudo-polynomial: fine for small sums, which is why partition stays NP-complete.",
        traced = path - corner,
        answer = setOf(corner),
        chips = listOf(StoryChip("halves", "${half.sum()} + ${rest.sum()}"), StoryChip("each", "$target", StoryTone.Answer)),
    )
    return b.frames
}

private const val LCSUB_A = "abcdxy"
private const val LCSUB_B = "zabcdw"

// The same table shape as LCS with one clause changed: a mismatch writes 0 instead of carrying the
// best neighbour forward. Every zero in this grid is the contiguity requirement being enforced.
private fun longestCommonSubstringFrames(): List<DpFrame> {
    val a = LCSUB_A
    val w = LCSUB_B
    val rows = a.length + 1
    val cols = w.length + 1
    val b = DpBuilder(rows, cols)
    val dp = Array(rows) { IntArray(cols) }
    var best = 0
    var endI = 0
    var endJ = 0
    for (i in 0 until rows) {
        for (j in 0 until cols) {
            val chips = listOf(StoryChip("i", "$i"), StoryChip("j", "$j"), StoryChip("best", "$best", StoryTone.Answer))
            if (i == 0 || j == 0) {
                b.fill(
                    i, j, "0", chips = chips, headline = "An empty prefix shares no suffix with anything: {0}.",
                    body = "Row ε and column ε stay zero, so every run has somewhere to start from.",
                )
                continue
            }
            val x = a[i - 1]
            val y = w[j - 1]
            if (x == y) {
                dp[i][j] = dp[i - 1][j - 1] + 1
                val v = dp[i][j]
                val run = a.substring(i - v, i)
                val isBest = v > best
                if (isBest) {
                    best = v
                    endI = i
                    endJ = j
                }
                b.fill(
                    i, j, "$v", reads = setOf(b.key(i - 1, j - 1)),
                    chips = listOf(StoryChip("i", "$i"), StoryChip("j", "$j"), StoryChip("best", "$best", StoryTone.Answer)),
                    headline = "dp[$i][$j] = {$v}: the run \"$run\" ends here" + if (isBest) ", a new best." else ".",
                    body = "A mismatch resets to 0, unlike subsequence DP, so only the diagonal carries a run.",
                    formula = "'$x' = '$y' → dp[${i - 1}][${j - 1}] + 1 = {p:${dp[i - 1][j - 1]}} + 1 = {$v}",
                )
            } else {
                b.fill(
                    i, j, "0", chips = chips, headline = "'$x' ≠ '$y', so the run breaks: {0}.",
                    body = "Not max(up, left): that would be subsequence DP, which lets a run survive a gap.",
                    formula = "'$x' ≠ '$y' → {0}",
                )
            }
        }
    }
    val path = (0 until best).map { b.key(endI - it, endJ - it) }.toSet()
    val corner = b.key(endI, endJ)
    val run = a.substring(endI - best, endI)
    b.frame(
        headline = "The longest common substring is {v:$run}, length $best.",
        body = "Its run peaks at dp[$endI][$endJ], not in the corner: the answer is the largest cell anywhere.",
        traced = path - corner, answer = setOf(corner),
        chips = listOf(StoryChip("substring", run, StoryTone.Answer), StoryChip("length", "$best")),
        formula = "max cell = dp[$endI][$endJ] = {v:$best}",
    )
    return b.frames
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
        rowHeader = { "dp" }, colHeader = { it.toString() }, corner = "",
        intro = "Bottom-up Fibonacci: each cell is the sum of the two before it — computed once, left to right. No recursion, no repeated work.",
        build = ::fibonacciDpFrames,
        title = "BOTTOM-UP",
        note = "dp[i] = dp[i-1] + dp[i-2]",
    ),
    "edit_distance" to DpConfig(
        rows = 4, cols = 4,
        rowHeader = { if (it == 0) "ε" else "cat"[it - 1].toString() },
        colHeader = { if (it == 0) "ε" else "cut"[it - 1].toString() },
        corner = "",
        intro = "Edit distance between \"cat\" and \"cut\". Each cell is the cheapest way to turn one prefix into the other; the traceback shows the actual edits.",
        build = ::editDistanceFrames,
        title = "CAT → CUT",
    ),
    "longest_common_subsequence" to DpConfig(
        rows = 6, cols = 5,
        rowHeader = { if (it == 0) "ε" else "abcbd"[it - 1].toString() },
        colHeader = { if (it == 0) "ε" else "acbd"[it - 1].toString() },
        corner = "",
        intro = "Longest common subsequence of \"abcbd\" and \"acbd\". On a match the diagonal grows; otherwise carry the best neighbour. Traceback recovers the subsequence.",
        build = ::lcsFrames,
        title = "ABCBD · ACBD",
        legend = listOf(
            StoryTone.Active to "Current",
            StoryTone.Path to "Reads from",
            StoryTone.Done to "Traceback",
            StoryTone.Answer to "Length",
        ),
    ),
    "coin_change" to DpConfig(
        rows = coinChangeCoins.size + 1, cols = COIN_CHANGE_AMOUNT + 1,
        rowHeader = { if (it == 0) "ε" else coinChangeCoins[it - 1].toString() },
        colHeader = { it.toString() },
        corner = "¢",
        intro = "Fewest coins to make each amount, using denominations {1, 3, 4}. Each row adds a coin type; ∞ means unreachable. Traceback shows which coins make the target.",
        build = ::coinChangeFrames,
        title = "COINS {1, 3, 4}",
        note = "fewest coins",
        legend = DpLegend.dropLast(1) + (StoryTone.Answer to "Fewest"),
    ),
    "rod_cutting" to DpConfig(
        rows = rodPrices.size + 1, cols = ROD_LENGTH + 1,
        rowHeader = { if (it == 0) "ε" else it.toString() },
        colHeader = { it.toString() },
        corner = "len",
        intro = "Rod cutting — prices (1,5,8,9,10) for lengths 1..5. Each row allows one more piece length; the traceback shows which cuts produce the best revenue.",
        build = ::rodCuttingFrames,
        title = "PIECE PRICES",
        note = rodPrices.withIndex().joinToString(" ") { (i, p) -> "${i + 1}:\$$p" },
        legend = DpLegend.dropLast(1) + (StoryTone.Answer to "Best"),
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
        corner = "",
        intro = "Matrix chain with dimensions 10×30, 30×5, 5×60. The table fills along diagonals — by chain length — so every sub-chain is solved before the chains that contain it. Cells below the diagonal stay empty.",
        build = ::matrixChainFrames,
        title = "DIMENSIONS",
        note = "p = ${chainDims.joinToString(", ")}",
        pills = (0 until chainDims.size - 1).map { "A${it + 1} ${chainDims[it]}×${chainDims[it + 1]}" },
    ),
    "knapsack_01" to DpConfig(
        rows = knapWeights.size + 1, cols = KNAP_CAPACITY + 1,
        rowHeader = { if (it == 0) "ε" else "${knapWeights[it - 1]}, \$${knapValues[it - 1]}" },
        colHeader = { it.toString() },
        corner = "cap →",
        intro = "0/1 knapsack — items (wt, val) = (1,6), (2,10), (3,12), capacity 5. Each cell is the best value achievable; the traceback marks the items chosen.",
        build = ::knapsackFrames,
        title = "ITEMS (w, \$)",
        note = "cap $KNAP_CAPACITY",
    ),
    "bitmask_dp" to DpConfig(
        rows = 8, cols = 4,
        rowHeader = { tspMaskLabel(it) },
        colHeader = { "at $it" },
        corner = "",
        intro = "Held-Karp TSP over four cities. A row is a subset of visited cities encoded as a bitmask, a " +
            "column is the city you are standing on — 2ⁿ·n states instead of n! tours.",
        build = ::bitmaskDpFrames,
        title = "VISITED SET × ENDS AT",
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
        corner = "",
        intro = "Can {1, 5, 11, 5} be split into two equal halves? The total is 22, so the question becomes whether " +
            "any subset reaches exactly 11 — subset-sum with the target derived from the input rather than given.",
        build = ::partitionFrames,
        title = "{${partitionItems.joinToString(", ")}} · SUM $PARTITION_TOTAL",
        note = "target $PARTITION_HALF",
    ),
    "longest_common_substring" to DpConfig(
        rows = LCSUB_A.length + 1, cols = LCSUB_B.length + 1,
        rowHeader = { if (it == 0) "ε" else LCSUB_A[it - 1].toString() },
        colHeader = { if (it == 0) "ε" else LCSUB_B[it - 1].toString() },
        corner = "",
        intro = "Longest common substring of \"$LCSUB_A\" and \"$LCSUB_B\". A cell is the longest common *suffix* of the two " +
            "prefixes, so a mismatch resets it to zero — and the answer is the largest cell anywhere, not the corner.",
        build = ::longestCommonSubstringFrames,
        title = "ABCDXY · ZABCDW",
        legend = listOf(
            StoryTone.Active to "Current",
            StoryTone.Path to "Reads from",
            StoryTone.Done to "Run",
            StoryTone.Answer to "Longest",
        ),
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

// The DP story card (docs mocks for Coin Change, Knapsack, LCS, Matrix Chain…): the table with written
// cells in grey, the cell being written in yellow and the cells it reads in solid blue, then the
// recurrence with this cell's numbers, a legend of only what is on screen, value chips, and a headline
// whose key number takes the cell's colour. Cells a table never writes (matrix chain's lower triangle)
// are left out; cells still to come are dim tiles.

@Composable
fun DpGridSection(topicId: String) {
    val config = remember(topicId) { dpConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    // Every cell some step writes; the rest of the rectangle is never part of the table.
    val used = remember(frames) { frames.flatMapTo(HashSet()) { it.values.keys } }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        if (config.title == null) {
            LabIntro(config.intro)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        config.title.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = muted,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        frame.note ?: config.note ?: if (config.rows == 1) "dp[i]" else "dp[i][j]",
                        fontFamily = IBMPlexMono,
                        fontSize = 13.sp,
                        color = muted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }

                if (config.pills.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        config.pills.forEach { pill ->
                            Box(
                                modifier = Modifier.weight(1f).height(40.dp).tile(StoryTone.Idle, 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(pill, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1)
                            }
                        }
                    }
                }

                DpGrid(config = config, frame = frame, used = used, modifier = Modifier.padding(top = 12.dp))

                if (frame.strip.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        frame.strip.forEach { cell ->
                            Box(
                                modifier = Modifier.weight(1f).height(42.dp).tile(cell.tone, 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(cell.text, fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tileColors(cell.tone).second)
                            }
                        }
                    }
                }

                if (frame.options.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        frame.options.forEach { OptionCard(it, Modifier.weight(1f)) }
                    }
                }
                frame.formula?.let { StoryFormula(it, Modifier.padding(top = 12.dp)) }
                if (frame.formulaRows.isNotEmpty()) {
                    StoryFormulaRows(frame.formulaRows, Modifier.padding(top = if (frame.formula == null) 12.dp else 6.dp))
                }

                val present = cellTones(frame, used).values.toSet()
                StoryLegendRow(
                    config.legend.filter { it.first in present }.map { Triple(it.first.color(), SwatchStyle.Fill, it.second) },
                    Modifier.padding(top = 14.dp),
                )
            }
        }

        val chips = frame.chips.ifEmpty {
            val key = frame.active ?: return@ifEmpty emptyList()
            if (config.rows == 1) {
                listOf(StoryChip("i", "${key % config.cols}"))
            } else {
                listOf(StoryChip("i", "${key / config.cols}"), StoryChip("j", "${key % config.cols}"))
            }
        }
        StoryChips(chips, Modifier.padding(top = 16.dp))

        val headline = frame.headline
        if (headline != null) {
            LabStoryNarration(headline, frame.body.orEmpty(), Modifier.padding(top = 16.dp))
        } else {
            // Tables without narration show their one-line status as the headline, a size down —
            // several run to two sentences.
            Text(
                frame.status,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        PlaybackTransport(playback, captions = frames.map { it.status })
    }
}

// Each written or still-to-come cell's tone. Unwritten cells are Empty; cells outside [used] are absent.
private fun cellTones(frame: DpFrame, used: Set<Int>): Map<Int, StoryTone> =
    used.associateWith { key ->
        when {
            key == frame.active || key in frame.actives -> StoryTone.Active
            key in frame.answer -> StoryTone.Answer
            key in frame.traced -> StoryTone.Done
            key in frame.reads -> StoryTone.Path
            key in frame.values -> StoryTone.Idle
            else -> StoryTone.Empty
        }
    }

@Composable
private fun OptionCard(option: DpOption, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .background(if (option.chosen) SimColors.Active.copy(alpha = 0.12f) else SimColors.Tint, shape)
            .then(if (option.chosen) Modifier.border(1.5.dp, SimColors.Active, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            "${option.arrow} ${option.name}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (option.chosen) StoryTone.Active.ink() else muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            option.formula,
            fontFamily = IBMPlexMono,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DpGrid(config: DpConfig, frame: DpFrame, used: Set<Int>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Roomy squares only fit small tables; wider ones step down so twelve columns still fit a phone.
    // Tall tables (bitmask's eight sets) step down too, so the card stays on one screen.
    val cellHeight = when {
        config.cols <= 5 && config.rows <= 6 -> 42.dp
        config.cols <= 7 && config.rows <= 6 -> 38.dp
        config.cols <= 10 -> 34.dp
        else -> 28.dp
    }
    val gap = if (config.cols <= 7) 6.dp else 4.dp
    val fontSize = when {
        config.cols <= 5 -> 16.sp
        config.cols <= 7 -> 15.sp
        config.cols <= 10 -> 13.sp
        else -> 12.sp
    }
    val radius = if (config.cols <= 7) 9.dp else 6.dp
    // Single letters (ε, c, a) get a narrow gutter; longer labels ({0,1,3}, 3, $12) get room for themselves.
    val headerWidth = remember(config) {
        val longest = (0 until config.rows).maxOf { config.rowHeader(it).length }
        if (longest <= 2) 28.dp else (longest * 8 + 6).coerceAtMost(72).dp
    }
    val tones = cellTones(frame, used)
    val activeRow = frame.active?.let { it / config.cols }
    val activeCol = frame.active?.let { it % config.cols }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            HeaderLabel(config.corner, activeLine = false, start = true, modifier = Modifier.width(headerWidth))
            for (c in 0 until config.cols) {
                HeaderLabel(config.colHeader(c), c == activeCol, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until config.rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderLabel(config.rowHeader(r), r == activeRow, start = true, modifier = Modifier.width(headerWidth))
                for (c in 0 until config.cols) {
                    val key = r * config.cols + c
                    val tone = tones[key]
                    val cell = Modifier.weight(1f).height(cellHeight)
                    when (tone) {
                        null -> Spacer(cell)
                        // A slot still to fill: a dim tile, quieter than a written cell.
                        StoryTone.Empty -> Box(cell.background(muted.copy(alpha = 0.08f), RoundedCornerShape(radius)))
                        else -> Box(cell.tile(tone, radius), contentAlignment = Alignment.Center) {
                            Text(
                                frame.values[key].orEmpty(),
                                fontFamily = IBMPlexMono,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                color = tileColors(tone).second,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}


// Row and column labels; the current cell's row and column light up in the active yellow.
@Composable
private fun HeaderLabel(text: String, activeLine: Boolean, modifier: Modifier = Modifier, start: Boolean = false) {
    Box(modifier = modifier.height(22.dp), contentAlignment = if (start) Alignment.CenterStart else Alignment.Center) {
        Text(
            text,
            fontFamily = IBMPlexMono,
            fontSize = 13.sp,
            fontWeight = if (activeLine) FontWeight.Bold else FontWeight.Normal,
            color = if (activeLine) StoryTone.Active.ink() else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
