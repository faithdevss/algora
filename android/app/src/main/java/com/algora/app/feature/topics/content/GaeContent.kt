package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gaeContent = TopicContent(
    topicId = "gae",
    whatIsIt = listOf(
        "Generalized Advantage Estimation (GAE) computes advantage estimates that trade off bias and variance with a single knob, λ, blending short and long-horizon TD errors.",
        "It's the standard advantage estimator inside PPO and TRPO: an exponentially-weighted average of n-step advantages that smooths the noisy signal without adding much bias.",
    ),
    steps = listOf(
        StepCard(1, "Compute TD Residuals", "δₜ = rₜ + γV(sₜ₊₁) − V(sₜ) at every step.", 0xFF818CF8),
        StepCard(2, "Exponentially Weight", "Combine future residuals with decay (γλ)ᵏ.", 0xFF60A5FA),
        StepCard(3, "Tune λ", "λ→0 gives low-variance one-step TD; λ→1 gives low-bias Monte-Carlo.", 0xFF10B981),
        StepCard(4, "Feed the Policy Update", "Use the resulting advantages to weight the policy gradient.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("TD residual", "δₜ = rₜ + γV(sₜ₊₁) − V(sₜ)", "One-step temporal-difference error."),
        FormulaEntry("GAE", "Âₜ = Σ (γλ)ᵏ δₜ₊ₖ", "Exponentially-weighted advantage."),
        FormulaEntry("λ knob", "0 ≤ λ ≤ 1", "Bias–variance trade-off dial."),
    ),
    notationKey = listOf(
        NotationEntry("δ", "TD residual"),
        NotationEntry("λ", "GAE decay — bias/variance trade-off"),
        NotationEntry("Âₜ", "estimated advantage at step t"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "GAE (backward pass)",
            accentColor = 0xFF6366F1,
            code = """
                adv = 0.0
                advantages = []
                for t in reversed(range(T)):
                    delta = r[t] + gamma * V[t + 1] * mask[t] - V[t]
                    adv = delta + gamma * lam * mask[t] * adv
                    advantages.insert(0, adv)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "PPO / TRPO", "GAE is the default advantage estimator in these leading policy-gradient methods."),
        ApplicationCard("bulb", 0xFF60A5FA, "Bias–Variance Control", "One λ dial replaces choosing a fixed n-step horizon."),
        ApplicationCard("game", 0xFF10B981, "Stable Training", "Smoother advantages make policy updates more reliable."),
    ),
    takeaways = listOf(
        "GAE blends multi-step TD errors into a low-variance advantage estimate.",
        "λ tunes the bias–variance trade-off between one-step TD and Monte-Carlo.",
        "It's computed cheaply in one backward pass over a trajectory.",
        "It's a near-universal component of modern on-policy actor-critic methods.",
    ),
    crossLinks = listOf(
        CrossLink("actor_critic", "Actor-Critic"),
        CrossLink("ppo", "PPO"),
    ),
)
