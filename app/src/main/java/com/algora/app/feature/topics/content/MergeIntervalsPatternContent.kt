package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val mergeIntervalsPatternContent = TopicContent(
    topicId = "merge_intervals_pattern",
    whatIsIt = listOf(
        "The merge-intervals pattern consolidates a set of ranges into the fewest disjoint ranges by sorting them once and sweeping left to right, joining any that touch or overlap.",
        "Reach for it whenever the input is intervals — meeting times, numeric ranges, byte offsets — and the question involves overlap, gaps, or coverage.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "The input is a list of [start, end] pairs and the question asks about overlaps, merges, free gaps, or minimum resources.", 0xFFF59E0B),
        StepCard(2, "Sort by Start", "Order the intervals by their start value. After this, any overlap can only be with the interval immediately before it.", 0xFF3B82F6),
        StepCard(3, "Sweep and Merge", "Walk the sorted list. If the current start is ≤ the last kept end, extend that end to the max of the two; otherwise begin a new interval.", 0xFF8B5CF6),
        StepCard(4, "Emit the Result", "The kept list is the merged set. The whole cost is dominated by the sort — O(n log n).", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log n)", "The sort dominates; the sweep itself is a single O(n) pass."),
        FormulaEntry("Space", "O(n)", "Output list of merged intervals (O(1) beyond it if merging in place)."),
        FormulaEntry("Overlap test", "aₛ ≤ bₑ", "Two intervals touch when the later start is within the earlier end."),
    ),
    notationKey = listOf(
        NotationEntry("[s, e]", "an interval with start s and end e"),
        NotationEntry("n", "number of intervals"),
        NotationEntry("last", "the most recently kept (open) interval"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Merge overlapping intervals (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def merge(intervals):
                    intervals.sort(key=lambda p: p[0])   # sort by start
                    out = []
                    for s, e in intervals:
                        if out and s <= out[-1][1]:      # overlaps the last kept
                            out[-1][1] = max(out[-1][1], e)
                        else:
                            out.append([s, e])           # disjoint — start fresh
                    return out
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Calendar Scheduling", "Collapsing busy blocks to find free slots or double-bookings across meetings."),
        ApplicationCard("trend", 0xFF10B981, "Range Consolidation", "Merging IP ranges, byte offsets, or version spans into canonical form."),
        ApplicationCard("search", 0xFF8B5CF6, "Resource Allocation", "Minimum meeting rooms / CPUs = peak overlap depth after sorting endpoints."),
    ),
    takeaways = listOf(
        "Sorting by start is the whole trick — it guarantees overlaps are adjacent.",
        "After sorting, one linear sweep merges everything; total cost is O(n log n).",
        "The overlap test is simply: current start ≤ last kept end.",
        "'Minimum rooms' variants sweep start/end events instead of merging ranges.",
    ),
    crossLinks = listOf(
        CrossLink("merge_sort", "Merge Sort (Algorithms)"),
        CrossLink("timed_mock_interview", "Practice: Timed Mock Interview"),
    ),
)
