package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val insertionSortContent = TopicContent(
    topicId = "insertion_sort",
    whatIsIt = listOf(
        "Insertion sort builds a sorted prefix one element at a time, taking the next element and sliding it left until it sits in the right spot.",
        "It works the way most people sort a hand of playing cards: pick up the next card and insert it into its correct position among the cards already held.",
    ),
    steps = listOf(
        StepCard(1, "Take the Next Element", "Treat the first element as a sorted prefix; pick up the next unsorted one.", 0xFF10B981),
        StepCard(2, "Shift Larger Elements Right", "Compare leftward, shifting bigger elements one slot to the right to open a gap.", 0xFF3B82F6),
        StepCard(3, "Drop Into the Gap", "Place the picked element where the shifting stopped.", 0xFFF59E0B),
        StepCard(4, "Extend the Sorted Prefix", "Repeat until every element has been inserted.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Best case (sorted)", "O(n)", "Each element only compares once and stays put."),
        FormulaEntry("Average / worst", "O(n²)", "Reversed input shifts every element past all its predecessors."),
        FormulaEntry("Space", "O(1)", "In-place, and stable — equal elements keep their order."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("key", "the element currently being inserted"),
        NotationEntry("j", "scan index moving left through the sorted prefix"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Insertion sort",
            accentColor = 0xFF6366F1,
            code = """
                fun insertionSort(a: IntArray) {
                    for (i in 1 until a.size) {
                        val key = a[i]
                        var j = i - 1
                        while (j >= 0 && a[j] > key) {
                            a[j + 1] = a[j]
                            j--
                        }
                        a[j + 1] = key
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("trend", 0xFF10B981, "Nearly-Sorted Data", "On almost-sorted input it runs in near-linear time, ideal for keeping a live list ordered as items trickle in."),
        ApplicationCard("chip", 0xFF3B82F6, "Small Subarrays", "Fast sorts like quicksort switch to insertion sort for tiny partitions where its low overhead wins."),
        ApplicationCard("history", 0xFFF59E0B, "Online Sorting", "It can sort a stream as elements arrive, inserting each new value into the already-sorted portion."),
    ),
    takeaways = listOf(
        "Insertion sort is O(n²) in general but O(n) on nearly-sorted input — a real strength.",
        "It is in-place, stable, and simple, making it the go-to for small or almost-sorted arrays.",
        "Production sorts use it as the base case for their smallest partitions.",
        "For large, randomly-ordered data, a divide-and-conquer sort is far faster.",
    ),
    crossLinks = listOf(
        CrossLink("selection_sort", "Selection Sort"),
        CrossLink("merge_sort", "Merge Sort"),
    ),
)
