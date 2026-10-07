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

internal val feedForwardContent = TopicContent(
    topicId = "feed_forward",
    figure = Figure(
        caption = "One BERT-base block at d = 768, priced by its weight matrices. Attention's four " +
            "projections — Q, K, V and the output — are 4d² = 2,359,296 parameters. The " +
            "feed-forward network's two matrices, 768 → 3,072 and back, are 2 × 4d² = 4,718,592: " +
            "twice as many, two thirds of the block, in the sublayer the architecture is not named " +
            "after. FLOPs per token follow the same 2:1 split (9.4M against 4.7M), so until the " +
            "sequence is long enough for attention's N² term to matter, the FFN is where the time " +
            "goes too. The third bar is LLaMA's SwiGLU, which adds a gate matrix and pays for it by " +
            "shrinking the hidden width from 4d to 8/3·d — three 768 × 2,048 matrices are exactly " +
            "4,718,592 again. The bars only look the same because that was the design constraint.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("attention", 0.5f, FigureTone.Muted),
                FigureBar("FFN (4d)", 1f, FigureTone.Accent),
                FigureBar("SwiGLU (8/3·d)", 1f, FigureTone.Primary),
            ),
            yLabel = "parameters, 0 to 4.7M",
        ),
    ),
    whatIsIt = listOf(
        "Every transformer block is attention followed by a position-wise feed-forward network: two linear layers with a non-linearity between them, applied to each token independently. Attention moves information *between* positions; the FFN is the only place each position is transformed on its own. It expands d_model to 4·d_model and projects back — 768 → 3072 → 768 in BERT-base.",
        "The parameter arithmetic is the part that surprises people. Attention's four projections (Q, K, V and output) are 4d² = 2,359,296 at d = 768. The FFN's two matrices are 2 × 4d² = 4,718,592 — twice as many. **Two thirds of a transformer block is the feed-forward network** (66.7%), not the attention the architecture is named after. Per-token FLOPs follow: 9,437,184 for the FFN against 4,718,592 for attention's projections, so at short sequence lengths the FFN dominates compute as well as parameters, and attention's quadratic term only overtakes it once the sequence is long. That is why quantisation, pruning and mixture-of-experts all target the FFN first.",
        "Two changes since 2017 are worth knowing. GELU replaced ReLU: it is smooth and slightly negative for small negative inputs, so a nearly-off unit still passes some gradient rather than being hard-clipped. Then LLaMA and most models after it adopted SwiGLU, a gated unit with *three* matrices instead of two — to hold the parameter count fixed the hidden width drops from 4d to 8/3·d, which is why LLaMA-7B's FFN width is 11008 rather than 16384. The interpretation that stuck is the key-value memory reading: each row of the first matrix is a pattern detector and the matching column of the second is the value written when it fires. Activations are sparse — about 92% of hidden units sit near zero for any given token — so only a handful of these memories are read per token, which is precisely the structure mixture-of-experts exploits.",
    ),
    steps = listOf(
        StepCard(1, "Take One Token", "The FFN is position-wise: no token sees another inside it.", 0xFF3B82F6),
        StepCard(2, "Expand ×4", "d → 4d with the first matrix; the width is where the capacity lives.", 0xFF6366F1),
        StepCard(3, "Apply the Non-Linearity", "GELU by default; SwiGLU in the LLaMA lineage.", 0xFF8B5CF6),
        StepCard(4, "Project Back", "4d → d, so the residual stream keeps its width.", 0xFF06B6D4),
        StepCard(5, "Add the Residual", "Output is added to the input, not replacing it — the stream is never overwritten.", 0xFF14B8A6),
        StepCard(6, "Count the Parameters", "Two thirds of the block. Optimise here before touching attention.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("The block", "FFN(x) = W₂ · GELU(W₁x + b₁) + b₂", "Applied identically at every position."),
        FormulaEntry("Attention parameters", "4d² = 2,359,296 at d = 768", "Q, K, V and the output projection."),
        FormulaEntry("FFN parameters", "2 · 4d² = 4,718,592", "Twice attention's — 66.7% of the block."),
        FormulaEntry("FLOPs per token", "9,437,184 (FFN) vs 4,718,592 (attention projections)", "Forward only, 2 per parameter."),
        FormulaEntry("SwiGLU width", "8/3 · d keeps three matrices at two matrices' cost", "d = 4096 → 10,922, which LLaMA rounds to 11,008."),
        FormulaEntry("Activation sparsity", "≈92% of hidden units near zero per token", "The premise behind the key-value-memory reading and behind MoE."),
    ),
    notationKey = listOf(
        NotationEntry("position-wise", "the same network applied to each token separately, with no mixing"),
        NotationEntry("d_model / d_ff", "the residual stream width and the FFN's hidden width; d_ff = 4·d_model by default"),
        NotationEntry("GELU", "Gaussian error linear unit — a smooth, probabilistic gate"),
        NotationEntry("SwiGLU", "a gated linear unit with a Swish gate; three matrices, 8/3·d wide"),
        NotationEntry("key-value memory", "reading FFN rows as detectors and columns as the values they write"),
        NotationEntry("residual stream", "the running representation that attention and FFN blocks add into"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The block, and where the parameters actually are",
            accentColor = 0xFF3B82F6,
            code = """
                import torch.nn as nn

                class FeedForward(nn.Module):
                    def __init__(self, d_model=768, expansion=4):
                        super().__init__()
                        self.up   = nn.Linear(d_model, expansion * d_model)
                        self.down = nn.Linear(expansion * d_model, d_model)
                        self.act  = nn.GELU()

                    def forward(self, x):          # x: (batch, seq, d_model)
                        return self.down(self.act(self.up(x)))   # no token mixing at all

                d = 768
                attention_params = 4 * d * d          # 2,359,296  (Q, K, V, output)
                ffn_params       = 2 * 4 * d * d      # 4,718,592
                print(ffn_params / (attention_params + ffn_params))   # 0.667

                # Two thirds of the block. If you are quantising, pruning, or splitting a model
                # across experts, this is the part that matters -- not attention.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "SwiGLU, and the 11008 that puzzles everyone",
            accentColor = 0xFFEC4899,
            code = """
                import torch, torch.nn as nn, torch.nn.functional as F

                class SwiGLU(nn.Module):
                    def __init__(self, d_model=4096, hidden=11008):   # LLaMA-7B's numbers
                        super().__init__()
                        self.gate = nn.Linear(d_model, hidden, bias=False)
                        self.up   = nn.Linear(d_model, hidden, bias=False)
                        self.down = nn.Linear(hidden, d_model, bias=False)

                    def forward(self, x):
                        return self.down(F.silu(self.gate(x)) * self.up(x))

                # Why 11008 and not 16384? Three matrices instead of two, so the width is cut to
                # 8/3 * d to keep the parameter budget fixed:
                print(int(4096 * 8 / 3))     # 10922 -> rounded up to a multiple of 256 = 11008
                print(3 * 4096 * 11008, 2 * 4 * 4096 * 4096)   # 135M vs 134M -- within 1%

                # And the sparsity that motivates MoE: most hidden units are ~0 for a given token,
                # so a router that picks a couple of expert FFNs per token loses little.
                h = F.silu(torch.randn(4096) @ torch.randn(4096, 11008))
                print((h.abs() < 0.05).float().mean().item())     # a large fraction
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Model Compression", "Quantisation and pruning target the FFN first because that is where the weights are."),
        ApplicationCard("network", 0xFF6366F1, "Mixture of Experts", "MoE replicates exactly this block per expert and routes tokens between them."),
        ApplicationCard("search", 0xFF8B5CF6, "Interpretability", "Knowledge-editing work (ROME, MEMIT) locates and edits facts in FFN weights."),
        ApplicationCard("flask", 0xFF14B8A6, "Architecture Search", "The expansion factor and gate variant are the most-tuned knobs in the block."),
    ),
    takeaways = listOf(
        "The FFN is the only position-wise part of a block — attention mixes tokens, this transforms each one.",
        "It holds two thirds of a block's parameters (4,718,592 vs attention's 2,359,296 at d = 768) and twice the per-token FLOPs.",
        "GELU replaced ReLU for smoothness; SwiGLU's three matrices force the 8/3·d width behind LLaMA's 11008.",
        "Activations are ~92% near-zero per token, which is what makes the key-value-memory reading plausible.",
        "That sparsity is also MoE's premise: if only a few units fire, only a few experts need to run.",
    ),
    crossLinks = listOf(
        CrossLink("transformers", "Transformers"),
        CrossLink("positional_encodings", "Positional Encodings"),
        CrossLink("mistral_mixtral", "Mistral & Mixtral (MoE)"),
        CrossLink("activation_functions", "Activation Functions"),
    ),
)
