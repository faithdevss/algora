package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val partitionProblemContent = TopicContent(
    topicId = "partition_problem",
    whatIsIt = listOf(
        "The partition problem asks whether a multiset of positive integers can be split into two groups with equal sums. It is a decision problem — yes or no — and it is NP-complete, which is worth stating plainly before the tractable-looking table below makes it seem easy.",
        "The reduction that makes it solvable is one observation: if the total is T, then the two halves must each sum to T/2, so a valid split exists exactly when some subset sums to T/2. That turns partition into subset-sum with a fixed target, and immediately gives two free rejections — an odd total can never be split, and neither can a set containing an element larger than T/2. Everything after that is the standard subset-sum table: dp[i][s] is true when some subset of the first i items reaches sum s, and each cell either skips item i or takes it and looks up dp[i−1][s − wᵢ].",
        "The table is O(n·T) cells, which is not a contradiction of NP-completeness — it is pseudo-polynomial. T is the numeric value of the total, so the cost is exponential in the number of bits used to write the inputs down. A hundred items each around a billion is a hopeless table and a trivial input to type. The practical consequence is that the DP is the right tool for small-valued inputs and useless for large-valued ones, where you fall back to meet-in-the-middle at O(2^(n/2)) or accept an approximation. Rolling the table down to a single boolean row, iterated from high sum to low, drops the memory to O(T) without changing the recurrence.",
    ),
    steps = listOf(
        StepCard(1, "Total and Parity", "Sum every element. An odd total is an immediate no — no rounding, no search.", 0xFFEC4899),
        StepCard(2, "Halve the Target", "The question becomes: does any subset sum to exactly T/2?", 0xFF3B82F6),
        StepCard(3, "Seed the Table", "dp[i][0] = true for every i — the empty subset always makes zero.", 0xFFF59E0B),
        StepCard(4, "Skip or Take", "dp[i][s] = dp[i−1][s] (skip item i) OR dp[i−1][s − wᵢ] (take it, if it fits).", 0xFF10B981),
        StepCard(5, "Read the Corner", "dp[n][T/2] answers the question; the rest of the last row answers it for other targets too.", 0xFF8B5CF6),
        StepCard(6, "Trace the Split", "Walk back from the corner: a cell that differs from the one above it means that item was taken.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Feasibility", "T even ∧ ∃S ⊆ W : Σ S = T/2", "Both conditions are necessary; together they are sufficient."),
        FormulaEntry("Recurrence", "dp[i][s] = dp[i−1][s] ∨ dp[i−1][s − wᵢ]", "Second term only when wᵢ ≤ s."),
        FormulaEntry("Time", "O(n · T/2)", "One boolean per (item, sum) pair."),
        FormulaEntry("Space", "O(T)", "One rolling row, swept from high sum down to low."),
        FormulaEntry("Complexity class", "NP-complete", "The DP is pseudo-polynomial, not polynomial."),
        FormulaEntry("Large values", "O(2^(n/2)) meet-in-the-middle", "The fallback when T is too big to tabulate."),
    ),
    notationKey = listOf(
        NotationEntry("W", "the multiset of positive integers to be split"),
        NotationEntry("T", "the total Σ W; the target for each half is T/2"),
        NotationEntry("dp[i][s]", "true when some subset of the first i items sums to exactly s"),
        NotationEntry("pseudo-polynomial", "polynomial in T's value, exponential in T's bit length"),
        NotationEntry("rolling row", "the single boolean array the 2-D table collapses to"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Partition via subset-sum (rolling row)",
            accentColor = 0xFF3B82F6,
            code = """
                fun canPartition(nums: List<Int>): Boolean {
                    val total = nums.sum()
                    if (total % 2 != 0) return false        // odd totals are hopeless, cheaply
                    val target = total / 2

                    val reachable = BooleanArray(target + 1)
                    reachable[0] = true                     // the empty subset makes zero
                    for (n in nums) {
                        // Descending is essential: ascending would let one item be reused,
                        // which turns this into the unbounded (coin-change) problem.
                        for (s in target downTo n) {
                            if (reachable[s - n]) reachable[s] = true
                        }
                    }
                    return reachable[target]
                }

                canPartition(listOf(1, 5, 11, 5))   // true  — {1,5,5} and {11}, both 11
                canPartition(listOf(1, 2, 3, 5))    // false — total 11 is odd
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Recovering the actual split",
            accentColor = 0xFF10B981,
            code = """
                fun partitionSets(nums: List<Int>): Pair<List<Int>, List<Int>>? {
                    val total = nums.sum()
                    if (total % 2 != 0) return null
                    val target = total / 2

                    // Keep the full table this time — the traceback needs the rows.
                    val dp = Array(nums.size + 1) { BooleanArray(target + 1) }
                    for (i in 0..nums.size) dp[i][0] = true
                    for (i in 1..nums.size) {
                        for (s in 0..target) {
                            dp[i][s] = dp[i - 1][s] ||
                                (nums[i - 1] <= s && dp[i - 1][s - nums[i - 1]])
                        }
                    }
                    if (!dp[nums.size][target]) return null

                    val first = mutableListOf<Int>()
                    var s = target
                    for (i in nums.size downTo 1) {
                        // Reachable here but not without item i ⇒ item i was taken.
                        if (!dp[i - 1][s]) {
                            first += nums[i - 1]
                            s -= nums[i - 1]
                        }
                    }
                    val second = nums.toMutableList().also { rest -> first.forEach { rest.remove(it) } }
                    return first to second
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Load Balancing", "Splitting jobs across two identical machines to minimise makespan is the optimisation version of this decision problem."),
        ApplicationCard("finance", 0xFF10B981, "Asset Division", "Dividing indivisible holdings into two equal-value shares — and proving when no equal division exists."),
        ApplicationCard("bulb", 0xFFEC4899, "NP-Completeness Teaching", "The canonical gateway from subset-sum to bin packing and multiway number partitioning."),
    ),
    takeaways = listOf(
        "An equal split exists exactly when the total is even and some subset reaches half of it.",
        "The parity check and the max-element check reject most negatives before any table is built.",
        "The O(n·T) DP is pseudo-polynomial — fine for small values, useless for large ones despite the polynomial-looking bound.",
        "Sweeping the rolling row downward is what keeps each item usable once.",
    ),
    crossLinks = listOf(
        CrossLink("subset_sum", "Subset Sum"),
        CrossLink("knapsack_01", "0/1 Knapsack"),
        CrossLink("bitmask_dp", "Bitmask DP"),
    ),
)
