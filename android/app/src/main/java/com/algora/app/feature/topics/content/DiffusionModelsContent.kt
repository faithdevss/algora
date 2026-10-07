package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val diffusionModelsContent = TopicContent(
    topicId = "diffusion_models",
    figure = Figure(
        caption = "The forward process of the page's lab: T = 1,000 steps with β rising linearly from " +
            "0.0001 to 0.02, and how much of the original data and how much noise make up a sample " +
            "at each step, xₜ = √ᾱ·x₀ + √(1 − ᾱ)·ε. For the first hundred steps the signal barely " +
            "fades — 0.95 at t = 100, and the ring of 24 points is still plain to see. Signal and " +
            "noise cross near t = 250 (0.72 against 0.69); by t = 500 the signal is 0.28 and the ring " +
            "is gone; at t = 1,000 it is 0.01 and the samples are indistinguishable from pure noise. " +
            "None of this is learned — it is a fixed formula, and any step is one jump from the data. " +
            "What the network learns is to predict the ε at a random step, and generation runs the " +
            "curves backwards, from noise to a sample.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "signal √ᾱ",
                    listOf(
                        FigurePoint(0.000f, 1.000f), FigurePoint(0.050f, 0.985f), FigurePoint(0.100f, 0.947f),
                        FigurePoint(0.150f, 0.888f), FigurePoint(0.200f, 0.812f), FigurePoint(0.250f, 0.724f),
                        FigurePoint(0.300f, 0.630f), FigurePoint(0.400f, 0.442f), FigurePoint(0.500f, 0.280f),
                        FigurePoint(0.600f, 0.161f), FigurePoint(0.700f, 0.083f), FigurePoint(0.800f, 0.039f),
                        FigurePoint(1.000f, 0.006f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "noise √(1 − ᾱ)",
                    listOf(
                        FigurePoint(0.000f, 0.000f), FigurePoint(0.050f, 0.170f), FigurePoint(0.100f, 0.321f),
                        FigurePoint(0.150f, 0.460f), FigurePoint(0.200f, 0.584f), FigurePoint(0.250f, 0.690f),
                        FigurePoint(0.300f, 0.777f), FigurePoint(0.400f, 0.897f), FigurePoint(0.500f, 0.960f),
                        FigurePoint(0.600f, 0.987f), FigurePoint(0.700f, 0.997f), FigurePoint(0.800f, 0.999f),
                        FigurePoint(1.000f, 1.000f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(0.1f, 0.947f, "t 100: 0.95"),
                FigurePoint(0.25f, 0.724f, "cross ≈ t 250", FigureTone.Muted),
                FigurePoint(0.5f, 0.280f, "t 500: 0.28"),
            ),
            xLabel = "step t, 0 → 1,000",
            yLabel = "share of the sample",
        ),
    ),
    whatIsIt = listOf(
        "A diffusion model learns to generate data by learning to undo noise. A fixed forward process gradually adds Gaussian noise to the data over T steps until nothing but noise is left; a network is trained to predict the noise that was added, and generation runs the process in reverse, starting from pure noise and removing a little at each step until a sample emerges.",
        "The lab shows the forward process on 24 points arranged on a ring, with T = 1,000 and β rising linearly from 0.0001 to 0.02. Any step can be reached in one jump, xₜ = √ᾱ·x₀ + √(1 − ᾱ)·ε. At t = 100, ᾱ is still 0.90 — signal 0.95, noise 0.32 — and the ring is clearly visible. At t = 250 signal and noise are about equal (0.72 against 0.69); by t = 500 the signal is down to 0.28 and the ring is gone; at t = 1,000 the samples are indistinguishable from N(0, I). No learning happens in any of this — it is a fixed formula.",
        "Training is equally simple: pick a random t, noise a data point in one jump, and teach the network to guess the ε that was added, with a plain squared-error loss. All the network ever learns is the shape of the data manifold, one noise level at a time. Sampling then needs many denoising steps — DDPM uses all 1,000, DDIM 50 or fewer — and doing those steps in a compressed latent space rather than on pixels is what made Stable Diffusion practical.",
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
        "In the lab the signal fraction √ᾱ is 0.95 at t = 100, 0.72 at t = 250, 0.28 at t = 500 and 0.01 at t = 1,000 — a fixed schedule, no learning.",
    ),
    crossLinks = listOf(
        CrossLink("gans", "GANs"),
        CrossLink("autoencoders", "Autoencoders"),
        CrossLink("cnn", "CNNs"),
    ),
)
