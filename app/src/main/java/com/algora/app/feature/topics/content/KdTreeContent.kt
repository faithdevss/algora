package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kdTreeContent = TopicContent(
    topicId = "kd_tree",
    whatIsIt = listOf(
        "Space-partitioning trees — k-d trees, quad trees, and octrees — recursively split multi-dimensional space so nearest-neighbor and range queries skip most of the data.",
        "A k-d tree cycles through the dimensions, splitting on one axis at each level; quad trees (2D) and octrees (3D) instead split each region into 4 or 8 equal quadrants.",
    ),
    steps = listOf(
        StepCard(1, "Split on an Axis", "At depth d, a k-d tree partitions points by their coordinate on axis d mod k.", 0xFF10B981),
        StepCard(2, "Build Recursively", "Choose the median along that axis as the node, then recurse on the two halves.", 0xFF3B82F6),
        StepCard(3, "Prune the Search", "For nearest-neighbor, descend toward the query, then only revisit a branch if it could hold something closer.", 0xFFF59E0B),
        StepCard(4, "Region Trees Differ", "Quad/octrees split space into fixed equal cells rather than by data medians.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Build", "O(n log n)", "Median splits over n points."),
        FormulaEntry("Query (low dim)", "O(log n) average", "Pruning skips most subtrees when k is small."),
        FormulaEntry("Curse of dimensionality", "→ O(n) high dim", "Pruning fails as k grows large."),
    ),
    notationKey = listOf(
        NotationEntry("k", "number of dimensions"),
        NotationEntry("splitting axis", "the coordinate divided at a given level"),
        NotationEntry("quad/octree", "4-way (2D) / 8-way (3D) region splits"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "k-d tree build",
            accentColor = 0xFF6366F1,
            code = """
                class KdNode(val point: DoubleArray, var left: KdNode? = null, var right: KdNode? = null)

                fun build(points: List<DoubleArray>, depth: Int = 0): KdNode? {
                    if (points.isEmpty()) return null
                    val axis = depth % points[0].size
                    val sorted = points.sortedBy { it[axis] }
                    val mid = sorted.size / 2
                    return KdNode(sorted[mid]).apply {
                        left = build(sorted.subList(0, mid), depth + 1)
                        right = build(sorted.subList(mid + 1, sorted.size), depth + 1)
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NotYetAvailable,
    applications = listOf(
        ApplicationCard("map", 0xFF10B981, "Nearest-Neighbor Search", "Find the closest points — map POIs, recommendations — without scanning everything."),
        ApplicationCard("game", 0xFF3B82F6, "Collision & Culling", "Quad/octrees partition game worlds for fast collision checks and frustum culling."),
        ApplicationCard("robot", 0xFFF59E0B, "Robotics & Vision", "Point-cloud registration and motion planning query spatial neighbors constantly."),
    ),
    takeaways = listOf(
        "Space-partitioning trees prune multi-dimensional search to near-logarithmic in low dimensions.",
        "k-d trees split on a rotating axis; quad/octrees split space into equal cells.",
        "Nearest-neighbor pruning skips branches that can't beat the current best.",
        "High dimensionality breaks the pruning — use approximate methods (LSH) instead.",
    ),
    crossLinks = listOf(
        CrossLink("closest_pair_of_points", "Closest Pair of Points"),
        CrossLink("binary_search_tree", "Binary Search Tree"),
        // DSA ↔ AI bridge: k-d trees are what make k-NN's nearest-neighbour queries fast.
        CrossLink("knn", "k-Nearest Neighbors"),
    ),
)
