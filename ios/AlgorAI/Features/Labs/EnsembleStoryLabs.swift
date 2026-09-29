import SwiftUI

// Port of EnsembleStoryLabs.kt: Random Forests, Gradient Boosting, Extra Trees, Bagging, Voting, Stacking,
// XGBoost, AdaBoost, LightGBM and Isolation Forest as step-by-step storyboards: one figure in the card (a
// plane, a fitted step curve, a tree or a fold grid), the step's arithmetic under it, chips and a
// headline, then the lab's controls: a step track with a labelled next action, a parameter stepper
// beside it, or a vote picker. Every number is computed from the small fixed data below.

let ensembleStoryTopicIds: Set<String> = [
    "random_forest", "gradient_boosting", "extra_trees", "bagging", "voting",
    "stacking", "xgboost", "adaboost", "lightgbm", "isolation_forest",
]

private let c0 = SimColors.blue
private let c1 = CategoryAccents.pink
private let mutedGrey = Color(hex: 0x6B7280)
private let pinkInk = Color(hex: 0xF472B6)

// MARK: - Scenes

private enum Mark { case normal, faded, hollow, grey }

private struct PPoint {
    let x: Double, y: Double, cls: Int
    var mark: Mark = .normal
    var scale: CGFloat = 1
    var ring: Color? = nil
    var tag: String? = nil
}

/// A segment in data units; a nil colour is the accent.
private struct PLine {
    let x0: Double, y0: Double, x1: Double, y1: Double
    var color: Color? = nil
    var width: CGFloat = 2.5
    var dash = false
}

private struct PRect { let x0, y0, x1, y1: Double; let cls: Int }

private struct Bounds { let x0, x1, y0, y1: Double }

private struct VoteTile { let label: String; let value: Int?; let current: Bool }

private struct ModelRow { let name: String; let p1: Double }

private struct PlaneScene {
    let bounds: Bounds
    let points: [PPoint]
    var regions: [PRect] = []
    var field: ((Double, Double) -> Int)? = nil
    var lines: [PLine] = []
    var curve: [(Double, Double)] = []
    var query: (Double, Double)? = nil
    var tiles: [VoteTile]? = nil
    var models: [ModelRow]? = nil
}

private struct CurveScene {
    let xs: [Double]
    let ys: [Double]
    let f: (Double) -> Double
    let prev: ((Double) -> Double)?
    let split: Double?
}

private enum NodeTone { case current, kept, pruned, open }

private struct ETNode {
    let text: String
    let x: CGFloat
    let y: CGFloat
    let tone: NodeTone
    var dashed = false
    var caption: String? = nil
    var captionTone: NodeTone? = nil
}

private struct TreeScene { let nodes: [ETNode]; let edges: [(Int, Int, Bool)] }

private enum Cell { case idle, held, train, predicted, diagonal }

private struct FoldRow { let label: String; let cells: [Cell]; let result: String; let resultTone: StoryTone?; let current: Bool }

private enum EnsScene {
    case plane(PlaneScene)
    case curve(CurveScene)
    case tree(TreeScene)
    case folds([FoldRow])
}

private struct EnsFrame {
    let headline: String
    let body: String
    let scene: EnsScene
    let action: String
    var formula: [String] = []
    var legend: [(color: Color?, style: SwatchStyle, label: String)] = []
    var chips: [LabChip] = []
}

private struct EnsParam { let name: String; let symbol: String; let values: [Double]; let initial: Int; let format: (Double) -> String }

/// track: a step track with the labelled action. stepper: a parameter stepper over back + action. query: a picker over "New Query".
private enum Control { case track, stepper, query }

private struct EnsLab {
    let frames: (_ param: Int, _ tab: Int) -> [EnsFrame]
    var control: Control = .track
    var param: EnsParam? = nil
    var tabs: [String] = []
    var startTab = 0
    var navReset = false
}

// MARK: - Formatting and small math

/// Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−".
private func ex(_ v: Double, _ d: Int = 2) -> String {
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

private func sub(_ n: Int) -> String { String(String(n).map { Array("₀₁₂₃₄₅₆₇₈₉")[Int(String($0))!] }) }

private func sup(_ n: Int) -> String { String(String(n).map { Array("⁰¹²³⁴⁵⁶⁷⁸⁹")[Int(String($0))!] }) }

private func plural(_ n: Int, _ word: String) -> String { "\(n) \(word)\(n == 1 ? "" : "s")" }

private typealias EPt = (x: Double, y: Double, c: Int)

private func coord(_ p: EPt, _ f: Int) -> Double { f == 0 ? p.x : p.y }

private func giniOf(_ ys: [Int]) -> Double {
    if ys.isEmpty { return 0 }
    let p = Double(ys.filter { $0 == 1 }.count) / Double(ys.count)
    return 1 - p * p - (1 - p) * (1 - p)
}

private func splitGini(_ pts: [EPt], _ f: Int, _ t: Double) -> Double {
    let left = pts.filter { coord($0, f) < t }.map(\.c), right = pts.filter { coord($0, f) >= t }.map(\.c)
    return (Double(left.count) * giniOf(left) + Double(right.count) * giniOf(right)) / Double(pts.count)
}

private func majority(_ ys: [Int]) -> Int { ys.filter { $0 == 1 }.count * 2 > ys.count ? 1 : 0 }

private struct Split { let feature: Int; let t: Double; let gini: Double }

private func bestSplit(_ pts: [EPt], _ features: [Int]) -> Split? {
    var best: Split?
    for f in features {
        let vs = Array(Set(pts.map { coord($0, f) })).sorted()
        guard vs.count > 1 else { continue }
        for i in 0..<(vs.count - 1) {
            let t = (vs[i] + vs[i + 1]) / 2
            let g = splitGini(pts, f, t)
            if best == nil || g < best!.gini - 1e-12 { best = Split(feature: f, t: t, gini: g) }
        }
    }
    return best
}

/// A small CART: a leaf's class, or a cut on one feature.
private indirect enum Fit {
    case leaf(Int)
    case cut(feature: Int, t: Double, left: Fit, right: Fit)
}

private func grow(_ pts: [EPt], _ depth: Int, _ pick: () -> [Int]) -> Fit {
    let ys = pts.map(\.c)
    if depth == 0 || Set(ys).count < 2 { return .leaf(majority(ys)) }
    guard let s = bestSplit(pts, pick()) else { return .leaf(majority(ys)) }
    let left = pts.filter { coord($0, s.feature) < s.t }, right = pts.filter { coord($0, s.feature) >= s.t }
    if left.isEmpty || right.isEmpty { return .leaf(majority(ys)) }
    return .cut(feature: s.feature, t: s.t, left: grow(left, depth - 1, pick), right: grow(right, depth - 1, pick))
}

private func predictTree(_ n: Fit, _ x: Double, _ y: Double) -> Int {
    switch n {
    case .leaf(let c): return c
    case let .cut(f, t, l, r): return (f == 0 ? x : y) < t ? predictTree(l, x, y) : predictTree(r, x, y)
    }
}

/// The tree's leaves as rectangles, and its cuts as segments, inside `b`.
private func treeRegions(_ n: Fit, _ b: Bounds, _ rects: inout [PRect], _ lines: inout [PLine]) {
    switch n {
    case .leaf(let c): rects.append(PRect(x0: b.x0, y0: b.y0, x1: b.x1, y1: b.y1, cls: c))
    case let .cut(f, t, l, r):
        if f == 0 {
            lines.append(PLine(x0: t, y0: b.y0, x1: t, y1: b.y1))
            treeRegions(l, Bounds(x0: b.x0, x1: t, y0: b.y0, y1: b.y1), &rects, &lines)
            treeRegions(r, Bounds(x0: t, x1: b.x1, y0: b.y0, y1: b.y1), &rects, &lines)
        } else {
            lines.append(PLine(x0: b.x0, y0: t, x1: b.x1, y1: t))
            treeRegions(l, Bounds(x0: b.x0, x1: b.x1, y0: b.y0, y1: t), &rects, &lines)
            treeRegions(r, Bounds(x0: b.x0, x1: b.x1, y0: t, y1: b.y1), &rects, &lines)
        }
    }
}

// MARK: - Shared data

private let planeBounds = Bounds(x0: 0, x1: 10, y0: 0, y1: 6)

/// Five pink points up and to the left, eleven blue ones along the bottom and up to the right.
private let forestData: [EPt] = [
    (1.5, 4.3, 1), (2.0, 3.7, 1), (2.4, 4.9, 1), (3.1, 4.4, 1), (5.4, 1.9, 1),
    (1.2, 1.1, 0), (2.7, 1.4, 0), (3.8, 0.9, 0), (4.9, 1.5, 0), (6.1, 1.0, 0), (7.2, 1.3, 0),
    (8.4, 0.9, 0), (6.7, 4.1, 0), (7.3, 4.9, 0), (8.0, 4.6, 0), (8.8, 3.8, 0),
]

/// A blue arc to the upper left and a pink spread to the lower right, one pink straying into the arc.
private let arcData: [EPt] = [
    (1.3, 3.5, 0), (1.8, 3.9, 0), (2.0, 4.6, 0), (2.5, 4.7, 0), (3.0, 4.6, 0), (3.5, 4.3, 0),
    (4.0, 3.9, 0), (6.3, 3.6, 0),
    (4.7, 3.7, 1), (5.7, 2.8, 1), (6.0, 3.2, 1), (5.9, 2.4, 1), (7.1, 2.6, 1), (7.3, 1.6, 1),
    (8.4, 1.9, 1), (9.2, 2.3, 1),
]

private func classPoints(_ pts: [EPt]) -> [PPoint] { pts.map { PPoint(x: $0.x, y: $0.y, cls: $0.c) } }

private let classLegend: [(color: Color?, style: SwatchStyle, label: String)] = [(c0, .dot, "Class 0"), (c1, .dot, "Class 1")]

// MARK: - Random forest

private let rfQuery = (4.6, 3.1)
private let rfTrees = 8

private struct ForestTree { let drawn: [Int]; let fit: Fit }

private let forestTrees: [ForestTree] = (1...rfTrees).map { t in
    var random = KotlinRandom(Int32(100 + t))
    let drawn = (0..<forestData.count).map { _ in random.nextInt(forestData.count) }
    // One random feature per split: the other half of what makes the trees disagree.
    let fit = grow(drawn.map { forestData[$0] }, 2) { [random.nextInt(2)] }
    return ForestTree(drawn: drawn, fit: fit)
}

private func randomForestLab() -> EnsLab {
    EnsLab(frames: { _, _ in
        let votes = forestTrees.map { predictTree($0.fit, rfQuery.0, rfQuery.1) }
        return (1...rfTrees).map { k in
            let tree = forestTrees[k - 1]
            var rects: [PRect] = [], lines: [PLine] = []
            treeRegions(tree.fit, planeBounds, &rects, &lines)
            let inBag = Set(tree.drawn)
            let zero = votes.prefix(k).filter { $0 == 0 }.count, one = k - zero
            let v = votes[k - 1]
            let lead = zero == one ? "The forest is tied \(zero)–\(one)." : "The forest stands \(max(zero, one))–\(min(zero, one))."
            return EnsFrame(
                headline: k < rfTrees ? "Tree \(k) votes class \(v). \(lead)"
                    : "Tree \(k) votes class \(v). The forest says {class \(one > zero ? 1 : 0)}, \(max(zero, one))–\(min(zero, one)).",
                body: k < rfTrees
                    ? "Each tree trains on a bootstrap sample (faded points were left out) and checks one random feature per split, so the trees disagree near the query."
                    : "No single tree has to be right. Their errors differ, so the majority is steadier than any one of them.",
                scene: .plane(PlaneScene(
                    bounds: planeBounds,
                    points: forestData.enumerated().map { i, p in PPoint(x: p.x, y: p.y, cls: p.c, mark: inBag.contains(i) ? .normal : .faded) },
                    regions: rects, lines: lines, query: rfQuery,
                    tiles: (0..<rfTrees).map { VoteTile(label: "T\($0 + 1)", value: $0 < k ? votes[$0] : nil, current: $0 == k - 1) })),
                action: k < rfTrees ? "Grow Tree \(k + 1)" : "Start Over",
                legend: [(SimColors.active, .dot, "Query"), (mutedGrey, .dot, "Not in this sample"), (nil, .line, "Tree \(k) boundary")],
                chips: [LabChip(key: "class 0", value: "\(zero)", tint: .path), LabChip(key: "class 1", value: "\(one)", tintColor: c1)])
        }
    }, navReset: true)
}

// MARK: - Gradient boosting

private let gbData: ([Double], [Double]) = {
    var random = KotlinRandom(5)
    let xs = (0..<20).map { Double($0) / 19 }
    let ys = xs.map { x -> Double in
        let steps = 0.2 + (x > 0.3 ? 0.25 : 0) + (x > 0.55 ? 0.35 : 0) + (x > 0.75 ? 0.18 : 0)
        return steps + (random.nextDouble() - 0.5) * 0.08
    }
    return (xs, ys)
}()

private struct Stump {
    let t: Double, left: Double, right: Double
    func at(_ x: Double) -> Double { x < t ? left : right }
}

private func fitStump(_ xs: [Double], _ r: [Double]) -> Stump {
    var best: Stump?
    var bestSse = Double.greatestFiniteMagnitude
    for i in 0..<(xs.count - 1) {
        let t = (xs[i] + xs[i + 1]) / 2
        let l = xs.indices.filter { xs[$0] < t }.map { r[$0] }
        let rr = xs.indices.filter { xs[$0] >= t }.map { r[$0] }
        let lm = l.reduce(0, +) / Double(l.count), rm = rr.reduce(0, +) / Double(rr.count)
        let sse = l.reduce(0) { $0 + ($1 - lm) * ($1 - lm) } + rr.reduce(0) { $0 + ($1 - rm) * ($1 - rm) }
        if sse < bestSse { bestSse = sse; best = Stump(t: t, left: lm, right: rm) }
    }
    return best!
}

private let gbRounds = 6

private func etaWords(_ eta: Double) -> String {
    abs(eta - 1) < 1e-9 ? "all of it" : abs(eta - 0.5) < 1e-9 ? "half of it" : abs(eta - 0.1) < 1e-9 ? "a tenth of it" : "\(ex(eta)) of it"
}

private func gradientBoostingLab() -> EnsLab {
    let etas = (1...10).map { Double($0) / 10 }
    return EnsLab(frames: { p, _ in
        let eta = etas[p]
        let (xs, ys) = gbData
        let mean = ys.reduce(0, +) / Double(ys.count)
        var stumps: [Stump] = []
        var models: [(Double) -> Double] = [{ _ in mean }]
        for _ in 0..<gbRounds {
            let prev = models.last!
            let s = fitStump(xs, xs.indices.map { ys[$0] - prev(xs[$0]) })
            stumps.append(s)
            models.append { x in prev(x) + eta * s.at(x) }
        }
        func mse(_ f: (Double) -> Double) -> Double { xs.indices.reduce(0) { $0 + pow(ys[$1] - f(xs[$1]), 2) } / Double(xs.count) }
        return (0...gbRounds).map { r in
            let f = models[r]
            if r == 0 {
                return EnsFrame(
                    headline: "F₀ is just the mean: {one flat line}.",
                    body: "Every round from here fits a small tree to what the ensemble still gets wrong: the red residuals.",
                    scene: .curve(CurveScene(xs: xs, ys: ys, f: f, prev: nil, split: nil)),
                    action: "Fit Round 1",
                    formula: ["F₀ = mean(y) = \(ex(mean, 3))"],
                    legend: [(nil, .line, "Ensemble F₀"), (SimColors.red, .line, "Residual")],
                    chips: [LabChip(key: "round", value: "0"), LabChip(key: "MSE", value: ex(mse(f), 4), tint: .path)])
            }
            let s = stumps[r - 1]
            return EnsFrame(
                headline: "Tree \(r) fits only what F\(sub(r - 1)) {still gets wrong}, and adds \(etaWords(eta)).",
                body: "The red stubs are the next round's targets. A smaller η takes more rounds but overfits less.",
                scene: .curve(CurveScene(xs: xs, ys: ys, f: f, prev: models[r - 1], split: s.t)),
                action: r < gbRounds ? "Fit Round \(r + 1)" : "Start Over",
                formula: ["F\(sub(r)) = F\(sub(r - 1)) + \(ex(eta, 1)) × h\(sub(r))   h\(sub(r)) = \(ex(s.left, 3)) | \(ex(s.right, 3))",
                          "split at x = \(ex(s.t))"],
                legend: [(nil, .line, "Ensemble F\(sub(r))"), (SimColors.grey, .dashedLine, "F\(sub(r - 1))"),
                         (SimColors.red, .line, "Residual"), (SimColors.active, .dashedLine, "Split h\(sub(r))")],
                chips: [LabChip(key: "round", value: "\(r)"),
                        LabChip(key: "MSE", value: "\(ex(mse(models[r - 1]), 4)) → \(ex(mse(f), 4))", tint: .path)])
        }
    }, control: .stepper, param: EnsParam(name: "Learning rate", symbol: "η", values: etas, initial: 4, format: { ex($0) }), navReset: true)
}

// MARK: - Extra trees

private func randomCut(_ random: inout KotlinRandom, _ f: Int) -> Double {
    let vs = forestData.map { coord($0, f) }
    return vs.min()! + random.nextDouble() * (vs.max()! - vs.min()!)
}

private func cutLine(_ f: Int, _ t: Double, _ color: Color?, dash: Bool = false, width: CGFloat = 2.5) -> PLine {
    f == 0 ? PLine(x0: t, y0: planeBounds.y0, x1: t, y1: planeBounds.y1, color: color, width: width, dash: dash)
        : PLine(x0: planeBounds.x0, y0: t, x1: planeBounds.x1, y1: t, color: color, width: width, dash: dash)
}

private func extraTreesLab() -> EnsLab {
    EnsLab(frames: { _, _ in
        let best = bestSplit(forestData, [0, 1])!
        let axis = ["x", "y"]
        func draw(_ seed: Int32) -> (Double, Double, Int) {
            var random = KotlinRandom(seed)
            let tx = randomCut(&random, 0), ty = randomCut(&random, 1)
            return (tx, ty, splitGini(forestData, 0, tx) <= splitGini(forestData, 1, ty) ? 0 : 1)
        }
        let (tx, ty, kept) = draw(33)
        let gx = splitGini(forestData, 0, tx), gy = splitGini(forestData, 1, ty)
        let gk = kept == 0 ? gx : gy
        let (tx2, ty2, kept2) = draw(34)
        let gk2 = kept2 == 0 ? splitGini(forestData, 0, tx2) : splitGini(forestData, 1, ty2)
        let bestLine = cutLine(best.feature, best.t, SimColors.grey, dash: true, width: 1.5)
        let pts = classPoints(forestData)
        let cutLegend: [(color: Color?, style: SwatchStyle, label: String)] = [(SimColors.active, .line, "Random cut"), (nil, .line, "Kept"), (SimColors.grey, .dashedLine, "Best possible")]
        func formula(_ a: Double, _ b: Double, _ k: Int) -> [String] {
            let ga = ex(splitGini(forestData, 0, a), 3), gb = ex(splitGini(forestData, 1, b), 3)
            return ["x cut \(ex(a)) → \(k == 0 ? "{v:\(ga)}" : ga)   y cut \(ex(b)) → \(k == 1 ? "{v:\(gb)}" : gb)"]
        }
        let keptT = kept == 0 ? tx : ty, keptT2 = kept2 == 0 ? tx2 : ty2
        let forestCuts = (35...46).map { draw(Int32($0)) }.map { a, b, k in cutLine(k, k == 0 ? a : b, c0.opacity(0.5), width: 1.5) }
        return [
            EnsFrame(headline: "A normal tree tries {every threshold} on both features and keeps the best: gini \(ex(best.gini, 3)).",
                     body: "That search is what makes a tree expensive, and what makes a forest's trees alike.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts, lines: [bestLine])),
                     action: "Draw Random Cuts",
                     formula: ["best: \(axis[best.feature]) < \(ex(best.t)) → gini \(ex(best.gini, 3))"],
                     legend: [(SimColors.grey, .dashedLine, "Best possible")],
                     chips: [LabChip(key: "best", value: ex(best.gini, 3))]),
            EnsFrame(headline: "Extra Trees draws {one random threshold} per feature: x at \(ex(tx)), y at \(ex(ty)).",
                     body: "No search: each cut is uniform between the feature's smallest and largest value.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts, lines: [bestLine, cutLine(0, tx, SimColors.active), cutLine(1, ty, SimColors.active)])),
                     action: "Keep the Better", formula: formula(tx, ty, -1), legend: cutLegend,
                     chips: [LabChip(key: "best", value: ex(best.gini, 3))]),
            EnsFrame(headline: "Extra Trees draws {one random threshold} per feature and keeps the better one.",
                     body: "This time the \(axis[kept]) cut scores \(ex(gk, 3)) against the best split's \(ex(best.gini, 3)); the \(axis[1 - kept]) cut scores \(ex(kept == 0 ? gy : gx, 3)). With no search, trees are cheaper and differ more, so their average generalises better.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts, lines: [bestLine, cutLine(1 - kept, kept == 0 ? ty : tx, SimColors.active), cutLine(kept, keptT, nil)])),
                     action: "Draw New Cuts", formula: formula(tx, ty, kept), legend: cutLegend,
                     chips: [LabChip(key: "kept", value: ex(gk, 3), tint: .answer), LabChip(key: "best", value: ex(best.gini, 3))]),
            EnsFrame(headline: "A new draw keeps the {\(axis[kept2]) cut at \(ex(keptT2))}, scoring \(ex(gk2, 3)).",
                     body: "Each tree in the forest gets its own draws, so no two trees split alike.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts, lines: [bestLine, cutLine(1 - kept2, kept2 == 0 ? ty2 : tx2, SimColors.active), cutLine(kept2, keptT2, nil)])),
                     action: "Grow the Forest", formula: formula(tx2, ty2, kept2), legend: cutLegend,
                     chips: [LabChip(key: "kept", value: ex(gk2, 3), tint: .answer), LabChip(key: "best", value: ex(best.gini, 3))]),
            EnsFrame(headline: "Twelve trees, twelve different root cuts: their {average} is smoother than any one.",
                     body: "Extra Trees trades a little bias in each tree for much less variance across them.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts, lines: forestCuts + [bestLine])),
                     action: "Start Over",
                     legend: [(c0, .line, "Root cut of each tree"), (SimColors.grey, .dashedLine, "Best possible")],
                     chips: [LabChip(key: "trees", value: "12"), LabChip(key: "best", value: ex(best.gini, 3))]),
        ]
    }, navReset: true)
}

// MARK: - Bagging

private let bagCount = 4

private let bags: [ForestTree] = (1...bagCount).map { k in
    var random = KotlinRandom(Int32(200 + k))
    let drawn = (0..<forestData.count).map { _ in random.nextInt(forestData.count) }
    return ForestTree(drawn: drawn, fit: grow(drawn.map { forestData[$0] }, 2) { [0, 1] })
}

private func oobAccuracy(_ upTo: Int) -> (Int, Int) {
    var right = 0, seen = 0
    for (i, p) in forestData.enumerated() {
        let votes = bags.prefix(upTo).filter { !$0.drawn.contains(i) }.map { predictTree($0.fit, p.x, p.y) }
        if votes.isEmpty { continue }
        seen += 1
        if majority(votes) == p.c { right += 1 }
    }
    return (right, seen)
}

private func baggingLab() -> EnsLab {
    EnsLab(frames: { _, _ in
        let n = forestData.count
        let pOut = pow(1 - 1 / Double(n), Double(n))
        var frames: [EnsFrame] = (1...bagCount).map { k in
            let bag = bags[k - 1]
            var counts: [Int: Int] = [:]
            for i in bag.drawn { counts[i, default: 0] += 1 }
            var rects: [PRect] = [], lines: [PLine] = []
            treeRegions(bag.fit, planeBounds, &rects, &lines)
            let (right, seen) = oobAccuracy(k)
            let distinct = counts.count
            return EnsFrame(
                headline: "Sample \(k) drew \(distinct) distinct rows and left \(n - distinct) out.",
                body: "The left-out rows test trees that never saw them, so bagging gets a validation score for free. Averaging trees cuts variance, not bias.",
                scene: .plane(PlaneScene(
                    bounds: planeBounds,
                    points: forestData.enumerated().map { i, p in
                        let c = counts[i] ?? 0
                        return PPoint(x: p.x, y: p.y, cls: p.c, mark: c == 0 ? .hollow : .normal, tag: c > 1 ? "×\(c)" : nil)
                    },
                    regions: rects, lines: lines)),
                action: k < bagCount ? "Draw Sample \(k + 1)" : "Vote",
                formula: ["P(row left out) = (1 − 1/\(n))\(sup(n)) = {v:\(ex(pOut))}"],
                legend: [(SimColors.idle, .dot, "Drawn (×n = repeats)"), (SimColors.grey, .ring, "Out of bag"), (nil, .line, "Tree \(k)")],
                chips: [LabChip(key: "in bag", value: "\(distinct)/\(n)"), LabChip(key: "out of bag", value: "\(n - distinct)"),
                        LabChip(key: "OOB acc", value: "\(right)/\(seen)", good: true)])
        }
        let (right, seen) = oobAccuracy(bagCount)
        frames.append(EnsFrame(
            headline: "\(bagCount) trees vote, and the out-of-bag accuracy is {m:\(right)/\(seen)}.",
            body: "Each row was scored only by trees that never trained on it, so no hold-out set was needed.",
            scene: .plane(PlaneScene(bounds: planeBounds, points: classPoints(forestData),
                                     field: { x, y in majority(bags.map { predictTree($0.fit, x, y) }) })),
            action: "Start Over", legend: classLegend,
            chips: [LabChip(key: "trees", value: "\(bagCount)"), LabChip(key: "OOB acc", value: "\(right)/\(seen)", good: true)]))
        return frames
    }, navReset: true)
}

// MARK: - Voting

private func sigmoid(_ z: Double) -> Double { 1 / (1 + exp(-z)) }

private let voteModels: [(String, (Double, Double) -> Double)] = [
    ("Tree", { x, _ in x >= 5 ? 1 : 0 }),
    ("SVM", { x, y in sigmoid(1.5 * ((x - 5.3) - 0.8 * (y - 3.2))) }),
    ("LogReg", { x, y in sigmoid(1.2 * ((x - 5.35) - 0.3 * (y - 3.2))) }),
]

private let voteQueries = [(5.1, 3.2), (2.5, 4.5), (7.5, 2.0), (5.6, 4.6)]

private let voteData: [EPt] = [
    (1.4, 4.0, 0), (1.8, 4.8, 0), (2.2, 5.0, 0), (2.6, 4.9, 0), (3.1, 4.6, 0), (3.6, 4.2, 0), (2.0, 3.6, 0),
    (5.6, 2.9, 1), (5.9, 2.4, 1), (6.5, 2.8, 1), (6.5, 1.4, 1), (7.4, 1.8, 1), (8.1, 1.8, 1), (8.1, 2.3, 1),
]

private func softBoundary() -> [(Double, Double)] {
    (0...60).compactMap { j in
        let y = planeBounds.y0 + (planeBounds.y1 - planeBounds.y0) * Double(j) / 60
        func avg(_ x: Double) -> Double { voteModels.reduce(0) { $0 + $1.1(x, y) } / Double(voteModels.count) }
        var lo = planeBounds.x0, hi = planeBounds.x1
        if avg(lo) >= 0.5 || avg(hi) < 0.5 { return nil }
        for _ in 0..<40 { let mid = (lo + hi) / 2; if avg(mid) >= 0.5 { hi = mid } else { lo = mid } }
        return (hi, y)
    }
}

/// A linear model's p = 0.5 line clipped to the plane, found along its two ends.
private func halfLine(_ p: (Double, Double) -> Double) -> PLine? {
    let pts: [(Double, Double)] = [planeBounds.y0, planeBounds.y1].compactMap { y in
        var lo = -20.0, hi = 30.0
        if (p(lo, y) - 0.5) * (p(hi, y) - 0.5) > 0 { return nil }
        for _ in 0..<50 { let mid = (lo + hi) / 2; if p(mid, y) >= 0.5 { hi = mid } else { lo = mid } }
        return (hi, y)
    }
    return pts.count == 2 ? PLine(x0: pts[0].0, y0: pts[0].1, x1: pts[1].0, y1: pts[1].1, color: SimColors.grey, width: 1.5, dash: true) : nil
}

private func votingLab() -> EnsLab {
    EnsLab(frames: { _, tab in
        let soft = tab == 1
        let boundary = softBoundary()
        let modelLines = [PLine(x0: 5, y0: planeBounds.y0, x1: 5, y1: planeBounds.y1, color: SimColors.grey, width: 1.5, dash: true),
                          halfLine(voteModels[1].1), halfLine(voteModels[2].1)].compactMap { $0 }
        let field: (Double, Double) -> Int = soft
            ? { x, y in voteModels.reduce(0) { $0 + $1.1(x, y) } / 3 >= 0.5 ? 1 : 0 }
            : { x, y in majority(voteModels.map { $0.1(x, y) >= 0.5 ? 1 : 0 }) }
        return voteQueries.map { q in
            let ps = voteModels.map { $0.1(q.0, q.1) }
            let hardVotes = ps.map { $0 >= 0.5 ? 1 : 0 }
            let hard = majority(hardVotes)
            let ones = hardVotes.filter { $0 == 1 }.count
            let avg = ps.reduce(0, +) / 3
            let softCls = avg >= 0.5 ? 1 : 0
            let headline: String, body: String
            if hard != softCls && soft {
                let certain = voteModels[hardVotes.firstIndex(of: softCls) ?? 0].0
                headline = "Hard voting says class \(hard), \(max(ones, 3 - ones)) to \(min(ones, 3 - ones)). Soft voting says {v:class \(softCls)}."
                body = "The \(certain == "Tree" ? "tree" : certain) is certain; the others only lean the other way. Averaging probabilities lets confidence count."
            } else if hard != softCls {
                headline = "Hard voting says {v:class \(hard)}, \(max(ones, 3 - ones)) to \(min(ones, 3 - ones)). Soft voting would say class \(softCls)."
                body = "Each model gets one vote however sure it is, so two weak leanings outvote one certainty."
            } else {
                headline = "Both votes agree: {class \(hard)}."
                body = "Away from the boundary the models agree, and so do the two ways of counting them."
            }
            return EnsFrame(
                headline: headline, body: body,
                scene: .plane(PlaneScene(bounds: planeBounds, points: classPoints(voteData), field: field, lines: modelLines,
                                         curve: soft ? boundary : [], query: q,
                                         models: voteModels.indices.map { ModelRow(name: voteModels[$0].0, p1: ps[$0]) })),
                action: "New Query",
                formula: soft ? ["soft = (\(ps.map { ex($0) }.joined(separator: " + "))) / 3 = {v:\(ex(avg))} → \(softCls)"]
                    : ["hard = votes \(hardVotes.map(String.init).joined(separator: ", ")) → {v:\(hard)} (\(ones) to \(3 - ones))"],
                legend: [(nil, .line, soft ? "Soft-vote boundary" : "Hard-vote regions"), (SimColors.grey, .dashedLine, "Each model"), (SimColors.active, .dot, "Query")])
        }
    }, control: .query, tabs: ["Hard vote", "Soft vote"], startTab: 1, navReset: true)
}

// MARK: - Stacking

private let stackFolds = [[0, 7, 12, 3], [5, 10, 14], [1, 8, 13], [6, 11, 2], [9, 4, 15]]

private func nearestLabel(_ i: Int, _ pool: [Int]) -> Int {
    let p = forestData[i]
    let j = pool.min { pow(forestData[$0].x - p.x, 2) + pow(forestData[$0].y - p.y, 2) < pow(forestData[$1].x - p.x, 2) + pow(forestData[$1].y - p.y, 2) }!
    return forestData[j].c
}

private func stackingLab() -> EnsLab {
    EnsLab(frames: { _, _ in
        let all = Array(forestData.indices)
        let foldCorrect = stackFolds.map { fold in fold.filter { i in nearestLabel(i, all.filter { !fold.contains($0) }) == forestData[i].c }.count }
        func rows(_ done: Int, _ current: Int?) -> [FoldRow] {
            stackFolds.indices.map { f in
                let cells: [Cell] = stackFolds.indices.map { c in
                    f < done ? (c == f ? .predicted : .idle) : f == current ? (c == f ? .held : .train) : (c == f ? .diagonal : .idle)
                }
                return FoldRow(label: "Fold \(f + 1)", cells: cells,
                               result: f < done || f == current ? "\(foldCorrect[f])/\(stackFolds[f].count)" : "—",
                               resultTone: f < done ? .done : f == current ? .active : nil, current: f == current)
            }
        }
        func soFar(_ k: Int) -> (Int, Int) { (foldCorrect.prefix(k).reduce(0, +), stackFolds.prefix(k).reduce(0) { $0 + $1.count }) }
        let legend: [(color: Color?, style: SwatchStyle, label: String)] = [(SimColors.active, .fill, "Held out"), (SimColors.blue, .fill, "Trains the base model"), (SimColors.green, .fill, "Predicted")]
        let n = forestData.count
        var frames = [
            EnsFrame(headline: "Stacking trains a {meta-learner} on the base models' predictions.",
                     body: "Those predictions have to come from rows the base model never trained on, or the meta-learner learns from a lie.",
                     scene: .folds(rows(0, nil)), action: "Try In-Sample", formula: ["\(n) rows in 5 folds"], legend: legend,
                     chips: [LabChip(key: "meta-features", value: "0/\(n)", tint: .path)]),
            EnsFrame(headline: "In-sample, 1-NN scores {w:\(n)/\(n)}: every row is its own nearest neighbour.",
                     body: "A meta-learner trained on that would learn to trust a perfect score that isn't real.",
                     scene: .folds(rows(0, nil)), action: "Predict Fold 1", formula: ["in-sample {w:\(n)/\(n)}"], legend: legend,
                     chips: [LabChip(key: "meta-features", value: "0/\(n)", tint: .path)]),
        ]
        for f in stackFolds.indices {
            let (c, m) = soFar(f + 1)
            frames.append(EnsFrame(
                headline: "Fold \(f + 1) is predicted by a model that {never saw it}.",
                body: f == 2
                    ? "1-NN scores \(n)/\(n) on its own training rows, since every row is its own nearest neighbour. Training the meta-learner on that would teach it to trust a score that doesn't exist."
                    : "The other four folds train 1-NN; its predictions on fold \(f + 1) become the meta-features for those rows.",
                scene: .folds(rows(f, f)), action: f < stackFolds.count - 1 ? "Predict Fold \(f + 2)" : "Assemble",
                formula: ["in-sample {w:\(n)/\(n)} · out-of-fold so far {m:\(c)/\(m)}"], legend: legend,
                chips: [LabChip(key: "meta-features", value: "\(m)/\(n)", tint: .path)]))
        }
        let (c, _) = soFar(stackFolds.count)
        frames.append(EnsFrame(
            headline: "Every row now has an honest prediction: out-of-fold accuracy {m:\(c)/\(n)}.",
            body: "These out-of-fold predictions are the meta-learner's training features, one column per base model.",
            scene: .folds(rows(stackFolds.count, nil)), action: "Blend Instead",
            formula: ["in-sample {w:\(n)/\(n)} · out-of-fold {m:\(c)/\(n)}"], legend: legend,
            chips: [LabChip(key: "meta-features", value: "\(n)/\(n)", tint: .path)]))
        frames.append(EnsFrame(
            headline: "Blending skips the folds: it holds out {one set} once.",
            body: "Simpler and faster, but the meta-learner then trains on only the held-out rows.",
            scene: .folds(rows(stackFolds.count, nil)), action: "Start Over",
            formula: ["stacking: every row, out of fold · blending: one hold-out set"], legend: legend,
            chips: [LabChip(key: "meta-features", value: "\(n)/\(n)", tint: .path)]))
        return frames
    })
}

// MARK: - XGBoost

private let xgbLambda = 1.0

private func xgbGain(_ gl: Double, _ hl: Double, _ gr: Double, _ hr: Double, _ gamma: Double) -> Double {
    0.5 * (gl * gl / (hl + xgbLambda) + gr * gr / (hr + xgbLambda) - pow(gl + gr, 2) / (hl + hr + xgbLambda)) - gamma
}

private func xgboostLab() -> EnsLab {
    let gammas = (0...10).map { Double($0) / 10 }
    return EnsLab(frames: { p, _ in
        let gamma = gammas[p]
        let root = xgbGain(-6, 4, -1, 4, gamma)
        let raw = xgbGain(-0.6, 2, -0.4, 2, 0)
        let split = raw - gamma
        let rootKept = root > 0, splitKept = split > 0
        let legend: [(color: Color?, style: SwatchStyle, label: String)] = [(SimColors.active, .fill, "Current"), (SimColors.green, .fill, "Kept"), (SimColors.red, .fill, "Pruned")]
        let rootTone: NodeTone = rootKept ? .kept : .pruned
        let chipsBase = [LabChip(key: "λ", value: "1"), LabChip(key: "root gain", value: ex(root), good: rootKept, tint: rootKept ? nil : .warn)]
        func rootNode(_ tone: NodeTone) -> ETNode { ETNode(text: "gain \(ex(root))", x: 0.3, y: 0.14, tone: tone) }
        let left = ETNode(text: "G −6.0 H 4.0", x: 0.0, y: 0.5, tone: .open)
        let splitTone: NodeTone = splitKept ? .kept : .pruned
        var finalNodes = [rootKept ? rootNode(.kept) : ETNode(text: "G −7.0 H 8.0", x: 0.3, y: 0.14, tone: .kept)]
        var finalEdges: [(Int, Int, Bool)] = []
        if rootKept {
            finalNodes.append(ETNode(text: "w \(ex(6.0 / 5))", x: 0.0, y: 0.5, tone: .kept))
            finalNodes.append(splitKept ? ETNode(text: "gain \(ex(split))", x: 0.72, y: 0.5, tone: .kept) : ETNode(text: "w \(ex(1.0 / 5))", x: 0.72, y: 0.5, tone: .kept))
            finalEdges += [(0, 1, false), (0, 2, false)]
            if splitKept {
                finalNodes.append(ETNode(text: "w \(ex(0.6 / 3))", x: 0.44, y: 0.86, tone: .kept))
                finalNodes.append(ETNode(text: "w \(ex(0.4 / 3))", x: 1.0, y: 0.86, tone: .kept))
                finalEdges += [(2, 3, false), (2, 4, false)]
            }
        }
        let leaves = !rootKept ? 1 : splitKept ? 3 : 2
        return [
            EnsFrame(headline: "Each leaf keeps two sums: gradients {G} and Hessians H.",
                     body: "Its best output is −G / (H + λ). A split is worth making only if it lowers the loss by more than γ.",
                     scene: .tree(TreeScene(nodes: [ETNode(text: "G −7.0 H 8.0", x: 0.3, y: 0.14, tone: .current)], edges: [])),
                     action: "Split Root", formula: ["w* = −G / (H + λ) = 7.0 / 9 = {v:\(ex(7.0 / 9))}"], legend: legend,
                     chips: [LabChip(key: "λ", value: "1")]),
            EnsFrame(headline: rootKept ? "Splitting the root gains {m:\(ex(root))} after γ, so it stays."
                         : "Splitting the root gains {w:\(ex(root))} after γ, so even the root split is pruned.",
                     body: "Gain is how much the split lowers the loss, minus γ for the extra leaf.",
                     scene: .tree(TreeScene(nodes: [rootNode(rootTone), left, ETNode(text: "G −1.0 H 4.0", x: 0.72, y: 0.5, tone: .open)],
                                            edges: [(0, 1, false), (0, 2, false)])),
                     action: "Try Right Child",
                     formula: ["½[6.0²/5 + 1.0²/5 − 7.0²/9] − γ", "= \(ex(root + gamma)) − \(ex(gamma)) = \(rootKept ? "{m:" : "{w:")\(ex(root))}"],
                     legend: legend, chips: chipsBase),
            EnsFrame(headline: splitKept ? "Splitting the right child scores {m:\(ex(split))}, so XGBoost keeps it."
                         : "Splitting the right child scores {w:\(ex(split))}, so XGBoost prunes it.",
                     body: splitKept ? "The split helps by \(ex(raw)) before γ, more than the \(ex(gamma)) that γ charges for a new split."
                         : "The split barely helps (\(ex(raw)) before γ), and γ charges \(ex(gamma)) for each new split. Pruning runs after the tree reaches max depth.",
                     scene: .tree(TreeScene(nodes: [rootNode(rootTone), left, ETNode(text: "G −1.0 H 4.0", x: 0.72, y: 0.5, tone: .current),
                                                    ETNode(text: "G −0.6 H 2", x: 0.44, y: 0.86, tone: splitTone, dashed: true),
                                                    ETNode(text: "G −0.4 H 2", x: 1.0, y: 0.86, tone: splitTone, dashed: true)],
                                            edges: [(0, 1, false), (0, 2, false), (2, 3, true), (2, 4, true)])),
                     action: splitKept ? "Keep Split" : "Prune Split",
                     formula: ["½[0.6²/3 + 0.4²/3 − 1.0²/5] − γ", "= \(ex(raw)) − \(ex(gamma)) = \(splitKept ? "{m:" : "{w:")\(ex(split))}"],
                     legend: legend,
                     chips: chipsBase + [LabChip(key: "split", value: ex(split), good: splitKept, tint: splitKept ? nil : .warn)]),
            EnsFrame(headline: "The finished tree has {\(leaves) \(leaves == 1 ? "leaf" : "leaves")}.",
                     body: "Each leaf outputs w = −G / (H + λ). Raise γ and weak splits go first; lower it and the tree grows until only λ holds it back.",
                     scene: .tree(TreeScene(nodes: finalNodes, edges: finalEdges)),
                     action: "Start Over", formula: ["w = −G / (H + λ) at every leaf"], legend: legend, chips: chipsBase),
        ]
    }, control: .stepper, param: EnsParam(name: "Split penalty", symbol: "γ", values: gammas, initial: 5, format: { ex($0) }))
}

// MARK: - AdaBoost

private let adaRoundCount = 9

private struct AdaStump {
    let feature: Int, t: Double, sign: Int
    func h(_ x: Double, _ y: Double) -> Int { ((feature == 0 ? x : y) >= t) == (sign > 0) ? 1 : -1 }
}

private struct AdaRound { let stump: AdaStump; let weights: [Double]; let eps: Double; let alpha: Double; let missed: Set<Int> }

private let adaRounds: [AdaRound] = {
    let n = arcData.count
    var w = Array(repeating: 1.0 / Double(n), count: n)
    let labels = arcData.map { $0.c == 1 ? 1 : -1 }
    var rounds: [AdaRound] = []
    for _ in 0..<adaRoundCount {
        var best: AdaStump?
        var bestErr = Double.greatestFiniteMagnitude
        for f in 0...1 {
            let vs = Array(Set(arcData.map { coord($0, f) })).sorted()
            for i in 0..<(vs.count - 1) {
                let t = (vs[i] + vs[i + 1]) / 2
                for sign in [1, -1] {
                    let s = AdaStump(feature: f, t: t, sign: sign)
                    let err = arcData.indices.reduce(0.0) { $0 + (s.h(arcData[$1].x, arcData[$1].y) != labels[$1] ? w[$1] : 0) }
                    if err < bestErr - 1e-12 { bestErr = err; best = s }
                }
            }
        }
        let s = best!
        let eps = min(max(bestErr, 1e-6), 1 - 1e-6)
        let alpha = 0.5 * log((1 - eps) / eps)
        let missed = Set(arcData.indices.filter { s.h(arcData[$0].x, arcData[$0].y) != labels[$0] })
        rounds.append(AdaRound(stump: s, weights: w, eps: eps, alpha: alpha, missed: missed))
        let next = arcData.indices.map { w[$0] * exp(-alpha * Double(labels[$0] * s.h(arcData[$0].x, arcData[$0].y))) }
        let z = next.reduce(0, +)
        w = next.map { $0 / z }
    }
    return rounds
}()

private func stumpLine(_ s: AdaStump, _ color: Color?, dash: Bool) -> PLine { cutLine(s.feature, s.t, color, dash: dash, width: dash ? 1.5 : 2.5) }

private func adaBoostLab() -> EnsLab {
    EnsLab(frames: { _, _ in
        let rounds = adaRounds
        let n = arcData.count
        func ensemble(_ k: Int) -> (Double, Double) -> Int {
            { x, y in rounds.prefix(k).reduce(0.0) { $0 + $1.alpha * Double($1.stump.h(x, y)) } >= 0 ? 1 : 0 }
        }
        var frames = [EnsFrame(
            headline: "Every point starts with {equal weight}: 1/\(n) each.",
            body: "Each round fits one stump, a single cut, to the weighted points. Dot size is the weight.",
            scene: .plane(PlaneScene(bounds: planeBounds, points: classPoints(arcData))),
            action: "Fit Round 1", legend: classLegend, chips: [LabChip(key: "round", value: "0 / \(adaRoundCount)")])]
        for k in 1...adaRoundCount {
            let r = rounds[k - 1]
            let prev: AdaRound? = k >= 2 ? rounds[k - 2] : nil
            let fin = ensemble(k)
            let right = arcData.filter { fin($0.x, $0.y) == $0.c }.count
            let headline: String, body: String
            if let prev {
                let ratio = Int(exp(2 * prev.alpha).rounded())
                let m = prev.missed.count
                headline = m == 1 ? "The one point stump \(k - 1) missed is now {\(ratio)× heavier}, so stump \(k) cuts for it."
                    : "The \(m) points stump \(k - 1) missed are now {\(ratio)× heavier}, so stump \(k) weighs them most."
                body = "Dot size is the weight. Each stump votes with its α, so more accurate stumps count for more."
                    + (k == adaRoundCount ? " After \(adaRoundCount) rounds the weighted vote gets \(right)/\(n) right." : "")
            } else {
                headline = "Stump 1 is the best single cut: it misses {\(plural(r.missed.count, "point"))}."
                body = "Its weighted error is ε₁ = \(ex(r.eps, 3)), so its vote counts α₁ = \(ex(r.alpha))."
            }
            let shown = prev ?? r
            let kk = prev == nil ? k : k - 1
            let ring = prev?.missed ?? []
            frames.append(EnsFrame(
                headline: headline, body: body,
                scene: .plane(PlaneScene(
                    bounds: planeBounds,
                    points: arcData.enumerated().map { i, p in
                        PPoint(x: p.x, y: p.y, cls: p.c, scale: min(max(CGFloat((r.weights[i] * Double(n)).squareRoot()), 0.6), 3.2),
                               ring: ring.contains(i) ? SimColors.red : nil)
                    },
                    field: fin,
                    lines: [prev.map { stumpLine($0.stump, SimColors.grey, dash: true) }, stumpLine(r.stump, nil, dash: false)].compactMap { $0 })),
                action: k < adaRoundCount ? "Fit Round \(k + 1)" : "Start Over",
                formula: ["ε\(sub(kk)) = \(ex(shown.eps, 3))   α\(sub(kk)) = ½ ln((1 − ε\(sub(kk)))/ε\(sub(kk))) = {v:\(ex(shown.alpha))}",
                          "wrong × e^α\(sub(kk)), right × e^−α\(sub(kk)) → ×\(Int(exp(2 * shown.alpha).rounded())) heavier"],
                legend: prev == nil ? [(nil, .line, "Stump 1")] + classLegend
                    : [(SimColors.red, .ring, "Missed by stump \(k - 1)"), (SimColors.grey, .dashedLine, "Stump \(k - 1)"), (nil, .line, "Stump \(k)")],
                chips: [LabChip(key: "round", value: "\(k) / \(adaRoundCount)"),
                        LabChip(key: "ε\(sub(k))", value: ex(r.eps, 3), tint: .active),
                        LabChip(key: "α\(sub(k))", value: ex(r.alpha), tint: .answer)]))
        }
        return frames
    }, navReset: true)
}

// MARK: - LightGBM

private struct GNode { let id: String; let parent: String?; let gain: Double; let depth: Int }

private let gbmNodes = [
    GNode(id: "A", parent: nil, gain: 5.0, depth: 0), GNode(id: "B", parent: "A", gain: 4.0, depth: 1), GNode(id: "C", parent: "A", gain: 0.3, depth: 1),
    GNode(id: "D", parent: "B", gain: 2.1, depth: 2), GNode(id: "E", parent: "B", gain: 1.8, depth: 2), GNode(id: "J", parent: "C", gain: 0.15, depth: 2),
    GNode(id: "K", parent: "C", gain: 0.1, depth: 2), GNode(id: "F", parent: "D", gain: 1.2, depth: 3), GNode(id: "G", parent: "D", gain: 0.4, depth: 3),
    GNode(id: "H", parent: "E", gain: 0.9, depth: 3), GNode(id: "I", parent: "E", gain: 0.2, depth: 3),
]

private let leafOrder = ["A", "B", "D", "E"]
private let levelOrder = ["A", "B", "C", "D"]

private func gnode(_ id: String) -> GNode { gbmNodes.first { $0.id == id }! }

/// The visible tree: the root, and the children of every split node, laid out by in-order leaf slots.
private func gbmScene(_ done: [String], _ current: String?, _ otherNext: String?, _ mode: String, _ other: String) -> TreeScene {
    let visible = gbmNodes.filter { $0.parent == nil || done.contains($0.parent!) }
    var xs: [String: CGFloat] = [:]
    var slot = 0
    func place(_ n: GNode) {
        let kids = visible.filter { $0.parent == n.id }
        if kids.isEmpty { xs[n.id] = CGFloat(slot); slot += 1; return }
        kids.forEach(place)
        xs[n.id] = kids.map { xs[$0.id]! }.reduce(0, +) / CGFloat(kids.count)
    }
    place(visible[0])
    let slots = CGFloat(max(slot - 1, 1))
    let nodes = visible.map { n -> ETNode in
        let tone: NodeTone = done.contains(n.id) ? .kept : n.id == current ? .current : .open
        let caption: String? = n.id == current ? "\(mode) takes" : n.id == otherNext ? "\(other) next" : nil
        return ETNode(text: "gain \(ex(n.gain, 1))", x: slot == 1 ? 0.5 : xs[n.id]! / slots, y: 0.12 + CGFloat(n.depth) * 0.27, tone: tone,
                     caption: caption, captionTone: n.id == current ? .current : .open)
    }
    let index = Dictionary(uniqueKeysWithValues: visible.enumerated().map { ($1.id, $0) })
    return TreeScene(nodes: nodes, edges: visible.compactMap { n in n.parent.map { (index[$0]!, index[n.id]!, false) } })
}

private func lightGbmLab() -> EnsLab {
    EnsLab(frames: { _, tab in
        let leaf = tab == 0
        let order = leaf ? leafOrder : levelOrder
        let otherOrder = leaf ? levelOrder : leafOrder
        let mode = leaf ? "leaf-wise" : "level-wise", other = leaf ? "level-wise" : "leaf-wise"
        func loss(_ o: [String], _ k: Int) -> Double { o.prefix(k).reduce(0) { $0 + gnode($1).gain } }
        func chips(_ k: Int) -> [LabChip] {
            [LabChip(key: "loss ↓ leaf-wise", value: ex(loss(leafOrder, k), 1), tint: leaf ? .answer : nil),
             LabChip(key: "level-wise", value: ex(loss(levelOrder, k), 1), tint: leaf ? nil : .answer)]
        }
        let legend: [(color: Color?, style: SwatchStyle, label: String)] = [(SimColors.active, .fill, "Split now"), (SimColors.green, .fill, "Split"), (SimColors.grey, .fill, "Open leaf")]
        var frames: [EnsFrame] = order.indices.map { i in
            let s = i + 1
            let cur = order[i], alt = otherOrder[i]
            let g = gnode(cur).gain, ga = gnode(alt).gain
            let headline: String, body: String
            if cur == alt && i == 0 {
                headline = "Split 1: both strategies start at the {root}, gain \(ex(g, 1))."
                body = "A leaf's gain is how much loss its best split would remove."
            } else if cur == alt {
                headline = "Split \(s): the {\(ex(g, 1))} leaf is next either way."
                body = "While the tree is shallow the two strategies agree."
            } else if leaf {
                let deeper = gnode(cur).depth > gnode(alt).depth
                headline = "Split \(s): leaf-wise takes the {\(ex(g, 1))} leaf\(deeper ? ", one level deeper" : "")."
                body = "Level-wise would split the \(ex(ga, 1)) leaf first and remove \(ex(g - ga, 1)) less loss."
            } else {
                headline = "Split \(s): level-wise takes the {\(ex(g, 1))} leaf to finish depth \(gnode(cur).depth)."
                body = "Leaf-wise would split the \(ex(ga, 1)) leaf here and remove \(ex(ga - g, 1)) more loss."
            }
            let done = Array(order.prefix(i))
            return EnsFrame(headline: headline, body: body,
                            scene: .tree(gbmScene(done, cur, alt != cur && !done.contains(alt) ? alt : nil, mode, other)),
                            action: s < order.count ? (leaf ? "Split Best Leaf" : "Split Next Leaf") : "Compare",
                            legend: legend, chips: chips(s))
        }
        frames.append(EnsFrame(
            headline: "With 5 leaves each, leaf-wise removes {\(ex(loss(leafOrder, 4), 1))} loss, level-wise \(ex(loss(levelOrder, 4), 1)).",
            body: "Same leaf budget, more loss removed: that is why LightGBM grows leaf-wise and caps trees with num_leaves.",
            scene: .tree(gbmScene(order, nil, nil, mode, other)), action: "Why It Overfits", legend: legend, chips: chips(4)))
        frames.append(EnsFrame(
            headline: "Deeper leaves hold {fewer rows}, so leaf-wise trees fit noise faster.",
            body: "On small data keep num_leaves low, set min_data_in_leaf, or cap max_depth. Level-wise trees are safer there.",
            scene: .tree(gbmScene(order, nil, nil, mode, other)), action: "Start Over", legend: legend, chips: chips(4)))
        return frames
    }, tabs: ["Leaf-wise", "Level-wise"])
}

// MARK: - Isolation forest

private let isoData: [EPt] = {
    var random = KotlinRandom(8)
    var pts: [EPt] = []
    for _ in 0..<18 {
        let x = 5.0 + (random.nextDouble() - 0.5) * 2.2
        pts.append((x, 3.0 + (random.nextDouble() - 0.5) * 1.6, 0))
    }
    return pts + [(1.2, 5.3, 1), (9.2, 1.1, 1)]
}()

/// Random x cuts until `target` is alone; the cuts in order.
private func isolate(_ target: Int, _ random: inout KotlinRandom) -> [Double] {
    var pool = Array(isoData.indices)
    var cuts: [Double] = []
    while pool.count > 1 && cuts.count < 40 {
        let xs = pool.map { isoData[$0].x }
        let lo = xs.min()!, hi = xs.max()!
        if hi - lo < 1e-9 { break }
        let t = lo + random.nextDouble() * (hi - lo)
        cuts.append(t)
        let tx = isoData[target].x
        pool = pool.filter { (isoData[$0].x < t) == (tx < t) }
    }
    return cuts
}

private func cOf(_ n: Int) -> Double { 2 * (log(Double(n) - 1) + 0.5772156649) - 2 * Double(n - 1) / Double(n) }

private func isolationForestLab() -> EnsLab {
    EnsLab(frames: { _, _ in
        let n = isoData.count
        let outlier = n - 2
        let mx = isoData.prefix(18).reduce(0) { $0 + $1.x } / 18, my = isoData.prefix(18).reduce(0) { $0 + $1.y } / 18
        let centre = (0..<18).min { pow(isoData[$0].x - mx, 2) + pow(isoData[$0].y - my, 2) < pow(isoData[$1].x - mx, 2) + pow(isoData[$1].y - my, 2) }!
        var r3 = KotlinRandom(3), r4 = KotlinRandom(4)
        let outCuts = isolate(outlier, &r3), inCuts = isolate(centre, &r4)
        let c = cOf(n)
        let depth: [Double] = isoData.indices.map { i in
            (0..<100).reduce(0.0) { acc, t in
                var r = KotlinRandom(Int32(1000 + t))
                return acc + Double(isolate(i, &r).count)
            } / 100
        }
        let scores = depth.map { pow(2, -$0 / c) }
        func pts(_ active: Int?) -> [PPoint] { isoData.enumerated().map { i, p in PPoint(x: p.x, y: p.y, cls: p.c, ring: i == active ? SimColors.active : nil) } }
        func cutLines(_ cuts: [Double]) -> [PLine] { cuts.map { PLine(x0: $0, y0: planeBounds.y0, x1: $0, y1: planeBounds.y1, width: 1.5) } }
        let legend: [(color: Color?, style: SwatchStyle, label: String)] = [(SimColors.active, .dot, "Being isolated"), (c1, .dot, "Outlier"), (nil, .line, "Random cut")]
        let formula = [
            "s = 2^(−E[h] / c(n))   c(\(n)) = \(ex(c))",
            "outlier 2^(−\(ex(depth[outlier], 1))/\(ex(c))) = {w:\(ex(scores[outlier]))}   point 2^(−\(ex(depth[centre], 1))/\(ex(c))) = {m:\(ex(scores[centre]))}",
        ]
        let clusterMax = scores.prefix(18).max()!
        return [
            EnsFrame(headline: "Isolation Forest cuts at {random} places until a point stands alone.",
                     body: "No distances, no densities: anomalies are simply the points that are easy to cut off.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts(nil))), action: "Isolate an Outlier", legend: Array(legend.prefix(2))),
            EnsFrame(headline: outCuts.count == 1 ? "One random cut isolates the {w:outlier}." : "\(outCuts.count) random cuts isolate the {w:outlier}.",
                     body: "It sits far from everything, so almost any cut separates it.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts(outlier), lines: cutLines(outCuts))),
                     action: "Isolate a Cluster Point", legend: legend,
                     chips: [LabChip(key: "outlier", value: "\(outCuts.count)", tintColor: c1)]),
            EnsFrame(headline: "This tree needs {\(inCuts.count) cuts} to isolate a point from the middle of the cluster.",
                     body: "The outlier fell out after \(outCuts.count). Averaged over 100 trees, short paths give scores near 1 and long paths push scores below 0.5.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: pts(centre), lines: cutLines(inCuts))),
                     action: "Score All Points", formula: formula, legend: legend,
                     chips: [LabChip(key: "cuts here", value: "\(inCuts.count)", tint: .active), LabChip(key: "outlier", value: "\(outCuts.count)", tintColor: c1)]),
            EnsFrame(headline: "Averaged over 100 trees the outliers score {w:\(ex(min(scores[outlier], scores[n - 1])))}+, the cluster at most \(ex(clusterMax)).",
                     body: "Anything well above 0.5 is flagged. In scikit-learn, contamination sets where that threshold falls.",
                     scene: .plane(PlaneScene(bounds: planeBounds, points: isoData.enumerated().map { i, p in
                         PPoint(x: p.x, y: p.y, cls: scores[i] > 0.6 ? 1 : 0, tag: scores[i] > 0.6 ? ex(scores[i]) : nil)
                     })),
                     action: "Start Over", formula: formula,
                     legend: [(c1, .dot, "Score above 0.6"), (c0, .dot, "Normal")],
                     chips: [LabChip(key: "trees", value: "100"), LabChip(key: "flagged", value: "\(scores.filter { $0 > 0.6 }.count)", tintColor: c1)]),
        ]
    })
}

// MARK: - Lab

private func ensembleLab(_ topicId: String) -> EnsLab {
    switch topicId {
    case "gradient_boosting": gradientBoostingLab()
    case "extra_trees": extraTreesLab()
    case "bagging": baggingLab()
    case "voting": votingLab()
    case "stacking": stackingLab()
    case "xgboost": xgboostLab()
    case "adaboost": adaBoostLab()
    case "lightgbm": lightGbmLab()
    case "isolation_forest": isolationForestLab()
    default: randomForestLab()
    }
}

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class EnsModel {
    let lab: EnsLab
    var tab: Int { didSet { reload() } }
    var param: Int { didSet { reload() } }
    private(set) var frames: [EnsFrame]
    let playback: PlaybackState

    init(lab: EnsLab) {
        self.lab = lab
        let tab = lab.startTab, param = lab.param?.initial ?? 0
        self.tab = tab
        self.param = param
        let frames = lab.frames(param, tab)
        self.frames = frames
        playback = PlaybackState(stepCount: frames.count, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        let step = UserDefaults.standard.integer(forKey: "openStep")
        if step > 0 { playback.index = min(step, frames.count) - 1 }
        #endif
    }

    private func reload() {
        frames = lab.frames(param, tab)
        // A new tab restarts the story; a new parameter value keeps the step.
        if lab.control == .track { playback.load(stepCount: frames.count) }
    }

    var frame: EnsFrame { frames[min(playback.index, frames.count - 1)] }

    func reset() {
        tab = lab.startTab
        param = lab.param?.initial ?? 0
        playback.jump(to: 0)
    }
}

struct EnsembleStoryLab: View {
    @State private var model: EnsModel
    @Environment(\.labDock) private var dock
    @Environment(\.palette) private var palette

    init(topicId: String) {
        _model = State(initialValue: EnsModel(lab: ensembleLab(topicId)))
    }

    var body: some View {
        let lab = model.lab
        let frame = model.frame
        VStack(alignment: .leading, spacing: 0) {
            if !lab.tabs.isEmpty && lab.control != .query {
                LabSegments(labels: lab.tabs, selected: $model.tab).padding(.bottom, 14)
            }
            LabCard {
                switch frame.scene {
                case .plane(let s): PlaneView(scene: s)
                case .curve(let s): CurveView(scene: s)
                case .tree(let s): EnsTreeView(scene: s)
                case .folds(let rows): FoldView(rows: rows)
                }
                if !frame.formula.isEmpty { EnsFormula(lines: frame.formula).padding(.top, 12) }
                StoryLegendRow(items: frame.legend.map { ($0.color ?? palette.primary, $0.style, $0.label) }).padding(.top, 14)
            }
            if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            if lab.control == .track {
                PlaybackTransport(state: model.playback, captions: model.frames.map { storyPlain($0.headline) },
                                  action: { [model] in model.frames[min(max($0, 0), model.frames.count - 1)].action })
            } else if dock == nil {
                Divider().padding(.top, 16)
                EnsControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            if lab.control != .track { dock.controls = { AnyView(EnsControls(model: model)) } }
            if lab.navReset { dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.reset() } }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct EnsControls: View {
    @Bindable var model: EnsModel

    var body: some View {
        let lab = model.lab
        let playback = model.playback
        VStack(spacing: 14) {
            switch lab.control {
            case .stepper:
                if let p = lab.param {
                    LabParamStepper(param: LabParam(tab: p.name, name: p.name, symbol: p.symbol, text: p.format(p.values[model.param]),
                                                    canDecrease: model.param > 0, canIncrease: model.param < p.values.count - 1)) { d in
                        model.param = min(max(model.param + d, 0), p.values.count - 1)
                    }
                }
                LabBackActionRow(action: model.frame.action, backEnabled: playback.index > 0,
                                 onBack: { playback.stepBack() },
                                 onAction: { if playback.atEnd { playback.jump(to: 0) } else { playback.stepForward() } })
            case .query:
                LabSegments(labels: lab.tabs, selected: $model.tab)
                LabButton(label: model.frame.action, primary: true) { playback.jump(to: (playback.index + 1) % model.frames.count) }
            case .track:
                EmptyView()
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

private struct EnsFormula: View {
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

private func dot(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private struct PlaneView: View {
    let scene: PlaneScene
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            Canvas { ctx, size in
                let b = scene.bounds
                func px(_ x: Double) -> CGFloat { CGFloat((x - b.x0) / (b.x1 - b.x0)) * size.width }
                func py(_ y: Double) -> CGFloat { size.height - CGFloat((y - b.y0) / (b.y1 - b.y0)) * size.height }
                func tint(_ cls: Int) -> Color { (cls == 1 ? c1 : c0).opacity(0.16) }
                for r in scene.regions {
                    ctx.fill(Path(CGRect(x: px(r.x0), y: py(r.y1), width: px(r.x1) - px(r.x0), height: py(r.y0) - py(r.y1))), with: .color(tint(r.cls)))
                }
                if let f = scene.field {
                    let cols = 48, rows = 30
                    let cw = size.width / CGFloat(cols), ch = size.height / CGFloat(rows)
                    var zero = Path(), one = Path()
                    for i in 0..<cols {
                        for j in 0..<rows {
                            let x = b.x0 + (b.x1 - b.x0) * (Double(i) + 0.5) / Double(cols)
                            let y = b.y1 - (b.y1 - b.y0) * (Double(j) + 0.5) / Double(rows)
                            let rect = CGRect(x: CGFloat(i) * cw, y: CGFloat(j) * ch, width: cw + 0.5, height: ch + 0.5)
                            if f(x, y) == 1 { one.addRect(rect) } else { zero.addRect(rect) }
                        }
                    }
                    ctx.fill(zero, with: .color(tint(0)))
                    ctx.fill(one, with: .color(tint(1)))
                }
                for l in scene.lines {
                    var p = Path()
                    p.move(to: CGPoint(x: px(l.x0), y: py(l.y0)))
                    p.addLine(to: CGPoint(x: px(l.x1), y: py(l.y1)))
                    ctx.stroke(p, with: .color(l.color ?? palette.primary), style: StrokeStyle(lineWidth: l.width, dash: l.dash ? [4, 4] : []))
                }
                if scene.curve.count > 1 {
                    var p = Path()
                    for (i, pt) in scene.curve.enumerated() {
                        let c = CGPoint(x: px(pt.0), y: py(pt.1))
                        if i == 0 { p.move(to: c) } else { p.addLine(to: c) }
                    }
                    ctx.stroke(p, with: .color(palette.primary), lineWidth: 3)
                }
                for p in scene.points {
                    let c = CGPoint(x: px(p.x), y: py(p.y))
                    let r = 5.5 * p.scale
                    let color = p.cls == 1 ? c1 : c0
                    switch p.mark {
                    case .normal:
                        ctx.fill(dot(c, r), with: .color(color))
                        ctx.stroke(dot(c, r), with: .color(palette.surface), lineWidth: 1)
                    case .faded: ctx.fill(dot(c, r), with: .color(color.opacity(0.3)))
                    case .hollow: ctx.stroke(dot(c, r), with: .color(SimColors.grey), lineWidth: 1.5)
                    case .grey: ctx.fill(dot(c, r), with: .color(mutedGrey))
                    }
                    if let ring = p.ring { ctx.stroke(dot(c, r + 4), with: .color(ring), lineWidth: 2) }
                    if let tag = p.tag {
                        ctx.draw(Text(tag).font(AppFont.sans(10, .bold)).foregroundColor(.white), at: CGPoint(x: c.x + r, y: c.y - r - 4), anchor: .bottomLeading)
                    }
                }
                if let q = scene.query {
                    let c = CGPoint(x: px(q.0), y: py(q.1))
                    ctx.fill(dot(c, 7), with: .color(SimColors.active))
                    ctx.stroke(dot(c, 12), with: .color(SimColors.active), lineWidth: 2)
                }
            }
            .aspectRatio(1.6, contentMode: .fit)
            .modifier(Stage())
            if let tiles = scene.tiles {
                HStack(spacing: 6) { ForEach(tiles.indices, id: \.self) { VoteTileView(tile: tiles[$0]) } }.padding(.top, 12)
            }
            if let models = scene.models {
                VStack(spacing: 10) { ForEach(models.indices, id: \.self) { ModelRowView(row: models[$0]) } }.padding(.top, 12)
            }
        }
    }
}

private struct VoteTileView: View {
    let tile: VoteTile
    @Environment(\.palette) private var palette

    var body: some View {
        let fill: Color = tile.value == nil ? SimColors.tint : tile.value == 1 ? c1.opacity(0.2) : c0.opacity(0.2)
        let ink: Color = tile.value == nil ? palette.muted : tile.value == 1 ? pinkInk : StoryTone.path.ink(palette)
        VStack(spacing: 1) {
            Text(tile.label).font(AppFont.sans(11)).foregroundStyle(palette.muted)
            Text(tile.value.map(String.init) ?? "—").font(AppFont.mono(15, .bold)).foregroundStyle(ink)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 48)
        .background(fill, in: RoundedRectangle(cornerRadius: 9))
        .overlay { if tile.current { RoundedRectangle(cornerRadius: 9).strokeBorder(SimColors.active, lineWidth: 1.5) } }
    }
}

private struct ModelRowView: View {
    let row: ModelRow
    @Environment(\.palette) private var palette

    var body: some View {
        let cls = row.p1 >= 0.5 ? 1 : 0
        HStack(spacing: 0) {
            Text(row.name).font(AppFont.sans(15, .bold)).frame(width: 76, alignment: .leading)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(c0.opacity(0.35))
                    Rectangle().fill(c1).frame(width: geo.size.width * CGFloat(min(max(row.p1, 0), 1)))
                    Rectangle().fill(Color.white.opacity(0.85)).frame(width: 2).offset(x: geo.size.width / 2 - 1)
                }
                .clipShape(Capsule())
            }
            .frame(height: 12)
            Text(ex(row.p1)).font(AppFont.mono(14)).padding(.leading, 12)
            Text("→\(cls)").font(AppFont.mono(13)).foregroundStyle(cls == 1 ? pinkInk : StoryTone.path.ink(palette)).padding(.leading, 6)
        }
    }
}

private struct CurveView: View {
    let scene: CurveScene
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let pad: CGFloat = 14
            let yLo = -0.05, yHi = 1.1
            func px(_ x: Double) -> CGFloat { pad + CGFloat(x) * (size.width - 2 * pad) }
            func py(_ y: Double) -> CGFloat { size.height - pad - CGFloat((y - yLo) / (yHi - yLo)) * (size.height - 2 * pad) }
            ctx.line(CGPoint(x: 0, y: py(0.5)), CGPoint(x: size.width, y: py(0.5)), color: palette.outline, width: 1)
            func step(_ f: (Double) -> Double) -> Path {
                var p = Path()
                for i in 0...200 {
                    let x = Double(i) / 200
                    let c = CGPoint(x: px(x), y: py(f(x)))
                    if i == 0 { p.move(to: c) } else { p.addLine(to: c) }
                }
                return p
            }
            if let t = scene.split {
                var p = Path(); p.move(to: CGPoint(x: px(t), y: 0)); p.addLine(to: CGPoint(x: px(t), y: size.height))
                ctx.stroke(p, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
            }
            for i in scene.xs.indices {
                let x = scene.xs[i]
                ctx.line(CGPoint(x: px(x), y: py(scene.ys[i])), CGPoint(x: px(x), y: py(scene.f(x))), color: SimColors.red, width: 1.5)
            }
            if let prev = scene.prev { ctx.stroke(step(prev), with: .color(SimColors.grey), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])) }
            ctx.stroke(step(scene.f), with: .color(palette.primary), lineWidth: 3)
            for i in scene.xs.indices { ctx.fill(dot(CGPoint(x: px(scene.xs[i]), y: py(scene.ys[i])), 4.5), with: .color(SimColors.idle)) }
        }
        .aspectRatio(1.6, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct EnsTreeView: View {
    let scene: TreeScene
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let texts = scene.nodes.map { n -> GraphicsContext.ResolvedText in
                let ink: Color = switch n.tone {
                case .current: StoryTone.active.ink(palette)
                case .kept: StoryTone.done.ink(palette)
                case .pruned: StoryTone.warn.ink(palette)
                case .open: palette.onSurface.opacity(0.85)
                }
                return ctx.resolve(Text(n.text).font(AppFont.mono(13, .bold)).foregroundColor(ink))
            }
            let widths = texts.map { $0.measure(in: size).width + 24 }
            let h: CGFloat = 34
            let padX = (widths.max() ?? 0) / 2 + 8
            let centres = scene.nodes.map { CGPoint(x: padX + $0.x * (size.width - 2 * padX), y: $0.y * size.height) }
            for (a, b, dashed) in scene.edges {
                var p = Path()
                p.move(to: CGPoint(x: centres[a].x, y: centres[a].y + h / 2))
                p.addLine(to: CGPoint(x: centres[b].x, y: centres[b].y - h / 2))
                ctx.stroke(p, with: .color(palette.muted.opacity(0.7)), style: StrokeStyle(lineWidth: 1.5, dash: dashed ? [4, 4] : []))
            }
            for (i, n) in scene.nodes.enumerated() {
                let (fill, stroke): (Color, Color) = switch n.tone {
                case .current: (SimColors.active.opacity(0.18), SimColors.active)
                case .kept: (SimColors.green.opacity(0.18), SimColors.green)
                case .pruned: (SimColors.red.opacity(0.15), SimColors.red)
                case .open: (SimColors.tint, palette.muted.opacity(0.5))
                }
                let rect = CGRect(x: centres[i].x - widths[i] / 2, y: centres[i].y - h / 2, width: widths[i], height: h)
                let box = Path(roundedRect: rect, cornerRadius: 8)
                ctx.fill(box, with: .color(fill))
                ctx.stroke(box, with: .color(stroke), style: StrokeStyle(lineWidth: 1.5, dash: n.dashed ? [4, 3] : []))
                ctx.draw(texts[i], at: centres[i])
                if let caption = n.caption {
                    ctx.draw(Text(caption).font(AppFont.mono(11)).foregroundColor(n.captionTone == .current ? StoryTone.active.ink(palette) : palette.muted),
                             at: CGPoint(x: centres[i].x, y: centres[i].y + h / 2 + 3), anchor: .top)
                }
            }
        }
        .aspectRatio(1.45, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct FoldView: View {
    let rows: [FoldRow]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Text("5-fold out-of-fold predictions")
                Spacer()
                Text("1-NN base model")
            }
            .font(AppFont.sans(13)).foregroundStyle(palette.muted)
            ForEach(rows.indices, id: \.self) { i in
                let row = rows[i]
                HStack(spacing: 0) {
                    Text(row.label).font(AppFont.sans(14, row.current ? .bold : .regular))
                        .foregroundStyle(row.current ? StoryTone.active.ink(palette) : palette.onSurface.opacity(0.85))
                        .frame(width: 62, alignment: .leading)
                    HStack(spacing: 5) {
                        ForEach(row.cells.indices, id: \.self) { c in
                            let color: Color = switch row.cells[c] {
                            case .held: SimColors.active
                            case .train: SimColors.blue.opacity(0.55)
                            case .predicted: SimColors.green.opacity(0.75)
                            case .diagonal: palette.muted.opacity(0.3)
                            case .idle: SimColors.tint
                            }
                            RoundedRectangle(cornerRadius: 4).fill(color).frame(height: 18)
                        }
                    }
                    Text(row.result).font(AppFont.mono(14)).foregroundStyle(row.resultTone?.ink(palette) ?? palette.muted)
                        .frame(width: 48, alignment: .trailing)
                }
            }
        }
    }
}
