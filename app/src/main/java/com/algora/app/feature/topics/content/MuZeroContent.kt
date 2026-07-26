package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val muZeroContent = TopicContent(
    topicId = "muzero",
    whatIsIt = listOf(
        "MuZero matches AlphaZero's superhuman play but without being told the rules — it learns its own model of the environment's dynamics purely from experience.",
        "It plans in a learned latent space, predicting only what matters for decisions — reward, value, and policy — rather than reconstructing full observations.",
    ),
    steps = listOf(
        StepCard(1, "Representation", "Encode the observation history into an initial latent state.", 0xFF818CF8),
        StepCard(2, "Dynamics", "A learned model predicts the next latent state and reward for a hypothetical action.", 0xFF60A5FA),
        StepCard(3, "Prediction", "From any latent state, output a policy and a value.", 0xFF10B981),
        StepCard(4, "Plan in Latent Space", "Run MCTS entirely through these learned functions — no real simulator needed.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Three functions", "h (encode), g (dynamics), f (predict)", "Learned model components."),
        FormulaEntry("Latent rollout", "sᵏ, rᵏ = g(sᵏ⁻¹, aᵏ)", "Imagined transitions in latent space."),
        FormulaEntry("Trained to match", "policy, value, reward", "Only decision-relevant quantities."),
    ),
    notationKey = listOf(
        NotationEntry("h, g, f", "representation, dynamics, prediction networks"),
        NotationEntry("latent state", "abstract internal state used for planning"),
        NotationEntry("learned model", "dynamics inferred from experience"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "MuZero's learned model (structure)",
            accentColor = 0xFF6366F1,
            code = """
                s0        = h(observation_history)   # representation
                p0, v0    = f(s0)                     # prediction (policy, value)
                s1, r1    = g(s0, action)             # dynamics (next latent, reward)
                # MCTS unrolls g/f in latent space — the true rules are never given.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Games + Atari", "Mastered Go, chess, shogi, and Atari with one algorithm, no rules given."),
        ApplicationCard("chip", 0xFF60A5FA, "Real-World Control", "Applied to video compression and other systems where dynamics are unknown."),
        ApplicationCard("bulb", 0xFF10B981, "Model Learning", "Shows planning works on a learned, decision-focused model."),
    ),
    takeaways = listOf(
        "MuZero learns its own dynamics model and plans without knowing the rules.",
        "It plans in latent space, predicting only reward, value, and policy.",
        "It generalizes AlphaZero to environments with unknown dynamics, including Atari.",
        "Not reconstructing full observations is what keeps its model efficient.",
    ),
    crossLinks = listOf(
        CrossLink("alphazero", "AlphaZero"),
        CrossLink("world_models", "World Models"),
    ),
)
