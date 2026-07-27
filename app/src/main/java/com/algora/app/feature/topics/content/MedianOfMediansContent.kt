package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val medianOfMediansContent = TopicContent(
    topicId = "median_of_medians",
    whatIsIt = listOf(
        "Median of medians is a pivot-selection strategy that gives quickselect a guaranteed O(n) worst case, instead of quickselect's usual O(n²) on bad pivots.",
        "It finds a provably good pivot by grouping elements into fives, taking each group's median, then recursively finding the median of those medians.",
    ),
    steps = listOf(
        StepCard(1, "Group Into Fives", "Split the array into groups of five elements.", 0xFF10B981),
        StepCard(2, "Median of Each Group", "Sort each tiny group and take its median — cheap, since each is size 5.", 0xFF3B82F6),
        StepCard(3, "Recurse for the Pivot", "Recursively find the median of those ⌈n/5⌉ medians; use it as the pivot.", 0xFFF59E0B),
        StepCard(4, "Partition & Recurse", "Partition around that pivot and recurse into the side holding the k-th element.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time (worst case)", "O(n)", "The good pivot guarantees balanced partitions."),
        FormulaEntry("Recurrence", "T(n) ≤ T(n/5) + T(7n/10) + O(n)", "Solves to linear because 1/5 + 7/10 < 1."),
        FormulaEntry("Pivot guarantee", "≥ 30% on each side", "The chosen pivot always discards a constant fraction."),
    ),
    notationKey = listOf(
        NotationEntry("k", "the order statistic sought (k-th smallest)"),
        NotationEntry("pivot", "the median-of-medians partition value"),
        NotationEntry("n/5, 7n/10", "subproblem sizes in the recurrence"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Median-of-medians pivot",
            accentColor = 0xFF6366F1,
            code = """
                // Returns a pivot value guaranteeing balanced partitions.
                fun medianOfMedians(a: List<Int>): Int {
                    if (a.size <= 5) return a.sorted()[a.size / 2]
                    val medians = a.chunked(5).map { group ->
                        group.sorted()[group.size / 2]         // median of each 5-group
                    }
                    return medianOfMedians(medians)            // median of the medians
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Worst-Case Selection", "Finds the k-th smallest / the median in guaranteed linear time, unlike randomized quickselect."),
        ApplicationCard("chip", 0xFF3B82F6, "Deterministic Guarantees", "Real-time and safety-critical code prefers its predictable bound over expected-case speed."),
        ApplicationCard("chart", 0xFFF59E0B, "Robust Statistics", "Computing medians and quantiles on large datasets without a full sort."),
    ),
    takeaways = listOf(
        "Median of medians picks a provably good pivot, giving quickselect an O(n) worst case.",
        "Grouping by five and recursing on the medians guarantees each partition discards ≥ 30%.",
        "It's mainly of theoretical value — high constants mean randomized quickselect is faster in practice.",
        "It's the classic proof that selection can be done in deterministic linear time.",
    ),
    crossLinks = listOf(
        CrossLink("quickselect", "Quickselect"),
        CrossLink("quick_sort", "Quick Sort"),
    ),
)
