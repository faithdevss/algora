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

internal val mambaContent = TopicContent(
    topicId = "mamba",
    figure = Figure(
        caption = "The page's lab: one signal token, then seven fillers worth 0.1 each, through a " +
            "one-channel state — and how much of the signal is still in the state after each " +
            "filler. A time-invariant SSM with Ā = 0.6 forgets on a timer: 1.000, 0.600, 0.360, " +
            "0.216 … down to 0.6⁷ = 0.028, while the fillers it cannot refuse keep the state near " +
            "0.27, so the signal is about 10% of what is left. A slower decay would keep the signal " +
            "longer and every filler too; no fixed Ā does one without the other. Mamba computes Ā " +
            "and B̄ from each token: on the signal the gate opens (Ā = 0, B̄ = 1), on every filler " +
            "it closes (Ā = 1, B̄ = 0), and the signal is still 100% of the state at the end. That " +
            "input dependence is also what breaks the single-convolution form, which is why Mamba " +
            "trains with a parallel scan instead.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "selective (Mamba)",
                    listOf(FigurePoint(0f, 1f), FigurePoint(1f, 1f)),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "fixed Ā = 0.6",
                    listOf(
                        FigurePoint(0.000f, 1.000f), FigurePoint(0.143f, 0.600f), FigurePoint(0.286f, 0.360f),
                        FigurePoint(0.429f, 0.216f), FigurePoint(0.571f, 0.130f), FigurePoint(0.714f, 0.078f),
                        FigurePoint(0.857f, 0.047f), FigurePoint(1.000f, 0.028f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(1f, 0.028f, "0.028", FigureTone.Warn),
                FigurePoint(1f, 1f, "100%"),
            ),
            xLabel = "fillers after the signal, 0 → 7",
            yLabel = "signal still in the state",
        ),
    ),
    whatIsIt = listOf(
        "Mamba makes a state space model's parameters depend on the token. In an LTI system Ā, B̄ and C are the same at every position, so the model decides what to remember before it has seen anything. Mamba computes Δ, B and C from the current input, which means the recurrence can choose to write a token into the state or to hold the state unchanged and ignore it.",
        "The lab runs the selective-copying task the paper is built around, in its smallest form: one signal token worth remembering, then seven filler tokens carrying a value of 0.1 each, through a one-channel state. The question is whether the state still holds the signal at the end.",
        "A time-invariant SSM cannot do it, and no tuning fixes that. With a fixed decay of Ā = 0.6 every token fades on the same timer, signal and filler alike: the signal's contribution falls 1.000, 0.600, 0.360 … down to 0.6⁷ = 0.028, while the state settles near 0.27, made almost entirely of filler — the signal is about 10% of what is left. A decay slow enough to keep the signal would keep every filler too. The selective version computes Ā and B̄ from each token: on the signal the gate opens fully (Ā = 0 clears the old state, B̄ = 1 writes the token), and on each filler it closes (Ā = 1, B̄ = 0), so the state passes through untouched and the signal is still 100% of it seven tokens later.",
        "Selectivity is not free: it costs the convolution. An LTI system is one fixed kernel, which is why SSMs can train in parallel through an FFT; a selective one has a different kernel at every position,. That is why Mamba needs a hardware-aware parallel scan instead — the scan survives input-dependence, the convolution does not. What it buys at inference is a state that does not grow: 128 KB per layer whatever the length, against a transformer layer's KV cache 65,536× larger at 1M tokens.",
    ),
    steps = listOf(
        StepCard(1, "Project Δ From The Token", "Δ = softplus(W_Δ·x). A large Δ writes; Δ ≈ 0 holds the state.", 0xFF06B6D4),
        StepCard(2, "Make B And C Input-Dependent", "What gets written and what gets read now depend on the content.", 0xFF14B8A6),
        StepCard(3, "Discretize Per Position", "Ā = exp(−Δ), B̄ = 1 − Ā, recomputed at every token.", 0xFF10B981),
        StepCard(4, "Give Up The Convolution", "A time-varying kernel has no single K. The FFT path is gone.", 0xFF3B82F6),
        StepCard(5, "Scan In SRAM", "Materialise the expanded state on-chip only, and recompute it in the backward pass.", 0xFF6366F1),
        StepCard(6, "Decode In Constant Memory", "One fixed state per layer, whatever the sequence length.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Selective recurrence", "hₜ = Āₜ·hₜ₋₁ + B̄ₜ·xₜ, with Āₜ = exp(−Δ(xₜ))", "Every matrix now carries a t."),
        FormulaEntry("Hold", "Δ = 0 → ā = 1, b̄ = 0", "The state passes through untouched and nothing is written."),
        FormulaEntry("Write", "Δ large → ā ≈ 0, b̄ ≈ 1", "The state is replaced by the current token."),
        FormulaEntry("Decaying LTI", "0.6⁷ = 0.028", "The signal's contribution after seven fillers at Ā = 0.6."),
        FormulaEntry("What is left", "state ≈ 0.27, signal ≈ 10%", "A fixed decay forgets signal and filler on the same timer."),
        FormulaEntry("Selective", "signal 100% after 7 fillers", "Ā = 0, B̄ = 1 on the signal; Ā = 1, B̄ = 0 on each filler."),
    ),
    notationKey = listOf(
        NotationEntry("Δ", "the per-token step size — the selection gate, and Mamba's whole idea"),
        NotationEntry("selectivity", "letting the recurrence's parameters depend on the input"),
        NotationEntry("selective copy", "remember a flagged token, ignore filler, reproduce it later"),
        NotationEntry("signal contribution", "the difference the signal token makes, against a filler-only control"),
        NotationEntry("hardware-aware scan", "the parallel scan fused into one kernel that never leaves SRAM"),
        NotationEntry("expanded state", "the per-channel state, typically 16× the model width"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The gate, and the three arms it is compared against",
            accentColor = 0xFF06B6D4,
            code = """
                import math

                SIGNAL, FILLER = 1.0, 0.7

                def lti(seq, a, b):                 # time-invariant: one a, one b, forever
                    h = 0.0
                    for x in seq:
                        h = a * h + b * x
                    return h

                def selective(seq):                 # delta depends on the token
                    h = 0.0
                    for x in seq:
                        d = 4.0 if abs(x - SIGNAL) < 1e-9 else 0.0
                        a = math.exp(-d)            # d = 0 -> a = 1: hold exactly
                        h = a * h + (1 - a) * x     # d = 0 -> b = 0: write nothing
                    return h

                # Score against a filler-only control, not against the target -- the raw
                # value hides the failure. The decaying arm "recovers" 0.700 at 100
                # fillers, which is just the filler steady state.
                def contribution(f, k):
                    return f([SIGNAL] + [FILLER] * k) - f([FILLER] * (k + 1))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What selectivity costs, and what it buys",
            accentColor = 0xFF3B82F6,
            code = """
                # Cost: the convolution. An LTI system IS one kernel; a selective one
                # has a different kernel per position. Fit the best single fixed kernel
                # to the selective system's own outputs and it cannot reproduce them:
                residual = fit_fixed_kernel(selective_outputs)   # stays well above zero

                # So training uses a parallel scan fused into one CUDA kernel. The
                # expanded state (16x the model width) is materialised in SRAM only and
                # recomputed in the backward pass -- the same recomputation-for-traffic
                # trade Flash Attention makes.

                # Buys: an inference state that does not grow with the sequence.
                mamba_state   = 2 * 2048 * 16 * 2          # 128 KB per layer, constant
                kv_cache_1m   = 2 * 1_048_576 * 32 * 64 * 2  # 8 GB per layer at 1M tokens
                print(kv_cache_1m / mamba_state)             # 65,536x
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF06B6D4, "Long-Sequence Modelling", "Genomics, audio and raw byte streams at lengths attention cannot reach."),
        ApplicationCard("chip", 0xFF10B981, "Constant-Memory Serving", "Throughput that does not degrade as the conversation grows."),
        ApplicationCard("help", 0xFFF59E0B, "Content-Based Reasoning", "The task class LTI systems provably cannot do, and the reason Mamba exists."),
        ApplicationCard("stack", 0xFF8B5CF6, "Hybrid Stacks", "A few attention layers among many Mamba layers is the common compromise."),
    ),
    takeaways = listOf(
        "Score the signal's contribution against a filler-only control — the raw recovered value hides the decaying arm's failure entirely.",
        "A fixed decay forgets the signal and the fillers on the same timer: 0.6⁷ = 0.028 of the signal survives seven fillers.",
        "Only input-dependent Ā and B̄ do both: the gate writes the signal and then closes on every filler, keeping it intact.",
        "Selectivity costs the convolution — a time-varying kernel has no fixed K — which is why Mamba needs a fused parallel scan.",
    ),
    crossLinks = listOf(
        CrossLink("ssm", "State Space Models"),
        CrossLink("rwkv", "RWKV"),
        CrossLink("lstm_gru", "LSTM & GRU"),
        CrossLink("flash_attention", "Flash Attention"),
    ),
)
