import SwiftUI

// Port of DeepStoryFrames.kt: the fourteen labs drawn by DeepStoryLabs.swift. Every number on screen is
// computed here: the neuron in closed form, the deep stacks by a real forward and backward pass over
// seeded weights, the activation facts by a fine grid or a numeric integral. The generator is the same
// plain LCG as on Android, so both apps show the same numbers.

func deepLab(_ topicId: String) -> DkLab {
    if let lab = cnnLab(topicId) { return lab }
    if let lab = detectLab(topicId) { return lab }
    if let lab = rnnLab(topicId) { return lab }
    if let lab = transformerLab(topicId) { return lab }
    if let lab = modernLab(topicId) { return lab }
    if let lab = optimLab(topicId) { return lab }
    return switch topicId {
    case "biological_neuron": neuronLab()
    case "neural_network_basics": feedforwardLab()
    case "vanishing_gradient": vanishingLab()
    case "exploding_gradient": explodingLab()
    case "sigmoid": sigmoidLab()
    case "tanh": tanhLab()
    case "relu": reluLab()
    case "leaky_relu": leakyLab()
    case "prelu": preluLab()
    case "elu": eluLab()
    case "selu": seluLab()
    case "swish": swishLab()
    case "gelu": geluLab()
    default: softmaxLab()
    }
}

// MARK: - Formatting and small math

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }

/// Up to `d` decimals with trailing zeros dropped: 1.5, 0.35, −2.
private func t(_ v: Double, _ d: Int = 2) -> String {
    var s = dkNum(v, d)
    guard s.contains(".") else { return s }
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}

private let supDigits = Array("⁰¹²³⁴⁵⁶⁷⁸⁹")
private func sup(_ k: Int) -> String { (k < 0 ? "⁻" : "") + String(String(abs(k)).map { supDigits[Int(String($0))!] }) }

/// 7.2e6, 2.8e−6.
private func sci(_ v: Double) -> String {
    if v == 0 { return "0" }
    var e = Int(log10(abs(v)).rounded(.down))
    var m = (abs(v) / pow(10, Double(e)) * 10 + 0.5).rounded(.down) / 10
    if m >= 10 { m /= 10; e += 1 }
    let body = dkNum(m, 1) + "e" + (e < 0 ? "−" : "") + String(abs(e))
    return v < 0 ? "−" + body : body
}

/// Plain between 0.01 and 1000, scientific outside.
private func mag(_ v: Double) -> String { abs(v) >= 0.01 && abs(v) < 1000 ? n(v) : sci(v) }

/// Two significant figures with thousands separators: 360,000.
private func grouped(_ v: Double) -> String {
    if v < 100 { return n(v, 0) }
    let e = Int(log10(v).rounded(.down)) - 1
    let r = Int64(((v / pow(10, Double(e)) + 0.5).rounded(.down) * pow(10, Double(e))).rounded())
    let digits = Array(String(r))
    var out = ""
    for (i, c) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out += "," }
        out.append(c)
    }
    return out
}

private func grid(_ a: Double, _ b: Double, _ step: Double) -> [Double] {
    (0...Int(((b - a) / step).rounded())).map { (((a + Double($0) * step) * 1000).rounded()) / 1000 }
}

/// f sampled across [a, b], with x = 0 included so a kink lands on a sample.
private func curve(_ a: Double, _ b: Double, _ count: Int = 160, _ f: (Double) -> Double) -> [DkP] {
    var xs = (0...count).map { a + (b - a) * Double($0) / Double(count) }
    if a < 0 && b > 0 { xs.append(0) }
    return xs.sorted().map { DkP($0, f($0)) }
}

private func sig(_ z: Double) -> Double { 1 / (1 + exp(-z)) }

private func line(_ ink: DkInk, _ label: String) -> DkLegend { DkLegend(ink: ink, style: .line, label: label) }
private func dashed(_ ink: DkInk, _ label: String) -> DkLegend { DkLegend(ink: ink, style: .dashedLine, label: label) }
private func swatch(_ ink: DkInk, _ label: String) -> DkLegend { DkLegend(ink: ink, style: .fill, label: label) }

/// Decade ticks over a log10 range: every decade, or every other one when the range is wide.
private func logTicks(_ lo: Double, _ hi: Double) -> [(Double, String)] {
    let top = Int(hi.rounded(.down)), bottom = Int(lo.rounded(.up))
    guard top >= bottom else { return [] }
    let step = top - bottom >= 6 ? 2 : 1
    return stride(from: top, through: bottom, by: -step).map { k in (Double(k), k == 0 ? "1" : "1e" + (k < 0 ? "−" : "") + String(abs(k))) }
}

private func logRange(_ values: [Double]) -> (Double, Double) {
    let lo = values.min()!, hi = values.max()!
    let pad = max(0.35, (hi - lo) * 0.06)
    return (lo - pad, hi + pad)
}

private func zt(_ z: Double) -> String { t(z) }

/// e raised to −z: e⁻³ for a whole number, e^(−0.5) otherwise.
private func eNeg(_ z: Double) -> String { z == z.rounded(.down) ? "e" + sup(-Int(z)) : "e^(\(zt(-z)))" }

/// A seeded LCG with Box–Muller normals, the same sequence as on Android.
private struct DkRng {
    var s: Int64
    init(_ seed: Int64) { s = seed }
    mutating func u() -> Double {
        s = (s &* 1103515245 &+ 12345) & 0x7fffffff
        return Double(s) / 2147483648.0
    }
    mutating func g() -> Double {
        let a = max(u(), 1e-12)
        let b = u()
        return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b)
    }
}

/// The standard normal CDF via Abramowitz–Stegun 7.1.26 (error under 1.5e−7).
private func phi(_ x: Double) -> Double {
    let z = abs(x) / 2.0.squareRoot()
    let k = 1 / (1 + 0.3275911 * z)
    let erf = 1 - (((((1.061405429 * k - 1.453152027) * k) + 1.421413741) * k - 0.284496736) * k + 0.254829592) * k * exp(-z * z)
    return x >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf)
}

/// E[f(z)] and Var[f(z)] for z ~ N(0, 1), by a fine Riemann sum over ±8.
private func normalMoments(_ f: (Double) -> Double) -> (Double, Double) {
    let h = 0.001
    var m1 = 0.0, m2 = 0.0
    var x = -8.0
    while x <= 8.0 {
        let w = exp(-x * x / 2) / (2 * Double.pi).squareRoot() * h
        let v = f(x)
        m1 += v * w
        m2 += v * v * w
        x += h
    }
    return (m1, m2 - m1 * m1)
}

/// The x in [a, b] (step h) where f is smallest.
private func argMin(_ a: Double, _ b: Double, _ h: Double, _ f: (Double) -> Double) -> Double {
    var best = a, bv = f(a), x = a
    while x <= b {
        let v = f(x)
        if v < bv { bv = v; best = x }
        x += h
    }
    return best
}

/// A stepper over input z ("−0.75").
private func zStepper(_ a: Double, _ b: Double, _ step: Double, _ initial: Double, _ digits: Int) -> DkStepper {
    let zs = grid(a, b, step)
    return DkStepper(caption: "input z", values: zs, initial: zs.firstIndex(of: initial) ?? 0) { n($0, digits) }
}

private func withActions(_ frames: [DkFrame], _ actions: [String]) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = actions[i]; return f }
}

/// "Next" on every step, "Start Over" on the last.
private func nextActions(_ frames: [DkFrame]) -> [DkFrame] {
    withActions(frames, frames.indices.map { $0 == frames.count - 1 ? "Start Over" : "Next" })
}

// MARK: - Deep stacks: a real forward and backward pass

private struct DeepRun {
    /// Mean |activation| at layers 1...L.
    let act: [Double]
    /// Mean |∂L/∂a| at layers 1...L, with ∂L/∂a = 1 at the top.
    let grad: [Double]
    /// ‖∂L/∂W‖ per layer.
    let wgrad: [Double]
    /// Mean f′(z) over every unit, layer and sample.
    let slope: Double
    let variance: [Double]
    let mean: [Double]
}

private enum Act { case sigmoid, tanh, relu, selu }

private let seluL = 1.0507009873554805
private let seluA = 1.6732632423543772

private func actF(_ kind: Act, _ z: Double) -> Double {
    switch kind {
    case .sigmoid: sig(z)
    case .tanh: Foundation.tanh(z)
    case .relu: max(0, z)
    case .selu: z > 0 ? seluL * z : seluL * seluA * (exp(z) - 1)
    }
}

private func actD(_ kind: Act, _ z: Double) -> Double {
    switch kind {
    case .sigmoid: sig(z) * (1 - sig(z))
    case .tanh: 1 - Foundation.tanh(z) * Foundation.tanh(z)
    case .relu: z > 0 ? 1 : 0
    case .selu: z > 0 ? seluL : seluL * seluA * exp(z)
    }
}

/// `layers` dense layers of `width` units, weights N(0, σw²), no biases, on `batch` standard-normal inputs.
private func deepRun(_ kind: Act, _ layers: Int, _ width: Int, _ sigmaW: Double, _ seed: Int64, batch: Int = 32, backward: Bool = true) -> DeepRun {
    var rng = DkRng(seed)
    var w: [[[Double]]] = []
    for _ in 0..<layers {
        var m: [[Double]] = []
        for _ in 0..<width {
            var row: [Double] = []
            for _ in 0..<width { row.append(rng.g() * sigmaW) }
            m.append(row)
        }
        w.append(m)
    }
    var x: [[Double]] = []
    for _ in 0..<batch {
        var row: [Double] = []
        for _ in 0..<width { row.append(rng.g()) }
        x.append(row)
    }
    var zs: [[[Double]]] = [], outs: [[[Double]]] = []
    var a = x
    for l in 0..<layers {
        var z = Array(repeating: Array(repeating: 0.0, count: width), count: batch)
        for b in 0..<batch {
            for i in 0..<width {
                var s = 0.0
                for j in 0..<width { s += w[l][i][j] * a[b][j] }
                z[b][i] = s
            }
        }
        a = z.map { $0.map { actF(kind, $0) } }
        zs.append(z)
        outs.append(a)
    }
    let count = Double(batch * width)
    let act = outs.map { o in o.reduce(0.0) { $0 + $1.reduce(0.0) { $0 + abs($1) } } / count }
    let mean = outs.map { o in o.reduce(0.0) { $0 + $1.reduce(0.0, +) } / count }
    let variance = outs.enumerated().map { l, o in o.reduce(0.0) { $0 + $1.reduce(0.0) { $0 + ($1 - mean[l]) * ($1 - mean[l]) } } / count }
    var slopeSum = 0.0
    for z in zs { for r in z { for v in r { slopeSum += actD(kind, v) } } }

    var grad = Array(repeating: 0.0, count: layers), wgrad = Array(repeating: 0.0, count: layers)
    var g = Array(repeating: Array(repeating: 1.0, count: width), count: batch)
    for l in stride(from: layers - 1, through: 0, by: -1) where backward {
        grad[l] = g.reduce(0.0) { $0 + $1.reduce(0.0) { $0 + abs($1) } } / count
        var delta = g
        for b in 0..<batch { for i in 0..<width { delta[b][i] = g[b][i] * actD(kind, zs[l][b][i]) } }
        let prev = l == 0 ? x : outs[l - 1]
        var sq = 0.0
        for i in 0..<width {
            for j in 0..<width {
                var s = 0.0
                for b in 0..<batch { s += delta[b][i] * prev[b][j] }
                s /= Double(batch)
                sq += s * s
            }
        }
        wgrad[l] = sq.squareRoot()
        var next = Array(repeating: Array(repeating: 0.0, count: width), count: batch)
        for b in 0..<batch {
            for j in 0..<width {
                var s = 0.0
                for i in 0..<width { s += w[l][i][j] * delta[b][i] }
                next[b][j] = s
            }
        }
        g = next
    }
    return DeepRun(act: act, grad: grad, wgrad: wgrad, slope: slopeSum / (count * Double(layers)), variance: variance, mean: mean)
}

private func logLine(_ values: [Double], _ ink: DkInk, dashed: Bool = false, upTo: Int? = nil, from: Int = 0) -> DkLine {
    DkLine(pts: (from..<(upTo ?? values.count)).map { DkP(Double($0) + 1, log10(values[$0])) }, ink: ink, dashed: dashed, dots: !dashed)
}

// MARK: - The biological neuron: leaky integrate-and-fire in closed form

private let tau = 10.0, rest = -70.0, thresh = -55.0, refract = 2.0, runMs = 120.0
private let rheobase = thresh - rest

private func firingRate(_ i: Double) -> Double { i <= rheobase ? 0 : 1000 / (tau * log(i / (i - rheobase)) + refract) }

private func neuronLab() -> DkLab {
    let currents = grid(8, 30, 2)
    return DkLab(control: .stepper, stepper: DkStepper(caption: "current I", values: currents, initial: currents.firstIndex(of: 18)!) { n($0, 0) }) { _, p in
        let i = currents[p]
        let vInf = rest + i
        let fires = vInf > thresh
        let tStar = fires ? tau * log(i / (i - rheobase)) : 0
        let period = tStar + refract
        var spikes: [Double] = []
        if fires { var s = tStar; while s <= runMs { spikes.append(s); s += period } }
        let rate = firingRate(i)
        func charge(_ t0: Double, _ t: Double) -> Double { vInf + (rest - vInf) * exp(-(t - t0) / tau) }
        let passive = curve(0, runMs, 240) { charge(0, $0) }
        var train: [DkP] = []
        if !fires { train = passive } else {
            var t0 = 0.0
            while true {
                let end = min(t0 + tStar, runMs)
                for k in 0...30 { let tt = t0 + (end - t0) * Double(k) / 30; train.append(DkP(tt, charge(t0, tt))) }
                if t0 + tStar > runMs { break }
                train.append(DkP(t0 + tStar, -40))
                train.append(DkP(t0 + tStar, rest))
                t0 += period
                train.append(DkP(min(t0, runMs), rest))
                if t0 >= runMs { break }
            }
        }
        let iText = n(i, 0)
        let vText = "\(n(vInf, 0)) mV"
        let ticks: [(Double, String)] = [(-40, "−40"), (-55, "−55"), (-70, "−70")]
        func trace(_ lines: [DkLine], _ rules: [DkRule], _ dots: [DkDot] = [], bracket: DkBracket? = nil) -> DkStage {
            .plot(DkPlot(xr: (0, runMs), yr: (-73, -37), yTicks: ticks, xLeft: "0", xRight: "120 ms", lines: lines, dots: dots, rules: rules, axis: false, bracket: bracket))
        }
        let vInfRule = DkRule(y: vInf, ink: .grey, thin: true)
        let threshRule = DkRule(y: thresh, ink: .pink)
        let traceLegend = [line(.blue, "V(t)"), dashed(.pink, "Threshold −55"), dashed(.grey, "V∞ = \(n(vInf, 0))")]
        let tStarLine = "t* = 10·ln(\(iText)/\(n(i - rheobase, 0))) = {\(n(tStar, 1)) ms}"
        let rateText = "\(n(rate, 0)) Hz"
        let maxRate = firingRate(currents.last!)
        let fi = curve(0, 30, 300) { firingRate($0) }
        func fiPlot(_ extra: [DkLine]) -> DkStage {
            .plot(DkPlot(xr: (0, 30), yr: (-6, maxRate * 1.1), yTicks: [(100, "100"), (50, "50"), (0, "0")], xLeft: "I = 0", xRight: "30",
                         lines: extra + [DkLine(pts: fi, ink: .blue)], dots: [DkDot(p: DkP(i, rate))], axis: false, guide: (i, "I = \(iText)")))
        }
        return [
            DkFrame(
                header: nil, stage: trace([DkLine(pts: passive, ink: .blue)], [vInfRule]),
                legend: [line(.blue, "V(t)"), dashed(.grey, "V∞ = \(n(vInf, 0))")],
                formula: ["τ·dV/dt = −(V + 70) + I,  τ = 10 ms", "V∞ = −70 + \(iText) = {\(vText)}"],
                headline: "Current I = \(iText) charges the membrane toward {\(vText)}.",
                body: "The leak pulls V back to rest at −70 while the input pushes it up; they balance at V∞ = −70 + I. Each τ = 10 ms closes 63% of the gap."
            ),
            DkFrame(
                header: nil,
                stage: trace([DkLine(pts: passive, ink: .blue)], [threshRule, vInfRule], fires ? [DkDot(p: DkP(tStar, thresh))] : []),
                legend: traceLegend,
                formula: fires ? ["V∞ = \(vText) > −55", tStarLine] : ["V∞ = \(vText) < −55", "t* = {never}"],
                headline: fires ? "It crosses the −55 threshold at {\(n(tStar, 1)) ms}." : "It settles at \(vText), {short of} the −55 threshold.",
                body: fires ? "V∞ sits past threshold, so the climb reaches −55 first, at t* = τ·ln(I / (I − 15))."
                    : "Below 15 units of current the leak always wins. 15 is the rheobase: the least current that can ever cause a spike."
            ),
            DkFrame(
                header: nil,
                stage: trace([DkLine(pts: train, ink: .blue)], [vInfRule, threshRule],
                             bracket: spikes.count >= 2 ? DkBracket(x0: spikes[0], x1: spikes[1], y: -40, label: "\(n(period, 1)) ms") : nil),
                legend: traceLegend,
                formula: fires ? ["V∞ = −70 + \(iText) = \(vText) > −55", tStarLine] : ["V∞ = −70 + \(iText) = \(vText) < −55", "no crossing: {0 spikes}"],
                headline: fires ? "Above rheobase, the cell fires every {\(n(period, 1)) ms}." : "Below rheobase, the cell {never fires}.",
                body: fires ? "At \(iText) units the resting balance sits at \(vText), past threshold. It takes \(n(tStar, 1)) ms to climb there, plus 2 ms refractory."
                    : "At \(iText) units the balance point \(vText) is under threshold, so V creeps up and stops. Raise I past 15.",
                chips: [DkChip(key: "spikes", value: "\(spikes.count)", tint: true), DkChip(key: "rate", value: rateText)]
            ),
            DkFrame(
                header: "firing rate f vs input current I", stage: fiPlot([]),
                legend: [line(.blue, "f(I)"), DkLegend(ink: .yellow, style: .dot, label: "I = \(iText)")],
                formula: ["f = 1000 / (t* + 2 ms)", fires ? "f(\(iText)) = 1000 / \(n(period, 1)) = {\(rateText)}" : "f(\(iText)) = {0 Hz}"],
                headline: fires ? "At I = \(iText) the cell fires at {\(rateText)}; below 15 it's silent." : "At I = \(iText) the rate is {0 Hz}: left of the rheobase.",
                body: "Sweep every current and the rate traces this f–I curve: zero up to the rheobase, then rising steeply and bending over as the refractory period caps it."
            ),
            DkFrame(
                header: "firing rate f vs input current I",
                stage: fiPlot([DkLine(pts: curve(0, 30, 300) { max(0, maxRate / 15 * ($0 - rheobase)) }, ink: .grey, dashed: true)]),
                legend: [line(.blue, "neuron f(I)"), dashed(.grey, "ReLU")],
                formula: ["neuron: f(I) = 0 for I ≤ 15, rising after", "ReLU: {max(0, z)} = 0 for z ≤ 0, rising after"],
                headline: "An artificial unit keeps only the shape: {max(0, z)}.",
                body: "A ReLU unit outputs its rate at once: no membrane, no spikes, no time. The neuron needed 120 ms of voltage to give the same kind of answer."
            ),
        ]
    }
}

// MARK: - Feedforward networks: one forward pass through 2-3-1

private let ffX = [0.90, 0.20]
private let ffW1 = [[0.9, -0.45], [0.3, 0.35], [-0.5, 0.5]]
private let ffW2 = [1.2, -0.7, 0.9]
private let ffB = -0.3

private func feedforwardLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        func forward(_ x: [Double]) -> ([Double], [Double], Double) {
            let pre = ffW1.map { $0[0] * x[0] + $0[1] * x[1] }
            let h = pre.map { max(0, $0) }
            return (pre, h, (0..<3).reduce(0.0) { $0 + ffW2[$1] * h[$1] } + ffB)
        }
        let (pre, h, z) = forward(ffX)
        let swapped = [ffX[1], ffX[0]]
        let (pre2, h2, z2) = forward(swapped)
        let hy: [CGFloat] = [0, 0.5, 1]

        func net(_ x: [Double], _ hidden: [(String, DkTone)], _ notes: [String?], _ out: (String, DkTone),
                 _ inState: (Int) -> DkEdgeState, _ outState: (Int) -> DkEdgeState?, _ labels: Bool) -> DkStage {
            var nodes = [DkNode(x: 0, y: 0.27, text: n(x[0]), tone: .input), DkNode(x: 0, y: 0.73, text: n(x[1]), tone: .input)]
            for (j, hn) in hidden.enumerated() { nodes.append(DkNode(x: 0.5, y: hy[j], text: hn.0, tone: hn.1, note: notes[j])) }
            nodes.append(DkNode(x: 1, y: 0.45, text: out.0, tone: out.1))
            var edges: [DkEdge] = []
            for j in 0..<3 { for i in 0..<2 { edges.append(DkEdge(from: i, to: 2 + j, w: ffW1[j][i], state: inState(j))) } }
            for j in 0..<3 { edges.append(DkEdge(from: 2 + j, to: 5, w: ffW2[j], state: outState(j) ?? .faint, label: labels ? t(ffW2[j]) : nil)) }
            return .net(DkNet(nodes: nodes, edges: edges, footers: [(0, "input"), (0.5, "hidden · ReLU"), (1, "output · σ")]))
        }
        let ghost = ("?", DkTone.ghost)
        let signs = [line(.blue, "Positive weight"), line(.orange, "Negative")]
        let withSilenced = signs + [dashed(.grey, "Silenced by ReLU")]
        let done = h.enumerated().map { j, v in (n(v), pre[j] > 0 ? DkTone.hidden : .off) }
        let silentNote: [String?] = [nil, nil, "z = \(n(pre[2]))"]
        let outEdges = { (j: Int) -> DkEdgeState? in pre[j] > 0 ? .lit : .silenced }
        func sumOf(_ x: [Double], _ j: Int) -> String {
            "\(t(ffW1[j][0]))·\(n(x[0])) \(ffW1[j][1] < 0 ? "−" : "+") \(t(abs(ffW1[j][1])))·\(n(x[1]))"
        }
        let y = sig(z), y2 = sig(z2)
        let frames = [
            DkFrame(
                header: nil, stage: net(ffX, [ghost, ghost, ghost], [nil, nil, nil], ghost, { _ in .faint }, { _ in nil }, false), legend: signs,
                formula: ["x = (\(n(ffX[0])), \(n(ffX[1])))", "each hidden unit: h = ReLU(w·x)"],
                headline: "Two inputs, {\(n(ffX[0]))} and {\(n(ffX[1]))}, enter the network.",
                body: "Every input connects to every hidden unit. Blue weights are positive, orange negative; thicker means larger."
            ),
            DkFrame(
                header: nil,
                stage: net(ffX, [(n(h[0]), .current), ghost, ghost], [nil, nil, nil], ghost, { $0 == 0 ? .lit : .faint }, { _ in nil }, false),
                legend: signs,
                formula: ["h₁ = ReLU(\(sumOf(ffX, 0)))", "   = ReLU(\(n(pre[0]))) = {\(n(h[0]))}"],
                headline: "Hidden unit 1 sums its weighted inputs: {\(n(h[0]))}.",
                body: "The \(t(ffW1[0][0])) weight on x₁ dominates; \(t(ffW1[0][1])) on x₂ pulls it down a little. Positive, so ReLU passes it unchanged."
            ),
            DkFrame(
                header: nil,
                stage: net(ffX, [(n(h[0]), .hidden), (n(h[1]), .hidden), (n(h[2]), .off)], silentNote, ghost, { $0 == 0 ? .faint : .lit }, { _ in nil }, false),
                legend: signs,
                formula: ["h₂ = ReLU(\(sumOf(ffX, 1))) = \(n(h[1]))", "h₃ = ReLU(\(sumOf(ffX, 2))) = ReLU({\(n(pre[2]))}) = 0"],
                headline: "Hidden unit 3's sum is {\(n(pre[2]))}, so ReLU outputs 0.",
                body: "Unit 2 is positive and passes \(n(h[1])). Unit 3 is switched off for this input."
            ),
            DkFrame(
                header: nil, stage: net(ffX, done, silentNote, ("?", .current), { _ in .faint }, outEdges, true), legend: withSilenced,
                formula: ["z = \(t(ffW2[0]))·\(n(h[0])) − \(t(-ffW2[1]))·\(n(h[1])) + \(t(ffW2[2]))·\(n(h[2])) − \(t(-ffB))", "  = {\(n(z, 3))}"],
                headline: "Hidden unit 3 adds {nothing} to the output.",
                body: "Its weighted sum is \(n(pre[2])), so ReLU sets it to 0 and its \(t(ffW2[2])) weight is skipped. Applying σ next turns z = \(n(z, 3)) into the prediction."
            ),
            DkFrame(
                header: nil, stage: net(ffX, done, silentNote, (n(y), .output), { _ in .faint }, outEdges, true), legend: withSilenced,
                formula: ["ŷ = σ(z) = 1/(1 + e^(−\(n(z, 3))))", "  = {\(n(y, 3))}"],
                headline: "σ(\(n(z, 3))) = {\(n(y, 3))}: the network's prediction.",
                body: "Read as a probability, that's \(n(y * 100, 0))% for class 1. Training adjusts every weight until this matches the label."
            ),
            DkFrame(
                header: nil,
                stage: net(swapped, h2.enumerated().map { j, v in (n(v), pre2[j] > 0 ? DkTone.hidden : .off) },
                           ["z = \(n(pre2[0]))", nil, nil], (n(y2), .output), { _ in .faint }, { pre2[$0] > 0 ? .lit : .silenced }, true),
                legend: withSilenced,
                formula: ["h = (\(h2.map { n($0) }.joined(separator: ", ")))", "ŷ = σ(\(n(z2, 3))) = {\(n(y2, 3))}"],
                headline: "Swap the inputs and {unit 1} goes silent instead.",
                body: "Which units fire depends on the input, so each input runs through its own part of the network. That's how a ReLU net builds a piecewise-linear function."
            ),
        ]
        return withActions(frames, ["Compute h₁", "Compute h₂, h₃", "Weigh Hidden Units", "Apply σ", "Swap Inputs", "Start Over"])
    }
}

// MARK: - Vanishing gradients: ten layers, three activations

private let vgLayers = 10, vgWidth = 16
private let vgSeed: Int64 = 2024

private let vgRuns: [DeepRun] = [
    deepRun(.sigmoid, vgLayers, vgWidth, (2.0 / Double(vgWidth + vgWidth)).squareRoot(), vgSeed),
    deepRun(.tanh, vgLayers, vgWidth, (2.0 / Double(vgWidth + vgWidth)).squareRoot(), vgSeed),
    deepRun(.relu, vgLayers, vgWidth, (2.0 / Double(vgWidth)).squareRoot(), vgSeed),
]

private func vanishingLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Sigmoid", "Tanh", "ReLU + He"]) { tab, _ in
        let rel = vgRuns.map { run in run.grad.map { $0 / run.grad.last! } }
        let mine = rel[tab]
        let other = tab == 2 ? rel[0] : rel[2]
        let inks: [DkInk] = [.pink, .orange, .green]
        let names = ["Sigmoid, Xavier", "Tanh, Xavier", "ReLU, He"]
        let otherInk: DkInk = tab == 2 ? .pink : .green
        let otherName = tab == 2 ? names[0] : names[2]
        let (lo, hi) = logRange((mine + other).map { log10($0) } + [0])
        let per = pow(mine[0], 1.0 / Double(vgLayers - 1))
        let slopeName = ["σ′", "tanh′", "ReLU′"][tab]
        let ceilingText = ["(ceiling 0.25)", "(ceiling 1)", "(active half)"][tab]
        let slopeLine = "mean \(slopeName) = \(n(vgRuns[tab].slope, 3)) \(ceilingText)"
        func plot(_ upTo: Int, _ withOther: Bool, _ dotAt: Int) -> DkStage {
            .plot(DkPlot(xr: (0.6, Double(vgLayers) + 0.4), yr: (lo, hi), yTicks: logTicks(lo, hi), xLeft: "layer 1", xRight: "layer \(vgLayers) (output)",
                         lines: (withOther ? [logLine(other, otherInk, dashed: true)] : []) + [logLine(mine, inks[tab], from: vgLayers - upTo)],
                         dots: [DkDot(p: DkP(Double(dotAt) + 1, log10(mine[dotAt])))], axis: false))
        }
        let legendMine = line(inks[tab], names[tab])
        let legendBoth = [legendMine, dashed(otherInk, otherName)]
        let header = "gradient size per layer, relative to output"
        let firstText = mag(mine[0])
        let frames = [
            DkFrame(
                header: header, stage: plot(1, false, vgLayers - 1), legend: [legendMine],
                formula: ["g ← Wᵀ(g ⊙ f′(z)), once per layer", "layer \(vgLayers): g = {1}"],
                headline: "Backprop starts at the output with gradient {1}.",
                body: "Each layer it passes back through multiplies it by that layer's weights and by the activation's slope f′(z)."
            ),
            DkFrame(
                header: header, stage: plot(2, false, vgLayers - 2), legend: [legendMine],
                formula: [slopeLine, "layer \(vgLayers - 1): {\(mag(mine[vgLayers - 2]))}"],
                headline: "One layer back, {\(mag(mine[vgLayers - 2]))} of it is left.",
                body: [
                    "σ′ is at most 0.25, and Xavier weights only restore about ×1, so most of the gradient is lost at every layer.",
                    "tanh′ reaches 1 at zero, but units pushed toward ±1 pass back much less.",
                    "ReLU′ is exactly 1 on active units, and He init doubles the weight variance to make up for the half that are off.",
                ][tab]
            ),
            DkFrame(
                header: header, stage: plot(vgLayers, true, 0), legend: legendBoth,
                formula: [slopeLine, "per layer ≈ \(n(per, 3))\(sup(vgLayers - 1)) = {\(firstText)}"],
                headline: "Layer 1 gets {\(firstText)} of the output's gradient.",
                body: tab == 0 ? "Each sigmoid layer shrinks it by about \(n(1 / per, 1))×. ReLU with He init ends at \(mag(rel[2][0])), so its first layer still learns."
                    : tab == 1 ? "Each tanh layer shrinks it by about \(n(1 / per, 1))×: better than sigmoid, still vanishing. ReLU with He init ends at \(mag(rel[2][0]))."
                    : "Sigmoid with Xavier ends at \(mag(rel[0][0])) on the same inputs. With ReLU the first layer learns about as fast as the last."
            ),
            DkFrame(
                header: header, stage: plot(vgLayers, true, 0), legend: legendBoth,
                formula: ["Δw ∝ η·g, the same η for every layer", "layer 1 / layer \(vgLayers) = {\(firstText)}"],
                headline: tab == 2 ? "With ReLU and He init, layer 1 moves at {\(n(mine[0]))×} the output's rate."
                    : "At one learning rate, layer 1 moves {\(grouped(1 / mine[0]))×} slower.",
                body: tab == 2 ? "No layer is starved, so all ten learn together. The opposite risk is a gain above 1: see exploding gradients."
                    : 1 / mine[0] < 100 ? "Slower, not stuck: near 0 tanh's slope is close to 1. Saturated units or a deeper stack push it toward sigmoid's numbers."
                    : "By the time layer \(vgLayers) has settled, layer 1 has barely left its random start. ReLU, careful init and residual connections fixed this."
            ),
        ]
        return nextActions(frames)
    }
}

// MARK: - Exploding gradients: twelve ReLU layers, σw on a stepper

private let egLayers = 12, egWidth = 16
private let egSeed: Int64 = 77
private let egSigmas = [0.25, 0.35, 0.5, 0.75, 1.0, 1.5, 2.0]

private func explodingLab() -> DkLab {
    DkLab(control: .stepper, stepper: DkStepper(caption: "weight σw", values: egSigmas, initial: egSigmas.firstIndex(of: 1.5)!) { t($0) }) { _, p in
        let sw = egSigmas[p]
        let run = deepRun(.relu, egLayers, egWidth, sw, egSeed)
        let he = deepRun(.relu, egLayers, egWidth, (2.0 / Double(egWidth)).squareRoot(), egSeed)
        let gain = sw * (Double(egWidth) / 2).squareRoot()
        let measured = pow(run.act.last! / run.act.first!, 1.0 / Double(egLayers - 1))
        let grow = measured > 1.15, shrink = measured < 0.87
        let top = run.act.last!, heTop = he.act.last!
        let swText = t(sw)
        let heLine = dashed(.green, "He, σw = 0.35")
        let mineLine = line(.pink, "σw = \(swText)")
        func plot(_ values: [Double], _ base: [Double], _ mine: Bool, _ dotAt: Int, lines: [DkLine]? = nil) -> DkStage {
            let (lo, hi) = logRange((values + base).map { log10($0) })
            return .plot(DkPlot(xr: (0.6, Double(egLayers) + 0.4), yr: (lo, hi), yTicks: logTicks(lo, hi), xLeft: "layer 1", xRight: "layer \(egLayers)",
                                lines: lines ?? ([logLine(base, .green, dashed: true)] + (mine ? [logLine(values, .pink)] : [])),
                                dots: mine ? [DkDot(p: DkP(Double(dotAt) + 1, log10(values[dotAt])))] : [], axis: false))
        }
        let actHeader = "mean |activation| per layer · \(egLayers)-layer ReLU, width \(egWidth)"
        let gainLine = "gain ≈ σw·√(n/2) = \(swText)·√8 = \(n(gain))"
        let norm = run.wgrad.reduce(0.0) { $0 + $1 * $1 }.squareRoot()
        let scale = min(1.0, 1.0 / norm)
        let clipped = run.wgrad.map { $0 * scale }
        let spread = run.wgrad.max()! / run.wgrad.min()!
        let g1 = run.grad.first!
        return [
            DkFrame(
                header: actHeader, stage: plot(run.act, he.act, false, egLayers - 1), legend: [heLine],
                formula: ["He: σw = √(2/n) = √(2/16) = 0.35", "gain ≈ σw·√(n/2) = {1.00}"],
                headline: "With He scaling, layer \(egLayers)'s activations stay at {\(mag(heTop))}.",
                body: "He init draws weights with σw = √(2/n): ReLU zeroes half the units, so each weight is made √2 larger to keep the signal's size.",
                chips: [DkChip(key: "He", value: mag(heTop))]
            ),
            DkFrame(
                header: actHeader, stage: plot(run.act, he.act, true, egLayers - 1), legend: [mineLine, heLine],
                formula: [gainLine, "measured {\(n(measured))×} per layer"],
                headline: grow ? "Activations grow \(n(measured, 1))× per layer, to {\(mag(top))}."
                    : shrink ? "Activations shrink to \(n(measured, 2))× per layer, down to {\(mag(top))}."
                    : "Activations hold steady: layer \(egLayers) is at {\(mag(top))}.",
                body: grow ? "With He scaling the gain is 1 and layer \(egLayers) stays at \(mag(heTop)). Gradients flow back through the same weights, so they explode too."
                    : shrink ? "Below He's 0.35 each layer loses signal, and gradients flowing back through the same weights vanish the same way."
                    : "Near σw = 0.35 the gain is about 1, so neither activations nor gradients drift.",
                chips: [DkChip(key: "layer \(egLayers)", value: mag(top), tint: true), DkChip(key: "He", value: mag(heTop))]
            ),
            DkFrame(
                header: "mean |gradient| per layer, backward from layer \(egLayers)", stage: plot(run.grad, he.grad, true, 0), legend: [mineLine, heLine],
                formula: ["g₁ = W₂ᵀ(… W₁₂ᵀ(g ⊙ ReLU′) …)", "layer 1: {\(mag(g1))}   He: \(mag(he.grad.first!))"],
                headline: grow ? "Going back, the gradient grows to {\(mag(g1))} at layer 1."
                    : shrink ? "Going back, the gradient shrinks to {\(mag(g1))} at layer 1."
                    : "Going back, layer 1's gradient is {\(mag(g1))}, close to the output's.",
                body: grow ? "Backprop multiplies by the same weights in reverse, so it compounds by the same gain. One step at a normal learning rate throws layer 1's weights far away."
                    : shrink ? "Backprop multiplies by the same weights in reverse, so layer 1 barely learns."
                    : "With the gain near 1, every layer gets a usable gradient."
            ),
            DkFrame(
                header: "‖∂L/∂W‖ per layer, before and after clipping",
                stage: plot(clipped, run.wgrad, true, 0, lines: [logLine(run.wgrad, .pink, dashed: true), logLine(clipped, .blue)]),
                legend: [dashed(.pink, "before"), line(.blue, "clipped to norm 1")],
                formula: ["‖g‖ = \(mag(norm))", "g ← g · min(1, 1/‖g‖) = g · {\(mag(scale))}"],
                headline: norm > 1 ? "Clipping scales every gradient by {\(mag(scale))}, to total norm 1."
                    : "The total norm is {\(mag(norm))}, under the clip threshold of 1: nothing changes.",
                body: norm > 1 ? "It keeps the direction and caps the step, so one bad batch can't wreck the weights. It doesn't fix the scale: the layers are still \(mag(spread))× apart."
                    : "Clipping only acts when the gradient is too large; this one passes untouched."
            ),
            DkFrame(
                header: actHeader, stage: plot(run.act, he.act, true, egLayers - 1), legend: [mineLine, heLine],
                formula: ["gain = σw·√8 = 1  ⇒  σw = 1/√8 = {0.35}", gainLine],
                headline: grow || shrink ? "The real fix is the scale: σw = {0.35} gives gain 1." : "At σw = \(swText) this is {He init} already.",
                body: "He init, batch norm and residual connections all keep each layer's gain near 1, so activations and gradients stay in range without clipping."
                    + (grow || shrink ? " Step σw to 0.35 to see it." : ""),
                chips: [DkChip(key: "layer \(egLayers)", value: mag(top), tint: true), DkChip(key: "He", value: mag(heTop))]
            ),
        ]
    }
}

// MARK: - Shared activation-plot pieces

/// How much gradient survives n layers at a given slope, against the best case.
private func chainPlot(_ slope: Double, _ best: Double) -> DkStage {
    let ys = (0...10).map { log10(pow(slope, Double($0))) } + (0...10).map { log10(pow(best, Double($0))) }
    let (lo, hi) = logRange(ys + [0])
    return .plot(DkPlot(
        xr: (-0.3, 10.3), yr: (lo, hi), yTicks: logTicks(lo, hi), xLeft: "0 layers", xRight: "10",
        lines: [
            DkLine(pts: (0...10).map { DkP(Double($0), log10(pow(best, Double($0)))) }, ink: .grey, dashed: true),
            DkLine(pts: (0...10).map { DkP(Double($0), log10(pow(slope, Double($0)))) }, ink: .pink, dots: true),
        ],
        dots: [DkDot(p: DkP(10, log10(pow(slope, 10))))], axis: false))
}

private func dots(_ ps: [DkP]) -> [DkDot] { ps.map { DkDot(p: $0) } }

// MARK: - Sigmoid

private func sigmoidLab() -> DkLab {
    DkLab(control: .stepper, stepper: zStepper(-4, 4, 0.5, 3, 1)) { _, p in
        let z = grid(-4, 4, 0.5)[p]
        let s = sig(z), d = s * (1 - s)
        let zT = zt(z)
        let ticks: [(Double, String)] = [(1, "1"), (0.25, "0.25"), (0, "0")]
        let sCurve = DkLine(pts: curve(-4, 4) { sig($0) }, ink: .blue)
        let dCurve = DkLine(pts: curve(-4, 4) { sig($0) * (1 - sig($0)) }, ink: .pink)
        func plot(_ lines: [DkLine], _ ds: [DkP], _ at: Double? = nil, rules: [DkRule] = []) -> DkStage {
            let x = at ?? z
            return .plot(DkPlot(xr: (-4, 4), yr: (-0.06, 1.06), yTicks: ticks, xLeft: "−4", xRight: "+4", lines: lines, dots: dots(ds), rules: rules, guide: (x, "z = \(zt(x))")))
        }
        let sLine = "σ(\(zT)) = 1/(1 + \(eNeg(z))) = \(n(s, 3))"
        let both = [line(.blue, "σ(z)"), line(.pink, "σ′(z)")]
        return [
            DkFrame(
                header: "σ(z)", stage: plot([sCurve], [DkP(z, s)]), legend: [line(.blue, "σ(z)")],
                formula: ["σ(z) = 1/(1 + e^−z)", "σ(\(zT)) = 1/(1 + \(eNeg(z))) = {\(n(s, 3))}"],
                headline: "Sigmoid squashes any z into (0, 1): σ(\(zT)) = {\(n(s, 3))}.",
                body: "Large positive z lands near 1, large negative near 0, and σ(0) is exactly 0.5. That's why it reads as a probability."
            ),
            DkFrame(
                header: "σ(z) and σ′(z)", stage: plot([sCurve, dCurve], [DkP(0, 0.5), DkP(0, 0.25)], 0), legend: both,
                formula: ["σ′(z) = σ(z)·(1 − σ(z))", "σ′(0) = 0.5·(1 − 0.5) = {0.25}"],
                headline: "Its slope peaks at {0.25}, at z = 0.",
                body: "That's the most gradient a sigmoid unit can ever pass back: at best it keeps a quarter."
            ),
            DkFrame(
                header: "σ(z) and σ′(z)", stage: plot([sCurve, dCurve], [DkP(z, s), DkP(z, d)]), legend: both,
                formula: [sLine, "σ′(\(zT)) = \(n(s, 3))·(1 − \(n(s, 3))) = {\(n(d, 3))}"],
                headline: z >= 2 ? "At z = \(zT) the output is nearly 1 and the slope is {\(n(d, 3))}."
                    : z <= -2 ? "At z = \(zT) the output is nearly 0 and the slope is {\(n(d, 3))}."
                    : "At z = \(zT) the output is \(n(s, 3)) and the slope is {\(n(d, 3))}.",
                body: d < 0.1 ? "A saturated unit passes back under \(Int((d * 100).rounded(.up)))% of its gradient. Even at the 0.25 peak, five layers leave \(n(pow(0.25, 5), 4))."
                    : "Near the middle the slope is close to its 0.25 peak. Even so, five layers at the peak leave \(n(pow(0.25, 5), 4))."
            ),
            DkFrame(
                header: "gradient left after n sigmoid layers at z = \(zT)", stage: chainPlot(d, 0.25),
                legend: [line(.pink, "σ′(\(zT))ⁿ"), dashed(.grey, "best case 0.25ⁿ")],
                formula: ["σ′(\(zT))¹⁰ = \(n(d, 3))¹⁰ = {\(sci(pow(d, 10)))}", "best case 0.25¹⁰ = \(sci(pow(0.25, 10)))"],
                headline: "Ten layers at z = \(zT) leave {\(sci(pow(d, 10)))} of the gradient.",
                body: "Every layer multiplies by its σ′. That's the vanishing gradient: ReLU's slope is 1 when active, so it doesn't shrink."
            ),
            DkFrame(
                header: "σ(z) at the output", stage: plot([sCurve], [DkP(z, s)], rules: [DkRule(y: 0.5, ink: .grey, thin: true)]),
                legend: [line(.blue, "σ(z)"), dashed(.grey, "0.5 cut")],
                formula: ["p = σ(\(zT)) = {\(n(s, 3))}", "class 1 if p > 0.5 → \(s > 0.5 ? "class 1" : "class 0")"],
                headline: "At the output, σ turns one logit into a {probability}.",
                body: "Binary classifiers and LSTM gates still use it: there the (0, 1) range is the point, not a problem."
            ),
        ]
    }
}

// MARK: - Tanh

private func tanhLab() -> DkLab {
    DkLab(control: .stepper, stepper: zStepper(-4, 4, 0.5, 1, 1)) { _, p in
        let z = grid(-4, 4, 0.5)[p]
        let th = tanh(z), td = 1 - th * th, sd = sig(z) * (1 - sig(z))
        let zT = zt(z)
        let ticks: [(Double, String)] = [(1, "1"), (0, "0"), (-1, "−1")]
        let tCurve = DkLine(pts: curve(-4, 4) { tanh($0) }, ink: .blue)
        let dCurve = DkLine(pts: curve(-4, 4) { 1 - tanh($0) * tanh($0) }, ink: .pink)
        let sCurve = DkLine(pts: curve(-4, 4) { sig($0) }, ink: .grey, dashed: true)
        let sdCurve = DkLine(pts: curve(-4, 4) { sig($0) * (1 - sig($0)) }, ink: .grey, dashed: true)
        func plot(_ lines: [DkLine], _ ds: [DkP], _ at: Double? = nil) -> DkStage {
            let x = at ?? z
            return .plot(DkPlot(xr: (-4, 4), yr: (-1.1, 1.1), yTicks: ticks, xLeft: "−4", xRight: "+4", lines: lines, dots: dots(ds), guide: (x, "z = \(zt(x))")))
        }
        let stretch = "tanh(\(zT)) = 2σ(\(zt(2 * z))) − 1 = 2·\(n(sig(2 * z), 3)) − 1 = \(n(th, 3))"
        let ratio = td / sd
        let header = "tanh(z), tanh′(z) and sigmoid for scale"
        let all = [line(.blue, "tanh"), line(.pink, "tanh′"), dashed(.grey, "sigmoid, σ′")]
        return [
            DkFrame(
                header: "tanh(z)", stage: plot([tCurve], [DkP(z, th)]), legend: [line(.blue, "tanh")],
                formula: ["tanh(z) = (eᶻ − e⁻ᶻ)/(eᶻ + e⁻ᶻ)", "tanh(\(zT)) = {\(n(th, 3))}"],
                headline: "tanh maps any z into (−1, 1), centred on {0}.",
                body: "Negative inputs give negative outputs, so a layer of tanh units averages near 0 instead of near 0.5."
            ),
            DkFrame(
                header: "tanh(z) and sigmoid for scale", stage: plot([sCurve, tCurve], [DkP(z, th)]),
                legend: [line(.blue, "tanh"), dashed(.grey, "sigmoid")],
                formula: ["tanh(z) = 2σ(2z) − 1", "tanh(\(zT)) = 2·\(n(sig(2 * z), 3)) − 1 = {\(n(th, 3))}"],
                headline: "It is a sigmoid {stretched}: twice as tall, twice as steep.",
                body: "Shift the doubled sigmoid down by 1 and it lands exactly on tanh."
            ),
            DkFrame(
                header: header, stage: plot([sCurve, sdCurve, tCurve, dCurve], [DkP(z, th), DkP(z, td)]), legend: all,
                formula: [stretch, "tanh′(\(zT)) = {\(n(td, 3))} vs σ′(\(zT)) = \(n(sd, 3))"],
                headline: ratio >= 1 ? "At z = \(zT), tanh passes back {\(n(ratio, 1))×} more gradient."
                    : "At z = \(zT), tanh passes back only {\(n(ratio, 2))×} sigmoid's gradient.",
                body: ratio >= 1 ? "It is a stretched sigmoid centred on zero, so outputs average near 0 and the next layer's updates aren't all one sign."
                    : "Steeper means it also flattens sooner: past about z = 2.4 tanh is more saturated than sigmoid."
            ),
            DkFrame(
                header: header, stage: plot([sCurve, sdCurve, tCurve, dCurve], [DkP(0, 0), DkP(0, 1)], 0), legend: all,
                formula: ["tanh′(0) = 1 − tanh²(0) = {1}", "σ′(0) = 0.25"],
                headline: "Its slope peaks at {1}, four times sigmoid's 0.25.",
                body: "Near zero a tanh unit passes gradient back undiminished. Saturation still kills it: tanh′(3) = \(n(1 - tanh(3.0) * tanh(3.0), 3))."
            ),
            DkFrame(
                header: "gradient left after n tanh layers at z = \(zT)", stage: chainPlot(td, 1),
                legend: [line(.pink, "tanh′(\(zT))ⁿ"), dashed(.grey, "best case 1ⁿ")],
                formula: ["tanh′(\(zT))¹⁰ = {\(mag(pow(td, 10)))}", "σ′(\(zT))¹⁰ = \(mag(pow(sd, 10)))"],
                headline: "Ten layers at z = \(zT) leave {\(mag(pow(td, 10)))} of the gradient.",
                body: td > sd ? "Far better than sigmoid's \(mag(pow(sd, 10))), but it still shrinks with depth unless every unit sits near 0. That's why deep nets moved to ReLU."
                    : "Out here tanh is even more saturated than sigmoid. Deep nets moved to ReLU, whose active slope is exactly 1."
            ),
        ]
    }
}

// MARK: - ReLU: dead units after a bias shift

private let reluShifts = [0.0, -3.0, -4.0]

/// Units (of 64) whose pre-activation is ≤ 0 on all 256 samples, for each bias shift.
private let reluDead: [Int] = {
    var rng = DkRng(31)
    var xs: [[Double]] = []
    for _ in 0..<256 { var x: [Double] = []; for _ in 0..<8 { x.append(rng.g()) }; xs.append(x) }
    var units: [([Double], Double)] = []
    for _ in 0..<64 {
        let scale = 0.9 + 1.2 * rng.u()
        var w: [Double] = []
        for _ in 0..<8 { w.append(rng.g() * scale / 8.0.squareRoot()) }
        units.append((w, 0.3 * rng.g()))
    }
    return reluShifts.map { shift in
        units.filter { u in xs.allSatisfy { x in (0..<8).reduce(0.0) { $0 + u.0[$1] * x[$1] } + u.1 + shift <= 0 } }.count
    }
}()

private func reluLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Bias 0", "−3", "−4"]) { tab, _ in
        let z = -1.5
        let ticks: [(Double, String)] = [(4, "4"), (1, "1"), (0, "0")]
        let f = DkLine(pts: curve(-4, 4) { max(0, $0) }, ink: .blue)
        let d = DkLine(pts: [DkP(-4, 0), DkP(0, 0), DkP(0, 1), DkP(4, 1)], ink: .pink)
        func plot(_ lines: [DkLine], _ at: Double, _ y: Double) -> DkStage {
            .plot(DkPlot(xr: (-4, 4), yr: (-0.3, 4.2), yTicks: ticks, xLeft: "−4", xRight: "+4", lines: lines, dots: [DkDot(p: DkP(at, y))], guide: (at, "z = \(n(at))")))
        }
        let dead = reluDead[tab]
        let legend = [line(.blue, "max(0, z)"), line(.pink, "derivative: 1 or 0")]
        let chips = [DkChip(key: "dead units", value: "\(dead) of 64", tint: true), DkChip(key: "before", value: "\(reluDead[0])")]
        let shiftText = t(reluShifts[tab])
        let frames = [
            DkFrame(
                header: "ReLU", stage: plot([f], 1.5, 1.5), legend: [line(.blue, "max(0, z)")],
                formula: ["ReLU(z) = max(0, z)", "ReLU(1.5) = 1.5,  ReLU(−1.5) = {0}"],
                headline: "ReLU passes positive z unchanged and {zeros} the rest.",
                body: "No exponentials, no saturation on the positive side: one comparison per unit."
            ),
            DkFrame(
                header: "ReLU and its derivative", stage: plot([f, d], z, 0), legend: legend,
                formula: ["ReLU′(z) = 1 if z > 0, else 0", "ReLU′(−1.5) = {0}"],
                headline: "Its slope is {1 or 0}: no shrinking, no middle.",
                body: "Active units pass gradient back untouched, which is why deep ReLU nets train. Inactive ones pass back nothing."
            ),
            DkFrame(
                header: "ReLU and its derivative", stage: plot([f, d], z, 0), legend: legend, formula: [],
                headline: tab == 0 ? "With no shift, {\(dead) of 64} units are dead." : "A \(shiftText) bias shift silences {\(dead) of 64} units for every input.",
                body: tab == 0 ? "Every unit is positive on some of the 256 samples, so each still gets gradient. Shift the biases to −3 or −4 to push units off."
                    : "Their derivative is 0 on all 256 samples, so no gradient reaches them and they never recover. At \(tab == 1 ? "−4 it's \(reluDead[2])" : "−3 it's \(reluDead[1])").",
                chips: chips
            ),
            DkFrame(
                header: "ReLU and a leaky slope",
                stage: plot([DkLine(pts: curve(-4, 4) { $0 > 0 ? $0 : 0.1 * $0 }, ink: .grey, dashed: true), f, d], z, 0),
                legend: [line(.blue, "max(0, z)"), line(.pink, "derivative"), dashed(.grey, "leaky, slope 0.1")],
                formula: ["dead: ReLU′ = 0 on every input", "fix: a small slope below 0, lower η, careful init"],
                headline: tab == 0 ? "Nothing is dead here, but {one bad update} can push units off." : "A leaky slope would give those {\(dead) units} a way back.",
                body: "A dead unit's gradient is exactly 0, so gradient descent can't revive it. A large learning rate or a big negative bias is the usual cause.",
                chips: chips
            ),
        ]
        return nextActions(frames)
    }
}

// MARK: - Leaky ReLU

private let leakyAlphas = [0.01, 0.1, 0.3]
private let leakySteps = 1000

/// A unit knocked to bias −4, trained back toward max(0, x): its bias after every step.
private func leakyRecovery(_ alpha: Double) -> [Double] {
    var rng = DkRng(53)
    var xs: [Double] = []
    for _ in 0..<256 { xs.append(rng.g()) }
    var b = -4.0
    var out = [b]
    for _ in 0..<leakySteps {
        var g = 0.0
        for x in xs {
            let z = x + b
            let f = z > 0 ? z : alpha * z
            let df = z > 0 ? 1 : alpha
            g += 2 * (f - max(0, x)) * df
        }
        b -= 0.5 * g / Double(xs.count)
        out.append(b)
    }
    return out
}

private let leakyRuns: [[Double]] = leakyAlphas.map { leakyRecovery($0) }

private func leakyLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["α = 0.01", "0.1", "0.3"]) { tab, _ in
        let a = leakyAlphas[tab]
        let aT = t(a)
        let z = -1.5
        let fz = a * z
        let ticks: [(Double, String)] = [(1, "1"), (0, "0")]
        let f = DkLine(pts: curve(-3, 2) { $0 > 0 ? $0 : a * $0 }, ink: .blue)
        let d = DkLine(pts: [DkP(-3, a), DkP(0, a), DkP(0, 1), DkP(2, 1)], ink: .pink)
        let relu = DkLine(pts: curve(-3, 2) { max(0, $0) }, ink: .grey, dashed: true)
        func plot(_ lines: [DkLine], _ ds: [DkP]) -> DkStage {
            .plot(DkPlot(xr: (-3, 2), yr: (-0.35, 1.35), yTicks: ticks, xLeft: "−3", xRight: "+2", lines: lines, dots: dots(ds), guide: (z, "z = \(n(z))")))
        }
        let legend = [line(.blue, "max(αz, z)"), line(.pink, "derivative"), dashed(.grey, "ReLU")]
        let run = leakyRuns[tab]
        let back = run.firstIndex { $0 >= -1 }
        let header = "Leaky ReLU and its derivative · negative side"
        let frames = [
            DkFrame(
                header: "Leaky ReLU · negative side", stage: plot([relu, f], [DkP(z, fz)]),
                legend: [line(.blue, "max(αz, z)"), dashed(.grey, "ReLU")],
                formula: ["f(z) = max(αz, z)", "f(−1.5) = \(aT)·(−1.5) = {\(t(fz, 3))}"],
                headline: "Leaky ReLU keeps {αz} below zero instead of 0.",
                body: "Above zero it is ReLU exactly. Below, a slope of α = \(aT) lets a little signal through."
            ),
            DkFrame(
                header: header, stage: plot([relu, f, d], [DkP(z, fz)]), legend: legend,
                formula: ["f(−1.5) = \(aT)·(−1.5) = \(t(fz, 3))", "f′(−1.5) = {\(aT)}  ReLU: 0"],
                headline: "A negative unit still gets {\(t(a * 100))%} of its gradient.",
                body: "Small, but not zero, so a unit that drifts negative can climb back. Dead units in the ReLU lab's −4 layer: 0."
            ),
            DkFrame(
                header: "a unit knocked to bias −4, trained back",
                stage: .plot(DkPlot(
                    xr: (0, Double(leakySteps)), yr: (-4.3, 0.4), yTicks: [(0, "0"), (-2, "−2"), (-4, "−4")], xLeft: "step 0", xRight: "\(leakySteps)",
                    lines: [
                        DkLine(pts: [DkP(0, -4), DkP(Double(leakySteps), -4)], ink: .grey, dashed: true),
                        DkLine(pts: run.enumerated().filter { $0.offset % 5 == 0 }.map { DkP(Double($0.offset), $0.element) }, ink: .blue),
                    ],
                    dots: [back.map { DkDot(p: DkP(Double($0), run[$0])) } ?? DkDot(p: DkP(Double(leakySteps), run.last!))],
                    rules: [DkRule(y: -1, ink: .grey, thin: true)], axis: false)),
                legend: [line(.blue, "bias, α = \(aT)"), dashed(.grey, "ReLU: stuck")],
                formula: ["∂L/∂b ∝ (f − target)·f′,  f′ = \(aT) below 0", back.map { "bias past −1 after {\($0) steps}" } ?? "after \(leakySteps) steps: {\(n(run.last!))}"],
                headline: back.map { "With α = \(aT) the unit climbs back in {\($0) steps}." } ?? "With α = \(aT) the unit is still climbing: {\(n(run.last!))} after \(leakySteps) steps.",
                body: "Its small slope keeps a gradient flowing, so every step nudges the bias up. A ReLU unit in the same spot gets exactly 0 and stays at −4."
            ),
            DkFrame(
                header: header, stage: plot([DkLine(pts: curve(-3, 2) { $0 }, ink: .grey, dashed: true), f], [DkP(z, fz)]),
                legend: [line(.blue, "α = \(aT)"), dashed(.grey, "α = 1: a line")],
                formula: ["α = 1 → f(z) = z, no non-linearity", "α = \(aT) → f(−1.5) = {\(t(fz, 3))}"],
                headline: "Bigger α revives faster but is {less non-linear}.",
                body: "At α = 1 the unit is a straight line and the network collapses to one linear map. 0.01 is the usual default; PReLU learns α instead."
            ),
        ]
        return nextActions(frames)
    }
}

// MARK: - PReLU: α learned by gradient descent

private struct PreluFit { let xs: [Double]; let ys: [Double]; let alphas: [Double]; let grad0: Double }

private let preluFit: PreluFit = {
    var rng = DkRng(11)
    var xs: [Double] = []
    for _ in 0..<40 { xs.append(-3 + 6 * rng.u()) }
    var ys: [Double] = []
    for x in xs { ys.append((x > 0 ? x : 0.25 * x) + 0.08 * rng.g()) }
    func grad(_ a: Double) -> Double {
        xs.indices.reduce(0.0) { acc, i in
            let x = xs[i]
            let f = x > 0 ? x : a * x
            return acc + 2 * (f - ys[i]) * (x < 0 ? x : 0)
        } / Double(xs.count)
    }
    var alphas = [0.01]
    var a = 0.01
    for _ in 0..<60 {
        a -= 0.1 * grad(a)
        alphas.append(a)
    }
    return PreluFit(xs: xs, ys: ys, alphas: alphas, grad0: grad(0.01))
}()

private func preluLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let fit = preluFit
        let ticks: [(Double, String)] = [(3, "3"), (0, "0"), (-1, "−1")]
        let start = DkLine(pts: curve(-3, 3) { $0 > 0 ? $0 : 0.01 * $0 }, ink: .orange, dashed: true)
        let relu = DkLine(pts: curve(-3, 3) { max(0, $0) }, ink: .grey, dashed: true)
        let scatter = fit.xs.indices.map { DkP(fit.xs[$0], fit.ys[$0]) }
        func plot(_ a: Double, _ showStart: Bool) -> DkStage {
            .plot(DkPlot(xr: (-3, 3), yr: (-1.15, 3.1), yTicks: ticks, xLeft: "−3", xRight: "+3",
                         lines: [relu] + (showStart ? [start] : []) + [DkLine(pts: curve(-3, 3) { $0 > 0 ? $0 : a * $0 }, ink: .blue)],
                         dots: [DkDot(p: DkP(-2, -2 * a))], guide: (-2, "z = −2"), scatter: scatter))
        }
        let a10 = fit.alphas[10], a20 = fit.alphas[20], a60 = fit.alphas[60]
        let header = "fit to data generated with slope 0.25"
        return [
            DkFrame(
                header: header, stage: plot(0.01, false), legend: [line(.blue, "α = 0.010"), dashed(.grey, "ReLU")],
                formula: ["f(z) = z if z > 0, else α·z", "start: α = {0.010}"],
                headline: "PReLU starts at {α = 0.01}, almost ReLU.",
                body: "The grey points were made with a negative-side slope of 0.25. α is a parameter, so the network can find that slope itself.",
                action: "Compute Gradient"
            ),
            DkFrame(
                header: header, stage: plot(0.01, false), legend: [line(.blue, "α = 0.010"), dashed(.grey, "ReLU")],
                formula: ["∂f/∂α = z for z < 0, else 0", "∂L/∂α at α = 0.01: {\(n(fit.grad0, 3))}"],
                headline: "The gradient for α comes only from {negative z}.",
                body: "Every negative point sits below the nearly flat line, so they all pull α up. Positive points don't involve α at all.",
                action: "Train 10 Epochs"
            ),
            DkFrame(
                header: header, stage: plot(a10, true), legend: [line(.blue, "α = \(n(a10, 3))"), dashed(.orange, "α = 0.01 start"), dashed(.grey, "ReLU")],
                formula: ["∂f/∂α = z for z < 0, else 0", "α: 0.010 → {\(n(a10, 3))} after 10 epochs"],
                headline: "α climbed from 0.01 to {\(n(a10, 3))} on its own.",
                body: "It is trained by the same gradient descent as the weights, one number per channel. By epoch 60 it settles at \(n(a60, 3)).",
                action: "Train 10 Epochs"
            ),
            DkFrame(
                header: header, stage: plot(a20, true), legend: [line(.blue, "α = \(n(a20, 3))"), dashed(.orange, "α = 0.01 start"), dashed(.grey, "ReLU")],
                formula: ["α after 20 epochs: {\(n(a20, 3))}", "data slope: 0.25 (plus noise)"],
                headline: "After 20 epochs α = {\(n(a20, 3))}, the slope the data was made with.",
                body: "The positive side never changed: α only shapes z < 0. With one α per channel, each channel can learn its own leak.",
                action: "Start Over"
            ),
        ]
    }
}

// MARK: - ELU

private func elu(_ z: Double) -> Double { z > 0 ? z : exp(z) - 1 }

private func eluLab() -> DkLab {
    DkLab(control: .stepper, stepper: zStepper(-4, 3, 0.5, -3, 1)) { _, p in
        let z = grid(-4, 3, 0.5)[p]
        let fz = elu(z), dz = z > 0 ? 1 : exp(z)
        let zT = zt(z)
        let ticks: [(Double, String)] = [(3, "3"), (1, "1"), (0, "0"), (-1, "−1")]
        let f = DkLine(pts: curve(-4, 3) { elu($0) }, ink: .blue)
        let d = DkLine(pts: curve(-4, 3) { $0 > 0 ? 1 : exp($0) }, ink: .pink)
        let relu = DkLine(pts: curve(-4, 3) { max(0, $0) }, ink: .grey, dashed: true)
        func plot(_ lines: [DkLine], _ ds: [DkP], _ at: Double? = nil) -> DkStage {
            let x = at ?? z
            return .plot(DkPlot(xr: (-4, 3), yr: (-1.2, 3.1), yTicks: ticks, xLeft: "−4", xRight: "+3", lines: lines, dots: dots(ds), guide: (x, "z = \(zt(x))")))
        }
        let legend = [line(.blue, "ELU"), line(.pink, "derivative"), dashed(.grey, "ReLU")]
        let header = "ELU and its derivative, α = 1"
        let (eluMean, _) = normalMoments { elu($0) }
        let (reluMean, _) = normalMoments { max(0, $0) }
        let fLine = z > 0 ? "f(\(zT)) = \(zT)" : "f(\(zT)) = 1·(\(eNeg(-z)) − 1) = \(n(fz, 3))"
        return [
            DkFrame(
                header: "ELU, α = 1", stage: plot([relu, f], [DkP(z, fz)]), legend: [line(.blue, "ELU"), dashed(.grey, "ReLU")],
                formula: ["f(z) = z if z > 0, else α·(eᶻ − 1)", "f(\(zT)) = {\(n(fz, 3))}"],
                headline: "ELU is z above zero and {α(eᶻ − 1)} below.",
                body: "The negative side curves smoothly down toward −α instead of stopping flat at 0."
            ),
            DkFrame(
                header: "ELU, α = 1", stage: plot([relu, f], [DkP(z, fz)]), legend: [line(.blue, "ELU"), dashed(.grey, "ReLU")],
                formula: ["mean ELU(z), z ~ N(0, 1) = {\(n(eluMean))}", "mean ReLU(z) = \(n(reluMean))"],
                headline: "Negative outputs pull the mean toward {0}: \(n(eluMean)) against ReLU's \(n(reluMean)).",
                body: "A mean near 0 keeps the next layer's updates from all sharing one sign, the same reason tanh beats sigmoid."
            ),
            DkFrame(
                header: header, stage: plot([relu, f, d], [DkP(z, fz), DkP(z, dz)]), legend: legend,
                formula: [z > 0 ? "f(\(zT)) = {\(zT)}" : "f(\(zT)) = 1·(\(eNeg(-z)) − 1) = {\(n(fz, 3))}", z > 0 ? "f′(\(zT)) = 1" : "f′(\(zT)) = \(eNeg(-z)) = \(n(dz, 3))"],
                headline: z <= -1 ? "At z = \(zT) the output is already {\(n(fz))}, near its floor of −1."
                    : z <= 0 ? "At z = \(zT) the output is {\(n(fz))} and the slope \(n(dz))."
                    : "At z = \(zT) ELU is just z: {\(n(fz))}, slope 1.",
                body: z <= 0 ? "Large negative inputs are capped rather than passed on, and the slope is \(n(dz, 3)) rather than ReLU's flat 0."
                    : "On the positive side ELU and ReLU are the same line. They differ only below 0."
            ),
            DkFrame(
                header: header, stage: plot([relu, f, d], [DkP(0, 0), DkP(0, 1)], 0), legend: legend,
                formula: ["f′(0⁻) = e⁰ = 1 = f′(0⁺)", fLine],
                headline: "Smooth at 0: the slope is {1} on both sides.",
                body: "ReLU's slope jumps from 0 to 1 at zero; ELU's doesn't, which suits gradient descent. The cost is an exp for every negative unit."
            ),
        ]
    }
}

// MARK: - SELU: self-normalizing through twenty layers

private let seluLayers = 20, seluWidth = 64

private let seluRuns: (DeepRun, DeepRun) = (
    deepRun(.selu, seluLayers, seluWidth, (1.0 / Double(seluWidth)).squareRoot(), 5, batch: 64, backward: false),
    deepRun(.tanh, seluLayers, seluWidth, (1.0 / Double(seluWidth)).squareRoot(), 5, batch: 64, backward: false)
)

private func seluLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let (selu, th) = seluRuns
        let fixedPoint = normalMoments { actF(.selu, $0) }
        func plot(_ upTo: Int) -> DkStage {
            .plot(DkPlot(
                xr: (0.5, Double(seluLayers) + 0.5), yr: (-0.06, 1.35), yTicks: [(1, "1"), (0, "0")], xLeft: "layer 1", xRight: "layer \(seluLayers)",
                lines: [
                    DkLine(pts: (0..<upTo).map { DkP(Double($0) + 1, th.variance[$0]) }, ink: .grey, dashed: true),
                    DkLine(pts: (0..<upTo).map { DkP(Double($0) + 1, selu.variance[$0]) }, ink: .green, dots: true),
                ],
                dots: [DkDot(p: DkP(Double(upTo), selu.variance[upTo - 1]), r: 6)],
                rules: [DkRule(y: 1, ink: .grey, thin: true)], axis: false))
        }
        let header = "activation variance per layer · width \(seluWidth)"
        let legend = [line(.green, "SELU"), dashed(.grey, "tanh, same init"), dashed(.grey, "target 1")]
        let def = "SELU(z) = 1.0507·(z, or 1.6733·(eᶻ − 1))"
        let v = selu.variance
        let frames = [
            DkFrame(
                header: "SELU(z) = λ·ELU_α(z)",
                stage: .plot(DkPlot(xr: (-3, 3), yr: (-1.9, 3.3), yTicks: [(3, "3"), (1, "1"), (0, "0"), (-1, "−1")], xLeft: "−3", xRight: "+3",
                                    lines: [DkLine(pts: curve(-3, 3) { max(0, $0) }, ink: .grey, dashed: true), DkLine(pts: curve(-3, 3) { actF(.selu, $0) }, ink: .green)])),
                legend: [line(.green, "SELU"), dashed(.grey, "ReLU")],
                formula: ["SELU(z) = λz if z > 0, else λα(eᶻ − 1)", "λ = {1.0507},  α = 1.6733"],
                headline: "SELU is ELU scaled by {λ = 1.0507}, with α = 1.6733.",
                body: "Both constants were solved for, not tuned: they are what makes the next step work."
            ),
            DkFrame(
                header: header, stage: plot(5), legend: legend, formula: [def, "layer 5: SELU var {\(n(v[4]))},  tanh \(n(th.variance[4]))"],
                headline: "Five layers in, SELU holds variance {\(n(v[4]))}.",
                body: "Same inputs, same weights: the tanh stack is already down to \(n(th.variance[4])), and every layer takes a little more."
            ),
            DkFrame(
                header: header, stage: plot(10), legend: legend,
                formula: ["z ~ N(0, 1) → mean \(n(fixedPoint.0, 3)), var {\(n(fixedPoint.1, 3))}", "layer 10: var \(n(v[9]))"],
                headline: "λ and α make (mean 0, variance 1) a {fixed point}.",
                body: "Feed SELU inputs with mean 0 and variance 1 through weights of variance 1/n and the outputs come back with the same mean and variance, layer after layer."
            ),
            DkFrame(
                header: header, stage: plot(seluLayers), legend: legend,
                formula: [def, "layer \(seluLayers): mean \(n(selu.mean.last!)), var {\(n(v.last!))}"],
                headline: "After \(seluLayers) layers SELU still has variance {\(n(v.last!))}.",
                body: "λ and α are the fixed point that maps mean 0, variance 1 to itself. tanh with the same init has drifted to \(n(th.variance.last!))."
            ),
            DkFrame(
                header: header, stage: plot(seluLayers), legend: legend,
                formula: ["weights: variance 1/n (LeCun init)", "dropout: alpha dropout only"],
                headline: "The guarantee needs {plain dense layers} and LeCun init.",
                body: "Self-normalizing assumes weights with variance 1/n. Ordinary dropout, skip connections or batch norm break it, which is why SELU stayed niche."
            ),
        ]
        return nextActions(frames)
    }
}

// MARK: - Swish

private let swishBetas = [0.5, 1.0, 2.0]

private func swish(_ z: Double, _ b: Double) -> Double { z * sig(b * z) }
private func swishD(_ z: Double, _ b: Double) -> Double { sig(b * z) + b * z * sig(b * z) * (1 - sig(b * z)) }

private func swishLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["β = 0.5", "β = 1", "β = 2"], initialTab: 1) { tab, _ in
        let b = swishBetas[tab]
        let bT = t(b)
        let ticks: [(Double, String)] = [(3, "3"), (1, "1"), (0, "0")]
        let f = DkLine(pts: curve(-4, 3) { swish($0, b) }, ink: .blue)
        let d = DkLine(pts: curve(-4, 3) { swishD($0, b) }, ink: .pink)
        let relu = DkLine(pts: curve(-4, 3) { max(0, $0) }, ink: .grey, dashed: true)
        func plot(_ lines: [DkLine], _ ds: [DkP], _ at: Double, rules: [DkRule] = [], xHi: Double = 3) -> DkStage {
            .plot(DkPlot(xr: (-4, xHi), yr: (-0.65, max(3.1, xHi + 0.1)), yTicks: ticks, xLeft: "−4", xRight: "+\(t(xHi))",
                         lines: lines, dots: dots(ds), rules: rules, guide: (at, "z = \(n(at))")))
        }
        let zMin = argMin(-5, 0, 0.0005) { swish($0, b) }
        let fMin = swish(zMin, b)
        let zPeak = argMin(0, 8, 0.0005) { -swishD($0, b) }
        let dPeak = swishD(zPeak, b)
        let header = "Swish z·σ(βz), β = \(bT)"
        let legend = [line(.blue, "Swish"), line(.pink, "derivative"), dashed(.grey, "ReLU")]
        let minLine = b == 1 ? "f = \(n(zMin, 3))·σ(\(n(zMin, 3))) = \(n(zMin, 3))·\(n(sig(b * zMin), 3)) = {\(n(fMin, 4))}"
            : "f = \(n(zMin, 3))·σ(\(bT)·(\(n(zMin, 3)))) = \(n(zMin, 3))·\(n(sig(b * zMin), 3)) = {\(n(fMin, 4))}"
        // The slope peaks at z = 2.40/β, past the usual +3 when β = 0.5.
        let peakHi = max(3, (zPeak + 0.5).rounded(.up))
        let wide = [
            DkLine(pts: curve(-4, peakHi) { max(0, $0) }, ink: .grey, dashed: true),
            DkLine(pts: curve(-4, peakHi) { swishD($0, b) }, ink: .pink),
            DkLine(pts: curve(-4, peakHi) { swish($0, b) }, ink: .blue),
        ]
        let frames = [
            DkFrame(
                header: header, stage: plot([relu, f], [DkP(2, swish(2, b))], 2), legend: [line(.blue, "Swish"), dashed(.grey, "ReLU")],
                formula: ["f(z) = z·σ(βz)", "f(2) = 2·σ(\(t(2 * b))) = {\(n(swish(2, b), 3))}"],
                headline: "Swish multiplies z by its own gate {σ(βz)}.",
                body: "For large positive z the gate is open and f ≈ z; for large negative z it closes and f → 0. In between it's smooth."
            ),
            DkFrame(
                header: header, stage: plot([relu, d, f], [DkP(zMin, fMin)], zMin), legend: legend,
                formula: ["min at z = \(n(zMin, 3)), f′ = 0", minLine],
                headline: "Swish bottoms out at {\(n(fMin, 3))}, then rises back to 0.",
                body: "It's smooth and not monotone: slightly negative inputs pass a small negative signal instead of being cut off."
            ),
            DkFrame(
                header: header,
                stage: plot(swishBetas.filter { $0 != b }.map { o in DkLine(pts: curve(-4, 3) { swish($0, o) }, ink: .grey, dashed: true) } + [relu, f],
                            [DkP(zMin, fMin)], zMin),
                legend: [line(.blue, "β = \(bT)"), dashed(.grey, "other β, ReLU")],
                formula: ["β → ∞: σ(βz) → step, f → max(0, z)", "β = 0: f = z/2"],
                headline: tab == 0 ? "At β = 0.5 Swish is gentle, closer to the {line z/2}."
                    : tab == 1 ? "β = 1 sits between a line and ReLU; this is {SiLU}."
                    : "At β = 2 Swish is already close to {ReLU}.",
                body: "As β grows σ(βz) sharpens into a step and Swish becomes ReLU; at β = 0 it is exactly z/2. A learnable β lets the network choose."
            ),
            DkFrame(
                header: header, stage: plot(wide, [DkP(zPeak, dPeak)], zPeak, rules: [DkRule(y: 1, ink: .grey, thin: true)], xHi: peakHi), legend: legend,
                formula: ["f′(z) = σ(βz) + βz·σ(βz)(1 − σ(βz))", "max f′ = {\(n(dPeak, 3))} at z = \(n(zPeak))"],
                headline: "Its slope peaks at {\(n(dPeak, 3))}, a little over 1.",
                body: "Unlike ReLU it overshoots a slope of 1 before settling back. Found by automated search, it's used in EfficientNet and many language models."
            ),
        ]
        return nextActions(frames)
    }
}

// MARK: - GELU

private func gelu(_ z: Double) -> Double { z * phi(z) }
private func geluTanh(_ z: Double) -> Double { 0.5 * z * (1 + tanh((2 / Double.pi).squareRoot() * (z + 0.044715 * z * z * z))) }

private let geluFacts: (zDev: Double, dev: Double, approxErr: Double) = {
    let zDev = argMin(-3, 3, 0.0005) { -abs(gelu($0) - max(0, $0)) }
    var err = 0.0
    var x = -3.0
    while x <= 3.0 {
        err = max(err, abs(gelu(x) - geluTanh(x)))
        x += 0.001
    }
    return (zDev, abs(gelu(zDev) - max(0, zDev)), err)
}()

private func geluLab() -> DkLab {
    DkLab(control: .stepper, stepper: zStepper(-3, 3, 0.25, -0.75, 2)) { _, p in
        let z = grid(-3, 3, 0.25)[p]
        let ph = phi(z), g = gelu(z)
        let zT = n(z)
        let ticks: [(Double, String)] = [(3, "3"), (1, "1"), (0, "0")]
        let f = DkLine(pts: curve(-3, 3) { gelu($0) }, ink: .blue)
        let gate = DkLine(pts: curve(-3, 3) { phi($0) }, ink: .green, dashed: true)
        let relu = DkLine(pts: curve(-3, 3) { max(0, $0) }, ink: .grey, dashed: true)
        func plot(_ lines: [DkLine], _ ds: [DkP], _ at: Double? = nil) -> DkStage {
            let x = at ?? z
            return .plot(DkPlot(xr: (-3, 3), yr: (-0.4, 3.1), yTicks: ticks, xLeft: "−3", xRight: "+3", lines: lines, dots: dots(ds), guide: (x, "z = \(n(x))")))
        }
        let header = "GELU z·Φ(z), with the gate Φ"
        let legend = [line(.blue, "GELU"), dashed(.green, "Φ(z) gate"), dashed(.grey, "ReLU")]
        let (zDev, dev, approxErr) = geluFacts
        return [
            DkFrame(
                header: "GELU z·Φ(z)", stage: plot([relu, f], [DkP(z, g)]), legend: [line(.blue, "GELU"), dashed(.grey, "ReLU")],
                formula: ["GELU(z) = z·Φ(z)", "GELU(\(zT)) = \(zT)·\(n(ph, 3)) = {\(n(g, 3))}"],
                headline: "GELU weights z by {Φ(z)}, the normal CDF.",
                body: "Where ReLU makes a hard yes-or-no at 0, GELU scales each input by how likely it is to be large."
            ),
            DkFrame(
                header: header, stage: plot([relu, gate, f], [DkP(z, ph)]), legend: legend,
                formula: ["Φ(\(zT)) = P(N(0,1) < \(zT)) = {\(n(ph, 3))}", "Φ(−∞) = 0,  Φ(0) = 0.5,  Φ(∞) = 1"],
                headline: "Φ(z) is the chance a standard normal lands {below z}.",
                body: "It rises smoothly from 0 to 1, so it works as a soft gate: nearly shut for very negative z, nearly open for positive z."
            ),
            DkFrame(
                header: header, stage: plot([relu, gate, f], [DkP(z, ph), DkP(z, g)]), legend: legend,
                formula: ["Φ(\(zT)) = P(N(0,1) < \(zT)) = \(n(ph, 3))", "GELU = \(zT) · \(n(ph, 3)) = {\(n(g, 3))}"],
                headline: "At \(zT) the unit is kept with probability {\(n(ph))}.",
                body: "GELU is the expected output if each unit were dropped at random by that probability: dropout and ReLU in one function."
            ),
            DkFrame(
                header: "GELU against ReLU", stage: plot([relu, f], [DkP(zDev, gelu(zDev))], zDev),
                legend: [line(.blue, "GELU"), dashed(.grey, "ReLU")],
                formula: ["max |GELU(z) − ReLU(z)| = {\(n(dev, 3))}", "at z = \(n(zDev))"],
                headline: "GELU and ReLU differ by at most {\(n(dev, 3))}, at z = \(n(zDev)).",
                body: "Far from 0 they agree. Near 0 GELU is smooth and dips slightly negative, so small negative inputs still pass a little signal and gradient."
            ),
            DkFrame(
                header: "GELU and its tanh approximation",
                stage: plot([relu, f, DkLine(pts: curve(-3, 3) { geluTanh($0) }, ink: .orange, dashed: true)], [DkP(z, g)]),
                legend: [line(.blue, "GELU"), dashed(.orange, "tanh approx"), dashed(.grey, "ReLU")],
                formula: ["0.5z·(1 + tanh(√(2/π)·(z + 0.044715z³)))", "max error on [−3, 3]: {\(sci(approxErr))}"],
                headline: "Most libraries offered a {tanh approximation}.",
                body: "BERT and GPT-2 computed GELU this way. It avoids the CDF and differs by under \(sci(approxErr)) anywhere on this plot."
            ),
        ]
    }
}

// MARK: - Softmax

private let smLogits = [2.0, 1.0, 0.1, -0.5]
private let smTemps = [0.5, 1.0, 2.0]

private func softmaxLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["T = 0.5", "T = 1", "T = 2"], initialTab: 1) { tab, _ in
        let temp = smTemps[tab]
        let tT = t(temp)
        let scaled = smLogits.map { $0 / temp }
        let ex = scaled.map { exp($0) }
        let sum = ex.reduce(0, +)
        let p = ex.map { $0 / sum }
        let mx = scaled.max()!
        let shifted = scaled.map { exp($0 - mx) }
        let sumShift = shifted.reduce(0, +)
        let top = p.indices.max { p[$0] < p[$1] }!
        let labels = (1...4).map { "class \($0)" }
        let entropy = -p.reduce(0.0) { $0 + $1 * log($1) }
        let expName = temp == 1 ? "e^z" : "e^(z/\(tT))"
        let shiftName = temp == 1 ? "e^(z − \(t(smLogits.max()!)))" : "e^((z − \(t(smLogits.max()!)))/\(tT))"
        let header = "vector in, vector out"
        let pText = p.map { n($0, 3) }.joined(separator: " ")
        let legendP = [swatch(.grey, "logits"), swatch(.blue, "probabilities"), swatch(.yellow, "top class")]
        let probs = DkStage.bars(DkBars(logits: smLogits, lower: p, lowerLabel: "probabilities p", lowerDigits: 3, labels: labels, top: top))
        let frames = [
            DkFrame(
                header: header, stage: .bars(DkBars(logits: smLogits, lower: ex, lowerLabel: expName, lowerDigits: 3, labels: labels, top: nil)),
                legend: [swatch(.grey, "logits"), swatch(.blue, expName)],
                formula: ["\(expName): \(ex.map { n($0, 3) }.joined(separator: " "))", "sum {\(n(sum, 3))}"],
                headline: "Softmax first makes every logit positive: {\(expName)}.",
                body: "Exponentiating keeps the order and turns gaps into ratios: a logit 1 higher gets e ≈ 2.7× the weight."
            ),
            DkFrame(
                header: header, stage: probs, legend: legendP,
                formula: ["p = \(expName) / \(n(sum, 3))", "p = {\(pText)}"],
                headline: "Divide by the sum and the four add to {1}.",
                body: "Every output is positive and they sum to one, so softmax turns any scores into a distribution over the classes."
            ),
            DkFrame(
                header: header, stage: probs, legend: legendP,
                formula: ["\(shiftName): \(shifted.map { n($0, 3) }.joined(separator: " "))", "sum \(n(sumShift, 3)) → p = {\(pText)}"],
                headline: "Subtracting the max first gives the same {\(n(p[top], 3))}.",
                body: "Every output shares one denominator, so raising any logit lowers all the others. The four sum to \(n(p.reduce(0, +), 4))."
            ),
            DkFrame(
                header: header, stage: probs, legend: legendP,
                formula: ["p = softmax(z / T),  T = \(tT)", "entropy H = {\(n(entropy))} nats (max ln 4 = \(n(log(4.0))))"],
                headline: tab == 0 ? "At T = 0.5 the top class takes {\(n(p[top]))}: sharper."
                    : tab == 1 ? "At T = 1 the top class takes {\(n(p[top]))}: the model's own odds."
                    : "At T = 2 the top class drops to {\(n(p[top]))}: flatter.",
                body: "Dividing the logits by T before softmax sharpens below 1 and flattens above it. Sampling from a language model uses exactly this knob."
            ),
        ]
        return nextActions(frames)
    }
}
