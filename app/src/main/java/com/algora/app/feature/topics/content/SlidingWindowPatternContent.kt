package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val slidingWindowPatternContent = TopicContent(
    topicId = "sliding_window_pattern",
    figure = Figure(
        caption = "The window is the whole state. It grows on the right, and shrinks from the left only " +
            "while the constraint is broken — so every index is added once and removed once, which is " +
            "why the scan is O(n) rather than O(n²).",
        shape = FigureShape.Strip(
            cells = listOf("a", "b", "c", "a", "d", "e"),
            bands = listOf(FigureBand(1, 3, "window: no repeats")),
            pointers = listOf(FigurePointer(1, "l"), FigurePointer(3, "r")),
        ),
    ),
    whatIsIt = listOf(
        "The sliding window pattern turns a nested-loop scan over every subarray or substring into a single pass by maintaining a moving range and updating it incrementally.",
        "Reach for it whenever a problem asks about a contiguous run — the longest, shortest, or best subarray/substring satisfying some condition.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Contiguous subarray/substring + an optimum (longest/shortest/max sum) is the tell-tale sign.", 0xFFF59E0B),
        StepCard(2, "Grow the Window", "Advance the right edge, adding each new element into a running total or frequency map.", 0xFF3B82F6),
        StepCard(3, "Shrink When Invalid", "While the window breaks the constraint, advance the left edge and remove elements until it's valid again.", 0xFFEF4444),
        StepCard(4, "Record the Best", "After every valid state, update the answer. Each element enters and leaves the window at most once — O(n).", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Each index is added and removed once across the whole scan."),
        FormulaEntry("Space", "O(k)", "k distinct elements tracked inside the window."),
        FormulaEntry("Brute force it replaces", "O(n²) or O(n³)", "Re-scanning every subarray from scratch."),
    ),
    notationKey = listOf(
        NotationEntry("l, r", "the left and right window edges"),
        NotationEntry("n", "length of the input"),
        NotationEntry("k", "distinct items or window width"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Longest substring w/o repeats (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def longest_unique(s):
                    seen = {}
                    l = best = 0
                    for r, ch in enumerate(s):
                        if ch in seen and seen[ch] >= l:
                            l = seen[ch] + 1     # jump left past the repeat
                        seen[ch] = r
                        best = max(best, r - l + 1)
                    return best
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Longest / Shortest Substring", "Longest substring without repeats, smallest window containing all targets."),
        ApplicationCard("trend", 0xFF10B981, "Rolling Aggregates", "Maximum sum or average of any k consecutive readings in a stream."),
        ApplicationCard("chip", 0xFF8B5CF6, "Rate Limiting", "Counting events within a moving time window to throttle requests."),
    ),
    takeaways = listOf(
        "Collapses an O(n²) subarray scan into a single O(n) pass.",
        "Two flavours: fixed-width windows and variable-width (grow/shrink) windows.",
        "The invariant lives in a running total or a frequency map updated on both edges.",
        "If the problem says 'contiguous' and 'longest/shortest', try a window first.",
    ),
    crossLinks = listOf(
        CrossLink("sliding_window", "Sliding Window (Algorithms)"),
        CrossLink("timed_mock_interview", "Practice: Timed Mock Interview"),
    ),
)
