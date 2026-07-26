package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val autoencodersContent = TopicContent(
    topicId = "autoencoders",
    whatIsIt = listOf(
        "An autoencoder is a neural network trained to reconstruct its own input, forced through a narrow bottleneck that compels it to learn a compact representation.",
        "The encoder squeezes input into a low-dimensional latent code; the decoder rebuilds it — and the bottleneck is where the useful, compressed features live.",
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
    ),
    crossLinks = listOf(
        CrossLink("pca", "PCA"),
        CrossLink("gans", "GANs"),
    ),
)
