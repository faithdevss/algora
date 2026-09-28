package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mbpoContent = TopicContent(
    topicId = "mbpo",
    whatIsIt = listOf(
        "Model-Based Policy Optimization (MBPO) improves sample efficiency by training a model-free agent (like SAC) on short imagined rollouts branched off real states.",
        "Its key insight: a learned model is only trusted for a few steps, so branching short rollouts from real data avoids the compounding error that ruins long model predictions.",
    ),
    steps = listOf(
        StepCard(1, "Learn a Model Ensemble", "Train several probabilistic dynamics models to capture uncertainty.", 0xFF818CF8),
        StepCard(2, "Branch Short Rollouts", "From real states in the buffer, roll the model forward only k steps.", 0xFF60A5FA),
        StepCard(3, "Augment the Data", "Add these short imagined transitions to the agent's replay buffer.", 0xFF10B981),
        StepCard(4, "Optimize Model-Free", "Train SAC on the mix of real and model-generated data.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Short horizon", "k steps only", "Limits compounding model error."),
        FormulaEntry("Branching", "rollouts start from real states", "Anchors imagination in true data."),
        FormulaEntry("Ensemble", "multiple models", "Uncertainty-aware predictions."),
    ),
    notationKey = listOf(
        NotationEntry("k", "model rollout horizon (short)"),
        NotationEntry("ensemble", "set of dynamics models"),
        NotationEntry("branching", "starting imagined rollouts from real states"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "MBPO short-rollout augmentation",
            accentColor = 0xFF6366F1,
            code = """
                for s in sample_real_states(real_buffer):
                    for _ in range(k):                 # short horizon only
                        a = policy(s)
                        s2, r = model_ensemble.step(s, a)
                        model_buffer.add((s, a, r, s2))
                        s = s2
                sac.update(real_buffer + model_buffer)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Sample-Efficient Control", "Reaches strong continuous-control performance in far fewer real steps."),
        ApplicationCard("chart", 0xFF60A5FA, "Data-Limited RL", "Valuable where each real interaction is expensive or slow."),
        ApplicationCard("bulb", 0xFF10B981, "Model–Model-Free Hybrid", "Marries a learned model's efficiency with SAC's robustness."),
    ),
    takeaways = listOf(
        "MBPO augments a model-free agent with short model-generated rollouts.",
        "Keeping rollouts short avoids the compounding error of a learned model.",
        "Branching from real states anchors imagination in true experience.",
        "It substantially improves sample efficiency over pure model-free RL.",
    ),
    crossLinks = listOf(
        CrossLink("dyna_q", "Dyna-Q"),
        CrossLink("sac", "SAC"),
    ),
)
