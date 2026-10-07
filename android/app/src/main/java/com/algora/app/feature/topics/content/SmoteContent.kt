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

internal val smoteContent = TopicContent(
    topicId = "smote",
    figure = Figure(
        caption = "The page's lab before and after SMOTE: 6 minority points against 60 majority, then " +
            "24 synthetic minority points, each placed a random fraction of the way from a real " +
            "minority point to one of its minority neighbours. Scored by k-NN on held-out points, " +
            "recall goes from 0.40 to 0.90 — the minority region is now dense enough for its " +
            "neighbours to outvote the majority. The precision column is the half that gets left " +
            "out of the pitch: 0.67 to 0.56, because the filled region now also claims a few " +
            "majority points. Every synthetic point sits on a segment between two real ones, never " +
            "outside their span, so SMOTE can only densify the region the minority already " +
            "occupies. And it belongs inside each training fold: a synthetic point built from a " +
            "validation row's neighbour leaks that row into training.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("6 : 60", "0.40", "0.67"),
                listOf("30 : 60", "0.90", "0.56"),
            ),
            rowHeaders = listOf("original", "SMOTE"),
            colHeaders = listOf("minority : majority", "recall", "precision"),
            marks = listOf(
                FigureCell(1, 1, FigureTone.Accent),
                FigureCell(1, 2, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "SMOTE — Synthetic Minority Over-sampling Technique — does not copy minority rows, it interpolates between them. Pick a minority point, pick one of its k nearest minority neighbours, and place a new point somewhere on the segment between them — x + λ(x_nn − x) with λ drawn between 0 and 1. Repeat until the classes are closer to balanced. The lab starts from 6 minority points against 60 majority, a 10:1 problem, and adds 24 synthetic points, taking the minority to 30.",
        "It works, in the direction it is meant to. Scored by k-NN on held-out points, minority recall goes from 0.40 to 0.90. Precision goes from 0.67 to 0.56, because the way recall was bought was by filling the minority region until it claims a few majority points too. That is a trade with a name and a price, not a free win, and quoting the recall without the precision is how SMOTE gets oversold.",
        "The mistake that costs more than the trade is where it is applied. A synthetic point is built from a real neighbour, so if that neighbour lands in the validation fold, the training set now contains something interpolated towards a row it is about to be scored on. Score that pipeline and the number is inflated by construction: it does not reproduce in production, where no synthetic point was ever built from the row being predicted. Resample inside the pipeline, and compare against the two cheaper options first: class weights, which invent no data at all, and moving the decision threshold, which is free at inference time.",
    ),
    steps = listOf(
        StepCard(1, "Split First", "Before any resampling. This is the whole ballgame.", 0xFFEC4899),
        StepCard(2, "Find Minority Neighbours", "k nearest, among minority points only.", 0xFF3B82F6),
        StepCard(3, "Interpolate", "A new point on the segment, not a copy of an old one.", 0xFFF97316),
        StepCard(4, "Balance the Training Fold", "6 minority points → 30 here, against 60 majority.", 0xFF10B981),
        StepCard(5, "Score Recall and Precision", "0.40 → 0.90 recall, 0.67 → 0.56 precision.", 0xFF8B5CF6),
        StepCard(6, "Compare the Cheap Options", "Class weights and thresholds first.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Synthetic point", "x' = xᵢ + λ(xⱼ − xᵢ), λ ~ U(0,1)", "xⱼ is one of xᵢ's k minority neighbours."),
        FormulaEntry("Imbalance", "60:6 = 10:1", "A majority-only classifier scores 90.9% accuracy."),
        FormulaEntry("Measured recall", "0.40 → 0.90", "k-NN on held-out points, after 24 synthetic points."),
        FormulaEntry("Measured precision", "0.67 → 0.56", "The cost of moving the boundary."),
        FormulaEntry("The leak", "resample before the split → inflated scores", "Synthetic points built from validation rows' neighbours."),
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

                # The leaky pipeline reports the higher score, and it is the one that will not
                # reproduce in production. It is not a subtle bug -- it produces a number you will
                # be asked to reproduce.
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
        "On a 10:1 problem it took the minority from 6 real points to 30 by adding 24 synthetic ones.",
        "Minority recall 0.40 → 0.90, measured on held-out points.",
        "Precision 0.67 → 0.56: recall was bought, not found.",
        "Resampling before the split leaks validation rows into training through their neighbours — the inflated score will not reproduce.",
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
