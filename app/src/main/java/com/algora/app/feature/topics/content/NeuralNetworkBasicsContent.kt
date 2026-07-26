package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val neuralNetworkBasicsContent = TopicContent(
    topicId = "neural_network_basics",
    whatIsIt = listOf(
        "A neural network stacks layers of simple units (neurons), each computing a weighted sum of its inputs followed by a non-linear activation, to learn complex input-to-output mappings.",
        "Stacking non-linear layers lets the network approximate almost any function — the universal approximation property that makes deep learning so flexible.",
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
    ),
    crossLinks = listOf(
        CrossLink("perceptron", "Perceptron"),
        CrossLink("backpropagation", "Backpropagation"),
    ),
)
