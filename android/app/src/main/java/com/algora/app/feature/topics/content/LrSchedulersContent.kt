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

internal val lrSchedulersContent = TopicContent(
    topicId = "lr_schedulers",
    figure = Figure(
        caption = "The four schedules the page's lab compares, drawn over its 60 steps against the " +
            "one number that bounds them all: on the bowl ½(w₁² + 20·w₂²) the steep axis diverges " +
            "once lr passes 2/20 = 0.1, the top of this axis. The constant rate sits at a safe " +
            "0.03 throughout. Step decay starts at 0.09 — just under the limit — and halves every " +
            "15 steps, so its shape is a staircase of plateaus. Cosine starts at the same 0.09 and " +
            "glides to zero, spending most of its budget early and almost none at the end. Warmup " +
            "+ cosine is the transformer default: five steps ramping up from 0.018 before the same " +
            "glide begins, so the very first updates — the ones made with the least information — " +
            "are the smallest. Read the curves as how much of the stability budget each schedule " +
            "spends at each step. After 60 steps that buys 0.827 for the constant, 0.171 for step " +
            "decay, 0.109 for cosine and 0.099 for warmup + cosine.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "constant",
                    listOf(FigurePoint(0f, 0.3f), FigurePoint(1f, 0.3f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "step decay",
                    listOf(
                        FigurePoint(0f, 0.9f), FigurePoint(0.237f, 0.9f), FigurePoint(0.254f, 0.45f),
                        FigurePoint(0.492f, 0.45f), FigurePoint(0.508f, 0.225f), FigurePoint(0.746f, 0.225f),
                        FigurePoint(0.763f, 0.112f), FigurePoint(1f, 0.112f),
                    ),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    "cosine",
                    listOf(
                        FigurePoint(0f, 0.9f), FigurePoint(0.085f, 0.885f), FigurePoint(0.169f, 0.84f),
                        FigurePoint(0.254f, 0.768f), FigurePoint(0.339f, 0.675f), FigurePoint(0.424f, 0.566f),
                        FigurePoint(0.508f, 0.45f), FigurePoint(0.593f, 0.334f), FigurePoint(0.678f, 0.225f),
                        FigurePoint(0.763f, 0.132f), FigurePoint(0.847f, 0.06f), FigurePoint(0.932f, 0.015f),
                        FigurePoint(1f, 0.001f),
                    ),
                    tone = FigureTone.Primary,
                ),
                FigureSeries(
                    "warmup + cosine",
                    listOf(
                        FigurePoint(0f, 0.18f), FigurePoint(0.085f, 0.9f), FigurePoint(0.169f, 0.882f),
                        FigurePoint(0.254f, 0.829f), FigurePoint(0.339f, 0.745f), FigurePoint(0.424f, 0.637f),
                        FigurePoint(0.508f, 0.514f), FigurePoint(0.593f, 0.386f), FigurePoint(0.678f, 0.263f),
                        FigurePoint(0.763f, 0.155f), FigurePoint(0.847f, 0.071f), FigurePoint(0.932f, 0.018f),
                        FigurePoint(1f, 0.001f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0f, 0.18f, "0.018"),
                FigurePoint(0f, 0.9f, "0.09", FigureTone.Warn),
            ),
            xLabel = "step 0 → 59",
            yLabel = "learning rate, 0 to 0.1 (limit)",
        ),
    ),
    whatIsIt = listOf(
        "A learning rate scheduler makes the rate itself a function of the step: constant, step decay (halved every fixed interval), cosine annealing (smoothly to zero, or to a floor), or warmup-then-decay (a short ramp before the decay begins). The lab runs all four for 60 steps on the ill-conditioned bowl ½(w₁² + 20·w₂²), starting from (−8, 1) at a loss of 42.0.",
        "The bowl sets the constraint. Its steep axis has curvature 20, so any rate above 2/20 = 0.1 diverges along it — and the flat axis, with curvature 1, wants the largest rate it can get. A constant rate has to be safe for the steep axis for the whole run, so the lab's constant sits at 0.03 and crawls along the flat one: it ends at **0.827**. The decaying schedules start near the limit at 0.09, move fast while the rate is high, and shrink it before it can do damage. Step decay (halved every 15 steps) ends at **0.171**, cosine (0.09 gliding to 0) at **0.109**, and warmup + cosine — five steps ramping up from 0.018, then the same glide — at **0.099**, about 8× below the constant rate.",
        "Warmup costs almost nothing here because this bowl is gentle at the start. Its real job is in a fresh network, where Adam's moment estimates are noisy for the first steps and one full-size update can throw training somewhere it never recovers from. Decay has a second job too, not shown in the lab: under gradient noise a fixed rate never stops bouncing around the minimum by an amount set by the rate, and shrinking the rate shrinks the bounce. The second code block below measures that on a deterministic disturbance — a constant 0.05 settles at 0.000223 and a rate decayed to 0.02 at 0.0000366, 6.1× lower.",
    ),
    steps = listOf(
        StepCard(1, "Pick a Schedule", "Constant, step decay, cosine, or warmup+cosine.", 0xFF0EA5E9),
        StepCard(2, "Find the Stability Limit", "On ½(w₁² + 20·w₂²) any rate above 2/20 = 0.1 diverges along the steep axis.", 0xFF3B82F6),
        StepCard(3, "Keep the Constant Safe", "A fixed rate must respect that limit for all 60 steps — 0.03 here — so the flat axis crawls.", 0xFF8B5CF6),
        StepCard(4, "Start Hot, Then Decay", "Step decay, cosine and warmup+cosine start near 0.09 and shrink, getting speed early and stability late.", 0xFFF59E0B),
        StepCard(5, "Compare Final Loss", "Constant 0.827 · step 0.171 · cosine 0.109 · warmup+cosine 0.099 after 60 steps.", 0xFFEC4899),
        StepCard(6, "Know What Warmup Is For", "Protecting the first steps of a fresh network, when Adam's estimates are noisiest.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Step decay", "lr_t = 0.09 · 0.5^⌊t/15⌋", "Halves every 15 steps."),
        FormulaEntry("Cosine annealing", "lr_t = lr_min + (lr_0−lr_min)·½(1+cos(π·t/T))", "0.09 to 0 over T = 60 steps in the lab."),
        FormulaEntry("Stability limit", "lr < 2/λ_max = 2/20 = 0.1", "What every schedule on this bowl has to respect at its peak."),
        FormulaEntry("Final loss, 60 steps", "0.827 · 0.171 · 0.109 · 0.099", "Constant 0.03 · step · cosine · warmup+cosine."),
        FormulaEntry("Noise floor, constant", "0.000223", "Steady-state loss under a persistent per-step disturbance (code block 2)."),
        FormulaEntry("Noise floor, decayed", "0.0000366", "6.1x lower — close to the predicted (0.05/0.02)² = 6.25."),
    ),
    notationKey = listOf(
        NotationEntry("lr_0", "the starting (or peak) learning rate"),
        NotationEntry("lr_min", "the floor a decaying schedule settles to, if any"),
        NotationEntry("warmup", "a linear ramp from 0 (or a small value) up to lr_0 before decay begins"),
        NotationEntry("noise floor", "the steady-state loss a fixed learning rate bounces around under persistent gradient noise"),
        NotationEntry("clean bowl", "the noise-free quadratic the lab runs on, where the only question is speed against stability"),
        NotationEntry("stability limit", "2/curvature — the largest rate a fixed-curvature axis tolerates before diverging"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The lab's four schedules on the bowl",
            accentColor = 0xFF0EA5E9,
            code = """
                import math
                curvature = [1.0, 20.0]          # flat axis, steep axis: stable while lr < 2/20
                def loss(w): return 0.5 * sum(c * x * x for c, x in zip(curvature, w))

                def run(lr_fn, steps=60, start=(-8.0, 1.0)):
                    w = list(start)
                    for t in range(steps):
                        lr = lr_fn(t)
                        w = [w[i] - lr * curvature[i] * w[i] for i in range(2)]
                    return loss(w)

                print(run(lambda t: 0.03))                                       # constant: 0.827
                print(run(lambda t: 0.09 * 0.5 ** (t // 15)))                    # step decay: 0.171
                print(run(lambda t: 0.045 * (1 + math.cos(math.pi * t / 60))))  # cosine: 0.109
                print(run(lambda t: 0.09 * (t + 1) / 5 if t < 5
                          else 0.045 * (1 + math.cos(math.pi * (t - 5) / 55))))  # warmup+cosine: 0.099
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Decay pays off once there's noise to shrink",
            accentColor = 0xFFEC4899,
            code = """
                import math
                def run_perturbed(lr_fn, steps=300, start=2.0, amp=0.6):
                    w = start
                    losses = []
                    for t in range(1, steps + 1):
                        perturb = amp * math.sin(t * 0.9) * math.cos(t * 0.31)  # fixed, deterministic
                        g = w + perturb
                        w -= lr_fn(t) * g
                        losses.append(0.5 * w * w)
                    return sum(losses[-40:]) / 40  # tail-average, i.e. the noise floor

                constant = run_perturbed(lambda t: 0.05)
                decayed  = run_perturbed(lambda t: 0.02 + 0.03 * 0.5 * (1 + math.cos(math.pi * (t - 1) / 300)))
                print(constant, decayed, constant / decayed)
                # 0.000223  0.0000366  6.09   -- close to (0.05/0.02)**2 = 6.25
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Mini-Batch Training", "Mini-batch gradients are noisy estimates of the true gradient — the exact regime where decay's benefit applies."),
        ApplicationCard("trend", 0xFF3B82F6, "Cosine Annealing", "A common modern default, often paired with warmup, for training large models from scratch."),
        ApplicationCard("check", 0xFF8B5CF6, "Warmup for Adam", "Adam's early-step estimates are noisiest before its EMAs stabilize — warmup avoids large steps during that window."),
        ApplicationCard("help", 0xFFEC4899, "Constant Is Slow", "A constant rate pinned below the stability limit is safe and slow — 0.827 against warmup+cosine's 0.099 in the lab."),
    ),
    takeaways = listOf(
        "A scheduler makes the learning rate a function of step: constant, step decay, cosine, or warmup+decay.",
        "The peak rate is capped by the steepest direction: 2/20 = 0.1 on the lab's bowl.",
        "A constant rate must stay safe the whole run (0.03 here) and ends at 0.827; schedules that start at 0.09 and decay end 5–8× lower.",
        "Warmup + cosine finishes best at 0.099 — warmup costs little on a gentle start and protects a fresh network's first steps.",
        "Under gradient noise decay has a second payoff: a smaller rate means a smaller bounce around the minimum (6.1x lower floor in code block 2).",
        "Pick the peak from the curvature you can tolerate, then decay so late steps are small.",
    ),
    crossLinks = listOf(
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("momentum", "Momentum"),
        CrossLink("adam", "Adam (Adaptive Moment Estimation)"),
        CrossLink("early_stopping", "Early Stopping"),
    ),
)
