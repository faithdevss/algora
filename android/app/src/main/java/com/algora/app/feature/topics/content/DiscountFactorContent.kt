package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val discountFactorContent = TopicContent(
    topicId = "discount_factor",
    whatIsIt = listOf(
        "The discount factor γ ∈ [0,1) sets how much a reward arriving k steps from now is worth today: γᵏ times its face value. It is the single number that decides whether an agent is a scavenger or a planner.",
        "Two jobs are bundled into one hyperparameter, and it helps to keep them apart. Mathematically, γ < 1 guarantees the infinite sum of rewards converges and makes the Bellman operator a contraction — without it, values in a continuing task can diverge and nothing is well defined. Behaviourally, γ defines an effective horizon of roughly 1/(1−γ) steps: γ = 0.9 means the agent effectively reasons about the next ten steps, γ = 0.99 about a hundred, and γ = 0 makes it purely greedy for immediate reward.",
        "Raising γ is not free, and this is where practitioners get burned. A higher γ means the target r + γV(s′) leans more heavily on the agent's own estimate, so bootstrap error compounds and variance in returns grows — training becomes slower and less stable exactly when you most want long-horizon reasoning. The standard practical move is to treat γ as part of the problem definition rather than a knob to maximize: pick the smallest γ whose horizon actually covers the task, and if credit still fails to propagate, reach for GAE, n-step returns or reward shaping rather than pushing γ toward 1.",
    ),
    steps = listOf(
        StepCard(1, "Write the Return", "Gₜ = rₜ₊₁ + γrₜ₊₂ + γ²rₜ₊₃ + … — each step further out is worth γ times less.", 0xFF818CF8),
        StepCard(2, "Guarantee Convergence", "With bounded rewards and γ < 1, the sum is bounded by rₘₐₓ/(1−γ). The problem is well posed.", 0xFF60A5FA),
        StepCard(3, "Read the Horizon", "≈ 1/(1−γ) steps. Pick γ from the horizon the task needs, not the other way round.", 0xFF8B5CF6),
        StepCard(4, "See the Effect on Values", "Low γ: value hugs the reward. High γ: value spreads far out across the state space.", 0xFF10B981),
        StepCard(5, "Watch the Cost", "Higher γ means longer credit chains, more bootstrap error, higher variance and slower convergence.", 0xFFF59E0B),
        StepCard(6, "Reach for Better Tools", "If credit still will not propagate, use n-step returns or GAE rather than pushing γ to 0.999.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Discounted return", "Gₜ = Σₖ₌₀^∞ γᵏ rₜ₊ₖ₊₁", "Geometrically weighted future reward."),
        FormulaEntry("Effective horizon", "H ≈ 1/(1 − γ)", "γ=0.9 → ~10 steps; γ=0.99 → ~100."),
        FormulaEntry("Value bound", "|V(s)| ≤ rₘₐₓ/(1 − γ)", "Why γ<1 keeps continuing tasks finite."),
        FormulaEntry("Contraction modulus", "‖TV − TV′‖∞ ≤ γ‖V − V′‖∞", "γ is literally the convergence rate of planning."),
        FormulaEntry("Myopic case", "γ = 0 ⟹ Q(s,a) = E[r]", "A pure one-step bandit — no sequential reasoning at all."),
    ),
    notationKey = listOf(
        NotationEntry("γ", "discount factor, gamma"),
        NotationEntry("H", "effective horizon in steps"),
        NotationEntry("Gₜ", "return from timestep t"),
        NotationEntry("episodic", "task with a natural terminal state; γ=1 is sometimes admissible"),
        NotationEntry("continuing", "task with no terminal state; γ<1 is mandatory"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Discounted returns, computed backwards",
            accentColor = 0xFF6366F1,
            code = """
                def discounted_returns(rewards, gamma=0.99):
                    \"\"\"Backwards accumulation is O(n) — the forward double loop is O(n^2)
                    and computes the same thing.\"\"\"
                    out, running = [0.0] * len(rewards), 0.0
                    for t in reversed(range(len(rewards))):
                        running = rewards[t] + gamma * running
                        out[t] = running
                    return out

                # A reward of 1.0 arriving 100 steps away is worth:
                #   gamma=0.9   ->  0.9**100  = 0.000027   (invisible)
                #   gamma=0.99  ->  0.99**100 = 0.366      (clearly felt)
                #   gamma=0.999 ->  0.999**100 = 0.905     (nearly undiscounted)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Decoupling horizon from variance with n-step returns",
            accentColor = 0xFF10B981,
            code = """
                # Instead of raising gamma to make distant reward matter, take more real
                # reward before bootstrapping. This lengthens credit assignment without
                # changing the discount, and trades a little variance for much less bias.
                def n_step_target(rewards, next_value, gamma=0.99, n=5):
                    g = 0.0
                    for k in range(n):
                        g += (gamma ** k) * rewards[k]
                    return g + (gamma ** n) * next_value

                # GAE generalizes this with lambda, sweeping smoothly between
                # n=1 (low variance, high bias) and Monte Carlo (unbiased, high variance).
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari", "DQN uses γ = 0.99 — roughly a hundred frames, enough to connect a paddle move to the point it scores."),
        ApplicationCard("finance", 0xFF60A5FA, "Economics", "γ is the same object as a discount rate on future cash flows; RL inherited both the maths and the name."),
        ApplicationCard("robot", 0xFF10B981, "Continuing Control", "A walking robot has no terminal state, so γ < 1 is what keeps its value function finite at all."),
    ),
    takeaways = listOf(
        "γ does two jobs: it guarantees the return converges, and it sets the agent's effective horizon at ~1/(1−γ).",
        "γ = 0 is a bandit; γ → 1 is a planner that is slow and unstable to train.",
        "Raising γ increases bootstrap error and return variance — it is a real trade, not a free upgrade.",
        "Prefer n-step returns or GAE over pushing γ toward 1 when credit fails to propagate.",
    ),
    crossLinks = listOf(
        CrossLink("mdp", "MDP"),
        CrossLink("value_function", "Value Function (V)"),
        CrossLink("gae", "GAE"),
        CrossLink("state_action_reward", "State, Action, Reward"),
    ),
)
