package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val tdLearningContent = TopicContent(
    topicId = "td_learning",
    whatIsIt = listOf(
        "Temporal difference learning is the idea that made model-free RL practical: update a value estimate using another value estimate. After a single transition, TD(0) moves V(s) toward r + γV(s′) — one real reward plus a guess about everything after it — instead of waiting to observe the whole return.",
        "It takes the useful half of each neighbour. Like Monte Carlo, it learns from raw experience with no model. Like dynamic programming, it bootstraps, so it can update after every step rather than every episode. That means it works on continuing tasks that never terminate, learns online, and cuts variance dramatically — the target contains one random reward instead of a hundred.",
        "The price is bias. Early on, V(s′) is simply wrong, so TD is chasing a moving and incorrect target, and unlike MC it depends on the state actually being Markov — if s′ does not summarize the past, bootstrapping through it propagates the error. The bias vanishes as estimates improve, and in practice TD converges faster than MC on Markov problems despite it. Everything downstream is built on this update: SARSA is TD on Q with the on-policy action, Q-learning is TD on Q with a max, and every deep value method is TD with a neural network in place of the table.",
    ),
    steps = listOf(
        StepCard(1, "Take One Step", "From s take an action, observe reward r and next state s′. That is all the data needed.", 0xFF6366F1),
        StepCard(2, "Form the TD Target", "r + γV(s′): one real reward plus the current guess for the rest.", 0xFF818CF8),
        StepCard(3, "Compute the TD Error", "δ = target − V(s). The size of your surprise, and the entire learning signal.", 0xFF60A5FA),
        StepCard(4, "Move a Fraction of the Way", "V(s) ← V(s) + α·δ. Nudge, do not jump — the target is itself noisy.", 0xFF10B981),
        StepCard(5, "Continue Immediately", "No need to wait for the episode to end, which is what makes continuing tasks possible.", 0xFFF59E0B),
        StepCard(6, "Watch the Bias Decay", "Early targets are wrong because V(s′) is wrong. Both improve together.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("TD(0) update", "V(s) ← V(s) + α[r + γV(s′) − V(s)]", "The core of model-free RL."),
        FormulaEntry("TD error", "δₜ = rₜ₊₁ + γV(sₜ₊₁) − V(sₜ)", "Surprise: what happened minus what was expected."),
        FormulaEntry("MC target", "Gₜ — unbiased, high variance", "The whole observed return."),
        FormulaEntry("TD target", "rₜ₊₁ + γV(sₜ₊₁) — biased, low variance", "One reward plus a bootstrap."),
        FormulaEntry("n-step", "Gₜ⁽ⁿ⁾ = Σₖ₌₀ⁿ⁻¹ γᵏrₜ₊ₖ₊₁ + γⁿV(sₜ₊ₙ)", "n = 1 is TD, n = ∞ is MC."),
        FormulaEntry("TD(λ)", "Gₜ^λ = (1−λ)Σₙ λⁿ⁻¹Gₜ⁽ⁿ⁾", "A geometric average over all n at once."),
    ),
    notationKey = listOf(
        NotationEntry("δ", "TD error — the update signal"),
        NotationEntry("α", "step size; how much of the error to absorb"),
        NotationEntry("bootstrapping", "updating an estimate from another estimate"),
        NotationEntry("λ", "trace decay, sweeping between TD(0) and MC"),
        NotationEntry("eligibility trace", "a decaying record of recently visited states, to spread credit"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "TD(0) prediction",
            accentColor = 0xFF6366F1,
            code = """
                def td_prediction(policy, env, episodes=1000, alpha=0.1, gamma=0.9):
                    V = defaultdict(float)
                    for _ in range(episodes):
                        s, done = env.reset(), False
                        while not done:
                            a = policy(s)
                            s2, r, done = env.step(a)
                            # The update happens HERE, mid-episode. Monte Carlo cannot do this:
                            # it has nothing to learn from until the episode terminates.
                            target = r + (0.0 if done else gamma * V[s2])
                            V[s] += alpha * (target - V[s])
                            s = s2
                    return V
            """.trimIndent(),
        ),
        CodeBlock(
            title = "n-step returns: the dial between TD and MC",
            accentColor = 0xFF10B981,
            code = """
                # n controls how much real reward is collected before bootstrapping.
                #   n = 1        -> TD(0): lowest variance, most bias
                #   n = infinity -> Monte Carlo: no bias, most variance
                #   n = 3..10    -> usually beats both endpoints in practice
                def n_step_return(rewards, bootstrap_value, gamma, n):
                    g = sum((gamma ** k) * rewards[k] for k in range(min(n, len(rewards))))
                    if len(rewards) >= n:
                        g += (gamma ** n) * bootstrap_value
                    return g

                # TD(lambda) averages over every n simultaneously with geometric weights,
                # implemented efficiently with eligibility traces rather than n separate sums.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("game", 0xFF6366F1, "TD-Gammon", "Tesauro's 1992 backgammon program learned from self-play with TD(λ) and reached world-class play."),
        ApplicationCard("network", 0xFF818CF8, "Every Deep Value Method", "DQN, SAC and TD3 all minimize a TD error — the table is simply replaced by a network."),
        ApplicationCard("flask", 0xFF10B981, "Neuroscience", "Dopamine neuron firing tracks reward-prediction error remarkably closely — the TD error, in biological form."),
    ),
    takeaways = listOf(
        "TD updates a value estimate from another value estimate, so it learns after every step.",
        "It combines MC's model-free sampling with DP's bootstrapping.",
        "Lower variance than MC, at the cost of bias and a genuine dependence on the Markov property.",
        "n-step returns and TD(λ) interpolate continuously between TD(0) and Monte Carlo.",
    ),
    crossLinks = listOf(
        CrossLink("monte_carlo_rl", "Monte Carlo Methods"),
        CrossLink("sarsa", "SARSA"),
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("gae", "GAE"),
    ),
)
