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

internal val neuralOdesContent = TopicContent(
    topicId = "neural_odes",
    figure = Figure(
        caption = "The page's lab: dz/dt = −z from z(0) = 1, whose exact answer is e⁻ᵗ. Four " +
            "residual blocks are four Euler steps of h = 0.5: each multiplies z by (1 − 0.5), so " +
            "the path drops in straight jumps of 0.5, 0.25, 0.125 and lands on 0.0625 at t = 2. " +
            "The exact value is 0.1353, so four ResNet-style steps miss by 54%. RK4 with the " +
            "same four steps misses by 0.00021, about 0.2%. A neural ODE learns f and leaves the " +
            "step count to an adaptive solver, which takes more steps where the curve bends fast.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "exact e⁻ᵗ",
                    listOf(
                        FigurePoint(0f, 1f), FigurePoint(0.125f, 0.779f), FigurePoint(0.25f, 0.607f),
                        FigurePoint(0.375f, 0.472f), FigurePoint(0.5f, 0.368f), FigurePoint(0.625f, 0.287f),
                        FigurePoint(0.75f, 0.223f), FigurePoint(0.875f, 0.174f), FigurePoint(1f, 0.135f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "4 Euler steps",
                    listOf(
                        FigurePoint(0f, 1f), FigurePoint(0.25f, 0.5f), FigurePoint(0.5f, 0.25f),
                        FigurePoint(0.75f, 0.125f), FigurePoint(1f, 0.0625f),
                    ),
                    tone = FigureTone.Warn,
                    dashed = true,
                ),
            ),
            markers = listOf(
                FigurePoint(1f, 0.135f, "0.1353"),
                FigurePoint(1f, 0.0625f, "0.0625", FigureTone.Warn),
            ),
            xLabel = "t, 0 → 2",
            yLabel = "z(t), 0 to 1",
        ),
    ),
    whatIsIt = listOf(
        "The lab makes the link concrete on dz/dt = −z from z(0) = 1, whose exact answer at t = 2 is e⁻² = 0.1353. Four residual blocks are four Euler steps of size 0.5, each multiplying z by (1 − 0.5), so they land on 0.0625 — 54% off. RK4 with the same four steps lands within 0.00021, about 0.2%. A neural ODE learns f and lets an adaptive solver choose how many steps to take, more where the dynamics change fast.",
        "A ResNet's residual connection, h_{t+1} = h_t + f(h_t), is one Euler step of a differential equation with a step size of exactly 1. A Neural ODE takes that observation literally: instead of a fixed stack of discrete layers, it defines dz/dt = f(z, t) and hands the whole thing to a numerical ODE solver, which decides how many steps to take and how large each one should be. Depth becomes a solver setting rather than an architectural choice — and because the solver's accuracy is a well-understood property of numerical analysis rather than a network hyperparameter, it can be checked directly: on dz/dt = −z, whose exact solution z(t) = z₀e⁻ᵗ is known in closed form, Euler's error at t = 2 falls by a factor of almost exactly 2 every time the step count doubles — 0.0576, 0.0280, 0.0138, 0.0068, 0.0034 across five doublings — first-order convergence, to the letter. (Those five step counts start at 5, not the lab's 4.)",
        "A fourth-order solver like RK4, run on the identical equation, converges far faster: its error drops by a factor of roughly 16 with every doubling of steps — 8.07×10⁻⁵ down to 9.0×10⁻¹⁰ — and at matched step counts it is already several orders of magnitude more accurate than Euler. That is not a Neural-ODE-specific claim; it is the standard convergence order of any fourth-order Runge-Kutta method on a smooth system, verified here rather than assumed, because a network whose entire depth is \"however many steps the solver takes\" inherits the solver's error behavior directly.",
        "The other half of the argument is a counting one, not a numerical one. Backpropagating through a discrete N-layer ResNet requires storing every layer's activation for the backward pass — N times the state size. The adjoint sensitivity method solves a second ODE backward through the same dynamics to reconstruct gradients, needing only the current state and its adjoint at any instant — a constant, roughly 2× the state size, regardless of how many effective steps the forward solve took. For a 50-layer network with a 64-dimensional state, that is 3,200 stored values against 128 — a 25× difference that grows linearly with depth on one side and stays fixed on the other.",
    ),
    steps = listOf(
        StepCard(1, "See the ResNet Update as One Euler Step", "h_{t+1} = h_t + f(h_t) is Euler's method with step size 1.", 0xFF14B8A6),
        StepCard(2, "Define a Continuous Dynamics Instead", "dz/dt = f(z, t) — no fixed layer count, just a function to integrate.", 0xFF3B82F6),
        StepCard(3, "Hand It to a Solver", "Euler, RK4, or an adaptive-step method decides how finely to integrate.", 0xFF10B981),
        StepCard(4, "Check the Solver's Error Against a Known Answer", "Euler: error halves per doubling. RK4: error drops ~16x per doubling.", 0xFFF59E0B),
        StepCard(5, "Backprop via the Adjoint, Not Stored Activations", "A second backward ODE — O(1) memory instead of O(depth).", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Continuous depth", "dz/dt = f(z, t)", "The ResNet update in the limit of infinitesimal steps."),
        FormulaEntry("Euler step", "z_{n+1} = z_n + h·f(z_n)", "First order: global error scales as O(h)."),
        FormulaEntry("RK4 step", "z_{n+1} = z_n + (h/6)(k₁+2k₂+2k₃+k₄)", "Fourth order: global error scales as O(h⁴)."),
        FormulaEntry("Measured convergence", "Euler ×2, RK4 ×16", "Error reduction per doubling of steps — matches theory exactly."),
    ),
    notationKey = listOf(
        NotationEntry("z(t)", "the continuous hidden state, in place of a discrete layer's activation"),
        NotationEntry("h", "the solver's step size — smaller h means more steps, more accuracy, more compute"),
        NotationEntry("adjoint", "the backward-time sensitivity variable used to compute gradients without storing activations"),
        NotationEntry("O(1) memory", "constant in depth — the adjoint method's whole point"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Convergence order, verified against a closed-form solution",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def f(z):
                    return -z                       # dz/dt = -z, exact solution z0 * exp(-t)

                def euler(z0, t, steps):
                    z, h = z0, t / steps
                    for _ in range(steps):
                        z += h * f(z)
                    return z

                def rk4(z0, t, steps):
                    z, h = z0, t / steps
                    for _ in range(steps):
                        k1 = f(z)
                        k2 = f(z + h/2 * k1)
                        k3 = f(z + h/2 * k2)
                        k4 = f(z + h * k3)
                        z += h/6 * (k1 + 2*k2 + 2*k3 + k4)
                    return z

                exact = np.exp(-2.0)                # z0=1, t=2
                for steps in (5, 10, 20, 40, 80):
                    print(steps, abs(euler(1.0, 2.0, steps) - exact), abs(rk4(1.0, 2.0, steps) - exact))

                # euler error ratio between doublings: ~2.0, every time  (first order)
                # rk4 error ratio between doublings:   ~16, every time  (fourth order)

                # Memory: a 50-layer ResNet, 64-dim state -> 50 * 64 = 3200 stored activations.
                # The adjoint method needs only the state + adjoint: 2 * 64 = 128.  Ratio: 25x.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF3B82F6, "Irregularly-Sampled Time Series", "A continuous model naturally handles gaps a fixed-step RNN cannot."),
        ApplicationCard("Image", 0xFF10B981, "Continuous Normalizing Flows", "Invertible-by-construction generative models built on the same ODE machinery."),
        ApplicationCard("chart", 0xFFF59E0B, "Adaptive Compute", "The solver can take more steps on hard inputs, fewer on easy ones."),
        ApplicationCard("help", 0xFFEC4899, "The Cost", "Solving an ODE is slower per forward pass than one fixed matrix multiply per layer."),
    ),
    takeaways = listOf(
        "A ResNet's residual update is exactly one Euler step; a Neural ODE generalizes it to a continuous dynamics integrated by a real solver.",
        "Euler's error at a fixed endpoint halves every time the step count doubles — verified directly against a closed-form exact solution.",
        "RK4's error drops by roughly 16x per doubling — fourth-order convergence, the standard numerical-analysis fact, not a Neural-ODE-specific claim.",
        "The adjoint method backpropagates via a second backward-time ODE, needing only the current state and adjoint — not every layer's stored activation.",
        "Counted exactly: a 50-layer, 64-dimensional ResNet stores 3,200 values for backprop; the adjoint method needs 128 — a 25x difference that only grows with depth.",
    ),
    crossLinks = listOf(
        CrossLink("resnet", "ResNet (Residual Connections)"),
        CrossLink("backpropagation", "Backpropagation Algorithm"),
        CrossLink("kan", "Kolmogorov-Arnold Networks (KAN)"),
        CrossLink("rnn", "RNNs"),
    ),
)
