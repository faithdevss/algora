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

internal val bellmanFordContent = TopicContent(
    topicId = "bellman_ford",
    figure = Figure(
        caption = "Distances after each full pass over the edges, on a graph with S→A 5, S→B 4, " +
            "B→A −3 and A→C 2. Watch A: it reads 5 after the first pass and then *drops* to 1, because " +
            "the negative edge out of B undercuts a value that already looked settled. Dijkstra cannot " +
            "survive that — it would have finalised A at 5 and never looked again. Bellman-Ford gives up " +
            "on being clever and relaxes every edge V−1 times, which is enough whatever order the edges " +
            "come in, because a shortest path uses at most V−1 of them. The last row is the extra pass: " +
            "nothing improves, so there is no negative cycle. Anything that still improved would prove " +
            "one, and would mean no shortest path exists at all. O(V·E).",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "∞", "∞", "∞"),
                listOf("0", "5", "4", "∞"),
                listOf("0", "1", "4", "7"),
                listOf("0", "1", "4", "3"),
                listOf("0", "1", "4", "3"),
            ),
            rowHeaders = listOf("init", "pass 1", "pass 2", "pass 3", "check"),
            colHeaders = listOf("S", "A", "B", "C"),
            marks = listOf(
                FigureCell(2, 1, FigureTone.Warn),
                FigureCell(3, 3, FigureTone.Accent),
                FigureCell(4, 1, FigureTone.Muted),
                FigureCell(4, 2, FigureTone.Muted),
                FigureCell(4, 3, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Bellman-Ford finds shortest paths from a single source even when edges have negative weights — the case Dijkstra's greedy approach can't handle.",
        "Instead of settling nodes one at a time, it relaxes every edge repeatedly; after V−1 full passes all shortest paths are final, and one extra pass detects negative cycles.",
    ),
    steps = listOf(
        StepCard(1, "Initialize", "Source distance is 0; all others start at infinity.", 0xFF10B981),
        StepCard(2, "Relax All Edges", "In one pass, for every edge (u,v,w) update d[v] if d[u] + w is smaller.", 0xFF3B82F6),
        StepCard(3, "Repeat V−1 Times", "A shortest path uses at most V−1 edges, so V−1 passes guarantee convergence.", 0xFFF59E0B),
        StepCard(4, "Detect Negative Cycles", "One more pass that still improves a distance proves a reachable negative cycle exists.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(V · E)", "V−1 passes, each relaxing all E edges."),
        FormulaEntry("Relaxation", "if d[u] + w < d[v]: d[v] = d[u] + w", "The single update rule."),
        FormulaEntry("Negative cycle", "improvement on pass V", "Any further relaxation flags an unbounded path."),
    ),
    notationKey = listOf(
        NotationEntry("V, E", "vertices and edges"),
        NotationEntry("d[v]", "best known distance from the source"),
        NotationEntry("relax", "improve a distance estimate via an edge"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bellman-Ford",
            accentColor = 0xFF6366F1,
            code = """
                // edges: (u, v, w). Returns null if a negative cycle is reachable.
                fun bellmanFord(n: Int, edges: List<Triple<Int, Int, Int>>, src: Int): IntArray? {
                    val d = IntArray(n) { Int.MAX_VALUE }
                    d[src] = 0
                    repeat(n - 1) {
                        for ((u, v, w) in edges) {
                            if (d[u] != Int.MAX_VALUE && d[u] + w < d[v]) d[v] = d[u] + w
                        }
                    }
                    for ((u, v, w) in edges) {              // extra pass: cycle check
                        if (d[u] != Int.MAX_VALUE && d[u] + w < d[v]) return null
                    }
                    return d
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Currency Arbitrage", "Modeling exchange rates as negative log weights turns arbitrage detection into a negative-cycle search."),
        ApplicationCard("share", 0xFF3B82F6, "Distance-Vector Routing", "RIP and similar protocols relax neighbor distances the Bellman-Ford way."),
        ApplicationCard("target", 0xFFF59E0B, "Constraint Systems", "Difference constraints (x − y ≤ c) are solved as shortest paths with Bellman-Ford."),
    ),
    takeaways = listOf(
        "Bellman-Ford handles negative edge weights that break Dijkstra.",
        "Relaxing all edges V−1 times guarantees shortest paths; a V-th pass detects negative cycles.",
        "It costs O(V·E) — slower than Dijkstra but more general.",
        "Its negative-cycle detection powers arbitrage and constraint-feasibility checks.",
    ),
    crossLinks = listOf(
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
        CrossLink("floyd_warshall", "Floyd-Warshall Algorithm"),
    ),
)
