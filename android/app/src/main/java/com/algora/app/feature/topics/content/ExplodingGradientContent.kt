package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val explodingGradientContent = TopicContent(
    topicId = "exploding_gradient",
    whatIsIt = listOf(
        "The same product, with the average factor on the other side of one. If each layer multiplies the signal by more than one on average, depth compounds it upward instead of downward — and unlike vanishing gradients, this failure is visible in the forward pass. In the simulation, twelve ReLU layers initialised at a scale of 1.5 instead of He's 0.35 take the mean activation from 1.4 to 5.7 million, compounding at roughly 4× per layer. Thirty layers instead of twelve would reach about 10¹⁷–10¹⁸ — still inside float32 (max 3.4 × 10³⁸) but far past fp16's 65,504 limit.",
        "The gradients follow, reaching a total norm around 10¹⁵. That number is what a NaN loss looks like one step before it happens: the update is finite and enormous, the weights land somewhere absurd, the next forward pass overflows, and every parameter in the model becomes NaN simultaneously. The symptom people report is \"the loss went to NaN at step 400\" — the cause was a geometric series doing what geometric series do, several steps earlier.",
        "The standard fix is global-norm gradient clipping, and the detail that makes it work is worth being precise about. Compute the norm over the entire gradient — every parameter in the model as one vector — and if it exceeds a threshold, multiply *everything* by threshold/norm. Because a single factor is applied uniformly, the direction of the step is unchanged and only its length is capped; clipping each parameter independently would distort the direction, which is why `clip_grad_norm_` is the one in general use and `clip_grad_value_` is not. Alongside it: a sensible initialisation scale, and lower learning rates. Recurrent networks are where this bites hardest, because an unrolled RNN multiplies by the *same* matrix at every timestep — so if its largest singular value exceeds one, growth is possible and structural rather than incidental (it is necessary for explosion, not sufficient — saturating nonlinearities can keep gradients bounded). It is worth noting that exploding gradients, for all the drama, are the easier failure to have: they announce themselves loudly and clipping usually resolves them in an afternoon, where a vanishing gradient can silently cost a project months.",
    ),
    steps = listOf(
        StepCard(1, "Watch the Forward Pass", "Activation magnitude per layer. Unlike vanishing, this failure shows up before the backward pass.", 0xFF06B6D4),
        StepCard(2, "Measure the Gradient Norm", "Global norm over all parameters. A spike here precedes the NaN by a step or two.", 0xFF22D3EE),
        StepCard(3, "Understand the Cause", "A geometric series with ratio above one. Not numerical instability — arithmetic.", 0xFF8B5CF6),
        StepCard(4, "Clip Globally, Not Per-Parameter", "One scale factor for the whole gradient caps the length and preserves the direction.", 0xFF6366F1),
        StepCard(5, "Fix the Initialisation", "Clipping treats the symptom every step; the right init scale removes the cause.", 0xFF10B981),
        StepCard(6, "Watch RNNs Especially", "The same matrix at every timestep, so a largest singular value above 1 makes growth structural.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Growth per layer", "‖a⁽ˡ⁾‖ ≈ k·‖a⁽ˡ⁻¹⁾‖", "k > 1 compounds; the lab measures k ≈ 4."),
        FormulaEntry("After L layers", "‖a⁽ᴸ⁾‖ ≈ kᴸ‖a⁽⁰⁾‖", "4¹¹ ≈ 4 × 10⁶. Depth is an exponent."),
        FormulaEntry("Global norm", "‖g‖ = √(Σ_p ‖g_p‖²)", "Over every parameter tensor, as one vector."),
        FormulaEntry("Clipping rule", "g ← g · min(1, θ/‖g‖)", "One factor everywhere, so the direction survives."),
        FormulaEntry("RNN condition", "ρ(W_hh) < 1 ⟹ vanishes; above 1 explosion becomes possible", "Spectral radius; a largest singular value above 1 is necessary for explosion, not sufficient. The same matrix every timestep makes it structural."),
        FormulaEntry("He init", "Var(W) = 2/nᵢₙ", "For width 16 that is a scale of 0.35, against the lab's careless 1.5."),
        FormulaEntry("Float32 ceiling", "≈ 3.4 × 10³⁸", "Where the overflow that produces NaN actually happens."),
    ),
    notationKey = listOf(
        NotationEntry("global norm", "the L2 norm over every parameter's gradient at once"),
        NotationEntry("θ", "the clipping threshold; 1.0 or 5.0 are common defaults"),
        NotationEntry("spectral radius ρ(W)", "largest absolute eigenvalue — decides RNN growth or decay"),
        NotationEntry("NaN", "not-a-number; once one appears it propagates through every parameter"),
        NotationEntry("loss spike", "the visible symptom, usually one or two steps before the NaN"),
        NotationEntry("clip_grad_norm_", "the PyTorch call; the one that preserves direction"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Clipping, and why the global version is the one to use",
            accentColor = 0xFF06B6D4,
            code = """
                import torch
                import torch.nn as nn

                for epoch in range(epochs):
                    for batch in loader:
                        optimizer.zero_grad()
                        loss = criterion(model(batch.x), batch.y)
                        loss.backward()

                        # Returns the norm BEFORE clipping -- log it, it is the early warning.
                        norm = nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                        if norm > 10:
                            print(f"step {step}: gradient norm {norm:.1f}")

                        optimizer.step()

                # clip_grad_norm_ scales every parameter by the same factor, so the update points
                # exactly where it did before -- only shorter. clip_grad_value_ clamps each
                # element independently, which changes the direction of the step and is why it is
                # rarely what you want.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Seeing it coming, and the RNN case where it is structural",
            accentColor = 0xFFF59E0B,
            code = """
                import numpy as np
                import torch
                import torch.nn as nn

                # 1. The forward-pass warning: activation magnitude per layer.
                x = torch.randn(32, 16)
                for scale, label in ((np.sqrt(2 / 16), "He"), (1.5, "careless")):
                    a = x
                    sizes = []
                    for _ in range(12):
                        W = torch.randn(16, 16) * scale
                        a = torch.relu(a @ W)
                        sizes.append(a.abs().mean().item())
                    print(label, [f"{v:.1e}" for v in sizes[::3]])
                # He:       ~3e-1 ... ~2e-1        careless: 1e0 ... 6e6

                # 2. For an RNN it is not luck. The SAME matrix applies at every timestep, so the
                # spectral radius decides it: above 1 the backpropagated gradient can grow exponentially with sequence length (the tanh state itself stays bounded in (−1, 1)).
                rnn = nn.RNN(32, 32)
                W_hh = rnn.weight_hh_l0.detach().numpy()
                print(round(float(np.max(np.abs(np.linalg.eigvals(W_hh)))), 3))
                # Above 1.0 and a long sequence will explode however careful the learning rate is.
                # This is the reason gradient clipping is standard in RNN training and optional
                # almost everywhere else.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF06B6D4, "RNN and LSTM Training", "Clipping is not optional here — the shared recurrent matrix makes growth structural rather than accidental."),
        ApplicationCard("robot", 0xFF8B5CF6, "Reinforcement Learning", "Policy gradients have high variance and occasional enormous updates; clipping is standard in PPO's implementation, separate from its objective's own clipping."),
        ApplicationCard("chip", 0xFF6366F1, "Large-Scale Pretraining", "Loss-spike monitoring and clipping are routine at scale, where a single bad step can waste days of compute."),
    ),
    takeaways = listOf(
        "The same per-layer product as vanishing, with the ratio above one instead of below.",
        "Visible in the forward pass: activation magnitude compounding with depth.",
        "A NaN loss is the end of the story; the gradient norm spike a step or two earlier is the diagnosis.",
        "Clip the global norm, not per-parameter values — one uniform factor preserves the step's direction.",
        "RNNs are the worst case, because the same matrix applies at every timestep.",
    ),
    crossLinks = listOf(
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
        CrossLink("rnn", "RNNs"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("batch_normalization", "Batch Normalization"),
    ),
)
