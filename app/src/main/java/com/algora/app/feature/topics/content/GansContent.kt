package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureLayer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gansContent = TopicContent(
    topicId = "gans",
    figure = Figure(
        caption = "The generator never sees the training data. Its only signal is the gradient that " +
            "comes back through the discriminator, which is why the two have to improve together — a " +
            "discriminator that wins too easily returns almost nothing to learn from, and one that " +
            "cannot tell fakes apart returns nothing worth learning. The instability everyone reports " +
            "is this dependency, not a bad hyperparameter.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("z", "noise"),
                FigureLayer("generator", "never sees real data", FigureTone.Primary),
                FigureLayer("discriminator", "fake beside real", FigureTone.Warn),
                FigureLayer("real or fake", "one verdict", FigureTone.Accent),
            ),
            horizontal = true,
            backwardLabel = "the generator's only gradient",
        ),
    ),
    whatIsIt = listOf(
        "A generative adversarial network (GAN) pits two networks against each other: a generator that fabricates fake samples and a discriminator that tries to tell fakes from real data.",
        "They train in a minimax game — as the discriminator improves, the generator is pushed to produce ever more realistic outputs, until fakes are indistinguishable from real.",
    ),
    steps = listOf(
        StepCard(1, "Generator Fakes", "Turn random noise z into a synthetic sample.", 0xFF818CF8),
        StepCard(2, "Discriminator Judges", "Score samples as real (from data) or fake (from the generator).", 0xFF60A5FA),
        StepCard(3, "Adversarial Loss", "The generator is rewarded for fooling the discriminator; the discriminator for catching it.", 0xFF10B981),
        StepCard(4, "Reach Equilibrium", "At the ideal point the generator's samples match the true data distribution.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Minimax game", "min_G max_D E[log D(x)] + E[log(1 − D(G(z)))]", "Two-player adversarial objective."),
        FormulaEntry("Generator goal", "maximize D(G(z))", "Make fakes the discriminator accepts."),
        FormulaEntry("Failure mode", "mode collapse", "Generator outputs little variety."),
    ),
    notationKey = listOf(
        NotationEntry("G, D", "generator and discriminator networks"),
        NotationEntry("z", "random noise input to the generator"),
        NotationEntry("mode collapse", "generator producing near-identical samples"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "GAN training step (PyTorch sketch)",
            accentColor = 0xFF6366F1,
            code = """
                # Update discriminator: real should score 1, fakes 0.
                d_loss = bce(D(real), ones) + bce(D(G(z).detach()), zeros)
                d_loss.backward(); opt_D.step()

                # Update generator: fakes should fool D into scoring 1.
                g_loss = bce(D(G(z)), ones)
                g_loss.backward(); opt_G.step()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("image", 0xFF818CF8, "Image Synthesis", "Photorealistic faces, art, and textures from StyleGAN-style generators."),
        ApplicationCard("flask", 0xFF60A5FA, "Data Augmentation", "Generating synthetic training data where real samples are scarce."),
        ApplicationCard("robot", 0xFF10B981, "Super-Resolution & Translation", "Upscaling images and translating between domains (sketch → photo)."),
    ),
    takeaways = listOf(
        "GANs train a generator and discriminator in an adversarial minimax game.",
        "Competition drives the generator toward the real data distribution.",
        "They're notoriously unstable — mode collapse and non-convergence are common.",
        "Diffusion models have largely overtaken GANs for high-fidelity generation.",
    ),
    crossLinks = listOf(
        CrossLink("autoencoders", "Autoencoders"),
        CrossLink("neural_network_basics", "Neural Network Basics"),
    ),
)
