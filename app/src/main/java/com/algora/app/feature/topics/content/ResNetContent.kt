package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val resNetContent = TopicContent(
    topicId = "resnet",
    whatIsIt = listOf(
        "The problem ResNet was built for is not overfitting. A 56-layer plain network had *higher training error* than a 20-layer one — it could not even fit the data it already had. Depth was making optimisation harder rather than the model weaker, which is a strange result: the deeper network can represent everything the shallow one can, simply by setting the extra layers to the identity. It just could not find that solution by gradient descent.",
        "The fix is one addition. A residual block computes y = F(x) + x instead of y = F(x), so the block only has to learn the *difference* between its input and the desired output. If the best thing it can do is nothing, a plain block has to construct the identity out of its weights while a residual block gets it by driving F toward zero — which is where weight decay is already pushing it. The easy case became the default case.",
        "The backward pass is where this pays. The simulation runs the same thirty-layer stack twice with identical weights, differing only in that one addition: starting from a unit gradient at the output, the plain stack delivers 1.3×10⁻⁸ of it to the first layer, while the residual stack delivers 6.7×10³. The identity path differentiates to 1, so every layer's gradient is the output's gradient plus a correction and no weight can attenuate it away. Note the direction — summing corrections makes the residual gradient *grow* toward the input, which is a far easier problem than vanishing and is what batch normalisation in each block, plus initialising the final BN's γ to zero so a fresh block starts as exactly the identity, exist to keep in range.",
    ),
    steps = listOf(
        StepCard(1, "Observe the Degradation", "56 plain layers train worse than 20. Not overfitting — underfitting.", 0xFF10B981),
        StepCard(2, "Add the Shortcut", "y = F(x) + x. The block learns a residual, not a whole mapping.", 0xFF06B6D4),
        StepCard(3, "Make Identity Free", "Driving F → 0 gives the identity, which weight decay already favours.", 0xFF6366F1),
        StepCard(4, "Follow the Gradient Back", "∂y/∂x = 1 + ∂F/∂x — a route no weight can close.", 0xFF8B5CF6),
        StepCard(5, "Match Shapes at Downsamples", "A 1×1 stride-2 projection when the channel count or size changes.", 0xFFF59E0B),
        StepCard(6, "Go Deep With Bottlenecks", "1×1 down, 3×3, 1×1 up — ResNet-50 at 25.6M parameters.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Residual block", "y = F(x, W) + x", "F is typically two 3×3 convolutions with BN and ReLU."),
        FormulaEntry("Gradient through the block", "∂y/∂x = 1 + ∂F/∂x", "The 1 is the entire mechanism."),
        FormulaEntry("Across L blocks", "∂L/∂x₀ = ∂L/∂x_L · ∏(1 + ∂F/∂x)", "A product of terms near 1 rather than of small weights."),
        FormulaEntry("Measured survival, 30 layers", "plain 1.3×10⁻⁸ · residual 6.7×10³", "Identical weights; the only difference is the addition."),
        FormulaEntry("Projection shortcut", "y = F(x) + W_s x", "1×1 stride-2 when the shapes do not match."),
        FormulaEntry("Bottleneck block", "1×1 → 3×3 → 1×1, ×4 width", "Same 3×3 cost at four times the channel count."),
    ),
    notationKey = listOf(
        NotationEntry("degradation problem", "deeper plain networks with worse *training* error — the paper's motivation"),
        NotationEntry("identity shortcut", "the parameter-free skip; the projection variant only appears at shape changes"),
        NotationEntry("bottleneck", "the 1×1/3×3/1×1 block used from ResNet-50 upward"),
        NotationEntry("pre-activation", "the v2 ordering (BN → ReLU → conv) that makes the skip path completely clean"),
        NotationEntry("zero-init γ", "starting the block's last BN at zero, so a fresh block is exactly the identity"),
        NotationEntry("ResNeXt", "the grouped-convolution successor: same block, cardinality as a new axis"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The block, and the one line that is the whole idea",
            accentColor = 0xFF10B981,
            code = """
                import torch.nn as nn

                class BasicBlock(nn.Module):
                    def __init__(self, c_in, c_out, stride=1):
                        super().__init__()
                        self.conv1 = nn.Conv2d(c_in, c_out, 3, stride, 1, bias=False)
                        self.bn1 = nn.BatchNorm2d(c_out)
                        self.conv2 = nn.Conv2d(c_out, c_out, 3, 1, 1, bias=False)
                        self.bn2 = nn.BatchNorm2d(c_out)
                        nn.init.zeros_(self.bn2.weight)      # a fresh block starts as the identity
                        self.short = (nn.Sequential() if stride == 1 and c_in == c_out else
                                      nn.Sequential(nn.Conv2d(c_in, c_out, 1, stride, bias=False),
                                                    nn.BatchNorm2d(c_out)))
                        self.relu = nn.ReLU(inplace=True)

                    def forward(self, x):
                        out = self.bn2(self.conv2(self.relu(self.bn1(self.conv1(x)))))
                        return self.relu(out + self.short(x))     # <- the addition
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measuring what the shortcut does to the backward pass",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch, torch.nn as nn

                torch.manual_seed(0)
                W = [nn.Linear(24, 24, bias=False) for _ in range(30)]
                for w in W:                                   # deliberately under-scaled init
                    nn.init.normal_(w.weight, std=0.55 * (2 / 24) ** 0.5)

                def survival(residual):
                    x = torch.randn(1, 24, requires_grad=True)
                    h = x
                    for w in W:
                        f = torch.relu(w(h))
                        h = h + f if residual else f
                    h.sum().backward()
                    return x.grad.norm().item()

                print(survival(residual=False))   # ~1e-8  -- eight orders of magnitude gone
                print(survival(residual=True))    # ~1e+3  -- the identity path carries it through

                # The plain number is a product of thirty small Jacobians. The residual number is a
                # product of thirty terms of the form (1 + small). Same weights, same inputs.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "The Default Backbone", "ResNet-50 remains the reference feature extractor for detection, segmentation and retrieval a decade on."),
        ApplicationCard("network", 0xFF06B6D4, "Transformers", "Every transformer block is residual — the same trick, applied to a completely different layer type."),
        ApplicationCard("game", 0xFF8B5CF6, "AlphaGo Zero and AlphaZero", "Their policy/value networks are residual towers; depth was what made the tabula-rasa training work."),
        ApplicationCard("flask", 0xFFF59E0B, "Medical and Satellite Imaging", "Pretrained ResNet features transfer to domains with far too little labelled data to train from scratch."),
    ),
    takeaways = listOf(
        "The motivating fact is degradation: deeper plain networks had worse *training* error, so this is an optimisation fix.",
        "y = F(x) + x means a block learns the difference, and gets the identity for free by driving F to zero.",
        "∂y/∂x = 1 + ∂F/∂x, so gradient reaches every layer — measured, 1.3×10⁻⁸ vs 6.7×10³ over thirty layers.",
        "The residual gradient grows rather than shrinks toward the input, which is what BN and zero-init γ hold in range.",
        "Bottleneck blocks made depth cheap: ResNet-50 is 25.6M parameters against VGG-16's 138.4M, and far more accurate.",
    ),
    crossLinks = listOf(
        CrossLink("vanishing_gradient", "Vanishing Gradients"),
        CrossLink("batch_normalization", "Batch Normalization"),
        CrossLink("densenet", "DenseNet"),
        CrossLink("transformers", "Transformers"),
    ),
)
