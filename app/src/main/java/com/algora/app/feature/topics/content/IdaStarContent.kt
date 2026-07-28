package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val idaStarContent = TopicContent(
    topicId = "ida_star",
    whatIsIt = listOf(
        "IDA* — iterative-deepening A* — finds the same optimal path A* finds, using memory proportional to the depth of that path rather than to the number of states explored. It is what you reach for when A*'s frontier is the thing that fails, not A*'s running time.",
        "The mechanism is a depth-first search bounded by f-cost instead of by depth. Set the initial threshold to h(start), the cheapest the answer could possibly be. Run a plain recursive DFS that abandons any node whose f = g + h exceeds the threshold, and while abandoning them, record the smallest f it had to reject. If the search returns without a goal, that recorded minimum becomes the next threshold, and the search restarts from scratch. Each iteration therefore admits exactly the nodes with the next distinct f value, and the first iteration that reaches the goal reaches it at the optimal cost — provided h is admissible, exactly as for A*.",
        "Restarting from scratch sounds ruinous and usually is not, because the number of nodes in a search tree grows geometrically with the bound: the final iteration typically dominates the sum of all previous ones, so the re-expansion overhead is a constant factor. Where IDA* genuinely breaks down is when f-values are nearly all distinct — real-valued edge costs, for instance — because then each iteration raises the threshold enough to admit only a handful of new nodes and the number of iterations approaches the number of nodes. It also cannot detect duplicate states cheaply, since it keeps no closed list beyond the current path, so it revisits transpositions that A* would have collapsed. Korf's 1985 result solving random 15-puzzle instances is the canonical demonstration: A* ran out of memory, IDA* used O(d) and finished.",
    ),
    steps = listOf(
        StepCard(1, "Seed the Threshold", "Start at f = h(start) — the optimistic lower bound on the answer's cost.", 0xFF06B6D4),
        StepCard(2, "Depth-First Under the Bound", "Recurse, keeping only the current path on the stack. No frontier, no priority queue.", 0xFF3B82F6),
        StepCard(3, "Prune on f > threshold", "Abandon the branch immediately, and remember that f as a candidate next bound.", 0xFFEF4444),
        StepCard(4, "Raise to the Minimum Overshoot", "The next threshold is the smallest f that was pruned — never a guess or a fixed increment.", 0xFFF59E0B),
        StepCard(5, "Restart the Search", "Re-run from the start under the new bound. Earlier iterations are re-expanded and that is affordable.", 0xFF8B5CF6),
        StepCard(6, "Stop at the First Goal", "With an admissible h, the first goal found under any threshold is already optimal.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Evaluation", "f(n) = g(n) + h(n)", "Identical to A*; only the search order and the memory differ."),
        FormulaEntry("Initial bound", "threshold₀ = h(start)", "The tightest bound that cannot exclude the optimum."),
        FormulaEntry("Next bound", "min{ f(n) : f(n) > threshold }", "The smallest overshoot seen during the failed iteration."),
        FormulaEntry("Space", "O(d)", "Only the current path — against A*'s O(b^d) frontier."),
        FormulaEntry("Optimality", "h admissible", "Same requirement as A*; consistency is not needed."),
        FormulaEntry("Weak case", "many distinct f-values", "Iterations approach node count — real-valued costs are the classic trap."),
    ),
    notationKey = listOf(
        NotationEntry("g(n)", "cost of the path from the start to n"),
        NotationEntry("h(n)", "admissible estimate of the remaining cost — never an overestimate"),
        NotationEntry("threshold", "the f-cost ceiling for the current iteration"),
        NotationEntry("d", "depth of the optimal solution, which is IDA*'s memory bound"),
        NotationEntry("transposition", "the same state reached by two different paths — IDA* cannot cheaply detect these"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "IDA*",
            accentColor = 0xFF8B5CF6,
            code = """
                fun <S> idaStar(
                    start: S,
                    isGoal: (S) -> Boolean,
                    successors: (S) -> List<Pair<S, Int>>,
                    h: (S) -> Int,
                ): List<S>? {
                    val path = mutableListOf(start)

                    // Returns 0 on success, otherwise the smallest f-value it had to prune.
                    fun search(g: Int, threshold: Int): Int {
                        val node = path.last()
                        val f = g + h(node)
                        if (f > threshold) return f          // over budget — report the overshoot
                        if (isGoal(node)) return 0
                        var nextBound = Int.MAX_VALUE
                        for ((next, stepCost) in successors(node)) {
                            if (next in path) continue       // the only cycle check available
                            path += next
                            val result = search(g + stepCost, threshold)
                            if (result == 0) return 0
                            nextBound = minOf(nextBound, result)
                            path.removeAt(path.lastIndex)
                        }
                        return nextBound
                    }

                    var threshold = h(start)
                    while (true) {
                        when (val result = search(0, threshold)) {
                            0 -> return path.toList()
                            Int.MAX_VALUE -> return null     // nothing left to raise to
                            else -> threshold = result       // raise to the minimum overshoot
                        }
                    }
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The memory argument, made concrete",
            accentColor = 0xFF06B6D4,
            code = """
                // 15-puzzle, Manhattan-distance heuristic. Korf (1985) solved random
                // instances this way after A* exhausted memory on the same problems.
                //
                //   A*   : frontier holds O(b^d) states — millions of 16-byte boards,
                //          plus a closed set that grows without bound.
                //   IDA* : the recursion stack holds d states. For a 50-move solution
                //          that is 50 boards, whatever d costs to store.
                //
                // The trade is re-expansion. Because the tree grows geometrically with
                // the bound, the final iteration dominates and the overhead is a
                // constant factor — but only while f-values cluster. Give the same
                // search real-valued edge costs and each iteration admits a handful of
                // new nodes, turning that constant into something close to linear in
                // the node count.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PathfindingGrid,
    applications = listOf(
        ApplicationCard("chip", 0xFF8B5CF6, "Puzzle Solving", "15-puzzle and Rubik's cube solvers, where the state space is far larger than any frontier that fits in memory."),
        ApplicationCard("map", 0xFF06B6D4, "Memory-Bounded Planning", "Embedded and robotics planners with a fixed memory budget and integer-valued action costs."),
        ApplicationCard("bulb", 0xFFF59E0B, "Optimality Under Constraint", "The standard proof that you can keep A*'s optimality guarantee and pay for it in time instead of space."),
    ),
    takeaways = listOf(
        "IDA* is a depth-first search bounded by f = g + h, restarted at a higher bound each time it fails.",
        "The next threshold is the minimum f that was pruned — that is what makes each iteration admit exactly one more f-level.",
        "Memory drops from A*'s O(b^d) frontier to O(d) path, and optimality survives as long as h stays admissible.",
        "Re-expansion is a constant factor when f-values cluster and a disaster when they are all distinct.",
    ),
    crossLinks = listOf(
        CrossLink("a_star_search", "A* Search"),
        CrossLink("uniform_cost_search", "Uniform Cost Search"),
        CrossLink("dfs", "Depth-First Search (DFS)"),
    ),
)
