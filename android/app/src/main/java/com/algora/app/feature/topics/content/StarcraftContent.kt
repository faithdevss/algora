package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val starcraftContent = TopicContent(
    topicId = "starcraft",
    whatIsIt = listOf(
        "StarCraft II is a grand-challenge RL environment: a real-time strategy game with imperfect information, an enormous action space, and long time horizons.",
        "DeepMind's AlphaStar reached Grandmaster level using deep RL, self-play leagues, and imitation from human games — a milestone for complex, partially-observed decision making.",
    ),
    steps = listOf(
        StepCard(1, "Partial Observability", "Fog of war hides the opponent — the agent must act under uncertainty.", 0xFF818CF8),
        StepCard(2, "Vast Action Space", "Thousands of possible unit and building commands each step.", 0xFF60A5FA),
        StepCard(3, "Long Horizons", "Games last thousands of steps, demanding long-term strategy and credit assignment.", 0xFF10B981),
        StepCard(4, "League Self-Play", "AlphaStar trained a league of diverse agents to avoid exploitable strategies.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Observation", "partial (fog of war)", "Imperfect information."),
        FormulaEntry("Action space", "~10²⁶ combinations", "Enormous and structured."),
        FormulaEntry("Training", "imitation + league self-play", "Human bootstrap, then leagues."),
    ),
    notationKey = listOf(
        NotationEntry("fog of war", "hidden opponent information"),
        NotationEntry("league", "a population of diverse self-play agents"),
        NotationEntry("APM", "actions per minute — an interface constraint"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The StarCraft II challenge (why it's hard)",
            accentColor = 0xFF6366F1,
            code = """
                # Unlike Chess/Go, StarCraft II combines:
                #   - partial observability (fog of war)
                #   - a combinatorial, structured action space (~10^26)
                #   - long horizons (thousands of decisions per game)
                #   - real-time play and multiple viable strategies
                # AlphaStar: supervised imitation + multi-agent league self-play.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.EnvironmentPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "AlphaStar", "Reached Grandmaster rank, a landmark for complex-game RL."),
        ApplicationCard("users", 0xFF60A5FA, "Multi-Agent Strategy", "A testbed for partial-information, long-horizon coordination."),
        ApplicationCard("bulb", 0xFF10B981, "League Training", "Popularized league-based self-play to avoid strategic cycles."),
    ),
    takeaways = listOf(
        "StarCraft II combines partial observability, huge action spaces, and long horizons.",
        "AlphaStar reached Grandmaster via imitation plus league self-play.",
        "It tests decision making far beyond perfect-information board games.",
        "League self-play was key to producing robust, non-exploitable strategies.",
    ),
    crossLinks = listOf(
        CrossLink("self_play", "Self-Play"),
        CrossLink("qmix", "QMIX"),
    ),
)
