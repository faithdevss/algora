package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val retinaNetContent = TopicContent(
    topicId = "retinanet",
    figure = Figure(
        caption = "One factor, drawn. Cross-entropy is −log(p_t) and focal loss multiplies it by " +
            "(1 − p_t)^γ, so the two curves are identical in shape and differ only in how fast " +
            "they fall as the model gets an example right. At p_t = 0.9 — an anchor the model has " +
            "already learned — CE still charges 0.105 while focal loss charges 0.001, a factor of " +
            "0.01. At p_t = 0.1, the hard case, the factor is 0.81 and almost nothing is taken " +
            "away. That gap is the whole mechanism, and on the lab's 100,000 background anchors " +
            "at 0.9 against 10 foreground at 0.1 it moves the loss split from 10,536-against-23 " +
            "to 105-against-19: a background-to-foreground ratio of 458:1 becoming 5.6:1, an 81× " +
            "rebalance. Nothing is thrown away — the easy examples are still in the sum, which is " +
            "what separates this from hard negative mining.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "cross-entropy",
                    listOf(
                        FigurePoint(0.000f, 0.966f), FigurePoint(0.053f, 0.743f),
                        FigurePoint(0.106f, 0.612f), FigurePoint(0.160f, 0.519f),
                        FigurePoint(0.266f, 0.388f), FigurePoint(0.372f, 0.296f),
                        FigurePoint(0.479f, 0.224f), FigurePoint(0.585f, 0.165f),
                        FigurePoint(0.691f, 0.115f), FigurePoint(0.798f, 0.072f),
                        FigurePoint(0.904f, 0.034f), FigurePoint(0.957f, 0.017f),
                        FigurePoint(1.000f, 0.003f),
                    ),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    "focal, γ = 2",
                    listOf(
                        FigurePoint(0.000f, 0.872f), FigurePoint(0.053f, 0.602f),
                        FigurePoint(0.106f, 0.442f), FigurePoint(0.160f, 0.332f),
                        FigurePoint(0.266f, 0.190f), FigurePoint(0.372f, 0.106f),
                        FigurePoint(0.479f, 0.056f), FigurePoint(0.585f, 0.026f),
                        FigurePoint(0.691f, 0.010f), FigurePoint(0.798f, 0.003f),
                        FigurePoint(0.904f, 0.000f), FigurePoint(0.957f, 0.000f),
                        FigurePoint(1.000f, 0.000f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.053f, 0.743f, "hard · ×0.81", FigureTone.Warn),
                FigurePoint(0.904f, 0.034f, "easy · ×0.01"),
            ),
            xLabel = "p_t, 0.05 → 0.99",
            yLabel = "loss, 0 → 3.1",
        ),
    ),
    whatIsIt = listOf(
        "By 2017 the pattern was accepted as a law: two-stage detectors are accurate, one-stage detectors are fast, and you choose. RetinaNet's paper argues the gap was never architectural — it was the loss. A one-stage detector scores every anchor on the whole pyramid, roughly 100,000 per image, and essentially all of them are background. A two-stage detector never faces that, because its proposal step has already thrown away the easy negatives before the classifier sees them.",
        "Cross-entropy has no answer to that imbalance, and the simulation prices it. Take 100,000 background anchors the model already gets right at 0.9 confidence and 10 hard foreground anchors at 0.1: the background contributes 10,536 of loss against the foreground's 23 — 99.8% of the gradient comes from examples that are already correct. The model's best move is to predict background everywhere, and that is exactly what an untreated one-stage detector does.",
        "Focal loss multiplies each example's loss by (1 − p_t)^γ. At γ = 2 an anchor the model is 90% sure about is scaled by 0.01 while one it is 10% sure about keeps 0.81 of its loss. On the same 100,010 anchors the split becomes 105 against 19, and the background-to-foreground ratio falls from 458:1 to 5.6:1 — an 81× rebalance from one factor in the loss. Nothing is discarded, unlike SSD's hard negative mining; the easy examples are down-weighted smoothly and still contribute. With that plus a ResNet-FPN backbone and two small subnets, a one-stage detector reached 39.1 AP on COCO, above every published Faster R-CNN variant at the time.",
    ),
    steps = listOf(
        StepCard(1, "Count the Anchors", "Five pyramid levels × 9 anchors ≈ 120,000 per image.", 0xFF3B82F6),
        StepCard(2, "Notice What They Are", "Two are on objects. The rest is background the model finds easy.", 0xFF06B6D4),
        StepCard(3, "Price Cross-Entropy", "99.8% of the loss comes from anchors already classified correctly.", 0xFF6366F1),
        StepCard(4, "Add the Focal Term", "×(1 − p_t)^γ: 0.01 for an easy example, 0.81 for a hard one.", 0xFF8B5CF6),
        StepCard(5, "Re-price It", "Background:foreground drops 458:1 → 5.6:1, an 81× rebalance.", 0xFFF59E0B),
        StepCard(6, "Initialise the Bias", "Start with π = 0.01 prior, or the first epoch diverges.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Cross-entropy", "CE(p_t) = −log(p_t)", "0.105 for a correct-at-0.9 example, and there are 100,000 of them."),
        FormulaEntry("Focal loss", "FL(p_t) = −α(1 − p_t)^γ log(p_t)", "γ = 2, α = 0.25 in the paper."),
        FormulaEntry("Focal weight", "(1 − p_t)^γ", "0.01 at p = 0.9, 0.81 at p = 0.1 with γ = 2."),
        FormulaEntry("Measured split (CE)", "10,536 background vs 23 foreground", "99.8% of the gradient from already-correct examples."),
        FormulaEntry("Measured split (FL)", "105 vs 19 — ratio 458:1 → 5.6:1", "An 81× rebalance from one factor."),
        FormulaEntry("Prior initialisation", "b = −log((1 − π)/π), π = 0.01", "Without it the initial loss from 100k negatives destabilises training."),
    ),
    notationKey = listOf(
        NotationEntry("p_t", "the probability assigned to the *correct* class — p for positives, 1 − p for negatives"),
        NotationEntry("γ (gamma)", "the focusing parameter; 0 recovers cross-entropy, 2 is the paper's choice"),
        NotationEntry("α (alpha)", "the ordinary class-weighting term, kept alongside the focal term"),
        NotationEntry("FPN", "feature pyramid network — top-down pathway with lateral connections, P3–P7 here"),
        NotationEntry("class imbalance", "the foreground-background ratio a one-stage detector faces at every anchor"),
        NotationEntry("subnet", "the small shared conv head applied identically to every pyramid level"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The imbalance, and what one factor does to it",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np

                def split(n_bg, p_bg, n_fg, p_fg, gamma=0.0):
                    w = lambda p: (1 - p) ** gamma
                    bg = n_bg * w(p_bg) * -np.log(p_bg)
                    fg = n_fg * w(p_fg) * -np.log(p_fg)
                    return bg, fg, bg / fg

                print(split(100_000, 0.9, 10, 0.1))            # (10536, 23.0, 457.6)  cross-entropy
                print(split(100_000, 0.9, 10, 0.1, gamma=2))   # (105.4, 18.7, 5.65)   focal

                print(457.6 / 5.65)                            # 81x rebalance

                # Note what did NOT happen: no example was dropped, no ratio was imposed, no
                # threshold was chosen. The 100,000 easy negatives still contribute -- 105 units of
                # loss between them, which is roughly the right amount of attention for examples the
                # model already has right.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Implementing it, and the initialisation nobody mentions",
            accentColor = 0xFFEC4899,
            code = """
                import torch, torch.nn.functional as F

                def focal_loss(logits, targets, alpha=0.25, gamma=2.0):
                    p = torch.sigmoid(logits)
                    ce = F.binary_cross_entropy_with_logits(logits, targets, reduction='none')
                    p_t = p * targets + (1 - p) * (1 - targets)
                    loss = ce * ((1 - p_t) ** gamma)
                    a_t = alpha * targets + (1 - alpha) * (1 - targets)
                    return (a_t * loss).sum() / targets.sum().clamp(min=1)   # normalise by POSITIVES

                # Two details that decide whether this trains at all:
                #  - normalise by the number of positive anchors, not by the total. Dividing by
                #    100,000 makes the gradient vanish.
                #  - initialise the classification head's final bias to -log((1 - 0.01)/0.01) so the
                #    model starts out predicting "background" with 99% confidence. Without it the
                #    first iteration's loss over 100k negatives is large enough to diverge.
                prior = 0.01
                bias = -torch.log(torch.tensor((1 - prior) / prior))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.FeatureMapPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF3B82F6, "Dense Detection", "The default one-stage loss; FCOS, later YOLO versions (v8's DFL) and most modern dense detectors use focal loss or a descendant (YOLOv3 tried it and lost about 2 mAP)."),
        ApplicationCard("flask", 0xFF06B6D4, "Rare-Event Classification", "Fraud, defects and disease screening face the same imbalance — the loss transfers directly out of detection."),
        ApplicationCard("search", 0xFF8B5CF6, "Segmentation", "Focal loss is standard for pixel-level tasks where the object occupies a small fraction of the image."),
        ApplicationCard("bulb", 0xFFF59E0B, "Loss Design", "The general lesson: an imbalance heuristic in the training loop is often a missing term in the loss."),
    ),
    takeaways = listOf(
        "The one-stage accuracy gap was a class-imbalance problem, not an architectural one.",
        "A one-stage detector scores ~100k anchors per image and nearly all of them are easy background.",
        "Under cross-entropy 99.8% of the loss comes from already-correct examples — measured, not asserted.",
        "(1 − p_t)^γ down-weights the easy ones smoothly: the background:foreground ratio falls 458:1 → 5.6:1.",
        "Nothing is discarded, unlike hard negative mining — and the classification bias must be initialised to a 0.01 prior or training diverges.",
    ),
    crossLinks = listOf(
        CrossLink("ssd", "SSD"),
        CrossLink("yolo", "YOLO"),
        CrossLink("resnet", "ResNet"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
