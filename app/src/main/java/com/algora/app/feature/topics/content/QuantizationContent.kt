package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val quantizationContent = TopicContent(
    topicId = "quantization",
    whatIsIt = listOf(
        "Quantization replaces each weight with the nearest of a small set of levels and stores the index instead of the number. Four bits per weight instead of sixteen is a 4× reduction in the thing that decides whether a model loads at all: a 7B model is 13.2 GB in fp16, 6.7 GB in int8 and 3.5 GB in NF4 — the difference between a data-centre card and a laptop.",
        "The lab measures error on 4,096 normally distributed weights, which is what trained weights actually look like. int8 with a single absmax scale is nearly lossless (MSE 0.000068, 41.7 dB). The same scheme at 4 bits is not: 0.0194, some 285× worse, because sixteen levels spread uniformly over the range put most of them where almost no weights are.",
        "NF4 spends the same four bits differently. Its sixteen levels sit at the quantiles of a normal distribution, so they crowd where the weights are, and on the same tensor that is MSE 0.0115 against int4's 0.0194 — **41% less error for free**, since the levels are a fixed table rather than anything learned. That is the entire content of \"normal float\".",
        "Then one weight goes to 20σ, and the scheme that looked fine falls over. A single absmax scale is set by the largest magnitude in the tensor, so one outlier stretches the whole grid and every *other* weight is quantized more coarsely: the error on the 4,095 innocent weights rises **30× for int8 and 32× for int4**. Blockwise scaling — one absmax per 64 weights — confines the damage to one block and drops the penalty to 1.8×, at a cost of 0.25 bits per weight. This is why LLM.int8() and every 4-bit kernel are blockwise, and it is an activation problem before it is a weight problem.",
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
        FormulaEntry("Absmax quantize", "q = round(w / s · L), s = max|w| / L", "One outlier sets s for everything sharing it."),
        FormulaEntry("int8 error", "MSE 0.000068 · 41.7 dB", "Effectively lossless on Gaussian weights."),
        FormulaEntry("NF4 vs int4", "0.0115 vs 0.0194", "41% less error at the same 4 bits, from level placement alone."),
        FormulaEntry("Outlier penalty", "30× (int8) · 32× (int4), per tensor", "Measured on the weights that are *not* the outlier."),
        FormulaEntry("Blockwise", "1.8× (int4/64) · 1.5× (NF4/64)", "One scale per 64 weights, +0.25 bits per weight."),
        FormulaEntry("7B footprint", "13.2 → 6.7 → 3.5 GB", "fp16 → int8 → NF4."),
    ),
    notationKey = listOf(
        NotationEntry("absmax", "scaling by the largest absolute value, so the extremes map to ±1"),
        NotationEntry("NF4", "4-bit normal float — 16 levels at the quantiles of a standard normal"),
        NotationEntry("blockwise", "a separate scale per fixed-size group of weights, typically 64"),
        NotationEntry("SNR", "signal-to-quantization-noise ratio in dB; every extra bit is worth about 6 dB"),
        NotationEntry("outlier", "a weight or activation far outside the distribution, which sets the shared scale"),
        NotationEntry("double quantization", "quantizing the block scales themselves, for another ~0.4 bits per weight"),
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

                print(drift_int8_per_tensor)   # 0.000026
                print(drift_int4_per_tensor)   # 0.007529
                print(drift_int4_blockwise)    # 0.005597

                # Worth doing rather than inferring. An earlier version of this lab scored
                # quantization by downstream *task loss* and reported NF4 as better than
                # fp16 -- it was watching quantization noise nudge an under-fitted model
                # around at random. Drift from the full-precision model is the honest
                # measure; task loss against a distant target is not a measure of it at all.

                # And check the outlier case explicitly, on the weights that are innocent:
                clean = [i for i in range(len(w)) if i != outlier_index]
                penalty = mse(w[clean], q_with_outlier[clean]) / mse(w[clean], q_clean[clean])
                print(penalty)                 # 30.4x per-tensor, 1.8x blockwise
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
        "int8 with absmax is effectively lossless on Gaussian weights (41.7 dB); the same scheme at 4 bits is 285× worse.",
        "NF4 beats uniform int4 by 41% at identical width, purely by placing its 16 levels at the normal's quantiles.",
        "One 20σ outlier raises the error on every other weight by 30× under per-tensor scaling — it stretches the shared grid.",
        "Blockwise scaling drops that penalty to 1.8× for 0.25 extra bits per weight, which is why every real 4-bit kernel is blockwise.",
        "Score quantization by how far the layer's outputs move, not by weight MSE and never by task loss on an under-fitted model.",
    ),
    crossLinks = listOf(
        CrossLink("lora_qlora", "LoRA & QLoRA"),
        CrossLink("peft", "PEFT"),
        CrossLink("vector_databases", "Vector Databases"),
        CrossLink("distilbert", "DistilBERT"),
    ),
)
