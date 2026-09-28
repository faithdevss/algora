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

internal val vanishingGradientContent = TopicContent(
    topicId = "vanishing_gradient",
    figure = Figure(
        caption = "The measurement the page insists on: ‖∂L/∂W‖ at each of twelve layers, from one " +
            "real forward and one real backward pass through a 16-wide stack, on a log scale " +
            "because a linear one draws eleven of the twelve as nothing. Sigmoid at unit init " +
            "runs from 1.45×10⁻³ at layer 1 to 8.26×10⁻¹ at layer 12 — the input end learns 569× " +
            "slower than the output end, on the same step size. Shrinking the init to 0.25 does " +
            "not help, it steepens the line to a ratio of 3.1×10⁷, because small weights shrink " +
            "the backward signal too; growing it to 1.5 flattens the ratio to 57 and buys that by " +
            "saturating the units, where σ′ is near zero anyway. ReLU with He initialisation on " +
            "the identical architecture is the flat line, varying by 0.6× end to end. The ceiling " +
            "σ′ ≤ 0.25 is what none of the init scales can move.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "ReLU + He",
                    listOf(
                        FigurePoint(0.000f, 0.943f), FigurePoint(0.091f, 0.952f),
                        FigurePoint(0.182f, 0.953f), FigurePoint(0.273f, 0.948f),
                        FigurePoint(0.364f, 0.943f), FigurePoint(0.455f, 0.947f),
                        FigurePoint(0.545f, 0.942f), FigurePoint(0.636f, 0.922f),
                        FigurePoint(0.727f, 0.928f), FigurePoint(0.818f, 0.925f),
                        FigurePoint(0.909f, 0.932f), FigurePoint(1.000f, 0.916f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "sigmoid, init 1.0",
                    listOf(
                        FigurePoint(0.000f, 0.574f), FigurePoint(0.091f, 0.617f),
                        FigurePoint(0.182f, 0.646f), FigurePoint(0.273f, 0.659f),
                        FigurePoint(0.364f, 0.692f), FigurePoint(0.455f, 0.711f),
                        FigurePoint(0.545f, 0.734f), FigurePoint(0.636f, 0.770f),
                        FigurePoint(0.727f, 0.786f), FigurePoint(0.818f, 0.800f),
                        FigurePoint(0.909f, 0.845f), FigurePoint(1.000f, 0.880f),
                    ),
                    tone = FigureTone.Primary,
                ),
                FigureSeries(
                    "sigmoid, init 0.25",
                    listOf(
                        FigurePoint(0.000f, 0.059f), FigurePoint(0.091f, 0.142f),
                        FigurePoint(0.182f, 0.237f), FigurePoint(0.273f, 0.293f),
                        FigurePoint(0.364f, 0.364f), FigurePoint(0.455f, 0.439f),
                        FigurePoint(0.545f, 0.515f), FigurePoint(0.636f, 0.587f),
                        FigurePoint(0.727f, 0.666f), FigurePoint(0.818f, 0.731f),
                        FigurePoint(0.909f, 0.810f), FigurePoint(1.000f, 0.891f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(0.000f, 0.574f, "569× weaker"),
                FigurePoint(0.000f, 0.059f, "3.1×10⁷ weaker", FigureTone.Warn),
            ),
            xLabel = "layer, 1 (input) → 12 (loss)",
            yLabel = "log₁₀ ‖∂L/∂W‖, −8 → +1",
        ),
    ),
    whatIsIt = listOf(
        "Backpropagation multiplies. The gradient reaching layer l is the product of every activation derivative and every weight matrix between l and the loss, so whatever those factors do on average, they do it once per layer. If the average factor is below one, the product decays geometrically with depth, and the layers nearest the input receive a gradient many orders of magnitude smaller than the layers nearest the output.",
        "With sigmoid activations this is not bad luck, it is arithmetic. The derivative σ′(z) = σ(z)(1−σ(z)) peaks at exactly 0.25 at z = 0 and falls away fast on both sides, so every layer contributes a factor of at most a quarter before the weights are even considered. The simulation builds a real 12-layer network, runs one forward and one backward pass, and measures the Frobenius norm of ∂L/∂W at every layer — the first layer's gradient comes out around 570 times weaker than the last's, and it is the measurement rather than the formula that is on screen.",
        "The reason it took until 2012 to diagnose properly is that it is silent. The forward pass looks completely healthy — activations sit in a normal range at every depth, the loss goes down, the model trains. What is actually happening is that only the last few layers are learning while the early ones, the ones that decide which features exist at all, are effectively frozen. It presents as a model that is not big enough rather than a model that is broken. And initialisation cannot rescue it: shrink the weights and they shrink the backward signal too; grow them and the units saturate, where σ′ is near zero anyway. The lab shows all three regimes. The fixes that worked were architectural — ReLU, whose derivative is exactly 1 where the unit is active; He and Xavier initialisation; normalisation layers that keep units off the saturated tails; and residual connections, which add an identity path the gradient can travel without being multiplied by anything at all. That last one is why 152 layers became routine in 2015 when 20 had been hard in 2012.",
    ),
    steps = listOf(
        StepCard(1, "Write Out the Chain", "∂L/∂W⁽ˡ⁾ is a product over every layer above l. Depth is a product, not a sum.", 0xFF06B6D4),
        StepCard(2, "Bound the Activation Derivative", "max σ′ = 0.25, at z = 0. Every sigmoid layer contributes at most a quarter.", 0xFF22D3EE),
        StepCard(3, "Measure, Do Not Assume", "Run a backward pass and take ‖∂L/∂W‖ per layer. On a log scale, or you will see nothing.", 0xFF8B5CF6),
        StepCard(4, "Notice the Forward Pass Is Fine", "Activations look healthy at every depth. This is why the failure goes undiagnosed.", 0xFF6366F1),
        StepCard(5, "Check Both Init Extremes", "Small weights shrink the signal; large ones saturate the units. Neither escapes the 0.25 ceiling.", 0xFF10B981),
        StepCard(6, "Apply the Architectural Fixes", "ReLU, He init, normalisation, residual connections. In that historical order.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("The chain", "∂L/∂a⁽ˡ⁾ = ∂L/∂a⁽ᴸ⁾ · Πₖ₌ₗ₊₁ᴸ W⁽ᵏ⁾ᵀ diag(φ′(z⁽ᵏ⁾))", "A product with one factor per layer."),
        FormulaEntry("Sigmoid derivative", "σ′(z) = σ(z)(1 − σ(z)) ≤ 0.25", "The ceiling that makes deep sigmoid stacks hopeless."),
        FormulaEntry("Tanh derivative", "tanh′(z) = 1 − tanh²(z) ≤ 1", "Better, and zero-centred, but still ≤ 1 and saturating."),
        FormulaEntry("ReLU derivative", "1 if z > 0, else 0", "Exactly one where active — no shrinking factor at all."),
        FormulaEntry("Xavier init", "Var(W) = 2/(nᵢₙ + nₒᵤₜ)", "Preserves variance for tanh and sigmoid."),
        FormulaEntry("He init", "Var(W) = 2/nᵢₙ", "The ReLU version; the factor of 2 pays for the half that is zeroed."),
        FormulaEntry("Residual block", "a⁽ˡ⁾ = a⁽ˡ⁻¹⁾ + F(a⁽ˡ⁻¹⁾)", "The gradient gets an identity path with no multiplication on it."),
    ),
    notationKey = listOf(
        NotationEntry("‖∂L/∂W‖", "Frobenius norm of a layer's gradient — one number for how much it is learning"),
        NotationEntry("saturation", "the flat tails of sigmoid or tanh, where the derivative is near zero"),
        NotationEntry("Xavier / Glorot", "variance-preserving init for saturating activations"),
        NotationEntry("He / Kaiming", "the ReLU counterpart"),
        NotationEntry("residual connection", "an additive skip path around a block"),
        NotationEntry("dead ReLU", "a unit stuck at z < 0 for every input; gradient exactly zero, permanently"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Measure it on your own model in four lines",
            accentColor = 0xFF06B6D4,
            code = """
                import torch
                import torch.nn as nn

                def stack(activation, depth=12, width=16):
                    layers = []
                    for _ in range(depth):
                        layers += [nn.Linear(width, width), activation()]
                    return nn.Sequential(*layers)

                for name, act in (("sigmoid", nn.Sigmoid), ("relu", nn.ReLU)):
                    net = stack(act)
                    if name == "relu":
                        for m in net.modules():
                            if isinstance(m, nn.Linear):
                                nn.init.kaiming_normal_(m.weight, nonlinearity="relu")

                    loss = ((net(torch.randn(32, 16)) - torch.randn(32, 16)) ** 2).mean()
                    loss.backward()

                    norms = [p.grad.norm().item() for n, p in net.named_parameters() if "weight" in n]
                    print(name, [f"{v:.1e}" for v in norms[::4]])
                    print("  first/last ratio:", f"{norms[0] / norms[-1]:.1e}")

                # This is worth running on any deep model that is training suspiciously slowly.
                # It costs one backward pass and it answers the question directly.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The fix that changed what depth was possible",
            accentColor = 0xFF10B981,
            code = """
                import torch.nn as nn

                class Residual(nn.Module):
                    def __init__(self, width):
                        super().__init__()
                        self.block = nn.Sequential(
                            nn.Linear(width, width), nn.BatchNorm1d(width), nn.ReLU(),
                            nn.Linear(width, width), nn.BatchNorm1d(width),
                        )
                        self.out = nn.ReLU()

                    def forward(self, x):
                        # The identity term is the whole point. d(x + F(x))/dx = 1 + dF/dx, so the
                        # gradient always has a route home that is multiplied by exactly 1 --
                        # even when dF/dx has decayed to nothing.
                        return self.out(x + self.block(x))

                deep = nn.Sequential(*[Residual(64) for _ in range(50)])

                # Before residuals, adding layers past ~20 made both training AND test error worse
                # -- a degradation that plain overfitting cannot explain. ResNet's 152 layers in
                # 2015 were not a bigger model so much as a trainable one.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF06B6D4, "Why LSTMs Exist", "An unrolled RNN is a very deep network with one shared weight matrix; the gates give the cell state an additive path, which is the same fix as a residual connection."),
        ApplicationCard("chip", 0xFF8B5CF6, "Why ResNets Exist", "Skip connections were introduced for exactly this, and every large vision and language model since has some form of them."),
        ApplicationCard("flask", 0xFF6366F1, "Debugging Slow Training", "Per-layer gradient norms are the first diagnostic to run when a deep model trains but plateaus early."),
    ),
    takeaways = listOf(
        "Backpropagation multiplies once per layer, so depth turns a factor below one into geometric decay.",
        "σ′ ≤ 0.25 is a hard ceiling — a deep sigmoid stack cannot propagate gradients at any init scale.",
        "The forward pass looks healthy, which is why this failure is silent and gets misread as too small a model.",
        "The early layers are the ones starved, and they are the ones that decide which features exist.",
        "The fixes are architectural: ReLU, He/Xavier init, normalisation, and residual connections.",
    ),
    crossLinks = listOf(
        CrossLink("exploding_gradient", "The Exploding Gradient Problem"),
        CrossLink("backpropagation", "Backpropagation Algorithm"),
        CrossLink("activation_functions", "Activation Functions"),
        CrossLink("lstm_gru", "LSTMs / GRUs"),
    ),
)
