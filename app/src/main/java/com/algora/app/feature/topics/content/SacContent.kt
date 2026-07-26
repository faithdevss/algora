package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val sacContent = TopicContent(
    topicId = "sac",
    whatIsIt = listOf(
        "Soft Actor-Critic (SAC) is an off-policy actor-critic that maximizes reward plus policy entropy, so the agent stays exploratory and robust rather than collapsing to a single deterministic action.",
        "The entropy bonus is baked into the objective, and a learnable temperature automatically balances exploration against exploitation.",
    ),
    steps = listOf(
        StepCard(1, "Maximum-Entropy Objective", "Reward the policy for both high return and high entropy (randomness).", 0xFF818CF8),
        StepCard(2, "Stochastic Actor", "The policy outputs a distribution; actions are sampled and reparameterized for gradients.", 0xFF60A5FA),
        StepCard(3, "Twin Soft Critics", "Two Q-networks (min target) include the entropy term in their bootstrap.", 0xFF10B981),
        StepCard(4, "Auto-Tune Temperature", "A learnable α adjusts the entropy weight to hit a target entropy.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "E[Σ rₜ + α·H(π(·|sₜ))]", "Reward plus entropy."),
        FormulaEntry("Soft target", "y = r + γ(minᵢ Qᵢ′ − α·logπ)", "Entropy folded into the target."),
        FormulaEntry("Temperature", "α auto-tuned", "Balances exploration and exploitation."),
    ),
    notationKey = listOf(
        NotationEntry("H(π)", "policy entropy"),
        NotationEntry("α", "temperature — the entropy weight"),
        NotationEntry("reparameterization", "trick for low-variance stochastic gradients"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "SAC soft critic target",
            accentColor = 0xFF6366F1,
            code = """
                a2, logp2 = actor.sample(s2)                    # reparameterized
                q_next = torch.min(target_q1(s2, a2), target_q2(s2, a2)) - alpha * logp2
                y = r + gamma * q_next * (1 - done)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Real-World Robotics", "Its sample efficiency and stability suit learning on physical robots."),
        ApplicationCard("game", 0xFF60A5FA, "Continuous Benchmarks", "State-of-the-art on MuJoCo continuous-control suites."),
        ApplicationCard("bulb", 0xFF10B981, "Robust Exploration", "The entropy objective keeps the policy from prematurely collapsing."),
    ),
    takeaways = listOf(
        "SAC maximizes reward plus entropy for robust, exploratory policies.",
        "It's off-policy, stochastic, and uses twin soft critics.",
        "An auto-tuned temperature removes a key hyperparameter headache.",
        "It's a top choice for continuous control alongside TD3.",
    ),
    crossLinks = listOf(
        CrossLink("td3", "TD3"),
        CrossLink("max_entropy_rl", "Maximum Entropy RL"),
    ),
)
