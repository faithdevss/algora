package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ridgeRegressionContent = TopicContent(
    topicId = "ridge_regression",
    figure = Figure(
        caption = "Shrinkage is dⱼ²/(dⱼ²+λ), and it is not the same number for every direction. A " +
            "direction the data constrains strongly (large dⱼ) is still at 0.95 of its OLS value once " +
            "the direction with the least signal has already lost half its coefficient (0.55, then " +
            "0.25, then 0.10) — the same λ, two completely different discounts. Both start at 1.0 at " +
            "λ = 0, which is OLS exactly, and neither one is forced to zero at any finite λ.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "large-signal direction",
                    listOf(
                        FigurePoint(0f, 1.0f), FigurePoint(0.25f, 0.95f), FigurePoint(0.5f, 0.85f),
                        FigurePoint(0.75f, 0.70f), FigurePoint(1f, 0.55f),
                    ),
                ),
                FigureSeries(
                    "small-signal direction",
                    listOf(
                        FigurePoint(0f, 1.0f), FigurePoint(0.25f, 0.55f), FigurePoint(0.5f, 0.25f),
                        FigurePoint(0.75f, 0.10f), FigurePoint(1f, 0.03f),
                    ),
                ),
            ),
            xLabel = "λ",
            yLabel = "fraction of OLS coefficient kept",
        ),
    ),
    whatIsIt = listOf(
        "Ridge regression adds λ‖β‖² to the least-squares objective. The fit is no longer allowed to buy a small residual with enormous coefficients — it has to pay for them — and the result is a model that generalizes better despite fitting the training data worse.",
        "The mechanism is visible in the closed form: β̂ = (XᵀX + λI)⁻¹Xᵀy. That λI added to the diagonal is where the name comes from, and it does two things at once. It makes the matrix invertible even when XᵀX is singular — so ridge still has a unique answer when features outnumber observations or are perfectly collinear, cases where OLS has none — and it shrinks each coefficient toward zero, hardest along the directions where the data carries least information.",
        "The trade is deliberate. Ridge is biased by construction, and the Gauss-Markov theorem guarantees OLS has the lowest variance among linear unbiased estimators. But there always exists a λ > 0 whose reduction in variance exceeds the bias it introduces, so ridge strictly beats OLS on expected prediction error. What ridge will not do is select features: the penalty's smooth bowl has no corner at zero, so coefficients approach it asymptotically and never arrive. If you want exact zeros you need L1.",
    ),
    steps = listOf(
        StepCard(1, "Standardize the Features", "The penalty is scale-dependent — a feature in millimetres is penalized differently from the same feature in metres.", 0xFF6366F1),
        StepCard(2, "Leave the Intercept Alone", "Penalizing β₀ makes the fit depend on where you happened to put y's origin.", 0xFF818CF8),
        StepCard(3, "Add λI to the Diagonal", "Solve (XᵀX + λI)β = Xᵀy. Always invertible for λ > 0.", 0xFF60A5FA),
        StepCard(4, "Sweep λ", "λ = 0 is OLS; λ → ∞ drives every slope to zero and leaves the mean.", 0xFF10B981),
        StepCard(5, "Select by Cross-Validation", "The optimal λ is a property of the data, not a default worth guessing.", 0xFFF59E0B),
        StepCard(6, "Expect Shrinkage, Not Sparsity", "Every coefficient gets smaller; none becomes exactly zero.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min ‖y − Xβ‖² + λ‖β‖²", "Fit quality plus a size penalty."),
        FormulaEntry("Solution", "β̂ = (XᵀX + λI)⁻¹Xᵀy", "Closed form; the λI is the ridge."),
        FormulaEntry("Shrinkage per direction", "dⱼ²/(dⱼ² + λ)", "Directions with small singular values shrink most."),
        FormulaEntry("Effective parameters", "df(λ) = Σⱼ dⱼ²/(dⱼ² + λ)", "Falls from p to 0 as λ grows."),
        FormulaEntry("Bayesian view", "β ~ N(0, σ²/λ · I)", "Ridge is the MAP estimate under a Gaussian prior."),
        FormulaEntry("Constraint form", "min ‖y − Xβ‖² s.t. ‖β‖² ≤ t", "A sphere — no corners, hence no exact zeros."),
    ),
    notationKey = listOf(
        NotationEntry("λ", "regularization strength; larger means more shrinkage"),
        NotationEntry("dⱼ", "j-th singular value of X"),
        NotationEntry("df(λ)", "effective degrees of freedom"),
        NotationEntry("multicollinearity", "correlated features, which inflate OLS variance"),
        NotationEntry("MAP", "maximum a posteriori — the Bayesian reading of the penalty"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Ridge, and why standardizing matters",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.linear_model import RidgeCV
                from sklearn.preprocessing import StandardScaler
                from sklearn.pipeline import make_pipeline
                import numpy as np

                # The penalty sums squared coefficients, so a feature measured in millimetres
                # carries a coefficient 1000x smaller than the same feature in metres — and is
                # therefore penalized 1e6 times less. Standardizing removes the arbitrariness.
                model = make_pipeline(
                    StandardScaler(),
                    RidgeCV(alphas=np.logspace(-6, 6, 100)),   # alpha is sklearn's lambda
                )
                model.fit(X, y)
                print(model[-1].alpha_)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Where OLS breaks and ridge does not",
            accentColor = 0xFF10B981,
            code = """
                # Two perfectly correlated columns: X^T X is singular, so OLS has infinitely
                # many solutions and numpy will either fail or return an arbitrary one.
                x1 = np.random.randn(50)
                X = np.column_stack([x1, x1])            # exact duplicate
                y = 3 * x1 + np.random.randn(50) * 0.1

                # OLS: the coefficient split between the two columns is undetermined.
                print(np.linalg.lstsq(X, y, rcond=None)[0])       # e.g. [1.5, 1.5], or worse

                # Ridge: (X^T X + lambda I) is positive definite, so there is exactly one
                # answer — and it splits the weight evenly, which is usually what you want.
                lam = 1.0
                beta = np.linalg.solve(X.T @ X + lam * np.eye(2), X.T @ y)
                print(beta)                                       # stable and reproducible
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("flask", 0xFF6366F1, "Genomics", "Twenty thousand genes and a few hundred samples: p ≫ n, where OLS has no unique solution at all."),
        ApplicationCard("finance", 0xFF818CF8, "Factor Models", "Financial predictors are heavily correlated, and ridge stabilizes the coefficients rather than letting them swing between runs."),
        ApplicationCard("network", 0xFF10B981, "Weight Decay", "L2 regularization in a neural network is ridge applied to every layer — the same penalty under a different name."),
    ),
    takeaways = listOf(
        "Ridge adds λ‖β‖² and shrinks coefficients toward zero without ever reaching it.",
        "λI on the diagonal guarantees a unique solution even when features are collinear or outnumber samples.",
        "It is deliberately biased: a small amount of bias buys a large reduction in variance.",
        "Shrinkage is strongest along the directions the data constrains least — and it is scale-dependent, so standardize first.",
    ),
    crossLinks = listOf(
        CrossLink("lasso_regression", "Lasso Regression (L1)"),
        CrossLink("bayesian_ridge", "Bayesian Ridge Regression"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
    ),
)
