package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lstmGruContent = TopicContent(
    topicId = "lstm_gru",
    whatIsIt = listOf(
        "LSTMs and GRUs are gated RNNs designed to remember information over long sequences, solving the vanishing-gradient problem that cripples plain RNNs.",
        "Learnable gates decide what to keep, forget, and output, letting a nearly-unchanged cell state carry information across many timesteps.",
    ),
    steps = listOf(
        StepCard(1, "Cell State Highway", "An LSTM maintains a cell state that flows through with only minor gated edits.", 0xFF818CF8),
        StepCard(2, "Forget Gate", "Decide what fraction of the old memory to discard.", 0xFF60A5FA),
        StepCard(3, "Input Gate", "Decide what new information to write into the cell state.", 0xFF10B981),
        StepCard(4, "Output Gate", "Decide what part of the cell state to expose as the hidden output.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Forget gate", "fₜ = σ(W_f·[hₜ₋₁, xₜ])", "How much memory to keep."),
        FormulaEntry("Cell update", "cₜ = fₜ⊙cₜ₋₁ + iₜ⊙c̃ₜ", "Forget old, add new — the memory highway."),
        FormulaEntry("GRU", "merges gates & states", "Fewer parameters, often comparable accuracy."),
    ),
    notationKey = listOf(
        NotationEntry("cₜ", "cell state — the long-term memory"),
        NotationEntry("gate", "sigmoid-controlled information valve"),
        NotationEntry("⊙", "element-wise multiplication"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LSTM and GRU (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn as nn

                lstm = nn.LSTM(input_size=64, hidden_size=128, batch_first=True)
                gru  = nn.GRU(input_size=64,  hidden_size=128, batch_first=True)

                out, (h_n, c_n) = lstm(x)   # GRU returns just h_n
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Language & Translation", "Powered machine translation and text generation before Transformers."),
        ApplicationCard("chart", 0xFF60A5FA, "Long Time Series", "Modeling dependencies spanning many steps in forecasting and monitoring."),
        ApplicationCard("music", 0xFF10B981, "Speech Recognition", "Gated recurrence captured long acoustic context in early end-to-end ASR."),
    ),
    takeaways = listOf(
        "Gates let LSTMs/GRUs preserve information across long sequences.",
        "The cell-state highway is what escapes vanishing gradients.",
        "GRUs simplify the LSTM with fewer gates and parameters, often matching it.",
        "Transformers now dominate, but gated RNNs remain strong for streaming, low-latency tasks.",
    ),
    crossLinks = listOf(
        CrossLink("rnn", "RNNs"),
        CrossLink("transformers", "Transformers"),
    ),
)
