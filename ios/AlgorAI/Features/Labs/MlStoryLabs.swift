import SwiftUI

// Port of MlStoryLabs.kt: Bias-Variance, Regularization (L1 / L2), k-Nearest Neighbors and Decision
// Trees as step-by-step storyboards: a figure in the card (fitted curves over an error-by-degree strip, a
// fit over its data, a growing neighbour circle over distance tiles, or a partitioned plane over the tree
// that cuts it), a formula, a legend, then chips and a headline. The fits are real least-squares, ridge
// and lasso solutions, the tree is grown greedily on gini, and the neighbours are ranked by distance.

let mlStoryTopicIds: Set<String> = ["bias_variance", "regularization", "knn", "decision_trees"]

private let mlPink = CategoryAccents.pink

/// `color` nil is the page's text colour (the dashed "True f").
private struct MlLegend { let color: Color?; let style: SwatchStyle; let label: String }

private typealias MlPt = (Double, Double)
private typealias Labelled = (x: Double, y: Double, c: Int)

/// Bias-variance: fitted curves over the truth, and train / test error by degree underneath.
private struct FitScene {
    let truth: [MlPt]
    let fits: [[MlPt]]
    let dots: [MlPt]
    let train: [Double]
    let test: [Double]
    let marker: Int
}

/// Regularization: the data, the unpenalized fit dashed, and the penalized fit.
private struct PenaltyScene {
    let dots: [MlPt]
    let overfit: [MlPt]?
    let fit: [MlPt]
}

private enum TileTone { case done, current, pending }

private struct KnnTile { let distance: String; let label: Int?; let tone: TileTone }

private struct KnnScene {
    let query: MlPt
    let queryLabel: Int?
    let radius: Double?
    /// Neighbour indices into the points, nearest first; the last is the current one while growing.
    let neighbours: [Int]
    let growing: Bool
    let tiles: [KnnTile]
}

private enum TreeBoxTone { case pending, question, leaf0, leaf1, current }

private struct TreeBox { let title: String; let caption: String; let tone: TreeBoxTone }

private struct TreeRegion { let x0, y0, x1, y1: Double; let label: Int? }

private struct TreeScene {
    let regions: [TreeRegion]
    let splits: [(MlPt, MlPt)]
    let current: TreeRegion?
    /// Root, its yes and no children, then the yes child's yes and no children; nil is not grown yet.
    let boxes: [TreeBox?]
}

private enum MlScene {
    case fit(FitScene)
    case penalty(PenaltyScene)
    case knn(KnnScene)
    case tree(TreeScene)
}

private struct MlFrame {
    let headline: String
    let body: String
    let scene: MlScene
    var formula: String? = nil
    var legend: [MlLegend] = []
    var chips: [LabChip] = []
}

private struct MlTab { let label: String; let frames: [MlFrame] }

// MARK: - Shared math and formatting

/// Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−".
private func mlf(_ v: Double, _ d: Int = 2) -> String {
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

/// λ as written: 0, 0.0001, 0.01, 1.
private func lam(_ v: Double) -> String {
    if v == 0 { return "0" }
    var s = mlf(v, 4)
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}

/// The linear congruential generator the Android lab shares, so both draw the same samples.
private struct MlRandom {
    var state: Int64
    mutating func next() -> Double {
        state = (state &* 1103515245 &+ 12345) % 2147483648
        return Double(state) / 2147483648.0
    }
    mutating func gauss() -> Double {
        let u = max(next(), 1e-12)
        let v = next()
        return (-2 * log(u)).squareRoot() * cos(2 * Double.pi * v)
    }
}

/// Gaussian elimination with partial pivoting.
private func solve(_ a: [[Double]], _ b: [Double]) -> [Double] {
    let n = b.count
    var m = (0..<n).map { i in a[i] + [b[i]] }
    for c in 0..<n {
        var p = c
        for r in (c + 1)..<max(n, c + 1) where abs(m[r][c]) > abs(m[p][c]) { p = r }
        m.swapAt(c, p)
        for r in 0..<n where r != c {
            let k = m[r][c] / m[c][c]
            for j in c...n { m[r][j] -= k * m[c][j] }
        }
    }
    return (0..<n).map { m[$0][n] / m[$0][$0] }
}

private func features(_ x: Double, _ degree: Int) -> [Double] {
    let t = 2 * x - 1
    var out = [Double](repeating: 0, count: degree + 1)
    var p = 1.0
    for k in 0...degree { out[k] = p; p *= t }
    return out
}

/// Least squares on polynomial features, with an L2 penalty `lambda` on every weight but the intercept.
private func polyFit(_ xs: [Double], _ ys: [Double], _ degree: Int, _ lambda: Double = 0) -> [Double] {
    let n = xs.count
    let x = xs.map { features($0, degree) }
    let a = (0...degree).map { i in
        (0...degree).map { j -> Double in
            var s = 0.0
            for r in 0..<n { s += x[r][i] * x[r][j] }
            return s / Double(n) + (i == j && i > 0 ? lambda : 0) + (i == j ? 1e-10 : 0)
        }
    }
    let b = (0...degree).map { i -> Double in
        var s = 0.0
        for r in 0..<n { s += x[r][i] * ys[r] }
        return s / Double(n)
    }
    return solve(a, b)
}

/// Lasso by cyclic coordinate descent: minimizes MSE + λ‖w‖₁, the intercept unpenalized.
private func lassoFit(_ xs: [Double], _ ys: [Double], _ degree: Int, _ lambda: Double, sweeps: Int = 1500) -> [Double] {
    let n = xs.count
    let x = xs.map { features($0, degree) }
    var w = [Double](repeating: 0, count: degree + 1)
    for _ in 0..<sweeps {
        for j in 0...degree {
            var rho = 0.0, z = 0.0
            for r in 0..<n {
                var others = 0.0
                for k in 0...degree where k != j { others += w[k] * x[r][k] }
                rho += x[r][j] * (ys[r] - others)
                z += x[r][j] * x[r][j]
            }
            rho /= Double(n)
            z /= Double(n)
            w[j] = j == 0 ? rho / z : copysign(max(abs(rho) - lambda / 2, 0), rho) / z
        }
    }
    return w
}

private func predict(_ w: [Double], _ x: Double) -> Double {
    let f = features(x, w.count - 1)
    var s = 0.0
    for k in w.indices { s += w[k] * f[k] }
    return s
}

private let grid = (0...100).map { Double($0) / 100 }

private func curve(_ f: (Double) -> Double) -> [MlPt] { grid.map { ($0, f($0)) } }

private func firstMin(_ xs: [Double]) -> Int {
    var best = 0
    for i in xs.indices where xs[i] < xs[best] { best = i }
    return best
}

// MARK: - Bias-variance

private func biasTruth(_ x: Double) -> Double { 0.8 * sin(2 * Double.pi * x) + 0.3 * x }
private let biasNoise = 0.25
private let biasN = 12
private let biasSamples = 40
private let biasDegrees = [0, 3, 9]

private func biasVarianceTabs() -> [MlTab] {
    var rng = MlRandom(state: 7)
    var samples: [([Double], [Double])] = []
    for _ in 0..<biasSamples {
        var xs: [Double] = []
        for i in 0..<biasN { xs.append((Double(i) + 0.5) / Double(biasN) + (rng.next() - 0.5) * 0.6 / Double(biasN)) }
        var ys: [Double] = []
        for x in xs { ys.append(biasTruth(x) + biasNoise * rng.gauss()) }
        samples.append((xs, ys))
    }
    struct Stats { let fits: [[Double]]; let train: Double; let bias2: Double; let variance: Double; var test: Double { bias2 + variance + biasNoise * biasNoise } }
    let stats = (0...9).map { d -> Stats in
        let fits = samples.map { polyFit($0.0, $0.1, d) }
        var train = 0.0
        for s in samples.indices {
            let (xs, ys) = samples[s]
            var e = 0.0
            for i in xs.indices { let r = predict(fits[s], xs[i]) - ys[i]; e += r * r }
            train += e / Double(biasN)
        }
        train /= Double(biasSamples)
        let mean = grid.map { x in fits.reduce(0) { $0 + predict($1, x) } / Double(fits.count) }
        var bias2 = 0.0
        for i in grid.indices { let e = mean[i] - biasTruth(grid[i]); bias2 += e * e }
        bias2 /= Double(grid.count)
        var variance = 0.0
        for i in grid.indices {
            var v = 0.0
            for w in fits { let e = predict(w, grid[i]) - mean[i]; v += e * e }
            variance += v / Double(fits.count)
        }
        variance /= Double(grid.count)
        return Stats(fits: fits, train: train, bias2: bias2, variance: variance)
    }
    let train = stats.map(\.train), test = stats.map(\.test)
    let best = firstMin(test)
    let truth = curve(biasTruth)
    func scene(_ d: Int, many: Bool) -> MlScene {
        .fit(FitScene(
            truth: truth,
            fits: stats[d].fits.prefix(many ? 5 : 1).map { w in curve { predict(w, $0) } },
            dots: many ? [] : Array(zip(samples[0].0, samples[0].1)),
            train: train, test: test, marker: d
        ))
    }
    let legend = [
        MlLegend(color: nil, style: .dashed, label: "True f"),
        MlLegend(color: SimColors.blue, style: .fill, label: "Fit per sample"),
        MlLegend(color: SimColors.green, style: .fill, label: "Train error"),
        MlLegend(color: SimColors.red, style: .fill, label: "Test error"),
    ]
    func chips(_ d: Int) -> [LabChip] { [LabChip(key: "bias²", value: mlf(stats[d].bias2, 3)), LabChip(key: "variance", value: mlf(stats[d].variance, 3))] }
    let s0 = stats[0], s3 = stats[3], s9 = stats[9]
    let frames = [
        MlFrame(headline: "Degree 0 can only draw a flat line: {the mean of its sample}.",
                body: "It misses the rise and fall of f entirely, whatever sample it is given.",
                scene: scene(0, many: false), legend: legend, chips: chips(0)),
        MlFrame(headline: "Five samples, five flat lines, all {nearly the same}.",
                body: "Bias² is \(mlf(s0.bias2, 3)) and variance only \(mlf(s0.variance, 3)). The error is the model's fault, not the data's.",
                scene: scene(0, many: true), legend: legend, chips: chips(0)),
        MlFrame(headline: "Degree 3 has enough bends to {follow f}.",
                body: "Training error falls from \(mlf(s0.train, 3)) at degree 0 to \(mlf(s3.train, 3)).",
                scene: scene(3, many: false), legend: legend, chips: chips(3)),
        MlFrame(headline: "Five samples give five cubics that {agree with each other} and with f.",
                body: "Bias² \(mlf(s3.bias2, 3)), variance \(mlf(s3.variance, 3)): both small, so test error sits near its lowest.",
                scene: scene(3, many: true), legend: legend, chips: chips(3)),
        MlFrame(headline: "Degree 9 bends to every sample, so {five samples give five different curves}.",
                body: "Bias² is only \(mlf(s9.bias2, 3)) but variance is \(mlf(s9.variance, 2)). Test error climbs while training error keeps falling.",
                scene: scene(9, many: true), legend: legend, chips: chips(9)),
        MlFrame(headline: "On its own sample degree 9 looks {nearly perfect}: training error \(mlf(s9.train, 3)).",
                body: "That is the trap. Training error rewards the very capacity that test error punishes.",
                scene: scene(9, many: false), legend: legend, chips: chips(9)),
        MlFrame(headline: "The best capacity is {m:degree \(best)}, where bias² + variance is smallest.",
                body: "Too simple and bias dominates; too flexible and variance does. Test error = bias² + variance + noise.",
                scene: scene(best, many: true), legend: legend,
                chips: [LabChip(key: "best degree", value: "\(best)", tone: .done), LabChip(key: "test error", value: mlf(test[best], 3))]),
    ]
    return [MlTab(label: "", frames: frames)]
}

/// The degree a bias-variance frame is showing, for its picker.
private func biasDegreeOf(_ frame: MlFrame) -> Int {
    if case .fit(let s) = frame.scene { return s.marker }
    return 0
}

// MARK: - Regularization

private let penaltyLambdas: [Double] = [0, 0.0001, 0.001, 0.01, 1]
private let penaltyDegree = 9

private func regularizationTabs() -> [MlTab] {
    var rng = MlRandom(state: 11)
    let xs = (0..<12).map { (Double($0) + 0.5) / 12 }
    var ys: [Double] = []
    for x in xs { ys.append(0.15 + 0.7 * x + 0.07 * rng.gauss()) }
    let dots = Array(zip(xs, ys))
    func mse(_ w: [Double]) -> Double {
        var s = 0.0
        for i in xs.indices { let e = predict(w, xs[i]) - ys[i]; s += e * e }
        return s / Double(xs.count)
    }
    let exact = polyFit(xs, ys, penaltyDegree)
    let overfit = curve { predict(exact, $0) }

    func tab(l1: Bool) -> MlTab {
        let fits = penaltyLambdas.map { l in l == 0 ? exact : l1 ? lassoFit(xs, ys, penaltyDegree, l) : polyFit(xs, ys, penaltyDegree, l) }
        func norm(_ w: [Double]) -> Double {
            var s = 0.0
            for k in 1...penaltyDegree { s += l1 ? abs(w[k]) : w[k] * w[k] }
            return s
        }
        func size(_ w: [Double]) -> Double { l1 ? norm(w) : norm(w).squareRoot() }
        func zeros(_ w: [Double]) -> Int { (1...penaltyDegree).filter { abs(w[$0]) < 1e-9 }.count }
        let normName = l1 ? "‖w‖₁" : "‖w‖²"
        let sizeName = l1 ? "‖w‖₁" : "‖w‖"
        let frames = penaltyLambdas.enumerated().map { i, l -> MlFrame in
            let w = fits[i], m = mse(w), start = size(fits[0])
            let headline: String, body: String
            switch i {
            case 0:
                headline = "With λ = 0, degree 9 {threads every point}: MSE \(mlf(m, 4))."
                body = "Nothing stops the weights from growing, so \(sizeName) is \(mlf(start, 1))."
            case 1:
                headline = "A tiny penalty, λ = \(lam(l)), already shrinks \(sizeName) to {\(mlf(size(w), 2))}."
                body = l1 ? "L1 has set \(zeros(w)) of the 9 weights to exactly zero." : "The largest weights paid for the sharpest wiggles, so those shrink first."
            case 2:
                headline = "At λ = \(lam(l)) the wiggles {flatten out}, and MSE only rises to \(mlf(m, 4))."
                body = l1 ? "\(zeros(w)) of 9 weights are now exactly zero: L1 drops features instead of shrinking them." : "Every weight is smaller, none is zero. That is how L2 behaves."
            case 3:
                headline = "λ = \(lam(l)) keeps {the trend} and drops the wiggles."
                body = "Wiggles need large weights, so they are the first thing the penalty removes." + (l1 ? " Only \(penaltyDegree - zeros(w)) weights survive." : "")
            default:
                headline = "λ = \(lam(l)) {w:over-penalizes}: the fit sags toward a flat line."
                body = "MSE climbs to \(mlf(m, 4)). Too much λ is underfitting by another route."
            }
            let penalty = l * norm(w)
            var legend = [MlLegend(color: SimColors.blue, style: .dot, label: "Data")]
            if i > 0 { legend.append(MlLegend(color: SimColors.red, style: .dashed, label: "λ = 0")) }
            legend.append(MlLegend(color: SimColors.answer, style: .fill, label: "λ = \(lam(l))"))
            var chips = [LabChip(key: sizeName, value: i == 0 ? mlf(start, 1) : "\(mlf(start, 1)) → \(mlf(size(w), 2))")]
            if l1 { chips.append(LabChip(key: "zero weights", value: "\(zeros(w))/9", tone: zeros(w) > 0 ? .done : .idle)) }
            return MlFrame(
                headline: headline, body: body,
                scene: .penalty(PenaltyScene(dots: dots, overfit: i == 0 ? nil : overfit, fit: curve { predict(w, $0) })),
                formula: "loss = MSE {p:\(mlf(m, 4))} + λ {p:\(lam(l))} × \(normName) {p:\(mlf(norm(w), 2))} = {\(mlf(m + penalty, 4))}",
                legend: legend, chips: chips
            )
        }
        return MlTab(label: l1 ? "L1 · lasso" : "L2 · ridge", frames: frames)
    }
    return [tab(l1: true), tab(l1: false)]
}

// MARK: - k-nearest neighbours

private let knnPoints: [Labelled] = [
    (0.12, 0.62, 0), (0.22, 0.78, 0), (0.30, 0.70, 0), (0.33, 0.74, 0), (0.34, 0.60, 0),
    (0.40, 0.82, 0), (0.42, 0.68, 0), (0.46, 0.72, 0), (0.44, 0.58, 0), (0.28, 0.52, 0),
    (0.60, 0.40, 1), (0.64, 0.44, 1), (0.66, 0.38, 1), (0.70, 0.47, 1), (0.72, 0.30, 1),
    (0.80, 0.36, 1), (0.84, 0.46, 1), (0.86, 0.28, 1), (0.76, 0.22, 1), (0.56, 0.50, 1),
]
private let knnQuery: MlPt = (0.50, 0.55)
private let knnK = 5

private func knnTabs() -> [MlTab] {
    func dist(_ i: Int) -> Double {
        let dx = knnPoints[i].x - knnQuery.0, dy = knnPoints[i].y - knnQuery.1
        return (dx * dx + dy * dy).squareRoot()
    }
    let ranked = knnPoints.indices.sorted { dist($0) < dist($1) }
    let ordinal = ["one", "two", "three", "four", "five"]
    func votes(_ n: Int) -> [Int] { (0...1).map { c in ranked.prefix(n).filter { knnPoints[$0].c == c }.count } }
    func tiles(_ n: Int, _ growing: Bool) -> [KnnTile] {
        (0..<knnK).map { i in
            if i < n - 1 || (i == n - 1 && !growing) { return KnnTile(distance: mlf(dist(ranked[i])), label: knnPoints[ranked[i]].c, tone: .done) }
            if i == n - 1 { return KnnTile(distance: mlf(dist(ranked[i])), label: knnPoints[ranked[i]].c, tone: .current) }
            return KnnTile(distance: "—", label: nil, tone: .pending)
        }
    }
    func chips(_ n: Int) -> [LabChip] {
        let v = votes(n)
        return [LabChip(key: "k", value: "\(knnK)"), LabChip(key: "class 0", value: "\(v[0])", dot: SimColors.blue), LabChip(key: "class 1", value: "\(v[1])", dot: mlPink)]
    }
    let legend = [
        MlLegend(color: SimColors.blue, style: .fill, label: "Class 0"),
        MlLegend(color: mlPink, style: .fill, label: "Class 1"),
        MlLegend(color: SimColors.active, style: .fill, label: "Query · current"),
    ]
    var frames = [
        MlFrame(headline: "The query has no label. Its {k = \(knnK)} nearest neighbours will vote on one.",
                body: "k-NN stores the data and does nothing else. All the work happens now, at prediction time.",
                scene: .knn(KnnScene(query: knnQuery, queryLabel: nil, radius: nil, neighbours: [], growing: false, tiles: tiles(0, false))),
                legend: legend, chips: chips(0)),
    ]
    for n in 1...knnK {
        let i = ranked[n - 1], v = votes(n)
        frames.append(MlFrame(
            headline: "Neighbour \(n) is \(mlf(dist(i))) away, class \(knnPoints[i].c). {The circle grows} to reach it.",
            body: n < knnK ? "After \(ordinal[knnK - 1]) neighbours the majority class is the prediction. No model is trained at all."
                : "That makes \(ordinal[knnK - 1]). The vote stands at \(v[0]) to \(v[1]).",
            scene: .knn(KnnScene(query: knnQuery, queryLabel: nil, radius: dist(i), neighbours: Array(ranked.prefix(n)), growing: true, tiles: tiles(n, true))),
            legend: legend, chips: chips(n)
        ))
    }
    let v = votes(knnK)
    let winner = v[1] > v[0] ? 1 : 0
    frames.append(MlFrame(
        headline: "Class \(winner) wins the vote \(max(v[0], v[1])) to \(min(v[0], v[1])), so the query is {\(winner == 0 ? "p" : "w"):class \(winner)}.",
        body: "Every prediction scans the stored points. That is the price of skipping training.",
        scene: .knn(KnnScene(query: knnQuery, queryLabel: winner, radius: dist(ranked[knnK - 1]), neighbours: Array(ranked.prefix(knnK)), growing: false, tiles: tiles(knnK, false))),
        legend: Array(legend.dropLast()) + [MlLegend(color: SimColors.active, style: .fill, label: "Query")],
        chips: [LabChip(key: "prediction", value: "class \(winner)", tone: .answer)] + Array(chips(knnK).dropFirst())
    ))
    return [MlTab(label: "", frames: frames)]
}

// MARK: - Decision tree

private let treePoints: [Labelled] = [
    (0.20, 0.80, 1), (0.12, 0.68, 1), (0.30, 0.70, 1), (0.18, 0.58, 1),
    (0.10, 0.20, 0), (0.26, 0.30, 0),
    (0.45, 0.20, 0), (0.55, 0.15, 0), (0.66, 0.12, 0), (0.78, 0.18, 0), (0.90, 0.10, 0),
    (0.62, 0.75, 0), (0.75, 0.88, 0), (0.85, 0.82, 0), (0.90, 0.65, 0),
]

private func gini(_ items: [Labelled]) -> Double {
    if items.isEmpty { return 0 }
    let k = Double(items.filter { $0.c == 1 }.count) / Double(items.count)
    return 1 - k * k - (1 - k) * (1 - k)
}

private struct TreeSplit { let onX: Bool; let threshold: Double; let score: Double }

/// The threshold on either axis that lowers weighted gini the most; the first wins a tie.
private func bestSplit(_ items: [Labelled]) -> TreeSplit {
    var best: TreeSplit?
    for onX in [true, false] {
        let values = Array(Set(items.map { onX ? $0.x : $0.y })).sorted()
        for i in 0..<(values.count - 1) {
            let t = (values[i] + values[i + 1]) / 2
            let left = items.filter { (onX ? $0.x : $0.y) < t }
            let right = items.filter { (onX ? $0.x : $0.y) >= t }
            let score = (Double(left.count) * gini(left) + Double(right.count) * gini(right)) / Double(items.count)
            if best == nil || score < best!.score - 1e-12 { best = TreeSplit(onX: onX, threshold: t, score: score) }
        }
    }
    return best!
}

private func giniText(_ items: [Labelled]) -> String {
    let n = items.count, a = items.filter { $0.c == 0 }.count
    return "gini = 1 − ( {p:\(a)} /\(n))² − ( {p:\(n - a)} /\(n))² = {\(mlf(gini(items)))}"
}

private func decisionTreeTabs() -> [MlTab] {
    let pts = treePoints
    let root = bestSplit(pts)
    func side(_ t: Labelled, _ s: TreeSplit) -> Bool { (s.onX ? t.x : t.y) < s.threshold }
    let yes = pts.filter { side($0, root) }, no = pts.filter { !side($0, root) }
    let inner = bestSplit(yes)
    let yesYes = yes.filter { side($0, inner) }, yesNo = yes.filter { !side($0, inner) }
    func question(_ s: TreeSplit) -> String { "\(s.onX ? "x" : "y") < \(mlf(s.threshold))" }
    func majority(_ items: [Labelled]) -> Int { items.filter { $0.c == 1 }.count * 2 > items.count ? 1 : 0 }
    func leaf(_ items: [Labelled], current: Bool = false) -> TreeBox {
        TreeBox(title: "leaf → \(majority(items))", caption: "\(items.count) pts" + (current ? " · gini 0" : ""),
                tone: current ? .current : majority(items) == 1 ? .leaf1 : .leaf0)
    }
    let all = TreeRegion(x0: 0, y0: 0, x1: 1, y1: 1, label: nil)
    let yesBox = root.onX ? TreeRegion(x0: 0, y0: 0, x1: root.threshold, y1: 1, label: nil) : TreeRegion(x0: 0, y0: 0, x1: 1, y1: root.threshold, label: nil)
    let noBox = root.onX ? TreeRegion(x0: root.threshold, y0: 0, x1: 1, y1: 1, label: nil) : TreeRegion(x0: 0, y0: root.threshold, x1: 1, y1: 1, label: nil)
    func cut(_ r: TreeRegion, _ s: TreeSplit) -> (MlPt, MlPt) {
        s.onX ? ((s.threshold, r.y0), (s.threshold, r.y1)) : ((r.x0, s.threshold), (r.x1, s.threshold))
    }
    func part(_ r: TreeRegion, _ s: TreeSplit, lower: Bool, _ label: Int?) -> TreeRegion {
        switch (s.onX, lower) {
        case (true, true): return TreeRegion(x0: r.x0, y0: r.y0, x1: s.threshold, y1: r.y1, label: label)
        case (true, false): return TreeRegion(x0: s.threshold, y0: r.y0, x1: r.x1, y1: r.y1, label: label)
        case (false, true): return TreeRegion(x0: r.x0, y0: r.y0, x1: r.x1, y1: s.threshold, label: label)
        case (false, false): return TreeRegion(x0: r.x0, y0: s.threshold, x1: r.x1, y1: r.y1, label: label)
        }
    }
    let rootCut = cut(all, root), innerCut = cut(yesBox, inner)
    let leafYY = part(yesBox, inner, lower: true, majority(yesYes))
    let leafYN = part(yesBox, inner, lower: false, majority(yesNo))
    let leafNo = TreeRegion(x0: noBox.x0, y0: noBox.y0, x1: noBox.x1, y1: noBox.y1, label: majority(no))
    let rootQ = TreeBox(title: question(root), caption: "\(pts.count) pts", tone: .question)
    let innerQ = TreeBox(title: question(inner), caption: "\(yes.count) pts", tone: .question)
    func pending(_ items: [Labelled]) -> TreeBox { TreeBox(title: "?", caption: "\(items.count) pts", tone: .pending) }
    let legend = [
        MlLegend(color: SimColors.blue, style: .fill, label: "Class 0"),
        MlLegend(color: mlPink, style: .fill, label: "Class 1"),
        MlLegend(color: SimColors.answer, style: .fill, label: "Split"),
        MlLegend(color: SimColors.active, style: .fill, label: "Current region"),
    ]
    let ones = pts.filter { $0.c == 1 }.count
    func tree(_ regions: [TreeRegion], _ splits: [(MlPt, MlPt)], _ current: TreeRegion?, _ boxes: [TreeBox?]) -> MlScene {
        .tree(TreeScene(regions: regions, splits: splits, current: current, boxes: boxes))
    }
    let frames = [
        MlFrame(headline: "\(pts.count) points, {two classes}. Gini impurity measures how mixed they are: \(mlf(gini(pts))).",
                body: "0 would mean one class only. The tree looks for the cut that lowers it the most.",
                scene: tree([], [], all, [TreeBox(title: "root", caption: "\(pts.count) pts", tone: .current), nil, nil, nil, nil]),
                formula: giniText(pts), legend: legend),
        MlFrame(headline: "The best cut is {\(question(root))}: weighted impurity falls from \(mlf(gini(pts))) to \(mlf(root.score)).",
                body: "Every threshold on both features was tried. This one leaves the other side pure.",
                scene: tree([], [rootCut], nil, [rootQ, pending(yes), pending(no), nil, nil]),
                formula: "weighted = {p:\(yes.count)}/\(pts.count) × \(mlf(gini(yes))) + {p:\(no.count)}/\(pts.count) × \(mlf(gini(no))) = {\(mlf(root.score))}",
                legend: legend),
        MlFrame(headline: "The \(root.onX ? "left" : "lower") region still mixes {\(yes.filter { $0.c == 1 }.count) pink and \(yes.filter { $0.c == 0 }.count) blue}, so it splits again.",
                body: "Recursion: the same search, run on these \(yes.count) points only.",
                scene: tree([], [rootCut], yesBox, [rootQ, TreeBox(title: "?", caption: "\(yes.count) pts", tone: .current), pending(no), nil, nil]),
                formula: giniText(yes), legend: legend),
        MlFrame(headline: "{\(question(inner))} splits it perfectly: both sides are pure.",
                body: "Weighted impurity drops from \(mlf(gini(yes))) to \(mlf(inner.score)), the lowest it can go.",
                scene: tree([], [rootCut, innerCut], yesBox, [rootQ, innerQ, pending(no), pending(yesYes), pending(yesNo)]),
                formula: "weighted = {p:\(yesYes.count)}/\(yes.count) × \(mlf(gini(yesYes))) + {p:\(yesNo.count)}/\(yes.count) × \(mlf(gini(yesNo))) = {\(mlf(inner.score))}",
                legend: legend),
        MlFrame(headline: "Pure regions become {leaves}: \(yesYes.count) points → class \(majority(yesYes)), \(yesNo.count) points → class \(majority(yesNo)).",
                body: "A leaf predicts its majority class. Here there is no minority to overrule.",
                scene: tree([leafYY, leafYN], [rootCut, innerCut], nil, [rootQ, innerQ, pending(no), leaf(yesYes), leaf(yesNo)]),
                formula: "gini = {m:0.00} on both sides", legend: legend),
        MlFrame(headline: "The \(root.onX ? "right" : "upper") region is pure: {\(no.count) points, all class \(majority(no))}.",
                body: "So it becomes a leaf and stops splitting.",
                scene: tree([leafYY, leafYN, leafNo], [rootCut, innerCut], noBox, [rootQ, innerQ, leaf(no, current: true), leaf(yesYes), leaf(yesNo)]),
                formula: giniText(no), legend: legend),
        MlFrame(headline: "Three leaves from two questions. Any new point needs {at most two comparisons}.",
                body: "Grown deeper on noisy data, the same greedy search would carve a leaf around every point.",
                scene: tree([leafYY, leafYN, leafNo], [rootCut, innerCut], nil, [rootQ, innerQ, leaf(no), leaf(yesYes), leaf(yesNo)]),
                formula: "depth 2 · 3 leaves · {m:\(pts.count)/\(pts.count)} correct", legend: Array(legend.dropLast()),
                chips: [LabChip(key: "class 1", value: "\(ones)", dot: mlPink), LabChip(key: "class 0", value: "\(pts.count - ones)", dot: SimColors.blue)]),
    ]
    return [MlTab(label: "", frames: frames)]
}

// MARK: - Lab

private func mlStoryTabs(_ topicId: String) -> [MlTab] {
    switch topicId {
    case "bias_variance": return biasVarianceTabs()
    case "regularization": return regularizationTabs()
    case "knn": return knnTabs()
    default: return decisionTreeTabs()
    }
}

struct MlStoryLab: View {
    let topicId: String
    private let tabs: [MlTab]
    @State private var tab = 0
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        self.topicId = topicId
        let tabs = mlStoryTabs(topicId)
        self.tabs = tabs
        _playback = State(initialValue: PlaybackState(stepCount: tabs[0].frames.count, speedMs: 1000))
    }

    var body: some View {
        let frames = tabs[min(tab, tabs.count - 1)].frames
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                if tabs.count > 1 {
                    LabSegments(labels: tabs.map(\.label), selected: Binding(get: { tab }, set: { select($0) })).padding(.bottom, 14)
                }
                if topicId == "bias_variance" {
                    // The picker follows the story's degree; a tap jumps to where that degree starts.
                    let degree = biasDegreeOf(frame)
                    LabSegments(labels: biasDegrees.map { "Degree \($0)" }, selected: Binding(
                        get: { biasDegrees.firstIndex(of: degree) ?? 0 },
                        set: { i in playback.jump(to: frames.firstIndex { biasDegreeOf($0) == biasDegrees[i] } ?? 0) }
                    )).padding(.bottom, 14)
                }
                switch frame.scene {
                case .fit(let s): FitView(scene: s)
                case .penalty(let s): PenaltyView(scene: s)
                case .knn(let s): KnnView(scene: s)
                case .tree(let s): TreeView(scene: s)
                }
                if let formula = frame.formula {
                    storyText(formula, palette)
                        .font(AppFont.mono(13))
                        .foregroundStyle(palette.onSurface.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                        .lineSpacing(3)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10).padding(.horizontal, 12)
                        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
                        .padding(.top, 12)
                }
                StoryLegendRow(items: frame.legend.map { ($0.color ?? palette.onSurface, $0.style, $0.label) }).padding(.top, 14)
            }
            if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) })
        }
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        playback = PlaybackState(stepCount: tabs[i].frames.count, speedMs: 1000)
    }
}

// MARK: - Rendering

private func segment(_ a: CGPoint, _ b: CGPoint) -> Path {
    var p = Path()
    p.move(to: a)
    p.addLine(to: b)
    return p
}

private func polyline(_ points: [CGPoint]) -> Path {
    var p = Path()
    guard let first = points.first else { return p }
    p.move(to: first)
    points.dropFirst().forEach { p.addLine(to: $0) }
    return p
}

private func dot(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)) }

private extension View {
    func stage() -> some View {
        background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 12)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct FitView: View {
    let scene: FitScene
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            Canvas { ctx, size in
                let yLo = -1.6, yHi = 1.6
                func at(_ p: MlPt) -> CGPoint { CGPoint(x: CGFloat(p.0) * size.width, y: CGFloat((yHi - p.1) / (yHi - yLo)) * size.height) }
                ctx.stroke(segment(at((0, 0)), at((1, 0))), with: .color(palette.muted.opacity(0.3)), lineWidth: 1)
                for fit in scene.fits {
                    ctx.stroke(polyline(fit.map(at)), with: .color(SimColors.blue), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
                ctx.stroke(polyline(scene.truth.map(at)), with: .color(palette.onSurface), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [5, 4]))
                for p in scene.dots {
                    ctx.fill(dot(at(p), 4.5), with: .color(SimColors.blue))
                    ctx.stroke(dot(at(p), 4.5), with: .color(.white.opacity(0.8)), lineWidth: 1)
                }
            }
            .frame(height: 200)
            .stage()
            Canvas { ctx, size in
                ctx.draw(Text("error by degree · log scale").font(AppFont.sans(12)).foregroundColor(palette.muted), at: CGPoint(x: 0, y: 8), anchor: .leading)
                let top: CGFloat = 22, bottom = size.height - 18
                let lo = log10(0.005), hi = log10(2.0)
                func px(_ d: Int) -> CGFloat { 8 + CGFloat(d) * (size.width - 16) / 9 }
                func py(_ e: Double) -> CGFloat { bottom - CGFloat((log10(min(max(e, 0.005), 2)) - lo) / (hi - lo)) * (bottom - top) }
                ctx.stroke(segment(CGPoint(x: 0, y: bottom), CGPoint(x: size.width, y: bottom)), with: .color(palette.muted.opacity(0.35)), lineWidth: 1)
                ctx.stroke(polyline(scene.train.indices.map { CGPoint(x: px($0), y: py(scene.train[$0])) }), with: .color(SimColors.green),
                           style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                ctx.stroke(polyline(scene.test.indices.map { CGPoint(x: px($0), y: py(scene.test[$0])) }), with: .color(SimColors.red),
                           style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                ctx.stroke(segment(CGPoint(x: px(scene.marker), y: top - 4), CGPoint(x: px(scene.marker), y: bottom)),
                           with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                for d in 0...9 where d % 3 == 0 || d == scene.marker {
                    ctx.draw(Text("\(d)").font(AppFont.mono(11)).foregroundColor(d == scene.marker ? StoryTone.active.ink(palette) : palette.muted),
                             at: CGPoint(x: px(d), y: bottom + 10))
                }
            }
            .frame(height: 96)
        }
    }
}

private struct PenaltyView: View {
    let scene: PenaltyScene
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let yLo = -0.1, yHi = 1.1, pad: CGFloat = 10
            func at(_ p: MlPt) -> CGPoint { CGPoint(x: pad + CGFloat(p.0) * (size.width - 2 * pad), y: CGFloat((yHi - p.1) / (yHi - yLo)) * size.height) }
            ctx.stroke(segment(CGPoint(x: 0, y: size.height / 2), CGPoint(x: size.width, y: size.height / 2)), with: .color(palette.muted.opacity(0.3)), lineWidth: 1)
            if let overfit = scene.overfit {
                ctx.stroke(polyline(overfit.map(at)), with: .color(SimColors.red), style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: [4, 3]))
            }
            ctx.stroke(polyline(scene.fit.map(at)), with: .color(SimColors.answer), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
            for p in scene.dots {
                ctx.fill(dot(at(p), 6), with: .color(SimColors.blue))
                ctx.stroke(dot(at(p), 6), with: .color(palette.surface), lineWidth: 1.5)
            }
        }
        .frame(height: 230)
        .stage()
    }
}

private struct KnnView: View {
    let scene: KnnScene
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 12) {
            Canvas { ctx, size in
                // Equal scale on both axes, so the neighbour circle stays round.
                let unit = size.height / 0.8
                func at(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: size.width / 2 + CGFloat(x - 0.5) * unit, y: size.height / 2 - CGFloat(y - 0.5) * unit) }
                let q = at(scene.query.0, scene.query.1)
                if let r = scene.radius {
                    let rp = CGFloat(r) * unit
                    ctx.fill(dot(q, rp), with: .color(.white.opacity(0.05)))
                    ctx.stroke(dot(q, rp), with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                }
                for i in scene.neighbours {
                    ctx.stroke(segment(q, at(knnPoints[i].x, knnPoints[i].y)), with: .color(palette.muted.opacity(0.6)), lineWidth: 1.5)
                }
                for p in knnPoints { ctx.fill(dot(at(p.x, p.y), 6), with: .color(p.c == 1 ? mlPink : SimColors.blue)) }
                for (n, i) in scene.neighbours.enumerated() {
                    let current = scene.growing && n == scene.neighbours.count - 1
                    ctx.stroke(dot(at(knnPoints[i].x, knnPoints[i].y), 10), with: .color(current ? SimColors.active : palette.muted.opacity(0.85)), lineWidth: 2)
                }
                var diamond = Path()
                diamond.move(to: CGPoint(x: q.x, y: q.y - 8)); diamond.addLine(to: CGPoint(x: q.x + 8, y: q.y))
                diamond.addLine(to: CGPoint(x: q.x, y: q.y + 8)); diamond.addLine(to: CGPoint(x: q.x - 8, y: q.y)); diamond.closeSubpath()
                let fill = scene.queryLabel == 0 ? SimColors.blue : scene.queryLabel == 1 ? mlPink : SimColors.active
                ctx.fill(diamond, with: .color(fill))
                ctx.stroke(diamond, with: .color(scene.queryLabel == nil ? palette.surface : SimColors.active), lineWidth: 1.5)
            }
            .frame(height: 230)
            .stage()
            HStack(spacing: 6) {
                ForEach(scene.tiles.indices, id: \.self) { i in KnnTileView(rank: i + 1, tile: scene.tiles[i]) }
            }
        }
    }
}

private struct KnnTileView: View {
    let rank: Int
    let tile: KnnTile
    @Environment(\.palette) private var palette

    var body: some View {
        let (bg, ink): (Color, Color) = switch tile.tone {
        case .current: (SimColors.active, Color(hex: 0x1F1A0A))
        case .done: (palette.muted.opacity(0.16), palette.onSurface)
        case .pending: (.clear, palette.muted.opacity(0.6))
        }
        VStack(spacing: 2) {
            Text("#\(rank)").font(AppFont.mono(11)).foregroundStyle(ink.opacity(0.8))
            Text(tile.distance).font(AppFont.mono(15, .bold)).foregroundStyle(ink)
            Circle().fill(tile.label == 0 ? SimColors.blue : tile.label == 1 ? mlPink : palette.muted.opacity(0.4))
                .frame(width: 7, height: 7).padding(.top, 3)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 66)
        .background(bg, in: RoundedRectangle(cornerRadius: 10))
        .overlay {
            if tile.tone == .pending {
                RoundedRectangle(cornerRadius: 10).stroke(palette.muted.opacity(0.4), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
            }
        }
    }
}

private struct TreeView: View {
    let scene: TreeScene
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 12) {
            Canvas { ctx, size in
                func at(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: CGFloat(x) * size.width, y: CGFloat(1 - y) * size.height) }
                func rect(_ r: TreeRegion) -> CGRect {
                    let a = at(r.x0, r.y1), b = at(r.x1, r.y0)
                    return CGRect(x: a.x, y: a.y, width: b.x - a.x, height: b.y - a.y)
                }
                for r in scene.regions {
                    ctx.fill(Path(rect(r)), with: .color((r.label == 1 ? mlPink : SimColors.blue).opacity(0.16)))
                }
                for (a, b) in scene.splits {
                    ctx.stroke(segment(at(a.0, a.1), at(b.0, b.1)), with: .color(SimColors.answer), lineWidth: 2.5)
                }
                if let r = scene.current {
                    ctx.stroke(Path(roundedRect: rect(r).insetBy(dx: 2, dy: 2), cornerRadius: 10), with: .color(SimColors.active), lineWidth: 2.5)
                }
                for p in treePoints {
                    ctx.fill(dot(at(p.x, p.y), 6), with: .color(p.c == 1 ? mlPink : SimColors.blue))
                    ctx.stroke(dot(at(p.x, p.y), 6), with: .color(palette.surface), lineWidth: 1.5)
                }
            }
            .frame(height: 200)
            .stage()
            Canvas { ctx, size in
                let bh: CGFloat = 42
                let w = min(118, size.width * 0.3)
                let centres = [
                    CGPoint(x: size.width * 0.5, y: bh / 2),
                    CGPoint(x: size.width * 0.33, y: bh / 2 + 66),
                    CGPoint(x: size.width * 0.78, y: bh / 2 + 66),
                    CGPoint(x: size.width * 0.17, y: bh / 2 + 132),
                    CGPoint(x: size.width * 0.5, y: bh / 2 + 132),
                ]
                for (from, to, text) in [(0, 1, "yes"), (0, 2, "no"), (1, 3, "yes"), (1, 4, "no")] where scene.boxes[to] != nil {
                    let a = CGPoint(x: centres[from].x, y: centres[from].y + bh / 2)
                    let b = CGPoint(x: centres[to].x, y: centres[to].y - bh / 2)
                    ctx.stroke(segment(a, b), with: .color(palette.muted.opacity(0.5)), lineWidth: 1.5)
                    ctx.draw(Text(text).font(AppFont.mono(10)).foregroundColor(palette.muted),
                             at: CGPoint(x: (a.x + b.x) / 2 + (to % 2 == 1 ? -14 : 14), y: (a.y + b.y) / 2 - 4))
                }
                let pinkInk = palette.dark ? Color(hex: 0xF9A8D4) : Color(hex: 0xBE185D)
                for (i, box) in scene.boxes.enumerated() {
                    guard let box else { continue }
                    let c = centres[i]
                    let r = CGRect(x: c.x - w / 2, y: c.y - bh / 2, width: w, height: bh)
                    let (fill, border, ink): (Color, Color, Color) = switch box.tone {
                    case .current: (SimColors.active, SimColors.active, Color(hex: 0x1F1A0A))
                    case .leaf1: (mlPink.opacity(0.18), mlPink, pinkInk)
                    case .leaf0: (SimColors.blue.opacity(0.18), SimColors.blue, StoryTone.path.ink(palette))
                    case .question: (SimColors.answer.opacity(0.12), SimColors.answer.opacity(0.7), palette.onSurface)
                    case .pending: (.clear, palette.muted.opacity(0.45), palette.muted)
                    }
                    let shape = Path(roundedRect: r, cornerRadius: 8)
                    ctx.fill(shape, with: .color(fill))
                    ctx.stroke(shape, with: .color(border), style: StrokeStyle(lineWidth: 1.5, dash: box.tone == .pending ? [4, 3] : []))
                    ctx.draw(Text(box.title).font(AppFont.mono(12, .bold)).foregroundColor(ink), at: CGPoint(x: c.x, y: c.y - 8))
                    ctx.draw(Text(box.caption).font(AppFont.mono(10)).foregroundColor(ink.opacity(0.8)), at: CGPoint(x: c.x, y: c.y + 9))
                }
            }
            .frame(height: 176)
        }
    }
}
