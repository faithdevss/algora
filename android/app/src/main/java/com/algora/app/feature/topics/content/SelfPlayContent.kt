package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val selfPlayContent = TopicContent(
    topicId = "self_play",
    whatIsIt = listOf(
        "Self-play trains an agent by having it compete against copies of itself, so its opponent automatically scales in difficulty as the agent improves.",
        "This creates an automatic curriculum — always facing an evenly-matched rival — and is how AlphaZero and OpenAI Five reached superhuman skill.",
    ),
    steps = listOf(
        StepCard(1, "Play Against Yourself", "The agent's current (or past) policies serve as opponents.", 0xFF818CF8),
        StepCard(2, "Learn from the Games", "Update the policy with RL on the resulting match outcomes.", 0xFF60A5FA),
        StepCard(3, "Auto-Scaling Difficulty", "As the agent improves, so does its opponent — an ever-harder curriculum.", 0xFF10B981),
        StepCard(4, "Maintain a Pool", "Keep a league of past versions to avoid cycles and forgetting.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Opponent", "π_opp = past or current π", "The agent plays versions of itself."),
        FormulaEntry("Curriculum", "difficulty tracks skill", "Always an even match."),
        FormulaEntry("Stability", "opponent pool / league", "Prevents chasing and forgetting."),
    ),
    notationKey = listOf(
        NotationEntry("self-play", "training against copies of oneself"),
        NotationEntry("league", "a pool of past agent versions"),
        NotationEntry("curriculum", "automatically increasing difficulty"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Self-play training loop (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                pool = [policy.copy()]
                for _ in range(iterations):
                    opponent = sample(pool)              # a past or current self
                    games = play(policy, opponent)
                    policy.update(games)                 # RL on outcomes
                    if improved(policy): pool.append(policy.copy())
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "AlphaZero & OpenAI Five", "Superhuman Go, chess, and Dota 2 emerged largely from self-play."),
        ApplicationCard("users", 0xFF60A5FA, "Competitive MARL", "Trains robust strategies in adversarial multi-agent games."),
        ApplicationCard("robot", 0xFF10B981, "Emergent Skills", "Self-play produced tool use and complex tactics in hide-and-seek."),
    ),
    takeaways = listOf(
        "Self-play trains an agent against versions of itself for an auto-scaling curriculum.",
        "The opponent's difficulty always tracks the agent's own skill.",
        "A league of past versions prevents strategy cycling and forgetting.",
        "It drove landmark superhuman results in Go, chess, and Dota 2.",
    ),
    crossLinks = listOf(
        CrossLink("alphazero", "AlphaZero"),
        CrossLink("minimax", "Minimax"),
    ),
)
