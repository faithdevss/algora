package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lassoRegressionContent = TopicContent(
    topicId = "lasso_regression",
    whatIsIt = listOf(
        "Lasso replaces ridge's squared penalty with an absolute one: λΣ|βⱼ|. A one-character change in the formula, and a categorical change in behaviour — coefficients do not merely shrink toward zero, they arrive there exactly and stay. The fit performs feature selection as a side effect of being fitted.",
        "The geometry explains why. Minimizing squared error subject to a budget on the coefficients means expanding an elliptical contour until it touches the constraint region. Ridge's region is a sphere, which is smooth everywhere, so the touch point almost never lands on an axis. Lasso's is a diamond with corners on the axes, and corners are exactly where a random ellipse is most likely to make first contact. In one dimension the same fact appears as soft-thresholding: the solution is (|ρ| − λ)₊ · sign(ρ), which is identically zero for every |ρ| below λ, not merely small.",
        "The costs are real and worth knowing before reaching for it. The objective is not differentiable at zero, so there is no closed form and you need coordinate descent or LARS. Among a group of correlated predictors lasso tends to keep one and zero the rest, and which one it keeps can flip with a small perturbation of the data — bad if the selection is the thing you intend to report. It also cannot select more than n features when p > n. ElasticNet exists to fix the first two of those.",
    ),
    steps = listOf(
        StepCard(1, "Standardize", "L1 is scale-dependent in the same way L2 is. Unscaled features get arbitrary penalties.", 0xFF6366F1),
        StepCard(2, "Penalize Absolute Size", "Add λΣ|βⱼ|, leaving the intercept out of the sum.", 0xFF818CF8),
        StepCard(3, "Solve Iteratively", "No closed form — coordinate descent applies soft-thresholding one coefficient at a time.", 0xFF60A5FA),
        StepCard(4, "Watch Coefficients Hit Zero", "Each one crosses to exactly zero at its own λ and stays there as λ grows.", 0xFF10B981),
        StepCard(5, "Read Off the Selection", "The non-zero set is the model. Selection and fitting happened in one step.", 0xFFF59E0B),
        StepCard(6, "Check Stability", "Resample and refit. If the chosen set moves a lot, do not report it as \"the\" set.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min ‖y − Xβ‖²/2n + λΣ|βⱼ|", "L1 in place of L2."),
        FormulaEntry("Soft-threshold", "βⱼ = sign(ρⱼ)(|ρⱼ| − λ)₊", "Exactly zero whenever |ρⱼ| ≤ λ."),
        FormulaEntry("Constraint form", "min ‖y − Xβ‖² s.t. Σ|βⱼ| ≤ t", "A diamond — corners on the axes."),
        FormulaEntry("Entry threshold", "λₘₐₓ = max|xⱼᵀy|/n", "Above this every coefficient is zero."),
        FormulaEntry("Bayesian view", "β ~ Laplace(0, 1/λ)", "A prior with a sharp spike at zero."),
        FormulaEntry("Limit when p > n", "at most n non-zero coefficients", "A structural ceiling on selection."),
    ),
    notationKey = listOf(
        NotationEntry("ρⱼ", "correlation of feature j with the current residual"),
        NotationEntry("(z)₊", "positive part: z if z > 0, else 0"),
        NotationEntry("sparsity", "how many coefficients are exactly zero"),
        NotationEntry("regularization path", "the coefficients traced as a function of λ"),
        NotationEntry("soft-thresholding", "shrink toward zero, then clip at zero"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Coordinate descent, which is the whole algorithm",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                def soft_threshold(rho, lam):
                    return np.sign(rho) * max(abs(rho) - lam, 0.0)

                def lasso(X, y, lam, iters=300):
                    n, p = X.shape
                    beta = np.zeros(p)
                    residual = y.copy()
                    norms = (X ** 2).sum(axis=0)

                    for _ in range(iters):
                        for j in range(p):
                            # Remove j's contribution, then recompute its correlation
                            # with what is left — that is rho.
                            residual += beta[j] * X[:, j]
                            rho = X[:, j] @ residual
                            beta[j] = soft_threshold(rho, lam * n) / norms[j]
                            residual -= beta[j] * X[:, j]
                    return beta

                # The whole difference from ridge sits in soft_threshold: subtract lambda and
                # clip at zero, instead of dividing by (1 + lambda) which never reaches it.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The correlated-predictor instability",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.linear_model import Lasso
                rng = np.random.default_rng(0)

                base = rng.normal(size=200)
                X = np.column_stack([base + rng.normal(scale=0.01, size=200),   # near-duplicates
                                     base + rng.normal(scale=0.01, size=200),
                                     rng.normal(size=200)])
                y = 2 * base + rng.normal(scale=0.1, size=200)

                # Refit on bootstrap resamples and watch which of the two twins survives.
                for seed in range(5):
                    idx = rng.integers(0, 200, 200)
                    fit = Lasso(alpha=0.05).fit(X[idx], y[idx])
                    print(np.round(fit.coef_, 3))   # the pair swaps between runs

                # ElasticNet keeps both, with the weight split between them.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("flask", 0xFF6366F1, "Biomarker Discovery", "Thousands of candidate genes, a handful that matter — lasso returns a shortlist rather than twenty thousand tiny weights."),
        ApplicationCard("chart", 0xFF818CF8, "Compressed Sensing", "Reconstructing a signal from far fewer samples than Nyquist requires, by assuming the signal is sparse in some basis."),
        ApplicationCard("finance", 0xFF10B981, "Interpretable Models", "Regulated settings often need a model a human can read, and a five-term model qualifies where a two-hundred-term one does not."),
    ),
    takeaways = listOf(
        "L1 produces exactly-zero coefficients, so fitting and feature selection happen together.",
        "The diamond's corners — soft-thresholding, in one dimension — are the reason zeros are hit rather than approached.",
        "No closed form: coordinate descent or LARS.",
        "Among correlated predictors it picks one arbitrarily and the choice is unstable; that is what ElasticNet fixes.",
    ),
    crossLinks = listOf(
        CrossLink("ridge_regression", "Ridge Regression (L2)"),
        CrossLink("elasticnet_regression", "ElasticNet Regression"),
        CrossLink("lars", "Least Angle Regression (LARS)"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
    ),
)
