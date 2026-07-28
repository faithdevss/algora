package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val elasticNetRegressionContent = TopicContent(
    topicId = "elasticnet_regression",
    whatIsIt = listOf(
        "ElasticNet carries both penalties at once: λ(α·Σ|βⱼ| + (1−α)/2·Σβⱼ²). The L1 part still zeroes useless coefficients, and the L2 part fixes the specific failure lasso has when predictors are correlated.",
        "That failure is worth stating precisely. Given two near-identical predictors, lasso's diamond has its corner on one axis or the other, so the solution keeps one and zeroes its twin — and which twin survives is decided by noise, flipping between bootstrap resamples. ElasticNet's L2 component makes the objective strictly convex, which produces the grouping effect: correlated predictors get near-equal coefficients and enter or leave the model together. If the selected set is something you intend to report rather than merely predict with, this is the difference between a finding and an artifact.",
        "It also removes lasso's structural ceiling. When p > n, lasso can select at most n predictors no matter how many are genuinely relevant; ElasticNet has no such limit. The cost is a second hyperparameter — but the search is well-behaved in practice, since α is usually tuned over a short list like [0.1, 0.5, 0.7, 0.9, 0.95, 1.0] rather than a continuum, and λ is swept over a path for each.",
    ),
    steps = listOf(
        StepCard(1, "Standardize", "Both penalties are scale-dependent, so this is not optional.", 0xFF6366F1),
        StepCard(2, "Choose the Mix α", "1 is pure lasso, 0 is pure ridge. It sets what kind of regularization you want.", 0xFF818CF8),
        StepCard(3, "Choose the Strength λ", "Separately, it sets how much. The two dials are genuinely independent.", 0xFF60A5FA),
        StepCard(4, "Solve by Coordinate Descent", "Soft-threshold for the L1 part, divide by (1 + L2) for the L2 part.", 0xFF10B981),
        StepCard(5, "Get the Grouping Effect", "Correlated predictors now enter together with similar weights instead of one crowding out the rest.", 0xFFF59E0B),
        StepCard(6, "Tune Both by CV", "Grid over a short α list crossed with a λ path. Cheap, because the path is computed warm-started.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min ‖y − Xβ‖²/2n + λ(α·Σ|βⱼ| + ((1−α)/2)·Σβⱼ²)", "Both penalties, mixed by α."),
        FormulaEntry("Update", "βⱼ = S(ρⱼ, λα) / (1 + λ(1−α))", "Soft-threshold, then shrink."),
        FormulaEntry("α = 1", "pure lasso", "Sparse, unstable among correlated features."),
        FormulaEntry("α = 0", "pure ridge", "Stable, never sparse."),
        FormulaEntry("Grouping effect", "|βᵢ − βⱼ| → 0 as corr(xᵢ,xⱼ) → 1", "Correlated predictors get equal weight."),
        FormulaEntry("Constraint shape", "rounded diamond", "Corners for sparsity, curvature for stability."),
    ),
    notationKey = listOf(
        NotationEntry("α", "L1 ratio — the mix between the two penalties"),
        NotationEntry("λ", "overall regularization strength"),
        NotationEntry("S(·,·)", "the soft-threshold operator"),
        NotationEntry("grouping effect", "correlated predictors receiving similar coefficients"),
        NotationEntry("warm start", "initializing each λ's solve from the previous one's answer"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tuning both hyperparameters",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.linear_model import ElasticNetCV
                from sklearn.preprocessing import StandardScaler
                from sklearn.pipeline import make_pipeline

                model = make_pipeline(
                    StandardScaler(),
                    ElasticNetCV(
                        # sklearn's l1_ratio is alpha here; its alpha is lambda. Values are
                        # clustered near 1 because anything below ~0.1 behaves like ridge.
                        l1_ratio=[0.1, 0.5, 0.7, 0.9, 0.95, 0.99, 1.0],
                        n_alphas=100,          # lambda path, warm-started along its length
                        cv=5,
                    ),
                )
                model.fit(X, y)
                fit = model[-1]
                print(fit.l1_ratio_, fit.alpha_, (fit.coef_ != 0).sum())
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The grouping effect, measured",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from sklearn.linear_model import Lasso, ElasticNet
                rng = np.random.default_rng(1)

                base = rng.normal(size=300)
                X = np.column_stack([base + rng.normal(scale=0.02, size=300),
                                     base + rng.normal(scale=0.02, size=300),
                                     rng.normal(size=300)])
                y = 2 * base + rng.normal(scale=0.1, size=300)

                print(np.round(Lasso(alpha=0.05).fit(X, y).coef_, 3))
                # -> one twin carries all the weight, the other is exactly 0

                print(np.round(ElasticNet(alpha=0.05, l1_ratio=0.5).fit(X, y).coef_, 3))
                # -> the twins split the weight roughly evenly, and both survive
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("flask", 0xFF6366F1, "Genomics", "Genes in a shared pathway are correlated by biology; keeping the group is the scientifically meaningful result."),
        ApplicationCard("chart", 0xFF818CF8, "Marketing Mix", "Ad channels move together, and attributing everything to one of them is an artifact rather than an insight."),
        ApplicationCard("finance", 0xFF10B981, "Netflix Prize", "The competition that popularized it — many correlated predictors, and a need for both sparsity and stability."),
    ),
    takeaways = listOf(
        "ElasticNet mixes L1 and L2, so α sets the kind of regularization and λ sets the amount.",
        "The L2 term makes the objective strictly convex, producing the grouping effect for correlated predictors.",
        "It escapes lasso's structural limit of at most n selected features when p > n.",
        "Two hyperparameters, but α is tuned over a short list and λ over a warm-started path.",
    ),
    crossLinks = listOf(
        CrossLink("lasso_regression", "Lasso Regression (L1)"),
        CrossLink("ridge_regression", "Ridge Regression (L2)"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
