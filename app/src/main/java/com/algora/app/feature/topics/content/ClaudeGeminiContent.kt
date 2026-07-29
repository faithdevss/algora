package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val claudeGeminiContent = TopicContent(
    topicId = "claude_gemini",
    whatIsIt = listOf(
        "The frontier families differ far less in architecture than the marketing suggests. All of them are decoder-only transformers with RoPE-family position handling, preference-based post-training, and a mixture-of-experts variant somewhere in the line-up. What actually separates them is the context window, the modality story, how they were aligned, and — increasingly — how the tier structure lets you trade capability against cost. Anthropic's Claude line and Google's Gemini line are the clearest pair to compare, because they made different bets on each of those axes.",
        "Context is the headline capability and a genuine engineering achievement rather than a config value. A million-token window means a large codebase or a few hundred thousand words of documents can go into the prompt directly, with no retrieval step and no chunking — Claude's main tier (Opus and Sonnet) and Gemini both sit there, while the smaller, faster tiers trade the window for cost. What that costs is quadratic in attention and linear in the KV cache, and the second number is the binding one: at ~2.6 MB per token for a 70B-class model, a full 1M-token context needs about 2,441 GB of cache. Grouped-query attention, sharing one key/value pair across eight query heads, cuts that to roughly 305 GB, which is the difference between impossible and merely expensive.",
        "Alignment is where the families genuinely diverge. The common baseline is RLHF: collect human preference comparisons, fit a reward model, optimise the policy against it. Anthropic's Constitutional AI replaces much of the human labelling with a written set of principles the model critiques and revises its own outputs against — cheaper to scale and, more usefully, auditable, because the rules are a document you can read rather than a distribution over annotator opinions. Gemini's distinguishing claim is native multimodality: text, images, audio and video interleaved from pretraining rather than bolted on with an adapter afterwards, on a mixture-of-experts design trained on Google's TPUs. Claude's is the opposite emphasis — a written constitution, reasoning depth the model decides how much of to spend, and an explicit tier structure so the model choice is a cost decision. Two cautions: published capability and price tables are stale within months, so treat the *shape* of the trade as the durable part; and benchmark scores between families are close enough that the deciding factors in practice are usually the boring ones — latency, context window, tool-calling reliability, deployment region, price per token.",
    ),
    steps = listOf(
        StepCard(1, "Compare Windows, Not Slogans", "Context length is the capability that changes what you can build.", 0xFFEC4899),
        StepCard(2, "Price the Cache", "KV cache, not attention FLOPs, is what bounds long context at serving time.", 0xFFF43F5E),
        StepCard(3, "Read the Alignment Method", "RLHF, Constitutional AI or DPO — it decides how the model behaves at the edges.", 0xFF8B5CF6),
        StepCard(4, "Check the Modality Story", "Native multimodal pretraining and a bolted-on adapter are not the same product.", 0xFF6366F1),
        StepCard(5, "Pick the Tier", "Small/balanced/frontier exists so that the model choice is a cost decision.", 0xFF3B82F6),
        StepCard(6, "Evaluate on Your Task", "Public benchmarks are close; your workload is not the benchmark.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Attention cost", "O(n²) — 1M tokens is ≈59,605× the 4K score matrix", "Quadratic, and the reason long context was hard at all."),
        FormulaEntry("KV cache per token", "2 · layers · heads · head_dim · 2 bytes ≈ 2.6 MB", "For a 70B-class model — the real serving constraint."),
        FormulaEntry("Full 1M context", "≈2,441 GB of KV cache", "Which no single accelerator holds."),
        FormulaEntry("Grouped-query attention", "≈305 GB at 8 query heads per KV pair", "An 8× cut — what makes the window shippable."),
        FormulaEntry("Constitutional AI", "self-critique against written principles", "Replaces much of RLHF's human labelling; the rules are auditable."),
        FormulaEntry("Tiering", "small / balanced / frontier", "So capability-versus-cost becomes a routing decision per request."),
    ),
    notationKey = listOf(
        NotationEntry("context window", "the maximum tokens a model can attend over in one request"),
        NotationEntry("KV cache", "stored keys and values for past tokens; grows linearly and dominates serving memory"),
        NotationEntry("GQA", "grouped-query attention — several query heads sharing one K/V pair"),
        NotationEntry("RLHF", "reinforcement learning from human feedback"),
        NotationEntry("Constitutional AI", "alignment by self-critique against a written set of principles"),
        NotationEntry("native multimodality", "modalities interleaved from pretraining rather than adapted in afterwards"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "What a long context costs, computed",
            accentColor = 0xFFEC4899,
            code = """
                layers, heads, head_dim, bytes_per_value = 80, 64, 128, 2      # 70B-class model
                per_token = 2 * layers * heads * head_dim * bytes_per_value    # K and V
                print(f"{per_token/1e6:.2f} MB per token")                     # ~2.62 MB

                for n in (4_096, 128_000, 1_000_000):
                    gb  = per_token * n / 1024**3
                    gqa = gb * 8 / heads          # one K/V pair shared by 8 query heads
                    print(f"{n:>9,} tokens: attention {(n/4096)**2:>10,.0f}x   "
                          f"KV {gb:>7,.0f} GB   with GQA {gqa:>6,.1f} GB")
                #     4,096 tokens: attention          1x   KV      10 GB   with GQA    1.2 GB
                #   128,000 tokens: attention        977x   KV     313 GB   with GQA   39.1 GB
                # 1,000,000 tokens: attention     59,605x   KV   2,441 GB   with GQA  305.2 GB

                # This is why "1M context" is an engineering achievement rather than a config flag,
                # and why every long-context model uses GQA (or MQA, or an attention variant).
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Calling one of them",
            accentColor = 0xFF3B82F6,
            code = """
                import anthropic

                client = anthropic.Anthropic()

                # The tier IS the cost decision: a small fast model for classification and routing,
                # a frontier one for the work that needs it. Same API shape either way.
                response = client.messages.create(
                    model="claude-opus-5",
                    max_tokens=4096,
                    system="You are reviewing a large codebase.",
                    messages=[{"role": "user", "content": whole_repository_dump}],
                )
                print(response.content[0].text)
                print(response.usage.input_tokens, response.usage.output_tokens)

                # Two habits worth forming with any vendor:
                #   1. Check stop_reason before reading content -- a refusal or a token limit is
                #      not an exception, it is a normal outcome your code has to handle.
                #   2. Measure YOUR task. Public benchmarks between frontier families are close
                #      enough that latency, context, tool-calling reliability and price usually
                #      decide the choice long before a leaderboard does.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Whole-Repository Work", "A million-token window replaces a retrieval pipeline for many codebase and document tasks."),
        ApplicationCard("chip", 0xFF8B5CF6, "Model Routing", "Tiers make per-request cost/capability routing a normal engineering decision."),
        ApplicationCard("lock", 0xFF6366F1, "Regulated Deployment", "Deployment region, data handling and auditability decide more enterprise choices than benchmarks."),
        ApplicationCard("flask", 0xFF14B8A6, "Evaluation Practice", "Vendor comparison is a measurement problem on your own workload, not a table lookup."),
    ),
    takeaways = listOf(
        "Architecturally the families converge: decoder-only, RoPE-family positions, preference post-training, MoE somewhere.",
        "Context is the differentiating capability — Claude's main tier and Gemini both reach 1M tokens.",
        "The binding cost is the KV cache: ≈2,441 GB for 1M tokens, cut to ≈305 GB by grouped-query attention.",
        "Alignment is the real divergence — RLHF's human comparisons versus Constitutional AI's written, auditable principles.",
        "Treat scores and prices as volatile; pick on context, latency, tool reliability, region and cost per token.",
    ),
    crossLinks = listOf(
        CrossLink("llms", "LLMs"),
        CrossLink("rlhf", "RLHF"),
        CrossLink("mistral_mixtral", "Mistral & Mixtral (MoE)"),
        CrossLink("attention", "Attention"),
    ),
)
