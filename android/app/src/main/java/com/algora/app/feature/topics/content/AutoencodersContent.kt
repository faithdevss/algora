package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val autoencodersContent = TopicContent(
    topicId = "autoencoders",
    figure = Figure(
        caption = "The page's lab: six numbers squeezed through a code of only two and rebuilt. The " +
            "encoder turns the input into z = [1.22, 0.72] — roughly the overall level and the " +
            "downward slope — and the decoder brings back the second row from those two numbers " +
            "alone, every value within 0.04 of the first, a mean squared error of 0.0010. What two " +
            "numbers cannot hold is lost: the input is not quite a straight ramp, so the fifth value " +
            "comes back as 0.24 instead of 0.20. The same bottleneck is what makes it robust: nudge " +
            "every input by up to 0.14 and the code barely moves, to [1.21, 0.75], because noise that " +
            "does not look like a level or a slope has nowhere to go in two numbers.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.90", "0.80", "0.60", "0.40", "0.20", "0.10"),
                listOf("0.93", "0.76", "0.59", "0.41", "0.24", "0.07"),
            ),
            rowHeaders = listOf("input x", "rebuilt x̂"),
            colHeaders = listOf("1", "2", "3", "4", "5", "6"),
            marks = listOf(
                FigureCell(1, 4, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "An autoencoder is a network trained to reproduce its own input through a narrow middle layer. The encoder squeezes the input into a short code and the decoder rebuilds the input from that code; the loss is simply how far the reconstruction is from the original. Nothing else forces compression — only the shape of the network — so the code has to keep the structure of the data and drop everything else.",
        "The lab pushes six numbers, [0.90, 0.80, 0.60, 0.40, 0.20, 0.10], through a code of only two. The encoder produces z = [1.22, 0.72], where the first number tracks the overall level and the second the downward slope, and the decoder brings back [0.93, 0.76, 0.59, 0.41, 0.24, 0.07] — every value within 0.04, a mean squared error of 0.0010. What two numbers cannot hold is lost: the input is not quite a straight ramp, so the fifth value comes back as 0.24 instead of 0.20.",
        "The same bottleneck makes it a denoiser. Corrupt the input by up to 0.14 per value and the code barely moves — [1.21, 0.75] against the clean [1.22, 0.72] — because noise that does not line up with a level or a slope has nowhere to go in two numbers. Train it to reconstruct the clean input from the noisy one and you have a denoising autoencoder; make the code a distribution and you have a VAE, which can also generate new samples.",
    ),
    steps = listOf(
        StepCard(1, "Encode", "Map the input down to a small latent vector z.", 0xFF818CF8),
        StepCard(2, "Bottleneck", "The narrow latent layer forces the network to keep only essential information.", 0xFF60A5FA),
        StepCard(3, "Decode", "Reconstruct the original input from z.", 0xFF10B981),
        StepCard(4, "Minimize Reconstruction Error", "Train end-to-end to make the output match the input.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min ‖x − decode(encode(x))‖²", "Reconstruction loss."),
        FormulaEntry("Bottleneck", "dim(z) ≪ dim(x)", "Undercomplete latent forces compression."),
        FormulaEntry("VAE twist", "latent = distribution", "Variational autoencoders make z generative."),
    ),
    notationKey = listOf(
        NotationEntry("z", "latent code — the compressed representation"),
        NotationEntry("encoder / decoder", "compression and reconstruction halves"),
        NotationEntry("reconstruction loss", "difference between input and output"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A simple autoencoder (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn as nn

                encoder = nn.Sequential(nn.Linear(784, 64), nn.ReLU(), nn.Linear(64, 16))
                decoder = nn.Sequential(nn.Linear(16, 64), nn.ReLU(), nn.Linear(64, 784))

                z = encoder(x)
                x_hat = decoder(z)   # trained to match x
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF818CF8, "Dimensionality Reduction", "A non-linear alternative to PCA for compressing and visualizing data."),
        ApplicationCard("target", 0xFF60A5FA, "Anomaly Detection", "Inputs that reconstruct poorly are flagged as anomalies."),
        ApplicationCard("image", 0xFF10B981, "Denoising & Generation", "Denoising autoencoders clean corrupted inputs; VAEs generate new samples."),
    ),
    takeaways = listOf(
        "Autoencoders learn compressed representations by reconstructing their input through a bottleneck.",
        "The latent code captures the data's essential structure — a non-linear PCA.",
        "High reconstruction error signals anomalies.",
        "Variational autoencoders turn the latent space generative, a bridge to GANs.",
        "In the lab six values squeezed through a two-number code come back within 0.04 (MSE 0.0010), and noise of up to 0.14 barely moves the code.",
    ),
    crossLinks = listOf(
        CrossLink("pca", "PCA"),
        CrossLink("gans", "GANs"),
    ),
)
