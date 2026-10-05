import SwiftUI

// Port of SeriesStoryLabs.kt. Moving average, AR, ARIMA, SARIMA, Holt-Winters and Prophet all run on
// one seeded monthly series (48 months to fit, 12 held out) and are scored on the held-out year
// against seasonal naive. Apriori, Eclat and FP-growth mine the same ten baskets (AssociationMath).
// Each is one card, the arithmetic, chips and a headline, then either a step track or a panel of tabs,
// steppers and a button.

let seriesStoryTopicIds: Set<String> = [
    "moving_average", "autoregression", "arima", "sarima", "exponential_smoothing", "prophet", "apriori", "eclat", "fp_growth",
]

private let sObserved = Color(hex: 0xB8BEC9)
private let sViolet = SimColors.answer
private let sRed = Color(hex: 0xE5534B)
private let sOrange = Color(hex: 0xF08A3C)
private let sBlue = Color(hex: 0x4F7FE0)
private let sGreen = Color(hex: 0x3F9A62)

private func sfx(_ v: Double, _ d: Int = 2) -> String {
    var p: Int64 = 1
    for _ in 0..<d { p *= 10 }
    let r = Int64((abs(v) * Double(p) + 0.5).rounded(.down))
    var body = "\(r / p)"
    if d > 0 {
        let frac = String(r % p)
        body += "." + String(repeating: "0", count: d - frac.count) + frac
    }
    return v < 0 && r != 0 ? "−" + body : body
}

/// Index of the first smallest key, as Kotlin's minBy picks it.
private func firstMin<C: Collection>(_ c: C, _ key: (C.Element) -> Double) -> C.Element {
    var best = c.first!
    for x in c where key(x) < key(best) { best = x }
    return best
}

// MARK: - The series

private struct SsRng {
    var state: Int64
    init(_ seed: Int64) { state = seed }
    mutating func u() -> Double {
        state = (state * 1103515245 + 12345) & 0x7fffffff
        return Double(state) / 2147483648.0
    }
    mutating func g() -> Double {
        let a = max(u(), 1e-12)
        let b = u()
        return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b)
    }
}

private let fitMonths = 48
private let horizon = 12

/// Trend 0.5 a month, a yearly swing and its half-year harmonic, and noise.
private let monthly: [Double] = {
    var r = SsRng(2)
    return (0..<(fitMonths + horizon)).map { t in
        let tt = Double(t)
        let base = 40 + 0.5 * tt + 6 * sin(2 * Double.pi * tt / 12) + 1.5 * cos(4 * Double.pi * tt / 12)
        return base + r.g() * 1.8
    }
}()

private var train: [Double] { Array(monthly.prefix(fitMonths)) }
private var heldOut: [Double] { Array(monthly.dropFirst(fitMonths)) }
private let naiveRmse: Double = forecastRmse(seasonalNaiveForecast(Array(monthly.prefix(48)), horizon: 12), Array(monthly.dropFirst(48)))

/// Undoes `d` lag-1 differences: each level is rebuilt from the last value of the level above it.
private func undifference(_ forecast: [Double], _ series: [Double], _ d: Int) -> [Double] {
    var levels = [series]
    for _ in 0..<d { levels.append(difference(levels.last!)) }
    var current = forecast
    for k in stride(from: d - 1, through: 0, by: -1) {
        var previous = levels[k].last!
        current = current.map { previous += $0; return previous }
    }
    return current
}

private func undoSeasonal(_ steps: [Double], _ series: [Double]) -> [Double] {
    var out: [Double] = []
    for (h, step) in steps.enumerated() {
        let i = series.count + h - 12
        out.append((i < series.count ? series[i] : out[i - series.count]) + step)
    }
    return out
}

/// Amplitude of the 12-month cycle: least squares on a line plus one sine and cosine.
private func seasonalSwing(_ values: [Double]) -> Double {
    var a = Array(repeating: Array(repeating: 0.0, count: 5), count: 4)
    for t in values.indices {
        let tt = Double(t)
        let row = [1.0, tt, sin(2 * Double.pi * tt / 12), cos(2 * Double.pi * tt / 12)]
        for i in 0..<4 {
            for j in 0..<4 { a[i][j] += row[i] * row[j] }
            a[i][4] += row[i] * values[t]
        }
    }
    for c in 0..<4 {
        var p = c
        for r in c..<4 where abs(a[r][c]) > abs(a[p][c]) { p = r }
        a.swapAt(c, p)
        for r in 0..<4 where r != c {
            let f = a[r][c] / a[c][c]
            for k in c...4 { a[r][k] -= f * a[c][k] }
        }
    }
    return hypot(a[2][4] / a[2][2], a[3][4] / a[3][3])
}

/// A forecast drawn from the last fitted month, so it reads as one continuous line.
private func forecastLine(_ forecast: [Double], _ color: Color, dashed: Bool = false) -> SsLine {
    SsLine(points: [(fitMonths - 1, train.last!)] + forecast.enumerated().map { (fitMonths + $0.offset, $0.element) }, color: color, dashed: dashed)
}

// MARK: - Model

private struct SsLine { let points: [(Int, Double)]; let color: Color; var dashed = false; var width: CGFloat = 2 }
private struct SsRow { let cells: [String]; var ink: StoryTone? = nil; var fill: StoryTone? = nil; var muted = false }
private struct SsBar { let label: String; let count: Int?; let frequent: Bool }
private struct SsNode { let label: String; let parent: Int; let lit: Bool }
private struct SsGridRow { let label: String; let ids: Set<Int>; let color: Color; var labelColor: Color? = nil }

private enum SsBlock {
    case series([SsLine])
    case table(header: [String], rows: [SsRow], weights: [CGFloat])
    case formula([String])
    case bars([SsBar])
    /// Node 0 is the root; `links` is an item's header chain, drawn dashed.
    case tree(nodes: [SsNode], links: [Int])
    case grid(rows: [SsGridRow], resultRow: Bool)
}

private struct SsFrame {
    let headline: String
    let body: String
    let blocks: [SsBlock]
    var legend: [(Color, SwatchStyle, String)] = []
    var chips: [LabChip] = []
    var action = ""
}

private struct SsParam { let name: String; let symbol: String; let count: Int; let initial: Int; let format: (Int) -> String }

private struct SsState: Equatable { var tab = 0; var a = 0; var b = 0 }

private struct SsLab {
    /// A step track; otherwise a panel of tabs, steppers and a button over one frame.
    let track: Bool
    var tabs: [String] = []
    var startTab = 0
    var params: [SsParam] = []
    var button: String? = nil
    var onButton: (SsState) -> SsState = { $0 }
    let frames: (SsState) -> [SsFrame]

    var initial: SsState { SsState(tab: startTab, a: params.first?.initial ?? 0, b: params.count > 1 ? params[1].initial : 0) }
}

private let observedLegend: (Color, SwatchStyle, String) = (sObserved, .dot, "Observed")

private func naiveRow(_ middle: String...) -> SsRow { SsRow(cells: ["seasonal naive"] + middle + [sfx(naiveRmse)], muted: true) }

/// Green when it beats seasonal naive, red when it doesn't.
private func verdict(_ rmse: Double) -> StoryTone { rmse < naiveRmse ? .done : .warn }

private func linePoints(_ values: [Double], from start: Int = 0) -> [(Int, Double)] { values.enumerated().map { (start + $0.offset, $0.element) } }

// MARK: - Moving average

private func maLab() -> SsLab {
    SsLab(track: false, tabs: ["k = 3", "k = 6", "k = 12"], startTab: 2) { s in
        let ks = [3, 6, 12]
        let k = ks[s.tab]
        func ma(_ w: Int) -> [(Int, Double)] { movingAverage(train, window: w).enumerated().compactMap { t, v in v.map { (t, $0) } } }
        let rawJitter = roughness(train)
        let rawSwing = seasonalSwing(train)
        func stats(_ w: Int) -> (Double, String, Double) {
            let v = ma(w).map(\.1)
            let lag = Double(w - 1) / 2
            return (roughness(v), (lag.truncatingRemainder(dividingBy: 1) == 0 ? sfx(lag, 0) : sfx(lag, 1)) + " mo", seasonalSwing(v))
        }
        let (jit, lag, swing) = stats(k)
        let (jit3, lag3, swing3) = stats(3)
        var rows = [SsRow(cells: ["raw", sfx(rawJitter), "—", sfx(rawSwing)], muted: true)]
        if k != 3 { rows.append(SsRow(cells: ["k = 3", sfx(jit3), lag3, sfx(swing3)])) }
        rows.append(SsRow(cells: ["k = \(k)", sfx(jit), lag, sfx(swing)], fill: .answer))
        let lines = (k != 3 ? [SsLine(points: ma(3), color: SimColors.grey, dashed: true, width: 1.5)] : []) + [SsLine(points: ma(k), color: sViolet, width: 2.5)]
        let headline: String, body: String
        switch k {
        case 12:
            headline = "A 12-month window cuts the seasonal swing from \(sfx(rawSwing, 1)) to {\(sfx(swing))}."
            body = "What's left is the trend, \(lag) late. That makes MA a trend estimate, not a forecaster."
        case 3:
            headline = "A 3-month window cuts jitter from \(sfx(rawJitter)) to {\(sfx(jit))}, but keeps the season."
            body = "The swing only falls to \(sfx(swing)): three months can't average out a year."
        default:
            headline = "A \(k)-month window shrinks the swing to {\(sfx(swing))}, \(lag) late."
            body = "Half a season still leaks through. Only a window of a full year, 12, cancels it."
        }
        return [SsFrame(
            headline: headline, body: body,
            blocks: [.series(lines), .table(header: ["series", "jitter", "lag", "season amp."], rows: rows, weights: [1.2, 1, 1, 1.3])],
            legend: [observedLegend, (sViolet, .line, "MA, k = \(k)")] + (k != 3 ? [(SimColors.grey, .dashedLine, "MA, k = 3")] : [])
        )]
    }
}

// MARK: - Autoregression

private func arRmse(_ p: Int) -> Double { forecastRmse(arForecast(fitAr(train, p: p), train, horizon: horizon), heldOut) }

private func arLab() -> SsLab {
    let best = firstMin(1...14) { arRmse($0) }
    return SsLab(track: false, params: [SsParam(name: "Lags", symbol: "p", count: 14, initial: 1) { "\($0 + 1)" }],
                 button: "Best by Held-out", onButton: { var s = $0; s.a = best - 1; return s }) { s in
        let p = s.a + 1
        let fit = fitAr(train, p: p)
        let forecast = arForecast(fit, train, horizon: horizon)
        let rmse = forecastRmse(forecast, heldOut)
        let ar2 = arRmse(2)
        let fitted = (p..<fitMonths).map { t in (t, fit.predict(Array(train.prefix(t)))) }
        var rows = [naiveRow("0")]
        if p != 2 { rows.append(SsRow(cells: ["AR(2)", "3", sfx(ar2)])) }
        rows.append(SsRow(cells: ["AR(\(p))", "\(p + 1)", sfx(rmse)], fill: .answer))
        let lines = [SsLine(points: fitted, color: sViolet)]
            + (p != 2 ? [forecastLine(arForecast(fitAr(train, p: 2), train, horizon: horizon), SimColors.grey, dashed: true)] : [])
            + [forecastLine(forecast, sRed)]
        let body: String
        if p < 12 { body = "\(p) \(p == 1 ? "lag can't" : "lags can't") reach last year, so the season fades out of the forecast." }
        else if p == 12 { body = "AR has no seasonal term, so reaching 12 months back costs 12 lags." }
        else { body = "Past 12, extra lags mostly fit noise: \(p + 1) parameters from 48 months." }
        return [SsFrame(
            headline: p == 2 ? "AR(2) forecasts {\(sfx(rmse))}, worse than seasonal naive's \(sfx(naiveRmse))." : "AR(2) forecasts \(sfx(ar2)); AR(\(p)) forecasts {\(sfx(rmse))}.",
            body: body,
            blocks: [.series(lines), .table(header: ["model", "params", "forecast RMSE"], rows: rows, weights: [1.5, 0.8, 1.3])],
            legend: [(sViolet, .line, "AR(\(p)) fit"), (sRed, .line, "AR(\(p)) forecast")] + (p != 2 ? [(SimColors.grey, .dashedLine, "AR(2) forecast")] : [])
        )]
    }
}

// MARK: - ARIMA

private func arimaLab() -> SsLab {
    SsLab(track: false, tabs: ["d = 0", "d = 1", "d = 2"], startTab: 1, params: [SsParam(name: "AR order", symbol: "p", count: 6, initial: 1) { "\($0 + 1)" }]) { s in
        let p = s.a + 1
        let d = s.tab
        let runs: [(acf: Double, rows: Int, forecast: [Double], rmse: Double)] = (0...2).map { dd in
            var w = train
            for _ in 0..<dd { w = difference(w) }
            let f = undifference(arForecast(fitAr(w, p: p), w, horizon: horizon), train, dd)
            return (lag1Autocorrelation(w), w.count - p, f, forecastRmse(f, heldOut))
        }
        let bestD = firstMin(0...2) { runs[$0].rmse }
        let others = (0...2).filter { $0 != d }
        let lines = others.map { forecastLine(runs[$0].forecast, SimColors.grey, dashed: true) } + [forecastLine(runs[d].forecast, sRed)]
        let rows = runs.enumerated().map { i, r in
            SsRow(cells: ["\(i)", sfx(r.acf, 3), "\(r.rows)", sfx(r.rmse)], ink: i == bestD ? .done : nil, fill: i == d ? .answer : nil)
        }
        let headline: String, body: String
        switch d {
        case 0:
            headline = "Undifferenced, lag-1 ACF is {\(sfx(runs[0].acf))}: the series still trends."
            body = "AR(\(p)) has to learn the trend from its own lags, and its forecast sags back toward the mean."
        case 1:
            headline = "One difference drops lag-1 ACF from \(sfx(runs[0].acf)) to {\(sfx(runs[1].acf))}."
            body = "A second difference pushes it to \(sfx(runs[2].acf)): over-differencing adds structure that isn't there."
        default:
            headline = "Two differences overshoot: lag-1 ACF is {w:\(sfx(runs[2].acf))}."
            body = "Integrating twice compounds every forecast error: RMSE \(sfx(runs[2].rmse)) against \(sfx(runs[1].rmse)) at d = 1."
        }
        return [SsFrame(
            headline: headline, body: body,
            blocks: [.series(lines), .table(header: ["d", "lag-1 ACF", "rows", "forecast RMSE"], rows: rows, weights: [0.6, 1.2, 0.8, 1.4])],
            legend: [(sRed, .line, "ARIMA(\(p),\(d),0)"), (SimColors.grey, .dashedLine, "d = \(others.map(String.init).joined(separator: ", "))")]
        )]
    }
}

// MARK: - SARIMA

private func sarimaLab() -> SsLab {
    SsLab(track: false, tabs: ["Seasonal diff off", "On (lag 12)"], startTab: 1, params: [SsParam(name: "AR order", symbol: "p", count: 4, initial: 0) { "\($0 + 1)" }]) { s in
        let p = s.a + 1
        let on = s.tab == 1
        let work = on ? difference(train, lag: 12) : train
        let fit = fitAr(work, p: p)
        let steps = arForecast(fit, work, horizon: horizon)
        let forecast = on ? undoSeasonal(steps, train) : steps
        let rmse = forecastRmse(forecast, heldOut)
        let phis = (1...p).map { sfx(fit.coefficients[$0]) }.joined(separator: ", ")
        let v = on ? "z" : "y"
        let model = p == 1 ? "\(v)ₜ = c + φ \(v)ₜ₋₁ + εₜ" : "\(v)ₜ = c + φ₁\(v)ₜ₋₁ + … + εₜ"
        let tone = verdict(rmse)
        let body: String
        if !on { body = "\(p) \(p == 1 ? "lag can't" : "lags can't") reach 12 months back, so the season washes out of the forecast." }
        else if p == 1 { body = "Subtracting last year removes the season, so a single AR term models the rest." }
        else { body = "More AR terms barely move it: the seasonal difference already did the work." }
        return [SsFrame(
            headline: on ? "One seasonal difference gets {\(sfx(rmse))} with \(p + 2) parameters." : "Without the seasonal difference, AR(\(p)) forecasts {w:\(sfx(rmse))}.",
            body: body,
            blocks: [
                .series([forecastLine(forecast, sRed)]),
                .formula([(on ? "zₜ = yₜ − yₜ₋₁₂, " : "") + model, "c = \(sfx(fit.coefficients[0])), φ = {v:\(phis)}, rows \(work.count) of 48"]),
                .table(header: ["model", "forecast RMSE"], rows: [naiveRow(), SsRow(cells: [on ? "Δ₁₂ + AR(\(p))" : "AR(\(p))", sfx(rmse)], ink: tone, fill: tone)], weights: [2, 1]),
            ],
            legend: [observedLegend, (sRed, .line, "Forecast")]
        )]
    }
}

// MARK: - Holt-Winters

private let smoothingLadder: [(Double, Double, Double)] = [
    (0.1, 0.05, 0.1), (0.1, 0.05, 0.3), (0.1, 0.05, 0.5), (0.2, 0.05, 0.3),
    (0.3, 0.1, 0.3), (0.5, 0.1, 0.3), (0.7, 0.2, 0.3), (0.9, 0.3, 0.5),
]

private let defaultSmoothing = 4

private func short(_ v: Double) -> String {
    let s = sfx(v, Int((v * 100).rounded()) % 10 == 0 ? 1 : 2)
    return s.hasPrefix("0") ? String(s.dropFirst()) : s
}

private func weights(_ i: Int) -> String { let w = smoothingLadder[i]; return "\(short(w.0)) \(short(w.1)) \(short(w.2))" }

private func hwFit(_ i: Int) -> HoltWintersFit { let w = smoothingLadder[i]; return holtWinters(train, alpha: w.0, beta: w.1, gamma: w.2, horizon: horizon) }

private func smoothingLab() -> SsLab {
    let scores = smoothingLadder.indices.map { forecastRmse(hwFit($0).forecast, heldOut) }
    let best = firstMin(scores.indices) { scores[$0] }
    return SsLab(track: false, params: [SsParam(name: "Smoothing", symbol: "αβγ", count: smoothingLadder.count, initial: defaultSmoothing, format: weights)],
                 button: "Tune on Held-out", onButton: { var s = $0; s.a = best; return s }) { s in
        let i = s.a
        let fit = hwFit(i)
        let rmse = scores[i]
        var rows = [naiveRow("—"), SsRow(cells: ["default", weights(defaultSmoothing), sfx(scores[defaultSmoothing])])]
        if i != defaultSmoothing {
            rows.append(SsRow(cells: [i == best ? "tuned" : "current", weights(i), sfx(rmse)], ink: i == best ? .done : nil, fill: i == best ? .done : .answer))
        }
        let alpha = smoothingLadder[i].0
        let headline: String
        if i == best { headline = "Tuned, the forecast error is {\(sfx(rmse))} against \(sfx(naiveRmse)) for seasonal naive." }
        else if i == defaultSmoothing { headline = "With the default weights the forecast error is {\(sfx(rmse))}." }
        else { headline = "At α β γ = \(weights(i)) the forecast error is {\(sfx(rmse))}." }
        let body: String
        if i == best { body = "Level, trend and season each get their own smoothing weight." }
        else if alpha >= 0.5 { body = "A high α lets each month's noise move the level, and the forecast starts from a jolt." }
        else { body = "Each weight sets how fast its component forgets the past. Tune them on the held-out year." }
        return [SsFrame(
            headline: headline, body: body,
            blocks: [
                .series([
                    SsLine(points: (12..<fitMonths).map { ($0, fit.level[$0]) }, color: sOrange, dashed: true, width: 1.5),
                    SsLine(points: (12..<fitMonths).map { ($0, fit.fitted[$0]) }, color: sViolet),
                    forecastLine(fit.forecast, sRed),
                ]),
                .table(header: ["model", "αβγ", "forecast RMSE"], rows: rows, weights: [1.4, 1.1, 1.3]),
            ],
            legend: [(sViolet, .line, "Fitted"), (sOrange, .dashedLine, "Level"), (sRed, .line, "Forecast")]
        )]
    }
}

// MARK: - Prophet

private func prophetLab() -> SsLab {
    SsLab(track: false, params: [SsParam(name: "Changepoints", symbol: "n", count: 9, initial: 4) { "\($0)" }, SsParam(name: "Fourier order", symbol: "K", count: 5, initial: 2) { "\($0)" }]) { s in
        let n = s.a, k = s.b
        let fit = fitProphet(train, changepointCount: n, fourierOrder: k, penalty: 1.0, horizon: horizon)
        let rmse = forecastRmse(fit.forecast, heldOut)
        let tone = verdict(rmse)
        let body: String
        if k == 0 { body = "With K = 0 there is no s(t), so the forecast is a straight line through the season." }
        else if n == 0 { body = "With no changepoints g(t) is one straight line, which is all this steady trend needs." }
        else { body = "The penalty shrinks unneeded slope changes toward zero, so the trend doesn't chase noise." }
        return [SsFrame(
            headline: "The additive fit forecasts {\(sfx(rmse))} against \(sfx(naiveRmse)) for the baseline.",
            body: body,
            blocks: [
                .series([
                    SsLine(points: linePoints(fit.trend + fit.forecastTrend), color: sOrange, dashed: true, width: 1.5),
                    SsLine(points: linePoints(fit.fitted), color: sViolet),
                    SsLine(points: [(fitMonths - 1, fit.fitted.last!)] + linePoints(fit.forecast, from: fitMonths), color: sRed),
                ]),
                .formula(["y(t) = g(t) + s(t)", "\(n) changepoints, Fourier order \(k), ridge λ = 1"]),
                .table(header: ["model", "in-sample", "forecast"], rows: [naiveRow("—"), SsRow(cells: ["Prophet-style", sfx(fit.trainRmse), sfx(rmse)], ink: tone, fill: tone)], weights: [1.6, 1, 1]),
            ],
            legend: [(sOrange, .dashedLine, "Trend g(t)"), (sViolet, .line, "g + s"), (sRed, .line, "Forecast")]
        )]
    }
}

// MARK: - Pattern mining: shared pieces

private func label(_ items: [String]) -> String { items.sorted().joined() }

private func freqItems() -> [String] { basketItems.filter { support([$0]) >= minSupport } }

/// Every frequent itemset of the ten baskets, by size then name.
private let frequentSets: [[String]] = {
    let items = basketItems.filter { support([$0]) >= minSupport }
    var out: [[String]] = []
    for mask in 1..<(1 << items.count) {
        let set = items.enumerated().filter { mask & (1 << $0.offset) != 0 }.map(\.element)
        if support(set) >= minSupport { out.append(set) }
    }
    return out.sorted { ($0.count, label($0)) < ($1.count, label($1)) }
}()

private func sizesLine(_ sets: [[String]]) -> [String] {
    (1...3).compactMap { size in
        let g = sets.filter { $0.count == size }
        return g.isEmpty ? nil : g.map { "\(label($0)):\(support($0))" }.joined(separator: "  ")
    }
}

private func sizesChip() -> LabChip { LabChip(key: "tid-list sizes", value: freqItems().map { "\($0): \(support([$0]))" }.joined(separator: " ")) }

private func same(_ a: [String], _ b: [String]) -> Bool { a.sorted() == b.sorted() }

// MARK: - Apriori

private func bar(_ set: [String], counted: Bool = true) -> SsBar { SsBar(label: label(set), count: counted ? support(set) : nil, frequent: support(set) >= minSupport) }

private func aprioriLab() -> SsLab {
    SsLab(track: true) { _ in
        let singles = basketItems.map { [$0] }
        let l1 = freqItems()
        let dropped = basketItems.filter { !l1.contains($0) }
        var pairs: [[String]] = []
        for (i, a) in l1.enumerated() { for b in l1.dropFirst(i + 1) { pairs.append([a, b]) } }
        let l2 = pairs.filter { support($0) >= minSupport }
        let allPairs = basketItems.count * (basketItems.count - 1) / 2
        func inL2(_ s: [String]) -> Bool { l2.contains { same($0, s) } }
        // Join pairs that share their first item, then prune any triple with an infrequent pair.
        var joined: [[String]] = []
        for (i, a) in l2.enumerated() {
            for b in l2.dropFirst(i + 1) where a.sorted()[0] == b.sorted()[0] {
                let t = a + b.filter { !a.contains($0) }
                if !joined.contains(where: { same($0, t) }) { joined.append(t) }
            }
        }
        let kept = joined.filter { t in t.allSatisfy { x in inL2(t.filter { $0 != x }) } }
        func prunedBy(_ t: [String]) -> [String] { t.map { x in t.filter { $0 != x } }.first { !inL2($0) }! }
        let pruned = joined.filter { t in !kept.contains { same($0, t) } }
        let l3 = kept.filter { support($0) >= minSupport }
        let l1Line = "L1 = \\{\(l1.joined(separator: ", "))}  " + dropped.map { "\($0) dropped (\(support([$0])))" }.joined(separator: ", ")
        let l2Line = "L2 = \\{{\(l2.map(label).joined(separator: ", "))}}"
        let legend: [(Color, SwatchStyle, String)] = [(sGreen, .fill, "Frequent"), (SimColors.grey.opacity(0.5), .fill, "Below \(minSupport)"), (SimColors.active, .dashedLine, "Threshold")]
        func passes(_ n: Int) -> LabChip { LabChip(key: "baskets read", value: "\(n) \(n == 1 ? "pass" : "passes")") }
        var rules: [AssociationRule] = []
        for set in frequentSets where set.count >= 2 { for c in set { rules.append(associationRule(set.filter { $0 != c }, [c])) } }
        // Stable, as Kotlin's sortedByDescending is.
        rules = rules.enumerated().sorted { ($0.element.lift, -$0.offset) > ($1.element.lift, -$1.offset) }.map(\.element)
        rules = Array(rules.prefix(4))
        let top = rules[0]
        let keptText = kept.map(label).joined(separator: ", ")
        return [
            SsFrame(
                headline: "Pass 1 counts all {\(basketItems.count)} items in one read of the baskets.",
                body: "Support is the number of baskets holding an itemset. Min support is \(minSupport) of \(transactions.count).",
                blocks: [.bars(singles.map { bar($0) })], legend: legend, chips: [passes(1)], action: "Prune"
            ),
            SsFrame(
                headline: "\(dropped.joined(separator: ", ")) \(dropped.count == 1 ? "is" : "are") below \(minSupport), so no itemset containing it can be frequent.",
                body: "That is the Apriori property: every subset of a frequent itemset is frequent too.",
                blocks: [.bars(singles.map { bar($0) }), .formula([l1Line])], legend: legend, chips: [passes(1)], action: "Join Pairs"
            ),
            SsFrame(
                headline: "Joining L1 gives {\(pairs.count)} candidate pairs instead of \(allPairs).",
                body: "Each pair is built from two frequent items. None of them has been counted yet.",
                blocks: [.bars(pairs.map { bar($0, counted: false) }), .formula([l1Line, "C2 = \\{\(pairs.map(label).joined(separator: ", "))}"])],
                legend: legend, chips: [LabChip(key: "candidates", value: "\(pairs.count) of \(allPairs)", tint: .answer), passes(1)], action: "Count Pairs"
            ),
            SsFrame(
                headline: "\(l2.count) of \(pairs.count) pairs reach support \(minSupport).",
                body: "Only pairs of frequent items are counted, so \(allPairs - pairs.count) pairs with \(dropped.joined(separator: ", ")) are never tried. Pass 3 tests only \(l3.map(label).joined(separator: ", ")).",
                blocks: [.bars(pairs.map { bar($0) }), .formula([l1Line, l2Line])],
                legend: legend, chips: [LabChip(key: "candidates", value: "\(pairs.count) of \(allPairs)", tint: .answer), passes(2)], action: "Next Pass"
            ),
            SsFrame(
                headline: "Of \(joined.count) joined triples, only {\(keptText)} has every pair frequent.",
                body: pruned.map { "\(label($0)) is pruned: \(label(prunedBy($0))) has support \(support(prunedBy($0)))." }.joined(separator: " ") + " Pruning costs no basket reads.",
                blocks: [.bars(kept.map { bar($0, counted: false) }), .formula(joined.map { t in kept.contains { same($0, t) } ? "\(label(t)): every pair frequent → keep" : "\(label(t)): \(label(prunedBy(t))) is rare → prune" })],
                legend: legend, chips: [LabChip(key: "candidates", value: "\(kept.count) of \(joined.count)", tint: .answer), passes(2)], action: "Count Triples"
            ),
            SsFrame(
                headline: "\(l3.map(label).joined(separator: ", ")) is in {\(l3.map { "\(support($0))" }.joined(separator: ", "))} baskets: frequent.",
                body: "Pass 3 read every basket once more, to count a single candidate.",
                blocks: [.bars(kept.map { bar($0) }), .formula(["L3 = \\{\(l3.map(label).joined(separator: ", "))}"])], legend: legend, chips: [passes(3)], action: "Join Again"
            ),
            SsFrame(
                headline: "One triple can't be joined into a 4-item set, so Apriori {stops}.",
                body: "Levels stop when a pass finds fewer than two frequent itemsets to join.",
                blocks: [.bars(kept.map { bar($0) }), .formula(["L3 = \\{\(l3.map(label).joined(separator: ", "))},  C4 = ∅"])], legend: legend, chips: [passes(3)], action: "Find Rules"
            ),
            SsFrame(
                headline: "The strongest rule, {\(top.label)}, has lift \(sfx(top.lift)).",
                body: "Confidence is support(both) ÷ support(left). Lift above 1 means the items go together more than chance.",
                blocks: [.table(header: ["rule", "supp", "conf", "lift"], rows: rules.enumerated().map { i, r in
                    SsRow(cells: [r.label, "\(r.support)", sfx(r.confidence), sfx(r.lift)], fill: i == 0 ? .answer : nil)
                }, weights: [1.4, 0.8, 0.9, 0.9])],
                chips: [passes(3)], action: "Summary"
            ),
            SsFrame(
                headline: "{\(frequentSets.count)} frequent itemsets from 3 passes over \(transactions.count) baskets.",
                body: "Each pass reads every basket once; pruning keeps the candidate lists short.",
                blocks: [.bars(frequentSets.map { bar($0) })], legend: legend, chips: [passes(3)], action: "Start Over"
            ),
        ]
    }
}

// MARK: - Eclat

private func tids(_ set: [String]) -> Set<Int> { Set(transactions.indices.filter { i in set.allSatisfy { transactions[i].contains($0) } }.map { $0 + 1 }) }

private func braces(_ ids: Set<Int>) -> String { "\\{\(ids.sorted().map(String.init).joined(separator: ","))}" }

private func eclatLab() -> SsLab {
    SsLab(track: true) { _ in
        let items = freqItems()
        let legend: [(Color, SwatchStyle, String)] = [(sBlue, .fill, "In tid-list"), (sGreen, .fill, "In both")]
        func row(_ set: [String]) -> SsGridRow { SsGridRow(label: label(set), ids: tids(set), color: sBlue) }
        // Each step and what the step before it should call its action.
        var steps: [(SsFrame, String)] = []
        steps.append((SsFrame(
            headline: "Eclat turns the baskets sideways: each item keeps its {basket ids}.",
            body: "One read of the baskets builds these tid-lists. Support is then just the length of a list.",
            blocks: [.grid(rows: basketItems.map { row([$0]) }, resultRow: false)],
            legend: legend, chips: [LabChip(key: "tid-list sizes", value: basketItems.map { "\($0): \(support([$0]))" }.joined(separator: " "))]
        ), ""))
        let rare = basketItems.filter { !items.contains($0) }
        steps.append((SsFrame(
            headline: rare.map { "\($0)'s list has {w:\(support([$0]))} ids" }.joined(separator: ", ") + ", below min support \(minSupport).",
            body: "Every itemset containing it would have an even shorter list, so it leaves the search.",
            blocks: [.grid(rows: basketItems.map { items.contains($0) ? row([$0]) : SsGridRow(label: $0, ids: tids([$0]), color: SimColors.grey.opacity(0.45), labelColor: SimColors.grey) }, resultRow: false)],
            legend: legend, chips: [sizesChip()]
        ), "Drop Rare Items"))
        func intersect(_ a: [String], _ b: [String]) -> Bool {
            let both = a + b.filter { !a.contains($0) }
            let ids = tids(both)
            let ok = ids.count >= minSupport
            let name = "\(label(a))∩\(label(b))"
            let headline: String, body: String
            if ok && both.count == 2 { headline = "\(name) has {\(ids.count)} baskets, so \(label(both)) is frequent." }
            else if ok { headline = "\(name) = \(braces(ids)): \(label(both)) has {\(ids.count)} baskets." }
            else { headline = "\(name) has only {w:\(ids.count)} \(ids.count == 1 ? "basket" : "baskets"), so \(label(both)) is dropped." }
            if !ok { body = "Anything that extends \(label(both)) would be rarer still, so that whole branch is skipped." }
            else if both.count == 2 { body = "Eclat stores each item's basket ids. Support is an intersection size, with no rescan." }
            else { body = "Longer itemsets come from intersecting two frequent siblings that share a prefix, never from the baskets." }
            steps.append((SsFrame(
                headline: headline, body: body,
                blocks: [
                    .grid(rows: [row(a), row(b), SsGridRow(label: name, ids: ids, color: sGreen, labelColor: sGreen)], resultRow: true),
                    .formula(["\(braces(tids(a))) ∩ \(braces(tids(b)))", "= \(braces(ids)) → support {\(ok ? "" : "w:")\(ids.count)}"]),
                ],
                legend: legend, chips: [sizesChip()]
            ), "Intersect"))
            return ok
        }
        for (i, item) in items.enumerated() {
            let later = Array(items.dropFirst(i + 1))
            if later.isEmpty { continue }
            if later.count > 1 {
                steps.append((SsFrame(
                    headline: i == 0 ? "Depth first: \(item) is intersected with each later item." : "\(item)'s branch: pair \(item) with each later item.",
                    body: i == 0 ? "Every itemset starting with \(item) is checked before moving on to \(items[1])."
                        : "Itemsets with \(items.prefix(i).joined(separator: ", ")) were found in earlier branches, so each set is visited once.",
                    blocks: [.grid(rows: items.dropFirst(i).map { row([$0]) }, resultRow: false)],
                    legend: legend, chips: [sizesChip()]
                ), i == 0 ? "Start with \(item)" : "Next Branch"))
            }
            let frequent = later.filter { intersect([item], [$0]) }.map { [item, $0] }
            for (j, a) in frequent.enumerated() { for b in frequent.dropFirst(j + 1) { _ = intersect(a, b) } }
        }
        steps.append((SsFrame(
            headline: "{\(frequentSets.count)} frequent itemsets, and the baskets were read {once}.",
            body: "Eclat trades memory for speed: the tid-lists replace every later pass over the data.",
            blocks: [.formula(sizesLine(frequentSets))],
            legend: legend, chips: [sizesChip()]
        ), "Summary"))
        return steps.enumerated().map { i, step in
            var f = step.0
            f.action = i < steps.count - 1 ? steps[i + 1].1 : "Start Over"
            return f
        }
    }
}

// MARK: - FP-growth

private func nodeWord(_ n: Int) -> String { n == 1 ? "1 node gives" : "\(n) nodes give" }

private func fpLab() -> SsLab {
    SsLab(track: true) { _ in
        let order = fpItemOrder()
        let dropped = basketItems.filter { !order.contains($0) }
        let header = order.map { "\($0):\(support([$0]))" }.joined(separator: "  ") + "  " + dropped.map { "(\($0):\(support([$0])) dropped)" }.joined(separator: ", ")
        let entries = transactions.reduce(0) { $0 + fpSortedTransaction($1).count }
        func tree(_ upTo: Int, _ lit: Set<Int>, _ links: [Int] = []) -> SsBlock {
            let t = buildFpTree(upTo: upTo)
            return .tree(nodes: [SsNode(label: "root", parent: -1, lit: false)] + t.nodes.map { SsNode(label: "\($0.item):\($0.count)", parent: ($0.parent ?? -1) + 1, lit: lit.contains($0.id)) },
                         links: links.map { $0 + 1 })
        }
        func pathOf(_ upTo: Int) -> [Int] {
            let t = buildFpTree(upTo: upTo)
            var parent: Int? = nil
            var path: [Int] = []
            for item in fpSortedTransaction(transactions[upTo - 1]) {
                let node = t.nodes.first { $0.item == item && $0.parent == parent }!
                path.append(node.id)
                parent = node.id
            }
            return path
        }
        var frames = [SsFrame(
            headline: "Pass 1 counts items and orders them {\(order.joined(separator: ", "))}.",
            body: "Every basket is inserted in this order, so the common items share the top of the tree.",
            blocks: [tree(0, []), .formula([header])],
            chips: [LabChip(key: "entries → nodes", value: "0 → 0")],
            action: "Insert Basket 1"
        )]
        var entriesSoFar = 0
        for b in 1...transactions.count {
            let items = fpSortedTransaction(transactions[b - 1])
            entriesSoFar += items.count
            let before = buildFpTree(upTo: b - 1).nodes.count
            let after = buildFpTree(upTo: b).nodes.count
            let added = after - before
            let shared = items.count - added
            let headline: String
            if b == 1 { headline = "Basket 1 starts the path {\(items.joined(separator: " → "))}." }
            else if added == 0 { headline = "Basket \(b) follows an existing path: {0} new nodes, \(items.count) counts bumped." }
            else if shared == 0 { headline = "Basket \(b) shares nothing, so it starts {\(added)} new \(added == 1 ? "node" : "nodes") from the root." }
            else { headline = "Basket \(b) shares \(shared) \(shared == 1 ? "node" : "nodes") and adds {\(added)}." }
            frames.append(SsFrame(
                headline: headline,
                body: "\(label(transactions[b - 1])) sorts to \(items.joined(separator: " "))" + (transactions[b - 1].count > items.count ? ", with the rare item left out." : ".") + " Shared prefixes are what keep the tree small.",
                blocks: [tree(b, Set(pathOf(b))), .formula([header, "basket \(b): \(items.joined(separator: " "))"])],
                legend: [(SimColors.active, .fill, "This basket's path")],
                chips: [LabChip(key: "entries → nodes", value: "\(entriesSoFar) → \(after)", good: true)],
                action: b < transactions.count ? "Insert Basket \(b + 1)" : "Mine \(order.last!)"
            ))
        }
        let full = buildFpTree()
        let mined = Array(order.reversed())
        for (i, item) in mined.enumerated() {
            let base = conditionalPatternBase(full, item)
            let before = Array(order.prefix(order.firstIndex(of: item)!))
            // Every combination of earlier items that reaches min support inside this item's base.
            var found: [[String]] = []
            if !before.isEmpty {
                for mask in 1..<(1 << before.count) {
                    let set = before.enumerated().filter { mask & (1 << $0.offset) != 0 }.map(\.element)
                    let count = base.filter { p in set.allSatisfy(p.path.contains) }.reduce(0) { $0 + $1.count }
                    if count >= minSupport { found.append(set + [item]) }
                }
            }
            let pairCounts = before.map { other in (other, base.filter { $0.path.contains(other) }.reduce(0) { $0 + $1.count }) }
            let baseText = base.map { ($0.path.isEmpty ? "∅" : $0.path.joined()) + ":\($0.count)" }.joined(separator: " ")
            let nodes = full.header[item] ?? []
            let headline: String
            if before.isEmpty { headline = "\(item) sits at the top, so its pattern base is {empty}." }
            else if found.isEmpty { headline = "\(item)'s \(nodeWord(nodes.count)) its pattern base; no pair reaches \(minSupport)." }
            else if found.count == 1 { headline = "\(item)'s \(nodeWord(nodes.count)) its pattern base; only {\(label(found[0]))} reaches \(minSupport)." }
            else { headline = "\(item)'s \(nodeWord(nodes.count)) its pattern base: " + found.map { "{\(label($0))}" }.joined(separator: ", ") + " reach \(minSupport)." }
            frames.append(SsFrame(
                headline: headline,
                body: before.isEmpty ? "Every itemset with \(item) was already found while mining the items below it."
                    : "Shared prefixes compress the baskets into \(full.nodes.count) nodes, and mining never rescans them.",
                blocks: [
                    tree(transactions.count, Set(nodes), nodes),
                    .formula(["\(item)'s pattern base: \(baseText.isEmpty ? "∅" : baseText)"]
                        + (pairCounts.isEmpty ? [] : [pairCounts.map { "\($0.0)·\(item) = " + ($0.1 >= minSupport ? "{\($0.1)}" : "\($0.1)") }.joined(separator: "; ")])),
                ],
                legend: [(SimColors.active, .fill, "\(item) node"), (SimColors.active, .dashedLine, "Node link")],
                chips: [LabChip(key: "entries → nodes", value: "\(entries) → \(full.nodes.count)", good: true)],
                action: i < mined.count - 1 ? "Mine Next Item" : "Summary"
            ))
        }
        frames.append(SsFrame(
            headline: "{\(frequentSets.count)} frequent itemsets from a tree of \(full.nodes.count) nodes.",
            body: "The baskets were read twice: once to count items, once to build the tree.",
            blocks: [tree(transactions.count, []), .formula(sizesLine(frequentSets))],
            chips: [LabChip(key: "entries → nodes", value: "\(entries) → \(full.nodes.count)", good: true)],
            action: "Start Over"
        ))
        return frames
    }
}

private func seriesLab(_ topicId: String) -> SsLab {
    switch topicId {
    case "autoregression": return arLab()
    case "arima": return arimaLab()
    case "sarima": return sarimaLab()
    case "exponential_smoothing": return smoothingLab()
    case "prophet": return prophetLab()
    case "apriori": return aprioriLab()
    case "eclat": return eclatLab()
    case "fp_growth": return fpLab()
    default: return maLab()
    }
}

// MARK: - Lab

struct SeriesStoryLab: View {
    private let lab: SsLab

    init(topicId: String) { lab = seriesLab(topicId) }

    var body: some View {
        if lab.track { SsTrackLab(lab: lab) } else { SsPanelLab(lab: lab) }
    }
}

private struct SsTrackLab: View {
    let lab: SsLab
    @State private var frames: [SsFrame]
    @State private var playback: PlaybackState

    init(lab: SsLab) {
        self.lab = lab
        let frames = lab.frames(lab.initial)
        _frames = State(initialValue: frames)
        let state = PlaybackState(stepCount: frames.count, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        let step = UserDefaults.standard.integer(forKey: "openStep")
        if step > 0 { state.index = min(step, frames.count) - 1 }
        #endif
        _playback = State(initialValue: state)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SsBody(frame: frames[min(playback.index, frames.count - 1)])
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) },
                              action: { frames[min(max($0, 0), frames.count - 1)].action })
        }
    }
}

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class SsModel {
    let lab: SsLab
    var state: SsState { didSet { if state != oldValue { frame = lab.frames(state)[0] } } }
    private(set) var frame: SsFrame

    init(lab: SsLab) {
        self.lab = lab
        state = lab.initial
        frame = lab.frames(lab.initial)[0]
    }
}

private struct SsPanelLab: View {
    @State private var model: SsModel
    @Environment(\.labDock) private var dock

    init(lab: SsLab) { _model = State(initialValue: SsModel(lab: lab)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SsBody(frame: model.frame)
            if dock == nil {
                Divider().padding(.top, 16)
                SsControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(SsControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.state = model.lab.initial }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct SsControls: View {
    let model: SsModel

    var body: some View {
        let lab = model.lab
        let s = model.state
        VStack(spacing: 14) {
            if !lab.tabs.isEmpty {
                LabSegments(labels: lab.tabs, selected: Binding(get: { model.state.tab }, set: { model.state.tab = $0 }))
            }
            ForEach(lab.params.indices, id: \.self) { i in
                let p = lab.params[i]
                let v = i == 0 ? s.a : s.b
                LabParamStepper(param: LabParam(tab: p.name, name: p.name, symbol: p.symbol, text: p.format(v), canDecrease: v > 0, canIncrease: v < p.count - 1)) { d in
                    let next = min(max(v + d, 0), p.count - 1)
                    if i == 0 { model.state.a = next } else { model.state.b = next }
                }
            }
            if let label = lab.button {
                LabButton(label: label, primary: true) { model.state = lab.onButton(model.state) }
            }
        }
    }
}

private struct SsBody: View {
    let frame: SsFrame

    var body: some View {
        LabCard {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(frame.blocks.indices, id: \.self) { i in
                    switch frame.blocks[i] {
                    case .series(let lines): SsSeriesView(lines: lines)
                    case let .table(header, rows, weights): SsTableView(header: header, rows: rows, weights: weights)
                    case .formula(let lines): SsFormulaView(lines: lines)
                    case .bars(let bars): SsBarsView(bars: bars)
                    case let .tree(nodes, links): SsTreeView(nodes: nodes, links: links)
                    case let .grid(rows, resultRow): SsGridView(rows: rows, resultRow: resultRow)
                    }
                }
                if !frame.legend.isEmpty { StoryLegendRow(items: frame.legend).padding(.top, 2) }
            }
        }
        if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
        LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
    }
}

// MARK: - Rendering

private struct SsStage: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private func circlePath(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private struct SsFormulaView: View {
    let lines: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 2) {
            ForEach(lines.indices, id: \.self) { i in
                storyText(lines[i], palette).font(AppFont.mono(13)).multilineTextAlignment(.center)
            }
        }
        .foregroundStyle(palette.onSurface.opacity(0.85))
        .lineSpacing(3)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10).padding(.horizontal, 12)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct SsSeriesView: View {
    let lines: [SsLine]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let pad: CGFloat = 14
            let lo = monthly.min()!, hi = monthly.max()!
            let span = hi - lo
            let yLo = lo - span * 0.08, yHi = hi + span * 0.18
            func at(_ t: Int, _ v: Double) -> CGPoint {
                CGPoint(x: pad + (CGFloat(t) + 0.5) / CGFloat(monthly.count) * (size.width - 2 * pad),
                        y: size.height - pad - CGFloat((v - yLo) / (yHi - yLo)) * (size.height - 2 * pad))
            }
            let split = at(fitMonths, 0).x - (size.width - 2 * pad) / CGFloat(monthly.count) / 2
            var divider = Path(); divider.move(to: CGPoint(x: split, y: pad / 2)); divider.addLine(to: CGPoint(x: split, y: size.height - pad / 2))
            ctx.stroke(divider, with: .color(palette.muted.opacity(0.5)), lineWidth: 1)
            ctx.draw(Text("held out").font(AppFont.sans(11)).foregroundStyle(palette.muted), at: CGPoint(x: split + 4, y: pad / 2), anchor: .topLeading)
            for line in lines {
                var path = Path()
                for (i, p) in line.points.enumerated() { if i == 0 { path.move(to: at(p.0, p.1)) } else { path.addLine(to: at(p.0, p.1)) } }
                ctx.stroke(path, with: .color(line.color), style: StrokeStyle(lineWidth: line.width, lineJoin: .round, dash: line.dashed ? [5, 4] : []))
            }
            for (t, v) in monthly.enumerated() {
                let o = at(t, v)
                if t < fitMonths {
                    ctx.fill(circlePath(o, 3), with: .color(sObserved))
                } else {
                    ctx.fill(circlePath(o, 4), with: .color(palette.surface))
                    ctx.stroke(circlePath(o, 4), with: .color(.white.opacity(0.85)), lineWidth: 1.5)
                }
            }
        }
        .aspectRatio(2, contentMode: .fit)
        .modifier(SsStage())
    }
}

private struct SsTableView: View {
    let header: [String]
    let rows: [SsRow]
    let weights: [CGFloat]
    @Environment(\.palette) private var palette

    var body: some View {
        let total = weights.reduce(0, +)
        GeometryReader { geo in
            let inner = geo.size.width - 24
            VStack(spacing: 4) {
                HStack(spacing: 0) {
                    ForEach(header.indices, id: \.self) { i in
                        Text(header[i]).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1)
                            .frame(width: inner * weights[i] / total, alignment: i == 0 ? .leading : .trailing)
                    }
                }
                .padding(.horizontal, 12)
                ForEach(rows.indices, id: \.self) { r in
                    let row = rows[r]
                    let ink = row.muted ? palette.muted : row.ink?.ink(palette) ?? palette.onSurface
                    HStack(spacing: 0) {
                        ForEach(row.cells.indices, id: \.self) { i in
                            Text(row.cells[i])
                                .font(i == 0 ? AppFont.sans(15, .semibold) : AppFont.mono(15))
                                .foregroundStyle(ink)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                                .frame(width: inner * weights[i] / total, alignment: i == 0 ? .leading : .trailing)
                        }
                    }
                    .padding(.horizontal, 12)
                    .frame(height: 40)
                    .background(fill(row.fill), in: RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .frame(height: CGFloat(rows.count) * 44 + 18)
    }

    private func fill(_ tone: StoryTone?) -> Color {
        guard let tone else { return .clear }
        return tone == .answer ? SimColors.answer.opacity(0.22) : tone.color.opacity(0.16)
    }
}

private struct SsBarsView: View {
    let bars: [SsBar]
    @Environment(\.palette) private var palette

    var body: some View {
        let top = CGFloat(transactions.count)
        let threshold = CGFloat(minSupport) / top
        VStack(spacing: 6) {
            ForEach(bars.indices, id: \.self) { i in
                let bar = bars[i]
                HStack(spacing: 0) {
                    Text(bar.label).font(AppFont.mono(13, .bold)).foregroundStyle(bar.count == nil || bar.frequent ? palette.onSurface : palette.muted)
                        .lineLimit(1).frame(width: 40, alignment: .leading)
                    Canvas { ctx, size in
                        ctx.fill(Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 4), with: .color(SimColors.tint))
                        if let c = bar.count {
                            ctx.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: size.width * CGFloat(c) / top, height: size.height), cornerRadius: 4),
                                     with: .color(bar.frequent ? sGreen : SimColors.grey.opacity(0.5)))
                        }
                        var line = Path(); line.move(to: CGPoint(x: size.width * threshold, y: -3)); line.addLine(to: CGPoint(x: size.width * threshold, y: size.height + 3))
                        ctx.stroke(line, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                    }
                    .frame(height: 18)
                    Text(bar.count.map(String.init) ?? "?").font(AppFont.mono(13))
                        .foregroundStyle(bar.count != nil && bar.frequent ? StoryTone.done.ink(palette) : palette.muted)
                        .frame(width: 32, alignment: .trailing)
                }
            }
            HStack(spacing: 0) {
                Color.clear.frame(width: 40, height: 1)
                GeometryReader { geo in
                    Text("min support \(minSupport)").font(AppFont.sans(11)).foregroundStyle(SimColors.active).fixedSize()
                        .offset(x: geo.size.width * threshold + 4)
                }
                .frame(height: 16)
                Color.clear.frame(width: 32, height: 1)
            }
        }
        .padding(.horizontal, 12).padding(.top, 10).padding(.bottom, 6)
        .modifier(SsStage())
    }
}

private struct SsTreeView: View {
    let nodes: [SsNode]
    let links: [Int]
    @Environment(\.palette) private var palette

    var body: some View {
        // Depth of each node, and x slots: leaves take consecutive slots in depth-first order and each
        // parent sits over the middle of its children.
        var children: [Int: [Int]] = [:]
        for i in nodes.indices { children[nodes[i].parent, default: []].append(i) }
        var depth = Array(repeating: 0, count: nodes.count)
        var x = Array(repeating: 0.0, count: nodes.count)
        var slot = 0
        func place(_ i: Int, _ d: Int) {
            depth[i] = d
            let kids = children[i] ?? []
            if kids.isEmpty {
                x[i] = Double(slot)
                slot += 1
            } else {
                for k in kids { place(k, d + 1) }
                x[i] = (x[kids.first!] + x[kids.last!]) / 2
            }
        }
        place(0, 0)
        let levels = (depth.max() ?? 0) + 1
        let slots = max(slot, 1)
        let depths = depth, xs = x
        return Canvas { ctx, size in
            let w: CGFloat = 50, h: CGFloat = 26
            func at(_ i: Int) -> CGPoint {
                CGPoint(x: slots == 1 ? size.width / 2 : w / 2 + 14 + CGFloat(xs[i] / Double(slots - 1)) * (size.width - w - 28), y: 22 + CGFloat(depths[i]) * 48)
            }
            for (i, n) in nodes.enumerated() where n.parent >= 0 {
                var p = Path(); p.move(to: at(n.parent)); p.addLine(to: at(i))
                ctx.stroke(p, with: .color(SimColors.grey.opacity(0.6)), lineWidth: 1.5)
            }
            for (a, b) in zip(links, links.dropFirst()) {
                var p = Path(); p.move(to: at(a)); p.addLine(to: at(b))
                ctx.stroke(p, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
            }
            for (i, n) in nodes.enumerated() {
                let c = at(i)
                ctx.fill(Path(roundedRect: CGRect(x: c.x - w / 2, y: c.y - h / 2, width: w, height: h), cornerRadius: 6), with: .color(n.lit ? SimColors.active : Color(hex: 0x2E3340)))
                ctx.draw(Text(n.label).font(AppFont.mono(12, .bold)).foregroundStyle(n.lit ? Color(hex: 0x1B1F27) : palette.onSurface), at: c)
            }
        }
        .frame(height: CGFloat(28 + levels * 48))
        .frame(maxWidth: .infinity)
        .modifier(SsStage())
    }
}

private struct SsGridView: View {
    let rows: [SsGridRow]
    let resultRow: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        let n = transactions.count
        VStack(spacing: 6) {
            HStack(spacing: 0) {
                Color.clear.frame(width: 44, height: 1)
                ForEach(1...n, id: \.self) { Text("\($0)").font(AppFont.mono(11)).foregroundStyle(palette.muted).frame(maxWidth: .infinity) }
            }
            ForEach(rows.indices, id: \.self) { r in
                let row = rows[r]
                if resultRow && r == rows.count - 1 { Divider() }
                HStack(spacing: 0) {
                    Text(row.label).font(AppFont.mono(13, .bold)).foregroundStyle(row.labelColor ?? palette.onSurface).lineLimit(1).minimumScaleFactor(0.7)
                        .frame(width: 44, alignment: .leading)
                    ForEach(1...n, id: \.self) { id in
                        RoundedRectangle(cornerRadius: 5).fill(row.ids.contains(id) ? row.color : SimColors.tint)
                            .frame(height: 24).padding(.horizontal, 2).frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .modifier(SsStage())
    }
}
