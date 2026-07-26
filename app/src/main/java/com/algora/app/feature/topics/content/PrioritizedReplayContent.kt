package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val prioritizedReplayContent = TopicContent(
    topicId = "prioritized_replay",
    whatIsIt = listOf(
        "Prioritized Experience Replay samples transitions in proportion to how much the agent can learn from them, measured by their TD error, instead of uniformly.",
        "Surprising transitions — where the prediction was most wrong — are replayed more often, speeding up learning, with importance-sampling weights to correct the resulting bias.",
    ),
    steps = listOf(
        StepCard(1, "Score by TD Error", "Each transition's priority is its absolute TD error — how surprising it was.", 0xFF818CF8),
        StepCard(2, "Sample Proportionally", "Draw transitions with probability proportional to priority^α.", 0xFF60A5FA),
        StepCard(3, "Correct the Bias", "Non-uniform sampling skews the gradient; importance-sampling weights undo it.", 0xFF10B981),
        StepCard(4, "Update Priorities", "After training, refresh each sampled transition's priority with its new TD error.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Priority", "pᵢ = |δᵢ| + ε", "Absolute TD error, ε keeps it non-zero."),
        FormulaEntry("Sample prob", "P(i) = pᵢ^α / Σ pⱼ^α", "α tunes how much prioritization applies."),
        FormulaEntry("IS weight", "wᵢ = (1/(N·P(i)))^β", "Corrects the sampling bias in the update."),
    ),
    notationKey = listOf(
        NotationEntry("δ", "TD error of a transition"),
        NotationEntry("α", "prioritization strength (0 = uniform)"),
        NotationEntry("β", "importance-sampling correction, annealed to 1"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Priority and IS weight",
            accentColor = 0xFF6366F1,
            code = """
                priority = (abs(td_error) + eps) ** alpha
                prob = priority / total_priority
                is_weight = (N * prob) ** (-beta)     # bias correction, applied to the loss
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF818CF8, "Faster Learning", "Focusing on informative transitions cuts the samples needed to learn."),
        ApplicationCard("chip", 0xFF60A5FA, "Rainbow Component", "One of the six ingredients combined in Rainbow DQN."),
        ApplicationCard("robot", 0xFF10B981, "Sparse-Reward Tasks", "Rare, high-error transitions get replayed instead of drowned out."),
    ),
    takeaways = listOf(
        "Prioritized replay samples high-TD-error transitions more often to learn faster.",
        "Importance-sampling weights correct the bias non-uniform sampling introduces.",
        "A sum-tree data structure makes proportional sampling efficient.",
        "It's a drop-in upgrade to uniform experience replay in off-policy agents.",
    ),
    crossLinks = listOf(
        CrossLink("experience_replay", "Experience Replay"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
    ),
)
