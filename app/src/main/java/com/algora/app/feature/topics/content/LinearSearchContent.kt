package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val linearSearchContent = TopicContent(
    topicId = "linear_search",
    whatIsIt = listOf(
        "Linear search looks for a target by checking each element in order until it finds a match or reaches the end.",
        "It makes no assumptions about the data — the array need not be sorted — which is why it is the fallback whenever no structure can be exploited.",
    ),
    steps = listOf(
        StepCard(1, "Start at the Beginning", "Point at the first element of the collection.", 0xFF10B981),
        StepCard(2, "Compare", "Check whether the current element equals the target.", 0xFF3B82F6),
        StepCard(3, "Advance or Stop", "On a match, return the index; otherwise move to the next element.", 0xFFF59E0B),
        StepCard(4, "Exhaust the List", "If the end is reached without a match, the target isn't present.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Worst / average", "O(n)", "May have to inspect every element."),
        FormulaEntry("Best case", "O(1)", "The target is the first element checked."),
        FormulaEntry("Space", "O(1)", "Only a running index is needed."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("target", "the value being searched for"),
        NotationEntry("i", "current scan index"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Linear search",
            accentColor = 0xFF6366F1,
            code = """
                fun linearSearch(a: IntArray, target: Int): Int {
                    for (i in a.indices) {
                        if (a[i] == target) return i
                    }
                    return -1
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SearchVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFF10B981, "Unsorted Data", "When data has no order or index, scanning every element is the only correct option."),
        ApplicationCard("chip", 0xFF3B82F6, "Small Collections", "For a handful of elements its zero setup cost beats building a hash table or sorting first."),
        ApplicationCard("check", 0xFFF59E0B, "Finding All Matches", "It naturally collects every element satisfying a predicate in one pass, not just the first."),
    ),
    takeaways = listOf(
        "Linear search is O(n) but needs no sorted or indexed data — the universal fallback.",
        "It is the right tool for small or one-off searches where preprocessing isn't worth it.",
        "For repeated lookups on large data, sort and binary-search or build a hash table instead.",
        "It generalizes to any sequence and any match condition, not just equality on arrays.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search", "Binary Search"),
        CrossLink("hash_table", "Hash Table / Hash Map"),
    ),
)
