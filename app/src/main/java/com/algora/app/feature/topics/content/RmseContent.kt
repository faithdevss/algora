package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rmseContent = TopicContent(
    topicId = "rmse",
    whatIsIt = listOf(
        "RMSE is √MSE — the square root undoes the squaring, bringing the number back into the same units as y. Where MSE on the lab's least-squares fit is 18.482 (units²), RMSE is 4.299 (units) — a number you could read as \"typically off by about 4.3\" in whatever the target's own units are.",
        "Because it's a monotonic transform of MSE, RMSE never changes which model wins a comparison — only how the winning margin reads. The lab's three fits rank identically under both metrics: least squares (MSE 18.482 → RMSE 4.299), the MAE-optimal fit (MSE 21.765 → RMSE 4.665), and a deliberately bad model (MSE 69.285 → RMSE 8.324). Whichever fit has the lowest MSE also has the lowest RMSE, by construction — the square root is strictly increasing.",
        "What changes is interpretability, not the ranking. \"MSE 18.482\" is not a quantity anyone has intuition for; \"RMSE 4.299\" can be compared directly against the scale of y — is 4.3 large or small for a variable that ranges from 0 to 30? That comparison is the entire reason RMSE exists on top of a metric it adds no new information to.",
    ),
    steps = listOf(
        StepCard(1, "Start from MSE", "18.482 on the lab's least-squares fit.", 0xFF0EA5E9),
        StepCard(2, "Take the Square Root", "4.299 — back in the units of y.", 0xFF3B82F6),
        StepCard(3, "Check It Preserves Ranking", "Every fit's MSE order matches its RMSE order.", 0xFF8B5CF6),
        StepCard(4, "Least Squares", "MSE 18.482 → RMSE 4.299.", 0xFFF59E0B),
        StepCard(5, "MAE-Optimal Fit", "MSE 21.765 → RMSE 4.665.", 0xFFEC4899),
        StepCard(6, "A Bad Model", "MSE 69.285 → RMSE 8.324 — same ranking, readable units.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("RMSE", "√MSE", "The only transform applied."),
        FormulaEntry("Least squares", "MSE 18.482 → RMSE 4.299", "The best fit on this data, by either metric."),
        FormulaEntry("MAE-optimal fit", "MSE 21.765 → RMSE 4.665", "Second, under both metrics."),
        FormulaEntry("Deliberately bad fit", "MSE 69.285 → RMSE 8.324", "Worst, under both metrics."),
        FormulaEntry("Units", "same as y", "Unlike MSE's squared units."),
        FormulaEntry("Ranking", "identical to MSE's", "√ is strictly increasing — order never flips."),
    ),
    notationKey = listOf(
        NotationEntry("RMSE", "root mean squared error"),
        NotationEntry("monotonic transform", "an operation that preserves order — never reverses a ranking"),
        NotationEntry("units of y", "what RMSE is expressed in, unlike MSE"),
        NotationEntry("residual", "y − ŷ, squared and averaged before the root is taken"),
        NotationEntry("interpretability", "the property RMSE adds; it adds no new ranking information"),
        NotationEntry("scale-dependent", "RMSE 4.3 means different things for a variable ranging 0-10 vs 0-1000"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Same ranking, readable units",
            accentColor = 0xFF0EA5E9,
            code = """
                import numpy as np
                from sklearn.metrics import mean_squared_error

                fits = {"least squares": ls_fit, "MAE-optimal": mae_fit, "deliberately bad": bad_fit}
                for name, fit in fits.items():
                    mse = mean_squared_error(y, fit.predict(X))
                    print(f"{name}: MSE {mse:.3f}  RMSE {np.sqrt(mse):.3f}")
                # least squares:     MSE 18.482  RMSE 4.299
                # MAE-optimal:       MSE 21.765  RMSE 4.665
                # deliberately bad:  MSE 69.285  RMSE 8.324
                #
                # The ranking (least squares < MAE-optimal < deliberately bad) is identical under
                # both columns -- sqrt is strictly increasing, so it cannot reorder them.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the units matter for judgment, not for ranking",
            accentColor = 0xFF10B981,
            code = """
                # "MSE is 18.482" has no intuitive scale on its own -- squared dollars, squared
                # days, squared whatever y measures. "RMSE is 4.299" can be set directly against
                # the range of y: if y spans roughly 0 to 30 in this dataset, being typically off
                # by 4.3 is a specific, judgeable claim in a way the squared number is not.
                #
                # This is the only thing RMSE adds over MSE -- it changes nothing about which
                # model a comparison prefers.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Reporting to Stakeholders", "A number in the target's own units, unlike MSE."),
        ApplicationCard("check", 0xFF3B82F6, "Model Selection", "Ranks fits identically to MSE — pick either, report RMSE."),
        ApplicationCard("finance", 0xFF8B5CF6, "Forecast Accuracy", "\"Typically off by $4.30\" reads directly against a budget."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Comparing across datasets with different scales — normalize first."),
    ),
    takeaways = listOf(
        "RMSE is √MSE — the same information, expressed in the units of y instead of y².",
        "On the lab's data, MSE 18.482 becomes RMSE 4.299, a number readable against y's scale.",
        "Because √ is strictly increasing, RMSE never reorders a comparison MSE already settled.",
        "All three lab fits rank identically under MSE and RMSE: 18.482/4.299, 21.765/4.665, 69.285/8.324.",
        "The only thing it adds over MSE is interpretability — not new ranking information.",
        "\"Typically off by 4.3\" is a judgeable claim; \"18.482 squared units\" is not.",
        "Still scale-dependent — an RMSE of 4.3 means different things for different y ranges.",
    ),
    crossLinks = listOf(
        CrossLink("mse", "Mean Squared Error (MSE)"),
        CrossLink("mae", "Mean Absolute Error (MAE)"),
        CrossLink("r_squared", "R-Squared (R²)"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
