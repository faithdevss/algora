package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val quantileRegressionContent = TopicContent(
    topicId = "quantile_regression",
    whatIsIt = listOf(
        "Ordinary regression models the conditional mean. Quantile regression models a conditional percentile instead — the median, the 90th, the 5th — by swapping squared error for the pinball loss, which penalizes over- and under-prediction at different rates.",
        "The asymmetry is the whole mechanism. At τ = 0.9, being below the true value costs 0.9 per unit while being above costs only 0.1, so the fit is pushed upward until exactly 90% of the data sits beneath it. At τ = 0.5 the two costs are equal, the loss reduces to mean absolute error, and you get the conditional median — which is also why median regression is naturally robust to outliers in a way mean regression is not.",
        "This matters most when the spread of y changes with x, because then no single line describes the relationship. Delivery times might average twenty minutes across the board while the 95th percentile climbs steeply with distance, and it is the 95th percentile that determines your service guarantee. Fitting several τ values at once produces a picture of the whole conditional distribution rather than one summary of it. Two practical notes: the fitted lines can cross at extreme τ (nothing in the procedure forbids it, and non-crossing variants exist), and the loss is piecewise linear so it is solved by linear programming or subgradient descent rather than a closed form.",
    ),
    steps = listOf(
        StepCard(1, "Pick τ", "Which percentile the model should track. 0.5 is the median.", 0xFF6366F1),
        StepCard(2, "Weight the Two Sides", "Under-prediction costs τ per unit; over-prediction costs 1 − τ.", 0xFF818CF8),
        StepCard(3, "Minimize the Pinball Loss", "Piecewise linear, so linear programming or subgradient descent — no normal equations.", 0xFF60A5FA),
        StepCard(4, "Check the Coverage", "About τ of the data should fall below the fitted line. This is directly measurable.", 0xFF10B981),
        StepCard(5, "Fit Several τ", "A set of quantiles sketches the whole conditional distribution.", 0xFFF59E0B),
        StepCard(6, "Watch for Crossings", "Independently fitted quantile lines can cross; use a non-crossing method if that matters.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Pinball loss", "ρτ(r) = r·τ if r ≥ 0, else r·(τ−1)", "Asymmetric absolute error."),
        FormulaEntry("Objective", "min Σ ρτ(yᵢ − xᵢᵀβ)", "Solved as a linear program."),
        FormulaEntry("τ = 0.5", "reduces to Σ|rᵢ|", "Median regression — MAE, and robust."),
        FormulaEntry("Coverage check", "P(y ≤ xᵀβ̂τ) ≈ τ", "A property you can verify on held-out data."),
        FormulaEntry("Heteroscedasticity", "quantile lines not parallel", "The signature that spread depends on x."),
        FormulaEntry("Prediction interval", "[β̂₀.₀₅, β̂₀.₉₅]", "A 90% interval with no distributional assumption."),
    ),
    notationKey = listOf(
        NotationEntry("τ", "the target quantile, between 0 and 1"),
        NotationEntry("ρτ", "the pinball (check) loss"),
        NotationEntry("heteroscedastic", "variance that changes with the predictors"),
        NotationEntry("coverage", "the fraction of observations actually below the fit"),
        NotationEntry("crossing", "when a lower-τ line rises above a higher-τ one"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The loss, and a fit at several quantiles",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np, statsmodels.formula.api as smf

                def pinball(residual, tau):
                    # residual = y - prediction. Being under the truth costs tau per unit,
                    # being over costs (1 - tau). Equal weights at tau = 0.5 gives MAE.
                    return np.maximum(tau * residual, (tau - 1) * residual).mean()

                fits = {}
                for tau in (0.1, 0.5, 0.9):
                    fits[tau] = smf.quantreg("y ~ x", data).fit(q=tau)

                for tau, fit in fits.items():
                    coverage = (data.y <= fit.predict(data)).mean()
                    print(tau, round(fit.params["x"], 3), round(coverage, 3))
                # The slopes differ, and each coverage lands near its tau — if the slopes
                # were equal, the spread would not depend on x and OLS would have sufficed.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Gradient boosting with a quantile objective",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.ensemble import GradientBoostingRegressor

                # The pinball loss is not specific to linear models. Any learner that accepts
                # a custom objective can target a quantile, which is how modern prediction
                # intervals are usually produced.
                lower = GradientBoostingRegressor(loss="quantile", alpha=0.05).fit(X, y)
                upper = GradientBoostingRegressor(loss="quantile", alpha=0.95).fit(X, y)

                interval = np.column_stack([lower.predict(X_test), upper.predict(X_test)])
                covered = ((y_test >= interval[:, 0]) & (y_test <= interval[:, 1])).mean()
                print(covered)   # should land near 0.90, with no Gaussian assumption anywhere
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("finance", 0xFF6366F1, "Value at Risk", "Regulators ask for a portfolio's 1% worst-case loss — a quantile, not a mean, and the mean is nearly irrelevant to it."),
        ApplicationCard("chart", 0xFF818CF8, "Growth Charts", "Paediatric percentile curves are quantile regressions of height and weight on age."),
        ApplicationCard("map", 0xFF10B981, "Delivery Guarantees", "A service promise is about the 95th percentile of delivery time; the average tells you almost nothing about it."),
    ),
    takeaways = listOf(
        "Quantile regression models a conditional percentile via the asymmetric pinball loss.",
        "τ = 0.5 gives the median — mean absolute error, and naturally robust to outliers.",
        "Non-parallel quantile lines are the direct signature of spread depending on x.",
        "Fitting several τ yields distribution-free prediction intervals, at the cost of possible crossings.",
    ),
    crossLinks = listOf(
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("robust_regression", "Robust Regression (RANSAC)"),
        CrossLink("gradient_boosting", "Gradient Boosting"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
