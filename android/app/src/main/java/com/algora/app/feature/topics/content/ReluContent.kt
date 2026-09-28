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

internal val reluContent = TopicContent(
    topicId = "relu",
    figure = Figure(
        caption = "One kink, and everything follows from which side of it a unit sits on. To the right " +
            "the derivative is exactly 1, so backpropagation multiplies by one per layer instead of by " +
            "at most a quarter — that is most of why depth became practical. To the left it is exactly " +
            "0, and a unit driven there by too large a step receives no gradient from any example ever " +
            "again: at lr 60, 78% of the layer; at lr 100, all of it.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    label = "max(0, z)",
                    points = listOf(FigurePoint(0f, 0f), FigurePoint(0.5f, 0f), FigurePoint(1f, 1f)),
                ),
            ),
            xLabel = "z, −1 → 1",
            yLabel = "output",
            markers = listOf(
                FigurePoint(0.25f, 0f, "derivative 0 — nothing comes back", FigureTone.Warn),
                FigurePoint(0.85f, 0.70f, "derivative 1", FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "max(0, z). No exponential, no division, and a derivative that is exactly 1 wherever the unit is active — so backpropagation multiplies the signal by exactly one per layer instead of by at most a quarter. That single property is most of why depth stopped being impractical around 2012, and it is worth appreciating how little machinery it took.",
        "The half of the domain with zero derivative looks like sigmoid's problem and is a different thing. The simulation measures both: ReLU's zero-derivative fraction is 50% and stays at 50% no matter how wide the pre-activations get, because the boundary sits at zero and does not move. Sigmoid's climbs from 0% to 78% over the same sweep. One is a fixed property of the shape — and a useful one, since exact zeros make the representation sparse — while the other is a failure that worsens as weights grow during training.",
        "ReLU's real failure has a different cause. Train a layer with plain SGD and count the units inactive for *every* input in the batch: at a learning rate of 1, none; at 30, 12.5%; at 60, 78%; at 100, the entire layer. A large step drives the bias far enough negative that the unit stops firing at all — and then its gradient is exactly zero for every example, so no future update can move it. The simulation shows the permanence directly: every death happens inside the first ten steps and the fraction is flat for the remaining hundred and ten. Dying ReLU is caused by the step size, not by the initialisation, and Leaky ReLU on the identical run loses nothing at any rate tested.",
    ),
    steps = listOf(
        StepCard(1, "Take max(0, z)", "A comparison. No transcendental function anywhere.", 0xFFF59E0B),
        StepCard(2, "Note the Derivative", "Exactly 1 when active, exactly 0 when not. Nothing in between to compound.", 0xFFFBBF24),
        StepCard(3, "Initialise with He", "Var(W) = 2/nᵢₙ. The 2 pays for the half of the output that is zeroed.", 0xFFEC4899),
        StepCard(4, "Enjoy the Sparsity", "Exact zeros are useful — a regulariser, and something sparse kernels can exploit.", 0xFF8B5CF6),
        StepCard(5, "Watch the Step Size", "Too large a rate drives biases negative and kills units permanently.", 0xFF6366F1),
        StepCard(6, "Check for Dead Units", "Fraction inactive across the whole batch. Cheap to measure, easy to miss.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "ReLU(z) = max(0, z)", "Also written z⁺."),
        FormulaEntry("Derivative", "1 if z > 0, else 0", "Undefined at exactly 0; every framework picks 0 and it never matters."),
        FormulaEntry("He initialisation", "Var(W) = 2/nᵢₙ", "The factor of 2 compensates for the zeroed half."),
        FormulaEntry("Expected output", "E[ReLU(z)] = σ/√(2π) for z ~ N(0, σ²)", "Positive, so ReLU is not zero-centred either."),
        FormulaEntry("Sparsity", "~50% of units zero at init", "By construction, and constant in the pre-activation scale."),
        FormulaEntry("Dead unit", "z < 0 for every input ⟹ ∂L/∂w = 0 always", "Which is why the death is permanent."),
    ),
    notationKey = listOf(
        NotationEntry("dying ReLU", "a unit inactive for every input; unrecoverable"),
        NotationEntry("He / Kaiming init", "the variance-preserving initialisation for rectifiers"),
        NotationEntry("sparsity", "the fraction of activations that are exactly zero"),
        NotationEntry("piecewise linear", "ReLU networks are piecewise-linear functions of their input"),
        NotationEntry("rectifier", "the family: ReLU, Leaky, PReLU, ELU, SELU"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Find dead units — four lines, and worth running on any ReLU model",
            accentColor = 0xFFF59E0B,
            code = """
                import torch
                import torch.nn as nn

                activations = {}

                def record(name):
                    def hook(_module, _inp, out):
                        # A unit is dead only if it is off for EVERY example. Off for one input
                        # is not dead, it is just off -- that distinction is the whole diagnostic.
                        activations[name] = (out > 0).any(dim=0).float().mean().item()
                    return hook

                for name, module in model.named_modules():
                    if isinstance(module, nn.ReLU):
                        module.register_forward_hook(record(name))

                model(next(iter(loader))[0])
                for name, alive in activations.items():
                    print(f"{name}: {(1 - alive) * 100:.1f}% dead")
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The learning rate is the cause, measured",
            accentColor = 0xFF6366F1,
            code = """
                import torch
                import torch.nn as nn

                def dead_fraction(lr, steps=120, width=32, batch=64, seed=0):
                    torch.manual_seed(seed)
                    layer = nn.Linear(width, width)
                    nn.init.kaiming_normal_(layer.weight, nonlinearity="relu")
                    x, y = torch.randn(batch, width), torch.randn(batch, width) * 0.5
                    opt = torch.optim.SGD(layer.parameters(), lr=lr)
                    for _ in range(steps):
                        opt.zero_grad()
                        ((torch.relu(layer(x)) - y) ** 2).mean().backward()
                        opt.step()
                    return 1 - (layer(x) > 0).any(dim=0).float().mean().item()

                for lr in (1, 30, 60, 100):
                    print(lr, round(dead_fraction(lr), 3))
                # 1 -> 0.0, 30 -> 0.125, 60 -> 0.781, 100 -> 1.0
                #
                # And the deaths all happen in the first few steps. After that the dead units have
                # zero gradient forever, so nothing recovers -- lowering the rate later does not
                # help, because there is no gradient left to descend.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFFF59E0B, "Convolutional Networks", "The default since AlexNet, and the reason its depth trained at all."),
        ApplicationCard("chip", 0xFF8B5CF6, "Efficient Inference", "A comparison rather than an exponential, and exact zeros that sparse kernels and quantisation both exploit."),
        ApplicationCard("robot", 0xFF6366F1, "Reinforcement Learning", "The usual choice in policy and value networks, where cheap forward passes matter more than the last point of accuracy."),
    ),
    takeaways = listOf(
        "Derivative exactly 1 where active, so depth multiplies by one instead of by a fraction.",
        "Its 50% zero region is fixed and by design — unlike sigmoid's saturation, it does not grow.",
        "Exact zeros give sparsity, which is genuinely useful.",
        "Dying ReLU is caused by too large a learning rate, and it is permanent — zero gradient forever.",
        "Measure the dead fraction across a whole batch; it is four lines and easy to forget.",
    ),
    crossLinks = listOf(
        CrossLink("leaky_relu", "Leaky ReLU"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
        CrossLink("sigmoid", "Sigmoid"),
        CrossLink("gelu", "GELU (Gaussian Error Linear Unit)"),
    ),
)
