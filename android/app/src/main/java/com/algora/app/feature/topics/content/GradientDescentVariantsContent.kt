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

internal val gradientDescentVariantsContent = TopicContent(
    topicId = "gradient_descent_variants",
    figure = Figure(
        caption = "The ravine that motivates every variant on this page: steep across, shallow along. " +
            "Plain SGD spends its step budget crossing the walls and barely advances down the floor. " +
            "Momentum accumulates the component that keeps pointing the same way and cancels the one " +
            "that reverses every step — same gradients, same learning rate, different bookkeeping.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    label = "ravine floor",
                    points = listOf(FigurePoint(0f, 0.5f), FigurePoint(1f, 0.5f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    label = "plain SGD",
                    points = listOf(
                        FigurePoint(0f, 0.95f),
                        FigurePoint(0.08f, 0.12f),
                        FigurePoint(0.16f, 0.85f),
                        FigurePoint(0.24f, 0.18f),
                        FigurePoint(0.32f, 0.78f),
                        FigurePoint(0.40f, 0.24f),
                        FigurePoint(0.50f, 0.72f),
                        FigurePoint(0.60f, 0.30f),
                        FigurePoint(0.70f, 0.68f),
                        FigurePoint(0.80f, 0.35f),
                        FigurePoint(0.90f, 0.64f),
                        FigurePoint(1f, 0.40f),
                    ),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    label = "with momentum",
                    points = listOf(
                        FigurePoint(0f, 0.95f),
                        FigurePoint(0.10f, 0.62f),
                        FigurePoint(0.25f, 0.53f),
                        FigurePoint(0.50f, 0.50f),
                        FigurePoint(1f, 0.50f),
                    ),
                ),
            ),
            xLabel = "one step budget, one learning rate",
            yLabel = "position across the ravine",
        ),
    ),
    whatIsIt = listOf(
        "Gradient descent variants differ in how much data each step uses and how the step size adapts — trading noise, speed, and memory.",
        "Modern optimizers like Adam add momentum and per-parameter learning rates on top of plain SGD, making deep networks train far faster and more reliably.",
        "Two failure modes motivate everything here. Plain SGD crawls along a ravine — a direction where the loss is steep across and shallow along — oscillating across the walls while barely advancing; momentum accumulates the consistent component and cancels the oscillating one. And a single global learning rate cannot suit both a frequently-updated embedding and a rarely-touched one; adaptive methods give every parameter its own effective step.",
    ),
    steps = listOf(
        StepCard(1, "Batch vs Stochastic vs Mini-Batch", "Use all data, one sample, or a small batch per step; mini-batch is the practical default.", 0xFF818CF8),
        StepCard(2, "Momentum", "Accumulate a velocity of past gradients to power through flat and noisy regions.", 0xFF60A5FA),
        StepCard(3, "Adaptive Rates", "AdaGrad/RMSProp scale each parameter's step by its recent gradient magnitude.", 0xFF10B981),
        StepCard(4, "Adam", "Combine momentum and adaptive rates — the go-to optimizer for most deep nets.", 0xFFF59E0B),
        StepCard(5, "Correct the Startup Bias", "m and v start at zero, so early steps are biased toward it; Adam divides by (1 − βᵗ) to undo that.", 0xFF8B5CF6),
        StepCard(6, "Schedule the Learning Rate", "Warmup then cosine decay is now standard — the schedule often matters more than the optimizer.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("SGD", "θ := θ − α·∇θ", "Step opposite the (mini-batch) gradient."),
        FormulaEntry("Momentum", "v := βv + ∇θ; θ := θ − α·v", "Velocity smooths the trajectory."),
        FormulaEntry("RMSProp", "v := βv + (1−β)(∇θ)²; θ := θ − α∇/(√v+ε)", "Per-parameter scaling by recent gradient size."),
        FormulaEntry("Adam", "m, v estimates → θ := θ − α·m̂/(√v̂+ε)", "Momentum plus adaptive scaling."),
        FormulaEntry("Bias correction", "m̂ = m/(1−β₁ᵗ),  v̂ = v/(1−β₂ᵗ)", "Matters most in the first few hundred steps: uncorrected, m/√v is ≈ 3.16× too large at step 1."),
        FormulaEntry("AdamW", "θ := θ − α·m̂/(√v̂+ε) − αλθ", "Decoupled weight decay — not the same as L2 inside the gradient."),
    ),
    notationKey = listOf(
        NotationEntry("α", "learning rate (step size)"),
        NotationEntry("mini-batch", "small subset of data per update"),
        NotationEntry("β", "momentum / decay coefficient"),
        NotationEntry("β₁, β₂", "Adam's moment decays — 0.9 and 0.999 by default"),
        NotationEntry("ε", "small constant, ≈1e-8, guarding the division"),
        NotationEntry("warmup", "ramping α up from ~0 over the first steps to stabilize early training"),
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
        CodeBlock(
            title = "Adam from scratch, one step",
            accentColor = 0xFF10B981,
            code = """
                class Adam(
                    private val size: Int,
                    private val lr: Double = 1e-3,
                    private val beta1: Double = 0.9,
                    private val beta2: Double = 0.999,
                    private val eps: Double = 1e-8,
                ) {
                    private val m = DoubleArray(size)      // first moment: momentum
                    private val v = DoubleArray(size)      // second moment: per-parameter scale
                    private var t = 0

                    fun step(params: DoubleArray, grads: DoubleArray) {
                        t++
                        for (i in 0 until size) {
                            m[i] = beta1 * m[i] + (1 - beta1) * grads[i]
                            v[i] = beta2 * v[i] + (1 - beta2) * grads[i] * grads[i]

                            // Both moments start at 0, which biases m and v toward zero (v far more than m, so uncorrected early steps are too LARGE).
                            val mHat = m[i] / (1 - Math.pow(beta1, t.toDouble()))
                            val vHat = v[i] / (1 - Math.pow(beta2, t.toDouble()))

                            // Large recent gradients shrink the step; tiny ones enlarge it.
                            params[i] -= lr * mHat / (Math.sqrt(vHat) + eps)
                        }
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Training Deep Nets", "Optimizer choice is a core lever on convergence speed and final accuracy."),
        ApplicationCard("chart", 0xFF60A5FA, "Large-Scale Learning", "Mini-batch SGD makes training on massive datasets tractable and GPU-friendly."),
        ApplicationCard("bulb", 0xFF10B981, "Escaping Bad Minima", "Noise and momentum help skip saddle points and sharp minima."),
        ApplicationCard("book", 0xFFF59E0B, "Transformer Training", "AdamW with warmup and cosine decay is effectively the standard recipe for language models."),
    ),
    takeaways = listOf(
        "Batch/stochastic/mini-batch trade gradient accuracy against speed and noise.",
        "Momentum accelerates descent and dampens oscillation across a ravine.",
        "Adaptive methods (RMSProp, Adam) give each parameter its own effective learning rate.",
        "Bias correction removes the zero-initialization bias in m and v; without it the first steps are far too large, because v (β₂ = 0.999) is underestimated much more than m.",
        "AdamW's decoupled weight decay is not equivalent to adding L2 to the gradient — with adaptive scaling the two differ, and AdamW is the one that generalizes.",
        "Adam is the default, but well-tuned SGD+momentum can generalize better.",
    ),
    crossLinks = listOf(
        CrossLink("backpropagation", "Backpropagation"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("batch_normalization", "Batch Normalization"),
    ),
)
