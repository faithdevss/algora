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

internal val stableDiffusionContent = TopicContent(
    topicId = "stable_diffusion",
    figure = Figure(
        caption = "Where latent diffusion's savings come from, priced in the page's lab for one " +
            "512×512 image. The autoencoder squeezes 786,432 pixel values into a 64×64×4 latent of " +
            "16,384 — 48× fewer — but the bigger win is downstream. The U-Net's self-attention " +
            "compares every token with every other, so 64× fewer tokens means 4,096× fewer pairs. " +
            "Cross-attention to the 77 text tokens is linear in the image tokens and only saves " +
            "the 64×. A 50-step DDIM sampler against DDPM's 1,000 adds another 20×, and together " +
            "the attention work per image falls 81,920-fold, from 68.7 trillion pair comparisons " +
            "to 839 million. That arithmetic, not a better denoiser, is why the model runs on a " +
            "consumer GPU.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("786,432", "16,384", "48×"),
                listOf("262,144", "4,096", "64×"),
                listOf("68.7B", "16.8M", "4,096×"),
                listOf("20.2M", "315k", "64×"),
                listOf("1,000", "50", "20×"),
                listOf("68.7T", "839M", "81,920×"),
            ),
            rowHeaders = listOf("values", "tokens", "self-attn pairs", "cross-attn", "steps", "attn / image"),
            colHeaders = listOf("pixel", "latent", "saving"),
            marks = listOf(
                FigureCell(2, 2, FigureTone.Accent),
                FigureCell(5, 2, FigureTone.Accent),
                FigureCell(3, 2, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Stable Diffusion is a diffusion model that does not run on pixels. A separately trained autoencoder compresses a 512×512×3 image to a 64×64×4 latent, the whole noising and denoising process happens there, and the decoder turns the finished latent back into an image exactly once at the end. That is the entire idea, and its justification is a division: 786,432 elements become 16,384, a factor of 48. Diffusion needs many forward passes over the same tensor — that is what makes it expensive and what makes it stable — so a 48× smaller tensor is not a 48× smaller saving. It is 48× per pass, multiplied by every pass.",
        "The self-attention layers in the denoising UNet make the saving much larger than 48×, because attention is quadratic in token count and the token count is what was reduced. A 512×512 image is 262,144 spatial tokens; the latent is 4,096. Self-attention compares every token with every other, so that is 68,719,476,736 pairs against 16,777,216 — a factor of 4,096, the square of the 64× reduction in tokens. Cross-attention behaves completely differently and it is worth not conflating them: it compares image tokens against 77 text tokens, so it is linear in image tokens and the same compression buys only 64×. Add a 50-step DDIM schedule in place of DDPM's 1,000 and the self-attention work over a full sampling run falls by 81,920×. That is the number that moved image generation from a cluster to a consumer graphics card.",
        "A checkpoint is three networks, not one, and only one of them is denoised. The VAE encoder and decoder are about 84M parameters, the CLIP text encoder about 123M, and the UNet about 860M — roughly 1,067M in total, of which the UNet is 81%. The other two run once each per image, at the start and the end; the UNet runs once per step, fifty times. This is also where the model's characteristic failures live. Fine detail that the autoencoder cannot represent in 4 channels at 1/8 resolution — small faces, hands, text — is lost before diffusion begins, and no amount of sampling recovers it, because the information was gone from the latent the UNet was handed.",
    ),
    steps = listOf(
        StepCard(1, "Start From a Latent", "A 64×64×4 noise latent. The VAE encoder (512×512×3 → 64×64×4) is only used in training and img2img/inpainting.", 0xFF10B981),
        StepCard(2, "Encode the Prompt", "CLIP turns the text into 77 conditioning tokens.", 0xFF3B82F6),
        StepCard(3, "Denoise in the Latent", "The UNet runs once per step — 50, not 1,000.", 0xFF8B5CF6),
        StepCard(4, "Attend, Twice Per Block", "Self-attention over 4,096 tokens; cross-attention against the 77.", 0xFFF59E0B),
        StepCard(5, "Guide the Sample", "Classifier-free guidance: two passes, conditional and unconditional.", 0xFFEC4899),
        StepCard(6, "Decode Once", "The VAE decoder produces the pixels — and cannot restore what it dropped.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Elements", "786,432 → 16,384", "512×512×3 against 64×64×4 — a factor of 48."),
        FormulaEntry("Tokens", "262,144 → 4,096", "Downsample factor 8, so 64× fewer."),
        FormulaEntry("Self-attention", "6.87×10¹⁰ → 1.68×10⁷ pairs", "Quadratic: 4,096×, the square of 64."),
        FormulaEntry("Cross-attention", "only 64×", "Linear in image tokens — do not conflate the two."),
        FormulaEntry("Over a sampling run", "81,920×", "4,096× from the latent, 20× from 1,000 → 50 steps."),
        FormulaEntry("Checkpoint", "84M + 123M + 860M", "VAE, CLIP, UNet — the UNet is 81%."),
    ),
    notationKey = listOf(
        NotationEntry("latent diffusion", "running the whole process in a compressed space, not on pixels"),
        NotationEntry("f = 8", "the autoencoder's downsample factor; 512 → 64 per side"),
        NotationEntry("self-attention", "image tokens against image tokens — quadratic"),
        NotationEntry("cross-attention", "image tokens against 77 text tokens — linear"),
        NotationEntry("DDIM", "a deterministic sampler that reaches a sample in far fewer steps"),
        NotationEntry("classifier-free guidance", "two passes per step, extrapolated apart by a scale"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole argument, as arithmetic",
            accentColor = 0xFF8B5CF6,
            code = """
                side, channels, f, latent_channels, text_tokens = 512, 3, 8, 4, 77

                pixel_elements  = side * side * channels                 #    786,432
                latent_elements = (side // f) ** 2 * latent_channels     #     16,384
                print(pixel_elements / latent_elements)                  #      48.0

                pixel_tokens  = side * side                              #    262,144
                latent_tokens = (side // f) ** 2                         #      4,096

                # Self-attention is every token against every other one.
                print(pixel_tokens ** 2 / latent_tokens ** 2)            #    4096.0
                # Cross-attention is every token against 77. Linear -- a completely different saving.
                print((pixel_tokens * text_tokens) / (latent_tokens * text_tokens))   #  64.0

                # Fewer steps multiplies on top of it.
                print((pixel_tokens ** 2 * 1000) / (latent_tokens ** 2 * 50))         #  81920.0
                #
                # The 48x is what people quote; the 4096x is what actually made it run on a consumer
                # GPU, and it exists only because attention is quadratic in the thing that shrank.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Three networks, and only one of them runs fifty times",
            accentColor = 0xFF10B981,
            code = """
                import torch

                @torch.no_grad()
                def sample(prompt, steps=50, guidance=7.5):
                    cond   = text_encoder(tokenize(prompt))        # CLIP, 123M -- once
                    uncond = text_encoder(tokenize(""))            #             -- once
                    latent = torch.randn(1, 4, 64, 64)             # never 3 x 512 x 512

                    for t in scheduler.timesteps(steps):           # UNet, 860M -- 50 times
                        eps_c = unet(latent, t, cond)
                        eps_u = unet(latent, t, uncond)
                        eps   = eps_u + guidance * (eps_c - eps_u)
                        latent = scheduler.step(eps, t, latent)

                    return vae.decode(latent / 0.18215)            # VAE, 84M   -- once

                # Guidance costs a second UNet pass per step, so the real count is 100, not 50 --
                # which is why batching the two conditionings together is the standard trick.
                #
                # And the failure mode this architecture is known for is set before the loop starts:
                # whatever the VAE could not fit into 4 channels at 1/8 resolution -- small faces,
                # hands, text -- is already gone. Sampling longer cannot recover it.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFF8B5CF6, "Text-to-Image", "The architecture that put diffusion on consumer hardware."),
        ApplicationCard("flask", 0xFF10B981, "Inpainting & img2img", "Start the loop from a real encoded latent instead of noise."),
        ApplicationCard("chip", 0xFF3B82F6, "Fine-Tuning", "LoRA and ControlNet attach to the UNet — 81% of the parameters."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Detail below the autoencoder's resolution is lost before diffusion starts."),
    ),
    takeaways = listOf(
        "Diffusion runs in a 64×64×4 latent, not on 512×512×3 pixels — 48× fewer elements.",
        "Because self-attention is quadratic in tokens, the real saving there is 4,096× — the square of 64.",
        "Cross-attention is linear in image tokens, so the same compression buys it only 64×. Different mechanisms.",
        "With 50 DDIM steps against DDPM's 1,000, self-attention work over a full run falls 81,920×.",
        "A checkpoint is three networks: VAE 84M, CLIP 123M, UNet 860M — about 1,067M total.",
        "Only the UNet runs per step; the other two run once each, at the start and the end.",
        "Guidance means two UNet passes per step, so a 50-step sample is really 100 forward passes.",
        "Detail the autoencoder cannot encode is gone before the first denoising step — sampling longer never recovers it.",
    ),
    crossLinks = listOf(
        CrossLink("diffusion_models", "Diffusion Models"),
        CrossLink("vae", "Variational Autoencoders (VAE)"),
        CrossLink("unet", "U-Net (Medical Segmentation)"),
        CrossLink("attention", "Attention"),
        CrossLink("self_cross_attention", "Self- vs Cross-Attention"),
    ),
)
