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

internal val trpoContent = TopicContent(
    topicId = "trpo",
    figure = Figure(
        caption = "The page's lab, three runs of the same policy-gradient direction. With a modest " +
            "step size the policy improves steadily, return 0.08 → 0.59, and no update moves it " +
            "far: the largest KL divergence between consecutive policies is 0.026. Raise the step " +
            "size to 20 and one noisy advantage estimate is enough to throw the policy somewhere " +
            "the data never covered — KL 5.45 in a single update — and it never recovers, ending " +
            "at −0.26 with the goal never reached again. TRPO keeps the large step size but adds a " +
            "hard constraint on the KL between the old and new policy; held to 0.004, the " +
            "aggressive run is safe again and reaches 0.63. The constraint costs a conjugate-" +
            "gradient solve and a line search per update — the machinery PPO's clipping replaced.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.026", "0.59"),
                listOf("5.45", "−0.26"),
                listOf("0.004", "0.63"),
            ),
            rowHeaders = listOf("small step", "large step", "large + KL cap"),
            colHeaders = listOf("max KL / update", "final return"),
            marks = listOf(
                FigureCell(1, 0, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "TRPO — Trust Region Policy Optimization — starts from the observation that a policy gradient tells you a direction, not a distance. Take too large a step along a noisy gradient and the policy lands somewhere the batch that justified the step says nothing about; in RL that is worse than in supervised learning, because a bad policy then collects bad data and may never recover.",
        "The lab shows the collapse. At a modest step size the policy improves steadily — return 0.08 → 0.59 — and the largest KL divergence between consecutive policies is 0.026. Push the step size to 20 and the same gradient direction destroys it: the KL between successive policies hits 5.45, and the return ends at −0.26 with the agent never reaching the goal again. TRPO maximises the surrogate objective subject to a hard KL constraint between the old and new policy, so no single update can move the policy more than a fixed distance in distribution space. With the same large learning rate and the KL held to 0.004, the return reaches 0.63 without the collapse.",
        "The price is machinery: enforcing the constraint needs a natural-gradient step through a conjugate-gradient solve and a backtracking line search on every update. PPO was designed to get most of the same safety with a simple clipped objective and ordinary SGD, which is why it replaced TRPO as the default — but the idea of a trust region is the one PPO kept.",
    ),
    steps = listOf(
        StepCard(1, "Surrogate Objective", "Maximize an importance-weighted advantage objective over the new policy.", 0xFF818CF8),
        StepCard(2, "KL Constraint", "Require the new policy to stay within a small KL distance of the old one.", 0xFF60A5FA),
        StepCard(3, "Solve Approximately", "Use a conjugate-gradient step on the natural gradient, then line-search to satisfy the constraint.", 0xFF10B981),
        StepCard(4, "Stable Improvement", "The trust region keeps updates reliably stable; exact monotonic improvement is only guaranteed for the theoretical KL-penalized surrogate.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "max E[ (π/π_old)·A ]", "Importance-weighted advantage."),
        FormulaEntry("Constraint", "E[ KL(π_old ‖ π) ] ≤ δ", "Bounded policy change."),
        FormulaEntry("Solver", "natural gradient + line search", "Conjugate gradient avoids forming the Hessian."),
    ),
    notationKey = listOf(
        NotationEntry("KL", "Kullback–Leibler divergence between policies"),
        NotationEntry("δ", "trust-region size"),
        NotationEntry("natural gradient", "gradient scaled by the Fisher information"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "TRPO objective and constraint",
            accentColor = 0xFF6366F1,
            code = """
                # Maximize surrogate advantage subject to a KL trust region:
                ratio = torch.exp(new_log_prob - old_log_prob)
                surrogate = (ratio * advantages).mean()
                # constraint: mean KL(old || new) <= delta
                # solved via conjugate gradient + backtracking line search.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Continuous Control", "TRPO delivered stable locomotion policies on MuJoCo benchmarks."),
        ApplicationCard("bulb", 0xFF60A5FA, "Monotonic Improvement", "Its theory motivates updates that don't collapse the policy (a bound for the exact penalized surrogate, not a guarantee in practice)."),
        ApplicationCard("chip", 0xFF10B981, "Precursor to PPO", "PPO simplifies TRPO's constraint into a clipped objective."),
    ),
    takeaways = listOf(
        "TRPO bounds each policy update by a KL trust region for stability.",
        "It optimizes an importance-weighted surrogate advantage objective.",
        "The constrained natural-gradient solve is powerful but complex to implement.",
        "PPO trades TRPO's hard constraint for a simple clip, keeping most of the benefit.",
        "In the lab a large step pushes the KL between policies to 5.45 and the return to −0.26; the same step with KL held to 0.004 reaches 0.63.",
    ),
    crossLinks = listOf(
        CrossLink("ppo", "PPO"),
        CrossLink("gae", "GAE"),
    ),
)
