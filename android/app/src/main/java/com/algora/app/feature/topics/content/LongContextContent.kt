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

internal val longContextContent = TopicContent(
    topicId = "long_context",
    figure = Figure(
        caption = "Attention's share of prefill FLOPs for LLaMA-2-7B (d = 4,096, FFN width 11,008) " +
            "as the context grows, from the page's per-layer formulas: 4·L²·d for attention " +
            "against 8·L·d² for the projections and 6·L·d·d_ffn for the feed-forward block. The " +
            "share is 4L / (4L + 8d + 6·d_ffn), so it depends on length alone. At 4k tokens " +
            "attention is 14% of the work and the quadratic term really was ignorable; it " +
            "passes everything else combined at 24,704 tokens, almost exactly 6·d; at 1M it is " +
            "98%. The algorithm never changed — the length did. Compute is only one of the three " +
            "prices, and not the largest: the KV cache at 1M is 512 GiB, ≈40× the weights, before " +
            "grouped-query attention divides it, and reading five retrieved passages instead of " +
            "stuffing the window costs 21,072× less prefill.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "attention share",
                    listOf(
                        FigurePoint(0f, 0.04f), FigurePoint(0.1f, 0.077f), FigurePoint(0.2f, 0.142f),
                        FigurePoint(0.3f, 0.249f), FigurePoint(0.4f, 0.399f), FigurePoint(0.5f, 0.57f),
                        FigurePoint(0.6f, 0.726f), FigurePoint(0.7f, 0.841f), FigurePoint(0.8f, 0.914f),
                        FigurePoint(0.9f, 0.955f), FigurePoint(1f, 0.977f),
                    ),
                ),
                FigureSeries(
                    "half",
                    listOf(FigurePoint(0f, 0.5f), FigurePoint(1f, 0.5f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
            ),
            markers = listOf(
                FigurePoint(0.2f, 0.142f, "4k: 14%", FigureTone.Muted),
                FigurePoint(0.459f, 0.5f, "24,704"),
                FigurePoint(1f, 0.977f, "1M: 98%", FigureTone.Warn),
            ),
            xLabel = "context, 1k → 1M tokens (log)",
            yLabel = "attention / all prefill FLOPs",
        ),
    ),
    whatIsIt = listOf(
        "A context window has three prices and the advertised number mentions none of them. The lab computes all three over LLaMA-2-7B's configuration — 32 layers, 32 heads, head dimension 128 — so every figure is checkable against a real model rather than a shape.",
        "**Price one is the KV cache**, which is linear in the length and paid at every decode step. At 4k tokens it is 2.0 GiB, already a sixth of the weights. At 1M it is **512 GiB — ≈40× the model itself**. This is the price grouped-query attention exists to cut, and it is the cheapest win in the stack: share one K/V head across a group of query heads and the cache divides by the group size, taking 1M tokens from 512 GiB to 128 GiB at GQA-8 and to 16 GiB at multi-query.",
        "**Price two is prefill arithmetic**, and this is where the quadratic warning finally bites. At 4k tokens attention is only 14% of the FLOPs — the feed-forward blocks dominate, which is why quadratic attention was ignorable for years. The crossover is at **24,704 tokens, essentially exactly 6·d for this model**, and by 1M attention is 98% of the bill. The often-repeated \"attention is quadratic\" is true and was irrelevant at the lengths anyone used; what changed is the length, not the algorithm.",
        "**Price three decides architecture.** Stuffing 1M tokens into the window costs **21,072× the prefill** of retrieving five 400-token passages and reading only those. Long context and retrieval are not competitors on capability — they are the same capability at four orders of magnitude difference in price, and the reason to stuff the window is that retrieval missed, not that stuffing is better. Finally, the window a model advertises is not the window its heads use: under ALiBi's standard slope schedule the position bias alone drives the steepest head's weight below 1% of the nearest token's at **9 tokens**, and the shallowest at 1,178. Effective context is measured, not declared.",
    ),
    steps = listOf(
        StepCard(1, "Count The Cache", "2 · L · layers · kv_heads · d_head · 2 bytes. Linear, and paid every step.", 0xFF06B6D4),
        StepCard(2, "Share The KV Heads", "GQA and MQA divide the cache by the group size for almost no quality cost.", 0xFF14B8A6),
        StepCard(3, "Find The Crossover", "Attention passes the FFN at roughly 6·d tokens. Below that it is not the problem.", 0xFF10B981),
        StepCard(4, "Price Against Retrieval", "Compare prefill FLOPs to reading only the passages that matter.", 0xFF3B82F6),
        StepCard(5, "Measure Effective Context", "Position bias and training length both cap what heads actually use.", 0xFF6366F1),
        StepCard(6, "Test In The Middle", "Retrieval accuracy at the ends is not retrieval accuracy at the midpoint.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("KV cache", "2·L·layers·kv_heads·d_head·2 bytes", "2.0 GiB at 4k · 512 GiB at 1M."),
        FormulaEntry("GQA saving", "512 → 128 → 16 GiB", "Multi-head, GQA-8, multi-query at 1M tokens."),
        FormulaEntry("Attention FLOPs", "4·L²·d per layer", "Against 8·L·d² of projections plus 6·L·d·d_ffn of FFN."),
        FormulaEntry("Crossover", "24,704 tokens ≈ 6·d", "Where attention first exceeds everything else combined."),
        FormulaEntry("Stuffing overhead", "21,072× at 1M tokens", "Versus prefilling five retrieved 400-token passages."),
        FormulaEntry("ALiBi effective window", "9 to 1,178 tokens across 8 heads", "Where the bias drops the weight below 1%."),
    ),
    notationKey = listOf(
        NotationEntry("KV cache", "the stored keys and values for every past token, per layer, per head"),
        NotationEntry("prefill", "the one-off forward pass over the whole prompt, before the first output token"),
        NotationEntry("GQA", "grouped-query attention — one K/V head shared across a group of query heads"),
        NotationEntry("MQA", "multi-query attention — a single K/V head for the whole layer"),
        NotationEntry("effective context", "the span a model actually uses, which is measured rather than advertised"),
        NotationEntry("lost in the middle", "the observed accuracy dip for information placed away from either end"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The three bills, computed",
            accentColor = 0xFF06B6D4,
            code = """
                LAYERS, HEADS, D_HEAD, D, D_FFN = 32, 32, 128, 4096, 11008

                def kv_cache_bytes(L, kv_heads=HEADS):
                    return 2 * L * LAYERS * kv_heads * D_HEAD * 2      # fp16

                def attention_flops(L):
                    return 4 * L * L * D * LAYERS

                def everything_else_flops(L):
                    return (8 * L * D * D + 6 * L * D * D_FFN) * LAYERS

                print(kv_cache_bytes(4_096) / 2**30)          # 2.0 GiB
                print(kv_cache_bytes(1_048_576) / 2**30)      # 512 GiB
                print(kv_cache_bytes(1_048_576, 8) / 2**30)   # 128 GiB with GQA-8
                print(kv_cache_bytes(1_048_576, 1) / 2**30)   # 16 GiB with MQA

                # Attention overtakes the FFN at 4L^2*d = 24.1*L*d^2, i.e. L = 6d:
                #   d = 4096  ->  24,704 tokens
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Long context or retrieval — it is a price comparison",
            accentColor = 0xFF3B82F6,
            code = """
                def prefill(L):
                    return attention_flops(L) + everything_else_flops(L)

                stuffed   = prefill(1_048_576)
                retrieved = prefill(5 * 400)      # five passages that contain the answer

                print(stuffed / retrieved)        # 21,072x

                # Both approaches can answer the question. One costs four orders of
                # magnitude more. The right reason to stuff the window is that retrieval
                # missed -- which is a recall problem, and cheaper to fix than to route
                # around at this price.

                # And check the window is real before relying on it. Under ALiBi's
                # standard slopes the position bias alone caps each head:
                slopes = [2 ** (-8 * (i + 1) / 8) for i in range(8)]
                windows = [int(-math.log(0.01) / s) for s in slopes]
                print(windows)   # [9, 18, 36, 73, 147, 294, 589, 1178]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF06B6D4, "Whole-Document Reasoning", "Contracts, codebases and transcripts that do not chunk cleanly."),
        ApplicationCard("finance", 0xFF10B981, "Serving Economics", "Cache bytes per user decide how many conversations fit on a card."),
        ApplicationCard("search", 0xFF3B82F6, "Retrieval Versus Stuffing", "Four orders of magnitude apart in price for the same answer."),
        ApplicationCard("help", 0xFFF59E0B, "Benchmark Honestly", "Needle tests at the ends do not measure the middle."),
    ),
    takeaways = listOf(
        "The KV cache at 1M tokens is 512 GiB — ≈40× the 7B model's own weights — and it is paid at every decode step.",
        "Grouped-query attention divides that by the group size: 512 GiB → 128 GiB at GQA-8, → 16 GiB at multi-query.",
        "Attention is only 14% of prefill FLOPs at 4k tokens; it overtakes everything else at 24,704 tokens, essentially 6·d.",
        "Stuffing 1M tokens costs 21,072× the prefill of retrieving five passages — the same answer at four orders of magnitude.",
        "Advertised context is not effective context: ALiBi's own slopes cap heads between 9 and 1,178 tokens.",
    ),
    crossLinks = listOf(
        CrossLink("flash_attention", "Flash Attention"),
        CrossLink("positional_encodings", "Positional Encodings"),
        CrossLink("rag", "Retrieval-Augmented Generation"),
        CrossLink("ssm", "State Space Models"),
    ),
)
