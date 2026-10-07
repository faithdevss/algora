package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fastRcnnContent = TopicContent(
    topicId = "fast_rcnn",
    whatIsIt = listOf(
        "R-CNN runs the convolutional stack once per proposal, 2,000 times over the same image, on regions that overlap heavily. Fast R-CNN runs it once. The image passes through the convolutions a single time, and each proposal is *projected* onto that shared feature map — a box in image pixels becomes a box on the feature grid by dividing by the stride. All the expensive work is now shared.",
        "The piece that makes it possible is RoI pooling: take the projected region, divide it into a fixed 7×7 grid of bins whatever its size, and max-pool inside each bin. Any region becomes a 7×7 feature block that a dense head can read. That also collapses R-CNN's three training stages into one — class scores and box refinement come from two heads on one network trained with a single multi-task loss, so there is no SVM stage and no feature cache on disk.",
        "The measured result is 47 seconds per image down to 2.3, and 0.32 if the proposals already exist; training is about 9× faster. But look at where the time went: selective search now takes roughly seven times longer than the network it feeds, and it is the only part of the pipeline that is not learned. That is the whole premise of Faster R-CNN. The simulation also prices RoI pooling's known defect — it quantises twice, snapping the box to the feature grid and then the grid into bins, and at stride 16 those roundings cost 1 pixel for the box and up to one feature cell (16 pixels) per bin boundary on a 145-pixel box. Tolerable for a class label; not tolerable for a mask, which is what Mask R-CNN's RoIAlign later fixes.",
    ),
    steps = listOf(
        StepCard(1, "One Forward Pass", "The whole image through the conv stack, once.", 0xFF3B82F6),
        StepCard(2, "Project the Proposals", "Divide box coordinates by the stride to land on the feature map.", 0xFF06B6D4),
        StepCard(3, "RoI Pool to 7×7", "Any region size in, a fixed block out. No parameters.", 0xFF6366F1),
        StepCard(4, "Two Heads, One Loss", "Softmax over K+1 classes and per-class box offsets, trained jointly.", 0xFF8B5CF6),
        StepCard(5, "Backprop Through the Pooling", "Gradients route to the argmax cell, so the shared map is trained too.", 0xFFF59E0B),
        StepCard(6, "Notice the New Bottleneck", "The proposals are now 87% of the runtime.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("RoI projection", "feature box = image box / stride", "Stride 16 for VGG-16's conv5."),
        FormulaEntry("RoI pooling", "bin size = RoI size / 7, max within each bin", "Both divisions are rounded — that is the misalignment."),
        FormulaEntry("Multi-task loss", "L = L_cls(p, u) + λ[u ≥ 1]·L_loc(tᵘ, v)", "The indicator means background boxes contribute no localisation loss."),
        FormulaEntry("Smooth L1", "0.5x² if |x| < 1, else |x| − 0.5", "Less sensitive to outliers than L2, no exploding gradients."),
        FormulaEntry("Quantisation cost", "1 px snapping the RoI + up to 16 px per snapped bin edge", "At stride 16 on a 145-pixel box."),
        FormulaEntry("Measured speedup", "47 s → 2.3 s (0.32 s excluding proposals)", "Test time per image with VGG-16."),
    ),
    notationKey = listOf(
        NotationEntry("RoI", "region of interest — one proposal, expressed on the feature map"),
        NotationEntry("RoI pooling", "fixed-size max pooling over a variable-size region"),
        NotationEntry("stride", "how many image pixels one feature-map cell covers; 16 for VGG-16 conv5"),
        NotationEntry("multi-task loss", "classification and localisation summed and optimised together"),
        NotationEntry("smooth L1", "the robust regression loss used for box offsets"),
        NotationEntry("truncated SVD", "the paper's trick for compressing the fc layers at test time"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The shared map, and RoI pooling on top of it",
            accentColor = 0xFF3B82F6,
            code = """
                import torch, torchvision
                from torchvision.ops import roi_pool

                backbone = torchvision.models.vgg16().features[:-1]     # stride 16
                image = torch.randn(1, 3, 600, 800)
                feats = backbone(image)                                  # ONE forward pass
                print(feats.shape)                                       # [1, 512, 37, 50]

                # Proposals in image coordinates, with a batch index in front.
                rois = torch.tensor([[0, 20., 40., 300., 500.], [0, 310., 60., 620., 420.]])
                pooled = roi_pool(feats, rois, output_size=(7, 7), spatial_scale=1/16)
                print(pooled.shape)                                      # [2, 512, 7, 7]

                # Two thousand proposals would give [2000, 512, 7, 7] from the same single pass --
                # the convolutions are computed once and read many times.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The two roundings, counted in image pixels",
            accentColor = 0xFFEC4899,
            code = """
                stride, bins, box = 16, 7, 145.0

                exact = box / stride                    # 9.0625 feature cells
                snapped = int(exact)                    # 9
                print((exact - snapped) * stride)       # 1.0 px lost snapping the RoI

                # Bins use floor(i*h/7) .. ceil((i+1)*h/7), so together they cover all 9 cells
                # (nothing is skipped) but are uneven and overlapping.
                import math
                edges = [(math.floor(i * snapped / bins), math.ceil((i + 1) * snapped / bins))
                         for i in range(bins)]
                print(edges)   # [(0,2),(1,3),(2,4),(3,6),(5,7),(6,8),(7,9)]: widths 2 or 3, not 1.29

                # Each bin boundary is snapped to a whole cell, so it can sit up to one cell
                # (16 px) from where it belongs. A class label survives that. A 28x28 mask
                # misaligned by that much does not, which is exactly the misalignment Mask
                # R-CNN's RoIAlign was introduced to remove.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Shared Computation", "The general lesson: compute features once over the whole input and index into them, rather than recomputing per region."),
        ApplicationCard("target", 0xFF06B6D4, "End-to-End Training", "Collapsing three stages into one loss is what let the backbone learn features suited to detection rather than to classification."),
        ApplicationCard("search", 0xFF8B5CF6, "RoI Operators Everywhere", "RoI pooling and RoIAlign appear in video models, 3-D detection and document understanding."),
        ApplicationCard("bulb", 0xFFF59E0B, "Profiling Before Optimising", "The speedup relocated the bottleneck to the one un-learned component — which set the next paper's agenda."),
    ),
    takeaways = listOf(
        "One forward pass over the image, with every proposal reading from the same feature map.",
        "RoI pooling turns any region into a fixed 7×7 block, which is what lets a dense head follow it.",
        "Three separately trained stages become one network with one multi-task loss and no feature cache.",
        "47 s → 2.3 s per image, and ~9× faster training; the proposals then become 87% of the remaining time.",
        "RoI pooling quantises twice — 1 px plus up to 16 px per bin edge at stride 16 — which is fine for labels and fatal for masks.",
    ),
    crossLinks = listOf(
        CrossLink("rcnn", "R-CNN"),
        CrossLink("faster_rcnn", "Faster R-CNN"),
        CrossLink("mask_rcnn", "Mask R-CNN"),
        CrossLink("pooling_layers", "Pooling Layers"),
    ),
)
