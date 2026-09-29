import SwiftUI

// Port of NeuralStoryLabs.kt: RBM, Deep Belief Networks, Batch Normalization, Backpropagation,
// Autoencoders, Dropout, Activation Functions, MLP and Gradient Descent Variants as step-by-step
// storyboards: one figure in the card (a layered net, two bar panels, a curve plot, or XOR's two planes
// over its truth table), the arithmetic of the step in a formula strip, a legend of what is on screen,
// then chips and a headline. Every number is computed from the small fixed weights and inputs below.

let neuralStoryTopicIds: Set<String> = [
    "restricted_boltzmann_machines",
    "deep_belief_networks",
    "batch_normalization",
    "backpropagation",
    "autoencoders",
    "dropout",
    "activation_functions",
    "mlp",
    "gradient_descent_variants",
]

/// `current` is the yellow "being computed"; `off` a silent unit (ring); `ghost` a slot not filled yet (dashed).
private enum NnTone: CaseIterable { case blue, green, violet, current, off, ghost, dropped, warn, band }

private enum NnEdgeTone { case faint, blue, green, red, redDashed, current }

private struct NnBadge { let text: String; let warn: Bool }

private struct NnNode {
    let x: CGFloat
    let y: CGFloat
    let text: String
    let tone: NnTone
    let r: CGFloat
    var badge: NnBadge? = nil
    /// Muted text to the node's right (a dropped unit's value).
    var side: String? = nil
}

private struct NnEdge {
    let from: Int
    let to: Int
    let tone: NnEdgeTone
    var label: String? = nil
}

/// A shaded funnel between two columns (an autoencoder's encoder and decoder).
private struct NnBand { let x0, top0, bottom0, x1, top1, bottom1: CGFloat }

private struct NnLabel { let text: String; let align: TextAlignment }

private struct NnPill { let text: String; let tone: NnTone }

private struct NetScene {
    let height: CGFloat
    let nodes: [NnNode]
    let edges: [NnEdge]
    var headers: [NnLabel] = []
    var footers: [NnLabel] = []
    var pills: [NnPill] = []
    var bands: [NnBand] = []
}

/// A nil value is a slot still to fill, drawn dashed on the zero line.
private struct NnBar { let value: Double?; let tone: NnTone }

private struct BarsScene {
    let raw: [NnBar]
    let rawMax: Double
    /// μ and σ: a band from μ − σ to μ + σ with a dashed line at μ.
    let band: (Double, Double)?
    let lowerLabel: String
    let lower: [NnBar]
    let lowerScale: Double
}

private struct NnCurve { let segments: [[(Double, Double)]]; let tone: NnTone }

private struct NnDot {
    let x: Double
    let y: Double
    let tone: NnTone
    var hollow = false
    var label: String? = nil
}

private struct PlotScene {
    let height: CGFloat
    let xRange: ClosedRange<Double>
    let yRange: ClosedRange<Double>
    let curves: [NnCurve]
    var dots: [NnDot] = []
    var axes = true
    var refY: Double? = nil
    var probe: (Double, String)? = nil
    /// Loss contours as ellipses around the origin: half-width along x, and x-to-y half-width ratio.
    var contours: [Double] = []
    var contourAspect: Double = 1
    var header: String? = nil
}

private struct XorPoint { let x: Int; let y: Int; let filled: Bool; var ring = false }

private enum XorCellTone { case idle, row, one, current, empty }

private struct XorScene {
    let input: [XorPoint]
    let hidden: [XorPoint]
    let inputLine: Bool
    let hiddenLine: Bool
    let rows: [[(String, XorCellTone)]]
}

private enum NnScene {
    case net(NetScene)
    case bars(BarsScene)
    case plot(PlotScene)
    case xor(XorScene)
}

private struct NnChip {
    let key: String
    let value: String
    var dot: NnTone? = nil
    var tone: StoryTone = .idle
}

private struct NnFrame {
    let headline: String
    let body: String
    let scene: NnScene
    var formula: String? = nil
    var legend: [(NnTone, String)] = []
    var chips: [NnChip] = []
}

private struct NnTab { let label: String; let frames: [NnFrame] }

// MARK: - Shared math and formatting

private func nnSigmoid(_ z: Double) -> Double { 1 / (1 + exp(-z)) }

/// Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−".
private func nnf(_ v: Double, _ d: Int = 2) -> String {
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

/// A weight as written: 0.9, −0.72, 1.2.
private func nnw(_ v: Double) -> String {
    var s = nnf(v, 2)
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}

/// Terms joined as a sum: "0.9 − 0.4 + 0.3", each term optionally wrapped ("{p:0.9}").
private func nnSum(_ terms: [Double], _ mark: (String) -> String = { $0 }) -> String {
    terms.enumerated().map { i, t in
        let a = mark(nnw(abs(t)))
        if i == 0 { return t < 0 ? "−" + a : a }
        return t < 0 ? " − " + a : " + " + a
    }.joined()
}

private func nnVec(_ values: [Double], _ d: Int = 2) -> String { "[" + values.map { nnf($0, d) }.joined(separator: ", ") + "]" }
private func nnInts(_ values: [Int]) -> String { "[" + values.map(String.init).joined(separator: ", ") + "]" }

private func nnSub(_ n: Int) -> String {
    let digits = Array("₀₁₂₃₄₅₆₇₈₉")
    return String(String(n).map { digits[Int(String($0))!] })
}

private func nnSpread(_ n: Int, _ top: CGFloat = 0, _ bottom: CGFloat = 1) -> [CGFloat] {
    (0..<n).map { i in n == 1 ? (top + bottom) / 2 : top + (bottom - top) * CGFloat(i) / CGFloat(n - 1) }
}

private func nnMax(_ xs: [Double]) -> Int { xs.indices.max { xs[$0] < xs[$1] || (xs[$0] == xs[$1] && $0 > $1) }! }

// MARK: - Restricted Boltzmann Machine

private let rbmW: [[Double]] = [
    [0.9, -0.6, 0.2],
    [-0.4, 0.8, -0.9],
    [0.5, -0.7, 0.4],
    [-0.8, -0.3, 0.6],
    [-0.2, -0.9, 0.7],
    [0.3, 1.1, -0.5],
]
private let rbmA: [Double] = [0.1, 0.2, -0.3, -0.2, -0.1, 0.0]
private let rbmB: [Double] = [-0.72, -0.3, 0.1]
private let rbmV = [1, 1, 0, 0, 0, 1]
private let rbmU: [Double] = [0.31, 0.64, 0.58]

private func rbmScene(_ visible: [(String, NnTone)], _ hidden: [(String, NnTone)], _ lit: (Int, Int) -> NnEdgeTone?) -> NnScene {
    let vy = nnSpread(6), hy = nnSpread(3, 0.12, 0.88)
    let nodes = visible.enumerated().map { i, n in NnNode(x: 0.1, y: vy[i], text: n.0, tone: n.1, r: 17) } +
        hidden.enumerated().map { j, n in NnNode(x: 0.9, y: hy[j], text: n.0, tone: n.1, r: 20) }
    let edges = (0..<6).flatMap { i in (0..<3).map { j in NnEdge(from: i, to: 6 + j, tone: lit(i, j) ?? .faint) } }
    return .net(NetScene(height: 250, nodes: nodes, edges: edges,
                         headers: [NnLabel(text: "VISIBLE v · 6", align: .leading), NnLabel(text: "HIDDEN h · 3", align: .trailing)]))
}

private func rbmTabs() -> [NnTab] {
    let v = rbmV.map(Double.init)
    let pre = (0..<3).map { j in rbmB[j] + (0..<6).reduce(0) { $0 + rbmW[$1][j] * v[$1] } }
    let ph = pre.map(nnSigmoid)
    let h = (0..<3).map { j in rbmU[j] < ph[j] ? 1 : 0 }
    let pv = (0..<6).map { i in nnSigmoid(rbmA[i] + (0..<3).reduce(0) { $0 + rbmW[i][$1] * Double(h[$1]) }) }
    let ph2 = (0..<3).map { j in nnSigmoid(rbmB[j] + (0..<6).reduce(0) { $0 + rbmW[$1][j] * pv[$1] }) }
    let err = (0..<6).reduce(0) { $0 + (v[$1] - pv[$1]) * (v[$1] - pv[$1]) } / 6
    let on = (0..<6).filter { rbmV[$0] == 1 }
    let hOn = (0..<3).filter { h[$0] == 1 }
    let visibleData = rbmV.map { ("\($0)", $0 == 1 ? NnTone.blue : .off) }
    let hiddenSample = h.map { ("\($0)", $0 == 1 ? NnTone.blue : .off) }
    let ghosts3 = Array(repeating: ("", NnTone.ghost), count: 3)
    let ghosts6 = Array(repeating: ("", NnTone.ghost), count: 6)
    let names = ["one", "two", "three", "four", "five", "six"]

    var up = [
        NnFrame(
            headline: "The visible layer holds one training example: {p:six binary units}.",
            body: "Every visible unit links to every hidden unit and to nothing in its own layer: 18 weights in all.",
            scene: rbmScene(visibleData, ghosts3) { _, _ in nil },
            formula: "v = {p:\(nnInts(rbmV))}",
            legend: [(.blue, "On"), (.off, "Off"), (.ghost, "Not computed")]
        ),
    ]
    let upHeads = [
        "Hidden unit 1 reads the {p:\(names[on.count - 1]) visible units} that are on. Nothing else feeds it.",
        "Hidden unit 2 reads the same \(names[on.count - 1]) units through its own weights: {\(nnf(ph[1]))}.",
        "Hidden unit 3's weights on those units are mostly negative, so p falls to {\(nnf(ph[2]))}.",
    ]
    let upBodies = [
        "No links inside a layer, so every hidden unit is one sigmoid and the whole layer samples in parallel.",
        "The wiring is the same for every hidden unit. The weights decide what each one detects.",
        "Its bias of \(nnw(rbmB[2])) barely matters against a \(nnw(pre[2] - rbmB[2])) pull from the weights.",
    ]
    for j in 0..<3 {
        let hidden: [(String, NnTone)] = (0..<3).map { k in
            k < j ? (nnf(ph[k]), .violet) : k == j ? (nnf(ph[k]), .current) : ("", .ghost)
        }
        let terms = nnSum(on.map { rbmW[$0][j] }) { "{p:\($0)}" }
        var legend: [(NnTone, String)] = [(.current, "Current"), (.blue, "On · feeds h\(nnSub(j + 1))"), (.off, "Off")]
        if j > 0 { legend.append((.violet, "p(h|v)")) }
        up.append(NnFrame(
            headline: upHeads[j], body: upBodies[j],
            scene: rbmScene(visibleData, hidden) { i, k in k == j && rbmV[i] == 1 ? .blue : nil },
            formula: "p(h\(nnSub(j + 1))|v) = σ( \(terms) \(rbmB[j] < 0 ? "−" : "+") \(nnw(abs(rbmB[j]))) ) = σ(\(nnf(pre[j]))) = {\(nnf(ph[j]))}",
            legend: legend
        ))
    }
    let offUnit = (0..<3).first { h[$0] == 0 }
    up.append(NnFrame(
        headline: "Each probability becomes a coin flip: {p:h = \(nnInts(h))}.",
        body: offUnit.map { "Unit \($0 + 1) drew \(nnf(rbmU[$0])), above its \(nnf(ph[$0])), so it stays off. The next half-step reads these 0s and 1s." }
            ?? "Every unit drew below its probability. The next half-step reads these 0s and 1s.",
        scene: rbmScene(visibleData, hiddenSample) { _, _ in nil },
        formula: "u = \(nnVec(rbmU)) → h = {p:\(nnInts(h))}",
        legend: [(.blue, "On"), (.off, "Off")]
    ))
    up.append(NnFrame(
        headline: "One matrix product does the whole layer: {v:p(h|v) = σ(Wᵀv + b)}.",
        body: "Hidden units never talk to each other, so they are independent given v. That is the \"restricted\" in RBM.",
        scene: rbmScene(visibleData, ph.map { (nnf($0), .violet) }) { i, _ in rbmV[i] == 1 ? .blue : nil },
        formula: "σ(Wᵀv + b) = {v:\(nnVec(ph))}",
        legend: [(.blue, "On"), (.off, "Off"), (.violet, "p(h|v)")]
    ))

    let downTerms = nnSum(hOn.map { rbmW[0][$0] }) { "{p:\($0)}" }
    let preV0 = rbmA[0] + hOn.reduce(0) { $0 + rbmW[0][$1] }
    let recon = pv.map { (nnf($0), NnTone.violet) }
    let dw = 0.1 * (v[0] * ph[0] - pv[0] * ph2[0])
    let down = [
        NnFrame(
            headline: "Now run it backwards: the sample {p:h = \(nnInts(h))} rebuilds the visible layer.",
            body: "The same 18 weights are used, transposed. There is no separate decoder.",
            scene: rbmScene(ghosts6, hiddenSample) { _, _ in nil },
            formula: "h = {p:\(nnInts(h))}",
            legend: [(.blue, "On"), (.off, "Off"), (.ghost, "Not computed")]
        ),
        NnFrame(
            headline: "Visible unit 1 reads the hidden units that are on: {\(nnf(pv[0]))}.",
            body: "It was \(rbmV[0]) in the data, so a value \(rbmV[0] == 1 ? "above" : "below") 0.5 is the right direction.",
            scene: rbmScene([(nnf(pv[0]), .current)] + ghosts6.dropFirst(), hiddenSample) { i, j in i == 0 && h[j] == 1 ? .blue : nil },
            formula: "p(v₁|h) = σ( \(downTerms) \(rbmA[0] < 0 ? "−" : "+") \(nnw(abs(rbmA[0]))) ) = σ(\(nnf(preV0))) = {\(nnf(pv[0]))}",
            legend: [(.current, "Current"), (.blue, "On · feeds v₁"), (.off, "Off")]
        ),
        NnFrame(
            headline: "The reconstruction {v:v′} leans the right way on all six units.",
            body: "Error so far: mean((v − v′)²) = \(nnf(err)). Training exists to push this down.",
            scene: rbmScene(recon, hiddenSample) { _, _ in nil },
            formula: "v′ = {v:\(nnVec(pv))}",
            legend: [(.violet, "Reconstruction"), (.blue, "On"), (.off, "Off")]
        ),
        NnFrame(
            headline: "One more upward pass gives the {v:negative phase}: p(h|v′).",
            body: "Contrastive divergence stops after this single step (CD-1) instead of running the chain to equilibrium.",
            scene: rbmScene(recon, ph2.map { (nnf($0), .violet) }) { _, _ in nil },
            formula: "p(h|v′) = {v:\(nnVec(ph2))}",
            legend: [(.violet, "Model's own values")]
        ),
        NnFrame(
            headline: "Each weight moves by {data minus reconstruction}.",
            body: "w₁₁ grows because v₁ and h₁ were on together more in the data than in the model's own reconstruction.",
            scene: rbmScene([(nnf(pv[0]), .current)] + recon.dropFirst(), [(nnf(ph2[0]), .current)] + ph2.dropFirst().map { (nnf($0), .violet) }) { i, j in
                i == 0 && j == 0 ? .current : nil
            },
            formula: "Δw₁₁ = 0.1 × (\(rbmV[0])·\(nnf(ph[0])) − \(nnf(pv[0]))·\(nnf(ph2[0]))) = {\(dw >= 0 ? "+" : "")\(nnf(dw))}",
            legend: [(.current, "Weight being updated"), (.violet, "Reconstruction")]
        ),
    ]
    return [NnTab(label: "v → h", frames: up), NnTab(label: "h → v", frames: down)]
}

// MARK: - Deep Belief Network

private let dbnW1: [[Double]] = [
    [-1.5, 2.6, -1.4],
    [-1.6, 2.4, -1.7],
    [-1.5, 2.2, -1.5],
    [2.3, -2.5, -2.0],
    [2.4, -2.4, -2.1],
    [2.2, -2.6, -2.2],
]
private let dbnW2: [[Double]] = [[-2.0, 2.9, -0.3], [2.4, -2.2, 0.5]]
private let dbnB2: [Double] = [-0.9, -0.4]
private let dbnUntrained: [Double] = [-0.08, 0.2, 0.04]

private func dbnScene(
    _ v: [Int], _ h1: [(String, NnTone)], _ h2: [(String, NnTone)], _ pills: [NnPill],
    lower: (Int, Int) -> NnEdgeTone?, upper: (Int, Int) -> NnEdgeTone?, frozen: Bool
) -> NnScene {
    let vy = nnSpread(6), h1y = nnSpread(3, 0.12, 0.88), h2y: [CGFloat] = [0.3, 0.62]
    let nodes = v.enumerated().map { i, bit in
        NnNode(x: 0.06, y: vy[i], text: "\(bit)", tone: frozen ? .green : bit == 1 ? .blue : .off, r: 15)
    } + h1.enumerated().map { j, n in NnNode(x: 0.5, y: h1y[j], text: n.0, tone: n.1, r: 19) } +
        h2.enumerated().map { k, n in NnNode(x: 0.92, y: h2y[k], text: n.0, tone: n.1, r: 19) }
    let edges = (0..<6).flatMap { i in (0..<3).map { j in NnEdge(from: i, to: 6 + j, tone: lower(i, j) ?? .faint) } } +
        (0..<3).flatMap { j in (0..<2).compactMap { k in upper(j, k).map { NnEdge(from: 6 + j, to: 9 + k, tone: $0) } } }
    return .net(NetScene(
        height: 220, nodes: nodes, edges: edges,
        footers: [NnLabel(text: "v · 6", align: .leading), NnLabel(text: "h¹ · 3", align: .center), NnLabel(text: "h² · 2", align: .trailing)],
        pills: pills
    ))
}

private func dbnTabs() -> [NnTab] {
    let a = [1, 1, 1, 0, 0, 0], b = [0, 0, 0, 1, 1, 1]
    func h1(_ v: [Int]) -> [Double] { (0..<3).map { j in nnSigmoid((0..<6).reduce(0) { $0 + dbnW1[$1][j] * Double(v[$1]) }) } }
    func h2(_ h: [Double]) -> [Double] { (0..<2).map { k in nnSigmoid(dbnB2[k] + (0..<3).reduce(0) { $0 + dbnW2[k][$1] * h[$1] }) } }
    let raw = dbnUntrained.map(nnSigmoid)
    let ha = h1(a), hb = h1(b)
    let topA = h2(ha), topB = h2(hb)
    let strongest = nnMax(ha)
    let ghosts2 = Array(repeating: ("", NnTone.ghost), count: 2)
    let frames = [
        NnFrame(
            headline: "Greedy step 1: train {p:RBM 1} on the raw data, exactly like a lone RBM.",
            body: "At its random starting weights every hidden unit sits near 0.5. It has learned nothing yet.",
            scene: dbnScene(a, raw.map { (nnf($0), .blue) }, ghosts2,
                            [NnPill(text: "RBM 1 · training", tone: .blue), NnPill(text: "RBM 2 · waiting", tone: .off)],
                            lower: { _, _ in .blue }, upper: { _, _ in nil }, frozen: false),
            formula: "h¹ = σ(W₁ᵀv) = {p:\(nnVec(raw))}",
            legend: [(.blue, "Training"), (.off, "Off"), (.ghost, "Not trained yet")]
        ),
        NnFrame(
            headline: "After CD-1 training, hidden unit \(strongest + 1) fires for this pattern: {\(nnf(ha[strongest]))}.",
            body: "The other two units have learned other patterns and stay near 0.",
            scene: dbnScene(a, ha.enumerated().map { j, p in (nnf(p), j == strongest ? .current : .blue) }, ghosts2,
                            [NnPill(text: "RBM 1 · trained", tone: .blue), NnPill(text: "RBM 2 · waiting", tone: .off)],
                            lower: { i, j in j == strongest && a[i] == 1 ? .blue : nil }, upper: { _, _ in nil }, frozen: false),
            formula: "h¹ = σ(W₁ᵀv) = [" + ha.enumerated().map { j, p in j == strongest ? "{\(nnf(p))}" : nnf(p) }.joined(separator: ", ") + "]",
            legend: [(.current, "Current"), (.blue, "Trained"), (.off, "Off")]
        ),
        NnFrame(
            headline: "RBM 1 is frozen. Its hidden activations are now {the only data} RBM 2 ever sees.",
            body: "Greedy stacking: each layer learns features of the layer below, one RBM at a time.",
            scene: dbnScene(a, ha.map { (nnf($0), .green) }, [(nnf(topA[0]), .current), ("", .ghost)],
                            [NnPill(text: "RBM 1 · frozen", tone: .green), NnPill(text: "RBM 2 · training", tone: .blue)],
                            lower: { _, _ in .green }, upper: { _, k in k == 0 ? .blue : nil }, frozen: true),
            formula: "RBM 2 data = h¹ = [ " + ha.map { "{p:\(nnf($0))}" }.joined(separator: " , ") + " ]",
            legend: [(.current, "Current"), (.blue, "Training"), (.green, "Frozen")]
        ),
        NnFrame(
            headline: "A different class lights {the other top unit}: h² = \(nnVec(topB)).",
            body: "No label was used anywhere. The top layer separates the classes on structure alone, which is why DBNs made good pretraining.",
            scene: dbnScene(b, hb.map { (nnf($0), .green) }, [(nnf(topB[0]), .green), (nnf(topB[1]), .current)],
                            [NnPill(text: "RBM 1 · frozen", tone: .green), NnPill(text: "RBM 2 · frozen", tone: .green)],
                            lower: { _, _ in .green }, upper: { _, k in k == 1 ? .blue : .green }, frozen: true),
            formula: "class A → \(nnVec(topA))   class B → [\(nnf(topB[0])), {\(nnf(topB[1]))}]",
            legend: [(.current, "Current"), (.blue, "Feeds it"), (.green, "Frozen")]
        ),
    ]
    return [NnTab(label: "", frames: frames)]
}

// MARK: - Batch Normalization

private let bnX: [Double] = [6.8, 8.9, 5.9, 10.4, 7.7, 9.0, 6.2, 9.6]
private let bnGamma = 1.2
private let bnBeta = 0.3

private func bnTabs() -> [NnTab] {
    let n = Double(bnX.count)
    let mu = bnX.reduce(0, +) / n
    let sd = (bnX.reduce(0) { $0 + ($1 - mu) * ($1 - mu) } / n).squareRoot()
    let xh = bnX.map { ($0 - mu) / sd }
    let y = xh.map { bnGamma * $0 + bnBeta }
    let focus = 3
    let rawMax = 11.5
    func raw(_ current: Int? = nil) -> [NnBar] { bnX.enumerated().map { i, v in NnBar(value: v, tone: i == current ? .current : .blue) } }
    let empty = Array(repeating: NnBar(value: nil, tone: .ghost), count: bnX.count)
    let scale = 1.8
    let muChips = [NnChip(key: "μ", value: nnf(mu)), NnChip(key: "σ", value: nnf(sd))]
    let norm = "x̂ = (x − μ) / σ"
    let muRun = 0.9 * 7.5 + 0.1 * mu
    let count = bnX.count
    let frames = [
        NnFrame(
            headline: "A mini-batch of eight: {p:one feature} from eight different samples.",
            body: "The values sit around 8 with a spread of a few units. The next layer would have to adapt to that scale.",
            scene: .bars(BarsScene(raw: raw(), rawMax: rawMax, band: nil, lowerLabel: norm, lower: empty, lowerScale: scale)),
            formula: "x = {p:[\(bnX.map { nnf($0, 1) }.joined(separator: ", "))]}",
            legend: [(.blue, "Raw")]
        ),
        NnFrame(
            headline: "The batch mean is {v:\(nnf(mu))} and its spread σ is {v:\(nnf(sd))}.",
            body: "Both are computed across the batch, separately for every feature.",
            scene: .bars(BarsScene(raw: raw(), rawMax: rawMax, band: (mu, sd), lowerLabel: norm, lower: empty, lowerScale: scale)),
            formula: "μ = Σx / \(count) = {v:\(nnf(mu))}   σ = √(Σ(x − μ)² / \(count)) = {v:\(nnf(sd))}",
            legend: [(.blue, "Raw"), (.band, "μ ± σ")],
            chips: muChips
        ),
        NnFrame(
            headline: "Sample \(focus + 1) is \(nnf(xh[focus])) σ \(xh[focus] >= 0 ? "above" : "below") the batch mean, so it normalizes to {\(nnf(xh[focus]))}.",
            body: "μ and σ are taken across the batch, per feature. At inference, running averages replace them.",
            scene: .bars(BarsScene(
                raw: raw(focus), rawMax: rawMax, band: (mu, sd), lowerLabel: norm,
                lower: xh.enumerated().map { i, v in i < focus ? NnBar(value: v, tone: .green) : i == focus ? NnBar(value: v, tone: .current) : NnBar(value: nil, tone: .ghost) },
                lowerScale: scale
            )),
            formula: "x̂\(nnSub(focus + 1)) = ( {\(nnw(bnX[focus]))} − {p:\(nnf(mu))} ) / {v:\(nnf(sd))} = {\(nnf(xh[focus]))}",
            legend: [(.current, "Current"), (.blue, "Raw"), (.green, "Normalized"), (.band, "μ ± σ")],
            chips: muChips
        ),
        NnFrame(
            headline: "Now the batch has {m:mean 0 and σ 1}, whatever scale it arrived in.",
            body: "Add 100 to every input and these eight bars would not move.",
            scene: .bars(BarsScene(raw: raw(), rawMax: rawMax, band: (mu, sd), lowerLabel: norm, lower: xh.map { NnBar(value: $0, tone: .green) }, lowerScale: scale)),
            formula: "mean(x̂) = {m:\(nnf(xh.reduce(0, +) / n))}   std(x̂) = {m:\(nnf((xh.reduce(0) { $0 + $1 * $1 } / n).squareRoot()))}",
            legend: [(.blue, "Raw"), (.green, "Normalized"), (.band, "μ ± σ")],
            chips: muChips
        ),
        NnFrame(
            headline: "Learned {v:γ and β} rescale it, so the layer can undo the normalization if that helps.",
            body: "With γ = σ and β = μ the original values come back exactly. Normalizing never removes what the network can express.",
            scene: .bars(BarsScene(
                raw: raw(focus), rawMax: rawMax, band: (mu, sd), lowerLabel: "y = γ x̂ + β",
                lower: y.enumerated().map { i, v in NnBar(value: v, tone: i == focus ? .current : .violet) }, lowerScale: scale
            )),
            formula: "y\(nnSub(focus + 1)) = {v:\(nnw(bnGamma))} × \(nnf(xh[focus])) + {v:\(nnw(bnBeta))} = {\(nnf(y[focus]))}",
            legend: [(.current, "Current"), (.blue, "Raw"), (.violet, "After γ, β"), (.band, "μ ± σ")],
            chips: [NnChip(key: "γ", value: nnw(bnGamma)), NnChip(key: "β", value: nnw(bnBeta))]
        ),
        NnFrame(
            headline: "At inference there is no batch. A {running average} of μ and σ stands in.",
            body: "It is updated on every training step, so one test example is normalized the same way every time.",
            scene: .bars(BarsScene(raw: raw(), rawMax: rawMax, band: (mu, sd), lowerLabel: norm, lower: xh.map { NnBar(value: $0, tone: .green) }, lowerScale: scale)),
            formula: "μ_run ← 0.9 × 7.50 + 0.1 × \(nnf(mu)) = {\(nnf(muRun))}",
            legend: [(.blue, "Raw"), (.green, "Normalized"), (.band, "μ ± σ")],
            chips: [NnChip(key: "μ_run", value: nnf(muRun), tone: .active)]
        ),
    ]
    return [NnTab(label: "", frames: frames)]
}

// MARK: - Backpropagation

private let bpX: [Double] = [0.9, 0.2]
private let bpW1: [[Double]] = [[0.6, 0.4], [-0.5, 0.3], [0.3, 0.2]]
private let bpW2: [Double] = [1.2, 0.8, 0.6]
private let bpB2 = -0.73
private let bpTarget = 0.6
private let bpEta = 0.5

private func bpScene(
    _ hidden: [(String, NnTone)], _ out: (String, NnTone), badges: [NnBadge?] = [nil, nil, nil, nil],
    edgeTone: (Int) -> NnEdgeTone, edgeLabel: (Int) -> String
) -> NnScene {
    let hy = nnSpread(3)
    let nodes = [
        NnNode(x: 0.1, y: 0.3, text: nnf(bpX[0]), tone: .blue, r: 20),
        NnNode(x: 0.1, y: 0.72, text: nnf(bpX[1]), tone: .blue, r: 20),
    ] + hidden.enumerated().map { j, n in NnNode(x: 0.5, y: hy[j], text: n.0, tone: n.1, r: 20, badge: badges[j]) } +
        [NnNode(x: 0.88, y: 0.5, text: out.0, tone: out.1, r: 20, badge: badges[3])]
    let edges = (0..<2).flatMap { i in (0..<3).map { j in NnEdge(from: i, to: 2 + j, tone: .faint) } } +
        (0..<3).map { j in NnEdge(from: 2 + j, to: 5, tone: edgeTone(j), label: edgeLabel(j)) }
    return .net(NetScene(
        height: 230, nodes: nodes, edges: edges,
        headers: [NnLabel(text: "INPUT", align: .leading), NnLabel(text: "HIDDEN · ReLU", align: .center), NnLabel(text: "OUTPUT", align: .trailing)]
    ))
}

private func bpTabs() -> [NnTab] {
    let pre = bpW1.map { $0[0] * bpX[0] + $0[1] * bpX[1] }
    let h = pre.map { max(0, $0) }
    let z = (0..<3).reduce(0) { $0 + bpW2[$1] * h[$1] } + bpB2
    let out = nnSigmoid(z)
    let d = out - bpTarget
    let dh = (0..<3).map { j in pre[j] > 0 ? d * bpW2[j] : 0 }
    let grad = h.map { d * $0 }
    let w2n = (0..<3).map { bpW2[$0] - bpEta * grad[$0] }
    let b2n = bpB2 - bpEta * d
    let w1n = (0..<3).map { j in (0..<2).map { k in bpW1[j][k] - bpEta * dh[j] * bpX[k] } }
    let h2 = w1n.map { max(0, $0[0] * bpX[0] + $0[1] * bpX[1]) }
    let out2 = nnSigmoid((0..<3).reduce(0) { $0 + w2n[$1] * h2[$1] } + b2n)
    let silent = (0..<3).first { pre[$0] <= 0 }!
    func hid(_ tones: (Int) -> NnTone = { pre[$0] > 0 ? .blue : .off }) -> [(String, NnTone)] { h.enumerated().map { j, v in (nnf(v), tones(j)) } }
    let weights = { (j: Int) in "w \(nnw(bpW2[j]))" }
    let forward = { (j: Int) -> NnEdgeTone in pre[j] > 0 ? .blue : .faint }
    let dBadge = NnBadge(text: "δ \(nnf(d))", warn: true)
    let hBadges: [NnBadge?] = dh.enumerated().map { j, v in pre[j] > 0 ? NnBadge(text: "δ \(nnf(v))", warn: true) : NnBadge(text: "δ 0", warn: false) }
    let tracing = { (j: Int) -> NnTone in j == 0 ? .current : pre[j] > 0 ? .blue : .off }
    let back = { (j: Int) -> NnEdgeTone in pre[j] > 0 ? .red : .redDashed }
    let frames = [
        NnFrame(
            headline: "Forward pass: the network predicts {v:\(nnf(out))}. The target is \(nnf(bpTarget)).",
            body: "Hidden unit \(silent + 1)'s weighted sum is \(nnf(pre[silent])), so ReLU outputs exactly 0. Remember that.",
            scene: bpScene(hid(), (nnf(out), .violet), edgeTone: forward, edgeLabel: weights),
            formula: "ŷ = σ(\((0..<3).map { "\(nnw(bpW2[$0]))·\(nnf(h[$0]))" }.joined(separator: " + ")) − \(nnw(-bpB2))) = {v:\(nnf(out))}",
            legend: [(.blue, "Forward value"), (.violet, "Output"), (.off, "Silent")]
        ),
        NnFrame(
            headline: "The output is too low by \(nnf(-d)), so its error is {w:δ = \(nnf(d))}.",
            body: "With a sigmoid output and cross-entropy loss, δ is simply prediction minus target.",
            scene: bpScene(hid(), (nnf(out), .violet), badges: [nil, nil, nil, dBadge], edgeTone: forward, edgeLabel: weights),
            formula: "δₒ = ŷ − y = \(nnf(out)) − \(nnf(bpTarget)) = {w:\(nnf(d))}",
            legend: [(.blue, "Forward value"), (.warn, "Gradient δ"), (.off, "Silent")]
        ),
        NnFrame(
            headline: "The output's error {w:δ = \(nnf(d))} flows back along each weight. Unit 1 gets \(nnf(d)) × \(nnw(bpW2[0])).",
            body: "Unit \(silent + 1) never fired, so ReLU′ is 0 and it receives no gradient at all.",
            scene: bpScene(hid(tracing), (nnf(out), .violet), badges: hBadges + [dBadge], edgeTone: back, edgeLabel: weights),
            formula: "δh₁ = {w:\(nnf(d))} × w {p:\(nnw(bpW2[0]))} × ReLU′ {p:1} = {\(nnf(dh[0]))}",
            legend: [(.current, "Current"), (.blue, "Forward value"), (.warn, "Gradient δ"), (.off, "Silent")]
        ),
        NnFrame(
            headline: "A weight's gradient is the δ it feeds times the value it carries: {w:\(nnf(grad[0], 3))} for w₁.",
            body: "The weight from the silent unit carried 0, so its gradient is 0 too. Input weights get δh × x the same way.",
            scene: bpScene(hid(tracing), (nnf(out), .violet), badges: [nil, nil, nil, dBadge], edgeTone: back, edgeLabel: { "∂ \(nnf(grad[$0], 3))" }),
            formula: "∂L/∂w₁ = δₒ × h₁ = \(nnf(d)) × \(nnf(h[0])) = {w:\(nnf(grad[0], 3))}",
            legend: [(.current, "Current"), (.blue, "Forward value"), (.warn, "Gradient"), (.off, "Silent")]
        ),
        NnFrame(
            headline: "One update later the prediction moves from \(nnf(out)) to {v:\(nnf(out2))}, toward \(nnf(bpTarget)).",
            body: "Every weight stepped against its gradient at η = \(nnw(bpEta)). Repeat over many examples and that is training.",
            scene: bpScene(h2.map { (nnf($0), $0 > 0 ? .blue : .off) }, (nnf(out2), .current),
                           edgeTone: { h2[$0] > 0 ? .blue : .faint }, edgeLabel: { "w \(nnf(w2n[$0]))" }),
            formula: "w₁ ← \(nnw(bpW2[0])) − \(nnw(bpEta)) × (\(nnf(grad[0], 3))) = {\(nnf(w2n[0]))}",
            legend: [(.current, "New prediction"), (.blue, "Forward value"), (.off, "Silent")]
        ),
    ]
    return [NnTab(label: "", frames: frames)]
}

// MARK: - Autoencoders

private let aeX: [Double] = [0.90, 0.80, 0.60, 0.40, 0.20, 0.10]
private let aeNoise: [Double] = [0.08, -0.14, 0.11, -0.12, 0.10, -0.07]

/// A linear 6 → 2 → 6 autoencoder at its optimum: encode onto a level and a slope, decode back.
private func aeRun(_ x: [Double]) -> ([Double], [Double]) {
    let level = Array(repeating: 1 / 6.0.squareRoot(), count: 6)
    let ramp = [5.0, 3.0, 1.0, -1.0, -3.0, -5.0].map { $0 / 70.0.squareRoot() }
    let z = [level, ramp].map { b in x.indices.reduce(0) { $0 + x[$1] * b[$1] } }
    return (z, x.indices.map { z[0] * level[$0] + z[1] * ramp[$0] })
}

private func nnMse(_ a: [Double], _ b: [Double]) -> Double { a.indices.reduce(0) { $0 + (a[$1] - b[$1]) * (a[$1] - b[$1]) } / Double(a.count) }

private func aeScene(_ input: [(String, NnTone)], _ code: [(String, NnTone)], _ output: [(String, NnTone)]) -> NnScene {
    let y6 = nnSpread(6), y2: [CGFloat] = [0.34, 0.66]
    let nodes = input.enumerated().map { i, n in NnNode(x: 0.08, y: y6[i], text: n.0, tone: n.1, r: 14) } +
        code.enumerated().map { k, n in NnNode(x: 0.5, y: y2[k], text: n.0, tone: n.1, r: 20) } +
        output.enumerated().map { i, n in NnNode(x: 0.92, y: y6[i], text: n.0, tone: n.1, r: 14) }
    let edges = (0..<6).flatMap { i in (0..<2).map { k in NnEdge(from: i, to: 6 + k, tone: .faint) } } +
        (0..<2).flatMap { k in (0..<6).map { i in NnEdge(from: 6 + k, to: 8 + i, tone: .faint) } }
    return .net(NetScene(
        height: 240, nodes: nodes, edges: edges,
        headers: [NnLabel(text: "INPUT x", align: .leading), NnLabel(text: "CODE · 2", align: .center), NnLabel(text: "OUTPUT x̂", align: .trailing)],
        bands: [NnBand(x0: 0.08, top0: 0, bottom0: 1, x1: 0.5, top1: 0.34, bottom1: 0.66), NnBand(x0: 0.5, top0: 0.34, bottom0: 0.66, x1: 0.92, top1: 0, bottom1: 1)]
    ))
}

private func aeTabs() -> [NnTab] {
    let (z, xh) = aeRun(aeX)
    let loss = nnMse(aeX, xh)
    let errs = aeX.indices.map { abs(aeX[$0] - xh[$0]) }
    let worst = nnMax(errs)
    let xt = aeX.indices.map { aeX[$0] + aeNoise[$0] }
    let (zt, xht) = aeRun(xt)
    let inLoss = nnMse(xt, aeX), outLoss = nnMse(xht, aeX)
    let input = aeX.map { (nnf($0), NnTone.blue) }
    let noisy = xt.map { (nnf($0), NnTone.warn) }
    let ghost2 = Array(repeating: ("", NnTone.ghost), count: 2)
    let ghost6 = Array(repeating: ("", NnTone.ghost), count: 6)
    let code = z.map { (nnf($0), NnTone.violet) }
    let codeT = zt.map { (nnf($0), NnTone.violet) }
    let out = xh.map { (nnf($0), NnTone.green) }
    let legend: [(NnTone, String)] = [(.blue, "Input"), (.violet, "Code"), (.green, "Reconstruction")]
    let mark = { (xs: [(String, NnTone)]) in xs.enumerated().map { i, p in i == worst ? (p.0, NnTone.current) : p } }
    let plain = [
        NnFrame(
            headline: "Six numbers go in. The middle layer has room for only {v:two}.",
            body: "Nothing but the shape forces compression: the output is trained to match the input.",
            scene: aeScene(input, ghost2, ghost6),
            formula: "x = {p:\(nnVec(aeX))}",
            legend: [(.blue, "Input"), (.ghost, "Not computed")]
        ),
        NnFrame(
            headline: "The encoder squeezes x into a two-number code, {v:z = \(nnVec(z))}.",
            body: "The first number tracks the overall level, the second the downward slope.",
            scene: aeScene(input, code, ghost6),
            formula: "z = Eᵀx = {v:\(nnVec(z))}",
            legend: Array(legend.prefix(2)) + [(.ghost, "Not computed")]
        ),
        NnFrame(
            headline: "Six numbers squeezed through two come back within {m:\(nnf(errs.max()!))} of where they started.",
            body: "To fit in two units, the encoder has to keep the structure and drop the rest.",
            scene: aeScene(input, code, out),
            formula: "loss = mean((x − x̂)²) = {m:\(nnf(loss, 4))}",
            legend: legend
        ),
        NnFrame(
            headline: "What two numbers cannot hold is lost: unit \(worst + 1) comes back as {\(nnf(xh[worst]))}, not \(nnf(aeX[worst])).",
            body: "x is not exactly a straight ramp, and the code only has room for a level and a slope.",
            scene: aeScene(mark(input), code, mark(out)),
            formula: "|x\(nnSub(worst + 1)) − x̂\(nnSub(worst + 1))| = |\(nnf(aeX[worst])) − \(nnf(xh[worst]))| = {\(nnf(errs[worst]))}",
            legend: [(.current, "Largest miss")] + legend
        ),
    ]
    let noiseMax = aeNoise.map(abs).max()!
    let denoise = [
        NnFrame(
            headline: "Now corrupt the input: every value is nudged by up to {w:\(nnf(noiseMax))}.",
            body: "The target stays the clean x. The network never sees it at the input.",
            scene: aeScene(noisy, ghost2, ghost6),
            formula: "loss(x̃, x) = mean((x̃ − x)²) = {w:\(nnf(inLoss, 4))}",
            legend: [(.warn, "Corrupted input"), (.ghost, "Not computed")]
        ),
        NnFrame(
            headline: "The code barely moves: {v:\(nnVec(zt))} against a clean \(nnVec(z)).",
            body: "Noise that doesn't line up with a level or a slope has nowhere to go in two numbers.",
            scene: aeScene(noisy, codeT, ghost6),
            formula: "z = Eᵀx̃ = {v:\(nnVec(zt))}",
            legend: [(.warn, "Corrupted input"), (.violet, "Code"), (.ghost, "Not computed")]
        ),
        NnFrame(
            headline: "The output lands back near the clean x: the error falls {m:about \(nnf(inLoss / outLoss, 0))×}.",
            body: "Squeezing through the bottleneck throws away the directions the noise lives in.",
            scene: aeScene(noisy, codeT, xht.map { (nnf($0), .green) }),
            formula: "loss(x̂, x) = {m:\(nnf(outLoss, 4))}  vs  \(nnf(inLoss, 4)) in",
            legend: [(.warn, "Corrupted input"), (.violet, "Code"), (.green, "Reconstruction")]
        ),
        NnFrame(
            headline: "A denoising autoencoder is trained on exactly this: {w:noisy} in, {m:clean} out.",
            body: "Learning to undo corruption forces features that describe the data rather than copy the input.",
            scene: aeScene(noisy, codeT, aeX.map { (nnf($0), .green) }),
            formula: "minimize mean((x − dec(enc(x̃)))²)",
            legend: [(.warn, "Corrupted input"), (.violet, "Code"), (.green, "Clean target")]
        ),
    ]
    return [NnTab(label: "Plain", frames: plain), NnTab(label: "Denoising", frames: denoise)]
}

// MARK: - Dropout

private let dropHidden: [Double] = [0.8, 0.8, 0.4, 1.6, 0.9, 0.2]
private let dropIn: [Double] = [1.0, 0.6]

/// Three passes' masks per number of dropped units (1 to 3 of 6).
private let dropMasks: [Int: [Set<Int>]] = [
    1: [[1], [4], [2]],
    2: [[1, 5], [0, 3], [2, 4]],
    3: [[1, 3, 5], [0, 2, 4], [0, 1, 5]],
]

private let dropoutMin = 1
private let dropoutMax = 3

private func dropP(_ k: Int) -> String { k == 2 ? "1/3" : k == 3 ? "1/2" : "\(k)/6" }

private func dropScene(_ values: [(String, NnTone)], _ dropped: Set<Int>, _ sides: [String?], _ out: (String, NnTone)) -> NnScene {
    let hy = nnSpread(6)
    let nodes = [
        NnNode(x: 0.1, y: 0.3, text: nnf(dropIn[0]), tone: .blue, r: 21),
        NnNode(x: 0.1, y: 0.7, text: nnf(dropIn[1]), tone: .blue, r: 21),
    ] + values.enumerated().map { j, n in NnNode(x: 0.5, y: hy[j], text: n.0, tone: n.1, r: 16, side: sides[j]) } +
        [NnNode(x: 0.88, y: 0.5, text: out.0, tone: out.1, r: 21)]
    let kept = (0..<6).filter { !dropped.contains($0) }
    let edges = (0..<2).flatMap { i in kept.map { j in NnEdge(from: i, to: 2 + j, tone: .faint) } } +
        kept.map { j in NnEdge(from: 2 + j, to: 8, tone: .blue) }
    return .net(NetScene(
        height: 240, nodes: nodes, edges: edges,
        headers: [NnLabel(text: "INPUT", align: .leading), NnLabel(text: "HIDDEN · 6", align: .center), NnLabel(text: "OUTPUT", align: .trailing)]
    ))
}

private func dropoutTabs(_ k: Int) -> [NnTab] {
    let kept = 6 - k
    let scale = 6.0 / Double(kept)
    let p = dropP(k)
    let expected = dropHidden.reduce(0, +)
    let words = ["None", "One", "Two", "Three", "Four", "Five", "Six"]
    let masks = dropMasks[k]!
    let allMasks = (0..<64).filter { $0.nonzeroBitCount == k }
    let combos = allMasks.count
    // Every mask with k units off, each kept unit scaled: the mean output is exactly the full sum.
    let meanOut = allMasks.reduce(0.0) { acc, m in acc + (0..<6).filter { (m >> $0) & 1 == 0 }.reduce(0.0) { $0 + dropHidden[$1] * scale } } / Double(combos)
    func pass(_ mask: Set<Int>, scaled: Bool) -> (NnScene, Double) {
        let f = scaled ? scale : 1
        let outV = (0..<6).filter { !mask.contains($0) }.reduce(0.0) { $0 + dropHidden[$1] * f }
        let values: [(String, NnTone)] = dropHidden.enumerated().map { j, v in mask.contains(j) ? ("", .dropped) : (nnf(v * f), .blue) }
        let sides: [String?] = dropHidden.enumerated().map { j, v in mask.contains(j) ? nnf(v * f) : nil }
        return (dropScene(values, mask, sides, (nnf(outV), .violet)), outV)
    }
    let full = dropScene(dropHidden.map { (nnf($0), .blue) }, [], Array(repeating: nil, count: 6), (nnf(expected), .violet))
    let legendFull: [(NnTone, String)] = [(.blue, "Active"), (.violet, "Output")]
    let legendDrop: [(NnTone, String)] = [(.blue, "Kept, scaled"), (.dropped, "Dropped"), (.violet, "Output")]
    func chip(_ v: Double) -> [NnChip] { [NnChip(key: "Σ expected → this pass", value: "\(nnf(expected)) → \(nnf(v))")] }
    let (raw1, rawOut1) = pass(masks[0], scaled: false)
    let passes = masks.map { pass($0, scaled: true) }
    let frames = [
        NnFrame(
            headline: "Without dropout, all {p:six hidden units} feed the output: {v:\(nnf(expected))}.",
            body: "That sum is what the network produces at test time, so training should hit it on average.",
            scene: full, legend: legendFull, chips: [NnChip(key: "Σ expected", value: nnf(expected))]
        ),
        NnFrame(
            headline: "Pass 1: a random mask switches {w:\(words[k].lowercased()) of six} units off.",
            body: "Each unit is dropped with probability p = \(p), independently, on every training step. Unscaled, the output falls to \(nnf(rawOut1)).",
            scene: raw1, legend: [(.blue, "Kept"), (.dropped, "Dropped"), (.violet, "Output")], chips: chip(rawOut1)
        ),
        NnFrame(
            headline: "{w:\(words[k])} of six units \(k == 1 ? "is" : "are") off. The \(words[kept].lowercased()) left are scaled by {1/(1 − p) = \(nnw(scale))}.",
            body: "No unit can rely on a neighbour, so each learns to stand alone.",
            scene: passes[0].0, legend: legendDrop, chips: chip(passes[0].1)
        ),
        NnFrame(
            headline: "Pass 2: a {different mask}, a different thinned network: {v:\(nnf(passes[1].1))}.",
            body: "Every step trains one of the \(combos) sub-networks with \(k) unit\(k == 1 ? "" : "s") off. They all share the same weights.",
            scene: passes[1].0, legend: legendDrop, chips: chip(passes[1].1)
        ),
        NnFrame(
            headline: "Pass 3 gives {v:\(nnf(passes[2].1))}. Each pass lands somewhere different.",
            body: "The noise is the point: the network cannot memorize one exact path through its units.",
            scene: passes[2].0, legend: legendDrop, chips: chip(passes[2].1)
        ),
        NnFrame(
            headline: "Averaged over all \(combos) masks, the scaled output is exactly {v:\(nnf(meanOut))}.",
            body: "Passes of \(passes.map { nnf($0.1) }.joined(separator: ", ")) scatter around the full network's sum. That is what 1/(1 − p) buys.",
            scene: full, formula: "mean over \(combos) masks = {v:\(nnf(meanOut))} = Σ expected", legend: legendFull,
            chips: [NnChip(key: "mean of \(combos) masks", value: nnf(meanOut), tone: .answer)]
        ),
        NnFrame(
            headline: "At test time nothing is dropped and {m:nothing is scaled}: \(nnf(expected)).",
            body: "Training already scaled up the kept units (\"inverted\" dropout), so inference uses the weights as they are.",
            scene: full, legend: legendFull, chips: [NnChip(key: "inference", value: nnf(expected), tone: .done)]
        ),
    ]
    return [NnTab(label: "", frames: frames)]
}

// MARK: - Activation functions

private let geluK = (2 / Double.pi).squareRoot()
private func nnGelu(_ x: Double) -> Double { 0.5 * x * (1 + tanh(geluK * (x + 0.044715 * x * x * x))) }
private func nnGeluPrime(_ x: Double) -> Double {
    let t = tanh(geluK * (x + 0.044715 * x * x * x))
    return 0.5 * (1 + t) + 0.5 * x * (1 - t * t) * geluK * (1 + 3 * 0.044715 * x * x)
}
private func nnTanhPrime(_ x: Double) -> Double { 1 - tanh(x) * tanh(x) }

private let actProbes: [Double] = [-2.0, -0.5, 0.5, 1.5, 3.0]

private func activationTabs() -> [NnTab] {
    let xs = (0...160).map { -4.0 + Double($0) * 0.05 }
    func curve(_ f: (Double) -> Double, _ tone: NnTone) -> NnCurve { NnCurve(segments: [xs.map { ($0, f($0)) }], tone: tone) }
    let relu = { (x: Double) in max(0, x) }
    let reluPrime = NnCurve(segments: [xs.filter { $0 <= 0 }.map { ($0, 0.0) }, xs.filter { $0 >= 0 }.map { ($0, 1.0) }], tone: .blue)
    let fnCurves = [curve(relu, .blue), curve(tanh, .green), curve(nnGelu, .violet)]
    let dCurves = [reluPrime, curve(nnTanhPrime, .green), curve(nnGeluPrime, .violet)]
    let fnHeads = [
        "At x = −2, ReLU outputs {exactly 0}, and tanh is already near its floor of −1.",
        "GELU dips {below zero} here, to \(nnf(nnGelu(-0.5))), where ReLU is flat at 0.",
        "Just past zero all three rise: ReLU {\(nnf(0.5))}, tanh \(nnf(tanh(0.5))), GELU \(nnf(nnGelu(0.5))).",
        "At x = 1.5 tanh has bent to {\(nnf(tanh(1.5)))}, while ReLU keeps going to 1.50.",
        "By x = 3, GELU and ReLU agree at {\(nnf(nnGelu(3)))} and tanh is pinned at \(nnf(tanh(3))).",
    ]
    let fnBodies = [
        "A negative input switches a ReLU unit off completely. GELU lets a little through.",
        "That small negative bump makes GELU smooth at zero, which ReLU is not.",
        "Near zero, tanh is almost the identity and GELU is about half of x.",
        "tanh squashes everything into (−1, 1). ReLU passes large values through unchanged.",
        "For large inputs GELU becomes ReLU. The difference lives only near zero.",
    ]
    let dHeads = [
        "Below zero, {ReLU′ = 0}: no gradient flows back through a unit that is off.",
        "tanh′ is {\(nnf(nnTanhPrime(-0.5)))} here, but ReLU′ is still exactly 0.",
        "Past zero, {ReLU′ = 1}. tanh′ has already fallen to \(nnf(nnTanhPrime(0.5))).",
        "At x = 1.5, ReLU′ is exactly 1. tanh′ has already dropped to {\(nnf(nnTanhPrime(1.5)))}.",
        "At x = 3, tanh′ is {w:\(nnf(nnTanhPrime(3)))}: a saturated tanh unit has stopped learning.",
    ]
    let dBodies = [
        "tanh′ is \(nnf(nnTanhPrime(-2))), and GELU′ is even slightly negative, \(nnf(nnGeluPrime(-2))).",
        "A unit whose input stays negative for every example is a dead ReLU: it never updates again.",
        "GELU′ is \(nnf(nnGeluPrime(0.5))) and still rising. It overshoots 1 before it settles.",
        "Ten layers of \(nnf(nnTanhPrime(1.5))) multiply the gradient down to almost nothing. ReLU passes it on unchanged.",
        "ReLU′ and GELU′ are both about 1. This is why deep networks moved to ReLU-style activations.",
    ]
    func frames(_ derivative: Bool) -> [NnFrame] {
        actProbes.enumerated().map { i, x in
            let values = derivative ? [x > 0 ? 1.0 : 0.0, nnTanhPrime(x), nnGeluPrime(x)] : [relu(x), tanh(x), nnGelu(x)]
            let tones: [NnTone] = [.blue, .green, .violet]
            let names = derivative ? ["ReLU′", "tanh′", "GELU′"] : ["ReLU", "tanh", "GELU"]
            return NnFrame(
                headline: derivative ? dHeads[i] : fnHeads[i],
                body: derivative ? dBodies[i] : fnBodies[i],
                scene: .plot(PlotScene(
                    height: 200, xRange: -4...4, yRange: derivative ? -0.3...1.3 : -1.3...3.3,
                    curves: derivative ? dCurves : fnCurves,
                    dots: values.enumerated().map { k, v in NnDot(x: x, y: v, tone: tones[k]) },
                    refY: 1, probe: (x, "x = \(nnw(x))")
                )),
                legend: names.enumerated().map { k, n in (tones[k], n) } + [(.current, "Probe x")],
                chips: values.enumerated().map { k, v in NnChip(key: names[k], value: nnf(v), dot: tones[k]) }
            )
        }
    }
    return [NnTab(label: "Function", frames: frames(false)), NnTab(label: "Derivative", frames: frames(true))]
}

// MARK: - Multi-layer perceptron (XOR)

private func mlpTabs() -> [NnTab] {
    let rows = [(0, 0), (0, 1), (1, 0), (1, 1)]
    let or = rows.map { Double($0.0 + $0.1) - 0.5 > 0 ? 1 : 0 }
    let and = rows.map { Double($0.0 + $0.1) - 1.5 > 0 ? 1 : 0 }
    let y = rows.indices.map { or[$0] == 1 && and[$0] == 0 ? 1 : 0 }
    func scene(_ hCols: Int, _ current: Int?, hidden: Bool, hiddenLine: Bool = false, inputLine: Bool = false) -> NnScene {
        let table: [[(String, XorCellTone)]] = rows.enumerated().map { r, row in
            let base: XorCellTone = r == current ? .row : .idle
            return [
                ("\(row.0)", base),
                ("\(row.1)", base),
                hCols >= 1 ? ("\(or[r])", base) : ("", .empty),
                hCols >= 2 ? ("\(and[r])", base) : ("", .empty),
                ("\(y[r])", r == current ? .current : y[r] == 1 ? .one : .idle),
            ]
        }
        let input = rows.enumerated().map { r, row in XorPoint(x: row.0, y: row.1, filled: y[r] == 1, ring: r == current) }
        let hid = hidden ? rows.indices.map { r in XorPoint(x: or[r], y: and[r], filled: y[r] == 1, ring: r == current) } : []
        return .xor(XorScene(input: input, hidden: hid, inputLine: inputLine, hiddenLine: hiddenLine, rows: table))
    }
    let legend: [(NnTone, String)] = [(.current, "Current"), (.violet, "y = 1"), (.off, "y = 0")]
    let noCurrent = Array(legend.dropFirst())
    let rule = { (r: Int) in "y = h₁ AND NOT h₂ = {p:\(or[r])} AND NOT {p:\(and[r])} = {\(y[r])}" }
    let frames = [
        NnFrame(headline: "XOR is 1 when {v:exactly one} input is 1.",
                body: "Plot the four rows: the two 1s sit on opposite corners of the square.",
                scene: scene(0, nil, hidden: false), legend: noCurrent),
        NnFrame(headline: "{w:No single line} separates the violet corners from the hollow ones.",
                body: "One unit draws one line, so a lone perceptron cannot learn XOR. A hidden layer can.",
                scene: scene(0, nil, hidden: false, inputLine: true),
                formula: "one unit: y = step(w₁x₁ + w₂x₂ + b)  →  {w:no w, b works}", legend: noCurrent),
        NnFrame(headline: "Hidden unit 1 computes {p:OR}: it fires if either input is on.",
                body: "Its line cuts (0,0) off from the other three corners.",
                scene: scene(1, nil, hidden: false), formula: "h₁ = step(x₁ + x₂ − 0.5)  →  {p:OR}", legend: noCurrent),
        NnFrame(headline: "Hidden unit 2 computes {p:AND}: it fires only when both are on.",
                body: "A second line, cutting (1,1) off from the rest.",
                scene: scene(2, nil, hidden: false), formula: "h₂ = step(x₁ + x₂ − 1.5)  →  {p:AND}", legend: noCurrent),
        NnFrame(headline: "Replot each row at (h₁, h₂): (0,1) and (1,0) {land on one point}.",
                body: "The hidden layer has moved the points. Only three distinct positions are left.",
                scene: scene(2, nil, hidden: true), formula: "(x₁, x₂) → (h₁, h₂) = (OR, AND)", legend: noCurrent),
        NnFrame(headline: "Row (0,0): neither hidden unit fires, so {y = 0}.",
                body: "The output unit computes h₁ AND NOT h₂: on for OR, but vetoed by AND.",
                scene: scene(2, 0, hidden: true), formula: rule(0), legend: legend),
        NnFrame(headline: "Row (0,1): OR fires and AND doesn't, so {y = 1}.",
                body: "Exactly the case XOR wants to be 1.",
                scene: scene(2, 1, hidden: true), formula: rule(1), legend: legend),
        NnFrame(headline: "Row (1,0) lands on the same hidden point as (0,1), so it gets {the same answer}.",
                body: "Once two inputs share a hidden point, no later layer can tell them apart. Here that is exactly right.",
                scene: scene(2, 2, hidden: true), formula: rule(2), legend: legend),
        NnFrame(headline: "In hidden space, (0,1) and (1,0) land on one point. {A single line} now splits the classes.",
                body: "Row (1,1) fires both OR and AND, so the output unit turns it off.",
                scene: scene(2, 3, hidden: true, hiddenLine: true), formula: rule(3), legend: legend),
    ]
    return [NnTab(label: "", frames: frames)]
}

// MARK: - Gradient descent variants

private let gdSteep = 12.0
private let gdStart = (-4.0, 1.2)
private let gdCheckpoints = [0, 3, 8, 20, 40]

private func gdLoss(_ w: (Double, Double)) -> Double { 0.5 * (w.0 * w.0 + gdSteep * w.1 * w.1) }

private func gdPaths(_ steps: Int) -> [[(Double, Double)]] {
    func grad(_ w: (Double, Double)) -> (Double, Double) { (w.0, gdSteep * w.1) }
    var sgd = [gdStart]
    for _ in 0..<steps {
        let w = sgd.last!, g = grad(w)
        sgd.append((w.0 - 0.15 * g.0, w.1 - 0.15 * g.1))
    }
    var mom = [gdStart]
    var v = (0.0, 0.0)
    for _ in 0..<steps {
        let w = mom.last!, g = grad(w)
        v = (0.85 * v.0 + g.0, 0.85 * v.1 + g.1)
        mom.append((w.0 - 0.03 * v.0, w.1 - 0.03 * v.1))
    }
    var adam = [gdStart]
    var m = (0.0, 0.0), s = (0.0, 0.0)
    for t in 1...steps {
        let w = adam.last!, g = grad(w)
        m = (0.9 * m.0 + 0.1 * g.0, 0.9 * m.1 + 0.1 * g.1)
        s = (0.999 * s.0 + 0.001 * g.0 * g.0, 0.999 * s.1 + 0.001 * g.1 * g.1)
        let c1 = 1 - pow(0.9, Double(t)), c2 = 1 - pow(0.999, Double(t))
        adam.append((w.0 - 0.35 * (m.0 / c1) / ((s.0 / c2).squareRoot() + 1e-8),
                     w.1 - 0.35 * (m.1 / c1) / ((s.1 / c2).squareRoot() + 1e-8)))
    }
    return [sgd, mom, adam]
}

private func gdTabs() -> [NnTab] {
    let paths = gdPaths(gdCheckpoints.last!)
    let tones: [NnTone] = [.blue, .green, .violet]
    let names = ["SGD", "Mom", "Adam"]
    let heads = [
        "Three optimizers leave {the same start} on the same bowl.",
        "SGD {zigzags}: every step overshoots the steep axis and lands on the other side.",
        "Momentum {builds speed} along the flat axis, where every gradient points the same way.",
        "Adam divides each step by that axis's own gradient size, so it moves {the same distance} on the steep and flat axes.",
        "After 40 steps SGD ends lowest, {only because its rate was tuned} to this bowl.",
    ]
    let bodies = [
        "The bowl is \(nnw(gdSteep)) times steeper across than along: the shape that makes plain gradient descent struggle.",
        "Its rate is set just under the point where the steep axis would diverge.",
        "On the steep axis successive gradients cancel, so the velocity there stays small.",
        "Momentum and Adam both carry enough speed to overshoot the minimum and swing back.",
        "Adam used one untuned rate for both axes and still got close. On a real network nobody can tune a rate per axis.",
    ]
    let frames = gdCheckpoints.enumerated().map { i, t in
        let curves = paths.enumerated().map { k, p in NnCurve(segments: [Array(p.prefix(t + 1))], tone: tones[k]) }
        let ends = paths.enumerated().map { k, p in NnDot(x: p[t].0, y: p[t].1, tone: tones[k]) }
        return NnFrame(
            headline: heads[i], body: bodies[i],
            scene: .plot(PlotScene(
                height: 200, xRange: -4.6...1.6, yRange: -1.25...1.55, curves: curves,
                dots: [NnDot(x: gdStart.0, y: gdStart.1, tone: .off, hollow: true, label: "start")] + (t > 0 ? ends : []),
                axes: false,
                contours: [0.7, 1.5, 2.4, 3.4, 4.5, 5.7],
                contourAspect: gdSteep.squareRoot(),
                header: "ITERATION \(t) · SAME START"
            )),
            legend: [(.blue, "SGD"), (.green, "Momentum"), (.violet, "Adam"), (.off, "Loss contour")],
            chips: paths.enumerated().map { k, p in NnChip(key: names[k], value: nnf(gdLoss(p[t]), 3), dot: tones[k]) }
        )
    }
    return [NnTab(label: "", frames: frames)]
}

// MARK: - Lab

private func neuralStoryTabs(_ topicId: String, _ dropped: Int) -> [NnTab] {
    switch topicId {
    case "restricted_boltzmann_machines": return rbmTabs()
    case "deep_belief_networks": return dbnTabs()
    case "batch_normalization": return bnTabs()
    case "backpropagation": return bpTabs()
    case "autoencoders": return aeTabs()
    case "dropout": return dropoutTabs(dropped)
    case "activation_functions": return activationTabs()
    case "mlp": return mlpTabs()
    default: return gdTabs()
    }
}

struct NeuralStoryLab: View {
    let topicId: String
    @State private var dropped = 2
    @State private var tab = 0
    @State private var tabs: [NnTab]
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        self.topicId = topicId
        let tabs = neuralStoryTabs(topicId, 2)
        _tabs = State(initialValue: tabs)
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
                if topicId == "dropout" {
                    DropRateRow(dropped: dropped) { delta in
                        let next = min(max(dropped + delta, dropoutMin), dropoutMax)
                        guard next != dropped else { return }
                        dropped = next
                        tabs = neuralStoryTabs(topicId, next)
                        playback = PlaybackState(stepCount: tabs[tab].frames.count, speedMs: 1000)
                    }
                    .padding(.bottom, 12)
                }
                switch frame.scene {
                case .net(let scene): NetView(scene: scene)
                case .bars(let scene): BarsView(scene: scene)
                case .plot(let scene): PlotView(scene: scene)
                case .xor(let scene): XorView(scene: scene)
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
                StoryLegendRow(items: frame.legend.map { (nnColor($0.0, palette), nnSwatch($0.0), $0.1) }).padding(.top, 14)
            }
            if !frame.chips.isEmpty { NnChips(chips: frame.chips).padding(.top, 16) }
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

private func nnColor(_ tone: NnTone, _ palette: Palette) -> Color {
    switch tone {
    case .blue: SimColors.blue
    case .green: SimColors.green
    case .violet: SimColors.answer
    case .current: SimColors.active
    case .warn, .dropped: SimColors.red
    case .band: SimColors.answer.opacity(0.45)
    case .off, .ghost: palette.muted
    }
}

private func nnSwatch(_ tone: NnTone) -> SwatchStyle {
    switch tone {
    case .off: .ring
    case .ghost, .dropped: .dashed
    default: .fill
    }
}

private func nnOnFill(_ tone: NnTone, _ palette: Palette) -> Color {
    switch tone {
    case .current: Color(hex: 0x1F1A0A)
    case .off, .ghost, .dropped: palette.muted
    case .warn: StoryTone.warn.ink(palette)
    default: .white
    }
}

private func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)) }

private func line(_ a: CGPoint, _ b: CGPoint) -> Path {
    var p = Path()
    p.move(to: a)
    p.addLine(to: b)
    return p
}

private struct LabelRow: View {
    let labels: [NnLabel]
    let mono: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            ForEach(labels.indices, id: \.self) { i in
                let label = labels[i]
                Text(label.text)
                    .font(mono ? AppFont.mono(12) : AppFont.sans(12, .semibold))
                    .tracking(mono ? 0 : 1)
                    .foregroundStyle(palette.muted)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: label.align == .leading ? .leading : label.align == .center ? .center : .trailing)
            }
        }
    }
}

private struct NetView: View {
    let scene: NetScene
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            if !scene.pills.isEmpty {
                HStack(spacing: 8) {
                    ForEach(scene.pills.indices, id: \.self) { i in
                        let pill = scene.pills[i]
                        let (bg, ink): (Color, Color) = switch pill.tone {
                        case .green: (SimColors.green.opacity(0.2), StoryTone.done.ink(palette))
                        case .blue: (SimColors.blue.opacity(0.22), StoryTone.path.ink(palette))
                        default: (SimColors.tint, palette.muted)
                        }
                        Text(pill.text).font(AppFont.sans(14, .semibold)).foregroundStyle(ink).lineLimit(1)
                            .frame(maxWidth: .infinity).frame(height: 34)
                            .background(bg, in: RoundedRectangle(cornerRadius: 8))
                    }
                }
                .padding(.bottom, 12)
            }
            if !scene.headers.isEmpty { LabelRow(labels: scene.headers, mono: false).padding(.bottom, 6) }
            Canvas { ctx, size in draw(&ctx, size) }.frame(height: scene.height)
            if !scene.footers.isEmpty { LabelRow(labels: scene.footers, mono: true).padding(.top, 6) }
        }
    }

    private func draw(_ ctx: inout GraphicsContext, _ size: CGSize) {
        let maxR = scene.nodes.map(\.r).max() ?? 20
        let badgeRoom: CGFloat = scene.nodes.contains { $0.badge != nil } ? 22 : 0
        let padX = maxR + 2, padTop = maxR + 2, padBottom = maxR + 2 + badgeRoom
        func at(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: padX + x * (size.width - 2 * padX), y: padTop + y * (size.height - padTop - padBottom))
        }
        func pos(_ n: NnNode) -> CGPoint { at(n.x, n.y) }

        for b in scene.bands {
            var p = Path()
            p.move(to: CGPoint(x: at(b.x0, b.top0).x, y: at(b.x0, b.top0).y - 8))
            p.addLine(to: CGPoint(x: at(b.x1, b.top1).x, y: at(b.x1, b.top1).y - 8))
            p.addLine(to: CGPoint(x: at(b.x1, b.bottom1).x, y: at(b.x1, b.bottom1).y + 8))
            p.addLine(to: CGPoint(x: at(b.x0, b.bottom0).x, y: at(b.x0, b.bottom0).y + 8))
            p.closeSubpath()
            ctx.fill(p, with: .color(palette.muted.opacity(0.08)))
        }

        let edges = scene.edges.filter { $0.tone == .faint } + scene.edges.filter { $0.tone != .faint }
        for e in edges {
            let (color, width): (Color, CGFloat) = switch e.tone {
            case .faint: (palette.muted.opacity(0.28), 1)
            case .blue: (SimColors.blue, 2)
            case .green: (SimColors.green.opacity(0.45), 1)
            case .red: (SimColors.red, 2.5)
            case .redDashed: (SimColors.red.opacity(0.6), 1.5)
            case .current: (SimColors.active, 3)
            }
            ctx.stroke(line(pos(scene.nodes[e.from]), pos(scene.nodes[e.to])), with: .color(color),
                       style: StrokeStyle(lineWidth: width, lineCap: .round, dash: e.tone == .redDashed ? [5, 4] : []))
        }
        for e in scene.edges {
            guard let label = e.label else { continue }
            let a = pos(scene.nodes[e.from]), b = pos(scene.nodes[e.to])
            let ink = e.tone == .red || e.tone == .redDashed ? StoryTone.warn.ink(palette) : palette.muted
            // Beside the edge, on its upper side, on a surface-coloured pill so crossing lines don't cut it.
            let dx = b.x - a.x, dy = b.y - a.y, len = max((dx * dx + dy * dy).squareRoot(), 1)
            var nx = dy / len, ny = -dx / len
            if ny > 0 { nx = -nx; ny = -ny }
            let text = ctx.resolve(Text(label).font(AppFont.mono(11, .medium)).foregroundColor(ink))
            let s = text.measure(in: CGSize(width: 200, height: 30))
            let mid = CGPoint(x: a.x + dx * 0.45 + nx * 12, y: a.y + dy * 0.45 + ny * 12)
            ctx.fill(Path(roundedRect: CGRect(x: mid.x - s.width / 2 - 3, y: mid.y - s.height / 2, width: s.width + 6, height: s.height), cornerRadius: 4),
                     with: .color(palette.surface))
            ctx.draw(text, at: mid)
        }

        for n in scene.nodes {
            let c = pos(n), r = n.r
            switch n.tone {
            case .off:
                ctx.fill(circle(c, r), with: .color(palette.surface))
                ctx.stroke(circle(c, r - 0.75), with: .color(palette.muted.opacity(0.6)), lineWidth: 1.5)
            case .ghost, .dropped:
                ctx.fill(circle(c, r), with: .color(palette.surface))
                ctx.stroke(circle(c, r - 0.75), with: .color(palette.muted.opacity(0.5)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                if n.tone == .dropped {
                    let s = r * 0.38
                    var x = Path()
                    x.move(to: CGPoint(x: c.x - s, y: c.y - s)); x.addLine(to: CGPoint(x: c.x + s, y: c.y + s))
                    x.move(to: CGPoint(x: c.x + s, y: c.y - s)); x.addLine(to: CGPoint(x: c.x - s, y: c.y + s))
                    ctx.stroke(x, with: .color(SimColors.red), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                }
            case .warn:
                ctx.fill(circle(c, r), with: .color(palette.surface))
                ctx.fill(circle(c, r), with: .color(SimColors.red.opacity(0.2)))
                ctx.stroke(circle(c, r - 0.75), with: .color(SimColors.red), lineWidth: 1.5)
            default:
                ctx.fill(circle(c, r), with: .color(nnColor(n.tone, palette)))
            }
            if !n.text.isEmpty, n.tone != .ghost, n.tone != .dropped {
                ctx.draw(Text(n.text).font(AppFont.mono(r >= 17 ? 12 : 10, .bold)).foregroundColor(nnOnFill(n.tone, palette)), at: c)
            }
            if let side = n.side {
                ctx.draw(Text(side).font(AppFont.mono(11, .medium)).foregroundColor(palette.muted),
                         at: CGPoint(x: c.x + r + 5, y: c.y), anchor: .leading)
            }
            if let badge = n.badge {
                let text = ctx.resolve(Text(badge.text).font(AppFont.mono(11, .bold)).foregroundColor(badge.warn ? .white : palette.muted))
                let s = text.measure(in: CGSize(width: 200, height: 30))
                let rect = CGRect(x: c.x - s.width / 2 - 6, y: c.y + r + 4, width: s.width + 12, height: 18)
                ctx.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(badge.warn ? SimColors.red.opacity(0.85) : palette.muted.opacity(0.22)))
                ctx.draw(text, at: CGPoint(x: rect.midX, y: rect.midY))
            }
        }
    }
}

private struct BarsView: View {
    let scene: BarsScene
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let n = CGFloat(scene.raw.count)
            let gap: CGFloat = 8
            let barW = (size.width - gap * (n - 1)) / n
            func left(_ i: Int) -> CGFloat { CGFloat(i) * (barW + gap) }

            // Upper panel: the raw values from a shared baseline.
            ctx.draw(Text("raw x").font(AppFont.sans(12)).foregroundColor(palette.muted), at: CGPoint(x: 0, y: 8), anchor: .leading)
            let top: CGFloat = 22, base = size.height * 0.45
            func yRaw(_ v: Double) -> CGFloat { base - CGFloat(v / scene.rawMax) * (base - top) }
            if let (mu, sd) = scene.band {
                ctx.fill(Path(CGRect(x: 0, y: yRaw(mu + sd), width: size.width, height: yRaw(mu - sd) - yRaw(mu + sd))),
                         with: .color(SimColors.answer.opacity(0.2)))
                ctx.stroke(line(CGPoint(x: 0, y: yRaw(mu)), CGPoint(x: size.width, y: yRaw(mu))),
                           with: .color(StoryTone.answer.ink(palette)), style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
            }
            for (i, bar) in scene.raw.enumerated() {
                guard let v = bar.value else { continue }
                ctx.fill(Path(roundedRect: CGRect(x: left(i), y: yRaw(v), width: barW, height: base - yRaw(v)), cornerRadius: 4),
                         with: .color(nnColor(bar.tone, palette)))
            }
            ctx.stroke(line(CGPoint(x: 0, y: base), CGPoint(x: size.width, y: base)), with: .color(palette.muted.opacity(0.3)), lineWidth: 1)

            // Lower panel: the normalized values around zero, with ±1 marked.
            let lowerTop = size.height * 0.52
            ctx.draw(Text(scene.lowerLabel).font(AppFont.mono(12)).foregroundColor(palette.muted), at: CGPoint(x: 0, y: lowerTop + 6), anchor: .leading)
            let zoneTop = lowerTop + 20
            let zero = (zoneTop + size.height) / 2
            let unit = ((size.height - zoneTop) / 2 - 2) / CGFloat(scene.lowerScale)
            for (s, label) in [(CGFloat(1), "+1"), (-1, "−1")] {
                let y = zero - s * unit
                ctx.stroke(line(CGPoint(x: 0, y: y), CGPoint(x: size.width, y: y)), with: .color(palette.muted.opacity(0.35)),
                           style: StrokeStyle(lineWidth: 1, dash: [2, 4]))
                ctx.draw(Text(label).font(AppFont.mono(10)).foregroundColor(palette.muted), at: CGPoint(x: size.width, y: y - 8), anchor: .trailing)
            }
            ctx.stroke(line(CGPoint(x: 0, y: zero), CGPoint(x: size.width, y: zero)), with: .color(palette.muted.opacity(0.3)), lineWidth: 1)
            for (i, bar) in scene.lower.enumerated() {
                if let v = bar.value {
                    let y = zero - CGFloat(v) * unit
                    ctx.fill(Path(roundedRect: CGRect(x: left(i), y: min(y, zero), width: barW, height: max(abs(zero - y), 2)), cornerRadius: 4),
                             with: .color(nnColor(bar.tone, palette)))
                } else {
                    ctx.stroke(Path(roundedRect: CGRect(x: left(i) + 1, y: zero - 6, width: barW - 2, height: 12), cornerRadius: 3),
                               with: .color(palette.muted.opacity(0.45)), style: StrokeStyle(lineWidth: 1, dash: [3, 2]))
                }
            }
        }
        .frame(height: 236)
    }
}

private struct PlotView: View {
    let scene: PlotScene
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let padT: CGFloat = scene.header != nil ? 24 : 6
            let padB: CGFloat = scene.probe != nil ? 18 : 6
            let xr = scene.xRange, yr = scene.yRange
            func px(_ x: Double) -> CGFloat { CGFloat((x - xr.lowerBound) / (xr.upperBound - xr.lowerBound)) * size.width }
            func py(_ y: Double) -> CGFloat { padT + (1 - CGFloat((y - yr.lowerBound) / (yr.upperBound - yr.lowerBound))) * (size.height - padT - padB) }
            if let header = scene.header {
                ctx.draw(Text(header).font(AppFont.sans(12, .semibold)).tracking(1).foregroundColor(palette.muted), at: CGPoint(x: 0, y: 8), anchor: .leading)
            }
            var inner = ctx
            inner.clip(to: Path(CGRect(x: 0, y: padT, width: size.width, height: size.height - padT - padB)))
            for rx in scene.contours {
                let ry = rx / scene.contourAspect
                inner.stroke(Path(ellipseIn: CGRect(x: px(-rx), y: py(ry), width: px(rx) - px(-rx), height: py(-ry) - py(ry))),
                             with: .color(palette.muted.opacity(0.3)), lineWidth: 1)
            }
            if scene.axes {
                ctx.stroke(line(CGPoint(x: px(0), y: padT), CGPoint(x: px(0), y: size.height - padB)), with: .color(palette.muted.opacity(0.4)), lineWidth: 1)
                ctx.stroke(line(CGPoint(x: 0, y: py(0)), CGPoint(x: size.width, y: py(0))), with: .color(palette.muted.opacity(0.4)), lineWidth: 1)
            }
            if let ry = scene.refY {
                ctx.stroke(line(CGPoint(x: 0, y: py(ry)), CGPoint(x: size.width, y: py(ry))), with: .color(palette.muted.opacity(0.35)),
                           style: StrokeStyle(lineWidth: 1, dash: [2, 4]))
                ctx.draw(Text(nnw(ry)).font(AppFont.mono(10)).foregroundColor(palette.muted), at: CGPoint(x: px(0) - 5, y: py(ry) - 7), anchor: .trailing)
            }
            if let (x, label) = scene.probe {
                ctx.stroke(line(CGPoint(x: px(x), y: padT), CGPoint(x: px(x), y: size.height - padB)), with: .color(SimColors.active),
                           style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                ctx.draw(Text(label).font(AppFont.mono(11, .medium)).foregroundColor(StoryTone.active.ink(palette)),
                         at: CGPoint(x: px(x) + 4, y: size.height - padB + 9), anchor: .leading)
            }
            var curves = ctx
            curves.clip(to: Path(CGRect(x: 0, y: padT - 4, width: size.width, height: size.height - padT - padB + 8)))
            for curve in scene.curves {
                for seg in curve.segments where seg.count > 1 {
                    var p = Path()
                    p.move(to: CGPoint(x: px(seg[0].0), y: py(seg[0].1)))
                    seg.dropFirst().forEach { p.addLine(to: CGPoint(x: px($0.0), y: py($0.1))) }
                    curves.stroke(p, with: .color(nnColor(curve.tone, palette)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                }
            }
            for dot in scene.dots {
                let c = CGPoint(x: px(dot.x), y: py(dot.y))
                if dot.hollow {
                    ctx.fill(circle(c, 6), with: .color(palette.surface))
                    ctx.stroke(circle(c, 5), with: .color(palette.muted), lineWidth: 2)
                } else {
                    ctx.fill(circle(c, 7), with: .color(palette.surface))
                    ctx.fill(circle(c, 5), with: .color(nnColor(dot.tone, palette)))
                }
                if let label = dot.label {
                    ctx.draw(Text(label).font(AppFont.mono(11)).foregroundColor(palette.muted), at: CGPoint(x: c.x + 9, y: c.y - 11), anchor: .leading)
                }
            }
        }
        .frame(height: scene.height)
    }
}

private struct XorView: View {
    let scene: XorScene
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                XorPlane(title: "input space", xLabel: "x₁", yLabel: "x₂", points: scene.input, line: scene.inputLine ? .fail : nil)
                XorPlane(title: "hidden space", xLabel: "OR", yLabel: "AND", points: scene.hidden, line: scene.hiddenLine ? .split : nil)
            }
            HStack(spacing: 6) {
                ForEach(["x₁", "x₂", "h₁ OR", "h₂ AND", "y"], id: \.self) {
                    Text($0).font(AppFont.mono(11)).foregroundStyle(palette.muted).lineLimit(1).frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 12).padding(.bottom, 6)
            VStack(spacing: 6) {
                ForEach(scene.rows.indices, id: \.self) { r in
                    HStack(spacing: 6) {
                        ForEach(scene.rows[r].indices, id: \.self) { c in XorCell(text: scene.rows[r][c].0, tone: scene.rows[r][c].1) }
                    }
                }
            }
        }
    }
}

private struct XorCell: View {
    let text: String
    let tone: XorCellTone
    @Environment(\.palette) private var palette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 8)
        let (fill, ink): (Color, Color) = switch tone {
        case .idle: (palette.muted.opacity(0.2), palette.onSurface)
        case .row: (SimColors.blue.opacity(0.22), palette.onSurface)
        case .one: (SimColors.answer, .white)
        case .current: (SimColors.active, Color(hex: 0x1F1A0A))
        case .empty: (.clear, palette.onSurface)
        }
        Text(text).font(AppFont.mono(15, .bold)).foregroundStyle(ink)
            .frame(maxWidth: .infinity).frame(height: 34)
            .background(fill, in: shape)
            .overlay {
                switch tone {
                case .row: shape.stroke(SimColors.blue, lineWidth: 1.5)
                case .empty: shape.stroke(palette.muted.opacity(0.4), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                default: EmptyView()
                }
            }
    }
}

private enum XorLine { case fail, split }

private struct XorPlane: View {
    let title: String
    let xLabel: String
    let yLabel: String
    let points: [XorPoint]
    let line: XorLine?
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            ctx.draw(Text(title).font(AppFont.sans(11)).foregroundColor(palette.muted), at: CGPoint(x: 8, y: 11), anchor: .leading)
            let ax = size.width * 0.12, ay = size.height * 0.86
            func at(_ x: Double, _ y: Double) -> CGPoint {
                CGPoint(x: size.width * (0.24 + 0.56 * CGFloat(x)), y: size.height * (0.72 - 0.46 * CGFloat(y)))
            }
            let axis = palette.muted.opacity(0.45)
            ctx.stroke(NeuralStoryLabsLine.make(CGPoint(x: ax, y: size.height * 0.2), CGPoint(x: ax, y: ay)), with: .color(axis), lineWidth: 1)
            ctx.stroke(NeuralStoryLabsLine.make(CGPoint(x: ax, y: ay), CGPoint(x: size.width * 0.94, y: ay)), with: .color(axis), lineWidth: 1)
            ctx.draw(Text(xLabel).font(AppFont.mono(10)).foregroundColor(palette.muted), at: CGPoint(x: size.width * 0.94, y: ay + 7), anchor: .trailing)
            ctx.draw(Text(yLabel).font(AppFont.mono(10)).foregroundColor(palette.muted), at: CGPoint(x: size.width * 0.94, y: size.height * 0.2), anchor: .trailing)
            if let line {
                let (a, b, color): (CGPoint, CGPoint, Color) = switch line {
                case .fail: (at(-0.2, 0.7), at(0.7, -0.2), SimColors.red)
                case .split: (at(0.3, -0.25), at(1.35, 0.8), SimColors.active)
                }
                ctx.stroke(NeuralStoryLabsLine.make(a, b), with: .color(color), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            }
            for p in points {
                let c = at(Double(p.x), Double(p.y))
                if p.filled {
                    ctx.fill(circle(c, 8), with: .color(SimColors.answer))
                } else {
                    ctx.stroke(circle(c, 7), with: .color(palette.muted.opacity(0.8)), lineWidth: 1.5)
                }
            }
            for p in points where p.ring {
                ctx.stroke(circle(at(Double(p.x), Double(p.y)), 12), with: .color(SimColors.active), lineWidth: 2.5)
            }
        }
        .frame(height: 120)
        .frame(maxWidth: .infinity)
        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 12))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

/// `line` is shadowed inside XorPlane by its stored property.
private enum NeuralStoryLabsLine {
    static func make(_ a: CGPoint, _ b: CGPoint) -> Path { line(a, b) }
}

/// Value chips with an optional series dot before the key ("• ReLU′ 1.00").
private struct NnChips: View {
    let chips: [NnChip]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 8, lineSpacing: 8) {
            ForEach(chips.indices, id: \.self) { i in
                let chip = chips[i]
                HStack(spacing: 8) {
                    if let dot = chip.dot { Circle().fill(nnColor(dot, palette)).frame(width: 8, height: 8) }
                    Text(chip.key).foregroundStyle(palette.muted)
                    Text(chip.value).fontWeight(.bold).foregroundStyle(chip.tone.ink(palette))
                }
                .font(AppFont.mono(15))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            }
        }
    }
}

/// "Drop rate p = 1/3" with a − | + pill, stepping how many of the six hidden units a pass drops.
private struct DropRateRow: View {
    let dropped: Int
    let onStep: (Int) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            (Text("Drop rate ") + Text("p").fontWeight(.bold) + Text("  =  ") + Text(dropP(dropped)).font(AppFont.mono(16, .bold)))
                .font(AppFont.sans(16))
                .foregroundStyle(palette.onSurface)
            Spacer(minLength: 8)
            HStack(spacing: 0) {
                button("−", -1, dropped > dropoutMin)
                Rectangle().fill(palette.muted.opacity(0.35)).frame(width: 1, height: 18)
                button("+", 1, dropped < dropoutMax)
            }
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
        }
    }

    private func button(_ glyph: String, _ delta: Int, _ enabled: Bool) -> some View {
        Button { onStep(delta) } label: {
            Text(glyph).font(AppFont.sans(20, .medium)).foregroundStyle(palette.onSurface.opacity(enabled ? 1 : 0.3))
                .frame(width: 44, height: 36).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
