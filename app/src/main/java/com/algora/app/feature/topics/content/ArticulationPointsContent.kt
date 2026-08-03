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

internal val articulationPointsContent = TopicContent(
    topicId = "articulation_points",
    figure = Figure(
        caption = "Two triangles joined by a single edge. Remove C or D and the graph falls into two " +
            "pieces; remove anything else and it does not, because every other vertex sits on a cycle " +
            "and a cycle has no single point of failure. One DFS finds both: with disc as visit time and " +
            "low as the earliest vertex a subtree can reach using at most one back edge, a tree edge " +
            "u→v marks u as a cut vertex when low[v] ≥ disc[u] — the child cannot get above its parent, " +
            "so nothing bypasses it. Make the inequality strict and the edge itself is a bridge, which " +
            "C–D is. The DFS root is the one exception, being a cut vertex only when it has two or more " +
            "DFS children rather than one. O(V+E), same pass, same low-link machinery Tarjan's uses on " +
            "directed graphs.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A", 0.08f, 0.18f),
                FigureGraphNode("B", 0.08f, 0.82f),
                FigureGraphNode("C", 0.36f, 0.50f, FigureTone.Warn),
                FigureGraphNode("D", 0.66f, 0.50f, FigureTone.Warn),
                FigureGraphNode("E", 0.93f, 0.18f),
                FigureGraphNode("F", 0.93f, 0.82f),
            ),
            edges = listOf(
                FigureEdge(0, 1),
                FigureEdge(0, 2),
                FigureEdge(1, 2),
                FigureEdge(2, 3, "bridge", tone = FigureTone.Warn),
                FigureEdge(3, 4),
                FigureEdge(3, 5),
                FigureEdge(4, 5),
            ),
        ),
    ),
    whatIsIt = listOf(
        "An articulation point is a vertex whose removal increases the number of connected components; a bridge is an edge with the same property. They are the single points of failure in a network.",
        "One DFS finds them all. Each vertex is stamped with a discovery time, and `low` records the earliest-discovered vertex reachable from its subtree using at most one back edge. If a child cannot reach above its parent, that parent is a cut vertex.",
    ),
    steps = listOf(
        StepCard(1, "DFS and Timestamp", "Assign each vertex a discovery time `disc` in visit order.", 0xFF3B82F6),
        StepCard(2, "Compute low", "low[u] = min(disc[u], low[child] over tree edges, disc[w] over back edges).", 0xFF10B981),
        StepCard(3, "Test Each Child", "For a tree edge u → v: if low[v] ≥ disc[u], u is an articulation point.", 0xFFF59E0B),
        StepCard(4, "Bridges Are Strict", "The same edge is a bridge when low[v] > disc[u] — no back edge bypasses it at all.", 0xFF8B5CF6),
        StepCard(5, "Special-Case the Root", "The DFS root is a cut vertex only when it has two or more DFS children.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("low", "low[u] = min(disc[u], min low[child], min disc[back-edge target])", "How far up the DFS tree u's subtree can climb."),
        FormulaEntry("Cut vertex", "low[v] ≥ disc[u] for some child v", "Non-root rule."),
        FormulaEntry("Bridge", "low[v] > disc[u]", "Strict — the child subtree hangs off this edge alone."),
        FormulaEntry("Time", "O(V + E)", "A single DFS pass."),
    ),
    notationKey = listOf(
        NotationEntry("disc[u]", "time at which the DFS first entered u"),
        NotationEntry("low[u]", "smallest disc reachable from u's subtree via ≤ 1 back edge"),
        NotationEntry("tree edge", "edge to an unvisited vertex during DFS"),
        NotationEntry("back edge", "edge to an already-visited ancestor"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Cut vertices and bridges in one DFS",
            accentColor = 0xFF6366F1,
            code = """
                class CutFinder(private val n: Int, private val adj: Array<List<Int>>) {
                    private val disc = IntArray(n) { -1 }
                    private val low = IntArray(n)
                    private var timer = 0

                    val articulation = sortedSetOf<Int>()
                    val bridges = mutableListOf<Pair<Int, Int>>()

                    fun run() {
                        for (v in 0 until n) if (disc[v] == -1) dfs(v, parent = -1)
                    }

                    private fun dfs(u: Int, parent: Int) {
                        disc[u] = timer
                        low[u] = timer
                        timer++
                        var children = 0

                        for (v in adj[u]) {
                            if (v == parent) continue              // do not climb the edge we came in on
                            if (disc[v] != -1) {
                                low[u] = minOf(low[u], disc[v])    // back edge
                            } else {
                                children++
                                dfs(v, u)
                                low[u] = minOf(low[u], low[v])
                                if (low[v] > disc[u]) bridges += u to v
                                if (parent != -1 && low[v] >= disc[u]) articulation += u
                            }
                        }

                        if (parent == -1 && children > 1) articulation += u
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF3B82F6, "Network Resilience", "Routers and links that would partition a backbone are exactly its articulation points and bridges."),
        ApplicationCard("share", 0xFF10B981, "Social Graph Analysis", "Cut vertices are the brokers connecting otherwise separate communities."),
        ApplicationCard("map", 0xFFF59E0B, "Road & Grid Planning", "A bridge in a transport or power graph is an outage with no alternate route."),
    ),
    takeaways = listOf(
        "One DFS with disc/low answers both the cut-vertex and bridge questions.",
        "≥ for vertices, > for edges — that single character is the whole difference.",
        "The root of the DFS tree needs its own rule: two or more children.",
        "Parallel edges break the naive parent check; skip by edge id, not by vertex, when they can occur.",
    ),
    crossLinks = listOf(
        CrossLink("dfs", "Depth-First Search (DFS)"),
        CrossLink("tarjans_algorithm", "Tarjan's Algorithm"),
        CrossLink("disjoint_set", "Disjoint Set (Union-Find)"),
    ),
)
