package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val preluContent = TopicContent(
    topicId = "prelu",
    whatIsIt = listOf(
        "PReLU is Leaky ReLU with α promoted from a hyperparameter to a parameter. Instead of fixing the negative slope at 0.01 because a paper said so, the network learns it by gradient descent along with every weight — typically one α per channel, occasionally one for the whole layer. The gradient is available for free: ∂out/∂α is simply z on the negative side and 0 on the positive side.",
        "The simulation runs it against data generated with a true negative slope of 0.25, starting from α = 0 — that is, starting as plain ReLU. Gradient descent recovers 0.2500 and drives the loss to zero. Nothing about the slope was specified in advance, and the extra cost is one number per channel against the millions already in the layer.",
        "It arrived in the 2015 paper that first reported super-human top-5 accuracy on ImageNet, and the same paper introduced He initialisation — the two belong together, because the initialisation's variance derivation accounts for the negative slope, so pairing PReLU with a scaling that assumes plain ReLU leaves the numbers slightly wrong. The caveats are the ordinary ones for extra parameters: on a small dataset a learned α is one more thing that can overfit, and reported gains over Leaky ReLU are modest. It is also worth knowing that α is unconstrained — nothing stops it learning a negative value, which would make the activation non-monotone, and nothing in the formulation says that is wrong.",
    ),
    steps = listOf(
        StepCard(1, "Promote α to a Parameter", "One per channel is the usual granularity; one per layer also works.", 0xFFF59E0B),
        StepCard(2, "Initialise It", "0.25 is the paper's choice; 0 starts you at plain ReLU.", 0xFFFBBF24),
        StepCard(3, "Take the Gradient", "∂out/∂α = z on the negative side, 0 on the positive side. Free.", 0xFFEC4899),
        StepCard(4, "Do Not Weight-Decay It", "α is a shape parameter, not a weight; decaying it pulls the activation toward ReLU.", 0xFF8B5CF6),
        StepCard(5, "Match the Initialisation", "He init takes the slope as an argument. Use it.", 0xFF6366F1),
        StepCard(6, "Inspect the Learned Values", "Early layers usually learn larger α than late ones — a readable diagnostic.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "f(zᵢ) = zᵢ if zᵢ > 0, else αᵢzᵢ", "The subscript matters: α is per channel."),
        FormulaEntry("Gradient wrt α", "∂f/∂αᵢ = zᵢ if zᵢ ≤ 0, else 0", "Summed over every position in the channel."),
        FormulaEntry("Parameter cost", "one scalar per channel", "Negligible against the weight count."),
        FormulaEntry("He init with slope", "Var(W) = 2/((1 + α²)nᵢₙ)", "The α term is why the two go together."),
        FormulaEntry("Special cases", "α = 0 is ReLU, α = 0.01 is Leaky", "PReLU contains both."),
        FormulaEntry("No constraint", "α ∈ ℝ", "Negative α is permitted and makes the function non-monotone."),
    ),
    notationKey = listOf(
        NotationEntry("αᵢ", "the learned negative slope for channel i"),
        NotationEntry("channel-wise", "one α per feature map, the usual granularity"),
        NotationEntry("channel-shared", "a single α for the layer; fewer parameters, less flexibility"),
        NotationEntry("weight decay", "should not be applied to α — it biases the shape toward ReLU"),
        NotationEntry("He initialisation", "from the same paper; its derivation includes α"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Using it, including the two things people get wrong",
            accentColor = 0xFFF59E0B,
            code = """
                import torch.nn as nn

                # num_parameters=C gives one alpha per channel. The default is 1, shared.
                act = nn.PReLU(num_parameters=64, init=0.25)

                model = nn.Sequential(nn.Conv2d(3, 64, 3, padding=1), act, nn.Conv2d(64, 10, 1))

                # 1. Do not weight-decay alpha. It is a shape parameter, and decaying it pulls the
                #    activation back toward ReLU -- undoing the thing you added it for.
                decay, no_decay = [], []
                for name, p in model.named_parameters():
                    (no_decay if "weight" not in name or p.ndim == 1 else decay).append(p)
                opt = torch.optim.SGD(
                    [{"params": decay, "weight_decay": 5e-4},
                     {"params": no_decay, "weight_decay": 0.0}],
                    lr=0.1,
                )

                # 2. Tell He init about the slope; its variance derivation includes it.
                nn.init.kaiming_normal_(model[0].weight, a=0.25, nonlinearity="leaky_relu")
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Read the learned slopes — they are a diagnostic",
            accentColor = 0xFF10B981,
            code = """
                import torch
                import torch.nn as nn

                for name, module in model.named_modules():
                    if isinstance(module, nn.PReLU):
                        a = module.weight.detach()
                        print(f"{name}: mean {a.mean():.3f}  min {a.min():.3f}  max {a.max():.3f}")

                # The paper's own observation, and it reproduces: early layers learn larger alpha
                # (closer to a linear function, keeping both signs of an edge response) while deep
                # layers learn alpha near 0 (closer to ReLU, discriminative and sparse). That is a
                # readable statement about what each depth is doing.
                #
                # If every alpha collapses to ~0, PReLU has told you plain ReLU was fine.
                # If any goes negative, the activation is non-monotone there -- allowed, and worth
                # knowing before you are surprised by it.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFFF59E0B, "Large Vision Networks", "The 2015 ImageNet result that introduced it, and still common in face-recognition backbones."),
        ApplicationCard("flask", 0xFF8B5CF6, "Architecture Diagnostics", "The learned slopes say something readable about what each layer's depth is doing."),
        ApplicationCard("chip", 0xFF6366F1, "Super-Resolution", "Widely used in SR networks, where preserving signed low-level detail matters."),
    ),
    takeaways = listOf(
        "α becomes a learned parameter, usually one per channel, at negligible cost.",
        "Its gradient is free: z on the negative side, zero on the positive side.",
        "It came with He initialisation, whose derivation accounts for the slope — pair them.",
        "Do not apply weight decay to α; it pulls the activation back toward ReLU.",
        "Learned α is unconstrained, and the values it settles on are a readable diagnostic.",
    ),
    crossLinks = listOf(
        CrossLink("leaky_relu", "Leaky ReLU"),
        CrossLink("relu", "ReLU (Rectified Linear Unit)"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
        CrossLink("backpropagation", "Backpropagation Algorithm"),
    ),
)
