package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val adaGradContent = TopicContent(
    topicId = "adagrad",
    whatIsIt = listOf(
        "AdaGrad gives every parameter its own learning rate, scaled by the inverse square root of every squared gradient that parameter has ever received: G_t += g_t², effective rate = lr/√(G_t+ε). A parameter with large or frequent gradients accumulates a large G_t and gets a small effective rate; one with small or rare gradients keeps a larger rate. The motivating case is a rare feature that matters a lot when it does show up — AdaGrad is supposed to keep it from being drowned out by a dense feature that updates every step.",
        "Run a dense feature (gradient magnitude 1, every step) beside a sparse one (magnitude 2, one step in ten) for 200 steps: the dense feature accumulates G=200 (200 steps of 1²), the sparse one only G=80 (20 firings of 2²) — fewer, bigger gradients still sum to less here. That flips into the effective rate: dense settles to 0.0354, sparse keeps 0.0559 — 1.58× the dense rate, exactly the rare-feature protection AdaGrad is built for.",
        "But G_t only accumulates — it never resets, and it never forgets. Run the dense feature 2,000 steps instead of 200 and its rate has shrunk to 0.0112, exactly lr/√t because a constant unit gradient makes G_t = t. The rate keeps falling long after the loss it's supposed to be driving has flattened out, and there's no mechanism inside AdaGrad to stop it — which is the exact problem RMSprop was built to fix.",
    ),
    steps = listOf(
        StepCard(1, "Square and Accumulate", "G_t = G_{t-1} + g_t² — a running sum, never reset.", 0xFF0EA5E9),
        StepCard(2, "Divide the Rate", "effective rate = lr/√(G_t+ε).", 0xFF3B82F6),
        StepCard(3, "Run Dense vs Sparse", "Gradient 1 every step vs gradient 2 one step in ten, 200 steps.", 0xFF8B5CF6),
        StepCard(4, "Compare the Accumulators", "G_dense=200, G_sparse=80 — fewer, bigger gradients still sum to less.", 0xFFF59E0B),
        StepCard(5, "Compare the Rates", "0.0354 vs 0.0559 — the sparse feature keeps 1.58x the dense rate.", 0xFFEC4899),
        StepCard(6, "Run to 2,000 Steps", "Dense rate falls to 0.0112 — exactly lr/√t, and it never stops falling.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Accumulator", "G_t = G_{t-1} + g_t²", "Monotonically non-decreasing — never resets."),
        FormulaEntry("Effective rate", "lr/√(G_t+ε)", "Per-parameter, shrinks as its own G_t grows."),
        FormulaEntry("Dense at t=200", "G=200, rate=0.0354", "200 steps of gradient magnitude 1."),
        FormulaEntry("Sparse at t=200", "G=80, rate=0.0559", "20 firings of gradient magnitude 2 — 1.58x the dense rate."),
        FormulaEntry("Dense at t=2,000", "rate=0.0112", "= 0.5/√2000 exactly, matching the lr/√t identity."),
        FormulaEntry("Long-run ratio", "0.0354/0.0112 ≈ 3.16", "= √10, matching √(2000/200) exactly."),
    ),
    notationKey = listOf(
        NotationEntry("G_t", "the running sum of squared gradients for one parameter"),
        NotationEntry("ε", "a small constant (1e-8) preventing division by zero at G_t=0"),
        NotationEntry("dense feature", "updates every step, with a moderate gradient magnitude here"),
        NotationEntry("sparse feature", "updates rarely, with a larger magnitude when it does"),
        NotationEntry("effective rate", "lr divided by √(G_t+ε) — the actual per-parameter step scale"),
        NotationEntry("stall", "AdaGrad's failure mode: G_t keeps growing, so the rate can shrink to the point of no further progress"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The dense/sparse comparison, run for real",
            accentColor = 0xFF0EA5E9,
            code = """
                import math
                lr, eps = 0.5, 1e-8
                G_dense = G_sparse = 0.0

                for t in range(1, 2001):
                    g_dense = 1.0
                    g_sparse = 2.0 if t % 10 == 0 else 0.0
                    G_dense += g_dense ** 2
                    if g_sparse: G_sparse += g_sparse ** 2
                    if t in (200, 2000):
                        rate_dense = lr / math.sqrt(G_dense + eps)
                        rate_sparse = lr / math.sqrt(G_sparse + eps)
                        print(t, "dense:", round(rate_dense, 4), "sparse:", round(rate_sparse, 4))
                # 200   dense: 0.0354  sparse: 0.0559   <- sparse keeps 1.58x
                # 2000  dense: 0.0112  sparse: 0.0559   <- dense has kept shrinking; sparse hasn't fired since t=2000
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The stall is exact, not approximate",
            accentColor = 0xFFEC4899,
            code = """
                # With a constant unit gradient, G_t = t exactly, so the effective rate is
                # exactly lr / sqrt(t) -- checkable, not just observed:
                import math
                lr = 0.5
                assert abs(lr / math.sqrt(200) - 0.035355) < 1e-5
                assert abs(lr / math.sqrt(2000) - 0.011180) < 1e-5
                # The rate at t=2000 is sqrt(10) = 3.162x smaller than at t=200 -- and it will be
                # another 3.162x smaller by t=20000, with no floor built into the algorithm.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Sparse Features", "NLP and recommendation models with word/item embeddings that update rarely benefit most."),
        ApplicationCard("trend", 0xFF3B82F6, "No Rate Search", "The per-parameter scaling reduces how sensitive training is to the single global learning rate."),
        ApplicationCard("check", 0xFF8B5CF6, "Convex Guarantees", "AdaGrad's original convergence proof is for convex problems, where the accumulator's stall is less costly."),
        ApplicationCard("help", 0xFFEC4899, "Long Training Runs", "Rarely used unmodified for deep learning today — RMSprop and Adam exist specifically to fix the stall."),
    ),
    takeaways = listOf(
        "AdaGrad divides each parameter's rate by √(G_t+ε), where G_t is the running sum of that parameter's squared gradients.",
        "G_t never resets — it only accumulates, for the life of training.",
        "On a dense/sparse gradient stream, the sparse feature keeps 1.58x the dense feature's rate at t=200 (0.0559 vs 0.0354).",
        "That's not just \"rare gradients keep a higher rate\" — it depends on actual accumulated magnitude: G_dense=200, G_sparse=80.",
        "By t=2,000 the dense rate has fallen to 0.0112 — exactly lr/√t, an identity checked to the digit, not an approximation.",
        "The accumulator's monotonic growth means the rate keeps shrinking even after the loss has flattened — the stall RMSprop was built to fix.",
        "AdaGrad's convex-case convergence guarantee is real, but the stall is what limits its use in deep, non-convex training.",
    ),
    crossLinks = listOf(
        CrossLink("rmsprop", "RMSprop"),
        CrossLink("adam", "Adam (Adaptive Moment Estimation)"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("momentum", "Momentum"),
    ),
)
