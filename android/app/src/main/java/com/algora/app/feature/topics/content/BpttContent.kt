package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bpttContent = TopicContent(
    topicId = "bptt",
    figure = Figure(
        caption = "Ten steps, one cue, and the two gradient norms the pointers carry. " +
            "The loss sits at step 10 and the backward pass multiplies by the same recurrent " +
            "matrix at every step on the way down, so ‖∂L/∂h‖ measured at initialization is 0.480 " +
            "at the last step and 0.0085 nine steps earlier — 56× from repeated multiplication " +
            "alone. Step 1 is worse than small: its contribution to the recurrent matrix is " +
            "exactly zero, because h₀ is the zero vector, so the cue reaches the input weights " +
            "and never reaches W at all. Truncating at k = 3 keeps three steps of graph alive — " +
            "36 stored activations instead of 120 — and yields a gradient 99.0% cosine-aligned " +
            "with the full one, sometimes larger than it, since the terms dropped were partly " +
            "cancelling. That alignment predicts nothing: k = 3 learns the rule on one seed in " +
            "three, while k = 9, which does not reach the cue either, learns it on all three.",
        shape = FigureShape.Strip(
            cells = listOf("x₁", "x₂", "x₃", "x₄", "x₅", "x₆", "x₇", "x₈", "x₉", "x₁₀"),
            bands = listOf(
                FigureBand(0, 0, "cue", FigureTone.Warn),
                FigureBand(7, 9, "k = 3 window", FigureTone.Primary),
            ),
            pointers = listOf(
                FigurePointer(0, ".0085", FigureTone.Warn),
                FigurePointer(9, ".480"),
            ),
            aux = listOf("·", "·", "·", "·", "·", "·", "·", "h₈", "h₉", "h₁₀"),
            auxLabel = "kept for the backward pass at k = 3 — 36 values, not 120",
        ),
    ),
    whatIsIt = listOf(
        "Backpropagation through time is ordinary backpropagation applied to a network that was written as a loop. Unroll a recurrent layer over T timesteps and what you have is a T-layer feedforward network in which every layer shares one weight matrix. The forward pass is the loop you wrote; the backward pass walks from the loss at the end of the sequence to the first step, multiplying by that same matrix on the way, and the gradient for the shared weights is the *sum* of what every step contributed. Nothing about it is special except the sharing, and the sharing is where all the trouble lives.",
        "The trouble is measurable rather than folkloric. In the lab's task — one informative token, nine steps of noise, a decision at the end — the gradient arriving at the last step has norm 0.480 at initialization and the one arriving at the first step has norm 0.0085, 56× smaller after nine multiplications. The first step's contribution to the recurrent matrix is not merely small, it is exactly zero, because h₀ is the zero vector and that step's outer product has a zero factor in it. The cue reaches the input matrix and the recurrent matrix never sees it at all.",
        "So the standard remedy is truncation: keep the forward pass whole, stop the gradient after k steps, and store k activations instead of T. That is how every recurrent model on long sequences is actually trained, and it is usually described as a cheap approximation. Measured on this task it is something stranger. A truncated gradient is 99% cosine-aligned with the full one at k = 3, and can even be *larger* than it. It is also, at that width, useless: across three initializations the k = 3 model learns the rule once and fails twice. The alignment and the learning have almost nothing to do with each other.",
    ),
    steps = listOf(
        StepCard(1, "Unroll", "T timesteps become T layers sharing one weight matrix.", 0xFF06B6D4),
        StepCard(2, "Walk Back", "One multiplication by Wᵀ per step, from the loss to the start.", 0xFFF97316),
        StepCard(3, "Sum the Contributions", "∂L/∂W is a sum over steps — not one term, T of them.", 0xFF8B5CF6),
        StepCard(4, "Watch It Decay", "0.480 at the output, 0.0085 nine steps earlier: 56×.", 0xFFEC4899),
        StepCard(5, "Truncate", "Cut the walk at k steps: k activations stored instead of T.", 0xFF10B981),
        StepCard(6, "Check What Was Cut", "Gradient similarity says almost nothing about what gets learned.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Unrolled state", "hₜ = tanh(W·hₜ₋₁ + U·xₜ + b)", "One cell, T applications, one set of weights."),
        FormulaEntry("Shared-weight gradient", "∂L/∂W = Σₜ (∂L/∂hₜ)(∂hₜ/∂W)", "The sum every recurrent gradient is."),
        FormulaEntry("The chain that decays", "∂hₜ/∂hₖ = Πⱼ diag(1−h²)·Wᵀ", "t−k factors; a product, so it vanishes or explodes."),
        FormulaEntry("Measured decay", "0.480 → 0.0085 over 9 steps", "‖∂L/∂h‖ at initialization, averaged over the training set."),
        FormulaEntry("Truncation", "stop after k steps", "Stores k·H activations instead of T·H: 36 instead of 120 here."),
        FormulaEntry("Alignment ≠ learnability", "cos = 0.990 at k = 3", "And that window solves the task on 1 of 3 seeds."),
    ),
    notationKey = listOf(
        NotationEntry("T", "sequence length — 10 in the lab"),
        NotationEntry("k", "truncation window: how many steps back the gradient is allowed to travel"),
        NotationEntry("W", "the recurrent matrix, shared by every timestep"),
        NotationEntry("h₀", "the initial state, zero — which is why step 1 contributes nothing to ∂L/∂W"),
        NotationEntry("cos", "cosine similarity between the truncated gradient and the full one"),
        NotationEntry("cue", "the one informative token, at step 1, nine steps before the decision"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Truncated BPTT is one detach()",
            accentColor = 0xFFF97316,
            code = """
                import torch
                import torch.nn as nn

                cell = nn.RNNCell(input_size=8, hidden_size=32)
                head = nn.Linear(32, 2)
                optimizer = torch.optim.Adam(list(cell.parameters()) + list(head.parameters()), lr=1e-2)

                K = 20  # truncation window, in timesteps

                def train_chunked(sequence, label):
                    "Forward the whole sequence; backprop within windows of K."
                    h = torch.zeros(sequence.size(1), 32)
                    for start in range(0, sequence.size(0), K):
                        chunk = sequence[start:start + K]
                        for x in chunk:
                            h = cell(x, h)
                        if start + K >= sequence.size(0):
                            loss = nn.functional.cross_entropy(head(h), label)
                            optimizer.zero_grad()
                            loss.backward()
                            optimizer.step()
                        # The one line that makes it truncated: the next window starts from a
                        # state with no history attached, so the graph never grows past K.
                        h = h.detach()

                # Without the detach, the graph keeps every step alive and memory grows with T.
                # With it, memory is flat -- and any dependency longer than K is outside the
                # gradient. That is a modelling decision, not a performance tweak.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measure the decay instead of assuming it",
            accentColor = 0xFFEC4899,
            code = """
                import torch
                import torch.nn as nn

                T, H = 10, 12
                cell = nn.RNNCell(5, H)
                head = nn.Linear(H, 2)

                x = torch.nn.functional.one_hot(torch.randint(0, 5, (T,)), 5).float()
                states = []
                h = torch.zeros(1, H)
                for t in range(T):
                    h = cell(x[t:t + 1], h)
                    h.retain_grad()          # keep dL/dh_t for every step, not just the last
                    states.append(h)

                nn.functional.cross_entropy(head(states[-1]), torch.tensor([1])).backward()

                norms = [s.grad.norm().item() for s in states]
                print([f"{n:.4f}" for n in norms])
                print(f"decay over {T - 1} steps: {norms[-1] / norms[0]:.0f}x")
                # A monotone climb toward the output. If your own model prints something flat,
                # the recurrent matrix has a spectral radius near 1 -- worth knowing either way,
                # and two lines to find out.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFFF97316, "Long Sequences", "Audio, sensor streams and character-level text are trained in windows, not whole."),
        ApplicationCard("finance", 0xFF10B981, "Memory Budget", "The window is the activation memory: k·H values per training step."),
        ApplicationCard("flask", 0xFF8B5CF6, "Debugging Training", "Per-step gradient norms tell you whether depth or the data is the problem."),
        ApplicationCard("help", 0xFFEC4899, "Choosing k", "Set it from the dependency you care about, not from what fits in memory."),
    ),
    takeaways = listOf(
        "BPTT is backprop on an unrolled loop: T layers, one shared weight matrix, and ∂L/∂W is a sum over all T steps.",
        "Measured at initialization, ‖∂L/∂h‖ falls 0.480 → 0.0085 across nine steps — 56×, from repeated multiplication alone.",
        "Step 1 contributes exactly zero to the recurrent matrix, because h₀ is zero. The cue reaches only the input weights.",
        "Truncation to k steps is what makes long sequences trainable at all: 36 stored activations here instead of 120.",
        "A truncated gradient is 99% aligned with the full one at k = 3 — and can exceed its magnitude, since dropped terms were partly cancelling.",
        "That alignment predicts nothing: k = 3 learns the rule on one seed in three, while k = 9 — which never reaches the cue — learns it on all three.",
        "The window bounds credit assignment, not memory. The shared matrix is trained inside the window and still carries the state outside it.",
    ),
    crossLinks = listOf(
        CrossLink("rnn", "RNNs"),
        CrossLink("backpropagation", "Backpropagation"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("exploding_gradient", "The Exploding Gradient Problem"),
    ),
)
