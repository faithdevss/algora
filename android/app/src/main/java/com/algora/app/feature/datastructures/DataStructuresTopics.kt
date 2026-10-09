package com.algora.app.feature.datastructures

import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.model.Topic

private val arrayTopic = Topic(
    id = "array",
    name = "Array",
    categoryId = DataStructuresCategories.core.id,
    tagline = "The Simplest Data Structure",
    description = "A collection of elements stored in contiguous memory, each reachable directly by index.",
    iconName = "DataArray",
    accentColor = 0xFF8B5CF6,
    difficulty = Difficulty.BEGINNER,
)

// categoryIconName/categoryAccent default a topic's chrome to its owning category (the mock's
// row UI never shows a per-topic icon — only section headers do). Bespoke icon/color is only
// worth setting for topics the mock itself specifies (Singly Linked List, Graph).
private fun topic(
    id: String,
    name: String,
    category: com.algora.app.core.data.model.Category,
    tagline: String,
    iconName: String = category.iconName,
    accentColor: Long = category.accentColor,
) = Topic(
    id = id,
    name = name,
    categoryId = category.id,
    tagline = tagline,
    description = tagline,
    iconName = iconName,
    accentColor = accentColor,
)

private val core = DataStructuresCategories.core
private val nonLinear = DataStructuresCategories.nonLinear
private val advanced = DataStructuresCategories.advanced
private val specialized = DataStructuresCategories.specialized
private val adt = DataStructuresCategories.adt

private val coreTopics = listOf(
    arrayTopic,
    topic("string", "String", core, "Sequences of characters, arrays with text-specific operations."),
    topic(
        "singly_linked_list", "Singly Linked List", core,
        "Nodes chained by next-pointers",
        iconName = "link", accentColor = 0xFF7C3AED,
    ),
    topic("doubly_linked_list", "Doubly Linked List", core, "Nodes chained both forward and backward."),
    topic("stack", "Stack", core, "Last-in, first-out access at one end."),
    topic("queue", "Queue", core, "First-in, first-out access at two ends."),
    topic("deque", "Deque (Double-Ended Queue)", core, "Push and pop at both ends in O(1)."),
    topic("hash_table", "Hash Table / Hash Map", core, "Key-value lookup backed by a hash function."),
)

private val nonLinearTopics = listOf(
    topic("tree", "Tree", nonLinear, "Hierarchical nodes with parent/child links."),
    topic("binary_search_tree", "Binary Search Tree", nonLinear, "Ordered binary tree for fast lookup, insert, delete."),
    topic("heap", "Heap (Min / Max)", nonLinear, "Complete binary tree keeping the smallest or largest at the root."),
    topic("trie", "Trie (Prefix Tree)", nonLinear, "Tree keyed by string prefixes, built for fast lookup."),
    topic(
        "graph", "Graph", nonLinear,
        "Nodes connected by edges, directed or undirected",
        iconName = "share", accentColor = 0xFF3B82F6,
    ),
)

private val advancedTopics = listOf(
    topic("segment_tree", "Segment Tree", advanced, "Range queries and updates in logarithmic time."),
    topic("fenwick_tree", "Fenwick Tree (BIT)", advanced, "Compact structure for prefix-sum queries and updates."),
    topic("disjoint_set", "Disjoint Set (Union-Find)", advanced, "Tracks a partition of elements into disjoint sets."),
    topic("avl_red_black_tree", "AVL / Red-Black Tree", advanced, "Self-balancing binary search trees."),
    topic("b_tree", "B-Tree / B+ Tree", advanced, "Balanced multi-way trees built for disk/database access."),
    topic("suffix_tree", "Suffix Tree / Suffix Array", advanced, "Indexes every suffix of a string for fast substring search."),
    topic("skip_list", "Skip List", advanced, "Layered linked lists giving probabilistic logarithmic search."),
    topic("sparse_table", "Sparse Table", advanced, "O(1) idempotent range queries on immutable data."),
)

private val specializedTopics = listOf(
    topic("bloom_filter", "Bloom Filter", specialized, "Space-efficient probabilistic set membership test."),
    topic("lru_cache", "LRU Cache", specialized, "Fixed-capacity cache that evicts the least recently used entry."),
    topic("kd_tree", "K-D Tree / Quad Tree / Octree", specialized, "Space-partitioning trees for multi-dimensional data."),
    topic("graph_variants", "Graph Variants", specialized, "Weighted, directed, and multigraph representations."),
)

private val adtTopics = listOf(
    topic("list_adt", "List", adt, "Abstract ordered collection with positional access."),
    topic("set_adt", "Set", adt, "Abstract collection of unique elements."),
    topic("map_adt", "Map", adt, "Abstract key-value association."),
    topic("priority_queue_adt", "Priority Queue", adt, "Abstract queue ordered by priority rather than arrival."),
)

object DataStructuresTopics {
    val topics: List<Topic> = coreTopics + nonLinearTopics + advancedTopics + specializedTopics + adtTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
