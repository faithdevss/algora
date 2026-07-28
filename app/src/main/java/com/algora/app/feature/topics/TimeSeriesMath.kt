package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// ── Time series ──────────────────────────────────────────────────────────────
// The estimators behind the six B7 Time Series labs. All six run on the same monthly series so the
// methods are comparable rather than each flattered by its own data, and all six are the real
// estimator: AR by least squares on the lagged design, ARIMA's MA terms by Hannan–Rissanen two-stage
// regression, Holt-Winters by its three recursions, Prophet by a piecewise-linear trend on
// changepoint basis functions plus a Fourier seasonality, fitted as one regularised least-squares
// problem.
//
// Every lab forecasts past the end of the observed data, which is the only honest way to show a
// forecasting method: an in-sample fit can be made arbitrarily good and says nothing.

internal const val SeasonPeriod = 12

/** Index of the first forecast point. Everything at or past this is held out from every fit. */
internal const val TrainLength = 48

internal class SeriesPoint(val t: Int, val value: Double)

private class SeriesRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767.0
    }

    fun gaussian(): Double {
        val u1 = next().coerceAtLeast(1e-9)
        val u2 = next()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}

/**
 * Sixty months of a retail-shaped series: a trend that changes slope once, an annual seasonal
 * pattern, and noise. The slope change is deliberate — it is what separates a method that assumes
 * one global trend from one that does not, and without it Prophet's changepoints would have nothing
 * to find.
 */
internal fun retailSeries(seed: Int = 5): List<Double> {
    val rng = SeriesRng(seed * 7919 + 13)
    return List(TrainLength + 12) { t ->
        val trend = if (t < 30) 100.0 + 1.30 * t else 100.0 + 1.30 * 30 + 0.35 * (t - 30)
        val seasonal = 9.0 * sin(2 * PI * t / SeasonPeriod) + 4.0 * cos(4 * PI * t / SeasonPeriod)
        trend + seasonal + rng.gaussian() * 3.0
    }
}

internal fun trainingPart(series: List<Double>) = series.take(TrainLength)

internal fun holdOutPart(series: List<Double>) = series.drop(TrainLength)

// ── Smoothing ────────────────────────────────────────────────────────────────

/**
 * Trailing moving average: `null` until enough history exists. Trailing rather than centred because
 * that is the only version usable in a forecast — a centred window needs values from the future.
 */
internal fun movingAverage(series: List<Double>, window: Int): List<Double?> =
    series.indices.map { i ->
        if (i < window - 1) null
        else series.subList(i - window + 1, i + 1).average()
    }

/** Standard deviation of the first difference — how much of the series is jitter rather than shape. */
internal fun roughness(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val diffs = values.zipWithNext { a, b -> b - a }
    val mean = diffs.average()
    return sqrt(diffs.sumOf { (it - mean) * (it - mean) } / diffs.size)
}

internal class HoltWinters(
    val level: List<Double>,
    val trend: List<Double>,
    val seasonal: List<Double>,
    val fitted: List<Double>,
    val forecast: List<Double>,
)

/**
 * Additive Holt-Winters. Three recursions, three smoothing parameters: α for the level, β for the
 * trend, γ for the seasonal figures. Each is an exponentially-weighted update of one component
 * against what the newest observation implies for it, with the other two components subtracted out.
 */
internal fun holtWinters(
    train: List<Double>,
    alpha: Double,
    beta: Double,
    gamma: Double,
    horizon: Int,
    period: Int = SeasonPeriod,
): HoltWinters {
    // Initial level and trend from the first two whole seasons; initial seasonal figures from the
    // first season's deviation. Standard initialisation, and it matters: with 48 points a bad start
    // is still visible at the end.
    val firstSeason = train.take(period).average()
    val secondSeason = train.drop(period).take(period).average()
    var level = firstSeason
    var trend = (secondSeason - firstSeason) / period
    val seasonal = DoubleArray(period) { train[it] - firstSeason }

    val levels = mutableListOf<Double>()
    val trends = mutableListOf<Double>()
    val seasons = mutableListOf<Double>()
    val fitted = mutableListOf<Double>()

    train.forEachIndexed { t, y ->
        val s = seasonal[t % period]
        fitted += level + trend + s
        val newLevel = alpha * (y - s) + (1 - alpha) * (level + trend)
        val newTrend = beta * (newLevel - level) + (1 - beta) * trend
        seasonal[t % period] = gamma * (y - newLevel) + (1 - gamma) * s
        level = newLevel
        trend = newTrend
        levels += level
        trends += trend
        seasons += seasonal[t % period]
    }

    val forecast = List(horizon) { h ->
        level + (h + 1) * trend + seasonal[(train.size + h) % period]
    }
    return HoltWinters(levels, trends, seasons, fitted, forecast)
}

// ── Autoregression ───────────────────────────────────────────────────────────

internal class ArFit(
    /** `coefficients[0]` is the intercept; the rest are φ₁…φₚ. */
    val coefficients: DoubleArray,
    val residuals: List<Double>,
    val rmse: Double,
    val p: Int,
) {
    fun predict(history: List<Double>): Double {
        var v = coefficients[0]
        for (i in 1..p) v += coefficients[i] * history[history.size - i]
        return v
    }
}

/** AR(p) by ordinary least squares on the lagged design matrix — the conditional-likelihood estimator. */
internal fun fitAr(series: List<Double>, p: Int, lambda: Double = 0.0): ArFit {
    if (p < 1) {
        val mean = series.average()
        val residuals = series.map { it - mean }
        return ArFit(doubleArrayOf(mean), residuals, rmseOf(residuals), 0)
    }
    val rows = series.size - p
    val x = Array(rows) { r -> DoubleArray(p + 1) { c -> if (c == 0) 1.0 else series[r + p - c] } }
    val y = DoubleArray(rows) { series[it + p] }
    val beta = ridgeSolve(x, y, lambda)
    val residuals = (0 until rows).map { r ->
        var pred = 0.0
        for (c in 0..p) pred += x[r][c] * beta[c]
        y[r] - pred
    }
    return ArFit(beta, residuals, rmseOf(residuals), p)
}

internal fun rmseOf(values: List<Double>): Double =
    if (values.isEmpty()) 0.0 else sqrt(values.sumOf { it * it } / values.size)

/** Recursive multi-step forecast: each prediction is fed back in as the next lag. */
internal fun arForecast(fit: ArFit, history: List<Double>, horizon: Int): List<Double> {
    val working = history.toMutableList()
    return List(horizon) {
        val next = fit.predict(working)
        working += next
        next
    }
}

// ── Differencing and ARIMA ───────────────────────────────────────────────────

internal fun difference(series: List<Double>, lag: Int = 1): List<Double> =
    if (series.size <= lag) emptyList() else (lag until series.size).map { series[it] - series[it - lag] }

/**
 * Undo `d` rounds of lag-1 differencing on a forecast, given the tail of the original series. The
 * integration step is the "I" in ARIMA, and it is where the drift in a differenced forecast comes
 * from.
 */
internal fun integrate(forecastDiff: List<Double>, lastValues: List<Double>, d: Int, lag: Int = 1): List<Double> {
    if (d == 0) return forecastDiff
    var current = forecastDiff
    var anchors = lastValues
    repeat(d) {
        val undone = mutableListOf<Double>()
        var previous = anchors.last()
        current.forEach { step ->
            previous += step
            undone += previous
        }
        current = undone
        anchors = anchors.dropLast(1)
        if (anchors.isEmpty()) anchors = listOf(0.0)
    }
    return current
}

/**
 * A crude but real stationarity check: the lag-1 autocorrelation of the series. A trending series
 * has one very close to 1; differencing pulls it down. This is the intuition behind an augmented
 * Dickey-Fuller test without the test's distribution theory, and the labs say so rather than
 * pretending it is ADF.
 */
internal fun lag1Autocorrelation(series: List<Double>): Double {
    if (series.size < 2) return 0.0
    val mean = series.average()
    var num = 0.0
    var den = 0.0
    series.indices.forEach { i ->
        den += (series[i] - mean) * (series[i] - mean)
        if (i > 0) num += (series[i] - mean) * (series[i - 1] - mean)
    }
    return if (den < 1e-12) 0.0 else num / den
}

internal fun autocorrelation(series: List<Double>, lag: Int): Double {
    if (series.size <= lag) return 0.0
    val mean = series.average()
    var num = 0.0
    var den = 0.0
    series.indices.forEach { i ->
        den += (series[i] - mean) * (series[i] - mean)
        if (i >= lag) num += (series[i] - mean) * (series[i - lag] - mean)
    }
    return if (den < 1e-12) 0.0 else num / den
}

internal class ArimaFit(
    val ar: DoubleArray,
    val ma: DoubleArray,
    val intercept: Double,
    val forecast: List<Double>,
    val fitted: List<Double>,
    val trainRmse: Double,
)

/**
 * ARIMA(p, d, q) by Hannan–Rissanen: difference d times, fit a long AR to get residual estimates,
 * then regress the differenced series on its own lags *and* those estimated residuals' lags. It is
 * the standard two-stage initialisation for an ARMA likelihood, and it is a genuine estimator on its
 * own rather than a stand-in — which matters, because a lab that animated a fake fit would be
 * teaching the wrong thing.
 */
internal fun fitArima(train: List<Double>, p: Int, d: Int, q: Int, horizon: Int): ArimaFit {
    var work = train
    repeat(d) { work = difference(work) }

    // Stage one: a long AR whose residuals stand in for the unobservable innovations.
    val longOrder = max(p + q + 2, 6).coerceAtMost(work.size / 3)
    val stageOne = fitAr(work, longOrder, lambda = 1e-6)
    val innovations = DoubleArray(work.size)
    stageOne.residuals.forEachIndexed { i, e -> innovations[i + longOrder] = e }

    // Stage two: regress on p own-lags and q innovation-lags together.
    val start = max(p, q) + longOrder
    val rows = work.size - start
    val terms = 1 + p + q
    val x = Array(rows) { r ->
        DoubleArray(terms) { c ->
            when {
                c == 0 -> 1.0
                c <= p -> work[start + r - c]
                else -> innovations[start + r - (c - p)]
            }
        }
    }
    val y = DoubleArray(rows) { work[start + it] }
    val beta = if (rows > terms) ridgeSolve(x, y, 1e-6) else DoubleArray(terms)

    val fittedDiff = (0 until rows).map { r -> (0 until terms).sumOf { x[r][it] * beta[it] } }
    val residuals = (0 until rows).map { y[it] - fittedDiff[it] }

    // Forecast on the differenced scale, then integrate back. Future innovations are zero in
    // expectation, so the MA terms decay out of the forecast after q steps — which is exactly why
    // an MA(q) model has no memory beyond q periods.
    val history = work.toMutableList()
    val futureInnovations = MutableList(q) { i -> innovations.getOrElse(work.size - q + i) { 0.0 } }
    val forecastDiff = List(horizon) { h ->
        var v = beta[0]
        for (c in 1..p) v += beta[c] * history[history.size - c]
        for (c in 1..q) {
            val e = futureInnovations.getOrElse(futureInnovations.size - c) { 0.0 }
            v += beta[p + c] * e
        }
        history += v
        futureInnovations += 0.0
        v
    }

    return ArimaFit(
        ar = DoubleArray(p) { beta[it + 1] },
        ma = DoubleArray(q) { beta[p + it + 1] },
        intercept = beta[0],
        forecast = integrate(forecastDiff, train.takeLast(max(d, 1)), d),
        fitted = fittedDiff,
        trainRmse = rmseOf(residuals),
    )
}

/**
 * SARIMA's seasonal part, as the labs need it: difference at the seasonal lag, fit an AR that
 * carries both short lags and the seasonal lag, forecast, then undo both differences. Written
 * separately from [fitArima] rather than as a general (p,d,q)(P,D,Q)ₘ engine, because the point the
 * lab makes is about the seasonal *difference* and a general engine would bury it.
 */
internal class SarimaFit(
    val forecast: List<Double>,
    val seasonalDifferenced: List<Double>,
    val acfBefore: Double,
    val acfAfter: Double,
    val trainRmse: Double,
)

internal fun fitSarima(train: List<Double>, p: Int, seasonalDifference: Boolean, horizon: Int): SarimaFit {
    val acfBefore = autocorrelation(train, SeasonPeriod)
    val work = if (seasonalDifference) difference(train, SeasonPeriod) else train
    val acfAfter = autocorrelation(work, SeasonPeriod)

    val fit = fitAr(work, max(p, 1), lambda = 1e-6)
    val diffForecast = arForecast(fit, work, horizon)

    val forecast = if (seasonalDifference) {
        // Undo a lag-12 difference: each forecast adds back the value one season earlier, taken from
        // the observed series while it reaches and from the forecast itself after that.
        val out = mutableListOf<Double>()
        diffForecast.forEachIndexed { h, step ->
            val seasonAgoIndex = train.size + h - SeasonPeriod
            val base = if (seasonAgoIndex < train.size) train[seasonAgoIndex] else out[seasonAgoIndex - train.size]
            out += base + step
        }
        out
    } else {
        diffForecast
    }
    return SarimaFit(forecast, work, acfBefore, acfAfter, fit.rmse)
}

// ── Prophet-style decomposable model ─────────────────────────────────────────

internal class ProphetFit(
    val trend: List<Double>,
    val seasonal: List<Double>,
    val fitted: List<Double>,
    val forecast: List<Double>,
    val forecastTrend: List<Double>,
    val changepoints: List<Int>,
    /** Slope change at each changepoint. The ones near zero are changepoints the fit declined to use. */
    val deltas: DoubleArray,
    val trainRmse: Double,
)

/**
 * Prophet's model form: y(t) = g(t) + s(t) + ε, with g a piecewise-linear trend built from
 * changepoint basis functions max(0, t − cⱼ) and s a Fourier series of the given order. Fitted here
 * as one regularised least-squares problem, with the penalty applied only to the changepoint slopes
 * — which is Prophet's sparse prior on δ, the thing that stops it inventing a slope change at every
 * candidate.
 */
internal fun fitProphet(
    train: List<Double>,
    changepointCount: Int,
    fourierOrder: Int,
    changepointPenalty: Double,
    horizon: Int,
): ProphetFit {
    val n = train.size
    // Candidates over the first 80% of the history, as Prophet does — a changepoint at the very end
    // has almost no data after it and would fit noise.
    val changepoints = (1..changepointCount).map { (it * (n * 0.8) / (changepointCount + 1)).toInt() }

    val terms = 2 + changepoints.size + 2 * fourierOrder
    fun row(t: Int) = DoubleArray(terms) { c ->
        when {
            c == 0 -> 1.0
            c == 1 -> t.toDouble()
            c < 2 + changepoints.size -> max(0.0, (t - changepoints[c - 2]).toDouble())
            else -> {
                val k = (c - 2 - changepoints.size) / 2 + 1
                val phase = 2 * PI * k * t / SeasonPeriod
                if ((c - 2 - changepoints.size) % 2 == 0) sin(phase) else cos(phase)
            }
        }
    }

    val x = Array(n) { row(it) }
    val y = DoubleArray(n) { train[it] }

    // Penalise only the changepoint columns: the intercept, the global slope and the seasonal terms
    // are meant to be free.
    val penalised = (2 until 2 + changepoints.size).toSet()
    val beta = ridgeSolveSelective(x, y, changepointPenalty, penalised)

    fun trendAt(t: Int): Double {
        var v = beta[0] + beta[1] * t
        changepoints.forEachIndexed { j, c -> v += beta[2 + j] * max(0.0, (t - c).toDouble()) }
        return v
    }

    fun seasonalAt(t: Int): Double {
        var v = 0.0
        for (k in 1..fourierOrder) {
            val phase = 2 * PI * k * t / SeasonPeriod
            val base = 2 + changepoints.size + (k - 1) * 2
            v += beta[base] * sin(phase) + beta[base + 1] * cos(phase)
        }
        return v
    }

    val trend = List(n) { trendAt(it) }
    val seasonal = List(n) { seasonalAt(it) }
    val fitted = List(n) { trend[it] + seasonal[it] }
    val residuals = List(n) { train[it] - fitted[it] }
    val forecast = List(horizon) { h -> trendAt(n + h) + seasonalAt(n + h) }
    val forecastTrend = List(horizon) { h -> trendAt(n + h) }

    return ProphetFit(
        trend = trend,
        seasonal = seasonal,
        fitted = fitted,
        forecast = forecast,
        forecastTrend = forecastTrend,
        changepoints = changepoints,
        deltas = DoubleArray(changepoints.size) { beta[2 + it] },
        trainRmse = rmseOf(residuals),
    )
}

/** Ridge with the penalty applied only to the named columns — Prophet's prior is on δ alone. */
internal fun ridgeSolveSelective(
    x: Array<DoubleArray>,
    y: DoubleArray,
    lambda: Double,
    penalised: Set<Int>,
): DoubleArray {
    val p = if (x.isEmpty()) 0 else x[0].size
    if (p == 0) return DoubleArray(0)
    val a = Array(p) { DoubleArray(p + 1) }
    for (i in 0 until p) {
        for (j in 0 until p) {
            var sum = 0.0
            for (r in x.indices) sum += x[r][i] * x[r][j]
            a[i][j] = sum
        }
        if (i in penalised) a[i][i] += lambda
        var rhs = 0.0
        for (r in x.indices) rhs += x[r][i] * y[r]
        a[i][p] = rhs
    }
    for (col in 0 until p) {
        var pivot = col
        for (r in col + 1 until p) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
        val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
        if (abs(a[col][col]) < 1e-12) a[col][col] = 1e-12
        for (r in 0 until p) {
            if (r == col) continue
            val f = a[r][col] / a[col][col]
            if (f == 0.0) continue
            for (c in col..p) a[r][c] -= f * a[col][c]
        }
    }
    return DoubleArray(p) { a[it][p] / a[it][it] }
}

// ── Scoring ──────────────────────────────────────────────────────────────────

/** Forecast RMSE against the held-out tail — the only number in these labs worth comparing. */
internal fun forecastRmse(forecast: List<Double>, actual: List<Double>): Double {
    val n = minOf(forecast.size, actual.size)
    if (n == 0) return 0.0
    return sqrt((0 until n).sumOf { val e = forecast[it] - actual[it]; e * e } / n)
}

/** Mean absolute percentage error, in percent. Undefined at zero, and this series never is. */
internal fun forecastMape(forecast: List<Double>, actual: List<Double>): Double {
    val n = minOf(forecast.size, actual.size)
    if (n == 0) return 0.0
    return 100.0 * (0 until n).sumOf { abs((forecast[it] - actual[it]) / actual[it]) } / n
}

/** The naive seasonal benchmark: next year looks like last year. Anything that loses to this is not working. */
internal fun seasonalNaiveForecast(train: List<Double>, horizon: Int): List<Double> =
    List(horizon) { h -> train[train.size - SeasonPeriod + (h % SeasonPeriod)] }
