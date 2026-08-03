package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val attentionContent = TopicContent(
    topicId = "attention",
    figure = Figure(
        caption = "One row per output word, one column per input word, and the row sums to 1 — so every " +
            "output is an explicit mixture of the whole input rather than of one fixed summary vector. " +
            "That is the seq2seq bottleneck fixed. The outlined cell is the payoff: English \"sat\" is " +
            "the fourth French word, and the model finds it there without anything in the architecture " +
            "telling it that word order differs between the two languages.",
        shape = FigureShape.Heatmap(
            values = listOf(
                listOf(0.80f, 0.10f, 0.05f, 0.05f),
                listOf(0.10f, 0.85f, 0.03f, 0.02f),
                listOf(0.02f, 0.05f, 0.25f, 0.68f),
                listOf(0.03f, 0.05f, 0.55f, 0.37f),
            ),
            rowLabels = listOf("the", "cat", "sat", "down"),
            colLabels = listOf("le", "chat", "s'est", "assis"),
            marks = listOf(FigureCell(2, 3)),
            legend = "darker = more of that output came from this input",
        ),
    ),
    whatIsIt = listOf(
        "Attention lets a model focus on the most relevant parts of the input when producing each output, weighting every input element by how much it matters right now.",
        "It began as a fix for the seq2seq bottleneck — instead of cramming a whole sentence into one vector, the decoder looks back at all encoder states — and became the core of the Transformer.",
    ),
    steps = listOf(
        StepCard(1, "Query, Keys, Values", "The current position forms a query; every input offers a key and a value.", 0xFF818CF8),
        StepCard(2, "Score Relevance", "Compare the query to each key (dot product) to score how relevant that input is.", 0xFF60A5FA),
        StepCard(3, "Softmax Weights", "Normalize the scores into attention weights that sum to one.", 0xFF10B981),
        StepCard(4, "Weighted Sum", "Blend the values by those weights — the output focuses on what mattered most.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Attention", "softmax(QKᵀ/√dₖ)·V", "Values mixed by query-key relevance."),
        FormulaEntry("Weights", "αᵢ = softmax(scoreᵢ)", "Non-negative, sum to one."),
        FormulaEntry("Self vs cross", "same vs different sequences", "Self-attention: input attends to itself."),
    ),
    notationKey = listOf(
        NotationEntry("Q, K, V", "query, key, value vectors"),
        NotationEntry("αᵢ", "attention weight on input i"),
        NotationEntry("√dₖ", "scaling to stabilize gradients"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Scaled dot-product attention (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn.functional as F

                def attention(Q, K, V):
                    d_k = Q.size(-1)
                    scores = Q @ K.transpose(-2, -1) / d_k ** 0.5
                    return F.softmax(scores, dim=-1) @ V
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Transformers & LLMs", "Self-attention is the engine of every modern language model."),
        ApplicationCard("globe", 0xFF60A5FA, "Translation Alignment", "Attention weights show which source words align to each translated word."),
        ApplicationCard("image", 0xFF10B981, "Vision & Captioning", "Models attend to image regions when generating a caption or answer."),
    ),
    takeaways = listOf(
        "Attention computes a relevance-weighted blend of inputs for each output.",
        "It solved the seq2seq bottleneck by letting the decoder see all encoder states.",
        "Query-key-value scoring plus softmax is its universal recipe.",
        "Self-attention generalizes it into the Transformer's core operation.",
    ),
    crossLinks = listOf(
        CrossLink("transformers", "Transformers"),
        CrossLink("rnn_lstm", "RNN / LSTM"),
    ),
)
