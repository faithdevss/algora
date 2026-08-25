package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val valueIterationContent = TopicContent(
    topicId = "value_iteration",
    figure = Figure(
        caption = "Four cells of this page's own 4×4 world, sweep by sweep, run at γ = 0.9 with " +
            "−0.04 a step, +1 at the goal and −1 at the pit. Value does not fade in everywhere at " +
            "once — it arrives as a wavefront. Sweep 1 gives every non-terminal the step cost; " +
            "sweep 2 reaches (1,2), the cell beside the goal, at +0.860; sweep 3 reaches (0,0) " +
            "and sweep 5 reaches (3,1), and the start at (3,0) — six steps from the goal — turns " +
            "positive only at sweep 6, at +0.427. The marked cell in each column is the sweep " +
            "where that state reached its final value and stopped moving. What the table is " +
            "really for is the gap between two convergences: the largest change per sweep runs " +
            "1.000, 0.900, 0.810, 0.729, 0.656, 0.590 and then 0.000, so the values settle at " +
            "sweep 6 — but the greedy policy has been optimal everywhere since sweep 3, while " +
            "three of these four numbers were still wrong. Ranking actions needs the differences " +
            "to be right, not the magnitudes, so a run that only wants a policy can stop at half " +
            "the work.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.000", "0.000", "0.000", "0.000"),
                listOf("−0.040", "−0.040", "−0.040", "−0.040"),
                listOf("−0.076", "+0.860", "−0.076", "−0.076"),
                listOf("+0.734", "+0.860", "−0.108", "−0.108"),
                listOf("+0.734", "+0.860", "−0.138", "−0.138"),
                listOf("+0.734", "+0.860", "+0.519", "−0.164"),
                listOf("+0.734", "+0.860", "+0.519", "+0.427"),
            ),
            rowHeaders = listOf("0", "1", "2", "3", "4", "5", "6"),
            colHeaders = listOf("(0,0)", "(1,2)", "(3,1)", "(3,0)"),
            marks = listOf(
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(3, 0, FigureTone.Accent),
                FigureCell(5, 2, FigureTone.Accent),
                FigureCell(6, 3, FigureTone.Accent),
                FigureCell(3, 2, FigureTone.Warn),
                FigureCell(3, 3, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Value iteration is policy iteration with the evaluation step cut down to a single sweep. Rather than compute Vπ exactly and then improve, it applies one Bellman optimality backup — reward plus discounted best successor value — to every state, and repeats. No policy is ever stored; the max inside the backup does the improving.",
        "Convergence follows directly from contraction. The optimality operator shrinks the max-norm error by at least γ each sweep, so error after k sweeps is at most γᵏ times the initial error and any starting guess works. The practical stopping rule follows from the same bound: when the largest change in a sweep is below ε, the value function is within ε·γ/(1−γ) of optimal, which is a real guarantee rather than a heuristic.",
        "The detail worth internalizing is that the greedy policy converges long before the values do. The values are still visibly moving in the fourth decimal place while argmaxₐ Q(s,a) has been the optimal action everywhere for several sweeps already — because ranking actions only requires the value differences to be right, not their magnitudes. If you want a policy rather than a value table, you can stop far earlier than the convergence test suggests, and the simulation on this page reports the exact sweep where each happens.",
    ),
    steps = listOf(
        StepCard(1, "Initialize Arbitrarily", "Zeros are conventional. Contraction means the starting guess does not affect the answer.", 0xFF6366F1),
        StepCard(2, "Back Up With a Max", "V(s) ← maxₐ of reward plus γ times the successor's current value.", 0xFF818CF8),
        StepCard(3, "Sweep All States", "One pass over the state space. Improvement is folded into the max — no separate step.", 0xFF60A5FA),
        StepCard(4, "Track the Largest Change", "Record max |Vₙₑw(s) − Vₒₗd(s)| across the sweep.", 0xFF10B981),
        StepCard(5, "Stop on the Bound", "When that change is below ε, you are within ε·γ/(1−γ) of V*.", 0xFFF59E0B),
        StepCard(6, "Extract the Policy Once", "Read π*(s) = argmax of the backup at the end. It was probably correct several sweeps ago.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("The update", "Vₖ₊₁(s) = maxₐ Σₛ′ P(s′|s,a)[r + γVₖ(s′)]", "One backup, improvement included."),
        FormulaEntry("Error bound", "‖Vₖ − V*‖∞ ≤ γᵏ‖V₀ − V*‖∞", "Geometric convergence from any start."),
        FormulaEntry("Stopping rule", "Δ < ε ⟹ ‖V − V*‖∞ < εγ/(1−γ)", "A real guarantee, not a heuristic."),
        FormulaEntry("Policy extraction", "π*(s) = argmaxₐ Σₛ′ P(s′|s,a)[r + γV(s′)]", "Done once, at the end."),
        FormulaEntry("Per-sweep cost", "O(|S|²·|A|)", "Every state, every action, every successor."),
    ),
    notationKey = listOf(
        NotationEntry("Δ", "largest value change in a sweep — the convergence measure"),
        NotationEntry("ε", "convergence threshold"),
        NotationEntry("Vₖ", "the value table after k sweeps"),
        NotationEntry("asynchronous VI", "backing up states in any order, even unevenly"),
        NotationEntry("prioritized sweeping", "ordering backups by expected change, for speed"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Value iteration with the error bound",
            accentColor = 0xFF6366F1,
            code = """
                def value_iteration(states, actions, P, R, gamma=0.9, eps=1e-6):
                    V = {s: 0.0 for s in states}
                    sweeps = 0
                    while True:
                        delta = 0.0
                        for s in states:
                            if is_terminal(s):
                                continue
                            old = V[s]
                            V[s] = max(sum(P[s][a][s2] * (R[s][a][s2] + gamma * V[s2])
                                           for s2 in states)
                                       for a in actions)
                            delta = max(delta, abs(V[s] - old))
                        sweeps += 1
                        if delta < eps:
                            bound = eps * gamma / (1 - gamma)
                            policy = {s: max(actions, key=lambda a: sum(
                                P[s][a][s2] * (R[s][a][s2] + gamma * V[s2]) for s2 in states))
                                for s in states if not is_terminal(s)}
                            return policy, V, sweeps, bound
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The policy settles before the values do",
            accentColor = 0xFFEC4899,
            code = """
                # Instrument the loop and the gap is obvious: ranking actions only needs the
                # value DIFFERENCES to be right, while the convergence test waits for their
                # magnitudes. If a policy is all you want, this is free early stopping.
                prev_policy, policy_stable_at = None, None
                for k in range(max_sweeps):
                    V, delta = sweep(V)
                    policy = greedy_policy(V)
                    if policy == prev_policy and policy_stable_at is None:
                        policy_stable_at = k
                    prev_policy = policy
                    if delta < eps:
                        print(f"values converged at sweep {k}, "
                              f"policy stopped changing at sweep {policy_stable_at}")
                        break
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("map", 0xFF6366F1, "Robot Path Planning", "Value iteration over an occupancy grid produces a cost-to-go map the robot can descend from anywhere."),
        ApplicationCard("network", 0xFF818CF8, "Q-Learning's Ancestor", "Q-learning is value iteration with the expectation replaced by a single sampled transition."),
        ApplicationCard("chip", 0xFF10B981, "Value Iteration Networks", "The backup unrolled as differentiable convolution layers, so a network can learn to plan."),
    ),
    takeaways = listOf(
        "Value iteration is policy iteration truncated to one evaluation sweep, with the max doing the improving.",
        "The contraction property gives geometric convergence from any initial guess.",
        "The Δ < ε stopping rule comes with a real bound: ε·γ/(1−γ) from optimal.",
        "The greedy policy is typically optimal several sweeps before the values converge — you can stop early.",
    ),
    crossLinks = listOf(
        CrossLink("policy_iteration", "Policy Iteration"),
        CrossLink("bellman_equation", "Bellman Equation"),
        CrossLink("dynamic_programming", "Dynamic Programming"),
        CrossLink("q_learning", "Q-Learning (off-policy)"),
    ),
)
