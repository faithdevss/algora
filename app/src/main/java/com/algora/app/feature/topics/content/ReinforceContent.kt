package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val reinforceContent = TopicContent(
    topicId = "reinforce",
    whatIsIt = listOf(
        "REINFORCE is the foundational policy-gradient method: it directly adjusts a parameterized policy to make high-return actions more likely, without ever learning a value function.",
        "It uses complete-episode returns and the log-likelihood trick to estimate the gradient of expected reward — simple, unbiased, but noisy.",
    ),
    steps = listOf(
        StepCard(1, "Run an Episode", "Sample a full trajectory by acting with the current stochastic policy.", 0xFF818CF8),
        StepCard(2, "Compute Returns", "For each step, compute the discounted return Gₜ from that point onward.", 0xFF60A5FA),
        StepCard(3, "Weight Log-Probs by Return", "Scale each action's log-probability gradient by its return.", 0xFF10B981),
        StepCard(4, "Ascend the Gradient", "Step the policy parameters to increase the probability of high-return actions.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Policy gradient", "∇J = E[Σ ∇logπ(aₜ|sₜ)·Gₜ]", "Log-likelihood trick weighted by return."),
        FormulaEntry("Update", "θ += α·∇logπ(a|s)·G", "Push up high-return actions."),
        FormulaEntry("Weakness", "high variance", "Monte-Carlo returns are noisy; a baseline helps."),
    ),
    notationKey = listOf(
        NotationEntry("π(a|s;θ)", "the parameterized stochastic policy"),
        NotationEntry("Gₜ", "return from timestep t"),
        NotationEntry("baseline", "a subtracted value to cut variance"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "REINFORCE loss (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                # returns: discounted G_t per step; log_probs: log pi(a_t | s_t).
                returns = (returns - returns.mean()) / (returns.std() + 1e-8)
                loss = -(torch.stack(log_probs) * returns).sum()   # gradient ascent
                loss.backward(); optimizer.step()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Continuous & Discrete Control", "Directly optimizes any differentiable policy, discrete or continuous."),
        ApplicationCard("book", 0xFF60A5FA, "Policy-Gradient Foundation", "Every actor-critic and PPO method builds on its core theorem."),
        ApplicationCard("bulb", 0xFF10B981, "Non-Differentiable Rewards", "Optimizes expected reward even when the reward itself isn't differentiable."),
    ),
    takeaways = listOf(
        "REINFORCE optimizes a policy directly via the policy-gradient theorem.",
        "It weights action log-probabilities by episode returns.",
        "It's unbiased but high-variance; subtracting a baseline reduces the noise.",
        "Actor-critic methods replace its Monte-Carlo return with a learned value estimate.",
    ),
    crossLinks = listOf(
        CrossLink("actor_critic", "Actor-Critic"),
        CrossLink("ppo", "PPO"),
    ),
)
