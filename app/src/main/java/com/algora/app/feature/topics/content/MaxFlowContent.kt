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

internal val maxFlowContent = TopicContent(
    topicId = "max_flow",
    figure = Figure(
        caption = "Every edge here has capacity 1. Take the obvious first augmenting path, S→A→B→T: it " +
            "saturates the middle edge and now neither S→B nor A→T can reach the other side. A greedy " +
            "algorithm stops at 1 and is wrong. What saves it is that pushing a unit along A→B creates " +
            "a backward edge B→A of the same size, so the second path S→B→A→T can travel it and " +
            "*cancel* the first commitment on its way through — the answer is 2. That reverse edge is " +
            "the whole difference between Ford-Fulkerson and greed, and it is why no bad early choice is " +
            "permanent. When no augmenting path is left, the set still reachable from S is one side of a " +
            "minimum cut and its capacity equals the flow. Choosing paths by BFS makes it Edmonds-Karp, " +
            "at O(V·E²) regardless of the capacities.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("S", 0.08f, 0.50f, FigureTone.Primary),
                FigureGraphNode("A", 0.45f, 0.16f, FigureTone.Primary),
                FigureGraphNode("B", 0.45f, 0.84f, FigureTone.Primary),
                FigureGraphNode("T", 0.92f, 0.50f, FigureTone.Accent),
            ),
            edges = listOf(
                FigureEdge(0, 1, "1", directed = true, tone = FigureTone.Accent),
                FigureEdge(0, 2, "1", directed = true, tone = FigureTone.Accent),
                FigureEdge(1, 3, "1", directed = true, tone = FigureTone.Accent),
                FigureEdge(2, 3, "1", directed = true, tone = FigureTone.Accent),
                FigureEdge(2, 1, "undo 1", directed = true, tone = FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Maximum flow asks how much can be pushed from a source to a sink through a network whose edges each have a capacity.",
        "Ford-Fulkerson answers it by repeatedly finding a path with spare capacity and saturating it. The subtlety is the residual graph: every unit pushed along u → v creates a backward edge v → u, so a later path can undo an earlier commitment. Choosing augmenting paths by BFS (shortest first) is Edmonds-Karp and gives a polynomial bound.",
    ),
    steps = listOf(
        StepCard(1, "Build the Residual Graph", "Each edge starts with residual = capacity; each reverse edge starts at 0.", 0xFF3B82F6),
        StepCard(2, "Find an Augmenting Path", "Search source → sink using only edges with residual > 0. BFS makes it Edmonds-Karp.", 0xFF10B981),
        StepCard(3, "Push the Bottleneck", "The path can carry only as much as its smallest residual edge allows.", 0xFFF59E0B),
        StepCard(4, "Update Both Directions", "Subtract the bottleneck from forward residuals, add it to the reverse ones — this is what allows rerouting.", 0xFF8B5CF6),
        StepCard(5, "Stop When No Path Remains", "The reachable set from the source is then one side of a minimum cut.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Conservation", "Σ flow_in(v) = Σ flow_out(v) for v ∉ {s, t}", "Nothing is created or destroyed at intermediate nodes."),
        FormulaEntry("Bottleneck", "Δ = min residual along the path", "How much this augmentation adds."),
        FormulaEntry("Max-flow min-cut", "max flow = min cut capacity", "The two problems are duals."),
        FormulaEntry("Edmonds-Karp", "O(V·E²)", "BFS augmenting paths bound the iteration count independently of capacities."),
    ),
    notationKey = listOf(
        NotationEntry("s, t", "source and sink"),
        NotationEntry("c(u,v)", "capacity of edge u → v"),
        NotationEntry("f(u,v)", "flow currently sent along u → v"),
        NotationEntry("residual", "c(u,v) − f(u,v), the spare capacity left"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Edmonds-Karp",
            accentColor = 0xFF6366F1,
            code = """
                // capacity[u][v] is mutated in place into the residual graph.
                fun maxFlow(capacity: Array<IntArray>, s: Int, t: Int): Int {
                    val n = capacity.size
                    var total = 0

                    while (true) {
                        // BFS for the shortest augmenting path.
                        val parent = IntArray(n) { -1 }
                        parent[s] = s
                        val queue = ArrayDeque<Int>().apply { add(s) }
                        while (queue.isNotEmpty() && parent[t] == -1) {
                            val u = queue.removeFirst()
                            for (v in 0 until n) {
                                if (parent[v] == -1 && capacity[u][v] > 0) {
                                    parent[v] = u
                                    queue += v
                                }
                            }
                        }
                        if (parent[t] == -1) return total   // sink unreachable: done

                        // Bottleneck along the path.
                        var delta = Int.MAX_VALUE
                        var v = t
                        while (v != s) {
                            val u = parent[v]
                            delta = minOf(delta, capacity[u][v])
                            v = u
                        }

                        // Push it, opening reverse capacity for later reroutes.
                        v = t
                        while (v != s) {
                            val u = parent[v]
                            capacity[u][v] -= delta
                            capacity[v][u] += delta
                            v = u
                        }
                        total += delta
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF3B82F6, "Bipartite Matching", "Job-to-worker assignment becomes max flow with unit capacities — the flow value is the matching size."),
        ApplicationCard("globe", 0xFF10B981, "Network Provisioning", "Traffic engineering asks the same question of routers, links and bandwidth."),
        ApplicationCard("chip", 0xFFF59E0B, "Image Segmentation", "Foreground/background cuts in vision are solved as a min cut on a pixel graph."),
    ),
    takeaways = listOf(
        "Residual reverse edges are the heart of the algorithm — without them greedy paths get stuck at a suboptimal flow.",
        "Max flow equals min cut, so one run answers both the throughput and the bottleneck question.",
        "Plain Ford-Fulkerson can loop badly on irrational or huge capacities; Edmonds-Karp's BFS choice fixes the bound.",
        "Many assignment and matching problems are max flow in disguise.",
    ),
    crossLinks = listOf(
        CrossLink("bfs", "Breadth-First Search (BFS)"),
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
        CrossLink("graph_variants", "Graph Variants"),
    ),
)
