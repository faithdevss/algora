package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dStarAlgorithmContent = TopicContent(
    topicId = "d_star_algorithm",
    whatIsIt = listOf(
        "D* (Dynamic A*) is an incremental pathfinding algorithm for environments that change as you move — it repairs the existing path locally instead of replanning from scratch.",
        "Built for robots exploring unknown terrain, it plans from the goal backward and, when new obstacles appear, updates only the affected region, making it far cheaper than re-running A* each step.",
    ),
    steps = listOf(
        StepCard(1, "Plan from the Goal", "Compute an initial path working backward from the goal to the start.", 0xFF10B981),
        StepCard(2, "Start Moving", "Follow the path one step at a time toward the goal.", 0xFF3B82F6),
        StepCard(3, "Detect Changes", "When a sensor reveals a new obstacle or a changed edge cost, mark the affected nodes inconsistent.", 0xFFF59E0B),
        StepCard(4, "Repair Locally", "Propagate cost updates only where needed, reusing the rest of the previous plan.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Key idea", "incremental replanning", "Reuse prior results; fix only the changed region."),
        FormulaEntry("vs A*", "cheaper on updates", "Full A* replans O(whole map); D* touches the affected part."),
        FormulaEntry("Variants", "D*, Focussed D*, D* Lite", "D* Lite builds on LPA* and is the common modern choice."),
    ),
    notationKey = listOf(
        NotationEntry("g", "current cost-to-goal estimate of a node"),
        NotationEntry("rhs", "one-step lookahead value (LPA*/D* Lite)"),
        NotationEntry("inconsistent", "a node whose g and rhs disagree, needing repair"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "D* Lite — replanning loop (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                // High-level control loop; computeShortestPath() does the incremental repair.
                fun navigate() {
                    computeShortestPath()                 // initial plan from goal
                    while (current != goal) {
                        current = bestNeighbor(current)   // step along the path
                        move(current)
                        val changes = scanForEdgeChanges()
                        if (changes.isNotEmpty()) {
                            for (edge in changes) updateVertex(edge)   // mark inconsistent
                            computeShortestPath()          // repair only affected nodes
                        }
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PathfindingGrid,
    applications = listOf(
        ApplicationCard("robot", 0xFF10B981, "Autonomous Robots", "Mars rovers and mobile robots replan around newly-seen obstacles without stopping to recompute everything."),
        ApplicationCard("game", 0xFF3B82F6, "Dynamic Game Maps", "Units repath efficiently when terrain, doors, or hazards change mid-move."),
        ApplicationCard("map", 0xFFF59E0B, "Live Route Updates", "Navigation reacts to new road closures or traffic by repairing the current route."),
    ),
    takeaways = listOf(
        "D* replans incrementally for changing environments, repairing rather than recomputing paths.",
        "It plans from the goal backward so early progress stays valid as the start advances.",
        "It vastly outperforms repeated A* when only part of the map changes.",
        "D* Lite, built on LPA*, is the simpler and now-standard formulation.",
    ),
    crossLinks = listOf(
        CrossLink("a_star_search", "A* Search"),
        CrossLink("dijkstras_algorithm", "Dijkstra's Algorithm"),
    ),
)
