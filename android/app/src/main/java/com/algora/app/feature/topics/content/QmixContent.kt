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

internal val qmixContent = TopicContent(
    topicId = "qmix",
    figure = Figure(
        caption = "The page's lab: QMIX against VDN on two team payoffs, scored by how closely each " +
            "can represent the payoff (fit error) and whether the agents' independent greedy choices " +
            "land on the team optimum. VDN models the team value as a sum; QMIX passes the agents' " +
            "utilities through a small mixer whose weights are forced non-negative, so the team " +
            "value can bend but always rises when any agent's utility does — which keeps local " +
            "argmaxes globally right. On the product game the mixer drives the error from 0.444 to " +
            "0.001. On the penalty game, where an action is good only if the partner plays its half, " +
            "the optimum needs non-monotone mixing: QMIX's error is 35.6 against VDN's 50.6, and " +
            "both choose the wrong joint action. Monotone helps; it isn't everything.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.444 ✓", "0.001 ✓"),
                listOf("50.6 ✗", "35.6 ✗"),
            ),
            rowHeaders = listOf("product game", "penalty game"),
            colHeaders = listOf("VDN (sum)", "QMIX (monotone)"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(1, 0, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "QMIX keeps VDN's promise — each agent acts on its own utility, and the agents' local argmaxes are the team's best joint action — while representing far more team payoffs than a sum. It does that by mixing the agents' utilities with a small network whose weights are forced to be non-negative, so Q_tot is monotone in each agent's utility: raising any one agent's value can never lower the team's. Monotonicity is exactly the condition under which local argmaxes stay globally optimal.",
        "The lab compares it with VDN on a product game, a payoff that is monotone in each agent's contribution but has an interaction a sum cannot capture. VDN's best additive fit, solved exactly by least squares, has a mean squared error of 0.444. QMIX's mixer — six ReLU units with non-negative weights, trained 6,000 steps — bends the sum into a product and drives the error to 0.001. Because a sum is itself one monotone mixer, QMIX can only match or beat VDN.",
        "Monotone is not everything. On the lab's penalty game, where whether one agent's action is good depends entirely on what the other plays, the optimum needs non-monotone mixing: VDN's fit error is 50.6 and QMIX's 35.6, and both pick the wrong joint action. That gap is what QTRAN and QPLEX target. QMIX nonetheless became the standard baseline on the StarCraft multi-agent challenge.",
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
        "In the lab QMIX fits a product game with error 0.001 against VDN's 0.444; on a non-monotone penalty game both fail (35.6 and 50.6).",
    ),
    crossLinks = listOf(
        CrossLink("vdn", "VDN"),
        CrossLink("maddpg", "MADDPG"),
    ),
)
