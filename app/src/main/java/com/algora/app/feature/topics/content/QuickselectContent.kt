package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val quickselectContent = TopicContent(
    topicId = "quickselect",
    whatIsIt = listOf(
        "Quickselect finds the k-th smallest element of an unsorted array in expected linear time, without sorting the whole thing.",
        "It reuses quicksort's partition step, but recurses into only the one side that must contain the k-th element — halving the work each time instead of sorting both halves.",
    ),
    steps = listOf(
        StepCard(1, "Partition Around a Pivot", "Rearrange so smaller elements go left, larger go right; the pivot lands in its final sorted index p.", 0xFF10B981),
        StepCard(2, "Compare p to k", "If p equals the target index, the pivot is the answer.", 0xFF3B82F6),
        StepCard(3, "Recurse One Side", "If k is smaller, recurse left; if larger, recurse right — never both.", 0xFFF59E0B),
        StepCard(4, "Shrink Toward k", "Each partition discards a chunk, converging on the k-th element.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Average", "O(n)", "n + n/2 + n/4 + … ≈ 2n with balanced pivots."),
        FormulaEntry("Worst case", "O(n²)", "Consistently bad pivots; median-of-medians makes it O(n) worst-case."),
        FormulaEntry("Space", "O(1)", "In-place, iterative partitioning."),
    ),
    notationKey = listOf(
        NotationEntry("k", "rank of the element sought (0-indexed)"),
        NotationEntry("p", "the pivot's final index after partitioning"),
        NotationEntry("pivot", "the partition value"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Quickselect (Lomuto partition)",
            accentColor = 0xFF6366F1,
            code = """
                fun quickselect(a: IntArray, k: Int): Int {
                    var lo = 0; var hi = a.size - 1
                    while (lo <= hi) {
                        val pivot = a[hi]
                        var i = lo
                        for (j in lo until hi) {
                            if (a[j] < pivot) { a[i] = a[j].also { a[j] = a[i] }; i++ }
                        }
                        a[i] = a[hi].also { a[hi] = a[i] }
                        when {
                            i == k -> return a[i]
                            i < k -> lo = i + 1     // recurse right only
                            else -> hi = i - 1      // recurse left only
                        }
                    }
                    return -1
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Median & Percentiles", "Compute a median or any percentile without the cost of a full sort."),
        ApplicationCard("trend", 0xFF3B82F6, "Top-K Selection", "Partition once to gather the k largest/smallest elements in linear expected time."),
        ApplicationCard("chip", 0xFFF59E0B, "Order Statistics", "Any 'find the k-th ranked item' query in analytics and databases."),
    ),
    takeaways = listOf(
        "Quickselect finds the k-th element in expected O(n) by recursing into just one partition side.",
        "It's the selection cousin of quicksort, sharing the same partition routine.",
        "Bad pivots make it O(n²); median-of-medians pivots guarantee linear worst-case.",
        "Prefer it over full sorting whenever you need a single order statistic or a top-K set.",
    ),
    crossLinks = listOf(
        CrossLink("quick_sort", "Quick Sort"),
        CrossLink("median_of_medians", "Median of Medians"),
    ),
)
