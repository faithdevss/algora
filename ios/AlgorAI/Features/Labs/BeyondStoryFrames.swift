import Foundation

// Port of BeyondStoryFrames.kt: SSMs, Mamba and RWKV run token by token, long-context costs from
// LLaMA-2-7B's published config, and perplexity, WER, BLEU, ROUGE, METEOR and MMLU each computed from its
// definition on the strings shown. Drawn by LlmStoryLabs.swift.

let beyondStoryTopicIds: Set<String> = ["ssm", "mamba", "rwkv", "long_context", "perplexity", "wer", "bleu", "rouge", "meteor", "mmlu"]

func beyondLab(_ topicId: String) -> LsLab? {
    switch topicId {
    case "ssm": ssmStory()
    case "mamba": mambaStory()
    case "rwkv": rwkvStory()
    case "long_context": longContextStory()
    case "perplexity": perplexityStory()
    case "wer": werStory()
    case "bleu": bleuStory()
    case "rouge": rougeStory()
    case "meteor": meteorStory()
    case "mmlu": mmluStory()
    default: nil
    }
}

private func f(_ x: Double, _ d: Int = 2) -> String { lsF(x, d) }
private func pct(_ p: Double) -> String { lsPct(p) }
private func C(_ toks: [LsTok]) -> LsBlock { .chips(toks) }
private func S(_ rows: LsStat...) -> LsBlock { .stats(rows) }
private func st(_ k: String, _ v: String, _ ink: LsInk? = nil) -> LsStat { LsStat(k: k, v: v, ink: ink) }
private func F(_ a: String, _ b: String = "", _ ink: LsInk? = nil) -> LsFx { LsFx(a: a, b: b, ink: ink) }
private func fr(_ blocks: [LsBlock], _ fx: [LsFx], _ cap: (String, String)) -> LsFrame { LsFrame(blocks: blocks, fx: fx, headline: cap.0, body: cap.1) }
private func pick(_ on: Bool) -> LsText { on ? .strong : .muted }
private func words(_ s: String) -> [String] { s.components(separatedBy: " ") }

// MARK: - State space model

private func ssmStory() -> LsLab {
    let xs = [1.7, 1.0, 0.0, -0.5, 1.7, -0.7, -0.6, -0.4]
    let cc = [0.4, 0.3, 0.2, 0.1]
    return LsLab(tabs: ["slow Ā", "fast Ā"], initialTab: 0,
                 legend: [(.yellow, "Current token"), (.blue, "State"), (.violet, "Output / kernel")]) { tab in
        let a = [[0.95, 0.9, 0.8, 0.6], [0.6, 0.4, 0.2, 0.1]][tab]
        var hs: [(h: [Double], y: Double)] = []
        var h = [0.0, 0.0, 0.0, 0.0]
        for x in xs {
            h = h.indices.map { a[$0] * h[$0] + x }
            hs.append((h, h.indices.reduce(0) { $0 + cc[$1] * h[$1] }))
        }
        let kernel = (0..<8).map { k in a.indices.reduce(0.0) { $0 + cc[$1] * pow(a[$1], Double(k)) } }
        let ys = xs.indices.map { t in (0...t).reduce(0.0) { $0 + kernel[t - $1] * xs[$1] } }
        let states = hs
        return (0..<(xs.count + 2)).map { s in
            let cur: Int? = (1...8).contains(s) ? s - 1 : nil
            let chips = C(xs.enumerated().map { i, x in
                LsTok(t: f(x, 1), tone: cur == i ? .cur : (s == 9 || (cur != nil && i < cur!)) ? .done : .fut, sub: "t\(i)")
            })
            let done = s == 9 ? 8 : cur != nil ? cur! + 1 : 0
            let yc = C((0..<max(done, 1)).map { i in i < done ? LsTok(t: f(states[i].y, 1), tone: .ans) : LsTok(t: "·", tone: .fut) })
            let head: [LsBlock] = [.label("input x"), chips]
            if s == 0 {
                return fr(head + [S(st("Ā per channel", a.map(lsJs).joined(separator: ", ")), st("B̄", "1"), st("C", cc.map(lsJs).joined(separator: ", ")))],
                          [F("h_t = Ā·h_{t−1} + B̄·x_t"), F("y_t = C·h_t")],
                          ("A state space model is a linear recurrence.", "Four channels, each forgetting at its own rate Ā. Fixed numbers: the same for every token."))
            }
            if let cur {
                let p = states[cur]
                return fr(head + [.label("state h, 4 channels"), .bars(p.h.indices.map { lsBrow("Ā " + lsJs(a[$0]), p.h[$0], 4) }), .label("output y"), yc],
                          [F("h₀ = \(lsJs(a[0]))·\(f(cur > 0 ? states[cur - 1].h[0] : 0)) + \(f(xs[cur], 1)) =", f(p.h[0]), .blue), F("y = C·h =", f(p.y, 3), .lilac)],
                          ("Token \(cur): every channel decays, then adds x.", "One multiply-add per channel per token: constant cost and memory at any length."))
            }
            return fr(head + [.label("same outputs as one convolution, kernel K_k = Σ C·Āᵏ"), .bars((0..<4).map { lsRow("K\($0)", f(kernel[$0], 3), kernel[$0], .violet) })],
                      [F("y₇ by recurrence =", f(states[7].y, 4)), F("y₇ by convolution =", f(ys[7], 4), .lilac)],
                      ("Because Ā is fixed, the recurrence is a convolution.", "Train in parallel with one kernel, decode with the cheap recurrence — same numbers."))
        }
    }
}

// MARK: - Mamba: selective state

private func mambaStory() -> LsLab {
    let xs = ["SIG", "·", "·", "·", "·", "·", "·", "·"]
    let a = 0.6
    return LsLab(tabs: ["LTI Ā = 0.6", "Selective"], initialTab: 1,
                 legend: [(.violet, "Signal"), (.blue, "State"), (.yellow, "Current")]) { tab in
        let sel = tab == 1
        var hs: [(h: Double, sig: Double, aBar: Double, bBar: Double)] = []
        var h = 0.0, sig = 0.0
        for x in xs {
            let isSig = x == "SIG"
            let v = isSig ? 1.0 : 0.1
            if sel {
                if isSig { h = 1; sig = 1 }
            } else {
                h = a * h + v
                sig = isSig ? 1 : sig * a
            }
            hs.append((h, sig, sel ? (isSig ? 0 : 1) : a, sel ? (isSig ? 1 : 0) : 1))
        }
        let states = hs
        return (0..<(xs.count + 2)).map { s in
            let cur: Int? = (1...8).contains(s) ? s - 1 : nil
            let upto = s == 9 ? 8 : cur != nil ? cur! + 1 : 0
            let chips = C(xs.enumerated().map { i, x in
                LsTok(t: x, tone: cur == i ? .cur : x == "SIG" ? .ans : i < upto ? .done : .fut, sub: i < upto ? "h " + f(states[i].h) : "")
            })
            let head: [LsBlock] = [.label("one signal, then 7 fillers (value 0.1 each)"), chips]
            if s == 0 {
                return fr(head, [F(sel ? "selective: Ā, B̄ computed from each token" : "LTI: Ā = 0.6 for every token")],
                          ("Can the state keep one token across seven fillers?", sel ? "Mamba makes Ā and B̄ functions of the input: a filler can say “ignore me”." : "A plain SSM applies the same decay to everything."))
            }
            if let cur {
                let p = states[cur]
                let cap: (String, String) = cur == 0 ? ("The signal is written into the state.", sel ? "The input gate opens fully: Ā = 0 clears the old state, B̄ = 1 writes SIG." : "h = 1.0.")
                    : sel ? ("Filler \(cur): the gate closes.", "Ā = 1, B̄ = 0 — the state passes through untouched.")
                    : ("Filler \(cur): SIG fades to \(f(p.sig, 3)).", "Fixed decay forgets on a timer, signal and noise alike.")
                return fr(head + [.bars([lsRow("state h", f(p.h, 3), p.h / 1.1, .blue, .strong), lsRow("from SIG", f(p.sig, 3), p.sig / 1.1, .violet, .strong)])],
                          [F("Ā = \(f(p.aBar)), B̄ = \(f(p.bBar))"), F("h =", f(p.h, 3), .blue)], cap)
            }
            let a7 = pow(a, 7)
            return fr(head + [.bars([lsRow("LTI, SIG share", pct(a7 / (a7 + 0.1 * (1 - a7) / (1 - a))), a7, .slate), lsRow("selective", pct(1), 1, .violet, .strong)])],
                      [F("LTI: 0.6⁷ =", f(a7, 4)), F("selective: SIG intact =", "1.000", .lilac)],
                      ("Selection is what lets Mamba remember.", "Input-dependent Ā and B̄ break the convolution trick, so Mamba uses a parallel scan instead — linear time, content-aware memory."))
        }
    }
}

// MARK: - RWKV

private func rwkvStory() -> LsLab {
    let keys = [0.3, -1.1, -1.7, -1.7, 1.0, 3.5, -1.4, -0.9]
    let vals = [1.8, -0.2, -1.2, -1.0, 1.5, -2.1, 0.5, -0.3]
    let u = 0.5
    let decays = [0.1, 0.5, 2.0]
    return LsLab(tabs: ["w 0.1", "w 0.5", "w 2"], initialTab: 1,
                 legend: [(.yellow, "Current token"), (.blue, "Weight"), (.violet, "Read-out")]) { tab in
        let w = decays[tab]
        func wkv(_ t: Int) -> ([Double], Double) {
            let ws = (0...t).map { i in i == t ? exp(u + keys[i]) : exp(-Double(t - 1 - i) * w + keys[i]) }
            let sum = ws.reduce(0, +)
            var out = 0.0
            for i in ws.indices { out += ws[i] * vals[i] }
            return (ws.map { $0 / sum }, out / sum)
        }
        return (0..<10).map { s in
            let cur: Int? = (1...8).contains(s) ? s - 1 : nil
            let chips = C(keys.enumerated().map { i, k in
                LsTok(t: "k " + f(k, 1), tone: cur == i ? .cur : (cur != nil && i < cur!) ? .done : .fut, sub: "v " + f(vals[i], 1))
            })
            let head: [LsBlock] = [.label("8 tokens: key k (bid to be remembered), value v"), chips]
            if s == 0 {
                return fr(head, [F("weight(i) = e^(k_i − w·distance)"), F("w = \(lsJs(w)), bonus u = \(lsJs(u)) for the current token")],
                          ("RWKV replaces attention with a weighted average.", "There is no query: weights depend only on each token’s key and how far back it is."))
            }
            if let cur {
                let (ws, out) = wkv(cur)
                var top = 0
                for i in ws.indices where ws[i] > ws[top] { top = i }
                return fr(head + [.label("weights at t = \(cur)"), .bars(ws.enumerated().map { i, x in lsRow("t\(i)", pct(x), x, i == cur ? .yellow : .blue, i == cur ? .strong : .normal) })],
                          [F("out_\(cur) = Σ weight·v =", f(out, 3), .lilac)],
                          ("t = \(cur): token \(top) dominates.", w > 1 ? "Strong decay: only the last token or two count." : keys[top] > 2 ? "Its key \(f(keys[top], 1)) outbids distance." : "Old tokens fade by e^(−w) per step."))
            }
            return fr(head + [S(st("state per channel", "2 numbers (a, b)"), st("memory at t = 1M", "still 2 numbers"))],
                      [F("a ← e^(−w)·a + e^k·v"), F("b ← e^(−w)·b + e^k  ·  out = a / b")],
                      ("The same average runs as an RNN.", "Numerator and denominator update in O(1) per token, so generation never grows a KV cache."))
        }
    }
}

// MARK: - Long context on LLaMA-2-7B

private func longContextStory() -> LsLab {
    let params = 6.74e9, layers = 32.0, d = 4096.0
    let kvTok = 2.0 * layers * 32 * 128 * 2
    let wts = params * 2
    let windows = [4096.0, 32768.0, 131072.0], names = ["4K", "32K", "128K"]
    return LsLab(tabs: names, initialTab: 2,
                 legend: [(.violet, "Cache"), (.pink, "Attention cost"), (.yellow, "Position scale")]) { tab in
        let n = windows[tab], nl = names[tab]
        let kv = n * kvTok
        let fW = 2 * params * n, fA = 4 * layers * n * n * d
        let scale = n / 4096
        return [
            fr([S(st("parameters", "6.74B"), st("layers · heads", "32 · 32"), st("head dim", "128"), st("trained context", "4,096"))],
               [F("weights: 6.74B × 2 B =", f(wts / 1e9, 1) + " GB")],
               ("A context window has three prices.", "LLaMA-2-7B’s published config, so every figure is checkable.")),
            fr([.label("KV cache, one sequence"), .bars([lsRow("weights", f(wts / 1e9, 1) + " GB", wts / 8e10, .slate), lsRow("KV @ \(nl)", f(kv / 1e9, 1) + " GB", kv / 8e10, .violet, .strong)])],
               [F("2 × 32 × 32 × 128 × 2 B =", "512 KiB / token"), F("× \(lsNum(n)) =", f(kv / 1e9, 1) + " GB", .lilac)],
               ("Price one: memory.", kv > wts ? "At \(nl) the cache outweighs the model itself." : "The cache grows linearly with every token kept.")),
            fr([.label("prefill FLOPs"), .bars([lsRow("weights 2·N·n", lsSci(fW), fW / (fW + fA), .blue), lsRow("attention 4·L·n²·d", lsSci(fA), fA / (fW + fA), .pink, .strong)])],
               [F("attention share =", pct(fA / (fW + fA)), .pink)],
               ("Price two: quadratic compute.", fA > fW ? "At \(nl), attention costs more than all the weights combined." : "Short contexts are dominated by the weights; the n² term is waiting.")),
            fr([S(st("trained on", "4,096 positions"), st("asked for", lsNum(n)), st("RoPE interpolation factor", "\(lsJs(scale))×", .yellow))],
               [F("positions scaled by 4096 / \(lsNum(n)) =", f(1 / scale, 4))],
               ("Price three: positions it never saw.", scale > 1 ? "Position interpolation squeezes new positions into the trained range, then fine-tunes briefly." : "Within the trained window: no extension needed.")),
            fr([.label("memory per sequence, three windows"), .bars(windows.enumerated().map { j, m in lsRow(names[j], f((wts + m * kvTok) / 1e9, 1) + " GB", (wts + m * kvTok) / 9e10, m == n ? .violet : .slate, pick(m == n)) })],
               [F("weights + KV =", f((wts + kv) / 1e9, 1) + " GB", .lilac)],
               ("The advertised number names none of these costs.", "Grouped-query attention, cache quantization and sliding windows each attack one of them.")),
        ]
    }
}

// MARK: - Perplexity of a bigram model

private func perplexityStory() -> LsLab {
    let corpus = ["the cat sat on the mat", "the dog sat on the rug", "the cat ate the fish"]
    var uni: [String: Int] = [:], bi: [String: Int] = [:]
    var voc: Set<String> = ["</s>"]
    for c in corpus {
        let t = ["<s>"] + words(c) + ["</s>"]
        for w in t where w != "<s>" { voc.insert(w) }
        for i in 0..<(t.count - 1) {
            uni[t[i], default: 0] += 1
            bi[t[i] + " " + t[i + 1], default: 0] += 1
        }
    }
    let v = voc.count
    let uniC = uni, biC = bi
    func prob(_ a: String, _ b: String) -> Double { Double((biC[a + " " + b] ?? 0) + 1) / Double((uniC[a] ?? 0) + v) }
    func bits(_ p: Double) -> Double { -log(p) / log(2) }
    func score(_ c: String) -> (ps: [(w: String, p: Double, seen: Bool)], h: Double, ppl: Double) {
        let t = ["<s>"] + words(c) + ["</s>"]
        let ps = (1..<t.count).map { (w: t[$0], p: prob(t[$0 - 1], t[$0]), seen: biC[t[$0 - 1] + " " + t[$0]] != nil) }
        let h = ps.reduce(0) { $0 + bits($1.p) } / Double(ps.count)
        return (ps, h, pow(2, h))
    }
    let tests = ["the cat sat on the rug", "the rug ate the cat", "cat the on sat rug the"]
    let names = ["sentence", "reordered", "scrambled"]
    return LsLab(tabs: names, initialTab: 0,
                 legend: [(.blue, "Seen bigram"), (.yellow, "Unseen bigram"), (.violet, "Perplexity")]) { k in
        let r = score(tests[k])
        return [
            fr([.label("training corpus"), .stats(corpus.enumerated().map { st("sentence \($0 + 1)", $1) }), S(st("vocabulary V", "\(v)"))],
               [F("P(w | prev) = (c(prev,w) + 1) / (c(prev) + V)")],
               ("A bigram model with add-1 smoothing.", "Perplexity needs no reference answer — only the model and some text.")),
            fr([.label("P(token | previous) for “\(tests[k])”"), C(r.ps.map { LsTok(t: $0.w, tone: $0.seen ? .plain : .cur, sub: f($0.p, 3)) })],
               [F("P(\(r.ps[0].w) | <s>) =", f(r.ps[0].p, 3))],
               ("Score every token given the one before.", "Yellow tokens follow a bigram never seen in training; smoothing gives them a small but non-zero probability.")),
            fr([.label("surprisal −log₂ P, bits"), .bars(r.ps.map { lsRow($0.w, f(bits($0.p), 2), bits($0.p) / 5, $0.seen ? .blue : .yellow) })],
               [F("total =", f(r.h * Double(r.ps.count), 2) + " bits")],
               ("Surprisal: how many bits each token cost.", "Unseen bigrams cost the most.")),
            fr([S(st("mean surprisal H", f(r.h, 3) + " bits"), st("perplexity 2^H", f(r.ppl, 3), .lilac))],
               [F("PPL = 2^\(f(r.h, 3)) =", f(r.ppl, 3), .lilac)],
               ("Perplexity \(f(r.ppl, 2)).", "As uncertain as choosing uniformly among \(f(r.ppl, 1)) words at every step.")),
            fr([.label("perplexity of three test strings"), .bars(tests.enumerated().map { j, t in let p = score(t).ppl; return lsRow(names[j], f(p, 2), p / 14, j == k ? .violet : .slate, pick(j == k)) })],
               [F("lower = less surprised")],
               ("Word order matters to the score.", "Same words, worse order, higher perplexity. Only compare models that share a tokenizer and test set.")),
        ]
    }
}

// MARK: - WER

private struct WerOp { let t: Character; var w = ""; var r = "" }

/// Minimum word edit distance with a backtrace: "o" match, "s" substitution, "d" deletion, "i" insertion.
private func werAlign(_ ref: [String], _ hyp: [String]) -> [WerOp] {
    let n = ref.count, m = hyp.count
    var d = (0...n).map { i in (0...m).map { j in i == 0 ? j : j == 0 ? i : 0 } }
    if n > 0 && m > 0 {
        for i in 1...n { for j in 1...m { d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (ref[i - 1] == hyp[j - 1] ? 0 : 1)) } }
    }
    var ops: [WerOp] = []
    var i = n, j = m
    while i > 0 || j > 0 {
        if i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + (ref[i - 1] == hyp[j - 1] ? 0 : 1) {
            ops.insert(ref[i - 1] == hyp[j - 1] ? WerOp(t: "o", w: hyp[j - 1]) : WerOp(t: "s", w: hyp[j - 1], r: ref[i - 1]), at: 0)
            i -= 1; j -= 1
        } else if i > 0 && d[i][j] == d[i - 1][j] + 1 {
            ops.insert(WerOp(t: "d", r: ref[i - 1]), at: 0)
            i -= 1
        } else {
            ops.insert(WerOp(t: "i", w: hyp[j - 1]), at: 0)
            j -= 1
        }
    }
    return ops
}

private func werNorm(_ s: String) -> String { s.lowercased().replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: "") }

private func werStory() -> LsLab {
    let ref = words("the model did not converge on the second run")
    let hyps = ["model did not converge second run", "the model did converge on the second run", "The model did not converge on the 2nd run."]
    let n = ref.count
    func rate(_ j: Int) -> Double {
        let hh = words(j == 2 ? werNorm(hyps[j]) : hyps[j])
        return Double(werAlign(ref, hh).filter { $0.t != "o" }.count) / Double(n)
    }
    return LsLab(tabs: ["dropped", "“not” gone", "format"], initialTab: 1,
                 legend: [(.green, "Match"), (.red, "Substitution"), (.slate, "Deletion"), (.yellow, "Meaning reversed")]) { k in
        (0..<5).map { s in
            let norm = k == 2 && s >= 3
            let hyp = words(norm ? werNorm(hyps[k]) : hyps[k])
            let ops = werAlign(ref, hyp)
            let subs = ops.filter { $0.t == "s" }.count, dels = ops.filter { $0.t == "d" }.count, ins = ops.filter { $0.t == "i" }.count
            let wer = Double(subs + dels + ins) / Double(n)
            let chips = ops.map { o -> LsTok in
                switch o.t {
                case "o": LsTok(t: o.w, tone: .done)
                case "s": LsTok(t: o.w, tone: .err, sub: "≠ " + o.r)
                case "d": LsTok(t: o.r, tone: .fut, sub: "deleted")
                default: LsTok(t: o.w, tone: .cur, sub: "inserted")
                }
            }
            let head: [LsBlock] = [.label("reference"), C(ref.map { LsTok(t: $0) }), .label("hypothesis" + (norm ? " (normalised)" : "")), C(s == 0 ? hyp.map { LsTok(t: $0) } : chips)]
            switch s {
            case 0:
                return fr(head, [F("reference:", "\(n) words")],
                          ("WER compares a transcript with a reference, word by word.", "It counts the fewest substitutions, deletions and insertions that turn one into the other."))
            case 1:
                return fr(head, [F("edit distance (dynamic programming) =", "\(subs + dels + ins)", .pink)],
                          ("Align with minimum edit distance.", "Green matches, red substitutions, grey deletions."))
            case 2:
                return fr(head + [.bars([lsRow("substitutions", "\(subs)", Double(subs) / 4, .pink), lsRow("deletions", "\(dels)", Double(dels) / 4, .pink), lsRow("insertions", "\(ins)", Double(ins) / 4, .pink)])],
                          [F("WER = (\(subs) + \(dels) + \(ins)) / \(n) =", f(wer, 3), .pink)],
                          ("WER \(f(wer, 3)).", k == 1 ? "One deleted word, a low score — and the meaning is reversed." : k == 0 ? "Three function words gone, meaning intact, and a third of the words counted wrong." : "Case and punctuation count as errors before normalising."))
            case 3:
                return fr(head, [F("WER =", f(wer, 3), .pink)],
                          k == 2 ? ("Normalise first: lowercase, strip punctuation.", "Only “2nd” vs “second” remains: WER \(f(wer, 3)). Normalisation rules change the reported number.")
                          : ("WER weighs every word equally.", k == 1 ? "“not” costs the same as “the”. A metric for transcription accuracy, not for meaning." : "Dropping “the” costs as much as dropping “not”."))
            default:
                return fr(head + [.bars(hyps.indices.map { j in let d = rate(j); return lsRow(["dropped words", "“not” deleted", "format"][j], f(d, 3), d / 0.4, j == 1 ? .yellow : .pink, pick(j == k)) })],
                          [F("lowest WER, reversed meaning:", "“not” deleted", .yellow)],
                          ("The best-scoring hypothesis is the most wrong.", "Report WER alongside a check on meaning-bearing words."))
            }
        }
    }
}

// MARK: - BLEU

struct LsNgram { let m: Int; let raw: Int; let t: Int; let p: Double }

/// Unsmoothed sentence BLEU: clipped 1- to 4-gram precision, geometric mean, brevity penalty.
func lsBleu(_ c: [String], _ r: [String]) -> (ps: [LsNgram], bp: Double, b: Double) {
    func grams(_ t: [String], _ n: Int) -> [String: Int] {
        var o: [String: Int] = [:]
        if t.count >= n { for i in 0...(t.count - n) { o[t[i..<(i + n)].joined(separator: " "), default: 0] += 1 } }
        return o
    }
    let ps = (1...4).map { n -> LsNgram in
        let cg = grams(c, n), rg = grams(r, n)
        var m = 0, raw = 0, t = 0
        for (g, cnt) in cg { t += cnt; raw += rg[g] != nil ? cnt : 0; m += min(cnt, rg[g] ?? 0) }
        return LsNgram(m: m, raw: raw, t: t, p: t > 0 ? Double(m) / Double(t) : 0)
    }
    let bp = c.count > r.count ? 1 : exp(1 - Double(r.count) / Double(c.count))
    let b = ps.contains { $0.p == 0 } ? 0 : bp * exp(ps.reduce(0) { $0 + log($1.p) } / 4)
    return (ps, bp, b)
}

private func bleuStory() -> LsLab {
    let ref = words("the cat is on the mat")
    let cands = ["the the the the the the the the", "the cat sat on the mat", "on the mat the cat is"].map(words)
    let names = ["repetition", "one word off", "reordered"]
    var rc: [String: Int] = [:]
    for w in ref { rc[w, default: 0] += 1 }
    let refCounts = rc
    return LsLab(tabs: names, initialTab: 0,
                 legend: [(.violet, "Credited"), (.blue, "Clipped precision"), (.pink, "Raw")]) { k in
        let c = cands[k], r = lsBleu(c, ref)
        return (0..<5).map { s in
            var used: [String: Int] = [:]
            let chips = C(c.map { w in
                let ok = (used[w] ?? 0) < (refCounts[w] ?? 0)
                if ok { used[w, default: 0] += 1 }
                return LsTok(t: w, tone: s == 0 ? .plain : ok ? .ans : .fut, sub: s >= 1 ? (ok ? "credited" : refCounts[w] != nil ? "over cap" : "no match") : "")
            })
            let head: [LsBlock] = [.label("reference: the cat is on the mat"), chips]
            switch s {
            case 0:
                return fr(head, [F("candidate length", "\(c.count) vs reference \(ref.count)")],
                          ("BLEU scores n-gram overlap with a reference.", "Precision of 1- to 4-grams, combined, with a penalty for being too short."))
            case 1:
                let p = r.ps[0]
                return fr(head + [.bars([lsRow("unclipped", "\(p.raw)/\(p.t)", Double(p.raw) / Double(p.t), .pink), lsRow("clipped", "\(p.m)/\(p.t)", Double(p.m) / Double(p.t), .blue, .strong)])],
                          [F("clip each n-gram at its reference count")],
                          ("Clipping is the whole defence against repetition.", k == 0 ? "“the” appears twice in the reference, so only 2 of 8 count." : "Each word is credited at most as often as the reference uses it."))
            case 2:
                return fr(head + [.bars(r.ps.enumerated().map { i, p in lsRow("p\(i + 1)", "\(p.m)/\(p.t)", p.p, p.p > 0 ? .blue : .red) })],
                          [F("p₁ … p₄ =", r.ps.map { f($0.p, 2) }.joined(separator: ", "))],
                          ("Precision at each n-gram length.", r.ps[3].p == 0 ? "No 4-gram matches: unsmoothed BLEU will be zero." : "Longer n-grams reward correct word order."))
            case 3:
                return fr(head, [F("BP = \(c.count > ref.count ? "1 (not shorter)" : "e^(1 − r/c)") =", f(r.bp, 3))],
                          ("Brevity penalty.", "Precision alone would reward a one-word output; BP punishes candidates shorter than the reference."))
            default:
                return fr(head + [.bars(cands.enumerated().map { j, cc in let b = lsBleu(cc, ref).b; return lsRow(names[j], f(b, 3), b, j == k ? .violet : .slate, pick(j == k)) })],
                          [F("BLEU = BP · (p₁p₂p₃p₄)^¼ =", f(r.b, 3), .lilac)],
                          ("BLEU \(f(r.b, 3)).", k == 2 ? "All six words right, order shuffled: zero. BLEU is a corpus metric and harsh on single sentences." : "One substitution breaks every n-gram that crosses it."))
            }
        }
    }
}

// MARK: - ROUGE-1

private func rougeStory() -> LsLab {
    let ref = words("the council approved a new budget for city parks on monday")
    let cands = [
        "council approved new budget for city parks monday",
        "on monday the city council met for three hours and after a long debate approved a new budget that raises spending on city parks and libraries by ten percent next year",
        "budget",
    ].map(words)
    let names = ["summary", "whole doc", "“budget”"]
    func counts(_ t: [String]) -> [String: Int] { var o: [String: Int] = [:]; for w in t { o[w, default: 0] += 1 }; return o }
    func score(_ c: [String]) -> (m: Int, r: Double, p: Double, f1: Double) {
        let rc = counts(ref)
        let m = counts(c).reduce(0) { $0 + min($1.value, rc[$1.key] ?? 0) }
        let rec = Double(m) / Double(ref.count), prec = Double(m) / Double(c.count)
        return (m, rec, prec, rec + prec > 0 ? 2 * rec * prec / (rec + prec) : 0)
    }
    return LsLab(tabs: names, initialTab: 1,
                 legend: [(.violet, "In the reference"), (.pink, "Recall"), (.blue, "Precision")]) { k in
        let c = cands[k], r = score(c)
        return (0..<5).map { s in
            let head: [LsBlock] = [
                .label("reference summary (11 words)"),
                C(ref.map { LsTok(t: $0, tone: s >= 1 && c.contains($0) ? .ans : .plain) }),
                S(st("candidate", "\(names[k]), \(c.count) words")),
            ]
            switch s {
            case 0:
                return fr(head, [F("ROUGE-1: unigram overlap with the reference")],
                          ("ROUGE was built for summaries.", "Originally recall-first: how much of the reference does the candidate cover?"))
            case 1:
                return fr(head, [F("overlapping words (clipped) =", "\(r.m)", .lilac)],
                          ("Purple: reference words the candidate contains.", k == 1 ? "The whole document contains all of them — without summarising anything." : "Counts are clipped, as in BLEU."))
            case 2:
                return fr(head, [F("recall = \(r.m) / 11 =", f(r.r, 3), .pink)],
                          ("Recall \(f(r.r, 3)).", k == 1 ? "Submit the entire document and recall is perfect. Recall alone can’t be the score." : "Coverage of the reference."))
            case 3:
                return fr(head, [F("precision = \(r.m) / \(c.count) =", f(r.p, 3), .blue)],
                          ("Precision \(f(r.p, 3)).", k == 2 ? "One correct word: perfect precision, almost no recall." : k == 1 ? "Precision exposes the padding." : "Almost every word earns its place."))
            default:
                return fr(head + [.bars(cands.enumerated().map { j, cc in let x = score(cc); return lsRow(names[j], "F1 \(f(x.f1, 3))", x.f1, j == k ? .violet : .slate, pick(j == k)) })],
                          [F("F1 = 2PR / (P + R) =", f(r.f1, 3), .lilac)],
                          ("Report F1, not recall.", "Both exploits — the whole document and one word — collapse under F1; the real summary wins."))
            }
        }
    }
}

// MARK: - METEOR

private func meteorStory() -> LsLab {
    let ref = words("the committee approved the revised budget on friday")
    let hyps = [
        "on friday the panel signed off on the amended budget",
        "budget the friday on approved committee the revised",
        "the committee approved the revised budget on friday",
    ].map(words)
    func met(_ h: [String]) -> (al: [Int], m: Int, ch: Int, p: Double, r: Double, f: Double, pen: Double, score: Double) {
        var used = Array(repeating: false, count: ref.count)
        let al = h.map { w -> Int in
            guard let j = ref.indices.first(where: { !used[$0] && ref[$0] == w }) else { return -1 }
            used[j] = true
            return j
        }
        let m = al.filter { $0 >= 0 }.count
        var ch = 0, prev = -2
        for a in al {
            if a < 0 { prev = -2; continue }
            if a != prev + 1 { ch += 1 }
            prev = a
        }
        let p = Double(m) / Double(h.count), r = Double(m) / Double(ref.count)
        let fm = m > 0 ? 10 * p * r / (r + 9 * p) : 0
        let pen = m > 0 ? 0.5 * pow(Double(ch) / Double(m), 3) : 0
        return (al, m, ch, p, r, fm, pen, fm * (1 - pen))
    }
    return LsLab(tabs: ["paraphrase", "shuffled", "exact"], initialTab: 0,
                 legend: [(.violet, "Aligned"), (.yellow, "Fragmentation"), (.blue, "METEOR")]) { k in
        let h = hyps[k], r = met(h), b = lsBleu(h, ref).b
        let head: [LsBlock] = [
            .label("alignment to: the committee approved the revised budget on friday"),
            C(h.enumerated().map { i, w in r.al[i] >= 0 ? LsTok(t: w, tone: .ans, sub: "→ \(r.al[i])") : LsTok(t: w, tone: .fut) }),
        ]
        return [
            fr(head, [F("aligned words:", "\(r.m) of \(h.count)", .lilac)],
               ("METEOR aligns words one-to-one.", "Exact matches first; the full metric adds stems and WordNet synonyms.")),
            fr(head + [S(st("precision", "\(r.m)/\(h.count) = \(f(r.p, 3))"), st("recall", "\(r.m)/\(ref.count) = \(f(r.r, 3))"))],
               [F("P =", f(r.p, 3)), F("R =", f(r.r, 3))],
               ("Precision and recall over aligned words.", "Unlike BLEU, recall counts.")),
            fr(head, [F("F = 10PR / (R + 9P) =", f(r.f, 4), .lilac)],
               ("Recall weighted 9× over precision.", "Missing content is punished more than extra words.")),
            fr(head, [F("chunks =", "\(r.ch)"), F("penalty = 0.5 · (\(r.ch)/\(r.m))³ =", f(r.pen, 4), .yellow)],
               ("\(r.ch) chunk\(r.ch > 1 ? "s" : ""): contiguous runs in matching order.", k == 1 ? "Every word present, order broken: \(r.ch) chunks for \(r.m) matches, heavy penalty." : "Fewer chunks means better word order.")),
            fr(head + [.bars([lsRow("BLEU", f(b, 4), b, .slate), lsRow("METEOR", f(r.score, 4), r.score, .blue, .strong)])],
               [F("METEOR = F · (1 − penalty) =", f(r.score, 4), .blue)],
               (k == 0 ? "BLEU 0, METEOR \(f(r.score, 2)) on a fair paraphrase." : k == 1 ? "Shuffled words: METEOR’s penalty bites." : "An exact copy scores near 1 on both.",
                "METEOR was built to correlate better with human judgements at the sentence level.")),
        ]
    }
}

// MARK: - MMLU: floors and error bars

private func mmluStory() -> LsLab {
    let models: [(String, Double)] = [("A", 0.712), ("B", 0.698), ("C", 0.655), ("D", 0.310)]
    func cc(_ p: Double) -> Double { (p - 0.25) / 0.75 }
    let sizes = [14042.0, 1000.0, 100.0]
    return LsLab(tabs: ["n 14,042", "n 1,000", "n 100"], initialTab: 2,
                 legend: [(.blue, "Reported"), (.green, "Chance-corrected"), (.red, "Not significant")]) { tab in
        let n = sizes[tab]
        func se(_ p: Double) -> Double { sqrt(p * (1 - p) / n) }
        let diff = 0.712 - 0.698
        let sd = sqrt(pow(se(0.712), 2) + pow(se(0.698), 2))
        let z = diff / sd
        let zInk: LsInk = z > 2 ? .green : .red
        return [
            fr([.label("reported accuracy"), .bars(models.map { lsRow("model \($0.0)", f($0.1, 3), $0.1, .blue) })],
               [F("14,042 four-way questions, 57 subjects")],
               ("MMLU is quoted as one accuracy.", "Before comparing models, that number needs a floor and an error bar.")),
            fr([.label("chance-corrected (acc − 0.25) / 0.75"), .bars(models.map { lsRow("model \($0.0)", f(cc($0.1), 3), max(cc($0.1), 0), .green) })],
               [F("model D: (0.310 − 0.25) / 0.75 =", f(cc(0.31), 3), .green)],
               ("Guessing scores 0.25, not 0.", "Model D’s 31% is only 8% of the way from guessing to perfect.")),
            fr([.label("±2 standard errors, n = \(lsNum(n))"), .bars(models.map { lsRow("model \($0.0)", "\(f($0.1, 3)) ± \(f(2 * se($0.1), 3))", $0.1, .blue) })],
               [F("SE = √(p(1−p)/n) at p 0.7 =", f(se(0.7), 4))],
               ("At n = \(lsNum(n)), the error bar is ±\(f(2 * se(0.7) * 100, 1)) points.", n < 1000 ? "One subject’s ~100 questions can’t rank close models." : "Full MMLU is large enough for a tight interval.")),
            fr([S(st("A − B", "0.014"), st("SE of difference", f(sd, 4)), st("z", f(z, 2), zInk))],
               [F("z = 0.014 / SE =", f(z, 2), zInk)],
               z > 2 ? ("A beats B — significant at this n.", "Only with all 14,042 questions does a 1.4-point gap clear two standard errors.")
               : ("A vs B is a coin flip at this n.", "Leaderboard gaps this small need the full test set, or they are noise.")),
        ]
    }
}
