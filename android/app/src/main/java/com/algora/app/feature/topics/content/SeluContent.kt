package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val seluContent = TopicContent(
    topicId = "selu",
    whatIsIt = listOf(
        "SELU is ELU multiplied by λ, with α and λ fixed at 1.6732632 and 1.0507010. Those digits are not tuned — they are the solution to a fixed-point equation, chosen so that a layer maps activations with mean 0 and variance 1 to activations with mean 0 and variance 1. The claim that follows is unusually strong: a deep stack of these normalises itself, with no normalisation layer anywhere.",
        "The simulation tests it rather than repeating it. Twenty layers, LeCun normal initialisation, and the mean stays within a few hundredths of zero and the standard deviation within a few hundredths of one the whole way down — layer 20 measures mean −0.014, std 0.971. The same twenty layers with ReLU end at std 0.090 and with tanh at 0.148, both having lost most of their signal. The property is real and it reproduces.",
        "Two conditions come attached, and the second one is easy to miss. The fixed point is derived assuming LeCun normal initialisation with variance 1/n; run the identical SELU stack under He initialisation instead and layer 20 comes out at mean 2.00 and std 4.81 — the self-normalisation is simply gone. It also requires its own dropout variant, alpha-dropout, because ordinary dropout does not preserve mean and variance. So why is SELU not everywhere? The property holds for plain feedforward stacks and not for convolutions, residual connections or attention, where the architecture moves the statistics itself; and batch and layer normalisation deliver the same stability with none of the conditions. SELU is worth knowing as a result about what an activation function *can* do on its own, and it is rarely the right default.",
    ),
    steps = listOf(
        StepCard(1, "Scale ELU by λ", "f(z) = λ·ELU(z). The two constants are the entire difference.", 0xFFF59E0B),
        StepCard(2, "Use LeCun Normal Init", "Var(W) = 1/nᵢₙ. The fixed point assumes it; He breaks it.", 0xFFFBBF24),
        StepCard(3, "Standardise the Inputs", "The map has a fixed point at (0, 1) and you have to start there.", 0xFFEC4899),
        StepCard(4, "Use Alpha-Dropout", "Ordinary dropout destroys the variance the whole scheme maintains.", 0xFF8B5CF6),
        StepCard(5, "Verify by Measuring", "Print mean and variance per layer. It either holds or it does not.", 0xFF6366F1),
        StepCard(6, "Know Where It Stops", "Plain feedforward only. Convolutions, residuals and attention break the derivation.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "f(z) = λ·z if z > 0, else λ·α(eᶻ − 1)", "λ ≈ 1.0507, α ≈ 1.6733."),
        FormulaEntry("Fixed point", "(μ, ν) = (0, 1) ↦ (0, 1)", "The mean/variance map has this as an attractor."),
        FormulaEntry("Required init", "Var(W) = 1/nᵢₙ (LeCun normal)", "Not He. The derivation depends on it."),
        FormulaEntry("Derivative", "λ if z > 0, else λα·eᶻ", "Peaks at λα ≈ 1.758, above 1."),
        FormulaEntry("Banach fixed point", "the mapping is a contraction near (0,1)", "Which is why the statistics are pulled back, not just preserved."),
        FormulaEntry("Alpha-dropout", "drops to −λα, then affinely rescales", "Preserves mean and variance where ordinary dropout does not."),
    ),
    notationKey = listOf(
        NotationEntry("λ", "the scale constant, ≈ 1.0507"),
        NotationEntry("α", "the ELU saturation constant, ≈ 1.6733"),
        NotationEntry("self-normalising", "activations converge to mean 0, variance 1 without a norm layer"),
        NotationEntry("LeCun normal", "Var(W) = 1/nᵢₙ; the required initialisation"),
        NotationEntry("alpha-dropout", "the variance-preserving dropout SELU needs"),
        NotationEntry("SNN", "self-normalising network — the paper's term for a stack of these"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "All three conditions, and what happens if you miss one",
            accentColor = 0xFFF59E0B,
            code = """
                import torch
                import torch.nn as nn

                def snn(depth=20, width=128, correct_init=True):
                    layers = []
                    for _ in range(depth):
                        lin = nn.Linear(width, width)
                        if correct_init:
                            nn.init.normal_(lin.weight, std=(1 / width) ** 0.5)   # LeCun normal
                        else:
                            nn.init.kaiming_normal_(lin.weight)                    # He -- wrong
                        nn.init.zeros_(lin.bias)
                        layers += [lin, nn.SELU()]
                    return nn.Sequential(*layers)

                x = torch.randn(256, 128)      # inputs must be standardised too
                for correct in (True, False):
                    h = x
                    for m in snn(correct_init=correct):
                        h = m(h)
                    print(correct, round(h.mean().item(), 3), round(h.std().item(), 3))
                # True  -> ~0.00, ~1.00   the property holds
                # False -> ~2.00, ~4.81   it is simply gone
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Where the derivation stops applying",
            accentColor = 0xFF10B981,
            code = """
                import torch.nn as nn

                # Works: a plain feedforward stack is exactly what the fixed point was derived for.
                mlp = nn.Sequential(
                    nn.Linear(128, 128), nn.SELU(), nn.AlphaDropout(0.05),
                    nn.Linear(128, 128), nn.SELU(),
                )

                # Does not: the residual add re-introduces the input's statistics after the
                # activation has normalised them, so the fixed-point argument no longer applies.
                class Block(nn.Module):
                    def forward(self, x):
                        return x + self.selu(self.linear(x))      # SELU cannot control this sum

                # Also does not: weight sharing in a convolution and the softmax mixing in
                # attention both move the statistics in ways the derivation does not cover.
                #
                # This is the practical reason SELU stayed niche. Every architecture that mattered
                # after 2017 has residual connections, and LayerNorm handles them without asking
                # anything of the initialiser or the dropout.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFFF59E0B, "Deep Tabular Networks", "The paper's own target: plain feedforward stacks on tabular data, where there is no convolution or residual to break the derivation."),
        ApplicationCard("chip", 0xFF8B5CF6, "Normalisation-Free Inference", "No running statistics to store or synchronise, which matters for very small batches and streaming."),
        ApplicationCard("flask", 0xFF6366F1, "A Result Worth Knowing", "Proof that activation shape alone can control the statistics of a deep stack — even where the practice went elsewhere."),
    ),
    takeaways = listOf(
        "λ and α are the solution to a fixed-point equation, not tuned values.",
        "It genuinely self-normalises: mean ~0 and std ~1 held across twenty layers, measured.",
        "It requires LeCun normal init — under He the property vanishes entirely.",
        "It also requires alpha-dropout, since ordinary dropout destroys the variance.",
        "It does not survive convolutions, residual connections or attention, which is why normalisation layers won.",
    ),
    crossLinks = listOf(
        CrossLink("elu", "ELU (Exponential Linear Unit)"),
        CrossLink("batch_normalization", "Batch Normalization"),
        CrossLink("dropout", "Dropout"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
    ),
)
