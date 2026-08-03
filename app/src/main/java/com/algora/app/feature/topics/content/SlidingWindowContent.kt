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

internal val slidingWindowContent = TopicContent(
    topicId = "sliding_window",
    figure = Figure(
        caption = "The window's answer is repaired, not recomputed: one element leaves the left, one " +
            "joins the right, and the update is O(1) instead of O(k) — which is what turns an O(n·k) " +
            "scan into a single O(n) pass. The precondition is that the quantity being tracked is " +
            "incrementally invertible. Sums and counts are, so a running variable suffices. A maximum " +
            "is not: removing the element that happened to be the maximum tells you nothing about what " +
            "the new one is, which is exactly why maximum-in-window needs a monotonic deque rather than " +
            "a variable, and why that problem is a genuinely different exercise despite looking like " +
            "this one. The variable-size version is the same machinery with the left edge advanced by " +
            "a condition rather than by a fixed width.",
        shape = FigureShape.Strip(
            cells = listOf("2", "1", "5", "1", "3", "2"),
            bands = listOf(FigureBand(1, 3, "window k=3", FigureTone.Accent)),
            pointers = listOf(
                FigurePointer(1, "leaves", FigureTone.Warn),
                FigurePointer(4, "enters"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The sliding window technique maintains a moving subrange over a sequence, updating an answer incrementally as the window slides instead of recomputing it from scratch.",
        "By adding the new element and removing the old one at each step, it turns many O(n·k) or O(n²) scans into a single O(n) pass.",
    ),
    steps = listOf(
        StepCard(1, "Open the Window", "Expand the right edge, adding each new element into the window's running state.", 0xFF10B981),
        StepCard(2, "Maintain an Invariant", "Track a sum, count, or frequency map that summarizes the current window.", 0xFF3B82F6),
        StepCard(3, "Shrink When Invalid", "When the window breaks a constraint (size or condition), advance the left edge and undo its contribution.", 0xFFF59E0B),
        StepCard(4, "Record the Best", "At each valid state, update the answer — max length, min window, or count.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Each element enters and leaves the window at most once."),
        FormulaEntry("Space", "O(1) or O(k)", "Constant for sums; a map for distinct-element windows."),
        FormulaEntry("Fixed vs variable", "size k or condition", "Windows are either constant-width or grown/shrunk by a rule."),
    ),
    notationKey = listOf(
        NotationEntry("l, r", "left and right window boundaries"),
        NotationEntry("window state", "sum / count / frequency summary of l..r"),
        NotationEntry("k", "window size, for fixed-width variants"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Max sum of any window of size k",
            accentColor = 0xFF6366F1,
            code = """
                fun maxWindowSum(a: IntArray, k: Int): Int {
                    var sum = 0
                    for (i in 0 until k) sum += a[i]
                    var best = sum
                    for (r in k until a.size) {
                        sum += a[r] - a[r - k]      // add new, drop old
                        best = maxOf(best, sum)
                    }
                    return best
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Moving Averages", "Streaming metrics and stock indicators recompute rolling stats in O(1) per step."),
        ApplicationCard("link", 0xFF3B82F6, "Substring Problems", "Longest-substring-without-repeats and minimum-window-substring are classic variable windows."),
        ApplicationCard("trend", 0xFFF59E0B, "Rate Limiting", "Counting events in a rolling time window enforces per-interval quotas."),
    ),
    takeaways = listOf(
        "Sliding windows update an answer incrementally as the range moves, giving O(n).",
        "Fixed windows keep constant width; variable windows grow and shrink on a condition.",
        "The key is cheaply adding the entering element and removing the leaving one.",
        "It's a specialization of the two-pointer idea for contiguous subranges.",
    ),
    crossLinks = listOf(
        CrossLink("sliding_window_pattern", "Sliding Window"),
        CrossLink("two_pointer", "Two Pointer Technique"),
    ),
)
