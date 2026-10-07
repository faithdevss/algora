package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mdpContent = TopicContent(
    topicId = "mdp",
    figure = Figure(
        caption = "One step of an MDP written out: the policy chooses among the actions, the " +
            "environment's P(s′|s,a) chooses where that action lands, and the reward rides on the " +
            "arrival rather than on the choice. Action a₁ is stochastic — 0.8 to s′ for nothing, " +
            "0.2 to s″ for +1 — while a₂ pays −1 with certainty. Only the node you are standing on " +
            "feeds the next hop; that is the Markov property, and it is what collapses an " +
            "unbounded history into |S| equations in |S| unknowns. ⟨S, A, P, R, γ⟩ is exactly the " +
            "five things this picture needed before it could be drawn.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("s", 0.06f, 0.50f, FigureTone.Primary),
                FigureGraphNode("a₁", 0.42f, 0.18f, FigureTone.Accent),
                FigureGraphNode("a₂", 0.42f, 0.86f, FigureTone.Accent),
                FigureGraphNode("s′ 0", 0.94f, 0.04f, FigureTone.Muted),
                FigureGraphNode("s″ +1", 0.94f, 0.44f, FigureTone.Primary),
                FigureGraphNode("s‴ −1", 0.94f, 0.96f, FigureTone.Warn),
            ),
            edges = listOf(
                FigureEdge(0, 1, "π", directed = true, tone = FigureTone.Accent),
                FigureEdge(0, 2, "π", directed = true, tone = FigureTone.Accent),
                FigureEdge(1, 3, "0.8", directed = true),
                FigureEdge(1, 4, "0.2", directed = true),
                FigureEdge(2, 5, "1.0", directed = true),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A Markov Decision Process (MDP) is the mathematical framework for reinforcement learning: an agent in a state takes an action, receives a reward, and transitions to a new state.",
        "Its defining Markov property is that the future depends only on the current state, not the full history — which makes the problem tractable.",
    ),
    steps = listOf(
        StepCard(1, "States, Actions, Rewards", "Define the state space S, action space A, reward function R, and transition dynamics P.", 0xFF818CF8),
        StepCard(2, "The Markov Property", "The next state depends only on the current state and action, not the past.", 0xFF60A5FA),
        StepCard(3, "Policy & Return", "A policy π chooses actions; the goal is to maximize the expected discounted return.", 0xFF10B981),
        StepCard(4, "Value Functions", "V(s) and Q(s,a) estimate expected return, satisfying the Bellman equations.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Return", "Gₜ = Σₖ₌₀^∞ γᵏ rₜ₊ₖ₊₁", "Discounted sum of future rewards."),
        FormulaEntry("Bellman (V)", "V(s) = Σ π(a|s)Σ P(s′|s,a)[r + γV(s′)]", "Recursive value definition."),
        FormulaEntry("Discount γ", "0 ≤ γ < 1", "How much future rewards are worth now."),
    ),
    notationKey = listOf(
        NotationEntry("S, A", "state and action spaces"),
        NotationEntry("γ", "discount factor"),
        NotationEntry("π", "policy — mapping states to actions"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Value iteration (dynamic programming)",
            accentColor = 0xFF6366F1,
            code = """
                def value_iteration(states, actions, P, R, gamma=0.9, eps=1e-6):
                    V = {s: 0.0 for s in states}
                    while True:
                        delta = 0
                        for s in states:
                            v = max(sum(P[s][a][s2] * (R[s][a] + gamma * V[s2])
                                        for s2 in states) for a in actions)
                            delta = max(delta, abs(v - V[s]))
                            V[s] = v
                        if delta < eps:
                            return V
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "RL Foundation", "Every RL algorithm — Q-learning to PPO — is defined over an MDP."),
        ApplicationCard("robot", 0xFF60A5FA, "Robotics & Control", "Sequential decision problems in navigation and manipulation are modeled as MDPs."),
        ApplicationCard("finance", 0xFF10B981, "Operations Research", "Inventory, maintenance, and resource scheduling are classic MDP applications."),
    ),
    takeaways = listOf(
        "An MDP formalizes RL as states, actions, rewards, and transitions.",
        "The Markov property — future depends only on the present — makes it solvable.",
        "The Bellman equations relate a state's value to its successors'.",
        "Value/policy iteration solves small MDPs exactly; RL scales the ideas to unknown, large ones.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("grid_world", "Grid World"),
        // DSA ↔ AI bridge: value iteration is dynamic programming — the same idea as coin change.
        CrossLink("coin_change", "Coin Change (DP)"),
    ),
)
