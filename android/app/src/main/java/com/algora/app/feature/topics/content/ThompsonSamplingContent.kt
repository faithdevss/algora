package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val thompsonSamplingContent = TopicContent(
    topicId = "thompson_sampling",
    figure = Figure(
        caption = "Thompson sampling's belief about the best arm at the end of the page's lab, " +
            "drawn on the upper half of the win-rate axis. Before any pull every arm's posterior is " +
            "Beta(1, 1), flat across 0 to 1 — the dashed line — so any draw is as likely as any " +
            "other, and an untried arm can win the draw outright. After 200 pulls arm D has been " +
            "played 163 times, 125 wins and 38 losses, and its posterior is Beta(126, 39): a narrow " +
            "peak around its mean of 0.76, against a true rate of 0.72. Each round the agent draws " +
            "one sample from every arm's posterior and plays the highest; a posterior this narrow " +
            "and this high wins almost every draw, while the weaker arms' wider, lower posteriors " +
            "only occasionally produce a sample above it. How often an arm is explored is set by " +
            "nothing but how unsure the agent still is about it.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "prior Beta(1, 1)",
                    listOf(FigurePoint(0f, 0.08f), FigurePoint(1f, 0.08f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "arm D after 200 pulls",
                    listOf(
                        FigurePoint(0.100f, 0.000f), FigurePoint(0.120f, 0.000f), FigurePoint(0.140f, 0.000f),
                        FigurePoint(0.160f, 0.000f), FigurePoint(0.180f, 0.000f), FigurePoint(0.200f, 0.000f),
                        FigurePoint(0.220f, 0.000f), FigurePoint(0.240f, 0.000f), FigurePoint(0.260f, 0.001f),
                        FigurePoint(0.280f, 0.002f), FigurePoint(0.300f, 0.005f), FigurePoint(0.320f, 0.012f),
                        FigurePoint(0.340f, 0.025f), FigurePoint(0.360f, 0.050f), FigurePoint(0.380f, 0.094f),
                        FigurePoint(0.400f, 0.163f), FigurePoint(0.420f, 0.264f), FigurePoint(0.440f, 0.400f),
                        FigurePoint(0.460f, 0.563f), FigurePoint(0.480f, 0.734f), FigurePoint(0.500f, 0.886f),
                        FigurePoint(0.520f, 0.983f), FigurePoint(0.540f, 1.000f), FigurePoint(0.560f, 0.927f),
                        FigurePoint(0.580f, 0.778f), FigurePoint(0.600f, 0.587f), FigurePoint(0.620f, 0.395f),
                        FigurePoint(0.640f, 0.234f), FigurePoint(0.660f, 0.122f), FigurePoint(0.680f, 0.054f),
                        FigurePoint(0.700f, 0.021f), FigurePoint(0.720f, 0.006f), FigurePoint(0.740f, 0.002f),
                        FigurePoint(0.760f, 0.000f), FigurePoint(0.780f, 0.000f), FigurePoint(0.800f, 0.000f),
                        FigurePoint(0.820f, 0.000f), FigurePoint(0.840f, 0.000f), FigurePoint(0.860f, 0.000f),
                        FigurePoint(0.880f, 0.000f), FigurePoint(0.900f, 0.000f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.527f, 1f, "Beta(126, 39)"),
                FigurePoint(0.44f, 0.08f, "true 0.72", FigureTone.Muted),
            ),
            xLabel = "win rate, 0.5 → 1.0",
            yLabel = "belief density (relative)",
        ),
    ),
    whatIsIt = listOf(
        "Thompson sampling explores by probability matching. It keeps a posterior belief about each action's value — for win/lose rewards, a Beta(1 + wins, 1 + losses) distribution per arm — draws one sample from every posterior, and plays the arm whose sample is highest. An arm is therefore chosen exactly as often as it is believed to be the best: there is no ε to tune and no bonus formula, only the width of each posterior.",
        "The lab plays four arms paying 45%, 60%, 40% and 72% for 200 pulls. On pull 1 every posterior is flat and D's random draw (0.56) happens to win. Arms with few pulls have wide posteriors, so they sometimes draw high — A, untried, draws 0.97 on pull 2; C draws 1.00 on pull 7 — and that is the exploration. As evidence accumulates, the best arm's posterior narrows around a high value and wins almost every draw, while the others' draws keep coming in lower.",
        "By pull 200, D has been played 163 times — 125 wins and 38 losses — far more decisively than UCB's 87 on similar arms, because Thompson stops spending on arms whose posteriors have moved clearly below the leader's. It extends naturally to any reward model with a tractable posterior, is used heavily in recommendation and ad selection, and its regret is near-optimal in practice. The cost is the posterior: when it cannot be computed exactly, it has to be approximated.",
    ),
    steps = listOf(
        StepCard(1, "Maintain Posteriors", "Hold a probability distribution over each action's reward (e.g. Beta for Bernoulli rewards).", 0xFF818CF8),
        StepCard(2, "Sample", "Draw one value from each action's posterior.", 0xFF60A5FA),
        StepCard(3, "Play the Best Sample", "Choose the action whose sampled value is largest.", 0xFF10B981),
        StepCard(4, "Update the Belief", "After observing the reward, update that action's posterior.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Bayes update", "posterior ∝ likelihood · prior", "Refine belief with each reward."),
        FormulaEntry("Beta-Bernoulli", "Beta(α+successes, β+failures)", "Conjugate update for 0/1 rewards."),
        FormulaEntry("Selection", "argmaxₐ sample(posteriorₐ)", "Probability-matched exploration."),
    ),
    notationKey = listOf(
        NotationEntry("posterior", "current belief over an action's value"),
        NotationEntry("prior", "initial belief before data"),
        NotationEntry("conjugate", "prior/likelihood pair with a closed-form update"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Thompson sampling (Beta-Bernoulli bandit)",
            accentColor = 0xFF6366F1,
            code = """
                import random

                def select(alpha, beta):
                    samples = [random.betavariate(alpha[a], beta[a]) for a in range(len(alpha))]
                    return max(range(len(samples)), key=lambda a: samples[a])

                # After reward r in {0,1}: alpha[a] += r ; beta[a] += (1 - r)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BanditExplorer,
    applications = listOf(
        ApplicationCard("globe", 0xFF818CF8, "Online A/B Testing", "Allocates traffic to variants in proportion to their probability of being best."),
        ApplicationCard("target", 0xFF60A5FA, "Ad & Content Selection", "Widely used in production recommendation and ad-serving bandits."),
        ApplicationCard("flask", 0xFF10B981, "Clinical Trials", "Adaptive trial designs steer patients toward promising treatments."),
    ),
    takeaways = listOf(
        "Thompson sampling picks actions by sampling from posterior beliefs over their value.",
        "It explores in proportion to the probability each action is optimal.",
        "It's Bayesian, randomized, and often beats UCB empirically.",
        "Conjugate priors (Beta-Bernoulli, Gaussian) make its updates cheap.",
        "In the lab Thompson sampling has played the best arm 163 of 200 times — 125 wins, 38 losses.",
    ),
    crossLinks = listOf(
        CrossLink("ucb", "UCB"),
        CrossLink("naive_bayes", "Naive Bayes"),
    ),
)
