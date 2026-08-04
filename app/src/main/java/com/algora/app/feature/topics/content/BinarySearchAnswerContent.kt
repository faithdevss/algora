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
internal val binarySearchAnswerContent = TopicContent(
    topicId = "binary_search_answer",
    figure = Figure(
        caption = "The array is not what is searched — the *answer space* is. Each candidate is fed to " +
            "a monotone feasibility test, and because the answers form a false…false-true…true run, the " +
            "same discard-half loop applies to a range that was never stored anywhere.",
        shape = FigureShape.Strip(
            cells = listOf("✗", "✗", "✗", "✗", "✓", "✓", "✓", "✓"),
            bands = listOf(
                FigureBand(0, 3, "too small", FigureTone.Warn),
                FigureBand(4, 4, "answer", FigureTone.Accent),
                FigureBand(5, 7, "works, wasteful", FigureTone.Muted),
            ),
            pointers = listOf(FigurePointer(0, "lo"), FigurePointer(3, "mid"), FigurePointer(7, "hi")),
        ),
    ),
    whatIsIt = listOf(
        "Binary search on the answer drops the requirement that the input be sorted. What has to be monotone is the question you ask about a candidate answer: if capacity 15 works, every larger capacity works too.",
        "The search then runs over the range of possible answers — capacities, speeds, distances — and each probe calls a feasibility check that scans the input once.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"Minimum largest …\", \"maximum smallest …\", or a threshold under a budget. The array itself is usually unsorted.", 0xFFF59E0B),
        StepCard(2, "Write feasible(x)", "A single-pass predicate: can the job be done with candidate x? It must be monotone — false, false, …, true, true.", 0xFF3B82F6),
        StepCard(3, "Bound the Range", "lo = the smallest answer that could conceivably work (often max element), hi = one that certainly works (often the total).", 0xFFEF4444),
        StepCard(4, "Halve to the Boundary", "Keep the first true: if feasible(mid), hi = mid, else lo = mid + 1. Converge to lo == hi.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log R)", "log R probes, each running an O(n) feasibility scan."),
        FormulaEntry("R", "hi − lo", "Width of the answer range, not the length of the input."),
        FormulaEntry("Space", "O(1)", "Two bounds and whatever the predicate accumulates."),
    ),
    notationKey = listOf(
        NotationEntry("x", "a candidate answer being tested"),
        NotationEntry("feasible(x)", "monotone predicate: does x suffice?"),
        NotationEntry("lo, hi", "current bounds on the answer"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Minimum ship capacity in D days (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def ship_capacity(weights, days):
                    def feasible(cap):
                        used, load = 1, 0
                        for w in weights:
                            if load + w > cap:
                                used, load = used + 1, 0
                            load += w
                        return used <= days

                    lo, hi = max(weights), sum(weights)
                    while lo < hi:
                        mid = (lo + hi) // 2
                        if feasible(mid):
                            hi = mid          # keep this candidate, try smaller
                        else:
                            lo = mid + 1
                    return lo
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Capacity Planning", "Smallest machine, truck or shard size that clears the workload in time."),
        ApplicationCard("history", 0xFF10B981, "Rate & Deadline Problems", "Slowest eating speed, minimum days, smallest divisor under a cap."),
        ApplicationCard("target", 0xFF8B5CF6, "Minimax Splits", "Split an array into k parts minimising the largest part."),
    ),
    takeaways = listOf(
        "The array need not be sorted — the predicate must be monotone.",
        "Cost is O(n log R): the log comes from the answer range, the n from each check.",
        "hi = mid (not mid − 1) when the answer is 'the first x that works'.",
        "If you can't state feasible(x) in one pass, this is the wrong pattern.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search", "Binary Search (Algorithms)"),
        CrossLink("exponential_search", "Exponential Search (Algorithms)"),
        CrossLink("sorting_searching_quiz", "Practice: Sorting & Searching Quiz"),
    ),
)
