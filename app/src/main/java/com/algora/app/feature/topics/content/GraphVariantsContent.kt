package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val graphVariantsContent = TopicContent(
    topicId = "graph_variants",
    figure = Figure(
        caption = "The same node set answers different questions depending on four independent choices, " +
            "and each one changes which algorithm is even legal: direction decides whether a cycle check " +
            "needs colours or a visited set, weights decide BFS versus Dijkstra, negative weights rule " +
            "Dijkstra out entirely, and density decides list versus matrix.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("yes", "no"),
                listOf("yes", "no"),
                listOf("yes", "no"),
                listOf("dense", "sparse"),
            ),
            rowHeaders = listOf("directed", "weighted", "negative", "density"),
            colHeaders = listOf("then", "else"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Primary),
                FigureCell(1, 0, FigureTone.Primary),
                FigureCell(2, 0, FigureTone.Warn),
                FigureCell(3, 0, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Graphs come in many flavors — directed vs undirected, weighted vs unweighted, and multigraphs that allow parallel edges — and the variant dictates which algorithms and representations apply.",
        "The two core representations, adjacency list and adjacency matrix, trade space for lookup speed, and the right choice depends on whether the graph is sparse or dense.",
    ),
    steps = listOf(
        StepCard(1, "Direction", "Directed edges point one way (u→v); undirected edges connect both ways symmetrically.", 0xFF10B981),
        StepCard(2, "Weights", "Weighted edges carry a cost or capacity; unweighted edges are all equal.", 0xFF3B82F6),
        StepCard(3, "Multiplicity", "Simple graphs forbid parallel edges and self-loops; multigraphs allow them.", 0xFFF59E0B),
        StepCard(4, "Representation", "Adjacency lists suit sparse graphs; adjacency matrices suit dense ones or O(1) edge tests.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Adjacency list", "O(V + E) space", "Best for sparse graphs; iterate neighbors in O(deg)."),
        FormulaEntry("Adjacency matrix", "O(V²) space", "O(1) edge lookup; best for dense graphs."),
        FormulaEntry("Edge count", "≤ V² (V(V−1)/2 undirected)", "Density is E relative to this maximum."),
    ),
    notationKey = listOf(
        NotationEntry("V, E", "vertices and edges"),
        NotationEntry("directed / undirected", "one-way vs symmetric edges"),
        NotationEntry("sparse / dense", "E ≪ V² vs E ≈ V²"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two representations",
            accentColor = 0xFF6366F1,
            code = """
                // Adjacency list — sparse-friendly, iterate neighbors directly.
                val adjList = HashMap<Int, MutableList<Pair<Int, Int>>>()   // node -> (nbr, weight)
                fun addEdge(u: Int, v: Int, w: Int, directed: Boolean) {
                    adjList.getOrPut(u) { mutableListOf() }.add(v to w)
                    if (!directed) adjList.getOrPut(v) { mutableListOf() }.add(u to w)
                }

                // Adjacency matrix — O(1) edge test, O(V^2) space.
                val n = 100
                val adjMatrix = Array(n) { IntArray(n) }   // 0 = no edge, else weight
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF10B981, "Road & Transit Networks", "Weighted directed graphs model one-way streets and travel costs for routing."),
        ApplicationCard("users", 0xFF3B82F6, "Social Networks", "Undirected graphs capture mutual friendships; directed ones model follows."),
        ApplicationCard("share", 0xFFF59E0B, "Flow & Capacity", "Multigraphs with edge capacities underpin max-flow and network-design problems."),
    ),
    takeaways = listOf(
        "Direction, weight, and multiplicity define a graph's variant and constrain the usable algorithms.",
        "Adjacency lists are the default for sparse graphs; matrices win when dense or for O(1) edge tests.",
        "Choosing the representation up front shapes every traversal's time and space cost.",
        "Most textbook algorithms assume a specific variant — match the model to the problem.",
    ),
    crossLinks = listOf(
        CrossLink("graph", "Graph"),
        CrossLink("bfs", "Breadth-First Search (BFS)"),
    ),
)
