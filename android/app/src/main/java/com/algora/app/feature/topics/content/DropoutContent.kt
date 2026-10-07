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

internal val dropoutContent = TopicContent(
    topicId = "dropout",
    figure = Figure(
        caption = "A different subnetwork on every training step, sampled from an exponentially large " +
            "family — and inference averages over all of them for free. The scaling step is what makes " +
            "that free: divide by the keep probability during training and the expected activation is " +
            "unchanged, so the test-time network is the plain one with no mask and no correction. What " +
            "it buys is that no feature can depend on a specific partner being present.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("layer output", "n activations"),
                FigureLayer("mask ~ Bernoulli(1 − p)", "resampled every step", FigureTone.Primary),
                FigureLayer("zero the dropped units", "co-adaptation has nothing to lean on", FigureTone.Warn),
                FigureLayer("÷ (1 − p)", "so inference needs no change at all", FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Dropout randomly zeroes a fraction p of a layer's units on every training step, so the network can never rely on any single neuron being present.",
        "The effect is co-adaptation breaking: features must be individually useful rather than useful only in combination with a specific partner. Equivalently, training samples from an exponentially large set (2ⁿ) of thinned sub-networks, and inference averages over them — a very cheap ensemble.",
    ),
    steps = listOf(
        StepCard(1, "Sample a Mask", "Each unit is kept with probability 1 − p, independently, on every forward pass.", 0xFFEC4899),
        StepCard(2, "Zero the Dropped Units", "Multiply activations by the mask; the same mask is reused in the backward pass.", 0xFF3B82F6),
        StepCard(3, "Rescale the Survivors", "Divide by (1 − p) so the expected sum reaching the next layer is unchanged — inverted dropout.", 0xFFF59E0B),
        StepCard(4, "Disable at Inference", "Evaluation uses the full network with no mask and no rescaling.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Mask", "mᵢ ~ Bernoulli(1 − p)", "One independent draw per unit per step."),
        FormulaEntry("Inverted dropout", "y = (m ⊙ x) / (1 − p)", "Rescale during training so inference needs no change."),
        FormulaEntry("Expected activation", "E[y] = x", "Which is exactly why the division is there."),
        FormulaEntry("Typical rates", "p ≈ 0.5 dense, 0.1–0.3 conv/embeddings", "Convolutional layers already share weights, so they need less."),
    ),
    notationKey = listOf(
        NotationEntry("p", "drop probability"),
        NotationEntry("m", "binary mask sampled per step"),
        NotationEntry("⊙", "element-wise product"),
        NotationEntry("co-adaptation", "units that only work as a fixed group — what dropout prevents"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Inverted dropout",
            accentColor = 0xFF6366F1,
            code = """
                class Dropout(private val p: Double = 0.5) {
                    private var mask: DoubleArray? = null

                    fun forward(x: DoubleArray, training: Boolean): DoubleArray {
                        if (!training || p <= 0.0) return x        // full network at inference

                        val keep = 1.0 - p
                        // Scale by 1/keep now so the expected value matches the un-dropped layer.
                        val m = DoubleArray(x.size) { if (Math.random() < keep) 1.0 / keep else 0.0 }
                        mask = m
                        return DoubleArray(x.size) { i -> x[i] * m[i] }
                    }

                    // The backward pass must reuse the SAME mask that was sampled going forward.
                    fun backward(gradOut: DoubleArray): DoubleArray {
                        val m = mask ?: return gradOut
                        return DoubleArray(gradOut.size) { i -> gradOut[i] * m[i] }
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("network", 0xFFEC4899, "Fully Connected Heads", "Dense classifier heads over a frozen backbone are where dropout still earns its keep."),
        ApplicationCard("robot", 0xFF3B82F6, "Transformers", "Applied to attention weights and feed-forward blocks throughout modern language models."),
        ApplicationCard("chart", 0xFF10B981, "Uncertainty Estimates", "Keeping dropout on at inference (MC dropout) samples predictions and gives an error bar."),
    ),
    takeaways = listOf(
        "Dropout prevents co-adaptation by making every unit unreliable during training.",
        "Inverted dropout rescales during training so inference is a plain forward pass.",
        "It must be off at evaluation — leaving it on silently degrades your metrics.",
        "It fights variance, not bias: an underfitting model gets worse, not better, with more dropout.",
    ),
    crossLinks = listOf(
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("batch_normalization", "Batch Normalization"),
        CrossLink("neural_network_basics", "Neural Network Basics"),
    ),
)
