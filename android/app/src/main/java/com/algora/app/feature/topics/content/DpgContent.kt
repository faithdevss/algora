package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dpgContent = TopicContent(
    topicId = "dpg",
    whatIsIt = listOf(
        "The Deterministic Policy Gradient (DPG) learns a policy that outputs a specific action rather than a distribution, enabling efficient gradients for continuous action spaces.",
        "Instead of integrating over all actions, it pushes the deterministic policy in the direction that most increases the critic's Q-value — the gradient flows through the critic into the actor.",
    ),
    steps = listOf(
        StepCard(1, "Deterministic Actor", "The policy μ(s) maps a state to one continuous action, not a distribution.", 0xFF818CF8),
        StepCard(2, "Critic Learns Q", "A Q-network estimates the value of state-action pairs.", 0xFF60A5FA),
        StepCard(3, "Gradient Through the Critic", "∇Q with respect to the action tells the actor which way to move.", 0xFF10B981),
        StepCard(4, "Chain Rule Update", "Backprop that action gradient into the actor's parameters.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("DPG theorem", "∇J = E[∇_a Q(s,a)|_{a=μ(s)} · ∇_θ μ(s)]", "Chain rule through the critic."),
        FormulaEntry("Deterministic policy", "a = μ(s; θ)", "One action per state."),
        FormulaEntry("Why it helps", "no action integral", "Efficient in continuous, high-dim action spaces."),
    ),
    notationKey = listOf(
        NotationEntry("μ(s)", "the deterministic policy"),
        NotationEntry("Q(s,a)", "the critic's action-value"),
        NotationEntry("∇_a Q", "action-gradient guiding the actor"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Deterministic policy gradient (actor loss)",
            accentColor = 0xFF6366F1,
            code = """
                # Maximize the critic's Q at the actor's chosen action.
                actor_loss = -critic(s, actor(s)).mean()
                actor_loss.backward()   # gradient flows Q -> action -> actor params
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Continuous Control", "Enables policy gradients for robot joints and continuous actuators."),
        ApplicationCard("chip", 0xFF60A5FA, "Basis for DDPG", "DDPG is DPG made deep, with replay and target networks."),
        ApplicationCard("bulb", 0xFF10B981, "Efficient Gradients", "Avoids the costly action integral of stochastic policy gradients."),
    ),
    takeaways = listOf(
        "DPG learns a deterministic policy, ideal for continuous action spaces.",
        "Its gradient flows through the critic's Q with respect to the action.",
        "It skips the expensive integral over actions that stochastic PG needs.",
        "DDPG, TD3, and (soft) SAC all descend from this deterministic-gradient idea.",
    ),
    crossLinks = listOf(
        CrossLink("ddpg", "DDPG"),
        CrossLink("actor_critic", "Actor-Critic"),
    ),
)
