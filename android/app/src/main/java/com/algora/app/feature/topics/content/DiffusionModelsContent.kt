package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val diffusionModelsContent = TopicContent(
    topicId = "diffusion_models",
    whatIsIt = listOf(
        "A diffusion model learns to generate data by reversing a corruption process: noise is added to real samples in small steps until nothing remains, and a network is trained to undo one step at a time.",
        "Training is deceptively simple — take an image, pick a random timestep, add the corresponding amount of Gaussian noise, and ask the network to predict the noise it added. Sampling then starts from pure noise and walks the learned reverse process back to a clean image. Unlike a GAN there is no adversary, so training is stable.",
    ),
    steps = listOf(
        StepCard(1, "Define the Forward Noising", "A fixed schedule β₁…β_T adds a little Gaussian noise per step; no learning is involved.", 0xFF8B5CF6),
        StepCard(2, "Jump to Any Timestep", "Closed form: x_t = √ᾱ_t·x₀ + √(1−ᾱ_t)·ε, so training samples t at random.", 0xFF3B82F6),
        StepCard(3, "Predict the Noise", "A U-Net conditioned on t outputs ε̂; the loss is plain MSE against the ε actually used.", 0xFFF59E0B),
        StepCard(4, "Denoise Step by Step", "Sampling starts at x_T ~ N(0, I) and subtracts the predicted noise T times.", 0xFF10B981),
        StepCard(5, "Steer the Generation", "Classifier-free guidance blends conditional and unconditional predictions to push samples toward a prompt.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Forward step", "q(x_t | x_{t−1}) = N(√(1−β_t)·x_{t−1}, β_t·I)", "Fixed, learning-free corruption."),
        FormulaEntry("Closed form", "x_t = √ᾱ_t·x₀ + √(1−ᾱ_t)·ε,  ᾱ_t = Π(1−β_s)", "Any timestep in one shot — this is what makes training cheap."),
        FormulaEntry("Training loss", "L = E_{t,x₀,ε} ‖ε − ε_θ(x_t, t)‖²", "Predict the noise, nothing more."),
        FormulaEntry("Guidance", "ε̂ = ε_uncond + w·(ε_cond − ε_uncond)", "w > 1 trades diversity for prompt fidelity."),
    ),
    notationKey = listOf(
        NotationEntry("x₀ / x_T", "clean sample / pure noise"),
        NotationEntry("β_t", "variance added at step t — the noise schedule"),
        NotationEntry("ᾱ_t", "cumulative product of (1 − β), the signal left at step t"),
        NotationEntry("ε_θ", "the network's prediction of the noise"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Training step and DDPM sampling loop",
            accentColor = 0xFF6366F1,
            code = """
                class Diffusion(private val steps: Int = 1000, private val net: NoisePredictor) {
                    // Linear beta schedule and its cumulative products.
                    private val beta = DoubleArray(steps) { 1e-4 + (0.02 - 1e-4) * it / (steps - 1) }
                    private val alpha = DoubleArray(steps) { 1 - beta[it] }
                    private val alphaBar = DoubleArray(steps).also {
                        var running = 1.0
                        for (t in 0 until steps) { running *= alpha[t]; it[t] = running }
                    }

                    // One training step: corrupt to a random t, ask the net for the noise back.
                    fun trainStep(x0: DoubleArray, lr: Double): Double {
                        val t = (0 until steps).random()
                        val noise = DoubleArray(x0.size) { gaussian() }
                        val xt = DoubleArray(x0.size) { i ->
                            Math.sqrt(alphaBar[t]) * x0[i] + Math.sqrt(1 - alphaBar[t]) * noise[i]
                        }
                        return net.trainStep(xt, t, target = noise, lr = lr)   // MSE against the true noise
                    }

                    // Sampling: start from pure noise and walk the reverse chain.
                    fun sample(size: Int): DoubleArray {
                        var x = DoubleArray(size) { gaussian() }
                        for (t in steps - 1 downTo 0) {
                            val predicted = net.predict(x, t)
                            val mean = DoubleArray(size) { i ->
                                (x[i] - (beta[t] / Math.sqrt(1 - alphaBar[t])) * predicted[i]) / Math.sqrt(alpha[t])
                            }
                            x = if (t == 0) mean
                            else DoubleArray(size) { i -> mean[i] + Math.sqrt(beta[t]) * gaussian() }
                        }
                        return x
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("image", 0xFF8B5CF6, "Text-to-Image", "Stable Diffusion, Imagen and DALL·E 2 all run this reverse process, mostly in a compressed latent space."),
        ApplicationCard("robot", 0xFF3B82F6, "Audio & Video", "The same objective generates speech and coherent video when the network is given the right structure."),
        ApplicationCard("chip", 0xFF10B981, "Scientific Design", "Protein and molecule generators sample structures from a learned denoiser."),
    ),
    takeaways = listOf(
        "The forward noising is fixed; only the reverse denoiser is learned.",
        "Predicting the added noise reduces the whole objective to an MSE regression.",
        "Sampling is expensive because it is iterative — DDIM and distillation cut the step count.",
        "Training is far more stable than a GAN's: no discriminator, no mode collapse.",
    ),
    crossLinks = listOf(
        CrossLink("gans", "GANs"),
        CrossLink("autoencoders", "Autoencoders"),
        CrossLink("cnn", "CNNs"),
    ),
)
