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
internal val prefixSumPatternContent = TopicContent(
    topicId = "prefix_sum_pattern",
    figure = Figure(
        caption = "One pass builds P, and every range query afterwards is two lookups and a " +
            "subtraction: sum[2..4] = P[4] − P[1] = 14 − 4 = 10. The width of the range never enters " +
            "the cost.",
        shape = FigureShape.Strip(
            cells = listOf("3", "1", "4", "1", "5", "9"),
            bands = listOf(FigureBand(2, 4, "range asked for", FigureTone.Accent)),
            pointers = listOf(FigurePointer(1, "P[l−1]"), FigurePointer(4, "P[r]")),
            aux = listOf("3", "4", "8", "9", "14", "23"),
            auxLabel = "P — inclusive prefix sums",
        ),
    ),
    whatIsIt = listOf(
        "The prefix sum pattern precomputes running totals once so that any later range question — the sum of a[l..r], the count of items before i — costs two lookups instead of a scan.",
        "Interviews rarely ask for the prefix array itself. They ask for \"subarray sums equal to k\" or \"pivot index\", where the answer falls out of pairing a running total with a hash map of totals already seen.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Repeated range queries, or a subarray condition stated as a sum or count over a contiguous span.", 0xFFF59E0B),
        StepCard(2, "Build the Running Total", "One pass accumulating P[i] = P[i-1] + a[i]. Keep P[-1] = 0 so ranges starting at 0 need no special case.", 0xFF3B82F6),
        StepCard(3, "Answer by Subtraction", "sum(l..r) = P[r] − P[l-1]. The width of the range never enters the cost.", 0xFF8B5CF6),
        StepCard(4, "Store Totals, Not Indices", "For \"count subarrays summing to k\", keep a map of prefix → how many times seen and look up total − k.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Build", "O(n)", "One pass to accumulate the running total."),
        FormulaEntry("Query", "O(1)", "Two array reads and a subtraction."),
        FormulaEntry("Count subarrays = k", "O(n) time, O(n) space", "One pass with a hash map of prefix frequencies."),
    ),
    notationKey = listOf(
        NotationEntry("P[i]", "sum of a[0..i], the inclusive prefix"),
        NotationEntry("l, r", "inclusive bounds of the queried range"),
        NotationEntry("k", "the target sum being matched"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Count subarrays summing to k (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def subarrays_with_sum(a, k):
                    seen = {0: 1}        # empty prefix, so a[0..r] itself counts
                    total = count = 0
                    for x in a:
                        total += x
                        count += seen.get(total - k, 0)
                        seen[total] = seen.get(total, 0) + 1
                    return count
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Range Sum Queries", "Immutable arrays answering thousands of sum queries after one build pass."),
        ApplicationCard("target", 0xFF10B981, "Subarray Targets", "Counting or locating subarrays whose sum hits a target, including negatives."),
        ApplicationCard("trend", 0xFF8B5CF6, "Running Aggregates", "Cumulative revenue, scores or event counts up to any point in a log."),
    ),
    takeaways = listOf(
        "Pay O(n) once, then answer any range in O(1).",
        "P[r] − P[l−1] is the whole idea; the sentinel P[-1] = 0 removes the edge case.",
        "Pairing the running total with a hash map handles negatives, which a sliding window cannot.",
        "The 2-D version (integral image) subtracts four corners instead of two ends.",
    ),
    crossLinks = listOf(
        CrossLink("prefix_sum", "Prefix Sum (Algorithms)"),
        CrossLink("difference_array", "Difference Array (Algorithms)"),
        CrossLink("timed_mock_interview", "Practice: Timed Mock Interview"),
    ),
)
