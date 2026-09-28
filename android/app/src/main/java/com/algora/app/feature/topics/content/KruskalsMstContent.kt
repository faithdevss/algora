package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kruskalsMstContent = TopicContent(
    topicId = "kruskals_mst",
    figure = Figure(
        caption = "Edges considered cheapest-first: 1, 2, 3, then 4 — which is rejected. Not for being " +
            "expensive, but because B and C already sit in the same component by then, so adding it " +
            "would close a cycle; that is the only test Kruskal ever performs, and a disjoint-set " +
            "structure answers it in near-constant time. Drawn here is the finished tree; mid-run it " +
            "would not look like a tree at all, because after 1 and 2 there are two disconnected " +
            "fragments and nothing joining them — Prim's never has that stage. The run stops at " +
            "V − 1 = 4 edges, so the edge weighted 6 is never " +
            "examined at all. Total weight 11, and the sort dominates: O(E log E).",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A", 0.12f, 0.22f, FigureTone.Primary),
                FigureGraphNode("B", 0.12f, 0.80f, FigureTone.Primary),
                FigureGraphNode("C", 0.50f, 0.50f, FigureTone.Primary),
                FigureGraphNode("D", 0.88f, 0.22f, FigureTone.Primary),
                FigureGraphNode("E", 0.88f, 0.80f, FigureTone.Primary),
            ),
            edges = listOf(
                FigureEdge(0, 1, "1", tone = FigureTone.Accent),
                FigureEdge(2, 3, "2", tone = FigureTone.Accent),
                FigureEdge(0, 2, "3", tone = FigureTone.Accent),
                FigureEdge(1, 2, "4", tone = FigureTone.Warn),
                FigureEdge(3, 4, "5", tone = FigureTone.Accent),
                FigureEdge(2, 4, "6"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Kruskal's algorithm builds a minimum spanning tree by repeatedly adding the cheapest edge that doesn't form a cycle, until every node is connected.",
        "It is edge-centric and greedy: sort all edges by weight, then grow a forest of trees that gradually merge into one, using a disjoint-set structure to detect cycles.",
    ),
    steps = listOf(
        StepCard(1, "Sort the Edges", "Order every edge by weight, cheapest first.", 0xFF10B981),
        StepCard(2, "Take the Cheapest", "Consider edges in that order, one at a time.", 0xFF3B82F6),
        StepCard(3, "Reject Cycles", "Add the edge only if its endpoints are in different components; skip it otherwise.", 0xFFF59E0B),
        StepCard(4, "Stop at V − 1 Edges", "Once the tree has V − 1 edges it spans all nodes and is minimal.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(E log E)", "Dominated by sorting the edges."),
        FormulaEntry("Cycle check", "union-find", "Near-constant per query with path compression + union by rank."),
        FormulaEntry("Tree size", "V − 1 edges", "A spanning tree of V nodes has exactly V − 1 edges."),
    ),
    notationKey = listOf(
        NotationEntry("V, E", "vertices and edges"),
        NotationEntry("MST", "minimum spanning tree — connects all nodes at least total cost"),
        NotationEntry("find/union", "disjoint-set operations detecting/merging components"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Kruskal with union-find",
            accentColor = 0xFF6366F1,
            code = """
                fun kruskal(n: Int, edges: List<Triple<Int, Int, Int>>): Int {
                    val parent = IntArray(n) { it }
                    fun find(x: Int): Int {
                        var r = x
                        while (parent[r] != r) r = parent[r]
                        parent[x] = r
                        return r
                    }
                    var total = 0
                    for ((u, v, w) in edges.sortedBy { it.third }) {
                        val ru = find(u); val rv = find(v)
                        if (ru != rv) {          // different components -> no cycle
                            parent[ru] = rv
                            total += w
                        }
                    }
                    return total
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF10B981, "Network Design", "Lay cable, pipe, or road between sites at minimum total cost while keeping everything connected."),
        ApplicationCard("chip", 0xFF3B82F6, "Clustering", "Cutting the most expensive MST edges partitions data into natural clusters."),
        ApplicationCard("map", 0xFFF59E0B, "Circuit & Grid Layout", "Minimizing wiring length across components maps directly onto an MST."),
    ),
    takeaways = listOf(
        "Kruskal adds cheapest edges globally, skipping any that would form a cycle.",
        "Its O(E log E) cost is dominated by sorting; union-find makes cycle checks near-instant.",
        "It naturally handles disconnected input, building a minimum spanning forest.",
        "Prefer Kruskal for sparse, edge-listed graphs; Prim's suits dense, adjacency-based ones.",
    ),
    crossLinks = listOf(
        CrossLink("prims_mst", "Prim's MST"),
        CrossLink("disjoint_set", "Disjoint Set (Union-Find)"),
    ),
)
