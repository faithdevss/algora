package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val priorityQueueAdtContent = TopicContent(
    topicId = "priority_queue_adt",
    whatIsIt = listOf(
        "A Priority Queue is an abstract data type that always serves the highest- (or lowest-) priority element next, regardless of insertion order.",
        "It's almost always implemented with a binary heap, giving O(log n) insert and remove and O(1) peek at the top-priority element.",
    ),
    steps = listOf(
        StepCard(1, "Priority, Not Arrival", "Unlike a FIFO queue, order of service is by priority value.", 0xFF10B981),
        StepCard(2, "Peek the Top", "The min (or max) priority element is always available in O(1).", 0xFF3B82F6),
        StepCard(3, "Insert (Enqueue)", "Add an element; the heap sifts it into place in O(log n).", 0xFFF59E0B),
        StepCard(4, "Extract (Dequeue)", "Remove the top-priority element; the heap re-heapifies in O(log n).", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Peek", "O(1)", "The extreme element sits at the heap root."),
        FormulaEntry("Insert / Extract", "O(log n)", "Sift up or down the height of the heap."),
        FormulaEntry("Build from n items", "O(n)", "Bottom-up heapify beats n inserts."),
    ),
    notationKey = listOf(
        NotationEntry("priority", "the key deciding service order"),
        NotationEntry("peek / poll", "read vs remove the top element"),
        NotationEntry("heap", "the usual backing structure"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Priority queue via a heap",
            accentColor = 0xFF6366F1,
            code = """
                // Min-priority queue: smallest value served first.
                val pq = java.util.PriorityQueue<Int>()
                pq.add(5); pq.add(1); pq.add(3)

                val top = pq.peek()     // 1  — O(1)
                val next = pq.poll()    // 1  — O(log n), removes it

                // Max-priority: supply a reverse comparator.
                val maxPq = java.util.PriorityQueue<Int>(compareByDescending { it })
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("map", 0xFF10B981, "Shortest-Path Search", "Dijkstra's and A* pull the closest frontier node from a priority queue each step."),
        ApplicationCard("history", 0xFF3B82F6, "Schedulers", "OS task schedulers and event simulations dequeue the next job by priority or timestamp."),
        ApplicationCard("chip", 0xFFF59E0B, "Streaming Top-K & Merging", "Merging k sorted streams and tracking top-K elements both lean on a priority queue."),
    ),
    takeaways = listOf(
        "A Priority Queue serves elements by priority, not arrival order.",
        "A binary heap gives O(log n) insert/extract and O(1) peek — the standard implementation.",
        "It's the engine behind Dijkstra's, A*, Huffman coding, and event scheduling.",
        "For strict FIFO, use a plain queue; the priority queue reorders by key.",
    ),
    crossLinks = listOf(
        CrossLink("heap", "Heap (Min / Max)"),
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
    ),
)
