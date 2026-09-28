package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val maddpgContent = TopicContent(
    topicId = "maddpg",
    whatIsIt = listOf(
        "MADDPG (Multi-Agent DDPG) extends DDPG to mixed cooperative-competitive settings using centralized critics that see all agents but decentralized actors that see only local observations.",
        "Giving each critic access to every agent's actions during training makes the environment stationary from its perspective, taming the instability that plagues independent learners.",
    ),
    steps = listOf(
        StepCard(1, "Decentralized Actors", "Each agent's policy uses only its own observation.", 0xFF818CF8),
        StepCard(2, "Centralized Critics", "Each agent's critic sees all agents' observations and actions during training.", 0xFF60A5FA),
        StepCard(3, "Stationary Learning", "With others' actions as inputs, each critic faces a stationary target.", 0xFF10B981),
        StepCard(4, "Execute Independently", "At run time only the local actors are used — critics are dropped.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Central critic", "Qᵢ(x, a₁,…,aₙ)", "Conditioned on all agents' actions."),
        FormulaEntry("CTDE", "central critics, local actors", "Train globally, act locally."),
        FormulaEntry("Handles", "coop, competitive, mixed", "Works across interaction types."),
    ),
    notationKey = listOf(
        NotationEntry("x", "joint observations/state of all agents"),
        NotationEntry("central critic", "value conditioned on all actions"),
        NotationEntry("mixed setting", "both cooperative and competitive agents"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "MADDPG centralized critic",
            accentColor = 0xFF6366F1,
            code = """
                # Critic i sees the full joint action; actor i sees only its own obs.
                q_i = critic_i(all_obs, all_actions)          # centralized
                a_i = actor_i(obs_i)                           # decentralized
                # target uses target actors of every agent for the next joint action.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.MultiAgentPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF818CF8, "Mixed Cooperation/Competition", "Predator-prey, negotiation, and team-vs-team scenarios."),
        ApplicationCard("game", 0xFF60A5FA, "Continuous Multi-Agent", "Extends continuous-action control to many interacting agents."),
        ApplicationCard("robot", 0xFF10B981, "Multi-Robot Systems", "Coordinated continuous control with local execution."),
    ),
    takeaways = listOf(
        "MADDPG uses centralized critics and decentralized actors (CTDE).",
        "Conditioning critics on all actions restores a stationary learning target.",
        "It handles cooperative, competitive, and mixed settings with continuous actions.",
        "Critics are training-only; execution needs just the local actors.",
    ),
    crossLinks = listOf(
        CrossLink("ddpg", "DDPG"),
        CrossLink("qmix", "QMIX"),
    ),
)
