import Foundation

// Port of SyntaxMath.kt (D2 syntax labs). Each lab object is ported as the Swift labs need it.

// MARK: - Dependency parsing

enum DependencyLab {
    static let sentence = ["the", "small", "dog", "chased", "a", "cat"]

    /// Gold heads, 1-indexed with 0 = ROOT.
    static let goldHeads = [3, 3, 4, 0, 6, 4]
    static let goldLabels = ["det", "amod", "nsubj", "root", "det", "obj"]

    enum Move { case shift, leftArc, rightArc }

    struct Arc: Equatable { let head: Int; let dependent: Int; let label: String }

    struct Step {
        let move: Move
        let label: String?
        let stack: [Int]
        let buffer: [Int]
        let arcs: [Arc]
    }

    /// Arc-standard transition parsing driven by an oracle that reads the gold tree.
    static func parse() -> [Step] {
        var stack = [0]
        var buffer = Array(1...sentence.count)
        var arcs: [Arc] = []
        var steps: [Step] = []
        func hasAllChildren(_ node: Int) -> Bool {
            !goldHeads.indices.contains { i in goldHeads[i] == node && !arcs.contains { $0.dependent == i + 1 } }
        }
        while !buffer.isEmpty || stack.count > 1 {
            guard let top = stack.last else { break }
            let second: Int? = stack.count >= 2 ? stack[stack.count - 2] : nil
            let move: Move
            var label: String?
            if let second, second != 0, goldHeads[second - 1] == top, hasAllChildren(second) {
                move = .leftArc
                label = goldLabels[second - 1]
            } else if let second, top != 0, goldHeads[top - 1] == second, hasAllChildren(top) {
                move = .rightArc
                label = goldLabels[top - 1]
            } else if !buffer.isEmpty {
                move = .shift
            } else {
                break
            }
            switch move {
            case .shift: stack.append(buffer.removeFirst())
            case .leftArc:
                arcs.append(Arc(head: top, dependent: second!, label: label!))
                stack.remove(at: stack.count - 2)
            case .rightArc:
                arcs.append(Arc(head: second!, dependent: top, label: label!))
                stack.removeLast()
            }
            steps.append(Step(move: move, label: label, stack: stack, buffer: buffer, arcs: arcs))
        }
        return steps
    }

    static func transitionCount() -> Int { parse().count }

    static func wordAt(_ index: Int) -> String { index == 0 ? "ROOT" : sentence[index - 1] }

    static func uas(_ predictedHeads: [Int]) -> Double {
        Double(goldHeads.indices.filter { predictedHeads[$0] == goldHeads[$0] }.count) / Double(goldHeads.count)
    }

    static func las(_ predictedHeads: [Int], _ predictedLabels: [String]) -> Double {
        Double(goldHeads.indices.filter { predictedHeads[$0] == goldHeads[$0] && predictedLabels[$0] == goldLabels[$0] }.count) / Double(goldHeads.count)
    }

    /// "small" attached to "chased" (a head error) and "cat" labelled nsubj (a label error).
    static let predictedHeads = [3, 4, 4, 0, 6, 4]
    static let predictedLabels = ["det", "amod", "nsubj", "root", "det", "nsubj"]
}

// MARK: - Constituency parsing

enum ConstituencyLab {
    final class Node {
        let label: String
        let children: [Node]
        let word: String?
        init(_ label: String, _ children: [Node] = [], word: String? = nil) {
            self.label = label
            self.children = children
            self.word = word
        }
        var isLeaf: Bool { word != nil }
    }

    struct Span: Hashable { let label: String; let start: Int; let end: Int }

    static let gold = Node("S", [
        Node("NP", [Node("DT", word: "the"), Node("JJ", word: "small"), Node("NN", word: "dog")]),
        Node("VP", [Node("VBD", word: "chased"), Node("NP", [Node("DT", word: "a"), Node("NN", word: "cat")])]),
    ])

    /// The object NP swallowed into a flat VP.
    static let predicted = Node("S", [
        Node("NP", [Node("DT", word: "the"), Node("JJ", word: "small"), Node("NN", word: "dog")]),
        Node("VP", [Node("VBD", word: "chased"), Node("DT", word: "a"), Node("NN", word: "cat")]),
    ])

    /// (label, start, end) for every non-terminal, in post-order — the units evalb scores.
    static func spans(_ node: Node, _ start: Int = 0) -> ([Span], Int) {
        if node.isLeaf { return ([], start + 1) }
        var cursor = start
        var out: [Span] = []
        for child in node.children {
            let (childSpans, next) = spans(child, cursor)
            out += childSpans
            cursor = next
        }
        return (out + [Span(label: node.label, start: start, end: cursor)], cursor)
    }

    static func spanList(_ node: Node) -> [Span] { spans(node).0 }

    struct Prf {
        let precision: Double
        let recall: Double
        var f1: Double { precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall) }
    }

    static func evalb(_ predictedTree: Node = predicted, _ goldTree: Node = gold) -> Prf {
        let p = Set(spanList(predictedTree)), g = Set(spanList(goldTree))
        let hits = p.filter(g.contains).count
        return Prf(precision: Double(hits) / Double(p.count), recall: Double(hits) / Double(g.count))
    }

    private static let headRules: [String: [String]] = [
        "S": ["VP", "NP"],
        "VP": ["VBD", "VBZ", "VBP", "VB", "NP"],
        "NP": ["NN", "NNS", "NP"],
    ]

    static func leafCount(_ node: Node) -> Int { node.isLeaf ? 1 : node.children.reduce(0) { $0 + leafCount($1) } }

    /// 1-based index of this subtree's head word.
    static func headOf(_ node: Node, _ offset: Int = 0) -> Int {
        if node.isLeaf { return offset + 1 }
        var positions: [(Node, Int)] = []
        var cursor = offset
        for child in node.children {
            positions.append((child, cursor))
            cursor += leafCount(child)
        }
        let priority = headRules[node.label] ?? []
        let chosen = priority.lazy.compactMap { label in positions.last { $0.0.label == label } }.first ?? positions.last!
        return headOf(chosen.0, chosen.1)
    }

    static func toDependencies(_ node: Node = gold) -> [Int] {
        var heads = [Int](repeating: 0, count: leafCount(node))
        func walk(_ current: Node, _ offset: Int, _ parentHead: Int) {
            if current.isLeaf { heads[offset] = parentHead; return }
            let head = headOf(current, offset)
            var cursor = offset
            for child in current.children {
                let childHead = headOf(child, cursor)
                walk(child, cursor, childHead == head ? parentHead : head)
                cursor += leafCount(child)
            }
        }
        walk(node, 0, 0)
        return heads
    }
}
