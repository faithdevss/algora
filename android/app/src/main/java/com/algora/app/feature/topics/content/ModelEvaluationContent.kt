package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val modelEvaluationContent = TopicContent(
    topicId = "model_evaluation",
    whatIsIt = listOf(
        "Model evaluation is the discipline of measuring a classifier honestly: on data it has never seen, with a metric that matches what the mistakes actually cost.",
        "Accuracy hides the interesting cases — a detector for a condition affecting 1% of people is 99% accurate while never detecting anything. Precision, recall, F1 and ROC-AUC each expose a different face of the confusion matrix, and the decision threshold that produces that matrix is itself a choice.",
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
    ),
    crossLinks = listOf(
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
    ),
)
