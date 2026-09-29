import SwiftUI

// Port of LinkedStructureSection.kt: nodes in a row with pointer lanes around them — forward links
// above, optional backward links below (doubly linked list), express lanes on top (skip list).

private enum LinkMark { case idle, path, active, result, ghost }
private struct LinkNode { let label: String; var mark: LinkMark = .idle }

private struct LinkFrame {
    let nodes: [LinkNode]
    /// Narration: what happened, then why it matters. When `detail` is nil the status is split after
    /// its first sentence.
    let status: String
    var backward = false
    var presence: [[Bool]] = []
    var levelFocus: Int?
    var readout: String?
    var detail: String? = nil
    /// Pointers drawn in the rewired blue: edge i joins node i and node i + 1.
    var rewired: Set<Int> = []
    /// Labels under nodes ("head", "new", "tail"). Nil means the default: head and tail on a doubly
    /// linked row, nothing otherwise.
    var tags: [Int: String]? = nil

    /// One caption for the transcript sheet and the scrub preview.
    var caption: String { detail.map { "\(status) \($0)" } ?? status }
}

private struct LinkConfig { let intro: String; let legend: [(Color, String)]; let build: () -> [LinkFrame] }

private let linkResult = SimColors.answer

private func f1(_ v: Double) -> String { String(format: "%.1f", v) }

private func doublyLinked() -> [LinkFrame] {
    let values = [10, 20, 30, 40]
    func row(_ list: [Int], _ marks: [Int: LinkMark] = [:]) -> [LinkNode] { list.enumerated().map { LinkNode(label: "\($1)", mark: marks[$0] ?? .idle) } }
    func all(_ m: LinkMark) -> [Int: LinkMark] { Dictionary(uniqueKeysWithValues: values.indices.map { ($0, m) }) }
    var f: [LinkFrame] = []
    f.append(LinkFrame(nodes: row(values), status: "Every node carries two pointers instead of one: next above, prev below.", backward: true,
                       detail: "That second pointer is the entire structural difference, and everything cheap or expensive below follows from it."))
    f.append(LinkFrame(nodes: row(values, all(.path)), status: "Traversal works from either end, with the same loop using prev instead of next.", backward: true,
                       readout: "hops each way = \(values.count)",
                       detail: "A singly linked list can only walk forward. Printing it backwards means a stack of \(values.count) pointers or reversing the list first.",
                       rewired: Set(0..<(values.count - 1))))
    // Insert after a node you already hold: the four pointer writes, and nothing shifts.
    let after = 1, inserted = 25
    var withNew = values
    withNew.insert(inserted, at: after + 1)
    f.append(LinkFrame(nodes: row(withNew, [after: .path, after + 1: .active, after + 2: .path]),
                       status: "Insert \(inserted) after \(values[after]). Four pointers change and nothing shifts.", backward: true,
                       readout: "pointers changed = 4 · cost = O(1)",
                       detail: "That is O(1) once you hold node \(values[after]). Walking there is still O(n).",
                       rewired: [after, after + 1], tags: [0: "head", after + 1: "new", withNew.count - 1: "tail"]))
    let t = 2
    f.append(LinkFrame(nodes: row(values, [t: .active]), status: "Say you already hold a pointer to \(values[t]). Deleting it needs its predecessor.", backward: true,
                       detail: "The handle might be a cache entry, an LRU node, or whatever an earlier insert returned."))
    f.append(LinkFrame(nodes: row(values, [t - 1: .path, t: .active, t + 1: .path]), status: "Delete \(values[t]) with two writes: prev.next = next and next.prev = prev.", backward: true,
                       readout: "writes = 2 · hops = 0 · cost = O(1)",
                       detail: "The node reads its own prev and next, so there is no search, however long the list is.",
                       rewired: [t - 1, t]))
    var m: [Int: LinkMark] = [:]
    for i in 0..<t { m[i] = .path }
    m[t] = .active
    f.append(LinkFrame(nodes: row(values, m), status: "A singly linked list has no way back, so it walks \(t) hops from the head to find the predecessor.",
                       readout: "hops = \(t) · writes = 1 · cost = O(n)",
                       detail: "That is O(n) in general, and it is why LRU caches, browser history and text editors are doubly linked.",
                       rewired: Set(0..<t)))
    let deletions = [3, 1, 2, 0]
    var dw = 0, sw = 0, sh = 0
    var alive = Array(values.indices)
    for d in deletions {
        guard let p = alive.firstIndex(of: d) else { continue }
        dw += p == 0 || p == alive.count - 1 ? 1 : 2
        sh += p
        sw += 1
        alive.remove(at: p)
    }
    f.append(LinkFrame(nodes: row(values, all(.ghost)), status: "Deleting all \(values.count) by handle: \(dw) writes and 0 hops doubly linked, \(sw) writes and \(sh) hops singly linked.",
                       readout: "doubly = \(dw) writes · singly = \(sw) writes + \(sh) hops",
                       detail: "The order was \(deletions.map { "\(values[$0])" }.joined(separator: ", ")). The prev pointer buys those hops with memory: {value, next} becomes {value, prev, next}, 50% larger per node."))
    f.append(LinkFrame(nodes: row(values, all(.result)), status: "Pay one pointer per node, and any node you hold is O(1) to delete or walk from.", backward: true,
                       detail: "If you never hold nodes and always start from the head, the second pointer is pure overhead."))
    return f
}

private func skipList() -> [LinkFrame] {
    let keys = [3, 7, 12, 19, 24, 31, 38, 45, 52, 61]
    let levelOf = [1, 2, 1, 3, 1, 2, 1, 4, 1, 2]
    let maxLevel = levelOf.max()!
    var f: [LinkFrame] = []
    let presence = (0..<maxLevel).map { level in keys.indices.map { levelOf[$0] > level } }
    func row(_ marks: [Int: LinkMark] = [:]) -> [LinkNode] { keys.enumerated().map { LinkNode(label: "\($1)", mark: marks[$0] ?? .idle) } }
    f.append(LinkFrame(nodes: row(), status: "A sorted linked list of \(keys.count) keys, plus express lanes. A node on level L appears in every lane below L, so the bottom lane is the full list and each lane above it is a shortcut over the one beneath.", presence: presence,
                       readout: "levels: " + (1...maxLevel).map { l in "L\(l) holds \(levelOf.filter { $0 >= l }.count)" }.joined(separator: ", ")))
    let target = 38
    var comparisons = 0
    var visited: [Int: LinkMark] = [:]
    var position = -1
    var found = false
    func withMark(_ i: Int?) -> [Int: LinkMark] { var v = visited; if let i, i >= 0 { v[i] = .active }; return v }
    for level in stride(from: maxLevel - 1, through: 0, by: -1) {
        while !found {
            guard let next = ((position + 1)..<keys.count).first(where: { levelOf[$0] > level }) else {
                f.append(LinkFrame(nodes: row(withMark(position)), status: "Level \(level + 1): this lane has nothing left after \(position >= 0 ? "\(keys[position])" : "the head"), so there is nothing to compare — drop a level. Reaching the end of a lane costs no comparison at all.",
                                   presence: presence, levelFocus: level, readout: "\(comparisons) comparisons so far"))
                break
            }
            comparisons += 1
            if keys[next] < target {
                position = next
                visited[next] = .path
                f.append(LinkFrame(nodes: row(withMark(next)), status: "Level \(level + 1): \(keys[next]) is still below \(target), so step onto it. Everything before it is now ruled out — a whole prefix skipped for one comparison.",
                                   presence: presence, levelFocus: level, readout: "\(comparisons) comparisons so far"))
            } else if keys[next] == target {
                position = next
                visited[next] = .path
                found = true
            } else {
                f.append(LinkFrame(nodes: row(withMark(next)), status: "Level \(level + 1): the next node on this lane is \(keys[next]), which overshoots \(target). Drop down a level rather than step — the overshoot is information, not a wasted comparison: the answer is between \(position >= 0 ? "\(keys[position])" : "the head") and \(keys[next]).",
                                   presence: presence, levelFocus: level, readout: "\(comparisons) comparisons so far"))
                break
            }
        }
        if found { break }
    }
    let ti = keys.firstIndex(of: target)!
    var fm = visited
    fm[ti] = .result
    f.append(LinkFrame(nodes: row(fm), status: "Found \(target) after \(comparisons) comparisons against a plain linked list's \(ti + 1). At \(keys.count) keys that is barely a win, and saying otherwise would be dishonest — the structure is asymptotic, and ten items have no asymptotics. The frame after next measures what happens when n grows.",
                       presence: presence, readout: "skip list \(comparisons) vs linked list \(ti + 1)"))
    var skipTotal = 0, linearTotal = 0
    for (idx, key) in keys.enumerated() {
        var pos = -1, cmp = 0, hit = false
        for level in stride(from: maxLevel - 1, through: 0, by: -1) {
            while !hit {
                guard let next = ((pos + 1)..<keys.count).first(where: { levelOf[$0] > level }) else { break }
                cmp += 1
                if keys[next] < key { pos = next } else if keys[next] == key { pos = next; hit = true } else { break }
            }
            if hit { break }
        }
        skipTotal += cmp
        linearTotal += idx + 1
    }
    f.append(LinkFrame(nodes: row(), status: "Searching every one of the \(keys.count) keys: \(f1(Double(skipTotal) / Double(keys.count))) comparisons on average against the linked list's \(f1(Double(linearTotal) / Double(keys.count))). The lanes are doing the work a balanced tree would do, with no rotations and no rebalancing code.",
                       presence: presence, readout: "avg \(skipTotal)/\(keys.count) vs \(linearTotal)/\(keys.count) comparisons"))
    let scaling = [16, 128, 1024, 8192].map { n -> (Int, Double, Double) in
        var rng = KotlinRandom(11)
        let levels = (0..<n).map { _ -> Int in var h = 1; while h < 20 && rng.nextBoolean() { h += 1 }; return h }
        let top = levels.max()!
        var total = 0
        for target in 0..<n {
            var pos = -1
            for level in stride(from: top - 1, through: 0, by: -1) {
                var hit = false
                while true {
                    var next = pos + 1
                    while next < n && levels[next] <= level { next += 1 }
                    if next >= n { break }
                    total += 1
                    if next < target { pos = next } else if next == target { hit = true; break } else { break }
                }
                if hit { break }
            }
        }
        return (n, Double(total) / Double(n), Double(n + 1) / 2)
    }
    f.append(LinkFrame(nodes: row(), status: "Built at four sizes with real coin flips, searching every key: " + scaling.map { "n=\($0.0) → \(f1($0.1)) vs \(f1($0.2))" }.joined(separator: "; ") + ". The linked list doubles when n doubles; the skip list adds a constant — that is log growth measured, not assumed. At n=\(scaling.last!.0) it is \(Int((scaling.last!.2 / scaling.last!.1).rounded()))× fewer comparisons.",
                       presence: presence, readout: "avg comparisons: skip list vs sorted linked list"))
    var rng = KotlinRandom(7)
    let trials = 20000
    var heights = [Int](repeating: 0, count: 8)
    var heightSum = 0
    for _ in 0..<trials {
        var h = 1
        while h < 8 && rng.nextBoolean() { h += 1 }
        heights[h - 1] += 1
        heightSum += h
    }
    let expected = (1...8).map { "L\($0) \(String(format: "%.3f", Double(heights[$0 - 1]) / Double(trials)))" }.joined(separator: ", ")
    let mean = String(format: "%.2f", Double(heightSum) / Double(trials))
    f.append(LinkFrame(nodes: keys.map { LinkNode(label: "\($0)", mark: .ghost) }, status: "Real skip lists pick each node's height by flipping a coin until it comes up tails. Over \(trials) draws the heights came out \(expected) — halving each level, exactly the 2^-L the analysis assumes, with mean height \(mean). That is why the expected search is O(log n): the level count is O(log n) with high probability, and each level is expected to take a constant number of steps.",
                       presence: presence, readout: "mean height \(mean) over \(trials) samples"))
    f.append(LinkFrame(nodes: row(), status: "The trade against a balanced tree is honest: a skip list has no worst-case guarantee — a bad run of coin flips gives a bad structure, it just becomes vanishingly unlikely as n grows. What it buys is code you can read, and concurrent insertion that needs no rotation locking, which is why it shows up in databases like LevelDB and Redis's sorted sets.", presence: presence))
    return f
}

private func deque() -> [LinkFrame] {
    var f: [LinkFrame] = []
    func row(_ list: [String], _ marks: [Int: LinkMark] = [:]) -> [LinkNode] { list.enumerated().map { LinkNode(label: $1, mark: marks[$0] ?? .idle) } }
    var contents = ["20", "30", "40"]
    f.append(LinkFrame(nodes: row(contents), status: "A deque exposes four operations instead of a queue's two: push and pop at the front, push and pop at the back. Nothing shifts — only the head index and the size move.", backward: true, readout: "front \(contents.first!) · back \(contents.last!)"))
    contents.insert("10", at: 0)
    f.append(LinkFrame(nodes: row(contents, [0: .active]), status: "pushFront(10): head steps backward one slot — in a circular buffer that is head = (head − 1 + capacity) mod capacity, wrapping to the array's end rather than shifting everything right.", backward: true, readout: "O(1) · nothing else moved"))
    contents.append("50")
    f.append(LinkFrame(nodes: row(contents, [contents.count - 1: .active]), status: "pushBack(50): written at slot (head + size) mod capacity. Both ends are symmetric, which a plain array-backed queue is not — pushing at its front is O(n).", backward: true, readout: "O(1) · size \(contents.count)"))
    let pf = contents.removeFirst()
    f.append(LinkFrame(nodes: row(contents, [0: .path]), status: "popFront() returns \(pf) and advances head. Used this way the deque is a queue.", backward: true, readout: "FIFO view"))
    let pb = contents.removeLast()
    f.append(LinkFrame(nodes: row(contents, [contents.count - 1: .path]), status: "popBack() returns \(pb) and just decrements size. Push and pop at the same end and the deque is a stack — one structure, both disciplines.", backward: true, readout: "LIFO view"))
    let values = [1, 3, -1, -3, 5, 3, 6, 7]
    let k = 3
    var window: [Int] = []
    var answers: [Int] = []
    f.append(LinkFrame(nodes: values.map { LinkNode(label: "\($0)", mark: .ghost) }, status: "The payoff: sliding-window maximum over these 8 values with k = \(k). The deque will hold indices whose values strictly decrease, so its front is always the current window's maximum.", readout: "brute force would rescan k values per window"))
    for i in values.indices {
        if let first = window.first, first <= i - k {
            let expired = window.removeFirst()
            f.append(LinkFrame(nodes: values.enumerated().map { LinkNode(label: "\($1)", mark: $0 == expired ? .path : window.contains($0) ? .active : .ghost) },
                               status: "Index \(expired) has fallen out of the window, so it leaves the front. That is one removal, not a rescan.",
                               readout: "deque: [\(window.map { "\(values[$0])" }.joined(separator: ", "))]"))
        }
        var dropped: [Int] = []
        while let last = window.last, values[last] <= values[i] { dropped.append(window.removeLast()) }
        if !dropped.isEmpty {
            f.append(LinkFrame(nodes: values.enumerated().map { p, v in LinkNode(label: "\(v)", mark: p == i ? .result : dropped.contains(p) ? .path : window.contains(p) ? .active : .ghost) },
                               status: "\(values[i]) arrives and dominates \(dropped.map { "\(values[$0])" }.joined(separator: ", ")) — those can never be the maximum again while \(values[i]) is in the window, so they are popped off the back for good.",
                               readout: "each index enters and leaves at most once"))
        }
        window.append(i)
        if i >= k - 1 {
            answers.append(values[window[0]])
            f.append(LinkFrame(nodes: values.enumerated().map { p, v in LinkNode(label: "\(v)", mark: p == window[0] ? .result : (i - k + 1...i).contains(p) ? .active : .ghost) },
                               status: "Window [\(i - k + 1)..\(i)] — the deque's front is index \(window[0]), so the maximum is \(values[window[0]]). Read, not computed.",
                               readout: "maxima so far: \(answers.map(String.init).joined(separator: ", "))"))
        }
    }
    f.append(LinkFrame(nodes: values.map { LinkNode(label: "\($0)", mark: .result) }, status: "Maxima: \(answers.map(String.init).joined(separator: ", ")). Every index was pushed once and popped once, so the whole sweep is O(n) — against O(n·k) for rescanning each window.",
                       readout: "\(values.count) pushes, \(values.count) pops, \(answers.count) answers"))
    return f
}

private func lruComposite() -> [LinkFrame] {
    let capacity = 3
    var order: [String] = []
    var f: [LinkFrame] = []
    func row(_ marks: [String: LinkMark] = [:]) -> [LinkNode] { order.map { LinkNode(label: $0, mark: marks[$0] ?? .idle) } }
    f.append(LinkFrame(nodes: [LinkNode(label: "head", mark: .ghost), LinkNode(label: "tail", mark: .ghost)],
                       status: "\"get and put in O(1), evict the least recently used\" — a map alone cannot do it, because a map has no order. Pair it with a doubly linked list: the row below is that list, most recent on the left.",
                       backward: true, readout: "capacity \(capacity) · size 0"))
    func put(_ key: String) {
        let evicted = !order.contains(key) && order.count == capacity ? order.removeLast() : nil
        order.removeAll { $0 == key }
        order.insert(key, at: 0)
        f.append(LinkFrame(nodes: row([key: .active]) + (evicted.map { [LinkNode(label: $0, mark: .ghost)] } ?? []),
                           status: "put(\(key)): splice the node in at the head. " + (evicted.map { "The cache was full, so the *tail* — \($0), least recently used by construction — is evicted. The list already knows which one that is; nothing had to be searched." } ?? "The map now stores a reference to this node, not the value, which is what makes every later touch O(1)."),
                           backward: true, readout: "size \(order.count) of \(capacity)" + (evicted.map { " · evicted \($0)" } ?? "")))
    }
    func get(_ key: String) {
        let hit = order.contains(key)
        if hit { order.removeAll { $0 == key }; order.insert(key, at: 0) }
        f.append(LinkFrame(nodes: row(hit ? [key: .result] : [:]),
                           status: hit ? "get(\(key)) hits. The map finds the node in O(1), and because the node carries both pointers it can unlink itself and move to the head in O(1) too — a singly linked list would have to walk to find its predecessor, which is the whole reason the list is doubly linked."
                           : "get(\(key)) misses — \(key) was evicted earlier. The miss costs one map lookup and touches the list not at all.",
                           backward: true, readout: hit ? "\(key) moved to head" : "\(key) not cached"))
    }
    put("A"); put("B"); put("C"); get("A"); put("D"); get("B")
    f.append(LinkFrame(nodes: row(), status: "Every operation touched both structures and both stayed O(1). The recipe generalises: swap the list for a dynamic array and you get insert / delete / getRandom in O(1) (swap-and-pop into the hole); swap it for a parallel stack of running minima and you get a min-stack.",
                       backward: true, readout: "order: \(order.joined(separator: " → ")) (most → least recent)"))
    return f
}

private let linkConfigs: [String: LinkConfig] = [
    "composite_design_pattern": LinkConfig(intro: "An LRU cache as the two-structure recipe: a hash map for O(1) location, a doubly linked list for the recency order the map cannot hold. The list is drawn; the map is what makes reaching into it free.",
                                           legend: [(SimColors.active, "Just written"), (linkResult, "Cache hit, moved to head"), (SimColors.grey, "Evicted / sentinel")], build: lruComposite),
    "deque": LinkConfig(intro: "Four O(1) end operations first, then the reason the structure earns its keep: a monotonic deque answering sliding-window maximum in one pass.",
                        legend: [(SimColors.active, "In deque"), (linkResult, "Window max"), (SimColors.blue, "Popped")], build: deque),
    "doubly_linked_list": LinkConfig(intro: "Two pointers per node, drawn above and below the row. The lab spends its frames on the two operations that separate it from a singly linked list, then counts both over the same script.",
                                     legend: [(SimColors.active, "Node in hand"), (SimColors.blue, "Pointer rewired"), (linkResult, "Settled")], build: doublyLinked),
    "skip_list": LinkConfig(intro: "Express lanes over a sorted linked list. Watch the search drop a level whenever the next node on the lane overshoots — then the last frames measure both the comparison count and the coin flips the structure is built from.",
                            legend: [(SimColors.active, "Overshoot / compare"), (SimColors.blue, "Search path"), (linkResult, "Found")], build: skipList),
]

// Player layout: a stage card (the row + legend), readout chips, narration, then the transport —
// pinned in thumb reach when docked. Nodes use LinkedNodeSpec so every linked list in the app draws
// them at one size; a row that doesn't fit scrolls sideways rather than shrinking them.
struct LinkedStructureLab: View {
    private let config: LinkConfig
    private let frames: [LinkFrame]
    @State private var playback: PlaybackState
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        config = linkConfigs[topicId] ?? linkConfigs["doubly_linked_list"]!
        frames = config.build()
        _playback = State(initialValue: PlaybackState(stepCount: frames.count, speedMs: 750))
    }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: config.intro, bottom: 12)
            LabCard {
                LinkedStage(frame: frame)
                FlowLayout(spacing: 14, lineSpacing: 6) {
                    ForEach(Array(config.legend.enumerated()), id: \.offset) { _, item in LegendItem(color: item.0, label: item.1) }
                }
                .padding(.top, 14)
                if dock == nil {
                    LinkReadout(frame: frame).padding(.top, 16)
                    LinkNarration(frame: frame).padding(.top, 14)
                }
                PlaybackTransport(state: playback, captions: frames.map(\.caption))
            }
            if dock != nil {
                LinkReadout(frame: frame).padding(.top, 14)
                LinkNarration(frame: frame).padding(.horizontal, 4).padding(.top, 16)
            }
        }
    }
}

private struct LinkReadout: View {
    let frame: LinkFrame

    var body: some View {
        if let readout = frame.readout {
            let parts = ReadoutChips.parts(readout)
            // The cost chip is the frame's verdict, so it takes the accent.
            ReadoutChips(parts: parts, accented: Set(parts.indices.filter { parts[$0].key?.hasPrefix("cost") == true }))
        }
    }
}

private struct LinkNarration: View {
    let frame: LinkFrame
    @Environment(\.palette) private var palette

    var body: some View {
        let (head, tail): (String, String?) = frame.detail.map { (frame.status, $0) } ?? LabCaption.split(frame.status)
        VStack(alignment: .leading, spacing: 8) {
            headline(head)
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            if let tail {
                Text(tail).font(AppFont.sans(15)).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The node in hand is named in yellow wherever the headline mentions it.
    private func headline(_ h: String) -> Text {
        let key = palette.dark ? SimColors.active : Color(hex: 0xB45309)
        let inHand = Set(frame.nodes.filter { $0.mark == .active }.map(\.label))
        var out = Text("")
        var word = ""
        func flush() {
            if !word.isEmpty { out = out + (inHand.contains(word) ? Text(word).foregroundColor(key) : Text(word)); word = "" }
        }
        for ch in h {
            if ch.isLetter || ch.isNumber { word.append(ch) } else { flush(); out = out + Text(String(ch)) }
        }
        flush()
        return out
    }
}

private struct LinkedStage: View {
    let frame: LinkFrame
    @Environment(\.palette) private var palette

    private let nullW: CGFloat = 24, padX: CGFloat = 12, laneGap: CGFloat = 30

    private var tags: [Int: String] {
        frame.tags ?? (frame.backward && frame.nodes.count > 1 ? [0: "head", frame.nodes.count - 1: "tail"] : [:])
    }
    private var lanes: Int { max(frame.presence.count - 1, 0) }
    private var contentWidth: CGFloat {
        let n = CGFloat(frame.nodes.count)
        return padX * 2 + (frame.backward ? nullW : 0) + nullW + LinkedNodeSpec.width * n + LinkedNodeSpec.link * max(n - 1, 0)
    }
    private var stageHeight: CGFloat { 18 + laneGap * CGFloat(lanes) + LinkedNodeSpec.height + (tags.isEmpty ? 8 : 24) + 8 }

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { geo in
                ScrollView(.horizontal, showsIndicators: false) {
                    Canvas { ctx, size in draw(ctx, size) }
                        .frame(width: max(geo.size.width, contentWidth), height: stageHeight)
                }
                .scrollDisabled(contentWidth <= geo.size.width)
            }
            .frame(height: stageHeight)
            if frame.backward {
                Text("→ next (top)    ← prev (bottom)")
                    .font(AppFont.sans(14))
                    .foregroundStyle(palette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 14)
            }
        }
        .background(palette.onSurface.opacity(0.03))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func look(_ mark: LinkMark) -> (fill: Color, text: Color, border: Color?) {
        switch mark {
        case .idle: (SimColors.tint, palette.onSurface, nil)
        case .path: (SimColors.blue.opacity(0.28), palette.onSurface, SimColors.blue)
        case .active: (SimColors.active, Color(hex: 0x1A1A1A), nil)
        case .result: (SimColors.answer, .white, nil)
        case .ghost: (SimColors.tint.opacity(0.12), palette.muted.opacity(0.6), SimColors.tint)
        }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let n = frame.nodes.count
        guard n > 0 else { return }
        let w = LinkedNodeSpec.width, h = LinkedNodeSpec.height, link = LinkedNodeSpec.link
        let left = (size.width - contentWidth) / 2 + padX + (frame.backward ? nullW : 0)
        let top = 18 + laneGap * CGFloat(lanes)
        let cy = top + h / 2
        func x(_ i: Int) -> CGFloat { left + CGFloat(i) * (w + link) }
        let muted = palette.muted
        func arrow(_ a: CGPoint, _ b: CGPoint, _ color: Color) {
            ctx.line(a, b, color: color, width: 1.5)
            let dir: CGFloat = b.x >= a.x ? 1 : -1
            ctx.line(b, CGPoint(x: b.x - dir * 4, y: b.y - 4), color: color, width: 1.5)
            ctx.line(b, CGPoint(x: b.x - dir * 4, y: b.y + 4), color: color, width: 1.5)
        }

        // Express lanes, bottom-up above the row: level 1 is just above the nodes.
        for (index, lane) in frame.presence.dropFirst().enumerated() {
            let level = index + 1
            let y = top - laneGap * CGFloat(level) + 6
            let color = frame.levelFocus == level ? SimColors.active : muted.opacity(0.5)
            let present = lane.indices.filter { lane[$0] }
            for (a, b) in zip(present, present.dropFirst()) {
                arrow(CGPoint(x: x(a) + w * 0.75 + 3, y: y), CGPoint(x: x(b) + w * 0.25 - 3, y: y), color)
            }
            for i in present {
                let mark = frame.nodes[i].mark
                ctx.fill(Path(roundedRect: CGRect(x: x(i) + w * 0.25, y: y - 5, width: w * 0.5, height: 10), cornerRadius: 3),
                         with: .color(mark == .idle ? muted.opacity(0.45) : look(mark).fill.opacity(1)))
            }
            ctx.label("L\(level + 1)", at: CGPoint(x: 12, y: y), font: AppFont.mono(10, .bold), color: muted)
        }

        // Pointers: next on top, prev below on a doubly linked row.
        for i in 0..<(n - 1) {
            let color = frame.rewired.contains(i) ? SimColors.blue : muted.opacity(0.6)
            let from = x(i) + w + 4, to = x(i + 1) - 4
            if frame.backward {
                arrow(CGPoint(x: from, y: cy - 7), CGPoint(x: to, y: cy - 7), color)
                arrow(CGPoint(x: to, y: cy + 7), CGPoint(x: from, y: cy + 7), color)
            } else {
                arrow(CGPoint(x: from, y: cy), CGPoint(x: to, y: cy), color)
            }
        }
        if frame.backward { ctx.label("∅", at: CGPoint(x: x(0) - nullW / 2, y: cy), font: AppFont.mono(14), color: muted) }
        ctx.label("∅", at: CGPoint(x: x(n - 1) + w + nullW / 2, y: cy), font: AppFont.mono(14), color: muted)

        for (i, node) in frame.nodes.enumerated() {
            let l = look(node.mark)
            let rect = Path(roundedRect: CGRect(x: x(i), y: top, width: w, height: h), cornerRadius: LinkedNodeSpec.radius)
            ctx.fill(rect, with: .color(l.fill))
            if let border = l.border { ctx.stroke(rect, with: .color(border), lineWidth: 1.5) }
            ctx.label(node.label, at: CGPoint(x: x(i) + w / 2, y: cy), font: AppFont.mono(LinkedNodeSpec.fontSize, .medium), color: l.text)
            if let tag = tags[i] {
                ctx.label(tag, at: CGPoint(x: x(i) + w / 2, y: top + h + 14), font: AppFont.mono(12), color: muted)
            }
        }
    }
}

// MARK: - Doubly linked list story
// Port of DoublyLinkedLab.kt. Insert, delete and reverse as three storyboards over one row of nodes.
// Each gap holds two arrows, next above and prev below; a pointer that skips a node is drawn as an arc
// over (next) or under (prev) the row, labelled with the field being written.

private enum DllTone { case idle, hand, rewired, fresh }
private enum DllStyle { case plain, removed, rewired }
private struct DllNode { let label: String; var tone: DllTone = .idle; var tag: String? = nil }
/// One pointer. `from` and `to` are row positions; `next` draws it above the row, prev below.
private struct DllLink { let from: Int; let to: Int; let next: Bool; var style: DllStyle = .plain; var label: String? = nil }

private struct DllFrame {
    let nodes: [DllNode]
    let links: [DllLink]
    let formula: String
    let chips: [StoryChip]
    let headline: String
    let body: String
}

/// Both pointers between each neighbouring pair, except the ones a step replaces.
private func dllChain(_ count: Int, except: Set<Int> = []) -> [DllLink] {
    (0..<(count - 1)).filter { !except.contains($0) }.flatMap { [DllLink(from: $0, to: $0 + 1, next: true), DllLink(from: $0 + 1, to: $0, next: false)] }
}

private func dllNodes(_ values: [Int], _ tones: [Int: DllTone] = [:], tags: [Int: String]? = nil) -> [DllNode] {
    values.enumerated().map { i, v in
        DllNode(label: "\(v)", tone: tones[i] ?? .idle, tag: tags.map { $0[i] } ?? (i == 0 ? "head" : i == values.count - 1 ? "tail" : nil))
    }
}

private func dllDelete() -> [DllFrame] {
    let v = [10, 20, 30, 40]
    let writes = [StoryChip("pointer writes", "0"), StoryChip("cost", "O(1)")]
    return [
        DllFrame(nodes: dllNodes(v, [2: .hand]), links: dllChain(4), formula: "delete(node 30)", chips: writes,
                 headline: "You hold a pointer to {30}. Deleting it means joining its two neighbours.",
                 body: "The handle could be a cache entry, or whatever an earlier insert returned."),
        DllFrame(nodes: dllNodes(v, [1: .rewired, 2: .hand, 3: .rewired]), links: dllChain(4), formula: "30.prev = {p:20}   30.next = {p:40}", chips: writes,
                 headline: "30.prev and 30.next hand over both neighbours, {p:20} and {p:40}.",
                 body: "No search: the node already knows who is on either side of it."),
        DllFrame(nodes: dllNodes(v, [1: .rewired, 2: .hand, 3: .rewired]),
                 links: dllChain(4, except: [1]) + [DllLink(from: 1, to: 2, next: true, style: .removed), DllLink(from: 2, to: 1, next: false),
                                                    DllLink(from: 1, to: 3, next: true, style: .rewired, label: "20.next")],
                 formula: "20.next = {p:40}", chips: [StoryChip("pointer writes", "1"), StoryChip("cost", "O(1)")],
                 headline: "Write 20.next = {p:40}. Walking forward now skips 30.",
                 body: "The old 20 → 30 link is dropped, but 30 still points at its neighbours."),
        DllFrame(nodes: dllNodes(v, [1: .rewired, 2: .hand, 3: .rewired]),
                 links: dllChain(4, except: [1, 2]) + [DllLink(from: 1, to: 2, next: true, style: .removed), DllLink(from: 2, to: 1, next: false, style: .removed),
                                                       DllLink(from: 2, to: 3, next: true, style: .removed), DllLink(from: 3, to: 2, next: false, style: .removed),
                                                       DllLink(from: 1, to: 3, next: true, style: .rewired, label: "20.next"),
                                                       DllLink(from: 3, to: 1, next: false, style: .rewired, label: "40.prev")],
                 formula: "20.next = {p:40}   40.prev = {p:20}", chips: [StoryChip("pointer writes", "2"), StoryChip("cost", "O(1)")],
                 headline: "Holding 30, both neighbours are one hop away, so it unlinks in 2 writes.",
                 body: "That is what the prev pointer buys: a singly linked list has to walk from the head to find 20."),
        DllFrame(nodes: dllNodes([10, 20, 40], [1: .rewired, 2: .rewired]), links: dllChain(3), formula: "10 ⇄ 20 ⇄ 40",
                 chips: [StoryChip("pointer writes", "2"), StoryChip("cost", "O(1)", .answer)],
                 headline: "30 is unlinked. The list reads {v:10 ⇄ 20 ⇄ 40} both ways.",
                 body: "Nothing shifted and nothing walked, so delete-given-a-node is O(1) however long the list is."),
    ]
}

private func dllInsert() -> [DllFrame] {
    // The new node sits in its slot from the start; the old 20 ⇄ 30 pair arcs around it until rewired.
    let v = [10, 20, 25, 30, 40]
    let tags: [Int: String] = [0: "head", 2: "new", 4: "tail"]
    let outer = [DllLink(from: 1, to: 3, next: true), DllLink(from: 3, to: 1, next: false)]
    let rest = dllChain(5, except: [1, 2])
    func chips(_ n: Int) -> [StoryChip] { [StoryChip("pointer writes", "\(n)"), StoryChip("cost", "O(1)")] }
    return [
        DllFrame(nodes: dllNodes(v, [1: .hand, 2: .fresh], tags: tags), links: rest + outer, formula: "insertAfter(node 20, 25)", chips: chips(0),
                 headline: "Insert {25} after 20, with 20 already in hand.",
                 body: "The new node is allocated on its own. Nothing in the list moves to make room."),
        DllFrame(nodes: dllNodes(v, [1: .hand, 2: .fresh], tags: tags),
                 links: rest + outer + [DllLink(from: 2, to: 1, next: false, style: .rewired), DllLink(from: 2, to: 3, next: true, style: .rewired)],
                 formula: "25.prev = {p:20}   25.next = {p:30}", chips: chips(2),
                 headline: "First the new node points outward: 25.prev = {p:20}, 25.next = {p:30}.",
                 body: "Setting its own fields first means the list is never broken halfway through."),
        DllFrame(nodes: dllNodes(v, [1: .hand, 2: .fresh], tags: tags),
                 links: rest + [DllLink(from: 1, to: 3, next: true, style: .removed), DllLink(from: 3, to: 1, next: false),
                                DllLink(from: 2, to: 1, next: false, style: .rewired), DllLink(from: 2, to: 3, next: true, style: .rewired),
                                DllLink(from: 1, to: 2, next: true, style: .rewired)],
                 formula: "20.next = {p:25}", chips: chips(3),
                 headline: "Then 20.next = {p:25}. Walking forward now reaches the new node.",
                 body: "The old 20 → 30 pointer is replaced, not deleted separately."),
        DllFrame(nodes: dllNodes(v, [1: .hand, 2: .fresh], tags: tags),
                 links: rest + [DllLink(from: 1, to: 3, next: true, style: .removed), DllLink(from: 3, to: 1, next: false, style: .removed),
                                DllLink(from: 2, to: 1, next: false, style: .rewired), DllLink(from: 2, to: 3, next: true, style: .rewired),
                                DllLink(from: 1, to: 2, next: true, style: .rewired), DllLink(from: 3, to: 2, next: false, style: .rewired)],
                 formula: "30.prev = {p:25}", chips: chips(4),
                 headline: "Last, 30.prev = {p:25}: four writes and the node is in.",
                 body: "A singly linked list needs two writes here but cannot insert before a node without walking to it."),
        DllFrame(nodes: dllNodes(v, [2: .rewired], tags: tags), links: dllChain(5), formula: "10 ⇄ 20 ⇄ 25 ⇄ 30 ⇄ 40",
                 chips: [StoryChip("pointer writes", "4"), StoryChip("cost", "O(1)", .answer)],
                 headline: "25 is linked both ways: {v:10 ⇄ 20 ⇄ 25 ⇄ 30 ⇄ 40}.",
                 body: "O(1) once you hold 20. Finding 20 in the first place is still a walk, O(n)."),
    ]
}

private func dllReverse() -> [DllFrame] {
    let v = [10, 20, 30, 40]
    var frames = [DllFrame(nodes: dllNodes(v), links: dllChain(4), formula: "reverse()", chips: [StoryChip("swapped", "0 of 4"), StoryChip("cost", "O(n)")],
                           headline: "Reversing swaps next and prev inside every node.",
                           body: "No node moves in memory. Only the two pointers in each node trade places.")]
    for i in 0..<v.count {
        var tones: [Int: DllTone] = [i: .hand]
        for j in 0..<i { tones[j] = .rewired }
        frames.append(DllFrame(nodes: dllNodes(v, tones), links: dllChain(4), formula: "swap(\(v[i]).next, \(v[i]).prev)",
                               chips: [StoryChip("swapped", "\(i + 1) of \(v.count)"), StoryChip("pointer writes", "\(2 * (i + 1))")],
                               headline: "Swap {\(v[i])}'s pointers: next becomes prev, prev becomes next.",
                               body: i == 0 ? "The old head's prev was null, so its next is now null: it will be the tail."
                                   : i == v.count - 1 ? "The old tail's next was null, so it becomes the new head." : "Each node takes two writes, so the pass is O(n)."))
    }
    let r = Array(v.reversed())
    frames.append(DllFrame(nodes: dllNodes(r, Dictionary(uniqueKeysWithValues: r.indices.map { ($0, DllTone.rewired) })), links: dllChain(4),
                           formula: "head = {v:40}   tail = {v:10}", chips: [StoryChip("pointer writes", "8"), StoryChip("cost", "O(n)", .answer)],
                           headline: "Swap head and tail, and the list reads {v:40 ⇄ 30 ⇄ 20 ⇄ 10}.",
                           body: "Two writes per node plus the two ends. The same loop over a singly linked list has to carry a prev variable along."))
    return frames
}

struct DoublyLinkedLab: View {
    private let tabs: [(String, [DllFrame], [(Color, SwatchStyle, String)])] = [
        ("Insert", dllInsert(), [(SimColors.active, .fill, "Node in hand"), (SimColors.answer, .dashed, "New node"), (SimColors.blue, .fill, "Pointer rewired"), (SimColors.red, .fill, "Link removed")]),
        ("Delete", dllDelete(), [(SimColors.active, .fill, "Node in hand"), (SimColors.blue, .fill, "Pointer rewired"), (SimColors.red, .fill, "Link removed")]),
        ("Reverse", dllReverse(), [(SimColors.active, .fill, "Swapping"), (SimColors.blue, .fill, "Swapped")]),
    ]
    @State private var tab = 1
    @State private var playback = PlaybackState(stepCount: 5, speedMs: 900)

    var body: some View {
        let frames = tabs[tab].1
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                LabSegments(labels: tabs.map(\.0), selected: Binding(get: { tab }, set: { select($0) }))
                DllStage(frame: frame)
                    .frame(height: 170)
                    .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                    .padding(.top, 14)
                StoryFormula(text: frame.formula).padding(.top, 12)
                StoryLegendRow(items: tabs[tab].2).padding(.top, 14)
            }
            StoryChips(chips: frame.chips).padding(.top, 16)
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) })
        }
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        playback = PlaybackState(stepCount: tabs[i].1.count, speedMs: 900)
    }
}

private struct DllStage: View {
    let frame: DllFrame
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let n = frame.nodes.count
            let w: CGFloat = 50, h: CGFloat = 44
            let pad: CGFloat = 12
            let step = (size.width - 2 * pad - w) / CGFloat(max(n - 1, 1))
            let cy = size.height / 2 - 6
            func cx(_ i: Int) -> CGFloat { pad + w / 2 + CGFloat(i) * step }
            func color(_ s: DllStyle) -> Color { s == .removed ? SimColors.red : s == .rewired ? SimColors.blue : palette.muted.opacity(0.7) }
            func arrowHead(_ tip: CGPoint, _ angle: CGFloat, _ c: Color) {
                var p = Path()
                p.move(to: tip)
                p.addLine(to: CGPoint(x: tip.x - 7 * cos(angle - 0.45), y: tip.y - 7 * sin(angle - 0.45)))
                p.addLine(to: CGPoint(x: tip.x - 7 * cos(angle + 0.45), y: tip.y - 7 * sin(angle + 0.45)))
                p.closeSubpath()
                ctx.fill(p, with: .color(c))
            }
            // Links first, so the tiles sit on top of them.
            for link in frame.links {
                let c = color(link.style)
                let stroke = StrokeStyle(lineWidth: link.style == .plain ? 1.5 : 2, dash: link.style == .removed ? [4, 3] : [])
                let dir: CGFloat = link.to > link.from ? 1 : -1
                if abs(link.to - link.from) == 1 {
                    let y = cy + (link.next ? -7 : 7)
                    let a = CGPoint(x: cx(link.from) + dir * (w / 2 + 3), y: y)
                    let b = CGPoint(x: cx(link.to) - dir * (w / 2 + 3), y: y)
                    var p = Path(); p.move(to: a); p.addLine(to: b)
                    ctx.stroke(p, with: .color(c), style: stroke)
                    arrowHead(b, dir > 0 ? 0 : .pi, c)
                } else {
                    // A pointer that skips nodes arcs over (next) or under (prev) the row.
                    let sign: CGFloat = link.next ? -1 : 1
                    let a = CGPoint(x: cx(link.from), y: cy + sign * h / 2)
                    let b = CGPoint(x: cx(link.to), y: cy + sign * h / 2)
                    let control = CGPoint(x: (a.x + b.x) / 2, y: cy + sign * (h / 2 + 52))
                    var p = Path(); p.move(to: a); p.addQuadCurve(to: b, control: control)
                    ctx.stroke(p, with: .color(c), style: stroke)
                    arrowHead(b, atan2(b.y - control.y, b.x - control.x), c)
                    if let label = link.label {
                        let text = ctx.resolve(Text(label).font(AppFont.mono(12, .bold)).foregroundColor(StoryTone.path.ink(palette)))
                        let apex = CGPoint(x: control.x, y: (a.y + 2 * control.y + b.y) / 4 + sign * 10)
                        ctx.draw(text, at: apex)
                    }
                }
            }
            for (i, node) in frame.nodes.enumerated() {
                let rect = CGRect(x: cx(i) - w / 2, y: cy - h / 2, width: w, height: h)
                let tile = Path(roundedRect: rect, cornerRadius: 9)
                let (fill, ink): (Color, Color) = switch node.tone {
                case .idle: (palette.muted.opacity(0.22), palette.onSurface)
                case .hand: (SimColors.active, Color(hex: 0x1F1A0A))
                case .rewired: (SimColors.blue.opacity(0.3), palette.onSurface)
                case .fresh: (SimColors.answer.opacity(0.2), StoryTone.answer.ink(palette))
                }
                ctx.fill(tile, with: .color(palette.surface))
                ctx.fill(tile, with: .color(fill))
                if node.tone == .rewired { ctx.stroke(tile, with: .color(SimColors.blue), lineWidth: 1.5) }
                if node.tone == .fresh { ctx.stroke(tile, with: .color(SimColors.answer), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])) }
                ctx.draw(ctx.resolve(Text(node.label).font(AppFont.mono(17, .bold)).foregroundColor(ink)), at: CGPoint(x: rect.midX, y: rect.midY))
                if let tag = node.tag {
                    let ink = node.tone == .fresh ? StoryTone.answer.ink(palette) : palette.muted
                    ctx.draw(ctx.resolve(Text(tag).font(AppFont.mono(11)).foregroundColor(ink)), at: CGPoint(x: rect.midX, y: rect.maxY + 12))
                }
            }
        }
    }
}
