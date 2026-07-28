package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bellmanEquationContent = TopicContent(
    topicId = "bellman_equation",
    whatIsIt = listOf(
        "The Bellman equation says a state's value must equal the immediate reward for leaving it plus the discounted value of wherever it lands. It is a consistency condition, not a procedure — a description of what a correct value function looks like, which every algorithm in this section then chases in a different way.",
        "It comes in two forms and the difference is one operator. The expectation equation averages over the actions a given policy would take, and describes Vπ. The optimality equation takes a max over actions instead, and describes V*. Expectation is what you evaluate; max is what you optimize. Nearly every confusion about the tabular methods dissolves once you can tell which of the two you are looking at.",
        "Its power comes from being a fixed point. Define an operator T that applies one backup everywhere; then V is correct exactly when TV = V. Because T is a γ-contraction in the max-norm, repeatedly applying it to any starting guess converges to that unique fixed point at rate γ — you can start from zeros, from noise, from a previous run's answer, and still land in the same place. That single fact is what licenses value iteration, and its approximate version is what licenses Q-learning.",
    ),
    steps = listOf(
        StepCard(1, "Look One Step Ahead", "From state s take action a. Record the reward and where you land.", 0xFF6366F1),
        StepCard(2, "Add the Discounted Future", "Add γ times the value already assigned to that next state — an estimate leaning on an estimate.", 0xFF818CF8),
        StepCard(3, "Combine Over Actions", "Average weighted by π for the expectation form, or take the max for the optimality form.", 0xFF60A5FA),
        StepCard(4, "Assert Equality", "That result must equal V(s). One equation per state, |S| equations in |S| unknowns.", 0xFF10B981),
        StepCard(5, "Solve or Iterate", "Solve the linear system directly for small |S|, or apply the operator repeatedly.", 0xFFF59E0B),
        StepCard(6, "Lean on the Contraction", "Each application shrinks the worst-case error by γ, so convergence is geometric from any start.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Expectation (V)", "Vπ(s) = Σₐ π(a|s) Σₛ′ P(s′|s,a)[r + γVπ(s′)]", "Averages over actions — evaluates a policy."),
        FormulaEntry("Optimality (V)", "V*(s) = maxₐ Σₛ′ P(s′|s,a)[r + γV*(s′)]", "Maximizes over actions — finds the best policy."),
        FormulaEntry("Optimality (Q)", "Q*(s,a) = E[r + γ maxₐ′ Q*(s′,a′)]", "The form Q-learning samples."),
        FormulaEntry("Fixed point", "V = TV", "Correct exactly when the backup changes nothing."),
        FormulaEntry("Contraction", "‖TU − TV‖∞ ≤ γ‖U − V‖∞", "Guarantees a unique solution and geometric convergence."),
        FormulaEntry("Linear solve", "Vπ = (I − γPπ)⁻¹ Rπ", "The expectation form is linear, so it has a closed form."),
    ),
    notationKey = listOf(
        NotationEntry("T", "Bellman operator — one full backup over all states"),
        NotationEntry("fixed point", "a V with TV = V"),
        NotationEntry("‖·‖∞", "max-norm: the largest error across all states"),
        NotationEntry("Pπ", "transition matrix induced by following π"),
        NotationEntry("γ-contraction", "each application shrinks the error by at least a factor γ"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Both forms, side by side",
            accentColor = 0xFF6366F1,
            code = """
                def bellman_expectation(s, V, policy, P, R, gamma):
                    \"\"\"Describes V^pi. Note the weighted SUM over actions.\"\"\"
                    return sum(
                        policy[s][a] * sum(P[s][a][s2] * (R[s][a][s2] + gamma * V[s2])
                                           for s2 in states)
                        for a in actions(s)
                    )

                def bellman_optimality(s, V, P, R, gamma):
                    \"\"\"Describes V*. Identical, except sum-over-actions becomes MAX.\"\"\"
                    return max(
                        sum(P[s][a][s2] * (R[s][a][s2] + gamma * V[s2]) for s2 in states)
                        for a in actions(s)
                    )
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Solving the expectation form exactly",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                # For a FIXED policy the equation is linear in V, so no iteration is needed:
                #   V = R_pi + gamma * P_pi @ V   =>   (I - gamma*P_pi) V = R_pi
                # The inverse always exists because gamma < 1 keeps the spectral radius below 1.
                def solve_policy_values(P_pi, R_pi, gamma):
                    n = len(R_pi)
                    return np.linalg.solve(np.eye(n) - gamma * P_pi, R_pi)

                # The OPTIMALITY form has a max in it, so it is nonlinear and has no such
                # closed form. That is precisely why value iteration exists.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Every Algorithm Here", "Value iteration, policy iteration, TD, SARSA and Q-learning are all methods for solving the same equation."),
        ApplicationCard("stack", 0xFF818CF8, "Shortest Paths", "Set γ = 1 with deterministic transitions and the optimality equation becomes the relaxation step in Bellman-Ford."),
        ApplicationCard("finance", 0xFF10B981, "Optimal Stopping", "Option pricing and replacement scheduling are Bellman equations over a continuation-versus-stop decision."),
    ),
    takeaways = listOf(
        "The Bellman equation is a consistency condition on values, not an algorithm.",
        "Sum over actions gives the expectation form (Vπ); max gives the optimality form (V*).",
        "The correct value function is the operator's unique fixed point.",
        "γ-contraction is why iterating from any starting guess converges, and why it converges geometrically.",
    ),
    crossLinks = listOf(
        CrossLink("value_function", "Value Function (V)"),
        CrossLink("value_iteration", "Value Iteration"),
        CrossLink("dynamic_programming", "Dynamic Programming"),
        CrossLink("bellman_ford", "Bellman-Ford (DSA)"),
    ),
)
