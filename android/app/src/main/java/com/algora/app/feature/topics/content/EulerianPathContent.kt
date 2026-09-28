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

internal val eulerianPathContent = TopicContent(
    topicId = "eulerian_path",
    figure = Figure(
        caption = "Königsberg, 1736 — four land masses, seven bridges, and the labels are degrees (the " +
            "doubled edges are two bridges drawn as one line). All four are odd, and that settles it " +
            "without any searching at all. Every time a walk enters a vertex it has to leave again, so " +
            "edges get consumed in pairs and an odd vertex can only ever be an endpoint; a walk has two " +
            "endpoints, four odd vertices is two too many, and no amount of cleverness would have " +
            "helped. Zero odd vertices gives a circuit, exactly two gives a path forced to run between " +
            "them, anything else gives nothing. Existence decided by counting in linear time is what " +
            "makes this the easy twin — the Hamiltonian version, one word different, has no such test. " +
            "Given a legal graph, Hierholzer's algorithm builds the walk in O(V+E) by splicing " +
            "sub-circuits back into the first one.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("N 3", 0.38f, 0.10f, FigureTone.Warn),
                FigureGraphNode("A 5", 0.14f, 0.50f, FigureTone.Warn),
                FigureGraphNode("S 3", 0.38f, 0.90f, FigureTone.Warn),
                FigureGraphNode("B 3", 0.82f, 0.50f, FigureTone.Warn),
            ),
            edges = listOf(
                FigureEdge(1, 0, "×2"),
                FigureEdge(1, 2, "×2"),
                FigureEdge(1, 3, "1"),
                FigureEdge(0, 3, "1"),
                FigureEdge(2, 3, "1"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "An Eulerian path walks every edge of a graph exactly once. If it also returns to where it started it is an Eulerian circuit. This is the problem Euler settled in 1736 for the seven bridges of Königsberg, and the settling of it is generally taken as the start of graph theory.",
        "What makes it remarkable is that existence is decided by counting, not by searching. An undirected connected graph has an Eulerian circuit exactly when every vertex has even degree, and an Eulerian path exactly when either zero or two vertices have odd degree — and when there are two, the walk is forced to start at one and end at the other. The reason is local: every time the walk enters a vertex it must leave again, consuming edges in pairs, so an odd-degree vertex can only be an endpoint. Königsberg had four odd vertices, which is two too many, and no amount of cleverness could have helped.",
        "Constructing the walk is Hierholzer's algorithm and it is linear in the number of edges. Follow unused edges from the start vertex until you get stuck — with even degrees, you can only ever get stuck back where you began, so what you have is a closed circuit that may have missed edges. Scan that circuit for any vertex still carrying unused edges, run the same procedure from there to get a second circuit, and splice it into the first at that vertex. Repeat until nothing is left. Each edge is consumed once and inspected O(1) times, giving O(V + E). The naive alternative, Fleury's algorithm, avoids bridges at each step and pays O(E²) for the bridge tests.",
    ),
    steps = listOf(
        StepCard(1, "Count Degrees", "Tally each vertex's degree. This alone decides whether a walk exists.", 0xFF3B82F6),
        StepCard(2, "Apply the Parity Gate", "Zero odd vertices → circuit. Exactly two → path between them. Anything else → no walk.", 0xFFEC4899),
        StepCard(3, "Check Connectivity", "All edges must lie in one component; isolated vertices with no edges are allowed and ignored.", 0xFFF59E0B),
        StepCard(4, "Walk Until Stuck", "From the start vertex, consume unused edges greedily. Even degrees guarantee you halt where you began.", 0xFF10B981),
        StepCard(5, "Find a Vertex with Leftovers", "Scan the circuit built so far for any vertex that still has unused edges.", 0xFF8B5CF6),
        StepCard(6, "Splice and Repeat", "Build a sub-circuit from that vertex and insert it into the main one. Terminate when every edge is used.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Circuit (undirected)", "every deg(v) even", "Plus: all edges in one connected component."),
        FormulaEntry("Path (undirected)", "exactly 0 or 2 odd-degree vertices", "With two, the endpoints are forced to be those two."),
        FormulaEntry("Circuit (directed)", "in(v) = out(v) for all v", "And the graph is connected when arrow directions are ignored."),
        FormulaEntry("Path (directed)", "one v with out−in = 1, one with in−out = 1", "Start and end respectively; all others balanced."),
        FormulaEntry("Hierholzer", "O(V + E)", "Each edge consumed once; splicing is O(1) with a linked structure."),
        FormulaEntry("Fleury", "O(E²)", "The bridge test per step is what costs; Hierholzer needs no such test."),
    ),
    notationKey = listOf(
        NotationEntry("deg(v)", "number of edges incident to v; a self-loop counts twice"),
        NotationEntry("odd vertex", "a vertex of odd degree — only ever an endpoint of an Eulerian path"),
        NotationEntry("circuit", "a closed walk; a path here is the open version"),
        NotationEntry("bridge", "an edge whose removal disconnects the graph — Fleury's obstacle, not Hierholzer's"),
        NotationEntry("splice", "inserting a sub-circuit into a circuit at their shared vertex"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The parity gate",
            accentColor = 0xFFEC4899,
            code = """
                enum class EulerKind { CIRCUIT, PATH, NONE }

                fun classify(adj: Map<String, List<String>>): EulerKind {
                    val odd = adj.count { (_, nbrs) -> nbrs.size % 2 == 1 }
                    return when (odd) {
                        0 -> EulerKind.CIRCUIT       // start anywhere, end where you started
                        2 -> EulerKind.PATH          // start at one odd vertex, end at the other
                        else -> EulerKind.NONE       // Königsberg had four
                    }
                    // Connectivity of the edge-carrying vertices is a separate check;
                    // parity alone is necessary but not sufficient.
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Hierholzer's algorithm",
            accentColor = 0xFF10B981,
            code = """
                fun eulerianTrail(edges: List<Pair<String, String>>, start: String): List<String> {
                    // Adjacency as mutable deques of edge ids, so each edge is removable once.
                    val adj = HashMap<String, ArrayDeque<Pair<String, Int>>>()
                    edges.forEachIndexed { id, (u, v) ->
                        adj.getOrPut(u) { ArrayDeque() }.add(v to id)
                        adj.getOrPut(v) { ArrayDeque() }.add(u to id)
                    }
                    val used = BooleanArray(edges.size)

                    val stack = ArrayDeque(listOf(start))
                    val trail = mutableListOf<String>()
                    while (stack.isNotEmpty()) {
                        val v = stack.last()
                        val q = adj[v]
                        // Drop edges already consumed from the other end.
                        while (q != null && q.isNotEmpty() && used[q.first().second]) q.removeFirst()
                        if (q == null || q.isEmpty()) {
                            // Stuck: this vertex closes a circuit, so it belongs at the end.
                            trail += stack.removeLast()
                        } else {
                            val (next, id) = q.removeFirst()
                            used[id] = true
                            stack.addLast(next)
                        }
                    }
                    return trail.reversed()   // popped in reverse, so the splicing is implicit
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF3B82F6, "Route Inspection", "Street sweeping, mail delivery and meter reading all want to traverse every street once — the Chinese postman problem starts here."),
        ApplicationCard("chip", 0xFF10B981, "Genome Assembly", "De Bruijn graph assemblers reconstruct a sequence by finding an Eulerian path through k-mer overlaps."),
        ApplicationCard("bulb", 0xFFEC4899, "Circuit Layout & Puzzles", "One-stroke drawing puzzles and single-pass wiring runs are the same parity question."),
    ),
    takeaways = listOf(
        "Existence is a degree count, not a search: all even → circuit, exactly two odd → path, otherwise none.",
        "With two odd vertices the endpoints are not a choice — the walk must start at one and finish at the other.",
        "Hierholzer builds the walk in O(V + E) by splicing circuits; Fleury's bridge-avoiding version costs O(E²).",
        "The directed analogue swaps parity for balance: in-degree equals out-degree everywhere for a circuit.",
    ),
    crossLinks = listOf(
        CrossLink("hamiltonian_path", "Hamiltonian Path & Circuit"),
        CrossLink("dfs", "Depth-First Search (DFS)"),
        CrossLink("articulation_points", "Articulation Points & Bridges"),
    ),
)
