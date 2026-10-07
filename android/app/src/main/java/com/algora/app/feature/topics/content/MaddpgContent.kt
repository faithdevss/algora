package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val maddpgContent = TopicContent(
    topicId = "maddpg",
    figure = Figure(
        caption = "The page's lab: two agents each choosing a real number, rewarded by " +
            "r = 1.6·a₁a₂ + a₁ + a₂ − a₁² − a₂² − 0.5, so each one's best action depends on the " +
            "other's; the optimum is 1.10 at (1, 1). With independent critics, each agent learns a " +
            "value for its own action with the partner hidden in the noise, from a replay buffer " +
            "full of the partner's old behaviour — at update 10 its gradient is 0.84 where the " +
            "truth is 0.60. A centralised critic sees both actions, so its gradient is right. " +
            "Averaged over 30 runs with exploration noise σ = 0.1, the centralised critics reach " +
            "the optimum and earn 1.10 within 15 updates; the independent ones are still 1.02 away " +
            "and earn −0.08. At run time both versions act on their own observations only.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.84", "1.02", "−0.08"),
                listOf("0.60", "0.00", "1.10"),
            ),
            rowHeaders = listOf("independent", "centralised"),
            colHeaders = listOf("∂Q/∂a₁ (true 0.60)", "distance to optimum", "reward"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(0, 2, FigureTone.Warn),
                FigureCell(1, 2, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "MADDPG extends DDPG to several agents with one idea: centralised training, decentralised execution. Each agent keeps its own actor, which at run time sees only its own observation. But during training each agent's critic is given every agent's observation and action, so the value it learns does not change underneath it when the other agents change their behaviour.",
        "The lab shows why that matters on a continuous two-agent task: each agent picks a real number, and the reward r = 1.6·a₁a₂ + a₁ + a₂ − a₁² − a₂² − 0.5 couples them through the cross term, so each agent's best action depends on the other's; the optimum is 1.10 at (1, 1). An independent critic for agent 1 learns Q₁(a₁) with its partner hidden in the noise, from a replay buffer full of the partner's old, different behaviour — at update 10 its gradient ∂Q/∂a₁ is 0.84 where the true one is 0.60. A centralised critic that sees both actions gets 0.60 exactly.",
        "The consequence shows up in the result. Averaged over 30 runs with exploration noise σ = 0.1, the centralised critics walk straight to (1, 1) and earn the maximum 1.10 after 15 updates; independent critics are still 1.02 away from the optimum and earn −0.08. Non-stationarity — every agent's environment includes other learning agents — is the central problem of multi-agent RL, and giving the critic the joint action is MADDPG's way around it without sacrificing decentralised execution.",
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
        "In the lab after 15 updates, centralised critics reach the optimum reward 1.10; independent critics are still 1.02 away and earn −0.08.",
    ),
    crossLinks = listOf(
        CrossLink("ddpg", "DDPG"),
        CrossLink("qmix", "QMIX"),
    ),
)
