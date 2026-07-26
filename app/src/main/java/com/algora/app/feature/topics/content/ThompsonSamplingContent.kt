package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val thompsonSamplingContent = TopicContent(
    topicId = "thompson_sampling",
    whatIsIt = listOf(
        "Thompson sampling explores probabilistically: it keeps a posterior belief over each action's value, samples a value from each, and plays whichever sample is highest.",
        "This Bayesian, randomized approach naturally tries actions in proportion to the probability they're best, and often outperforms UCB in practice.",
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
    ),
    crossLinks = listOf(
        CrossLink("ucb", "UCB"),
        CrossLink("naive_bayes", "Naive Bayes"),
    ),
)
