package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val sarimaContent = TopicContent(
    topicId = "sarima",
    whatIsIt = listOf(
        "ARIMA has no way to say \"December resembles last December\". It can only reach twelve months back by carrying twelve lags, spending a parameter on each of the eleven months in between that it does not care about. SARIMA adds a second, seasonal copy of the same machinery operating at lag m — a seasonal difference, seasonal AR terms and seasonal MA terms — so the annual structure is expressed directly rather than reconstructed one month at a time.",
        "The single most important piece is the seasonal difference, ∇ₘyₜ = yₜ − yₜ₋ₘ. It compares each period with the same period a year earlier, which removes a stable seasonal pattern outright and, as a side effect, removes any linear trend along with it. In the simulation this is one toggle, and both numbers it moves are on screen: the lag-12 autocorrelation drops from 0.33 to −0.10, and the forecast error against the held-out year falls with it. That one subtraction is most of what SARIMA is.",
        "The costs are real and worth stating. The seasonal difference throws away m observations, which on monthly data is a whole year of a history that is usually short to begin with — with 48 training points, taking 12 leaves 36. The notation (p,d,q)(P,D,Q)ₘ has seven things to choose. And the standard advice is worth repeating: D ≤ 1 and d + D ≤ 2, because seasonal and ordinary differencing both remove trend and doing both twice manufactures noise. Auto-ARIMA searches this space by AICc and is what most people should use, but knowing which knob does what is the difference between reading its output and trusting it blindly.",
    ),
    steps = listOf(
        StepCard(1, "Identify the Period", "m = 12 monthly, 7 daily-with-weekly, 4 quarterly. Get this wrong and nothing else helps.", 0xFF10B981),
        StepCard(2, "Seasonal Difference First", "∇ₘ before ∇. It removes the seasonal pattern and the trend together.", 0xFF14B8A6),
        StepCard(3, "Difference Again Only If Needed", "d + D ≤ 2. Check the ACF before adding another round.", 0xFF06B6D4),
        StepCard(4, "Read the Seasonal Lags", "Spikes at m, 2m, 3m in the ACF and PACF identify Q and P, the same way the low lags identify p and q.", 0xFF6366F1),
        StepCard(5, "Fit and Check Residuals", "Ljung-Box at lag m specifically — leftover seasonal structure is the failure to look for.", 0xFF8B5CF6),
        StepCard(6, "Beat Seasonal-Naive", "The benchmark is ŷₜ₊ₕ = yₜ₊ₕ₋ₘ. A seasonal model that cannot beat it has not earned its parameters.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Model", "Φ(Bᵐ)φ(B)∇ᴰₘ∇ᵈyₜ = Θ(Bᵐ)θ(B)εₜ", "The seasonal polynomials multiply the ordinary ones."),
        FormulaEntry("Seasonal difference", "∇ₘyₜ = yₜ − yₜ₋ₘ", "The single most useful operation in seasonal forecasting."),
        FormulaEntry("Both differences", "∇∇ₘyₜ = yₜ − yₜ₋₁ − yₜ₋ₘ + yₜ₋ₘ₋₁", "Costs m + 1 observations."),
        FormulaEntry("Notation", "(p,d,q)(P,D,Q)ₘ", "Ordinary orders, then seasonal, then the period."),
        FormulaEntry("Airline model", "(0,1,1)(0,1,1)₁₂", "Box and Jenkins' default, and still hard to beat on monthly data."),
        FormulaEntry("Rule of thumb", "D ≤ 1 and d + D ≤ 2", "Both differences remove trend; doing it twice manufactures noise."),
        FormulaEntry("Rows lost", "d + D·m", "d=1, D=1, m=12 costs 13 observations before anything is fitted."),
    ),
    notationKey = listOf(
        NotationEntry("m", "seasonal period"),
        NotationEntry("P, D, Q", "seasonal AR, differencing and MA orders"),
        NotationEntry("∇ₘ", "seasonal difference at lag m"),
        NotationEntry("airline model", "(0,1,1)(0,1,1)₁₂ — the standard monthly baseline"),
        NotationEntry("SARIMAX", "the same model with external regressors added"),
        NotationEntry("seasonal-naive", "ŷₜ₊ₕ = yₜ₊ₕ₋ₘ, the benchmark to beat"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fitting it, and checking the seasonal difference earned its year",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from statsmodels.tsa.statespace.sarimax import SARIMAX
                from statsmodels.stats.diagnostic import acorr_ljungbox

                train, test = y[:-12], y[-12:]

                model = SARIMAX(
                    train,
                    order=(1, 1, 1),
                    seasonal_order=(1, 1, 1, 12),   # (P, D, Q, m)
                ).fit(disp=False)

                pred = model.forecast(12)
                rmse = float(np.sqrt(((pred - test) ** 2).mean()))
                naive = float(np.sqrt(((train[-12:].values - test.values) ** 2).mean()))
                print(round(rmse, 3), round(naive, 3))

                # Check lag m specifically -- leftover seasonal structure is the failure mode this
                # model exists to remove, and it hides at lag 12 rather than at low lags.
                print(acorr_ljungbox(model.resid, lags=[12, 24], return_df=True))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What the seasonal difference actually does, in two numbers",
            accentColor = 0xFF6366F1,
            code = """
                import pandas as pd
                import pmdarima as pm

                s = pd.Series(y)
                print(round(float(s.autocorr(12)), 3))               # e.g. 0.33 -- season present
                print(round(float(s.diff(12).dropna().autocorr(12)), 3))  # e.g. -0.10 -- removed
                print(len(s), "->", len(s.diff(12).dropna()))        # 12 observations gone

                # Auto-ARIMA searches (p,d,q)(P,D,Q) by AICc, using statistical tests to pick d
                # and D rather than guessing. Use it -- but read what it chose, because a D of 1
                # on a short series is a real cost and worth knowing you paid.
                auto = pm.auto_arima(s[:-12], seasonal=True, m=12, stepwise=True, suppress_warnings=True)
                print(auto.order, auto.seasonal_order)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("browser", 0xFF10B981, "Retail Demand", "Monthly sales with an annual cycle — the canonical use, and where the airline model came from."),
        ApplicationCard("globe", 0xFF14B8A6, "Energy Load Forecasting", "Daily and annual cycles together, usually as two seasonal periods or a seasonal model plus regressors."),
        ApplicationCard("flask", 0xFF6366F1, "Epidemiological Surveillance", "Weekly case counts with a strong annual pattern, where the baseline decides what counts as an outbreak."),
    ),
    takeaways = listOf(
        "A second copy of the AR/I/MA machinery running at lag m, expressing the cycle directly.",
        "The seasonal difference yₜ − yₜ₋ₘ removes a stable seasonal pattern and the trend together.",
        "It costs m observations, which is a whole year of a monthly history.",
        "D ≤ 1 and d + D ≤ 2 — differencing twice for trend manufactures noise.",
        "Always score against seasonal-naive; it is a strong benchmark and beating it is the bar.",
    ),
    crossLinks = listOf(
        CrossLink("arima", "ARIMA"),
        CrossLink("autoregression", "Autoregression (AR)"),
        CrossLink("prophet", "Prophet (by Meta)"),
        CrossLink("exponential_smoothing", "Exponential Smoothing (Holt-Winters)"),
    ),
)
