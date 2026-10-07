package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val eluContent = TopicContent(
    topicId = "elu",
    whatIsIt = listOf(
        "ELU keeps the identity on the positive side and replaces the negative side with α(eᶻ − 1). Two things follow. It is smooth at every point including zero, where ReLU has a kink. And it saturates gently to −α rather than falling away without limit, so a large negative input produces a bounded response instead of being propagated.",
        "The argument for it is the mean. Measured over standard normal input ELU averages 0.160 against ReLU's 0.399 — much closer to zero, because the negative branch contributes something rather than nothing. Activations centred near zero are precisely the property batch normalisation is added to enforce, and ELU gets part of the way there for free, which is what the 2015 paper claimed.",
        "It is not saturation-free, and the simulation says how far from it: the fraction of units with a near-zero derivative climbs with pre-activation width the way sigmoid's does, though from a lower base and more slowly. ELU trades ReLU's hard zero for a soft floor, and a soft floor is still a floor. In practice the deciding factor is usually cost rather than accuracy — an exponential per negative unit against ReLU's single comparison, on a model with billions of activations — and the accuracy gain over ReLU-with-batch-norm is small. ELU is a good default when you are *not* using normalisation layers, which is a narrower situation now than it was when it was introduced.",
    ),
    steps = listOf(
        StepCard(1, "Keep the Positive Identity", "Same as ReLU above zero, so the gradient is exactly 1 there.", 0xFFF59E0B),
        StepCard(2, "Curve the Negative Side", "α(eᶻ − 1): smooth, negative, and bounded below.", 0xFFFBBF24),
        StepCard(3, "Get a Mean Nearer Zero", "0.160 against ReLU's 0.399, measured on standard normal input.", 0xFFEC4899),
        StepCard(4, "Accept the Soft Saturation", "The negative tail flattens at −α, so its derivative decays.", 0xFF8B5CF6),
        StepCard(5, "Pay for the Exponential", "One exp() per negative unit. Real at scale.", 0xFF6366F1),
        StepCard(6, "Compare Against BatchNorm", "Both target centred activations. If you already have one, the case weakens.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "f(z) = z if z > 0, else α(eᶻ − 1)", "α = 1 by default."),
        FormulaEntry("Derivative", "1 if z > 0, else α·eᶻ = f(z) + α", "Cheap: reuse the forward value."),
        FormulaEntry("Lower bound", "f(z) → −α as z → −∞", "Bounded, unlike Leaky ReLU."),
        FormulaEntry("Continuity", "f and f′ continuous at 0 when α = 1", "ReLU's derivative jumps; ELU's does not."),
        FormulaEntry("Mean output", "≈ 0.160 for z ~ N(0,1)", "Against ReLU's 0.399."),
        FormulaEntry("SELU", "λ·ELU(z), λ ≈ 1.0507, α ≈ 1.6733", "The self-normalising rescaling."),
    ),
    notationKey = listOf(
        NotationEntry("α", "the negative saturation level; the curve flattens at −α"),
        NotationEntry("soft saturation", "a bounded tail with a decaying derivative, not a hard zero"),
        NotationEntry("bias shift", "the mean drift ELU exists to reduce"),
        NotationEntry("internal covariate shift", "the phenomenon batch norm targets, addressed differently here"),
        NotationEntry("CELU", "a variant that is continuously differentiable for any α"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The mean-shift argument, measured",
            accentColor = 0xFFF59E0B,
            code = """
                import torch
                import torch.nn.functional as F

                z = torch.randn(200_000)

                print(F.relu(z).mean().item())          # ~0.399
                print(F.elu(z).mean().item())           # ~0.160
                print(F.leaky_relu(z, 0.01).mean().item())  # ~0.395 -- barely moves

                # Note the third line. Leaky ReLU does NOT fix the mean shift: a slope of 0.01
                # contributes almost nothing on the negative side. ELU and Leaky ReLU are often
                # described as solving the same problem, and they do not -- Leaky targets dead
                # units, ELU targets the mean.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Its cost, and where the case for it actually holds",
            accentColor = 0xFF6366F1,
            code = """
                import torch, torch.nn as nn, time

                x = torch.randn(4096, 4096)
                for name, fn in (("relu", torch.relu), ("elu", torch.nn.functional.elu)):
                    t = time.perf_counter()
                    for _ in range(50):
                        fn(x)
                    print(name, round(time.perf_counter() - t, 3))
                # ELU is consistently slower -- an exp() per negative element against a compare.

                # And the accuracy case: ELU vs ReLU is a real but small gap WITHOUT normalisation,
                # and close to a wash with it. So the honest decision rule is about what else is
                # in the block, not about the activation alone.
                without_norm = nn.Sequential(nn.Linear(256, 256), nn.ELU())    # ELU earns its keep
                with_norm = nn.Sequential(nn.Linear(256, 256), nn.BatchNorm1d(256), nn.ReLU())
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFFF59E0B, "Networks Without Normalisation", "Where the mean-shift argument is not already handled by a batch-norm layer."),
        ApplicationCard("robot", 0xFF8B5CF6, "Continuous Control", "Small policy networks where batch norm interacts badly with on-policy data but centred activations still help."),
        ApplicationCard("flask", 0xFF6366F1, "As SELU's Base", "SELU is ELU with two specific constants; understanding one is most of understanding the other."),
    ),
    takeaways = listOf(
        "Smooth everywhere, with a negative branch that saturates at −α rather than running away.",
        "Mean output 0.160 against ReLU's 0.399 — this, not dead units, is what it targets.",
        "Leaky ReLU does not fix the mean shift; the two solve different problems.",
        "It still saturates, just more slowly than sigmoid — a soft floor is still a floor.",
        "Costs an exponential per negative unit; the case for it weakens once you have normalisation layers.",
    ),
    crossLinks = listOf(
        CrossLink("selu", "SELU (Scaled ELU)"),
        CrossLink("relu", "ReLU (Rectified Linear Unit)"),
        CrossLink("leaky_relu", "Leaky ReLU"),
        CrossLink("batch_normalization", "Batch Normalization"),
    ),
)
