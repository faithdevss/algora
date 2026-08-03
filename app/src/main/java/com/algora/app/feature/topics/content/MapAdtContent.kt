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

internal val mapAdtContent = TopicContent(
    topicId = "map_adt",
    figure = Figure(
        caption = "Same trade one level up: a Map is key → value with unique keys, and the backing " +
            "structure decides what else you get. Hash for speed, tree for order, insertion-ordered hash " +
            "when iteration has to be predictable — the interface is identical in all three.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("O(1)*", "O(log n)", "O(1)*"),
                listOf("O(1)*", "O(log n)", "O(1)*"),
                listOf("none", "sorted", "insertion"),
            ),
            rowHeaders = listOf("put", "get", "iteration"),
            colHeaders = listOf("hash map", "tree map", "linked hash"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(1, 0, FigureTone.Accent),
                FigureCell(2, 1, FigureTone.Primary),
                FigureCell(2, 2, FigureTone.Primary),
                FigureCell(2, 0, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A Map (dictionary, associative array) is an abstract data type storing key-value pairs, letting you look up, insert, or remove a value by its unique key.",
        "Like Set, it comes in a hash-backed flavor (O(1) average, unordered) and a tree-backed flavor (O(log n), keys kept sorted).",
    ),
    steps = listOf(
        StepCard(1, "Unique Keys", "Each key maps to exactly one value; inserting an existing key overwrites it.", 0xFF10B981),
        StepCard(2, "Lookup by Key", "get(key) returns its value (or absent) — the map's defining operation.", 0xFF3B82F6),
        StepCard(3, "Insert & Delete", "put(key, value) and remove(key) maintain the association.", 0xFFF59E0B),
        StepCard(4, "Ordered or Not", "Hash maps iterate in no fixed order; tree maps iterate keys in sorted order.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Hash map ops", "O(1) average", "get / put / remove via hashing."),
        FormulaEntry("Tree map ops", "O(log n)", "Sorted keys, range and successor queries."),
        FormulaEntry("Worst case (hash)", "O(n)", "Pathological collisions degrade to a scan."),
    ),
    notationKey = listOf(
        NotationEntry("key → value", "the stored association"),
        NotationEntry("get / put / remove", "core map operations"),
        NotationEntry("hash vs tree", "unordered O(1) vs ordered O(log n)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The Map contract",
            accentColor = 0xFF6366F1,
            code = """
                interface Map<K, V> {
                    fun get(key: K): V?          // value or null if absent
                    fun put(key: K, value: V)    // insert or overwrite
                    fun remove(key: K): V?
                    fun containsKey(key: K): Boolean
                    val size: Int
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.HashingVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Everywhere in Code", "Config lookups, caches, counters, and indexes are all maps under the hood."),
        ApplicationCard("search", 0xFF3B82F6, "Frequency & Grouping", "Counting occurrences and grouping records by key are one-line map operations."),
        ApplicationCard("book", 0xFFF59E0B, "Sorted Directories", "Tree maps back ordered indexes where you iterate or range-scan by key."),
    ),
    takeaways = listOf(
        "A Map ADT associates unique keys with values and centers on lookup by key.",
        "Hash maps give O(1) average access unordered; tree maps give O(log n) with sorted keys.",
        "It's the most-used data structure in practice — the default for lookups and counting.",
        "A Set is just a Map without values; a Map is a Set of keys carrying payloads.",
    ),
    crossLinks = listOf(
        CrossLink("hash_table", "Hash Table / Hash Map"),
        CrossLink("set_adt", "Set"),
    ),
)
