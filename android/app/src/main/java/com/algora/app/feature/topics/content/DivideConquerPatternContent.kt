package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Split, solve, and — the part candidates forget — count or combine
// the cross-boundary cases during the merge.
internal val divideConquerPatternContent = TopicContent(
    topicId = "divide_conquer_pattern",
    figure = Figure(
        caption = "Splitting is the cheap half. The answers that *span* the split — the inversions " +
            "between the two halves, the subarray crossing the midpoint, the closest pair straddling " +
            "the line — are found during the combine step, and that step is the algorithm.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("5,3,8,1", null, FigureTone.Accent),
                FigureNode("5,3", 0, FigureTone.Primary),
                FigureNode("8,1", 0, FigureTone.Primary),
                FigureNode("5", 1),
                FigureNode("3", 1),
                FigureNode("8", 2),
                FigureNode("1", 2),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Divide and conquer splits the input in half, solves both halves recursively, and spends linear work combining them. The answers that span the split are found during that combine step — which is where the real algorithm lives.",
        "Counting inversions is the classic: merge sort's merge step already knows how many left-half elements outrank a right-half element, so the count falls out of a sort that was happening anyway.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A question about pairs or ranges across a whole array where sorting or halving does not destroy the answer — inversions, closest pair, range maxima.", 0xFFF59E0B),
        StepCard(2, "Split at the Middle", "Recurse on [lo, mid) and [mid, hi). Base case is a single element, which is trivially solved and sorted.", 0xFF3B82F6),
        StepCard(3, "Handle the Crossing Cases", "Everything with one endpoint in each half is counted or combined here — usually with two pointers over the two sorted halves.", 0xFFEF4444),
        StepCard(4, "Return a Useful Merge", "Return the merged, sorted half (or the aggregate the parent needs) so the parent's combine step is linear.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "T(n) = 2T(n/2) + O(n)", "Two halves plus a linear combine."),
        FormulaEntry("Master theorem", "T(n) = O(n log n)", "log n levels, O(n) work per level."),
        FormulaEntry("Quadratic combine", "T(n) = 2T(n/2) + O(n²) → O(n²)", "A slow combine erases the benefit of splitting."),
    ),
    notationKey = listOf(
        NotationEntry("mid", "split point, lo + (hi - lo) / 2"),
        NotationEntry("cross", "pairs with one endpoint in each half"),
        NotationEntry("T(n)", "cost of solving a problem of size n"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Counting inversions during a merge sort (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def count_inversions(a):
                    def sort_count(xs):
                        if len(xs) <= 1:
                            return xs, 0
                        mid = len(xs) // 2
                        left, li = sort_count(xs[:mid])
                        right, ri = sort_count(xs[mid:])
                        merged, ci = [], 0
                        i = j = 0
                        while i < len(left) and j < len(right):
                            if left[i] <= right[j]:
                                merged.append(left[i]); i += 1
                            else:
                                ci += len(left) - i      # left[i:] all outrank right[j]
                                merged.append(right[j]); j += 1
                        merged += left[i:] + right[j:]
                        return merged, li + ri + ci
                    return sort_count(a)[1]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Inversion Counting", "How far a ranking is from sorted — recommendation and diff scoring."),
        ApplicationCard("target", 0xFF10B981, "Closest Pair of Points", "O(n log n) geometry by only checking a narrow strip around the split."),
        ApplicationCard("chip", 0xFF8B5CF6, "Parallel Work", "Independent halves map cleanly onto threads or map-reduce stages."),
    ),
    takeaways = listOf(
        "The combine step is the algorithm; the recursion is bookkeeping.",
        "Return more than the answer — a sorted half makes the parent's merge linear.",
        "Write the recurrence before coding: a quadratic combine gives back everything the split earned.",
        "A Fenwick tree over compressed values solves many of the same counting problems iteratively.",
    ),
    crossLinks = listOf(
        CrossLink("merge_sort", "Merge Sort (Algorithms)"),
        CrossLink("quickselect", "Quickselect (Algorithms)"),
        CrossLink("closest_pair_of_points", "Closest Pair of Points (Algorithms)"),
    ),
)
