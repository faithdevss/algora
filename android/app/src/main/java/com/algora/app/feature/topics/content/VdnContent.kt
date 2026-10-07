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

internal val vdnContent = TopicContent(
    topicId = "vdn",
    figure = Figure(
        caption = "The page's lab: three team payoffs, each fitted by VDN's assumption that the team " +
            "value is a sum of the agents' own utilities, Q_tot = Q₁(a₁) + Q₂(a₂). An additive " +
            "game — every cell a row value plus a column value — is matched exactly, and each " +
            "agent's local argmax plays the true optimum. A product game cannot be written as a " +
            "sum; the best additive fit misses by a mean squared error of 0.444, but the ranking " +
            "survives and VDN still plays the optimum. A penalty game, where one action pays 8 only " +
            "if the partner plays its half and loses 12 otherwise, is the case a sum cannot " +
            "handle: the fit error is 50.6 and the local choices miss the optimum. QMIX relaxes " +
            "the sum to any monotone mix, which fixes the product game but not the penalty game.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.000", "✓"),
                listOf("0.444", "✓"),
                listOf("50.6", "✗"),
            ),
            rowHeaders = listOf("additive", "product", "penalty"),
            colHeaders = listOf("fit error (MSE)", "plays optimum"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(1, 1, FigureTone.Accent),
                FigureCell(2, 1, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "VDN — Value Decomposition Networks — is the simplest way to train a cooperative team with one shared reward. Each agent keeps its own utility table or network Qᵢ(aᵢ), and the team value is modelled as their sum, Q_tot = Q₁ + Q₂. One team reward trains the sum end to end; at run time each agent simply takes its own argmax with no communication, and because the sum is maximised by maximising each term, those local choices are the team's best joint action.",
        "The lab tests how far a sum can go on three team payoffs. On an additive game — every cell a row value plus a column value — VDN's fit is exact, its utilities come out as Q₁ = 2.5, 0.5, −1.5 and the local argmaxes play the true optimum, 5 at (A0, B0). On a product game the best additive fit misses by a mean squared error of 0.444, but the ranking survives and VDN still plays the optimum, 9. On a penalty game — where one action is great only if the partner plays its half — the fit error is 50.6 and the local choices miss the optimum.",
        "That is the limit of additivity: a sum cannot represent payoffs where the value of one agent's action depends on what the other does. QMIX relaxes the sum to any monotone mixing, which captures the product game, and methods such as QTRAN and QPLEX go further for the non-monotone cases.",
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
        "In the lab a sum fits an additive payoff exactly, misses a product game by MSE 0.444 (still picking the optimum), and misses a penalty game by 50.6.",
    ),
    crossLinks = listOf(
        CrossLink("iql", "Independent Q-Learning"),
        CrossLink("qmix", "QMIX"),
    ),
)
