package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureArrow
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val knapsack01Content = TopicContent(
    topicId = "knapsack_01",
    figure = Figure(
        caption = "Three items — weight 2 value 3, weight 3 value 5, weight 4 value 6 — against a bag " +
            "of capacity 6. Each row adds one item to the pool, so the take arrow always reaches into " +
            "the row *above*: item 3 is used at most once, which is exactly what 0/1 means and exactly " +
            "what greedy value-per-weight cannot honour. The corner is 9, from items 1 and 3 filling the " +
            "bag exactly. Which items those were is not stored anywhere — it is recovered by walking " +
            "back up the last column and noting every row where the value changed. The table is n·W " +
            "cells, and W is a magnitude rather than an input length, so this is pseudo-polynomial: a " +
            "capacity of a billion is one number to type and an impossible table to fill.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "0", "0", "0", "0", "0", "0"),
                listOf("0", "0", "3", "3", "3", "3", "3"),
                listOf("0", "0", "3", "5", "5", "8", "8"),
                listOf("0", "0", "3", "5", "6", "8", "9"),
            ),
            rowHeaders = listOf("ø", "w2 v3", "w3 v5", "w4 v6"),
            colHeaders = listOf("0", "1", "2", "3", "4", "5", "6"),
            marks = listOf(
                FigureCell(3, 6, FigureTone.Accent),
                FigureCell(2, 2, FigureTone.Primary),
                FigureCell(2, 6, FigureTone.Muted),
            ),
            arrows = listOf(
                FigureArrow(2, 2, 3, 6, label = "take"),
                // One cell long, so it carries no label: this is the skip.
                FigureArrow(2, 6, 3, 6, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The 0/1 knapsack problem maximizes the value packed into a weight-limited bag where each item is taken whole or left behind — no fractions allowed.",
        "That all-or-nothing constraint defeats greedy strategies, so it's solved by dynamic programming over a table of (items considered, capacity used).",
    ),
    steps = listOf(
        StepCard(1, "Define the State", "dp[i][w] = the best value using the first i items within capacity w.", 0xFF10B981),
        StepCard(2, "Take or Leave", "For item i, choose the better of skipping it or including it (value plus dp for the reduced capacity).", 0xFF3B82F6),
        StepCard(3, "Fill the Table", "Build up from zero items and zero capacity to the full problem.", 0xFFF59E0B),
        StepCard(4, "Read the Answer", "dp[n][W] holds the maximum achievable value; backtrack the table to recover which items.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Transition", "dp[i][w] = max(dp[i−1][w], vᵢ + dp[i−1][w−wᵢ])", "Leave vs take item i."),
        FormulaEntry("Time", "O(n · W)", "One cell per item-capacity pair — pseudo-polynomial."),
        FormulaEntry("Space", "O(n · W) → O(W)", "A single rolling row suffices if items aren't recovered."),
    ),
    notationKey = listOf(
        NotationEntry("W", "the bag's weight capacity"),
        NotationEntry("vᵢ, wᵢ", "value and weight of item i"),
        NotationEntry("dp[i][w]", "best value from first i items in capacity w"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "0/1 knapsack — 1D DP",
            accentColor = 0xFF6366F1,
            code = """
                fun knapsack(values: IntArray, weights: IntArray, capacity: Int): Int {
                    val dp = IntArray(capacity + 1)
                    for (i in values.indices) {
                        // Iterate capacity downward so each item is used at most once.
                        for (w in capacity downTo weights[i]) {
                            dp[w] = maxOf(dp[w], values[i] + dp[w - weights[i]])
                        }
                    }
                    return dp[capacity]
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Budget Selection", "Pick the most valuable indivisible investments or purchases under a fixed budget."),
        ApplicationCard("chip", 0xFF3B82F6, "Resource Packing", "Fit whole jobs, files, or containers into fixed capacity to maximize value."),
        ApplicationCard("target", 0xFFF59E0B, "Feature/Project Selection", "Choose which whole projects to fund within a capacity constraint."),
    ),
    takeaways = listOf(
        "0/1 knapsack is DP over (items, capacity) with a take-or-leave choice per item.",
        "It runs in pseudo-polynomial O(n · W); iterating capacity downward keeps items single-use.",
        "Greedy fails here — that's exactly what separates it from fractional knapsack.",
        "Backtracking the filled table recovers which items were chosen, not just the value.",
    ),
    crossLinks = listOf(
        CrossLink("fractional_knapsack", "Fractional Knapsack"),
        CrossLink("subset_sum", "Subset Sum"),
    ),
)
