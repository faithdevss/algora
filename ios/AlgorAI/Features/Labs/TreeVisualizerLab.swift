import SwiftUI

// Port of TreeVisualizerSection.kt: one player for every tree-shaped structure. Each frame carries
// its own node list, so insertions, rotations and B-tree splits are just a different set of parent
// links. Layout is the leaf-slot algorithm: leaves take sequential x slots, parents centre over
// their children, siblings ordered by `order`.

/// Skipped is a node the step decided not to visit (a segment-tree range outside the query): dimmer than idle.
/// Warn is a key that broke a rule (the third key in a B-tree node that holds two).
/// Ghost is a node or bar that is not there yet (where an insert lands, the next bar a walk reads);
/// answer is a sparse table's result cell.
private enum TreeState: CaseIterable { case idle, path, active, marked, skipped, warn, ghost, answer }

private struct KeyCell { let value: String; var state: TreeState = .idle }

/// `sub` is a small caption above the label (a segment-tree node's range, "0..3"); `edge` the substring on
/// the edge down from the parent; `keys` makes a multi-key node padded to `capacity` empty slots, and
/// `overflow` draws it overfull with that line under it.
private struct TreeNodeSpec {
    let id: Int; let label: String; let parent: Int?; let order: Int; let state: TreeState
    var sub: String? = nil
    var edge: String? = nil
    var keys: [KeyCell]? = nil
    var capacity = 0
    var overflow: String? = nil
    /// A small circle on the top-right corner (visit order, heap index, balance factor), ringed in
    /// `badgeTone`'s colour.
    var badge: String? = nil
    var badgeTone: TreeState = .idle
    /// A green ring round the tile: a trie node that ends a word.
    var ring = false
    /// A line under the tile in the node's colour (tree DP's "take / skip").
    var below: String? = nil
}
private struct TreeLink { let from: Int; let to: Int }
private struct TreeFrame { let nodes: [TreeNodeSpec]; let status: String; var links: [TreeLink] = []; var story: TreeStory? = nil }

private struct TreeConfig {
    let intro: String
    let markedLabel: String
    let build: () -> [TreeFrame]
    var linkLabel: String?
    /// Non-nil is a hand-written storyboard's style; nil gets the default story card.
    var storyStyle: StoryStyle? = nil
    /// Tabs over the story card, each its own storyboard (B-Tree / B+ Tree).
    var variants: [StoryVariant] = []
    /// The card header for topics without a hand-written storyboard.
    var cardTitle = ""
    var cardNote = ""
}

/// One tab of a story card. `style` overrides the config's when the tab needs other legend words.
private struct StoryVariant { let label: String; let build: () -> [TreeFrame]; var style: StoryStyle? = nil }

// MARK: - Story card model
// Every tree lab (docs mocks): a header naming the operation, the tree, an array that mirrors it cell
// for cell, a legend of only what is on screen, then value chips and a headline whose key term takes
// its node's colour.

private enum LegendKey: Hashable { case active, path, marked, skipped, link, next, overflow, warn, ring, ghost, answer }

private struct StoryStyle {
    let canvasHeight: CGFloat
    let rowGap: CGFloat
    /// In display order; an entry shows only while its colour is on screen.
    let legend: [(LegendKey, String)]
    var noteMono = false
    /// LCA's d0…dN row labels and dashed rules, so "equal depth" is something you can see.
    var depthGuides = false
    /// Array cells wide enough for the values to breathe (4 cells) instead of 8 squares.
    var wideCells = false
    /// "Insert 5 of 7" rather than "Step 5 of 7".
    var stepNoun = "Step"
}

/// A blank value draws an empty slot (a heap array's unused capacity).
private struct StripCell { let header: String; let value: String; var state: TreeState = .idle }

private enum RowState { case done, current, pending }

/// One line of a list under the tree: "[2]  a$  inserting".
private struct StoryRow { let index: String; let text: String; let status: String; let state: RowState }

private struct TreeStory {
    let title: String
    let note: String
    let stripLabel: String?
    let cells: [StripCell]
    let chips: [(key: String?, value: String)]
    /// `{…}` marks the term drawn in `emphasis`'s colour.
    let headline: String
    let emphasis: TreeState
    let body: String
    var accentedChips: Set<Int> = []
    var warnedChips: Set<Int> = []
    var positiveChips: Set<Int> = []
    /// Children whose edge to their parent is the next move, drawn dashed in the current colour.
    var nextEdges: Set<Int> = []
    /// Children whose edge to their parent takes a state's colour, overriding the both-ends-lit rule.
    var edgeStates: [Int: TreeState] = [:]
    /// The query range, bracketed under the array.
    var bracket: ClosedRange<Int>? = nil
    var focusDepth: Int? = nil
    var footnote: String? = nil
    var rows: [StoryRow] = []
    var rowColumns = 2
    /// B+ leaves are a linked list; draw the arrows between neighbours.
    var leafChain = false
    /// Generic stories reuse a status line, which may carry **bold** / *italic* markdown.
    var markdown = false
    /// Words on the right of the strip's label ("3 of 6", "1 new node").
    var stripNote: String? = nil
    /// Empty strip cells drawn as dashed slots still to fill (a visit order).
    var dashedEmpty = false
    /// A label and note over `rows` ("COMPARISONS · left < node < right").
    var rowsLabel: String? = nil
    var rowsNote: String? = nil
    /// The suffix list outlines its current row; the BST's comparisons only tint it.
    var rowBorder = true
    /// Status words in the row's text colour rather than green (a comparison's "go right").
    var plainStatus = false
    /// Lay a binary tree out by in-order position, so a lone right child still sits to the right.
    var inorder = false
    /// One title per root, drawn side by side with an arrow between (BEFORE → AFTER).
    var panels: [String] = []
    /// Edges stay plain whatever their ends' states (a traversal's order is on the badges).
    var plainEdges = false
    /// Badge text in its tone's colour (a balance factor) rather than the text colour (a visit number).
    var badgeInk = false
    /// Rows of cells laid on a column grid instead of a tree (Fenwick bars, a sparse table).
    var grid: [StoryGridBlock] = []
    /// Per-step legend words, over the style's.
    var legendLabels: [LegendKey: String] = [:]
    /// Labelled lines of arithmetic under the tree ("take B   2 + 0 + 0 = 2").
    var formulaRows: [StoryFormulaRow] = []

    var status: String { headlineSpans(headline).map(\.text).joined() + (body.isEmpty ? "" : " " + body) }
}

/// `{…}` in a headline takes the story's emphasis colour; `{w:…}` red, `{a:…}` yellow, `{p:…}` blue and
/// `{m:…}` green name the colour outright, for a headline that points at two different nodes.
private func headlineSpans(_ text: String) -> [(text: String, mark: Character?)] {
    var out: [(String, Character?)] = []
    var rest = Substring(text)
    while let open = rest.firstIndex(of: "{"), let close = rest[open...].firstIndex(of: "}") {
        out.append((String(rest[..<open]), nil))
        var inner = rest[rest.index(after: open)..<close]
        var mark: Character = "*"
        if inner.count > 2, let first = inner.first, "wapm".contains(first), inner.dropFirst().first == ":" {
            mark = first
            inner = inner.dropFirst(2)
        }
        out.append((String(inner), mark))
        rest = rest[rest.index(after: close)...]
    }
    out.append((String(rest), nil))
    return out
}

/// One cell on a grid row: `span` columns from `start`, or an empty slot when `text` is blank.
private struct StoryGridCell { let start: Int; let span: Int; let text: String; var state: TreeState = .idle }

/// `label` / `sub` sit in a gutter on the left ("k2" over "len 4"); `labelState` colours them.
private struct StoryGridRow { let cells: [StoryGridCell]; var label: String? = nil; var sub: String? = nil; var labelState: TreeState = .idle }

private struct StoryGridBlock { let title: String?; let columns: Int; let rows: [StoryGridRow]; var headers: [String]? = nil; var rowHeight: CGFloat = 36 }

private let pathColor = SimColors.blue
private let activeColor = SimColors.active
private let markedColor = SimColors.green
/// Dark text on the yellow current pill: white on #F5C542 is unreadable.
private let onActiveColor = Color(hex: 0x1F1A0A)
private let linkColor = Color(hex: 0xF97316)

/// Mutable tree the builders mutate; each `frame()` call snapshots it (insertion-ordered).
private final class TreeBuilder {
    private struct Node { let id: Int; var label: String; var parent: Int?; var order: Int; var sub: String? = nil; var edge: String? = nil }
    private var nodes: [Node] = []
    private var nextId = 0
    var frames: [TreeFrame] = []

    @discardableResult
    func add(_ label: String, _ parent: Int?, _ order: Int, sub: String? = nil, edge: String? = nil) -> Int {
        let id = nextId
        nextId += 1
        nodes.append(Node(id: id, label: label, parent: parent, order: order, sub: sub, edge: edge))
        return id
    }

    private func index(_ id: Int) -> Int { nodes.firstIndex { $0.id == id }! }
    func relabel(_ id: Int, _ label: String) { nodes[index(id)].label = label }
    func reparent(_ id: Int, _ parent: Int?, _ order: Int) {
        let i = index(id)
        nodes[i].parent = parent
        nodes[i].order = order
    }
    func remove(_ id: Int) { nodes.removeAll { $0.id == id } }
    func setEdge(_ id: Int, _ edge: String?) { nodes[index(id)].edge = edge }
    func labelOf(_ id: Int) -> String { nodes[index(id)].label }
    func parentOf(_ id: Int) -> Int? { nodes.first { $0.id == id }?.parent }

    func frame(_ status: String, active: Set<Int> = [], path: Set<Int> = [], marked: Set<Int> = [], links: [TreeLink] = [],
               skipped: Set<Int> = [], story: TreeStory? = nil, ghost: Set<Int> = [], badges: [Int: (String, TreeState)] = [:],
               rings: Set<Int> = [], below: [Int: String] = [:]) {
        frames.append(TreeFrame(nodes: nodes.map { n in
            let state: TreeState = ghost.contains(n.id) ? .ghost : active.contains(n.id) ? .active : marked.contains(n.id) ? .marked
                : path.contains(n.id) ? .path : skipped.contains(n.id) ? .skipped : .idle
            return TreeNodeSpec(id: n.id, label: n.label, parent: n.parent, order: n.order, state: state, sub: n.sub, edge: n.edge,
                                badge: badges[n.id]?.0, badgeTone: badges[n.id]?.1 ?? .idle, ring: rings.contains(n.id),
                                below: below[n.id])
        }, status: story?.status ?? status, links: links, story: story))
    }
}

// MARK: - Binary search tree

private func bstFrames() -> [TreeFrame] { bstStoryFrames(0) }

/// Insert, search and delete on one small tree, three steps each. The comparison list is the whole
/// algorithm: one line per level, each ending in which way to go.
private func bstStoryFrames(_ kind: Int) -> [TreeFrame] {
    let b = TreeBuilder()
    let n50 = b.add("50", nil, 0)
    let n30 = b.add("30", n50, 0)
    let n70 = b.add("70", n50, 1)
    b.add("20", n30, 0)
    let n40 = b.add("40", n30, 1)

    func row(_ text: String, _ status: String, _ current: Bool) -> StoryRow {
        StoryRow(index: "", text: text, status: status, state: current ? .current : .done)
    }
    func story(_ rows: [StoryRow], _ chips: [(key: String?, value: String)], _ headline: String, _ body: String,
               next: Set<Int> = []) -> TreeStory {
        var s = TreeStory(title: "BINARY SEARCH TREE", note: "", stripLabel: nil, cells: [], chips: chips,
                          headline: headline, emphasis: .active, body: body, nextEdges: next, rows: rows, rowColumns: 1)
        s.rowsLabel = "COMPARISONS"
        s.rowsNote = "left < node < right"
        s.rowBorder = false
        s.plainStatus = true
        s.inorder = true
        return s
    }

    switch kind {
    case 0:
        let r1 = row("60 > 50", "go right", false)
        b.frame("", active: [n50], story: story([row("60 > 50", "go right", true)], [("key", "60"), ("depth", "0")],
                                                "60 is greater than {50}, so it goes right.",
                                                "Every insert starts at the root and makes one comparison per level."))
        let ghost = b.add("60", n70, 0)
        b.frame("", active: [n70], path: [n50], story: story([r1, row("60 < 70", "go left", true)], [("key", "60"), ("depth", "2")],
                                                             "60 is less than {70}, so it goes left.",
                                                             "70 has no left child, so 60 is inserted there on the next step.", next: [ghost]),
                ghost: [ghost])
        b.frame("", path: [n50, n70], marked: [ghost],
                story: story([r1, row("60 < 70", "go left", false), row("70.left is empty", "insert", true)], [("key", "60"), ("depth", "2")],
                             "60 becomes 70's left {m:child}.",
                             "No existing node moved. The tree still reads 20, 30, 40, 50, 60, 70 in order."))
    case 1:
        b.add("60", n70, 0)
        b.frame("", active: [n50], story: story([row("40 < 50", "go left", true)], [("key", "40"), ("checked", "1")],
                                                "40 is less than {50}, so search left.",
                                                "Everything bigger than 50 is on the right, so half the tree is ruled out at once."))
        b.frame("", active: [n30], path: [n50], story: story([row("40 < 50", "go left", false), row("40 > 30", "go right", true)],
                                                             [("key", "40"), ("checked", "2")],
                                                             "40 is greater than {30}, so search right.",
                                                             "One comparison per level. The path only ever goes down."))
        b.frame("", path: [n50, n30], marked: [n40],
                story: story([row("40 < 50", "go left", false), row("40 > 30", "go right", false), row("40 = 40", "found", true)],
                             [("key", "40"), ("checked", "3")],
                             "Found {m:40} after 3 comparisons.",
                             "A search looks at one node per level, so a balanced tree answers in O(log n)."))
    default:
        b.add("60", n70, 0)
        b.frame("", active: [n30], path: [n50], story: story([row("30 < 50", "go left", false), row("30 = 30", "found", true)],
                                                             [("delete", "30"), ("children", "2")],
                                                             "{30} has two children, so it can't just be removed.",
                                                             "Removing it would leave 20 and 40 without a parent. It is replaced by its in-order successor."))
        b.frame("", active: [n40], path: [n30], story: story([row("30 = 30", "found", false), row("min of 30's right", "40", true)],
                                                             [("delete", "30"), ("successor", "40")],
                                                             "Its successor is {40}, the smallest key on its right.",
                                                             "The successor has at most one child, so it is easy to lift out."))
        b.remove(n40)
        b.relabel(n30, "40")
        b.frame("", marked: [n30],
                story: story([row("30 = 30", "found", false), row("min of 30's right", "40", false), row("40 replaces 30", "done", true)],
                             [("delete", "30"), ("moved", "1 node")],
                             "{m:40} takes 30's place, and the tree stays ordered.",
                             "In order it still reads 20, 40, 50, 60, 70. Only one key moved."))
    }
    return b.frames
}

// MARK: - General tree: depth-first then breadth-first

private func treeTraversalFrames() -> [TreeFrame] { traversalFrames(0) }

/// A small directory tree walked three ways. Each step visits one node: its badge is its place in the
/// order, the strip fills left to right, and the stack chip is the path the recursion is standing on.
private func traversalFrames(_ kind: Int) -> [TreeFrame] {
    let b = TreeBuilder()
    let root = b.add("/", nil, 0)
    let usr = b.add("usr", root, 0)
    let etc = b.add("etc", root, 1)
    let bin = b.add("bin", usr, 0)
    let lib = b.add("lib", usr, 1)
    let conf = b.add("conf", etc, 0)
    let parent = [usr: root, etc: root, bin: usr, lib: usr, conf: etc]

    let orderChip: String
    let sequence: [Int]
    let lines: [(String, String)]
    switch kind {
    case 0:
        orderChip = "L → N → R"
        sequence = [bin, usr, lib, root, conf, etc]
        lines = [
            ("Visit {bin} first: it is the leftmost node.", "Inorder goes left as far as it can before visiting anything."),
            ("Visit {usr}. Its left side, bin, is finished.", "Now the node itself, then its right subtree."),
            ("Visit {lib}. usr came before it because its left side was finished.",
             "Inorder visits left, then the node, then right. On a search tree this reads out sorted order."),
            ("Visit {/}. The whole left subtree is done.", "The root comes in the middle of inorder, not at the start."),
            ("Visit {conf}, the leftmost node on the right side.", "etc has a left child, so conf goes before etc."),
            ("Visit {etc} last.", "Left, node, right at every level gives bin, usr, lib, /, conf, etc."),
        ]
    case 1:
        orderChip = "N → L → R"
        sequence = [root, usr, bin, lib, etc, conf]
        lines = [
            ("Visit {/} first: preorder starts at the root.",
             "Every node is visited before its children, the way a listing prints a folder before its files."),
            ("Visit {usr}, then go into its children.", "Preorder follows the left branch down before looking right."),
            ("Visit {bin}, a leaf.", "Nothing below it, so back up to usr's right child."),
            ("Visit {lib}. usr's subtree is finished.", "Next the walk returns to the root's right side."),
            ("Visit {etc}, the root's right child.", "Its child comes after it, as always in preorder."),
            ("Visit {conf} last.", "Preorder gave /, usr, bin, lib, etc, conf: every parent before its children. Copying a tree uses this order."),
        ]
    default:
        orderChip = "L → R → N"
        sequence = [bin, lib, usr, conf, etc, root]
        lines = [
            ("Visit {bin} first: postorder starts at the deepest left leaf.", "A node waits until all of its children are done."),
            ("Visit {lib}, usr's other child.", "With both children done, usr can go next."),
            ("Visit {usr} now that bin and lib are done.", "Children always come before their parent in postorder."),
            ("Visit {conf}, a leaf on the right side.", "The root still waits for its whole right subtree."),
            ("Visit {etc}. Its only child is done.", "Only the root is left."),
            ("Visit {/} last.", "Postorder gave bin, lib, usr, conf, etc, /. Deleting a folder needs this order: files before the folder."),
        ]
    }

    for (step, current) in sequence.enumerated() {
        let done = Array(sequence.prefix(step))
        var chain = [current]
        while let p = parent[chain.last!] { chain.append(p) }
        let stack = chain.reversed().map(b.labelOf).joined(separator: " › ")
        var badges: [Int: (String, TreeState)] = [current: ("\(step + 1)", .active)]
        for (i, id) in done.enumerated() { badges[id] = ("\(i + 1)", .marked) }
        let cells: [StripCell] = sequence.indices.map { i in
            i < step ? StripCell(header: "", value: b.labelOf(sequence[i]), state: .marked)
                : i == step ? StripCell(header: "", value: b.labelOf(sequence[i]), state: .active)
                : StripCell(header: "", value: "", state: .skipped)
        }
        var story = TreeStory(title: "TRAVERSAL", note: "", stripLabel: "VISIT ORDER", cells: cells,
                              chips: [("order", orderChip), ("stack", stack)], headline: lines[step].0, emphasis: .active, body: lines[step].1)
        story.stripNote = "\(step + 1) of \(sequence.count)"
        story.dashedEmpty = true
        story.inorder = true
        story.plainEdges = true
        b.frame("", active: [current], marked: Set(done), story: story, badges: badges)
    }
    return b.frames
}

// MARK: - Binary max-heap

private func heapFrames() -> [TreeFrame] { heapStoryFrames(max: true) }

/// One insert and its sift-up, compared a level at a time. The badges are array indices, so the tree
/// and the backing array can be read against each other.
private func heapStoryFrames(max: Bool) -> [TreeFrame] {
    var heap = max ? [12, 9, 3, 5] : [2, 5, 4, 9]
    let value = max ? 20 : 1
    let word = max ? "bigger" : "smaller"
    var frames: [TreeFrame] = []
    heap.append(value)

    func frame(_ active: Set<Int>, _ marked: Set<Int>, _ compare: Int?, _ chips: [(key: String?, value: String)],
               _ headline: String, _ body: String, emphasis: TreeState = .active) {
        func stateOf(_ i: Int) -> TreeState { active.contains(i) ? .active : marked.contains(i) ? .marked : .idle }
        let nodes = heap.indices.map { i in
            TreeNodeSpec(id: i, label: "\(heap[i])", parent: i == 0 ? nil : (i - 1) / 2, order: i % 2 == 1 ? 0 : 1, state: stateOf(i),
                         badge: active.contains(i) ? "\(i)" : nil)
        }
        var story = TreeStory(title: max ? "MAX-HEAP" : "MIN-HEAP", note: "", stripLabel: "BACKING ARRAY",
                              cells: heap.indices.map { StripCell(header: "\($0)", value: "\(heap[$0])", state: stateOf($0)) },
                              chips: chips, headline: headline, emphasis: emphasis, body: body,
                              edgeStates: compare.map { [$0: TreeState.active] } ?? [:])
        story.stripNote = "parent(i) = ⌊(i−1)/2⌋"
        story.plainEdges = compare == nil
        frames.append(TreeFrame(nodes: nodes, status: story.status, story: story))
    }

    let p1 = heap[1]
    frame([4], [], nil, [("i", "4"), ("parent", "1")],
          "Insert {\(value)} at the next free slot, index 4.",
          "The tree stays complete: the bottom row fills left to right. Now \(value) may be out of order.")
    frame([1, 4], [], 4, [("i", "4"), ("parent", "1")],
          "{\(value)} is \(word) than its parent {\(p1)}, so they swap.",
          "Then \(value) is compared with \(heap[0]). Sift-up stops once the parent is \(max ? "larger" : "smaller").")
    heap.swapAt(1, 4)
    let p0 = heap[0]
    frame([0, 1], [], 1, [("i", "1"), ("parent", "0")],
          "{\(value)} is \(word) than {\(p0)} too, so they swap again.",
          "\(value) has climbed one level. The root has no parent, so this is the last comparison.")
    heap.swapAt(0, 1)
    frame([], [0], nil, [("i", "0"), ("swaps", "2")],
          "{\(value)} reaches the root and stops.",
          "Two swaps, one per level, so an insert costs O(log n). The \(max ? "largest" : "smallest") value is always at index 0.",
          emphasis: .marked)
    return frames
}

// MARK: - Trie

private func trieFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let root = b.add("•", nil, 0)
    let c = b.add("c", root, 0)
    let a = b.add("a", c, 0)
    let t = b.add("t", a, 0)

    func story(_ word: String, _ cells: [TreeState], _ note: String, _ chips: [(key: String?, value: String)],
               _ headline: String, _ body: String, _ edges: [Int: TreeState], emphasis: TreeState = .active) -> TreeStory {
        var s = TreeStory(title: "INSERT \"\(word)\"", note: "shared prefixes", stripLabel: "WORD",
                          cells: word.enumerated().map { StripCell(header: "", value: String($1), state: cells[$0]) },
                          chips: chips, headline: headline, emphasis: emphasis, body: body, edgeStates: edges)
        s.stripNote = note
        return s
    }

    b.frame("", active: [c, a, t],
            story: story("cat", [.active, .active, .active], "3 new nodes", [("stored", "none"), ("prefix", "\"\"")],
                         "The trie is empty, so every letter of \"cat\" is {new}.",
                         "Each node is one character. The path from the root spells the prefix.", [c: .active, a: .active, t: .active]))
    b.frame("", story: story("cat", [.idle, .idle, .idle], "end of word", [("stored", "cat"), ("prefix", "\"cat\"")],
                             "t is marked as the {m:end of a word}.",
                             "Without the mark, \"ca\" would look like a stored word too.", [c: .idle]), rings: [t])
    b.frame("", active: [c], story: story("car", [.active, .idle, .idle], "following c", [("stored", "cat"), ("prefix", "\"c\"")],
                                          "\"car\" starts with {c}, which already exists.",
                                          "Inserting walks down from the root and only creates a node when the letter is missing.", [c: .path]),
            rings: [t])
    let r = b.add("r", a, 1)
    b.frame("", active: [r], path: [root, c, a],
            story: story("car", [.path, .path, .active], "1 new node", [("stored", "cat"), ("prefix", "\"ca\"")],
                         "\"c\" and \"a\" already exist. Only {r} is new.",
                         "Words with the same prefix share nodes. Next, r gets marked as the end of a word.", [c: .path, a: .path, r: .active]),
            rings: [t])
    b.frame("", story: story("car", [.idle, .idle, .idle], "end of word", [("stored", "cat, car"), ("letters", "4 of 6")],
                             "Both words fit in {m:4} letter nodes instead of 6.",
                             "r is marked as an end of word. A lookup for \"ca\" walks 2 nodes and finds no mark, so it is only a prefix.",
                             [c: .idle], emphasis: .marked), rings: [t, r])
    return b.frames
}

// MARK: - AVL / segment tree / B-tree

private func avlFrames() -> [TreeFrame] { balanceFrames(redBlack: false) }

/// Three ascending inserts, the third of which breaks the rule; that step shows the tree before and
/// after its rotation side by side. The badges carry the rule itself: a balance factor, or a colour.
private func balanceFrames(redBlack: Bool) -> [TreeFrame] {
    var frames: [TreeFrame] = []
    let tone: [String: TreeState] = ["0": .marked, "-1": .idle, "-2": .warn, "B": .idle, "R": .warn]
    func node(_ id: Int, _ label: String, _ parent: Int?, _ order: Int, _ state: TreeState, _ badge: String) -> TreeNodeSpec {
        TreeNodeSpec(id: id, label: label, parent: parent, order: order, state: state, badge: badge, badgeTone: tone[badge]!)
    }
    func frame(_ nodes: [TreeNodeSpec], _ chips: [(key: String?, value: String)], _ headline: String, _ body: String,
               emphasis: TreeState = .active, panels: [String] = [], warned: Set<Int> = [], activeWord: String) {
        var story = TreeStory(title: redBlack ? "RED-BLACK" : "AVL", note: "", stripLabel: nil, cells: [], chips: chips,
                              headline: headline, emphasis: emphasis, body: body, warnedChips: warned)
        story.inorder = true
        story.panels = panels
        story.plainEdges = true
        story.badgeInk = true
        story.legendLabels = [.active: activeWord]
        frames.append(TreeFrame(nodes: nodes, status: story.status, story: story))
    }
    if !redBlack {
        frame([node(0, "10", nil, 0, .active, "0")], [("bf(10)", "0"), ("height", "1")],
              "Insert {10}. A single node is balanced.", "A node's balance factor is its left height minus its right height.",
              activeWord: "Inserted")
        frame([node(0, "10", nil, 0, .idle, "-1"), node(1, "20", 0, 1, .active, "0")], [("bf(10)", "-1"), ("height", "2")],
              "Insert {20}. 10 now leans right by one.", "AVL allows balance factors of −1, 0 and +1, so nothing needs fixing yet.",
              activeWord: "Inserted")
        frame([node(0, "10", nil, 0, .warn, "-2"), node(1, "20", 0, 1, .active, "-1"), node(2, "30", 1, 1, .idle, "0"),
               node(3, "20", nil, 1, .marked, "0"), node(4, "10", 3, 0, .marked, "0"), node(5, "30", 3, 1, .marked, "0")],
              [("bf(10)", "-2"), ("height", "3 → 2")],
              "{w:10} is right-heavy, so the tree rotates left around {20}.",
              "20 moves up and 10 becomes its left child. Without the rotation, the tree turns into a linked list.",
              panels: ["BEFORE", "AFTER"], warned: [0], activeWord: "Pivot")
        frame([node(0, "20", nil, 0, .marked, "0"), node(1, "10", 0, 0, .marked, "0"), node(2, "30", 0, 1, .marked, "0")],
              [("bf(20)", "0"), ("height", "2")],
              "Every node is balanced again: all factors are {0}.", "Height went from 3 to 2. One rotation keeps lookups at O(log n).",
              emphasis: .marked, activeWord: "Pivot")
    } else {
        frame([node(0, "10", nil, 0, .active, "B")], [("black height", "1")],
              "Insert {10}. The root is always black.",
              "Each node carries one colour bit. Rules on those colours keep the tree roughly balanced.", activeWord: "Inserted")
        frame([node(0, "10", nil, 0, .idle, "B"), node(1, "20", 0, 1, .active, "R")], [("black height", "1"), ("20", "red")],
              "Insert {20} as red, under the black root.", "A new node is always red. A red child of a black parent breaks no rule.",
              activeWord: "Inserted")
        frame([node(0, "10", nil, 0, .idle, "B"), node(1, "20", 0, 1, .active, "R"), node(2, "30", 1, 1, .warn, "R"),
               node(3, "20", nil, 1, .marked, "B"), node(4, "10", 3, 0, .marked, "R"), node(5, "30", 3, 1, .marked, "R")],
              [("red-red", "20 → 30"), ("rotate", "left at 10")],
              "{w:30} is red under red {20}, so rotate left and recolor.",
              "20 moves up and turns black, 10 turns red. Now no red node has a red child.",
              panels: ["BEFORE", "AFTER"], warned: [0], activeWord: "Pivot")
        frame([node(0, "20", nil, 0, .marked, "B"), node(1, "10", 0, 0, .marked, "R"), node(2, "30", 0, 1, .marked, "R")],
              [("black height", "1"), ("rotations", "1")],
              "Every path from the root now passes {1} black node.",
              "Red-black trees allow more slack than AVL, so inserts rotate less often, while height stays under 2·log n.",
              emphasis: .marked, activeWord: "Pivot")
    }
    return frames
}

// MARK: - Fenwick tree: prefix query and point update over one array

private let fenwickA = [3, 1, 5, 4, 2, 9, 2, 6]

private func fenwickStoryFrames(update: Bool) -> [TreeFrame] {
    var a = fenwickA
    func low(_ i: Int) -> Int { i & -i }
    func tree(_ i: Int) -> Int { (i - low(i) + 1...i).reduce(0) { $0 + a[$1 - 1] } }
    var frames: [TreeFrame] = []

    func frame(_ states: [Int: TreeState], _ cellStates: [Int: TreeState], _ chips: [(key: String?, value: String)],
               _ headline: String, _ body: String, emphasis: TreeState = .active, accented: Set<Int> = []) {
        func bar(_ i: Int) -> StoryGridCell {
            StoryGridCell(start: i - low(i), span: low(i), text: low(i) == 1 ? "\(tree(i))" : "T\(i) = \(tree(i))", state: states[i] ?? .idle)
        }
        let levels = [1, 2, 4, 8].map { size in (1...8).filter { low($0) == size } }
        var story = TreeStory(title: "FENWICK TREE", note: "", stripLabel: nil, cells: [], chips: chips,
                              headline: headline, emphasis: emphasis, body: body, accentedChips: accented)
        story.grid = [
            StoryGridBlock(title: "ARRAY A", columns: 8,
                           rows: [StoryGridRow(cells: (0..<8).map { StoryGridCell(start: $0, span: 1, text: "\(a[$0])", state: cellStates[$0 + 1] ?? .idle) })],
                           headers: (1...8).map(String.init), rowHeight: 40),
            StoryGridBlock(title: "TREE T · EACH BAR COVERS ITS RANGE", columns: 8,
                           rows: levels.map { StoryGridRow(cells: $0.map(bar)) }, rowHeight: 32),
        ]
        frames.append(TreeFrame(nodes: [], status: story.status, story: story))
    }

    if !update {
        frame([7: .active, 6: .ghost], [:], [("i", "7"), ("next", "7 − 1 = 6"), ("sum", "\(tree(7))")],
              "Add {T7 = \(tree(7))}. It covers only A[7].",
              "prefix(7) starts at i = 7. A bar's length is the lowest set bit of its index, here 1.")
        frame([7: .marked, 6: .active, 4: .ghost], [:], [("i", "6"), ("next", "6 − 2 = 4"), ("sum", "\(tree(7)) + \(tree(6)) = \(tree(7) + tree(6))")],
              "Add {T6 = \(tree(6))}. It covers A[5..6].",
              "Clearing the lowest bit takes 6 to 4. T4 covers A[1..4], so prefix(7) takes 3 reads.")
        let total = tree(7) + tree(6) + tree(4)
        frame([7: .marked, 6: .marked, 4: .active], [:], [("i", "4"), ("next", "4 − 4 = 0"), ("sum", "\(tree(7) + tree(6)) + \(tree(4)) = \(total)")],
              "Add {T4 = \(tree(4))}. It covers A[1..4].",
              "Clearing the lowest bit of 4 gives 0, so the walk ends here.")
        frame([7: .marked, 6: .marked, 4: .marked], Dictionary(uniqueKeysWithValues: (1...7).map { ($0, TreeState.path) }),
              [("prefix(7)", "\(total)"), ("reads", "3")],
              "prefix(7) = {m:\(total)} from 3 reads, not 7.",
              "Each step clears one bit of i, so any prefix sum takes at most log n reads.", accented: [0])
    } else {
        a[2] += 3
        frame([3: .active, 4: .ghost], [3: .active], [("A[3]", "5 + 3 = 8"), ("next", "3 + 1 = 4")],
              "Adding 3 to A[3] starts at {T3}.",
              "Every bar whose range contains index 3 must change, and there are only log n of them.")
        frame([3: .marked, 4: .active, 8: .ghost], [3: .active], [("T4", "13 + 3 = \(tree(4))"), ("next", "4 + 4 = 8")],
              "{T4} covers A[1..4], so it gains 3 too.",
              "Adding the lowest set bit jumps to the next bar that also covers index 3.")
        frame([3: .marked, 4: .marked, 8: .active], [3: .active], [("T8", "32 + 3 = \(tree(8))"), ("next", "8 + 8 = 16")],
              "{T8} covers everything, so it gains 3 as well.",
              "16 is past the end of the array, so the walk stops.")
        frame([3: .marked, 4: .marked, 8: .marked], [:], [("bars changed", "3"), ("of", "8")],
              "The update touched {m:3} bars out of 8.",
              "Like the query, the walk moves one bit per step, so an update costs O(log n) too.", emphasis: .marked)
    }
    return frames
}

// MARK: - Sparse table: every power-of-two block's min, then a query from two overlapping blocks

private func sparseTableFrames() -> [TreeFrame] {
    let a = [5, 2, 4, 7, 1, 3, 6, 8]
    var table = [a]
    while 1 << table.count <= a.count {
        let prev = table.last!
        let half = 1 << (table.count - 1)
        table.append((0...(a.count - (1 << table.count))).map { min(prev[$0], prev[$0 + half]) })
    }
    var frames: [TreeFrame] = []
    func frame(_ title: String, _ note: String, _ state: (Int, Int) -> TreeState, _ focusRow: Int?,
               _ chips: [(key: String?, value: String)], _ headline: String, _ body: String, accented: Set<Int> = []) {
        var story = TreeStory(title: title, note: note, stripLabel: nil, cells: [], chips: chips, headline: headline,
                              emphasis: .active, body: body, accentedChips: accented)
        story.grid = [StoryGridBlock(
            title: nil, columns: a.count,
            rows: table.enumerated().map { k, row in
                StoryGridRow(cells: row.enumerated().map { j, v in StoryGridCell(start: j, span: 1, text: "\(v)", state: state(k, j)) },
                             label: "k\(k)", sub: "len \(1 << k)", labelState: k == focusRow ? .active : .idle)
            },
            headers: a.indices.map(String.init), rowHeight: 38)]
        frames.append(TreeFrame(nodes: [], status: story.status, story: story))
    }
    let cells = table.reduce(0) { $0 + $1.count }
    frame("SPARSE TABLE", "n log n cells", { _, _ in .idle }, nil, [("rows", "\(table.count)"), ("cells", "\(cells)")],
          "Row k stores the min of every {length-2ᵏ block}.",
          "Each row is built from two blocks of the row above, so the whole table costs O(n log n) once.")
    let lo = 0, hi = 5, k = 2
    frame("RANGE MIN [\(lo), \(hi)]", "k = ⌊log₂ 6⌋ = \(k)", { r, j in r == 0 && (lo...hi).contains(j) ? .path : .idle }, k,
          [("length", "6"), ("k", "\(k)")],
          "The range has length 6, so use row {k = \(k)}.",
          "2² = 4 is the largest block that fits inside the range.")
    let second = hi - (1 << k) + 1
    let answer = min(table[k][lo], table[k][second])
    let at = (lo...hi).first { a[$0] == answer }!
    frame("RANGE MIN [\(lo), \(hi)]", "k = ⌊log₂ 6⌋ = \(k)", { r, j in
        if r == 0 && j == at { return .answer }
        if r == 0 && (lo...hi).contains(j) { return .path }
        if r == k && (j == lo || j == second) { return .active }
        return .idle
    }, k,
          [("blocks", "[\(lo)..\(lo + (1 << k) - 1)] [\(second)..\(hi)]"), ("min", "min(\(table[k][lo]), \(table[k][second])) = \(answer)")],
          "Two {length-4 blocks} cover [\(lo), \(hi)]. They overlap, which is fine for min.",
          "The answer is the smaller of the two, \(answer). Any range takes 2 reads.", accented: [1])
    return frames
}

private func segmentTreeFrames() -> [TreeFrame] {
    let data = [2, 1, 5, 3]
    let b = TreeBuilder()
    let root = b.add("\(data.reduce(0, +))", nil, 0, sub: "0..3")
    let left = b.add("\(data[0] + data[1])", root, 0, sub: "0..1")
    let right = b.add("\(data[2] + data[3])", root, 1, sub: "2..3")
    let leaves = data.indices.map { i in b.add("\(data[i])", i < 2 ? left : right, i % 2, sub: "\(i)") }
    let query = 1...3
    let total = data.reduce(0, +)
    let rightSum = data[2] + data[3]

    func story(_ cells: (Int) -> TreeState, _ chips: [(key: String?, value: String)], _ headline: String, _ body: String,
               accented: Set<Int> = []) -> TreeStory {
        TreeStory(title: "SUM QUERY [\(query.lowerBound)..\(query.upperBound)]", note: "node = range sum", stripLabel: "ARRAY",
                  cells: data.indices.map { StripCell(header: "\($0)", value: "\(data[$0])", state: cells($0)) },
                  chips: chips, headline: headline, emphasis: .active, body: body, accentedChips: accented, bracket: query)
    }

    b.frame("", story: story({ _ in .idle }, [("sum", "0"), ("visited", "0 of 7")],
                             "Every node already stores the sum of its range.",
                             "The root covers 0..3 and holds \(total), the whole array. The query wants indices 1 to 3."))
    b.frame("", active: [root], story: story({ _ in .active }, [("sum", "0"), ("visited", "1 of 7")],
                                             "The root {0..3} only partly overlaps the query, so split it.",
                                             "Index 0 is outside [1..3], so the root's \(total) is too much. Ask both children."))
    b.frame("", active: [left], path: [root], story: story({ $0 <= 1 ? .active : .idle }, [("sum", "0"), ("visited", "2 of 7")],
                                                           "{0..1} straddles the edge of the query too, so go down again.",
                                                           "Only a node whose range sits wholly inside the query can answer for it."))
    b.frame("", active: [leaves[1]], path: [root, left], skipped: [leaves[0]],
            story: story({ $0 == 0 ? .skipped : $0 == 1 ? .active : .idle }, [("sum", "\(data[1])"), ("visited", "3 of 7")],
                         "Leaf 1 is inside the query, so count its value, {\(data[1])}.",
                         "Leaf 0 is outside, so it is skipped without a visit. Next, the root's right child."))
    b.frame("", active: [right], path: [root, left], marked: [leaves[1]], skipped: [leaves[0], leaves[2], leaves[3]],
            story: story({ $0 == 0 ? .skipped : $0 == 1 ? .marked : .active },
                         [("sum", "\(data[1]) + \(rightSum) = \(data[1] + rightSum)"), ("visited", "4 of 7")],
                         "[2..3] sits fully inside the query, so take its sum, {\(rightSum)}, without going down.",
                         "Together with leaf 1 that gives \(data[1] + rightSum). Only 4 of 7 nodes were visited.",
                         accented: [0]))
    return b.frames
}

private func bTreeFrames() -> [TreeFrame] { bTreeStoryFrames(plus: false) }

private final class BNode {
    var keys: [Int]
    var kids: [BNode]
    init(_ keys: [Int] = [], _ kids: [BNode] = []) { self.keys = keys; self.kids = kids }
    var leaf: Bool { kids.isEmpty }
}

/// Order 3 (at most 2 keys a node), ascending inserts. An overfull node is shown for one step, overfull,
/// and split at the start of the next — except on the last insert, where it splits in place.
private func bTreeStoryFrames(plus: Bool) -> [TreeFrame] {
    let maxKeys = 2
    let inserts = plus ? [10, 20, 30, 40, 50] : [10, 20, 30, 40, 50, 60, 70]
    let headlines: [(String, String)] = plus ? [
        ("Insert {10}: in a B+ tree every key lives in a leaf.",
         "Inner nodes only hold copies that steer the search. Order 3 still means at most 2 keys per node."),
        ("Insert {20}: it fits beside 10.", "One leaf, two keys, still within the limit."),
        ("Insert {30}: the leaf holds 3 keys, one too many.",
         "The next step splits it. 20 is copied up, not moved: it stays in the right leaf as well."),
        ("Insert {40}: 20 was copied up, and now 40 overfills the right leaf.",
         "Ascending keys always land in the rightmost leaf, so it keeps filling. Next, 30 is copied up."),
        ("Insert {50}: the leaf splits, then the root splits too.",
         "40 is copied up and the root reaches 3 keys, so 30 moves up to a new root. Every key still sits in a leaf, and the leaves are chained left to right for range scans."),
    ] : [
        ("Insert {10}: the tree is one node with room for two keys.", "A B-tree of order 3 keeps at most 2 keys per node, in sorted order."),
        ("Insert {20}: it fits beside 10, keys kept in order.", "Nothing moves. A node only reorganises once it holds too many keys."),
        ("Insert {30}: the node now holds 3 keys, one too many.",
         "The next step splits it. 20 moves up into a new root, and 10 and 30 become separate leaves."),
        ("Insert {40}: 20 went up, and 40 joins 30 in the right leaf.",
         "The split grew the tree upward by one level. Every leaf is still at the same depth."),
        ("Insert {50}: the right leaf now holds 3 keys, one too many.",
         "The next step splits it. 40 moves up into the root, and 30 and 50 become separate leaves."),
        ("Insert {60}: 40 went up into the root, and 60 joins 50.", "The root now holds 2 keys and 3 children. It is full, but not over."),
        ("Insert {70}: two splits in a row, and the tree grows a level.",
         "60 moves up and overfills the root, so the root splits too and 40 becomes the new root. A B-tree only ever gets taller at the top."),
    ]

    var root = BNode()
    func parentOf(_ target: BNode, _ cur: BNode) -> BNode? {
        for k in cur.kids {
            if k === target { return cur }
            if let p = parentOf(target, k) { return p }
        }
        return nil
    }
    /// A B+ leaf copies its middle key up and keeps it; everything else moves the median up.
    func split(_ node: BNode) {
        let p = parentOf(node, root)
        let up = node.keys[1]
        let left = BNode([node.keys[0]], Array(node.kids.prefix(2)))
        let right = plus && node.leaf ? BNode(Array(node.keys[1...2])) : BNode([node.keys[2]], Array(node.kids.dropFirst(2)))
        if let p {
            let i = p.kids.firstIndex { $0 === node }!
            p.kids[i] = left
            p.kids.insert(right, at: i + 1)
            p.keys.insert(up, at: i)
        } else {
            root = BNode([up], [left, right])
        }
    }
    func resolve(_ node: BNode) {
        var cur: BNode? = node
        while let c = cur, c.keys.count > maxKeys {
            let p = parentOf(c, root)
            split(c)
            cur = p
        }
    }
    func childIndex(_ n: BNode, _ key: Int) -> Int { plus ? n.keys.filter { $0 <= key }.count : n.keys.filter { $0 < key }.count }
    func descend(_ key: Int) -> ([BNode], BNode) {
        var path: [BNode] = []
        var n = root
        while !n.leaf, plus || !n.keys.contains(key) {
            path.append(n)
            n = n.kids[childIndex(n, key)]
        }
        return (path, n)
    }
    func height(_ n: BNode) -> Int { n.leaf ? 1 : 1 + height(n.kids[0]) }

    var frames: [TreeFrame] = []
    var pending: BNode?
    for (step, key) in inserts.enumerated() {
        if let pn = pending { resolve(pn) }
        pending = nil
        var (path, target) = descend(key)
        target.keys.append(key)
        target.keys.sort()
        var over = target.keys.count > maxKeys
        if over, step == inserts.count - 1 {
            resolve(target)
            over = false
            (path, target) = descend(key)
        } else if over {
            pending = target
        }

        var nodes: [TreeNodeSpec] = []
        var ids: [ObjectIdentifier: Int] = [:]
        func emit(_ n: BNode, _ parent: Int?, _ order: Int) {
            let id = nodes.count
            ids[ObjectIdentifier(n)] = id
            let onPath = path.contains { $0 === n }
            let isTarget = n === target
            nodes.append(TreeNodeSpec(
                id: id, label: "", parent: parent, order: order,
                state: onPath ? .path : isTarget ? .active : .idle,
                keys: n.keys.map { k in KeyCell(value: "\(k)", state: onPath ? .path : isTarget && k == key ? (over ? .warn : .active) : .idle) },
                capacity: maxKeys,
                overflow: isTarget && over ? "\(n.keys.count) keys · max \(maxKeys)" : nil))
            for (i, k) in n.kids.enumerated() { emit(k, id, i) }
        }
        emit(root, nil, 0)

        let (headline, body) = headlines[step]
        var chips: [(key: String?, value: String)] = [("max keys", "\(maxKeys)"), ("height", "\(height(root))")]
        if over { chips.append(("leaf", "overflow")) }
        let pathEdges = (Array(path.dropFirst()) + [target]).filter { $0 !== root }.compactMap { ids[ObjectIdentifier($0)] }
        let story = TreeStory(
            title: plus ? "B+ TREE" : "B-TREE", note: "order 3", stripLabel: "INSERT ORDER",
            cells: inserts.enumerated().map { i, k in StripCell(header: "", value: "\(k)", state: i < step ? .skipped : i == step ? .active : .idle) },
            chips: chips, headline: headline, emphasis: .active, body: body,
            warnedChips: over ? [2] : [],
            edgeStates: Dictionary(uniqueKeysWithValues: pathEdges.map { ($0, TreeState.path) }),
            leafChain: plus)
        frames.append(TreeFrame(nodes: nodes, status: story.status, story: story))
    }
    return frames
}

// MARK: - Priority queue ADT

private func priorityQueueFrames() -> [TreeFrame] {
    let capacity = 7
    var heap: [Int] = []
    var frames: [TreeFrame] = []

    /// Inserts one value with sift-up; returns the slot it came to rest in and the slots it passed through.
    func insert(_ value: Int) -> (Int, [Int]) {
        heap.append(value)
        var i = heap.count - 1
        var trail = [i]
        while i > 0, heap[(i - 1) / 2] > heap[i] {
            let parent = (i - 1) / 2
            heap.swapAt(parent, i)
            i = parent
            trail.append(i)
        }
        return (i, trail)
    }
    func frame(_ rest: Int, _ edges: Set<Int>, _ chips: [(key: String?, value: String)], _ headline: String, _ body: String,
               _ emphasis: TreeState, positive: Set<Int> = []) {
        func stateOf(_ i: Int) -> TreeState { i == 0 ? .marked : i == rest ? .active : .idle }
        let nodes = heap.indices.map { i in
            TreeNodeSpec(id: i, label: "\(heap[i])", parent: i == 0 ? nil : (i - 1) / 2, order: i % 2 == 1 ? 0 : 1, state: stateOf(i), sub: "i\(i)")
        }
        let story = TreeStory(title: "MIN-HEAP", note: "parent ≤ child", stripLabel: "STORED AS AN ARRAY",
                              cells: (0..<capacity).map { i in i < heap.count ? StripCell(header: "\(i)", value: "\(heap[i])", state: stateOf(i)) : StripCell(header: "\(i)", value: "", state: .skipped) },
                              chips: chips, headline: headline, emphasis: emphasis, body: body, positiveChips: positive,
                              edgeStates: Dictionary(uniqueKeysWithValues: edges.map { ($0, TreeState.active) }),
                              footnote: "children of i → 2i+1, 2i+2")
        frames.append(TreeFrame(nodes: nodes, status: story.status, story: story))
    }
    /// Every edge a value crossed on its way up, named by the lower slot.
    func climbed(_ trail: [Int]) -> Set<Int> { Set(trail.dropLast()) }

    var (rest, trail) = insert(7)
    frame(rest, [], [("size", "1"), ("peek", "7")],
          "Insert 7: it lands in slot 0 and is the {minimum} so far.",
          "A heap is a complete tree kept in an array. The root, slot 0, always holds the smallest value.", .marked)
    (rest, trail) = insert(4)
    frame(rest, climbed(trail), [("size", "2"), ("peek", "4"), ("4 < 7", "swap")],
          "Insert 4: it is smaller than its parent 7, so they {swap}.",
          "Sift-up compares a value with the parent at (i − 1) / 2 and swaps while the parent is bigger. 4 is the new root.", .active)
    (rest, trail) = insert(9)
    frame(rest, [rest], [("size", "3"), ("peek", "4"), ("9 ≥ 4", "ok")],
          "Insert 9: it lands in slot 2 and stays, since {9 ≥ 4}.",
          "No swap needed. Most inserts stop after one or two comparisons.", .active, positive: [2])
    (rest, trail) = insert(2)
    frame(rest, climbed(trail), [("size", "4"), ("peek", "2"), ("swaps", "\(trail.count - 1)")],
          "Insert 2: two swaps carry it all the way to the {root}.",
          "2 < 7, then 2 < 4. Sift-up only walks one path, so an insert costs at most log n swaps.", .marked)
    (rest, trail) = insert(6)
    frame(rest, [rest], [("size", "5"), ("peek", "\(heap[0])"), ("6 ≥ 4", "ok")],
          "Five inserts done. The minimum, {\(heap[0])}, is at the root.",
          "The array isn't sorted: 9 comes before 7. Only one rule holds, that each parent is ≤ its children.", .marked, positive: [2])
    return frames
}

// MARK: - Huffman coding

/// The forest over the queue it is built from: each step pops the two lightest subtrees (yellow), hangs
/// them under a new dashed node, and pushes that node back. Everything already inside a subtree is green.
private func huffmanFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let freq: [(String, Int)] = [("a", 20), ("b", 12), ("c", 8), ("d", 5), ("e", 3)]
    let total = freq.reduce(0) { $0 + $1.1 }

    struct Sub {
        let id: Int; let weight: Int; let symbol: String?
        var label: String { symbol.map { "\($0):\(weight)" } ?? "\(weight)" }
    }
    /// The queue in pop order: lightest first, and on a tie the merged node before the symbol.
    func sorted(_ queue: [Sub]) -> [Sub] {
        queue.enumerated().sorted { l, r in
            if l.element.weight != r.element.weight { return l.element.weight < r.element.weight }
            let lk = l.element.symbol == nil ? 0 : 1, rk = r.element.symbol == nil ? 0 : 1
            return lk != rk ? lk < rk : l.offset < r.offset
        }.map(\.element)
    }

    // Code lengths up front, so a merge step can say where the symbols it touches will end up.
    var lengths = Dictionary(uniqueKeysWithValues: freq.map { ($0.0, 0) })
    var pool = freq.map { ($0.1, [$0.0]) }
    while pool.count > 1 {
        let s = pool.sortedBy { $0.0 }
        (s[0].1 + s[1].1).forEach { lengths[$0]! += 1 }
        pool = Array(s.dropFirst(2)) + [(s[0].0 + s[1].0, s[0].1 + s[1].1)]
    }
    let maxLen = lengths.values.max()!, minLen = lengths.values.min()!
    let deepest = freq.filter { lengths[$0.0] == maxLen }.sortedBy { $0.1 }.map(\.0)
    let shallowest = freq.first { lengths[$0.0] == minLen }!.0

    var merged = Set<Int>()
    var kids: [Int: (Int, Int)] = [:]
    func queueCells(_ queue: [Sub], _ popping: Int) -> [StripCell] {
        sorted(queue).enumerated().map { i, sub in StripCell(header: "", value: sub.label, state: i < popping ? .active : .idle) }
    }
    func story(_ cells: [StripCell], _ chips: [(key: String?, value: String)], _ headline: String, _ body: String,
               emphasis: TreeState = .active, next: Set<Int> = []) -> TreeStory {
        TreeStory(title: "FOREST", note: "merge two rarest", stripLabel: "QUEUE", cells: cells, chips: chips, headline: headline,
                  emphasis: emphasis, body: body, nextEdges: next,
                  edgeStates: Dictionary(uniqueKeysWithValues: merged.map { ($0, TreeState.marked) }), stripNote: "by weight")
    }

    var queue = freq.map { Sub(id: b.add("\($0.0):\($0.1)", nil, 0), weight: $0.1, symbol: $0.0) }
    b.frame("", story: story(queueCells(queue, 0), [("symbols", "\(freq.count)"), ("fixed", "3 bits")],
                             "Count each symbol, then queue them {by weight}.",
                             "Fixed-width codes spend 3 bits on every symbol, rare ones included. Huffman gives the rare ones the long codes instead."))

    var step = 0
    while queue.count > 1 {
        let pop = sorted(queue)
        let x = pop[0], y = pop[1]
        // Lighter on the left; on a tie the symbol goes left of the merged node.
        let (left, right) = x.weight < y.weight || x.symbol != nil ? (x, y) : (y, x)
        let weight = x.weight + y.weight
        let node = b.add("\(weight)", nil, 0)
        b.reparent(left.id, node, 0)
        b.reparent(right.id, node, 1)
        kids[node] = (left.id, right.id)
        let body: String
        switch step {
        case 0: body = "Rare symbols end up deepest, so they get the longest codes."
        case 1: body = "Rare symbols end up deepest, so they get the longest codes. \(deepest.joined(separator: " and ")) end with \(maxLen) bits, and \(shallowest) with \(minLen)."
        default: body = queue.count == 2 ? "The last two subtrees join at the root." : "Every symbol under the new node gets one bit longer."
        }
        b.frame("", active: [x.id, y.id], marked: merged, story: story(
            queueCells(queue, 2),
            [("weight", "\(x.weight) + \(y.weight) = \(weight)"), ("queue", "\(queue.count) → \(queue.count - 1)")],
            "Merge the two rarest, {\(x.label)} and {\(y.label)}, into \(weight).", body, next: [left.id, right.id]),
            ghost: [node])
        merged.insert(left.id)
        merged.insert(right.id)
        queue = queue.filter { $0.id != x.id && $0.id != y.id } + [Sub(id: node, weight: weight, symbol: nil)]
        b.frame("", marked: merged, story: story(
            queueCells(queue, 0), [("queue", "\(queue.count)")],
            queue.count == 1 ? "{m:\(weight)} is the root: the whole message." : "{m:\(weight)} goes back into the queue.",
            queue.count == 1 ? "Its weight is the total count, \(total). The tree is finished." : "\(queue.count) subtrees left. It is sorted in by weight like any other entry.",
            emphasis: .marked))
        step += 1
    }

    // Left edges read 0, right edges 1; a symbol's code is the path down to its leaf.
    for (_, pair) in kids {
        b.setEdge(pair.0, "0")
        b.setEdge(pair.1, "1")
    }
    var codes: [Int: String] = [:]
    func walk(_ id: Int, _ code: String) {
        codes[id] = code
        if case let (l, r)? = kids[id] { walk(l, code + "0"); walk(r, code + "1") }
    }
    walk(queue[0].id, "")
    let leafIds = Dictionary(uniqueKeysWithValues: b.frames[0].nodes.map { (String($0.label.split(separator: ":")[0]), $0.id) })
    let codeCells = freq.map { StripCell(header: $0.0, value: codes[leafIds[$0.0]!]!, state: .marked) }
    let leaves = Set(leafIds.values)
    let huffmanBits = freq.reduce(0) { $0 + $1.1 * lengths[$1.0]! }
    let fixedBits = total * 3

    func codeStory(_ chips: [(key: String?, value: String)], _ headline: String, _ body: String) -> TreeStory {
        TreeStory(title: "CODE TREE", note: "left 0 · right 1", stripLabel: "CODES", cells: codeCells, chips: chips, headline: headline,
                  emphasis: .marked, body: body, legendLabels: [.marked: "Symbol"])
    }
    b.frame("", marked: leaves, story: codeStory(
        freq.map { (key: Optional($0.0), value: codes[leafIds[$0.0]!]!) },
        "Read each code off the path: {left is 0, right is 1}.",
        "Frequent \(shallowest) gets \(minLen) bit; rare \(deepest.joined(separator: " and ")) get \(maxLen). No code is a prefix of another, since every symbol is a leaf."))
    b.frame("", marked: leaves, story: codeStory(
        [("huffman", "\(huffmanBits) bits"), ("fixed", "\(fixedBits) bits"), ("saved", "\(100 * (fixedBits - huffmanBits) / fixedBits)%")],
        "The message costs {\(huffmanBits) bits} instead of \(fixedBits).",
        "Each symbol costs its count times its code length: " + freq.map { "\($0.1)×\(lengths[$0.0]!)" }.joined(separator: " + ") + " = \(huffmanBits). Fixed 3-bit codes need \(total) × 3 = \(fixedBits)."))
    return b.frames
}

// MARK: - Disjoint set

private func disjointSetFrames() -> [TreeFrame] {
    let labels = ["A", "B", "C", "D", "E", "F", "G", "H"]
    let b = TreeBuilder()
    let nodeId = labels.enumerated().map { b.add($1, nil, $0) }
    var parent = Array(labels.indices)
    var rank = [Int](repeating: 0, count: labels.count)
    func root(_ x: Int) -> Int {
        var cur = x
        while parent[cur] != cur { cur = parent[cur] }
        return cur
    }
    func union(_ x: Int, _ y: Int) -> Int {
        let rx = root(x), ry = root(y)
        let child: Int, newRoot: Int
        if rank[rx] < rank[ry] { (child, newRoot) = (rx, ry) }
        else if rank[ry] < rank[rx] { (child, newRoot) = (ry, rx) }
        else { rank[rx] += 1; (child, newRoot) = (ry, rx) }
        parent[child] = newRoot
        b.reparent(nodeId[child], nodeId[newRoot], child)
        return child
    }
    func story(_ title: String, _ note: String, _ cells: (Int) -> TreeState, _ chips: [(key: String?, value: String)],
               _ headline: String, _ body: String, accented: Set<Int> = []) -> TreeStory {
        TreeStory(title: title, note: note, stripLabel: "PARENT[]",
                  cells: labels.indices.map { StripCell(header: labels[$0], value: labels[parent[$0]], state: cells($0)) },
                  chips: chips, headline: headline, emphasis: .marked, body: body, accentedChips: accented)
    }
    func roots(_ x: Int) -> TreeState { parent[x] == x ? .marked : .idle }

    b.frame("", marked: Set(nodeId), story: story("MAKE-SET × 8", "parent[x] = x", roots, [("sets", "8"), ("height", "0")],
                                                  "Eight elements, eight sets. Each one is its own {root}.",
                                                  "The forest is the partition. Nothing else is stored, just one parent pointer per element."))

    // Ordered so both cases appear: equal ranks (which grows the forest) and unequal (which does not).
    let paired = [(0, 1), (2, 3), (4, 5), (6, 7)].map { union($0.0, $0.1) }
    b.frame("", active: Set(paired.map { nodeId[$0] }), marked: Set(labels.indices.filter { parent[$0] == $0 }.map { nodeId[$0] }),
            story: story("UNION × 4", "by rank", { paired.contains($0) ? .active : roots($0) }, [("sets", "4"), ("height", "1")],
                         "Four unions pair them off. B, D, F and H now hang under a {root}.",
                         "Each pair tied at rank 0, so either could lead. The root's rank goes up to 1."))

    let merged = [(0, 2), (0, 4), (0, 6)].map { union($0.0, $0.1) }
    b.frame("", active: Set(merged.map { nodeId[$0] }), marked: [nodeId[0]],
            story: story("UNION BY RANK", "shorter under taller", { merged.contains($0) ? .active : roots($0) },
                         [("sets", "1"), ("height", "2"), ("rank A", "\(rank[0])")],
                         "Three more unions join everything under {A}.",
                         "C ties A at rank 1, so A grows to rank 2. E and G are shorter and add no depth."))

    func depthOf(_ x: Int) -> Int {
        var d = 0, cur = x
        while parent[cur] != cur { cur = parent[cur]; d += 1 }
        return d
    }
    var deepest = 0
    for x in labels.indices where depthOf(x) > depthOf(deepest) { deepest = x }
    var walkPath: [Int] = []
    var cur = deepest
    while parent[cur] != cur { walkPath.append(cur); cur = parent[cur] }
    let theRoot = cur
    let walked = Array(walkPath.dropFirst())
    let start = labels[deepest], rootName = labels[theRoot]
    b.frame("", active: [nodeId[deepest]], path: Set(walked.map { nodeId[$0] }), marked: [nodeId[theRoot]],
            links: [TreeLink(from: nodeId[deepest], to: nodeId[theRoot])],
            story: story("FIND(\(start))", "with path compression",
                         { $0 == deepest ? .active : walked.contains($0) ? .path : $0 == theRoot ? .marked : .idle },
                         [("hops", "\(walkPath.count)"), ("root", rootName), ("parent[\(start)]", "\(labels[parent[deepest]]) → \(rootName)")],
                         "find(\(start)) walks \(walkPath.count) pointers to reach {\(rootName)}.",
                         "Everything on that walk is in \(rootName)'s set, so \(start) can point straight at \(rootName). The next find(\(start)) takes 1 hop."))

    for node in walkPath {
        parent[node] = theRoot
        b.reparent(nodeId[node], nodeId[theRoot], node)
    }
    b.frame("", active: [nodeId[deepest]], marked: [nodeId[theRoot]],
            story: story("FIND(\(start))", "after compression", { $0 == deepest ? .active : $0 == theRoot ? .marked : .idle },
                         [("hops", "1"), ("root", rootName), ("parent[\(start)]", rootName)],
                         "\(start) now points straight at {\(rootName)}.",
                         "The set did not change, only the path to its root. Every later find(\(start)) costs one hop."))

    // Same script, two implementations, hops counted — the claim about near-constant time is measured.
    let unions = [(0, 1), (2, 3), (4, 5), (6, 7), (0, 2), (0, 4), (0, 6)]
    func measure(_ useRank: Bool, _ useCompression: Bool) -> Int {
        var p = Array(labels.indices)
        var r = [Int](repeating: 0, count: labels.count)
        var hops = 0
        func find(_ x: Int) -> Int {
            var c = x
            var seen: [Int] = []
            while p[c] != c { hops += 1; seen.append(c); c = p[c] }
            if useCompression { for s in seen { p[s] = c } }
            return c
        }
        for (x, y) in unions {
            let rx = find(x), ry = find(y)
            if rx == ry { continue }
            if !useRank { p[rx] = ry }
            else if r[rx] < r[ry] { p[rx] = ry }
            else if r[ry] < r[rx] { p[ry] = rx }
            else { p[ry] = rx; r[rx] += 1 }
        }
        for _ in 0..<4 { for i in labels.indices { _ = find(i) } }
        return hops
    }
    let naive = measure(false, false), ranked = measure(true, false), both = measure(true, true)
    b.frame("", marked: [nodeId[theRoot]],
            story: story("TOTAL POINTER HOPS", "same unions, 4 rounds of find", roots,
                         [("naive", "\(naive)"), ("rank", "\(ranked)"), ("rank + compress", "\(both)")],
                         "Rank plus compression walks {\(both)} pointers. Naive walks \(naive).",
                         "Same \(unions.count) unions, then 4 rounds of find on all 8 elements. Rank alone walks \(ranked). The forest flattens itself as you use it.",
                         accented: [2]))
    return b.frames
}

// MARK: - Suffix tree

private let suffixText = "aba$"

private func suffix(_ i: Int) -> String { String(suffixText.dropFirst(i)) }

/// The suffixes as the list under the tree shows them after suffix `current` goes in.
private func suffixRows(_ current: Int) -> [StoryRow] {
    (0..<suffixText.count).map { i in
        if i < current { return StoryRow(index: "[\(i)]", text: suffix(i), status: "added", state: .done) }
        if i == current { return StoryRow(index: "[\(i)]", text: suffix(i), status: "inserting", state: .current) }
        return StoryRow(index: "[\(i)]", text: suffix(i), status: i == current + 1 ? "next" : "", state: .pending)
    }
}

/// Ukkonen-free, by hand: "aba$" is small enough that each insert's effect on the tree is scripted.
private func suffixTreeFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let root = b.add("•", nil, 0)
    let leaf0 = b.add("[0]", root, 0, edge: "aba$")
    let leaf1 = b.add("[1]", root, 1, edge: "ba$")
    func story(_ current: Int, _ headline: String, _ body: String, emphasis: TreeState = .active, _ edges: [Int: TreeState]) -> TreeStory {
        TreeStory(title: "SUFFIX TREE", note: "", stripLabel: nil, cells: [], chips: [], headline: headline, emphasis: emphasis,
                  body: body, edgeStates: edges, rows: suffixRows(current))
    }
    // Leaf [1] is drawn ahead of time, ghosted: its edge is where the next suffix will go.
    b.frame("", marked: [leaf0], skipped: [leaf1],
            story: story(0, "Suffix 0 \"aba$\" is the whole text, so it hangs off the root as one {edge}.",
                         "An edge can carry a whole substring. The leaf stores 0, the index where the suffix starts.", [leaf0: .active]))
    b.frame("", marked: [leaf0, leaf1],
            story: story(1, "Suffix 1 \"ba$\" starts with b, which no edge does, so it gets a {new edge}.",
                         "Nothing under the root begins with b. A fresh edge from the root, and leaf [1].", [leaf1: .active]))
    // "a$" shares the first letter of "aba$": split that edge after "a".
    b.remove(leaf0)
    let split = b.add("•", root, 0, edge: "a")
    let leaf0b = b.add("[0]", split, 0, edge: "ba$")
    let leaf2 = b.add("[2]", split, 1, edge: "$")
    let leaf3 = b.add("[3]", root, 2, edge: "$")
    b.frame("", active: [split], path: [root], marked: [leaf0b, leaf1, leaf2], skipped: [leaf3],
            story: story(2, "Suffix 2 \"a$\" matches {a}, then breaks at b.",
                         "The edge \"aba$\" splits after \"a\". The new leaf [2] hangs off the split, and a search for \"a\" now returns 0 and 2.",
                         emphasis: .path, [split: .path, leaf2: .active]))
    b.frame("", marked: [leaf0b, leaf1, leaf2, leaf3],
            story: story(3, "Suffix 3 \"$\" is only the terminator, so it gets its own {leaf}.",
                         "4 suffixes, 4 leaves, each labelled with where it starts. Any substring of \"aba\" is now a walk down from the root.",
                         [leaf3: .active]))
    return b.frames
}

/// The same four suffixes kept as a sorted list of start indices: no tree, just the order.
private func suffixArrayFrames() -> [TreeFrame] {
    let n = suffixText.count
    let lines: [(String, String)] = [
        ("Suffix 0 \"aba$\" starts the {sorted} list.", "A suffix array keeps every suffix's start index, ordered by the suffixes themselves."),
        ("Suffix 1 \"ba$\" sorts {after} \"aba$\", since b > a.", "Only the start index is stored. The text itself is never copied."),
        ("Suffix 2 \"a$\" sorts {first}: $ comes before every letter.", "a$ and aba$ share the prefix \"a\", exactly where the suffix tree branched."),
        ("Suffix 3 \"$\" is the smallest of all, so it takes {rank 0}.", "Binary search on the array finds any pattern in O(m log n), with far less memory than the tree."),
    ]
    return (0..<n).map { current in
        let sorted = (0...current).sorted { suffix($0) < suffix($1) }
        let rows = sorted.enumerated().map { rank, i in
            i == current ? StoryRow(index: "[\(i)]", text: suffix(i), status: "inserting", state: .current)
                : StoryRow(index: "[\(i)]", text: suffix(i), status: "rank \(rank)", state: .done)
        } + (current + 1..<n).map { i in StoryRow(index: "[\(i)]", text: suffix(i), status: i == current + 1 ? "next" : "", state: .pending) }
        let story = TreeStory(
            title: "SUFFIX ARRAY", note: "", stripLabel: "SA",
            cells: (0..<n).map { rank in
                guard rank < sorted.count else { return StripCell(header: "\(rank)", value: "", state: .skipped) }
                return StripCell(header: "\(rank)", value: "\(sorted[rank])", state: sorted[rank] == current ? .active : .idle)
            },
            chips: [("SA", "[\(sorted.map(String.init).joined(separator: ", "))]")],
            headline: lines[current].0, emphasis: .active, body: lines[current].1, rows: rows, rowColumns: 1)
        return TreeFrame(nodes: [], status: story.status, story: story)
    }
}

// MARK: - Lowest common ancestor

private func lcaFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let a = b.add("A", nil, 0)
    let bb = b.add("B", a, 0)
    let c = b.add("C", a, 1)
    let d = b.add("D", bb, 0)
    let e = b.add("E", bb, 1)
    let f = b.add("F", c, 0)
    let g = b.add("G", c, 1)
    let h = b.add("H", d, 0)
    let order = [a, bb, c, d, e, f, g, h]
    let depth = [a: 0, bb: 1, c: 1, d: 2, e: 2, f: 2, g: 2, h: 3]
    let dist = depth[h]! + depth[e]! - 2 * depth[bb]!

    func step(_ note: String, active: Set<Int> = [], path: Set<Int> = [], marked: Set<Int> = [],
              _ chips: [(key: String?, value: String)], _ headline: String, _ body: String,
              emphasis: TreeState = .active, next: Set<Int> = [], focusDepth: Int? = nil, accented: Set<Int> = []) {
        let cells = order.map { id -> StripCell in
            let state: TreeState = active.contains(id) ? .active : marked.contains(id) ? .marked : path.contains(id) ? .path : .idle
            return StripCell(header: b.labelOf(id), value: "\(depth[id]!)", state: state)
        }
        b.frame("", active: active, path: path, marked: marked,
                story: TreeStory(title: "LCA(H, E)", note: note, stripLabel: "DEPTH", cells: cells, chips: chips,
                                 headline: headline, emphasis: emphasis, body: body, accentedChips: accented,
                                 nextEdges: next, focusDepth: focusDepth))
    }

    step("record every depth", [("u", "H"), ("v", "E")],
         "One pass from the root gives every node its {depth}.",
         "A is at depth 0 and H is deepest at 3. That table is all the climb needs.")
    step("climb to equal depth", active: [h, e], [("u", "H"), ("v", "E"), ("depth", "3 ≠ 2")],
         "H sits at depth 3 and E at depth 2, so lift {H} first.",
         "Only the deeper node climbs, one parent at a time, until both depths match.", next: [h])
    step("climb to equal depth", active: [d, e], path: [h], [("u", "H → D"), ("v", "E"), ("depth", "2 = 2")],
         "H climbs to depth 2. Now {D} and {E} are level, but different.",
         "Next both climb one step together. The first node where they meet is the LCA.", next: [d, e], focusDepth: 2)
    step("climb together", active: [bb], path: [d, e, h], [("u", "D → B"), ("v", "E → B"), ("depth", "1 = 1")],
         "Both climb one step and land on the same node, {B}.",
         "u and v are now equal, so the climb stops here.", focusDepth: 1)
    step("answer", path: [d, e, h], marked: [bb], [("lca", "B"), ("distance", "\(dist) edges")],
         "LCA(H, E) = {B}.",
         "The path between them is depth[H] + depth[E] − 2·depth[B] = 3 + 2 − 2 = \(dist) edges. With a jump table each climb is O(log n).",
         emphasis: .marked, focusDepth: 1, accented: [0])
    return b.frames
}

// MARK: - Tree DP: maximum-weight independent set

private func treeDpFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let weight = ["A": 3, "B": 2, "C": 4, "D": 5, "E": 1, "F": 6]
    let children = ["A": ["B", "C"], "B": ["D", "E"], "C": ["F"]]
    let a = b.add("A·3", nil, 0)
    let bb = b.add("B·2", a, 0)
    let c = b.add("C·4", a, 1)
    let d = b.add("D·5", bb, 0)
    let e = b.add("E·1", bb, 1)
    let f = b.add("F·6", c, 0)
    let ids = ["A": a, "B": bb, "C": c, "D": d, "E": e, "F": f]
    // take[n]: best weight in n's subtree with n in the set; skip[n]: with n left out.
    var take: [String: Int] = [:], skip: [String: Int] = [:]

    func step(_ headline: String, _ body: String, active: String? = nil, rows: [StoryFormulaRow] = [], marked: Set<String> = [],
              chips: [(key: String?, value: String)] = [("order", "post-order")]) {
        var story = TreeStory(title: "MAX INDEPENDENT SET", note: "badges = take / skip", stripLabel: nil, cells: [], chips: chips,
                              headline: headline, emphasis: marked.isEmpty ? .active : .marked, body: body)
        story.plainEdges = true
        story.formulaRows = rows
        b.frame(story.status, active: Set([active].compactMap { $0 }.map { ids[$0]! }),
                path: Set((active.flatMap { children[$0] } ?? []).map { ids[$0]! }),
                marked: Set(marked.map { ids[$0]! }), story: story,
                below: Dictionary(uniqueKeysWithValues: take.keys.map { (ids[$0]!, "\(take[$0]!) / \(skip[$0]!)") }))
    }

    func solve(_ n: String) -> [StoryFormulaRow] {
        let kids = children[n] ?? []
        take[n] = weight[n]! + kids.reduce(0) { $0 + skip[$1]! }
        skip[n] = kids.reduce(0) { $0 + max(take[$1]!, skip[$1]!) }
        if kids.isEmpty {
            return [StoryFormulaRow(label: "take \(n)", formula: "{\(take[n]!)}"), StoryFormulaRow(label: "skip \(n)", formula: "{\(skip[n]!)}")]
        }
        let takeSum = (["\(weight[n]!)"] + kids.map { "{p:\(skip[$0]!)}" }).joined(separator: " + ")
        let skipSum = kids.map { "{p:\(max(take[$0]!, skip[$0]!))}" }.joined(separator: " + ")
        return [StoryFormulaRow(label: "take \(n)", formula: "\(takeSum) = {\(take[n]!)}"),
                StoryFormulaRow(label: "skip \(n)", formula: kids.count == 1 ? skipSum : "\(skipSum) = {\(skip[n]!)}")]
    }

    step("Pick nodes with the largest total weight, never two {w:joined by an edge}.",
         "Labels are node·weight. Post-order solves every child before its parent, so each parent's inputs are ready.")
    step("Leaf {D}: taking it is worth 5, skipping it 0.", "A leaf has no children to rule out, so its two answers are its weight and zero.",
         active: "D", rows: solve("D"))
    step("Leaf {E}: taking it is worth 1, skipping it 0.", "Post-order finishes both of B's children before B itself.", active: "E", rows: solve("E"))
    step("Taking {B} rules out D and E. Skipping it lets both in.", "Each node keeps two answers, with and without itself. The root picks the better one.",
         active: "B", rows: solve("B"))
    step("Leaf {F}: taking it is worth 6, skipping it 0.", "B's subtree is finished, so the pass moves over to C's.", active: "F", rows: solve("F"))
    step("Taking {C} rules out F. Skipping it lets F in, and F weighs more.", "A heavy child can beat its parent: skipping C keeps 6, taking it only 4.",
         active: "C", rows: solve("C"))
    step("Taking {A} wins: 15 against 12.", "Taking A means skipping B and C, and both already did best without themselves.", active: "A", rows: solve("A"))
    let best = max(take["A"]!, skip["A"]!)
    step("{A, D, E and F} weigh 3 + 5 + 1 + 6 = \(best).", "No two of them share an edge. Every subtree was solved once, so the whole pass is O(n).",
         marked: ["A", "D", "E", "F"], chips: [("order", "post-order"), ("best", "\(best)")])
    return b.frames
}

// MARK: - Gradient-boosting implementations as tree shapes

private func xgbGain(_ gl: Double, _ hl: Double, _ gr: Double, _ hr: Double, _ lambda: Double, _ gamma: Double) -> Double {
    0.5 * (gl * gl / (hl + lambda) + gr * gr / (hr + lambda) - (gl + gr) * (gl + gr) / (hl + hr + lambda)) - gamma
}

private func xgbLeaf(_ g: Double, _ h: Double, _ lambda: Double) -> Double { -g / (h + lambda) }

private func xgboostFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let lambda = 1.0, gamma = 0.5
    let root = b.add("G=−7.0  H=8.0", nil, 0)
    b.frame("XGBoost is gradient boosting written as a proper regularized optimization. Each node carries two sums over the rows that reach it: G, the gradients of the loss, and H, the second derivatives. Ordinary gradient boosting uses only G.", active: [root])
    b.relabel(root, "w* = \(fx(xgbLeaf(-7, 8, lambda)))")
    b.frame("If this node stayed a leaf, its optimal value would be −G/(H+λ) = \(fx(xgbLeaf(-7, 8, lambda))). That is a closed-form Newton step, not a line search — the second-order term is what makes it exact, and λ in the denominator shrinks the leaf toward zero.", marked: [root])
    let gainA = xgbGain(-6, 4, -1, 4, lambda, gamma)
    let left = b.add("G=−6.0  H=4.0", root, 0)
    let right = b.add("G=−1.0  H=4.0", root, 1)
    b.relabel(root, "gain \(fx(gainA))")
    b.frame("Candidate split: gain = ½[G_L²/(H_L+λ) + G_R²/(H_R+λ) − G²/(H+λ)] − γ = \(fx(gainA)). Positive, so it is worth taking. γ is a fixed toll charged per split — a structural penalty that has no analogue in plain GBM.", active: [left, right], path: [root])
    let gainB = xgbGain(-0.6, 2, -0.4, 2, lambda, gamma)
    let ll = b.add("G=−0.6  H=2.0", right, 0)
    let lr = b.add("G=−0.4  H=2.0", right, 1)
    b.frame("Try splitting the right child: gain = \(fx(gainB)). Negative, because the improvement it buys is smaller than γ. XGBoost prunes it — and note it computes this *after* growing to max depth, so a bad split whose children are excellent is not discarded prematurely.", active: [ll, lr])
    b.remove(ll)
    b.remove(lr)
    b.relabel(left, "w = \(fx(xgbLeaf(-6, 4, lambda)))")
    b.relabel(right, "w = \(fx(xgbLeaf(-1, 4, lambda)))")
    b.frame("Pruned. The two surviving leaves take their closed-form values \(fx(xgbLeaf(-6, 4, lambda))) and \(fx(xgbLeaf(-1, 4, lambda))). Both are shrunk toward zero by λ — with λ = 0 they would be \(fx(xgbLeaf(-6, 4, 0))) and \(fx(xgbLeaf(-1, 4, 0))).", marked: [left, right])
    b.frame("Growth here is level-wise: every node at a depth is considered before going deeper, which keeps the tree balanced and made the algorithm straightforward to parallelize. That choice is exactly what LightGBM changes.", path: [root], marked: [left, right])
    return b.frames
}

private func lightgbmFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let root = b.add("root", nil, 0)
    b.frame("Same budget for both strategies: three splits, so four leaves either way. XGBoost's level-wise growth splits every node at a depth before descending.")
    let l = b.add("gain 4.0", root, 0)
    let r = b.add("gain 0.3", root, 1)
    b.frame("Split 1 is the root, giving two children whose own split gains are very different — 4.0 and 0.3.", active: [l, r])
    let ll = b.add("gain 2.1", l, 0), lr = b.add("gain 1.8", l, 1), rl = b.add("gain 0.2", r, 0), rr = b.add("gain 0.1", r, 1)
    b.frame("Splits 2 and 3 go to both nodes at this level, because that is what level-wise means. Realized gain 4.0 + 0.3 = 4.3, and one of those splits went to a node with almost nothing left to give. Four leaves, depth 2.", marked: [ll, lr, rl, rr])
    for id in [rr, rl, lr, ll, r, l] { b.remove(id) }
    b.frame("Now the same three splits, grown leaf-wise: each time, split whichever leaf anywhere in the tree offers the largest gain, regardless of depth.")
    let a = b.add("gain 4.0", root, 0)
    let bn = b.add("gain 0.3", root, 1)
    b.frame("Split 1 is identical — the strategies only diverge once there is a choice of leaf.", active: [a, bn])
    let c = b.add("gain 2.1", a, 0)
    let d = b.add("gain 1.8", a, 1)
    b.frame("Split 2: the best available leaf is the 4.0 node, so take it. Level-wise would have made the same choice here.", active: [c, d])
    b.frame("Split 3 is where they part. The candidates are now 0.3, 2.1 and 1.8, and leaf-wise takes the 2.1 — leaving the 0.3 node unsplit rather than spending a budgeted split on it. Realized gain 4.0 + 2.1 = 6.1 against level-wise's 4.3, from the same three splits and the same four leaves.", active: [c], marked: [bn, d])
    let e = b.add("gain 1.2", c, 0)
    let f = b.add("gain 0.9", c, 1)
    b.frame("The resulting tree is deeper and lopsided. That is the trade: more gain per leaf, and a shape that overfits small datasets readily — which is why num_leaves and min_data_in_leaf matter far more here than max_depth does in XGBoost. The other half of LightGBM's speed is histogram binning: features bucketed into ~255 bins, so split search costs O(bins) instead of sorting every value.",
            path: [root, a, c], marked: [bn, d, e, f])
    return b.frames
}

private func catboostFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let root = b.add("x₁ < 5?", nil, 0)
    let l = b.add("x₁ < 5?", root, 0)
    let r = b.add("x₁ < 5?", root, 1)
    b.frame("CatBoost grows oblivious (symmetric) trees: every node at the same depth tests the identical condition. Here the root's test is reused across the whole level, which is not a coincidence but the constraint.", active: [root])
    b.relabel(l, "x₂ < 2?")
    b.relabel(r, "x₂ < 2?")
    b.frame("Depth 2 picks one condition and applies it to both nodes. The tree is fully described by a list of (feature, threshold) pairs — one per level — rather than a per-node structure.", active: [l, r])
    let leaves = Set((0..<4).map { b.add("leaf \($0)", $0 < 2 ? l : r, $0 % 2) })
    b.frame("That makes prediction a bit-trick rather than a traversal: evaluate each level's condition to a 0 or 1, concatenate them into an index, and look the leaf up directly. No branching, no pointer chasing — which is why CatBoost's inference is unusually fast.", marked: leaves)
    b.frame("The symmetry is also a regularizer. A balanced tree forced to reuse conditions is far less expressive than LightGBM's leaf-wise growth, so it overfits less on small data — and it is a real constraint, so on large datasets with complex interactions it can cost accuracy.", path: [root, l, r], marked: leaves)
    b.frame("CatBoost's other two ideas are not visible in the tree shape. Ordered target statistics encode a categorical value using the target mean over only the rows *before* it in a random permutation, so a row never contributes to its own encoding — plain target encoding leaks the label and overfits badly. Ordered boosting applies the same trick to residuals, computing each row's gradient from a model that never saw it.", marked: leaves)
    return b.frames
}

// MARK: - Divisive hierarchical clustering

private func divisiveFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let points = compactBlobs
    let splits = divisive(points, splits: 4)
    var nodeFor: [[Int]: Int] = [:]
    let root = b.add("all \(points.count)", nil, 0)
    nodeFor[Array(points.indices)] = root
    b.frame("Divisive clustering starts at the opposite end from agglomerative: everything in one cluster, split downward. Both produce a dendrogram; the difference is which end of it is computed first.", active: [root])
    for (index, step) in splits.enumerated() {
        let parent = nodeFor[step.parent] ?? root
        let left = b.add("\(step.left.count) pts", parent, 0)
        let right = b.add("\(step.right.count) pts", parent, 1)
        nodeFor[step.left] = left
        nodeFor[step.right] = right
        b.relabel(parent, "split @ \(fx(step.diameter))")
        b.frame("Split \(index + 1): the least cohesive cluster is the one with the largest diameter (\(fx(step.diameter))), so it goes first. It divides into \(step.left.count) and \(step.right.count) points.", active: [left, right], path: [parent])
    }
    let clusters = Set(nodeFor.values).subtracting([root])
    b.frame("The exact version of this is intractable — finding the best split of a cluster of n points means checking 2^(n−1) − 1 partitions. Practical implementations approximate, and this one uses 2-means on the chosen cluster, which is what DIANA-style implementations do.", marked: clusters)
    b.frame("The tradeoff against agglomerative is about where each one can go wrong. Divisive makes its most consequential decision first, with the whole dataset in view, so a good top-level split is likely — but the cost is high and a bad early split is never revisited. Agglomerative decides locally and cheaply at the bottom, where a wrong early merge is equally permanent but affects far fewer points.", path: [root], marked: clusters)
    return b.frames
}

// MARK: - Aho-Corasick

private func ahoCorasickFrames() -> [TreeFrame] {
    let patterns = ["he", "she", "his", "hers"]
    let text = "ushers"
    let b = TreeBuilder()
    let root = b.add("·", nil, 0)
    var nodeOf = ["": root]
    var prefixOrder = [""]
    var terminal: [Int: String] = [:]
    b.frame("Root. Every pattern will hang off it, sharing whatever prefixes they have in common.", marked: [root])
    for (index, pattern) in patterns.enumerated() {
        var prefix = ""
        var parent = root
        var touched: Set<Int> = [root]
        for c in pattern {
            let next = prefix + String(c)
            if let existing = nodeOf[next] { parent = existing } else {
                parent = b.add(String(c), parent, index)
                nodeOf[next] = parent
                prefixOrder.append(next)
            }
            touched.insert(parent)
            prefix = next
        }
        terminal[parent] = pattern
        let shared = patterns.prefix(index).contains { pattern.hasPrefix($0) || $0.hasPrefix(pattern) }
        b.frame("Insert \"\(pattern)\". \(shared ? "It shares a prefix with an earlier pattern, so those nodes are reused." : "A fresh branch.")", path: touched, marked: [parent])
    }
    var fail = [root: root]
    var links: [TreeLink] = []
    let byDepth = prefixOrder.filter { !$0.isEmpty }.sortedBy(\.count)
    let terminals = Set(terminal.keys)
    b.frame("Now the failure links. A node's link points at the node for the longest proper suffix of its own string — KMP's prefix function, except the target usually lives in a different branch.", marked: terminals)
    for prefix in byDepth {
        let node = nodeOf[prefix]!
        let chars = Array(prefix)
        let target = (1..<max(chars.count, 1)).lazy.compactMap { nodeOf[String(chars[$0...])] }.first ?? root
        fail[node] = target
        links.append(TreeLink(from: node, to: target))
        let targetPrefix = prefixOrder.first { nodeOf[$0] == target }!
        b.frame("\"\(prefix)\" → " + (target == root
                ? "no proper suffix of it is in the trie, so it fails to the root."
                : "its longest suffix present in the trie is \"\(targetPrefix)\", so that is its failure target."),
                active: [node], marked: terminals, links: links)
    }
    var outputVia: [Int: String] = [:]
    for prefix in byDepth {
        let node = nodeOf[prefix]!
        var f = fail[node]!
        while f != root {
            if let t = terminal[f] { outputVia[node] = t }
            if outputVia[node] != nil { break }
            f = fail[f]!
        }
    }
    b.frame("One piece left. \"she\" ends at a node whose failure target is \"he\", and \"he\" is itself a pattern — so landing on \"she\" has also matched \"he\". Reporting every terminal up the failure chain is what catches those; without it the automaton finds only the longest match at each position.",
            active: Set(outputVia.keys), marked: terminals, links: links)
    var current = root
    var found: [String] = []
    var prefixOf: [Int: String] = [:]
    for (p, id) in nodeOf { prefixOf[id] = p }
    func show(_ id: Int) -> String { prefixOf[id]!.isEmpty ? "root" : prefixOf[id]! }
    for (i, c) in text.enumerated() {
        let before = current
        var walked = 0
        while current != root && nodeOf[prefixOf[current]! + String(c)] == nil {
            current = fail[current]!
            walked += 1
        }
        current = nodeOf[prefixOf[current]! + String(c)] ?? root
        let hits = [terminal[current], outputVia[current]].compactMap { $0 }
        found += hits
        let walkText = walked > 0 ? "no edge from \"\(show(before))\", so follow \(walked) failure link\(walked == 1 ? "" : "s") first, then " : ""
        let hitText = hits.isEmpty ? "No pattern ends here." : "Match: \(hits.map { "\"\($0)\"" }.joined(separator: " and "))." + (hits.count > 1 ? " The second one came through the output link." : "")
        b.frame("Text '\(c)' (index \(i)): " + walkText + "move to \"\(show(current))\". " + hitText, active: [current], marked: terminals, links: links)
    }
    b.frame("Found \(found.map { "\"\($0)\"" }.joined(separator: ", ")) in one pass over \"\(text)\". The cost is O(n + m + z) — text length, total dictionary length, matches reported — with the number of patterns absent from the scan term entirely.",
            marked: terminals, links: links)
    return b.frames
}

// MARK: - FP-Growth

private func fpGrowthFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let order = fpItemOrder()
    let root = b.add("root", nil, 0)
    b.frame("Pass 1 counts single items and throws away everything below support \(minSupport): \(order.map { "\($0)=\(support([$0]))" }.joined(separator: ", ")) survive, \(basketItems.filter { !order.contains($0) }.map { "\($0)=\(support([$0]))" }.joined(separator: ", ")) does not. The surviving items are then ordered by descending count — that order is what makes the tree compress, because the most common items become shared prefixes.",
            active: [root])
    var ids: [String: Int] = [:]
    var counts: [Int: Int] = [:]
    var perItem: [String: [Int]] = [:]
    for (index, transaction) in transactions.enumerated() {
        let sorted = fpSortedTransaction(transaction)
        var parent = root
        var touched = Set<Int>()
        var reusedDepth = 0
        var reusing = true
        for (depth, item) in sorted.enumerated() {
            let key = "\(parent)|\(item)"
            if let existing = ids[key] {
                counts[existing]! += 1
                b.relabel(existing, "\(item):\(counts[existing]!)")
                parent = existing
                if reusing { reusedDepth = depth + 1 }
            } else {
                reusing = false
                let id = b.add("\(item):1", parent, depth)
                ids[key] = id
                counts[id] = 1
                perItem[item, default: []].append(id)
                parent = id
            }
            touched.insert(parent)
        }
        let dropped = transaction.count != sorted.count ? ", which becomes \(sorted.joined()) once infrequent items are dropped" : ""
        let tail = reusedDepth > 0
            ? "Its first \(reusedDepth) item\(reusedDepth > 1 ? "s" : "") already exist as a path, so those nodes just have their counter incremented — no new storage at all. That reuse is the compression."
            : "No existing path shares its prefix, so it becomes a new branch off the root."
        b.frame("Basket \(index + 1) is \(transaction.sorted().joined())\(dropped), sorted into header order. " + tail, active: touched)
    }
    let tree = buildFpTree()
    let slots = transactions.reduce(0) { $0 + fpSortedTransaction($1).count }
    b.frame("All \(transactions.count) baskets are in. They contained \(slots) item slots between them and the tree holds \(tree.nodes.count) nodes — the database, complete, with every count recoverable and every duplicate prefix stored once. On a real basket dataset that ratio is the reason this method exists.")
    let target = "E"
    let chain = perItem[target] ?? []
    let links = zip(chain, chain.dropFirst()).map { TreeLink(from: $0, to: $1) }
    b.frame("The header table keeps, for each item, a linked list through every node holding it. Here is \(target)'s: \(chain.count) nodes scattered across the tree, \(chain.reduce(0) { $0 + counts[$1]! }) occurrences in total — which matches the \(support([target])) baskets containing \(target), as it must.",
            active: Set(chain), links: links)
    let base = conditionalPatternBase(tree, target)
    var above = Set<Int>()
    for id in chain {
        var cur: Int? = id
        while let c = cur { above.insert(c); cur = b.parentOf(c) }
    }
    b.frame("Mining \(target): walk that chain and read the path above each node. The conditional pattern base is \(base.map { "\($0.path.joined().isEmpty ? "∅" : $0.path.joined())×\($0.count)" }.joined(separator: ", ")) — every context in which \(target) occurred, with its multiplicity.",
            path: above.subtracting(chain).subtracting([root]), marked: Set(chain), links: links)
    var conditional: [(String, Int)] = []
    var distinctItems: [String] = []
    for item in base.flatMap(\.path) where !distinctItems.contains(item) { distinctItems.append(item) }
    for item in distinctItems {
        let total = base.filter { $0.path.contains(item) }.reduce(0) { $0 + $1.count }
        if total >= minSupport { conditional.append((item, total)) }
    }
    let itemCounts = Array(Set(base.flatMap(\.path))).sorted().map { item in "\(item)=\(base.filter { $0.path.contains(item) }.reduce(0) { $0 + $1.count })" }.joined(separator: ", ")
    b.frame("Count items within that base: \(itemCounts). Only \(conditional.isEmpty ? "nothing" : conditional.map(\.0).joined(separator: ", ")) clears support \(minSupport), so \(conditional.map { "\($0.0)\(target)" }.joined(separator: ", ")) is frequent — support \(conditional.first?.1 ?? 0), and Apriori found exactly the same set by counting candidates instead.",
            marked: Set(chain), links: links)
    b.frame("Two database passes total: one to count items, one to build the tree. Everything after that is recursion over conditional trees held in memory, with no candidate generation and no further scans. Apriori needed a pass per level. The cost is the tree itself — on data with little shared prefix structure it can be larger than the database, and then FP-Growth is the wrong choice.",
            marked: Set(chain))
    return b.frames
}

// MARK: - Dependency parsing

private func dependencyFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let words = DependencyLab.sentence
    let steps = DependencyLab.parse()
    let rootId = b.add("ROOT", nil, 0)
    var nodeOf: [Int: Int] = [:]
    for i in words.indices { nodeOf[i + 1] = b.add(words[i], nil, i + 1) }
    let allWords = Set(nodeOf.values)
    b.frame("A dependency parse is a set of labelled arcs, one per word: every token gets exactly one head, and one token — the main verb here — is headed by ROOT. No phrase nodes exist at all, which is the difference from a constituency tree.", active: allWords)
    b.frame("Arc-standard parsing runs a stack, a buffer and three moves. SHIFT pushes the next word; LEFT-ARC attaches the second stack item to the top and pops it; RIGHT-ARC attaches the top to the second and pops it. An arc is only drawn once its dependent has all of its own children, so nothing has to be revisited.", marked: [rootId])
    for (index, step) in steps.enumerated() {
        for word in 1...words.count {
            let id = nodeOf[word]!
            if let arc = step.arcs.first(where: { $0.dependent == word }) {
                b.reparent(id, arc.head == 0 ? rootId : nodeOf[arc.head]!, word)
            } else {
                b.reparent(id, nil, word)
            }
        }
        for arc in step.arcs { b.relabel(nodeOf[arc.dependent]!, "\(words[arc.dependent - 1]) · \(arc.label)") }
        let stackText = step.stack.map(DependencyLab.wordAt).joined(separator: " ")
        let bufferText = step.buffer.map(DependencyLab.wordAt).joined(separator: " ")
        let moveText: String
        switch step.move {
        case .shift: moveText = "SHIFT — push the next word; no arc yet."
        case .leftArc: moveText = "LEFT-ARC (\(step.label!)) — the second stack item becomes a dependent of the top and is removed."
        case .rightArc: moveText = "RIGHT-ARC (\(step.label!)) — the top becomes a dependent of the second item and is removed."
        }
        b.frame("Step \(index + 1) of \(steps.count): \(moveText)  ·  stack [\(stackText)]  buffer [\(bufferText)]  ·  \(step.arcs.count) arc\(step.arcs.count == 1 ? "" : "s") built.",
                active: Set(step.stack.compactMap { $0 == 0 ? rootId : nodeOf[$0] }), marked: Set(step.arcs.map { nodeOf[$0.dependent]! }))
    }
    b.frame("Done in \(DependencyLab.transitionCount()) transitions, which is exactly 2n for \(words.count) words — every word is shifted once and attached once, so a greedy parser is linear in sentence length. Chart-based (graph) parsers are O(n²) or O(n³) but search globally; the transition family trades that for speed and a classifier that only has to pick the next move.",
            path: [rootId], marked: allWords)
    b.frame("Scoring is per word. UAS counts heads only; LAS also requires the relation to match. A parse with \"small\" attached to the verb instead of the noun, and \"cat\" labelled nsubj instead of obj, scores UAS \(fx(DependencyLab.uas(DependencyLab.predictedHeads))) and LAS \(fx(DependencyLab.las(DependencyLab.predictedHeads, DependencyLab.predictedLabels))) — the gap between them is entirely label errors on attachments that were right.",
            marked: [nodeOf[2]!, nodeOf[6]!])
    b.frame("One structural limit: arc-standard can only build projective trees — no two arcs may cross. \"A hearing is scheduled on the issue today\" needs a crossing arc (hearing → on crosses scheduled → today) and is unreachable by any sequence of these three moves. Roughly 1% of English sentences and far more in German or Czech are non-projective, which is why pseudo-projective transforms, the swap transition and graph-based parsers exist.",
            marked: allWords)
    return b.frames
}

// MARK: - Constituency parsing

private func constituencyFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let gold = ConstituencyLab.gold
    var ids: [String: Int] = [:]
    func build(_ node: ConstituencyLab.Node, _ parent: Int?, _ order: Int, _ path: String) -> Int {
        let id = b.add(node.isLeaf ? "\(node.label) \(node.word!)" : node.label, parent, order)
        ids[path] = id
        for (i, child) in node.children.enumerated() { _ = build(child, id, i, "\(path)/\(i)") }
        return id
    }
    let root = build(gold, nil, 0, "")
    b.frame("A constituency parse groups words into nested phrases: the sentence is an NP and a VP, the NP is a determiner, an adjective and a noun. Every internal node is a phrase, and the words only appear at the leaves — where a dependency parse has arcs between words and no phrase nodes at all.", marked: [root])
    let spanText = ConstituencyLab.spanList(gold).map { "\($0.label)[\($0.start),\($0.end))" }.joined(separator: ", ")
    b.frame("The units it is scored on are labelled spans: \(spanText). Part-of-speech nodes are excluded by convention, because a tagger's accuracy would otherwise inflate the parser's score.",
            marked: [root, ids["/0"]!, ids["/1"]!, ids["/1/1"]!])
    let objectNp = ids["/1/1"]!, vp = ids["/1"]!, det = ids["/1/1/0"]!, noun = ids["/1/1/1"]!
    b.reparent(det, vp, 1)
    b.reparent(noun, vp, 2)
    b.remove(objectNp)
    let evalb = ConstituencyLab.evalb()
    b.frame("Here is a parse that misses one phrase: the object NP is flattened into the VP. Every bracket it does predict is correct, so precision is \(fx(evalb.precision)), but it recovers only \(ConstituencyLab.spanList(ConstituencyLab.predicted).count) of the gold tree's \(ConstituencyLab.spanList(gold).count) spans, so recall is \(fx(evalb.recall)) and F1 is \(fx(evalb.f1)). That asymmetry is why evalb reports all three: a parser that emits fewer, safer brackets can hold precision at 1.00 indefinitely.",
            active: [det, noun], marked: [vp])
    let restoredNp = b.add("NP", vp, 1)
    b.reparent(det, restoredNp, 0)
    b.reparent(noun, restoredNp, 1)
    b.frame("Restored. The two formalisms are convertible, and the conversion is a table of head rules: the head of a VP is its verb, the head of an NP is its rightmost noun, the head of an S is its VP's head. Percolate those upward and every phrase gets a head word.",
            marked: [restoredNp, vp, ids["/0"]!])
    let heads = ConstituencyLab.toDependencies()
    b.frame("Running that conversion on this tree produces heads \(heads.map(String.init).joined(separator: ", ")) — identical to the dependency lab's gold parse for the same sentence, computed here rather than asserted. This is how the Penn Treebank became a dependency treebank, and why a claim that one formalism carries more information than the other needs to be about the *annotation*, not the notation.",
            path: Set(ids.values).subtracting([root]), marked: [root])
    b.frame("Which to use: constituency when the phrase itself is the object of interest — extracting noun phrases, grammar checking, anything that asks \"is this a well-formed clause\" — and dependency when the question is what relates to what, which is most of information extraction and nearly all multilingual work, because dependency annotation transfers across languages with far less redesign (that is the entire premise of Universal Dependencies).",
            marked: [root])
    return b.frames
}

// MARK: - PCFG: CYK over an ambiguous sentence

private func pcfgProb(_ p: Double) -> String { p >= 0.01 ? fx(p) : fx(p, 4) }

private func pcfgFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let words = PcfgLab.sentence
    let vpReading = PcfgLab.vpAttachment()
    let vpTopParse = vpReading.right!, vpCoreParse = vpTopParse.left!, ppParse = vpTopParse.right!
    let npReading = PcfgLab.npAttachment()
    let vpBParse = npReading.right!, bigNpParse = vpBParse.right!
    let npManParse = vpCoreParse.right!, npScopeParse = ppParse.right!

    let wordIds = words.enumerated().map { b.add($1, nil, $0) }
    b.frame("Seven words, and the grammar is six binary rules plus a lexicon. A PCFG is a CFG where every rule carries a probability, and the rules sharing a left-hand side sum to 1 — so VP → V NP at 0.70 and VP → VP PP at 0.30 are a distribution over what a verb phrase can be.", active: Set(wordIds))
    let preLabels = ["NP \(pcfgProb(0.40))", "V \(pcfgProb(1))", "Det \(pcfgProb(1))", "N \(pcfgProb(0.50))", "P \(pcfgProb(1))", "Det \(pcfgProb(1))", "N \(pcfgProb(0.50))"]
    let preIds = wordIds.enumerated().map { i, wordId -> Int in
        let id = b.add(preLabels[i], nil, i)
        b.reparent(wordId, id, 0)
        return id
    }
    b.frame("Lexical rules first — this is CYK's diagonal, the width-1 spans. \"the\" is only ever a determiner, but a real lexicon gives most words several tags, and every one of them starts a different parse.", marked: Set(preIds))
    let npMan = b.add("NP \(pcfgProb(npManParse.probability))", nil, 2)
    b.reparent(preIds[2], npMan, 0)
    b.reparent(preIds[3], npMan, 1)
    let npScope = b.add("NP \(pcfgProb(npScopeParse.probability))", nil, 5)
    b.reparent(preIds[5], npScope, 0)
    b.reparent(preIds[6], npScope, 1)
    b.frame("Width-2 spans: NP → Det N fires twice, at 0.40 × 1.0 × 0.50 = \(pcfgProb(npManParse.probability)) each. A cell's probability is the rule's probability times its children's — the parse tree's score is the product of every rule in it.", active: [npMan, npScope])
    let pp = b.add("PP \(pcfgProb(ppParse.probability))", nil, 4)
    b.reparent(preIds[4], pp, 0)
    b.reparent(npScope, pp, 1)
    let vpCore = b.add("VP \(pcfgProb(vpCoreParse.probability))", nil, 1)
    b.reparent(preIds[1], vpCore, 0)
    b.reparent(npMan, vpCore, 1)
    b.frame("\"with the telescope\" becomes a PP at \(pcfgProb(ppParse.probability)), and \"saw the man\" a VP at \(pcfgProb(vpCoreParse.probability)). Everything so far is forced. The ambiguity is entirely about what the PP attaches to — and both answers are grammatical.", active: [pp, vpCore])
    let vpTop = b.add("VP \(pcfgProb(vpTopParse.probability))", nil, 1)
    b.reparent(vpCore, vpTop, 0)
    b.reparent(pp, vpTop, 1)
    let sA = b.add("S \(pcfgProb(vpReading.probability))", nil, 0)
    b.reparent(preIds[0], sA, 0)
    b.reparent(vpTop, sA, 1)
    b.frame("Reading 1 — VP attachment: the PP modifies the seeing, so she used the telescope to look. VP → VP PP at 0.30 × \(pcfgProb(vpCoreParse.probability)) × \(pcfgProb(ppParse.probability)) = \(pcfgProb(vpTopParse.probability)), and S → NP VP gives \(pcfgProb(vpReading.probability)).",
            path: [vpCore, pp], marked: [sA, vpTop])
    b.reparent(preIds[0], nil, 0)
    b.reparent(vpTop, nil, 1)
    b.remove(sA)
    b.reparent(vpCore, nil, 1)
    b.reparent(pp, nil, 4)
    b.remove(vpTop)
    b.reparent(preIds[1], nil, 1)
    b.reparent(npMan, nil, 2)
    b.remove(vpCore)
    let bigNp = b.add("NP \(pcfgProb(bigNpParse.probability))", nil, 2)
    b.reparent(npMan, bigNp, 0)
    b.reparent(pp, bigNp, 1)
    let vpB = b.add("VP \(pcfgProb(vpBParse.probability))", nil, 1)
    b.reparent(preIds[1], vpB, 0)
    b.reparent(bigNp, vpB, 1)
    let sB = b.add("S \(pcfgProb(npReading.probability))", nil, 0)
    b.reparent(preIds[0], sB, 0)
    b.reparent(vpB, sB, 1)
    b.frame("Reading 2 — NP attachment: the PP modifies the man, so he was the one holding the telescope. Same words, same grammar, different tree: NP → NP PP at 0.20 × \(pcfgProb(npManParse.probability)) × \(pcfgProb(ppParse.probability)) = \(pcfgProb(bigNpParse.probability)), then VP → V NP and S → NP VP for \(pcfgProb(npReading.probability)).",
            path: [vpB], marked: [sB, bigNp])
    b.reparent(preIds[0], nil, 0)
    b.reparent(vpB, nil, 1)
    b.remove(sB)
    b.reparent(preIds[1], nil, 1)
    b.reparent(bigNp, nil, 2)
    b.remove(vpB)
    b.reparent(npMan, nil, 2)
    b.reparent(pp, nil, 4)
    b.remove(bigNp)
    let vpCore2 = b.add("VP \(pcfgProb(vpCoreParse.probability))", nil, 1)
    b.reparent(preIds[1], vpCore2, 0)
    b.reparent(npMan, vpCore2, 1)
    let vpTop2 = b.add("VP \(pcfgProb(vpTopParse.probability))", nil, 1)
    b.reparent(vpCore2, vpTop2, 0)
    b.reparent(pp, vpTop2, 1)
    let sWinner = b.add("S \(pcfgProb(vpReading.probability))", nil, 0)
    b.reparent(preIds[0], sWinner, 0)
    b.reparent(vpTop2, sWinner, 1)
    b.frame("\(pcfgProb(vpReading.probability)) against \(pcfgProb(npReading.probability)) — VP attachment wins by \(fx(PcfgLab.attachmentRatio))×, and CYK keeps only the winner per cell plus a backpointer, so the losing parse costs no storage. The disambiguation is the whole reason the grammar is probabilistic: a plain CFG returns both trees and cannot rank them.",
            marked: [sWinner, vpTop2])
    b.frame("The cost: \(PcfgLab.filledCells()) chart cells filled out of n(n+1)/2 = \(words.count * (words.count + 1) / 2) spans, over \(PcfgLab.splitsConsidered()) split points and the whole grammar at each one — O(n³·|G|). Same table shape as matrix-chain multiplication, with max in place of min.",
            path: [vpTop2, vpCore2, pp, npMan, npScope], marked: [sWinner])
    b.frame("And the limit worth remembering: the rule probabilities here are estimated from a treebank and are context-free by construction, so this grammar prefers VP attachment for *every* sentence of this shape — it has no idea a telescope is an instrument of seeing. Lexicalised PCFGs condition each rule on its head word (saw … with … telescope) to fix exactly this, at the cost of a far sparser table.",
            marked: [sWinner])
    return b.frames
}

// MARK: - Tree of Thoughts

private func chainNodes(_ builder: TreeBuilder, _ trace: [String], _ root: Int) -> [Int] {
    var parent = root
    return trace.map { step in
        let id = builder.add(step, parent, 0)
        parent = id
        return id
    }
}

private func afterEquals(_ s: String) -> String { s.components(separatedBy: "= ").dropFirst().joined(separator: "= ").isEmpty ? s : s.components(separatedBy: "= ").dropFirst().joined(separator: "= ") }

private func treeOfThoughtsFrames() -> [TreeFrame] {
    var frames: [TreeFrame] = []
    let (expanded, solutions) = TotLab.exhaustive()
    let puzzleLabel = TotLab.label(TotLab.puzzle)

    let intro = TreeBuilder()
    let introRoot = intro.add(puzzleLabel, nil, 0)
    intro.frame("Game of 24: combine \(puzzleLabel) with + − × ÷ until one number is left and it is 24. Each operation replaces two numbers with one, so the search is exactly three moves deep. Expanded exhaustively this tree visits \(expanded) states and ends at 24 along \(solutions) of them — small enough to check every claim below by brute force.",
                active: [introRoot])
    frames += intro.frames

    let greedy = TotLab.beam(1, depth: 1)
    let gb = TreeBuilder()
    let greedyRoot = gb.add(puzzleLabel, nil, 0)
    let greedyNodes = chainNodes(gb, greedy.trace, greedyRoot)
    for (index, id) in greedyNodes.enumerated() {
        let status: String
        switch index {
        case 0: status = "A chain of thought is beam width 1: pick the best-looking next step and commit. The evaluator here is the cheap one — \"could one more operation on some pair land on 24?\" — which is the stand-in for the paper's value prompt, and it likes \(greedy.trace[0])."
        case 1: status = "Second move, still no way back. The state is now three numbers and the same evaluator picks \(greedy.trace[1]). Nothing has gone visibly wrong yet, which is the problem: a single chain gives no signal that it is already in a dead branch."
        default: status = "Third move, and the chain lands on \(afterEquals(greedy.trace.last!)) rather than 24. It cannot backtrack, because it never kept an alternative. Width 1 fails on this puzzle for a reason that has nothing to do with arithmetic."
        }
        gb.frame(status, active: [id], path: Set(greedyNodes.prefix(index)).union([greedyRoot]))
    }
    frames += gb.frames

    let ranked = TotLab.rankedFrontier(depth: 1)
    let (firstSolvableRank, solvableInTopFive, frontierSize) = TotLab.evaluatorQuality(5, depth: 1)
    let rb = TreeBuilder()
    let rankRoot = rb.add(puzzleLabel, nil, 0)
    let shown = ranked.prefix(5).enumerated().map { rb.add("\(TotLab.label($1.state))  (\(fx($1.score, 1)))", rankRoot, $0) }
    let best = ranked[firstSolvableRank - 1]
    let bestNode = rb.add("\(TotLab.label(best.state))  (\(fx(best.score, 1)))", rankRoot, 5)
    rb.frame("So before widening the search, measure the evaluator. It ranks \(frontierSize) distinct first moves, and the highest-ranked move that can still reach 24 is its \(firstSolvableRank)th — the five it likes best contain \(solvableInTopFive) that can. This is not a bad implementation; it is what a cheap one-step heuristic is worth on a four-number state, because it ignores the numbers left over.",
             active: Set(shown), marked: [bestNode])
    frames += rb.frames

    let narrow = TotLab.beam(5, depth: 1)
    let needed = TotLab.widthNeeded(1)
    let winner = TotLab.beam(needed, depth: 1)
    let wb = TreeBuilder()
    let widthRoot = wb.add(puzzleLabel, nil, 0)
    let keptFive = ranked.prefix(5).enumerated().map { wb.add(TotLab.label($1.state), widthRoot, $0) }
    wb.frame("Beam width 5 keeps the evaluator's top five and drops the other \(frontierSize - 5). Since none of those five can reach 24, the beam is already lost at depth 1 and spends the rest of the search confirming it: \(narrow.expanded) states expanded, best result \(afterEquals(narrow.trace.last!)).",
             active: Set(keptFive))
    let extra = ranked[5..<needed].enumerated().map { (wb.add(TotLab.label($1.state), widthRoot, 5 + $0), $1.solvable) }
    let firstSolvable = extra.first { $0.1 }!.0
    wb.frame("Width \(needed) is where it turns over — the smallest beam that keeps a state the puzzle can be solved from. Nothing about the evaluator changed. The search simply stopped trusting it enough to throw the answer away, which is the entire argument for Tree of Thoughts.",
             active: Set(extra.filter(\.1).map(\.0)), path: Set(keptFive))
    let winPath = chainNodes(wb, winner.trace, firstSolvable)
    wb.frame("The solution the surviving branch reaches: \(winner.trace.joined(separator: ", ")). It cost \(winner.expanded) expanded states and \(winner.evaluatorCalls) evaluator calls against \(greedy.expanded) and \(greedy.evaluatorCalls) for the greedy chain — roughly \(fx(Double(winner.evaluatorCalls) / Double(greedy.evaluatorCalls), 0))× the evaluation, for the difference between failing and finishing.",
             path: [widthRoot, firstSolvable], marked: Set(winPath))
    frames += wb.frames

    let deep = TotLab.beam(1, depth: 2)
    let deepWidth = TotLab.widthNeeded(2)
    let (deepRank, deepTopFive, _) = TotLab.evaluatorQuality(5, depth: 2)
    let db = TreeBuilder()
    let deepRoot = db.add(puzzleLabel, nil, 0)
    let deepNodes = Set(chainNodes(db, deep.trace, deepRoot))
    db.frame("Width is not the only axis. Give the evaluator one more operation of lookahead and its ranking changes completely: the best-ranked solvable state is now its \(deepRank)th and \(deepTopFive) of its top five can reach 24. Width \(deepWidth) is then enough — a single chain solves the puzzle, because the evaluator no longer throws the answer away.",
             path: [deepRoot], marked: deepNodes)
    db.frame("But price the two. The cheap evaluator at width \(needed) spends \(winner.evaluatorCalls) evaluator calls; the deep evaluator at width \(deepWidth) spends \(deep.evaluatorCalls). The better evaluator is \(fx(Double(deep.evaluatorCalls) / Double(winner.evaluatorCalls), 1))× *more* expensive here, not less. Width and evaluator quality are substitutes, and which one is cheaper is a measurement, not a principle — with an LLM as the evaluator, each of those calls is a request.",
             marked: deepNodes)
    db.frame("The rest of Tree of Thoughts is bookkeeping on top of this: a frontier instead of a single state, an evaluator that scores partial states, and pruning that is allowed to be wrong because the beam keeps alternatives. Exhaustive search over this puzzle visits \(expanded) states and finds \(solutions) solution paths — the beam found one of them after \(winner.expanded) expansions, which is \(fx(Double(expanded) / Double(winner.expanded), 1))× less of the tree.",
             marked: deepNodes)
    frames += db.frames
    return frames
}

// MARK: - Interview-prep pattern trees

private final class PatternTree {
    let b: TreeBuilder
    let root: Int
    let children: [Int: [Int]]
    init(_ b: TreeBuilder) {
        self.b = b
        root = b.add("5", nil, 0)
        let n4 = b.add("4", root, 0), n8 = b.add("8", root, 1)
        let n11 = b.add("11", n4, 0), n13 = b.add("13", n8, 0), n4b = b.add("4", n8, 1)
        let n7 = b.add("7", n11, 0), n2 = b.add("2", n11, 1), n1 = b.add("1", n4b, 0)
        children = [root: [n4, n8], n4: [n11], n8: [n13, n4b], n11: [n7, n2], n4b: [n1]]
    }
    func valueOf(_ id: Int) -> Int { Int(b.labelOf(id))! }
    func childrenOf(_ id: Int) -> [Int] { children[id] ?? [] }
    func isLeaf(_ id: Int) -> Bool { childrenOf(id).isEmpty }
}

private func treeBfsPatternFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let tree = PatternTree(b)
    b.frame("Level order needs the nodes grouped by depth, not just visited in the right sequence. The queue holds one contiguous frontier.")
    var queue = [tree.root]
    var done = Set<Int>()
    var depth = 0
    while !queue.isEmpty {
        let size = queue.count
        var level: [Int] = []
        b.frame("Level \(depth) starts. Freeze size = \(size) before touching the queue — those \(size) node(s) are this level, and everything pushed from here belongs to the next one.", active: Set(queue), marked: done)
        for _ in 0..<size {
            let id = queue.removeFirst()
            level.append(id)
            done.insert(id)
            queue += tree.childrenOf(id)
            b.frame("Pop \(b.labelOf(id)) and enqueue its \(tree.childrenOf(id).count) child(ren). The queue now mixes level \(depth) leftovers with level \(depth + 1) — which is why the size was frozen.",
                    active: [id], path: Set(queue), marked: done.subtracting([id]))
        }
        b.frame("Level \(depth) = [\(level.map(b.labelOf).joined(separator: ", "))].", marked: done)
        depth += 1
    }
    b.frame("\(depth) levels, \(done.count) nodes, each enqueued and dequeued exactly once — O(n) time, and peak memory is the widest level rather than the height.", marked: done)
    return b.frames
}

private func treeDfsPatternFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let tree = PatternTree(b)
    let target = 22
    var path: [Int] = []
    var found = Set<Int>()
    b.frame("Find every root-to-leaf path summing to \(target). The running total travels down as an argument; nothing has to be returned up.")
    func walk(_ id: Int, _ remaining: Int) {
        path.append(id)
        let left = remaining - tree.valueOf(id)
        if tree.isLeaf(id) {
            let hit = left == 0
            if hit { found.formUnion(path) }
            b.frame("Leaf \(b.labelOf(id)): budget after subtracting is \(left). " + (hit ? "Exactly spent — the path \(path.map(b.labelOf).joined(separator: " → ")) sums to \(target)." : "Not zero, so this path misses. Pop back up."),
                    active: [id], path: Set(path).subtracting([id]), marked: found)
        } else {
            b.frame("At \(b.labelOf(id)): \(remaining) − \(tree.valueOf(id)) = \(left) left for the subtree below.", active: [id], path: Set(path).subtracting([id]), marked: found)
            for c in tree.childrenOf(id) { walk(c, left) }
        }
        path.removeLast()
    }
    walk(tree.root, target)
    b.frame("Every node was entered once — O(n) — and the only extra memory was the call stack plus the current path, so O(h). A skewed tree, not a wide one, is this pattern's expensive case.", marked: found)
    return b.frames
}

private func bstInorderPatternFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let n8 = b.add("8", nil, 0)
    let n4 = b.add("4", n8, 0), n12 = b.add("12", n8, 1)
    let n2 = b.add("2", n4, 0), n6 = b.add("6", n4, 1)
    let n10 = b.add("10", n12, 0), n14 = b.add("14", n12, 1)
    let n1 = b.add("1", n2, 0), n3 = b.add("3", n2, 1)
    let left = [n8: n4, n4: n2, n12: n10, n2: n1]
    let right = [n8: n12, n4: n6, n12: n14, n2: n3]
    var stack: [Int] = []
    var emitted: [Int] = []
    let k = 4
    b.frame("In-order on a BST emits its keys in sorted order — that one fact answers k-th smallest, validation, successor and range counting. Done iteratively, it can also stop early.")
    var cur: Int? = n8
    while cur != nil || !stack.isEmpty {
        var spine: [Int] = []
        while let c = cur {
            stack.append(c)
            spine.append(c)
            cur = left[c]
        }
        if !spine.isEmpty {
            b.frame("Push the left spine \(spine.map(b.labelOf).joined(separator: " → ")). Nothing smaller than the stack top can still be unvisited, so the top is always the next key in order.",
                    active: Set(spine), path: Set(stack).subtracting(spine), marked: Set(emitted))
        }
        let node = stack.removeLast()
        emitted.append(node)
        let move = right[node].map { "Now move right to \(b.labelOf($0)) and push its left spine." } ?? "No right child, so the next key is already waiting on the stack."
        let stop = emitted.count == k ? " A k-th-smallest query with k = \(k) stops right here: O(height + k), and the right half of the tree is never touched." : ""
        b.frame("Pop \(b.labelOf(node)) — key \(emitted.count) of the sorted order. " + move + stop, active: [node], path: Set(stack), marked: Set(emitted).subtracting([node]))
        cur = right[node]
    }
    b.frame("\(emitted.map(b.labelOf).joined(separator: ", ")) — sorted, from a structure that was never sorted. Every node was pushed and popped exactly once: O(n) time, O(height) space, versus O(n) for a recursive traversal that materialises the whole list.",
            marked: Set(emitted))
    return b.frames
}

private func treeDpPatternFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let tree = PatternTree(b)
    var down: [Int: Int] = [:]
    var best = 0
    var bestAt = tree.root
    b.frame("Diameter of this tree, post-order. Each node returns its longest *downward* path; the best path *through* the node combines two children and is recorded, never returned. Mixing those up is the classic bug.")
    func walk(_ id: Int) {
        for c in tree.childrenOf(id) { walk(c) }
        let childDowns = tree.childrenOf(id).map { down[$0]! }.sorted(by: >)
        let through = 1 + childDowns.prefix(2).reduce(0, +)
        let returned = 1 + (childDowns.first ?? 0)
        down[id] = returned
        if through > best { best = through; bestAt = id }
        let base = b.labelOf(id).components(separatedBy: "·")[0]
        b.relabel(id, "\(base)·↓\(returned)")
        b.frame(tree.isLeaf(id)
                ? "Leaf \(b.labelOf(id)): nothing below, so it returns a downward path of 1 and combines to 1."
                : "\(b.labelOf(id)) sees child paths \(childDowns.map(String.init).joined(separator: " and ")). Through it: 1 + \(childDowns.prefix(2).map(String.init).joined(separator: " + ")) = \(through) nodes — recorded, because a parent cannot use a path that bends here. Returned upward: 1 + \(childDowns[0]) = \(returned).",
                active: [id], path: Set(tree.childrenOf(id)), marked: through == best ? [id] : [])
    }
    walk(tree.root)
    func deepest(_ id: Int) -> [Int] {
        let kids = tree.childrenOf(id)
        if kids.isEmpty { return [id] }
        return [id] + deepest(kids[argmaxFirst(kids.map { down[$0]! })])
    }
    let branches = tree.childrenOf(bestAt).sortedByDescending { down[$0]! }.prefix(2)
    let diameterPath = Set(branches.flatMap(deepest) + [bestAt])
    b.frame("Diameter = \(best) nodes, bending at \(b.labelOf(bestAt)). One post-order pass, O(n) time and O(height) stack — and the answer never travelled upward, which is why `best` lives outside the recursion.",
            active: [bestAt], marked: diameterPath)
    return b.frames
}

private func triePrefixPatternFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let root = b.add("·", nil, 0)
    var children: [String: Int] = [:]
    var childKeysParents: [Int] = []
    var terminal = Set<Int>()
    var prefixCount: [Int: Int] = [:]
    func childOf(_ node: Int, _ c: Character) -> Int? { children["\(node)|\(c)"] }
    func insert(_ word: String) {
        var node = root
        var path = [root]
        var created = 0
        for c in word {
            if let existing = childOf(node, c) { node = existing } else {
                let slot = childKeysParents.filter { $0 == node }.count
                let id = b.add(String(c), node, slot)
                children["\(node)|\(c)"] = id
                childKeysParents.append(node)
                created += 1
                node = id
            }
            prefixCount[node, default: 0] += 1
            path.append(node)
        }
        terminal.insert(node)
        b.relabel(node, "\(b.labelOf(node))●")
        b.frame("Insert \"\(word)\": " + (created == 0 ? "every character already had a node — only the word-end mark is new." : "\(created) new node(s), the rest shared with words already stored.") + " The ● marks a word end; without it the trie cannot tell a stored word from a passing prefix.",
                active: [node], path: Set(path).subtracting([node]), marked: terminal.subtracting([node]))
    }
    b.frame("A trie stores one node per character and one path per word, so words sharing a prefix share nodes.")
    for w in ["app", "apple", "apt", "bat"] { insert(w) }
    func walk(_ query: String) -> [Int]? {
        var node = root
        var path = [root]
        for c in query {
            guard let next = childOf(node, c) else { return nil }
            node = next
            path.append(node)
        }
        return path
    }
    let appPath = walk("app")!
    b.frame("search(\"app\") walks a-p-p and finds ● on the last node — a stored word. Cost is O(3), the length of the query, no matter how many words the trie holds.",
            active: [appPath.last!], path: Set(appPath.dropLast()), marked: terminal)
    let applPath = walk("appl")!
    b.frame("search(\"appl\") walks the same way and the path exists — but the last node has no ●, so \"appl\" is not a stored word. startsWith(\"appl\") is true on that same walk. Same traversal, different acceptance test.",
            active: [applPath.last!], path: Set(applPath.dropLast()), marked: terminal)
    let apPath = walk("ap")!
    b.frame("A counter per node turns the trie into an index: the \"ap\" node was crossed by \(prefixCount[apPath.last!]!) insertions, so \"ap\" has that many completions — answered without visiting any of them. Autocomplete then DFSes only what hangs below this node.",
            active: [apPath.last!], path: Set(apPath.dropLast()), marked: terminal)
    return b.frames
}

private func binaryLiftingPatternFrames() -> [TreeFrame] {
    let b = TreeBuilder()
    let a = b.add("A", nil, 0)
    let bb = b.add("B", a, 0), f = b.add("F", a, 1)
    let c = b.add("C", bb, 0), g = b.add("G", f, 0)
    let d = b.add("D", c, 0)
    let e = b.add("E", d, 0)
    let parent = [bb: a, f: a, c: bb, g: f, d: c, e: d]
    let depth = [a: 0, bb: 1, f: 1, c: 2, g: 2, d: 3, e: 4]
    var up = [[Int: Int]](repeating: [:], count: 3)
    up[0] = parent
    for j in 1..<3 { for v in Array(parent.keys) + [a] { if let mid = up[j - 1][v], let top = up[j - 1][mid] { up[j][v] = top } } }
    func name(_ id: Int?) -> String { id.map(b.labelOf) ?? "root sentinel" }
    b.frame("A rooted tree with depths 0 to \(depth[e]!). Answering \"the k-th ancestor of E\" by walking up k times is O(k) per query; binary lifting pays O(n log n) once and answers in O(log n) forever after.",
            path: [e, d, c, bb], marked: [a])
    b.frame("up[0][v] is just the parent — the table's base row, free to fill.", active: [e], path: [d])
    b.frame("up[1][v] = up[0][up[0][v]]: a 2-step is two 1-steps, so the second row is built from the first. up[1][E] = \(name(up[1][e])).", active: [e], path: [c])
    b.frame("up[2][v] = up[1][up[1][v]] — a 4-step is two 2-steps. up[2][E] = \(name(up[2][e])). Each row costs one pass over the nodes, and there are log n rows.", active: [e], path: [a])
    let k = 3
    let afterTwo = up[1][e]
    let afterOne = afterTwo.flatMap { up[0][$0] }
    b.frame("Query: the \(k)rd ancestor of E. \(k) in binary is 11, so take the 2-jump and then the 1-jump — never \(k) single steps. First: E → \(name(afterTwo)).",
            active: [e], path: Set([afterTwo].compactMap { $0 }))
    b.frame("Then the 1-jump: \(name(afterTwo)) → \(name(afterOne)). Two jumps instead of three steps here; for k in the millions it is still at most log k jumps.",
            active: Set([afterOne].compactMap { $0 }), path: Set([afterTwo, e].compactMap { $0 }), marked: Set([afterOne].compactMap { $0 }))
    b.frame("LCA(E, G) reuses the same table. E is at depth \(depth[e]!), G at \(depth[g]!) — lift E by the difference (\(depth[e]! - depth[g]!), one 2-jump) to \(name(up[1][e])) so both sit at the same depth.",
            active: [c, g], path: [e, d])
    b.frame("Now jump both by the largest power whose ancestors still *differ*: up[0][C] = \(name(up[0][c])) and up[0][G] = \(name(up[0][g])) differ, so both move. Stopping while they differ is what keeps the answer one step above.",
            active: [bb, f], path: [c, g, e, d])
    b.frame("up[0][B] = up[0][F] = A: the ancestors finally agree, so A is the LCA. Distance = depth[E] + depth[G] − 2·depth[A] = \(depth[e]!) + \(depth[g]!) − 0 = \(depth[e]! + depth[g]!) edges — the whole path query answered from depths alone.",
            path: [e, d, c, bb, g, f], marked: [a])
    return b.frames
}

// MARK: - Config

private let treeConfigs: [String: TreeConfig] = [
    "bst_inorder_pattern": TreeConfig(intro: "Iterative in-order over a nine-key BST. The stack holds a left spine, never the whole tree, and the fourth pop is where a k-th-smallest query would stop.", markedLabel: "Emitted in order", build: bstInorderPatternFrames, cardTitle: "IN-ORDER WALK", cardNote: "left, node, right"),
    "tree_dp_pattern": TreeConfig(intro: "Diameter computed post-order, with each node relabelled by the downward path it returns. The number it returns and the number it records are deliberately different.", markedLabel: "Diameter path", build: treeDpPatternFrames, cardTitle: "TREE DP", cardNote: "children first"),
    "trie_prefix_pattern": TreeConfig(intro: "Four words inserted into one prefix tree, then search versus startsWith on the same walk — the ● flag is the only thing separating them.", markedLabel: "Word end (●)", build: triePrefixPatternFrames, cardTitle: "PREFIX TRIE", cardNote: "one node per prefix"),
    "binary_lifting_pattern": TreeConfig(intro: "The jump table built row by row, then spent twice: a 3rd-ancestor query decomposed into 2 + 1, and an LCA that lifts both nodes without ever overshooting.", markedLabel: "Answer", build: binaryLiftingPatternFrames, cardTitle: "BINARY LIFTING", cardNote: "jumps of 2^k"),
    "tree_bfs_pattern": TreeConfig(intro: "Level-order traversal with the queue size frozen per level. Watch the queue hold two levels at once mid-sweep — that is exactly what the frozen size protects against.", markedLabel: "Visited", build: treeBfsPatternFrames, cardTitle: "LEVEL ORDER", cardNote: "queue"),
    "tree_dfs_pattern": TreeConfig(intro: "Root-to-leaf paths summing to 22. The budget is carried down as an argument and the path is popped on the way out, so siblings never inherit each other's state.", markedLabel: "On a matching path", build: treeDfsPatternFrames, cardTitle: "DEPTH-FIRST", cardNote: "stack"),
    "tree_of_thoughts": TreeConfig(intro: "Game of 24 searched for real: the greedy chain that fails, the evaluator's ranking measured against what can actually reach 24, and the beam width that fixes it — priced against a better evaluator.", markedLabel: "Reaches 24", build: treeOfThoughtsFrames, cardTitle: "TREE OF THOUGHTS", cardNote: "search over steps"),
    "dependency_parsing": TreeConfig(intro: "One sentence parsed by arc-standard transitions, stack and buffer replayed step by step — then UAS against LAS on a wrong parse, and the crossing arc this transition system cannot build.", markedLabel: "Attached", build: dependencyFrames, cardTitle: "DEPENDENCY PARSE", cardNote: "head → dependent"),
    "constituency_parsing": TreeConfig(intro: "The same sentence as nested phrases: the spans evalb scores, a flattened VP costing recall, and head rules converting the tree into the dependency parse next door.", markedLabel: "Constituent", build: constituencyFrames, cardTitle: "CONSTITUENCY PARSE", cardNote: "phrases nest"),
    "pcfg": TreeConfig(intro: "\"she saw the man with the telescope\" parsed by probabilistic CYK: the chart bottom-up, then both attachments of the prepositional phrase scored against each other.", markedLabel: "Best parse", build: pcfgFrames, cardTitle: "PCFG PARSE", cardNote: "P = product of rules"),
    "fp_growth": TreeConfig(intro: "Ten baskets compressed into a prefix tree in two passes, then mined by walking the header chain for one item back up to the root.", markedLabel: "Header chain", build: fpGrowthFrames, linkLabel: "Header link", cardTitle: "FP-TREE", cardNote: "shared prefixes"),
    "aho_corasick": TreeConfig(intro: "The dictionary {he, she, his, hers} as a trie, then its failure links, then one walk over \"ushers\". The dashed arcs are the failure links — they are the whole algorithm, and they are why the text pointer never backs up.", markedLabel: "Pattern ends here", build: ahoCorasickFrames, linkLabel: "Failure link", cardTitle: "AHO-CORASICK", cardNote: "trie + failure links"),
    "hierarchical_divisive": TreeConfig(intro: "The dendrogram built top-down: repeatedly split the least cohesive cluster, with the real diameters driving which one goes next.", markedLabel: "Cluster", build: divisiveFrames, cardTitle: "DIVISIVE SPLIT", cardNote: "top down"),
    "xgboost": TreeConfig(intro: "One tree built by the real second-order gain formula: G and H sums, the closed-form leaf value, and a split that γ prunes away.", markedLabel: "Leaf", build: xgboostFrames, cardTitle: "XGBOOST TREE", cardNote: "gain per split"),
    "lightgbm": TreeConfig(intro: "The same six-leaf budget spent level-wise and then leaf-wise, so the difference in shape and in captured gain is directly comparable.", markedLabel: "Leaf", build: lightgbmFrames, cardTitle: "LEAF-WISE GROWTH", cardNote: "best leaf first"),
    "catboost": TreeConfig(intro: "Oblivious trees: one condition per level, reused across the whole level, and what that buys at inference time.", markedLabel: "Leaf", build: catboostFrames, cardTitle: "OBLIVIOUS TREE", cardNote: "one test per level"),
    "lca": TreeConfig(intro: "LCA(H, E) on an 8-node tree: lift the deeper node until both are level, then climb them together until they meet.", markedLabel: "LCA", build: lcaFrames,
                      storyStyle: StoryStyle(canvasHeight: 210, rowGap: 58,
                                             legend: [(.active, "Current pair"), (.path, "Climbed"), (.marked, "LCA"), (.next, "Next climb")],
                                             depthGuides: true)),
    "tree_dp": TreeConfig(intro: "Maximum-weight independent set. Labels turn into \"not-taken / taken\" as the post-order pass resolves each subtree — children are always finished before their parent is touched.", markedLabel: "Chosen set", build: treeDpFrames,
                          storyStyle: StoryStyle(canvasHeight: 220, rowGap: 84,
                                                 legend: [(.active, "Solving"), (.path, "Solved child"), (.marked, "Chosen")], noteMono: true)),
    "binary_search_tree": TreeConfig(intro: "Inserting 50, 30, 70, 20, 40, 60, 80, then searching for 60. Every operation walks one root-to-leaf path, comparing once per level.", markedLabel: "Placed / found", build: bstFrames,
                                     storyStyle: StoryStyle(canvasHeight: 196, rowGap: 78, legend: [(.active, "Comparing"), (.path, "Path taken"), (.marked, "Result"), (.ghost, "Insert here")]),
                                     variants: [StoryVariant(label: "Insert", build: { bstStoryFrames(0) }),
                                                StoryVariant(label: "Search", build: { bstStoryFrames(1) }),
                                                StoryVariant(label: "Delete", build: { bstStoryFrames(2) })]),
    "tree": TreeConfig(intro: "One hierarchy, two traversal orders: depth-first goes as deep as it can before backtracking; breadth-first sweeps level by level.", markedLabel: "Visited", build: treeTraversalFrames,
                       storyStyle: StoryStyle(canvasHeight: 196, rowGap: 78, legend: [(.active, "Visiting"), (.marked, "Visited")]),
                       variants: [StoryVariant(label: "Inorder", build: { traversalFrames(0) }),
                                  StoryVariant(label: "Preorder", build: { traversalFrames(1) }),
                                  StoryVariant(label: "Postorder", build: { traversalFrames(2) })]),
    "heap": TreeConfig(intro: "A binary max-heap is a complete tree stored in an array. Each insert drops the value into the next free slot, then sifts it up while it beats its parent.", markedLabel: "Settled", build: heapFrames,
                       storyStyle: StoryStyle(canvasHeight: 196, rowGap: 72, legend: [(.active, "Comparing"), (.marked, "Root")]),
                       variants: [StoryVariant(label: "Max-heap", build: { heapStoryFrames(max: true) }),
                                  StoryVariant(label: "Min-heap", build: { heapStoryFrames(max: false) })]),
    "trie": TreeConfig(intro: "Inserting \"cat\", \"car\", \"dog\" — shared prefixes share nodes. Lookup cost depends on the word length, never on how many words are stored.", markedLabel: "Word end / found", build: trieFrames,
                       storyStyle: StoryStyle(canvasHeight: 214, rowGap: 58, legend: [(.active, "Adding"), (.path, "Shared prefix"), (.ring, "End of word")],
                                              noteMono: true, wideCells: true)),
    "avl_red_black_tree": TreeConfig(intro: "Three ascending inserts would make a plain BST degenerate into a list. A self-balancing tree detects the violation and rotates.", markedLabel: "Balanced", build: avlFrames,
                                     storyStyle: StoryStyle(canvasHeight: 214, rowGap: 76, legend: [(.warn, "Imbalanced"), (.active, "Pivot"), (.marked, "Balanced")]),
                                     variants: [StoryVariant(label: "AVL", build: { balanceFrames(redBlack: false) }),
                                                StoryVariant(label: "Red-Black", build: { balanceFrames(redBlack: true) },
                                                             style: StoryStyle(canvasHeight: 214, rowGap: 76, legend: [(.warn, "Red-red conflict"), (.active, "Pivot"), (.marked, "Settled")]))]),
    "segment_tree": TreeConfig(intro: "A sum query over [2, 1, 5, 3]: every internal node caches its range's sum, so a range query only touches O(log n) nodes.", markedLabel: "Contributes to answer", build: segmentTreeFrames,
                               storyStyle: StoryStyle(canvasHeight: 196, rowGap: 76,
                                                      legend: [(.active, "Current"), (.path, "Path"), (.marked, "Counted"), (.skipped, "Skipped")],
                                                      noteMono: true, wideCells: true)),
    "fenwick_tree": TreeConfig(intro: "A prefix query and a point update over the same eight values, each walking one bit of the index per step.", markedLabel: "Read",
                               build: { fenwickStoryFrames(update: false) },
                               storyStyle: StoryStyle(canvasHeight: 0, rowGap: 0, legend: [(.active, "Reading"), (.marked, "Read"), (.ghost, "Next")]),
                               variants: [StoryVariant(label: "Query", build: { fenwickStoryFrames(update: false) }),
                                          StoryVariant(label: "Update", build: { fenwickStoryFrames(update: true) },
                                                       style: StoryStyle(canvasHeight: 0, rowGap: 0, legend: [(.active, "Writing"), (.marked, "Updated"), (.ghost, "Next")]))]),
    "sparse_table": TreeConfig(intro: "Every power-of-two block's minimum, then one range query answered from two overlapping blocks.", markedLabel: "Answer",
                               build: sparseTableFrames,
                               storyStyle: StoryStyle(canvasHeight: 0, rowGap: 0, legend: [(.active, "Reading"), (.path, "In range"), (.answer, "Answer")], noteMono: true)),
    "b_tree": TreeConfig(intro: "An order-3 B-tree: nodes hold multiple keys and split at the median when full, so the tree stays shallow and wide — the shape disk and database indexes want.", markedLabel: "Settled", build: bTreeFrames,
                         storyStyle: StoryStyle(canvasHeight: 214, rowGap: 76, legend: [(.path, "Path"), (.active, "Inserting"), (.overflow, "Overflow")], stepNoun: "Insert"),
                         variants: [StoryVariant(label: "B-Tree", build: { bTreeStoryFrames(plus: false) }),
                                    StoryVariant(label: "B+ Tree", build: { bTreeStoryFrames(plus: true) })]),
    "priority_queue_adt": TreeConfig(intro: "The contract is insert, peek and remove-highest-priority. A binary heap keeps just enough order to serve it — watch how little of the structure each operation has to touch.", markedLabel: "Root / minimum", build: priorityQueueFrames,
                                     storyStyle: StoryStyle(canvasHeight: 196, rowGap: 68, legend: [(.marked, "Root / minimum"), (.active, "Just inserted")],
                                                            noteMono: true, stepNoun: "Insert")),
    "huffman_coding": TreeConfig(intro: "Merge the two rarest symbols, repeat, and the tree that falls out assigns short codes to common symbols. The bit totals at the end are counted from the tree it actually built.", markedLabel: "Merged", build: huffmanFrames,
                                storyStyle: StoryStyle(canvasHeight: 210, rowGap: 56, legend: [(.active, "Merging"), (.marked, "Merged"), (.ghost, "New node")], wideCells: true)),
    "disjoint_set": TreeConfig(intro: "A forest where the only thing a tree means is \"these elements are in one set\". Union by rank keeps it shallow, path compression flattens what it walks, and the final frame counts both against the naive version.", markedLabel: "Root / settled", build: disjointSetFrames,
                               storyStyle: StoryStyle(canvasHeight: 214, rowGap: 86,
                                                      legend: [(.active, "Start"), (.path, "Walked"), (.marked, "Root"), (.link, "Compressed link")])),
    "suffix_tree": TreeConfig(intro: "The suffixes of \"aba$\" inserted one at a time: edges carry substrings, and a shared prefix splits an edge.", markedLabel: "Leaf / suffix start", build: suffixTreeFrames,
                              storyStyle: StoryStyle(canvasHeight: 206, rowGap: 84, legend: [(.path, "Matched"), (.active, "Split + new edge"), (.marked, "Leaf = start index")], stepNoun: "Suffix"),
                              variants: [StoryVariant(label: "Suffix tree", build: suffixTreeFrames),
                                         StoryVariant(label: "Suffix array", build: suffixArrayFrames,
                                                      style: StoryStyle(canvasHeight: 0, rowGap: 0, legend: [(.active, "Inserting"), (.skipped, "Empty slot")], stepNoun: "Suffix"))]),
]

// MARK: - UI

/// Topics without a hand-written storyboard still get the story card: their status line split into a
/// headline and a sentence under it, and the default legend words.
private func defaultStyle(_ config: TreeConfig) -> StoryStyle {
    var legend: [(LegendKey, String)] = [(.active, "Current"), (.path, "Path"), (.marked, config.markedLabel)]
    if let link = config.linkLabel { legend.append((.link, link)) }
    return StoryStyle(canvasHeight: 240, rowGap: 64, legend: legend, noteMono: true)
}

private func genericStory(_ config: TreeConfig, _ status: String) -> TreeStory {
    let (head, tail) = LabCaption.split(status)
    return TreeStory(title: config.cardTitle, note: config.cardNote, stripLabel: nil, cells: [], chips: [],
                     headline: head, emphasis: .active, body: tail ?? "", markdown: true)
}

struct TreeVisualizerLab: View {
    private let config: TreeConfig
    private let key: String
    @State private var tab = 0

    init(topicId: String) {
        key = treeConfigs[topicId] == nil ? "binary_search_tree" : topicId
        config = treeConfigs[key]!
    }

    var body: some View {
        if key == "priority_queue_adt" { PriorityQueueLab() } else { player }
    }

    @ViewBuilder private var player: some View {
        let key = self.key
        let tab = self.tab
        AsyncFrameLab(key: "tree:\(key):\(tab)", speedMs: 700, build: {
            let c = treeConfigs[key]!
            return (tab < c.variants.count ? c.variants[tab].build : c.build)()
        }) { frames, playback in
            content(frames, playback)
        }
        .id(tab)
    }

    @ViewBuilder
    private func content(_ frames: [TreeFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        let variant = tab < config.variants.count ? config.variants[tab] : nil
        TreeStoryLab(style: variant?.style ?? config.storyStyle ?? defaultStyle(config),
                     frame: frame,
                     story: frame.story ?? genericStory(config, frame.status),
                     playback: playback,
                     captions: frames.map(\.status),
                     tabs: config.variants.map(\.label),
                     tab: $tab,
                     linkTint: config.storyStyle != nil ? activeColor : linkColor)
    }
}

// MARK: - Story card UI

private func storyFill(_ state: TreeState, _ palette: Palette) -> (fill: Color, ink: Color) {
    switch state {
    case .active: return (activeColor, onActiveColor)
    case .marked: return (markedColor, .white)
    case .path: return (pathColor, .white)
    case .idle: return (palette.muted.opacity(0.2), palette.onSurface)
    case .skipped: return (palette.muted.opacity(0.07), palette.muted.opacity(0.5))
    case .warn: return (SimColors.red, .white)
    case .ghost: return (.clear, activeColor)
    case .answer: return (SimColors.answer, .white)
    }
}

private func stateColor(_ state: TreeState) -> Color? {
    switch state {
    case .active: return activeColor
    case .path: return pathColor
    case .marked: return markedColor
    case .warn: return SimColors.red
    case .answer: return SimColors.answer
    default: return nil
    }
}

private struct TreeStoryLab: View {
    let style: StoryStyle
    let frame: TreeFrame
    let story: TreeStory
    let playback: PlaybackState
    let captions: [String]
    let tabs: [String]
    @Binding var tab: Int
    let linkTint: Color
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    private var states: Set<TreeState> {
        var out = Set<TreeState>()
        for n in frame.nodes {
            out.insert(n.state)
            n.keys?.forEach { out.insert($0.state) }
        }
        story.cells.forEach { out.insert($0.value.isEmpty ? .skipped : $0.state) }
        out.formUnion(story.edgeStates.values)
        if story.rows.contains(where: { $0.state == .current }) { out.insert(.active) }
        for block in story.grid { for row in block.rows { row.cells.forEach { out.insert($0.state) } } }
        return out
    }

    private func present(_ key: LegendKey) -> Bool {
        switch key {
        case .active: return states.contains(.active)
        case .path: return states.contains(.path)
        case .marked: return states.contains(.marked)
        case .skipped: return states.contains(.skipped)
        case .link: return !frame.links.isEmpty
        case .next: return !story.nextEdges.isEmpty
        case .overflow: return frame.nodes.contains { $0.overflow != nil }
        case .warn: return states.contains(.warn)
        case .ring: return frame.nodes.contains { $0.ring }
        case .ghost: return states.contains(.ghost)
        case .answer: return states.contains(.answer)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                HStack {
                    if tabs.isEmpty {
                        Text(story.title).font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted).lineLimit(1).minimumScaleFactor(0.8)
                    } else {
                        StoryTabs(labels: tabs, selected: $tab)
                    }
                    Spacer(minLength: 12)
                    if !story.note.isEmpty {
                        Text(story.note).font(style.noteMono ? AppFont.mono(13) : AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1).minimumScaleFactor(0.8)
                    }
                }
                if !frame.nodes.isEmpty {
                    StoryCanvas(style: style, frame: frame, story: story, linkTint: linkTint).padding(.top, 12)
                }
                if !story.formulaRows.isEmpty {
                    StoryFormulaRows(rows: story.formulaRows).padding(.top, 12)
                }
                ForEach(story.grid.indices, id: \.self) { i in
                    StoryGrid(block: story.grid[i]).padding(.top, i == 0 ? 12 : 16)
                }
                if !story.rows.isEmpty {
                    if let label = story.rowsLabel { StorySectionLabel(label: label, note: story.rowsNote) }
                    StoryRows(story: story).padding(.top, story.rowsLabel == nil ? 12 : 0)
                }
                if !story.cells.isEmpty {
                    if let label = story.stripLabel { StorySectionLabel(label: label, note: story.stripNote) }
                    StoryStrip(story: story, wide: style.wideCells)
                }
                if let note = story.footnote {
                    Text(note).font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted).padding(.top, 10)
                }
                let legend = style.legend.filter { present($0.0) }
                if !legend.isEmpty {
                    FlowLayout(spacing: 14, lineSpacing: 6) {
                        ForEach(legend.indices, id: \.self) { i in
                            let (key, styleLabel) = legend[i]
                            let label = story.legendLabels[key] ?? styleLabel
                            switch key {
                            case .active: StoryLegendItem(color: activeColor, label: label)
                            case .path: StoryLegendItem(color: pathColor, label: label)
                            case .marked: StoryLegendItem(color: markedColor, label: label)
                            case .skipped: StoryLegendItem(color: palette.muted.opacity(0.2), label: label)
                            case .link: StoryLegendItem(color: linkTint, label: label, dash: true)
                            case .next: StoryLegendItem(color: activeColor, label: label, dash: true)
                            case .overflow: StoryLegendItem(color: SimColors.red, label: label)
                            case .warn: StoryLegendItem(color: SimColors.red, label: label)
                            case .ring: StoryLegendItem(color: markedColor, label: label, ring: true)
                            // A ghost is drawn as a dashed box, so its key is one too.
                            case .ghost: StorySwatch(color: activeColor, style: .dashed, label: label)
                            case .answer: StoryLegendItem(color: SimColors.answer, label: label)
                            }
                        }
                    }
                    .padding(.top, 14)
                }
            }
            if !story.chips.isEmpty {
                ReadoutChips(parts: story.chips, accented: story.accentedChips, warned: story.warnedChips, positive: story.positiveChips)
                    .padding(.top, 16)
            }
            headline
                .font(AppFont.sans(20, .bold))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            if !story.body.isEmpty {
                Text(story.markdown ? inlineMarkdown(story.body) : AttributedString(story.body))
                    .font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3).padding(.top, 8)
            }
            PlaybackTransport(state: playback, captions: captions,
                              stepLabel: { [noun = style.stepNoun, count = captions.count] in "\(noun) \($0 + 1) of \(count)" })
        }
    }

    /// The headline with its `{…}` term in the colour of the node it names.
    private var headline: Text {
        if story.markdown { return Text(inlineMarkdown(story.headline)) }
        // The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
        let accent: Color = switch story.emphasis {
        case .active: scheme == .dark ? activeColor : Color(hex: 0xB7791F)
        case .path: pathColor
        default: markedColor
        }
        let yellow = scheme == .dark ? activeColor : Color(hex: 0xB7791F)
        return headlineSpans(story.headline).reduce(Text("")) { out, span in
            switch span.mark {
            case nil: return out + Text(span.text)
            case "w": return out + Text(span.text).foregroundColor(SimColors.red)
            case "a": return out + Text(span.text).foregroundColor(yellow)
            case "p": return out + Text(span.text).foregroundColor(pathColor)
            case "m": return out + Text(span.text).foregroundColor(markedColor)
            default: return out + Text(span.text).foregroundColor(accent)
            }
        }
    }
}

/// The compact segmented control in the card's corner (B-Tree / B+ Tree).
private struct StoryTabs: View {
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
                    .padding(.horizontal, 14)
                    .frame(height: 28)
                    .background(on ? (scheme == .dark ? Color(hex: 0x636366) : .white) : .clear, in: RoundedRectangle(cornerRadius: 7))
                    .contentShape(Rectangle())
                    .onTapGesture { selected = i }
            }
        }
        .padding(2)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
    }
}

/// A caps label over a block of the card, with an optional note on its right ("3 of 6").
private struct StorySectionLabel: View {
    let label: String
    let note: String?
    @Environment(\.palette) private var palette

    var body: some View {
        HStack {
            Text(label).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted)
            Spacer(minLength: 8)
            if let note { Text(note).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1) }
        }
        .padding(.top, 14).padding(.bottom, 8)
    }
}

private struct StoryLegendItem: View {
    let color: Color
    let label: String
    var dash = false
    var ring = false
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 6) {
            if ring {
                RoundedRectangle(cornerRadius: 3).stroke(color, lineWidth: 1.5).frame(width: 10, height: 10)
            } else if dash {
                RoundedRectangle(cornerRadius: 1).fill(color).frame(width: 12, height: 2)
            } else {
                RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 10, height: 10)
            }
            Text(label).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
        }
    }
}

/// The list under a suffix tree: one line per suffix, marked added / inserting / next.
private struct StoryRows: View {
    let story: TreeStory
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let yellow = scheme == .dark ? activeColor : Color(hex: 0xB7791F)
        let lines = stride(from: 0, to: story.rows.count, by: story.rowColumns).map { Array(story.rows[$0..<min($0 + story.rowColumns, story.rows.count)]) }
        VStack(spacing: 8) {
            ForEach(lines.indices, id: \.self) { li in
                HStack(spacing: 8) {
                    ForEach(lines[li].indices, id: \.self) { ri in
                        let row = lines[li][ri]
                        let shape = RoundedRectangle(cornerRadius: 10)
                        HStack(spacing: 12) {
                            if !row.index.isEmpty {
                                Text(row.index).font(.system(size: 13, design: .monospaced)).foregroundStyle(row.state == .current ? yellow : palette.muted)
                            }
                            Text(row.text).font(.system(size: 14, weight: story.plainStatus ? .medium : .bold, design: .monospaced))
                                .foregroundStyle(row.state == .done ? palette.onSurface : row.state == .current ? yellow : palette.muted.opacity(0.5))
                                .lineLimit(1)
                            Spacer(minLength: 4)
                            Text(row.status)
                                .font(story.plainStatus ? .system(size: 14, weight: .semibold, design: .monospaced) : AppFont.sans(12, .semibold))
                                .lineLimit(1)
                                .foregroundStyle(row.state == .done ? (story.plainStatus ? palette.onSurface : markedColor)
                                                 : row.state == .current ? yellow : palette.muted.opacity(0.6))
                        }
                        .padding(.horizontal, 12)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(row.state == .done ? palette.muted.opacity(0.10)
                                    : row.state == .current ? activeColor.opacity(story.rowBorder ? 0.10 : 0.16) : .clear, in: shape)
                        .overlay {
                            if row.state == .current, story.rowBorder { shape.stroke(activeColor, lineWidth: 1.5) }
                            else if row.state == .pending { shape.stroke(palette.muted.opacity(0.18), lineWidth: 1) }
                        }
                    }
                    ForEach(0..<(story.rowColumns - lines[li].count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                }
            }
        }
    }
}

/// The array the tree stands for, one cell per element: a letter or index above, the value in a tile
/// coloured like its node. A blank value is an empty slot; a query range gets a bracket underneath.
private struct StoryStrip: View {
    let story: TreeStory
    let wide: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        let gap: CGFloat = wide ? 8 : 6
        VStack(spacing: 6) {
            if story.cells.contains(where: { !$0.header.isEmpty }) {
                HStack(spacing: gap) {
                    ForEach(story.cells.indices, id: \.self) { i in
                        Text(story.cells[i].header).font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted)
                            .frame(maxWidth: .infinity)
                    }
                }
            }
            HStack(spacing: gap) {
                ForEach(story.cells.indices, id: \.self) { i in
                    let cell = story.cells[i]
                    let tone = storyFill(cell.state, palette)
                    let shape = RoundedRectangle(cornerRadius: wide ? 10 : 8)
                    Text(cell.value).font(.system(size: 15, weight: .bold, design: .monospaced)).foregroundStyle(tone.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: wide ? 46 : 40)
                        .background(tone.fill, in: shape)
                        .overlay {
                            if cell.value.isEmpty, story.dashedEmpty {
                                shape.stroke(palette.muted.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                            } else if cell.state == .skipped {
                                shape.stroke(palette.muted.opacity(0.12), lineWidth: 1)
                            }
                        }
                }
            }
            if let range = story.bracket {
                let count = CGFloat(story.cells.count)
                Canvas { ctx, size in
                    let cell = (size.width - gap * (count - 1)) / count
                    let left = CGFloat(range.lowerBound) * (cell + gap) + cell * 0.1
                    let right = CGFloat(range.upperBound) * (cell + gap) + cell * 0.9
                    let y = size.height - 2, top: CGFloat = 5
                    var p = Path()
                    p.move(to: CGPoint(x: left, y: top))
                    p.addLine(to: CGPoint(x: left, y: y))
                    p.addLine(to: CGPoint(x: right, y: y))
                    p.addLine(to: CGPoint(x: right, y: top))
                    ctx.stroke(p, with: .color(palette.muted.opacity(0.6)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                }
                .frame(height: 14)
                .padding(.top, -6)
            }
        }
    }
}

/// Rows of cells on a shared column grid: a sparse table's k-rows, or Fenwick bars that each span the
/// range they cover. A row's label and sub sit in a gutter on the left.
private struct StoryGrid: View {
    let block: StoryGridBlock
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let gutter: CGFloat = block.rows.contains { $0.label != nil } ? 44 : 0
        let gap: CGFloat = 6
        let yellow = scheme == .dark ? activeColor : Color(hex: 0xB7791F)
        VStack(alignment: .leading, spacing: 0) {
            if let title = block.title {
                Text(title).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).padding(.bottom, 8)
            }
            if let headers = block.headers {
                HStack(spacing: gap) {
                    ForEach(headers.indices, id: \.self) { i in
                        Text(headers[i]).font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted).frame(maxWidth: .infinity)
                    }
                }
                .padding(.leading, gutter).padding(.bottom, 6)
            }
            VStack(spacing: gap) {
                ForEach(block.rows.indices, id: \.self) { r in
                    let row = block.rows[r]
                    HStack(spacing: 0) {
                        if gutter > 0 {
                            let tint = row.labelState == .active ? yellow : palette.muted
                            VStack(alignment: .leading, spacing: 0) {
                                if let label = row.label { Text(label).font(.system(size: 12, weight: .semibold, design: .monospaced)) }
                                if let sub = row.sub { Text(sub).font(.system(size: 11, design: .monospaced)).opacity(0.8) }
                            }
                            .foregroundStyle(tint)
                            .frame(width: gutter, alignment: .leading)
                        }
                        GeometryReader { geo in
                            let cell = (geo.size.width - gap * CGFloat(block.columns - 1)) / CGFloat(block.columns)
                            ForEach(row.cells.indices, id: \.self) { i in
                                let c = row.cells[i]
                                let tone = storyFill(c.state, palette)
                                let shape = RoundedRectangle(cornerRadius: 8)
                                Text(c.text).font(.system(size: 14, weight: .bold, design: .monospaced))
                                    .foregroundStyle(c.state == .ghost ? yellow : tone.ink).lineLimit(1)
                                    .frame(width: cell * CGFloat(c.span) + gap * CGFloat(c.span - 1), height: geo.size.height)
                                    .background(tone.fill, in: shape)
                                    .overlay { if c.state == .ghost { shape.stroke(activeColor, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])) } }
                                    .offset(x: (cell + gap) * CGFloat(c.start))
                            }
                        }
                    }
                    .frame(height: block.rowHeight)
                }
            }
        }
    }
}

private struct StoryCanvas: View {
    let style: StoryStyle
    let frame: TreeFrame
    let story: TreeStory
    let linkTint: Color
    @Environment(\.palette) private var palette

    var body: some View {
        var children: [Int: [TreeNodeSpec]] = [:]
        for n in frame.nodes { if let p = n.parent { children[p, default: []].append(n) } }
        for k in children.keys { children[k] = children[k]!.sortedBy(\.order) }
        let roots = frame.nodes.filter { $0.parent == nil }.sortedBy(\.order)
        var depthById: [Int: Int] = [:]
        func depthWalk(_ id: Int, _ d: Int) {
            depthById[id] = d
            for k in children[id] ?? [] { depthWalk(k.id, d + 1) }
        }
        for r in roots { depthWalk(r.id, 0) }
        let maxDepth = depthById.values.max() ?? 0
        let byId = Dictionary(uniqueKeysWithValues: frame.nodes.map { ($0.id, $0) })
        let hasSub = frame.nodes.contains { $0.sub != nil }
        let hasOverflow = frame.nodes.contains { $0.overflow != nil }
        let hasBelow = frame.nodes.contains { $0.below != nil }
        let palette = self.palette
        let children2 = children

        return Canvas { ctx, size in
            let nodeH: CGFloat = hasSub ? 40 : 34
            // Chained B+ leaves need room between them for the arrows.
            let slot: CGFloat = story.leafChain ? 30 : 34, keyGap: CGFloat = 4, outlinePad: CGFloat = 4
            let gutter: CGFloat = style.depthGuides ? 30 : 0
            let padX: CGFloat = 6
            let available = size.width - gutter - 2 * padX
            let labelFont = Font.system(size: 14, weight: .bold, design: .monospaced)

            func slots(_ n: TreeNodeSpec) -> Int { max(n.capacity, n.keys?.count ?? 0) }
            func width(_ n: TreeNodeSpec) -> CGFloat {
                if n.keys != nil { return CGFloat(slots(n)) * slot + CGFloat(slots(n) - 1) * keyGap + (n.overflow != nil ? 2 * outlinePad : 0) }
                if n.label == "•" { return 26 }
                let measured = ctx.resolve(Text(n.label).font(labelFont)).measure(in: CGSize(width: 400, height: 40))
                return max(measured.width + 18, hasSub ? 44 : 34)
            }
            var widthById: [Int: CGFloat] = [:]
            for n in frame.nodes { widthById[n.id] = width(n) }

            // Width-aware leaf slots: each leaf gets its own width plus an equal share of what is left,
            // and parents centre over their children. Equal-width leaves come out evenly spaced.
            func leavesUnder(_ id: Int) -> [Int] {
                let kids = children2[id] ?? []
                return kids.isEmpty ? [id] : kids.flatMap { leavesUnder($0.id) }
            }
            let leaves = roots.flatMap { leavesUnder($0.id) }
            let total = leaves.reduce(0) { $0 + widthById[$1]! }
            let share = total <= available ? (available - total) / CGFloat(leaves.count) : 0
            let scale = total > available ? available / total : 1
            var xById: [Int: CGFloat] = [:]
            var cursor = gutter + padX
            for id in leaves {
                let w = widthById[id]! * scale + share
                xById[id] = cursor + w / 2
                cursor += w
            }
            func place(_ id: Int) -> CGFloat {
                if let x = xById[id] { return x }
                let kids = children2[id]!
                let x = kids.map { place($0.id) }.reduce(0, +) / CGFloat(kids.count)
                xById[id] = x
                return x
            }
            for r in roots { _ = place(r.id) }

            // In-order layout: each root gets its own region (BEFORE | AFTER), and inside it a node's x is its
            // in-order rank, so a lone right child still sits to the right of its parent. Order 0 is a left
            // child, anything else a right one.
            let arrowGap: CGFloat = 44
            let regionW = (available - CGFloat(roots.count - 1) * arrowGap) / CGFloat(roots.count)
            func regionLeft(_ i: Int) -> CGFloat { gutter + padX + CGFloat(i) * (regionW + arrowGap) }
            if story.inorder {
                for (i, root) in roots.enumerated() {
                    var order: [Int] = []
                    func visit(_ id: Int) {
                        let kids = children2[id] ?? []
                        for k in kids where k.order == 0 { visit(k.id) }
                        order.append(id)
                        for k in kids where k.order != 0 { visit(k.id) }
                    }
                    visit(root.id)
                    for (rank, id) in order.enumerated() { xById[id] = regionLeft(i) + (CGFloat(rank) + 0.5) / CGFloat(order.count) * regionW }
                }
            }

            let captionSpace: CGFloat = hasOverflow || hasBelow ? 20 : 0
            let panelSpace: CGFloat = story.panels.isEmpty ? 0 : 22
            let rowGap = maxDepth == 0 ? 0 : min(style.rowGap, (size.height - nodeH - captionSpace - panelSpace - 8) / CGFloat(maxDepth))
            // Centre the tree's block vertically, so a shallow frame (eight singletons) is not stuck to the top.
            let top = panelSpace + (size.height - panelSpace - CGFloat(maxDepth) * rowGap - captionSpace) / 2
            func py(_ d: Int) -> CGFloat { top + CGFloat(d) * rowGap }
            func point(_ id: Int) -> CGPoint { CGPoint(x: xById[id]!, y: py(depthById[id]!)) }

            for (i, title) in story.panels.enumerated() {
                let label = ctx.resolve(Text(title).font(AppFont.sans(11, .semibold)).tracking(1).foregroundStyle(palette.muted))
                ctx.draw(label, at: CGPoint(x: regionLeft(i) + regionW / 2, y: 0), anchor: .top)
                if i > 0 {
                    let y = top + CGFloat(maxDepth) * rowGap / 2
                    let x0 = regionLeft(i) - arrowGap + 10, x1 = regionLeft(i) - 10
                    var arrow = Path()
                    arrow.move(to: CGPoint(x: x0, y: y)); arrow.addLine(to: CGPoint(x: x1, y: y))
                    arrow.move(to: CGPoint(x: x1, y: y)); arrow.addLine(to: CGPoint(x: x1 - 6, y: y - 5))
                    arrow.move(to: CGPoint(x: x1, y: y)); arrow.addLine(to: CGPoint(x: x1 - 6, y: y + 5))
                    ctx.stroke(arrow, with: .color(palette.muted.opacity(0.7)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                }
            }

            if style.depthGuides {
                for level in 0...maxDepth {
                    let y = py(level)
                    let label = ctx.resolve(Text("d\(level)").font(.system(size: 12, weight: .semibold, design: .monospaced))
                        .foregroundStyle(level == story.focusDepth ? activeColor : palette.muted.opacity(0.6)))
                    ctx.draw(label, at: CGPoint(x: 0, y: y), anchor: .leading)
                    var rule = Path()
                    rule.move(to: CGPoint(x: gutter, y: y))
                    rule.addLine(to: CGPoint(x: size.width, y: y))
                    ctx.stroke(rule, with: .color(palette.muted.opacity(0.18)), style: StrokeStyle(lineWidth: 1, dash: [3, 4]))
                }
            }

            func lit(_ s: TreeState) -> Bool { s != .idle && s != .skipped }
            /// A multi-key parent sends child i down from the gap between its keys i-1 and i.
            func anchor(_ parent: TreeNodeSpec, _ child: TreeNodeSpec) -> CGPoint {
                let c = point(parent.id)
                guard parent.keys != nil else { return c }
                let i = children2[parent.id]!.firstIndex { $0.id == child.id }!
                let inner = widthById[parent.id]! - (parent.overflow != nil ? 2 * outlinePad : 0)
                let left = c.x - inner / 2
                let x = min(max(left + CGFloat(i) * (slot + keyGap) - keyGap / 2, left + 6), left + inner - 6)
                return CGPoint(x: x, y: c.y + nodeH / 2)
            }

            for node in frame.nodes {
                guard let p = node.parent, let parent = byId[p] else { continue }
                let next = story.nextEdges.contains(node.id)
                let explicit = story.edgeStates[node.id].flatMap(stateColor)
                // A story that names its coloured edges leaves every other edge plain.
                let onPath = !story.plainEdges && story.edgeStates.isEmpty && lit(node.state) && lit(parent.state)
                let color: Color = next ? activeColor : explicit ?? (node.state == .skipped ? palette.muted.opacity(0.22) : onPath ? pathColor : palette.muted.opacity(0.4))
                let from = anchor(parent, node)
                var to = point(node.id)
                if parent.keys != nil { to.y -= nodeH / 2 + (node.overflow != nil ? outlinePad : 0) }
                var line = Path()
                line.move(to: from)
                line.addLine(to: to)
                ctx.stroke(line, with: .color(color),
                           style: StrokeStyle(lineWidth: onPath || next || explicit != nil ? 2.5 : 1.5, lineCap: .round, dash: next ? [5, 4] : []))
                if let text = node.edge {
                    let ink: Color = explicit ?? (node.state == .skipped ? palette.muted.opacity(0.5) : palette.onSurface.opacity(0.85))
                    let label = ctx.resolve(Text(text).font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundStyle(ink))
                    let mid = CGPoint(x: (from.x + to.x) / 2, y: (from.y + to.y) / 2)
                    // Beside the line: a first child's label on its left, every other one on its right, so two
                    // sibling labels never meet in the gap between their edges.
                    if children2[p]!.first!.id == node.id {
                        ctx.draw(label, at: CGPoint(x: mid.x - 7, y: mid.y), anchor: .trailing)
                    } else {
                        ctx.draw(label, at: CGPoint(x: mid.x + 7, y: mid.y), anchor: .leading)
                    }
                }
            }

            // B+ leaves are chained: a short arrow from each leaf to the next one along.
            if story.leafChain {
                let chain = leaves.filter { depthById[$0] == maxDepth }
                for (a, b) in zip(chain, chain.dropFirst()) {
                    let y = point(a).y
                    let start = CGPoint(x: point(a).x + widthById[a]! * scale / 2 + 3, y: y)
                    let end = CGPoint(x: point(b).x - widthById[b]! * scale / 2 - 3, y: y)
                    guard end.x - start.x >= 5 else { continue }
                    var arrow = Path()
                    arrow.move(to: start); arrow.addLine(to: end)
                    arrow.move(to: end); arrow.addLine(to: CGPoint(x: end.x - 4, y: y - 3))
                    arrow.move(to: end); arrow.addLine(to: CGPoint(x: end.x - 4, y: y + 3))
                    ctx.stroke(arrow, with: .color(palette.muted.opacity(0.55)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
                }
            }

            // Links, with an arrowhead where they land. Union-Find's compressed pointer bows out to the left
            // of the walk it replaces; anything else arcs over the tree edges it would otherwise lie on.
            for link in frame.links {
                guard byId[link.from] != nil, byId[link.to] != nil else { continue }
                let a = point(link.from), z = point(link.to)
                var arc = Path()
                let c2: CGPoint, end: CGPoint
                if linkTint == activeColor, depthById[link.from]! - depthById[link.to]! >= 2 {
                    let start = CGPoint(x: a.x - widthById[link.from]! / 2, y: a.y - nodeH * 0.15)
                    end = CGPoint(x: z.x - widthById[link.to]! / 2 - 3, y: z.y + nodeH * 0.1)
                    let bow: CGFloat = 46
                    let c1 = CGPoint(x: start.x - bow, y: start.y - (start.y - end.y) * 0.3)
                    c2 = CGPoint(x: min(start.x, end.x) - bow * 0.8, y: end.y + (start.y - end.y) * 0.1)
                    arc.move(to: start)
                    arc.addCurve(to: end, control1: c1, control2: c2)
                } else {
                    c2 = CGPoint(x: (a.x + z.x) / 2, y: min(a.y, z.y) - 22)
                    let dx = z.x - c2.x, dy = z.y - c2.y
                    let len = max(sqrt(dx * dx + dy * dy), 0.001)
                    end = CGPoint(x: z.x - dx / len * (nodeH / 2 + 2), y: z.y - dy / len * (nodeH / 2 + 2))
                    arc.move(to: CGPoint(x: a.x, y: a.y - nodeH / 2))
                    arc.addQuadCurve(to: end, control: c2)
                }
                ctx.stroke(arc, with: .color(linkTint), style: StrokeStyle(lineWidth: 1.8, dash: [4, 3.5]))
                let dx = end.x - c2.x, dy = end.y - c2.y
                let len = max(sqrt(dx * dx + dy * dy), 0.001)
                let dir = CGPoint(x: dx / len, y: dy / len), normal = CGPoint(x: -dir.y, y: dir.x)
                let head: CGFloat = 7
                var tip = Path()
                tip.move(to: end)
                tip.addLine(to: CGPoint(x: end.x - dir.x * head + normal.x * head * 0.55, y: end.y - dir.y * head + normal.y * head * 0.55))
                tip.move(to: end)
                tip.addLine(to: CGPoint(x: end.x - dir.x * head - normal.x * head * 0.55, y: end.y - dir.y * head - normal.y * head * 0.55))
                ctx.stroke(tip, with: .color(linkTint), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
            }

            for node in frame.nodes {
                let center = point(node.id)
                if let keys = node.keys {
                    let n = slots(node)
                    let inner = CGFloat(n) * slot + CGFloat(n - 1) * keyGap
                    let left = center.x - inner / 2
                    if let caption = node.overflow {
                        let outline = Path(roundedRect: CGRect(x: left - outlinePad, y: center.y - nodeH / 2 - outlinePad,
                                                               width: inner + 2 * outlinePad, height: nodeH + 2 * outlinePad), cornerRadius: 11)
                        ctx.stroke(outline, with: .color(SimColors.red), lineWidth: 1.5)
                        let text = ctx.resolve(Text(caption).font(AppFont.sans(12, .semibold)).foregroundStyle(Color(hex: 0xF87171)))
                        ctx.draw(text, at: CGPoint(x: center.x, y: center.y + nodeH / 2 + outlinePad + 4), anchor: .top)
                    }
                    for i in 0..<n {
                        let rect = CGRect(x: left + CGFloat(i) * (slot + keyGap), y: center.y - nodeH / 2, width: slot, height: nodeH)
                        let tile = Path(roundedRect: rect, cornerRadius: 8)
                        ctx.fill(tile, with: .color(palette.surface))
                        if i < keys.count {
                            // An overfull key is tinted, not filled: the red outline already says "overflow".
                            let tone = keys[i].state == .warn ? (fill: SimColors.red.opacity(0.2), ink: Color(hex: 0xFCA5A5)) : storyFill(keys[i].state, palette)
                            ctx.fill(tile, with: .color(tone.fill))
                            if keys[i].state == .warn { ctx.stroke(tile, with: .color(SimColors.red.opacity(0.7)), lineWidth: 1) }
                            let text = ctx.resolve(Text(keys[i].value).font(.system(size: 15, weight: .bold, design: .monospaced)).foregroundStyle(tone.ink))
                            ctx.draw(text, at: CGPoint(x: rect.midX, y: rect.midY))
                        } else {
                            ctx.fill(tile, with: .color(palette.muted.opacity(0.06)))
                            ctx.stroke(tile, with: .color(palette.muted.opacity(0.28)), lineWidth: 1)
                        }
                    }
                    continue
                }
                let tone = storyFill(node.state, palette)
                let w = widthById[node.id]! * scale
                let rect = node.label == "•"
                    ? CGRect(x: center.x - w / 2, y: center.y - w / 2, width: w, height: w)
                    : CGRect(x: center.x - w / 2, y: center.y - nodeH / 2, width: w, height: nodeH)
                let tile = Path(roundedRect: rect, cornerRadius: 8)
                // Idle tiles are translucent, so they sit on an opaque base; edges must not show through.
                ctx.fill(tile, with: .color(palette.surface))
                ctx.fill(tile, with: .color(tone.fill))
                if node.state == .skipped { ctx.stroke(tile, with: .color(palette.muted.opacity(0.16)), lineWidth: 1) }
                if node.state == .ghost { ctx.stroke(tile, with: .color(activeColor), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])) }
                if node.ring { ctx.stroke(tile, with: .color(markedColor), lineWidth: 2) }
                if let below = node.below {
                    let ink: Color = switch node.state {
                    case .active: StoryTone.active.ink(palette)
                    case .path: StoryTone.path.ink(palette)
                    case .marked: StoryTone.done.ink(palette)
                    default: palette.muted
                    }
                    let caption = ctx.resolve(Text(below).font(AppFont.mono(13, .bold)).foregroundStyle(ink))
                    let size = caption.measure(in: CGSize(width: 200, height: 40))
                    // Backed by the card colour, so an edge running past the caption passes behind it.
                    let back = CGRect(x: center.x - size.width / 2 - 3, y: rect.maxY + 4, width: size.width + 6, height: size.height)
                    ctx.fill(Path(roundedRect: back, cornerRadius: 4), with: .color(palette.surface))
                    ctx.draw(caption, at: CGPoint(x: center.x, y: rect.maxY + 4), anchor: .top)
                }
                let label = ctx.resolve(Text(node.label).font(labelFont).foregroundStyle(tone.ink))
                if let sub = node.sub {
                    let caption = ctx.resolve(Text(sub).font(.system(size: 9, weight: .medium, design: .monospaced)).foregroundStyle(tone.ink.opacity(0.7)))
                    ctx.draw(caption, at: CGPoint(x: center.x, y: center.y - 8))
                    ctx.draw(label, at: CGPoint(x: center.x, y: center.y + 5))
                } else {
                    ctx.draw(label, at: center)
                }
                // The badge sits on the tile's top-right corner, half outside it.
                if let text = node.badge {
                    let ringColor = stateColor(node.badgeTone) ?? palette.muted.opacity(0.7)
                    let resolved = ctx.resolve(Text(text).font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundStyle(story.badgeInk ? ringColor : palette.onSurface))
                    let size = resolved.measure(in: CGSize(width: 60, height: 20))
                    let r = max(9, size.width / 2 + 4)
                    let c = CGPoint(x: rect.maxX - 1, y: rect.minY + 1)
                    let pill = Path(roundedRect: CGRect(x: c.x - r, y: c.y - 9, width: 2 * r, height: 18), cornerRadius: 9)
                    ctx.fill(pill, with: .color(palette.surface))
                    ctx.stroke(pill, with: .color(ringColor), lineWidth: 1.5)
                    ctx.draw(resolved, at: c)
                }
            }
        }
        .frame(height: style.canvasHeight)
    }
}

// MARK: - Priority queue lab
// Port of PriorityQueueLab.kt. A min-heap drawn twice, as the tree and as the array that stores it,
// with insert (sift up) and extract-min (sift down) as tabs. The comparison being made rides on the
// edge between the two nodes.

private enum PqTone { case idle, active, compared, swapped }

private struct PqFrame {
    let heap: [Int]
    let tones: [Int: PqTone]
    /// The edge up from this child carries the comparison ("3 < 4").
    var bubble: (child: Int, text: String)? = nil
    let formula: String
    let chips: [StoryChip]
    let headline: String
    let body: String
}

private let pqCapacity = 7

private func pqInsertFrames() -> [PqFrame] {
    let h0 = [4, 7, 9, 8, 10]
    return [
        PqFrame(heap: h0, tones: [:], formula: "parent(i) = (i − 1) / 2", chips: [StoryChip("size", "5"), StoryChip("min", "4")],
                headline: "Every parent is at most its children, so the minimum, {4}, sits at the root.",
                body: "The heap is a complete tree stored level by level in an array, so no pointers are needed."),
        PqFrame(heap: h0 + [3], tones: [5: .active], formula: "heap[5] = {3}", chips: [StoryChip("size", "5 → 6"), StoryChip("compares", "0")],
                headline: "Insert puts {3} in the next free slot, i5.",
                body: "That keeps the tree complete, but 3 may now be smaller than its parent."),
        PqFrame(heap: h0 + [3], tones: [5: .active, 2: .compared], bubble: (5, "3 < 9"),
                formula: "parent(5) = (5 − 1) / 2 = 2 → {3} < {p:9} → swap", chips: [StoryChip("compares", "1"), StoryChip("swaps", "0")],
                headline: "3 is smaller than its parent 9, so they swap.",
                body: "Sift-up compares a node only with its parent, never with its sibling."),
        PqFrame(heap: [4, 7, 3, 8, 10, 9], tones: [2: .active, 5: .swapped], formula: "swap(heap[2], heap[5])",
                chips: [StoryChip("compares", "1"), StoryChip("swaps", "1")],
                headline: "{3} climbs to i2 and 9 drops to i5.", body: "One level up for one compare."),
        PqFrame(heap: [4, 7, 3, 8, 10, 9], tones: [2: .active, 0: .compared, 5: .swapped], bubble: (2, "3 < 4"),
                formula: "parent(2) = (2 − 1) / 2 = 0 → {3} < {p:4} → swap", chips: [StoryChip("compares", "2"), StoryChip("swaps", "1")],
                headline: "3 is smaller than its parent 4, so they swap and 3 becomes the new minimum.",
                body: "It already passed 9 on the way up. Sift-up is at most one compare per level, so O(log n)."),
        PqFrame(heap: [3, 7, 4, 8, 10, 9], tones: [0: .active, 2: .swapped, 5: .swapped], formula: "peek() = {v:3}",
                chips: [StoryChip("compares", "2"), StoryChip("swaps", "2"), StoryChip("cost", "O(log n)", .answer)],
                headline: "3 reached the root, so peek() now returns {v:3}.",
                body: "Two compares for six elements: the work follows the height of the tree, not its size."),
    ]
}

private func pqExtractFrames() -> [PqFrame] {
    let h = [3, 7, 4, 8, 10, 9]
    return [
        PqFrame(heap: h, tones: [0: .active], formula: "extractMin() → {v:3}", chips: [StoryChip("size", "6"), StoryChip("min", "3")],
                headline: "Extract-min returns the root, {3}.",
                body: "The hard part is filling the hole it leaves without breaking the heap."),
        PqFrame(heap: [9, 7, 4, 8, 10], tones: [0: .active], formula: "heap[0] = heap[5] = {9}", chips: [StoryChip("size", "6 → 5"), StoryChip("compares", "0")],
                headline: "The last leaf, {9}, moves into the root's slot.",
                body: "The array shrinks by one and the tree stays complete, but 9 is far too big for the root."),
        PqFrame(heap: [9, 7, 4, 8, 10], tones: [0: .active, 1: .compared, 2: .compared], bubble: (2, "4 < 9"),
                formula: "min({p:7}, {p:4}) = 4 → {9} > 4 → swap", chips: [StoryChip("compares", "2"), StoryChip("swaps", "0")],
                headline: "9 is bigger than its smaller child, 4, so they swap.",
                body: "Sift-down picks the smaller child, so the new parent is at most its sibling too."),
        PqFrame(heap: [4, 7, 9, 8, 10], tones: [2: .active, 0: .swapped], formula: "swap(heap[0], heap[2])",
                chips: [StoryChip("compares", "2"), StoryChip("swaps", "1")],
                headline: "4 rises to the root and {9} sinks to i2.", body: "One level down for one pair of compares."),
        PqFrame(heap: [4, 7, 9, 8, 10], tones: [2: .active, 0: .swapped], formula: "children(2) = 5, 6 → none → stop",
                chips: [StoryChip("compares", "2"), StoryChip("swaps", "1"), StoryChip("min", "4", .answer)],
                headline: "i2 has no children, so {9} stops and the heap is valid again.",
                body: "At most one swap per level on the way down, so extract-min is O(log n) too."),
    ]
}

struct PriorityQueueLab: View {
    private let tabs: [(String, [PqFrame], [String])] = [
        ("Insert", pqInsertFrames(), ["Sifting up", "Parent compared", "Swapped down"]),
        ("Extract min", pqExtractFrames(), ["Sifting down", "Child compared", "Swapped up"]),
    ]
    @State private var tab = 0
    @State private var playback = PlaybackState(stepCount: 6, speedMs: 1000)
    @Environment(\.palette) private var palette

    var body: some View {
        let frames = tabs[tab].1
        let frame = frames[min(playback.index, frames.count - 1)]
        let present = Set(frame.tones.values)
        let words = tabs[tab].2
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                LabSegments(labels: tabs.map(\.0), selected: Binding(get: { tab }, set: { select($0) }))
                PqTree(frame: frame)
                    .frame(height: 186)
                    .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                    .padding(.top, 14)
                PqStrip(frame: frame).padding(.top, 12)
                StoryFormula(text: frame.formula).padding(.top, 12)
                StoryLegendRow(items: [(PqTone.active, SimColors.active, words[0]), (.compared, SimColors.blue, words[1]), (.swapped, SimColors.green, words[2])]
                    .filter { present.contains($0.0) }.map { ($0.1, .fill, $0.2) })
                    .padding(.top, 14)
            }
            StoryChips(chips: frame.chips).padding(.top, 16)
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) })
        }
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        playback = PlaybackState(stepCount: tabs[i].1.count, speedMs: 1000)
    }
}

private func pqColors(_ tone: PqTone?, _ palette: Palette) -> (Color, Color) {
    switch tone ?? .idle {
    case .idle: (palette.muted.opacity(0.22), palette.onSurface)
    case .active: (SimColors.active, Color(hex: 0x1F1A0A))
    case .compared: (SimColors.blue, .white)
    case .swapped: (SimColors.green.opacity(palette.dark ? 0.24 : 0.18), StoryTone.done.ink(palette))
    }
}

private struct PqTree: View {
    let frame: PqFrame
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let w: CGFloat = 44, h: CGFloat = 34
            func at(_ i: Int) -> CGPoint {
                let depth = Int(log2(Double(i + 1)))
                let first = (1 << depth) - 1
                let slots = CGFloat(1 << depth)
                return CGPoint(x: (CGFloat(i - first) + 0.5) * size.width / slots, y: 30 + CGFloat(depth) * 62)
            }
            for i in 1..<frame.heap.count {
                let lit = frame.bubble?.child == i
                var p = Path(); p.move(to: at((i - 1) / 2)); p.addLine(to: at(i))
                ctx.stroke(p, with: .color(lit ? SimColors.active : palette.muted.opacity(0.45)), lineWidth: lit ? 2.5 : 1.5)
            }
            for (i, v) in frame.heap.enumerated() {
                let c = at(i)
                let rect = CGRect(x: c.x - w / 2, y: c.y - h / 2, width: w, height: h)
                let tile = Path(roundedRect: rect, cornerRadius: 8)
                let (fill, ink) = pqColors(frame.tones[i], palette)
                ctx.fill(tile, with: .color(palette.surface))
                ctx.fill(tile, with: .color(fill))
                ctx.draw(ctx.resolve(Text("\(v)").font(AppFont.mono(16, .bold)).foregroundColor(ink)), at: c)
                ctx.draw(ctx.resolve(Text("i\(i)").font(AppFont.mono(10)).foregroundColor(palette.muted)), at: CGPoint(x: rect.maxX + 3, y: rect.minY + 2), anchor: .leading)
            }
            if let bubble = frame.bubble {
                let a = at((bubble.child - 1) / 2), b = at(bubble.child)
                let mid = CGPoint(x: (a.x + b.x) / 2 + (b.x > a.x ? 10 : -10), y: (a.y + b.y) / 2)
                let text = ctx.resolve(Text(bubble.text).font(AppFont.mono(12, .bold)).foregroundColor(Color(hex: 0x1F1A0A)))
                let size = text.measure(in: CGSize(width: 120, height: 30))
                let pill = CGRect(x: mid.x - size.width / 2 - 6, y: mid.y - size.height / 2 - 3, width: size.width + 12, height: size.height + 6)
                ctx.fill(Path(roundedRect: pill, cornerRadius: 6), with: .color(SimColors.active))
                ctx.draw(text, at: mid)
            }
        }
    }
}

private struct PqStrip: View {
    let frame: PqFrame
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 5) {
                ForEach(0..<pqCapacity, id: \.self) { i in
                    let tone = frame.tones[i]
                    Text("\(i)").font(AppFont.mono(12, tone == .active || tone == .compared ? .bold : .regular))
                        .foregroundStyle(tone == .active ? StoryTone.active.ink(palette) : tone == .compared ? StoryTone.path.ink(palette) : palette.muted)
                        .frame(maxWidth: .infinity)
                }
            }
            HStack(spacing: 5) {
                ForEach(0..<pqCapacity, id: \.self) { i in
                    let filled = i < frame.heap.count
                    let (fill, ink) = filled ? pqColors(frame.tones[i], palette) : (palette.muted.opacity(0.08), palette.muted.opacity(0.6))
                    Text(filled ? "\(frame.heap[i])" : "·").font(AppFont.mono(15, .bold)).foregroundStyle(ink)
                        .frame(maxWidth: .infinity).frame(height: 40)
                        .background(fill, in: RoundedRectangle(cornerRadius: 8))
                }
            }
        }
    }
}
