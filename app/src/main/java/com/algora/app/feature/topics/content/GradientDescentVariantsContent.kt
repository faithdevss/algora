package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gradientDescentVariantsContent = TopicContent(
    topicId = "gradient_descent_variants",
    whatIsIt = listOf(
        "Gradient descent variants differ in how much data each step uses and how the step size adapts — trading noise, speed, and memory.",
        "Modern optimizers like Adam add momentum and per-parameter learning rates on top of plain SGD, making deep networks train far faster and more reliably.",
    ),
    steps = listOf(
        StepCard(1, "Batch vs Stochastic vs Mini-Batch", "Use all data, one sample, or a small batch per step; mini-batch is the practical default.", 0xFF818CF8),
        StepCard(2, "Momentum", "Accumulate a velocity of past gradients to power through flat and noisy regions.", 0xFF60A5FA),
        StepCard(3, "Adaptive Rates", "AdaGrad/RMSProp scale each parameter's step by its recent gradient magnitude.", 0xFF10B981),
        StepCard(4, "Adam", "Combine momentum and adaptive rates — the go-to optimizer for most deep nets.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("SGD", "θ := θ − α·∇θ", "Step opposite the (mini-batch) gradient."),
        FormulaEntry("Momentum", "v := βv + ∇θ; θ := θ − α·v", "Velocity smooths the trajectory."),
        FormulaEntry("Adam", "m, v estimates → θ := θ − α·m̂/(√v̂+ε)", "Momentum plus adaptive scaling."),
    ),
    notationKey = listOf(
        NotationEntry("α", "learning rate (step size)"),
        NotationEntry("mini-batch", "small subset of data per update"),
        NotationEntry("β", "momentum / decay coefficient"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Choosing an optimizer (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.optim as optim

                sgd  = optim.SGD(model.parameters(), lr=0.01, momentum=0.9)
                adam = optim.Adam(model.parameters(), lr=1e-3)   # common default
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Training Deep Nets", "Optimizer choice is a core lever on convergence speed and final accuracy."),
        ApplicationCard("chart", 0xFF60A5FA, "Large-Scale Learning", "Mini-batch SGD makes training on massive datasets tractable and GPU-friendly."),
        ApplicationCard("bulb", 0xFF10B981, "Escaping Bad Minima", "Noise and momentum help skip saddle points and sharp minima."),
    ),
    takeaways = listOf(
        "Batch/stochastic/mini-batch trade gradient accuracy against speed and noise.",
        "Momentum accelerates descent and dampens oscillation.",
        "Adaptive methods (RMSProp, Adam) give each parameter its own effective learning rate.",
        "Adam is the default, but well-tuned SGD+momentum can generalize better.",
    ),
    crossLinks = listOf(
        CrossLink("backpropagation", "Backpropagation"),
        CrossLink("linear_regression", "Linear Regression"),
    ),
)
