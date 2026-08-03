package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureStack
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val monotonicStackPatternContent = TopicContent(
    topicId = "monotonic_stack_pattern",
    figure = Figure(
        caption = "The stack holds indices whose answer is still unknown, kept in decreasing value " +
            "order. An incoming larger value pops every smaller one at once — each pop is one index " +
            "learning its answer, and each index is pushed and popped at most once, so O(n).",
        shape = FigureShape.Stacks(
            columns = listOf(
                FigureStack(
                    label = "stack (top first)",
                    entries = listOf("a[4] = 2", "a[3] = 6", "a[2] = 5"),
                    note = "values increase downward — the invariant",
                ),
                FigureStack(
                    label = "incoming a[5] = 3",
                    entries = listOf("3"),
                    tone = FigureTone.Accent,
                    note = "pops a[4] = 2, whose next greater is 3",
                ),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A monotonic stack keeps its contents sorted — increasing or decreasing — by popping anything that would break the order. Each pop is the moment a question about the popped element gets answered.",
        "It solves the whole \"next greater / previous smaller\" family in one pass, because an element only waits on the stack while no later element has beaten it yet.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "For each element, find the nearest larger/smaller one to its left or right — or a span bounded by such elements.", 0xFFF59E0B),
        StepCard(2, "Pick the Order", "Next greater → keep a decreasing stack. Next smaller → keep an increasing stack. Store indices, not values.", 0xFF3B82F6),
        StepCard(3, "Pop to Restore It", "While the incoming element breaks the order, pop. The incoming element is the popped one's answer.", 0xFFEF4444),
        StepCard(4, "Drain What's Left", "Anything still on the stack at the end has no answer — for heights problems, its span reaches the array's edge.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Each index is pushed once and popped at most once."),
        FormulaEntry("Space", "O(n)", "Worst case the whole array sits on the stack (already sorted input)."),
        FormulaEntry("Brute force it replaces", "O(n²)", "Scanning right from every index to find its next greater element."),
    ),
    notationKey = listOf(
        NotationEntry("st", "stack of indices, kept monotone"),
        NotationEntry("a[i]", "the incoming element being pushed"),
        NotationEntry("nge[i]", "next greater element for index i"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Next greater element (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def next_greater(a):
                    nge = [-1] * len(a)
                    st = []                      # indices, values decreasing
                    for i, x in enumerate(a):
                        while st and a[st[-1]] < x:
                            nge[st.pop()] = x    # x is the answer for that index
                        st.append(i)
                    return nge                   # leftovers stay -1
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Histogram Rectangles", "Largest rectangle in a histogram and maximal rectangles in a binary matrix."),
        ApplicationCard("trend", 0xFF10B981, "Stock Spans", "Days until a warmer temperature, or the span since the last higher price."),
        ApplicationCard("stack", 0xFF8B5CF6, "Expression Parsing", "Remove-k-digits, valid parentheses ranges and other order-sensitive scans."),
    ),
    takeaways = listOf(
        "Amortised O(n): push once, pop once, no rescanning.",
        "Store indices — you almost always need the distance, not just the value.",
        "The stack's direction follows the question: decreasing for next-greater, increasing for next-smaller.",
        "Whatever survives to the end had no qualifying neighbour; handle it explicitly.",
    ),
    crossLinks = listOf(
        CrossLink("stack", "Stack (Data Structures)"),
        CrossLink("deque", "Deque (Data Structures)"),
        CrossLink("faang_set", "Practice: FAANG Set"),
    ),
)
