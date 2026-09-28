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

internal val primsMstContent = TopicContent(
    topicId = "prims_mst",
    figure = Figure(
        caption = "The same graph Kruskal's page uses, caught mid-run. The tree so far is {A, B} and the " +
            "only edges that exist as far as Prim is concerned are the ones crossing out of it: 3 beats " +
            "4, so C joins next. Look at the edge weighted 2 — it is the cheapest edge left anywhere in " +
            "the graph and Prim will not look at it yet, because neither end is in the tree. Kruskal " +
            "would already have taken it. That is the real difference: one connected tree growing " +
            "across a cut, never a forest, so no cycle test is needed at all — an edge into a node " +
            "already inside simply is not a crossing edge. Same MST, weight 11, different order.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A", 0.12f, 0.22f, FigureTone.Primary),
                FigureGraphNode("B", 0.12f, 0.80f, FigureTone.Primary),
                FigureGraphNode("C", 0.50f, 0.50f),
                FigureGraphNode("D", 0.88f, 0.22f),
                FigureGraphNode("E", 0.88f, 0.80f),
            ),
            edges = listOf(
                FigureEdge(0, 1, "1", tone = FigureTone.Primary),
                FigureEdge(0, 2, "3", tone = FigureTone.Accent),
                FigureEdge(1, 2, "4", tone = FigureTone.Warn),
                FigureEdge(2, 3, "2"),
                FigureEdge(3, 4, "5"),
                FigureEdge(2, 4, "6"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Prim's algorithm grows a minimum spanning tree from a single starting node, repeatedly adding the cheapest edge that connects the tree to a node not yet in it.",
        "Where Kruskal thinks in edges, Prim is node-centric: it keeps one connected tree the whole time and extends its frontier greedily.",
    ),
    steps = listOf(
        StepCard(1, "Start Anywhere", "Put one arbitrary node into the tree.", 0xFF10B981),
        StepCard(2, "Look at the Frontier", "Consider every edge crossing from the tree to a node outside it.", 0xFF3B82F6),
        StepCard(3, "Add the Cheapest Crossing Edge", "Pick the lightest such edge and pull its outside node into the tree.", 0xFFF59E0B),
        StepCard(4, "Repeat Until Spanned", "Keep extending the frontier until all nodes are in the tree.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("With a binary heap", "O((V + E) log V)", "A min-heap holds candidate crossing edges."),
        FormulaEntry("Dense graphs", "O(V²)", "A simple array scan can beat the heap when E ≈ V²."),
        FormulaEntry("Invariant", "one connected tree", "The chosen edges always form a single growing tree."),
    ),
    notationKey = listOf(
        NotationEntry("V, E", "vertices and edges"),
        NotationEntry("key[v]", "cheapest edge weight connecting v to the current tree"),
        NotationEntry("frontier", "edges crossing from tree to non-tree nodes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Prim with a priority queue",
            accentColor = 0xFF6366F1,
            code = """
                fun prim(n: Int, adj: Map<Int, List<Pair<Int, Int>>>): Int {
                    val inTree = BooleanArray(n)
                    val pq = java.util.PriorityQueue<Pair<Int, Int>>(compareBy { it.second })
                    pq.add(0 to 0)   // start node, cost 0
                    var total = 0
                    while (pq.isNotEmpty()) {
                        val (u, w) = pq.poll()
                        if (inTree[u]) continue
                        inTree[u] = true
                        total += w
                        for ((v, wv) in adj[u].orEmpty()) {
                            if (!inTree[v]) pq.add(v to wv)
                        }
                    }
                    return total
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF10B981, "Utility & Network Layout", "Connect all sites at minimum cost when the graph is dense and given as adjacency lists."),
        ApplicationCard("map", 0xFF3B82F6, "Maze Generation", "Randomized-weight Prim's carves connected, loop-free mazes."),
        ApplicationCard("chip", 0xFFF59E0B, "Approximate TSP", "MSTs from Prim's seed 2-approximation heuristics for the traveling salesman problem."),
    ),
    takeaways = listOf(
        "Prim's grows one tree outward, always adding the cheapest edge to a new node.",
        "With a heap it runs in O((V + E) log V); a plain array is better on dense graphs at O(V²).",
        "It and Kruskal both yield a minimum spanning tree — the choice is graph density and representation.",
        "Its always-connected invariant makes it a natural fit for adjacency-list graphs.",
    ),
    crossLinks = listOf(
        CrossLink("kruskals_mst", "Kruskal's MST"),
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
    ),
)
