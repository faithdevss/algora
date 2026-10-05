import SwiftUI

// Port of RnnStoryFrames.kt: RNNs, BPTT, LSTMs and GRUs, bidirectional RNNs, encoder-decoders and
// seq2seq beam search. The recurrences are run step by step on small fixed inputs; BPTT's noise comes
// from the same plain LCG as on Android.

let rnnStoryTopicIds: Set<String> = ["rnn", "bptt", "lstm_gru", "bidirectional_rnn", "encoder_decoder", "seq2seq"]

func rnnLab(_ topicId: String) -> DkLab? {
    switch topicId {
    case "rnn": rnnIntroLab()
    case "bptt": bpttLab()
    case "lstm_gru": lstmLab()
    case "bidirectional_rnn": biRnnLab()
    case "encoder_decoder": encDecLab()
    case "seq2seq": seq2seqLab()
    default: nil
    }
}

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }

private func t(_ v: Double, _ d: Int = 2) -> String {
    var s = dkNum(v, d)
    guard s.contains(".") else { return s }
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}

private func pct(_ share: Double, _ d: Int = 1) -> String { n(share * 100, d) + "%" }

private func legend(_ ink: DkInk, _ label: String, _ style: SwatchStyle = .fill) -> DkLegend { DkLegend(ink: ink, style: style, label: label) }

private func stepActions(_ frames: [DkFrame], _ action: String) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = i == frames.count - 1 ? "Start Over" : action; return f }
}

private let supDigits = Array("⁰¹²³⁴⁵⁶⁷⁸⁹")
private func sup(_ k: Int) -> String { String(String(k).map { supDigits[Int(String($0))!] }) }

private func sci(_ v: Double) -> String {
    var e = Int(log10(v).rounded(.down))
    var m = (v / pow(10, Double(e)) * 10 + 0.5).rounded(.down) / 10
    if m >= 10 { m /= 10; e += 1 }
    return n(m, 1) + "e" + (e < 0 ? "−" : "") + String(abs(e))
}

private func plural(_ k: Int) -> String { k > 1 ? "s" : "" }

// MARK: - RNNs: one cell unrolled

private let rnnX = [0.80, -0.40, 0.60, 0.20]
private let rnnW = 0.7, rnnU = 0.6

private func rnnIntroLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        var h: [Double] = []
        var prev = 0.0
        for x in rnnX { prev = tanh(rnnW * x + rnnU * prev); h.append(prev) }
        let reach = rnnW * (1 - h[0] * h[0]) * (1...3).reduce(1.0) { $0 * rnnU * (1 - h[$1] * h[$1]) }
        func stage(_ done: Int, _ current: Int?) -> DkStage {
            .tiles(DkTiles(rows: [
                DkTileRow(tiles: rnnX.enumerated().map { i, x in DkTile(text: "x\(i + 1)", tone: i <= (current ?? done - 1) ? .source : .plain, sub: n(x)) }),
                DkTileRow(tiles: h.enumerated().map { i, v in
                    if i == current { return DkTile(text: "h\(i + 1)", tone: .current, sub: n(v, 3)) }
                    if i < done { return DkTile(text: "h\(i + 1)", tone: .emitted, sub: n(v, 3)) }
                    return DkTile(text: "h\(i + 1)", tone: .plain, sub: "–")
                }),
            ], arrows: true))
        }
        func stepLine(_ k: Int) -> [String] {
            let hp = k == 0 ? 0 : h[k - 1]
            let z = rnnW * rnnX[k] + rnnU * hp
            return ["h\(k + 1) = tanh(w·x\(k + 1) + u·h\(k))",
                    "= tanh(\(t(rnnW))·\(n(rnnX[k])) + \(t(rnnU))·\(n(hp, 3))) = tanh(\(n(z, 3))) = {\(n(h[k], 3))}"]
        }
        let header = "one cell, unrolled over 4 timesteps · w = \(t(rnnW)), u = \(t(rnnU))"
        let lg = [legend(.blue, "Input"), legend(.green, "Computed state"), legend(.yellow, "Current", .ring)]
        let frames = [
            DkFrame(header: header, stage: stage(0, nil), legend: Array(lg.prefix(1)),
                    formula: ["h_t = tanh(w·x_t + u·h_{t−1}),  h0 = 0", "the same w and u at every step"],
                    headline: "An RNN reads a sequence {one step at a time}.",
                    body: "Its state h carries forward whatever it has seen so far, so each step depends on the past."),
            DkFrame(header: header, stage: stage(1, 0), legend: lg, formula: stepLine(0),
                    headline: "h1 sees only {x1}: \(n(h[0], 3)).", body: "With no past, h0 = 0 and the step is a single tanh neuron."),
            DkFrame(header: header, stage: stage(2, 1), legend: lg, formula: stepLine(1),
                    headline: "h2 = {\(n(h[1], 3))}: the negative x2 nearly cancels the past.",
                    body: "u·h1 = \(n(rnnU * h[0], 3)) pulls up while w·x2 = \(n(rnnW * rnnX[1], 3)) pulls down."),
            DkFrame(header: header, stage: stage(3, 2), legend: lg, formula: stepLine(2),
                    headline: "h3 mixes the new input with {everything before it}.",
                    body: "The same w and u are reused at every step, so a sequence of any length needs just 2 weights here."),
            DkFrame(header: header, stage: stage(4, 3), legend: lg, formula: stepLine(3),
                    headline: "h4 = {\(n(h[3], 3))} summarises all four inputs.",
                    body: "A classifier reading only h4 sees the whole sequence, compressed into this one state."),
            DkFrame(header: header, stage: stage(4, nil), legend: Array(lg.prefix(2)),
                    formula: ["unrolled: 4 layers, all sharing w and u", "gradients for w add up over the 4 steps"],
                    headline: "Unrolled, it's a 4-layer net with {tied weights}.",
                    body: "Training backpropagates through these copies, which is BPTT, and sums each step's gradient for the shared w and u."),
            DkFrame(header: header, stage: stage(4, nil), legend: Array(lg.prefix(2)),
                    formula: ["∂h4/∂x1 = w·(1 − h1²) · Π u·(1 − h_k²)", "= {\(n(reach, 3))}"],
                    headline: "x1 still reaches h4, scaled by {\(n(reach, 3))}.",
                    body: "Every step multiplies by u·tanh′ < 1, so early inputs fade. Longer sequences make it worse: that is the vanishing gradient BPTT shows."),
        ]
        return stepActions(frames, "Next Timestep")
    }
}

// MARK: - BPTT: the gradient back to the cue

private let bpttUs = [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0, 1.2]
private let bpttSteps = 10

private let bpttInputs: [Double] = {
    var s: Int64 = 909
    func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
    func g() -> Double { let a = max(u(), 1e-12); let b = u(); return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b) }
    var out = [1.0]
    for _ in 0..<(bpttSteps - 1) { out.append(0.3 * g()) }
    return out
}()

/// |∂h10/∂h_t| for t = 1...10 with recurrent weight u.
private func bpttGrads(_ u: Double) -> [Double] {
    var h: [Double] = []
    var prev = 0.0
    for x in bpttInputs { prev = tanh(x + u * prev); h.append(prev) }
    var out = Array(repeating: 0.0, count: bpttSteps)
    out[bpttSteps - 1] = 1
    for k in stride(from: bpttSteps - 2, through: 0, by: -1) { out[k] = out[k + 1] * abs(u * (1 - h[k + 1] * h[k + 1])) }
    return out
}

private func bpttLab() -> DkLab {
    DkLab(control: .stepperOnly, stepper: DkStepper(caption: "recurrent u", values: bpttUs, initial: bpttUs.firstIndex(of: 0.6)!) { t($0) }) { _, p in
        let u = bpttUs[p]
        let g = bpttGrads(u), ref = bpttGrads(0.95)
        let logs = (g + ref).map { log10($0) }
        let lo = logs.min()!.rounded(.down) - 0.1
        let hi = max(0, logs.max()!) + 0.15
        let ticks: [(Double, String)] = stride(from: Int(hi.rounded(.down)), through: Int(lo.rounded(.up)), by: -1).map { k in
            (Double(k), k == 0 ? "1" : "1e" + (k < 0 ? "−" : "") + String(abs(k)))
        }
        func plot(_ hot: Set<Int>, dim: Set<Int> = []) -> DkStage {
            .plot(DkPlot(
                xr: (0.4, Double(bpttSteps) + 0.6), yr: (lo, hi), yTicks: ticks, xLeft: "", xRight: "",
                lines: [DkLine(pts: ref.enumerated().map { DkP(Double($0.offset) + 1, log10($0.element)) }, ink: .green, dashed: true)],
                axis: false,
                bars: g.enumerated().map { i, v in DkPBar(x: Double(i) + 1, y: log10(v), ink: hot.contains(i) ? .yellow : dim.contains(i) ? .slate : .pink) },
                xTicks: (1...bpttSteps).map { DkTick(x: Double($0), label: "t\($0)", hot: hot.contains($0 - 1)) }))
        }
        let uT = t(u)
        let header = "|∂h10 / ∂h_t| · cue at t1, noise t2–t10"
        let lg = [legend(.pink, "u = \(uT)"), legend(.yellow, "Reaches the cue"), legend(.green, "u = 0.95", .dashedLine)]
        let first = g[0]
        let frames = [
            DkFrame(header: header, stage: plot([]), legend: [lg[0], lg[2]],
                    formula: ["h_t = tanh(x_t + u·h_{t−1})", "∂h10/∂h_t = Π u·(1 − h_k²) from k = t+1 to 10"],
                    headline: "The answer at t10 depends on the {cue at t1}.",
                    body: "Steps 2–10 are noise. To learn the task, the error at t10 has to travel back through nine steps to reach t1."),
            DkFrame(header: header, stage: plot([0]), legend: lg,
                    formula: ["∂h10/∂h1 = Π u·(1 − h_t²) over 9 steps", "u = \(uT): {\(first < 0.01 ? n(first, 4) : n(first, 3))} · u = 0.95: \(n(ref[0], 3))"],
                    headline: first > 1.05 ? "The signal reaching the cue has grown {\(n(first, 1))×}."
                        : first > 0.3 ? "{\(pct(first, 0))} of the error signal gets back to the cue."
                        : "Only {\(pct(first))} of the error signal gets back to the cue.",
                    body: first > 1.05 ? "Above 1 the factors compound the other way and the gradient explodes; clipping is the usual guard."
                        : first > 0.3 ? "With u near 1 the factors barely shrink it, so the cue can still be learned, though tanh′ keeps nibbling at it."
                        : "Backprop through time multiplies one factor per step. Below 1, the signal vanishes before it reaches the one input that matters."),
            DkFrame(header: header, stage: plot([], dim: Set(0..<5)), legend: [lg[0], legend(.slate, "Cut off")],
                    formula: ["truncated BPTT, k = 5: backprop stops at t6", "cost: 5 steps of memory instead of 10"],
                    headline: "Truncate at 5 steps and the cue gets {no} gradient.",
                    body: "Truncated BPTT bounds memory and compute on long sequences, but it can't learn dependencies longer than its window. LSTMs attack the factor itself."),
        ]
        return frames
    }
}

// MARK: - LSTMs and GRUs

private let lstmF = [0.10, 0.92, 0.92, 0.92, 0.05]
private let lstmI = [0.95, 0.12, 0.12, 0.12, 0.10]
private let lstmG = [0.9, 0.1, 0.1, 0.1, -0.2]
private let gruZ = [0.95, 0.08, 0.08, 0.08, 0.95]
private let gruR = [0.50, 0.90, 0.90, 0.90, 0.10]
private let gruH = [0.9, 0.1, 0.1, 0.1, 0.0]

private func lstmLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["LSTM", "GRU"]) { tab, _ in
        let gru = tab == 1
        var c: [Double] = []
        var prev = 0.0
        for k in 0..<5 {
            prev = gru ? (1 - gruZ[k]) * prev + gruZ[k] * gruH[k] : lstmF[k] * prev + lstmI[k] * lstmG[k]
            c.append(prev)
        }
        let gate1 = gru ? gruZ : lstmF, gate2 = gru ? gruR : lstmI
        let names = gru ? ["update z", "reset r", "state h"] : ["forget", "input", "cell c"]
        func stage(_ hot: Int) -> DkStage {
            .tiles(DkTiles(rows: [
                DkTileRow(tiles: gate1.map { DkTile(text: "", tone: .fill, fill: $0, fillInk: .blue, caption: n($0)) }, label: names[0]),
                DkTileRow(tiles: gate2.map { DkTile(text: "", tone: .fill, fill: $0, fillInk: .orange, caption: n($0)) }, label: names[1]),
                DkTileRow(tiles: c.map { DkTile(text: "", tone: .fill, fill: abs($0), fillInk: .violet, caption: n($0)) }, label: names[2]),
            ], headers: (1...5).map { "t\($0)" }, hot: hot))
        }
        let keepFactor = gru ? 1 - gruZ[1] : lstmF[1]
        let v = gru ? "h" : "c"
        let chain = "∂\(v)4/∂\(v)1 = Π \(gru ? "(1 − z)" : "f") = \(n(keepFactor))³ = \(n(pow(keepFactor, 3), 3)) vs RNN 0.6³ = 0.216"
        func update(_ k: Int) -> String {
            let p = k == 0 ? 0 : c[k - 1]
            return gru ? "h\(k + 1) = (1 − z)·h\(k) + z·h̃ = \(n(1 - gruZ[k]))·\(n(p, 3)) + \(n(gruZ[k]))·\(t(gruH[k])) = {\(n(c[k], 3))}"
                : "c\(k + 1) = f·c\(k) + i·g = \(n(lstmF[k]))·\(n(p, 3)) + \(n(lstmI[k]))·\(t(lstmG[k])) = {\(n(c[k], 3))}"
        }
        let header = gru ? "gates and state · t1 store, t2–t4 keep, t5 overwrite" : "gates and cell state · t1 store, t2–t4 keep, t5 forget"
        let lg = gru ? [legend(.blue, "Update z"), legend(.orange, "Reset r"), legend(.violet, "State h")]
            : [legend(.blue, "Forget f"), legend(.orange, "Input i"), legend(.violet, "Cell c")]
        let mem = gru ? "state" : "cell"
        let heads: [(String, String)] = [
            ("t1: the \(gru ? "update" : "input") gate opens and {stores \(n(c[0]))}.",
             gru ? "z near 1 replaces the old state with the new candidate h̃ = 0.9." : "i = 0.95 lets the candidate g = 0.9 in; f = 0.1 throws away the empty past."),
            ("The \(gru ? "update gate stays shut" : "forget gate keeps") {\(pct(keepFactor, 0))}; little new comes in.",
             gru ? "1 − z = 0.92 carries the state forward; z = 0.08 lets only a trace of the new input in." : "The cell is a conveyor belt: f decides how much rides on, i how much is added."),
            ("The \(mem) holds {\(n(c[2]))}, \(pct(c[2] / c[0], 0)) of what was stored at t1.",
             "A \(gru ? "update gate near 0" : "forget gate near 1") lets memory and gradient pass almost unchanged; at t5 it \(gru ? "opens" : "closes") and \(v) drops to \(n(c[4], 3))."),
            ("Three steps on, it still holds {\(n(c[3]))}.",
             "An RNN with u = 0.6 would have kept \(pct(pow(0.6, 3), 0)) of it by now; the gate is learned, so the network chooses what to remember."),
            ("At t5 the \(gru ? "update gate opens" : "forget gate closes"): \(v) drops to {\(n(c[4], 3))}.",
             gru ? "The GRU merges the LSTM's cell and output into one state with two gates: fewer parameters, similar results."
                 : "Forgetting is as deliberate as remembering: the network clears the cell when the stored fact stops mattering."),
        ]
        let frames = heads.enumerated().map { k, hb in
            DkFrame(header: header, stage: stage(k), legend: lg, formula: [update(k), chain], headline: hb.0, body: hb.1)
        }
        return stepActions(frames, "Next Timestep")
    }
}

// MARK: - Bidirectional RNNs

private let biWords = ["the", "horse", "raced", "past", "the", "barn", "fell"]

private func biRnnLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Forward only", "Bidirectional"], initialTab: 1) { tab, _ in
        let bi = tab == 1
        let last = biWords.count - 1
        func stage(_ i: Int) -> DkStage {
            .tiles(DkTiles(rows: [DkTileRow(tiles: biWords.enumerated().map { k, w in
                DkTile(text: w, tone: k == i ? .current : k == last ? .hot : .plain,
                       under: [k <= i ? .blue : nil] + (bi ? [k >= i ? .green : nil] : []))
            }, height: 46)]))
        }
        let header = "\"\(biWords.joined(separator: " "))\""
        let lg = [legend(.blue, "→ forward state")] + (bi ? [legend(.green, "← backward state")] : []) + [legend(.violet, "Disambiguates")]
        let frames = biWords.indices.map { i -> DkFrame in
            let w = biWords[i]
            let seenF = i + 1, seenB = biWords.count - i
            return DkFrame(
                header: header, stage: stage(i), legend: lg,
                formula: bi ? ["h_\(w) = [ →h ; ←h ] · 2 × 128 = 256 dims", "→h has seen \(seenF) word\(plural(seenF)), ←h has seen {\(seenB)\(i < last ? ", incl. \"fell\"" : "")}"]
                    : ["h_\(w) = →h · 128 dims", "→h has seen \(seenF) word\(plural(seenF)), {\(i < last ? "not \"fell\"" : "including \"fell\"")}"],
                headline: i == 2 && bi ? "A forward RNN's state at \"raced\" is {identical} in both readings."
                    : i == 2 ? "At \"raced\", a forward RNN {can't tell} which reading it is."
                    : i == last && bi ? "At \"fell\" both directions agree: it's the {main verb}."
                    : i == last ? "Only at \"fell\" does the forward pass {see} the main verb, too late for \"raced\"."
                    : bi ? "At \"\(w)\", the state joins {\(seenF)} word\(plural(seenF)) from the left with \(seenB) from the right."
                    : "At \"\(w)\", the state has seen only the {\(seenF)} word\(plural(seenF)) so far.",
                body: i == 2 && bi ? "\"fell\" is 4 words to the right, so only the backward pass can tell main verb from reduced relative. Concatenating both gives the tagger that context."
                    : i == 2 ? "\"The horse raced past the barn\" reads as complete. Only \"fell\" reveals that \"raced\" meant \"that was raced\", and this RNN hasn't seen it."
                    : i == last ? "Garden-path sentences are why taggers and BERT-style encoders read both ways; a generator can't, because the future isn't written yet."
                    : bi ? "The backward RNN runs right to left over the same words; the two states are concatenated at every position."
                    : "A forward-only RNN is what a text generator must use: it can only condition on the past."
            )
        }
        return stepActions(frames, "Next Word")
    }
}

// MARK: - Encoder-decoder

private let encSource = ["a", "c", "f", "e", "b", "b"]
private let encUnits = 12
private let encU = 0.6

private func encDecLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let nSrc = encSource.count
        func stage(_ emitted: Int, _ current: Int?, _ hotSource: Int?) -> DkStage {
            .tiles(DkTiles(rows: [
                DkTileRow(tiles: encSource.enumerated().map { k, s in DkTile(text: s, tone: k == hotSource ? .current : .source) }, title: "encoder"),
                DkTileRow(tiles: encSource.enumerated().map { k, s in
                    k == current ? DkTile(text: "?", tone: .current) : k < emitted ? DkTile(text: s, tone: .emitted) : DkTile(text: "", tone: .empty)
                }, title: "decoder"),
            ], pill: (0, "context · \(encUnits) numbers")))
        }
        func path(_ j: Int) -> Int { (nSrc - j) + 1 + (j - 1) }
        let header = "copy task · \(encUnits)-unit encoder and decoder"
        let lg = [legend(.blue, "Source"), legend(.green, "Emitted"), legend(.yellow, "Current", .ring), legend(.violet, "Bottleneck")]
        var frames = [DkFrame(
            header: header, stage: stage(0, nil, nil), legend: [lg[0], lg[3]],
            formula: ["encoder: 6 steps, one per symbol", "final state → context: {\(encUnits)} numbers"],
            headline: "The encoder reads \"\(encSource.joined())\" into {\(encUnits) numbers}.",
            body: "Only its last state is passed on. Everything the decoder will ever know about the source is in that vector.")]
        for j in 1...nSrc {
            let steps = path(j)
            frames.append(DkFrame(
                header: header, stage: stage(j - 1, j - 1, j - 1), legend: lg,
                formula: ["path \(encSource[j - 1]) → output \(j): \(nSrc - j) encoder + 1 hand-off + \(j - 1) decoder = {\(steps) steps}",
                          "at u = \(t(encU)): \(t(encU))\(sup(steps)) = \(n(pow(encU, Double(steps)), 3)) of the gradient"],
                headline: j == 4 ? "Every source symbol must squeeze through {\(encUnits) numbers}." : "Output \(j) copies \"\(encSource[j - 1])\" over a path of {\(steps) steps}.",
                body: j == 4 ? "Output 4 can only reach \"e\" through \(steps) recurrent steps and the context vector. Longer sources overflow it, which is the problem attention removes."
                    : "The decoder emits one symbol per step, feeding each back in, with only the context and its own state to go on."))
        }
        let long = 20
        frames.append(DkFrame(
            header: header, stage: stage(nSrc, nil, nil), legend: [lg[0], lg[1], lg[3]],
            formula: ["source of \(long): first symbol's path = \(long) steps", "\(t(encU))\(sup(long)) = {\(sci(pow(encU, Double(long))))}"],
            headline: "A \(long)-symbol source leaves its first symbol {\(sci(pow(encU, Double(long))))} of the gradient.",
            body: "The fixed-size context can't grow with the input. Attention lets each output look back at every encoder state directly."))
        return stepActions(frames, "Emit Next")
    }
}

// MARK: - Seq2seq: greedy vs beam search

private let eos = "⟨eos⟩"
private let vocab = ["a", "b", "c", "d", "e", eos]

private let nextProbs: [String: [String: Double]] = [
    "": ["e": 0.55, "b": 0.25, "c": 0.10, "a": 0.06, "d": 0.04, eos: 0.0],
    "e": ["b": 0.36, "c": 0.34, "a": 0.10, eos: 0.10, "d": 0.06, "e": 0.04],
    "b": ["e": 0.60, "c": 0.24, "a": 0.08, "d": 0.04, "b": 0.02, eos: 0.02],
    "c": ["a": 0.40, "b": 0.25, "e": 0.15, "d": 0.10, "c": 0.05, eos: 0.05],
    "a": ["e": 0.30, "b": 0.30, "c": 0.20, "d": 0.10, "a": 0.05, eos: 0.05],
    "d": ["e": 0.30, "b": 0.25, "c": 0.20, "a": 0.15, "d": 0.05, eos: 0.05],
    "eb": [eos: 0.25, "a": 0.24, "c": 0.20, "d": 0.16, "e": 0.10, "b": 0.05],
    "ec": ["b": 0.90, eos: 0.04, "a": 0.03, "d": 0.01, "e": 0.01, "c": 0.01],
]

/// P(next | prefix): the prefix's own table if it has one, else its last symbol's.
private func probs(_ prefix: [String]) -> [String: Double] { nextProbs[prefix.joined()] ?? nextProbs[prefix.last ?? ""]! }

private struct Hyp {
    let seq: [String]
    let score: Double
    var done: Bool { seq.last == eos }
    var text: String { seq.joined(separator: " ") }
}

private struct BeamStep { let expansions: [Hyp]; let kept: [Hyp]; let total: Int }

private func beamSearch(_ k: Int, _ steps: Int = 3) -> [BeamStep] {
    var beam = [Hyp(seq: [], score: 1)]
    var out: [BeamStep] = []
    for _ in 0..<steps {
        let live = beam.filter { !$0.done }
        let exp = live.flatMap { h in vocab.map { v in Hyp(seq: h.seq + [v], score: h.score * (probs(h.seq)[v] ?? 0)) } }.filter { $0.score > 0 }
        // A stable sort keeps ties in vocabulary order, as Kotlin's sortedByDescending does.
        let sortedExp = exp.enumerated().sorted { $0.element.score != $1.element.score ? $0.element.score > $1.element.score : $0.offset < $1.offset }.map(\.element)
        let pool = (exp + beam.filter { $0.done }).enumerated()
            .sorted { $0.element.score != $1.element.score ? $0.element.score > $1.element.score : $0.offset < $1.offset }.map(\.element)
        let kept = Array(pool.prefix(k))
        out.append(BeamStep(expansions: sortedExp, kept: kept, total: live.count * vocab.count))
        beam = kept
    }
    return out
}

private func seq2seqLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Greedy", "k = 2", "k = 5"], initialTab: 1) { tab, _ in
        let k = [1, 2, 5][tab]
        let run = beamSearch(k)
        let greedy = beamSearch(1).last!.kept[0]
        let best = run.last!.kept[0]
        let beam2 = beamSearch(2).last!.kept[0]
        func factors(_ h: Hyp) -> String { h.seq.indices.map { n(probs(Array(h.seq.prefix($0)))[h.seq[$0]] ?? 0) }.joined(separator: "·") }
        func pathText(_ h: Hyp) -> String { h.seq.joined(separator: " → ") }
        func compact(_ h: Hyp) -> String { h.text.replacingOccurrences(of: " ", with: "") }
        func stage(_ s: Int) -> DkStage {
            let st = run[s]
            let shown = Array(st.expansions.prefix(6))
            let keptTexts = Set(st.kept.map(\.text))
            let top = shown[0].score
            return .rows(DkRows(rows: shown.map { h in
                let kept = keptTexts.contains(h.text)
                return DkRow(title: h.text, meta: "", bars: [DkBar(frac: h.score / top, ink: .blue, label: n(h.score, 3) + (kept ? " kept" : ""))], inline: true, dim: !kept)
            }, caption: "step \(s + 1) · \(shown.count) of \(st.total) expansions shown, top \(k) kept"))
        }
        let header = "source \"e c b\" · 6 symbols, 55,986 sequences of length ≤ 6"
        let lg = [legend(.blue, "In the beam"), legend(.slate, "Pruned")]
        let greedyLine = "greedy: \(pathText(greedy)) = \(factors(greedy)) = \(n(greedy.score, 3))"
        let beamLine = k == 1 ? "beam, k = 2: \(pathText(beam2)) = \(factors(beam2)) = {\(n(beam2.score, 3))}"
            : "beam: \(pathText(best)) = \(factors(best)) = {\(n(best.score, 3))}"
        let first = run[0].expansions[0]
        let s2 = run[1].expansions
        let pB = probs(["e"])["b"]!, pC = probs(["e"])["c"]!
        let frames = [
            DkFrame(header: header, stage: stage(0), legend: lg,
                    formula: ["score = Π P(symbol | prefix)", "kept: top {\(k)} of \(run[0].total)"],
                    headline: "Step 1 scores all 6 first symbols; {\"\(first.text)\"} leads at \(n(first.score, 2)).",
                    body: k == 1 ? "Greedy keeps only the best one and builds on it." : "A beam of \(k) keeps the \(k) best and expands each of them at the next step."),
            DkFrame(header: header, stage: stage(1), legend: lg,
                    formula: [greedyLine, k == 1 ? "only one prefix survives each step" : beamLine],
                    headline: k == 1 ? "Greedy keeps only {\"\(compact(s2[0]))\"}: \(n(pB)) beat \(n(pC))."
                        : "\"\(compact(s2[0]))\" \(n(s2[0].score, 3)) edges out \"\(compact(s2[1]))\" \(n(s2[1].score, 3)); the beam keeps {\(k == 2 ? "both" : "the top \(k)")}.",
                    body: k == 1 ? "It commits to the locally best symbol and can never revisit that choice."
                        : "Greedy took \"b\" at step 2 because 0.36 beat 0.34. Keeping the runner-up gives the far better third step a chance."),
            DkFrame(header: header, stage: stage(2), legend: lg, formula: [greedyLine, beamLine],
                    headline: tab == 0 ? "Greedy commits to \"\(compact(greedy).replacingOccurrences(of: eos, with: ""))\" and ends at {\(n(greedy.score, 3))}."
                        : tab == 1 ? "Beam search finds \"\(compact(best))\" at {\(n(best.score, 3))}; greedy is stuck at \(n(greedy.score, 3))."
                        : "k = 5 finds the same \"\(compact(best))\" at {\(n(best.score, 3))}.",
                    body: tab == 0 ? "\"e c b\" scores \(n(beam2.score, 3)), more than three times as likely, but greedy dropped \"e c\" at step 2."
                        : tab == 1 ? "Greedy took \"b\" at step 2 because 0.36 beat 0.34. Keeping the runner-up let the far better third step win."
                        : "A wider beam costs 5 × 6 = 30 expansions a step instead of 12 and buys nothing more here. Translation systems typically use k = 4 to 10."),
        ]
        return stepActions(frames, "Expand Beam")
    }
}
