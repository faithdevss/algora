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

internal val rodCuttingContent = TopicContent(
    topicId = "rod_cutting",
    figure = Figure(
        caption = "A rod of length 4 priced 1, 5, 8, 9 by piece length. Two cuts of length 2 earn 10, " +
            "which beats selling it whole for 9 and beats 3 + 1 for 9 — the price list is not " +
            "proportional to length, and that is the only reason the problem exists. dp[len] tries " +
            "every first piece i and adds price[i] to dp[len − i]; because the remainder is the same " +
            "problem again, a length may be cut out as many times as it fits, which makes this the " +
            "unbounded case rather than the 0/1 one. n lengths each trying n first cuts gives O(n²).",
        shape = FigureShape.Strip(
            cells = listOf("1", "1", "1", "1"),
            bands = listOf(
                FigureBand(0, 1, "cut 2 → 5"),
                FigureBand(2, 3, "cut 2 → 5", FigureTone.Accent),
            ),
            aux = listOf("1", "5", "8", "9"),
            auxLabel = "price by piece length 1..4 — the whole rod is worth less than its halves",
        ),
    ),
    whatIsIt = listOf(
        "Rod cutting maximizes revenue from a rod of length n, given a price for each possible piece length — you decide where, if at all, to cut it.",
        "It's a clean unbounded-DP example: each length can be used any number of times, and the best value for length n is built from the best values for shorter lengths.",
    ),
    steps = listOf(
        StepCard(1, "State by Length", "dp[len] = the maximum revenue obtainable from a rod of length len.", 0xFF10B981),
        StepCard(2, "Try Every First Cut", "For length len, try each first piece i and add its price to dp[len − i].", 0xFF3B82F6),
        StepCard(3, "Keep the Best", "dp[len] is the maximum over all those first-cut choices.", 0xFFF59E0B),
        StepCard(4, "Build Upward", "Compute dp from 1 to n so each subproblem is solved before it's used.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Transition", "dp[len] = max over i of (price[i] + dp[len − i])", "Cut off a piece of length i, solve the rest."),
        FormulaEntry("Time", "O(n²)", "Each of n lengths tries up to n first cuts."),
        FormulaEntry("Space", "O(n)", "A single revenue array over lengths."),
    ),
    notationKey = listOf(
        NotationEntry("n", "total rod length"),
        NotationEntry("price[i]", "revenue for a piece of length i"),
        NotationEntry("dp[len]", "best revenue for a rod of length len"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Rod cutting — bottom-up",
            accentColor = 0xFF6366F1,
            code = """
                // price[i] = revenue for a piece of length i (1-indexed).
                fun rodCutting(price: IntArray, n: Int): Int {
                    val dp = IntArray(n + 1)
                    for (len in 1..n) {
                        var best = Int.MIN_VALUE
                        for (i in 1..len) {
                            best = maxOf(best, price[i] + dp[len - i])
                        }
                        dp[len] = best
                    }
                    return dp[n]
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Cutting Stock", "Slicing raw material — rods, rolls, boards — into pieces that maximize sale value."),
        ApplicationCard("chip", 0xFF3B82F6, "Resource Splitting", "Dividing a fixed budget of a divisible unit into value-optimal chunks."),
        ApplicationCard("book", 0xFFF59E0B, "Teaching Unbounded DP", "It's the standard example where each item may be reused any number of times."),
    ),
    takeaways = listOf(
        "Rod cutting is DP over length, choosing the best first cut at each step.",
        "Pieces can be reused freely, making it an unbounded-knapsack-style problem.",
        "It runs in O(n²) time and O(n) space.",
        "The 'try every first piece' pattern recurs across cutting, partition, and coin-change problems.",
    ),
    crossLinks = listOf(
        CrossLink("coin_change", "Coin Change"),
        CrossLink("knapsack_01", "0/1 Knapsack"),
    ),
)
