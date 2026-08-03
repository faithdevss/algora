package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureLayer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val backpropagationContent = TopicContent(
    topicId = "backpropagation",
    figure = Figure(
        caption = "Two passes over the same stack. Forward, each layer keeps what it computed; backward, " +
            "the loss hands each layer a gradient and every layer reuses those saved values instead of " +
            "recomputing them. That reuse is the whole algorithm — the chain rule was never the hard " +
            "part, and computing each weight's contribution independently would cost a forward pass per " +
            "weight.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("input x"),
                FigureLayer("hidden layer", "z = Wx + b, then a = f(z)", FigureTone.Primary),
                FigureLayer("output layer", "ŷ", FigureTone.Primary),
                FigureLayer("loss L(ŷ, y)", "where the backward pass starts", FigureTone.Accent),
            ),
            backwardLabel = "∂L/∂w, one chain-rule factor per layer",
        ),
    ),
    whatIsIt = listOf(
        "Backpropagation is the algorithm that computes how much each weight in a neural network contributed to the error, so gradient descent knows which way to adjust them.",
        "It's the chain rule applied efficiently: gradients from the loss flow backward through the network, reusing intermediate results instead of recomputing them.",
    ),
    steps = listOf(
        StepCard(1, "Forward Pass", "Run the input through the network, caching each layer's activations.", 0xFF818CF8),
        StepCard(2, "Compute the Loss", "Measure the error between the prediction and the target.", 0xFF60A5FA),
        StepCard(3, "Backward Pass", "Apply the chain rule layer by layer, propagating the error gradient from output to input.", 0xFF10B981),
        StepCard(4, "Update Weights", "Each weight steps opposite its gradient, scaled by the learning rate.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Chain rule", "∂L/∂w = ∂L/∂a · ∂a/∂z · ∂z/∂w", "Error attributed through each layer."),
        FormulaEntry("Weight update", "w := w − α ∂L/∂w", "Gradient descent step."),
        FormulaEntry("Cost", "O(1) vs forward pass", "One backward pass, same order as the forward one."),
    ),
    notationKey = listOf(
        NotationEntry("L", "the loss being minimized"),
        NotationEntry("∂L/∂w", "gradient of the loss w.r.t. a weight"),
        NotationEntry("α", "learning rate"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Autograd handles backprop (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                loss = criterion(model(x), y)

                optimizer.zero_grad()   # clear old gradients
                loss.backward()         # backprop: fills every .grad
                optimizer.step()        # gradient-descent update
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Training Every Deep Net", "Backprop is the universal engine behind training CNNs, RNNs, and Transformers."),
        ApplicationCard("chip", 0xFF60A5FA, "Automatic Differentiation", "Frameworks generalize it to autograd, differentiating arbitrary computation graphs."),
        ApplicationCard("bulb", 0xFF10B981, "Gradient Debugging", "Understanding it explains vanishing/exploding gradients and how to fix them."),
    ),
    takeaways = listOf(
        "Backprop computes weight gradients efficiently via the chain rule, output to input.",
        "It reuses forward-pass activations, making a backward pass about as cheap as a forward one.",
        "It supplies the gradients that gradient descent needs to learn.",
        "Vanishing/exploding gradients are backprop pathologies fixed by activations, normalization, and skip connections.",
    ),
    crossLinks = listOf(
        CrossLink("neural_network_basics", "Neural Network Basics"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
    ),
)
