import Foundation

// Port of StatisticalNlpMath.kt (D1 labs). Each lab object is ported as the Swift labs need it.

// MARK: - Probabilistic context-free grammar

enum PcfgLab {
    static let sentence = ["she", "saw", "the", "man", "with", "the", "telescope"]

    /// Binary rules A → B C in Chomsky Normal Form.
    static let binary: [(String, String, String, Double)] = [
        ("S", "NP", "VP", 1.0), ("VP", "V", "NP", 0.70), ("VP", "VP", "PP", 0.30),
        ("NP", "Det", "N", 0.40), ("NP", "NP", "PP", 0.20), ("PP", "P", "NP", 1.00),
    ]

    /// Lexical rules A → word.
    static let lexical: [(String, String, Double)] = [
        ("NP", "she", 0.40), ("V", "saw", 1.00), ("Det", "the", 1.00),
        ("N", "man", 0.50), ("N", "telescope", 0.50), ("P", "with", 1.00),
    ]

    final class Parse {
        let label: String
        let probability: Double
        let left: Parse?
        let right: Parse?
        let word: String?
        init(_ label: String, _ probability: Double, _ left: Parse? = nil, _ right: Parse? = nil, word: String? = nil) {
            self.label = label; self.probability = probability; self.left = left; self.right = right; self.word = word
        }
    }

    /// Probabilistic CYK: chart[i][j] holds the best parse of words[i..<j] per label.
    static func cyk(_ words: [String] = sentence) -> [[[String: Parse]]] {
        let n = words.count
        var chart = [[[String: Parse]]](repeating: [[String: Parse]](repeating: [:], count: n + 1), count: n)
        for i in 0..<n {
            for (label, word, p) in lexical where word == words[i] { chart[i][i + 1][label] = Parse(label, p, word: word) }
        }
        if n >= 2 {
            for span in 2...n {
                for i in 0...(n - span) {
                    let j = i + span
                    for split in (i + 1)..<j {
                        for (label, lc, rc, p) in binary {
                            guard let left = chart[i][split][lc], let right = chart[split][j][rc] else { continue }
                            let score = p * left.probability * right.probability
                            if let existing = chart[i][j][label], score <= existing.probability { continue }
                            chart[i][j][label] = Parse(label, score, left, right)
                        }
                    }
                }
            }
        }
        return chart
    }

    /// Cells the table actually fills.
    static func filledCells(_ words: [String] = sentence) -> Int { cyk(words).reduce(0) { $0 + $1.filter { !$0.isEmpty }.count } }

    static func splitsConsidered(_ n: Int = sentence.count) -> Int { (2...n).reduce(0) { acc, span in acc + (n - span + 1) * (span - 1) } }

    private static func det(_ noun: String) -> Parse { Parse("NP", 0.40 * 1.00 * 0.50, Parse("Det", 1.0, word: "the"), Parse("N", 0.50, word: noun)) }

    /// she saw [the man] [with the telescope].
    static func vpAttachment() -> Parse {
        let np = Parse("NP", 0.40, word: "she")
        let theMan = det("man"), theTelescope = det("telescope")
        let pp = Parse("PP", 1.00 * 1.00 * theTelescope.probability, Parse("P", 1.0, word: "with"), theTelescope)
        let vpCore = Parse("VP", 0.70 * 1.00 * theMan.probability, Parse("V", 1.0, word: "saw"), theMan)
        let vp = Parse("VP", 0.30 * vpCore.probability * pp.probability, vpCore, pp)
        return Parse("S", 1.0 * np.probability * vp.probability, np, vp)
    }

    /// she saw [the man with the telescope].
    static func npAttachment() -> Parse {
        let subject = Parse("NP", 0.40, word: "she")
        let theMan = det("man"), theTelescope = det("telescope")
        let pp = Parse("PP", 1.00 * 1.00 * theTelescope.probability, Parse("P", 1.0, word: "with"), theTelescope)
        let bigNp = Parse("NP", 0.20 * theMan.probability * pp.probability, theMan, pp)
        let vp = Parse("VP", 0.70 * 1.00 * bigNp.probability, Parse("V", 1.0, word: "saw"), bigNp)
        return Parse("S", 1.0 * subject.probability * vp.probability, subject, vp)
    }

    static var attachmentRatio: Double { vpAttachment().probability / npAttachment().probability }
}

// MARK: - Hidden Markov model

enum HmmLab {
    static let tags = ["NN", "VB", "DT", "IN"]
    static let sentence = ["book", "that", "flight"]

    static let start: [String: Double] = ["NN": 0.35, "VB": 0.25, "DT": 0.30, "IN": 0.10]

    static let transition: [String: [String: Double]] = [
        "NN": ["NN": 0.15, "VB": 0.30, "DT": 0.05, "IN": 0.30, "</s>": 0.20],
        "VB": ["NN": 0.20, "VB": 0.05, "DT": 0.50, "IN": 0.10, "</s>": 0.15],
        "DT": ["NN": 0.90, "VB": 0.02, "DT": 0.02, "IN": 0.02, "</s>": 0.04],
        "IN": ["NN": 0.60, "VB": 0.05, "DT": 0.30, "IN": 0.03, "</s>": 0.02],
    ]

    static let emission: [String: [String: Double]] = [
        "NN": ["book": 0.030, "that": 0.000, "flight": 0.020],
        "VB": ["book": 0.040, "that": 0.000, "flight": 0.000],
        "DT": ["book": 0.000, "that": 0.150, "flight": 0.000],
        "IN": ["book": 0.000, "that": 0.250, "flight": 0.000],
    ]

    static func a(_ from: String, _ to: String) -> Double { transition[from]?[to] ?? 0 }
    static func b(_ tag: String, _ word: String) -> Double { emission[tag]?[word] ?? 0 }

    /// The forward trellis: one column per word, indexed by tag.
    static func forward(_ words: [String] = sentence) -> [[String: Double]] {
        var column = Dictionary(uniqueKeysWithValues: tags.map { ($0, (start[$0] ?? 0) * b($0, words[0])) })
        var trellis = [column]
        for i in 1..<max(words.count, 1) {
            let prev = column
            column = Dictionary(uniqueKeysWithValues: tags.map { to in
                (to, tags.reduce(0.0) { $0 + prev[$1]! * a($1, to) } * b(to, words[i]))
            })
            trellis.append(column)
        }
        return trellis
    }

    static func sentenceProbability(_ words: [String] = sentence) -> Double {
        let last = forward(words).last!
        return tags.reduce(0.0) { $0 + last[$1]! * a($1, "</s>") }
    }

    struct Path { let tags: [String]; let probability: Double }

    static func allPaths(_ words: [String] = sentence) -> [Path] {
        var out: [Path] = []
        func walk(_ prefix: [String], _ score: Double) {
            if prefix.count == words.count {
                out.append(Path(tags: prefix, probability: score * a(prefix.last!, "</s>")))
                return
            }
            let i = prefix.count
            for tag in tags {
                let step = i == 0 ? (start[tag] ?? 0) : a(prefix.last!, tag)
                walk(prefix + [tag], score * step * b(tag, words[i]))
            }
        }
        walk([], 1.0)
        return out.filter { $0.probability > 0 }.sortedByDescending(\.probability)
    }

    private static func firstMax(_ keys: [String], _ value: (String) -> Double) -> String {
        var best = keys[0]
        for k in keys where value(k) > value(best) { best = k }
        return best
    }

    static func viterbi(_ words: [String] = sentence) -> Path {
        var column = Dictionary(uniqueKeysWithValues: tags.map { ($0, (start[$0] ?? 0) * b($0, words[0])) })
        var backpointers: [[String: String]] = []
        for i in 1..<max(words.count, 1) {
            var back: [String: String] = [:]
            var next: [String: Double] = [:]
            let prev = column
            for to in tags {
                let best = firstMax(tags) { prev[$0]! * a($0, to) }
                back[to] = best
                next[to] = prev[best]! * a(best, to) * b(to, words[i])
            }
            backpointers.append(back)
            column = next
        }
        let finals = Dictionary(uniqueKeysWithValues: tags.map { ($0, column[$0]! * a($0, "</s>")) })
        var tag = firstMax(tags) { finals[$0]! }
        var path = [tag]
        for i in backpointers.indices.reversed() {
            tag = backpointers[i][tag]!
            path.insert(tag, at: 0)
        }
        return Path(tags: path, probability: finals.values.max()!)
    }

    /// Left-to-right greedy tagging: never revisits a choice.
    static func greedy(_ words: [String] = sentence) -> Path {
        var path: [String] = []
        for i in words.indices {
            let best = firstMax(tags) { tag in
                let step = i == 0 ? (start[tag] ?? 0) : a(path.last!, tag)
                return step * b(tag, words[i])
            }
            path.append(best)
        }
        return Path(tags: path, probability: scoreOf(path, words))
    }

    static func scoreOf(_ path: [String], _ words: [String] = sentence) -> Double {
        var p = start[path[0]] ?? 0
        p *= b(path[0], words[0])
        for i in 1..<max(path.count, 1) { p *= a(path[i - 1], path[i]) * b(path[i], words[i]) }
        return p * a(path.last!, "</s>")
    }

    static func viterbiOperations(_ length: Int = sentence.count) -> Int { length * tags.count * tags.count }
    static func bruteForcePaths(_ length: Int = sentence.count) -> Int {
        var n = 1
        for _ in 0..<length { n *= tags.count }
        return n
    }
}

// MARK: - Cosine and Jaccard

enum SimilarityLab {
    static let terms = ["data", "model"]

    static let names = ["short", "long", "theory", "query"]
    static let vectors: [String: [Double]] = ["short": [3.0, 1.0], "long": [6.0, 2.0], "theory": [1.0, 3.0], "query": [2.0, 1.0]]

    static func dot(_ a: [Double], _ b: [Double]) -> Double { a.indices.reduce(0.0) { $0 + a[$1] * b[$1] } }
    static func norm(_ a: [Double]) -> Double { dot(a, a).squareRoot() }
    static func cosine(_ a: [Double], _ b: [Double]) -> Double { dot(a, b) / (norm(a) * norm(b)) }
    static func euclidean(_ a: [Double], _ b: [Double]) -> Double { a.indices.reduce(0.0) { $0 + (a[$1] - b[$1]) * (a[$1] - b[$1]) }.squareRoot() }

    static func cosine(_ a: String, _ b: String) -> Double { cosine(vectors[a]!, vectors[b]!) }
    static func euclidean(_ a: String, _ b: String) -> Double { euclidean(vectors[a]!, vectors[b]!) }

    static func rankByCosine(_ query: String = "query") -> [String] { names.filter { $0 != query }.sortedByDescending { cosine(query, $0) } }
    static func rankByEuclidean(_ query: String = "query") -> [String] { names.filter { $0 != query }.sortedBy { euclidean(query, $0) } }

    static var lengthInvariant: Bool { abs(cosine("short", "long") - 1.0) < 1e-9 }

    static let docA = "the model learns from data and more data"
    static let docB = "the model learns from data"
    static let docC = "a neural model learns representations from raw data"

    static func setOf(_ doc: String) -> Set<String> { Set(doc.components(separatedBy: " ")) }

    static func jaccard(_ a: String, _ b: String) -> Double {
        let x = setOf(a), y = setOf(b)
        return Double(x.intersection(y).count) / Double(x.union(y).count)
    }

    /// Word counts in first-seen order.
    static func counts(_ doc: String) -> [(String, Int)] {
        var order: [String] = []
        var c: [String: Int] = [:]
        for w in doc.components(separatedBy: " ") {
            if c[w] == nil { order.append(w) }
            c[w, default: 0] += 1
        }
        return order.map { ($0, c[$0]!) }
    }

    static func cosineOfDocs(_ a: String, _ b: String) -> Double {
        let ca = counts(a), cb = counts(b)
        let ma = Dictionary(uniqueKeysWithValues: ca), mb = Dictionary(uniqueKeysWithValues: cb)
        var seen = Set<String>()
        let vocab = (ca.map(\.0) + cb.map(\.0)).filter { seen.insert($0).inserted }
        return cosine(vocab.map { Double(ma[$0] ?? 0) }, vocab.map { Double(mb[$0] ?? 0) })
    }

    /// Character k-shingles.
    static func shingles(_ doc: String, _ k: Int = 5) -> Set<String> {
        let chars = Array(doc)
        guard chars.count >= k else { return [] }
        return Set((0...(chars.count - k)).map { String(chars[$0..<($0 + k)]) })
    }

    static func jaccardShingles(_ a: String, _ b: String, _ k: Int = 5) -> Double {
        let x = shingles(a, k), y = shingles(b, k)
        return Double(x.intersection(y).count) / Double(x.union(y).count)
    }

    static func signature(_ set: Set<String>, _ permutations: Int) -> [Int32] {
        (0..<permutations).map { seed in set.map { hash($0, seed) }.min()! }
    }

    /// FNV-style hash with Kotlin's wrapping Int arithmetic.
    private static func hash(_ value: String, _ seed: Int) -> Int32 {
        var h = Int32(truncatingIfNeeded: Int64(2166136261)) ^ (Int32(truncatingIfNeeded: seed) &* Int32(truncatingIfNeeded: Int64(0x9E3779B1)))
        for unit in value.utf16 {
            h ^= Int32(unit)
            h = h &* 16777619
        }
        return h & 0x7fffffff
    }

    static func minHashEstimate(_ a: String, _ b: String, _ permutations: Int, _ k: Int = 5) -> Double {
        let sa = signature(shingles(a, k), permutations), sb = signature(shingles(b, k), permutations)
        return Double(sa.indices.filter { sa[$0] == sb[$0] }.count) / Double(permutations)
    }

    static func exactComparisonSize(_ a: String, _ b: String, _ k: Int = 5) -> Int { shingles(a, k).count + shingles(b, k).count }
}
