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

internal val rSquaredContent = TopicContent(
    topicId = "r_squared",
    figure = Figure(
        caption = "R²'s baseline is a model, not a convention: the horizontal line at ȳ = 10.565, " +
            "whose residual sum of squares over the lab's 44 points is 2,088.5. That is SS total, " +
            "and every other row is that same quantity divided by it. Least squares leaves 813.2 " +
            "of it and scores 0.611. The line fitted to minimise *absolute* error leaves 957.7 and " +
            "scores 0.541 — even though its slope of 1.434 is nearer the data's true 1.4 than " +
            "least squares' 1.861, because R² is built out of squared error and therefore prefers " +
            "the line the four contaminating points pulled. And a plausible-looking wrong model " +
            "(intercept 14, slope −0.6) leaves 3,048.6, which is more than the flat line does: " +
            "that ratio exceeding 1 is the whole content of a negative R².",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("2,088.5", "1.000", "0.000"),
                listOf("813.2", "0.389", "0.611"),
                listOf("957.7", "0.459", "0.541"),
                listOf("3,048.6", "1.460", "−0.460"),
            ),
            rowHeaders = listOf("mean only", "least sq", "LAD fit", "bad fit"),
            colHeaders = listOf("SS res", "÷ SS tot", "R²"),
            marks = listOf(
                FigureCell(0, 2),
                FigureCell(1, 2, FigureTone.Accent),
                FigureCell(3, 2, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "R² is 1 − (sum of squared residuals / total sum of squares) — the share of y's variance the model explains beyond what predicting the mean of y every time would already give you. Predicting the mean scores exactly R² = 0, by construction; that's the floor a model has to clear to be worth anything.",
        "The lab's least-squares fit scores R² = 0.611 — it explains 61.1% of the variance a mean-only baseline misses. A deliberately bad model (intercept 14, slope −0.6, chosen to look plausible without fitting the data) scores R² = −0.460: negative, meaning it is worse than just guessing the mean for every point. R² has no floor at zero for an arbitrary model — only for the one that literally is the mean.",
        "R² also has a property that sounds convenient and isn't: adding columns to a regression, even columns of pure random noise, can never lower it. Across 8 added noise columns on the lab's data, R² climbs every single step — 0.6106, 0.6135, 0.6168, 0.6184, 0.6355, 0.6357, 0.6399, 0.6450, 0.6453 — strictly increasing, though none of that improvement reflects anything real. That is exactly the gap Adjusted R² is built to close.",
    ),
    steps = listOf(
        StepCard(1, "Compute Residual Sum of Squares", "Σ(y − ŷ)² for the fitted model.", 0xFF0EA5E9),
        StepCard(2, "Compute Total Sum of Squares", "Σ(y − ȳ)² — variance around the mean.", 0xFF3B82F6),
        StepCard(3, "Take 1 − Ratio", "R² = 0.611 on the lab's least-squares fit.", 0xFF8B5CF6),
        StepCard(4, "Check the Floor", "Predicting the mean always scores exactly 0.", 0xFFF59E0B),
        StepCard(5, "Find a Negative Score", "A deliberately bad model scores R² = −0.460.", 0xFFEC4899),
        StepCard(6, "Add Noise Columns", "R² climbs every one of 8 steps — 0.6106 → 0.6453.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("R²", "1 − SSres / SStot", "Measured 0.611 on the least-squares fit."),
        FormulaEntry("Baseline", "R² = 0", "Predicting the mean for every point, exactly."),
        FormulaEntry("Deliberately bad fit", "R² = −0.460", "Worse than the mean-only baseline."),
        FormulaEntry("Noise columns +0 → +8", "0.6106 → 0.6453", "Strictly increasing — see the takeaway."),
        FormulaEntry("Range", "(−∞, 1]", "1 is a perfect fit; negative means worse than guessing the mean."),
        FormulaEntry("What it doesn't do", "penalize extra predictors", "See Adjusted R²."),
    ),
    notationKey = listOf(
        NotationEntry("SSres", "residual sum of squares, Σ(y − ŷ)²"),
        NotationEntry("SStot", "total sum of squares, Σ(y − ȳ)²"),
        NotationEntry("ȳ", "the mean of y — R²'s implicit baseline model"),
        NotationEntry("coefficient of determination", "R²'s formal name"),
        NotationEntry("noise column", "a predictor of pure random numbers, added to test this property"),
        NotationEntry("Adjusted R²", "the version that penalizes adding predictors — see that topic"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Negative R² is a real, informative outcome",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import r2_score

                print(r2_score(y, least_squares_fit.predict(X)))  # 0.611
                print(r2_score(y, [y.mean()] * len(y)))            # 0.000 -- exact, by construction
                print(r2_score(y, bad_model.predict(X)))           # -0.460
                #
                # A negative R^2 means the model is worse than predicting the mean for every point --
                # not a bug in the metric, a real statement about a bad fit.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "R² can only go up when you add a column",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np

                r2_by_step = []
                noise = np.random.randn(len(y), 8)          # draw ONCE: each step must nest the last
                for extra_columns in range(9):
                    x_with_noise = np.column_stack([x, noise[:, :extra_columns]])
                    fit = fit_ols(x_with_noise, y)
                    r2_by_step.append(r2_score(y, fit.predict(x_with_noise)))

                print(r2_by_step)
                # [0.6106, 0.6135, 0.6168, 0.6184, 0.6355, 0.6357, 0.6399, 0.6450, 0.6453]
                #
                # Strictly increasing across all 8 additions of pure noise. R^2 cannot fall when a
                # column is added, no matter how useless it is -- this is a mathematical property
                # of least squares, not a coincidence of this dataset.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Model Fit Summary", "The standard single-number regression report."),
        ApplicationCard("check", 0xFF3B82F6, "Baseline Comparison", "Whether a model beats \"just predict the mean.\""),
        ApplicationCard("finance", 0xFF8B5CF6, "Forecast Quality", "0.611 reads as \"explains 61% of the variance a naive guess misses.\""),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Comparing models with different numbers of predictors — use Adjusted R²."),
    ),
    takeaways = listOf(
        "R² is 1 − (residual variance / total variance) — variance explained beyond the mean.",
        "Predicting the mean of y for every point scores exactly R² = 0, by construction.",
        "A deliberately bad model on the lab's data scores R² = −0.460 — worse than that baseline.",
        "The least-squares fit explains 61.1% of the variance a mean-only guess misses.",
        "Adding 8 columns of pure random noise raises R² every single time: 0.6106 → 0.6453.",
        "That's a mathematical guarantee of least squares, not evidence the extra columns help.",
        "Use Adjusted R² instead whenever comparing models with different numbers of predictors.",
    ),
    crossLinks = listOf(
        CrossLink("adjusted_r_squared", "Adjusted R²"),
        CrossLink("mse", "Mean Squared Error (MSE)"),
        CrossLink("mae", "Mean Absolute Error (MAE)"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
