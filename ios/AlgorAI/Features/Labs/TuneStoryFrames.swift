import Foundation

// Port of TuneStoryFrames.kt: full fine-tuning, DPO, PEFT, LoRA & QLoRA, quantization, Flash Attention
// and RLHF, drawn by LlmStoryLabs.swift. Three tiny networks are trained here, DPO is optimised on seven
// pairs, 4,096 seeded weights are quantized and the online softmax is run block by block.

let tuneStoryTopicIds: Set<String> = ["fine_tuning_full", "dpo", "peft", "lora_qlora", "quantization", "flash_attention", "rlhf"]

func tuneLab(_ topicId: String) -> LsLab? {
    switch topicId {
    case "fine_tuning_full": fineTuneStory()
    case "dpo": dpoStory()
    case "peft": peftStory()
    case "lora_qlora": loraStory()
    case "quantization": quantStory()
    case "flash_attention": flashStory()
    case "rlhf": rlhfStory()
    default: nil
    }
}

private func f(_ x: Double, _ d: Int = 2) -> String { lsF(x, d) }
private func pct(_ p: Double) -> String { lsPct(p) }
private func S(_ rows: LsStat...) -> LsBlock { .stats(rows) }
private func st(_ k: String, _ v: String, _ ink: LsInk? = nil) -> LsStat { LsStat(k: k, v: v, ink: ink) }
private func F(_ a: String, _ b: String = "", _ ink: LsInk? = nil) -> LsFx { LsFx(a: a, b: b, ink: ink) }
private func fr(_ blocks: [LsBlock], _ fx: [LsFx], _ cap: (String, String)) -> LsFrame { LsFrame(blocks: blocks, fx: fx, headline: cap.0, body: cap.1) }
private func pick(_ on: Bool) -> LsText { on ? .strong : .muted }

// MARK: - Fine-tuning: scratch vs head only vs full

private struct FtNet { var w: [[Double]]; var b: [Double]; var v: [Double]; var c: Double }
private struct FtPt { let x: Double; let y: Double; let l: Int }
private struct FtSnap { let net: FtNet; let tr: Double; let te: Double }

private func ftHidden(_ m: FtNet, _ p: FtPt) -> [Double] { (0..<6).map { tanh(m.w[$0][0] * p.x + m.w[$0][1] * p.y + m.b[$0]) } }

private func ftProb(_ m: FtNet, _ p: FtPt) -> Double {
    let h = ftHidden(m, p)
    var z = m.c
    for j in 0..<6 { z += h[j] * m.v[j] }
    return 1 / (1 + exp(-z))
}

private func ftAcc(_ m: FtNet, _ d: [FtPt]) -> Double { Double(d.filter { (ftProb(m, $0) > 0.5 ? 1 : 0) == $0.l }.count) / Double(d.count) }

/// Batch gradient descent for 300 epochs; snapshots at 10, 50 and 300. `body` = false freezes the hidden layer.
private func ftRun(_ start: FtNet, _ body: Bool, _ lr: Double, _ tr: [FtPt], _ te: [FtPt]) -> [Int: FtSnap] {
    var m = start
    var snaps: [Int: FtSnap] = [:]
    for e in 1...300 {
        var gw = Array(repeating: [0.0, 0.0], count: 6)
        var gb = Array(repeating: 0.0, count: 6)
        var gv = Array(repeating: 0.0, count: 6)
        var gc = 0.0
        for p in tr {
            let h = ftHidden(m, p)
            var z = m.c
            for j in 0..<6 { z += h[j] * m.v[j] }
            let d = 1 / (1 + exp(-z)) - Double(p.l)
            gc += d
            for j in 0..<6 {
                gv[j] += d * h[j]
                if body {
                    let dp = d * m.v[j] * (1 - h[j] * h[j])
                    gw[j][0] += dp * p.x
                    gw[j][1] += dp * p.y
                    gb[j] += dp
                }
            }
        }
        let n = Double(tr.count)
        m.c -= lr * gc / n
        for j in 0..<6 { m.v[j] -= lr * gv[j] / n }
        if body {
            for j in 0..<6 {
                m.w[j][0] -= lr * gw[j][0] / n
                m.w[j][1] -= lr * gw[j][1] / n
                m.b[j] -= lr * gb[j] / n
            }
        }
        if e == 10 || e == 50 || e == 300 { snaps[e] = FtSnap(net: m, tr: ftAcc(m, tr), te: ftAcc(m, te)) }
    }
    return snaps
}

private func fineTuneStory() -> LsLab {
    var rnd = LsRng(11)
    var pts: [FtPt] = []
    for _ in 0..<76 {
        let x = rnd.next() * 2 - 1
        let y = rnd.next() * 2 - 1
        pts.append(FtPt(x: x, y: y, l: x * x + y * y < 0.45 ? 1 : 0))
    }
    let tr = Array(pts.prefix(16)), te = Array(pts.dropFirst(16))
    let pre = FtNet(w: (0..<6).map { [3 * cos(Double($0) * .pi / 3), 3 * sin(Double($0) * .pi / 3)] }, b: Array(repeating: -1.5, count: 6), v: Array(repeating: 0, count: 6), c: 0)
    var randW: [[Double]] = []
    for _ in 0..<6 { let a = rnd.next() * 2 - 1; let b = rnd.next() * 2 - 1; randW.append([a, b]) }
    var randB: [Double] = [], randV: [Double] = []
    for _ in 0..<6 { randB.append(rnd.next() - 0.5) }
    for _ in 0..<6 { randV.append(rnd.next() - 0.5) }
    let runs = [
        ftRun(FtNet(w: randW, b: randB, v: randV, c: 0), true, 0.5, tr, te),
        ftRun(pre, false, 0.5, tr, te),
        ftRun(pre, true, 0.5, tr, te),
    ]
    let names = ["From scratch", "Head only", "Full fine-tune"], counts = [25, 7, 25]
    return LsLab(tabs: ["Scratch", "Head only", "Full"], initialTab: 1,
                 legend: [(.green, "Right"), (.red, "Wrong"), (.grey, "Training point")]) { k in
        (0..<5).map { s in
            let ep = [0, 10, 50, 300, 300][s]
            let sn = s > 0 ? runs[k][ep]! : nil
            let all = te.map { ($0, false) } + tr.map { ($0, true) }
            let plot = LsBlock.plot(height: 170, pts: all.map { p, isT in
                var ink: LsInk = p.l == 1 ? .blue : .grey
                if let sn, s < 4 { ink = (ftProb(sn.net, p) > 0.5 ? 1 : 0) == p.l ? .green : .red }
                return LsPt(x: (p.x + 1) / 2, y: (p.y + 1) / 2, ink: ink, big: isT)
            })
            if s == 0 {
                return fr([.label("task: inside the circle? 16 training points (ringed), 60 test"), plot, S(st("trainable, head only", "7 weights"), st("trainable, full", "25 weights"))],
                          [F("body: 2 → 6 tanh units (18 weights)"), F("head: 6 → 1 sigmoid (7 weights)", "", .lilac)],
                          ("Three ways to use a small dataset.", "The body was pretrained on a related task. Train from scratch, train only a new head, or fine-tune everything."))
            }
            if s < 4, let sn {
                return fr([.label("\(names[k]), epoch \(ep): test predictions"), plot,
                           .bars([lsRow("train acc.", pct(sn.tr), sn.tr, .slate), lsRow("test acc.", pct(sn.te), sn.te, .violet, .strong)])],
                          [F("\(names[k]): \(counts[k]) trainable · lr 0.5"), F("epoch \(ep) test accuracy =", pct(sn.te), .lilac)],
                          ("Epoch \(ep): \(pct(sn.te)) on unseen points.",
                           k == 0 ? "Random features must be learned from 16 points; the boundary is slow to form and easy to overfit."
                           : k == 1 ? "The frozen features already describe distance from the centre; only 7 weights need fitting."
                           : "Starts from the pretrained features and adjusts them too — the most flexible, and the most to store."))
            }
            let last = runs[k][300]!
            return fr([.label("test accuracy after 300 epochs"),
                       .bars(runs.indices.map { j in let a = runs[j][300]!.te; return lsRow(names[j], pct(a), a, j == k ? .violet : .slate, pick(j == k)) }),
                       S(st("gap train − test", pct(last.tr - last.te)))],
                      [F("trained for real: batch gradient descent, 300 epochs")],
                      ("Pretrained features carry the small-data case.", "With 16 examples, reusing a body beats learning one. Full fine-tuning adds capacity on top, and a copy of every weight per task."))
        }
    }
}

// MARK: - DPO

private func dpoStory() -> LsLab {
    let names = ["A": "cites a source", "B": "correct, terse", "C": "hedged", "D": "confident, wrong"]
    let keys = ["A", "B", "C", "D"]
    let pairs = [("A", "B"), ("A", "C"), ("B", "C"), ("C", "D"), ("B", "D"), ("A", "D"), ("D", "A")]
    func sig(_ z: Double) -> Double { 1 / (1 + exp(-z)) }
    let betas = [0.1, 0.5, 2.0]
    return LsLab(tabs: ["β 0.1", "β 0.5", "β 2"], initialTab: 1,
                 legend: [(.blue, "Policy probability"), (.pink, "Bad response"), (.red, "Mislabelled pair")]) { tab in
        let beta = betas[tab]
        var th: [String: Double] = ["A": 0, "B": 0, "C": 0, "D": 0]
        var snaps: [Int: [String: Double]] = [0: th]
        for t in 1...1000 {
            var g: [String: Double] = ["A": 0, "B": 0, "C": 0, "D": 0]
            for (w, l) in pairs {
                let d = beta * (1 - sig(beta * (th[w]! - th[l]!)))
                g[w]! -= d
                g[l]! += d
            }
            for x in keys { th[x]! -= g[x]! / Double(pairs.count) }
            if t == 10 || t == 100 || t == 1000 { snaps[t] = th }
        }
        let chips = LsBlock.chips(pairs.enumerated().map { i, p in LsTok(t: "\(p.0) ≻ \(p.1)", tone: i == 6 ? .err : .plain, sub: i == 6 ? "mislabelled" : "") })
        return (0..<5).map { s in
            let big = [0, 0, 10, 100, 1000][s]
            let t = snaps[big]!
            let e = keys.map { exp(t[$0]!) }
            let sum = e.reduce(0, +)
            var pi: [String: Double] = [:]
            for (i, x) in keys.enumerated() { pi[x] = e[i] / sum }
            let loss = pairs.reduce(0.0) { $0 - log(sig(beta * (t[$1.0]! - t[$1.1]!))) } / Double(pairs.count)
            let kl = keys.reduce(0.0) { $0 + pi[$1]! * log(pi[$1]! / 0.25) }
            let bars = LsBlock.bars(keys.map { x in lsRow("\(x) · \(names[x]!)", pct(pi[x]!), pi[x]!, x == "D" ? .pink : .blue, x == "A" ? .strong : .normal) })
            let head: [LsBlock] = [.label("7 preference pairs (one mislabelled)"), chips]
            switch s {
            case 0:
                return fr(head + [.label("reference policy π_ref"), bars], [F("π_ref = uniform over 4 responses", "25% each")],
                          ("DPO learns straight from preferences.", "No reward model and no RL loop: one classification-style loss on (chosen, rejected) pairs."))
            case 1:
                return fr(head + [.label("implicit reward"), S(st("r(y)", "β · log π(y) / π_ref(y)"), st("β", lsJs(beta)))],
                          [F("loss = −log σ(β[(log π(y_w) − log π(y_l)) − ref])")],
                          ("The policy is its own reward model.", "How much more likely the policy makes a response than the reference does, scaled by β, plays the role of the reward."))
            default:
                let note = beta < 0.5 ? "small steps per pair, the policy stays near the reference" : beta > 1 ? "large β moves far from the reference fast" : "a middle setting"
                return fr(head + [.label("policy after \(big) steps"), bars],
                          [F("mean DPO loss =", f(loss, 3), .lilac), F("KL(π ‖ π_ref) =", f(kl, 3) + " nats")],
                          s == 4 ? ("After 1,000 steps: \(pct(pi["A"]!)) on the best response.", "β = \(lsJs(beta)): \(note). The mislabelled D ≻ A pair keeps pulling the other way.")
                          : ("Step \(big): the ranking A > B > C > D is emerging.", "KL from the reference is \(f(kl, 2)) nats so far."))
            }
        }
    }
}

// MARK: - PEFT on BERT-base

private func peftStory() -> LsLab {
    let total = 108891648.0
    let methods: [(String, Double)] = [("Full", total), ("Adapters r64", 2379264), ("Prefix (20)", 368640), ("LoRA r8 (Q,V)", 294912), ("BitFit", 102144), ("Head only", 1538)]
    let formulas = ["2 · 768 · 8 per matrix × 2 matrices × 12 layers", "(768·64 + 64 + 64·768 + 768) × 2 × 12", "every bias: 8,448 per layer × 12 + 768"]
    let how = [
        ("LoRA adds a rank-8 bypass to two attention matrices.", "Each frozen 768×768 weight gets B (768×8) · A (8×768) beside it."),
        ("Adapters insert a small bottleneck MLP twice per layer.", "768 → 64 → 768, after attention and after the FFN."),
        ("BitFit trains only the bias vectors.", "No new modules at all — the cheapest method that still touches every layer."),
    ]
    return LsLab(tabs: ["LoRA", "Adapters", "BitFit"], initialTab: 0,
                 legend: [(.blue, "Trainable"), (.pink, "Full fine-tune"), (.green, "Per-task file")]) { k in
        let pickIdx = [3, 1, 4][k]
        let (nm, n) = methods[pickIdx]
        let share = f(n / total * 100, 2) + "%"
        func mem(_ x: Double) -> Double { (total * 2 + x * 16) / 1e9 }
        return [
            fr([S(st("layers", "12"), st("width", "768"), st("FFN width", "3,072"), st("vocab", "30,522"), st("total", lsNum(total)))],
               [F("embeddings 23.8M + 12 × 7.09M =", "108.9M", .lilac)],
               ("One concrete model: BERT-base.", "Every count on this screen is arithmetic over this config, not a figure copied from a paper.")),
            fr([.label("trainable parameters, log scale"), .bars(methods.enumerated().map { j, m in lsRow(m.0, lsBig(m.1), log10(m.1) / 8.1, j == pickIdx ? .blue : .slate, pick(j == pickIdx)) })],
               [F("\(nm):", lsNum(n), .blue), F("share of model =", share)],
               ("Six ways to tune it span five orders of magnitude.", "\(nm) trains \(share) of the weights; the rest stay frozen.")),
            fr([S(st(nm, lsNum(n), .blue))], [F(formulas[k]), F("=", lsNum(n), .blue)], how[k]),
            fr([.label("training memory: fp16 weights + 16 B per trainable (fp32 copy, grad, Adam ×2)"),
                .bars([lsRow("Full", f(mem(total), 2) + " GB", mem(total) / 2.2, .pink), lsRow(nm, f(mem(n), 2) + " GB", mem(n) / 2.2, .blue, .strong)])],
               [F("full: 2·108.9M + 16·108.9M =", f(mem(total), 2) + " GB", .pink), F("\(nm): 2·108.9M + 16·\(lsBig(n)) =", f(mem(n), 2) + " GB", .blue)],
               ("\(f(mem(total) / mem(n), 1))× less training memory.", "The frozen weights still have to sit in memory; what disappears is the optimizer state for them.")),
            fr([.label("saved per task (fp16)"),
                .bars([lsRow("Full", lsBig(total * 2) + "B", 1, .pink), lsRow(nm, lsBig(n * 2) + "B", max(n / total, 0.01), .green, .strong)])],
               [F("100 tasks, full:", lsBig(total * 2 * 100) + "B", .pink), F("100 tasks, \(nm):", lsBig(total * 2 + n * 2 * 100) + "B incl. one base", .green)],
               ("The real win is what you store per task.", "One shared base model plus a tiny file per task, instead of a full copy each time.")),
        ]
    }
}

// MARK: - LoRA & QLoRA

private func loraStory() -> LsLab {
    let ds = [2.34, 2.12, 1.19, 0.46, 0.41, 0.37, 0.33, 0.30, 0.27, 0.25, 0.22, 0.20, 0.18, 0.16, 0.14, 0.12, 0.11, 0.10, 0.09, 0.08, 0.07, 0.06, 0.05, 0.04]
    let ws = ds.indices.map { 3.1 * pow(0.93, Double($0)) }
    func energy(_ a: ArraySlice<Double>) -> Double { a.reduce(0) { $0 + $1 * $1 } }
    let ranks = [1, 2, 4, 8]
    return LsLab(tabs: ["r 1", "r 2", "r 4", "r 8"], initialTab: 1,
                 legend: [(.pink, "Update spectrum / full"), (.blue, "Kept by LoRA"), (.green, "QLoRA")]) { tab in
        let r = ranks[tab]
        let keep = energy(ds.prefix(r)) / energy(ds[...])
        let d = 4096.0, full = d * d, lora = 2 * d * Double(r)
        let fullMem = 7e9 * (2 + 2 + 12) / 1e9, q = 7e9 * 0.5 / 1e9
        return [
            fr([.label("singular values, first 10 of 24"), .bars((0..<10).map { i in lsRow("σ\(i + 1)", "ΔW \(f(ds[i])) · W₀ \(f(ws[i]))", ds[i] / 3.2, .pink) })],
               [F("ΔW = W_finetuned − W₀ (24×24)")],
               ("LoRA’s claim: the update is low-rank, not the model.", "ΔW’s spectrum collapses after three values; the pretrained W₀ decays slowly.")),
            fr([.label("energy kept by the top \(r)"), .bars((0..<10).map { i in lsRow("σ\(i + 1)", f(ds[i]), ds[i] / 2.4, i < r ? .blue : .slate, i < r ? .strong : .muted) }),
                S(st("energy kept", pct(keep), .blue))],
               [F("Σ σ²(top \(r)) / Σ σ² =", pct(keep), .blue)],
               ("Rank \(r) keeps \(pct(keep)) of the update.", r < 3 ? "Too low: part of the real update can’t be represented." : "Past the cliff, extra rank buys almost nothing.")),
            fr([S(st("full ΔW, d = 4096", lsNum(full)), st("LoRA B·A, r = \(r)", lsNum(lora), .blue), st("ratio", "\(lsNum(full / lora))×"))],
               [F("2 · 4096 · \(r) =", lsNum(lora), .blue)],
               ("\(lsNum(full / lora))× fewer parameters per matrix.", "Train B (d×r) and A (r×d) instead of the d×d update; W₀ stays frozen.")),
            fr([.label("fine-tuning a 7B model"), .bars([lsRow("full fp16", f(fullMem, 0) + " GB", 1, .pink), lsRow("QLoRA 4-bit", f(q, 1) + " GB + adapters", q / fullMem + 0.02, .green, .strong)])],
               [F("full: 7B × (2 W + 2 grad + 12 Adam/fp32) =", f(fullMem, 0) + " GB", .pink), F("QLoRA base: 7B × 0.5 B =", f(q, 1) + " GB", .green)],
               ("QLoRA stores the frozen base in 4 bits.", "Gradients flow through the quantized weights into fp16 adapters. A 7B fine-tune fits on one consumer GPU.")),
            fr([S(st("inference", "W = W₀ + B·A, merged once"), st("extra latency", "0"))],
               [F("merge cost: one d×r×d multiply per matrix")],
               ("At inference, merge and forget.", "B·A is added into W₀, so the deployed model is exactly the original shape — unlike adapters, which add layers.")),
        ]
    }
}

// MARK: - Quantization

private let quantWeights: [Double] = {
    var rnd = LsRng(5)
    var w: [Double] = []
    for _ in 0..<4096 {
        var u = rnd.next()
        if u == 0 { u = 1e-9 }
        let v = rnd.next()
        w.append(0.02 * sqrt(-2 * log(u)) * cos(2 * .pi * v))
    }
    return w
}()

private func quantStory() -> LsLab {
    let w = quantWeights, n = Double(w.count)
    var bins = Array(repeating: 0, count: 16)
    for x in w { let j = Int(((x / 0.08 + 0.5) * 16).rounded(.down)); if j >= 0 && j < 16 { bins[j] += 1 } }
    let mx = Double(bins.max()!)
    let hist = LsBlock.hist(bins.map { Double($0) / mx })
    let variance = w.reduce(0) { $0 + $1 * $1 } / n
    func mse(_ a: [Double]) -> Double { var s = 0.0; for i in a.indices { s += (a[i] - w[i]) * (a[i] - w[i]) }; return s / n }
    func snr(_ a: [Double]) -> Double { 10 * log10(variance / mse(a)) }
    let widths = [8, 4, 3]
    return LsLab(tabs: ["8-bit", "4-bit", "3-bit"], initialTab: 1,
                 legend: [(.grey, "Weights"), (.green, "Quantized"), (.pink, "Error")]) { tab in
        let b = widths[tab]
        let qmax = pow(2, Double(b - 1)) - 1
        let amax = w.map { abs($0) }.max()!
        let sc = amax / qmax
        let deq = w.map { lsRound($0 / sc) * sc }
        var blk: [Double] = []
        for start in stride(from: 0, to: w.count, by: 64) {
            let chunk = w[start..<min(start + 64, w.count)]
            let s2 = chunk.map { abs($0) }.max()! / qmax
            blk += chunk.map { lsRound($0 / s2) * s2 }
        }
        let used = Set(w.map { lsRound($0 / sc) }).count
        let levels = lsNum(2 * qmax + 1)
        let snrT = snr(deq), snrB = snr(blk)
        let bits = Double(b) + 16.0 / 64
        let g = 7e9 * bits / 8 / 1e9
        return [
            fr([.label("4,096 weights, σ = 0.02"), hist], [F("fp32: 4,096 × 4 B =", "16,384 B")],
               ("Trained weights look like a bell curve.", "Quantizing replaces each with the nearest of a few levels and stores the level’s index.")),
            fr([.label("absmax scaling"), hist, S(st("max |w|", f(amax, 4)), st("levels ±\(lsNum(qmax))", levels))],
               [F("scale = \(f(amax, 4)) / \(lsNum(qmax)) =", lsExp(sc), .green)],
               ("\(b)-bit: \(levels) levels spread to the largest weight.", "One outlier sets the scale for everyone.")),
            fr([.label("levels actually used"), hist, S(st("levels available", levels), st("levels used", "\(used)", .green))],
               [F("q = round(w / scale)")],
               ("Only \(used) of \(levels) levels get used.", b <= 4 ? "The tails are rare, so most weights crowd into a handful of levels near zero." : "At 8 bits there are levels to spare.")),
            fr([.label("reconstruction error, whole tensor"), S(st("MSE", lsExp(mse(deq))), st("SNR", f(snrT, 1) + " dB", .pink))],
               [F("SNR = 10·log₁₀(var(w) / MSE) =", f(snrT, 1) + " dB", .pink)],
               ("\(f(snrT, 1)) dB signal-to-noise.", "Each bit removed costs roughly 6 dB.")),
            fr([.label("per-tensor vs per-block (64) scales"), .bars([lsRow("per-tensor", f(snrT, 1) + " dB", snrT / 50, .slate), lsRow("block 64", f(snrB, 1) + " dB", snrB / 50, .green, .strong)])],
               [F("gain from blockwise scales =", "+\(f(snrB - snrT, 1)) dB", .green)],
               ("Blockwise scales tame outliers.", "Each 64-weight block gets its own scale, so one large weight only coarsens its own block.")),
            fr([.label("7B model weights"), .bars([lsRow("fp16", f(14, 1) + " GB", 1, .slate), lsRow("\(b)-bit + scales", f(g, 1) + " GB", g / 14, .green, .strong)])],
               [F("\(b) + 16/64 bits per weight =", f(bits, 2) + " bits"), F("7B ×", f(g, 1) + " GB", .green)],
               ("\(f(14 / g, 1))× smaller than fp16.", "Scales cost a quarter-bit per weight at block 64 — worth it for the error they remove.")),
        ]
    }
}

// MARK: - Flash Attention: the online softmax

private func flashStory() -> LsLab {
    let sc = [2.1, 3.4, 7.6, 5.0, 7.9, 1.2, 6.3, 4.4]
    let vals = [1, 0, 2, 1, 3, 0, 2, 1]
    let nb = sc.count
    struct H { let mOld: Double; let m: Double; let r: Double; let e: Double; let l: Double; let acc: Double }
    var hist: [H] = []
    var m = -Double.infinity, l = 0.0, acc = 0.0
    for i in 0..<nb {
        let mn = max(m, sc[i])
        let r = m == -Double.infinity ? 0 : exp(m - mn)
        let e = exp(sc[i] - mn)
        l = l * r + e
        acc = acc * r + e * Double(vals[i])
        hist.append(H(mOld: m, m: mn, r: r, e: e, l: l, acc: acc))
        m = mn
    }
    let top = sc.max()!
    let ex = sc.map { exp($0 - top) }
    var num = 0.0
    for i in ex.indices { num += ex[i] * Double(vals[i]) }
    let exact = num / ex.reduce(0, +)
    let hs = hist
    return LsLab(tabs: ["4K", "32K", "128K"], initialTab: 0,
                 legend: [(.yellow, "Current block"), (.green, "Folded in"), (.violet, "Result")]) { tab in
        let ns = [4096.0, 32768.0, 131072.0][tab]
        let nl = ["4K", "32K", "128K"][tab]
        return (0..<(nb + 3)).map { s in
            let cur: Int? = (1...nb).contains(s) ? s - 1 : nil
            let chips = LsBlock.chips(sc.enumerated().map { i, x in
                let tone: LsTone = cur == i ? .cur : (s > nb || (cur != nil && i < cur!)) ? .done : .fut
                return LsTok(t: "b\(i)", tone: tone, sub: "s " + f(x, 1))
            })
            let head: [LsBlock] = [.label("one query row, 8 key blocks (s = score, v = value)"), chips]
            if s == 0 {
                return fr(head, [F("softmax needs max and Σ over all 8 blocks"), F("standard: write all scores to memory first")],
                          ("Attention’s softmax seems to need the whole row.", "Flash Attention streams blocks through fast on-chip memory, keeping three running numbers: m, ℓ, acc."))
            }
            if let cur {
                let h = hs[cur]
                let rescale = h.r < 1 && cur > 0
                return fr(head + [S(st("running max m", f(h.m, 2), .yellow), st("ℓ (denominator)", f(h.l, 4)), st("acc (numerator)", f(h.acc, 4)))],
                          rescale ? [F("m: \(f(h.mOld, 2)) → \(f(h.m, 2)), rescale ×", f(h.r, 4), .yellow), F("ℓ = ℓ·\(f(h.r, 4)) + e^(\(f(sc[cur], 1))−\(f(h.m, 1))) =", f(h.l, 4))]
                          : [F("ℓ = ℓ + e^(\(f(sc[cur], 1))−\(f(h.m, 1))) =", f(h.l, 4)), F("acc += \(f(h.e, 4)) × v=\(vals[cur]) =", f(h.acc, 4))],
                          rescale ? ("Block \(cur) beats the running max.", "Everything so far was scaled to the old max, so ℓ and acc are multiplied by \(f(h.r, 4)) before adding this block.")
                          : ("Block \(cur) folds in without rescaling.", cur == 0 ? "The first block sets the max." : "\(f(sc[cur], 1)) is below the max \(f(h.m, 1)), so it just adds its share."))
            }
            if s == nb + 1 {
                let h = hs[nb - 1]
                return fr(head + [S(st("streamed result", f(h.acc / h.l, 6), .lilac), st("exact softmax", f(exact, 6)))],
                          [F("out = acc / ℓ = \(f(h.acc, 4)) / \(f(h.l, 4)) =", f(h.acc / h.l, 6), .lilac)],
                          ("Identical to the exact softmax.", "Not an approximation: the rescaling makes the streamed sum mathematically equal."))
            }
            let std = ns * ns * 2 / 1e6, fl = ns * 2 * 3 / 1e3
            return fr(head + [.bars([lsRow("score matrix", f(std, 0) + " MB", 1, .pink), lsRow("Flash state", f(fl, 1) + " KB", 0.02, .green, .strong)])],
                      [F("N² × 2 B = \(nl)² × 2 =", f(std, 0) + " MB per head", .pink)],
                      ("At \(nl) tokens the full score matrix would be \(f(std, 0)) MB per head.", "Flash never writes it. The work is the same; the slow-memory traffic disappears."))
        }
    }
}

// MARK: - RLHF: the reward model's blind spot

private func rlhfStory() -> LsLab {
    struct Beh { let n: String; let goal: Double; let steps: Double; let vase: Double }
    let behs = [Beh(n: "careful path", goal: 1, steps: 10, vase: 0), Beh(n: "shortcut via vase", goal: 1, steps: 6, vase: 1), Beh(n: "wander", goal: 0, steps: 12, vase: 0)]
    let ref = [0.5, 0.2, 0.3]
    func tru(_ b: Beh) -> Double { 2 * b.goal - 0.1 * b.steps - 1.5 * b.vase }
    func prox(_ b: Beh) -> Double { 2.06 * b.goal - 0.1 * b.steps }
    func pol(_ bt: Double) -> [Double] {
        let w = behs.indices.map { ref[$0] * exp(prox(behs[$0]) / bt) }
        let s = w.reduce(0, +)
        return w.map { $0 / s }
    }
    func ex(_ p: [Double], _ fn: (Beh) -> Double) -> Double { behs.indices.reduce(0) { $0 + p[$1] * fn(behs[$1]) } }
    let betas = [0.1, 0.5, 2.0]
    return LsLab(tabs: ["β 0.1", "β 0.5", "β 2"], initialTab: 0,
                 legend: [(.violet, "Proxy reward"), (.green, "True return"), (.pink, "Hidden cost")]) { tab in
        let beta = betas[tab]
        let pi = pol(beta)
        let eProx = ex(pi, prox), eTrue = ex(pi, tru)
        return [
            fr([.label("three behaviours, reference policy"), .bars(behs.indices.map { lsRow(behs[$0].n, pct(ref[$0]), ref[$0], .slate) }), S(st("true reward", "2·goal − 0.1·steps − 1.5·vase"))],
               [F("raters compare trajectories pairwise")],
               ("RLHF learns a reward from human comparisons.", "Raters can see whether the goal was reached and how long it took. They can’t see the vase.")),
            fr([.label("fitted reward weights vs true"), .bars([lsBrow("goal", 2.06, 2.5, "2.06 / 2.0"), lsBrow("steps", -0.1, 2.5, "−0.10 / −0.10"), lsBrow("vase", 0, 2.5, "0.00 / −1.50")])],
               [F("vase weight:", "0 — never observed", .red)],
               ("The reward model is right about everything it was shown.", "It agrees with raters on held-out pairs, and is blind to the cost they never saw.")),
            fr([.label("policy π ∝ π_ref · exp(r / β), β = \(lsJs(beta))"), .bars(behs.indices.map { lsRow(behs[$0].n, pct(pi[$0]), pi[$0], $0 == 1 ? .pink : .violet, $0 == 1 ? .strong : .normal) })],
               [F("proxy r: careful 1.06, vase 1.46, wander −1.20"), F("shortcut share =", pct(pi[1]), .pink)],
               ("The policy optimises the proxy.", "The shortcut scores 0.4 higher on the learned reward, so it gets more probability — limited only by the KL leash β.")),
            fr([.bars([lsRow("proxy reward", f(eProx), max(eProx, 0) / 1.6, .violet, .strong), lsRow("true return", f(eTrue), max(eTrue, 0) / 1.6, .green, .strong)])],
               [F("E[proxy] =", f(eProx, 3), .lilac), F("E[true] =", f(eTrue, 3), .green)],
               ("The proxy goes up; the true return need not.", "Gap \(f(eProx - eTrue, 2)): the policy is paid for breaking the vase.")),
            fr([.label("true return by KL strength β"), .bars(betas.map { bt in let v = ex(pol(bt), tru); return lsBrow("β \(lsJs(bt))", v, 1, f(v, 3), pick(bt == beta)) }),
                S(st("reference policy", f(ex(ref, tru), 3)))],
               [F("weak KL (β 0.1) → exploits the proxy")],
               ("The KL penalty is the guard against reward hacking.", "Weak leash: the policy chases the proxy into the blind spot. Strong leash: little improvement at all.")),
        ]
    }
}
