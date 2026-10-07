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

internal val rmsPropContent = TopicContent(
    topicId = "rmsprop",
    figure = Figure(
        caption = "The same dense gradient stream through both accumulators. AdaGrad's running sum only " +
            "grows, so its effective rate traces lr/√t all the way down to 0.0112 by step 2,000. " +
            "RMSprop's moving average can fall as well as rise, so it settles at 0.5000 — the raw " +
            "learning rate — and stays there. 44.7× apart on identical input.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    label = "RMSprop",
                    points = listOf(FigurePoint(0f, 1f), FigurePoint(1f, 1f)),
                ),
                FigureSeries(
                    label = "AdaGrad",
                    points = listOf(
                        FigurePoint(0.0005f, 1f),
                        FigurePoint(0.025f, 0.141f),
                        FigurePoint(0.10f, 0.0707f),
                        FigurePoint(0.25f, 0.0447f),
                        FigurePoint(0.50f, 0.0316f),
                        FigurePoint(1f, 0.0224f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            xLabel = "2,000 steps, gradient 1 every step",
            yLabel = "dense-feature rate (÷ lr)",
            markers = listOf(FigurePoint(1f, 0.0224f, "44.7× smaller", FigureTone.Warn)),
        ),
    ),
    whatIsIt = listOf(
        "RMSprop replaces AdaGrad's running sum of squared gradients with an exponential moving average: E[g²]_t = γ·E[g²]_{t-1} + (1−γ)·g_t², γ typically 0.9. A moving average can go back down as well as up, which is exactly what a running sum can't do — it fixes AdaGrad's stall by letting the accumulator forget old gradients instead of keeping every one of them forever.",
        "On the identical dense/sparse stream AdaGrad runs (gradient 1 every step, gradient 2 one step in ten), the fix is visible directly: by step 2,000 RMSprop's dense-feature rate has settled at 0.5000 — essentially the raw learning rate — and stays there. AdaGrad's has shrunk to 0.0112 over the same steps, 44.7× smaller for the identical gradient stream. RMSprop simply doesn't stall.",
        "That forgetting has a real cost, checked here rather than waved at: right after a sparse firing at t=200, RMSprop keeps only 1.28× the dense rate for the sparse feature — AdaGrad keeps 1.58×. The EMA's memory of the sparse feature's last big gradient decays between firings instead of compounding forever the way AdaGrad's sum does, so RMSprop fixes AdaGrad's core failure mode at the price of some of its core benefit.",
    ),
    steps = listOf(
        StepCard(1, "Square the Gradient", "g_t² at the current step, same input AdaGrad squares.", 0xFF0EA5E9),
        StepCard(2, "Update the EMA", "E[g²]_t = γ·E[g²]_{t-1} + (1−γ)·g_t² — γ=0.9, so 90% old, 10% new.", 0xFF3B82F6),
        StepCard(3, "Divide the Rate", "effective rate = lr/√(E[g²]_t + ε).", 0xFF8B5CF6),
        StepCard(4, "Compare to AdaGrad at t=2,000", "RMSprop: 0.5000 (flat). AdaGrad: 0.0112 (still falling) — 44.7x apart.", 0xFFF59E0B),
        StepCard(5, "Check the Sparse-Feature Cost", "RMSprop keeps 1.28x the dense rate for a sparse feature; AdaGrad keeps 1.58x.", 0xFFEC4899),
        StepCard(6, "See the Trade", "No stall, less memory — the EMA forgets what a running sum never does.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("EMA update", "E[g²]_t = γ·E[g²]_{t-1} + (1−γ)·g_t²", "γ=0.9 — a moving average, not a running sum."),
        FormulaEntry("Effective rate", "lr/√(E[g²]_t+ε)", "Bounded, because E[g²]_t converges rather than growing forever."),
        FormulaEntry("RMSprop at t=2,000", "rate = 0.5000", "Converged to essentially the raw learning rate — flat, not shrinking."),
        FormulaEntry("AdaGrad at t=2,000", "rate = 0.0112", "Still shrinking — 44.7x below RMSprop's rate on the identical stream."),
        FormulaEntry("RMSprop sparse boost at t=200", "1.28x dense", "Smaller than AdaGrad's own 1.58x on the same stream."),
        FormulaEntry("Why the boost is smaller", "EMA decays between firings", "A running sum never decays at all."),
    ),
    notationKey = listOf(
        NotationEntry("γ", "the EMA decay, 0.9 here — higher keeps more history, lower forgets faster"),
        NotationEntry("E[g²]_t", "the moving average of squared gradients, replacing AdaGrad's G_t"),
        NotationEntry("ε", "small constant preventing division by zero"),
        NotationEntry("stall", "AdaGrad's failure this EMA fixes — a running sum that can only grow"),
        NotationEntry("forgetting", "the EMA's own cost — old large gradients stop mattering after enough steps"),
        NotationEntry("steady state", "the value E[g²]_t converges to under a stationary gradient distribution"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "RMSprop doesn't stall where AdaGrad does",
            accentColor = 0xFF0EA5E9,
            code = """
                import math
                lr, gamma, eps = 0.5, 0.9, 1e-8
                E = 0.0
                for t in range(1, 2001):
                    g = 1.0  # constant dense gradient
                    E = gamma * E + (1 - gamma) * g * g
                    if t in (200, 2000):
                        print(t, "RMSprop rate:", round(lr / math.sqrt(E + eps), 4))
                # 200   RMSprop rate: 0.5000
                # 2000  RMSprop rate: 0.5000   <- flat; AdaGrad's own rate at t=2000 is 0.0112
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The sparse-feature cost, measured",
            accentColor = 0xFFEC4899,
            code = """
                import math
                lr, gamma, eps = 0.5, 0.9, 1e-8
                E_dense = E_sparse = 0.0
                for t in range(1, 201):
                    g_dense, g_sparse = 1.0, (2.0 if t % 10 == 0 else 0.0)
                    E_dense = gamma * E_dense + (1 - gamma) * g_dense ** 2
                    E_sparse = gamma * E_sparse + (1 - gamma) * g_sparse ** 2
                rate_dense = lr / math.sqrt(E_dense + eps)
                rate_sparse = lr / math.sqrt(E_sparse + eps)
                print(rate_sparse / rate_dense)
                # 1.276  -- AdaGrad's own ratio on the identical stream is 1.581
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "RNN Training", "Introduced (unpublished, Hinton's course notes) specifically to stabilize recurrent-network training."),
        ApplicationCard("trend", 0xFF3B82F6, "Non-Stationary Objectives", "The EMA adapts to changing gradient statistics over training instead of averaging over all of history."),
        ApplicationCard("check", 0xFF8B5CF6, "No Manual Rate Decay", "The bounded effective rate removes the need to hand-schedule a decay just to avoid AdaGrad's stall."),
        ApplicationCard("help", 0xFFEC4899, "One of Two Halves", "RMSprop only tracks the second moment; Adam adds the first-moment (momentum) half on top."),
    ),
    takeaways = listOf(
        "RMSprop tracks E[g²]_t as an exponential moving average (γ=0.9), not a running sum like AdaGrad's G_t.",
        "Because an EMA can decrease as well as increase, RMSprop's effective rate converges instead of shrinking forever.",
        "On a constant unit gradient, RMSprop's rate settles at 0.5000 by t=200 and stays there through t=2,000.",
        "AdaGrad's rate on the identical stream keeps falling — 0.0112 by t=2,000, 44.7x below RMSprop's.",
        "The cost: RMSprop keeps only 1.28x the dense rate for a sparse feature at t=200, against AdaGrad's 1.58x.",
        "That gap is the EMA forgetting the sparse feature's last firing between occurrences — a running sum never forgets.",
        "RMSprop is the second-moment half of Adam; Adam adds momentum's first-moment half on top of exactly this mechanism.",
    ),
    crossLinks = listOf(
        CrossLink("adagrad", "AdaGrad"),
        CrossLink("adam", "Adam (Adaptive Moment Estimation)"),
        CrossLink("momentum", "Momentum"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
    ),
)
