package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rnnLstmContent = TopicContent(
    topicId = "rnn_lstm",
    whatIsIt = listOf(
        "In NLP, recurrent networks and their gated LSTM/GRU variants process text token by token, carrying a hidden state that accumulates sentence context.",
        "For years they were the backbone of language modeling and sequence-to-sequence tasks — until attention and Transformers displaced them.",
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
