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

internal val inceptionContent = TopicContent(
    topicId = "inception",
    figure = Figure(
        caption = "The inception 3a module priced twice, in multiply-accumulates per output " +
            "position, at its real widths: 192 channels in, branches of 64, 128, 32 and 32. The " +
            "naive column is what running the kernels directly costs; the second is the same " +
            "module with a 1×1 reduction in front of each spatial branch. Almost all of the " +
            "saving is in one row — the 5×5 branch drops 9.7×, because compressing 192 channels " +
            "to 16 first costs 3,072 and then the 5×5 runs over 16 channels instead of 192. The " +
            "1×1 branch and the pooling projection have no spatial kernel to feed, so nothing " +
            "changes there. Module total 393,216 → 163,328, a 2.4× cut at an identical output " +
            "shape, which is what made stacking nine of these affordable — and the 1×1 sandwich " +
            "is the part of this paper that outlived the module.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("12,288", "12,288", "1.0×"),
                listOf("221,184", "129,024", "1.7×"),
                listOf("153,600", "15,872", "9.7×"),
                listOf("6,144", "6,144", "1.0×"),
                listOf("393,216", "163,328", "2.4×"),
            ),
            rowHeaders = listOf("1×1 · 64", "3×3 · 128", "5×5 · 32", "pool · 32", "module"),
            colHeaders = listOf("naive", "with 1×1", "cut"),
            marks = listOf(
                FigureCell(2, 0, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(4, 2),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Every architecture before it answered \"which kernel size should this layer use?\" by choosing one. Inception's answer is to stop choosing: one module runs 1×1, 3×3 and 5×5 convolutions plus a pooling branch over the same input and concatenates the outputs along the channel axis, so the network decides per feature which scale mattered. GoogLeNet stacks nine of these and won ILSVRC 2014 at 6.67% top-5 error.",
        "Done naively that is unaffordable, and the simulation prices it exactly. At the real widths of the inception 3a module — 192 input channels, branches of 64, 128, 32 and 32 — the naive version costs 393,216 multiply-accumulates per output position, of which the 5×5 branch alone is 153,600. Worse, concatenation means each module's output is wider than its input, so stacking makes the next module's cost grow with it.",
        "The fix is the 1×1 convolution, and it is the idea from this paper that outlived the module. A 1×1 has no spatial extent at all: it is a learned linear map across channels, so it can compress 192 channels to 16 before the 5×5 ever runs. That branch drops from 153,600 to 15,872 MACs — 9.7× — and the whole module from 393,216 to 163,328, a 2.4× cut, with the same output shape. GoogLeNet also ends with global average pooling instead of a dense head, which is why it holds about 6.8M parameters against VGG-16's 138.4M while scoring better. Every ResNet-50 block and every MobileNetV2 block is a 1×1 sandwich built on this observation.",
    ),
    steps = listOf(
        StepCard(1, "Run Every Scale", "1×1, 3×3, 5×5 and a pooling branch, all over the same input.", 0xFF10B981),
        StepCard(2, "Concatenate the Outputs", "Channels stack; the spatial size is kept identical across branches.", 0xFF06B6D4),
        StepCard(3, "Price the Naive Version", "393,216 MACs per position, 153,600 of it in the 5×5 branch.", 0xFF6366F1),
        StepCard(4, "Insert 1×1 Reductions", "192 → 16 channels before the 5×5. No spatial extent, pure channel mixing.", 0xFF8B5CF6),
        StepCard(5, "Re-price It", "163,328 MACs — 2.4× cheaper, same output shape.", 0xFFF59E0B),
        StepCard(6, "Delete the Dense Head", "Global average pooling: 6.8M parameters against VGG's 138.4M.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Naive 5×5 branch", "192 × 32 × 25 = 153,600", "MACs per output position."),
        FormulaEntry("With a 16-channel reduction", "192×16 + 16×32×25 = 15,872", "9.7× cheaper for the same output."),
        FormulaEntry("Module total", "393,216 → 163,328 MACs", "2.4× overall, at identical output width."),
        FormulaEntry("1×1 convolution", "y[c] = Σ_c' W[c,c']·x[c']", "A learned linear map across channels, applied per position."),
        FormulaEntry("Concatenation", "C_out = ΣC_branch", "Width grows by design — which is why the bottleneck is required."),
        FormulaEntry("Factorised 5×5 (v2/v3)", "5×5 → two 3×3, then n×n → 1×n + n×1", "Later versions push the same argument further."),
    ),
    notationKey = listOf(
        NotationEntry("bottleneck", "a 1×1 that reduces channels before an expensive kernel"),
        NotationEntry("GoogLeNet", "the 22-layer network built from nine inception modules; Inception-v1"),
        NotationEntry("inception 3a", "the first module after the stem — the one priced here"),
        NotationEntry("auxiliary classifiers", "v1's two mid-network heads, added to push gradient into early layers"),
        NotationEntry("factorisation", "v3's replacement of large kernels by stacks and by n×1 + 1×n pairs"),
        NotationEntry("Inception-ResNet", "v4's hybrid: inception modules with residual connections"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The module, and the reduction that makes it affordable",
            accentColor = 0xFF10B981,
            code = """
                import torch, torch.nn as nn

                class Inception(nn.Module):
                    def __init__(self, c_in, n1, r3, n3, r5, n5, pool):
                        super().__init__()
                        self.b1 = nn.Conv2d(c_in, n1, 1)
                        self.b3 = nn.Sequential(nn.Conv2d(c_in, r3, 1), nn.ReLU(),
                                                nn.Conv2d(r3, n3, 3, padding=1))
                        self.b5 = nn.Sequential(nn.Conv2d(c_in, r5, 1), nn.ReLU(),
                                                nn.Conv2d(r5, n5, 5, padding=2))
                        self.bp = nn.Sequential(nn.MaxPool2d(3, 1, 1), nn.Conv2d(c_in, pool, 1))

                    def forward(self, x):
                        return torch.cat([self.b1(x), self.b3(x), self.b5(x), self.bp(x)], dim=1)

                # inception 3a, at the paper's widths
                m = Inception(192, n1=64, r3=96, n3=128, r5=16, n5=32, pool=32)
                print(m(torch.randn(1, 192, 28, 28)).shape)     # [1, 256, 28, 28]
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the 1×1 is nearly free and the 5×5 is not",
            accentColor = 0xFF8B5CF6,
            code = """
                c_in = 192
                naive_5x5   = c_in * 32 * 5 * 5                 # 153,600 MACs / position
                reduce_then = c_in * 16 + 16 * 32 * 5 * 5       #  15,872
                print(naive_5x5 / reduce_then)                  # 9.68

                module_naive   = c_in*64 + c_in*128*9 + naive_5x5 + c_in*32      # 393,216
                module_reduced = c_in*64 + (c_in*96 + 96*128*9) + reduce_then + c_in*32  # 163,328
                print(module_naive / module_reduced)            # 2.41

                # The 1x1 costs c_in * c_out per position with no k^2 factor at all, so compressing
                # the channel axis is always cheap relative to the layer it protects. That is the
                # transferable idea: ResNet-50's bottleneck block is 1x1 down, 3x3, 1x1 up, and
                # MobileNetV2 inverts it -- 1x1 up, depthwise 3x3, 1x1 down -- for the same reason.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "ImageNet 2014 Winner", "6.67% top-5 error with 12× fewer parameters than VGG-16, which took second place."),
        ApplicationCard("chip", 0xFF06B6D4, "Efficient Inference", "The bottleneck pattern is now standard in every architecture designed for a compute budget."),
        ApplicationCard("flask", 0xFF8B5CF6, "Medical Imaging", "Inception-v3 is the transfer-learning backbone behind several published diagnostic models."),
        ApplicationCard("chart", 0xFFF59E0B, "FID and Inception Score", "Generative-model evaluation is defined in terms of a pretrained Inception network's features."),
    ),
    takeaways = listOf(
        "The module runs every kernel size at once and concatenates, so scale is chosen per feature rather than per layer.",
        "A 1×1 convolution is a learned linear map across channels with no spatial extent — and it is what makes the module affordable.",
        "Measured at the paper's widths: the 5×5 branch drops 9.7×, the whole module 2.4×, for the same output shape.",
        "Global average pooling instead of a dense head is why GoogLeNet is ~6.8M parameters against VGG-16's 138.4M.",
        "The bottleneck outlived the module: ResNet-50 and MobileNetV2 blocks are both 1×1 sandwiches.",
    ),
    crossLinks = listOf(
        CrossLink("vgg", "VGG-16 / VGG-19"),
        CrossLink("resnet", "ResNet"),
        CrossLink("mobilenet", "MobileNet"),
        CrossLink("pooling_layers", "Pooling Layers"),
    ),
)
