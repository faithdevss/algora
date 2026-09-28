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

internal val dfsContent = TopicContent(
    topicId = "dfs",
    figure = Figure(
        caption = "The same graph BFS's page uses, numbered by visit order instead of by distance: A, B, " +
            "C, E, F, D. The tree is one long chain, because DFS commits to a direction and only " +
            "reconsiders when it runs out of room — deep and narrow where BFS is shallow and wide, on " +
            "identical input. A–C is a back edge, reaching a node still open on the current path, and " +
            "back edges are exactly what cycle detection watches for; the ancestor relation they expose " +
            "is what Tarjan's low-links generalise. The cost is the stack: recursion depth is O(V) here " +
            "rather than O(width), so a long graph overflows a DFS that a BFS would have walked fine.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A 1", 0.08f, 0.50f, FigureTone.Accent),
                FigureGraphNode("B 2", 0.36f, 0.16f, FigureTone.Primary),
                FigureGraphNode("C 3", 0.36f, 0.84f, FigureTone.Primary),
                FigureGraphNode("D 6", 0.66f, 0.16f, FigureTone.Primary),
                FigureGraphNode("E 4", 0.66f, 0.84f, FigureTone.Primary),
                FigureGraphNode("F 5", 0.93f, 0.50f, FigureTone.Primary),
            ),
            edges = listOf(
                FigureEdge(0, 1, tone = FigureTone.Accent),
                FigureEdge(1, 2, tone = FigureTone.Accent),
                FigureEdge(2, 4, tone = FigureTone.Accent),
                FigureEdge(4, 5, tone = FigureTone.Accent),
                FigureEdge(3, 5, tone = FigureTone.Accent),
                FigureEdge(0, 2, "back edge", tone = FigureTone.Warn),
                FigureEdge(1, 3),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Depth-first search explores a graph by going as deep as possible along one path before backtracking to try the next unexplored branch.",
        "It uses a stack — explicit, or implicitly via recursion — so the most recently discovered node is the next one expanded.",
    ),
    steps = listOf(
        StepCard(1, "Visit & Mark", "Start at a source node, mark it visited so it's never processed twice.", 0xFF10B981),
        StepCard(2, "Go Deeper", "Recurse into the first unvisited neighbor, then its unvisited neighbor, and so on.", 0xFF3B82F6),
        StepCard(3, "Backtrack", "When a node has no unvisited neighbors, return to the previous node and try its next branch.", 0xFFF59E0B),
        StepCard(4, "Repeat Until Done", "Continue until every node reachable from the source has been visited.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(V + E)", "Each vertex and edge is examined once."),
        FormulaEntry("Space", "O(V)", "Recursion/stack depth plus the visited set."),
        FormulaEntry("Traversal order", "Deepest-first", "Explores a branch fully before its siblings."),
    ),
    notationKey = listOf(
        NotationEntry("V", "number of vertices (nodes)"),
        NotationEntry("E", "number of edges"),
        NotationEntry("visited", "set marking already-explored nodes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "DFS — recursive",
            accentColor = 0xFF6366F1,
            code = """
                fun dfs(
                    node: Int,
                    adjacency: Map<Int, List<Int>>,
                    visited: MutableSet<Int> = mutableSetOf(),
                ) {
                    if (!visited.add(node)) return   // already seen
                    for (next in adjacency[node].orEmpty()) {
                        dfs(next, adjacency, visited)
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphVisualizer,
    applications = listOf(
        ApplicationCard("share", 0xFF10B981, "Cycle & Connectivity Checks", "DFS detects cycles, finds connected components, and drives topological sort on dependency graphs."),
        ApplicationCard("map", 0xFF3B82F6, "Maze & Puzzle Solving", "Exploring one path fully before backing up is the backbone of backtracking search."),
        ApplicationCard("link", 0xFFF59E0B, "Path & Tree Traversal", "Pre/in/post-order tree walks and 'does a path exist?' queries are DFS in disguise."),
    ),
    takeaways = listOf(
        "DFS goes deep first and backtracks, driven by a stack (often the call stack via recursion).",
        "It runs in O(V + E) and needs a visited set to avoid revisiting nodes in cyclic graphs.",
        "It underpins cycle detection, topological sort, connected components, and backtracking.",
        "Use DFS when you must exhaust paths; use BFS when you need the shortest unweighted path.",
    ),
    crossLinks = listOf(
        CrossLink("bfs", "Breadth-First Search (BFS)"),
        CrossLink("graph", "Graph"),
    ),
)
