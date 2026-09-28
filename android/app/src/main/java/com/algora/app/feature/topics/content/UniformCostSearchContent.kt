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

internal val uniformCostSearchContent = TopicContent(
    topicId = "uniform_cost_search",
    figure = Figure(
        caption = "G is *generated* the instant S is expanded, at a cost of 10. Testing for the goal " +
            "right there returns 10 and is simply wrong — which is the mistake, because that test is " +
            "correct for breadth-first search on unit costs and people carry it over. UCS tests when a " +
            "node is *popped*, the point at which nothing cheaper can still be pending, and G comes off " +
            "the queue at 3 by way of A. The same reasoning forces the second rule: a state regenerated " +
            "more cheaply while still on the frontier must have its priority lowered, not be enqueued " +
            "again. The relaxation is Dijkstra's exactly; what differs is that the state space is " +
            "produced on demand by a successor function rather than written down, so UCS runs on spaces " +
            "too large or infinite to store and stops at the goal instead of settling everything. Its " +
            "bound is O(b^(1 + C*/ε)), not b^d — tiny step costs cost far more than the depth " +
            "suggests, and ε = 0 can mean it never terminates at all.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("S", 0.08f, 0.62f, FigureTone.Primary),
                FigureGraphNode("A", 0.50f, 0.14f, FigureTone.Primary),
                FigureGraphNode("G", 0.92f, 0.62f, FigureTone.Accent),
            ),
            edges = listOf(
                FigureEdge(0, 2, "10 · first seen", directed = true, tone = FigureTone.Warn),
                FigureEdge(0, 1, "1", directed = true, tone = FigureTone.Accent),
                FigureEdge(1, 2, "2", directed = true, tone = FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Uniform cost search expands the unexpanded state with the lowest path cost from the start, over and over, until the goal is the cheapest thing on the frontier. It is best-first search with the evaluation function f(n) = g(n) — that is, A* with the heuristic switched off.",
        "Its relaxation step is Dijkstra's, identically. The difference is what each is for. Dijkstra is stated over an explicit graph and computes the distance to every vertex; UCS is stated over a state space that is generated on demand by a successor function, has a goal test, and stops the moment the goal is popped. That makes UCS usable where the graph is too large to write down or outright infinite — a puzzle's configuration space, a planner's world states — and it makes early termination the normal case rather than an optimisation.",
        "The one detail people get wrong is where the goal test goes. Testing at generation time — the moment a successor is created — is correct for breadth-first search on unit costs and wrong here, because a goal reached by an expensive edge can be generated long before a cheaper route to it is found. UCS tests when a node is *popped*, which is the point at which its cost is known to be minimal. The same reasoning forces a second rule: if a state is regenerated with a lower cost while still on the frontier, its priority must be lowered rather than the duplicate simply enqueued. Complexity is not the textbook b^d either — it is O(b^(1+⌊C*/ε⌋)), where C* is the optimal solution cost and ε the smallest step cost, so tiny step costs can be far more expensive than the depth suggests. If ε is zero, UCS can fail to terminate at all.",
    ),
    steps = listOf(
        StepCard(1, "Push the Start at Cost 0", "The frontier is a priority queue ordered by g — cost from the start, nothing else.", 0xFF06B6D4),
        StepCard(2, "Pop the Cheapest", "Remove the lowest-g state. Its cost is now final; no later route can beat it.", 0xFF3B82F6),
        StepCard(3, "Goal-Test on Pop", "Test here, never at generation. A goal generated early may still have a cheaper route pending.", 0xFFEF4444),
        StepCard(4, "Generate Successors", "Ask the successor function for children — the state space is never materialised in advance.", 0xFF10B981),
        StepCard(5, "Add or Lower the Priority", "New states are enqueued; states already on the frontier with a worse g get their priority reduced.", 0xFFF59E0B),
        StepCard(6, "Skip the Explored Set", "A state already popped is done. Without that check the frontier grows without bound on cyclic spaces.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Evaluation", "f(n) = g(n)", "A* with h ≡ 0; the goal exerts no pull on the ordering."),
        FormulaEntry("Time & space", "O(b^(1+⌊C*/ε⌋))", "Not b^d — small step costs inflate the exponent."),
        FormulaEntry("Optimality", "requires step costs ≥ 0", "And ε > 0 for completeness on an infinite space."),
        FormulaEntry("Goal test", "on pop, not on generation", "Testing at generation returns the first goal found, not the cheapest."),
        FormulaEntry("Dijkstra equivalence", "same relaxation, explicit graph", "Dijkstra settles every vertex; UCS stops at the goal."),
        FormulaEntry("Frontier operations", "O(log n) push/pop", "A binary heap with decrease-key, or lazy deletion with duplicates."),
    ),
    notationKey = listOf(
        NotationEntry("g(n)", "cost of the best known path from the start to n"),
        NotationEntry("C*", "cost of the optimal solution"),
        NotationEntry("ε", "the smallest positive step cost in the space"),
        NotationEntry("b", "branching factor — successors generated per state"),
        NotationEntry("frontier / explored", "states generated but not yet expanded / states already expanded"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Uniform cost search over a generated state space",
            accentColor = 0xFF06B6D4,
            code = """
                fun <S> uniformCostSearch(
                    start: S,
                    isGoal: (S) -> Boolean,
                    successors: (S) -> List<Pair<S, Int>>,   // the space is never listed up front
                ): Pair<List<S>, Int>? {
                    val best = hashMapOf(start to 0)
                    val cameFrom = hashMapOf<S, S>()
                    val explored = hashSetOf<S>()
                    val frontier = java.util.PriorityQueue<Pair<S, Int>>(compareBy { it.second })
                    frontier += start to 0

                    while (frontier.isNotEmpty()) {
                        val (state, cost) = frontier.poll()
                        if (state in explored) continue          // stale duplicate, already settled
                        // Goal test on POP. Testing at generation would return a goal reached
                        // by an expensive edge before a cheaper route to it was discovered.
                        if (isGoal(state)) {
                            val path = generateSequence(state) { cameFrom[it] }.toList().reversed()
                            return path to cost
                        }
                        explored += state

                        for ((next, stepCost) in successors(state)) {
                            if (next in explored) continue
                            val candidate = cost + stepCost
                            if (candidate < (best[next] ?: Int.MAX_VALUE)) {
                                best[next] = candidate
                                cameFrom[next] = state
                                frontier += next to candidate    // lazy decrease-key
                            }
                        }
                    }
                    return null
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the goal test cannot move",
            accentColor = 0xFFEF4444,
            code = """
                // Start S, goal G.
                //   S --10--> G        (one expensive hop)
                //   S --1--> A --1--> G  (two cheap hops, total 2)
                //
                // Expanding S generates BOTH successors: G at cost 10 and A at cost 1.
                // Testing at generation time reports G immediately, cost 10 — wrong.
                //
                // Testing on pop: the queue holds [A:1, G:10]. A pops first, generates
                // G at cost 2, which supersedes the entry at 10. G then pops at 2.

                val space = mapOf(
                    "S" to listOf("G" to 10, "A" to 1),
                    "A" to listOf("G" to 1),
                    "G" to emptyList(),
                )
                uniformCostSearch("S", { it == "G" }, { space.getValue(it) })
                // → ([S, A, G], 2)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PathfindingGrid,
    applications = listOf(
        ApplicationCard("map", 0xFF06B6D4, "Routing Without a Heuristic", "Road and transit networks where the cost is time or fare and no admissible distance estimate is available."),
        ApplicationCard("chip", 0xFF3B82F6, "Planning Over Generated States", "Puzzle and task planners where the state space is defined by a successor function and never enumerated."),
        ApplicationCard("bulb", 0xFFF59E0B, "A* Baseline", "The h ≡ 0 control case: run it beside A* and the expansion counts measure exactly what the heuristic bought."),
    ),
    takeaways = listOf(
        "UCS is best-first search on f = g — the same relaxation as Dijkstra, framed as a goal-directed search.",
        "Dijkstra settles every vertex of an explicit graph; UCS generates states lazily and halts when the goal pops.",
        "The goal test must happen on pop; testing at generation returns the first goal found, not the cheapest.",
        "Its real bound is O(b^(1+⌊C*/ε⌋)) — with very small step costs that is far worse than the depth would suggest.",
    ),
    crossLinks = listOf(
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
        CrossLink("a_star_search", "A* Search"),
        CrossLink("ida_star", "IDA*"),
    ),
)
