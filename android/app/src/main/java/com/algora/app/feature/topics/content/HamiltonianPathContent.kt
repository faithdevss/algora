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

internal val hamiltonianPathContent = TopicContent(
    topicId = "hamiltonian_path",
    figure = Figure(
        caption = "A six-cycle: every vertex has degree 2, and the Hamiltonian circuit is the graph " +
            "itself. Dirac's condition wants every degree to be at least n/2 = 3 and Ore's wants " +
            "non-adjacent degrees summing to at least 6; this fails both comfortably and is Hamiltonian " +
            "anyway. That is the shape of the whole problem — the sufficient conditions prove yes and " +
            "can never prove no, so a negative answer always costs a search. Swap \"edge\" for \"vertex\" " +
            "in the Eulerian definition and the statement barely changes while the difficulty changes " +
            "completely: Eulerian existence is a degree count in linear time, Hamiltonian existence is " +
            "NP-complete. Backtracking is O(n!) worst case; for weighted circuits Held-Karp trades that " +
            "for O(2ⁿ·n²) time at O(2ⁿ·n) memory, which moves the practical ceiling from about twelve " +
            "vertices to about twenty.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("1", 0.50f, 0.06f, FigureTone.Accent),
                FigureGraphNode("2", 0.86f, 0.29f, FigureTone.Accent),
                FigureGraphNode("3", 0.86f, 0.73f, FigureTone.Accent),
                FigureGraphNode("4", 0.50f, 0.94f, FigureTone.Accent),
                FigureGraphNode("5", 0.14f, 0.73f, FigureTone.Accent),
                FigureGraphNode("6", 0.14f, 0.29f, FigureTone.Accent),
            ),
            edges = listOf(
                FigureEdge(0, 1, tone = FigureTone.Accent),
                FigureEdge(1, 2, tone = FigureTone.Accent),
                FigureEdge(2, 3, tone = FigureTone.Accent),
                FigureEdge(3, 4, tone = FigureTone.Accent),
                FigureEdge(4, 5, tone = FigureTone.Accent),
                FigureEdge(5, 0, tone = FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A Hamiltonian path visits every vertex exactly once; a Hamiltonian circuit is one that closes back to its start. Swapping the word \"edge\" for \"vertex\" in the Eulerian definition changes almost nothing about the statement and everything about the difficulty.",
        "Eulerian existence is settled by counting degrees in linear time. Hamiltonian existence is NP-complete, and no degree condition decides it. There are sufficient conditions — Dirac's, that every vertex has degree at least n/2, and Ore's, that every non-adjacent pair has degrees summing to at least n — but they are one-way. A graph can fail both comfortably and still be Hamiltonian; a cycle on ten vertices has every degree equal to 2 and is trivially Hamiltonian while satisfying neither. So the conditions prove yes and never prove no, which is why the general algorithm is search.",
        "The search is backtracking: extend the current path to any unvisited neighbour, and when a vertex has none left, undo the last step and try the next alternative. Worst case is O(n!) and the pruning that helps in practice is all about failing early — reject a vertex of degree 1 that is not an endpoint, reject when the unvisited part of the graph splits into two components, and order candidates by fewest remaining options first. For circuits over weighted graphs the same question becomes the travelling salesman problem, and there the Held-Karp dynamic program trades factorial time for O(2ⁿ·n²) at O(2ⁿ·n) memory — still exponential, but exponential in a base you can push to about twenty vertices instead of twelve.",
    ),
    steps = listOf(
        StepCard(1, "Pick a Start", "For a circuit any vertex works by symmetry; for a path you may have to try each one.", 0xFF3B82F6),
        StepCard(2, "Extend to an Unvisited Neighbour", "Append a neighbour not already on the path and mark it visited.", 0xFF10B981),
        StepCard(3, "Recurse Deeper", "Repeat from the new endpoint. The path grows one vertex per level of recursion.", 0xFFF59E0B),
        StepCard(4, "Backtrack on a Dead End", "No unvisited neighbour and fewer than n vertices placed — undo the last step and take the next candidate.", 0xFFEF4444),
        StepCard(5, "Prune Before Recursing", "Reject early if the unvisited vertices are disconnected, or if a degree-1 vertex is stranded mid-path.", 0xFF8B5CF6),
        StepCard(6, "Close or Report", "At n vertices you have a path; if an edge runs back to the start it is also a circuit.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Decision complexity", "NP-complete", "For both the path and the circuit version."),
        FormulaEntry("Backtracking", "O(n!) worst case", "Pruning changes the constant and the practical ceiling, not the bound."),
        FormulaEntry("Held-Karp (TSP)", "O(2ⁿ·n²) time, O(2ⁿ·n) space", "The bitmask DP — exponential but tractable to roughly n = 20."),
        FormulaEntry("Dirac's condition", "deg(v) ≥ n/2 for all v, n ≥ 3", "Sufficient for a circuit; not necessary."),
        FormulaEntry("Ore's condition", "deg(u) + deg(v) ≥ n for all non-adjacent u,v", "Strictly weaker premise than Dirac's, same conclusion."),
        FormulaEntry("Contrast", "Eulerian: O(V+E)", "Same question about edges instead of vertices, and it is linear."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of vertices; a Hamiltonian path has exactly n of them"),
        NotationEntry("path vs circuit", "circuit additionally requires an edge from the last vertex back to the first"),
        NotationEntry("sufficient condition", "proves existence when it holds; says nothing when it fails"),
        NotationEntry("bitmask state", "(visited set, current vertex) — the Held-Karp state, 2ⁿ·n of them"),
        NotationEntry("pruning", "rejecting a partial path before recursing, on a cheaper test than the full search"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Backtracking search",
            accentColor = 0xFF10B981,
            code = """
                fun hamiltonianCircuit(adj: Map<String, List<String>>, start: String): List<String>? {
                    val n = adj.size
                    val path = mutableListOf(start)
                    val visited = mutableSetOf(start)

                    fun extend(): Boolean {
                        if (path.size == n) {
                            // Every vertex placed — a circuit also needs the closing edge.
                            return start in adj.getValue(path.last())
                        }
                        for (next in adj.getValue(path.last())) {
                            if (next in visited) continue
                            path += next
                            visited += next
                            if (extend()) return true
                            path.removeAt(path.lastIndex)   // undo and try the next candidate
                            visited -= next
                        }
                        return false                        // dead end: caller backtracks
                    }

                    return if (extend()) path else null
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Held-Karp: the same question as a bitmask DP",
            accentColor = 0xFF8B5CF6,
            code = """
                // Exists a Hamiltonian path ending at each vertex, over each visited subset.
                // 2^n * n states instead of n! orderings — the states collapse every
                // permutation that visits the same set and ends in the same place.
                fun hasHamiltonianPath(adj: Array<BooleanArray>): Boolean {
                    val n = adj.size
                    val dp = Array(1 shl n) { BooleanArray(n) }
                    for (v in 0 until n) dp[1 shl v][v] = true   // single-vertex paths

                    for (mask in 1 until (1 shl n)) {
                        for (v in 0 until n) {
                            if (!dp[mask][v]) continue
                            for (u in 0 until n) {
                                if (mask and (1 shl u) != 0 || !adj[v][u]) continue
                                dp[mask or (1 shl u)][u] = true
                            }
                        }
                    }
                    val full = (1 shl n) - 1
                    return (0 until n).any { dp[full][it] }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF3B82F6, "Route & Delivery Planning", "Visiting every stop once is the Hamiltonian circuit; adding distances makes it the travelling salesman problem."),
        ApplicationCard("chip", 0xFF8B5CF6, "Sequencing & Scheduling", "DNA fragment ordering and job sequencing with changeover costs both reduce to finding a Hamiltonian order."),
        ApplicationCard("bulb", 0xFFEF4444, "Complexity Boundary", "The textbook pair with Eulerian paths: an almost identical question that lands on the other side of P."),
    ),
    takeaways = listOf(
        "Every vertex once, not every edge once — and that single word change makes the problem NP-complete.",
        "Dirac's and Ore's conditions prove a circuit exists; failing them proves nothing at all.",
        "Backtracking is O(n!) in the worst case, and the useful pruning is connectivity and degree checks before recursing.",
        "Held-Karp reformulates it as a bitmask DP at O(2ⁿ·n²), which is what makes twenty vertices practical.",
    ),
    crossLinks = listOf(
        CrossLink("eulerian_path", "Eulerian Path & Circuit"),
        CrossLink("bitmask_dp", "Bitmask DP"),
        CrossLink("n_queens", "N-Queens"),
    ),
)
