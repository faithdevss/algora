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

internal val bayesianRidgeContent = TopicContent(
    topicId = "bayesian_ridge",
    figure = Figure(
        caption = "Probed at x = 1.5, 4.5 and 7.5 across a design with a deliberate gap between 3 and 6: " +
            "predictive std is low in both dense regions and several times higher in the middle, exactly " +
            "where no training point ever fell. A plain ridge fit produces the same mean curve here and " +
            "carries no signal at all that the middle third of it is a guess rather than a measurement.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "predictive std",
                    listOf(
                        FigurePoint(0f, 0.15f), FigurePoint(0.25f, 0.35f), FigurePoint(0.5f, 0.55f),
                        FigurePoint(0.75f, 0.35f), FigurePoint(1f, 0.15f),
                    ),
                ),
            ),
            markers = listOf(FigurePoint(0.5f, 0.55f, "gap: std ≈ 3× dense region")),
            xLabel = "x (dense · gap · dense)",
            yLabel = "predictive std",
        ),
    ),
    whatIsIt = listOf(
        "Bayesian ridge regression returns a distribution over coefficients rather than a single vector. Put a Gaussian prior β ~ N(0, α⁻¹I) on the weights and a Gaussian likelihood on the data, and the posterior is Gaussian too — available in closed form, with a mean and a full covariance matrix.",
        "The mean of that posterior is exactly the ridge estimate with λ = α/β. So ordinary ridge was already doing Bayesian inference; it simply threw away everything except the peak. What the covariance buys you is calibrated uncertainty: the predictive variance at a point is σ² + φ(x)ᵀSφ(x), where the second term grows wherever the data does not constrain the fit. The model can say \"I do not know here\", and it says it loudest in the gaps between observations and beyond the edges of the training range — which is precisely where a point estimate is most confidently wrong.",
        "The other practical gain is that the hyperparameters stop being a cross-validation problem. Maximizing the marginal likelihood — evidence maximization, or empirical Bayes — estimates α and the noise level from the data directly, in a single fit, with no held-out split. This is why Bayesian ridge is attractive on small datasets where carving out a validation set is expensive. The limits are the assumptions: Gaussian everything, and closed form only for linear models. Drop either and you are into MCMC or variational inference, which is where the cost comes back.",
    ),
    steps = listOf(
        StepCard(1, "Put a Prior on β", "N(0, α⁻¹I) — a belief that coefficients are small before seeing data.", 0xFF6366F1),
        StepCard(2, "Write the Likelihood", "y ~ N(Xβ, σ²I). Gaussian noise around a linear mean.", 0xFF818CF8),
        StepCard(3, "Multiply, and Get a Gaussian", "Conjugacy: the posterior is Gaussian with closed-form mean and covariance.", 0xFF60A5FA),
        StepCard(4, "Note the Mean Is Ridge", "The posterior mean equals the ridge solution with λ = α/β. Ridge is its MAP estimate.", 0xFF10B981),
        StepCard(5, "Propagate to Predictions", "Predictive variance = noise + φᵀSφ. The second term is the model's own ignorance.", 0xFFF59E0B),
        StepCard(6, "Learn α and σ² from Evidence", "Maximize the marginal likelihood — no cross-validation split needed.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Prior", "p(β) = N(0, α⁻¹I)", "α is the prior precision."),
        FormulaEntry("Posterior covariance", "S = (αI + βXᵀX)⁻¹", "β here is noise precision, 1/σ²."),
        FormulaEntry("Posterior mean", "μ = βSXᵀy", "Identical to ridge with λ = α/β."),
        FormulaEntry("Predictive variance", "σ²(x) = 1/β + φ(x)ᵀSφ(x)", "Irreducible noise plus model uncertainty."),
        FormulaEntry("Evidence", "p(y|α,β) = ∫ p(y|β,w)p(w|α)dw", "Maximized to choose the hyperparameters."),
        FormulaEntry("Effective parameters", "γ = Σᵢ λᵢ/(α + λᵢ)", "How many directions the data actually pinned down."),
    ),
    notationKey = listOf(
        NotationEntry("α", "prior precision on the coefficients"),
        NotationEntry("β", "noise precision, 1/σ² (not a coefficient here)"),
        NotationEntry("S", "posterior covariance matrix"),
        NotationEntry("φ(x)", "the basis functions evaluated at x"),
        NotationEntry("conjugacy", "prior and posterior in the same family, giving closed form"),
        NotationEntry("epistemic vs aleatoric", "model uncertainty, which data reduces, versus noise, which it does not"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The posterior, and the uncertainty it exposes",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np
                from sklearn.linear_model import BayesianRidge

                model = BayesianRidge(compute_score=True).fit(X, y)
                print(model.alpha_, model.lambda_)     # both estimated from the data itself

                # return_std is the whole point: a prediction and how much to trust it.
                mean, std = model.predict(X_test, return_std=True)

                # Two components, and they behave differently:
                #   aleatoric  = 1/alpha_  -> noise in y. More data does NOT reduce it.
                #   epistemic  = phi^T S phi -> ignorance about beta. More data DOES.
                print(np.sqrt(1 / model.alpha_))       # the floor std can never go below
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the band widens where data is sparse",
            accentColor = 0xFF10B981,
            code = """
                # Build a design matrix with a deliberate gap, then measure the predictive
                # std inside the gap against a dense region.
                x = np.concatenate([np.linspace(0, 3, 30), np.linspace(6, 9, 30)])
                y = np.sin(x) + rng.normal(scale=0.2, size=60)
                Phi = np.vander(x, 6, increasing=True)

                fit = BayesianRidge().fit(Phi, y)
                for probe in (1.5, 4.5, 7.5):                 # dense, gap, dense
                    _, std = fit.predict(np.vander([probe], 6, increasing=True),
                                         return_std=True)
                    print(probe, round(float(std), 3))
                # The middle value is several times the other two. A plain ridge fit gives
                # the same mean curve and no signal at all that the gap is a guess.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("flask", 0xFF6366F1, "Active Learning", "Sample next wherever predictive variance is highest — the model chooses its own most informative experiment."),
        ApplicationCard("chip", 0xFF818CF8, "Bayesian Optimization", "Hyperparameter search balances predicted value against predicted uncertainty, which requires both to exist."),
        ApplicationCard("robot", 0xFF10B981, "Safety-Critical Prediction", "A prediction paired with an honest confidence interval is actionable in a way a bare number is not."),
    ),
    takeaways = listOf(
        "The posterior mean is exactly ridge — Bayesian ridge keeps the covariance that ridge discards.",
        "Predictive variance separates irreducible noise from model ignorance, and the latter grows where data is sparse.",
        "Evidence maximization estimates the hyperparameters in one fit, with no validation split.",
        "Closed form holds only under Gaussian assumptions and linear models; anything else needs MCMC or variational inference.",
    ),
    crossLinks = listOf(
        CrossLink("ridge_regression", "Ridge Regression (L2)"),
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
