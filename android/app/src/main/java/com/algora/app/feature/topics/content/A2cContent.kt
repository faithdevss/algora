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

internal val a2cContent = TopicContent(
    topicId = "a2c",
    figure = Figure(
        caption = "The page's lab: the spread of A2C's gradient estimate as more parallel actors " +
            "contribute rollouts to each update. One actor: 0.298. Eight: 0.112, close to the 0.105 " +
            "that the √n law predicts for independent samples. Sixteen: 0.080. Halving the noise " +
            "costs four times the actors, so the returns diminish exactly as fast as the compute " +
            "grows. What the bars cannot show is the other benefit: actors in different parts of " +
            "the environment make each batch decorrelated by construction, which is what an " +
            "off-policy learner gets from a replay buffer and an on-policy one cannot use. A2C runs " +
            "the actors synchronously and batches them into one forward pass, which is why it " +
            "replaced the asynchronous A3C on GPUs.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("1 actor", 1f, FigureTone.Warn),
                FigureBar("8 actors", 0.376f, FigureTone.Primary),
                FigureBar("16 actors", 0.268f, FigureTone.Accent),
            ),
            yLabel = "gradient sd, 0 to 0.298",
        ),
    ),
    whatIsIt = listOf(
        "A2C — advantage actor-critic — runs several copies of the environment in parallel, collects a short rollout from each, and computes one synchronous update from all of them together. The actor is trained on the advantage the critic estimates, the critic on the TD error, exactly as in plain actor-critic; what A2C adds is the batch of parallel actors.",
        "The lab measures what that batch buys. One actor's gradient estimate has a standard deviation of 0.298 across repeated rollouts. Averaging 8 independent rollouts cuts it to 0.112 — close to the 0.105 that the √n law predicts for independent samples. Doubling again to 16 actors reaches 0.080: each halving of the noise costs four times the compute, so the returns diminish exactly as fast as the theory says.",
        "The second benefit does not show up in that number. Parallel actors are in different parts of the environment at any moment, so a batch is decorrelated by construction — the same problem a replay buffer solves for off-policy learners, solved here without storing anything. That is what lets an on-policy method train a neural network stably, and A2C, being synchronous, batches all actors into one forward pass, which is why it displaced the asynchronous A3C on GPUs.",
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
        "In the lab gradient noise falls 0.298 → 0.112 → 0.080 with 1, 8 and 16 actors — the √n law, and diminishing returns.",
    ),
    crossLinks = listOf(
        CrossLink("actor_critic", "Actor-Critic"),
        CrossLink("a3c", "A3C"),
    ),
)
