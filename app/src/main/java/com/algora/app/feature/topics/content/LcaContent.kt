package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lcaContent = TopicContent(
    topicId = "lca",
    figure = Figure(
        caption = "The two marked nodes are the query and A is their answer — the point where their two " +
            "root-paths merge, and the deepest node that still has both below it. One query can just " +
            "walk both paths up in O(h) and that is the right answer for a handful of queries. Binary " +
            "lifting is for thousands: precompute up[k][v], the 2ᵏ-th ancestor, as two 2ᵏ⁻¹ hops, " +
            "then level the deeper node by the binary decomposition of the depth gap and jump both nodes " +
            "from the largest power downward — but only where their ancestors *differ*. That rule is " +
            "what keeps the jumps from overshooting: the pair lands immediately below the meeting point " +
            "every time, so the answer is one parent step up. O(n log n) to build, O(log n) per query.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("R", null, FigureTone.Primary),
                FigureNode("A", 0, FigureTone.Accent),
                FigureNode("C", 1, FigureTone.Primary),
                FigureNode("E", 2, FigureTone.Warn),
                FigureNode("F", 2),
                FigureNode("D", 1, FigureTone.Warn),
                FigureNode("B", 0),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The lowest common ancestor of two nodes is the deepest node that has both of them as descendants — the point where their two root-paths merge.",
        "A single query can be answered by walking both paths in O(h), but when thousands of queries hit the same tree it pays to preprocess. Binary lifting stores each node's 2^k-th ancestor, letting a query jump in powers of two and land on the LCA in O(log n).",
    ),
    steps = listOf(
        StepCard(1, "Root the Tree and Record Depth", "One DFS assigns every node a depth and its immediate parent.", 0xFF3B82F6),
        StepCard(2, "Build the Jump Table", "up[k][v] = up[k−1][ up[k−1][v] ] — the 2^k-th ancestor is two 2^(k−1) hops.", 0xFF10B981),
        StepCard(3, "Level the Two Nodes", "Lift the deeper node by the binary decomposition of the depth difference.", 0xFFF59E0B),
        StepCard(4, "Descend Together", "From the largest power down, jump both nodes whenever their ancestors differ — never overshooting past the LCA.", 0xFF8B5CF6),
        StepCard(5, "Answer Is One Step Up", "The nodes now sit just below the meeting point, so the LCA is their parent.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Jump table", "up[k][v] = up[k−1][up[k−1][v]]", "Doubling recurrence, filled level by level."),
        FormulaEntry("Preprocess", "O(n log n) time and space", "log n ancestors stored per node."),
        FormulaEntry("Query", "O(log n)", "At most one jump per bit of the depth."),
        FormulaEntry("Distance", "dist(u,v) = depth[u] + depth[v] − 2·depth[lca]", "The classic use of an LCA query."),
    ),
    notationKey = listOf(
        NotationEntry("depth[v]", "edges from the root down to v"),
        NotationEntry("up[k][v]", "the 2^k-th ancestor of v"),
        NotationEntry("LOG", "⌈log₂ n⌉ — how many doubling levels are needed"),
        NotationEntry("h", "tree height, the cost of the naive walk"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Binary lifting LCA",
            accentColor = 0xFF6366F1,
            code = """
                class Lca(private val n: Int, adj: Array<List<Int>>, root: Int = 0) {
                    private val log = maxOf(1, 32 - Integer.numberOfLeadingZeros(n))
                    private val up = Array(log) { IntArray(n) { root } }
                    private val depth = IntArray(n)

                    init {
                        // Iterative DFS to avoid deep recursion on path-like trees.
                        val stack = ArrayDeque<Int>().apply { add(root) }
                        val seen = BooleanArray(n).also { it[root] = true }
                        while (stack.isNotEmpty()) {
                            val u = stack.removeLast()
                            for (v in adj[u]) if (!seen[v]) {
                                seen[v] = true
                                depth[v] = depth[u] + 1
                                up[0][v] = u
                                stack += v
                            }
                        }
                        for (k in 1 until log) {
                            for (v in 0 until n) up[k][v] = up[k - 1][up[k - 1][v]]
                        }
                    }

                    fun query(a: Int, b: Int): Int {
                        var u = a
                        var v = b
                        if (depth[u] < depth[v]) { val t = u; u = v; v = t }

                        var diff = depth[u] - depth[v]
                        var k = 0
                        while (diff > 0) {                       // lift the deeper node
                            if (diff and 1 == 1) u = up[k][u]
                            diff = diff shr 1
                            k++
                        }
                        if (u == v) return u                     // one was an ancestor of the other

                        for (j in log - 1 downTo 0) {            // descend without overshooting
                            if (up[j][u] != up[j][v]) { u = up[j][u]; v = up[j][v] }
                        }
                        return up[0][u]
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("share", 0xFF3B82F6, "Version Control Merges", "Git's merge base is the LCA of two commits in the history DAG."),
        ApplicationCard("browser", 0xFF10B981, "DOM & Scene Graphs", "The nearest common container of two elements decides where an event handler or transform belongs."),
        ApplicationCard("map", 0xFFF59E0B, "Routing on Trees", "Path queries in tree-shaped networks decompose into two upward walks meeting at the LCA."),
    ),
    takeaways = listOf(
        "LCA turns a path question into an ancestor question — distance, path sums and path maxima all reduce to it.",
        "Binary lifting trades O(n log n) preprocessing for O(log n) queries; the naive walk is fine for a handful of queries.",
        "Lifting by the bits of the depth difference is what makes the two nodes comparable.",
        "Descending only when ancestors differ guarantees you stop just below the LCA, never above it.",
    ),
    crossLinks = listOf(
        CrossLink("tree", "Tree"),
        CrossLink("sparse_table", "Sparse Table"),
        CrossLink("tree_dp", "Tree DP"),
    ),
)
