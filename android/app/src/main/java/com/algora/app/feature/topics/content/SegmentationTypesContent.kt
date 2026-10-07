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

internal val segmentationTypesContent = TopicContent(
    topicId = "segmentation_types",
    figure = Figure(
        caption = "Two sheep touching — the case that separates the two tasks. A semantic head has " +
            "one channel per class and none per object, so every lit cell here is the same label " +
            "and the whole thing is one connected region: no output it can produce would split " +
            "them. The instance map assigns the two colours. That is why the metrics are not " +
            "comparable either — the lab's semantic prediction drops the sheep's bottom row and " +
            "still scores 0.917 mean IoU, a good result, while merging two sheep into one costs " +
            "instance segmentation a false negative outright under mask AP. If the application " +
            "counts, grasps or tracks objects, a semantic map cannot answer it however accurate " +
            "it is. Panoptic segmentation is the task that demands both at once.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("S", "S", "·", "·", "S", "S"),
                listOf("S", "S", "S", "S", "S", "S"),
                listOf("S", "S", "S", "S", "S", "S"),
                listOf("S", "·", "·", "·", "·", "S"),
            ),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Primary), FigureCell(0, 1, FigureTone.Primary),
                FigureCell(1, 0, FigureTone.Primary), FigureCell(1, 1, FigureTone.Primary),
                FigureCell(1, 2, FigureTone.Primary),
                FigureCell(2, 0, FigureTone.Primary), FigureCell(2, 1, FigureTone.Primary),
                FigureCell(2, 2, FigureTone.Primary),
                FigureCell(3, 0, FigureTone.Primary),
                FigureCell(0, 4, FigureTone.Accent), FigureCell(0, 5, FigureTone.Accent),
                FigureCell(1, 3, FigureTone.Accent), FigureCell(1, 4, FigureTone.Accent),
                FigureCell(1, 5, FigureTone.Accent),
                FigureCell(2, 3, FigureTone.Accent), FigureCell(2, 4, FigureTone.Accent),
                FigureCell(2, 5, FigureTone.Accent),
                FigureCell(3, 5, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Four tasks sit on the same photograph and answer different questions. Classification: what is in this image. Detection: where, as boxes. Semantic segmentation: a class label for every pixel. Instance segmentation: a class label for every pixel *and* which object each pixel belongs to. The words are used loosely in conversation and mean precisely different things in a spec, a dataset and a metric.",
        "The distinction is not academic, and it shows up the moment two objects of the same class touch. In the simulation two sheep standing shoulder to shoulder are, semantically, one connected region of 54 pixels — there is no label that could separate them, because a semantic output has one channel per class and none per object. The instance map on the same picture reports three objects. If your application counts things, picks one thing up, or tracks a thing across frames, a semantic map cannot answer it no matter how accurate it is.",
        "So they are evaluated differently too, and the metrics are not comparable. Semantic segmentation reports mean IoU per class — the lab's prediction, which drops the sheep's bottom row, scores 0.917 and is a perfectly good result. Instance segmentation uses detection's average precision computed over masks, where merging two sheep into one costs a false negative outright rather than a few pixels of IoU. Panoptic segmentation is the task that demands both at once: every pixel labelled, every countable object separated, no overlaps allowed, scored by panoptic quality. Architecturally the split is just as clean — semantic segmentation is a dense per-pixel classifier (FCN, U-Net, DeepLab), instance segmentation is detection with a mask head (Mask R-CNN), and the modern query-based models (Mask2Former, SAM) are attempts to serve all of it from one design.",
    ),
    steps = listOf(
        StepCard(1, "Classify", "One label for the whole image. No location at all.", 0xFF3B82F6),
        StepCard(2, "Detect", "A box and a class per object. Location, coarsely.", 0xFF06B6D4),
        StepCard(3, "Segment Semantically", "A class per pixel — but touching objects of one class merge.", 0xFF6366F1),
        StepCard(4, "Segment by Instance", "A mask per object. Two sheep are two answers.", 0xFF8B5CF6),
        StepCard(5, "Pick the Metric to Match", "mIoU for classes, mask AP for objects, PQ for both.", 0xFFF59E0B),
        StepCard(6, "Note Stuff vs Things", "Sky and road cannot be counted; sheep and cars can.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Per-class IoU", "|P ∩ G| / |P ∪ G| for one class", "Computed over pixels, not boxes."),
        FormulaEntry("Mean IoU", "average of per-class IoU over classes present", "0.917 for the lab's prediction."),
        FormulaEntry("Pixel accuracy", "correct pixels / total pixels", "Misleading when one class dominates — a 95% background image scores 95% by predicting nothing."),
        FormulaEntry("Mask AP", "detection AP with IoU computed over masks", "Merging two objects costs a whole false negative."),
        FormulaEntry("Dice / F1", "2|P ∩ G| / (|P| + |G|)", "The medical-imaging convention; monotonically related to IoU but not equal to it."),
        FormulaEntry("Panoptic quality", "PQ = (Σ IoU over matched segments) / (TP + ½FP + ½FN)", "Segmentation quality × recognition quality, in one number."),
    ),
    notationKey = listOf(
        NotationEntry("semantic segmentation", "class per pixel; countable objects of one class are not separated"),
        NotationEntry("instance segmentation", "one mask per object; background \"stuff\" is usually ignored"),
        NotationEntry("panoptic segmentation", "both, with no overlapping labels allowed"),
        NotationEntry("stuff vs things", "amorphous regions (sky, grass) vs countable objects (sheep, cars)"),
        NotationEntry("mIoU", "the semantic metric: per-class IoU averaged over classes"),
        NotationEntry("PQ", "panoptic quality — the metric that penalises merged instances and mislabelled stuff together"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The same picture under both label spaces",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np
                from scipy.ndimage import label

                semantic = np.zeros((12, 12), int)
                semantic[3:9, 1:10] = 1        # two sheep, touching
                semantic[1:5, 10:12] = 2       # a tree

                instance = np.zeros((12, 12), int)
                instance[3:9, 1:5] = 1
                instance[3:9, 5:10] = 2
                instance[1:5, 10:12] = 3

                print(len(np.unique(semantic)) - 1)          # 2 classes
                print(label(semantic == 1)[1])               # 1 connected sheep region
                print(len(np.unique(instance)) - 1)          # 3 objects

                # No post-processing recovers the second sheep from `semantic`: connected components
                # sees one blob because there is one blob. The information was never in the output
                # space -- which is why "just run connected components on the semantic map" fails on
                # exactly the images where it matters.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Metrics that do not compare, and one that hides the problem",
            accentColor = 0xFFF59E0B,
            code = """
                import numpy as np

                def iou(p, g, c):
                    return ((p == c) & (g == c)).sum() / (((p == c) | (g == c)).sum() or 1)

                truth = semantic.copy()
                pred = semantic.copy(); pred[8, 1:10] = 0     # drop the sheep's bottom row

                print(np.mean([iou(pred, truth, c) for c in (1, 2)]))   # 0.917 -- a good result
                print((pred == truth).mean())                            # 0.938 pixel accuracy

                # Pixel accuracy is the trap: predict "background" for everything in this image and
                # it still scores 0.57, because background is most of the pixels. mIoU is the
                # semantic default for that reason.
                #
                # And note what neither number can see: a prediction that merges the two sheep into
                # one region scores 1.000 mIoU. Under mask AP it loses an object outright. Choose the
                # metric from the question, not from the architecture.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF3B82F6, "Autonomous Driving", "Drivable surface is semantic; each pedestrian and vehicle must be an instance, because they are tracked and predicted individually."),
        ApplicationCard("flask", 0xFF06B6D4, "Cell Counting", "Counting is instance segmentation by definition — a semantic mask of touching cells reports one cell."),
        ApplicationCard("globe", 0xFF8B5CF6, "Land Cover Mapping", "Forest and water are stuff, not things: semantic segmentation is the correct task and instances would be meaningless."),
        ApplicationCard("browser", 0xFFF59E0B, "Photo Editing", "\"Select this person\" is instance; \"replace the sky\" is semantic. The UI verb tells you which model to run."),
    ),
    takeaways = listOf(
        "Semantic segmentation labels pixels by class; instance segmentation also says which object each pixel belongs to.",
        "Two touching objects of one class are one semantic region and two instances — measured here as 2 regions vs 3 objects.",
        "No post-processing recovers instances from a semantic map: the distinction was never in the output space.",
        "Metrics do not transfer — mIoU (0.917 for the lab's prediction) rewards pixels, mask AP punishes merged objects.",
        "Panoptic segmentation demands both with no overlaps, and separates \"stuff\" that cannot be counted from \"things\" that can.",
    ),
    crossLinks = listOf(
        CrossLink("unet", "U-Net"),
        CrossLink("mask_rcnn", "Mask R-CNN"),
        CrossLink("cnn", "CNNs"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
