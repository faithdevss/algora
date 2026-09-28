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

internal val accuracyContent = TopicContent(
    topicId = "accuracy",
    figure = Figure(
        caption = "The lab's model scores accuracy 0.947 at the default threshold — only 3.5 points " +
            "above 0.912, what predicting the majority class scores for free on this 8.8%-positive " +
            "data. Pushed to t = 0.99, accuracy rises to 0.921 while recall collapses to 0.011: a " +
            "higher score from a model that has stopped finding positives. On the identical matrix, " +
            "Cohen's kappa — the chance-corrected version of the same question — reads 0.546.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("Model, t=0.5", 0.947f, FigureTone.Primary),
                FigureBar("Baseline (predict −)", 0.912f, FigureTone.Muted),
                FigureBar("Model, t=0.99", 0.921f, FigureTone.Warn),
            ),
            yLabel = "accuracy",
        ),
    ),
    whatIsIt = listOf(
        "Accuracy is (TP + TN) / everything — the share of cases a model gets right. It is the metric everyone reaches for first, it is the right metric when the classes are balanced and both errors cost the same, and it is actively misleading everywhere else. The lab's model scores 0.947 at the default threshold, which sounds like a finished result.",
        "Here is the same number without a model. Predicting \"negative\" for every case scores 0.912 on this data, because 912 of the 1,000 cases are negative. The trained model's 0.947 is worth 3.5 points over answering the same way every time — and the majority-class baseline, not 0.5, is what any accuracy figure has to be read against. Push the threshold to 0.99 and accuracy goes *up* to 0.921 while the model finds almost no positives at all.",
        "The deeper problem is range. Across the lab's threshold sweep accuracy moves between 0.406 and 0.960 while recall moves between 0.000 and 1.000, and over the useful part of that range accuracy is nearly flat — a metric that barely responds to the thing you are changing cannot be used to choose an operating point. On the same matrix where accuracy reads 0.947, Cohen's kappa reads 0.546, which is the chance-corrected version of the same question and a far better summary on imbalanced data.",
    ),
    steps = listOf(
        StepCard(1, "Count the Diagonal", "(TP + TN) / total — 0.947 at t = 0.5.", 0xFF0EA5E9),
        StepCard(2, "Compute the Baseline", "Predict the majority class: 0.912 here.", 0xFFEC4899),
        StepCard(3, "Subtract", "The model is worth 3.5 points over answering \"no\".", 0xFF8B5CF6),
        StepCard(4, "Push the Threshold", "0.921 at t = 0.99, while recall collapses.", 0xFFF59E0B),
        StepCard(5, "Check the Range", "Nearly flat where the model is actually changing.", 0xFF3B82F6),
        StepCard(6, "Switch Metrics", "Kappa 0.546 on the matrix accuracy scores 0.947.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Accuracy", "(TP + TN) / N", "0.947 at t = 0.5 on the lab's data."),
        FormulaEntry("Majority baseline", "max(P, N) / N", "0.912 — what predicting one class always gives."),
        FormulaEntry("Balanced accuracy", "(recall + specificity) / 2", "Averages per class instead of per case."),
        FormulaEntry("At t = 0.99", "accuracy 0.921 · recall 0.011", "Higher accuracy, useless model."),
        FormulaEntry("Range over thresholds", "0.406 – 0.960", "Against recall's 0.000 – 1.000."),
        FormulaEntry("Chance-corrected", "κ = 0.546", "The same matrix, honestly summarised."),
    ),
    notationKey = listOf(
        NotationEntry("N", "total cases — 1,000 in the lab"),
        NotationEntry("majority baseline", "the accuracy of always predicting the largest class"),
        NotationEntry("balanced accuracy", "the mean of per-class recalls; immune to the base rate"),
        NotationEntry("accuracy paradox", "a useless model scoring high because one class dominates"),
        NotationEntry("base rate", "8.8% positive here, which is what sets the baseline"),
        NotationEntry("specificity", "TN / (TN + FP) — recall for the negative class"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Never report accuracy alone",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.dummy import DummyClassifier
                from sklearn.metrics import accuracy_score, balanced_accuracy_score

                baseline = DummyClassifier(strategy="most_frequent").fit(X_train, y_train)

                print(f"model    {accuracy_score(y_test, model.predict(X_test)):.3f}")
                print(f"baseline {accuracy_score(y_test, baseline.predict(X_test)):.3f}")
                print(f"balanced {balanced_accuracy_score(y_test, model.predict(X_test)):.3f}")

                # model    0.947
                # baseline 0.912   <- the number your model has to beat, and it is not 0.5
                # balanced 0.699   <- per-class average, which the base rate cannot inflate
                #
                # DummyClassifier costs one line and makes the accuracy figure interpretable. Without
                # it, "94.7% accurate" is a sentence with no scale attached.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Watch it fail to respond",
            accentColor = 0xFFF59E0B,
            code = """
                import numpy as np
                from sklearn.metrics import accuracy_score, recall_score

                for t in (0.1, 0.2, 0.3, 0.5, 0.7, 0.9, 0.99):
                    predicted = probabilities >= t
                    print(f"t={t:<5} accuracy {accuracy_score(y_test, predicted):.3f}"
                          f"  recall {recall_score(y_test, predicted):.3f}")

                # t=0.3   accuracy 0.960  recall 0.693
                # t=0.5   accuracy 0.947  recall 0.398
                # t=0.9   accuracy 0.912  recall 0.000   <- accuracy = the baseline, model gone
                #
                # Accuracy is highest at t=0.3 here, which is at least directionally useful -- but it
                # is 0.912 at t=0.9, where the model has stopped working entirely. A metric that
                # cannot distinguish "good" from "switched off" is not a monitoring signal.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("check", 0xFF0EA5E9, "Balanced Problems", "Where both classes are common and both errors cost the same."),
        ApplicationCard("chart", 0xFF3B82F6, "Reporting", "Fine as a headline, next to the baseline it beat."),
        ApplicationCard("help", 0xFFEC4899, "Imbalanced Data", "The accuracy paradox: 0.912 for free on this dataset."),
        ApplicationCard("target", 0xFF10B981, "Better Defaults", "Balanced accuracy, kappa or MCC when classes are skewed."),
    ),
    takeaways = listOf(
        "(TP + TN) / N: the share of cases correct, and the right metric only on balanced data.",
        "The lab's model scores 0.947; predicting \"negative\" always scores 0.912.",
        "So the model is worth 3.5 points, and the baseline — not 0.5 — is what to read it against.",
        "At t = 0.99 accuracy rises to 0.921 while recall falls to 0.011: higher score, worse model.",
        "Over the useful threshold range it is nearly flat, so it cannot select an operating point.",
        "Balanced accuracy averages per class instead of per case and is immune to the base rate.",
        "On the identical matrix, Cohen's kappa reads 0.546 — the same question, chance-corrected.",
    ),
    crossLinks = listOf(
        CrossLink("confusion_matrix", "Confusion Matrix"),
        CrossLink("cohens_kappa", "Cohen's Kappa"),
        CrossLink("precision_recall", "Precision & Recall"),
        CrossLink("smote", "SMOTE (Oversampling)"),
        CrossLink("f1_score", "F1 Score"),
    ),
)
