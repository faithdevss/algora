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

internal val multiArmedBanditContent = TopicContent(
    topicId = "multi_armed_bandit",
    figure = Figure(
        caption = "The four arms the lab pulls, and the only thing a strategy is trying to find: " +
            "arm 4 pays 0.72 and every pull spent elsewhere is regret. Pulling at random for all " +
            "200 pulls lands on it 25.5% of the time — indistinguishable from the 25% of using no " +
            "information whatsoever, which is the floor every method in this category is measured " +
            "against. Its opposite is pure greedy: sample each arm once, commit forever to " +
            "whichever looked best, and never collect the evidence that would overturn one unlucky " +
            "sample. Epsilon-greedy, UCB and Thompson sampling are all ways of standing between " +
            "those two failures — spending just enough of the budget on the shaded arms to make " +
            "the rest of it pay.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("arm 1", 0.30f, FigureTone.Muted),
                FigureBar("arm 2", 0.55f, FigureTone.Muted),
                FigureBar("arm 3", 0.45f, FigureTone.Muted),
                FigureBar("arm 4", 0.72f, FigureTone.Accent),
            ),
            yLabel = "true win rate (unknown to the agent)",
            xLabel = "200 pulls to find the one on the right",
        ),
    ),
    whatIsIt = listOf(
        "The multi-armed bandit is the simplest possible version of the explore-exploit problem: several arms (slot machines, ad variants, treatment options), each with a fixed but unknown probability of paying out, and a fixed budget of pulls. Every pull teaches you a little about one arm and costs you the chance to have pulled a different one instead — there is no way to learn without spending some of the budget on options that might be worse. Every strategy this app covers separately — epsilon-greedy, UCB, Thompson sampling, pure greedy, Boltzmann exploration — is an answer to exactly this one problem, each with a different rule for which arm to pull next given what has been observed so far.",
        "This topic runs the problem's own worst-case baseline: pick an arm completely at random, every single pull, using no estimate of anything. Over 200 pulls across four arms (true win rates 0.30, 0.55, 0.45 and 0.72), pure random selection lands on the best arm 25.5% of the time — indistinguishable, within sampling noise, from the 25% a coin-flip-per-arm would give with zero information used at all. That is the floor every real strategy in this category is measured against.",
        "It is also the other half of a pair with `exploration_exploitation`'s pure-greedy baseline, which sits at the opposite extreme: sample every arm once, then commit forever to whichever looked best, and never collect the evidence that would correct a bad first sample. Pure random never commits to anything it has learned; pure greedy commits too early and never learns anything more. Every strategy that actually works — epsilon-greedy's occasional random pull, UCB's shrinking confidence bonus, Thompson sampling's belief distribution — is a way of standing somewhere between those two failures, spending just enough of the budget on exploration to make the rest of it pay off.",
    ),
    steps = listOf(
        StepCard(1, "Several Arms, Unknown Win Rates", "Fixed probabilities, never observed directly — only through pulling.", 0xFFEF4444),
        StepCard(2, "A Pull Reveals One Sample", "Reward from the pulled arm only — every other arm stays exactly as uncertain as before.", 0xFF3B82F6),
        StepCard(3, "Pure Random: the Zero-Information Baseline", "Every pull ignores every previous one — the floor every real strategy beats.", 0xFF10B981),
        StepCard(4, "Pure Greedy: the Opposite Failure", "One sample per arm, then commit forever — covered in Exploration vs. Exploitation.", 0xFFF59E0B),
        StepCard(5, "Real Strategies Sit Between Them", "Epsilon-greedy, UCB and Thompson sampling all trade explore for exploit deliberately.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Reward model", "reward ~ Bernoulli(pₐ)", "Each arm a has a fixed, unknown success probability."),
        FormulaEntry("Regret", "R(T) = T·p* − Σₜ p_{aₜ}", "What is lost, in expectation, versus always pulling the best arm."),
        FormulaEntry("Pure random", "P(pull arm a) = 1/n, always", "No estimate used, ever."),
        FormulaEntry("Measured", "25.5% optimal-arm pulls", "Over 200 pulls, 4 arms — matches the 25% chance floor."),
    ),
    notationKey = listOf(
        NotationEntry("arm", "one option with a fixed, unknown reward probability"),
        NotationEntry("pull / trial", "one choice of arm and the reward it returns"),
        NotationEntry("regret", "the gap between what was earned and what the best arm would have earned"),
        NotationEntry("explore / exploit", "gather more information, versus use what is already known"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The baseline every real strategy has to beat",
            accentColor = 0xFFEF4444,
            code = """
                import random

                true_rates = [0.30, 0.55, 0.45, 0.72]   # unknown to the agent
                pulls = 200
                optimal_pulls = 0

                for t in range(pulls):
                    arm = random.randrange(len(true_rates))     # no estimate used at all
                    reward = 1 if random.random() < true_rates[arm] else 0
                    if arm == 3:  # the true best arm
                        optimal_pulls += 1

                print(optimal_pulls / pulls)   # 0.255 -- matches the 25% chance floor

                # Compare: epsilon-greedy, UCB and Thompson sampling (this app's other Exploration
                # Strategies topics) all push this number well above 25% by actually using what each
                # pull teaches them -- that gap IS what an exploration strategy buys you.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BanditExplorer,
    applications = listOf(
        ApplicationCard("finance", 0xFF3B82F6, "A/B Testing at Scale", "Bandit algorithms outperform fixed-split A/B tests by shifting traffic toward the winner early."),
        ApplicationCard("search", 0xFF10B981, "Ad & Content Ranking", "Which ad, headline or recommendation to show next, with the payoff only known after the fact."),
        ApplicationCard("flask", 0xFFF59E0B, "Clinical Trials", "Adaptive trial designs are bandit problems with an ethical cost attached to exploration."),
        ApplicationCard("help", 0xFFEC4899, "Why It Matters Beyond RL", "Every full RL problem contains a bandit problem inside it — deciding what to try next, at every state."),
    ),
    takeaways = listOf(
        "The multi-armed bandit is the purest form of explore-exploit: fixed unknown reward rates, a pull budget, and no way to learn for free.",
        "Pure random selection — no estimate used, ever — lands on the best of four arms 25.5% of the time, matching the 25% chance floor almost exactly.",
        "That floor is what every real strategy (epsilon-greedy, UCB, Thompson sampling) is measured against.",
        "Pure random and pure greedy are the two failure extremes: one never stops exploring, the other stops after one sample and never starts again.",
        "Real strategies work by spending the exploration budget deliberately, rather than either all at once or never at all.",
    ),
    crossLinks = listOf(
        CrossLink("exploration_exploitation", "Exploration vs. Exploitation"),
        CrossLink("thompson_sampling", "Thompson Sampling"),
        CrossLink("ucb", "Upper Confidence Bound (UCB)"),
        CrossLink("q_learning", "Q-Learning (Off-Policy)"),
    ),
)
