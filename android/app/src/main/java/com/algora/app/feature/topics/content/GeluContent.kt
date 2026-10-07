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

internal val geluContent = TopicContent(
    topicId = "gelu",
    figure = Figure(
        caption = "GELU against ReLU over the range where they differ. Above z ≈ 2 the two are " +
            "indistinguishable and below z ≈ −3 GELU is within 0.004 of zero, so everything this " +
            "function is for lives in a band around the origin. There it does two things ReLU " +
            "cannot. It dips — to −0.170 at z = −0.752, so a unit that is slightly off still " +
            "passes a small signal and a nonzero gradient instead of being cut flat. And it has no " +
            "kink: the corner where ReLU's derivative jumps from 0 to 1 is replaced by a smooth " +
            "bend, which is where the largest gap between them, 0.170 at z = +0.752, sits. That " +
            "shape is z·Φ(z), an input weighted by how likely a normal draw is to fall below it — " +
            "a random keep-or-drop gate replaced by its expectation.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "ReLU",
                    listOf(FigurePoint(0f, 0.111f), FigurePoint(0.6f, 0.111f), FigurePoint(1f, 1f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "GELU",
                    listOf(
                        FigurePoint(0f, 0.109f), FigurePoint(0.1f, 0.104f), FigurePoint(0.2f, 0.091f),
                        FigurePoint(0.3f, 0.067f), FigurePoint(0.35f, 0.052f), FigurePoint(0.4f, 0.041f),
                        FigurePoint(0.45f, 0.036f), FigurePoint(0.5f, 0.043f), FigurePoint(0.55f, 0.067f),
                        FigurePoint(0.6f, 0.111f), FigurePoint(0.65f, 0.178f), FigurePoint(0.7f, 0.265f),
                        FigurePoint(0.75f, 0.37f), FigurePoint(0.8f, 0.485f), FigurePoint(0.9f, 0.733f),
                        FigurePoint(1f, 0.98f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.45f, 0.036f, "−0.170 at −0.752", FigureTone.Warn),
                FigurePoint(0.75f, 0.37f, "gap 0.170"),
            ),
            xLabel = "z, −3 to +2",
            yLabel = "output, −0.25 to 2",
        ),
    ),
    whatIsIt = listOf(
        "GELU is z·Φ(z) — the input times the probability that a standard normal draw falls below it. Its motivation is different in kind from every other activation here. Rather than shaping a curve to have nice properties, it asks what happens if a unit is kept or dropped at random with probability depending on its own value, and then takes the expectation of that. It is dropout and ReLU merged into one deterministic function.",
        "Shape-wise the result is a smoothed ReLU with a dip: the simulation measures a minimum of −0.1700 at z = −0.752, and a maximum deviation from ReLU of 0.1700 occurring at z = 0.752. That is a small difference in absolute terms and it is concentrated exactly at the kink, where ReLU is not differentiable — which is the part that matters for optimisation. Against Swish, which it closely resembles, the largest gap is 0.1930; the two were derived completely differently and landed in almost the same place, and neither has a convincing argument for being better.",
        "One practical detail matters more than it should. Because Φ was expensive, BERT and GPT-2 shipped a tanh-based approximation, and it is within 0.00047 of the exact function everywhere. That difference is negligible mathematically and not negligible operationally: pretrained weights were fitted with the approximation, so frameworks keep both — `nn.GELU()` and `nn.GELU(approximate='tanh')` — and swapping one for the other under an existing checkpoint shifts its outputs slightly. GELU is the default in essentially every transformer, and the honest reason to know it is that: the empirical case is a small consistent edge over ReLU on those architectures, the theoretical case is suggestive rather than decisive, and the standard is partly a standard because everyone else uses it.",
    ),
    steps = listOf(
        StepCard(1, "Start From Stochastic Gating", "Keep a unit with probability Φ(z), drop it otherwise. That is the idea.", 0xFFF59E0B),
        StepCard(2, "Take the Expectation", "E[z·Bernoulli(Φ(z))] = z·Φ(z). Deterministic, differentiable.", 0xFFFBBF24),
        StepCard(3, "Read the Shape", "A smoothed ReLU with a small dip below zero near −0.75.", 0xFFEC4899),
        StepCard(4, "Note the Derivative Exceeds 1", "Like Swish, it can amplify the backward signal slightly.", 0xFF8B5CF6),
        StepCard(5, "Mind the Approximation", "The tanh form is what pretrained checkpoints were fitted with.", 0xFF6366F1),
        StepCard(6, "Use It in Transformers", "The default there, and the reason is empirical rather than principled.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "GELU(z) = z·Φ(z)", "Φ is the standard normal CDF."),
        FormulaEntry("With erf", "z·½[1 + erf(z/√2)]", "The exact form frameworks compute."),
        FormulaEntry("Derivative", "Φ(z) + z·φ(z)", "φ is the normal PDF; peaks at ≈ 1.129."),
        FormulaEntry("Tanh approximation", "0.5z(1 + tanh[√(2/π)(z + 0.044715z³)])", "Within 0.00047 of exact, everywhere."),
        FormulaEntry("Minimum", "≈ −0.170 at z ≈ −0.752", "The non-monotone dip."),
        FormulaEntry("Max deviation from ReLU", "0.170, at z ≈ 0.752", "Concentrated at the kink."),
    ),
    notationKey = listOf(
        NotationEntry("Φ(z)", "standard normal CDF — the keep probability"),
        NotationEntry("erf", "the error function; how Φ is actually computed"),
        NotationEntry("stochastic regulariser", "the dropout-style framing GELU takes the expectation of"),
        NotationEntry("approximate='tanh'", "the framework flag selecting the BERT/GPT-2 form"),
        NotationEntry("GEGLU", "the gated variant used in T5 v1.1 feed-forward blocks; the Swish-gated sibling SwiGLU is used in PaLM and LLaMA"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Exact and approximate, and why both exist",
            accentColor = 0xFFF59E0B,
            code = """
                import torch
                import torch.nn as nn

                z = torch.linspace(-6, 6, 100_001)
                exact = nn.GELU()(z)
                approx = nn.GELU(approximate="tanh")(z)

                print((exact - approx).abs().max().item())    # ~4.7e-4

                # Mathematically negligible. Operationally not: BERT and GPT-2 were TRAINED with
                # the tanh form, so their weights encode it. Loading such a checkpoint under the
                # exact GELU shifts every activation slightly -- small, but enough to change
                # generated text and to break exact-reproduction tests. Match what the checkpoint
                # was trained with; do not assume "exact is better".
                #
                # The original motivation, made literal:
                mask = torch.bernoulli(torch.distributions.Normal(0, 1).cdf(z))
                print(((z * mask).mean() - exact.mean()).abs().item())   # small; it IS the mean
            """.trimIndent(),
        ),
        CodeBlock(
            title = "In a transformer block, and the gated variant that followed",
            accentColor = 0xFF10B981,
            code = """
                import torch.nn as nn

                class FeedForward(nn.Module):
                    def __init__(self, d, hidden):
                        super().__init__()
                        self.up, self.down, self.act = nn.Linear(d, hidden), nn.Linear(hidden, d), nn.GELU()

                    def forward(self, x):
                        return self.down(self.act(self.up(x)))

                # GEGLU, from the "GLU Variants Improve Transformer" line of work: split the
                # projection and use half of it as a gate. T5 v1.1 uses this; PaLM and LLaMA use the Swish-gated SwiGLU.
                class GeGLU(nn.Module):
                    def __init__(self, d, hidden):
                        super().__init__()
                        # 2/3 the hidden size keeps the parameter count matched -- worth doing,
                        # or the comparison against plain GELU is not a fair one.
                        self.up = nn.Linear(d, 2 * hidden)
                        self.down = nn.Linear(hidden, d)
                        self.act = nn.GELU()

                    def forward(self, x):
                        a, b = self.up(x).chunk(2, dim=-1)
                        return self.down(self.act(a) * b)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFFF59E0B, "Every Major Transformer", "BERT, GPT, T5, ViT. If a model has a feed-forward block, this is usually its activation."),
        ApplicationCard("Image", 0xFF8B5CF6, "Vision Transformers", "Inherited wholesale from the language side, along with LayerNorm and the residual structure."),
        ApplicationCard("chip", 0xFF6366F1, "Checkpoint Compatibility", "Knowing which variant a checkpoint used is a real operational concern, not a footnote."),
    ),
    takeaways = listOf(
        "z·Φ(z): the expectation of gating a unit by its own value, so dropout and ReLU in one function.",
        "A smoothed ReLU with a −0.17 dip; it differs from ReLU by at most 0.17, right at the kink.",
        "Its derivative exceeds 1, like Swish's — the two landed in nearly the same place by different routes.",
        "The tanh approximation is within 5×10⁻⁴, and pretrained checkpoints depend on which one you use.",
        "It is the transformer default on empirical grounds, and partly because it already is one.",
    ),
    crossLinks = listOf(
        CrossLink("swish", "Swish (by Google)"),
        CrossLink("relu", "ReLU (Rectified Linear Unit)"),
        CrossLink("transformers", "Transformers"),
        CrossLink("dropout", "Dropout"),
    ),
)
