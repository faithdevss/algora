package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mobileNetContent = TopicContent(
    topicId = "mobilenet",
    whatIsIt = listOf(
        "A standard convolution does two jobs at once: it filters spatially and it mixes channels, because every output channel reads every input channel through its own k×k kernel. MobileNet's observation is that those two jobs can be separated. A depthwise convolution applies one k×k kernel per input channel and mixes nothing; a pointwise 1×1 mixes channels and does no spatial work. Together they produce the same output shape as the standard layer.",
        "The saving is exact and independent of image size. The cost ratio is 1/N + 1/k², where N is the output channel count — at 128 → 256 channels with k = 3 that is 0.1150, so the layer drops from 294,912 parameters to 33,920, 8.7× cheaper, and the same factor applies to its multiply-accumulates. Because the 1/k² term dominates once N is large, the saving parks near 8–9× for 3×3 kernels and stays there; the simulation sweeps N to show the curve flattening. MobileNetV1 is that block repeated 28 times: 4.2M parameters and 569M MACs, within about a point of VGG-16's ImageNet accuracy at 33× fewer parameters.",
        "V2 added the inverted residual, which reads backwards until you see the reason. A standard bottleneck goes wide → narrow → wide; V2 goes narrow → wide → narrow: expand with a 1×1, filter depthwise in the expanded space, project back down, and put the skip connection between the *narrow* ends. Depthwise convolutions are cheap enough to run wide, and the projection's output is deliberately linear — no ReLU — because ReLU destroys information in a low-dimensional space, which the paper demonstrates rather than assumes. V3 then tuned the whole thing by architecture search and added squeeze-and-excitation plus the h-swish activation.",
    ),
    steps = listOf(
        StepCard(1, "Split the Two Jobs", "Filtering is spatial; mixing is across channels. They need not be one operation.", 0xFF10B981),
        StepCard(2, "Depthwise: One Kernel per Channel", "k² weights per input channel, no cross-channel mixing at all.", 0xFF06B6D4),
        StepCard(3, "Pointwise: A 1×1 to Mix", "C_in × C_out weights, no spatial extent.", 0xFF6366F1),
        StepCard(4, "Price the Ratio", "1/N + 1/k² = 0.1150 at N = 256, k = 3 — 8.7× cheaper.", 0xFF8B5CF6),
        StepCard(5, "Add Width and Resolution Multipliers", "α and ρ scale the whole network for a device's budget.", 0xFFF59E0B),
        StepCard(6, "Invert the Bottleneck (V2)", "Expand, filter depthwise, project down linearly; skip between the narrow ends.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Standard convolution", "C_in · C_out · k²", "294,912 at 128 → 256, k = 3."),
        FormulaEntry("Depthwise separable", "C_in·k² + C_in·C_out", "1,152 + 32,768 = 33,920 at the same shape."),
        FormulaEntry("Cost ratio", "1/N + 1/k²", "0.1150 at N = 256, k = 3 — independent of spatial size."),
        FormulaEntry("Ceiling of the saving", "→ 1/k² as N → ∞", "9× for 3×3; the curve flattens fast."),
        FormulaEntry("Width multiplier α", "channels → ⌈αC⌉", "Cost scales with α²."),
        FormulaEntry("Resolution multiplier ρ", "input → ρ·n", "Cost scales with ρ²; parameters are untouched."),
    ),
    notationKey = listOf(
        NotationEntry("depthwise convolution", "groups = channels: one filter per channel, no mixing"),
        NotationEntry("pointwise convolution", "a 1×1; mixing only, no spatial extent"),
        NotationEntry("inverted residual", "V2's narrow → wide → narrow block with the skip between narrow ends"),
        NotationEntry("linear bottleneck", "no ReLU on the projection, because it would discard low-dimensional information"),
        NotationEntry("squeeze-and-excitation", "V3's per-channel gating, learned from a global pooled summary"),
        NotationEntry("h-swish", "V3's piecewise-linear swish, chosen because it is cheap on mobile hardware"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The block, and the ratio that justifies it",
            accentColor = 0xFF10B981,
            code = """
                import torch.nn as nn

                def separable(c_in, c_out, stride=1):
                    return nn.Sequential(
                        nn.Conv2d(c_in, c_in, 3, stride, 1, groups=c_in, bias=False),  # depthwise
                        nn.BatchNorm2d(c_in), nn.ReLU6(inplace=True),
                        nn.Conv2d(c_in, c_out, 1, bias=False),                          # pointwise
                        nn.BatchNorm2d(c_out), nn.ReLU6(inplace=True),
                    )

                standard = nn.Conv2d(128, 256, 3, padding=1, bias=False)
                print(sum(p.numel() for p in standard.parameters()))    # 294912
                print(128*9 + 128*256)                                  # 33920
                print(1/256 + 1/9)                                      # 0.11502 -> 8.7x cheaper

                # groups=c_in is the whole depthwise trick: PyTorch's grouped convolution with one
                # group per channel is exactly "one filter per channel, no mixing".
            """.trimIndent(),
        ),
        CodeBlock(
            title = "V2's inverted residual, and the missing ReLU",
            accentColor = 0xFFEC4899,
            code = """
                import torch, torch.nn as nn

                class InvertedResidual(nn.Module):
                    def __init__(self, c, expand=6):
                        super().__init__()
                        hidden = c * expand
                        self.body = nn.Sequential(
                            nn.Conv2d(c, hidden, 1, bias=False), nn.BatchNorm2d(hidden), nn.ReLU6(),
                            nn.Conv2d(hidden, hidden, 3, padding=1, groups=hidden, bias=False),
                            nn.BatchNorm2d(hidden), nn.ReLU6(),
                            nn.Conv2d(hidden, c, 1, bias=False), nn.BatchNorm2d(c),   # no ReLU here
                        )

                    def forward(self, x):
                        return x + self.body(x)          # skip joins the NARROW ends

                # Two deliberate inversions of the usual bottleneck:
                #  - it expands before the 3x3 rather than reducing, because a depthwise 3x3 is cheap
                #    enough to run at 6x width;
                #  - the projection is linear, because ReLU on a low-dimensional tensor throws away
                #    information that cannot be recovered -- the paper shows this by embedding a
                #    spiral in n dimensions, ReLU-ing it and projecting back.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "On-Device Vision", "Real-time classification and detection on phones, where the model runs inside a fixed latency budget."),
        ApplicationCard("browser", 0xFF06B6D4, "Browser and Edge Inference", "Small enough to ship over a network and run in WebAssembly or on a microcontroller."),
        ApplicationCard("search", 0xFF8B5CF6, "Detection Backbones", "SSD-MobileNet is the standard mobile detector; V3 backbones drive most on-device segmentation."),
        ApplicationCard("target", 0xFFF59E0B, "Latency-Constrained Serving", "α and ρ let one architecture be re-tuned to a device rather than redesigned."),
    ),
    takeaways = listOf(
        "Depthwise separable convolution splits filtering from mixing: one k×k per channel, then a 1×1 to combine.",
        "The saving is exactly 1/N + 1/k² — 8.7× at 256 channels with a 3×3 — and it does not depend on image size.",
        "The ceiling is 1/k², so 3×3 separable layers park at roughly 9× and no channel count improves that much further.",
        "MobileNetV1: 4.2M parameters and 569M MACs, close to VGG-16's accuracy at 33× fewer parameters.",
        "V2's inverted residual expands before the depthwise layer and projects back down *linearly*, because ReLU in a narrow space destroys information.",
    ),
    crossLinks = listOf(
        CrossLink("efficientnet", "EfficientNet"),
        CrossLink("resnet", "ResNet"),
        CrossLink("inception", "Inception"),
        CrossLink("conv_layers", "Convolution Layers"),
    ),
)
