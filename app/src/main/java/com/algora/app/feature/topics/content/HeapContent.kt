package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val heapContent = TopicContent(
    topicId = "heap",
    whatIsIt = listOf(
        "A heap is a complete binary tree that maintains the heap property: in a min-heap every parent is ≤ its children, so the smallest element is always at the root (a max-heap keeps the largest).",
        "Because the tree is complete, it packs perfectly into an array — a node at index i finds its children at 2i+1 and 2i+2, so no pointers are needed.",
    ),
    steps = listOf(
        StepCard(1, "Peek the Root", "The min (or max) is always at index 0 — reading it is O(1).", 0xFF10B981),
        StepCard(2, "Insert & Sift Up", "Add at the end, then swap it upward with its parent until the heap property holds.", 0xFF3B82F6),
        StepCard(3, "Extract & Sift Down", "Move the last element to the root, remove the old root, then swap it downward with its smaller child.", 0xFFF59E0B),
        StepCard(4, "Array Layout", "Parent/child links are computed from indices, so the whole structure lives in one flat array.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Peek min/max", "O(1)", "The extreme element is always at the root."),
        FormulaEntry("Insert / Extract", "O(log n)", "Sift up or down travels at most the height of the tree."),
        FormulaEntry("Build heap", "O(n)", "Heapifying an existing array is linear, not n log n."),
    ),
    notationKey = listOf(
        NotationEntry("root", "index 0 — the min (min-heap) or max (max-heap)"),
        NotationEntry("i", "a node's array index; children at 2i+1 and 2i+2"),
        NotationEntry("n", "number of elements"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Min-heap insert (sift up)",
            accentColor = 0xFF6366F1,
            code = """
                class MinHeap {
                    private val a = mutableListOf<Int>()

                    fun push(x: Int) {
                        a.add(x)
                        var i = a.size - 1
                        while (i > 0) {
                            val parent = (i - 1) / 2
                            if (a[parent] <= a[i]) break
                            a[parent] = a[i].also { a[i] = a[parent] }
                            i = parent
                        }
                    }

                    fun peek(): Int? = a.firstOrNull()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Priority Queues", "A heap is the standard backing for a priority queue — always serve the highest-priority item next."),
        ApplicationCard("map", 0xFF3B82F6, "Dijkstra & A*", "Shortest-path search pulls the closest unvisited node from a min-heap each step."),
        ApplicationCard("trend", 0xFFF59E0B, "Top-K & Streaming Medians", "A size-k heap tracks the k largest elements of a stream without sorting everything."),
    ),
    takeaways = listOf(
        "A heap gives O(1) access to the min or max and O(log n) insert/extract — perfect for priority queues.",
        "Completeness lets a heap live in a flat array with index arithmetic instead of node pointers.",
        "Building a heap from an array is O(n); heapsort then extracts in O(n log n).",
        "A heap orders only along root-to-leaf paths — siblings are unordered, so it isn't a sorted structure.",
    ),
    crossLinks = listOf(
        CrossLink("heap_sort", "Heap Sort"),
        CrossLink("priority_queue_adt", "Priority Queue"),
    ),
)
