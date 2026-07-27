package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fenwickTreeContent = TopicContent(
    topicId = "fenwick_tree",
    whatIsIt = listOf(
        "A Fenwick tree (binary indexed tree, or BIT) supports prefix-sum queries and point updates in O(log n) using a single array and clever bit manipulation.",
        "It's a leaner alternative to a segment tree for cumulative sums: less memory, tiny code, and blazing constants — at the cost of only handling invertible operations like addition.",
    ),
    steps = listOf(
        StepCard(1, "Index by Low Bit", "Each position i is responsible for a range of length equal to its lowest set bit (i & −i).", 0xFF10B981),
        StepCard(2, "Query a Prefix", "Sum a prefix by repeatedly subtracting the low bit: i −= i & −i until zero.", 0xFF3B82F6),
        StepCard(3, "Update a Point", "Add a delta and propagate upward: i += i & −i until past the array.", 0xFFF59E0B),
        StepCard(4, "Range = Two Prefixes", "sum(l..r) = prefix(r) − prefix(l−1).", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Query / update", "O(log n)", "Each jumps over O(log n) responsible ranges."),
        FormulaEntry("Low-bit trick", "i & −i", "Isolates the lowest set bit — the range size."),
        FormulaEntry("Space", "O(n)", "A single array, roughly half a segment tree's memory."),
    ),
    notationKey = listOf(
        NotationEntry("i & −i", "the lowest set bit of index i"),
        NotationEntry("prefix(i)", "cumulative sum of the first i elements"),
        NotationEntry("n", "number of elements"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fenwick tree (BIT)",
            accentColor = 0xFF6366F1,
            code = """
                class Fenwick(n: Int) {
                    private val tree = IntArray(n + 1)   // 1-indexed

                    fun update(i: Int, delta: Int) {
                        var x = i
                        while (x < tree.size) { tree[x] += delta; x += x and -x }
                    }

                    fun prefix(i: Int): Int {
                        var x = i; var sum = 0
                        while (x > 0) { sum += tree[x]; x -= x and -x }
                        return sum
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Cumulative Frequencies", "Running counts and rank queries over a changing multiset are the BIT's classic use."),
        ApplicationCard("trend", 0xFF3B82F6, "Counting Inversions", "Merge-sort-style inversion counting is a few lines with a Fenwick tree."),
        ApplicationCard("chip", 0xFFF59E0B, "Order Statistics", "A BIT over value counts answers 'k-th smallest' with binary lifting."),
    ),
    takeaways = listOf(
        "A Fenwick tree does prefix-sum queries and point updates in O(log n).",
        "The i & −i low-bit trick is what makes its jumps work in one array.",
        "It uses less memory and code than a segment tree but only handles invertible ops.",
        "Two prefix queries give any range sum; pair with a difference array for range updates.",
    ),
    crossLinks = listOf(
        CrossLink("segment_tree", "Segment Tree"),
        CrossLink("difference_array", "Difference Array"),
    ),
)
