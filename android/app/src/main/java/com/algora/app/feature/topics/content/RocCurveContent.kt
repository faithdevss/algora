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

internal val rocCurveContent = TopicContent(
    topicId = "roc_curve",
    figure = Figure(
        caption = "The lab's ROC curve, traced end to end: one point per threshold from t = 0.95 " +
            "down to 0, joined — which is what an ROC curve is, a piecewise-linear path between the " +
            "points distinct thresholds produce. t = 0.5 sits at (0.007, 0.602): 6 false alarms in " +
            "912, and 40% of the positives missed, which is the corner a confusion matrix reports " +
            "from without saying it is a corner. Dropping to t = 0.2 buys 0.284 more recall for " +
            "0.143 false-positive rate; going on to t = 0.1 buys only 0.046 more for another 0.103. " +
            "That is about 2 points of recall per point of FPR in the first stretch and under half " +
            "a point in the second. The bend between them is the diminishing return, the area " +
            "under the whole path is the lab's AUC of 0.945, and the dashed diagonal is what a " +
            "model with no signal draws.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "no skill",
                    listOf(FigurePoint(0f, 0f), FigurePoint(1f, 1f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "the lab's model",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0f, 0.057f), FigurePoint(0f, 0.102f),
                        FigurePoint(0f, 0.136f), FigurePoint(0f, 0.216f), FigurePoint(0f, 0.295f),
                        FigurePoint(0f, 0.352f), FigurePoint(0f, 0.42f), FigurePoint(0f, 0.466f),
                        FigurePoint(0f, 0.523f), FigurePoint(0.007f, 0.602f), FigurePoint(0.03f, 0.648f),
                        FigurePoint(0.049f, 0.682f), FigurePoint(0.081f, 0.739f), FigurePoint(0.098f, 0.784f),
                        FigurePoint(0.125f, 0.83f), FigurePoint(0.15f, 0.886f), FigurePoint(0.195f, 0.92f),
                        FigurePoint(0.253f, 0.932f), FigurePoint(0.338f, 0.966f), FigurePoint(1f, 1f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.007f, 0.602f, "t = 0.5"),
                FigurePoint(0.15f, 0.886f, "t = 0.2"),
                FigurePoint(0.253f, 0.932f, "t = 0.1", FigureTone.Warn),
            ),
            xLabel = "false positive rate",
            yLabel = "true positive rate",
        ),
    ),
    whatIsIt = listOf(
        "The ROC curve plots true positive rate (recall) against false positive rate at every threshold a score can produce, instead of the one a confusion matrix freezes. It is the same lab model from Confusion Matrix, read at every cut point at once rather than at 0.5.",
        "On the lab's 1,000 cases (88 positive), sweeping the threshold traces: t = 0.5 sits at (FPR 0.007, TPR 0.602) — 6 false alarms, but 40% of the positives missed. t = 0.2 is (FPR 0.150, TPR 0.886) — a large recall gain for a small cost. t = 0.1 is (FPR 0.253, TPR 0.932) — the next 0.046 of recall costs 0.103 more false-alarm rate, where the stretch before bought 0.284 for 0.143. The curve's bend is exactly this diminishing return, drawn instead of computed by hand.",
        "A diagonal from (0,0) to (1,1) is what a coin flip produces — no separation between classes at all. The lab's curve bows toward the top-left corner, and the area under it is 0.945 against a random model's 0.5. What the curve buys over a single matrix is threshold-independence: it describes the model's ranking ability without committing to an operating point, which is also its limitation — it says nothing about which point a deployment should actually use.",
    ),
    steps = listOf(
        StepCard(1, "Fix the Model, Vary the Threshold", "Same scores, swept from 0 to 1.", 0xFF0EA5E9),
        StepCard(2, "Plot TPR vs FPR", "Recall on y, false positive rate on x, at each cut.", 0xFF3B82F6),
        StepCard(3, "Read the Corners", "(0,0) calls nothing positive; (1,1) calls everything positive.", 0xFF8B5CF6),
        StepCard(4, "Compare to the Diagonal", "The line a random model would draw.", 0xFFF59E0B),
        StepCard(5, "Notice the Bend", "t=0.2 reaches 0.886 recall at 0.150 FPR; t=0.1 pays 0.253 FPR for 0.932.", 0xFFEC4899),
        StepCard(6, "Integrate the Area", "AUC 0.945 summarizes the whole curve in one number.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("TPR (recall)", "TP / (TP + FN)", "The curve's y-axis."),
        FormulaEntry("FPR", "FP / (FP + TN) = 1 − specificity", "The curve's x-axis."),
        FormulaEntry("At t = 0.5", "(0.007, 0.602)", "6 false alarms, 40% of positives missed."),
        FormulaEntry("At t = 0.2", "(0.150, 0.886)", "The knee of the curve on this data."),
        FormulaEntry("At t = 0.1", "(0.253, 0.932)", "The last bit of recall, at steep cost."),
        FormulaEntry("Diagonal", "TPR = FPR", "What a model with no signal draws."),
    ),
    notationKey = listOf(
        NotationEntry("TPR", "true positive rate — recall, sensitivity"),
        NotationEntry("FPR", "false positive rate — one minus specificity"),
        NotationEntry("operating point", "one (FPR, TPR) pair, produced by one threshold"),
        NotationEntry("diagonal", "the no-skill baseline; AUC 0.5"),
        NotationEntry("AUC", "area under this curve — see the AUC topic"),
        NotationEntry("knee", "the point past which more recall costs disproportionately more FPR"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Trace the curve, don't just report its area",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import roc_curve, auc

                fpr, tpr, thresholds = roc_curve(y_test, probabilities)
                area = auc(fpr, tpr)
                print(f"AUC: {area:.4f}")  # 0.9446

                for t in (0.5, 0.2, 0.1):
                    idx = (abs(thresholds - t)).argmin()
                    print(f"t~{t}: FPR {fpr[idx]:.3f} TPR {tpr[idx]:.3f}")
                # t~0.5: FPR 0.007 TPR 0.602
                # t~0.2: FPR 0.150 TPR 0.886
                # t~0.1: FPR 0.253 TPR 0.932
                #
                # The curve is the confusion matrix at every threshold; a single AUC number throws
                # the operating-point choice away, which is the thing a deployment actually needs.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Base rate breaks it silently",
            accentColor = 0xFFEC4899,
            code = """
                # ROC is famously insensitive to class imbalance -- FPR is measured against the
                # negative class alone, so a rare positive class barely moves the curve even when
                # precision collapses. Precision-recall is the curve that reacts to the base rate:
                from sklearn.metrics import precision_recall_curve
                precision, recall, _ = precision_recall_curve(y_test, probabilities)
                # Same model, same scores -- but a PR curve on a 10%-positive problem looks far
                # worse than its ROC curve, because precision divides by predicted positives, which
                # are mostly wrong when positives are rare. Report PR alongside ROC on imbalanced data.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Model Comparison", "Two models with the same AUC can still trade off differently at the point you'll use."),
        ApplicationCard("target", 0xFF3B82F6, "Threshold Selection", "Pick the operating point on the curve, then freeze it."),
        ApplicationCard("flask", 0xFF8B5CF6, "Medical Screening", "A steep early rise means cheap recall before the cost climbs."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Heavily imbalanced classes — use precision-recall instead."),
    ),
    takeaways = listOf(
        "TPR against FPR at every threshold — the confusion matrix swept, not frozen at 0.5.",
        "At t = 0.5 the operating point is (0.007, 0.602); at t = 0.2 it's (0.150, 0.886).",
        "The curve's bend is diminishing returns made visible: t = 0.1 pays 0.103 more FPR for 0.046 more recall.",
        "The diagonal is the no-skill baseline; this model's curve bows well above it.",
        "AUC (0.945) integrates the whole curve, at the cost of naming no operating point.",
        "Threshold-free by construction, which is also what it cannot see: no single decision.",
        "Insensitive to the base rate — use precision-recall when positives are rare.",
    ),
    crossLinks = listOf(
        CrossLink("confusion_matrix", "Confusion Matrix"),
        CrossLink("auc", "AUC Score"),
        CrossLink("precision_recall", "Precision & Recall"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
