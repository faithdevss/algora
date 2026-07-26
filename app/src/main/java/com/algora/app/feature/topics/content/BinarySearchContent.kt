package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val binarySearchContent = TopicContent(
    topicId = "binary_search",
    whatIsIt = listOf(
        "Binary search finds a target value in a sorted array by repeatedly halving the search range.",
        "Think of looking up a word in a paper dictionary: you open to the middle, decide which half your word is in, and repeat — never scanning page by page.",
    ),
    steps = listOf(
        StepCard(1, "Requires Sorted Input", "Binary search only works because the array is sorted — that's what lets you discard half each step.", 0xFF06B6D4),
        StepCard(2, "Check the Middle", "Compare the target to the middle element of the current range.", 0xFF3B82F6),
        StepCard(3, "Discard Half", "If the target is smaller, discard the right half; if larger, discard the left half.", 0xFF8B5CF6),
        StepCard(4, "Repeat Until Found", "Keep halving the range until the target is found or the range is empty.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Time complexity", "O(log n)", "Each comparison eliminates half of the remaining elements."),
        FormulaEntry("Space complexity", "O(1)", "Iterative version needs only a few index variables."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements in the array"),
        NotationEntry("lo, hi", "current search range boundaries"),
        NotationEntry("mid", "the midpoint index being checked"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Binary Search — iterative",
            accentColor = 0xFF6366F1,
            code = """
                fun binarySearch(sorted: IntArray, target: Int): Int {
                    var lo = 0
                    var hi = sorted.size - 1

                    while (lo <= hi) {
                        val mid = lo + (hi - lo) / 2
                        when {
                            sorted[mid] == target -> return mid
                            sorted[mid] < target -> lo = mid + 1
                            else -> hi = mid - 1
                        }
                    }
                    return -1
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SearchVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFF06B6D4, "Dictionary/Index Lookups", "Any sorted lookup table — word lists, sorted database indexes — searches in logarithmic time."),
        ApplicationCard("trend", 0xFF3B82F6, "Version Bisection", "`git bisect` binary-searches commit history to find the commit that introduced a bug."),
        ApplicationCard("chip", 0xFF8B5CF6, "Finding Insertion Points", "Binary search variants locate where a value should be inserted to keep an array sorted."),
    ),
    takeaways = listOf(
        "Binary search needs sorted input — running it on unsorted data gives wrong answers, not just slow ones.",
        "Each comparison halves the search space, giving O(log n) time from O(n) linear search.",
        "The classic bug is mid computation overflow; `lo + (hi - lo) / 2` avoids it.",
        "Binary search generalizes beyond arrays to any monotonic predicate — 'find the boundary where a condition flips.'",
    ),
)
