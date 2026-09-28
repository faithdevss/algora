package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ssdContent = TopicContent(
    topicId = "ssd",
    whatIsIt = listOf(
        "SSD agrees with YOLO that a detector should be one forward pass and disagrees about scale. One grid over one feature map carries a single notion of object size; SSD attaches detection heads to six feature maps of decreasing resolution, so a 38×38 map at stride 8 handles small objects and a 1×1 map handles ones that fill the frame. Prediction happens at every scale rather than at one, using the pyramid the backbone already produces.",
        "Each head places default boxes — anchors, by another name — at every location: 4 or 6 shapes per position, each predicting a class distribution and a four-number offset. Counted level by level that is 5,776 + 2,166 + 600 + 150 + 36 + 4 = 8,732 boxes for SSD300, and the simulation sums them from the level table rather than quoting the total. Nearly two thirds come from the finest map alone, which is exactly where small objects live and where a coarse detector fails.",
        "Scoring 8,732 boxes against an image holding two objects creates a brutal imbalance, and SSD handles it with hard negative mining: sort the background boxes by loss, keep only the worst at a 3:1 ratio to the positives, discard the rest. It works — SSD300 reached 74.3% mAP at 59 fps on VOC 2007, beating both YOLO v1's 63.4% at 45 fps and Faster R-CNN's 73.2% at 7 — and it is a heuristic bolted onto the loss, discarding most of the training signal to keep the ratio manageable. Replacing that heuristic with a loss function is RetinaNet's entire contribution a year later.",
    ),
    steps = listOf(
        StepCard(1, "Take the Whole Pyramid", "Six feature maps at strides 8 to 300, not one.", 0xFF3B82F6),
        StepCard(2, "Attach a Head Per Level", "A small conv predicting classes and offsets, per location.", 0xFF06B6D4),
        StepCard(3, "Place Default Boxes", "4 or 6 aspect ratios per location, scaled to the level.", 0xFF6366F1),
        StepCard(4, "Count Them", "5,776 + 2,166 + 600 + 150 + 36 + 4 = 8,732 for SSD300.", 0xFF8B5CF6),
        StepCard(5, "Match and Mine", "Match by IoU > 0.5, then keep the hardest negatives at 3:1.", 0xFFF59E0B),
        StepCard(6, "Suppress and Report", "NMS per class, and you are done in one pass.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Total default boxes", "Σ_levels W·H·k", "8,732 for SSD300; 24,564 for SSD512."),
        FormulaEntry("Scale per level", "s_k = s_min + (s_max − s_min)(k − 1)/(m − 1)", "Linearly spaced from 0.2 to 0.9 of the image."),
        FormulaEntry("Box shapes", "w = s_k√aᵣ, h = s_k/√aᵣ", "aᵣ ∈ {1, 2, 3, ½, ⅓}, plus one extra square box per level."),
        FormulaEntry("Matching", "IoU > 0.5 with any ground-truth box", "Multiple defaults may match one object, unlike YOLO's single responsible cell."),
        FormulaEntry("Hard negative mining", "keep worst negatives at 3:1 to positives", "A heuristic that RetinaNet's focal loss replaces."),
        FormulaEntry("Measured", "74.3% mAP at 59 fps (SSD300, VOC07)", "Against YOLO 63.4 @ 45 and Faster R-CNN 73.2 @ 7."),
    ),
    notationKey = listOf(
        NotationEntry("default box", "SSD's name for an anchor: a fixed reference shape at a location"),
        NotationEntry("multi-scale prediction", "detection heads on several feature maps at once"),
        NotationEntry("SSD300 / SSD512", "the two input resolutions; the larger is more accurate and slower"),
        NotationEntry("hard negative mining", "training only on the background boxes the model finds hardest"),
        NotationEntry("atrous / dilated conv", "used in SSD's backbone modification to keep resolution up"),
        NotationEntry("data augmentation", "the paper's aggressive random cropping, worth several points of mAP on small objects"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Counting the boxes, level by level",
            accentColor = 0xFF3B82F6,
            code = """
                levels = [(38, 4), (19, 6), (10, 6), (5, 6), (3, 4), (1, 4)]   # (grid, boxes per cell)

                counts = [g * g * k for g, k in levels]
                print(counts)          # [5776, 2166, 600, 150, 36, 4]
                print(sum(counts))     # 8732

                print(counts[0] / sum(counts))   # 0.66 -- two thirds from the finest map

                # This is the whole design in one line of arithmetic. YOLO v1 predicted 98 boxes from
                # one 7x7 map; SSD predicts 8,732 across six, and most of the extra capacity is spent
                # exactly where a single coarse grid is weakest.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Hard negative mining, and why it is a stopgap",
            accentColor = 0xFFF59E0B,
            code = """
                import torch

                def mine(conf_loss, positives, ratio=3):
                    # conf_loss: per-box classification loss. positives: bool mask of matched boxes.
                    neg_loss = conf_loss.clone()
                    neg_loss[positives] = 0
                    k = int(ratio * positives.sum())
                    hardest = neg_loss.topk(k).indices
                    keep = positives.clone()
                    keep[hardest] = True
                    return keep                     # everything else contributes nothing at all

                # With ~8,700 negatives and ~10 positives, this trains on 30 background boxes and
                # throws away 8,700. It stabilises training and it is lossy and arbitrary: the ratio
                # is a hyperparameter, the cut is hard, and an easy negative that later becomes hard
                # was never seen. Focal loss keeps every box and weights it by how wrong it is,
                # which is the same intent expressed as a continuous function.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Mobile Detection", "SSD-MobileNet is the standard on-device detector, and the reference model in most mobile ML toolkits."),
        ApplicationCard("game", 0xFF06B6D4, "Real-Time Video", "Fast enough for live streams while staying close to two-stage accuracy."),
        ApplicationCard("search", 0xFF8B5CF6, "Small-Object Detection", "The fine-resolution head is what makes distant or small objects findable at all in a one-stage design."),
        ApplicationCard("browser", 0xFFF59E0B, "Multi-Scale Design", "Predicting at several resolutions became universal — FPN generalised it to a top-down pathway with lateral connections."),
    ),
    takeaways = listOf(
        "One pass like YOLO, but with detection heads on six feature maps instead of one.",
        "8,732 default boxes for SSD300, summed from the level table — two thirds from the finest map alone.",
        "Default boxes are anchors: fixed shapes per location, each predicting a class distribution and an offset.",
        "74.3% mAP at 59 fps on VOC07, beating both YOLO v1's accuracy and Faster R-CNN's speed.",
        "Hard negative mining at 3:1 keeps the imbalance manageable by discarding most negatives — the heuristic RetinaNet replaces with focal loss.",
    ),
    crossLinks = listOf(
        CrossLink("yolo", "YOLO"),
        CrossLink("retinanet", "RetinaNet"),
        CrossLink("faster_rcnn", "Faster R-CNN"),
        CrossLink("mobilenet", "MobileNet"),
    ),
)
