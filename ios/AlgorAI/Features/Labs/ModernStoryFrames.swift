import SwiftUI

// Port of ModernStoryFrames.kt: KANs, neural ODEs, GCNs, GATs, capsule networks, Siamese networks, layer
// and group normalisation, early stopping and data augmentation. Every fit, routing round and accuracy is
// run here with the same seeds and the same plain LCG as on Android.

let modernStoryTopicIds: Set<String> = [
    "kan", "neural_odes", "gcn", "gat", "capsule_networks", "siamese_networks",
    "layer_normalization", "group_normalization", "early_stopping", "data_augmentation",
]

func modernLab(_ topicId: String) -> DkLab? {
    switch topicId {
    case "kan": kanLab()
    case "neural_odes": odeLab()
    case "gcn": gcnLab()
    case "gat": gatLab()
    case "capsule_networks": capsuleLab()
    case "siamese_networks": siameseLab()
    case "layer_normalization": layerNormLab()
    case "group_normalization": groupNormLab()
    case "early_stopping": earlyStopLab()
    case "data_augmentation": augmentLab()
    default: nil
    }
}

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }
private func pct(_ share: Double, _ d: Int = 0) -> String { n(share * 100, d) + "%" }
private func legend(_ ink: DkInk, _ label: String, _ style: SwatchStyle = .fill) -> DkLegend { DkLegend(ink: ink, style: style, label: label) }

private func stepActions(_ frames: [DkFrame], _ action: String) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = i == frames.count - 1 ? "Start Over" : action; return f }
}

private func plural(_ k: Int) -> String { k == 1 ? "" : "s" }

private struct ModRng {
    var s: Int64
    init(_ seed: Int64) { s = seed }
    mutating func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
    mutating func g() -> Double { let a = max(u(), 1e-12); let b = u(); return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b) }
}

private func avg(_ v: [Double]) -> Double { v.reduce(0, +) / Double(v.count) }

// MARK: - KAN vs MLP

private let kanX: [Double] = (0..<100).map { -2 + 4.0 * Double($0) / 99 }
private func kanTarget(_ x: Double) -> Double { sin(5 * x) * exp(-x * x / 2) }
private let kanY = kanX.map { kanTarget($0) }

private func rmse(_ pred: [Double]) -> Double { (pred.indices.reduce(0.0) { $0 + pow(pred[$1] - kanY[$1], 2) } / Double(pred.count)).squareRoot() }

/// A 1-6-1 tanh MLP (19 weights) trained by full-batch Adam; its predictions on kanX.
private func trainMlp(_ seed: Int64) -> [Double] {
    var rng = ModRng(seed)
    let h = 6
    var p = [Double](repeating: 0, count: 3 * h + 1)
    for j in 0..<h {
        p[j] = rng.g() * 3
        p[h + j] = rng.g()
        p[2 * h + j] = rng.g() * 0.5
    }
    var m = [Double](repeating: 0, count: p.count), v = [Double](repeating: 0, count: p.count)
    var g = [Double](repeating: 0, count: p.count)
    var acts = [Double](repeating: 0, count: h)
    let count = Double(kanX.count)
    for t in 1...3000 {
        for k in g.indices { g[k] = 0 }
        for i in kanX.indices {
            let x = kanX[i]
            var out = 0.0
            for j in 0..<h { acts[j] = tanh(p[j] * x + p[h + j]); out += p[2 * h + j] * acts[j] }
            let err = out + p[3 * h] - kanY[i]
            let d = 2 * err / count
            for j in 0..<h {
                let back = d * p[2 * h + j] * (1 - acts[j] * acts[j])
                g[j] += back * x
                g[h + j] += back
                g[2 * h + j] += d * acts[j]
            }
            g[3 * h] += d
        }
        let c1 = 1 - pow(0.9, Double(t)), c2 = 1 - pow(0.999, Double(t))
        for k in p.indices {
            m[k] = 0.9 * m[k] + 0.1 * g[k]
            v[k] = 0.999 * v[k] + 0.001 * g[k] * g[k]
            p[k] -= 0.02 * (m[k] / c1) / ((v[k] / c2).squareRoot() + 1e-8)
        }
    }
    return kanX.map { x in (0..<h).reduce(0.0) { $0 + p[2 * h + $1] * tanh(p[$1] * x + p[h + $1]) } + p[3 * h] }
}

/// A piecewise-linear spline on `knots` uniform knots, fitted by least squares; returns knot values.
private func fitSpline(_ knots: Int) -> [Double] {
    let step = 4.0 / Double(knots - 1)
    func basis(_ x: Double, _ k: Int) -> Double { max(0, 1 - abs((x - (-2 + Double(k) * step)) / step)) }
    var a = [[Double]](repeating: [Double](repeating: 0, count: knots), count: knots)
    var b = [Double](repeating: 0, count: knots)
    for (i, x) in kanX.enumerated() {
        for r in 0..<knots {
            let br = basis(x, r)
            if br == 0 { continue }
            b[r] += br * kanY[i]
            for c in 0..<knots { a[r][c] += br * basis(x, c) }
        }
    }
    for col in 0..<knots {
        let piv = (col..<knots).max { abs(a[$0][col]) < abs(a[$1][col]) }!
        a.swapAt(col, piv); b.swapAt(col, piv)
        for r in (col + 1)..<knots where col + 1 < knots {
            let f = a[r][col] / a[col][col]
            for c in col..<knots { a[r][c] -= f * a[col][c] }
            b[r] -= f * b[col]
        }
    }
    var sol = [Double](repeating: 0, count: knots)
    for r in stride(from: knots - 1, through: 0, by: -1) {
        var s = b[r]
        for c in (r + 1)..<max(r + 1, knots) { s -= a[r][c] * sol[c] }
        sol[r] = s / a[r][r]
    }
    return sol
}

private let kanRuns: (mlps: [[Double]], spline: [Double], knots: [Double]) = {
    let mlps = [Int64(1), 2, 3].map { trainMlp($0) }
    let knots = fitSpline(20)
    let step = 4.0 / 19
    let spline = kanX.map { x in knots.indices.reduce(0.0) { $0 + knots[$1] * max(0, 1 - abs((x - (-2 + Double($1) * step)) / step)) } }
    return (mlps, spline, knots)
}()

private func kanLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["MLP", "KAN", "Both"], initialTab: 2) { tab, _ in
        let (mlps, spline, knots) = kanRuns
        let mlpErr = mlps.map { rmse($0) }
        let kanErr = rmse(spline)
        let lo = mlpErr.min()!, hi = mlpErr.max()!
        let target = DkLine(pts: kanX.map { DkP($0, kanTarget($0)) }, ink: .grey)
        let mlpLine = DkLine(pts: kanX.enumerated().map { DkP($0.element, mlps[0][$0.offset]) }, ink: .pink)
        let kanLine = DkLine(pts: kanX.enumerated().map { DkP($0.element, spline[$0.offset]) }, ink: .blue, dashed: true)
        let knotDots = knots.enumerated().map { DkDot(p: DkP(-2 + Double($0.offset) * 4.0 / 19, $0.element), ink: .blue, r: 3.5) }
        func plot(_ showKnots: Bool = false) -> DkStage {
            .plot(DkPlot(xr: (-2.05, 2.05), yr: (-1.15, 1.15), yTicks: [], xLeft: "x = −2", xRight: "+2",
                         lines: [target] + (tab != 1 ? [mlpLine] : []) + (tab != 0 ? [kanLine] : []),
                         dots: showKnots && tab != 0 ? knotDots : [], rules: [DkRule(y: 0, ink: .grey, thin: true)], axis: false))
        }
        let header = "fit to sin(5x)·e^(−x²/2) · 100 points"
        let lg = [legend(.grey, "Target", .line)] + (tab != 1 ? [legend(.pink, "MLP 1-6-1, seed 1", .line)] : []) + (tab != 0 ? [legend(.blue, "KAN edge, 20 knots", .dashedLine)] : [])
        let mlpLine1 = "MLP 1-6-1 (19 weights), 3 seeds · RMSE \(n(lo, 3))–\(n(hi, 3))"
        let kanLine1 = "KAN edge, 20 knots · RMSE {\(n(kanErr, 3))} every time"
        let similar = hi < kanErr * 3 && lo > kanErr / 3
        let frames = [
            DkFrame(header: header, stage: plot(), legend: lg,
                    formula: tab == 0 ? ["h = tanh(w·x + b) for 6 units, y = Σ v·h + c", "seed 1 · RMSE {\(n(mlpErr[0], 3))}"]
                        : tab == 1 ? ["y = φ(x), φ a spline through 20 knots", "RMSE {\(n(kanErr, 3))}"] : [mlpLine1, kanLine1],
                    headline: tab == 0 ? "A 1-6-1 MLP with {19} weights fits it to RMSE \(n(mlpErr[0], 3))."
                        : tab == 1 ? "One learnable spline edge, {20} numbers, fits it to RMSE \(n(kanErr, 3))."
                        : similar ? "Same budget, similar error, but the KAN fit is {the same every time}."
                        : "Same budget, and the KAN fit is {\(n(lo / kanErr, 1))×} closer and the same every time.",
                    body: tab == 0 ? "Fixed tanh curves on the nodes, learned weights on the edges: it has to bend six S-curves into four wiggles."
                        : tab == 1 ? "A KAN moves the learning onto the edge: the curve itself is the parameter, a value at each knot."
                        : "Its learnable part is the spline on the edge, so fitting it is least squares. The MLP's error varies \(n(hi / lo, 1))× across random seeds."),
            DkFrame(header: header, stage: plot(), legend: lg, formula: [mlpLine1, kanLine1],
                    headline: tab == 1 ? "The spline fit is {least squares}: no seed, no learning rate." : "Three seeds give RMSE {\(n(lo, 3))–\(n(hi, 3))}.",
                    body: tab == 1 ? "With the knots fixed the problem is linear in the knot values, so there is exactly one best answer."
                        : "Gradient descent on a non-convex loss lands somewhere different from each random start."),
            DkFrame(header: header, stage: plot(true), legend: lg + (tab != 0 ? [legend(.blue, "Knot", .dot)] : []),
                    formula: ["KAN: a learned φ on every edge, sums at nodes", "MLP: a fixed activation on every node, weights on edges"],
                    headline: "A KAN edge is a curve you can {read off}.",
                    body: "That interpretability is the main claim for KANs. The cost: splines on every edge make large KANs slower to train than MLPs."),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - Neural ODEs

private let odeSteps = [1, 2, 4, 8, 16, 32]

private func odeLab() -> DkLab {
    DkLab(control: .stepperOnly, stepper: DkStepper(caption: "steps", values: odeSteps.map(Double.init), initial: odeSteps.firstIndex(of: 4)!) { n($0, 0) }) { _, p in
        let steps = odeSteps[p]
        let h = 2.0 / Double(steps)
        let euler = (0...steps).map { DkP(Double($0) * h, pow(1 - h, Double($0))) }
        let rkFactor = 1 - h + h * h / 2 - pow(h, 3) / 6 + pow(h, 4) / 24
        let rk = (0...steps).map { DkP(Double($0) * h, pow(rkFactor, Double($0))) }
        let exact = exp(-2.0)
        let eErr = abs(euler.last!.y - exact), rErr = abs(rk.last!.y - exact)
        let exactLine = DkLine(pts: (0...100).map { DkP(Double($0) * 0.02, exp(-Double($0) * 0.02)) }, ink: .green)
        func plot(_ withRk: Bool) -> DkStage {
            .plot(DkPlot(xr: (0, 2), yr: (-0.04, 1.05), yTicks: [(1, "1"), (0.5, "0.5"), (0, "0")], xLeft: "t = 0", xRight: "t = 2",
                         lines: [exactLine, DkLine(pts: euler, ink: .pink)],
                         dots: euler.map { DkDot(p: $0, ink: .pink, r: 3.5) } + (withRk ? rk.map { DkDot(p: $0, ink: .violet, r: 4) } : []), axis: false))
        }
        let header = "dz/dt = −z, z(0) = 1"
        let lg = [legend(.green, "Exact e^−t", .line), legend(.pink, "Euler = \(steps) residual block\(plural(steps))"), legend(.violet, "RK4")]
        var hText = n(h, h < 0.1 ? 4 : 2)
        if hText.contains(".") { while hText.hasSuffix("0") { hText.removeLast() }; if hText.hasSuffix(".") { hText.removeLast() } }
        let eulerLine = "Euler: z ← z + h·f(z) = z·(1 − \(hText)) → z(2) = \(n(euler.last!.y, 4))"
        return [
            DkFrame(header: header, stage: plot(false), legend: Array(lg.prefix(2)), formula: [eulerLine, "exact e^−2 = \(n(exact, 4))"],
                    headline: "Each residual block is one {Euler step}: z ← z + h·f(z).",
                    body: "A ResNet with \(steps) block\(plural(steps)) follows the curve in \(steps) straight jump\(plural(steps)) of size h = \(n(h, 3))."),
            DkFrame(header: header, stage: plot(true), legend: lg, formula: [eulerLine, "exact \(n(exact, 4)) · Euler error \(n(eErr, 4)) · RK4 {\(n(rErr, 5))}"],
                    headline: "\(steps == 1 ? "One ResNet-style step misses" : "\(steps) ResNet-style steps miss") by {\(pct(eErr / exact))}; RK4 by \(pct(rErr / exact, 1)).",
                    body: "x + f(x) is one Euler step. A neural ODE learns f and lets an adaptive solver choose how many steps to take."),
            DkFrame(header: header, stage: plot(true), legend: lg, formula: ["depth → continuous time: dz/dt = f(z, t; θ)", "adjoint method: memory constant in the number of steps"],
                    headline: "A neural ODE has {no fixed depth}.",
                    body: "The solver takes as many steps as accuracy needs, more where the dynamics change fast. Gradients come from solving a second ODE backwards."),
        ]
    }
}

// MARK: - Graphs: GCN and GAT

private let gPos: [(CGFloat, CGFloat)] = [(0.12, 0.12), (0.12, 0.88), (0.45, 0.5), (0.62, 0.5), (0.92, 0.12), (0.92, 0.88)]
private let gEdges = [(0, 1), (0, 2), (1, 2), (2, 3), (3, 4), (3, 5), (4, 5)]
private let gX = [1.96, 2.11, 1.80, -2.09, -1.95, -2.02]

private func neighbours(_ i: Int) -> [Int] { gEdges.compactMap { $0.0 == i ? $0.1 : $0.1 == i ? $0.0 : nil } }
private func deg(_ i: Int) -> Int { neighbours(i).count + 1 }

private func gcnStep(_ x: [Double]) -> [Double] {
    x.indices.map { i in ([i] + neighbours(i)).reduce(0.0) { $0 + x[$1] / (Double(deg(i)) * Double(deg($1))).squareRoot() } }
}

private func gcnLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let h1 = gcnStep(gX), h2 = gcnStep(h1)
        func stage(_ values: [Double], _ hot: Int?) -> DkStage {
            .graph(DkGraph(nodes: gPos.enumerated().map { i, p in DkGNode(x: p.0, y: p.1, label: "\(i)", value: n(values[i]), ink: i < 3 ? .blue : .orange, ring: i == hot) },
                           edges: gEdges.map { DkGEdge(a: $0.0, b: $0.1, hot: hot != nil && ($0.0 == hot || $0.1 == hot), weight: 0.4) }))
        }
        let subs = Array("₀₁₂₃₄₅")
        func sumLine(_ i: Int) -> [String] {
            let terms = ([i] + neighbours(i)).map { j in "\(n(1 / (Double(deg(i)) * Double(deg(j))).squareRoot(), 3))·\(n(gX[j]))" }.joined(separator: " + ")
            return ["h\(subs[i]) = \(terms)", "= {\(n(h1[i]))} · weights 1/√(dᵢdⱼ) incl. self-loop"]
        }
        func gap(_ v: [Double]) -> Double { avg(Array(v.prefix(3))) - avg(Array(v.suffix(3))) }
        let header = "feature 1 after one GCN layer"
        let lg = [legend(.blue, "Triangle A"), legend(.orange, "Triangle B"), legend(.yellow, "Edges into the node", .line)]
        let frames = [
            DkFrame(header: "feature 1 before any layer", stage: stage(gX, nil), legend: Array(lg.prefix(2)),
                    formula: ["x = [" + gX.map { n($0) }.joined(separator: ", ") + "]", "two triangles joined by the bridge 2 — 3"],
                    headline: "Two {triangles} share one bridge edge.",
                    body: "Triangle A's nodes carry about +2, triangle B's about −2. A GCN layer lets each node look at its neighbours."),
            DkFrame(header: "\(header) · node 0 highlighted", stage: stage(h1, 0), legend: lg, formula: sumLine(0),
                    headline: "Node 0 averages with its triangle: {\(n(gX[0])) → \(n(h1[0]))}.",
                    body: "All its neighbours agree with it, so averaging changes little."),
            DkFrame(header: "\(header) · node 2 highlighted", stage: stage(h1, 2), legend: lg, formula: sumLine(2),
                    headline: "The bridge node 2 is pulled toward B: {\(n(gX[2])) → \(n(h1[2]))}.",
                    body: "Each node averages itself with its neighbours. Within-triangle nodes grow alike; across the bridge the gap between the triangles' means shrinks from \(n(gap(gX))) to \(n(gap(h1)))."),
            DkFrame(header: "\(header) · node 3 highlighted", stage: stage(h1, 3), legend: lg, formula: sumLine(3),
                    headline: "Node 3 is pulled toward A just as much: {\(n(gX[3])) → \(n(h1[3]))}.",
                    body: "The normalisation 1/√(dᵢdⱼ) weights a neighbour less when either end has many links."),
            DkFrame(header: header, stage: stage(h1, nil), legend: Array(lg.prefix(2)),
                    formula: ["H′ = D^−½ (A + I) D^−½ H W", "gap between triangle means: \(n(gap(gX))) → {\(n(gap(h1)))}"],
                    headline: "One layer, every node at once: {Â·X}.",
                    body: "It's one sparse matrix product. A learned W (identity here) then mixes features, and a nonlinearity follows."),
            DkFrame(header: "feature 1 after two GCN layers", stage: stage(h2, nil), legend: Array(lg.prefix(2)),
                    formula: ["two layers: Â²X reaches 2 hops", "gap: \(n(gap(h1))) → {\(n(gap(h2)))}"],
                    headline: "A second layer {blurs} the two triangles further.",
                    body: "Each layer widens the receptive field by one hop but also averages away differences. Stack too many and every node looks the same: oversmoothing."),
        ]
        return stepActions(frames, "Next Node")
    }
}

private func leaky(_ z: Double) -> Double { z > 0 ? z : 0.2 * z }

private func gatWeights(_ x: [Double], _ i: Int) -> [Int: Double] {
    let js = [i] + neighbours(i)
    let e = js.map { leaky(0.4 * x[i] + 0.9 * x[$0]) }
    let m = e.max()!
    let ex = e.map { exp($0 - m) }
    let s = ex.reduce(0, +)
    var out: [Int: Double] = [:]
    for (k, j) in js.enumerated() { out[j] = ex[k] / s }
    return out
}

private func gatLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["GCN", "GAT"], initialTab: 1) { tab, _ in
        let gat = tab == 1
        let centre = 2
        let moved = gX.enumerated().map { $0.offset == 3 ? $0.element + 3 : $0.element }
        func weights(_ x: [Double]) -> [Int: Double] {
            if gat { return gatWeights(x, centre) }
            var out: [Int: Double] = [:]
            for j in [centre] + neighbours(centre) { out[j] = 1 / (Double(deg(centre)) * Double(deg(j))).squareRoot() }
            return out
        }
        let before = weights(gX), after = weights(moved)
        func stage(_ w: [Int: Double], _ perturbed: Bool) -> DkStage {
            .graph(DkGraph(
                nodes: gPos.enumerated().map { i, p in DkGNode(x: p.0, y: p.1, label: "\(i)", value: w[i].map { n($0) }, ink: i == 3 && perturbed ? .green : nil, ring: i == centre) },
                edges: gEdges.map { e in
                    let other: Int? = e.0 == centre ? e.1 : e.1 == centre ? e.0 : nil
                    return DkGEdge(a: e.0, b: e.1, hot: other != nil, weight: other.flatMap { w[$0] } ?? 0)
                }))
        }
        let kind = gat ? "GAT" : "GCN"
        let gcnW = 1 / (Double(deg(centre)) * Double(deg(3))).squareRoot()
        let header = "\(gat ? "attention" : "weights") from node 2\(gat ? "" : ", fixed by degree")"
        let lg = [legend(.yellow, "Centre", .ring), legend(.green, "Perturbed neighbour"), legend(.yellow, "Edge width = weight", .line)]
        let b3 = before[3]!, a3 = after[3]!
        let frames = [
            DkFrame(header: header, stage: stage(before, false), legend: [lg[0], lg[2]],
                    formula: gat ? ["e_ij = LeakyReLU(a·[h_i ‖ h_j]) · α = softmax over neighbours", "weight on 3: {\(n(b3, 3))}"]
                        : ["w_ij = 1/√(dᵢdⱼ)", "weight on 3: {\(n(b3, 3))}"],
                    headline: gat ? "GAT {scores} each neighbour from the two nodes' features." : "GCN's weights come from {degrees} alone.",
                    body: gat ? "Node 3's feature (\(n(gX[3]))) is unlike node 2's, so its edge scores low."
                        : "Two nodes with the same number of links always get the same say, whatever they carry."),
            DkFrame(header: "\(header) after shifting node 3's feature by +3", stage: stage(after, true), legend: lg,
                    formula: ["GCN weight on 3: \(n(gcnW, 3)) before and after", "\(kind) weight on 3: \(n(b3, 3)) → {\(n(a3, 3))}"],
                    headline: gat ? "GAT moves node 3's weight from \(n(b3)) to {\(n(a3))}; GCN can't move it." : "GCN's weight on node 3 stays {\(n(gcnW, 3))}: the change is ignored.",
                    body: gat ? "Attention scores each edge from the two nodes' features, so a neighbour that changes gets a different say."
                        : "The aggregation still sees node 3's new value, but how much it counts never changes. Switch to GAT to compare."),
            DkFrame(header: "\(header) after shifting node 3's feature by +3", stage: stage(after, true), legend: lg,
                    formula: ["multi-head GAT: K heads, outputs concatenated", "cost: one score per edge, O(|E|)"],
                    headline: gat ? "Real GATs run {several heads} and concatenate them." : "GCN is {cheaper}: no scores to compute.",
                    body: gat ? "Like transformer attention restricted to the graph's edges: each head can favour a different kind of neighbour."
                        : "On graphs where neighbours really are interchangeable, the fixed weights do just as well."),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - Capsule networks: routing by agreement

private let votesA: [(Double, Double)] = [(1.0, 0.35), (0.9, 0.5), (-0.75, 0.7)]
private let votesB: [(Double, Double)] = [(-0.3, 0.4), (0.2, -0.5), (-0.9, 0.8)]

private func squash(_ s: (Double, Double)) -> (Double, Double) {
    let n2 = s.0 * s.0 + s.1 * s.1
    let k = n2 / (1 + n2) / (n2 + 1e-12).squareRoot()
    return (s.0 * k, s.1 * k)
}

private func route(_ rounds: Int) -> (shareA: [Double], vA: (Double, Double)) {
    var bA = [0.0, 0, 0], bB = [0.0, 0, 0]
    func shares() -> [Double] { (0..<3).map { 1 / (1 + exp(bB[$0] - bA[$0])) } }
    func outputs(_ c: [Double]) -> ((Double, Double), (Double, Double)) {
        var sA = (0.0, 0.0), sB = (0.0, 0.0)
        for i in 0..<3 {
            sA = (sA.0 + c[i] * votesA[i].0, sA.1 + c[i] * votesA[i].1)
            sB = (sB.0 + (1 - c[i]) * votesB[i].0, sB.1 + (1 - c[i]) * votesB[i].1)
        }
        return (squash(sA), squash(sB))
    }
    for _ in 0..<rounds {
        let (vA, vB) = outputs(shares())
        for i in 0..<3 {
            bA[i] += votesA[i].0 * vA.0 + votesA[i].1 * vA.1
            bB[i] += votesB[i].0 * vB.0 + votesB[i].1 * vB.1
        }
    }
    let c = shares()
    return (c, outputs(c).0)
}

private func capsuleLab() -> DkLab {
    DkLab(control: .stepperOnly, stepper: DkStepper(caption: "rounds", values: (0...5).map(Double.init), initial: 3) { n($0, 0) }) { _, p in
        let r = route(p), r0 = route(0)
        func len(_ v: (Double, Double)) -> Double { (v.0 * v.0 + v.1 * v.1).squareRoot() }
        let votesPlot = DkStage.plot(DkPlot(
            xr: (-0.95, 1.15), yr: (-0.15, 0.95), yTicks: [], xLeft: "", xRight: "",
            lines: votesA.enumerated().map { DkLine(pts: [DkP(0, 0), DkP($0.element.0, $0.element.1)], ink: $0.offset == 2 ? .pink : .blue) }
                + [DkLine(pts: [DkP(0, 0), DkP(r.vA.0, r.vA.1)], ink: .yellow, dashed: true)],
            axis: false))
        let rows = DkStage.rows(DkRows(rows: r.shareA.enumerated().map { i, s in
            DkRow(title: "vote \(i + 1)", meta: "", bars: [DkBar(frac: s, ink: i == 2 ? .pink : .blue, label: n(s))], inline: true)
        }, caption: "share routed to A"))
        let lg = [legend(.blue, "Agreeing votes", .line), legend(.pink, "Outlier", .line), legend(.yellow, "Output v_A", .dashedLine)]
        let shares0 = r0.shareA.map { n($0) }.joined(separator: ", "), sharesR = r.shareA.map { n($0) }.joined(separator: ", ")
        let rounds = "\(p) round\(plural(p))"
        return [
            DkFrame(header: "votes for capsule A · \(rounds) of routing", stage: votesPlot, legend: lg,
                    formula: ["û_i = W_i · u_i: each lower capsule predicts A's pose", "v_A = squash(Σ c_i·û_i) · |v_A| = {\(n(len(r.vA)))}"],
                    headline: "Two votes for A {agree}; the third points elsewhere.",
                    body: "A capsule outputs a vector: its direction is the pose, its length the probability the entity is there."),
            DkFrame(header: "votes for capsule A · share routed to A", stage: rows, legend: Array(lg.prefix(2)),
                    formula: ["b ← b + û·v · c = softmax over parents A, B", "share to A: \(shares0) → {\(sharesR)}"],
                    headline: p == 0 ? "Before routing, every vote splits {50 / 50}." : "The outlier sends only {\(pct(r.shareA[2]))} of its vote to A after \(rounds).",
                    body: "Routing-by-agreement moves each vote toward the parent it agrees with. A's length rises to \(n(len(r.vA))), against \(n(len(r0.vA))) with equal weights."),
            DkFrame(header: "votes for capsule A · \(rounds) of routing", stage: votesPlot, legend: lg,
                    formula: ["routing: iterative, no learned weights of its own", "CapsNet on MNIST: 3 rounds"],
                    headline: "Routing is {inference-time} clustering of votes.",
                    body: "It replaces max-pooling's \"keep the loudest\" with \"keep what agrees\", which preserves pose. The iterations are why capsules never scaled well."),
        ]
    }
}

// MARK: - Siamese networks

private struct SiamesePoint { let cls: Int; let s1: Double; let s2: Double; let noise: Double }

private let siamese: [SiamesePoint] = {
    var rng = ModRng(321)
    let centres: [(Double, Double)] = [(2.0, 1.5), (2.0, -1.5), (-2.0, 1.5), (-2.0, -1.5)]
    var out: [SiamesePoint] = []
    for (c, ctr) in centres.enumerated() {
        for _ in 0..<10 {
            let a = ctr.0 + 0.45 * rng.g()
            let b = ctr.1 + 0.45 * rng.g()
            let nz = 3 * rng.g()
            out.append(SiamesePoint(cls: c, s1: a, s2: b, noise: nz))
        }
    }
    return out
}()

private func siameseLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Raw", "Embedded"], initialTab: 1) { tab, _ in
        let pts = siamese
        func within(_ f: (SiamesePoint) -> Double) -> Double {
            var all: [Double] = []
            for c in 0...2 {
                let g = pts.filter { $0.cls == c }
                let m = avg(g.map(f))
                all += g.map { pow(f($0) - m, 2) }
            }
            return avg(all)
        }
        let ws = [1 / within { $0.s1 }.squareRoot(), 1 / within { $0.s2 }.squareRoot(), 1 / within { $0.noise }.squareRoot()]
        let noiseW = ws[2] / ((ws[0] + ws[1]) / 2)
        let w: [Double] = tab == 1 ? [1, 1, noiseW] : [1, 1, 1]
        func dist(_ a: SiamesePoint, _ b: SiamesePoint, _ wt: [Double]) -> Double {
            (pow(wt[0] * (a.s1 - b.s1), 2) + pow(wt[1] * (a.s2 - b.s2), 2) + pow(wt[2] * (a.noise - b.noise), 2)).squareRoot()
        }
        func oneShot(_ wt: [Double]) -> Double {
            let support = (0...3).map { c in pts.first { $0.cls == c }! }
            let queries = pts.enumerated().filter { $0.element.cls == 3 && $0.offset != pts.firstIndex { $0.cls == 3 }! }.map(\.element)
            let right = queries.filter { q in support.min { dist(q, $0, wt) < dist(q, $1, wt) }!.cls == 3 }.count
            return Double(right) / Double(queries.count)
        }
        let rawAcc = oneShot([1, 1, 1]), embAcc = oneShot([1, 1, noiseW])
        let inks: [DkInk] = [.blue, .orange, .green, .pink]
        var dots: [DkDot] = []
        for p in pts {
            let y = p.s2 + w[2] * p.noise
            if p.cls == 3 { dots.append(DkDot(p: DkP(p.s1, y), ink: .yellow, r: 5.5)) }
            dots.append(DkDot(p: DkP(p.s1, y), ink: inks[p.cls], r: 4))
        }
        let span = pts.map { abs($0.s2 + w[2] * $0.noise) }.max()! * 1.15
        let plot = DkStage.plot(DkPlot(xr: (-3.6, 3.6), yr: (-span, span), yTicks: [], xLeft: "", xRight: "", lines: [], dots: dots, axis: false))
        let header = tab == 1 ? "embedding fit on classes 0–2 · class 3 never seen" : "raw features · x = signal 1, y = signal 2 + noise"
        let lg = [legend(.blue, "Class 0"), legend(.orange, "1"), legend(.green, "2"), legend(.pink, "3, held out")]
        let frames = [
            DkFrame(header: header, stage: plot, legend: lg,
                    formula: ["3 features: 2 signal axes, 1 noisy axis (σ = 3)", "noise axis weight: {\(n(w[2]))×} the signal axes"],
                    headline: tab == 1 ? "The learned metric squeezes the {noisy axis} to \(n(noiseW))×." : "Raw distances are dominated by the {noisy axis}.",
                    body: tab == 1 ? "Both branches of a Siamese network share weights, so they embed every input the same way; training on pairs from classes 0–2 learned to ignore the noise."
                        : "The classes separate cleanly on the two signal axes, but the third axis's spread is larger than the gaps between them."),
            DkFrame(header: header, stage: plot, legend: lg,
                    formula: ["noise axis weight: \(n(noiseW))× the signal axes", "1-shot NN on class 3: raw \(pct(rawAcc)) → {embedded \(pct(embAcc))}"],
                    headline: "The unseen class is matched correctly {\(pct(tab == 1 ? embAcc : rawAcc))} of the time.",
                    body: tab == 1 ? "The shared network learned to shrink the noisy feature using classes 0–2 only. That metric transfers to a class it never trained on, which is the point of a Siamese setup."
                        : "With one example per class, nearest-neighbour matching in raw space is at the mercy of the noise."),
            DkFrame(header: header, stage: plot, legend: lg,
                    formula: ["contrastive loss: pull same-class pairs together", "push different pairs apart beyond a margin m"],
                    headline: "Siamese nets learn {a distance}, not classes.",
                    body: "That is why they handle classes they never saw: face verification, signature matching, few-shot learning."),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - Layer normalisation

private let lnRows: [[Double]] = [
    [1.0, 4.2, -3.8, 2.4, 5.6, 6.0], [1.2, 1.0, 3.9, -3.8, 6.6, 4.0],
    [0.9, 3.4, -0.7, 6.4, 3.0, 5.8], [4.4, 2.3, 4.3, 7.4, 7.5, 7.9],
]

private func stats(_ v: [Double]) -> (Double, Double) {
    let m = avg(v)
    return (m, (v.reduce(0.0) { $0 + pow($1 - m, 2) } / Double(v.count)).squareRoot())
}

private func layerNormLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["BatchNorm", "LayerNorm"], initialTab: 1) { tab, _ in
        let layer = tab == 1
        let row = 1, col = 2
        let sel = layer ? lnRows[row] : lnRows.map { $0[col] }
        let (mu, sd) = stats(sel)
        let normed = sel.map { ($0 - mu) / sd }
        func grid(_ showNormed: Bool) -> DkStage {
            var cells: [DkCell] = []
            for (r, vals) in lnRows.enumerated() {
                for (c, v) in vals.enumerated() {
                    let inSel = layer ? r == row : c == col
                    let k = layer ? c : r
                    cells.append(inSel ? DkCell(text: n(showNormed ? normed[k] : v, showNormed ? 2 : 1), tone: .heat, level: 0.55) : DkCell(text: n(v, 1), tone: .zero))
                }
            }
            return .grids(DkGrids(columns: [[DkGrid(title: "", rows: 4, cols: 6, cells: cells,
                                                     boxes: [layer ? DkBox(r0: row, c0: 0, r1: row, c1: 5) : DkBox(r0: 0, c0: col, r1: 3, c1: col)],
                                                     maxCell: 44, rowLabels: (1...4).map { "x\($0)" }, colLabels: (1...6).map { "f\($0)" }, hotRow: layer ? row : nil)]],
                                  weights: [1]))
        }
        let header = "batch of 4 · \(layer ? "LayerNorm reads one row only" : "BatchNorm reads one column across the batch")"
        let lg = [legend(.violet, "Normalised \(layer ? "row" : "column")"), legend(.slate, "Never read")]
        let what = layer ? "x2" : "f3"
        let frames = [
            DkFrame(header: header, stage: grid(false), legend: lg,
                    formula: ["μ = \(n(mu)) · σ = \(n(sd))", "over \(sel.count) values: \(layer ? "one example's features" : "one feature across the batch")"],
                    headline: "\(what) is normalised with {its own} mean and spread.",
                    body: layer ? "BatchNorm would use each column across the 4 rows, so it changes with batch size. LayerNorm works the same with a batch of 1."
                        : "Every example's f3 is shifted by the batch's mean, so one example's output depends on which others share its batch."),
            DkFrame(header: header, stage: grid(true), legend: lg,
                    formula: ["μ = \(n(mu)) · σ = \(n(sd))", "x̂ = {[" + normed.map { n($0) }.joined(separator: ", ") + "]}"],
                    headline: "After normalising, \(what) has {mean 0, std 1}.",
                    body: "A learned scale γ and shift β per feature then let the network restore whatever range it needs."),
            DkFrame(header: header, stage: grid(true), legend: lg,
                    formula: layer ? ["batch of 1: still 6 values per row", "transformers and RNNs: LayerNorm"] : ["batch of 1: one value per column → σ = 0", "CNNs with big batches: BatchNorm"],
                    headline: layer ? "LayerNorm needs {no batch}: same at training and inference." : "BatchNorm {breaks} with a batch of 1.",
                    body: layer ? "That is why every transformer uses it: sequences vary in length and batches can be tiny."
                        : "At inference it switches to running averages from training, so train and test behave differently."),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - Group normalisation

private let gnScales = [0.5, 0.5, 1.0, 1.0, 3.0, 3.0, 4.0, 4.0]

private let gnValues: [[Double]] = {
    var rng = ModRng(808)
    var out: [[Double]] = []
    for s in gnScales {
        var row: [Double] = []
        for _ in 0..<6 { row.append(s * rng.g() + 0.3 * s) }
        out.append(row)
    }
    return out
}()

private func groupNormLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["G = 1", "G = 2", "G = 8"], initialTab: 1) { tab, _ in
        let groups = [1, 2, 8][tab]
        let per = 8 / groups
        let (mu, sd) = stats(gnValues.prefix(per).flatMap { $0 })
        func groupNorm(_ c: Int, _ v: Double) -> Double {
            let g = c / per
            let (m, s) = stats(gnValues[(g * per)..<((g + 1) * per)].flatMap { $0 })
            return (v - m) / s
        }
        func grid(_ normed: Bool) -> DkStage {
            let vals = gnValues.enumerated().map { c, row in row.map { normed ? groupNorm(c, $0) : $0 } }
            let top = vals.flatMap { $0 }.map { abs($0) }.max()!
            let cells = vals.flatMap { $0 }.map { v -> DkCell in
                var t = n(v, 1)
                if t == "−0.0" { t = "0.0" }
                return DkCell(text: t, tone: v >= 0 ? .pos : .neg, level: abs(v) / top)
            }
            let side = ["G = \(groups) group\(plural(groups))", "{group 1: c0–c\(per - 1)}"] + (groups == 2 ? ["group 2: c4–c7"] : [])
                + ["", "channel scales", gnScales.map { s -> String in var t = n(s, 1); if t.hasSuffix(".0") { t.removeLast(2) }; return t }.joined(separator: " · ")]
            return .grids(DkGrids(columns: [[DkGrid(title: "", rows: 8, cols: 6, cells: cells,
                                                     boxes: (0..<groups).map { DkBox(r0: $0 * per, c0: 0, r1: ($0 + 1) * per - 1, c1: 5, dashed: $0 > 0) },
                                                     maxCell: 30, rowLabels: (0..<8).map { "c\($0)" })]],
                                  weights: [1.5], side: side))
        }
        let header = "8 channels × 6 positions · one sample"
        let lg = [legend(.yellow, "Group being normalised", .ring), legend(.blue, "Positive"), legend(.pink, "Negative")]
        let c0 = gnValues[0].map { ($0 - mu) / sd }
        let frames = [
            DkFrame(header: header, stage: grid(false), legend: lg,
                    formula: ["group 1 (\(per * 6) values): μ = \(n(mu)), σ = \(n(sd))", "c0 → {[" + c0.map { n($0, 1) }.joined(separator: ", ") + "]}"],
                    headline: groups == 1 ? "G = 1 normalises all {48 values} together: LayerNorm."
                        : groups == 8 ? "G = 8 gives every channel {its own} statistics: InstanceNorm."
                        : "Channels with similar scale share {one} mean and σ.",
                    body: "G = 1 is LayerNorm over all 48 values; G = 8 is InstanceNorm per channel. Neither needs a batch, which is why detection models use GroupNorm."),
            DkFrame(header: header, stage: grid(true), legend: lg,
                    formula: ["each group → mean 0, std 1", "within a group, channels keep their relative scale"],
                    headline: "After normalising, every group sits {on the same scale}.",
                    body: groups == 1 ? "But c0–c3 are now squeezed near 0: one σ for all channels is dominated by the loud ones."
                        : "Big-scale and small-scale channels no longer dwarf each other, yet channels inside a group keep their differences."),
            DkFrame(header: header, stage: grid(true), legend: lg,
                    formula: ["Mask R-CNN, small batches: GN beats BN", "default: G = 32 groups"],
                    headline: "GroupNorm works at {any batch size}.",
                    body: "Detection and segmentation train with 1–2 images per GPU, where BatchNorm's statistics are noise."),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - Early stopping

private let earlyRun: (train: [Double], valid: [Double]) = {
    var rng = ModRng(4242)
    let d = 40, nTrain = 30, nVal = 30
    var wTrue: [Double] = []
    for i in 0..<d { wTrue.append(i < 10 ? rng.g() : 0) }
    func data(_ rows: Int) -> ([[Double]], [Double]) {
        var x: [[Double]] = []
        for _ in 0..<rows { var r: [Double] = []; for _ in 0..<d { r.append(rng.g()) }; x.append(r) }
        var y: [Double] = []
        for r in x { var s = 0.0; for k in 0..<d { s += r[k] * wTrue[k] }; y.append(s + 1.5 * rng.g()) }
        return (x, y)
    }
    let (xt, yt) = data(nTrain)
    let (xv, yv) = data(nVal)
    var w = [Double](repeating: 0, count: d)
    func mse(_ x: [[Double]], _ y: [Double]) -> Double {
        var total = 0.0
        for i in x.indices { var s = 0.0; for k in 0..<d { s += x[i][k] * w[k] }; total += (s - y[i]) * (s - y[i]) }
        return total / Double(x.count)
    }
    var train: [Double] = [], valid: [Double] = []
    var g = [Double](repeating: 0, count: d)
    for _ in 0...1500 {
        train.append(mse(xt, yt))
        valid.append(mse(xv, yv))
        for k in 0..<d { g[k] = 0 }
        for i in 0..<nTrain {
            var s = 0.0
            for k in 0..<d { s += xt[i][k] * w[k] }
            let err = s - yt[i]
            for k in 0..<d { g[k] += 2 * err * xt[i][k] / Double(nTrain) }
        }
        for k in 0..<d { w[k] -= 0.004 * g[k] }
    }
    return (train, valid)
}()

private let patiences = [10, 20, 50, 100, 200]

private func earlyStopLab() -> DkLab {
    DkLab(control: .stepperOnly, stepper: DkStepper(caption: "patience", values: patiences.map(Double.init), initial: patiences.firstIndex(of: 50)!) { n($0, 0) }) { _, p in
        let run = earlyRun
        let pat = patiences[p]
        let best = run.valid.indices.min { run.valid[$0] < run.valid[$1] }!
        var bestSoFar = 0, stop = run.valid.count - 1
        for t in run.valid.indices {
            if run.valid[t] < run.valid[bestSoFar] { bestSoFar = t }
            if t - bestSoFar >= pat { stop = t; break }
        }
        let final = run.valid.last!
        let top = max(run.train[0], run.valid.max()!)
        func curve(_ v: [Double]) -> [DkP] { v.indices.filter { $0 % 5 == 0 }.map { DkP(Double($0), v[$0]) } }
        func plot(_ showVal: Bool, _ guide: Int?) -> DkStage {
            .plot(DkPlot(xr: (0, 1500), yr: (-top * 0.03, top * 1.03), yTicks: [(top, n(top, 0)), (top / 2, n(top / 2, 0)), (0, "0")], xLeft: "step 0", xRight: "1500",
                         lines: [DkLine(pts: curve(run.train), ink: .blue)] + (showVal ? [DkLine(pts: curve(run.valid), ink: .violet)] : []),
                         dots: guide.map { [DkDot(p: DkP(Double($0), run.valid[$0]), ink: .yellow, r: 6)] } ?? [], axis: false,
                         guide: guide.map { (Double($0), "") }))
        }
        let header = "40 features, 30 training rows · gradient descent"
        let lg = [legend(.blue, "Train", .line), legend(.violet, "Validation", .line), legend(.yellow, "Stop here", .dot)]
        return [
            DkFrame(header: header, stage: plot(false, nil), legend: Array(lg.prefix(1)),
                    formula: ["train MSE: \(n(run.train[0])) → \(n(run.train.last!))", "40 weights, only 30 equations"],
                    headline: "Training loss {keeps falling}, all the way to \(n(run.train.last!)).",
                    body: "With more features than rows, gradient descent can fit the training set almost perfectly, noise included."),
            DkFrame(header: header, stage: plot(true, best), legend: lg,
                    formula: ["best val {\(n(run.valid[best]))} at step \(best) · final \(n(final))", "train keeps falling: \(n(run.train[best])) → \(n(run.train.last!))"],
                    headline: "Stopping at step \(best) saves {\(pct(1 - run.valid[best] / final))} of the final validation loss.",
                    body: "With more features than rows, gradient descent eventually fits the noise. Validation, never used for updates, shows when that starts."),
            DkFrame(header: header, stage: plot(true, bestSoFar), legend: lg,
                    formula: ["patience \(pat): stop at step {\(stop)}, restore step \(bestSoFar)", "steps saved: \(1500 - stop) of 1500"],
                    headline: "With patience \(pat), training halts at step {\(stop)}.",
                    body: "Patience is how long to wait for a new best before giving up. Too short stops on a noisy blip; too long wastes compute. Either way, keep the best weights."),
        ]
    }
}

// MARK: - Data augmentation

private typealias Pattern = [[Double]]

private let letterL: Pattern = (0..<5).map { r in (0..<5).map { c in c == 0 || r == 4 ? 1 : 0 } }
private let letterT: Pattern = (0..<5).map { r in (0..<5).map { c in r == 0 || c == 2 ? 1 : 0 } }

private func shift(_ p: Pattern, _ dx: Int, _ dy: Int) -> Pattern {
    (0..<5).map { r in (0..<5).map { c in
        let rr = r - dy, cc = c - dx
        return rr >= 0 && rr < 5 && cc >= 0 && cc < 5 ? p[rr][cc] : 0
    } }
}

private func centroid(_ p: Pattern, _ poses: Bool) -> Pattern {
    let set = poses ? [p, shift(p, 1, 0), shift(p, -1, 0), shift(p, 0, 1), shift(p, 0, -1)] : [p]
    return (0..<5).map { r in (0..<5).map { c in set.reduce(0.0) { $0 + $1[r][c] } / Double(set.count) } }
}

private func distance(_ a: Pattern, _ b: Pattern) -> Double {
    var s = 0.0
    for r in 0..<5 { for c in 0..<5 { s += pow(a[r][c] - b[r][c], 2) } }
    return s
}

private func arrow(_ dx: Int, _ dy: Int) -> String {
    if dx == 0 && dy == 0 { return "·" }
    if dx == 0 { return dy > 0 ? "↓" : "↑" }
    if dy == 0 { return dx > 0 ? "→" : "←" }
    if dx > 0 { return dy > 0 ? "↘" : "↗" }
    return dy > 0 ? "↙" : "↖"
}

private func augmentLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["1 pose", "5 poses"], initialTab: 1) { tab, _ in
        func accuracy(_ poses: Bool) -> (Double, [String]) {
            let cl = centroid(letterL, poses), ct = centroid(letterT, poses)
            var misses: [String] = []
            var right = 0
            for (name, letter) in [("L", letterL), ("T", letterT)] {
                for dy in -1...1 {
                    for dx in -1...1 {
                        let test = shift(letter, dx, dy)
                        let guess = distance(test, cl) <= distance(test, ct) ? "L" : "T"
                        if guess == name { right += 1 } else { misses.append("\(name) \(arrow(dx, dy))") }
                    }
                }
            }
            return (Double(right) / 18, misses)
        }
        let (acc1, miss1) = accuracy(false), (acc5, miss5) = accuracy(true)
        let poses = tab == 1
        let c = centroid(letterL, poses)
        let test = shift(letterL, 1, 1)
        let stage = DkStage.grids(DkGrids(columns: [
            [DkGrid(title: "L centroid · \(poses ? 5 : 1) pose\(poses ? "s" : "")", rows: 5, cols: 5,
                    cells: c.flatMap { $0 }.map { $0 > 0 ? DkCell(text: n($0, 1), tone: .pos, level: $0) : DkCell(text: "", tone: .empty) }, maxCell: 30)],
            [DkGrid(title: "test: L shifted ↘ 1 px", rows: 5, cols: 5,
                    cells: test.flatMap { $0 }.map { $0 > 0 ? DkCell(text: "", tone: .hot) : DkCell(text: "", tone: .empty) }, maxCell: 30)],
        ], weights: [1, 1]))
        let header = "nearest-centroid classifier · classes L and T"
        let lg = [legend(.blue, "Averaged centroid"), legend(.yellow, "Test pattern")]
        let misses = poses ? miss5 : miss1
        let frames = [
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["centroid = mean of the training images", poses ? "5 poses: original + 4 one-pixel shifts" : "1 pose: just the original"],
                    headline: poses ? "Four shifted copies {smear} the centroid across nearby pixels." : "One pose gives a {sharp} centroid: exactly the training L.",
                    body: "A shifted test letter overlaps the sharp template only partly, and can land closer to the other class."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["test set: both letters at all 9 one-pixel shifts", "accuracy: 1 pose \(pct(acc1)) → 5 poses {\(pct(acc5))}"],
                    headline: poses ? "Four shifted copies lift accuracy from \(pct(acc1)) to {\(pct(acc5))}." : "With one pose, accuracy is {\(pct(acc1))}.",
                    body: poses ? "No new data was collected. The centroid now smears across nearby pixels, so a shifted L still lands closer to L than to T."
                        : "Switch to 5 poses: the same two letters, shifted by one pixel each way, added to training."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["misclassified: \(misses.isEmpty ? "none" : misses.joined(separator: ", "))", "\(misses.count) of 18 test patterns"],
                    headline: "Augmentation teaches the {invariance} the test needs.",
                    body: "Shifts, flips, crops and colour jitter each encode a change that shouldn't alter the label. Augment with the wrong one (flipping digits) and you teach a lie."),
        ]
        return stepActions(frames, "Next")
    }
}
