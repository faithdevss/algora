package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val worldModelsContent = TopicContent(
    topicId = "world_models",
    whatIsIt = listOf(
        "World Models learn a compact generative model of an environment so an agent can 'dream' — training its policy inside its own learned simulation instead of the real world.",
        "The classic design compresses observations with a vision model (VAE), predicts their evolution with a memory model (RNN), and trains a tiny controller in the imagined rollout.",
    ),
    steps = listOf(
        StepCard(1, "Vision (V)", "A VAE compresses each high-dimensional observation into a small latent vector.", 0xFF818CF8),
        StepCard(2, "Memory (M)", "An RNN models how the latent evolves over time, predicting the next latent.", 0xFF60A5FA),
        StepCard(3, "Controller (C)", "A tiny policy maps latent + memory to actions.", 0xFF10B981),
        StepCard(4, "Train in the Dream", "Learn the controller inside the learned model, then deploy in the real environment.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("V", "z = encode(observation)", "Latent compression via a VAE."),
        FormulaEntry("M", "hₜ, ẑₜ₊₁ = RNN(zₜ, aₜ, hₜ₋₁)", "Predict the next latent state."),
        FormulaEntry("C", "aₜ = controller(zₜ, hₜ)", "A small, fast policy."),
    ),
    notationKey = listOf(
        NotationEntry("z", "latent observation code"),
        NotationEntry("V, M, C", "vision, memory, controller components"),
        NotationEntry("dreaming", "training inside the learned model"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "World Models pipeline (V-M-C)",
            accentColor = 0xFF6366F1,
            code = """
                z      = vae.encode(obs)              # V: compress
                h, z_next = rnn(z, action, h)         # M: predict dynamics
                action = controller(z, h)             # C: act
                # The controller is trained on rollouts imagined by V + M.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Learning in Imagination", "Trained agents that solved tasks largely inside their own dreamed environment."),
        ApplicationCard("robot", 0xFF60A5FA, "Sample-Efficient RL", "Reduces costly real interaction by planning in a learned model."),
        ApplicationCard("bulb", 0xFF10B981, "Latent Dynamics", "Pioneered compressing observations and learning their evolution for control."),
    ),
    takeaways = listOf(
        "World Models learn a generative simulator and train the policy inside it.",
        "The V-M-C split compresses observations, predicts dynamics, and acts.",
        "Learning in imagination cuts real-environment interaction.",
        "Dreamer built on this idea into a leading model-based RL family.",
    ),
    crossLinks = listOf(
        CrossLink("dreamer", "Dreamer (V1–V3)"),
        CrossLink("autoencoders", "Autoencoders"),
    ),
)
