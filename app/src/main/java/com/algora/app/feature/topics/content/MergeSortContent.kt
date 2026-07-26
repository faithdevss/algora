package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mergeSortContent = TopicContent(
    topicId = "merge_sort",
    whatIsIt = listOf(
        "Merge sort is a divide-and-conquer sort that splits an array in half, recursively sorts each half, then merges the two sorted halves back together.",
        "The merge step is the key insight: merging two already-sorted lists into one sorted list only takes a single linear pass.",
    ),
    steps = listOf(
        StepCard(1, "Divide", "Split the array into two halves, recursively, until each piece has one element.", 0xFF3B82F6),
        StepCard(2, "Conquer", "A single-element array is trivially sorted — that's the base case.", 0xFF8B5CF6),
        StepCard(3, "Merge", "Combine two sorted halves by repeatedly taking the smaller front element from either side.", 0xFFF59E0B),
        StepCard(4, "Stable & Predictable", "Merge sort's runtime is O(n log n) in every case — best, average, and worst.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time complexity (all cases)", "O(n log n)", "log n levels of splitting, O(n) merge work at each level."),
        FormulaEntry("Space complexity", "O(n)", "Merging needs an auxiliary array the size of the input."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements in the array"),
        NotationEntry("mid", "the split point of the current subarray"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Merge Sort",
            accentColor = 0xFF6366F1,
            code = """
                fun mergeSort(arr: IntArray): IntArray {
                    if (arr.size <= 1) return arr
                    val mid = arr.size / 2
                    val left = mergeSort(arr.sliceArray(0 until mid))
                    val right = mergeSort(arr.sliceArray(mid until arr.size))
                    return merge(left, right)
                }

                private fun merge(left: IntArray, right: IntArray): IntArray {
                    val result = IntArray(left.size + right.size)
                    var i = 0; var j = 0; var k = 0
                    while (i < left.size && j < right.size) {
                        result[k++] = if (left[i] <= right[j]) left[i++] else right[j++]
                    }
                    while (i < left.size) result[k++] = left[i++]
                    while (j < right.size) result[k++] = right[j++]
                    return result
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("stack", 0xFF3B82F6, "External Sorting", "Sorting data too large for memory reads/merges chunks from disk — the same merge step scales up."),
        ApplicationCard("chip", 0xFF8B5CF6, "Stable Sort Requirements", "Merge sort preserves the relative order of equal elements, which matters for multi-key sorts."),
        ApplicationCard("trend", 0xFFF59E0B, "Guaranteed Worst-Case Bound", "Systems needing predictable O(n log n) even on adversarial input prefer merge sort over quicksort."),
    ),
    takeaways = listOf(
        "Merge sort guarantees O(n log n) in every case — no worst-case O(n²) like quicksort's pivot-unlucky scenario.",
        "The trade-off is O(n) extra space for the merge step, unlike in-place quicksort.",
        "Merge sort is stable — equal elements keep their original relative order.",
        "The divide-and-conquer pattern here (split, recurse, combine) reappears throughout algorithm design.",
    ),
)
