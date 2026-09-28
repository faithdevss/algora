package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val metaRlContent = TopicContent(
    topicId = "meta_rl",
    whatIsIt = listOf(
        "Meta-RL, or 'learning to learn', trains an agent across many tasks so it can adapt to a brand-new task in just a handful of trials.",
        "MAML, the canonical method, learns an initialization of the parameters from which a few gradient steps yield strong performance on any related task.",
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
    ),
    crossLinks = listOf(
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("ppo", "PPO"),
    ),
)
