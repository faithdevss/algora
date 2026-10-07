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

internal val dynamicProgrammingContent = TopicContent(
    topicId = "dynamic_programming",
    figure = Figure(
        caption = "The page's lab, run both ways on its 4×4 grid with the model in hand: how many of " +
            "the 13 non-terminal states hold their final value after each sweep. A synchronous " +
            "sweep computes every new value from the previous sweep's table, so value spreads " +
            "exactly one step per sweep — 1, 3, 5, 9, 12, then all 13 at sweep 6 — and the largest " +
            "change falls 1.000, 0.900, 0.810, 0.729 before reaching zero; it needs 7 sweeps to see " +
            "a change below 0.0001. An in-place sweep overwrites as it goes, so a state visited " +
            "after its neighbour already reads the neighbour's new value: 6 states are final after " +
            "one sweep, 9 after two, all 13 after three, and it confirms convergence in 4 sweeps. " +
            "Same fixed point either way — sweep order changes the speed, never the answer. Each " +
            "sweep is |S|·|A| backups over every successor, which is the cost the curse of " +
            "dimensionality multiplies.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "synchronous",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.143f, 0.077f), FigurePoint(0.286f, 0.231f),
                        FigurePoint(0.429f, 0.385f), FigurePoint(0.571f, 0.692f), FigurePoint(0.714f, 0.923f),
                        FigurePoint(0.857f, 1f), FigurePoint(1f, 1f),
                    ),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "in-place",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.143f, 0.462f), FigurePoint(0.286f, 0.692f),
                        FigurePoint(0.429f, 1f), FigurePoint(1f, 1f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.429f, 1f, "in-place: 3"),
                FigurePoint(0.857f, 1f, "synchronous: 6", FigureTone.Muted),
            ),
            xLabel = "sweep, 0 → 7",
            yLabel = "states at final value, 0 to 13",
        ),
    ),
    whatIsIt = listOf(
        "Dynamic programming solves an MDP exactly by sweeping the whole state space, using the transition and reward functions directly. It is the same DP you already know from coin change or longest common subsequence — overlapping subproblems, optimal substructure, results reused instead of recomputed — with a state's value playing the role of the memo table entry.",
        "The distinguishing precondition is the model. DP queries P(s′|s,a) and R(s,a) as functions you can call, so it never runs an episode and never touches the environment. In RL vocabulary this makes it planning rather than learning. Everything after it in this section — Monte Carlo, TD, Q-learning, DQN — exists to recover DP's answers when those functions are unavailable, which in practice is nearly always.",
        "Its second limitation is the sweep itself. Every iteration touches every state, so cost scales with |S|²·|A| (every successor of every state-action pair), and |S| explodes combinatorially in any interesting problem — Bellman's own \"curse of dimensionality\". Backgammon has around 10²⁰ states and a sweep is not merely slow but impossible. This is exactly the pressure that produces sampling (visit only states you actually reach) and function approximation (generalize across states rather than tabulate them), which together are the whole of modern RL.",
    ),
    steps = listOf(
        StepCard(1, "Require the Model", "P and R must be known and queryable. Without them, none of this applies.", 0xFF6366F1),
        StepCard(2, "Initialize the Table", "One value per state, arbitrary except that terminals are fixed.", 0xFF818CF8),
        StepCard(3, "Sweep Every State", "Apply the Bellman backup to each state in turn — the DP transition step.", 0xFF60A5FA),
        StepCard(4, "Reuse Neighbours' Answers", "Each backup consumes values already computed. Overlapping subproblems, memoized.", 0xFF10B981),
        StepCard(5, "Iterate to Convergence", "Repeat until the largest change falls below a threshold. Contraction guarantees this terminates.", 0xFFF59E0B),
        StepCard(6, "Notice the Ceiling", "Cost is per-sweep |S|²·|A|. Beyond modest |S| you must sample or approximate instead.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("The backup", "V(s) ← maxₐ Σₛ′ P(s′|s,a)[r + γV(s′)]", "One DP transition, applied per state."),
        FormulaEntry("Sweep cost", "O(|S|²·|A|) per iteration", "|S| states × |A| actions × |S| possible successors."),
        FormulaEntry("Iterations", "O(log(1/ε)/log(1/γ))", "Geometric convergence at rate γ."),
        FormulaEntry("Optimal substructure", "V*(s) uses V*(s′)", "The optimal solution contains optimal sub-solutions."),
        FormulaEntry("Curse of dimensionality", "|S| = ∏ᵢ |dimᵢ|", "State count multiplies with every variable added."),
    ),
    notationKey = listOf(
        NotationEntry("sweep", "one pass applying the backup to every state"),
        NotationEntry("planning", "computing a policy from a known model"),
        NotationEntry("learning", "computing a policy from sampled experience"),
        NotationEntry("in-place", "overwriting values during a sweep; usually converges faster"),
        NotationEntry("asynchronous DP", "updating states in any order, not full sweeps"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The same DP shape you already know",
            accentColor = 0xFF6366F1,
            code = """
                # Coin change: value of a subproblem from smaller subproblems.
                for amount in range(1, target + 1):
                    dp[amount] = min(dp[amount - c] + 1 for c in coins if c <= amount)

                # Value iteration: value of a state from its successors' values.
                for s in states:
                    V[s] = max(sum(P[s][a][s2] * (R[s][a][s2] + gamma * V[s2])
                                   for s2 in states)
                               for a in actions(s))

                # Identical structure. The differences: an MDP's dependency graph has cycles
                # (states reach each other both ways), so there is no topological order to
                # follow and you iterate to a fixed point instead of filling a table once.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "In-place sweeps converge faster",
            accentColor = 0xFF10B981,
            code = """
                def value_iteration(states, actions, P, R, gamma=0.9, eps=1e-8):
                    V = {s: 0.0 for s in states}
                    sweeps = 0
                    while True:
                        delta = 0.0
                        for s in states:
                            if is_terminal(s):
                                continue
                            old = V[s]
                            # In-place: later states in this sweep already see the updated
                            # values of earlier ones. Same fixed point, fewer sweeps.
                            V[s] = max(sum(P[s][a][s2] * (R[s][a][s2] + gamma * V[s2])
                                           for s2 in states)
                                       for a in actions)
                            delta = max(delta, abs(V[s] - old))
                        sweeps += 1
                        if delta < eps:
                            return V, sweeps
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("stack", 0xFF6366F1, "Operations Research", "Inventory control, equipment replacement and scheduling are small enough to solve exactly this way."),
        ApplicationCard("chip", 0xFF818CF8, "Model-Based RL", "Dyna-Q learns a model from experience and then runs DP-style backups against it — planning on a learned map."),
        ApplicationCard("game", 0xFF10B981, "Where It Runs Out", "Backgammon has ~10²⁰ states. One sweep is not slow, it is impossible — which is why TD-Gammon sampled instead."),
    ),
    takeaways = listOf(
        "RL's DP is the DP you already know: overlapping subproblems and optimal substructure over states.",
        "It requires the model, which makes it planning rather than learning.",
        "Cyclic dependencies mean you iterate to a fixed point instead of filling a table in topological order.",
        "Full sweeps cost |S|²·|A|, and |S| explodes — the pressure that produced sampling and function approximation.",
    ),
    crossLinks = listOf(
        CrossLink("bellman_equation", "Bellman Equation"),
        CrossLink("value_iteration", "Value Iteration"),
        CrossLink("dyna_q", "Dyna-Q"),
        CrossLink("coin_change", "Coin Change (DSA)"),
        CrossLink("fibonacci_dp", "Fibonacci DP (DSA)"),
    ),
)
