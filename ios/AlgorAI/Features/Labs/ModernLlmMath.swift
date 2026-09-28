import Foundation

// Port of ModernLlmMath.kt (D5 labs). None of these labs run a language model; they run the problem
// the technique solves. Each lab object is ported as the Swift labs need it.

// MARK: - Tree of Thoughts: a real search over Game of 24

enum TotLab {
    static let puzzle: [Double] = [4, 9, 10, 13]
    static let target = 24.0
    private static let eps = 1e-6

    struct Step { let expression: String; let result: Double; let state: [Double] }

    static func successors(_ state: [Double]) -> [Step] {
        var out: [Step] = []
        for i in state.indices {
            for j in state.indices where i < j {
                let a = state[i], b = state[j]
                let rest = state.indices.filter { $0 != i && $0 != j }.map { state[$0] }
                var combos: [(String, Double)] = [
                    ("\(fmt(a)) + \(fmt(b))", a + b),
                    ("\(fmt(a)) × \(fmt(b))", a * b),
                    ("\(fmt(a)) − \(fmt(b))", a - b),
                    ("\(fmt(b)) − \(fmt(a))", b - a),
                ]
                if abs(b) > eps { combos.append(("\(fmt(a)) ÷ \(fmt(b))", a / b)) }
                if abs(a) > eps { combos.append(("\(fmt(b)) ÷ \(fmt(a))", b / a)) }
                for (expr, value) in combos { out.append(Step(expression: expr, result: value, state: (rest + [value]).sorted(by: >))) }
            }
        }
        return out
    }

    static func solved(_ state: [Double]) -> Bool { state.count == 1 && abs(state[0] - target) < eps }

    static func fmt(_ x: Double) -> String {
        let r = (x + 0.5).rounded(.down) // Java Math.round: half up
        return abs(x - r) < eps ? "\(Int(r))" : String(format: "%.2f", x)
    }

    static func label(_ state: [Double]) -> String { state.map(fmt).joined(separator: " ") }

    struct Ranked { let state: [Double]; let score: Double; let solvable: Bool }

    /// The frontier an evaluator ranks, with its score and whether it can actually reach 24.
    static func rankedFrontier(depth: Int = 1, _ state: [Double] = puzzle) -> [Ranked] {
        var seen = Set<String>()
        return successors(state).map(\.state).filter { seen.insert(label($0)).inserted }
            .map { Ranked(state: $0, score: heuristic($0, depth: depth), solvable: reachable($0)) }
            .sortedBy(\.score)
    }

    struct SearchResult { let solved: Bool; let expanded: Int; let evaluatorCalls: Int; let trace: [String] }

    /// Every reachable state, and the number of distinct solution paths.
    static func exhaustive(_ state: [Double] = puzzle) -> (Int, Int) {
        var expanded = 0, solutions = 0
        func walk(_ current: [Double]) {
            expanded += 1
            if current.count == 1 {
                if solved(current) { solutions += 1 }
                return
            }
            for s in successors(current) { walk(s.state) }
        }
        walk(state)
        return (expanded, solutions)
    }

    /// The stand-in for the paper's value prompt; lower is better.
    static func heuristic(_ state: [Double], depth: Int = 1) -> Double {
        if state.count == 1 { return abs(state[0] - target) }
        let next = successors(state)
        if depth <= 1 { return next.map { abs($0.result - target) }.min()! }
        return next.map { heuristic($0.state, depth: depth - 1) }.min()!
    }

    /// Successor generations spent by one evaluator call.
    static func evaluatorCost(_ state: [Double], depth: Int = 1) -> Int {
        if state.count == 1 { return 0 }
        if depth <= 1 { return 1 }
        return 1 + successors(state).reduce(0) { $0 + evaluatorCost($1.state, depth: depth - 1) }
    }

    static func reachable(_ state: [Double]) -> Bool {
        if state.count == 1 { return solved(state) }
        return successors(state).contains { reachable($0.state) }
    }

    /// Beam search at `width`, ranking by the evaluator. Width 1 is the greedy chain.
    static func beam(_ width: Int, depth: Int = 1, _ state: [Double] = puzzle) -> SearchResult {
        var expanded = 0, calls = 0
        var frontier: [([Double], [String])] = [(state, [])]
        for _ in 0..<(state.count - 1) {
            var scored: [([Double], [String], Double)] = []
            for (current, trace) in frontier {
                expanded += 1
                for step in successors(current) {
                    calls += evaluatorCost(step.state, depth: depth)
                    scored.append((step.state, trace + ["\(step.expression) = \(fmt(step.result))"], heuristic(step.state, depth: depth)))
                }
            }
            frontier = scored.sortedBy { $0.2 }.prefix(width).map { ($0.0, $0.1) }
        }
        let win = frontier.first { solved($0.0) }
        return SearchResult(solved: win != nil, expanded: expanded, evaluatorCalls: calls, trace: win?.1 ?? frontier[0].1)
    }

    static func widthNeeded(_ depth: Int, maxWidth: Int = 40) -> Int { (1...maxWidth).first { beam($0, depth: depth).solved } ?? -1 }

    /// The rank given to the best solvable state, how many of the top `width` are solvable, and the frontier size.
    static func evaluatorQuality(_ width: Int, depth: Int = 1) -> (Int, Int, Int) {
        var seen = Set<String>()
        let ranked = successors(puzzle).map { ($0.state, heuristic($0.state, depth: depth)) }
            .filter { seen.insert($0.0.map { String(format: "%.4f", $0) }.joined(separator: ",")).inserted }
            .sortedBy { $0.1 }
        let firstSolvableRank = (ranked.firstIndex { reachable($0.0) } ?? -1) + 1
        let solvableInTop = ranked.prefix(width).filter { reachable($0.0) }.count
        return (firstSolvableRank, solvableInTop, ranked.count)
    }
}

// MARK: - Prompt engineering

/// A prompt as an induction problem: demonstrations pick one rule out of the rules consistent with them.
enum PromptLab {
    struct Rule { let name: String; let apply: (String) -> Character }

    static let rules: [Rule] = [
        Rule(name: "first letter") { $0.first! },
        Rule(name: "last letter") { $0.last! },
        Rule(name: "middle letter") { w in Array(w)[w.count / 2] },
        Rule(name: "most frequent letter") { word in
            var order: [Character] = []
            var counts: [Character: Int] = [:]
            for c in word {
                if counts[c] == nil { order.append(c) }
                counts[c, default: 0] += 1
            }
            return order.sorted { a, b in counts[a]! != counts[b]! ? counts[a]! > counts[b]! : a < b }[0]
        },
        Rule(name: "alphabetically first letter") { $0.min()! },
    ]

    static let trueRule = rules.first { $0.name == "middle letter" }!

    static let pool = ["banana", "adage", "otter", "level", "melon", "sonar"]
    static let query = "kayak"

    static func label(_ word: String) -> Character { trueRule.apply(word) }

    static func consistent(_ demos: [String]) -> [Rule] { rules.filter { rule in demos.allSatisfy { rule.apply($0) == label($0) } } }

    /// Uniform posterior over the surviving rules, keyed by the answer each gives, in first-seen order.
    static func prediction(_ demos: [String]) -> [(Character, Double)] {
        let survivors = consistent(demos)
        var order: [Character] = []
        var counts: [Character: Int] = [:]
        for rule in survivors {
            let c = rule.apply(query)
            if counts[c] == nil { order.append(c) }
            counts[c, default: 0] += 1
        }
        return order.map { ($0, Double(counts[$0]!) / Double(survivors.count)) }
    }

    static func predictedCorrectly(_ demos: [String]) -> Bool {
        let posterior = prediction(demos)
        guard var best = posterior.first else { return false }
        for p in posterior where p.1 > best.1 { best = p }
        let tied = posterior.filter { $0.1 == best.1 }
        return tied.count == 1 && best.0 == label(query)
    }

    static func allPairs() -> [(String, String)] {
        pool.indices.flatMap { i in ((i + 1)..<pool.count).map { (pool[i], pool[$0]) } }
    }

    static func identifyingPairs() -> [(String, String)] { allPairs().filter { consistent([$0.0, $0.1]).count == 1 } }

    static func eliminationCurve(_ order: [String] = pool) -> [(Int, Int)] {
        (0...order.count).map { k in (k, consistent(Array(order.prefix(k))).count) }
    }

    static func instructionEliminates() -> [Rule] { rules.filter { $0.name.contains("first") || $0.name.contains("last") } }

    static func demosToEliminate(_ target: [Rule], _ order: [String] = pool) -> Int {
        let names = Set(target.map(\.name))
        for k in 0...order.count {
            let survivors = Set(consistent(Array(order.prefix(k))).map(\.name))
            if names.allSatisfy({ !survivors.contains($0) }) { return k }
        }
        return order.count
    }
}

// MARK: - Chain of thought and self-consistency

enum CotLab {
    static let directAccuracy = 0.55
    static let stepAccuracy = 0.92

    static func chainAccuracy(_ steps: Int, _ p: Double = stepAccuracy) -> Double { pow(p, Double(steps)) }

    static func breakEvenSteps(_ p: Double = stepAccuracy, _ q: Double = directAccuracy) -> Int {
        var n = 1
        while chainAccuracy(n + 1, p) > q { n += 1 }
        return n
    }

    private static func logFactorial(_ n: Int) -> Double {
        var s = 0.0
        if n >= 1 { for i in 1...n { s += log(Double(i)) } }
        return s
    }

    /// Enumerates every count vector (correct, wrong_1 … wrong_d) summing to `samples`.
    private static func enumerate(_ samples: Int, _ distinctWrong: Int, _ visit: ([Int]) -> Void) {
        var counts = [Int](repeating: 0, count: distinctWrong + 1)
        func walk(_ index: Int, _ remaining: Int) {
            if index == distinctWrong {
                counts[index] = remaining
                visit(counts)
                return
            }
            for c in 0...remaining {
                counts[index] = c
                walk(index + 1, remaining - c)
            }
        }
        walk(0, samples)
    }

    /// Exact probability the plurality of `samples` answers is correct; ties fail.
    static func pluralityAccuracy(_ p: Double, _ samples: Int, _ distinctWrong: Int) -> Double {
        let wrongEach = (1.0 - p) / Double(distinctWrong)
        var total = 0.0
        enumerate(samples, distinctWrong) { counts in
            let correct = counts[0]
            let topWrong = counts[1...].max()!
            if correct > topWrong {
                var logProb = logFactorial(samples)
                for c in counts { logProb -= logFactorial(c) }
                logProb += Double(counts[0]) * log(p)
                for i in 1...distinctWrong { logProb += Double(counts[i]) * log(wrongEach) }
                total += exp(logProb)
            }
        }
        return total
    }

    static func votingCurve(_ p: Double, _ distinctWrong: Int, maxSamples: Int = 9) -> [(Int, Double)] {
        stride(from: 1, through: maxSamples, by: 2).map { ($0, pluralityAccuracy(p, $0, distinctWrong)) }
    }

    /// Expected share of samples on the modal answer: what self-consistency reports as confidence.
    static func modalShare(_ p: Double, _ samples: Int, _ distinctWrong: Int) -> Double {
        let wrongEach = (1.0 - p) / Double(distinctWrong)
        var expected = 0.0
        enumerate(samples, distinctWrong) { counts in
            var logProb = logFactorial(samples)
            for c in counts { logProb -= logFactorial(c) }
            if counts[0] > 0 { logProb += Double(counts[0]) * log(p) }
            for i in 1...distinctWrong where counts[i] > 0 { logProb += Double(counts[i]) * log(wrongEach) }
            expected += exp(logProb) * Double(counts.max()!) / Double(samples)
        }
        return expected
    }
}

// MARK: - Vector databases

private struct LcgRandom64 {
    var state: Int64
    init(_ seed: Int64) { state = seed }

    mutating func nextFloat() -> Double {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        let v = Double(UInt64(bitPattern: state) >> 11) / Double(Int64(1) << 53)
        return v < 0 ? v + 1 : v
    }

    mutating func nextGaussian() -> Double {
        let u1 = max(nextFloat(), 1e-9)
        let u2 = nextFloat()
        return (-2.0 * log(u1)).squareRoot() * cos(2.0 * .pi * u2)
    }
}

enum VectorDbLab {
    static let corpusSize = 180
    static let clusters = 6
    static let k = 5

    struct Doc: Equatable { let id: Int; let x: Double; let y: Double; let cluster: Int }

    static let corpus: [Doc] = {
        var rng = LcgRandom64(20260729)
        let centres = (0..<clusters).map { _ -> (Double, Double) in
            let a = 0.18 + 0.64 * rng.nextFloat()
            let b = 0.18 + 0.64 * rng.nextFloat()
            return (a, b)
        }
        return (0..<corpusSize).map { i in
            let c = i % clusters
            let (cx, cy) = centres[c]
            let x = min(max(cx + 0.055 * rng.nextGaussian(), 0.02), 0.98)
            let y = min(max(cy + 0.055 * rng.nextGaussian(), 0.02), 0.98)
            return Doc(id: i, x: x, y: y, cluster: c)
        }
    }()

    private static func sq(_ v: Double) -> Double { v * v }

    /// On a cell boundary: halfway between the two closest coarse centroids.
    static let query: (Double, Double) = {
        let c = ivf.centroids
        var best = (0, 1)
        var bestD = Double.infinity
        for i in c.indices {
            for j in (i + 1)..<max(c.count, i + 1) {
                let d = sq(c[i].0 - c[j].0) + sq(c[i].1 - c[j].1)
                if d < bestD { bestD = d; best = (i, j) }
            }
        }
        return ((c[best.0].0 + c[best.1].0) / 2, (c[best.0].1 + c[best.1].1) / 2)
    }()

    static func distance(_ doc: Doc, _ q: (Double, Double) = query) -> Double { (sq(doc.x - q.0) + sq(doc.y - q.1)).squareRoot() }

    static func exactNeighbours(_ topK: Int = k, _ q: (Double, Double) = query) -> [Doc] {
        Array(corpus.sortedBy { distance($0, q) }.prefix(topK))
    }

    struct Ivf { let centroids: [(Double, Double)]; let assignment: [Int] }

    static func buildIvf(cells: Int = 12) -> Ivf {
        var rng = LcgRandom64(4242)
        var centroids = (0..<cells).map { _ -> (Double, Double) in
            let doc = corpus[min(max(Int(rng.nextFloat() * Double(corpus.count)), 0), corpus.count - 1)]
            return (doc.x, doc.y)
        }
        var assignment = [Int](repeating: 0, count: corpus.count)
        for _ in 0..<25 {
            for (i, doc) in corpus.enumerated() {
                var best = 0
                var bestD = Double.infinity
                for c in centroids.indices {
                    let d = sq(doc.x - centroids[c].0) + sq(doc.y - centroids[c].1)
                    if d < bestD { bestD = d; best = c }
                }
                assignment[i] = best
            }
            centroids = centroids.indices.map { c in
                let members = corpus.indices.filter { assignment[$0] == c }.map { corpus[$0] }
                if members.isEmpty { return centroids[c] }
                return (members.reduce(0.0) { $0 + $1.x } / Double(members.count), members.reduce(0.0) { $0 + $1.y } / Double(members.count))
            }
        }
        return Ivf(centroids: centroids, assignment: assignment)
    }

    static let ivf: Ivf = buildIvf()

    static func ivfSearch(_ nprobe: Int, _ topK: Int = k, _ q: (Double, Double) = query) -> ([Doc], Int) {
        let order = Set(ivf.centroids.indices.sortedBy { sq(q.0 - ivf.centroids[$0].0) + sq(q.1 - ivf.centroids[$0].1) }.prefix(nprobe))
        let scanned = corpus.indices.filter { order.contains(ivf.assignment[$0]) }.map { corpus[$0] }
        let comparisons = ivf.centroids.count + scanned.count
        return (Array(scanned.sortedBy { distance($0, q) }.prefix(topK)), comparisons)
    }

    static func recall(_ found: [Doc], _ topK: Int = k, _ q: (Double, Double) = query) -> Double {
        let truth = Set(exactNeighbours(topK, q).map(\.id))
        return Double(found.filter { truth.contains($0.id) }.count) / Double(topK)
    }

    static let knnGraph: [[Int]] = buildKnnGraph(6)

    private static func buildKnnGraph(_ degree: Int) -> [[Int]] {
        corpus.map { doc in
            Array(corpus.filter { $0.id != doc.id }.sortedBy { (sq($0.x - doc.x) + sq($0.y - doc.y)).squareRoot() }.prefix(degree).map(\.id))
        }
    }

    static let smallWorldGraph: [[Int]] = {
        var rng = LcgRandom64(777)
        return knnGraph.enumerated().map { index, near in
            let extra = (0..<2).map { _ in min(max(Int(rng.nextFloat() * Double(corpus.count)), 0), corpus.count - 1) }
            var seen = Set<Int>()
            return (near + extra).filter { $0 != index }.filter { seen.insert($0).inserted }
        }
    }()

    private static func adjacency(_ graph: [[Int]]) -> [Set<Int>] {
        var adj = [Set<Int>](repeating: [], count: graph.count)
        for (from, links) in graph.enumerated() { for to in links { adj[from].insert(to); adj[to].insert(from) } }
        return adj
    }

    static func components(_ graph: [[Int]]) -> [Int] {
        let adj = adjacency(graph)
        var label = [Int](repeating: -1, count: graph.count)
        var next = 0
        for start in graph.indices where label[start] < 0 {
            var stack = [start]
            label[start] = next
            while let node = stack.popLast() {
                for n in adj[node] where label[n] < 0 { label[n] = next; stack.append(n) }
            }
            next += 1
        }
        return label
    }

    static func strandedEntry(_ graph: [[Int]] = knnGraph) -> Int {
        let label = components(graph)
        let target = label[exactNeighbours(1)[0].id]
        return label.indices.first { label[$0] != target }!
    }

    static func strandedFraction(_ graph: [[Int]] = knnGraph) -> Double {
        let label = components(graph)
        let target = label[exactNeighbours(1)[0].id]
        return Double(label.filter { $0 != target }.count) / Double(label.count)
    }

    static func componentCount(_ graph: [[Int]]) -> Int { (components(graph).max() ?? -1) + 1 }

    struct GraphResult { let found: [Doc]; let comparisons: Int; let hops: Int }

    static func graphSearch(ef: Int, graph: [[Int]] = smallWorldGraph, topK: Int = k, entry: Int = 0, q: (Double, Double) = query) -> GraphResult {
        var visited: Set<Int> = [entry]
        var candidates = [entry]
        var best = [entry]
        var hops = 0
        var comparisons = 1
        while !candidates.isEmpty {
            hops += 1
            let expanded = candidates.flatMap { graph[$0] }.filter { visited.insert($0).inserted }
            comparisons += expanded.count
            if expanded.isEmpty { break }
            var seen = Set<Int>()
            let next = Array((best + expanded).filter { seen.insert($0).inserted }.sortedBy { distance(corpus[$0], q) }.prefix(max(ef, topK)))
            if next == best { break }
            candidates = Array(expanded.sortedBy { distance(corpus[$0], q) }.prefix(ef))
            best = next
        }
        return GraphResult(found: best.prefix(topK).map { corpus[$0] }, comparisons: comparisons, hops: hops)
    }

    /// Median over queries of (d_max − d_min) / d_min for uniform points.
    static func contrastRatio(_ dimension: Int, points: Int = 400, queries: Int = 20, seed: Int64 = 991) -> Double {
        var rng = LcgRandom64(seed)
        let data = (0..<points).map { _ in (0..<dimension).map { _ in rng.nextFloat() } }
        let ratios = (0..<queries).map { _ -> Double in
            let q = (0..<dimension).map { _ in rng.nextFloat() }
            let distances = data.map { p in p.indices.reduce(0.0) { $0 + sq(p[$1] - q[$1]) }.squareRoot() }
            return (distances.max()! - distances.min()!) / distances.min()!
        }
        return ratios.sorted()[queries / 2]
    }

    static func quantizedRecall(_ levels: Int, _ topK: Int = k) -> Double {
        func quant(_ v: Double) -> Double { (v * Double(levels - 1) + 0.5).rounded(.down) / Double(levels - 1) }
        let approx = Array(corpus.sortedBy { (sq(quant($0.x) - query.0) + sq(quant($0.y) - query.1)).squareRoot() }.prefix(topK))
        return recall(approx, topK)
    }

    static func quantizationStep(_ levels: Int) -> Double { 1.0 / Double(levels - 1) }

    static func neighbourSpread(_ topK: Int = k) -> Double {
        let near = exactNeighbours(topK)
        return distance(near.last!) - distance(near.first!)
    }

    static func bytesPerVector(_ dimension: Int, _ bitsPerComponent: Int) -> Int { dimension * bitsPerComponent / 8 }
}

// MARK: - Retrieval, ReAct and agents

enum RetrievalLab {
    struct Passage { let id: Int; let title: String; let text: String }

    static let corpus = [
        Passage(id: 0, title: "Linux kernel", text: "The Linux kernel is written mainly in the C programming language, with a small amount of assembly."),
        Passage(id: 1, title: "C language", text: "C was designed by Dennis Ritchie and first released in 1972 at Bell Labs."),
        Passage(id: 2, title: "Python", text: "Python is an interpreted language first released in 1991 by Guido van Rossum."),
        Passage(id: 3, title: "Rust", text: "Rust is a systems programming language first released in 2015 and used for kernel modules."),
        Passage(id: 4, title: "Git", text: "Git is a distributed version control system written in C and created to host the Linux kernel."),
        Passage(id: 5, title: "Assembly", text: "Assembly language is a low level notation released with the earliest machines and still used in kernels."),
        Passage(id: 6, title: "Bell Labs", text: "Bell Labs is a research organisation where Unix and the C language were developed."),
        Passage(id: 7, title: "Unix", text: "Unix is an operating system written in C and developed at Bell Labs in the early 1970s."),
    ]

    private static let stop: Set<String> = ["the", "is", "a", "an", "of", "in", "and", "with", "was", "were", "at", "to", "for", "by", "it", "that", "as", "on", "used", "still"]

    static func tokens(_ text: String) -> [String] {
        var out: [String] = []
        var current = ""
        for ch in text.lowercased() {
            if ch.isASCII && (ch.isLetter || ch.isNumber) { current.append(ch) } else {
                if !current.isEmpty { out.append(current); current = "" }
            }
        }
        if !current.isEmpty { out.append(current) }
        return out.filter { !stop.contains($0) }
    }

    private static let df: [String: Int] = {
        var counts: [String: Int] = [:]
        for p in corpus { for t in Set(tokens(p.text)) { counts[t, default: 0] += 1 } }
        return counts
    }()

    /// TF-IDF weights in first-occurrence order (Kotlin's LinkedHashMap).
    static func vector(_ text: String) -> [(String, Double)] {
        var order: [String] = []
        var tf: [String: Int] = [:]
        for t in tokens(text) {
            if tf[t] == nil { order.append(t) }
            tf[t, default: 0] += 1
        }
        return order.map { term in
            let idf = log((Double(corpus.count) + 1.0) / (Double(df[term] ?? 0) + 1.0)) + 1.0
            return (term, Double(tf[term]!) * idf)
        }
    }

    static func score(_ query: String, _ passage: Passage) -> Double {
        let q = vector(query), d = vector(passage.text)
        let dMap = Dictionary(uniqueKeysWithValues: d)
        var dot = 0.0
        for (term, w) in q { if let dw = dMap[term] { dot += w * dw } }
        let nq = q.reduce(0.0) { $0 + $1.1 * $1.1 }.squareRoot()
        let nd = d.reduce(0.0) { $0 + $1.1 * $1.1 }.squareRoot()
        return nq == 0 || nd == 0 ? 0 : dot / (nq * nd)
    }

    static func ranked(_ query: String) -> [(Passage, Double)] { corpus.map { ($0, score(query, $0)) }.sortedByDescending { $0.1 } }

    static func rankOf(_ query: String, _ passageId: Int) -> Int { (ranked(query).firstIndex { $0.0.id == passageId } ?? -1) + 1 }
}

enum ReActLab {
    static let question = "In what year was the language the Linux kernel is written in first released?"
    static let hop1PassageId = 0
    static let hop2PassageId = 1

    struct Turn { let thought: String; let action: String; let observation: String; let retrievedId: Int? }

    static func singleShot(topK: Int = 3) -> ([RetrievalLab.Passage], Bool) {
        let top = RetrievalLab.ranked(question).prefix(topK).map(\.0)
        return (Array(top), top.contains { $0.id == hop2PassageId })
    }

    static let trajectory: [Turn] = {
        let firstQuery = "Linux kernel written language"
        let firstHit = RetrievalLab.ranked(firstQuery)[0].0
        let secondQuery = "C language first released"
        let secondHit = RetrievalLab.ranked(secondQuery)[0].0
        return [
            Turn(thought: "The question has two parts. I need the language first, then that language's release year.",
                 action: "search(\"\(firstQuery)\")", observation: firstHit.text, retrievedId: firstHit.id),
            Turn(thought: "The language is C. The observation does not give a year, so the original question cannot be answered yet.",
                 action: "search(\"\(secondQuery)\")", observation: secondHit.text, retrievedId: secondHit.id),
            Turn(thought: "The observation gives 1972 for C, and the first hop established that the kernel is written in C.",
                 action: "finish(\"1972\")", observation: "1972", retrievedId: nil),
        ]
    }()

    static let closedBookAnswer = "1970"
    static let groundedAnswer = "1972"
}

enum AgentLab {
    struct Tool { let name: String; let latencyMs: Int; let schemaTokens: Int }

    static let tools = [
        Tool(name: "search", latencyMs: 240, schemaTokens: 38),
        Tool(name: "calculator", latencyMs: 15, schemaTokens: 22),
        Tool(name: "units", latencyMs: 12, schemaTokens: 26),
    ]

    struct Step { let label: String; let tokens: Int; var failed = false }

    static let trajectory = [
        Step(label: "thought + search(\"…\")", tokens: 48),
        Step(label: "observation: 3 passages", tokens: 96),
        Step(label: "thought + units(\"mi\", \"km\")", tokens: 61),
        Step(label: "observation: error, unknown unit \"mi\"", tokens: 34, failed: true),
        Step(label: "thought + units(\"mile\", \"km\")", tokens: 54, failed: true),
        Step(label: "observation: 1.609", tokens: 24),
        Step(label: "thought + calculator(\"…\")", tokens: 43),
        Step(label: "observation: 386.2 → final answer", tokens: 74),
    ]

    static let systemTokens = 180

    static func schemaTokens() -> Int { tools.reduce(0) { $0 + $1.schemaTokens } }

    static func contextAt(_ step: Int, _ steps: [Step] = trajectory) -> Int {
        systemTokens + schemaTokens() + steps.prefix(step).reduce(0) { $0 + $1.tokens }
    }

    /// One request per model turn; every turn resends everything before it.
    static func billedTokens(_ steps: [Step] = trajectory) -> Int {
        steps.indices.filter { $0 % 2 == 0 }.reduce(0) { $0 + contextAt($1 + 1, steps) }
    }

    static func finalContext(_ steps: [Step] = trajectory) -> Int { contextAt(steps.count, steps) }

    static func billingMultiple(_ steps: [Step] = trajectory) -> Double { Double(billedTokens(steps)) / Double(finalContext(steps)) }

    static var cleanTrajectory: [Step] { trajectory.filter { !$0.failed } }

    static func retryOverhead() -> Int { billedTokens() - billedTokens(cleanTrajectory) }

    static func sequentialLatency() -> Int { tools.reduce(0) { $0 + $1.latencyMs } }
    static func parallelLatency() -> Int { tools.map(\.latencyMs).max()! }
}

// MARK: - Hallucination mitigation

enum HallucinationLab {
    struct Question { let text: String; let chainAccuracy: Double; let distinctWrong: Int; let supported: Bool }

    static let samples = 5

    static let questions = [
        Question(text: "Which language is the Linux kernel written in?", chainAccuracy: 0.94, distinctWrong: 4, supported: true),
        Question(text: "When was C first released?", chainAccuracy: 0.90, distinctWrong: 4, supported: true),
        Question(text: "Who developed Unix?", chainAccuracy: 0.88, distinctWrong: 4, supported: true),
        Question(text: "Which organisation employed Dennis Ritchie?", chainAccuracy: 0.82, distinctWrong: 3, supported: true),
        Question(text: "What year was Python released?", chainAccuracy: 0.78, distinctWrong: 3, supported: true),
        Question(text: "What is Rust's release year?", chainAccuracy: 0.70, distinctWrong: 3, supported: true),
        Question(text: "Which release introduced the parser rewrite?", chainAccuracy: 0.30, distinctWrong: 1, supported: false),
        Question(text: "What is the current maintainer's tenure?", chainAccuracy: 0.22, distinctWrong: 1, supported: false),
        Question(text: "How many modules shipped in the last release?", chainAccuracy: 0.18, distinctWrong: 1, supported: false),
        Question(text: "What is the internal name of the scheduler patch?", chainAccuracy: 0.12, distinctWrong: 1, supported: false),
    ]

    static let scatteredQuestions = questions.map { $0.supported ? $0 : Question(text: $0.text, chainAccuracy: $0.chainAccuracy, distinctWrong: 4, supported: false) }

    static func confidence(_ q: Question) -> Double { CotLab.modalShare(q.chainAccuracy, samples, q.distinctWrong) }
    static func accuracy(_ q: Question) -> Double { CotLab.pluralityAccuracy(q.chainAccuracy, samples, q.distinctWrong) }

    static func overallAccuracy(_ population: [Question] = questions) -> Double {
        population.reduce(0.0) { $0 + accuracy($1) } / Double(population.count)
    }

    static func expectedCalibrationError(_ population: [Question] = questions, bins: Int = 5) -> Double {
        var order: [Int] = []
        var groups: [Int: [Question]] = [:]
        for q in population {
            let b = min(max(Int(confidence(q) * Double(bins)), 0), bins - 1)
            if groups[b] == nil { order.append(b) }
            groups[b, default: []].append(q)
        }
        return order.reduce(0.0) { acc, b in
            let group = groups[b]!
            let conf = group.reduce(0.0) { $0 + confidence($1) } / Double(group.count)
            let acc2 = group.reduce(0.0) { $0 + accuracy($1) } / Double(group.count)
            return acc + Double(group.count) / Double(population.count) * abs(conf - acc2)
        }
    }

    static func abstentionSweep(_ population: [Question] = questions) -> [(Double, Double, Double)] {
        [0.0, 0.5, 0.6, 0.7, 0.8, 0.9].map { threshold in
            let answered = population.filter { confidence($0) >= threshold }
            let coverage = Double(answered.count) / Double(population.count)
            let selective = answered.isEmpty ? 1.0 : answered.reduce(0.0) { $0 + accuracy($1) } / Double(answered.count)
            return (threshold, coverage, selective)
        }
    }

    static func bestThreshold(_ population: [Question] = questions) -> (Double, Double, Double) {
        let eligible = abstentionSweep(population).filter { $0.1 >= 0.5 }
        var best = eligible[0]
        for e in eligible where e.2 > best.2 { best = e }
        return best
    }

    static func groundedCoverage(_ population: [Question] = questions) -> Double {
        Double(population.filter(\.supported).count) / Double(population.count)
    }

    static func groundedSelectiveAccuracy(_ population: [Question] = questions) -> Double {
        let supported = population.filter(\.supported)
        return supported.reduce(0.0) { $0 + accuracy($1) } / Double(supported.count)
    }

    static func confidentlyWrong(_ population: [Question] = questions) -> [Question] {
        population.filter { confidence($0) > 0.55 && accuracy($0) < 0.5 }
    }

    static func minSupportedConfidence(_ population: [Question] = questions) -> Double { population.filter(\.supported).map(confidence).min()! }
    static func maxUnsupportedConfidence(_ population: [Question] = questions) -> Double { population.filter { !$0.supported }.map(confidence).max()! }
    static func confidenceSeparates(_ population: [Question] = questions) -> Bool { minSupportedConfidence(population) > maxUnsupportedConfidence(population) }

    static func spread(_ population: [Question] = questions) -> [(Double, Double)] { population.map { (confidence($0), accuracy($0)) } }
}
