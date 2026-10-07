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

internal val arimaContent = TopicContent(
    topicId = "arima",
    figure = Figure(
        caption = "What \"stationary\" looks like when it is measured: the autocorrelation of the " +
            "lab's 48 training months at lags 1 to 12, undifferenced and after one and two rounds " +
            "of differencing. Undifferenced it starts at 0.851 and decays slowly, still 0.292 at " +
            "lag 8 and 0.322 at lag 12 — every month still remembers the ones before it, which is " +
            "a trend, not a signal a model can use. One difference collapses lag 1 to 0.084 and " +
            "the whole profile into a band around zero: that is d = 1, and it costs exactly one " +
            "row. Take a second and lag 1 goes to −0.488 — over-differencing does not read as " +
            "\"more stationary\", it reads as a large negative autocorrelation the model then has " +
            "to fit. The one thing d = 1 does not remove is the annual cycle, still visible as " +
            "+0.250 at lag 12, which is the whole reason SARIMA exists.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "zero",
                    listOf(FigurePoint(0f, 0.5f), FigurePoint(1f, 0.5f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "d = 0",
                    listOf(
                        FigurePoint(0.000f, 0.925f), FigurePoint(0.091f, 0.855f),
                        FigurePoint(0.182f, 0.788f), FigurePoint(0.273f, 0.736f),
                        FigurePoint(0.364f, 0.706f), FigurePoint(0.455f, 0.674f),
                        FigurePoint(0.545f, 0.648f), FigurePoint(0.636f, 0.646f),
                        FigurePoint(0.727f, 0.647f), FigurePoint(0.818f, 0.665f),
                        FigurePoint(0.909f, 0.670f), FigurePoint(1.000f, 0.661f),
                    ),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    "d = 1",
                    listOf(
                        FigurePoint(0.000f, 0.542f), FigurePoint(0.091f, 0.531f),
                        FigurePoint(0.182f, 0.444f), FigurePoint(0.273f, 0.345f),
                        FigurePoint(0.364f, 0.461f), FigurePoint(0.455f, 0.408f),
                        FigurePoint(0.545f, 0.352f), FigurePoint(0.636f, 0.443f),
                        FigurePoint(0.727f, 0.459f), FigurePoint(0.818f, 0.564f),
                        FigurePoint(0.909f, 0.609f), FigurePoint(1.000f, 0.625f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "d = 2",
                    listOf(
                        FigurePoint(0.000f, 0.256f), FigurePoint(0.091f, 0.545f),
                        FigurePoint(0.182f, 0.523f), FigurePoint(0.273f, 0.377f),
                        FigurePoint(0.364f, 0.588f), FigurePoint(0.455f, 0.501f),
                        FigurePoint(0.545f, 0.415f), FigurePoint(0.636f, 0.537f),
                        FigurePoint(0.727f, 0.459f), FigurePoint(0.818f, 0.520f),
                        FigurePoint(0.909f, 0.508f), FigurePoint(1.000f, 0.506f),
                    ),
                    tone = FigureTone.Primary,
                ),
            ),
            markers = listOf(
                FigurePoint(0.000f, 0.925f, "0.851 · a trend", FigureTone.Warn),
                FigurePoint(0.000f, 0.256f, "−0.488 · too far"),
                FigurePoint(1.000f, 0.625f, "+0.250 · the season"),
            ),
            xLabel = "lag, 1 → 12 months",
            yLabel = "autocorrelation, −1 → +1",
        ),
    ),
    whatIsIt = listOf(
        "ARIMA is three ideas bolted together, and the name is the assembly instructions. The **I** differences the series until it is stationary, because everything else assumes a fixed mean. The **AR** regresses the differenced series on its own recent values. The **MA** regresses it on its own recent *errors* — not past observations, past surprises — which is how a shock that is not explained by the lags still gets to influence the next few periods.",
        "The MA part is the one that trips people up, partly because it shares a name with the moving average of raw values, which it is not. Its innovations εₜ are unobservable: they only exist relative to a fitted model, so the model and its own residuals have to be estimated together. Classically this is done by maximum likelihood; the two-stage Hannan–Rissanen procedure — fit a long AR, take its residuals as estimates of the innovations, then regress on both sets of lags — is the standard initialisation and is what this topic's simulation runs, so the fit on screen is a real estimator rather than a scripted animation.",
        "Differencing is where judgment enters. Each round removes one order of trend and costs a row; d = 1 handles a linear trend, d = 2 a quadratic one, and d = 3 is almost always a mistake. The simulation reports the lag-1 autocorrelation as you turn d up: undifferenced it sits near 0.87, which is what non-stationary looks like in one number, and one difference brings it to about 0.17. Over-differencing is a real failure with its own signature — the autocorrelation driven strongly negative and the forecast made noisier, not better. And note what plain ARIMA still cannot do: it has no seasonal term, so on the monthly series in the lab it loses to the seasonal-naive benchmark at every setting. SARIMA is the fix, and that is the next topic.",
    ),
    steps = listOf(
        StepCard(1, "Plot It, Then Test It", "Trend, variance changes, level shifts. A log transform first if the variance grows with the level.", 0xFF10B981),
        StepCard(2, "Difference for Stationarity", "d = 1 for a linear trend. Check the ACF after each round; stop as soon as it is enough.", 0xFF14B8A6),
        StepCard(3, "Read ACF and PACF", "PACF cuts off at p for a pure AR; ACF cuts off at q for a pure MA. Mixed models blur both.", 0xFF06B6D4),
        StepCard(4, "Fit (p, d, q)", "Maximum likelihood, or Hannan-Rissanen two-stage as the initialisation.", 0xFF6366F1),
        StepCard(5, "Check the Residuals", "They must look like white noise. Ljung-Box is the test; a failure means the model missed structure.", 0xFF8B5CF6),
        StepCard(6, "Forecast and Integrate", "Forecast on the differenced scale, then undo the differencing to get back to the original units.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Backshift operator", "Byₜ = yₜ₋₁", "Notation that makes the next two lines readable."),
        FormulaEntry("Differencing", "∇ᵈyₜ = (1−B)ᵈyₜ", "d = 1 is yₜ − yₜ₋₁."),
        FormulaEntry("ARIMA(p,d,q)", "φ(B)(1−B)ᵈyₜ = c + θ(B)εₜ", "The whole model in one line."),
        FormulaEntry("MA(q)", "yₜ = μ + εₜ + Σⱼ θⱼεₜ₋ⱼ", "Past *errors*, not past values."),
        FormulaEntry("MA memory", "zero autocorrelation beyond lag q", "Which is why the ACF cutting off identifies q."),
        FormulaEntry("Invertibility", "roots of θ(B) outside the unit circle", "Otherwise the MA has no equivalent AR form and is not identified."),
        FormulaEntry("Ljung-Box", "Q = n(n+2)Σ ρ̂ₖ²/(n−k)", "Residual autocorrelation test. A small p-value means structure is left."),
        FormulaEntry("Selection", "AICc, on the same d", "Models with different d have different data and cannot be compared by AIC."),
    ),
    notationKey = listOf(
        NotationEntry("p, d, q", "AR order, differencing order, MA order"),
        NotationEntry("B", "backshift operator"),
        NotationEntry("∇", "difference operator, (1 − B)"),
        NotationEntry("εₜ", "innovation — the unobservable one-step surprise"),
        NotationEntry("integration", "undoing the differencing to return to the original scale"),
        NotationEntry("Hannan-Rissanen", "two-stage estimator: long AR for the innovations, then regress on both"),
        NotationEntry("over-differencing", "differencing past stationarity; shows up as strong negative autocorrelation"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The Box-Jenkins loop, with the residual check that closes it",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from statsmodels.tsa.arima.model import ARIMA
                from statsmodels.tsa.stattools import adfuller
                from statsmodels.stats.diagnostic import acorr_ljungbox

                train, test = y[:-12], y[-12:]

                # Is it stationary? A high p-value here means "cannot reject a unit root".
                print(round(adfuller(train)[1], 4))          # e.g. 0.71 -> difference it
                print(round(adfuller(np.diff(train))[1], 4)) # e.g. 0.001 -> d = 1 is enough

                model = ARIMA(train, order=(2, 1, 1)).fit()
                print(model.summary())

                # The check most people skip: residuals must be indistinguishable from noise.
                # A small p-value means the model left structure on the table.
                print(acorr_ljungbox(model.resid, lags=[12], return_df=True))

                pred = model.forecast(12)
                print(float(np.sqrt(((pred - test) ** 2).mean())).__round__(3))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Over-differencing, and the signature it leaves",
            accentColor = 0xFF8B5CF6,
            code = """
                import numpy as np
                import pandas as pd

                s = pd.Series(y)
                for d in range(4):
                    x = s.diff(1).dropna() if d else s
                    for _ in range(max(d - 1, 0)):
                        x = x.diff(1).dropna()
                    print(d, "lag-1 acf", round(float(x.autocorr(1)), 3),
                          "sd", round(float(x.std()), 3))

                # Typical output shape:
                #   d=0  acf  0.87   <- non-stationary: the series remembers where it was
                #   d=1  acf  0.17   <- about right
                #   d=2  acf -0.44   <- over-differenced
                #   d=3  acf -0.60   <- worse, and sd is now RISING
                #
                # Two tells: the autocorrelation swings strongly negative, and the standard
                # deviation stops falling and starts climbing. Differencing once more is not a
                # safe default -- it manufactures noise the model then has to fit.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Economic Forecasting", "Inflation, unemployment, GDP — the setting Box and Jenkins wrote the method for, and still a standard baseline."),
        ApplicationCard("chip", 0xFF14B8A6, "Capacity and Demand", "Short-horizon forecasts where the series is regular and the data is short enough to make deep learning silly."),
        ApplicationCard("flask", 0xFF6366F1, "Intervention Analysis", "ARIMA with an added regressor measures whether a policy or a launch actually changed the series."),
    ),
    takeaways = listOf(
        "I differences for stationarity, AR uses past values, MA uses past errors.",
        "MA innovations are unobservable, so the model and its residuals are estimated together.",
        "d = 1 for a linear trend, d = 2 for a quadratic; over-differencing shows as strong negative autocorrelation.",
        "Residuals must pass a white-noise test — that check is what makes the fit trustworthy.",
        "No seasonal term: on seasonal data plain ARIMA can lose to seasonal-naive, and SARIMA is the fix.",
    ),
    crossLinks = listOf(
        CrossLink("autoregression", "Autoregression (AR)"),
        CrossLink("sarima", "SARIMA (Seasonal)"),
        CrossLink("moving_average", "Moving Average (MA)"),
        CrossLink("exponential_smoothing", "Exponential Smoothing (Holt-Winters)"),
    ),
)
