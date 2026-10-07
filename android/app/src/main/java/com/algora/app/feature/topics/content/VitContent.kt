package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val vitContent = TopicContent(
    topicId = "vit",
    whatIsIt = listOf(
        "A Vision Transformer does not convolve at all. It cuts a 224×224 image into 16×16 patches — a 14×14 grid, 196 of them — flattens each patch to a 768-vector with one shared linear layer, prepends a learned class token, adds position embeddings, and hands the resulting 197-token sequence to a standard transformer encoder. The paper's title is the whole claim: an image is worth 16×16 words.",
        "The image-specific machinery is almost nothing. Patch embedding is a single linear map from 16·16·3 = 768 raw values to 768 dimensions — 590,592 parameters — and position embeddings add 151,296 more, learned rather than sinusoidal because there was no advantage to hand-designing them. Everything after that is the same encoder used for text. What changes is the inductive bias: a convolution hard-codes locality and translation equivariance, while self-attention hard-codes nothing and compares all 197² = 38,809 token pairs in every layer, so it can relate opposite corners of the image in layer one.",
        "That freedom is a liability at small scale and an advantage at large scale, which is the paper's actual finding. Trained on ImageNet-1k alone, ViT loses to a comparable ResNet; pre-trained on JFT-300M it wins clearly. The bias a convnet is given for free, a transformer has to learn from data — so the crossover is a question of how much data you have. The field's follow-up went both ways: Swin put locality and hierarchy back in via shifted attention windows, DeiT showed distillation and augmentation could close much of the data gap, and ConvNeXt rebuilt a pure convnet with the transformer's training recipe and matched it — which suggests the recipe was doing more of the work than the attention.",
    ),
    steps = listOf(
        StepCard(1, "Cut Into Patches", "224 ÷ 16 = 14, so 196 non-overlapping 16×16 patches.", 0xFF10B981),
        StepCard(2, "Embed Each Patch", "One shared linear map, 768 → 768. 590,592 parameters in total.", 0xFF06B6D4),
        StepCard(3, "Prepend a Class Token", "A learned vector whose final state is the image representation. 197 tokens.", 0xFF6366F1),
        StepCard(4, "Add Position Embeddings", "Attention is permutation-invariant; without these, patch order is lost.", 0xFF8B5CF6),
        StepCard(5, "Run a Standard Encoder", "12 layers of multi-head self-attention and MLP — nothing vision-specific.", 0xFFF59E0B),
        StepCard(6, "Pre-train Large, Then Fine-Tune", "Below JFT scale a ResNet wins; above it, ViT does.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Token count", "N = (H/P)(W/P) + 1", "(224/16)² + 1 = 197, the +1 being the class token."),
        FormulaEntry("Patch embedding", "z₀ = [x_class; x¹ₚE; …; x^N_ₚE] + E_pos", "E is 768×768 here: 590,592 parameters."),
        FormulaEntry("Self-attention", "softmax(QKᵀ/√d)V", "Every token attends to all 197, including itself."),
        FormulaEntry("Attention cost", "O(N²·d) per layer", "38,809 pairs at P = 16; halving the patch size quadruples it."),
        FormulaEntry("Position embeddings", "N × d learned parameters", "197 × 768 = 151,296; interpolated when the input resolution changes."),
        FormulaEntry("Patch embedding ≡ convolution", "Conv2d(3, 768, kernel=16, stride=16)", "Exactly equivalent, and how every implementation does it."),
    ),
    notationKey = listOf(
        NotationEntry("patch", "a 16×16 square of pixels, treated as one token"),
        NotationEntry("class token", "a learned extra token whose output vector is fed to the classifier head"),
        NotationEntry("ViT-B/16", "Base size, 16×16 patches: 12 layers, 768 dims, ≈86M parameters"),
        NotationEntry("inductive bias", "assumptions built into the architecture — locality here, or the lack of it"),
        NotationEntry("JFT-300M", "the 300-million-image internal dataset where ViT overtakes convnets"),
        NotationEntry("Swin / DeiT / ConvNeXt", "the three responses: put locality back, fix the data need, or rebuild the convnet"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Patchify, embed, and count what is actually image-specific",
            accentColor = 0xFF10B981,
            code = """
                import torch, torch.nn as nn

                patch = nn.Conv2d(3, 768, kernel_size=16, stride=16)   # == the linear map on patches
                x = torch.randn(1, 3, 224, 224)
                tokens = patch(x).flatten(2).transpose(1, 2)           # [1, 196, 768]

                cls = nn.Parameter(torch.zeros(1, 1, 768))
                pos = nn.Parameter(torch.zeros(1, 197, 768))
                z0 = torch.cat([cls.expand(1, -1, -1), tokens], dim=1) + pos
                print(z0.shape)                                        # [1, 197, 768]

                print(sum(p.numel() for p in patch.parameters()))      # 590592 = 768 * (768 + 1)
                print(pos.numel())                                     # 151296 = 197 * 768
                # Everything after this line is the text transformer, unchanged.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What dropping the convolution costs",
            accentColor = 0xFF8B5CF6,
            code = """
                # A 3x3 convolution's output at one position depends on 9 inputs. A ViT layer's
                # output at one token depends on all 197.
                print(197 ** 2)                     # 38809 pairs per head, per layer

                # Attention cost is quadratic in token count, and token count is quadratic in
                # 1/patch_size, so the patch size is the single most expensive hyperparameter here:
                for p in (32, 16, 8):
                    n = (224 // p) ** 2 + 1
                    print(p, n, n * n)
                # 32   50    2500
                # 16  197   38809
                #  8  785  616225        -- 16x the attention cost of P=16

                # This is also why high-resolution vision transformers use windowed attention (Swin)
                # or a convolutional stem: full attention over a 1024px image at P=16 is 4097 tokens
                # and ~16.8M pairs per layer.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Large-Scale Classification", "The dominant backbone once pre-training data is measured in hundreds of millions of images."),
        ApplicationCard("network", 0xFF06B6D4, "Multimodal Models", "CLIP, LLaVA, BLIP-2 and many vision-language models use a ViT image tower because it already emits tokens (Flamingo used a ResNet-style NFNet instead)."),
        ApplicationCard("search", 0xFF8B5CF6, "Segmentation and Detection", "Segment Anything and DINOv2 are ViT-based; patch tokens map naturally onto dense prediction."),
        ApplicationCard("flask", 0xFFF59E0B, "Self-Supervised Learning", "Masked autoencoding works cleanly on patches — mask 75% of them and reconstruct."),
    ),
    takeaways = listOf(
        "196 patches plus a class token: an image becomes a 197-token sequence and a standard text encoder does the rest.",
        "The only image-specific parts are a 590,592-parameter patch embedding and 151,296 position parameters.",
        "Self-attention compares all 38,809 token pairs per layer, so global context exists from layer one — and cost is quadratic in token count.",
        "ViT loses to ResNets on ImageNet-1k and wins after JFT-300M pre-training: the convolution's bias is worth a lot of data.",
        "ConvNeXt matched ViT with a pure convnet and the transformer's training recipe, which says the recipe was doing much of the work.",
    ),
    crossLinks = listOf(
        CrossLink("transformers", "Transformers"),
        CrossLink("attention", "Attention"),
        CrossLink("resnet", "ResNet"),
        CrossLink("efficientnet", "EfficientNet"),
    ),
)
