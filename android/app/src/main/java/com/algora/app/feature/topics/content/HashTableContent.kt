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

internal val hashTableContent = TopicContent(
    topicId = "hash_table",
    figure = Figure(
        caption = "The hash turns a key into a bucket index, so lookup skips the search entirely — but " +
            "two keys can land in the same bucket, and then the chain has to be walked. Average O(1) is " +
            "really \"O(1 + chain length)\", which is why the load factor is watched and the table resized.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("—", "—"),
                listOf("cat", "—"),
                listOf("—", "—"),
                listOf("dog", "owl"),
                listOf("fish", "—"),
            ),
            rowHeaders = listOf("0", "1", "2", "3", "4"),
            colHeaders = listOf("first", "chained"),
            marks = listOf(
                FigureCell(3, 0, FigureTone.Warn),
                FigureCell(3, 1, FigureTone.Warn),
                FigureCell(1, 0, FigureTone.Primary),
                FigureCell(4, 0, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A hash table stores key-value pairs and finds any key in expected O(1) time by turning the key into an array index with a hash function.",
        "Because two keys can hash to the same slot (a collision), a hash table also needs a strategy — chaining or open addressing — to keep those keys apart.",
    ),
    steps = listOf(
        StepCard(1, "Hash the Key", "Run the key through a hash function to get a number, then mod by the bucket count to pick a slot.", 0xFF10B981),
        StepCard(2, "Store in a Bucket", "Place the key-value pair in that slot's bucket.", 0xFF3B82F6),
        StepCard(3, "Resolve Collisions", "When two keys land in the same slot, chain them in a list (or probe for the next open slot).", 0xFFF59E0B),
        StepCard(4, "Resize on Load", "When the table fills past its load factor, allocate a bigger array and rehash every entry.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Insert / Lookup / Delete", "O(1) average", "A good hash spreads keys evenly across buckets."),
        FormulaEntry("Worst case", "O(n)", "If every key collides into one bucket, operations degrade to a linear scan."),
        FormulaEntry("Load factor", "α = n / buckets", "Kept below a threshold (often 0.75) by resizing to hold O(1) behavior."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of stored entries"),
        NotationEntry("bucket", "one slot of the backing array"),
        NotationEntry("α", "load factor — entries per bucket"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Separate chaining",
            accentColor = 0xFF6366F1,
            code = """
                class HashMap<K, V>(private val buckets: Int = 16) {
                    private val table = Array(buckets) { mutableListOf<Pair<K, V>>() }

                    private fun slot(key: K) = (key.hashCode() and 0x7FFFFFFF) % buckets

                    fun put(key: K, value: V) {
                        val bucket = table[slot(key)]
                        val i = bucket.indexOfFirst { it.first == key }
                        if (i >= 0) bucket[i] = key to value else bucket.add(key to value)
                    }

                    fun get(key: K): V? =
                        table[slot(key)].firstOrNull { it.first == key }?.second
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.HashingVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Language Dictionaries/Maps", "The built-in map, dict, and object types in most languages are hash tables under the hood."),
        ApplicationCard("search", 0xFF3B82F6, "Database Indexing & Caches", "Hash indexes and in-memory caches like memcached use hashing for near-instant key lookup."),
        ApplicationCard("check", 0xFFF59E0B, "Deduplication", "Membership sets built on hashing test 'have I seen this before?' in O(1)."),
    ),
    takeaways = listOf(
        "Hashing converts a key into an array index, giving O(1) average insert, lookup, and delete.",
        "Collisions are unavoidable; chaining or open addressing keeps colliding keys separated.",
        "A bad hash or a full table drags performance toward O(n) — load factor and resizing matter.",
        "Hash tables trade ordering for speed: iterate a tree if you need sorted keys, a hash table if you need fast lookup.",
    ),
    crossLinks = listOf(
        CrossLink("array", "Array"),
        CrossLink("binary_search_tree", "Binary Search Tree"),
    ),
)
