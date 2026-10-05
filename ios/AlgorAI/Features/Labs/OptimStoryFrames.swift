import SwiftUI

// Port of OptimStoryFrames.kt: momentum, AdaGrad, RMSprop, Adam, AdamW, learning-rate schedulers, KL
// divergence and cross-entropy. The optimisers run on the same ill-conditioned bowl ½(w₁² + 20·w₂²) or
// on fixed gradient streams; the KL fits are found by grid search.

let optimStoryTopicIds: Set<String> = [
    "momentum", "adagrad", "rmsprop", "adam", "adamw", "lr_schedulers", "kl_divergence", "cross_entropy_loss",
]

func optimLab(_ topicId: String) -> DkLab? {
    switch topicId {
    case "momentum": momentumLab()
    case "adagrad": adagradLab()
    case "rmsprop": rmspropLab()
    case "adam": adamLab()
    case "adamw": adamwLab()
    case "lr_schedulers": schedulerLab()
    case "kl_divergence": klLab()
    case "cross_entropy_loss": crossEntropyLab()
    default: nil
    }
}

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }
private func trim(_ s: String) -> String {
    var s = s
    guard s.contains(".") else { return s }
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}
private func legend(_ ink: DkInk, _ label: String, _ style: SwatchStyle = .line) -> DkLegend { DkLegend(ink: ink, style: style, label: label) }

private func stepActions(_ frames: [DkFrame]) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = i == frames.count - 1 ? "Start Over" : "Next"; return f }
}

private func logTicks(_ lo: Double, _ hi: Double) -> [(Double, String)] {
    stride(from: Int(hi.rounded(.down)), through: Int(lo.rounded(.up)), by: -1).map { k in (Double(k), k == 0 ? "1" : "1e" + (k < 0 ? "−" : "") + String(abs(k))) }
}

/// A ratio as a headline writes it: 43×, 1.58×.
private func times(_ v: Double) -> String { v >= 10 ? n(v, 0) : trim(n(v, 2)) }

// MARK: - The bowl

private let steep = 20.0
private func bowl(_ w1: Double, _ w2: Double) -> Double { 0.5 * (w1 * w1 + steep * w2 * w2) }

/// Heavy-ball momentum on the bowl: v ← β·v + ∇f, w ← w − lr·v.
private func heavyBall(_ beta: Double, _ steps: Int = 60, _ lr: Double = 0.02) -> [DkP] {
    var w1 = -8.0, w2 = 1.0, v1 = 0.0, v2 = 0.0
    var path = [DkP(w1, w2)]
    for _ in 0..<steps {
        v1 = beta * v1 + w1
        v2 = beta * v2 + steep * w2
        w1 -= lr * v1
        w2 -= lr * v2
        path.append(DkP(w1, w2))
    }
    return path
}

// MARK: - Momentum

private let betas = [0.5, 0.9, 0.99]

private func momentumLab() -> DkLab {
    DkLab(control: .tabs, tabs: betas.map { "β = \(trim(n($0)))" }, initialTab: 1) { tab, _ in
        let b = betas[tab]
        let bT = trim(n(b))
        let base = heavyBall(0.5), mine = heavyBall(b)
        let lBase = bowl(base.last!.x, base.last!.y), lMine = bowl(mine.last!.x, mine.last!.y)
        let ellipses = [2.0, 4.0, 6.5, 9.5].map { ($0, $0 / steep.squareRoot()) }
        func plot(_ showMine: Bool) -> DkStage {
            .plot(DkPlot(xr: (-9, 3), yr: (-1.7, 1.7), yTicks: [], xLeft: "", xRight: "",
                         lines: [DkLine(pts: base, ink: .grey)] + (showMine && tab != 0 ? [DkLine(pts: mine, ink: .blue)] : []),
                         dots: [DkDot(p: DkP(-8, 1), ink: .grey, r: 4), DkDot(p: DkP(0, 0), ink: .green, r: 4.5), DkDot(p: (showMine ? mine : base).last!, ink: .yellow, r: 5)],
                         axis: false, ellipses: ellipses))
        }
        let header = "f = ½(w₁² + 20·w₂²) · lr 0.02 · start (−8, 1)"
        let lgAll = [legend(.grey, "β = 0.5")] + (tab != 0 ? [legend(.blue, "β = \(bT)")] : []) + [legend(.green, "Minimum", .fill)]
        let ratio = lBase / lMine
        let frames = [
            DkFrame(header: header, stage: plot(false), legend: [legend(.grey, "β = 0.5"), legend(.green, "Minimum", .fill)],
                    formula: ["v ← β·v + ∇f · w ← w − lr·v", "β = 0.5: loss at 60 = {\(n(lBase, 3))}"],
                    headline: "With little momentum, progress along w₁ {crawls}: loss \(n(lBase, 3)) after 60 steps.",
                    body: "The bowl is 20× steeper across than along. A learning rate safe for the steep axis is tiny for the flat one."),
            DkFrame(header: header, stage: plot(true), legend: lgAll,
                    formula: ["v ← β·v + ∇f · w ← w − lr·v",
                              tab == 0 ? "loss at 60: β 0.5 → {\(n(lBase, 3))}" : "loss at 60: β 0.5 → \(n(lBase, 3)) · β \(bT) → {\(lMine < 0.01 ? n(lMine, 4) : n(lMine, 3))}"],
                    headline: tab == 0 ? "This is the baseline: β = 0.5 ends at {\(n(lBase, 3))}."
                        : ratio >= 1 ? "β = \(bT) ends {\(times(ratio))×} lower after the same 60 steps."
                        : "β = \(bT) {overshoots}: it ends \(times(1 / ratio))× higher than β = 0.5.",
                    body: tab == 0 ? "Pick β = 0.9 to see velocity build up along the flat axis."
                        : ratio >= 1 ? "On the flat w₁ axis every gradient points the same way, so velocity builds up. On the steep axis the signs alternate and largely cancel."
                        : "So much velocity is kept that the ball swings past the minimum again and again; 60 steps aren't enough to settle."),
            DkFrame(header: header, stage: plot(true), legend: lgAll,
                    formula: ["v_t = Σ βᵏ·∇f_(t−k) · up to 1/(1 − β) = \(n(1 / (1 - b), 0))× the gradient", "β = 0.9 is the usual default"],
                    headline: "β sets {how far back} the velocity remembers.",
                    body: "Steady gradients add up to \(n(1 / (1 - b), 0))× their size; oscillating ones cancel. Too close to 1 and the optimiser can't brake."),
        ]
        return stepActions(frames)
    }
}

// MARK: - AdaGrad

private func adagradLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        var dense = 0.0, sparse = 0.0
        var dRate: [Double] = [], sRate: [Double] = []
        for t in 0..<100 {
            dense += 1
            if t % 10 == 9 { sparse += 4 }
            dRate.append(1 / max(1, dense.squareRoot()))
            sRate.append(1 / max(1, sparse.squareRoot()))
        }
        func line(_ v: [Double], _ ink: DkInk, _ stairs: Bool) -> DkLine {
            var pts: [DkP] = []
            for t in v.indices {
                if stairs && t > 0 && v[t] != v[t - 1] { pts.append(DkP(Double(t), log10(v[t - 1]))) }
                pts.append(DkP(Double(t), log10(v[t])))
            }
            return DkLine(pts: pts, ink: ink)
        }
        func plot(_ showSparse: Bool) -> DkStage {
            .plot(DkPlot(xr: (0, 99), yr: (-2.1, 0.1), yTicks: logTicks(-2.1, 0.1), xLeft: "step 0", xRight: "99",
                         lines: [line(dRate, .blue, false)] + (showSparse ? [line(sRate, .orange, true)] : []),
                         dots: [DkDot(p: DkP(99, log10(dRate.last!)), ink: .blue, r: 4.5)] + (showSparse ? [DkDot(p: DkP(99, log10(sRate.last!)), ink: .orange, r: 4.5)] : []),
                         axis: false))
        }
        let header = "η / √(Σ g²) · dense g = 1 every step, sparse g = 2 every 10th"
        let lg = [legend(.blue, "Dense"), legend(.orange, "Sparse")]
        let ratio = sRate.last! / dRate.last!
        let frames = [
            DkFrame(header: header, stage: plot(false), legend: [lg[0]], formula: ["G ← G + g² · step = η·g / √G", "dense at 100: 1/√100 = {\(n(dRate.last!, 3))}"],
                    headline: "A feature seen every step has its rate fall as {1/√t}.",
                    body: "AdaGrad divides each parameter's step by the root of all its past squared gradients."),
            DkFrame(header: header, stage: plot(true), legend: lg, formula: ["dense at 100: 1/√100 = \(n(dRate.last!, 3))", "sparse at 100: 1/√(10·4) = {\(n(sRate.last!, 3))}"],
                    headline: "The rare feature keeps a {\(times(ratio))×} larger step.",
                    body: "AdaGrad sums every squared gradient, so frequent features cool down fast and rare ones stay responsive. The sum never shrinks, so every rate only falls."),
            DkFrame(header: header, stage: plot(true), legend: lg, formula: ["G only grows → rate only falls", "after 10,000 steps the dense rate would be 0.01"],
                    headline: "The rate {never recovers}, even if gradients change.",
                    body: "On long training runs AdaGrad's steps shrink toward zero and learning stalls. RMSprop replaces the sum with a moving average."),
            DkFrame(header: header, stage: plot(true), legend: lg, formula: ["good fit: sparse features (word embeddings, ads)", "per-parameter rates with no tuning"],
                    headline: "AdaGrad shines on {sparse} data.",
                    body: "Rare words or features get large updates when they finally appear, which is why it was popular for embeddings and click models."),
        ]
        return stepActions(frames)
    }
}

// MARK: - RMSprop

private func rmspropLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let eta = 0.01
        var v = 0.0, sum = 0.0
        var rms: [Double] = [], ada: [Double] = []
        for t in 0..<200 {
            let g = t < 100 ? 1.0 : 0.1
            v = 0.9 * v + 0.1 * g * g
            sum += g * g
            rms.append(eta / v.squareRoot())
            ada.append(eta / sum.squareRoot())
        }
        func plot(_ upTo: Int) -> DkStage {
            .plot(DkPlot(xr: (0, 199), yr: (-4.2, -0.85), yTicks: logTicks(-4.2, -0.85), xLeft: "step 0", xRight: "199",
                         lines: [DkLine(pts: (0..<upTo).map { DkP(Double($0), log10(rms[$0])) }, ink: .blue),
                                 DkLine(pts: (0..<upTo).map { DkP(Double($0), log10(ada[$0])) }, ink: .grey)],
                         dots: [DkDot(p: DkP(Double(upTo - 1), log10(rms[upTo - 1])), ink: .blue, r: 4.5), DkDot(p: DkP(Double(upTo - 1), log10(ada[upTo - 1])), ink: .grey, r: 4.5)],
                         axis: false))
        }
        let header = "effective rate · g = 1, then 0.1 from step 100"
        let lg = [legend(.blue, "RMSprop, γ = 0.9"), legend(.grey, "AdaGrad")]
        let frames = [
            DkFrame(header: header, stage: plot(100), legend: lg, formula: ["v ← 0.9·v + 0.1·g² · rate = η/√v", "step 100: RMSprop \(n(rms[99], 3)) · AdaGrad \(n(ada[99], 4))"],
                    headline: "With a steady gradient both rates {settle}; AdaGrad's keeps sinking.",
                    body: "RMSprop's v is an average of recent g², so it levels off at g² = 1. AdaGrad's sum keeps growing."),
            DkFrame(header: header, stage: plot(200), legend: lg,
                    formula: ["v ← 0.9·v + 0.1·g² → √v at 200: \(n((pow(0.9, 100) + 0.01 * (1 - pow(0.9, 100))).squareRoot(), 3))", "rate at 200: RMSprop {\(n(rms[199], 3))} · AdaGrad \(n(ada[199], 5))"],
                    headline: "After the shift RMSprop's rate recovers {\(n(rms[199] / rms[99], 0))×}; AdaGrad's stays stuck.",
                    body: "An exponential average forgets old gradients, so the step adapts to the current scale. At step 200 it is \(n(rms[199] / ada[199], 0))× AdaGrad's."),
            DkFrame(header: header, stage: plot(200), legend: lg, formula: ["RMSprop + momentum on the gradient = Adam", "γ = 0.9: remembers about 10 steps"],
                    headline: "Add momentum to RMSprop and you get {Adam}.",
                    body: "RMSprop was never formally published: Hinton proposed it in a Coursera lecture, and it became the default for RNNs."),
        ]
        return stepActions(frames)
    }
}

// MARK: - Adam: bias correction

private func adamLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let g = 2.0
        let ratios = (1...60).map { t in (1 - pow(0.9, Double(t))) / (1 - pow(0.999, Double(t))).squareRoot() }
        let peak = ratios.indices.max { ratios[$0] < ratios[$1] }!
        let top = ratios.max()!
        let plot = DkStage.plot(DkPlot(
            xr: (0, 59), yr: (-0.3, top * 1.08), yTicks: [(top, n(top, 1)), (top / 2, n(top / 2, 1)), (0, "0.00")], xLeft: "step 0", xRight: "59",
            lines: [DkLine(pts: ratios.enumerated().map { DkP(Double($0.offset), $0.element) }, ink: .pink), DkLine(pts: [DkP(0, 1), DkP(59, 1)], ink: .blue)],
            dots: [DkDot(p: DkP(Double(peak), top), ink: .yellow, r: 5)], axis: false))
        let header = "step size ÷ lr · m/√v vs m̂/√v̂"
        let lg = [legend(.pink, "Uncorrected"), legend(.blue, "Bias-corrected"), legend(.yellow, "Peak", .fill)]
        let m1 = 0.1 * g, v1 = 0.001 * g * g
        let frames = [
            DkFrame(header: header, stage: plot, legend: lg,
                    formula: ["t = 1: m = \(n(m1, 1)), v = \(n(v1, 3)) → m/√v = \(n(m1 / v1.squareRoot()))", "m̂ = m/(1−0.9ᵗ), v̂ = v/(1−0.999ᵗ) → {1.00} every step"],
                    headline: "Without correction steps run up to {\(n(top))×} too large at step \(peak + 1).",
                    body: "Both averages start at 0, and v warms up 100× slower than m. Dividing by (1 − βᵗ) removes that start-up bias exactly."),
            DkFrame(header: header, stage: plot, legend: lg,
                    formula: ["Adam = momentum (m) + RMSprop (v) + bias correction", "defaults: β₁ 0.9, β₂ 0.999, ε 1e−8"],
                    headline: "Adam is momentum and RMSprop {in one update}.",
                    body: "m smooths the direction, v scales each parameter's step, and the corrections make the first few hundred steps behave. It works well out of the box, which made it the default."),
        ]
        return stepActions(frames)
    }
}

// MARK: - AdamW: decoupled weight decay

private func adamwLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["L2 in Adam", "AdamW"], initialTab: 1) { tab, _ in
        let lr = 0.001, lambda = 0.01, w = 1.0
        let ps: [(name: String, vhat: Double, g: Double)] = [("large history", 25, 5), ("small history", 0.25, 0.5)]
        let l2 = ps.map { lr * lambda * w / $0.vhat.squareRoot() }
        let aw = lr * lambda * w
        let top = l2.max()!
        func micro(_ v: Double) -> String { n(v * 1e6, 1) + "μ" }
        let both = tab == 1
        let rows = DkStage.rows(DkRows(rows: ps.enumerated().map { i, p in
            var gT = n(p.g, 1); if gT.hasSuffix(".0") { gT.removeLast(2) }
            return DkRow(title: "\(p.name) · v̂ = \(trim(n(p.vhat)))", meta: "|g| was \(gT)",
                         bars: [DkBar(frac: l2[i] / top, ink: .pink, label: micro(l2[i]))] + (both ? [DkBar(frac: aw / top, ink: .blue, label: micro(aw))] : []),
                         pair: both)
        }))
        let header = "decay applied to each parameter · λ = \(trim(n(lambda))), lr = \(trim(n(lr, 3)))"
        let lg = [legend(.pink, "L2 inside Adam", .fill), legend(.blue, "AdamW, decoupled", .fill)]
        let l2Line = "L2: lr·λw/√v̂ = 0.001·0.01/√25 = \(micro(l2[0])) vs /√0.25 = \(micro(l2[1]))"
        let frames = [
            DkFrame(header: header, stage: rows, legend: both ? lg : [lg[0]],
                    formula: [l2Line, both ? "AdamW: lr·λ·w = {\(micro(aw))} for both" : "same weight, same λ, {\(n(l2[1] / l2[0], 0))×} different decay"],
                    headline: "L2-in-Adam decays the two weights {\(n(l2[1] / l2[0], 0))×} differently.",
                    body: "Adam divides the decay by √v̂ along with the gradient, so weights with big gradients barely shrink. AdamW applies decay outside the adaptive step."),
            DkFrame(header: header, stage: rows, legend: both ? lg : [lg[0]],
                    formula: ["AdamW: w ← w − lr·(m̂/√v̂ + λ·w)", "decay no longer depends on gradient history"],
                    headline: both ? "AdamW shrinks every weight by the {same} \(micro(aw))." : "With L2 inside Adam, regularisation {depends} on the gradients.",
                    body: "That makes λ behave like true weight decay. AdamW generalises better and is what transformers are trained with."),
        ]
        return stepActions(frames)
    }
}

// MARK: - Learning-rate schedulers

private struct LrSchedule { let name: String; let ink: DkInk; let dashed: Bool; let lr: (Int) -> Double }

private let schedules: [LrSchedule] = [
    LrSchedule(name: "constant", ink: .grey, dashed: true) { _ in 0.03 },
    LrSchedule(name: "step decay", ink: .orange, dashed: false) { t in 0.09 * pow(0.5, Double(t / 15)) },
    LrSchedule(name: "cosine", ink: .violet, dashed: false) { t in 0.045 * (1 + cos(Double.pi * Double(t) / 60)) },
    LrSchedule(name: "warmup+cos", ink: .blue, dashed: false) { t in t < 5 ? 0.09 * Double(t + 1) / 5 : 0.045 * (1 + cos(Double.pi * Double(t - 5) / 55)) },
]

private func runSchedule(_ s: LrSchedule) -> [Double] {
    var w1 = -8.0, w2 = 1.0
    var loss = [bowl(w1, w2)]
    for t in 0..<60 {
        let lr = s.lr(t)
        w1 -= lr * w1
        w2 -= lr * steep * w2
        loss.append(bowl(w1, w2))
    }
    return loss
}

private func schedulerLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Constant", "Step", "Cosine", "Warmup"], initialTab: 3) { tab, _ in
        let runs = schedules.map { runSchedule($0) }
        let finals = runs.map { $0.last! }
        let logs = runs.flatMap { $0 }.map { log10($0) }
        let lo = logs.min()!.rounded(.down) - 0.3, hi = logs.max()! + 0.3
        let lossPlot = DkStage.plot(DkPlot(xr: (0, 60), yr: (lo, hi), yTicks: logTicks(lo, hi), xLeft: "step 0", xRight: "60",
                                           lines: schedules.enumerated().map { i, s in DkLine(pts: runs[i].enumerated().map { DkP(Double($0.offset), log10($0.element)) }, ink: s.ink, dashed: s.dashed) },
                                           axis: false))
        let lrPlot = DkStage.plot(DkPlot(xr: (0, 59), yr: (-0.004, 0.096), yTicks: [(0.09, "0.09"), (0.03, "0.03"), (0, "0")], xLeft: "step 0", xRight: "59",
                                         lines: schedules.map { s in DkLine(pts: (0..<60).map { DkP(Double($0), s.lr($0)) }, ink: s.ink, dashed: s.dashed) },
                                         rules: [DkRule(y: 0.1, ink: .pink, thin: true)], axis: false))
        let lg = schedules.map { legend($0.ink, $0.name, $0.dashed ? .dashedLine : .line) }
        let s = schedules[tab], f = finals[tab]
        let step1 = runs.map { $0[1] }
        let finalLine = "step 60: " + finals.enumerated().map { $0.offset == tab ? "{\(n($0.element, 3))}" : n($0.element, 3) }.joined(separator: " · ")
        let lossHeader = "loss on f = ½(w₁² + 20·w₂²) · four schedules"
        let frames = [
            DkFrame(header: "learning rate per step · steep-axis limit 0.1", stage: lrPlot, legend: lg,
                    formula: ["stable while lr < 2/20 = 0.1 on the steep axis", "constant 0.03 · others start near 0.09"],
                    headline: "Four ways to set the {learning rate} over 60 steps.",
                    body: "Step decay halves it every 15 steps, cosine glides to 0, warmup ramps up over 5 steps first. All but the constant start near the stability limit."),
            DkFrame(header: lossHeader, stage: lossPlot, legend: lg,
                    formula: ["step 1: constant \(n(step1[0], 3)) · warmup \(n(step1[3], 3)) · hot starts \(n(step1[1], 3))", finalLine],
                    headline: tab == 0 ? "A safe constant 0.03 ends at {\(n(finals[0], 3))}." : "A safe constant 0.03 ends at \(n(finals[0], 3)); {\(s.name)} reaches \(n(f, 3)).",
                    body: tab == 0 ? "It is slow on the flat axis all the way. Pick another schedule to compare."
                        : "Starting at 0.09 is near the steep axis's 0.1 limit but moves fast along the flat one. Decaying from there gets both: \(s.name) ends \(n(finals[0] / f, 0))× below the constant rate."),
            DkFrame(header: lossHeader, stage: lossPlot, legend: lg,
                    formula: ["warmup: protects early steps when gradients are wild", "cosine with warmup: the transformer default"],
                    headline: "Warmup matters when the {first steps} are unstable.",
                    body: "Here the bowl is gentle, so warmup costs a little. In a fresh transformer, Adam's early estimates are noisy and a full-size first step can blow training up."),
        ]
        return stepActions(frames)
    }
}

// MARK: - KL divergence

private let klP: [Double] = {
    let p = [0.0, 0.03, 0.18, 0.18, 0.03, 0.0, 0.0, 0.03, 0.18, 0.18, 0.03, 0.0]
    let s = p.reduce(0, +)
    return p.map { $0 / s }
}()

private func gaussRaw(_ x: Double, _ mu: Double, _ sigma: Double) -> Double { exp(-0.5 * pow((x - mu) / sigma, 2)) }

private func gaussQ(_ mu: Double, _ sigma: Double) -> [Double] {
    let raw = (1...12).map { gaussRaw(Double($0), mu, sigma) }
    let s = raw.reduce(0, +)
    return raw.map { $0 / s }
}

private func kl(_ a: [Double], _ b: [Double]) -> Double { a.indices.reduce(0.0) { a[$1] <= 0 ? $0 : $0 + a[$1] * log(a[$1] / max(b[$1], 1e-9)) } }

private struct KlBest { let mu: Double; let sigma: Double; let value: Double }

private let klFits: (KlBest, KlBest) = {
    var fwd = KlBest(mu: 0, sigma: 0, value: .greatestFiniteMagnitude)
    var rev = KlBest(mu: 0, sigma: 0, value: .greatestFiniteMagnitude)
    let pFloor = klP.map { max($0, 1e-6) }
    var mu = 1.0
    while mu <= 12.0001 {
        var s = 0.4
        while s <= 6.0001 {
            let q = gaussQ(mu, s)
            let f = kl(klP, q), r = kl(q, pFloor)
            if f < fwd.value { fwd = KlBest(mu: mu, sigma: s, value: f) }
            if r < rev.value { rev = KlBest(mu: mu, sigma: s, value: r) }
            s += 0.05
        }
        mu += 0.5
    }
    return (fwd, rev)
}()

private func klLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Forward", "Reverse", "Both"], initialTab: 2) { tab, _ in
        let (fwd, rev) = klFits
        let top = klP.max()! * 1.3
        func qCurve(_ f: KlBest, _ ink: DkInk) -> DkLine {
            let norm = (1...12).reduce(0.0) { $0 + gaussRaw(Double($1), f.mu, f.sigma) }
            return DkLine(pts: (0...110).map { k in let x = 1 + 11.0 * Double(k) / 110; return DkP(x, gaussRaw(x, f.mu, f.sigma) / norm) }, ink: ink)
        }
        func plot(_ showF: Bool, _ showR: Bool) -> DkStage {
            .plot(DkPlot(xr: (0.4, 12.6), yr: (0, top), yTicks: [], xLeft: "bin 1", xRight: "12",
                         lines: (showF ? [qCurve(fwd, .orange)] : []) + (showR ? [qCurve(rev, .pink)] : []),
                         axis: false, bars: klP.enumerated().map { DkPBar(x: Double($0.offset) + 1, y: $0.element, ink: .slate, width: 0.8) }))
        }
        let header = "P = two bumps · best single-Gaussian Q under each direction"
        let lg = [legend(.slate, "P (target)", .fill)] + (tab != 1 ? [legend(.orange, "min KL(P‖Q)")] : []) + (tab != 0 ? [legend(.pink, "min KL(Q‖P)")] : [])
        let fLine = "KL(P‖Q): μ \(n(fwd.mu, 1)), σ \(n(fwd.sigma)) → \(n(fwd.value, 3)) · covers both"
        let rLine = "KL(Q‖P): μ \(n(rev.mu, 1)), σ \(n(rev.sigma)) → {\(n(rev.value, 3))} · picks one"
        let frames = [
            DkFrame(header: header, stage: plot(false, false), legend: [lg[0]], formula: ["KL(P‖Q) = Σ P·log(P/Q)", "Q: one Gaussian, μ and σ searched on a grid"],
                    headline: "Fit {one Gaussian} to a two-peaked P.",
                    body: "No single bump can match both peaks, so the direction of KL decides which compromise wins."),
            DkFrame(header: header, stage: plot(tab != 1, tab != 0), legend: lg,
                    formula: tab == 0 ? ["KL(P‖Q): μ \(n(fwd.mu, 1)), σ \(n(fwd.sigma)) → {\(n(fwd.value, 3))}", "Q must cover wherever P > 0"]
                        : tab == 1 ? ["KL(Q‖P): μ \(n(rev.mu, 1)), σ \(n(rev.sigma)) → {\(n(rev.value, 3))}", "Q must avoid wherever P ≈ 0"] : [fLine, rLine],
                    headline: tab == 0 ? "Forward KL {spreads} Q across both peaks." : tab == 1 ? "Reverse KL {locks onto} one peak." : "Swapping the order changes the {answer}, not just the number.",
                    body: "Forward KL punishes Q for missing any of P's mass, so it spreads across both peaks. Reverse KL punishes Q for putting mass where P has none, so it locks onto one."),
            DkFrame(header: header, stage: plot(tab != 1, tab != 0), legend: lg,
                    formula: ["forward: maximum likelihood, mass-covering", "reverse: variational inference, mode-seeking"],
                    headline: "KL is {not a distance}: KL(P‖Q) ≠ KL(Q‖P).",
                    body: "Training a model by likelihood minimises forward KL, which is why it hedges. VAEs and variational methods minimise reverse KL, which is why they can miss modes."),
        ]
        return stepActions(frames)
    }
}

// MARK: - Cross-entropy

private func crossEntropyLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["True class 0", "True class 2"], initialTab: 1) { tab, _ in
        let z = [2.0, 1.0, 0.1]
        let e = z.map { exp($0) }
        let es = e.reduce(0, +)
        let p = e.map { $0 / es }
        let y = tab == 0 ? 0 : 2
        let grad = p.enumerated().map { $0.element - ($0.offset == y ? 1 : 0) }
        func rows(_ withGrad: Bool) -> DkStage {
            .rows(DkRows(rows: p.enumerated().map { i, v in
                DkRow(title: "class \(i)", meta: "",
                      bars: [DkBar(frac: v, ink: .blue, label: n(v, 3))] + (withGrad ? [DkBar(frac: abs(grad[i]), ink: grad[i] >= 0 ? .blue : .pink, label: n(grad[i], 3))] : []),
                      hot: i == y, pair: withGrad)
            }, caption: withGrad ? "left: p · right: ∂L/∂z = p − y" : "p = softmax(z)"))
        }
        let header = "logits (2.0, 1.0, 0.1) → softmax p, gradient p − y"
        let lg = [legend(.blue, "Probability / push up", .fill), legend(.pink, "Push down", .fill), legend(.yellow, "True class", .fill)]
        let l0 = -log(p[0]), l2 = -log(p[2])
        let lossLine = "true 0: −ln \(n(p[0], 3)) = \(tab == 0 ? "{\(n(l0, 3))}" : n(l0, 3)) · true 2: −ln \(n(p[2], 3)) = \(tab == 1 ? "{\(n(l2, 3))}" : n(l2, 3))"
        let frames = [
            DkFrame(header: header, stage: rows(false), legend: [lg[0], lg[2]], formula: ["L = −ln p_true", lossLine],
                    headline: tab == 0 ? "The model already favours class 0: loss {\(n(l0, 3))}." : "The model gives the true class only {\(n(p[2], 3))}.",
                    body: "Cross-entropy only looks at the probability of the true class: the lower it is, the steeper the penalty."),
            DkFrame(header: header, stage: rows(true), legend: lg, formula: [lossLine],
                    headline: tab == 0 ? "Already right: the gradient is {small} everywhere." : "Being wrong costs {\(n(l2 / l0, 1))×} more, and the gradient says exactly how to fix it.",
                    body: tab == 0 ? "p − y is \(n(grad[0], 3)) on the true class and small elsewhere: the model is nearly done with this example."
                        : "p − y lowers each wrong logit by its own probability and raises the true one by \(n(-grad[2], 2)). No Jacobian needed."),
            DkFrame(header: header, stage: rows(true), legend: lg, formula: ["softmax + cross-entropy: ∂L/∂z = p − y", "−ln p → ∞ as p → 0"],
                    headline: "Softmax and cross-entropy {cancel} into p − y.",
                    body: "That clean gradient is why the two are always paired. Confident mistakes cost the most, so they get fixed first."),
        ]
        return stepActions(frames)
    }
}
