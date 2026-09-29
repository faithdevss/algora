package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors

// ── Bit story labs ───────────────────────────────────────────────────────────
// Bit Basics, Count Set Bits, XOR Tricks and Subsets as tabbed storyboards: rows of bit cells under a
// place-value header, the value beside each row, the operation with its numbers underneath, then chips
// and a headline. Result rows and their values take the violet.

private enum class SBitTone { Zero, One, Lowest, Result, Pending, Member }
private class SBitCell(val text: String, val tone: SBitTone)
private class SBitRow(val label: String, val cells: List<SBitCell>, val readout: String, val result: Boolean = false)
private enum class MaskTone { Listed, Current, Pending }
private class MaskTile(val top: String, val bottom: String, val tone: MaskTone)

private class SBitFrame(
    val rows: List<SBitRow>,
    val headline: String,
    val body: String,
    val headers: List<String> = (0 until 8).map { "${7 - it}" },
    // Header indices lit in yellow (the position being tested or isolated).
    val lit: Set<Int> = emptySet(),
    val formula: String? = null,
    val formulaRows: List<StoryFormulaRow> = emptyList(),
    val masks: List<MaskTile> = emptyList(),
    val chips: List<StoryChip> = emptyList(),
)

private class SBitTab(val label: String, val frames: List<SBitFrame>, val legend: List<Triple<Color, SwatchStyle, String>>)

private val SbDim = Color.Gray.copy(alpha = 0.35f)

private fun bin8(n: Int) = (0 until 8).joinToString("") { if ((n shr (7 - it)) and 1 == 1) "1" else "0" }

/** An 8-bit row, most significant bit first. Result rows paint their 1s violet; [lowest] is yellow. */
private fun sbRow(label: String, n: Int, readout: String? = null, result: Boolean = false, lowest: Int? = null): SBitRow {
    val v = n and 0xFF
    val cells = (0 until 8).map { i ->
        val pos = 7 - i
        val bit = (v shr pos) and 1
        val tone = when {
            pos == lowest -> SBitTone.Lowest
            bit == 1 -> if (result) SBitTone.Result else SBitTone.One
            else -> SBitTone.Zero
        }
        SBitCell("$bit", tone)
    }
    return SBitRow(label, cells, readout ?: "$n", result)
}

private fun lowestBit(n: Int) = (0 until 8).firstOrNull { (n shr it) and 1 == 1 } ?: 0
private fun litCol(pos: Int) = setOf(7 - pos)

private val basicsLegend = listOf(
    Triple(SimColors.Blue, SwatchStyle.Fill, "Set bit"),
    Triple(SimColors.Active, SwatchStyle.Fill, "Lowest set bit"),
    Triple(SimColors.Answer, SwatchStyle.Fill, "Result"),
)

private fun bitBasicsTabs(): List<SBitTab> {
    val n = 44
    val low = lowestBit(n)
    val neg = (256 - n) and 0xFF
    val m1 = n - 1
    val negRow = StoryFormulaRow("−n", "~n + 1 = ${bin8(n.inv())} + 1")
    val andNeg = SBitTab(
        "n & −n",
        listOf(
            SBitFrame(
                listOf(sbRow("n", n)), "n = 44 has three set bits: 5, 3 and 2.",
                "Each cell is a place value. The tricks below are all about which places are occupied.",
                formula = "44 = 32 + 8 + 4 = ${bin8(n)}",
            ),
            SBitFrame(
                listOf(sbRow("n", n, lowest = low), sbRow("−n", -n, lowest = low)),
                "−n flips every bit and adds 1, which carries up to bit {$low}.",
                "Two's complement: the +1 stops at the first 0 of ~n, and that is exactly n's lowest set bit.",
                lit = litCol(low), formulaRows = listOf(negRow),
            ),
            SBitFrame(
                listOf(sbRow("n", n, lowest = low), sbRow("−n", -n, lowest = low), sbRow("n & −n", n and neg, result = true)),
                "Only bit $low is 1 in both n and −n, so n & −n = {v:${n and neg}}.",
                "−n flips every bit, then +1 carries up to the lowest set bit. That bit is the only one they share.",
                lit = litCol(low), formula = "${bin8(n)} & ${bin8(neg)} = {v:${bin8(n and neg)}}", formulaRows = listOf(negRow),
            ),
            SBitFrame(
                listOf(sbRow("n", n, lowest = low), sbRow("n & −n", n and neg, result = true)),
                "n & −n isolates the lowest set bit, {v:${n and neg}}, in one step.",
                "A Fenwick tree walks between ranges by exactly this amount.",
                lit = litCol(low), formula = "i += i & −i → $n + {v:${n and neg}} = ${n + (n and neg)}",
            ),
        ),
        basicsLegend,
    )
    val orM1 = SBitTab(
        "n | (n−1)",
        listOf(
            SBitFrame(
                listOf(sbRow("n", n, lowest = low)), "n = 44. Its lowest set bit is bit {$low}, with two zeros below it.",
                "Subtracting 1 has to borrow from that bit.", lit = litCol(low), formula = "n = ${bin8(n)}",
            ),
            SBitFrame(
                listOf(sbRow("n", n, lowest = low), sbRow("n − 1", m1)), "n − 1 = 43 clears bit $low and sets every bit below it.",
                "Borrowing turns the lowest 1 into 0 and the trailing zeros into ones. Everything above is untouched.",
                lit = litCol(low), formula = "44 − 1 = ${bin8(m1)}",
            ),
            SBitFrame(
                listOf(sbRow("n", n, lowest = low), sbRow("n − 1", m1), sbRow("n | (n−1)", n or m1, result = true)),
                "n | (n − 1) = {v:${n or m1}}: the trailing zeros are filled in.",
                "Bits above $low keep their value and every bit from $low down becomes 1.",
                lit = litCol(low), formula = "${bin8(n)} | ${bin8(m1)} = {v:${bin8(n or m1)}}",
            ),
        ),
        basicsLegend,
    )
    val andM1 = SBitTab(
        "n & (n−1)",
        listOf(
            SBitFrame(
                listOf(sbRow("n", n, lowest = low), sbRow("n − 1", m1)), "n − 1 flips bit {$low} and everything below it.",
                "So n and n − 1 agree on every bit above $low and disagree on the rest.",
                lit = litCol(low), formula = "44 − 1 = ${bin8(m1)}",
            ),
            SBitFrame(
                listOf(sbRow("n", n, lowest = low), sbRow("n − 1", m1), sbRow("n & (n−1)", n and m1, result = true)),
                "n & (n − 1) = {v:${n and m1}}: the lowest set bit is gone.",
                "Repeat it until n is 0 and you have counted the set bits, one per loop.",
                lit = litCol(low), formula = "${bin8(n)} & ${bin8(m1)} = {v:${bin8(n and m1)}}",
            ),
            SBitFrame(
                listOf(sbRow("n", 8, lowest = 3), sbRow("n − 1", 7), sbRow("n & (n−1)", 0, result = true)),
                "For n = 8 the answer is {v:0}, because 8 has a single set bit.",
                "That is the power-of-two test: n > 0 && (n & (n − 1)) == 0.",
                lit = litCol(3), formula = "${bin8(8)} & ${bin8(7)} = {v:${bin8(0)}}",
            ),
        ),
        basicsLegend,
    )
    return listOf(andNeg, orM1, andM1)
}

private fun countSetBitsTabs(): List<SBitTab> {
    val n = 156
    val total = (0 until 8).count { (n shr it) and 1 == 1 }
    val compare = listOf(StoryFormulaRow("naive", "8 iterations, always"), StoryFormulaRow("Kernighan", "$total iterations, one per set bit"))
    val naive = mutableListOf(
        SBitFrame(
            listOf(SBitRow("n", (0 until 8).map { SBitCell("${(n shr (7 - it)) and 1}", SBitTone.Pending) }, "$n")),
            "Count the 1s in n = 156 by testing one position at a time.",
            "Shift the bit into place and mask it with 1, from bit 7 down to bit 0.",
            formula = "n = ${bin8(n)}", formulaRows = compare,
            chips = listOf(StoryChip("count", "0"), StoryChip("iteration", "0 of 8")),
        ),
    )
    var count = 0
    (7 downTo 0).forEachIndexed { k, pos ->
        val bit = (n shr pos) and 1
        count += bit
        val cells = (0 until 8).map { i ->
            val p = 7 - i
            val b = (n shr p) and 1
            SBitCell(
                "$b",
                when {
                    p == pos -> SBitTone.Lowest
                    p > pos -> if (b == 1) SBitTone.One else SBitTone.Zero
                    else -> SBitTone.Pending
                },
            )
        }
        naive += SBitFrame(
            listOf(SBitRow("n", cells, "$n")),
            if (bit == 1) "Bit $pos is {1}, so the count goes up to $count." else "Bit $pos is 0, so the count stays at $count.",
            if (bit == 1) "The loop visits all 8 positions, including zeros. Kernighan skips zeros by clearing the lowest set bit each time."
            else "A zero still costs an iteration. The naive loop's work depends on the width, not on n.",
            lit = litCol(pos),
            formula = "($n >> $pos) & 1 = ${if (bit == 1) "{1}" else "0"} → count ${if (bit == 1) "{$count}" else "$count"}",
            formulaRows = compare,
            chips = listOf(StoryChip("count", "$count"), StoryChip("iteration", "${k + 1} of 8")),
        )
    }
    naive += SBitFrame(
        listOf(sbRow("n", n)), "Eight iterations to find {v:$total} set bits.", "Half of those iterations looked at zeros.",
        formula = "popcount($n) = {v:$total}", formulaRows = compare,
        chips = listOf(StoryChip("count", "$total", StoryTone.Answer), StoryChip("iterations", "8")),
    )
    val kern = mutableListOf(
        SBitFrame(
            listOf(sbRow("n", n)), "Kernighan's loop runs once per set bit, not once per position.",
            "Each n & (n − 1) removes exactly the lowest set bit.",
            formula = "while n ≠ 0: n &= n − 1, count++", formulaRows = compare,
            chips = listOf(StoryChip("count", "0"), StoryChip("iteration", "0")),
        ),
    )
    var v = n
    var c = 0
    while (v != 0) {
        val low = lowestBit(v)
        val next = v and (v - 1)
        c += 1
        kern += SBitFrame(
            listOf(sbRow("n", v, lowest = low), sbRow("n − 1", v - 1), sbRow("n & (n−1)", next, result = true)),
            "Clearing bit {$low} leaves $next, and the count is $c.",
            if (c == 1) "The zeros below bit $low were never visited." else "Only set bits cost an iteration.",
            lit = litCol(low), formula = "$v & ${v - 1} = {v:$next} → count {$c}", formulaRows = compare,
            chips = listOf(StoryChip("count", "$c"), StoryChip("iteration", "$c of $total")),
        )
        v = next
    }
    kern += SBitFrame(
        listOf(sbRow("n", 0)), "n reached 0 after {v:$total} iterations, one per set bit.",
        "Same answer as the naive loop in half the iterations here, and far fewer on sparse numbers.",
        formula = "n = 0 → count = {v:$total}", formulaRows = compare,
        chips = listOf(StoryChip("count", "$total", StoryTone.Answer), StoryChip("iterations", "$total")),
    )
    return listOf(
        SBitTab(
            "Naive", naive,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Counted"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Testing"),
                Triple(SbDim, SwatchStyle.Fill, "Not checked yet"),
            ),
        ),
        SBitTab("Kernighan", kern, basicsLegend),
    )
}

private fun xorTricksTabs(): List<SBitTab> {
    val a = 44
    val b = 25
    val legend = listOf(
        Triple(SimColors.Blue, SwatchStyle.Fill, "Set bit"),
        Triple(SimColors.Answer, SwatchStyle.Fill, "Bits differ → 1"),
        Triple(SbDim, SwatchStyle.Fill, "Bits match → 0"),
    )
    val basics = SBitTab(
        "Basics",
        listOf(
            SBitFrame(
                listOf(sbRow("a", a), sbRow("b", b), sbRow("a ⊕ b", a xor b, result = true)),
                "XOR is {v:1} wherever a and b differ.",
                "So x ⊕ x = 0 and x ⊕ 0 = x, which means XOR undoes itself. Swap and Find unique rely on this.",
                formula = "$a ⊕ $b = {v:${a xor b}}", formulaRows = listOf(StoryFormulaRow("undo", "${a xor b} ⊕ $b = $a")),
            ),
            SBitFrame(
                listOf(sbRow("a", a), sbRow("a", a), sbRow("a ⊕ a", 0, result = true)),
                "a ⊕ a = {v:0}: every bit matches itself.", "Any value XOR-ed with itself cancels completely.",
                formula = "$a ⊕ $a = {v:0}",
            ),
            SBitFrame(
                listOf(sbRow("a", a), sbRow("0", 0), sbRow("a ⊕ 0", a, result = true)),
                "a ⊕ 0 = {v:$a}: XOR with zero changes nothing.", "Zero is the identity, the way 0 is for addition.",
                formula = "$a ⊕ 0 = {v:$a}",
            ),
            SBitFrame(
                listOf(sbRow("a ⊕ b", a xor b), sbRow("b", b), sbRow("(a ⊕ b) ⊕ b", a, result = true)),
                "XOR-ing b in again cancels it and gives back {v:$a}.", "(a ⊕ b) ⊕ b = a ⊕ (b ⊕ b) = a ⊕ 0 = a.",
                formula = "${a xor b} ⊕ $b = {v:$a}",
            ),
        ),
        legend,
    )
    val s1 = a xor b
    val s2 = b xor s1
    val s3 = s1 xor s2
    val swap = SBitTab(
        "Swap",
        listOf(
            SBitFrame(
                listOf(sbRow("a", a), sbRow("b", b)), "Swap a and b with three XORs and no temporary.",
                "Each line folds one value into the other, then unfolds it.", formula = "a = $a, b = $b",
            ),
            SBitFrame(
                listOf(sbRow("a", s1, result = true), sbRow("b", b)), "a ^= b stores both values in a: {v:$s1}.",
                "a now holds a ⊕ b, and b is untouched.", formula = "a ^= b → $a ⊕ $b = {v:$s1}",
            ),
            SBitFrame(
                listOf(sbRow("a", s1), sbRow("b", s2, result = true)), "b ^= a cancels b's own bits and leaves the old a, {v:$s2}.",
                "b ⊕ (a ⊕ b) = a.", formula = "b ^= a → $b ⊕ $s1 = {v:$s2}",
            ),
            SBitFrame(
                listOf(sbRow("a", s3, result = true), sbRow("b", s2)), "a ^= b cancels the old a and leaves the old b, {v:$s3}.",
                "(a ⊕ b) ⊕ a = b. The two values have traded places.", formula = "a ^= b → $s1 ⊕ $s2 = {v:$s3}",
            ),
        ),
        legend,
    )
    val items = listOf(4, 7, 2, 7, 4)
    val arrayChip = StoryChip("array", "4 7 2 7 4")
    var acc = 0
    val unique = mutableListOf(
        SBitFrame(
            listOf(sbRow("acc", 0)), "Every value appears twice except one. XOR them all together.",
            "Pairs cancel to 0 whatever order they arrive in, so only the loner survives.",
            formula = "acc = 0", chips = listOf(arrayChip),
        ),
    )
    items.forEachIndexed { i, x ->
        val next = acc xor x
        unique += SBitFrame(
            listOf(sbRow("acc", acc), sbRow("x", x), sbRow("acc ⊕ x", next, result = true)),
            if (x in items.take(i)) "$x again cancels its first copy: acc = {v:$next}." else "Fold in $x: acc = {v:$next}.",
            "One pass, one integer of memory, O(n).",
            formula = "$acc ⊕ $x = {v:$next}", chips = listOf(arrayChip, StoryChip("read", "${i + 1} of ${items.size}")),
        )
        acc = next
    }
    unique += SBitFrame(
        listOf(sbRow("acc", acc, result = true)), "Both pairs cancelled, so {v:$acc} is the one that appears once.",
        "No sorting and no hash set: the pairs erase each other.",
        formula = "unique = {v:$acc}", chips = listOf(arrayChip, StoryChip("unique", "$acc", StoryTone.Answer)),
    )
    return listOf(basics, swap, SBitTab("Find unique", unique, legend))
}

private fun subsetsTabs(): List<SBitTab> {
    val names = listOf("a", "b", "c")
    fun members(m: Int) = (0 until 3).filter { (m shr it) and 1 == 1 }.map { names[it] }
    fun setText(m: Int) = if (m == 0) "∅" else members(m).joinToString(",", "{", "}")
    fun bits(m: Int) = (0 until 3).joinToString("") { if ((m shr (2 - it)) and 1 == 1) "1" else "0" }
    fun tiles(current: Int?) = (0 until 8).map { m ->
        MaskTile(
            "$m · ${bits(m)}", setText(m),
            when {
                current == null || m < current -> MaskTone.Listed
                m == current -> MaskTone.Current
                else -> MaskTone.Pending
            },
        )
    }
    val headers = listOf("c · 2", "b · 1", "a · 0")
    fun maskRow(m: Int) = SBitRow(
        "mask",
        (0 until 3).map { i ->
            val bit = (m shr (2 - i)) and 1
            SBitCell("$bit", if (bit == 1) SBitTone.Member else SBitTone.Zero)
        },
        "$m",
    )
    val frames = mutableListOf(
        SBitFrame(
            listOf(maskRow(0)), "Three items, so every mask from 0 to 7 is one subset.",
            "Bit i of the mask says whether item i is in the subset.",
            headers = headers, formula = "for mask in 0 ..< 2³", masks = tiles(-1),
            chips = listOf(StoryChip("items", "a b c"), StoryChip("masks", "8")),
        ),
    )
    for (m in 0 until 8) {
        val parts = (0 until 3).filter { (m shr it) and 1 == 1 }
        val formula = when (parts.size) {
            0 -> "0 has no set bits → take {v:nothing}"
            1 -> "$m & (1 << ${parts[0]}) = ${1 shl parts[0]} ≠ 0 → take {v:${names[parts[0]]}}"
            else -> parts.joinToString(", ") { "$m & ${1 shl it} ≠ 0" } + " → {v:${members(m).joinToString(", ")}}"
        }
        val subset = if (m == 0) "the empty set, {v:∅}" else "\\{ {v:${members(m).joinToString(", ")}} }"
        frames += SBitFrame(
            listOf(maskRow(m)), "mask $m = ${bits(m)}, so the subset is $subset.",
            "Counting 0 to 2ⁿ−1 lists every subset once. The mask is also a plain integer, so it can index a DP array.",
            headers = headers, lit = (0 until 3).filter { (m shr (2 - it)) and 1 == 1 }.toSet(), formula = formula, masks = tiles(m),
            chips = listOf(StoryChip("mask", "$m"), StoryChip("subset", setText(m), StoryTone.Answer), StoryChip("listed", "${m + 1} of 8")),
        )
    }
    frames += SBitFrame(
        listOf(maskRow(7)), "All {v:2³ = 8} subsets, each exactly once.",
        "No recursion and no visited set: the counter itself guarantees coverage.",
        headers = headers, formula = "8 masks → {v:8 subsets}", masks = tiles(null), chips = listOf(StoryChip("listed", "8 of 8", StoryTone.Answer)),
    )
    frames += SBitFrame(
        listOf(maskRow(5)), "Because a mask is an integer, dp[mask] can store an answer per subset.",
        "That is bitmask DP: 2ⁿ states, each reachable by flipping one bit of a smaller mask.",
        headers = headers, formula = "dp[5] ← best answer for \\{{v:a,c}}", masks = tiles(null), chips = listOf(StoryChip("state", "mask 5")),
    )
    return listOf(
        SBitTab(
            "Subsets", frames,
            listOf(
                Triple(SimColors.Answer, SwatchStyle.Fill, "In this subset"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Current mask"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Listed"),
            ),
        ),
    )
}

internal val bitStoryTopicIds = setOf("bit_basics", "count_set_bits", "xor_tricks", "subsets_bitmask")

private fun bitStoryTabs(topicId: String) = when (topicId) {
    "count_set_bits" -> countSetBitsTabs()
    "xor_tricks" -> xorTricksTabs()
    "subsets_bitmask" -> subsetsTabs()
    else -> bitBasicsTabs()
}

// Also checks every frame's rows are as wide as its header, which the grid assumes.
internal fun bitStoryFrameCount(topicId: String): Int =
    bitStoryTabs(topicId).sumOf { tab ->
        tab.frames.forEach { f -> f.rows.forEach { require(it.cells.size == f.headers.size) { "$topicId: row ${it.label} width" } } }
        tab.frames.size
    }

@Composable
internal fun BitStorySection(topicId: String) {
    val tabs = remember(topicId) { bitStoryTabs(topicId) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(0) }
    val frames = tabs[tab].frames
    val playback = rememberPlaybackState(key = topicId to tab, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (tabs.size > 1) LabSegments(tabs.map { it.label }, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
                SBitGrid(frame)
                frame.formula?.let { StoryFormula(it, Modifier.padding(top = 12.dp)) }
                if (frame.formulaRows.isNotEmpty()) {
                    StoryFormulaRows(frame.formulaRows, Modifier.padding(top = if (frame.formula == null) 12.dp else 8.dp))
                }
                if (frame.masks.isNotEmpty()) {
                    Text(
                        "ALL 2³ MASKS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = muted,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        frame.masks.chunked(4).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { line.forEach { MaskTileView(it, Modifier.weight(1f)) } }
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
private fun SBitGrid(frame: SBitFrame) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gutter = 70.dp
    val readout = 44.dp
    val wide = frame.headers.size <= 4
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(gutter))
            frame.headers.forEachIndexed { i, h ->
                val on = i in frame.lit
                Text(
                    h,
                    fontFamily = IBMPlexMono,
                    fontSize = if (wide) 12.sp else 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) StoryTone.Active.ink() else muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(readout))
        }
        frame.rows.forEach { row ->
            val ink = if (row.result) StoryTone.Answer.ink() else onSurface
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label, fontFamily = IBMPlexMono, fontSize = 13.sp, maxLines = 1,
                    color = if (row.result) ink else onSurface.copy(alpha = 0.8f), modifier = Modifier.width(gutter),
                )
                row.cells.forEach { SBitCellView(it, if (wide) 44 else 34, Modifier.weight(1f)) }
                Text(
                    row.readout, fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink,
                    textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(readout),
                )
            }
        }
    }
}

@Composable
private fun SBitCellView(cell: SBitCell, height: Int, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (fill, ink) = when (cell.tone) {
        SBitTone.Zero -> muted.copy(alpha = 0.18f) to muted
        SBitTone.One -> SimColors.Blue to Color.White
        SBitTone.Lowest -> SimColors.Active to Color(0xFF1F1A0A)
        SBitTone.Result, SBitTone.Member -> SimColors.Answer to Color.White
        SBitTone.Pending -> muted.copy(alpha = 0.08f) to muted.copy(alpha = 0.55f)
    }
    Box(modifier = modifier.height(height.dp).background(fill, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        Text(cell.text, fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

@Composable
private fun MaskTileView(tile: MaskTile, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (fill, ink) = when (tile.tone) {
        MaskTone.Listed -> SimColors.Green.copy(alpha = if (LocalDarkTheme.current) 0.22f else 0.16f) to StoryTone.Done.ink()
        MaskTone.Current -> SimColors.Active to Color(0xFF1F1A0A)
        MaskTone.Pending -> muted.copy(alpha = 0.08f) to muted.copy(alpha = 0.6f)
    }
    Column(
        modifier = modifier.height(48.dp).background(fill, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tile.top, fontFamily = IBMPlexMono, fontSize = 11.sp, color = ink.copy(alpha = 0.85f), maxLines = 1)
        Text(tile.bottom, fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
    }
}
