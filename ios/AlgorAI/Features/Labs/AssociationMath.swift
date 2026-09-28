import Foundation

// Port of AssociationMath.kt: the shared basket database and the three algorithms behind the B7
// Association Rules labs (Apriori, Eclat, FP-Growth). Itemsets are kept as ordered arrays so they
// iterate the way Kotlin's insertion-ordered sets do.

let basketItems = ["A", "B", "C", "D", "E"]

let basketNames = ["A": "bread", "B": "milk", "C": "eggs", "D": "beer", "E": "cereal"]

let transactions: [[String]] = [
    ["A", "B", "C"], ["A", "B"], ["A", "B", "D"], ["B", "C"], ["A", "B", "C", "E"],
    ["D", "E"], ["A", "B", "C"], ["B", "C", "E"], ["B", "E"], ["A", "B"],
]

/// Absolute minimum support: 3 of 10 transactions.
let minSupport = 3

func support(_ itemset: [String]) -> Int { transactions.filter { t in itemset.allSatisfy(t.contains) }.count }

func tidList(_ item: String) -> [Int] { transactions.indices.filter { transactions[$0].contains(item) } }

func itemsetLabel(_ itemset: [String]) -> String { itemset.sorted().joined() }

// MARK: - Apriori

struct AprioriCandidate {
    let itemset: [String]
    let support: Int
    /// Non-nil when downward closure killed it before any transaction was read.
    var prunedBySubset: [String]?
    var frequent: Bool { prunedBySubset == nil && support >= minSupport }
    var label: String { itemsetLabel(itemset) }
}

struct AprioriPass {
    let k: Int
    let candidates: [AprioriCandidate]
    /// Candidates that survived to be counted.
    let counted: Int
    var frequent: [AprioriCandidate] { candidates.filter(\.frequent) }
}

/// Fₖ₋₁ × Fₖ₋₁ candidate generation plus subset pruning.
func aprioriPasses() -> [AprioriPass] {
    var passes: [AprioriPass] = []
    let level1 = basketItems.map { AprioriCandidate(itemset: [$0], support: support([$0])) }
    passes.append(AprioriPass(k: 1, candidates: level1, counted: level1.count))
    var frequent = level1.filter(\.frequent).map(\.itemset)
    var k = 2
    while frequent.count >= 2 {
        let sortedSets = frequent.map { $0.sorted() }
        let frequentSets = Set(frequent.map { Set($0) })
        var candidates: [AprioriCandidate] = []
        var seen = Set<[String]>()
        for i in sortedSets.indices {
            for j in (i + 1)..<sortedSets.count {
                let a = sortedSets[i], b = sortedSets[j]
                if Array(a.dropLast()) != Array(b.dropLast()) { continue }
                let joined = Array(Set(a + b)).sorted()
                if joined.count != k || !seen.insert(joined).inserted { continue }
                let badSubset = joined.map { item in joined.filter { $0 != item } }.first { !frequentSets.contains(Set($0)) }
                candidates.append(badSubset != nil
                    ? AprioriCandidate(itemset: joined, support: 0, prunedBySubset: badSubset)
                    : AprioriCandidate(itemset: joined, support: support(joined)))
            }
        }
        if candidates.isEmpty { break }
        passes.append(AprioriPass(k: k, candidates: candidates, counted: candidates.filter { $0.prunedBySubset == nil }.count))
        frequent = candidates.filter(\.frequent).map(\.itemset)
        k += 1
    }
    return passes
}

// MARK: - Eclat

struct EclatStep {
    let itemset: [String]
    let tids: [Int]
    /// The two tid-lists intersected to produce this one; nil for the 1-itemsets.
    var from: ([String], [String])?
    let depth: Int
    var frequent: Bool { tids.count >= minSupport }
    var label: String { itemsetLabel(itemset) }
}

/// Depth-first mining over the vertical layout; support is the intersection's size.
func eclatSteps() -> [EclatStep] {
    var steps: [EclatStep] = []
    let roots = basketItems.map { ($0, tidList($0)) }.filter { $0.1.count >= minSupport }.sortedByDescending { $0.1.count }
    for (item, tids) in roots { steps.append(EclatStep(itemset: [item], tids: tids, depth: 0)) }

    func extend(_ prefix: [String], _ prefixTids: [Int], _ rest: [(String, [Int])], _ depth: Int) {
        for (index, (item, tids)) in rest.enumerated() {
            let merged = prefixTids.filter(tids.contains)
            let itemset = prefix.contains(item) ? prefix : prefix + [item]
            steps.append(EclatStep(itemset: itemset, tids: merged, from: (prefix, [item]), depth: depth))
            if merged.count >= minSupport { extend(itemset, merged, Array(rest.dropFirst(index + 1)), depth + 1) }
        }
    }
    for (index, (item, tids)) in roots.enumerated() { extend([item], tids, Array(roots.dropFirst(index + 1)), 1) }
    return steps
}

// MARK: - FP-Growth

/// Frequent items, most frequent first — the order the tree is built in.
func fpItemOrder() -> [String] {
    basketItems.map { ($0, support([$0])) }.filter { $0.1 >= minSupport }.sortedByDescending { $0.1 }.map(\.0)
}

/// A transaction with infrequent items dropped and the rest in header order.
func fpSortedTransaction(_ transaction: [String]) -> [String] { fpItemOrder().filter(transaction.contains) }

struct FpNode { let id: Int; let item: String; let parent: Int?; var count: Int }

struct FpTree {
    let nodes: [FpNode]
    /// item → node ids holding it, in insertion order.
    let header: [String: [Int]]
}

func buildFpTree(upTo: Int = transactions.count) -> FpTree {
    var nodes: [FpNode] = []
    var children: [String: Int] = [:]
    var header: [String: [Int]] = [:]
    for transaction in transactions.prefix(upTo) {
        var parent: Int?
        for item in fpSortedTransaction(transaction) {
            let key = "\(parent.map(String.init) ?? "nil")|\(item)"
            if let existing = children[key] {
                nodes[existing].count += 1
                parent = existing
            } else {
                let node = FpNode(id: nodes.count, item: item, parent: parent, count: 1)
                nodes.append(node)
                children[key] = node.id
                header[item, default: []].append(node.id)
                parent = node.id
            }
        }
    }
    return FpTree(nodes: nodes, header: header)
}

struct ConditionalPattern { let path: [String]; let count: Int }

/// Every root-ward path above a node holding `item`, weighted by that node's count.
func conditionalPatternBase(_ tree: FpTree, _ item: String) -> [ConditionalPattern] {
    (tree.header[item] ?? []).map { id in
        var path: [String] = []
        var current = tree.nodes[id].parent
        while let c = current {
            path.append(tree.nodes[c].item)
            current = tree.nodes[c].parent
        }
        return ConditionalPattern(path: path.reversed(), count: tree.nodes[id].count)
    }
}

// MARK: - Rules

struct AssociationRule {
    let antecedent: [String]
    let consequent: [String]
    let support: Int
    let confidence: Double
    let lift: Double
    var label: String { "\(itemsetLabel(antecedent)) → \(itemsetLabel(consequent))" }
}

func associationRule(_ antecedent: [String], _ consequent: [String]) -> AssociationRule {
    let n = Double(transactions.count)
    let both = support(antecedent + consequent.filter { !antecedent.contains($0) })
    let confidence = Double(both) / Double(support(antecedent))
    let lift = confidence / (Double(support(consequent)) / n)
    return AssociationRule(antecedent: antecedent, consequent: consequent, support: both, confidence: confidence, lift: lift)
}

/// Every rule derivable from the frequent itemsets of size ≥ 2, ordered by lift.
func allRules() -> [AssociationRule] {
    let frequent = aprioriPasses().flatMap { $0.frequent.map(\.itemset) }.filter { $0.count >= 2 }
    return frequent.flatMap { itemset in itemset.map { c in associationRule(itemset.filter { $0 != c }, [c]) } }.sortedByDescending(\.lift)
}
