package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val valueFunctionContent = TopicContent(
    topicId = "value_function",
    whatIsIt = listOf(
        "The value function Vπ(s) answers one question: starting in state s and following policy π forever, what total discounted reward do I expect? It converts a stream of delayed rewards into a single number attached to the present, which is what makes long-horizon credit assignment tractable.",
        "The crucial detail is the superscript. Value is always relative to a policy — there is no such thing as \"the value of a state\" on its own. A square one step from the cliff is worth a lot under a careful policy and very little under a random one. This is why policy evaluation (compute Vπ for a fixed π) and policy improvement (make π greedy with respect to it) are separate operations that alternate.",
        "V is not directly actionable. Knowing that a neighbouring state is worth 0.8 tells you nothing about which action gets you there unless you also know the transition model. That gap is exactly what the Q-function closes, and it is the reason model-free control is built on Q rather than V — while V remains the natural choice as a baseline in policy-gradient methods, where you only need to know whether an action did better than average.",
    ),
    steps = listOf(
        StepCard(1, "Fix a Policy", "Value is defined relative to behaviour. Nothing can be evaluated until π is pinned down.", 0xFF818CF8),
        StepCard(2, "Initialize V", "Zeros everywhere except terminals, whose value is their reward and never changes.", 0xFF60A5FA),
        StepCard(3, "Back Up One Step", "Replace V(s) with the expected immediate reward plus γ times the value of where π lands.", 0xFF8B5CF6),
        StepCard(4, "Sweep Until Stable", "Repeat over all states. The Bellman operator is a γ-contraction, so it converges to a unique Vπ.", 0xFF10B981),
        StepCard(5, "Read the Gradient", "Value now spreads outward from reward. Higher-valued neighbours mark the direction of the goal.", 0xFFF59E0B),
        StepCard(6, "Improve, Then Re-evaluate", "Act greedily with respect to Vπ, then evaluate the new policy. That alternation is policy iteration.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "Vπ(s) = Eπ[Gₜ | sₜ = s]", "Expected return from s under π."),
        FormulaEntry("Bellman expectation", "Vπ(s) = Σₐ π(a|s) Σₛ′ P(s′|s,a)[r + γVπ(s′)]", "Recursive, one step of lookahead."),
        FormulaEntry("Bellman optimality", "V*(s) = maxₐ Σₛ′ P(s′|s,a)[r + γV*(s′)]", "Expectation over actions becomes a max."),
        FormulaEntry("Relation to Q", "Vπ(s) = Σₐ π(a|s)Qπ(s,a)", "V is Q averaged under the policy."),
        FormulaEntry("Contraction", "‖TV − TV′‖∞ ≤ γ‖V − V′‖∞", "Why iterative evaluation converges, and why γ < 1 matters."),
    ),
    notationKey = listOf(
        NotationEntry("Vπ(s)", "state value under policy π"),
        NotationEntry("V*(s)", "optimal state value"),
        NotationEntry("T", "the Bellman operator, one full backup"),
        NotationEntry("γ", "discount factor — also the contraction modulus"),
        NotationEntry("‖·‖∞", "max-norm; the largest error over all states"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Iterative policy evaluation",
            accentColor = 0xFF6366F1,
            code = """
                def evaluate(policy, states, P, R, gamma=0.9, eps=1e-8):
                    \"\"\"Compute V^pi for a FIXED policy. Note the sum over actions
                    weighted by pi — not a max. That is the whole difference from
                    value iteration.\"\"\"
                    V = {s: 0.0 for s in states}
                    while True:
                        delta = 0.0
                        for s in states:
                            if is_terminal(s):
                                continue
                            v_new = sum(
                                policy[s][a] * sum(
                                    P[s][a][s2] * (R[s][a][s2] + gamma * V[s2])
                                    for s2 in states
                                )
                                for a in actions(s)
                            )
                            delta = max(delta, abs(v_new - V[s]))
                            V[s] = v_new
                        if delta < eps:
                            return V
            """.trimIndent(),
        ),
        CodeBlock(
            title = "V as a policy-gradient baseline",
            accentColor = 0xFF10B981,
            code = """
                # V's main modern use is not control — it is variance reduction. Subtracting a
                # state-dependent baseline leaves the gradient unbiased (its expectation is zero)
                # while shrinking its variance, because "did this action beat the state's average?"
                # is a far less noisy signal than "what was the raw return?".
                advantage = returns - value_net(states).detach()
                policy_loss = -(log_probs * advantage).mean()
                value_loss = F.mse_loss(value_net(states), returns)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("chart", 0xFF818CF8, "Policy Iteration", "Evaluate then improve, alternating to the optimal policy — the classical planning algorithm."),
        ApplicationCard("trend", 0xFF10B981, "Actor-Critic Baselines", "The critic in A2C, PPO and GAE is a value network; its whole job is cutting gradient variance."),
        ApplicationCard("game", 0xFFF59E0B, "Board-Game Search", "AlphaZero's value head evaluates a position so MCTS can stop the rollout early instead of playing to the end."),
    ),
    takeaways = listOf(
        "Vπ(s) is expected discounted return from s under a specific policy — the superscript is not decoration.",
        "The Bellman operator is a γ-contraction, which is why repeated sweeps converge to a unique fixed point.",
        "V alone cannot select actions without a model; that is Q's job.",
        "In modern deep RL, V's dominant role is as a baseline that reduces policy-gradient variance.",
    ),
    crossLinks = listOf(
        CrossLink("q_function", "Q-Function (Q)"),
        CrossLink("policy", "The Policy (π)"),
        CrossLink("gae", "GAE"),
        CrossLink("actor_critic", "Actor-Critic"),
    ),
)
