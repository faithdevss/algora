package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gaeContent = TopicContent(
    topicId = "gae",
    figure = Figure(
        caption = "The page's lab: the spread of policy-gradient estimates for five values of GAE's " +
            "λ, from the one-step TD error (λ = 0) to the full Monte Carlo advantage (λ = 1), with " +
            "the plain sampled return's 0.303 dashed for reference. The textbook expects the curve " +
            "to rise steadily with λ. It does not: the minimum is at λ = 0.5 (0.09), and λ = 0 is " +
            "noisier (0.12) — because this critic is itself an estimate with errors, and at λ = 0 " +
            "every bit of that error goes straight into the gradient. From 0.5 upward the trend is " +
            "monotone as expected: 0.15, 0.19, 0.25. The other half of the trade is bias, not drawn " +
            "here — the gradient's mean drifts 0.169 → 0.184 as λ rises and leans less on the " +
            "critic. λ ≈ 0.95 is the usual default: most of the variance reduction, little " +
            "dependence on a critic that may be wrong.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "sampled return, 0.303",
                    listOf(FigurePoint(0f, 0.977f), FigurePoint(1f, 0.977f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "GAE",
                    listOf(FigurePoint(0.000f, 0.387f), FigurePoint(0.500f, 0.290f), FigurePoint(0.900f, 0.484f), FigurePoint(0.950f, 0.606f), FigurePoint(1.000f, 0.803f)),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.5f, 0.290f, "λ 0.5: 0.09"),
                FigurePoint(0.95f, 0.606f, "0.95: 0.19", FigureTone.Primary),
            ),
            xLabel = "λ, 0 → 1",
            yLabel = "gradient sd, 0 to 0.31",
        ),
    ),
    whatIsIt = listOf(
        "Generalized Advantage Estimation is one knob for the bias-variance trade at the heart of policy gradients. The advantage of an action can be estimated from the sampled return (unbiased but noisy) or from the critic's one-step TD error (quiet but only as right as the critic). GAE takes an exponentially weighted average of every n-step advantage: Âₜ = Σₗ (γλ)ˡ δₜ₊ₗ. λ = 0 is the one-step TD error; λ = 1 is the full Monte Carlo advantage.",
        "The lab measures both ends and the middle. The Monte Carlo return gives gradient estimates with a standard deviation of 0.303; the one-step TD error, 0.120. Across λ = 0, 0.5, 0.9, 0.95 and 1 the spread runs 0.12, 0.09, 0.15, 0.19, 0.25 — and the minimum is at 0.5, not 0. The textbook picture of variance rising steadily with λ assumes an accurate critic; this critic is itself a noisy estimate, and at λ = 0 every bit of its error goes straight into the gradient.",
        "The other half of the trade is bias: the gradient's mean drifts from 0.169 to 0.184 as λ goes to 1, because at low λ the estimate leans on the critic, and a wrong critic pulls the gradient with it however many rollouts are averaged. λ ≈ 0.95 is the common default (here sd 0.188 against 0.249 at λ = 1) because it keeps most of the variance reduction while depending on the critic only weakly. PPO uses GAE by default.",
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
        "In the lab gradient noise runs 0.12, 0.09, 0.15, 0.19, 0.25 for λ = 0, 0.5, 0.9, 0.95, 1 — the minimum is not at λ = 0 when the critic is imperfect.",
    ),
    crossLinks = listOf(
        CrossLink("actor_critic", "Actor-Critic"),
        CrossLink("ppo", "PPO"),
    ),
)
