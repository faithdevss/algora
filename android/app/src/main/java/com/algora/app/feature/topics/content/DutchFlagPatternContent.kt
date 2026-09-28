package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
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

// Interview-prep pattern guide. Two pointers that partition rather than converge on a pair — the
// engine inside quicksort, and the answer to most "sort by category in place" questions.
internal val dutchFlagPatternContent = TopicContent(
    topicId = "dutch_flag_pattern",
    figure = Figure(
        caption = "Four regions, three pointers, one pass, no extra array. Everything mid touches is " +
            "classified immediately — except after a swap with high, where mid stalls, because the " +
            "value that just arrived has never been looked at.",
        shape = FigureShape.Strip(
            cells = listOf("0", "0", "1", "?", "?", "2", "2"),
            bands = listOf(
                FigureBand(0, 1, "< pivot"),
                FigureBand(2, 2, "= pivot", FigureTone.Accent),
                FigureBand(3, 4, "unclassified", FigureTone.Muted),
                FigureBand(5, 6, "> pivot", FigureTone.Warn),
            ),
            pointers = listOf(FigurePointer(2, "low"), FigurePointer(3, "mid"), FigurePointer(4, "high")),
        ),
    ),
    whatIsIt = listOf(
        "In-place partitioning sweeps once and rearranges the array into regions — smaller, equal, larger — using pointers that mark the region boundaries. No extra array, one pass, stable regions by construction of the invariant.",
        "The three-way version (Dijkstra's Dutch national flag) keeps low, mid and high. Everything before low is small, everything after high is large, and mid scans the unclassified middle until it meets high.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Reorder into a few categories in place — sort colours, move zeroes, evens before odds, or partition around a pivot for quickselect.", 0xFFF59E0B),
        StepCard(2, "Name the Regions", "Write the invariant: [0, low) small, [low, mid) equal, [mid, high] unknown, (high, n) large. The code follows from it.", 0xFF3B82F6),
        StepCard(3, "Classify the Front", "Small → swap with low, advance both. Equal → advance mid only. Large → swap with high and shrink high *without* advancing mid.", 0xFFEF4444),
        StepCard(4, "Stop When They Cross", "Loop while mid ≤ high. The element swapped down from high is unexamined, which is why mid stays put.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time / Space", "O(n) / O(1)", "One pass, swaps only — no auxiliary array."),
        FormulaEntry("Invariant", "[0, low) < p ≤ [low, mid) ; (high, n) > p", "Everything the loop must preserve."),
        FormulaEntry("Quickselect partition", "O(n) expected", "Three-way partitioning also handles duplicate-heavy input in quicksort."),
    ),
    notationKey = listOf(
        NotationEntry("low", "first index of the equal region"),
        NotationEntry("mid", "cursor over the unclassified region"),
        NotationEntry("high", "last index not yet known to be large"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Three-way partition (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def sort_three(a, pivot):
                    low, mid, high = 0, 0, len(a) - 1
                    while mid <= high:
                        if a[mid] < pivot:
                            a[low], a[mid] = a[mid], a[low]
                            low += 1
                            mid += 1                       # swapped-in value already classified
                        elif a[mid] > pivot:
                            a[mid], a[high] = a[high], a[mid]
                            high -= 1                      # mid stays: new value is unexamined
                        else:
                            mid += 1
                    return a

                def move_zeroes(a):                        # two-region version
                    write = 0
                    for read in range(len(a)):
                        if a[read] != 0:
                            a[write], a[read] = a[read], a[write]
                            write += 1
                    return a
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Bucketing In Place", "Group records by a small number of categories without allocating."),
        ApplicationCard("target", 0xFF10B981, "Quickselect", "Partitioning around a pivot is the core loop of selection and quicksort."),
        ApplicationCard("chip", 0xFF8B5CF6, "Memory-Constrained Sorting", "Duplicate-heavy keys sort in O(n) with three-way partitioning."),
    ),
    takeaways = listOf(
        "State the invariant first; the three branches are then forced.",
        "After swapping from the high side, do not advance mid — that value has not been looked at.",
        "Two regions need one write pointer; three need low/mid/high.",
        "In-place partitioning is not stable — if relative order matters, use a counting pass instead.",
    ),
    crossLinks = listOf(
        CrossLink("quick_sort", "Quick Sort (Algorithms)"),
        CrossLink("quickselect", "Quickselect (Algorithms)"),
        CrossLink("two_pointer_pattern", "Two Pointer Pattern"),
    ),
)
