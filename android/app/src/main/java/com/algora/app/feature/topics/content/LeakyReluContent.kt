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

internal val leakyReluContent = TopicContent(
    topicId = "leaky_relu",
    figure = Figure(
        caption = "α = 0.01 is too small to see on a plot of the curve, so this is what it does " +
            "instead — the same learning-rate sweep, run on both. ReLU loses 12.5%, 78% and finally " +
            "the entire layer as the rate climbs; Leaky ReLU loses nothing at any rate tested. The " +
            "dying problem is not reduced, it is structurally absent: a unit cannot become permanently " +
            "unreachable when the derivative is never exactly zero.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("ReLU 30", 0.125f, FigureTone.Warn),
                FigureBar("Leaky", 0f),
                FigureBar("ReLU 60", 0.78f, FigureTone.Warn),
                FigureBar("Leaky", 0f),
                FigureBar("ReLU 100", 1f, FigureTone.Warn),
                FigureBar("Leaky", 0f),
            ),
            yLabel = "units dead for every input in the batch",
        ),
    ),
    whatIsIt = listOf(
        "max(αz, z) with α = 0.01 instead of max(0, z). The negative branch is no longer flat, so its derivative is 0.01 rather than 0 — small, but not zero, and that is the entire point. A unit whose bias has drifted negative still receives a gradient, so it can still come back.",
        "The simulation runs the same learning-rate sweep that kills ReLU's units. ReLU loses 12.5%, 78% and 100% of its layer as the rate climbs from 30 to 100; Leaky ReLU loses nothing at any rate tested. The dying problem is not reduced, it is structurally absent — a unit cannot become permanently unreachable when the derivative is never exactly zero.",
        "What it costs is worth stating plainly. The output is no longer exactly zero for negative input, so the representation stops being sparse, and ReLU's exact zeros are genuinely useful — both as a mild regulariser and for the sparse kernels some hardware exploits. And α is a hyperparameter nobody tunes: 0.01 is a number from the paper with no principled derivation, which is exactly the gap PReLU fills by learning it instead. The honest summary is that Leaky ReLU is cheap insurance rather than an upgrade. Published comparisons find the accuracy difference against ReLU small and inconsistent across tasks; the case for it is that it removes one specific catastrophic failure at essentially no cost, not that it learns better.",
    ),
    steps = listOf(
        StepCard(1, "Add a Slope", "max(αz, z) with a small positive α. One character of code.", 0xFFF59E0B),
        StepCard(2, "The Derivative Is Never Zero", "α on the negative side, so gradient always flows.", 0xFFFBBF24),
        StepCard(3, "Dead Units Become Impossible", "There is always something to descend, so recovery is always available.", 0xFFEC4899),
        StepCard(4, "Lose the Exact Zeros", "Sparsity goes, along with what depended on it.", 0xFF8B5CF6),
        StepCard(5, "Accept That α Is a Guess", "0.01 is convention, not derivation. PReLU learns it instead.", 0xFF6366F1),
        StepCard(6, "Treat It as Insurance", "Reach for it when you have seen dead units, or cannot afford to check.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "f(z) = z if z > 0, else αz", "α = 0.01 conventionally."),
        FormulaEntry("Derivative", "1 if z > 0, else α", "Never exactly zero, which is the whole design."),
        FormulaEntry("Equivalent form", "max(αz, z) for 0 < α < 1", "One expression, no branch."),
        FormulaEntry("Randomised variant (RReLU)", "α ~ U(l, u) during training", "Sampled per unit; the mean is used at inference."),
        FormulaEntry("Mean output", "≈ 0.395 for z ~ N(0,1)", "Barely below ReLU's 0.399 — it is not a zero-centring fix."),
        FormulaEntry("Sparsity", "0% exact zeros", "Against ReLU's ~50%."),
    ),
    notationKey = listOf(
        NotationEntry("α", "the negative slope; a hyperparameter here, a parameter in PReLU"),
        NotationEntry("RReLU", "the randomised variant, where α is sampled during training"),
        NotationEntry("sparsity", "the fraction of exact zeros; this activation has none"),
        NotationEntry("dying ReLU", "the failure this activation removes"),
        NotationEntry("negative_slope", "the argument name in every framework"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The change, and the sweep that motivates it",
            accentColor = 0xFFF59E0B,
            code = """
                import torch.nn as nn

                model = nn.Sequential(
                    nn.Linear(256, 256),
                    nn.LeakyReLU(negative_slope=0.01),    # was nn.ReLU()
                    nn.Linear(256, 10),
                )

                # He init has a nonlinearity argument that accounts for the slope. Using the ReLU
                # variance with a leaky activation leaves the scaling slightly off -- small, but
                # free to get right.
                for m in model.modules():
                    if isinstance(m, nn.Linear):
                        nn.init.kaiming_normal_(m.weight, a=0.01, nonlinearity="leaky_relu")

                # Run the dead_fraction sweep from the ReLU topic with LeakyReLU substituted and
                # every rate returns 0.0. Not reduced -- absent.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What it costs: the sparsity is gone",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch

                z = torch.randn(10_000)

                relu = torch.relu(z)
                leaky = torch.nn.functional.leaky_relu(z, 0.01)

                print((relu == 0).float().mean().item())     # ~0.50 -- half exactly zero
                print((leaky == 0).float().mean().item())     # ~0.00 -- none

                # That matters in three places: sparse kernels that skip zeros, quantisation
                # schemes that special-case them, and the mild regularising effect of a genuinely
                # sparse representation. None of those are decisive, but "leaky is strictly
                # better" is not true either -- it is a trade, and the accuracy difference in
                # published comparisons is small and does not consistently favour one side.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFFF59E0B, "GAN Discriminators", "Near-universal here. A discriminator that loses units early stops giving the generator any signal, so the insurance is worth more than the sparsity."),
        ApplicationCard("chip", 0xFF8B5CF6, "Object Detection", "YOLO used it throughout its earlier versions, where deep stacks and aggressive learning rates meet."),
        ApplicationCard("flask", 0xFF6366F1, "Recovering a Stalled Model", "The first thing to try when the dead-unit diagnostic comes back non-zero."),
    ),
    takeaways = listOf(
        "A small negative slope means the derivative is never exactly zero, so units cannot die.",
        "The dying-ReLU failure is structurally absent, not merely rarer.",
        "The cost is sparsity: no exact zeros at all.",
        "α = 0.01 is convention with no derivation behind it — PReLU exists because of that.",
        "It removes a catastrophic failure at near-zero cost; it does not reliably improve accuracy.",
    ),
    crossLinks = listOf(
        CrossLink("relu", "ReLU (Rectified Linear Unit)"),
        CrossLink("prelu", "Parametric ReLU (PReLU)"),
        CrossLink("elu", "ELU (Exponential Linear Unit)"),
        CrossLink("gans", "GANs"),
    ),
)
