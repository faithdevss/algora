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

internal val minMaxNormalizationContent = TopicContent(
    topicId = "min_max_normalization",
    figure = Figure(
        caption = "Unscaled, age spans 49 years and income spans \$129,514 — averaged over every " +
            "train/test pair, income alone contributes 99.996% of the squared k-NN distance and age " +
            "0.004%, so the model effectively has one feature instead of two. Min-max normalization " +
            "moves the split to 44%/56% and takes the same classifier's held-out accuracy from 0.738 " +
            "to 0.900, without touching the model or the data.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("age, raw", 0.00004f),
                FigureBar("income, raw", 0.99996f, FigureTone.Warn),
                FigureBar("age, scaled", 0.44f, FigureTone.Accent),
                FigureBar("income, scaled", 0.56f, FigureTone.Accent),
            ),
            yLabel = "share of squared k-NN distance",
        ),
    ),
    whatIsIt = listOf(
        "Min-max normalization maps a column onto a fixed range, almost always [0,1], by subtracting its minimum and dividing by its range. It is the cheapest of the scalers and the easiest to reason about: the smallest value becomes 0, the largest becomes 1, and the shape of everything between them is preserved exactly. Nothing about it is statistical — it is a straight-line map defined entirely by two numbers.",
        "Why it matters is not aesthetic. The lab's dataset has age in years and income in dollars, spanning 49 and 129,514 respectively, and a k-NN classifier over both. Averaged over every train/test pair, income contributes 99.996% of the squared distance and age contributes 0.004% — so the model has two features and uses one. Min-max takes the same classifier and the same data from 0.738 accuracy to 0.900, and z-score to 0.888. The model did not change; only the units the distance was measured in.",
        "Its failure mode is the other side of its simplicity. Because the transform is defined by the two most extreme values in the column, a single bad row rescales everything: fitting the scaler on data that includes one income of 5,000,000 squeezes every real point into 0.026 of the [0,1] range, where z-score leaves the same points spanning 0.289. Use min-max when the range is genuinely bounded and known — pixel values, bounded scores, inputs to a sigmoid — and something robust when it is not. Fit on the training split only: fitting on everything leaks the test set's minimum and maximum into the transform.",
    ),
    steps = listOf(
        StepCard(1, "Find the Extremes", "Minimum and maximum, from the training split only.", 0xFFF97316),
        StepCard(2, "Subtract and Divide", "(x − min) / (max − min) — a straight-line map.", 0xFF3B82F6),
        StepCard(3, "Check the Range", "Every column now spans exactly 1.", 0xFF10B981),
        StepCard(4, "Re-Measure Distance", "The distance split moves from 0/100 to 44/56.", 0xFF6366F1),
        StepCard(5, "Score It", "Same k-NN, same data: 0.738 → 0.900.", 0xFF8B5CF6),
        StepCard(6, "Watch the Extremes", "One outlier in the fit squeezes real points into 0.026.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Min-max", "x' = (x − min) / (max − min)", "Defined by two values, both of which an outlier can be."),
        FormulaEntry("Custom range", "x' = a + (b − a)·(x − min)/(max − min)", "For [−1,1] or any other target range."),
        FormulaEntry("Measured, raw", "income = 99.996% of squared distance", "Age contributes 0.004%."),
        FormulaEntry("Measured, scaled", "44% / 56%", "The two features finally comparable."),
        FormulaEntry("k-NN accuracy", "0.738 → 0.900", "Held-out, same k, same data."),
        FormulaEntry("Under contamination", "real points span 0.026", "After one 5,000,000 value enters the fit."),
    ),
    notationKey = listOf(
        NotationEntry("min, max", "the column's extremes on the training split — the whole of the transform"),
        NotationEntry("range", "max − min, which becomes 1 after scaling"),
        NotationEntry("leakage", "fitting the scaler on data that includes the test split"),
        NotationEntry("distance share", "how much of a squared distance one column contributes"),
        NotationEntry("bounded feature", "one whose limits are known in advance — where min-max is the right choice"),
        NotationEntry("robust scaler", "the same idea on the median and IQR, for contaminated columns"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fit on train, transform both — the order that matters",
            accentColor = 0xFFF97316,
            code = """
                from sklearn.model_selection import train_test_split
                from sklearn.neighbors import KNeighborsClassifier
                from sklearn.pipeline import make_pipeline
                from sklearn.preprocessing import MinMaxScaler

                X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=0)

                # Wrong: the scaler has now seen the test set's minimum and maximum.
                # X_all = MinMaxScaler().fit_transform(X)

                # Right: the pipeline refits the scaler on the training part of every fold.
                model = make_pipeline(MinMaxScaler(), KNeighborsClassifier(n_neighbors=5))
                model.fit(X_train, y_train)
                print(model.score(X_test, y_test))

                # The leak is small on a big sample and large on a small one, and it always flatters
                # the score. Using a pipeline is not tidiness -- it is what makes the number real.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Check which feature your distance is actually made of",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                def distance_share(X, queries):
                    "Per-column share of total squared distance -- run this before scaling anything."
                    diffs = (X[None, :, :] - queries[:, None, :]) ** 2
                    per_column = diffs.sum(axis=(0, 1))
                    return per_column / per_column.sum()

                print(distance_share(X_train, X_test[:20]))
                # [4.1e-05 9.9996e-01]  <- age contributes nothing; k-NN is an income classifier
                #
                # Two lines, and they tell you whether scaling will change anything at all. If the
                # shares are already balanced, scaling is bookkeeping; if they look like this, it is
                # the single highest-value line in the pipeline.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFFF97316, "Distance Models", "k-NN, k-means and SVM are all distance-based; all three need this."),
        ApplicationCard("robot", 0xFF3B82F6, "Neural Networks", "Bounded inputs keep saturating activations out of their flat regions."),
        ApplicationCard("browser", 0xFF10B981, "Images", "Pixels are genuinely bounded at 0-255 — the ideal min-max case."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Trees ignore scale entirely, and contaminated columns need a robust scaler."),
    ),
    takeaways = listOf(
        "A straight-line map onto [0,1], defined entirely by the column's minimum and maximum.",
        "Unscaled, income was 99.996% of the k-NN distance and age 0.004% — two features, one used.",
        "Scaling took the same classifier from 0.738 to 0.900 without touching the model or the data.",
        "Because two values define the transform, one extreme row squeezed every real point into 0.026 of the range.",
        "Fit on the training split only; fitting on everything leaks the test set's extremes.",
        "Use it for genuinely bounded features, and a median/IQR scaler when the column has tails.",
        "Trees and boosted ensembles are invariant to it — scaling them buys nothing.",
    ),
    crossLinks = listOf(
        CrossLink("z_score_standardization", "Z-Score Standardization"),
        CrossLink("knn", "K-Nearest Neighbors"),
        CrossLink("outlier_detection", "Outlier Detection"),
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("pca", "Principal Component Analysis"),
    ),
)
