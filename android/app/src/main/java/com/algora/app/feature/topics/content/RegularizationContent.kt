package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val regularizationContent = TopicContent(
    topicId = "regularization",
    whatIsIt = listOf(
        "Regularization adds a penalty on the size of the model's weights to the training loss, so the optimizer must justify every large coefficient with enough reduction in error.",
        "L2 (ridge) penalizes squared weights and shrinks them all smoothly toward zero. L1 (lasso) penalizes absolute values, and because its penalty has a constant pull regardless of how small a weight already is, it drives weights exactly to zero — giving feature selection for free. Elastic net mixes both.",
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
        FormulaEntry("Ridge gradient step", "w ← w(1 − ην·λ) − η∇loss", "Weight decay: every step scales w down before the data pulls it back."),
    ),
    notationKey = listOf(
        NotationEntry("λ", "regularization strength — the knob you tune"),
        NotationEntry("w", "model weights, excluding the bias/intercept"),
        NotationEntry("α", "elastic-net mixing ratio between L1 and L2"),
        NotationEntry("weight decay", "the optimizer's name for L2 regularization"),
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
    ),
    crossLinks = listOf(
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("dropout", "Dropout"),
    ),
)
