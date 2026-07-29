package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lenet5Content = TopicContent(
    topicId = "lenet5",
    whatIsIt = listOf(
        "LeNet-5 is the 1998 network that established the shape of every convolutional model since: alternating convolution and subsampling layers that build features, then a small dense classifier on top. It reads a 32×32 greyscale digit through two conv/pool pairs, a third convolution that collapses the map to 1×1×120, and two dense layers to ten outputs. LeCun and colleagues put it into production reading cheques, where it processed a large share of all cheques in the United States.",
        "Its size is the fact worth carrying: 61,706 parameters, summed layer by layer in the simulation rather than quoted — 156 in C1, 2,416 in C3, 48,120 in C5, 10,164 in F6 and 850 at the output. Nearly four fifths of the model is that one 5×5×16→120 convolution, and the whole thing runs in about 416,000 multiply-accumulates per digit. A modern phone does that in microseconds; in 1998 it justified custom hardware.",
        "What LeNet lacked was not architecture but scale, and the fourteen years to AlexNet are the story of that gap closing. It used tanh rather than ReLU, average-style subsampling with learned coefficients rather than max pooling, and a hand-designed sparse connectivity table between C1 and C3 — the modern reading, and the one the 61,706 figure comes from, is fully connected. Trained on 60,000 images with a few megabytes of memory, it reached roughly 0.9% error on MNIST. The ideas were right; ImageNet and GPUs were missing.",
    ),
    steps = listOf(
        StepCard(1, "C1 · Six 5×5 Filters", "32×32 → 28×28×6, 156 parameters. Edge and stroke detectors.", 0xFF10B981),
        StepCard(2, "S2 · Subsample by 2", "28 → 14. No parameters in the modern reading; LeNet learned a scale and bias.", 0xFF06B6D4),
        StepCard(3, "C3 · Sixteen 5×5 Filters", "14 → 10, 2,416 parameters. The paper used a sparse connection table here.", 0xFF6366F1),
        StepCard(4, "S4 · Subsample Again", "10 → 5. The map is now small enough to collapse.", 0xFF8B5CF6),
        StepCard(5, "C5 · 5×5 → 120", "Output 1×1×120 and 48,120 parameters — 78% of the whole network.", 0xFFF59E0B),
        StepCard(6, "F6 and Output", "120 → 84 → 10. Total 61,706 parameters, 416k MACs per digit.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Conv parameters", "C_out(C_in·k² + 1)", "C1 = 6(1·25+1) = 156; C3 = 16(6·25+1) = 2,416."),
        FormulaEntry("C5", "120(16·25 + 1) = 48,120", "One layer, 78% of the model."),
        FormulaEntry("Dense parameters", "out(in + 1)", "F6 = 84·121 = 10,164; output = 10·85 = 850."),
        FormulaEntry("Total", "156 + 2,416 + 48,120 + 10,164 + 850 = 61,706", "Summed from the table, not quoted."),
        FormulaEntry("Activation", "tanh, not ReLU", "Saturating both ways — the depth ceiling of the era."),
        FormulaEntry("Output layer", "RBF units in the original", "Euclidean distance to a prototype per class; today, softmax."),
    ),
    notationKey = listOf(
        NotationEntry("C1, C3, C5", "convolution layers, numbered by position in the paper"),
        NotationEntry("S2, S4", "subsampling layers — average pooling with a learned scale and bias"),
        NotationEntry("F6", "the 84-unit fully connected layer, sized for a 7×12 character bitmap"),
        NotationEntry("connection table", "the paper's hand-designed sparse C1→C3 wiring, dropped in modern versions"),
        NotationEntry("MNIST", "the 60k handwritten-digit dataset this network defined the benchmark on"),
        NotationEntry("gradient-based learning", "the paper's actual title claim: features learned, not engineered"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole network, and its parameter count layer by layer",
            accentColor = 0xFF10B981,
            code = """
                import torch, torch.nn as nn

                lenet = nn.Sequential(
                    nn.Conv2d(1, 6, 5), nn.Tanh(), nn.AvgPool2d(2),      # 32 -> 28 -> 14
                    nn.Conv2d(6, 16, 5), nn.Tanh(), nn.AvgPool2d(2),     # 14 -> 10 -> 5
                    nn.Conv2d(16, 120, 5), nn.Tanh(),                    # 5 -> 1
                    nn.Flatten(),
                    nn.Linear(120, 84), nn.Tanh(),
                    nn.Linear(84, 10),
                )

                for name, p in lenet.named_parameters():
                    if 'weight' in name:
                        print(name, p.shape)

                total = sum(p.numel() for p in lenet.parameters())
                print(total)                                   # 61706
                print(48120 / total)                           # 0.78 -- C5 alone
                print(lenet(torch.randn(1, 1, 32, 32)).shape)  # [1, 10]
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why 32×32 for 28×28 digits, and what the padding is for",
            accentColor = 0xFF6366F1,
            code = """
                # MNIST digits are 28x28. LeNet takes 32x32, because two unpadded 5x5 convolutions
                # and two halvings only land on a whole number from 32:
                #   32 -5+1-> 28 -/2-> 14 -5+1-> 10 -/2-> 5 -5+1-> 1
                # From 28 the third convolution would face a 4x4 map and could not run at all.

                from torchvision import transforms
                tf = transforms.Compose([
                    transforms.Pad(2),          # 28 -> 32, and it centres the stroke in the field
                    transforms.ToTensor(),
                ])

                # The paper's own reason is the second one: padding puts the distinctive parts of a
                # digit (stroke ends, corners) near the centre of the highest-level receptive fields
                # rather than at the border, where the unpadded convolutions read them least often.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Cheque Reading", "Deployed by NCR and others through the late 1990s and 2000s, reading a large fraction of US cheques."),
        ApplicationCard("book", 0xFF06B6D4, "Document OCR", "Postal codes and form digitisation — the first commercially significant neural vision systems."),
        ApplicationCard("bulb", 0xFF8B5CF6, "The Reference Architecture", "Conv → pool → conv → pool → dense is still the first thing anyone builds when learning the subject."),
        ApplicationCard("chip", 0xFFF59E0B, "Embedded Vision", "At 62k parameters it still fits comfortably on a microcontroller, which keeps it in use for tiny classifiers."),
    ),
    takeaways = listOf(
        "61,706 parameters, and 78% of them sit in the single C5 convolution.",
        "The template — alternating convolution and subsampling, then a dense classifier — has not changed since 1998.",
        "It used tanh and average-style subsampling; ReLU and max pooling arrived later as fixes to what limited it.",
        "The 32×32 input is a geometry requirement: three unpadded 5×5 layers with two halvings only work out from 32.",
        "What separated it from AlexNet was data and compute, not ideas — which is the honest lesson of the fourteen-year gap.",
    ),
    crossLinks = listOf(
        CrossLink("conv_layers", "Convolution Layers"),
        CrossLink("pooling_layers", "Pooling Layers"),
        CrossLink("alexnet", "AlexNet"),
        CrossLink("tanh", "Tanh"),
    ),
)
