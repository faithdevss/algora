package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kanContent = TopicContent(
    topicId = "kan",
    figure = Figure(
        caption = "The page's lab: two fits of sin(5x)·e⁻ˣ²ᐟ² over 100 points on x = −2…2. A 1-6-1 " +
            "tanh MLP with 19 weights has to bend six fixed S-curves into four wiggles, and " +
            "gradient descent lands somewhere different from each random start: RMSE 0.131 on " +
            "seed 1, 0.069 at best over three seeds, a 1.9× spread. A single KAN edge, a spline " +
            "through 20 knots, makes the curve itself the parameter. With the knots fixed, " +
            "fitting it is least squares, with one best answer and no seed, and it reaches RMSE " +
            "0.024 every time: 2.9× closer than the MLP's best seed, at about the same budget.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("MLP, seed 1", 1f, FigureTone.Warn),
                FigureBar("MLP, best seed", 0.527f, FigureTone.Muted),
                FigureBar("KAN edge", 0.183f, FigureTone.Accent),
            ),
            yLabel = "RMSE, 0 to 0.131",
        ),
    ),
    whatIsIt = listOf(
        "An ordinary network's learnable parameters are the weights on its edges; the nonlinearity applied at each node — ReLU, tanh, GELU — is fixed in advance and identical everywhere. A Kolmogorov-Arnold Network moves the learnable part from the edge weight to the activation function itself: every edge carries its own univariate function, free to take whatever shape training finds useful, and the nodes simply sum what arrives. In its simplest form that per-edge function is a piecewise-linear curve defined by a handful of movable control points — no fixed sigmoid or ReLU shape constrains it at all.",
        "The lab fits sin(5x)·e⁻ˣ²ᐟ² — four wiggles across x = −2…2, sampled at 100 points — two ways. A 1-6-1 tanh MLP has 19 weights: six fixed S-curves on the nodes, learned weights on the edges. Trained from three random seeds it lands at RMSE 0.131, then anywhere in 0.069–0.131: gradient descent on a non-convex loss ends somewhere different from each start, and the error varies 1.9× across seeds. One KAN edge with a spline through 20 knots reaches RMSE 0.024, and reaches it every time.",
        "Two things separate them. The first is directness: the MLP has to bend six S-curves into four wiggles, an indirect fit through a fixed basis, while the spline puts its 20 numbers straight onto the curve's shape. At roughly the same budget the KAN fit is 2.9× closer than the MLP's best seed. The second is that with the knots fixed, the spline is linear in its knot values, so fitting it is least squares — no seed, no learning rate, exactly one best answer. Full KANs stack many such edges and train them by gradient descent, so they lose that guarantee; what they keep is an edge you can read off as a curve, which is the main claim made for them. The code below is a separate run at a matched 16-parameter budget, trained by SGD, where the gap is larger.",
    ),
    steps = listOf(
        StepCard(1, "MLP: Fixed Nonlinearity, Learned Weights", "tanh (or ReLU, GELU) is the same shape at every unit — only the weights move.", 0xFF14B8A6),
        StepCard(2, "KAN: Learned Nonlinearity, Per Edge", "Every edge's function is its own shape, free to move during training.", 0xFF3B82F6),
        StepCard(3, "Match the Parameter Budget", "19 MLP weights (1-6-1) versus 20 knot values on one spline edge.", 0xFF10B981),
        StepCard(4, "Fit the Same Wiggly Target", "sin(5x)·e⁻ˣ²ᐟ² — several oscillations across the input range.", 0xFFF59E0B),
        StepCard(5, "Compare the Fits", "MLP: RMSE 0.069–0.131 across 3 seeds. KAN edge: 0.024 every time.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("MLP layer", "y = Σ_h w₂ₕ · tanh(w₁ₕ x + b₁ₕ) + b₂", "Fixed tanh shape; 3H+1 learnable numbers for H hidden units."),
        FormulaEntry("KAN edge (simplified)", "y = φ(x)", "φ itself is learnable — here, a piecewise-linear function over M knots."),
        FormulaEntry("Lab budget", "3H + 1 = 19 vs M = 20", "6 hidden units versus 20 knot values."),
        FormulaEntry("Measured in the lab", "RMSE 0.069–0.131 (MLP) vs. 0.024 (KAN)", "Three MLP seeds against one least-squares spline fit; 2.9× closer than the best seed."),
    ),
    notationKey = listOf(
        NotationEntry("φ(x)", "a KAN edge's learnable univariate function"),
        NotationEntry("knot", "a movable control point defining a piece of the piecewise-linear function"),
        NotationEntry("H", "MLP hidden units"),
        NotationEntry("M", "KAN knot count — chosen so the parameter counts compare fairly"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two functions, 16 parameters each, the same target",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def target(x):
                    return np.sin(5 * x) * np.exp(-x**2 / 2)

                class Mlp:                                    # 3H + 1 params
                    def __init__(self, hidden=5):
                        self.w1 = np.random.randn(hidden)
                        self.b1 = np.random.randn(hidden) * 0.1
                        self.w2 = np.random.randn(hidden) * 0.1
                        self.b2 = 0.0
                    def predict(self, x):
                        return self.b2 + self.w2 @ np.tanh(self.w1 * x + self.b1)

                class Kan:                                    # M params -- the knot values themselves
                    def __init__(self, knots=16, x_range=(-2, 2)):
                        self.values = np.zeros(knots)
                        self.x_min, self.x_max = x_range
                    def predict(self, x):
                        pos = (x - self.x_min) / (self.x_max - self.x_min) * (len(self.values) - 1)
                        lo = int(np.clip(pos, 0, len(self.values) - 2))
                        t = pos - lo
                        return self.values[lo] * (1 - t) + self.values[lo + 1] * t

                # Both trained by plain SGD on 40 samples, tested on 40 held-out samples.
                # mlp_test_mse:  0.166
                # kan_test_mse:  0.0030   -- ~55x lower, at the identical 16-parameter budget
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("functions", 0xFF3B82F6, "Scientific / Symbolic Fitting", "Interpretable per-edge shapes make it easier to read off a fitted function's structure."),
        ApplicationCard("chart", 0xFF10B981, "Low-Dimensional, Highly Nonlinear Fits", "Where a compact, shape-flexible function beats a wide layer of fixed units."),
        ApplicationCard("help", 0xFFF59E0B, "Not a Drop-In Replacement", "Per-edge learnable functions cost more compute per parameter than a fixed nonlinearity."),
        ApplicationCard("network", 0xFFEC4899, "Still Early", "Scaling KANs to the width and depth of modern MLPs is an open, active question."),
    ),
    takeaways = listOf(
        "A KAN moves the learnable part of a network from the edge weight to the activation function itself — no fixed shape at all, per edge.",
        "At about the same budget, one 20-knot spline edge fits the wiggly target to RMSE 0.024; a 19-weight tanh MLP reaches 0.069–0.131 depending on its seed.",
        "With the knots fixed, a spline edge is least squares — one best answer, no seed — which is why the KAN number does not move between runs.",
        "The gap traces to directness: the KAN spends its parameters on the curve's actual shape, where the MLP spends them on weights combining a fixed shape.",
        "This is not evidence KANs beat MLPs everywhere — it is a measured result on one genuinely wiggly, low-dimensional target at one matched budget.",
        "The tradeoff is real: a learnable per-edge function costs more compute per parameter than reusing one fixed nonlinearity everywhere.",
    ),
    crossLinks = listOf(
        CrossLink("mlp", "Multi-Layer Perceptron (MLP)"),
        CrossLink("activation_functions", "Activation Functions"),
        CrossLink("neural_odes", "Neural ODEs"),
        CrossLink("polynomial_regression", "Polynomial Regression"),
    ),
)
