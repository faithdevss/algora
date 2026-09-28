package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lrSchedulersContent = TopicContent(
    topicId = "lr_schedulers",
    whatIsIt = listOf(
        "A learning rate scheduler makes the rate itself a function of the step: constant, step decay (halved every fixed interval), cosine annealing (smoothly to zero, or to a floor), or warmup-then-decay (a linear ramp before the decay begins). The folklore is that decay always helps. Run it on the same noise-free bowl gradient_descent_variants and momentum use and the folklore is only half right.",
        "On that clean bowl, constant (lr=0.05) and step decay (start 0.09, halve every 20 steps) actually finish best — step decay at 0.0398, constant at 0.0453 — while cosine (to 0) finishes at 0.0553 and warmup+cosine at 0.0574, both worse than doing nothing. The decaying schedules can afford to start hotter (0.09, close to the steep axis's own stability limit of 0.1) because they immediately move away from it — but shrinking the rate before the flat axis has finished using it costs more than the hot start saves, on a landscape with no noise to justify decaying at all.",
        "Add a fixed disturbance every step instead — standing in for gradient noise, deterministic so the result doesn't depend on a random seed — and the story flips. A rate decayed from 0.05 to 0.02 shrinks the steady-state loss floor to 0.0000366, against a constant rate's 0.000223 — a 6.1× reduction, close to the (lr ratio)² = 6.25 the floor's own scaling law predicts. Under noise, a constant rate never stops bouncing around the minimum by an amount proportional to the rate; a decaying one keeps shrinking that bounce.",
    ),
    steps = listOf(
        StepCard(1, "Pick a Schedule", "Constant, step decay, cosine, or warmup+cosine.", 0xFF0EA5E9),
        StepCard(2, "Run on a Clean Bowl", "Same problem gradient_descent_variants and momentum use, 60 steps.", 0xFF3B82F6),
        StepCard(3, "Compare Final Loss", "Step decay 0.0398, constant 0.0453 -- both beat cosine's 0.0553 and warmup's 0.0574.", 0xFF8B5CF6),
        StepCard(4, "Add a Persistent Disturbance", "A fixed, deterministic perturbation every step -- standing in for gradient noise.", 0xFFF59E0B),
        StepCard(5, "Compare the Noise Floor", "Constant: 0.000223. Decayed (0.05 to 0.02): 0.0000366 -- 6.1x lower.", 0xFFEC4899),
        StepCard(6, "Check the Scaling Law", "6.1x is close to (lr ratio)² = 6.25 -- the floor scales with the rate squared.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Step decay", "lr_t = lr_0 · 0.5^⌊(t−1)/20⌋", "Halves every 20 steps."),
        FormulaEntry("Cosine annealing", "lr_t = lr_min + (lr_0−lr_min)·½(1+cos(π·t/T))", "Smooth decay to a floor over T total steps."),
        FormulaEntry("Clean-bowl result", "step decay 0.0398 < constant 0.0453", "Decay wins here only because it starts hotter, not because decay itself helps."),
        FormulaEntry("Cosine on clean bowl", "0.0553", "Worse than doing nothing — shrinks the rate before the flat axis needs it."),
        FormulaEntry("Noise floor, constant", "0.000223", "Steady-state loss under a persistent per-step disturbance."),
        FormulaEntry("Noise floor, decayed", "0.0000366", "6.1x lower — close to the predicted (0.05/0.02)² = 6.25."),
    ),
    notationKey = listOf(
        NotationEntry("lr_0", "the starting (or peak) learning rate"),
        NotationEntry("lr_min", "the floor a decaying schedule settles to, if any"),
        NotationEntry("warmup", "a linear ramp from 0 (or a small value) up to lr_0 before decay begins"),
        NotationEntry("noise floor", "the steady-state loss a fixed learning rate bounces around under persistent gradient noise"),
        NotationEntry("clean bowl", "the noise-free quadratic where decay's only cost, not its noise-reduction benefit, shows up"),
        NotationEntry("stability limit", "2/curvature — the largest rate a fixed-curvature axis tolerates before diverging"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Decay isn't free on a clean bowl",
            accentColor = 0xFF0EA5E9,
            code = """
                import math
                curvature = [20.0, 0.4]
                def loss(w): return 0.5 * sum(c * x * x for c, x in zip(curvature, w))
                def grad(w): return [c * x for c, x in zip(curvature, w)]

                def run(lr_fn, steps=60, start=(1.0, 1.6)):
                    w = list(start)
                    for t in range(1, steps + 1):
                        g = grad(w)
                        lr = lr_fn(t)
                        w = [w[i] - lr * g[i] for i in range(2)]
                    return loss(w)

                print(run(lambda t: 0.05))                                  # constant: 0.0453
                print(run(lambda t: 0.09 * 0.5 ** ((t - 1) // 20)))         # step decay: 0.0398
                print(run(lambda t: 0.09 * 0.5 * (1 + math.cos(math.pi * (t - 1) / 60))))  # cosine: 0.0553
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
        ApplicationCard("help", 0xFFEC4899, "No Free Decay", "On a deterministic, noise-free objective, decay only costs — there's no floor to shrink."),
    ),
    takeaways = listOf(
        "A scheduler makes the learning rate a function of step: constant, step decay, cosine, or warmup+decay.",
        "On a clean, noise-free bowl, step decay (0.0398) and constant (0.0453) actually beat cosine (0.0553) and warmup+cosine (0.0574).",
        "Decaying schedules can start hotter (0.09, near the steep axis's own stability limit) precisely because they move away from it fast.",
        "Add a fixed per-step disturbance standing in for gradient noise, and decay wins clearly: floor 0.0000366 against constant's 0.000223.",
        "That's a 6.1x reduction, close to the (lr ratio)² = 6.25 predicted by the floor's own scaling law under persistent perturbation.",
        "The two halves of the story aren't in tension — decay's cost shows up without noise, its benefit shows up with it.",
        "Choosing a schedule is choosing which regime training is actually in: clean and short, or noisy and long.",
    ),
    crossLinks = listOf(
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("momentum", "Momentum"),
        CrossLink("adam", "Adam (Adaptive Moment Estimation)"),
        CrossLink("early_stopping", "Early Stopping"),
    ),
)
