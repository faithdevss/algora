package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Covers the whole take-it-or-leave-it DP family, not just the
// weight/value knapsack — subset sum, partition and coin change are the same recurrence.
internal val knapsackDpPatternContent = TopicContent(
    topicId = "knapsack_dp_pattern",
    whatIsIt = listOf(
        "The knapsack pattern is the take-it-or-leave-it decision repeated over a budget. State is (items considered, budget used); each item is either skipped, keeping the old value, or taken, spending its cost and adding its value.",
        "Subset sum, partition-into-equal-halves, target sum and coin change are all this recurrence with the value term changed. Recognising the family is worth more than memorising any one of them.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A set of items, a capacity or target, and a best/possible/count question — with each item usable once (0/1) or unlimited times (unbounded).", 0xFFF59E0B),
        StepCard(2, "Name the State", "dp[i][c] = the answer using the first i items with capacity c. Write down what the cell means before writing the loop.", 0xFF3B82F6),
        StepCard(3, "Write the Choice", "dp[i][c] = max(dp[i-1][c], value[i] + dp[i-1][c - cost[i]]) — skip versus take, with the take branch guarded by c ≥ cost[i].", 0xFFEF4444),
        StepCard(4, "Roll It Flat", "Only the previous row is read, so keep one array. 0/1 iterates capacity downward; unbounded iterates upward so an item can be reused.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "dp[c] = max(dp[c], v_i + dp[c - w_i])", "Applied per item, capacity descending for 0/1."),
        FormulaEntry("Time", "O(n · C)", "n items × C capacity states."),
        FormulaEntry("Space", "O(C)", "One rolling row; O(n · C) only if you must reconstruct the chosen set."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of items"),
        NotationEntry("C", "capacity or target"),
        NotationEntry("w_i, v_i", "cost and value of item i"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "0/1 knapsack, rolled to one row (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def knapsack(weights, values, cap):
                    dp = [0] * (cap + 1)
                    for w, v in zip(weights, values):
                        for c in range(cap, w - 1, -1):   # descending: each item used once
                            dp[c] = max(dp[c], v + dp[c - w])
                    return dp[cap]

                def coin_change(coins, target):           # unbounded: ascending capacity
                    dp = [0] + [float('inf')] * target
                    for coin in coins:
                        for c in range(coin, target + 1):
                            dp[c] = min(dp[c], 1 + dp[c - coin])
                    return -1 if dp[target] == float('inf') else dp[target]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Budget Allocation", "Pick the highest-value subset of projects under a fixed spend."),
        ApplicationCard("target", 0xFF10B981, "Partition & Subset Sum", "Ask whether capacity sum/2 is reachable — the value term becomes a boolean."),
        ApplicationCard("chip", 0xFF8B5CF6, "Resource Packing", "Fitting jobs into memory or a container under a hard limit."),
    ),
    takeaways = listOf(
        "One recurrence, many names: knapsack, subset sum, partition, target sum, coin change.",
        "Loop direction encodes reuse — descending capacity for 0/1, ascending for unbounded.",
        "O(n·C) is pseudo-polynomial: it grows with the capacity's value, not its digit count.",
        "Swap max for min, `or` or `+` in the recurrence to turn best-value into feasibility or counting.",
    ),
    crossLinks = listOf(
        CrossLink("knapsack_01", "0/1 Knapsack (Algorithms)"),
        CrossLink("subset_sum", "Subset Sum (Algorithms)"),
        CrossLink("coin_change", "Coin Change (Algorithms)"),
    ),
)
