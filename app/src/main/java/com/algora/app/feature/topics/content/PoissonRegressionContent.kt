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

internal val poissonRegressionContent = TopicContent(
    topicId = "poisson_regression",
    figure = Figure(
        caption = "OLS fit to the same counts runs straight through zero and keeps going, predicting a " +
            "negative expected count past the right edge of the data. exp(xᵀβ) cannot do that by " +
            "construction — it curves upward instead, and every coefficient reads as a rate ratio: " +
            "exp(βⱼ) multiplies the expected count rather than adding to it, which is why the two curves " +
            "agree near the middle of the data and disagree completely at the edges.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "OLS (linear)",
                    listOf(
                        FigurePoint(0f, 0.6f), FigurePoint(0.25f, 0.45f), FigurePoint(0.5f, 0.30f),
                        FigurePoint(0.75f, 0.15f), FigurePoint(1f, 0.0f),
                    ),
                    tone = FigureTone.Warn,
                    dashed = true,
                ),
                FigureSeries(
                    "Poisson mean, exp(xᵀβ)",
                    listOf(
                        FigurePoint(0f, 0.15f), FigurePoint(0.25f, 0.22f), FigurePoint(0.5f, 0.35f),
                        FigurePoint(0.75f, 0.55f), FigurePoint(1f, 0.85f),
                    ),
                ),
            ),
            markers = listOf(FigurePoint(1f, 0.0f, "OLS: negative past here")),
            xLabel = "x",
            yLabel = "expected count",
        ),
    ),
    whatIsIt = listOf(
        "Poisson regression models counts: emails per hour, defects per batch, claims per policy. It assumes y follows a Poisson distribution whose mean is exp(xᵀβ), which makes it a generalized linear model with a log link.",
        "Fitting counts with ordinary least squares is wrong in three specific ways, and each has a visible consequence. A straight line eventually goes negative, predicting an impossible number of events. OLS assumes constant variance, but a Poisson's variance equals its mean, so high-count regions are genuinely noisier and get systematically over-weighted. And effects on counts are usually multiplicative rather than additive — doubling the exposure doubles the expected count — which the log link expresses directly and a linear mean does not.",
        "The one assumption to check afterwards is equidispersion: Poisson forces variance = mean, and real count data very often has variance well above it. When that happens standard errors come out too small and marginal predictors look significant. The diagnostic is the ratio of Pearson chi-square to residual degrees of freedom; well above 1 means overdispersion, and the fix is a negative binomial model or quasi-Poisson standard errors. Also remember the offset: if exposure varies between observations — different follow-up times, different populations — you must include log(exposure) as a fixed term, or you are modelling raw counts where you meant rates.",
    ),
    steps = listOf(
        StepCard(1, "Confirm the Response Is a Count", "Non-negative integers from a counting process. Not a proportion, not a bounded score.", 0xFF6366F1),
        StepCard(2, "Use the Log Link", "log(μ) = xᵀβ, so μ = exp(xᵀβ) — positive for every input, by construction.", 0xFF818CF8),
        StepCard(3, "Add an Offset if Exposure Varies", "Include log(exposure) with a fixed coefficient of 1 to model rates rather than counts.", 0xFF60A5FA),
        StepCard(4, "Fit by IRLS", "Maximum likelihood via iteratively reweighted least squares, with weights equal to the fitted mean.", 0xFF10B981),
        StepCard(5, "Read Coefficients Multiplicatively", "exp(βⱼ) is the factor by which the expected count changes per unit of xⱼ.", 0xFFF59E0B),
        StepCard(6, "Test for Overdispersion", "If Pearson χ²/df ≫ 1, switch to negative binomial or quasi-Poisson.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Distribution", "P(y=k) = e^(−μ)μᵏ/k!", "The Poisson probability mass function."),
        FormulaEntry("Link", "log(μ) = β₀ + β₁x₁ + …", "So μ = exp(xᵀβ) > 0 always."),
        FormulaEntry("Mean = variance", "E[y] = Var[y] = μ", "Equidispersion — the assumption to check."),
        FormulaEntry("Coefficient meaning", "exp(βⱼ) = rate ratio", "Multiplicative, not additive."),
        FormulaEntry("Deviance", "2Σ[y·log(y/μ̂) − (y − μ̂)]", "The GLM analogue of residual sum of squares."),
        FormulaEntry("With exposure", "log(μ) = log(t) + xᵀβ", "The offset that turns counts into rates."),
    ),
    notationKey = listOf(
        NotationEntry("μ", "expected count"),
        NotationEntry("link function", "the transform relating the linear predictor to the mean"),
        NotationEntry("offset", "a term with coefficient fixed at 1, for exposure"),
        NotationEntry("overdispersion", "variance exceeding the mean"),
        NotationEntry("IRLS", "iteratively reweighted least squares"),
        NotationEntry("rate ratio", "exp(β) — the multiplicative effect of a predictor"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fit, offset, and the overdispersion check",
            accentColor = 0xFF6366F1,
            code = """
                import statsmodels.api as sm
                import numpy as np

                # exposure varies per row, so model the RATE: log(exposure) enters as an
                # offset, with its coefficient pinned to 1 rather than estimated.
                model = sm.GLM(
                    counts,
                    sm.add_constant(X),
                    family=sm.families.Poisson(),
                    offset=np.log(exposure),
                ).fit()

                print(np.exp(model.params))     # rate ratios, not additive effects

                # The single most important diagnostic. Poisson FORCES var = mean; if the
                # data disagrees, the standard errors above are too small and p-values lie.
                dispersion = model.pearson_chi2 / model.df_resid
                print(dispersion)               # ~1 is fine; 2+ means switch models
                if dispersion > 1.5:
                    model = sm.GLM(counts, sm.add_constant(X),
                                   family=sm.families.NegativeBinomial(),
                                   offset=np.log(exposure)).fit()
            """.trimIndent(),
        ),
        CodeBlock(
            title = "IRLS, which is what \"fit\" is actually doing",
            accentColor = 0xFF10B981,
            code = """
                def poisson_irls(X, y, iters=25):
                    beta = np.zeros(X.shape[1])
                    beta[0] = np.log(max(y.mean(), 0.5))
                    for _ in range(iters):
                        eta = X @ beta
                        mu = np.exp(np.clip(eta, -20, 20))
                        w = mu                       # Poisson weights ARE the fitted means
                        z = eta + (y - mu) / w       # the working response
                        # One weighted least-squares solve per iteration.
                        W = np.diag(w)
                        beta = np.linalg.solve(X.T @ W @ X, X.T @ W @ z)
                    return beta

                # Weighting by mu is exactly the correction OLS lacks: high-count rows are
                # genuinely noisier, so they are down-weighted relative to their magnitude.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("finance", 0xFF6366F1, "Insurance Claims", "Claim frequency per policy-year, with the offset handling policies held for different lengths of time."),
        ApplicationCard("globe", 0xFF818CF8, "Epidemiology", "Case counts per population, where the offset is log(population) and the output is an incidence rate."),
        ApplicationCard("browser", 0xFF10B981, "Web Analytics", "Page views, clicks and error counts — count outcomes where a linear model can predict negative traffic."),
    ),
    takeaways = listOf(
        "Poisson regression models counts with a log link, so predictions are positive by construction.",
        "Coefficients are multiplicative: exp(β) is a rate ratio, not an additive effect.",
        "Varying exposure requires an offset of log(exposure), or you model counts where you meant rates.",
        "Always check dispersion — variance above the mean means negative binomial, not Poisson.",
    ),
    crossLinks = listOf(
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
