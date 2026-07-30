package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mseContent = TopicContent(
    topicId = "mse",
    whatIsIt = listOf(
        "Mean squared error is 1/n Σ(y − ŷ)² — the average squared residual. Squaring does two things at once: it makes every error positive so they don't cancel, and it weights large errors far more than small ones, since a residual twice as big contributes four times the penalty.",
        "That second property is the whole story on the lab's data: 40 clean points plus 4 deliberately contaminated ones (44 total, so the contaminated share is 9.1% of the data). Fit by least squares, the model's MSE is 18.482 — but the 4 contaminated points alone account for 81.2% of that total squared error. Nine percent of the data produces the large majority of the number.",
        "That's a direct consequence of squaring, not an incidental fact about this dataset: a model fit by minimizing absolute error instead (see MAE) gets a different line entirely — MSE 21.765 under this metric, worse than the least-squares fit's 18.482, because minimizing one loss does not minimize the other. MSE rewards the fit that specifically pulls in toward large residuals; that is a design choice, not a flaw to route around silently.",
    ),
    steps = listOf(
        StepCard(1, "Take Every Residual", "y minus the model's prediction, for each point.", 0xFF0EA5E9),
        StepCard(2, "Square Each One", "Removes sign, and inflates large errors disproportionately.", 0xFF3B82F6),
        StepCard(3, "Average Them", "18.482 on the lab's least-squares fit.", 0xFF8B5CF6),
        StepCard(4, "Isolate the Outliers", "4 of 44 points (9.1%) are contaminated.", 0xFFF59E0B),
        StepCard(5, "Attribute the Error", "Those 4 points carry 81.2% of the total squared error.", 0xFFEC4899),
        StepCard(6, "Compare Fits", "The MAE-optimal line scores worse under MSE: 21.765.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("MSE", "1/n Σ(y − ŷ)²", "Measured 18.482 on the least-squares fit."),
        FormulaEntry("Outlier share", "4 of 44 = 9.1%", "The contaminated fraction of the data."),
        FormulaEntry("Error share", "81.2%", "What those 4 points contribute to total squared error."),
        FormulaEntry("MAE-optimal fit under MSE", "21.765", "Worse — minimizing one loss doesn't minimize the other."),
        FormulaEntry("Units", "(units of y)²", "Squared, and not directly interpretable — see RMSE."),
        FormulaEntry("Least-squares", "argmin Σ(y − ŷ)²", "The fit MSE is the training objective for."),
    ),
    notationKey = listOf(
        NotationEntry("residual", "y − ŷ, the signed prediction error"),
        NotationEntry("outlier", "a point whose residual is unusually large"),
        NotationEntry("least squares", "the fitting procedure that directly minimizes MSE"),
        NotationEntry("error share", "the fraction of total squared error one subset of points accounts for"),
        NotationEntry("MAE", "the sibling metric that doesn't square — see that topic"),
        NotationEntry("contamination", "points that don't follow the data's main linear trend"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Four points, 81% of the error",
            accentColor = 0xFF0EA5E9,
            code = """
                import numpy as np
                from sklearn.metrics import mean_squared_error

                residuals_sq = (y - model.predict(X)) ** 2
                print(mean_squared_error(y, model.predict(X)))  # 18.482

                outlier_mask = contamination_flags  # the 4 known-contaminated points, of 44
                share = residuals_sq[outlier_mask].sum() / residuals_sq.sum()
                print(f"{outlier_mask.sum()} of {len(y)} points carry {share:.1%} of the error")
                # 4 of 44 points carry 81.2% of the error
                #
                # Squaring is why: a residual 4x the size of a typical one contributes 16x the
                # squared error, not 4x. MSE does not merely tolerate large errors more than small
                # ones -- it is specifically designed to weight them this way.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Minimizing MSE picks a different line than minimizing MAE",
            accentColor = 0xFFEC4899,
            code = """
                # Two fits on the same 44 points, one per objective:
                least_squares_fit = fit_ols(X, y)              # minimizes squared error
                least_absolute_fit = fit_irls_l1(X, y)          # minimizes absolute error

                print(mean_squared_error(y, least_squares_fit.predict(X)))   # 18.482 (its own metric)
                print(mean_squared_error(y, least_absolute_fit.predict(X)))  # 21.765 (worse, here)
                #
                # Each fit wins under its own objective and loses under the other's -- MSE is not
                # a neutral report card, it is the thing least squares was built to minimize.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Training Objective", "The loss ordinary least squares directly minimizes."),
        ApplicationCard("finance", 0xFF3B82F6, "Forecast Error", "Penalizes a rare large miss far more than routine ones."),
        ApplicationCard("check", 0xFF8B5CF6, "Model Comparison", "Standard baseline for regression, alongside RMSE and MAE."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Reporting to a human — its units are squared; use RMSE instead."),
    ),
    takeaways = listOf(
        "MSE is the mean squared residual — squaring removes sign and inflates large errors.",
        "On the lab's 44-point dataset, the least-squares fit scores MSE 18.482.",
        "4 of 44 points (9.1%) are contaminated, and they alone carry 81.2% of that error.",
        "That's squaring's direct effect: a residual twice as large counts four times as much.",
        "A model fit to minimize MAE instead scores worse under MSE: 21.765.",
        "Each fit wins under its own metric — MSE is a training objective, not a neutral referee.",
        "Its units are squared, so RMSE is usually the version worth reporting to a person.",
    ),
    crossLinks = listOf(
        CrossLink("rmse", "Root Mean Squared Error (RMSE)"),
        CrossLink("mae", "Mean Absolute Error (MAE)"),
        CrossLink("r_squared", "R-Squared (R²)"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
