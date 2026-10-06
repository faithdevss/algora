import Foundation

// Port of NlpTextFrames.kt: text preprocessing, statistical NLP and word-embedding labs. The regex
// patterns run through NSRegularExpression, NFKC through Foundation's normaliser, and the counts, HMM
// and word2vec model use the same arithmetic and LCG as Android.

let nlpTextTopicIds: Set<String> = [
    "n_grams", "lemmatization", "stemming", "stop_words", "regex_nlp", "text_cleaning", "bow_tfidf",
    "cosine_similarity", "jaccard_similarity", "hmm", "word_embeddings", "word2vec_cbow", "word2vec_skipgram",
    "rnn_lstm",
]

func nlpTextLab(_ topicId: String) -> [NbFrame]? {
    switch topicId {
    case "n_grams": nGramFrames()
    case "lemmatization": lemmaFrames()
    case "stemming": stemFrames()
    case "stop_words": stopWordFrames()
    case "regex_nlp": regexFrames()
    case "text_cleaning": cleaningFrames()
    case "bow_tfidf": bowFrames()
    case "cosine_similarity": cosineFrames()
    case "jaccard_similarity": jaccardFrames()
    case "hmm": hmmFrames()
    case "word_embeddings": embeddingFrames()
    case "word2vec_cbow": cbowFrames()
    case "word2vec_skipgram": skipGramFrames()
    case "rnn_lstm": rnnFrames()
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

// MARK: - N-grams

private func nGramFrames() -> [NbFrame] {
    let padded = ["<s>", "i", "like", "nlp", "</s>"]
    let bigrams = zip(padded, padded.dropFirst()).map { ($0, $1) }
    let use = ["first word", "—", "—", "ending"]
    func window(_ at: Int) -> NbBlock {
        .toks(padded.enumerated().map { i, w in nbTok(w, i == at || i == at + 1 ? .hot : w.hasPrefix("<") ? .dim : .plain) })
    }
    func tbl(_ at: Int) -> NbBlock {
        table(["bigram", "position", "used for"], [1, 0.6, 0.8], bigrams.enumerated().map { i, b in
            NbRow(cells: [nbName("(\(b.0), \(b.1))"), nbCell("\(i + 1)"), nbCell(use[i], use[i] == "—" ? nil : .indigo)], ring: i == at)
        })
    }
    let legend = [nbLegend(.yellow, "Window"), nbLegend(.slate, "Boundary marker")]
    let tiles = NbBlock.tiles([NbTile(big: "4", caption: "bigrams with padding", ink: .indigo), NbTile(big: "2", caption: "without — no start, no end", ink: .grey)])
    let windowText = [
        ("A bigram is a two-token window, slid one position at a time.",
         "Without <s> and </s> the model has nothing to condition the first word on and no way to end a sentence, so its probabilities would not sum to 1."),
        ("Position 2: {(i, like)}.", "The window moves one token right. Every inner pair is counted exactly once."),
        ("Position 3: {(like, nlp)}.", "Three words give two inner bigrams; the padding adds one at each end."),
        ("Position 4: {(nlp, </s>)} — the ending.", "This window is what lets the model learn that \"nlp\" can finish a sentence. A sentence of n words always yields n + 1 bigrams."),
    ]
    var frames: [NbFrame] = []
    for at in 0..<4 {
        frames.append(frame([.caption("\"i like nlp\", padded · bigram window at position \(at + 1)"), window(at), tbl(at)] + (at == 0 ? [tiles] : []),
                            legend, windowText[at].0, windowText[at].1))
    }
    let corpus = ["i like nlp", "i like cats", "you like nlp"].map { ["<s>"] + $0.split(separator: " ").map(String.init) + ["</s>"] }
    var keys: [String] = []
    var counts: [String: Int] = [:]
    var unigramOrder: [String] = []
    var unigram: [String: Int] = [:]
    func key(_ a: String, _ b: String) -> String { a + "\u{1}" + b }
    for s in corpus {
        for w in s.dropLast() { if unigram[w] == nil { unigramOrder.append(w) }; unigram[w, default: 0] += 1 }
        for (a, b) in zip(s, s.dropFirst()) { let k = key(a, b); if counts[k] == nil { keys.append(k) }; counts[k, default: 0] += 1 }
    }
    func p(_ a: String, _ b: String) -> Double { Double(counts[key(a, b)] ?? 0) / Double(unigram[a] ?? 1) }
    let corpusCaption = NbBlock.caption("corpus · \"i like nlp\" · \"i like cats\" · \"you like nlp\"")
    let total = counts.values.reduce(0, +)
    let countRows = keys.map { k -> NbRow in
        let parts = k.components(separatedBy: "\u{1}")
        let c = counts[k]!
        return NbRow(cells: [nbName("(\(parts[0]), \(parts[1]))"), nbCell("\(c)"), nbCell("\(unigram[parts[0]]!)"), nbCell(f2(Double(c) / Double(unigram[parts[0]]!)), .indigo)])
    }
    frames.append(frame([corpusCaption, table(["bigram", "count", "c(first)", "P"], [1.2, 0.5, 0.6, 0.5], countRows)], [nbLegend(.indigo, "P(second | first)")],
                        "Count every window in the corpus.",
                        "Three sentences give \(total) bigram windows and \(keys.count) distinct pairs. Dividing each count by how often its first word starts a window gives P(second | first).",
                        chips: [nbChip("windows", "\(total)", true), nbChip("distinct", "\(keys.count)")]))
    let pLike = p("i", "like"), pNlp = p("like", "nlp")
    frames.append(frame([corpusCaption, .bars([
        NbBar(label: "P(nlp|like)", value: f2(pNlp), frac: pNlp, ink: .indigo),
        NbBar(label: "P(cats|like)", value: f2(p("like", "cats")), frac: p("like", "cats"), ink: .indigo),
        NbBar(label: "P(like|i)", value: f2(pLike), frac: pLike, ink: .indigo),
    ])], [nbLegend(.indigo, "Maximum-likelihood estimate")],
                        "P(nlp | like) = c(like, nlp) / c(like) = {\(f2(pNlp))}.",
                        "\"like\" starts three windows and two of them continue with \"nlp\". That ratio is the whole model: no parameters beyond the counts.",
                        fx: ["P(nlp | like) = \(counts[key("like", "nlp")]!) / \(unigram["like"]!) = {\(f2(pNlp))}"]))
    let sentWords = ["<s>", "i", "like", "nlp", "</s>"]
    let sent = zip(sentWords, sentWords.dropFirst()).map { ($0, $1) }
    let probs = sent.map { p($0.0, $0.1) }
    let product = probs.reduce(1, *)
    frames.append(frame([
        .caption("P(\"i like nlp\") as a chain of bigrams"),
        table(["bigram", "P"], [1, 0.5], sent.enumerated().map { i, b in NbRow(cells: [nbName("(\(b.0), \(b.1))"), nbCell(f2(probs[i]), .indigo)]) }),
        .tiles([NbTile(big: f2(product), caption: "P of the whole sentence", ink: .indigo)]),
    ], [], "A sentence's probability is the product of its windows.",
                        "The bigram assumption: each word depends only on the one before. \(sent.map { f2(p($0.0, $0.1)) }.joined(separator: " · ")) multiply to \(f2(product)).",
                        fx: [probs.map(f2).joined(separator: " × ") + " = {\(f2(product))}"]))
    let unseen = p("like", "dogs")
    frames.append(frame([
        .caption("\"i like dogs\" · never seen in the corpus"),
        .toks(["<s>", "i", "like", "dogs", "</s>"].map { nbTok($0, $0 == "dogs" ? .bad : .plain) }),
        .callout(["c(like, dogs) = 0  →  P(dogs | like) = {0}", "→  P(\"i like dogs\") = {0}"]),
    ], [nbLegend(.red, "Unseen bigram")], "One unseen pair zeroes the whole sentence.",
                        "Maximum likelihood gives exactly \(f2(unseen)) to anything it never counted, so a perfectly normal sentence becomes impossible. Every real n-gram model needs smoothing."))
    let v = unigramOrder.count + 1
    let add1 = 1.0 / Double(unigram["like"]! + v)
    let add1Nlp = Double(counts[key("like", "nlp")]! + 1) / Double(unigram["like"]! + v)
    frames.append(frame([
        .caption("add-one (Laplace) smoothing · V = \(v) word types"),
        .bars([NbBar(label: "P(nlp|like)", value: "\(f2(pNlp)) → \(f2(add1Nlp))", frac: add1Nlp, ink: .indigo),
               NbBar(label: "P(dogs|like)", value: "0.00 → \(f2(add1))", frac: add1, ink: .green)], labelWidth: 104),
    ], [nbLegend(.indigo, "Seen pair, discounted"), nbLegend(.green, "Unseen pair, now non-zero")],
                        "Add one to every count: {nothing is impossible}.",
                        "Each pair gets a pseudo-count of 1 and the denominator grows by V. Seen pairs give up mass (\(f2(pNlp)) → \(f2(add1Nlp))) so unseen ones can have some.",
                        fx: ["P = (c(like, w) + 1) / (c(like) + V) = (0 + 1) / (\(unigram["like"]!) + \(v)) = {\(f2(add1))}"]))
    let possible = v * v
    frames.append(frame([
        .caption("possible vs observed n-grams"),
        .tiles([NbTile(big: "\(keys.count)", caption: "bigrams seen", ink: .indigo), NbTile(big: "\(possible)", caption: "possible with V = \(v)"), NbTile(big: "\(possible * v)", caption: "possible trigrams", ink: .red)]),
    ], [], "Longer windows, {sparser counts}.",
                        "Each extra word of context multiplies the possible n-grams by V. With a 50,000-word vocabulary there are 2.5 billion bigrams — most never appear, which is why neural language models replaced counting."))
    return frames
}

// MARK: - Lemmatization

private func lemmaFrames() -> [NbFrame] {
    let rows: [(word: String, pos: String, stem: String, lemma: String)] = [
        ("running", "VBG", porterStem("running"), "run"), ("flies", "NNS", porterStem("flies"), "fly"),
        ("studies", "NNS", porterStem("studies"), "study"), ("better", "JJR", porterStem("better"), "good"),
        ("mice", "NNS", porterStem("mice"), "mouse"),
    ]
    let dictionary = Set(rows.map(\.lemma))
    func tbl(_ ring: String? = nil) -> NbBlock {
        table(["word", "POS", "stem", "lemma"], [1, 0.5, 0.7, 0.7], rows.map { r in
            NbRow(cells: [nbName(r.word), nbCell(r.pos), nbCell(r.stem, r.stem == r.lemma ? .green : .red), nbCell(r.lemma, .green)], ring: r.word == ring)
        })
    }
    let stemsOk = rows.filter { dictionary.contains($0.stem) && $0.stem == $0.lemma }.count
    let legend = [nbLegend(.green, "Dictionary word"), nbLegend(.red, "Not a word / wrong form")]
    let head = NbBlock.caption("same five words · Porter stemmer vs WordNet lemmatizer")
    let score = NbBlock.tiles([NbTile(big: "\(stemsOk) / 5", caption: "stems equal the dictionary form", ink: .red), NbTile(big: "5 / 5", caption: "lemmas, given the POS tag", ink: .green)])
    let saw = table(["sentence", "POS", "lemma"], [1.6, 0.5, 0.6], [
        NbRow(cells: [nbName("I saw the film"), nbCell("VBD"), nbCell("see", .green)]),
        NbRow(cells: [nbName("a saw cuts wood"), nbCell("NN"), nbCell("saw", .green)]),
        NbRow(cells: [nbName("the meeting ran long"), nbCell("NN"), nbCell("meeting", .green)]),
        NbRow(cells: [nbName("we are meeting at 5"), nbCell("VBG"), nbCell("meet", .green)]),
    ])
    return [
        frame([head, tbl(), score], legend, "A lemmatizer needs two things a stemmer lacks.",
              "A dictionary, and the part of speech of the word in this sentence. \"better\" only becomes \"good\" because it is tagged as a comparative adjective."),
        frame([head, tbl("running")], legend, "\"running\" → {run} both ways.",
              "Regular inflection is where the two agree: strip -ing, undouble the n. The stemmer gets there by rule, the lemmatizer by lookup."),
        frame([head, tbl("flies")], legend, "\"flies\" → stem {fli}, lemma {m:fly}.",
              "The stemmer's rule IES → I is right for the suffix and wrong for the word. The lemmatizer knows the noun \"fly\" and its plural."),
        frame([head, tbl("better")], legend, "\"better\" → stem {better}, lemma {m:good}.",
              "No suffix rule can reach \"good\" — the form is irregular. The lemmatizer only gets it because the tag says JJR, a comparative adjective; as a verb (\"to better oneself\") the lemma is \"better\"."),
        frame([head, tbl("mice")], legend, "\"mice\" → stem {mice}, lemma {m:mouse}.",
              "Irregular plurals have no suffix to strip, so the stemmer passes them through. A dictionary of exceptions is the only way."),
        frame([.caption("same spelling, different lemma · the POS decides"), saw], [nbLegend(.green, "Lemma for this tag")],
              "The tag, not the spelling, picks the lemma.",
              "\"saw\" is the past of \"see\" or a tool; \"meeting\" is an event or the verb \"meet\". A lemmatizer is only as good as the tagger in front of it."),
        frame([head, score, .kv([("stemmer", "fast, no dictionary, rough"), ("lemmatizer", "needs POS + lexicon, exact"), ("search index", "stems are fine"), ("text analysis", "lemmas read as words")])], legend,
              "Stem for recall, {lemmatize} for meaning.",
              "A search engine only needs related forms to collide, and \"fli\" does that. Anything a person reads, or that counts real words, needs the lemma."),
    ]
}

// MARK: - Stemming (Porter step 1)

private struct StemStep { let rule: String; let result: String; let title: String; let why: String }

private func isCons(_ w: [Character], _ i: Int) -> Bool {
    switch w[i] {
    case "a", "e", "i", "o", "u": return false
    case "y": return i == 0 || !isCons(w, i - 1)
    default: return true
    }
}

/// Porter's m: the number of vowel–consonant sequences.
private func measure(_ s: String) -> Int {
    let w = Array(s)
    var m = 0, i = 0
    while i < w.count && isCons(w, i) { i += 1 }
    while i < w.count {
        while i < w.count && !isCons(w, i) { i += 1 }
        if i >= w.count { break }
        while i < w.count && isCons(w, i) { i += 1 }
        m += 1
    }
    return m
}

private func hasVowel(_ s: String) -> Bool { let w = Array(s); return w.indices.contains { !isCons(w, $0) } }

private func endsDouble(_ s: String) -> Bool { let w = Array(s); return w.count >= 2 && w[w.count - 1] == w[w.count - 2] && isCons(w, w.count - 1) }

private func cvc(_ s: String) -> Bool {
    let w = Array(s)
    return w.count >= 3 && isCons(w, w.count - 3) && !isCons(w, w.count - 2) && isCons(w, w.count - 1) && !"wxy".contains(w.last!)
}

/// Porter's step 1 (1a, 1b, 1c) with a trace of the rules that fired.
private func porterTrace(_ word: String) -> [StemStep] {
    var w = word
    var out: [StemStep] = []
    if w.hasSuffix("sses") { w = String(w.dropLast(2)); out.append(StemStep(rule: "1a", result: w, title: "-sses → -ss", why: "plural of -ss")) }
    else if w.hasSuffix("ies") { w = String(w.dropLast(2)); out.append(StemStep(rule: "1a", result: w, title: "-ies → -i", why: "no check on what is left")) }
    else if w.hasSuffix("ss") {}
    else if w.hasSuffix("s") { w = String(w.dropLast()); out.append(StemStep(rule: "1a", result: w, title: "-s removed", why: "plural")) }
    var second = false
    if w.hasSuffix("eed") {
        if measure(String(w.dropLast(3))) > 0 { w = String(w.dropLast()); out.append(StemStep(rule: "1b", result: w, title: "-eed → -ee", why: "m > 0")) }
    } else if w.hasSuffix("ed") && hasVowel(String(w.dropLast(2))) {
        w = String(w.dropLast(2)); second = true; out.append(StemStep(rule: "1b", result: w, title: "-ed removed", why: "stem \"\(w)\" has a vowel"))
    } else if w.hasSuffix("ing") && hasVowel(String(w.dropLast(3))) {
        w = String(w.dropLast(3)); second = true; out.append(StemStep(rule: "1b", result: w, title: "-ing removed", why: "stem \"\(w)\" has a vowel"))
    }
    if second {
        if w.hasSuffix("at") || w.hasSuffix("bl") || w.hasSuffix("iz") { w += "e"; out.append(StemStep(rule: "1b+", result: w, title: "add -e", why: "ends in at/bl/iz")) }
        else if endsDouble(w) && !"lsz".contains(w.last!) { w = String(w.dropLast()); out.append(StemStep(rule: "1b+", result: w, title: "undouble", why: "ends in double consonant, not l/s/z")) }
        else if measure(w) == 1 && cvc(w) { w += "e"; out.append(StemStep(rule: "1b+", result: w, title: "add -e", why: "short stem, consonant-vowel-consonant")) }
    }
    if w.hasSuffix("y") && hasVowel(String(w.dropLast())) { w = String(w.dropLast()) + "i"; out.append(StemStep(rule: "1c", result: w, title: "-y → -i", why: "stem has a vowel")) }
    return out
}

func porterStem(_ word: String) -> String { porterTrace(word).last?.result ?? word }

private func stemFrames() -> [NbFrame] {
    let words = ["running", "flies", "studies", "happily", "better"]
    func chips(_ cur: String?) -> NbBlock { .toks(words.map { w in nbTok(w, w == cur ? .hot : .plain, (w == cur ? "→ " : "") + porterStem(w)) }) }
    func trace(_ word: String, _ upTo: Int? = nil) -> NbBlock {
        let steps = porterTrace(word)
        let shown = Array(steps.prefix(upTo ?? steps.count))
        var rows = [NbRow(cells: [nbCell("input"), nbCell(word, bold: true), nbCell("")])]
        for (i, s) in shown.enumerated() {
            rows.append(NbRow(cells: [nbCell(s.rule), nbCell(s.result, i == steps.count - 1 ? .indigo : nil, bold: true), nbProse(s.title, sub: s.why)], ring: upTo != nil && i == shown.count - 1))
        }
        if steps.isEmpty { rows.append(NbRow(cells: [nbCell("—"), nbCell(word, .indigo, bold: true), nbProse("no rule fires", sub: "no step-1 suffix matches")])) }
        return table([], [0.35, 0.7, 1.4], rows, aligns: [0, 0, 0])
    }
    let legend = [nbLegend(.yellow, "Current word"), nbLegend(.indigo, "Final stem")]
    let head = NbBlock.caption("Porter stemmer · 5 words, current one traced")
    func traced(_ w: String) -> NbBlock { .caption("rule trace for \"\(w)\"") }
    return [
        frame([head, chips(nil), traced("running"), trace("running", 0)], legend, "A stemmer chops suffixes by rule — {no dictionary}.",
              "Porter's algorithm is a list of suffix rules with conditions on what remains. Each word below shows its final stem; the trace walks step 1, which does most of the work."),
        frame([head, chips("running"), traced("running"), trace("running", 1)], legend, "\"running\" ends in -ing: strip it → \"runn\".",
              "Step 1b only strips -ing when what remains still contains a vowel. The next rule undoubles the final \"nn\", so the stem settles on \"run\"."),
        frame([head, chips("running"), traced("running"), trace("running")], legend, "Undouble: \"runn\" → {p:run}.",
              "A double consonant left by removing -ing or -ed is reduced to one, except l, s and z (\"fall\", \"miss\", \"buzz\")."),
        frame([head, chips("flies"), traced("flies"), trace("flies")], legend, "\"flies\" → {p:fli}.",
              "Rule 1a turns -ies into -i unconditionally. Good for grouping \"flies\" and \"flied\"; the stem is not a word, and was never meant to be."),
        frame([head, chips("studies"), traced("studies"), trace("studies")], legend, "\"studies\" → {p:studi}.",
              "Same rule. \"study\" itself becomes \"studi\" by 1c (-y → -i), so all three forms collide on one key — which is the point."),
        frame([head, chips("happily"), traced("happily"), trace("happily")], legend, "\"happily\" → {p:happili}.",
              "Only -y → -i fires in step 1. Porter's later steps handle derivational suffixes; -ly after a vowel stem is left alone."),
        frame([head, chips("better"), traced("better"), trace("better")], legend, "\"better\" passes through {unchanged}.",
              "No step-1 suffix matches, and step 4's -er rule needs a longer stem. Irregular forms are invisible to a stemmer — that is the lemmatizer's job."),
    ]
}

// MARK: - Stop words

/// The NLTK English stop words that matter for the sentences here (the full list has 179).
private let nltkStops: Set<String> = [
    "i", "me", "my", "we", "our", "you", "your", "he", "him", "his", "she", "her", "it", "its", "they", "them", "their",
    "what", "which", "who", "this", "that", "these", "those", "am", "is", "are", "was", "were", "be", "been", "being",
    "have", "has", "had", "do", "does", "did", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until",
    "while", "of", "at", "by", "for", "with", "about", "against", "between", "into", "through", "to", "from", "in",
    "out", "on", "off", "over", "under", "again", "then", "once", "here", "there", "when", "where", "why", "how",
    "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own",
    "same", "so", "than", "too", "very", "can", "will", "just", "don't", "should", "now",
]

private func words(_ s: String) -> [String] { s.split(separator: " ").map(String.init) }

private func stopWordFrames() -> [NbFrame] {
    let review = words("the movie was not good at all")
    let kept = review.filter { !nltkStops.contains($0) }
    let listed = review.filter { nltkStops.contains($0) }.count
    let head = NbBlock.caption("\"\(review.joined(separator: " "))\" · NLTK English list")
    let flagged = NbBlock.toks(review.map { nbTok($0, nltkStops.contains($0) ? .hot : .plain) })
    let survives = NbBlock.toks(review.map { nbTok($0, nltkStops.contains($0) ? .bad : .good) })
    let legend = [nbLegend(.yellow, "On the list"), nbLegend(.red, "Removed"), nbLegend(.green, "Survives")]
    let other = words("the movie was good")
    let otherKept = other.filter { !nltkStops.contains($0) }
    let corpus = ["the movie was not good at all", "i loved the acting and the music", "it was a waste of time", "the plot is thin but the cast is great", "not my kind of film"]
    let tokens = corpus.flatMap(words)
    let stopCount = tokens.filter { nltkStops.contains($0) }.count
    let stopShare = Double(stopCount) / Double(tokens.count)
    let negations: Set<String> = ["not", "no", "nor"]
    let custom = review.filter { !nltkStops.contains($0) || negations.contains($0) }
    return [
        frame([head, flagged, .caption("what survives"), survives, .banner(big: "\"\(kept.joined(separator: " "))\"", text: "\"not\" is on the list — the sentiment flips.", ink: .red)], legend,
              "One review, seven tokens.",
              "Five are on NLTK's English list. They carry little about which review this is — but one of them carries whether it's positive.",
              chips: [nbChip("on list", "\(listed) of \(review.count)", true), nbChip("kept", "\(kept.count)")]),
        frame([.caption("5 short reviews · \(tokens.count) tokens"), .bars([
            NbBar(label: "stop words", value: "\(stopCount)", frac: stopShare, ink: .yellow),
            NbBar(label: "content", value: "\(tokens.count - stopCount)", frac: 1 - stopShare, ink: .green),
        ])], [nbLegend(.yellow, "On the list"), nbLegend(.green, "Content words")],
              "About {\(nbF(stopShare * 100, 0))%} of running text is stop words.",
              "They are the most frequent words in any corpus and the least informative about topic, so removing them shrinks bag-of-words vectors a lot."),
        frame([.caption("two reviews after removal"), .toks(kept.map { nbTok($0, .good) }, label: "review 1"), .toks(otherKept.map { nbTok($0, .good) }, label: "review 2"),
               .callout(["\"\(review.joined(separator: " "))\" → {\(kept.joined(separator: " "))}", "\"\(other.joined(separator: " "))\" → {\(otherKept.joined(separator: " "))}"])],
              [nbLegend(.green, "Survives")],
              "A negative and a positive review {become identical}.",
              "After removal both are \"movie good\". Any classifier fed these tokens cannot tell them apart."),
        frame([head, .toks(review.map { nbTok($0, negations.contains($0) ? .good : nltkStops.contains($0) ? .bad : .good) }),
               .callout(["list − {not, no, nor} → {\(custom.joined(separator: " "))}"])], [nbLegend(.red, "Removed"), nbLegend(.green, "Survives")],
              "Fix: keep {negations} for sentiment.",
              "The list is a default, not a law. Sentiment work drops \"not\", \"no\" and \"nor\" from it; \"movie not good\" survives."),
        frame([.caption("search: query \"the cast of the film\""), .toks(words("the cast of the film").map { nbTok($0, nltkStops.contains($0) ? .bad : .good) }),
               .tiles([NbTile(big: "5 → 2", caption: "query terms to match", ink: .green), NbTile(big: "\(nltkStops.count)", caption: "words in this list")]), .callout(["index entries for \"the\" ≈ every document"])],
              [nbLegend(.red, "Removed"), nbLegend(.green, "Matched")],
              "For keyword search, removal {saves work}.",
              "\"the\" and \"of\" appear in nearly every document, so matching them ranks nothing. Dropping them shrinks the index and the query."),
        frame([.caption("same review · subword tokens for a transformer"), .toks(review.map { nbTok($0) }), .callout(["tokens kept: {\(review.count) of \(review.count)}"])], [],
              "Transformers keep {every token}.",
              "Attention learns how much each word matters in context — including \"not\". Removing stop words before a BERT-style model only deletes information it would have used."),
        frame([.kv([("bag-of-words, TF-IDF", "remove — shrinks vectors"), ("keyword search", "remove — faster index"), ("sentiment", "keep negations"), ("transformers", "keep everything")])], [],
              "Whether to remove them {depends on the model}.",
              "Stop-word lists come from the count-based era. They help models that weigh every word equally and hurt models that learn weights themselves."),
    ]
}

// MARK: - Regular expressions

private let regexText = "Dr. Smith's e-mail is a.smith@x.co, the U.S. GDP rose 3.5% on 2024-01-05."

private func matches(_ pattern: String, _ text: String) -> [NSRange] {
    let re = try! NSRegularExpression(pattern: pattern)
    return re.matches(in: text, range: NSRange(location: 0, length: (text as NSString).length)).map(\.range)
}

private func regexSpans(_ pattern: String) -> ([NbSpan], Int) {
    let ns = regexText as NSString
    var spans: [NbSpan] = []
    var at = 0
    let ms = matches(pattern, regexText)
    for (k, m) in ms.enumerated() {
        if m.location > at { spans.append(NbSpan(t: ns.substring(with: NSRange(location: at, length: m.location - at)), tone: .dim)) }
        spans.append(NbSpan(t: ns.substring(with: m), tone: k % 2 == 0 ? .blue : .pick))
        at = m.location + m.length
    }
    if at < ns.length { spans.append(NbSpan(t: ns.substring(from: at), tone: .dim)) }
    return (spans, ms.count)
}

private func regexFrames() -> [NbFrame] {
    let units = ["Smith's", "e-mail", "a.smith@x.co", "U.S.", "3.5%", "2024-01-05"]
    let p1 = "\\w+"
    let p2 = "\\w+(?:['@.\\-]\\w+)*"
    let p3 = "(?:[A-Z]\\.)+|\\w+(?:['@.\\-]\\w+)*%?"
    let p4 = "Dr\\.|(?:[A-Z]\\.)+|\\w+(?:['@.\\-]\\w+)*%?|[^\\w\\s]"
    let linguist = 12
    func pieces(_ u: String, _ p: String) -> Int { matches(p, u).count }
    func splitTable(_ p: String) -> NbBlock { .toks(units.map { u in let n = pieces(u, p); return nbTok("\(u)  → \(n)", n > 1 ? .hot : .good) }, columns: 2, mono: true, start: true) }
    let legend = [nbLegend(.violet, "Match (alternating shades)"), nbLegend(.grey, "Dropped characters"), nbLegend(.yellow, "Broken unit")]
    func make(_ p: String, _ extra: [NbBlock], _ headline: String, _ body: String) -> NbFrame {
        let (spans, count) = regexSpans(p)
        return frame([.caption("\(p) over one sentence · \(count) matches"), .text(spans, mono: true)] + extra, legend, headline, body,
                     chips: [nbChip(String(p.prefix(14)) + (p.count > 14 ? "…" : ""), "\(count)"), nbChip("linguist", "\(linguist)", true)])
    }
    let n1 = regexSpans(p1).1, n2 = regexSpans(p2).1, n3 = regexSpans(p3).1, n4 = regexSpans(p4).1
    let ns = regexText as NSString
    let dates = matches("\\d{4}-\\d{2}-\\d{2}", regexText).map { ns.substring(with: $0) }
    let emails = matches("[\\w.]+@\\w+(?:\\.\\w+)+", regexText).map { ns.substring(with: $0) }
    return [
        make(p1, [.caption("units that got split"), splitTable(p1)], "The pattern everyone writes first.",
             "\\w+ finds \(n1) tokens where a linguist would count \(linguist): every apostrophe, dot, hyphen and @ becomes a split point."),
        make(p2, [.caption("units that got split"), splitTable(p2)], "Allow {inner punctuation}: \(n1) → \(n2) matches.",
             "(?:['@.-]\\w+)* lets a token continue through an apostrophe, hyphen, dot or @ when a word character follows. Smith's, e-mail and the address hold together."),
        make(p3, [.caption("units that got split"), splitTable(p3)], "Abbreviations and percentages: {every unit whole}.",
             "(?:[A-Z]\\.)+ catches U.S. with its final dot, and %? keeps 3.5% together. All six tricky units are one match each."),
        make(p4, [.callout(["punctuation becomes its own token", "\"Dr.\" listed explicitly — no rule can tell it from a sentence end"])], "Punctuation as tokens: {\(n4)} matches.",
             "Commas and the final period are now kept as tokens, which a parser needs. \"Dr.\" has to be listed by name: a dot after a capitalised word is also how sentences end."),
        frame([.caption("extraction, not tokenization"), table(["pattern", "finds"], [1.4, 1], [
            NbRow(cells: [nbCell("\\d{4}-\\d{2}-\\d{2}"), nbCell(dates.joined(separator: ", "), .green)]),
            NbRow(cells: [nbCell("[\\w.]+@\\w+(\\.\\w+)+"), nbCell(emails.joined(separator: ", "), .green)]),
        ], aligns: [0, 2])], [nbLegend(.green, "Extracted")],
              "Regexes shine at {fixed formats}.",
              "Dates, e-mails, phone numbers and IDs follow a grammar a regex can state exactly. Free text doesn't."),
        frame([.caption("where rules run out"), .kv([("\"Dr.\" vs sentence end", "needs a list"), ("\"U.S.\" at sentence end", "one dot or two?"), ("don't → do + n't", "language-specific"), ("Chinese, Japanese", "no spaces at all")])], [],
              "Every regex tokenizer becomes {a list of exceptions}.",
              "That is why modern pipelines learn subword tokenizers (BPE, WordPiece) from data instead — and keep regexes for the formats they handle perfectly.",
              chips: [nbChip("\\w+", "\(n1)"), nbChip("final pattern", "\(n3)", true)]),
    ]
}

// MARK: - Lowercasing and cleaning

private func nfkc(_ s: String) -> String { s.precomposedStringWithCompatibilityMapping }

private func replacing(_ s: String, _ pattern: String, _ with: String) -> String {
    let re = try! NSRegularExpression(pattern: pattern)
    return re.stringByReplacingMatches(in: s, range: NSRange(location: 0, length: (s as NSString).length), withTemplate: with)
}

private func cleaningFrames() -> [NbFrame] {
    let pairs: [(String, String, String)] = [
        ("Café", "Cafe\u{0301}", "é vs e + combining accent"), ("ﬁle", "file", "fi ligature"), ("１２%", "12%", "full-width digits"),
        ("Ｑ3", "Q3", "full-width 3"), ("Apple’s", "Apple's", "curly vs straight apostrophe"),
    ]
    // Strings are compared by their scalars, as Kotlin compares chars: "Café" and "Cafe\u{301}" stay different until normalised.
    func same(_ a: String, _ b: String) -> Bool { Array(a.unicodeScalars) == Array(b.unicodeScalars) }
    func pairTable(_ mapQuotes: Bool) -> NbBlock {
        table([], [1.3, 0.8, 0.6], pairs.map { a, b, why in
            var x = nfkc(a), y = nfkc(b)
            if mapQuotes { x = x.replacingOccurrences(of: "’", with: "'"); y = y.replacingOccurrences(of: "’", with: "'") }
            let merged = same(x, y)
            return NbRow(cells: [nbProse("\(a) · \(b)", sub: why), nbCell(merged ? x : "\(x) · \(y)", merged ? .green : .yellow), nbCell(merged ? "merged" : "still 2", merged ? .green : .yellow, bold: true)],
                         tint: merged ? nil : .yellow)
        })
    }
    func distinct(_ xs: [String]) -> Int { Set(xs.map { $0.unicodeScalars.map(\.value) }).count }
    let types0 = distinct(pairs.flatMap { [$0.0, $0.1] })
    let types1 = distinct(pairs.flatMap { [nfkc($0.0), nfkc($0.1)] })
    let types2 = distinct(pairs.flatMap { [nfkc($0.0), nfkc($0.1)] }.map { $0.replacingOccurrences(of: "’", with: "'") })
    let legend = [nbLegend(.green, "Folded to one form"), nbLegend(.yellow, "NFKC leaves it")]
    let raw = "<p>Visit https://shop.ex/deals NOW!!   Prices   from $5</p>"
    let noHtml = replacing(raw, "<[^>]+>", "")
    let noUrl = replacing(noHtml, "https?://\\S+", "<URL>")
    let spaced = replacing(noUrl, "\\s+", " ").trimmingCharacters(in: .whitespaces)
    let lower = spaced.lowercased()
    let steps = [("raw", raw), ("strip HTML", noHtml), ("URL → <URL>", noUrl), ("collapse spaces", spaced), ("lowercase", lower)]
    func pipeline(_ upTo: Int) -> NbBlock {
        table([], [0.5, 1.5], steps.prefix(upTo + 1).enumerated().map { i, kv in NbRow(cells: [nbCell(kv.0), NbCell(text: kv.1, ink: i == upTo ? .yellow : nil)], ring: i == upTo) }, aligns: [0, 0])
    }
    let caseRows: [(String, String)] = [("US", "country vs pronoun"), ("Apple", "company vs fruit"), ("WHO", "organisation vs pronoun")]
    let current = [nbLegend(.yellow, "Current step")]
    return [
        frame([.caption("5 look-alike pairs · raw strings"), table([], [1.3, 1], pairs.map { a, b, why in
            NbRow(cells: [nbProse("\(a) · \(b)", sub: why), nbCell(same(a, b) ? "equal" : "different", same(a, b) ? .green : .red, bold: true)])
        })], [nbLegend(.red, "Different strings")],
              "Same text to a reader, {different strings} to a program.",
              "Each pair looks identical on screen and fails ==. A vocabulary built on raw strings counts every variant as its own word: \(types0) types here.",
              chips: [nbChip("types", "\(types0)", true)]),
        frame([.caption("5 look-alike pairs · NFKC applied to each"), pairTable(false)], legend,
              "NFKC normalise — first, before anything counts a string.",
              "Compatibility normalisation folds accents, ligatures and full-width forms: \(types0) types become \(types1). The curly apostrophe needs its own mapping step.",
              chips: [nbChip("types", "\(types0) → \(types1)", true)]),
        frame([.caption("NFKC, then map ’ → '"), pairTable(true)], legend,
              "Punctuation folding is {a separate table}.",
              "Unicode treats ’ and ' as different characters by design, so normalisation keeps them apart. A small mapping of quotes and dashes finishes the job: \(types1) → \(types2) types.",
              chips: [nbChip("types", "\(types0) → \(types2)", true)]),
        frame([.caption("a scraped snippet · step by step"), pipeline(1)], current, "Strip markup {before} tokenizing.",
              "Tags like <p> are page structure, not text. Left in, they become tokens that appear in every document."),
        frame([.caption("a scraped snippet · step by step"), pipeline(2)], current, "Replace URLs with {a placeholder}.",
              "Every URL is unique, so each would be its own rare token. <URL> keeps the fact that a link was there without the vocabulary cost."),
        frame([.caption("a scraped snippet · step by step"), pipeline(3)], current, "Collapse whitespace.",
              "Runs of spaces, tabs and newlines become one space, so splitting on spaces never produces empty tokens."),
        frame([.caption("a scraped snippet · step by step"), pipeline(4)], current, "Lowercase: \"NOW\" and \"now\" {become one word}.",
              "For counting models this halves the variants of every sentence-initial word. It also erases information, which the next step shows."),
        frame([.caption("lowercasing merges these pairs"), table(["raw", "lower", "meaning lost"], [0.6, 0.6, 1.4], caseRows.map { a, why in
            NbRow(cells: [nbName(a), nbCell(a.lowercased(), .red), nbProse(why)])
        }, aligns: [0, 0, 0])], [nbLegend(.red, "Two words collapse")],
              "Lowercasing {deletes} entities.",
              "\"US\" and \"us\", \"Apple\" and \"apple\": case was carrying the meaning. NER and cased transformers keep case; bag-of-words models usually drop it."),
        frame([.kv([("1 · normalise", "NFKC + quote/dash map"), ("2 · strip", "HTML, control characters"), ("3 · placeholders", "URLs, e-mails, numbers"), ("4 · whitespace", "collapse"), ("5 · case", "only if the model ignores it")])], [],
              "Order matters: {normalise first}.",
              "Every later step compares strings. Doing them before NFKC means a full-width URL or a ligature slips through every regex."),
    ]
}

// MARK: - Bag-of-words / TF-IDF

private func cosine(_ a: [Double], _ b: [Double]) -> Double {
    a.indices.reduce(0.0) { $0 + a[$1] * b[$1] } / (a.reduce(0.0) { $0 + $1 * $1 }.squareRoot() * b.reduce(0.0) { $0 + $1 * $1 }.squareRoot())
}

private func bowFrames() -> [NbFrame] {
    let d1 = words("the cat sat on the mat"), d2 = words("the dog sat on the log")
    let vocab = ["the", "cat", "sat", "on", "mat", "dog", "log"]
    let c1 = vocab.map { w in d1.filter { $0 == w }.count }
    let c2 = vocab.map { w in d2.filter { $0 == w }.count }
    let df = vocab.indices.map { (c1[$0] > 0 ? 1 : 0) + (c2[$0] > 0 ? 1 : 0) }
    let n = 2.0
    let idf = df.map { log((1 + n) / (1 + Double($0))) + 1 }
    let t1 = c1.indices.map { Double(c1[$0]) * idf[$0] }
    let t2 = c2.indices.map { Double(c2[$0]) * idf[$0] }
    let top = (t1 + t2).max()!
    func countCell(_ c: Int) -> NbGCell { NbGCell(text: "\(c)", level: c == 0 ? 0 : 0.35 + 0.3 * Double(c), ink: .blue) }
    func countGrid(_ hot: Int?) -> NbBlock {
        .grid(NbGridBlock(cols: vocab, rows: ["doc 1", "doc 2", "df"], cells: [c1.map(countCell), c2.map(countCell), df.map { NbGCell(text: "\($0)", level: 0.4 + 0.25 * Double($0), ink: .violet) }], hotRow: hot))
    }
    let tfidfGrid = NbBlock.grid(NbGridBlock(cols: vocab, rows: ["doc 1", "doc 2"], cells: [t1, t2].map { row in row.map { NbGCell(text: f2($0), level: $0 == 0 ? 0 : $0 / top, ink: .blue) } }))
    let legend = [nbLegend(.blue, "Term count"), nbLegend(.violet, "Document frequency")]
    let cosCount = cosine(c1.map(Double.init), c2.map(Double.init))
    let cosTfidf = cosine(t1, t2)
    let head = NbBlock.caption("vocabulary from both documents · \(vocab.count) terms")
    return [
        frame([head, .toks(d1.map { nbTok($0, .hot) }), .toks(d2.map { nbTok($0) }), .toks(vocab.map { nbTok($0, .pick) })], [nbLegend(.yellow, "Document 1"), nbLegend(.violet, "Vocabulary")],
              "Build the vocabulary from {every document}.",
              "Each distinct word gets one column. Two six-word sentences share four words, so the vocabulary has \(vocab.count) entries."),
        frame([head, .toks(d1.map { nbTok($0, .hot) }), countGrid(0)], legend, "Document 1 as counts.",
              "the = 2, cat = 1, sat = 1, on = 1, mat = 1. The df row previews TF-IDF: words in both documents will be weighted down next.",
              fx: ["doc 1 = [\(c1.map(String.init).joined(separator: ", "))] · \(d1.count) tokens, \(Set(d1).count) distinct", "order is gone: \"mat sat the on the cat\" → same vector"]),
        frame([head, .toks(d2.map { nbTok($0, .hot) }), countGrid(1)], legend, "Document 2: four columns {overlap}.",
              "the, sat and on appear in both. By raw counts the two documents look \(nbF(cosCount * 100, 0))% similar (cosine), mostly because of \"the\".",
              chips: [nbChip("cosine, counts", f2(cosCount), true)]),
        frame([.caption("inverse document frequency · N = 2, smoothed"), .bars(vocab.indices.map { NbBar(label: vocab[$0], value: f2(idf[$0]), frac: idf[$0] / idf.max()!, ink: df[$0] == 2 ? .grey : .violet) }, labelWidth: 56)],
              [nbLegend(.violet, "In one document"), nbLegend(.grey, "In both")],
              "IDF rewards {rare} words.",
              "idf = ln((1 + N) / (1 + df)) + 1. A word in every document gets the floor of 1; one that singles out a document gets \(f2(idf[1])).",
              fx: ["idf(the) = ln(3 / 3) + 1 = {\(f2(idf[0]))}", "idf(cat) = ln(3 / 2) + 1 = {\(f2(idf[1]))}"]),
        frame([.caption("TF-IDF = count × idf"), tfidfGrid], [nbLegend(.blue, "TF-IDF weight")],
              "\"the\" is still counted twice — but {cat and mat now stand out}.",
              "Words that distinguish the documents get heavier; shared ones stay at their count. Cosine similarity drops from \(f2(cosCount)) to \(f2(cosTfidf)).",
              chips: [nbChip("cosine, counts", f2(cosCount)), nbChip("cosine, TF-IDF", f2(cosTfidf), true)]),
        frame([.caption("what the vector cannot see"), .callout(["\"dog bites man\" = [1, 1, 1]", "\"man bites dog\" = [1, 1, 1]", "→ {identical} vectors"]),
               .tiles([NbTile(big: "\(vocab.count)", caption: "dimensions here"), NbTile(big: "~10⁵", caption: "in a real corpus", ink: .red)])], [],
              "A bag of words {forgets order} and grows with the vocabulary.",
              "Fast, interpretable and still a strong baseline for search and classification. Word embeddings fix the sparsity; sequence models fix the order."),
    ]
}

// MARK: - Cosine similarity

private func cosineFrames() -> [NbFrame] {
    let query = NbP(2, 1)
    let docs: [(String, NbP, NbInk)] = [("short", NbP(2, 1.2), .sky), ("long", NbP(8, 5), .orange), ("theory", NbP(1, 3), .green)]
    func cos(_ a: NbP, _ b: NbP) -> Double { (a.x * b.x + a.y * b.y) / (hypot(a.x, a.y) * hypot(b.x, b.y)) }
    func dist(_ a: NbP, _ b: NbP) -> Double { hypot(a.x - b.x, a.y - b.y) }
    let cosRank = docs.sortedByDescending { cos(query, $0.1) }.map(\.0)
    let distRank = docs.sortedBy { dist(query, $0.1) }.map(\.0)
    func plot(_ show: Set<String>, distances: Bool = false) -> NbBlock {
        let shown = docs.filter { show.contains($0.0) }
        return .plot(NbPlotBlock(
            xr: (0, 9), yr: (0, 6), height: 190,
            lines: [NbLine(pts: [NbP(0, 0), query], ink: .yellow)] + shown.map { NbLine(pts: [NbP(0, 0), $0.1], ink: $0.2) },
            dots: [NbDot(p: query, ink: .yellow, r: 4.5, label: "query")] + shown.map { NbDot(p: $0.1, ink: $0.2, r: 4.5, label: $0.0) },
            segs: distances ? shown.map { NbSeg(a: query, b: $0.1, ink: .grey, dashed: true, width: 1.2) } : [],
            xTicks: [(0, "0"), (3, "3"), (6, "6"), (9, "9")], yTicks: [(0, "0"), (2, "2"), (4, "4"), (6, "6")],
            xLabel: "\"learning\" →", yLabel: "↑ \"proof\""))
    }
    func tbl(_ ring: String?) -> NbBlock {
        table(["doc", "cosine", "#", "distance", "#"], [1, 0.8, 0.3, 0.8, 0.3], docs.map { name, p, ink in
            let cr = cosRank.firstIndex(of: name)! + 1, dr = distRank.firstIndex(of: name)! + 1
            return NbRow(cells: [nbName(name, ink), nbCell(nbF(cos(query, p), 3)), nbCell("\(cr)", cr == dr ? .green : .red), nbCell(f2(dist(query, p))), nbCell("\(dr)", cr == dr ? .green : .red)], ring: name == ring)
        })
    }
    let legend = [nbLegend(.yellow, "Query"), nbLegend(.sky, "short"), nbLegend(.orange, "long"), nbLegend(.green, "theory")]
    let head = NbBlock.caption("term space · counts of two words per document")
    let cLong = cos(query, docs[1].1)
    let longRank = cosRank.firstIndex(of: "long")! + 1
    let qn = NbP(query.x / hypot(query.x, query.y), query.y / hypot(query.x, query.y))
    return [
        frame([head, plot([])], [nbLegend(.yellow, "Query")], "A document is {an arrow} in term space.",
              "Each axis counts one word. The query mentions \"learning\" twice and \"proof\" once, so it points to (2, 1).", fx: ["query = (2, 1)"]),
        frame([head, plot(["short"])], Array(legend.prefix(2)), "\"short\" points {almost the same way}.",
              "(2, 1.2) is nearly parallel to the query: same topic, slightly more \"proof\".", fx: ["cos = (2·2 + 1·1.2) / (|q|·|d|) = {\(nbF(cos(query, docs[0].1), 3))}"]),
        frame([head, plot(["short", "long"])], Array(legend.prefix(3)), "\"long\" is four times bigger — {same direction}.",
              "(8, 5) is the same mix of words written at length. Cosine ignores length, so it scores \(nbF(cLong, 3)).", fx: ["cos(query, long) = {\(nbF(cLong, 3))}"]),
        frame([head, plot(["short", "long", "theory"])], legend, "\"theory\" leans toward the other word.",
              "(1, 3) is mostly \"proof\". The angle to the query is 45°, cosine \(nbF(cos(query, docs[2].1), 3)).", fx: ["cos(query, theory) = 5 / (√5·√10) = {\(nbF(cos(query, docs[2].1), 3))}"]),
        frame([head, plot(["short", "long", "theory"], distances: true), tbl("long")], legend + [nbLegend(.grey, "Distance", .dashedLine)],
              "Now rank the corpus against a query.",
              "\"long\" points the query's way — cosine ranks it \(longRank)\(longRank == 2 ? "nd" : "th") — but distance ranks it last just for being long. That flip is why retrieval uses cosine."),
        frame([.caption("normalise first · every vector to length 1"), table(["doc", "unit vector", "distance"], [0.8, 1.2, 0.7], docs.map { name, p, ink in
            let l = hypot(p.x, p.y)
            let u = NbP(p.x / l, p.y / l)
            return NbRow(cells: [nbName(name, ink), nbCell("(\(f2(u.x)), \(f2(u.y)))"), nbCell(nbF(dist(qn, u), 3))])
        })], Array(legend.dropFirst()),
              "On unit vectors, distance and cosine {agree}.",
              "|a − b|² = 2 − 2·cos(a, b) when both have length 1. Vector databases normalise once and then use whichever is faster."),
        frame([.kv([("range", "−1 to 1 (0 to 1 for counts)"), ("ignores", "length — how much is written"), ("measures", "direction — what it is about"), ("used in", "search, embeddings, deduplication")])], [],
              "Cosine compares {what} a document says, not how much.",
              "That is the right notion for text, where a long article and a short note on the same topic should match."),
    ]
}

// MARK: - Jaccard

private func orderedSet(_ xs: [String]) -> [String] { var seen = Set<String>(); return xs.filter { seen.insert($0).inserted } }

private func jaccardFrames() -> [NbFrame] {
    let a = words("the model learns from data and more data"), b = words("the model learns from data")
    let sa = Set(a), sb = Set(b)
    let inter = sa.intersection(sb), union = sa.union(sb)
    let j = Double(inter.count) / Double(union.count)
    func aToks() -> NbBlock {
        var seen = Set<String>()
        return .toks(a.map { w in seen.insert(w).inserted ? nbTok(w, sb.contains(w) ? .pick : .hot) : nbTok(w, .dim, "dup") })
    }
    let bToks = NbBlock.toks(b.map { nbTok($0, .pick) })
    let counts = NbBlock.tiles([NbTile(big: "\(sa.count)", caption: "|A|"), NbTile(big: "\(sb.count)", caption: "|B|"), NbTile(big: "\(inter.count)", caption: "|A ∩ B|", ink: .indigo), NbTile(big: "\(union.count)", caption: "|A ∪ B|")])
    let legend = [nbLegend(.violet, "Shared"), nbLegend(.yellow, "Only in A"), nbLegend(.slate, "Repeat — ignored")]
    let head = NbBlock.caption("two sentences as sets")
    let c = orderedSet(words("the model learns")), d = orderedSet(words("a network is trained"))
    let jcd = Double(Set(c).intersection(Set(d)).count) / Double(Set(c).union(Set(d)).count)
    func shingles(_ s: String) -> [String] { let ch = Array(s); return orderedSet((0...(ch.count - 3)).map { String(ch[$0..<$0 + 3]) }) }
    let s1 = shingles("learning"), s2 = shingles("learner")
    let set1 = Set(s1), set2 = Set(s2)
    let js = Double(set1.intersection(set2).count) / Double(set1.union(set2).count)
    let vocab = orderedSet(a + b)
    let va = vocab.map { w in Double(a.filter { $0 == w }.count) }, vb = vocab.map { w in Double(b.filter { $0 == w }.count) }
    return [
        frame([head, .caption("A · \(a.count) tokens"), aToks(), .caption("B · \(b.count) tokens"), bToks], legend, "Treat each sentence as {a set of words}.",
              "Order and counts are dropped; only which words appear matters. A has \(a.count) tokens but \(sa.count) distinct words."),
        frame([head, .caption("A · \(a.count) tokens"), aToks(), .caption("B · \(b.count) tokens"), bToks, counts, .banner(big: nbF(j, 3), text: "J = \(inter.count) / \(union.count)", ink: .violet, bigRight: true)], legend,
              "J(A, B) = |A ∩ B| / |A ∪ B|.",
              "\"data\" appears twice in A and counts once. The repetition is invisible to the set — that is the design, not an oversight."),
        frame([.caption("same meaning, no shared words"), .toks(c.map { nbTok($0, .hot) }, label: "C"), .toks(d.map { nbTok($0, .hot) }, label: "D"),
               .banner(big: nbF(jcd, 3), text: "J = 0 / \(Set(c).union(Set(d)).count)", ink: .red, bigRight: true)], [nbLegend(.yellow, "No overlap")],
              "Paraphrases score {zero}.",
              "\"the model learns\" and \"a network is trained\" say the same thing with different words. Jaccard only sees surface overlap."),
        frame([.caption("character 3-grams · \"learning\" vs \"learner\""), .toks(s1.map { nbTok($0, set2.contains($0) ? .pick : .hot) }, mono: true),
               .toks(s2.map { nbTok($0, set1.contains($0) ? .pick : .hot) }, mono: true),
               .banner(big: nbF(js, 3), text: "J = \(set1.intersection(set2).count) / \(set1.union(set2).count)", ink: .violet, bigRight: true)], Array(legend.prefix(2)),
              "On character shingles it {catches spelling variants}.",
              "Splitting words into 3-grams gives typos and inflections partial credit — the basis of fuzzy matching."),
        frame([.caption("near-duplicate detection"), .kv([("each document", "set of 5-word shingles"), ("MinHash", "estimates J without comparing sets"), ("threshold", "J > 0.8 → duplicate"), ("used for", "deduplicating training corpora")])], [],
              "Its real home: {finding near-duplicates} at scale.",
              "Web-scale corpora are deduplicated by Jaccard on shingles, estimated with MinHash so billions of pairs never have to be compared directly."),
        frame([table(["", "Jaccard", "cosine"], [0.8, 1, 1], [
            NbRow(cells: [nbName("input"), nbCell("sets"), nbCell("vectors (counts)")]),
            NbRow(cells: [nbName("counts"), nbCell("ignored", .yellow), nbCell("used", .green)]),
            NbRow(cells: [nbName("length"), nbCell("hurts (bigger union)"), nbCell("ignored", .green)]),
            NbRow(cells: [nbName("A vs B"), nbCell(nbF(j, 3), .violet), nbCell(nbF(cosine(va, vb), 3), .violet)]),
        ])], [], "Jaccard for sets, {cosine} for weighted vectors.",
              "On the same two sentences cosine also counts the repeated \"data\" — the measures agree on the ranking but not the scale."),
    ]
}

// MARK: - Hidden Markov models

private let hmmTags = ["NN", "VB", "DT", "IN"]
private let hmmWords = ["book", "that", "flight"]
private let hmmPi = [0.5, 0.2, 0.2, 0.1]
private let hmmA: [[Double]] = [[0.2, 0.3, 0.1, 0.4], [0.1, 0.05, 0.6, 0.25], [0.9, 0.05, 0.0, 0.05], [0.3, 0.05, 0.6, 0.05]]
private let hmmB: [[Double]] = [[0.03, 0.0, 0.02], [0.04, 0.0, 0.0], [0.0, 0.15, 0.0], [0.0, 0.25, 0.0]]

private func sci(_ v: Double) -> String { v == 0 ? "0" : lsSci(v) }

private func hmmFrames() -> [NbFrame] {
    let k = hmmTags.count, t = hmmWords.count
    var v = [[Double]](repeating: [Double](repeating: 0, count: k), count: t)
    var back = [[Int]](repeating: [Int](repeating: 0, count: k), count: t)
    for s in 0..<k { v[0][s] = hmmPi[s] * hmmB[s][0] }
    for i in 1..<t {
        for s in 0..<k {
            var best = 0.0, arg = 0
            for p in 0..<k { let c = v[i - 1][p] * hmmA[p][s]; if c > best { best = c; arg = p } }
            v[i][s] = best * hmmB[s][i]
            back[i][s] = arg
        }
    }
    var last = (0..<k).max { v[t - 1][$0] < v[t - 1][$1] }!
    var path = [Int](repeating: 0, count: t)
    path[t - 1] = last
    for i in stride(from: t - 1, through: 1, by: -1) { last = back[i][last]; path[i - 1] = last }
    var greedy = [Int](repeating: 0, count: t)
    var gScore = 1.0
    for i in 0..<t {
        let scores = (0..<k).map { s in (i == 0 ? hmmPi[s] : hmmA[greedy[i - 1]][s]) * hmmB[s][i] }
        greedy[i] = scores.indices.max { scores[$0] < scores[$1] }!
        gScore *= scores[greedy[i]]
    }
    let best = v[t - 1][path[t - 1]]
    let bMax = hmmB.flatMap { $0 }.max()!
    let emission = NbBlock.grid(NbGridBlock(cols: hmmWords, rows: hmmTags, cells: hmmB.map { row in row.enumerated().map { w, p in
        NbGCell(text: p == 0 ? "0" : nbF(p, 3), level: p == 0 ? 0 : 0.3 + 0.7 * p / bMax, ink: .blue, ring: w == 0 && p > 0)
    } }))
    let aMax = hmmA.flatMap { $0 }.max()!
    let transition = NbBlock.grid(NbGridBlock(cols: hmmTags, rows: hmmTags.map { "\($0) →" }, cells: hmmA.map { row in row.map { NbGCell(text: f2($0), level: $0 == 0 ? 0 : 0.25 + 0.7 * $0 / aMax, ink: .violet) } }, cellHeight: 26))
    func lattice(_ upTo: Int, _ mark: [Int]?) -> NbBlock {
        .grid(NbGridBlock(cols: hmmWords, rows: hmmTags, cells: (0..<k).map { s in (0..<t).map { i in
            i > upTo ? NbGCell(text: "", level: 0) : NbGCell(text: sci(v[i][s]), level: v[i][s] == 0 ? 0 : 0.35 + 0.6 * (v[i][s] / v[i].max()!), ink: .violet, ring: mark?[i] == s)
        } }))
    }
    let candidates = table(hmmWords, hmmWords.map { _ in 1 }, [NbRow(cells: hmmWords.indices.map { w in nbCell(hmmTags.indices.filter { hmmB[$0][w] > 0 }.map { hmmTags[$0] }.joined(separator: " · ")) })], aligns: [1, 1, 1])
    func tagStr(_ p: [Int]) -> String { p.map { hmmTags[$0] }.joined(separator: " ") }
    let latticeLegend = [nbLegend(.violet, "v(t, tag)")]
    return [
        frame([.caption("\"book that flight\" · the tags are hidden"), .toks(hmmWords.map { nbTok($0, .plain, "?") }), .caption("tags each word can take (B > 0)"), candidates], [],
              "Words are observed; {tags are hidden}.",
              "\"book\" can be a noun or a verb, \"that\" a determiner or a preposition. An HMM picks the tag sequence that best explains the words."),
        frame([.caption("emission table B(tag → word) · from a tagged corpus"), emission, .caption("tags each word can take (B > 0)"), candidates],
              [nbLegend(.blue, "P(word | tag)"), nbLegend(.yellow, "Comparison in caption", .ring)],
              "The model is two tables estimated by counting.",
              "This is the emission half. P(book | VB) beats P(book | NN): verbs are rarer, so when one appears it's more likely to be this word. Transitions come next."),
        frame([.caption("transition table A(previous → next) and start π"), transition, .callout(["π = " + hmmTags.indices.map { "\(hmmTags[$0]) \(f2(hmmPi[$0]))" }.joined(separator: " · ")])],
              [nbLegend(.violet, "P(tag | previous tag)")],
              "The other half: {which tag follows which}.",
              "A determiner is followed by a noun 90% of the time. Sentences usually start with a noun here (π = 0.5), which is what misleads a greedy tagger."),
        frame([.caption("greedy · best tag per word, left to right"), .toks(hmmWords.indices.map { nbTok(hmmWords[$0], .hot, hmmTags[greedy[$0]]) }),
               .callout(["book: NN \(nbF(hmmPi[0] * hmmB[0][0], 3)) vs VB \(nbF(hmmPi[1] * hmmB[1][0], 3)) → {NN}", "score = {\(sci(gScore))}"])],
              [nbLegend(.yellow, "Greedy choice")],
              "Greedy commits to {\(tagStr(greedy))}.",
              "At \"book\", the strong noun start wins locally (0.015 vs 0.008). Every later choice is stuck with it."),
        frame([.caption("Viterbi lattice · best path probability into each cell"), lattice(0, nil)], latticeLegend,
              "Viterbi keeps {every tag} alive at each word.",
              "Column 1 is π × B. Nothing is decided yet — VB stays in the running with \(sci(v[0][1])).",
              fx: ["v(book, VB) = π(VB)·B(VB, book) = 0.2 × 0.04 = {\(sci(v[0][1]))}"]),
        frame([.caption("Viterbi lattice · best path probability into each cell"), lattice(2, nil)], latticeLegend,
              "Each cell takes {the best way in}.",
              "v(t, s) = max over previous tags of v(t − 1, p)·A(p, s), times B(s, word). Back-pointers remember which p won.",
              fx: ["v(that, DT) = max(…, v(book, VB)·0.6)·0.15 = {\(sci(v[1][2]))}"]),
        frame([.caption("Viterbi lattice · best path ringed"), lattice(2, path),
               .tiles([NbTile(big: tagStr(path), caption: "Viterbi · \(sci(best))", ink: .green), NbTile(big: tagStr(greedy), caption: "greedy · \(sci(gScore))", ink: .red)])],
              latticeLegend + [nbLegend(.yellow, "Best path", .ring)],
              "Follow the back-pointers: {\(tagStr(path))}.",
              "The full path beats greedy's by \(nbF(best / gScore, 2))×. Viterbi is exact and costs T·K² — \(t)·\(k)² = \(t * k * k) steps here instead of \(k)^\(t) = \(Int(pow(Double(k), Double(t)))) paths."),
    ]
}

// MARK: - Word embeddings

private let embDims = ["royal", "male", "female", "human", "fruit"]
private let embWords = ["king", "queen", "man", "woman", "apple"]
private let embVec: [[Double]] = [
    [0.95, 0.90, 0.05, 0.80, 0.00], [0.95, 0.05, 0.90, 0.80, 0.00], [0.10, 0.90, 0.05, 0.90, 0.00],
    [0.10, 0.05, 0.90, 0.90, 0.00], [0.00, 0.00, 0.00, 0.05, 0.95],
]

private func embeddingFrames() -> [NbFrame] {
    func grid(_ hot: Int? = nil, _ extra: (String, [Double])? = nil) -> NbBlock {
        .grid(NbGridBlock(cols: embDims, rows: embWords + (extra.map { [$0.0] } ?? []), cells: (embVec + (extra.map { [$0.1] } ?? [])).map { v in v.map { NbGCell(text: nbDot2($0), level: max($0, 0), ink: .blue) } }, hotRow: hot))
    }
    let king = embVec[0]
    let sims = (1..<5).map { (embWords[$0], cosine(king, embVec[$0])) }.sortedByDescending { $0.1 }
    let simBars = NbBlock.bars(sims.map { NbBar(label: $0.0, value: f2($0.1), frac: $0.1, ink: .violet) }, labelWidth: 64)
    let target = (0..<5).map { king[$0] - embVec[2][$0] + embVec[3][$0] }
    let ranked = embWords.indices.filter { ![0, 2, 3].contains($0) }.map { (embWords[$0], cosine(target, embVec[$0])) }.sortedByDescending { $0.1 }
    let oneHot = embWords.indices.map { i in (0..<5).map { $0 == i ? 1.0 : 0.0 } }
    return [
        frame([.caption("5 words × 5 dimensions"), grid(0), .caption("cosine similarity to \"king\""), simBars], [nbLegend(.blue, "Dimension value"), nbLegend(.violet, "Similarity")],
              "Each word is a dense vector.",
              "Words that share dimensions point the same way: queen and man each share two of king's three strong features; apple shares none. Labels are for readability — trained dimensions have no names."),
        frame([.caption("one-hot · each word its own axis"), .grid(NbGridBlock(cols: embWords, rows: embWords, cells: oneHot.map { v in v.map { NbGCell(text: $0 == 1 ? "1" : "0", level: $0, ink: .grey) } })),
               .callout(["cos(king, queen) = {0}", "cos(king, apple) = {0}"])], [nbLegend(.grey, "One-hot")],
              "One-hot vectors make {every pair equally unrelated}.",
              "With one axis per word, any two different words are orthogonal. Dense vectors share axes, so similarity can be graded."),
        frame([.caption("king − man + woman"), grid(5, ("result", target)),
               .bars(ranked.enumerated().map { i, r in NbBar(label: r.0, value: f2(r.1), frac: max(r.1, 0), ink: i == 0 ? .green : .violet) }, labelWidth: 64)],
              [nbLegend(.blue, "Dimension value"), nbLegend(.green, "Nearest remaining word")],
              "Vector arithmetic: king − man + woman ≈ {m:\(ranked[0].0)}.",
              "Subtracting man removes \"male\" and adds nothing royal; adding woman puts \"female\" back. The nearest word left is \(ranked[0].0) (cosine \(f2(ranked[0].1)))."),
        frame([.kv([("this toy", "5 hand-set, named dimensions"), ("word2vec / GloVe", "100–300 learned dimensions"), ("dimensions", "unnamed, rotated, mixed"), ("where it comes from", "words in similar contexts")])], [],
              "Real embeddings are {learned from context}.",
              "Nobody sets the numbers. Training nudges words that appear in similar contexts toward similar vectors — CBOW and skip-gram are the two classic ways."),
    ]
}

// MARK: - Word2Vec

private let w2vSentence = ["the", "king", "rules", "the", "kingdom"]
private let w2vVocab = ["the", "king", "rules", "kingdom"]

private struct W2vRng {
    var s: Int64
    init(_ seed: Int64) { s = seed }
    mutating func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
}

/// CBOW examples: the context words within ±2 and the centre word.
private func cbowExamples() -> [([String], String)] {
    w2vSentence.indices.map { c in ((c - 2)...(c + 2)).filter { $0 != c && w2vSentence.indices.contains($0) }.map { w2vSentence[$0] } }
        .enumerated().map { ($1, w2vSentence[$0]) }
}

/// Skip-gram pairs: centre → each context word, with its offset.
private func skipPairs() -> [(String, String, Int)] {
    w2vSentence.indices.flatMap { c in ((c - 2)...(c + 2)).filter { $0 != c && w2vSentence.indices.contains($0) }.map { (w2vSentence[c], w2vSentence[$0], $0 - c) } }
}

private struct W2v {
    var inV: [[Double]]
    var outV: [[Double]]
    func probs(_ h: [Double]) -> [Double] {
        let s = outV.map { o in h.indices.reduce(0.0) { $0 + h[$1] * o[$1] } }
        let m = s.max()!
        let e = s.map { exp($0 - m) }
        let z = e.reduce(0, +)
        return e.map { $0 / z }
    }
    func hidden(_ ctx: [String]) -> [Double] {
        let ids = ctx.map { w2vVocab.firstIndex(of: $0)! }
        return (0..<inV[0].count).map { d in ids.reduce(0.0) { $0 + inV[$1][d] } / Double(ids.count) }
    }
}

private func w2vInit() -> W2v {
    var r = W2vRng(5)
    var inV: [[Double]] = [], outV: [[Double]] = []
    for _ in 0..<4 { var row: [Double] = []; for _ in 0..<3 { row.append((r.u() - 0.5) * 0.2) }; inV.append(row) }
    for _ in 0..<4 { var row: [Double] = []; for _ in 0..<3 { row.append((r.u() - 0.5) * 0.2) }; outV.append(row) }
    return W2v(inV: inV, outV: outV)
}

/// Full-softmax SGD over the examples; `cbow` averages the context, otherwise each pair is its own example.
private func w2vTrain(_ cbow: Bool, _ epochs: Int, _ rate: Double = 0.5) -> W2v {
    var m = w2vInit()
    let examples: [([String], String)] = cbow ? cbowExamples() : skipPairs().map { ([$0.0], $0.1) }
    for _ in 0..<epochs {
        for (ctx, target) in examples {
            let h = m.hidden(ctx)
            let p = m.probs(h)
            let t = w2vVocab.firstIndex(of: target)!
            var grad = [0.0, 0.0, 0.0]
            for o in 0..<4 {
                let e = p[o] - (o == t ? 1.0 : 0.0)
                for d in 0..<3 { grad[d] += e * m.outV[o][d]; m.outV[o][d] -= rate * e * h[d] }
            }
            for w in ctx { let i = w2vVocab.firstIndex(of: w)!; for d in 0..<3 { m.inV[i][d] -= rate * grad[d] / Double(ctx.count) } }
        }
    }
    return m
}

private func cbowFrames() -> [NbFrame] {
    let ex = cbowExamples()
    let head = NbBlock.caption("\"the king rules the kingdom\" · window ±2")
    func sentence(_ centre: Int) -> NbBlock { .toks(w2vSentence.enumerated().map { i, w in i == centre ? nbTok("_____", .empty) : nbTok(w, abs(i - centre) <= 2 ? .hot : .plain) }) }
    func tbl(_ ring: Int) -> NbBlock { table(["context (input)", "target"], [1.6, 0.7], ex.enumerated().map { i, e in NbRow(cells: [nbName(e.0.joined(separator: ", ")), nbCell(e.1, i == ring ? .indigo : nil)], ring: i == ring) }) }
    let flow = NbBlock.flow(rows: [[nbTok("the", .hot), nbTok("king", .hot)], [nbTok("the", .hot), nbTok("kingdom", .hot)]], caption: "average\n→ softmax", target: nbTok("rules", .pick))
    let legend = [nbLegend(.yellow, "Context"), nbLegend(.violet, "Target")]
    let m0 = w2vInit()
    let trained = w2vTrain(true, 200)
    let ctx = ex[2].0
    let p0 = m0.probs(m0.hidden(ctx)), p1 = trained.probs(trained.hidden(ctx))
    let ri = w2vVocab.firstIndex(of: "rules")!
    func probBars(_ p: [Double]) -> NbBlock { .bars(w2vVocab.indices.map { NbBar(label: w2vVocab[$0], value: f2(p[$0]), frac: p[$0], ink: $0 == ri ? .violet : .grey) }, labelWidth: 72) }
    let h = trained.hidden(ctx)
    func lossOf(_ m: W2v) -> Double { ex.reduce(0.0) { $0 - log(m.probs(m.hidden($1.0))[w2vVocab.firstIndex(of: $1.1)!]) } / Double(ex.count) }
    let loss0 = lossOf(m0), loss1 = lossOf(trained)
    let mid = w2vTrain(true, 20)
    let target = [nbLegend(.violet, "Target \"rules\"")]
    var avgRows = orderedSet(ctx).map { w -> NbRow in let v = trained.inV[w2vVocab.firstIndex(of: w)!]; return NbRow(cells: [nbName(w), nbCell(f2(v[0])), nbCell(f2(v[1])), nbCell(f2(v[2]))]) }
    avgRows.append(NbRow(cells: [nbName("average", .yellow), nbCell(f2(h[0]), .yellow), nbCell(f2(h[1]), .yellow), nbCell(f2(h[2]), .yellow)]))
    return [
        frame([head, sentence(2), flow, .caption("all \(ex.count) training examples from this sentence"), tbl(2)], legend,
              "CBOW turns the corpus into fill-in-the-blank.",
              "Hide the centre word and guess it from its window. The text labels itself, so no annotation is needed."),
        frame([head, sentence(0), tbl(0)], legend, "Near the edge the window is {shorter}.",
              "\"the\" at position 1 has only two words to its right. CBOW averages however many context words there are, so every position yields one example."),
        frame([.caption("context vectors averaged · 3 dimensions"), table(["word", "v₁", "v₂", "v₃"], [1, 0.6, 0.6, 0.6], avgRows)], legend,
              "The four context vectors are {averaged} into one.",
              "\"the\" appears twice in the window and counts twice. Averaging throws away order — the \"bag\" in continuous bag-of-words."),
        frame([.caption("softmax over the vocabulary · before training"), probBars(p0)], target,
              "Untrained, every word is {about equally likely}.",
              "Small random vectors give near-uniform scores: P(rules) = \(f2(p0[ri])) out of 4 words. The loss is −log P(target) = \(f2(-log(p0[ri])))."),
        frame([.caption("softmax over the vocabulary · after 20 passes"), probBars(mid.probs(mid.hidden(ctx)))], target,
              "Each update {pulls the target up}.",
              "The gradient raises the target's score and lowers the others', and moves the context vectors toward the target's output vector."),
        frame([.caption("softmax over the vocabulary · after 200 passes"), probBars(p1), .tiles([NbTile(big: f2(loss0), caption: "mean loss, start", ink: .grey), NbTile(big: f2(loss1), caption: "after 200 passes", ink: .indigo)])], target,
              "Trained: P(rules | context) = {\(f2(p1[ri]))}.",
              "The loss fell from \(f2(loss0)) to \(f2(loss1)). The two \"the\" examples can't both be perfect — they have different contexts but the same answer, and different answers for similar contexts."),
        frame([.kv([("vocabulary", "4 here · 10⁵–10⁶ real"), ("softmax cost", "one score per vocabulary word"), ("fix", "negative sampling: 5–20 random words"), ("kept after training", "the input vectors")])], [],
              "At real scale, {negative sampling} replaces the softmax.",
              "Scoring every word for every example is too slow with a big vocabulary. Word2vec scores the target against a handful of random words instead; the input vectors are the embeddings."),
    ]
}

private func skipGramFrames() -> [NbFrame] {
    let pairs = skipPairs()
    let cbow = cbowExamples().count
    let head = NbBlock.caption("\"the king rules the kingdom\" · window ±2")
    func sentence(_ centre: Int) -> NbBlock { .toks(w2vSentence.enumerated().map { i, w in nbTok(w, i == centre ? .pick : abs(i - centre) <= 2 ? .hot : .plain) }) }
    func pairTable(_ centre: Int) -> NbBlock {
        table([], [0.7, 0.3, 1, 0.4], ((centre - 2)...(centre + 2)).filter { $0 != centre && w2vSentence.indices.contains($0) }.map { i in
            let o = i - centre
            return NbRow(cells: [nbName(w2vSentence[centre], .indigo), nbCell("→"), nbName(w2vSentence[i], .yellow), nbCell(o > 0 ? "+\(o)" : "−\(-o)")])
        }, aligns: [0, 1, 0, 2])
    }
    let legend = [nbLegend(.violet, "Centre (input)"), nbLegend(.yellow, "Context (predicted)")]
    let counts = NbBlock.tiles([NbTile(big: "\(cbow)", caption: "CBOW examples from this sentence", ink: .grey), NbTile(big: "\(pairs.count)", caption: "Skip-gram pairs from this sentence", ink: .indigo)])
    let trained = w2vTrain(false, 200)
    let ri = w2vVocab.firstIndex(of: "rules")!
    let p = trained.probs(trained.inV[ri])
    let fromRules = pairs.filter { $0.0 == "rules" }
    let freq = w2vVocab.map { w in Double(fromRules.filter { $0.1 == w }.count) / Double(fromRules.count) }
    return [
        frame([head, sentence(2), .caption("one centre word → four separate predictions"), pairTable(2), counts], legend, "Skip-gram inverts CBOW.",
              "One centre word, and the model predicts each neighbour separately. The window that gave CBOW one example gives skip-gram 4; the whole sentence gives \(pairs.count)."),
        frame([head, sentence(1), pairTable(1)], legend, "Centre \"king\": {three} pairs.",
              "Position 2 has one word to its left. Edge words make fewer pairs, so \(pairs.count) rather than 5 × 4 = 20."),
        frame([head, sentence(4), pairTable(4)], legend, "Centre \"kingdom\": two pairs, both {to the left}.",
              "Offsets are recorded for clarity only — the model ignores them. A neighbour two away is predicted exactly like one next door."),
        frame([.caption("all \(pairs.count) pairs"), .toks(pairs.map { nbTok("\($0.0)→\($0.1)", $0.0 == "rules" ? .pick : .plain) }, mono: true)], legend,
              "Every pair is {its own training example}.",
              "Where CBOW blurs four context words into one average, skip-gram gives each a separate gradient. Rare words get more updates, which is why skip-gram represents them better."),
        frame([.caption("P(context | \"rules\") after 200 passes"), .bars(w2vVocab.indices.map { NbBar(label: w2vVocab[$0], value: f2(p[$0]), frac: p[$0], ink: $0 == ri ? .grey : .yellow) }, labelWidth: 72),
               .callout(["pair frequencies: " + w2vVocab.indices.filter { freq[$0] > 0 }.map { "\(w2vVocab[$0]) \(f2(freq[$0]))" }.joined(separator: " · ")])],
              [nbLegend(.yellow, "Predicted neighbour")],
              "The model learns the {neighbour distribution}.",
              "\"rules\" sees \"the\" twice and king, kingdom once each, so the target is 0.50 / 0.25 / 0.25. After 200 passes it gives " +
                "\(f2(p[0])) / \(f2(p[1])) / \(f2(p[3])): the same output vectors also serve the other four centre words, so it can only get close."),
        frame([table(["", "CBOW", "skip-gram"], [0.9, 1, 1], [
            NbRow(cells: [nbName("predicts"), nbCell("centre from context"), nbCell("context from centre")]),
            NbRow(cells: [nbName("examples"), nbCell("\(cbow)"), nbCell("\(pairs.count)", .indigo)]),
            NbRow(cells: [nbName("speed"), nbCell("faster", .green), nbCell("slower")]),
            NbRow(cells: [nbName("rare words"), nbCell("averaged away"), nbCell("better", .green)]),
        ])], [], "CBOW is faster; skip-gram {handles rare words better}.",
              "Same vectors, same window, opposite direction. Mikolov et al. found skip-gram stronger on semantic analogies and CBOW quicker to train."),
        frame([.kv([("negative sampling", "k random 'not neighbours' per pair"), ("subsampling", "drop frequent words like 'the'"), ("window", "random 1…5, nearer words count more"), ("output", "input vectors = embeddings")])], [],
              "Two tricks make it scale: {negative sampling and subsampling}.",
              "Frequent words like \"the\" are mostly dropped before pairing, and each pair is scored against a few random words instead of the whole vocabulary."),
    ]
}

// MARK: - RNN / LSTM
// A 4-unit recurrence h = tanh(x + 0.6·h) over hand-set input vectors, run here step by step. The
// gradient figures are the real Jacobian products along this sentence: 0.6·(1 − h²) per step.

private let rnnTokens = ["the", "movie", "was", "not", "good"]
private let rnnDims = ["topic", "polarity", "negation", "syntax"]
private let rnnInputs: [String: [Double]] = [
    "the": [0.1, 0.0, 0.0, 0.1], "movie": [0.6, 0.2, 0.0, 0.1], "was": [0.1, 0.1, 0.0, 0.2],
    "not": [0.0, 0.0, -0.9, 0.3], "good": [0.0, 0.7, 0.5, 0.1],
]
private let rnnU = 0.6

private func rnnRun(_ tokens: [String]) -> [[Double]] {
    var h = [Double](repeating: 0, count: 4)
    return tokens.map { t in
        let x = rnnInputs[t]!
        h = (0..<4).map { tanh(x[$0] + rnnU * h[$0]) }
        return h
    }
}

private func signedCell(_ v: Double) -> NbGCell { NbGCell(text: nbF(v, 2), level: min(abs(v), 1), ink: v < 0 ? .pink : .blue) }

private func rnnFrames() -> [NbFrame] {
    let states = rnnRun(rnnTokens)
    let reversed = rnnRun(rnnTokens.reversed())
    func grid(_ upTo: Int) -> NbBlock {
        .grid(NbGridBlock(cols: rnnTokens.indices.map { "h\($0 + 1)" }, rows: rnnDims,
                          cells: rnnDims.indices.map { d in rnnTokens.indices.map { t in t > upTo ? NbGCell(text: "", level: 0) : signedCell(states[t][d]) } }, cellHeight: 26))
    }
    func toks(_ cur: Int) -> NbBlock { .toks(rnnTokens.enumerated().map { i, w in nbTok(w, i == cur ? .hot : i < cur ? .plain : .dim) }) }
    let legend = [nbLegend(.blue, "Positive"), nbLegend(.pink, "Negative"), nbLegend(.yellow, "Current token")]
    let head = NbBlock.caption("\"the movie was not good\" · h = tanh(x + 0.6·h)", note: "4 units")
    let notes: [String: (String, String)] = [
        "the": ("Read \"the\": {a faint start}.", "Almost nothing to remember yet. The state is a small copy of the first input."),
        "movie": ("Read \"movie\": {topic} rises.", "The new state mixes this token's vector with 0.6 of the previous state. \"the\" is still in there, faded."),
        "was": ("Read \"was\": the state {drifts}.", "A function word adds little. Topic decays toward its new input — each step keeps only part of the past."),
        "not": ("Read \"not\": {w:negation} goes sharply negative.", "For the sentence to be read correctly, this has to survive the next step — the RNN has no other place to keep it."),
        "good": ("Read \"good\": polarity rises, {negation is cancelled}.",
                 "\"good\" pushes the negation unit up by 0.5 while 0.6 × (\(nbF(states[3][2], 2))) carried from \"not\" pulls it down: it lands at \(nbF(states[4][2], 2)). The state now holds the interaction — which is why word order matters."),
    ]
    var frames: [NbFrame] = []
    frames.append(NbFrame(blocks: [head, toks(-1), grid(-1), .kv([("input", "one token vector x"), ("memory", "the previous state h"), ("update", "h = tanh(W·x + U·h)"), ("here", "W = I, U = 0.6·I")])],
                          legend: legend, headline: "An RNN reads one token at a time and keeps {a single hidden state}.",
                          body: "Everything it remembers about the sentence so far has to fit in these four numbers. The same weights are reused at every step."))
    for (t, w) in rnnTokens.enumerated() {
        let x = rnnInputs[w]!
        let prev = t == 0 ? [Double](repeating: 0, count: 4) : states[t - 1]
        let table = NbBlock.table(headers: ["unit", "x", "0.6·h", "h new"], weights: [0.9, 0.6, 0.6, 0.6], rows: rnnDims.indices.map { d in
            NbRow(cells: [nbName(rnnDims[d]), nbCell(nbF(x[d], 2)), nbCell(nbF(rnnU * prev[d], 2)), nbCell(nbF(states[t][d], 2), states[t][d] < 0 ? .pink : .blue, bold: true)])
        })
        let (hl, body) = notes[w]!
        frames.append(NbFrame(blocks: [head, toks(t), grid(t), table], legend: legend, headline: hl, body: body, fx: ["h\(t + 1) = tanh(x(\"\(w)\") + 0.6·h\(t))"]))
    }
    let last = states.last!, rlast = reversed.last!
    let diff = last.indices.max { abs(last[$0] - rlast[$0]) < abs(last[$1] - rlast[$1]) }!
    frames.append(NbFrame(blocks: [
        .caption("final state · same words, opposite order"),
        .grid(NbGridBlock(cols: ["forward", "reversed"], rows: rnnDims, cells: rnnDims.indices.map { [signedCell(last[$0]), signedCell(rlast[$0])] }, cellHeight: 26)),
        .toks(rnnTokens.reversed().map { nbTok($0) }, label: "reversed"),
    ], legend: Array(legend.prefix(2)), headline: "Reverse the words, {get a different state}.",
       body: "Read backwards, \"not\" comes after \"good\" and the units end up elsewhere — \(rnnDims[diff]) differs most (\(nbF(last[diff], 2)) vs \(nbF(rlast[diff], 2))). A bag of words could never tell these apart."))
    // ∂h5/∂h_t per unit: product over later steps of 0.6·(1 − h²); averaged over the four units.
    let influence = rnnTokens.indices.map { t in
        (0..<4).map { d in ((t + 1)..<rnnTokens.count).reduce(1.0) { $0 * rnnU * (1 - states[$1][d] * states[$1][d]) } }.reduce(0, +) / 4
    }
    frames.append(NbFrame(blocks: [
        .caption("how much h5 depends on each earlier state · |∂h5/∂h_t|"),
        .bars(rnnTokens.indices.map { NbBar(label: "h\($0 + 1) \"\(rnnTokens[$0])\"", value: nbF(influence[$0], 3), frac: influence[$0], ink: $0 == 0 ? .red : .sky) }, labelWidth: 104),
    ], legend: [nbLegend(.sky, "Gradient reaching that step"), nbLegend(.red, "First token")],
       headline: "Signal from early tokens {fades geometrically}.",
       body: "Each step multiplies the path back by 0.6 × tanh′ ≤ 0.6. After four steps \"the\" contributes \(nbF(influence[0], 3)) — so the gradient that should teach the model about early words all but vanishes.",
       fx: ["∂h_t/∂h_(t−1) = 0.6 · (1 − h_t²)  ≤ 0.6"]))
    let forget = 0.95
    frames.append(NbFrame(blocks: [
        .caption("signal kept after n steps"),
        .plot(NbPlotBlock(xr: (1, 20), yr: (0, 1), height: 170,
                          lines: [NbLine(pts: (1...20).map { NbP(Double($0), pow(rnnU, Double($0))) }, ink: .red), NbLine(pts: (1...20).map { NbP(Double($0), pow(forget, Double($0))) }, ink: .green)],
                          xTicks: [(1, "1"), (5, "5"), (10, "10"), (15, "15"), (20, "20")], yTicks: [(0, "0"), (0.5, "0.5"), (1, "1")], xLabel: "steps")),
        .tiles([NbTile(big: nbF(pow(rnnU, 20), 5), caption: "plain RNN after 20", ink: .red), NbTile(big: nbF(pow(forget, 20), 2), caption: "LSTM cell, forget 0.95", ink: .green)]),
    ], legend: [nbLegend(.red, "RNN · 0.6ⁿ", .line), nbLegend(.green, "LSTM cell · 0.95ⁿ", .line)],
       headline: "An LSTM's cell state {keeps the path open}.",
       body: "The cell is updated by addition, scaled only by a forget gate. With the gate near 1 the signal after 20 steps is \(nbF(pow(forget, 20), 2)), not \(lsSci(pow(rnnU, 20))).",
       fx: ["c_t = f·c_(t−1) + i·g     ∂c_t/∂c_(t−1) = f"]))
    frames.append(NbFrame(blocks: [
        .caption("the LSTM's three gates · each a sigmoid in 0…1"),
        .boxes([NbBox(title: "forget f", lines: ["how much old cell to keep", "σ(W_f·[h, x])"], ink: .green, lastInk: .green),
                NbBox(title: "input i", lines: ["how much new content to write", "σ(W_i·[h, x])"], ink: .sky, lastInk: .sky),
                NbBox(title: "output o", lines: ["how much cell to expose as h", "σ(W_o·[h, x])"], ink: .violet, lastInk: .violet)]),
        .callout(["c_t = f ⊙ c_(t−1) + i ⊙ tanh(W_g·[h, x])", "h_t = o ⊙ tanh(c_t)"]),
    ], legend: [], headline: "Gates decide what to {forget, write and show}.",
       body: "The network learns when to hold \"not\" in the cell and when to let it go. A GRU merges forget and input into one update gate with similar results and fewer weights."))
    frames.append(NbFrame(blocks: [.kv([("RNN", "one state, fades fast"), ("LSTM / GRU", "gated cell, long memory"), ("still sequential", "token t waits for t − 1"), ("replaced by", "attention: every token sees every other")])],
                          legend: [], headline: "LSTMs carried NLP until {attention} took over.",
                          body: "Gating fixed the memory problem but not the speed problem: a recurrence can't be parallelised over the sequence. Transformers drop the recurrence entirely."))
    return frames
}
