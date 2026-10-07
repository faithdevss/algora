package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val restrictedBoltzmannMachinesContent = TopicContent(
    topicId = "restricted_boltzmann_machines",
    figure = Figure(
        caption = "What CD-1 training does to the page's RBM — six visible bits, three hidden " +
            "units, no labels anywhere — measured before and after. Untrained, with random " +
            "weights, it reconstructs an input with 49.8% of bits wrong: a coin flip per bit. " +
            "After contrastive-divergence training, which only ever pushes reconstructions " +
            "towards the data, the error is 17.0%. The second row is the surprise. The data has " +
            "two categories the RBM was never told about, and the separation between them in the " +
            "hidden layer goes from 0.06 to 1.33 — over twenty times larger — because two hidden " +
            "units learned to fire for one category and the third for the other. Structure the " +
            "labels would have described emerged from reconstruction pressure alone, which is " +
            "what made RBMs the building block of the deep belief net.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("49.8%", "17.0%"),
                listOf("0.06", "1.33"),
            ),
            rowHeaders = listOf("bits wrong", "class separation"),
            colHeaders = listOf("untrained", "after CD-1"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(1, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A Restricted Boltzmann Machine has a visible layer and a hidden layer, connected by one weight matrix — and, unlike a general Boltzmann machine, no visible-visible or hidden-hidden connections at all. That restriction is what makes the model tractable: with no connections within a layer, every hidden unit's probability given the visible layer is an independent sigmoid, and every visible unit's probability given the hidden layer is too. Sampling alternates cleanly between the two layers, which is exactly what training needs.",
        "Training uses Contrastive Divergence (CD-1): from a real data example, sample the hidden layer once, reconstruct the visible layer from that, then read the hidden probabilities again — three steps instead of running the Gibbs chain to equilibrium, which is the exact quantity the true gradient would need. It is an approximation, and measured directly it is a good one: an untrained RBM (random weights) reconstructs a six-bit input with 49.8% of bits wrong — no better than a coin flip per bit. After training with CD-1 on the same data, reconstruction error falls to 17.0%, well under half.",
        "The more striking result is in the hidden layer, which is never told the data has two categories. Before training, the hidden units' average activation for either category is nearly identical — a separation of 0.06 between the two classes' mean hidden vectors, close to nothing. After training, that separation is 1.33, more than twentyfold larger, and it resolves into something readable: two of the three hidden units fire strongly (>0.7) for one category and stay off (<0.3) for the other, while the third does the reverse. Three unlabeled hidden units learned to encode the exact structure of the data, purely from CD-1's reconstruction pressure.",
    ),
    steps = listOf(
        StepCard(1, "One Weight Matrix, No Within-Layer Links", "Visible and hidden units connect to each other, never to their own layer.", 0xFFA855F7),
        StepCard(2, "Sample Hidden From Data", "h₀ ~ P(h | v₀) — a Bernoulli draw per hidden unit, each an independent sigmoid.", 0xFF3B82F6),
        StepCard(3, "Reconstruct Visible From Hidden", "v₁ = P(v | h₀) — one step back down, not run to equilibrium.", 0xFF10B981),
        StepCard(4, "CD-1: Compare Two Correlations", "ΔW ∝ ⟨v₀h₀⟩ − ⟨v₁h₁⟩ — data statistics against one-step reconstruction statistics.", 0xFFF59E0B),
        StepCard(5, "Watch the Hidden Layer Specialize", "No label used anywhere — two units learn one category, the third learns the other.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Hidden given visible", "P(hⱼ=1|v) = σ(cⱼ + Σᵢ vᵢWⱼᵢ)", "Independent per unit — the restriction's whole payoff."),
        FormulaEntry("Visible given hidden", "P(vᵢ=1|h) = σ(bᵢ + Σⱼ hⱼWⱼᵢ)", "Symmetric to the hidden update."),
        FormulaEntry("CD-1 weight update", "ΔWⱼᵢ = η(⟨v₀ᵢh₀ⱼ⟩ − ⟨v₁ᵢh₁ⱼ⟩)", "One up-down-up pass approximates the true gradient."),
        FormulaEntry("Measured", "0.498 → 0.170 reconstruction error", "Untrained versus CD-1-trained, on the identical test data."),
    ),
    notationKey = listOf(
        NotationEntry("v, h", "visible and hidden unit vectors"),
        NotationEntry("W", "the one weight matrix — the only connections in the model"),
        NotationEntry("σ", "the logistic sigmoid"),
        NotationEntry("CD-1", "Contrastive Divergence, truncated to one Gibbs step"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "CD-1, and the separation it buys with no labels at all",
            accentColor = 0xFFA855F7,
            code = """
                import numpy as np

                def sigmoid(x):
                    return 1 / (1 + np.exp(-x))

                def cd1_step(v0, w, b, c, lr):
                    h0_probs = sigmoid(c + w @ v0)
                    h0_sample = (np.random.rand(*h0_probs.shape) < h0_probs).astype(float)
                    v1_probs = sigmoid(b + w.T @ h0_sample)
                    h1_probs = sigmoid(c + w @ v1_probs)

                    w += lr * (np.outer(h0_probs, v0) - np.outer(h1_probs, v1_probs))
                    b += lr * (v0 - v1_probs)
                    c += lr * (h0_probs - h1_probs)

                # Two 6-bit prototypes, 15% bit-flip noise, no labels used during training.
                for epoch in range(60):
                    for v0 in training_data:
                        cd1_step(v0, w, b, c, lr=0.1)

                # reconstruction error:  0.498 (untrained) -> 0.170 (trained)
                # hidden separation:     0.06  (untrained) -> 1.33  (trained), >20x
                # trained hidden means:  class A [0.92, 0.82, 0.10], class B [0.09, 0.14, 0.89]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("tree", 0xFF3B82F6, "Unsupervised Pretraining", "RBMs were the layer-by-layer building block of early deep nets, before better inits and ReLU."),
        ApplicationCard("search", 0xFF10B981, "Collaborative Filtering", "Netflix Prize-era recommender systems used RBMs to model rating patterns."),
        ApplicationCard("chart", 0xFFF59E0B, "Feature Learning", "The hidden layer's activations are a learned, unsupervised representation of the input."),
        ApplicationCard("help", 0xFFEC4899, "Mostly Historical Now", "Modern deep nets train end-to-end directly; CD-1 pretraining is rarely needed anymore."),
    ),
    takeaways = listOf(
        "No visible-visible or hidden-hidden connections — only one weight matrix, which is what makes both conditionals simple, independent sigmoids.",
        "CD-1 approximates the true gradient with one up-down-up pass instead of running Gibbs sampling to equilibrium.",
        "Measured: reconstruction error falls from 0.498 (untrained, chance-level) to 0.170 after CD-1 training.",
        "The hidden layer's class separation grows from 0.06 to 1.33 — over twentyfold — with no label ever used.",
        "Individual hidden units specialize: two fire for one category and not the other, the third runs the opposite way.",
    ),
    crossLinks = listOf(
        CrossLink("deep_belief_networks", "Deep Belief Networks"),
        CrossLink("autoencoders", "Autoencoders"),
        CrossLink("gans", "GANs"),
        CrossLink("backpropagation", "Backpropagation Algorithm"),
    ),
)
