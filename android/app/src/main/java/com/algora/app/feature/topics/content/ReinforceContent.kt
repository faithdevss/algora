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

internal val reinforceContent = TopicContent(
    topicId = "reinforce",
    figure = Figure(
        caption = "REINFORCE's problem, measured in the page's lab: 300 independent estimates of the " +
            "same gradient component, each from one rollout of an unchanged policy, drawn here as a " +
            "normal curve with the sample's mean and spread. They are all estimating one number — " +
            "the mean, 0.214 — but their standard deviation is 0.303, so the spread is 1.4 times " +
            "the signal, and a large share of single estimates (everything left of the dashed zero " +
            "line) points the wrong way. The cause is credit assignment: every action in an episode " +
            "is multiplied by the whole return, including rewards it had nothing to do with. The " +
            "algorithm still works — the lab's return climbs from 0.35 to 0.60 over 60 updates — " +
            "but slowly, and every method after it, from baselines to actor-critic, A2C, GAE and " +
            "PPO, is an attack on that 1.4.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "zero",
                    listOf(FigurePoint(0.5f, 0f), FigurePoint(0.5f, 1f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "gradient estimates",
                    listOf(
                        FigurePoint(0.000f, 0.000f), FigurePoint(0.042f, 0.000f), FigurePoint(0.083f, 0.000f),
                        FigurePoint(0.125f, 0.001f), FigurePoint(0.167f, 0.004f), FigurePoint(0.208f, 0.011f),
                        FigurePoint(0.250f, 0.027f), FigurePoint(0.292f, 0.062f), FigurePoint(0.333f, 0.128f),
                        FigurePoint(0.375f, 0.237f), FigurePoint(0.417f, 0.393f), FigurePoint(0.458f, 0.585f),
                        FigurePoint(0.500f, 0.779f), FigurePoint(0.542f, 0.932f), FigurePoint(0.583f, 0.999f),
                        FigurePoint(0.625f, 0.961f), FigurePoint(0.667f, 0.828f), FigurePoint(0.708f, 0.641f),
                        FigurePoint(0.750f, 0.444f), FigurePoint(0.792f, 0.276f), FigurePoint(0.833f, 0.154f),
                        FigurePoint(0.875f, 0.077f), FigurePoint(0.917f, 0.035f), FigurePoint(0.958f, 0.014f),
                        FigurePoint(1.000f, 0.005f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(0.589f, 1f, "mean 0.214"),
                FigurePoint(0.715f, 0.607f, "± 0.303", FigureTone.Warn),
            ),
            xLabel = "gradient estimate, −1.2 to 1.2",
            yLabel = "how often (relative)",
        ),
    ),
    whatIsIt = listOf(
        "REINFORCE is the original policy-gradient algorithm, and it skips the step every value method takes. Instead of learning what states are worth and deriving a policy from that, it adjusts the policy's parameters directly: play an episode, then push up the log-probability of every action taken in proportion to the return that followed. ∇J ≈ Σₜ ∇log π(aₜ|sₜ)·Gₜ — no value function anywhere in the algorithm.",
        "The lab shows that it works: over 60 updates on its chain the average return climbs from 0.35 to 0.60. It also shows the catch, which is in the estimator rather than the idea. Run 300 independent rollouts of the same, unchanged policy and compute the gradient each time — all 300 should be estimating one number. They have a mean of 0.214 and a standard deviation of 0.303: the spread is 1.4 times the signal. Every action in an episode is credited with the whole return, including the rewards that came before it or had nothing to do with it.",
        "Every later method in the family is an attack on that 1.4. Subtracting a baseline b(s) leaves the gradient unbiased and cuts the variance; replacing the return with a learned critic's advantage gives actor-critic; averaging many actors gives A2C; GAE dials between the return and the critic; PPO and TRPO limit how far one noisy estimate may move the policy. REINFORCE is on-policy and needs complete episodes, so it is rarely used directly — but it is the estimator all of them start from.",
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
        "In the lab, 300 gradient estimates of the same policy have mean 0.214 and standard deviation 0.303 — noise 1.4× the signal.",
    ),
    crossLinks = listOf(
        CrossLink("actor_critic", "Actor-Critic"),
        CrossLink("ppo", "PPO"),
    ),
)
