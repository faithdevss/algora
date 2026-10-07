package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lstmGruContent = TopicContent(
    topicId = "lstm_gru",
    figure = Figure(
        caption = "The page's lab: one value stored at t1 and how much of it is still held at each later " +
            "step. The LSTM's input gate opens at t1 and stores 0.855; for three steps its forget gate " +
            "stays at 0.92, so the cell keeps 0.799, 0.747, 0.699 — and the gradient back to t1 keeps " +
            "0.92³ = 0.779 of its size. A plain RNN multiplies its state by the same weight every " +
            "step; with u = 0.6 (dashed) the same value would be down to about 22% by t4, and its " +
            "gradient to 0.6³ = 0.216. At t5 the LSTM's forget gate drops to 0.05 and the cell is " +
            "cleared to 0.015: forgetting is learned and deliberate, not a decay. The GRU in the " +
            "lab's second tab does the same with an update gate — 0.855, 0.795, 0.739, 0.688, then " +
            "0.034 — using two gates instead of three.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "plain RNN, u = 0.6",
                    listOf(FigurePoint(0.000f, 0.855f), FigurePoint(0.250f, 0.513f), FigurePoint(0.500f, 0.308f), FigurePoint(0.750f, 0.185f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "LSTM cell",
                    listOf(FigurePoint(0f, 0.855f), FigurePoint(0.25f, 0.799f), FigurePoint(0.5f, 0.747f), FigurePoint(0.75f, 0.699f), FigurePoint(1f, 0.015f)),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.75f, 0.699f, "0.699 kept"),
                FigurePoint(0.75f, 0.185f, "RNN ≈ 0.18", FigureTone.Muted),
                FigurePoint(1f, 0.015f, "cleared", FigureTone.Warn),
            ),
            xLabel = "t1 → t5",
            yLabel = "stored value still held",
        ),
    ),
    whatIsIt = listOf(
        "LSTMs and GRUs are recurrent cells built so that memory and gradient can survive many steps. Their core is an additive state updated through learned gates: in an LSTM, the cell state cₜ = f·cₜ₋₁ + i·g, where the forget gate f decides how much of the old memory rides on and the input gate i how much of the new candidate g is added. A plain RNN multiplies its state by the same weight every step; a gated cell multiplies by whatever the gate chooses.",
        "The lab shows the cell holding one fact. At t1 the input gate opens (i = 0.95) and the forget gate clears the empty past (f = 0.10), storing 0.855. For the next three steps the forget gate stays at 0.92 and little new comes in: the cell holds 0.799, 0.747, then 0.699 — 82% of what was stored. A plain RNN with u = 0.6 would have kept about 22% by then, and the gradient tells the same story: ∂c₄/∂c₁ = 0.92³ = 0.779 against 0.6³ = 0.216. At t5 the forget gate drops to 0.05 and the cell is cleared to 0.015 — forgetting is as deliberate as remembering.",
        "The GRU does the same with fewer parts. It merges the LSTM's cell and output into one state and uses an update gate z in place of the forget/input pair, hₜ = (1 − z)·hₜ₋₁ + z·h̃, plus a reset gate for the candidate; in the lab's second tab it stores 0.855, keeps 0.795, 0.739, 0.688, and overwrites to 0.034 at t5 — the same behaviour with two gates instead of three. Both largely solved vanishing gradients in recurrent models, and both were then overtaken by transformers, which reach any position directly.",
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
        "In the lab a forget gate of 0.92 keeps 82% of a stored value over three steps; a plain RNN would keep about 22% (gradient 0.779 vs 0.216).",
    ),
    crossLinks = listOf(
        CrossLink("rnn", "RNNs"),
        CrossLink("transformers", "Transformers"),
    ),
)
