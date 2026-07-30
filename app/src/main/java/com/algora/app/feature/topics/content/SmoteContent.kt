package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val smoteContent = TopicContent(
    topicId = "smote",
    whatIsIt = listOf(
        "SMOTE — Synthetic Minority Over-sampling Technique — does not copy minority rows, it interpolates between them. Pick a minority point, pick one of its k nearest minority neighbours, and place a new point somewhere on the segment between them. Repeat until the classes are balanced. On the lab's 10:1 problem that takes the training fold from 99 rows to 180, and the minority region becomes dense with points inferred from the 9 real ones it had.",
        "It works, in the direction it is meant to. Scored with 5-NN on an untouched real test split, minority recall goes from 0.667 to 1.000 — every minority case found. Precision goes from 0.400 to 0.333 and accuracy from 0.879 to 0.818, because the way recall was bought was by pushing the decision boundary into majority territory. That is a trade with a name and a price, not a free win, and quoting the recall without the precision is how SMOTE gets oversold.",
        "The mistake that costs more than the trade is where it is applied. A synthetic point is built from a real neighbour, so if that neighbour lands in the validation fold, the training set now contains something interpolated towards a row it is about to be scored on. Measured over 5 folds at 1-NN: 0.954 for the pipeline that resamples before splitting against 0.908 for the one that resamples inside each fold. Those 4.6 points are not a result, they are a leak — and the leaky number is the one that will not reproduce in production. Resample inside the pipeline, and compare against the two cheaper options first: class weights, which invent no data at all, and moving the decision threshold, which is free at inference time.",
    ),
    steps = listOf(
        StepCard(1, "Split First", "Before any resampling. This is the whole ballgame.", 0xFFEC4899),
        StepCard(2, "Find Minority Neighbours", "k nearest, among minority points only.", 0xFF3B82F6),
        StepCard(3, "Interpolate", "A new point on the segment, not a copy of an old one.", 0xFFF97316),
        StepCard(4, "Balance the Training Fold", "99 rows → 180 here.", 0xFF10B981),
        StepCard(5, "Score Recall and Precision", "0.667 → 1.000 recall, 0.400 → 0.333 precision.", 0xFF8B5CF6),
        StepCard(6, "Compare the Cheap Options", "Class weights and thresholds first.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Synthetic point", "x' = xᵢ + λ(xⱼ − xᵢ), λ ~ U(0,1)", "xⱼ is one of xᵢ's k minority neighbours."),
        FormulaEntry("Imbalance", "120:12 = 10:1", "A majority-only classifier scores 90.9% accuracy."),
        FormulaEntry("Measured recall", "0.667 → 1.000", "5-NN on an untouched real test split."),
        FormulaEntry("Measured precision", "0.400 → 0.333", "The cost of moving the boundary."),
        FormulaEntry("The leak", "0.954 vs 0.908", "Resampling before the split against inside the folds."),
        FormulaEntry("Cheaper alternatives", "class weights · threshold", "No synthetic data, no leak surface."),
    ),
    notationKey = listOf(
        NotationEntry("k", "neighbours considered when interpolating — 5 in the lab"),
        NotationEntry("λ", "the random position along the segment between two minority points"),
        NotationEntry("recall", "the share of real minority cases the model finds"),
        NotationEntry("precision", "the share of predicted positives that are real — what SMOTE spends"),
        NotationEntry("leakage", "synthetic rows built from points that end up in the validation fold"),
        NotationEntry("class weight", "penalising minority errors more, without inventing rows"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The only correct place to put it",
            accentColor = 0xFFEC4899,
            code = """
                from imblearn.over_sampling import SMOTE
                from imblearn.pipeline import Pipeline          # NOT sklearn's Pipeline
                from sklearn.model_selection import cross_val_score
                from sklearn.neighbors import KNeighborsClassifier

                # Wrong -- and it will report a better score than it deserves:
                # X_res, y_res = SMOTE().fit_resample(X, y)
                # cross_val_score(model, X_res, y_res, cv=5)

                # Right: imblearn's Pipeline resamples the TRAINING part of each fold only.
                pipeline = Pipeline([
                    ("smote", SMOTE(k_neighbors=5, random_state=0)),
                    ("model", KNeighborsClassifier(n_neighbors=5)),
                ])
                print(cross_val_score(pipeline, X, y, cv=5, scoring="recall").mean())

                # The lab measures the gap between these two at 0.954 vs 0.908 over 5 folds. It is
                # not a subtle bug -- it produces a number you will be asked to reproduce.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Try the free options before synthesising anything",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np
                from sklearn.linear_model import LogisticRegression
                from sklearn.metrics import precision_recall_curve

                # 1. Class weights: no new rows, no leak surface, one argument.
                weighted = LogisticRegression(class_weight="balanced").fit(X_train, y_train)

                # 2. Threshold: free at inference, and it exposes the trade explicitly.
                probabilities = weighted.predict_proba(X_test)[:, 1]
                precision, recall, thresholds = precision_recall_curve(y_test, probabilities)
                for t in (0.5, 0.3, 0.2, 0.1):
                    predicted = probabilities >= t
                    print(f"t={t}: recall {(predicted & (y_test == 1)).sum() / (y_test == 1).sum():.3f}",
                          f"precision {(predicted & (y_test == 1)).sum() / max(predicted.sum(), 1):.3f}")

                # SMOTE picks one point on this curve for you, indirectly, by moving the boundary.
                # Choosing the threshold does the same thing directly, reversibly, and for free --
                # so it is the baseline SMOTE should have to beat.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFFF97316, "Fraud Detection", "The canonical imbalanced problem, and recall is what it wants."),
        ApplicationCard("flask", 0xFF3B82F6, "Rare Disease Screening", "Where a missed positive costs far more than a false one."),
        ApplicationCard("target", 0xFF10B981, "Defect Detection", "Few defective units, and finding them is the whole job."),
        ApplicationCard("help", 0xFFEC4899, "Where It Misleads", "Resampled before the split, every score is inflated."),
    ),
    takeaways = listOf(
        "Interpolates new minority points between real ones — it does not duplicate rows.",
        "On a 10:1 problem it took the training fold from 99 rows to 180, from 9 real minority points.",
        "Minority recall 0.667 → 1.000, measured on an untouched real test split.",
        "Precision 0.400 → 0.333 and accuracy 0.879 → 0.818: recall was bought, not found.",
        "Resampling before the split reported 0.954 against the honest pipeline's 0.908 — a 4.6-point leak.",
        "Resample inside the pipeline so each fold's synthetic rows come only from its own training data.",
        "Class weights and threshold moves cost nothing and invent no data — make SMOTE beat them first.",
    ),
    crossLinks = listOf(
        CrossLink("knn", "K-Nearest Neighbors"),
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("isolation_forest", "Isolation Forest"),
        CrossLink("bagging", "Bagging"),
        CrossLink("logistic_regression", "Logistic Regression"),
    ),
)
