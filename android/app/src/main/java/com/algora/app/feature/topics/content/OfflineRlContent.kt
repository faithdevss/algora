package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val offlineRlContent = TopicContent(
    topicId = "offline_rl",
    figure = Figure(
        caption = "The page's lab: a fixed log of 397 transitions from a policy that mostly walks right " +
            "and now and then pulls a ±5 lottery worth exactly 0 — and how many times each state's " +
            "lottery was actually tried, with the average reward those few pulls produced. With 2 " +
            "to 12 samples per state, the averages are noise (a standard error of about 2.2 at " +
            "n = 5), and at s0 twelve pulls happen to average +2.50. Fitted Q-iteration takes a max " +
            "over actions in every backup, so it picks up exactly that noise, and its greedy policy " +
            "heads for the lottery from every state. Deployed, it scores 0.000 from s0 — worse than " +
            "the behaviour policy it learned from (0.158) and far below always walking right " +
            "(0.656). The max systematically favours whatever the data supports least.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("12", "+2.50"),
                listOf("11", "+0.45"),
                listOf("2", "0.00"),
                listOf("4", "0.00"),
                listOf("7", "−0.71"),
            ),
            rowHeaders = listOf("s0", "s1", "s2", "s3", "s4"),
            colHeaders = listOf("lottery pulls logged", "mean reward (true 0)"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Offline RL learns a policy from a fixed log of someone else's experience, with no further interaction. That is the setting of most real data — hospital records, logged recommendations, driving logs — and it looks like ordinary RL with a bigger replay buffer. It is not, because the learned policy will want to take actions the log rarely or never tried, and their values can only be guessed.",
        "The lab builds the trap. A behaviour policy that mostly walks right, dithers, and occasionally pulls a ±5 lottery whose true value is exactly 0, produces 397 transitions. With only 2 to 12 lottery pulls per state, the empirical means are pure noise — a standard error of about 2.2 at n = 5 — and at s0 the 12 pulls happen to average +2.50. Fitted Q-iteration on the log takes its max over actions, so it picks up exactly that noise: its greedy policy heads for the lottery from every state.",
        "Deployed, the \"improved\" policy is worse than the log it learned from: it scores 0.000 from s0, against 0.158 for the behaviour policy and 0.656 for the optimum of always walking right. That is distributional shift in one number — the max in the Bellman backup systematically favours whatever the data supports least — and every offline method is a way to restrain it: penalise unsupported actions (CQL), constrain the policy to the data (BCQ, BEAR), or avoid querying them at all (IQL, decision transformers).",
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
        "In the lab a policy learned from the log scores 0.000, worse than the behaviour policy's 0.158, because the max picks up a noisy +2.50 lottery estimate.",
    ),
    crossLinks = listOf(
        CrossLink("cql", "CQL"),
        CrossLink("decision_transformer", "Decision Transformer"),
    ),
)
