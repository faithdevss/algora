package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureStack
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val stackContent = TopicContent(
    topicId = "stack",
    figure = Figure(
        caption = "One end, two operations. Everything a stack is good at follows from that restriction: " +
            "the most recent thing is the cheapest to reach, so it models nesting — call frames, " +
            "bracket matching, undo — where the last context opened is always the first that must close.",
        shape = FigureShape.Stacks(
            columns = listOf(
                FigureStack(
                    label = "stack (top first)",
                    entries = listOf("D ← push / pop", "C", "B", "A"),
                    note = "only the top is reachable — O(1) both ways",
                ),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A stack is a last-in, first-out (LIFO) collection — elements are added and removed from the same end, called the top.",
        "Think of a stack of plates: you can only take from the top, and the last plate you put down is the first one you pick back up.",
    ),
    steps = listOf(
        StepCard(1, "Push", "Adds a new element on top of the stack.", 0xFF10B981),
        StepCard(2, "Pop", "Removes and returns the top element.", 0xFF3B82F6),
        StepCard(3, "Peek", "Reads the top element without removing it.", 0xFFF59E0B),
        StepCard(4, "LIFO Ordering", "The most recently pushed element is always the first one popped — no access to elements underneath.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Push / Pop / Peek", "O(1)", "All operations touch only the top element."),
        FormulaEntry("Search", "O(n)", "Finding an arbitrary element means popping down to it."),
    ),
    notationKey = listOf(
        NotationEntry("top", "the most recently pushed element"),
        NotationEntry("LIFO", "last-in, first-out"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Stack — array-backed",
            accentColor = 0xFF6366F1,
            code = """
                class Stack<T> {
                    private val items = mutableListOf<T>()

                    fun push(item: T) = items.add(item)

                    fun pop(): T? = items.removeLastOrNull()

                    fun peek(): T? = items.lastOrNull()

                    fun isEmpty(): Boolean = items.isEmpty()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.StackVisualizer,
    applications = listOf(
        ApplicationCard("history", 0xFF10B981, "Undo/Redo", "Each action pushes onto an undo stack; undoing pops the most recent one off."),
        ApplicationCard("chip", 0xFF3B82F6, "Function Call Stack", "Every function call pushes a stack frame; returning pops it — this is how recursion is implemented under the hood."),
        ApplicationCard("browser", 0xFFF59E0B, "Browser Back Button", "Visited pages push onto a history stack; back pops the most recent page."),
    ),
    takeaways = listOf(
        "A stack only exposes its top — push, pop, and peek are all O(1).",
        "LIFO ordering makes stacks the natural fit for anything with a 'most recent first' undo semantic.",
        "Recursion and stacks are deeply linked: every recursive call is implicitly a stack push.",
        "Unlike a queue, a stack cannot efficiently access or remove elements from the bottom.",
    ),
)
