package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val setAdtContent = TopicContent(
    topicId = "set_adt",
    whatIsIt = listOf(
        "A Set is an abstract data type holding a collection of unique elements with no duplicates, defined by membership and the classic set operations.",
        "Its two common implementations differ in ordering: a hash set gives O(1) average membership but no order, while a tree set keeps elements sorted at O(log n).",
    ),
    steps = listOf(
        StepCard(1, "Uniqueness", "Adding an element already present is a no-op — a set never holds duplicates.", 0xFF10B981),
        StepCard(2, "Membership Test", "The core query is 'is x in the set?', ideally in O(1) or O(log n).", 0xFF3B82F6),
        StepCard(3, "Set Algebra", "Union, intersection, and difference combine two sets element-wise.", 0xFFF59E0B),
        StepCard(4, "Ordered or Not", "Hash sets are unordered; tree sets maintain sorted iteration and range queries.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Hash set ops", "O(1) average", "add / remove / contains via hashing."),
        FormulaEntry("Tree set ops", "O(log n)", "Sorted order and range queries."),
        FormulaEntry("Union / intersection", "O(n)", "Linear in the combined size."),
    ),
    notationKey = listOf(
        NotationEntry("membership", "the x ∈ S test"),
        NotationEntry("∪ / ∩ / −", "union, intersection, difference"),
        NotationEntry("hash vs tree", "unordered O(1) vs ordered O(log n)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Set operations",
            accentColor = 0xFF6366F1,
            code = """
                val a = hashSetOf(1, 2, 3, 4)
                val b = hashSetOf(3, 4, 5, 6)

                val union = a union b          // {1,2,3,4,5,6}
                val inter = a intersect b      // {3,4}
                val diff  = a subtract b       // {1,2}

                val hasThree = 3 in a          // O(1) average membership
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.HashingVisualizer,
    applications = listOf(
        ApplicationCard("check", 0xFF10B981, "Deduplication", "Collecting unique visitors, tags, or IDs is a set insertion per element."),
        ApplicationCard("search", 0xFF3B82F6, "Fast Membership", "'Have I seen this?' checks — visited nodes, seen tokens — use a hash set."),
        ApplicationCard("chip", 0xFFF59E0B, "Set Algebra in Queries", "Filtering by union/intersection of tag sets powers search and permission systems."),
    ),
    takeaways = listOf(
        "A Set ADT stores unique elements and centers on fast membership.",
        "Hash sets give O(1) average ops but no order; tree sets give O(log n) with sorted order.",
        "Union, intersection, and difference express set algebra directly.",
        "Choose a tree set only when you need ordering or range queries; otherwise a hash set.",
    ),
    crossLinks = listOf(
        CrossLink("hash_table", "Hash Table / Hash Map"),
        CrossLink("binary_search_tree", "Binary Search Tree"),
    ),
)
