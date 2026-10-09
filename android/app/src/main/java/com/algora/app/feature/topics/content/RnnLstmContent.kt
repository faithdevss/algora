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

internal val rnnLstmContent = TopicContent(
    topicId = "rnn_lstm",
    figure = Figure(
        caption = "How much the final state h5 depends on each earlier state, |∂h5/∂hₜ| averaged over " +
            "the four units, after the lab's RNN, h = tanh(x + 0.6·h), reads \"the movie was not good\". " +
            "Each step back multiplies the path by 0.6 · (1 − h²), which is at most 0.6. The state " +
            "after \"not\" still reaches h5 at 0.514, but \"was\" is down to 0.241, \"movie\" to 0.131 " +
            "and \"the\" to 0.069. A gradient that should teach the model about early words mostly " +
            "never arrives. An LSTM's cell state is updated by addition and scaled only by its forget " +
            "gate: at 0.95 it keeps 0.36 of a signal after 20 steps, where 0.6 per step keeps almost " +
            "none.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("the", 0.069f, FigureTone.Warn),
                FigureBar("movie", 0.131f),
                FigureBar("was", 0.241f),
                FigureBar("not", 0.514f),
                FigureBar("good", 1f, FigureTone.Accent),
            ),
            xLabel = "h1 → h5",
            yLabel = "|∂h5/∂hₜ|, 0 to 1",
        ),
    ),
    whatIsIt = listOf(
        "In NLP, recurrent networks and their gated LSTM/GRU variants process text token by token, carrying a hidden state that accumulates sentence context.",
        "For years they were the backbone of language modeling and sequence-to-sequence tasks — until attention and Transformers displaced them.",
        "The lab reads \"the movie was not good\" with four units and h = tanh(x + 0.6·h). After \"not\" the negation unit sits at −0.72; \"good\" pushes it up by 0.5 and it lands at 0.07, so the state holds the interaction, and reading the words in reverse leaves a different final state. The same 0.6 is the problem: the gradient reaching h5 from earlier states falls to 0.514, 0.241, 0.131 and finally 0.069 for \"the\". An LSTM's cell is scaled only by its forget gate, and at 0.95 it still keeps 0.36 of a signal after 20 steps.",
    ),
    steps = listOf(
        StepCard(1, "Embed Tokens", "Turn each token into a vector via an embedding layer.", 0xFF818CF8),
        StepCard(2, "Recur Over the Sequence", "Feed tokens one at a time; the hidden state carries context forward.", 0xFF60A5FA),
        StepCard(3, "Gate Long-Range Memory", "LSTM/GRU gates preserve information across long sentences, escaping vanishing gradients.", 0xFF10B981),
        StepCard(4, "Encode–Decode", "Seq2seq stacks an encoder RNN and a decoder RNN for translation and summarization.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Hidden update", "hₜ = f(xₜ, hₜ₋₁)", "Context flows through the sequence."),
        FormulaEntry("Bottleneck", "fixed-size context vector", "Seq2seq compresses a whole input into one state."),
        FormulaEntry("Weakness", "sequential, not parallel", "Can't parallelize across timesteps like a Transformer."),
    ),
    notationKey = listOf(
        NotationEntry("hₜ", "hidden state after token t"),
        NotationEntry("seq2seq", "encoder–decoder for sequence output"),
        NotationEntry("context vector", "encoder's summary handed to the decoder"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Embedding + LSTM for text (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn as nn

                embed = nn.Embedding(vocab_size, 128)
                lstm  = nn.LSTM(128, 256, batch_first=True)

                out, (h_n, c_n) = lstm(embed(token_ids))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Language Modeling", "Predicting the next word powered early text generation and autocomplete."),
        ApplicationCard("globe", 0xFF60A5FA, "Machine Translation", "Encoder–decoder LSTMs were the pre-Transformer translation standard."),
        ApplicationCard("music", 0xFF10B981, "Speech & Sequence Tagging", "NER, POS tagging, and speech recognition used recurrent sequence models."),
    ),
    takeaways = listOf(
        "Recurrent nets process text sequentially, carrying context in a hidden state.",
        "LSTM/GRU gates handle the long-range dependencies plain RNNs lose.",
        "Seq2seq encoder–decoders powered translation before attention.",
        "Their sequential nature blocks parallelism — the gap Transformers filled.",
    ),
    crossLinks = listOf(
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("attention", "Attention"),
    ),
)
