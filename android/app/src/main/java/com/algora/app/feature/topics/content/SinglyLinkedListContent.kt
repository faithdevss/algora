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

internal val singlyLinkedListContent = TopicContent(
    topicId = "singly_linked_list",
    figure = Figure(
        caption = "No index arithmetic: reaching the k-th node means walking k links, so random access is " +
            "O(n). What it buys is O(1) splicing — given the node before, inserting or deleting is two " +
            "pointer writes and no shifting, which an array cannot do at any position but the end.",
        shape = FigureShape.Strip(
            cells = listOf("A →", "B →", "C →", "D →", "∅"),
            bands = listOf(
                FigureBand(0, 0, "head"),
                FigureBand(4, 4, "null", FigureTone.Muted),
            ),
            pointers = listOf(FigurePointer(1, "prev"), FigurePointer(2, "splice here")),
        ),
    ),
    whatIsIt = listOf(
        "A singly linked list is a chain of nodes where each node holds a value and a pointer to the next node — there's no requirement that nodes sit next to each other in memory.",
        "Think of it like a scavenger hunt: each clue tells you where to find the next one, but you can't jump ahead without following the chain.",
    ),
    steps = listOf(
        StepCard(1, "Node Structure", "Each node bundles a value with a reference to the next node — the last node's next is null.", 0xFF7C3AED),
        StepCard(2, "Head Pointer", "The list only tracks its head; every other node is reached by following next pointers from there.", 0xFF3B82F6),
        StepCard(3, "O(1) Insert at Head", "Inserting at the front just points the new node at the old head — no shifting, unlike an array.", 0xFFF59E0B),
        StepCard(4, "O(n) Access", "There's no index math — reaching the k-th node means walking k pointers from the head.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Insert / Delete at head", "O(1)", "Just repoint the head — no shifting needed."),
        FormulaEntry("Insert / Delete at tail", "O(n)*", "*O(1) if a tail pointer is maintained; O(n) to find it otherwise."),
        FormulaEntry("Access / Search by value", "O(n)", "Must walk from the head — no direct addressing."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of nodes in the list"),
        NotationEntry("head", "reference to the first node, or null if empty"),
        NotationEntry("next", "each node's pointer to its successor"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Singly Linked List — core operations",
            accentColor = 0xFF6366F1,
            code = """
                class Node(var value: Int, var next: Node? = null)

                class SinglyLinkedList {
                    private var head: Node? = null

                    fun insertHead(value: Int) {
                        head = Node(value, next = head)
                    }

                    fun insertAt(index: Int, value: Int) {
                        if (index == 0) return insertHead(value)
                        val prev = nodeAt(index - 1) ?: return
                        prev.next = Node(value, next = prev.next)
                    }

                    fun deleteAt(index: Int) {
                        if (index == 0) { head = head?.next; return }
                        val prev = nodeAt(index - 1) ?: return
                        prev.next = prev.next?.next
                    }

                    fun search(value: Int): Int {
                        var node = head
                        var index = 0
                        while (node != null) {
                            if (node.value == value) return index
                            node = node.next
                            index++
                        }
                        return -1
                    }

                    private fun nodeAt(index: Int): Node? {
                        var node = head
                        repeat(index) { node = node?.next }
                        return node
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.LinkedListVisualizer,
    applications = listOf(
        ApplicationCard("link", 0xFF7C3AED, "Undo History", "Each edit points back to the previous state, letting you walk backward without an array."),
        ApplicationCard("history", 0xFF3B82F6, "Music/Playlist Queues", "Songs chain to the next track — inserting or removing mid-queue is O(1) once you're there."),
        ApplicationCard("chip", 0xFFF59E0B, "Memory-Constrained Allocation", "Nodes can live anywhere in memory, so a linked list doesn't need one big contiguous block."),
    ),
    takeaways = listOf(
        "A linked list trades O(1) indexed access for O(1) insert/delete at the head — the opposite trade-off from an array.",
        "There is no formula for a node's address — every access means walking pointers from the head.",
        "Maintaining a tail pointer turns tail-append from O(n) into O(1).",
        "Because nodes aren't contiguous, linked lists are less CPU-cache-friendly than arrays for sequential scans.",
    ),
)
