package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val zScoreStandardizationContent = TopicContent(
    topicId = "z_score_standardization",
    whatIsIt = listOf(
        "Z-score standardization subtracts a column's mean and divides by its standard deviation, so the result has mean 0 and standard deviation 1. Unlike min-max it does not produce a bounded range — the lab's two columns end up spanning 3.58 and 3.13, because how far the extremes sit is a property of the data, not of the transform. What it produces instead is a column measured in standard deviations, which is what makes coefficients, distances and regularization penalties comparable across features.",
        "The reason to reach for it is the same as for any scaler, and the lab measures it the same way: a k-NN classifier over age and income where income supplies 99.996% of the squared distance goes from 0.738 accuracy to 0.888 after standardizing, with the distance split moving to 51% / 49%. Min-max reaches 0.900 on this data. The two are within a point of each other and the choice between them is not about accuracy on clean data — it is about what happens when the data is not clean.",
        "That is where they separate. Fit both scalers on a column containing one income of 5,000,000: min-max squeezes every real point into 0.026 of its range, because it is defined by the two extremes, while z-score leaves the same points spanning 0.289 — eleven times more room. Z-score is not robust either, since the outlier moves both the mean and σ, but it degrades instead of collapsing. It is also the transform that PCA, ridge and lasso implicitly assume: all three penalise or rotate coefficients on the assumption that a unit of one feature means the same as a unit of another.",
    ),
    steps = listOf(
        StepCard(1, "Compute μ and σ", "From the training split, per column.", 0xFF6366F1),
        StepCard(2, "Centre and Scale", "(x − μ) / σ — the column is now in standard deviations.", 0xFF3B82F6),
        StepCard(3, "Expect an Unbounded Range", "3.58 and 3.13 here, not 1.", 0xFF10B981),
        StepCard(4, "Re-Measure Distance", "51% / 49% instead of 0% / 100%.", 0xFF8B5CF6),
        StepCard(5, "Score It", "0.738 → 0.888, same k-NN.", 0xFFF59E0B),
        StepCard(6, "Compare Under Contamination", "0.289 of the range against min-max's 0.026.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Z-score", "x' = (x − μ) / σ", "Mean 0, standard deviation 1 — range unspecified."),
        FormulaEntry("Interpretation", "x' = standard deviations from the mean", "Which is what makes columns comparable."),
        FormulaEntry("Measured, raw", "income = 99.996% of squared distance", "The same starting point as min-max."),
        FormulaEntry("Measured, scaled", "51% / 49%", "k-NN accuracy 0.738 → 0.888."),
        FormulaEntry("Under contamination", "span 0.289 vs min-max's 0.026", "Degrades rather than collapses."),
        FormulaEntry("Robust alternative", "(x − median) / IQR", "For columns with real tails."),
    ),
    notationKey = listOf(
        NotationEntry("μ", "the column mean, estimated on the training split"),
        NotationEntry("σ", "the column standard deviation — an outlier moves it too"),
        NotationEntry("standard score", "the output: how many σ from the mean a value sits"),
        NotationEntry("unbounded", "z-scores have no fixed range; 3.58 and 3.13 here"),
        NotationEntry("implicit assumption", "PCA, ridge and lasso all assume comparable units"),
        NotationEntry("robust scaling", "median and IQR in place of μ and σ"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The three scalers, and when each is the right one",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.preprocessing import MinMaxScaler, RobustScaler, StandardScaler

                StandardScaler()   # (x - mu) / sigma        -- default choice; tails allowed
                MinMaxScaler()     # (x - min) / (max - min) -- bounded features, no outliers
                RobustScaler()     # (x - median) / IQR      -- contaminated columns

                # All three are fit_transform on train, transform on test. The decision is about the
                # column, not about taste:
                #   bounded and clean (pixels, percentages)      -> MinMax
                #   roughly symmetric with tails (most measures) -> Standard
                #   heavy tails or known bad rows                -> Robust
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What contamination does to each one",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.preprocessing import MinMaxScaler, RobustScaler, StandardScaler

                clean = np.random.default_rng(0).normal(85_000, 20_000, 200).reshape(-1, 1)
                dirty = np.vstack([clean, [[5_000_000]]])     # one bad row

                for scaler in (MinMaxScaler(), StandardScaler(), RobustScaler()):
                    fitted = scaler.fit(dirty)
                    span = np.ptp(fitted.transform(clean)) # room the REAL points still occupy
                    print(f"{type(scaler).__name__:16} {span:.3f}")

                # MinMaxScaler     0.018   <- the real data is now a single point
                # StandardScaler   0.254
                # RobustScaler     3.34    <- median and IQR did not move at all
                #
                # Run this on your own column before choosing. One bad row is not a rare event in
                # data that came from a form, a sensor or a join.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Regularized Models", "Ridge and lasso penalise coefficients — only fair on comparable units."),
        ApplicationCard("share", 0xFF3B82F6, "PCA", "Variance is the objective, so units decide which axis wins."),
        ApplicationCard("trend", 0xFF10B981, "Gradient Descent", "Comparable scales mean one learning rate suits every weight."),
        ApplicationCard("help", 0xFFEC4899, "Not Robust", "μ and σ both move under contamination — use median and IQR then."),
    ),
    takeaways = listOf(
        "Subtract the mean, divide by the standard deviation: the column is then measured in σ.",
        "The output is not bounded — the lab's columns span 3.58 and 3.13, not 1.",
        "Same measurement as min-max: k-NN 0.738 → 0.888, distance split 0/100 → 51/49.",
        "On clean data the two scalers are within a point of each other; the choice is about dirty data.",
        "With one extreme row in the fit, real points keep 0.289 of the range against min-max's 0.026.",
        "Z-score is not robust either — μ and σ both move — but it degrades instead of collapsing.",
        "PCA, ridge and lasso all assume this transform has happened, whether or not it has.",
    ),
    crossLinks = listOf(
        CrossLink("min_max_normalization", "Min-Max Normalization"),
        CrossLink("outlier_detection", "Outlier Detection"),
        CrossLink("pca", "Principal Component Analysis"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("batch_normalization", "Batch Normalization"),
    ),
)
