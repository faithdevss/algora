import SwiftUI

// Port of EvalStoryLabs.kt: Davies-Bouldin, Silhouette, Hinge Loss, Gini Impurity, Adjusted R², MSE and
// R², each one figure (a clustering with a score per k, a loss curve, a node of samples or a fitted line),
// the arithmetic of the current setting, chips and a headline, then a stepper (or a picker over two) and
// one action. Every number is computed from the fixed data below.

let evalStoryTopicIds: Set<String> = [
    "davies_bouldin", "silhouette_score", "hinge_loss", "gini_impurity", "adjusted_r_squared", "mse", "r_squared",
]

private let clusterColors: [Color] = [SimColors.blue, CategoryAccents.pink, SimColors.green, Color(hex: 0xF97316), Color(hex: 0x8B5CF6), Color(hex: 0x14B8A6)]
private let ownLineColor = Color(hex: 0xB8A27A)
private let bandColor = Color(hex: 0x6B5E3C)
private let violetInk = Color(hex: 0xB4A2FF)

// MARK: - Formatting and small math

private func vx(_ v: Double, _ d: Int = 2) -> String {
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

private func ladder(_ from: Double, _ to: Double, _ step: Double) -> [Double] {
    (0...Int(((to - from) / step).rounded())).map { from + Double($0) * step }
}

private func nearest(_ values: [Double], _ v: Double) -> Int { values.indices.min { abs(values[$0] - v) < abs(values[$1] - v) }! }

private struct EvRng {
    var random: KotlinRandom
    init(_ seed: Int32) { random = KotlinRandom(seed) }
    mutating func next() -> Double { random.nextDouble() }
    mutating func normal() -> Double {
        let u = max(random.nextDouble(), 1e-12)
        return (-2 * log(u)).squareRoot() * cos(2 * Double.pi * random.nextDouble())
    }
}

private struct EvV2 { let x: Double; let y: Double }

private func dist(_ a: EvV2, _ b: EvV2) -> Double { ((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).squareRoot() }

/// Lloyd's k-means from a farthest-point start at point 0: deterministic, so both platforms agree.
private func kMeans(_ pts: [EvV2], _ k: Int) -> [Int] {
    var centres = [pts[0]]
    while centres.count < k {
        let far = pts.indices.max { a, b in centres.map { dist(pts[a], $0) }.min()! < centres.map { dist(pts[b], $0) }.min()! }!
        centres.append(pts[far])
    }
    var labels = Array(repeating: 0, count: pts.count)
    for _ in 0..<60 {
        for (i, p) in pts.enumerated() { labels[i] = centres.indices.min { dist(p, centres[$0]) < dist(p, centres[$1]) }! }
        for c in 0..<k {
            let m = pts.indices.filter { labels[$0] == c }
            if !m.isEmpty { centres[c] = EvV2(x: m.reduce(0) { $0 + pts[$1].x } / Double(m.count), y: m.reduce(0) { $0 + pts[$1].y } / Double(m.count)) }
        }
    }
    return labels
}

private func centroids(_ pts: [EvV2], _ labels: [Int], _ k: Int) -> [EvV2] {
    (0..<k).map { c in
        let m = pts.indices.filter { labels[$0] == c }
        return m.isEmpty ? EvV2(x: 0, y: 0) : EvV2(x: m.reduce(0) { $0 + pts[$1].x } / Double(m.count), y: m.reduce(0) { $0 + pts[$1].y } / Double(m.count))
    }
}

private struct EvDb { let score: Double; let worst: (Int, Int); let spread: [Double]; let gap: Double }

private func daviesBouldin(_ pts: [EvV2], _ labels: [Int], _ k: Int) -> EvDb {
    let cs = centroids(pts, labels, k)
    let s: [Double] = (0..<k).map { c in
        let d = pts.indices.filter { labels[$0] == c }.map { dist(pts[$0], cs[c]) }
        return d.reduce(0, +) / Double(max(d.count, 1))
    }
    var worst = (0, 1)
    var worstR = -1.0
    var total = 0.0
    for i in 0..<k {
        var best = -Double.greatestFiniteMagnitude
        for j in 0..<k where j != i {
            let r = (s[i] + s[j]) / max(dist(cs[i], cs[j]), 1e-9)
            if r > worstR { worstR = r; worst = (i, j) }
            best = max(best, r)
        }
        total += best
    }
    return EvDb(score: total / Double(k), worst: worst, spread: s, gap: dist(cs[worst.0], cs[worst.1]))
}

private func silhouetteOf(_ pts: [EvV2], _ labels: [Int], _ k: Int, _ i: Int) -> (Double, Double, Int) {
    func meanTo(_ c: Int) -> Double {
        let d = pts.indices.filter { labels[$0] == c && $0 != i }.map { dist(pts[i], pts[$0]) }
        return d.isEmpty ? 0 : d.reduce(0, +) / Double(d.count)
    }
    let a = meanTo(labels[i])
    let others = (0..<k).filter { $0 != labels[i] && labels.contains($0) }
    let near = others.min { meanTo($0) < meanTo($1) }!
    return (a, meanTo(near), near)
}

private func meanSilhouette(_ pts: [EvV2], _ labels: [Int], _ k: Int) -> Double {
    pts.indices.map { i in
        let (a, b, _) = silhouetteOf(pts, labels, k, i)
        return max(a, b) == 0 ? 0 : (b - a) / max(a, b)
    }.reduce(0, +) / Double(pts.count)
}

/// Gaussian elimination with partial pivoting and a hair of ridge, for the normal equations.
private func solve(_ a: [[Double]], _ b: [Double]) -> [Double] {
    let n = b.count
    var m = (0..<n).map { i in (0...n).map { j in j < n ? a[i][j] + (i == j ? 1e-9 : 0) : b[i] } }
    for c in 0..<n {
        let p = (c..<n).max { abs(m[$0][c]) < abs(m[$1][c]) }!
        m.swapAt(c, p)
        for r in 0..<n where r != c {
            let f = m[r][c] / m[c][c]
            for j in c...n { m[r][j] -= f * m[c][j] }
        }
    }
    return (0..<n).map { m[$0][n] / m[$0][$0] }
}

private func rSquaredOf(_ cols: [[Double]], _ y: [Double]) -> Double {
    let n = y.count, d = cols.count + 1
    let x = (0..<n).map { i in (0..<d).map { j in j == 0 ? 1 : cols[j - 1][i] } }
    let xtx = (0..<d).map { r in (0..<d).map { c in x.reduce(0) { $0 + $1[r] * $1[c] } } }
    let xty = (0..<d).map { r in (0..<n).reduce(0) { $0 + x[$1][r] * y[$1] } }
    let beta = solve(xtx, xty)
    let mean = y.reduce(0, +) / Double(n)
    let res = (0..<n).reduce(0.0) { acc, i in
        let e = y[i] - (0..<d).reduce(0) { $0 + beta[$1] * x[i][$1] }
        return acc + e * e
    }
    let tot = y.reduce(0) { $0 + ($1 - mean) * ($1 - mean) }
    return 1 - res / tot
}

// MARK: - Data

/// Two concentric rings: a small inner one and a wide outer one.
private let ringData: ([EvV2], [Int]) = {
    var r = EvRng(12)
    var pts: [EvV2] = []
    for _ in 0..<50 { let t = r.next() * 2 * Double.pi; let rad = 1.0 + r.normal() * 0.12; pts.append(EvV2(x: rad * cos(t), y: rad * sin(t))) }
    for _ in 0..<90 { let t = r.next() * 2 * Double.pi; let rad = 3.0 + r.normal() * 0.12; pts.append(EvV2(x: rad * cos(t), y: rad * sin(t))) }
    return (pts, (0..<140).map { $0 < 50 ? 0 : 1 })
}()

/// Three blobs: one on top, two below.
private let blobData: [EvV2] = {
    var r = EvRng(4)
    let centres = [(EvV2(x: 0, y: 2.6), EvV2(x: 0.55, y: 0.3)), (EvV2(x: -3.2, y: -1.2), EvV2(x: 0.65, y: 0.3)), (EvV2(x: 3.0, y: -1.4), EvV2(x: 0.5, y: 0.4))]
    var pts: [EvV2] = []
    for (c, s) in centres {
        for _ in 0..<22 { let x = c.x + r.normal() * s.x; pts.append(EvV2(x: x, y: c.y + r.normal() * s.y)) }
    }
    return pts
}()

private let regN = 44
private let outlierIdx: Set<Int> = [33, 36, 39, 42]

/// A straight trend with four points far above it at the right end.
private let fitData: ([Double], [Double]) = {
    var r = EvRng(9)
    let xs = (0..<regN).map { Double($0) * 0.23 }
    let ys = (0..<regN).map { i in 1.2 * xs[i] + 2 + (r.next() - 0.5) * 2.0 + (outlierIdx.contains(i) ? 14 : 0) }
    return (xs, ys)
}()

private func leastSquaresLine(_ xs: [Double], _ ys: [Double], _ w: [Double]? = nil) -> (Double, Double) {
    let ww = w ?? Array(repeating: 1.0, count: xs.count)
    let sw = ww.reduce(0, +)
    let mx = xs.indices.reduce(0) { $0 + ww[$1] * xs[$1] } / sw
    let my = xs.indices.reduce(0) { $0 + ww[$1] * ys[$1] } / sw
    let m = xs.indices.reduce(0) { $0 + ww[$1] * (xs[$1] - mx) * (ys[$1] - my) } / xs.indices.reduce(0) { $0 + ww[$1] * (xs[$1] - mx) * (xs[$1] - mx) }
    return (m, my - m * mx)
}

/// Least absolute deviations by iteratively reweighted least squares.
private let maeLine: (Double, Double) = {
    let (xs, ys) = fitData
    var line = leastSquaresLine(xs, ys)
    for _ in 0..<80 {
        let w = xs.indices.map { 1 / max(abs(ys[$0] - (line.0 * xs[$0] + line.1)), 1e-3) }
        line = leastSquaresLine(xs, ys, w)
    }
    return line
}()

// MARK: - Scenes

private struct ClusterPlot {
    let points: [EvV2]
    let labels: [Int]
    let centres: [EvV2]
    var link: (EvV2, EvV2)? = nil
    var scored: Int? = nil
    var own: EvV2? = nil
    var other: EvV2? = nil
    var bars: [Double] = []
    var selected = -1
}

private struct EvSeries { let points: [(Double, Double)]; let color: Color?; var dashed = false; var width: CGFloat = 2.5 }

private struct EvMarker { let x: Double; let y: Double; let color: Color? }

private struct EvCurve {
    let series: [EvSeries]
    let markers: [EvMarker]
    let xRange: (Double, Double)
    let yRange: (Double, Double)
    let xLabels: (String, String, String)
    let yLabels: (String, String)
    var yTitle: String? = nil
    var vline: Double? = nil
    var band: (Double, Double)? = nil
    var tiles: [Int]? = nil
    var splitAt: Int? = nil
}

private struct FitPlot { let slope: Double; let intercept: Double; let showMae: Bool; let toMean: Bool; var outlierShare: Double? = nil; var ss: (Double, Double)? = nil }

private enum EvScene {
    case cluster(ClusterPlot)
    case curve(EvCurve)
    case fit(FitPlot)
}

private struct EvFrame {
    let headline: String
    let body: String
    let scene: EvScene
    var formula: [String] = []
    var legend: [(color: Color?, style: SwatchStyle, label: String)] = []
    var chips: [LabChip] = []
}

private struct EvParam { let tab: String; let name: String; let symbol: String; let values: [Double]; let format: (Double) -> String }

/// idx: each parameter's index; sel: the picker; flag: the action's own state.
private struct EvState: Equatable { var idx: [Int]; var sel = 0; var flag = 0 }

private struct EvLab {
    let initial: EvState
    let params: [EvParam]
    let frame: (EvState) -> EvFrame
    var action: ((EvState) -> String)? = nil
    var onAction: (EvState) -> EvState = { $0 }
    /// Two parameters and the action share one row under a picker (Slope m / Intercept c).
    var paired = false
    var navReset = false
}

// MARK: - Labs

private let ks = Array(2...6)

private func daviesBouldinLab() -> EvLab {
    let (pts, truth) = ringData
    let fits = ks.map { kMeans(pts, $0) }
    let scores = ks.indices.map { daviesBouldin(pts, fits[$0], ks[$0]) }
    let best = scores.indices.min { scores[$0].score < scores[$1].score }!
    let trueDb = daviesBouldin(pts, truth, 2)
    return EvLab(
        initial: EvState(idx: [best]),
        params: [EvParam(tab: "Clusters", name: "Clusters", symbol: "k", values: ks.map(Double.init), format: { "\(Int($0.rounded()))" })],
        frame: { s in
            let i = s.idx[0], k = ks[i]
            if s.flag == 1 {
                let cs = centroids(pts, truth, 2)
                return EvFrame(
                    headline: "The true rings score DB {w:\(vx(trueDb.score))}: both centroids sit near the middle.",
                    body: "DB rewards compact, well-separated blobs. Rings are neither, so it prefers any cut into arcs.",
                    scene: .cluster(ClusterPlot(points: pts, labels: truth, centres: cs, link: (cs[0], cs[1]), bars: scores.map(\.score), selected: -1)),
                    formula: ["rings: (\(vx(trueDb.spread[0])) + \(vx(trueDb.spread[1]))) / \(vx(trueDb.gap)) = {w:\(vx(trueDb.score))}"],
                    legend: [(.white, .ring, "Centroid"), (SimColors.active, .dashedLine, "Centroid gap")])
            }
            let r = scores[i]
            let cs = centroids(pts, fits[i], k)
            let (a, b) = r.worst
            return EvFrame(
                headline: i == best ? "Lowest DB is at {w:k = \(k)}, but the data is two rings." : "At k = \(k) DB is \(vx(r.score)); the lowest is {w:k = \(ks[best])}.",
                body: "DB measures spread around centroids, and a ring's centroid sits in empty space.",
                scene: .cluster(ClusterPlot(points: pts, labels: fits[i], centres: cs, link: (cs[a], cs[b]), bars: scores.map(\.score), selected: i)),
                formula: ["worst pair: (\(vx(r.spread[a])) + \(vx(r.spread[b]))) / \(vx(r.gap)) = {v:\(vx((r.spread[a] + r.spread[b]) / r.gap))}"],
                legend: [(.white, .ring, "Centroid"), (SimColors.active, .dashedLine, "Most similar pair")])
        },
        action: { $0.flag == 0 ? "Show True Rings" : "Back to k-means" },
        onAction: { var s = $0; s.flag = 1 - s.flag; return s },
        navReset: true)
}

private let scoredPoints = [8, 30, 50, 14, 60]

private func silhouetteLab() -> EvLab {
    let pts = blobData
    let fits = ks.map { kMeans(pts, $0) }
    let means = ks.indices.map { meanSilhouette(pts, fits[$0], ks[$0]) }
    let best = means.indices.max { means[$0] < means[$1] }!
    return EvLab(
        initial: EvState(idx: [best]),
        params: [EvParam(tab: "Clusters", name: "Clusters", symbol: "k", values: ks.map(Double.init), format: { "\(Int($0.rounded()))" })],
        frame: { s in
            let i = s.idx[0], k = ks[i]
            let labels = fits[i]
            let cs = centroids(pts, labels, k)
            let p = scoredPoints[s.flag]
            let (a, b, near) = silhouetteOf(pts, labels, k, p)
            let sil = (b - a) / max(a, b)
            return EvFrame(
                headline: sil >= 0 ? "This point is {\(vx(b / max(a, 1e-9), 1))×} closer to its own cluster than to the next." : "This point sits {w:closer to another cluster}: s = \(vx(sil)).",
                body: sil >= 0 ? "It needs no labels, so it can be swept over k; it peaks at k = \(ks[best])."
                    : "A negative silhouette means k-means put it on the wrong side. The average over all points peaks at k = \(ks[best]).",
                scene: .cluster(ClusterPlot(points: pts, labels: labels, centres: [], scored: p, own: cs[labels[p]], other: cs[near], bars: means, selected: i)),
                formula: ["s = (b − a) / max(a, b) = (\(vx(b)) − \(vx(a))) / \(vx(max(a, b))) = {v:\(vx(sil))}"],
                legend: [(SimColors.active, .dot, "Scored point"), (ownLineColor, .line, "a: own cluster"), (SimColors.grey, .dashedLine, "b: nearest other")])
        },
        action: { _ in "Score Another Point" },
        onAction: { var s = $0; s.flag = (s.flag + 1) % scoredPoints.count; return s },
        navReset: true)
}

private let margins = ladder(-2, 3, 0.1)

private func logistic(_ m: Double) -> Double { log(1 + exp(-m)) / log(2) }

private func hingeLossLab() -> EvLab {
    EvLab(
        initial: EvState(idx: [nearest(margins, 0.4)]),
        params: [EvParam(tab: "Margin", name: "Margin", symbol: "y·f(x)", values: margins, format: { vx($0) })],
        frame: { s in
            let m = margins[s.idx[0]]
            let hinge = max(0, 1 - m)
            let zeroOne = m < 0 ? 1.0 : 0.0
            let xs = ladder(-2, 3, 0.05)
            let headline: String, body: String
            if m < 0 {
                headline = "This point is {w:misclassified} and costs \(vx(hinge))."
                body = "Hinge grows linearly with the violation, so one bad point can't dominate the way a squared loss would."
            } else if m < 1 {
                headline = "This point is {m:classified correctly} but still costs \(vx(hinge))."
                body = "It sits inside the margin, so hinge keeps pushing. 0-1 loss is flat here and gives no gradient."
            } else {
                headline = "This point is {m:outside the margin}, so hinge is exactly 0."
                body = "Logistic still charges \(vx(logistic(m))) and never quite reaches zero; hinge stops pushing once the margin is met."
            }
            return EvFrame(
                headline: headline, body: body,
                scene: .curve(EvCurve(
                    series: [EvSeries(points: xs.map { ($0, $0 < 0 ? 1 : 0) }, color: SimColors.grey, width: 2),
                             EvSeries(points: xs.map { ($0, logistic($0)) }, color: SimColors.blue),
                             EvSeries(points: xs.map { ($0, max(0, 1 - $0)) }, color: nil)],
                    markers: [EvMarker(x: m, y: zeroOne, color: SimColors.grey), EvMarker(x: m, y: logistic(m), color: SimColors.blue), EvMarker(x: m, y: hinge, color: SimColors.active)],
                    xRange: (-2, 3), yRange: (0, 3), xLabels: ("−2", "margin y · f(x)", "3"), yLabels: ("0", "3"),
                    yTitle: "loss", vline: m, band: (0, 1))),
                formula: ["hinge = max(0, 1 − \(vx(m))) = {v:\(vx(hinge))}   0-1 = \(Int(zeroOne))"],
                legend: [(nil, .line, "Hinge"), (SimColors.blue, .line, "Logistic"), (SimColors.grey, .line, "0-1 loss"), (bandColor, .fill, "Margin")],
                chips: [LabChip(key: "margin", value: vx(m), tint: .active), LabChip(key: "logistic", value: vx(logistic(m)), tint: .path)])
        })
}

private let shares = ladder(0, 1, 0.1)

private func entropy(_ p: Double) -> Double { p <= 0 || p >= 1 ? 0 : -(p * log(p) + (1 - p) * log(1 - p)) / log(2) }

private func gini(_ p: Double) -> Double { 1 - p * p - (1 - p) * (1 - p) }

private func giniLab() -> EvLab {
    EvLab(
        initial: EvState(idx: [3]),
        params: [EvParam(tab: "Class 1 share", name: "Class 1 share", symbol: "p", values: shares, format: { vx($0) })],
        frame: { s in
            let p = shares[s.idx[0]]
            let pink = Int((p * 10).rounded())
            let g = gini(p)
            let xs = ladder(0, 1, 0.01)
            let split = s.flag == 1 && (1...9).contains(pink)
            let curve = EvCurve(
                series: [EvSeries(points: xs.map { ($0, min($0, 1 - $0)) }, color: SimColors.grey, width: 2),
                         EvSeries(points: xs.map { ($0, entropy($0)) }, color: SimColors.blue),
                         EvSeries(points: xs.map { ($0, gini($0)) }, color: nil)],
                markers: [EvMarker(x: p, y: min(p, 1 - p), color: SimColors.grey), EvMarker(x: p, y: entropy(p), color: SimColors.blue), EvMarker(x: p, y: g, color: SimColors.active)],
                xRange: (0, 1), yRange: (0, 1), xLabels: ("0", "share of class 1 in the node", "1"), yLabels: ("0", "1"),
                yTitle: "impurity", vline: p, tiles: (0..<10).map { $0 < pink ? 1 : 0 }, splitAt: split ? pink + 1 : nil)
            let legend: [(color: Color?, style: SwatchStyle, label: String)] = [(nil, .line, "Gini"), (SimColors.blue, .line, "Entropy (bits)"), (SimColors.grey, .line, "Misclassification")]
            let chips = [LabChip(key: "entropy", value: vx(entropy(p), 3), tint: .path), LabChip(key: "misclass", value: vx(min(p, 1 - p)))]
            if split {
                // The split sends every class-1 sample and one class-0 sample left, the rest right.
                let left = pink + 1, right = 10 - left
                let gl = gini(Double(pink) / Double(left))
                let weighted = Double(left) / 10 * gl
                return EvFrame(
                    headline: "The split drops Gini from \(vx(g)) to {m:\(vx(weighted))}.",
                    body: "A tree tries every split and keeps the one with the largest drop, weighting each child by its size. The right child is pure, so it adds nothing.",
                    scene: .curve(curve),
                    formula: ["\(left)/10 × \(vx(gl)) + \(right)/10 × 0.00 = {v:\(vx(weighted))}", "gain = \(vx(g)) − \(vx(weighted)) = \(vx(g - weighted))"],
                    legend: legend, chips: chips)
            }
            if pink == 0 || pink == 10 {
                return EvFrame(
                    headline: "A pure node: Gini is {0}.",
                    body: "Every sample carries the same label, so there is nothing left to split. Both curves bottom out at the edges.",
                    scene: .curve(curve), formula: ["Gini = 1 − (\(vx(p, 1))² + \(vx(1 - p, 1))²) = {v:0.00}"], legend: legend, chips: chips)
            }
            return EvFrame(
                headline: "Two samples from this node carry {different labels} \(Int((g * 100).rounded()))% of the time.",
                body: "Gini peaks at 0.5 and entropy at 1.0. Both curve, so they reward purer splits more than misclassification does.",
                scene: .curve(curve), formula: ["Gini = 1 − (\(vx(p, 1))² + \(vx(1 - p, 1))²) = {v:\(vx(g))}"], legend: legend, chips: chips)
        },
        action: { $0.flag == 0 ? "Score a Split" : "Back to One Node" },
        onAction: { var s = $0; s.flag = 1 - s.flag; return s })
}

private let noiseMax = 20

private let adjustedCurve: [(Double, Double)] = {
    var r = EvRng(3)
    let x = (0..<regN).map { _ in r.next() }
    let y = x.map { $0 + r.normal() * 0.2 }
    let noise = (0..<noiseMax).map { _ in (0..<regN).map { _ in r.normal() } }
    return (0...noiseMax).map { q in
        let r2 = rSquaredOf([x] + Array(noise.prefix(q)), y)
        let p = q + 1
        return (r2, 1 - (1 - r2) * Double(regN - 1) / Double(regN - p - 1))
    }
}()

private func adjustedLab() -> EvLab {
    EvLab(
        initial: EvState(idx: [12]),
        params: [EvParam(tab: "Noise columns", name: "Noise columns", symbol: "p", values: (0...noiseMax).map(Double.init), format: { "\(Int($0.rounded()))" })],
        frame: { s in
            let q = s.idx[0]
            let (r2, adj) = adjustedCurve[q], (r20, adj0) = adjustedCurve[0]
            let p = q + 1
            let lo = Double(Int(adjustedCurve.map { min($0.0, $0.1) }.min()! * 20)) / 20
            let hi = Double(Int(adjustedCurve.map { max($0.0, $0.1) }.max()! * 20) + 1) / 20
            return EvFrame(
                headline: q == 0 ? "With only the real feature, R² is {\(vx(r2, 3))} and adjusted R² \(vx(adj, 3))."
                    : "\(q) column\(q == 1 ? "" : "s") of pure noise push R² {up} to \(vx(r2, 3)).",
                body: q == 0 ? "Add noise columns and watch which one notices."
                    : "Least squares can always use a new column to fit noise. Adjusted R² charges for each one, and falls to \(vx(adj, 3)).",
                scene: .curve(EvCurve(
                    series: [EvSeries(points: adjustedCurve.enumerated().map { (Double($0), $1.0) }, color: nil),
                             EvSeries(points: adjustedCurve.enumerated().map { (Double($0), $1.1) }, color: SimColors.grey, dashed: true)],
                    markers: [EvMarker(x: Double(q), y: r2, color: nil), EvMarker(x: Double(q), y: adj, color: SimColors.active)],
                    xRange: (0, Double(noiseMax)), yRange: (lo, hi), xLabels: ("0", "noise columns added", "\(noiseMax)"), yLabels: (vx(lo), vx(hi)),
                    vline: Double(q))),
                formula: ["adj = 1 − (1 − R²)(n − 1)/(n − p − 1)", "= 1 − (1 − \(vx(r2, 3))) × \(regN - 1)/\(regN - p - 1) = {v:\(vx(adj, 3))}"],
                legend: [(nil, .line, "R²"), (SimColors.grey, .dashedLine, "Adjusted R²"), (SimColors.active, .dashedLine, "Current")],
                chips: [LabChip(key: "R²", value: "\(vx(r20, 3)) → \(vx(r2, 3))", tint: .answer), LabChip(key: "adj", value: "\(vx(adj0, 3)) → \(vx(adj, 3))", tint: .active)])
        },
        navReset: true)
}

private let slopes = ladder(0, 3, 0.01)
private let intercepts = ladder(-5, 10, 0.1)
private let lsLine = leastSquaresLine(fitData.0, fitData.1)

private func lineLab(rSquared: Bool) -> EvLab {
    let start = EvState(idx: [nearest(slopes, lsLine.0), nearest(intercepts, lsLine.1)])
    return EvLab(
        initial: start,
        params: [EvParam(tab: "Slope m", name: "Slope", symbol: "m", values: slopes, format: { vx($0) }),
                 EvParam(tab: "Intercept c", name: "Intercept", symbol: "c", values: intercepts, format: { vx($0) })],
        frame: { s in
            let m = slopes[s.idx[0]], c = intercepts[s.idx[1]]
            let (xs, ys) = fitData
            let res = (0..<regN).map { ys[$0] - (m * xs[$0] + c) }
            let sq = res.reduce(0) { $0 + $1 * $1 }
            let mse = sq / Double(regN)
            let mae = res.reduce(0) { $0 + abs($1) } / Double(regN)
            let share = outlierIdx.reduce(0) { $0 + res[$1] * res[$1] } / sq
            let mean = ys.reduce(0, +) / Double(regN)
            let ssTot = ys.reduce(0) { $0 + ($1 - mean) * ($1 - mean) }
            let r2 = 1 - sq / ssTot
            if !rSquared {
                return EvFrame(
                    headline: "{w:\(outlierIdx.count) outliers}, \(Int((Double(outlierIdx.count) * 100 / Double(regN)).rounded()))% of the data, cause \(Int((share * 100).rounded()))% of the squared error.",
                    body: "Squaring makes the worst points count most, so least squares tilts toward them. The MAE fit stays with the bulk.",
                    scene: .fit(FitPlot(slope: m, intercept: c, showMae: true, toMean: false, outlierShare: share)),
                    legend: [(nil, .line, "Least squares"), (SimColors.grey, .dashedLine, "MAE fit"), (SimColors.red, .ring, "Outlier")],
                    chips: [LabChip(key: "MSE", value: vx(mse), tint: .answer), LabChip(key: "MAE", value: vx(mae))])
            }
            return EvFrame(
                headline: r2 < 0 ? "Your line is {w:worse than the mean}: R² is \(vx(r2, 3))." : "Your line removes {\(Int((r2 * 100).rounded()))%} of the spread around the mean.",
                body: r2 < 0 ? "A flat line at the mean scores 0. Anything below that fits worse than not modelling at all."
                    : "R² only compares against a flat line. A high score says you beat the mean, not that the model is useful.",
                scene: .fit(FitPlot(slope: m, intercept: c, showMae: false, toMean: true, ss: (ssTot, sq))),
                formula: ["R² = 1 − \(Int(sq.rounded())) / \(Int(ssTot.rounded())) = {v:\(vx(r2, 3))}"],
                legend: [(nil, .line, "Your line"), (SimColors.grey, .dashedLine, "Mean of y")])
        },
        action: { _ in "Least Squares" },
        onAction: { var s = $0; s.idx = start.idx; return s },
        paired: true,
        navReset: true)
}

private func evalLab(_ topicId: String) -> EvLab {
    switch topicId {
    case "silhouette_score": silhouetteLab()
    case "hinge_loss": hingeLossLab()
    case "gini_impurity": giniLab()
    case "adjusted_r_squared": adjustedLab()
    case "mse": lineLab(rSquared: false)
    case "r_squared": lineLab(rSquared: true)
    default: daviesBouldinLab()
    }
}

// MARK: - Lab

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class EvModel {
    let lab: EvLab
    var state: EvState { didSet { frame = lab.frame(state) } }
    private(set) var frame: EvFrame

    init(lab: EvLab) {
        self.lab = lab
        state = lab.initial
        frame = lab.frame(lab.initial)
    }
}

struct EvalStoryLab: View {
    @State private var model: EvModel
    @Environment(\.labDock) private var dock
    @Environment(\.palette) private var palette

    init(topicId: String) {
        _model = State(initialValue: EvModel(lab: evalLab(topicId)))
    }

    var body: some View {
        let frame = model.frame
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                switch frame.scene {
                case .cluster(let plot):
                    ClusterView(plot: plot)
                    if plot.scored != nil {
                        if !frame.formula.isEmpty { EvFormula(lines: frame.formula).padding(.top, 12) }
                        KBars(values: plot.bars, selected: plot.selected).padding(.top, 14)
                    } else {
                        KBars(values: plot.bars, selected: plot.selected).padding(.top, 14)
                        if !frame.formula.isEmpty { EvFormula(lines: frame.formula).padding(.top, 12) }
                    }
                case .curve(let curve):
                    if let tiles = curve.tiles { NodeTiles(tiles: tiles, splitAt: curve.splitAt).padding(.bottom, 12) }
                    EvCurveView(curve: curve)
                    if !frame.formula.isEmpty { EvFormula(lines: frame.formula).padding(.top, 12) }
                case .fit(let plot):
                    FitView(plot: plot)
                    if let share = plot.outlierShare { ShareBar(share: share).padding(.top, 12) }
                    if let ss = plot.ss { SsBars(ss: ss).padding(.top, 12) }
                    if !frame.formula.isEmpty { EvFormula(lines: frame.formula).padding(.top, 12) }
                }
                StoryLegendRow(items: frame.legend.map { ($0.color ?? palette.primary, $0.style, $0.label) }).padding(.top, 14)
            }
            if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                EvControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(EvControls(model: model)) }
            if model.lab.navReset { dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.state = model.lab.initial } }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct EvControls: View {
    let model: EvModel

    var body: some View {
        let lab = model.lab
        let s = model.state
        let params = lab.params.indices.map { i in
            let p = lab.params[i], at = s.idx[i]
            return LabParam(tab: p.tab, name: p.name, symbol: p.symbol, text: p.format(p.values[at]), canDecrease: at > 0, canIncrease: at < p.values.count - 1)
        }
        let selected = Binding(get: { model.state.sel }, set: { model.state.sel = $0 })
        let step: (Int, Int) -> Void = { i, d in model.state.idx[i] = min(max(model.state.idx[i] + d, 0), lab.params[i].values.count - 1) }
        VStack(spacing: 14) {
            if lab.paired, let action = lab.action {
                LabParamActionControls(params: params, selected: selected, onStep: step, action: action(s)) { model.state = lab.onAction(model.state) }
            } else {
                LabParamControls(params: params, selected: selected, onStep: step)
                if let action = lab.action {
                    LabButton(label: action(s), primary: true) { model.state = lab.onAction(model.state) }
                }
            }
        }
    }
}

// MARK: - Rendering

private struct Stage: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct EvFormula: View {
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

private func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private struct ClusterView: View {
    let plot: ClusterPlot
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let pts = plot.points
            let xLo = pts.map(\.x).min()!, xHi = pts.map(\.x).max()!, yLo = pts.map(\.y).min()!, yHi = pts.map(\.y).max()!
            let pad: CGFloat = 18
            let scale = min((size.width - 2 * pad) / CGFloat(xHi - xLo), (size.height - 2 * pad) / CGFloat(yHi - yLo))
            let cx = size.width / 2 - CGFloat((xLo + xHi) / 2) * scale
            let cy = size.height / 2 + CGFloat((yLo + yHi) / 2) * scale
            func at(_ p: EvV2) -> CGPoint { CGPoint(x: cx + CGFloat(p.x) * scale, y: cy - CGFloat(p.y) * scale) }
            if let i = plot.scored {
                if let own = plot.own { ctx.line(at(pts[i]), at(own), color: ownLineColor, width: 1.5) }
                if let other = plot.other {
                    var p = Path(); p.move(to: at(pts[i])); p.addLine(to: at(other))
                    ctx.stroke(p, with: .color(SimColors.grey), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                }
            }
            for (i, p) in pts.enumerated() { ctx.fill(circle(at(p), 3.5), with: .color(clusterColors[plot.labels[i] % clusterColors.count])) }
            if let (a, b) = plot.link {
                var p = Path(); p.move(to: at(a)); p.addLine(to: at(b))
                ctx.stroke(p, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
            }
            for c in plot.centres {
                ctx.fill(circle(at(c), 6), with: .color(palette.surface))
                ctx.stroke(circle(at(c), 6), with: .color(.white), lineWidth: 2)
            }
            if let i = plot.scored {
                ctx.fill(circle(at(pts[i]), 5), with: .color(SimColors.active))
                ctx.stroke(circle(at(pts[i]), 9), with: .color(SimColors.active), lineWidth: 2)
            }
        }
        .aspectRatio(1.6, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct KBars: View {
    let values: [Double]
    let selected: Int
    @Environment(\.palette) private var palette

    var body: some View {
        let top = values.max() ?? 1
        HStack(alignment: .bottom, spacing: 8) {
            ForEach(values.indices, id: \.self) { i in
                let on = i == selected
                VStack(spacing: 4) {
                    Text(vx(values[i])).font(AppFont.mono(14, .bold)).foregroundStyle(on ? violetInk : palette.onSurface)
                    RoundedRectangle(cornerRadius: 8).fill(on ? palette.primary : SimColors.tint)
                        .overlay { if on { RoundedRectangle(cornerRadius: 8).strokeBorder(SimColors.active, lineWidth: 2) } }
                        .frame(height: min(max(64 * CGFloat(values[i] / top), 6), 64))
                        .padding(.top, 2)
                    Text("k=\(ks[i])").font(AppFont.mono(12)).foregroundStyle(on ? StoryTone.active.ink(palette) : palette.muted)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 110, alignment: .bottom)
    }
}

private struct NodeTiles: View {
    let tiles: [Int]
    let splitAt: Int?
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Node · \(tiles.count) samples").font(AppFont.sans(14)).foregroundStyle(palette.muted)
            HStack(spacing: 5) {
                ForEach(tiles.indices, id: \.self) { i in
                    if let splitAt, i == splitAt { Color.clear.frame(width: 10, height: 26) }
                    RoundedRectangle(cornerRadius: 6).fill(tiles[i] == 1 ? CategoryAccents.pink : SimColors.blue).frame(height: 26)
                }
            }
        }
    }
}

private struct EvCurveView: View {
    let curve: EvCurve
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = curve.yTitle != nil ? 44 : 38, right = size.width - 14, top: CGFloat = 14, bottom = size.height - 26
            let (x0, x1) = curve.xRange, (y0, y1) = curve.yRange
            func px(_ x: Double) -> CGFloat { left + CGFloat((x - x0) / (x1 - x0)) * (right - left) }
            func py(_ y: Double) -> CGFloat { bottom - CGFloat((min(max(y, y0), y1) - y0) / (y1 - y0)) * (bottom - top) }
            if let (a, b) = curve.band {
                ctx.fill(Path(CGRect(x: px(a), y: top, width: px(b) - px(a), height: bottom - top)), with: .color(bandColor.opacity(0.35)))
            }
            ctx.line(CGPoint(x: left, y: top), CGPoint(x: left, y: bottom), color: palette.outline, width: 1)
            ctx.line(CGPoint(x: left, y: bottom), CGPoint(x: right, y: bottom), color: palette.outline, width: 1)
            if let v = curve.vline {
                var p = Path(); p.move(to: CGPoint(x: px(v), y: top)); p.addLine(to: CGPoint(x: px(v), y: bottom))
                ctx.stroke(p, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
            }
            for s in curve.series {
                var p = Path()
                for (i, pt) in s.points.enumerated() {
                    let c = CGPoint(x: px(pt.0), y: py(pt.1))
                    if i == 0 { p.move(to: c) } else { p.addLine(to: c) }
                }
                ctx.stroke(p, with: .color(s.color ?? palette.primary), style: StrokeStyle(lineWidth: s.width, dash: s.dashed ? [5, 4] : []))
            }
            for m in curve.markers {
                let c = CGPoint(x: px(m.x), y: py(m.y))
                ctx.fill(circle(c, 6), with: .color(m.color ?? palette.primary))
                ctx.stroke(circle(c, 6), with: .color(palette.surface), lineWidth: 1.5)
            }
            func text(_ s: String, _ at: CGPoint) { ctx.draw(Text(s).font(AppFont.mono(11)).foregroundColor(palette.muted), at: at) }
            text(curve.yLabels.1, CGPoint(x: left - 18, y: top))
            text(curve.yLabels.0, CGPoint(x: left - 18, y: bottom))
            text(curve.xLabels.0, CGPoint(x: left, y: bottom + 13))
            text(curve.xLabels.2, CGPoint(x: right, y: bottom + 13))
            text(curve.xLabels.1, CGPoint(x: (left + right) / 2, y: bottom + 13))
            if let yTitle = curve.yTitle {
                var rotated = ctx
                rotated.translateBy(x: 12, y: (top + bottom) / 2)
                rotated.rotate(by: .degrees(-90))
                rotated.draw(Text(yTitle).font(AppFont.mono(11)).foregroundColor(palette.muted), at: .zero)
            }
        }
        .aspectRatio(1.6, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct FitView: View {
    let plot: FitPlot
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let (xs, ys) = fitData
            let pad: CGFloat = 16
            let xLo = xs.min()! - 0.3, xHi = xs.max()! + 0.3
            let yLo = min(ys.min()!, 0) - 1, yHi = ys.max()! + 2
            func px(_ x: Double) -> CGFloat { pad + CGFloat((x - xLo) / (xHi - xLo)) * (size.width - 2 * pad) }
            func py(_ y: Double) -> CGFloat { size.height - pad - CGFloat((y - yLo) / (yHi - yLo)) * (size.height - 2 * pad) }
            let mean = ys.reduce(0, +) / Double(ys.count)
            func dashed(_ a: CGPoint, _ b: CGPoint, _ color: Color) {
                var p = Path(); p.move(to: a); p.addLine(to: b)
                ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            }
            if plot.toMean {
                dashed(CGPoint(x: px(xLo), y: py(mean)), CGPoint(x: px(xHi), y: py(mean)), SimColors.grey)
                for i in xs.indices { ctx.line(CGPoint(x: px(xs[i]), y: py(ys[i])), CGPoint(x: px(xs[i]), y: py(mean)), color: SimColors.grey.opacity(0.6), width: 1) }
            } else {
                for i in xs.indices {
                    let out = outlierIdx.contains(i)
                    ctx.line(CGPoint(x: px(xs[i]), y: py(ys[i])), CGPoint(x: px(xs[i]), y: py(plot.slope * xs[i] + plot.intercept)),
                             color: SimColors.red.opacity(out ? 0.9 : 0.5), width: out ? 1.5 : 1)
                }
            }
            if plot.showMae {
                let (m, c) = maeLine
                dashed(CGPoint(x: px(xLo), y: py(m * xLo + c)), CGPoint(x: px(xHi), y: py(m * xHi + c)), SimColors.grey)
            }
            ctx.line(CGPoint(x: px(xLo), y: py(plot.slope * xLo + plot.intercept)), CGPoint(x: px(xHi), y: py(plot.slope * xHi + plot.intercept)),
                     color: palette.primary, width: 3)
            for i in xs.indices {
                let c = CGPoint(x: px(xs[i]), y: py(ys[i]))
                if outlierIdx.contains(i) {
                    ctx.fill(circle(c, 5), with: .color(SimColors.red))
                    ctx.stroke(circle(c, 9), with: .color(SimColors.red), lineWidth: 2)
                } else {
                    ctx.fill(circle(c, 4.5), with: .color(SimColors.idle))
                }
            }
        }
        .aspectRatio(1.3, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct ShareBar: View {
    let share: Double
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Squared error from the \(outlierIdx.count) outliers").font(AppFont.sans(14)).foregroundStyle(palette.muted)
                Spacer()
                Text("\(Int((share * 100).rounded()))%").font(AppFont.mono(14, .bold))
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(SimColors.tint)
                    Capsule().fill(SimColors.red).frame(width: geo.size.width * CGFloat(min(max(share, 0), 1)))
                }
            }
            .frame(height: 12)
        }
    }
}

private struct SsBars: View {
    let ss: (Double, Double)
    @Environment(\.palette) private var palette

    var body: some View {
        let top = max(ss.0, ss.1)
        VStack(spacing: 10) {
            ForEach(0..<2, id: \.self) { i in
                let v = i == 0 ? ss.0 : ss.1
                HStack(spacing: 0) {
                    Text(i == 0 ? "SS total" : "SS residual").font(AppFont.sans(14)).foregroundStyle(palette.muted).frame(width: 96, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(SimColors.tint)
                            Capsule().fill(i == 0 ? SimColors.grey : palette.primary).frame(width: geo.size.width * CGFloat(min(max(v / top, 0), 1)))
                        }
                    }
                    .frame(height: 12)
                    Text(verbatim: "\(Int(v.rounded()))").font(AppFont.mono(14)).frame(width: 56, alignment: .trailing)
                }
            }
        }
    }
}
