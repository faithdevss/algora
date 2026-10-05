import SwiftUI

// Port of TransformerStoryFrames.kt: attention, multi-head attention, self- and cross-attention,
// transformers, BERT, GPT, DistilBERT, T5, RoBERTa and Hugging Face tokenizers. One sentence, "the cat
// sat on the mat", with one set of query, key and value vectors (d = 4) runs through every attention
// lab; the tokenizer lab trains real BPE merges on a fifteen-sentence corpus.

let transformerStoryTopicIds: Set<String> = [
    "attention", "multi_head_attention", "self_cross_attention", "transformers", "bert", "gpt", "distilbert", "t5", "roberta", "hf_tokenizers",
]

func transformerLab(_ topicId: String) -> DkLab? {
    switch topicId {
    case "attention": attentionLab()
    case "multi_head_attention": multiHeadLab()
    case "self_cross_attention": selfCrossLab()
    case "transformers": blockLab()
    case "bert": bertLab()
    case "gpt": gptLab()
    case "distilbert": distilLab()
    case "t5": t5Lab()
    case "roberta": robertaLab()
    case "hf_tokenizers": bpeLab()
    default: nil
    }
}

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }
private func pct(_ share: Double, _ d: Int = 0) -> String { n(share * 100, d) + "%" }
private func legend(_ ink: DkInk, _ label: String, _ style: SwatchStyle = .fill) -> DkLegend { DkLegend(ink: ink, style: style, label: label) }

private func stepActions(_ frames: [DkFrame], _ actions: (Int) -> String) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = i == frames.count - 1 ? "Start Over" : actions(i); return f }
}

private func vec(_ v: [Double]) -> String { "[" + v.map { n($0) }.joined(separator: ", ") + "]" }

private func softmax(_ v: [Double]) -> [Double] {
    let m = v.filter { $0.isFinite }.max()!
    let e = v.map { $0.isFinite ? exp($0 - m) : 0 }
    let s = e.reduce(0, +)
    return e.map { $0 / s }
}

private func dot(_ a: [Double], _ b: [Double]) -> Double { a.indices.reduce(0.0) { $0 + a[$1] * b[$1] } }

private func argmax(_ v: [Double]) -> Int { v.indices.max { v[$0] < v[$1] }! }

// MARK: - The shared sentence

private let words = ["the", "cat", "sat", "on", "the", "mat"]
private let dim = 4

private let xs: [[Double]] = [
    [0.90, -0.30, 0.20, 0.10], [1.40, 0.80, -0.50, 0.30], [2.07, -1.19, 0.38, 0.29],
    [0.40, 1.10, 0.90, -0.60], [0.80, -0.20, 0.40, 0.50], [1.20, 0.30, -0.80, -0.40],
]
private let qs: [[Double]] = [
    [1.0, 1.5, 0.5, 0.5], [0.8, 2.0, 0.2, 0.6], [2.0, 1.6, 2.0, 0.4],
    [0.5, 0.2, 0.3, -1.0], [1.0, 1.0, 0.8, 0.2], [1.5, -0.5, 1.0, -0.8],
]
private let ks: [[Double]] = [
    [0.2, 0.1, 0.0, 0.0], [1.5, 0.2, 1.0, 0.3], [0.3, 0.5, 0.2, 0.1],
    [1.0, 1.8, 0.5, 0.6], [-0.6, 0.0, -0.4, 0.2], [0.9, -0.8, 0.3, -0.6],
]
private let vs: [[Double]] = [
    [0.1, 0.2, 0.0, 0.1], [-1.2, 1.9, 1.1, -0.2], [0.3, -0.2, 0.5, 0.1],
    [-0.9, 1.6, 1.8, -0.5], [0.2, 0.1, -0.1, 0.3], [0.8, -0.5, 0.6, 0.9],
]

/// q·k/√d for every query and key, on the dimensions `dims` (all four by default).
private func scores(_ q: [[Double]], _ dims: [Int] = Array(0..<4)) -> [[Double]] {
    q.map { qi in ks.map { kj in dims.reduce(0.0) { $0 + qi[$1] * kj[$1] } / Double(dims.count).squareRoot() } }
}

private let selfScores = scores(qs)
private let selfWeights = selfScores.map { softmax($0) }

private func heatGrid(_ w: [[Double]], _ rows: [String], _ hot: Int?, masked: Bool = false, maxCell: CGFloat = 30) -> DkGrid {
    var cells: [DkCell] = []
    for (i, row) in w.enumerated() {
        for (j, v) in row.enumerated() {
            cells.append(masked && j > i ? DkCell(text: "−∞", tone: .zero) : DkCell(text: n(v), tone: .heat, level: v))
        }
    }
    return DkGrid(title: "", rows: w.count, cols: words.count, cells: cells,
                  boxes: hot.map { [DkBox(r0: $0, c0: 0, r1: $0, c1: words.count - 1)] } ?? [],
                  maxCell: maxCell, rowLabels: rows, colLabels: words, hotRow: hot)
}

// MARK: - Attention

private func attentionLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let qi = 2
        let sc = selfScores[qi], w = selfWeights[qi]
        let top = argmax(w)
        let out = (0..<dim).map { d in w.indices.reduce(0.0) { $0 + w[$1] * vs[$1][d] } }
        func rows(_ showScores: Bool, _ showWeights: Bool) -> DkStage {
            .rows(DkRows(rows: words.enumerated().map { j, t in
                DkRow(title: t, meta: showScores ? n(sc[j]) : "",
                      bars: [DkBar(frac: showWeights ? w[j] : 0, ink: .violet, label: showWeights ? n(w[j]) : "–")], hot: j == qi, inline: true)
            }))
        }
        let header = "query \"sat\" against every key · d = \(dim)"
        let lg = [legend(.yellow, "Query"), legend(.violet, "Weight")]
        let frames = [
            DkFrame(header: header, stage: rows(false, false), legend: Array(lg.prefix(1)),
                    formula: ["q_sat = \(vec(qs[qi]))", "one key per token, one value per token"],
                    headline: "Each word asks a {question}: its query vector.",
                    body: "Every token also offers a key, what it matches, and a value, what it hands over. \"sat\" will compare its query with all six keys."),
            DkFrame(header: header, stage: rows(true, false), legend: Array(lg.prefix(1)),
                    formula: ["score = q·k / √\(dim)", "q_sat·k_on / 2 = {\(n(sc[3]))}, q_sat·k_mat / 2 = \(n(sc[5]))"],
                    headline: "\"on\" scores highest at {\(n(sc[3]))}.",
                    body: "A dot product is large when the vectors point the same way. Dividing by √d keeps scores in a range softmax can handle."),
            DkFrame(header: header, stage: rows(true, true), legend: lg,
                    formula: ["w = softmax(q·k / √\(dim)) · Σw = \(n(w.reduce(0, +)))", "top: \"\(words[top])\" {\(n(w[top]))} of the mix"],
                    headline: "\"sat\" draws {\(pct(w[top]))} of its update from \"\(words[top])\".",
                    body: "Softmax turns unbounded scores into weights that sum to 1. The output is that weighted mix of the six value vectors."),
            DkFrame(header: header, stage: rows(true, true), legend: lg,
                    formula: ["out = Σ w_j · v_j", "= {\(vec(out))}"],
                    headline: "The output is {\(vec(out))}, mostly \"on\" and \"cat\".",
                    body: "Nothing was looked up by position: \"sat\" found its context by content, wherever those words sat in the sentence."),
            DkFrame(header: header, stage: rows(true, true), legend: lg,
                    formula: ["all queries at once: softmax(QKᵀ / √d) · V", "6 × 6 scores, \(6 * 6 * dim) multiplies for QKᵀ"],
                    headline: "Every word does this {in parallel}.",
                    body: "Stack the queries into Q and it's two matrix products. That parallelism, unlike an RNN's step-by-step loop, is why transformers train fast."),
        ]
        return stepActions(frames) { ["Score Keys", "Softmax", "Mix Values", "All Queries"][$0] }
    }
}

// MARK: - Multi-head attention

private func multiHeadLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["h = 1", "h = 2", "h = 4"], initialTab: 1) { tab, _ in
        let h = [1, 2, 4][tab]
        let per = dim / h
        let qi = 2
        let heads = (0..<h).map { k in softmax(scores([qs[qi]], Array((k * per)..<((k + 1) * per)))[0]) }
        let tops = heads.map { argmax($0) }
        let stage = DkStage.tiles(DkTiles(rows: heads.enumerated().map { k, w in
            DkTileRow(tiles: words.enumerated().map { j, t in DkTile(text: t, tone: .heat, sub: n(w[j]), fill: w[j], ring: j == tops[k]) },
                      title: "head \(k + 1)", height: 46)
        }))
        let header = "query \"sat\" · d = \(dim) split into \(h) head\(h > 1 ? "s" : "") of \(per)"
        let lg = [legend(.violet, "Weight"), legend(.yellow, "Head's top key", .ring)]
        var distinct: [String] = []
        for t in tops where !distinct.contains(words[t]) { distinct.append(words[t]) }
        let frames = [
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["each head: its own \(per)-dim slice of q and k", "head weights: softmax(q_h·k_h / √\(per))"],
                    headline: h == 1 ? "One head puts {\(pct(heads[0][tops[0]]))} on \"\(words[tops[0]])\"."
                        : distinct.count > 1 ? "The \(h) heads attend to {different words}: \(distinct.map { "\"\($0)\"" }.joined(separator: " vs "))."
                        : "All \(h) heads agree on {\"\(distinct[0])\"} here.",
                    body: h == 1 ? "A single head must blend every relationship into one weighting. Split d and each slice can specialise."
                        : "Splitting d costs no parameters; it lets each slice learn its own pattern, then concatenates them."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["params = 4·d² at every h · d = 512 → 1.05M", "d per head: h=1 → 512, h=8 → {64}, h=16 → 32"],
                    headline: "Splitting d costs {no} extra parameters.",
                    body: "Q, K, V and the output projection are d×d whatever h is; heads only change how those columns are grouped."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["concat: \(h) × \(per) = \(dim) dims → W_O (\(dim)×\(dim))", "output mixes all heads' findings"],
                    headline: "The heads' outputs are {concatenated} and projected.",
                    body: "In trained models some heads track syntax, some coreference, some the previous token; the output layer learns how to combine them."),
        ]
        return stepActions(frames) { _ in "Next" }
    }
}

// MARK: - Self- vs cross-attention

private let targetWords = ["die", "Katze", "saß"]
private let targetQs: [[Double]] = [[1.6, 0.0, 1.2, 0.4], [1.0, 1.4, 0.6, 0.5], [0.3, 0.4, 0.2, 0.0]]

private func selfCrossLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Self", "Cross", "Both"], initialTab: 2) { tab, _ in
        let cross = scores(targetQs).map { softmax($0) }
        func stage(_ hotSelf: Int?, _ hotCross: Int?) -> DkStage {
            let s = heatGrid(selfWeights, words, hotSelf, maxCell: 34), c = heatGrid(cross, targetWords, hotCross, maxCell: 34)
            return .grids(DkGrids(columns: [tab == 0 ? [s] : tab == 1 ? [c] : [s, c]], weights: [1]))
        }
        let header = tab == 0 ? "self · Q, K, V from \"the cat sat on the mat\"" : tab == 1 ? "cross · Q from German target, K, V from English source" : "self above · cross below"
        let lg = [legend(.violet, "Attention weight"), legend(.yellow, "Highlighted query", .ring)]
        let sat = selfWeights[2], katze = cross[1]
        let kTop = argmax(katze), sTop = argmax(sat)
        let frames = [
            DkFrame(header: header, stage: stage(nil, nil), legend: Array(lg.prefix(1)),
                    formula: ["self: Q, K, V all from one sequence → 6 × 6", "cross: Q from the decoder, K, V from the encoder → 3 × 6"],
                    headline: tab == 0 ? "Self-attention: every word {reads its own sentence}." : tab == 1 ? "Cross-attention: each German word {reads the English}." : "Two maps, {one formula}.",
                    body: "Each row is one query's weights over the six source words, and sums to 1."),
            DkFrame(header: header, stage: stage(2, 1), legend: lg,
                    formula: tab == 0 ? ["\"sat\" row: top \"\(words[sTop])\" {\(n(sat[sTop]))}", "rows: 6 queries × 6 keys"]
                        : tab == 1 ? ["\"Katze\" row: top \"\(words[kTop])\" {\(n(katze[kTop]))}", "rows: 3 target words × 6 source words"]
                        : ["self \"sat\" → \"\(words[sTop])\" \(n(sat[sTop]))", "cross \"Katze\" → \"\(words[kTop])\" {\(n(katze[kTop]))}"],
                    headline: tab == 0 ? "\"sat\" puts {\(n(sat[sTop]))} on \"\(words[sTop])\"."
                        : tab == 1 ? "\"Katze\" puts {\(n(katze[kTop]))} on \"\(words[kTop])\"."
                        : "Same softmax(QKᵀ/√d)·V; only {where Q comes from} changes.",
                    body: tab == 2 ? "Self-attention is 6×6. Cross-attention is 3×6: each target word reads the source, e.g. \"Katze\" puts \(n(katze[kTop])) on \"\(words[kTop])\"."
                        : "The weights here come from hand-set vectors, not a trained model, so the pairs they favour are only illustrative."),
            DkFrame(header: header, stage: stage(nil, nil), legend: Array(lg.prefix(1)),
                    formula: ["cost: self n², cross n_target × n_source", "6² = 36 · 3 × 6 = {18}"],
                    headline: "Cross-attention is how a decoder {consults} the source.",
                    body: "An encoder-decoder transformer uses both in every decoder block: self-attention over what it has written, then cross-attention into the encoder."),
        ]
        return stepActions(frames) { _ in "Next" }
    }
}

// MARK: - Transformers: one block

private func blockLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let qi = 2
        let w = selfWeights[qi], x = xs[qi]
        let attn = (0..<dim).map { d in w.indices.reduce(0.0) { $0 + w[$1] * vs[$1][d] } }
        let res = x.indices.map { x[$0] + attn[$0] }
        let mean = res.reduce(0, +) / Double(dim)
        let sd = (res.reduce(0.0) { $0 + ($1 - mean) * ($1 - mean) } / Double(dim) + 1e-5).squareRoot()
        let norm = res.map { ($0 - mean) / sd }
        var s: Int64 = 77
        func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 - 0.5 }
        var w1: [[Double]] = []
        for _ in 0..<16 { var r: [Double] = []; for _ in 0..<dim { r.append(u()) }; w1.append(r) }
        var w2: [[Double]] = []
        for _ in 0..<dim { var r: [Double] = []; for _ in 0..<16 { r.append(u() * 0.6) }; w2.append(r) }
        let hidden = w1.map { max(0, dot($0, norm)) }
        let ffn = w2.map { dot($0, hidden) }
        let out = norm.indices.map { norm[$0] + ffn[$0] }
        let titles: [(String, String)] = [
            ("x · embedding + position", "6 × 4"), ("multi-head attention", "mixes tokens"), ("x + Attn(x) · residual", "add"),
            ("layer norm", "per token"), ("feed-forward 4 → 16 → 4", "per token"), ("x + FFN(x) · residual", "add"),
        ]
        func stage(_ current: Int) -> DkStage {
            .pipeline(DkPipeline(items: titles.enumerated().map { i, tm in DkPipeItem(title: tm.0, meta: tm.1, tone: i < current ? .done : i == current ? .current : .next) }))
        }
        let header = "one block, applied to \"sat\" · d = \(dim)"
        let lg = [legend(.green, "Done"), legend(.yellow, "Current", .ring), legend(.slate, "Next")]
        let frames = [
            DkFrame(header: header, stage: stage(0), legend: lg, formula: ["x_sat = embedding(\"sat\") + position(3)", "= {\(vec(x))}"],
                    headline: "\"sat\" enters as {4 numbers}: meaning plus position.",
                    body: "Attention ignores order, so a position vector is added in; the same word at another position gets a different x."),
            DkFrame(header: header, stage: stage(1), legend: lg, formula: ["attn = Σ w_j · v_j over all 6 tokens", "= {\(vec(attn))}"],
                    headline: "Attention gathers {context} from the other words.",
                    body: "This is the only step where tokens exchange information. Everything after it works on each token alone."),
            DkFrame(header: header, stage: stage(2), legend: lg, formula: ["x_sat = \(vec(x))", "+ attn = \(vec(attn))", "= {\(vec(res))}"],
                    headline: "Attention's output is {added} to the token, not swapped in.",
                    body: "The residual keeps \"sat\" itself in the stream while mixing in context. Stack this block N times and that is the whole transformer."),
            DkFrame(header: header, stage: stage(3), legend: lg, formula: ["mean \(n(mean)), std \(n(sd))", "→ {\(vec(norm))}"],
                    headline: "Layer norm rescales the token to {mean 0, std 1}.",
                    body: "Without it, values drift as blocks stack. A learned scale and shift (left out here) let the model undo it where useful."),
            DkFrame(header: header, stage: stage(5), legend: [lg[0], lg[2]], formula: ["FFN: ReLU(W1·x) then W2 · 4 → 16 → 4", "x + FFN(x) = {\(vec(out))}"],
                    headline: "A per-token MLP and a second residual {finish} the block.",
                    body: "Attention mixes tokens; the feed-forward layer, two thirds of a block's weights, transforms each one. GPT-3 stacks 96 of these blocks."),
        ]
        return stepActions(frames) { _ in "Next Stage" }
    }
}

// MARK: - BERT

private func bertLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let masked = 3
        let qMask = [1.2, -0.6, 0.8, -0.9]
        let keys = ks.enumerated().map { $0.offset == masked ? [Double](repeating: 0, count: dim) : $0.element }
        let w = softmax(keys.map { dot(qMask, $0) / Double(dim).squareRoot() })
        let right = w.indices.filter { $0 > masked }.reduce(0.0) { $0 + w[$1] }
        func stage(_ mask: Bool, _ weights: Bool) -> DkStage {
            .tiles(DkTiles(rows: [DkTileRow(tiles: words.enumerated().map { j, t in
                let under: [DkInk?] = [weights && j != masked ? (j < masked ? .blue : .green) : nil]
                if j == masked && mask { return DkTile(text: "[MASK]", tone: .current, sub: weights ? "?" : nil, under: under) }
                if weights { return DkTile(text: t, tone: .heat, sub: n(w[j]), fill: w[j], under: under) }
                return DkTile(text: t, tone: .plain, under: under)
            }, height: 50)]))
        }
        let sel = Int((0.15 * 512).rounded())
        let m80 = Int((Double(sel) * 0.8).rounded()), m10 = Int((Double(sel) * 0.1).rounded())
        let preds: [(String, Double)] = [("on", 0.62), ("in", 0.18), ("at", 0.09), ("under", 0.06), ("by", 0.03)]
        let header = "\"on\" replaced by [MASK] · what it attends to"
        let lg = [legend(.blue, "Left context"), legend(.green, "Right context"), legend(.yellow, "Masked", .ring)]
        let frames = [
            DkFrame(header: "the sentence", stage: stage(false, false), legend: [], formula: ["6 tokens, no labels needed", "the text is its own supervision"],
                    headline: "BERT learns from {plain text}, no labels.",
                    body: "Hide a word and ask the model to fill it in: every sentence on the web becomes a training example."),
            DkFrame(header: header, stage: stage(true, false), legend: [lg[2]], formula: ["hide \"on\" → [MASK]", "target: predict \"on\" at position 4"],
                    headline: "One word is {hidden} behind [MASK].",
                    body: "The model sees the rest of the sentence, both sides of the gap."),
            DkFrame(header: header, stage: stage(true, true), legend: lg,
                    formula: ["select 15% of 512 = \(sel) positions", "of those: 80% [MASK] · 10% random · 10% kept → {\(m80) / \(m10) / \(m10)}"],
                    headline: "[MASK] reads both sides: {\(pct(right))} of its weight is to the right.",
                    body: "No causal mask, so it can use \"the mat\" to guess \"on\". The price: BERT can fill blanks but not continue text."),
            DkFrame(header: header,
                    stage: .rows(DkRows(rows: preds.enumerated().map { i, p in
                        DkRow(title: p.0, meta: "", bars: [DkBar(frac: p.1 / preds[0].1, ink: .violet, label: n(p.1))], inline: true, dim: i > 0)
                    })),
                    legend: [legend(.violet, "P(word | context)")], formula: ["softmax over a 30,522-word vocabulary", "loss = −log P(\"on\") = {\(n(-log(0.62)))}"],
                    headline: "The head predicts {\"on\"} with 0.62 (illustrative).",
                    body: "Only the masked positions contribute to the loss, about 15% of tokens, which is why BERT needs a lot of text."),
            DkFrame(header: header, stage: stage(true, true), legend: lg,
                    formula: ["80% → [MASK], 10% → random word, 10% → unchanged", "[MASK] never appears when fine-tuning"],
                    headline: "Not every chosen word becomes {[MASK]}.",
                    body: "Swapping some for random or unchanged words stops the model relying on seeing [MASK], which it never will after pre-training."),
            DkFrame(header: header, stage: stage(true, true), legend: lg,
                    formula: ["BERT-base: 12 layers, 768 dims, 110M params", "fine-tune: add one small head per task"],
                    headline: "One pre-trained encoder, {many tasks}.",
                    body: "Classification, NER and question answering each add a tiny output layer on top and fine-tune the whole stack for an epoch or two."),
            DkFrame(header: header, stage: stage(true, true), legend: lg,
                    formula: ["bidirectional: sees the future → can't generate", "GPT: causal mask → can generate"],
                    headline: "BERT {understands}; it doesn't write.",
                    body: "Reading both directions is perfect for filling and classifying, and useless for producing text one token at a time. That's GPT's job."),
        ]
        return stepActions(frames) { ["Mask a Word", "Attend", "Predict", "Masking Recipe", "Fine-tune", "Compare GPT"][$0] }
    }
}

// MARK: - GPT: the causal mask

private func gptLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let causal = selfScores.enumerated().map { i, row in softmax(row.enumerated().map { $0.offset > i ? -Double.infinity : $0.element }) }
        let maskedCount = (0..<6).reduce(0) { $0 + 5 - $1 }
        let header = "attention weights with the causal mask"
        let lg = [legend(.violet, "Weight"), legend(.slate, "Masked to −∞"), legend(.yellow, "Query row", .ring)]
        let frames = (0..<6).map { i -> DkFrame in
            let row = causal[i]
            let top = (0...i).max { row[$0] < row[$1] }!
            return DkFrame(
                header: header, stage: .grids(DkGrids(columns: [[heatGrid(causal, words, i, masked: true, maxCell: 36)]], weights: [1])), legend: lg,
                formula: ["score(i, j) = −∞ for j > i → e^−∞ = 0", "\"\(words[i])\" row: \((0...i).map { n(row[$0]) }.joined(separator: " + ")) = {\(n(row.reduce(0, +)))}"],
                headline: i == 0 ? "The first token can only attend to {itself}."
                    : "\"\(words[i])\" can only use {\(words.prefix(i + 1).joined(separator: ", "))}; \(pct(row[top])) goes to \"\(words[top])\".",
                body: i == 0 ? "Every later position is masked, so its whole weight, 1.00, stays home."
                    : i == 5 ? "The last row sees everything. Training predicts all six next tokens in one pass: \(maskedCount) of 36 entries are masked."
                    : "Same block as BERT plus the triangle mask, so each position predicts the next token without seeing it. \(maskedCount) of 36 entries are masked.")
        }
        return stepActions(frames) { _ in "Next Row" }
    }
}

// MARK: - DistilBERT: soft targets

private let distilLabels = ["a", "b", "c", "d", "e", "f", "EOS"]
private let teacher: [Double] = {
    let p = [0.12, 0.23, 0.22, 0.09, 0.16, 0.18, 0.001]
    let s = p.reduce(0, +)
    return p.map { $0 / s }
}()

private func distilLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["T = 1", "T = 2", "T = 4"], initialTab: 1) { tab, _ in
        let temp = [1.0, 2.0, 4.0][tab]
        let raw = teacher.map { pow($0, 1 / temp) }
        let rs = raw.reduce(0, +)
        let soft = raw.map { $0 / rs }
        func bits(_ p: [Double]) -> Double { -p.filter { $0 > 0 }.reduce(0.0) { $0 + $1 * log($1) / log(2.0) } }
        let hard = argmax(teacher)
        let tT = n(temp, 0)
        let stage = DkStage.hist(DkHist(rows: [DkHistRow(label: "T = 1", values: teacher, ink: .blue, hot: hard),
                                               DkHistRow(label: "T = \(tT) · what the student trains on", values: soft, ink: .green, hot: hard)],
                                        labels: distilLabels))
        let header = "teacher distribution at its least certain step"
        let lg = [legend(.blue, "Teacher, T = 1"), legend(.green, "Softened, T = \(tT)"), legend(.yellow, "Hard label")]
        let frames = [
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["p_i = softmax(z_i / T)", "top: \"\(distilLabels[hard])\" {\(n(teacher[hard]))} · runner-up \"c\" \(n(teacher[2]))"],
                    headline: "The teacher's top guess gets only {\(pct(teacher[hard]))}.",
                    body: "The rest of the distribution says which wrong answers are nearly right. That is knowledge a one-hot label never carries."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["hard label keeps \(pct(teacher[hard])) · entropy \(n(bits(teacher))) → {\(n(bits(soft))) bits} at T = \(tT)", "student: 6 layers, 66M vs 12, 110M · ~97% of the score"],
                    headline: "A one-hot label would throw away {\(pct(1 - teacher[hard]))} of what the teacher knows.",
                    body: temp == 1 ? "At T = 1 the student copies the teacher's own distribution; raise T to spread it further."
                        : "\"b\" and \"c\" are nearly tied. Softening with T = \(tT) makes that visible, and the student learns it, at 40% fewer parameters."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["loss = α·T²·KL(teacher_T ‖ student_T) + (1 − α)·CE", "DistilBERT: 40% smaller, 60% faster"],
                    headline: "DistilBERT keeps {~97%} of BERT's score.",
                    body: "Half the layers, initialised from the teacher's, trained on its softened outputs. Higher T gives smoother targets but weaker signal on the top class."),
        ]
        return stepActions(frames) { _ in "Next" }
    }
}

// MARK: - T5: text to text

private let t5Rows: [(String, String, String)] = [
    ("translate English to German:", "that is good", "Das ist gut."),
    ("cola sentence:", "the cat sat on mat", "acceptable"),
    ("stsb sentence1: … sentence2: …", "(pair)", "3.8"),
    ("summarize:", "(article)", "(summary)"),
]

private func t5Lab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Translate", "CoLA", "STS-B", "Summ."]) { tab, _ in
        let stage = DkStage.tiles(DkTiles(rows: t5Rows.enumerated().map { i, r in
            DkTileRow(tiles: [DkTile(text: r.0, tone: .plain, ring: i == tab), DkTile(text: r.1, tone: .plain), DkTile(text: r.2, tone: .hot)], height: 54)
        }, footers: ["task prefix", "input", "target"]))
        let (p, x, y) = t5Rows[tab]
        func wc(_ s: String) -> Int { s.split(separator: " ").filter { $0 != "…" }.count }
        let header = "every task is input text → output text"
        let lg = [legend(.yellow, "Current prefix", .ring), legend(.violet, "Decoder output")]
        let frames = [
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["encoder in: \(wc(p) + wc(x)) words · decoder out: \(wc(y)) word\(wc(y) > 1 ? "s" : "")", "loss = cross-entropy on target tokens, {same for all 4}"],
                    headline: tab == 0 ? "Translation is just {text in, text out}."
                        : tab == 1 ? "A grammar judgement comes out as the word {\"acceptable\"}."
                        : tab == 2 ? "Even a similarity score is emitted as the text {\"3.8\"}."
                        : "A summary is the {same shape}: text in, text out.",
                    body: "The prefix tells one encoder-decoder which task it is doing, so there is one model, one loss and one decoding path."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["pre-training: span corruption on C4 (750 GB)", "\"the <X> sat on <Y>\" → \"<X> cat <Y> the mat\""],
                    headline: "T5 pre-trains by filling {masked spans}.",
                    body: "Like BERT's masking, but whole spans, and the answer is generated as text, so pre-training and fine-tuning use the same decoder."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["T5-base 220M · T5-11B: same recipe, more of it", "new task = new prefix, no new head"],
                    headline: "Adding a task needs {no new layer}.",
                    body: "Classification heads, span pointers and regressors all become text. The model just learns another kind of answer."),
        ]
        return stepActions(frames) { _ in "Next" }
    }
}

// MARK: - RoBERTa: dynamic masking

private func robertaLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let len = 12, epochs = 4
        let stat: [Int] = [3, 5]
        var s: Int64 = 2019
        func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
        var dynamic: [[Int]] = []
        for _ in 0..<epochs {
            var picks: [Int] = []
            while picks.count < 2 { let p = Int(u() * Double(len)); if !picks.contains(p) { picks.append(p) } }
            dynamic.append(picks)
        }
        func grid(_ masks: [[Int]], _ shown: Int) -> DkStage {
            var cells: [DkCell] = []
            for e in 0..<epochs { for c in 0..<len { cells.append(e < shown && masks[e].contains(c) ? DkCell(text: "", tone: .hot) : DkCell(text: "", tone: .empty)) } }
            return .grids(DkGrids(columns: [[DkGrid(title: "", rows: epochs, cols: len, cells: cells, maxCell: 24, rowLabels: (1...epochs).map { "e\($0)" })]], weights: [1]))
        }
        func covered(_ k: Int) -> Int { Set(dynamic.prefix(k).flatMap { $0 }).count }
        let header = "same sentence, \(epochs) epochs · 15% masked (2 of \(len))"
        let lg = [legend(.yellow, "Masked this epoch"), legend(.slate, "Visible")]
        let staticLine = "static (BERT): \(stat.count) positions ever masked"
        let frames = [
            DkFrame(header: header, stage: grid(Array(repeating: stat, count: epochs), epochs), legend: lg,
                    formula: [staticLine, "masks chosen once, when the data was prepared"],
                    headline: "BERT masks the same {\(stat.count) positions} every epoch.",
                    body: "The masking was done once in preprocessing, so the model sees the identical puzzle on every pass."),
            DkFrame(header: header, stage: grid(dynamic, 2), legend: lg,
                    formula: [staticLine, "dynamic: {\(covered(2)) of \(len)} positions after 2 epochs"],
                    headline: "RoBERTa draws {fresh masks} every epoch.",
                    body: "Masking happens as each batch is built, so the same sentence becomes a new exercise each time."),
            DkFrame(header: header, stage: grid(dynamic, 3), legend: lg,
                    formula: [staticLine, "dynamic: {\(covered(3)) of \(len)} positions after 3 epochs"],
                    headline: "Three epochs in, {\(covered(3)) positions} have been predicted.",
                    body: "Each is a different fill-in-the-blank on the same words."),
            DkFrame(header: header, stage: grid(dynamic, 4), legend: lg,
                    formula: [staticLine, "dynamic: {\(covered(4)) of \(len)} positions after \(epochs) epochs", "also: no NSP · batch 256 → 8K · 16 → 160 GB"],
                    headline: "Over \(epochs) epochs the model learns to predict {\(covered(4)) positions}, not \(stat.count).",
                    body: "RoBERTa kept BERT's architecture and changed only the training recipe. Fresh masks are the change you can see."),
            DkFrame(header: header, stage: grid(dynamic, 4), legend: lg,
                    formula: ["dropped next-sentence prediction", "batch 256 → 8K · data 16 → 160 GB · trained longer"],
                    headline: "The rest of the recipe: {no NSP}, bigger batches, 10× the data.",
                    body: "Removing next-sentence prediction didn't hurt; more data and longer training did most of the work."),
            DkFrame(header: header, stage: grid(dynamic, 4), legend: lg,
                    formula: ["same 125M-parameter architecture as BERT-base", "GLUE: 88.5 (RoBERTa) vs 82.1 (BERT-large)"],
                    headline: "Same model, better training: RoBERTa {beat} every BERT result.",
                    body: "BERT was undertrained. The lesson carried into every later model: the recipe matters as much as the architecture."),
        ]
        return stepActions(frames) { _ in "Next Epoch" }
    }
}

// MARK: - Hugging Face tokenizers: BPE

private let bpeCorpus = [
    "the gardener watched the birds", "the gardener planted the small tree", "a small bird sat on the fence",
    "the birds watched the gardener", "the tallest tree in the garden", "the gardener watered the garden",
    "a bird watched the small garden", "the small bird sang", "the gardener smiled at the bird",
    "the best garden in the town", "the birds sang in the tree", "the gardener watched the sky",
    "the smaller bird flew to the tree", "the gardener and the birds", "the gardener rested in the garden",
]
private let endMark = "·"
private let bpeMergeCounts = [0, 5, 10, 20, 40, 80]

private func merge(_ w: [String], _ p: (String, String)) -> [String] {
    var out: [String] = []
    var k = 0
    while k < w.count {
        if k < w.count - 1 && w[k] == p.0 && w[k + 1] == p.1 { out.append(w[k] + w[k + 1]); k += 2 } else { out.append(w[k]); k += 1 }
    }
    return out
}

private let bpeFull: (merges: [(String, String)], counts: [Int]) = {
    var order: [String] = []
    var freq: [String: Int] = [:]
    for line in bpeCorpus { for w in line.split(separator: " ").map(String.init) { if freq[w] == nil { order.append(w) }; freq[w, default: 0] += 1 } }
    var words = order.map { $0.map { String($0) } + [endMark] }
    let counts = order.map { freq[$0]! }
    var merges: [(String, String)] = []
    var mergeCounts: [Int] = []
    for _ in 0..<bpeMergeCounts.last! {
        var pairs: [String: (String, String, Int)] = [:]
        for (i, w) in words.enumerated() where w.count > 1 {
            for k in 0..<(w.count - 1) {
                let key = w[k] + "\u{1}" + w[k + 1]
                pairs[key] = (w[k], w[k + 1], (pairs[key]?.2 ?? 0) + counts[i])
            }
        }
        // Highest count, then the lexicographically first pair, the same tie-break as Android.
        guard let best = pairs.values.sorted(by: { a, b in
            a.2 != b.2 ? a.2 > b.2 : a.0 != b.0 ? a.0 < b.0 : a.1 < b.1
        }).first else { break }
        merges.append((best.0, best.1))
        mergeCounts.append(best.2)
        words = words.map { merge($0, (best.0, best.1)) }
    }
    return (merges, mergeCounts)
}()

private func bpeTokenize(_ word: String, _ merges: [(String, String)]) -> [String] {
    merges.reduce(word.map { String($0) } + [endMark]) { merge($0, $1) }
}

private func bpeLab() -> DkLab {
    DkLab(control: .stepperOnly, stepper: DkStepper(caption: "merges", values: bpeMergeCounts.map(Double.init), initial: bpeMergeCounts.firstIndex(of: 40)!) { n($0, 0) }) { _, p in
        let m = bpeMergeCounts[p]
        let merges = Array(bpeFull.merges.prefix(m))
        let sentence = "the gardener watched the smallest bird".split(separator: " ").map(String.init)
        let tokens = sentence.map { bpeTokenize($0, merges) }
        let flat = tokens.flatMap { $0 }
        let seen = Set(bpeCorpus.flatMap { $0.split(separator: " ").map(String.init) })
        let unseen = sentence.first { !seen.contains($0) }!
        let unseenPieces = tokens[sentence.firstIndex(of: unseen)!]
        func tone(_ piece: String) -> DkTokTone {
            if piece == endMark { return .plain }
            return (piece.hasSuffix(endMark) ? String(piece.dropLast()) : piece).count >= 3 ? .whole : .part
        }
        let firstMerges = bpeFull.merges.prefix(5).enumerated().map { "\($0.element.0)+\($0.element.1) (\(bpeFull.counts[$0.offset]))" }.joined(separator: " ")
        let stage = DkStage.tokens(DkTokens(tokens: flat.map { ($0, tone($0)) }, notes: m > 0 ? ["first merges:", firstMerges] : ["no merges yet: every character is a token"]))
        let header = "\"\(sentence.joined(separator: " "))\" · merges learned from \(bpeCorpus.count) sentences"
        let lg = [legend(.violet, "Whole learned piece"), legend(.yellow, "Short fragment"), legend(.slate, "· = word end")]
        let perWord = Double(flat.count) / Double(sentence.count)
        let baseSymbols = Set(bpeCorpus.joined().replacingOccurrences(of: " ", with: "")).count + 1
        return [
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["tokens: {\(flat.count)} for \(sentence.count) words = \(n(perWord)) per word · unknown 0", "vocabulary: \(baseSymbols + m) symbols after \(m) merges"],
                    headline: m == 0 ? "With no merges, the sentence is {\(flat.count) characters}." : "\(m) merges cut the sentence to {\(flat.count) tokens}.",
                    body: "BPE starts from single characters, so any text can be written; each merge adds one new symbol to the vocabulary."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["tokens: \(flat.count) for \(sentence.count) words = \(n(perWord)) per word · unknown 0", "\"\(unseen)\" → \(unseenPieces.joined(separator: " | "))"],
                    headline: "An unseen word is still covered: \"\(unseen)\" splits into {\(unseenPieces.count) pieces}.",
                    body: "BPE repeatedly merges the most frequent adjacent pair. Common words become one token; rare ones fall back to fragments, so nothing is unknown."),
            DkFrame(header: header, stage: stage, legend: lg,
                    formula: ["more merges → bigger vocabulary, shorter sequences", "GPT-2: 50,257 tokens · BERT WordPiece: 30,522"],
                    headline: "Vocabulary size is a {trade-off}.",
                    body: "Fewer tokens per word means shorter sequences and cheaper attention, but a bigger embedding table and rarer pieces to learn."),
        ]
    }
}
