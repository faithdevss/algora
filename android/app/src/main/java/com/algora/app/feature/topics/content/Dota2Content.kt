package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dota2Content = TopicContent(
    topicId = "dota2",
    whatIsIt = listOf(
        "Dota 2 is a five-versus-five team game that became a landmark RL challenge for cooperation, long horizons, and partial information at massive scale.",
        "OpenAI Five beat the human world champions using PPO scaled across thousands of GPUs with self-play, showing that a simple algorithm plus enormous compute can master team coordination.",
    ),
    steps = listOf(
        StepCard(1, "Team Coordination", "Five agents must cooperate toward a shared objective.", 0xFF818CF8),
        StepCard(2, "Long Horizons", "Games span tens of thousands of decisions with delayed payoffs.", 0xFF60A5FA),
        StepCard(3, "Massive-Scale PPO", "Train with PPO and self-play across thousands of GPUs for months.", 0xFF10B981),
        StepCard(4, "Emergent Teamwork", "Coordinated strategies arise from shared reward and self-play, not explicit design.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Algorithm", "PPO + self-play", "Simple method, extreme scale."),
        FormulaEntry("Horizon", "tens of thousands of steps", "Long-term credit assignment."),
        FormulaEntry("Team", "5 cooperating agents", "Shared-reward coordination."),
    ),
    notationKey = listOf(
        NotationEntry("OpenAI Five", "the agent that beat human champions"),
        NotationEntry("team reward", "shared objective driving cooperation"),
        NotationEntry("scale", "thousands of GPUs, months of training"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Why Dota 2 pushed RL (summary)",
            accentColor = 0xFF6366F1,
            code = """
                # OpenAI Five: a demonstration that scale + a simple algorithm work.
                #   - 5v5 team cooperation under partial information
                #   - horizons of ~20,000 decisions per game
                #   - PPO + self-play across thousands of GPUs
                #   - defeated the human world champions (OG) in 2019
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.EnvironmentPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "OpenAI Five", "First to beat esports world champions at a complex team game."),
        ApplicationCard("users", 0xFF60A5FA, "Cooperative Multi-Agent", "A benchmark for large-scale team coordination."),
        ApplicationCard("chip", 0xFF10B981, "Scaling RL", "Showed that scale can substitute for algorithmic novelty."),
    ),
    takeaways = listOf(
        "Dota 2 tests team cooperation, long horizons, and partial information at scale.",
        "OpenAI Five mastered it with PPO plus self-play on massive compute.",
        "Coordinated teamwork emerged from shared reward, not hand-coding.",
        "It's the headline case that scaling a simple algorithm can conquer hard games.",
    ),
    crossLinks = listOf(
        CrossLink("ppo", "PPO"),
        CrossLink("self_play", "Self-Play"),
    ),
)
