package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val f1ScoreContent = TopicContent(
    topicId = "f1_score",
    whatIsIt = listOf(
        "F1 is the harmonic mean of precision and recall: 2PR / (P + R). The harmonic part is the whole design. An arithmetic mean lets a model score 0.5 by being perfect at one and hopeless at the other; the harmonic mean is dragged towards the smaller of the two, so both have to be decent. On the lab's matrix at t = 0.5 — precision 1.000, recall 0.398 — the arithmetic mean is 0.699 and F1 is 0.569.",
        "Like everything built on a confusion matrix, F1 depends on the threshold, and the default is rarely the best one. Sweeping every threshold on the same score vector, F1 peaks at t = 0.34 with 0.776 against 0.569 at t = 0.5. Nothing about the model changed — 0.5 was simply the wrong place to cut it, and a report quoting F1 at the default is quoting a number the model can beat by 20 points for free.",
        "Two things F1 hides. It is a function of TP, FP and FN only — the 912 true negatives are invisible to it — which makes it right when the positive class is the subject and wrong when both classes matter, where accuracy or kappa belong. And its 1:1 weighting of precision and recall is a choice you probably did not make deliberately: Fβ generalises it, with β = 2 weighting recall twice as heavily (0.735 here) and β = 0.5 weighting precision (0.822). Pick β from the cost ratio rather than defaulting to 1.",
    ),
    steps = listOf(
        StepCard(1, "Take Both Metrics", "Precision and recall at one threshold.", 0xFF0EA5E9),
        StepCard(2, "Use the Harmonic Mean", "2PR / (P + R) — pulled towards the smaller.", 0xFF8B5CF6),
        StepCard(3, "Compare the Means", "0.699 arithmetic against 0.569 harmonic.", 0xFF3B82F6),
        StepCard(4, "Sweep the Threshold", "F1 peaks at t = 0.34 with 0.776.", 0xFFF59E0B),
        StepCard(5, "Notice the Missing Cell", "TN is not in the formula at all.", 0xFFEC4899),
        StepCard(6, "Choose β", "F2 = 0.735, F0.5 = 0.822 — from the cost ratio.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("F1", "2PR / (P + R)", "The harmonic mean of precision and recall."),
        FormulaEntry("In cells", "2TP / (2TP + FP + FN)", "TN does not appear."),
        FormulaEntry("At t = 0.5", "0.569", "Against an arithmetic mean of 0.699."),
        FormulaEntry("At the best threshold", "0.776 at t = 0.34", "Same model, same scores."),
        FormulaEntry("Fβ", "(1 + β²)PR / (β²P + R)", "β > 1 favours recall, β < 1 favours precision."),
        FormulaEntry("Measured Fβ", "F2 0.735 · F0.5 0.822", "At the F1-optimal threshold."),
    ),
    notationKey = listOf(
        NotationEntry("harmonic mean", "the mean that punishes imbalance between its arguments"),
        NotationEntry("β", "the weight on recall relative to precision; 1 by default"),
        NotationEntry("macro F1", "the unweighted mean of per-class F1 — treats rare classes equally"),
        NotationEntry("micro F1", "F1 over pooled counts — equals accuracy in single-label problems"),
        NotationEntry("threshold", "what F1 is silently a function of"),
        NotationEntry("TN", "the cell F1 ignores by design"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tune the threshold, then report F1",
            accentColor = 0xFF8B5CF6,
            code = """
                import numpy as np
                from sklearn.metrics import f1_score

                grid = np.linspace(0.01, 0.99, 99)
                scores = [f1_score(y_test, probabilities >= t) for t in grid]
                best = grid[int(np.argmax(scores))]

                print(f"F1 at 0.5:  {f1_score(y_test, probabilities >= 0.5):.3f}")
                print(f"F1 at {best:.2f}: {max(scores):.3f}")
                # F1 at 0.5:  0.569
                # F1 at 0.34: 0.776
                #
                # Choose the threshold on validation data, not on the test set -- otherwise this
                # 20-point gain is partly a measurement of the test set. The gain is real; measuring
                # it on the data you tuned on is not.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Fβ, and the averaging choice for multi-class",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.metrics import fbeta_score, f1_score

                # Recall matters twice as much as precision (a miss costs ~4x a false alarm):
                print(fbeta_score(y_test, predicted, beta=2))     # 0.735

                # Multi-class averaging is a substantive choice, not a formatting one:
                f1_score(y_true, y_pred, average="macro")     # unweighted mean over classes
                f1_score(y_true, y_pred, average="weighted")  # weighted by class support
                f1_score(y_true, y_pred, average="micro")     # pooled counts = accuracy here
                #
                # "macro" is the one that notices a rare class being handled badly; "weighted" hides
                # it by construction, since the rare class gets a small weight. Papers reporting
                # "F1" without saying which are reporting one of three different numbers.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF8B5CF6, "Imbalanced Classification", "The standard single-number summary when positives are rare."),
        ApplicationCard("browser", 0xFF3B82F6, "NER & Tagging", "Span-level F1 is the field's default metric."),
        ApplicationCard("search", 0xFF0EA5E9, "Retrieval", "Balances the two things a results page trades off."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Both classes matter, or the errors cost differently — use kappa or Fβ."),
    ),
    takeaways = listOf(
        "The harmonic mean of precision and recall, so a model cannot score well by abandoning one.",
        "At t = 0.5: precision 1.000, recall 0.398 → arithmetic mean 0.699, F1 0.569.",
        "F1 peaks at t = 0.34 with 0.776 — 20 points for moving a threshold, not retraining.",
        "It is 2TP / (2TP + FP + FN): the 912 true negatives are invisible to it.",
        "That makes it right for a rare positive class and wrong when both classes matter.",
        "Its 1:1 weighting is a default, not a finding — Fβ encodes the cost ratio instead.",
        "For multi-class, macro / weighted / micro are three different numbers; say which.",
    ),
    crossLinks = listOf(
        CrossLink("precision_recall", "Precision & Recall"),
        CrossLink("confusion_matrix", "Confusion Matrix"),
        CrossLink("cohens_kappa", "Cohen's Kappa"),
        CrossLink("accuracy", "Accuracy"),
        CrossLink("ner", "Named Entity Recognition"),
    ),
)
