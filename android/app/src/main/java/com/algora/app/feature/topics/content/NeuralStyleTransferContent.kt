package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val neuralStyleTransferContent = TopicContent(
    topicId = "neural_style_transfer",
    figure = Figure(
        caption = "Why the method transfers brushwork and never composition, in one row. A Gram " +
            "matrix sums over spatial positions, so permuting those positions leaves it " +
            "bit-for-bit unchanged — the second row is the first one shuffled, and the style loss " +
            "between them is 1.5×10⁻³², floating-point zero, while the content loss on the same " +
            "pair is 0.163. That is an equality, not an approximation: style as this algorithm " +
            "defines it is a claim about which features co-occur and contains no claim about " +
            "where. It is also not compression — VGG-19's conv4_1 map holds 401,408 values against " +
            "the Gram's 131,328 unique entries, a ratio of 3.06. What matters is that an enormous " +
            "set of different images share one Gram, and the optimiser picks whichever also fits " +
            "the content loss.",
        shape = FigureShape.Strip(
            cells = listOf("p₁", "p₂", "p₃", "p₄", "p₅", "p₆"),
            bands = listOf(
                FigureBand(0, 5, "feature columns, one per spatial position", FigureTone.Primary),
            ),
            aux = listOf("p₄", "p₁", "p₆", "p₂", "p₅", "p₃"),
            auxLabel = "any permutation — same Gram, style loss 1.5×10⁻³²",
        ),
    ),
    whatIsIt = listOf(
        "Neural style transfer predates every generative model in this section and trains no network at all. The weights are a frozen, pretrained VGG; what gets optimized is the image itself, pixel by pixel, by gradient descent on two losses. The content loss compares the image's feature map at one layer against the content photograph's. The style loss compares Gram matrices at five layers against the painting's. Run a few hundred steps and the pixels settle into something that has the photograph's layout and the painting's texture. The whole method rests on one definition, and the definition is far sharper than its usual description.",
        "A Gram matrix is G = F Fᵀ, where F is a feature map flattened to channels × positions. Entry G[i][j] is the sum over every spatial position of channel i times channel j — how strongly two features co-occur, added up across the image. The sum is the crucial part. Because it runs over positions, permuting those positions leaves G bit-for-bit unchanged. This is not a good approximation or a useful simplification; it is an equality. Take a real feature map, shuffle its spatial columns into a completely different arrangement, and the style loss between the original and the shuffle is 1.5×10⁻³², which is floating-point zero. The content loss between the same two is 0.163. Style, as this algorithm defines it, is a statement about which features occur together and contains no statement whatsoever about where.",
        "That single property explains both what the method does well and what it cannot do. It transfers brushwork, palette and texture faithfully, because those really are position-independent statistics. It cannot transfer composition, and it will happily paint sky texture onto a face, because nothing in the loss knows the difference. It is also why the Gram matrix is not really a compression: at VGG-19's conv4_1 the feature map is 401,408 values and the Gram's unique entries number 131,328, a ratio of only 3.06. The reduction is modest. What matters is not that the representation is smaller but that it is invariant — an enormous set of completely different images share one Gram matrix, and the optimizer is free to pick whichever of them also satisfies the content loss.",
    ),
    steps = listOf(
        StepCard(1, "Freeze the Network", "Pretrained VGG-19. No weight is ever updated.", 0xFF10B981),
        StepCard(2, "Optimize the Image", "The pixels are the parameters — that is the unusual part.", 0xFF3B82F6),
        StepCard(3, "Content Loss on Raw Features", "conv4_2, compared position by position.", 0xFF8B5CF6),
        StepCard(4, "Style Loss on Gram Matrices", "Five layers, conv1_1 through conv5_1.", 0xFFF59E0B),
        StepCard(5, "Prove the Invariance", "Shuffle the positions: style loss 1.5×10⁻³², content loss 0.163.", 0xFFEC4899),
        StepCard(6, "Read the Consequences", "Texture transfers; composition cannot.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Gram matrix", "G = F Fᵀ / N", "F is channels × positions; the sum is over positions."),
        FormulaEntry("Total loss", "α·L_content + β·L_style", "Their ratio is the one real dial."),
        FormulaEntry("Content loss", "‖F − F_content‖² at conv4_2", "Position-sensitive."),
        FormulaEntry("Style loss", "Σ_layers ‖G − G_style‖²", "Position-blind, exactly."),
        FormulaEntry("Under a shuffle", "style 1.5×10⁻³² · content 0.163", "Floating-point zero against a real difference."),
        FormulaEntry("conv4_1", "401,408 values → 131,328 entries", "Only 3.06× — invariance, not compression."),
    ),
    notationKey = listOf(
        NotationEntry("F", "a feature map, flattened to channels × spatial positions"),
        NotationEntry("Gram matrix", "channel co-occurrence summed over position — hence blind to it"),
        NotationEntry("content layer", "conv4_2; deep enough for layout, shallow enough to keep it"),
        NotationEntry("style layers", "conv1_1 … conv5_1; fine texture through coarse structure"),
        NotationEntry("α / β", "the content-to-style weighting, the parameter that actually matters"),
        NotationEntry("optimized variable", "the image, not the weights"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The invariance, asserted rather than described",
            accentColor = 0xFFEC4899,
            code = """
                import torch

                def gram(f):                      # f: (channels, positions)
                    return f @ f.t() / f.shape[1]

                f = torch.rand(8, 64)
                shuffled = f[:, torch.randperm(64)]      # same values, different places

                style   = ((gram(f) - gram(shuffled)) ** 2).mean()
                content = ((f - shuffled) ** 2).mean()

                print(style.item(), content.item())
                # 1.48e-32   0.163
                #
                # The first number is zero to floating-point. Not "small" -- the Gram matrix sums
                # over positions, so a permutation cannot change it, and this is an identity rather
                # than an empirical finding. Every arrangement of these values has the same style.
                #
                # The second number is why the content loss is computed on the raw feature map: it
                # is the only thing in the objective that knows where anything is.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Optimizing the image instead of the network",
            accentColor = 0xFF3B82F6,
            code = """
                import torch
                from torchvision.models import vgg19

                vgg = vgg19(weights="DEFAULT").features.eval()
                for p in vgg.parameters():
                    p.requires_grad_(False)          # the network is frozen -- all of it

                image = content_image.clone().requires_grad_(True)    # <- the parameters
                optimizer = torch.optim.LBFGS([image])

                STYLE_LAYERS   = {0: "conv1_1", 5: "conv2_1", 10: "conv3_1", 19: "conv4_1", 28: "conv5_1"}
                CONTENT_LAYER  = 21                                   # conv4_2

                def step():
                    optimizer.zero_grad()
                    loss, x = 0.0, image
                    for i, layer in enumerate(vgg):
                        x = layer(x)
                        if i == CONTENT_LAYER:
                            loss = loss + 1.0 * ((x - content_features) ** 2).mean()
                        if i in STYLE_LAYERS:
                            f = x.flatten(2).squeeze(0)
                            loss = loss + 1e6 * ((gram(f) - style_grams[i]) ** 2).mean()
                    loss.backward()
                    return loss

                for _ in range(300):
                    optimizer.step(step)

                # The 1e6 is not arbitrary tuning noise -- Gram entries are products of activations
                # and land orders of magnitude below feature differences, so without it the style
                # term contributes nothing. The content/style ratio is the dial worth touching.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFF10B981, "Artistic Filters", "The technique behind a generation of photo-styling apps."),
        ApplicationCard("flask", 0xFF3B82F6, "Texture Synthesis", "Drop the content term and it generates texture from the Gram alone."),
        ApplicationCard("chip", 0xFFF59E0B, "Feed-Forward Distillation", "Train a network to do in one pass what this does in 300 steps."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Composition transfer — the Gram matrix provably cannot carry it."),
    ),
    takeaways = listOf(
        "Nothing is trained: VGG is frozen and the image itself is the optimized variable.",
        "Content loss uses the raw feature map; style loss uses Gram matrices at five layers.",
        "A Gram matrix sums over spatial positions, so permuting them cannot change it.",
        "Measured on a shuffled feature map: style loss 1.5×10⁻³² — floating-point zero — against a content loss of 0.163.",
        "That is an identity, not an approximation, and it defines what this algorithm means by \"style\".",
        "It transfers brushwork and palette faithfully and cannot transfer composition at all.",
        "At conv4_1 the Gram is only a 3.06× reduction — the useful property is invariance, not size.",
    ),
    crossLinks = listOf(
        CrossLink("stylegan", "StyleGAN"),
        CrossLink("vgg", "VGG-16 / VGG-19"),
        CrossLink("cyclegan", "CycleGAN (Image-to-Image)"),
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("conv_layers", "Convolution Layers"),
    ),
)
