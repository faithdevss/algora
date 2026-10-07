package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val iqlContent = TopicContent(
    topicId = "iql",
    figure = Figure(
        caption = "The page's lab: two independent Q-learners sharing one payoff, where the joint " +
            "action (A0, B0) pays 8 but either half played alone loses 12. While the partner still " +
            "explores, A0 averages (8 − 12 − 12) / 3 = −5.33 against −4.00 for the safe actions, so " +
            "both agents learn to avoid exactly the moves the optimum needs — relative " +
            "over-generalisation. The bars are where the greedy joint action ended after 500 " +
            "rounds, across 50 runs: the optimum in only 16, and safe, coordinated-on-nothing " +
            "joint actions worth 0 in the other 34. Neither agent can reach the optimum by changing " +
            "its own action alone, so once both settle on safety, nothing in independent learning " +
            "moves them. Centralised critics and value decomposition exist for this.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("(A0, B0) · 8", 1f, FigureTone.Accent),
                FigureBar("(A2, B2) · 0", 0.8125f, FigureTone.Warn),
                FigureBar("(A1, B1) · 0", 0.6875f, FigureTone.Warn),
                FigureBar("(A2, B1) · 0", 0.375f, FigureTone.Warn),
                FigureBar("(A1, B2) · 0", 0.25f, FigureTone.Warn),
            ),
            yLabel = "runs ending here, 0 to 16 of 50",
        ),
    ),
    whatIsIt = listOf(
        "Independent Q-learning is the naive approach to multi-agent RL: give every agent its own Q-learner and let each treat the others as part of the environment. It scales trivially and needs no communication, and it often works — but the environment each agent sees is not stationary, because it contains other agents who are learning too.",
        "The lab shows the classic failure on a cooperative game with one shared payoff: the joint action (A0, B0) pays 8, but if either agent plays its half while the partner does anything else, the team loses 12; the other combinations pay 0. While the partner is still exploring roughly uniformly, A0 averages (8 − 12 − 12) / 3 = −5.33 for agent A while the safe actions average −4.00, so after 10 rounds A0 is already worth −2.28 and falling. Both agents retreat to safe, mediocre actions. After 500 rounds the greedy joint action in the lab's run is (A1, B1), worth 0 instead of 8, and across 50 runs the optimum is found in only 16.",
        "This is relative over-generalisation: each agent judges an action by its average outcome against a partner who is still changing, so actions that need coordination look bad until both agents commit to them at the same time — which neither will do alone. Centralised critics (MADDPG), value decomposition (VDN, QMIX) and optimistic learners such as hysteretic Q-learning all exist to get past it.",
    ),
    steps = listOf(
        StepCard(1, "One Learner per Agent", "Each agent maintains its own independent Q-function.", 0xFF818CF8),
        StepCard(2, "Treat Others as Environment", "From one agent's view, other agents' behavior is just environment dynamics.", 0xFF60A5FA),
        StepCard(3, "Learn Locally", "Each agent updates its Q-values from its own observations and rewards.", 0xFF10B981),
        StepCard(4, "Face Non-Stationarity", "Because everyone is learning at once, each agent's world keeps shifting.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Per-agent update", "Qᵢ(s,aᵢ) += α[r + γ maxQᵢ − Qᵢ]", "Standard Q-learning, run independently."),
        FormulaEntry("Problem", "non-stationarity", "Others' changing policies break the MDP assumption."),
        FormulaEntry("Upside", "scales linearly", "No joint action space to model."),
    ),
    notationKey = listOf(
        NotationEntry("Qᵢ", "agent i's independent Q-function"),
        NotationEntry("non-stationary", "environment changes as co-agents learn"),
        NotationEntry("decentralized", "each agent learns and acts on its own"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Independent Q-learners",
            accentColor = 0xFF6366F1,
            code = """
                # Each agent has its own Q-table; others are treated as environment.
                for i in range(num_agents):
                    a = agents[i].act(obs[i])
                    r, next_obs = env.step_agent(i, a)
                    agents[i].q_update(obs[i], a, r, next_obs[i])
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.MultiAgentPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF818CF8, "Baseline Multi-Agent RL", "The simplest starting point for cooperative or competitive MARL."),
        ApplicationCard("game", 0xFF60A5FA, "Many-Agent Systems", "Scales to many agents where a joint model would be intractable."),
        ApplicationCard("robot", 0xFF10B981, "Decentralized Control", "Each robot or unit learns from purely local information."),
    ),
    takeaways = listOf(
        "IQL runs independent single-agent Q-learning per agent.",
        "It scales well but suffers from a non-stationary environment.",
        "It's the natural multi-agent baseline to compare against.",
        "Value-decomposition methods (VDN, QMIX) address its coordination weakness.",
        "In the lab independent learners find the 8-point optimum in only 16 of 50 runs; most settle on safe joint actions worth 0.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("vdn", "VDN"),
    ),
)
