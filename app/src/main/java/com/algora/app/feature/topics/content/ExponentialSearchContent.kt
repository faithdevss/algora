package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val exponentialSearchContent = TopicContent(
    topicId = "exponential_search",
    whatIsIt = listOf(
        "Exponential search finds a target in a sorted array by first doubling an index until it passes the target, then binary-searching the bounded range that must contain it.",
        "It shines when the array is unbounded or huge and the target is near the front — the range is found in a logarithmic number of doublings, before any binary search runs.",
    ),
    steps = listOf(
        StepCard(1, "Check the First Element", "If index 0 holds the target, you're done immediately.", 0xFF10B981),
        StepCard(2, "Double the Bound", "Try index 1, 2, 4, 8, … until that element exceeds the target or runs off the end.", 0xFF3B82F6),
        StepCard(3, "Fix the Range", "The target, if present, lies between the previous bound and the current one.", 0xFFF59E0B),
        StepCard(4, "Binary Search the Range", "Run a standard binary search over that bracketed subarray.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(log i)", "i is the target's index — doublings plus a binary search over the found range."),
        FormulaEntry("Space", "O(1)", "Iterative version needs only index variables."),
        FormulaEntry("Best when", "target near the front", "Or the array size is unknown/unbounded."),
    ),
    notationKey = listOf(
        NotationEntry("i", "index where the target lives"),
        NotationEntry("bound", "the doubling probe index (1, 2, 4, …)"),
        NotationEntry("lo, hi", "range handed to the binary search"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Exponential search",
            accentColor = 0xFF6366F1,
            code = """
                fun exponentialSearch(a: IntArray, target: Int): Int {
                    if (a.isEmpty()) return -1
                    if (a[0] == target) return 0
                    var bound = 1
                    while (bound < a.size && a[bound] < target) bound *= 2
                    val lo = bound / 2
                    val hi = minOf(bound, a.size - 1)
                    return binarySearch(a, target, lo, hi)
                }

                private fun binarySearch(a: IntArray, target: Int, lo0: Int, hi0: Int): Int {
                    var lo = lo0; var hi = hi0
                    while (lo <= hi) {
                        val mid = lo + (hi - lo) / 2
                        when {
                            a[mid] == target -> return mid
                            a[mid] < target -> lo = mid + 1
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
        ApplicationCard("trend", 0xFF10B981, "Unbounded / Streaming Data", "When the array length is unknown, doubling finds a valid range without needing the size up front."),
        ApplicationCard("search", 0xFF3B82F6, "Front-Loaded Targets", "If matches usually sit near the start, it beats plain binary search by bounding the range quickly."),
        ApplicationCard("chip", 0xFFF59E0B, "Sorted Index Probing", "Used over sorted on-disk or remote indexes where reads are sequential and cheap early on."),
    ),
    takeaways = listOf(
        "Exponential search doubles to bracket the target, then binary-searches that range, in O(log i).",
        "It is ideal for unbounded arrays or when the target is near the front.",
        "It requires sorted data, just like the binary search it delegates to.",
        "For a bounded array with a random target position, plain binary search is simpler and equivalent.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search", "Binary Search"),
        CrossLink("jump_search", "Jump Search"),
    ),
)
