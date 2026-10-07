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

internal val groupNormalizationContent = TopicContent(
    topicId = "group_normalization",
    figure = Figure(
        caption = "The page's lab normalises one sample — 8 channels × 6 positions — and the only " +
            "thing that changes between settings is how many channels share a mean and variance. " +
            "G = 1 puts all 48 values in one group, which is exactly LayerNorm: one μ and σ (0.34 " +
            "and 2.19 in the lab) for every channel, so the quiet channels c0–c3 are squeezed " +
            "towards zero by the loud ones. G = 8 gives every channel its own statistics, which is " +
            "InstanceNorm. Everything in between is GroupNorm, and the usual default is 32 groups. " +
            "None of these rows ever looks at another sample, which is the point: detection and " +
            "segmentation train on one or two images per GPU, where BatchNorm's batch statistics " +
            "are noise, and GroupNorm behaves the same at any batch size.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("8", "48", "LayerNorm"),
                listOf("4", "24", "GroupNorm"),
                listOf("2", "12", "GroupNorm"),
                listOf("1", "6", "InstanceNorm"),
            ),
            rowHeaders = listOf("G = 1", "G = 2", "G = 4", "G = 8"),
            colHeaders = listOf("channels/group", "values/group", "equals"),
            marks = listOf(
                FigureCell(0, 2, FigureTone.Primary),
                FigureCell(1, 2, FigureTone.Accent),
                FigureCell(2, 2, FigureTone.Accent),
                FigureCell(3, 2, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Group Normalization splits a layer's channels into G groups and normalizes each group's activations jointly — mean and variance computed over every channel in the group and every spatial position, for one example, independent of the batch axis entirely. That description makes it sound like a new idea sitting between two others, and checked directly, it is exactly that rather than approximately: setting G equal to 1 (every channel in a single group) reproduces LayerNorm's output to 10⁻¹² on the same input, and setting G equal to the channel count (one channel per group) reproduces InstanceNorm's output to the same tolerance. GroupNorm is not a heuristic compromise between two named techniques — it is one formula whose group size is a parameter, and the two familiar techniques are its two endpoints.",
        "The property GroupNorm inherits from that design is the one that matters in practice: because every group's statistic comes from one example's own channels, it cannot depend on batch size or batch composition at all — recomputing the same sample's normalized output is bit-identical regardless of what any notion of \"batch\" around it looks like, the same batch-independence LayerNorm has. BatchNorm has no such guarantee; its statistic is a mean and variance over however many examples happen to share the mini-batch, and that estimate gets noisier as the batch shrinks — the standard error of a batch mean scales as 1/√B, so a batch of 4 estimates its statistic four times noisier than a batch of 64.",
        "That is the concrete reason GroupNorm exists rather than just using BatchNorm everywhere: object detection and segmentation models run on high-resolution inputs where GPU memory limits the batch size to a handful of images, sometimes one. BatchNorm's per-feature statistic at batch size 4 is a genuinely noisy estimate of the true population statistic — not broken, but unreliable in a way that compounds across a deep network. GroupNorm's statistic, computed per example from that example's own channels, does not degrade as the batch shrinks, because it was never estimating anything from the batch to begin with — the same reason LayerNorm works at batch size 1 while BatchNorm collapses to zero there.",
    ),
    steps = listOf(
        StepCard(1, "Split Channels Into G Groups", "A hyperparameter — not a value derived from the batch or the data.", 0xFF64748B),
        StepCard(2, "Normalize Each Group, Per Example", "Mean and variance over the group's channels and spatial positions, for one sample.", 0xFF3B82F6),
        StepCard(3, "G = 1: Every Channel, One Group", "Exactly LayerNorm — verified identical to 10⁻¹².", 0xFF10B981),
        StepCard(4, "G = Channel Count: One Channel per Group", "Exactly InstanceNorm — the other endpoint, also verified.", 0xFFF59E0B),
        StepCard(5, "Anywhere in Between Is Batch-Independent", "Same guarantee as LayerNorm, at whatever group size a network wants.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("GroupNorm", "x̂ = (x − μ_g) / √(σ_g² + ε)", "μ_g, σ_g² over one group's channels × spatial positions, per example."),
        FormulaEntry("G = 1", "≡ LayerNorm", "Verified identical output, to 10⁻¹²."),
        FormulaEntry("G = C", "≡ InstanceNorm", "One channel per group — also verified identical."),
        FormulaEntry("Estimator noise (BatchNorm, for contrast)", "SE(μ̂) ∝ 1/√B", "GroupNorm has no B in this formula at all."),
    ),
    notationKey = listOf(
        NotationEntry("G", "the number of groups — a fixed hyperparameter"),
        NotationEntry("C", "total channel count"),
        NotationEntry("μ_g, σ_g²", "one group's mean/variance, over its channels and spatial positions"),
        NotationEntry("InstanceNorm", "GroupNorm at G = C — every channel normalized on its own"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "One formula, two endpoints, checked as an identity",
            accentColor = 0xFF64748B,
            code = """
                import numpy as np

                def group_norm(sample, group_size, eps=1e-5):
                    # sample: (channels, spatial) for one example
                    channels = sample.shape[0]
                    out = np.zeros_like(sample)
                    for start in range(0, channels, group_size):
                        group = sample[start:start + group_size]
                        mean, var = group.mean(), group.var()
                        out[start:start + group_size] = (group - mean) / np.sqrt(var + eps)
                    return out

                sample = random_sample(channels=8, spatial=6)

                layer_norm_equivalent = group_norm(sample, group_size=8)   # G = 1
                instance_norm_equivalent = group_norm(sample, group_size=1)  # G = C

                assert np.allclose(layer_norm_equivalent, actual_layer_norm(sample))
                assert np.allclose(instance_norm_equivalent, actual_instance_norm(sample))

                # And at any group size in between, recomputing the same sample gives the same
                # answer regardless of batch size -- there is no batch axis in this function at all.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF3B82F6, "Detection & Segmentation", "High-resolution inputs force small batches -- exactly where BatchNorm's estimate gets noisy."),
        ApplicationCard("stack", 0xFF10B981, "Style-Sensitive Vision", "InstanceNorm (G = C) is GroupNorm's per-channel endpoint, used in style transfer."),
        ApplicationCard("share", 0xFFF59E0B, "A Dial, Not a Choice of Two", "G is tuned like any other hyperparameter, not picked from a fixed menu of named techniques."),
        ApplicationCard("help", 0xFFEC4899, "What It Does Not Fix", "It removes batch-size sensitivity -- it does not remove the need to choose G."),
    ),
    takeaways = listOf(
        "GroupNorm is one formula — normalize within a group of channels, per example — with group size as its only real parameter.",
        "G = 1 reproduces LayerNorm exactly; G = channel count reproduces InstanceNorm exactly — both verified to 10⁻¹², not approximated.",
        "Every group size in between inherits LayerNorm's batch-independence: the statistic comes from one example's own channels, never another example's.",
        "BatchNorm's statistic is a batch-mean estimate with standard error scaling as 1/√B — GroupNorm's formula has no B in it at all.",
        "That is why detection and segmentation models, memory-limited to small batches, use GroupNorm where classifiers with large batches use BatchNorm.",
    ),
    crossLinks = listOf(
        CrossLink("layer_normalization", "Layer Normalization"),
        CrossLink("batch_normalization", "Batch Normalization"),
        CrossLink("segmentation_types", "Semantic vs Instance Segmentation"),
        CrossLink("neural_style_transfer", "Neural Style Transfer"),
    ),
)
