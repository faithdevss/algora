package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val efficientNetContent = TopicContent(
    topicId = "efficientnet",
    figure = Figure(
        caption = "Compound scaling: depth, width and input resolution grown together by one knob φ, " +
            "with the per-step factors α = 1.2, β = 1.1 and γ = 1.15 found once on the B0 baseline. " +
            "The lab's φ = 3 marks the point it walks through — depth ×1.73, width ×1.33, " +
            "resolution ×1.52 — and the factors were chosen so α·β²·γ² ≈ 2, because width and " +
            "resolution each cost compute in proportion to their square: every step of φ roughly " +
            "doubles FLOPs, so φ = 3 costs 7.1× the FLOPs of B0. The lab's counterfactual is the " +
            "point of the method: spend the same 7.1× on depth alone and the network is seven times " +
            "deeper but no wider and no sharper, and accuracy per FLOP flattens. Balanced growth " +
            "is what let the family run from B0 to B7 without retuning each dimension by hand.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "depth, 1.2^φ",
                    listOf(
                        FigurePoint(0.000f, 0.333f), FigurePoint(0.167f, 0.400f), FigurePoint(0.333f, 0.480f),
                        FigurePoint(0.500f, 0.576f), FigurePoint(0.667f, 0.691f), FigurePoint(0.833f, 0.829f),
                        FigurePoint(1.000f, 0.995f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "resolution, 1.15^φ",
                    listOf(
                        FigurePoint(0.000f, 0.333f), FigurePoint(0.167f, 0.383f), FigurePoint(0.333f, 0.441f),
                        FigurePoint(0.500f, 0.507f), FigurePoint(0.667f, 0.583f), FigurePoint(0.833f, 0.670f),
                        FigurePoint(1.000f, 0.771f),
                    ),
                    tone = FigureTone.Primary,
                ),
                FigureSeries(
                    "width, 1.1^φ",
                    listOf(
                        FigurePoint(0.000f, 0.333f), FigurePoint(0.167f, 0.367f), FigurePoint(0.333f, 0.403f),
                        FigurePoint(0.500f, 0.444f), FigurePoint(0.667f, 0.488f), FigurePoint(0.833f, 0.537f),
                        FigurePoint(1.000f, 0.591f),
                    ),
                    tone = FigureTone.Muted,
                ),
            ),
            markers = listOf(
                FigurePoint(0.5f, 0.576f, "×1.73"),
                FigurePoint(0.5f, 0.507f, "×1.52", FigureTone.Primary),
                FigurePoint(0.5f, 0.444f, "×1.33", FigureTone.Muted),
            ),
            xLabel = "φ, 0 → 6 (B0 at 0)",
            yLabel = "multiplier over B0, 0 to 3",
        ),
    ),
    whatIsIt = listOf(
        "There are exactly three ways to make a convolutional network bigger: more layers (depth), more channels per layer (width), or larger input images (resolution). Every architecture before EfficientNet scaled one of them by hand — ResNet-50 to ResNet-152 is depth, WideResNet is width — and each axis saturates on its own. Accuracy flattens while the FLOPs keep climbing, because a deeper network on small images runs out of detail to see and a wider one on few layers runs out of abstraction to build.",
        "Compound scaling ties all three to a single exponent: depth = 1.2^φ, width = 1.1^φ, resolution = 1.15^φ. The constants come from a small grid search under the constraint α·β²·γ² ≈ 2 — the squares are there because compute scales linearly with depth but quadratically with both width and resolution — which makes each unit of φ approximately a doubling of FLOPs (α·β²·γ² = 1.92). At φ = 6 that is depth ×2.99, width ×1.77 and resolution ×2.31, and the simulation plots all three growing together, with depth growing fastest and resolution taking the largest share of the added compute (γ² = 1.32 per step). That is why the family's input grows from 224² to 600² while its layer count barely triples.",
        "The measured payoff is large: EfficientNet-B7 reached 84.3% ImageNet top-1 with about 66M parameters, against GPipe's 557M at the same accuracy — 8.4× smaller and 6.1× faster to run. Two caveats keep this honest. The B0 backbone was itself found by neural architecture search over MobileNetV2-style inverted-residual blocks with squeeze-and-excitation, so compound scaling is what was applied to an already good small model rather than a substitute for having one. And FLOPs are not latency: the depthwise-heavy blocks that make B0 cheap on paper are memory-bandwidth-bound on GPUs, which is exactly what EfficientNetV2 went back and fixed by using ordinary convolutions in the early stages.",
    ),
    steps = listOf(
        StepCard(1, "Name the Three Axes", "Depth, width, resolution — the only knobs a convnet has.", 0xFF10B981),
        StepCard(2, "Watch Each One Saturate", "Scaling any single axis flattens in accuracy long before it flattens in cost.", 0xFF06B6D4),
        StepCard(3, "Tie Them to One Exponent", "d = α^φ, w = β^φ, r = γ^φ, with α·β²·γ² ≈ 2.", 0xFF6366F1),
        StepCard(4, "Search the Constants Once", "1.2, 1.1, 1.15 — found on B0 by a small grid search, then reused.", 0xFF8B5CF6),
        StepCard(5, "Turn One Dial", "φ = 0…6 approximately gives B0…B6, each step roughly doubling the FLOPs (the released B-models use hand-set coefficients).", 0xFFF59E0B),
        StepCard(6, "Start From a Good Backbone", "B0 came from architecture search; scaling amplifies it, it does not replace it.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Compound scaling", "d = α^φ, w = β^φ, r = γ^φ", "One exponent drives all three axes."),
        FormulaEntry("The constraint", "α·β²·γ² ≈ 2", "1.2 · 1.1² · 1.15² = 1.920 — each unit of φ doubles the FLOPs."),
        FormulaEntry("Why the squares", "cost ∝ d · w² · r²", "Linear in depth, quadratic in width and in each spatial axis."),
        FormulaEntry("At φ = 6", "d ×2.99 · w ×1.77 · r ×2.31", "Depth grows fastest; resolution takes the largest share of added compute (γ² = 1.32 per step)."),
        FormulaEntry("Total FLOPs", "≈2^φ × baseline", "The scaling law the constants were chosen to produce."),
        FormulaEntry("B7 vs GPipe", "66M vs 557M parameters at 84.3% top-1", "8.4× smaller, 6.1× faster."),
    ),
    notationKey = listOf(
        NotationEntry("φ (phi)", "the single compound coefficient; B0 through B7 map to it only approximately — the released models use hand-set coefficients"),
        NotationEntry("MBConv", "the inverted-residual block from MobileNetV2, with squeeze-and-excitation added"),
        NotationEntry("NAS", "the neural architecture search that produced the B0 baseline"),
        NotationEntry("squeeze-and-excitation", "per-channel gating learned from a global pooled summary"),
        NotationEntry("FLOPs vs latency", "the gap this family made famous; V2 was the correction"),
        NotationEntry("EfficientNetV2", "fused-MBConv in early stages plus progressive resizing, for real-hardware speed"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The scaling rule, and the constraint behind the constants",
            accentColor = 0xFF10B981,
            code = """
                alpha, beta, gamma = 1.2, 1.1, 1.15
                print(alpha * beta**2 * gamma**2)          # 1.9203 -- the "approximately 2"

                for phi in range(7):
                    d, w, r = alpha**phi, beta**phi, gamma**phi
                    print(phi, round(d, 2), round(w, 2), round(r, 2), round(d * w*w * r*r, 1))
                # 0 1.00 1.00 1.00   1.0
                # 3 1.73 1.33 1.52   7.1        (2^3 = 8)
                # 6 2.99 1.77 2.31  50.1        (2^6 = 64)
                # The gap is the "approximately": 1.9203^6 = 50, not 64. The constants were rounded
                # to two decimals for reporting, and the family's actual per-step FLOP growth is
                # a little under a doubling.

                # The squares are the whole reason the constraint is not alpha*beta*gamma:
                # doubling the channel count multiplies work by 4 (both the input and output of
                # every conv widen), and doubling the resolution multiplies it by 4 as well.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Using the family, and the resolution nobody remembers to change",
            accentColor = 0xFF8B5CF6,
            code = """
                import timm, torch

                for name, size in [('efficientnet_b0', 224), ('efficientnet_b3', 300),
                                   ('efficientnet_b7', 600)]:
                    m = timm.create_model(name, pretrained=False)
                    n = sum(p.numel() for p in m.parameters())
                    print(name, f"{n/1e6:.1f}M params", f"input {size}")
                # efficientnet_b0  5.3M params  input 224
                # efficientnet_b3 12.2M params  input 300
                # efficientnet_b7 66.3M params  input 600

                # The input size is part of the model, not a preprocessing detail: feeding B7 a 224px
                # image throws away most of what the scaling bought, and is the single most common
                # way this family gets benchmarked badly.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Accuracy per FLOP", "The reference point for \"best model under a compute budget\" for several years after 2019."),
        ApplicationCard("chip", 0xFF06B6D4, "Model Family Design", "One architecture with a documented scaling rule, rather than a redesign per device class."),
        ApplicationCard("flask", 0xFF8B5CF6, "Transfer Learning", "B0–B4 are common fine-tuning backbones where a ResNet-50 would be needlessly large."),
        ApplicationCard("trend", 0xFFF59E0B, "Scaling Laws", "An early, concrete instance of the idea that scaling axes should be grown in a fixed ratio — the same argument later made for language models."),
    ),
    takeaways = listOf(
        "Depth, width and resolution are the only three axes, and each saturates when scaled alone.",
        "Compound scaling drives all three from one exponent: 1.2^φ, 1.1^φ, 1.15^φ.",
        "α·β²·γ² ≈ 2 makes each unit of φ approximately a doubling of FLOPs; the squares reflect width and resolution both costing quadratically.",
        "B7 hit 84.3% ImageNet top-1 with 66M parameters against GPipe's 557M — 8.4× smaller at equal accuracy.",
        "FLOPs are not latency: the depthwise-heavy B0 is bandwidth-bound on GPUs, which is what EfficientNetV2 corrected.",
    ),
    crossLinks = listOf(
        CrossLink("mobilenet", "MobileNet"),
        CrossLink("resnet", "ResNet"),
        CrossLink("vit", "Vision Transformers"),
        CrossLink("transfer_learning", "Transfer Learning"),
    ),
)
