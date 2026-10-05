import Foundation

// Port of AnalysisBoard.kt's model and AnalysisBoardLabs.kt: the Analysis topics as interactive boards
// (docs/ios-design/Simulations iOS 11) — blocks, the step's arithmetic, a caption and a dock of
// segmented rows, a slider and run buttons, each a function of the tool's settings. Counts come from
// running the algorithms; the two timing tools sort real arrays on this device. Drawn by AnalysisBoard.swift.

/// A tool's settings: the picked options, the slider index, a seed, the Master-theorem a/b/d, sandbox runs.
struct AnVals {
    var opt = 0
    var opt2 = 0
    var ni = 0
    var seed = 1
    var a = 2
    var b = 2
    var d = 1
    /// Sandbox runs as (algorithm, shape, n).
    var runs: [(Int, Int, Int)] = []
}

/// Measured µs per sort at n = 250 … 4,000, per algorithm ("ins", "bub", "sys").
typealias AnBench = [String: [Double]]

struct AnTok { let t: String; let bg: String; let color: String }
/// A strip cell over [l, l + w] of the track (fractions), at least 3pt wide.
struct AnCell { let l: Double; let w: Double; let bg: String }
struct AnBig { let v: String; let label: String; let c: String }
struct AnRow { let w: String; let v: String; let width: Double; let bar: String; let wc: String; var mk: Double? = nil }
struct AnVBar { let h: Double; let bg: String; var ring = "none" }
struct AnVCol { let label: String; let v: String; let bars: [AnVBar] }
struct AnPath { let pts: [(Double, Double)]; let c: String }
struct AnGrid { let y: Double; let t: String }
struct AnTRow { let a: String; let b: String; let c: String; let d: String; let dc: String }
struct AnDuel { let name: String; let c: String; let bg: String; let time: String; let tSub: String; let space: String; let sSub: String }
struct AnStep { let label: String; let sub: String; let v: String; let canDec: Bool; let canInc: Bool; let dec: (AnVals) -> AnVals; let inc: (AnVals) -> AnVals }
struct AnTier { let label: String; let c: String; let bg: String; let items: [String]; let ops: String; let time: String; let tc: String }

enum AnBlock {
    case label(String)
    case chips([AnTok])
    case strip([AnCell], lo: String, hi: String)
    case big([AnBig])
    case bars([AnRow], labelW: Double = 84, valueW: Double = 64)
    case cols([AnVCol], gap: Double = 6, bw: Double? = 26, axis: String = "")
    /// Curves in a `w` × `h` box, gridlines at y with labels.
    case lines(h: Double, w: Double, paths: [AnPath], grid: [AnGrid])
    case stats([(String, String, String)])
    case table(head: [String], rows: [AnTRow])
    case duel([AnDuel])
    case steps([AnStep])
    case res(bg: String, c: String, top: String, main: String, sub: String)
    case tiers([AnTier])
}

struct AnFx { let a: String; var b = ""; var c = "#f2f3f7" }
struct AnSeg { let labels: [String]; let selected: Int; let pick: (AnVals, Int) -> AnVals }
struct AnSlider { let name: String; let labels: [String] }
struct AnBtn { let label: String; var bench = false; var update: (AnVals) -> AnVals = { $0 } }
struct AnDock { var segs: [AnSeg] = []; var slider: AnSlider? = nil; var btn: AnBtn? = nil; var alt: AnBtn? = nil }

struct AnFrame {
    let title: String
    let blocks: [AnBlock]
    let fx: [AnFx]
    let capT: String
    let capB: String
    let legend: [(String, String, String)]
    let dock: AnDock
}

/// A tool: where it starts, whether it needs the on-device benchmark, and its frame for any settings.
struct AnLab { let initial: AnVals; var bench = false; let build: (AnVals, AnBench?) -> AnFrame }

/// The Analysis topics drawn as boards; Benchmark Dashboard and Complexity Class Comparison keep their tools.
let analysisBoardIds: Set<String> = [
    "operation_counter", "cost_profiler", "parameterized_input_generator", "growth_curve_chart", "growth_class_comparison",
    "best_case", "worst_case", "average_case", "real_vs_predicted_runtime", "input_size_scaling", "amortized_analysis",
    "space_time_tradeoffs", "master_theorem", "complexity_map", "sandbox_mode",
]

func analysisBoardLab(_ topicId: String) -> AnLab? {
    switch topicId {
    case "operation_counter": opCounter()
    case "cost_profiler": costProfiler()
    case "parameterized_input_generator": inputGenerator()
    case "growth_curve_chart": growthCurves()
    case "growth_class_comparison": logLinearExp()
    case "best_case": cases(0)
    case "worst_case": cases(1)
    case "average_case": cases(2)
    case "real_vs_predicted_runtime": realVsPredicted()
    case "input_size_scaling": sizeScaling()
    case "amortized_analysis": amortized()
    case "space_time_tradeoffs": spaceTime()
    case "master_theorem": masterTheorem()
    case "complexity_map": complexityMap()
    case "sandbox_mode": sandbox()
    default: nil
    }
}

// MARK: - The design's palette and helpers

private let B = "#3b82f6", Bl = "#8fb6ff", P = "#6d5dfc", Pl = "#b3abff", G = "#22a06b", Gl = "#5fd09f"
private let Y = "#f5c542", R = "#e5484d", Rl = "#ff8a8e", K = "#e5337a", Kl = "#f47aa8", N = "#3a3f4c"
private let ON = "#f2f3f7", DIM = "#9aa0ae"

/// The design's rng: `s = seed·2654435761 mod (2³¹ − 1)` (7 if that is 0), then Park–Miller, in [0, 1).
func anRng(_ seed: Int64) -> () -> Double {
    var s = (seed &* 2654435761) % 2147483647
    if s == 0 { s = 7 }
    return {
        s = (s * 16807) % 2147483647
        return Double(s - 1) / 2147483646.0
    }
}

private func fxd(_ x: Double, _ d: Int) -> String { Rb.fixed(x, d) }
private func js(_ x: Double) -> String { Rb.js(x) }
private func log2d(_ x: Double) -> Double { log(x) / log(2.0) }

/// `Math.round(x).toLocaleString('en-US')`: grouped, with an ASCII minus as the design prints it.
private func num(_ x: Double) -> String {
    let r = Int64(Rb.round(x))
    var digits = Array(String(abs(r)))
    var out = ""
    while digits.count > 3 { out = "," + String(digits.suffix(3)) + out; digits.removeLast(3) }
    out = String(digits) + out
    return r < 0 ? "-" + out : out
}

/// The design's big(): 12.5K, 1.05M; under 10,000 a rounded, grouped number.
private func big(_ n: Double) -> String {
    func g(_ x: Double) -> String {
        var s = fxd(x, x < 10 ? 2 : x < 100 ? 1 : 0)
        if s.contains(".") {
            while s.hasSuffix("0") { s.removeLast() }
            if s.hasSuffix(".") { s.removeLast() }
        }
        return s
    }
    if n >= 1e12 { return g(n / 1e12) + "T" }
    if n >= 1e9 { return g(n / 1e9) + "B" }
    if n >= 1e6 { return g(n / 1e6) + "M" }
    if n >= 1e4 { return g(n / 1e3) + "K" }
    return num(n)
}

/// toExponential(2): "2.38e-4".
private func expo(_ x: Double) -> String {
    if x == 0 { return "0.00e+0" }
    var e = Int(floor(log10(abs(x))))
    var m = x / pow(10, Double(e))
    if Double(fxd(abs(m), 2))! >= 10 { e += 1; m = x / pow(10, Double(e)) }
    return (m < 0 ? "-" : "") + fxd(abs(m), 2) + "e" + (e < 0 ? "-\(-e)" : "+\(e)")
}

private func perm(_ n: Int, _ r: () -> Double) -> [Int] {
    var a = Array(0..<n)
    var i = n - 1
    while i > 0 {
        let j = Int(floor(r() * Double(i + 1)))
        a.swapAt(i, j)
        i -= 1
    }
    return a
}

/// Sorted, reversed, random or nearly sorted (a few neighbours swapped).
private func shape(_ sh: Int, _ n: Int, _ seed: Int) -> [Int] {
    let r = anRng(Int64(seed) + Int64(n) * 31 + Int64(sh))
    switch sh {
    case 0: return Array(0..<n)
    case 1: return (0..<n).map { n - 1 - $0 }
    case 2: return perm(n, r)
    default:
        var a = Array(0..<n)
        for _ in 0..<max(1, n / 6) {
            let i = Int(floor(r() * Double(n - 1)))
            a.swapAt(i, i + 1)
        }
        return a
    }
}

private func insC(_ x: [Int]) -> Int {
    var a = x
    var c = 0
    for i in 1..<max(a.count, 1) {
        let k = a[i]
        var j = i - 1
        while j >= 0 {
            c += 1
            if a[j] > k { a[j + 1] = a[j]; j -= 1 } else { break }
        }
        a[j + 1] = k
    }
    return c
}

private func bubC(_ x: [Int]) -> Int {
    var a = x
    let n = a.count
    var c = 0
    for i in 0..<max(n - 1, 0) {
        var sw = false
        for j in 0..<(n - 1 - i) {
            c += 1
            if a[j] > a[j + 1] { a.swapAt(j, j + 1); sw = true }
        }
        if !sw { break }
    }
    return c
}

private func selC(_ x: [Int]) -> Int {
    var a = x
    let n = a.count
    var c = 0
    for i in 0..<max(n - 1, 0) {
        var m = i
        for j in (i + 1)..<n { c += 1; if a[j] < a[m] { m = j } }
        a.swapAt(i, m)
    }
    return c
}

private func linC(_ a: [Int], _ t: Int) -> Int {
    var c = 0
    for v in a { c += 1; if v == t { break } }
    return c
}

private func tok(_ t: String, _ k: String = "plain") -> AnTok {
    switch k {
    case "cur": AnTok(t: t, bg: "rgba(245,197,66,.2)", color: Y)
    case "fut": AnTok(t: t, bg: "#1f232d", color: "#6b7180")
    default: AnTok(t: t, bg: "#2a2e39", color: ON)
    }
}

private func row(_ w: String, _ v: String, _ frac: Double, _ bar: String, _ wc: String = "#c3c7d1", _ mk: Double? = nil) -> AnRow {
    AnRow(w: w, v: v, width: max(0, min(frac, 1)), bar: bar, wc: wc, mk: mk)
}

private func L(_ t: String) -> AnBlock { .label(t) }
private func F(_ a: String, _ b: String = "", _ c: String = ON) -> AnFx { AnFx(a: a, b: b, c: c) }
private func bigs(_ items: (String, String, String)...) -> AnBlock { .big(items.map { AnBig(v: $0.0, label: $0.1, c: $0.2) }) }

private struct VIn { let label: String; let v: String; let vals: [Double]; var bg: [String]? = nil; var ring: [String]? = nil }

/// The design's V(): bars scaled to the tallest, at least 2pt.
private func vcols(_ cols: [VIn], _ h: Double, bg: String = G, gap: Double = 6, bw: Double? = 26, axis: String = "") -> AnBlock {
    let mx = max(cols.flatMap(\.vals).max() ?? 0, 1e-9)
    return .cols(cols.map { c in
        AnVCol(label: c.label, v: c.v, bars: c.vals.enumerated().map { i, v in
            AnVBar(h: max(2, v / mx * h), bg: c.bg.flatMap { i < $0.count ? $0[i] : nil } ?? bg, ring: c.ring.flatMap { i < $0.count ? $0[i] : nil } ?? "none")
        })
    }, gap: gap, bw: bw, axis: axis)
}

private func frame(_ title: String, _ blocks: [AnBlock], _ fx: [AnFx], _ cap: (String, String), _ dock: AnDock, _ legend: [(String, String, String)] = []) -> AnFrame {
    AnFrame(title: title, blocks: blocks, fx: fx, capT: cap.0, capB: cap.1, legend: legend, dock: dock)
}

private func lg(_ c: String, _ label: String, _ ring: String = "none") -> (String, String, String) { (c, label, ring) }

// MARK: - 57a Operation Counter

private func opCounter() -> AnLab {
    AnLab(initial: AnVals(opt: 0, ni: 1, seed: 1)) { s, _ in
        let ns = [8, 16, 32, 64, 128, 256, 512, 1024]
        let n = ns[s.ni]
        let bin = s.opt == 1
        let t = Int(floor(anRng(Int64(s.seed) * 7919 + Int64(n))() * Double(n)))
        let lc = t + 1
        var lo = 0, hi = n - 1
        var pr: [Int] = []
        while lo <= hi {
            let m = (lo + hi) >> 1
            pr.append(m)
            if m == t { break }
            if m < t { lo = m + 1 } else { hi = m - 1 }
        }
        let bc = pr.count
        let wb = Int(floor(log2d(Double(n)))) + 1
        let cw = 1.0 / Double(n)
        let dn = Double(n)
        let cells = bin ? pr.enumerated().map { i, p in AnCell(l: Double(p) / dn, w: cw, bg: i == pr.count - 1 ? Y : Pl) }
            : [AnCell(l: 0, w: Double(t) / dn, bg: "rgba(59,130,246,.5)"), AnCell(l: Double(t) / dn, w: cw, bg: Y)]
        return frame("Operation Counter", [
            L("sorted array · target at index \(t)"),
            .strip(cells, lo: "index 0", hi: "index \(n - 1)"),
            bigs(("\(bin ? bc : lc)", "comparisons, this run", bin ? Pl : Bl), ("\(bin ? wb : n)", "worst case at n = \(n)", ON)),
            L("same target, both searches"),
            .bars([row("Linear", "\(lc)", Double(lc) / dn, B, bin ? DIM : ON), row("Binary", "\(bc)", Double(bc) / dn, P, bin ? ON : DIM)]),
        ], [F("linear worst = n =", "\(n)", Bl), F("binary worst = ⌊log₂ n⌋ + 1 =", "\(wb)", Pl)],
            bin ? ("\(bc) probes to find index \(t).", "Each probe halves what's left. From n = 8 to 1,024 the worst case grows by only \(wb - 4) probes.")
                : ("\(lc) comparisons — one per element up to index \(t).", "Linear search checks left to right. Double n and the worst case doubles."),
            AnDock(segs: [AnSeg(labels: ["Linear search", "Binary search"], selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                   slider: AnSlider(name: "Array size", labels: ns.map { "n = \($0)" }),
                   btn: AnBtn(label: "Run on a new target") { v in var v = v; v.seed += 1; return v }),
            bin ? [lg(Pl, "Probed"), lg(Y, "Found")] : [lg("rgba(59,130,246,.5)", "Checked"), lg(Y, "Found")])
    }
}

// MARK: - 57b Cost Profiler

private func costProfiler() -> AnLab {
    AnLab(initial: AnVals(opt: 1, ni: 3)) { s, _ in
        let mxs = [8, 16, 24, 32, 48, 64]
        let mx = mxs[s.ni]
        let alg = s.opt
        let col = [B, Y, K][alg]
        let pts = (0..<8).map { Int(Rb.round(Double(mx * ($0 + 1)) / 8)) }
        func cnt(_ n: Int) -> Double {
            var tot = 0.0
            for k in 0..<5 {
                let r = anRng(101 + Int64(k) * 13 + Int64(n))
                let a = perm(n, r)
                tot += Double(alg == 0 ? linC(a, Int(floor(r() * Double(n)))) : alg == 1 ? insC(a) : bubC(a))
            }
            return tot / 5
        }
        let ops = pts.map(cnt)
        let ratio = ops[7] / ops[3]
        return frame("Cost Profiler", [
            L("operations at 8 sizes up to n = \(mx) · random input, mean of 5 runs"),
            vcols(pts.enumerated().map { i, n in VIn(label: "\(n)", v: big(ops[i]), vals: [ops[i]]) }, 150, bg: col, axis: "n →"),
        ], [F("ops(\(mx)) ÷ ops(\(mx / 2)) =", fxd(ratio, 2) + "×", col == B ? Bl : col), F("model:", ["≈ n / 2", "≈ n² / 4", "≤ n(n − 1) / 2"][alg])],
            alg == 0 ? ("Linear: double n, double the work.", "The bars climb in a straight line — ratio \(fxd(ratio, 2))×, close to 2.")
                : ("Quadratic: double n, four times the work.", alg == 2 ? "Ratio \(fxd(ratio, 2))×. Bubble sort compares nearly every pair, so it sits above insertion." : "Ratio \(fxd(ratio, 2))×. Insertion stops shifting early, averaging about n²/4."),
            AnDock(segs: [AnSeg(labels: ["Linear", "Insertion", "Bubble"], selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                   slider: AnSlider(name: "Largest n", labels: mxs.map { "n = \($0)" })))
    }
}

// MARK: - 57c Input Generator

private let SH4 = ["Sorted", "Reversed", "Random", "Nearly"]

private func inputGenerator() -> AnLab {
    AnLab(initial: AnVals(opt: 2, ni: 4, seed: 3)) { s, _ in
        let ns = [4, 6, 8, 10, 12, 14, 16]
        let n = ns[s.ni]
        let sh = s.opt
        let arr = shape(sh, n, s.seed)
        let cmp = insC(arr)
        let all = (0...3).map { insC(shape($0, n, s.seed)) }
        let w = n * (n - 1) / 2
        let cap = [
            ("Already sorted: one check per element.", "The inner loop breaks on its first comparison every time."),
            ("Reversed: every pair gets compared.", "Each new element shifts all the way left — the true worst case."),
            ("Random: about half the worst case.", "Expected ≈ n²/4 comparisons; this input took \(cmp). Generate another to see it move."),
            ("Nearly sorted: close to the best case.", "Only the few swapped neighbours move — why insertion sort finishes off faster sorts."),
        ][sh]
        return frame("Input Generator", [
            L("\(SH4[sh].lowercased()) input · yellow = smaller than its left neighbour"),
            .chips(arr.enumerated().map { i, v in tok("\(v)", i > 0 && v < arr[i - 1] ? "cur" : "plain") }),
            bigs(("\(cmp)", "insertion-sort comparisons", Pl)),
            L("same n = \(n), all four shapes"),
            .bars(all.enumerated().map { k, v in row(SH4[k], "\(v)", Double(v) / Double(w), k == sh ? P : N, k == sh ? ON : DIM) }),
        ], [F("sorted = n − 1 =", "\(n - 1)", Gl), F("reversed = n(n − 1) / 2 =", "\(w)", Rl)], cap,
            AnDock(segs: [AnSeg(labels: SH4, selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                   slider: AnSlider(name: "Array size", labels: ns.map { "n = \($0)" }),
                   btn: AnBtn(label: "Generate new input") { v in var v = v; v.seed += 1; return v }))
    }
}

// MARK: - Growth curves

private struct Cls { let n: String; let f: (Double) -> Double; let c: String }

private func classes(_ withExp: Bool) -> [Cls] {
    func l2(_ n: Double) -> Double { max(1, log2d(n)) }
    let a = [Cls(n: "O(1)", f: { _ in 1 }, c: B), Cls(n: "O(log n)", f: { l2($0) }, c: G), Cls(n: "O(n)", f: { $0 }, c: Y),
             Cls(n: "O(n log n)", f: { $0 * l2($0) }, c: "#8f84ff"), Cls(n: "O(n²)", f: { $0 * $0 }, c: R)]
    return withExp ? a + [Cls(n: "O(2ⁿ)", f: { pow(2, $0) }, c: K)] : a
}

private func curves(_ cls: [Cls], _ mx: Int, _ log: Bool, _ h: Double) -> AnBlock {
    let w = 333.0, pad = 10.0
    let top = cls.map { $0.f(Double(mx)) }.max()!
    let ymax = log ? log10(top) : top
    func x(_ n: Double) -> Double { pad + (n - 1) / Double(mx - 1) * (w - 2 * pad) }
    func y(_ v: Double) -> Double { h - pad - (log ? log10(max(v, 1)) / ymax : v / ymax) * (h - 2 * pad) }
    let paths = cls.map { c in AnPath(pts: (0...60).map { i in let n = 1 + Double(mx - 1) * Double(i) / 60; return (x(n), y(c.f(n))) }, c: c.c) }
    let ticks = log ? (0...Int(floor(ymax))).map { pow(10, Double($0)) } : [0, 0.25, 0.5, 0.75, 1].map { $0 * ymax }
    return .lines(h: h, w: w, paths: paths, grid: ticks.map { AnGrid(y: y($0), t: big($0)) })
}

// MARK: - 57d Growth Curve Chart

private func growthCurves() -> AnLab {
    AnLab(initial: AnVals(ni: 2)) { s, _ in
        let mxs = [10, 20, 50, 100, 200, 500]
        let mx = mxs[s.ni]
        let cl = classes(false)
        let m = Double(mx)
        let top = log10(m * m)
        let r = m * m / (m * log2d(m))
        return frame("Growth Curve Chart", [
            L("operations vs n · y-axis log-scaled"), curves(cl, mx, true, 180), L("at n = \(mx)"),
            .bars(cl.map { c in row(c.n, "≈ " + big(c.f(m)), max(log10(c.f(m)), 0.02) / top, c.c, ON) }, labelW: 88, valueW: 70),
        ], [F("n² ÷ n log n at n = \(mx) =", fxd(r, 1) + "×", Rl)],
            ("At n = \(mx), O(n²) is \(fxd(r, 1))× O(n log n).", "Slide n up and the gap keeps widening — the curves never cross back."),
            AnDock(slider: AnSlider(name: "Max n", labels: mxs.map { "n = \($0)" })))
    }
}

// MARK: - 57e Log vs Linear vs Exponential

private func logLinearExp() -> AnLab {
    AnLab(initial: AnVals(opt: 0, ni: 3)) { s, _ in
        let ns = [5, 10, 15, 20, 25, 30]
        let n = ns[s.ni]
        let isLog = s.opt == 0
        let cl = classes(true)
        let nd = Double(n)
        let mxv = pow(2, nd)
        let top = log10(mxv)
        return frame("Log vs Linear vs Exponential", [
            L(isLog ? "log scale — each gridline is ×10" : "linear scale — true magnitudes"), curves(cl, n, isLog, 180), L("at n = \(n)"),
            .bars(cl.map { c in row(c.n, big(c.f(nd)), isLog ? max(log10(c.f(nd)), 0.02) / top : c.f(nd) / mxv, c.c, ON) }, labelW: 88, valueW: 64),
        ], [F("2ⁿ ÷ n² at n = \(n) =", big(mxv / (nd * nd)) + "×", Kl)],
            isLog ? ("Log scale: every curve readable.", "Equal steps up are ×10, so shapes separate — but the distances understate the gaps.")
                : ("Linear scale: 2ⁿ reaches \(big(mxv)).", "Everything polynomial is flattened against the floor. This is what “exponential” means."),
            AnDock(segs: [AnSeg(labels: ["Log scale", "Linear scale"], selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                   slider: AnSlider(name: "n", labels: ns.map { "n = \($0)" })))
    }
}

// MARK: - 57f Best / Worst / Average

private func cases(_ start: Int) -> AnLab {
    AnLab(initial: AnVals(opt: start, ni: 1)) { s, _ in
        let ns = [4, 8, 12, 16, 20, 24, 32]
        let n = ns[s.ni]
        let k = s.opt
        let best = n - 1
        let worst = n * (n - 1) / 2
        var tot = 0.0
        for q in 0..<200 { tot += Double(insC(perm(n, anRng(900 + Int64(q))))) }
        let avg = tot / 200
        let sample = perm(n, anRng(900))
        let inp = [Array(0..<n), (0..<n).map { n - 1 - $0 }, sample][k]
        var show = inp.prefix(n > 16 ? 15 : n).enumerated().map { i, v in tok("\(v)", i > 0 && v < inp[i - 1] ? "cur" : "plain") }
        if n > 16 { show.append(tok("…", "fut")) }
        let v = [Double(best), Double(worst), avg][k]
        let col = [Gl, Rl, Y][k]
        return frame(["Best Case", "Worst Case", "Average Case"][k], [
            L(["sorted input", "reversed input", "one of 200 random inputs"][k]),
            .chips(show),
            bigs((k == 2 ? fxd(avg, 1) : js(v), k == 2 ? "mean comparisons, 200 runs" : "comparisons", col), (fxd(v / Double(best), 1) + "×", "vs best case", ON)),
            L("same n = \(n), all three cases"),
            .bars([
                row("Best", "\(best)", Double(best) / Double(worst), G, k == 0 ? ON : DIM),
                row("Average", fxd(avg, 1), avg / Double(worst), Y, k == 2 ? ON : DIM),
                row("Worst", "\(worst)", 1, R, k == 1 ? ON : DIM),
            ]),
        ], [[F("n − 1 =", "\(best)", Gl)], [F("n(n − 1) / 2 =", "\(worst)", Rl)], [F("≈ n² / 4 =", js(Double(n * n) / 4), Y)]][k],
            [("Sorted input: the fastest case.", "The inner loop breaks on its first check every time — linear."),
             ("Reversed input: the true worst case.", "Every element shifts past all the others: \(fxd(Double(worst) / Double(best), 1))× the best case at this n."),
             ("Random input: about half the worst.", "Averaged over 200 inputs. Typical still grows as n² — the constant is just smaller.")][k],
            AnDock(segs: [AnSeg(labels: ["Best", "Average", "Worst"], selected: [0, 2, 1][k]) { v, i in var v = v; v.opt = [0, 2, 1][i]; return v }],
                   slider: AnSlider(name: "Array size", labels: ns.map { "n = \($0)" })))
    }
}

// MARK: - Timing

let anBenchSizes = [250, 500, 1000, 2000, 4000]

/// µs below a millisecond, ms above.
private func fT(_ us: Double) -> String { us >= 1000 ? fxd(us / 1000, us >= 10000 ? 1 : 2) + " ms" : fxd(us, us < 10 ? 2 : 1) + " µs" }

private func benchWaiting(_ title: String, _ dock: AnDock) -> AnFrame {
    frame(title, [L("Timing sorts on this device…")], [F("n =", "250 → 4,000")], ("Benchmark running.", "Each size is timed three times; the median is kept."), dock)
}

/// Times insertion sort, bubble sort and the built-in sort at five sizes; the median of three, in µs per sort.
func runAnBench() -> AnBench {
    func ins(_ a: inout [Double]) {
        if a.count < 2 { return }
        for i in 1..<a.count {
            let k = a[i]
            var j = i - 1
            while j >= 0 && a[j] > k { a[j + 1] = a[j]; j -= 1 }
            a[j + 1] = k
        }
    }
    func bub(_ a: inout [Double]) {
        let n = a.count
        if n < 2 { return }
        for i in 0..<(n - 1) { for j in 0..<(n - 1 - i) where a[j] > a[j + 1] { a.swapAt(j, j + 1) } }
    }
    func sys(_ a: inout [Double]) { a.sort() }
    let algs: [(String, (inout [Double]) -> Void, (Double) -> Double)] = [
        ("ins", ins, { $0 * $0 / 4 }), ("bub", bub, { $0 * $0 / 2 }), ("sys", sys, { $0 * log2d($0) * 4 }),
    ]
    var out: AnBench = [:]
    for (name, fn, cost) in algs {
        for _ in 0..<3 { var w = (0..<600).map { _ in Double.random(in: 0..<1) }; fn(&w) }
        out[name] = anBenchSizes.map { n in
            let base = (0..<n).map { _ in Double.random(in: 0..<1) }
            let m = max(1, Int(Rb.round(1.5e6 / cost(Double(n)))))
            var ts: [Double] = []
            for _ in 0..<3 {
                var cp = Array(repeating: base, count: m)
                let t0 = DispatchTime.now().uptimeNanoseconds
                for q in 0..<m { fn(&cp[q]) }
                ts.append(Double(DispatchTime.now().uptimeNanoseconds - t0) / 1000 / Double(m))
            }
            return ts.sorted()[1]
        }
    }
    return out
}

// MARK: - 57g Real vs Predicted

private func realVsPredicted() -> AnLab {
    AnLab(initial: AnVals(opt: 0), bench: true) { s, bench in
        let dock = AnDock(segs: [AnSeg(labels: ["Insertion sort", "Bubble sort"], selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                          btn: AnBtn(label: "Run benchmark", bench: true))
        guard let t = bench?[["ins", "bub"][s.opt]] else { return benchWaiting("Real vs Predicted Runtime", dock) }
        let ns = anBenchSizes.map(Double.init)
        let c = t.indices.reduce(0.0) { $0 + t[$1] * ns[$1] * ns[$1] } / ns.reduce(0.0) { $0 + pow($1, 4) }
        let p = ns.map { c * $0 * $0 }
        let dv = t.indices.map { (t[$0] - p[$0]) / p[$0] }
        let mdev = dv.dropFirst(2).map(abs).max()!
        return frame("Real vs Predicted Runtime", [
            L("\(s.opt == 1 ? "bubble" : "insertion") sort on random input · median of 3"),
            vcols(anBenchSizes.enumerated().map { i, n in VIn(label: "\(n)", v: "", vals: [t[i], p[i]], bg: [G, "transparent"], ring: ["none", "inset 0 0 0 1.5px #9aa0ae"]) },
                  130, gap: 10, bw: 22, axis: "n →"),
            .table(head: ["n", "measured", "c·n²", "Δ"], rows: anBenchSizes.enumerated().map { i, n in
                AnTRow(a: "\(n)", b: fT(t[i]), c: fT(p[i]), d: (dv[i] >= 0 ? "+" : "−") + fxd(abs(dv[i] * 100), 0) + "%", dc: abs(dv[i]) < 0.25 ? Gl : Y)
            }),
        ], [F("c fit to all 5 points =", expo(c) + " µs")],
            mdev < 0.3 ? ("Measured tracks c·n².", "From n = 1,000 up, every point is within \(Int(Rb.round(mdev * 100)))% of the quadratic fit.")
                : ("Noisy run — tap Run again.", "Timing varies on this device; small n is dominated by timer and cache effects."),
            dock, [lg(G, "Measured"), lg("transparent", "Predicted c·n²", "inset 0 0 0 1.5px #9aa0ae")])
    }
}

// MARK: - 57h Input Size Scaling

private func sizeScaling() -> AnLab {
    AnLab(initial: AnVals(opt: 0), bench: true) { s, bench in
        let dock = AnDock(segs: [AnSeg(labels: ["Insertion · O(n²)", "Built-in · O(n log n)"], selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                          btn: AnBtn(label: "Run benchmark", bench: true))
        guard let t = bench?[["ins", "sys"][s.opt]] else { return benchWaiting("Input Size Scaling", dock) }
        func ex(_ i: Int) -> Double { s.opt == 1 ? 2 * log2d(Double(anBenchSizes[i])) / log2d(Double(anBenchSizes[i - 1])) : 4 }
        let rat = (1..<t.count).map { t[$0] / t[$0 - 1] }
        let sorted = rat.sorted()
        let med = sorted[1] / 2 + sorted[2] / 2
        return frame("Input Size Scaling", [
            L("measured time · \(s.opt == 1 ? "built-in sort" : "insertion sort")"),
            vcols(anBenchSizes.enumerated().map { i, n in VIn(label: "\(n)", v: fT(t[i]), vals: [t[i]]) }, 110, gap: 8, bw: 34),
            L("time ÷ previous size · yellow mark = expected"),
            .bars(rat.enumerated().map { i, r in row("\(anBenchSizes[i])→\(anBenchSizes[i + 1])", fxd(r, 2) + "×", r / 6, P, "#c3c7d1", ex(i + 1) / 6) }, labelW: 88, valueW: 56),
        ], [F("expected per doubling =", s.opt == 1 ? "≈ 2.2×" : "4×", Y), F("median measured =", fxd(med, 2) + "×", Pl)],
            s.opt == 1 ? ("Double n, a little over ×2.", "O(n log n): each doubling costs 2 · log(2n) / log n ≈ 2.2×.")
                : ("Double n, ×4 the time.", "Ratios settle near 4× as n grows — the fingerprint of O(n²)."),
            dock)
    }
}

// MARK: - 57i Amortized Analysis

private func amortized() -> AnLab {
    AnLab(initial: AnVals(opt: 0, ni: 2)) { s, _ in
        let ps = [8, 12, 16, 24, 32, 48, 64]
        let p = ps[s.ni]
        let grow: (Int) -> Int = [{ $0 * 2 }, { Int(ceil(Double($0) * 1.5)) }, { $0 + 4 }][s.opt]
        var cap = 1, size = 0
        var cs: [(Int, Bool)] = []
        for _ in 0..<p {
            var c = 1, rs = false
            if size == cap { c += size; cap = grow(cap); rs = true }
            size += 1
            cs.append((c, rs))
        }
        let tot = cs.reduce(0) { $0 + $1.0 }
        let am = Double(tot) / Double(p)
        let wst = cs.map(\.0).max()!
        let step = p <= 16 ? 1 : p <= 32 ? 4 : 8
        return frame("Amortized Analysis", [
            L("cost of each push · 1 write + copies on resize"),
            vcols(cs.enumerated().map { i, x in
                VIn(label: (i + 1) % step == 0 || i == 0 ? "\(i + 1)" : "", v: x.1 && p <= 24 ? "\(x.0)" : "", vals: [Double(x.0)], bg: [x.1 ? R : G])
            }, 140, gap: p > 32 ? 1 : 2, bw: nil, axis: "push #"),
            bigs(("\(tot)", "total cost", ON), (fxd(am, 2), "amortized / push", Pl), ("\(wst)", "worst single push", Rl)),
        ], [F("total ÷ pushes =", fxd(am, 2), Pl), F("final capacity =", "\(cap)")],
            [("Spikes, but a flat average.", "Doubling makes each resize twice as rare. The average stays under 3 per push — amortized O(1)."),
             ("Smaller steps, more copies.", "×1.5 growth is still geometric, so still amortized O(1) — with a larger constant and less wasted space."),
             ("Fixed +4 growth: the average climbs.", "A resize every 4 pushes, each copying everything. Cost per push grows with n — amortized O(n).")][s.opt],
            AnDock(segs: [AnSeg(labels: ["Grow ×2", "Grow ×1.5", "Grow +4"], selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                   slider: AnSlider(name: "Pushes", labels: ps.map { "\($0)" })),
            [lg(G, "Plain push"), lg(R, "Push + resize copy")])
    }
}

// MARK: - 57j Space-Time Tradeoffs

private func spaceTime() -> AnLab {
    AnLab(initial: AnVals(opt: 0, ni: 2)) { s, _ in
        let ns = [8, 16, 24, 32, 48, 64]
        let n = ns[s.ni]
        let end = s.opt == 0
        let pp = end ? (n - 2, n - 1) : (0, 1)
        var bc = 0
        outer: for i in 0..<n { for j in (i + 1)..<max(n, i + 1) { bc += 1; if i == pp.0 && j == pp.1 { break outer } } }
        var hl = 0, hs = 0
        for i in 0..<n { hl += 1; if i == pp.1 { break }; hs += 1 }
        let mx = Double(max(bc, hl))
        return frame("Space-Time Tradeoffs", [
            .duel([
                AnDuel(name: "Brute force", c: Rl, bg: "rgba(229,72,77,.12)", time: num(Double(bc)) + " pair checks", tSub: "O(n²) time", space: "0 extra cells", sSub: "O(1) space"),
                AnDuel(name: "Hash set", c: Gl, bg: "rgba(34,160,107,.12)", time: "\(hl) lookups", tSub: "O(n) time", space: "\(hs) cells", sSub: "O(n) space"),
            ]),
            L("operations"), .bars([row("Brute", num(Double(bc)), Double(bc) / mx, R), row("Hash", "\(hl)", Double(hl) / mx, G)]),
            L("extra memory, cells"), .bars([row("Brute", "0", 0, R), row("Hash", "\(hs)", Double(hs) / mx, G)]),
        ], [F("operations saved =", num(Double(bc - hl)), Gl), F("memory paid =", "\(hs) cells", Y)],
            end ? ("Hash set: \(num(Double(bc - hl))) fewer operations for \(hs) cells.", "Brute force checks every pair; the hash set remembers what it has seen. Memory buys time.")
                : ("Pair at the start: both finish instantly.", "Big-O describes the worst case — here brute force simply got lucky."),
            AnDock(segs: [AnSeg(labels: ["Pair at the end", "Pair at the start"], selected: s.opt) { v, i in var v = v; v.opt = i; return v }],
                   slider: AnSlider(name: "Array size", labels: ns.map { "n = \($0)" })))
    }
}

// MARK: - 57k Master Theorem

private func sup(_ x: String) -> String {
    let digits = Array("⁰¹²³⁴⁵⁶⁷⁸⁹")
    return String(x.map { ch in ch.wholeNumberValue.map { digits[$0] } ?? (ch == "." ? "·" : ch) })
}

private func np(_ e: Double) -> String {
    if e == 0 { return "1" }
    if e == 1 { return "n" }
    return "n" + sup(js(e == floor(e) ? e : Double(fxd(e, 2))!))
}

private func masterTheorem() -> AnLab {
    AnLab(initial: AnVals(a: 2, b: 2, d: 1)) { s, _ in
        let a = s.a, b = s.b, d = s.d
        let lgv = log(Double(a)) / log(Double(b))
        let eq = abs(lgv - Double(d)) < 1e-9
        let res: String
        if eq { res = d == 0 ? "Θ(log n)" : "Θ(\(np(Double(d))) log n)" }
        else if lgv > Double(d) {
            let r9 = Double(fxd(lgv, 9))!
            res = "Θ(\(np(r9 == floor(r9) ? Rb.round(lgv) : lgv)))"
        } else { res = "Θ(\(np(Double(d))))" }
        let cs = eq ? 2 : lgv > Double(d) ? 1 : 3
        let q = Double(a) / pow(Double(b), Double(d))
        let lv = (0...4).map { pow(q, Double($0)) }
        let lm = lv.max()!
        let ex = [(2, 2, 1), (1, 2, 0), (3, 2, 1), (7, 2, 2)]
        let sel = ex.firstIndex { $0.0 == a && $0.1 == b && $0.2 == d } ?? -1
        func st(_ l: String, _ sub: String, _ kp: WritableKeyPath<AnVals, Int>, _ lo: Int, _ hi: Int) -> AnStep {
            AnStep(label: l, sub: sub, v: "\(s[keyPath: kp])", canDec: s[keyPath: kp] > lo, canInc: s[keyPath: kp] < hi,
                   dec: { v in var v = v; v[keyPath: kp] = max(lo, v[keyPath: kp] - 1); return v },
                   inc: { v in var v = v; v[keyPath: kp] = min(hi, v[keyPath: kp] + 1); return v })
        }
        return frame("Master Theorem", [
            .steps([st("a", "subproblems", \.a, 1, 9), st("b", "shrink factor", \.b, 2, 6), st("d", "exponent of f(n)", \.d, 0, 3)]),
            .res(bg: "rgba(109,93,252,.14)", c: Pl, top: "log_\(b)(\(a)) = \(fxd(lgv, 3))  vs  d = \(d)", main: res, sub: [
                "Case 1 · leaves dominate — work grows down the tree.", "Case 2 · every level does equal work.", "Case 3 · the root dominates — work shrinks down the tree.",
            ][cs - 1]),
            L("work per recursion level, relative to the root (×\(fxd(q, 2)) per level)"),
            .bars(lv.enumerated().map { i, w in row("level \(i)", fxd(w, 2) + "×", w / lm, [R, B, Y][cs - 1]) }, labelW: 64, valueW: 60),
        ], [F("T(n) = \(a)·T(n/\(b)) + Θ(\(np(Double(d))))", "", Pl)],
            ("Compare d with log_b a.", "Level i does (a / bᵈ)ⁱ times the root’s work. Above 1 the leaves win; below 1 the root wins."),
            AnDock(segs: [AnSeg(labels: ["Merge", "Binary", "Karatsuba", "Strassen"], selected: sel) { v, i in var v = v; (v.a, v.b, v.d) = ex[i]; return v }]))
    }
}

// MARK: - 57l Complexity Map

private func complexityMap() -> AnLab {
    AnLab(initial: AnVals(ni: 2)) { s, _ in
        let ns = [10.0, 100, 1e3, 1e4, 1e5, 1e6]
        let n = ns[s.ni]
        let tiers: [(String, Double, String, String, [String])] = [
            ("O(1)", 0, Gl, "rgba(34,160,107,.16)", ["Array access", "Hash lookup", "Stack push"]),
            ("O(log n)", log10(max(1, log2d(n))), Gl, "rgba(34,160,107,.16)", ["Binary search", "BST insert", "Heap push"]),
            ("O(n)", log10(n), Bl, "rgba(59,130,246,.16)", ["Linear search", "Array scan", "BFS / DFS"]),
            ("O(n log n)", log10(n * log2d(n)), Y, "rgba(245,197,66,.14)", ["Merge sort", "Heap sort", "Quicksort (avg)"]),
            ("O(n²)", 2 * log10(n), Kl, "rgba(229,51,122,.14)", ["Bubble sort", "Insertion sort", "Selection sort"]),
            ("O(2ⁿ)", n * log10(2), Rl, "rgba(229,72,77,.16)", ["Naive Fibonacci", "Subset generation", "TSP brute force"]),
        ]
        func tm(_ lgv: Double) -> String {
            let sec = lgv - 9
            if sec > 17.64 { return "> age of universe" }
            let v = pow(10, sec)
            if v < 1e-6 { return "\(Int64(Rb.round(v * 1e9))) ns" }
            if v < 1e-3 { return fxd(v * 1e6, 0) + " µs" }
            if v < 1 { return fxd(v * 1e3, 0) + " ms" }
            if v < 60 { return fxd(v, 1) + " s" }
            if v < 3600 { return fxd(v / 60, 0) + " min" }
            if v < 86400 { return fxd(v / 3600, 0) + " h" }
            if v < 3.15e7 { return fxd(v / 86400, 0) + " days" }
            return big(v / 3.15e7) + " yrs"
        }
        func tc(_ lgv: Double) -> String { lgv - 9 < 0 ? Gl : lgv - 9 < 3.56 ? Y : Rl }
        func ops(_ lgv: Double) -> String { lgv < 15 ? big(pow(10, lgv)) : "10^\(Int64(Rb.round(lgv)))" }
        return frame("Complexity Map", [
            L("operations and time at n = \(big(n)), 1 ns per operation"),
            .tiers(tiers.map { t in AnTier(label: t.0, c: t.2, bg: t.3, items: t.4, ops: ops(t.1), time: tm(t.1), tc: tc(t.1)) }),
        ], [F("n² at n = \(big(n)) =", tm(2 * log10(n)), tc(2 * log10(n)))],
            ("At n = \(big(n)), O(2ⁿ) takes \(tm(n * log10(2))).", "Green finishes under a millisecond, yellow within an hour, red is impractical."),
            AnDock(slider: AnSlider(name: "Input size", labels: ns.map { "n = \(big($0))" })))
    }
}

// MARK: - 57m Sandbox

private let ALG = ["Linear", "Insertion", "Bubble", "Selection"]
private let SH3 = ["Sorted", "Reversed", "Random"]

private func sandbox() -> AnLab {
    AnLab(initial: AnVals(opt: 1, opt2: 1, ni: 1, runs: [(0, 1, 16), (1, 1, 16)])) { s, _ in
        let ns = [8, 16, 24, 32, 48, 64]
        let col = [B, Y, K, G]
        func run(_ alg: Int, _ sh: Int, _ n: Int) -> Int {
            let a = shape(sh, n, 5)
            switch alg { case 0: return linC(a, n - 1); case 1: return insC(a); case 2: return bubC(a); default: return selC(a) }
        }
        let rs = s.runs.map { ($0.0, $0.1, $0.2, run($0.0, $0.1, $0.2)) }
        let mx = Double(max(1, rs.map(\.3).max() ?? 0))
        var blocks: [AnBlock]
        if let last = rs.last {
            blocks = [
                bigs(("\(last.3)", "\(ALG[last.0].lowercased()) · \(SH3[last.1].lowercased()) · n = \(last.2)", ON)),
                L("every run so far, operations"),
                .bars(rs.map { r in row("\(ALG[r.0]) · \(SH3[r.1].prefix(3).lowercased()) · \(r.2)", num(Double(r.3)), Double(r.3) / mx, col[r.0], "#c3c7d1") }, labelW: 128, valueW: 48),
            ]
        } else {
            blocks = [L("No runs yet — pick an algorithm, a shape and a size, then tap Run.")]
        }
        return frame("Sandbox Mode", blocks,
            rs.count > 1 ? [F("latest ÷ first =", fxd(Double(rs.last!.3) / Double(rs[0].3), 1) + "×", Pl)] : [],
            ("Stack runs, compare anything.", "Try one algorithm on all three shapes, or one shape across all four algorithms."),
            AnDock(segs: [AnSeg(labels: ALG, selected: s.opt) { v, i in var v = v; v.opt = i; return v },
                          AnSeg(labels: SH3, selected: s.opt2) { v, i in var v = v; v.opt2 = i; return v }],
                   slider: AnSlider(name: "Input size", labels: ns.map { "n = \($0)" }),
                   btn: AnBtn(label: "Run") { v in var v = v; v.runs = Array((v.runs + [(v.opt, v.opt2, ns[v.ni])]).suffix(7)); return v },
                   alt: AnBtn(label: "Clear") { v in var v = v; v.runs = []; return v }),
            ALG.enumerated().map { i, l in lg(col[i], l) })
    }
}
