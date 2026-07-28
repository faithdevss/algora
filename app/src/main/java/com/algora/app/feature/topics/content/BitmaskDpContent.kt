package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bitmaskDpContent = TopicContent(
    topicId = "bitmask_dp",
    whatIsIt = listOf(
        "Bitmask DP encodes a set as the bits of an integer, so \"which items have I used so far\" becomes an array index instead of a collection.",
        "That turns an exponential search over subsets into a table of 2^n entries filled once. The canonical example is the travelling salesman problem: dp[mask][i] is the cheapest way to visit exactly the cities in `mask` and stop at city i — O(2^n · n²) instead of O(n!).",
    ),
    steps = listOf(
        StepCard(1, "Number the Items", "Item k owns bit k, so a subset is one integer in [0, 2^n).", 0xFFEC4899),
        StepCard(2, "Define dp[mask][last]", "Best cost to have covered exactly `mask`, currently sitting at `last`.", 0xFF3B82F6),
        StepCard(3, "Iterate Masks Upward", "Masks only ever grow, so processing them in increasing numeric order respects the dependency order.", 0xFFF59E0B),
        StepCard(4, "Extend by One Bit", "From (mask, last), move to an unused city next: dp[mask | 1<<next][next] = min(…, dp[mask][last] + cost).", 0xFF10B981),
        StepCard(5, "Close the Tour", "Answer = min over last of dp[full][last] + cost(last → start).", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("TSP transition", "dp[mask | 1<<j][j] = min(dp[mask][i] + c[i][j])  for j ∉ mask", "One city added per step."),
        FormulaEntry("Subset count", "2^n masks", "Practical only up to n ≈ 20–22."),
        FormulaEntry("Time / space", "O(2^n · n²) / O(2^n · n)", "Every (mask, last, next) triple is examined once."),
        FormulaEntry("Submask enumeration", "for (s = mask; s > 0; s = (s−1) & mask)", "Walks every submask in O(3^n) total."),
    ),
    notationKey = listOf(
        NotationEntry("mask", "integer whose set bits are the chosen items"),
        NotationEntry("1 shl k", "the bit for item k"),
        NotationEntry("mask and (1 shl k) != 0", "test: is item k already used?"),
        NotationEntry("full", "(1 shl n) − 1, the all-items mask"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Held-Karp TSP",
            accentColor = 0xFF6366F1,
            code = """
                fun tsp(cost: Array<IntArray>): Int {
                    val n = cost.size
                    val full = (1 shl n) - 1
                    val inf = Int.MAX_VALUE / 2
                    val dp = Array(1 shl n) { IntArray(n) { inf } }
                    dp[1][0] = 0                                   // start at city 0, only it visited

                    for (mask in 1..full) {
                        if (mask and 1 == 0) continue              // every tour includes the start
                        for (last in 0 until n) {
                            val here = dp[mask][last]
                            if (here == inf || mask and (1 shl last) == 0) continue
                            for (next in 0 until n) {
                                if (mask and (1 shl next) != 0) continue
                                val nextMask = mask or (1 shl next)
                                val candidate = here + cost[last][next]
                                if (candidate < dp[nextMask][next]) dp[nextMask][next] = candidate
                            }
                        }
                    }

                    return (0 until n).minOf { last -> dp[full][last] + cost[last][0] }
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Useful bit idioms",
            accentColor = 0xFF10B981,
            code = """
                val has = mask and (1 shl k) != 0        // is k in the set?
                val added = mask or (1 shl k)            // add k
                val removed = mask and (1 shl k).inv()   // remove k
                val size = Integer.bitCount(mask)        // |set|
                val lowest = mask and -mask              // lowest set bit

                // Every subset of `mask`, largest first, in O(2^|mask|).
                var s = mask
                while (s > 0) { /* use s */ s = (s - 1) and mask }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("map", 0xFFEC4899, "Route Optimization", "Delivery and drilling tours over a couple of dozen stops are solved exactly with Held-Karp."),
        ApplicationCard("chip", 0xFF3B82F6, "Task Assignment", "Matching n workers to n jobs at minimum cost is a bitmask DP over assigned workers."),
        ApplicationCard("stack", 0xFFF59E0B, "Grid Tiling & Profile DP", "The state is the occupancy mask of one row's boundary, extended column by column."),
    ),
    takeaways = listOf(
        "A subset becomes an array index — that is the entire idea.",
        "Iterating masks in increasing order works because adding a bit only ever increases the number.",
        "2^n grows fast: bitmask DP is an exact method for small n, not a scalable one.",
        "The submask loop `(s−1) & mask` is the standard tool when transitions split a set in two.",
    ),
    crossLinks = listOf(
        CrossLink("subset_sum", "Subset Sum"),
        CrossLink("knapsack_01", "0/1 Knapsack"),
        CrossLink("permutation_generation", "Permutation Generation"),
    ),
)
