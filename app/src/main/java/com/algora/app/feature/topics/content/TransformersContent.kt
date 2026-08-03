package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureLayer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val transformersContent = TopicContent(
    topicId = "transformers",
    figure = Figure(
        caption = "One block, repeated N times. Attention is the only place tokens talk to each other — " +
            "the feed-forward runs on each position independently — so the whole sequence goes through " +
            "in parallel instead of one step at a time. That parallelism is the reason these scaled and " +
            "RNNs did not, and it is also why position has to be added as an input: nothing in the " +
            "block itself knows what order the tokens came in.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("token + positional embeddings", "order is data here, not structure"),
                FigureLayer("multi-head self-attention", "every token sees every token", FigureTone.Primary),
                FigureLayer("add & norm", "the residual keeps the gradient short"),
                FigureLayer("feed-forward", "each position on its own", FigureTone.Primary),
                FigureLayer("add & norm"),
                FigureLayer("× N blocks", "identical, stacked", FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The Transformer is a sequence architecture built entirely on self-attention, letting every token directly attend to every other token — no recurrence, no convolution.",
        "Because attention processes a whole sequence in parallel, Transformers train far faster than RNNs and scale to the enormous models behind modern LLMs.",
    ),
    steps = listOf(
        StepCard(1, "Embed + Position", "Turn tokens into vectors and add positional encodings, since attention itself is order-agnostic.", 0xFF818CF8),
        StepCard(2, "Self-Attention", "Each token computes queries, keys, and values to weigh how much every other token matters.", 0xFF60A5FA),
        StepCard(3, "Multi-Head", "Several attention heads capture different relationships in parallel.", 0xFF10B981),
        StepCard(4, "Feed-Forward + Residual", "Position-wise MLPs, residual connections, and layer norm refine and stack the representation.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Attention", "softmax(QKᵀ/√dₖ)·V", "Weighted mix of values by query-key similarity."),
        FormulaEntry("Multi-head", "concat(head₁…headₕ)·W_O", "Parallel attention subspaces."),
        FormulaEntry("Complexity", "O(n²·d)", "Quadratic in sequence length n."),
    ),
    notationKey = listOf(
        NotationEntry("Q, K, V", "query, key, value projections"),
        NotationEntry("self-attention", "tokens attending to the same sequence"),
        NotationEntry("positional encoding", "injects token order into embeddings"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Scaled dot-product attention (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch, torch.nn.functional as F

                def attention(Q, K, V):
                    d_k = Q.size(-1)
                    scores = Q @ K.transpose(-2, -1) / d_k ** 0.5
                    weights = F.softmax(scores, dim=-1)
                    return weights @ V
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Large Language Models", "GPT, BERT, and every modern LLM are Transformer stacks."),
        ApplicationCard("image", 0xFF60A5FA, "Vision & Multimodal", "Vision Transformers and CLIP apply attention to images and image-text pairs."),
        ApplicationCard("flask", 0xFF10B981, "Science", "AlphaFold and protein/genomics models use attention over biological sequences."),
    ),
    takeaways = listOf(
        "Transformers replace recurrence with self-attention, processing sequences in parallel.",
        "Attention lets any token directly influence any other, capturing long-range dependencies.",
        "Multi-head attention and positional encodings are the architecture's signature pieces.",
        "Attention is O(n²) in sequence length — the main bottleneck efficiency research targets.",
    ),
    crossLinks = listOf(
        CrossLink("attention", "Attention"),
        CrossLink("llms", "LLMs"),
    ),
)
