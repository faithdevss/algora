package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val yoloContent = TopicContent(
    topicId = "yolo",
    whatIsIt = listOf(
        "YOLO removes the proposal stage entirely. One CNN pass produces every box for the whole image at once: the image is divided into a 7×7 grid, and the cell containing an object's centre is responsible for predicting it. Each cell emits two boxes with confidences plus one set of 20 class probabilities, so the entire output is a single 7×7×30 tensor — 1,470 numbers, and 98 boxes total against R-CNN's 2,000 proposals.",
        "Framing detection as one regression problem is what buys the speed: 45 frames per second against Faster R-CNN's 7, and 155 for the smaller Fast YOLO. The accuracy cost was real — 63.4% mAP on VOC 2007 against 73.2% — but it makes *fewer* background false positives than Fast R-CNN, because each prediction is made with the whole image in view rather than from a cropped region. Reasoning globally about the scene is a genuine advantage of the design, not a side effect.",
        "Version 1's weaknesses come straight from its grid, and every later version is an answer to them. One class vector per cell means a cell holding two different objects can only report one; two boxes per cell means small clustered objects — a flock of birds — cannot all be found; and coordinates regressed from scratch made localisation the largest error term. v2 added anchors, batch norm and a higher input resolution; v3 predicted at three scales with an FPN-style neck and replaced the class softmax with per-class sigmoids so an object can carry multiple labels; v4 and v5 were training-recipe and engineering work; v8 went anchor-free with a decoupled head. The family's constant is the shape of the answer: one pass, one tensor, boxes everywhere at once.",
    ),
    steps = listOf(
        StepCard(1, "Divide the Image", "A 7×7 grid over the whole image, fixed in advance.", 0xFF3B82F6),
        StepCard(2, "Assign by Centre", "The cell containing an object's centre is responsible for it.", 0xFF06B6D4),
        StepCard(3, "Predict Per Cell", "2 boxes × (x, y, w, h, confidence) + 20 class probabilities.", 0xFF6366F1),
        StepCard(4, "Read One Tensor", "7×7×30 = 1,470 numbers, produced by a single forward pass.", 0xFF8B5CF6),
        StepCard(5, "Score and Suppress", "Class score = confidence × class probability, then NMS over 98 boxes.", 0xFFF59E0B),
        StepCard(6, "Know the Grid's Limits", "One class per cell, two boxes per cell — the constraints v2–v8 lift.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Output tensor", "S × S × (B·5 + C)", "7 × 7 × (2·5 + 20) = 1,470 for v1."),
        FormulaEntry("Boxes predicted", "S²·B = 98", "Against R-CNN's 2,000 proposals per image."),
        FormulaEntry("Confidence", "Pr(object) × IoU(pred, truth)", "One number that means both \"is something here\" and \"how well am I boxing it\"."),
        FormulaEntry("Class-specific score", "Pr(classᵢ | object) × confidence", "The value NMS and the metric actually rank."),
        FormulaEntry("Loss weighting", "λ_coord = 5, λ_noobj = 0.5", "Most cells are empty, so their confidence term is down-weighted by hand."),
        FormulaEntry("Square-root trick", "regress √w, √h", "So a fixed error matters more on a small box than a large one."),
    ),
    notationKey = listOf(
        NotationEntry("one-stage detector", "no proposal step: boxes come straight out of the network"),
        NotationEntry("grid cell", "the S×S unit responsible for objects whose centre falls in it"),
        NotationEntry("responsible box", "the one of B predictors with the best IoU on that object during training"),
        NotationEntry("anchor-free", "v8's return to direct box regression, without reference boxes"),
        NotationEntry("decoupled head", "separate branches for classification and localisation"),
        NotationEntry("mosaic augmentation", "v4's four-images-in-one training trick, now standard across the family"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The output tensor, decoded",
            accentColor = 0xFF3B82F6,
            code = """
                import torch

                S, B, C = 7, 2, 20
                out = torch.randn(1, S, S, B * 5 + C)      # 7 x 7 x 30 = 1470 numbers
                print(out.numel())                          # 1470
                print(S * S * B)                            # 98 boxes for the whole image

                boxes = out[..., :B * 5].reshape(1, S, S, B, 5)   # x, y, w, h, confidence
                classes = out[..., B * 5:]                        # ONE class vector per cell

                # x and y are offsets within the cell, in [0, 1]; w and h are fractions of the whole
                # image. So a box can be larger than the cell that owns it -- the cell decides
                # responsibility, not extent.
                #
                # `classes` having no B dimension is v1's single sharpest limitation: both boxes in a
                # cell must agree on the class.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What changed across the versions, in one place",
            accentColor = 0xFFEC4899,
            code = """
                # v1 (2016)  7x7 grid, 2 boxes/cell, one class vector per cell.  45 fps, 63.4 mAP
                # v2 (2017)  anchors, batch norm, 416x416 input, k-means anchor shapes. 67 fps, 76.8
                # v3 (2018)  3 scales with an FPN neck, per-class sigmoids instead of softmax,
                #            Darknet-53 backbone -- the version that fixed small objects
                # v4 (2020)  CSPDarknet, mosaic augmentation, CIoU loss; a recipe paper
                # v5 (2020)  PyTorch reimplementation, auto-anchors, export tooling
                # v8 (2023)  anchor-free, decoupled head, task-aligned label assignment
                #
                # The per-class sigmoid in v3 is the one worth understanding: softmax assumes the
                # classes are mutually exclusive, and "person" and "woman" are not. Multi-label
                # detection needs independent sigmoids, and the change costs nothing.

                from ultralytics import YOLO
                model = YOLO("yolov8n.pt")
                for r in model("street.jpg"):
                    print(r.boxes.xyxy, r.boxes.conf, r.boxes.cls)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF3B82F6, "Real-Time Video", "Sports analysis, traffic monitoring and refereeing systems, where the frame rate is the requirement."),
        ApplicationCard("chip", 0xFF06B6D4, "Edge Devices", "The small variants run on phones and single-board computers, which is most of the family's deployed use."),
        ApplicationCard("robot", 0xFF8B5CF6, "Robotics", "A control loop needs detections at its own frequency; a 7 fps detector cannot close it."),
        ApplicationCard("search", 0xFFF59E0B, "Retail and Inventory", "Counting objects on shelves, where speed over many cameras beats a point of mAP."),
    ),
    takeaways = listOf(
        "One forward pass produces the whole answer: a 7×7×30 tensor, 98 boxes, no proposal stage.",
        "45 fps against Faster R-CNN's 7, at 63.4% vs 73.2% VOC07 mAP — the trade is explicit.",
        "Seeing the whole image at once means fewer background false positives than region-based detectors.",
        "v1's limits are the grid's: one class vector per cell, two boxes per cell, poor on small clustered objects.",
        "Every later version attacks that — anchors and higher resolution (v2), multi-scale prediction and per-class sigmoids (v3), recipes (v4/v5), anchor-free heads (v8).",
    ),
    crossLinks = listOf(
        CrossLink("faster_rcnn", "Faster R-CNN"),
        CrossLink("ssd", "SSD"),
        CrossLink("retinanet", "RetinaNet"),
        CrossLink("cnn", "CNNs"),
    ),
)
