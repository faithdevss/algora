package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bfsContent = TopicContent(
    topicId = "bfs",
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
