import Foundation

// Port of NlpModelFrames.kt: LLaMA, Mixtral, Claude & Gemini, RAG, prompting, chain of thought, Tree of
// Thoughts, vector databases, ReAct, agents, hallucination, the feed-forward block and positional
// encodings. The prompting, reasoning, retrieval, agent and calibration numbers come from
// ModernLlmMath.swift; the rest is computed here with the same seeds as Android.

let nlpModelTopicIds: Set<String> = [
    "llama_vicuna", "mistral_mixtral", "claude_gemini", "rag", "prompt_engineering", "chain_of_thought",
    "tree_of_thoughts", "vector_databases", "react", "ai_agents", "hallucination_mitigation", "feed_forward",
    "positional_encodings",
]

func nlpModelLab(_ topicId: String) -> [NbFrame]? {
    switch topicId {
    case "llama_vicuna": llamaFrames()
    case "mistral_mixtral": mixtralFrames()
    case "claude_gemini": frontierFrames()
    case "rag": ragFrames()
    case "prompt_engineering": promptFrames()
    case "chain_of_thought": cotFrames()
    case "tree_of_thoughts": totFrames()
    case "vector_databases": vectorDbFrames()
    case "react": reactFrames()
    case "ai_agents": agentFrames()
    case "hallucination_mitigation": hallucinationFrames()
    case "feed_forward": ffnFrames()
    case "positional_encodings": positionalFrames()
    default: nil
    }
}

private func f2(_ v: Double) -> String { nbF(v, 2) }
private func pct(_ v: Double, _ d: Int = 0) -> String { nbF(v * 100, d) + "%" }

private func frame(_ blocks: [NbBlock], _ legend: [NbLegend], _ headline: String, _ body: String, chips: [DkChip] = [], fx: [String] = []) -> NbFrame {
    NbFrame(blocks: blocks, legend: legend, headline: headline, body: body, chips: chips, fx: fx)
}

private func table(_ headers: [String], _ weights: [CGFloat], _ rows: [NbRow], aligns: [Int]? = nil) -> NbBlock {
    .table(headers: headers, weights: weights, rows: rows, aligns: aligns)
}

private struct ModelRng {
    var s: Int64
    init(_ seed: Int64) { s = seed }
    mutating func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
    mutating func g() -> Double { let a = max(u(), 1e-12); let b = u(); return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b) }
}

// MARK: - LLaMA & Vicuna

private func llamaFrames() -> [NbFrame] {
    let models: [(String, Double, Double)] = [("LLaMA-1 65B", 65e9, 1.4e12), ("LLaMA-3 70B", 70e9, 15e12), ("Mistral 7B", 7.3e9, 8e12)]
    func frac(_ ratio: Double) -> Double { log10(ratio) / 4 }
    let ratioBars = NbBlock.bars(models.map { NbBar(label: $0.0, value: nbGrouped(Int64(($0.2 / $0.1).rounded())), frac: frac($0.2 / $0.1), ink: .sky) },
                                 ticks: [(0, "1"), (0.25, "10"), (0.5, "100"), (0.75, "1k"), (1, "10k")], ref: (frac(20), "20 · Chinchilla"), labelWidth: 104)
    let multiples = models.map { nbF($0.2 / $0.1 / 20, 1) + "×" }.joined(separator: " · ")
    let small = (7e9, 8e12), big = (70e9, 1.4e12)
    func serve(_ m: (Double, Double)) -> Double { 2 * m.0 }
    func train(_ m: (Double, Double)) -> Double { 6 * m.0 * m.1 }
    let served = [1e11, 1e12, 1e13]
    let lifetime = served.map { s in (train(small) + serve(small) * s, train(big) + serve(big) * s) }
    let top = lifetime.map { max($0.0, $0.1) }.max()!
    let serveBars = NbBlock.bars([NbBar(label: "7B", value: lsSci(serve(small)), frac: serve(small) / serve(big), ink: .pink), NbBar(label: "70B", value: lsSci(serve(big)), frac: 1, ink: .pink)], labelWidth: 48)
    let boxes = NbBlock.boxes([NbBox(title: "training", lines: ["paid once", "6·N·D"], lastInk: .pink), NbBox(title: "inference", lines: ["paid per token", "2·N"], lastInk: .pink)])
    let lastRatio = lifetime.last!.1 / lifetime.last!.0
    return [
        frame([.caption("tokens per parameter · log scale"), ratioBars, .callout(["vs compute-optimal: \(multiples)"])], [],
              "LLaMA's thesis inverts Chinchilla's.",
              "Chinchilla optimises the training budget. LLaMA assumes the model will be served millions of times, so it trains a smaller model far past compute-optimal."),
        frame([.caption("inference FLOPs per token = 2N"), serveBars, boxes, .callout(["D (training tokens) is absent from the serving cost"])], [],
              "The payoff shows up at serving time.",
              "A 7B model trained on 8T tokens costs \(lsSci(serve(small))) FLOPs per token to run; a 70B model costs 10× more, forever. Extra training is paid once."),
        frame([.caption("lifetime FLOPs = 6·N·D + 2·N·served"), table(["tokens served", "7B · 8T", "70B · 1.4T"], [1, 0.8, 0.8], served.indices.map { i in
            let (a, b) = lifetime[i]
            return NbRow(cells: [nbName(lsSci(served[i])), nbCell(lsSci(a), a < b ? .green : nil), nbCell(lsSci(b), b < a ? .green : nil)])
        }), .bars(served.indices.map { NbBar(label: lsSci(served[$0]), value: nbF(lifetime[$0].1 / lifetime[$0].0, 1) + "×", frac: lifetime[$0].1 / top, ink: .pink) }, labelWidth: 64)],
              [nbLegend(.green, "Cheaper overall")],
              "The more it is served, {the more the small model wins}.",
              "Training the 7B model longer costs \(lsSci(train(small))) FLOPs against \(lsSci(train(big))) for the 70B, and every served token is 10× cheaper. At \(lsSci(served.last!)) tokens served the 70B costs \(nbF(lastRatio, 1))× as much in total."),
        frame([.caption("Vicuna · LLaMA-13B fine-tuned on chats"), .kv([("data", "~70K ShareGPT conversations"), ("training cost", "about $300 (reported)"), ("evaluation", "GPT-4 as judge"), ("claim", "~90% of ChatGPT quality (judged)")])], [],
              "Open weights made {cheap fine-tunes} possible.",
              "Vicuna took LLaMA's weights and fine-tuned on shared ChatGPT conversations. Its evaluation used GPT-4 as a judge — a method that later research found flatters chat-style answers."),
        frame([.kv([("LLaMA-1 (2023)", "research licence, 7B–65B"), ("LLaMA-2", "commercial use, chat models"), ("LLaMA-3", "15T tokens, 8B–405B"), ("effect", "Alpaca, Vicuna, a fine-tuning ecosystem")])], [],
              "Small, overtrained, open: {the LLaMA recipe}.",
              "Each release pushed tokens per parameter higher. The models are cheap to run, so they became the base for most open fine-tuning work."),
    ]
}

// MARK: - Mixtral

private func softmax(_ xs: [Double]) -> [Double] {
    let m = xs.max()!
    let e = xs.map { exp($0 - m) }
    let z = e.reduce(0, +)
    return e.map { $0 / z }
}

private func mixtralFrames() -> [NbFrame] {
    let experts = (0..<8).map { "E\($0)" }
    // Illustrative router logits for two tokens.
    let theLogits = [-0.6, 0.53, 2.8, 0.2, 0.53, 2.5, -1.7, 1.15]
    let parisLogits = [2.1, -0.4, 0.3, 1.0, 2.6, -0.8, 0.5, 0.1]
    func route(_ l: [Double]) -> ([Double], [Int], [Double]) {
        let p = softmax(l)
        let top = Array(p.indices.sortedByDescending { p[$0] }.prefix(2)).sorted()
        let s = top.reduce(0.0) { $0 + p[$1] }
        return (p, top, top.map { p[$0] / s })
    }
    let (pThe, topThe, wThe) = route(theLogits)
    let (pParis, topParis, wParis) = route(parisLogits)
    func cols(_ p: [Double], _ top: [Int]) -> NbBlock { .columns(values: p, labels: experts, hot: Set(top), top: p.max()!) }
    func mix(_ top: [Int], _ w: [Double]) -> String { "y = {\(f2(w[0]))}·E\(top[0])(x) + {\(f2(w[1]))}·E\(top[1])(x)" }
    var r = ModelRng(17)
    var load = [Int](repeating: 0, count: 8)
    for _ in 0..<64 {
        var l: [Double] = []
        for i in 0..<8 { l.append(r.g() + (i == 2 ? 0.9 : 0)) }
        let p = softmax(l)
        for i in p.indices.sortedByDescending({ p[$0] }).prefix(2) { load[i] += 1 }
    }
    let busiest = load.indices.max { load[$0] < load[$1] }!
    let legend = [nbLegend(.violet, "Chosen (top 2)"), nbLegend(.slate, "Not run")]
    return [
        frame([.caption("one transformer block, FFN replaced"), .kv([("attention", "shared by every token"), ("FFN", "8 expert FFNs + a router"), ("per token", "router picks 2 experts"), ("Mixtral 8×7B", "47B total, ~13B active")])], [],
              "Mixtral swaps the FFN for {eight experts}.",
              "Attention stays shared. Each feed-forward layer becomes eight separate FFNs, and a small router decides which two each token uses."),
        frame([.caption("router softmax for \"the\"", note: "top-2 of 8"), cols(pThe, topThe), .callout([mix(topThe, wThe), "\(f2(pThe[topThe[0]])), \(f2(pThe[topThe[1]])) renormalised over the kept pair"]),
               .callout(["active ≈ 13B of 47B parameters per token"])], legend,
              "A router scores all 8 experts; the top 2 run.",
              "Their weights are renormalised and mix the two outputs. Routing is per token and per layer — the next token usually goes elsewhere."),
        frame([.caption("router softmax for \"Paris\"", note: "top-2 of 8"), cols(pParis, topParis), .callout([mix(topParis, wParis)])], legend,
              "A different token, {different experts}.",
              "\"Paris\" goes to E\(topParis[0]) and E\(topParis[1]). Experts end up specialising loosely — on syntax, on token types — rather than on clean topics."),
        frame([.caption("tokens routed to each expert · batch of 64"), .columns(values: load.map(Double.init), labels: experts, hot: [busiest], top: Double(load.max()!)),
               .callout(["ideal: \(64 * 2 / 8) per expert · busiest: {\(load.max()!)}"])], [nbLegend(.violet, "Overloaded expert")],
              "Left alone, the router {plays favourites}.",
              "A slight bias toward E2 sends it \(load.max()!) of 128 slots against an even \(64 * 2 / 8). Training adds a load-balancing loss so every expert keeps learning and no device sits idle."),
        frame([.caption("parameters stored vs used per token"), .bars([
            NbBar(label: "stored", value: "47B", frac: 1, ink: .grey), NbBar(label: "active", value: "13B", frac: 13.0 / 47, ink: .violet), NbBar(label: "dense 13B", value: "13B", frac: 13.0 / 47, ink: .sky),
        ], labelWidth: 84), .tiles([NbTile(big: "3.6×", caption: "more knowledge stored", ink: .indigo), NbTile(big: "1×", caption: "compute of a 13B")])],
              [nbLegend(.violet, "Mixtral, per token")],
              "Big model's memory, {small model's compute}.",
              "Every expert must sit in GPU memory, but each token only pays for two. Mixtral matched or beat LLaMA-2 70B while running like a 13B."),
        frame([.caption("Mistral 7B · sliding-window attention"), .tiles([NbTile(big: "4,096", caption: "tokens each layer attends to", ink: .indigo), NbTile(big: "32", caption: "layers"), NbTile(big: "131K", caption: "reach after 32 layers", ink: .green)])], [],
              "Mistral 7B: each layer looks back {4,096 tokens}.",
              "Information hops one window per layer, so 32 layers reach 32 × 4,096 ≈ 131K tokens back while each layer's attention cost stays fixed."),
        frame([.kv([("Mistral 7B", "dense, sliding window, GQA"), ("Mixtral 8×7B", "sparse MoE, top-2 routing"), ("cost", "memory for all experts"), ("benefit", "quality per FLOP")])], [],
              "Sparsity trades {memory for compute}.",
              "Mixture-of-experts is now standard in large models: parameters scale with experts, per-token cost with the number chosen."),
    ]
}

// MARK: - Claude & Gemini

private func frontierFrames() -> [NbFrame] {
    let windows: [(String, Double)] = [("Claude Opus", 1e6), ("Claude Sonnet", 1e6), ("Gemini", 1e6), ("GPT-4-class", 128e3), ("LLaMA", 128e3)]
    func wf(_ v: Double) -> Double { (log10(v) - 3) / 3 }
    func short(_ v: Double) -> String { v >= 1e6 ? nbF(v / 1e6, 0) + "M" : nbF(v / 1e3, 0) + "K" }
    let windowBars = NbBlock.bars(windows.map { NbBar(label: $0.0, value: short($0.1), frac: wf($0.1), ink: $0.1 >= 1e6 ? .violet : .sky) },
                                  ticks: [(0, "1K"), (1.0 / 3, "10K"), (2.0 / 3, "100K"), (1, "1M")], labelWidth: 104)
    let artifacts: [(String, Double)] = [("an email", 500), ("a long paper", 15e3), ("a novel", 120e3), ("a mid-size codebase", 800e3)]
    let pairs1 = 128e3 * 128e3, pairs2 = 1e6 * 1e6
    return [
        frame([.caption("two frontier families"), table(["", "Claude", "Gemini"], [0.8, 1, 1], [
            NbRow(cells: [nbName("maker"), nbCell("Anthropic"), nbCell("Google DeepMind")]),
            NbRow(cells: [nbName("tiers"), nbCell("Opus · Sonnet · Haiku"), nbCell("Pro · Flash")]),
            NbRow(cells: [nbName("inputs"), nbCell("text, images, PDFs"), nbCell("text, images, audio, video")]),
            NbRow(cells: [nbName("weights"), nbCell("closed"), nbCell("closed")]),
        ])], [], "Two families, {tiers by size and price}.",
              "Both ship a large, a medium and a small model. Architectures and training data are not published, so comparisons are made on behaviour."),
        frame([.caption("context window · log scale"), windowBars, .callout(["1M tokens ≈ a large codebase, no chunking"])], [nbLegend(.violet, "1M-token window"), nbLegend(.sky, "128K")],
              "Context is the headline number — and a real capability.",
              "A million tokens puts a codebase or hundreds of thousands of words straight into the prompt. GPT-4 set the 128K bar open models still target."),
        frame([.caption("what fits · tokens, rough"), .bars(artifacts.map { NbBar(label: $0.0, value: $0.1 < 1e3 ? "\(Int($0.1))" : short($0.1), frac: (log10($0.1) - 2) / 4, ink: $0.1 <= 128e3 ? .sky : .violet) }, labelWidth: 128)],
              [nbLegend(.sky, "Fits in 128K"), nbLegend(.violet, "Needs 1M")],
              "A novel fits in 128K; {a codebase needs 1M}.",
              "Long context replaces some retrieval: instead of choosing which chunks to show the model, show it everything."),
        frame([.caption("self-attention pairs grow with the square"), .tiles([NbTile(big: lsSci(pairs1), caption: "pairs at 128K"), NbTile(big: lsSci(pairs2), caption: "pairs at 1M", ink: .red), NbTile(big: nbF(pairs2 / pairs1, 0) + "×", caption: "more work", ink: .yellow)])], [],
              "8× the context, {\(nbF(pairs2 / pairs1, 0))×} the attention.",
              "Naive attention is quadratic, so million-token windows depend on memory-efficient kernels and caching. Long prompts are also slower and costlier per call."),
        frame([.caption("needle in a haystack"), .kv([("test", "hide one fact in a long document"), ("ask", "retrieve it"), ("measures", "recall by depth and length"), ("doesn't measure", "reasoning across the whole context")])], [],
              "Having a long window ≠ {using it well}.",
              "Both families report near-perfect needle retrieval. Tasks that need many facts combined from across the context are harder and less often reported."),
        frame([.kv([("pick by", "task, latency and price tier"), ("long documents", "1M-context models"), ("cheap volume", "Haiku / Flash"), ("hardest reasoning", "Opus / Pro")])], [],
              "Choose the tier, {then the family}.",
              "Within a family the small model is several times cheaper and faster. For most applications that choice matters more than which lab made it."),
    ]
}

// MARK: - RAG

private let ragDocs = [
    "The refund window is 45 days from delivery for unused items.",
    "Shipping is free on orders over 50 dollars within the country.",
    "Gift cards cannot be refunded or exchanged for cash.",
    "Support is available by chat from 9am to 6pm on weekdays.",
    "Damaged items can be returned for a full refund at any time.",
]
private let ragStop: Set<String> = ["the", "is", "are", "a", "an", "of", "for", "on", "or", "be", "can", "to", "by", "from", "at", "any", "what", "within", "over", "in", "cannot"]

private func ragTokens(_ s: String) -> [String] {
    s.lowercased().components(separatedBy: CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyz0123456789").inverted)
        .filter { !$0.isEmpty && !ragStop.contains($0) }
        .map { $0.hasSuffix("s") && $0.count > 3 ? String($0.dropLast()) : $0 }
}

private func ragScores(_ query: String) -> [Double] {
    let docs = ragDocs.map(ragTokens)
    var df: [String: Int] = [:]
    for d in docs { for w in Set(d) { df[w, default: 0] += 1 } }
    func vec(_ t: [String]) -> [String: Double] {
        var c: [String: Int] = [:]
        for w in t { c[w, default: 0] += 1 }
        return c.mapValues { Double($0) }.reduce(into: [:]) { acc, kv in acc[kv.key] = kv.value * (log((Double(docs.count) + 1) / (Double(df[kv.key] ?? 0) + 1)) + 1) }
    }
    let q = vec(ragTokens(query))
    return docs.map { d in
        let v = vec(d)
        let dot = q.keys.filter { v[$0] != nil }.reduce(0.0) { $0 + q[$1]! * v[$1]! }
        let n = q.values.reduce(0.0) { $0 + $1 * $1 }.squareRoot() * v.values.reduce(0.0) { $0 + $1 * $1 }.squareRoot()
        return n == 0 ? 0 : dot / n
    }
}

private func ragFrames() -> [NbFrame] {
    let query = "What is the refund window?"
    let steps = ["Query", "Embed", "Search", "Augment", "Generate"]
    func pipe(_ i: Int) -> NbBlock { .pipeline(steps: steps, current: i, done: i) }
    let scores = ragScores(query)
    let order = scores.indices.sortedByDescending { scores[$0] }
    let top2 = Array(order.prefix(2))
    let qBox = NbBlock.text([NbSpan(t: query)])
    let miss = "Can I get my money back?"
    let missScores = ragScores(miss)
    return [
        frame([.caption("query"), qBox, .caption("pipeline"), pipe(0), .caption("closed-book answer"),
               .callout(["\"Refunds are accepted within 30 days.\"", "{fluent · unsourced · possibly outdated}"])], [],
              "The question is about a private, changeable policy.",
              "The weights can't be trusted here: the answer was never in training data, or it has since changed."),
        frame([.caption("query"), qBox, pipe(1), .caption("query terms after stop words · TF-IDF weighted"), .toks(ragTokens(query).map { nbTok($0, .pick) })], [],
              "Embed: turn the query into {a vector}.",
              "Here a TF-IDF vector over the \(ragDocs.count)-document store; production systems use a dense embedding model. Either way query and documents live in the same space."),
        frame([pipe(2), .caption("cosine to every stored chunk"), table(["chunk", "score"], [1.8, 0.4], order.map { i in
            NbRow(cells: [nbProse(ragDocs[i]), nbCell(f2(scores[i]), top2.contains(i) ? .green : nil, bold: true)], ring: i == order[0])
        }, aligns: [0, 2])], [nbLegend(.green, "Top 2 kept")],
              "Search: rank every chunk, {keep the top k}.",
              "The policy chunk wins with \(f2(scores[order[0]])); \"damaged items … full refund\" shares only the word refund. k = 2 keeps one more for context; everything else stays out of the prompt."),
        frame([pipe(3), .caption("the prompt the model actually sees"), .callout(["Answer using only the sources."] + top2.enumerated().map { "[\($0 + 1)] \(ragDocs[$1])" } + ["Q: \(query)"])], [],
              "Augment: {paste the evidence} into the prompt.",
              "The model is not retrained. Retrieval only changes what is in its context window — which is why updating a document updates the answer immediately."),
        frame([pipe(4), .caption("grounded answer"), .callout(["\"The refund window is {45 days} from delivery, for unused items [1].\""]),
               .tiles([NbTile(big: "30 → 45", caption: "days, closed-book vs grounded", ink: .green), NbTile(big: "[1]", caption: "citation a user can check", ink: .indigo)])], [],
              "Generate: the answer {cites its source}.",
              "The closed-book 30 was a plausible guess; the store says 45. The citation makes the claim checkable, and wrong answers traceable to a document."),
        frame([.caption("\"\(miss)\" · keyword retrieval"), table(["chunk", "score"], [1.8, 0.4], Array(missScores.indices.sortedByDescending { missScores[$0] }.prefix(3)).map { i in
            NbRow(cells: [nbProse(ragDocs[i]), nbCell(f2(missScores[i]), .red, bold: true)])
        }, aligns: [0, 2])], [],
              "Same question, other words: {retrieval misses}.",
              "\"money back\" shares no term with \"refund\", so every keyword score is \(f2(missScores.max()!)). Dense embeddings, hybrid search and query rewriting exist for exactly this — RAG is only as good as its retriever."),
    ]
}

// MARK: - Prompt engineering

private func promptFrames() -> [NbFrame] {
    typealias L = PromptLab
    let pool = L.pool
    func poolBlock(_ used: Int) -> NbBlock { .toks(pool.enumerated().map { i, w in nbTok("\(w) → \(L.label(w))", i < used ? .plain : .empty) }, columns: 3, mono: true) }
    func shortName(_ n: String) -> String {
        let base = n.components(separatedBy: " letter").first!
        return base.replacingOccurrences(of: "most frequent", with: "freq").replacingOccurrences(of: "alphabetically first", with: "alpha")
    }
    func rulesBlock(_ demos: [String]) -> [NbBlock] {
        let alive = Set(L.consistent(demos).map(\.name))
        let p = L.prediction(demos).sortedByDescending { $0.1 }
        return [
            .caption("rules consistent with \"word → one letter\"", note: "\(alive.count) fit"),
            .toks(L.rules.map { r in nbTok(shortName(r.name), alive.contains(r.name) ? .blue : .bad, alive.contains(r.name) ? f2(1.0 / Double(alive.count)) : "out") }, columns: 5),
            .caption("answer for \"\(L.query)\"", note: "correct: \(L.label(L.query))"),
            .bars(p.map { NbBar(label: "'\($0.0)'", value: f2($0.1), frac: $0.1, ink: $0.0 == L.label(L.query) ? .green : .slate) }, labelWidth: 40),
        ]
    }
    func make(_ k: Int, _ headline: String, _ body: String) -> NbFrame {
        frame([.caption("demonstration pool", note: "\(k) of \(pool.count) used"), poolBlock(k)] + rulesBlock(Array(pool.prefix(k))),
              [nbLegend(.blue, "Still consistent"), nbLegend(.green, "Correct answer")], headline, body)
    }
    let curve = L.eliminationCurve()
    let pairs = L.identifyingPairs().count, all = L.allPairs().count
    let instruction = L.instructionEliminates()
    let worth = L.demosToEliminate(instruction)
    let zeroShot = L.prediction([]).first { $0.0 == L.label(L.query) }?.1 ?? 0
    return [
        make(0, "A prompt is an induction problem.",
             "Five simple rules all fit \"word → one letter\". Zero-shot asks the model to guess which one you meant — here it picks the right one \(pct(zeroShot)) of the time."),
        make(1, "One demonstration: {banana → n}.",
             "\"banana\" rules out first and last letter. \(curve[1].1) rules remain, and they still disagree about \"kayak\"."),
        make(2, "Two demonstrations: {\(curve[2].1) rules} left.",
             "\"adage\" agrees with the true rule but also with others. More examples of the same kind add little."),
        make(3, "Three demonstrations, {still ambiguous}.",
             "The examples a person reaches for first all have the same letter in several positions. \(curve[3].1) hypotheses survive."),
        make(4, "\"level\" settles it: {one rule left}.",
             "One well-chosen example removes what three ordinary ones couldn't. The answer for \"kayak\" is now certain: \(L.label(L.query))."),
        frame([.caption("pairs of demonstrations from the pool"), .tiles([NbTile(big: "\(pairs)", caption: "pairs that identify the rule", ink: .green), NbTile(big: "\(all)", caption: "possible pairs")])], [],
              "Which examples matters {more than how many}.",
              "Only \(pairs) of the \(all) two-example prompts pin the rule down. Few-shot prompting is choosing informative demonstrations."),
        frame([.caption("instruction: \"a letter from the middle of the word\""), .toks(instruction.map { nbTok($0.name, .bad) }), .tiles([NbTile(big: "\(worth)", caption: "demonstrations it replaces", ink: .indigo)])], [],
              "An instruction is {worth \(worth) demonstrations} here.",
              "Saying what you want removes \"first\" and \"last\" before any example — the same work as \(worth) demonstrations from the pool, at a fraction of the tokens."),
    ]
}

// MARK: - Chain of thought

private func cotFrames() -> [NbFrame] {
    typealias L = CotLab
    let q = L.directAccuracy, p = L.stepAccuracy
    let n = L.breakEvenSteps()
    func plot(_ highlight: Int?) -> NbBlock {
        .plot(NbPlotBlock(xr: (1, 10), yr: (0, 1), height: 170,
                          lines: [NbLine(pts: (1...10).map { NbP(Double($0), L.chainAccuracy($0)) }, ink: .sky, width: 2.2, dots: true),
                                  NbLine(pts: [NbP(1, q), NbP(10, q)], ink: .yellow, dashed: true, width: 1.6)],
                          dots: highlight.map { [NbDot(p: NbP(Double($0), L.chainAccuracy($0)), ink: .sky, r: 5, ring: true, label: nbF(L.chainAccuracy($0), 3))] } ?? [],
                          xTicks: (1...10).map { (Double($0), "\($0)") }, yTicks: [(0, "0.0"), (0.5, "0.5"), (1, "1.0")],
                          shade: (Double(n) + 0.5, 10, "")))
    }
    let legend = [nbLegend(.sky, "Chain pⁿ", .dot), nbLegend(.yellow, "Direct guess q", .dot), nbLegend(.pink, "Chain loses")]
    let head = NbBlock.caption("accuracy by chain length n", note: "p = \(f2(p)) per step")
    let spread = L.votingCurve(0.6, 4), fixed = L.votingCurve(0.4, 1)
    return [
        frame([head, plot(1), .callout(["one step: p = {\(f2(p))} vs direct q = \(f2(q))"])], legend,
              "Each small step is {easier than the whole}.",
              "Answering in one shot is right \(pct(q)) of the time. Broken into steps, each is right \(pct(p)) — but all of them have to be right."),
        frame([head, plot(n), .callout(["break-even at n = \(n)"])], legend,
              "The curves cross at \(n) steps.",
              "At \(n), decomposing still wins (\(nbF(L.chainAccuracy(n), 3)) vs \(f2(q))). Past it, each step is another chance to be wrong. \"Think step by step\" only helps while pⁿ stays above q."),
        frame([head, plot(10)], legend, "Ten steps: {\(nbF(L.chainAccuracy(10), 3))} — worse than guessing.",
              "Errors compound multiplicatively. Long chains need either more reliable steps or a way to catch mistakes."),
        frame([.caption("self-consistency · sample k chains, majority vote"), table(["k", "wrong answers scattered", "wrong answers agree"], [0.3, 1, 1], spread.indices.map { i in
            NbRow(cells: [nbName("\(spread[i].0)"), nbCell(f2(spread[i].1), .green), nbCell(f2(fixed[i].1), .red)])
        })], [nbLegend(.green, "p = 0.6, errors spread over 4"), nbLegend(.red, "p = 0.4, one shared error")],
              "Voting helps {when errors disagree}.",
              "With p = 0.6 and scattered errors, 9 votes reach \(f2(spread.last!.1)). When the wrong chains all land on the same answer, voting entrenches it."),
        frame([.caption("exact, not simulated"), .kv([("chain", "P(all n right) = pⁿ"), ("direct", "q"), ("vote", "multinomial over k samples"), ("ties", "counted as failures")])], [],
              "Two formulas {explain most of CoT}.",
              "pⁿ says when to decompose; the plurality vote says when sampling more helps. Both assume steps fail independently — real errors correlate, which is the next lab's problem."),
        frame([.kv([("helps", "multi-step arithmetic, logic"), ("hurts", "long chains of shaky steps"), ("fix 1", "self-consistency voting"), ("fix 2", "search over chains (Tree of Thoughts)")])], [],
              "Chain of thought is {one path}; search explores many.",
              "When a single chain is likely to go wrong somewhere, keeping several partial chains alive and evaluating them is the next step."),
    ]
}

// MARK: - Tree of Thoughts

private func totFrames() -> [NbFrame] {
    typealias L = TotLab
    let root = L.puzzle
    let succ = L.successors(root)
    func exprFor(_ state: [Double]) -> String {
        let s = succ.first { L.label($0.state) == L.label(state) }!
        return "\(s.expression)=\(L.label([s.result]))".replacingOccurrences(of: " ", with: "")
    }
    let ranked = L.rankedFrontier(depth: 1)
    let greedy = L.beam(1)
    let width = L.widthNeeded(1), width2 = L.widthNeeded(2)
    let wide = L.beam(width)
    let (expanded, solutions) = L.exhaustive()
    let (firstRank, _, frontierSize) = L.evaluatorQuality(width)
    let top3 = Array(ranked.prefix(3))
    let solutionText = "(10−4)×(13−9)=24"
    func tree(_ chain: Int) -> NbBlock {
        var nodes = [NbNode(x: 0.5, y: 0.1, text: L.label(root), tone: .blue)]
        var edges: [NbEdge] = []
        for (i, t) in top3.enumerated() {
            nodes.append(NbNode(x: 0.18 + 0.32 * CGFloat(i), y: 0.32, text: exprFor(t.state), tone: i == 0 ? .hot : .plain, below: "h \(nbF(t.score, 1))", belowInk: i == 0 ? .yellow : nil))
            edges.append(NbEdge(a: 0, b: nodes.count - 1))
        }
        var parent = 1
        for (k, step) in greedy.trace.dropFirst().prefix(chain).enumerated() {
            let last = k == greedy.trace.count - 2
            let fail = last && !greedy.solved
            nodes.append(NbNode(x: 0.18, y: 0.55 + 0.22 * CGFloat(k), text: step.replacingOccurrences(of: " ", with: ""), tone: fail ? .bad : .plain, below: fail ? "≠ 24 · dead end" : "", belowInk: .pink))
            edges.append(NbEdge(a: parent, b: nodes.count - 1))
            parent = nodes.count - 1
        }
        if chain >= 2 { nodes.append(NbNode(x: 0.66, y: 0.68, text: "never explored:", sub: solutionText, dashed: true)) }
        return .tree(nodes: nodes, edges: edges, height: 230)
    }
    let legend = [nbLegend(.yellow, "Committed"), nbLegend(.pink, "Failure"), nbLegend(.green, "Missed solution")]
    let head = NbBlock.caption("Game of 24 · greedy chain = beam width 1")
    let beamRows = Array(Set([1, 2, width])).sorted().map { w -> NbRow in
        let r = L.beam(w)
        return NbRow(cells: [nbName("\(w)"), nbCell(r.solved ? "yes" : "no", r.solved ? .green : .red, bold: true), nbCell("\(r.expanded)")])
    }
    let costRows = [("1-step, width \(width)", L.beam(width, depth: 1)), ("2-step, width \(width2)", L.beam(width2, depth: 2))].map { name, r in
        NbRow(cells: [nbName(name), nbCell(r.solved ? "yes" : "no", r.solved ? .green : .red), nbCell(nbGrouped(Int64(r.evaluatorCalls)))])
    }
    return [
        frame([.caption("Game of 24 · combine all four numbers into 24"), .toks(root.map { nbTok(L.label([$0]), .blue) }),
               .callout(["first moves available: {\(succ.count)}", "distinct states after one move: {\(ranked.count)}"])], [],
              "A puzzle where {the first move decides}.",
              "Pick two numbers, apply + − × ÷, repeat until one number is left. Most first moves can never reach 24."),
        frame([head, tree(1)], legend, "A chain of thought is beam width 1.",
              "The cheap evaluator likes \(exprFor(top3[0].state)) and commits. The solution was one sibling away."),
        frame([head, tree(greedy.trace.count - 1)], legend, "The greedy chain {\(greedy.solved ? "solves it" : "dead-ends")}.",
              greedy.solved ? "This time the top-ranked move worked." : "Every later step was the best local choice, and the final number is not 24. There is no way back — a chain never revisits."),
        frame([.caption("the evaluator's ranking of all \(frontierSize) first moves"), table(["state", "h", "can reach 24"], [1, 0.4, 0.7], ranked.prefix(6).map { t in
            NbRow(cells: [nbName(exprFor(t.state)), nbCell(nbF(t.score, 1)), nbCell(t.solvable ? "yes" : "no", t.solvable ? .green : .red, bold: true)])
        })], [nbLegend(.green, "Solvable")],
              "The evaluator {ranks the first solvable move #\(firstRank)}.",
              "h is \"how close could one more operation get to 24\" — cheap and optimistic. Its favourites look close and lead nowhere."),
        frame([.caption("beam search · keep the best w states each level"), table(["width", "solved", "states expanded"], [0.5, 0.6, 0.9], beamRows)], [],
              "Keep more branches: {width \(width)} finds it.",
              "Tree of Thoughts keeps several partial solutions alive and lets the evaluator prune. Width substitutes for a better evaluator."),
        frame([.caption("the solving beam's path"), table([], [0.2, 1.4], wide.trace.enumerated().map { i, s in
            NbRow(cells: [nbCell("\(i + 1)"), nbCell(s, i == wide.trace.count - 1 ? .green : nil, bold: true)], ring: i == wide.trace.count - 1)
        }, aligns: [0, 0])], [nbLegend(.green, "Reaches 24")],
              "At width \(width): {\(wide.trace.last!)}.",
              "The path starts from a move the evaluator ranked below its favourite — exactly the sibling a single chain discards."),
        frame([.caption("width needed vs evaluator look-ahead"), .tiles([NbTile(big: "\(width)", caption: "width · 1-step evaluator", ink: .yellow), NbTile(big: "\(width2)", caption: "width · 2-step evaluator", ink: .green)])], [],
              "A smarter evaluator needs {a narrower beam}.",
              "Looking one more move ahead before scoring cuts the width needed from \(width) to \(width2) — paid for in evaluator calls instead of beam slots."),
        frame([.caption("evaluator calls to solve"), table(["setting", "solved", "evaluator work"], [1.2, 0.5, 0.8], costRows)], [],
              "Every option {costs model calls}.",
              "In the paper each evaluation is an LLM call. ToT solved 74% of Game of 24 against 4% for chain of thought — at roughly a hundred times the calls."),
        frame([.caption("the whole tree, searched exhaustively"), .tiles([NbTile(big: nbGrouped(Int64(expanded)), caption: "states", ink: .grey), NbTile(big: "\(solutions)", caption: "paths reaching 24", ink: .green)])], [],
              "Exhaustive search: {\(nbGrouped(Int64(expanded))) states}.",
              "Brute force always works on a puzzle this small. Search with an evaluator matters when the tree is too big to enumerate — which is every interesting problem."),
        frame([.caption("never explored by the greedy chain"), .callout([solutionText]), .tiles([NbTile(big: "13−9=4", caption: "a move ranked low", ink: .grey), NbTile(big: "10−4=6", caption: "another", ink: .grey), NbTile(big: "4×6=24", caption: "together", ink: .green)])], [],
              "The solution needs {two unglamorous moves}.",
              "Neither 13 − 9 nor 10 − 4 looks close to 24 on its own. Only a search that keeps them alive can combine them."),
        frame([.kv([("chain of thought", "one path, width 1"), ("self-consistency", "many full paths, then vote"), ("Tree of Thoughts", "partial paths + evaluator + backtracking"), ("cost", "evaluator calls × width × depth")])], [],
              "ToT = {search + a learned evaluator}.",
              "It is classic beam or best-first search with the LLM proposing moves and scoring states. It pays off when one early mistake ruins a chain."),
    ]
}

// MARK: - Vector databases

private func vectorDbFrames() -> [NbFrame] {
    typealias L = VectorDbLab
    let q = L.query
    let exactIds = Set(L.exactNeighbours().map(\.id))
    func scatter(_ cells: Bool, showQuery: Bool = true, found: Set<Int> = [], centroids: Bool = false, probed: Set<Int> = []) -> NbBlock {
        var dots = L.corpus.map { d -> NbDot in
            let cell = L.ivf.assignment[d.id]
            return NbDot(p: NbP(d.x, d.y), ink: .grey, r: found.contains(d.id) ? 4.5 : 2.6, ring: found.contains(d.id), cat: cells ? (probed.isEmpty || probed.contains(cell) ? cell : nil) : d.cluster)
        }
        if centroids { dots += L.ivf.centroids.map { NbDot(p: NbP($0.0, $0.1), r: 5, hollow: true) } }
        if showQuery { dots.append(NbDot(p: NbP(q.0, q.1), ink: .yellow, r: 6, ring: true)) }
        return .plot(NbPlotBlock(xr: (0, 1), yr: (0, 1), height: 210, dots: dots, grid: false))
    }
    let ivfLegend = [nbLegend(.grey, "Centroid", .ring), nbLegend(.indigo, "Vector, coloured by cell")]
    let (one, c1) = L.ivfSearch(1)
    let r1 = L.recall(one)
    let stranded = L.strandedFraction(L.knnGraph)
    let g1 = L.graphSearch(ef: 2), g2 = L.graphSearch(ef: 8)
    let dims = [2, 10, 100, 1000]
    let contrast = dims.map { L.contrastRatio($0) }
    let ordered = L.ivf.centroids.indices.sortedBy { pow(q.0 - L.ivf.centroids[$0].0, 2) + pow(q.1 - L.ivf.centroids[$0].1, 2) }
    let cMax = contrast.max()!
    return [
        frame([.caption("\(L.corpusSize) stored vectors · 2-D for drawing"), scatter(false, showQuery: false)], [nbLegend(.indigo, "Vector, coloured by topic")],
              "A vector database stores {embeddings}.",
              "Every document becomes a point; similar meaning means nearby points. The job is finding the nearest ones to a query, fast."),
        frame([.caption("brute force · compare the query to every vector"), scatter(false, found: exactIds), .callout(["\(L.corpusSize) distance computations · recall {1.00}"])],
              [nbLegend(.yellow, "Query"), nbLegend(.grey, "True top-5", .ring)],
              "Exact search: {every vector, every query}.",
              "Always right, and linear in the corpus. At a billion vectors that is a billion distances per query — too slow."),
        frame([.caption("IVF index · k-means on stored vectors", note: "k = \(L.ivf.centroids.count)"), scatter(true, showQuery: false, centroids: true), .callout(["\(L.ivf.centroids.count) cells · \(L.corpusSize) vectors · one assignment pass"])], ivfLegend,
              "IVF partitions the corpus first.",
              "k-means gives \(L.ivf.centroids.count) coarse cells and tags each vector with one. A query scans only the cells it probes — fast to build, and its failure mode is geometric."),
        frame([.caption("probe the nearest cell only · nprobe = 1"), scatter(true, found: Set(one.map(\.id)), centroids: true, probed: Set(ordered.prefix(1))),
               .tiles([NbTile(big: nbF(r1, 1), caption: "recall@5", ink: r1 < 1 ? .red : .green), NbTile(big: "\(c1)", caption: "distances computed")])], ivfLegend,
              "The query sits {on a cell boundary}.",
              "Its true neighbours are split across two cells. Probing only the nearest cell finds \(Int(r1 * 5)) of 5 — the geometric failure."),
        frame([.caption("more probes · recall vs work"), table(["nprobe", "recall@5", "distances"], [0.6, 0.7, 0.7], [1, 2, 3, 4].map { n in
            let (f, c) = L.ivfSearch(n)
            let rr = L.recall(f)
            return NbRow(cells: [nbName("\(n)"), nbCell(nbF(rr, 1), rr == 1 ? .green : .red), nbCell("\(c)")])
        } + [NbRow(cells: [nbName("exact"), nbCell("1.0", .green), nbCell("\(L.corpusSize)")])])], [],
              "nprobe is {the recall dial}.",
              "Each extra cell costs more distances and recovers more neighbours. Production systems tune it per workload."),
        frame([.caption("graph index · each vector linked to its 6 nearest"), .tiles([NbTile(big: pct(stranded), caption: "start points that can't reach the answer", ink: .red), NbTile(big: "\(L.componentCount(L.knnGraph))", caption: "disconnected islands")])], [],
              "A plain k-NN graph {strands} some searches.",
              "Clusters link only to themselves. A greedy walk that starts on the wrong island never reaches the query's neighbours, whatever its budget."),
        frame([.caption("+2 random long links per node (small world)"), table(["ef", "recall@5", "distances", "hops"], [0.4, 0.6, 0.6, 0.4], [(2, g1), (8, g2)].map { ef, r in
            let rr = L.recall(r.found)
            return NbRow(cells: [nbName("\(ef)"), nbCell(nbF(rr, 1), rr == 1 ? .green : .yellow), nbCell("\(r.comparisons)"), nbCell("\(r.hops)")])
        }), .callout(["islands: {\(L.componentCount(L.smallWorldGraph))}"])], [],
              "Long links fix it: {the HNSW idea}.",
              "A few random edges join every island. Greedy search then reaches the answer from anywhere; ef, the candidate list size, trades work for recall."),
        frame([.caption("contrast (d_max − d_min) / d_min · random points"), .bars(dims.indices.map { NbBar(label: "d = \(dims[$0])", value: f2(contrast[$0]), frac: log10(1 + contrast[$0]) / log10(1 + cMax), ink: .violet) }, labelWidth: 72)], [],
              "In high dimensions {everything is about equally far}.",
              "With 1,000 dimensions the nearest and farthest points differ by only \(pct(contrast.last!)). Embeddings work because real data clusters on a low-dimensional surface — uniform data would be hopeless."),
        frame([.caption("scalar quantization · levels per dimension"), table(["levels", "bits", "recall@5", "bytes · 768-d"], [0.5, 0.4, 0.6, 0.8], [256, 16, 4].map { lv in
            let bits = Int(log(Double(lv)) / log(2.0))
            let rr = L.quantizedRecall(lv)
            return NbRow(cells: [nbName("\(lv)"), nbCell("\(bits)"), nbCell(nbF(rr, 1), rr == 1 ? .green : .red), nbCell(nbGrouped(Int64(L.bytesPerVector(768, bits))))])
        })], [],
              "Compress the vectors: {memory vs accuracy}.",
              "8 bits per dimension keeps the ranking; at 2 bits the grid is coarser than the gap between neighbours and recall falls. 768 floats take 3,072 bytes uncompressed."),
        frame([.kv([("IVF", "cluster, probe a few cells"), ("HNSW", "navigable small-world graph"), ("PQ / quantization", "smaller vectors"), ("filters", "metadata alongside vectors")])], [],
              "Every index is {approximate on purpose}.",
              "Vector databases (FAISS, pgvector, Pinecone, Milvus) combine these: an index for speed, quantization for memory, and recall as the price."),
    ]
}

// MARK: - ReAct

private func reactFrames() -> [NbFrame] {
    typealias L = ReActLab
    let (top3, found) = L.singleShot(topK: 3)
    let head = NbBlock.caption("\"\(L.question)\"")
    let turns = L.trajectory
    let rank2 = RetrievalLab.rankOf(L.question, L.hop2PassageId)
    func turnTable(_ upTo: Int) -> NbBlock {
        table([], [0.35, 1.6], turns.prefix(upTo + 1).enumerated().flatMap { i, t in [
            NbRow(cells: [nbCell("thought"), nbProse(t.thought)]),
            NbRow(cells: [nbCell("act", .indigo), nbCell(t.action, .indigo)], ring: i == upTo),
            NbRow(cells: [nbCell("obs", .green), nbProse(t.observation, .green)]),
        ] }, aligns: [0, 0])
    }
    let loopLegend = [nbLegend(.indigo, "Action"), nbLegend(.green, "Observation")]
    return [
        frame([head, .caption("one retrieval with the question as the query · top 3"), table(["passage", "score"], [1.3, 0.4], top3.map { p in
            NbRow(cells: [nbName(p.title, p.id == L.hop2PassageId ? .green : nil), nbCell(f2(RetrievalLab.score(L.question, p)))])
        }), .callout(["answer passage (C, 1972) in top 3: {\(found ? "yes" : "no")} · its rank: \(rank2)"])], [],
              "A two-hop question {defeats one retrieval}.",
              "The passage with the year never mentions Linux, so searching with the question ranks it #\(rank2). The first hop's answer is needed to find the second."),
        frame([.caption("\"When was it first released?\" · three ways to answer"), table([], [0.8, 1, 0.6], [
            NbRow(cells: [nbName("closed book"), nbProse("guess from weights"), nbCell("\(L.closedBookAnswer) ✗", .pink, bold: true)]),
            NbRow(cells: [nbName("reason only"), nbProse("longer guess"), nbCell("\(L.closedBookAnswer) ✗", .pink, bold: true)]),
            NbRow(cells: [nbName("ReAct"), nbProse("search → read → answer"), nbCell("\(L.groundedAnswer) ✓", .green, bold: true)], tint: .violet),
        ]), .kv([("reasoning fixes", "steps you can derive"), ("retrieval fixes", "facts you do not hold"), ("this question needs", "both, interleaved")])], [],
              "More thinking does not recover a missing fact.",
              "Closed-book gives \"\(L.closedBookAnswer)\" — fluent and wrong. Reasoning harder only lengthens the guess. ReAct interleaves thought with action."),
        frame([head, turnTable(0)], loopLegend, "Turn 1: {find the language}.",
              "The thought plans the decomposition; the action is a tool call; the observation is what the tool returned — passage \"\(RetrievalLab.corpus[turns[0].retrievedId!].title)\"."),
        frame([head, turnTable(1)], loopLegend, "Turn 2: {a new query} from what was learned.",
              "\"C language first released\" was impossible to write before turn 1. It retrieves the passage the original question ranked #\(rank2)."),
        frame([head, turnTable(2)], loopLegend, "Turn 3: {finish(\"\(L.groundedAnswer)\")}.",
              "Every claim in the answer traces to an observation. If a search had failed, the next thought could have tried another query."),
        frame([.kv([("loop", "thought → action → observation"), ("actions", "search, lookup, finish"), ("strength", "grounded, inspectable"), ("cost", "one model call per turn")])], [],
              "ReAct is {the loop under most agents}.",
              "Reason about what is missing, act to get it, read the result, repeat. Tool-using agents generalise the action set beyond search."),
    ]
}

// MARK: - Agents

private func agentFrames() -> [NbFrame] {
    typealias L = AgentLab
    let total = L.trajectory.reduce(0) { $0 + $1.tokens }
    let maxT = Double(L.trajectory.map(\.tokens).max()!)
    let log = table([], [0.15, 1.4, 0.6, 0.35], L.trajectory.enumerated().map { i, s in
        NbRow(cells: [nbCell("\(i + 1)"), nbCell(s.label, s.failed ? .yellow : nil, bold: true), NbCell(text: "", ink: s.failed ? .yellow : .sky, bar: Double(s.tokens) / maxT), nbCell("\(s.tokens)")], tint: s.failed ? .yellow : nil)
    }, aligns: [0, 0, 0, 2])
    let requests = L.trajectory.indices.filter { $0 % 2 == 0 }
    let contexts = requests.map { L.contextAt($0 + 1) }
    let cMax = Double(contexts.max()!)
    return [
        frame([.caption("three tools · schemas sent with every request"), table(["tool", "latency", "schema tokens"], [0.8, 0.6, 0.7], L.tools.map { NbRow(cells: [nbName($0.name), nbCell("\($0.latencyMs) ms"), nbCell("\($0.schemaTokens)")]) }),
               .callout(["system prompt \(L.systemTokens) + schemas \(L.schemaTokens()) = {\(L.systemTokens + L.schemaTokens())} tokens before any work"])], [],
              "An agent is a model {plus tools it can call}.",
              "Each tool is described by a schema the model reads on every turn. Those tokens are paid before the task even starts."),
        frame([.caption("trajectory · tokens added per entry", note: "Σ \(total)"), log, .callout(["the error came back as an observation"])], [nbLegend(.yellow, "Failure and retry")],
              "The trajectory, one entry per turn.",
              "Step 4 is a real failure: the tool rejected \"mi\" and the model retried with \"mile\". Recovery is worth having — but it is not free."),
        frame([.caption("context resent on each model request"), .bars(requests.indices.map { NbBar(label: "request \($0 + 1)", value: "\(contexts[$0])", frac: Double(contexts[$0]) / cMax, ink: .sky) }, labelWidth: 84),
               .tiles([NbTile(big: nbGrouped(Int64(L.billedTokens())), caption: "tokens billed", ink: .red), NbTile(big: "\(L.finalContext())", caption: "final transcript"), NbTile(big: nbF(L.billingMultiple(), 1) + "×", caption: "billing multiple", ink: .yellow)])], [],
              "Every turn {resends everything before it}.",
              "The model is stateless, so request k carries the whole transcript. Billed tokens grow roughly with the square of the number of steps."),
        frame([.caption("the same task without the failed call"), .tiles([NbTile(big: nbGrouped(Int64(L.billedTokens())), caption: "with retry"), NbTile(big: nbGrouped(Int64(L.billedTokens(L.cleanTrajectory))), caption: "clean", ink: .green), NbTile(big: "+" + nbGrouped(Int64(L.retryOverhead())), caption: "cost of one error", ink: .red)])], [],
              "One retried call cost {\(nbGrouped(Int64(L.retryOverhead()))) tokens}.",
              "The two failure entries are only \(L.trajectory.filter(\.failed).reduce(0) { $0 + $1.tokens }) tokens in the transcript, but they are resent on every later request. Clear tool errors and good schemas pay for themselves."),
        frame([.caption("three independent tool calls"), .bars([
            NbBar(label: "one per turn", value: "\(L.sequentialLatency()) ms", frac: 1, ink: .red), NbBar(label: "parallel", value: "\(L.parallelLatency()) ms", frac: Double(L.parallelLatency()) / Double(L.sequentialLatency()), ink: .green),
        ], labelWidth: 96)], [],
              "Issue independent calls {in one turn}.",
              "Parallel tool calls wait only for the slowest (\(L.parallelLatency()) ms) and save two round trips of resent context."),
    ]
}

// MARK: - Hallucination

private func hallucinationFrames() -> [NbFrame] {
    typealias L = HallucinationLab
    let qs = L.questions
    let supported = Double(qs.filter(\.supported).count)
    func dumbbell(_ pop: [HallucinationLab.Question]) -> NbBlock {
        .plot(NbPlotBlock(xr: (0.5, Double(qs.count) + 0.5), yr: (0, 1), height: 170,
                          dots: pop.enumerated().flatMap { i, q in [NbDot(p: NbP(Double(i + 1), L.confidence(q)), ink: .violet, r: 4), NbDot(p: NbP(Double(i + 1), L.accuracy(q)), ink: .sky, r: 4)] },
                          segs: pop.enumerated().map { i, q in NbSeg(a: NbP(Double(i + 1), L.confidence(q)), b: NbP(Double(i + 1), L.accuracy(q)), ink: abs(L.confidence(q) - L.accuracy(q)) > 0.3 ? .pink : .slate) },
                          xTicks: (1...qs.count).map { (Double($0), "Q\($0)") }, yTicks: [(0, "0.0"), (0.5, "0.5"), (1, "1.0")],
                          shade: (supported + 0.5, Double(qs.count) + 0.5, "unsupported"), groups: [(3.5, "supported"), (8.5, "unsupported")]))
    }
    let legend = [nbLegend(.violet, "Confidence (5-chain agreement)", .dot), nbLegend(.sky, "Accuracy", .dot)]
    let ece = L.expectedCalibrationError(), eceScattered = L.expectedCalibrationError(L.scatteredQuestions)
    let sweep = L.abstentionSweep()
    let unsup = qs.filter { !$0.supported }.map(L.confidence)
    return [
        frame([.caption("10 questions · accuracy of a 5-sample majority"), .bars(qs.enumerated().map { i, q in NbBar(label: "Q\(i + 1)", value: f2(L.accuracy(q)), frac: L.accuracy(q), ink: q.supported ? .sky : .pink) }, labelWidth: 36)],
              [nbLegend(.sky, "Supported by the documents"), nbLegend(.pink, "Not answerable from them")],
              "Some questions {can't be answered} from what the model knows.",
              "The six supported questions are mostly right. On the four unsupported ones the model is wrong in a consistent way — the definition of a hallucination.",
              chips: [nbChip("overall accuracy", f2(L.overallAccuracy()), true)]),
        frame([.caption("confidence vs accuracy · per question", note: "ECE \(f2(ece))"), dumbbell(qs)], legend,
              "Self-agreement is the first fix people reach for.",
              "It fails here: the four unsupported questions report \(f2(unsup.min()!))–\(f2(unsup.max()!)) confidence while almost never being right."),
        frame([.caption("control: the same model, errors scattered", note: "ECE \(f2(eceScattered))"), dumbbell(L.scatteredQuestions)], legend,
              "When wrong answers {disagree}, confidence drops.",
              "Spread the errors over four different wrong answers and agreement falls, so confidence becomes a warning sign. Hallucinations are dangerous because they are systematic."),
        frame([.caption("answer only above a confidence threshold"), table(["threshold", "answered", "accuracy"], [0.6, 0.6, 0.6], sweep.map { t, c, a in
            NbRow(cells: [nbName(f2(t)), nbCell(pct(c)), nbCell(f2(a), a > 0.8 ? .green : .red)])
        })], [],
              "Thresholding confidence {can't separate} them.",
              "The unsupported questions are as confident as the real ones, so any threshold that drops them also drops good answers. Confidence measures agreement, not truth."),
        frame([.caption("grounding: answer only when retrieval supports it"), .tiles([NbTile(big: pct(L.groundedCoverage()), caption: "questions answered", ink: .indigo), NbTile(big: f2(L.groundedSelectiveAccuracy()), caption: "accuracy when answering", ink: .green), NbTile(big: f2(L.overallAccuracy()), caption: "answering everything", ink: .red)])], [],
              "Check the {evidence}, not the model's agreement with itself.",
              "Refusing when no retrieved passage supports the answer drops exactly the four unsupported questions. Accuracy on what remains is \(f2(L.groundedSelectiveAccuracy()))."),
        frame([.kv([("self-consistency", "catches random errors only"), ("confidence threshold", "fails on systematic errors"), ("retrieval + citation", "checks against a source"), ("abstention", "\"I don't know\" as a valid answer")])], [],
              "Mitigation means {grounding and abstaining}.",
              "A model that agrees with itself can still be wrong. External evidence and the option to decline are what reduce hallucinations in practice."),
    ]
}

// MARK: - Feed-forward network

private func ffnFrames() -> [NbFrame] {
    let x = [0.4, 0.5, 0.6, 0.7]
    var r = ModelRng(31)
    var w1: [[Double]] = []
    for _ in 0..<8 { var row: [Double] = []; for _ in 0..<4 { row.append(r.g() * 0.7) }; w1.append(row) }
    var b1: [Double] = []
    for _ in 0..<8 { b1.append(r.g() * 0.2) }
    var w2: [[Double]] = []
    for _ in 0..<4 { var row: [Double] = []; for _ in 0..<8 { row.append(r.g() * 0.5) }; w2.append(row) }
    let pre = w1.indices.map { j in b1[j] + x.indices.reduce(0.0) { $0 + w1[j][$1] * x[$1] } }
    let hidden = pre.map { max($0, 0) }
    let out = w2.map { row in row.indices.reduce(0.0) { $0 + row[$1] * hidden[$1] } }
    let zeroed = hidden.filter { $0 == 0 }.count
    let inks: [NbInk] = [.sky, .sky, .violet]
    let footers = [("token", "d = 768"), ("hidden · ReLU", "4d = 3072"), ("output", "d = 768")]
    let legend = [nbLegend(.sky, "Active", .dot), nbLegend(.slate, "Zeroed by ReLU", .dot), nbLegend(.violet, "Output", .dot)]
    func net(_ showHidden: Bool, _ relu: Bool, _ showOut: Bool) -> NbBlock {
        let h: [Double?] = !showHidden ? Array(repeating: nil, count: 8) : relu ? hidden.map { $0 == 0 ? nil : $0 } : pre.map { Optional($0) }
        return .net(cols: [x.map { Optional($0) }, h, showOut ? out.map { Optional($0) } : Array(repeating: nil, count: 4)], inks: inks, footers: footers)
    }
    let head = NbBlock.caption("one position, expand → ReLU → project", note: "shown 4 → 8 → 4")
    let d: Int64 = 768
    let ffnParams = 2 * d * 4 * d + 4 * d + d
    let attnParams = 4 * d * d + 4 * d
    return [
        frame([head, net(true, true, true)], legend, "Attention, then a position-wise FFN.",
              "Two linear layers with a ReLU, applied to each token alone: 768 → 3072 → 768. No token talks to another here — attention already did that."),
        frame([head, net(true, false, false), .callout(["h = W₁x + b₁ · \(pre.filter { $0 < 0 }.count) of 8 pre-activations negative"])], legend, "Expand: {4× wider}.",
              "W₁ projects the token into a larger space. Some of the 8 values come out negative: \(pre.map(f2).joined(separator: " "))."),
        frame([head, net(true, true, false)], legend, "ReLU zeroes {\(zeroed) of 8} units.",
              "max(0, h) switches off every negative unit for this token. Which units fire depends on the token — the FFN behaves like a lookup keyed on the input."),
        frame([head, net(true, true, true), .callout(["y = W₂·ReLU(h) → back to d = 768"])], legend, "Project back {to the model width}.",
              "W₂ combines only the active units. The result is added to the residual stream and passed to the next block."),
        frame([.caption("parameters per block · d = 768"), .bars([
            NbBar(label: "FFN", value: nbGrouped(ffnParams), frac: 1, ink: .violet), NbBar(label: "attention", value: nbGrouped(attnParams), frac: Double(attnParams) / Double(ffnParams), ink: .sky),
        ], labelWidth: 84), .tiles([NbTile(big: pct(Double(ffnParams) / Double(ffnParams + attnParams)), caption: "of a block's weights", ink: .indigo)])], [],
              "The FFN holds {two-thirds} of the weights.",
              "8d² against attention's 4d². Much of what a transformer \"knows\" is stored here — interpretability work finds facts recalled through FFN key-value patterns."),
        frame([.kv([("original", "ReLU, 4× expansion"), ("GPT-2 / BERT", "GELU"), ("LLaMA, Mistral", "SwiGLU, ~2.7× with a gate"), ("MoE", "many FFNs, router picks a few")])], [],
              "Modern models {gate} the FFN.",
              "SwiGLU multiplies a second projection into the hidden layer before projecting back. Mixture-of-experts replaces the single FFN with several."),
    ]
}

// MARK: - Positional encodings

private func pe(_ pos: Int, _ i: Int, _ d: Int = 16) -> Double {
    let k = i / 2
    let angle = Double(pos) / pow(10000.0, 2.0 * Double(k) / Double(d))
    return i % 2 == 0 ? sin(angle) : cos(angle)
}

private func positionalFrames() -> [NbFrame] {
    let d = 16
    let grid = NbBlock.grid(NbGridBlock(cols: (0..<d).map { "\($0)" }, rows: (0..<8).map { "p\($0)" },
                                        cells: (0..<8).map { p in (0..<d).map { i in NbGCell(text: "", level: (pe(p, i, d) + 1) / 2, ink: .indigo) } },
                                        text: false, cellHeight: 16, scale: ("−1", "+1")))
    let waves = (0..<4).map { k in 2 * Double.pi * pow(10000.0, 2.0 * Double(k) / Double(d)) }
    let waveTiles = NbBlock.tiles(waves.enumerated().map { NbTile(big: nbF($1, 1), caption: "dims \(2 * $0)–\(2 * $0 + 1)") })
    func dot(_ a: Int, _ b: Int) -> Double { (0..<d).reduce(0.0) { $0 + pe(a, $1, d) * pe(b, $1, d) } }
    let starts = [0, 3, 10]
    return [
        frame([.caption("attention sees a set, not a sequence"), .toks(["dog", "bites", "man"].map { nbTok($0) }, label: "A"), .toks(["man", "bites", "dog"].map { nbTok($0) }, label: "B"),
               .callout(["same tokens → same attention scores, permuted"])], [],
              "Self-attention is {order-blind}.",
              "Shuffling the tokens just shuffles the outputs. Without extra information \"dog bites man\" and \"man bites dog\" look the same."),
        frame([.caption("PE(pos, dim) · 8 positions × 16 dims"), grid, .caption("wavelength per dimension pair"), waveTiles], [],
              "A fixed sinusoid per dimension pair.",
              "Wavelengths grow geometrically from 2π to 10000·2π: early dims cycle every few positions, late ones barely move."),
        frame([.caption("dims 0 (sin) and 1 (cos) across positions"), .plot(NbPlotBlock(xr: (0, 7), yr: (-1, 1), height: 150,
            lines: [NbLine(pts: (0...70).map { NbP(Double($0) / 10, sin(Double($0) / 10)) }, ink: .indigo), NbLine(pts: (0...70).map { NbP(Double($0) / 10, cos(Double($0) / 10)) }, ink: .sky, dashed: true)],
            dots: (0...7).map { NbDot(p: NbP(Double($0), sin(Double($0))), ink: .indigo, r: 3.5) },
            xTicks: (0...7).map { (Double($0), "p\($0)") }, yTicks: [(-1, "−1"), (0, "0"), (1, "1")]))],
              [nbLegend(.indigo, "sin(pos)", .line), nbLegend(.sky, "cos(pos)", .dashedLine)],
              "Each pair is {a point on a circle}.",
              "(sin, cos) at a given frequency rotates as position grows. Every position gets a unique combination of angles across the 8 pairs."),
        frame([.caption("PE(p) · PE(p + k) for different starting p"), table(["offset k", "p = 0", "p = 3", "p = 10"], [0.6, 0.6, 0.6, 0.6], [1, 2, 4].map { k in
            NbRow(cells: [nbName("\(k)")] + starts.map { nbCell(f2(dot($0, $0 + k)), .indigo) })
        })], [],
              "The dot product depends {only on the offset}.",
              "Shifting by k rotates each pair by a fixed angle, so PE(p)·PE(p+k) is the same wherever p starts. Relative position is visible to attention."),
        frame([.caption("added to the token embedding"), .callout(["x_p = embedding(token) + PE(p)", "same width d · no parameters"]), .tiles([NbTile(big: "0", caption: "learned parameters", ink: .green), NbTile(big: "any", caption: "length, in principle")])], [],
              "The encoding is {added, not concatenated}.",
              "Each position's vector is summed into the token embedding before the first layer. Sinusoids need no training and are defined for any position."),
        frame([.caption("learned positions · BERT, GPT-2"), .kv([("table", "one trained vector per position"), ("max length", "512 (BERT) · 1,024 (GPT-2)"), ("beyond it", "no vector exists"), ("quality", "about the same as sinusoids")])], [],
              "Learned tables are simpler — {and capped}.",
              "BERT and GPT-2 train a vector per position. They match sinusoids in quality but stop at the table's length."),
        frame([.kv([("RoPE (LLaMA, Mistral)", "rotate q and k by position"), ("ALiBi", "penalise attention by distance"), ("why", "relative position, longer contexts"), ("extension", "rescale RoPE frequencies")])], [],
              "Modern models {rotate} queries and keys.",
              "RoPE applies the same sin/cos rotation inside attention instead of adding it to the input, so q·k depends directly on relative position — and stretches to longer contexts."),
    ]
}
