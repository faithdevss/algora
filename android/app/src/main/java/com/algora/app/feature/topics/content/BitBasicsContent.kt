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

internal val bitBasicsContent = TopicContent(
    topicId = "bit_basics",
    figure = Figure(
        caption = "n = 12 is 1100. Subtracting one flips the lowest set bit off and turns every bit " +
            "below it on, so n and (n − 1) has nothing left to keep there and clears exactly that bit, " +
            "leaving 1000. Several tests are corollaries of that one identity. A power of two has " +
            "exactly one set bit, so n and (n − 1) == 0 identifies one in a single instruction — with " +
            "the guard n > 0, which is mandatory, because zero passes the test and is not a power of " +
            "two. Repeating the operation until the value reaches zero counts set bits at one iteration " +
            "per bit, which is Kernighan's algorithm. Its mirror image, n and −n, keeps only the lowest " +
            "set bit and is what a Fenwick tree walks. The honest caveat is readability: each of these " +
            "is one instruction and belongs in a hot path, but n % 2 == 0 tells a reader what is meant " +
            "and n and 1 == 0 does not.",
        shape = FigureShape.Strip(
            cells = listOf("1", "1", "0", "0"),
            bands = listOf(
                FigureBand(0, 0, "survives"),
                FigureBand(1, 1, "cleared", FigureTone.Accent),
            ),
            aux = listOf("1", "0", "1", "1"),
            auxLabel = "n − 1 = 1011 — the lowest set bit off, everything below it on",
        ),
    ),
    whatIsIt = listOf(
        "An integer is a row of places, each worth a power of two, and bit manipulation is arithmetic performed by naming places rather than by computing values. The operators are the whole vocabulary: & keeps bits set in both operands, | keeps bits set in either, xor keeps bits where they differ, inv flips every bit, and shl / shr slide the whole row left or right.",
        "Three idioms account for most real uses. n and 1 isolates the ones place, which alone decides parity — and it never yields a negative result, whereas Kotlin's % returns −1 for −3 % 2, so only the test n % 2 == 1 breaks on negative odd numbers (n % 2 == 0 and n % 2 != 0 are fine). Shifting by k multiplies or divides by 2ᵏ, though shr on a negative number keeps the sign bit and therefore rounds toward negative infinity rather than toward zero, which is not what integer division does. And n and (n − 1) clears the lowest set bit, because subtracting one flips that bit off and turns everything below it on, leaving nothing for the & to keep.",
        "That last identity is the one worth internalising, because several tests are corollaries of it. A power of two has exactly one set bit, so n and (n − 1) == 0 identifies one in a single instruction — with the caveat that zero passes the test and is not a power of two, so the guard n > 0 is mandatory. Repeating the operation until the value reaches zero counts the set bits in one iteration per bit, which is Brian Kernighan's algorithm. Its mirror image, n and −n, keeps only the lowest set bit and is what a Fenwick tree walks. The honest caveat is readability: these compile to one instruction each and belong in hot paths, but n % 2 == 0 tells a reader what is meant and n and 1 == 0 does not, so outside those paths the clear form wins.",
    ),
    steps = listOf(
        StepCard(1, "Read the Places", "Bit at position p is worth 2ᵖ. Every idiom below is a statement about which places are occupied.", 0xFF10B981),
        StepCard(2, "Mask with &", "n and 1 keeps only the ones place — parity, in one instruction, negatives included.", 0xFF3B82F6),
        StepCard(3, "Shift to Scale", "shl k multiplies by 2ᵏ; shr k divides, but rounds toward negative infinity on negatives.", 0xFFF59E0B),
        StepCard(4, "Clear the Lowest Set Bit", "n − 1 flips it off and fills below it with 1s, so n and (n − 1) removes exactly that bit.", 0xFF8B5CF6),
        StepCard(5, "Test for a Power of Two", "n > 0 && n and (n − 1) == 0. The n > 0 guard is not optional — zero passes otherwise.", 0xFFEF4444),
        StepCard(6, "Isolate the Lowest Set Bit", "n and −n keeps that bit and nothing else — the step a Fenwick tree walks.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Parity", "n and 1", "0 when even. Never negative; n % 2 == 1 fails on negative odds, though n % 2 == 0 is fine."),
        FormulaEntry("Scale", "n shl k = n · 2ᵏ", "shr divides, but rounds toward −∞ on negative values."),
        FormulaEntry("Clear lowest set bit", "n and (n − 1)", "44 and 43 = 40 — the 4 is gone."),
        FormulaEntry("Power of two", "n > 0 && n and (n − 1) == 0", "Exactly one set bit. Zero fails the guard, not the test."),
        FormulaEntry("Isolate lowest set bit", "n and −n", "44 and −44 = 4."),
        FormulaEntry("Toggle a bit", "n xor (1 shl k)", "Set with or, clear with `and (1 shl k).inv()`."),
    ),
    notationKey = listOf(
        NotationEntry("and, or, xor, inv", "Kotlin's bitwise operators — Java's &, |, ^, ~"),
        NotationEntry("shl, shr, ushr", "left shift, arithmetic right shift (sign-preserving), logical right shift"),
        NotationEntry("mask", "a value whose set bits select the positions an operation applies to"),
        NotationEntry("lowest set bit", "the least significant 1 — what n and −n isolates"),
        NotationEntry("two's complement", "why −n is inv(n) + 1, and why n and −n works"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The idioms",
            accentColor = 0xFF10B981,
            code = """
                fun isEven(n: Int) = n and 1 == 0
                // n % 2 == 0 is also correct for negatives; only n % 2 == 1 is not:
                //   (-3) % 2  == -1   → the == 0 test is fine but the == 1 test is not
                //   (-3) and 1 == 1   → always 0 or 1, never negative

                fun isPowerOfTwo(n: Int) = n > 0 && n and (n - 1) == 0
                // The n > 0 guard is load-bearing: 0 and (0 - 1) == 0, and 0 is not a power of two.

                fun lowestSetBit(n: Int) = n and -n           // 44 and -44 = 4
                fun clearLowestSetBit(n: Int) = n and (n - 1) // 44 and 43   = 40

                fun getBit(n: Int, k: Int) = (n shr k) and 1
                fun setBit(n: Int, k: Int) = n or (1 shl k)
                fun clearBit(n: Int, k: Int) = n and (1 shl k).inv()
                fun toggleBit(n: Int, k: Int) = n xor (1 shl k)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why n & (n − 1) works, and where the shortcuts bite",
            accentColor = 0xFFEF4444,
            code = """
                //   n      = 44  = 0010 1100
                //   n - 1  = 43  = 0010 1011   ← lowest set bit off, everything below it on
                //   n & (n-1)    = 0010 1000 = 40
                // Subtracting 1 borrows through the trailing zeros, so the two operands agree
                // nowhere at or below that bit and the & drops it.

                //   -n is inv(n) + 1, which is n's trailing zeros preserved, the lowest set bit
                //   preserved, and everything above it flipped — so n and -n keeps just that bit.

                // Two traps that survive code review because they look like arithmetic:
                (-7) shr 1      // -4, not -3 — arithmetic shift rounds toward negative infinity
                (-7) / 2        // -3         — integer division rounds toward zero
                (-7) ushr 1     // 2147483644 — logical shift, sign bit becomes a value bit

                // And one that is not a trap but a style choice: all of the above are single
                // instructions, but `n % 2 == 0` states the intent and `n and 1 == 0` states
                // the mechanism. Reach for the mechanism only where the instruction count matters.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BitBoardPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Flags & Permissions", "A single integer holding 32 independent booleans, each set, cleared and tested in one instruction."),
        ApplicationCard("stack", 0xFF3B82F6, "Data Structures", "Fenwick trees walk by n and −n; hash tables size to powers of two so index = hash and (size − 1)."),
        ApplicationCard("bulb", 0xFFEC4899, "Graphics & Compression", "Colour channel packing, alignment rounding and bit-level codecs are all mask-and-shift work."),
    ),
    takeaways = listOf(
        "Bit positions are place values, and every idiom here is a statement about which places are occupied.",
        "n and (n − 1) clears the lowest set bit; the power-of-two test and Kernighan's counter are both corollaries.",
        "The n > 0 guard on the power-of-two test is mandatory — zero passes the bit test.",
        "shr on a negative rounds toward −∞ while integer division rounds toward zero; they are not interchangeable.",
    ),
    crossLinks = listOf(
        CrossLink("count_set_bits", "Count Set Bits"),
        CrossLink("xor_tricks", "XOR Tricks"),
        CrossLink("fenwick_tree", "Fenwick Tree"),
    ),
)
