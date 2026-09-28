package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val boltzmannExplorationContent = TopicContent(
    topicId = "boltzmann_exploration",
    whatIsIt = listOf(
        "Boltzmann (softmax) exploration chooses actions with probability proportional to the exponential of their estimated value, so better actions are more likely but every action retains a chance.",
        "A temperature parameter controls the sharpness: high temperature explores near-uniformly, low temperature acts almost greedily.",
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
    ),
    crossLinks = listOf(
        CrossLink("epsilon_greedy", "Epsilon-Greedy"),
        CrossLink("max_entropy_rl", "Maximum Entropy RL"),
    ),
)
