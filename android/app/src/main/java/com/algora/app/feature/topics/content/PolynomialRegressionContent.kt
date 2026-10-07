package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val polynomialRegressionContent = TopicContent(
    topicId = "polynomial_regression",
    figure = Figure(
        caption = "Cross-validated on degrees 1 through 9: training error falls every step — 0.90, 0.55, " +
            "0.30, 0.15, 0.05 — because a higher degree can only fit the training points better, never " +
            "worse. Held-out error does the opposite past degree 5: 0.75, 0.35, 0.25, then back up to " +
            "0.45 and 0.85 as Runge's phenomenon takes over near the edges. Choosing by training error " +
            "would pick degree 9 every time; choosing by validation error picks 5.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "train error",
                    listOf(
                        FigurePoint(0f, 0.90f), FigurePoint(0.25f, 0.55f), FigurePoint(0.5f, 0.30f),
                        FigurePoint(0.75f, 0.15f), FigurePoint(1f, 0.05f),
                    ),
                ),
                FigureSeries(
                    "validation error",
                    listOf(
                        FigurePoint(0f, 0.75f), FigurePoint(0.25f, 0.35f), FigurePoint(0.5f, 0.25f),
                        FigurePoint(0.75f, 0.45f), FigurePoint(1f, 0.85f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(FigurePoint(0.5f, 0.25f, "best: degree 5")),
            xLabel = "degree (1 → 9)",
            yLabel = "error",
        ),
    ),
    whatIsIt = listOf(
        "Polynomial regression fits a curve by feeding powers of x — x, x², x³ and so on — into an ordinary linear model. The curve bends, but nothing about the estimator changes: it is still linear in the coefficients, so the same closed-form least-squares solution applies unchanged.",
        "That phrase is the one worth holding onto. \"Linear model\" constrains how the parameters enter, not how the input does. y = β₀ + β₁x + β₂x² is linear in β and curved in x, which is why the entire toolkit — normal equations, ridge penalties, confidence intervals — carries over untouched. Basis expansion is the general version of this trick, and splines, Fourier features and kernel methods are all the same idea with a different basis.",
        "It is also the cheapest place to watch overfitting happen. Each added degree adds a coefficient, and with enough of them the curve threads every training point exactly — training error goes to zero while held-out error climbs. Two practical cautions come with it: powers of raw x are wildly different in scale (x⁹ at x = 9 is 387 million alongside a column of ones), so centre and scale before fitting or the normal equations lose all precision; and high-degree polynomials oscillate violently near the edges of the data, which is Runge's phenomenon and the reason splines are usually the better answer past degree three or four.",
    ),
    steps = listOf(
        StepCard(1, "Choose a Degree", "d controls flexibility. It is a hyperparameter, so it must be picked on data the fit never saw.", 0xFF6366F1),
        StepCard(2, "Build the Basis", "Expand each x into [1, x, x², …, x^d] — the design matrix's rows.", 0xFF818CF8),
        StepCard(3, "Centre and Scale", "Rescale x to roughly [-1,1] first, or high powers destroy the conditioning of XᵀX.", 0xFF60A5FA),
        StepCard(4, "Solve Least Squares", "Identical normal equations as linear regression. Nothing about the estimator changed.", 0xFF10B981),
        StepCard(5, "Check Held-Out Error", "Train error falls monotonically with degree. Only validation error can tell you when to stop.", 0xFFF59E0B),
        StepCard(6, "Watch the Edges", "High degrees oscillate wildly just outside the data. Extrapolation is meaningless here.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Model", "y = β₀ + β₁x + β₂x² + … + β_dx^d + ε", "Curved in x, linear in β."),
        FormulaEntry("Design matrix", "Xᵢⱼ = xᵢ^j", "The Vandermonde matrix — notoriously ill-conditioned."),
        FormulaEntry("Solution", "β̂ = (XᵀX)⁻¹Xᵀy", "Unchanged from ordinary linear regression."),
        FormulaEntry("Parameters", "d + 1", "Interpolates any d + 1 points exactly."),
        FormulaEntry("Runge's phenomenon", "edge error grows exponentially with degree (equispaced nodes)", "Why splines beat high-degree polynomials."),
    ),
    notationKey = listOf(
        NotationEntry("d", "polynomial degree"),
        NotationEntry("basis expansion", "mapping x into a richer feature set before a linear fit"),
        NotationEntry("Vandermonde", "the powers-of-x design matrix"),
        NotationEntry("condition number", "how much the solve amplifies numerical error"),
        NotationEntry("interpolation", "passing exactly through every training point"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fit and select the degree honestly",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.preprocessing import PolynomialFeatures, StandardScaler
                from sklearn.linear_model import LinearRegression
                from sklearn.pipeline import make_pipeline
                from sklearn.model_selection import cross_val_score
                import numpy as np

                def poly_model(degree):
                    # StandardScaler is not cosmetic: without it x**9 and the intercept
                    # column differ by ~8 orders of magnitude and the solve loses precision.
                    return make_pipeline(
                        PolynomialFeatures(degree, include_bias=False),
                        StandardScaler(),
                        LinearRegression(),
                    )

                scores = {
                    d: -cross_val_score(poly_model(d), X, y,
                                        scoring="neg_mean_squared_error", cv=5).mean()
                    for d in range(1, 10)
                }
                best = min(scores, key=scores.get)
                print(best, scores)   # train error would have chosen 9 every time
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the raw basis is numerically dangerous",
            accentColor = 0xFFEC4899,
            code = """
                x = np.linspace(0, 9, 40)

                raw = np.vander(x, 10)                       # columns x^9, ..., x, 1 (decreasing powers by default)
                print(np.linalg.cond(raw))                   # ~1e10 — catastrophic

                scaled = np.vander((x - x.mean()) / x.std(), 10)
                print(np.linalg.cond(scaled))                # ~1e3 — workable

                # Orthogonal polynomials (numpy.polynomial.legendre, or QR on the raw basis)
                # push this further: same fitted curve, condition number near 1.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("trend", 0xFF6366F1, "Dose-Response Curves", "Pharmacology relationships bend and saturate; a quadratic or cubic captures that where a line cannot."),
        ApplicationCard("chart", 0xFF818CF8, "Physical Calibration", "Sensor non-linearity is routinely corrected with a low-degree polynomial fitted to reference readings."),
        ApplicationCard("bulb", 0xFF10B981, "Teaching Overfitting", "The clearest demonstration there is: one slider, train error falling, held-out error turning up."),
    ),
    takeaways = listOf(
        "Polynomial regression is a linear model — linear in the coefficients, curved in x.",
        "Basis expansion generalizes this: splines, Fourier features and kernels are the same move.",
        "Degree is a hyperparameter and must be chosen on held-out data, never on training error.",
        "Scale before fitting, and distrust high-degree fits near the edges — Runge's phenomenon is real.",
    ),
    crossLinks = listOf(
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("ridge_regression", "Ridge Regression (L2)"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
    ),
)
