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

internal val activationFunctionsContent = TopicContent(
    topicId = "activation_functions",
    figure = Figure(
        caption = "Three shapes, three different answers to the same question — what happens to a " +
            "gradient passing through. Sigmoid and tanh both flatten at either end, so a saturated " +
            "unit passes almost nothing back; tanh at least sits centred on zero, which sigmoid does " +
            "not. ReLU never flattens on the positive side, and that is the whole reason it replaced " +
            "both. Drawn on a shared axis from −4 to 4, with ReLU clipped where it leaves the top.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    label = "sigmoid",
                    points = listOf(
                        FigurePoint(0f, 0.509f),
                        FigurePoint(0.25f, 0.545f),
                        FigurePoint(0.5f, 0.75f),
                        FigurePoint(0.75f, 0.955f),
                        FigurePoint(1f, 0.991f),
                    ),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    label = "tanh",
                    points = listOf(
                        FigurePoint(0f, 0.0005f),
                        FigurePoint(0.25f, 0.018f),
                        FigurePoint(0.5f, 0.5f),
                        FigurePoint(0.75f, 0.982f),
                        FigurePoint(1f, 0.9995f),
                    ),
                    tone = FigureTone.Muted,
                ),
                FigureSeries(
                    label = "ReLU",
                    points = listOf(
                        FigurePoint(0f, 0.5f),
                        FigurePoint(0.5f, 0.5f),
                        FigurePoint(0.625f, 1f),
                        FigurePoint(1f, 1f),
                    ),
                ),
            ),
            xLabel = "z, −4 → 4",
            yLabel = "output, −1 → 1",
        ),
    ),
    whatIsIt = listOf(
        "Activation functions are the non-linearities applied after each neuron's weighted sum — they're what let a network model curves, not just straight lines.",
        "The choice matters: it affects how gradients flow, whether neurons saturate, and how fast the network trains.",
    ),
    steps = listOf(
        StepCard(1, "Sigmoid & Tanh", "Squash to (0,1) or (−1,1); smooth but saturate, causing vanishing gradients.", 0xFF818CF8),
        StepCard(2, "ReLU", "max(0, z): cheap, sparse, and the default for hidden layers — but can 'die' at zero.", 0xFF60A5FA),
        StepCard(3, "Leaky ReLU / GELU", "Variants that keep a small gradient for negatives, avoiding dead units.", 0xFF10B981),
        StepCard(4, "Softmax", "Turns a vector of logits into a probability distribution for the output layer.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("ReLU", "f(z) = max(0, z)", "Zero for negatives, identity for positives."),
        FormulaEntry("Sigmoid", "σ(z) = 1/(1+e^(−z))", "Smooth 0–1 squash; saturates at the tails."),
        FormulaEntry("Softmax", "eᶻⁱ / Σ eᶻʲ", "Normalizes logits into class probabilities."),
    ),
    notationKey = listOf(
        NotationEntry("z", "the pre-activation (weighted sum)"),
        NotationEntry("saturation", "flat region where the gradient ≈ 0"),
        NotationEntry("dead ReLU", "a unit stuck outputting 0 forever"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Common activations (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn.functional as F

                h = F.relu(z)          # hidden layers
                g = F.gelu(z)          # Transformers
                p = F.softmax(logits, dim=-1)   # output probabilities
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Hidden Layers", "ReLU and its variants are the workhorse non-linearities in nearly every deep net."),
        ApplicationCard("chart", 0xFF60A5FA, "Output Heads", "Softmax for multi-class, sigmoid for multi-label or binary outputs."),
        ApplicationCard("bulb", 0xFF10B981, "Gradient Health", "Activation choice directly controls vanishing gradients and training speed."),
    ),
    takeaways = listOf(
        "Activations inject the non-linearity that makes depth expressive.",
        "ReLU is the hidden-layer default; GELU dominates Transformers.",
        "Sigmoid/tanh saturate and cause vanishing gradients in deep stacks.",
        "Softmax and sigmoid live at the output to produce probabilities.",
    ),
    crossLinks = listOf(
        CrossLink("neural_network_basics", "Neural Network Basics"),
        CrossLink("logistic_regression", "Logistic Regression"),
    ),
)
