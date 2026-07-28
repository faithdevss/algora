package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val stepwiseRegressionContent = TopicContent(
    topicId = "stepwise_regression",
    whatIsIt = listOf(
        "Stepwise regression builds a model by greedy search over feature subsets. Forward selection starts empty and repeatedly adds whichever remaining feature improves the fit most; backward elimination starts full and removes the least useful; bidirectional does both, allowing a feature added earlier to be dropped later.",
        "It exists because exhaustive search does not scale — 2^p subsets is 10³⁰ at p = 100 — and greedy search is O(p²). But greedy is not optimal, and the failure mode is specific: a pair of features that is jointly predictive while neither is individually predictive will never be found, because forward selection judges each candidate alone. Backward elimination can catch that case, which is one reason the two directions disagree.",
        "The serious problem is inferential rather than computational, and it is why most modern practice has moved on. Every reported p-value, confidence interval and R² assumes the model was specified before seeing the data — and here it demonstrably was not. Selecting on the same data you then test on inflates significance, biases coefficients away from zero, and produces R² values that look excellent on noise: run stepwise on purely random predictors and it will confidently hand you a model with several \"significant\" terms. If you want prediction, use lasso or ElasticNet, which regularize while selecting. If you want inference, specify the model first, or use post-selection inference methods that correct for the search.",
    ),
    steps = listOf(
        StepCard(1, "Pick a Direction", "Forward from empty, backward from full, or bidirectional. They routinely disagree.", 0xFF6366F1),
        StepCard(2, "Score Every Candidate", "Fit the model with each remaining feature added, and measure the improvement.", 0xFF818CF8),
        StepCard(3, "Take the Best One", "Add the winner. Greedy — this choice is never reconsidered in pure forward selection.", 0xFF60A5FA),
        StepCard(4, "Stop on a Criterion", "AIC, BIC or adjusted R², which penalize count. Raw R² never decreases, so it cannot stop you.", 0xFF10B981),
        StepCard(5, "Distrust the Statistics", "Every p-value is now optimistic, because selection used the same data.", 0xFFEC4899),
        StepCard(6, "Validate Externally", "The only honest score comes from data untouched by the entire selection procedure.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Subset count", "2^p", "Exhaustive search is impossible past ~30 features."),
        FormulaEntry("Greedy cost", "O(p²) fits", "Why stepwise is used at all."),
        FormulaEntry("AIC", "2k − 2ln(L̂)", "Penalizes each added parameter by 2."),
        FormulaEntry("BIC", "k·ln(n) − 2ln(L̂)", "Penalizes harder as n grows; picks smaller models."),
        FormulaEntry("Adjusted R²", "1 − (1−R²)(n−1)/(n−k−1)", "Can decrease when a useless term is added."),
        FormulaEntry("Selection bias", "E[β̂ | selected] ≠ β", "Coefficients are biased away from zero by the selection itself."),
    ),
    notationKey = listOf(
        NotationEntry("k", "number of parameters in the current model"),
        NotationEntry("L̂", "maximized likelihood"),
        NotationEntry("greedy", "locally optimal at each step, not globally"),
        NotationEntry("post-selection inference", "methods that correct statistics for the search"),
        NotationEntry("data dredging", "searching until something looks significant"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Forward selection by adjusted R²",
            accentColor = 0xFF6366F1,
            code = """
                import statsmodels.api as sm

                def forward_select(X, y, names):
                    chosen, remaining, best_score = [], list(names), -np.inf
                    while remaining:
                        scored = []
                        for candidate in remaining:
                            cols = chosen + [candidate]
                            model = sm.OLS(y, sm.add_constant(X[cols])).fit()
                            scored.append((model.rsquared_adj, candidate))
                        score, winner = max(scored)
                        if score <= best_score:      # adjusted R^2 stopped improving
                            break
                        best_score, chosen = score, chosen + [winner]
                        remaining.remove(winner)
                    return chosen, best_score
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Stepwise on pure noise still finds \"significant\" predictors",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np, statsmodels.api as sm
                rng = np.random.default_rng(0)

                # 100 observations, 50 predictors, and a target independent of all of them.
                X = rng.normal(size=(100, 50))
                y = rng.normal(size=100)

                chosen = []
                for _ in range(5):
                    scores = [(sm.OLS(y, sm.add_constant(X[:, chosen + [j]])).fit().rsquared, j)
                              for j in range(50) if j not in chosen]
                    chosen.append(max(scores)[1])

                final = sm.OLS(y, sm.add_constant(X[:, chosen])).fit()
                print(final.rsquared, final.pvalues.round(4))
                # R^2 around 0.2 and several p-values below 0.05 — on data with no signal at
                # all. The p-values are computed as if these five columns had been chosen in
                # advance, and they were not.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("history", 0xFF6366F1, "Legacy Practice", "Still common in fields where it was standard training, which is why recognizing its problems matters more than using it."),
        ApplicationCard("search", 0xFF818CF8, "Exploratory Screening", "Defensible for generating hypotheses to test on new data — not for confirming them on the same data."),
        ApplicationCard("chart", 0xFF10B981, "What to Use Instead", "Lasso and ElasticNet select while regularizing, and their held-out error is an honest score."),
    ),
    takeaways = listOf(
        "Greedy subset search: O(p²) fits instead of 2^p, at the cost of no optimality guarantee.",
        "Forward selection cannot find pairs that are jointly but not individually predictive.",
        "All reported p-values and R² are optimistically biased, because selection used the same data.",
        "It will produce a confident model from pure noise — prefer lasso or ElasticNet for prediction, and pre-specification for inference.",
    ),
    crossLinks = listOf(
        CrossLink("lasso_regression", "Lasso Regression (L1)"),
        CrossLink("lars", "Least Angle Regression (LARS)"),
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
    ),
)
