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

internal val cqlContent = TopicContent(
    topicId = "cql",
    figure = Figure(
        caption = "The page's lab: the same 397-step log as the offline RL page, the same Q estimator, " +
            "and only CQL's penalty weight α changed — then each resulting policy deployed from s0. " +
            "At α = 0 (plain fitted Q-iteration) and at α = 0.3 the noisy +2.50 lottery estimate " +
            "still wins the max, the policy chases it, and the return is 0.000 — below even the " +
            "behaviour policy's 0.158. At α = 1 the penalty pushes Q down wherever the policy would " +
            "act more than the data does — the lottery is 10% of logged actions at s0 but would get " +
            "42% under the inflated values — and the policy recovers the best supported action: " +
            "0.656, the optimum. α = 3 gets the same. Too little conservatism trusts the noise; far " +
            "too much would collapse to copying the behaviour policy.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("α = 0", 0f, FigureTone.Warn),
                FigureBar("α = 0.3", 0f, FigureTone.Warn),
                FigureBar("behaviour", 0.241f, FigureTone.Muted),
                FigureBar("α = 1", 1f, FigureTone.Accent),
                FigureBar("α = 3", 1f, FigureTone.Accent),
            ),
            yLabel = "deployed return from s0, 0 to 0.656",
        ),
    ),
    whatIsIt = listOf(
        "Conservative Q-Learning is a fix for offline RL's central failure: an action the log barely covers gets a noisy value, and the max in the Bellman backup selects the noise. CQL does not try to estimate those actions better; it refuses to trust them. It adds a penalty that pushes Q down wherever the learned policy would put more probability than the data does, and up on the actions actually logged, so it learns a lower bound on the policy's value rather than an optimistic guess.",
        "The lab uses the same 397-transition log as the offline RL page, where a ±5 lottery worth exactly 0 is estimated at +2.50 at s0 from 12 pulls. The penalty scales with μ(a|s) / π̂β(a|s) — the policy's probability over the data's — which is largest exactly where the data is thin: the lottery is 10% of logged actions at s0 but would take 42% under a softmax over the inflated Q. With α = 0 (plain fitted Q-iteration) and with α = 0.3, the policy still heads for the lottery and the deployed return is 0.000. At α = 1 and α = 3 it recovers the best supported action and scores 0.656, the optimum — on the same log with the same estimator.",
        "Conservatism is a dial. Too little and extrapolation error wins; too much and the policy collapses to cloning the behaviour policy, unable to improve on it. CQL became one of the standard offline RL baselines precisely because the lower bound makes it fail safely: when it is wrong, it is wrong by being cautious.",
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
        "In the lab, on the same log, α = 0 and 0.3 still chase the noisy lottery (return 0.000); α = 1 and 3 recover the optimum, 0.656.",
    ),
    crossLinks = listOf(
        CrossLink("offline_rl", "Offline RL"),
        CrossLink("dqn", "DQN"),
    ),
)
