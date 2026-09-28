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

// Interview-prep pattern guide. Picking the right shortest-path tool is most of the answer; the
// implementations already live in the Algorithms section.
internal val shortestPathPatternContent = TopicContent(
    topicId = "shortest_path_pattern",
    figure = Figure(
        caption = "Pick the algorithm from the edges, not the problem statement. Unit weights → BFS " +
            "(the queue already is a priority queue). Non-negative weights → Dijkstra. A negative edge " +
            "→ Bellman-Ford, because settling a node as final is exactly what a negative edge invalidates.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("S 0", 0.05f, 0.50f, FigureTone.Accent),
                FigureGraphNode("A 1", 0.38f, 0.14f, FigureTone.Primary),
                FigureGraphNode("B 4", 0.38f, 0.86f, FigureTone.Primary),
                FigureGraphNode("C 5", 0.72f, 0.50f, FigureTone.Primary),
                FigureGraphNode("T 6", 0.96f, 0.16f, FigureTone.Accent),
            ),
            edges = listOf(
                FigureEdge(0, 1, "1", directed = true, tone = FigureTone.Primary),
                FigureEdge(0, 2, "4", directed = true, tone = FigureTone.Accent),
                FigureEdge(1, 3, "6", directed = true),
                FigureEdge(2, 3, "1", directed = true, tone = FigureTone.Accent),
                FigureEdge(1, 4, "9", directed = true, tone = FigureTone.Warn),
                FigureEdge(3, 4, "1", directed = true, tone = FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Once edges carry weights, BFS stops being correct — the fewest hops and the cheapest route are different questions. The shortest-path pattern is choosing between BFS, 0-1 BFS, Dijkstra and Bellman-Ford by looking at the weights.",
        "Dijkstra is the default: a min-heap always expands the cheapest frontier node, and because no edge can lower a settled cost, the first time a node is popped its distance is final. That guarantee dies with negative edges.",
    ),
    steps = listOf(
        StepCard(1, "Read the Weights", "All equal → BFS. Only 0 and 1 → deque BFS. Non-negative → Dijkstra. Any negative → Bellman-Ford. All-pairs on a small graph → Floyd-Warshall.", 0xFFF59E0B),
        StepCard(2, "Model the State", "A node is not always a vertex: with fuel, stops or keys collected, the state is (vertex, resource) and the graph is the product.", 0xFF3B82F6),
        StepCard(3, "Relax from the Heap", "Pop the cheapest node; skip it if the popped cost is stale. For each edge, if d[u] + w < d[v], record it and push v.", 0xFFEF4444),
        StepCard(4, "Recover the Route", "Store a parent per improved node and walk it back from the target — the distances alone rarely satisfy the follow-up question.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Relaxation", "d[v] = min(d[v], d[u] + w(u, v))", "The single operation every shortest-path algorithm repeats."),
        FormulaEntry("Dijkstra", "O((V + E) log V)", "Binary heap; lazy deletion means up to E entries pushed."),
        FormulaEntry("Bellman-Ford", "O(V · E)", "V - 1 relaxation rounds; a V-th improvement proves a negative cycle."),
    ),
    notationKey = listOf(
        NotationEntry("d[v]", "best known distance from the source to v"),
        NotationEntry("w(u, v)", "weight of the edge u → v"),
        NotationEntry("pq", "min-heap keyed by tentative distance"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Dijkstra with lazy deletion (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                import heapq

                def dijkstra(graph, src):                    # graph: u -> [(v, w), ...]
                    dist = {src: 0}
                    pq = [(0, src)]
                    while pq:
                        d, u = heapq.heappop(pq)
                        if d > dist.get(u, float('inf')):
                            continue                         # stale entry, already settled cheaper
                        for v, w in graph.get(u, ()):
                            nd = d + w
                            if nd < dist.get(v, float('inf')):
                                dist[v] = nd
                                heapq.heappush(pq, (nd, v))
                    return dist
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF3B82F6, "Routing", "Road and transit directions, with weights as travel time rather than distance."),
        ApplicationCard("network", 0xFF10B981, "Network Latency", "Cheapest path through a weighted topology; K-stops variants add a hop dimension."),
        ApplicationCard("finance", 0xFF8B5CF6, "Arbitrage Detection", "Bellman-Ford over negated log rates — a negative cycle is a profitable loop."),
    ),
    takeaways = listOf(
        "Weights decide the algorithm; naming the right one is most of the interview answer.",
        "Dijkstra's correctness rests on non-negative edges — a single negative edge breaks the settled-once guarantee.",
        "Skip stale heap entries instead of implementing decrease-key; it is simpler and the same complexity.",
        "When a constraint like stops or fuel exists, put it in the state rather than patching the loop.",
    ),
    crossLinks = listOf(
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm (Algorithms)"),
        CrossLink("bellman_ford", "Bellman-Ford (Algorithms)"),
        CrossLink("heap", "Heap (Data Structures)"),
    ),
)
