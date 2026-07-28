package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val movingAverageContent = TopicContent(
    topicId = "moving_average",
    whatIsIt = listOf(
        "A moving average replaces each observation with the mean of a window around it. It is the first thing anyone does to a noisy series and it is worth understanding precisely, because almost every later method is a refinement of it: exponential smoothing is a moving average with geometrically decaying weights, and the MA terms in ARIMA are a moving average of past *errors* rather than of past values.",
        "Everything about it is one trade. Averaging w points reduces the variance of independent noise by a factor of w, so a wider window gives a smoother line. But a trailing window's centre of mass sits (w−1)/2 periods in the past, so the smoothed line lags the series by exactly that much — and the first w−1 points have no value at all. The simulation makes both sides measurable: widening the window drops the month-to-month roughness sharply while the lag readout climbs in step.",
        "Two distinctions matter in practice. A *centred* window is symmetric and introduces no lag, but it needs future values, so it can be used to describe history and never to forecast — a fact quietly violated in a lot of published charts. And when the window equals the seasonal period, the seasonal component averages out to nothing, which is why a 12-month moving average on monthly data is the classical way to extract a trend, and the first step of a classical seasonal decomposition.",
    ),
    steps = listOf(
        StepCard(1, "Choose the Window", "Width w. Everything else follows from it, and there is no value that is right in general.", 0xFF10B981),
        StepCard(2, "Slide and Average", "Each output is the mean of the w most recent values. O(1) per step with a running sum.", 0xFF14B8A6),
        StepCard(3, "Accept the Warm-Up", "The first w−1 positions have no window. Padding them is fabricating data.", 0xFF06B6D4),
        StepCard(4, "Account for the Lag", "A trailing window lags by (w−1)/2. A centred one does not — and cannot forecast.", 0xFF6366F1),
        StepCard(5, "Match w to the Season", "w = m averages the seasonal cycle away, leaving trend. That is the classical decomposition's first step.", 0xFF8B5CF6),
        StepCard(6, "Forecast, Cautiously", "The naive forecast is the last window mean, flat forever. It is a benchmark, not a model.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Trailing average", "MAₜ = (1/w) Σᵢ₌₀^{w−1} yₜ₋ᵢ", "Uses only the past — the only version usable in a forecast."),
        FormulaEntry("Centred average", "MAₜ = (1/w) Σᵢ₌₋k^{k} yₜ₊ᵢ", "No lag, but needs the future. Description only."),
        FormulaEntry("Lag", "(w − 1)/2", "The window's centre of mass, measured back from now."),
        FormulaEntry("Noise reduction", "Var(MA) = σ²/w", "For independent noise. Halving the noise costs a 4× window."),
        FormulaEntry("Running update", "MAₜ = MAₜ₋₁ + (yₜ − yₜ₋w)/w", "O(1) per step regardless of w."),
        FormulaEntry("Weighted variant", "Σ wᵢyₜ₋ᵢ / Σ wᵢ", "Recency weighting; exponential smoothing is the limit case."),
    ),
    notationKey = listOf(
        NotationEntry("w", "window width"),
        NotationEntry("m", "seasonal period — 12 for monthly, 7 for daily-with-weekly"),
        NotationEntry("trailing", "window ends at the current point; causal, laggy"),
        NotationEntry("centred", "window straddles the current point; lag-free, non-causal"),
        NotationEntry("roughness", "standard deviation of the first difference — how much jitter is left"),
        NotationEntry("warm-up", "the leading positions with no full window"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Both variants, and the lag one of them hides",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                import pandas as pd

                s = pd.Series(np.random.default_rng(0).normal(size=120).cumsum() + np.arange(120) * 0.3)

                trailing = s.rolling(window=12).mean()               # causal, lags by 5.5
                centred = s.rolling(window=12, center=True).mean()   # lag-free, uses the future

                # The lag is measurable: cross-correlate and find the offset that lines them up.
                def best_lag(a, b, max_lag=12):
                    a, b = a.dropna(), b.dropna()
                    idx = a.index.intersection(b.index)
                    return max(range(max_lag), key=lambda k: a[idx].shift(k).corr(b[idx]))

                print(best_lag(trailing, s))    # ~5-6, i.e. (w-1)/2

                # Centred is fine for describing history and unusable for forecasting: the value at
                # time t was computed from values up to t+5. Backtesting with it leaks the future
                # and produces results that will not survive contact with production.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why w = m is the classical trend estimate",
            accentColor = 0xFF8B5CF6,
            code = """
                import numpy as np
                import pandas as pd
                from statsmodels.tsa.seasonal import seasonal_decompose

                t = np.arange(120)
                y = pd.Series(100 + 0.5 * t + 8 * np.sin(2 * np.pi * t / 12) +
                              np.random.default_rng(1).normal(scale=2, size=120))

                # A 12-month window contains exactly one full cycle, so the seasonal component
                # sums to (almost) zero inside it and what survives is trend plus a little noise.
                print(round(float(y.rolling(12, center=True).mean().std()), 2))   # smooth
                print(round(float(y.rolling(5, center=True).mean().std()), 2))    # season survives

                # That is step one of the classical decomposition, and statsmodels does exactly
                # this: centred MA for the trend, average the detrended values by period for the
                # seasonal figures, whatever is left is the residual.
                result = seasonal_decompose(y, period=12, model="additive")
                print(result.seasonal[:12].round(2).tolist())
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Trading Indicators", "The 50- and 200-day averages, and crossover rules built on them — which are largely a story about lag."),
        ApplicationCard("chart", 0xFF14B8A6, "Dashboard Smoothing", "Seven-day rolling means on daily metrics, to remove the weekday cycle before anyone reads a trend into it."),
        ApplicationCard("chip", 0xFF6366F1, "Sensor Filtering", "The cheapest possible low-pass filter, and often the right one on an embedded device."),
    ),
    takeaways = listOf(
        "Wider windows smooth more and lag more — one trade, no free setting.",
        "A trailing window lags by (w−1)/2; a centred one does not lag but cannot forecast.",
        "Noise variance falls as σ²/w, so halving the noise costs four times the window.",
        "w equal to the seasonal period averages the season away, which is how trend is extracted.",
        "The flat last-window forecast is a benchmark to beat, not a forecasting method.",
    ),
    crossLinks = listOf(
        CrossLink("exponential_smoothing", "Exponential Smoothing (Holt-Winters)"),
        CrossLink("arima", "ARIMA"),
        CrossLink("sliding_window", "Sliding Window (DSA)"),
        CrossLink("prefix_sum", "Prefix Sum (DSA)"),
    ),
)
