package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
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

internal val queueContent = TopicContent(
    topicId = "queue",
    figure = Figure(
        caption = "Two ends: add at the back, remove from the front. Implemented on an array the indices " +
            "wrap around modulo the capacity, so nothing is ever shifted — the empty cells behind the " +
            "front are reused, and the structure is a ring rather than a line.",
        shape = FigureShape.Strip(
            cells = listOf("·", "·", "C", "D", "E", "·"),
            bands = listOf(
                FigureBand(0, 1, "free (wraps)", FigureTone.Muted),
                FigureBand(2, 4, "in the queue"),
            ),
            pointers = listOf(FigurePointer(2, "front"), FigurePointer(5, "back")),
        ),
    ),
    whatIsIt = listOf(
        "A queue is a first-in, first-out (FIFO) collection — elements are added at the back and removed from the front.",
        "Think of a checkout line: the first person to join is the first person served.",
    ),
    steps = listOf(
        StepCard(1, "Enqueue", "Adds a new element at the back of the queue.", 0xFF10B981),
        StepCard(2, "Dequeue", "Removes and returns the element at the front.", 0xFF3B82F6),
        StepCard(3, "Peek", "Reads the front element without removing it.", 0xFFF59E0B),
        StepCard(4, "FIFO Ordering", "The element that has waited longest is always served next.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Enqueue / Dequeue / Peek", "O(1)", "A ring buffer or linked list avoids shifting on either end."),
        FormulaEntry("Search", "O(n)", "Finding an arbitrary element means scanning from the front."),
    ),
    notationKey = listOf(
        NotationEntry("front", "the next element to be dequeued"),
        NotationEntry("FIFO", "first-in, first-out"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Queue — deque-backed",
            accentColor = 0xFF6366F1,
            code = """
                class Queue<T> {
                    private val items = ArrayDeque<T>()

                    fun enqueue(item: T) = items.addLast(item)

                    fun dequeue(): T? = items.removeFirstOrNull()

                    fun peek(): T? = items.firstOrNull()

                    fun isEmpty(): Boolean = items.isEmpty()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.QueueVisualizer,
    applications = listOf(
        ApplicationCard("history", 0xFF10B981, "Task Scheduling", "Print jobs, print spoolers, and CPU task queues all process work in arrival order."),
        ApplicationCard("share", 0xFF3B82F6, "BFS Traversal", "Breadth-first search on a graph or tree uses a queue to visit nodes level by level."),
        ApplicationCard("network", 0xFFF59E0B, "Request Buffering", "Servers queue incoming requests so bursts of traffic get processed in order instead of dropped."),
    ),
    takeaways = listOf(
        "A queue only exposes its front and back — enqueue, dequeue, and peek are all O(1) with the right backing structure.",
        "FIFO ordering makes queues the natural fit for anything that must preserve arrival order.",
        "Breadth-first search relies on a queue the same way depth-first search relies on a stack.",
        "A plain array-backed queue needs care (ring buffer or deque) to avoid O(n) shifting on dequeue.",
    ),
)
