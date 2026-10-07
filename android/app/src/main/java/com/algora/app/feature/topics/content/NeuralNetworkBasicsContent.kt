package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val neuralNetworkBasicsContent = TopicContent(
    topicId = "neural_network_basics",
    figure = Figure(
        caption = "The page's lab, one forward pass through a 2-3-1 network with input x = (0.90, " +
            "0.20). Each hidden unit takes a weighted sum of both inputs and applies ReLU: unit 1 gets " +
            "0.9·0.90 − 0.45·0.20 = 0.72, unit 2 gets 0.34, and unit 3's sum is −0.35, so ReLU outputs " +
            "0 and its outgoing weight contributes nothing — this input simply does not use it. The " +
            "output adds 1.2·0.72 − 0.7·0.34 + 0.9·0 − 0.3 = 0.326 and a sigmoid turns that into " +
            "0.581: a 58% probability of class 1. Swap the inputs and a different set of hidden units " +
            "switches on. Training would compare 0.581 with the label and use backpropagation to " +
            "decide how much each weight should move.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("0.90", 0.08f, 0.28f, FigureTone.Primary),
                FigureGraphNode("0.20", 0.08f, 0.72f, FigureTone.Primary),
                FigureGraphNode("0.72", 0.50f, 0.12f, FigureTone.Accent),
                FigureGraphNode("0.34", 0.50f, 0.50f, FigureTone.Accent),
                FigureGraphNode("0", 0.50f, 0.88f, FigureTone.Warn),
                FigureGraphNode("0.581", 0.92f, 0.50f, FigureTone.Primary),
            ),
            edges = listOf(
                FigureEdge(0, 2, directed = true),
                FigureEdge(0, 3, directed = true),
                FigureEdge(0, 4, directed = true),
                FigureEdge(1, 2, directed = true),
                FigureEdge(1, 3, directed = true),
                FigureEdge(1, 4, directed = true),
                FigureEdge(2, 5, "1.2", directed = true, tone = FigureTone.Accent),
                FigureEdge(3, 5, "−0.7", directed = true, tone = FigureTone.Accent),
                FigureEdge(4, 5, "silent", directed = true, tone = FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A neural network is layers of simple units, each computing a weighted sum of its inputs and passing it through a non-linearity. Stack a hidden layer between inputs and output and the network can represent functions no single weighted sum can; train the weights by gradient descent on a loss and it learns them from examples.",
        "The lab runs one forward pass through a tiny network: two inputs, three ReLU hidden units and a sigmoid output. For x = (0.90, 0.20), hidden unit 1 computes ReLU(0.9·0.90 − 0.45·0.20) = 0.72, unit 2 gets 0.34, and unit 3's weighted sum is −0.35, so ReLU switches it off and its outgoing weight of 0.9 contributes nothing. The output sums 1.2·0.72 − 0.7·0.34 + 0.9·0 − 0.3 = 0.326, and the sigmoid turns that into 0.581 — a 58% probability of class 1. Swap the inputs and a different set of hidden units is active.",
        "Two ideas carry all the way to the largest models. The non-linearity is essential — without it, stacked layers collapse into a single linear map — and ReLU's habit of switching units off per input means each example effectively uses its own sub-network. And everything is learned the same way: compare the prediction with the label, and backpropagation computes how much each weight contributed to the error so gradient descent can adjust it.",
    ),
    steps = listOf(
        StepCard(1, "Neuron = Weighted Sum + Activation", "Each unit computes a = f(w·x + b), a linear combination passed through a non-linearity.", 0xFF818CF8),
        StepCard(2, "Layers Stack", "Outputs of one layer feed the next; hidden layers build up increasingly abstract features.", 0xFF60A5FA),
        StepCard(3, "Forward Pass", "Data flows input → hidden → output to produce a prediction.", 0xFF10B981),
        StepCard(4, "Learn the Weights", "A loss measures error; backpropagation and gradient descent adjust every weight.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Neuron", "a = f(w·x + b)", "Weighted sum through activation f."),
        FormulaEntry("Layer", "a⁽ˡ⁾ = f(W⁽ˡ⁾a⁽ˡ⁻¹⁾ + b⁽ˡ⁾)", "Matrix form of a full layer."),
        FormulaEntry("Why non-linearity", "stacked linear = linear", "Without f, depth adds no power."),
    ),
    notationKey = listOf(
        NotationEntry("W, b", "weight matrix and bias per layer"),
        NotationEntry("f", "activation function (ReLU, sigmoid, …)"),
        NotationEntry("hidden layer", "an intermediate feature-building layer"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A small MLP (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn as nn

                model = nn.Sequential(
                    nn.Linear(784, 128),
                    nn.ReLU(),
                    nn.Linear(128, 10),
                )
                logits = model(x)   # forward pass
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("image", 0xFF818CF8, "Perception", "Vision, speech, and language systems are all built on layered neural networks."),
        ApplicationCard("robot", 0xFF60A5FA, "Function Approximation", "Networks stand in for unknown functions in control, forecasting, and simulation."),
        ApplicationCard("chart", 0xFF10B981, "Representation Learning", "Hidden layers learn useful features automatically, replacing hand-crafted ones."),
    ),
    takeaways = listOf(
        "A neuron is a weighted sum plus a non-linearity; stacking them builds a network.",
        "Non-linear activations are what give depth its expressive power.",
        "The forward pass predicts; backpropagation and gradient descent train.",
        "Enough hidden units can approximate virtually any function — the universal approximation theorem.",
        "In the lab x = (0.90, 0.20) gives hidden values 0.72, 0.34 and 0 (switched off by ReLU) and an output of 0.581.",
    ),
    crossLinks = listOf(
        CrossLink("perceptron", "Perceptron"),
        CrossLink("backpropagation", "Backpropagation"),
    ),
)
