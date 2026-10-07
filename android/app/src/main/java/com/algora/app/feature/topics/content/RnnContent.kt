package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rnnContent = TopicContent(
    topicId = "rnn",
    figure = Figure(
        caption = "The page's lab: one recurrent cell, hₜ = tanh(0.7·xₜ + 0.6·hₜ₋₁), unrolled over " +
            "four inputs — the top row — with the hidden state it produces at each step underneath. " +
            "With no past, h₁ = tanh(0.56) = 0.508. At step 2 the negative input nearly cancels the " +
            "carried state — 0.6 × 0.508 pulls up by 0.305 while 0.7 × −0.40 pulls down by 0.280 — " +
            "leaving 0.025. Then 0.409 and 0.368: the last state summarises all four inputs in one " +
            "number, and it is all a classifier reading the end of the sequence ever sees. The same " +
            "two weights serve every step, so the sequence could be any length; the price is that " +
            "an early input's influence is multiplied by 0.6 (and a tanh slope) at every step on the " +
            "way back, which is how long-range gradients vanish.",
        shape = FigureShape.Strip(
            cells = listOf("x 0.80", "x −0.40", "x 0.60", "x 0.20"),
            pointers = listOf(
                FigurePointer(1, "cancels"),
                FigurePointer(3, "summary"),
            ),
            aux = listOf("0.508", "0.025", "0.409", "0.368"),
            auxLabel = "h",
        ),
    ),
    whatIsIt = listOf(
        "A recurrent neural network reads a sequence one step at a time and carries a hidden state forward: hₜ = tanh(w·xₜ + u·hₜ₋₁). The same weights are used at every step, so a sequence of any length needs the same small set of parameters, and the state at each step is a running summary of everything read so far.",
        "The lab unrolls one cell with w = 0.7 and u = 0.6 over four inputs, 0.80, −0.40, 0.60 and 0.20. With no past, h₁ = tanh(0.56) = 0.508. At step 2 the negative input nearly cancels the past — u·h₁ = 0.305 pulls up while w·x₂ = −0.280 pulls down — and h₂ = 0.025. Then h₃ = 0.409 and h₄ = 0.368, a single number that summarises all four inputs, which is all a classifier reading the final state ever sees.",
        "Unrolled, the RNN is a four-layer network with tied weights, trained by backpropagating through the copies; the gradient for w adds up over every step. That is also its weakness: the signal from an early input is multiplied by u (and a tanh slope) once per step on its way back, so with u = 0.6 only 0.6³ ≈ 22% of it survives three steps. Long-range dependencies vanish, which is the problem LSTMs and GRUs were built to fix and transformers sidestepped.",
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
        "In the lab one cell with w = 0.7, u = 0.6 turns inputs 0.80, −0.40, 0.60, 0.20 into states 0.508, 0.025, 0.409, 0.368 — the last one summarising all four.",
    ),
    crossLinks = listOf(
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("attention", "Attention"),
    ),
)
