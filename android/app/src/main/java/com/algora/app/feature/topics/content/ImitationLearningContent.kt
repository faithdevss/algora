package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val imitationLearningContent = TopicContent(
    topicId = "imitation_learning",
    whatIsIt = listOf(
        "Imitation learning trains an agent to copy expert demonstrations instead of learning from a reward signal — useful when good behavior is easy to demonstrate but hard to reward.",
        "Its simplest form, behavioral cloning, treats control as supervised learning: map states to the expert's actions.",
    ),
    steps = listOf(
        StepCard(1, "Collect Demonstrations", "Gather state-action pairs from an expert.", 0xFF818CF8),
        StepCard(2, "Supervised Fit", "Train a policy to predict the expert's action for each state.", 0xFF60A5FA),
        StepCard(3, "Watch for Drift", "Small errors push the agent into states the expert never visited (covariate shift).", 0xFF10B981),
        StepCard(4, "Correct with DAgger", "Iteratively query the expert on the agent's own states to fix compounding errors.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Behavioral cloning", "min E[ℓ(π(s), a_expert)]", "Supervised action prediction."),
        FormulaEntry("Covariate shift", "errors compound off-distribution", "The core failure mode."),
        FormulaEntry("DAgger", "aggregate expert labels on π's states", "Iterative correction."),
    ),
    notationKey = listOf(
        NotationEntry("behavioral cloning", "supervised state → action fitting"),
        NotationEntry("covariate shift", "test states differ from demo states"),
        NotationEntry("DAgger", "Dataset Aggregation algorithm"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Behavioral cloning (supervised)",
            accentColor = 0xFF6366F1,
            code = """
                # Treat (state, expert_action) pairs as a supervised dataset.
                pred = policy(states)
                loss = F.cross_entropy(pred, expert_actions)   # or MSE for continuous
                loss.backward(); optimizer.step()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robot Teaching", "Learning manipulation from human teleoperation demos."),
        ApplicationCard("map", 0xFF60A5FA, "Autonomous Driving", "Cloning expert driving from recorded human trajectories."),
        ApplicationCard("bulb", 0xFF10B981, "Reward-Free Learning", "When demonstrating is easier than designing a reward."),
    ),
    takeaways = listOf(
        "Imitation learning copies expert behavior instead of maximizing a reward.",
        "Behavioral cloning is simple supervised learning but suffers covariate shift.",
        "DAgger corrects compounding errors by relabeling the agent's own states.",
        "Inverse RL and GAIL go further by recovering the expert's intent.",
    ),
    crossLinks = listOf(
        CrossLink("irl", "IRL"),
        CrossLink("gail", "GAIL"),
    ),
)
