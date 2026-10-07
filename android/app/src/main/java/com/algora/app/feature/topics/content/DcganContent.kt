package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dcganContent = TopicContent(
    topicId = "dcgan",
    whatIsIt = listOf(
        "DCGAN is the paper that made GANs trainable, and its contribution was not a new loss — the objective is Goodfellow's, unchanged. It was a list of architectural rules: replace pooling with strided convolutions, use batch normalization in both networks, drop the fully connected hidden layers, use ReLU in the generator with tanh at the output, and LeakyReLU throughout the discriminator. Most of that list is empirical, arrived at by \"extensive model exploration\" and honestly labelled as such. One item on it is not empirical at all, and it is the one worth the arithmetic.",
        "A transposed convolution builds a larger output by writing each input value into a kernel-sized window. How many times a given output position gets written depends only on the kernel and the stride, and it is countable before any image exists. With kernel 4 and stride 2 — DCGAN's choice — every interior output position is written exactly twice: the coverage is uniform. With kernel 3 and stride 2 the counts alternate 1, 2, 1, 2 forever. With kernel 5 and stride 2 they alternate 2, 3. With kernel 4 and stride 3 they run 1, 1, 2. The pattern is exact: coverage is uniform precisely when the stride divides the kernel. That periodic unevenness, compounded across four upsampling layers, is the checkerboard artefact, and it is a property of two integers rather than of the data or the training.",
        "The parameter table is worth reading before choosing what to change, because it is lopsided in a way the architecture diagram hides. The generator is 12,672,771 parameters taking a 100-dimensional z to 64×64×3. The dense projection at the front — the one thing DCGAN's own rules tell you to be suspicious of — is 1,654,784 of them, 13%. The single transposed convolution from 1024 to 512 channels at 8×8 (with its BatchNorm) is 8,389,632, which is 66% of the entire generator. Two thirds of the model sits in one layer at the second-lowest resolution, and the final layer that actually produces the pixels is 6,147 parameters, under 0.05%. Capacity in a generator lives where the channels are, not where the pixels are.",
    ),
    steps = listOf(
        StepCard(1, "Project z Into a Small Grid", "100 numbers to 4×4×1024 — the only dense layer.", 0xFF3B82F6),
        StepCard(2, "Upsample by Transposed Convolution", "Not unpooling: a learned kernel writing into a larger grid.", 0xFF10B981),
        StepCard(3, "Count the Writes Per Output", "Kernel 4, stride 2 gives exactly 2 everywhere.", 0xFFF59E0B),
        StepCard(4, "Check the Divisibility", "kernel % stride ≠ 0 makes the counts alternate — that is the checkerboard.", 0xFFEC4899),
        StepCard(5, "Halve the Channels Each Step", "1024 → 512 → 256 → 128 → 3, doubling resolution each time.", 0xFF8B5CF6),
        StepCard(6, "Tanh at the Output", "Images scaled to [−1, 1], which is what the discriminator expects.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Output size", "O = (I − 1)·s + k − 2p", "Transposed convolution, one dimension."),
        FormulaEntry("Uniform coverage", "k mod s = 0", "The exact condition — not a rule of thumb."),
        FormulaEntry("k=4, s=2", "2, 2, 2, 2, …", "DCGAN's choice. Uniform."),
        FormulaEntry("k=3, s=2", "1, 2, 1, 2, …", "Checkerboard, before any training."),
        FormulaEntry("k=4, s=3", "1, 1, 2, 1, 1, 2, …", "Period 3 — same failure, longer stripe."),
        FormulaEntry("Generator", "12,672,771 parameters", "66% of it in the 1024 → 512 layer alone."),
    ),
    notationKey = listOf(
        NotationEntry("transposed convolution", "each input written into a k-sized output window; not an inverse convolution"),
        NotationEntry("k, s, p", "kernel size, stride, padding"),
        NotationEntry("coverage", "how many input taps write to a given output position"),
        NotationEntry("checkerboard artefact", "the periodic intensity pattern uneven coverage produces"),
        NotationEntry("LeakyReLU", "the discriminator's activation; gradient survives on the negative side"),
        NotationEntry("z", "the 100-dimensional noise vector the generator starts from"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Coverage, counted rather than assumed",
            accentColor = 0xFFF59E0B,
            code = """
                def coverage(kernel, stride, input_length=16):
                    out = (input_length - 1) * stride + kernel
                    counts = [0] * out
                    for i in range(input_length):
                        for k in range(kernel):
                            counts[i * stride + k] += 1
                    return counts[kernel:-kernel]        # drop the edge ramp

                for k, s in [(4, 2), (3, 2), (5, 2), (6, 2), (6, 3), (4, 3)]:
                    c = coverage(k, s)
                    print(k, s, sorted(set(c)), k % s == 0)

                # 4 2 [2]     True     <- DCGAN. uniform
                # 3 2 [1, 2]  False    <- alternating: checkerboard
                # 5 2 [2, 3]  False
                # 6 2 [3]     True     <- also fine, just more expensive
                # 6 3 [2]     True
                # 4 3 [1, 2]  False    <- period 3
                #
                # The set has one element exactly when the stride divides the kernel. Nothing about
                # the data or the loss enters this -- it is arithmetic on two integers, and it is
                # decided when you type the layer.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The generator, and where its parameters actually sit",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch.nn as nn

                def block(cin, cout):
                    return nn.Sequential(
                        nn.ConvTranspose2d(cin, cout, 4, stride=2, padding=1, bias=False),
                        nn.BatchNorm2d(cout), nn.ReLU(True),
                    )

                generator = nn.Sequential(
                    nn.Linear(100, 4 * 4 * 1024),      #  1,654,784   13.1%
                    nn.Unflatten(1, (1024, 4, 4)),
                    block(1024, 512),                  #  8,389,632   66.2%   <- two thirds, one layer
                    block(512, 256),                   #  2,097,664   16.6%
                    block(256, 128),                   #    524,544    4.1%
                    nn.ConvTranspose2d(128, 3, 4, 2, 1),  #   6,147    0.05%  <- makes the pixels
                    nn.Tanh(),
                )                                      # 12,672,771 total

                # Capacity is where the channels are. Widening the last layer to "get sharper
                # images" adds almost nothing; the 8x8 block is where the model actually is.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFF3B82F6, "Image Synthesis", "The template every convolutional generator since has started from."),
        ApplicationCard("chip", 0xFF10B981, "Representation Learning", "The discriminator's features transfer — DCGAN's other published result."),
        ApplicationCard("flask", 0xFFF59E0B, "Latent Arithmetic", "Vector offsets in z produce consistent semantic edits."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Uneven coverage is unfixable by training; change the kernel instead."),
    ),
    takeaways = listOf(
        "DCGAN changed the architecture, not the loss — the objective is Goodfellow's original.",
        "Coverage is uniform exactly when the stride divides the kernel; k=4, s=2 gives 2 writes everywhere.",
        "k=3, s=2 alternates 1, 2 and k=4, s=3 runs 1, 1, 2 — checkerboard artefacts, decided at layer-definition time.",
        "No amount of training removes it, because it is a property of two integers rather than of the weights.",
        "The generator is 12,672,771 parameters from a 100-dimensional z to 64×64×3.",
        "66% of that sits in the single 1024 → 512 layer at 8×8 resolution.",
        "The final layer, the one that emits the pixels, is 6,147 parameters — under 0.05%.",
    ),
    crossLinks = listOf(
        CrossLink("gans", "GANs"),
        CrossLink("conv_layers", "Convolution Layers"),
        CrossLink("padding_strides", "Padding & Strides"),
        CrossLink("batch_normalization", "Batch Normalization"),
        CrossLink("leaky_relu", "Leaky ReLU"),
    ),
)
