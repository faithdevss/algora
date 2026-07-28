package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val exponentialSmoothingContent = TopicContent(
    topicId = "exponential_smoothing",
    whatIsIt = listOf(
        "A moving average treats the last w observations as equally informative and everything before them as worthless. Exponential smoothing fixes both halves of that: every past observation contributes, with a weight that decays geometrically as it recedes. One line does it — ℓₜ = αyₜ + (1−α)ℓₜ₋₁ — and expanding the recursion shows the weights are α, α(1−α), α(1−α)², and so on, summing to one.",
        "That is simple exponential smoothing, which has no notion of trend or season, so its forecast is a flat line. Holt adds a second recursion for the slope; Holt-Winters adds a third for the seasonal figures, each stored per position in the cycle and updated once per cycle. Three components, three recursions, three smoothing parameters — α for the level, β for the trend, γ for the season — and a forecast that is level plus h steps of trend plus the seasonal figure for the month you are forecasting.",
        "The parameters are commonly described as controlling responsiveness, which invites setting them high. They control *how little smoothing is applied*, which is not the same thing: at α = 0.8 the level is 80% the newest observation, so noise passes straight through and the forecast inherits it. The simulation shows this as a measured gap — a setting can fit the observed history well and forecast the held-out year badly, and the readouts print both numbers side by side so the difference is visible rather than argued. In production these are fitted by minimising one-step error, not chosen by hand, and the additive/multiplicative choice (does the seasonal swing grow with the level?) matters as much as any of them.",
    ),
    steps = listOf(
        StepCard(1, "Initialise the Components", "Level and trend from the first cycles, seasonal figures from their deviations. A bad start is visible for a long time.", 0xFF10B981),
        StepCard(2, "Update the Level", "ℓₜ = α(yₜ − sₜ₋ₘ) + (1−α)(ℓₜ₋₁ + bₜ₋₁): the newest observation with the season removed.", 0xFF14B8A6),
        StepCard(3, "Update the Trend", "bₜ = β(ℓₜ − ℓₜ₋₁) + (1−β)bₜ₋₁: smoothing the level's own change.", 0xFF06B6D4),
        StepCard(4, "Update the Season", "sₜ = γ(yₜ − ℓₜ) + (1−γ)sₜ₋ₘ: each position in the cycle updated once per cycle.", 0xFF6366F1),
        StepCard(5, "Forecast", "ŷₜ₊ₕ = ℓₜ + h·bₜ + sₜ₊ₕ₋ₘ. A linear extrapolation with the cycle laid back on top.", 0xFF8B5CF6),
        StepCard(6, "Fit the Parameters", "Minimise one-step-ahead squared error over α, β, γ. Hand-picking them is a last resort.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Simple", "ℓₜ = αyₜ + (1−α)ℓₜ₋₁", "One parameter, flat forecast."),
        FormulaEntry("Implied weights", "α(1−α)ᵏ on yₜ₋ₖ", "Geometric decay; every observation contributes something."),
        FormulaEntry("Effective window", "≈ (2−α)/α", "α = 0.3 behaves roughly like a 5-6 period average."),
        FormulaEntry("Holt's trend", "bₜ = β(ℓₜ − ℓₜ₋₁) + (1−β)bₜ₋₁", "The slope, smoothed the same way the level is."),
        FormulaEntry("Seasonal (additive)", "sₜ = γ(yₜ − ℓₜ) + (1−γ)sₜ₋ₘ", "Use multiplicative when the swing grows with the level."),
        FormulaEntry("Forecast", "ŷₜ₊ₕ = ℓₜ + h·bₜ + sₜ₊ₕ₋ₘ", "The only place h appears; nothing is re-estimated."),
        FormulaEntry("Damped trend", "ŷₜ₊ₕ = ℓₜ + (φ + φ² + … + φʰ)bₜ", "φ < 1. Almost always better at long horizons."),
    ),
    notationKey = listOf(
        NotationEntry("α", "level smoothing — high means little smoothing, not high quality"),
        NotationEntry("β", "trend smoothing"),
        NotationEntry("γ", "seasonal smoothing"),
        NotationEntry("φ", "trend damping, so the forecast does not extrapolate forever"),
        NotationEntry("ℓₜ, bₜ, sₜ", "level, trend and seasonal components at time t"),
        NotationEntry("ETS", "the state-space family this belongs to: Error, Trend, Seasonal"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Holt-Winters, fitted rather than guessed",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from statsmodels.tsa.holtwinters import ExponentialSmoothing

                train, test = y[:-12], y[-12:]

                model = ExponentialSmoothing(
                    train,
                    trend="add",
                    seasonal="add",
                    seasonal_periods=12,
                    damped_trend=True,          # almost always helps beyond a few steps
                ).fit()                          # optimises alpha, beta, gamma, phi by MLE

                forecast = model.forecast(12)
                print(np.sqrt(((forecast - test) ** 2).mean()).round(3))
                print({k: round(v, 3) for k, v in model.params.items()
                       if k in ("smoothing_level", "smoothing_trend", "smoothing_seasonal")})

                # Benchmark first. If seasonal-naive wins, the model is not earning its parameters.
                naive = train[-12:]
                print(np.sqrt(((naive.values - test.values) ** 2).mean()).round(3))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The three recursions, written out",
            accentColor = 0xFF6366F1,
            code = """
                def holt_winters(y, alpha, beta, gamma, m, horizon):
                    level = y[:m].mean()
                    trend = (y[m:2 * m].mean() - level) / m
                    season = [y[i] - level for i in range(m)]

                    fitted = []
                    for t, value in enumerate(y):
                        s = season[t % m]
                        fitted.append(level + trend + s)                 # one-step forecast
                        new_level = alpha * (value - s) + (1 - alpha) * (level + trend)
                        trend = beta * (new_level - level) + (1 - beta) * trend
                        season[t % m] = gamma * (value - new_level) + (1 - gamma) * s
                        level = new_level

                    return fitted, [level + (h + 1) * trend + season[(len(y) + h) % m]
                                    for h in range(horizon)]

                # Note what alpha=0.9 does: the level becomes 90% of the newest observation, so
                # noise passes almost unfiltered into the state and out into the forecast. High
                # smoothing parameters mean LESS smoothing -- the name is the wrong way round for
                # intuition, and it is a common source of badly tuned models.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("browser", 0xFF10B981, "Demand Forecasting", "Retail and supply chain at scale — cheap enough to fit per SKU, which is why it survives against fancier models."),
        ApplicationCard("chip", 0xFF14B8A6, "Capacity Planning", "Traffic and load forecasts with a strong weekly cycle, where m = 7 and the method is nearly free."),
        ApplicationCard("finance", 0xFF6366F1, "Inventory Reorder Points", "Level plus trend feeds the safety-stock calculation directly."),
    ),
    takeaways = listOf(
        "Weights decay geometrically, so every past observation contributes something.",
        "Three components, three recursions: level, trend, season.",
        "High α, β, γ mean *less* smoothing, not more responsiveness — noise passes straight through.",
        "Fit the parameters by minimising one-step error; hand-tuning is a last resort.",
        "Damping the trend almost always improves long-horizon forecasts.",
    ),
    crossLinks = listOf(
        CrossLink("moving_average", "Moving Average (MA)"),
        CrossLink("arima", "ARIMA"),
        CrossLink("prophet", "Prophet (by Meta)"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
    ),
)
