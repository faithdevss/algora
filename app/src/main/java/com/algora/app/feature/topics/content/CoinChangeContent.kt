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

internal val coinChangeContent = TopicContent(
    topicId = "coin_change",
    figure = Figure(
        caption = "Coins {1, 4, 5} against a target of 8. Greed takes 5 first and then has only ones " +
            "left — four coins; the table finds 4 + 4 in two. Every cell reads dp[a − coin] in the same " +
            "row it is being written into, so a coin can be picked again and again, and that single " +
            "detail is the whole difference from the 0/1 knapsack, which reads the row above to force " +
            "each item to be used once. The cost is O(amount × coins), which is pseudo-polynomial: the " +
            "amount is a magnitude, so doubling the number of digits in it squares the work.",
        shape = FigureShape.Strip(
            cells = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8"),
            bands = listOf(
                FigureBand(4, 4, "dp[4]"),
                FigureBand(8, 8, "2 coins", FigureTone.Accent),
            ),
            aux = listOf("0", "1", "2", "3", "1", "1", "2", "3", "2"),
            auxLabel = "dp[a] — fewest coins making a; dp[8] = 1 + dp[4]",
        ),
    ),
    whatIsIt = listOf(
        "Coin change asks for the fewest coins (or the number of distinct ways) to make a target amount from a set of denominations.",
        "Greedy works for some coin systems but not all, so the general answer is dynamic programming over amounts from 0 up to the target.",
    ),
    steps = listOf(
        StepCard(1, "State by Amount", "dp[a] = the fewest coins needed to make amount a (∞ if impossible).", 0xFF10B981),
        StepCard(2, "Base Case", "dp[0] = 0 — zero coins make amount zero.", 0xFF3B82F6),
        StepCard(3, "Try Each Coin", "For every amount, dp[a] = 1 + min over coins c of dp[a − c].", 0xFFF59E0B),
        StepCard(4, "Build Upward", "Fill dp from 1 to target so every smaller subproblem is ready when needed.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Min-coins transition", "dp[a] = 1 + min(dp[a − c]) over coins c", "Use one coin c, then solve the remainder."),
        FormulaEntry("Time", "O(amount · coins)", "Each amount tries every denomination."),
        FormulaEntry("Ways variant", "dp[a] += dp[a − c]", "Loop coins outer to count combinations, not permutations."),
    ),
    notationKey = listOf(
        NotationEntry("amount", "the target total to make"),
        NotationEntry("c", "a coin denomination"),
        NotationEntry("dp[a]", "fewest coins (or ways) for amount a"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Coin change — minimum coins",
            accentColor = 0xFF6366F1,
            code = """
                fun coinChange(coins: IntArray, amount: Int): Int {
                    val dp = IntArray(amount + 1) { amount + 1 }   // "infinity"
                    dp[0] = 0
                    for (a in 1..amount) {
                        for (c in coins) {
                            if (c <= a) dp[a] = minOf(dp[a], 1 + dp[a - c])
                        }
                    }
                    return if (dp[amount] > amount) -1 else dp[amount]
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Cash & Change Machines", "Vending machines and registers dispense the fewest coins/notes for change."),
        ApplicationCard("chart", 0xFF3B82F6, "Making-Change Counting", "The ways-variant counts how many combinations reach a total — a classic combinatorics DP."),
        ApplicationCard("target", 0xFFF59E0B, "Resource Composition", "Hitting an exact quota from fixed-size units (packets, tokens) maps onto coin change."),
    ),
    takeaways = listOf(
        "Coin change is 1D DP over amounts, trying every denomination at each step.",
        "Greedy is correct only for canonical coin systems; DP handles arbitrary ones.",
        "Min-coins and count-of-ways are two variants that differ in loop order.",
        "It runs in O(amount · coins) — pseudo-polynomial in the amount.",
    ),
    crossLinks = listOf(
        CrossLink("fibonacci_dp", "Fibonacci (Dynamic Programming)"),
        CrossLink("rod_cutting", "Rod Cutting"),
        // DSA ↔ AI bridge: the same optimal-substructure DP powers value iteration over an MDP.
        CrossLink("mdp", "Markov Decision Process"),
    ),
)
