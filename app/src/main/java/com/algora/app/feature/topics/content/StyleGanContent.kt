package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val styleGanContent = TopicContent(
    topicId = "stylegan",
    whatIsIt = listOf(
        "StyleGAN rebuilt the generator around a question earlier GANs never asked: where does the latent code enter? In DCGAN it enters once, at the front, and everything downstream is a consequence of that single vector. StyleGAN feeds it in everywhere instead. The synthesis network starts from a learned constant — the noise vector is not its input at all — and the latent is injected at every resolution through adaptive instance normalization. A 1024×1024 model has nine resolutions from 4×4 up, two AdaIN operations at each, and so eighteen separate style inputs. Because they are separate, they can be driven by different latents: that is style mixing, and it is why coarse, middle and fine attributes can be taken from different faces.",
        "AdaIN itself is blunter than it looks, and it is worth stating as an equality rather than an approximation. It normalizes the content feature to zero mean and unit variance per channel, then scales and shifts by the style's mean and standard deviation. Run it on a content vector with mean 1.333 and standard deviation 3.436 and a style with mean 0.233 and standard deviation 0.0986, and the output's statistics are 0.233 and 0.0986 — the style's, to the last digit floating point carries. Not close to. The content's own per-channel statistics are destroyed and replaced, exactly, every time. What survives is the *pattern* within the channel — which positions are relatively high and low — and that is the entire division of labour the architecture rests on.",
        "The mapping network is the part usually described with adjectives, so it is worth measuring instead. Its argument is that forcing a fixed prior onto a data distribution with a missing combination requires a warped map, and the warping is what entangles attributes. Build exactly that: a feature space whose top-right quadrant is unpopulated, because the training set never contains both attributes at once, and an area-correct map from the uniform square onto it. Interpolating linearly between two sampled points gives a mean squared path length of 2.469 through that map, against 0.305 for the same endpoints interpolated in the feature space directly — a factor of 8.1. An intermediate latent W that need not be uniform can be far closer to the second number, which is what the eight-layer mapping network buys. The honest cost is small and real: a straight line in W leaves the populated region 3.1% of the time.",
    ),
    steps = listOf(
        StepCard(1, "Map z Into W First", "Eight fully connected layers, before synthesis begins.", 0xFF8B5CF6),
        StepCard(2, "Start From a Constant", "The synthesis network's input is learned, not sampled.", 0xFF0EA5E9),
        StepCard(3, "Inject Style at Every Resolution", "Two AdaIN operations per resolution — 18 for a 1024 model.", 0xFFF59E0B),
        StepCard(4, "AdaIN Replaces the Statistics", "Output mean and std are the style's exactly; content keeps only its pattern.", 0xFF10B981),
        StepCard(5, "Mix Styles Across Bands", "Coarse 4, middle 4, fine 10 — different latents into different bands.", 0xFFEC4899),
        StepCard(6, "Measure the Warping", "Path length 2.469 in Z against 0.305 in W: a factor of 8.1.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("AdaIN", "σ(y)·(x − μ(x))/σ(x) + μ(y)", "Per channel; substitution, not blending."),
        FormulaEntry("Measured", "std 3.436 → 0.0986", "The style's, to floating-point precision."),
        FormulaEntry("Style inputs", "9 resolutions × 2 = 18", "4×4 up to 1024×1024."),
        FormulaEntry("Bands", "coarse 4 · middle 4 · fine 10", "≤8, ≤32, and the rest."),
        FormulaEntry("Path length", "2.469 in Z · 0.305 in W", "Same endpoints, ratio 8.1."),
        FormulaEntry("The cost", "3.1% outside the support", "A straight line in W can leave the data."),
    ),
    notationKey = listOf(
        NotationEntry("Z", "the sampled latent space, with a fixed prior it cannot escape"),
        NotationEntry("W", "the intermediate latent, free to be non-uniform — the whole point"),
        NotationEntry("AdaIN", "adaptive instance normalization; replaces per-channel mean and std"),
        NotationEntry("style mixing", "driving different resolutions from different latents"),
        NotationEntry("path length", "how far the output travels under a straight latent walk; lower is more disentangled"),
        NotationEntry("truncation", "sampling W nearer its mean, trading variety for fidelity"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "AdaIN is a substitution, and the assertion holds exactly",
            accentColor = 0xFF10B981,
            code = """
                import torch

                def adain(content, style, eps=1e-8):
                    c_mean, c_std = content.mean(-1, keepdim=True), content.std(-1, unbiased=False, keepdim=True)
                    s_mean, s_std = style.mean(-1, keepdim=True), style.std(-1, unbiased=False, keepdim=True)
                    return s_std * (content - c_mean) / (c_std + eps) + s_mean

                content = torch.tensor([3.0, -1.0, 7.0, 2.5, 0.5, -4.0])
                style   = torch.tensor([0.2, 0.1, 0.4, 0.25, 0.15, 0.3])
                out = adain(content, style)

                # content  mean 1.3333  std 3.4359
                # style    mean 0.2333  std 0.0986
                # out      mean 0.2333  std 0.0986   <- the style's, exactly
                #
                # Nothing of the content's scale or offset survives. What survives is which
                # positions are relatively high and low, and that carries all the structure. The
                # style controls "how much and where centred"; the content controls "in what shape".
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why a fixed prior entangles, measured on a distribution with a hole",
            accentColor = 0xFF6366F1,
            code = """
                import random

                # Feature space: the unit square minus its top-right quadrant. "Both attributes at
                # once" is the combination the training set never contains.
                def generate(z1, z2):
                    if z1 < 2 / 3:                       # left column, area 0.5 -> 2/3 of the square
                        return (z1 * 0.75, z2)
                    return (0.5 + (z1 - 2 / 3) * 1.5, z2 * 0.5)     # bottom-right, area 0.25

                def path_length(steps=64, samples=40_000, warp=True):
                    total = 0.0
                    for _ in range(samples):
                        a = (random.random(), random.random())
                        b = (random.random(), random.random())
                        pts = [(a[0] + (b[0] - a[0]) * t / steps,
                                a[1] + (b[1] - a[1]) * t / steps) for t in range(steps + 1)]
                        if warp:
                            pts = [generate(*p) for p in pts]         # interpolate in Z
                        else:
                            p0, p1 = generate(*a), generate(*b)       # interpolate in W
                            pts = [(p0[0] + (p1[0] - p0[0]) * t / steps,
                                    p0[1] + (p1[1] - p0[1]) * t / steps) for t in range(steps + 1)]
                        total += sum((q[0] - p[0]) ** 2 + (q[1] - p[1]) ** 2
                                     for p, q in zip(pts, pts[1:])) * steps
                    return total / samples

                # Z-space (through the warp): 2.469
                # W-space (straight):         0.305      ratio 8.1x
                #
                # The prior cannot change shape, so the map must -- and the bend is the entanglement.
                # W is allowed to be non-uniform, so it does not have to bend. The cost is honest and
                # small: 3.1% of a straight W path lies in the quadrant no training example occupies.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFF8B5CF6, "Controllable Synthesis", "Coarse pose, middle features, fine colour — from different latents."),
        ApplicationCard("users", 0xFF0EA5E9, "Face Generation", "The model behind a decade of \"this person does not exist\"."),
        ApplicationCard("flask", 0xFFF59E0B, "Latent Editing", "Directions in W are far more nearly attribute-aligned than in Z."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "W can leave the data manifold — 3.1% of a straight path here."),
    ),
    takeaways = listOf(
        "The latent enters at every resolution, not once at the front; synthesis starts from a learned constant.",
        "Nine resolutions × two AdaIN operations = 18 style inputs, split coarse 4, middle 4, fine 10.",
        "AdaIN replaces per-channel mean and std exactly: std 3.436 → 0.0986, the style's to the last digit.",
        "What survives the substitution is the within-channel pattern, and that carries the content.",
        "A fixed prior on a distribution with a missing combination forces a warped map — that is the entanglement.",
        "Measured: path length 2.469 in Z against 0.305 in W, a factor of 8.1 for the same endpoints.",
        "The mapping network's cost is real but small — a straight line in W is outside the data 3.1% of the time.",
    ),
    crossLinks = listOf(
        CrossLink("gans", "GANs"),
        CrossLink("dcgan", "DCGAN (Deep Convolutional GAN)"),
        CrossLink("batch_normalization", "Batch Normalization"),
        CrossLink("neural_style_transfer", "Neural Style Transfer"),
        CrossLink("vae", "Variational Autoencoders (VAE)"),
    ),
)
