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
