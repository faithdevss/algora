import SwiftUI

// Port of RecursionTreeSection.kt: runs a real recursive function under a tracer, capturing a
// frame at every call and every return. Nodes light up active → waiting → returned.

private enum RecState { case active, waiting, returned }

private struct RecNode { let id: Int; let parent: Int?; let label: String; let depth: Int }
private struct RecFrame { let state: [Int: RecState]; let stack: [String]; let status: String }
/// `results` holds what each call returned, keyed by node id — the tree shows the short ones on the node.
private struct RecTrace { let nodes: [RecNode]; let frames: [RecFrame]; let results: [Int: String] }

private final class Tracer {
    var nodes: [RecNode] = []
    var frames: [RecFrame] = []
    private var stack: [Int] = []
    private var returned = Set<Int>()
    private var results: [Int: String] = [:]

    func call(_ parent: Int?, _ label: String) -> Int {
        let depth = parent.map { nodes[$0].depth + 1 } ?? 0
        let id = nodes.count
        nodes.append(RecNode(id: id, parent: parent, label: label, depth: depth))
        stack.append(id)
        frame("call \(label)")
        return id
    }

    func ret(_ id: Int, _ result: String) {
        returned.insert(id)
        results[id] = result
        stack.removeLast()
        frame("return \(nodes[id].label) \(result)")
    }

    private func frame(_ status: String) {
        let top = stack.last
        var map: [Int: RecState] = [:]
        for n in nodes { map[n.id] = returned.contains(n.id) ? .returned : n.id == top ? .active : .waiting }
        frames.append(RecFrame(state: map, stack: stack.map { nodes[$0].label }, status: status))
    }

    var trace: RecTrace { RecTrace(nodes: nodes, frames: frames, results: results) }
}

private struct RecursionConfig {
    let range: ClosedRange<Int>
    let defaultN: Int
    let nLabel: String
    let build: (Int) -> RecTrace
}

private func factorialTrace(_ n: Int) -> RecTrace {
    let t = Tracer()
    func fact(_ p: Int?, _ k: Int) -> Int {
        let id = t.call(p, "fact(\(k))")
        let r = k <= 1 ? 1 : k * fact(id, k - 1)
        t.ret(id, "= \(r)")
        return r
    }
    _ = fact(nil, n)
    return t.trace
}

private func fibonacciTrace(_ n: Int) -> RecTrace {
    let t = Tracer()
    func fib(_ p: Int?, _ k: Int) -> Int {
        let id = t.call(p, "fib(\(k))")
        let r = k < 2 ? k : fib(id, k - 1) + fib(id, k - 2)
        t.ret(id, "= \(r)")
        return r
    }
    _ = fib(nil, n)
    return t.trace
}

private func hanoiTrace(_ disks: Int) -> RecTrace {
    let t = Tracer()
    func hanoi(_ p: Int?, _ n: Int, _ from: String, _ to: String, _ via: String) {
        let id = t.call(p, "h(\(n))")
        if n == 1 {
            t.ret(id, "move \(from)→\(to)")
        } else {
            hanoi(id, n - 1, from, via, to)
            hanoi(id, n - 1, via, to, from)
            t.ret(id, "move \(from)→\(to) + done")
        }
    }
    hanoi(nil, disks, "A", "C", "B")
    return t.trace
}

private func nQueensTrace(_ n: Int) -> RecTrace {
    let t = Tracer()
    var cols = [Int](repeating: -1, count: n)
    func safe(_ row: Int, _ col: Int) -> Bool {
        for r in 0..<row where cols[r] == col || abs(cols[r] - col) == row - r { return false }
        return true
    }
    func solve(_ p: Int?, _ row: Int) -> Bool {
        let id = t.call(p, "r\(row)")
        if row == n { t.ret(id, "✓ solved"); return true }
        for col in 0..<n where safe(row, col) {
            cols[row] = col
            if solve(id, row + 1) { t.ret(id, "✓ col \(col)"); cols[row] = -1; return true }
            cols[row] = -1
        }
        t.ret(id, "✗ backtrack")
        return false
    }
    _ = solve(nil, 0)
    return t.trace
}

private func permutationTrace(_ n: Int) -> RecTrace {
    let t = Tracer()
    let letters = Array("abcd".prefix(n))
    var used = [Bool](repeating: false, count: n)
    var current: [Character] = []
    var leaves = 0
    func permute(_ p: Int?) {
        let id = t.call(p, current.isEmpty ? "·" : String(current))
        if current.count == n {
            leaves += 1
            t.ret(id, "✓ \(String(current))")
            return
        }
        for i in 0..<n where !used[i] {
            used[i] = true
            current.append(letters[i])
            permute(id)
            current.removeLast()
            used[i] = false
        }
        t.ret(id, "\(leaves) so far")
    }
    permute(nil)
    return t.trace
}

private func sudokuTrace(_ n: Int) -> RecTrace {
    let t = Tracer()
    var grid = [[Int]](repeating: [Int](repeating: 0, count: n), count: n)
    if n == 4 { grid[0][0] = 1; grid[1][2] = 1; grid[3][3] = 4 }
    let box = n == 4 ? 2 : 1
    func legal(_ r: Int, _ c: Int, _ v: Int) -> Bool {
        for i in 0..<n where grid[r][i] == v || grid[i][c] == v { return false }
        let r0 = r / box * box, c0 = c / box * box
        for i in r0..<(r0 + box) { for j in c0..<(c0 + box) where grid[i][j] == v { return false } }
        return true
    }
    func solve(_ p: Int?, _ pos: Int) -> Bool {
        if pos == n * n {
            let id = t.call(p, "done")
            t.ret(id, "✓ solved")
            return true
        }
        let r = pos / n, c = pos % n
        if grid[r][c] != 0 { return solve(p, pos + 1) }
        let id = t.call(p, "r\(r)c\(c)")
        for v in 1...n where legal(r, c, v) {
            grid[r][c] = v
            if solve(id, pos + 1) { t.ret(id, "✓ = \(v)"); return true }
            grid[r][c] = 0
        }
        t.ret(id, "✗ dead end")
        return false
    }
    _ = solve(nil, 0)
    return t.trace
}

private func karatsubaTrace(_ digits: Int) -> RecTrace {
    let t = Tracer()
    let operands = [2: (47, 82), 3: (471, 823), 4: (1234, 5678)]
    let (x, y) = operands[digits] ?? operands[4]!
    var oneDigit = 0
    func pow10(_ k: Int) -> Int { (0..<k).reduce(1) { r, _ in r * 10 } }
    func kara(_ p: Int?, _ a: Int, _ b: Int, _ width: Int) -> Int {
        let id = t.call(p, "\(a)×\(b)")
        if a < 10 || b < 10 {
            oneDigit += 1
            t.ret(id, "= \(a * b)")
            return a * b
        }
        let half = width / 2
        let pw = pow10(half)
        let a1 = a / pw, a0 = a % pw, b1 = b / pw, b0 = b % pw
        let z2 = kara(id, a1, b1, width - half)
        let z0 = kara(id, a0, b0, half)
        // One product for the middle term where the schoolbook split needs two.
        let z1 = kara(id, a1 + a0, b1 + b0, max(width - half, half) + 1) - z2 - z0
        let r = z2 * pw * pw + z1 * pw + z0
        let schoolbook = width * width
        t.ret(id, p == nil ? "= \(r) · \(oneDigit) one-digit mults vs \(schoolbook) schoolbook" + (oneDigit >= schoolbook ? " — Karatsuba loses at this size" : "") : "= \(r)")
        return r
    }
    _ = kara(nil, x, y, digits)
    return t.trace
}

private func strassenTrace(_ size: Int) -> RecTrace {
    typealias M = [[Int]]
    let t = Tracer()
    let n = size >= 4 ? 4 : 2
    let a: M = (0..<n).map { i in (0..<n).map { j in (i * n + j) % 7 + 1 } }
    let b: M = (0..<n).map { i in (0..<n).map { j in (i + 2 * j) % 5 + 1 } }
    func add(_ x: M, _ y: M, _ s: Int) -> M { x.indices.map { i in x.indices.map { j in x[i][j] + s * y[i][j] } } }
    func naive(_ x: M, _ y: M, _ count: inout Int) -> M {
        let s = x.count
        var c = M(repeating: [Int](repeating: 0, count: s), count: s)
        for i in 0..<s { for j in 0..<s { for k in 0..<s { c[i][j] += x[i][k] * y[k][j]; count += 1 } } }
        return c
    }
    func quad(_ m: M, _ r: Int, _ c: Int) -> M { let h = m.count / 2; return (0..<h).map { i in (0..<h).map { j in m[r + i][c + j] } } }
    var mults = 0, adds = 0
    func strassen(_ p: Int?, _ x: M, _ y: M, _ label: String) -> M {
        let s = x.count
        let id = t.call(p, label)
        if s <= 2 {
            let c = naive(x, y, &mults)
            t.ret(id, "= 2×2 block, 8 scalar mults")
            return c
        }
        let h = s / 2
        let a11 = quad(x, 0, 0), a12 = quad(x, 0, h), a21 = quad(x, h, 0), a22 = quad(x, h, h)
        let b11 = quad(y, 0, 0), b12 = quad(y, 0, h), b21 = quad(y, h, 0), b22 = quad(y, h, h)
        adds += 10
        let m1 = strassen(id, add(a11, a22, 1), add(b11, b22, 1), "M1")
        let m2 = strassen(id, add(a21, a22, 1), b11, "M2")
        let m3 = strassen(id, a11, add(b12, b22, -1), "M3")
        let m4 = strassen(id, a22, add(b21, b11, -1), "M4")
        let m5 = strassen(id, add(a11, a12, 1), b22, "M5")
        let m6 = strassen(id, add(a21, a11, -1), add(b11, b12, 1), "M6")
        let m7 = strassen(id, add(a12, a22, -1), add(b21, b22, 1), "M7")
        adds += 8
        let c11 = add(add(add(m1, m4, 1), m5, -1), m7, 1), c12 = add(m3, m5, 1)
        let c21 = add(m2, m4, 1), c22 = add(add(add(m1, m3, 1), m2, -1), m6, 1)
        var c = M(repeating: [Int](repeating: 0, count: s), count: s)
        for i in 0..<h { for j in 0..<h { c[i][j] = c11[i][j]; c[i][j + h] = c12[i][j]; c[i + h][j] = c21[i][j]; c[i + h][j + h] = c22[i][j] } }
        var naiveCount = 0
        let reference = naive(x, y, &naiveCount)
        t.ret(id, "= \(c == reference ? "matches" : "DIFFERS from") the plain product · \(mults) scalar mults vs \(naiveCount), paid for with \(adds) block additions")
        return c
    }
    _ = strassen(nil, a, b, "\(n)×\(n)")
    return t.trace
}

private func powerTrace(_ e: Int, mod: Int?) -> RecTrace {
    let t = Tracer()
    func power(_ p: Int?, _ e: Int) -> Int {
        let id = t.call(p, mod == nil ? "3^\(e)" : "3^\(e) mod 17")
        var r = 1
        if e > 0 {
            // Computed once and squared — two calls on e/2 would be the O(n) mistake.
            let half = power(id, e / 2)
            var sq = half * half
            if let m = mod { sq %= m }
            r = e % 2 == 0 ? sq : sq * 3
            if let m = mod { r %= m }
        }
        t.ret(id, "= \(r)")
        return r
    }
    _ = power(nil, e)
    return t.trace
}

private func combinationSumTrace(_ count: Int) -> RecTrace {
    let t = Tracer()
    let nums = Array([2, 3, 5, 6, 7].prefix(count))
    var path: [Int] = []
    var solutions = 0
    func walk(_ p: Int?, _ start: Int, _ remaining: Int) {
        let id = t.call(p, path.isEmpty ? "[] need \(remaining)" : "[\(path.map(String.init).joined(separator: ","))] need \(remaining)")
        if remaining == 0 {
            solutions += 1
            t.ret(id, "✓ solution #\(solutions)")
            return
        }
        var explored = 0
        for i in start..<nums.count {
            // Sorted input: every later candidate is worse, so the rest of the loop dies here.
            if nums[i] > remaining { break }
            explored += 1
            path.append(nums[i])
            walk(id, i, remaining - nums[i])
            path.removeLast()
        }
        t.ret(id, explored == 0 ? "dead end — every candidate overshoots" : "tried \(explored) candidate(s)")
    }
    walk(nil, 0, 8)
    return t.trace
}

private func subsetsTrace(_ count: Int) -> RecTrace {
    let t = Tracer()
    let items = Array(["a", "b", "c", "d"].prefix(count))
    var path: [String] = []
    var emitted = 0
    func walk(_ p: Int?, _ start: Int) {
        emitted += 1
        let index = emitted
        let id = t.call(p, path.isEmpty ? "{}" : "{\(path.joined(separator: ","))}")
        for i in start..<items.count {
            path.append(items[i])
            walk(id, i + 1)
            path.removeLast()
        }
        t.ret(id, "subset \(index) of \(1 << items.count)")
    }
    walk(nil, 0)
    return t.trace
}

private func memoFibTrace(_ n: Int) -> RecTrace {
    let t = Tracer()
    var memo: [Int: Int] = [:]
    func fib(_ p: Int?, _ k: Int) -> Int {
        let id = t.call(p, "fib(\(k))")
        if let cached = memo[k] {
            t.ret(id, "= \(cached) (memo hit — the whole subtree below this call is skipped)")
            return cached
        }
        let v = k < 2 ? k : fib(id, k - 1) + fib(id, k - 2)
        memo[k] = v
        t.ret(id, "= \(v) (stored: k=\(k) will never be recomputed)")
        return v
    }
    _ = fib(nil, n)
    return t.trace
}

private func meetInMiddleTrace(_ n: Int) -> RecTrace {
    let t = Tracer()
    let items = Array([3, 34, 4, 12, 5, 2].prefix(n))
    let half = items.count / 2
    let target = 15
    let root = t.call(nil, "target \(target)")
    func enumerate(_ parent: Int, _ label: String, _ part: [Int], _ i: Int, _ sum: Int, _ sums: inout [Int]) {
        if i == part.count {
            let leaf = t.call(parent, "sum \(sum)")
            sums.append(sum)
            t.ret(leaf, "= \(sum)")
            return
        }
        let id = t.call(parent, "\(label)\(part[i])?")
        enumerate(id, label, part, i + 1, sum, &sums)
        enumerate(id, label, part, i + 1, sum + part[i], &sums)
        t.ret(id, "both branches enumerated")
    }
    let left = Array(items.prefix(half)), right = Array(items.dropFirst(half))
    var ls: [Int] = [], rs: [Int] = []
    let lid = t.call(root, "left \(left.map(String.init).joined(separator: ","))")
    enumerate(lid, "L", left, 0, 0, &ls)
    t.ret(lid, "= \(ls.count) sums")
    let rid = t.call(root, "right \(right.map(String.init).joined(separator: ","))")
    enumerate(rid, "R", right, 0, 0, &rs)
    t.ret(rid, "= \(rs.count) sums")
    let hit = ls.contains { l in rs.contains { l + $0 == target } }
    t.ret(root, "\(ls.count) + \(rs.count) = \(ls.count + rs.count) sums enumerated instead of 2^\(items.count) = \(1 << items.count). Sort one side and binary-search it for target − s: " + (hit ? "\(target) is reachable." : "\(target) is not reachable."))
    return t.trace
}

private let recursionConfigs: [String: RecursionConfig] = [
    "memo_recursion_pattern": RecursionConfig(range: 3...7, defaultN: 6, nLabel: "n", build: memoFibTrace),
    "meet_in_middle_pattern": RecursionConfig(range: 4...6, defaultN: 6, nLabel: "items", build: meetInMiddleTrace),
    "fast_power": RecursionConfig(range: 1...20, defaultN: 13, nLabel: "exponent") { powerTrace($0, mod: nil) },
    "modular_exponentiation": RecursionConfig(range: 1...20, defaultN: 13, nLabel: "exponent") { powerTrace($0, mod: 17) },
    "factorial": RecursionConfig(range: 1...8, defaultN: 5, nLabel: "n", build: factorialTrace),
    "karatsubas_algorithm": RecursionConfig(range: 2...4, defaultN: 4, nLabel: "digits per operand", build: karatsubaTrace),
    "strassens_algorithm": RecursionConfig(range: 4...4, defaultN: 4, nLabel: "matrix size", build: strassenTrace),
    "permutation_generation": RecursionConfig(range: 2...4, defaultN: 3, nLabel: "letters", build: permutationTrace),
    "sudoku_solver": RecursionConfig(range: 4...4, defaultN: 4, nLabel: "grid size", build: sudokuTrace),
    "fibonacci_recursive": RecursionConfig(range: 1...6, defaultN: 4, nLabel: "n", build: fibonacciTrace),
    "tower_of_hanoi": RecursionConfig(range: 1...4, defaultN: 3, nLabel: "disks", build: hanoiTrace),
    "n_queens": RecursionConfig(range: 4...6, defaultN: 4, nLabel: "board size", build: nQueensTrace),
    "backtracking_pattern": RecursionConfig(range: 2...5, defaultN: 4, nLabel: "candidate values", build: combinationSumTrace),
    "subsets_pattern": RecursionConfig(range: 2...4, defaultN: 3, nLabel: "elements", build: subsetsTrace),
]

struct RecursionTreeLab: View {
    let topicId: String

    var body: some View {
        // Hanoi and N-Queens have their own designs in docs/ios-design/Simulations iOS.html — pegs above
        // a call tree, and a board with the placement path. Every other recursion is the tree alone.
        switch topicId {
        case "tower_of_hanoi": HanoiLab()
        case "n_queens": NQueensLab()
        default: CallTreeLab(topicId: topicId)
        }
    }
}

private struct CallTreeLab: View {
    private let config: RecursionConfig
    @State private var n: Double
    @State private var trace: RecTrace
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        let c = recursionConfigs[topicId] ?? recursionConfigs["factorial"]!
        config = c
        let t = c.build(c.defaultN)
        _n = State(initialValue: Double(c.defaultN))
        _trace = State(initialValue: t)
        _playback = State(initialValue: PlaybackState(stepCount: t.frames.count))
    }

    var body: some View {
        let frame = trace.frames[min(playback.index, trace.frames.count - 1)]
        LabCard {
            Text("\(config.nLabel) = \(Int(n))").font(AppFont.sans(14, .semibold))
            if config.range.lowerBound < config.range.upperBound {
                Slider(value: $n, in: Double(config.range.lowerBound)...Double(config.range.upperBound), step: 1).tint(palette.primary)
            }
            CallTreeView(trace: trace, frame: frame).padding(.top, 14)
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 14) { legend }
                VStack(alignment: .leading, spacing: 6) { legend }
            }
            .padding(.top, 10)
            LabCaption(text: frame.status).padding(.top, 14)
            Text("Call stack: " + (frame.stack.isEmpty ? "(empty)" : frame.stack.joined(separator: "  ▸  ")))
                .font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted).padding(.top, 4)
            PlaybackTransport(state: playback, captions: trace.frames.map(\.status))
        }
        .onChange(of: Int(n)) { _, value in
            trace = config.build(value)
            playback.load(stepCount: trace.frames.count)
        }
    }

    @ViewBuilder private var legend: some View {
        CallTreeSwatch(color: SimColors.active, label: "Active")
        CallTreeSwatch(color: palette.muted.opacity(0.16), label: "On the stack", border: SimColors.active)
        CallTreeSwatch(color: SimColors.green, label: "Returned")
        CallTreeSwatch(color: palette.muted.opacity(0.16), label: "Not called yet")
    }
}

private struct CallTreeSwatch: View {
    let color: Color
    let label: String
    var border: Color?
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 7) {
            RoundedRectangle(cornerRadius: 3)
                .fill(color)
                .overlay { if let border { RoundedRectangle(cornerRadius: 3).strokeBorder(border, lineWidth: 1.5) } }
                .frame(width: 11, height: 11)
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.muted)
        }
    }
}

// MARK: - Call-tree canvas
// Nodes are pills sized to their label, never discs the label spills out of. Every leaf gets the
// same slot — wide enough for the widest label in the tree — so no two nodes on a row can overlap;
// a tree too wide for the card scrolls sideways and follows the active call instead of shrinking.

/// Dark text on the yellow active pill: white on #F5C542 is unreadable.
private let onActive = Color(hex: 0x1F1A0A)

/// The badge under a returned node: the value itself, not the sentence. Long results (Strassen's
/// tally, meet-in-the-middle's verdict) stay in the caption only.
private func badgeFor(_ result: String) -> String? {
    var core = result
    for cut in [" (", " · "] { if let r = core.range(of: cut) { core = String(core[..<r.lowerBound]) } }
    core = core.trimmingCharacters(in: .whitespaces)
    return core.count <= 12 ? core : nil
}

private struct CallTreeMetrics {
    static let pillH: CGFloat = 22, pillPad: CGFloat = 9, gap: CGFloat = 10, rowGap: CGFloat = 42
    static let edge: CGFloat = 12, badgeRoom: CGFloat = 16, maxLabel: CGFloat = 96
    static let labelUIFont = UIFont.monospacedSystemFont(ofSize: 11, weight: .bold)

    let x: [Int: Double]
    let leafCount: Int
    let maxDepth: Int
    let labels: [String]
    let labelWidths: [CGFloat]
    let slot: CGFloat
    let width: CGFloat

    init(trace: RecTrace, viewport: CGFloat) {
        let nodes = trace.nodes
        // Depth → row; leaves take sequential slots, internal nodes center over their children.
        var children: [Int: [Int]] = [:]
        for n in nodes { if let p = n.parent { children[p, default: []].append(n.id) } }
        var x: [Int: Double] = [:]
        var cursor = 0
        func assign(_ id: Int) {
            let kids = children[id] ?? []
            if kids.isEmpty { x[id] = Double(cursor); cursor += 1 } else {
                kids.forEach(assign)
                x[id] = kids.map { x[$0]! }.reduce(0, +) / Double(kids.count)
            }
        }
        if let root = nodes.first(where: { $0.parent == nil }) { assign(root.id) }
        self.x = x
        leafCount = max(cursor, 1)
        maxDepth = Self.maxDepth(trace)
        labels = nodes.map { n in
            var label = n.label
            for prefix in ["fact", "fib"] where label.hasPrefix(prefix) { label.removeFirst(prefix.count) }
            return Self.fit(label)
        }
        labelWidths = labels.map(Self.measure)
        let minSlot = (labelWidths.max() ?? 0) + 2 * Self.pillPad + Self.gap
        // A narrow tree stretches to fill the card; a wide one keeps its slots and scrolls.
        width = max(viewport, minSlot * CGFloat(leafCount) + 2 * Self.edge)
        slot = (width - 2 * Self.edge) / CGFloat(leafCount)
    }

    static func maxDepth(_ trace: RecTrace) -> Int { trace.nodes.map(\.depth).max() ?? 0 }

    static func height(_ trace: RecTrace, badges: Bool) -> CGFloat {
        edge * 2 + pillH + rowGap * CGFloat(maxDepth(trace)) + (badges ? badgeRoom : 0)
    }

    static func measure(_ s: String) -> CGFloat {
        ceil((s as NSString).size(withAttributes: [.font: labelUIFont]).width)
    }

    static func fit(_ s: String) -> String {
        guard measure(s) > maxLabel else { return s }
        var t = s
        while !t.isEmpty && measure(t + "…") > maxLabel { t.removeLast() }
        return t + "…"
    }

    func cx(_ id: Int) -> CGFloat { Self.edge + CGFloat(x[id]! + 0.5) * slot }
    func cy(_ depth: Int) -> CGFloat { Self.edge + Self.pillH / 2 + CGFloat(depth) * Self.rowGap }
}

/// `framed` = its own tinted box, for the labs that are only a tree; Hanoi sets the tree straight onto
/// its card under a "CALL TREE" heading. `showBadges` prints each returned call's value under its
/// pill; Hanoi's calls return nothing.
private struct CallTreeView: View {
    let trace: RecTrace
    let frame: RecFrame
    var framed = true
    var showBadges = true
    @Environment(\.palette) private var palette

    private var height: CGFloat { CallTreeMetrics.height(trace, badges: showBadges) }

    private var activeId: Int? { frame.state.first { $0.value == .active }?.key }

    var body: some View {
        GeometryReader { geo in
            let m = CallTreeMetrics(trace: trace, viewport: geo.size.width)
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    ZStack(alignment: .topLeading) {
                        // One invisible anchor per leaf slot, so the scroll can center on the
                        // active call — internal nodes sit over a fractional slot, rounded here.
                        HStack(spacing: 0) {
                            ForEach(0..<m.leafCount, id: \.self) { i in
                                Color.clear.frame(width: m.slot, height: 1).id(i)
                            }
                        }
                        .padding(.horizontal, CallTreeMetrics.edge)
                        Canvas { ctx, _ in draw(ctx, m) }
                            .frame(width: m.width, height: height)
                    }
                }
                .onAppear { follow(proxy, m) }
                .onChange(of: activeId) { _, _ in withAnimation(.easeInOut(duration: 0.25)) { follow(proxy, m) } }
            }
        }
        .frame(height: height)
        .background(framed ? palette.muted.opacity(0.08) : .clear, in: RoundedRectangle(cornerRadius: 14))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func follow(_ proxy: ScrollViewProxy, _ m: CallTreeMetrics) {
        guard let id = activeId, let x = m.x[id] else { return }
        proxy.scrollTo(min(Int(x.rounded()), m.leafCount - 1), anchor: .center)
    }

    private func draw(_ ctx: GraphicsContext, _ m: CallTreeMetrics) {
        let nodes = trace.nodes
        let pillH = CallTreeMetrics.pillH
        let idleFill = palette.muted.opacity(0.16)

        // Plain connectors, one colour: the pills carry the state (docs/ios-design mock).
        for n in nodes {
            guard let p = n.parent else { continue }
            var edge = Path()
            edge.move(to: CGPoint(x: m.cx(p), y: m.cy(nodes[p].depth) + pillH / 2))
            edge.addLine(to: CGPoint(x: m.cx(n.id), y: m.cy(n.depth) - pillH / 2))
            ctx.stroke(edge, with: .color(palette.muted.opacity(0.45)), lineWidth: 1)
        }

        let labelFont = Font.system(size: 11, weight: .bold, design: .monospaced)
        let badgeFont = Font.system(size: 10, weight: .medium, design: .monospaced)
        for n in nodes {
            let w = m.labelWidths[n.id] + 2 * CallTreeMetrics.pillPad
            let c = CGPoint(x: m.cx(n.id), y: m.cy(n.depth))
            let rect = CGRect(x: c.x - w / 2, y: c.y - pillH / 2, width: w, height: pillH)
            let pill = Path(roundedRect: rect, cornerRadius: pillH / 2)
            let textColor: Color
            switch frame.state[n.id] {
            case .active:
                ctx.fill(pill, with: .color(SimColors.active))
                textColor = onActive
            case .waiting:
                // Called and waiting on a child: neutral like an idle call, ringed in the active
                // yellow because it is part of the chain that leads to the active one.
                ctx.fill(pill, with: .color(idleFill))
                ctx.stroke(pill, with: .color(SimColors.active), lineWidth: 1.5)
                textColor = palette.onSurface
            case .returned:
                ctx.fill(pill, with: .color(SimColors.green))
                if showBadges, let result = trace.results[n.id], let badge = badgeFor(result) {
                    ctx.draw(
                        Text(badge).font(badgeFont).foregroundColor(SimColors.green),
                        at: CGPoint(x: c.x, y: rect.maxY + 2),
                        anchor: .top
                    )
                }
                textColor = .white
            case nil:
                ctx.fill(pill, with: .color(idleFill))
                textColor = palette.muted.opacity(0.7)
            }
            ctx.draw(Text(m.labels[n.id]).font(labelFont).foregroundColor(textColor), at: c, anchor: .center)
        }
    }
}

// MARK: - Tower of Hanoi
// Built to docs/ios-design/Simulations iOS.html: disk-count picker, pegs with the disk that just moved
// in yellow and a dashed arc from its old peg, the call tree underneath, then step chips and a
// headline + explanation. One step per disk move rather than per call/return — the pegs are what is
// being taught, and h(n) always moves disk n, so the tree and the pegs never disagree.

private let hanoiPegNames = ["A", "B", "C"]
private let hanoiDiskCounts = [3, 4, 5]

private struct HanoiMove { let disk: Int; let from: Int; let to: Int; let caller: Int }

/// `first`/`last` are each call's first and last move index across its whole subtree: a call is
/// "not called yet" before its first move and "returned" after its last.
private struct HanoiPlan {
    let disks: Int
    let trace: RecTrace
    let moves: [HanoiMove]
    private let first: [Int]
    private let last: [Int]

    init(disks: Int) {
        var nodes: [RecNode] = []
        var moves: [HanoiMove] = []
        var first: [Int] = []
        var last: [Int] = []
        func solve(_ parent: Int?, _ n: Int, _ from: Int, _ to: Int, _ via: Int) {
            let id = nodes.count
            let depth = parent.map { nodes[$0].depth + 1 } ?? 0
            nodes.append(RecNode(id: id, parent: parent, label: "h(\(n))", depth: depth))
            first.append(moves.count)
            last.append(-1)
            if n > 1 { solve(id, n - 1, from, via, to) }
            moves.append(HanoiMove(disk: n, from: from, to: to, caller: id))
            if n > 1 { solve(id, n - 1, via, to, from) }
            last[id] = moves.count - 1
        }
        solve(nil, disks, 0, 2, 1)
        self.disks = disks
        self.trace = RecTrace(nodes: nodes, frames: [], results: [:])
        self.moves = moves
        self.first = first
        self.last = last
    }

    func frame(_ m: Int) -> RecFrame {
        let mover = moves[m].caller
        var state: [Int: RecState] = [:]
        for n in trace.nodes {
            if n.id == mover { state[n.id] = .active }
            else if last[n.id] < m { state[n.id] = .returned }
            else if first[n.id] <= m { state[n.id] = .waiting }
        }
        var stack: [String] = []
        var cursor: Int? = mover
        while let id = cursor { stack.insert(trace.nodes[id].label, at: 0); cursor = trace.nodes[id].parent }
        return RecFrame(state: state, stack: stack, status: headline(m))
    }

    func pegsAfter(_ m: Int) -> [[Int]] {
        var pegs: [[Int]] = [Array((1...disks).reversed()), [], []]
        for mv in moves[0...m] { pegs[mv.to].append(pegs[mv.from].removeLast()) }
        return pegs
    }

    func headline(_ m: Int) -> String {
        let mv = moves[m]
        return "h(\(mv.disk)) moves disk \(mv.disk) from \(hanoiPegNames[mv.from]) to \(hanoiPegNames[mv.to])."
    }

    func body(_ m: Int) -> String {
        let mv = moves[m]
        let via = hanoiPegNames[3 - mv.from - mv.to]
        let to = hanoiPegNames[mv.to]
        let k = mv.disk - 1
        let text: String
        switch k {
        case 0: text = "Base case: nothing sits on disk 1, so h(1) moves it straight across and returns."
        case 1: text = "The first h(1) already parked disk 1 on \(via). The second h(1) moves it onto \(to)."
        case 2: text = "The first h(2) already parked disks 1 and 2 on \(via). The second h(2) moves them onto \(to)."
        default: text = "The first h(\(k)) already parked disks 1–\(k) on \(via). The second h(\(k)) moves them onto \(to)."
        }
        guard m == moves.count - 1 else { return text }
        return text + " That completes the puzzle: \(disks) disks in \(moves.count) = 2^\(disks) − 1 moves, the fewest possible."
    }
}

private struct HanoiLab: View {
    @State private var disks = hanoiDiskCounts[0]
    @State private var plan = HanoiPlan(disks: hanoiDiskCounts[0])
    @State private var playback = PlaybackState(stepCount: (1 << hanoiDiskCounts[0]) - 1)
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    /// The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
    private var highlight: Color { scheme == .dark ? SimColors.active : Color(hex: 0xB7791F) }

    var body: some View {
        let m = min(playback.index, plan.moves.count - 1)
        let move = plan.moves[m]
        let frame = plan.frame(m)
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                HStack {
                    SegmentPicker(options: hanoiDiskCounts, selected: disks) { disks = $0 }
                    Spacer()
                    Text("disks · \(plan.moves.count) moves").font(AppFont.sans(14)).foregroundStyle(palette.muted)
                }
                HanoiPegs(pegs: plan.pegsAfter(m), move: move, disks: disks).padding(.top, 16)
                Rectangle().fill(palette.outlineVariant).frame(height: 1).padding(.vertical, 16)
                Text("CALL TREE").font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted)
                CallTreeView(trace: plan.trace, frame: frame, framed: false, showBadges: false)
                FlowLayout(spacing: 16, lineSpacing: 6) {
                    CallTreeSwatch(color: SimColors.active, label: "Moving / active")
                    CallTreeSwatch(color: SimColors.green, label: "Returned")
                    CallTreeSwatch(color: palette.muted.opacity(0.16), label: "Not called yet")
                }
                .padding(.top, 4)
            }
            HStack(spacing: 8) {
                StepChip(label: "move", value: "\(m + 1) / \(plan.moves.count)")
                StepChip(label: "stack", value: frame.stack.joined(separator: " › "))
            }
            .padding(.top, 16)
            headline(move)
                .font(AppFont.sans(20, .bold))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            Text(plan.body(m))
                .font(AppFont.sans(15))
                .foregroundStyle(palette.muted)
                .lineSpacing(3)
                .padding(.top, 8)
            PlaybackTransport(state: playback, captions: plan.moves.indices.map(plan.headline))
        }
        .onChange(of: disks) { _, value in
            plan = HanoiPlan(disks: value)
            playback.load(stepCount: plan.moves.count)
        }
    }

    private func headline(_ move: HanoiMove) -> Text {
        let from = hanoiPegNames[move.from], to = hanoiPegNames[move.to]
        return Text("h(\(move.disk)) moves disk ")
            + Text("\(move.disk)").foregroundColor(highlight)
            + Text(" from \(from) to \(to).")
    }
}

/// The mock's segmented size control (Hanoi's disk count, N-Queens' board size).
private struct SegmentPicker: View {
    let options: [Int]
    let selected: Int
    let onSelect: (Int) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.self) { count in
                let on = count == selected
                Button { onSelect(count) } label: {
                    Text("\(count)")
                        .font(AppFont.sans(14, on ? .bold : .medium))
                        .foregroundStyle(on ? palette.onSurface : palette.muted)
                        .frame(width: 36, height: 28)
                        .background(on ? palette.muted.opacity(0.3) : .clear, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(palette.muted.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

/// `alert` tints the chip red — N-Queens' "safe 0 / n" when a row is a dead end.
private struct StepChip: View {
    let label: String
    let value: String
    var alert = false
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            Text(label).foregroundStyle(palette.muted)
            Text(value).fontWeight(.bold).foregroundStyle(palette.onSurface)
        }
        .font(.system(size: 15, design: .monospaced))
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(alert ? SimColors.red.opacity(0.18) : palette.muted.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct HanoiPegs: View {
    let pegs: [[Int]]
    let move: HanoiMove
    let disks: Int
    @Environment(\.palette) private var palette

    private let diskH: CGFloat = 22, diskGap: CGFloat = 3, arcRoom: CGFloat = 30, overhang: CGFloat = 14

    var body: some View {
        let stackH = (diskH + diskGap) * CGFloat(disks)
        Canvas { ctx, size in draw(ctx, size, stackH) }
            .frame(height: arcRoom + overhang + stackH + 36)
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize, _ stackH: CGFloat) {
        let colW = size.width / 3
        let rodTop = arcRoom
        let baseY = rodTop + overhang + stackH
        let inset: CGFloat = 4, rodW: CGFloat = 6
        let rodColor = palette.muted.opacity(0.3)
        func cx(_ p: Int) -> CGFloat { colW * (CGFloat(p) + 0.5) }

        for peg in 0..<3 {
            let rod = CGRect(x: cx(peg) - rodW / 2, y: rodTop, width: rodW, height: baseY - rodTop)
            ctx.fill(Path(roundedRect: rod, cornerRadius: rodW / 2), with: .color(rodColor))
            let base = CGRect(x: colW * CGFloat(peg) + inset, y: baseY, width: colW - 2 * inset, height: 4)
            ctx.fill(Path(roundedRect: base, cornerRadius: 2), with: .color(rodColor))
            ctx.draw(Text(hanoiPegNames[peg]).font(AppFont.sans(15, .bold)).foregroundColor(palette.onSurface),
                     at: CGPoint(x: cx(peg), y: baseY + 14), anchor: .top)

            // Widths scale from 42% of the peg's column for disk 1 up to the full column for the
            // largest, so every disk count uses the same footprint.
            let maxW = colW - 2 * inset
            let minW = maxW * 0.42
            for (level, disk) in pegs[peg].enumerated() {
                let t = disks == 1 ? 1 : CGFloat(disk - 1) / CGFloat(disks - 1)
                let w = minW + (maxW - minW) * t
                let top = baseY - CGFloat(level + 1) * (diskH + diskGap)
                let rect = CGRect(x: cx(peg) - w / 2, y: top, width: w, height: diskH)
                let moved = disk == move.disk
                ctx.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(moved ? SimColors.active : palette.muted.opacity(0.32)))
                ctx.draw(Text("\(disk)").font(AppFont.sans(13, .bold)).foregroundColor(moved ? onActive : palette.onSurface),
                         at: CGPoint(x: rect.midX, y: rect.midY))
            }
        }

        // The move just made: a dashed arc from beside the old peg's top to the new one's, peaking
        // just under the canvas top.
        let dir: CGFloat = move.to > move.from ? 1 : -1
        let start = CGPoint(x: cx(move.from) + dir * 8, y: rodTop + 20)
        let end = CGPoint(x: cx(move.to) - dir * 12, y: rodTop + 12)
        let control = CGPoint(x: (start.x + end.x) / 2, y: rodTop - 60)
        var arc = Path()
        arc.move(to: start)
        arc.addQuadCurve(to: end, control: control)
        ctx.stroke(arc, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [6, 5]))
        let angle = atan2(end.y - control.y, end.x - control.x)
        var head = Path()
        for spread: CGFloat in [0.5, -0.5] {
            head.move(to: end)
            head.addLine(to: CGPoint(x: end.x - 9 * cos(angle + spread), y: end.y - 9 * sin(angle + spread)))
        }
        ctx.stroke(head, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 2, lineCap: .round))
    }
}

// MARK: - N-Queens
// Built to docs/ios-design/Simulations iOS.html: a board with the row being tried outlined in yellow
// and its attacked squares crossed out, the placements so far as a path of chips, then row / safe /
// backtracks chips and a headline. One step per decision — a queen placed, or a row found dead — and
// a dead end jumps straight to the row the search resumes from, naming every row it unwound past.

private let queensSizes = [4, 5, 6, 8]

private enum QueensTone { case placed, attacked, solved }

private struct QueensFrame {
    /// `queens[r]` is the column of the queen in row r, for every row placed so far.
    let queens: [Int]
    /// The row being tried; nil once every row holds a queen.
    let row: Int?
    /// Columns of `row` that the queens above it attack.
    let attacked: Set<Int>
    let deadEnd: Bool
    let backtracks: Int
    /// Headline in three parts so the middle word can take the tone's colour.
    let lead: String
    let emphasis: String
    let tail: String
    let tone: QueensTone
    let body: String

    var headline: String { lead + emphasis + tail }
}

private func queensFrames(_ n: Int) -> [QueensFrame] {
    var cols: [Int] = []
    var frames: [QueensFrame] = []
    var backtracks = 0
    func attackedIn(_ row: Int, _ placed: [Int]) -> Set<Int> {
        Set((0..<n).filter { c in placed.enumerated().contains { r, qc in qc == c || abs(qc - c) == row - r } })
    }

    var row = 0
    var startCol = 0
    /// The column this row's queen sat in before a backtrack slid it right.
    var resumedFrom: Int?
    while row < n {
        let attacked = attackedIn(row, cols)
        if let col = (startCol..<n).first(where: { !attacked.contains($0) }) {
            cols.append(col)
            let safe = n - attacked.count
            let body: String
            if let old = resumedFrom {
                body = "c\(old) led nowhere, so row \(row) moves its queen to c\(col), the next column nothing attacks."
            } else if attacked.isEmpty {
                body = "Nothing attacks row \(row) yet, so the queen goes in the leftmost square."
            } else {
                body = "\(safe) of \(n) squares in row \(row) are safe. Take the leftmost and move on to row \(row + 1)."
            }
            frames.append(QueensFrame(queens: cols, row: row, attacked: attacked, deadEnd: false, backtracks: backtracks,
                                      lead: "c\(col) is ", emphasis: "safe", tail: " in row \(row). Place a queen.",
                                      tone: .placed, body: body))
            row += 1
            startCol = 0
            resumedFrom = nil
            continue
        }

        backtracks += 1
        // Unwind: lift queens from the bottom up until one can slide right to a column nothing attacks.
        var trial = cols
        var k = row - 1
        var next: Int?
        var lifted = -1
        while k >= 0 {
            lifted = trial.remove(at: k)
            let lower = lifted + 1
            let blocked = attackedIn(k, trial)
            next = lower < n ? (lower..<n).first(where: { !blocked.contains($0) }) : nil
            if next != nil { break }
            k -= 1
        }
        let body: String
        if k < 0 {
            body = "No row above has another safe column, so there is no solution for n = \(n)."
        } else if k + 1 == row {
            body = "The search backs up to row \(k) and tries c\(next!) instead."
        } else if k + 2 == row {
            body = "Row \(k + 1) has no other safe column, so the search unwinds to row \(k) and tries c\(next!)."
        } else {
            body = "Rows \(k + 1)–\(row - 1) have no other safe column, so the search unwinds to row \(k) and tries c\(next!)."
        }
        frames.append(QueensFrame(queens: cols, row: row, attacked: attacked, deadEnd: true, backtracks: backtracks,
                                  lead: "Every square in row \(row) is ", emphasis: "attacked", tail: ". Backtrack.",
                                  tone: .attacked, body: body))
        guard k >= 0, let resume = next else { return frames }
        cols = trial
        row = k
        startCol = resume
        resumedFrom = lifted
    }

    frames.append(QueensFrame(queens: cols, row: nil, attacked: [], deadEnd: false, backtracks: backtracks,
                              lead: "All \(n) queens are ", emphasis: "placed", tail: ".", tone: .solved,
                              body: "No two share a row, column or diagonal. Found after \(backtracks) \(backtracks == 1 ? "backtrack" : "backtracks") — the search stops at the first solution."))
    return frames
}

/// Text colours for the headline word and the dead-end chip: the mock's pale red and blue are for a
/// dark card, and would wash out on white.
private func queensTone(_ tone: QueensTone, dark: Bool) -> Color {
    switch tone {
    case .placed: dark ? Color(hex: 0x93B8FF) : Color(hex: 0x2563EB)
    case .attacked: dark ? Color(hex: 0xF28B82) : SimColors.red
    case .solved: SimColors.green
    }
}

private struct NQueensLab: View {
    @State private var n = queensSizes[0]
    @State private var frames = queensFrames(queensSizes[0])
    @State private var playback = PlaybackState(stepCount: queensFrames(queensSizes[0]).count)
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        let dark = scheme == .dark
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                HStack {
                    SegmentPicker(options: queensSizes, selected: n) { n = $0 }
                    Spacer()
                    Text("board size").font(AppFont.sans(14)).foregroundStyle(palette.muted)
                }
                QueensBoard(n: n, frame: frame).padding(.top, 14)
                Text("PATH").font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).padding(.top, 18)
                FlowLayout(spacing: 6, lineSpacing: 6) {
                    ForEach(Array(frame.queens.enumerated()), id: \.offset) { r, c in
                        if r > 0 { PathSeparator() }
                        PathChip(text: "r\(r) c\(c)", background: SimColors.blue.opacity(0.28), content: palette.onSurface)
                    }
                    if frame.deadEnd, let row = frame.row {
                        if !frame.queens.isEmpty { PathSeparator() }
                        PathChip(text: "r\(row) ×", background: SimColors.red.opacity(0.18), content: queensTone(.attacked, dark: dark))
                    }
                }
                .padding(.top, 8)
                FlowLayout(spacing: 16, lineSpacing: 6) {
                    CallTreeSwatch(color: SimColors.blue, label: "Placed")
                    CallTreeSwatch(color: .clear, label: "Trying row", border: SimColors.active)
                    CallTreeSwatch(color: SimColors.red, label: "Attacked")
                }
                .padding(.top, 14)
            }
            FlowLayout(spacing: 8, lineSpacing: 8) {
                if let row = frame.row {
                    StepChip(label: "row", value: "\(row)")
                    StepChip(label: "safe", value: "\(n - frame.attacked.count) / \(n)", alert: frame.deadEnd)
                } else {
                    StepChip(label: "queens", value: "\(n) / \(n)")
                }
                StepChip(label: "backtracks", value: "\(frame.backtracks)")
            }
            .padding(.top, 16)
            (Text(frame.lead) + Text(frame.emphasis).foregroundColor(queensTone(frame.tone, dark: dark)) + Text(frame.tail))
                .font(AppFont.sans(20, .bold))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            Text(frame.body)
                .font(AppFont.sans(15))
                .foregroundStyle(palette.muted)
                .lineSpacing(3)
                .padding(.top, 8)
            // Dead ends are the steps worth finding again, so they get red ticks on the track.
            PlaybackTransport(state: playback, captions: frames.map(\.headline),
                              marks: TrackMarks(steps: Set(frames.indices.filter { frames[$0].deadEnd }), label: "backtrack", color: SimColors.red))
        }
        .onChange(of: n) { _, value in
            frames = queensFrames(value)
            playback.load(stepCount: frames.count)
        }
    }
}

private struct QueensBoard: View {
    let n: Int
    let frame: QueensFrame
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    private let labelWidth: CGFloat = 28
    /// Largest a square gets; smaller boards stop growing here instead of filling the card.
    private let maxCell: CGFloat = 58

    private var gap: CGFloat { n >= 8 ? 4 : 6 }
    /// Glyph size by board size, since the squares size themselves and never report back.
    private var glyph: CGFloat { n >= 8 ? 17 : n >= 6 ? 21 : 24 }

    var body: some View {
        let dark = scheme == .dark
        VStack(spacing: gap) {
            HStack(spacing: gap) {
                Color.clear.frame(width: labelWidth, height: 1)
                ForEach(0..<n, id: \.self) { c in
                    Text("c\(c)").font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted)
                        .frame(maxWidth: maxCell)
                }
            }
            ForEach(0..<n, id: \.self) { r in
                row(r, dark: dark)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func row(_ r: Int, dark: Bool) -> some View {
        let trying = r == frame.row
        return HStack(spacing: gap) {
            Text("r\(r)")
                .font(.system(size: 13, weight: trying ? .bold : .medium, design: .monospaced))
                .foregroundStyle(trying ? (dark ? SimColors.active : Color(hex: 0xB7791F)) : palette.muted)
                .frame(width: labelWidth)
            ForEach(0..<n, id: \.self) { c in
                square(r, c, trying: trying, dark: dark)
            }
        }
    }

    private func square(_ r: Int, _ c: Int, trying: Bool, dark: Bool) -> some View {
        let queenHere = r < frame.queens.count && frame.queens[r] == c
        let attacked = trying && frame.attacked.contains(c)
        let fill: Color = queenHere ? SimColors.blue.opacity(0.3) : attacked ? SimColors.red.opacity(0.18) : palette.muted.opacity(0.16)
        let border: Color = queenHere ? SimColors.blue : trying ? SimColors.active : .clear
        let shape = RoundedRectangle(cornerRadius: n >= 8 ? 6 : 8)
        return ZStack {
            shape.fill(fill)
            shape.strokeBorder(border, lineWidth: 1.5)
            if queenHere {
                Text("♛").font(.system(size: glyph)).foregroundStyle(dark ? Color.white : Color(hex: 0x1E3A8A))
            } else if attacked {
                Text("×").font(.system(size: glyph * 1.15)).foregroundStyle(queensTone(.attacked, dark: dark))
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .frame(maxWidth: maxCell)
    }
}

private struct PathChip: View {
    let text: String
    let background: Color
    let content: Color

    var body: some View {
        Text(text)
            .font(.system(size: 14, weight: .bold, design: .monospaced))
            .foregroundStyle(content)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(background, in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct PathSeparator: View {
    @Environment(\.palette) private var palette

    var body: some View {
        Text("›").font(AppFont.sans(14)).foregroundStyle(palette.muted).padding(.vertical, 6)
    }
}
