package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ppoContent = TopicContent(
    topicId = "ppo",
    whatIsIt = listOf(
        "Proximal Policy Optimization (PPO) keeps policy updates close to the old policy using a clipped objective — capturing TRPO's stability with far simpler first-order optimization.",
        "It's the default policy-gradient algorithm in modern RL, prized for being robust, easy to tune, and effective across discrete and continuous tasks — including RLHF for LLMs.",
    ),
    steps = listOf(
        StepCard(1, "Collect a Rollout", "Gather trajectories with the current policy and compute GAE advantages.", 0xFF818CF8),
        StepCard(2, "Probability Ratio", "Compute r = π_new/π_old for each action taken.", 0xFF60A5FA),
        StepCard(3, "Clip the Objective", "Cap the ratio to [1−ε, 1+ε] so beneficial moves can't overshoot.", 0xFF10B981),
        StepCard(4, "Multiple Epochs", "Reuse the same batch for several gradient epochs before discarding it.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Clipped objective", "min(r·A, clip(r, 1−ε, 1+ε)·A)", "Pessimistic bound on the surrogate."),
        FormulaEntry("Ratio", "r = π_new(a|s) / π_old(a|s)", "How much the policy changed."),
        FormulaEntry("ε", "≈ 0.1–0.2", "Clip range — the effective trust region."),
    ),
    notationKey = listOf(
        NotationEntry("r", "importance-sampling ratio"),
        NotationEntry("ε", "clipping range"),
        NotationEntry("A", "advantage estimate (usually GAE)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "PPO clipped loss (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                ratio = torch.exp(new_log_prob - old_log_prob)
                clipped = torch.clamp(ratio, 1 - eps, 1 + eps)
                loss = -torch.min(ratio * adv, clipped * adv).mean()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robotics & Control", "The workhorse for locomotion, manipulation, and continuous control."),
        ApplicationCard("book", 0xFF60A5FA, "RLHF for LLMs", "PPO is the classic optimizer that aligns language models to human feedback."),
        ApplicationCard("game", 0xFF10B981, "Game Agents", "OpenAI Five and many game-playing agents are PPO-based."),
    ),
    takeaways = listOf(
        "PPO clips the probability ratio to keep updates near the old policy.",
        "It gets TRPO-like stability with simple first-order optimization.",
        "It reuses each batch for several epochs, improving sample efficiency.",
        "Its robustness made it the default policy-gradient method — including for RLHF.",
    ),
    crossLinks = listOf(
        CrossLink("trpo", "TRPO"),
        CrossLink("rlhf", "RLHF"),
    ),
)
