package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val adamWContent = TopicContent(
    topicId = "adamw",
    whatIsIt = listOf(
        "The standard way to add L2 regularization to Adam is to fold it into the gradient before anything else happens: g'_t = g_t + λ·w. That decay term then flows through the same first- and second-moment EMAs as any gradient, which means it gets divided by √v̂_t exactly like the data gradient does. AdamW's fix is to skip that entirely — apply the decay directly to the weight, outside the adaptive step: w_t = w_t − lr·(m̂_t/√v̂_t) − lr·λ·w_t.",
        "The difference is not cosmetic. Warm up two parameters for 300 steps with different gradient histories — magnitude 5 for one, 0.5 for the other — so their accumulated second moments land 100× apart (v=6.482 vs v=0.0648). Now take one weight-decay-only step (no new data gradient) under each method. L2-in-Adam decays the small-v parameter 10.0× faster than the large-v one — identical weight decay λ, wildly unequal effect, purely because the decay term got divided by different v's.",
        "AdamW's decoupled version applies exactly lr·λ = 0.010 to both parameters, regardless of their gradient history — a ratio of 1.000, not 10.0. This is the entire argument from the paper that introduced AdamW: L2 regularization and weight decay are the same thing in plain SGD, but Adam's per-parameter adaptive scaling breaks that equivalence, and decoupling restores it.",
    ),
    steps = listOf(
        StepCard(1, "Warm Up Two Parameters", "300 steps each at gradient magnitude 5 and 0.5 — different second-moment histories.", 0xFF0EA5E9),
        StepCard(2, "Check the Accumulators", "v_large=6.482, v_small=0.0648 — 100x apart.", 0xFF3B82F6),
        StepCard(3, "L2-in-Adam: Fold Decay Into g", "g' = λ·w, then run it through the same m/v EMAs as any gradient.", 0xFF8B5CF6),
        StepCard(4, "See the Unequal Effect", "Small-v parameter decays 10.0x faster than large-v — same λ, different result.", 0xFFF59E0B),
        StepCard(5, "AdamW: Decouple the Decay", "w −= lr·λ·w directly, bypassing v entirely.", 0xFFEC4899),
        StepCard(6, "Check It's Equal Now", "Both parameters shrink by exactly lr·λ = 0.010 — ratio 1.000.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("L2-in-Adam", "g'_t = g_t + λw, then standard Adam", "The decay term is divided by √v̂_t like any gradient."),
        FormulaEntry("AdamW", "w_t −= lr·(m̂_t/√v̂_t) + lr·λ·w_t", "Decay applied directly — never divided by v."),
        FormulaEntry("Accumulator ratio", "v_large/v_small = 100.0", "From gradient histories of magnitude 5 vs 0.5."),
        FormulaEntry("L2-in-Adam decay ratio", "step_small/step_large = 10.0", "= √(v_large/v_small) — unequal decay from identical λ."),
        FormulaEntry("AdamW decay ratio", "step_small/step_large = 1.000", "Exactly equal, regardless of v."),
        FormulaEntry("AdamW step size", "lr·λ = 0.1 × 0.1 = 0.010", "Same for both parameters, by construction."),
    ),
    notationKey = listOf(
        NotationEntry("λ", "the weight-decay coefficient"),
        NotationEntry("v", "the accumulated second moment — different per parameter depending on gradient history"),
        NotationEntry("L2-in-Adam", "adding λw to the gradient before the Adam update — the older, coupled approach"),
        NotationEntry("decoupled decay", "AdamW's fix — the decay term bypasses the adaptive per-parameter scaling"),
        NotationEntry("m̂_t, v̂_t", "Adam's bias-corrected first and second moments — see Adam"),
        NotationEntry("high-v parameter", "one with a large or frequent gradient history"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building two different gradient histories",
            accentColor = 0xFF0EA5E9,
            code = """
                b1, b2 = 0.9, 0.999
                def warmup_v(grad_value, steps=300):
                    m = v = 0.0
                    for _ in range(steps):
                        m = b1 * m + (1 - b1) * grad_value
                        v = b2 * v + (1 - b2) * grad_value ** 2
                    return v

                v_large = warmup_v(5.0)     # 6.482
                v_small = warmup_v(0.5)     # 0.0648
                print(v_large / v_small)    # 100.0
            """.trimIndent(),
        ),
        CodeBlock(
            title = "One decay-only step, both methods",
            accentColor = 0xFFEC4899,
            code = """
                lr, wd, eps = 0.1, 0.1, 1e-8

                def l2_in_adam_step(v_prev, w, t=301):
                    g_eff = wd * w                       # decay folded into the gradient
                    m = (1 - b1) * g_eff                  # m restarts at 0 -- no data gradient this step
                    v = b2 * v_prev + (1 - b2) * g_eff ** 2
                    m_hat, v_hat = m / (1 - b1 ** t), v / (1 - b2 ** t)
                    return lr * m_hat / (v_hat ** 0.5 + eps)

                def adamw_step(w):
                    return lr * wd * w                    # never touches v

                step_large, step_small = l2_in_adam_step(v_large, 1.0), l2_in_adam_step(v_small, 1.0)
                print(step_small / step_large)             # 10.0  -- unequal
                print(adamw_step(1.0) / adamw_step(1.0))   # 1.0   -- equal, by construction
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Modern Default", "AdamW, not plain Adam, is the standard optimizer for training transformers and most modern architectures."),
        ApplicationCard("trend", 0xFF3B82F6, "Consistent Regularization", "Every parameter is decayed by the same fraction of its own magnitude, regardless of gradient history."),
        ApplicationCard("check", 0xFF8B5CF6, "Better Generalization", "The paper that introduced AdamW measured improved test performance over L2-in-Adam at matched hyperparameters."),
        ApplicationCard("help", 0xFFEC4899, "Only Matters With Adaptive Optimizers", "In plain SGD, L2 regularization and weight decay are already mathematically identical — decoupling adds nothing there."),
    ),
    takeaways = listOf(
        "L2-in-Adam folds weight decay into the gradient, so it gets divided by √v̂_t exactly like the data gradient.",
        "AdamW applies decay directly to the weight, lr·λ·w, bypassing the adaptive per-parameter scale entirely.",
        "Two parameters with second-moment histories 100x apart (v=6.482 vs 0.0648) show the difference directly.",
        "L2-in-Adam decays the small-v parameter 10.0x faster than the large-v one — identical λ, unequal effect.",
        "AdamW decays both parameters by exactly the same fraction, lr·λ = 0.010 — ratio 1.000, not 10.0.",
        "The 10.0x ratio is exact: it equals √(v_large/v_small) = √100, because L2-in-Adam's decay step scales as 1/√v.",
        "AdamW's argument is that L2 and weight decay are equivalent in plain SGD, and Adam's own adaptivity is what breaks that equivalence.",
    ),
    crossLinks = listOf(
        CrossLink("adam", "Adam (Adaptive Moment Estimation)"),
        CrossLink("regularization", "L1 / L2 Regularization"),
        CrossLink("dropout", "Dropout"),
        CrossLink("lr_schedulers", "Learning Rate Schedulers"),
    ),
)
