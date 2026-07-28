package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val stackingContent = TopicContent(
    topicId = "stacking",
    whatIsIt = listOf(
        "Stacking replaces voting's fixed combination rule with a learned one. Base models make predictions, those predictions become the features of a second model — the meta-learner — and that model works out how to combine them. It can discover that one member is reliable on one region of the data and another elsewhere, which no fixed averaging rule can express.",
        "Everything hinges on one detail, and getting it wrong is the standard way stacking fails. The meta-learner must be trained on *out-of-fold* predictions: split the training data into folds, fit the base models on all but one, predict the held-out fold, and rotate. If instead you feed it predictions the base models made for rows they were fitted on, those predictions carry a level of accuracy that will not exist at inference time — the meta-learner learns to trust it, and the whole stack overfits. The symptom is a validation score that looks excellent and a test score that does not.",
        "Blending is the cheaper variant: hold out a single validation split instead of cross-validating, train base models on the rest, and fit the meta-learner on that one split's predictions. Simpler and much faster, but the meta-learner sees far less data and the base models never train on the holdout. Two practical rules regardless of which you pick: keep the meta-learner simple, because logistic regression on a handful of highly-correlated prediction columns will already overfit and anything more flexible will do so immediately; and pass the original features through alongside the predictions only if you have enough data to afford it.",
    ),
    steps = listOf(
        StepCard(1, "Choose Diverse Base Models", "Different families. Correlated members give the meta-learner nothing to arbitrate.", 0xFFF59E0B),
        StepCard(2, "Split Into K Folds", "This is the machinery that makes the next step honest.", 0xFF818CF8),
        StepCard(3, "Generate Out-of-Fold Predictions", "Fit on K−1 folds, predict the held-out one, rotate. Never predict rows you fitted on.", 0xFF60A5FA),
        StepCard(4, "Train the Meta-Learner", "Those out-of-fold predictions are its features; the true labels are its target.", 0xFF10B981),
        StepCard(5, "Refit Base Models on Everything", "For inference, each base model is retrained on the full training set.", 0xFF14B8A6),
        StepCard(6, "Keep the Meta-Learner Simple", "Logistic regression is the default for a reason — a flexible one overfits the columns immediately.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Meta features", "zᵢ = [h₁(xᵢ), …, h_m(xᵢ)]", "One column per base model."),
        FormulaEntry("Prediction", "ŷ = g(z)", "g is the meta-learner, learned rather than fixed."),
        FormulaEntry("Voting as a special case", "g = fixed average", "Stacking generalizes it by learning g."),
        FormulaEntry("Out-of-fold requirement", "hⱼ(xᵢ) fitted without xᵢ", "The condition the whole method depends on."),
        FormulaEntry("Fit count", "m·(K+1)", "K folds plus one full refit, per base model."),
        FormulaEntry("Blending", "single holdout instead of K folds", "Cheaper, and the meta-learner sees less."),
    ),
    notationKey = listOf(
        NotationEntry("base model", "a first-level learner"),
        NotationEntry("meta-learner", "the second-level model that combines them"),
        NotationEntry("out-of-fold", "predicted by a model that never saw the row"),
        NotationEntry("leakage", "information about a row reaching the model that scores it"),
        NotationEntry("blending", "the single-holdout variant of stacking"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Stacking with the folds handled for you",
            accentColor = 0xFFF59E0B,
            code = """
                from sklearn.ensemble import StackingClassifier, RandomForestClassifier
                from sklearn.linear_model import LogisticRegression
                from sklearn.svm import SVC

                stack = StackingClassifier(
                    estimators=[
                        ("rf", RandomForestClassifier(n_estimators=200)),
                        ("svc", SVC(probability=True)),
                        ("lr", LogisticRegression(max_iter=1000)),
                    ],
                    final_estimator=LogisticRegression(),   # keep it simple
                    cv=5,                # sklearn generates the out-of-fold predictions
                    passthrough=False,   # True also feeds the raw features to the meta-learner
                )
                stack.fit(X_train, y_train)
                print(stack.score(X_test, y_test))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The leakage, done by hand so it is visible",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.model_selection import KFold, cross_val_predict

                # WRONG: predictions on rows the model was fitted on. A high-variance base
                # model will be near-perfect here, so the meta-learner learns to trust it
                # completely — and at inference that accuracy evaporates.
                leaky = np.column_stack([
                    m.fit(X_train, y_train).predict_proba(X_train)[:, 1] for m in models
                ])

                # RIGHT: each row's prediction comes from a fold that excluded it.
                honest = np.column_stack([
                    cross_val_predict(m, X_train, y_train, cv=KFold(5),
                                      method="predict_proba")[:, 1]
                    for m in models
                ])

                # The gap is the size of the lie:
                for j, name in enumerate(names):
                    print(name,
                          round(((leaky[:, j] > 0.5) == y_train).mean(), 4),
                          round(((honest[:, j] > 0.5) == y_train).mean(), 4))
                # An unpruned tree reads ~1.0 on the left and its real accuracy on the right.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFFF59E0B, "Kaggle Solutions", "Nearly every winning entry for a decade was a stack, often several levels deep."),
        ApplicationCard("history", 0xFF818CF8, "The Netflix Prize", "The winning ensemble blended over a hundred models; the complexity is also why it was never deployed."),
        ApplicationCard("chart", 0xFF10B981, "Heterogeneous Signals", "Combining models over genuinely different data sources, where a fixed weight would be arbitrary."),
    ),
    takeaways = listOf(
        "Stacking learns the combination rule; voting fixes it in advance.",
        "The meta-learner must train on out-of-fold predictions, or it learns to trust accuracy that will not exist.",
        "Blending swaps K folds for one holdout: faster, with less data for the meta-learner.",
        "Keep the meta-learner simple — its inputs are few and highly correlated.",
    ),
    crossLinks = listOf(
        CrossLink("voting", "Voting Classifiers"),
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("bagging", "Bagging (Bootstrap Aggregating)"),
    ),
)
