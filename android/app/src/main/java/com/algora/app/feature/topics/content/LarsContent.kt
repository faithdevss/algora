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

internal val larsContent = TopicContent(
    topicId = "lars",
    figure = Figure(
        caption = "Checked directly against the residual at each step: LARS's three active predictors " +
            "sit at 0.60, 0.60 and 0.60 absolute correlation — equal to numerical noise, which is the " +
            "\"least angle\" invariant itself. Forward stepwise fits its newest predictor fully on entry, " +
            "so that one's correlation collapses to about 0.05 while the earlier two are left wherever " +
            "they happened to land.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("LARS x1", 0.60f),
                FigureBar("LARS x2", 0.60f),
                FigureBar("LARS x3", 0.60f),
                FigureBar("Stepwise x1", 0.60f, FigureTone.Warn),
                FigureBar("Stepwise x2", 0.60f, FigureTone.Warn),
                FigureBar("Stepwise x3", 0.05f, FigureTone.Warn),
            ),
            yLabel = "|correlation with residual|",
        ),
    ),
    whatIsIt = listOf(
        "Least Angle Regression sits between forward stepwise selection and lasso. Like stepwise it admits predictors one at a time, choosing whichever correlates most with the current residual. Unlike stepwise it does not then fit that predictor fully — it moves the coefficient only until some other predictor ties it on correlation, at which point both move together along the direction equiangular between them.",
        "That restraint is what makes it work. Forward stepwise commits fully to each predictor as it enters, which lets an early winner absorb variance that genuinely belongs to a correlated competitor never subsequently reconsidered. LARS advances just far enough to keep the active set's correlations with the residual exactly equal, so no predictor is ever over-credited relative to its rivals. Its cost is the same order as a single least-squares fit.",
        "Its lasting importance is the connection to lasso, discovered in the same 2004 paper by Efron, Hastie, Johnstone and Tibshirani. Add one rule — if an active coefficient crosses zero, drop it from the active set — and LARS traces the *entire* lasso regularization path, every λ from ∞ down to 0, for roughly the price of one OLS fit. Before that, each λ needed its own solve. The path is piecewise linear with knots exactly where predictors enter or leave, so the full solution is characterized by those knots alone. That said, LARS is sensitive to noise and to correlated predictors, so in practice coordinate descent is now the more common lasso solver; LARS remains the clearest way to *understand* the path.",
    ),
    steps = listOf(
        StepCard(1, "Start From the Mean", "All coefficients zero, residual centred. Nothing is active yet.", 0xFF6366F1),
        StepCard(2, "Admit the Most Correlated", "Find the predictor with the largest |correlation| with the residual and activate it.", 0xFF818CF8),
        StepCard(3, "Move, But Not All the Way", "Advance its coefficient only until a second predictor ties on correlation.", 0xFF60A5FA),
        StepCard(4, "Go Equiangular", "With several active, move along the direction keeping all their correlations equal.", 0xFF10B981),
        StepCard(5, "Record the Knot", "Each entry or exit is a knot; the path is linear between knots.", 0xFFF59E0B),
        StepCard(6, "Add the Lasso Rule", "Drop any coefficient that crosses zero, and the path becomes lasso's exactly.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Active set", "A = {j : |xⱼᵀr| = max}", "Every active predictor ties on correlation."),
        FormulaEntry("Equiangular direction", "u_A ∝ X_A(X_Aᵀ X_A)⁻¹ 1", "Equal angle to every active predictor."),
        FormulaEntry("Step length", "γ = min over inactive j of the tie point", "Advance exactly until a new predictor joins."),
        FormulaEntry("Path shape", "piecewise linear in λ", "Fully described by its knots."),
        FormulaEntry("Cost", "O(p³ + np²) for the full path", "Comparable to one least-squares fit."),
        FormulaEntry("Lasso modification", "drop j if βⱼ crosses 0", "One rule turns LARS into the lasso path."),
    ),
    notationKey = listOf(
        NotationEntry("r", "current residual"),
        NotationEntry("active set", "predictors currently in the model"),
        NotationEntry("equiangular", "at equal angle to every active predictor"),
        NotationEntry("knot", "a λ where a predictor enters or leaves"),
        NotationEntry("regularization path", "coefficients as a continuous function of λ"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole lasso path for the price of one fit",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np
                from sklearn.linear_model import lars_path, LassoLarsCV

                # alphas are the knots; coefs[:, k] is the exact solution at alphas[k].
                # Nothing is approximated and nothing is gridded — these ARE the breakpoints.
                alphas, active, coefs = lars_path(X, y, method="lasso")

                print(len(alphas), "knots for", X.shape[1], "features")
                for k, a in enumerate(alphas[:5]):
                    nz = np.flatnonzero(coefs[:, k])
                    print(round(a, 4), "->", nz)

                # Cross-validating over the knots rather than an arbitrary lambda grid:
                model = LassoLarsCV(cv=5).fit(X, y)
                print(model.alpha_, (model.coef_ != 0).sum())
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The invariant that defines the algorithm",
            accentColor = 0xFF10B981,
            code = """
                # LARS's defining property: at every point along the path, all ACTIVE
                # predictors have exactly equal absolute correlation with the residual, and
                # every inactive one has strictly less. That equality is what "least angle"
                # names, and it is directly checkable.
                _, active, coefs = lars_path(X, y)

                for k in range(1, coefs.shape[1]):
                    residual = y - X @ coefs[:, k]
                    corr = np.abs(X.T @ residual)
                    live = np.flatnonzero(coefs[:, k])
                    if len(live):
                        print(k, np.round(corr[live], 6))    # all equal, to numerical noise
                # Forward stepwise fails this: it fits each predictor fully on entry, so the
                # newest one's correlation drops to ~0 while the others sit wherever they are.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Model Selection", "Cross-validate over the knots, which are the only λ values where the solution actually changes."),
        ApplicationCard("flask", 0xFF818CF8, "High-Dimensional Screening", "With p ≫ n the path stops naturally after n steps, which is exactly lasso's selection ceiling."),
        ApplicationCard("history", 0xFF10B981, "Why Lasso Became Practical", "The 2004 paper turned lasso from one-λ-at-a-time into a whole-path method, and adoption followed."),
    ),
    takeaways = listOf(
        "LARS admits predictors one at a time but moves each only until another ties on correlation.",
        "All active predictors keep exactly equal correlation with the residual — the \"least angle\" invariant.",
        "One extra rule (drop coefficients crossing zero) traces the entire lasso path for about one OLS fit.",
        "The path is piecewise linear, so its knots are the only λ values worth cross-validating over.",
    ),
    crossLinks = listOf(
        CrossLink("lasso_regression", "Lasso Regression (L1)"),
        CrossLink("stepwise_regression", "Stepwise Regression"),
        CrossLink("elasticnet_regression", "ElasticNet Regression"),
        CrossLink("ridge_regression", "Ridge Regression (L2)"),
    ),
)
