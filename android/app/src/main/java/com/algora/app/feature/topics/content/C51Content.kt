package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val c51Content = TopicContent(
    topicId = "c51",
    whatIsIt = listOf(
        "Distributional RL learns the full distribution of returns for each action, not just their mean — C51 is the canonical version, using 51 fixed atoms to represent that distribution.",
        "Modeling the whole distribution captures risk and multimodality, and empirically yields richer, more stable learning signals than predicting the expected value alone.",
    ),
    steps = listOf(
        StepCard(1, "Fixed Support Atoms", "Discretize the possible return range into N (=51) fixed values (atoms).", 0xFF818CF8),
        StepCard(2, "Predict Probabilities", "The network outputs a probability over those atoms for each action.", 0xFF60A5FA),
        StepCard(3, "Distributional Bellman", "Shift and scale the next-state distribution by r and γ, then project it back onto the atoms.", 0xFF10B981),
        StepCard(4, "Cross-Entropy Loss", "Train the predicted distribution toward the projected target with KL/cross-entropy.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Distributional Bellman", "Z(s,a) =ᴰ R + γZ(S′,A′)", "Equality in distribution (ᴰ) of the return, not just its mean."),
        FormulaEntry("Atoms", "zᵢ = Vmin + i·Δz", "51 evenly spaced support values."),
        FormulaEntry("Action choice", "argmaxₐ Σ zᵢ·pᵢ(s,a)", "Act on the distribution's mean."),
    ),
    notationKey = listOf(
        NotationEntry("Z(s,a)", "distribution of returns (a random variable)"),
        NotationEntry("atoms", "the 51 fixed support values"),
        NotationEntry("projection", "mapping the shifted target back onto atoms"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "C51 action selection",
            accentColor = 0xFF6366F1,
            code = """
                # logits: (actions, num_atoms); support: the 51 atom values.
                probs = F.softmax(logits, dim=-1)
                q_values = (probs * support).sum(dim=-1)   # expected return per action
                action = q_values.argmax()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari SOTA", "C51 beat prior value-based methods across the Atari benchmark."),
        ApplicationCard("chip", 0xFF60A5FA, "Rainbow Component", "The distributional ingredient in Rainbow DQN."),
        ApplicationCard("finance", 0xFF10B981, "Risk-Aware RL", "Modeling the return distribution enables risk-sensitive decision making."),
    ),
    takeaways = listOf(
        "Distributional RL predicts the return distribution, not just its mean.",
        "C51 uses 51 fixed atoms and a projected distributional Bellman update.",
        "It trains with cross-entropy against the projected target distribution.",
        "Richer signals improve stability; QR-DQN later removed the fixed-atom limitation.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
    ),
)
