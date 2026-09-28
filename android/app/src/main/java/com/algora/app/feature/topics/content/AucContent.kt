package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val aucContent = TopicContent(
    topicId = "auc",
    whatIsIt = listOf(
        "AUC has two definitions that happen to be the same number. Geometrically it's the trapezoid area under the ROC curve. Probabilistically it's the chance a randomly chosen positive scores higher than a randomly chosen negative. On the lab's model both give 0.9692 — not approximately, but the same statistic computed two ways, one by integration and one by counting pairwise wins.",
        "AUC is a pure ranking measure: it only asks whether positives tend to outscore negatives, never whether the scores themselves mean anything as probabilities. That is demonstrable directly. Push every score in the lab's model toward 0 or 1 with a monotone transform — the model becomes wildly overconfident — and because the transform preserves order, AUC is unchanged at 0.9692 to four digits. But log loss on the same pair of models moves from 0.1750 to 0.2876, and Brier score from 0.0437 to 0.0556: both notice the miscalibration AUC cannot see by construction.",
        "That makes AUC the right question when the deployment only needs a ranking — fraud queues sorted by risk, search results sorted by relevance — and the wrong one whenever the actual probability matters, such as when a downstream system multiplies the score by a dollar amount. A model can have excellent AUC and be useless for that second job.",
    ),
    steps = listOf(
        StepCard(1, "Take the ROC Curve", "TPR vs FPR at every threshold.", 0xFF0EA5E9),
        StepCard(2, "Integrate It", "Trapezoid area: 0.9692.", 0xFF3B82F6),
        StepCard(3, "Or Count Pairs Instead", "P(random positive > random negative): 0.9692.", 0xFF8B5CF6),
        StepCard(4, "Distort the Scores", "Push them toward 0/1 with a monotone transform.", 0xFFF59E0B),
        StepCard(5, "Watch AUC Not Move", "Still 0.9692 — ranking is unchanged.", 0xFFEC4899),
        StepCard(6, "Watch Log Loss Move", "0.1750 → 0.2876 — the miscalibration AUC missed.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Geometric", "∫ ROC curve", "Trapezoid area — measured 0.9692."),
        FormulaEntry("Probabilistic", "P(score⁺ > score⁻)", "Same value, measured by pairwise comparison — 0.9692."),
        FormulaEntry("Range", "0.5 random · 1.0 perfect", "Below 0.5 means the ranking is inverted."),
        FormulaEntry("Overconfident AUC", "0.9692 (unchanged)", "Monotone transform preserves rank."),
        FormulaEntry("Overconfident log loss", "0.1750 → 0.2876", "What AUC's own definition cannot register."),
        FormulaEntry("Overconfident Brier", "0.0437 → 0.0556", "A gentler probability metric, still moved."),
    ),
    notationKey = listOf(
        NotationEntry("AUC", "area under the ROC curve"),
        NotationEntry("ranking metric", "cares about order, not magnitude"),
        NotationEntry("calibration", "whether a stated probability matches the observed rate"),
        NotationEntry("monotone transform", "a reshaping that preserves order — e.g. squashing scores toward 0/1"),
        NotationEntry("Mann-Whitney U", "the statistical test AUC is equivalent to"),
        NotationEntry("log loss", "the probability metric that catches what AUC can't — see that topic"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two definitions, one number",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import roc_auc_score
                from scipy.stats import mannwhitneyu

                auc_trapezoid = roc_auc_score(y_test, probabilities)  # 0.9692

                pos = probabilities[y_test == 1]
                neg = probabilities[y_test == 0]
                u_stat, _ = mannwhitneyu(pos, neg)
                auc_by_ranking = u_stat / (len(pos) * len(neg))         # 0.9692

                print(f"trapezoid {auc_trapezoid:.4f}  ranking {auc_by_ranking:.4f}")
                # These are not two approximations of the same idea -- they are the same statistic.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Break calibration without touching AUC",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.metrics import roc_auc_score, log_loss, brier_score_loss

                # Any strictly increasing function preserves every pairwise ordering.
                overconfident = np.where(probabilities >= 0.5,
                                          0.5 + (probabilities - 0.5) * 1.98,
                                          probabilities * 0.02)

                print(roc_auc_score(y_test, probabilities), roc_auc_score(y_test, overconfident))
                # 0.9692 0.9692 -- identical to 4 decimals

                print(log_loss(y_test, probabilities), log_loss(y_test, overconfident))
                # 0.1750 0.2876 -- this is what changed
                print(brier_score_loss(y_test, probabilities), brier_score_loss(y_test, overconfident))
                # 0.0437 0.0556
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Ranking Tasks", "Fraud queues, search relevance — order is all that's needed."),
        ApplicationCard("check", 0xFF3B82F6, "Model Comparison", "A single number that survives a monotone recalibration step."),
        ApplicationCard("finance", 0xFF8B5CF6, "Cost Models", "Where the score itself gets multiplied by a dollar amount, AUC is the wrong metric — use log loss."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Reporting a calibrated probability to a human or a downstream formula."),
    ),
    takeaways = listOf(
        "Two definitions — trapezoid area and pairwise win probability — that are one statistic: 0.9692.",
        "0.5 is random, 1.0 is perfect separation; below 0.5 means the ranking is backwards.",
        "AUC is a pure ranking measure: it depends only on order, never on the score's magnitude.",
        "A monotone transform that badly miscalibrates the model leaves AUC at 0.9692, unmoved.",
        "The same transform moves log loss from 0.1750 to 0.2876 and Brier from 0.0437 to 0.0556.",
        "Blind to calibration by design — pick it when only the ranking matters, not the probability.",
        "Report log loss or Brier alongside AUC whenever the raw score gets used downstream.",
    ),
    crossLinks = listOf(
        CrossLink("roc_curve", "ROC Curve"),
        CrossLink("log_loss", "Log Loss (Cross-Entropy)"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
