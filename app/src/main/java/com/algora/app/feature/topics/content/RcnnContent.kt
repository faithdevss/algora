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

internal val rcnnContent = TopicContent(
    topicId = "rcnn",
    figure = Figure(
        caption = "Where the 47 seconds goes, and what each successor removed — the same VGG-16 " +
            "backbone in all three rows, so the column is a like-for-like measurement. R-CNN runs " +
            "the convolutional stack once per proposal, 2,000 times, with nothing shared between " +
            "regions that overlap almost entirely; that is roughly 45 of the 47 seconds, and the " +
            "other 2 are selective search. Fast R-CNN runs the stack once over the whole image and " +
            "crops features instead of pixels, which takes the network's share to 0.32 s — at " +
            "which point selective search is 87% of what is left and is the only unlearned stage " +
            "in the pipeline. Faster R-CNN learns the proposals too and lands at 0.2 s. Two orders " +
            "of magnitude, and not one of the three changed what is being computed, only how many " +
            "times.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("47", "2,000", "≈2.0 s"),
                listOf("2.3", "1", "≈2.0 s"),
                listOf("0.2", "1", "learned"),
            ),
            rowHeaders = listOf("R-CNN", "Fast", "Faster"),
            colHeaders = listOf("s / image", "conv passes", "proposals"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 2, FigureTone.Warn),
                FigureCell(2, 0, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Classification asks what is in an image. Detection asks what, where, and how many — and the number of answers is not known in advance, so it cannot be a fixed-size output layer. R-CNN's move in 2014 was to turn the problem back into classification: propose about 2,000 candidate regions with selective search, warp each to 227×227, run the CNN on it, and classify. \"Regions with CNN features\" is the whole name and the whole idea.",
        "It worked spectacularly and cost accordingly. On VOC 2012 it reached 53.3% mAP against the previous best of 35.1% from deformable part models — a jump of eighteen points in a field used to arguing over one. It also runs the convolutional stack 2,000 times per image with no computation shared between heavily overlapping regions, which is 47 seconds per image with VGG-16, and it is three models trained in three separate stages: the CNN fine-tuned for classification, a linear SVM per class trained on cached features, and a bounding-box regressor trained after that.",
        "Two pieces of machinery introduced here outlive the architecture completely, and the simulation runs both for real. Non-maximum suppression: overlapping proposals of the same object all score highly, so keep the best box and delete anything overlapping it by more than an IoU threshold. And average precision, which is why NMS is part of the score rather than tidying — a second detection of an object already found counts as a false positive. On the lab's six boxes, AP@0.5 is 0.833 with the duplicate left in and 1.000 after suppression, from identical features.",
    ),
    steps = listOf(
        StepCard(1, "Propose Regions", "Selective search merges superpixels into ~2,000 candidates. Not learned.", 0xFF3B82F6),
        StepCard(2, "Warp Each to a Fixed Size", "227×227, because the CNN behind it takes nothing else.", 0xFF06B6D4),
        StepCard(3, "Run the CNN 2,000 Times", "One forward pass per region. This is the 47 seconds.", 0xFF6366F1),
        StepCard(4, "Classify With Per-Class SVMs", "Trained separately, on features cached to disk.", 0xFF8B5CF6),
        StepCard(5, "Refine the Box", "A per-class linear regressor on the same features.", 0xFFF59E0B),
        StepCard(6, "Suppress and Score", "NMS at IoU 0.5, then average precision over what survives.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Intersection over union", "IoU = |A ∩ B| / |A ∪ B|", "The matching rule for every detection metric."),
        FormulaEntry("Non-maximum suppression", "keep argmax score; drop all IoU > τ; repeat", "τ = 0.5 conventionally."),
        FormulaEntry("Precision / recall", "P = TP/(TP+FP), R = TP/(TP+FN)", "A duplicate detection is a false positive, not a duplicate."),
        FormulaEntry("Average precision", "AP = Σ (rₙ − rₙ₋₁)·p_interp(rₙ)", "Area under the precision envelope — the VOC2010+/COCO rule."),
        FormulaEntry("Box regression target", "tₓ = (Gₓ − Pₓ)/P_w, t_w = log(G_w/P_w)", "Offsets are learned relative to the proposal, not in absolute pixels."),
        FormulaEntry("Measured on the lab's boxes", "AP 0.833 → 1.000 after NMS", "Same detector, same features, different post-processing."),
    ),
    notationKey = listOf(
        NotationEntry("selective search", "the hand-written proposal algorithm; ~2 s per image, CPU"),
        NotationEntry("region proposal", "a class-agnostic box that might contain something"),
        NotationEntry("mAP", "mean average precision — AP averaged over classes"),
        NotationEntry("IoU threshold", "how much overlap counts as the same object; 0.5 in VOC, 0.5:0.95 averaged in COCO"),
        NotationEntry("hard negative mining", "retraining on the background boxes the model gets wrong"),
        NotationEntry("warp", "resizing a proposal to the CNN's fixed input, aspect ratio and all"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "IoU and NMS, which every detector since still uses unchanged",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np

                def iou(a, b):
                    x1, y1 = max(a[0], b[0]), max(a[1], b[1])
                    x2, y2 = min(a[2], b[2]), min(a[3], b[3])
                    inter = max(0, x2 - x1) * max(0, y2 - y1)
                    area = lambda z: (z[2] - z[0]) * (z[3] - z[1])
                    return inter / (area(a) + area(b) - inter)

                def nms(boxes, scores, thr=0.5):
                    order, keep = np.argsort(scores)[::-1], []
                    while len(order):
                        i = order[0]; keep.append(i)
                        order = [j for j in order[1:] if iou(boxes[i], boxes[j]) <= thr]
                    return keep

                boxes  = [(18,44,88,148), (24,36,96,156), (112,58,178,142)]
                scores = [0.94, 0.88, 0.81]
                print(iou(boxes[0], boxes[1]))     # 0.80 -- same object, twice
                print(nms(boxes, scores))          # [0, 2]
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the duplicate costs you, in average precision",
            accentColor = 0xFF8B5CF6,
            code = """
                # Detections in descending score, each matched to an UNCLAIMED ground-truth box
                # with IoU >= 0.5. The second box on object 1 is a false positive.
                #
                #  score  match          TP  FP  recall  precision
                #  0.94   object 1        1   0    0.50     1.000
                #  0.88   object 1 taken  1   1    0.50     0.500   <- the duplicate
                #  0.81   object 2        2   1    1.00     0.667
                #  0.55   background      2   2    1.00     0.500
                #
                # AP = area under the precision envelope = 0.5*1.000 + 0.5*0.667 = 0.833
                # Run NMS first and the 0.88 box never reaches the metric:  AP = 1.000
                #
                # Note also that VOC2007's 11-point rule scores the same detections 0.848 rather
                # than 0.833. An mAP is only comparable within one protocol -- always check whether
                # a paper means VOC07, VOC10+ or COCO's average over IoU 0.5:0.95.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF3B82F6, "The Detection Baseline", "Every modern detector is a response to some part of this pipeline; reading it is how the rest make sense."),
        ApplicationCard("target", 0xFF06B6D4, "Transfer Learning in Practice", "Its features came from an ImageNet classifier fine-tuned for detection — the paper that made pretraining standard practice."),
        ApplicationCard("search", 0xFF8B5CF6, "Region-Based Reasoning", "Two-stage \"propose then verify\" still appears in 3-D detection, video and document layout analysis."),
        ApplicationCard("chart", 0xFFF59E0B, "Evaluation Protocol", "IoU, NMS and mAP as defined here are still the metrics every detection paper reports."),
    ),
    takeaways = listOf(
        "Detection is not classification because the number of outputs varies — R-CNN's fix is to propose regions and classify each one.",
        "It jumped VOC 2012 mAP from 35.1% to 53.3%, and cost 47 seconds per image with VGG-16.",
        "2,000 forward passes with no shared computation, and three models trained in three separate stages.",
        "NMS is part of the metric, not cleanup: a duplicate detection is scored as a false positive.",
        "On the lab's boxes AP@0.5 goes 0.833 → 1.000 with NMS alone — and VOC2007's older 11-point rule reports 0.848 for the same detections.",
    ),
    crossLinks = listOf(
        CrossLink("fast_rcnn", "Fast R-CNN"),
        CrossLink("cnn", "CNNs"),
        CrossLink("transfer_learning", "Transfer Learning"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
