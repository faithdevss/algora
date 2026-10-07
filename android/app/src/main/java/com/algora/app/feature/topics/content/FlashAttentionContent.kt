package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val flashAttentionContent = TopicContent(
    topicId = "flash_attention",
    whatIsIt = listOf(
        "Flash Attention computes exactly the same function as standard attention and never builds the N×N score matrix. It tiles the keys and values, streams them through on-chip memory, and carries three running numbers per query block — a maximum, a denominator and an output — rescaling the accumulator whenever a new block raises the maximum. The lab runs both algorithms on the same 512 scores: they differ by 3e-16, which is floating-point noise. This is not an approximation, and the block size changes nothing about the answer.",
        "The running maximum is not an optimisation, it is what makes the sum finite. Without it every term is exp(score), which overflows a float64 above 709.78 (and far sooner in the precisions kernels actually use: ≈88.7 in fp32/bf16, ≈11.1 in fp16). A peak score of 500 survives in float64; 710 returns NaN.",
        "What it buys is memory, and that saving is enormous. The score matrix at N = 65,536 across 32 heads is **256 GiB** of activations, and the tiled kernel allocates none of it. That term is what made long-sequence training impossible and its removal is what made 128k-token training routine.",
        "The traffic saving is smaller than the folklore, and worth stating precisely because the folklore has no denominator. Standard attention moves about 4N² elements through HBM; the tiled kernel re-reads K and V once per query block, which is 2N²·d/Br. The ratio is exactly **2·Br/d** — a property of the tile size and the head dimension, with no N in it at all. At Br = 128 and d = 64 that is 4×, and it measures 4.00× at N = 65,536. It also *costs* arithmetic: the backward pass has no stored scores to read, so it recomputes QKᵀ — 16.7% more FLOPs across forward and backward. It is faster anyway, which is the lesson: on this hardware the arithmetic is nearly free and the memory traffic is not.",
    ),
    steps = listOf(
        StepCard(1, "Tile The Keys", "Split K and V into blocks small enough that a block fits in SRAM.", 0xFFEF4444),
        StepCard(2, "Carry Three Numbers", "A running max m, a running denominator ℓ, and a running output accumulator.", 0xFFF97316),
        StepCard(3, "Rescale On A New Max", "Multiply everything accumulated so far by exp(m_old − m_new), then add the block.", 0xFFF59E0B),
        StepCard(4, "Never Write S", "The N×N matrix exists one tile at a time and is never allocated.", 0xFF10B981),
        StepCard(5, "Recompute In Backward", "No stored scores means recomputing QKᵀ — more FLOPs, less traffic.", 0xFF3B82F6),
        StepCard(6, "Check It Is Exact", "Against the textbook softmax: 3e-16. Anything larger is a bug, not a tolerance.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Online softmax", "m ← max(m, m_blk); ℓ ← ℓ·e^(m_old−m) + Σ e^(s−m)", "One pass, exact, no score row held."),
        FormulaEntry("Exactness", "max difference 3e-16", "Against the naive softmax on the same 512 scores."),
        FormulaEntry("Overflow", "exp overflows above 709.78", "Which is why the running max is load-bearing."),
        FormulaEntry("Traffic ratio", "2·Br / d", "Exactly — no N in it. 4× at Br=128, d=64."),
        FormulaEntry("Score matrix", "256 GiB at N=65,536 over 32 heads", "Never allocated. This is the real saving."),
        FormulaEntry("FLOP cost", "1.167× forward+backward", "Recomputation is arithmetic bought back for traffic."),
    ),
    notationKey = listOf(
        NotationEntry("HBM", "high-bandwidth memory — large, off-chip, and the bottleneck"),
        NotationEntry("SRAM", "on-chip scratchpad — tiny, fast, and what the tile size is chosen for"),
        NotationEntry("Br", "query block size, set by how much SRAM a tile may occupy"),
        NotationEntry("d", "head dimension, typically 64 or 128"),
        NotationEntry("online softmax", "computing a softmax in one streaming pass with a running maximum"),
        NotationEntry("recomputation", "recalculating a forward quantity in the backward pass rather than storing it"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The online softmax, in full",
            accentColor = 0xFFEF4444,
            code = """
                import math

                def tiled_attention(scores, values, block=64):
                    m, l, o = -math.inf, 0.0, [0.0] * len(values[0])
                    for start in range(0, len(scores), block):
                        blk = scores[start:start + block]
                        new_m = max(m, max(blk))
                        correction = 0.0 if m == -math.inf else math.exp(m - new_m)
                        # Everything already accumulated was scaled against the old max.
                        l = l * correction + sum(math.exp(s - new_m) for s in blk)
                        for j in range(len(o)):
                            o[j] = o[j] * correction + sum(
                                math.exp(s - new_m) * values[start + i][j]
                                for i, s in enumerate(blk)
                            )
                        m = new_m
                    return [x / l for x in o]

                # Against the textbook softmax on the same scores: 3e-16 apart.
                # The block size is a hardware choice, not an accuracy choice.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Using it, and pricing it",
            accentColor = 0xFF3B82F6,
            code = """
                import torch.nn.functional as F

                # PyTorch dispatches to the fused kernel when the shapes and dtype allow it.
                out = F.scaled_dot_product_attention(q, k, v, is_causal=True)

                # What it saved, and what it cost:
                N, d, Br, heads = 65_536, 64, 128, 32

                score_matrix_gb = heads * N * N * 2 / 2**30      # 256 GiB, never allocated
                traffic_ratio   = 2 * Br / d                      # 4.0x, exactly, for any N
                flop_overhead   = 1.167                           # recomputation in backward

                # The ratio has no N in it, which is the part the "10x less IO" summary
                # loses. Bigger tiles buy more: Br = 256 gives 8x, Br = 64 gives 2x.
                # Tile size is set by SRAM, so the saving is a property of the hardware.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFEF4444, "Long-Sequence Training", "Removing the N² activation is what makes 128k-token training fit."),
        ApplicationCard("flame", 0xFF10B981, "Every Modern Stack", "The default attention kernel in PyTorch, vLLM and every serving framework."),
        ApplicationCard("check", 0xFF3B82F6, "Exactness", "Not an approximation — swap it in without re-evaluating the model."),
        ApplicationCard("help", 0xFFF59E0B, "IO-Bound Reasoning", "Trading arithmetic for memory traffic is usually the winning direction."),
    ),
    takeaways = listOf(
        "Flash Attention is exact: 3e-16 from the textbook softmax, and the block size does not affect the answer.",
        "The running maximum is what keeps the sum finite — exp overflows above 709.78 in float64 and ≈88.7 in fp32 (≈11.1 in fp16), which is why kernels subtract the running max.",
        "The real win is the 256 GiB score matrix at N=65,536 that is never allocated, not the traffic saving.",
        "The traffic saving is exactly 2·Br/d — 4× at Br=128, d=64, with no dependence on sequence length whatsoever.",
        "It costs 16.7% more FLOPs to recompute QKᵀ in the backward pass, and is faster anyway. Traffic dominates arithmetic.",
    ),
    crossLinks = listOf(
        CrossLink("self_cross_attention", "Self-Attention"),
        CrossLink("long_context", "Long Context Windows"),
        CrossLink("rwkv", "RWKV"),
        CrossLink("softmax", "Softmax"),
    ),
)
