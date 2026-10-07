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

internal val c51Content = TopicContent(
    topicId = "c51",
    figure = Figure(
        caption = "The page's lab: two actions that a single Q-value cannot tell apart. The risky " +
            "one pays −1 or +1 with even odds; the safe one always pays 0.30. On C51's grid of atoms " +
            "(nine here, from −1 to +1) risky is two spikes at the ends and safe is one lump near the " +
            "middle — a return of 0.30 falls between atoms 0.25 and 0.50 and is split 0.80 / 0.20 " +
            "between them, the same projection applied after every Bellman backup. A DQN sees only " +
            "the means, 0.00 and 0.30. Make the risky odds 0.65 and the means tie; make them 0.8 and " +
            "the risky mean is 0.60 and DQN switches to it. The distribution keeps what the mean " +
            "throws away: the average of the worst 25% of outcomes is −1.00 for risky and 0.25 for " +
            "safe, so a cautious agent can choose by risk, not only by expectation.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("−1", 0.5f, FigureTone.Warn),
                FigureBar("−.75", 0f, FigureTone.Muted),
                FigureBar("−.5", 0f, FigureTone.Muted),
                FigureBar("−.25", 0f, FigureTone.Muted),
                FigureBar("0", 0f, FigureTone.Muted),
                FigureBar(".25", 0.8f, FigureTone.Accent),
                FigureBar(".5", 0.2f, FigureTone.Accent),
                FigureBar(".75", 0f, FigureTone.Muted),
                FigureBar("+1", 0.5f, FigureTone.Warn),
            ),
            yLabel = "P(return): risky (ends) · safe (middle)",
        ),
    ),
    whatIsIt = listOf(
        "Distributional RL learns the whole distribution of returns for each action instead of only its mean. C51 is the canonical version: it represents that distribution as probabilities on 51 fixed, evenly spaced atoms between a minimum and maximum return, and applies the Bellman update to the distribution itself — shift every atom by the reward, shrink it by γ, and project the result back onto the fixed grid.",
        "The lab shows what a single Q-value throws away. A risky action pays ±1 with even odds, expected return 0.00; a safe one always pays 0.30. A DQN sees only those two numbers and picks safe. Raise the risky action's odds of +1 to 0.65 and the two means are equal — a plain Q-network cannot tell them apart at all; at 0.8 the risky mean is 0.60 and DQN picks it. On a 9-atom grid from −1 to +1, risky is two spikes at the ends and safe is one lump in the middle, and the projection step is visible too: a return of 0.30 sits between atoms 0.25 and 0.50 and is split 0.80 / 0.20 between them.",
        "Keeping the distribution enables choices the mean cannot express. The average of the worst 25% of outcomes — CVaR — is −1.00 for the risky action and 0.25 for the safe one, so a cautious agent can prefer safe even when risky has the higher mean. Even when the agent still acts on the mean, learning the full distribution gives a richer training signal and was one of the most valuable ingredients in Rainbow.",
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
        "In the lab the worst-25% average (CVaR) is −1.00 for the risky action and 0.25 for the safe one — a distinction a single mean cannot make.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
    ),
)
