import Foundation

// Port of NlpSyntaxFrames.kt: GloVe, FastText, ELMo, tagging, NER, chunking, both parsers, coreference,
// lexicon sentiment, and the LLM, BART, XLNet and GPT-3/4 labs. GloVe is trained here, the parser runs the
// arc-standard oracle, the chunker matches its rule over the tags; vectors and logits that stand in for a
// trained network say so in their caption.

let nlpSyntaxTopicIds: Set<String> = [
    "glove", "fasttext", "elmo", "pos_tagging", "ner", "chunking", "dependency_parsing", "constituency_parsing",
    "coreference", "sentiment_lexicon", "llms", "bart", "xlnet", "gpt3_gpt4",
]

func nlpSyntaxLab(_ topicId: String) -> [NbFrame]? {
    switch topicId {
    case "glove": gloveFrames()
    case "fasttext": fastTextFrames()
    case "elmo": elmoFrames()
    case "pos_tagging": posFrames()
    case "ner": nerFrames()
    case "chunking": chunkFrames()
    case "dependency_parsing": dependencyFrames()
    case "constituency_parsing": constituencyFrames()
    case "coreference": corefFrames()
    case "sentiment_lexicon": sentimentFrames()
    case "llms": llmFrames()
    case "bart": bartFrames()
    case "xlnet": xlnetFrames()
    case "gpt3_gpt4": gptFrames()
    default: nil
    }
}

private func f2(_ v: Double) -> String { nbF(v, 2) }

private func frame(_ blocks: [NbBlock], _ legend: [NbLegend], _ headline: String, _ body: String, chips: [DkChip] = [], fx: [String] = []) -> NbFrame {
    NbFrame(blocks: blocks, legend: legend, headline: headline, body: body, chips: chips, fx: fx)
}

private func table(_ headers: [String], _ weights: [CGFloat], _ rows: [NbRow], aligns: [Int]? = nil) -> NbBlock {
    .table(headers: headers, weights: weights, rows: rows, aligns: aligns)
}

private func words(_ s: String) -> [String] { s.split(separator: " ").map(String.init) }

private struct SynRng {
    var s: Int64
    init(_ seed: Int64) { s = seed }
    mutating func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
}

private func cosA(_ a: [Double], _ b: [Double]) -> Double {
    a.indices.reduce(0.0) { $0 + a[$1] * b[$1] } / (a.reduce(0.0) { $0 + $1 * $1 }.squareRoot() * b.reduce(0.0) { $0 + $1 * $1 }.squareRoot())
}

// MARK: - GloVe

private let gloveCorpus = ["the king rules the land", "the queen rules the land", "a man rules", "a woman rules"]
private let gloveVocab = ["the", "king", "queen", "rules", "land", "a", "man", "woman"]

private struct GloveRun { let x: [[Double]]; let losses: [Double]; let w: [[Double]]; let nonZero: Int; let tokens: Int }

private func trainGlove() -> GloveRun {
    let v = gloveVocab.count
    var x = [[Double]](repeating: [Double](repeating: 0, count: v), count: v)
    var tokens = 0
    for s in gloveCorpus {
        let ws = words(s).map { gloveVocab.firstIndex(of: $0)! }
        tokens += ws.count
        for i in ws.indices {
            for j in ws.indices {
                let d = abs(i - j)
                if d >= 1 && d <= 2 { x[ws[i]][ws[j]] += 1.0 / Double(d) }
            }
        }
    }
    var pairs: [(Int, Int)] = []
    for i in 0..<v { for j in 0..<v where x[i][j] > 0 { pairs.append((i, j)) } }
    let dim = 4
    var r = SynRng(11)
    var w: [[Double]] = [], c: [[Double]] = []
    for _ in 0..<v { var row: [Double] = []; for _ in 0..<dim { row.append((r.u() - 0.5) * 0.5) }; w.append(row) }
    for _ in 0..<v { var row: [Double] = []; for _ in 0..<dim { row.append((r.u() - 0.5) * 0.5) }; c.append(row) }
    var bw = [Double](repeating: 0, count: v), bc = [Double](repeating: 0, count: v)
    let xMax = 3.0
    func weight(_ xx: Double) -> Double { min(1.0, pow(xx / xMax, 0.75)) }
    var losses: [Double] = []
    let rate = 0.05
    for epoch in 0...400 {
        var loss = 0.0
        for (i, j) in pairs {
            let diff = (0..<dim).reduce(0.0) { $0 + w[i][$1] * c[j][$1] } + bw[i] + bc[j] - log(x[i][j])
            let f = weight(x[i][j])
            loss += f * diff * diff
            if epoch < 400 {
                let g = f * diff
                for k in 0..<dim {
                    let wi = w[i][k]
                    w[i][k] -= rate * g * c[j][k]
                    c[j][k] -= rate * g * wi
                }
                bw[i] -= rate * g
                bc[j] -= rate * g
            }
        }
        losses.append(loss / Double(pairs.count))
    }
    let emb = (0..<v).map { i in (0..<dim).map { w[i][$0] + c[i][$0] } }
    return GloveRun(x: x, losses: losses, w: emb, nonZero: pairs.count, tokens: tokens)
}

private func gloveFrames() -> [NbFrame] {
    let run = trainGlove()
    let v = gloveVocab.count
    let top = run.x.flatMap { $0 }.max()!
    func matrix(_ hot: (Int, Int)? = nil) -> NbBlock {
        .grid(NbGridBlock(cols: gloveVocab.map { String($0.prefix(5)) }, rows: gloveVocab, cells: run.x.enumerated().map { i, row in row.enumerated().map { j, xx in
            NbGCell(text: xx == 0 ? "" : (xx >= 1 ? f2(xx) : nbDot2(xx)), level: xx == 0 ? 0 : 0.25 + 0.7 * xx / top, ink: .sky, ring: hot.map { $0.0 == i && $0.1 == j } ?? false)
        } }, cellHeight: 18))
    }
    func lossPlot() -> NbBlock {
        .plot(NbPlotBlock(xr: (0, 400), yr: (-5, 0), height: 150,
                          lines: [NbLine(pts: run.losses.enumerated().map { NbP(Double($0), min(max(log10(max($1, 1e-5)), -5), 0)) }, ink: .sky, width: 2)],
                          xTicks: [(0, "0"), (100, "100"), (200, "200"), (300, "300"), (400, "400")],
                          yTicks: [(0, "1e0"), (-1, "1e−1"), (-2, "1e−2"), (-3, "1e−3"), (-4, "1e−4"), (-5, "1e−5")], xLabel: "epoch"))
    }
    let k = gloveVocab.firstIndex(of: "king")!, q = gloveVocab.firstIndex(of: "queen")!
    let sims = gloveVocab.indices.filter { $0 != k }.map { (gloveVocab[$0], cosA(run.w[k], run.w[$0])) }.sortedByDescending { $0.1 }
    let head = NbBlock.caption("co-occurrence X · \(v) words, window 2, 1/distance")
    let legend = [nbLegend(.sky, "Co-occurrence weight")]
    let rules = gloveVocab.firstIndex(of: "rules")!
    let kq = run.x[k][rules]
    return [
        frame([.caption("corpus · \(run.tokens) tokens"), .toks(gloveCorpus.map { nbTok($0) })], [],
              "GloVe starts from {global counts}, not a sliding model.",
              "Word2vec learns one window at a time. GloVe first counts how often every pair of words appears near each other across the whole corpus, then fits vectors to those counts."),
        frame([head, matrix((k, rules))], legend, "Count co-occurrences, {weighted by distance}.",
              "A neighbour one word away adds 1, two words away adds ½. X(king, rules) = \(f2(kq)): \"king\" is followed by \"rules\" once.",
              fx: ["X(king, rules) = 1/1 = {\(f2(kq))}"]),
        frame([head, matrix()], legend, "Fit vectors so {w_i · w̃_j ≈ log X_ij}.",
              "Every non-zero cell becomes one regression target. The weight f(X) caps frequent pairs so \"the\" doesn't dominate, and zero cells are skipped entirely.",
              fx: ["J = Σ f(X_ij)·(w_i·w̃_j + b_i + b̃_j − log X_ij)²", "f(x) = min(1, (x / 3)^0.75)"]),
        frame([head, matrix(), .caption("weighted least-squares loss, log scale"), lossPlot()], legend + [nbLegend(.sky, "Mean loss", .line)],
              "Fit by SGD over the \(run.nonZero) non-zero entries.",
              "400 epochs, no sliding window, no sampling: the matrix is the whole training set. Loss falls from \(lsSci(run.losses.first!)) to \(lsSci(run.losses.last!))."),
        frame([.caption("cosine similarity to \"king\" · 4-d vectors w + w̃"), .bars(sims.map { NbBar(label: $0.0, value: f2($0.1), frac: max($0.1, 0), ink: $0.0 == "queen" ? .green : .sky) }, labelWidth: 64)],
              [nbLegend(.green, "Same contexts as king")],
              "\"king\" and \"queen\" {share every context}.",
              "Both appear between \"the\" and \"rules\", so their rows of X are nearly identical and their vectors end up close: cosine \(f2(cosA(run.w[k], run.w[q])))."),
        frame([.caption("why log counts"), table(["ratio", "meaning"], [1.2, 1], [
            NbRow(cells: [nbCell("P(ice|solid) / P(steam|solid)"), nbCell("8.9 — large", .green)]),
            NbRow(cells: [nbCell("P(ice|gas) / P(steam|gas)"), nbCell("0.085 — small", .red)]),
            NbRow(cells: [nbCell("P(ice|water) / P(steam|water)"), nbCell("1.36 — ≈ 1")]),
        ], aligns: [0, 2])], [],
              "Meaning lives in {ratios} of co-occurrence.",
              "From the GloVe paper: \"solid\" relates to ice not steam, \"gas\" the reverse, \"water\" to both. Logs turn those ratios into differences, which dot products can express."),
        frame([table(["", "word2vec", "GloVe"], [0.8, 1, 1], [
            NbRow(cells: [nbName("sees"), nbCell("one window at a time"), nbCell("all counts at once")]),
            NbRow(cells: [nbName("objective"), nbCell("predict neighbours"), nbCell("fit log X")]),
            NbRow(cells: [nbName("training set"), nbCell("every token"), nbCell("non-zero cells", .green)]),
            NbRow(cells: [nbName("vectors"), nbCell("similar quality"), nbCell("similar quality")]),
        ])], [], "Two routes to {the same kind of vector}.",
              "Levy and Goldberg showed skip-gram with negative sampling implicitly factorises a shifted PMI matrix — GloVe just does it explicitly."),
    ]
}

// MARK: - FastText

private func ngrams(_ w: String, _ n: Int = 3) -> [String] {
    let ch = Array("<\(w)>")
    return (0...(ch.count - n)).map { String(ch[$0..<$0 + n]) }
}

private func fastTextFrames() -> [NbFrame] {
    let base = Set(ngrams("king"))
    func row(_ w: String) -> [NbBlock] {
        [.caption(w, note: "\(ngrams(w).count) n-grams" + (w != "king" ? " · \(ngrams(w).filter { base.contains($0) }.count) shared with king" : "")),
         .toks(ngrams(w).map { nbTok($0, base.contains($0) ? .pick : .plain) })]
    }
    let legend = [nbLegend(.violet, "Shared with \"king\""), nbLegend(.slate, "Unique to the word")]
    let known = Set(ngrams("king") + ngrams("kingdom") + ngrams("kings"))
    let unseen = ngrams("kingly"), typo = ngrams("kingg")
    let unseenKnown = unseen.filter { known.contains($0) }.count
    let typoShared = typo.filter { base.contains($0) }.count
    let head = NbBlock.caption("character 3-grams with boundary markers")
    return [
        frame([head] + row("king") + [.callout(["\"king\" → <ki · kin · ing · ng>  + the whole word <king>"])], legend,
              "FastText splits each word into {character n-grams}.",
              "< and > mark the word's edges, so \"ing\" inside a word differs from the suffix \"ng>\". The whole word is kept as one more unit."),
        frame([head] + row("king") + row("kingdom") + row("kings"), legend,
              "A word's vector is the sum of its subwords' vectors.",
              "\"king\", \"kingdom\" and \"kings\" share their first three n-grams, so evidence for one helps all, and unseen words still get a vector.",
              fx: ["v(w) = Σ v(g) over g ∈ n-grams(w)", "unseen \"kingly\" → {\(unseenKnown) of \(unseen.count)} n-grams already trained"]),
        frame([head] + row("kingly"), legend, "An unseen word {still gets a vector}.",
              "Word2vec has nothing for a word it never saw. FastText sums the \(unseenKnown) trained n-grams of \"kingly\" and the rest start at their (shared, hashed) initial values.",
              chips: [nbChip("known n-grams", "\(unseenKnown) / \(unseen.count)", true)]),
        frame([head] + row("kingg"), legend, "Typos land {near the right word}.",
              "\"kingg\" shares \(typoShared) of its \(typo.count) n-grams with \"king\", so its vector sits close by — useful for social-media text and morphologically rich languages.",
              chips: [nbChip("shared with king", "\(typoShared) / \(typo.count)", true)]),
        frame([.caption("n-grams per word length, n = 3…6"), table(["word", "letters", "n-grams"], [1, 0.6, 0.6], ["king", "kingdom", "internationalization"].map { w in
            NbRow(cells: [nbName(w), nbCell("\(w.count)"), nbCell("\((3...6).reduce(0) { $0 + ngrams(w, $1).count })", .indigo)])
        })], [nbLegend(.indigo, "Units summed per word")],
              "Real FastText uses n = 3 to 6 — {many units per word}.",
              "A long word sums dozens of vectors. To bound memory, n-grams are hashed into a fixed number of buckets (2 million by default); collisions are tolerated."),
        frame([.kv([("vocabulary", "words + hashed n-grams"), ("OOV words", "handled"), ("morphology", "shared prefixes and suffixes"), ("cost", "more vectors to store and sum")])], [],
              "Subwords buy {robustness} for the price of memory.",
              "FastText's vectors are trained like skip-gram; only the input representation changes. Modern subword tokenizers (BPE, WordPiece) carry the same idea into transformers."),
    ]
}

// MARK: - ELMo

private let elmoSentences = ["the river bank flooded", "we sat on the bank of the stream", "the bank raised its rates", "I paid it into the bank"]
/// Illustrative contextual vectors for "bank" (2-D projection) — river sense up and left, money sense down and right.
private let elmoVecs: [[Double]] = [[0.62, 0.78], [0.70, 0.70], [0.55, -0.62], [0.66, -0.58]]
private let elmoStatic: [Double] = [0.6, 0.05]

private func elmoFrames() -> [NbFrame] {
    func plot(_ upTo: Int) -> NbBlock {
        .plot(NbPlotBlock(xr: (-1, 1), yr: (-1, 1), height: 170,
                          dots: [NbDot(p: NbP(elmoStatic[0] - 0.75, elmoStatic[1]), ink: .yellow, r: 6, label: "word2vec \"bank\" (one vector)")] +
                            elmoVecs.prefix(upTo).enumerated().map { i, v in NbDot(p: NbP(v[0], v[1]), ink: i < 2 ? .sky : .orange, r: 7, text: "\(i + 1)") },
                          segs: [NbSeg(a: NbP(-0.95, 0), b: NbP(0.95, 0), ink: .slate, width: 1)], grid: false))
    }
    let sentences = table([], [0.15, 1.6], elmoSentences.enumerated().map { i, s in NbRow(cells: [nbCell("\(i + 1)", i < 2 ? .sky : .orange, bold: true), nbProse(s)]) }, aligns: [0, 0])
    let legend = [nbLegend(.sky, "River sense"), nbLegend(.orange, "Money sense"), nbLegend(.yellow, "Static vector")]
    let head = NbBlock.caption("4 sentences containing \"bank\" · 2-D projection (illustrative)")
    let c12 = cosA(elmoVecs[0], elmoVecs[1]), c13 = cosA(elmoVecs[0], elmoVecs[2]), c34 = cosA(elmoVecs[2], elmoVecs[3])
    let left: Set<String> = ["sat", "on", "the"]
    return [
        frame([head, plot(0), sentences], [nbLegend(.yellow, "Static vector")], "Word2vec gives \"bank\" {one vector}.",
              "A static embedding is a lookup: every occurrence of \"bank\" gets the same point, halfway between its senses."),
        frame([head, plot(4), sentences], legend, "ELMo makes the vector a function of the sentence.",
              "A biLSTM reads the whole sentence, so four sentences give four vectors — and the senses separate.",
              chips: [nbChip("cos(1,2)", f2(c12), true), nbChip("cos(1,3)", f2(c13))]),
        frame([.caption("two directions over \"we sat on the bank of the stream\""), .toks(words("we sat on the bank of the stream").map { nbTok($0, $0 == "bank" ? .pick : left.contains($0) ? .blue : .orange) }),
               .kv([("forward LSTM", "reads we → sat → on → the → bank"), ("backward LSTM", "reads stream → the → of → bank"), ("\"bank\" vector", "both directions, concatenated")])],
              [nbLegend(.blue, "Left context"), nbLegend(.orange, "Right context")],
              "Two LSTMs, {one per direction}.",
              "\"stream\" comes after \"bank\" — only the backward LSTM sees it. Concatenating both states gives a vector informed by the whole sentence."),
        frame([.caption("ELMo = weighted sum of layers"), .bars([
            NbBar(label: "char CNN", value: "layer 0", frac: 0.25, ink: .grey), NbBar(label: "biLSTM 1", value: "syntax", frac: 0.55, ink: .sky), NbBar(label: "biLSTM 2", value: "semantics", frac: 0.85, ink: .indigo),
        ], labelWidth: 80), .callout(["ELMo_k = γ · Σ_j s_j · h_k,j   (s = softmax, learned per task)"])], [],
              "Each task learns {its own mix of layers}.",
              "Lower layers capture syntax (good for tagging), higher ones word sense (good for disambiguation). The task picks the weights s_j."),
        frame([head, plot(4), .tiles([NbTile(big: f2(c12), caption: "river vs river", ink: .sky), NbTile(big: f2(c34), caption: "money vs money", ink: .orange), NbTile(big: f2(c13), caption: "river vs money", ink: .red)])], legend,
              "Same sense {close}, different sense {w:far}.",
              "Within a sense the vectors agree; across senses cosine drops to \(f2(c13)). A static vector can't do both — its similarity to each use is fixed."),
        frame([.kv([("2018 · ELMo", "contextual vectors from a biLSTM LM"), ("use", "features added to a task model"), ("next · BERT", "transformer, fine-tuned end to end"), ("kept idea", "a word's vector depends on its sentence")])], [],
              "ELMo started {contextual embeddings}.",
              "It was used as frozen features; BERT and GPT replaced the LSTMs with transformers and fine-tuned the whole network — but the vector-per-occurrence idea is ELMo's."),
    ]
}

// MARK: - POS tagging

private func posFrames() -> [NbFrame] {
    let s1 = [("the", "DT"), ("old", "JJ"), ("man", "NN"), ("chased", "VBD")]
    let s2 = [("they", "PRP"), ("man", "VBP"), ("a", "DT"), ("boat", "NN")]
    func toks(_ s: [(String, String)], _ hot: String? = "man") -> NbBlock { .toks(s.map { nbTok($0.0, $0.0 == hot ? .hot : .plain, $0.1) }) }
    let bars = NbBlock.bars([NbBar(label: "NN", value: "2", frac: 2.0 / 3, ink: .blue), NbBar(label: "VBP", value: "1", frac: 1.0 / 3, ink: .violet)], labelWidth: 44)
    let head = NbBlock.caption("the same word, two categories")
    let legend = [nbLegend(.yellow, "Ambiguous word")]
    let baseline = [true, true, true, true, true, false, true, true]
    let correct = baseline.filter { $0 }.count
    let acc = Double(correct) / Double(baseline.count)
    let all = s1 + s2
    let tags: [(String, String, String)] = [("DT", "determiner", "the, a"), ("JJ", "adjective", "old"), ("NN", "noun, singular", "man, boat"), ("VBD", "verb, past tense", "chased"), ("VBP", "verb, present", "man (staff)"), ("PRP", "personal pronoun", "they")]
    return [
        frame([head, .caption("sentence 1"), toks(s1), .caption("sentence 2"), toks(s2), .caption("training counts for \"man\""), bars,
               .callout(["Most-frequent-tag baseline: always NN → right in sentence 1, wrong in sentence 2."], warn: true)], legend,
              "Tagging assigns each token a syntactic category.",
              "It looks like a lookup until a word has more than one. Nothing about \"man\" itself decides — only its neighbours do."),
        frame([.caption("most-frequent-tag baseline on both sentences"), .toks(all.enumerated().map { i, wt in nbTok(wt.0, baseline[i] ? .good : .bad, baseline[i] ? wt.1 : "NN ≠ \(wt.1)") })],
              [nbLegend(.green, "Correct"), nbLegend(.red, "Wrong")],
              "The baseline gets {\(correct) of \(baseline.count)}.",
              "Tag every word with its most common tag in training. On real text this already reaches about 92% — most words have one tag.",
              chips: [nbChip("accuracy", nbF(acc * 100, 1) + "%", true)]),
        frame([.caption("context decides · P(tag | previous tag)"), toks(s2), table(["previous", "next", "P"], [0.8, 0.6, 0.5], [
            NbRow(cells: [nbName("PRP"), nbCell("VBP"), nbCell("0.42", .green)]), NbRow(cells: [nbName("PRP"), nbCell("NN"), nbCell("0.01", .red)]),
            NbRow(cells: [nbName("JJ"), nbCell("NN"), nbCell("0.45", .green)]), NbRow(cells: [nbName("JJ"), nbCell("VBP"), nbCell("0.003", .red)]),
        ])], legend,
              "After a pronoun, {a verb} is far likelier than a noun.",
              "An HMM or CRF multiplies these transition probabilities by how often each tag emits \"man\". The context term overrules the word's own preference for NN. (Transition values illustrative.)"),
        frame([.caption("Penn Treebank tags used here"), table(["tag", "meaning", "example"], [0.5, 1.2, 0.8], tags.map { NbRow(cells: [nbName($0.0, .indigo), nbProse($0.1), nbCell($0.2)]) }, aligns: [0, 0, 2])], [],
              "The tag set has {45 tags}, not 8 parts of speech.",
              "Penn Treebank splits verbs by tense and person and nouns by number, because those distinctions matter to parsers downstream."),
        frame([.caption("accuracy on WSJ (Penn Treebank)"), .bars([
            NbBar(label: "most frequent", value: "≈92%", frac: 0.92, ink: .grey), NbBar(label: "HMM", value: "≈96%", frac: 0.96, ink: .sky), NbBar(label: "BiLSTM-CRF", value: "≈97.5%", frac: 0.975, ink: .indigo),
        ], labelWidth: 108)], [],
              "The last few points are {all about context}.",
              "Most tokens are easy; the gains come from ambiguous words like \"man\", \"that\" and \"back\", where the neighbours decide."),
        frame([.kv([("ambiguous types", "~15% of word types"), ("ambiguous tokens", "~55% of running text"), ("why", "common words are the ambiguous ones"), ("next", "chunking and parsing use the tags")])], [],
              "Few word types are ambiguous — but {they are the common ones}.",
              "\"that\", \"back\", \"down\", \"put\": a minority of the vocabulary, a majority of the tokens. Tagging is where every syntactic pipeline starts."),
    ]
}

// MARK: - NER

private func nerInk(_ type: String) -> NbInk { type == "PER" ? .blue : type == "ORG" ? .pink : .orange }

private func nerFrames() -> [NbFrame] {
    let toks = [("Ada", "B-PER"), ("Lovelace", "I-PER"), ("joined", "O"), ("Analytical", "B-ORG"), ("Engine", "I-ORG"), ("Ltd", "I-ORG"), ("in", "O"), ("London", "B-LOC")]
    func tone(_ tag: String) -> NbTone {
        switch tag.components(separatedBy: "-").last! {
        case "PER": .blue
        case "ORG": .pink
        case "LOC": .orange
        default: .dim
        }
    }
    func bio(_ show: Bool = true) -> NbBlock { .toks(toks.map { nbTok($0.0, show ? tone($0.1) : .plain, show ? $0.1 : "") }) }
    var spans: [(String, String)] = []
    for (w, t) in toks {
        if t.hasPrefix("B-") { spans.append((w, String(t.dropFirst(2)))) }
        else if t.hasPrefix("I-"), !spans.isEmpty { spans[spans.count - 1] = (spans.last!.0 + " " + w, spans.last!.1) }
    }
    let entities = table([], [1.5, 0.5], spans.map { NbRow(cells: [nbName($0.0, nerInk($0.1)), nbCell($0.1, nerInk($0.1))]) })
    let labels = NbBlock.boxes(["PER", "ORG", "LOC"].map { NbBox(title: $0, lines: ["B-\($0) · I-\($0)"], ink: nerInk($0)) })
    let legend = [nbLegend(.blue, "Person"), nbLegend(.pink, "Organisation"), nbLegend(.orange, "Location"), nbLegend(.slate, "Outside (O)")]
    let predicted = [("Ada Lovelace", "PER"), ("Analytical Engine", "ORG"), ("London", "ORG")]
    func inGold(_ p: (String, String)) -> Bool { spans.contains { $0.0 == p.0 && $0.1 == p.1 } }
    let correct = predicted.filter(inGold).count
    let p = Double(correct) / Double(predicted.count), r = Double(correct) / Double(spans.count)
    let f1 = 2 * p * r / (p + r)
    return [
        frame([.caption("find the names · one label per token"), bio(false)], [], "NER turns span-finding into {token labelling}.",
              "Entities can be several tokens long. Labelling each token keeps the model a simple per-position classifier."),
        frame([.caption("gold BIO tags · one tag per token"), bio(), .caption("entities recovered from the tags"), entities, .caption("label set · 2 × 3 + O = 7"), labels], legend,
              "B- starts an entity, I- continues it, O is outside.",
              "Spans are read back by joining each B- with the I- tags that follow it."),
        frame([.caption("why B- is needed"), .toks([nbTok("Paris", .orange, "B-LOC"), nbTok("London", .orange, "B-LOC"), nbTok("Rome", .orange, "B-LOC")]),
               .callout(["with only I-LOC: Paris London Rome → {one} entity", "with B-LOC each: → {three} entities"])], legend,
              "Adjacent entities of the same type {need the B- tag}.",
              "\"…flights Paris London Rome…\" are three places. Without a begin tag the decoder would merge them into one span."),
        frame([.caption("a sequence the decoder must not output"), .toks([nbTok("joined", .dim, "O"), nbTok("Engine", .bad, "I-ORG"), nbTok("Ltd", .pink, "I-ORG")]),
               .callout(["O → I-ORG is invalid: an entity can't continue without starting", "CRF layer: transition score O → I-* = {−∞}"])], legend,
              "A CRF layer {forbids} invalid tag sequences.",
              "Independent per-token predictions can output O followed by I-ORG. A CRF scores whole sequences and gives impossible transitions zero probability."),
        frame([.caption("same word, different type"), table([], [1.6, 0.5], [
            NbRow(cells: [nbProse("Washington signed the bill"), nbCell("PER", .blue)]),
            NbRow(cells: [nbProse("we flew to Washington"), nbCell("LOC", .orange)]),
            NbRow(cells: [nbProse("Washington beat Dallas 3–1"), nbCell("ORG", .pink)]),
        ])], legend, "The type comes from context, {not the name}.",
              "A gazetteer of names can't decide this. The model has to read the verb and the preposition around the name."),
        frame([.caption("scored at entity level · exact span and type"), table(["predicted", "type", "gold?"], [1.4, 0.5, 0.5], predicted.map { s in
            let ok = inGold(s)
            return NbRow(cells: [nbName(s.0, nerInk(s.1)), nbCell(s.1), nbCell(ok ? "✓" : "✗", ok ? .green : .red, bold: true)])
        }), .tiles([NbTile(big: f2(p), caption: "precision", ink: .indigo), NbTile(big: f2(r), caption: "recall", ink: .indigo), NbTile(big: f2(f1), caption: "F1", ink: .green)])], legend,
              "Off by one token {counts as wrong}.",
              "\"Analytical Engine\" misses \"Ltd\" and \"London\" has the wrong type: both are errors. \(correct) of \(predicted.count) predictions are exact, giving F1 = \(f2(f1))."),
        frame([.caption("what flat BIO cannot express"), .toks([nbTok("Bank", .pink), nbTok("of", .pink), nbTok("England", .orange)]),
               .callout(["ORG: Bank of England", "LOC inside it: England  → needs a {second layer}"])], legend,
              "Nested entities {don't fit} one tag per token.",
              "\"England\" is a location inside an organisation's name. Flat BIO picks one; nested NER uses span classification or layered tags."),
    ]
}

// MARK: - Chunking

private let chunkSentence = [("the", "DT"), ("small", "JJ"), ("dog", "NN"), ("chased", "VBD"), ("a", "DT"), ("cat", "NN"), ("in", "IN"), ("the", "DT"), ("garden", "NN")]

/// NP = DT? JJ* NN+, matched greedily left to right; returns [start, end) spans.
private func npChunks() -> [(Int, Int)] {
    var out: [(Int, Int)] = []
    let tags = chunkSentence.map(\.1)
    var i = 0
    while i < tags.count {
        var j = i
        if j < tags.count && tags[j] == "DT" { j += 1 }
        while j < tags.count && tags[j] == "JJ" { j += 1 }
        let nounStart = j
        while j < tags.count && tags[j].hasPrefix("NN") { j += 1 }
        if j > nounStart { out.append((i, j)); i = j } else { i += 1 }
    }
    return out
}

private func chunkFrames() -> [NbFrame] {
    let chunks = npChunks()
    func chunkOf(_ i: Int) -> Int { chunks.firstIndex { i >= $0.0 && i < $0.1 } ?? -1 }
    func toks(_ upTo: Int, _ scanned: Int) -> NbBlock {
        .toks(chunkSentence.enumerated().map { i, wt in
            let k = chunkOf(i)
            let tone: NbTone = k == upTo ? .hot : (k >= 0 && k < upTo) ? .pick : i < scanned ? .plain : .dim
            return nbTok(wt.0, tone, wt.1)
        })
    }
    func bracket(_ n: Int) -> NbBlock {
        var spans: [NbSpan] = []
        var i = 0
        while i < chunkSentence.count {
            if let k = chunks.firstIndex(where: { $0.0 == i }), k < n {
                let (a, b) = chunks[k]
                spans.append(NbSpan(t: "[NP " + chunkSentence[a..<b].map(\.0).joined(separator: " ") + "]", tone: k == n - 1 ? .hot : .blue))
                spans.append(NbSpan(t: " "))
                i = b
            } else {
                spans.append(NbSpan(t: chunkSentence[i].0 + " ", tone: .dim))
                i += 1
            }
        }
        return .text(spans, mono: true)
    }
    func ruleTable(_ k: Int) -> NbBlock {
        let (a, b) = chunks[k]
        let ws = Array(chunkSentence[a..<b])
        func pick(_ f: (String) -> Bool) -> String { let s = ws.filter { f($0.1) }.map(\.0).joined(separator: " "); return s.isEmpty ? "—" : s }
        return table(["pattern", "means", "token"], [0.5, 1.2, 0.7], [
            NbRow(cells: [nbName("DT?", .yellow), nbCell("optional determiner"), nbCell(pick { $0 == "DT" }, bold: true)]),
            NbRow(cells: [nbName("JJ*", .yellow), nbCell("any adjectives"), nbCell(pick { $0 == "JJ" }, bold: true)]),
            NbRow(cells: [nbName("NN+", .yellow), nbCell("one or more nouns"), nbCell(pick { $0.hasPrefix("NN") }, bold: true)]),
        ])
    }
    let legend = [nbLegend(.yellow, "Current chunk"), nbLegend(.violet, "Later chunks"), nbLegend(.slate, "Not yet scanned")]
    let doneLegend = [nbLegend(.violet, "Chunk found"), nbLegend(.yellow, "Current chunk")]
    let head = NbBlock.caption("NP rule · DT? JJ* NN+")
    let iob = chunkSentence.enumerated().map { i, wt -> NbTok in
        let k = chunkOf(i)
        return nbTok(wt.0, k < 0 ? .dim : .blue, k < 0 ? "O" : chunks[k].0 == i ? "B-NP" : "I-NP")
    }
    func chunkText(_ k: Int) -> String { chunkSentence[chunks[k].0..<chunks[k].1].map(\.0).joined(separator: " ") }
    return [
        frame([.caption("input · words with their POS tags"), .toks(chunkSentence.map { nbTok($0.0, .plain, $0.1) })], [],
              "Chunking groups tags into {flat phrases}.",
              "It runs on POS tags, not words. A noun phrase is a determiner, any adjectives, then nouns — one regular expression over the tag sequence."),
        frame([head, toks(0, chunks[0].1), .caption("rule matched token by token"), ruleTable(0), .caption("after all chunks (preview)"), bracket(chunks.count)], legend,
              "Chunk 1: \"\(chunkText(0))\" is an NP — tokens [\(chunks[0].0), \(chunks[0].1)).",
              "Chunks never nest and never overlap — that is the whole simplification over a full parse."),
        frame([head, toks(1, chunks[0].1 + 1)], legend, "\"chased\" is VBD — {no NP starts here}.",
              "The rule needs a noun to finish. A verb can't start a determiner or an adjective run, so the scanner moves on one token."),
        frame([head, toks(1, chunks[1].1), ruleTable(1)], legend, "Chunk 2: \"\(chunkText(1))\" — tokens [\(chunks[1].0), \(chunks[1].1)).",
              "No adjectives this time: JJ* matches zero tokens, which a star allows."),
        frame([head, toks(2, chunks[1].1 + 1)], legend, "\"in\" is a preposition — {skipped}.",
              "Prepositions belong to PP chunks, which a second rule would find. The NP rule simply passes over it."),
        frame([head, toks(2, chunks[2].1), ruleTable(2)], legend, "Chunk 3: \"\(chunkText(2))\" — tokens [\(chunks[2].0), \(chunks[2].1)).",
              "The scan reaches the end with \(chunks.count) noun phrases."),
        frame([head, bracket(chunks.count)], doneLegend, "Three NPs, {each a contiguous span}.",
              "The output is a bracketing of the sentence. Every word is either inside exactly one chunk or outside all of them."),
        frame([.caption("the same chunks as IOB tags"), .toks(iob)], [nbLegend(.blue, "Inside a noun phrase"), nbLegend(.slate, "Outside")],
              "As labels, chunking is {the same task as NER}.",
              "B-NP / I-NP / O per token: the same encoding, so the same sequence models (CRF, BiLSTM) learn it from data instead of hand-written rules."),
        frame([.caption("more rules · VP and PP"), .text([NbSpan(t: "[NP the small dog] ", tone: .blue), NbSpan(t: "[VP chased] ", tone: .pink), NbSpan(t: "[NP a cat] ", tone: .blue), NbSpan(t: "[PP in] ", tone: .orange), NbSpan(t: "[NP the garden]", tone: .blue)], mono: true)],
              [nbLegend(.blue, "NP"), nbLegend(.pink, "VP"), nbLegend(.orange, "PP")],
              "Add rules for verbs and prepositions: {a shallow parse}.",
              "Every word now sits in a phrase, but the phrases stay flat — nothing says the PP attaches to \"chased\" rather than \"a cat\"."),
        frame([.kv([("output", "flat, non-overlapping spans"), ("cost", "linear time, one pass"), ("loses", "nesting and attachment"), ("good for", "keyphrases, information extraction")])], [],
              "Chunking trades structure for {speed and robustness}.",
              "When you only need the noun phrases, a full parser is overkill. When you need who-did-what-to-whom, see dependency parsing."),
    ]
}

// MARK: - Dependency parsing (arc-standard oracle)

private let depWords = ["ROOT", "the", "small", "dog", "chased", "a", "cat"]
private let depHead: [Int: Int] = [1: 3, 2: 3, 3: 4, 4: 0, 5: 6, 6: 4]
private let depLabel: [Int: String] = [1: "det", 2: "amod", 3: "nsubj", 4: "root", 5: "det", 6: "obj"]

private struct DepState { let stack: [Int]; let buffer: [Int]; let arcs: [Int]; let action: String }

private func depOracle() -> [DepState] {
    var stack = [0], buffer = Array(1...6), arcs: [Int] = []
    var out: [DepState] = []
    func attached(_ h: Int) -> Bool { depHead.filter { $0.value == h }.keys.allSatisfy { arcs.contains($0) } }
    while !buffer.isEmpty || stack.count > 1 {
        let action: String
        if stack.count >= 2 && stack[stack.count - 2] != 0 && depHead[stack[stack.count - 2]] == stack.last! {
            arcs.append(stack.remove(at: stack.count - 2)); action = "LA"
        } else if stack.count >= 2 && depHead[stack.last!] == stack[stack.count - 2] && attached(stack.last!) {
            arcs.append(stack.removeLast()); action = "RA"
        } else {
            stack.append(buffer.removeFirst()); action = "SH"
        }
        out.append(DepState(stack: stack, buffer: buffer, arcs: arcs, action: action))
    }
    return out
}

private func dependencyFrames() -> [NbFrame] {
    let states = depOracle()
    func plan(_ cur: Int) -> NbBlock { .toks(states.enumerated().map { i, s in nbTok(s.action, i == cur ? .hot : s.action == "SH" ? .plain : .pick, "\(i + 1)") }, columns: 6, mono: true) }
    func arcText(_ d: Int) -> String { "\(depWords[depHead[d]!]) → \(depWords[d]) \(depLabel[d]!)" }
    let arcOrder = [1, 2, 3, 6, 5, 4]
    func arcGrid(_ built: [Int]) -> NbBlock { .toks(arcOrder.map { nbTok(arcText($0), built.contains($0) ? .good : .dim) }, columns: 2, mono: true, start: true) }
    func sb(_ s: DepState?) -> [NbBlock] {
        let stack = s?.stack ?? [0], buffer = s?.buffer ?? Array(1...6)
        return [
            .toks(stack.enumerated().map { i, w in nbTok(depWords[w], s != nil && i == stack.count - 1 && w != 0 ? .hot : .plain) }, label: "stack"),
            .toks(buffer.isEmpty ? [nbTok("empty", .empty)] : buffer.map { nbTok(depWords[$0], .dim) }, label: "buffer"),
        ]
    }
    let legend = [nbLegend(.yellow, "Current step"), nbLegend(.violet, "Arc action (LA / RA)"), nbLegend(.grey, "Shift")]
    let head = NbBlock.caption("arc-standard transitions · gold tree oracle")
    var frames: [NbFrame] = []
    frames.append(frame([.caption("the goal · every word gets one head"), .toks((1...6).map { nbTok(depWords[$0], .plain, "← \(depWords[depHead[$0]!])") }), .caption("arcs it will build"), arcGrid([])],
                        [nbLegend(.green, "Built"), nbLegend(.slate, "To build")],
                        "A dependency parse gives every word {one head}.",
                        "\"dog\" is the subject of \"chased\", \"cat\" its object, and the determiners hang off their nouns. Six words, six arcs, one of them from ROOT."))
    frames.append(frame([head] + sb(nil) + [.caption("all \(states.count) transitions for this sentence"), plan(-1), .caption("arcs it will build"), arcGrid([])], legend,
                        "Parse with {a stack and three moves}.",
                        "SHIFT moves the next word onto the stack. LEFT-ARC makes the top word the head of the one under it; RIGHT-ARC the reverse. An oracle reads the moves off the gold tree: \(states.count) = 2 × 6."))
    for (i, s) in states.enumerated() {
        let name = s.action == "SH" ? "SHIFT" : s.action == "LA" ? "LEFT-ARC" : "RIGHT-ARC"
        let body = s.action == "SH"
            ? "Push \"\(depWords[s.stack.last!])\" onto the stack; no arc yet. \(s.arcs.count) of 6 arcs built."
            : "\(arcText(s.arcs.last!)): \"\(depWords[s.arcs.last!])\" leaves the stack with its head found. \(s.arcs.count) of 6 arcs built."
        frames.append(frame([head] + sb(s) + [.caption("all \(states.count) transitions for this sentence"), plan(i), .caption("arcs it will build"), arcGrid(s.arcs)], legend,
                            "Transition \(i + 1) of \(states.count): \(name).", body))
    }
    frames.append(frame([.caption("scored per word · a parser's output"), table(["word", "gold head", "predicted", ""], [0.8, 0.9, 0.9, 0.3], (1...6).map { d in
        let ok = d != 6
        let pred = ok ? "\(depWords[depHead[d]!]) \(depLabel[d]!)" : "dog obj"
        return NbRow(cells: [nbName(depWords[d]), nbCell("\(depWords[depHead[d]!]) \(depLabel[d]!)"), nbCell(pred, ok ? nil : .red), nbCell(ok ? "✓" : "✗", ok ? .green : .red, bold: true)])
    }), .tiles([NbTile(big: "5 / 6", caption: "UAS · right head", ink: .indigo), NbTile(big: "5 / 6", caption: "LAS · head and label", ink: .indigo)])], [],
                        "Score: {right head per word}.",
                        "Unlabelled attachment (UAS) checks the head; labelled (LAS) also the relation. Attaching \"cat\" to \"dog\" costs one word on both."))
    frames.append(frame([.kv([("time", "linear — 2n transitions"), ("learned part", "a classifier picks the move"), ("limit", "projective trees only"), ("fix", "swap transition or graph parsers")])], [],
                        "Transition parsing is {linear time}.",
                        "A trained classifier replaces the oracle and picks each move from the stack and buffer. Crossing arcs (common in Dutch, Czech) need an extra SWAP move."))
    return frames
}

// MARK: - Constituency parsing

private func constituencyFrames() -> [NbFrame] {
    let ws = [("the", "DT"), ("small", "JJ"), ("dog", "NN"), ("chased", "VBD"), ("a", "DT"), ("cat", "NN")]
    func leaf(_ i: Int) -> CGFloat { 0.08 + 0.84 * CGFloat(i) / 5 }
    let gold = [("NP", 0, 3), ("VP", 3, 6), ("NP", 4, 6), ("S", 0, 6)]
    let pos: [Int: (CGFloat, CGFloat)] = [0: (0.25, 0.4), 1: (0.66, 0.4), 2: (0.83, 0.6), 3: (0.45, 0.14)]
    func tree(_ show: Set<Int>, _ hot: Int? = nil) -> NbBlock {
        var nodes = ws.enumerated().map { NbNode(x: leaf($0), y: 0.82, text: $1.0, sub: $1.1) }
        var idx: [Int: Int] = [:]
        for (k, g) in gold.enumerated() where show.contains(k) {
            idx[k] = nodes.count
            nodes.append(NbNode(x: pos[k]!.0, y: pos[k]!.1, text: g.0, sub: "[\(g.1),\(g.2))", tone: k == hot ? .hot : .good))
        }
        var edges: [NbEdge] = []
        if let n = idx[0] { for i in 0...2 { edges.append(NbEdge(a: n, b: i)) } }
        if let n = idx[2] { for i in 4...5 { edges.append(NbEdge(a: n, b: i)) } }
        if let n = idx[1] { edges.append(NbEdge(a: n, b: 3)); if let m = idx[2] { edges.append(NbEdge(a: n, b: m, hot: true)) } }
        if let n = idx[3] {
            if let m = idx[0] { edges.append(NbEdge(a: n, b: m, hot: true)) }
            if let m = idx[1] { edges.append(NbEdge(a: n, b: m, hot: true)) }
        }
        return .tree(nodes: nodes, edges: edges, height: 210)
    }
    let spanToks = NbBlock.toks(gold.map { nbTok("\($0.0) [\($0.1),\($0.2))", .good) }, mono: true)
    let legend = [nbLegend(.green, "Constituent (scored)"), nbLegend(.blue, "Phrase-to-phrase edge", .line), nbLegend(.slate, "POS node (not scored)")]
    let head = NbBlock.caption("constituency tree · spans are [start, end)")
    let pred = gold + [("NP", 3, 6)]
    func inGold(_ s: (String, Int, Int)) -> Bool { gold.contains { $0 == s } }
    let correct = pred.filter(inGold).count
    let p = Double(correct) / Double(pred.count), r = Double(correct) / Double(gold.count)
    let f1 = 2 * p * r / (p + r)
    return [
        frame([head, tree([])], Array(legend.suffix(1)), "Start from the words and {their tags}.",
              "A constituency parse groups words into nested phrases. The POS tags are the bottom layer every phrase is built on."),
        frame([head, tree([0, 1, 2, 3]), .caption("what the parser is scored on"), spanToks], legend, "The units it is scored on are labelled spans.",
              "POS nodes are excluded by convention, since tagger accuracy would otherwise inflate the parser's score."),
        frame([head, tree([0, 2], 2)], legend, "Bottom-up: two noun phrases, {[0,3) and [4,6)}.",
              "Each NP covers a contiguous span. Unlike chunks, they will be nested inside larger phrases."),
        frame([head, tree([0, 1, 2], 1)], legend, "VP [3,6) {contains} NP [4,6).",
              "The verb and its object form a verb phrase — the nesting a chunker can't express."),
        frame([.caption("a parser's output vs gold · labelled brackets"), table(["span", "in gold?"], [1, 0.5], pred.map { s in
            let ok = inGold(s)
            return NbRow(cells: [nbName("\(s.0) [\(s.1),\(s.2))"), nbCell(ok ? "✓" : "✗", ok ? .green : .red, bold: true)])
        }), .tiles([NbTile(big: f2(p), caption: "precision", ink: .indigo), NbTile(big: f2(r), caption: "recall", ink: .indigo), NbTile(big: f2(f1), caption: "F1", ink: .green)])], [],
              "PARSEVAL: {precision and recall over spans}.",
              "An extra wrong bracket NP [3,6) costs precision but not recall: \(correct) of \(pred.count) predicted spans are in gold, all \(gold.count) gold spans are found."),
        frame([.kv([("CKY chart", "every span, every split: O(n³)"), ("n = 6", "\(6 * 6 * 6) span-split checks"), ("n = 40", "\(40 * 40 * 40) checks"), ("neural parsers", "score spans, same chart")])], [],
              "Exact parsing is {cubic} in sentence length.",
              "The CKY chart considers every span and every split point. Modern neural parsers keep the chart and learn the span scores."),
    ]
}

// MARK: - Coreference

private func corefFrames() -> [NbFrame] {
    let doc = [NbSpan(t: "Ada Lovelace", tone: .pick), NbSpan(t: " wrote to "), NbSpan(t: "Charles Babbage"), NbSpan(t: " from "), NbSpan(t: "London"), NbSpan(t: ". "),
               NbSpan(t: "She", tone: .hot), NbSpan(t: " admired "), NbSpan(t: "his", tone: .hot), NbSpan(t: " engine.")]
    let cands = [("Ada Lovelace", "fem", true), ("Charles Babbage", "masc", true), ("London", "—", false)]
    func tbl(_ gender: String) -> NbBlock {
        table(["mention", "number", "gender", "animate"], [1.3, 0.6, 0.7, 0.7], cands.map { m in
            let gOk = m.1 == gender, ok = gOk && m.2
            return NbRow(cells: [nbName(m.0), nbCell("sg ✓", .green), nbCell(m.1 + (gOk ? " ✓" : " ✗"), gOk ? .green : .red), nbCell(m.2 ? "yes ✓" : "no ✗", m.2 ? .green : .red)], ring: ok, dim: !ok)
        })
    }
    let legend = [nbLegend(.yellow, "Pronoun"), nbLegend(.violet, "Antecedent"), nbLegend(.slate, "Filtered out")]
    return [
        frame([.caption("which mentions refer to the same entity?"), .text(doc.map { $0.tone == .pick ? NbSpan(t: $0.t) : $0 })], [nbLegend(.yellow, "Pronoun")],
              "Coreference links mentions {to the same entity}.",
              "\"She\" and \"his\" point back to people named earlier. A reader resolves them instantly; a program has to choose among every earlier mention."),
        frame([.caption("candidates for \"She\" · agreement check"), .text(doc), tbl("fem"),
               .tiles([NbTile(big: "1 of 3", caption: "candidates survive", ink: .indigo), NbTile(big: "0", caption: "learned parameters")])], legend,
              "Agreement filters candidates cheaply.",
              "A singular feminine animate pronoun can only refer to Ada Lovelace here — and \"his\" only to Babbage — with no learning at all."),
        frame([.caption("candidates for \"his\" · agreement check"), tbl("masc")], legend, "\"his\" → {Charles Babbage}.",
              "Masculine and animate: one candidate left again. Rules like these were the core of early systems (Hobbs, 1978)."),
        frame([.caption("resolved · two entity clusters"), table(["entity", "mentions"], [0.8, 1.4], [
            NbRow(cells: [nbName("Ada Lovelace", .indigo), nbCell("Ada Lovelace · She")]),
            NbRow(cells: [nbName("Charles Babbage", .pink), nbCell("Charles Babbage · his")]),
            NbRow(cells: [nbName("London", .grey), nbCell("London")]),
        ], aligns: [0, 0])], [], "The output is {clusters} of mentions.",
              "Each cluster is one entity. Downstream, \"She admired his engine\" can be read as \"Lovelace admired Babbage's engine\"."),
        frame([.caption("when agreement can't decide"), .text([NbSpan(t: "The trophy", tone: .pick), NbSpan(t: " didn't fit in "), NbSpan(t: "the suitcase", tone: .pick), NbSpan(t: " because "), NbSpan(t: "it", tone: .hot), NbSpan(t: " was too big.")]),
               .callout(["both singular, neuter, inanimate → agreement keeps {2 of 2}", "\"too big\" → trophy · \"too small\" → suitcase"])], legend,
              "Some pronouns need {world knowledge}.",
              "Winograd schemas swap one word and flip the answer. Only knowing how trophies and suitcases work resolves \"it\"."),
        frame([.kv([("rules", "agreement, syntax, recency"), ("mention-ranking", "score each antecedent"), ("span-based (2017+)", "find mentions and links jointly"), ("LLMs", "resolve in context, implicitly")])], [],
              "Filters first, {learned scores} for the rest.",
              "Neural systems still prune with agreement-like features, then rank the surviving antecedents with a model trained on annotated clusters."),
    ]
}

// MARK: - Lexicon sentiment

private func sentimentFrames() -> [NbFrame] {
    let ws = words("the plot was not good but the acting was very good")
    let lexicon: [String: Double] = ["good": 2.0]
    let butAt = ws.firstIndex(of: "but")!
    // VADER-style rules: negation in the 3 words before ×−0.75, "very" ×1.5, clause before "but" ×0.5 and after ×1.5.
    func score(_ i: Int) -> (Double, String) {
        guard var s = lexicon[ws[i]] else { return (0, "") }
        var notes = ["+\(nbF(s, 0))"]
        if (max(0, i - 3)..<i).contains(where: { ws[$0] == "not" }) { s *= -0.75; notes.append("not ×−0.75") }
        if i > 0 && ws[i - 1] == "very" { s *= 1.5; notes.append("very ×1.5") }
        if i < butAt { s *= 0.5; notes.append("before but ×0.5") } else if i > butAt { s *= 1.5; notes.append("after but ×1.5") }
        return (s, notes.joined(separator: " · "))
    }
    let scored = ws.indices.filter { lexicon[ws[$0]] != nil }
    let raw = scored.reduce(0.0) { $0 + lexicon[ws[$1]]! }
    let ruled = scored.reduce(0.0) { $0 + score($1).0 }
    func toks(_ rules: Bool) -> NbBlock {
        .toks(ws.map { w in nbTok(w, lexicon[w] != nil ? .pick : rules && w == "not" ? .hot : rules && (w == "very" || w == "but") ? .blue : .dim) })
    }
    func tbl(_ rules: Bool) -> NbBlock {
        table(["word", "lex", "rules applied", "score"], [0.7, 0.3, 1.4, 0.5], scored.enumerated().map { k, i in
            let (s, n) = score(i)
            let rest = n.contains(" · ") ? String(n[n.range(of: " · ")!.upperBound...]) : "—"
            return NbRow(cells: [nbName("good #\(k + 1)"), nbCell("+2"), nbCell(rules ? rest : "—"), nbCell(rules ? (s > 0 ? "+" : "") + f2(s) : "+2.00", rules ? (s < 0 ? .red : .green) : nil)])
        })
    }
    let tiles = NbBlock.tiles([NbTile(big: "+" + nbF(raw, 1), caption: "raw lexicon sum"), NbTile(big: (ruled >= 0 ? "+" : "") + f2(ruled), caption: "with the three rules", ink: .green)])
    let head = NbBlock.caption("\"\(ws.joined(separator: " "))\"")
    let legend = [nbLegend(.yellow, "Negator"), nbLegend(.blue, "Modifier / contrast"), nbLegend(.violet, "Scored word")]
    let s1 = score(scored[0]).0
    return [
        frame([head, toks(false), .caption("lexicon: every word has a fixed score"), table(["word", "score"], [1, 0.5], [
            NbRow(cells: [nbName("good"), nbCell("+2.0", .green)]), NbRow(cells: [nbName("great"), nbCell("+3.1", .green)]), NbRow(cells: [nbName("bad"), nbCell("−2.5", .red)]), NbRow(cells: [nbName("plot, acting, the…"), nbCell("0")]),
        ])], [nbLegend(.violet, "In the lexicon")],
              "A lexicon gives each word {a polarity score}.",
              "Hand-built lists (VADER, AFINN) rate thousands of words. Most words, including \"plot\" and \"acting\", score zero. (Scores illustrative.)"),
        frame([head, toks(false), tbl(false), tiles], legend, "Summing the lexicon says {+\(nbF(raw, 1))} — clearly positive.",
              "Two \"good\"s, nothing negative. But the reviewer said the plot was not good."),
        frame([head, toks(true), tbl(true), tiles], legend, "Every lexicon system grows the same three rules.",
              "Negation flips and damps nearby scores, intensifiers scale them, and the clause after \"but\" outweighs the one before."),
        frame([head, toks(true), .callout(["good #1 = +2 × (−0.75) × 0.5 = {\(f2(s1))}"])], legend, "\"not good\" becomes {\(f2(s1))}, not −2.",
              "Negation is damped, not mirrored: \"not good\" is milder than \"bad\". VADER looks three words back for a negator."),
        frame([head, toks(true), tbl(true), .callout(["before \"but\" ×0.5 · after \"but\" ×1.5"])], legend, "After \"but\", {the writer's real view}.",
              "Contrast shifts weight to the second clause. The final +\(f2(ruled)) is positive but well below the naive +\(nbF(raw, 1))."),
        frame([.caption("where rules break"), table(["text", "lexicon", "truth"], [1.6, 0.5, 0.5], [
            NbRow(cells: [nbProse("great, another delay"), nbCell("+", .green), nbCell("−", .red)]),
            NbRow(cells: [nbProse("not bad at all"), nbCell("+ / −"), nbCell("+", .green)]),
            NbRow(cells: [nbProse("the battery died fast"), nbCell("0"), nbCell("−", .red)]),
        ])], [], "Sarcasm and domain words {defeat the lexicon}.",
              "\"fast\" is good for a car and bad for a battery; \"great\" is sarcastic here. Lexicons are transparent and need no training data — and that is all."),
    ]
}

// MARK: - LLMs: the next-token distribution

private let llmContext = ["The", "capital", "of", "France", "is"]
private let llmCands = ["Paris", "the", "a", "located", "home", "Lyon", "known", "not"]
/// Illustrative logits for the top candidates; the rest of the 50,257-entry vocabulary shares the remainder.
private let llmLogits = [9.0, 5.1, 4.4, 4.0, 3.4, 3.3, 2.7, 2.6]
private let llmVocab = 50257
private let llmTailLogit = -2.1

private func llmSoftmax(_ t: Double) -> ([Double], Double) {
    let m = llmLogits.max()!
    let top = llmLogits.map { exp(($0 - m) / t) }
    let tail = Double(llmVocab - llmLogits.count) * exp((llmTailLogit - m) / t)
    let z = top.reduce(0, +) + tail
    return (top.map { $0 / z }, tail / z)
}

private func llmFrames() -> [NbFrame] {
    func bars(_ p: [Double]) -> NbBlock { .bars(llmCands.indices.map { NbBar(label: llmCands[$0], value: nbF(p[$0] * 100, 1) + "%", frac: p[$0] / p.max()!, ink: $0 == 0 ? .violet : .blue) }, labelWidth: 72) }
    let (p1, tail1) = llmSoftmax(1), (p05, _) = llmSoftmax(0.5), (p2, tail2) = llmSoftmax(2)
    let ctx = NbBlock.toks(llmContext.map { nbTok($0) } + [nbTok("?", .hot)])
    let legend = [nbLegend(.blue, "Next-token probability"), nbLegend(.violet, "Most likely")]
    // Nucleus at p = 0.5 is the top token alone; at p = 0.9 it has to reach into the tail.
    var cum = 0.0
    var nucleus: [Int] = []
    for i in llmCands.indices { if cum < 0.5 { nucleus.append(i); cum += p1[i] } else { break } }
    let tailEach = tail1 / Double(llmVocab - llmCands.count)
    let need90 = Int64(llmCands.count) + Int64(((0.9 - p1.reduce(0, +)) / tailEach).rounded(.up))
    return [
        frame([.caption("context → one score per vocabulary entry"), ctx, .caption("top 8 of \(nbGrouped(Int64(llmVocab))) · softmax of illustrative logits"), bars(p1),
               .tiles([NbTile(big: nbF(p1[0] * 100, 0) + "%", caption: "on \"Paris\"", ink: .indigo), NbTile(big: nbF((1 - p1[0]) * 100, 0) + "%", caption: "spread over the other \(nbGrouped(Int64(llmVocab - 1)))")])], legend,
              "An LLM does exactly one thing.",
              "Given the tokens so far, score every token in the vocabulary as a candidate for the next one. Everything else is how you pick from this list."),
        frame([.caption("temperature T divides the logits before softmax"), table(["T", "P(Paris)", "tail mass"], [0.5, 0.7, 0.7], [
            NbRow(cells: [nbName("0.5"), nbCell(nbF(p05[0] * 100, 1) + "%", .indigo), nbCell("≈0%")]),
            NbRow(cells: [nbName("1.0"), nbCell(nbF(p1[0] * 100, 1) + "%", .indigo), nbCell(nbF(tail1 * 100, 1) + "%")]),
            NbRow(cells: [nbName("2.0"), nbCell(nbF(p2[0] * 100, 1) + "%", .indigo), nbCell(nbF(tail2 * 100, 1) + "%", .red)]),
        ]), bars(p2)], legend,
              "Temperature {sharpens or flattens} the list.",
              "At T = 0.5 \"Paris\" takes \(nbF(p05[0] * 100, 0))%; at T = 2 the long tail of unlikely tokens grows to \(nbF(tail2 * 100, 0))% and the output gets creative — or wrong."),
        frame([.caption("top-p (nucleus) sampling · p = 0.5"), .bars(llmCands.indices.map { NbBar(label: llmCands[$0], value: nbF(p1[$0] * 100, 1) + "%", frac: p1[$0] / p1.max()!, ink: nucleus.contains($0) ? .violet : .slate) }, labelWidth: 72)],
              [nbLegend(.violet, "Kept"), nbLegend(.slate, "Cut")],
              "Top-p keeps the {smallest set} covering p.",
              "At p = 0.5 that is \(nucleus.count) token\(nucleus.count == 1 ? "" : "s"). At p = 0.9 it must reach \(nbGrouped(need90)) tokens deep, because the remaining mass is spread thinly over the tail — the set adapts to the model's confidence, unlike a fixed top-k.",
              chips: [nbChip("p = 0.5", "\(nucleus.count)", true), nbChip("p = 0.9", nbGrouped(need90))]),
        frame([.caption("generation is the same step in a loop"), table(["step", "context ends …", "picked"], [0.4, 1.4, 0.6], [
            NbRow(cells: [nbCell("1"), nbCell("… France is"), nbCell("Paris", .indigo, bold: true)]),
            NbRow(cells: [nbCell("2"), nbCell("… is Paris"), nbCell(".", .indigo, bold: true)]),
            NbRow(cells: [nbCell("3"), nbCell("… Paris ."), nbCell("It", .indigo, bold: true)]),
        ], aligns: [0, 0, 2])], [], "Append the pick, {score again}.",
              "Each new token joins the context and the whole distribution is recomputed. A paragraph is a few hundred of these steps."),
        frame([.kv([("training", "make the true next token likely"), ("data", "trillions of tokens of text"), ("loss", "−log P(true next token)"), ("everything else", "instruction tuning, RLHF, sampling")])], [],
              "Training only ever {raises the right token's score}.",
              "The model sees text and is penalised by −log P of each actual next token. Knowledge, grammar and reasoning are whatever helps that prediction."),
    ]
}

// MARK: - BART

private let bartDoc = ["the", "cat", "sat", "on", "the", "mat", ".", "it", "purred", "loudly", "."]

private func bartFrames() -> [NbFrame] {
    let models = [
        NbModel(name: "BERT-large", sub: "fill blanks", enc: true, dec: false, frac: 340.0 / 774, value: "340M"),
        NbModel(name: "GPT-2 large", sub: "continue", enc: false, dec: true, frac: 1, value: "774M"),
        NbModel(name: "BART-large", sub: "both", enc: true, dec: true, frac: 400.0 / 774, value: "400M", ring: true),
    ]
    let overhead = 400.0 / 340 - 1
    let doc = NbBlock.toks(bartDoc.map { nbTok($0) })
    let legend = [nbLegend(.blue, "Encoder"), nbLegend(.violet, "Decoder")]
    let corruptLegend = [nbLegend(.yellow, "Corrupted"), nbLegend(.red, "Deleted")]
    let docCaption = NbBlock.caption("the original document · \(bartDoc.count) tokens")
    func corrupted(_ caption: String, _ toks: [NbTok], _ headline: String, _ body: String) -> NbFrame {
        frame([docCaption, doc, .caption(caption), .toks(toks)], corruptLegend, headline, body)
    }
    let infilled = [nbTok("the"), nbTok("[MASK]", .hot)] + bartDoc.dropFirst(4).map { nbTok($0) }
    return [
        frame([docCaption, doc, .caption("three shapes of pretrained transformer"), .models(models)], legend,
              "BART: corrupt the text, reconstruct the original.",
              "An encoder and a decoder, so one model fine-tunes for both classification and generation.",
              chips: [nbChip("over BERT-large", "+" + nbF(overhead * 100, 0) + "%", true)]),
        corrupted("1 · token masking", bartDoc.enumerated().map { i, w in i == 1 || i == 8 ? nbTok("[MASK]", .hot) : nbTok(w) },
                  "Token masking — {BERT's} corruption.",
                  "Random tokens become [MASK]. The decoder must regenerate the whole document, not just the masked slots."),
        corrupted("2 · token deletion", bartDoc.enumerated().filter { $0.offset != 2 && $0.offset != 9 }.map { nbTok($0.element) },
                  "Token deletion: {where} is the gap?",
                  "No [MASK] marks the spot. The model must work out which positions are missing as well as what goes there."),
        corrupted("3 · text infilling · span length ~ Poisson(3)", infilled,
                  "Text infilling: {three tokens}, one mask.",
                  "\"cat sat on\" is replaced by a single [MASK], so the model also predicts how many tokens are missing. This was BART's best single objective."),
        corrupted("4 · sentence permutation", (Array(bartDoc[7..<11]) + Array(bartDoc[0..<7])).map { nbTok($0) },
                  "Sentence permutation: {restore the order}.",
                  "The two sentences are shuffled. On its own this objective helped little; combined with infilling it was BART's final recipe."),
        corrupted("5 · document rotation", (Array(bartDoc.dropFirst(3)) + Array(bartDoc.prefix(3))).enumerated().map { nbTok($1, $0 == 0 ? .hot : .plain) },
                  "Document rotation: {find the start}.",
                  "The document is rotated to begin at a random token (\"on\"). The model must identify where the real document begins."),
        frame([.caption("encoder reads the corruption · decoder writes the original"), .toks([nbTok("the"), nbTok("[MASK]", .hot)] + bartDoc.dropFirst(4).map { nbTok($0, .blue) }, label: "enc"),
               .toks(bartDoc.map { nbTok($0, .pick) }, label: "dec")], legend,
              "The loss is over {every output token}.",
              "The encoder sees the corrupted text bidirectionally; the decoder reproduces the full original left to right, attending to the encoder. Cross-entropy on all \(bartDoc.count) tokens."),
        frame([.kv([("summarisation", "CNN/DM ROUGE-L 40.9 (state of the art, 2019)"), ("classification", "matches RoBERTa on GLUE"), ("translation", "encoder adapts a new source language"), ("shape", "encoder-decoder, like T5")])], [],
              "Denoising pretraining fits {generation tasks} best.",
              "Because the decoder is trained to write whole documents, BART was strongest on summarisation while staying competitive on understanding tasks."),
    ]
}

// MARK: - XLNet

private func xlnetFrames() -> [NbFrame] {
    let sent = ["New", "York", "is", "a", "city"]
    let masked = NbBlock.toks([nbTok("[MASK]", .hot), nbTok("[MASK]", .hot)] + sent.dropFirst(2).map { nbTok($0) })
    let fine = NbBlock.toks(sent.map { nbTok($0) })
    let legend = [nbLegend(.yellow, "[MASK] token")]
    let order = [3, 2, 5, 1, 4]
    func orderToks(_ upTo: Int) -> NbBlock { .toks(order.enumerated().map { k, p in nbTok(sent[p - 1], k < upTo ? .pick : k == upTo ? .hot : .dim, "#\(k + 1)") }) }
    let york = order.firstIndex(of: 2)!, newIdx = order.firstIndex(of: 1)!
    let ctxLegend = [nbLegend(.violet, "Visible context"), nbLegend(.yellow, "Being predicted")]
    return [
        frame([.caption("the same sentence, two moments"), .caption("pretraining input · BERT"), masked, .caption("fine-tuning input"), fine,
               .tiles([NbTile(big: "15%", caption: "positions masked in pretraining", ink: .yellow), NbTile(big: "0%", caption: "at fine-tuning time")]),
               .caption("and the masked pair is predicted independently"),
               table([], [1.2, 0.7], [NbRow(cells: [nbName("P(New | is a city)"), nbCell("guessed alone")]), NbRow(cells: [nbName("P(York | is a city)"), nbCell("guessed alone")])])], legend,
              "XLNet starts from two complaints about BERT.",
              "[MASK] appears in pretraining and never downstream, and two masked words can't inform each other — \"York\" never conditions on \"New\"."),
        frame([.caption("one sampled factorisation order"), .toks(order.enumerated().map { k, p in nbTok(sent[p - 1], .plain, "#\(k + 1)") })],
              [nbLegend(.violet, "Already predicted"), nbLegend(.yellow, "Being predicted")],
              "Predict the words in {a random order}.",
              "Positions stay where they are; only the order of prediction is shuffled. This order is is → York → city → New → a."),
        frame([.caption("predicting \"York\" (#\(york + 1) in this order)"), orderToks(york)], ctxLegend,
              "\"York\" sees only {what came earlier in the order}.",
              "Here that is just \"is\". In another order \"New\" comes first and \"York\" conditions on it — something BERT's independent guesses never do."),
        frame([.caption("predicting \"New\" (#\(newIdx + 1) in this order)"), orderToks(newIdx)], ctxLegend,
              "\"New\" sees {both sides} — York and city.",
              "Over many sampled orders every word sees every subset of the others, so the model learns bidirectional context with no [MASK] token at all.",
              chips: [nbChip("orders of 5 words", "\((1...5).reduce(1, *))", true)]),
        frame([.caption("two-stream attention"), .kv([("content stream", "knows the word and its position"), ("query stream", "knows the position only"), ("why", "predicting a word must not see it"), ("result", "same weights, two views")])], [],
              "The target position must {know where, not what}.",
              "A standard transformer state includes the token itself. XLNet adds a query stream that carries position but hides content, so the word can't predict itself."),
        frame([table(["", "BERT", "XLNet"], [0.9, 1, 1], [
            NbRow(cells: [nbName("[MASK] mismatch"), nbCell("yes", .red), nbCell("no", .green)]),
            NbRow(cells: [nbName("masked words"), nbCell("independent", .red), nbCell("conditioned", .green)]),
            NbRow(cells: [nbName("training cost"), nbCell("lower", .green), nbCell("higher", .red)]),
        ])], [], "Better on paper, {costlier in practice}.",
              "XLNet beat BERT on 20 tasks in 2019, but RoBERTa matched it by simply training BERT longer on more data — and that simpler recipe won out."),
    ]
}

// MARK: - GPT-3 and GPT-4

private func gptFrames() -> [NbFrame] {
    let models: [(String, Double, Double)] = [("GPT-3", 175, 300), ("Chinchilla", 70, 1400), ("LLaMA-1", 65, 1400), ("LLaMA-2", 70, 2000), ("LLaMA-3", 70, 15000)]
    let logMax = log10(15000.0)
    func tbl(_ ring: String?) -> NbBlock {
        table(["", "params (B)", "tokens (B)", "tok/p"], [0.9, 0.9, 0.9, 0.4], models.map { m in
            NbRow(cells: [nbName(m.0), nbCell(nbGrouped(Int64(m.1))), nbCell(nbGrouped(Int64(m.2))), nbCell(nbF(m.2 / m.1, m.2 / m.1 < 10 ? 1 : 0), m.0 == ring ? .yellow : nil, bold: true)], ring: m.0 == ring)
        })
    }
    func bars(_ ring: String?) -> NbBlock {
        .bars(models.flatMap { m in [
            NbBar(label: m.0, value: nbGrouped(Int64(m.1)) + "B p", frac: log10(m.1) / logMax, ink: .blue, labelInk: m.0 == ring ? .yellow : nil),
            NbBar(label: "", value: nbGrouped(Int64(m.2)) + "B t", frac: log10(m.2) / logMax, ink: .violet),
        ] }, labelWidth: 88)
    }
    let legend = [nbLegend(.blue, "Parameters"), nbLegend(.violet, "Training tokens")]
    let g = models[0]
    let flops = models.map { 6 * $0.1 * 1e9 * $0.2 * 1e9 }
    return [
        frame([.caption("parameters vs training tokens · log scale"), bars("GPT-3")], legend, "GPT-3 changed scale, not shape.",
              "The same decoder-only design as GPT-2 at 175B parameters — and it did tasks from a few in-prompt examples, no gradient update. Later models trained far longer per parameter.",
              fx: ["GPT-3: 175B params · 300B tokens · {\(nbF(g.2 / g.1, 1))} tokens/param"]),
        frame([.caption("tokens per parameter"), tbl("Chinchilla"), .callout(["compute-optimal ≈ {20} tokens per parameter"])], legend, "Chinchilla: GPT-3 was {undertrained}.",
              "For a fixed compute budget, a 70B model on 1.4T tokens beat much larger ones. The ratio jumped from 1.7 to 20."),
        frame([.caption("training compute ≈ 6 · N · D"), table(["", "FLOPs"], [1, 1], models.indices.map { NbRow(cells: [nbName(models[$0].0), nbCell(lsSci(flops[$0]), $0 == 0 ? .yellow : nil)]) })], [],
              "Compute is {6 × parameters × tokens}.",
              "Each token costs about 6 FLOPs per parameter (forward and backward). LLaMA-3 70B used \(nbF(flops[4] / flops[0], 0))× GPT-3's compute — spent on data, not size."),
        frame([.caption("in-context learning · GPT-3 175B, TriviaQA"), .bars([
            NbBar(label: "zero-shot", value: "64.3", frac: 0.643, ink: .indigo), NbBar(label: "one-shot", value: "68.0", frac: 0.680, ink: .indigo), NbBar(label: "few-shot", value: "71.2", frac: 0.712, ink: .violet),
        ], labelWidth: 84)], [],
              "Examples in the prompt, {no fine-tuning}.",
              "GPT-3's headline result: accuracy rises with the number of demonstrations in the prompt, with the weights frozen (Brown et al., 2020)."),
        frame([.kv([("GPT-4 (2023)", "size and data undisclosed"), ("inputs", "text and images"), ("alignment", "RLHF on top of pretraining"), ("exams", "e.g. bar exam ~90th percentile")])], [],
              "GPT-4: {details withheld}, capability measured.",
              "OpenAI published benchmark results but not parameter or token counts. The scaling story is now told through evaluations rather than architecture."),
    ]
}
