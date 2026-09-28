package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val td3Content = TopicContent(
    topicId = "td3",
    whatIsIt = listOf(
        "TD3 (Twin Delayed DDPG) fixes DDPG's overestimation and instability with three targeted tricks, becoming a reliable continuous-control algorithm.",
        "It borrows Double DQN's insight — twin critics take the minimum — and adds delayed actor updates plus target-action smoothing.",
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
    ),
    crossLinks = listOf(
        CrossLink("ddpg", "DDPG"),
        CrossLink("sac", "SAC"),
    ),
)
