package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val prefixSumContent = TopicContent(
    topicId = "prefix_sum",
    whatIsIt = listOf(
        "A prefix sum array precomputes running totals so that the sum of any range can be answered in O(1) by subtracting two entries.",
        "It trades a one-time O(n) preprocessing pass for constant-time range-sum queries — the go-to pattern whenever many range totals are needed on static data.",
    ),
    steps = listOf(
        StepCard(1, "Build the Prefix Array", "prefix[i] holds the sum of the first i elements; prefix[0] = 0.", 0xFF10B981),
        StepCard(2, "Each Entry Extends the Last", "prefix[i] = prefix[i−1] + a[i−1], filled in one linear pass.", 0xFF3B82F6),
        StepCard(3, "Answer a Range in O(1)", "Sum of a[l..r] = prefix[r+1] − prefix[l].", 0xFFF59E0B),
        StepCard(4, "Reuse Freely", "Any number of range queries now cost O(1) each with no rescanning.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Build", "prefix[i] = prefix[i−1] + a[i−1]", "One O(n) pass."),
        FormulaEntry("Range query", "sum(l..r) = prefix[r+1] − prefix[l]", "Constant time per query."),
        FormulaEntry("Space", "O(n)", "One extra array of running totals."),
    ),
    notationKey = listOf(
        NotationEntry("prefix[i]", "sum of the first i elements"),
        NotationEntry("l, r", "inclusive range bounds of a query"),
        NotationEntry("n", "array length"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Prefix sum + range query",
            accentColor = 0xFF6366F1,
            code = """
                fun buildPrefix(a: IntArray): IntArray {
                    val prefix = IntArray(a.size + 1)
                    for (i in a.indices) prefix[i + 1] = prefix[i] + a[i]
                    return prefix
                }

                // Inclusive sum of a[l..r] in O(1).
                fun rangeSum(prefix: IntArray, l: Int, r: Int): Int =
                    prefix[r + 1] - prefix[l]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Range Aggregates", "Dashboards answer 'total between these dates' instantly over precomputed cumulative sums."),
        ApplicationCard("image", 0xFF3B82F6, "2D Integral Images", "Summed-area tables extend prefix sums to O(1) rectangle sums in image and vision code."),
        ApplicationCard("target", 0xFFF59E0B, "Subarray Counting", "Combined with a hash map, prefix sums count subarrays with a target sum in O(n)."),
    ),
    takeaways = listOf(
        "Prefix sums turn repeated range-sum queries from O(n) into O(1) after O(n) setup.",
        "Any range total is the difference of two prefix entries.",
        "They extend to 2D (summed-area tables) for constant-time rectangle sums.",
        "For range updates rather than queries, pair them with a difference array.",
    ),
    crossLinks = listOf(
        CrossLink("difference_array", "Difference Array"),
        CrossLink("kadanes_algorithm", "Kadane's Algorithm"),
    ),
)
