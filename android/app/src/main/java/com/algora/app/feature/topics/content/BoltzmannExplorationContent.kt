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

internal val boltzmannExplorationContent = TopicContent(
    topicId = "boltzmann_exploration",
    figure = Figure(
        caption = "The page's lab, the same four slot machines as the ε-greedy page (30%, 50%, 45% " +
            "and 72%), choosing by softmax over the current estimates, P(a) ∝ exp(Q(a)/τ), and the " +
            "share of 200 pulls that reached the best arm, averaged over 50 runs. Too cold, " +
            "τ = 0.05, and the softmax is almost greedy: an early winner is drawn with probability " +
            "near 100% and the best arm can go untried — 28%. Too hot, τ = 0.5, and it is nearly " +
            "uniform, spending pulls on every arm regardless of evidence — 37%. In between, " +
            "τ = 0.15, the estimates steer the draws without locking any arm out: the best arm " +
            "gets 63% and the policy earns 0.620 per pull. Unlike ε-greedy, a bad arm is drawn less " +
            "the worse it looks — but τ has to be tuned to the scale of the rewards.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("τ = 0.05", 0.28f, FigureTone.Warn),
                FigureBar("τ = 0.15", 0.63f, FigureTone.Accent),
                FigureBar("τ = 0.5", 0.37f, FigureTone.Warn),
            ),
            yLabel = "share of pulls on the best arm",
        ),
    ),
    whatIsIt = listOf(
        "Boltzmann (softmax) exploration chooses actions in proportion to how good they look: P(a) = exp(Q(a)/τ) / Σ exp(Q(a′)/τ). The temperature τ sets how sharply the probabilities follow the estimates — near zero it is greedy, very large it is uniform — and unlike ε-greedy, a bad action is tried less the worse it looks rather than as often as everything else.",
        "The lab plays the same four slot machines as the ε-greedy page (30%, 50%, 45% and 72%) for 200 pulls. At τ = 0.05 the softmax is almost greedy: once C pays out early its estimate dominates, it is drawn with probability near 100%, and the best arm D is never tried. At τ = 0.15 the estimates still matter but no arm is locked out, D gets enough pulls to show its real rate, and by pull 50 it leads the estimates. Averaged over 50 runs, τ = 0.05 puts 28% of pulls on D, τ = 0.15 63%, and τ = 0.5 — nearly uniform — only 37%; τ = 0.15 earns the most, 0.620 per pull.",
        "The temperature is the whole method, and it is scale-dependent: the same τ means something different when rewards are in the hundreds than when they are in [0, 1], so it has to be tuned per problem, and the softmax over noisy estimates can still lock onto an early lucky action if τ is small. Its continuous relative is the maximum-entropy policy π ∝ exp(Q/α) behind SAC, where the temperature is learned rather than hand-set.",
    ),
    steps = listOf(
        StepCard(1, "Score by Value", "Take each action's estimated value Q(s,a).", 0xFF818CF8),
        StepCard(2, "Apply Softmax", "Convert values to probabilities via exp(Q/τ), normalized.", 0xFF60A5FA),
        StepCard(3, "Sample an Action", "Draw an action from that probability distribution.", 0xFF10B981),
        StepCard(4, "Anneal Temperature", "Lower τ over time to shift from exploration toward exploitation.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Softmax policy", "P(a) = exp(Q(a)/τ) / Σ exp(Q(a′)/τ)", "Value-proportional action probabilities."),
        FormulaEntry("τ → ∞", "uniform random", "Maximum exploration."),
        FormulaEntry("τ → 0", "greedy", "Pure exploitation."),
    ),
    notationKey = listOf(
        NotationEntry("τ", "temperature — exploration sharpness"),
        NotationEntry("softmax", "value-to-probability transform"),
        NotationEntry("Q(s,a)", "estimated action value"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Boltzmann action selection",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                def boltzmann(q_values, tau):
                    prefs = np.array(q_values) / tau
                    probs = np.exp(prefs - prefs.max())      # stable softmax
                    probs /= probs.sum()
                    return np.random.choice(len(q_values), p=probs)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BanditExplorer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Value-Based Exploration", "A smoother alternative to ε-greedy that respects value differences."),
        ApplicationCard("bulb", 0xFF60A5FA, "Max-Entropy Link", "The softmax policy is the optimal form under maximum-entropy RL."),
        ApplicationCard("target", 0xFF10B981, "Graded Preferences", "Explores promising actions more than clearly poor ones."),
    ),
    takeaways = listOf(
        "Boltzmann exploration samples actions in proportion to exp(value/τ).",
        "Temperature τ dials between uniform exploration and greedy exploitation.",
        "Unlike ε-greedy, it explores in proportion to value, not uniformly at random.",
        "It's the exploration form that maximum-entropy RL makes optimal.",
        "In the lab τ = 0.05 puts 28% of pulls on the best arm, τ = 0.15 63%, τ = 0.5 37% — too greedy and too uniform both lose.",
    ),
    crossLinks = listOf(
        CrossLink("epsilon_greedy", "Epsilon-Greedy"),
        CrossLink("max_entropy_rl", "Maximum Entropy RL"),
    ),
)
