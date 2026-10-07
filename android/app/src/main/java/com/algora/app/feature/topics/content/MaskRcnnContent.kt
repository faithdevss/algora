package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val maskRcnnContent = TopicContent(
    topicId = "mask_rcnn",
    figure = Figure(
        caption = "The defect a class label survived and a mask did not, measured along one axis " +
            "of a 145-pixel box at stride 16. In feature-map units the box is 145/16 = 9.0625 " +
            "wide, and RoI pooling floors that to 9 — one rounding, 1 pixel of the image gone " +
            "before anything is read. Then it divides those 9 units into 7 bins and snaps every bin boundary to a " +
            "whole cell (floor for the start, ceil for the end), so the bins come out 2 or 3 cells " +
            "wide and overlap, and each boundary can sit up to one cell — 16 image pixels — from " +
            "where it belongs. Both roundings happen in feature units, so each one costs a " +
            "multiple of the stride. RoIAlign takes " +
            "the same box, divides 9.0625 by 7 to get bins of 1.2946, samples at the exact " +
            "centres 0.65, 1.94, 3.24, 4.53, 5.83, 7.12 and 8.42 with bilinear interpolation, and " +
            "never calls floor() — worth about 3 mask AP, and roughly double that at AP75.",
        shape = FigureShape.Strip(
            cells = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
            bands = listOf(
                FigureBand(0, 8, "7 bins, edges snapped to whole cells (up to 16 px off)", FigureTone.Warn),
                FigureBand(9, 9, "9.06", FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Mask R-CNN is Faster R-CNN plus a third head: alongside the class score and the box, a small fully convolutional branch predicts a 28×28 binary mask for each region of interest. The addition is almost trivially simple, which is the paper's point — instance segmentation did not need a new paradigm, it needed one more branch and one arithmetic fix. It beat every entrant of the 2016 COCO segmentation challenge and runs at about 5 frames per second.",
        "The masks are per-class and binary, with no softmax across classes. The classification head decides *what* the object is; the mask head only decides *which pixels* belong to it, predicting one independent mask per class and using the one the classifier chose. Decoupling those two questions is worth several points of mask AP over the usual per-pixel multi-class softmax, because the mask branch stops competing with itself across classes it was never asked to distinguish.",
        "The mask branch also exposed a defect that classification had tolerated for two years. RoI pooling quantises twice — snapping the box onto the feature grid, then dividing that grid into bins — and both roundings happen in feature-map units, so each costs a multiple of the stride in image pixels. On a 145-pixel box at stride 16 the simulation measures 1 pixel lost snapping the RoI and bin boundaries that are snapped by up to one cell (16 pixels) each, with uneven, overlapping bins. A class label survives that. A pixel-accurate mask does not. RoIAlign removes both roundings by sampling each bin at exact floating-point locations with bilinear interpolation and never calling floor(), and the paper reports roughly a 3-point mask-AP gain from that change alone — about twice as much at the strict IoU 0.75 threshold, where a few pixels of misalignment is exactly what decides a match.",
    ),
    steps = listOf(
        StepCard(1, "Start From Faster R-CNN", "RPN, proposals, class head, box head — all unchanged.", 0xFF3B82F6),
        StepCard(2, "Add a Mask Branch", "A small FCN per RoI producing K masks of 28×28.", 0xFF06B6D4),
        StepCard(3, "Decouple Class From Mask", "Per-class binary masks, sigmoid not softmax. The class head picks one.", 0xFF6366F1),
        StepCard(4, "Find the Misalignment", "RoI pooling's roundings cost 1 px (RoI snap) + up to 16 px per bin edge at stride 16.", 0xFF8B5CF6),
        StepCard(5, "Replace It With RoIAlign", "Bilinear sampling at exact locations. No floor(), no snapping.", 0xFFF59E0B),
        StepCard(6, "Train All Three Heads", "L = L_cls + L_box + L_mask, one loss, one optimiser.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Total loss", "L = L_cls + L_box + L_mask", "Per sampled RoI, all three heads together."),
        FormulaEntry("Mask loss", "average per-pixel binary cross-entropy, on the true class's mask only", "The other K−1 masks contribute nothing."),
        FormulaEntry("Mask output", "K × 28 × 28 per RoI", "One binary mask per class, no competition between them."),
        FormulaEntry("RoI pooling quantisation", "1 px (RoI snap) + up to 16 px (bin-edge snap)", "Measured on a 145-pixel box at stride 16."),
        FormulaEntry("RoIAlign", "bilinear sample at exact bin centres", "Zero quantisation; the fix is arithmetic, not architecture."),
        FormulaEntry("Reported gain", "≈+3 mask AP, roughly double that at AP75", "Strict thresholds are where misalignment shows."),
    ),
    notationKey = listOf(
        NotationEntry("instance segmentation", "a mask per object, not per class — what this architecture produces"),
        NotationEntry("RoIAlign", "RoI pooling without the two roundings, using bilinear interpolation"),
        NotationEntry("mask AP", "average precision with IoU computed over masks rather than boxes"),
        NotationEntry("AP75", "AP at the strict 0.75 IoU threshold, where alignment matters most"),
        NotationEntry("FCN head", "the small fully convolutional network producing the mask"),
        NotationEntry("keypoint head", "the same trick with a one-hot mask per joint — human pose estimation for free"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The two roundings, and what removing them is worth",
            accentColor = 0xFF3B82F6,
            code = """
                stride, bins, box = 16, 7, 145.0

                exact_cells = box / stride              # 9.0625
                snapped = int(exact_cells)              # 9      <- rounding one
                bin_exact = snapped / bins              # 1.2857 cells per bin
                # rounding two: bin boundaries are snapped to whole cells (floor/ceil)

                print((exact_cells - snapped) * stride)                  # 1.0  px
                print(stride)   # each snapped bin edge can be off by up to one cell = 16 px

                # RoIAlign replaces both with sampling at exact positions:
                import torch
                from torchvision.ops import roi_align, roi_pool
                feats = torch.randn(1, 256, 50, 50)
                rois = torch.tensor([[0, 30.4, 20.7, 175.4, 165.7]])     # note the fractions
                print(roi_pool(feats, rois, (7, 7), 1/16).shape)         # snapped to the grid
                print(roi_align(feats, rois, (7, 7), 1/16, sampling_ratio=2).shape)  # not snapped
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Per-class binary masks, and why not a softmax",
            accentColor = 0xFF6366F1,
            code = """
                import torch, torch.nn as nn, torch.nn.functional as F

                mask_head = nn.Sequential(
                    *[l for _ in range(4) for l in (nn.Conv2d(256, 256, 3, padding=1), nn.ReLU())],
                    nn.ConvTranspose2d(256, 256, 2, stride=2), nn.ReLU(),
                    nn.Conv2d(256, 80, 1),               # K = 80 COCO classes, ONE mask each
                )
                logits = mask_head(torch.randn(4, 256, 14, 14))
                print(logits.shape)                      # [4, 80, 28, 28]

                # Training uses only the mask of the ground-truth class, with a per-pixel sigmoid:
                labels = torch.tensor([3, 17, 3, 62])
                chosen = logits[torch.arange(4), labels]                 # [4, 28, 28]
                loss = F.binary_cross_entropy_with_logits(chosen, torch.zeros_like(chosen))

                # A per-pixel softmax across the 80 classes would make the masks compete: raising
                # the "dog" mask at a pixel would lower "cat" there, even though the class decision
                # has already been made elsewhere. Decoupling the two questions is worth several
                # points of mask AP for no extra compute.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF3B82F6, "Cell and Tissue Analysis", "Counting and measuring individual cells needs instances, not a class map — touching cells must stay separate."),
        ApplicationCard("robot", 0xFF06B6D4, "Robotic Grasping", "A robot picks one object, so it needs that object's pixels rather than the class region."),
        ApplicationCard("browser", 0xFF8B5CF6, "Photo Editing", "Selecting and cutting out a specific person or item is instance segmentation in an interactive loop."),
        ApplicationCard("users", 0xFFF59E0B, "Human Pose Estimation", "Swap the mask for a one-hot map per joint and the same architecture predicts keypoints."),
    ),
    takeaways = listOf(
        "Faster R-CNN plus one small FCN head predicting a 28×28 binary mask per RoI — that is the whole architecture.",
        "Masks are per-class and independent, so the mask branch never competes with itself across classes.",
        "RoI pooling's roundings cost 1 px and up to 16 px per bin edge at stride 16 — invisible to a label, fatal to a mask.",
        "RoIAlign samples at exact positions with bilinear interpolation; the paper reports ~+3 mask AP, and about double that at AP75.",
        "The same head, with a one-hot map per joint, does human pose estimation with no other change.",
    ),
    crossLinks = listOf(
        CrossLink("faster_rcnn", "Faster R-CNN"),
        CrossLink("segmentation_types", "Semantic vs Instance Segmentation"),
        CrossLink("unet", "U-Net"),
        CrossLink("fast_rcnn", "Fast R-CNN"),
    ),
)
