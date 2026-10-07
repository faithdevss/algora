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

internal val vggContent = TopicContent(
    topicId = "vgg",
    figure = Figure(
        caption = "Parameters per convolution, in units of C² for C channels in and out, for the " +
            "two substitutions VGG's design rests on. A 5×5 kernel is 25C²; two stacked 3×3s see " +
            "the same 5×5 patch and cost 18C², 28% less. A 7×7 is 49C²; three stacked 3×3s reach " +
            "the same 7×7 and cost 27C², 45% less. The receptive field is identical in both " +
            "cases — that is the point, the comparison is like for like — and the stack throws in " +
            "two or three non-linearities where the single large kernel has one. Applied without " +
            "exception this gives VGG-16 its 138,357,544 parameters, of which only 14,714,688 are " +
            "in the thirteen convolutions: fc6 alone, 4096 × (25088 + 1) = 102,764,544, is 74% of " +
            "the model, which is the part later architectures deleted.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("one 5×5", 0.510f, FigureTone.Warn),
                FigureBar("two 3×3", 0.367f, FigureTone.Accent),
                FigureBar("one 7×7", 1.000f, FigureTone.Warn),
                FigureBar("three 3×3", 0.551f, FigureTone.Accent),
            ),
            xLabel = "same receptive field within each pair",
            yLabel = "parameters, in C² · 49C² full scale",
        ),
    ),
    whatIsIt = listOf(
        "VGG is one design decision applied without exception: every convolution is 3×3, stride 1, padded; every pooling layer is 2×2 stride 2; channels double after each pool — 64, 128, 256, 512, 512. VGG-16 has thirteen convolutions and three dense layers, VGG-19 has three more convolutions. The paper's contribution is the controlled experiment: hold the kernel at the smallest useful size, vary only depth, and show that accuracy keeps improving to 16–19 layers.",
        "The case for the small kernel is arithmetic, and the simulation prices it. Two stacked 3×3 layers see the same 5×5 receptive field as one 5×5 layer, with 4,718,592 parameters against 6,553,600 at 512 channels — 28% fewer — and a non-linearity in between that the single large kernel does not have. Three stacked 3×3s reach 7×7 the same way, at 45% fewer parameters than one 7×7. More depth, more non-linearity and fewer weights, all from refusing to use a big kernel.",
        "Where VGG is expensive is the part it inherited rather than designed. Of its 138,357,544 parameters, the dense layers hold 123,642,856 — 89.4% — and fc6 alone is 102.8M, because flattening a 7×7×512 map into 25,088 features and connecting them to 4,096 units is the most expensive thing you can do with a feature map. Compute is the mirror image: about 15.5 GMACs per image, 99.2% of it in the convolutions. VGG is a small, extremely slow feature extractor wearing a huge, nearly free classifier — which is exactly the trade ResNet went on to fix, and which GoogLeNet — developed in parallel — avoided.",
    ),
    steps = listOf(
        StepCard(1, "Fix the Kernel at 3×3", "The smallest kernel with a notion of left/right and up/down.", 0xFF10B981),
        StepCard(2, "Pad Every Layer", "p = 1 keeps the map, so depth costs nothing spatially.", 0xFF06B6D4),
        StepCard(3, "Halve, Then Double", "Pool 2×2; double the channels. Cost per layer stays roughly flat.", 0xFF6366F1),
        StepCard(4, "Stack for Receptive Field", "Two 3×3s = one 5×5, 28% fewer weights and an extra ReLU.", 0xFF8B5CF6),
        StepCard(5, "Repeat to 13 Conv Layers", "Only depth varies across the paper's A–E configurations.", 0xFFF59E0B),
        StepCard(6, "Pay for the Dense Head", "fc6 is 102.8M parameters — 74% of the whole network.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Two 3×3 vs one 5×5", "2·(9C²) = 18C² vs 25C²", "28% fewer parameters for the same receptive field."),
        FormulaEntry("Three 3×3 vs one 7×7", "27C² vs 49C²", "45% fewer, and three non-linearities instead of one."),
        FormulaEntry("Total parameters", "138,357,544", "Conv 14,714,688 · dense 123,642,856."),
        FormulaEntry("fc6", "4096 × (25088 + 1) = 102,764,544", "One layer, 74% of the model."),
        FormulaEntry("Compute", "≈15.5 GMACs per image", "99.2% of it in the thirteen convolutions."),
        FormulaEntry("Channel schedule", "64 → 128 → 256 → 512 → 512", "Doubling after each halving keeps per-layer cost roughly constant."),
    ),
    notationKey = listOf(
        NotationEntry("VGG-16 / VGG-19", "16 or 19 weight layers; the pooling layers are not counted"),
        NotationEntry("configuration D / E", "the paper's names for VGG-16 and VGG-19"),
        NotationEntry("effective receptive field", "5×5 from two stacked 3×3s, 7×7 from three"),
        NotationEntry("perceptual loss", "the still-standard use of a frozen VGG's features to compare two images"),
        NotationEntry("FLOPs vs parameters", "VGG is the clearest case of the two disagreeing completely"),
        NotationEntry("pre-initialisation", "the paper trained the shallow config first and used it to seed the deeper ones"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building it from a channel schedule, and the split that results",
            accentColor = 0xFF10B981,
            code = """
                import torch.nn as nn

                cfg = [64, 64, 'M', 128, 128, 'M', 256, 256, 256, 'M',
                       512, 512, 512, 'M', 512, 512, 512, 'M']

                layers, c_in = [], 3
                for v in cfg:
                    if v == 'M':
                        layers += [nn.MaxPool2d(2, 2)]
                    else:
                        layers += [nn.Conv2d(c_in, v, 3, padding=1), nn.ReLU(inplace=True)]
                        c_in = v
                features = nn.Sequential(*layers)
                classifier = nn.Sequential(
                    nn.Flatten(), nn.Linear(512 * 7 * 7, 4096), nn.ReLU(), nn.Dropout(),
                    nn.Linear(4096, 4096), nn.ReLU(), nn.Dropout(), nn.Linear(4096, 1000),
                )

                conv = sum(p.numel() for p in features.parameters())
                dense = sum(p.numel() for p in classifier.parameters())
                print(conv, dense)                  # 14714688 123642856
                print(dense / (conv + dense))       # 0.894
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The small-kernel argument, priced",
            accentColor = 0xFF8B5CF6,
            code = """
                C = 512
                two_3x3 = 2 * C * C * 3 * 3      # 4,718,592
                one_5x5 = C * C * 5 * 5          # 6,553,600
                print(two_3x3 / one_5x5)         # 0.72 -- 28% fewer parameters

                three_3x3 = 3 * C * C * 3 * 3    # 7,077,888
                one_7x7 = C * C * 7 * 7          # 12,845,056
                print(three_3x3 / one_7x7)       # 0.55 -- 45% fewer

                # Both stacks see the same input region as the single large kernel. The stack also
                # has two or three ReLUs inside it rather than one, so it is a strictly more
                # expressive function with strictly fewer parameters. After this paper, kernels
                # larger than 3x3 essentially disappeared from the middle of networks -- they
                # survive only in stems (ResNet's 7x7) and in depthwise layers, where the cost
                # argument runs differently.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "ImageNet Classification", "Runner-up in ILSVRC 2014 and the most reproduced backbone of its era."),
        ApplicationCard("flask", 0xFF06B6D4, "Perceptual Loss", "Style transfer, super-resolution and GAN evaluation still compare images in a frozen VGG's feature space."),
        ApplicationCard("search", 0xFF8B5CF6, "Detection Backbones", "Faster R-CNN and SSD were first built on VGG-16 before ResNet displaced it."),
        ApplicationCard("book", 0xFFF59E0B, "Teaching Baseline", "Its uniformity makes it the clearest architecture to reason about parameter and FLOP budgets with."),
    ),
    takeaways = listOf(
        "One rule everywhere: 3×3 stride 1 padded, 2×2 pooling, channels doubling after each pool.",
        "Two stacked 3×3s match a 5×5's receptive field with 28% fewer parameters and an extra non-linearity.",
        "138.4M parameters, 89.4% of them in the dense head — fc6 alone is 102.8M.",
        "Compute is the mirror image: ~15.5 GMACs, 99.2% in the convolutions. Parameters and FLOPs are separate budgets.",
        "GoogLeNet, developed in parallel, avoided exactly this cost with 1×1 bottlenecks and a global-average-pool head in place of fc6.",
    ),
    crossLinks = listOf(
        CrossLink("alexnet", "AlexNet"),
        CrossLink("inception", "Inception"),
        CrossLink("resnet", "ResNet"),
        CrossLink("conv_layers", "Convolution Layers"),
    ),
)
