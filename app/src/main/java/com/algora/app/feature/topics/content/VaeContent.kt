package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val vaeContent = TopicContent(
    topicId = "vae",
    whatIsIt = listOf(
        "A variational autoencoder makes two changes to an ordinary autoencoder, and the pair of them is what turns a compressor into a generator. The encoder stops emitting a point and starts emitting a distribution — a mean and a variance per latent dimension. And the loss gains a second term, the KL divergence between that distribution and a standard normal, which pulls every code toward the same shared prior. The consequence is the whole point: because all codes are pushed onto one known distribution, you can sample from that distribution and decode, and get something plausible. A plain autoencoder's latent space has holes, and decoding a point you did not encode gives you nothing.",
        "The first change creates a problem that has a famous fix and an under-told reason. You cannot backpropagate through \"draw a sample\", so z = μ + σ⊙ε moves the randomness into an input. The usual justification stops there, but it is not that the alternative is impossible — the score-function estimator differentiates through a sample perfectly well and is unbiased. It is that its variance is unusable. Measured on the same objective with the same number of samples, both estimators recover the true gradient of 2.0 — 2.008 and 2.063 — but their variances are 4.01 and 382.3. That is a factor of 95, and it is not a constant: it is 7.5× at one dimension, 33× at four, 95× at eight and 316× at sixteen. Reparameterization's variance does not grow with dimension and the alternative's does, which is why one of them scales to a real latent space and the other does not.",
        "The second change does more than regularise — it deletes latent dimensions outright. Trained here on data with exactly two underlying factors but given four latent dimensions, the model does not spread itself across all four. At β = 0.001 all four stay alive, at β = 0.01 three do, and from β = 0.05 through β = 2.0 — a fortyfold range — exactly two survive, which is the true factor count. The other two collapse to the prior and carry under 0.001 nats each; the decoder ignores them entirely. Push further and the pruning stops being free: at β = 4 only one dimension is left and reconstruction error has climbed from 0.051 to 0.812. The KL term is a dimension-selection mechanism first and a smoothness prior second, and β is the dial between recovering the data's real dimensionality and destroying it.",
    ),
    steps = listOf(
        StepCard(1, "Emit a Distribution, Not a Point", "The encoder returns μ and log σ² per latent dimension.", 0xFF818CF8),
        StepCard(2, "Reparameterize", "z = μ + σ⊙ε with ε ~ N(0, I) — randomness becomes an input.", 0xFF60A5FA),
        StepCard(3, "Decode and Score", "Reconstruction loss on the decoded output, exactly as before.", 0xFF10B981),
        StepCard(4, "Add the KL Term", "Pull every posterior toward N(0, I) so the latent space has no holes.", 0xFFF59E0B),
        StepCard(5, "Watch Dimensions Switch Off", "Two of four survive here — the data's true factor count.", 0xFFEC4899),
        StepCard(6, "Sample the Prior to Generate", "Draw z ~ N(0, I), decode, and get something never encoded.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("ELBO", "L = E[log p(x|z)] − β·KL(q(z|x) ‖ p(z))", "Reconstruction minus a penalty on the code."),
        FormulaEntry("Reparameterization", "z = μ + σ⊙ε,  ε ~ N(0, I)", "Differentiable in μ and σ."),
        FormulaEntry("KL, closed form", "½Σ(μ² + σ² − 1 − log σ²)", "Per dimension, against a standard normal."),
        FormulaEntry("Gradient variance", "4.01 vs 382.3 at d = 8", "Reparameterized against score-function."),
        FormulaEntry("And it scales", "7.5× · 33× · 95× · 316×", "At d = 1, 4, 8, 16 — one term grows, one does not."),
        FormulaEntry("Active units", "2 of 4 for β ∈ [0.05, 2.0]", "The data has exactly two factors."),
    ),
    notationKey = listOf(
        NotationEntry("q(z|x)", "the encoder's posterior — a Gaussian per input, not a point"),
        NotationEntry("p(z)", "the prior, N(0, I); what the KL term pulls every posterior toward"),
        NotationEntry("ε", "the sampled noise, moved out of the graph and in through an input"),
        NotationEntry("β", "the weight on the KL term; 1 is a plain VAE, higher is a β-VAE"),
        NotationEntry("active unit", "a latent dimension carrying more than 0.01 nats of KL"),
        NotationEntry("posterior collapse", "a dimension matching the prior exactly and carrying nothing"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The two changes, and the one line that is easy to write backwards",
            accentColor = 0xFF818CF8,
            code = """
                import torch
                import torch.nn as nn

                class Vae(nn.Module):
                    def __init__(self, d_in=784, d_hidden=256, d_latent=4):
                        super().__init__()
                        self.encode = nn.Sequential(nn.Linear(d_in, d_hidden), nn.ReLU())
                        self.to_mu = nn.Linear(d_hidden, d_latent)
                        self.to_logvar = nn.Linear(d_hidden, d_latent)   # log sigma^2, not sigma
                        self.decode = nn.Sequential(
                            nn.Linear(d_latent, d_hidden), nn.ReLU(), nn.Linear(d_hidden, d_in),
                        )

                    def forward(self, x):
                        h = self.encode(x)
                        mu, logvar = self.to_mu(h), self.to_logvar(h)
                        std = torch.exp(0.5 * logvar)
                        z = mu + std * torch.randn_like(std)      # <- the reparameterization
                        return self.decode(z), mu, logvar

                def loss_fn(x_hat, x, mu, logvar, beta=1.0):
                    recon = ((x_hat - x) ** 2).sum(dim=-1).mean()
                    # Per-dimension, so the collapsed ones are visible before they are summed away.
                    kl_per_dim = 0.5 * (mu.pow(2) + logvar.exp() - 1 - logvar).mean(dim=0)
                    return recon + beta * kl_per_dim.sum(), kl_per_dim
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the reparameterized estimator is the only usable one",
            accentColor = 0xFFEC4899,
            code = """
                import torch

                def compare(d=8, samples=400_000):
                    mu = torch.ones(d, requires_grad=True)
                    eps = torch.randn(samples, d)
                    z = mu + eps                                  # sigma = 1

                    # Reparameterized: differentiate the sample path. d f/d mu_0 = 2(z_0 - c_0).
                    reparam = 2 * z[:, 0]

                    # Score function: f(z) * d/d mu_0 log q(z). Also unbiased -- and unusable.
                    f = (z ** 2).sum(dim=-1)
                    score = f * z[:, 0].sub(mu[0].detach())

                    return (reparam.mean(), reparam.var()), (score.mean(), score.var())

                # d= 1  reparam var 3.99   score var   29.9   ratio    7.5x
                # d= 4  reparam var 4.01   score var  133.1   ratio   33.2x
                # d= 8  reparam var 4.01   score var  382.3   ratio   95.4x
                # d=16  reparam var 4.03   score var 1273.0   ratio  315.9x
                #
                # Both means land on the true gradient of 2.0. The textbook line is "you cannot
                # backprop through a sample", but you can -- the score-function estimator does. The
                # real reason is the right-hand column, and that it grows with d while the left does
                # not. At a realistic latent size the second estimator is pure noise.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF818CF8, "Generation From a Prior", "Sample z ~ N(0, I) and decode — no encoder input needed."),
        ApplicationCard("chart", 0xFF60A5FA, "Disentangled Factors", "β-VAE prunes to the data's real dimensionality, measurable as active units."),
        ApplicationCard("target", 0xFF10B981, "Anomaly Detection", "Low likelihood under the learned model rather than raw reconstruction error."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "VAE samples are blurrier than a GAN's — the Gaussian likelihood averages."),
    ),
    takeaways = listOf(
        "Two changes to an autoencoder: the encoder emits a distribution, and the loss gains a KL term.",
        "The KL term is what removes the holes, which is what makes sampling the prior produce something plausible.",
        "The score-function estimator is unbiased too — 2.06 against a true 2.0. It is the variance that rules it out.",
        "Reparameterized variance 4.01 against 382.3 at d = 8, and the gap grows with dimension: 7.5× → 33× → 95× → 316×.",
        "The KL term deletes latent dimensions: 2 of 4 survive here, which is the data's true factor count.",
        "That plateau holds across a fortyfold range of β, from 0.05 to 2.0 — β is not a fine dimension dial.",
        "Push β to 4 and it eats a real factor: one unit left, reconstruction error 0.051 → 0.812.",
    ),
    crossLinks = listOf(
        CrossLink("autoencoders", "Autoencoders"),
        CrossLink("gans", "GANs"),
        CrossLink("diffusion_models", "Diffusion Models"),
        CrossLink("pca", "PCA"),
        CrossLink("backpropagation", "Backpropagation Algorithm"),
    ),
)
