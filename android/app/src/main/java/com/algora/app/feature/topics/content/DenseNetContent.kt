package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val denseNetContent = TopicContent(
    topicId = "densenet",
    whatIsIt = listOf(
        "DenseNet takes ResNet's shortcut and changes the operator: concatenate instead of add. Inside a dense block, layer ℓ receives the feature maps of *every* earlier layer stacked along the channel axis, and passes its own output on to every later one. A six-layer block therefore has 21 direct connections rather than six. Nothing has to be recomputed, and features from early layers stay literally available — not summed into a mixture — all the way to the classifier.",
        "Because every layer sees everything before it, each one only needs to contribute a little. That contribution is the growth rate k, typically 32: layer ℓ receives k₀ + k(ℓ − 1) channels and emits k new ones. The simulation walks a six-layer block from 64 input channels — 64, 96, 128, 160, 192, 224 arriving — and shows why the 1×1 bottleneck is not optional: it pins the 3×3's input at 4k = 128 channels regardless of how wide the concatenation has grown, so only the 1×1's cost scales with the concatenated width: per-layer cost still grows linearly (128 parameters per incoming channel against 288 without the bottleneck), which pays off in deeper blocks.",
        "The result is a genuinely parameter-efficient network: DenseNet-121 matches ResNet-50's ImageNet accuracy with about 8.0M parameters against 25.6M. The cost moved rather than disappearing. Every intermediate concatenation has to be kept live for the backward pass, so training memory — not FLOPs and not parameters — is the binding constraint, and naive implementations allocate a new tensor per concatenation. The standard fix is shared memory allocation with recomputation, which trades a little compute for a large memory saving.",
    ),
    steps = listOf(
        StepCard(1, "Concatenate, Don't Add", "Layer ℓ's input is [x₀, x₁, …, x_{ℓ−1}] along the channel axis.", 0xFF10B981),
        StepCard(2, "Count the Connections", "L(L+1)/2 in a block — 21 for six layers, not six.", 0xFF06B6D4),
        StepCard(3, "Set a Small Growth Rate", "k = 32 new channels per layer; everything else is reused.", 0xFF6366F1),
        StepCard(4, "Bottleneck the 3×3", "A 1×1 pins the 3×3's input at 4k channels however wide the block gets.", 0xFF8B5CF6),
        StepCard(5, "Transition Between Blocks", "1×1 halving the channels, then 2×2 average pool.", 0xFFF59E0B),
        StepCard(6, "Pay in Memory", "~8.0M parameters vs ResNet-50's 25.6M — but every concatenation stays live.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Dense connectivity", "x_ℓ = H_ℓ([x₀, x₁, …, x_{ℓ−1}])", "Concatenation, where ResNet writes x_ℓ = H(x_{ℓ−1}) + x_{ℓ−1}."),
        FormulaEntry("Channels at layer ℓ", "k₀ + k(ℓ − 1)", "64, 96, 128, 160, 192, 224 for k₀ = 64, k = 32."),
        FormulaEntry("Connections in a block", "L(L + 1)/2", "21 for six layers — the number the architecture is named for."),
        FormulaEntry("Bottleneck width", "4k", "The 3×3 never sees more than 128 channels at k = 32."),
        FormulaEntry("Block parameters", "≈331,776 for six layers at k = 32", "Nearly flat per layer, thanks to the bottleneck."),
        FormulaEntry("Compression θ", "⌊θ·C⌋ at each transition, θ = 0.5", "DenseNet-BC halves the channel count between blocks."),
    ),
    notationKey = listOf(
        NotationEntry("growth rate k", "new channels contributed per layer; the network's width knob"),
        NotationEntry("dense block", "the region where concatenation applies; spatial size is constant inside it"),
        NotationEntry("transition layer", "1×1 compression plus average pooling between blocks"),
        NotationEntry("DenseNet-BC", "the bottleneck + compression variant; the one people actually use"),
        NotationEntry("feature reuse", "the paper's claim that later layers keep reading early features directly"),
        NotationEntry("memory-efficient DenseNet", "shared allocation with recomputation, to fix the concatenation blow-up"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A dense block, and why the bottleneck is load-bearing",
            accentColor = 0xFF10B981,
            code = """
                import torch, torch.nn as nn

                class DenseLayer(nn.Module):
                    def __init__(self, c_in, growth=32):
                        super().__init__()
                        self.body = nn.Sequential(
                            nn.BatchNorm2d(c_in), nn.ReLU(),
                            nn.Conv2d(c_in, 4 * growth, 1, bias=False),      # bottleneck
                            nn.BatchNorm2d(4 * growth), nn.ReLU(),
                            nn.Conv2d(4 * growth, growth, 3, padding=1, bias=False),
                        )

                    def forward(self, x):
                        return torch.cat([x, self.body(x)], dim=1)           # <- concatenate

                block = nn.Sequential(*[DenseLayer(64 + 32 * i) for i in range(6)])
                print(block(torch.randn(1, 64, 56, 56)).shape)      # [1, 256, 56, 56]
                print(sum(p.numel() for p in block.parameters()))   # ~332k; per-layer cost still grows linearly with the concatenated width
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What the concatenation costs, and where",
            accentColor = 0xFF8B5CF6,
            code = """
                growth, k0, L = 32, 64, 6

                with_bn = sum((k0 + growth*i) * 4*growth + 4*growth * growth * 9 for i in range(L))
                without = sum((k0 + growth*i) * growth * 9 for i in range(L))
                print(with_bn, without)     # 331776 248832

                # At six layers the bottleneck is not yet a saving -- it becomes one as the block
                # deepens, because the un-bottlenecked 3x3 faces the whole concatenation:
                L = 24
                print(sum((k0 + growth*i) * growth * 9 for i in range(L)))                    # 2,985,984
                print(sum((k0 + growth*i) * 4*growth + 4*growth*growth*9 for i in range(L)))  # 2,211,840

                # Parameters are only half the story. Every x_l stays live for the backward pass, so
                # activation memory grows with the square of block depth unless the implementation
                # shares one buffer and recomputes the BN/ReLU on the way back.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF10B981, "Medical Imaging", "CheXNet's pneumonia detector is a DenseNet-121; small models transfer better on limited labelled data."),
        ApplicationCard("chip", 0xFF06B6D4, "Parameter-Constrained Deployment", "Roughly a third of ResNet-50's parameters at comparable accuracy, when storage rather than latency is the limit."),
        ApplicationCard("globe", 0xFF8B5CF6, "Segmentation", "Dense connectivity in the encoder feeds a decoder features at every scale without extra skip machinery."),
        ApplicationCard("bulb", 0xFFF59E0B, "Feature Reuse Studies", "The clearest architecture for measuring which layers a classifier actually reads from."),
    ),
    takeaways = listOf(
        "Concatenation, not addition: layer ℓ reads every earlier layer's output directly rather than a sum.",
        "L(L+1)/2 connections in a block — 21 for six layers.",
        "Growth rate k is small (32) precisely because nothing has to be re-derived; features are reused, not rebuilt.",
        "The 1×1 bottleneck pins the 3×3's input at 4k channels, which cuts the per-layer growth rate (128 vs 288 parameters per incoming channel); per-layer cost still grows linearly with depth.",
        "DenseNet-121 matches ResNet-50 at ~8.0M parameters vs 25.6M, but pays in activation memory rather than FLOPs.",
    ),
    crossLinks = listOf(
        CrossLink("resnet", "ResNet"),
        CrossLink("inception", "Inception"),
        CrossLink("conv_layers", "Convolution Layers"),
        CrossLink("batch_normalization", "Batch Normalization"),
    ),
)
