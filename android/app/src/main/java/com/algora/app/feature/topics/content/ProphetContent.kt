package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val prophetContent = TopicContent(
    topicId = "prophet",
    whatIsIt = listOf(
        "Prophet is not a new statistical idea; it is a deliberate engineering choice about who forecasts. Its authors' observation was that most business forecasting is done by analysts who know the domain and not the Box-Jenkins method, and that ARIMA's parameters are unintuitive to exactly those people. So Prophet models a series as a sum of interpretable pieces — y(t) = g(t) + s(t) + h(t) + ε — and fits it as a curve-fitting problem in which every parameter means something a domain expert can reason about.",
        "The trend g(t) is piecewise linear, with candidate changepoints laid across the history and a sparse prior on the slope change at each. That prior is the important part: without it the fit puts a kink at every candidate and extrapolates whatever the last one happened to say. The seasonality s(t) is a Fourier series, so the number of terms directly sets how wiggly the annual pattern may be. Holidays h(t) are simply indicator regressors with their own windows — which is Prophet's genuinely useful contribution, because \"Black Friday moves and Easter moves and both matter\" is very awkward to express in a seasonal ARIMA and trivial here.",
        "The consequences are worth being clear-eyed about. Because it is curve fitting rather than a sequential model, Prophet does not require stationarity, handles missing data and irregular sampling without complaint, and is robust to outliers — all genuine advantages. But it also does not learn from autocorrelation the way ARIMA does: it fits a shape to the calendar, and if what you need is short-horizon prediction from recent momentum, an AR model will beat it. Published benchmarks have found it losing to well-tuned classical methods and even to seasonal-naive on some series. The simulation shows both sides — with no changepoints the trend is one straight line and the forecast is poor, and with too many and no penalty the slope changes alternate wildly and it is fitting noise.",
    ),
    steps = listOf(
        StepCard(1, "Lay Out Changepoints", "Candidates across the first 80% of history — a changepoint near the end has almost no data after it.", 0xFF10B981),
        StepCard(2, "Fit the Piecewise Trend", "A global slope plus a delta at each changepoint, under a sparse prior on the deltas.", 0xFF14B8A6),
        StepCard(3, "Add Fourier Seasonality", "Order 10 for yearly, 3 for weekly by default. The order is how wiggly the pattern is allowed to be.", 0xFF06B6D4),
        StepCard(4, "Add Holidays and Regressors", "Named dates with windows, plus any external series. The part that is genuinely hard elsewhere.", 0xFF6366F1),
        StepCard(5, "Tune the Two Scales", "changepoint_prior_scale for trend flexibility, seasonality_prior_scale for seasonal amplitude.", 0xFF8B5CF6),
        StepCard(6, "Cross-Validate on Rolling Origins", "Prophet ships the tooling; use it, and compare against seasonal-naive before shipping.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Decomposition", "y(t) = g(t) + s(t) + h(t) + εₜ", "Trend, seasonality, holidays, noise."),
        FormulaEntry("Piecewise trend", "g(t) = (k + a(t)ᵀδ)t + (m + a(t)ᵀγ)", "δⱼ is the slope change at changepoint j."),
        FormulaEntry("Changepoint basis", "aⱼ(t) = 1 if t ≥ cⱼ else 0", "Equivalently max(0, t − cⱼ) on the slope."),
        FormulaEntry("Sparse prior", "δⱼ ~ Laplace(0, τ)", "τ is changepoint_prior_scale. Small τ means a stiffer trend."),
        FormulaEntry("Fourier seasonality", "s(t) = Σₙ₌₁ᴺ [aₙcos(2πnt/P) + bₙsin(2πnt/P)]", "N is the Fourier order; 2N parameters."),
        FormulaEntry("Saturating growth", "g(t) = C(t) / (1 + exp(−k(t−m)))", "Logistic mode, when a capacity is known."),
        FormulaEntry("Fitting", "MAP or full Bayesian, via Stan", "MAP by default — the uncertainty intervals come from the posterior."),
    ),
    notationKey = listOf(
        NotationEntry("g(t)", "trend: piecewise linear, or logistic with a capacity"),
        NotationEntry("s(t)", "seasonality, as a Fourier series"),
        NotationEntry("h(t)", "holiday effects — indicator regressors with windows"),
        NotationEntry("δⱼ", "the slope change at changepoint j; the sparse prior lives here"),
        NotationEntry("changepoint_prior_scale", "trend flexibility. The parameter that matters most"),
        NotationEntry("Fourier order", "how many harmonics the seasonality may use"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A forecast with holidays, which is where it earns its place",
            accentColor = 0xFF10B981,
            code = """
                import pandas as pd
                from prophet import Prophet

                df = pd.DataFrame({"ds": dates, "y": values})    # the only two columns it wants

                m = Prophet(
                    yearly_seasonality=10,          # Fourier order, not a boolean
                    weekly_seasonality=3,
                    changepoint_prior_scale=0.05,   # default; raise for a more flexible trend
                    seasonality_mode="multiplicative",  # when the swing grows with the level
                )
                m.add_country_holidays(country_name="US")
                m.add_regressor("promo_spend")      # external series, aligned on ds

                m.fit(df)
                future = m.make_future_dataframe(periods=90)
                future["promo_spend"] = projected_spend      # regressors need future values too
                forecast = m.predict(future)

                # The components plot is the actual product: trend, weekly, yearly, holidays,
                # each separately, each in the units of y. That is what a domain expert can argue
                # with, and it is the reason this tool spread.
                m.plot_components(forecast)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Tuning it honestly, and the benchmark it has to clear",
            accentColor = 0xFF8B5CF6,
            code = """
                import numpy as np
                import itertools
                from prophet.diagnostics import cross_validation, performance_metrics

                grid = itertools.product([0.01, 0.05, 0.5], [1.0, 10.0])
                results = []
                for cps, sps in grid:
                    m = Prophet(changepoint_prior_scale=cps, seasonality_prior_scale=sps).fit(df)
                    cv = cross_validation(m, initial="730 days", period="90 days", horizon="90 days")
                    results.append((cps, sps, performance_metrics(cv)["rmse"].mean()))

                for cps, sps, rmse in sorted(results, key=lambda r: r[2]):
                    print(cps, sps, round(rmse, 3))

                # changepoint_prior_scale too low: one straight trend, and it misses a real slope
                # change. Too high: a kink at every candidate, fitting noise, and the forecast
                # extrapolates whatever the last kink said.
                #
                # And the check worth running before any of this: seasonal-naive. Prophet losing
                # to "same week last year" is a documented outcome on regular series, not an
                # exotic one.
                naive = df.y.shift(365).iloc[-90:]
                print(float(np.sqrt(((naive.values - df.y.iloc[-90:].values) ** 2).mean())))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("browser", 0xFF10B981, "Business Metrics at Scale", "Thousands of series forecast on a schedule with no analyst per series — the workload it was built for at Meta."),
        ApplicationCard("users", 0xFF14B8A6, "Capacity and Staffing", "Where holiday effects dominate and expressing them is most of the modelling work."),
        ApplicationCard("chart", 0xFF6366F1, "Anomaly Detection", "Points outside the predicted interval, with the trend and seasonality already accounted for."),
    ),
    takeaways = listOf(
        "A decomposable model — trend, seasonality, holidays — fitted as curve fitting, not as a sequential process.",
        "The sparse prior on changepoint slopes is what stops the trend kinking everywhere.",
        "Fourier order sets seasonal flexibility; holidays as regressors are its real differentiator.",
        "No stationarity requirement, tolerant of gaps and outliers, and the components plot is interpretable.",
        "It does not exploit autocorrelation, and it can lose to a tuned ARIMA or to seasonal-naive. Benchmark it.",
    ),
    crossLinks = listOf(
        CrossLink("sarima", "SARIMA (Seasonal)"),
        CrossLink("exponential_smoothing", "Exponential Smoothing (Holt-Winters)"),
        CrossLink("isotonic_regression", "Isotonic Regression"),
        CrossLink("bayesian_ridge", "Bayesian Ridge Regression"),
    ),
)
