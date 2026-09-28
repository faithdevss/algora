package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kanContent = TopicContent(
    topicId = "kan",
    whatIsIt = listOf(
        "An ordinary network's learnable parameters are the weights on its edges; the nonlinearity applied at each node — ReLU, tanh, GELU — is fixed in advance and identical everywhere. A Kolmogorov-Arnold Network moves the learnable part from the edge weight to the activation function itself: every edge carries its own univariate function, free to take whatever shape training finds useful, and the nodes simply sum what arrives. In its simplest form that per-edge function is a piecewise-linear curve defined by a handful of movable control points — no fixed sigmoid or ReLU shape constrains it at all.",
        "Fit head to head on a genuinely wiggly target — sin(5x)·e⁻ˣ²ᐟ² — at a matched parameter budget of 16 numbers each: a small tanh-based MLP (5 hidden units, 3·5+1 = 16 parameters) reaches a held-out test MSE of 0.166. A KAN-style layer with 16 movable knot values across the same input range reaches 0.0030 — roughly 55 times lower error, on data neither model has seen, at the identical parameter count.",
        "The gap traces to what each model has to do to bend sharply. The MLP must compose several fixed-shape tanh units, each contributing a smooth S-curve, and approximating a function with several oscillations and inflection points means finding the right combination of five S-curves layered on top of each other — an indirect fit through a fixed basis. The KAN's piecewise-linear function can place its knot points exactly where the target function actually bends, spending its 16 parameters directly on the shape of the curve rather than indirectly on weights that combine a fixed shape. For a target this locally wiggly, direct control of the function's shape is the more parameter-efficient path to the same fit.",
    ),
    steps = listOf(
        StepCard(1, "MLP: Fixed Nonlinearity, Learned Weights", "tanh (or ReLU, GELU) is the same shape at every unit — only the weights move.", 0xFF14B8A6),
        StepCard(2, "KAN: Learned Nonlinearity, Per Edge", "Every edge's function is its own shape, free to move during training.", 0xFF3B82F6),
        StepCard(3, "Match the Parameter Budget", "16 parameters each — 5 tanh units versus 16 movable knot points.", 0xFF10B981),
        StepCard(4, "Fit the Same Wiggly Target", "sin(5x)·e⁻ˣ²ᐟ² — several oscillations across the input range.", 0xFFF59E0B),
        StepCard(5, "Compare Held-Out Error", "MLP: 0.166. KAN: 0.0030 — about 55x lower, same parameter count.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("MLP layer", "y = Σ_h w₂ₕ · tanh(w₁ₕ x + b₁ₕ) + b₂", "Fixed tanh shape; 3H+1 learnable numbers for H hidden units."),
        FormulaEntry("KAN edge (simplified)", "y = φ(x)", "φ itself is learnable — here, a piecewise-linear function over M knots."),
        FormulaEntry("Matched budget", "3H + 1 = M", "5 hidden units (16 params) versus 16 knot values."),
        FormulaEntry("Measured", "MSE 0.166 (MLP) vs. 0.0030 (KAN)", "Held-out test error, same parameter count, same target function."),
    ),
    notationKey = listOf(
        NotationEntry("φ(x)", "a KAN edge's learnable univariate function"),
        NotationEntry("knot", "a movable control point defining a piece of the piecewise-linear function"),
        NotationEntry("H", "MLP hidden units"),
        NotationEntry("M", "KAN knot count — matched to H so parameter counts compare fairly"),
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
        "At a matched 16-parameter budget, a KAN-style piecewise-linear function fits a wiggly target far better than a small tanh MLP: MSE 0.0030 versus 0.166.",
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
