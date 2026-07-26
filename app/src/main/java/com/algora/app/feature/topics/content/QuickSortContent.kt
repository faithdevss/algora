package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val quickSortContent = TopicContent(
    topicId = "quick_sort",
    whatIsIt = listOf(
        "Quick sort is a divide-and-conquer sort that picks a pivot, partitions the array so smaller elements go left and larger go right, then recursively sorts each side.",
        "The partition step does all the work — once the pivot is in its final position, the two sides can be sorted independently.",
    ),
    steps = listOf(
        StepCard(1, "Choose a Pivot", "Pick an element (first, last, random, or median-of-three) to partition around.", 0xFF10B981),
        StepCard(2, "Partition", "Rearrange so everything smaller than the pivot is left of it and everything larger is right.", 0xFF3B82F6),
        StepCard(3, "Pivot Is Final", "After partitioning the pivot sits in its correct sorted position and never moves again.", 0xFFF59E0B),
        StepCard(4, "Recurse Both Sides", "Apply the same process to the left and right subarrays until they're size ≤ 1.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Average / best", "O(n log n)", "Balanced partitions halve the array log n times."),
        FormulaEntry("Worst case", "O(n²)", "Consistently bad pivots (e.g. already-sorted input, first-element pivot) shrink by one."),
        FormulaEntry("Space", "O(log n)", "In-place, but recursion uses stack proportional to depth."),
    ),
    notationKey = listOf(
        NotationEntry("pivot", "the element partitioned around"),
        NotationEntry("lo, hi", "current subarray boundaries"),
        NotationEntry("p", "the pivot's final index after partitioning"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Quick sort (Lomuto partition)",
            accentColor = 0xFF6366F1,
            code = """
                fun quickSort(a: IntArray, lo: Int = 0, hi: Int = a.size - 1) {
                    if (lo >= hi) return
                    val pivot = a[hi]
                    var i = lo
                    for (j in lo until hi) {
                        if (a[j] < pivot) {
                            a[i] = a[j].also { a[j] = a[i] }
                            i++
                        }
                    }
                    a[i] = a[hi].also { a[hi] = a[i] }
                    quickSort(a, lo, i - 1)
                    quickSort(a, i + 1, hi)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Standard Library Sorts", "Many language runtimes use quicksort variants (introsort) as their default in-memory sort."),
        ApplicationCard("target", 0xFF3B82F6, "Quickselect", "The same partition step finds the k-th smallest element in expected linear time without full sorting."),
        ApplicationCard("trend", 0xFFF59E0B, "Cache-Friendly Sorting", "Its in-place, sequential access pattern makes it fast on real hardware despite the worst case."),
    ),
    takeaways = listOf(
        "Quick sort averages O(n log n) and sorts in place, making it the default for many libraries.",
        "Its Achilles' heel is O(n²) on bad pivots; randomized or median-of-three pivots avoid it in practice.",
        "The partition routine is reusable — quickselect finds order statistics with it.",
        "Unlike merge sort it is not stable, so use merge sort when equal-key order must be preserved.",
    ),
    crossLinks = listOf(
        CrossLink("merge_sort", "Merge Sort"),
        CrossLink("quickselect", "Quickselect"),
    ),
)
