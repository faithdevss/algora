package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val baggingContent = TopicContent(
    topicId = "bagging",
    whatIsIt = listOf(
        "Bagging — bootstrap aggregating — trains the same model many times on different resamples of the data and averages the results. Each resample draws n rows with replacement from a dataset of n rows, so roughly a third are missed and some appear several times.",
        "It targets variance specifically, and the arithmetic says exactly how well it can work. Averaging B models each with variance σ² and pairwise correlation ρ gives variance ρσ² + (1−ρ)σ²/B. The second term vanishes as B grows, but the first does not — so the ceiling is set entirely by how correlated the models are, not by how many you train. Adding the thousandth tree to a forest of nine hundred changes almost nothing.",
        "That formula also tells you when not to bother. Bagging helps in proportion to the base model's instability: a fully grown decision tree gives a wildly different answer on a resampled dataset, so bagging it is transformative, while linear regression is stable enough that every resample gives nearly the same line and averaging is a waste of compute. Bias is essentially untouched — bagging a biased model gives a biased ensemble. The free bonus is out-of-bag scoring: each model can be evaluated on the ~37% of rows it never saw, which is a validation estimate at no cost in data or extra fitting.",
    ),
    steps = listOf(
        StepCard(1, "Draw a Bootstrap Sample", "n rows with replacement. Duplicates are expected and are the point.", 0xFFF59E0B),
        StepCard(2, "Fit the Base Model", "Unpruned and high-variance is what you want — bagging will handle the variance.", 0xFF818CF8),
        StepCard(3, "Repeat Independently", "Every model is fitted separately, so the whole loop is embarrassingly parallel.", 0xFF60A5FA),
        StepCard(4, "Aggregate", "Average for regression, majority or soft vote for classification.", 0xFF10B981),
        StepCard(5, "Score Out-of-Bag", "Each row is scored by the ~37% of models that never saw it. Free validation.", 0xFF14B8A6),
        StepCard(6, "Check the Correlation Ceiling", "Variance floors at ρσ². More models cannot get past it — only less correlated ones can.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Ensemble variance", "ρσ² + (1−ρ)σ²/B", "The first term is the floor B cannot lower."),
        FormulaEntry("Out-of-bag fraction", "(1 − 1/n)ⁿ → 1/e ≈ 0.368", "Roughly a third of rows are missed per draw."),
        FormulaEntry("Prediction", "ŷ = (1/B) Σ fᵦ(x)", "Or a vote, for classification."),
        FormulaEntry("Bias", "unchanged", "Bagging reduces variance, not bias."),
        FormulaEntry("Cost", "B independent fits", "Fully parallel — B times the compute, not B times the wall clock."),
    ),
    notationKey = listOf(
        NotationEntry("B", "number of bagged models"),
        NotationEntry("ρ", "pairwise correlation between models"),
        NotationEntry("bootstrap", "sampling n rows with replacement from n"),
        NotationEntry("OOB", "out-of-bag — rows a given model never saw"),
        NotationEntry("unstable learner", "one whose fit changes a lot with the sample"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bagging, and the free validation estimate",
            accentColor = 0xFFF59E0B,
            code = """
                from sklearn.ensemble import BaggingClassifier
                from sklearn.tree import DecisionTreeClassifier
                from sklearn.linear_model import LogisticRegression

                # An unpruned tree is deliberately high-variance — exactly what bagging fixes.
                bagged_tree = BaggingClassifier(
                    DecisionTreeClassifier(),      # no max_depth: let it overfit
                    n_estimators=200,
                    oob_score=True,                # score on the ~37% each model never saw
                    n_jobs=-1,                     # every fit is independent
                ).fit(X, y)
                print(bagged_tree.oob_score_)      # a validation estimate, no split needed

                # The same wrapper on a stable model buys almost nothing, at 200x the cost.
                bagged_linear = BaggingClassifier(
                    LogisticRegression(), n_estimators=200, oob_score=True).fit(X, y)
                print(bagged_linear.oob_score_,
                      LogisticRegression().fit(X, y).score(X, y))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Where the diminishing returns come from",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                # rho * sigma^2 + (1 - rho) * sigma^2 / B
                def ensemble_variance(rho, B, sigma2=1.0):
                    return rho * sigma2 + (1 - rho) * sigma2 / B

                for B in (1, 10, 100, 1000, 10_000):
                    print(B, round(ensemble_variance(0.5, B), 4))
                # 1.0 -> 0.55 -> 0.505 -> 0.5005 -> 0.50005
                # Almost all the benefit arrived by B=10, and the floor at 0.5 is rho.

                # The only way past the floor is to decorrelate the models. That is precisely
                # what a random forest adds over plain bagging: sampling features at each
                # split lowers rho, which lowers the floor itself.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("stack", 0xFFF59E0B, "Random Forests", "Bagging plus per-split feature sampling — the single most widely deployed ensemble."),
        ApplicationCard("chart", 0xFF818CF8, "Model Uncertainty", "The spread across bagged predictions is a usable confidence estimate for models that give none."),
        ApplicationCard("chip", 0xFF10B981, "Parallel Training", "Independent fits scale linearly across cores or machines, unlike boosting's sequential chain."),
    ),
    takeaways = listOf(
        "Bagging reduces variance and leaves bias alone, so bag unstable models and skip stable ones.",
        "Ensemble variance floors at ρσ² — more models cannot beat correlation, only less correlated models can.",
        "About 37% of rows are out-of-bag per model, giving a validation estimate for free.",
        "All fits are independent, so bagging parallelizes where boosting cannot.",
    ),
    crossLinks = listOf(
        CrossLink("random_forest", "Random Forests"),
        CrossLink("extra_trees", "Extra Trees Classifier"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("decision_trees", "Decision Trees"),
    ),
)
