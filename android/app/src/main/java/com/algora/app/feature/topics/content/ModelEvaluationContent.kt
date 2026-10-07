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

internal val modelEvaluationContent = TopicContent(
    topicId = "model_evaluation",
    figure = Figure(
        caption = "The page's lab at its default threshold, t = 0.72, as a confusion matrix of 18 " +
            "cases. Every case the model flags is truly positive — 3 flagged, 0 false alarms — so " +
            "precision is 1.00. But 6 of the 9 real positives fall below the threshold and are " +
            "missed, so recall is 0.33. F1, the harmonic mean of the two, is 0.50, and accuracy is " +
            "12 of 18, 0.67. Whether this is a good operating point is not in the matrix: it is the " +
            "right trade for a spam filter, where a false alarm throws away real mail, and a " +
            "terrible one for disease screening, where the six misses are the expensive cells. " +
            "Lowering the threshold moves cases from the missed cell to the caught one, and starts " +
            "filling the false-alarm cell.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("3 caught", "6 missed"),
                listOf("0 false alarms", "9 correct"),
            ),
            rowHeaders = listOf("actually positive", "actually negative"),
            colHeaders = listOf("flagged", "not flagged"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Model evaluation is choosing the number that measures what you actually care about. A classifier that outputs scores becomes a set of decisions only once a threshold is chosen, and every threshold trades one kind of error for another: raise it and the model flags fewer cases, so the ones it flags are more often right but more true cases are missed.",
        "The lab's default threshold makes the trade concrete. At t = 0.72 every case the model flags is truly positive — precision 1.00 — but 6 of the 9 positives are missed, so recall is 0.33. F1, the harmonic mean of the two, is 0.50, and plain accuracy is 0.67. Whether that is a good operating point depends entirely on the costs: it suits a spam filter, where a false positive throws away real mail, and would be disastrous for disease screening, where a missed case is the expensive error.",
        "So evaluation starts from the cost of each mistake, not from a favourite metric. Accuracy hides imbalance; precision and recall each tell half the story; F1 weights them equally, which is a choice; ROC and AUC summarise the whole range of thresholds without choosing one. And whatever the metric, it has to be measured on data the model never trained or tuned on — a held-out test set or cross-validation — or it measures memory rather than performance.",
    ),
    steps = listOf(
        StepCard(1, "Hold Out Honestly", "Split train / validation / test, and split before any preprocessing is fitted.", 0xFF6366F1),
        StepCard(2, "Cross-Validate", "k-fold averages over k train/test splits so the estimate does not hinge on one lucky partition.", 0xFF3B82F6),
        StepCard(3, "Build the Confusion Matrix", "TP, FP, FN, TN at a chosen threshold — every classification metric is a ratio of these four.", 0xFFF59E0B),
        StepCard(4, "Choose the Metric by Cost", "Recall when misses are expensive, precision when false alarms are, F1 when both matter.", 0xFF10B981),
        StepCard(5, "Sweep the Threshold", "ROC and precision-recall curves show every operating point at once; AUC summarizes the sweep.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Precision", "TP / (TP + FP)", "Of what I flagged, how much was right."),
        FormulaEntry("Recall (TPR)", "TP / (TP + FN)", "Of what was there, how much I caught."),
        FormulaEntry("F1", "2·P·R / (P + R)", "Harmonic mean — punishes a model that sacrifices one for the other."),
        FormulaEntry("ROC-AUC", "P(score of a random positive > score of a random negative)", "Threshold-free ranking quality."),
    ),
    notationKey = listOf(
        NotationEntry("TP / FP", "true positives / false positives"),
        NotationEntry("FN / TN", "false negatives / true negatives"),
        NotationEntry("threshold", "score above which a prediction is called positive"),
        NotationEntry("k-fold", "cross-validation splitting the data into k rotating folds"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Confusion matrix and the metrics on top of it",
            accentColor = 0xFF6366F1,
            code = """
                data class Confusion(val tp: Int, val fp: Int, val fn: Int, val tn: Int) {
                    val accuracy get() = (tp + tn).toDouble() / (tp + fp + fn + tn)
                    val precision get() = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
                    val recall get() = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
                    val f1 get() = if (precision + recall == 0.0) 0.0
                        else 2 * precision * recall / (precision + recall)
                }

                fun confusionAt(scores: DoubleArray, labels: IntArray, threshold: Double): Confusion {
                    var tp = 0; var fp = 0; var fn = 0; var tn = 0
                    for (i in scores.indices) {
                        val predictedPositive = scores[i] >= threshold
                        when {
                            predictedPositive && labels[i] == 1 -> tp++
                            predictedPositive && labels[i] == 0 -> fp++
                            !predictedPositive && labels[i] == 1 -> fn++
                            else -> tn++
                        }
                    }
                    return Confusion(tp, fp, fn, tn)
                }

                // AUC as the probability that a positive outranks a negative.
                fun rocAuc(scores: DoubleArray, labels: IntArray): Double {
                    val positives = scores.indices.filter { labels[it] == 1 }
                    val negatives = scores.indices.filter { labels[it] == 0 }
                    if (positives.isEmpty() || negatives.isEmpty()) return 0.5
                    var wins = 0.0
                    for (p in positives) for (n in negatives) {
                        wins += when {
                            scores[p] > scores[n] -> 1.0
                            scores[p] == scores[n] -> 0.5
                            else -> 0.0
                        }
                    }
                    return wins / (positives.size * negatives.size)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF6366F1, "Medical Screening", "Recall dominates: a missed diagnosis costs far more than a follow-up test."),
        ApplicationCard("globe", 0xFF3B82F6, "Spam & Fraud Filters", "Precision dominates: blocking a legitimate message is the expensive error."),
        ApplicationCard("chart", 0xFF10B981, "A/B Model Rollouts", "Threshold sweeps let a team pick the operating point the business actually wants."),
    ),
    takeaways = listOf(
        "Accuracy is misleading on imbalanced data — always look at the confusion matrix.",
        "Precision and recall trade off through the threshold, which is a product decision, not a modelling one.",
        "ROC-AUC is threshold-free but optimistic under heavy imbalance; prefer the precision-recall curve there.",
        "Fit scalers and encoders inside each fold — fitting them on all data leaks the test set.",
        "In the lab at threshold 0.72: precision 1.00, recall 0.33, F1 0.50, accuracy 0.67 — good for spam, bad for screening.",
    ),
    crossLinks = listOf(
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
    ),
)
