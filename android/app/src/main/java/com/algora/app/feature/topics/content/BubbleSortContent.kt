package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
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

internal val bubbleSortContent = TopicContent(
    topicId = "bubble_sort",
    figure = Figure(
        caption = "Only adjacent pairs are ever compared, so each pass drags the largest remaining value " +
            "to the end and the sorted tail grows by exactly one. The one thing it has over the other " +
            "quadratic sorts: a pass with no swap proves the array is sorted, making the best case O(n).",
        shape = FigureShape.Strip(
            cells = listOf("3", "7", "2", "5", "8", "9"),
            bands = listOf(
                FigureBand(1, 2, "compare", FigureTone.Accent),
                FigureBand(4, 5, "settled", FigureTone.Muted),
            ),
            pointers = listOf(FigurePointer(1, "i"), FigurePointer(2, "i+1")),
        ),
    ),
    whatIsIt = listOf(
        "Bubble sort repeatedly steps through an array, swapping adjacent elements that are out of order, until no swaps are needed.",
        "The name comes from how larger elements 'bubble up' toward the end of the array with each pass.",
    ),
    steps = listOf(
        StepCard(1, "Compare Neighbors", "Walk the array comparing each adjacent pair of elements.", 0xFF3B82F6),
        StepCard(2, "Swap if Out of Order", "If the left element is greater than the right, swap them.", 0xFF8B5CF6),
        StepCard(3, "Repeat Passes", "After each full pass, the largest unsorted element has bubbled to its correct position.", 0xFFF59E0B),
        StepCard(4, "Early Exit", "If a pass makes no swaps, the array is already sorted — stop early.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time complexity (average/worst)", "O(n²)", "Nested passes over the array — quadratic comparisons."),
        FormulaEntry("Time complexity (best, sorted input)", "O(n)", "With the early-exit optimization, one pass confirms it's sorted."),
        FormulaEntry("Space complexity", "O(1)", "Sorts in place — no auxiliary array needed."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements in the array"),
        NotationEntry("swapped", "flag tracking whether any swap happened in a pass"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bubble Sort — with early exit",
            accentColor = 0xFF6366F1,
            code = """
                fun bubbleSort(arr: IntArray) {
                    for (i in arr.indices) {
                        var swapped = false
                        for (j in 0 until arr.size - i - 1) {
                            if (arr[j] > arr[j + 1]) {
                                val tmp = arr[j]
                                arr[j] = arr[j + 1]
                                arr[j + 1] = tmp
                                swapped = true
                            }
                        }
                        if (!swapped) break
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("stack", 0xFF3B82F6, "Teaching Sorting Concepts", "Its simplicity makes bubble sort the standard first example for comparison-based sorting."),
        ApplicationCard("chip", 0xFF8B5CF6, "Nearly-Sorted Data", "With the early-exit check, bubble sort runs in near-linear time on data that's already almost sorted."),
    ),
    takeaways = listOf(
        "Bubble sort is O(n²) in the general case — rarely used in production for large datasets.",
        "It sorts in place with O(1) extra space, unlike merge sort's O(n).",
        "The early-exit optimization makes it O(n) best-case on already-sorted input.",
        "Its main value today is pedagogical — it's the clearest introduction to comparison-and-swap sorting.",
    ),
)
