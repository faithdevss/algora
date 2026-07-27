package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val doublyLinkedListContent = TopicContent(
    topicId = "doubly_linked_list",
    whatIsIt = listOf(
        "A doubly linked list chains nodes together where each node stores a value plus two pointers: one to the next node and one to the previous node.",
        "The extra back-pointer lets you walk the list in both directions and remove a node in O(1) once you hold it — the key advantage over a singly linked list.",
    ),
    steps = listOf(
        StepCard(1, "Node with Two Links", "Each node holds prev and next references alongside its value.", 0xFF7C3AED),
        StepCard(2, "Insert", "Splice a node in by rewiring four pointers: the neighbors' next/prev and the new node's own two.", 0xFF3B82F6),
        StepCard(3, "Delete in O(1)", "Given a node, connect its prev directly to its next — no scan from the head needed.", 0xFFF59E0B),
        StepCard(4, "Bidirectional Traversal", "Walk forward from head via next, or backward from tail via prev.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Insert / Delete at a known node", "O(1)", "Only a fixed number of pointers are rewired."),
        FormulaEntry("Access / Search", "O(n)", "No indexing — you follow links from an end."),
        FormulaEntry("Extra space per node", "O(1)", "One additional pointer versus a singly linked list."),
    ),
    notationKey = listOf(
        NotationEntry("head / tail", "the first and last nodes"),
        NotationEntry("prev / next", "a node's backward and forward pointers"),
        NotationEntry("n", "number of nodes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Node and O(1) removal",
            accentColor = 0xFF6366F1,
            code = """
                class Node<T>(val value: T) {
                    var prev: Node<T>? = null
                    var next: Node<T>? = null
                }

                // Detach a node in constant time using its own back/forward links.
                fun <T> remove(node: Node<T>) {
                    node.prev?.next = node.next
                    node.next?.prev = node.prev
                    node.prev = null
                    node.next = null
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.LinkedStructurePlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF7C3AED, "Browser & Editor History", "Back/forward navigation walks a doubly linked list of visited states in either direction."),
        ApplicationCard("chip", 0xFF3B82F6, "LRU Cache", "An LRU cache pairs a hash map with a doubly linked list to move used entries and evict the tail in O(1)."),
        ApplicationCard("music", 0xFFF59E0B, "Playlists", "Media players jump to previous or next tracks by following the two links of the current node."),
    ),
    takeaways = listOf(
        "Two pointers per node buy O(1) deletion at a known node and traversal in both directions.",
        "The cost is one extra pointer of memory per node plus more bookkeeping on every insert/delete.",
        "Access and search are still O(n) — links give no random indexing.",
        "The hash-map + doubly-linked-list combo is the classic O(1) LRU cache design.",
    ),
    crossLinks = listOf(
        CrossLink("singly_linked_list", "Singly Linked List"),
        CrossLink("lru_cache", "LRU Cache"),
    ),
)
