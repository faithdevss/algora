package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val bitManipulationPatternContent = TopicContent(
    topicId = "bit_manipulation_pattern",
    figure = Figure(
        caption = "Read the row by position rather than value: the shaded cells are the 32s and the 4s " +
            "place, and 44 is simply which places are occupied. Every trick here — n & (n−1), n & −n, " +
            "the XOR cancel — is a statement about position, so each is one instruction, not a loop.",
        shape = FigureShape.Strip(
            cells = listOf("0", "0", "1", "0", "1", "1", "0", "0"),
            bands = listOf(
                FigureBand(2, 2, "highest set", FigureTone.Accent),
                FigureBand(5, 5, "lowest set: n & −n", FigureTone.Primary),
            ),
            aux = listOf("128", "64", "32", "16", "8", "4", "2", "1"),
            auxLabel = "place values — 44 = 32 + 8 + 4",
        ),
    ),
    whatIsIt = listOf(
        "Bit problems reduce to a short vocabulary: XOR cancels pairs, n & (n−1) clears the lowest set bit, n & −n isolates it, and a mask is a set of up to 32 or 64 members held in one integer.",
        "The interview version usually hides one of those identities behind a story — \"every number appears twice except one\" is XOR's self-inverse property and nothing else.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Constant space demanded on a counting problem, duplicate/missing values, or subsets of ≤ 20 items.", 0xFFF59E0B),
        StepCard(2, "Reach for XOR", "x ^ x = 0 and x ^ 0 = x, so folding a list with XOR erases every value that appears an even number of times.", 0xFF3B82F6),
        StepCard(3, "Isolate a Bit", "n & −n gives the lowest set bit — the bit that separates two unpaired values into two groups.", 0xFF8B5CF6),
        StepCard(4, "Mask a Set", "Bit i means \"element i is in\". Iterate masks 0..2ⁿ−1 to enumerate subsets; popcount gives the size.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Single number", "O(n) time, O(1) space", "XOR the whole array; pairs cancel."),
        FormulaEntry("Clear lowest set bit", "n & (n − 1)", "Loop count = popcount, so it beats scanning all 32 bits."),
        FormulaEntry("Isolate lowest set bit", "n & −n", "Two's complement makes −n the flipped bits plus one."),
    ),
    notationKey = listOf(
        NotationEntry("^", "XOR — addition without carry, its own inverse"),
        NotationEntry("&, |, ~", "and, or, not"),
        NotationEntry("n >> k, n << k", "shift right/left by k, i.e. divide/multiply by 2^k"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two unpaired numbers (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def two_singles(a):
                    xor_all = 0
                    for x in a:
                        xor_all ^= x               # = p ^ q, the two unpaired values
                    bit = xor_all & -xor_all       # a bit where p and q differ
                    p = q = 0
                    for x in a:
                        if x & bit:
                            p ^= x                 # group with the bit set
                        else:
                            q ^= x                 # group without it
                    return p, q
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BitBoardPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Missing & Duplicate", "Single number I/II/III, missing number, finding the duplicate."),
        ApplicationCard("chip", 0xFF10B981, "Compact State", "Bitmask DP over ≤ 20 items — visited sets and permissions in one word."),
        ApplicationCard("functions", 0xFF8B5CF6, "Fast Arithmetic", "Power-of-two tests, popcount, swaps and multiplication by shifts."),
    ),
    takeaways = listOf(
        "XOR is self-inverse — that single fact solves most \"appears twice except one\" problems.",
        "n & (n−1) loops once per set bit, not once per bit width.",
        "n & −n isolates the lowest set bit and is how you split into two XOR groups.",
        "Watch the language: shifting a signed 32-bit value or using >> on negatives is where these break.",
    ),
    crossLinks = listOf(
        CrossLink("xor_tricks", "XOR Tricks (Algorithms)"),
        CrossLink("bit_basics", "Bit Basics (Algorithms)"),
        CrossLink("subsets_bitmask", "Subsets using Bitmask (Algorithms)"),
    ),
)
