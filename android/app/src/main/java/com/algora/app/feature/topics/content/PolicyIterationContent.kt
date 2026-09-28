package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val policyIterationContent = TopicContent(
    topicId = "policy_iteration",
    whatIsIt = listOf(
        "Policy iteration alternates two operations until they stop disagreeing. Evaluation computes Vπ for the current policy exactly. Improvement replaces the policy with the one that acts greedily on those values. Repeat. When improvement changes nothing, you are done — and for a finite MDP, done means optimal.",
        "The policy improvement theorem is what makes the loop safe rather than hopeful. If a new policy π′ is greedy with respect to Vπ, then Vπ′(s) ≥ Vπ(s) for every state simultaneously — never better here at the cost of worse there. So each round is a strict improvement unless the policy is already optimal, and since a finite MDP has finitely many deterministic policies, the loop cannot run forever and cannot cycle.",
        "In practice it converges in remarkably few rounds — often a handful, even for large state spaces — because each round extracts a great deal from a fully converged evaluation. The trade against value iteration is exactly that: policy iteration does few, expensive rounds; value iteration does many cheap ones. Generalized policy iteration is the observation that you can sit anywhere between the two, and that most of RL, deep methods included, is some point on that spectrum.",
    ),
    steps = listOf(
        StepCard(1, "Start Anywhere", "Any policy will do, including a bad one. Correctness does not depend on the initial guess.", 0xFF6366F1),
        StepCard(2, "Evaluate to Convergence", "Compute Vπ for that exact policy — iteratively, or by solving the linear system.", 0xFF818CF8),
        StepCard(3, "Improve Greedily", "For each state pick argmaxₐ of reward plus discounted next value under Vπ.", 0xFF60A5FA),
        StepCard(4, "Check for Change", "If no state's action changed, the policy is stable and the loop ends.", 0xFF10B981),
        StepCard(5, "Rely on the Theorem", "Any change is guaranteed to improve every state's value at once — no state is traded away.", 0xFFF59E0B),
        StepCard(6, "Terminate in Finite Rounds", "Finitely many policies, strict improvement each round, so termination is guaranteed.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Evaluation", "Vπ = Rπ + γPπVπ", "Linear in V for a fixed π — solvable exactly."),
        FormulaEntry("Improvement", "π′(s) = argmaxₐ Σₛ′ P(s′|s,a)[r + γVπ(s′)]", "Greedy with respect to the current values."),
        FormulaEntry("Improvement theorem", "Qπ(s,π′(s)) ≥ Vπ(s) ∀s ⟹ Vπ′ ≥ Vπ", "Improvement is simultaneous across all states."),
        FormulaEntry("Termination", "π′ = π ⟹ π satisfies Bellman optimality", "A stable policy is an optimal one."),
        FormulaEntry("Round count", "≤ |A|^|S| but typically O(few)", "The bound is astronomically loose in practice."),
    ),
    notationKey = listOf(
        NotationEntry("π′", "the improved policy"),
        NotationEntry("policy-stable", "improvement produced no change; the stopping condition"),
        NotationEntry("GPI", "generalized policy iteration — any interleaving of the two steps"),
        NotationEntry("Pπ", "transition matrix under π"),
        NotationEntry("modified PI", "truncating evaluation to k sweeps instead of convergence"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Policy iteration in full",
            accentColor = 0xFF6366F1,
            code = """
                def policy_iteration(states, actions, P, R, gamma=0.9):
                    policy = {s: actions[0] for s in states}
                    rounds = 0

                    while True:
                        rounds += 1
                        # 1. Evaluate: solve for V^pi under the CURRENT policy.
                        V = {s: 0.0 for s in states}
                        while True:
                            delta = 0.0
                            for s in states:
                                if is_terminal(s):
                                    continue
                                a = policy[s]
                                v = sum(P[s][a][s2] * (R[s][a][s2] + gamma * V[s2])
                                        for s2 in states)
                                delta = max(delta, abs(v - V[s]))
                                V[s] = v
                            if delta < 1e-9:
                                break

                        # 2. Improve: act greedily on those values.
                        stable = True
                        for s in states:
                            if is_terminal(s):
                                continue
                            best = max(actions, key=lambda a: sum(
                                P[s][a][s2] * (R[s][a][s2] + gamma * V[s2]) for s2 in states))
                            if best != policy[s]:
                                stable = False
                            policy[s] = best

                        if stable:
                            return policy, V, rounds
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Modified policy iteration: the dial between the two",
            accentColor = 0xFF10B981,
            code = """
                # Evaluation does not have to run to convergence. Truncate it at k sweeps and
                # you get a family of algorithms:
                #   k = 1        -> value iteration
                #   k = infinity -> policy iteration
                #   k = 5..20    -> usually the fastest in wall-clock terms
                def modified_policy_iteration(states, actions, P, R, gamma=0.9, k=10):
                    V = {s: 0.0 for s in states}
                    policy = {s: actions[0] for s in states}
                    while True:
                        for _ in range(k):                       # partial evaluation
                            V = {s: backup(s, policy[s], V, P, R, gamma) for s in states}
                        new_policy = {s: greedy(s, V, P, R, gamma) for s in states}
                        if new_policy == policy:
                            return policy, V
                        policy = new_policy
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Exact MDP Solving", "The standard tool for small, fully-known MDPs in operations research and control."),
        ApplicationCard("trend", 0xFF818CF8, "Actor-Critic", "The critic evaluates, the actor improves — deep actor-critic is policy iteration with both steps approximated."),
        ApplicationCard("game", 0xFF10B981, "AlphaZero", "MCTS produces an improved policy, the network is trained toward it, repeat. Policy iteration at scale."),
    ),
    takeaways = listOf(
        "Alternate exact evaluation with greedy improvement until the policy stops changing.",
        "The policy improvement theorem guarantees every state improves at once — nothing is traded away.",
        "Finitely many policies plus strict improvement gives guaranteed termination, usually in very few rounds.",
        "Truncating evaluation to k sweeps interpolates continuously to value iteration — the GPI spectrum most of RL sits on.",
    ),
    crossLinks = listOf(
        CrossLink("value_iteration", "Value Iteration"),
        CrossLink("value_function", "Value Function (V)"),
        CrossLink("actor_critic", "Actor-Critic"),
        CrossLink("alphazero", "AlphaZero"),
    ),
)
