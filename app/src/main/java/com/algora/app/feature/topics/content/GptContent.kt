package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gptContent = TopicContent(
    topicId = "gpt",
    whatIsIt = listOf(
        "GPT is the same transformer block as BERT with one line added to the attention: positions to the right of the query are set to −∞ before the softmax. That single mask is the whole architectural difference between a model that reconstructs text and one that continues it. Drawn as a matrix it is exactly triangular — over six tokens, bidirectional attention scores 36 pairs and the causal version scores 21 — and the first position attends only to itself, which is why the first token of a sequence carries no information about anything.",
        "What the mask buys is that every position becomes a training target. The same forward pass that gives a masked model 10 predictions on the lab's corpus gives a causal model 64, and the objective is exactly the task the model performs at inference: predict what comes next. There is no [MASK] token to create a pre-train/fine-tune mismatch and no corruption rule to tune. What it costs is context — each prediction sees only what is to its left, averaging 2.72 tokens against a masked model's 5.44 on the same corpus.",
        "The mask also makes generation cheap in a way a bidirectional model can never be. Because no position attends rightwards, the keys and values of tokens already generated never change, so they can be cached: each new token costs one row of scores instead of a whole matrix. Over a six-token generation that is 21 score computations instead of 56. GPT-2 small, summed from its config, is 124,439,808 parameters — the paper reports 117M, a 6.4% difference, and the released checkpoint has the larger number. Its layers are byte-for-byte the size of BERT's; what differs is the vocabulary bill, 31.6% of the model against BERT's 21.8%.",
    ),
    steps = listOf(
        StepCard(1, "Mask the Future", "Set scores for j > i to −∞ before the softmax.", 0xFF6366F1),
        StepCard(2, "Predict Every Position", "n targets per sequence, not 0.15n.", 0xFF10B981),
        StepCard(3, "Train As You Serve", "The objective is the inference task, exactly.", 0xFF3B82F6),
        StepCard(4, "Cache the Keys", "Nothing to the left changes, so nothing needs recomputing.", 0xFF8B5CF6),
        StepCard(5, "Generate", "Sample, append, repeat — one row of scores per token.", 0xFFF59E0B),
        StepCard(6, "Count the Model", "124,439,808, against a reported 117M.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "maximise Σ log P(xᵢ | x<ᵢ)", "Every position, left context only."),
        FormulaEntry("Causal mask", "scoreᵢⱼ = −∞ for j > i", "One line; the whole difference from BERT."),
        FormulaEntry("Pairs scored", "n(n+1)/2 vs n²", "21 of 36 at n = 6."),
        FormulaEntry("Targets per pass", "n vs 0.15n", "64 against 10 — 6.4× more."),
        FormulaEntry("KV cache", "2·layers·d·tokens", "18,874,368 values for GPT-2 small at 1,024 tokens."),
        FormulaEntry("GPT-2 small size", "124,439,808", "Summed from the config; the paper says 117M."),
    ),
    notationKey = listOf(
        NotationEntry("x<ᵢ", "everything before position i — all a causal model may read"),
        NotationEntry("causal mask", "the triangular pattern that removes rightward attention"),
        NotationEntry("KV cache", "stored keys and values for tokens already generated"),
        NotationEntry("decoder-only", "one stack, causally masked, no encoder and no cross-attention"),
        NotationEntry("byte-level BPE", "GPT's tokenizer — 50,257 pieces and no [UNK] token at all"),
        NotationEntry("context window", "1,024 positions for GPT-2 small; a learned embedding per position"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The mask, and the cache the mask makes possible",
            accentColor = 0xFF6366F1,
            code = """
                import torch
                import torch.nn.functional as F

                def causal_attention(q, k, v):
                    scores = q @ k.transpose(-2, -1) / q.size(-1) ** 0.5
                    n = scores.size(-1)
                    mask = torch.ones(n, n, dtype=torch.bool).tril()
                    return F.softmax(scores.masked_fill(~mask, float("-inf")), -1) @ v

                @torch.no_grad()
                def generate(model, prompt, steps, temperature=1.0):
                    tokens = prompt
                    cache = None
                    for _ in range(steps):
                        # First call sees the whole prompt; later calls pass ONE token, because
                        # every earlier key and value is already in the cache and can never change.
                        logits, cache = model(tokens if cache is None else tokens[:, -1:], cache=cache)
                        next_token = torch.multinomial((logits[:, -1] / temperature).softmax(-1), 1)
                        tokens = torch.cat([tokens, next_token], dim=1)
                    return tokens

                # Without the cache, generating n tokens recomputes the whole prefix every step:
                # sum(i(i+1)/2) instead of sum(i) score computations. The mask is what makes the
                # cheaper version correct.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Sum it yourself — the reported figure is not the checkpoint",
            accentColor = 0xFFEC4899,
            code = """
                def gpt2_parameters(vocab=50257, d=768, layers=12, ffn=3072, positions=1024):
                    embeddings = vocab * d + positions * d          # no token-type, no embedding LN
                    attention = 4 * (d * d + d)
                    feed_forward = (d * ffn + ffn) + (ffn * d + d)
                    per_layer = attention + feed_forward + 2 * (2 * d)
                    return embeddings + layers * per_layer + 2 * d  # final layer norm

                print(gpt2_parameters())     # 124439808
                # The paper reports 117M. The gap is 6.4%, and it matters twice: once when you are
                # comparing model sizes across papers, and once when you are sizing a GPU. Note
                # also what is NOT here -- the output projection is tied to the input embedding,
                # so a 50,257 x 768 matrix is counted exactly once.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF6366F1, "Generation", "Every chat model in use is this mask plus scale plus alignment."),
        ApplicationCard("finance", 0xFF10B981, "Serving Cost", "The KV cache is the memory that decides batch size and context limits."),
        ApplicationCard("book", 0xFF8B5CF6, "Completion Tasks", "Code completion, autocomplete, continuation — all left-to-right by nature."),
        ApplicationCard("help", 0xFFF59E0B, "Where BERT Wins", "Classification and retrieval, where the whole input is available at once."),
    ),
    takeaways = listOf(
        "One triangular mask separates a decoder from an encoder; everything else in the block is identical.",
        "It costs half the attention matrix — 21 of 36 pairs at n = 6 — and the first position sees only itself.",
        "It buys every position as a target: 64 predictions against a masked model's 10 on the same corpus.",
        "And it costs context per prediction: 2.72 tokens against 5.44, measured on that corpus.",
        "Because nothing attends rightwards, keys and values are cacheable: 21 score computations instead of 56 over six tokens.",
        "GPT-2 small summed from its config is 124,439,808 parameters; the paper's 117M is 6.4% low.",
        "Its layers are the same size as BERT-base's — the difference is a 50,257-piece vocabulary and a 1,024-position context.",
    ),
    crossLinks = listOf(
        CrossLink("bert", "BERT"),
        CrossLink("transformers", "Transformers"),
        CrossLink("gpt3_gpt4", "GPT-3 & GPT-4"),
        CrossLink("llms", "LLMs"),
        CrossLink("self_cross_attention", "Self- vs Cross-Attention"),
    ),
)
