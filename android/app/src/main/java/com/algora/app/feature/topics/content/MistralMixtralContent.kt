package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mistralMixtralContent = TopicContent(
    topicId = "mistral_mixtral",
    figure = Figure(
        caption = "Mixtral 8x7B's two parameter counts, against a dense Mistral 7B. Every block " +
            "holds eight feed-forward experts and a router sends each token, at each layer, to " +
            "just two, so a token touches 12.9B of the 46.7B parameters — 27.6%, a 72% saving in " +
            "FLOPs against a dense model of the full size. That is the bar the \"runs like a 13B\" " +
            "description is about. The tall bar is the one it leaves out: because the route is " +
            "chosen per token, any expert may be needed next, so all 46.7B must be resident — " +
            "about 6.7× the memory of the dense 7B. MoE buys quality per FLOP and pays in bytes. " +
            "The router also has to be kept honest during training: left alone, a slightly " +
            "favoured expert gets more gradient and more traffic until the others go dead, which " +
            "is what the auxiliary load-balancing loss (1.0 at perfect balance) is there to stop.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("Mixtral, resident", 1f, FigureTone.Warn),
                FigureBar("Mixtral, per token", 0.276f, FigureTone.Accent),
                FigureBar("Mistral 7B, dense", 0.156f, FigureTone.Muted),
            ),
            yLabel = "parameters, 0 to 46.7B",
        ),
    ),
    whatIsIt = listOf(
        "Mistral 7B's contribution was engineering discipline rather than a new idea: grouped-query attention to shrink the KV cache, sliding-window attention to bound the cost of long inputs, and an aggressively over-trained 7B that outperformed models twice its size. Mixtral's contribution is structural — replace each block's feed-forward network with eight of them and route every token to just two.",
        "The routing is per token *and* per layer. A small linear router scores all eight experts, the top two are kept, their softmax weights mix the two outputs, and the next token in the same sentence routes somewhere else entirely. Left alone this collapses: a slightly favoured expert receives more gradient, improves, is favoured more, and the rest go dead. An auxiliary load-balancing loss — experts × Σ (fraction of tokens routed) × (mean gate probability), 1.0 at perfect balance and 1.13 on the lab's short strip — is added to the training objective specifically to break that feedback loop.",
        "The trade is worth stating precisely, because it is routinely mis-stated as \"a 47B model that runs like a 13B\". Mixtral 8x7B has 46.7B total parameters and activates 12.9B per token, so it uses about 28% of the FLOPs a dense model of that size would need — a 72% compute saving. What it does *not* save is memory: every expert must be resident because any token might route to it, so serving needs roughly 6.7× the VRAM of a dense 7B. MoE buys quality per FLOP and pays for it in bytes. Two second-order costs follow: batching is harder because a batch can touch every expert at once, and fine-tuning overfits more readily than a dense model of equal active size.",
    ),
    steps = listOf(
        StepCard(1, "Replicate the FFN", "Eight copies per layer; attention and embeddings stay shared.", 0xFFEC4899),
        StepCard(2, "Score the Experts", "A linear router produces one logit per expert, per token.", 0xFFF43F5E),
        StepCard(3, "Take the Top-k", "k = 2 in Mixtral; renormalise their softmax weights.", 0xFF8B5CF6),
        StepCard(4, "Run and Mix", "Only the chosen experts execute; outputs are weighted-summed.", 0xFF6366F1),
        StepCard(5, "Balance the Load", "Add the auxiliary loss, or one expert wins and the rest die.", 0xFF3B82F6),
        StepCard(6, "Budget the Memory", "All experts resident — plan VRAM by total parameters, not active ones.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("MoE layer", "y = Σ_{i ∈ top-k} softmax(router(x))ᵢ · Expertᵢ(x)", "Per token, per layer."),
        FormulaEntry("Mixtral 8x7B", "46.7B total, 12.9B active", "27.6% of parameters used per token — a 72% compute saving."),
        FormulaEntry("Memory penalty", "6.7× a dense 7B", "Every expert must be resident; routing is data-dependent."),
        FormulaEntry("Load-balancing loss", "E · Σᵢ fᵢ · Pᵢ", "1.0 at perfect balance; 1.13 measured on the lab's strip."),
        FormulaEntry("Observed imbalance", "busiest expert takes 2.0× the mean", "Which is exactly what the auxiliary loss exists to punish."),
        FormulaEntry("Not 47B-quality", "quality lands well above the active size", "Mixtral (12.9B active) matches LLaMA-2 70B; memory is still paid on the total."),
    ),
    notationKey = listOf(
        NotationEntry("expert", "one replica of the block's feed-forward network"),
        NotationEntry("router / gate", "the linear layer choosing which experts see a token"),
        NotationEntry("top-k routing", "activating the k highest-scoring experts; k = 2 in Mixtral"),
        NotationEntry("sparse activation", "using a fraction of parameters per token — the source of the compute saving"),
        NotationEntry("load balancing", "the auxiliary loss preventing expert collapse"),
        NotationEntry("expert capacity", "the per-batch cap on tokens an expert accepts; overflow tokens are dropped"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A top-2 router",
            accentColor = 0xFFEC4899,
            code = """
                import torch, torch.nn as nn, torch.nn.functional as F

                class MoELayer(nn.Module):
                    def __init__(self, d_model, hidden, n_experts=8, k=2):
                        super().__init__()
                        self.router  = nn.Linear(d_model, n_experts, bias=False)
                        self.experts = nn.ModuleList(FeedForward(d_model, hidden) for _ in range(n_experts))
                        self.k = k

                    def forward(self, x):                      # x: (tokens, d_model)
                        logits = self.router(x)
                        weights, idx = torch.topk(logits, self.k, dim=-1)
                        weights = F.softmax(weights, dim=-1)    # renormalise over the chosen k only
                        out = torch.zeros_like(x)
                        for slot in range(self.k):
                            for e, expert in enumerate(self.experts):
                                mask = idx[:, slot] == e
                                if mask.any():
                                    out[mask] += weights[mask, slot, None] * expert(x[mask])
                        return out

                def load_balancing_loss(logits, idx, n_experts):
                    f = torch.zeros(n_experts).index_add_(0, idx.flatten(),
                                                          torch.ones(idx.numel())) / idx.numel()
                    P = F.softmax(logits, dim=-1).mean(0)
                    return n_experts * (f * P).sum()            # 1.0 at perfect balance
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Sizing it honestly",
            accentColor = 0xFF14B8A6,
            code = """
                total_b, active_b, dense_b = 46.7, 12.9, 7.0

                print(f"compute saved: {1 - active_b/total_b:.0%}")   # 72%
                print(f"VRAM vs dense 7B: {total_b/dense_b:.1f}x")    # 6.7x

                # The sentence to avoid: "a 47B model that runs like a 13B". Compute scales with
                # ACTIVE parameters; memory scales with TOTAL. Quality lands well above a dense model
                # of the active size -- Mixtral matches LLaMA-2 70B -- but memory is paid on the total.
                #
                # Two more costs the FLOP number hides:
                #   - batching: a batch of 32 tokens can touch all 8 experts, so the "only 2 run"
                #     saving is per token, not per batch.
                #   - fine-tuning: MoE overfits more readily than a dense model of equal active
                #     size; the extra capacity has to be regularised.

                # Serving in practice:
                from vllm import LLM
                llm = LLM("mistralai/Mixtral-8x7B-Instruct-v0.1", tensor_parallel_size=2)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFEC4899, "Frontier Serving", "Most large hosted models are believed to be MoE — it is how capacity scales without FLOPs."),
        ApplicationCard("finance", 0xFF8B5CF6, "Cost per Token", "MoE is the main lever for quality at a fixed inference budget."),
        ApplicationCard("globe", 0xFF6366F1, "Multilingual & Multi-Domain", "Specialised experts are a natural fit when the input distribution is genuinely mixed."),
        ApplicationCard("help", 0xFF14B8A6, "Where It Bites", "VRAM, batching efficiency and fine-tuning stability all get worse."),
    ),
    takeaways = listOf(
        "Mixtral replaces each FFN with 8 experts and routes every token to 2 — per token and per layer.",
        "46.7B total, 12.9B active: about 28% of the FLOPs, a 72% compute saving.",
        "Memory is not saved — all experts stay resident, ≈6.7× a dense 7B's VRAM.",
        "Without the load-balancing loss routing collapses; the lab's strip already shows a 2.0× imbalance.",
        "Mistral 7B's separate lesson: grouped-query and sliding-window attention plus heavy over-training beat larger models.",
    ),
    crossLinks = listOf(
        CrossLink("feed_forward", "Feed-Forward Networks"),
        CrossLink("llama_vicuna", "LLaMA & Vicuna"),
        CrossLink("claude_gemini", "Claude & Gemini"),
        CrossLink("llms", "LLMs"),
    ),
)
