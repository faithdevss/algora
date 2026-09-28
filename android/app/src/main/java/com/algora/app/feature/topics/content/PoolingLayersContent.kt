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

internal val poolingLayersContent = TopicContent(
    topicId = "pooling_layers",
    figure = Figure(
        caption = "2×2 max pooling over a 4×4 map: four blocks, four survivors — 6, 4, 7, 9 — and " +
            "twelve values gone. What leaves with them is *where inside the block* the winner sat, " +
            "which is the whole mechanism: the output says the feature was present in this " +
            "quadrant, not where. That buys less than the usual claim. Shifting a bright square by " +
            "one pixel changes the raw map by 100% of its own magnitude and the pooled map by 50%; " +
            "at a two-pixel shift it is 175% against 100%. Pooling halves sensitivity to a small " +
            "shift, it does not remove it, and the advantage is gone once the shift exceeds the " +
            "window. Average pooling would report how much of the feature was there instead — which " +
            "is why global average pooling, not max, is what replaced the fully-connected head.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("1", "3", "2", "4"),
                listOf("5", "6", "1", "2"),
                listOf("7", "2", "9", "1"),
                listOf("3", "4", "5", "8"),
            ),
            marks = listOf(
                FigureCell(1, 1, FigureTone.Accent),
                FigureCell(0, 3, FigureTone.Accent),
                FigureCell(2, 0, FigureTone.Accent),
                FigureCell(2, 2, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Pooling summarises a small neighbourhood of a feature map with a single number — the maximum, or the average — and slides that window with a stride equal to its size, so the map shrinks. It has no parameters and nothing to learn. Its job is to reduce how much spatial detail the next layer carries, which cuts memory and compute and, at the same time, grows the receptive field faster than convolution alone.",
        "Max and average answer different questions. Max pooling keeps the strongest response in the block and discards *where inside the block* it occurred, which reads as \"was this feature present here?\". Average pooling reports how much of the feature was present, diluting a single strong response among its neighbours. Max won for detection-style features and average survives in one specific and important place: global average pooling over the final map, which replaced the fully-connected head and deleted the majority of a network's parameters.",
        "The usual claim — that pooling gives translation invariance — is worth measuring rather than repeating, and the simulation does. Shift a small bright square one pixel and the raw feature map changes by 100% of its own magnitude; the 2×2 max-pooled map changes by 50%. Shift by two pixels and the pooled map changes by 100% against the raw map's 175%. Pooling *halves* the sensitivity to a small shift; it does not remove it, and the advantage erodes once the shift exceeds the window. Invariance in a real network comes from stacking several of these, not from one large window — and many architectures after ResNet dropped pooling entirely in favour of stride-2 convolutions, which downsample and filter in the same learned step.",
    ),
    steps = listOf(
        StepCard(1, "Take a Feature Map", "Pooling never sees the image — only what a conv layer already found.", 0xFF10B981),
        StepCard(2, "Tile It With Windows", "Usually 2×2 at stride 2, so windows do not overlap.", 0xFF06B6D4),
        StepCard(3, "Reduce Each Window", "Max keeps the strongest response; average keeps the amount.", 0xFF6366F1),
        StepCard(4, "Emit the Smaller Map", "Half the height and width — a quarter of the activations, zero parameters.", 0xFF8B5CF6),
        StepCard(5, "Backprop Through It", "Max routes the gradient to the argmax only; average splits it evenly.", 0xFFF59E0B),
        StepCard(6, "Or Skip It Entirely", "A stride-2 convolution downsamples too, and learns how.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Max pooling", "y[i,j] = max over the k×k window of x", "Ranked selection: no parameters, no arithmetic beyond comparison."),
        FormulaEntry("Average pooling", "y[i,j] = (1/k²) Σ over the k×k window", "A fixed uniform filter applied with stride k."),
        FormulaEntry("Output size", "⌊(n − k)/s⌋ + 1", "Same geometry as a convolution; pooling is usually unpadded with s = k."),
        FormulaEntry("Max pooling gradient", "∂y/∂x = 1 at the argmax, 0 elsewhere", "Only the winning unit is updated — a sparse, noisy route."),
        FormulaEntry("Average pooling gradient", "∂y/∂x = 1/k² everywhere in the window", "Every unit gets an equal share."),
        FormulaEntry("Global average pooling", "one value per channel, over the whole map", "Replaces the flatten + dense head; the origin of most parameter savings after VGG."),
    ),
    notationKey = listOf(
        NotationEntry("subsampling", "LeNet's name for the same operation, with a learned scale and bias"),
        NotationEntry("stride = window", "the default: non-overlapping windows, so the map halves"),
        NotationEntry("global average pooling", "pool the entire map to 1×1 per channel; GoogLeNet's head"),
        NotationEntry("translation tolerance", "what pooling actually buys — a reduction, not an invariance"),
        NotationEntry("strided convolution", "the learned alternative that replaced pooling in most modern stacks"),
        NotationEntry("adaptive pooling", "framework op that picks the window to hit a requested output size"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Measuring the invariance claim instead of repeating it",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from scipy.signal import correlate2d

                img = np.zeros((9, 9)); img[3:6, 3:6] = 1          # a small bright square
                k = np.array([[1, 0, -1], [2, 0, -2], [1, 0, -1]])

                def maxpool(m, s=2):
                    n = m.shape[0] // s * s
                    return m[:n, :n].reshape(n//s, s, n//s, s).max(axis=(1, 3))

                def moved(shift):
                    a = correlate2d(img, k, mode='valid')
                    b = correlate2d(np.roll(img, shift, axis=1), k, mode='valid')
                    raw    = np.abs(a - b).sum() / np.abs(a).sum()
                    pooled = np.abs(maxpool(a) - maxpool(b)).sum() / np.abs(maxpool(a)).sum()
                    return raw, pooled

                print(moved(1))    # (1.00, 0.50)  -- pooling halves the change
                print(moved(2))    # (1.75, 1.00)  -- and the advantage shrinks
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The head swap that deleted most of a network's parameters",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch.nn as nn

                # VGG-style head: flatten a 7x7x512 map, then dense.
                classic = nn.Sequential(nn.Flatten(), nn.Linear(7*7*512, 4096))
                print(sum(p.numel() for p in classic.parameters()))    # 102,764,544

                # Global-average-pool head: one number per channel, then dense.
                modern = nn.Sequential(nn.AdaptiveAvgPool2d(1), nn.Flatten(), nn.Linear(512, 1000))
                print(sum(p.numel() for p in modern.parameters()))     # 513,000

                # Same input map, 200x fewer parameters, and it accepts any input resolution because
                # the pool always emits 512 values. GoogLeNet made this the default in 2014 and
                # essentially every architecture since has kept it.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Cutting Activation Memory", "A 2×2 pool drops the activations carried forward by 4×, which is often what makes a deep stack fit in memory at all."),
        ApplicationCard("target", 0xFF06B6D4, "Classification Heads", "Global average pooling turns any map size into a fixed feature vector, so one model handles multiple input resolutions."),
        ApplicationCard("search", 0xFF8B5CF6, "Detection Backbones", "RoI pooling in Fast R-CNN is this operation applied to an arbitrary box, to make region features a fixed size."),
        ApplicationCard("music", 0xFFF59E0B, "Audio Models", "Pooling over time gives a clip-level prediction that does not depend on exactly when the sound occurred."),
    ),
    takeaways = listOf(
        "Pooling has zero parameters: it is a fixed reduction, not a learned layer.",
        "Max asks whether a feature was present; average asks how much of it was — and global average pooling replaced the dense head.",
        "Measured, 2×2 max pooling halves the response change under a one-pixel shift rather than eliminating it.",
        "The max gradient goes only to the winner, which makes pooling a sparse and slightly noisy route backward.",
        "Stride-2 convolutions do the same downsampling with learned weights, and have replaced pooling in most modern architectures.",
    ),
    crossLinks = listOf(
        CrossLink("conv_layers", "Convolution Layers"),
        CrossLink("padding_strides", "Padding & Strides"),
        CrossLink("lenet5", "LeNet-5"),
        CrossLink("inception", "Inception"),
    ),
)
