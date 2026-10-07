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

internal val metaRlContent = TopicContent(
    topicId = "meta_rl",
    figure = Figure(
        caption = "The page's lab: steps needed to reach a new goal, episode by episode, for a " +
            "Q-learner starting from scratch and one starting from values meta-learned across four " +
            "training tasks that all put the goal in the bottom-right. The optimal path is 10 steps " +
            "(dashed). From scratch the first episode takes 40.0 steps. The meta-learned start " +
            "already points down and right, so the held-out goal is found in 18.5, then 14.1, 12.1 " +
            "and 11.0 by episode 10. The prior only helps inside the family it was learned from: " +
            "for a goal outside it, episode 3 takes 39.0 steps from the meta start against 40.0 " +
            "from scratch, against 12.1 for the held-out goal. Meta-learning buys speed by betting " +
            "on which tasks will come.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "optimal, 10 steps",
                    listOf(FigurePoint(0f, 0.25f), FigurePoint(1f, 0.25f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "meta-learned start",
                    listOf(
                        FigurePoint(0f, 0.463f), FigurePoint(0.111f, 0.353f), FigurePoint(0.222f, 0.303f),
                        FigurePoint(0.444f, 0.28f), FigurePoint(1f, 0.275f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0f, 1f, "scratch, ep 1: 40.0", FigureTone.Warn),
                FigurePoint(0f, 0.463f, "18.5"),
                FigurePoint(1f, 0.275f, "11.0"),
            ),
            xLabel = "episode 1 → 10",
            yLabel = "steps to the goal, 0 to 40",
        ),
    ),
    whatIsIt = listOf(
        "Meta-RL learns across a family of related tasks so that a new task from the same family can be solved quickly. What transfers is not a policy for any one task but a fast way to find one: a good initialisation, an exploration strategy, or a recurrent policy that adapts within an episode. MAML and RL² are the two classic forms.",
        "The lab uses the initialisation form on a grid where every task puts the goal somewhere in the bottom-right, and the agent is told none of them. Averaging the solutions of four training tasks gives starting values that already point down and right — it cannot know which cell, but every task rewards that direction. On a held-out goal whose optimal path is 10 steps, a learner starting from scratch takes 40.0 steps in its first episode; starting from the meta-learned values it takes 18.5, then 14.1, 12.1, and 11.0 by episode 10.",
        "Meta-learning buys speed inside the family only. A prior is a bet about which tasks will come, and outside the family it is mostly dead weight: on an out-of-distribution goal, in episode 3 the meta-start takes 39.0 steps against 40.0 from scratch, against 12.1 for the held-out goal. The quality of meta-RL is bounded by how well the training tasks represent the ones that will actually arrive.",
    ),
    steps = listOf(
        StepCard(1, "Distribution of Tasks", "Train over many related tasks rather than a single one.", 0xFF818CF8),
        StepCard(2, "Inner Loop", "For each task, take a few gradient steps from the shared initialization.", 0xFF60A5FA),
        StepCard(3, "Outer Loop", "Update the initialization so that post-adaptation performance is high across tasks.", 0xFF10B981),
        StepCard(4, "Fast Adaptation", "At test time, a new task is solved with just a few adaptation steps.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Inner adapt", "θ′ᵢ = θ − α∇_θ L_i(θ)", "Task-specific fine-tune."),
        FormulaEntry("Meta objective", "min_θ Σᵢ L_i(θ′ᵢ)", "Good after adaptation on each task."),
        FormulaEntry("Second-order", "grad through the inner update", "MAML differentiates the adaptation."),
    ),
    notationKey = listOf(
        NotationEntry("task distribution", "the family of tasks trained over"),
        NotationEntry("inner/outer loop", "adaptation vs meta-update"),
        NotationEntry("MAML", "Model-Agnostic Meta-Learning"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "MAML meta-update (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                meta_loss = 0
                for task in sample_tasks():
                    theta_prime = theta - alpha * grad(loss(theta, task.support))
                    meta_loss += loss(theta_prime, task.query)   # after adaptation
                theta -= beta * grad(meta_loss, theta)           # meta-update
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ExplorationPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Fast Robot Adaptation", "Adapting to new terrains or payloads in a few trials."),
        ApplicationCard("flask", 0xFF60A5FA, "Few-Shot Learning", "MAML also underpins few-shot supervised learning."),
        ApplicationCard("bulb", 0xFF10B981, "Sample-Efficient Transfer", "Reusing structure shared across a task family."),
    ),
    takeaways = listOf(
        "Meta-RL learns to learn: adapt to new tasks in a few trials.",
        "MAML finds an initialization from which a few gradient steps suffice.",
        "It trains over a task distribution with an inner-adapt / outer-meta loop.",
        "It targets fast adaptation and sample-efficient transfer, not single-task mastery.",
        "In the lab a held-out goal takes 18.5 steps in episode 1 from the meta-learned start against 40.0 from scratch — but an out-of-family goal gains almost nothing (39.0).",
    ),
    crossLinks = listOf(
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("ppo", "PPO"),
    ),
)
