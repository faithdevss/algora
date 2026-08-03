package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureLayer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val batchNormalizationContent = TopicContent(
    topicId = "batch_normalization",
    figure = Figure(
        caption = "Standardise, then hand the scale back. The first two steps put every pre-activation " +
            "on a well-conditioned range no matter what the layer below did; γ and β then let the " +
            "network move it wherever it actually wanted — including all the way back, if the answer " +
            "was that the normalisation was unhelpful. Note the dependency the middle steps introduce: " +
            "a training example's output now depends on which other examples shared its batch.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("pre-activations z", "one mini-batch"),
                FigureLayer("− batch mean", "centred", FigureTone.Primary),
                FigureLayer("÷ batch std", "unit variance, whatever the layer below did", FigureTone.Primary),
                FigureLayer("× γ, + β", "learned — the layer can undo all of the above", FigureTone.Accent),
                FigureLayer("activation"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Batch normalization standardizes each layer's pre-activations across the current mini-batch — subtract the batch mean, divide by the batch standard deviation — then rescales them with two learned parameters, γ and β.",
        "The normalization keeps activations in a well-conditioned range as they pass through a deep stack, which smooths the loss surface and lets you train faster with higher learning rates. γ and β exist so the layer can undo the normalization when that is genuinely what the network needs.",
    ),
    steps = listOf(
        StepCard(1, "Batch Statistics", "Compute the mean and variance of each feature over the mini-batch.", 0xFFEC4899),
        StepCard(2, "Normalize", "x̂ = (x − μ) / √(σ² + ε) — zero mean, unit variance, per feature.", 0xFF3B82F6),
        StepCard(3, "Scale and Shift", "y = γx̂ + β, both learned, so the layer keeps its full expressive range.", 0xFFF59E0B),
        StepCard(4, "Track Running Statistics", "Keep an exponential moving average of μ and σ² during training.", 0xFF10B981),
        StepCard(5, "Switch at Inference", "Evaluation uses the running statistics, so a prediction never depends on the other samples in the batch.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Normalize", "x̂ᵢ = (xᵢ − μ_B) / √(σ²_B + ε)", "Per feature, over the batch dimension."),
        FormulaEntry("Affine restore", "yᵢ = γ·x̂ᵢ + β", "γ and β are learned like any other weight."),
        FormulaEntry("Running estimate", "μ ← (1−m)·μ + m·μ_B", "Momentum m ≈ 0.1; used at inference."),
        FormulaEntry("Placement", "Linear → BatchNorm → activation", "The usual ordering; the preceding layer's bias becomes redundant."),
    ),
    notationKey = listOf(
        NotationEntry("μ_B, σ²_B", "mean and variance over the current mini-batch"),
        NotationEntry("γ, β", "learned scale and shift"),
        NotationEntry("ε", "small constant guarding against division by zero"),
        NotationEntry("m", "momentum for the running-average update"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Batch norm forward pass, both modes",
            accentColor = 0xFF6366F1,
            code = """
                class BatchNorm(features: Int, private val eps: Double = 1e-5, private val momentum: Double = 0.1) {
                    private val gamma = DoubleArray(features) { 1.0 }
                    private val beta = DoubleArray(features)
                    private val runningMean = DoubleArray(features)
                    private val runningVar = DoubleArray(features) { 1.0 }

                    // batch[sample][feature]
                    fun forward(batch: Array<DoubleArray>, training: Boolean): Array<DoubleArray> {
                        val n = batch.size
                        val f = gamma.size
                        val out = Array(n) { DoubleArray(f) }

                        for (j in 0 until f) {
                            val mean: Double
                            val variance: Double
                            if (training) {
                                mean = (0 until n).sumOf { batch[it][j] } / n
                                variance = (0 until n).sumOf { val d = batch[it][j] - mean; d * d } / n
                                // Keep an EMA so inference does not need a batch.
                                runningMean[j] = (1 - momentum) * runningMean[j] + momentum * mean
                                runningVar[j] = (1 - momentum) * runningVar[j] + momentum * variance
                            } else {
                                mean = runningMean[j]
                                variance = runningVar[j]
                            }

                            val denom = Math.sqrt(variance + eps)
                            for (i in 0 until n) out[i][j] = gamma[j] * (batch[i][j] - mean) / denom + beta[j]
                        }
                        return out
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("image", 0xFFEC4899, "Deep CNNs", "ResNet-scale vision models depend on it to train at all at depth 50+."),
        ApplicationCard("chip", 0xFF3B82F6, "Faster Convergence", "Higher learning rates become stable, cutting training time substantially."),
        ApplicationCard("network", 0xFF10B981, "Mild Regularization", "Batch noise in the statistics acts as a small regularizer, often reducing the dropout needed."),
    ),
    takeaways = listOf(
        "Normalizing per feature across the batch keeps activations well-conditioned deep into the network.",
        "γ and β preserve expressiveness — the layer can learn to undo its own normalization.",
        "Training and inference behave differently; forgetting to switch modes is a classic bug.",
        "Small batches make the statistics noisy — layer norm or group norm is the usual substitute, and transformers use layer norm throughout.",
    ),
    crossLinks = listOf(
        CrossLink("neural_network_basics", "Neural Network Basics"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("dropout", "Dropout"),
    ),
)
