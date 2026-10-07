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

internal val ssmContent = TopicContent(
    topicId = "ssm",
    figure = Figure(
        caption = "What the state remembers, channel by channel: the lab's four decay rates " +
            "Ā = 0.95, 0.9, 0.8 and 0.6, each curve showing how much of one token's contribution " +
            "survives t steps later (Āᵗ). One state carries four timescales at once — half-lives " +
            "of 13.5, 6.6, 3.1 and 1.4 tokens. The fastest channel has kept only 1% of a token " +
            "after nine steps; the slowest still holds 21% after thirty. All four curves are " +
            "fixed: the decay does not depend on what the token was, which is exactly the " +
            "limitation Mamba removes by making Ā a function of the input. Being linear and fixed " +
            "is also what lets the same system run as one convolution with these curves as its " +
            "kernel — the lab gets y₇ = 0.7027 either way.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "Ā = 0.6",
                    listOf(
                        FigurePoint(0.000f, 1.000f), FigurePoint(0.033f, 0.600f), FigurePoint(0.067f, 0.360f),
                        FigurePoint(0.100f, 0.216f), FigurePoint(0.133f, 0.130f), FigurePoint(0.167f, 0.078f),
                        FigurePoint(0.200f, 0.047f), FigurePoint(0.267f, 0.017f), FigurePoint(0.333f, 0.006f),
                        FigurePoint(0.400f, 0.002f), FigurePoint(0.500f, 0.000f), FigurePoint(0.667f, 0.000f),
                        FigurePoint(0.833f, 0.000f), FigurePoint(1.000f, 0.000f),
                    ),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    "Ā = 0.8",
                    listOf(
                        FigurePoint(0.000f, 1.000f), FigurePoint(0.033f, 0.800f), FigurePoint(0.067f, 0.640f),
                        FigurePoint(0.100f, 0.512f), FigurePoint(0.133f, 0.410f), FigurePoint(0.167f, 0.328f),
                        FigurePoint(0.200f, 0.262f), FigurePoint(0.267f, 0.168f), FigurePoint(0.333f, 0.107f),
                        FigurePoint(0.400f, 0.069f), FigurePoint(0.500f, 0.035f), FigurePoint(0.667f, 0.012f),
                        FigurePoint(0.833f, 0.004f), FigurePoint(1.000f, 0.001f),
                    ),
                    tone = FigureTone.Muted,
                ),
                FigureSeries(
                    "Ā = 0.9",
                    listOf(
                        FigurePoint(0.000f, 1.000f), FigurePoint(0.033f, 0.900f), FigurePoint(0.067f, 0.810f),
                        FigurePoint(0.100f, 0.729f), FigurePoint(0.133f, 0.656f), FigurePoint(0.167f, 0.590f),
                        FigurePoint(0.200f, 0.531f), FigurePoint(0.267f, 0.430f), FigurePoint(0.333f, 0.349f),
                        FigurePoint(0.400f, 0.282f), FigurePoint(0.500f, 0.206f), FigurePoint(0.667f, 0.122f),
                        FigurePoint(0.833f, 0.072f), FigurePoint(1.000f, 0.042f),
                    ),
                    tone = FigureTone.Primary,
                ),
                FigureSeries(
                    "Ā = 0.95",
                    listOf(
                        FigurePoint(0.000f, 1.000f), FigurePoint(0.033f, 0.950f), FigurePoint(0.067f, 0.902f),
                        FigurePoint(0.100f, 0.857f), FigurePoint(0.133f, 0.815f), FigurePoint(0.167f, 0.774f),
                        FigurePoint(0.200f, 0.735f), FigurePoint(0.267f, 0.663f), FigurePoint(0.333f, 0.599f),
                        FigurePoint(0.400f, 0.540f), FigurePoint(0.500f, 0.463f), FigurePoint(0.667f, 0.358f),
                        FigurePoint(0.833f, 0.277f), FigurePoint(1.000f, 0.215f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.450f, 0.5f, "half-life 13.5"),
                FigurePoint(0.045f, 0.5f, "1.4", FigureTone.Warn),
            ),
            xLabel = "tokens later, 0 → 30",
            yLabel = "share remaining, Āᵗ",
        ),
    ),
    whatIsIt = listOf(
        "A state space model is a linear recurrence: h ← Ā·h + B̄·x, y = C·h. That is an RNN with the non-linearity removed from the recurrence, and removing it is what buys everything. Because the system is linear and time-invariant, it has a second form — a convolution with the impulse response K = (CB̄, CĀB̄, CĀ²B̄, …) — and the two forms compute the same function.",
        "The lab runs both on the same eight-token input and gets the same output: y₇ = **0.7027** by the recurrence and by the convolution. That equality is the family's entire structural argument, and it is worth seeing as an equality rather than a claim, because it sounds like an approximation. Train with the convolution, which is parallel over the sequence and can go through an FFT; decode with the recurrence, which carries O(1) memory per token. Attention has no second form to switch into, and an RNN has no parallel form at all.",
        "What the state remembers is set by the decay rates, and it is a half-life. In the lab Ā = diag(0.95, 0.9, 0.8, 0.6): channel 0 keeps half of a token's contribution 13.5 tokens later while channel 3 keeps half for 1.4. One state carries several timescales at once — a real memory, and a fixed one: the decay does not depend on what the token was.",
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
        FormulaEntry("Equivalence", "y₇ = 0.7027 both ways", "Recurrence and convolution in the lab — the same function, measured, not asserted."),
        FormulaEntry("Half-life", "ln(0.5) / ln(āₙ)", "13.5, 6.6, 3.1 and 1.4 tokens for the lab's four channels."),
        FormulaEntry("Fixed decay", "Ā independent of the input", "The limitation Mamba removes by making Ā a function of each token."),
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
        "One system, two forms: the recurrence and the convolution give the same output (0.7027 in the lab), and each is fast in a different place.",
        "Dropping the non-linearity from the recurrence is what makes the operator associative and the parallel scan possible.",
        "Memory is a half-life per channel — 13.5, 6.6, 3.1 and 1.4 tokens in the lab — so one state carries several timescales.",
        "That memory is real but fixed: the decay is the same whatever the token was.",
        "At 1M tokens the recurrence does 1/524,288 of attention's arithmetic, at scan depth 39 instead of a million steps.",
    ),
    crossLinks = listOf(
        CrossLink("mamba", "Mamba"),
        CrossLink("rwkv", "RWKV"),
        CrossLink("rnn", "Vanilla RNNs"),
        CrossLink("long_context", "Long Context Windows"),
    ),
)
