import Foundation

// Port of AssociationMath.kt: the shared basket database and the FP-tree behind the association-rule
// storyboards (SeriesStoryLabs). Itemsets are kept as ordered arrays so they
// iterate the way Kotlin's insertion-ordered sets do.

let basketItems = ["A", "B", "C", "D", "E"]

let transactions: [[String]] = [
    ["A", "B", "C"], ["A", "B"], ["A", "B", "D"], ["B", "C"], ["A", "B", "C", "E"],
    ["D", "E"], ["A", "B", "C"], ["B", "C", "E"], ["B", "E"], ["A", "B"],
]

/// Absolute minimum support: 3 of 10 transactions.
let minSupport = 3

func support(_ itemset: [String]) -> Int { transactions.filter { t in itemset.allSatisfy(t.contains) }.count }

func itemsetLabel(_ itemset: [String]) -> String { itemset.sorted().joined() }

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
