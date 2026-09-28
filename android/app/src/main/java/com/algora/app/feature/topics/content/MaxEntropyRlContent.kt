package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val maxEntropyRlContent = TopicContent(
    topicId = "max_entropy_rl",
    whatIsIt = listOf(
        "Maximum-entropy RL augments the standard reward objective with a bonus for policy entropy, so the agent is rewarded for acting as randomly as possible while still succeeding.",
        "This 'act randomly where it doesn't hurt' principle improves exploration, robustness, and the ability to capture multiple good strategies instead of one brittle solution.",
    ),
    steps = listOf(
        StepCard(1, "Augment the Objective", "Add α·H(π) to the return: value both reward and unpredictability.", 0xFF818CF8),
        StepCard(2, "Soft Value Functions", "Value functions incorporate the entropy term into their bootstrapping.", 0xFF60A5FA),
        StepCard(3, "Softmax Policy", "The optimal policy becomes a Boltzmann distribution over soft Q-values.", 0xFF10B981),
        StepCard(4, "Temperature Trade-Off", "α tunes how much exploration is rewarded versus pure reward-seeking.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "J = E[Σ rₜ + α·H(π(·|sₜ))]", "Reward plus entropy."),
        FormulaEntry("Optimal policy", "π*(a|s) ∝ exp(Q_soft(s,a)/α)", "Boltzmann over soft Q-values."),
        FormulaEntry("Limit", "α→0 recovers standard RL", "Entropy weight controls the trade-off."),
    ),
    notationKey = listOf(
        NotationEntry("H(π)", "policy entropy"),
        NotationEntry("α", "temperature weighting entropy"),
        NotationEntry("soft value", "value including the entropy bonus"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Entropy-augmented return",
            accentColor = 0xFF6366F1,
            code = """
                # Standard reward plus an entropy bonus at each step.
                augmented_reward = reward + alpha * policy_entropy(state)
                # As alpha -> 0, this reduces to ordinary reward maximization.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robust Policies", "Entropy keeps behaviors flexible and resilient to perturbations."),
        ApplicationCard("bulb", 0xFF60A5FA, "Better Exploration", "The framework that motivates SAC and soft Q-learning."),
        ApplicationCard("game", 0xFF10B981, "Multimodal Solutions", "Captures several near-optimal strategies instead of one."),
    ),
    takeaways = listOf(
        "Max-entropy RL adds an entropy bonus so agents stay as random as success allows.",
        "It yields soft value functions and a Boltzmann-form optimal policy.",
        "The temperature α trades exploration against pure reward.",
        "SAC and soft Q-learning are its practical instantiations.",
    ),
    crossLinks = listOf(
        CrossLink("sac", "SAC"),
        CrossLink("boltzmann_exploration", "Boltzmann Exploration"),
    ),
)
