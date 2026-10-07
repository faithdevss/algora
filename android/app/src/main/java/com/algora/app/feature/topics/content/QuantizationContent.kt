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

internal val quantizationContent = TopicContent(
    topicId = "quantization",
    figure = Figure(
        caption = "Two ways to spend the same four bits: the sixteen levels each scheme can store, " +
            "in order, over a weight range scaled to −1…+1. int4 with an absmax scale spaces them " +
            "evenly — the straight dashed line. NF4 places them at the quantiles of a normal " +
            "distribution, so they crowd near zero, where trained weights actually sit, and thin " +
            "out at the tails: the step between its two levels nearest zero is 0.08, against 0.28–0.30 " +
            "between its two outermost. Nothing is learned — the NF4 table is fixed — and on the " +
            "same 4,096-weight Gaussian sample that placement alone takes MSE from 0.026 to 0.012, " +
            "about half the error. The flat ends of the dashed line are also its weakness: an " +
            "absmax scale is set by the single largest weight, so one 20σ outlier stretches the " +
            "whole grid and the other 4,095 weights pay about 23× the error — which is why real " +
            "4-bit kernels give every 64 weights their own scale.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "int4, uniform",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.067f, 0.067f), FigurePoint(0.133f, 0.133f),
                        FigurePoint(0.2f, 0.2f), FigurePoint(0.267f, 0.267f), FigurePoint(0.333f, 0.333f),
                        FigurePoint(0.4f, 0.4f), FigurePoint(0.467f, 0.467f), FigurePoint(0.533f, 0.533f),
                        FigurePoint(0.6f, 0.6f), FigurePoint(0.667f, 0.667f), FigurePoint(0.733f, 0.733f),
                        FigurePoint(0.8f, 0.8f), FigurePoint(0.867f, 0.867f), FigurePoint(0.933f, 0.933f),
                        FigurePoint(1f, 1f),
                    ),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "NF4, normal quantiles",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.067f, 0.152f), FigurePoint(0.133f, 0.237f),
                        FigurePoint(0.2f, 0.303f), FigurePoint(0.267f, 0.358f), FigurePoint(0.333f, 0.408f),
                        FigurePoint(0.4f, 0.454f), FigurePoint(0.467f, 0.5f), FigurePoint(0.533f, 0.54f),
                        FigurePoint(0.6f, 0.58f), FigurePoint(0.667f, 0.623f), FigurePoint(0.733f, 0.669f),
                        FigurePoint(0.8f, 0.72f), FigurePoint(0.867f, 0.781f), FigurePoint(0.933f, 0.861f),
                        FigurePoint(1f, 1f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.467f, 0.5f, "dense near 0"),
                FigurePoint(0.933f, 0.861f, "sparse tails", FigureTone.Muted),
            ),
            xLabel = "level index, 0 → 15",
            yLabel = "level value, −1 to +1",
        ),
    ),
    whatIsIt = listOf(
        "Quantization replaces each weight with the nearest of a small set of levels and stores the index instead of the number. Four bits per weight instead of sixteen is close to a 4× reduction in the thing that decides whether a model loads at all: the lab prices a 7B model at 14.0 GB in fp16, 7.2 GB in 8-bit and 3.7 GB in 4-bit, scales included — the difference between a data-centre card and a laptop.",
        "The lab measures error on 4,096 normally distributed weights (σ = 0.02), which is what trained weights actually look like. 8-bit with a single absmax scale is nearly lossless: 255 levels, of which the bell curve uses 193, and an MSE of 3.17e−8 — 41.0 dB of signal-to-noise. The same scheme at 4 bits has 15 levels spread out to the largest weight, the rare tails get most of them, and the SNR falls to 15.8 dB — roughly 6 dB lost for every bit removed.",
        "NF4 spends the same four bits differently. Its sixteen levels sit at the quantiles of a normal distribution, so they crowd where the weights are. On a 4,096-weight Gaussian sample that halves the error of uniform 4-bit levels (MSE 0.012 against 0.026 at unit variance, 53% less) **for free**, since the levels are a fixed table rather than anything learned. That is the entire content of \"normal float\".",
        "Then one weight goes to 20σ, and the scheme that looked fine falls over. A single absmax scale is set by the largest magnitude in the tensor, so one outlier stretches the whole grid and every *other* weight is quantized more coarsely: on the same sample the error on the 4,095 innocent weights rises about **25× for 8-bit and 23× for 4-bit**. Blockwise scaling — one absmax per 64 weights — confines the damage to one block; in the lab it buys +3.7 dB at 8 bits (41.0 → 44.6) and +3.8 dB at 4 bits (15.8 → 19.6), at a cost of 0.25 bits per weight. This is why LLM.int8() isolates its outlier dimensions (vector-wise scaling plus an fp16 outlier path) and 4-bit kernels (NF4, GPTQ groups) are blockwise, and it is an activation problem before it is a weight problem.",
    ),
    steps = listOf(
        StepCard(1, "Pick A Scale", "absmax over the tensor or the block. This single choice is where outliers do their damage.", 0xFFEF4444),
        StepCard(2, "Choose Levels", "Uniform for int-N; the normal quantiles for NF4. Same width, different placement.", 0xFFF97316),
        StepCard(3, "Round And Store", "Keep the index and the scale. Dequantize on the fly in the matmul kernel.", 0xFFF59E0B),
        StepCard(4, "Go Blockwise", "One scale per 64 weights. Structural, not a tuning knob, and it fixes the outlier case.", 0xFF10B981),
        StepCard(5, "Measure The Outputs", "Weight MSE is a proxy. Layer output drift is the quantity that matters.", 0xFF3B82F6),
        StepCard(6, "Keep Sensitive Parts Wide", "Embeddings, the LM head and outlier channels usually stay in higher precision.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Absmax quantize", "q = round(w / s), s = max|w| / L", "One outlier sets s for everything sharing it."),
        FormulaEntry("8-bit error", "MSE 3.17e−8 · 41.0 dB", "σ = 0.02 weights in the lab — effectively lossless."),
        FormulaEntry("NF4 vs uniform 4-bit", "0.012 vs 0.026 (unit variance)", "About half the error at the same 4 bits, from level placement alone."),
        FormulaEntry("Outlier penalty", "≈25× (8-bit) · ≈23× (4-bit), per tensor", "Measured on the weights that are *not* the outlier."),
        FormulaEntry("Blockwise (64)", "+3.7 dB (8-bit) · +3.8 dB (4-bit)", "One scale per 64 weights, +0.25 bits per weight."),
        FormulaEntry("7B footprint", "14.0 → 7.2 → 3.7 GB", "fp16 → 8-bit → 4-bit, block scales included."),
    ),
    notationKey = listOf(
        NotationEntry("absmax", "scaling by the largest absolute value, so the extremes map to ±1"),
        NotationEntry("NF4", "4-bit normal float — 16 levels at the quantiles of a standard normal"),
        NotationEntry("blockwise", "a separate scale per fixed-size group of weights, typically 64"),
        NotationEntry("SNR", "signal-to-quantization-noise ratio in dB; every extra bit is worth about 6 dB"),
        NotationEntry("outlier", "a weight or activation far outside the distribution, which sets the shared scale"),
        NotationEntry("double quantization", "quantizing the block scales themselves, cutting their overhead from 0.5 to ≈0.127 bits per weight (fp32 scales, block 64) — QLoRA saves ≈0.37 bits per weight"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Blockwise NF4, written out",
            accentColor = 0xFFEF4444,
            code = """
                import torch

                NF4 = torch.tensor([
                    -1.0000, -0.6962, -0.5251, -0.3949, -0.2844, -0.1848, -0.0911, 0.0000,
                     0.0796,  0.1609,  0.2461,  0.3379,  0.4407,  0.5626,  0.7230, 1.0000,
                ])

                def quantize_nf4(w, block=64):
                    blocks = w.reshape(-1, block)
                    scales = blocks.abs().amax(dim=1, keepdim=True).clamp(min=1e-12)
                    idx = (blocks / scales).unsqueeze(-1).sub(NF4).abs().argmin(dim=-1)
                    return idx.to(torch.uint8), scales      # 4 bits + one fp16 scale per block

                def dequantize_nf4(idx, scales):
                    return (NF4[idx.long()] * scales).flatten()

                # The block size is the whole outlier story. Per-tensor scaling lets one
                # 20-sigma weight coarsen every other weight in the matrix by 30x.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Score the outputs, not the weights",
            accentColor = 0xFF3B82F6,
            code = """
                # Weight MSE is a proxy. What the next layer sees is the output.
                x = torch.randn(800, d)
                drift = ((x @ W.T - x @ dequantize(quantize(W)).T) ** 2).mean()

                print(drift_int8_per_tensor)   # tiny
                print(drift_int4_per_tensor)   # orders of magnitude larger
                print(drift_int4_blockwise)    # smaller again: block scales help

                # Worth doing rather than inferring. An earlier version of this lab scored
                # quantization by downstream *task loss* and reported NF4 as better than
                # fp16 -- it was watching quantization noise nudge an under-fitted model
                # around at random. Drift from the full-precision model is the honest
                # measure; task loss against a distant target is not a measure of it at all.

                # And check the outlier case explicitly, on the weights that are innocent:
                clean = [i for i in range(len(w)) if i != outlier_index]
                penalty = mse(w[clean], q_with_outlier[clean]) / mse(w[clean], q_clean[clean])
                print(penalty)                 # ~25x per-tensor; blockwise confines it to one block
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFEF4444, "On-Device Inference", "4-bit is what puts a 7B model inside a phone's memory budget."),
        ApplicationCard("finance", 0xFF10B981, "Serving Cost", "Smaller weights mean more concurrent models per card."),
        ApplicationCard("help", 0xFFF59E0B, "Outlier Channels", "A handful of activation dimensions decide whether int8 works at all."),
        ApplicationCard("flame", 0xFF3B82F6, "QLoRA", "A frozen NF4 base with full-precision adapters trained on top."),
    ),
    takeaways = listOf(
        "8-bit absmax is effectively lossless on Gaussian weights (41.0 dB in the lab); the same scheme at 4 bits drops to 15.8 dB.",
        "NF4 roughly halves uniform 4-bit's error at identical width, purely by placing its 16 levels at the normal's quantiles.",
        "One 20σ outlier raises the error on every other weight about 25× under per-tensor scaling — it stretches the shared grid.",
        "Blockwise scaling confines that damage to one 64-weight block for 0.25 extra bits per weight, which is why every real 4-bit kernel is blockwise.",
        "Score quantization by how far the layer's outputs move, not by weight MSE and never by task loss on an under-fitted model.",
    ),
    crossLinks = listOf(
        CrossLink("lora_qlora", "LoRA & QLoRA"),
        CrossLink("peft", "PEFT"),
        CrossLink("vector_databases", "Vector Databases"),
        CrossLink("distilbert", "DistilBERT"),
    ),
)
