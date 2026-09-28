package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val cqlContent = TopicContent(
    topicId = "cql",
    whatIsIt = listOf(
        "Conservative Q-Learning (CQL) is a leading offline-RL algorithm that deliberately learns pessimistic Q-values, pushing down the value of out-of-distribution actions.",
        "By ensuring the learned Q lower-bounds the true value, it stops the policy from exploiting overestimated actions the dataset never actually tried.",
    ),
    steps = listOf(
        StepCard(1, "Standard Bellman Loss", "Keep the usual TD objective on the dataset transitions.", 0xFF818CF8),
        StepCard(2, "Penalize OOD Actions", "Add a term that lowers Q for actions the current policy favors but the data lacks.", 0xFF60A5FA),
        StepCard(3, "Push Up Dataset Actions", "Simultaneously keep Q high for actions actually present in the data.", 0xFF10B981),
        StepCard(4, "Conservative Values", "The result lower-bounds the true value, preventing overestimation exploitation.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("CQL objective", "Bellman loss + α·(E_π[Q] − E_data[Q])", "Penalize policy actions, favor data actions."),
        FormulaEntry("Guarantee", "learned Q ≤ true Q", "A conservative lower bound."),
        FormulaEntry("α", "conservatism weight", "Trades pessimism against performance."),
    ),
    notationKey = listOf(
        NotationEntry("conservative", "Q-values deliberately pessimistic"),
        NotationEntry("OOD", "out-of-distribution actions"),
        NotationEntry("α", "strength of the conservative penalty"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "CQL penalty (idea)",
            accentColor = 0xFF6366F1,
            code = """
                bellman = mse(Q(s, a), td_target)
                # Push down Q on policy-sampled actions, up on dataset actions:
                conservative = (logsumexp(Q(s, all_actions), dim=1) - Q(s, a_data)).mean()
                loss = bellman + alpha * conservative
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF818CF8, "Safe Offline Learning", "Learning reliable policies from logged medical or industrial data."),
        ApplicationCard("robot", 0xFF60A5FA, "Robotics from Logs", "Reusing recorded robot data without unsafe exploration."),
        ApplicationCard("chart", 0xFF10B981, "Strong Offline Baseline", "A default, well-tested method for the offline-RL benchmark suites."),
    ),
    takeaways = listOf(
        "CQL learns conservative, lower-bounded Q-values for offline RL.",
        "It penalizes out-of-distribution actions while favoring dataset actions.",
        "This prevents the policy from exploiting overestimated unseen actions.",
        "The conservatism weight α trades safety against squeezing out performance.",
    ),
    crossLinks = listOf(
        CrossLink("offline_rl", "Offline RL"),
        CrossLink("dqn", "DQN"),
    ),
)
