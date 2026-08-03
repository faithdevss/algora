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

// Interview-prep pattern guide. The tails array is the trick worth remembering: a binary search
// turns the O(n²) LIS DP into O(n log n), and a whole family of problems reduces to it.
internal val lisPatiencePatternContent = TopicContent(
    topicId = "lis_patience_pattern",
    figure = Figure(
        caption = "tails[k] is the smallest value any increasing subsequence of length k+1 can end on. " +
            "Each element replaces the first tail ≥ itself, found by binary search — so the row's " +
            "*length* is the answer, while its contents are not a subsequence of the input.",
        shape = FigureShape.Strip(
            cells = listOf("10", "9", "2", "5", "3", "7", "101", "18"),
            bands = listOf(FigureBand(7, 7, "replaces tails[3]", FigureTone.Accent)),
            pointers = listOf(FigurePointer(7, "x")),
            aux = listOf("2", "3", "7", "18"),
            auxLabel = "tails — length 4 is the LIS length",
        ),
    ),
    whatIsIt = listOf(
        "Longest increasing subsequence has an obvious O(n²) DP, but the intended answer keeps a `tails` array: tails[k] is the smallest possible ending value of an increasing subsequence of length k+1. Each element replaces the first tail ≥ itself, found by binary search.",
        "The reduction matters more than the algorithm. Box nesting, Russian-doll envelopes, minimum removals to sort and maximum chain length all become LIS after the right sort — usually ascending on one dimension, descending on the tie-breaker so equal keys cannot chain.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Longest chain under a strict ordering, maximum nesting, or \"fewest deletions to make it increasing\" — which is n minus the LIS.", 0xFFF59E0B),
        StepCard(2, "Reduce to One Dimension", "For 2D problems, sort by the first dimension ascending and the second descending, then run LIS on the second. The descending tie-break blocks equal-width chains.", 0xFF3B82F6),
        StepCard(3, "Maintain the Tails", "For each x, binary-search tails for the first entry ≥ x. Replace it, or append when x exceeds every tail.", 0xFFEF4444),
        StepCard(4, "Read the Length, Rebuild if Asked", "len(tails) is the answer — but tails is *not* the subsequence. Record a parent index per element to reconstruct one.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time / Space", "O(n log n) / O(n)", "One binary search per element over the tails array."),
        FormulaEntry("Naive DP", "O(n²)", "dp[i] = 1 + max(dp[j]) over j < i with a[j] < a[i]."),
        FormulaEntry("Non-decreasing variant", "bisect_right instead of bisect_left", "Allows equal values to extend the chain."),
    ),
    notationKey = listOf(
        NotationEntry("tails[k]", "smallest tail of any increasing subsequence of length k+1"),
        NotationEntry("bisect_left", "first index whose value is ≥ x"),
        NotationEntry("len(tails)", "the LIS length — not the subsequence itself"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LIS in O(n log n) and envelope nesting (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from bisect import bisect_left

                def lis_length(a):
                    tails = []
                    for x in a:
                        i = bisect_left(tails, x)          # first tail >= x
                        if i == len(tails):
                            tails.append(x)                # extends the longest chain
                        else:
                            tails[i] = x                   # a smaller tail is strictly better
                    return len(tails)

                def max_envelopes(envelopes):              # (width, height) nesting
                    envelopes.sort(key=lambda e: (e[0], -e[1]))   # height descending on ties
                    return lis_length([h for _, h in envelopes])  # so equal widths cannot chain
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Trend Extraction", "Longest run of improving measurements inside a noisy series."),
        ApplicationCard("Layers", 0xFF10B981, "Nesting & Packing", "Russian-doll envelopes, box stacking and maximum chain of pairs."),
        ApplicationCard("code", 0xFF8B5CF6, "Diff & Reordering", "Minimum moves to sort, and stable-order edit scripts, are n minus the LIS."),
    ),
    takeaways = listOf(
        "tails[k] is the smallest ending value for length k+1 — that definition is the whole algorithm.",
        "The tails array holds the right *length*, never necessarily a valid subsequence; keep parents to rebuild one.",
        "2D problems become LIS after sorting ascending on one axis and descending on the tie-break.",
        "bisect_left for strictly increasing, bisect_right when equal values may repeat.",
    ),
    crossLinks = listOf(
        CrossLink("longest_increasing_subsequence", "Longest Increasing Subsequence (Algorithms)"),
        CrossLink("modified_binary_search_pattern", "Modified Binary Search"),
        CrossLink("grid_dp_pattern", "Grid & Sequence DP"),
    ),
)
