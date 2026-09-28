package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rnnContent = TopicContent(
    topicId = "rnn",
    whatIsIt = listOf(
        "Recurrent neural networks (RNNs) process sequences one step at a time, carrying a hidden state that summarizes everything seen so far.",
        "That loop gives them memory — the same weights are reused at every timestep — making them a natural fit for text, speech, and time series.",
    ),
    steps = listOf(
        StepCard(1, "Hidden State", "A vector h carries context from previous steps into the current one.", 0xFF818CF8),
        StepCard(2, "Recurrent Update", "At each step, combine the new input with the previous hidden state to produce the next.", 0xFF60A5FA),
        StepCard(3, "Shared Weights", "The same parameters apply at every timestep, so the network handles any sequence length.", 0xFF10B981),
        StepCard(4, "Backprop Through Time", "Unroll the loop and backpropagate across all steps to train.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "hₜ = tanh(Wₓxₜ + Wₕhₜ₋₁ + b)", "Next state from input and previous state."),
        FormulaEntry("Output", "yₜ = Wᵧhₜ", "Prediction from the current hidden state."),
        FormulaEntry("Weakness", "vanishing gradients", "Long-range dependencies fade over many steps."),
    ),
    notationKey = listOf(
        NotationEntry("hₜ", "hidden state at timestep t"),
        NotationEntry("BPTT", "backpropagation through time"),
        NotationEntry("timestep", "one position in the sequence"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "An RNN layer (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn as nn

                rnn = nn.RNN(input_size=64, hidden_size=128, batch_first=True)
                output, h_n = rnn(x)   # output: per-step states; h_n: final state
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Sequence Modeling", "Language modeling, handwriting, and speech before Transformers took over."),
        ApplicationCard("chart", 0xFF60A5FA, "Time Series", "Forecasting sensor, financial, and demand data where order matters."),
        ApplicationCard("music", 0xFF10B981, "Audio Generation", "Step-by-step generation of music and speech waveforms."),
    ),
    takeaways = listOf(
        "RNNs carry a hidden state through a sequence, reusing weights at every step.",
        "They're trained by backpropagation through time over the unrolled loop.",
        "Vanishing gradients cripple their long-range memory — the motivation for LSTMs.",
        "Transformers have largely replaced them, but the recurrent idea remains foundational.",
    ),
    crossLinks = listOf(
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("attention", "Attention"),
    ),
)
