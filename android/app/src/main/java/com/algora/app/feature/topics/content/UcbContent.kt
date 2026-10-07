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

internal val ucbContent = TopicContent(
    topicId = "ucb",
    figure = Figure(
        caption = "Where UCB's 200 pulls went in the page's lab, on four arms paying 45%, 60%, 40% and " +
            "72%. Each arm is scored by its estimate plus a bonus, √(2 ln t / n), that grows with " +
            "time and shrinks with every pull of that arm. Early on that bonus dominates: after D " +
            "leads on pull 5, each of the other three — tried only once — gets lifted above it and " +
            "pulled again. As counts grow the bonuses shrink and the estimate takes over, and the " +
            "best arm ends with 87 pulls, its estimate 0.724 against a true 0.72. The others still " +
            "get 49, 33 and 29: because ln t keeps growing, UCB never stops checking an arm " +
            "entirely. That steady insurance is what buys its logarithmic regret guarantee — and " +
            "why Thompson sampling, which drops weak arms faster, often earns more in practice.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("A · 45%", 0.379f, FigureTone.Muted),
                FigureBar("B · 60%", 0.563f, FigureTone.Primary),
                FigureBar("C · 40%", 0.333f, FigureTone.Muted),
                FigureBar("D · 72%", 1f, FigureTone.Accent),
            ),
            yLabel = "pulls out of 200, 0 to 87",
        ),
    ),
    whatIsIt = listOf(
        "Upper Confidence Bound selection explores by optimism. Each action is scored by its estimated value plus a bonus that is large when the action has been tried rarely and shrinks as it is tried more: Q(a) + √(2 ln t / n(a)) in UCB1. The agent picks the highest score, so an action is explored because it might be good, not because a coin said so — and its logarithmic regret is close to the best any algorithm can achieve.",
        "The lab steps through 200 pulls of four arms that pay 45%, 60%, 40% and 72%. Every arm is tried once first, because an untried arm's bonus is infinite. On pull 5, D leads with an estimate of 1.00 plus a bonus of 1.79 — but on the next pulls A, B and C, each tried only once, have bonuses large enough to lift them above D, so each gets another look. As pulls pile up every bonus shrinks, the estimates settle, and the best arm wins more often.",
        "By pull 200 the counts are D 87, B 49, A 33, C 29, with the estimates close to the truth (D's at 0.724 against 0.72). UCB keeps spending on the weaker arms — the bonus never quite reaches zero, because ln t keeps growing — which is the price of its guarantee. It assumes rewards are stationary and needs a count per action, which is why deep RL replaces counts with learned novelty signals; Monte Carlo tree search uses the same formula, as UCT, to decide which branch to expand.",
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
        "In the lab after 200 pulls UCB has pulled the best arm 87 times and the others 49, 33 and 29 — still checking them, as its bonus requires.",
    ),
    crossLinks = listOf(
        CrossLink("thompson_sampling", "Thompson Sampling"),
        CrossLink("mcts", "MCTS"),
    ),
)
