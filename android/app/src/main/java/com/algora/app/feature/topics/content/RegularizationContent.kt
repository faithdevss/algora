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

internal val regularizationContent = TopicContent(
    topicId = "regularization",
    figure = Figure(
        caption = "The page's lab: a degree-9 polynomial fitted to a handful of points with an L1 " +
            "penalty, λ swept from 0 to 1. With no penalty the curve threads every point (MSE " +
            "0.0001) using weights whose absolute sum is 57.0 — huge, cancelling coefficients that " +
            "make it wiggle wildly between the data. A tiny λ = 0.0001 cuts that to 4.62 and zeroes 3 " +
            "of the 9 weights; at λ = 0.001 the wiggles are gone, 5 weights are exactly zero, and the " +
            "training error has only risen to 0.0016. At λ = 0.01 two weights carry the trend. At " +
            "λ = 1 the penalty wins outright: every weight is zero and the fit sags flat, MSE 0.0480 " +
            "— underfitting from the other side. L1 drops features rather than shrinking them all, " +
            "and the right λ is the one that does best on validation data, never on training data.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("57.0", "0 / 9", "0.0001"),
                listOf("4.62", "3 / 9", "0.0002"),
                listOf("0.59", "5 / 9", "0.0016"),
                listOf("0.41", "7 / 9", "0.0022"),
                listOf("0.00", "9 / 9", "0.0480"),
            ),
            rowHeaders = listOf("λ = 0", "λ = 0.0001", "λ = 0.001", "λ = 0.01", "λ = 1"),
            colHeaders = listOf("‖w‖₁", "weights at 0", "training MSE"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(4, 2, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Regularization adds a penalty on the size of a model's weights to the training loss, so the model can no longer fit the training data with arbitrarily large, fragile weights. L1 regularization penalises the sum of absolute weights and drives some of them exactly to zero; L2 penalises the sum of squares and shrinks all of them smoothly. λ sets how much the penalty matters relative to fitting the data.",
        "The lab fits a degree-9 polynomial to a handful of points and sweeps λ for L1. With λ = 0 the curve threads every point (MSE 0.0001) using weights whose absolute sum is 57.0 — wild wiggles between the data. A tiny λ = 0.0001 shrinks that to 4.62 and sets 3 of the 9 weights to exactly zero; at λ = 0.001 the wiggles flatten, 5 weights are zero and the MSE only rises to 0.0016; at λ = 0.01 just 2 weights survive and the fit keeps the trend. At λ = 1 every weight is zero and the fit sags towards a flat line, MSE 0.0480 — too much λ is underfitting by another route. With L2 the unpenalised weights have a norm of 29.3, and the penalty shrinks them without zeroing any.",
        "Wiggles need large weights, so they are the first thing the penalty removes, which is why regularization fights overfitting: it trades a little training error for a simpler function that generalises. λ is chosen by validation, not by the training loss, which always prefers λ = 0. In deep learning the same idea appears as weight decay, and dropout and early stopping act as regularizers too.",
    ),
    steps = listOf(
        StepCard(1, "Add a Penalty Term", "Minimize loss + λ·penalty(w) instead of loss alone.", 0xFF6366F1),
        StepCard(2, "Pick the Norm", "L2 for smooth shrinkage of correlated features; L1 when you want a sparse model.", 0xFF3B82F6),
        StepCard(3, "Tune λ by Validation", "λ = 0 recovers the unpenalized fit; λ → ∞ flattens the model to its intercept.", 0xFFF59E0B),
        StepCard(4, "Standardize First", "Penalties compare weights directly, so features must share a scale — and the bias term is never penalized.", 0xFF10B981),
        StepCard(5, "Watch the Path", "As λ grows, ridge coefficients shrink toward zero, lasso coefficients hit it and stay.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Ridge (L2)", "J = Σ(yᵢ − ŷᵢ)² + λ Σ wⱼ²", "Smooth, has a closed form, keeps all features."),
        FormulaEntry("Lasso (L1)", "J = Σ(yᵢ − ŷᵢ)² + λ Σ |wⱼ|", "Non-differentiable at 0 — which is exactly why weights land there."),
        FormulaEntry("Elastic net", "J = loss + λ(α Σ|wⱼ| + (1−α) Σ wⱼ²)", "Sparsity plus stability under correlated features."),
        FormulaEntry("Ridge gradient step", "w ← w(1 − 2ηλ) − η∇loss", "Weight decay: every step scales w down before the data pulls it back."),
    ),
    notationKey = listOf(
        NotationEntry("λ", "regularization strength — the knob you tune"),
        NotationEntry("w", "model weights, excluding the bias/intercept"),
        NotationEntry("α", "elastic-net mixing ratio between L1 and L2"),
        NotationEntry("weight decay", "equivalent to L2 for plain SGD; differs for adaptive optimizers like Adam (see AdamW)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Ridge and lasso gradient steps",
            accentColor = 0xFF6366F1,
            code = """
                enum class Penalty { NONE, L1, L2 }

                fun trainLinear(
                    x: Array<DoubleArray>,
                    y: DoubleArray,
                    penalty: Penalty,
                    lambda: Double = 0.01,
                    lr: Double = 0.05,
                    epochs: Int = 500,
                ): DoubleArray {
                    val w = DoubleArray(x[0].size)
                    var b = 0.0

                    repeat(epochs) {
                        val gradW = DoubleArray(w.size)
                        var gradB = 0.0
                        for (i in x.indices) {
                            val error = (x[i].indices.sumOf { j -> w[j] * x[i][j] } + b) - y[i]
                            for (j in w.indices) gradW[j] += error * x[i][j]
                            gradB += error
                        }

                        for (j in w.indices) {
                            // The penalty gradient: 2λw for ridge, λ·sign(w) for lasso.
                            val reg = when (penalty) {
                                Penalty.NONE -> 0.0
                                Penalty.L2 -> 2 * lambda * w[j]
                                Penalty.L1 -> lambda * Math.signum(w[j])
                            }
                            w[j] -= lr * (gradW[j] / x.size + reg)
                        }
                        b -= lr * gradB / x.size          // the intercept is never penalized
                    }
                    return w
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "High-Dimensional Regression", "With more features than rows, a penalty is what makes the problem solvable at all."),
        ApplicationCard("target", 0xFF3B82F6, "Feature Selection", "Lasso zeroes irrelevant coefficients, producing a model a human can read."),
        ApplicationCard("network", 0xFF10B981, "Training Deep Nets", "Weight decay is standard in every modern optimizer, alongside dropout and early stopping."),
    ),
    takeaways = listOf(
        "Regularization buys lower variance at the cost of some bias — it is the tradeoff made deliberate.",
        "L1 produces sparsity; L2 shrinks smoothly and handles correlated features better.",
        "Always standardize features and never penalize the intercept.",
        "λ is a hyperparameter: choose it on validation data, never on the training loss.",
        "In the lab L1 at λ = 0.001 zeroes 5 of 9 weights and shrinks their sum from 57.0 to 0.59 while MSE rises only to 0.0016; λ = 1 zeroes all of them.",
    ),
    crossLinks = listOf(
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("dropout", "Dropout"),
    ),
)
