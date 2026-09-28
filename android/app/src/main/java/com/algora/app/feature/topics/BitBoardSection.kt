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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors

// ── Bit board player ─────────────────────────────────────────────────────────
// Integers as fixed-width rows of per-bit cells, with the decimal value beside each row and a
// caption naming the operation that produced it.
//
// Why this is not an ArrayWalkPlayer config: that widget's row is one weighted cell per element with
// a numeric label, and the bit topics need eight to sixteen narrow, uniform cells whose *position*
// carries the meaning — bit 3 is the 8s place whether it holds a 0 or a 1. Stretching the array row
// to that many columns collapses its labels, and the array row has no notion of a place value to
// annotate. Same config-driven shape as RegressionLab and DecisionSurface: a SimulationType data
// object plus a config map, not a bespoke screen.

private enum class BitMark {
    /** A 0 bit that nothing is happening to. */
    ZERO,

    /** A 1 bit that nothing is happening to. */
    ONE,

    /** The bit the current operation is reading or testing. */
    ACTIVE,

    /** A 1 that this step turned into a 0. */
    CLEARED,

    /** A bit that carries the answer. */
    RESULT,

    /** Outside the operand's width — drawn faint so the width stays visible. */
    MASKED,
}

private class BitCell(val value: Int, val mark: BitMark)

private class BitRow(
    val label: String,
    val cells: List<BitCell>,
    val readout: String? = null,
)

private class BitFrame(
    val status: String,
    val rows: List<BitRow>,
    val readout: String? = null,
)

private class BitConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<BitFrame>,
)

private val ZeroFill = SimColors.Wall
private val OneFill = SimColors.Blue
private val ActiveFillBit = SimColors.Active
private val ClearedFill = SimColors.Red
private val ResultFillBit = Color(0xFF7C3AED)
private val MaskedFill = Color(0xFF2A2F38)

private const val BIT_WIDTH = 8

// ── Builders ─────────────────────────────────────────────────────────────────

/** Most-significant bit first, so the row reads the way the number is written. */
private fun bitsOf(
    value: Int,
    width: Int = BIT_WIDTH,
    active: Set<Int> = emptySet(),
    cleared: Set<Int> = emptySet(),
    result: Set<Int> = emptySet(),
    masked: Set<Int> = emptySet(),
): List<BitCell> = (width - 1 downTo 0).map { position ->
    val bit = (value shr position) and 1
    BitCell(
        bit,
        when {
            position in masked -> BitMark.MASKED
            position in cleared -> BitMark.CLEARED
            position in active -> BitMark.ACTIVE
            position in result -> BitMark.RESULT
            bit == 1 -> BitMark.ONE
            else -> BitMark.ZERO
        },
    )
}

private fun row(label: String, value: Int, width: Int = BIT_WIDTH, vararg marks: Pair<String, Set<Int>>): BitRow {
    val byKind = marks.toMap()
    return BitRow(
        label,
        bitsOf(
            value,
            width,
            active = byKind["active"].orEmpty(),
            cleared = byKind["cleared"].orEmpty(),
            result = byKind["result"].orEmpty(),
            masked = byKind["masked"].orEmpty(),
        ),
        readout = "= $value",
    )
}

private fun bitBasicsFrames(): List<BitFrame> {
    val frames = mutableListOf<BitFrame>()

    val n = 44
    frames += BitFrame(
        status = "$n in binary. Bit at position p is worth 2ᵖ, so the row is a place-value system with only two digits — " +
            "and every trick below is a statement about places, not about arithmetic.",
        rows = listOf(row("n", n)),
        readout = "$n = 32 + 8 + 4",
    )

    for (candidate in listOf(44, 37)) {
        val even = candidate and 1 == 0
        frames += BitFrame(
            status = "n & 1 isolates the 1s place, and that place alone decides parity: $candidate & 1 = ${candidate and 1}, so $candidate is " +
                "${if (even) "even" else "odd"}. It is the same answer as n % 2 and it is correct for negatives too, which n % 2 is not " +
                "in Kotlin — (−3) % 2 is −1, but (−3) and 1 is 1.",
            rows = listOf(
                row("n", candidate, marks = arrayOf("active" to setOf(0))),
                row("1", 1, marks = arrayOf("active" to setOf(0))),
                row("n & 1", candidate and 1, marks = arrayOf("result" to setOf(0))),
            ),
            readout = "$candidate is ${if (even) "even" else "odd"}",
        )
    }

    for (candidate in listOf(16, 20)) {
        val minusOne = candidate - 1
        val anded = candidate and minusOne
        val isPower = anded == 0
        frames += BitFrame(
            status = "Subtracting 1 flips the lowest set bit to 0 and turns everything below it into 1s. So n & (n−1) always clears " +
                "exactly that lowest set bit: $candidate & $minusOne = $anded. " +
                if (isPower) "Zero, so $candidate had only one set bit — it is a power of two." else "Non-zero, so $candidate had more than one set bit and is not a power of two.",
            rows = listOf(
                row("n", candidate),
                row("n − 1", minusOne),
                row("n & (n−1)", anded, marks = arrayOf("result" to if (isPower) emptySet() else setOf(Integer.numberOfTrailingZeros(anded)))),
            ),
            readout = if (isPower) "$candidate is a power of two" else "$candidate is not a power of two",
        )
    }

    val v = 44
    frames += BitFrame(
        status = "The other two everyday isolations. n & −n keeps only the lowest set bit — because −n is the bitwise complement " +
            "plus one, which leaves that bit and nothing else agreeing. n | (n−1) fills every bit below the lowest set one.",
        rows = listOf(
            row("n", v),
            row("n & −n", v and -v, marks = arrayOf("result" to setOf(Integer.numberOfTrailingZeros(v)))),
            row("n | (n−1)", v or (v - 1)),
        ),
        readout = "lowest set bit of $v is ${v and -v}",
    )

    frames += BitFrame(
        status = "All of it is one machine instruction each: no division, no branch, no loop. That is the entire reason these " +
            "appear in hot paths — and the entire reason they should not appear anywhere else, since \"n & 1\" says nothing about " +
            "parity to a reader while \"n % 2 == 0\" does.",
        rows = listOf(row("n", v), row("n & 1", v and 1), row("n & (n−1)", v and (v - 1)), row("n & −n", v and -v)),
        readout = "each one instruction",
    )
    return frames
}

private fun countSetBitsFrames(): List<BitFrame> {
    val n = 156   // 10011100 — four set bits, with a gap, so the two loops differ visibly
    val frames = mutableListOf<BitFrame>()

    frames += BitFrame(
        status = "Count the 1s in $n. The naive loop tests every position regardless of what is there; Brian Kernighan's loop " +
            "runs once per set bit. On this value that is ${BIT_WIDTH} iterations against ${Integer.bitCount(n)}.",
        rows = listOf(row("n", n)),
        readout = "$n = ${Integer.toBinaryString(n).padStart(BIT_WIDTH, '0')}",
    )

    // Naive: shift and test, once per bit position, whatever the value.
    var naiveCount = 0
    for (position in BIT_WIDTH - 1 downTo 0) {
        val bit = (n shr position) and 1
        if (bit == 1) naiveCount++
        frames += BitFrame(
            status = "Naive pass, position $position: bit is $bit, running count $naiveCount. This iteration happens whether or not " +
                "there is anything there — the loop is driven by the width, not by the value.",
            rows = listOf(
                row("n", n, marks = arrayOf("active" to setOf(position), "masked" to (position - 1 downTo 0).toSet())),
            ),
            readout = "count $naiveCount after ${BIT_WIDTH - position} of $BIT_WIDTH iterations",
        )
    }

    // Kernighan: n & (n-1) clears the lowest set bit, so one iteration per set bit.
    var value = n
    var kernighanCount = 0
    while (value != 0) {
        val lowest = Integer.numberOfTrailingZeros(value)
        val next = value and (value - 1)
        kernighanCount++
        frames += BitFrame(
            status = "Kernighan iteration $kernighanCount: n & (n−1) clears the lowest set bit, at position $lowest. " +
                "$value becomes $next. Nothing was examined — the arithmetic located the bit.",
            rows = listOf(
                row("n", value, marks = arrayOf("active" to setOf(lowest))),
                row("n − 1", value - 1),
                row("n & (n−1)", next, marks = arrayOf("cleared" to setOf(lowest))),
            ),
            readout = "count $kernighanCount",
        )
        value = next
    }

    frames += BitFrame(
        status = "Both say ${Integer.bitCount(n)}. The naive loop took $BIT_WIDTH iterations and would take 32 on an Int; Kernighan took " +
            "$kernighanCount, one per set bit. On sparse values that is the whole difference, and on dense ones it is none. In " +
            "production neither is what you write: Integer.bitCount compiles to a single POPCNT instruction.",
        rows = listOf(row("n", n, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (n shr it) and 1 == 1 }.toSet()))),
        readout = "popcount = ${Integer.bitCount(n)} · naive $BIT_WIDTH steps · Kernighan $kernighanCount",
    )
    return frames
}

private fun subsetsBitmaskFrames(): List<BitFrame> {
    val items = listOf("a", "b", "c")
    val width = items.size
    val frames = mutableListOf<BitFrame>()

    frames += BitFrame(
        status = "Every subset of a ${width}-element set is a ${width}-bit number: bit i means \"item ${items.joinToString("/")} at index i is in\". " +
            "There are 2^$width = ${1 shl width} of them, and counting from 0 to ${(1 shl width) - 1} enumerates them all exactly once.",
        rows = listOf(BitRow("bit i ⇒ item", items.reversed().map { BitCell(0, BitMark.ZERO) }, readout = items.reversed().joinToString(" "))),
        readout = "${1 shl width} subsets",
    )

    for (mask in 0 until (1 shl width)) {
        val chosen = items.indices.filter { (mask shr it) and 1 == 1 }.map { items[it] }
        frames += BitFrame(
            status = "mask = $mask: ${if (chosen.isEmpty()) "the empty subset" else "{ ${chosen.joinToString(", ")} }"}. " +
                "The loop body is `if (mask shr i and 1 == 1)` — no recursion, no visited set, and the mask is itself a usable " +
                "array index, which is what makes it a DP state.",
            rows = listOf(
                row("mask", mask, width = width, marks = arrayOf("result" to items.indices.filter { (mask shr it) and 1 == 1 }.toSet())),
            ),
            readout = if (chosen.isEmpty()) "{ }" else "{ ${chosen.joinToString(", ")} }",
        )
    }

    val full = (1 shl width) - 1
    val sub = 5   // 101
    val complement = full xor sub
    frames += BitFrame(
        status = "Set operations become one instruction each: union is |, intersection is &, complement is xor with the full mask, " +
            "and membership is a shift and an &. Here mask $sub is { ${items.indices.filter { (sub shr it) and 1 == 1 }.map { items[it] }.joinToString(", ")} }, " +
            "and its complement against $full is $complement.",
        rows = listOf(
            row("subset", sub, width = width),
            row("full", full, width = width),
            row("complement", complement, width = width, marks = arrayOf("result" to items.indices.filter { (complement shr it) and 1 == 1 }.toSet())),
        ),
        readout = "union | · intersect & · complement xor",
    )
    frames += BitFrame(
        status = "The ceiling is the exponent, not the encoding: 2ⁿ masks is fine to about n = 20 and hopeless at n = 40. That is " +
            "exactly the boundary Held-Karp lives on — a bitmask DP over subsets is O(2ⁿ·n²), which beats n! and is still exponential.",
        rows = listOf(row("all masks", full, width = width, marks = arrayOf("result" to items.indices.toSet()))),
        readout = "2^20 ≈ 10⁶ · 2^40 ≈ 10¹²",
    )
    return frames
}

private fun xorTricksFrames(): List<BitFrame> {
    val frames = mutableListOf<BitFrame>()

    val a = 44
    val b = 25
    frames += BitFrame(
        status = "XOR is 1 where the operands differ. That single property gives it the two features everything below uses: " +
            "x xor x = 0, and x xor 0 = x. It is its own inverse.",
        rows = listOf(
            row("a", a),
            row("b", b),
            row("a xor b", a xor b, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { ((a xor b) shr it) and 1 == 1 }.toSet())),
        ),
        readout = "$a xor $b = ${a xor b}",
    )
    frames += BitFrame(
        status = "Applying the same value twice undoes it: ($a xor $b) xor $b = ${(a xor b) xor b}, back to a. That is the whole basis of " +
            "one-time-pad encryption and of the swap below.",
        rows = listOf(
            row("a xor b", a xor b),
            row("b", b),
            row("xor b again", (a xor b) xor b, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (a shr it) and 1 == 1 }.toSet())),
        ),
        readout = "recovered a = ${(a xor b) xor b}",
    )

    // Single number: every value appears twice except one.
    val stream = listOf(4, 1, 2, 1, 2)
    var acc = 0
    frames += BitFrame(
        status = "The classic use: in ${stream.joinToString(", ")} every value appears twice except one. XOR the whole list — pairs " +
            "cancel to 0 and the odd one out survives, in O(n) time and O(1) space with no hash set and no sorting.",
        rows = listOf(row("accumulator", 0)),
        readout = "start at 0",
    )
    for (value in stream) {
        val before = acc
        acc = acc xor value
        frames += BitFrame(
            status = "xor $value: $before → $acc." + if (stream.count { it == value } == 2 && stream.indexOf(value) != stream.lastIndexOf(value) && before != 0 && acc < before) " A pair just cancelled." else "",
            rows = listOf(
                row("accumulator", before),
                row("value", value),
                row("result", acc, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (acc shr it) and 1 == 1 }.toSet())),
            ),
            readout = "accumulator = $acc",
        )
    }
    require(acc == 4) { "xor_tricks single-number gave $acc, expected 4" }
    frames += BitFrame(
        status = "The survivor is $acc — order never mattered, because XOR is commutative and associative. Note what this does not " +
            "do: it finds the element appearing an odd number of times, so it is wrong the moment the premise \"exactly twice\" is.",
        rows = listOf(row("answer", acc, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (acc shr it) and 1 == 1 }.toSet()))),
        readout = "single number = $acc",
    )

    // XOR prefix: range XOR in O(1), the same idea as a prefix sum.
    val array = listOf(3, 8, 2, 6, 4)
    val prefix = IntArray(array.size + 1)
    for (i in array.indices) prefix[i + 1] = prefix[i] xor array[i]
    val l = 1
    val r = 3
    val expected = array.subList(l, r + 1).reduce(Int::xor)
    val viaPrefix = prefix[r + 1] xor prefix[l]
    require(expected == viaPrefix) { "xor prefix disagreed: $expected vs $viaPrefix" }
    frames += BitFrame(
        status = "Because XOR is its own inverse it also supports prefix arrays, exactly like a prefix sum but with subtraction " +
            "replaced by XOR itself. Over ${array.joinToString(", ")}, the XOR of positions $l..$r is prefix[${r + 1}] xor prefix[$l] = " +
            "${prefix[r + 1]} xor ${prefix[l]} = $viaPrefix — two lookups, and the range width never enters the cost.",
        rows = listOf(
            row("prefix[${r + 1}]", prefix[r + 1]),
            row("prefix[$l]", prefix[l]),
            row("range xor", viaPrefix, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (viaPrefix shr it) and 1 == 1 }.toSet())),
        ),
        readout = "a[$l..$r] xor = $viaPrefix",
    )
    return frames
}

// ── Interview-prep pattern: two unpaired numbers ─────────────────────────────
// The single-number scan is the easy half; the interview version hides two unpaired values, and the
// split that separates them is n & −n on the XOR of everything.
private fun twoSinglesFrames(): List<BitFrame> {
    val a = listOf(4, 1, 2, 1, 3, 2)          // 4 and 3 are unpaired
    val frames = mutableListOf<BitFrame>()
    var acc = 0

    frames += BitFrame(
        status = "Every value appears twice except two of them. No hash set is allowed, so the only tool left is " +
            "XOR's self-inverse property: x ^ x = 0.",
        rows = listOf(row("acc", 0)),
        readout = "input: ${a.joinToString(", ")}",
    )

    for (x in a) {
        val before = acc
        acc = acc xor x
        frames += BitFrame(
            status = "acc ^= $x. Bits where the two agreed cancel to 0; bits where they differed survive.",
            rows = listOf(
                row("acc", before),
                row("x", x, marks = arrayOf("active" to (0 until BIT_WIDTH).filter { (x shr it) and 1 == 1 }.toSet())),
                row("acc ^ x", acc, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (acc shr it) and 1 == 1 }.toSet())),
            ),
        )
    }

    val lowest = acc and -acc
    val bitIndex = Integer.numberOfTrailingZeros(lowest)
    frames += BitFrame(
        status = "The pairs are gone, so acc = $acc is p ^ q for the two unpaired values. It cannot be 0 — they " +
            "differ somewhere — and n & −n isolates the lowest place where they differ, bit $bitIndex.",
        rows = listOf(
            row("acc = p^q", acc),
            row("−acc", -acc and ((1 shl BIT_WIDTH) - 1)),
            row("acc & −acc", lowest, marks = arrayOf("result" to setOf(bitIndex))),
        ),
        readout = "split on bit $bitIndex (value ${1 shl bitIndex})",
    )

    var withBit = 0
    var withoutBit = 0
    for (x in a) {
        if (x and lowest != 0) withBit = withBit xor x else withoutBit = withoutBit xor x
    }
    frames += BitFrame(
        status = "Split the input on bit $bitIndex. Each duplicate pair lands wholly in one group, and p and q land " +
            "in different ones — so XOR-ing each group separately leaves exactly one value in each.",
        rows = listOf(
            row("group with bit", withBit, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (withBit shr it) and 1 == 1 }.toSet())),
            row("group without", withoutBit, marks = arrayOf("result" to (0 until BIT_WIDTH).filter { (withoutBit shr it) and 1 == 1 }.toSet())),
        ),
        readout = "answers: $withBit and $withoutBit — two passes, O(1) space",
    )
    return frames
}

// ── Interview-prep pattern guides ────────────────────────────────────────────

// Maximum XOR pair through a binary trie: the greedy walk is bit by bit, from the top.
private val xorNumbers = listOf(3, 10, 5, 25, 2, 8)
private const val XOR_WIDTH = 5

private fun bitTriePatternFrames(): List<BitFrame> {
    val frames = mutableListOf<BitFrame>()

    frames += BitFrame(
        status = "Find the pair with the largest XOR among ${xorNumbers.joinToString(", ")}. Every pair is O(n²); a " +
            "binary trie of the values, most significant bit first, answers each query in $XOR_WIDTH steps.",
        rows = xorNumbers.map { row(it.toString().padStart(2), it, width = XOR_WIDTH) },
        readout = "${xorNumbers.size} values, ${XOR_WIDTH} bits each",
    )

    val query = 5
    var partner = 0
    var best = 0
    for (bit in XOR_WIDTH - 1 downTo 0) {
        val queryBit = (query shr bit) and 1
        val wanted = 1 - queryBit
        val prefixMask = (-1 shl bit) and ((1 shl XOR_WIDTH) - 1)
        val candidates = xorNumbers.filter {
            (it and prefixMask) == ((partner and (prefixMask shl 1)) or (wanted shl bit))
        }
        val taken = candidates.isNotEmpty()
        if (taken) partner = partner or (wanted shl bit) else partner = partner or (queryBit shl bit)
        best = query xor partner
        frames += BitFrame(
            status = "Bit $bit of the query is $queryBit, so the branch worth ${1 shl bit} is the one holding " +
                "$wanted. " + if (taken) {
                "The trie has ${candidates.size} value(s) down that branch (${candidates.joinToString(", ")}), so " +
                    "take it — that bit of the answer is now guaranteed 1."
            } else {
                "Nothing is stored down that branch, so the walk is forced into the $queryBit side and this bit of " +
                    "the answer is 0. Being forced never undoes a higher bit already won."
            },
            rows = listOf(
                row("query $query", query, width = XOR_WIDTH, marks = arrayOf("active" to setOf(bit))),
                row("partner", partner, width = XOR_WIDTH, marks = arrayOf("result" to setOf(bit))),
                row("xor", best, width = XOR_WIDTH, marks = arrayOf("result" to setOf(bit))),
            ),
            readout = "best so far = $best",
        )
    }

    val bruteBest = xorNumbers.flatMap { a -> xorNumbers.map { b -> a xor b } }.max()
    frames += BitFrame(
        status = "Greedy from the top is optimal because one high bit outweighs every lower bit combined: " +
            "${1 shl (XOR_WIDTH - 1)} > ${(1 shl (XOR_WIDTH - 1)) - 1}. Query $query pairs best with $partner for " +
            "$best; over all queries the maximum is $bruteBest. n insertions and n queries, $XOR_WIDTH steps each — " +
            "O(n · bits) instead of O(n²).",
        rows = listOf(
            row("query $query", query, width = XOR_WIDTH),
            row("partner", partner, width = XOR_WIDTH),
            row("xor", best, width = XOR_WIDTH, marks = arrayOf("result" to (0 until XOR_WIDTH).filter { (best shr it) and 1 == 1 }.toSet())),
        ),
        readout = "max XOR = $bruteBest",
    )
    return frames
}

// Bitmask DP: the mask is the visited set, and it is also the array index.
private val tspCities = listOf("A", "B", "C", "D")
private val tspCost = listOf(
    listOf(0, 5, 9, 4),
    listOf(5, 0, 3, 8),
    listOf(9, 3, 0, 6),
    listOf(4, 8, 6, 0),
)

private fun bitmaskStatePatternFrames(): List<BitFrame> {
    val n = tspCities.size
    val full = (1 shl n) - 1
    val frames = mutableListOf<BitFrame>()
    val dp = Array(1 shl n) { IntArray(n) { Int.MAX_VALUE / 4 } }
    dp[1][0] = 0

    fun members(mask: Int) = tspCities.indices.filter { (mask shr it) and 1 == 1 }

    frames += BitFrame(
        status = "Visit all ${n} cities once, starting at ${tspCities[0]}. The state that matters is *which* cities " +
            "are visited plus where you are — not the order you visited them in. That set is a ${n}-bit mask, and " +
            "the mask doubles as the dp array index.",
        rows = listOf(row("mask", 1, width = n, marks = arrayOf("result" to setOf(0)))),
        readout = "${1 shl n} masks × $n positions = ${(1 shl n) * n} states",
    )

    for (mask in 1..full) {
        if (mask and 1 == 0) continue
        for (last in members(mask)) {
            if (dp[mask][last] >= Int.MAX_VALUE / 4) continue
            for (next in tspCities.indices) {
                if ((mask shr next) and 1 == 1) continue
                val nextMask = mask or (1 shl next)
                val candidate = dp[mask][last] + tspCost[last][next]
                if (candidate < dp[nextMask][next]) dp[nextMask][next] = candidate
            }
        }
        val reachable = members(mask).filter { dp[mask][it] < Int.MAX_VALUE / 4 }
        if (reachable.isEmpty()) continue
        frames += BitFrame(
            status = "mask $mask = { ${members(mask).joinToString(", ") { tspCities[it] }} }. Best cost per ending " +
                "city: ${reachable.joinToString(", ") { "${tspCities[it]} ${dp[mask][it]}" }}. Every route reaching " +
                "this same set collapses into these $n numbers — that collapse is what turns ${(1..n).fold(1) { acc, k -> acc * k }} " +
                "orderings into ${(1 shl n) * n} states.",
            rows = listOf(
                row("mask", mask, width = n, marks = arrayOf("result" to members(mask).toSet())),
            ),
            readout = "visited ${members(mask).size} of $n",
        )
    }

    val best = tspCities.indices.filter { it != 0 }.minOf { dp[full][it] + tspCost[it][0] }
    frames += BitFrame(
        status = "Full mask $full — every city visited. Closing the tour back to ${tspCities[0]} costs $best. " +
            "O(2ⁿ · n²) is still exponential, but it is the difference between ${(1..n).fold(1) { acc, k -> acc * k }} " +
            "permutations at n = $n and 20! ≈ 2.4 × 10¹⁸ at n = 20, where the mask version is merely expensive.",
        rows = listOf(row("mask", full, width = n, marks = arrayOf("result" to tspCities.indices.toSet()))),
        readout = "optimal tour = $best",
    )
    return frames
}

private val bitConfigs = mapOf(
    "bit_trie_pattern" to BitConfig(
        intro = "Maximum XOR through a binary trie: walk the query's bits from the top and take the opposite branch " +
            "whenever one exists. One high bit outweighs every lower bit, so greedy is optimal.",
        legend = listOf(
            OneFill to "Set bit",
            ActiveFillBit to "Bit being decided",
            ResultFillBit to "Answer bit",
        ),
        build = ::bitTriePatternFrames,
    ),
    "bitmask_state_pattern" to BitConfig(
        intro = "Held-Karp on four cities. The mask is the visited set *and* the dp index, which is the whole trick — " +
            "routes that visit the same set collapse into one state.",
        legend = listOf(
            OneFill to "Visited",
            ResultFillBit to "In this state",
            MaskedFill to "Outside the width",
        ),
        build = ::bitmaskStatePatternFrames,
    ),
    "bit_manipulation_pattern" to BitConfig(
        intro = "Two values appear once, everything else twice. XOR collapses the pairs, then the lowest set bit of " +
            "the result splits the input into two groups that each hide exactly one answer.",
        legend = listOf(
            OneFill to "Set bit",
            ActiveFillBit to "Incoming value",
            ResultFillBit to "Result",
        ),
        build = ::twoSinglesFrames,
    ),
    "bit_basics" to BitConfig(
        intro = "One integer as eight place-value cells. Parity, powers of two and the n & (n−1) trick are all statements about " +
            "which places are occupied — the arithmetic is incidental.",
        legend = listOf(
            OneFill to "Set bit",
            ActiveFillBit to "Being tested",
            ResultFillBit to "Answer",
        ),
        build = ::bitBasicsFrames,
    ),
    "count_set_bits" to BitConfig(
        intro = "The width-driven loop and Brian Kernighan's value-driven loop on the same number, so the difference is a step " +
            "count rather than a claim.",
        legend = listOf(
            OneFill to "Set bit",
            ActiveFillBit to "Current position",
            ClearedFill to "Just cleared",
        ),
        build = ::countSetBitsFrames,
    ),
    "subsets_bitmask" to BitConfig(
        intro = "Counting from 0 to 2ⁿ−1 enumerates every subset exactly once. Each mask is both the subset and a usable array " +
            "index, which is what makes it a DP state rather than just an encoding.",
        legend = listOf(
            OneFill to "Item present",
            ResultFillBit to "In this subset",
            MaskedFill to "Outside the width",
        ),
        build = ::subsetsBitmaskFrames,
    ),
    "xor_tricks" to BitConfig(
        intro = "XOR is its own inverse, and that one fact produces the swap, the single-number scan and a prefix array that " +
            "answers range queries in constant time.",
        legend = listOf(
            OneFill to "Set bit",
            ResultFillBit to "Result",
            ZeroFill to "Cancelled to 0",
        ),
        build = ::xorTricksFrames,
    ),
)

private fun bitConfigFor(topicId: String): BitConfig =
    bitConfigs[topicId] ?: bitConfigs.getValue("bit_basics")

internal val bitBoardTopicIds: Set<String> get() = bitConfigs.keys

internal fun bitBoardFrameCount(topicId: String): Int {
    val frames = bitConfigFor(topicId).build()
    frames.forEach { frame ->
        require(frame.rows.isNotEmpty()) { "$topicId has a frame with no rows" }
        frame.rows.forEach { r ->
            require(r.cells.all { it.value == 0 || it.value == 1 }) { "$topicId has a cell that is not a bit" }
        }
    }
    return frames.size
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun BitBoardSection(topicId: String) {
    val config = remember(topicId) { bitConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 750f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabIntro(config.intro)

            frame.rows.forEach { bitRow ->
                BitRowView(bitRow, modifier = Modifier.padding(top = 10.dp))
            }

            frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 14.dp)) }

            LabCaption(frame.status, Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> BitLegend(color, label) }
            }

            PlaybackTransport(playback, captions = frames.map { it.status })
        }
    }
}

@Composable
private fun BitRowView(bitRow: BitRow, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            bitRow.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            bitRow.cells.forEach { cell ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .background(bitFill(cell.mark), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cell.value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (cell.mark == BitMark.ACTIVE) Color(0xFF1F2530) else Color.White,
                    )
                }
            }
        }
        bitRow.readout?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(56.dp).padding(start = 8.dp),
            )
        }
    }
}

private fun bitFill(mark: BitMark): Color = when (mark) {
    BitMark.ZERO -> ZeroFill
    BitMark.ONE -> OneFill
    BitMark.ACTIVE -> ActiveFillBit
    BitMark.CLEARED -> ClearedFill
    BitMark.RESULT -> ResultFillBit
    BitMark.MASKED -> MaskedFill
}

@Composable
private fun BitLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
