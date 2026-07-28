package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val swishContent = TopicContent(
    topicId = "swish",
    whatIsIt = listOf(
        "Swish is z·σ(βz): the input multiplied by a gate computed from the input itself. It is smooth everywhere, unbounded above, bounded below, and — unlike every activation before it in this category — it is not monotone. It dips to −0.2785 at z = −1.2785 before coming back up, which the simulation measures rather than quotes.",
        "That dip has a consequence worth seeing: the derivative exceeds 1, peaking at 1.0998. Sigmoid, tanh, ReLU, Leaky ReLU and ELU all have derivatives capped at 1 or below, so they can only attenuate the backward signal. Swish can amplify it slightly. β interpolates between two things you already know — at β → 0 the gate is a constant ½ and Swish becomes the linear z/2, at β → ∞ the gate becomes a step and Swish becomes ReLU exactly. β = 1 is the usual choice and is what SiLU means.",
        "It is worth being straight about where it came from. Swish was found by an automated search over candidate activation functions, not derived from a property anyone wanted — the explanations for why it works were written afterwards, and they are plausible rather than compelling. It had also been published twice before under other names, as SiL and SiLU, which is a reasonable thing to know before citing it. The reported gains over ReLU are consistent but small, roughly a point of ImageNet top-1, and it costs a sigmoid evaluation per unit. That is the whole case: a modest, reproducible improvement with a real compute cost, which is enough to matter at scale and not enough to justify rewriting a small model.",
    ),
    steps = listOf(
        StepCard(1, "Gate the Input by Itself", "z·σ(βz). The gate's argument is the same value being gated.", 0xFFF59E0B),
        StepCard(2, "Notice It Is Not Monotone", "A dip to −0.2785 near z = −1.28. Deliberate, and unusual.", 0xFFFBBF24),
        StepCard(3, "Notice the Derivative Exceeds 1", "Peaks at 1.0998 — it can amplify, not only attenuate.", 0xFFEC4899),
        StepCard(4, "Understand β", "β → 0 gives a line, β → ∞ gives ReLU. β = 1 is SiLU.", 0xFF8B5CF6),
        StepCard(5, "Pay for the Sigmoid", "One exponential per unit, against ReLU's comparison.", 0xFF6366F1),
        StepCard(6, "Expect a Small Gain", "About a point of ImageNet top-1. Real, consistent, and not large.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "f(z) = z·σ(βz)", "β = 1 gives SiLU."),
        FormulaEntry("Derivative", "f′(z) = σ(βz) + βz·σ(βz)(1 − σ(βz))", "Equivalently βf(z) + σ(βz)(1 − βf(z))."),
        FormulaEntry("Maximum slope", "≈ 1.0998", "Above 1 — it can amplify the backward signal."),
        FormulaEntry("Minimum", "≈ −0.2785 at z ≈ −1.2785", "The non-monotone dip."),
        FormulaEntry("β → ∞", "f(z) → max(0, z)", "The gate becomes a step; Swish becomes ReLU."),
        FormulaEntry("β → 0", "f(z) → z/2", "The gate becomes a constant; Swish becomes linear."),
    ),
    notationKey = listOf(
        NotationEntry("self-gating", "the gate is computed from the value it multiplies"),
        NotationEntry("SiLU", "Sigmoid Linear Unit — Swish with β = 1; the earlier name"),
        NotationEntry("non-monotone", "the function decreases somewhere; the dip near −1.28"),
        NotationEntry("β", "the gate's sharpness; usually fixed at 1"),
        NotationEntry("NAS", "neural architecture search, which is how this was found"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The two shape facts, checked rather than quoted",
            accentColor = 0xFFF59E0B,
            code = """
                import torch

                z = torch.linspace(-8, 8, 200_001, requires_grad=True)
                y = torch.nn.functional.silu(z)          # SiLU == Swish with beta = 1

                print(y.min().item(), z[y.argmin()].item())    # -0.2785 at -1.2785
                y.sum().backward()
                print(z.grad.max().item())                     # 1.0998 -- above 1

                # Every activation earlier in this category caps its derivative at 1. Swish does
                # not, and the same is true of GELU. It is a small effect and it is not nothing:
                # a function that can pass more than it receives behaves differently in a deep
                # stack than one that can only attenuate.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What β interpolates between, and the cost",
            accentColor = 0xFF6366F1,
            code = """
                import torch, time

                def swish(z, beta=1.0):
                    return z * torch.sigmoid(beta * z)

                z = torch.tensor([-3.0, -1.0, 0.0, 1.0, 3.0])
                print(swish(z, 0.01).round(decimals=3))   # ~ z/2 -- a straight line
                print(swish(z, 1.0).round(decimals=3))    # the usual curve
                print(swish(z, 100.0).round(decimals=3))  # ~ max(0, z) -- ReLU

                # The cost, which decides it more often than accuracy does:
                x = torch.randn(4096, 4096)
                for name, fn in (("relu", torch.relu), ("silu", torch.nn.functional.silu)):
                    t = time.perf_counter()
                    for _ in range(50):
                        fn(x)
                    print(name, round(time.perf_counter() - t, 3))
                # Use nn.SiLU() rather than writing z * sigmoid(z) -- the fused kernel avoids
                # materialising the intermediate, which is most of the difference.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFFF59E0B, "EfficientNet and MobileNetV3", "Swish (and its hard-sigmoid approximation, hard-swish) throughout — chosen where the accuracy-per-FLOP trade is the design target."),
        ApplicationCard("robot", 0xFF8B5CF6, "YOLO Detection Heads", "SiLU is the default activation in recent YOLO versions."),
        ApplicationCard("chip", 0xFF6366F1, "Hard-Swish on Mobile", "z·ReLU6(z+3)/6 approximates it with no exponential, for hardware where that matters."),
    ),
    takeaways = listOf(
        "z·σ(βz): self-gated, smooth, and non-monotone with a dip to −0.2785.",
        "Its derivative peaks at 1.0998 — it can amplify the backward signal, unlike ReLU or tanh.",
        "β interpolates from linear (β→0) to exactly ReLU (β→∞); β = 1 is SiLU.",
        "It was found by automated search, and had been published twice before under other names.",
        "Gains over ReLU are consistent and small, at the cost of a sigmoid per unit.",
    ),
    crossLinks = listOf(
        CrossLink("gelu", "GELU (Gaussian Error Linear Unit)"),
        CrossLink("relu", "ReLU (Rectified Linear Unit)"),
        CrossLink("sigmoid", "Sigmoid"),
        CrossLink("cnn", "CNNs"),
    ),
)
