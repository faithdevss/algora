package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ucbContent = TopicContent(
    topicId = "ucb",
    whatIsIt = listOf(
        "Upper Confidence Bound (UCB) explores by optimism under uncertainty: it picks the action with the highest upper confidence bound, favoring options that are either promising or under-explored.",
        "Rarely-tried actions get a large uncertainty bonus, so UCB tries everything enough to be confident — achieving logarithmic regret in the bandit setting.",
    ),
    steps = listOf(
        StepCard(1, "Estimate the Mean", "Track each action's average observed reward.", 0xFF818CF8),
        StepCard(2, "Add an Uncertainty Bonus", "Add a term that grows when an action has been tried fewer times.", 0xFF60A5FA),
        StepCard(3, "Pick the Highest Bound", "Choose the action maximizing mean + bonus — optimism in the face of uncertainty.", 0xFF10B981),
        StepCard(4, "Shrink with Experience", "As an action's count rises, its bonus shrinks and its estimate sharpens.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("UCB", "Q(a) + c·√(ln t / n(a))", "Mean plus exploration bonus. UCB1 (Auer et al. 2002) is c = √2."),
        FormulaEntry("Bonus", "√(ln t / n(a))", "Large for rarely-tried actions."),
        FormulaEntry("Regret", "O(log t)", "Near-optimal for stochastic bandits."),
    ),
    notationKey = listOf(
        NotationEntry("Q(a)", "estimated mean reward of action a"),
        NotationEntry("n(a)", "times action a has been chosen"),
        NotationEntry("t", "total steps so far"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "UCB action selection (UCB1 is c = √2)",
            accentColor = 0xFF6366F1,
            code = """
                import math

                def ucb_select(Q, counts, t, c=2 ** 0.5):
                    return max(range(len(Q)), key=lambda a:
                        Q[a] + c * math.sqrt(math.log(t + 1) / (counts[a] + 1e-9)))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BanditExplorer,
    applications = listOf(
        ApplicationCard("target", 0xFF818CF8, "Multi-Armed Bandits", "The canonical optimism-based bandit strategy with strong guarantees."),
        ApplicationCard("game", 0xFF60A5FA, "MCTS (UCT)", "The UCB rule chooses which tree branches to explore in MCTS."),
        ApplicationCard("globe", 0xFF10B981, "Recommendation & Ads", "Balancing showing proven vs untested content online."),
    ),
    takeaways = listOf(
        "UCB explores by optimism: pick the highest plausible value, not just the highest mean.",
        "The uncertainty bonus shrinks as an action is tried more.",
        "It achieves logarithmic regret in stochastic bandits.",
        "It's the exploration rule inside MCTS's UCT selection.",
    ),
    crossLinks = listOf(
        CrossLink("thompson_sampling", "Thompson Sampling"),
        CrossLink("mcts", "MCTS"),
    ),
)
