import Foundation

// Port of TimeSeriesMath.kt: the estimators behind the forecasting storyboards (SeriesStoryLabs).

let seasonPeriod = 12
func mean(_ xs: [Double]) -> Double { xs.isEmpty ? 0 : xs.reduce(0, +) / Double(xs.count) }

/// Trailing moving average — nil until enough history exists.
func movingAverage(_ series: [Double], window: Int) -> [Double?] {
    series.indices.map { i in i < window - 1 ? nil : mean(Array(series[(i - window + 1)...i])) }
}

/// Standard deviation of the first difference.
func roughness(_ values: [Double]) -> Double {
    if values.count < 2 { return 0 }
    let diffs = zip(values, values.dropFirst()).map { $1 - $0 }
    let m = mean(diffs)
    return (diffs.reduce(0) { $0 + ($1 - m) * ($1 - m) } / Double(diffs.count)).squareRoot()
}

struct HoltWintersFit { let level: [Double]; let trend: [Double]; let seasonal: [Double]; let fitted: [Double]; let forecast: [Double] }

/// Additive Holt-Winters: three recursions, three smoothing parameters.
func holtWinters(_ train: [Double], alpha: Double, beta: Double, gamma: Double, horizon: Int, period: Int = seasonPeriod) -> HoltWintersFit {
    let firstSeason = mean(Array(train.prefix(period)))
    let secondSeason = mean(Array(train.dropFirst(period).prefix(period)))
    var level = firstSeason
    var trend = (secondSeason - firstSeason) / Double(period)
    var seasonal = (0..<period).map { train[$0] - firstSeason }
    var levels: [Double] = [], trends: [Double] = [], seasons: [Double] = [], fitted: [Double] = []
    for (t, y) in train.enumerated() {
        let s = seasonal[t % period]
        fitted.append(level + trend + s)
        let newLevel = alpha * (y - s) + (1 - alpha) * (level + trend)
        let newTrend = beta * (newLevel - level) + (1 - beta) * trend
        seasonal[t % period] = gamma * (y - newLevel) + (1 - gamma) * s
        level = newLevel
        trend = newTrend
        levels.append(level); trends.append(trend); seasons.append(seasonal[t % period])
    }
    let forecast = (0..<horizon).map { h in level + Double(h + 1) * trend + seasonal[(train.count + h) % period] }
    return HoltWintersFit(level: levels, trend: trends, seasonal: seasons, fitted: fitted, forecast: forecast)
}

struct ArFit {
    let coefficients: [Double]
    let residuals: [Double]
    let rmse: Double
    let p: Int
    func predict(_ history: [Double]) -> Double {
        var v = coefficients[0]
        if p >= 1 { for i in 1...p { v += coefficients[i] * history[history.count - i] } }
        return v
    }
}

/// AR(p) by least squares on the lagged design matrix.
func fitAr(_ series: [Double], p: Int, lambda: Double = 0) -> ArFit {
    if p < 1 {
        let m = mean(series)
        let r = series.map { $0 - m }
        return ArFit(coefficients: [m], residuals: r, rmse: rmseOf(r), p: 0)
    }
    let rows = series.count - p
    let x = (0..<rows).map { r in (0...p).map { c in c == 0 ? 1.0 : series[r + p - c] } }
    let y = (0..<rows).map { series[$0 + p] }
    let beta = ridgeSolve(x, y, lambda)
    let residuals = (0..<rows).map { r -> Double in
        var pred = 0.0
        for c in 0...p { pred += x[r][c] * beta[c] }
        return y[r] - pred
    }
    return ArFit(coefficients: beta, residuals: residuals, rmse: rmseOf(residuals), p: p)
}

func rmseOf(_ values: [Double]) -> Double { values.isEmpty ? 0 : (values.reduce(0) { $0 + $1 * $1 } / Double(values.count)).squareRoot() }

/// Recursive multi-step forecast: each prediction becomes the next lag.
func arForecast(_ fit: ArFit, _ history: [Double], horizon: Int) -> [Double] {
    var working = history
    return (0..<horizon).map { _ in
        let next = fit.predict(working)
        working.append(next)
        return next
    }
}

func difference(_ series: [Double], lag: Int = 1) -> [Double] {
    series.count <= lag ? [] : (lag..<series.count).map { series[$0] - series[$0 - lag] }
}

func lag1Autocorrelation(_ series: [Double]) -> Double { autocorrelation(series, lag: 1) }

func autocorrelation(_ series: [Double], lag: Int) -> Double {
    if series.count <= lag { return 0 }
    let m = mean(series)
    var num = 0.0, den = 0.0
    for i in series.indices {
        den += (series[i] - m) * (series[i] - m)
        if i >= lag { num += (series[i] - m) * (series[i - lag] - m) }
    }
    return den < 1e-12 ? 0 : num / den
}

struct ProphetFit {
    let trend: [Double], seasonal: [Double], fitted: [Double], forecast: [Double], forecastTrend: [Double]
    let changepoints: [Int]
    let deltas: [Double]
    let trainRmse: Double
}

/// Piecewise-linear trend on changepoint basis functions plus a Fourier seasonality, fitted as one
/// regularised least-squares problem with the penalty on the changepoint slopes alone.
func fitProphet(_ train: [Double], changepointCount: Int, fourierOrder: Int, penalty: Double, horizon: Int) -> ProphetFit {
    let n = train.count
    let cps = changepointCount < 1 ? [] : (1...changepointCount).map { Int(Double($0) * (Double(n) * 0.8) / Double(changepointCount + 1)) }
    let terms = 2 + cps.count + 2 * fourierOrder
    func row(_ t: Int) -> [Double] {
        (0..<terms).map { c in
            if c == 0 { return 1 }
            if c == 1 { return Double(t) }
            if c < 2 + cps.count { return max(0, Double(t - cps[c - 2])) }
            let k = (c - 2 - cps.count) / 2 + 1
            let phase = 2 * Double.pi * Double(k) * Double(t) / Double(seasonPeriod)
            return (c - 2 - cps.count) % 2 == 0 ? sin(phase) : cos(phase)
        }
    }
    let x = (0..<n).map(row)
    let beta = ridgeSolveSelective(x, train, penalty, Set(2..<(2 + cps.count)))
    func trendAt(_ t: Int) -> Double {
        var v = beta[0] + beta[1] * Double(t)
        for (j, c) in cps.enumerated() { v += beta[2 + j] * max(0, Double(t - c)) }
        return v
    }
    func seasonalAt(_ t: Int) -> Double {
        var v = 0.0
        if fourierOrder >= 1 {
            for k in 1...fourierOrder {
                let phase = 2 * Double.pi * Double(k) * Double(t) / Double(seasonPeriod)
                let base = 2 + cps.count + (k - 1) * 2
                v += beta[base] * sin(phase) + beta[base + 1] * cos(phase)
            }
        }
        return v
    }
    let trend = (0..<n).map(trendAt), seasonal = (0..<n).map(seasonalAt)
    let fitted = (0..<n).map { trend[$0] + seasonal[$0] }
    return ProphetFit(trend: trend, seasonal: seasonal, fitted: fitted,
                      forecast: (0..<horizon).map { trendAt(n + $0) + seasonalAt(n + $0) },
                      forecastTrend: (0..<horizon).map { trendAt(n + $0) },
                      changepoints: cps, deltas: cps.indices.map { beta[2 + $0] },
                      trainRmse: rmseOf((0..<n).map { train[$0] - fitted[$0] }))
}

/// Ridge with the penalty only on the named columns.
func ridgeSolveSelective(_ x: [[Double]], _ y: [Double], _ lambda: Double, _ penalised: Set<Int>) -> [Double] {
    let p = x.first?.count ?? 0
    if p == 0 { return [] }
    var a = [[Double]](repeating: [Double](repeating: 0, count: p + 1), count: p)
    for i in 0..<p {
        for j in 0..<p {
            var s = 0.0
            for r in x.indices { s += x[r][i] * x[r][j] }
            a[i][j] = s
        }
        if penalised.contains(i) { a[i][i] += lambda }
        var rhs = 0.0
        for r in x.indices { rhs += x[r][i] * y[r] }
        a[i][p] = rhs
    }
    for col in 0..<p {
        var pivot = col
        for r in (col + 1)..<max(p, col + 1) where abs(a[r][col]) > abs(a[pivot][col]) { pivot = r }
        a.swapAt(col, pivot)
        if abs(a[col][col]) < 1e-12 { a[col][col] = 1e-12 }
        for r in 0..<p where r != col {
            let f = a[r][col] / a[col][col]
            if f == 0 { continue }
            for c in col...p { a[r][c] -= f * a[col][c] }
        }
    }
    return (0..<p).map { a[$0][p] / a[$0][$0] }
}

func forecastRmse(_ forecast: [Double], _ actual: [Double]) -> Double {
    let n = min(forecast.count, actual.count)
    if n == 0 { return 0 }
    return ((0..<n).reduce(0.0) { let e = forecast[$1] - actual[$1]; return $0 + e * e } / Double(n)).squareRoot()
}

/// Next year looks like last year.
func seasonalNaiveForecast(_ train: [Double], horizon: Int) -> [Double] {
    (0..<horizon).map { train[train.count - seasonPeriod + ($0 % seasonPeriod)] }
}
