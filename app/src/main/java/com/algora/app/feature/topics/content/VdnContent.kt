package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val vdnContent = TopicContent(
    topicId = "vdn",
    whatIsIt = listOf(
        "Value Decomposition Networks (VDN) train cooperative agents by assuming the team's joint value is simply the sum of each agent's individual value.",
        "It's the first clean answer to centralized-training/decentralized-execution: agents learn together from a shared team reward, then act independently on their own value functions.",
    ),
    steps = listOf(
        StepCard(1, "Per-Agent Q-Values", "Each agent produces an individual Q(oᵢ, aᵢ) from its local observation.", 0xFF818CF8),
        StepCard(2, "Sum for the Team", "The joint action-value is the sum of the agents' individual Q-values.", 0xFF60A5FA),
        StepCard(3, "Train on Team Reward", "Backprop the team's TD error through the summed value into each agent.", 0xFF10B981),
        StepCard(4, "Decentralized Execution", "Each agent acts greedily on its own Q — no communication needed at run time.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Decomposition", "Q_tot = Σᵢ Qᵢ(oᵢ, aᵢ)", "Team value is a sum of individuals."),
        FormulaEntry("CTDE", "central train, decentral act", "Shared learning, independent execution."),
        FormulaEntry("Limitation", "additive only", "Can't represent non-linear agent interactions."),
    ),
    notationKey = listOf(
        NotationEntry("Q_tot", "joint team action-value"),
        NotationEntry("Qᵢ", "agent i's individual value"),
        NotationEntry("CTDE", "centralized training, decentralized execution"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "VDN value decomposition",
            accentColor = 0xFF6366F1,
            code = """
                # Each agent's individual Q, summed into a team value trained on shared reward.
                q_individual = [agent_net[i](obs[i]).gather(1, act[i]) for i in range(n)]
                q_tot = torch.stack(q_individual, dim=0).sum(dim=0)
                loss = F.mse_loss(q_tot, team_td_target)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.MultiAgentPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF818CF8, "Cooperative Teams", "Coordinating agents that share one team reward."),
        ApplicationCard("game", 0xFF60A5FA, "Multi-Agent Games", "StarCraft micromanagement and other team-vs-team tasks."),
        ApplicationCard("robot", 0xFF10B981, "Swarm Coordination", "Robot teams learning jointly but acting locally."),
    ),
    takeaways = listOf(
        "VDN decomposes a team value into a sum of per-agent values.",
        "It enables centralized training with decentralized execution.",
        "The additive form can't capture non-linear agent interactions.",
        "QMIX generalizes it to a richer, monotonic mixing function.",
    ),
    crossLinks = listOf(
        CrossLink("iql", "Independent Q-Learning"),
        CrossLink("qmix", "QMIX"),
    ),
)
