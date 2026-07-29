package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val unetContent = TopicContent(
    topicId = "unet",
    whatIsIt = listOf(
        "Segmentation needs a label for every pixel, so a classifier's ending — pool everything away, flatten, dense layer — is exactly wrong: it discards the spatial information the task is asking for. U-Net keeps the contracting encoder that builds semantics and mirrors it with an expanding decoder that upsamples back toward image resolution, giving the architecture the U shape it is named for.",
        "Upsampling alone cannot invent back the boundary detail that pooling threw away, and that is what the skip connections are for. Each decoder stage concatenates the encoder feature map of the same resolution: coarse \"what is this\" from below, fine \"exactly where is the edge\" from the side. Concatenation rather than addition matters — the decoder sees both maps in full and learns how to combine them, rather than being handed a sum.",
        "The original uses unpadded convolutions throughout, so every stage shrinks and the encoder map is always larger than the decoder map it joins. The paper crops it — by 4, 16, 40 and 88 pixels per side going up, as the simulation walks through — and the consequence shows in the shapes: 572×572 in, 388×388 out. The network deliberately predicts a smaller region than it reads, and a large image is covered by overlapping tiles so every predicted pixel has full context. It was trained on about 30 annotated images, with heavy elastic deformation standing in for the data that did not exist and a loss weighted to put extra cost on the thin background gaps *between* touching cells — a segmentation network explicitly taught to draw the separations it would otherwise merge. It won the ISBI cell-tracking challenge by a wide margin and is still the default architecture for medical segmentation.",
    ),
    steps = listOf(
        StepCard(1, "Contract", "Two unpadded 3×3 convolutions, then pool. Resolution down, channels doubled.", 0xFF3B82F6),
        StepCard(2, "Bottleneck", "28×28×1024 from a 572×572 input — the most semantic, least located point.", 0xFF06B6D4),
        StepCard(3, "Expand", "Up-convolution halves the channels and doubles the resolution.", 0xFF6366F1),
        StepCard(4, "Concatenate the Skip", "Crop the encoder map to fit and stack it on — 4, 16, 40, 88 px per side.", 0xFF8B5CF6),
        StepCard(5, "Refine", "Two more convolutions per level, mixing the coarse and fine signals.", 0xFFF59E0B),
        StepCard(6, "Predict a Smaller Region", "572 in, 388 out, and tile a large image with overlap.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Encoder stage", "n → n − 4, then ÷2", "Two unpadded 3×3 convolutions, then a 2×2 pool."),
        FormulaEntry("Decoder stage", "n → 2n, concat skip, then − 4", "Up-convolution, concatenation, two more convolutions."),
        FormulaEntry("Skip crop", "(encoder size − decoder size)/2 per side", "4, 16, 40 and 88 pixels going up from the bottleneck."),
        FormulaEntry("Input vs output", "572 → 388", "The border the network refuses to predict, because it lacks context for it."),
        FormulaEntry("Weighted loss", "w(x) = w_c(x) + w₀·exp(−(d₁+d₂)²/2σ²)", "Extra weight on pixels close to two different cells — the separating gaps."),
        FormulaEntry("Dice loss (later standard)", "1 − 2|P∩G| / (|P|+|G|)", "Overlap-based, and far more stable than cross-entropy on tiny foregrounds."),
    ),
    notationKey = listOf(
        NotationEntry("contracting path", "the encoder: convolutions and pooling, resolution down"),
        NotationEntry("expanding path", "the decoder: up-convolutions, resolution back up"),
        NotationEntry("skip connection", "the encoder map concatenated into the decoder at matching resolution"),
        NotationEntry("up-convolution", "transposed convolution — learned upsampling, not interpolation"),
        NotationEntry("overlap-tile strategy", "covering a large image with overlapping input tiles, since the output is smaller"),
        NotationEntry("elastic deformation", "the augmentation that made 30 training images sufficient"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The shape arithmetic, which is the architecture",
            accentColor = 0xFF3B82F6,
            code = """
                size, skips = 572, []
                for level in range(4):
                    size -= 4                 # two unpadded 3x3 convolutions
                    skips.append(size)
                    size //= 2                # 2x2 max pool
                    print(f"encode {level+1}: {size*2} -> pooled {size}")
                size -= 4
                print("bottleneck", size)     # 28

                for level in reversed(range(4)):
                    size *= 2                 # up-convolution
                    crop = (skips[level] - size) // 2
                    print(f"up {level+1}: {size}, skip {skips[level]} cropped {crop} px per side")
                    size -= 4
                print("output", size)         # 388

                # encode: 568/280/136/64, bottleneck 28, crops 4 / 16 / 40 / 88, output 388.
                # Padded implementations (padding=1 everywhere) keep 572 -> 572 and need no crop at
                # all -- which is what almost everyone does now, at the cost of slightly worse
                # predictions along the border the original refused to make.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "A padded U-Net block, and why concatenate rather than add",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch, torch.nn as nn

                def block(c_in, c_out):
                    return nn.Sequential(
                        nn.Conv2d(c_in, c_out, 3, padding=1), nn.BatchNorm2d(c_out), nn.ReLU(),
                        nn.Conv2d(c_out, c_out, 3, padding=1), nn.BatchNorm2d(c_out), nn.ReLU(),
                    )

                class Up(nn.Module):
                    def __init__(self, c_in, c_out):
                        super().__init__()
                        self.up = nn.ConvTranspose2d(c_in, c_out, 2, stride=2)
                        self.conv = block(c_out * 2, c_out)      # *2: the skip is CONCATENATED

                    def forward(self, x, skip):
                        return self.conv(torch.cat([self.up(x), skip], dim=1))

                # Addition (ResNet's operator) forces the two signals into one channel budget and
                # commits to a fixed mixture. Concatenation keeps both and lets the following
                # convolution learn the mixture per channel -- which is why the block after the
                # concat has twice the input channels, and why U-Net's decoder is not cheap.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF3B82F6, "Medical Imaging", "Organ, tumour and cell segmentation in CT, MRI and microscopy — still the default architecture a decade on."),
        ApplicationCard("globe", 0xFF06B6D4, "Satellite and Aerial Imagery", "Roads, buildings and land cover: dense labels over very large images, tiled exactly as the paper describes."),
        ApplicationCard("target", 0xFF8B5CF6, "Diffusion Models", "Stable Diffusion's denoiser is a U-Net — the same encoder-decoder-with-skips, predicting noise instead of labels."),
        ApplicationCard("chart", 0xFFF59E0B, "Industrial Inspection", "Defect maps on manufactured surfaces, where the training set is small and augmentation carries the load."),
    ),
    takeaways = listOf(
        "Encoder-decoder with skip connections: semantics from the deep path, boundaries from the shallow one.",
        "Skips are concatenated, not added, so the decoder learns how to mix coarse and fine rather than being handed a sum.",
        "Unpadded convolutions mean the encoder map is bigger than the decoder map — cropped by 4, 16, 40 and 88 px per side.",
        "572 in, 388 out: the network refuses to predict a border it has no context for, and large images are tiled with overlap.",
        "Trained on ~30 images using elastic deformation and a loss weighted toward the gaps between touching cells.",
    ),
    crossLinks = listOf(
        CrossLink("segmentation_types", "Semantic vs Instance Segmentation"),
        CrossLink("mask_rcnn", "Mask R-CNN"),
        CrossLink("conv_layers", "Convolution Layers"),
        CrossLink("diffusion_models", "Diffusion Models"),
    ),
)
