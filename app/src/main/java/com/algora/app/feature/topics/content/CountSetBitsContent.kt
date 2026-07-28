package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val countSetBitsContent = TopicContent(
    topicId = "count_set_bits",
    whatIsIt = listOf(
        "The population count, or popcount, of an integer is how many of its bits are 1. The obvious implementation walks every bit position, tests it, and adds — 32 iterations for an Int no matter what the value is, because the loop is driven by the type's width rather than by the number.",
        "Brian Kernighan's algorithm is driven by the value instead. Repeatedly apply n = n and (n − 1), which clears the lowest set bit each time, and count the iterations until n reaches zero. There is one iteration per set bit and none per empty position, so counting the bits of 156 — that is 10011100 — takes four iterations rather than eight. On sparse values the difference is large and on dense ones there is none, which is exactly the trade: the loop is now O(popcount) instead of O(width).",
        "Neither is what you should write in production. Integer.bitCount compiles to a single POPCNT instruction on any x86-64 or ARM64 chip made in the last fifteen years, so it beats both loops by an order of magnitude and is clearer besides. The loops are worth understanding anyway, because Kernighan's trick reappears wherever subsets are enumerated, and because the third approach — a precomputed table of counts for every 8- or 16-bit chunk — is still how you count bits across a large array or on hardware without the instruction. There is also a branchless divide-and-conquer form, the SWAR algorithm, which sums bits pairwise then in nibbles then in bytes using masked shifts and additions in a fixed twelve operations, and that is what a compiler emits when it cannot use POPCNT.",
    ),
    steps = listOf(
        StepCard(1, "Count the Naive Way", "One iteration per bit position, testing n shr i and 1. Cost is the width, not the value.", 0xFF3B82F6),
        StepCard(2, "Clear Instead of Test", "n and (n − 1) removes the lowest set bit — no position needs to be examined.", 0xFF10B981),
        StepCard(3, "Loop Until Zero", "Each iteration removes exactly one 1, so the iteration count is the answer.", 0xFFF59E0B),
        StepCard(4, "Compare on Sparse Input", "One set bit in a 32-bit word: 1 iteration against 32.", 0xFF8B5CF6),
        StepCard(5, "Precompute for Bulk Work", "A 256-entry table counts a byte at a time — four lookups for an Int.", 0xFFEC4899),
        StepCard(6, "Use the Instruction", "Integer.bitCount is one CPU instruction and beats every loop here.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("Naive", "O(width)", "32 iterations for an Int regardless of the value."),
        FormulaEntry("Kernighan", "O(popcount)", "One iteration per set bit; 4 for 156 = 10011100."),
        FormulaEntry("The trick", "n and (n − 1) clears the lowest set bit", "Because n − 1 flips it off and fills below it."),
        FormulaEntry("Table lookup", "O(width / chunk)", "A 256-entry byte table gives 4 lookups per Int."),
        FormulaEntry("SWAR", "12 branchless operations", "Pairwise, then nibbles, then bytes — fixed cost, no loop."),
        FormulaEntry("Hardware", "1 instruction", "POPCNT / CNT — what Integer.bitCount compiles to."),
    ),
    notationKey = listOf(
        NotationEntry("popcount", "population count — the number of 1 bits"),
        NotationEntry("Hamming weight", "the same quantity, named for its use in coding theory"),
        NotationEntry("Hamming distance", "popcount(a xor b) — how many positions two values differ in"),
        NotationEntry("SWAR", "SIMD Within A Register — parallel sub-word arithmetic in one machine word"),
        NotationEntry("width vs value", "the distinction between the two loops: 32 versus the number of 1s"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Three loops, one answer",
            accentColor = 0xFF10B981,
            code = """
                // Driven by the width: 32 iterations for any Int, including 0.
                fun countNaive(value: Int): Int {
                    var count = 0
                    for (position in 0 until 32) {
                        count += (value shr position) and 1
                    }
                    return count
                }

                // Driven by the value: one iteration per set bit, and none for the zeros.
                fun countKernighan(value: Int): Int {
                    var n = value
                    var count = 0
                    while (n != 0) {
                        n = n and (n - 1)   // drop the lowest set bit
                        count++
                    }
                    return count
                }

                countNaive(156)       // 4, in 32 iterations
                countKernighan(156)   // 4, in 4  — 156 is 10011100
                Integer.bitCount(156) // 4, in one POPCNT instruction

                // Note countKernighan's shape is why it generalises: "keep removing the lowest
                // set bit" is also how you iterate a bitmask's members.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Bulk counting: table and SWAR",
            accentColor = 0xFFEC4899,
            code = """
                // A byte at a time — still the right answer on hardware with no popcount, and
                // the way you count bits over a large buffer.
                private val byteCounts = IntArray(256) { Integer.bitCount(it) }

                fun countByTable(value: Int): Int =
                    byteCounts[value and 0xFF] +
                        byteCounts[(value ushr 8) and 0xFF] +
                        byteCounts[(value ushr 16) and 0xFF] +
                        byteCounts[(value ushr 24) and 0xFF]

                // Branchless SWAR: sum bits in pairs, then in nibbles, then in bytes, each
                // stage using the previous stage's partial sums held side by side in the word.
                fun countSwar(value: Int): Int {
                    var v = value
                    v -= (v ushr 1) and 0x55555555          // pairs
                    v = (v and 0x33333333) + ((v ushr 2) and 0x33333333)   // nibbles
                    v = (v + (v ushr 4)) and 0x0F0F0F0F     // bytes
                    return (v * 0x01010101) ushr 24         // sum the four bytes at once
                }

                // A common application, and the one that named the quantity:
                fun hammingDistance(a: Int, b: Int) = Integer.bitCount(a xor b)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BitBoardPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Bitsets & Indexes", "Cardinality of a compressed bitmap index, and the inner loop of bitmap-based query engines."),
        ApplicationCard("globe", 0xFF3B82F6, "Error Correction & Similarity", "Hamming distance is popcount(a xor b) — used in coding theory and in nearest-neighbour search over binary hashes."),
        ApplicationCard("bulb", 0xFF8B5CF6, "Bitmask DP", "Iterating subset states by size needs a popcount per mask, which is why the fast version matters there."),
    ),
    takeaways = listOf(
        "The naive loop costs the type's width; Kernighan's costs the number of set bits.",
        "n and (n − 1) clears the lowest set bit, so the iteration count is the answer.",
        "The saving is real only on sparse values — on a dense word both loops do the same work.",
        "In production use Integer.bitCount: one instruction, and clearer than either loop.",
    ),
    crossLinks = listOf(
        CrossLink("bit_basics", "Bit Basics"),
        CrossLink("subsets_bitmask", "Subsets using Bitmask"),
        CrossLink("bitmask_dp", "Bitmask DP"),
    ),
)
