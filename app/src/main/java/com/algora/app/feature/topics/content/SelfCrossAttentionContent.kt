package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val selfCrossAttentionContent = TopicContent(
    topicId = "self_cross_attention",
    figure = Figure(
        caption = "The matrix is not square, and that is the entire difference. Self-attention draws Q, " +
            "K and V from one sequence, so its matrix is n×n and every position refines itself using " +
            "the rest. Cross-attention takes Q from what is being generated and K, V from somewhere " +
            "else, so the shape follows the two lengths — the decoder is reading the encoder. Same " +
            "softmax(QKᵀ/√d)·V either way. Replacing one fixed handover vector with this took the copy " +
            "task from 0.292 to 0.867, and matching the parameter counts did not close the gap.",
        shape = FigureShape.Heatmap(
            values = listOf(
                listOf(0.72f, 0.14f, 0.05f, 0.04f, 0.03f, 0.02f),
                listOf(0.12f, 0.70f, 0.10f, 0.04f, 0.02f, 0.02f),
                listOf(0.05f, 0.12f, 0.68f, 0.10f, 0.03f, 0.02f),
                listOf(0.03f, 0.05f, 0.12f, 0.65f, 0.10f, 0.05f),
            ),
            rowLabels = listOf("y₁", "y₂", "y₃", "y₄"),
            colLabels = listOf("x₁", "x₂", "x₃", "x₄", "x₅", "x₆"),
            legend = "rows: decoder queries · columns: encoder keys — every score exists, which is the quadratic cost",
        ),
    ),
    whatIsIt = listOf(
        "Self-attention and cross-attention are the same operation. Score every query against every key, softmax the scores, return that weighted mixture of the values — softmax(QKᵀ/√d)·V, identically in both cases. What differs is only where the three come from. In self-attention Q, K and V are all projections of one sequence, so every position is refining its own representation using the rest of the sequence. In cross-attention the queries come from the sequence being generated and the keys and values from a different one, so the decoder is reading the encoder.",
        "That distinction is what turns an encoder-decoder from a bottleneck into an architecture. The encoder-decoder topic measures a fixed context vector failing: on a copy task it scores 0.292 exact match, collapsing to zero by length four, and a linear probe shows the vector holds only what the encoder read last. This lab retrains the same model, on the same 120 pairs, for the same 100 epochs, with cross-attention in place of that single handover — and it scores 0.867, with the per-length curve flat until the very end. The decoder no longer has to receive the source; it can go and look.",
        "The obvious objection is parameters, and it is answered by measurement rather than argument. Widening the fixed-vector model until its parameter count matches the attention model's — 1,069 against 1,087 — moves it from 0.292 to 0.300, and the collapse with length is exactly where it was. Capacity was never the missing ingredient; the missing ingredient was a path from each decoder step to every source position. What that path costs is quadratic: self-attention over n tokens computes n² scores, and the whole matrix has to exist.",
    ),
    steps = listOf(
        StepCard(1, "Project", "Q, K, V from whichever sequences the wiring says.", 0xFF6366F1),
        StepCard(2, "Score", "q·k / √d for every pair — n² of them, or target × source.", 0xFF3B82F6),
        StepCard(3, "Softmax", "Turn scores into weights that sum to one, per query.", 0xFF10B981),
        StepCard(4, "Mix", "Return Σ αᵢ vᵢ — a read, addressed by content.", 0xFF8B5CF6),
        StepCard(5, "Wire It Two Ways", "Same sequence, or decoder queries against encoder keys.", 0xFFF59E0B),
        StepCard(6, "Check the Claim", "0.292 → 0.867 on the same task, at matched parameters.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("The operation", "Attention(Q,K,V) = softmax(QKᵀ/√d)·V", "Identical for self and cross."),
        FormulaEntry("Self-attention", "Q = XWq, K = XWk, V = XWv", "One sequence X, three projections."),
        FormulaEntry("Cross-attention", "Q = YWq, K = XWk, V = XWv", "Y is the target, X the source; lengths may differ."),
        FormulaEntry("Why √d", "scores scale as √d", "Without it the softmax saturates and gradients vanish."),
        FormulaEntry("Measured, fixed vector", "0.292 exact match", "571 parameters; 0.300 when widened to 1,069."),
        FormulaEntry("Measured, cross-attention", "0.867 exact match", "1,087 parameters, same data, same budget."),
    ),
    notationKey = listOf(
        NotationEntry("Q, K, V", "queries, keys and values — three linear projections"),
        NotationEntry("d", "the dimension keys and queries are scored in; √d is the scaling factor"),
        NotationEntry("α", "the softmaxed weights — one distribution per query"),
        NotationEntry("self", "Q, K and V from the same sequence"),
        NotationEntry("cross", "Q from the target sequence, K and V from the source"),
        NotationEntry("alignment", "which source position each output step attends to"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "One function, both wirings",
            accentColor = 0xFF6366F1,
            code = """
                import torch
                import torch.nn.functional as F

                def attention(query, key, value, mask=None):
                    d = query.size(-1)
                    scores = query @ key.transpose(-2, -1) / d ** 0.5
                    if mask is not None:
                        scores = scores.masked_fill(mask == 0, float("-inf"))
                    return F.softmax(scores, dim=-1) @ value

                # Self-attention: one sequence, three projections of it.
                attention(Wq(x), Wk(x), Wv(x))

                # Cross-attention: the decoder asks, the encoder answers. Note the shapes --
                # nothing requires the two sequences to be the same length, which is the whole
                # reason this works for translation.
                attention(Wq(y), Wk(x), Wv(x))

                # Causal self-attention: the same call with a triangular mask.
                causal = torch.tril(torch.ones(n, n))
                attention(Wq(x), Wk(x), Wv(x), mask=causal)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Read the alignment out — it is a free diagnostic",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch

                @torch.no_grad()
                def cross_attention_weights(model, source, target):
                    "Return the target x source matrix the decoder actually used."
                    memory = model.encode(source)
                    weights = []
                    hooks = [
                        layer.cross_attn.register_forward_hook(
                            lambda _m, _i, out: weights.append(out[1])   # (batch, heads, tgt, src)
                        )
                        for layer in model.decoder.layers
                    ]
                    model.decode(target, memory)
                    for h in hooks:
                        h.remove()
                    return torch.stack(weights).mean(dim=(0, 1))          # over layers and heads

                # A diagonal-ish matrix means the model found the alignment. A matrix with all its
                # mass in one column usually means it is ignoring the source and running as a
                # language model -- which shows up as fluent output that is not a translation.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF14B8A6, "Translation", "Cross-attention is where the source is read, one output step at a time."),
        ApplicationCard("chip", 0xFF6366F1, "Encoder Stacks", "Self-attention is the whole of BERT's contextualisation."),
        ApplicationCard("flask", 0xFF8B5CF6, "Debugging", "The alignment matrix says whether the source is being used at all."),
        ApplicationCard("finance", 0xFFEC4899, "Cost", "n² scores per layer — the reason long contexts are expensive."),
    ),
    takeaways = listOf(
        "Self- and cross-attention are one operation, softmax(QKᵀ/√d)·V, differing only in where Q, K and V come from.",
        "Cross-attention lets the two sequences have different lengths, which is what an encoder-decoder needs.",
        "On the same copy task, same data and same budget: a fixed context vector scores 0.292, cross-attention 0.867.",
        "Widening the fixed-vector model to a matched 1,069 parameters moves it to 0.300 — the gap is not capacity.",
        "The learned alignment puts 0.942 of its mass on the position each step should read, without ever being told.",
        "The cost is quadratic: n tokens means n² scores, and the whole matrix has to be materialised.",
        "The attention gradients here are checked against finite differences — the first check failed on a loss-scale mismatch, not a derivation error.",
    ),
    crossLinks = listOf(
        CrossLink("attention", "Attention"),
        CrossLink("encoder_decoder", "Encoder-Decoder Architecture"),
        CrossLink("multi_head_attention", "Multi-Head Attention"),
        CrossLink("transformers", "Transformers"),
        CrossLink("seq2seq", "Seq2Seq Models"),
    ),
)
