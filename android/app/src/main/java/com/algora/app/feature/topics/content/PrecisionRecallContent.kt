package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val precisionRecallContent = TopicContent(
    topicId = "precision_recall",
    figure = Figure(
        caption = "At t = 0.5 the lab's model is precise and timid: precision 1.000 and recall 0.398, " +
            "missing 53 of 88 positives. At t = 0.1, same model and same scores, it is thorough and " +
            "noisy: recall 1.000 at precision 0.213 — 79% of its flags are false alarms. Nothing " +
            "retrained; the threshold alone moved the operating point from one corner of the trade-off " +
            "to the other.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("Precision, t=0.5", 1.000f, FigureTone.Primary),
                FigureBar("Recall, t=0.5", 0.398f, FigureTone.Primary),
                FigureBar("Precision, t=0.1", 0.213f, FigureTone.Accent),
                FigureBar("Recall, t=0.1", 1.000f, FigureTone.Accent),
            ),
            yLabel = "score",
        ),
    ),
    whatIsIt = listOf(
        "Precision and recall split a model's errors into the two kinds a decision can care about. Precision is TP / (TP + FP): of everything flagged, how much was real. Recall is TP / (TP + FN): of everything real, how much was found. Neither one mentions the true negatives, which is what makes them the right pair when the positive class is the subject and the negative class is background.",
        "The trade is not a subtlety, it is the entire behaviour. At t = 0.5 the lab's model has precision 1.000 and recall 0.398 — everything it flags is real, and it misses 53 of 88 positives. Drop the threshold to 0.1 and it has recall 1.000 at precision 0.213, so 79% of the flags are false alarms. Same model, same scores, nothing retrained: the threshold is a business decision about which error hurts more, and reporting one number without the other hides which decision was made.",
        "Both metrics have a blind spot worth naming precisely: neither uses TN, so neither can see how many true negatives there are. Recall is entirely independent of the negative class. Precision is not: false positives scale with the number of negatives (FP = FPR × N_neg), so ten times as many negatives at the same false-positive rate means ten times the false alarms and a much lower precision. That is why precision-recall curves react to imbalance. It is also why they are always quoted as a pair, always with the threshold, and usually alongside a curve rather than a point.",
    ),
    steps = listOf(
        StepCard(1, "Split the Errors", "False alarms against misses.", 0xFF0EA5E9),
        StepCard(2, "Precision", "TP / (TP + FP) — 1.000 at t = 0.5.", 0xFF3B82F6),
        StepCard(3, "Recall", "TP / (TP + FN) — 0.398 at the same threshold.", 0xFFEC4899),
        StepCard(4, "Move the Threshold", "Recall 1.000 at precision 0.213 at t = 0.1.", 0xFFF59E0B),
        StepCard(5, "Price the Errors", "The cost ratio picks the point on the curve.", 0xFF8B5CF6),
        StepCard(6, "Quote Both", "Neither uses TN, so neither stands alone.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Precision", "TP / (TP + FP)", "1.000 at t = 0.5 on the lab's model."),
        FormulaEntry("Recall (sensitivity)", "TP / (TP + FN)", "0.398 at the same threshold."),
        FormulaEntry("At t = 0.1", "precision 0.213 · recall 1.000", "Every positive found, 325 false alarms."),
        FormulaEntry("Specificity", "TN / (TN + FP)", "Recall for the negative class — the ROC axis."),
        FormulaEntry("Neither uses", "TN", "Which is the point, and the blind spot."),
        FormulaEntry("Average precision", "0.855", "The PR curve's single-number summary."),
    ),
    notationKey = listOf(
        NotationEntry("precision", "also positive predictive value; the reliability of a flag"),
        NotationEntry("recall", "also sensitivity or true positive rate; the coverage of the positives"),
        NotationEntry("miss", "a false negative — what recall measures the absence of"),
        NotationEntry("false alarm", "a false positive — what precision measures the absence of"),
        NotationEntry("operating point", "the threshold, which fixes both numbers at once"),
        NotationEntry("average precision", "the area under the precision-recall curve"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The curve, not the point",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import precision_recall_curve, average_precision_score

                precision, recall, thresholds = precision_recall_curve(y_test, probabilities)
                print(f"average precision {average_precision_score(y_test, probabilities):.3f}")

                # Find the threshold that meets a recall requirement -- which is how this is
                # actually used in production: the requirement comes from the business, not the data.
                target_recall = 0.80
                viable = [(p, t) for p, r, t in zip(precision, recall, thresholds) if r >= target_recall]
                best_precision, threshold = max(viable)
                print(f"at recall >= {target_recall}: precision {best_precision:.3f} at t={threshold:.3f}")
                # at recall >= 0.8: precision 0.645 at t=0.216
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What each error costs, in one table",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.metrics import precision_score, recall_score

                for t in (0.1, 0.2, 0.34, 0.5, 0.7):
                    predicted = probabilities >= t
                    p = precision_score(y_test, predicted, zero_division=0)
                    r = recall_score(y_test, predicted)
                    flagged = predicted.sum()
                    print(f"t={t:<5} precision {p:.3f} recall {r:.3f} "
                          f"flagged {flagged:4d} missed {int((y_test == 1).sum() - r * (y_test == 1).sum())}")

                # Read the last two columns, not the first two: "flag 413 cases and miss none" and
                # "flag 35 and miss 53" are the sentences a reviewer or an on-call team can act on.
                # Precision and recall are the same facts in a form that is easier to compare and
                # harder to feel.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF0EA5E9, "Retrieval", "Precision@k is what a search results page is judged on."),
        ApplicationCard("flask", 0xFFEC4899, "Screening", "Recall first: a missed case costs more than a second test."),
        ApplicationCard("finance", 0xFF3B82F6, "Fraud Review", "Precision is the analyst time each flag consumes."),
        ApplicationCard("help", 0xFF10B981, "Always Paired", "One without the other is a threshold choice you hid."),
    ),
    takeaways = listOf(
        "Precision is the reliability of a flag; recall is the coverage of the positives.",
        "At t = 0.5 the lab's model is precise and timid: precision 1.000, recall 0.398, 53 misses.",
        "At t = 0.1 it is thorough and noisy: recall 1.000, precision 0.213, 325 false alarms.",
        "Same model and same scores — the threshold is a decision about which error costs more.",
        "Neither metric uses TN; recall ignores the negative class entirely, while precision still falls as negatives (and so false alarms) grow.",
        "At 80% recall the best precision available on this model is 0.645.",
        "Report both with the threshold, and prefer the curve to any single point on it.",
    ),
    crossLinks = listOf(
        CrossLink("f1_score", "F1 Score"),
        CrossLink("confusion_matrix", "Confusion Matrix"),
        CrossLink("roc_curve", "ROC Curve"),
        CrossLink("accuracy", "Accuracy"),
        CrossLink("smote", "SMOTE (Oversampling)"),
    ),
)
