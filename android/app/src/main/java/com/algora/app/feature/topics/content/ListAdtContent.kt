package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val listAdtContent = TopicContent(
    topicId = "list_adt",
    figure = Figure(
        caption = "The List *interface* — ordered, indexable, duplicates allowed — says nothing about " +
            "cost. Two implementations answer the same calls with opposite performance, so the question " +
            "\"which list\" is really \"which operation runs in the loop\".",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("O(1)", "O(n)"),
                listOf("O(n)", "O(1)"),
                listOf("O(1)*", "O(1)"),
                listOf("O(n)", "O(1)†"),
            ),
            rowHeaders = listOf("get(i)", "add front", "append", "remove"),
            colHeaders = listOf("array list", "linked list"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(1, 1, FigureTone.Accent),
                FigureCell(3, 1, FigureTone.Accent),
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 0, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A List is an abstract data type: an ordered collection with positional access, defined by what operations it supports — not by how it's stored.",
        "The same List interface can be backed by a dynamic array or a linked list, and that hidden choice sets the performance profile of every operation.",
    ),
    steps = listOf(
        StepCard(1, "Interface, Not Implementation", "A List promises get, set, add, remove, and size — the contract, independent of storage.", 0xFF10B981),
        StepCard(2, "Array-Backed", "A dynamic array gives O(1) indexing but O(n) inserts/removes in the middle.", 0xFF3B82F6),
        StepCard(3, "Linked-Backed", "A linked list gives O(1) splice at a known node but O(n) indexing.", 0xFFF59E0B),
        StepCard(4, "Pick by Access Pattern", "Choose the backing structure to match whether you index randomly or edit ends.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Array get/set", "O(1)", "Direct index; O(n) middle insert/remove."),
        FormulaEntry("Linked splice", "O(1) at a node", "But O(n) to reach an arbitrary index."),
        FormulaEntry("Amortized append", "O(1)", "Dynamic-array growth doubles capacity."),
    ),
    notationKey = listOf(
        NotationEntry("ADT", "abstract data type — behavior, not representation"),
        NotationEntry("positional access", "get/set by index"),
        NotationEntry("backing structure", "the concrete array or linked list underneath"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The List contract",
            accentColor = 0xFF6366F1,
            code = """
                // The ADT is the interface; array vs linked is an implementation detail.
                interface List<T> {
                    val size: Int
                    fun get(index: Int): T
                    fun set(index: Int, value: T)
                    fun add(value: T)
                    fun removeAt(index: Int): T
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Standard Collections", "ArrayList and LinkedList are two implementations of the same List ADT."),
        ApplicationCard("book", 0xFF3B82F6, "API Design", "Coding to the List interface lets you swap implementations without touching callers."),
        ApplicationCard("chart", 0xFFF59E0B, "Ordered Data", "Any ordered, index-addressable sequence — history, playlists, queues of work — is a List."),
    ),
    takeaways = listOf(
        "A List ADT specifies ordered, positional operations, not how they're stored.",
        "Array backing favors indexing; linked backing favors end/splice edits.",
        "Programming to the ADT lets the implementation change underneath.",
        "Match the backing structure to your dominant access pattern.",
    ),
    crossLinks = listOf(
        CrossLink("array", "Array"),
        CrossLink("singly_linked_list", "Singly Linked List"),
    ),
)
