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

internal val bfsContent = TopicContent(
    topicId = "bfs",
    figure = Figure(
        caption = "Labels are the distance from A, and BFS assigns them in the order it visits: A, B, C, " +
            "D, E, F — one whole layer before the next begins. The queue is what enforces that, and it " +
            "is why the label a node gets the first time it is seen is already final: on an unweighted " +
            "graph nothing found later can be closer. Give the edges different weights and that stops " +
            "being true, which is the entire reason Dijkstra exists. B–C connects two nodes on the same " +
            "layer, so it can never be a tree edge — in an undirected BFS every non-tree edge joins " +
            "nodes at most one layer apart, and that is what makes odd cycles (and bipartiteness) " +
            "detectable in the same pass.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A 0", 0.08f, 0.50f, FigureTone.Accent),
                FigureGraphNode("B 1", 0.36f, 0.16f, FigureTone.Primary),
                FigureGraphNode("C 1", 0.36f, 0.84f, FigureTone.Primary),
                FigureGraphNode("D 2", 0.66f, 0.16f, FigureTone.Primary),
                FigureGraphNode("E 2", 0.66f, 0.84f, FigureTone.Primary),
                FigureGraphNode("F 3", 0.93f, 0.50f, FigureTone.Primary),
            ),
            edges = listOf(
                FigureEdge(0, 1, tone = FigureTone.Accent),
                FigureEdge(0, 2, tone = FigureTone.Accent),
                FigureEdge(1, 3, tone = FigureTone.Accent),
                FigureEdge(2, 4, tone = FigureTone.Accent),
                FigureEdge(3, 5, tone = FigureTone.Accent),
                FigureEdge(1, 2, "same layer", tone = FigureTone.Warn),
                FigureEdge(4, 5),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Breadth-first search (BFS) explores a graph or tree level by level, visiting all neighbors of a node before moving to the next level.",
        "Think of ripples spreading from a stone dropped in water — BFS visits everything at distance 1, then distance 2, and so on.",
    ),
    steps = listOf(
        StepCard(1, "Start at the Source", "Enqueue the starting node and mark it visited.", 0xFF3B82F6),
        StepCard(2, "Dequeue & Expand", "Remove the front of the queue and enqueue any unvisited neighbors.", 0xFF8B5CF6),
        StepCard(3, "Mark as Visited", "Mark each node visited the moment it's enqueued, not when it's dequeued, to avoid duplicate visits.", 0xFFF59E0B),
        StepCard(4, "Repeat Until Empty", "Continue until the queue is empty — every reachable node has now been visited in order of distance.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time complexity", "O(V + E)", "Every vertex is dequeued once, every edge is examined once."),
        FormulaEntry("Space complexity", "O(V)", "The queue and visited set can each hold up to all vertices."),
        FormulaEntry("Shortest path (unweighted)", "guaranteed", "BFS finds shortest paths by hop count in unweighted graphs."),
    ),
    notationKey = listOf(
        NotationEntry("V", "number of vertices"),
        NotationEntry("E", "number of edges"),
        NotationEntry("visited", "set of nodes already enqueued"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Breadth-First Search",
            accentColor = 0xFF6366F1,
            code = """
                fun bfs(adjacency: Map<Int, List<Int>>, start: Int): List<Int> {
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
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphVisualizer,
    applications = listOf(
        ApplicationCard("map", 0xFF3B82F6, "Shortest Path (Unweighted)", "Finding the fewest number of hops/moves — puzzle solvers, social-network 'degrees of separation.'"),
        ApplicationCard("network", 0xFF8B5CF6, "Web Crawling", "Crawlers visit pages level by level from a seed URL, mirroring BFS's queue-driven expansion."),
        ApplicationCard("share", 0xFFF59E0B, "Broadcast/Flood Fill", "Flood-fill in image editors and network broadcast both spread outward exactly like BFS."),
    ),
    takeaways = listOf(
        "BFS uses a queue (FIFO) — that's what forces level-by-level exploration.",
        "In an unweighted graph, BFS is guaranteed to find the shortest path in terms of edge count.",
        "Marking nodes visited at enqueue time, not dequeue time, is essential to avoid processing a node twice.",
        "DFS explores deep first with a stack; BFS explores wide first with a queue — same graph, opposite traversal shape.",
    ),
    crossLinks = listOf(
        // DSA ↔ AI bridge: breadth-first expansion mirrors tree-search node expansion in planning.
        CrossLink("mcts", "Monte Carlo Tree Search"),
    ),
)
