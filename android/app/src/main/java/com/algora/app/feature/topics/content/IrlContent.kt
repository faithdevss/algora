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

internal val irlContent = TopicContent(
    topicId = "irl",
    figure = Figure(
        caption = "The payoff of recovering a reward instead of copying actions, from the page's lab. " +
            "Maximum-entropy IRL adjusts a reward until its soft-optimal policy visits states as " +
            "often as the expert does — after 150 iterations the goal is worth 5.3, the middle row " +
            "1.4–2.0 and the outer cells about −1, and the visitation mismatch has fallen from 1.44 " +
            "to 0.024. On the demonstrated start that policy matches the expert's 100% success. The " +
            "row below is a start cell the expert never demonstrated from. Behaviour cloning only " +
            "knows what to do in states it has seen, and succeeds 47% of the time; the IRL policy " +
            "knows where it is trying to go and succeeds 99%. A reward generalises where a " +
            "lookup table of actions cannot.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("47%", "99%"),
            ),
            rowHeaders = listOf("new start cell"),
            colHeaders = listOf("behaviour cloning", "IRL policy"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(0, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Inverse reinforcement learning asks the opposite question to RL: given an expert's behaviour, what reward would make it optimal? Recovering the objective rather than copying the actions matters because a reward transfers — it explains behaviour in situations the expert never demonstrated.",
        "The lab uses maximum-entropy IRL, whose only target is the expert's expected state visitation — how long it spends where. It starts with no reward, computes the soft-optimal policy for the current guess, and nudges the reward up in states the expert visits more than the learner and down where the learner over-visits: r ← r + 0.2·(μ_expert − μ_learner). After 5 iterations reward is already piling up along the middle row and at the goal; after 150, the goal is worth 5.3, the middle row 1.4–2.0 and the outer cells about −1, and the visitation mismatch has fallen from 1.44 to 0.024. The recovered reward's policy reaches the goal 100% of the time under the same slips — the expert's rate — without copying a single action.",
        "The payoff shows on start cells the expert never demonstrated: from a new row, the IRL policy succeeds 99% of the time, while behaviour cloning manages 47%, because a reward says where to go and a cloned policy only says what to do in states it has seen. The costs are an RL solve inside every iteration and the fundamental ambiguity that many rewards explain the same behaviour — the maximum-entropy principle is what picks one.",
    ),
    steps = listOf(
        StepCard(1, "Observe the Expert", "Collect demonstrations assumed to be (near-)optimal.", 0xFF818CF8),
        StepCard(2, "Hypothesize a Reward", "Parameterize a reward function, often over state features.", 0xFF60A5FA),
        StepCard(3, "Match Behavior", "Adjust the reward so its optimal policy reproduces the expert's feature statistics.", 0xFF10B981),
        StepCard(4, "Resolve Ambiguity", "Max-entropy IRL chooses the maximum-entropy distribution over trajectories (P(τ) ∝ exp R(τ)) that matches the demos' feature counts, which pins down a reward.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Feature matching", "E_π[φ] = E_expert[φ]", "Match expected feature counts."),
        FormulaEntry("Ill-posed", "many rewards fit", "Multiple rewards explain the same behavior."),
        FormulaEntry("MaxEnt IRL", "max-entropy trajectory distribution", "P(τ) ∝ exp R(τ), matching expert feature counts; resolves the ambiguity in a principled way."),
    ),
    notationKey = listOf(
        NotationEntry("reward function", "the inferred objective R(s)"),
        NotationEntry("φ(s)", "state features the reward is built from"),
        NotationEntry("ill-posed", "many rewards explain the same demos"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Max-entropy IRL (idea)",
            accentColor = 0xFF6366F1,
            code = """
                # Learn reward weights so the optimal policy matches expert feature counts.
                for _ in range(iters):
                    policy = solve_mdp(reward_weights)              # inner RL
                    grad = expert_feature_counts - policy_feature_counts(policy)
                    reward_weights += lr * grad                    # feature matching
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF818CF8, "Driving & Navigation", "Recovering the cost function behind human route choices."),
        ApplicationCard("robot", 0xFF60A5FA, "Robot Skill Transfer", "Inferring goals so robots generalize demonstrated tasks."),
        ApplicationCard("bulb", 0xFF10B981, "Reward Design", "Learning hard-to-specify rewards from examples instead of hand-coding them."),
    ),
    takeaways = listOf(
        "IRL infers the reward an expert optimizes, not just their actions.",
        "Knowing the reward generalizes better than cloning behavior.",
        "It's ill-posed — max-entropy IRL resolves the reward ambiguity.",
        "GAIL sidesteps recovering an explicit reward while capturing the intent.",
        "In the lab, from a start the expert never demonstrated, the IRL policy succeeds 99% of the time; behaviour cloning 47%.",
    ),
    crossLinks = listOf(
        CrossLink("imitation_learning", "Imitation Learning"),
        CrossLink("gail", "GAIL"),
    ),
)
