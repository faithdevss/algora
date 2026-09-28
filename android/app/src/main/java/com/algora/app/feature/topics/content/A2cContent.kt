package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val a2cContent = TopicContent(
    topicId = "a2c",
    whatIsIt = listOf(
        "A2C (Advantage Actor-Critic) is the synchronous form of actor-critic that runs many parallel environments, collects a batch of experience, and updates the shared network once per batch.",
        "It's the deterministic, easier-to-tune cousin of A3C — same advantage-based updates, but with synchronized workers instead of asynchronous ones.",
    ),
    steps = listOf(
        StepCard(1, "Parallel Rollouts", "Several environment copies step in parallel with the shared policy.", 0xFF818CF8),
        StepCard(2, "Collect a Batch", "Gather a fixed number of steps from all workers at once.", 0xFF60A5FA),
        StepCard(3, "Compute Advantages", "Estimate advantages (often n-step or GAE) across the batch.", 0xFF10B981),
        StepCard(4, "Synchronous Update", "Average the gradients and update the single shared network.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Total loss", "L = L_actor + c₁·L_critic − c₂·H", "Policy, value, and entropy bonus."),
        FormulaEntry("Advantage", "n-step or GAE", "Lower-variance target than a single step."),
        FormulaEntry("Entropy bonus", "+ c₂·H(π)", "Encourages exploration."),
    ),
    notationKey = listOf(
        NotationEntry("H(π)", "policy entropy — an exploration incentive"),
        NotationEntry("synchronous", "all workers step and update together"),
        NotationEntry("c₁, c₂", "value-loss and entropy coefficients"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A2C combined loss",
            accentColor = 0xFF6366F1,
            code = """
                actor_loss   = -(log_probs * advantages.detach()).mean()
                critic_loss  = advantages.pow(2).mean()
                entropy      = dist.entropy().mean()
                loss = actor_loss + 0.5 * critic_loss - 0.01 * entropy
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari & Control", "A solid on-policy baseline across discrete and continuous benchmarks."),
        ApplicationCard("chip", 0xFF60A5FA, "GPU-Efficient RL", "Synchronous batching maps cleanly onto GPU training."),
        ApplicationCard("robot", 0xFF10B981, "Parallel Simulation", "Exploits many simulated environments running at once."),
    ),
    takeaways = listOf(
        "A2C is synchronous advantage actor-critic over parallel environments.",
        "Batched synchronous updates make it simpler and more GPU-friendly than A3C.",
        "An entropy bonus in the loss sustains exploration.",
        "It's a clean stepping stone from actor-critic to PPO.",
    ),
    crossLinks = listOf(
        CrossLink("actor_critic", "Actor-Critic"),
        CrossLink("a3c", "A3C"),
    ),
)
