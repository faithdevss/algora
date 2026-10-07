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

internal val graphContent = TopicContent(
    topicId = "graph",
    figure = Figure(
        caption = "Nodes and edges, and nothing else is promised — no root, no order, cycles allowed. The " +
            "representation is the real decision: an adjacency list costs O(V + E) space and lists a " +
            "node's neighbours instantly, while a matrix costs O(V²) but answers \"is there an edge\" in " +
            "O(1). Dense graphs favour the matrix; almost everything else favours the list.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A", 0.08f, 0.22f, FigureTone.Primary),
                FigureGraphNode("B", 0.40f, 0.05f, FigureTone.Primary),
                FigureGraphNode("C", 0.38f, 0.85f, FigureTone.Primary),
                FigureGraphNode("D", 0.72f, 0.40f, FigureTone.Accent),
                FigureGraphNode("E", 0.97f, 0.85f, FigureTone.Primary),
            ),
            edges = listOf(
                FigureEdge(0, 1),
                FigureEdge(0, 2),
                FigureEdge(1, 3, tone = FigureTone.Accent),
                FigureEdge(2, 3, tone = FigureTone.Accent),
                FigureEdge(3, 4),
                FigureEdge(2, 4),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A graph is a set of nodes (vertices) connected by edges, which may be directed or undirected and weighted or unweighted.",
        "Think of a city's road map: intersections are vertices, roads are edges, and one-way streets are directed edges.",
    ),
    steps = listOf(
        StepCard(1, "Vertices & Edges", "A graph is defined by a set of vertices V and a set of edges E connecting pairs of vertices.", 0xFF3B82F6),
        StepCard(2, "Adjacency Representation", "Graphs are stored as an adjacency list (per-vertex neighbor list) or adjacency matrix (V×V grid).", 0xFF8B5CF6),
        StepCard(3, "Traversal", "BFS explores level by level using a queue; DFS explores as deep as possible first using a stack or recursion.", 0xFFF59E0B),
        StepCard(4, "Weighted Paths", "When edges carry weights, shortest-path algorithms like Dijkstra's replace simple traversal.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("BFS / DFS traversal", "O(V + E)", "Every vertex and edge is visited once."),
        FormulaEntry("Adjacency matrix space", "O(V²)", "One cell per possible vertex pair, regardless of edge count."),
        FormulaEntry("Adjacency list space", "O(V + E)", "One entry per vertex plus one per edge — better for sparse graphs."),
    ),
    notationKey = listOf(
        NotationEntry("V", "number of vertices"),
        NotationEntry("E", "number of edges"),
        NotationEntry("adj[u]", "the list of neighbors of vertex u"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Graph — adjacency list + BFS",
            accentColor = 0xFF6366F1,
            code = """
                class Graph {
                    private val adjacency = mutableMapOf<Int, MutableList<Int>>()

                    fun addEdge(from: Int, to: Int) {
                        adjacency.getOrPut(from) { mutableListOf() }.add(to)
                        adjacency.getOrPut(to) { mutableListOf() }.add(from)
                    }

                    fun bfs(start: Int): List<Int> {
                        val visited = mutableSetOf(start)
                        val order = mutableListOf<Int>()
                        val queue = ArrayDeque(listOf(start))

                        while (queue.isNotEmpty()) {
                            val node = queue.removeFirst()
                            order.add(node)
                            for (neighbor in adjacency[node].orEmpty()) {
                                if (visited.add(neighbor)) queue.addLast(neighbor)
                            }
                        }
                        return order
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphVisualizer,
    applications = listOf(
        ApplicationCard("map", 0xFF3B82F6, "Navigation & Routing", "Road networks and flight routes are graphs; shortest-path algorithms power turn-by-turn directions."),
        ApplicationCard("share", 0xFF8B5CF6, "Social Networks", "People are vertices, friendships/follows are edges — traversal powers 'friends of friends' features."),
        ApplicationCard("network", 0xFF10B981, "Dependency Resolution", "Package managers model dependencies as a directed graph and topologically sort it to install in order."),
    ),
    takeaways = listOf(
        "A graph generalizes trees and linked lists — any node can connect to any other node, including cycles.",
        "Adjacency lists suit sparse graphs (few edges); adjacency matrices suit dense graphs (many edges).",
        "BFS finds shortest paths in unweighted graphs; DFS is naturally suited to cycle detection and topological sort.",
        "Weighted shortest-path problems need Dijkstra's, Bellman-Ford, or Floyd-Warshall instead of plain BFS.",
    ),
    crossLinks = listOf(
        CrossLink("graph_variants", "Graph Variants"),
        CrossLink("bfs", "Breadth-First Search"),
        CrossLink("dfs", "Depth-First Search"),
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
    ),
)
