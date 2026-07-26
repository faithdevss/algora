package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dijkstrasAlgorithmContent = TopicContent(
    topicId = "dijkstras_algorithm",
    whatIsIt = listOf(
        "Dijkstra's algorithm finds the shortest paths from a single source to every other node in a graph with non-negative edge weights.",
        "It is greedy: at each step it locks in the closest unfinished node, confident that no cheaper route to it can appear later — a guarantee that holds only when weights are non-negative.",
    ),
    steps = listOf(
        StepCard(1, "Initialize Distances", "Source distance is 0; every other node starts at infinity.", 0xFF10B981),
        StepCard(2, "Pick the Closest", "From a min-priority queue, take the unvisited node with the smallest known distance.", 0xFF3B82F6),
        StepCard(3, "Relax Neighbors", "For each neighbor, if going through the current node is cheaper, update its distance.", 0xFFF59E0B),
        StepCard(4, "Finalize & Repeat", "Mark the node done — its distance is now final — and repeat until all nodes are settled.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("With a binary heap", "O((V + E) log V)", "Each edge may trigger a heap update."),
        FormulaEntry("Relaxation", "if d[u] + w < d[v]: d[v] = d[u] + w", "Improve a neighbor's best-known distance."),
        FormulaEntry("Requires", "weights ≥ 0", "Negative edges break the greedy guarantee — use Bellman-Ford instead."),
    ),
    notationKey = listOf(
        NotationEntry("d[v]", "best known distance from the source to v"),
        NotationEntry("w", "weight of an edge"),
        NotationEntry("V, E", "vertices and edges"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Dijkstra with a priority queue",
            accentColor = 0xFF6366F1,
            code = """
                fun dijkstra(
                    source: Int,
                    adj: Map<Int, List<Pair<Int, Int>>>, // node -> (neighbor, weight)
                    n: Int,
                ): IntArray {
                    val dist = IntArray(n) { Int.MAX_VALUE }
                    dist[source] = 0
                    val pq = java.util.PriorityQueue<Pair<Int, Int>>(compareBy { it.second })
                    pq.add(source to 0)
                    while (pq.isNotEmpty()) {
                        val (u, du) = pq.poll()
                        if (du > dist[u]) continue          // stale entry
                        for ((v, w) in adj[u].orEmpty()) {
                            if (du + w < dist[v]) {
                                dist[v] = du + w
                                pq.add(v to dist[v])
                            }
                        }
                    }
                    return dist
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PathfindingGrid,
    applications = listOf(
        ApplicationCard("map", 0xFF10B981, "GPS & Routing", "Navigation apps compute fastest routes over road networks with Dijkstra and its heuristic cousin A*."),
        ApplicationCard("share", 0xFF3B82F6, "Network Routing", "Link-state protocols like OSPF run Dijkstra to build shortest-path routing tables."),
        ApplicationCard("game", 0xFFF59E0B, "Game Pathfinding", "Units find least-cost paths across weighted terrain grids using Dijkstra-based search."),
    ),
    takeaways = listOf(
        "Dijkstra greedily settles the nearest node first, giving single-source shortest paths.",
        "It requires non-negative edge weights; negative edges need Bellman-Ford.",
        "A min-priority queue brings it to O((V + E) log V).",
        "Add a goal heuristic and it becomes A*, the standard for point-to-point pathfinding.",
    ),
    crossLinks = listOf(
        CrossLink("bfs", "Breadth-First Search (BFS)"),
        CrossLink("prims_mst", "Prim's MST"),
    ),
)
