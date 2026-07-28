package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val sigmoidContent = TopicContent(
    topicId = "sigmoid",
    whatIsIt = listOf(
        "σ(z) = 1/(1+e⁻ᶻ) takes any real number and returns something in (0, 1). It is smooth, monotone, and its output reads directly as a probability — which is why it was the default hidden activation for three decades and why it remains exactly the right choice as the output of a binary classifier.",
        "Its problem is the derivative. σ′(z) = σ(z)(1−σ(z)) peaks at exactly 0.25, at z = 0, and falls away quickly on both sides. Backpropagation multiplies one such factor per layer, so a deep sigmoid stack multiplies the gradient by at most a quarter per layer before the weights are even considered — the simulation runs twenty layers and measures the first layer's gradient at around 10¹¹ times weaker than the last's.",
        "It has a second, subtler flaw that motivated tanh: it is not zero-centred. Measured over standard normal input the mean output is 0.497, so every activation a downstream unit sees is positive. That makes all the weights in that unit's gradient share a sign, so an update can only move them all up or all down, and the path to the minimum becomes a zig-zag. Worth noting that saturation is not a fixed property of the curve but of how wide the pre-activations are: at a pre-activation standard deviation of 1 essentially nothing is saturated, and at 16 more than three quarters is — which is why the same activation can look fine in a shallow well-scaled network and stall a deep one.",
    ),
    steps = listOf(
        StepCard(1, "Squash to (0, 1)", "Any real input, a bounded output. The bounding is what makes it read as a probability.", 0xFFF59E0B),
        StepCard(2, "Note the Derivative Ceiling", "σ′ ≤ 0.25. Nothing about the input can raise it.", 0xFFFBBF24),
        StepCard(3, "Watch It Compound", "One factor per layer. Twenty layers of at-most-a-quarter is the vanishing gradient.", 0xFFEC4899),
        StepCard(4, "Check Zero-Centredness", "Mean output ~0.5, never negative. Downstream gradients all share a sign.", 0xFF8B5CF6),
        StepCard(5, "Watch Saturation Grow", "Flat-tail fraction rises with pre-activation width, so it worsens as training proceeds.", 0xFF6366F1),
        StepCard(6, "Use It Where It Fits", "Binary outputs and gates. Not hidden layers.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "σ(z) = 1/(1 + e⁻ᶻ)", "Also the logistic function; the two names are the same curve."),
        FormulaEntry("Derivative", "σ′(z) = σ(z)(1 − σ(z))", "Expressible in terms of the output, which makes it cheap."),
        FormulaEntry("Maximum slope", "σ′(0) = 0.25", "The ceiling the vanishing-gradient argument rests on."),
        FormulaEntry("Relation to tanh", "tanh(z) = 2σ(2z) − 1", "Tanh is a rescaled, recentred sigmoid."),
        FormulaEntry("Logit (inverse)", "z = ln(p/(1−p))", "Which is why a network's pre-softmax outputs are called logits."),
        FormulaEntry("Range", "(0, 1), asymptotic", "Never exactly 0 or 1, which matters for log-loss stability."),
    ),
    notationKey = listOf(
        NotationEntry("σ", "the sigmoid, or logistic, function"),
        NotationEntry("saturation", "the flat tails where the derivative is near zero"),
        NotationEntry("zero-centred", "outputs distributed around 0 rather than around 0.5"),
        NotationEntry("logit", "the pre-activation value; sigmoid's inverse"),
        NotationEntry("gate", "a value in (0,1) used to scale something else, as in an LSTM"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The ceiling, and why you never write sigmoid-then-log yourself",
            accentColor = 0xFFF59E0B,
            code = """
                import torch
                import torch.nn as nn

                z = torch.linspace(-8, 8, 1001, requires_grad=True)
                torch.sigmoid(z).sum().backward()
                print(z.grad.max().item())      # 0.25 exactly, at z = 0

                # Use the fused loss, always. BCEWithLogitsLoss applies the log-sum-exp trick
                # internally; sigmoid followed by BCELoss computes log(sigmoid(z)) in two steps and
                # loses precision (or returns inf) once |z| is large.
                logits = torch.tensor([-40.0, 40.0])
                target = torch.tensor([0.0, 1.0])
                print(nn.BCEWithLogitsLoss()(logits, target))          # 0.0, stable
                print(nn.BCELoss()(torch.sigmoid(logits), target))     # underflows
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Saturation is about scale, not about the function",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                def sigmoid(z):
                    return 1 / (1 + np.exp(-z))

                rng = np.random.default_rng(0)
                for std in (1, 2, 4, 8, 16):
                    z = rng.normal(scale=std, size=200_000)
                    d = sigmoid(z) * (1 - sigmoid(z))
                    print(std, round(float((d < 0.01).mean()), 3))
                # 1 -> 0.000, 2 -> 0.020, 4 -> 0.248, 8 -> 0.568, 16 -> 0.777
                #
                # Nothing about sigmoid changed between those rows. What changed is how wide the
                # pre-activations are -- which grows as weights grow during training, so a network
                # that starts healthy can saturate later. That is the case for normalisation
                # layers stated in one measurement.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFFF59E0B, "Binary Classification Output", "Where it is not a compromise but the correct choice: the output is a calibrated probability and the saturation is the calibration."),
        ApplicationCard("history", 0xFF8B5CF6, "LSTM and GRU Gates", "A value in (0, 1) is exactly the \"how much to let through\" semantics a gate needs."),
        ApplicationCard("chart", 0xFF6366F1, "Logistic Regression", "The same function, predating neural networks by a century, and the reason pre-activations are called logits."),
    ),
    takeaways = listOf(
        "Bounded in (0, 1), so the output reads directly as a probability.",
        "σ′ ≤ 0.25 is a hard ceiling, and it compounds once per layer.",
        "Not zero-centred: mean output ~0.5, which makes downstream gradients share a sign.",
        "Saturation grows with pre-activation width, so it gets worse as training proceeds.",
        "Still correct for binary outputs and gates; no longer a hidden-layer default.",
    ),
    crossLinks = listOf(
        CrossLink("tanh", "Tanh (Hyperbolic Tangent)"),
        CrossLink("relu", "ReLU (Rectified Linear Unit)"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
        CrossLink("logistic_regression", "Logistic Regression"),
    ),
)
