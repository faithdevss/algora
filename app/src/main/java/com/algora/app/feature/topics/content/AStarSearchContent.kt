package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val aStarSearchContent = TopicContent(
    topicId = "a_star_search",
    whatIsIt = listOf(
        "A* is a best-first pathfinding search that finds the shortest path from a start to a goal by combining the actual cost so far with a heuristic estimate of the cost remaining.",
        "It's Dijkstra's algorithm with a sense of direction: the heuristic steers the search toward the goal, expanding far fewer nodes while staying optimal when the heuristic never overestimates.",
    ),
    steps = listOf(
        StepCard(1, "Score Each Node", "f(n) = g(n) + h(n): known cost from the start plus the estimated cost to the goal.", 0xFF10B981),
        StepCard(2, "Expand the Lowest f", "Always pull the node with the smallest f from a priority queue.", 0xFF3B82F6),
        StepCard(3, "Relax Neighbors", "Update each neighbor's g if a cheaper path is found, and recompute its f.", 0xFFF59E0B),
        StepCard(4, "Stop at the Goal", "When the goal is expanded, reconstruct the path via stored parent pointers.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Evaluation", "f(n) = g(n) + h(n)", "Cost so far plus heuristic-to-go."),
        FormulaEntry("Admissible h", "h(n) ≤ true remaining cost", "Never overestimating guarantees an optimal path."),
        FormulaEntry("Degenerates to", "Dijkstra when h = 0", "No heuristic means uniform-cost search."),
    ),
    notationKey = listOf(
        NotationEntry("g(n)", "actual cost from start to n"),
        NotationEntry("h(n)", "heuristic estimate from n to the goal"),
        NotationEntry("f(n)", "g(n) + h(n), the priority key"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A* search",
            accentColor = 0xFF6366F1,
            code = """
                fun aStar(
                    start: Int, goal: Int,
                    neighbors: (Int) -> List<Pair<Int, Int>>,   // node -> (next, cost)
                    h: (Int) -> Int,
                ): Int {
                    val g = HashMap<Int, Int>().apply { put(start, 0) }
                    val open = java.util.PriorityQueue<Pair<Int, Int>>(compareBy { it.second })
                    open.add(start to h(start))
                    while (open.isNotEmpty()) {
                        val (u, _) = open.poll()
                        if (u == goal) return g[u]!!
                        for ((v, cost) in neighbors(u)) {
                            val tentative = g[u]!! + cost
                            if (tentative < (g[v] ?: Int.MAX_VALUE)) {
                                g[v] = tentative
                                open.add(v to tentative + h(v))
                            }
                        }
                    }
                    return -1
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PathfindingGrid,
    applications = listOf(
        ApplicationCard("game", 0xFF10B981, "Game Pathfinding", "NPCs navigate tile and nav-mesh maps with A* using distance-to-goal heuristics."),
        ApplicationCard("map", 0xFF3B82F6, "Route Planning", "Map services guide road-network search toward the destination to prune the frontier."),
        ApplicationCard("robot", 0xFFF59E0B, "Robotics Motion", "Grid and lattice planners steer robots around obstacles toward a target pose."),
    ),
    takeaways = listOf(
        "A* ranks nodes by f = g + h, blending real cost with a heuristic estimate.",
        "An admissible (non-overestimating) heuristic makes it both optimal and efficient.",
        "With h = 0 it becomes Dijkstra; a sharper heuristic expands fewer nodes.",
        "The heuristic's quality is everything — it's the difference between A* and a blind search.",
    ),
    crossLinks = listOf(
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
        CrossLink("d_star_algorithm", "D* Algorithm"),
    ),
)
