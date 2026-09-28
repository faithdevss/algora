package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val offlineRlContent = TopicContent(
    topicId = "offline_rl",
    whatIsIt = listOf(
        "Offline (batch) RL learns a policy purely from a fixed dataset of previously-collected experience, with no further interaction with the environment.",
        "Its central challenge is distributional shift: the learned policy may prefer actions the dataset never covers, and the value function then extrapolates wildly on that unseen data.",
    ),
    steps = listOf(
        StepCard(1, "Fixed Dataset", "Start from logged transitions — no new exploration allowed.", 0xFF818CF8),
        StepCard(2, "Beware Out-of-Distribution Actions", "Q-values are unreliable for actions absent from the data.", 0xFF60A5FA),
        StepCard(3, "Constrain the Policy", "Stay close to the data-generating behavior, or penalize unfamiliar actions.", 0xFF10B981),
        StepCard(4, "Evaluate Carefully", "Off-policy evaluation estimates performance without risky live deployment.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Core problem", "distributional shift", "Policy drifts off the dataset's support."),
        FormulaEntry("Overestimation", "Q high on unseen actions", "Extrapolation errors compound."),
        FormulaEntry("Fix", "constrain or penalize", "Keep the policy near the data."),
    ),
    notationKey = listOf(
        NotationEntry("behavior policy", "the policy that produced the dataset"),
        NotationEntry("OOD action", "action outside the dataset's support"),
        NotationEntry("off-policy evaluation", "estimating a policy without deployment"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The offline RL setting",
            accentColor = 0xFF6366F1,
            code = """
                # No env.step() — learn only from a static buffer of logged transitions.
                dataset = load_logged_transitions()      # (s, a, r, s2, done)
                policy = train_offline(dataset)          # e.g. CQL, IQL, BCQ
                # Naive off-policy RL overestimates unseen actions; constraints are needed.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF818CF8, "Healthcare", "Learning treatment policies from historical records, where live trial is unsafe."),
        ApplicationCard("map", 0xFF60A5FA, "Autonomous Driving", "Reusing large logged driving datasets without risky on-road exploration."),
        ApplicationCard("finance", 0xFF10B981, "Recommendation & Ops", "Improving policies from past interaction logs without live experiments."),
    ),
    takeaways = listOf(
        "Offline RL learns from a fixed dataset with no environment interaction.",
        "Distributional shift and value overestimation on unseen actions are its core hazards.",
        "Methods constrain the policy toward the data or penalize OOD actions.",
        "It unlocks RL where exploration is expensive, unsafe, or impossible.",
    ),
    crossLinks = listOf(
        CrossLink("cql", "CQL"),
        CrossLink("decision_transformer", "Decision Transformer"),
    ),
)
