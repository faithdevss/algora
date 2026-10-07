package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val trpoContent = TopicContent(
    topicId = "trpo",
    whatIsIt = listOf(
        "Trust Region Policy Optimization (TRPO) improves a policy while constraining each update to stay within a 'trust region' — a bounded KL divergence from the old policy.",
        "That constraint prevents the destructively large policy jumps that plague vanilla policy gradients, motivated by a monotonic-improvement bound, and in practice giving stable updates (not a guaranteed improvement).",
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
    ),
    crossLinks = listOf(
        CrossLink("ppo", "PPO"),
        CrossLink("gae", "GAE"),
    ),
)
