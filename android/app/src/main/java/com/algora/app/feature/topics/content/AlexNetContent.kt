package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val alexNetContent = TopicContent(
    topicId = "alexnet",
    whatIsIt = listOf(
        "AlexNet won ImageNet 2012 with 15.3% top-5 error against the runner-up's 26.2% — a margin of nearly eleven points in a competition where a point was a good year — and restarted the entire field. Structurally it is LeNet made deep and wide: five convolutions, three max pools, three fully connected layers, on 227×227 colour input. Nothing in the layout was new. What was new was the training recipe and the hardware to run it.",
        "Four choices did the work. ReLU instead of tanh, which the paper reports trained roughly six times faster to the same error and made the depth trainable at all. Dropout at p = 0.5 on the two large dense layers, without which the model memorised the training set. Aggressive data augmentation — random crops, flips and PCA-based colour jitter — which multiplied the effective dataset. And two GTX 580 GPUs with the network split across them, because 62 million parameters did not fit in 3 GB.",
        "The parameter split is the thing to read, and the simulation prices it exactly: 62,378,344 parameters in total, of which the three dense layers hold 58,631,144 — 94.0% — while the five convolutions hold 3,747,200. Compute runs the other way: about 1.14 billion multiply-accumulates per image, roughly 95% of them in the convolutions. Almost the whole *model* is a classifier bolted onto a small feature extractor, and almost all the *work* is in the extractor. Every architecture that followed attacked one side or the other of that split — VGG deepened the extractor, GoogLeNet deleted the dense head.",
    ),
    steps = listOf(
        StepCard(1, "conv1 · 11×11 stride 4", "227 → 55×55×96. A huge kernel and a huge stride, to get the map down fast.", 0xFF10B981),
        StepCard(2, "conv2 · 5×5, 256 filters", "After a 3×3/2 pool: 27×27×256, 614,656 parameters.", 0xFF06B6D4),
        StepCard(3, "conv3–5 · 3×3 Stack", "384, 384, 256 filters at 13×13 with no pooling between them.", 0xFF6366F1),
        StepCard(4, "Flatten 6×6×256", "9,216 features into the dense head — the expensive moment.", 0xFF8B5CF6),
        StepCard(5, "fc6, fc7 · 4,096 Each", "37.75M + 16.78M parameters, both with dropout at 0.5.", 0xFFF59E0B),
        StepCard(6, "fc8 · 1,000 Classes", "62.4M parameters total, 94% of them in these three layers.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("conv1 output", "⌊(227 − 11)/4⌋ + 1 = 55", "The stride-4 stem: one layer takes 227 down to 55."),
        FormulaEntry("fc6", "4096 × (9216 + 1) = 37,752,832", "60.5% of the network in one layer."),
        FormulaEntry("Total parameters", "62,378,344", "Conv 3,747,200 · dense 58,631,144."),
        FormulaEntry("Parameter split", "94.0% dense / 6.0% conv", "Compute splits the other way: ~95% conv."),
        FormulaEntry("ReLU", "max(0, z)", "Non-saturating; the paper's measured ~6× training speedup."),
        FormulaEntry("Local response norm", "bᵢ = aᵢ / (k + α Σ a²)^β", "AlexNet's cross-channel normalisation, superseded by batch norm."),
    ),
    notationKey = listOf(
        NotationEntry("top-5 error", "the fraction of images whose true class is not in the model's five best guesses"),
        NotationEntry("ILSVRC", "the ImageNet challenge: 1.2M training images, 1,000 classes"),
        NotationEntry("grouped convolution", "the two-GPU split, revived later by ResNeXt as a design choice"),
        NotationEntry("LRN", "local response normalisation; dropped by later architectures for batch norm"),
        NotationEntry("PCA colour augmentation", "jittering along the principal components of RGB across the dataset"),
        NotationEntry("dropout", "the regulariser without which the dense head memorised the training set"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The network, and where its parameters actually are",
            accentColor = 0xFF10B981,
            code = """
                import torch, torch.nn as nn

                features = nn.Sequential(
                    nn.Conv2d(3, 96, 11, stride=4), nn.ReLU(), nn.MaxPool2d(3, 2),
                    nn.Conv2d(96, 256, 5, padding=2), nn.ReLU(), nn.MaxPool2d(3, 2),
                    nn.Conv2d(256, 384, 3, padding=1), nn.ReLU(),
                    nn.Conv2d(384, 384, 3, padding=1), nn.ReLU(),
                    nn.Conv2d(384, 256, 3, padding=1), nn.ReLU(), nn.MaxPool2d(3, 2),
                )
                classifier = nn.Sequential(
                    nn.Dropout(0.5), nn.Linear(256 * 6 * 6, 4096), nn.ReLU(),
                    nn.Dropout(0.5), nn.Linear(4096, 4096), nn.ReLU(),
                    nn.Linear(4096, 1000),
                )

                conv = sum(p.numel() for p in features.parameters())
                dense = sum(p.numel() for p in classifier.parameters())
                print(conv, dense, conv + dense)        # 3747200 58631144 62378344
                print(dense / (conv + dense))           # 0.9399...
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The recipe, which is what actually won",
            accentColor = 0xFFF59E0B,
            code = """
                from torchvision import transforms

                train_tf = transforms.Compose([
                    transforms.RandomResizedCrop(227),      # the paper's random 224 crops of a 256 image
                    transforms.RandomHorizontalFlip(),      # ~2048x more distinct training inputs, free
                    transforms.ColorJitter(0.4, 0.4, 0.4),  # stands in for the paper's PCA colour jitter
                    transforms.ToTensor(),
                ])

                # SGD, momentum 0.9, weight decay 5e-4, lr 0.01 divided by 10 when validation stalled.
                # Six days on two GTX 580s.
                #
                # Worth separating the two claims people make about 2012. AlexNet was not the first
                # convnet, nor the first to use ReLU or dropout. It was the first demonstration that
                # the combination -- a deep convnet, a large labelled dataset, GPU training and
                # heavy regularisation -- beat hand-engineered features by a margin no one could
                # argue with. That is why the date matters more than the architecture.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "ImageNet Classification", "The 2012 result that made deep learning the default approach to vision."),
        ApplicationCard("history", 0xFF06B6D4, "Transfer Learning's Origin", "Its conv features transferred to detection and retrieval tasks it was never trained for, which established the pretrain-then-finetune pattern."),
        ApplicationCard("chip", 0xFF8B5CF6, "GPU Deep Learning", "The first widely reproduced result that treated the GPU as the training platform rather than an accelerator."),
        ApplicationCard("bulb", 0xFFF59E0B, "The Regularisation Playbook", "Dropout plus heavy augmentation on a high-capacity model is still the standard recipe."),
    ),
    takeaways = listOf(
        "It cut ImageNet top-5 error from 26.2% to 15.3% — the margin, not the architecture, is why 2012 is a landmark.",
        "62,378,344 parameters, 94% of them in three dense layers; ~95% of the compute is in the five convolutions.",
        "ReLU is the change that made the depth trainable, and the paper measures roughly a 6× speedup over tanh.",
        "Dropout at 0.5 on fc6 and fc7 was load-bearing, not cosmetic — without it the head memorised the training set.",
        "The 11×11 stride-4 stem is the last time anyone used a kernel that large; VGG replaced it with stacked 3×3s.",
    ),
    crossLinks = listOf(
        CrossLink("lenet5", "LeNet-5"),
        CrossLink("vgg", "VGG-16 / VGG-19"),
        CrossLink("relu", "ReLU"),
        CrossLink("dropout", "Dropout"),
    ),
)
