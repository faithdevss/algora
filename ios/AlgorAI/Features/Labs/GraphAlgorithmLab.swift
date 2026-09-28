import SwiftUI

// Port of GraphAlgorithmSection.kt: shortest-path / MST / SCC / flow / colouring / traversal
// algorithms over small fixed graphs, one precomputed frame per interesting event. The renderer is
// shared: node fill + badge under the node, edge colour, arrowheads, and an optional distance matrix.

private let graphInf = Int.max / 4

private struct GNode { let id: String; let x: CGFloat; let y: CGFloat }
private struct GEdge { let from: String; let to: String; var weight: Int?; var directed = false }

private struct GraphDef {
    let nodes: [GNode]
    let edges: [GEdge]
    var ids: [String] { nodes.map(\.id) }
}

private enum NodeMark { case idle, frontier, active, updated, done }
private enum EdgeMark { case idle, active, accepted, rejected }

private func nodeColor(_ m: NodeMark) -> Color {
    switch m {
    case .idle: Color(hex: 0x7C3AED)
    case .frontier: SimColors.blue
    case .active: SimColors.active
    case .updated: SimColors.green
    case .done: Color(hex: 0xF97316)
    }
}

private func edgeColor(_ m: EdgeMark) -> Color {
    switch m {
    case .idle: SimColors.idle
    case .active: SimColors.active
    case .accepted: SimColors.green
    case .rejected: SimColors.red
    }
}

// Component colours; deliberately start far from the idle violet.
private let groupColors = [Color(hex: 0x0EA5E9), Color(hex: 0xF97316), Color(hex: 0x10B981), Color(hex: 0xEC4899), Color(hex: 0x6366F1), Color(hex: 0xA855F7)]

private struct GFrame {
    let status: String
    var nodeMarks: [String: NodeMark] = [:]
    var badges: [String: String] = [:]
    var groups: [String: Int] = [:]
    var edgeMarks: [Int: EdgeMark] = [:]
    /// Kosaraju's second pass runs on the transpose; the renderer flips every arrow.
    var reversed = false
    var undirected = false
    var hideWeights = false
    var hiddenEdges: Set<Int> = []
    var matrix: [[Int]]?
    var matrixFocus: (Int, Int)?
    var matrixVia: Int?
    /// The edge this frame is deciding on — drawn in the active yellow whatever its mark.
    var focusEdge: Int?
    /// A hand-written headline (Kruskal, Prim). Frames without one split `status` with
    /// `LabCaption.split`: its first sentence becomes the headline, the rest the explanation.
    var headline: [GraphSpan]?
    var body: String?
    /// The redesigned labs (docs mocks) carry everything they draw here instead.
    var story: GraphStory? = nil
}

/// A piece of a headline; a tone colours it to match what it names in the graph.
private enum GraphTone { case active, rejected, done }

private struct GraphSpan { let text: String; var tone: GraphTone? }


private struct GConfig {
    let intro: String
    let def: GraphDef
    let legend: [(Color, String)]
    let build: () -> [GFrame]
    /// Lists every edge sorted by weight under the graph: Kruskal walks it, Prim reads candidates off it.
    var edgeTable = false
    /// Tabs over a story card, each its own graph and storyboard (Path / Cycle).
    var variants: [GraphVariant] = []
}

private struct GraphVariant { let label: String; let def: GraphDef; let build: () -> [GFrame] }

// MARK: - Story frames
// The redesigned graph labs: solid nodes coloured by what the algorithm is doing to them, a badge above
// or below each, coloured edges with optional boxed labels, rows of cells for the structure beside the
// graph (path, queue, stack, dist), then chips and a headline whose key term takes its node's colour.

/// `active` is the current node or edge, `path` what is on the path / stack / queue, `done` finished or
/// used, `cut` a cut vertex or bridge (and a min cut), `warn` something missing, `empty` a slot to fill.
private enum GS { case idle, active, path, done, cut, warn, empty }

private struct GCell { let text: String; var tone: GS = .idle }
/// A labelled run of cells; several groups can share one row (CIRCUIT · SUB-TOUR, QUEUE · OUTPUT).
private struct GCellGroup { let label: String?; let cells: [GCell]; var note: String? = nil }
/// One line of a comparison list: the test on the left, what it decides on the right.
private struct GLine { let test: String; let verdict: String; let tone: GS }
private struct GChip { let key: String; let value: String; var tone: GS? = nil }

private struct GraphStory {
    let title: String
    let note: String
    let headline: String
    let body: String
    var nodes: [String: GS] = [:]
    /// Text and tone; drawn above a node in the top third of the graph, below it otherwise.
    var badges: [String: (String, GS)] = [:]
    var edges: [Int: GS] = [:]
    /// Replaces an edge's weight (a flow "2/3"); `boxed` draws the label in a pill on the edge.
    var edgeLabels: [Int: String] = [:]
    var boxed: Set<Int> = []
    /// Pairs with no edge between them, drawn dashed red: the edge a cycle would need.
    var missing: [(String, String)] = []
    var reversed = false
    var rows: [[GCellGroup]] = []
    var lines: [GLine] = []
    var chips: [GChip] = []
    var emphasis: GS = .active
    var legend: [(GS, String)] = []

    var status: String { graphSpans(headline).map(\.text).joined() + " " + body }
}

/// `{…}` takes the story's emphasis colour; `{w:…}` red, `{a:…}` yellow, `{p:…}` blue, `{m:…}` green and
/// `{c:…}` violet name it outright.
private func graphSpans(_ text: String) -> [(text: String, mark: Character?)] {
    var out: [(String, Character?)] = []
    var rest = Substring(text)
    while let open = rest.firstIndex(of: "{"), let close = rest[open...].firstIndex(of: "}") {
        out.append((String(rest[..<open]), nil))
        var inner = rest[rest.index(after: open)..<close]
        var mark: Character = "*"
        if inner.count > 2, let first = inner.first, "wapmc".contains(first), inner.dropFirst().first == ":" {
            mark = first
            inner = inner.dropFirst(2)
        }
        out.append((String(inner), mark))
        rest = rest[rest.index(after: close)...]
    }
    out.append((String(rest), nil))
    return out
}

private func storyFrame(_ story: GraphStory) -> GFrame { GFrame(status: story.status, story: story) }

private extension GraphDef {
    /// The index of the edge joining two nodes, in either direction.
    func edgeIndex(_ a: String, _ b: String) -> Int {
        edges.firstIndex { ($0.from == a && $0.to == b) || ($0.from == b && $0.to == a) }!
    }
    func directedIndex(_ a: String, _ b: String) -> Int { edges.firstIndex { $0.from == a && $0.to == b }! }
}

/// Kotlin's `map + map` (right wins).
private func + <K, V>(lhs: [K: V], rhs: [K: V]) -> [K: V] { lhs.merging(rhs) { _, r in r } }

private func marks<S: Sequence>(_ ids: S, _ m: NodeMark) -> [String: NodeMark] where S.Element == String {
    Dictionary(ids.map { ($0, m) }, uniquingKeysWith: { a, _ in a })
}

private func edgeMarks<S: Sequence>(_ idx: S, _ m: EdgeMark) -> [Int: EdgeMark] where S.Element == Int {
    Dictionary(idx.map { ($0, m) }, uniquingKeysWith: { a, _ in a })
}

private func e(_ from: String, _ to: String, _ w: Int? = nil, directed: Bool = false) -> GEdge { GEdge(from: from, to: to, weight: w, directed: directed) }
private func d(_ from: String, _ to: String, _ w: Int? = nil) -> GEdge { GEdge(from: from, to: to, weight: w, directed: true) }
private func n(_ id: String, _ x: CGFloat, _ y: CGFloat) -> GNode { GNode(id: id, x: x, y: y) }

// MARK: - Graphs

private let negativeWeightGraph = GraphDef(
    nodes: [n("A", 0.06, 0.50), n("B", 0.40, 0.12), n("C", 0.80, 0.12), n("D", 0.40, 0.88), n("E", 0.80, 0.88)],
    edges: [d("A", "B", 6), d("A", "D", 7), d("B", "C", 5), d("B", "D", 8), d("B", "E", -4), d("C", "B", -2), d("D", "C", -3), d("D", "E", 9), d("E", "C", 7), d("E", "A", 2)]
)

private let allPairsGraph = GraphDef(
    nodes: [n("A", 0.15, 0.15), n("B", 0.85, 0.15), n("C", 0.85, 0.85), n("D", 0.15, 0.85)],
    edges: [d("A", "B", 3), d("A", "D", 7), d("B", "A", 8), d("B", "C", 2), d("C", "A", 5), d("C", "D", 1), d("D", "A", 2)]
)

private let mstGraph = GraphDef(
    nodes: [n("A", 0.10, 0.22), n("B", 0.50, 0.05), n("C", 0.90, 0.24), n("D", 0.14, 0.80), n("E", 0.54, 0.95), n("F", 0.92, 0.74)],
    edges: [e("A", "B", 4), e("A", "D", 3), e("B", "C", 5), e("B", "D", 6), e("B", "E", 2), e("C", "E", 7), e("C", "F", 4), e("D", "E", 3), e("E", "F", 5)]
)


private let variantsGraph = GraphDef(
    nodes: [n("A", 0.08, 0.22), n("B", 0.42, 0.06), n("C", 0.78, 0.24), n("D", 0.30, 0.60), n("E", 0.70, 0.66), n("F", 0.44, 0.96)],
    edges: [d("A", "B", 7), d("A", "D", 1), d("B", "C", 1), d("D", "B", 1), d("D", "E", 9), d("C", "E", 1), d("E", "F", 2), d("F", "D", 1)]
)




private let dagDpGraph = GraphDef(
    nodes: [n("A", 0.08, 0.20), n("B", 0.08, 0.80), n("C", 0.38, 0.50), n("D", 0.68, 0.18), n("E", 0.68, 0.82), n("F", 0.94, 0.50)],
    edges: [d("A", "C", 3), d("B", "C", 6), d("C", "D", 4), d("C", "E", 2), d("D", "F", 5), d("E", "F", 9)]
)

private let shortestPathChoiceGraph = GraphDef(
    nodes: [n("S", 0.06, 0.50), n("A", 0.38, 0.14), n("B", 0.38, 0.86), n("C", 0.72, 0.50), n("T", 0.95, 0.14)],
    edges: [d("S", "A", 1), d("S", "B", 4), d("A", "C", 6), d("B", "C", 1), d("A", "T", 9), d("C", "T", 1)]
)

private let colouringGraph = GraphDef(
    nodes: [n("A", 0.10, 0.18), n("B", 0.10, 0.82), n("C", 0.42, 0.50), n("D", 0.72, 0.16), n("E", 0.72, 0.84), n("F", 0.96, 0.50)],
    edges: [e("A", "B"), e("A", "C"), e("B", "C"), e("C", "D"), e("C", "E"), e("D", "F"), e("E", "F")]
)

private let bayesNetGraph = GraphDef(
    nodes: [n("Cloudy", 0.50, 0.10), n("Sprinkler", 0.18, 0.45), n("Rain", 0.80, 0.45), n("WetGrass", 0.50, 0.85)],
    edges: [d("Cloudy", "Sprinkler"), d("Cloudy", "Rain"), d("Sprinkler", "WetGrass"), d("Rain", "WetGrass")]
)



private let courseGraph = GraphDef(
    nodes: [n("101", 0.08, 0.50), n("201", 0.34, 0.18), n("210", 0.34, 0.82), n("301", 0.62, 0.50), n("330", 0.90, 0.18), n("401", 0.90, 0.82)],
    edges: [d("101", "201"), d("101", "210"), d("201", "301"), d("210", "301"), d("301", "330"), d("301", "401"), d("401", "201")]
)

private let unionFindGraph = GraphDef(
    nodes: [n("A", 0.10, 0.22), n("B", 0.10, 0.78), n("C", 0.38, 0.50), n("D", 0.66, 0.18), n("E", 0.66, 0.82), n("F", 0.92, 0.50)],
    edges: [e("A", "B"), e("C", "D"), e("B", "C"), e("A", "D"), e("E", "F"), e("D", "E")]
)

private let smallBridgeGraph = GraphDef(
    nodes: [n("0", 0.15, 0.22), n("1", 0.15, 0.78), n("2", 0.38, 0.50), n("3", 0.62, 0.50), n("4", 0.85, 0.22), n("5", 0.85, 0.78)],
    edges: [e("0", "1"), e("1", "2"), e("0", "2"), e("3", "4"), e("4", "5"), e("3", "5"), e("2", "3")]
)

private func distText(_ v: Int) -> String { v >= graphInf ? "∞" : "\(v)" }

private func neighboursOf(_ def: GraphDef, _ id: String, reversed: Bool = false) -> [String] {
    def.edges.compactMap { e -> String? in
        let from = reversed ? e.to : e.from
        let to = reversed ? e.from : e.to
        if from == id { return to }
        if !e.directed && to == id { return from }
        return nil
    }.sorted()
}

private func undirectedAdjacency(_ def: GraphDef) -> [String: [String]] {
    Dictionary(uniqueKeysWithValues: def.ids.map { id in
        (id, def.edges.compactMap { $0.from == id ? $0.to : $0.to == id ? $0.from : nil }.sorted())
    })
}

private func positionBadges(_ path: [String]) -> [String: String] {
    Dictionary(path.enumerated().map { ($1, "#\($0 + 1)") }, uniquingKeysWith: { _, b in b })
}

// MARK: - Graph variants

private func graphVariantsFrames() -> [GFrame] {
    let def = variantsGraph
    let backEdgeIndex = def.edges.firstIndex { $0.from == "F" && $0.to == "D" }!

    func reachable(_ from: String, directed: Bool) -> Set<String> {
        var seen: Set<String> = [from]
        var queue = [from]
        while !queue.isEmpty {
            let cur = queue.removeFirst()
            for e in def.edges {
                let next: String? = e.from == cur ? e.to : (!directed && e.to == cur ? e.from : nil)
                if let next, seen.insert(next).inserted { queue.append(next) }
            }
        }
        return seen
    }

    func bfsHops(_ from: String, _ to: String) -> [String] {
        var prev: [String: String] = [:]
        var seen: Set<String> = [from]
        var queue = [from]
        while !queue.isEmpty {
            let cur = queue.removeFirst()
            for e in def.edges where e.from == cur && seen.insert(e.to).inserted {
                prev[e.to] = cur
                queue.append(e.to)
            }
        }
        if !seen.contains(to) { return [] }
        var path = [to]
        while path.last! != from { path.append(prev[path.last!]!) }
        return path.reversed()
    }

    func cheapestPath(_ from: String, _ to: String) -> ([String], Int) {
        var dist = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, graphInf) })
        var prev: [String: String] = [:]
        dist[from] = 0
        for _ in def.ids {
            for e in def.edges {
                let dd = dist[e.from]!, w = e.weight ?? 1
                if dd + w < dist[e.to]! { dist[e.to] = dd + w; prev[e.to] = e.from }
            }
        }
        var path = [to]
        while path.last! != from { path.append(prev[path.last!]!) }
        return (path.reversed(), dist[to]!)
    }

    func edgeIndicesOf(_ path: [String]) -> Set<Int> {
        Set(zip(path, path.dropFirst()).compactMap { u, v in def.edges.firstIndex { $0.from == u && $0.to == v } })
    }

    func topoOrder(_ skip: Set<Int>) -> [String] {
        var indeg = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, 0) })
        for (i, e) in def.edges.enumerated() where !skip.contains(i) { indeg[e.to]! += 1 }
        var ready = def.ids.filter { indeg[$0]! == 0 }.sorted()
        var order: [String] = []
        while !ready.isEmpty {
            let cur = ready.removeFirst()
            order.append(cur)
            for (i, e) in def.edges.enumerated() where !skip.contains(i) && e.from == cur {
                indeg[e.to]! -= 1
                if indeg[e.to]! == 0 { ready.append(e.to) }
            }
        }
        return order
    }

    let undirectedFromC = reachable("C", directed: false)
    let directedFromC = reachable("C", directed: true)
    let unreachable = def.ids.filter { !directedFromC.contains($0) }.sorted()
    let hopPath = bfsHops("A", "E")
    let hopCost = zip(hopPath, hopPath.dropFirst()).reduce(0) { acc, p in acc + (def.edges.first { $0.from == p.0 && $0.to == p.1 }!.weight ?? 0) }
    let (costPath, cost) = cheapestPath("A", "E")
    let partial = topoOrder([])
    let stuck = def.ids.filter { !partial.contains($0) }.sorted()
    let cycle = ["D", "E", "F", "D"]
    let acyclic = topoOrder([backEdgeIndex])
    let v = def.ids.count, ec = def.edges.count
    let costEdges = edgeIndicesOf(costPath)

    return [
        GFrame(status: "Six vertices, eight edges. Everything below is the same set of pairs — what changes is only what a pair is taken to mean, and every change costs an algorithm something.",
               undirected: true, hideWeights: true),
        GFrame(status: "Undirected: an edge is a mutual relation, so a walk from C reaches all \(undirectedFromC.count) vertices. Degrees sum to \(ec * 2) = 2 × \(ec) edges — the handshake lemma, and the reason an undirected adjacency list stores every edge twice.",
               groups: Dictionary(uniqueKeysWithValues: undirectedFromC.map { ($0, 2) }), undirected: true, hideWeights: true),
        GFrame(status: "Directed: the same edges, now one-way. From C only \(directedFromC.sorted().joined(separator: ", ")) are reachable — \(unreachable.joined(separator: ", ")) \(unreachable.count == 1 ? "is" : "are") cut off, because nothing points back into \(unreachable.joined(separator: " or ")). Reachability stops being symmetric, which is why directed graphs need SCCs rather than connected components.",
               nodeMarks: marks(unreachable, .idle), groups: Dictionary(uniqueKeysWithValues: directedFromC.map { ($0, 1) }), hideWeights: true),
        GFrame(status: "Unweighted, A to E: BFS returns \(hopPath.joined(separator: "→")) — \(hopPath.count - 1) hops, and no shorter walk exists. Fewest edges is the only question an unweighted graph can answer.",
               nodeMarks: marks(hopPath, .done), edgeMarks: edgeMarks(edgeIndicesOf(hopPath), .active), hideWeights: true),
        GFrame(status: "Put the weights back and that answer is wrong. \(hopPath.joined(separator: "→")) costs \(hopCost); \(costPath.joined(separator: "→")) costs \(cost) over \(costPath.count - 1) hops. More edges, less distance — a weighted graph makes hop count and cost different questions, which is exactly the gap Dijkstra fills and BFS cannot.",
               nodeMarks: marks(costPath, .updated),
               edgeMarks: edgeMarks(costEdges, .accepted) + edgeMarks(edgeIndicesOf(hopPath).subtracting(costEdges), .rejected)),
        GFrame(status: "Cyclic: D→E→F→D closes a loop. Kahn's algorithm places \(partial.isEmpty ? "nothing" : partial.joined(separator: ", ")) and then stalls — \(stuck.joined(separator: ", ")) never reach in-degree zero because each waits on another member of the cycle. There is no valid order, so any \"process dependencies first\" algorithm is undefined here.",
               nodeMarks: marks(cycle, .active), edgeMarks: edgeMarks(edgeIndicesOf(cycle), .rejected), hideWeights: true),
        GFrame(status: "Drop the single edge F→D and it is a DAG. Now every vertex places — \(acyclic.joined(separator: " → ")) — and with a topological order come longest-path in linear time, dynamic programming over vertices, and build/scheduling order. One edge is the whole difference.",
               nodeMarks: marks(acyclic, .done), badges: positionBadges(acyclic), hideWeights: true, hiddenEdges: [backEdgeIndex]),
        GFrame(status: "Last variant, and it is about storage rather than meaning: \(ec) edges out of a possible \(v * (v - 1)) is sparse. An adjacency matrix costs \(v * v) cells whatever you do; adjacency lists cost \(ec) entries. Matrices win on \"is A→B an edge\" in O(1); lists win on iterating neighbours and on memory, which is why almost every real graph library defaults to lists.",
               groups: Dictionary(uniqueKeysWithValues: def.ids.map { ($0, 0) }), hideWeights: true),
    ]
}

// MARK: - Bellman–Ford

// MARK: - Floyd–Warshall

private func floydWarshallFrames() -> [GFrame] {
    let def = allPairsGraph
    let ids = def.ids
    let count = ids.count
    var dm = (0..<count).map { i in (0..<count).map { j in i == j ? 0 : graphInf } }
    for edge in def.edges {
        let i = ids.firstIndex(of: edge.from)!, j = ids.firstIndex(of: edge.to)!
        dm[i][j] = min(dm[i][j], edge.weight ?? 0)
    }
    var frames = [GFrame(status: "The matrix starts as the edge list itself: dist[i][j] is the direct edge, 0 on the diagonal, ∞ where no edge exists.", matrix: dm)]
    for k in 0..<count {
        frames.append(GFrame(status: "Round \(k + 1): allow \(ids[k]) as an intermediate node. Every pair now asks \"is going through \(ids[k]) cheaper?\"",
                             nodeMarks: [ids[k]: .active], matrix: dm, matrixVia: k))
        for i in 0..<count {
            for j in 0..<count where i != j && i != k && j != k {
                let through = dm[i][k] + dm[k][j]
                if dm[i][k] < graphInf && dm[k][j] < graphInf && through < dm[i][j] {
                    let previous = dm[i][j]
                    dm[i][j] = through
                    frames.append(GFrame(status: "\(ids[i])→\(ids[j]) via \(ids[k]) costs \(dm[i][k]) + \(dm[k][j]) = \(through), better than \(distText(previous)).",
                                         nodeMarks: [ids[k]: .active, ids[i]: .frontier, ids[j]: .updated], matrix: dm, matrixFocus: (i, j), matrixVia: k))
                }
            }
        }
    }
    frames.append(GFrame(status: "After all \(count) rounds every entry is a true shortest path — \(count * count) answers from three nested loops, no per-source reruns.",
                         nodeMarks: marks(ids, .done), matrix: dm))
    return frames
}

// MARK: - Kruskal / Prim

/// Component colours only make sense once a component has more than one node.
private func groupsFrom(_ def: GraphDef, _ root: (String) -> String) -> [String: Int] {
    var sizes: [String: Int] = [:]
    for id in def.ids { sizes[root(id), default: 0] += 1 }
    var order: [String] = []
    for id in def.ids where !order.contains(root(id)) { order.append(root(id)) }
    return Dictionary(uniqueKeysWithValues: def.ids.filter { sizes[root($0)]! > 1 }.map { ($0, order.firstIndex(of: root($0))!) })
}

/// The tree edges already chosen, walked breadth-first: names the route that makes a skipped edge a
/// cycle ("A and B are already joined through D and E").
private func treePath(_ def: GraphDef, _ state: [Int: EdgeMark], _ from: String, _ to: String) -> [String] {
    var prev = [from: from]
    var queue = [from]
    while !queue.isEmpty {
        let cur = queue.removeFirst()
        for (i, e) in def.edges.enumerated() where state[i] == .accepted {
            let next = e.from == cur ? e.to : e.to == cur ? e.from : nil
            if let next, prev[next] == nil { prev[next] = cur; queue.append(next) }
        }
    }
    guard prev[to] != nil else { return [] }
    var path = [to]
    while path.last! != from { path.append(prev[path.last!]!) }
    return path.reversed()
}

private func joinNames(_ names: [String]) -> String {
    names.count <= 1 ? (names.first ?? "") : names.dropLast().joined(separator: ", ") + " and " + names.last!
}

private func kruskalFrames() -> [GFrame] {
    let def = mstGraph
    var parent = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, $0) })
    func find(_ id: String) -> String {
        var node = id
        while parent[node]! != node { node = parent[node]! }
        return node
    }
    var edgeState: [Int: EdgeMark] = [:]
    var total = 0
    var accepted = 0
    let needed = def.ids.count - 1
    /// The last skip, so the next take can say why an equal-weight edge before it was passed over.
    var lastSkip: GEdge?
    let sorted = def.edges.enumerated().map { ($0.offset, $0.element) }.sortedBy { $0.1.weight ?? 0 }
    var first = GFrame(status: "Sort every edge by weight: \(sorted.map { "\($0.1.from)\($0.1.to)(\($0.1.weight!))" }.joined(separator: ", ")). Kruskal then walks that list once.")
    first.headline = [GraphSpan(text: "Sort the "), GraphSpan(text: "\(def.edges.count) edges", tone: .active), GraphSpan(text: " by weight.")]
    first.body = "Kruskal walks this list once, lightest first, and keeps an edge only if it joins two different components. The node colours are those components — the union-find sets."
    var frames = [first]
    for (index, edge) in sorted {
        let rootFrom = find(edge.from), rootTo = find(edge.to)
        let name = "\(edge.from)–\(edge.to)"
        let w = edge.weight!
        if rootFrom == rootTo {
            let via = Array(treePath(def, edgeState, edge.from, edge.to).dropFirst().dropLast())
            edgeState[index] = .rejected
            var f = GFrame(status: "\(name) (w = \(w)) is skipped: both ends already sit in the same component, so this edge would close a cycle.",
                           nodeMarks: [edge.from: .active, edge.to: .active], groups: groupsFrom(def, find), edgeMarks: edgeState, focusEdge: index)
            f.headline = [GraphSpan(text: "Skip "), GraphSpan(text: name, tone: .rejected),
                          GraphSpan(text: " (\(w)). \(edge.from) and \(edge.to) are in the same set, so it would close a cycle.")]
            f.body = "\(edge.from) and \(edge.to) are already joined" + (via.isEmpty ? "" : " through \(joinNames(via))") + ". Both finds return the same root, and that one comparison is the whole cycle test."
            frames.append(f)
            lastSkip = edge
        } else {
            parent[rootFrom] = rootTo
            edgeState[index] = .accepted
            total += w
            accepted += 1
            let tally = "Tree weight is now \(total), with \(accepted) of \(needed) edges."
            var f = GFrame(status: "Take \(name) (w = \(w)): the ends were in different components, so union them. Tree weight \(total), \(accepted) of \(needed) edges.",
                           nodeMarks: [edge.from: .active, edge.to: .active], groups: groupsFrom(def, find), edgeMarks: edgeState, focusEdge: index)
            f.headline = [GraphSpan(text: "Take "), GraphSpan(text: name, tone: .active),
                          GraphSpan(text: " (\(w)). \(edge.from) and \(edge.to) are in different sets, so union them.")]
            if let skipped = lastSkip, skipped.weight == edge.weight {
                f.body = "\(skipped.from)–\(skipped.to) had the same weight but was already connected, so it was skipped. " + tally
            } else {
                f.body = tally
            }
            frames.append(f)
            lastSkip = nil
        }
        if accepted == needed { break }
    }
    var last = GFrame(status: "\(needed) edges accepted and every node is in one component: minimum spanning tree, total weight \(total).",
                      nodeMarks: marks(def.ids, .done), edgeMarks: edgeState)
    last.headline = [GraphSpan(text: "Minimum spanning tree", tone: .done), GraphSpan(text: ": \(needed) edges, total weight \(total).")]
    last.body = "Every node is in one set, so any further edge would close a cycle and the walk stops early. Sorting dominates: O(E log E)."
    frames.append(last)
    return frames
}

private func primFrames() -> [GFrame] {
    let def = mstGraph
    let start = "A"
    var inTree: Set<String> = [start]
    var edgeState: [Int: EdgeMark] = [:]
    var total = 0
    var seed = GFrame(status: "Prim grows one tree instead of collecting edges globally. Seed it with \(start).", nodeMarks: [start: .done])
    seed.headline = [GraphSpan(text: "Seed the tree with "), GraphSpan(text: start, tone: .active), GraphSpan(text: ".")]
    seed.body = "Prim never sorts the edge list. It grows one tree, and every step takes the cheapest edge with exactly one end inside it."
    var frames = [seed]
    while inTree.count < def.ids.count {
        let crossing = def.edges.enumerated().filter { inTree.contains($0.element.from) != inTree.contains($0.element.to) }
        var cut = GFrame(status: "Edges crossing the cut: \(crossing.map { "\($0.element.from)–\($0.element.to)(\($0.element.weight!))" }.joined(separator: ", ")).",
                         nodeMarks: marks(inTree, .done) + marks(crossing.flatMap { [$0.element.from, $0.element.to] }.filter { !inTree.contains($0) }, .frontier),
                         edgeMarks: edgeState + edgeMarks(crossing.map(\.offset), .active))
        cut.headline = [GraphSpan(text: "The cut has "), GraphSpan(text: "\(crossing.count) crossing \(crossing.count == 1 ? "edge" : "edges")", tone: .active), GraphSpan(text: ".")]
        cut.body = "Crossing: \(crossing.map { "\($0.element.from)–\($0.element.to) (\($0.element.weight!))" }.joined(separator: ", ")). Each has one end in the tree {\(inTree.sorted().joined(separator: ", "))} and one outside it."
        frames.append(cut)
        guard let pick = crossing.min(by: { ($0.element.weight ?? 0) < ($1.element.weight ?? 0) }) else { break }
        let edge = pick.element
        let added = inTree.contains(edge.from) ? edge.to : edge.from
        inTree.insert(added)
        edgeState[pick.offset] = .accepted
        total += edge.weight ?? 0
        var take = GFrame(status: "Cheapest crossing edge is \(edge.from)–\(edge.to) (w = \(edge.weight!)) — pull \(added) into the tree. Weight so far \(total).",
                          nodeMarks: marks(inTree, .done) + [added: .updated], edgeMarks: edgeState, focusEdge: pick.offset)
        take.headline = [GraphSpan(text: "Take "), GraphSpan(text: "\(edge.from)–\(edge.to)", tone: .active), GraphSpan(text: " (\(edge.weight!)), the cheapest edge leaving the tree.")]
        take.body = "\(added) joins the tree. Weight so far \(total), with \(inTree.count) of \(def.ids.count) nodes inside."
        frames.append(take)
    }
    var done = GFrame(status: "All \(def.ids.count) nodes absorbed, total weight \(total) — the same tree Kruskal builds, reached by growing a cut instead of sorting edges.",
                      nodeMarks: marks(def.ids, .done), edgeMarks: edgeState)
    done.headline = [GraphSpan(text: "Minimum spanning tree", tone: .done), GraphSpan(text: ": all \(def.ids.count) nodes, total weight \(total).")]
    done.body = "The same tree Kruskal builds, reached by growing a cut instead of sorting edges. With a heap of crossing edges it runs in O(E log V)."
    frames.append(done)
    return frames
}

// MARK: - Tarjan / Kosaraju

// MARK: - DAG DP / shortest-path choice / colouring (interview patterns)

private func outgoingEdges(_ def: GraphDef, _ u: String) -> [(Int, GEdge)] {
    def.edges.enumerated().filter { $0.element.from == u }.map { ($0.offset, $0.element) }
}

private func dagDpFrames() -> [GFrame] {
    let def = dagDpGraph
    var inDegree = Dictionary(uniqueKeysWithValues: def.ids.map { id in (id, def.edges.filter { $0.to == id }.count) })
    var best = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, 0) })
    var done: [String] = []
    func badges() -> [String: String] { best.mapValues { "\($0)" } }
    var frames = [GFrame(status: "Longest path from any source. On a general graph this is NP-hard; on a DAG it is a scan, because a topological order guarantees every predecessor of a node is finished before the node is read.",
                         badges: badges())]
    var ready = def.ids.filter { inDegree[$0]! == 0 }
    frames.append(GFrame(status: "A and B have no incoming edges, so their best-path values are already final at 0. Everything else starts at 0 and can only grow.",
                         nodeMarks: marks(ready, .frontier), badges: badges()))
    while !ready.isEmpty {
        let u = ready.removeFirst()
        done.append(u)
        let outgoing = outgoingEdges(def, u)
        var improved: [String] = []
        for (_, edge) in outgoing {
            let candidate = best[u]! + (edge.weight ?? 0)
            if candidate > best[edge.to]! {
                best[edge.to] = candidate
                improved.append(edge.to)
            }
            inDegree[edge.to]! -= 1
            if inDegree[edge.to]! == 0 { ready.append(edge.to) }
        }
        frames.append(GFrame(status: "\(u) is final at \(best[u]!) — no unprocessed node can still reach it. Relaxing its edges: " + (improved.isEmpty ? "nothing improved." : improved.map { "\($0) → \(best[$0]!)" }.joined(separator: ", ")) + ". Each edge is relaxed exactly once, so the whole thing is O(V + E).",
                             nodeMarks: marks(done, .done) + marks(ready, .frontier) + marks(improved, .updated) + [u: .active],
                             badges: badges(), edgeMarks: edgeMarks(outgoing.map(\.0), .active)))
    }
    let winner = def.ids[argmaxFirst(def.ids.map { best[$0]! })]
    frames.append(GFrame(status: "Longest path ends at \(winner) with weight \(best[winner]!). Flip the comparison to get the shortest path, or count instead of maximise to get path counts — the traversal never changes, only the combine step does.",
                         nodeMarks: marks(def.ids, .done) + [winner: .updated], badges: badges(), edgeMarks: edgeMarks(def.edges.indices, .accepted)))
    return frames
}

private func shortestPathChoiceFrames() -> [GFrame] {
    let def = shortestPathChoiceGraph
    var bfsDist = ["S": 0]
    var bfsOrder = ["S"]
    var queue = ["S"]
    var frames = [GFrame(status: "First question to ask: what do the edges cost? If every edge is 1, a queue is already a priority queue — BFS settles nodes in distance order for free.",
                         badges: ["S": "0"], hideWeights: true)]
    while !queue.isEmpty {
        let u = queue.removeFirst()
        let outgoing = outgoingEdges(def, u)
        var discovered: [String] = []
        for (_, e) in outgoing where bfsDist[e.to] == nil {
            bfsDist[e.to] = bfsDist[u]! + 1
            bfsOrder.append(e.to)
            queue.append(e.to)
            discovered.append(e.to)
        }
        frames.append(GFrame(status: "BFS from \(u) (hop \(bfsDist[u]!)): " + (discovered.isEmpty ? "everything reachable is already labelled." : "\(discovered.joined(separator: ", ")) reached in \(bfsDist[u]! + 1) hop(s), and that is final — a later path can only be longer."),
                             nodeMarks: marks(bfsOrder, .frontier) + [u: .active], badges: bfsDist.mapValues { "\($0)" },
                             edgeMarks: edgeMarks(outgoing.map(\.0), .active), hideWeights: true))
    }
    var dist = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, $0 == "S" ? 0 : graphInf) })
    var settled: [String] = []
    func badges() -> [String: String] { dist.mapValues(distText) }
    frames.append(GFrame(status: "Now the real weights. BFS's answer (S→A→T, 2 hops) costs \(1 + 9) — while S→B→C→T takes three hops but costs \(4 + 1 + 1). Hop count and cost disagree, so the queue has to become a priority queue.",
                         badges: badges()))
    while settled.count < def.ids.count {
        let unsettled = def.ids.filter { !settled.contains($0) }
        guard let u = unsettled.min(by: { dist[$0]! < dist[$1]! }), dist[u]! < graphInf else { break }
        settled.append(u)
        let outgoing = outgoingEdges(def, u)
        var improved: [String] = []
        for (_, e) in outgoing {
            let candidate = dist[u]! + (e.weight ?? 0)
            if candidate < dist[e.to]! {
                dist[e.to] = candidate
                improved.append(e.to)
            }
        }
        frames.append(GFrame(status: "Pop the cheapest unsettled node: \(u) at \(dist[u]!). Dijkstra declares it final here — no unsettled node is closer, and every edge is non-negative, so no detour can undercut it. " + (improved.isEmpty ? "No neighbour improved." : "Improved: \(improved.map { "\($0) → \(dist[$0]!)" }.joined(separator: ", ")))."),
                             nodeMarks: marks(settled, .done) + marks(improved, .updated) + [u: .active], badges: badges(), edgeMarks: edgeMarks(outgoing.map(\.0), .active)))
    }
    frames.append(GFrame(status: "Dijkstra settles T at \(dist["T"]!). Now change A→T from 9 to −3: S→A→T would cost \(1 - 3), but Dijkstra already finalised T and never looks again. That finality is exactly what a negative edge invalidates — Bellman-Ford drops it, relaxing every edge V−1 times instead, at O(V·E).",
                         nodeMarks: marks(def.ids, .done) + ["T": .updated], badges: badges(), edgeMarks: [4: .rejected]))
    return frames
}

private func graphColouringFrames() -> [GFrame] {
    let def = colouringGraph
    let adjacency = Dictionary(uniqueKeysWithValues: def.ids.map { id in
        (id, def.edges.filter { $0.from == id || $0.to == id }.map { $0.from == id ? $0.to : $0.from })
    })
    var colour: [String: Int] = [:]
    let names = ["c1", "c2", "c3", "c4"]
    var frames = [GFrame(status: "Colour every node so no edge joins two of the same colour, using as few colours as possible. Optimal colouring is NP-hard; greedy in a fixed order is the cheap answer, and its cost is bounded by max degree + 1.")]
    for id in def.ids {
        let used = Set(adjacency[id]!.compactMap { colour[$0] })
        let pick = (0...used.count).first { !used.contains($0) }!
        colour[id] = pick
        frames.append(GFrame(status: "\(id): neighbours already hold " + (used.isEmpty ? "nothing" : used.sorted().map { names[$0] }.joined(separator: ", ")) + ", so take the smallest colour not among them — \(names[pick]). Greedy never backtracks, which is why the node order changes the result.",
                             nodeMarks: [id: .active], badges: colour.mapValues { names[$0] }, groups: colour))
    }
    frames.append(GFrame(status: "\(Set(colour.values).count) colours in this order. The triangle A–B–C alone forces three: three mutually adjacent nodes cannot share any colour, so no ordering does better here.",
                         nodeMarks: marks(def.ids, .done), badges: colour.mapValues { names[$0] }, groups: colour))
    var side = ["D": 0]
    var queue = ["D"]
    let bipartitePart: Set<String> = ["C", "D", "E", "F"]
    while !queue.isEmpty {
        let u = queue.removeFirst()
        for v in adjacency[u]! where bipartitePart.contains(v) && side[v] == nil {
            side[v] = 1 - side[u]!
            queue.append(v)
        }
    }
    frames.append(GFrame(status: "\"Is it bipartite?\" is the two-colour case, and BFS answers it: alternate colours across every edge and see whether anything clashes. On the C–D–F–E square it succeeds — \(side.keys.sorted().map { "\($0)=\(names[side[$0]!])" }.joined(separator: ", ")) — so that part is bipartite.",
                         nodeMarks: marks(side.keys, .done), badges: side.mapValues { names[$0] }, groups: side))
    frames.append(GFrame(status: "Run the same alternation into the triangle and it fails: A and B are adjacent yet both sit one step from C, so they demand the same colour and share an edge. An odd cycle is precisely what makes a graph non-bipartite — the conflict edge, not a colour count, is the proof to state in an interview.",
                         nodeMarks: ["A": .active, "B": .active, "C": .done], badges: ["A": "c1", "B": "c1", "C": "c2"], edgeMarks: [0: .rejected, 1: .accepted, 2: .accepted]))
    return frames
}

// MARK: - Topological sort / max flow / articulation points

// MARK: - Bayesian network

private func bayesNetworkFrames() -> [GFrame] {
    [
        GFrame(status: "Four binary variables. Naive Bayes would assume all of them independent given the class; a Bayesian network instead states exactly which dependencies exist, as a directed acyclic graph.", hideWeights: true),
        GFrame(status: "Each node carries P(node | its parents). Cloudy has no parents so it needs 1 number; Sprinkler and Rain need 2 each; WetGrass has two parents so it needs 4. That is 9 parameters against the 2⁴ − 1 = 15 a full joint table would need.",
               badges: ["Cloudy": "1", "Sprinkler": "2", "Rain": "2", "WetGrass": "4"], hideWeights: true),
        GFrame(status: "The saving comes entirely from the missing edges. There is no arrow from Sprinkler to Rain, which asserts they are conditionally independent given Cloudy — a claim about the world that the graph makes explicit and testable.",
               nodeMarks: ["Sprinkler": .active, "Rain": .active, "Cloudy": .done], edgeMarks: [0: .accepted, 1: .accepted], hideWeights: true),
        GFrame(status: "Observe Cloudy and the path between Sprinkler and Rain is blocked — learning it is sunny tells you about the sprinkler, but once you already know the weather, the sprinkler tells you nothing more about rain. This is d-separation, and it is what makes inference tractable.",
               nodeMarks: ["Cloudy": .done], groups: ["Sprinkler": 0, "Rain": 1], hideWeights: true),
        GFrame(status: "Now the collider. Sprinkler and Rain are marginally independent — neither causes the other. But observe WetGrass and they become dependent: the grass is wet, so if the sprinkler was off, rain becomes far more likely.",
               nodeMarks: ["WetGrass": .done], groups: ["Sprinkler": 0, "Rain": 0], edgeMarks: [2: .active, 3: .active], hideWeights: true),
        GFrame(status: "That is explaining away, and it is the one pattern where conditioning creates dependence rather than removing it. It is also why you cannot read independence off the arrows alone — the direction of the arrows into a node matters.",
               nodeMarks: ["WetGrass": .done, "Sprinkler": .active], groups: ["Rain": 1], hideWeights: true),
        GFrame(status: "The joint factorizes along the arrows: P(C,S,R,W) = P(C)·P(S|C)·P(R|C)·P(W|S,R). Exact inference by variable elimination is efficient on a graph this sparse; on densely connected graphs it becomes intractable, which is where MCMC comes in.",
               nodeMarks: marks(bayesNetGraph.ids, .done), hideWeights: true),
    ]
}

// MARK: - Eulerian / Hamiltonian

// MARK: - Course schedule / union-find (interview patterns)

private func courseScheduleFrames() -> [GFrame] {
    let def = courseGraph
    let cycleEdge = def.edges.count - 1
    var frames: [GFrame] = []

    func run(_ active: [Int], _ hidden: Set<Int>, _ opening: String) -> [String] {
        let edges = active.map { ($0, def.edges[$0]) }
        var inDeg = Dictionary(uniqueKeysWithValues: def.ids.map { id in (id, edges.filter { $0.1.to == id }.count) })
        var done: Set<String> = []
        var emitted: [String] = []
        func badges() -> [String: String] { Dictionary(uniqueKeysWithValues: inDeg.map { ($0.key, done.contains($0.key) ? "✓" : "\($0.value)") }) }
        frames.append(GFrame(status: opening, badges: badges(), hiddenEdges: hidden))
        var ready = def.ids.filter { inDeg[$0]! == 0 }
        frames.append(GFrame(status: "Ready now: \(ready.isEmpty ? "nothing — every course is waiting on another" : ready.joined(separator: ", ")). In-degree is the count of prerequisites still unmet.",
                             nodeMarks: marks(ready, .frontier), badges: badges(), hiddenEdges: hidden))
        while !ready.isEmpty {
            let u = ready.removeFirst()
            emitted.append(u)
            done.insert(u)
            let outgoing = edges.filter { $0.1.from == u }
            frames.append(GFrame(status: "Take \(u) (\(emitted.joined(separator: " → "))). It unlocks \(outgoing.count) course(s).",
                                 nodeMarks: marks(done, .done) + marks(ready, .frontier) + [u: .active], badges: badges(),
                                 edgeMarks: edgeMarks(outgoing.map(\.0), .active), hiddenEdges: hidden))
            var freed: [String] = []
            for (_, edge) in outgoing {
                inDeg[edge.to]! -= 1
                if inDeg[edge.to]! == 0 {
                    ready.append(edge.to)
                    freed.append(edge.to)
                }
            }
            if !freed.isEmpty {
                frames.append(GFrame(status: "\(freed.joined(separator: " and ")) drop\(freed.count == 1 ? "s" : "") to in-degree 0 — every prerequisite met, so \(freed.count == 1 ? "it joins" : "they join") the queue.",
                                     nodeMarks: marks(done, .done) + marks(ready, .frontier) + marks(freed, .updated), badges: badges(), hiddenEdges: hidden))
            }
        }
        return emitted
    }

    let order = run(def.edges.indices.filter { $0 != cycleEdge }, [cycleEdge],
                    "Six courses, each arrow meaning \"must come first\". The question is whether a legal order exists at all, and Kahn's algorithm answers both halves at once.")
    frames.append(GFrame(status: "\(order.count) of \(def.ids.count) courses emitted, so the order \(order.joined(separator: " → ")) is valid. It is not the only one — 201 and 210 were ready simultaneously.",
                         nodeMarks: marks(def.ids, .done), badges: Dictionary(uniqueKeysWithValues: def.ids.map { ($0, "✓") }), hiddenEdges: [cycleEdge]))
    let cyclic = run(Array(def.edges.indices), [],
                     "Now add one prerequisite: 401 → 201, so 201 needs a course that needs 201. Nothing about the algorithm changes — only the in-degrees do.")
    frames.append(GFrame(status: "The queue drained after \(cyclic.count) of \(def.ids.count) courses. The \(def.ids.count - cyclic.count) left all still have an unmet prerequisite, which can only happen inside a cycle — that count is the whole cycle check.",
                         nodeMarks: Dictionary(uniqueKeysWithValues: def.ids.map { ($0, cyclic.contains($0) ? NodeMark.done : .active) }),
                         badges: Dictionary(uniqueKeysWithValues: def.ids.map { ($0, cyclic.contains($0) ? "✓" : "stuck") }), edgeMarks: [cycleEdge: .rejected]))
    return frames
}

private func unionFindFrames() -> [GFrame] {
    let def = unionFindGraph
    var parent = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, $0) })
    var components = def.ids.count
    func find(_ x: String) -> String {
        var node = x
        while parent[node]! != node {
            parent[node] = parent[parent[node]!]! // path compression
            node = parent[node]!
        }
        return node
    }
    // Group index per node, by first appearance so a colour never jumps between frames.
    func groups() -> [String: Int] {
        var order: [String: Int] = [:]
        var out: [String: Int] = [:]
        for id in def.ids {
            let root = find(id)
            if order[root] == nil { order[root] = order.count % groupColors.count }
            out[id] = order[root]!
        }
        return out
    }
    func badges() -> [String: String] { Dictionary(uniqueKeysWithValues: def.ids.map { ($0, find($0)) }) }
    var frames = [GFrame(status: "Six nodes, no edges yet: \(def.ids.count) components, each its own root. The edges below arrive one at a time, which is what rules out re-running BFS after every one.",
                         badges: badges(), groups: groups(), undirected: true, hiddenEdges: Set(def.edges.indices))]
    for (index, edge) in def.edges.enumerated() {
        let ra = find(edge.from), rb = find(edge.to)
        let merged = ra != rb
        if merged {
            parent[rb] = ra
            components -= 1
        }
        frames.append(GFrame(status: merged
                             ? "Edge \(edge.from)–\(edge.to): roots \(ra) and \(rb) differ, so attach \(rb) under \(ra). Components \(components + 1) → \(components)."
                             : "Edge \(edge.from)–\(edge.to): both already find their way to \(ra). Merging would change nothing — in an undirected graph that is exactly a cycle.",
                             nodeMarks: [edge.from: .active, edge.to: .active], badges: badges(), groups: groups(),
                             edgeMarks: [index: merged ? .accepted : .rejected], undirected: true, hiddenEdges: Set((index + 1)..<def.edges.count)))
    }
    frames.append(GFrame(status: "\(components) component(s) left after \(def.edges.count) edges, and the one rejected edge is the graph's cycle. Every find ran in near-constant time because path compression flattened the trees as it went — α(n), under 5 for any n you will meet.",
                         badges: badges(), groups: groups(), undirected: true))
    return frames
}

// MARK: - GCN / GAT on two triangles bridged by one edge (SpecializedMath.kt's SmallGraph)

private func gcnFrames() -> [GFrame] {
    let trace = GcnLab.layers(GcnLab.initialFeatures(), depth: 100)
    return [0, 1, 2, 5, 20, 100].map { depth in
        let features = trace[depth]
        let badges = Dictionary(uniqueKeysWithValues: (0..<SmallGraph.nodes).map { ("\($0)", fx(features[$0][0])) })
        let groups = Dictionary(uniqueKeysWithValues: (0..<SmallGraph.nodes).map { ("\($0)", $0 < 3 ? 0 : 1) })
        let s = GcnLab.separation(features)
        let status: String
        switch depth {
        case 0: status = "Two triangles, one bridge edge (2–3). Badges are each node's first feature value; colour is the true triangle. Cross-triangle distance is \(fx(s))x the within-triangle distance."
        case 1: status = "One layer of neighbor-averaging already blurs the split: the ratio drops to \(fx(s))."
        case 100: status = "By depth 100 the ratio has converged to \(fx(s, 3)) -- exactly two-thirds, not one. Nodes 2 and 3 (the bridge, degree 4 on both sides) are now nearly identical despite sitting in different triangles."
        default: status = "Depth \(depth): ratio now \(fx(s)). The bridge's weak connectivity slows this convergence down."
        }
        return GFrame(status: status, badges: badges, groups: groups, undirected: true, hideWeights: true)
    }
}

private func gatFrames() -> [GFrame] {
    // Node 2's neighbors are 0, 1 and 3 -- edge indices 2, 1 and 6.
    let scored = edgeMarks([1, 2, 6], .active)
    func badges(_ w: [Int: Double]) -> [String: String] { Dictionary(uniqueKeysWithValues: w.map { ("\($0.key)", fx($0.value, 3)) }) }
    return [
        GFrame(status: "Node 2's neighborhood: 0, 1 and 3. GCN's weight is 1/√(deg·deg) -- fixed by the graph alone, computed once, and blind to whatever the features say.",
               nodeMarks: ["2": .active], badges: badges(GatLab.gcnWeights()), edgeMarks: scored, undirected: true, hideWeights: true),
        GFrame(status: "GAT's attention on the identical neighborhood, computed from the real feature values: nodes 0 and 1 (feature-similar to node 2) take 92% of the mass between them; node 3 (very different features) gets 0.083.",
               nodeMarks: ["2": .active], badges: badges(GatLab.attentionWeights(GatLab.baseFeatures)), edgeMarks: scored, undirected: true, hideWeights: true),
        GFrame(status: "Move node 3's features to match node 2's exactly, and recompute both weightings. GAT's weight on that edge roughly quadruples (0.083 -> 0.331); GCN's weight on the same edge does not move at all -- it never read the features to begin with.",
               nodeMarks: ["2": .active, "3": .updated], badges: badges(GatLab.attentionWeights(GatLab.perturbedFeatures)), edgeMarks: scored, undirected: true, hideWeights: true),
        GFrame(status: "GCN's weight on that same edge, recomputed after the identical perturbation: 0.250, unchanged to the last decimal. Degree-normalization has no feature input to react to.",
               nodeMarks: ["2": .active, "3": .updated], badges: badges(GatLab.gcnWeights()), edgeMarks: scored, undirected: true, hideWeights: true),
    ]
}

// MARK: - Config

private func legend(_ items: [(NodeMark, String)]) -> [(Color, String)] { items.map { (nodeColor($0.0), $0.1) } }

private let shortestPathLegend = legend([(.active, "Relaxing from"), (.updated, "Improved"), (.done, "Final")])
private let kruskalLegend: [(Color, String)] = [(edgeColor(.active), "Current"), (edgeColor(.accepted), "In tree"), (edgeColor(.rejected), "Skipped: cycle")]
private let mstLegend = kruskalLegend
/// Prim never rejects an edge — a crossing edge that loses simply stays a candidate.
private let primLegend: [(Color, String)] = [(edgeColor(.active), "Crossing the cut"), (edgeColor(.accepted), "In tree")]
private let sccLegend: [(Color, String)] = [(nodeColor(.active), "Current"), (nodeColor(.frontier), "On stack"), (groupColors[0], "Component")]

// MARK: - Story storyboards

private let hamiltonStoryGraph = GraphDef(
    nodes: [n("A", 0.04, 0.5), n("B", 0.35, 0.06), n("C", 0.35, 0.94), n("D", 0.65, 0.06), n("E", 0.65, 0.94), n("F", 0.96, 0.5)],
    edges: [e("A", "B"), e("A", "C"), e("B", "C"), e("B", "D"), e("C", "E"), e("D", "E"), e("D", "F"), e("E", "F")])

/// Backtracking from A, neighbours in alphabetical order. A full path that cannot close is one step,
/// and consecutive backtracks collapse into one.
private func hamiltonStoryFrames(cycle: Bool) -> [GFrame] {
    let def = hamiltonStoryGraph
    let count = def.nodes.count
    var adj: [String: [String]] = [:]
    for id in def.ids { adj[id] = def.edges.compactMap { $0.from == id ? $0.to : $0.to == id ? $0.from : nil }.sorted() }
    var frames: [GFrame] = []
    let goal = cycle ? "Hamiltonian cycle" : "Hamiltonian path"
    let legend: [(GS, String)] = [(.active, "Extending"), (.path, "On path"), (.warn, "No closing edge"), (.done, goal)]

    func story(_ path: [String], _ headline: String, _ body: String, _ chips: [GChip], done: Bool = false, missing: [(String, String)] = []) -> GraphStory {
        let last = path.last!
        func tone(_ id: String) -> GS { done ? .done : id == last ? .active : .path }
        var edges: [Int: GS] = [:]
        for i in 0..<(path.count - 1) { edges[def.edgeIndex(path[i], path[i + 1])] = done ? .done : i == path.count - 2 ? .active : .path }
        if done && cycle { edges[def.edgeIndex(last, path[0])] = .done }
        var badges: [String: (String, GS)] = [:]
        for (i, id) in path.enumerated() { badges[id] = ("\(i + 1)", id == last && !done ? .active : .idle) }
        var s = GraphStory(title: cycle ? "HAMILTONIAN CYCLE" : "HAMILTONIAN PATH", note: "", headline: headline, body: body)
        s.nodes = Dictionary(uniqueKeysWithValues: path.map { ($0, tone($0)) })
        s.badges = badges
        s.edges = edges
        s.missing = missing
        s.rows = [[GCellGroup(label: "PATH", cells: path.map { GCell(text: $0, tone: tone($0)) } + Array(repeating: GCell(text: "", tone: .empty), count: count - path.count),
                              note: "\(path.count) of \(count) vertices")]]
        s.chips = chips
        s.emphasis = done ? .done : .active
        s.legend = legend
        return s
    }

    var path = ["A"]
    frames.append(storyFrame(story(path, "Start at {A}.",
        cycle ? "A Hamiltonian cycle visits every vertex once and comes back to A. The search grows a path one vertex at a time and backs up at dead ends."
              : "A Hamiltonian path visits every vertex exactly once. The search grows a path one vertex at a time and backs up at dead ends.",
        [GChip(key: "path", value: "1 of \(count)")])))
    var pendingBack = 0
    var backFrom = ""
    var afterNoClose = false

    func flushBack() {
        guard pendingBack > 0 else { return }
        let at = path.last!
        frames.append(storyFrame(story(path, "Dead end, so back up to {\(at)}.",
            (afterNoClose ? "The full path could not close" : "\(backFrom) has no neighbour left that is off the path") +
                ". The search undoes \(pendingBack) step\(pendingBack == 1 ? "" : "s") and tries \(at)'s next neighbour.",
            [GChip(key: "backtracked", value: "\(pendingBack)")])))
        pendingBack = 0
        afterNoClose = false
    }

    func dfs() -> Bool {
        if path.count == count {
            let last = path.last!
            if !cycle {
                frames.append(storyFrame(story(path, "Extend to {m:\(last)}. All \(count) vertices are on the path.",
                    "That is a Hamiltonian path: \(path.joined(separator: "→")). No vertex repeats.",
                    [GChip(key: "path", value: "\(count) of \(count)")], done: true)))
                return true
            }
            if adj[last]!.contains("A") {
                frames.append(storyFrame(story(path, "\(last) connects back to {m:A}, so the cycle closes.",
                    "\(path.joined(separator: "→"))→A visits every vertex once and returns home.",
                    [GChip(key: "closing edge", value: "\(last)–A", tone: .done)], done: true)))
                return true
            }
            frames.removeLast()
            frames.append(storyFrame(story(path, "Extend to {\(last)}. All \(count) vertices are on the path.",
                "There is no edge from \(last) back to A, so this is a path but not a cycle. Next, the search backtracks.",
                [GChip(key: "closing edge", value: "\(last)–A missing", tone: .warn)], missing: [(last, "A")])))
            afterNoClose = true
            return false
        }
        for v in adj[path.last!]! where !path.contains(v) {
            flushBack()
            let from = path.last!
            path.append(v)
            frames.append(storyFrame(story(path, "Extend to {\(v)}.", "\(v) is the first neighbour of \(from) that is not on the path yet.",
                                           [GChip(key: "path", value: "\(path.count) of \(count)")])))
            if dfs() { return true }
            if pendingBack == 0 { backFrom = path.last! }
            path.removeLast()
            pendingBack += 1
        }
        return false
    }
    _ = dfs()
    return frames
}

private let eulerCircuitGraph = GraphDef(
    nodes: [n("A", 0.12, 0.08), n("B", 0.12, 0.92), n("C", 0.5, 0.5), n("D", 0.88, 0.08), n("E", 0.88, 0.92)],
    edges: [e("A", "B"), e("A", "C"), e("B", "C"), e("C", "D"), e("C", "E"), e("D", "E")])

/// The same bow tie minus A–C: A and C now have odd degree, so only a path exists.
private let eulerPathGraph = GraphDef(nodes: eulerCircuitGraph.nodes,
                                      edges: [e("A", "B"), e("B", "C"), e("C", "D"), e("C", "E"), e("D", "E")])

private func eulerStoryFrames(circuit: Bool) -> [GFrame] {
    let def = circuit ? eulerCircuitGraph : eulerPathGraph
    var degree: [String: Int] = [:]
    for id in def.ids { degree[id] = def.edges.filter { $0.from == id || $0.to == id }.count }
    let odd = degree.values.filter { $0 % 2 == 1 }.count
    let m = def.edges.count
    var frames: [GFrame] = []
    let badges = degree.mapValues { ("\($0)", GS.idle) }

    func frame(_ headline: String, _ body: String, _ main: [String], sub: [String]? = nil, current: String? = nil,
               currentEdge: (String, String)? = nil, splice: String? = nil, emphasis: GS = .active) {
        func pairs(_ l: [String]) -> [(String, String)] { l.count < 2 ? [] : (0..<(l.count - 1)).map { (l[$0], l[$0 + 1]) } }
        let used = (pairs(main) + pairs(sub ?? [])).map { def.edgeIndex($0.0, $0.1) }
        var edges = Dictionary(used.map { ($0, GS.done) }, uniquingKeysWith: { a, _ in a })
        if let ce = currentEdge { edges[def.edgeIndex(ce.0, ce.1)] = .active }
        var nodes = Dictionary((main + (sub ?? [])).map { ($0, GS.done) }, uniquingKeysWith: { a, _ in a })
        if let splice { nodes[splice] = .path }
        if let current { nodes[current] = .active }
        func cells(_ list: [String], _ slots: Int) -> [GCell] {
            list.enumerated().map { i, id in GCell(text: id, tone: id == current && i == list.count - 1 ? .active : id == splice ? .path : .done) } +
                Array(repeating: GCell(text: "", tone: .empty), count: max(0, slots - list.count))
        }
        let label = circuit ? "CIRCUIT" : "PATH"
        var s = GraphStory(title: circuit ? "EULERIAN CIRCUIT" : "EULERIAN PATH", note: "badges = degree", headline: headline, body: body)
        s.nodes = nodes
        s.badges = badges
        s.edges = edges
        s.rows = [sub == nil ? [GCellGroup(label: label, cells: cells(main, m + 1))]
                             : [GCellGroup(label: label, cells: cells(main, main.count)), GCellGroup(label: "SUB-TOUR", cells: cells(sub!, 4))]]
        s.chips = [GChip(key: "odd degree", value: "\(odd)"), GChip(key: "edges", value: "\(used.count) of \(m)")]
        s.emphasis = emphasis
        s.legend = [(.active, "Current"), (.path, "Splice point"), (.done, "Edge used")]
        frames.append(storyFrame(s))
    }

    if circuit {
        frame("Every vertex has an {even} degree, so a circuit exists.",
              "An Eulerian circuit uses every edge once and ends where it started. Hierholzer's algorithm walks until it is stuck, then splices in detours.",
              ["A"], current: "A")
        frame("Walk from A to {B}.", "Any unused edge will do. Each edge is crossed once and then it is gone.", ["A", "B"], current: "B", currentEdge: ("A", "B"))
        frame("Walk from B to {C}.", "C has four edges, so the walk will come back through it.", ["A", "B", "C"], current: "C", currentEdge: ("B", "C"))
        frame("C→A closes {A→B→C→A}, but C still has unused edges.",
              "With every degree even, a walk can only get stuck back where it began. C's other two edges become a sub-tour.",
              ["A", "B", "C", "A"], current: "A", currentEdge: ("C", "A"), splice: "C")
        frame("A→B→C→A closed with edges left at C. A sub-tour starts there and reaches {D}.",
              "C→D→E→C gets spliced into the circuit at C. Every degree is even, so a circuit exists.",
              ["A", "B", "C", "A"], sub: ["C", "D"], current: "D", currentEdge: ("C", "D"), splice: "C")
        frame("The sub-tour goes on to {E}.", "D's only other edge leads to E.", ["A", "B", "C", "A"], sub: ["C", "D", "E"], current: "E", currentEdge: ("D", "E"), splice: "C")
        frame("E→C closes the sub-tour {C→D→E→C}.", "It starts and ends at C, so it can be dropped into the circuit at C without breaking it.",
              ["A", "B", "C", "A"], sub: ["C", "D", "E", "C"], current: "C", currentEdge: ("E", "C"), splice: "C")
        frame("Splice it in: {m:A→B→C→D→E→C→A}.", "All \(m) edges are used exactly once, and the walk ends at A where it started.",
              ["A", "B", "C", "D", "E", "C", "A"], emphasis: .done)
    } else {
        frame("Two vertices, {A} and {C}, have odd degree, so only a path exists.",
              "An Eulerian path must start at one odd vertex and end at the other. Here it starts at A.", ["A"], current: "A")
        frame("Walk from A to {B}.", "A's only edge leads to B.", ["A", "B"], current: "B", currentEdge: ("A", "B"))
        frame("Walk from B to {C}.", "C has three edges: one to arrive, then two more to use.", ["A", "B", "C"], current: "C", currentEdge: ("B", "C"))
        frame("Walk from C to {D}.", "Going to D first still leaves a way back to C through E.", ["A", "B", "C", "D"], current: "D", currentEdge: ("C", "D"))
        frame("Walk from D to {E}.", "D's other edge leads to E.", ["A", "B", "C", "D", "E"], current: "E", currentEdge: ("D", "E"))
        frame("The path ends at {m:C}, the other odd vertex.", "A→B→C→D→E→C uses all \(m) edges once. It cannot return to A, because A has odd degree.",
              ["A", "B", "C", "D", "E", "C"], emphasis: .done)
    }
    return frames
}

private let flowStoryGraph = GraphDef(
    nodes: [n("S", 0.05, 0.5), n("A", 0.5, 0.06), n("B", 0.5, 0.94), n("T", 0.95, 0.5)],
    edges: [d("S", "A", 3), d("S", "B", 2), d("A", "T", 2), d("B", "T", 3), d("A", "B", 1)])

private func maxFlowStoryFrames() -> [GFrame] {
    let def = flowStoryGraph
    var flow = [Int](repeating: 0, count: def.edges.count)
    var frames: [GFrame] = []
    var total = 0
    func cap(_ i: Int) -> Int { def.edges[i].weight! }

    func frame(_ title: String, _ path: [String]?, _ headline: String, _ body: String, _ chips: [GChip], cut: Bool = false, emphasis: GS = .active) {
        let onPath = path.map { p in (0..<(p.count - 1)).map { def.directedIndex(p[$0], p[$0 + 1]) } } ?? []
        var s = GraphStory(title: title, note: "flow / capacity", headline: headline, body: body)
        var edges: [Int: GS] = [:]
        for i in def.edges.indices {
            edges[i] = onPath.contains(i) ? .active : cut && def.edges[i].from == "S" ? .cut : flow[i] > 0 ? .done : .idle
        }
        s.edges = edges
        var nodes = Dictionary((path ?? []).map { ($0, GS.active) }, uniquingKeysWith: { a, _ in a })
        if cut { nodes["S"] = .cut }
        s.nodes = nodes
        s.edgeLabels = Dictionary(uniqueKeysWithValues: def.edges.indices.map { ($0, "\(flow[$0])/\(cap($0))") })
        s.boxed = Set(def.edges.indices)
        if path != nil {
            let residual = onPath.map { GCell(text: "\(def.edges[$0].from)→\(def.edges[$0].to) \(cap($0) - flow[$0])") }
            s.rows = [[GCellGroup(label: nil, cells: residual + [GCell(text: "min \(onPath.map { cap($0) - flow[$0] }.min()!)", tone: .active)])]]
        }
        s.chips = chips
        s.emphasis = emphasis
        s.legend = [(.active, "Augmenting path"), (.done, "Carrying flow"), (.cut, "Min cut")]
        frames.append(storyFrame(s))
    }
    func push(_ path: [String]) {
        let ids = (0..<(path.count - 1)).map { def.directedIndex(path[$0], path[$0 + 1]) }
        let amount = ids.map { cap($0) - flow[$0] }.min()!
        for i in ids { flow[i] += amount }
        total += amount
    }

    frame("NETWORK", nil, "Every edge starts with {flow 0}.",
          "Each label is flow / capacity. Ford-Fulkerson keeps finding a path from S to T with room left, and pushes as much as its tightest edge allows.",
          [GChip(key: "total flow", value: "0")])
    frame("AUGMENTING PATH 1", ["S", "A", "T"], "Push 2 along {S→A→T}. A→T is the bottleneck.",
          "A→T has room for only 2 of S→A's 3. Every unit that leaves S along this path reaches T.",
          [GChip(key: "bottleneck", value: "2"), GChip(key: "total flow", value: "0 → 2")])
    push(["S", "A", "T"])
    frame("AFTER PATH 1", nil, "{m:A→T} is now full.", "Its 2/2 means no more flow can use it, so the next path has to go another way.",
          [GChip(key: "total flow", value: "\(total)")], emphasis: .done)
    frame("AUGMENTING PATH 2", ["S", "B", "T"], "Push 2 along {S→B→T}. S→B is the bottleneck.",
          "Flow is now 4. Next, S→A→B→T carries the last unit, for a max flow of 5.",
          [GChip(key: "bottleneck", value: "2"), GChip(key: "total flow", value: "\(total) → \(total + 2)")])
    push(["S", "B", "T"])
    frame("AFTER PATH 2", nil, "{m:S→B} is full too.", "The only room left out of S is 1 unit on S→A.",
          [GChip(key: "total flow", value: "\(total)")], emphasis: .done)
    frame("AUGMENTING PATH 3", ["S", "A", "B", "T"], "Push 1 along {S→A→B→T}.",
          "S→A has 1 unit spare and A→B carries it across to B, which still has room to T.",
          [GChip(key: "bottleneck", value: "1"), GChip(key: "total flow", value: "\(total) → \(total + 1)")])
    push(["S", "A", "B", "T"])
    frame("AFTER PATH 3", nil, "Every edge out of S is now {m:full}.", "S→A carries 3 of 3 and S→B 2 of 2, so no augmenting path is left.",
          [GChip(key: "total flow", value: "\(total)")], emphasis: .done)
    frame("MAX FLOW", nil, "The max flow is {c:\(total)}.",
          "Cutting S off from the rest costs 3 + 2 = \(total), the same as the flow. That match is what proves no more can fit.",
          [GChip(key: "max flow", value: "\(total)", tone: .cut), GChip(key: "min cut", value: "\(total)", tone: .cut)], cut: true, emphasis: .cut)
    return frames
}

private let cutStoryGraph = GraphDef(
    nodes: [n("A", 0.1, 0.08), n("B", 0.1, 0.92), n("C", 0.4, 0.5), n("D", 0.6, 0.5), n("E", 0.9, 0.08), n("F", 0.9, 0.92)],
    edges: [e("A", "B"), e("A", "C"), e("B", "C"), e("C", "D"), e("D", "E"), e("D", "F"), e("E", "F")])

private func articulationStoryFrames() -> [GFrame] {
    let def = cutStoryGraph
    var frames: [GFrame] = []
    var disc: [String: Int] = [:], low: [String: Int] = [:]
    var cuts: [String] = [], bridges: [Int] = [], found: [String] = []

    func frame(_ current: String?, _ headline: String, _ body: String, _ lines: [GLine] = [], emphasis: GS = .active) {
        var s = GraphStory(title: "DFS · disc / low", note: "cut if low(child) ≥ disc", headline: headline, body: body)
        var nodes = Dictionary(uniqueKeysWithValues: cuts.map { ($0, GS.cut) })
        if let current { nodes[current] = .active }
        s.nodes = nodes
        s.badges = Dictionary(uniqueKeysWithValues: disc.keys.map { id in (id, ("\(disc[id]!)/\(low[id]!)", id == current ? GS.active : cuts.contains(id) ? .cut : .idle)) })
        s.edges = Dictionary(uniqueKeysWithValues: bridges.map { ($0, GS.cut) })
        s.lines = lines
        s.chips = found.isEmpty ? [GChip(key: "found", value: "none yet")] : [GChip(key: "found", value: found.joined(separator: ", "), tone: .cut)]
        s.emphasis = emphasis
        s.legend = [(.active, "Checking"), (.cut, "Cut vertex or bridge")]
        frames.append(storyFrame(s))
    }
    func visit(_ id: String, _ dd: Int) { disc[id] = dd; low[id] = dd }

    visit("A", 1)
    frame("A", "Start the DFS at {A}: disc 1, low 1.", "disc is the visit order. low is the earliest disc a node's subtree can reach with one back edge.")
    visit("B", 2)
    frame("B", "Visit {B}: disc 2.", "B's only other neighbour so far is A, its parent, so low stays 2.")
    visit("C", 3); low["C"] = 1
    frame("C", "Visit {C}: disc 3. It also sees A.", "A is already visited and isn't C's parent, so that is a back edge: low(C) drops to 1.")
    visit("D", 4)
    frame("D", "Visit {D}: disc 4.", "The only way to D is across the edge from C.")
    visit("E", 5)
    frame("E", "Visit {E}: disc 5.", "E's subtree is about to find its own way back.")
    visit("F", 6); low["F"] = 4
    frame("F", "Visit {F}: disc 6. It sees D.", "D is already visited, so that is a back edge: low(F) drops to 4.")
    low["E"] = 4
    frame("E", "{E} finishes with low 4.", "Its child F reaches D, so E's subtree can climb to D but no higher.")
    cuts.append("D"); found.append("D")
    frame("D", "E's subtree can't climb above {D}, so D is a cut vertex.",
          "Removing D would cut E and F off. D–E is not a bridge, because F gives E a second way to D.",
          [GLine(test: "low(E) 4 ≥ disc(D) 4", verdict: "D is a cut vertex", tone: .cut), GLine(test: "low(E) 4 = disc(D) 4", verdict: "no bridge D–E", tone: .idle)])
    cuts.append("C"); bridges.append(def.edgeIndex("C", "D")); found += ["C", "C–D"]
    frame("D", "{D}'s subtree can't climb above D, so C–D is a bridge.",
          "Removing C would cut A and B off from D, E and F, so C is a cut vertex too.",
          [GLine(test: "low(D) 4 > disc(C) 3", verdict: "bridge C–D", tone: .cut), GLine(test: "low(D) 4 ≥ disc(C) 3", verdict: "C is a cut vertex", tone: .cut)])
    low["B"] = 1
    frame("B", "{B} finishes with low 1.", "C's back edge to A lifts B's whole subtree above B, so B is not a cut vertex.",
          [GLine(test: "low(C) 1 < disc(B) 2", verdict: "B is not a cut vertex", tone: .idle)])
    frame("A", "The root {A} has one DFS child.", "A root is a cut vertex only when it has two or more children, so A is not one.")
    frame(nil, "Cut vertices {c:C and D}, and one bridge, {c:C–D}.",
          "Removing either vertex, or that edge, splits the graph. Every other edge sits on a cycle.", emphasis: .cut)
    return frames
}

private let dagStoryGraph = GraphDef(
    nodes: [n("A", 0.1, 0.08), n("B", 0.1, 0.92), n("C", 0.4, 0.5), n("D", 0.7, 0.08), n("E", 0.7, 0.92), n("F", 0.95, 0.5)],
    edges: [d("A", "C"), d("B", "C"), d("C", "D"), d("C", "E"), d("D", "F"), d("E", "F")])

private func topoStoryFrames() -> [GFrame] {
    let def = dagStoryGraph
    let count = def.nodes.count
    var indeg: [String: Int] = [:]
    for id in def.ids { indeg[id] = def.edges.filter { $0.to == id }.count }
    var queue: [String] = [], output: [String] = []
    var frames: [GFrame] = []

    func frame(_ popped: String?, _ changed: [String: Int], _ headline: String, _ body: String, emphasis: GS = .active) {
        var s = GraphStory(title: "KAHN'S ALGORITHM", note: "badges = in-degree", headline: headline, body: body)
        var nodes = Dictionary(uniqueKeysWithValues: output.map { ($0, GS.done) })
        for q in queue { nodes[q] = .path }
        if let popped { nodes[popped] = .active }
        s.nodes = nodes
        s.badges = Dictionary(uniqueKeysWithValues: def.ids.map { id in
            (id, changed[id].map { ("\($0) → \(indeg[id]!)", GS.path) } ?? ("\(indeg[id]!)", GS.idle))
        })
        if let popped { s.edges = Dictionary(uniqueKeysWithValues: def.edges.indices.filter { def.edges[$0].from == popped }.map { ($0, GS.active) }) }
        s.rows = [[
            GCellGroup(label: "QUEUE", cells: queue.isEmpty ? [GCell(text: "", tone: .empty)] : queue.map { GCell(text: $0, tone: .path) }),
            GCellGroup(label: "OUTPUT", cells: output.map { GCell(text: $0, tone: $0 == popped ? .active : .done) } + Array(repeating: GCell(text: "", tone: .empty), count: count - output.count)),
        ]]
        s.chips = [GChip(key: "emitted", value: "\(output.count) of \(count)")]
        s.emphasis = emphasis
        s.legend = [(.active, "Popped"), (.path, "Ready"), (.done, "Emitted")]
        frames.append(storyFrame(s))
    }

    frame(nil, [:], "Count each vertex's {in-degree}.", "In-degree is the number of edges coming in. A vertex at 0 has no dependency left and can go next.")
    queue = def.ids.filter { indeg[$0] == 0 }
    frame(nil, [:], "{p:A} and {p:B} have in-degree 0, so they start the queue.", "Kahn's algorithm only ever outputs a vertex whose dependencies are all out already.")
    let lines: [String: (String, String)] = [
        "A": ("Pop {A}. C loses one dependency.", "C still waits on B."),
        "B": ("Pop {B}. C's last dependency is gone.", "C drops to in-degree 0 and joins the queue."),
        "C": ("Pop {C}. D and E lose their last dependency.", "Both drop to in-degree 0 and join the queue. F still waits on two."),
        "D": ("Pop {D}. F loses one dependency.", "F still waits on E."),
        "E": ("Pop {E}. F is free.", "F drops to in-degree 0 and joins the queue."),
        "F": ("Pop {F}, the last vertex.", "Nothing depends on F, so no in-degree changes."),
    ]
    while !queue.isEmpty {
        let v = queue.removeFirst()
        output.append(v)
        var changed: [String: Int] = [:]
        for edge in def.edges where edge.from == v {
            changed[edge.to] = indeg[edge.to]!
            indeg[edge.to]! -= 1
            if indeg[edge.to] == 0 { queue.append(edge.to) }
        }
        frame(v, changed, lines[v]!.0, lines[v]!.1)
    }
    frame(nil, [:], "A valid order: {m:\(output.joined(separator: ", "))}.",
          "Every edge points forward in this order. If the queue ran dry before all \(count) were out, the graph would have a cycle.", emphasis: .done)
    return frames
}

private let sccStoryGraph = GraphDef(
    nodes: [n("A", 0.08, 0.35), n("B", 0.38, 0.06), n("C", 0.38, 0.68), n("D", 0.68, 0.14), n("E", 0.92, 0.56), n("F", 0.64, 0.94), n("G", 0.12, 0.94)],
    edges: [d("A", "B"), d("B", "C"), d("C", "A"), d("C", "D"), d("D", "E"), d("E", "F"), d("F", "G"), d("F", "D")])

private func kosarajuStoryFrames(pass2: Bool) -> [GFrame] {
    let def = sccStoryGraph
    var frames: [GFrame] = []
    func ed(_ a: String, _ b: String) -> Int { def.directedIndex(a, b) }

    if !pass2 {
        var finish: [String] = []
        func frame(_ current: String?, _ stack: [String], _ tree: [Int], _ active: Int?, _ headline: String, _ body: String, emphasis: GS = .active) {
            var s = GraphStory(title: "PASS 1 · DFS", note: "record finish order", headline: headline, body: body)
            var nodes = Dictionary(uniqueKeysWithValues: finish.map { ($0, GS.done) })
            for id in stack { nodes[id] = .path }
            if let current { nodes[current] = .active }
            s.nodes = nodes
            var edges = Dictionary(uniqueKeysWithValues: tree.map { ($0, GS.path) })
            if let active { edges[active] = .active }
            s.edges = edges
            s.rows = [[GCellGroup(label: "FINISH ORDER", cells: finish.map { GCell(text: $0, tone: .done) } + Array(repeating: GCell(text: "", tone: .empty), count: 7 - finish.count),
                                  note: "first to finish first")]]
            s.chips = [GChip(key: "finished", value: "\(finish.count) of 7")]
            s.emphasis = emphasis
            s.legend = [(.active, "Current"), (.path, "On stack"), (.done, "Finished")]
            frames.append(storyFrame(s))
        }
        frame("A", [], [], nil, "Pass 1 runs a plain DFS from {A}.", "It records the order in which vertices finish, nothing else.")
        frame("B", ["A"], [], ed("A", "B"), "Visit {B}.", "Follow A→B.")
        frame("C", ["A", "B"], [ed("A", "B")], ed("B", "C"), "Visit {C}.", "Follow B→C.")
        frame("D", ["A", "B", "C"], [ed("A", "B"), ed("B", "C")], ed("C", "D"), "C→A leads back to A, already visited, so C moves on to {D}.",
              "An edge to a visited vertex is skipped in both passes.")
        frame("E", ["A", "B", "C", "D"], [ed("A", "B"), ed("B", "C"), ed("C", "D")], ed("D", "E"), "Visit {E}.", "Follow D→E.")
        frame("F", ["A", "B", "C", "D", "E"], [ed("A", "B"), ed("B", "C"), ed("C", "D"), ed("D", "E")], ed("E", "F"), "Visit {F}.", "Follow E→F.")
        let tree = [ed("A", "B"), ed("B", "C"), ed("C", "D"), ed("D", "E"), ed("E", "F")]
        frame("G", ["A", "B", "C", "D", "E", "F"], tree, ed("F", "G"), "Visit {G}.", "F→G first. F→D goes back to a visited vertex, so it is skipped.")
        finish.append("G")
        frame(nil, ["A", "B", "C", "D", "E", "F"], tree, nil, "{m:G} has no way out, so it finishes first.", "A vertex finishes once every edge out of it is explored.", emphasis: .done)
        finish += ["F", "E", "D"]
        frame(nil, ["A", "B", "C"], tree, nil, "{m:F}, {m:E} and {m:D} finish in turn.", "Each has nothing left to explore once G is done.", emphasis: .done)
        finish += ["C", "B", "A"]
        frame(nil, [], tree, nil, "{m:C}, {m:B} and {m:A} finish last.", "A started the search, so it finishes after everything it reached.", emphasis: .done)
        var s = GraphStory(title: "PASS 1 · DFS", note: "record finish order", headline: "Read latest first: {A, B, C, D, E, F, G}.",
                           body: "Pass 2 starts from whichever vertex finished last. That guarantees each search stays inside one component.")
        s.nodes = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, GS.done) })
        s.rows = [[GCellGroup(label: "FINISH ORDER", cells: finish.reversed().map { GCell(text: $0, tone: .done) }, note: "latest first")]]
        s.chips = [GChip(key: "finished", value: "7 of 7")]
        s.legend = [(.done, "Finished")]
        frames.append(storyFrame(s))
        return frames
    }

    let order = ["A", "B", "C", "D", "E", "F", "G"]
    var found: [String] = []
    var components = 0
    func frame(_ current: String?, _ comp: [String], _ compEdges: [Int], _ active: Int?, _ headline: String, _ body: String, emphasis: GS = .active) {
        var s = GraphStory(title: "PASS 2 · REVERSED", note: "latest first", headline: headline, body: body)
        var nodes = Dictionary(uniqueKeysWithValues: found.map { ($0, GS.done) })
        for id in comp { nodes[id] = .path }
        if let current { nodes[current] = .active }
        s.nodes = nodes
        var edges = Dictionary(uniqueKeysWithValues: compEdges.map { ($0, GS.path) })
        if let active { edges[active] = .active }
        s.edges = edges
        s.reversed = true
        s.rows = [[GCellGroup(label: "PASS 1 FINISH ORDER", cells: order.map { id in
            GCell(text: id, tone: id == current ? .active : comp.contains(id) ? .path : found.contains(id) ? .done : .idle)
        }, note: "latest first")]]
        s.chips = [GChip(key: "components", value: "\(components) found")]
        s.emphasis = emphasis
        s.legend = [(.active, "Current"), (.path, "This component"), (.done, "Component found")]
        frames.append(storyFrame(s))
    }
    frame(nil, [], [], nil, "Reverse every {edge}.", "A component stays a component when its edges flip, but the links between components now point backwards.")
    frame("A", [], [], nil, "Start from {A}, the last to finish.", "Whatever A reaches in the reversed graph is its component.")
    frame("C", ["A"], [], ed("C", "A"), "A reaches {C}.", "C→A flipped is A→C.")
    frame("B", ["A", "C"], [ed("C", "A")], ed("B", "C"), "C reaches {B}, and B leads back to A.", "Nothing else is reachable from A, B and C in the reversed graph.")
    found += ["A", "B", "C"]; components = 1
    frame(nil, [], [], nil, "{m:A, B and C} form the first component.", "The next start is the first unvisited vertex in the order: D.", emphasis: .done)
    frame("D", [], [], nil, "Start again from {D}.", "C→D flipped points into C, which is already taken, so the search can't leak back.")
    frame("F", ["D"], [], ed("F", "D"), "D reaches {F}.", "F→D flipped is D→F.")
    frame("E", ["D", "F"], [ed("F", "D")], ed("E", "F"), "In the reversed graph, D reaches F and then {E}.",
          "They can't get back to A, B or C, so D, E and F form the second component. G is last, on its own.")
    found += ["D", "E", "F"]; components = 2
    frame(nil, [], [], nil, "{m:D, E and F} form the second component.", "Only G is left.", emphasis: .done)
    found.append("G"); components = 3
    frame(nil, [], [], nil, "{m:G} is a component of its own.",
          "Three components: {A, B, C}, {D, E, F} and {G}, found by two DFS passes in O(V + E).", emphasis: .done)
    return frames
}

private func bellmanStoryFrames() -> [GFrame] {
    let def = negativeWeightGraph
    var dist = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, $0 == "A" ? 0 : graphInf) })
    var frames: [GFrame] = []
    let rounds = def.nodes.count - 1
    var first = true
    for round in 1...rounds {
        for (i, edge) in def.edges.enumerated() {
            let du = dist[edge.from]!, dv = dist[edge.to]!, w = edge.weight!
            let improved = du < graphInf && du + w < dv
            if improved { dist[edge.to] = du + w }
            let headline: String, body: String
            if du >= graphInf {
                headline = "Skip {\(edge.from)→\(edge.to)}: \(edge.from) is still ∞."
                body = "An edge can only help once its start has a distance."
            } else if improved {
                headline = "Relax {\(edge.from)→\(edge.to)}. \(distText(du)) + \(w) beats \(distText(dv)), so \(edge.to) becomes \(du + w)."
                body = first ? "Each round relaxes every edge. Four rounds cover any shortest path in five vertices, and a fifth checks for negative cycles."
                    : "\(edge.to)'s best known distance drops from \(distText(dv)) to \(du + w)."
                first = false
            } else {
                headline = "{\(edge.from)→\(edge.to)} gives \(du + w), no better than \(distText(dv))."
                body = "\(edge.to) keeps \(distText(dv))."
            }
            var s = GraphStory(title: "ROUND \(round) OF \(rounds)", note: "relax every edge", headline: headline, body: body)
            var nodes: [String: GS] = [edge.from: .active]
            if improved { nodes[edge.to] = .done }
            s.nodes = nodes
            s.badges = Dictionary(uniqueKeysWithValues: def.ids.map { id in (id, (distText(dist[id]!), id == edge.from ? GS.active : improved && id == edge.to ? .done : .idle)) })
            s.edges = [i: improved ? .done : .active]
            s.boxed = [i]
            s.rows = [[GCellGroup(label: "DIST", cells: def.ids.map { id in GCell(text: "\(id) \(distText(dist[id]!))", tone: improved && id == edge.to ? .done : .idle) })]]
            s.chips = [GChip(key: "edge", value: "\(edge.from)→\(edge.to)"), GChip(key: "w", value: "\(w)")]
            s.legend = [(.active, "Relaxing from"), (.done, "Improved")]
            frames.append(storyFrame(s))
        }
    }
    let negative = def.edges.contains { dist[$0.from]! < graphInf && dist[$0.from]! + $0.weight! < dist[$0.to]! }
    var s = GraphStory(title: "ROUND 5 · CHECK", note: "relax every edge",
                       headline: negative ? "Round 5 still improves an edge, so there is a {w:negative cycle}." : "Round 5 changes nothing, so there is {m:no negative cycle}.",
                       body: "Final distances from A: " + def.ids.map { "\($0) \(distText(dist[$0]!))" }.joined(separator: ", ") + ".")
    s.nodes = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, GS.done) })
    s.badges = Dictionary(uniqueKeysWithValues: def.ids.map { ($0, (distText(dist[$0]!), GS.done)) })
    s.rows = [[GCellGroup(label: "DIST", cells: def.ids.map { GCell(text: "\($0) \(distText(dist[$0]!))", tone: .done) })]]
    s.chips = [GChip(key: "rounds", value: "\(rounds) + 1")]
    s.emphasis = .done
    s.legend = [(.done, "Final")]
    frames.append(storyFrame(s))
    return frames
}

private func tarjanStoryFrames() -> [GFrame] {
    let def = sccStoryGraph
    var frames: [GFrame] = []
    var index: [String: Int] = [:], low: [String: Int] = [:]
    var stack: [String] = [], done: [String] = []
    func ed(_ a: String, _ b: String) -> Int { def.directedIndex(a, b) }

    func frame(_ current: String?, _ active: Int?, _ headline: String, _ body: String, _ chips: [GChip] = [], emphasis: GS = .active) {
        var s = GraphStory(title: "ONE DFS", note: "badges = index / low", headline: headline, body: body)
        var nodes = Dictionary(uniqueKeysWithValues: done.map { ($0, GS.done) })
        for id in stack { nodes[id] = .path }
        if let current { nodes[current] = .active }
        s.nodes = nodes
        s.badges = Dictionary(uniqueKeysWithValues: index.keys.map { id in (id, ("\(index[id]!)/\(low[id]!)", id == current ? GS.active : .idle)) })
        if let active { s.edges = [active: .active] }
        s.rows = [[GCellGroup(label: "STACK", cells: stack.isEmpty ? [GCell(text: "", tone: .empty)] : stack.map { GCell(text: $0, tone: $0 == current ? .active : .path) },
                              note: "bottom → top")]]
        s.chips = chips.isEmpty ? [GChip(key: "components", value: "\(["G", "D", "A"].filter { done.contains($0) }.count) found")] : chips
        s.emphasis = emphasis
        s.legend = [(.active, "Current"), (.path, "On stack"), (.done, "Component found")]
        frames.append(storyFrame(s))
    }
    func visit(_ v: String) { index[v] = index.count; low[v] = index[v]; stack.append(v) }

    visit("A")
    frame("A", nil, "Visit {A}: index 0, pushed on the stack.",
          "Tarjan finds every strongly connected component in one DFS. low is the smallest index a vertex can reach while staying on the stack.")
    visit("B"); frame("B", ed("A", "B"), "Visit {B}: index 1.", "Every visit pushes the vertex on the stack.")
    visit("C"); frame("C", ed("B", "C"), "Visit {C}: index 2.", "Its first edge leads back to A.")
    low["C"] = 0
    frame("C", ed("C", "A"), "{C} reaches A, which is still on the stack.", "So low(C) drops to 0: C, B and A belong together.", [GChip(key: "low(C)", value: "min(2, 0) = 0")])
    visit("D"); frame("D", ed("C", "D"), "Visit {D}: index 3.", "C's next edge leaves for D.")
    visit("E"); frame("E", ed("D", "E"), "Visit {E}: index 4.", "Follow D→E.")
    visit("F"); frame("F", ed("E", "F"), "Visit {F}: index 5.", "F has two edges out: to G, then back to D.")
    visit("G"); frame("G", ed("F", "G"), "Visit {G}: index 6.", "G has no edges out.")
    stack.removeAll { $0 == "G" }; done.append("G")
    frame(nil, nil, "{m:G} is a component on its own.", "Its low equals its index, so it pops off the stack alone.", emphasis: .done)
    low["F"] = 3
    frame("F", ed("F", "D"), "{F} reaches D, which is still on the stack.",
          "So low(F) drops to 3. When D finishes with low equal to its index, D, E and F pop off as one component.", [GChip(key: "low(F)", value: "min(5, 3) = 3")])
    frame("F", nil, "{F} finishes with low 3.", "3 is less than F's index, so F stays on the stack for D to collect.")
    low["E"] = 3
    frame("E", nil, "{E} finishes with low 3.", "It takes F's low, since F is its child.", [GChip(key: "low(E)", value: "min(4, 3) = 3")])
    stack.removeAll { ["D", "E", "F"].contains($0) }; done += ["D", "E", "F"]
    frame(nil, nil, "{m:D, E and F} pop off as one component.", "D finished with low equal to its index, so it and everything above it leave the stack.", emphasis: .done)
    frame("C", nil, "{C} finishes with low 0.", "0 is A's index, so C stays on the stack.")
    low["B"] = 0
    frame("B", nil, "{B} finishes with low 0.", "It takes C's low.", [GChip(key: "low(B)", value: "min(1, 0) = 0")])
    stack.removeAll { ["A", "B", "C"].contains($0) }; done += ["A", "B", "C"]
    frame(nil, nil, "{m:A, B and C} pop off as one component.", "A's low equals its index, so the rest of the stack comes with it.", emphasis: .done)
    frame(nil, nil, "Three components from {m:one DFS}.",
          "{A, B, C}, {D, E, F} and {G}. Every vertex is pushed and popped exactly once, so the whole run is O(V + E).", emphasis: .done)
    return frames
}

private let graphConfigs: [String: GConfig] = [
    "dag_dp_pattern": GConfig(intro: "Longest path in a weighted DAG. Badges are the best distance found so far — relaxing nodes in topological order means each one is finished the first time it is popped.",
                              def: dagDpGraph, legend: shortestPathLegend, build: dagDpFrames),
    "shortest_path_pattern": GConfig(intro: "One graph, three answers: BFS when every edge costs 1, Dijkstra when they differ, and the negative edge that breaks Dijkstra's finality assumption and hands the problem to Bellman-Ford.",
                                     def: shortestPathChoiceGraph, legend: legend([(.active, "Settling"), (.frontier, "Reached"), (.done, "Final")]), build: shortestPathChoiceFrames),
    "graph_coloring_pattern": GConfig(intro: "Greedy colouring in a fixed node order, then the same graph two-coloured as a bipartite check — until the odd cycle refuses and forces a third colour.",
                                      def: colouringGraph, legend: [(nodeColor(.active), "Colouring now"), (edgeColor(.rejected), "Conflict edge"), (nodeColor(.done), "Coloured")], build: graphColouringFrames),
    "topological_sort_pattern": GConfig(intro: "Course schedule, the interview phrasing of a topological sort: first a curriculum that works, then the same one with a back edge added — where the emitted count, not a separate check, catches the cycle.",
                                        def: courseGraph, legend: legend([(.active, "Taken now"), (.frontier, "Ready"), (.done, "Completed")]), build: courseScheduleFrames),
    "union_find_pattern": GConfig(intro: "Connectivity as edges stream in. Node badges are the current root, colours are the components, and the one edge whose two finds agree is the cycle.",
                                  def: unionFindGraph, legend: [(nodeColor(.active), "Edge endpoints"), (edgeColor(.accepted), "Merged"), (edgeColor(.rejected), "Cycle edge")], build: unionFindFrames),
    "bayesian_networks": GConfig(intro: "The sprinkler network: how the missing edges buy the parameter saving, and the two ways conditioning changes what is independent of what.",
                                 def: bayesNetGraph, legend: [(nodeColor(.done), "Observed"), (nodeColor(.active), "In question"), (groupColors[0], "Dependent")], build: bayesNetworkFrames),
    "topological_sort": GConfig(intro: "Kahn's algorithm on a DAG. Badges are remaining in-degrees — a node joins the queue the moment its count hits zero, and the emitted count at the end is the cycle check.",
                                def: dagStoryGraph, legend: legend([(.active, "Popped"), (.frontier, "Ready"), (.done, "Emitted")]), build: topoStoryFrames),
    "max_flow": GConfig(intro: "Ford-Fulkerson with BFS-chosen paths. Edge labels are capacities; the third augmenting path can only exist because the first two left residual capacity behind.",
                        def: flowStoryGraph, legend: [(nodeColor(.active), "On path"), (edgeColor(.accepted), "Flow pushed"), (edgeColor(.rejected), "Saturated (min cut)")], build: maxFlowStoryFrames),
    "articulation_points": GConfig(intro: "One DFS, badges showing disc/low per node. The ≥ test marks cut vertices, the strict > test marks the single bridge holding the two triangles together.",
                                   def: cutStoryGraph, legend: [(nodeColor(.active), "Current"), (nodeColor(.done), "Cut vertex"), (edgeColor(.rejected), "Bridge")], build: articulationStoryFrames),
    "bellman_ford": GConfig(intro: "Bellman–Ford on a directed graph with negative edges. Badges show the current distance from A; watch a node that already looks settled get corrected later — the reason Dijkstra breaks here.",
                            def: negativeWeightGraph, legend: shortestPathLegend, build: bellmanStoryFrames),
    "floyd_warshall": GConfig(intro: "Floyd–Warshall fills in every pair at once. The matrix is dist[row][col]; each round opens one more node as a legal intermediate stop.",
                              def: allPairsGraph, legend: legend([(.active, "Via node"), (.frontier, "Row"), (.updated, "Improved")]), build: floydWarshallFrames),
    "kruskals_mst": GConfig(intro: "Kruskal sorts every edge by weight and takes each one unless it closes a cycle — the cycle test is a union-find lookup, shown here as node colouring by component.",
                            def: mstGraph, legend: kruskalLegend, build: kruskalFrames, edgeTable: true),
    "prims_mst": GConfig(intro: "Prim on the same graph as Kruskal. It never sorts: it grows one tree, repeatedly taking the cheapest edge that crosses out of it. Same total weight, different order of discovery.",
                         def: mstGraph, legend: primLegend, build: primFrames, edgeTable: true),
    "tarjans_algorithm": GConfig(intro: "Tarjan's SCC algorithm. Badges read index/low-link; a component pops off the stack the moment a node's low-link equals its own index.",
                                 def: sccStoryGraph, legend: sccLegend, build: tarjanStoryFrames),
    "kosarajus_algorithm": GConfig(intro: "Kosaraju's two-pass SCC algorithm: finish times on the original graph, then DFS the transpose in reverse finish order. Arrows flip when the second pass starts.",
                                   def: sccStoryGraph, legend: sccLegend, build: { kosarajuStoryFrames(pass2: false) }, variants: [GraphVariant(label: "Pass 1 · Finish", def: sccStoryGraph, build: { kosarajuStoryFrames(pass2: false) }), GraphVariant(label: "Pass 2 · Reverse", def: sccStoryGraph, build: { kosarajuStoryFrames(pass2: true) })]),
    "graph_variants": GConfig(intro: "The same six vertices and eight edges, read as undirected, directed, unweighted, weighted, cyclic and acyclic in turn. Each frame states what that reading costs or buys, measured on this graph.",
                              def: variantsGraph, legend: [(Color(hex: 0x0EA5E9), "Reachable"), (SimColors.green, "Cheapest route"), (SimColors.red, "Blocked / cycle")], build: graphVariantsFrames),
    "eulerian_path": GConfig(intro: "Two triangles hinged at C. Badges start as degrees, because the degrees decide existence outright — then Hierholzer walks until stuck and splices the leftover circuit in at the hinge.",
                             def: eulerPathGraph, legend: [(nodeColor(.active), "Current vertex"), (nodeColor(.frontier), "Splice point"), (edgeColor(.accepted), "Edge used")], build: { eulerStoryFrames(circuit: false) }, variants: [GraphVariant(label: "Path", def: eulerPathGraph, build: { eulerStoryFrames(circuit: false) }), GraphVariant(label: "Circuit", def: eulerCircuitGraph, build: { eulerStoryFrames(circuit: true) })]),
    "gcn": GConfig(intro: "Two triangles bridged by one edge, aggregated through the normalized adjacency matrix, depth after depth -- oversmoothing measured as an exact limit rather than asserted.",
                   def: smallBridgeGraph, legend: [(groupColors[0], "Triangle A"), (groupColors[1], "Triangle B")], build: gcnFrames),
    "gat": GConfig(intro: "The same neighborhood GCN reads by degree alone, reweighted by feature content instead -- and a direct perturbation showing which weighting reacts to it.",
                   def: smallBridgeGraph, legend: [(nodeColor(.active), "Center node"), (nodeColor(.updated), "Perturbed neighbor"), (edgeColor(.active), "Scored edge")], build: gatFrames),
    "hamiltonian_path": GConfig(intro: "The same question about vertices instead of edges, and no counting argument to settle it. Badges are position in the path; the frames include every dead end the search has to undo before the circuit appears.",
                                def: hamiltonStoryGraph, legend: legend([(.active, "Just extended"), (.frontier, "On the path"), (.updated, "Path but no closing edge")]), build: { hamiltonStoryFrames(cycle: false) }, variants: [GraphVariant(label: "Path", def: hamiltonStoryGraph, build: { hamiltonStoryFrames(cycle: false) }), GraphVariant(label: "Cycle", def: hamiltonStoryGraph, build: { hamiltonStoryFrames(cycle: true) })]),
]

// MARK: - UI

struct GraphAlgorithmLab: View {
    private let config: GConfig
    private let frames: [GFrame]
    /// Kruskal and Prim narrate every step by hand, so their intro paragraph would only repeat it.
    private let narrated: Bool
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    init(topicId: String) {
        let key = graphConfigs[topicId] == nil ? "bellman_ford" : topicId
        config = graphConfigs[key]!
        frames = LabCache.get("graphalgo:\(key)") { graphConfigs[key]!.build() }
        narrated = frames.contains { $0.headline != nil }
        _playback = State(initialValue: PlaybackState(stepCount: frames.count, speedMs: 700))
    }

    var body: some View {
        if frames.contains(where: { $0.story != nil }) {
            GraphStoryLab(config: config)
        } else {
            classic
        }
    }

    @ViewBuilder
    private var classic: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        let (autoHeadline, autoBody) = LabCaption.split(frame.status)
        VStack(alignment: .leading, spacing: 0) {
            if !narrated { LabIntro(text: config.intro).padding(.bottom, 12) }
            LabCard {
                GraphCanvas(def: config.def, frame: frame)
                if config.edgeTable { EdgeWeightTable(def: config.def, frame: frame) }
                if let matrix = frame.matrix { DistanceMatrix(ids: config.def.ids, matrix: matrix, frame: frame) }
                FlowLayout(spacing: 16, lineSpacing: 6) {
                    ForEach(config.legend.indices, id: \.self) { GraphSwatch(color: config.legend[$0].0, label: config.legend[$0].1) }
                }
                .padding(.top, 14)
            }
            headline(frame.headline, fallback: autoHeadline)
                .font(AppFont.sans(20, .bold))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            if let note = frame.headline == nil ? autoBody : frame.body {
                Text(note).font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3).padding(.top, 8)
            }
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }

    /// The mock's yellow and red are for a dark card; on white they need to be darker as text.
    private func toneColor(_ tone: GraphTone) -> Color {
        switch tone {
        case .active: scheme == .dark ? SimColors.active : Color(hex: 0xB7791F)
        case .rejected: scheme == .dark ? Color(hex: 0xF28B82) : SimColors.red
        case .done: SimColors.green
        }
    }

    private func headline(_ spans: [GraphSpan]?, fallback: String) -> Text {
        guard let spans else { return Text(fallback) }
        return spans.reduce(Text("")) { text, span in
            text + (span.tone.map { Text(span.text).foregroundColor(toneColor($0)) } ?? Text(span.text))
        }
    }
}

private struct GraphSwatch: View {
    let color: Color
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 7) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 11, height: 11)
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.muted)
        }
    }
}

private struct GraphCanvas: View {
    let def: GraphDef
    let frame: GFrame
    @Environment(\.palette) private var palette

    var body: some View {
        // Anti-parallel pairs are nudged off the centre line so both stay readable.
        let hasReverse = def.edges.enumerated().map { i, e in def.edges.enumerated().contains { j, o in j != i && o.from == e.to && o.to == e.from } }
        Canvas { ctx, size in
            let radius: CGFloat = 16, padX: CGFloat = 26, padY: CGFloat = 26
            var positions: [String: CGPoint] = [:]
            for node in def.nodes { positions[node.id] = CGPoint(x: padX + node.x * (size.width - 2 * padX), y: padY + node.y * (size.height - 2 * padY)) }
            for (index, edge) in def.edges.enumerated() where !frame.hiddenEdges.contains(index) {
                let flip = frame.reversed && edge.directed
                let from = positions[flip ? edge.to : edge.from]!
                let to = positions[flip ? edge.from : edge.to]!
                let length = max(hypot(to.x - from.x, to.y - from.y), 1)
                let ux = (to.x - from.x) / length, uy = (to.y - from.y) / length
                let shift: CGFloat = hasReverse[index] ? 7 : 0
                let ox = -uy * shift, oy = ux * shift
                let start = CGPoint(x: from.x + ux * radius + ox, y: from.y + uy * radius + oy)
                let end = CGPoint(x: to.x - ux * radius + ox, y: to.y - uy * radius + oy)
                let mark: EdgeMark = index == frame.focusEdge ? .active : frame.edgeMarks[index] ?? .idle
                // Idle edges recede so the ones an algorithm has touched carry the picture.
                let color = mark == .idle ? palette.muted.opacity(0.45) : edgeColor(mark)
                let width: CGFloat = mark == .idle ? 1.5 : mark == .rejected ? 2 : 3
                var line = Path()
                line.move(to: start)
                line.addLine(to: end)
                // Rejected edges are dashed as well as red, so "skipped" survives colour blindness.
                ctx.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, dash: mark == .rejected ? [5, 4] : []))
                if edge.directed && !frame.undirected {
                    let angle = atan2(Double(uy), Double(ux))
                    var arrow = Path()
                    arrow.move(to: end)
                    for spread in [2.7, -2.7] {
                        arrow.addLine(to: CGPoint(x: end.x + 9 * CGFloat(cos(angle + spread)), y: end.y + 9 * CGFloat(sin(angle + spread))))
                    }
                    arrow.closeSubpath()
                    ctx.fill(arrow, with: .color(color))
                }
                if let weight = edge.weight, !frame.hideWeights {
                    // Beside the line rather than on it, pushed out along the normal on the same side an
                    // anti-parallel pair is nudged to, so the two weights never collide.
                    let mid = CGPoint(x: (start.x + end.x) / 2 - uy * 10, y: (start.y + end.y) / 2 + ux * 10)
                    ctx.draw(Text("\(weight)").font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .foregroundStyle(mark == .idle ? palette.muted : color), at: mid)
                }
            }
            // Nodes carry two things at once: the fill says which group a node belongs to, the ring says
            // what the algorithm is doing to it right now. A plain node is neutral, not violet.
            for node in def.nodes {
                let c = positions[node.id]!
                let mark = frame.nodeMarks[node.id]
                // An explicit idle mark means "take this node out of its group" — draw it neutral.
                let markColor = mark.flatMap { $0 == .idle ? nil : nodeColor($0) }
                let groupColor = mark == .idle ? nil : frame.groups[node.id].map { groupColors[$0 % groupColors.count] }
                let rect = CGRect(x: c.x - radius, y: c.y - radius, width: 2 * radius, height: 2 * radius)
                let circle = Path(ellipseIn: rect)
                ctx.fill(circle, with: .color(palette.surface))
                ctx.fill(circle, with: .color(groupColor?.opacity(0.32) ?? markColor?.opacity(0.22) ?? palette.outlineVariant))
                ctx.stroke(circle, with: .color(markColor ?? groupColor ?? palette.muted.opacity(0.5)), lineWidth: markColor == nil ? 1.5 : 2.5)
                ctx.draw(Text(node.id).font(AppFont.sans(13, .bold)).foregroundStyle(palette.onSurface), at: c)
                if let badge = frame.badges[node.id] {
                    ctx.draw(Text(badge).font(AppFont.sans(11, .bold)).foregroundStyle(palette.primary),
                             at: CGPoint(x: c.x, y: c.y + radius + 2), anchor: .top)
                }
            }
        }
        .frame(height: 250)
        .padding(6)
        .background(palette.outlineVariant.opacity(0.3), in: RoundedRectangle(cornerRadius: 14))
    }
}

/// Every edge, lightest first, three to a row. Kruskal's progress is a cursor moving down this list;
/// each cell shows the same mark the edge carries in the graph above it.
private struct EdgeWeightTable: View {
    let def: GraphDef
    let frame: GFrame
    @Environment(\.palette) private var palette

    var body: some View {
        let sorted = def.edges.enumerated().map { ($0.offset, $0.element) }.sortedBy { $0.1.weight ?? 0 }
        let rows = stride(from: 0, to: sorted.count, by: 3).map { Array(sorted[$0..<min($0 + 3, sorted.count)]) }
        VStack(alignment: .leading, spacing: 6) {
            Text("EDGES BY WEIGHT").font(AppFont.sans(11, .semibold)).tracking(0.8).foregroundStyle(palette.muted)
            ForEach(rows.indices, id: \.self) { r in
                HStack(spacing: 6) {
                    ForEach(rows[r], id: \.0) { index, edge in cell(index, edge) }
                    ForEach(0..<(3 - rows[r].count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                }
            }
        }
        .padding(.top, 12)
    }

    private func cell(_ index: Int, _ edge: GEdge) -> some View {
        let focused = index == frame.focusEdge
        let mark = focused ? nil : frame.edgeMarks[index]
        let background: Color
        let content: Color
        switch (focused, mark) {
        case (true, _): background = SimColors.active; content = Color(hex: 0x1F1A0A)
        case (_, .accepted?): background = SimColors.green.opacity(0.16); content = SimColors.green
        case (_, .rejected?): background = SimColors.red.opacity(0.14); content = SimColors.red
        case (_, .active?): background = SimColors.active.opacity(0.18); content = palette.onSurface
        default: background = palette.outlineVariant.opacity(0.5); content = palette.muted
        }
        let font = Font.system(size: 13, weight: .semibold, design: .monospaced)
        return HStack {
            Text("\(edge.from)–\(edge.to)").font(font).strikethrough(mark == .rejected)
            Spacer(minLength: 4)
            Text(edge.weight.map(String.init) ?? "").font(font).strikethrough(mark == .rejected)
        }
        .foregroundStyle(content)
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .frame(maxWidth: .infinity)
        .background(background, in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct DistanceMatrix: View {
    let ids: [String]
    let matrix: [[Int]]
    let frame: GFrame
    @Environment(\.palette) private var palette

    var body: some View {
        let weights = [CGFloat](repeating: 1, count: ids.count + 1)
        VStack(spacing: 0) {
            WeightedRow(weights: weights) {
                cell("", header: true)
                ForEach(ids.indices, id: \.self) { cell(ids[$0], header: true, tinted: frame.matrixVia == $0) }
            }
            ForEach(matrix.indices, id: \.self) { i in
                WeightedRow(weights: weights) {
                    cell(ids[i], header: true, tinted: frame.matrixVia == i)
                    ForEach(matrix[i].indices, id: \.self) { j in
                        cell(distText(matrix[i][j]), focused: frame.matrixFocus.map { $0 == (i, j) } ?? false,
                             tinted: frame.matrixVia == i || frame.matrixVia == j)
                    }
                }
            }
        }
        .padding(.top, 12)
    }

    private func cell(_ text: String, header: Bool = false, focused: Bool = false, tinted: Bool = false) -> some View {
        let background: Color = focused ? nodeColor(.updated)
            : header ? palette.outlineVariant
            : tinted ? nodeColor(.active).opacity(0.25)
            : palette.outlineVariant.opacity(0.35)
        return Text(text)
            .font(AppFont.sans(12, header || focused ? .bold : .regular))
            .foregroundStyle(focused ? .white : palette.onSurface)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(background, in: RoundedRectangle(cornerRadius: 6))
            .padding(2)
    }
}


// MARK: - Story UI

private let cutColor = Color(hex: 0x7C5CFF)

private func gsColor(_ tone: GS) -> Color? {
    switch tone {
    case .active: return SimColors.active
    case .path: return SimColors.blue
    case .done: return SimColors.green
    case .cut: return cutColor
    case .warn: return SimColors.red
    default: return nil
    }
}

/// Holds the tab; each tab's storyboard runs in its own `GraphStoryRun`, so switching resets playback.
private struct GraphStoryLab: View {
    let config: GConfig
    @State private var tab = 0

    var body: some View {
        let variant = tab < config.variants.count ? config.variants[tab] : nil
        GraphStoryRun(def: variant?.def ?? config.def, frames: (variant?.build ?? config.build)(),
                      tabs: config.variants.map(\.label), tab: $tab)
            .id(tab)
    }
}

private struct GraphStoryRun: View {
    let def: GraphDef
    let frames: [GFrame]
    let tabs: [String]
    @Binding var tab: Int
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    init(def: GraphDef, frames: [GFrame], tabs: [String], tab: Binding<Int>) {
        self.def = def
        self.frames = frames
        self.tabs = tabs
        _tab = tab
        _playback = State(initialValue: PlaybackState(stepCount: frames.count, speedMs: 700))
    }

    private func ink(_ tone: GS?) -> Color? {
        switch tone {
        case .active?: return scheme == .dark ? SimColors.active : Color(hex: 0xB7791F)
        case .cut?: return scheme == .dark ? Color(hex: 0xA78BFA) : cutColor
        case .warn?: return scheme == .dark ? Color(hex: 0xF28B82) : SimColors.red
        case nil: return nil
        case let t?: return gsColor(t)
        }
    }

    var body: some View {
        let story = frames[min(playback.index, frames.count - 1)].story!
        var present = Set<GS>(story.nodes.values).union(story.edges.values)
        for row in story.rows { for g in row { g.cells.forEach { present.insert($0.tone) } } }
        if !story.missing.isEmpty { present.insert(.warn) }
        story.lines.forEach { present.insert($0.tone) }
        let legend = story.legend.filter { present.contains($0.0) }
        return VStack(alignment: .leading, spacing: 0) {
            LabCard {
                if tabs.isEmpty {
                    HStack {
                        Text(story.title).font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted).lineLimit(1)
                        Spacer(minLength: 12)
                        if !story.note.isEmpty { Text(story.note).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1) }
                    }
                } else {
                    GraphTabs(labels: tabs, selected: $tab)
                }
                GraphStoryCanvas(def: def, story: story).padding(.top, 12)
                ForEach(story.rows.indices, id: \.self) { GraphCellRow(groups: story.rows[$0]) }
                if !story.lines.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(story.lines.indices, id: \.self) { i in
                            let line = story.lines[i]
                            HStack {
                                Text(line.test).foregroundStyle(palette.onSurface)
                                Spacer(minLength: 8)
                                Text(line.verdict).foregroundStyle(ink(line.tone) ?? palette.muted)
                            }
                            .font(.system(size: 13, design: .monospaced)).lineLimit(1)
                            .padding(.horizontal, 12).frame(height: 40)
                            .background(palette.muted.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    .padding(.top, 12)
                }
                if !legend.isEmpty {
                    FlowLayout(spacing: 14, lineSpacing: 6) {
                        ForEach(legend.indices, id: \.self) { i in
                            HStack(spacing: 6) {
                                if legend[i].0 == .warn {
                                    RoundedRectangle(cornerRadius: 1).fill(SimColors.red).frame(width: 12, height: 2)
                                } else {
                                    RoundedRectangle(cornerRadius: 3).fill(gsColor(legend[i].0) ?? palette.muted).frame(width: 10, height: 10)
                                }
                                Text(legend[i].1).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
                            }
                        }
                    }
                    .padding(.top, 14)
                }
            }
            if !story.chips.isEmpty {
                FlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(story.chips.indices, id: \.self) { i in
                        let chip = story.chips[i]
                        HStack(spacing: 8) {
                            Text(chip.key).foregroundStyle(palette.muted)
                            Text(chip.value).fontWeight(.bold).foregroundStyle(ink(chip.tone) ?? palette.onSurface)
                        }
                        .font(AppFont.mono(15)).lineLimit(1)
                        .padding(.horizontal, 12).frame(height: 32)
                        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
                    }
                }
                .padding(.top, 16)
            }
            graphSpans(story.headline).reduce(Text("")) { out, span in
                guard let mark = span.mark else { return out + Text(span.text) }
                let tone: GS = mark == "w" ? .warn : mark == "a" ? .active : mark == "p" ? .path : mark == "m" ? .done : mark == "c" ? .cut : story.emphasis
                return out + Text(span.text).foregroundColor(ink(tone) ?? SimColors.active)
            }
            .font(AppFont.sans(20, .bold))
            .foregroundStyle(palette.onSurface)
            .padding(.top, 16)
            Text(story.body).font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3).padding(.top, 8)
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }
}

private struct GraphTabs: View {
    let labels: [String]
    @Binding var selected: Int
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(spacing: 0) {
            ForEach(labels.indices, id: \.self) { i in
                let on = i == selected
                Text(labels[i])
                    .font(AppFont.sans(13, on ? .semibold : .medium))
                    .foregroundStyle(on ? palette.onSurface : palette.muted)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity).frame(height: 30)
                    .background(on ? (scheme == .dark ? Color(hex: 0x636366) : .white) : .clear, in: RoundedRectangle(cornerRadius: 7))
                    .contentShape(Rectangle())
                    .onTapGesture { selected = i }
            }
        }
        .padding(2)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
    }
}

/// One row of labelled cell groups sharing the width: every cell is the same size, and a wider gap
/// separates groups.
private struct GraphCellRow: View {
    let groups: [GCellGroup]
    @Environment(\.palette) private var palette

    var body: some View {
        let total = CGFloat(groups.reduce(0) { $0 + $1.cells.count })
        GeometryReader { geo in
            let avail = geo.size.width - CGFloat(groups.count - 1) * 14
            HStack(alignment: .top, spacing: 14) {
                ForEach(groups.indices, id: \.self) { gi in
                    let g = groups[gi]
                    VStack(alignment: .leading, spacing: 8) {
                        if groups.contains(where: { $0.label != nil }) {
                            HStack {
                                Text(g.label ?? "").font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).lineLimit(1)
                                Spacer(minLength: 4)
                                if let note = g.note { Text(note).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1) }
                            }
                        }
                        HStack(spacing: 6) {
                            ForEach(g.cells.indices, id: \.self) { ci in cell(g.cells[ci]) }
                        }
                        .frame(height: 40)
                    }
                    .frame(width: avail * CGFloat(g.cells.count) / total)
                }
            }
        }
        .frame(height: groups.contains(where: { $0.label != nil }) ? 70 : 40)
        .padding(.top, 14)
    }

    private func cell(_ c: GCell) -> some View {
        let shape = RoundedRectangle(cornerRadius: 10)
        let fill = gsColor(c.tone)
        return Text(c.text)
            .font(.system(size: c.text.count > 4 ? 13 : 15, weight: .bold, design: .monospaced))
            .foregroundStyle(c.tone == .active ? Color(hex: 0x1F1A0A) : fill != nil ? .white : palette.onSurface)
            .lineLimit(1).minimumScaleFactor(0.6)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(c.tone == .empty ? Color.clear : (fill ?? palette.muted.opacity(0.2)), in: shape)
            .overlay { if c.tone == .empty { shape.stroke(palette.muted.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [4, 3])) } }
    }
}

private struct GraphStoryCanvas: View {
    let def: GraphDef
    let story: GraphStory
    @Environment(\.palette) private var palette

    var body: some View {
        let hasReverse = def.edges.map { e in def.edges.contains { $0.from == e.to && $0.to == e.from } }
        let palette = self.palette
        return Canvas { ctx, size in
            let radius: CGFloat = 16, padX: CGFloat = 22, padY: CGFloat = 30
            var pos: [String: CGPoint] = [:]
            for node in def.nodes { pos[node.id] = CGPoint(x: padX + node.x * (size.width - 2 * padX), y: padY + node.y * (size.height - 2 * padY)) }
            func unit(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
                let dx = b.x - a.x, dy = b.y - a.y, len = max(sqrt(dx * dx + dy * dy), 0.001)
                return CGPoint(x: dx / len, y: dy / len)
            }
            for (a, b) in story.missing {
                let p = pos[a]!, q = pos[b]!, u = unit(p, q)
                var line = Path()
                line.move(to: CGPoint(x: p.x + u.x * radius, y: p.y + u.y * radius))
                line.addLine(to: CGPoint(x: q.x - u.x * radius, y: q.y - u.y * radius))
                ctx.stroke(line, with: .color(SimColors.red), style: StrokeStyle(lineWidth: 2, dash: [6, 5]))
            }
            for (i, edge) in def.edges.enumerated() {
                let flip = story.reversed && edge.directed
                let from = pos[flip ? edge.to : edge.from]!, to = pos[flip ? edge.from : edge.to]!
                let u = unit(from, to)
                let shift: CGFloat = hasReverse[i] ? 7 : 0
                let off = CGPoint(x: -u.y * shift, y: u.x * shift)
                let start = CGPoint(x: from.x + u.x * radius + off.x, y: from.y + u.y * radius + off.y)
                let pull = radius + (edge.directed ? 2 : 0)
                let end = CGPoint(x: to.x - u.x * pull + off.x, y: to.y - u.y * pull + off.y)
                let tone = story.edges[i] ?? .idle
                let color = gsColor(tone) ?? palette.muted.opacity(0.45)
                var line = Path()
                line.move(to: start); line.addLine(to: end)
                ctx.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: tone == .idle ? 1.5 : 3, lineCap: .round))
                if edge.directed {
                    let head: CGFloat = 9, angle = atan2(u.y, u.x)
                    var arrow = Path()
                    arrow.move(to: end)
                    for spread in [2.7, -2.7] { arrow.addLine(to: CGPoint(x: end.x + head * cos(angle + spread), y: end.y + head * sin(angle + spread))) }
                    arrow.closeSubpath()
                    ctx.fill(arrow, with: .color(color))
                }
                guard let text = story.edgeLabels[i] ?? edge.weight.map(String.init) else { continue }
                if story.boxed.contains(i) {
                    let label = ctx.resolve(Text(text).font(.system(size: 12, weight: .semibold, design: .monospaced)).foregroundStyle(tone == .idle ? palette.onSurface : color))
                    let m = label.measure(in: CGSize(width: 100, height: 30))
                    let mid = CGPoint(x: (start.x + end.x) / 2, y: (start.y + end.y) / 2)
                    let box = Path(roundedRect: CGRect(x: mid.x - m.width / 2 - 5, y: mid.y - m.height / 2 - 2, width: m.width + 10, height: m.height + 4), cornerRadius: 5)
                    ctx.fill(box, with: .color(palette.surface))
                    ctx.stroke(box, with: .color(tone == .idle ? palette.muted.opacity(0.5) : color), lineWidth: 1)
                    ctx.draw(label, at: mid)
                } else {
                    // 40% along rather than halfway, so two edges crossing at their midpoints keep their weights apart.
                    let label = ctx.resolve(Text(text).font(.system(size: 12, weight: .semibold, design: .monospaced)).foregroundStyle(palette.muted))
                    let at = CGPoint(x: start.x + (end.x - start.x) * 0.4, y: start.y + (end.y - start.y) * 0.4)
                    ctx.draw(label, at: CGPoint(x: at.x - u.y * 10, y: at.y + u.x * 10))
                }
            }
            for node in def.nodes {
                let c = pos[node.id]!
                let tone = story.nodes[node.id] ?? .idle
                let fill = gsColor(tone)
                let circle = Path(ellipseIn: CGRect(x: c.x - radius, y: c.y - radius, width: 2 * radius, height: 2 * radius))
                ctx.fill(circle, with: .color(palette.surface))
                ctx.fill(circle, with: .color(fill ?? palette.muted.opacity(0.3)))
                let label = ctx.resolve(Text(node.id).font(AppFont.sans(13, .bold))
                    .foregroundStyle(tone == .active ? Color(hex: 0x1F1A0A) : fill != nil ? .white : palette.onSurface))
                ctx.draw(label, at: c)
                if let (text, badgeTone) = story.badges[node.id] {
                    let badge = ctx.resolve(Text(text).font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundStyle(gsColor(badgeTone) ?? palette.muted))
                    if node.y < 0.3 {
                        ctx.draw(badge, at: CGPoint(x: c.x, y: c.y - radius - 4), anchor: .bottom)
                    } else {
                        ctx.draw(badge, at: CGPoint(x: c.x, y: c.y + radius + 4), anchor: .top)
                    }
                }
            }
        }
        .frame(height: 210)
    }
}
