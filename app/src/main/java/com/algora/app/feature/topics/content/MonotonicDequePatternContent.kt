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

// Interview-prep pattern guide. The window-max sibling of the monotonic stack: same popping rule,
// plus an expiry check at the front because the window moves.
internal val monotonicDequePatternContent = TopicContent(
    topicId = "monotonic_deque_pattern",
    figure = Figure(
        caption = "The deque holds indices whose values decrease front to back, so its front is the " +
            "window's maximum. Two rules per element: drop the front when it slides out, pop the back " +
            "while it is dominated — each index enters and leaves once, so O(n) not O(n·k).",
        shape = FigureShape.Strip(
            cells = listOf("1", "3", "−1", "−3", "5", "3", "6"),
            bands = listOf(FigureBand(4, 6, "window, k = 3")),
            pointers = listOf(FigurePointer(6, "max")),
            aux = listOf("6", "3"),
            auxLabel = "deque — values decreasing, front first",
        ),
    ),
    whatIsIt = listOf(
        "A monotonic deque keeps the indices of a sliding window in decreasing value order. The front is the window's maximum; anything smaller than the incoming element can never be the max again, so it is dropped from the back.",
        "It is what makes sliding-window maximum O(n) instead of O(n·k). A heap also works but costs O(n log k) and needs lazy deletion, because the element leaving the window is rarely the heap's root.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Max or min of every window of fixed width k — or a window-constrained DP that keeps asking for the best earlier state.", 0xFFF59E0B),
        StepCard(2, "Evict the Expired", "Before answering, pop the front while its index ≤ i - k. That element has slid out of the window.", 0xFF3B82F6),
        StepCard(3, "Pop the Dominated", "While the back's value ≤ the incoming value, pop it. A smaller, older element is useless once a bigger, newer one exists.", 0xFFEF4444),
        StepCard(4, "Read the Front", "Push the new index. Once i ≥ k - 1, the front index holds the current window's maximum.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Each index enters and leaves the deque exactly once."),
        FormulaEntry("Space", "O(k)", "The deque never holds more than one window's worth of indices."),
        FormulaEntry("Heap alternative", "O(n log k)", "Correct but slower, and needs stale-entry handling."),
    ),
    notationKey = listOf(
        NotationEntry("dq", "deque of indices, values decreasing front to back"),
        NotationEntry("k", "window width"),
        NotationEntry("i - k", "the last index that has already expired"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Sliding window maximum (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import deque

                def window_max(a, k):
                    dq, out = deque(), []            # dq holds indices, a[dq] decreasing
                    for i, x in enumerate(a):
                        if dq and dq[0] <= i - k:
                            dq.popleft()             # front slid out of the window
                        while dq and a[dq[-1]] <= x:
                            dq.pop()                 # dominated by the newer, larger x
                        dq.append(i)
                        if i >= k - 1:
                            out.append(a[dq[0]])
                    return out
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Rolling Extremes", "Peak load or lowest price over the last k samples of a metric stream."),
        ApplicationCard("target", 0xFF10B981, "Constrained DP", "Jump Game VI and similar: dp[i] = a[i] + max(dp[i-k..i-1]) in O(n)."),
        ApplicationCard("stack", 0xFF8B5CF6, "Bounded Subarrays", "Longest subarray whose max − min ≤ limit, using one decreasing and one increasing deque."),
    ),
    takeaways = listOf(
        "Store indices, not values — expiry is an index comparison.",
        "Two rules, in order: evict expired at the front, then pop dominated at the back.",
        "Flip the comparison to `>=` for a window minimum; run both deques when you need max and min together.",
        "Amortised O(1) per element: every index is pushed once and popped once.",
    ),
    crossLinks = listOf(
        CrossLink("deque", "Deque (Data Structures)"),
        CrossLink("sliding_window_pattern", "Sliding Window Pattern"),
        CrossLink("monotonic_stack_pattern", "Monotonic Stack"),
    ),
)
