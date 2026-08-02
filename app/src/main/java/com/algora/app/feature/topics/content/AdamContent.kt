package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val adamContent = TopicContent(
    topicId = "adam",
    whatIsIt = listOf(
        "Adam combines momentum's first-moment EMA (m_t = β1·m_{t-1} + (1−β1)·g_t) and RMSprop's second-moment EMA (v_t = β2·v_{t-1} + (1−β2)·g_t², β1=0.9, β2=0.999) and steps by m_t/√v_t. Both EMAs start at zero, which biases early estimates toward zero — Adam's actual contribution beyond \"momentum plus RMSprop\" is the correction that fixes exactly that bias: m̂_t = m_t/(1−β1^t), v̂_t = v_t/(1−β2^t).",
        "Run a single constant gradient (g=2.0) through both EMAs and check the correction as an identity rather than an approximation: with a truly constant input, m_t = (1−β1^t)·g at every step — an exact geometric-series identity — so m̂_t = g exactly, at every t, not just asymptotically. The same holds for v̂_t = g². The corrected step ratio m̂_t/√v̂_t is exactly 1.000 from step 1 onward.",
        "Without correction, the uncorrected ratio m_t/√v_t starts at 3.162 (step 1), climbs to a peak of 6.569 around step 12, and is still 3.241 by step 100 — more than 3× the true value even after 100 steps, having first swung more than 6× past it. Correction isn't a minor early-training nicety; on this constant-gradient test it's the difference between the exact answer immediately and a value that's still measurably wrong 100 steps in.",
    ),
    steps = listOf(
        StepCard(1, "Update the First Moment", "m_t = β1·m_{t-1} + (1−β1)·g_t — momentum's EMA, β1=0.9.", 0xFF0EA5E9),
        StepCard(2, "Update the Second Moment", "v_t = β2·v_{t-1} + (1−β2)·g_t² — RMSprop's EMA, β2=0.999.", 0xFF3B82F6),
        StepCard(3, "Bias-Correct Both", "m̂_t = m_t/(1−β1^t), v̂_t = v_t/(1−β2^t).", 0xFF8B5CF6),
        StepCard(4, "Step by the Corrected Ratio", "w_t = w_{t-1} − lr·m̂_t/(√v̂_t + ε).", 0xFFF59E0B),
        StepCard(5, "Verify the Identity", "On a constant gradient, m̂_t/√v̂_t = 1.000 exactly, at every step.", 0xFFEC4899),
        StepCard(6, "Check Without Correction", "The uncorrected ratio peaks at 6.569 (step 12) before settling near 1.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("First moment", "m_t = 0.9·m_{t-1} + 0.1·g_t", "Momentum's own update, reused."),
        FormulaEntry("Second moment", "v_t = 0.999·v_{t-1} + 0.001·g_t²", "RMSprop's own update, reused."),
        FormulaEntry("Bias correction", "m̂_t = m_t/(1−0.9^t), v̂_t = v_t/(1−0.999^t)", "Recovers the true value even in early steps."),
        FormulaEntry("Corrected ratio", "m̂_t/√v̂_t = 1.000", "Exact at every t on a constant gradient — an identity."),
        FormulaEntry("Uncorrected ratio, step 1", "3.162", "Already off by more than 3x before any correction."),
        FormulaEntry("Uncorrected ratio, peak", "6.569 at t=12", "The overshoot correction removes entirely."),
    ),
    notationKey = listOf(
        NotationEntry("β1", "first-moment decay, 0.9 — same coefficient momentum uses"),
        NotationEntry("β2", "second-moment decay, 0.999 — slower than β1, so v_t changes gently"),
        NotationEntry("m_t, v_t", "the raw (uncorrected) first and second moment EMAs"),
        NotationEntry("m̂_t, v̂_t", "the bias-corrected moments — divided by (1−β^t)"),
        NotationEntry("bias", "the tendency of an EMA started at zero to underestimate its true value early on"),
        NotationEntry("ε", "small constant preventing division by zero, typically 1e-8"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The correction identity, checked at every step",
            accentColor = 0xFF0EA5E9,
            code = """
                b1, b2, g = 0.9, 0.999, 2.0
                m = v = 0.0
                for t in range(1, 101):
                    m = b1 * m + (1 - b1) * g
                    v = b2 * v + (1 - b2) * g * g
                    m_hat = m / (1 - b1 ** t)
                    v_hat = v / (1 - b2 ** t)
                    if t in (1, 10, 100):
                        print(t, "corrected:", round(m_hat / v_hat ** 0.5, 6))
                # 1    corrected: 1.000000
                # 10   corrected: 1.000000
                # 100  corrected: 1.000000   <- exact at every t, not just asymptotically
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Without correction, the same run overshoots",
            accentColor = 0xFFEC4899,
            code = """
                b1, b2, g = 0.9, 0.999, 2.0
                m = v = 0.0
                peak = (0, 0.0)
                for t in range(1, 101):
                    m = b1 * m + (1 - b1) * g
                    v = b2 * v + (1 - b2) * g * g
                    ratio = m / v ** 0.5
                    if ratio > peak[1]: peak = (t, ratio)
                print("peak:", peak)          # (12, 6.5685)
                print("at t=1:", 0.2 / 0.004 ** 0.5)   # 3.1623
                print("at t=100:", round(ratio, 4))     # 3.2408, still off from 1.000
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Default Deep-Learning Optimizer", "The most common default for training neural networks from scratch, across vision, NLP and RL."),
        ApplicationCard("trend", 0xFF3B82F6, "Fast Early Convergence", "The bias correction means good, well-scaled steps from the very first update, not after warming up."),
        ApplicationCard("check", 0xFF8B5CF6, "Robust to Rate Choice", "Adapts per-parameter, so it's less sensitive to a suboptimal global learning rate than plain SGD."),
        ApplicationCard("help", 0xFFEC4899, "Not Always Best", "On clean, well-conditioned problems, tuned SGD+momentum can generalize better — see gradient_descent_variants."),
    ),
    takeaways = listOf(
        "Adam combines momentum's first-moment EMA and RMSprop's second-moment EMA, then steps by m̂_t/√v̂_t.",
        "Both raw EMAs start at zero, biasing early estimates toward zero — bias correction divides by (1−β^t) to fix it.",
        "On a constant gradient, the corrected ratio m̂_t/√v̂_t is exactly 1.000 at every single step — an identity, verified.",
        "Without correction, the same ratio starts at 3.162, peaks at 6.569 around step 12, and is still 3.241 by step 100.",
        "Correction isn't asymptotic cleanup — it's the difference between the exact answer immediately and a measurably wrong one 100 steps in.",
        "β1=0.9 and β2=0.999 are momentum's and RMSprop's own coefficients, reused rather than newly invented.",
        "Adam inherits both halves' behavior: momentum's compounding on consistent gradients, RMSprop's per-parameter adaptive scale.",
    ),
    crossLinks = listOf(
        CrossLink("momentum", "Momentum"),
        CrossLink("rmsprop", "RMSprop"),
        CrossLink("adamw", "AdamW (Decoupled Weight Decay)"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
    ),
)
