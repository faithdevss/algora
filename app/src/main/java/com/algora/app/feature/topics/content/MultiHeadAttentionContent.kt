package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val multiHeadAttentionContent = TopicContent(
    topicId = "multi_head_attention",
    whatIsIt = listOf(
        "Multi-head attention splits the model dimension into h slices, runs the same scaled dot-product attention inside each one, and concatenates the results. The first thing to be precise about is the cost, because it is nothing: Q, K, V and the output projection are four d×d matrices at every head count. At d = 64 that is 16,384 parameters whether h is 1 or 16. Heads are a partition of a fixed budget, not an addition to it.",
        "The usual explanation — that a single head cannot represent as many patterns — is a rank argument, and at realistic widths it is the wrong way round. A head's score matrix is QKᵀ with an inner dimension of d/h, so its rank is capped at d/h; a *single* head has the full d and can represent anything a short sequence needs. The rank ceiling only bites when heads get thin: fitting four summed alignment patterns needs rank 12, and at h = 8 each head has 8 dimensions and 13.4% error, at h = 16 it has 4 and 37.1%. Too many heads is a real failure mode.",
        "What the split actually buys is simultaneous reads. One head emits one distribution per query, so attending to two positions at once means splitting the mass between them and receiving a blend of both values. Measured over 200 random value pairs, the best a single head can do at reading two positions into two halves of its output is α = 0.50 and 0.697 relative error; two heads do it exactly. That is the mechanism — and it also predicts the honest result on the copy task in the lab, where the task has exactly one alignment to learn and four heads score 0.842 against one head's 0.867. Heads help when there is more than one thing to attend to.",
    ),
    steps = listOf(
        StepCard(1, "Split", "d dimensions into h slices of d/h — same matrices, partitioned.", 0xFF6366F1),
        StepCard(2, "Attend Per Head", "Each slice scores and mixes independently.", 0xFF3B82F6),
        StepCard(3, "Concatenate", "h contexts of d/h back into one d-vector.", 0xFF10B981),
        StepCard(4, "Project Out", "One more d×d matrix, and the block is done.", 0xFF8B5CF6),
        StepCard(5, "Count the Cost", "4d² regardless of h — the split is free.", 0xFFF59E0B),
        StepCard(6, "Choose h", "Reads against expressiveness per read; ~64 dims each in practice.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Per head", "headᵢ = Attention(QWqᵢ, KWkᵢ, VWvᵢ)", "Each projection is d × d/h."),
        FormulaEntry("Combined", "MHA = concat(head₁…head_h)·Wo", "The concatenation is back to d."),
        FormulaEntry("Parameter count", "4d² (+ biases)", "16,384 at d = 64, at every head count."),
        FormulaEntry("Rank ceiling", "rank(QKᵀ) ≤ d/h", "8 dimensions at h = 8 → 13.4% error on a rank-12 target."),
        FormulaEntry("Simultaneous read", "one head: α = 0.50, error 0.697", "Two heads: exact."),
        FormulaEntry("Measured on one alignment", "1 head 0.867 · 4 heads 0.842", "Nothing for the extra heads to do."),
    ),
    notationKey = listOf(
        NotationEntry("h", "number of heads"),
        NotationEntry("d", "model dimension; each head works in d/h of it"),
        NotationEntry("d/h", "head dimension — 64 in most published transformers, whatever d is"),
        NotationEntry("rank", "how many independent patterns one head's score matrix can express"),
        NotationEntry("Wo", "the output projection applied after concatenation"),
        NotationEntry("simultaneous read", "attending to two positions at full weight rather than blending them"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The split is a reshape, which is why it is free",
            accentColor = 0xFF6366F1,
            code = """
                import torch
                import torch.nn as nn

                class MultiHeadAttention(nn.Module):
                    def __init__(self, d_model, heads):
                        super().__init__()
                        assert d_model % heads == 0
                        self.h, self.dk = heads, d_model // heads
                        # Four matrices. Their sizes do not depend on `heads`.
                        self.q, self.k, self.v, self.o = (nn.Linear(d_model, d_model) for _ in range(4))

                    def forward(self, x):
                        b, n, d = x.shape
                        def split(t):                       # (b, n, d) -> (b, h, n, dk)
                            return t.view(b, n, self.h, self.dk).transpose(1, 2)
                        q, k, v = split(self.q(x)), split(self.k(x)), split(self.v(x))
                        scores = q @ k.transpose(-2, -1) / self.dk ** 0.5
                        context = scores.softmax(-1) @ v
                        return self.o(context.transpose(1, 2).reshape(b, n, d))

                # sum(p.numel() for p in MultiHeadAttention(64, 1).parameters())   -> 16640
                # sum(p.numel() for p in MultiHeadAttention(64, 16).parameters())  -> 16640
                # Identical. The heads are a view of the same weights, not more of them.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Check whether your heads are doing different jobs",
            accentColor = 0xFFEC4899,
            code = """
                import torch

                @torch.no_grad()
                def head_similarity(weights):
                    "weights: (heads, queries, keys) attention from one layer, averaged over a batch."
                    flat = weights.flatten(1)
                    flat = flat / flat.norm(dim=1, keepdim=True)
                    return flat @ flat.T          # pairwise cosine between head patterns

                # Near-1 off-diagonal entries mean heads have collapsed onto the same pattern --
                # a well-documented outcome, and the finding behind "sixteen heads are better
                # than one?" (Michel et al., 2019), where most heads could be pruned at inference
                # with little loss. Measure before you assume the heads are specialising.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF6366F1, "Every Transformer", "The default attention block since 2017, at 64 dimensions per head."),
        ApplicationCard("flask", 0xFF8B5CF6, "Interpretability", "Induction heads, name-mover heads: found by reading individual heads."),
        ApplicationCard("finance", 0xFF10B981, "Head Pruning", "Many heads can be removed at inference — measure which first."),
        ApplicationCard("help", 0xFFF59E0B, "Choosing h", "Keep d/h near 64; more heads at a fixed d makes each one thinner."),
    ),
    takeaways = listOf(
        "Heads partition the model dimension; the block is 4d² parameters at every head count — 16,384 at d = 64.",
        "The rank story is backwards at realistic widths: one full-width head can express what a short sequence needs.",
        "Rank does bite when heads are thin — 13.4% error at 8 dimensions per head on a rank-12 target, 37.1% at 4.",
        "What the split really buys is simultaneous reads: one head must blend two positions (error 0.697), two heads read both exactly.",
        "On a task with a single alignment, four heads score 0.842 against one head's 0.867 — heads are not free accuracy.",
        "The four heads still specialised: attention mass on the copied position ranged 0.47 to 0.92 across them.",
        "Published models hold d/h ≈ 64 from BERT-base to GPT-3, which is what these two measurements jointly recommend.",
    ),
    crossLinks = listOf(
        CrossLink("self_cross_attention", "Self- vs Cross-Attention"),
        CrossLink("attention", "Attention"),
        CrossLink("transformers", "Transformers"),
        CrossLink("feed_forward", "Feed-Forward Networks"),
        CrossLink("bert", "BERT"),
    ),
)
