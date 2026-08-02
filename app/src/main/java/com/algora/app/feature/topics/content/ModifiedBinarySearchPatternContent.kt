package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Sibling of binary_search_answer: same halving, but the monotone
// structure is already in the array — including a rotated one.
internal val modifiedBinarySearchPatternContent = TopicContent(
    topicId = "modified_binary_search_pattern",
    whatIsIt = listOf(
        "Textbook binary search finds an exact value. Interviews ask for the boundary instead: the first element ≥ x, the last one < x, the insertion point, or the target inside a rotated array.",
        "Reframe it as a predicate. If some test is false for a prefix and true for the rest, binary search finds the flip point — and returning the boundary index, not a found/not-found flag, handles duplicates, insertion and range queries with one routine.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Sorted (or rotated-sorted) input with a first/last/insertion-point question — or an O(log n) requirement stated outright.", 0xFFF59E0B),
        StepCard(2, "Write the Predicate", "Define a test that is monotone: false, false, …, true, true. The answer is the first true.", 0xFF3B82F6),
        StepCard(3, "Halve on a Half-Open Range", "Keep [lo, hi) with hi = n. mid = lo + (hi - lo) // 2; predicate true → hi = mid, else lo = mid + 1. The loop ends with lo == hi.", 0xFFEF4444),
        StepCard(4, "Rotated? Find the Sorted Half", "Compare a[mid] with a[lo]. One half is always sorted — check whether the target lies inside it, and discard the other half.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(log n)", "The candidate range halves every iteration."),
        FormulaEntry("Midpoint", "mid = lo + (hi - lo) / 2", "Avoids the overflow that (lo + hi) / 2 causes in fixed-width integers."),
        FormulaEntry("Range count", "upper(x) - lower(x)", "Two boundary searches give the count of a value with duplicates."),
    ),
    notationKey = listOf(
        NotationEntry("[lo, hi)", "half-open candidate range; hi starts at n"),
        NotationEntry("lower_bound", "first index with a[i] ≥ x"),
        NotationEntry("upper_bound", "first index with a[i] > x"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Boundary search and rotated search (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def lower_bound(a, x):                     # first index with a[i] >= x
                    lo, hi = 0, len(a)
                    while lo < hi:
                        mid = lo + (hi - lo) // 2
                        if a[mid] < x:
                            lo = mid + 1
                        else:
                            hi = mid                       # keep mid as a candidate
                    return lo                              # == len(a) if x is largest

                def search_rotated(a, target):
                    lo, hi = 0, len(a) - 1
                    while lo <= hi:
                        mid = lo + (hi - lo) // 2
                        if a[mid] == target:
                            return mid
                        if a[lo] <= a[mid]:                # left half is sorted
                            if a[lo] <= target < a[mid]:
                                hi = mid - 1
                            else:
                                lo = mid + 1
                        else:                              # right half is sorted
                            if a[mid] < target <= a[hi]:
                                lo = mid + 1
                            else:
                                hi = mid - 1
                    return -1
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SearchVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Insertion Points", "Where a value belongs in a sorted list — the basis of bisect and ordered inserts."),
        ApplicationCard("chart", 0xFF10B981, "Range Counting", "Occurrences of a value, or how many records fall inside a time window."),
        ApplicationCard("history", 0xFF8B5CF6, "Versioned Data", "First bad version, or the earliest log entry after a timestamp."),
    ),
    takeaways = listOf(
        "Search for the boundary, not the value — it covers exact match, insertion and counting at once.",
        "Pick one range convention and keep it. Mixing half-open and inclusive is what causes off-by-one and infinite loops.",
        "Every iteration must shrink the range; `lo = mid` without the +1 hangs.",
        "In a rotated array, one half is always sorted — decide with a[lo] ≤ a[mid], then discard.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search", "Binary Search (Algorithms)"),
        CrossLink("binary_search_answer", "Binary Search on Answer"),
        CrossLink("exponential_search", "Exponential Search (Algorithms)"),
    ),
)
