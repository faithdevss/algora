import SwiftUI

// Port of PreprocessStoryLabs.kt: outlier detection, imputation, label and one-hot encoding, z-score and
// min-max scaling, SMOTE, chi-square selection and RFE. Each is one card of figures (a plot, a strip, a
// table or bars), the arithmetic of the current setting, chips and a headline, then a picker, a stepper
// and one action, or a step track. Every number is computed from the fixed, seeded data below.

let preprocessStoryTopicIds: Set<String> = [
    "outlier_detection", "missing_value_imputation", "label_encoding", "one_hot_encoding",
    "z_score_standardization", "min_max_normalization", "smote", "chi_square_selection", "rfe",
]

private let classPink = CategoryAccents.pink
private let incomeOrange = Color(hex: 0xF08A3C)
private let dotGrey = Color(hex: 0xCBD0DA)
private let colourDots = [Color(hex: 0xD9534F), Color(hex: 0x3F9A62), Color(hex: 0x4F7FE0), Color(hex: 0xE0B23F)]

// MARK: - Formatting and small math

private func px(_ v: Double, _ d: Int = 2) -> String {
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

private func pct(_ v: Double, _ d: Int = 1) -> String { px(v * 100, d) + "%" }

private struct PpRng {
    var random: KotlinRandom
    init(_ seed: Int32) { random = KotlinRandom(seed) }
    mutating func u() -> Double { random.nextDouble() }
    mutating func int(_ n: Int) -> Int { random.nextInt(n) }
    mutating func normal() -> Double {
        let a = max(random.nextDouble(), 1e-12)
        let b = random.nextDouble()
        return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b)
    }
}

private func ppMean(_ v: [Double]) -> Double { v.reduce(0, +) / Double(v.count) }

private func ppVar(_ v: [Double]) -> Double {
    let m = ppMean(v)
    return v.reduce(0) { $0 + ($1 - m) * ($1 - m) } / Double(v.count - 1)
}

private func ppSd(_ v: [Double]) -> Double { ppVar(v).squareRoot() }

private func ppCorr(_ a: [Double], _ b: [Double]) -> Double {
    let ma = ppMean(a), mb = ppMean(b)
    let cov = a.indices.reduce(0.0) { $0 + (a[$1] - ma) * (b[$1] - mb) }
    return cov / (a.reduce(0.0) { $0 + ($1 - ma) * ($1 - ma) } * b.reduce(0.0) { $0 + ($1 - mb) * ($1 - mb) }).squareRoot()
}

private func ppQuantile(_ v: [Double], _ q: Double) -> Double {
    let s = v.sorted()
    let at = q * Double(s.count - 1)
    let lo = Int(at)
    let hi = min(lo + 1, s.count - 1)
    return s[lo] + (at - Double(lo)) * (s[hi] - s[lo])
}

private func ppMedian(_ v: [Double]) -> Double { ppQuantile(v, 0.5) }

/// Least squares with an intercept first: Gaussian elimination on the normal equations.
private func ppLeastSquares(_ x: [[Double]], _ y: [Double]) -> [Double] {
    let d = x[0].count + 1
    func row(_ i: Int, _ j: Int) -> Double { j == 0 ? 1 : x[i][j - 1] }
    var m: [[Double]] = (0..<d).map { r in
        (0...d).map { c in
            c < d ? y.indices.reduce(0.0) { $0 + row($1, r) * row($1, c) } + (r == c ? 1e-9 : 0) : y.indices.reduce(0.0) { $0 + row($1, r) * y[$1] }
        }
    }
    for c in 0..<d {
        let p = (c..<d).max { abs(m[$0][c]) < abs(m[$1][c]) }!
        m.swapAt(c, p)
        for r in 0..<d where r != c {
            let f = m[r][c] / m[c][c]
            for j in c...d { m[r][j] -= f * m[c][j] }
        }
    }
    return (0..<d).map { m[$0][d] / m[$0][$0] }
}

private func ppPredict(_ w: [Double], _ row: [Double]) -> Double { w[0] + row.indices.reduce(0.0) { $0 + w[$1 + 1] * row[$1] } }

// MARK: - Model

private struct PpDot {
    let x: Double
    let y: Double
    let color: Color
    var r: CGFloat = 3
    var ring: Color? = nil
    var hollow = false
    var top = false
}

private struct PpSeg {
    let x0: Double, y0: Double, x1: Double, y1: Double
    let color: Color
    var dashed = false
    var width: CGFloat = 2
}

private struct PpPlot {
    let dots: [PpDot]
    let segs: [PpSeg]
    let xRange: (Double, Double)
    let yRange: (Double, Double)
    let aspect: CGFloat
    /// Category names under equal-width slots across the plot ("red=0").
    var slots: [String] = []
    var caption: String? = nil
}

private struct PpRow {
    let cells: [String]
    var ink: StoryTone? = nil
    var fill: StoryTone? = nil
    var muted = false
}

private struct PpWeight { let name: String; let value: Double; let smallest: Bool }

private enum PpBlock {
    case plot(PpPlot)
    /// The outlier strip: the clean values as a cloud, then the extremes stacked in their own panel.
    case strip(clean: [Double], jitter: [Double], extremes: Int, caught: Bool, label: String)
    case table(header: [String], rows: [PpRow], weights: [CGFloat])
    case formula([String])
    /// "Raw units ……… age 0.0%" over a bar split into age (blue) and income (orange).
    case shares([(String, Double)])
    case accuracy([(String, Double, Bool)])
    case oneHot(columns: [String], rows: [String], current: Int, dropped: Bool)
    case weights([PpWeight], top: Double)
}

private struct PpFrame {
    let headline: String
    let body: String
    let blocks: [PpBlock]
    var legend: [(Color, SwatchStyle, String)] = []
    var chips: [LabChip] = []
    var action = ""
}

private struct PpParam {
    let name: String
    let symbol: String
    let values: [Int]
    let initial: Int
}

private struct PpState: Equatable {
    var tab = 0
    var param = 0
    var flag = 0
}

private struct PpLab {
    var tabs: [String] = []
    var startTab = 0
    var param: PpParam? = nil
    var button: ((PpState) -> String)? = nil
    var onButton: (PpState) -> PpState = { $0 }
    /// A step track over the frames instead of a picker, stepper and button.
    var stepped = false
    let frames: (PpState) -> [PpFrame]

    var initial: PpState { PpState(tab: startTab, param: param?.initial ?? 0, flag: 0) }
}

// MARK: - Outlier detection

private let extreme = 4000.0

private let outlierClean: [Double] = {
    var r = PpRng(61)
    return (0..<60).map { _ in 65 + r.u() * 6 }
}()

/// Each clean value's vertical place in the strip, in [−1, 1].
private let outlierJitter: [Double] = {
    var r = PpRng(62)
    return outlierClean.map { _ in r.u() * 2 - 1 }
}()

private struct PpRules { let mean: Double, sd: Double, z: Double, zFlags: Int, upper: Double, iqrFlags: Int, madFlags: Int }

private func outlierRules(_ k: Int) -> PpRules {
    let data = outlierClean + Array(repeating: extreme, count: k)
    let mean = ppMean(data)
    let sd = ppSd(data)
    let z = (extreme - mean) / sd
    let q1 = ppQuantile(data, 0.25)
    let q3 = ppQuantile(data, 0.75)
    let upper = q3 + 1.5 * (q3 - q1)
    let med = ppMedian(data)
    let mad = ppMedian(data.map { abs($0 - med) })
    let zPrime = 0.6745 * abs(extreme - med) / mad
    return PpRules(mean: mean, sd: sd, z: z, zFlags: z > 3 ? k : 0, upper: upper, iqrFlags: extreme > upper ? k : 0, madFlags: zPrime > 3.5 ? k : 0)
}

private func outlierLab() -> PpLab {
    let ks = Array(1...12)
    let mask = ks.first { outlierRules($0).zFlags == 0 }!
    return PpLab(param: PpParam(name: "Extreme values", symbol: "k", values: ks, initial: ks.firstIndex(of: mask)!)) { s in
        let k = ks[s.param]
        let r = outlierRules(k)
        let hidden = r.zFlags == 0
        func tone(_ flags: Int) -> StoryTone { flags == k ? .done : .warn }
        let rows = [
            PpRow(cells: ["z-score", "|z| > 3", "\(r.zFlags) / \(k)"], ink: tone(r.zFlags), fill: hidden ? .warn : nil),
            PpRow(cells: ["IQR", "> \(px(r.upper, 1))", "\(r.iqrFlags) / \(k)"], ink: tone(r.iqrFlags), fill: r.iqrFlags < k ? .warn : nil),
            PpRow(cells: ["MAD", "|z′| > 3.5", "\(r.madFlags) / \(k)"], ink: tone(r.madFlags), fill: r.madFlags < k ? .warn : nil),
        ]
        let zText = hidden ? "{w:\(px(r.z))} < 3" : "{m:\(px(r.z))} > 3"
        let headline: String, body: String
        if hidden {
            headline = "With \(k) extremes, z-score {w:flags none}: they inflate σ and hide each other."
            body = "IQR and MAD use quartiles and medians, which \(k) points can't move."
        } else if k == 1 {
            headline = "One extreme: z-score {m:flags it} at z = \(px(r.z))."
            body = "Add more: each extreme inflates σ for the others, until at k = \(mask) z-score sees none."
        } else {
            headline = "With \(k) extremes, z-score still {m:flags all \(k)}."
            body = "σ is already \(px(r.sd, 0)), up from \(px(ppSd(outlierClean))). At k = \(mask) the extremes hide each other."
        }
        return [PpFrame(
            headline: headline,
            body: body,
            blocks: [
                .strip(clean: outlierClean, jitter: outlierJitter, extremes: k, caught: !hidden, label: px(extreme, 0)),
                .formula(["z = (\(px(extreme, 0)) − \(px(r.mean, 0))) / \(px(r.sd, 0)) = \(zText)"]),
                .table(header: ["rule", "cut-off", "flags"], rows: rows, weights: [1, 1.3, 0.8]),
            ],
            legend: [
                (dotGrey, .dot, "Normal"),
                hidden ? (SimColors.red, .ring, "Missed outlier") : (SimColors.green, .ring, "Flagged outlier"),
            ]
        )]
    }
}

// MARK: - Missing value imputation

private let imputeData: (x: [Double], y: [Double], u: [Double]) = {
    var r = PpRng(29)
    var x: [Double] = [], y: [Double] = []
    for _ in 0..<200 {
        let xi = 5 + 2 * r.normal()
        let noise = r.normal()
        x.append(xi)
        y.append(1.1 * xi + 0.45 * noise)
    }
    let u = (0..<200).map { _ in r.u() }
    return (x, y, u)
}()

private func imputeLab() -> PpLab {
    let rates = [10, 20, 30, 40, 50, 60]
    let d = imputeData
    return PpLab(tabs: ["Drop rows", "Mean", "Regression"], startTab: 1, param: PpParam(name: "Missing", symbol: "%", values: rates, initial: rates.firstIndex(of: 30)!)) { s in
        let rate = Double(rates[s.param]) / 100
        let missing = d.u.map { $0 < rate }
        let kept = d.x.indices.filter { !missing[$0] }
        let observedY = kept.map { d.y[$0] }
        let mean = ppMean(observedY)
        let w = ppLeastSquares(kept.map { [d.x[$0]] }, observedY)
        let meanY = d.y.indices.map { missing[$0] ? mean : d.y[$0] }
        let regY = d.y.indices.map { missing[$0] ? w[0] + w[1] * d.x[$0] : d.y[$0] }
        let strategies: [(name: String, rows: Int, variance: Double, corr: Double)] = [
            ("Complete", 200, ppVar(d.y), ppCorr(d.x, d.y)),
            ("Drop rows", kept.count, ppVar(observedY), ppCorr(kept.map { d.x[$0] }, observedY)),
            ("Mean", 200, ppVar(meanY), ppCorr(d.x, meanY)),
            ("Regression", 200, ppVar(regY), ppCorr(d.x, regY)),
        ]
        let rows = strategies.indices.map { i in
            let st = strategies[i]
            return PpRow(cells: [st.name, "\(st.rows)", px(st.variance), px(st.corr, 3)], fill: i == s.tab + 1 ? .answer : nil, muted: i == 0)
        }
        let xLo = d.x.min()! - 0.4, xHi = d.x.max()! + 0.4
        let yLo = d.y.min()! - 0.8, yHi = d.y.max()! + 0.8
        let violet = SimColors.answer
        let holes = d.x.indices.filter { missing[$0] }
        let observed = kept.map { PpDot(x: d.x[$0], y: d.y[$0], color: dotGrey, r: 2.8) }
        let dots: [PpDot], segs: [PpSeg]
        switch s.tab {
        case 0:
            dots = observed + holes.map { PpDot(x: d.x[$0], y: d.y[$0], color: SimColors.grey, r: 2.8, hollow: true) }
            segs = []
        case 1:
            dots = observed + holes.map { PpDot(x: d.x[$0], y: mean, color: violet, r: 3.2) }
            segs = [PpSeg(x0: xLo, y0: mean, x1: xHi, y1: mean, color: violet, dashed: true, width: 1.5)]
        default:
            dots = observed + holes.map { PpDot(x: d.x[$0], y: regY[$0], color: violet, r: 3.2) }
            segs = [PpSeg(x0: xLo, y0: w[0] + w[1] * xLo, x1: xHi, y1: w[0] + w[1] * xHi, color: violet, dashed: true, width: 1.5)]
        }
        let complete = strategies[0], picked = strategies[s.tab + 1]
        let headline: String, body: String
        switch s.tab {
        case 0:
            headline = "Dropping keeps {\(kept.count) of 200} rows, and variance stays near \(px(complete.variance))."
            body = "Fine when values go missing at random, as here. If missingness depends on y, the rows left are biased."
        case 1:
            headline = "Mean fill keeps all 200 rows, but variance drops from \(px(complete.variance)) to {\(px(picked.variance))}."
            body = "The \(holes.count) imputed values sit on one line, so they weaken the x–y link. Regression fill keeps it."
        default:
            headline = "Regression fill keeps all 200 rows and the x–y link: corr {\(px(picked.corr, 3))}."
            body = "Filled values sit exactly on the line, so the link is slightly overstated and variance still dips to \(px(picked.variance))."
        }
        return [PpFrame(
            headline: headline,
            body: body,
            blocks: [
                .plot(PpPlot(dots: dots, segs: segs, xRange: (xLo, xHi), yRange: (yLo, yHi), aspect: 1.95)),
                .table(header: ["strategy", "rows", "var(y)", "corr"], rows: rows, weights: [1.5, 0.8, 0.9, 0.9]),
            ],
            legend: [(dotGrey, .dot, "Observed"), s.tab == 0 ? (SimColors.grey, .ring, "Dropped") : (violet, .dot, "Imputed")]
        )]
    }
}

// MARK: - Label encoding

private let colours = ["red", "green", "blue", "yellow"]
private let colourEffects = [10.0, 2.0, 9.0, 1.0]

/// (colour, target, jitter) rows: twelve per colour, the target set by the colour and not by any order.
private let colourRows: [(c: Int, y: Double, jitter: Double)] = {
    var r = PpRng(23)
    var rows: [(c: Int, y: Double, jitter: Double)] = []
    for c in colours.indices {
        for _ in 0..<12 {
            let y = colourEffects[c] + (r.u() * 2 - 1) * 0.9
            let jitter = (r.u() - 0.5) * 0.44
            rows.append((c, y, jitter))
        }
    }
    return rows
}()

/// Code orders the Shuffle action cycles through: codes[colour].
private let codeOrders = [[0, 1, 2, 3], [1, 0, 3, 2], [2, 1, 3, 0], [2, 3, 0, 1]]

private func labelEncodingLab() -> PpLab {
    let rows = colourRows
    let means = colours.indices.map { c in ppMean(rows.filter { $0.c == c }.map(\.y)) }
    let mseOneHot = rows.reduce(0.0) { let e = $1.y - means[$1.c]; return $0 + e * e } / Double(rows.count)
    let yLo = rows.map(\.y).min()! - 0.8, yHi = rows.map(\.y).max()! + 0.8
    return PpLab(
        tabs: ["Label code", "One-hot"],
        button: { _ in "Shuffle Codes" },
        onButton: { var s = $0; s.flag = (s.flag + 1) % codeOrders.count; return s }
    ) { s in
        let codes = codeOrders[s.flag]
        let w = ppLeastSquares(rows.map { [Double(codes[$0.c])] }, rows.map(\.y))
        let mseCode = rows.reduce(0.0) { let e = $1.y - w[0] - w[1] * Double(codes[$1.c]); return $0 + e * e } / Double(rows.count)
        let bySlot = (0...3).map { slot in codes.firstIndex(of: slot)! }
        let labelTab = s.tab == 0
        let dots = rows.map { PpDot(x: Double(codes[$0.c]) + $0.jitter, y: $0.y, color: colourDots[$0.c], r: 3.2) }
        let meanSegs = colours.indices.map { c in
            PpSeg(x0: Double(codes[c]) - 0.3, y0: means[c], x1: Double(codes[c]) + 0.3, y1: means[c], color: .white, dashed: true, width: 2)
        }
        let line = PpSeg(x0: -0.35, y0: w[0] - 0.35 * w[1], x1: 3.35, y1: w[0] + 3.35 * w[1], color: SimColors.answer, width: 3)
        let c0 = colours[bySlot[0]], c1 = colours[bySlot[1]], c2 = colours[bySlot[2]]
        let between = (means[bySlot[1]] - means[bySlot[0]]) * (means[bySlot[2]] - means[bySlot[1]]) > 0
        let ratio = Int((mseCode / mseOneHot).rounded())
        let headline: String, body: String
        if !labelTab {
            headline = "One-hot gives each colour its own column, so the fit is each colour's {mean}."
            body = "MSE \(px(mseOneHot, 3)) whatever order the codes take. The \(ratio)× gap was the made-up order."
        } else if between {
            headline = "This order puts \(c1) {between} \(c0) and \(c2), and by luck the data agrees."
            body = "The line fits better, MSE \(px(mseCode)), but the order is arbitrary: the next shuffle breaks it."
        } else {
            headline = "Codes 0–3 imply \(c1) sits {between} \(c0) and \(c2). The data disagrees."
            body = "A straight line on the codes misses every colour, \(ratio)× worse than one-hot."
        }
        let sign = w[1] < 0 ? "−" : "+"
        return [PpFrame(
            headline: headline,
            body: body,
            blocks: [
                .plot(PpPlot(dots: dots, segs: labelTab ? meanSegs + [line] : meanSegs, xRange: (-0.5, 3.5), yRange: (yLo, yHi), aspect: 2.0,
                             slots: bySlot.enumerated().map { "\(colours[$1])=\($0)" })),
                .formula([labelTab ? "y = \(px(w[0])) \(sign) \(px(abs(w[1]))) × code" : "ŷ = mean of the row's colour"]),
            ],
            legend: (labelTab ? [(SimColors.answer, SwatchStyle.line, "Fit on the code")] : []) + [(Color.white, .dashedLine, "One-hot fit (per-colour mean)")],
            chips: [
                LabChip(key: "MSE code", value: px(mseCode), tint: .warn),
                LabChip(key: "MSE one-hot", value: px(mseOneHot, 3), good: true),
            ]
        )]
    }
}

// MARK: - One-hot encoding

private let oneHotRows = ["green", "green", "yellow", "red", "blue", "red"]

private func oneHotLab() -> PpLab {
    PpLab(stepped: true) { _ in
        let chips = [LabChip(key: "columns", value: "1 → 4"), LabChip(key: "drop first", value: "3 left", tint: .path)]
        let legend: [(Color, SwatchStyle, String)] = [
            (SimColors.active, .fill, "Current row"),
            (SimColors.answer, .fill, "Encoded 1"),
            (SimColors.tint, .fill, "Not yet"),
        ]
        let encode = oneHotRows.indices.map { i in
            let colour = oneHotRows[i]
            let vector = colours.map { $0 == colour ? "{1}" : "0" }.joined(separator: ", ")
            let seen = oneHotRows.prefix(i).contains(colour)
            return PpFrame(
                headline: "Row \(i + 1) is {\(colour)}, so only the \(colour) column gets a 1.",
                body: seen ? "Same colour, same vector: every \(colour) row encodes identically, whatever row it sits in."
                    : "Each colour gets its own column, so no order is implied. Dropping one column avoids a redundant fourth.",
                blocks: [.oneHot(columns: colours, rows: oneHotRows, current: i, dropped: false), .formula(["\(colour) → [\(vector)]   one 1 per row"])],
                legend: legend,
                chips: chips,
                action: i < oneHotRows.count - 1 ? "Encode Row \(i + 2)" : "Drop First Column"
            )
        }
        return encode + [PpFrame(
            headline: "Drop the {red} column: red becomes [0, 0, 0].",
            body: "Three columns still tell four colours apart. The fourth was redundant: it always equals 1 minus the other three.",
            blocks: [.oneHot(columns: colours, rows: oneHotRows, current: oneHotRows.count, dropped: true), .formula(["red → [0, 0, 0]   blue → [0, {1}, 0]"])],
            legend: legend,
            chips: chips,
            action: "Start Over"
        )]
    }
}

// MARK: - Feature scaling (z-score and min-max)

private enum Scaling: Int { case raw, minMax, zScore }

private struct PpScaler {
    let lo: [Double], hi: [Double], mu: [Double], sd: [Double]

    func apply(_ f: [Double], _ s: Scaling) -> [Double] {
        switch s {
        case .raw: return f
        case .minMax: return (0..<2).map { (f[$0] - lo[$0]) / (hi[$0] - lo[$0]) }
        case .zScore: return (0..<2).map { (f[$0] - mu[$0]) / sd[$0] }
        }
    }
}

private let scaler: PpScaler = {
    let t = FeatureScalingLab.train
    let cols = (0...1).map { i in t.map { $0.features[i] } }
    return PpScaler(lo: cols.map { $0.min()! }, hi: cols.map { $0.max()! }, mu: cols.map { ppMean($0) }, sd: cols.map { ppSd($0) })
}()

private func neighbours(_ query: [Double], _ s: Scaling, _ k: Int = 5) -> [Int] {
    let q = scaler.apply(query, s)
    let t = FeatureScalingLab.train
    return Array(t.indices.sortedBy { i -> Double in
        let p = scaler.apply(t[i].features, s)
        return (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1])
    }.prefix(k))
}

/// Upper-half test rows (clear of the axis caption) whose five nearest neighbours change once scaled.
private let scalingQueries: [Int] = Array(FeatureScalingLab.test.indices.filter { i in
    let f = FeatureScalingLab.test[i].features
    return f[1] > scaler.mu[1] && Set(neighbours(f, .raw)) != Set(neighbours(f, .minMax))
}.prefix(6))

private func scalingPlot(_ query: [Double]?, _ raw: [Int], _ scaled: [Int], _ caption: String?) -> PpPlot {
    let t = FeatureScalingLab.train
    let dots = t.map { PpDot(x: $0.features[0], y: $0.features[1], color: $0.label == 1 ? classPink : SimColors.blue, r: 2.8) }
    var segs: [PpSeg] = []
    if let q = query {
        for i in raw { segs.append(PpSeg(x0: q[0], y0: q[1], x1: t[i].features[0], y1: t[i].features[1], color: SimColors.grey, dashed: true, width: 1.2)) }
        for i in scaled { segs.append(PpSeg(x0: q[0], y0: q[1], x1: t[i].features[0], y1: t[i].features[1], color: SimColors.answer, width: 2)) }
    }
    let q = query.map { [PpDot(x: $0[0], y: $0[1], color: SimColors.active, r: 5, top: true)] } ?? []
    return PpPlot(dots: dots + q, segs: segs, xRange: (scaler.lo[0] - 2, scaler.hi[0] + 2), yRange: (scaler.lo[1] - 6000, scaler.hi[1] + 6000), aspect: 2.1, caption: caption)
}

private let scalingTabs = ["Raw", "Min-max", "Z-score"]

private func zScoreLab() -> PpLab {
    let shares = FeatureScalingLab.distanceShares.map { $0.1[0] }
    let acc = FeatureScalingLab.results.map(\.accuracy)
    let picks = [0, 3, 7, 12, 18, 25]
    return PpLab(
        tabs: scalingTabs,
        startTab: 2,
        button: { _ in "Pick Another Row" },
        onButton: { var s = $0; s.flag = (s.flag + 1) % picks.count; return s }
    ) { s in
        let f = FeatureScalingLab.test[picks[s.flag]].features
        let income = f[1]
        let formula: String
        switch s.tab {
        case 0: formula = "income: \(px(income, 0)) dollars, age: \(px(f[0], 0)) years"
        case 1: formula = "income: (\(px(income, 0)) − \(px(scaler.lo[1], 0))) / (\(px(scaler.hi[1], 0)) − \(px(scaler.lo[1], 0))) = {v:\(px(scaler.apply(f, .minMax)[1]))}"
        default: formula = "income: (\(px(income, 0)) − \(px(scaler.mu[1], 0))) / \(px(scaler.sd[1], 0)) = {v:\(px(scaler.apply(f, .zScore)[1]))}"
        }
        let incomeRaw = pct(1 - shares[0])
        let headline: String, body: String
        switch s.tab {
        case 0:
            headline = "Raw, income owns {\(incomeRaw)} of the distance."
            body = "A dollar of income counts as much as a year of age, so k-NN reads income alone. Accuracy: \(px(acc[0], 3))."
        case 1:
            headline = "Raw, income owns \(incomeRaw) of the distance. Min-max gives age {\(pct(shares[1]))}."
            body = "Accuracy moves from \(px(acc[0], 3)) to \(px(acc[1], 3)). Min-max uses the extremes, so one outlier would squeeze every other value."
        default:
            headline = "Raw, income owns {\(incomeRaw)} of the distance. Z-scored, the split is near even."
            body = "Accuracy moves from \(px(acc[0], 3)) to \(px(acc[2], 3)) with the same model."
        }
        var bars = [("Raw units", shares[0])]
        if s.tab == 1 { bars.append(("Min-max", shares[1])) }
        if s.tab == 2 { bars.append(("Z-scored", shares[2])) }
        return [PpFrame(
            headline: headline,
            body: body,
            blocks: [.plot(scalingPlot(f, [], [], nil)), .shares(bars), .formula([formula])],
            legend: [(SimColors.blue, .fill, "Age share"), (incomeOrange, .fill, "Income share")]
        )]
    }
}

private func minMaxLab() -> PpLab {
    let acc = FeatureScalingLab.results.map(\.accuracy)
    return PpLab(
        tabs: scalingTabs,
        startTab: 1,
        button: { _ in "Pick Another Query" },
        onButton: { var s = $0; s.flag = (s.flag + 1) % max(scalingQueries.count, 1); return s }
    ) { s in
        let f = FeatureScalingLab.test[s.flag < scalingQueries.count ? scalingQueries[s.flag] : 0].features
        let scaling = Scaling(rawValue: s.tab)!
        let raw = neighbours(f, .raw)
        let scaled = scaling == .raw ? [] : neighbours(f, scaling)
        let age = f[0]
        let formula: String
        switch scaling {
        case .raw: formula = "d² = Δage² + Δincome²: income is ~\(px((scaler.hi[1] - scaler.lo[1]) / (scaler.hi[0] - scaler.lo[0]), 0))× wider"
        case .minMax: formula = "age′ = (\(px(age, 0)) − \(px(scaler.lo[0], 0))) / (\(px(scaler.hi[0], 0)) − \(px(scaler.lo[0], 0))) = {v:\(px(scaler.apply(f, scaling)[0]))}"
        case .zScore: formula = "age′ = (\(px(age, 0)) − \(px(scaler.mu[0], 1))) / \(px(scaler.sd[0], 1)) = {v:\(px(scaler.apply(f, scaling)[0]))}"
        }
        let headline: String, body: String
        if scaling == .raw {
            headline = "Raw, held-out accuracy is only {\(px(acc[0], 3))}."
            body = "The five nearest neighbours are picked by income alone; age barely moves the distance."
        } else {
            headline = "Scaling lifts held-out accuracy from \(px(acc[0], 3)) to {\(px(acc[scaling.rawValue], 3))}."
            body = "In raw units income dominates the distance, so the neighbours ignore age."
        }
        let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .dot, "Query")]
            + (scaling == .raw ? [] : [(SimColors.answer, .line, "Neighbours, scaled")])
            + [(SimColors.grey, .dashedLine, "Neighbours, raw")]
        return [PpFrame(
            headline: headline,
            body: body,
            blocks: [
                .plot(scalingPlot(f, raw, scaled, "age → ↑ income")),
                .accuracy(scalingTabs.indices.map { (scalingTabs[$0], acc[$0], $0 == s.tab) }),
                .formula([formula]),
            ],
            legend: legend
        )]
    }
}

// MARK: - SMOTE

private struct PpSmote {
    let majority: [(Double, Double)]
    let minority: [(Double, Double)]
    let synthetic: [(Int, Int, Double)]
    let points: [(Double, Double)]
    let before: (Double, Double)
    let after: (Double, Double)
}

private let smoteData: PpSmote = {
    var r = PpRng(31)
    func blob(_ n: Int, _ cx: Double, _ cy: Double, _ sx: Double, _ sy: Double) -> [(Double, Double)] {
        (0..<n).map { _ in
            let x = cx + r.normal() * sx
            let y = cy + r.normal() * sy
            return (x, y)
        }
    }
    let majority = blob(60, 0, 0, 1.3, 0.9)
    let minority = blob(6, 0.9, 0.9, 0.45, 0.45)
    let testMajority = blob(60, 0, 0, 1.3, 0.9)
    let testMinority = blob(10, 0.9, 0.9, 0.45, 0.45)
    func d2(_ a: (Double, Double), _ b: (Double, Double)) -> Double { (a.0 - b.0) * (a.0 - b.0) + (a.1 - b.1) * (a.1 - b.1) }
    var g = PpRng(53)
    let synthetic: [(Int, Int, Double)] = (0..<24).map { _ in
        let o = g.int(minority.count)
        let near = Array(minority.indices.filter { $0 != o }.sortedBy { d2(minority[$0], minority[o]) }.prefix(5))
        let n = near[g.int(near.count)]
        return (o, n, g.u())
    }
    let points = synthetic.map { (o, n, lam) in
        (minority[o].0 + lam * (minority[n].0 - minority[o].0), minority[o].1 + lam * (minority[n].1 - minority[o].1))
    }
    func score(_ minorityTrain: [(Double, Double)]) -> (Double, Double) {
        let train = majority.map { ($0, 0) } + minorityTrain.map { ($0, 1) }
        let test = testMajority.map { ($0, 0) } + testMinority.map { ($0, 1) }
        var tp = 0, fp = 0
        for (p, label) in test {
            let votes = train.sortedBy { d2($0.0, p) }.prefix(3).filter { $0.1 == 1 }.count
            if votes >= 2 { if label == 1 { tp += 1 } else { fp += 1 } }
        }
        return (Double(tp) / Double(testMinority.count), tp + fp == 0 ? 0 : Double(tp) / Double(tp + fp))
    }
    return PpSmote(majority: majority, minority: minority, synthetic: synthetic, points: points, before: score(minority), after: score(minority + points))
}()

private func smoteLab() -> PpLab {
    PpLab(stepped: true) { _ in
        let d = smoteData
        let all = d.majority + d.minority + d.points
        let xr = (all.map(\.0).min()! - 0.3, all.map(\.0).max()! + 0.3)
        let yr = (all.map(\.1).min()! - 0.3, all.map(\.1).max()! + 0.3)
        let base = d.majority.map { PpDot(x: $0.0, y: $0.1, color: SimColors.blue, r: 2.8) }
        let minority = d.minority.map { PpDot(x: $0.0, y: $0.1, color: classPink, r: 4) }
        let chips = [
            LabChip(key: "recall", value: "\(px(d.before.0)) → \(px(d.after.0))", good: true),
            LabChip(key: "precision", value: "\(px(d.before.1)) → \(px(d.after.1))", tint: .warn),
        ]
        let legend: [(Color, SwatchStyle, String)] = [
            (classPink, .dot, "Minority (\(d.minority.count))"),
            (SimColors.answer, .dot, "Synthetic"),
            (SimColors.active, .dot, "Being made"),
        ]
        let intro = PpFrame(
            headline: "{\(d.minority.count)} minority points against \(d.majority.count): k-NN rarely votes minority.",
            body: "Held-out recall is only \(px(d.before.0)). Copying rows would just stack duplicates on the same spots.",
            blocks: [.plot(PpPlot(dots: base + minority, segs: [], xRange: xr, yRange: yr, aspect: 1.75)), .formula(["minority : majority = \(d.minority.count) : \(d.majority.count)"])],
            legend: Array(legend.prefix(1)),
            chips: chips,
            action: "Make a Synthetic"
        )
        let making = (0..<4).map { j -> PpFrame in
            let (o, n, lam) = d.synthetic[j]
            let origin = d.minority[o], nb = d.minority[n], p = d.points[j]
            let made = d.points.prefix(j).map { PpDot(x: $0.0, y: $0.1, color: SimColors.answer, r: 3.5) }
            return PpFrame(
                headline: "SMOTE places a new point {\(px(lam * 100, 0))%} of the way to a minority neighbour.",
                body: j == 0 ? "It fills the minority region instead of copying rows. Recall rises; some precision is the price."
                    : "Each synthetic point sits on a segment between two real minority points, never outside their span.",
                blocks: [
                    .plot(PpPlot(
                        dots: base + minority + made + [PpDot(x: origin.0, y: origin.1, color: classPink, r: 4, ring: SimColors.active), PpDot(x: p.0, y: p.1, color: SimColors.active, r: 4.5, top: true)],
                        segs: [PpSeg(x0: origin.0, y0: origin.1, x1: nb.0, y1: nb.1, color: SimColors.active, dashed: true, width: 1.2)],
                        xRange: xr, yRange: yr, aspect: 1.75
                    )),
                    .formula(["x_new = x + λ(x_nn − x), λ = {\(px(lam))}", "= (\(px(p.0)), \(px(p.1)))"]),
                ],
                legend: legend,
                chips: chips,
                action: "Next Synthetic"
            )
        }
        let done = PpFrame(
            headline: "{\(d.points.count)} synthetic points later, recall rises from \(px(d.before.0)) to \(px(d.after.0)).",
            body: "Precision moves from \(px(d.before.1)) to \(px(d.after.1)): the filled region now claims a few majority points too.",
            blocks: [
                .plot(PpPlot(dots: base + d.points.map { PpDot(x: $0.0, y: $0.1, color: SimColors.answer, r: 3.5) } + minority, segs: [], xRange: xr, yRange: yr, aspect: 1.75)),
                .formula(["minority : majority = \(d.minority.count + d.points.count) : \(d.majority.count)"]),
            ],
            legend: Array(legend.prefix(2)),
            chips: chips,
            action: "Start Over"
        )
        return [intro] + making + [done]
    }
}

// MARK: - Chi-square selection and RFE

private let featureNames = ["useful", "duplicate", "noise", "xorA", "xorB"]

/// `useful` tracks y, `duplicate` tracks useful, `noise` is unrelated, and y is really xorA ⊕ xorB.
private let selectionData: [(f: [Int], y: Int)] = {
    var r = PpRng(67)
    return (0..<400).map { _ in
        let a = r.int(2)
        let b = r.int(2)
        let y = r.u() < 0.1 ? 1 - (a ^ b) : a ^ b
        let useful = r.u() < 0.15 ? 1 - y : y
        let duplicate = r.u() < 0.06 ? 1 - useful : useful
        let noise = r.int(2)
        return ([useful, duplicate, noise, a, b], y)
    }
}()

private func contingency(_ feature: ([Int]) -> Int) -> [[Int]] {
    var c = [[0, 0], [0, 0]]
    for (f, y) in selectionData { c[feature(f)][y] += 1 }
    return c
}

private func chiSquare(_ c: [[Int]]) -> Double {
    let total = Double(c.flatMap { $0 }.reduce(0, +))
    var chi = 0.0
    for i in 0...1 {
        for j in 0...1 {
            let expected = Double(c[i].reduce(0, +)) * Double(c[0][j] + c[1][j]) / total
            if expected > 0 { chi += (Double(c[i][j]) - expected) * (Double(c[i][j]) - expected) / expected }
        }
    }
    return chi
}

private func chiSquareLab() -> PpLab {
    let scores = featureNames.indices.map { i in chiSquare(contingency { $0[i] }) }
    let order = featureNames.indices.sortedByDescending { scores[$0] }
    let pair = chiSquare(contingency { $0[3] ^ $0[4] })
    let pairAcc = Double(selectionData.filter { ($0.f[3] ^ $0.f[4]) == $0.y }.count) / Double(selectionData.count)
    let xorB = contingency { $0[4] }
    let ks = Array(1...5)
    return PpLab(
        param: PpParam(name: "Keep top", symbol: "k", values: ks, initial: 1),
        button: { $0.flag == 0 ? "Test Feature Pairs" : "Show Single Features" },
        onButton: { var s = $0; s.flag = 1 - s.flag; return s }
    ) { s in
        let k = ks[s.param]
        let kept = Set(order.prefix(k))
        let rows = order.map { i -> PpRow in
            let name = featureNames[i]
            if kept.contains(i) { return PpRow(cells: [name, px(scores[i], 1), "✓"], ink: .done, fill: .done) }
            if i >= 3 { return PpRow(cells: [name, px(scores[i], 1), "blind spot"], ink: .active, fill: .active) }
            return PpRow(cells: [name, px(scores[i], 1), "—"], muted: true)
        }
        let bothXor = kept.contains(3) && kept.contains(4)
        let headline: String, body: String
        if s.flag == 1 {
            headline = "Build the pair feature and χ² scores it {\(px(pair, 1))}, top of the table."
            body = "χ² only tests the columns you give it. An interaction has to be engineered before it can be selected."
        } else if bothXor {
            headline = "At k = \(k) both xor features survive, but only because {nearly everything} does."
            body = "χ² still ranks them beside noise. They are kept by accident, not for their signal."
        } else {
            headline = "χ² scores each feature {alone}, so xorA and xorB look like noise."
            body = "Together they predict y \(pct(pairAcc, 0)) of the time." + (kept.contains(1) ? " It also keeps the near-duplicate." : "")
        }
        let pairRow = s.flag == 1 ? [PpRow(cells: ["xorA ⊕ xorB", px(pair, 1), "pair"], ink: .answer, fill: .answer)] : []
        return [PpFrame(
            headline: headline,
            body: body,
            blocks: [
                .table(header: ["feature", "χ²", "keep"], rows: pairRow + rows, weights: [1.3, 1, 1]),
                .formula(["xorB vs y: [[\(xorB[0][0]), \(xorB[0][1])], [\(xorB[1][0]), \(xorB[1][1])]]", "xorA ⊕ xorB predicts y: {v:\(pct(pairAcc, 0))}"]),
            ],
            legend: [(SimColors.green, .fill, "Selected"), (Color(hex: 0x8A7440), .fill, "Missed pair")],
            chips: [LabChip(key: "model fits", value: "0"), LabChip(key: "k", value: "\(k)", tint: .answer)]
        )]
    }
}

private struct PpRound { let remaining: [Int]; let weights: [Double]; let mse: Double; let smallest: Int }

private let rfeRounds: [PpRound] = {
    let y = selectionData.map { Double($0.y) }
    var remaining = Array(featureNames.indices)
    var rounds: [PpRound] = []
    while true {
        let x = selectionData.map { row in remaining.map { Double(row.f[$0]) } }
        let w = ppLeastSquares(x, y)
        let mse = x.indices.reduce(0.0) { let e = y[$1] - ppPredict(w, x[$1]); return $0 + e * e } / Double(x.count)
        let weights = remaining.indices.map { abs(w[$0 + 1]) }
        let smallest = weights.indices.min { weights[$0] < weights[$1] }!
        rounds.append(PpRound(remaining: remaining, weights: weights, mse: mse, smallest: smallest))
        if remaining.count == 1 { break }
        remaining.remove(at: smallest)
    }
    return rounds
}()

private func rSquaredAlone(_ i: Int) -> Double {
    let r = ppCorr(selectionData.map { Double($0.f[i]) }, selectionData.map { Double($0.y) })
    return r * r
}

private func rfeLab() -> PpLab {
    PpLab(stepped: true) { _ in
        let rounds = rfeRounds.filter { $0.remaining.count > 1 }
        let last = rfeRounds.last!
        let top = rfeRounds.map { $0.weights.max()! }.max()!
        let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Smallest |w|: dropped"), (SimColors.blue, .fill, "Kept")]
        func bars(_ r: PpRound, _ mark: Bool) -> PpBlock {
            .weights(r.remaining.indices.map { PpWeight(name: featureNames[r.remaining[$0]], value: r.weights[$0], smallest: mark && $0 == r.smallest) }, top: top)
        }
        let first = rounds[0]
        let intro = PpFrame(
            headline: "RFE fits a linear model on all {\(first.remaining.count)} features and reads each |w|.",
            body: "The features are 0/1, so the weights are comparable. Each round drops the smallest and refits.",
            blocks: [bars(first, false), .formula(["fit 1: y ~ all \(featureNames.count) features"])],
            legend: Array(legend.dropFirst()),
            chips: [LabChip(key: "MSE", value: px(first.mse, 4), tint: .path), LabChip(key: "fits", value: "1")],
            action: "Find Smallest"
        )
        let steps = rounds.indices.map { n -> PpFrame in
            let r = rounds[n]
            let drop = r.remaining[r.smallest]
            let name = featureNames[drop]
            let alone = rSquaredAlone(drop)
            let before = rounds.prefix(n).map { featureNames[$0.remaining[$0.smallest]] }
            let headline: String, body: String
            if alone > 0.3 {
                headline = "{\(name)} is dropped though alone it explains \(pct(alone, 0)) of y."
                body = "Its near-copy \"\(name == "duplicate" ? "useful" : "duplicate")\" already carries the signal, so RFE sees it as redundant."
            } else if drop >= 3 {
                headline = "{\(name)} has the smallest |w|, so it goes."
                body = "A linear model can't express XOR, so the pair looks as empty to RFE as it does to χ²."
            } else {
                headline = "{\(name)} has the smallest |w|, so it goes."
                body = "Noise carries no signal; its weight sits near zero."
            }
            return PpFrame(
                headline: headline,
                body: body,
                blocks: [
                    bars(r, true),
                    .formula((before.isEmpty ? [] : ["dropped: \(before.joined(separator: ", "))"]) + ["round \(n + 1): drop {\(name)}, |w| = \(px(r.weights[r.smallest], 3))"]),
                ],
                legend: legend,
                chips: [LabChip(key: "MSE", value: px(r.mse, 4), tint: .path), LabChip(key: "R² alone", value: px(alone))],
                action: "Drop Smallest"
            )
        }
        let kept = featureNames[last.remaining[0]]
        let done = PpFrame(
            headline: "Only {\(kept)} is left after \(rounds.count + 1) fits.",
            body: "RFE pays one model fit per round; χ² needed none. Neither saw the xor pair, because a linear model can't express it.",
            blocks: [bars(last, false), .formula(["dropped: \(rounds.map { featureNames[$0.remaining[$0.smallest]] }.joined(separator: ", "))"])],
            legend: Array(legend.dropFirst()),
            chips: [LabChip(key: "MSE", value: px(last.mse, 4), tint: .path), LabChip(key: "fits", value: "\(rounds.count + 1)")],
            action: "Start Over"
        )
        return [intro] + steps + [done]
    }
}

private func preprocessLab(_ topicId: String) -> PpLab {
    switch topicId {
    case "missing_value_imputation": return imputeLab()
    case "label_encoding": return labelEncodingLab()
    case "one_hot_encoding": return oneHotLab()
    case "z_score_standardization": return zScoreLab()
    case "min_max_normalization": return minMaxLab()
    case "smote": return smoteLab()
    case "chi_square_selection": return chiSquareLab()
    case "rfe": return rfeLab()
    default: return outlierLab()
    }
}

// MARK: - Lab

struct PreprocessStoryLab: View {
    private let lab: PpLab
    private let topicId: String

    init(topicId: String) {
        self.topicId = topicId
        lab = preprocessLab(topicId)
    }

    var body: some View {
        if lab.stepped { PpSteppedLab(lab: lab) } else { PpInteractiveLab(lab: lab) }
    }
}

private struct PpSteppedLab: View {
    private let frames: [PpFrame]
    @State private var playback: PlaybackState

    init(lab: PpLab) {
        let frames = lab.frames(lab.initial)
        self.frames = frames
        let state = PlaybackState(stepCount: frames.count, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        let step = UserDefaults.standard.integer(forKey: "openStep")
        if step > 0 { state.index = min(step, frames.count) - 1 }
        #endif
        _playback = State(initialValue: state)
    }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            PpFrameBody(frame: frame)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) },
                              action: { frames[min(max($0, 0), frames.count - 1)].action })
        }
    }
}

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class PpModel {
    let lab: PpLab
    var state: PpState { didSet { frame = lab.frames(state)[0] } }
    private(set) var frame: PpFrame

    init(lab: PpLab) {
        self.lab = lab
        state = lab.initial
        frame = lab.frames(lab.initial)[0]
    }
}

private struct PpInteractiveLab: View {
    @State private var model: PpModel
    @Environment(\.labDock) private var dock

    init(lab: PpLab) {
        _model = State(initialValue: PpModel(lab: lab))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            PpFrameBody(frame: model.frame)
            if dock == nil {
                Divider().padding(.top, 16)
                PpControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(PpControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.state = model.lab.initial }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct PpControls: View {
    let model: PpModel

    var body: some View {
        let lab = model.lab
        let s = model.state
        VStack(spacing: 14) {
            if !lab.tabs.isEmpty {
                LabSegments(labels: lab.tabs, selected: Binding(get: { model.state.tab }, set: { model.state.tab = $0 }))
            }
            if let p = lab.param {
                LabParamStepper(param: LabParam(tab: p.name, name: p.name, symbol: p.symbol, text: "\(p.values[s.param])",
                                                canDecrease: s.param > 0, canIncrease: s.param < p.values.count - 1)) { d in
                    model.state.param = min(max(model.state.param + d, 0), p.values.count - 1)
                }
            }
            if let label = lab.button {
                LabButton(label: label(s), primary: true) { model.state = lab.onButton(model.state) }
            }
        }
    }
}

private struct PpFrameBody: View {
    let frame: PpFrame
    @Environment(\.palette) private var palette

    var body: some View {
        LabCard {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(frame.blocks.indices, id: \.self) { i in
                    switch frame.blocks[i] {
                    case .plot(let plot): PlotView(plot: plot)
                    case let .strip(clean, jitter, extremes, caught, label): StripView(clean: clean, jitter: jitter, extremes: extremes, caught: caught, label: label)
                    case let .table(header, rows, weights): TableView(header: header, rows: rows, weights: weights)
                    case .formula(let lines): FormulaView(lines: lines)
                    case .shares(let rows): ShareBarsView(rows: rows)
                    case .accuracy(let rows): AccBarsView(rows: rows)
                    case let .oneHot(columns, rows, current, dropped): OneHotView(columns: columns, rows: rows, current: current, dropped: dropped)
                    case let .weights(rows, top): WeightsView(rows: rows, top: top)
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

private struct Stage: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private struct FormulaView: View {
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

private struct PlotView: View {
    let plot: PpPlot
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            Canvas { ctx, size in
                let pad: CGFloat = 10
                let (xLo, xHi) = plot.xRange, (yLo, yHi) = plot.yRange
                func at(_ x: Double, _ y: Double) -> CGPoint {
                    CGPoint(x: pad + CGFloat((x - xLo) / (xHi - xLo)) * (size.width - 2 * pad),
                            y: size.height - pad - CGFloat((y - yLo) / (yHi - yLo)) * (size.height - 2 * pad))
                }
                func seg(_ s: PpSeg) {
                    var p = Path(); p.move(to: at(s.x0, s.y0)); p.addLine(to: at(s.x1, s.y1))
                    ctx.stroke(p, with: .color(s.color), style: StrokeStyle(lineWidth: s.width, lineCap: s.dashed ? .butt : .round, dash: s.dashed ? [5, 4] : []))
                }
                plot.segs.filter(\.dashed).forEach(seg)
                for d in plot.dots {
                    let c = at(d.x, d.y)
                    if d.hollow { ctx.stroke(circle(c, d.r), with: .color(d.color.opacity(0.7)), lineWidth: 1.2) }
                    else { ctx.fill(circle(c, d.r), with: .color(d.color)) }
                    if let ring = d.ring { ctx.stroke(circle(c, d.r + 4), with: .color(ring), lineWidth: 2) }
                }
                plot.segs.filter { !$0.dashed }.forEach(seg)
                // The highlighted dots draw last, over the lines.
                for d in plot.dots where d.top { ctx.fill(circle(at(d.x, d.y), d.r), with: .color(d.color)) }
            }
            .aspectRatio(plot.aspect, contentMode: .fit)
            .overlay(alignment: .bottomLeading) {
                if let caption = plot.caption {
                    Text(caption).font(AppFont.sans(12)).foregroundStyle(palette.muted).padding(.leading, 12).padding(.bottom, 4)
                }
            }
            if !plot.slots.isEmpty {
                HStack(spacing: 0) {
                    ForEach(plot.slots.indices, id: \.self) { i in
                        Text(plot.slots[i]).font(AppFont.mono(12)).foregroundStyle(palette.muted).lineLimit(1).frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 10).padding(.bottom, 8)
            }
        }
        .modifier(Stage())
    }
}

private struct StripView: View {
    let clean: [Double]
    let jitter: [Double]
    let extremes: Int
    let caught: Bool
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        let ring = caught ? SimColors.green : SimColors.red
        let lo = clean.min()!, hi = clean.max()!
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Canvas { ctx, size in
                    let r: CGFloat = 3.4, pad: CGFloat = 18
                    let band = size.height / 2 - 2 * r - 6
                    for (i, v) in clean.enumerated() {
                        let x = pad + CGFloat((v - lo) / (hi - lo)) * (size.width - 2 * pad)
                        ctx.fill(circle(CGPoint(x: x, y: size.height / 2 + CGFloat(jitter[i]) * band), r), with: .color(dotGrey))
                    }
                }
                .background(SimColors.tint.opacity(0.8), in: RoundedRectangle(cornerRadius: 10))
                Text("···").font(AppFont.sans(14)).foregroundStyle(palette.muted).padding(.horizontal, 8)
                Canvas { ctx, size in
                    let r: CGFloat = 7, gap: CGFloat = 3
                    let perColumn = max(1, Int((size.height - 8) / (2 * r + gap)))
                    let columns = (extremes + perColumn - 1) / perColumn
                    let width = CGFloat(columns) * 2 * r + CGFloat(columns - 1) * gap
                    let rows = min(extremes, perColumn)
                    let height = CGFloat(rows) * 2 * r + CGFloat(rows - 1) * gap
                    for i in 0..<extremes {
                        let col = i % columns, row = i / columns
                        let c = CGPoint(x: (size.width - width) / 2 + r + CGFloat(col) * (2 * r + gap), y: (size.height - height) / 2 + r + CGFloat(row) * (2 * r + gap))
                        ctx.fill(circle(c, r - 2), with: .color(classPink))
                        ctx.stroke(circle(c, r - 1), with: .color(ring), lineWidth: 2)
                    }
                }
                .frame(width: 76)
                .background(SimColors.tint.opacity(0.8), in: RoundedRectangle(cornerRadius: 10))
            }
            .frame(height: 96)
            HStack(spacing: 0) {
                Text(px(lo, 0))
                Spacer()
                Text(px(hi, 0)).padding(.trailing, 36)
                Text(label).frame(width: 76)
            }
            .font(AppFont.mono(12))
            .foregroundStyle(palette.muted)
            .padding(.top, 6).padding(.leading, 8).padding(.trailing, 4).padding(.bottom, 2)
        }
        .padding(4)
        .modifier(Stage())
    }
}

private struct TableView: View {
    let header: [String]
    let rows: [PpRow]
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
                                .font(i == 0 ? AppFont.sans(16, .semibold) : AppFont.mono(15))
                                .foregroundStyle(ink)
                                .lineLimit(1)
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
        return tone == .answer ? SimColors.answer.opacity(0.28) : tone.color.opacity(0.18)
    }
}

private struct ShareBarsView: View {
    let rows: [(String, Double)]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            ForEach(rows.indices, id: \.self) { i in
                let (label, share) = rows[i]
                VStack(spacing: 6) {
                    HStack {
                        Text(label).font(AppFont.sans(14))
                        Spacer()
                        Text("age \(pct(share))").font(AppFont.mono(13))
                    }
                    .foregroundStyle(palette.muted)
                    GeometryReader { geo in
                        HStack(spacing: 0) {
                            Rectangle().fill(SimColors.blue).frame(width: geo.size.width * CGFloat(share))
                            Rectangle().fill(incomeOrange)
                        }
                    }
                    .frame(height: 14)
                    .clipShape(RoundedRectangle(cornerRadius: 7))
                }
            }
        }
    }
}

private struct AccBarsView: View {
    let rows: [(String, Double, Bool)]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            ForEach(rows.indices, id: \.self) { i in
                let (label, value, on) = rows[i]
                HStack(spacing: 0) {
                    Text(label).font(AppFont.sans(15, on ? .semibold : .regular))
                        .foregroundStyle(on ? StoryTone.answer.ink(palette) : palette.muted)
                        .frame(width: 78, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(SimColors.tint)
                            Rectangle().fill(on ? SimColors.answer : SimColors.grey.opacity(0.45)).frame(width: geo.size.width * CGFloat(value))
                        }
                    }
                    .frame(height: 12)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                    Text(px(value, 3)).font(AppFont.mono(15)).foregroundStyle(palette.onSurface).padding(.leading, 12)
                }
            }
        }
    }
}

private struct OneHotView: View {
    let columns: [String]
    let rows: [String]
    let current: Int
    let dropped: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { geo in
            let labelWidth = (geo.size.width - CGFloat(columns.count) * 6) * 1.25 / (1.25 + CGFloat(columns.count))
            let cellWidth = (geo.size.width - CGFloat(columns.count) * 6 - labelWidth) / CGFloat(columns.count)
            VStack(spacing: 6) {
                HStack(spacing: 6) {
                    Color.clear.frame(width: labelWidth, height: 18)
                    ForEach(columns.indices, id: \.self) { c in
                        let gone = dropped && c == 0
                        Text(columns[c]).font(AppFont.sans(14)).foregroundStyle(palette.muted.opacity(gone ? 0.45 : 1))
                            .strikethrough(gone).lineLimit(1).frame(width: cellWidth)
                    }
                }
                ForEach(rows.indices, id: \.self) { r in
                    let colour = rows[r]
                    let isCurrent = r == current, done = r < current
                    HStack(spacing: 6) {
                        Text(colour).font(AppFont.sans(15, .semibold))
                            .foregroundStyle(isCurrent ? StoryTone.active.ink(palette) : done ? palette.onSurface : palette.muted.opacity(0.7))
                            .lineLimit(1)
                            .padding(.horizontal, 10)
                            .frame(width: labelWidth, height: 38, alignment: .leading)
                            .background(isCurrent ? SimColors.active.opacity(0.22) : SimColors.tint.opacity(done ? 1 : 0.6), in: RoundedRectangle(cornerRadius: 8))
                        ForEach(columns.indices, id: \.self) { c in
                            let one = columns[c] == colour
                            let gone = dropped && c == 0
                            let blank = !isCurrent && !done
                            let fill: Color = blank ? SimColors.tint.opacity(0.55) : one && isCurrent ? SimColors.active : one ? SimColors.answer : SimColors.tint
                            Text(blank ? "—" : one ? "1" : "0")
                                .font(AppFont.mono(15, one && !blank ? .bold : .regular))
                                .foregroundStyle(one && isCurrent ? Color(hex: 0x1F1A0A) : one && done ? .white : palette.muted.opacity(gone || blank ? 0.5 : 1))
                                .frame(width: cellWidth, height: 38)
                                .background(fill.opacity(gone ? 0.35 : 1), in: RoundedRectangle(cornerRadius: 8))
                        }
                    }
                }
            }
        }
        .frame(height: 18 + 6 + CGFloat(rows.count) * 44)
    }
}

private struct WeightsView: View {
    let rows: [PpWeight]
    let top: Double
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            ForEach(rows.indices, id: \.self) { i in
                let w = rows[i]
                let ink = w.smallest ? StoryTone.active.ink(palette) : palette.onSurface
                HStack(spacing: 0) {
                    Text(w.name).font(AppFont.sans(15, .semibold)).foregroundStyle(ink).lineLimit(1).frame(width: 92, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(SimColors.tint)
                            Rectangle().fill(w.smallest ? SimColors.active : SimColors.blue)
                                .frame(width: max(geo.size.width * CGFloat(min(max(w.value / top, 0), 1)), 4))
                        }
                    }
                    .frame(height: 12)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                    Text(px(w.value, 3)).font(AppFont.mono(15)).foregroundStyle(ink).frame(width: 64, alignment: .trailing)
                }
            }
        }
    }
}
