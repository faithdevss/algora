package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dynaQContent = TopicContent(
    topicId = "dyna_q",
    whatIsIt = listOf(
        "Dyna-Q blends model-free and model-based RL: it learns a model of the environment from real experience, then uses that model to generate extra simulated experience for planning.",
        "Every real step both updates Q-learning directly and trains a model, which then replays imagined transitions to squeeze far more learning out of each real interaction.",
    ),
    steps = listOf(
        StepCard(1, "Act & Learn Directly", "Take a real step and apply the standard Q-learning update.", 0xFF818CF8),
        StepCard(2, "Update the Model", "Record the observed (s,a) → (r, s′) so the model can reproduce it.", 0xFF60A5FA),
        StepCard(3, "Plan with Simulated Steps", "Sample past (s,a) pairs from the model and do Q-updates on the imagined outcomes.", 0xFF10B981),
        StepCard(4, "Repeat n Planning Steps", "More simulated updates per real step means faster convergence.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Direct RL", "Q(s,a) += α[r + γ maxQ(s′,·) − Q(s,a)]", "Real-experience Q-learning."),
        FormulaEntry("Model", "Model(s,a) → (r, s′)", "Learned from observed transitions."),
        FormulaEntry("Planning", "n simulated updates / real step", "Same update, imagined data."),
    ),
    notationKey = listOf(
        NotationEntry("model", "learned map (s,a) → (r, s′)"),
        NotationEntry("n", "planning steps per real step"),
        NotationEntry("planning", "Q-updates on simulated experience"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Dyna-Q planning loop",
            accentColor = 0xFF6366F1,
            code = """
                # 1) Direct RL from the real step, then update the model:
                q_update(s, a, r, s2)
                model[(s, a)] = (r, s2)

                # 2) Planning: n simulated updates from remembered transitions.
                for _ in range(n):
                    (ps, pa) = random.choice(list(model.keys()))
                    (pr, ps2) = model[(ps, pa)]
                    q_update(ps, pa, pr, ps2)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("chart", 0xFF818CF8, "Sample Efficiency", "Extracts far more learning per real interaction, vital when data is costly."),
        ApplicationCard("map", 0xFF60A5FA, "Navigation & Mazes", "Classic gridworld planning where a learned model speeds route-finding."),
        ApplicationCard("robot", 0xFF10B981, "Robotics", "Fewer real trials needed when a model can be replayed."),
    ),
    takeaways = listOf(
        "Dyna-Q integrates learning, model-building, and planning in one loop.",
        "Simulated experience from the learned model amplifies each real step.",
        "More planning steps trade compute for fewer real interactions.",
        "It's the bridge between tabular model-free and full model-based RL.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("mbpo", "MBPO"),
    ),
)
