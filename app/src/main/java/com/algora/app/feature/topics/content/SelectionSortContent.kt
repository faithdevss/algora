package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val selectionSortContent = TopicContent(
    topicId = "selection_sort",
    whatIsIt = listOf(
        "Selection sort builds the sorted array one slot at a time by repeatedly finding the smallest remaining element and swapping it into place.",
        "It splits the array into a sorted prefix and an unsorted suffix, growing the prefix by one element on every pass.",
    ),
    steps = listOf(
        StepCard(1, "Scan for the Minimum", "Search the unsorted suffix for the smallest element.", 0xFF10B981),
        StepCard(2, "Swap Into Place", "Swap that minimum with the first unsorted position.", 0xFF3B82F6),
        StepCard(3, "Grow the Sorted Prefix", "The boundary between sorted and unsorted moves one step right.", 0xFFF59E0B),
        StepCard(4, "Repeat n Times", "Each pass locks in one more element until the whole array is sorted.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time (all cases)", "O(n²)", "Every pass scans the rest of the array, regardless of input order."),
        FormulaEntry("Swaps", "O(n)", "At most one swap per pass — the fewest of the simple sorts."),
        FormulaEntry("Space", "O(1)", "Sorts in place with a constant number of variables."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("i", "boundary between the sorted prefix and unsorted suffix"),
        NotationEntry("min", "index of the smallest remaining element"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Selection sort",
            accentColor = 0xFF6366F1,
            code = """
                fun selectionSort(a: IntArray) {
                    for (i in a.indices) {
                        var min = i
                        for (j in i + 1 until a.size) {
                            if (a[j] < a[min]) min = j
                        }
                        a[min] = a[i].also { a[i] = a[min] }
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Minimizing Writes", "When a swap is far more expensive than a comparison (e.g. flash memory), selection sort's ≤ n swaps win."),
        ApplicationCard("book", 0xFF3B82F6, "Teaching Baseline", "Its simple 'find the smallest, put it first' loop makes it a classic first sorting algorithm."),
        ApplicationCard("target", 0xFFF59E0B, "Partial Sort / Top-K", "Running only k passes yields the k smallest elements without sorting the rest."),
    ),
    takeaways = listOf(
        "Selection sort is always O(n²) — it scans the whole remaining array even if the input is already sorted.",
        "It makes the fewest swaps of the simple sorts (≤ n), useful when writes are costly.",
        "It is not stable by default and gains nothing from nearly-sorted input.",
        "Prefer insertion sort for nearly-sorted data and a divide-and-conquer sort for large arrays.",
    ),
    crossLinks = listOf(
        CrossLink("insertion_sort", "Insertion Sort"),
        CrossLink("bubble_sort", "Bubble Sort"),
    ),
)
