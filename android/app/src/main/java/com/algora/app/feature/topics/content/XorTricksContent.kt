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

internal val xorTricksContent = TopicContent(
    topicId = "xor_tricks",
    figure = Figure(
        caption = "Everything follows from x xor x = 0 and x xor 0 = x. Those make XOR its own inverse, " +
            "and it is commutative and associative besides, so the order of a whole sequence never " +
            "matters — the two pairs here annihilate each other despite being interleaved rather than " +
            "adjacent, and the running value returns to the singleton. That is O(n) time and O(1) " +
            "space, against a hash set's O(n) space or a sort's O(n log n) time. Being self-inverse " +
            "also gives XOR a prefix array exactly like a prefix sum, with subtraction replaced by XOR " +
            "itself: the XOR of a range is prefix[r+1] xor prefix[l], two lookups whatever the width. " +
            "Two classics deserve caution rather than recommendation — the swap without a temporary " +
            "zeroes the value when both operands are the same location, which happens the moment i == j " +
            "on an array, and is slower than a temporary on any modern compiler; and the XOR linked " +
            "list halves memory while breaking garbage collection, ASan and every debugger.",
        shape = FigureShape.Strip(
            cells = listOf("4", "1", "2", "1", "2"),
            bands = listOf(
                FigureBand(0, 0, "odd one", FigureTone.Accent),
                FigureBand(1, 4, "two pairs"),
            ),
            aux = listOf("4", "5", "7", "6", "4"),
            auxLabel = "running XOR — it wanders and then lands back on the unpaired value",
        ),
    ),
    whatIsIt = listOf(
        "XOR sets a bit where its two operands differ. Everything interesting about it follows from two consequences of that: x xor x = 0, and x xor 0 = x. Together they make XOR its own inverse — applying the same value twice returns you to where you started — and the operation is commutative and associative besides, so the order of a whole sequence of XORs never matters.",
        "The best-known use is finding the element that appears an odd number of times. XOR a list where every value is paired except one, and the pairs annihilate each other regardless of how they are interleaved, leaving the singleton: 4 xor 1 xor 2 xor 1 xor 2 = 4. That is O(n) time and O(1) space, against a hash set's O(n) space or a sort's O(n log n) time. Being its own inverse also gives XOR a prefix array exactly like a prefix sum, with subtraction replaced by XOR itself: the XOR of a range is prefix[r+1] xor prefix[l], two lookups whatever the range width.",
        "Two classic tricks deserve a caution rather than a recommendation. The three-step swap without a temporary, a ^= b; b ^= a; a ^= b, is correct only when the two operands are distinct storage locations — call it with the same variable twice, or with i == j on an array, and it zeroes the value instead of swapping it. It is also slower than a temporary on any modern compiler, which sees the temporary and uses a register. The XOR linked list, which stores prev xor next in one pointer field per node, halves memory and breaks garbage collection, ASan, and every debugger, so it survives only in constrained embedded code. Where XOR genuinely earns its place is the algorithmic uses: the odd-occurrence scan, the two-singletons variant that splits the input on a differing bit, Nim's game-theoretic XOR of pile sizes, and Gray codes, where n xor (n shr 1) yields a sequence of integers each differing from the last in exactly one bit.",
    ),
    steps = listOf(
        StepCard(1, "Read It as Difference", "A bit is set where the operands disagree. Everything below follows from that.", 0xFF10B981),
        StepCard(2, "Use x xor x = 0", "Pairs annihilate, so duplicates vanish from a running XOR.", 0xFF3B82F6),
        StepCard(3, "Use x xor 0 = x", "The identity, which makes 0 the correct accumulator to start from.", 0xFFF59E0B),
        StepCard(4, "Scan for the Odd One Out", "XOR the whole list; the survivor appeared an odd number of times.", 0xFF8B5CF6),
        StepCard(5, "Build a Prefix Array", "Its own inverse, so range XOR is prefix[r+1] xor prefix[l] — two lookups.", 0xFFEC4899),
        StepCard(6, "Split on a Differing Bit", "Two singletons: XOR everything, take any set bit, and partition the input on it.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Self-inverse", "x xor x = 0, x xor 0 = x", "The two facts every trick here rests on."),
        FormulaEntry("Commutative & associative", "order is irrelevant", "Why the single-number scan works on any permutation."),
        FormulaEntry("Single number", "O(n) time, O(1) space", "4 xor 1 xor 2 xor 1 xor 2 = 4."),
        FormulaEntry("Range XOR", "prefix[r+1] xor prefix[l]", "Two lookups; the range width never enters the cost."),
        FormulaEntry("Two singletons", "split on any set bit of the total XOR", "The two answers must differ there, so they land in different halves."),
        FormulaEntry("Gray code", "g(n) = n xor (n shr 1)", "Consecutive codes differ in exactly one bit."),
    ),
    notationKey = listOf(
        NotationEntry("xor", "Kotlin's exclusive-or; Java's ^"),
        NotationEntry("involution", "an operation that is its own inverse"),
        NotationEntry("accumulator", "the running XOR, correctly seeded at 0"),
        NotationEntry("prefix XOR", "prefix[i] = a₀ xor … xor aᵢ₋₁"),
        NotationEntry("Nim-sum", "the XOR of the pile sizes; zero means the position is lost for the player to move"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The scans",
            accentColor = 0xFF10B981,
            code = """
                // Every value paired except one. Pairs cancel wherever they sit in the list.
                fun singleNumber(nums: List<Int>): Int = nums.fold(0) { acc, n -> acc xor n }

                singleNumber(listOf(4, 1, 2, 1, 2))   // 4

                // Range XOR in O(1), the same construction as a prefix sum with subtraction
                // replaced by XOR — which works precisely because XOR undoes itself.
                class XorPrefix(a: List<Int>) {
                    private val prefix = IntArray(a.size + 1).also {
                        for (i in a.indices) it[i + 1] = it[i] xor a[i]
                    }
                    fun rangeXor(l: Int, r: Int) = prefix[r + 1] xor prefix[l]
                }

                XorPrefix(listOf(3, 8, 2, 6, 4)).rangeXor(1, 3)   // 12 = 8 xor 2 xor 6

                // Two values appear once, everything else twice. The total XOR is those two
                // XORed together, so any set bit in it is a position where they differ —
                // partition on it and each half has exactly one singleton.
                fun twoSingles(nums: List<Int>): Pair<Int, Int> {
                    val both = nums.fold(0) { acc, n -> acc xor n }
                    val differingBit = both and -both       // any set bit will do; take the lowest
                    var first = 0
                    var second = 0
                    for (n in nums) {
                        if (n and differingBit != 0) first = first xor n else second = second xor n
                    }
                    return first to second
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The tricks that are traps, and the ones that are not",
            accentColor = 0xFFEF4444,
            code = """
                // The famous swap. It is correct only when a and b are distinct locations.
                fun swapInPlace(array: IntArray, i: Int, j: Int) {
                    if (i == j) return                 // ← without this line, array[i] becomes 0
                    array[i] = array[i] xor array[j]
                    array[j] = array[i] xor array[j]
                    array[i] = array[i] xor array[j]
                }
                // And it is slower than `val t = a; a = b; b = t` on any modern compiler, which
                // keeps the temporary in a register. Use it to explain XOR, not to swap.

                // The XOR linked list stores prev xor next in one field per node, halving
                // pointer memory. It also defeats garbage collection, every memory sanitiser
                // and every debugger, because no field ever holds a real address. It belongs
                // in constrained embedded code and nowhere else.

                // Two that are simply useful:
                fun grayCode(n: Int) = n xor (n shr 1)   // consecutive codes differ in one bit
                // 0,1,3,2,6,7,5,4 — used where a multi-bit reading must never be caught
                // mid-transition, as in rotary encoders.

                fun nimWinning(piles: List<Int>) = piles.fold(0) { acc, p -> acc xor p } != 0
                // A Nim position is losing for the player to move exactly when the XOR of the
                // pile sizes is zero. That is a theorem, not a heuristic.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BitBoardPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "RAID & Checksums", "RAID 5 parity is the XOR of the data blocks, so any one lost drive is recovered by XORing the rest."),
        ApplicationCard("finance", 0xFF3B82F6, "Cryptography", "The one-time pad and every stream cipher are XOR with a keystream; decryption is the identical operation."),
        ApplicationCard("bulb", 0xFF06B6D4, "Game Theory & Encoding", "Nim-sums decide Nim positions outright, and Gray codes give single-bit-transition encodings for encoders."),
    ),
    takeaways = listOf(
        "x xor x = 0 and x xor 0 = x — every trick here is one of those two facts applied.",
        "XORing a list leaves the value occurring an odd number of times, in O(n) time and O(1) space.",
        "Being its own inverse gives it a prefix array, so a range XOR is two lookups.",
        "The XOR swap breaks when both operands are the same location and is slower than a temporary — know it, do not ship it.",
    ),
    crossLinks = listOf(
        CrossLink("bit_basics", "Bit Basics"),
        CrossLink("prefix_sum", "Prefix Sum"),
        CrossLink("count_set_bits", "Count Set Bits"),
    ),
)
