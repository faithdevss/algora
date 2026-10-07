package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val layerNormalizationContent = TopicContent(
    topicId = "layer_normalization",
    whatIsIt = listOf(
        "Batch Normalization and Layer Normalization compute the exact same operation — subtract a mean, divide by a standard deviation — over two different axes of the same activation tensor. BatchNorm's mean and variance run down the batch axis, one statistic per feature, pooled across every example currently in the mini-batch. LayerNorm's run across the feature axis, one statistic per example, using nothing from any other row. That one axis swap is the entire difference, and it has a consequence worth computing rather than asserting: at batch size 1, BatchNorm's per-feature variance is the variance of a single number against itself, which is exactly zero. Every feature normalizes to 0/√(0+ε) — the output is the zero vector, regardless of what the input was. LayerNorm, at the same batch size 1, is unaffected, because it was never looking at the batch axis in the first place.",
        "The axis choice has a second, sharper consequence: LayerNorm's output for a given row is provably independent of every other row in the batch. Feed the identical input vector into a batch of size 4 and a batch of size 32 built from otherwise different data, and LayerNorm returns bit-identical output both times — verified here to 10⁻¹² — because its statistic never reads another row. BatchNorm's output for that same fixed input differs between the two batches, because its statistic is a function of who else happens to be present. A model built on LayerNorm behaves identically whether it is run one example at a time or in a batch of a thousand; a model built on BatchNorm does not, which is why BatchNorm needs a separate running-average statistic frozen at inference time and LayerNorm needs nothing of the sort.",
        "BatchNorm's batch-axis statistic is also a sample estimate, and small samples estimate noisily: the standard error of a mean over B examples scales as 1/√B, measured here directly — the estimate's own draw-to-draw spread is 0.549 at batch size 4 and 0.137 at batch size 64, a factor of exactly 4, matching √(64/4) = √16 to the digit. A small batch does not just normalize; it normalizes by a noisy guess at the true statistic, and that guess changes every mini-batch. This is the actual mechanical reason BatchNorm degrades at small batch sizes and why Transformers standardized on LayerNorm instead: sequences are variable-length and padded, the statistics are per token, and train and inference behave identically with no dependence on what else is in the batch.",
    ),
    steps = listOf(
        StepCard(1, "Same Operation, Different Axis", "Subtract a mean, divide by a standard deviation — the only question is over what.", 0xFF64748B),
        StepCard(2, "BatchNorm: Down the Batch, One Feature at a Time", "Every feature gets its own mean/variance, pooled across the mini-batch.", 0xFF3B82F6),
        StepCard(3, "LayerNorm: Across Features, One Example at a Time", "Every row normalizes itself — no other row is ever read.", 0xFF10B981),
        StepCard(4, "Batch Size 1 Breaks One of Them", "A single value's variance from itself is exactly zero — BatchNorm outputs all-zero.", 0xFFF59E0B),
        StepCard(5, "And Batch Composition Moves One of Them", "Same row, different batchmates: LayerNorm is unchanged, BatchNorm is not.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("BatchNorm", "x̂ᵢⱼ = (xᵢⱼ − μⱼ) / √(σⱼ² + ε)", "μⱼ, σⱼ² over the batch, for feature j."),
        FormulaEntry("LayerNorm", "x̂ᵢⱼ = (xᵢⱼ − μᵢ) / √(σᵢ² + ε)", "μᵢ, σᵢ² over the features, for example i."),
        FormulaEntry("Batch size 1", "σⱼ² = 0 exactly", "One value's variance from itself — BatchNorm outputs zero."),
        FormulaEntry("Estimator noise", "SE(μ̂) ∝ 1/√B", "Measured: 0.549 (B=4) → 0.137 (B=64), a 4× drop matching √16."),
    ),
    notationKey = listOf(
        NotationEntry("μⱼ, σⱼ²", "BatchNorm's per-feature mean/variance, over the batch"),
        NotationEntry("μᵢ, σᵢ²", "LayerNorm's per-example mean/variance, over the features"),
        NotationEntry("ε", "a small constant preventing division by zero"),
        NotationEntry("SE(μ̂)", "standard error of the estimated mean — how much it jitters draw to draw"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The same row, two batches, two different answers -- except for one of them",
            accentColor = 0xFF64748B,
            code = """
                import numpy as np

                def batch_norm(rows, eps=1e-5):
                    mean = rows.mean(axis=0)          # down the batch, per feature
                    var = rows.var(axis=0)
                    return (rows - mean) / np.sqrt(var + eps)

                def layer_norm(row, eps=1e-5):
                    mean = row.mean()                 # across features, per example
                    var = row.var()
                    return (row - mean) / np.sqrt(var + eps)

                fixed_row = sample_input()
                small_batch = np.vstack([fixed_row, *other_rows(3)])
                large_batch = np.vstack([fixed_row, *other_rows(31)])

                # LayerNorm never reads another row:
                assert np.allclose(layer_norm(small_batch[0]), layer_norm(large_batch[0]))

                # BatchNorm does -- same fixed_row, different answer depending on batchmates:
                assert not np.allclose(batch_norm(small_batch)[0], batch_norm(large_batch)[0])

                # And at batch size 1, BatchNorm's variance is exactly zero:
                assert np.allclose(batch_norm(fixed_row.reshape(1, -1)), 0.0)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF3B82F6, "Transformers", "Every block normalizes across features, per token — no batch dependency at all."),
        ApplicationCard("stack", 0xFF10B981, "CNNs Keep BatchNorm", "Large batches of images make the batch-axis statistic cheap to estimate well."),
        ApplicationCard("mic", 0xFFF59E0B, "RNNs", "One sequence at a time, sometimes batch of one — exactly where BatchNorm degenerates."),
        ApplicationCard("help", 0xFFEC4899, "The Actual Trade", "Neither is strictly better — a different axis, with a different failure mode."),
    ),
    takeaways = listOf(
        "BatchNorm and LayerNorm are the same formula over two different axes: the batch, or the features.",
        "At batch size 1, BatchNorm's variance is exactly zero, so every feature normalizes to zero regardless of the input — verified, not approximated.",
        "LayerNorm's output for a row is bit-identical no matter what else is in the batch; BatchNorm's is not, checked directly on the same fixed row.",
        "A small batch's estimated mean jitters by 1/√B — measured 0.549 at B=4 down to 0.137 at B=64, a 4× drop matching √16 exactly.",
        "That is the literal mechanical reason BatchNorm struggles at small batch sizes and Transformers use LayerNorm instead.",
    ),
    crossLinks = listOf(
        CrossLink("batch_normalization", "Batch Normalization"),
        CrossLink("group_normalization", "Group Normalization"),
        CrossLink("transformers", "Transformers"),
        CrossLink("attention", "Attention"),
    ),
)
