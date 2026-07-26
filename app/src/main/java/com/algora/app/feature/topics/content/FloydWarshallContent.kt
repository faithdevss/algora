package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val floydWarshallContent = TopicContent(
    topicId = "floyd_warshall",
    whatIsIt = listOf(
        "Floyd-Warshall computes the shortest path between every pair of nodes at once, using a compact triple loop over an all-pairs distance matrix.",
        "Its idea is deceptively simple: allow paths to pass through one more intermediate node at a time, and keep whichever route is shorter.",
    ),
    steps = listOf(
        StepCard(1, "Start with Direct Edges", "The distance matrix begins as edge weights (∞ where no edge, 0 on the diagonal).", 0xFF10B981),
        StepCard(2, "Add One Intermediate", "For each candidate midpoint k, test every pair (i, j).", 0xFF3B82F6),
        StepCard(3, "Relax Through k", "If going i → k → j is shorter than the current i → j, update it.", 0xFFF59E0B),
        StepCard(4, "All Pairs Done", "After every node has served as an intermediate, the matrix holds all shortest distances.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Transition", "d[i][j] = min(d[i][j], d[i][k] + d[k][j])", "Route i→j through intermediate k."),
        FormulaEntry("Time", "O(V³)", "Triple nested loop over all nodes."),
        FormulaEntry("Space", "O(V²)", "The all-pairs distance matrix."),
    ),
    notationKey = listOf(
        NotationEntry("V", "number of vertices"),
        NotationEntry("d[i][j]", "shortest distance from i to j so far"),
        NotationEntry("k", "the intermediate node being allowed"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Floyd-Warshall",
            accentColor = 0xFF6366F1,
            code = """
                // d[i][j] initialized to edge weights, INF if none, 0 on the diagonal.
                fun floydWarshall(d: Array<IntArray>) {
                    val n = d.size
                    for (k in 0 until n)
                        for (i in 0 until n)
                            for (j in 0 until n) {
                                if (d[i][k] != INF && d[k][j] != INF &&
                                    d[i][k] + d[k][j] < d[i][j]) {
                                    d[i][j] = d[i][k] + d[k][j]
                                }
                            }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF10B981, "All-Pairs Routing", "Precompute every source-destination distance in dense networks or small maps."),
        ApplicationCard("share", 0xFF3B82F6, "Transitive Closure", "The same loop with boolean OR answers 'is j reachable from i?' for all pairs (Warshall's algorithm)."),
        ApplicationCard("chart", 0xFFF59E0B, "Graph Metrics", "Diameter, centrality, and betweenness measures need all-pairs distances."),
    ),
    takeaways = listOf(
        "Floyd-Warshall solves all-pairs shortest paths in one O(V³) DP.",
        "It progressively allows each node as an intermediate, relaxing every pair.",
        "It handles negative edges (but not negative cycles) and is trivial to code.",
        "Prefer it for dense graphs or when you need every pair; run Dijkstra per source for sparse ones.",
    ),
    crossLinks = listOf(
        CrossLink("bellman_ford", "Bellman-Ford Algorithm"),
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
    ),
)
