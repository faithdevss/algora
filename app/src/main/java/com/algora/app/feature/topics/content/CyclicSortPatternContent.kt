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

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val cyclicSortPatternContent = TopicContent(
    topicId = "cyclic_sort_pattern",
    figure = Figure(
        caption = "Values 1..n each know the index they belong at, so sorting needs no comparisons — " +
            "only swaps. Whatever index ends up holding the wrong value is both the duplicate and the " +
            "missing number.",
        shape = FigureShape.Strip(
            cells = listOf("3", "1", "5", "4", "3"),
            bands = listOf(FigureBand(2, 2, "belongs at index 4", FigureTone.Accent)),
            pointers = listOf(FigurePointer(0, "i"), FigurePointer(4, "home")),
            aux = listOf("1", "2", "3", "4", "5"),
            auxLabel = "index i wants value i+1",
        ),
    ),
    whatIsIt = listOf(
        "Cyclic sort exploits a promise the problem hands you: the values are a permutation of 1..n (or 0..n−1). That means every value already knows the index it belongs at.",
        "Sorting is then a sequence of swaps that each place one value permanently, and the leftover mismatches spell out the missing, duplicate or corrupted entries — in O(n) time and no extra memory.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"n numbers in the range 1..n\" plus \"find the missing / duplicate\" and a hint about O(1) space.", 0xFFF59E0B),
        StepCard(2, "Swap Value Home", "At index i, while a[i] is not already at index a[i]−1, swap it there. Do not advance i on a swap.", 0xFF3B82F6),
        StepCard(3, "Advance Only When Settled", "When a[i] == i + 1 (or the target slot already holds its value), move to i + 1.", 0xFF8B5CF6),
        StepCard(4, "Read the Mismatches", "One pass: any i where a[i] != i + 1 exposes a missing value (i + 1) and a duplicate (a[i]).", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Every swap puts one value in its final slot, so there are at most n swaps."),
        FormulaEntry("Space", "O(1)", "In-place; no hash set or count array."),
        FormulaEntry("Applies when", "values ∈ 1..n", "Outside that range the index mapping is undefined."),
    ),
    notationKey = listOf(
        NotationEntry("n", "length of the array and the top of the value range"),
        NotationEntry("a[i]", "value currently at index i"),
        NotationEntry("a[i] − 1", "the index that value belongs at"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Find missing and duplicate (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def find_corrupt(a):
                    i = 0
                    while i < len(a):
                        home = a[i] - 1
                        if a[i] != a[home]:            # target slot holds something else
                            a[i], a[home] = a[home], a[i]
                        else:
                            i += 1                     # settled, or a duplicate
                    for i, x in enumerate(a):
                        if x != i + 1:
                            return {"duplicate": x, "missing": i + 1}
                    return None
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Missing Numbers", "First missing positive, all numbers absent from 1..n."),
        ApplicationCard("check", 0xFF10B981, "Duplicate Detection", "Find the repeated ID in a batch without a hash set."),
        ApplicationCard("chip", 0xFF8B5CF6, "Slot Assignment", "Any setting where item i owns a fixed seat and you're auditing the seating."),
    ),
    takeaways = listOf(
        "Only applies when values map onto indices — check the range before reaching for it.",
        "Compare against a[a[i]−1], not against i, so duplicates terminate the loop.",
        "Do not advance i after a swap; the value you just received still needs a home.",
        "It's the O(1)-space answer to problems a hash set would solve in O(n) space.",
    ),
    crossLinks = listOf(
        CrossLink("counting_sort", "Counting Sort (Algorithms)"),
        CrossLink("two_pointer_pattern", "Two Pointer Pattern"),
        CrossLink("timed_mock_interview", "Practice: Timed Mock Interview"),
    ),
)
