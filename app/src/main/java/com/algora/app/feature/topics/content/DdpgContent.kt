package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ddpgContent = TopicContent(
    topicId = "ddpg",
    whatIsIt = listOf(
        "DDPG (Deep Deterministic Policy Gradient) is an off-policy actor-critic for continuous control that combines the deterministic policy gradient with DQN's replay buffer and target networks.",
        "Think of it as 'DQN for continuous actions': a deterministic actor proposes actions, a critic scores them, and both learn from replayed experience.",
    ),
    steps = listOf(
        StepCard(1, "Actor + Critic Networks", "μ(s) outputs actions; Q(s,a) evaluates them, each with a target copy.", 0xFF818CF8),
        StepCard(2, "Off-Policy Replay", "Sample past transitions from a buffer, as in DQN.", 0xFF60A5FA),
        StepCard(3, "Exploration Noise", "Add noise (Ornstein-Uhlenbeck or Gaussian) to the deterministic action for exploration.", 0xFF10B981),
        StepCard(4, "Soft Updates", "Slowly Polyak-average the targets toward the online networks.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Critic target", "y = r + γ·Q′(s′, μ′(s′))", "Bootstrapped from target actor + critic."),
        FormulaEntry("Actor loss", "−E[Q(s, μ(s))]", "Push the policy toward higher Q."),
        FormulaEntry("Soft update", "θ′ ← τθ + (1−τ)θ′", "Polyak averaging of targets."),
    ),
    notationKey = listOf(
        NotationEntry("μ(s)", "deterministic actor"),
        NotationEntry("Q(s,a)", "critic"),
        NotationEntry("exploration noise", "added to actions for off-policy exploration"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "DDPG action with exploration noise",
            accentColor = 0xFF6366F1,
            code = """
                action = actor(state)
                action = (action + noise.sample()).clamp(-1.0, 1.0)   # explore
                # Critic trained on y = r + gamma * target_critic(s2, target_actor(s2)).
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robotic Control", "Continuous joint control for locomotion and manipulation."),
        ApplicationCard("game", 0xFF60A5FA, "Continuous Environments", "MuJoCo and other continuous-action benchmarks."),
        ApplicationCard("finance", 0xFF10B981, "Continuous Decisions", "Portfolio and control problems with real-valued actions."),
    ),
    takeaways = listOf(
        "DDPG is off-policy actor-critic for continuous actions — DQN's tricks meet DPG.",
        "It needs explicit exploration noise since the policy is deterministic.",
        "It's sample-efficient but notoriously brittle and hyperparameter-sensitive.",
        "TD3 fixes its overestimation and instability issues.",
    ),
    crossLinks = listOf(
        CrossLink("dpg", "DPG"),
        CrossLink("td3", "TD3"),
    ),
)
