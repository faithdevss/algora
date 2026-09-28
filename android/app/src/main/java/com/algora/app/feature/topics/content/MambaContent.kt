package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mambaContent = TopicContent(
    topicId = "mamba",
    whatIsIt = listOf(
        "Mamba makes a state space model's parameters depend on the token. In an LTI system Ā, B̄ and C are the same at every position, so the model decides what to remember before it has seen anything. Mamba computes Δ, B and C from the current input, which means the recurrence can choose to write a token into the state or to hold the state unchanged and ignore it.",
        "The lab runs the selective-copying task the paper is built around, on three one-channel systems that differ only in that. One token worth remembering arrives first, then filler tokens carrying a non-zero value, then a read-out. The measurement is the *signal contribution* — the difference the signal token makes to the answer — because the raw recovered value flatters the decaying arm badly: at 100 fillers it reports 0.700 against a target of 1.0, which reads like a 30% error and is in fact the filler steady state with no trace of the signal in it at all.",
        "Measured that way the two time-invariant arms fail in opposite directions, and neither failure is fixable by tuning. A fixed decay of 0.90 can forget the fillers, which is what you want — but it forgets on a timer, so the signal's contribution collapses from 3.0e-2 to **8.0e-7 over 100 fillers**, a factor of 37,000. No decay at all (a = 1.00) forgets nothing, including every filler: the state reaches 71.0, of which the signal is a fixed 0.300 and the rest is noise the system had no way to refuse. The selective arm holds **0.9817 at every distance**, because holding costs it nothing.",
        "Selectivity is not free: it costs the convolution. An LTI system is one fixed kernel, which is why SSMs can train in parallel through an FFT; a selective one has a different kernel at every position, and the best single fixed kernel fitted to this system's own outputs still leaves a residual of 0.721. That is why Mamba needs a hardware-aware parallel scan instead — the scan survives input-dependence, the convolution does not. What it buys at inference is a state that does not grow: 64 KB per layer whatever the length, against a transformer layer's KV cache 131,072× larger at 1M tokens.",
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
        FormulaEntry("Decaying LTI", "3.0e-2 → 8.0e-7 over 100 fillers", "A 37,000× collapse in signal contribution."),
        FormulaEntry("Lossless LTI", "signal 0.300 inside a state of 71.0", "Remembers everything, including what it should ignore."),
        FormulaEntry("Selective", "0.9817 at every distance", "The residual is the gate's own softness, not decay."),
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
                residual = 0.721

                # So training uses a parallel scan fused into one CUDA kernel. The
                # expanded state (16x the model width) is materialised in SRAM only and
                # recomputed in the backward pass -- the same recomputation-for-traffic
                # trade Flash Attention makes.

                # Buys: an inference state that does not grow with the sequence.
                mamba_state   = 2 * 2048 * 16 * 2          # 64 KB per layer, constant
                kv_cache_1m   = 2 * 1_048_576 * 32 * 64 * 2  # 8 GB per layer at 1M tokens
                print(kv_cache_1m / mamba_state)             # 131,072x
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
        "A fixed decay forgets on a timer: the signal's contribution collapsed 37,000× over 100 filler tokens.",
        "No decay forgets nothing, including the filler — the signal was 0.300 of a state that had reached 71.0.",
        "Only an input-dependent Δ does both, holding 0.9817 at every distance because holding costs it nothing.",
        "Selectivity costs the convolution — a time-varying kernel has no fixed K — which is why Mamba needs a fused parallel scan.",
    ),
    crossLinks = listOf(
        CrossLink("ssm", "State Space Models"),
        CrossLink("rwkv", "RWKV"),
        CrossLink("lstm_gru", "LSTM & GRU"),
        CrossLink("flash_attention", "Flash Attention"),
    ),
)
