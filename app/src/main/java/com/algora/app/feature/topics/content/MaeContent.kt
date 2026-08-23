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

internal val maeContent = TopicContent(
    topicId = "mae",
    figure = Figure(
        caption = "Two fits of the same 44 points, each scored under both metrics and against the " +
            "data's true slope of 1.4. Every cell is won by the fit that was trained for it: the " +
            "least-absolute-deviations line takes MAE 1.894 against 2.601, the least-squares line " +
            "takes MSE 18.482 against 21.765, and neither takes both. The slope column says why — " +
            "four of the 44 points are contaminated, squaring their residuals drags the " +
            "least-squares slope to 1.861, and weighing residuals by plain magnitude leaves the LAD " +
            "slope at 1.434. MAE is not a gentler report of the same fit; it is a different line.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("2.601", "18.482", "1.861"),
                listOf("1.894", "21.765", "1.434"),
            ),
            rowHeaders = listOf("least sq", "LAD"),
            colHeaders = listOf("MAE", "MSE", "slope"),
            marks = listOf(
                FigureCell(1, 0),
                FigureCell(0, 1),
                FigureCell(1, 2, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "MAE is 1/n Σ|y − ŷ| — same units as MSE's square root, but a genuinely different objective, not a gentler write-up of the same one. Fitting a line to minimize MAE (least absolute deviations) produces a different line than fitting to minimize MSE (least squares), and the lab's data shows both fits, side by side, on the same 44 points.",
        "The least-squares fit has slope 1.861 and MAE 2.601. The least-absolute-deviations fit — trained specifically to minimize MAE — has slope 1.434, far closer to the data's true slope of 1.4, and MAE 1.894, genuinely lower. But that fit's own MSE is 21.765, worse than the least-squares fit's 18.482. Each line wins under the metric it was built for and loses under the other's.",
        "The mechanism is the contaminated points: 4 of the 44 are outliers, and MSE squares their large residuals, pulling the least-squares line's slope up toward them (1.861 against a true 1.4). MAE weighs every residual by its plain magnitude, not its square, so it isn't dragged the same way — the LAD fit's slope stays close to 1.4 specifically because it doesn't over-count the outliers' size.",
    ),
    steps = listOf(
        StepCard(1, "Take Every Residual", "y minus the model's prediction.", 0xFF0EA5E9),
        StepCard(2, "Take Absolute Value", "No squaring — magnitude only.", 0xFF3B82F6),
        StepCard(3, "Average Them", "2.601 for the least-squares fit; 1.894 for the LAD fit.", 0xFF8B5CF6),
        StepCard(4, "Fit for MAE Directly", "Least absolute deviations: slope 1.434, close to the true 1.4.", 0xFFF59E0B),
        StepCard(5, "Check Its MSE", "21.765 — worse than the least-squares fit's 18.482.", 0xFFEC4899),
        StepCard(6, "See Neither Wins Both", "Each fit wins its own metric, loses the other's.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("MAE", "1/n Σ|y − ŷ|", "Measured 2.601 (least-squares fit)."),
        FormulaEntry("LAD-optimal fit", "MAE 1.894", "Trained specifically to minimize this metric."),
        FormulaEntry("Same LAD fit's MSE", "21.765", "Worse than the least-squares fit's 18.482."),
        FormulaEntry("Slope, least squares", "1.861", "Pulled up by 4 contaminated points out of 44."),
        FormulaEntry("Slope, LAD fit", "1.434", "Close to the data's true slope of 1.4."),
        FormulaEntry("Units", "same as y", "Not squared, unlike MSE."),
    ),
    notationKey = listOf(
        NotationEntry("MAE", "mean absolute error"),
        NotationEntry("LAD", "least absolute deviations — the fit that minimizes MAE directly"),
        NotationEntry("least squares", "the fit that minimizes MSE, not MAE"),
        NotationEntry("residual magnitude", "what MAE weighs — not the squared residual"),
        NotationEntry("robustness", "MAE's relative insensitivity to a handful of large outliers"),
        NotationEntry("contamination", "the 4 of 44 points not following the main linear trend"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two objectives, two different lines",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import mean_absolute_error, mean_squared_error

                # least_squares_fit minimizes squared error; lad_fit minimizes absolute error
                print(mean_absolute_error(y, least_squares_fit.predict(X)))  # 2.601
                print(mean_absolute_error(y, lad_fit.predict(X)))            # 1.894 -- lower, as designed

                print(mean_squared_error(y, least_squares_fit.predict(X)))  # 18.482 -- lower, as designed
                print(mean_squared_error(y, lad_fit.predict(X)))            # 21.765

                print(least_squares_fit.slope, lad_fit.slope)  # 1.861   1.434
                print("true slope: 1.4")
                #
                # LAD's slope sits much closer to the data's real trend. Least squares' slope is
                # dragged toward the 4 contaminated points because it squares their residuals.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Fitting least absolute deviations by iteratively reweighted least squares",
            accentColor = 0xFF10B981,
            code = """
                def fit_lad(x, y, iterations=60):
                    intercept, slope = fit_ols(x, y)  # start from the OLS solution
                    for _ in range(iterations):
                        residual = y - (intercept + slope * x)
                        weight = 1.0 / (np.abs(residual) + 1e-6)  # down-weight large residuals
                        intercept, slope = weighted_least_squares(x, y, weight)
                    return intercept, slope
                #
                # Each round shrinks the influence of whichever points currently have the largest
                # residuals -- the opposite of what plain least squares does, which is why the two
                # converge to different lines on contaminated data.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Robust Regression", "Less swayed by a handful of large outliers than MSE."),
        ApplicationCard("finance", 0xFF3B82F6, "Forecast Accuracy", "\"Typically off by X\" without letting rare large misses dominate."),
        ApplicationCard("check", 0xFF8B5CF6, "Model Comparison", "Pairs with MSE to reveal whether a fit is outlier-driven."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "When large errors genuinely should cost disproportionately more — use MSE."),
    ),
    takeaways = listOf(
        "MAE is the mean absolute residual — a different objective from MSE, not a softer version of it.",
        "The least-squares fit's own MAE is 2.601; a fit trained to minimize MAE gets 1.894.",
        "That MAE-optimal fit's MSE is 21.765 — worse than the least-squares fit's 18.482.",
        "Each fit wins under its own metric and loses under the other's; neither dominates.",
        "The MAE-optimal fit's slope (1.434) sits much closer to the true 1.4 than least squares' (1.861).",
        "MSE's squaring pulls a fit toward outliers; MAE's plain magnitude doesn't weigh them the same way.",
        "Report both when outliers might be present — agreement or disagreement between them is informative.",
    ),
    crossLinks = listOf(
        CrossLink("mse", "Mean Squared Error (MSE)"),
        CrossLink("rmse", "Root Mean Squared Error (RMSE)"),
        CrossLink("r_squared", "R-Squared (R²)"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
