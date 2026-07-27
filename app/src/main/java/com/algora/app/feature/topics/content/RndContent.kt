package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rndContent = TopicContent(
    topicId = "rnd",
    whatIsIt = listOf(
        "Random Network Distillation (RND) measures novelty by how well a trainable network can predict the output of a fixed, randomly-initialized target network on a given state.",
        "Familiar states are predicted well (low error, low novelty); new states are predicted poorly (high error, high curiosity) — a simple, robust exploration bonus.",
    ),
    steps = listOf(
        StepCard(1, "Fix a Random Target", "A randomly-initialized target network is frozen and never trained.", 0xFF818CF8),
        StepCard(2, "Train a Predictor", "A second network learns to match the target's output on visited states.", 0xFF60A5FA),
        StepCard(3, "Error = Novelty", "Prediction error is high on unfamiliar states, low on well-visited ones.", 0xFF10B981),
        StepCard(4, "Reward Novelty", "Use the error as an intrinsic reward encouraging exploration.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Intrinsic reward", "r_i = ‖predictor(s) − target(s)‖²", "Distillation error as novelty."),
        FormulaEntry("Target", "fixed random network", "Never updated — a stable prediction goal."),
        FormulaEntry("Advantage", "no dynamics model", "Simpler and noise-robust vs ICM."),
    ),
    notationKey = listOf(
        NotationEntry("target network", "frozen random feature extractor"),
        NotationEntry("predictor", "trained to imitate the target"),
        NotationEntry("distillation error", "the novelty signal"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "RND novelty bonus",
            accentColor = 0xFF6366F1,
            code = """
                with torch.no_grad():
                    target_feat = target_net(state)      # fixed, random
                pred_feat = predictor_net(state)         # trained to match
                intrinsic_reward = (pred_feat - target_feat).pow(2).mean()
                # predictor is trained to minimize this on visited states.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ExplorationPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Montezuma's Revenge", "RND achieved breakthrough scores on this notorious hard-exploration game."),
        ApplicationCard("robot", 0xFF60A5FA, "Sparse-Reward Control", "A drop-in novelty bonus for exploration-starved tasks."),
        ApplicationCard("bulb", 0xFF10B981, "Simple & Robust", "No dynamics model needed, and it resists the noisy-TV problem."),
    ),
    takeaways = listOf(
        "RND scores novelty by prediction error against a fixed random target network.",
        "High error means an unfamiliar state; low error means a well-visited one.",
        "It needs no dynamics or inverse model, making it simpler than ICM.",
        "It delivered landmark results on hard-exploration Atari games.",
    ),
    crossLinks = listOf(
        CrossLink("icm", "Curiosity-Driven (ICM)"),
        CrossLink("intrinsic_motivation", "Intrinsic Motivation"),
    ),
)
