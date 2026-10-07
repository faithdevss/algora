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

internal val td3Content = TopicContent(
    topicId = "td3",
    figure = Figure(
        caption = "The page's lab: how far a critic's value at the action the actor chose sits above " +
            "the true value. DDPG's actor climbs a single critic's gradient, so it seeks out " +
            "whatever that critic overvalues — the bias at its chosen action is +0.178, and the " +
            "policy ends up exploiting the critic's noise. TD3 trains two critics and takes the " +
            "smaller of their estimates in the target; noise that inflates one rarely inflates the " +
            "other at the same action, and the bias drops to 0.053. Its other two fixes — updating " +
            "the actor only every second critic step, and adding noise to the target action — keep " +
            "the actor from chasing an unsettled or spiky critic. The minimum is deliberately " +
            "pessimistic, because the errors are not symmetric: an undervalued action is simply " +
            "not chosen, an overvalued one becomes the policy.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("single critic (DDPG)", 1f, FigureTone.Warn),
                FigureBar("min of twin critics (TD3)", 0.298f, FigureTone.Accent),
            ),
            yLabel = "critic bias at the chosen action, 0 to 0.178",
        ),
    ),
    whatIsIt = listOf(
        "TD3 — Twin Delayed DDPG — is three fixes to DDPG, and the first is the one that matters. DDPG's actor climbs its critic's gradient, so it seeks out wherever the critic overvalues an action; a single critic's value at the action the actor picks is therefore biased upward, and the actor ends up exploiting the critic's errors instead of the environment's rewards.",
        "The lab measures it: at the chosen action, a single critic's value is too high by 0.178. TD3 trains two critics and uses the smaller of their two estimates in the target. Noise that inflates one critic rarely inflates the other at the same action, so the minimum lands much nearer the truth — the bias falls to 0.053. The second fix delays the actor, updating it once for every two critic updates so it chases a critic that has had time to settle. The third, target policy smoothing, adds noise to the target action so the critic cannot develop a sharp spike for the actor to exploit.",
        "The minimum of two critics is deliberately pessimistic — biased slightly low — and TD3 accepts that, because the two errors are not symmetric: an underestimated action simply does not get chosen, while an overestimated one becomes the policy. TD3 and SAC are the standard off-policy choices for continuous control.",
    ),
    steps = listOf(
        StepCard(1, "Twin Critics", "Learn two Q-networks and use the smaller estimate to curb overestimation.", 0xFF818CF8),
        StepCard(2, "Delayed Policy Updates", "Update the actor (and targets) less often than the critics, for stability.", 0xFF60A5FA),
        StepCard(3, "Target Policy Smoothing", "Add clipped noise to the target action so the critic can't exploit sharp Q peaks.", 0xFF10B981),
        StepCard(4, "Off-Policy Learning", "Otherwise it keeps DDPG's replay buffer and soft target updates.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Twin target", "y = r + γ·minᵢ Qᵢ′(s′, ã)", "Minimum of two critics reduces bias."),
        FormulaEntry("Target smoothing", "ã = μ′(s′) + clip(noise, −c, c)", "Regularizes the target action."),
        FormulaEntry("Delayed actor", "update every d critic steps", "Stabilizes the moving target."),
    ),
    notationKey = listOf(
        NotationEntry("twin critics", "Q₁, Q₂ — take the min"),
        NotationEntry("d", "actor-update delay interval"),
        NotationEntry("policy smoothing", "noise added to the target action"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "TD3 twin-critic target",
            accentColor = 0xFF6366F1,
            code = """
                noise = (torch.randn_like(a) * sigma).clamp(-c, c)
                a2 = (target_actor(s2) + noise).clamp(-1, 1)      # smoothed target action
                y = r + gamma * torch.min(target_q1(s2, a2), target_q2(s2, a2)) * (1 - done)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robust Continuous Control", "A dependable default for MuJoCo-style locomotion tasks."),
        ApplicationCard("chip", 0xFF60A5FA, "DDPG Replacement", "Preferred over DDPG wherever its instability bit."),
        ApplicationCard("bulb", 0xFF10B981, "Overestimation Fix", "Its twin-min target is a widely reused stabilization trick."),
    ),
    takeaways = listOf(
        "TD3 fixes DDPG with twin critics, delayed actor updates, and target smoothing.",
        "Taking the min of two critics counters Q-value overestimation.",
        "It's more stable and reliable than DDPG at similar sample efficiency.",
        "SAC is its main rival, adding stochasticity and entropy for exploration.",
        "In the lab a single critic overvalues the chosen action by 0.178; the minimum of two critics by 0.053.",
    ),
    crossLinks = listOf(
        CrossLink("ddpg", "DDPG"),
        CrossLink("sac", "SAC"),
    ),
)
