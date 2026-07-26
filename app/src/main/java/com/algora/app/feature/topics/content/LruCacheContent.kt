package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lruCacheContent = TopicContent(
    topicId = "lru_cache",
    whatIsIt = listOf(
        "An LRU (least-recently-used) cache holds a fixed number of entries and, when full, evicts the one that hasn't been touched for the longest time.",
        "The classic design pairs a hash map with a doubly linked list, giving O(1) get and put: the map finds a node instantly, the list tracks recency order.",
    ),
    steps = listOf(
        StepCard(1, "Map + Linked List", "A hash map keys to list nodes; the list orders nodes from most- to least-recently used.", 0xFF10B981),
        StepCard(2, "Get Promotes", "On a hit, move that node to the front (most recent) and return its value.", 0xFF3B82F6),
        StepCard(3, "Put Inserts", "Add or update at the front; the map now points at the new node.", 0xFFF59E0B),
        StepCard(4, "Evict the Tail", "If over capacity, remove the list's tail node and its map entry — the least recently used.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("get / put", "O(1)", "Map lookup plus constant-time list splicing."),
        FormulaEntry("Space", "O(capacity)", "One node and one map entry per cached item."),
        FormulaEntry("Eviction", "least recently used", "The tail is always the next to go."),
    ),
    notationKey = listOf(
        NotationEntry("capacity", "maximum entries before eviction"),
        NotationEntry("head / tail", "most- and least-recently-used ends"),
        NotationEntry("hit / miss", "key found in / absent from the cache"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LRU cache with LinkedHashMap",
            accentColor = 0xFF6366F1,
            code = """
                class LRUCache<K, V>(private val capacity: Int) :
                    LinkedHashMap<K, V>(capacity, 0.75f, /* accessOrder = */ true) {

                    // Evict the eldest (least recently accessed) entry when over capacity.
                    override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean =
                        size > capacity
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.HashingVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "CPU & Page Caches", "Hardware caches and OS page replacement approximate LRU to keep hot data resident."),
        ApplicationCard("globe", 0xFF3B82F6, "Web & App Caches", "Memcached-style layers and browser caches evict stale entries by recency."),
        ApplicationCard("chart", 0xFFF59E0B, "Memoization Bounds", "An LRU wrapper caps memoized results so memory stays bounded."),
    ),
    takeaways = listOf(
        "An LRU cache evicts the least recently used entry when it hits capacity.",
        "Hash map + doubly linked list gives O(1) get and put.",
        "Accessing an entry promotes it to most-recent; the tail is always next to evict.",
        "LinkedHashMap's access-order mode implements it in a few lines on the JVM.",
    ),
    crossLinks = listOf(
        CrossLink("doubly_linked_list", "Doubly Linked List"),
        CrossLink("hash_table", "Hash Table / Hash Map"),
    ),
)
