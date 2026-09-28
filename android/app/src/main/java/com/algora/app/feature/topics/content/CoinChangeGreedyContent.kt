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

internal val coinChangeGreedyContent = TopicContent(
    topicId = "coin_change_greedy",
    figure = Figure(
        caption = "Coins {1, 3, 4}, target 6. Greed takes the 4 and is then stuck with ones — three " +
            "coins — while 3 + 3 does it in two. Nothing is wrong with the rule; the coin system simply " +
            "lacks the structure that makes the rule safe. Systems where greed is always optimal are " +
            "called canonical, and real currencies are canonical by design: the 1-2-5 pattern of most " +
            "banknote series is a deliberate choice. There is no simple arithmetic test for it, but " +
            "Pearson's algorithm decides the question in O(n³) by checking a bounded set of candidate " +
            "amounts derived from the greedy representations. When canonicity is not guaranteed, the DP " +
            "at O(amount × n) is the correct algorithm.",
        shape = FigureShape.Strip(
            cells = listOf("4", "1", "1"),
            bands = listOf(FigureBand(0, 2, "greedy · 3 coins", FigureTone.Warn)),
            aux = listOf("3", "3"),
            auxLabel = "optimal · 2 coins — the table finds this, the greedy rule cannot",
        ),
    ),
    whatIsIt = listOf(
        "Greedy coin change makes an amount out of the fewest coins by repeatedly taking the largest denomination that still fits. On the coins in your pocket it is what you already do without thinking: 68¢ becomes 25 + 25 + 10 + 5 + 1 + 1 + 1, and no shorter answer exists.",
        "But the correctness comes from the denominations, not from the algorithm. With coins {1, 3, 4} and a target of 6, greed takes 4, then can only add 1 + 1 — three coins — while 3 + 3 does it in two. Nothing about the greedy rule is at fault; the coin system simply does not have the structure that makes the rule safe. The simulation runs both the greedy sweep and the DP table on exactly this input so the failure is watched rather than asserted.",
        "A coin system where greed is always optimal is called canonical. There is no simple arithmetic characterisation of canonical systems, but there is a decision procedure: Pearson's algorithm finds the smallest counterexample, if one exists, in O(n³) time for n denominations, by checking only a bounded set of candidate amounts derived from the greedy representations. Real currencies are canonical because they are designed to be — the 1-2-5 pattern of most banknote series is a deliberate choice, not an accident. When you cannot guarantee canonicity, the DP formulation is the correct algorithm, and it costs O(amount × n) rather than O(n log n).",
    ),
    steps = listOf(
        StepCard(1, "Sort Denominations Descending", "Largest coin first — the order the greedy rule consumes them in.", 0xFF10B981),
        StepCard(2, "Take the Largest That Fits", "While the current coin is ≤ the remaining amount, take it and subtract.", 0xFF3B82F6),
        StepCard(3, "Move Down on Overshoot", "When the coin exceeds what is left, drop to the next denomination.", 0xFFF59E0B),
        StepCard(4, "Stop at Zero", "The remainder reaching zero ends the sweep; a nonzero dead end means the amount is unmakeable.", 0xFFEC4899),
        StepCard(5, "Check the System, Not the Run", "Greed's answer is optimal only if the denominations are canonical — verify that separately.", 0xFFEF4444),
        StepCard(6, "Fall Back to DP", "For arbitrary denominations, tabulate dp[a] = 1 + min over coins of dp[a − coin].", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Greedy time", "O(n log n)", "Sort plus a sweep; the sweep itself is O(n + coins used)."),
        FormulaEntry("Greedy rule", "take max{c ∈ C : c ≤ remaining}", "Repeat until the remainder is zero."),
        FormulaEntry("DP recurrence", "dp[a] = 1 + min_{c ≤ a} dp[a − c]", "dp[0] = 0; unreachable amounts stay ∞."),
        FormulaEntry("DP time", "O(amount × n)", "Pseudo-polynomial — linear in the numeric value, not its bit length."),
        FormulaEntry("Counterexample", "C = {1,3,4}, a = 6", "Greedy 4+1+1 = 3 coins; optimal 3+3 = 2 coins."),
        FormulaEntry("Canonicity test", "O(n³) (Pearson)", "Decides whether greed is optimal for a given system at all amounts."),
    ),
    notationKey = listOf(
        NotationEntry("C", "the set of coin denominations available in unlimited supply"),
        NotationEntry("a", "the target amount to make"),
        NotationEntry("canonical system", "denominations for which greed is optimal at every amount"),
        NotationEntry("dp[a]", "fewest coins summing to exactly a, or ∞ if impossible"),
        NotationEntry("pseudo-polynomial", "polynomial in the value of a, exponential in its number of digits"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Greedy change-making",
            accentColor = 0xFF10B981,
            code = """
                fun greedyChange(coins: List<Int>, amount: Int): List<Int>? {
                    var remaining = amount
                    val used = mutableListOf<Int>()
                    for (coin in coins.sortedDescending()) {
                        while (coin <= remaining) {
                            used += coin
                            remaining -= coin
                        }
                    }
                    // Greed can dead-end entirely: coins {3,4} cannot make 5, and neither
                    // can it make 5 by any other route, so null is honest here.
                    return if (remaining == 0) used else null
                }

                greedyChange(listOf(1, 5, 10, 25), 68)   // [25,25,10,5,1,1,1] — optimal
                greedyChange(listOf(1, 3, 4), 6)         // [4,1,1] — three coins, but 3+3 is two
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The DP that is always right",
            accentColor = 0xFF8B5CF6,
            code = """
                fun minCoins(coins: List<Int>, amount: Int): Int {
                    val unreachable = amount + 1                 // stands in for infinity
                    val dp = IntArray(amount + 1) { unreachable }
                    dp[0] = 0
                    for (a in 1..amount) {
                        for (coin in coins) {
                            if (coin <= a && dp[a - coin] + 1 < dp[a]) {
                                dp[a] = dp[a - coin] + 1
                            }
                        }
                    }
                    return if (dp[amount] == unreachable) -1 else dp[amount]
                }

                minCoins(listOf(1, 3, 4), 6)   // 2 — the answer greed missed
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Cash Registers & ATMs", "Dispensing notes largest-first is correct because currency series are deliberately canonical."),
        ApplicationCard("chip", 0xFF3B82F6, "Resource Quantisation", "Allocating in fixed block sizes — the greedy split is safe only when the sizes divide cleanly."),
        ApplicationCard("bulb", 0xFFEF4444, "Counterexample Discipline", "The cleanest demonstration that a greedy rule's correctness lives in the input, not the code."),
    ),
    takeaways = listOf(
        "Greedy change-making is O(n log n) and takes the largest coin that fits, repeatedly.",
        "It is optimal only on canonical systems; {1,3,4} at amount 6 breaks it — 4+1+1 against 3+3.",
        "Real currencies are canonical by design, which is why the everyday intuition holds.",
        "Given arbitrary denominations, use the O(amount × n) DP — it never needs an argument about the coin set.",
    ),
    crossLinks = listOf(
        CrossLink("coin_change", "Coin Change (DP)"),
        CrossLink("activity_selection", "Activity Selection"),
        CrossLink("fractional_knapsack", "Fractional Knapsack"),
    ),
)
