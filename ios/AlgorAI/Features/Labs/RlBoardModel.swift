import Foundation

// Port of the model half of RlBoardLabs.kt: fifty-five RL labs (agent & environment to Dota 2) as one
// card of stacked blocks — a caption, chips, labelled bars, value grids, columns of bars, a scene of
// dots or key/value stats — over a legend, the step's arithmetic and a headline. Colours are the
// design's own strings; RlBoardLabs.swift maps the dark-only neutrals onto the theme when drawing.
// Frames: RlBasicsFrames.swift, RlDeepQFrames.swift, RlModelFrames.swift, RlExploreFrames.swift,
// RlFrontierFrames.swift and RlBenchFrames.swift. Foundation only, so the frames build without SwiftUI.

let rlBoardTopicIds: Set<String> = rlBasicsTopicIds.union(rlDeepQTopicIds).union(rlModelTopicIds)
    .union(rlExploreTopicIds).union(rlFrontierTopicIds).union(rlBenchTopicIds)

func rlBoardLab(_ topicId: String) -> RbLab {
    if let lab = rlBasicsLab(topicId) ?? rlDeepQLab(topicId) ?? rlModelLab(topicId) ?? rlExploreLab(topicId)
        ?? rlFrontierLab(topicId) ?? rlBenchLab(topicId) { return lab }
    fatalError("No RL storyboard for \(topicId)")
}

/// A chip: text, an optional mono line under it, its fill, text colour and ring.
struct RbTok { let t: String; var sub = ""; let bg: String; let color: String; var ring = "none" }

/// A labelled bar over [from, from + width] of the track (fractions); `mid` draws a zero line at the centre.
struct RbRow {
    let w: String
    let v: String
    let from: Double
    let width: Double
    var bar: String
    let wc: String
    let mid: Bool
}

/// One bar of a column, `h` in points of the 62pt column.
struct RbVBar { let h: Double; let bg: String }

struct RbVCol { let v: String; let label: String; let lc: String; let bars: [RbVBar] }

struct RbCell { let t: String; let bg: String; let color: String; var ring = "none" }

struct RbGRow { let label: String; let lc: String; let cells: [RbCell] }

struct RbGrid { let ch: Int; let cols: [String]; let rows: [RbGRow] }

/// A dot at `x`, `y` percent of the scene from the top left, `sz` points across.
struct RbPt { let x: Double; let y: Double; let sz: Double; let bg: String; var ring = "none" }

struct RbStat { let k: String; let v: String; let c: String }

enum RbBlock {
    case label(String)
    case chips([RbTok])
    case bars([RbRow])
    case cols([RbVCol])
    case grid(RbGrid)
    case plot(h: Int, pts: [RbPt])
    case stats([RbStat])
}

/// A formula line: `a` muted, then `b` bold in `c` (the line's result).
struct RbFx { let a: String; let b: String; let c: String }

struct RbFrame {
    let blocks: [RbBlock]
    let fx: [RbFx]
    let capT: String
    let capB: String
    let legend: [(String, String)]
}

/// `frames` builds every step for one picker option; the step count may differ between options.
struct RbLab {
    let tabs: [String]
    let initialTab: Int
    let frames: (_ opt: Int) -> [RbFrame]
}

// MARK: - The design's helpers, shared by the frame files

enum Rb {
    /// JavaScript's `toFixed` for a non-negative number: the exact binary value rounded half up, so
    /// 1.805 (stored as 1.80499…) prints 1.80 and 0.125 prints 0.13, as the design does.
    static func fixed(_ x: Double, _ d: Int) -> String {
        let s = String(format: "%.40f", x)
        let parts = s.split(separator: ".", maxSplits: 1).map(String.init)
        var whole = Array(parts[0])
        let frac = Array(parts.count > 1 ? parts[1] : "")
        var keep = Array(frac.prefix(d))
        let up = frac.count > d && frac[d] >= "5"
        if up {
            // Carry through the kept digits, then the whole part.
            var i = keep.count - 1
            var carry = true
            while carry && i >= 0 {
                if keep[i] == "9" { keep[i] = "0"; i -= 1 } else { keep[i] = Character(String(keep[i].wholeNumberValue! + 1)); carry = false }
            }
            var j = whole.count - 1
            while carry && j >= 0 {
                if whole[j] == "9" { whole[j] = "0"; j -= 1 } else { whole[j] = Character(String(whole[j].wholeNumberValue! + 1)); carry = false }
            }
            if carry { whole.insert("1", at: 0) }
        }
        return d == 0 ? String(whole) : String(whole) + "." + String(keep)
    }

    /// The design's f(): fixed decimals with a real minus sign; a value that rounds to zero prints unsigned.
    static func f(_ x: Double, _ d: Int = 2) -> String {
        let v = abs(x) < 0.5 * pow(10, -Double(d)) ? 0 : x
        return (v < 0 ? "−" : "") + fixed(abs(v), d)
    }

    static func pct(_ p: Double) -> String { p == 0 ? "0%" : fixed(p * 100, p < 0.1 ? 1 : 0) + "%" }

    /// `toLocaleString('en-US')` of a rounded number.
    static func num(_ x: Double) -> String {
        let r = Int64((x + 0.5).rounded(.down))
        var digits = Array(String(abs(r)))
        var out = ""
        while digits.count > 3 { out = "," + String(digits.suffix(3)) + out; digits.removeLast(3) }
        out = String(digits) + out
        return r < 0 ? "−" + out : out
    }

    /// The design's big(): 40.96K, 2.21M.
    static func big(_ n: Double) -> String {
        func g(_ x: Double) -> String {
            var s = fixed(x, x < 10 ? 2 : x < 100 ? 1 : 0)
            if s.contains(".") {
                while s.hasSuffix("0") { s.removeLast() }
                if s.hasSuffix(".") { s.removeLast() }
            }
            return s
        }
        if n >= 1e12 { return g(n / 1e12) + "T" }
        if n >= 1e9 { return g(n / 1e9) + "B" }
        if n >= 1e6 { return g(n / 1e6) + "M" }
        if n >= 1e3 { return g(n / 1e3) + "K" }
        return g(n)
    }

    /// A number as JavaScript prints it: 0.1, 2, 0.25.
    static func js(_ x: Double) -> String {
        if x == x.rounded() && abs(x) < 1e15 { return String(Int64(x)) }
        return "\(x)"
    }

    static func L(_ text: String) -> RbBlock { .label(text) }
    static func C(_ items: [RbTok]) -> RbBlock { .chips(items) }
    static func B(_ rows: [RbRow]) -> RbBlock { .bars(rows) }
    static func S(_ rows: [(String, String, String?)]) -> RbBlock { .stats(rows.map { RbStat(k: $0.0, v: $0.1, c: $0.2 ?? "#f2f3f7") }) }
    static func F(_ a: String, _ b: String = "", _ c: String? = nil) -> RbFx { RbFx(a: a, b: b, c: c ?? "#f2f3f7") }

    static func tok(_ t: String, _ k: String = "plain", _ sub: String = "") -> RbTok {
        let (bg, color): (String, String)
        switch k {
        case "cur", "mask": (bg, color) = ("#f5c542", "#1b1d24")
        case "done": (bg, color) = ("rgba(34,160,107,.18)", "#5fd49b")
        case "fut": (bg, color) = ("#1f232d", "#6b7180")
        case "ans": (bg, color) = ("#6d5dfc", "#fff")
        case "err": (bg, color) = ("rgba(229,72,77,.2)", "#ff8a8d")
        default: (bg, color) = ("#2a2e39", "#f2f3f7")
        }
        return RbTok(t: t, sub: sub, bg: bg, color: color)
    }

    static func row(_ w: String, _ v: String, _ frac: Double, _ bar: String, _ wc: String? = nil) -> RbRow {
        RbRow(w: w, v: v, from: 0, width: max(0, min(frac, 1)), bar: bar, wc: wc ?? "#c3c7d1", mid: false)
    }

    static func brow(_ w: String, _ x: Double, _ sc: Double, _ v: String? = nil, _ wc: String? = nil) -> RbRow {
        let a = min(abs(x) / sc, 1) * 0.5
        return RbRow(w: w, v: v ?? f(x), from: x >= 0 ? 0.5 : 0.5 - a, width: a, bar: x >= 0 ? "#3b82f6" : "#e5337a", wc: wc ?? "#c3c7d1", mid: true)
    }

    static func softmax(_ z: [Double]) -> [Double] {
        let m = z.max()!
        let e = z.map { exp($0 - m) }
        let s = e.reduce(0, +)
        return e.map { $0 / s }
    }

    /// The design's `rng(seed)`: Park–Miller, `x = x·16807 mod (2³¹ − 1)`.
    static func rng(_ seed: Int64) -> () -> Double {
        var x = seed
        return {
            x = (x * 16807) % 2147483647
            return Double(x) / 2147483647.0
        }
    }

    /// A Box–Muller normal drawn from `r`, as the design's `g()`.
    static func gauss(_ r: () -> Double) -> Double {
        var u = r()
        if u == 0 { u = 1e-9 }
        let v = r()
        return sqrt(-2 * log(u)) * cos(2 * Double.pi * v)
    }

    /// `toFixed(2)` as the alpha in an rgba() string.
    static func a2(_ x: Double) -> String { fixed(x, 2) }

    /// JavaScript's `Math.round`: halves round up.
    static func round(_ x: Double) -> Double { (x + 0.5).rounded(.down) }

    /// The index of the first maximum, as `q.indexOf(Math.max(...q))`.
    static func argmax(_ q: [Double]) -> Int {
        var b = 0
        for i in 1..<q.count where q[i] > q[b] { b = i }
        return b
    }

    static func frame(_ blocks: [RbBlock], _ fx: [RbFx], _ cap: (String, String), _ legend: [(String, String)]) -> RbFrame {
        RbFrame(blocks: blocks, fx: fx, capT: cap.0, capB: cap.1, legend: legend)
    }

    static func pt(_ x: Double, _ y: Double, _ sz: Double = 4, _ bg: String = "#3a3f4c", _ ring: String = "none") -> RbPt {
        RbPt(x: x, y: y, sz: sz, bg: bg, ring: ring)
    }

    static func dline(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ n: Int, _ sz: Double, _ bg: String) -> [RbPt] {
        (0...n).map { i in RbPt(x: x0 + (x1 - x0) * Double(i) / Double(n), y: y0 + (y1 - y0) * Double(i) / Double(n), sz: sz, bg: bg) }
    }

    struct Series { let vals: [Double]; let c: String; var sz = 4.0 }

    /// A line chart drawn as dots: a baseline, dashed reference levels, then each series.
    static func chart(_ series: [Series], _ hp: Int, _ lo: Double, _ hi: Double, _ refs: [(Double, String)] = []) -> RbBlock {
        var pts: [RbPt] = []
        for i in 0...40 { pts.append(RbPt(x: 6 + 88 * Double(i) / 40, y: 92, sz: 2, bg: "#3a3f4c")) }
        for (v, c) in refs { for i in stride(from: 0, through: 40, by: 2) { pts.append(RbPt(x: 6 + 88 * Double(i) / 40, y: 92 - 82 * (v - lo) / (hi - lo), sz: 2, bg: c)) } }
        for s in series {
            let n = s.vals.count
            for (i, v) in s.vals.enumerated() {
                pts.append(RbPt(x: 6 + 88 * Double(i) / Double(max(1, n - 1)), y: 92 - 82 * (max(lo, min(hi, v)) - lo) / (hi - lo), sz: s.sz, bg: s.c))
            }
        }
        return .plot(h: hp, pts: pts)
    }
}

/// A sort that keeps equal elements in their original order, as JavaScript's does.
extension Array {
    func stableSorted(by less: (Element, Element) -> Bool) -> [Element] {
        enumerated().sorted { a, b in
            if less(a.element, b.element) { return true }
            if less(b.element, a.element) { return false }
            return a.offset < b.offset
        }.map(\.element)
    }
}

/// A string-keyed map that remembers insertion order, as a JavaScript object does.
struct RbOrdered {
    private(set) var keys: [String] = []
    private var vals: [String: Double] = [:]

    subscript(_ k: String) -> Double? {
        get { vals[k] }
        set {
            if vals[k] == nil, newValue != nil { keys.append(k) }
            vals[k] = newValue
        }
    }

    var entries: [(String, Double)] { keys.map { ($0, vals[$0]!) } }
}

// MARK: - The shared 4×4 grid world: goal (0,3) +1, pit (1,3) −1, wall (1,1), start (3,0)

struct RbPos: Equatable { let r: Int; let c: Int }

enum RbGw {
    static let AC = [(-1, 0), (1, 0), (0, -1), (0, 1)]
    static let AR = ["↑", "↓", "←", "→"]
    static let GOAL = "0,3"
    static let PIT = "1,3"
    static let WALL = "1,1"
    static let START = RbPos(r: 3, c: 0)

    static func key(_ s: RbPos) -> String { "\(s.r),\(s.c)" }

    static func term(_ s: RbPos) -> Bool { let k = key(s); return k == GOAL || k == PIT }

    static func free(_ s: RbPos) -> Bool { (0...3).contains(s.r) && (0...3).contains(s.c) && key(s) != WALL }

    struct Mv { let ns: RbPos; let r: Double }

    static func mv(_ s: RbPos, _ a: Int, _ c: Double) -> Mv {
        let n = RbPos(r: s.r + AC[a].0, c: s.c + AC[a].1)
        let ns = free(n) ? n : s
        let k = key(ns)
        return Mv(ns: ns, r: k == GOAL ? 1 : k == PIT ? -1 : c)
    }

    static let states: [RbPos] = {
        var o: [RbPos] = []
        for r in 0...3 { for c in 0...3 { let s = RbPos(r: r, c: c); if free(s) && !term(s) { o.append(s) } } }
        return o
    }()

    /// A policy: for a state, the (action, probability) pairs.
    typealias Pi = (RbPos) -> [(Int, Double)]

    static func evalPi(_ pi: Pi, _ g: Double, _ c: Double, _ sw: Int) -> [String: Double] {
        var v: [String: Double] = [:]
        for _ in 0..<sw {
            var n: [String: Double] = [:]
            for s in states {
                n[key(s)] = pi(s).reduce(0.0) { a, x in
                    let m = mv(s, x.0, c)
                    return a + x.1 * (m.r + (term(m.ns) ? 0 : g * (v[key(m.ns)] ?? 0)))
                }
            }
            v = n
        }
        return v
    }

    struct Vi { let V: [String: Double]; let P: [String: Int] }

    private static let viCache = RbCache<String, Vi>()

    static func vi(_ g: Double, _ c: Double) -> Vi {
        viCache.get("\(g)_\(c)") {
            var v: [String: Double] = [:]
            for _ in 0..<300 {
                var n: [String: Double] = [:]
                for s in states {
                    n[key(s)] = (0...3).map { a -> Double in
                        let m = mv(s, a, c)
                        return m.r + (term(m.ns) ? 0 : g * (v[key(m.ns)] ?? 0))
                    }.max()!
                }
                v = n
            }
            var p: [String: Int] = [:]
            for s in states {
                var b = 0
                var bv = -1e9
                for a in 0...3 {
                    let m = mv(s, a, c)
                    let x = m.r + (term(m.ns) ? 0 : g * (v[key(m.ns)] ?? 0))
                    if x > bv + 1e-9 { bv = x; b = a }
                }
                p[key(s)] = b
            }
            return Vi(V: v, P: p)
        }
    }

    static func vcol(_ v: Double) -> String {
        let a = min(0.15 + 0.75 * abs(v), 0.9)
        return v >= 0 ? "rgba(59,130,246,\(Rb.js(a)))" : "rgba(229,72,77,\(Rb.js(a)))"
    }

    static func grid(_ ch: Int = 44, _ fn: (RbPos, String) -> RbCell) -> RbBlock {
        .grid(RbGrid(ch: ch, cols: ["c0", "c1", "c2", "c3"], rows: (0...3).map { r in
            RbGRow(label: "r\(r)", lc: "#9aa0ae", cells: (0...3).map { c in
                let k = "\(r),\(c)"
                switch k {
                case WALL: return RbCell(t: "", bg: "#3a3f4c", color: "")
                case GOAL: return RbCell(t: "+1", bg: "#22a06b", color: "#fff")
                case PIT: return RbCell(t: "−1", bg: "#e5484d", color: "#fff")
                default: return fn(RbPos(r: r, c: c), k)
                }
            })
        }))
    }

    static func plainCell(_ t: String = "", _ ring: String = "none") -> RbCell { RbCell(t: t, bg: "#1f232d", color: "#c3c7d1", ring: ring) }

    static func pol(_ name: String) -> Pi {
        switch name {
        case "right, then up": return { s in [(free(RbPos(r: s.r, c: s.c + 1)) ? 3 : 0, 1)] }
        case "up, then right": return { s in [(free(RbPos(r: s.r - 1, c: s.c)) ? 0 : 3, 1)] }
        default: return { _ in [(0, 0.25), (1, 0.25), (2, 0.25), (3, 0.25)] }
        }
    }

    static func perp(_ a: Int) -> [Int] { a < 2 ? [2, 3] : [0, 1] }

    static func evalSlip(_ pi: Pi, _ slip: Double, _ sw: Int = 300) -> [String: Double] {
        var v: [String: Double] = [:]
        for _ in 0..<sw {
            var n: [String: Double] = [:]
            for s in states {
                let a = pi(s)[0].0
                let outs = [(a, 1 - slip), (perp(a)[0], slip / 2), (perp(a)[1], slip / 2)]
                n[key(s)] = outs.reduce(0.0) { acc, x in
                    let m = mv(s, x.0, -0.04)
                    return acc + x.1 * (m.r + (term(m.ns) ? 0 : 0.9 * (v[key(m.ns)] ?? 0)))
                }
            }
            v = n
        }
        return v
    }

    struct Backup { let a: Int; let ns: RbPos; let r: Double; let q: Double }

    static func backups(_ v: [String: Double], _ s: RbPos) -> [Backup] {
        (0...3).map { a in
            let m = mv(s, a, -0.04)
            return Backup(a: a, ns: m.ns, r: m.r, q: m.r + (term(m.ns) ? 0 : 0.9 * (v[key(m.ns)] ?? 0)))
        }
    }

    static func vgrid(_ v: [String: Double], _ p: [String: Int]?, _ hl: String? = nil) -> RbBlock {
        grid { _, k in
            let x = v[k] ?? 0
            let f2 = (x < -0.005 ? "−" : "") + Rb.fixed(abs(x), 2)
            return RbCell(t: f2 + (p != nil ? " " + AR[p![k]!] : ""), bg: abs(x) < 1e-9 ? "#1f232d" : vcol(x), color: "#fff",
                          ring: k == hl ? "inset 0 0 0 2px #f5c542" : "none")
        }
    }

    static func greedy(_ v: [String: Double]) -> [String: Int] {
        var p: [String: Int] = [:]
        for s in states {
            let b = backups(v, s)
            var bi = 0
            for (i, x) in b.enumerated() where x.q > b[bi].q + 1e-9 { bi = i }
            p[key(s)] = bi
        }
        return p
    }

    static func show(_ s: RbPos) -> String { "\(s.r),\(s.c)" }
}

/// A cache shared by the frame files, guarded for frames built off the main thread.
final class RbCache<K: Hashable, V>: @unchecked Sendable {
    private var store: [K: V] = [:]
    private let lock = NSRecursiveLock()

    func get(_ k: K, _ make: () -> V) -> V {
        lock.lock(); defer { lock.unlock() }
        if let v = store[k] { return v }
        let v = make()
        store[k] = v
        return v
    }
}
