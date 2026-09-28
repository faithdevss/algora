import Foundation

// Port of EmbeddingMath.kt (D3): word2vec, GloVe, FastText and ELMo, each trained for real on a
// toy corpus from fixed LCG seeds, so every number the copy quotes is what this code produces.

func cosineOf(_ a: [Float], _ b: [Float]) -> Float {
    var dot: Float = 0, na: Float = 0, nb: Float = 0
    for i in a.indices {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    return na == 0 || nb == 0 ? 0 : dot / (kSqrt(na) * kSqrt(nb))
}

private struct EmbRng {
    var lcg: Lcg
    init(_ seed: Int) { lcg = Lcg(seed) }
    mutating func next() -> Float { lcg.nextF() }
    mutating func uniform(_ scale: Float) -> Float { (next() - 0.5) * scale }
    mutating func int(_ bound: Int) -> Int { lcg.nextIntF(bound) }
}

private func windowRange(_ i: Int, _ window: Int, _ last: Int) -> ClosedRange<Int> { max(0, i - window)...min(last, i + window) }

/// Top-2 PCA by power iteration with deflation, scaled into [pad, 1 - pad].
private func powerProject(_ rows: [[Float]], pad: Float) -> [(Float, Float)] {
    let dims = rows[0].count
    var mean = [Float](repeating: 0, count: dims)
    for r in rows { for d in 0..<dims { mean[d] += r[d] / Float(rows.count) } }
    let centred = rows.map { r in (0..<dims).map { r[$0] - mean[$0] } }

    func component(_ data: [[Float]], _ seed: Int) -> [Float] {
        var rng = EmbRng(seed)
        var v = (0..<dims).map { _ in rng.uniform(1) }
        for _ in 0..<200 {
            var next = [Float](repeating: 0, count: dims)
            for row in data {
                var dot: Float = 0
                for d in 0..<dims { dot += row[d] * v[d] }
                for d in 0..<dims { next[d] += dot * row[d] }
            }
            let norm = Float(next.reduce(0.0) { $0 + Double($1 * $1) }.squareRoot())
            if norm == 0 { return v }
            v = next.map { $0 / norm }
        }
        return v
    }

    let pc1 = component(centred, 3)
    let deflated = centred.map { row -> [Float] in
        var dot: Float = 0
        for d in 0..<dims { dot += row[d] * pc1[d] }
        return (0..<dims).map { row[$0] - dot * pc1[$0] }
    }
    let pc2 = component(deflated, 5)
    let raw = centred.map { row -> (Float, Float) in
        var x: Float = 0, y: Float = 0
        for d in 0..<dims {
            x += row[d] * pc1[d]
            y += row[d] * pc2[d]
        }
        return (x, y)
    }
    let xs = raw.map(\.0), ys = raw.map(\.1)
    let spanX = xs.max()! - xs.min()! > 1e-6 ? xs.max()! - xs.min()! : 1
    let spanY = ys.max()! - ys.min()! > 1e-6 ? ys.max()! - ys.min()! : 1
    let scale = 1 - 2 * pad
    return raw.map { (pad + scale * ($0.0 - xs.min()!) / spanX, pad + scale * ($0.1 - ys.min()!) / spanY) }
}

// MARK: - word2vec

enum Word2VecLab {
    static let corpus = [
        "the king rules the kingdom", "the queen rules the kingdom",
        "the king wears a crown", "the queen wears a crown",
        "the king sits on the throne", "the queen sits on the throne",
        "the king is a man", "the queen is a woman",
        "a strong king leads the army", "a strong queen leads the army",
        "the man walks the dog", "the woman walks the dog",
        "the man drinks the water", "the woman drinks the water",
        "the man reads a book", "the woman reads a book",
        "a young man works", "a young woman works",
        "the monarch rules the kingdom", "the monarch wears a crown",
    ]

    static let window = 2
    static let dim = 16
    static let negatives = 5
    static let epochs = 300
    static let learningRate: Float = 0.05

    static let sentences: [[String]] = corpus.map { $0.components(separatedBy: " ") }
    static let vocab: [String] = {
        var seen = Set<String>(), out: [String] = []
        for w in sentences.flatMap({ $0 }) where seen.insert(w).inserted { out.append(w) }
        return out.sorted()
    }()
    private static let index: [String: Int] = Dictionary(uniqueKeysWithValues: vocab.enumerated().map { ($1, $0) })

    static let counts: [String: Int] = sentences.flatMap { $0 }.reduce(into: [:]) { $0[$1, default: 0] += 1 }

    static let pairs: [(Int, Int)] = sentences.flatMap { tokens in
        tokens.indices.flatMap { i in
            windowRange(i, window, tokens.count - 1).filter { $0 != i }.map { (index[tokens[i]]!, index[tokens[$0]]!) }
        }
    }

    /// Unigram counts raised to 3/4.
    static let noiseTable: [Int] = {
        let weights = vocab.map { pow(Double(counts[$0]!), 0.75) }
        let total = weights.reduce(0, +)
        var table: [Int] = []
        for (i, w) in weights.enumerated() {
            for _ in 0..<max(Int(w / total * 1000), 1) { table.append(i) }
        }
        return table
    }()

    static func noiseShare(_ word: String) -> Double {
        let i = index[word]!
        return Double(noiseTable.filter { $0 == i }.count) / Double(noiseTable.count)
    }

    static func unigramShare(_ word: String) -> Double { Double(counts[word]!) / Double(counts.values.reduce(0, +)) }

    final class Model {
        let input: [[Float]]
        let output: [[Float]]
        let losses: [Float]
        init(input: [[Float]], output: [[Float]], losses: [Float]) { self.input = input; self.output = output; self.losses = losses }
        func vector(_ word: String) -> [Float] { input[Word2VecLab.indexOf(word)] }
    }

    static func indexOf(_ word: String) -> Int { index[word]! }

    static func trainSkipGram(seed: Int = 7) -> Model { train(seed, cbow: false) }
    static func trainCbow(seed: Int = 7) -> Model { train(seed, cbow: true) }

    static let skipGram: Model = trainSkipGram()
    static let cbow: Model = trainCbow()

    private static func train(_ seed: Int, cbow: Bool) -> Model {
        var rng = EmbRng(seed)
        var input = (0..<vocab.count).map { _ in (0..<dim).map { _ in rng.uniform(1 / Float(dim)) } }
        var output = [[Float]](repeating: [Float](repeating: 0, count: dim), count: vocab.count)
        var losses: [Float] = []

        for epoch in 0..<epochs {
            var loss: Float = 0
            var steps = 0
            for tokens in sentences {
                for i in tokens.indices {
                    let centre = index[tokens[i]]!
                    let contextIds = windowRange(i, window, tokens.count - 1).filter { $0 != i }.map { index[tokens[$0]]! }
                    if contextIds.isEmpty { continue }
                    if cbow {
                        var hidden = [Float](repeating: 0, count: dim)
                        for c in contextIds { for d in 0..<dim { hidden[d] += input[c][d] } }
                        for d in 0..<dim { hidden[d] /= Float(contextIds.count) }
                        var grad = [Float](repeating: 0, count: dim)
                        loss += step(hidden, &grad, centre, &output, &rng)
                        steps += 1
                        for c in contextIds { for d in 0..<dim { input[c][d] += grad[d] / Float(contextIds.count) } }
                    } else {
                        for c in contextIds {
                            let hidden = input[centre]
                            var grad = [Float](repeating: 0, count: dim)
                            loss += step(hidden, &grad, c, &output, &rng)
                            steps += 1
                            for d in 0..<dim { input[centre][d] += grad[d] }
                        }
                    }
                }
            }
            if epoch % 10 == 0 || epoch == epochs - 1 { losses.append(loss / Float(steps)) }
        }
        return Model(input: input, output: output, losses: losses)
    }

    private static func step(_ hidden: [Float], _ gradAccum: inout [Float], _ target: Int, _ output: inout [[Float]], _ rng: inout EmbRng) -> Float {
        var loss: Float = 0
        for k in 0...negatives {
            let word: Int, label: Float
            if k == 0 { word = target; label = 1 } else { word = noiseTable[rng.int(noiseTable.count)]; label = 0 }
            if k > 0 && word == target { continue }
            var dot: Float = 0
            for d in hidden.indices { dot += hidden[d] * output[word][d] }
            let p = kSigmoid(dot)
            let g = (label - p) * learningRate
            for d in hidden.indices {
                gradAccum[d] += g * output[word][d]
                output[word][d] += g * hidden[d]
            }
            loss += -Float(log(Double(max(label == 1 ? p : 1 - p, 1e-7))))
        }
        return loss
    }

    static func nearest(_ model: Model, _ word: String, k: Int = 3) -> [(String, Float)] {
        let v = model.vector(word)
        return Array(vocab.filter { $0 != word }.map { ($0, cosineOf(v, model.vector($0))) }.sortedByDescending { $0.1 }.prefix(k))
    }

    static func similarity(_ model: Model, _ a: String, _ b: String) -> Float { cosineOf(model.vector(a), model.vector(b)) }

    static func softmaxCost() -> Int { vocab.count * dim }
    static func negativeSamplingCost() -> Int { (negatives + 1) * dim }
    static func skipGramUpdatesPerEpoch() -> Int { pairs.count }
    static func cbowUpdatesPerEpoch() -> Int { sentences.reduce(0) { $0 + $1.count } }

    static func contrast(_ model: Model) -> Float { similarity(model, "king", "queen") - similarity(model, "king", "dog") }
}

// MARK: - GloVe

enum GloveLab {
    static let corpus = Word2VecLab.corpus
    static let window = 5
    static let dim = 8
    static let xMax: Float = 10
    static let alpha: Float = 0.75
    static let epochs = 400
    static let learningRate: Float = 0.05

    static var vocab: [String] { Word2VecLab.vocab }
    private static func idx(_ word: String) -> Int { Word2VecLab.indexOf(word) }

    /// X_ij weighted by 1/distance.
    static let cooccurrence: [[Float]] = {
        var x = [[Float]](repeating: [Float](repeating: 0, count: Word2VecLab.vocab.count), count: Word2VecLab.vocab.count)
        for tokens in corpus.map({ $0.components(separatedBy: " ") }) {
            for i in tokens.indices {
                for j in windowRange(i, window, tokens.count - 1) where i != j {
                    x[idx(tokens[i])][idx(tokens[j])] += 1 / Float(abs(i - j))
                }
            }
        }
        return x
    }()

    static func cooccur(_ a: String, _ b: String) -> Float { cooccurrence[idx(a)][idx(b)] }

    static let probeWords = ["solid", "gas", "water", "fashion"]
    static let pIce: [String: Double] = ["solid": 1.9e-4, "gas": 6.6e-5, "water": 3.0e-3, "fashion": 1.7e-5]
    static let pSteam: [String: Double] = ["solid": 2.2e-5, "gas": 7.8e-4, "water": 2.2e-3, "fashion": 1.8e-5]

    static func ratio(_ word: String) -> Double { pIce[word]! / pSteam[word]! }

    static func weight(_ x: Float) -> Float { x >= xMax ? 1 : kPow(x / xMax, alpha) }

    final class Model {
        let vectors: [[Float]]
        let context: [[Float]]
        let bias: [Float]
        let losses: [Float]
        init(vectors: [[Float]], context: [[Float]], bias: [Float], losses: [Float]) {
            self.vectors = vectors; self.context = context; self.bias = bias; self.losses = losses
        }
        /// The paper sums the two vector sets.
        func vector(_ word: String) -> [Float] {
            let i = Word2VecLab.indexOf(word)
            return vectors[i].indices.map { vectors[i][$0] + context[i][$0] }
        }
    }

    static let model: Model = train()

    static func train(seed: Int = 11) -> Model {
        var rng = EmbRng(seed)
        let n = vocab.count
        var w = (0..<n).map { _ in (0..<dim).map { _ in rng.uniform(0.5) } }
        var c = (0..<n).map { _ in (0..<dim).map { _ in rng.uniform(0.5) } }
        var bw = (0..<n).map { _ in rng.uniform(0.1) }
        var bc = (0..<n).map { _ in rng.uniform(0.1) }
        var losses: [Float] = []
        var entries: [(Int, Int)] = []
        for i in 0..<n { for j in 0..<n where cooccurrence[i][j] > 0 { entries.append((i, j)) } }

        for epoch in 0..<epochs {
            var loss: Float = 0
            for (i, j) in entries {
                let x = cooccurrence[i][j]
                var dot = bw[i] + bc[j]
                for d in 0..<dim { dot += w[i][d] * c[j][d] }
                let diff = dot - Float(log(Double(x)))
                let f = weight(x)
                loss += f * diff * diff
                let g = f * diff * learningRate
                for d in 0..<dim {
                    let wi = w[i][d]
                    w[i][d] -= g * c[j][d]
                    c[j][d] -= g * wi
                }
                bw[i] -= g
                bc[j] -= g
            }
            if epoch % 20 == 0 || epoch == epochs - 1 { losses.append(loss / Float(entries.count)) }
        }
        return Model(vectors: w, context: c, bias: bw, losses: losses)
    }

    static func nonZeroEntries() -> Int { cooccurrence.reduce(0) { $0 + $1.filter { $0 > 0 }.count } }
    static func totalCells() -> Int { vocab.count * vocab.count }
    static func sparsity() -> Double { 1.0 - Double(nonZeroEntries()) / Double(totalCells()) }

    static func similarity(_ model: Model, _ a: String, _ b: String) -> Float { cosineOf(model.vector(a), model.vector(b)) }

    static func nearest(_ model: Model, _ word: String, k: Int = 3) -> [(String, Float)] {
        Array(vocab.filter { $0 != word }.map { ($0, cosineOf(model.vector(word), model.vector($0))) }.sortedByDescending { $0.1 }.prefix(k))
    }

    /// a − b + c scored against every stored vector.
    static func analogy(_ model: Model, _ a: String, _ b: String, _ c: String, k: Int = 3) -> [(String, Float)] {
        let va = model.vector(a), vb = model.vector(b), vc = model.vector(c)
        let target = (0..<dim).map { va[$0] - vb[$0] + vc[$0] }
        return Array(vocab.filter { $0 != a && $0 != b && $0 != c }.map { ($0, cosineOf(target, model.vector($0))) }.sortedByDescending { $0.1 }.prefix(k))
    }

    /// PCA onto two components, in vocab order, scaled into [0.06, 0.94].
    static func project2D(_ model: Model) -> [(Float, Float)] { powerProject(vocab.map { model.vector($0) }, pad: 0.06) }

    static func coordinateOf(_ projection: [(Float, Float)], _ word: String) -> (Float, Float) { projection[vocab.firstIndex(of: word)!] }
}

// MARK: - FastText

enum FastTextLab {
    static let minN = 3
    static let maxN = 6

    /// Character n-grams of `<word>`, plus the whole word.
    static func subwords(_ word: String) -> [String] {
        let padded = Array("<\(word)>")
        var grams: [String] = []
        for n in minN...maxN where padded.count >= n {
            for i in 0...(padded.count - n) { grams.append(String(padded[i..<(i + n)])) }
        }
        return grams + [word]
    }

    final class Model {
        let subwordVectors: [String: [Float]]
        let dim: Int
        init(subwordVectors: [String: [Float]], dim: Int) { self.subwordVectors = subwordVectors; self.dim = dim }

        func vector(_ word: String) -> [Float] {
            var out = [Float](repeating: 0, count: dim)
            var hits = 0
            for g in FastTextLab.subwords(word) {
                guard let v = subwordVectors[g] else { continue }
                for d in 0..<dim { out[d] += v[d] }
                hits += 1
            }
            if hits > 0 { for d in 0..<dim { out[d] /= Float(hits) } }
            return out
        }

        func coverage(_ word: String) -> (Int, Int) {
            let grams = FastTextLab.subwords(word)
            return (grams.filter { subwordVectors[$0] != nil }.count, grams.count)
        }
    }

    static let model: Model = build()

    static func build(_ base: Word2VecLab.Model = Word2VecLab.skipGram) -> Model {
        var sums: [String: [Float]] = [:]
        var hits: [String: Int] = [:]
        for word in Word2VecLab.vocab {
            let v = base.vector(word)
            for g in subwords(word) {
                var acc = sums[g] ?? [Float](repeating: 0, count: Word2VecLab.dim)
                for d in acc.indices { acc[d] += v[d] }
                sums[g] = acc
                hits[g, default: 0] += 1
            }
        }
        for (g, acc) in sums { sums[g] = acc.map { $0 / Float(hits[g]!) } }
        return Model(subwordVectors: sums, dim: Word2VecLab.dim)
    }

    static let unseen = ["kingdoms", "kings", "queenly", "monarchy"]

    static func isOov(_ word: String) -> Bool { !Word2VecLab.vocab.contains(word) }

    static func similarityToKnown(_ model: Model, _ unknown: String, _ known: String) -> Float { cosineOf(model.vector(unknown), model.vector(known)) }

    static func subwordVocabularySize() -> Int { Set(Word2VecLab.vocab.flatMap { subwords($0) }).count }
}

// MARK: - ELMo

enum ElmoLab {
    static let sentences = [
        "we sat on the muddy river bank and watched the water",
        "the muddy water flowed past the river bank",
        "she deposited the cheque and counted the cash at the bank",
        "the bank counted the cash and cleared the cheque",
    ]

    static let target = "bank"
    static let senseOf = ["river", "river", "money", "money"]

    private static let staticVectors: [String: [Float]] = {
        var rng = EmbRng(23)
        var seen = Set<String>(), out: [String: [Float]] = [:]
        for w in sentences.flatMap({ $0.components(separatedBy: " ") }) where seen.insert(w).inserted {
            out[w] = (0..<12).map { _ in rng.uniform(1) }
        }
        return out
    }()

    static func staticVector(_ word: String) -> [Float] { staticVectors[word]! }

    static let contextWeight: Float = 0.6

    static func contextualVector(_ sentenceIndex: Int, _ word: String = target) -> [Float] {
        let tokens = sentences[sentenceIndex].components(separatedBy: " ")
        let others = tokens.filter { $0 != word }
        var mean = [Float](repeating: 0, count: 12)
        for t in others {
            let v = staticVector(t)
            for d in mean.indices { mean[d] += v[d] / Float(others.count) }
        }
        let base = staticVector(word)
        return (0..<12).map { (1 - contextWeight) * base[$0] + contextWeight * mean[$0] }
    }

    static func occurrenceVectors() -> [[Float]] { sentences.indices.map { contextualVector($0) } }

    static func similarity(_ a: Int, _ b: Int) -> Float { cosineOf(contextualVector(a), contextualVector(b)) }

    static func withinSense() -> Float { (similarity(0, 1) + similarity(2, 3)) / 2 }

    static func acrossSense() -> Float { (similarity(0, 2) + similarity(0, 3) + similarity(1, 2) + similarity(1, 3)) / 4 }

    static func staticSimilarity() -> Float { cosineOf(staticVector(target), staticVector(target)) }

    static func senseGap() -> Float { withinSense() - acrossSense() }

    /// The four occurrences plus the static vector, scaled into [0.08, 0.92].
    static func project2D() -> [(Float, Float)] { powerProject(occurrenceVectors() + [staticVector(target)], pad: 0.08) }

    static func lookupParameters(vocabulary: Int = 1_000_000, dim: Int = 300) -> Int64 { Int64(vocabulary) * Int64(dim) }
    static func elmoParameters() -> Int64 { 93_600_000 }
}
