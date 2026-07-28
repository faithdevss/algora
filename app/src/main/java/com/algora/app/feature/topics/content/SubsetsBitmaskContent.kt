package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val subsetsBitmaskContent = TopicContent(
    topicId = "subsets_bitmask",
    whatIsIt = listOf(
        "A subset of an n-element set is a yes-or-no answer for each element, which is exactly what an n-bit integer stores. Bit i means \"element i is in\". So the integers 0 through 2ⁿ − 1 enumerate every subset exactly once, in a single flat loop with no recursion, no visited set and no duplicate suppression.",
        "The consequences go beyond enumeration. Set operations become single instructions: union is |, intersection is &, difference is `a and b.inv()`, complement is `mask xor full`, membership is `(mask shr i) and 1`, and cardinality is a popcount. And because a mask is an ordinary integer, it can be used directly as an array index — which is what turns it from an encoding into a dynamic-programming state. Held-Karp's travelling-salesman formulation is exactly this: dp[mask][i] is the best tour visiting the set `mask` and ending at city i, giving O(2ⁿ·n²) instead of O(n!).",
        "Two enumeration patterns are worth knowing beyond the flat loop. Iterating the *members* of a mask is `while (m != 0) { val i = Integer.numberOfTrailingZeros(m); m = m and (m - 1) }` — Kernighan's trick again, costing one step per member rather than n. Iterating the *submasks* of a mask is `var s = mask; while (s > 0) { …; s = (s - 1) and mask }`, which visits every subset of that mask and, summed over all masks, costs O(3ⁿ) rather than O(4ⁿ). The hard limit is the exponent, not the representation: 2²⁰ masks is about a million and entirely routine, 2⁴⁰ is a trillion and out of reach. Above roughly n = 20 to 25 the technique stops helping, and at n = 64 the mask no longer fits a Long at all.",
    ),
    steps = listOf(
        StepCard(1, "Map Bits to Elements", "Bit i set means element i is in the subset. n elements, n bits.", 0xFF10B981),
        StepCard(2, "Count from 0 to 2ⁿ − 1", "Every subset appears exactly once, in a flat loop with no recursion.", 0xFF3B82F6),
        StepCard(3, "Test Membership", "(mask shr i) and 1 — one shift and one mask per element.", 0xFFF59E0B),
        StepCard(4, "Do Set Algebra with Operators", "Union |, intersection &, complement xor full, size popcount.", 0xFF8B5CF6),
        StepCard(5, "Iterate Members, Not Positions", "m and (m − 1) walks only the elements present, not all n slots.", 0xFFEC4899),
        StepCard(6, "Use the Mask as a DP Index", "dp[mask][…] is what makes Held-Karp O(2ⁿ·n²) instead of O(n!).", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Subset count", "2ⁿ", "3 elements → 8 masks, 0 through 7."),
        FormulaEntry("Enumeration", "for (mask in 0 until (1 shl n))", "Flat, complete, and duplicate-free by construction."),
        FormulaEntry("Membership", "(mask shr i) and 1 == 1", "One shift and one and."),
        FormulaEntry("Submask enumeration", "s = (s − 1) and mask", "All submasks of every mask sums to O(3ⁿ), not O(4ⁿ)."),
        FormulaEntry("Held-Karp", "O(2ⁿ·n²) time, O(2ⁿ·n) space", "The mask is the DP state; beats O(n!) decisively."),
        FormulaEntry("Practical ceiling", "n ≈ 20–25", "2²⁰ ≈ 10⁶ is routine; 2⁴⁰ ≈ 10¹² is not."),
    ),
    notationKey = listOf(
        NotationEntry("mask", "an integer whose set bits are the chosen elements"),
        NotationEntry("full mask", "(1 shl n) − 1 — every element present"),
        NotationEntry("submask", "a mask whose set bits are a subset of another mask's"),
        NotationEntry("popcount", "the subset's cardinality"),
        NotationEntry("1 shl i", "the singleton mask for element i"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Enumerating subsets, members and submasks",
            accentColor = 0xFF10B981,
            code = """
                fun <T> subsets(items: List<T>): List<List<T>> {
                    val result = mutableListOf<List<T>>()
                    for (mask in 0 until (1 shl items.size)) {
                        // Each mask is one subset, and every subset appears exactly once.
                        result += items.indices.filter { (mask shr it) and 1 == 1 }.map { items[it] }
                    }
                    return result
                }

                subsets(listOf("a", "b", "c"))
                // [[], [a], [b], [a, b], [c], [a, c], [b, c], [a, b, c]]  — masks 0 … 7

                // Walking only the members: one step per element present, not per position.
                fun membersOf(mask: Int): List<Int> {
                    var m = mask
                    val members = mutableListOf<Int>()
                    while (m != 0) {
                        members += Integer.numberOfTrailingZeros(m)
                        m = m and (m - 1)          // Kernighan again
                    }
                    return members
                }

                // Walking every submask of a mask. Over all masks this totals O(3ⁿ) — each
                // element is in the mask, in the submask, or in neither, so three states.
                fun forEachSubmask(mask: Int, action: (Int) -> Unit) {
                    var s = mask
                    while (s > 0) {
                        action(s)
                        s = (s - 1) and mask
                    }
                    action(0)                      // the loop stops before reaching the empty one
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why it is a DP state: Held-Karp",
            accentColor = 0xFF06B6D4,
            code = """
                // Shortest tour visiting every city once. The mask is a subset of visited
                // cities and doubles as an array index, which is the whole trick: every
                // permutation reaching the same set and ending at the same city collapses
                // into one state.
                fun heldKarp(dist: Array<IntArray>): Int {
                    val n = dist.size
                    val full = (1 shl n) - 1
                    val inf = Int.MAX_VALUE / 2
                    val dp = Array(1 shl n) { IntArray(n) { inf } }
                    dp[1][0] = 0                             // start at city 0, only it visited

                    for (mask in 1..full) {
                        for (last in 0 until n) {
                            if (dp[mask][last] == inf || (mask shr last) and 1 == 0) continue
                            for (next in 0 until n) {
                                if ((mask shr next) and 1 == 1) continue   // already visited
                                val nextMask = mask or (1 shl next)
                                val candidate = dp[mask][last] + dist[last][next]
                                if (candidate < dp[nextMask][next]) dp[nextMask][next] = candidate
                            }
                        }
                    }
                    return (1 until n).minOf { dp[full][it] + dist[it][0] }
                }

                // 2ⁿ·n states instead of n! tours: n = 20 is about 20 million states and
                // feasible; 20! is 2.4 × 10¹⁸ and is not. The mask is what buys that.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BitBoardPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Exact Combinatorial Search", "Travelling salesman, set cover and assignment problems on small n, solved exactly rather than approximately."),
        ApplicationCard("stack", 0xFF3B82F6, "Feature & Flag Sets", "Compact permission sets, feature toggles and state machines where set algebra is one instruction."),
        ApplicationCard("bulb", 0xFF06B6D4, "Constraint Propagation", "Sudoku and N-Queens solvers keep candidate sets as masks so intersection and elimination are single operations."),
    ),
    takeaways = listOf(
        "An n-bit integer is a subset, so counting 0 … 2ⁿ − 1 enumerates every subset exactly once with no recursion.",
        "Union, intersection, complement and cardinality all become single operations on that integer.",
        "The mask doubles as an array index, which is what makes it a DP state — Held-Karp is O(2ⁿ·n²) rather than O(n!).",
        "The ceiling is the exponent: about n = 20–25 in practice, and the mask stops fitting a Long at 64.",
    ),
    crossLinks = listOf(
        CrossLink("bitmask_dp", "Bitmask DP"),
        CrossLink("bit_basics", "Bit Basics"),
        CrossLink("hamiltonian_path", "Hamiltonian Path & Circuit"),
    ),
)
