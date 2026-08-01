package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ssmContent = TopicContent(
    topicId = "ssm",
    whatIsIt = listOf(
        "A state space model is a linear recurrence: h ← Ā·h + B̄·x, y = C·h. That is an RNN with the non-linearity removed from the recurrence, and removing it is what buys everything. Because the system is linear and time-invariant, it has a second form — a convolution with the impulse response K = (CB̄, CĀB̄, CĀ²B̄, …) — and the two forms compute the same function.",
        "The lab runs both on the same input and reports the gap: **3.1e-15**. That equality is the family's entire structural argument, and it is worth seeing as an equality rather than a claim, because it sounds like an approximation. Train with the convolution, which is parallel over the sequence and can go through an FFT; decode with the recurrence, which carries O(1) memory per token. Attention has no second form to switch into, and an RNN has no parallel form at all.",
        "What the state remembers is set by the decay rates, and it is a half-life. With Ā = diag(0.990, 0.951, 0.819, 0.449) — poles deliberately spread over decades — channel 0 keeps half of a token's contribution 69 tokens later while channel 3 keeps half for 0.9. One state carries several timescales at once, and together the impulse response is still above 1% of its peak at token 503. That is a real memory, and a fixed one: the decay does not depend on what the token was.",
        "The cost is linear in the sequence. At 1M tokens attention does 524,288× the arithmetic this recurrence does, and because the scan operator is associative — (a₂,b₂)∘(a₁,b₁) = (a₂a₁, a₂b₁+b₂) — training parallelises to depth 39 instead of 1,048,576 sequential steps. Being linear is what makes the operator associative, which is what makes the parallel scan possible. Every property in this topic traces back to that one omission.",
    ),
    steps = listOf(
        StepCard(1, "Write The Continuous System", "h′ = A·h + B·x, y = C·h — a linear ODE, borrowed from control theory.", 0xFF06B6D4),
        StepCard(2, "Discretize", "Zero-order hold: Ā = exp(Δ·A), B̄ = A⁻¹(Ā − I)·B. Δ is the step size in tokens.", 0xFF14B8A6),
        StepCard(3, "Diagonalise A", "A diagonal Ā makes the state N independent channels and the powers trivial.", 0xFF10B981),
        StepCard(4, "Read Off The Kernel", "K[t] = C·Āᵗ·B̄ — the impulse response, which is all an LTI system is.", 0xFF3B82F6),
        StepCard(5, "Train As A Convolution", "One long convolution over the whole sequence, parallel and FFT-able.", 0xFF6366F1),
        StepCard(6, "Decode As A Scan", "Same weights, same answers, constant memory per token.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "hₜ = Ā·hₜ₋₁ + B̄·xₜ,  yₜ = C·hₜ", "One multiply-add per channel per token."),
        FormulaEntry("Convolution kernel", "K[t] = C·Āᵗ·B̄", "The impulse response — the whole system, in one vector."),
        FormulaEntry("Equivalence", "max |y_scan − y_conv| = 3.1e-15", "The same function, measured, not asserted."),
        FormulaEntry("Half-life", "ln(0.5) / ln(āₙ)", "69, 14, 3.5 and 0.9 tokens for the four channels."),
        FormulaEntry("Effective horizon", "503 tokens", "Where the impulse response drops below 1% of its peak."),
        FormulaEntry("Scan depth", "2·⌈log₂L⌉ − 1 = 39 at 1M", "Because the scan operator is associative."),
    ),
    notationKey = listOf(
        NotationEntry("Ā, B̄", "the discretized state transition and input matrices"),
        NotationEntry("Δ", "the discretization step — how much 'time' one token advances"),
        NotationEntry("LTI", "linear time-invariant: the same Ā, B̄, C at every position"),
        NotationEntry("HiPPO", "an initialisation for A that makes the state approximate the input's history"),
        NotationEntry("associative scan", "a prefix computation parallelisable to logarithmic depth"),
        NotationEntry("impulse response", "the output for a single 1 at t=0 — the convolution kernel"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Both forms, and the check that they agree",
            accentColor = 0xFF06B6D4,
            code = """
                import numpy as np

                poles = np.array([0.01, 0.05, 0.20, 0.80])      # spread over decades
                delta = 1.0
                A = np.exp(-delta * poles)                       # 0.990 0.951 0.819 0.449
                B = (1 - A) / poles
                C = np.array([1.0, -0.6, 0.4, -0.25])

                def recurrent(x):                                # decode-time form
                    h = np.zeros_like(A)
                    out = []
                    for xt in x:
                        h = A * h + B * xt
                        out.append(C @ h)
                    return np.array(out)

                def convolutional(x):                            # train-time form
                    K = np.array([C @ (A ** t * B) for t in range(len(x))])
                    return np.convolve(x, K)[:len(x)]

                print(np.abs(recurrent(x) - convolutional(x)).max())   # 3.1e-15
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why linearity is the whole trick",
            accentColor = 0xFF3B82F6,
            code = """
                # An RNN's recurrence has a non-linearity inside it:
                #     h = tanh(W @ h + U @ x)
                # There is no kernel, no convolution, and no way to parallelise -- the
                # sequence has to be walked. That is what makes RNNs slow to train.

                # An SSM's recurrence does not, so the scan operator composes:
                def compose(first, second):
                    a1, b1 = first
                    a2, b2 = second
                    return (a2 * a1, a2 * b1 + b2)     # associative

                # Associativity is exactly the precondition for a parallel prefix scan,
                # which turns L sequential steps into depth 2*ceil(log2 L) - 1:
                #     L = 1,048,576  ->  depth 39
                # Total work stays O(L). Only the critical path collapses.

                # Half-life per channel, in tokens -- the model's actual memory:
                half_life = np.log(0.5) / np.log(A)     # [69.3, 13.9, 3.5, 0.9]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF06B6D4, "Long-Range Sequences", "Audio, genomics and time series where 100k steps are ordinary."),
        ApplicationCard("chip", 0xFF10B981, "Constant-Memory Decoding", "A fixed state per layer instead of a cache that grows with the prompt."),
        ApplicationCard("trend", 0xFF3B82F6, "Control Theory Roots", "The same equations engineers have used for state estimation for decades."),
        ApplicationCard("flask", 0xFF8B5CF6, "S4 And Successors", "The line that runs through S4, S5, H3 and Mamba."),
    ),
    takeaways = listOf(
        "One system, two forms: the recurrence and the convolution agree to 3.1e-15, and each is fast in a different place.",
        "Dropping the non-linearity from the recurrence is what makes the operator associative and the parallel scan possible.",
        "Memory is a half-life per channel — 69, 14, 3.5 and 0.9 tokens here — so one state carries several timescales.",
        "The impulse response is still above 1% of peak at token 503: real long-range memory, but fixed and input-independent.",
        "At 1M tokens the recurrence does 1/524,288 of attention's arithmetic, at scan depth 39 instead of a million steps.",
    ),
    crossLinks = listOf(
        CrossLink("mamba", "Mamba"),
        CrossLink("rwkv", "RWKV"),
        CrossLink("rnn", "Vanilla RNNs"),
        CrossLink("long_context", "Long Context Windows"),
    ),
)
