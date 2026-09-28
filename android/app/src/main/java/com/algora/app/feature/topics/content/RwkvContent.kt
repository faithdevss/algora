package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rwkvContent = TopicContent(
    topicId = "rwkv",
    whatIsIt = listOf(
        "RWKV replaces attention with a weighted average that has no query in it. A token's weight is exp(k) — how much it asked to be remembered — times exp(−w·distance), a decay the model learns once per channel, plus a bonus u on the current token so the present is not drowned by the past. Both sums are carried in a fixed-size state, so training looks like a parallel operator and inference looks like an RNN.",
        "Because there is no query, both sums can be accumulated incrementally and rescaled by a running maximum — exactly the trick Flash Attention uses, arrived at independently for the same reason. Written the textbook way the operator overflows a float64 once a key reaches 720; the stabilised form agrees with it to 4e-16 everywhere it is defined. The decay also has to be applied *before* the new token is folded in rather than after; getting that order wrong makes the two forms disagree by 5e-2 rather than 1e-16, which is small enough to look like rounding and is not.",
        "What it gives up is retrieval at distance, and the lab prices it. Plant a needle with a strong key and ask how much of the read-out it accounts for. Softmax attention can aim its query at that key and still holds **99% of the weight 100 tokens back**. RWKV's weight was decided when the needle was written, so it falls with distance whatever the reader wants: 69% at 5 tokens, 1.1% at 50, **0.008% at 100**. This is not a tuning problem. A weight that cannot depend on the token doing the reading cannot be aimed.",
        "What it buys is the other side of that trade. RWKV's state is (a, b, p) per channel — 24 KB, fixed — while a transformer's KV cache at 1M tokens is 96 GB, four million times larger. Constant memory per token, no quadratic prefill, and a decode step whose cost does not grow with the conversation. Which side of that trade is right is a question about the workload, not about the architecture.",
    ),
    steps = listOf(
        StepCard(1, "Score Without A Query", "A token's weight is its own key plus a learned decay. Nothing about the reader enters.", 0xFF06B6D4),
        StepCard(2, "Decay By Distance", "exp(−w·d), one w per channel, learned once and applied everywhere.", 0xFF14B8A6),
        StepCard(3, "Bonus The Present", "u gives the current token its own boost so it is not averaged away.", 0xFF10B981),
        StepCard(4, "Carry (a, b, p)", "Numerator, denominator and running maximum — the whole state, per channel.", 0xFF3B82F6),
        StepCard(5, "Decay Before Folding", "Shift the reference, then add the token. The other order is subtly wrong.", 0xFF6366F1),
        StepCard(6, "Decode As An RNN", "Constant memory and constant time per token, at any length.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("WKV", "(Σ_{i<t} e^(k_i − w(t−1−i))·v_i + e^(u+k_t)·v_t) / (same denominators)", "A decaying weighted average."),
        FormulaEntry("Stable form", "carry (a, b, p); rescale by exp(p_old − p_new)", "Agrees to 4e-16; the naive form NaNs at k = 720."),
        FormulaEntry("Needle share, RWKV", "69% at d=5 · 1.1% at d=50 · 0.008% at d=100", "Set at write time, unreachable at read time."),
        FormulaEntry("Needle share, attention", "99.9% · 99.4% · 98.8%", "A matched query holds the needle at any distance."),
        FormulaEntry("State", "3 floats per channel — 24 KB, constant", "Against 96 GB of KV cache at 1M tokens."),
        FormulaEntry("Decode cost", "O(1) per token", "No prefill quadratic, no cache growth."),
    ),
    notationKey = listOf(
        NotationEntry("R, W, K, V", "receptance, weight (decay), key, value — the four projections"),
        NotationEntry("w", "the per-channel time-decay, learned once and fixed across positions"),
        NotationEntry("u", "the bonus applied to the current token only"),
        NotationEntry("p", "the running maximum that keeps the exponentials in range"),
        NotationEntry("linear attention", "attention whose weights factor so the state can be summarised"),
        NotationEntry("receptance", "a sigmoid gate on the output — how much of the read-out is let through"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The WKV recurrence, stabilised",
            accentColor = 0xFF06B6D4,
            code = """
                import math

                def wkv(keys, values, w=0.10, u=1.0):
                    a, b, p = 0.0, 0.0, -math.inf     # numerator, denominator, running max
                    out = []
                    for k, v in zip(keys, values):
                        # Read out first: the current token gets the bonus, the state does not.
                        q = max(p, u + k)
                        e1 = 0.0 if p == -math.inf else math.exp(p - q)
                        e2 = math.exp(u + k - q)
                        out.append((e1 * a + e2 * v) / (e1 * b + e2))
                        # Then fold the token in. Decay shifts the reference BEFORE the add --
                        # doing it after decays the current token too, and the two forms then
                        # differ by 5e-2 instead of 1e-16.
                        shifted = p - w
                        q = max(shifted, k)
                        e1 = 0.0 if p == -math.inf else math.exp(shifted - q)
                        e2 = math.exp(k - q)
                        a, b, p = e1 * a + e2 * v, e1 * b + e2, q
                    return out
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Pricing the trade",
            accentColor = 0xFF3B82F6,
            code = """
                # A needle with a strong key, planted d tokens back. How much of the
                # read-out does it account for?

                #   d      RWKV       softmax attention (query aimed at the needle)
                #   5      0.685      0.999
                #   20     0.205      0.998
                #   50     0.011      0.994
                #   100    0.00008    0.988
                #   500    ~0         0.942

                # RWKV's weight is exp(k - w*d): fixed when the needle was written.
                # Attention's is exp(q.k): chosen by whoever is reading, right now.
                # No decay schedule fixes this -- it is what "no query" means.

                # The other side of the same trade:
                rwkv_state = 3 * 2048 * 4                          # 24 KB, any length
                kv_1m      = 2 * 1_048_576 * 24 * 16 * 64 * 2      # 96 GB at 1M tokens
                print(kv_1m / rwkv_state)                          # 4,194,304x
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF06B6D4, "Edge Deployment", "Constant state means a model that runs where a KV cache would not fit."),
        ApplicationCard("music", 0xFF10B981, "Streaming Inference", "Per-token cost that does not grow as the conversation does."),
        ApplicationCard("help", 0xFFF59E0B, "Retrieval-Heavy Tasks", "Exactly where the missing query costs the most — measure before switching."),
        ApplicationCard("flask", 0xFF8B5CF6, "Linear Attention", "The broader family: summarise the past into a fixed state."),
    ),
    takeaways = listOf(
        "RWKV weights a token by its own key and a learned decay — there is no query, so the weight cannot be aimed at read time.",
        "That costs retrieval at distance: a needle holds 0.008% of the read-out at 100 tokens against attention's 98.8%.",
        "It buys a 24 KB constant state against 96 GB of KV cache at 1M tokens, and a decode cost that never grows.",
        "The running-maximum rescale is the same trick Flash Attention uses; without it the operator NaNs at a key of 720.",
        "Apply the decay before folding in the new token, not after — the wrong order is a 5e-2 error that reads as rounding.",
    ),
    crossLinks = listOf(
        CrossLink("ssm", "State Space Models"),
        CrossLink("mamba", "Mamba"),
        CrossLink("flash_attention", "Flash Attention"),
        CrossLink("attention", "Attention"),
    ),
)
