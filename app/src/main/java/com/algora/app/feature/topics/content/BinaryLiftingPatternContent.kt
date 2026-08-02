package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Precompute power-of-two jumps once, then answer ancestor and
// successor queries in O(log n) each.
internal val binaryLiftingPatternContent = TopicContent(
    topicId = "binary_lifting_pattern",
    whatIsIt = listOf(
        "Binary lifting precomputes, for every node, the ancestor 1, 2, 4, 8 … steps above it. Any k-th ancestor is then reached by following the set bits of k — at most log n jumps instead of k.",
        "The same table answers lowest common ancestor: lift the deeper node to its partner's depth, then jump both upward by the largest powers that keep them apart. Whatever remains one step up is the LCA.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Many ancestor, LCA, or path queries on a static tree — or repeated \"apply this successor function k times\" on a functional graph.", 0xFFF59E0B),
        StepCard(2, "Build the Jump Table", "up[0][v] is the parent. up[j][v] = up[j-1][up[j-1][v]] — a 2^j jump is two 2^(j-1) jumps.", 0xFF3B82F6),
        StepCard(3, "Decompose k in Binary", "For the k-th ancestor, take a 2^j jump for each set bit j of k. Missing ancestors resolve to a sentinel root.", 0xFFEF4444),
        StepCard(4, "Lift Both for LCA", "Equalise depths, then for j high to low, jump both nodes when their 2^j ancestors differ. The answer is one step above where they stop.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Build", "O(n log n)", "log n levels × n nodes, filled level by level."),
        FormulaEntry("Query", "O(log n)", "One jump per set bit of k, or per level during an LCA descent."),
        FormulaEntry("Distance", "depth[u] + depth[v] - 2 · depth[lca]", "Path length in a tree, straight from the LCA."),
    ),
    notationKey = listOf(
        NotationEntry("up[j][v]", "the 2^j-th ancestor of v"),
        NotationEntry("LOG", "ceil(log2(n)) — the number of jump levels"),
        NotationEntry("depth[v]", "distance from the root to v"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Jump table, k-th ancestor and LCA (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def build(parent, n, LOG):                 # parent[root] = root
                    up = [parent[:]] + [[0] * n for _ in range(LOG - 1)]
                    for j in range(1, LOG):
                        for v in range(n):
                            up[j][v] = up[j - 1][up[j - 1][v]]
                    return up

                def kth_ancestor(up, v, k, LOG):
                    for j in range(LOG):
                        if k >> j & 1:                     # one jump per set bit
                            v = up[j][v]
                    return v

                def lca(up, depth, u, v, LOG):
                    if depth[u] < depth[v]:
                        u, v = v, u
                    u = kth_ancestor(up, u, depth[u] - depth[v], LOG)   # equalise depth
                    if u == v:
                        return u
                    for j in reversed(range(LOG)):
                        if up[j][u] != up[j][v]:           # jump while still apart
                            u, v = up[j][u], up[j][v]
                    return up[0][u]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("tree", 0xFF3B82F6, "Hierarchy Queries", "Common manager, shared category or nearest ancestor in a taxonomy."),
        ApplicationCard("map", 0xFF10B981, "Path Aggregates", "Max or sum along a tree path by lifting the aggregate alongside the ancestor."),
        ApplicationCard("history", 0xFF8B5CF6, "Functional Graphs", "The k-th successor in a next-pointer chain, cycles included."),
    ),
    takeaways = listOf(
        "Build once in O(n log n), then every query is O(log n) — worth it from a few hundred queries up.",
        "The recurrence is the whole idea: a 2^j jump is two 2^(j-1) jumps.",
        "For LCA, jump only while the ancestors differ; the answer is one step above the stopping point.",
        "A self-parent root makes over-jumping harmless and removes the bounds checks.",
    ),
    crossLinks = listOf(
        CrossLink("lca", "Lowest Common Ancestor (Algorithms)"),
        CrossLink("tree_dp_pattern", "Tree DP Pattern"),
        CrossLink("sparse_table", "Sparse Table (Data Structures)"),
    ),
)
