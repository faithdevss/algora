package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kadanesAlgorithmContent = TopicContent(
    topicId = "kadanes_algorithm",
    whatIsIt = listOf(
        "Kadane's algorithm finds the maximum-sum contiguous subarray in a single linear pass, turning an O(n²) or O(n³) brute force into O(n).",
        "Its core insight is a tiny DP: the best subarray ending at position i is either just a[i], or a[i] joined to the best subarray ending at i−1.",
    ),
    steps = listOf(
        StepCard(1, "Track Best-Ending-Here", "Keep a running sum of the best subarray that ends at the current index.", 0xFF10B981),
        StepCard(2, "Extend or Restart", "At each element, either extend the running sum or start fresh from this element — whichever is larger.", 0xFF3B82F6),
        StepCard(3, "Update the Global Best", "After each step, record the running sum if it beats the best seen so far.", 0xFFF59E0B),
        StepCard(4, "One Pass Done", "The global best after the final element is the maximum subarray sum.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Transition", "cur = max(a[i], cur + a[i])", "Restart at a[i] or extend the previous run."),
        FormulaEntry("Global", "best = max(best, cur)", "Track the largest running sum seen."),
        FormulaEntry("Time / Space", "O(n) / O(1)", "Single pass, two scalars."),
    ),
    notationKey = listOf(
        NotationEntry("cur", "best subarray sum ending at the current index"),
        NotationEntry("best", "best subarray sum found anywhere so far"),
        NotationEntry("n", "array length"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Kadane's algorithm",
            accentColor = 0xFF6366F1,
            code = """
                fun maxSubArray(a: IntArray): Int {
                    var cur = a[0]
                    var best = a[0]
                    for (i in 1 until a.size) {
                        cur = maxOf(a[i], cur + a[i])   // extend or restart
                        best = maxOf(best, cur)
                    }
                    return best
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Max Profit Windows", "Finding the best contiguous run of gains in a series of daily changes is Kadane's directly."),
        ApplicationCard("image", 0xFF3B82F6, "Image Processing", "Extended to 2D, it locates the brightest/highest-value rectangular region in a grid."),
        ApplicationCard("chart", 0xFFF59E0B, "Signal Analysis", "Detecting the strongest sustained segment in sensor or metric streams."),
    ),
    takeaways = listOf(
        "Kadane's solves maximum subarray in O(n) with O(1) space.",
        "The choice 'extend or restart' at each element is a minimal dynamic program.",
        "Handle all-negative arrays by seeding from the first element, not zero.",
        "It generalizes to 2D (max submatrix) by fixing column ranges and running Kadane's per row band.",
    ),
    crossLinks = listOf(
        CrossLink("prefix_sum", "Prefix Sum"),
        CrossLink("sliding_window", "Sliding Window"),
    ),
)
