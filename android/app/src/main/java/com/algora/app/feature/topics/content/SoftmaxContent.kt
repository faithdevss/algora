package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val softmaxContent = TopicContent(
    topicId = "softmax",
    figure = Figure(
        caption = "Logits [1000, 1001, 1002] — one apart, and eᶻ on them overflows to NaN. Subtract the " +
            "maximum first, which cancels between numerator and denominator and changes the result by " +
            "exactly nothing, and they soften into these three. Note what a single logit of separation " +
            "buys: 0.665 against 0.245. Every output depends on every input, which is why the " +
            "derivative is a matrix whose rows sum to zero rather than a number.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("z = 1000", 0.090f),
                FigureBar("1001", 0.245f),
                FigureBar("1002", 0.665f, FigureTone.Accent),
            ),
            yLabel = "p — non-negative, sums to exactly 1",
        ),
    ),
    whatIsIt = listOf(
        "Softmax is the odd one out in this category, and the difference is structural rather than a matter of curve shape. Every other activation here maps one number to one number; softmax maps a whole vector to a whole vector. Each output depends on every input, because the denominator is a sum over all of them, and the results are non-negative and sum to exactly 1 — a probability distribution over classes rather than an independent score per class.",
        "Dividing the logits by a temperature before exponentiating controls how peaked the distribution is and nothing else about it. The simulation sweeps it: at T = 0.25 the top class takes 98.2% of the mass, at T = 5 only 32.2%, with entropy climbing from 0.09 to 1.37 nats. That is the sampling temperature in every text generator, and it is a property of the softmax rather than of the model — the logits never changed.",
        "Two implementation facts are not optional. First, computing eᶻ directly on large logits overflows: on [1000, 1001, 1002] the naive version returns NaN, while subtracting the maximum logit first — which changes the result by exactly nothing, since the constant cancels between numerator and denominator — gives the correct [0.090, 0.245, 0.665]. Second, its derivative is a matrix rather than a number: ∂pᵢ/∂zⱼ = pᵢ(δᵢⱼ − pⱼ), whose rows all sum to zero, because pushing one probability up must pull the others down. Paired with cross-entropy loss that whole matrix collapses to ŷ − y, and that cancellation is why the two are always implemented as one fused operation and why you should pass logits to a loss function rather than probabilities.",
    ),
    steps = listOf(
        StepCard(1, "Take the Whole Vector", "Every output depends on every input. Not element-wise.", 0xFFF59E0B),
        StepCard(2, "Subtract the Maximum", "Mathematically free, numerically mandatory.", 0xFFFBBF24),
        StepCard(3, "Exponentiate and Normalise", "Non-negative, summing to 1: a distribution over classes.", 0xFFEC4899),
        StepCard(4, "Set the Temperature", "Divide the logits first. Lower is peakier; it is the only thing T does.", 0xFF8B5CF6),
        StepCard(5, "Know the Jacobian", "pᵢ(δᵢⱼ − pⱼ), rows summing to zero.", 0xFF6366F1),
        StepCard(6, "Fuse It With the Loss", "Pass logits, never probabilities. The gradient simplifies to ŷ − y.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "pᵢ = e^{zᵢ} / Σⱼ e^{zⱼ}", "Non-negative, summing to 1."),
        FormulaEntry("Stable form", "pᵢ = e^{zᵢ−m} / Σⱼ e^{zⱼ−m}, m = max z", "Identical result, no overflow."),
        FormulaEntry("Temperature", "pᵢ = e^{zᵢ/T} / Σⱼ e^{zⱼ/T}", "T → 0 gives argmax, T → ∞ gives uniform."),
        FormulaEntry("Jacobian", "∂pᵢ/∂zⱼ = pᵢ(δᵢⱼ − pⱼ)", "A full matrix; rows sum to zero."),
        FormulaEntry("With cross-entropy", "∂L/∂zᵢ = pᵢ − yᵢ", "The matrix cancels. This is why they are fused."),
        FormulaEntry("Shift invariance", "softmax(z + c) = softmax(z)", "The property the stable form exploits."),
        FormulaEntry("Binary case", "softmax over 2 logits ≡ sigmoid of their difference", "Sigmoid is the two-class special case."),
    ),
    notationKey = listOf(
        NotationEntry("logits", "the raw pre-softmax scores; unbounded, uncalibrated"),
        NotationEntry("temperature T", "divides the logits; controls peakedness only"),
        NotationEntry("log-sum-exp", "the stable way to compute the log denominator"),
        NotationEntry("δᵢⱼ", "Kronecker delta: 1 when i = j, else 0"),
        NotationEntry("label smoothing", "targets of 1−ε instead of 1, since softmax cannot reach 1"),
        NotationEntry("Gumbel-softmax", "a differentiable relaxation of sampling from this distribution"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Overflow, and the one-line fix every library already applies",
            accentColor = 0xFFF59E0B,
            code = """
                import numpy as np

                def naive(z):
                    e = np.exp(z)
                    return e / e.sum()

                def stable(z):
                    e = np.exp(z - z.max())     # shift-invariant: the constant cancels
                    return e / e.sum()

                z = np.array([1000.0, 1001.0, 1002.0])
                print(naive(z))     # [nan nan nan] -- exp(1000) overflows
                print(stable(z))    # [0.09  0.2447 0.6652]

                # Which is also why you pass LOGITS to a loss, not probabilities:
                import torch, torch.nn as nn
                logits = torch.tensor([[1000.0, 1001.0, 1002.0]])
                target = torch.tensor([2])
                print(nn.CrossEntropyLoss()(logits, target))              # fine
                print(nn.NLLLoss()(torch.log(torch.softmax(logits, -1)), target))  # fragile
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Temperature is the only knob, and it is not the model",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch

                logits = torch.tensor([2.0, 1.0, 0.1, -0.5])
                for T in (0.25, 0.5, 1.0, 2.0, 5.0):
                    p = torch.softmax(logits / T, dim=-1)
                    entropy = -(p * p.log()).sum()
                    print(T, p.round(decimals=3).tolist(), round(entropy.item(), 3))
                # 0.25 -> [0.982, 0.018, 0.0, 0.0]     entropy 0.095
                # 5.0  -> [0.322, 0.263, 0.220, 0.195] entropy 1.368

                # The logits are identical in every row. Temperature is a decoding choice, not a
                # property of the model -- worth being clear about when someone reports that a
                # model "became more creative".
                #
                # Two related notes. Softmax outputs are famously over-confident and usually need
                # calibration (temperature scaling on a validation set is the standard fix, and it
                # is the same T). And it can never output exactly 1, which is why one-hot targets
                # push logits toward infinity and why label smoothing exists.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFFF59E0B, "Multi-Class Classification", "The standard output layer, fused with cross-entropy in every framework."),
        ApplicationCard("book", 0xFF8B5CF6, "Language Model Decoding", "Over the vocabulary at every step; temperature, top-k and nucleus sampling all operate on this distribution."),
        ApplicationCard("share", 0xFF6366F1, "Attention Weights", "Attention is a softmax over scores — the mechanism that makes each token's contribution a normalised share."),
        ApplicationCard("chart", 0xFF10B981, "Knowledge Distillation", "A high-temperature softmax exposes the teacher's relative beliefs about wrong classes, which is the signal being transferred."),
    ),
    takeaways = listOf(
        "It maps a vector to a vector: every output depends on every input.",
        "Subtract the max before exponentiating — mathematically free, and the difference between a number and NaN.",
        "Temperature controls peakedness only, and it is a decoding choice rather than a model property.",
        "Its derivative is a Jacobian whose rows sum to zero, since the outputs are constrained to sum to 1.",
        "Fused with cross-entropy the gradient is just ŷ − y, which is why you always pass logits to the loss.",
    ),
    crossLinks = listOf(
        CrossLink("sigmoid", "Sigmoid"),
        CrossLink("attention", "Attention"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
