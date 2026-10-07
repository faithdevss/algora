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

internal val convLayersContent = TopicContent(
    topicId = "conv_layers",
    figure = Figure(
        caption = "The same nine weights, at two of the twenty-five positions they visit. On the " +
            "flat patch the dot product is nothing; straddling the edge it fires — the layer " +
            "detects its feature wherever the feature is, which is translation equivariance and " +
            "not something anyone coded. The saving is the other half: 10 parameters for this " +
            "kernel against 1,250 for a dense layer producing the same 25 outputs, 125× more, each " +
            "one bolted to a single pixel position. Each output still sees only 3×3, so context is " +
            "bought by stacking rather than by widening — three of these layers reach 7×7 of the " +
            "original image (3 → 5 → 7) while parameters grow with k².",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "0", "0", "0", "9", "9", "9"),
                listOf("0", "0", "0", "0", "9", "9", "9"),
                listOf("0", "0", "0", "0", "9", "9", "9"),
                listOf("0", "0", "0", "0", "9", "9", "9"),
                listOf("0", "0", "0", "0", "9", "9", "9"),
                listOf("0", "0", "0", "0", "9", "9", "9"),
                listOf("0", "0", "0", "0", "9", "9", "9"),
            ),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Primary), FigureCell(0, 1, FigureTone.Primary), FigureCell(0, 2, FigureTone.Primary),
                FigureCell(1, 0, FigureTone.Primary), FigureCell(1, 1, FigureTone.Primary), FigureCell(1, 2, FigureTone.Primary),
                FigureCell(2, 0, FigureTone.Primary), FigureCell(2, 1, FigureTone.Primary), FigureCell(2, 2, FigureTone.Primary),
                FigureCell(4, 2, FigureTone.Accent), FigureCell(4, 3, FigureTone.Accent), FigureCell(4, 4, FigureTone.Accent),
                FigureCell(5, 2, FigureTone.Accent), FigureCell(5, 3, FigureTone.Accent), FigureCell(5, 4, FigureTone.Accent),
                FigureCell(6, 2, FigureTone.Accent), FigureCell(6, 3, FigureTone.Accent), FigureCell(6, 4, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A convolution layer is a small window of weights slid over the whole input, computing one dot product per position. That single design decision replaces two assumptions a dense layer makes and that images do not satisfy: that every input position deserves its own weight, and that the meaning of a pixel depends on where it is. A cat in the top-left corner and the same cat in the bottom-right should produce the same features, and a dense layer has no way to know that without seeing both.",
        "The saving is stark and easy to measure. In the simulation a 3×3 kernel over a 7×7 patch produces a 5×5 feature map with 10 parameters; a dense layer producing those same 25 outputs holds 1,250 — 125× more — and every one of them is bound to one pixel position. The kernel is not just smaller, it is *reused*: the same nine weights are applied at all 25 positions, which is why the layer detects its feature wherever the feature happens to be. That property is translation **equivariance** — shift the input, and the feature map shifts with it.",
        "The other half of the design is depth. A single convolution is aggressively local: each output sees only k² inputs. Stack three 3×3 layers and each output's receptive field is 7×7 of the original image, while every layer still touches nine values of the layer below — the simulation reports the growth as 3 → 5 → 7. Modern networks buy global context by stacking cheap local operations rather than by using a large kernel, because depth grows the receptive field additively while parameters grow with k².",
    ),
    steps = listOf(
        StepCard(1, "Place the Window", "Line the kernel up over the top-left corner of the input.", 0xFF10B981),
        StepCard(2, "Multiply and Sum", "One dot product over the k² overlapping values, plus a bias.", 0xFF06B6D4),
        StepCard(3, "Slide and Repeat", "Move by the stride and do it again — same weights, new position.", 0xFF6366F1),
        StepCard(4, "One Kernel, One Map", "The result is a feature map: where in the input this pattern occurred.", 0xFF8B5CF6),
        StepCard(5, "Stack Kernels for Channels", "C filters produce C maps; each output channel reads every input channel.", 0xFFF59E0B),
        StepCard(6, "Stack Layers for Reach", "Receptive field grows 3 → 5 → 7 while each layer stays local.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Discrete 2-D convolution", "y[i,j] = Σₘ Σₙ x[i+m, j+n]·w[m,n] + b", "Frameworks compute this cross-correlation; the flip is absorbed into the learned kernel."),
        FormulaEntry("Output size", "⌊(n + 2p − k)/s⌋ + 1", "Per spatial axis. See Padding & Strides."),
        FormulaEntry("Parameters", "C_out · (C_in · k² + 1)", "Independent of the input's spatial size — the whole point."),
        FormulaEntry("Multiply-accumulates", "H_out · W_out · C_out · C_in · k²", "Compute *does* scale with spatial size, so cost and parameter count diverge."),
        FormulaEntry("Receptive field", "rᵢ = rᵢ₋₁ + (kᵢ − 1)·∏ⱼ<ᵢ sⱼ", "Additive in depth, so stacking beats widening."),
        FormulaEntry("Equivariance", "f(shift(x)) = shift(f(x))", "True of convolution; invariance is a separate thing that pooling approximates."),
    ),
    notationKey = listOf(
        NotationEntry("kernel / filter", "the k×k×C_in block of weights being slid"),
        NotationEntry("feature map", "one output channel — where in the input this filter responded"),
        NotationEntry("weight sharing", "the same kernel applied at every position, and why the count is small"),
        NotationEntry("receptive field", "how much of the original input one output value can see"),
        NotationEntry("1×1 convolution", "no spatial extent: a learned linear map across channels"),
        NotationEntry("channel", "one feature map in a stack; the depth dimension of a layer's output"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The layer from scratch, then the parameter count that motivates it",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def conv2d(x, w, stride=1, pad=0):
                    x = np.pad(x, pad)
                    k = w.shape[0]
                    out = (x.shape[0] - k) // stride + 1
                    y = np.zeros((out, out))
                    for i in range(out):
                        for j in range(out):
                            window = x[i*stride:i*stride+k, j*stride:j*stride+k]
                            y[i, j] = (window * w).sum()
                    return y

                img = np.zeros((7, 7)); img[3, :] = 1; img[:, 4:] = 1
                sobel_x = np.array([[1, 0, -1], [2, 0, -2], [1, 0, -1]])
                print(conv2d(img, sobel_x).shape)      # (5, 5)

                # 3x3 kernel + bias = 10 parameters, reused 25 times.
                # A dense layer producing the same 25 outputs: 25 * (49 + 1) = 1250.
                print(1250 / 10)                       # 125.0
            """.trimIndent(),
        ),
        CodeBlock(
            title = "In PyTorch, and what the shapes actually mean",
            accentColor = 0xFF6366F1,
            code = """
                import torch, torch.nn as nn

                layer = nn.Conv2d(in_channels=3, out_channels=64, kernel_size=3, padding=1)
                print(layer.weight.shape)              # [64, 3, 3, 3] -- one 3x3x3 filter per output channel
                print(sum(p.numel() for p in layer.parameters()))   # 1792 = 64 * (3*3*3 + 1)

                x = torch.randn(8, 3, 224, 224)        # batch, channels, height, width
                print(layer(x).shape)                  # [8, 64, 224, 224]

                # The parameter count did not depend on 224 at all -- feed it 512x512 and the same
                # 1,792 weights apply. What does scale is the arithmetic:
                #   224*224*64*3*9 = 8.67e7 multiply-accumulates for this one layer.
                # Parameters are a memory question; MACs are the latency question. Convolutional
                # networks are the architecture where those two answers stop agreeing.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Image Classification", "Every vision backbone from LeNet-5 to ConvNeXt is a stack of these, with the early layers reliably learning edge detectors."),
        ApplicationCard("flask", 0xFF06B6D4, "Medical Imaging", "Segmentation networks convolve volumetric scans, where weight sharing is what makes 3-D inputs tractable at all."),
        ApplicationCard("music", 0xFF8B5CF6, "Audio and Time Series", "1-D convolutions over waveforms and sensor streams: the same locality argument, one axis fewer."),
        ApplicationCard("network", 0xFFF59E0B, "Text Classification", "Convolutions over embedded tokens catch n-gram patterns wherever in the sentence they occur."),
    ),
    takeaways = listOf(
        "A convolution is one small kernel applied at every position — weight sharing is the entire parameter saving.",
        "Parameters depend on kernel size and channels only, never on the input's height and width; compute depends on both.",
        "Convolution is equivariant to translation, not invariant: shift the input and the feature map shifts with it.",
        "Receptive field grows additively with depth, which is why stacks of 3×3 beat a single large kernel.",
        "A 1×1 convolution has no spatial extent at all and is a learned linear map across channels — the basis of every bottleneck.",
    ),
    crossLinks = listOf(
        CrossLink("padding_strides", "Padding & Strides"),
        CrossLink("pooling_layers", "Pooling Layers"),
        CrossLink("cnn", "CNNs"),
        CrossLink("neural_network_basics", "Feedforward Networks"),
    ),
)
