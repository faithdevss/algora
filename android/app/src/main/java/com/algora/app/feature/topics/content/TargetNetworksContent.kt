package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val targetNetworksContent = TopicContent(
    topicId = "target_networks",
    whatIsIt = listOf(
        "A target network is a slowly-updated copy of the Q-network used to compute the learning target, so the network isn't chasing a target that shifts every step.",
        "Without it, the same weights appear on both sides of the TD update, creating a feedback loop that makes deep Q-learning unstable or divergent.",
    ),
    steps = listOf(
        StepCard(1, "Keep Two Networks", "An online network being trained and a target network holding frozen weights.", 0xFF818CF8),
        StepCard(2, "Target Computes the Goal", "The TD target uses the target network's Q-values, not the online net's.", 0xFF60A5FA),
        StepCard(3, "Update Slowly", "Copy the online weights into the target every N steps (hard) or blend them each step (soft).", 0xFF10B981),
        StepCard(4, "Stabilize Learning", "A near-stationary target turns the moving-goalpost problem into stable regression.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Target", "y = r + γ·maxₐ′ Q(s′,a′; θ⁻)", "θ⁻ are the frozen target params."),
        FormulaEntry("Hard update", "θ⁻ ← θ every N steps", "Periodic copy."),
        FormulaEntry("Soft update", "θ⁻ ← τθ + (1−τ)θ⁻", "Polyak averaging with small τ."),
    ),
    notationKey = listOf(
        NotationEntry("θ⁻", "target-network parameters"),
        NotationEntry("τ", "soft-update rate (Polyak coefficient)"),
        NotationEntry("N", "hard-update interval in steps"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Soft target update (Polyak)",
            accentColor = 0xFF6366F1,
            code = """
                # Blend online weights into the target network each step.
                tau = 0.005
                for tp, op in zip(target_net.parameters(), policy_net.parameters()):
                    tp.data.copy_(tau * op.data + (1 - tau) * tp.data)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF818CF8, "Stable Deep RL", "Target networks are standard in DQN, DDPG, TD3, and SAC."),
        ApplicationCard("bulb", 0xFF60A5FA, "Divergence Prevention", "They tame the 'deadly triad' of bootstrapping, function approximation, and off-policy learning."),
        ApplicationCard("robot", 0xFF10B981, "Continuous Control", "Soft-updated targets are essential to actor-critic stability."),
    ),
    takeaways = listOf(
        "A target network provides stable TD targets by lagging the online network.",
        "It breaks the feedback loop where a network chases its own shifting predictions.",
        "Updates are hard (periodic copy) or soft (Polyak averaging).",
        "Together with replay, it's a core stabilizer of off-policy deep RL.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("double_dqn", "Double DQN"),
    ),
)
