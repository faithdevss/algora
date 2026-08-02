package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val momentumContent = TopicContent(
    topicId = "momentum",
    whatIsIt = listOf(
        "Plain gradient descent takes the current gradient and nothing else: v_t = g_t. Momentum keeps a running velocity instead, v_t = β·v_{t-1} + g_t, and steps by that velocity rather than the instantaneous gradient. A consistent downhill direction compounds across steps instead of being paid one small increment at a time — the effective step length on a steady gradient is roughly 1/(1−β) times the raw one, so β=0.9 acts like a step about 10× larger than its learning rate alone would suggest.",
        "On the same ill-conditioned bowl gradient_descent_variants runs — 20× steeper on w1 than w2 — a β sweep at one fixed learning rate (0.012) shows where that compounding pays off and where it doesn't. β=0.5 barely helps: final loss 0.1623 after 60 steps, not far off plain SGD. β=0.9 is the clear winner at 0.0070 — more than an order of magnitude lower. β=0.99 overshoots: 0.2416, worse than β=0.5.",
        "The overshoot is visible in the trajectory, not just the final number. β=0.9's first swing back past the minimum on the steep axis reaches 0.737 of the starting distance; β=0.99's reaches 0.996 — essentially the full starting distance, ringing back and forth with almost no damping in 60 steps. More velocity is not more optimizer; past some β the compounding outruns the problem's own curvature.",
    ),
    steps = listOf(
        StepCard(1, "Compute the Gradient", "g_t at the current weights, same as plain SGD.", 0xFF0EA5E9),
        StepCard(2, "Update the Velocity", "v_t = β·v_{t-1} + g_t — this step's gradient added to a decayed memory of every earlier one.", 0xFF3B82F6),
        StepCard(3, "Step by the Velocity", "w_t = w_{t-1} − lr·v_t, not by g_t directly.", 0xFF8B5CF6),
        StepCard(4, "Sweep β", "0.0, 0.5, 0.9, 0.99 at one fixed learning rate.", 0xFFF59E0B),
        StepCard(5, "Find the Winner", "β=0.9: loss 0.0070 after 60 steps — the other three all land above 0.16.", 0xFFEC4899),
        StepCard(6, "Check the Overshoot", "β=0.99's first swing back reaches 0.996 of the start; β=0.9's reaches 0.737.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Velocity update", "v_t = β·v_{t-1} + g_t", "Decayed sum of every past gradient."),
        FormulaEntry("Weight update", "w_t = w_{t-1} − lr·v_t", "Steps by the velocity, not the raw gradient."),
        FormulaEntry("Effective step multiplier", "1/(1−β)", "≈10× at β=0.9 on a steady gradient."),
        FormulaEntry("β=0.9 result", "loss 0.0070", "After 60 steps — the sweep's best."),
        FormulaEntry("β=0.99 result", "loss 0.2416", "34.6× worse than β=0.9 — the compounding overshoots."),
        FormulaEntry("Overshoot at β=0.99", "0.996 × start", "Against β=0.9's 0.737× — barely damped in 60 steps."),
    ),
    notationKey = listOf(
        NotationEntry("β", "the momentum coefficient, typically 0.9 — how much of the old velocity survives each step"),
        NotationEntry("v_t", "the velocity — a decayed running sum of gradients, not the gradient itself"),
        NotationEntry("g_t", "the instantaneous gradient at step t"),
        NotationEntry("effective step", "roughly lr/(1−β) on a sustained, consistent gradient"),
        NotationEntry("overshoot", "how far the parameter swings past the minimum before turning back"),
        NotationEntry("Nesterov momentum", "a variant that evaluates the gradient after the velocity step, not before — not run here"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The β sweep, computed for real",
            accentColor = 0xFF0EA5E9,
            code = """
                curvature = [20.0, 0.4]  # 20x steeper on w1 than w2

                def loss(w): return 0.5 * sum(c * x * x for c, x in zip(curvature, w))
                def grad(w): return [c * x for c, x in zip(curvature, w)]

                def run(beta, lr=0.012, steps=60, start=(1.0, 1.6)):
                    w, v = list(start), [0.0, 0.0]
                    for _ in range(steps):
                        g = grad(w)
                        v = [beta * v[i] + g[i] for i in range(2)]
                        w = [w[i] - lr * v[i] for i in range(2)]
                    return w

                for beta in (0.0, 0.5, 0.9, 0.99):
                    print(beta, loss(run(beta)))
                # 0.0   0.2874
                # 0.5   0.1623
                # 0.9   0.0070   <- best
                # 0.99  0.2416   <- overshoots past 0.9's minimum
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why β=0.9 needs a smaller rate than plain SGD",
            accentColor = 0xFFEC4899,
            code = """
                # Plain SGD on this bowl is capped near lr = 0.1 (2 / curvature[0] = 2 / 20).
                # Momentum's effective step multiplies the raw rate by roughly 1 / (1 - beta):
                effective_multiplier = 1 / (1 - 0.9)          # 10x
                safe_lr = 0.09 / effective_multiplier          # roughly the 0.012 used above
                #
                # That's the whole tuning rule: momentum doesn't remove the need for a rate search,
                # it just changes what the safe rate is -- underestimate the multiplier and beta=0.99
                # (multiplier 100x) is exactly the overshoot measured above.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Default in Practice", "Almost every optimizer used in deep learning builds on this — SGD-with-momentum, RMSprop, Adam."),
        ApplicationCard("trend", 0xFF3B82F6, "Flat-Region Escape", "A consistent small gradient across many steps compounds into a real step instead of crawling."),
        ApplicationCard("check", 0xFF8B5CF6, "Damping Ravines", "Cancels the oscillation SGD alone gets stuck in in a steep, narrow valley — up to a point."),
        ApplicationCard("help", 0xFFEC4899, "When It Hurts", "Too high a β on a well-conditioned or noisy problem overshoots and rings, as β=0.99 does here."),
    ),
    takeaways = listOf(
        "Momentum keeps a decayed running velocity, v_t = β·v_{t-1} + g_t, and steps by that instead of the raw gradient.",
        "The effective step length on a sustained gradient is roughly lr/(1−β) — about 10× the raw rate at β=0.9.",
        "On the sweep, β=0.9 wins clearly: final loss 0.0070 against β=0.5's 0.1623 and β=0.0's 0.2874.",
        "β=0.99 overshoots and rings: final loss 0.2416, worse than β=0.5 despite the larger momentum.",
        "β=0.99's first swing back past the minimum reaches 0.996 of the starting distance; β=0.9's reaches only 0.737.",
        "Higher β needs a smaller learning rate — its own multiplier, not a free additional accelerant.",
        "Momentum is the base every other optimizer in this category builds on: RMSprop and Adam both keep a moving average too.",
    ),
    crossLinks = listOf(
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("adam", "Adam (Adaptive Moment Estimation)"),
        CrossLink("rmsprop", "RMSprop"),
        CrossLink("lr_schedulers", "Learning Rate Schedulers"),
    ),
)
