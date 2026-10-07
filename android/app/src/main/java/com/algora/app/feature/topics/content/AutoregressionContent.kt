package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val autoregressionContent = TopicContent(
    topicId = "autoregression",
    whatIsIt = listOf(
        "An autoregressive model is ordinary linear regression with an unusual choice of features: the series' own recent values. yₜ = c + φ₁yₜ₋₁ + … + φₚyₜ₋ₚ + εₜ. Build the lagged design matrix and least squares does the rest, which is why AR is the natural bridge from regression to time series — nothing new is required except the discipline about what you are allowed to know when.",
        "Two consequences follow immediately and neither is obvious from the regression framing. First, the model is only usable if the series is stationary: if |φ₁| ≥ 1 the process is non-stationary (φ₁ = 1 is a random walk, |φ₁| > 1 explodes) and the process has no fixed mean to regress toward, which is why ARIMA's differencing step exists. Second, forecasting more than one step means feeding predictions back in as inputs, so errors compound with the horizon — the twelfth prediction is built almost entirely from earlier predictions.",
        "The limitation the simulation is built to show is subtler than a failure. An AR model has no seasonal term at all, so the only way it can reach an event twelve months back is to carry a lag for every month in between. Slide p across the lab and watch it happen: against the seasonal-naive benchmark — \"next year looks like last year\" — every order up to eight loses, and from nine onward the model finally spans the annual cycle and wins. It is not that AR cannot represent seasonality; it is that it has to spend nine to twelve parameters, estimated from thirty-six usable rows, to say what one seasonal difference says in a single subtraction. That price is exactly why SARIMA and Holt-Winters exist.",
    ),
    steps = listOf(
        StepCard(1, "Check Stationarity First", "A trending series breaks the model's premise. Difference it, or use ARIMA, before fitting.", 0xFF10B981),
        StepCard(2, "Choose p", "PACF cuts off at the true order for a pure AR process — the clearest diagnostic in the whole family.", 0xFF14B8A6),
        StepCard(3, "Build the Lagged Design", "Row t holds yₜ₋₁ … yₜ₋ₚ. The first p rows have no history and are dropped.", 0xFF06B6D4),
        StepCard(4, "Fit by Least Squares", "Or Yule-Walker, or MLE. On a stationary series they agree closely.", 0xFF6366F1),
        StepCard(5, "Forecast Recursively", "Each prediction becomes the next input, so uncertainty grows with the horizon.", 0xFF8B5CF6),
        StepCard(6, "Score Against a Benchmark", "Naive or seasonal-naive. An AR model that loses to them has told you something important.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("AR(p)", "yₜ = c + Σᵢ₌₁ᵖ φᵢyₜ₋ᵢ + εₜ", "Linear regression on the series' own past."),
        FormulaEntry("AR(1) mean", "μ = c/(1−φ₁)", "Undefined at φ₁ = 1 — the boundary of stationarity."),
        FormulaEntry("Stationarity", "roots of 1 − φ₁z − … − φₚzᵖ outside the unit circle", "For AR(1) it reduces to |φ₁| < 1."),
        FormulaEntry("Yule-Walker", "Γφ = γ", "Fit from the autocovariances rather than the design matrix."),
        FormulaEntry("PACF", "correlation at lag k with 1…k−1 removed", "Cuts off after p for an AR(p). This is how p is chosen."),
        FormulaEntry("Recursive forecast", "ŷₜ₊ₕ = c + Σφᵢŷₜ₊ₕ₋ᵢ", "Predictions feed back in; error accumulates with h."),
        FormulaEntry("Order selection", "AIC = 2k − 2ln L", "Or BIC, which penalises parameters harder and picks smaller p."),
    ),
    notationKey = listOf(
        NotationEntry("p", "the number of lags — the model's only structural choice"),
        NotationEntry("φᵢ", "the coefficient on lag i"),
        NotationEntry("εₜ", "white noise: zero mean, constant variance, uncorrelated"),
        NotationEntry("stationary", "mean, variance and autocovariance do not change over time"),
        NotationEntry("ACF / PACF", "autocorrelation and partial autocorrelation, the two order-selection plots"),
        NotationEntry("seasonal-naive", "ŷₜ₊ₕ = yₜ₊ₕ₋ₘ. The benchmark any seasonal model must beat"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "AR as regression on lags — literally",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def fit_ar(y, p):
                    # Row t is [1, y[t-1], ..., y[t-p]]. Nothing here is time-series specific;
                    # it is a design matrix, and the answer is ordinary least squares.
                    X = np.column_stack([np.ones(len(y) - p)] +
                                        [y[p - i - 1:len(y) - i - 1] for i in range(p)])
                    return np.linalg.lstsq(X, y[p:], rcond=None)[0]

                def forecast(y, phi, horizon):
                    p = len(phi) - 1
                    history = list(y)
                    out = []
                    for _ in range(horizon):
                        # The prediction is appended to the history and used as the next input:
                        # this is what makes multi-step error compound.
                        nxt = phi[0] + sum(phi[i + 1] * history[-i - 1] for i in range(p))
                        history.append(nxt)
                        out.append(nxt)
                    return out
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Choosing p, and checking it earned its keep",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np
                from statsmodels.tsa.ar_model import AutoReg
                from statsmodels.graphics.tsaplots import plot_pacf

                train, test = y[:-12], y[-12:]

                # PACF cuts off after the true order for a pure AR process. Lags beyond that are
                # inside the confidence band and are not evidence of anything.
                plot_pacf(train, lags=24)

                best = min(range(1, 13), key=lambda p: AutoReg(train, lags=p).fit().aic)
                model = AutoReg(train, lags=best).fit()
                pred = model.forecast(12)

                rmse = float(np.sqrt(((pred - test) ** 2).mean()))
                naive = float(np.sqrt(((train[-12:].values - test.values) ** 2).mean()))
                print(best, round(rmse, 3), round(naive, 3))

                # If rmse > naive, the reason is usually that p is too small to span the season:
                # AR has no seasonal term, so reaching m periods back costs about m parameters.
                # Raising p until it does is possible and expensive -- a seasonal difference says
                # the same thing with one subtraction, which is what SARIMA and Holt-Winters use.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Volatility Modelling", "GARCH is autoregression applied to squared returns — the same idea one level up."),
        ApplicationCard("chip", 0xFF14B8A6, "Signal Prediction", "Linear predictive coding in speech codecs is an AR model fitted per frame."),
        ApplicationCard("globe", 0xFF6366F1, "Econometric Series", "Vector autoregression extends this to several series predicting each other, which is most of macro forecasting."),
    ),
    takeaways = listOf(
        "It is linear regression on lagged values — the design matrix is the only new idea.",
        "Stationarity is a precondition, not a nicety; |φ₁| ≥ 1 has no fixed mean to regress to.",
        "PACF cutting off is the standard way to choose p.",
        "Multi-step forecasts feed predictions back in, so error compounds with the horizon.",
        "No seasonal term: reaching m periods back costs roughly m parameters, which is why the seasonal models exist.",
    ),
    crossLinks = listOf(
        CrossLink("arima", "ARIMA"),
        CrossLink("sarima", "SARIMA (Seasonal)"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("rnn", "RNNs"),
    ),
)
