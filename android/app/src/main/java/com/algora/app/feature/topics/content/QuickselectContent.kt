package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val quickselectContent = TopicContent(
    topicId = "quickselect",
    figure = Figure(
        caption = "The same partition quicksort runs, used to throw work away instead of to divide it. " +
            "Pivot 4 lands at p = 2; the target k = 3 sits to its right, so everything at or left of p " +
            "is discarded unexamined and never sorted. Recursing one side turns the recurrence from " +
            "quicksort's 2T(n/2) into T(n/2), and n + n/2 + n/4 + … sums to 2n — linear expected time, " +
            "with no log factor. The worst case is unchanged though: pivots that shave off one element " +
            "at a time still give O(n²) unless the pivot is chosen deliberately.",
        shape = FigureShape.Strip(
            cells = listOf("3", "1", "4", "9", "7", "8"),
            bands = listOf(
                FigureBand(0, 1, "discarded", FigureTone.Warn),
                FigureBand(2, 2, "p=2", FigureTone.Accent),
                FigureBand(3, 5, "recurse here"),
            ),
            pointers = listOf(FigurePointer(3, "k=3")),
        ),
    ),
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
