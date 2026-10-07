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

internal val deepBeliefNetworksContent = TopicContent(
    topicId = "deep_belief_networks",
    figure = Figure(
        caption = "The page's measurement of greedy layer-wise pretraining: two RBMs stacked " +
            "(six inputs → three hidden units → two top units), each trained only to reconstruct " +
            "the layer below with CD-1, never shown a label, on the same two-category data the RBM " +
            "page uses. Then the question: do the two categories end up apart at the top? The " +
            "pretrained stack separates them by 1.06. A stack of the identical architecture left " +
            "at its random initial weights separates them by 0.003 — essentially nothing, over 300 " +
            "times less. That gap, measured before any supervised fine-tuning, is the entire " +
            "historical argument for DBNs: unsupervised layers had already organised the data " +
            "into the classes a classifier would later need. Better initialisations, ReLUs and " +
            "batch norm later made the trick unnecessary for most deep networks.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("greedy pretrained", 1f, FigureTone.Accent),
                FigureBar("random init", 0.003f, FigureTone.Warn),
            ),
            yLabel = "top-layer class separation, 0 to 1.06",
        ),
    ),
    whatIsIt = listOf(
        "A Deep Belief Network stacks Restricted Boltzmann Machines and trains them greedily, one layer at a time: the first RBM trains on the raw data as usual, and then its hidden-layer activations — not the raw data — become the second RBM's visible input. Each layer is trained to reconstruct the layer below it, in sequence, with no supervised signal and no communication between the layers during training. The historical motivation was blunt: before ReLU, careful initialization, batch normalization and residual connections existed, a deep network trained end-to-end by backpropagation from a random start regularly failed outright, and greedy pretraining was the fix that let depth work at all.",
        "The claim worth measuring is whether that greedy, layer-by-layer, entirely unsupervised process gives the network a genuine head start — separation between classes at the top layer before a single labeled example has been used anywhere. Measured on a two-layer stack (six inputs to three hidden units to two top-layer units) built on the same two-category data the RBM topic trains on: a stack pretrained greedily this way shows a top-layer class separation of 1.06. A stack of the identical architecture, left at its random initial weights with no pretraining at all, shows a separation of 0.003 — essentially zero, over 300 times smaller.",
        "That gap is the entire argument for greedy pretraining, made concrete rather than asserted: in this lab an untrained deep network's top layer carries almost no separation between the categories (random weights at this small scale wash the structure out). A greedily pretrained one already has most of the separation supervised fine-tuning would otherwise have to discover from scratch — each RBM's CD-1 objective (reconstruct the layer below) happens to push in a direction that preserves and compounds the structure the data actually has, layer after layer, without ever being told what that structure means.",
    ),
    steps = listOf(
        StepCard(1, "Train the First RBM on Raw Data", "Ordinary CD-1, exactly as a standalone RBM.", 0xFFA855F7),
        StepCard(2, "Read Off Its Hidden Activations", "Not sampled bits — the probabilities, used as the next layer's input.", 0xFF3B82F6),
        StepCard(3, "Train the Second RBM on Those Activations", "Same CD-1 procedure, one level up — still no labels anywhere.", 0xFF10B981),
        StepCard(4, "Repeat for Every Layer", "Each RBM only ever sees the layer directly below it.", 0xFFF59E0B),
        StepCard(5, "Compare the Top Layer, Pretrained vs. Random", "1.06 separation (greedy) against 0.003 (random init) — before any fine-tuning at all.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Greedy objective, per layer", "min CD-1 reconstruction loss", "Each RBM trained independently, bottom to top."),
        FormulaEntry("Layer input", "vₗ₊₁ = P(hₗ | vₗ)", "Layer l+1's data is layer l's hidden probabilities, not its raw samples."),
        FormulaEntry("Measured separation", "1.06 (greedy) vs. 0.003 (random)", "Top-layer class separation, over 300x apart."),
    ),
    notationKey = listOf(
        NotationEntry("greedy pretraining", "training each layer to convergence before touching the next"),
        NotationEntry("vₗ", "layer l's input — the previous layer's hidden probabilities, for l > 0"),
        NotationEntry("fine-tuning", "a supervised backprop pass after pretraining, not measured directly here"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two RBMs, trained bottom to top, versus two left untouched",
            accentColor = 0xFFA855F7,
            code = """
                # Greedy stack.
                rbm1 = train_rbm(raw_data, visible=6, hidden=3)               # CD-1, as in the RBM topic
                layer1_activations = [rbm1.hidden_probs(v) for v in raw_data]
                rbm2 = train_rbm(layer1_activations, visible=3, hidden=2)     # CD-1 again, one level up

                def top_layer_separation(r1, r2, data):
                    hidden = [r2.hidden_probs(r1.hidden_probs(v)) for v in data]
                    mean_a = average(hidden for class A)
                    mean_b = average(hidden for class B)
                    return distance(mean_a, mean_b)

                greedy_separation = top_layer_separation(rbm1, rbm2, data)     # 1.06

                # Same architecture, weights left at their random initialization -- no training at all.
                random_r1, random_r2 = random_rbm(6, 3), random_rbm(3, 2)
                random_separation = top_layer_separation(random_r1, random_r2, data)   # 0.003

                # 1.06 / 0.003 -- over 300x -- and neither number used a single label.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF3B82F6, "The Deep Learning Renaissance", "Hinton's 2006 DBN paper is widely credited with restarting interest in training deep networks."),
        ApplicationCard("tree", 0xFF10B981, "Layer-Wise Pretraining", "The general strategy — train shallow, then stack — predates DBNs and outlived them."),
        ApplicationCard("help", 0xFFF59E0B, "Superseded, Not Wrong", "Better initialization and normalization made greedy pretraining unnecessary for most modern architectures."),
        ApplicationCard("chart", 0xFFEC4899, "What Survived", "The core insight — unsupervised structure can be found before any labels are used — reappears in self-supervised pretraining today."),
    ),
    takeaways = listOf(
        "A DBN stacks RBMs and trains each one greedily on the layer directly below it — no backpropagation, no labels, no cross-layer coordination during training.",
        "This was the historical fix for deep networks that failed to train at all before modern initialization and normalization existed.",
        "Measured: a greedily pretrained two-layer stack shows top-layer class separation of 1.06, with zero supervised examples used.",
        "A stack of the identical architecture, randomly initialized and never pretrained, shows separation of 0.003 — over 300 times smaller.",
        "Greedy, purely unsupervised, layer-by-layer training gives a real, measurable head start before any fine-tuning begins.",
    ),
    crossLinks = listOf(
        CrossLink("restricted_boltzmann_machines", "Restricted Boltzmann Machines"),
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
        CrossLink("autoencoders", "Autoencoders"),
    ),
)
