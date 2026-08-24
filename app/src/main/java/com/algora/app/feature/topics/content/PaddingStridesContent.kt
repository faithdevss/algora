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

internal val paddingStridesContent = TopicContent(
    topicId = "padding_strides",
    figure = Figure(
        caption = "The formula drawn as what it does to the pixels. A 7×7 input, a 3×3 kernel, " +
            "stride 1, no padding: ⌊(7 + 0 − 3)/1⌋ + 1 = 5, so 25 windows land on the image and " +
            "each cell here counts how many of them read that pixel. The interior is read nine " +
            "times, the corners once — a 9-to-1 asymmetry fixed before a single weight exists, " +
            "and the reason the border of an unpadded feature map is systematically " +
            "under-represented. Same padding, p = (k − 1)/2 = 1, is that formula solved for p: " +
            "every count becomes 9 and the output stays 7×7. The shrinkage is the other half — " +
            "k − 1 = 2 pixels per layer, survivable once and not at depth, which takes a 224×224 " +
            "image to 204×204 over ten unpadded 3×3 layers and is why VGG pads all thirteen of its " +
            "convolutions.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("1", "2", "3", "3", "3", "2", "1"),
                listOf("2", "4", "6", "6", "6", "4", "2"),
                listOf("3", "6", "9", "9", "9", "6", "3"),
                listOf("3", "6", "9", "9", "9", "6", "3"),
                listOf("3", "6", "9", "9", "9", "6", "3"),
                listOf("2", "4", "6", "6", "6", "4", "2"),
                listOf("1", "2", "3", "3", "3", "2", "1"),
            ),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(0, 6, FigureTone.Warn),
                FigureCell(6, 0, FigureTone.Warn),
                FigureCell(6, 6, FigureTone.Warn),
                FigureCell(3, 3, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Padding and stride are the two knobs that decide a convolution's output shape, and one formula covers both: ⌊(n + 2p − k)/s⌋ + 1. Padding adds a border of zeros so the window has somewhere to stand at the edges; stride is how far it jumps between positions. Getting this arithmetic wrong is the single most common shape error in a vision model, and it fails loudly at the first dense layer rather than where the mistake was made.",
        "Without padding, the border of every image is under-read before a single weight is learned. The simulation counts it: with a 3×3 kernel over a 7×7 input, the corner pixel falls inside exactly one window while the centre pixel falls inside nine. The map also shrinks by k − 1 pixels per layer, which is survivable once and not survivable at depth — ten unpadded 3×3 layers take a 224×224 image down to 204×204. \"Same\" padding, p = (k − 1)/2 at stride 1, is that formula solved for p, and it is why VGG pads every one of its thirteen convolutions.",
        "Stride does something different in kind: it divides rather than subtracts. Stride 2 halves the map in one step, which is the cheapest downsampling available and the reason it displaced pooling in most architectures after ResNet — the network learns *how* to reduce rather than being told. The cost is information: a stride-2 layer looks at each input position once, so aliasing is real, and detection and segmentation architectures pay careful attention to where in the stack the strides sit because everything downstream inherits that resolution.",
    ),
    steps = listOf(
        StepCard(1, "Start From the Formula", "⌊(n + 2p − k)/s⌋ + 1, per spatial axis. Everything else is a special case.", 0xFF10B981),
        StepCard(2, "Count the Border Reads", "With p = 0 the corner is read once and the centre k² times.", 0xFF06B6D4),
        StepCard(3, "Pad to Keep the Size", "p = (k − 1)/2 at stride 1 is 'same'; p = 0 is 'valid'.", 0xFF6366F1),
        StepCard(4, "Watch the Shrinkage Compound", "Each unpadded layer costs k − 1 pixels — ten of them cost twenty.", 0xFF8B5CF6),
        StepCard(5, "Use Stride to Downsample", "Stride divides: s = 2 halves the map and quarters the activations.", 0xFFF59E0B),
        StepCard(6, "Check the Floor", "The ⌊·⌋ silently drops the last partial window — the classic off-by-one.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Output size", "o = ⌊(n + 2p − k)/s⌋ + 1", "The whole topic. Applied independently per axis."),
        FormulaEntry("Same padding", "p = (k − 1)/2, at s = 1", "Whole only for odd k, which is why kernels are odd-sized."),
        FormulaEntry("With dilation", "o = ⌊(n + 2p − d(k−1) − 1)/s⌋ + 1", "Dilation inflates the kernel's reach without adding weights."),
        FormulaEntry("Shrinkage over L layers", "n − L(k − 1), unpadded at s = 1", "224 → 204 for ten 3×3 layers."),
        FormulaEntry("Reads per pixel", "up to k² in the interior, as few as 1 at a corner", "The measured asymmetry padding exists to fix."),
        FormulaEntry("Transposed conv", "o = s(n − 1) + k − 2p", "The upsampling inverse, used by U-Net and every generator."),
    ),
    notationKey = listOf(
        NotationEntry("valid", "p = 0 — only positions where the kernel fully fits"),
        NotationEntry("same", "p chosen so the output matches the input at stride 1"),
        NotationEntry("stride s", "how far the window jumps; s > 1 downsamples"),
        NotationEntry("dilation d", "gaps inserted inside the kernel; reach grows, parameters do not"),
        NotationEntry("aliasing", "detail lost when a strided layer samples too coarsely"),
        NotationEntry("output stride", "cumulative ∏s from the input — what a detection head has to undo"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The formula, and the border pixels nobody reads",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def out_size(n, k, s=1, p=0, d=1):
                    return (n + 2*p - d*(k - 1) - 1) // s + 1

                print(out_size(224, 3, s=1, p=1))     # 224  -- 'same'
                print(out_size(224, 3, s=1, p=0))     # 222  -- 'valid', two pixels gone
                print(out_size(224, 3, s=2, p=1))     # 112  -- stride halves it
                print(out_size(224, 7, s=2, p=3))     # 112  -- ResNet's stem

                # How many windows read each pixel, unpadded 3x3 over 7x7:
                counts = np.zeros((7, 7), int)
                for i in range(out_size(7, 3)):
                    for j in range(out_size(7, 3)):
                        counts[i:i+3, j:j+3] += 1
                print(counts[0, 0], counts[3, 3])     # 1 9
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Where the off-by-one actually bites",
            accentColor = 0xFFF59E0B,
            code = """
                import torch, torch.nn as nn

                x = torch.randn(1, 3, 224, 224)

                # Ten unpadded 3x3 layers: the map is eaten twenty pixels at a time.
                unpadded = nn.Sequential(*[nn.Conv2d(3, 3, 3) for _ in range(10)])
                print(unpadded(x).shape)              # [1, 3, 204, 204]

                padded = nn.Sequential(*[nn.Conv2d(3, 3, 3, padding=1) for _ in range(10)])
                print(padded(x).shape)                # [1, 3, 224, 224]

                # The floor silently drops a partial window, and the error surfaces later:
                print(nn.Conv2d(3, 3, 3, stride=2)(torch.randn(1, 3, 7, 7)).shape)   # [1, 3, 3, 3]
                # (7 - 3)//2 + 1 = 3, not 4. Column 6 is never the start of a window at all,
                # so an odd input size quietly loses its last row and column at every stride-2 layer
                # -- which is why segmentation decoders that assume exact halving break on odd inputs.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Designing a Backbone", "Every architecture is a schedule of strides: where the map halves determines both cost and the finest detail anything downstream can see."),
        ApplicationCard("flask", 0xFF06B6D4, "Segmentation", "U-Net's decoder has to invert the encoder's strides exactly; a mismatched shape is the most common bug in a custom decoder."),
        ApplicationCard("search", 0xFF8B5CF6, "Detection", "Anchor boxes and feature-pyramid levels are indexed by output stride, so the geometry is part of the head's definition."),
        ApplicationCard("globe", 0xFFF59E0B, "Dilated Convolutions", "Semantic segmentation keeps resolution by growing the receptive field with dilation instead of stride."),
    ),
    takeaways = listOf(
        "One formula governs everything: ⌊(n + 2p − k)/s⌋ + 1, per axis.",
        "Unpadded convolution reads corner pixels once and centre pixels k² times — padding is what makes the border count.",
        "Shrinkage compounds: ten unpadded 3×3 layers take 224 down to 204, which is why deep stacks pad every layer.",
        "Stride divides rather than subtracts, and a stride-2 convolution is the learned replacement for pooling.",
        "The floor drops the last partial window, so odd input sizes lose a row and column at every strided layer.",
    ),
    crossLinks = listOf(
        CrossLink("conv_layers", "Convolution Layers"),
        CrossLink("pooling_layers", "Pooling Layers"),
        CrossLink("vgg", "VGG-16 / VGG-19"),
        CrossLink("resnet", "ResNet"),
    ),
)
