package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val segmentTreeContent = TopicContent(
    topicId = "segment_tree",
    whatIsIt = listOf(
        "A segment tree stores aggregate information over array ranges in a binary tree, answering range queries and point/range updates in O(log n).",
        "Each node covers a contiguous segment: leaves are single elements, and each internal node combines its two children — a sum, min, max, or any associative operation.",
    ),
    steps = listOf(
        StepCard(1, "Build the Tree", "Recursively split the array in half; each node stores the combined value of its range.", 0xFF10B981),
        StepCard(2, "Query a Range", "Descend from the root, combining the nodes whose ranges fully lie inside the query.", 0xFF3B82F6),
        StepCard(3, "Update a Point", "Change a leaf, then walk back up recomputing each ancestor's combined value.", 0xFFF59E0B),
        StepCard(4, "Lazy Propagation", "For range updates, defer changes with lazy tags pushed down only when a node is next visited.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Query / update", "O(log n)", "Only O(log n) nodes are touched per operation."),
        FormulaEntry("Build", "O(n)", "Bottom-up over 2n−1 nodes."),
        FormulaEntry("Space", "O(n)", "About 2n–4n array slots for the tree."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of array elements"),
        NotationEntry("combine", "the associative merge (sum, min, max, gcd…)"),
        NotationEntry("lazy tag", "deferred range update stored at a node"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Segment tree — range sum query",
            accentColor = 0xFF6366F1,
            code = """
                // tree sized 2n; build and query for range sums.
                fun query(tree: IntArray, node: Int, lo: Int, hi: Int, l: Int, r: Int): Int {
                    if (r < lo || hi < l) return 0                 // no overlap
                    if (l <= lo && hi <= r) return tree[node]      // full overlap
                    val mid = (lo + hi) / 2
                    return query(tree, 2 * node, lo, mid, l, r) +
                           query(tree, 2 * node + 1, mid + 1, hi, l, r)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Range Statistics", "Live sums, minimums, or maximums over changing array ranges — the segment tree's core job."),
        ApplicationCard("game", 0xFF3B82F6, "Interval Problems", "Range assignment, coloring, and collision intervals use lazy-propagation segment trees."),
        ApplicationCard("chip", 0xFFF59E0B, "Competitive Programming", "A workhorse for problems mixing frequent range queries and updates."),
    ),
    takeaways = listOf(
        "A segment tree gives O(log n) range queries and updates for any associative operation.",
        "Lazy propagation extends it to efficient range updates, not just point updates.",
        "It's more flexible than a Fenwick tree (min/max/gcd), at higher memory and code cost.",
        "Build is O(n); every query or update touches only O(log n) nodes.",
    ),
    crossLinks = listOf(
        CrossLink("fenwick_tree", "Fenwick Tree (BIT)"),
        CrossLink("mos_algorithm", "Mo's Algorithm"),
    ),
)
