package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fasterRcnnContent = TopicContent(
    topicId = "faster_rcnn",
    whatIsIt = listOf(
        "Fast R-CNN left a CPU algorithm in the middle of a GPU pipeline, and it took roughly seven times longer than the network it fed. Faster R-CNN's move is to make the proposals a network too: a Region Proposal Network slides a small convolution over the *same* feature map the detector already computed, so proposals cost about 10 milliseconds instead of two seconds. Everything in the detector is now learned, and everything shares one backbone.",
        "Anchors are the idea that made that possible. Rather than regressing boxes out of nothing, the RPN places a fixed set of reference boxes — three scales × three aspect ratios — at every feature-map position, scores each for objectness, and regresses an offset from the ones that fit. On a 40×60 feature map that is 21,600 anchors, which is the network's entire hypothesis space laid out in advance. NMS reduces the survivors to 2,000 proposals at training time and 300 at test time.",
        "The result is the canonical two-stage detector: propose, then classify and refine, at 73.2% mAP on VOC 2007 and about 0.2 seconds per image with VGG-16 — 5 frames per second, and 17 with a smaller backbone. The remaining cost is structural: every proposal still gets its own pass through the head, so work scales with the number of regions. That is exactly what the one-stage detectors set out to remove, and the anchor idea introduced here is what they took with them.",
    ),
    steps = listOf(
        StepCard(1, "Share the Backbone", "The RPN reads the same feature map the detector uses.", 0xFF3B82F6),
        StepCard(2, "Lay Down Anchors", "9 per position — 3 scales × 3 aspect ratios — 21,600 on a 40×60 map.", 0xFF06B6D4),
        StepCard(3, "Score Objectness", "A 1×1 conv per anchor: object or background, class-agnostic.", 0xFF6366F1),
        StepCard(4, "Regress an Offset", "Four numbers per anchor, relative to the anchor rather than absolute.", 0xFF8B5CF6),
        StepCard(5, "Suppress to 300", "NMS on objectness: 21,600 → 2,000 in training, 300 at test.", 0xFFF59E0B),
        StepCard(6, "Hand Them to the Head", "The same Fast R-CNN classifier and box regressor as before.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Anchor count", "W · H · k", "40 × 60 × 9 = 21,600 on a stride-16 map."),
        FormulaEntry("Box parameterisation", "tₓ = (x − xₐ)/wₐ, t_w = log(w/wₐ)", "Offsets are relative to the anchor, which makes the targets scale-free."),
        FormulaEntry("Anchor assignment", "positive if IoU > 0.7 or best for a GT box; negative if IoU < 0.3", "The \"best for a GT box\" clause is what stops an unusual object from having no positive at all."),
        FormulaEntry("RPN loss", "L = L_cls/N_cls + λ·Σp*·L_reg/N_reg", "The p* factor means only positive anchors contribute a regression term."),
        FormulaEntry("Proposal cost", "≈10 ms vs selective search's ≈2,000 ms", "The measurement the paper exists for."),
        FormulaEntry("End to end", "≈0.2 s per image, 73.2% mAP VOC07", "5 fps with VGG-16."),
    ),
    notationKey = listOf(
        NotationEntry("RPN", "region proposal network — a small conv head predicting objectness and offsets"),
        NotationEntry("anchor", "a reference box of fixed size and shape at a feature-map position"),
        NotationEntry("objectness", "a class-agnostic \"is anything here\" score"),
        NotationEntry("4-step alternating training", "the paper's original recipe for sharing features; later replaced by joint training"),
        NotationEntry("FPN", "feature pyramid network — the later upgrade that gives each scale its own map"),
        NotationEntry("two-stage", "propose then classify; the family this completes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Anchors, and how many boxes a detector really scores",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np

                scales, ratios, stride = [128, 256, 512], [0.5, 1.0, 2.0], 16
                fh, fw = 40, 60                          # a 640x960 image at stride 16

                def anchors_at(cx, cy):
                    out = []
                    for s in scales:
                        for r in ratios:
                            w, h = s * np.sqrt(1 / r), s * np.sqrt(r)
                            out.append((cx - w/2, cy - h/2, cx + w/2, cy + h/2))
                    return out

                print(len(anchors_at(0, 0)))             # 9 per position
                print(fh * fw * 9)                       # 21600 anchors per image

                # Every one gets an objectness score and four offsets. NMS on those scores leaves
                # 2000 proposals for training and 300 at test time -- so the expensive head runs
                # 300 times, not 21,600.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Using it, and reading where the time goes",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch, torchvision

                model = torchvision.models.detection.fasterrcnn_resnet50_fpn(weights=None)
                model.eval()
                out = model(torch.randn(1, 3, 600, 800))[0]
                print(out['boxes'].shape, out['labels'].shape, out['scores'].shape)

                # The knobs that matter at inference, and what each one trades:
                model.rpn._pre_nms_top_n = dict(training=2000, testing=1000)   # candidates kept
                model.rpn._post_nms_top_n = dict(training=2000, testing=300)   # proposals to head
                model.roi_heads.score_thresh = 0.05      # raise it for fewer, surer detections
                model.roi_heads.nms_thresh = 0.5         # lower it and crowded scenes lose objects

                # nms_thresh is the one people tune last and should tune first: at 0.3 two genuinely
                # overlapping objects are merged into one detection, and no amount of training fixes
                # a box that post-processing deleted.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF3B82F6, "General-Purpose Detection", "With an FPN backbone this is still a strong baseline and the default in most detection toolkits."),
        ApplicationCard("flask", 0xFF06B6D4, "Medical and Scientific Imaging", "Two-stage accuracy matters more than frame rate when a missed finding is the cost."),
        ApplicationCard("browser", 0xFF8B5CF6, "Document Layout Analysis", "Detecting tables, figures and columns on a page is detection with unusual aspect ratios — anchors are configured, not redesigned."),
        ApplicationCard("search", 0xFFF59E0B, "Instance Segmentation", "Mask R-CNN is this architecture with one more head; the whole pipeline carries over."),
    ),
    takeaways = listOf(
        "The RPN replaces selective search with a conv head on the shared feature map: ~2,000 ms → ~10 ms.",
        "Anchors turn detection into scoring a fixed hypothesis set — 9 per position, 21,600 on a 40×60 map.",
        "Boxes are regressed as offsets from an anchor, which makes the targets scale-free and easy to learn.",
        "73.2% VOC07 mAP at about 0.2 s per image, and every stage of the pipeline is now learned.",
        "The remaining cost is per-region work in the head, which is exactly what YOLO and SSD removed.",
    ),
    crossLinks = listOf(
        CrossLink("fast_rcnn", "Fast R-CNN"),
        CrossLink("yolo", "YOLO"),
        CrossLink("mask_rcnn", "Mask R-CNN"),
        CrossLink("resnet", "ResNet"),
    ),
)
