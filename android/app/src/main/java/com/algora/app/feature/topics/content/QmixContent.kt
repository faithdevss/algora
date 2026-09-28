package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val qmixContent = TopicContent(
    topicId = "qmix",
    whatIsIt = listOf(
        "QMIX generalizes VDN by combining per-agent values through a learned monotonic mixing network instead of a plain sum, capturing richer team dynamics.",
        "Monotonicity is the key constraint: raising any agent's individual Q never decreases the team value, so each agent's greedy action stays consistent with the team's — decentralized execution still works.",
    ),
    steps = listOf(
        StepCard(1, "Per-Agent Q-Values", "Each agent outputs an individual Q from its local observation.", 0xFF818CF8),
        StepCard(2, "Monotonic Mixing", "A mixing network combines them into Q_tot with non-negative weights (enforcing monotonicity).", 0xFF60A5FA),
        StepCard(3, "State-Conditioned Weights", "A hypernetwork generates the mixing weights from the global state.", 0xFF10B981),
        StepCard(4, "Train End-to-End", "The team TD error backpropagates through the mixer into every agent.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Monotonicity", "∂Q_tot / ∂Qᵢ ≥ 0", "Ensures per-agent argmax matches the team's."),
        FormulaEntry("Mixing", "Q_tot = f_state(Q₁,…,Qₙ)", "Learned non-linear, non-negative combination."),
        FormulaEntry("Expressiveness", "≥ VDN", "Sum is a special case of monotonic mixing."),
    ),
    notationKey = listOf(
        NotationEntry("mixing network", "combines agent Qs into Q_tot"),
        NotationEntry("hypernetwork", "generates state-dependent mixing weights"),
        NotationEntry("monotonic", "team value non-decreasing in each Qᵢ"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "QMIX monotonic mixer (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                # Non-negative weights from a hypernetwork enforce monotonicity.
                w1 = torch.abs(hyper_w1(state))     # >= 0
                q_tot = elu(q_agents @ w1 + hyper_b1(state)) @ torch.abs(hyper_w2(state))
                q_tot = q_tot + hyper_b2(state)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.MultiAgentPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "StarCraft Micro", "A leading method on the SMAC cooperative multi-agent benchmark."),
        ApplicationCard("users", 0xFF60A5FA, "Team Coordination", "Cooperative tasks needing richer credit assignment than a plain sum."),
        ApplicationCard("robot", 0xFF10B981, "Multi-Robot Teams", "Joint learning with local, communication-free execution."),
    ),
    takeaways = listOf(
        "QMIX mixes agent values with a learned monotonic network, beating VDN's sum.",
        "Monotonicity keeps decentralized greedy actions consistent with the team optimum.",
        "A hypernetwork conditions the mixing on the global state.",
        "It's a standard strong baseline for cooperative multi-agent RL.",
    ),
    crossLinks = listOf(
        CrossLink("vdn", "VDN"),
        CrossLink("maddpg", "MADDPG"),
    ),
)
