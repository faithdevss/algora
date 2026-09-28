package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val tanhContent = TopicContent(
    topicId = "tanh",
    whatIsIt = listOf(
        "tanh is a rescaled sigmoid — tanh(z) = 2σ(2z) − 1 — and the rescaling buys exactly two things. Its range is (−1, 1) rather than (0, 1), so it is zero-centred: the simulation measures a mean output of −0.009 over standard normal input against sigmoid's 0.497. And its derivative peaks at 1 rather than 0.25, four times as much gradient per layer.",
        "Zero-centring is the reason it displaced sigmoid as the hidden-layer default in the 1990s. When every activation is positive, every weight feeding the next unit gets a gradient of the same sign, so the update can only move all-positive or all-negative and the descent path zig-zags. Centring the outputs around zero removes that constraint outright, and it is a bigger practical difference than the numbers suggest.",
        "What it does not fix is saturation — and measured, it is slightly worse there than sigmoid, because its curve turns over sooner: at a pre-activation standard deviation of 4, a larger fraction of tanh units sit on a flat tail. A derivative that peaks at 1 still spends most of its range well below 1, so a deep stack still multiplies by less than one per layer, and the simulation shows the activation standard deviation shrinking steadily over twenty layers. Tanh was a real improvement and still not enough to make very deep networks trainable; that took ReLU. It remains the default inside LSTM and GRU cells, where a bounded zero-centred value is load-bearing rather than incidental — an unbounded cell update would drift without limit across a long sequence.",
    ),
    steps = listOf(
        StepCard(1, "Rescale the Sigmoid", "tanh(z) = 2σ(2z) − 1. Same shape, different range and slope.", 0xFFF59E0B),
        StepCard(2, "Gain Zero-Centring", "Mean output near 0, so downstream gradients no longer share a sign.", 0xFFFBBF24),
        StepCard(3, "Gain Four Times the Slope", "Derivative peaks at 1 rather than 0.25.", 0xFFEC4899),
        StepCard(4, "Keep the Saturation", "Both tails are still flat, and they turn over sooner than sigmoid's.", 0xFF8B5CF6),
        StepCard(5, "Watch Depth Anyway", "Still below 1 across most of its range, so deep stacks still lose signal.", 0xFF6366F1),
        StepCard(6, "Keep It for Gated Cells", "Bounded and centred is what a carried state wants.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "tanh(z) = (eᶻ − e⁻ᶻ)/(eᶻ + e⁻ᶻ)", "Range (−1, 1), odd symmetric."),
        FormulaEntry("From sigmoid", "tanh(z) = 2σ(2z) − 1", "Same curve, stretched and shifted."),
        FormulaEntry("Derivative", "tanh′(z) = 1 − tanh²(z)", "In terms of the output again, so it is cheap."),
        FormulaEntry("Maximum slope", "tanh′(0) = 1", "Four times sigmoid's ceiling."),
        FormulaEntry("Odd symmetry", "tanh(−z) = −tanh(z)", "The formal statement of zero-centredness."),
        FormulaEntry("Xavier init", "Var(W) = 2/(nᵢₙ + nₒᵤₜ)", "Derived for saturating activations like this one."),
    ),
    notationKey = listOf(
        NotationEntry("zero-centred", "outputs distributed around 0; formally, odd symmetry"),
        NotationEntry("zig-zag", "the descent path when all gradients in a unit share a sign"),
        NotationEntry("saturation", "the flat tails, where the derivative is near zero"),
        NotationEntry("Xavier / Glorot init", "the variance-preserving initialisation for tanh"),
        NotationEntry("cell state", "an LSTM's carried value, updated through tanh"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The two things it fixes, measured",
            accentColor = 0xFFF59E0B,
            code = """
                import numpy as np

                rng = np.random.default_rng(0)
                z = rng.normal(size=200_000)

                print(np.tanh(z).mean().round(4))                     # ~0.000 -- centred
                print((1 / (1 + np.exp(-z))).mean().round(4))         # ~0.500 -- not

                print((1 - np.tanh(z) ** 2).max().round(4))           # 1.0
                s = 1 / (1 + np.exp(-z))
                print((s * (1 - s)).max().round(4))                   # 0.25

                # And the thing it does NOT fix, which is worse here than for sigmoid:
                for std in (1, 2, 4, 8):
                    w = rng.normal(scale=std, size=200_000)
                    print(std,
                          round(float(((1 - np.tanh(w) ** 2) < 0.01).mean()), 3),
                          round(float((((1 / (1 + np.exp(-w))) * (1 - 1 / (1 + np.exp(-w)))) < 0.01).mean()), 3))
                # tanh saturates sooner at every width.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why LSTMs still use it",
            accentColor = 0xFF10B981,
            code = """
                import torch
                import torch.nn as nn

                # An LSTM cell: sigmoid for the three gates (a fraction to let through), tanh for
                # the candidate and the exposed output (a bounded, signed value).
                class Cell(nn.Module):
                    def __init__(self, d):
                        super().__init__()
                        self.gates = nn.Linear(2 * d, 4 * d)

                    def forward(self, x, h, c):
                        f, i, o, g = self.gates(torch.cat([x, h], -1)).chunk(4, -1)
                        c = torch.sigmoid(f) * c + torch.sigmoid(i) * torch.tanh(g)
                        return torch.sigmoid(o) * torch.tanh(c), c

                # The bounding is not decoration. The cell state is carried across hundreds of
                # steps, and an unbounded update -- ReLU, say -- would let it drift without limit,
                # which is precisely what the gates exist to prevent.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFFF59E0B, "LSTM and GRU Cells", "Candidate and output transforms, where a bounded signed value is what a carried state needs."),
        ApplicationCard("robot", 0xFF8B5CF6, "Bounded Control Outputs", "Policy networks emitting actions in a fixed range get the bounding for free."),
        ApplicationCard("chart", 0xFF6366F1, "Shallow Networks", "Still a perfectly good default at two or three layers, where saturation has no depth to compound over."),
    ),
    takeaways = listOf(
        "A rescaled sigmoid: range (−1, 1) and derivative peaking at 1 instead of 0.25.",
        "Zero-centred, which removes sigmoid's shared-sign zig-zag — the main reason it replaced it.",
        "It saturates slightly sooner than sigmoid, so deep stacks still lose signal.",
        "Its activation standard deviation still shrinks with depth; ReLU was the real fix.",
        "Still the default inside gated recurrent cells, where bounded and centred is required.",
    ),
    crossLinks = listOf(
        CrossLink("sigmoid", "Sigmoid"),
        CrossLink("relu", "ReLU (Rectified Linear Unit)"),
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
    ),
)
