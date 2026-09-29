import SwiftUI

// Port of TextLabs.kt. Naive search, KMP, Z algorithm, Rabin-Karp, Manacher and longest palindrome as
// storyboards over rows of characters: the text under an index header, patterns and arrays aligned
// beneath it, small labels under the cells that matter (i, C, R, the Z-box), then the comparison or
// recurrence, chips and a headline. Every value is computed.

/// `struck` is a value ruled out for good (two pointers, Kadane's dropped prefix), drawn dim and crossed out.
/// `outline` is a navy tile with a blue border: still possible, in the heap, waiting on a stack.
/// `green` is a solid green tile (a two-heaps upper half, a matched prefix).
/// `stack` is a dark tile with a yellow border (a call waiting on the stack); `memo` a violet-bordered memo hit.
/// `frontier` is an empty tile with a blue border (the next BFS wave); `rose` a solid pink (a bipartite side).
/// `plain` is bare text with no tile (the "= 7" after a row of bits, a spacer).
enum TTone { case idle, active, blue, matched, mismatch, mirror, answer, pending, ghost, ghostMatched, struck, outline, green, stack, memo, frontier, rose, plain }

struct TCell {
    let text: String
    let tone: TTone
    /// A small line above the value (a hash tile's window, "415").
    var top: String? = nil
    init(_ text: String, _ tone: TTone, top: String? = nil) { self.text = text; self.tone = tone; self.top = top }
}

struct TRow {
    var label = ""
    /// A caps title over the row ("P (RADIUS)").
    var title: String? = nil
    var note: String? = nil
    var offset = 0
    let cells: [TCell]
    /// Labels under cells, keyed by column: "i", "C", "window".
    var tags: [Int: (String, TTone)] = [:]
    /// Tiles spread across the full width instead of sitting on the column grid (Rabin-Karp's hashes).
    var spread = false
    /// Smaller spread tiles for long rows; opt-in so existing labs keep their size.
    var compact = false
    /// Spread rows only: cells from `split` on form a second group with its own title (two heaps, merge halves).
    var split: Int? = nil
    var splitTitle: String? = nil
    /// A thin rule between the two groups instead of a gap (meet in the middle's halves).
    var divider = false
    /// Tints the row label (the row being filled).
    var labelTone: TTone? = nil
    /// Spread rows only: a glyph between neighbouring tiles (a doubly linked list's ⇄).
    var joiner: String? = nil
}

struct TFrame {
    let columns: Int
    var lit: [Int: TTone] = [:]
    let rows: [TRow]
    var formula: [String] = []
    var formulaRows: [StoryFormulaRow] = []
    var chips: [StoryChip] = []
    let headline: String
    let body: String
    /// Queries or updates as labelled bars (Mo's, difference array), above the grid when `listFirst`.
    var list: [TListRow] = []
    var listFirst = false
    /// Monte Carlo's samples in the unit square; drawn instead of the grid.
    var scatter: [(Double, Double)] = []
    /// A linked list drawn as nodes and arrows (fast and slow pointers, in-place reversal).
    var chain: TChain? = nil
    /// Intervals as bars on a number line (merge intervals, greedy scheduling).
    var timeline: TTimeline? = nil
    /// Values as vertical bars (in-place partitioning).
    var bars: TBars? = nil
    /// A caps title over the index header ("ROTATED ARRAY · TARGET 8").
    var heading: String? = nil
    /// A caps title over the list ("PROBES").
    var listTitle: String? = nil
    /// Draw the timeline above the rows (heap scheduling's heap sits under its meetings).
    var timelineFirst = false
    /// A call tree or rooted tree, drawn above the rows.
    var tree: TTree? = nil
    /// Jobs as proportional bars, one lane per row (the exchange argument).
    var gantt: [TGanttRow] = []
    /// Column labels for the index header instead of 0, 1, 2 (binary lifting's A…G).
    var headers: [String]? = nil
    /// Nodes as circles joined by (weighted, directed) edges, drawn above the rows.
    var graph: TGraph? = nil
    /// Grid rows with no index header: a map of cells (multi-source BFS, islands).
    var noHeader = false
    /// Spread rows drawn above the heading (game theory's piles over its table).
    var topRows: [TRow] = []
    /// Lines of state chips and transition labels under the formula (rest → buy → hold …); `true` marks a chip.
    var flow: [[(String, Bool)]] = []
    /// With `topColumns`, `topRows` form a headed grid of their own (2D prefix sums' input matrix).
    var topColumns = 0
    var topHeaders: [String]? = nil
    var topHeading: String? = nil
}

struct TGNode { let label: String; let x: Double; let y: Double }
struct TGEdge { let a: Int; let b: Int; var weight: String? = nil; var tone: TTone? = nil; var dashed = false }

/// Circles at fractional positions; `captions` sit under a node ("15 → 17", "1st"); an edge's `tone` colours it.
struct TGraph {
    let nodes: [TGNode]
    var edges: [TGEdge]
    var directed = true
    var tones: [Int: TTone] = [:]
    var captions: [Int: (String, TTone)] = [:]
    var base: TTone = .pending
}

struct TNode { let label: String; var sub: String? = nil; let parent: Int? }

/// A curved arrow beside the tree from one node to another (a binary-lifting jump), on the right or left.
struct TArc { let from: Int; let to: Int; let label: String; let tone: TTone; let right: Bool }

/// Nodes laid out by leaf slots (or at fixed `pos`: x as a fraction of the width, and a row). `tones` default
/// to `base`; `edges` colours the edge into a node; `badges` sit to a node's right; `labels` rename a node.
struct TTree {
    let nodes: [TNode]
    var tones: [Int: TTone] = [:]
    var labels: [Int: String] = [:]
    var badges: [Int: (String, TTone)] = [:]
    var edges: [Int: TTone] = [:]
    var arcs: [TArc] = []
    var pos: [(Double, Int)]? = nil
    var base: TTone = .pending
}

struct TGanttRow { let title: String; let note: String; let jobs: [(Int, TTone)] }

/// A size or target the lab rebuilds its tabs for ("n 6", "Items · target 15  6").
struct TParam { let label: (Int) -> String; let values: [Int]; let start: Int; let build: (Int) -> [TTab] }

/// One bar per value with its index underneath and a tag row below that; `arc` is a pending swap, dashed.
struct TBars {
    let values: [Int]
    let tones: [TTone]
    var tags: [Int: (String, TTone)] = [:]
    var arc: (Int, Int)? = nil
}

/// Nodes in a row. `next[i]` is the node i points at (nil for null); neighbours get a straight arrow,
/// anything else an arc under the row. `special` restyles one node's pointer as an arc in a tone with an
/// optional label (the cycle edge, the pointer being rewired); `cut` is a link being broken, drawn dashed red.
struct TChain {
    let nodes: [TCell]
    let next: [Int?]
    var special: [Int: (TTone, String?)] = [:]
    var cut: (Int, Int)? = nil
    var above: [Int: (String, TTone)] = [:]
    var below: [Int: (String, TTone)] = [:]
    /// Tone for the straight arrow leaving node i; idle when absent.
    var arrowTones: [Int: TTone] = [:]
}

/// `side` is a small label right of the bar (the room a meeting landed in).
struct TBar { let start: Double; let end: Double; var label: String? = nil; let tone: TTone; var side: String? = nil }

/// One bar per row, then an output lane under a rule; `marker` is a dashed vertical line (lastEnd).
struct TTimeline {
    let maxX: Double
    let step: Double
    let bars: [TBar]
    var output: [TBar] = []
    var marker: Double? = nil
    var markerTone: TTone = .blue
}

/// One query or update: "Q2  [0, 5]  block 0  26". `tone` is matched (done), active (current) or pending.
struct TListRow { let name: String; let range: String; let note: String; let value: String; let tone: TTone }

struct TTab {
    let label: String
    let frames: [TFrame]
    let legend: [(Color, SwatchStyle, String)]
    /// A stepper over the card whose value names each step ("Samples 300"); − and + move between steps.
    var stepper: (label: String, values: [Int])? = nil
    /// The step the tab opens on (Top-K opens in its select phase).
    var start = 0
}

private func chars(_ s: String) -> [String] { s.map { String($0) } }
private func q(_ s: String) -> String { "'\(s)'" }

// MARK: - Naive search

private func naiveTabs() -> [TTab] {
    let t = chars("ABCABCABD"), p = chars("ABCABD")
    let n = t.count, m = p.count
    var frames: [TFrame] = []
    var comparisons = 0
    let found = (0...(n - m)).first { s in (0..<m).allSatisfy { t[s + $0] == p[$0] } }!
    let summary = [StoryFormulaRow(label: "match found at", formula: "s = \(found), after \(found) shift\(found == 1 ? "" : "s")")]
    func textRow(_ col: Int?) -> TRow { TRow(label: "T", cells: t.enumerated().map { TCell($1, $0 == col ? .active : .idle) }) }
    func patRow(_ s: Int, upTo j: Int, bad: Bool) -> TRow {
        TRow(label: "s=\(s)", offset: s, cells: p.enumerated().map { k, c in
            TCell(c, k < j ? .matched : k == j ? (bad ? .mismatch : .matched) : .pending)
        })
    }
    frames.append(TFrame(columns: n, rows: [textRow(nil), TRow(label: "s=0", cells: p.map { TCell($0, .pending) })], formula: ["slide P along T, comparing left to right"],
                         formulaRows: summary, chips: [StoryChip("s", "0"), StoryChip("comparisons", "0")],
                         headline: "Try the pattern at every shift s, comparing character by character.",
                         body: "A mismatch moves the pattern one place right and starts over from its first character."))
    for s in 0...found {
        for j in 0..<m {
            comparisons += 1
            let ok = t[s + j] == p[j]
            var rows = [textRow(s + j), patRow(s, upTo: j, bad: !ok)]
            if !ok { rows.append(TRow(label: "s=\(s + 1)", offset: s + 1, cells: p.map { TCell($0, .ghost) })) }
            let chips = [StoryChip("s", "\(s)"), StoryChip("comparisons", "\(comparisons)")]
            if ok && j == m - 1 {
                frames.append(TFrame(columns: n, lit: [s + j: .active], rows: [textRow(nil), TRow(label: "s=\(s)", offset: s, cells: p.map { TCell($0, .answer) })],
                                     formula: ["T[\(s)..\(s + m - 1)] = P → {v:match at s = \(s)}"], formulaRows: summary,
                                     chips: [StoryChip("s", "\(s)", .answer), StoryChip("comparisons", "\(comparisons)")],
                                     headline: "All \(m) characters match: the pattern is at {v:s = \(s)}.",
                                     body: "\(comparisons) comparisons in all. In the worst case naive search costs O(n · m)."))
                break
            }
            if ok {
                frames.append(TFrame(columns: n, lit: [s + j: .active], rows: rows, formula: ["T[\(s + j)] = \(q(t[s + j])) = P[\(j)] → next"], formulaRows: summary, chips: chips,
                                     headline: "T[\(s + j)] = P[\(j)] = \(q(p[j])), so j = \(j) matches.",
                                     body: "Compare left to right until a character differs or the whole pattern matches."))
            } else {
                frames.append(TFrame(columns: n, lit: [s + j: .active], rows: rows,
                                     formula: ["T[\(s + j)] = {w:\(q(t[s + j]))}", "≠ P[\(j)] = \(q(p[j])) → shift to s = \(s + 1)"], formulaRows: summary, chips: chips,
                                     headline: j > 0 ? "Mismatch at j = \(j) after \(j) match\(j == 1 ? "" : "es"), so shift by one." : "\(q(t[s + j])) ≠ \(q(p[0])) at once, so shift by one.",
                                     body: j > 0 ? "The \(j) matched character\(j == 1 ? " is" : "s are") compared again at s = \(s + 1). KMP avoids that by reusing them."
                                         : "Nothing matched, so this shift cost a single comparison."))
                break
            }
        }
    }
    return [TTab(label: "Search", frames: frames, legend: [(SimColors.active, .fill, "Comparing"), (SimColors.green, .fill, "Matched this alignment"), (SimColors.red, .fill, "Mismatch")])]
}

// MARK: - KMP

private func lpsOf(_ p: [String]) -> [Int] {
    var lps = [Int](repeating: 0, count: p.count), len = 0, i = 1
    while i < p.count {
        if p[i] == p[len] { len += 1; lps[i] = len; i += 1 } else if len > 0 { len = lps[len - 1] } else { lps[i] = 0; i += 1 }
    }
    return lps
}

private func kmpTabs() -> [TTab] {
    let t = chars("ABABABC"), p = chars("ABABC")
    let n = t.count, m = p.count
    let lps = lpsOf(p)
    var frames: [TFrame] = []
    var i = 0, j = 0
    func textRow(_ col: Int?) -> TRow { TRow(label: "T", cells: t.enumerated().map { TCell($1, $0 == col ? .active : .idle) }) }
    func patRow(_ offset: Int, upTo j: Int, bad: Bool) -> TRow {
        TRow(label: "P", offset: offset, cells: p.enumerated().map { k, c in TCell(c, k < j ? .matched : k == j ? (bad ? .mismatch : .matched) : .pending) })
    }
    func lpsRow(_ offset: Int, lit: Int?) -> TRow { TRow(label: "lps", offset: offset, cells: lps.enumerated().map { TCell("\($1)", $0 == lit ? .active : .pending) }) }
    frames.append(TFrame(columns: n, rows: [textRow(nil), TRow(label: "P", cells: p.map { TCell($0, .pending) }), lpsRow(0, lit: nil)],
                         formula: ["lps[j] = longest proper prefix of P[0..j] that is also its suffix"], chips: [StoryChip("i", "0"), StoryChip("j", "0")],
                         headline: "KMP never moves i backwards. On a mismatch, lps says how much of the pattern still matches.",
                         body: "The lps table depends only on the pattern, so it is built once, in O(m)."))
    while i < n {
        let offset = i - j
        if t[i] == p[j] {
            if j == m - 1 {
                frames.append(TFrame(columns: n, lit: [i: .active], rows: [textRow(nil), TRow(label: "P", offset: offset, cells: p.map { TCell($0, .answer) }), lpsRow(offset, lit: nil)],
                                     formula: ["\(q(t[i])) = \(q(p[j])) → {v:match at \(offset)}"], chips: [StoryChip("i", "\(i)"), StoryChip("found", "\(offset)", .answer)],
                                     headline: "The last character matches: the pattern is at {v:\(offset)}.",
                                     body: "i visited each text character once, so the search is O(n) on top of O(m) for lps."))
                break
            }
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [textRow(i), patRow(offset, upTo: j, bad: false), lpsRow(offset, lit: nil)],
                                 formula: ["T[\(i)] = \(q(t[i])) = P[\(j)] → i, j advance"], chips: [StoryChip("i", "\(i)"), StoryChip("j", "\(j)")],
                                 headline: "T[\(i)] = P[\(j)] = \(q(p[j])), so both pointers move on.",
                                 body: "Every matched character is a prefix of P that KMP can fall back into later."))
            i += 1; j += 1
        } else if j > 0 {
            let back = lps[j - 1]
            let next = TRow(label: "next", offset: i - back, cells: p.enumerated().map { TCell($1, $0 < back ? .ghostMatched : .ghost) })
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [textRow(i), patRow(offset, upTo: j, bad: true), lpsRow(offset, lit: j - 1), next],
                                 formula: ["{w:\(q(t[i])) ≠ \(q(p[j]))} → j = lps[\(j - 1)] = {\(back)}"], chips: [StoryChip("i", "\(i)"), StoryChip("j", "\(j) → \(back)")],
                                 headline: "T[\(i)] = \(q(t[i])) ≠ \(q(p[j])), so j falls back to lps[\(j - 1)] = {\(back)}, not 0.",
                                 body: back > 0 ? "\"\(p[0..<back].joined())\" already matches the end of \"\(p[0..<j].joined())\", so i never moves back."
                                     : "No prefix of the matched part is also its suffix, so j restarts at 0, but i still stays put."))
            j = back
        } else {
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [textRow(i), patRow(offset, upTo: 0, bad: true), lpsRow(offset, lit: nil)],
                                 formula: ["{w:\(q(t[i])) ≠ \(q(p[0]))} with j = 0 → i advances"], chips: [StoryChip("i", "\(i)"), StoryChip("j", "0")],
                                 headline: "Nothing to fall back on at j = 0, so only i moves.", body: "This is the one case where a comparison advances i without a match."))
            i += 1
        }
    }
    // Build LPS
    var build: [TFrame] = []
    var table = [Int?](repeating: nil, count: m)
    table[0] = 0
    func lpsCells(_ lit: Int?) -> [TCell] { table.enumerated().map { k, v in TCell(v.map(String.init) ?? "·", k == lit ? .active : v == nil ? .pending : .idle) } }
    build.append(TFrame(columns: m, rows: [TRow(label: "P", cells: p.map { TCell($0, .idle) }), TRow(label: "lps", cells: lpsCells(0))],
                        formula: ["lps[0] = {0}"], chips: [StoryChip("len", "0")],
                        headline: "lps[0] is always {0}: a single character has no proper prefix.",
                        body: "A second pointer, len, tracks how long the current matching prefix is."))
    var len = 0, k = 1
    while k < m {
        let pRow = TRow(label: "P", cells: p.enumerated().map { idx, c in TCell(c, idx == k ? .active : idx == len ? .blue : idx < len ? .matched : .idle) })
        if p[k] == p[len] {
            len += 1; table[k] = len
            build.append(TFrame(columns: m, rows: [pRow, TRow(label: "lps", cells: lpsCells(k))],
                                formula: ["P[\(k)] = \(q(p[k])) = P[\(len - 1)] → lps[\(k)] = {\(len)}"], chips: [StoryChip("len", "\(len)")],
                                headline: "P[\(k)] extends the matching prefix, so lps[\(k)] = {\(len)}.", body: "The prefix \"\(p[0..<len].joined())\" is also a suffix of \"\(p[0...k].joined())\"."))
            k += 1
        } else if len > 0 {
            let back = table[len - 1]!
            build.append(TFrame(columns: m, rows: [pRow, TRow(label: "lps", cells: lpsCells(len - 1))],
                                formula: ["{w:\(q(p[k])) ≠ \(q(p[len]))} → len = lps[\(len - 1)] = {\(back)}"], chips: [StoryChip("len", "\(len) → \(back)")],
                                headline: "\(q(p[k])) ≠ \(q(p[len])), so len falls back to lps[\(len - 1)] = {\(back)}.", body: "The table is built with the same fallback it will be used for."))
            len = back
        } else {
            table[k] = 0
            build.append(TFrame(columns: m, rows: [pRow, TRow(label: "lps", cells: lpsCells(k))],
                                formula: ["{w:\(q(p[k])) ≠ \(q(p[0]))} with len = 0 → lps[\(k)] = {0}"], chips: [StoryChip("len", "0")],
                                headline: "\(q(p[k])) matches no prefix, so lps[\(k)] = {0}.", body: "A mismatch here means the search will restart the pattern from its first character."))
            k += 1
        }
    }
    build.append(TFrame(columns: m, rows: [TRow(label: "P", cells: p.map { TCell($0, .idle) }), TRow(label: "lps", cells: table.map { TCell("\($0!)", .answer) })],
                        formula: ["lps = {v:" + table.map { "\($0!)" }.joined(separator: " ") + "}"], chips: [StoryChip("cost", "O(m)", .answer)],
                        headline: "The lps table is {v:" + table.map { "\($0!)" }.joined(separator: " ") + "}.", body: "len only grows by one per step and never falls further than it rose, so building is O(m)."))
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Reading"), (SimColors.green, .fill, "Matched"), (SimColors.red, .fill, "Mismatch")]
    return [TTab(label: "Search", frames: frames, legend: legend), TTab(label: "Build LPS", frames: build, legend: legend + [(SimColors.blue, .fill, "Prefix end")])]
}

// MARK: - Z algorithm

private func zTabs() -> [TTab] {
    let s = chars("aabcaabxaaz"), n = s.count
    var z = [Int?](repeating: nil, count: n)
    var frames: [TFrame] = []
    var l = 0, r = 0
    func zRow(_ lit: Int?, mirror: Int?) -> TRow {
        TRow(label: "Z", cells: (0..<n).map { k in
            if k == 0 { return TCell("-", .idle) }
            guard let v = z[k] else { return TCell("·", .pending) }
            return TCell("\(v)", k == lit ? .active : k == mirror ? .mirror : .idle)
        })
    }
    func sRow(_ i: Int?, box: ClosedRange<Int>?, k: Int?, len: Int) -> TRow {
        var tags: [Int: (String, TTone)] = [:]
        if let box { tags[box.lowerBound] = ("l", .blue); tags[box.upperBound] = ("r", .blue) }
        if let i { tags[i] = ("i", .active) }
        if let k { tags[k] = ("k", .matched) }
        return TRow(label: "s", cells: s.enumerated().map { idx, c in
            if idx == i { return TCell(c, .active) }
            if let box, box.contains(idx) { return TCell(c, .blue) }
            if let k, idx == k { return TCell(c, .mirror) }
            if len > 0 && idx < len { return TCell(c, .matched) }
            return TCell(c, .idle)
        }, tags: tags)
    }
    frames.append(TFrame(columns: n, rows: [sRow(nil, box: nil, k: nil, len: 0), zRow(nil, mirror: nil)], formula: ["Z[i] = length of the longest prefix of s starting at i"],
                         chips: [StoryChip("l", "0"), StoryChip("r", "0")],
                         headline: "Z[i] counts how far s[i..] agrees with the start of s.",
                         body: "A Z-box [l, r] remembers the rightmost match, so later positions can copy instead of compare."))
    for i in 1..<n {
        let inBox = i <= r
        let k = i - l
        var v = 0
        var formula: String, headline: String, body: String
        if inBox && z[k]! < r - i + 1 {
            v = z[k]!
            z[i] = v
            formula = "Z[\(i)] = min(Z[\(k)], r − i + 1) = min(\(z[k]!), \(r - i + 1)) = {\(v)}"
            headline = "i = \(i) is inside the Z-box, so Z[\(i)] copies Z[\(k)] = {\(v)} without comparing."
            body = "The box [\(l), \(r)] equals the prefix \"\(s[0...(r - l)].joined())\", so position \(i) mirrors position \(k)."
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [sRow(i, box: l...r, k: k, len: r - l + 1), zRow(i, mirror: k)], formula: [formula],
                                 chips: [StoryChip("l", "\(l)"), StoryChip("r", "\(r)"), StoryChip("k", "\(k)")], headline: headline, body: body))
            continue
        }
        v = inBox ? r - i + 1 : 0
        let start = v
        while i + v < n && s[v] == s[i + v] { v += 1 }
        z[i] = v
        if v > 0 && i + v - 1 > r { l = i; r = i + v - 1 }
        if inBox {
            formula = "Z[\(k)] reaches the box edge → start at \(start), compare on → {\(v)}"
            headline = "i = \(i) runs into the box edge, so Z[\(i)] starts at \(start) and compares on: {\(v)}."
            body = "Only the characters beyond r cost comparisons, which is what keeps Z linear."
        } else if v == 0 {
            formula = "s[\(i)] = \(q(s[i])) ≠ s[0] = \(q(s[0])) → Z[\(i)] = {0}"
            headline = "\(q(s[i])) ≠ \(q(s[0])), so Z[\(i)] = {0}."
            body = "Outside any Z-box there is nothing to copy, so it compares from scratch."
        } else {
            formula = "s[\(i)..\(i + v - 1)] = s[0..\(v - 1)] → Z[\(i)] = {\(v)}"
            headline = "i = \(i) is outside the box, so compare from scratch: {\(v)} character\(v == 1 ? "" : "s") match the prefix."
            body = "A match opens a new Z-box [\(l), \(r)] for the positions after it."
        }
        frames.append(TFrame(columns: n, lit: [i: .active], rows: [sRow(i, box: r > 0 ? l...r : nil, k: nil, len: v), zRow(i, mirror: nil)], formula: [formula],
                             chips: [StoryChip("l", "\(l)"), StoryChip("r", "\(r)")], headline: headline, body: body))
    }
    frames.append(TFrame(columns: n, rows: [sRow(nil, box: nil, k: nil, len: 0), TRow(label: "Z", cells: (0..<n).map { $0 == 0 ? TCell("-", .idle) : TCell("\(z[$0]!)", .answer) })],
                         formula: ["Z = {v:" + (1..<n).map { "\(z[$0]!)" }.joined(separator: " ") + "}"], chips: [StoryChip("cost", "O(n)", .answer)],
                         headline: "The Z array is complete in {v:O(n)}.",
                         body: "To search, run it on pattern + \"$\" + text: every Z equal to the pattern's length is a match."))
    return [TTab(label: "Z array", frames: frames, legend: [(SimColors.active, .fill, "Current index"), (SimColors.blue, .fill, "Z-box [l, r]"), (SimColors.green, .fill, "Prefix it matches")])]
}

// MARK: - Rabin-Karp

private func rabinTabs() -> [TTab] {
    let text = chars("31415926"), pattern = "415"
    let m = pattern.count, mod = 13, base = 10
    let n = text.count
    let windows = n - m + 1
    let pHash = Int(pattern)! % mod
    let high = Int(pow(Double(base), Double(m - 1))) % mod
    var hashes: [Int] = []
    var h = 0
    for k in 0..<m { h = (h * base + Int(text[k])!) % mod }
    hashes.append(h)
    for s in 1..<windows { h = ((h - Int(text[s - 1])! * high % mod + mod) * base + Int(text[s + m - 1])!) % mod; hashes.append(h) }
    func digits(_ s: Int?) -> TRow {
        var tags: [Int: (String, TTone)] = [:]
        if let s { tags[s + 1] = ("window", .blue) }
        return TRow(cells: text.enumerated().map { k, c in TCell(c, s.map { k >= $0 && k < $0 + m } == true ? .blue : .idle) }, tags: tags)
    }
    func tiles(_ current: Int?, hit: Bool = false) -> TRow {
        TRow(title: "WINDOW HASH MOD \(mod) · PATTERN \(pattern) → \(pHash)", cells: (0..<windows).map { s in
            let win = text[s..<(s + m)].joined()
            guard let current, s <= current else { return TCell("·", .pending, top: win) }
            return TCell("\(hashes[s])", s == current ? (hit ? .answer : .active) : .matched, top: win)
        }, spread: true)
    }
    func lit(_ s: Int) -> [Int: TTone] { Dictionary(uniqueKeysWithValues: (s..<(s + m)).map { ($0, .active) }) }
    var frames = [TFrame(columns: n, rows: [digits(nil), tiles(nil)], formula: ["hash(\(pattern)) = \(pattern) mod \(mod) = {\(pHash)}"],
                         chips: [StoryChip("pattern", "\(pHash)")],
                         headline: "Hash the pattern once: \(pattern) mod \(mod) = {\(pHash)}.",
                         body: "Each window of \(m) digits gets the same kind of hash, and only equal hashes are compared character by character.")]
    for s in 0..<windows {
        let win = text[s..<(s + m)].joined()
        let hit = hashes[s] == pHash
        let formula: String
        if s == 0 { formula = "\(win) mod \(mod) = {\(hashes[0])}" }
        else {
            let prev = hashes[s - 1], lead = Int(text[s - 1])!, add = Int(text[s + m - 1])!
            let raw = (prev - lead * high) * base + add
            formula = "(\(prev) − \(lead)·\(high))·\(base) + \(add) = \(raw) ≡ {\(hashes[s])} (mod \(mod))"
        }
        let headline = hit ? "Window \(win) hashes to {\(hashes[s])}, same as the pattern, so compare the characters."
            : "Window \(win) hashes to {\(hashes[s])}, not \(pHash), so skip it without comparing."
        let body = s == 0 ? "The first window is hashed in full. Every later one rolls in O(1)."
            : "Rolling costs O(1): drop the leading \(text[s - 1]), shift, add \(text[s + m - 1]). Equal hashes still need a character check."
        frames.append(TFrame(columns: n, lit: lit(s), rows: [digits(s), tiles(s)], formula: [formula],
                             chips: [StoryChip("pattern", "\(pHash)"), StoryChip("window", "\(hashes[s])", hit ? .active : .idle)], headline: headline, body: body))
        if hit {
            frames.append(TFrame(columns: n, lit: lit(s), rows: [digits(s), tiles(s, hit: true)], formula: ["\(win) = \(pattern) → {v:match at \(s)}"],
                                 chips: [StoryChip("found", "\(s)", .answer)],
                                 headline: "The characters agree too: {v:\(pattern)} is at index \(s).",
                                 body: "Without the check, a different window with the same hash would be reported as a false match."))
        }
    }
    frames.append(TFrame(columns: n, rows: [digits(nil), tiles(windows - 1)], formula: ["\(windows) windows, 1 character check"],
                         chips: [StoryChip("windows", "\(windows)"), StoryChip("checks", "1", .answer)],
                         headline: "{v:\(windows)} windows hashed, and only one needed a character check.",
                         body: "Expected O(n + m); a bad hash that collides often degrades it to O(n · m)."))
    return [TTab(label: "Search", frames: frames,
                 legend: [(SimColors.blue, .fill, "Window"), (SimColors.active, .fill, "Hash being checked"), (SimColors.green, .fill, "No hit"), (SimColors.answer, .fill, "Match")])]
}

// MARK: - Palindromes

/// Manacher over "#a#b#a#c#a#b#a#": radius per centre, reusing the mirror inside the rightmost palindrome.
private func manacherFrames(_ word: String) -> [TFrame] {
    let t = ["#"] + chars(word).flatMap { [$0, "#"] }
    let n = t.count
    var p = [Int?](repeating: nil, count: n)
    var c = 0, r = 0
    var best = 0, bestAt = 0
    var frames: [TFrame] = []
    for i in 0..<n {
        let mirror = 2 * c - i
        var start = 0
        var fromMirror = false
        if i < r { start = min(r - i, p[mirror]!); fromMirror = true }
        var k = start
        while i - k - 1 >= 0 && i + k + 1 < n && t[i - k - 1] == t[i + k + 1] { k += 1 }
        p[i] = k
        let expanded = k - start
        let oldC = c, oldR = r
        if i + k > r { c = i; r = i + k }
        if k > best { best = k; bestAt = i }
        var tags: [Int: (String, TTone)] = [:]
        if fromMirror { tags[mirror] = ("mir", .matched) }
        tags[oldC] = ("C", .blue)
        tags[i] = ("i", .active)
        if oldR < n { tags[max(oldR, 0)] = tags[max(oldR, 0)] ?? ("R", .blue) }
        let row = TRow(cells: t.enumerated().map { idx, ch in
            if idx == i { return TCell(ch, .active) }
            if fromMirror && idx == mirror { return TCell(ch, .mirror) }
            if abs(idx - oldC) <= (p[oldC] ?? 0) && oldR > 0 { return TCell(ch, .blue) }
            return TCell(ch, .idle)
        }, tags: tags)
        let pRow = TRow(title: "P (RADIUS)", cells: (0..<n).map { idx in
            guard let v = p[idx] else { return TCell("·", .pending) }
            if idx == i { return TCell("\(v)", .active) }
            if fromMirror && idx == mirror { return TCell("\(v)", .mirror) }
            if idx == bestAt && v == best { return TCell("\(v)", .answer) }
            return TCell("\(v)", .idle)
        })
        let formula: [String] = fromMirror
            ? ["mirror = 2·\(oldC) − \(i) = \(mirror) → P[\(i)] =", "min(P[\(mirror)], R − i) = {\(start)}" + (expanded > 0 ? " → expand to {\(k)}" : "")]
            : ["i ≥ R, so start at 0 → expand → P[\(i)] = {\(k)}"]
        let headline: String, body: String
        if fromMirror {
            headline = "i = \(i) mirrors \(mirror) across centre \(oldC), so P[\(i)] starts at {\(start)} for free."
            if expanded > 0 { body = "Past the right edge R it expands by \(expanded) more with direct checks." }
            else if i + start >= oldR && i + start + 1 < n { body = "It reaches the right edge R, so one direct check is tried, and it fails." }
            else if i + start >= oldR { body = "It reaches the right edge R, so one direct check is tried, but the string ends there." }
            else { body = "The mirror's palindrome sits wholly inside C's, so no check is needed at all." }
        } else {
            headline = k == 0 ? "t[\(i)] is outside every palindrome so far, and it cannot grow: P[\(i)] = {0}." : "t[\(i)] starts from 0 and expands to P[\(i)] = {\(k)}."
            body = k > 0 && i + k > oldR ? "It pushes past R, so it becomes the new centre C = \(i), R = \(i + k)." : "Each '#' stands for a gap, so even and odd palindromes are handled alike."
        }
        frames.append(TFrame(columns: n, lit: [i: .active], rows: [row, pRow], formula: formula,
                             chips: [StoryChip("C", "\(c)"), StoryChip("R", "\(r)"), StoryChip("longest", "\(best)", .answer)], headline: headline, body: body))
    }
    let s = chars(word)
    let startIdx = (bestAt - best) / 2
    let answer = s[startIdx..<(startIdx + best)].joined()
    frames.append(TFrame(columns: n, rows: [TRow(cells: t.enumerated().map { TCell($1, abs($0 - bestAt) <= best ? .answer : .idle) }),
                                            TRow(title: "P (RADIUS)", cells: p.enumerated().map { TCell("\($1!)", $0 == bestAt ? .answer : .idle) })],
                         formula: ["max P = \(best) at \(bestAt) → {v:\(answer)}"], chips: [StoryChip("longest", answer, .answer)],
                         headline: "The longest palindrome is {v:\(answer)}, length \(best).",
                         body: "R only ever moves right, so every character is expanded past at most once: O(n)."))
    return frames
}

private func expandFrames(_ word: String) -> [TFrame] {
    let s = chars(word), n = s.count
    var frames: [TFrame] = []
    var best = (0, 0)
    let total = 2 * n - 1
    for c in 0..<total {
        var l = c / 2, r = (c + 1) / 2
        guard s[l] == s[r] else {
            frames.append(TFrame(columns: n, lit: [l: .mismatch, r: .mismatch], rows: [TRow(cells: s.enumerated().map { TCell($1, $0 == l || $0 == r ? .mismatch : .idle) })],
                                 formula: ["s[\(l)] = \(q(s[l])) ≠ s[\(r)] = \(q(s[r])) → length 0"], formulaRows: [StoryFormulaRow(label: "best so far", formula: "\(q(s[best.0...best.1].joined())), length \(best.1 - best.0 + 1)")],
                                 chips: [StoryChip("centre", "\(c + 1) of \(total)"), StoryChip("best", s[best.0...best.1].joined(), .answer)],
                                 headline: "The gap between \(q(s[l])) and \(q(s[r])) has different sides, so nothing grows there.",
                                 body: "Every character and every gap is a centre (\(total) here). Each one expands until its ends differ."))
            continue
        }
        while l > 0 && r < n - 1 && s[l - 1] == s[r + 1] { l -= 1; r += 1 }
        let len = r - l + 1
        let before = best
        let newBest = len > before.1 - before.0 + 1
        if newBest { best = (l, r) }
        let centre = c % 2 == 0 ? q(s[c / 2]) : "the gap"
        var tags: [Int: (String, TTone)] = [:]
        if len > 1 { tags[l] = ("L", .active); tags[r] = ("R", .active) }
        tags[c / 2] = tags[c / 2] ?? ("centre", .blue)
        let row = TRow(cells: s.enumerated().map { idx, ch in
            if len > 1 && (idx == l || idx == r) { return TCell(ch, .active) }
            if idx >= l && idx <= r { return TCell(ch, len == 1 ? .active : .blue) }
            return TCell(ch, .idle)
        }, tags: tags)
        let d = (r - l) / 2
        let formula = len > 1 ? "s[\(c / 2)−\(d)] = s[\((c + 1) / 2)+\(d)] → \(q(s[l])) = \(q(s[r])) → length {v:\(len)}" : "a single character → length {\(len)}"
        frames.append(TFrame(columns: n, lit: len > 1 ? [l: .active, r: .active] : [c / 2: .active], rows: [row], formula: [formula],
                             formulaRows: [StoryFormulaRow(label: "best before", formula: "\(q(s[before.0...before.1].joined())), length \(before.1 - before.0 + 1)")],
                             chips: [StoryChip("centre", "\(c + 1) of \(total)"), StoryChip("best", s[best.0...best.1].joined(), .answer)],
                             headline: len > 1 ? "s[\(l)] = s[\(r)], so the palindrome around \(centre) grows to {v:\(len)}." : "\(centre) alone is a palindrome of length {1}.",
                             body: newBest ? "Every character and gap is a centre (\(total) here). Each one expands until its ends differ."
                                 : "Not longer than the best so far, so the best stays \(q(s[best.0...best.1].joined()))."))
    }
    let answer = s[best.0...best.1].joined()
    frames.append(TFrame(columns: n, rows: [TRow(cells: s.enumerated().map { TCell($1, $0 >= best.0 && $0 <= best.1 ? .answer : .idle) })],
                         formula: ["longest = {v:\(answer)}"], chips: [StoryChip("best", answer, .answer)],
                         headline: "The longest palindrome is {v:\(answer)}, length \(answer.count).",
                         body: "\(total) centres, each expanding up to n: O(n²) worst case, which Manacher brings down to O(n)."))
    return frames
}

private let palindromeLegend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Comparing"), (SimColors.blue, .fill, "Already mirrored"), (SimColors.answer, .fill, "New best")]
private let manacherLegend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Current"), (SimColors.blue, .fill, "Inside palindrome at C"),
                                                               (SimColors.green, .fill, "Mirror"), (SimColors.answer, .fill, "Longest so far")]

// MARK: - Lab

let textStoryTopicIds: Set<String> = ["naive_string_search", "kmp", "z_algorithm", "rabin_karp", "manacher", "longest_palindromic_substring",
                                     "two_pointer", "sliding_window", "prefix_sum", "kadanes_algorithm", "monte_carlo_method", "reservoir_sampling",
                                     "mos_algorithm", "difference_array", "two_pointer_pattern", "sliding_window_pattern", "prefix_sum_pattern",
                                     "running_best_pattern", "sweep_line_pattern", "rolling_hash_pattern", "prefix_function_pattern",
                                     "palindrome_expansion_pattern", "randomized_pattern", "fast_slow_pointers", "merge_intervals_pattern",
                                     "binary_search_answer", "top_k_pattern", "monotonic_stack_pattern", "cyclic_sort_pattern",
                                     "k_way_merge_pattern", "in_place_reversal_pattern", "greedy_intervals_pattern", "dutch_flag_pattern",
                                     "modified_binary_search_pattern", "divide_conquer_pattern", "lis_patience_pattern", "heap_scheduling_pattern",
                                     "hash_counting_pattern", "expression_stack_pattern", "monotonic_deque_pattern", "two_heaps_pattern",
                                     "memo_recursion_pattern", "meet_in_middle_pattern", "subsets_pattern", "backtracking_pattern",
                                     "bst_inorder_pattern", "greedy_exchange_pattern", "binary_lifting_pattern", "tree_dfs_pattern",
                                     "multi_source_bfs_pattern", "game_theory_dp_pattern", "matrix_islands_pattern", "dag_dp_pattern",
                                     "graph_coloring_pattern", "topological_sort_pattern", "shortest_path_pattern", "state_machine_dp_pattern",
                                     "grid_dp_pattern", "interval_dp_pattern", "matrix_transform_pattern", "bit_manipulation_pattern",
                                     "prefix_2d_pattern", "bit_trie_pattern", "composite_design_pattern"]

private func textTabs(_ topicId: String) -> [TTab] {
    switch topicId {
    case "naive_string_search": naiveTabs()
    case "kmp": kmpTabs()
    case "z_algorithm": zTabs()
    case "rabin_karp": rabinTabs()
    case "manacher": [TTab(label: "Manacher", frames: manacherFrames("abacaba"), legend: manacherLegend)]
    // The interview-prep pattern topics teach the same technique, so they share its storyboard.
    case "two_pointer", "two_pointer_pattern": twoPointerTabs()
    case "sliding_window", "sliding_window_pattern": slidingWindowTabs()
    case "prefix_sum", "prefix_sum_pattern": prefixSumTabs()
    case "running_best_pattern": kadaneTabs()
    case "sweep_line_pattern": differenceTabs()
    case "rolling_hash_pattern": rabinTabs()
    case "randomized_pattern": reservoirTabs()
    case "palindrome_expansion_pattern": textTabs("longest_palindromic_substring")
    // The prefix function is KMP's lps table, so that tab comes first here.
    case "prefix_function_pattern": kmpTabs().reversed()
    case "fast_slow_pointers": fastSlowTabs()
    case "merge_intervals_pattern": mergeIntervalTabs()
    case "binary_search_answer": binaryAnswerTabs()
    case "top_k_pattern": topKTabs()
    case "monotonic_stack_pattern": monoStackTabs()
    case "cyclic_sort_pattern": cyclicSortTabs()
    case "k_way_merge_pattern": kWayMergeTabs()
    case "in_place_reversal_pattern": reversalTabs()
    case "greedy_intervals_pattern": greedyIntervalTabs()
    case "dutch_flag_pattern": partitionTabs()
    case "modified_binary_search_pattern": rotatedSearchTabs()
    case "divide_conquer_pattern": divideConquerTabs()
    case "lis_patience_pattern": lisTabs()
    case "heap_scheduling_pattern": heapSchedulingTabs()
    case "hash_counting_pattern": hashCountingTabs()
    case "expression_stack_pattern": parsingStackTabs()
    case "monotonic_deque_pattern": monoDequeTabs()
    case "two_heaps_pattern": twoHeapsTabs()
    case "memo_recursion_pattern", "meet_in_middle_pattern", "subsets_pattern", "backtracking_pattern": textParam(topicId)!.build(textParam(topicId)!.start)
    case "bst_inorder_pattern": bstInorderTabs()
    case "greedy_exchange_pattern": exchangeTabs()
    case "binary_lifting_pattern": liftingTabs()
    case "tree_dfs_pattern": pathSumTabs()
    case "multi_source_bfs_pattern": multiSourceTabs()
    case "game_theory_dp_pattern": gameTheoryTabs()
    case "matrix_islands_pattern": islandsTabs()
    case "dag_dp_pattern": dagDpTabs()
    case "graph_coloring_pattern": twoColourTabs()
    case "topological_sort_pattern": topoTabs()
    case "shortest_path_pattern": weightedPathTabs()
    case "state_machine_dp_pattern": stateMachineTabs()
    case "grid_dp_pattern": gridDpTabs()
    case "interval_dp_pattern": intervalDpTabs()
    case "matrix_transform_pattern": matrixRotateTabs()
    case "bit_manipulation_pattern": xorSplitTabs()
    case "prefix_2d_pattern": prefix2dTabs()
    case "bit_trie_pattern": bitTrieTabs()
    case "composite_design_pattern": pairedDesignTabs()
    case "kadanes_algorithm": kadaneTabs()
    case "monte_carlo_method": monteCarloTabs()
    case "reservoir_sampling": reservoirTabs()
    case "mos_algorithm": mosTabs()
    case "difference_array": differenceTabs()
    default: [TTab(label: "Expand centres", frames: expandFrames("abacabad"), legend: palindromeLegend),
              TTab(label: "Manacher", frames: manacherFrames("abacabad"), legend: manacherLegend)]
    }
}

struct TextStoryLab: View {
    @State private var tabs: [TTab]
    private let param: TParam?
    @State private var value: Int
    @State private var tab = 0
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        let tabs = textTabs(topicId)
        _tabs = State(initialValue: tabs)
        param = textParam(topicId)
        _value = State(initialValue: textParam(topicId)?.start ?? 0)
        let first = PlaybackState(stepCount: tabs[0].frames.count, speedMs: 1000)
        if tabs[0].start > 0 { first.index = tabs[0].start }
        _playback = State(initialValue: first)
    }

    var body: some View {
        let frames = tabs[tab].frames
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                if tabs.count > 1 {
                    LabSegments(labels: tabs.map(\.label), selected: Binding(get: { tab }, set: { select($0) })).padding(.bottom, 14)
                }
                if let param, let at = param.values.firstIndex(of: value) {
                    StoryStepperRow(stepper: StoryStepper(label: param.label(value), value: value, canDecrease: at > 0, canIncrease: at < param.values.count - 1) { d in
                        value = param.values[min(max(at + d, 0), param.values.count - 1)]
                        tabs = param.build(value)
                        tab = min(tab, tabs.count - 1)
                        playback = PlaybackState(stepCount: tabs[tab].frames.count, speedMs: 1000)
                    })
                    .padding(.bottom, 12)
                }
                if let stepper = tabs[tab].stepper {
                    let i = min(playback.index, frames.count - 1)
                    StoryStepperRow(stepper: StoryStepper(label: stepper.label, value: stepper.values[i], canDecrease: i > 0, canIncrease: i < frames.count - 1) {
                        playback.jump(to: i + $0)
                    })
                    .padding(.bottom, 12)
                }
                if frame.listFirst && !frame.list.isEmpty { TListView(rows: frame.list, title: frame.listTitle).padding(.bottom, 14) }
                if let bars = frame.bars {
                    TBarsView(bars: bars).frame(height: 190)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                }
                if let tree = frame.tree {
                    TTreeView(tree: tree).frame(height: TTreeView.height(tree))
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                        .padding(.bottom, frame.rows.isEmpty ? 0 : 14)
                }
                if !frame.gantt.isEmpty {
                    TGanttView(rows: frame.gantt).frame(height: CGFloat(frame.gantt.count) * 68 + 8)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                }
                if let graph = frame.graph {
                    TGraphView(graph: graph).frame(height: 220)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                        .padding(.bottom, frame.rows.isEmpty ? 0 : 14)
                }
                if let top = frame.topHeading {
                    Text(top).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).padding(.bottom, 6)
                }
                if !frame.topRows.isEmpty {
                    TGrid(frame: TFrame(columns: frame.topColumns, rows: frame.topRows, headline: "", body: "", headers: frame.topHeaders, noHeader: frame.topColumns == 0))
                        .padding(.bottom, 12)
                }
                if let heading = frame.heading {
                    Text(heading).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).padding(.bottom, 6)
                }
                if !frame.scatter.isEmpty {
                    TScatterView(points: frame.scatter).frame(height: 250)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                }
                if frame.timelineFirst, let timeline = frame.timeline {
                    TTimelineView(timeline: timeline)
                        .frame(height: CGFloat(timeline.bars.count) * 24 + (timeline.output.isEmpty ? 0 : 36) + 44)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                        .padding(.bottom, 12)
                }
                if !frame.rows.isEmpty { TGrid(frame: frame).padding(.top, frame.bars == nil ? 0 : 14) }
                if let chain = frame.chain {
                    TChainView(chain: chain).frame(height: 150)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                        .padding(.top, frame.rows.isEmpty ? 0 : 12)
                }
                if !frame.timelineFirst, let timeline = frame.timeline {
                    TTimelineView(timeline: timeline)
                        .frame(height: CGFloat(timeline.bars.count) * 24 + (timeline.output.isEmpty ? 0 : 36) + 44)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                        .padding(.top, frame.rows.isEmpty ? 0 : 12)
                }
                if !frame.listFirst && !frame.list.isEmpty { TListView(rows: frame.list, title: frame.listTitle).padding(.top, 14) }
                if !frame.formula.isEmpty {
                    VStack(spacing: 4) {
                        ForEach(frame.formula.indices, id: \.self) { i in
                            storyText(frame.formula[i], palette).font(AppFont.mono(14)).foregroundStyle(palette.onSurface.opacity(0.8))
                                .multilineTextAlignment(.center).minimumScaleFactor(0.7)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12).padding(.horizontal, 10)
                    .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
                    .padding(.top, 12)
                }
                if !frame.formulaRows.isEmpty { StoryFormulaRows(rows: frame.formulaRows).padding(.top, 8) }
                if !frame.flow.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(frame.flow.indices, id: \.self) { l in
                            HStack(spacing: 5) {
                                ForEach(frame.flow[l].indices, id: \.self) { k in
                                    let (t, chip) = frame.flow[l][k]
                                    if chip {
                                        Text(t).font(AppFont.mono(12, .bold)).foregroundStyle(palette.onSurface).lineLimit(1).fixedSize()
                                            .padding(.horizontal, 8).padding(.vertical, 5).background(palette.muted.opacity(0.2), in: RoundedRectangle(cornerRadius: 6))
                                    } else {
                                        Text(t).font(AppFont.mono(12)).foregroundStyle(palette.muted).lineLimit(1).fixedSize()
                                    }
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity).padding(.top, 12)
                }
                StoryLegendRow(items: tabs[tab].legend).padding(.top, 14)
            }
            if !frame.chips.isEmpty { StoryChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) })
        }
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        let state = PlaybackState(stepCount: tabs[i].frames.count, speedMs: 1000)
        state.index = tabs[i].start
        playback = state
    }
}

private func tColors(_ tone: TTone, _ palette: Palette) -> (Color, Color) {
    let soft = palette.dark ? 0.22 : 0.16
    switch tone {
    case .idle: return (palette.muted.opacity(0.2), palette.onSurface)
    case .active: return (SimColors.active, Color(hex: 0x1F1A0A))
    case .blue: return (SimColors.blue, .white)
    case .matched: return (SimColors.green.opacity(soft), StoryTone.done.ink(palette))
    case .mismatch: return (SimColors.red.opacity(0.16), StoryTone.warn.ink(palette))
    case .mirror: return (SimColors.green.opacity(soft), StoryTone.done.ink(palette))
    case .answer: return (SimColors.answer, .white)
    case .pending: return (palette.muted.opacity(0.08), palette.muted.opacity(0.55))
    case .ghost: return (.clear, palette.muted.opacity(0.6))
    case .ghostMatched: return (SimColors.green.opacity(soft), StoryTone.done.ink(palette))
    case .struck: return (palette.muted.opacity(0.06), palette.muted.opacity(0.45))
    case .outline: return (SimColors.blue.opacity(0.28), palette.onSurface)
    case .green: return (SimColors.green, .white)
    case .stack: return (palette.muted.opacity(0.18), palette.onSurface)
    case .memo: return (SimColors.answer.opacity(0.14), Color(hex: 0xB9A5FF))
    case .frontier: return (palette.muted.opacity(0.06), palette.onSurface)
    case .rose: return (Color(hex: 0xE0457B), .white)
    case .plain: return (.clear, palette.muted)
    }
}

private func tTagInk(_ tone: TTone, _ palette: Palette) -> Color {
    switch tone {
    case .active: StoryTone.active.ink(palette)
    case .blue: StoryTone.path.ink(palette)
    case .matched, .mirror, .green: StoryTone.done.ink(palette)
    case .mismatch: StoryTone.warn.ink(palette)
    default: palette.muted
    }
}

private struct TGrid: View {
    let frame: TFrame
    @Environment(\.palette) private var palette

    var body: some View {
        let hasLabels = frame.rows.contains { !$0.label.isEmpty }
        let gutter: CGFloat = hasLabels ? 40 : 0
        let gap: CGFloat = frame.columns > 11 ? 3 : 5
        let height: CGFloat = frame.columns > 11 ? 30 : frame.columns > 9 ? 36 : 42
        let font: CGFloat = frame.columns > 11 ? 12 : frame.columns > 9 ? 14 : 16
        VStack(alignment: .leading, spacing: 8) {
            if !frame.noHeader { HStack(spacing: gap) {
                if hasLabels { Color.clear.frame(width: gutter, height: 1) }
                ForEach(0..<frame.columns, id: \.self) { c in
                    let tone = frame.lit[c]
                    Text(frame.headers?[c] ?? "\(c)").font(AppFont.mono(frame.columns > 11 ? 10 : 12, tone == nil ? .regular : .bold))
                        .foregroundStyle(tone.map { tTagInk($0, palette) } ?? palette.muted)
                        .lineLimit(1).minimumScaleFactor(0.6).frame(maxWidth: .infinity)
                }
            } }
            ForEach(frame.rows.indices, id: \.self) { r in
                let row = frame.rows[r]
                if let title = row.title, row.split == nil {
                    HStack {
                        Text(title).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted)
                        Spacer(minLength: 6)
                        if let note = row.note { Text(note).font(AppFont.sans(13)).foregroundStyle(palette.muted) }
                    }
                    .padding(.top, 4)
                }
                if let split = row.split {
                    HStack(spacing: 6) {
                        ForEach(row.cells.indices, id: \.self) { c in
                            if c == split && c > 0 { Color.clear.frame(width: row.divider ? 7.5 : 8, height: 1) }
                            Color.clear.frame(maxWidth: .infinity).frame(height: 14).overlay(alignment: .leading) {
                                if let t = c == 0 ? row.title : c == split ? row.splitTitle : nil {
                                    Text(t).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).fixedSize()
                                }
                            }
                        }
                    }
                    .padding(.top, 4)
                    HStack(spacing: 6) {
                        ForEach(row.cells.indices, id: \.self) { c in
                            if c == split && c > 0 {
                                if row.divider { Rectangle().fill(palette.muted.opacity(0.4)).frame(width: 1.5, height: 30).padding(.horizontal, 3) }
                                else { Color.clear.frame(width: 8, height: 1) }
                            }
                            TCellView(cell: row.cells[c], height: 44, font: 15)
                        }
                    }
                } else if row.spread {
                    let many = row.compact
                    HStack(spacing: many ? 4 : 6) {
                        ForEach(row.cells.indices, id: \.self) { c in
                            if c > 0, let j = row.joiner { Text(j).font(AppFont.mono(13)).foregroundStyle(palette.muted).frame(width: 12) }
                            TCellView(cell: row.cells[c], height: many ? 32 : row.cells[c].top == nil ? 44 : 50, font: many ? 12 : 15)
                        }
                    }
                    if !row.tags.isEmpty {
                        HStack(spacing: many ? 4 : 6) {
                            ForEach(row.cells.indices, id: \.self) { c in
                                let tag = row.tags[c]
                                Text(tag?.0 ?? " ").font(AppFont.mono(11, .bold)).foregroundStyle(tag.map { tTagInk($0.1, palette) } ?? .clear)
                                    .lineLimit(1).fixedSize().frame(maxWidth: .infinity)
                            }
                        }
                        .padding(.top, -2)
                    }
                } else {
                    HStack(spacing: gap) {
                        if hasLabels {
                            Text(row.label).font(AppFont.mono(13, row.labelTone == nil ? .regular : .bold)).foregroundStyle(row.labelTone.map { tTagInk($0, palette) } ?? palette.muted)
                                .lineLimit(1).minimumScaleFactor(0.7)
                                .frame(width: gutter, alignment: .leading)
                        }
                        ForEach(0..<frame.columns, id: \.self) { c in
                            let k = c - row.offset
                            if k >= 0 && k < row.cells.count { TCellView(cell: row.cells[k], height: height, font: font) }
                            else { Color.clear.frame(maxWidth: .infinity).frame(height: height) }
                        }
                    }
                    if !row.tags.isEmpty {
                        HStack(spacing: gap) {
                            if hasLabels { Color.clear.frame(width: gutter, height: 1) }
                            ForEach(0..<frame.columns, id: \.self) { c in
                                let tag = row.tags[c]
                                Text(tag?.0 ?? " ").font(AppFont.mono(11, .bold)).foregroundStyle(tag.map { tTagInk($0.1, palette) } ?? .clear)
                                    .lineLimit(1).fixedSize().frame(maxWidth: .infinity)
                            }
                        }
                        .padding(.top, -2)
                    }
                }
            }
        }
    }
}

private struct TCellView: View {
    let cell: TCell
    let height: CGFloat
    let font: CGFloat
    @Environment(\.palette) private var palette

    var body: some View {
        let (fill, ink) = tColors(cell.tone, palette)
        let shape = RoundedRectangle(cornerRadius: 7)
        VStack(spacing: 2) {
            if let top = cell.top { Text(top).font(AppFont.mono(11)).opacity(0.8) }
            Text(cell.text).font(AppFont.mono(font, .bold)).strikethrough(cell.tone == .struck, color: palette.muted.opacity(0.6))
        }
        .foregroundStyle(ink)
        .lineLimit(1).minimumScaleFactor(0.6)
        .frame(maxWidth: .infinity).frame(height: height)
        .background(fill, in: shape)
        .overlay {
            switch cell.tone {
            case .mismatch: shape.strokeBorder(SimColors.red, lineWidth: 1.5)
            case .mirror, .ghostMatched: shape.strokeBorder(SimColors.green, lineWidth: 1.5)
            case .ghost: shape.strokeBorder(palette.muted.opacity(0.35), lineWidth: 1.2)
            case .outline, .frontier: shape.strokeBorder(SimColors.blue, lineWidth: 1.5)
            default: EmptyView()
            }
        }
    }
}

private struct TListView: View {
    let rows: [TListRow]
    var title: String? = nil
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            if let title { Text(title).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).padding(.bottom, 1) }
            ForEach(rows.indices, id: \.self) { i in
                let row = rows[i]
                let (fill, ink): (Color, Color) = switch row.tone {
                case .active: (SimColors.active, Color(hex: 0x1F1A0A))
                case .matched: (SimColors.green.opacity(palette.dark ? 0.22 : 0.16), StoryTone.done.ink(palette))
                case .outline: (SimColors.active.opacity(0.1), StoryTone.active.ink(palette))
                default: (palette.muted.opacity(0.06), palette.muted.opacity(0.55))
                }
                HStack(spacing: 10) {
                    Text(row.name).font(AppFont.mono(14, .bold)).frame(width: row.value.isEmpty ? 16 : 30, alignment: .leading)
                    Text(row.range).font(AppFont.mono(14)).layoutPriority(row.value.isEmpty ? 1 : 0)
                    Spacer(minLength: 6)
                    Text(row.note).font(AppFont.mono(13)).opacity(0.85).layoutPriority(row.value.isEmpty ? 2 : 0)
                    if !row.value.isEmpty { Text(row.value).font(AppFont.mono(14, .bold)).frame(minWidth: 26, alignment: .trailing) }
                }
                .foregroundStyle(ink)
                .lineLimit(1)
                .padding(.horizontal, 14)
                .frame(height: 36)
                .background(fill, in: RoundedRectangle(cornerRadius: 8))
                .overlay { if row.tone == .outline { RoundedRectangle(cornerRadius: 8).strokeBorder(SimColors.active, lineWidth: 1.5) } }
            }
        }
    }
}

/// Samples in the unit square, blue inside the quarter circle and grey outside it, with the arc dashed.
private struct TScatterView: View {
    let points: [(Double, Double)]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let side = min(size.width - 60, size.height - 36)
            let origin = CGPoint(x: (size.width - side) / 2, y: (size.height + side) / 2)
            func at(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: origin.x + x * side, y: origin.y - y * side) }
            ctx.stroke(Path(CGRect(x: origin.x, y: origin.y - side, width: side, height: side)), with: .color(palette.muted.opacity(0.35)), lineWidth: 1)
            let dot: CGFloat = points.count > 1500 ? 1.6 : points.count > 400 ? 2.4 : 3.4
            for (x, y) in points {
                let c = at(x, y)
                let inside = x * x + y * y <= 1
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - dot, y: c.y - dot, width: 2 * dot, height: 2 * dot)),
                         with: .color(inside ? SimColors.blue : palette.muted.opacity(0.55)))
            }
            var arc = Path()
            arc.addArc(center: origin, radius: side, startAngle: .degrees(-90), endAngle: .degrees(0), clockwise: false)
            ctx.stroke(arc, with: .color(SimColors.answer), style: StrokeStyle(lineWidth: 2, dash: [6, 5]))
            for (t, p) in [("0", CGPoint(x: origin.x - 10, y: origin.y - 4)), ("1", CGPoint(x: origin.x - 10, y: origin.y - side + 4)),
                           ("1", CGPoint(x: origin.x + side, y: origin.y + 11))] {
                ctx.draw(ctx.resolve(Text(t).font(AppFont.mono(11)).foregroundColor(palette.muted)), at: p)
            }
        }
    }
}

// MARK: - Array technique storyboards
// Two pointers, sliding window, prefix sums, Kadane, Monte Carlo, reservoir sampling, Mo's algorithm and
// the difference array, on the same rows-of-cells renderer.

private func twoPointerTabs() -> [TTab] {
    let a = [1, 3, 4, 6, 8, 10, 13], target = 18, n = a.count
    var lo = 0, hi = n - 1
    var tried: [(String, String, TTone)] = []
    var trace: [(Int, Int, Int)] = []
    while lo < hi {
        let sum = a[lo] + a[hi]
        trace.append((lo, hi, sum))
        if sum == target { break }
        if sum < target { lo += 1 } else { hi -= 1 }
    }
    let found = trace.last!
    func row(_ lo: Int, _ hi: Int, done: Bool = false) -> TRow {
        TRow(cells: a.enumerated().map { i, v in
            if i == lo || i == hi { return TCell("\(v)", done ? .answer : .active) }
            return TCell("\(v)", i < lo || i > hi ? .struck : .idle)
        }, tags: [lo: ("lo", .active), hi: ("hi", .active)])
    }
    func tiles() -> TRow { TRow(title: "PAIRS TRIED · TARGET \(target)", cells: tried.map { TCell($0.1, $0.2, top: $0.0) }, spread: true) }
    var frames = [TFrame(columns: n, rows: [row(0, n - 1)], formula: ["sorted, so a bigger left or a smaller right moves the sum"],
                         chips: [StoryChip("lo", "0"), StoryChip("hi", "\(n - 1)")],
                         headline: "Find two values that add to \(target), starting from both ends of the sorted array.",
                         body: "Sorting is what lets one comparison decide which pointer to move.")]
    for (k, (l, h, sum)) in trace.enumerated() {
        tried = tried.map { ($0.0, $0.1, .matched) }
        let cmp = sum < target ? "<" : sum > target ? ">" : "="
        tried.append(("\(a[l])+\(a[h])", "\(sum) \(cmp)", sum == target ? .answer : .active))
        let hit = sum == target
        let formula = hit ? "\(a[l]) + \(a[h]) = {v:\(sum)} = \(target) → found" : "\(a[l]) + \(a[h]) = {\(sum)} \(cmp) \(target) → \(sum < target ? "lo moves right" : "hi moves left")"
        let headline = hit ? "\(a[l]) + \(a[h]) = {v:\(sum)}: the pair is found." :
            sum < target ? "{\(sum)} is under \(target). Only a bigger left value helps, so lo moves right." : "{\(sum)} is over \(target). Only a smaller right value helps, so hi moves left."
        let left = trace.count - 1 - k
        let body = hit ? "\(trace.count) sums checked instead of all \(n * (n - 1) / 2) pairs: O(n) after sorting."
            : "Each step rules out one element for good, so the scan is O(n). The pair \(a[found.0]) + \(a[found.1]) is found \(left) step\(left == 1 ? "" : "s") later."
        frames.append(TFrame(columns: n, lit: [l: .active, h: .active], rows: [row(l, h, done: hit), tiles()], formula: [formula],
                             chips: [StoryChip("lo", "\(l)"), StoryChip("hi", "\(h)")] + (hit ? [StoryChip("pair", "\(a[l]) + \(a[h])", .answer)] : []),
                             headline: headline, body: body))
    }
    return [TTab(label: "Pair sum", frames: frames, legend: [(SimColors.active, .fill, "lo and hi"), (SimColors.green, .fill, "Tried"), (Color.gray.opacity(0.4), .fill, "Ruled out"), (SimColors.answer, .fill, "Found")])]
}

private func slidingWindowTabs() -> [TTab] {
    let a = [2, 1, 5, 1, 3, 2, 7, 1], n = a.count, k = 3
    let windows = n - k + 1
    var sums: [Int] = []
    var fixed: [TFrame] = []
    var best = Int.min
    func tiles(_ current: Int?) -> TRow {
        TRow(title: "WINDOW SUMS", cells: (0..<windows).map { s in
            guard let current, s <= current else { return TCell("·", .pending, top: "[\(s),\(s + k - 1)]") }
            return TCell("\(sums[s])", s == current ? .active : .matched, top: "[\(s),\(s + k - 1)]")
        }, spread: true)
    }
    fixed.append(TFrame(columns: n, rows: [TRow(cells: a.map { TCell("\($0)", .idle) }), tiles(nil)], formula: ["window size k = \(k)"],
                        chips: [StoryChip("k", "\(k)")], headline: "Find the largest sum of \(k) consecutive values.",
                        body: "Recomputing each window from scratch costs k per window. Sliding reuses the last sum."))
    for s in 0..<windows {
        let sum = s == 0 ? a[0..<k].reduce(0, +) : sums[s - 1] - a[s - 1] + a[s + k - 1]
        sums.append(sum)
        let newBest = sum > best
        if newBest { best = sum }
        var tags: [Int: (String, TTone)] = [:]
        if s > 0 { tags[s - 1] = ("out", .mismatch); tags[s + k - 1] = ("in", .active); tags[s] = ("start", .blue) }
        let row = TRow(cells: a.enumerated().map { i, v in
            if s > 0 && i == s - 1 { return TCell("\(v)", .mismatch) }
            if s > 0 && i == s + k - 1 { return TCell("\(v)", .active) }
            return TCell("\(v)", i >= s && i < s + k ? .blue : .idle)
        }, tags: tags)
        var lit: [Int: TTone] = [:]
        if s > 0 { lit[s - 1] = .mismatch; lit[s + k - 1] = .active }
        let mark = newBest ? "{v:\(sum)}" : "{\(sum)}"
        let formula = s == 0 ? a[0..<k].map(String.init).joined(separator: " + ") + " = \(mark)"
            : "\(sums[s - 1]) − {w:a[\(s - 1)]} + {a[\(s + k - 1)]} = \(sums[s - 1]) − \(a[s - 1]) + \(a[s + k - 1]) = \(mark)"
        let headline: String
        if s == 0 { headline = "The first window [0, \(k - 1)] sums to {\(sum)}." }
        else if newBest { headline = "Drop \(a[s - 1]), add \(a[s + k - 1]): the window sum jumps to {v:\(sum)}, a new best." }
        else { headline = "Drop \(a[s - 1]), add \(a[s + k - 1]): \(sum), and the best stays {v:\(best)}." }
        fixed.append(TFrame(columns: n, lit: lit, rows: [row, tiles(s)], formula: [formula],
                            chips: [StoryChip("sum", "\(sum)"), StoryChip("best", "\(best)", .answer)], headline: headline,
                            body: s == 0 ? "Build the first window once; every later one reuses it." : "Each slide changes two elements, so all windows cost O(n), not O(n·k)."))
    }
    // Variable size: shortest window with sum ≥ target.
    let target = 8
    var variable = [TFrame(columns: n, rows: [TRow(cells: a.map { TCell("\($0)", .idle) })], formula: ["shortest window with sum ≥ \(target)"],
                           chips: [StoryChip("target", "\(target)")], headline: "Now the window grows and shrinks: find the shortest one summing to at least \(target).",
                           body: "R grows the window until it is big enough; L then shrinks it while it still is.")]
    var l = 0, sum = 0, shortest = Int.max, shortRange = (0, 0)
    for r in 0..<n {
        sum += a[r]
        func row(_ entering: Int?, _ leaving: Int?) -> TRow {
            var tags: [Int: (String, TTone)] = [:]
            tags[l] = ("L", .blue)
            tags[r] = ("R", .blue)
            if let entering { tags[entering] = ("in", .active) }
            if let leaving { tags[leaving] = ("out", .mismatch) }
            return TRow(cells: a.enumerated().map { i, v in
                if i == entering { return TCell("\(v)", .active) }
                if i == leaving { return TCell("\(v)", .mismatch) }
                return TCell("\(v)", i >= l && i <= r ? .blue : .idle)
            }, tags: tags)
        }
        variable.append(TFrame(columns: n, lit: [r: .active], rows: [row(r, nil)], formula: ["sum + a[\(r)] = \(sum - a[r]) + \(a[r]) = {\(sum)}"],
                               chips: [StoryChip("sum", "\(sum)"), StoryChip("shortest", shortest == .max ? "–" : "\(shortest)", .answer)],
                               headline: sum >= target ? "Adding \(a[r]) brings the sum to {\(sum)}, enough." : "Adding \(a[r]) brings the sum to {\(sum)}, still under \(target).",
                               body: "R only moves right, so each element enters the window once."))
        while sum >= target {
            let len = r - l + 1
            let better = len < shortest
            if better { shortest = len; shortRange = (l, r) }
            let gone = a[l]
            sum -= gone
            variable.append(TFrame(columns: n, lit: [l: .mismatch], rows: [row(nil, l)],
                                   formula: ["[\(l), \(r)] has length \(better ? "{v:\(len)}" : "\(len)"); drop {w:a[\(l)]} → \(sum)"],
                                   chips: [StoryChip("sum", "\(sum)"), StoryChip("shortest", "\(shortest)", .answer)],
                                   headline: better ? "[\(l), \(r)] reaches \(target) in {v:\(len)} values, the shortest yet. Try dropping \(gone)." : "[\(l), \(r)] still works but is not shorter. Drop \(gone).",
                                   body: "L also only moves right, so the whole scan is O(n) even though the window changes size."))
            l += 1
        }
    }
    variable.append(TFrame(columns: n, rows: [TRow(cells: a.enumerated().map { TCell("\($1)", $0 >= shortRange.0 && $0 <= shortRange.1 ? .answer : .idle) })],
                           formula: ["shortest = [\(shortRange.0), \(shortRange.1)], length {v:\(shortest)}"], chips: [StoryChip("shortest", "\(shortest)", .answer)],
                           headline: "The shortest window with sum ≥ \(target) is [\(shortRange.0), \(shortRange.1)], length {v:\(shortest)}.",
                           body: "Two pointers that never move back: O(n) for a question that looks like it needs every subarray."))
    return [TTab(label: "Fixed size", frames: fixed, legend: [(SimColors.blue, .fill, "In window"), (SimColors.active, .fill, "Entering"), (SimColors.red, .fill, "Leaving"), (SimColors.answer, .fill, "New best")]),
            TTab(label: "Variable size", frames: variable, legend: [(SimColors.blue, .fill, "In window"), (SimColors.active, .fill, "Entering"), (SimColors.red, .fill, "Leaving"), (SimColors.answer, .fill, "Shortest")])]
}

private func prefixSumTabs() -> [TTab] {
    let a = [3, 1, 4, 1, 5, 9, 2, 6], n = a.count
    var p: [Int] = []
    for v in a { p.append((p.last ?? 0) + v) }
    var build: [TFrame] = []
    for i in 0..<n {
        let aRow = TRow(label: "a", cells: a.enumerated().map { TCell("\($1)", $0 == i ? .active : .idle) })
        let pRow = TRow(label: "P", cells: (0..<n).map { k in k > i ? TCell("·", .pending) : TCell("\(p[k])", k == i ? .active : k == i - 1 ? .blue : .idle) })
        build.append(TFrame(columns: n, lit: [i: .active], rows: [aRow, pRow],
                            formula: [i == 0 ? "P[0] = a[0] = {\(p[0])}" : "P[\(i)] = P[\(i - 1)] + a[\(i)] = {p:\(p[i - 1])} + \(a[i]) = {\(p[i])}"],
                            chips: [StoryChip("i", "\(i)"), StoryChip("P[\(i)]", "\(p[i])")],
                            headline: i == 0 ? "P[0] is just a[0] = {\(p[0])}." : "P[\(i)] adds a[\(i)] to the running total: {\(p[i])}.",
                            body: "P[i] is the sum of a[0] through a[i], built in one pass of n additions."))
    }
    build.append(TFrame(columns: n, rows: [TRow(label: "a", cells: a.map { TCell("\($0)", .idle) }), TRow(label: "P", cells: p.map { TCell("\($0)", .answer) })],
                        formula: ["P = {v:" + p.map(String.init).joined(separator: " ") + "}"], chips: [StoryChip("cost", "O(n)", .answer)],
                        headline: "P is built: {v:\(n)} additions, once.", body: "Every range sum is now two lookups and a subtraction."))
    let queries = [(2, 5), (0, 3), (4, 7)]
    var query = [TFrame(columns: n, rows: [TRow(label: "a", cells: a.map { TCell("\($0)", .idle) }), TRow(label: "P", cells: p.map { TCell("\($0)", .idle) })],
                        formula: ["sum(L..R) = P[R] − P[L−1]"], chips: [StoryChip("queries", "\(queries.count)")],
                        headline: "A range sum is the total up to R minus the total before L.", body: "When L is 0 there is nothing to subtract.")]
    for (l, r) in queries {
        let v = p[r] - (l > 0 ? p[l - 1] : 0)
        var tags: [Int: (String, TTone)] = [r: ("R", .active)]
        if l > 0 { tags[l - 1] = ("L−1", .active) }
        let aRow = TRow(label: "a", cells: a.enumerated().map { TCell("\($1)", $0 >= l && $0 <= r ? .blue : .idle) })
        let pRow = TRow(label: "P", cells: p.enumerated().map { TCell("\($1)", $0 == r || (l > 0 && $0 == l - 1) ? .active : .idle) }, tags: tags)
        let formula = l > 0 ? "P[\(r)] − P[\(l - 1)] = {\(p[r])} − {\(p[l - 1])} = {v:\(v)}" : "P[\(r)] = {v:\(v)} (L = 0)"
        query.append(TFrame(columns: n, rows: [aRow, pRow], formula: [formula],
                            formulaRows: [StoryFormulaRow(label: "check", formula: a[l...r].map(String.init).joined(separator: " + ") + " = \(v)")],
                            chips: [StoryChip("L", "\(l)"), StoryChip("R", "\(r)"), StoryChip("sum", "\(v)", .answer)],
                            headline: l > 0 ? "sum(\(l)..\(r)) = P[\(r)] − P[\(l - 1)] = {v:\(v)}." : "sum(0..\(r)) is just P[\(r)] = {v:\(v)}.",
                            body: "P is built in one pass. After that, every range sum is a single subtraction."))
    }
    return [TTab(label: "Build", frames: build, legend: [(SimColors.active, .fill, "Current"), (SimColors.blue, .fill, "Read"), (SimColors.answer, .fill, "Built")]),
            TTab(label: "Query", frames: query, legend: [(SimColors.blue, .fill, "Query range"), (SimColors.active, .fill, "P values read"), (SimColors.answer, .fill, "Answer")])]
}

private func kadaneTabs() -> [TTab] {
    let a = [-2, 1, -3, 4, -1, 2, 1, -5, 4], n = a.count
    func fmt(_ v: Int) -> String { v < 0 ? "−\(-v)" : "\(v)" }
    var cur: [Int] = []
    var start = 0, best = Int.min, bestRange = (0, 0)
    var frames: [TFrame] = []
    // The answer, known up front so every step's body can point at it.
    var run = 0, top = Int.min, topRange = (0, 0), from = 0
    for (i, v) in a.enumerated() {
        if i == 0 || v > run + v { run = v; from = i } else { run += v }
        if run > top { top = run; topRange = (from, i) }
    }
    for i in 0..<n {
        let prev = cur.last
        let extend = prev.map { $0 + a[i] } ?? a[i]
        let restart = prev == nil || a[i] > extend
        if restart { start = i }
        let c = restart ? a[i] : extend
        cur.append(c)
        if c > best { best = c; bestRange = (start, i) }
        let aRow = TRow(label: "a", cells: a.enumerated().map { k, v in
            if k == i { return TCell(fmt(v), .active) }
            return TCell(fmt(v), k < start ? .struck : .idle)
        })
        let curRow = TRow(label: "cur", cells: (0..<n).map { k in k > i ? TCell("·", .pending) : TCell(fmt(cur[k]), k == i ? .active : .idle) }, tags: [i: ("i", .active)])
        let formula = prev.map { "cur = max(\(fmt(a[i])), \($0 < 0 ? "{w:\(fmt($0))}" : fmt($0)) + \(fmt(a[i]))) = {\(fmt(c))} → \(restart ? "restart" : "extend")" } ?? "cur = a[0] = {\(fmt(c))}"
        let headline: String
        if prev == nil { headline = "The first run is just a[0] = {\(fmt(c))}." }
        else if restart { headline = "Carrying \(fmt(prev!)) would only lower the sum, so the subarray restarts at i = \(i)." }
        else if c == best && bestRange.1 == i { headline = "Adding \(fmt(a[i])) keeps the run going: cur = {\(fmt(c))}, a new best." }
        else { headline = "Adding \(fmt(a[i])) still beats starting over, so the run extends: cur = {\(fmt(c))}." }
        frames.append(TFrame(columns: n, lit: [i: .active], rows: [aRow, curRow], formula: [formula],
                             chips: [StoryChip("cur", fmt(c)), StoryChip("best", fmt(best))], headline: headline,
                             body: "cur is the best sum ending exactly at i. The final best is \(fmt(top)), from [\(topRange.0), \(topRange.1)]."))
    }
    frames.append(TFrame(columns: n, rows: [TRow(label: "a", cells: a.enumerated().map { TCell(fmt($1), $0 >= bestRange.0 && $0 <= bestRange.1 ? .answer : .idle) }),
                                            TRow(label: "cur", cells: cur.map { TCell(fmt($0), .idle) })],
                         formula: ["best = {v:\(fmt(best))}, from [\(bestRange.0), \(bestRange.1)]"], chips: [StoryChip("best", fmt(best), .answer)],
                         headline: "The maximum subarray is [\(bestRange.0), \(bestRange.1)], summing to {v:\(fmt(best))}.",
                         body: "One pass, two variables: O(n) time and O(1) space."))
    return [TTab(label: "Kadane", frames: frames, legend: [(SimColors.active, .fill, "Current"), (Color.gray.opacity(0.4), .fill, "Dropped prefix"), (SimColors.answer, .fill, "Best")])]
}

private func monteCarloTabs() -> [TTab] {
    // A seed whose 300-sample estimate lands on the classic 237 inside; it converges to 3.14 by 4000.
    var seed: Int64 = 509
    func rand() -> Double { seed = (seed * 1103515245 + 12345) % 2147483648; return Double(seed) / 2147483648 }
    let counts = [25, 50, 100, 300, 600, 1000, 2000, 4000]
    var pts: [(Double, Double)] = []
    var frames: [TFrame] = []
    for n in counts {
        while pts.count < n { pts.append((rand(), rand())) }
        let inside = pts.filter { $0.0 * $0.0 + $0.1 * $0.1 <= 1 }.count
        let est = 4 * Double(inside) / Double(n)
        let err = abs(est - Double.pi)
        frames.append(TFrame(columns: 0, rows: [], formula: ["4 × {p:\(inside)} / \(n) = {v:\(String(format: "%.4f", est))}"],
                             chips: [StoryChip("π ≈", String(format: "%.3f", est), .answer), StoryChip("error", String(format: "%.3f", err))],
                             headline: "\(inside) of \(n) samples land inside, so π ≈ {v:\(String(format: "%.3f", est))}.",
                             body: "The inside fraction estimates π/4. Error shrinks like 1/√n, so 4× the samples roughly halves it.", scatter: pts))
    }
    return [TTab(label: "π", frames: frames, legend: [(SimColors.blue, .fill, "Inside the arc"), (Color.gray.opacity(0.5), .fill, "Outside"), (SimColors.answer, .dashed, "Quarter circle")],
                 stepper: ("Samples", counts))]
}

private func reservoirTabs() -> [TTab] {
    let stream = [41, 17, 63, 8, 92, 25, 54, 39, 71, 6, 88, 30], k = 3, n = stream.count
    // The draws of one seeded run, j = rand(0...i) for i ≥ k.
    let draws = [3: 3, 4: 1, 5: 5, 6: 0, 7: 6, 8: 2, 9: 7, 10: 9, 11: 1]
    var slots: [Int] = []
    var evicted = Set<Int>(), skipped = Set<Int>()
    var frames = [TFrame(columns: n, rows: [TRow(cells: stream.map { TCell("\($0)", .idle) }), TRow(title: "RESERVOIR", cells: (0..<k).map { _ in TCell("·", .pending) }, spread: true)],
                         formula: ["keep a uniform sample of k = \(k) from a stream of unknown length"], chips: [StoryChip("seen", "0"), StoryChip("k", "\(k)")],
                         headline: "Keep \(k) items from a stream, each equally likely, without knowing how long it is.",
                         body: "Only the reservoir is stored; each item is looked at once and then forgotten.")]
    func streamRow(_ i: Int) -> TRow {
        TRow(cells: stream.enumerated().map { idx, v in
            if idx == i { return TCell("\(v)", .active) }
            if idx > i { return TCell("\(v)", .idle) }
            if evicted.contains(idx) { return TCell("\(v)", .mismatch) }
            if skipped.contains(idx) { return TCell("\(v)", .pending) }
            return TCell("\(v)", .answer)
        }, tags: [i: ("i", .active)])
    }
    var slotOwner: [Int] = []
    for i in 0..<n {
        if i < k {
            slots.append(stream[i]); slotOwner.append(i)
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [streamRow(i), TRow(title: "RESERVOIR", cells: (0..<k).map { $0 < slots.count ? TCell("\(slots[$0])", $0 == i ? .active : .answer) : TCell("·", .pending) }, spread: true)],
                                 formula: ["i = \(i) < k → slot \(i) = {\(stream[i])}"], chips: [StoryChip("seen", "\(i + 1)"), StoryChip("k", "\(k)")],
                                 headline: "The first \(k) items fill the reservoir directly: slot \(i) = {\(stream[i])}.",
                                 body: "Until the reservoir is full there is nothing to choose."))
            continue
        }
        let j = draws[i]!
        let kept = j < k
        var tiles = slots.map { TCell("\($0)", .answer) }
        if kept {
            let old = slots[j]
            evicted.insert(slotOwner[j])
            tiles[j] = TCell("\(old) → \(stream[i])", .active)
            slots[j] = stream[i]; slotOwner[j] = i
        } else {
            skipped.insert(i)
        }
        frames.append(TFrame(columns: n, lit: [i: .active], rows: [streamRow(i), TRow(title: "RESERVOIR", cells: tiles, spread: true)],
                             formula: [kept ? "j = rand(0…\(i)) = {\(j)} < \(k) → slot \(j) = \(stream[i])" : "j = rand(0…\(i)) = {\(j)} ≥ \(k) → skip"],
                             formulaRows: [StoryFormulaRow(label: "P(item \(i) kept)", formula: "\(k) / \(i + 1)")],
                             chips: [StoryChip("seen", "\(i + 1)"), StoryChip("k", "\(k)")],
                             headline: kept ? "Item \(stream[i]) draws j = \(j), so it evicts \(tiles[j].text.components(separatedBy: " ").first!) from slot \(j)." : "Item \(stream[i]) draws j = \(j), not below \(k), so it is skipped.",
                             body: "Item i is kept with probability k/(i+1). That keeps every item seen so far equally likely."))
    }
    return [TTab(label: "Algorithm R", frames: frames, legend: [(SimColors.active, .fill, "Current item"), (SimColors.answer, .fill, "In reservoir"), (SimColors.red, .fill, "Evicted"), (Color.gray.opacity(0.4), .fill, "Skipped")])]
}

private func mosTabs() -> [TTab] {
    let a = [4, 1, 7, 2, 9, 3, 6, 5, 8, 2, 4, 1], n = a.count, block = 4
    let queries = [(1, 3), (0, 5), (4, 7), (5, 10), (8, 11)]
    let order = queries.indices.sorted { (queries[$0].0 / block, queries[$0].1) < (queries[$1].0 / block, queries[$1].1) }
    var answers = [Int?](repeating: nil, count: queries.count)
    var l = queries[order[0]].0, r = queries[order[0]].0 - 1, sum = 0, moves = 0
    var frames: [TFrame] = []
    func list(_ current: Int?) -> [TListRow] {
        order.map { qi in
            let (ql, qr) = queries[qi]
            let tone: TTone = qi == current ? .active : answers[qi] != nil ? .matched : .pending
            return TListRow(name: "Q\(qi + 1)", range: "[\(ql), \(qr)]", note: "block \(ql / block)", value: answers[qi].map(String.init) ?? "·", tone: tone)
        }
    }
    func row(_ moved: Int?) -> TRow {
        var tags: [Int: (String, TTone)] = [:]
        if r >= l { tags[l] = ("L", .blue); tags[r] = ("R", tags[r] == nil ? .active : .blue) }
        return TRow(cells: a.enumerated().map { i, v in TCell("\(v)", i == moved ? .active : i >= l && i <= r ? .blue : .idle) }, tags: tags)
    }
    frames.append(TFrame(columns: n, rows: [row(nil)], formula: ["sort queries by (block of L, R), block size \(block)"], chips: [StoryChip("sum", "0"), StoryChip("moves", "0")],
                         headline: "Answer \(queries.count) range-sum queries offline by reordering them.",
                         body: "Queries are sorted by block of L, then by R, so the window moves a little each time and is never rebuilt.", list: list(nil)))
    for (pos, qi) in order.enumerated() {
        let (ql, qr) = queries[qi]
        var count = 0
        // The second query is shown one pointer move at a time, so the cost of a move is visible.
        func moved(_ idx: Int, adds: Bool, _ what: String) {
            count += 1
            moves += 1
            guard pos == 1 else { return }
            let last = l == ql && r == qr
            if last { answers[qi] = sum }
            let before = adds ? sum - a[idx] : sum + a[idx]
            frames.append(TFrame(columns: n, lit: [idx: .active], rows: [row(idx)],
                                 formula: [adds ? "sum += a[\(idx)] → \(before) + \(a[idx]) = {\(sum)}" : "sum −= a[\(idx)] → \(before) − \(a[idx]) = {\(sum)}"],
                                 chips: [StoryChip("sum", "\(sum)"), StoryChip("moves", "\(moves)")],
                                 headline: what + (last ? " Q\(qi + 1)'s sum is {\(sum)}." : " Running sum {\(sum)}."),
                                 body: "Queries are sorted by block of L, then by R, so the window moves a little each time and is never rebuilt.",
                                 list: list(qi)))
        }
        while l > ql { l -= 1; sum += a[l]; moved(l, adds: true, "L steps to \(l) and adds \(a[l]).") }
        while r < qr { r += 1; sum += a[r]; moved(r, adds: true, "R steps to \(r) and adds \(a[r]).") }
        while r > qr { sum -= a[r]; r -= 1; moved(r + 1, adds: false, "R drops \(a[r + 1]).") }
        while l < ql { sum -= a[l]; l += 1; moved(l - 1, adds: false, "L drops \(a[l - 1]).") }
        answers[qi] = sum
        if pos == 1 { continue }
        frames.append(TFrame(columns: n, rows: [row(nil)], formula: ["\(count) pointer moves → sum = {\(sum)}"],
                             chips: [StoryChip("sum", "\(sum)"), StoryChip("moves", "\(moves)")],
                             headline: pos == 0 ? "Q\(qi + 1) builds the first window [\(ql), \(qr)] in \(count) moves: {\(sum)}." : "Q\(qi + 1) needs \(count) pointer moves from the last window: {\(sum)}.",
                             body: "Queries are sorted by block of L, then by R, so the window moves a little each time and is never rebuilt.", list: list(qi)))
    }
    frames.append(TFrame(columns: n, rows: [row(nil)], formula: ["\(queries.count) queries, {v:\(moves)} pointer moves"],
                         chips: [StoryChip("moves", "\(moves)", .answer)],
                         headline: "All \(queries.count) queries answered with {v:\(moves)} pointer moves in total.",
                         body: "Answering them in the given order would drag the window back and forth. The sort makes the total O((n + q)·√n).", list: list(nil)))
    return [TTab(label: "Queries", frames: frames, legend: [(SimColors.blue, .fill, "Window"), (SimColors.active, .fill, "Pointer move"), (SimColors.green, .fill, "Answered")])]
}

private func differenceTabs() -> [TTab] {
    let n = 8
    let updates = [(1, 4, 3), (3, 6, 2), (0, 2, -1)]
    func fmt(_ v: Int) -> String { v < 0 ? "−\(-v)" : "\(v)" }
    func sfmt(_ v: Int) -> String { v < 0 ? "−\(-v)" : "+\(v)" }
    var d = [Int](repeating: 0, count: n)
    var touched = Set<Int>()
    func list(_ current: Int?) -> [TListRow] {
        updates.enumerated().map { i, u in
            TListRow(name: "U\(i + 1)", range: "[\(u.0), \(u.1)]", note: "", value: sfmt(u.2), tone: current.map { i < $0 ? .matched : i == $0 ? .active : .pending } ?? .matched)
        }
    }
    func dRow(_ now: Set<Int>) -> TRow {
        TRow(label: "D", cells: d.enumerated().map { i, v in TCell(fmt(v), now.contains(i) ? .active : touched.contains(i) && v != 0 ? .matched : .idle) })
    }
    let pendingA = TRow(label: "a", cells: (0..<n).map { _ in TCell("·", .pending) })
    var frames = [TFrame(columns: n, rows: [dRow([]), pendingA], formula: ["add v to a[l..r]: D[l] += v, D[r + 1] −= v"],
                         chips: [StoryChip("updates", "\(updates.count)")],
                         headline: "Record each range update at its two ends instead of in every cell.",
                         body: "D holds the changes; a is only rebuilt once, at the end.", list: list(0), listFirst: true)]
    for (i, u) in updates.enumerated() {
        d[u.0] += u.2
        var now: Set<Int> = [u.0]
        var formula = "D[\(u.0)] += \(fmt(u.2))"
        if u.1 + 1 < n { d[u.1 + 1] -= u.2; now.insert(u.1 + 1); formula += ", D[\(u.1) + 1] −= \(fmt(u.2))" }
        let span = u.1 - u.0 + 1
        let lit = Dictionary(uniqueKeysWithValues: now.map { ($0, TTone.active) })
        frames.append(TFrame(columns: n, lit: lit, rows: [dRow(now), pendingA], formula: [formula],
                             chips: [StoryChip("cells touched", "\(now.count)"), StoryChip("naive", "\(span)")],
                             headline: "Update \(i + 1) writes only " + now.sorted().map { "D[\($0)]" }.joined(separator: " and ") + ", not the \(span) cells in between.",
                             body: "Once all updates are in, one prefix-sum pass over D produces a.", list: list(i), listFirst: true))
        touched.formUnion(now)
    }
    var run = 0
    let built = d.map { run += $0; return run }
    frames.append(TFrame(columns: n, rows: [dRow([]), TRow(label: "a", cells: built.map { TCell(fmt($0), .answer) })],
                         formula: ["a[i] = D[0] + … + D[i] → {v:" + built.map(fmt).joined(separator: " ") + "}"],
                         chips: [StoryChip("writes", "\(updates.count * 2)"), StoryChip("pass", "O(n)", .answer)],
                         headline: "One prefix-sum pass over D gives {v:a}.",
                         body: "q range updates cost O(q + n) instead of O(q · n).", list: list(nil), listFirst: true))
    return [TTab(label: "Updates", frames: frames, legend: [(SimColors.active, .fill, "Written now"), (SimColors.green, .fill, "Earlier updates"), (Color.gray.opacity(0.4), .fill, "Not built yet"), (SimColors.answer, .fill, "Built")])]
}

private func tStroke(_ tone: TTone, _ palette: Palette) -> Color {
    switch tone {
    case .active, .stack: SimColors.active
    case .blue, .outline: SimColors.blue
    case .matched, .mirror, .green: SimColors.green
    case .mismatch: SimColors.red
    case .answer, .memo: SimColors.answer
    case .rose: Color(hex: 0xE0457B)
    default: palette.muted.opacity(0.6)
    }
}

private struct TChainView: View {
    let chain: TChain
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let n = chain.nodes.count
            let pad: CGFloat = 12
            let slot = (size.width - 2 * pad) / CGFloat(n)
            let w = min(46, slot * 0.72), h: CGFloat = 42
            let cy: CGFloat = 62
            func cx(_ i: Int) -> CGFloat { pad + slot * (CGFloat(i) + 0.5) }
            func head(_ tip: CGPoint, _ angle: CGFloat, _ c: Color) {
                var p = Path()
                p.move(to: tip)
                p.addLine(to: CGPoint(x: tip.x - 7 * cos(angle - 0.45), y: tip.y - 7 * sin(angle - 0.45)))
                p.addLine(to: CGPoint(x: tip.x - 7 * cos(angle + 0.45), y: tip.y - 7 * sin(angle + 0.45)))
                p.closeSubpath()
                ctx.fill(p, with: .color(c))
            }
            func straight(_ from: Int, _ to: Int, _ c: Color, dashed: Bool) {
                let dir: CGFloat = to > from ? 1 : -1
                let a = CGPoint(x: cx(from) + dir * (w / 2 + 3), y: cy), b = CGPoint(x: cx(to) - dir * (w / 2 + 3), y: cy)
                var p = Path(); p.move(to: a); p.addLine(to: b)
                ctx.stroke(p, with: .color(c), style: StrokeStyle(lineWidth: dashed ? 1.8 : 1.5, dash: dashed ? [4, 3] : []))
                head(b, dir > 0 ? 0 : .pi, c)
            }
            if let cut = chain.cut { straight(cut.0, cut.1, SimColors.red, dashed: true) }
            for (i, target) in chain.next.enumerated() {
                guard let j = target else { continue }
                let special = chain.special[i]
                if special == nil && abs(i - j) == 1 {
                    straight(i, j, tStroke(chain.arrowTones[i] ?? .idle, palette), dashed: false)
                    continue
                }
                let c = tStroke(special?.0 ?? .idle, palette)
                let a = CGPoint(x: cx(i), y: cy + h / 2 + 2), b = CGPoint(x: cx(j), y: cy + h / 2 + 2)
                let control = CGPoint(x: (a.x + b.x) / 2, y: cy + h / 2 + 40)
                var p = Path(); p.move(to: a); p.addQuadCurve(to: b, control: control)
                ctx.stroke(p, with: .color(c), lineWidth: 2)
                head(b, atan2(b.y - control.y, b.x - control.x), c)
                if let label = special?.1 {
                    ctx.draw(ctx.resolve(Text(label).font(AppFont.mono(11, .bold)).foregroundColor(special.map { tTagInk($0.0 == .answer ? .blue : $0.0, palette) } ?? palette.muted)),
                             at: CGPoint(x: control.x, y: (a.y + 2 * control.y + b.y) / 4 + 10))
                }
            }
            for (i, node) in chain.nodes.enumerated() {
                let rect = CGRect(x: cx(i) - w / 2, y: cy - h / 2, width: w, height: h)
                let tile = Path(roundedRect: rect, cornerRadius: 8)
                let (fill, ink) = tColors(node.tone, palette)
                ctx.fill(tile, with: .color(palette.surface))
                ctx.fill(tile, with: .color(fill))
                if node.tone == .outline { ctx.stroke(tile, with: .color(SimColors.blue), lineWidth: 1.5) }
                if node.tone == .mismatch { ctx.stroke(tile, with: .color(SimColors.red), lineWidth: 1.5) }
                ctx.draw(ctx.resolve(Text(node.text).font(AppFont.mono(16, .bold)).foregroundColor(ink)), at: CGPoint(x: rect.midX, y: rect.midY))
                if let (t, tone) = chain.above[i] {
                    ctx.draw(ctx.resolve(Text(t).font(AppFont.mono(12, .bold)).foregroundColor(tTagInk(tone, palette))), at: CGPoint(x: rect.midX, y: rect.minY - 12))
                }
                if let (t, tone) = chain.below[i] {
                    ctx.draw(ctx.resolve(Text(t).font(AppFont.mono(11, .bold)).foregroundColor(tTagInk(tone, palette))), at: CGPoint(x: rect.midX, y: rect.maxY + 12))
                }
            }
        }
    }
}

private struct TTimelineView: View {
    let timeline: TTimeline
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let pad: CGFloat = 20
            func x(_ v: Double) -> CGFloat { pad + CGFloat(v / timeline.maxX) * (size.width - 2 * pad) }
            let top: CGFloat = 14, rowH: CGFloat = 24
            func bar(_ b: TBar, _ y: CGFloat) {
                let rect = CGRect(x: x(b.start), y: y, width: max(x(b.end) - x(b.start), 4), height: 15)
                let (fill, ink): (Color, Color) = switch b.tone {
                case .idle: (palette.muted.opacity(0.3), palette.onSurface)
                case .mismatch: (SimColors.red.opacity(0.45), .white)
                case .matched: (SimColors.green, .white)
                default: (tStroke(b.tone, palette), b.tone == .active ? Color(hex: 0x1F1A0A) : .white)
                }
                ctx.fill(Path(roundedRect: rect, cornerRadius: 4), with: .color(fill))
                if let label = b.label {
                    ctx.draw(ctx.resolve(Text(label).font(AppFont.mono(10, .bold)).foregroundColor(ink)), at: CGPoint(x: rect.midX, y: rect.midY))
                }
                if let side = b.side {
                    ctx.draw(ctx.resolve(Text(side).font(AppFont.mono(10)).foregroundColor(palette.muted)), at: CGPoint(x: rect.maxX + 5, y: rect.midY), anchor: .leading)
                }
            }
            for (i, b) in timeline.bars.enumerated() { bar(b, top + CGFloat(i) * rowH) }
            var axisY = top + CGFloat(timeline.bars.count) * rowH + 4
            if !timeline.output.isEmpty {
                var rule = Path(); rule.move(to: CGPoint(x: pad, y: axisY)); rule.addLine(to: CGPoint(x: size.width - pad, y: axisY))
                ctx.stroke(rule, with: .color(palette.muted.opacity(0.3)), lineWidth: 1)
                for b in timeline.output { bar(b, axisY + 10) }
                axisY += 36
            }
            if let m = timeline.marker {
                var line = Path(); line.move(to: CGPoint(x: x(m), y: 6)); line.addLine(to: CGPoint(x: x(m), y: axisY))
                ctx.stroke(line, with: .color(tStroke(timeline.markerTone, palette).opacity(0.8)), style: StrokeStyle(lineWidth: 1.2, dash: [3, 3]))
            }
            var v = 0.0
            while v <= timeline.maxX + 0.001 {
                let lit = timeline.marker == v
                ctx.draw(ctx.resolve(Text(v == v.rounded() ? "\(Int(v))" : "\(v)").font(AppFont.mono(11, lit ? .bold : .regular))
                    .foregroundColor(lit ? (timeline.markerTone == .active ? StoryTone.active.ink(palette) : StoryTone.path.ink(palette)) : palette.muted)), at: CGPoint(x: x(v), y: axisY + 12))
                v += timeline.step
            }
        }
    }
}

private struct TBarsView: View {
    let bars: TBars
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let n = bars.values.count
            let pad: CGFloat = 8
            let slot = (size.width - 2 * pad) / CGFloat(n)
            let w = slot * 0.84
            let base = size.height - 46, top: CGFloat = 34
            let maxV = CGFloat(max(bars.values.max() ?? 1, 1))
            func cx(_ i: Int) -> CGFloat { pad + slot * (CGFloat(i) + 0.5) }
            func h(_ i: Int) -> CGFloat { max(10, (base - top) * CGFloat(bars.values[i]) / maxV) }
            for i in 0..<n {
                let rect = CGRect(x: cx(i) - w / 2, y: base - h(i), width: w, height: h(i))
                let fill: Color = switch bars.tones[i] {
                case .idle: palette.muted.opacity(0.3)
                case .pending: palette.muted.opacity(0.14)
                default: tStroke(bars.tones[i], palette)
                }
                ctx.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(fill))
                ctx.draw(ctx.resolve(Text("\(bars.values[i])").font(AppFont.mono(15, .bold)).foregroundColor(palette.onSurface)), at: CGPoint(x: cx(i), y: rect.minY - 12))
                ctx.draw(ctx.resolve(Text("\(i)").font(AppFont.mono(11)).foregroundColor(palette.muted)), at: CGPoint(x: cx(i), y: base + 14))
                if let (t, tone) = bars.tags[i] {
                    ctx.draw(ctx.resolve(Text(t).font(AppFont.mono(12, .bold)).foregroundColor(tone == .idle ? palette.muted : tStroke(tone, palette))), at: CGPoint(x: cx(i), y: base + 34))
                }
            }
            if let (a, b) = bars.arc, a != b {
                let p1 = CGPoint(x: cx(a), y: base - h(a) - 26), p2 = CGPoint(x: cx(b), y: base - h(b) - 26)
                var path = Path(); path.move(to: p1)
                path.addQuadCurve(to: p2, control: CGPoint(x: (p1.x + p2.x) / 2, y: min(p1.y, p2.y) - 14))
                ctx.stroke(path, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
            }
        }
    }
}

private func tNodeInk(_ tone: TTone, _ palette: Palette) -> Color {
    switch tone {
    case .blue: StoryTone.path.ink(palette)
    case .answer, .memo: Color(hex: 0xB9A5FF)
    case .mismatch: StoryTone.warn.ink(palette)
    case .active, .stack: StoryTone.active.ink(palette)
    case .mirror, .green, .matched: StoryTone.done.ink(palette)
    default: palette.muted
    }
}

private func tTreeLayout(_ nodes: [TNode]) -> (x: [Double], depth: [Int], leaves: Int) {
    var kids: [Int: [Int]] = [:]
    for (i, n) in nodes.enumerated() { if let p = n.parent { kids[p, default: []].append(i) } }
    var x = [Double](repeating: 0, count: nodes.count), depth = [Int](repeating: 0, count: nodes.count)
    var slot = 0
    func place(_ id: Int, _ d: Int) {
        depth[id] = d
        let cs = kids[id] ?? []
        if cs.isEmpty { x[id] = Double(slot); slot += 1; return }
        cs.forEach { place($0, d + 1) }
        x[id] = (x[cs.first!] + x[cs.last!]) / 2
    }
    place(0, 0)
    return (x, depth, max(slot, 1))
}

private struct TTreeView: View {
    let tree: TTree
    @Environment(\.palette) private var palette

    static func twoLine(_ tree: TTree) -> Bool { tree.nodes.contains { $0.sub != nil } }
    static func height(_ tree: TTree) -> CGFloat {
        let rows = tree.pos.map { $0.map(\.1).max() ?? 0 } ?? (tTreeLayout(tree.nodes).depth.max() ?? 0)
        let two = twoLine(tree)
        return CGFloat(rows) * (two ? 66 : (tree.pos == nil ? 47 : 44)) + (two ? 40 : 28) + 32
    }

    var body: some View {
        Canvas { ctx, size in
            let two = Self.twoLine(tree)
            let rowGap: CGFloat = two ? 66 : (tree.pos == nil ? 47 : 44), h: CGFloat = two ? 40 : 28, pad: CGFloat = 16
            let layout = tTreeLayout(tree.nodes)
            let label = { (i: Int) in tree.labels[i] ?? tree.nodes[i].label }
            func measure(_ font: CGFloat) -> [CGFloat] {
                tree.nodes.indices.map { i in
                    let t = ctx.resolve(Text(label(i)).font(AppFont.mono(font, .bold))).measure(in: CGSize(width: 300, height: 40)).width
                    let sub = tree.nodes[i].sub.map { ctx.resolve(Text($0).font(AppFont.mono(font - 3))).measure(in: CGSize(width: 300, height: 40)).width } ?? 0
                    return max(t, sub) + 18
                }
            }
            // A tidy layout: each row packs left to right, a parent centres over its children, and a subtree
            // moves right only as far as its row needs. Too wide for the card, the font shrinks.
            let avail = size.width - 2 * pad
            let kids = tree.nodes.indices.map { i in tree.nodes.indices.filter { tree.nodes[$0].parent == i } }
            let depthMax = layout.depth.max() ?? 0
            var font: CGFloat = 13
            var widths: [CGFloat] = []
            var xs = [CGFloat](repeating: 0, count: tree.nodes.count)
            var total: CGFloat = 1
            for _ in 0..<5 {
                widths = measure(font)
                var next = [CGFloat](repeating: 0, count: depthMax + 1)
                func shift(_ id: Int, _ d: CGFloat) { xs[id] += d; kids[id].forEach { shift($0, d) } }
                func bump(_ id: Int) { next[layout.depth[id]] = max(next[layout.depth[id]], xs[id] + widths[id] / 2 + 10); kids[id].forEach(bump) }
                func place(_ id: Int) {
                    let cs = kids[id], dep = layout.depth[id]
                    if cs.isEmpty { xs[id] = next[dep] + widths[id] / 2 } else {
                        cs.forEach(place)
                        xs[id] = (xs[cs.first!] + xs[cs.last!]) / 2
                        let least = next[dep] + widths[id] / 2
                        if xs[id] < least { shift(id, least - xs[id]); bump(id) }
                    }
                    next[dep] = max(next[dep], xs[id] + widths[id] / 2 + 10)
                }
                place(0)
                total = tree.nodes.indices.map { xs[$0] + widths[$0] / 2 }.max() ?? 1
                if tree.pos != nil || total <= avail || font <= 9 { break }
                font -= 1
            }
            let spread = avail / max(total, 1)
            func at(_ i: Int) -> CGPoint {
                if let pos = tree.pos { return CGPoint(x: pad + CGFloat(pos[i].0) * avail, y: 16 + h / 2 + CGFloat(pos[i].1) * rowGap) }
                return CGPoint(x: pad + xs[i] * spread, y: 16 + h / 2 + CGFloat(layout.depth[i]) * rowGap)
            }
            for (i, n) in tree.nodes.enumerated() {
                guard let p = n.parent else { continue }
                let a = at(p), b = at(i)
                var path = Path(); path.move(to: CGPoint(x: a.x, y: a.y + h / 2)); path.addLine(to: CGPoint(x: b.x, y: b.y - h / 2))
                if let tone = tree.edges[i] { ctx.stroke(path, with: .color(tStroke(tone, palette)), lineWidth: 2) }
                else { ctx.stroke(path, with: .color(palette.muted.opacity(0.4)), lineWidth: 1.2) }
            }
            for arc in tree.arcs {
                let a = at(arc.from), b = at(arc.to)
                let side: CGFloat = arc.right ? 1 : -1
                let start = CGPoint(x: a.x + side * widths[arc.from] / 2, y: a.y), end = CGPoint(x: b.x + side * widths[arc.to] / 2, y: b.y)
                let control = CGPoint(x: max(start.x * side, end.x * side) * side + side * 40, y: (a.y + b.y) / 2)
                var path = Path(); path.move(to: start); path.addQuadCurve(to: end, control: control)
                let c = tStroke(arc.tone, palette)
                ctx.stroke(path, with: .color(c), lineWidth: 2)
                let ang = atan2(end.y - control.y, end.x - control.x)
                var head = Path()
                head.move(to: end)
                head.addLine(to: CGPoint(x: end.x - 8 * cos(ang - 0.45), y: end.y - 8 * sin(ang - 0.45)))
                head.addLine(to: CGPoint(x: end.x - 8 * cos(ang + 0.45), y: end.y - 8 * sin(ang + 0.45)))
                head.closeSubpath()
                ctx.fill(head, with: .color(c))
                ctx.draw(ctx.resolve(Text(arc.label).font(AppFont.mono(12, .bold)).foregroundColor(tNodeInk(arc.tone, palette))),
                         at: CGPoint(x: control.x + side * 4, y: control.y), anchor: arc.right ? .leading : .trailing)
            }
            for i in tree.nodes.indices {
                let c = at(i)
                let tone = tree.tones[i] ?? tree.base
                let (fill, ink) = tColors(tone, palette)
                let rect = CGRect(x: c.x - widths[i] / 2, y: c.y - h / 2, width: widths[i], height: h)
                let shape = Path(roundedRect: rect, cornerRadius: 8)
                ctx.fill(shape, with: .color(palette.surface))
                ctx.fill(shape, with: .color(fill))
                switch tone {
                case .stack: ctx.stroke(shape, with: .color(SimColors.active), lineWidth: 1.5)
                case .memo: ctx.stroke(shape, with: .color(SimColors.answer), lineWidth: 1.5)
                case .mirror: ctx.stroke(shape, with: .color(SimColors.green), lineWidth: 1.5)
                case .mismatch: ctx.stroke(shape, with: .color(SimColors.red), lineWidth: 1.5)
                default: break
                }
                if let sub = tree.nodes[i].sub {
                    ctx.draw(ctx.resolve(Text(label(i)).font(AppFont.mono(font, .bold)).foregroundColor(ink)), at: CGPoint(x: c.x, y: c.y - 7))
                    ctx.draw(ctx.resolve(Text(sub).font(AppFont.mono(font - 3)).foregroundColor(ink.opacity(0.85))), at: CGPoint(x: c.x, y: c.y + 9))
                } else {
                    ctx.draw(ctx.resolve(Text(label(i)).font(AppFont.mono(font, .bold)).foregroundColor(ink)), at: c)
                }
                if let (t, bt) = tree.badges[i] {
                    ctx.draw(ctx.resolve(Text(t).font(AppFont.mono(11, .bold)).foregroundColor(tNodeInk(bt, palette))), at: CGPoint(x: rect.maxX + 5, y: c.y), anchor: .leading)
                }
            }
        }
    }
}

private struct TGraphView: View {
    let graph: TGraph
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let r: CGFloat = 20, padX: CGFloat = 30, padTop: CGFloat = 30, padBottom: CGFloat = 38
            func at(_ i: Int) -> CGPoint {
                CGPoint(x: padX + CGFloat(graph.nodes[i].x) * (size.width - 2 * padX), y: padTop + CGFloat(graph.nodes[i].y) * (size.height - padTop - padBottom))
            }
            for e in graph.edges {
                let a = at(e.a), b = at(e.b)
                let len = max(hypot(b.x - a.x, b.y - a.y), 1)
                let ux = (b.x - a.x) / len, uy = (b.y - a.y) / len
                let start = CGPoint(x: a.x + ux * r, y: a.y + uy * r), end = CGPoint(x: b.x - ux * (r + 2), y: b.y - uy * (r + 2))
                let c = e.tone.map { $0 == .mismatch ? SimColors.red : tStroke($0, palette) } ?? palette.muted.opacity(0.45)
                var path = Path(); path.move(to: start); path.addLine(to: end)
                ctx.stroke(path, with: .color(c), style: StrokeStyle(lineWidth: e.tone == nil ? 1.4 : 2.2, dash: e.dashed ? [5, 4] : []))
                if graph.directed {
                    var head = Path()
                    head.move(to: end)
                    head.addLine(to: CGPoint(x: end.x - 9 * ux + 5 * uy, y: end.y - 9 * uy - 5 * ux))
                    head.addLine(to: CGPoint(x: end.x - 9 * ux - 5 * uy, y: end.y - 9 * uy + 5 * ux))
                    head.closeSubpath()
                    ctx.fill(head, with: .color(c))
                }
                if let w = e.weight {
                    // Beside the edge's midpoint, on the side facing up (or left for a vertical edge).
                    var nx = -uy, ny = ux
                    if ny > 0 || (ny == 0 && nx > 0) { nx = -nx; ny = -ny }
                    let m = CGPoint(x: (a.x + b.x) / 2 + nx * 11, y: (a.y + b.y) / 2 + ny * 11)
                    ctx.draw(ctx.resolve(Text(w).font(AppFont.mono(12, .bold)).foregroundColor(e.tone.map { tNodeInk($0, palette) } ?? palette.muted)), at: m)
                }
            }
            for i in graph.nodes.indices {
                let c = at(i)
                let tone = graph.tones[i] ?? graph.base
                let (fill, ink) = tColors(tone, palette)
                let circle = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
                ctx.fill(circle, with: .color(palette.surface))
                ctx.fill(circle, with: .color(fill))
                switch tone {
                case .outline, .frontier: ctx.stroke(circle, with: .color(SimColors.blue), lineWidth: 1.5)
                case .pending, .idle: ctx.stroke(circle, with: .color(palette.muted.opacity(0.4)), lineWidth: 1.2)
                case .stack: ctx.stroke(circle, with: .color(SimColors.active), lineWidth: 1.5)
                default: break
                }
                ctx.draw(ctx.resolve(Text(graph.nodes[i].label).font(AppFont.mono(graph.nodes[i].label.count > 2 ? 12 : 14, .bold)).foregroundColor(ink)), at: c)
                if let (t, ct) = graph.captions[i] {
                    ctx.draw(ctx.resolve(Text(t).font(AppFont.mono(12, .bold)).foregroundColor(tNodeInk(ct, palette))), at: CGPoint(x: c.x, y: c.y + r + 11))
                }
            }
        }
    }
}

/// Each row: a caps title with a note on the right, the jobs as bars sized by duration, end times underneath.
private struct TGanttView: View {
    let rows: [TGanttRow]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let pad: CGFloat = 18
            let total = CGFloat(rows.map { $0.jobs.map(\.0).reduce(0, +) }.max() ?? 1)
            let scale = (size.width - 2 * pad) / total
            for (r, row) in rows.enumerated() {
                let y0 = 12 + CGFloat(r) * 68
                ctx.draw(ctx.resolve(Text(row.title).font(AppFont.mono(12, .semibold)).foregroundColor(palette.muted)), at: CGPoint(x: pad, y: y0 + 6), anchor: .leading)
                ctx.draw(ctx.resolve(Text(row.note).font(AppFont.mono(13, .bold)).foregroundColor(palette.onSurface)), at: CGPoint(x: size.width - pad, y: y0 + 6), anchor: .trailing)
                var x = pad, t = 0
                for (d, tone) in row.jobs {
                    let w = CGFloat(d) * scale
                    let rect = CGRect(x: x + 1.5, y: y0 + 16, width: w - 3, height: 30)
                    let (fill, ink): (Color, Color) = switch tone {
                    case .active: (SimColors.active, Color(hex: 0x1F1A0A))
                    case .green, .matched: (SimColors.green, .white)
                    default: (palette.muted.opacity(0.3), palette.onSurface)
                    }
                    ctx.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(fill))
                    ctx.draw(ctx.resolve(Text("\(d)").font(AppFont.mono(13, .bold)).foregroundColor(ink)), at: CGPoint(x: rect.midX, y: rect.midY))
                    t += d
                    x += w
                    ctx.draw(ctx.resolve(Text("\(t)").font(AppFont.mono(10)).foregroundColor(palette.muted)), at: CGPoint(x: x - 4, y: y0 + 56), anchor: .trailing)
                }
            }
        }
    }
}

// MARK: - Coding pattern storyboards
// Fast and slow pointers, merge intervals, binary search on the answer, top-k, monotonic stack, cyclic
// sort, k-way merge, in-place reversal and greedy interval scheduling.

private func fastSlowTabs() -> [TTab] {
    let names = ["A", "B", "C", "D", "E", "F", "G", "H"], n = names.count, entry = 3
    func nxt(_ i: Int) -> Int { i == n - 1 ? entry : i + 1 }
    let next: [Int?] = (0..<n).map { nxt($0) }
    let cycleLen = n - entry
    /// Two pointers on the list; on the same node they share it, violet only once they have met in the loop.
    func chain(_ a: Int, _ aName: String, _ aTone: TTone, _ b: Int, _ bName: String, _ bTone: TTone, dim: Int = 0, met: Bool = false) -> TChain {
        var above: [Int: (String, TTone)] = [:]
        above[b] = (bName, bTone == .active ? .active : .blue)
        above[a] = a == b ? (met ? "meet" : "\(aName) · \(bName)", met ? .answer : .active) : (aName, aTone == .active ? .active : .blue)
        return TChain(nodes: names.enumerated().map { i, c in
            if i == a && i == b { return TCell(c, met ? .answer : .active) }
            if i == a { return TCell(c, aTone) }
            if i == b { return TCell(c, bTone) }
            return TCell(c, i < dim ? .struck : .idle)
        }, next: next, special: [n - 1: (.answer, "\(names[n - 1]).next = \(names[entry])")], above: above)
    }
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.blue, .fill, "slow · 1 hop"), (SimColors.active, .fill, "fast · 2 hops"), (SimColors.answer, .fill, "Cycle")]
    var s = 0, f = 0
    var detect = [TFrame(columns: 0, rows: [], formula: ["slow += 1, fast += 2 each step"], chips: [StoryChip("slow", names[s]), StoryChip("fast", names[f])],
                         headline: "Both pointers start at A. slow takes one hop per step, fast takes two.",
                         body: "If the list ends, fast reaches null first. If it loops, fast laps slow and they meet.",
                         chain: chain(f, "fast", .active, s, "slow", .outline))]
    var step = 0
    while true {
        s = nxt(s); f = nxt(nxt(f)); step += 1
        let bothIn = s >= entry && f >= entry
        let gap = ((s - f) % cycleLen + cycleLen) % cycleLen
        if s == f {
            detect.append(TFrame(columns: 0, rows: [], formula: ["slow = fast = {v:\(names[s])} → cycle"],
                                 chips: [StoryChip("slow", names[s]), StoryChip("fast", names[f]), StoryChip("steps", "\(step)", .answer)],
                                 headline: "slow and fast meet at {v:\(names[s])}: the list has a cycle.",
                                 body: "Only a loop lets the faster pointer come round behind the slower one. O(n) time, O(1) space.",
                                 chain: chain(f, "fast", .active, s, "slow", .outline, dim: entry, met: true)))
            break
        }
        let headline: String, body: String
        if f < s && bothIn {
            headline = "Fast has wrapped past \(names[n - 1]) and is now \(gap == 1 ? "one node" : "\(gap) nodes") behind slow."
            body = "Inside the loop fast closes the gap by 1 each step, so it can't jump over slow. They meet at \(names[nxt(s)])."
        } else {
            headline = "slow moves to \(names[s]), fast jumps to {\(names[f])}."
            body = f >= entry ? "fast is already inside the loop; slow enters it at \(names[entry])." : "Still on the straight part: fast pulls ahead by one node per step."
        }
        detect.append(TFrame(columns: 0, rows: [],
                             formula: bothIn ? ["gap = {p:slow} − {fast}", "= \(gap) → fast gains 1 per step → meet \(gap == 1 ? "next step" : "in \(gap) steps")"] : ["slow = \(names[s]), fast = \(names[f])"],
                             chips: [StoryChip("slow", names[s]), StoryChip("fast", names[f])] + (bothIn ? [StoryChip("gap", "\(gap)")] : []),
                             headline: headline, body: body, chain: chain(f, "fast", .active, s, "slow", .outline, dim: bothIn ? entry : 0)))
    }
    detect.append(TFrame(columns: 0, rows: [], formula: ["meeting point = {v:\(names[s])}"],
                         chips: [StoryChip("cycle length", "\(cycleLen)"), StoryChip("meet", names[s], .answer)],
                         headline: "The meeting point proves the cycle but is not where it starts.",
                         body: "Find start uses one more trick to locate \(names[entry]).", chain: chain(s, "meet", .answer, s, "meet", .answer, met: true)))
    // Find start: one pointer back to the head, both at one hop.
    let meet = s
    var p = 0, q = meet
    var start = [TFrame(columns: 0, rows: [], formula: ["p = head, q = meeting point, both += 1"], chips: [StoryChip("p", names[p]), StoryChip("q", names[q])],
                        headline: "Put p back at the head and leave q at \(names[meet]). Now both take one hop.",
                        body: "The head is as far from the cycle's start as the meeting point is, going round.",
                        chain: chain(q, "q", .outline, p, "p", .active))]
    while p != q {
        p = nxt(p); q = nxt(q)
        let met = p == q
        start.append(TFrame(columns: 0, rows: [], formula: [met ? "p = q = {v:\(names[p])} → cycle starts here" : "p = \(names[p]), q = \(names[q])"],
                            chips: [StoryChip("p", names[p]), StoryChip("q", names[q])] + (met ? [StoryChip("start", names[p], .answer)] : []),
                            headline: met ? "p and q meet at {v:\(names[p])}: that is where the cycle begins." : "p moves to \(names[p]), q to \(names[q]).",
                            body: met ? "Floyd's second phase: the distance from head to start equals meeting point to start, mod the cycle." : "Same speed now, so the gap between them never changes.",
                            chain: chain(q, "q", .outline, p, "p", .active, met: met)))
    }
    return [TTab(label: "Detect cycle", frames: detect, legend: legend),
            TTab(label: "Find start", frames: start, legend: [(SimColors.active, .fill, "p from head"), (SimColors.blue, .fill, "q from meeting point"), (SimColors.answer, .fill, "Cycle")])]
}

private func mergeIntervalTabs() -> [TTab] {
    let iv: [(Int, Int)] = [(1, 3), (2, 6), (8, 10), (9, 12), (15, 18)]
    var out: [(Int, Int)] = []
    var fate = [TTone](repeating: .idle, count: iv.count)
    var lastIdx: Int? = nil
    func tiles(_ current: Int?) -> TRow {
        TRow(title: "SORTED BY START", cells: iv.enumerated().map { i, v in
            TCell("\(v.0)–\(v.1)", i == current ? .active : i == lastIdx ? .outline : fate[i] == .matched ? .matched : .idle)
        }, spread: true)
    }
    func timeline(_ current: Int?) -> TTimeline {
        TTimeline(maxX: 18, step: 3, bars: iv.enumerated().map { i, v in
            TBar(start: Double(v.0), end: Double(v.1), tone: i == current ? .active : i == lastIdx ? .blue : fate[i] == .matched ? .matched : .idle)
        }, output: out.map { TBar(start: Double($0.0), end: Double($0.1), tone: .answer) })
    }
    func outText() -> String { out.isEmpty ? "–" : out.map { "[\($0.0),\($0.1)]" }.joined(separator: " ") }
    var frames = [TFrame(columns: 0, rows: [tiles(nil)], formula: ["sort by start, then sweep once"], chips: [StoryChip("output", "–")],
                         headline: "Sort the intervals by start, then walk them once.",
                         body: "Once sorted by start, only the last merged interval can overlap the next one.", timeline: timeline(nil))]
    for (i, v) in iv.enumerated() {
        if var last = out.last, v.0 <= last.1 {
            let old = last
            last.1 = max(last.1, v.1)
            out[out.count - 1] = last
            frames.append(TFrame(columns: 0, rows: [tiles(i)], formula: ["{\(v.0)} ≤ last.end {p:\(old.1)} → end = max(\(old.1), \(v.1)) = {v:\(last.1)}"],
                                 chips: [StoryChip("output", outText())],
                                 headline: v.1 > old.1 ? "\(v.0)–\(v.1) starts before \(old.0)–\(old.1) ends, so they fuse into \(last.0)–\(last.1)." : "\(v.0)–\(v.1) sits inside \(old.0)–\(old.1), so nothing changes.",
                                 body: "Once sorted by start, only the last merged interval can overlap the next one.", timeline: timeline(i)))
            fate[i] = .matched
            if let l = lastIdx { fate[l] = .matched }
            lastIdx = i
        } else {
            out.append(v)
            frames.append(TFrame(columns: 0, rows: [tiles(i)],
                                 formula: [out.count == 1 ? "first interval → output" : "{\(v.0)} > last.end {p:\(out[out.count - 2].1)} → new interval"],
                                 chips: [StoryChip("output", outText())],
                                 headline: out.count == 1 ? "\(v.0)–\(v.1) opens the output." : "\(v.0)–\(v.1) starts after the last one ends, so it opens a new interval.",
                                 body: "A gap means no later interval can bridge back: everything after starts even later.", timeline: timeline(i)))
            if let l = lastIdx { fate[l] = .matched }
            lastIdx = i
        }
    }
    lastIdx = nil
    for k in fate.indices { fate[k] = .matched }
    frames.append(TFrame(columns: 0, rows: [tiles(nil)], formula: ["output = {v:\(outText())}"], chips: [StoryChip("output", outText(), .answer)],
                         headline: "\(iv.count) intervals collapse into {v:\(out.count)}: \(outText()).",
                         body: "The sort is O(n log n); the sweep that follows is a single O(n) pass.", timeline: timeline(nil)))
    return [TTab(label: "Merge", frames: frames, legend: [(SimColors.active, .fill, "Considering"), (SimColors.blue, .fill, "Last merged"), (SimColors.green, .fill, "Absorbed"), (SimColors.answer, .fill, "Output")])]
}

private func binaryAnswerTabs() -> [TTab] {
    let w = [3, 2, 2, 4, 1, 4], days = 3
    let lo0 = w.max()!, hi0 = w.reduce(0, +)
    func split(_ cap: Int) -> [Int] {
        var d = 1, load = 0
        return w.map { x in if load + x > cap { d += 1; load = 0 }; load += x; return d }
    }
    func packages(_ cap: Int?) -> TRow {
        guard let cap else { return TRow(title: "PACKAGES · \(days) DAYS", cells: w.map { TCell("\($0)", .idle) }, spread: true) }
        let d = split(cap)
        var tags: [Int: (String, TTone)] = [:]
        for (i, day) in d.enumerated() { tags[i] = ("day \(day)", day > days ? .mismatch : .idle) }
        return TRow(title: "PACKAGES · \(days) DAYS", cells: w.enumerated().map { i, x in TCell("\(x)", d[i] > days ? .mismatch : .outline) }, tags: tags, spread: true)
    }
    func capacities(_ lo: Int, _ hi: Int, _ mid: Int?) -> TRow {
        var tags: [Int: (String, TTone)] = [:]
        tags[lo - lo0] = ("lo", .blue)
        tags[hi - lo0] = lo == hi ? ("lo = hi", .answer) : ("hi", .blue)
        if let mid { tags[mid - lo0] = ("mid", .active) }
        return TRow(title: "CAPACITY", cells: (lo0...hi0).map { c in
            TCell("\(c)", c == mid ? .active : c == lo || c == hi ? .outline : c > lo && c < hi ? .idle : .pending)
        }, tags: tags, spread: true, compact: true)
    }
    var lo = lo0, hi = hi0
    var frames = [TFrame(columns: 0, rows: [packages(nil), capacities(lo, hi, nil)], formula: ["lo = max = \(lo0), hi = sum = \(hi0)"],
                         chips: [StoryChip("lo", "\(lo)"), StoryChip("hi", "\(hi)")],
                         headline: "The smallest ship capacity that moves everything in \(days) days lies between \(lo0) and \(hi0).",
                         body: "Below the heaviest package nothing ships; at the total everything ships in one day.")]
    while lo < hi {
        let mid = (lo + hi) / 2
        let used = split(mid).last!
        let ok = used <= days
        let oldLo = lo, oldHi = hi
        if ok { hi = mid } else { lo = mid + 1 }
        frames.append(TFrame(columns: 0, rows: [packages(mid), capacities(oldLo, oldHi, mid)],
                             formula: [ok ? "cap {\(mid)} → \(used) day\(used == 1 ? "" : "s") ≤ \(days) → fits → hi = {p:\(mid)}" : "cap {\(mid)} → \(used) days > \(days) → too small → lo = {p:\(mid + 1)}"],
                             chips: [StoryChip("lo", "\(oldLo)"), StoryChip("mid", "\(mid)"), StoryChip("hi", "\(oldHi)")],
                             headline: ok ? "At capacity \(mid) everything ships in \(used) day\(used == 1 ? "" : "s"), so try smaller." : "At capacity \(mid) the last package spills into a \(used)th day, so \(mid) is too small.",
                             body: ok ? "Every capacity above \(mid) works too, so the answer is \(mid) or below." : "Every capacity below \(mid) fails too. The array is never sorted; the yes/no answer is."))
    }
    frames.append(TFrame(columns: 0, rows: [packages(lo), capacities(lo, lo, nil)], formula: ["lo = hi = {v:\(lo)}"],
                         chips: [StoryChip("capacity", "\(lo)", .answer), StoryChip("checks", "\(frames.count - 1)")],
                         headline: "lo and hi meet: the smallest capacity is {v:\(lo)}.",
                         body: "\(frames.count - 1) feasibility checks instead of \(hi0 - lo0 + 1). Each check is O(n), so O(n log(sum))."))
    return [TTab(label: "Ship packages", frames: frames, legend: [(SimColors.active, .fill, "mid"), (SimColors.blue, .fill, "Still possible"), (SimColors.red, .fill, "Overflow day")])]
}

private func topKTabs() -> [TTab] {
    let nums = [1, 3, 1, 5, 3, 1, 7, 3, 5], k = 2
    var order: [Int] = []
    var counts: [Int: Int] = [:]
    for x in nums { if counts[x] == nil { order.append(x) }; counts[x, default: 0] += 1 }
    let numsRow = TRow(title: "NUMS · K = \(k)", cells: nums.map { TCell("\($0)", .pending) }, spread: true, compact: true)
    func countRow(_ tones: [Int: TTone]) -> TRow {
        TRow(title: "COUNTS", cells: order.map { TCell("\($0) ×\(counts[$0]!)", tones[$0] ?? .idle) }, spread: true)
    }
    var heap: [Int] = []
    func heapRow(_ popped: Int?) -> TRow {
        let shown = (heap + (popped.map { [$0] } ?? [])).sorted { (counts[$0]!, $0) < (counts[$1]!, $1) }
        return TRow(title: "MIN-HEAP BY COUNT", cells: shown.map { TCell("\($0) ×\(counts[$0]!)", $0 == popped ? .active : .outline) }, spread: true)
    }
    var tones: [Int: TTone] = [:]
    var heapFrames = [TFrame(columns: 0, rows: [numsRow, countRow([:])], formula: ["count each value, then keep the k most frequent"],
                             chips: [StoryChip("k", "\(k)")], headline: "Find the \(k) most frequent values.",
                             body: "Counting is one pass. The question is how to pick the top k without sorting every count."),
                      TFrame(columns: 0, rows: [numsRow, countRow([:])], formula: [order.map { "\($0) ×\(counts[$0]!)" }.joined(separator: ", ")],
                             chips: [StoryChip("distinct", "\(order.count)")], headline: "One pass counts every value: \(order.count) distinct.",
                             body: "A hash map makes each count O(1).")]
    for x in order {
        heap.append(x)
        tones[x] = .outline
        if heap.count > k {
            let minX = heap.min { (counts[$0]!, $0) < (counts[$1]!, $1) }!
            heap.removeAll { $0 == minX }
            tones[minX] = .mismatch
            for h in heap { tones[h] = .matched }
            heapFrames.append(TFrame(columns: 0, rows: [numsRow, countRow(tones), heapRow(minX)],
                                     formula: ["size \(k + 1) > k = \(k) → pop min → {w:\(minX) ×\(counts[minX]!)} out"],
                                     chips: [StoryChip("heap", "\(heap.count) / \(k)")],
                                     headline: minX == x ? "\(x) appears \(counts[x] == 1 ? "once" : counts[x] == 2 ? "twice" : "\(counts[x]!) times"), fewer than \(heap.map(String.init).joined(separator: " and ")), so the heap pops it straight back out."
                                         : "\(x) pushes out \(minX), the least frequent in the heap.",
                                     body: "The heap never holds more than k, so selection is O(n log k)."))
        } else {
            heapFrames.append(TFrame(columns: 0, rows: [numsRow, countRow(tones), heapRow(nil)], formula: ["push \(x) ×\(counts[x]!) → size {\(heap.count)}"],
                                     chips: [StoryChip("heap", "\(heap.count) / \(k)")],
                                     headline: "Push {\(x)} (×\(counts[x]!)). The heap has room for \(k).",
                                     body: "A min-heap keeps the least frequent kept value on top, ready to be evicted."))
        }
    }
    for h in heap { tones[h] = .matched }
    heapFrames.append(TFrame(columns: 0, rows: [numsRow, countRow(tones), heapRow(nil)], formula: ["top \(k) = {v:\(heap.sorted().map(String.init).joined(separator: ", "))}"],
                             chips: [StoryChip("top \(k)", heap.sorted().map(String.init).joined(separator: ", "), .answer)],
                             headline: "The heap holds the answer: {v:\(heap.sorted().map(String.init).joined(separator: " and "))}.",
                             body: "O(n) to count plus O(m log k) over m distinct values, far less than sorting when k is small."))
    // Bucket sort: index by frequency, read from the top.
    let maxF = nums.count
    var buckets = [[Int]](repeating: [], count: maxF + 1)
    for x in order { buckets[counts[x]!].append(x) }
    let used = (1...maxF).filter { !buckets[$0].isEmpty }
    func bucketRow(_ lit: Int?) -> TRow {
        TRow(title: "BUCKETS BY FREQUENCY", cells: used.reversed().map { f in TCell(buckets[f].map(String.init).joined(separator: " "), f == lit ? .active : .outline, top: "f = \(f)") }, spread: true)
    }
    var taken: [Int] = []
    var bucket = [TFrame(columns: 0, rows: [numsRow, countRow([:]), bucketRow(nil)], formula: ["bucket[f] = values seen f times, f ≤ n = \(maxF)"],
                         chips: [StoryChip("buckets", "\(maxF)")], headline: "Put each value in the bucket for its count.",
                         body: "A count can never exceed n, so an array of n buckets replaces the heap.")]
    for f in used.reversed() where taken.count < k {
        taken += buckets[f].prefix(k - taken.count)
        bucket.append(TFrame(columns: 0, rows: [numsRow, countRow(Dictionary(uniqueKeysWithValues: taken.map { ($0, TTone.matched) })), bucketRow(f)],
                             formula: ["bucket[\(f)] → take \(buckets[f].map(String.init).joined(separator: ", ")) → {\(taken.count)} of \(k)"],
                             chips: [StoryChip("taken", "\(taken.count) / \(k)", taken.count == k ? .answer : .idle)],
                             headline: "Scanning from the top, bucket \(f) gives {\(buckets[f].map(String.init).joined(separator: " and "))}.",
                             body: taken.count == k ? "k values found, so the scan stops: O(n) overall, no heap at all." : "Keep going down until k values are taken."))
    }
    return [TTab(label: "Min-heap", frames: heapFrames, legend: [(SimColors.active, .fill, "Popped"), (SimColors.blue, .fill, "In heap"), (SimColors.green, .fill, "Kept"), (SimColors.red, .fill, "Discarded")],
                 start: min(4, heapFrames.count - 1)),
            TTab(label: "Bucket sort", frames: bucket, legend: [(SimColors.active, .fill, "Bucket read"), (SimColors.blue, .fill, "Bucket"), (SimColors.green, .fill, "Taken")])]
}

private func monoStackTabs() -> [TTab] {
    let a = [2, 1, 5, 6, 2, 3], n = a.count
    func run(greater: Bool) -> [TFrame] {
        var stack: [Int] = []
        var ans = [Int?](repeating: nil, count: n)
        var pops = 0
        func row(_ i: Int?) -> TRow {
            var tags: [Int: (String, TTone)] = [:]
            for (k, v) in ans.enumerated() where v != nil { tags[k] = ("→\(a[v!])", .matched) }
            return TRow(cells: a.enumerated().map { k, v in TCell("\(v)", k == i ? .active : ans[k] != nil ? .matched : .idle) }, tags: tags)
        }
        func stackRow() -> TRow {
            TRow(title: "STACK · VALUES \(greater ? "DECREASING" : "INCREASING")", cells: (0..<n).map { k in k < stack.count ? TCell("\(a[stack[k]]) · i\(stack[k])", .outline) : TCell("", .ghost) }, spread: true)
        }
        func ansText() -> String { "[" + ans.map { $0.map { "\(a[$0])" } ?? "·" }.joined(separator: ",") + "]" }
        let word = greater ? "bigger" : "smaller"
        var frames = [TFrame(columns: n, rows: [row(nil), stackRow()], formula: ["stack keeps indices still waiting for a \(greater ? "greater" : "smaller") value"],
                             chips: [StoryChip("pops", "0"), StoryChip("answer", ansText())],
                             headline: "For each value, find the next one to its right that is \(word).",
                             body: "The stack holds indices still waiting, their values kept in \(greater ? "decreasing" : "increasing") order.")]
        for i in 0..<n {
            var popped: [Int] = []
            while let top = stack.last, greater ? a[i] > a[top] : a[i] < a[top] { stack.removeLast(); ans[top] = i; popped.append(top); pops += 1 }
            stack.append(i)
            let cmp = greater ? ">" : "<"
            let formula: [String] = popped.isEmpty ? ["{\(a[i])} → nothing to pop · push i\(i)"]
                : popped.enumerated().map { k, t in (k == 0 ? "{\(a[i])} " : "") + "\(cmp) \(a[t]) → pop i\(t)" + (k == popped.count - 1 ? " · push i\(i)" : " · {\(a[i])}") }
            let headline = popped.isEmpty ? "\(a[i]) answers nothing, so it waits on the stack."
                : "\(a[i]) is the first value \(word) than \(popped.map { "\(a[$0])" }.joined(separator: " and ")), so it answers \(popped.count == 1 ? "it" : "both")."
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [row(i), stackRow()], formula: formula,
                                 chips: [StoryChip("pops", "\(pops)"), StoryChip("answer", ansText())], headline: headline,
                                 body: "Each index is pushed once and popped once, so the whole pass is O(n)."))
        }
        frames.append(TFrame(columns: n, rows: [row(nil), stackRow()], formula: ["answer = {v:\(ansText())}"],
                             chips: [StoryChip("answer", ansText(), .answer)],
                             headline: "The \(stack.count) indices left on the stack have no \(word) value to their right.",
                             body: "\(n) pushes and \(pops) pops: linear, although the inner loop looks quadratic."))
        return frames
    }
    return [TTab(label: "Next greater", frames: run(greater: true), legend: [(SimColors.active, .fill, "Incoming"), (SimColors.blue, .fill, "On stack"), (SimColors.green, .fill, "Answered")]),
            TTab(label: "Next smaller", frames: run(greater: false), legend: [(SimColors.active, .fill, "Incoming"), (SimColors.blue, .fill, "On stack"), (SimColors.green, .fill, "Answered")])]
}

private func cyclicSortTabs() -> [TTab] {
    func run(_ start: [Int], duplicate: Bool) -> [TFrame] {
        var a = start
        let n = a.count
        var i = 0, swaps = 0
        var frames: [TFrame] = []
        func rows(_ cur: Int?, dup: Int? = nil) -> [TRow] {
            [TRow(cells: a.enumerated().map { k, v in
                if k == cur { return TCell("\(v)", .active) }
                if k == dup { return TCell("\(v)", .mismatch) }
                return TCell("\(v)", v == k + 1 ? .matched : .idle)
            }), TRow(title: "HOME · INDEX I HOLDS I + 1", cells: (0..<n).map { TCell("\($0 + 1)", a[$0] == $0 + 1 ? .matched : .pending) })]
        }
        frames.append(TFrame(columns: n, rows: rows(nil), formula: ["value v belongs at index v − 1"], chips: [StoryChip("i", "0"), StoryChip("swaps", "0")],
                             headline: "Values 1 to \(n) each have a home: v belongs at index v − 1.",
                             body: "Swap each value straight into its home; whatever lands at i is placed next."))
        while i < n {
            let v = a[i]
            let j = v - 1
            if v < 1 || v > n {
                frames.append(TFrame(columns: n, lit: [i: .active], rows: rows(i), formula: ["\(v) is out of range → i++"], chips: [StoryChip("i", "\(i)"), StoryChip("swaps", "\(swaps)")],
                                     headline: "\(v) has no home in 1…\(n), so leave it and move on.", body: "Its slot is where the missing number would go."))
                i += 1
            } else if j == i {
                i += 1
                continue
            } else if a[j] == v {
                frames.append(TFrame(columns: n, lit: [i: .active, j: .mismatch], rows: rows(i, dup: j),
                                     formula: ["home( {\(v)} ) = \(v) − 1 = \(j) · nums[\(j)] = {w:\(v)}", "→ duplicate, i++"], chips: [StoryChip("i", "\(i)"), StoryChip("swaps", "\(swaps)")],
                                     headline: "\(v) wants index \(j), but index \(j) already holds a \(v). That is the duplicate.",
                                     body: "Swapping would loop forever, so leave it and move on."))
                i += 1
            } else {
                a.swapAt(i, j)
                swaps += 1
                frames.append(TFrame(columns: n, lit: [i: .active, j: .matched], rows: rows(i), formula: ["home( {\(v)} ) = \(j) → swap nums[\(i)] ↔ nums[\(j)]"],
                                     chips: [StoryChip("i", "\(i)"), StoryChip("swaps", "\(swaps)")],
                                     headline: "\(v) goes home to index \(j); \(a[i]) comes back to index \(i).",
                                     body: "i stays put: the value that just arrived still needs placing."))
            }
        }
        let bad = (0..<n).first { a[$0] != $0 + 1 }
        let answer = bad.map { duplicate ? "duplicate \(a[$0]), missing \($0 + 1)" : "missing \($0 + 1)" } ?? "none"
        frames.append(TFrame(columns: n, lit: bad.map { [$0: .mismatch] } ?? [:], rows: rows(nil, dup: bad), formula: ["first index not holding i + 1 → {v:\(answer)}"],
                             chips: [StoryChip("swaps", "\(swaps)"), StoryChip("answer", answer, .answer)],
                             headline: bad.map { "Index \($0) holds \(a[$0]), not \($0 + 1): \(duplicate ? "\(a[$0]) is doubled and \($0 + 1) is missing" : "\($0 + 1) is missing")." } ?? "Every value is home.",
                             body: "At most n swaps and one scan: O(n) time and no extra space."))
        return frames
    }
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Being placed"), (SimColors.green, .fill, "Home"), (SimColors.red, .fill, "Duplicate / gap")]
    return [TTab(label: "Find missing", frames: run([6, 2, 4, 1, 5], duplicate: false), legend: legend),
            TTab(label: "Find duplicate", frames: run([5, 1, 3, 3, 4], duplicate: true), legend: legend)]
}

private func kWayMergeTabs() -> [TTab] {
    let lists = [[2, 6, 8], [3, 6, 7], [1, 3, 4]]
    var pos = [0, 0, 0]
    var output: [Int] = []
    var heap: [(Int, Int)] = (0..<3).map { (lists[$0][0], $0) }
    func sortHeap() { heap.sort { ($0.0, $0.1) < ($1.0, $1.1) } }
    sortHeap()
    func rows(_ popped: (Int, Int)?) -> [TRow] {
        lists.enumerated().map { li, list in
            TRow(label: "L\(li + 1)", cells: list.enumerated().map { k, v in
                if let popped, popped.1 == li && k == pos[li] - 1 { return TCell("\(v)", .active) }
                if k < pos[li] { return TCell("\(v)", .pending) }
                return TCell("\(v)", k == pos[li] ? .outline : .idle)
            })
        }
    }
    func heapRow() -> TRow { TRow(title: "MIN-HEAP · ONE HEAD PER LIST", cells: heap.map { TCell("\($0.0) · L\($0.1 + 1)", .outline) }, spread: true) }
    var frames = [TFrame(columns: 3, rows: rows(nil) + [heapRow()], formula: ["heap = first value of each list"], chips: [StoryChip("output", "–"), StoryChip("heap", "\(heap.count)")],
                         headline: "Seed a min-heap with the head of each of the \(lists.count) lists.",
                         body: "The smallest remaining value is always one of the heads.")]
    while !heap.isEmpty {
        let top = heap.removeFirst()
        output.append(top.0)
        pos[top.1] += 1
        var formula = "pop {\(top.0)} from L\(top.1 + 1)"
        let refill = pos[top.1] < lists[top.1].count
        if refill {
            heap.append((lists[top.1][pos[top.1]], top.1))
            sortHeap()
            formula += " → push L\(top.1 + 1)[\(pos[top.1])] = {p:\(lists[top.1][pos[top.1]])}"
        } else {
            formula += " → L\(top.1 + 1) is empty"
        }
        frames.append(TFrame(columns: 3, rows: rows(top) + [heapRow()], formula: [formula],
                             chips: [StoryChip("output", output.map(String.init).joined(separator: " ")), StoryChip("heap", "\(heap.count)")],
                             headline: refill ? "\(top.0) came from L\(top.1 + 1), so L\(top.1 + 1)'s next value, \(lists[top.1][pos[top.1]]), takes its place in the heap."
                                 : "\(top.0) was L\(top.1 + 1)'s last value, so the heap shrinks to \(heap.count).",
                             body: "The heap stays at k items, so each of the n values costs O(log k)."))
    }
    frames.append(TFrame(columns: 3, rows: rows(nil), formula: ["merged = {v:\(output.map(String.init).joined(separator: " "))}"],
                         chips: [StoryChip("output", output.map(String.init).joined(separator: " "), .answer)],
                         headline: "All \(output.count) values are out, in order.", body: "O(n log k) for n values across k lists, with only k values held at once."))
    return [TTab(label: "Merge", frames: frames, legend: [(SimColors.active, .fill, "Popped"), (SimColors.blue, .fill, "In heap"), (Color.gray.opacity(0.4), .fill, "Merged")])]
}

/// Reverses nodes [from, to] of A…E in place, one node per step; `groups` repeats it over fixed-size blocks.
private func reversalFrames(from: Int, to: Int, groupSize: Int? = nil) -> [TFrame] {
    let names = ["A", "B", "C", "D", "E"], n = names.count
    var next: [Int?] = (0..<n).map { $0 + 1 < n ? $0 + 1 : nil }
    var head = 0
    var reversed = Set<Int>()
    var frames: [TFrame] = []
    let ranges: [(Int, Int)] = groupSize.map { k in stride(from: 0, to: n, by: k).compactMap { $0 + k - 1 < n ? ($0, $0 + k - 1) : nil } } ?? [(from, to)]
    func chain(prev: Int?, cur: Int?, save: Int?, cut: (Int, Int)?) -> TChain {
        var above: [Int: (String, TTone)] = [:]
        if let prev { above[prev] = ("prev", .matched) }
        if let cur { above[cur] = ("cur", .active) }
        if let save { above[save] = above[save] ?? ("next", .blue) }
        var below: [Int: (String, TTone)] = [:]
        for i in 0..<n where next[i] == nil && reversed.contains(i) { below[i] = ("next: null", .matched) }
        if head != 0 { below[head] = below[head] ?? ("head", .answer) }
        var special: [Int: (TTone, String?)] = [:]
        if let cur, let t = next[cur], abs(t - cur) == 1 && t < cur { special[cur] = (.active, nil) }
        return TChain(nodes: names.enumerated().map { i, c in TCell(c, i == cur ? .active : i == save ? .outline : reversed.contains(i) ? .matched : .idle) },
                      next: next, special: special, cut: cut, above: above, below: below,
                      arrowTones: Dictionary(uniqueKeysWithValues: reversed.map { ($0, TTone.matched) }))
    }
    frames.append(TFrame(columns: 0, rows: [], formula: [groupSize.map { "reverse every \($0) nodes" } ?? "reverse nodes \(names[from])…\(names[to])"],
                         chips: [StoryChip("prev", from == 0 ? "null" : names[from - 1]), StoryChip("cur", names[from])],
                         headline: groupSize == nil && from == 0 ? "Reverse the whole list by flipping one arrow at a time." : "Reverse only part of the list, then stitch it back in.",
                         body: "Three pointers do all of it: prev, cur, and next saved before each flip.",
                         chain: chain(prev: nil, cur: from, save: nil, cut: nil)))
    for (lo, hi) in ranges {
        let before: Int? = lo > 0 ? lo - 1 : nil
        let after = next[hi]
        var prev: Int? = after
        var cur: Int? = lo
        while let c = cur, c <= hi, c >= lo {
            let save = next[c]
            next[c] = prev
            reversed.insert(c)
            let prevName = prev.map { names[$0] } ?? "null"
            let saveName = save.map { names[$0] } ?? "null"
            frames.append(TFrame(columns: 0, rows: [],
                                 formula: ["next = {p:\(saveName)} · {\(names[c])}.next = {m:\(prevName)}", "· prev = \(names[c]) · cur = \(saveName)"],
                                 chips: [StoryChip("prev", prevName), StoryChip("cur", names[c]), StoryChip("next", saveName)],
                                 headline: "\(names[c])'s arrow flips from \(saveName) to \(prevName)" + (prev == after && after == nil ? ", becoming the new tail." : prev == after ? ", pointing past the reversed block." : ", joining the reversed part."),
                                 body: "next is saved first; without it, the rest of the list would be lost. No second list, O(1) space.",
                                 chain: chain(prev: prev, cur: c, save: save.flatMap { $0 <= hi ? $0 : nil }, cut: save.map { (c, $0) })))
            prev = c
            cur = save
        }
        if let before { next[before] = hi } else { head = hi }
        frames.append(TFrame(columns: 0, rows: [], formula: [before.map { "\(names[$0]).next = {v:\(names[hi])}" } ?? "head = {v:\(names[hi])}"],
                             chips: [StoryChip("head", names[head])],
                             headline: before.map { "\(names[$0]) now points at \(names[hi]), the block's new first node." } ?? "\(names[hi]) is the new head.",
                             body: "The block's old first node, \(names[lo]), already points at whatever followed the block.",
                             chain: chain(prev: nil, cur: nil, save: nil, cut: nil)))
    }
    var order: [String] = []
    var at: Int? = head
    while let a = at, order.count <= n { order.append(names[a]); at = next[a] }
    frames.append(TFrame(columns: 0, rows: [], formula: ["list = {v:\(order.joined(separator: " → "))}"], chips: [StoryChip("head", names[head], .answer)],
                         headline: "The list now reads {v:\(order.joined(separator: " → "))}.", body: "Every node was visited once: O(n) time, O(1) space.",
                         chain: chain(prev: nil, cur: nil, save: nil, cut: nil)))
    return frames
}

private func reversalTabs() -> [TTab] {
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "cur"), (SimColors.blue, .fill, "next (saved)"), (SimColors.green, .fill, "Reversed"), (SimColors.red, .fill, "Link cut")]
    return [TTab(label: "Whole list", frames: reversalFrames(from: 0, to: 4), legend: legend),
            TTab(label: "Sublist", frames: reversalFrames(from: 1, to: 3), legend: legend),
            TTab(label: "K-group", frames: reversalFrames(from: 0, to: 4, groupSize: 2), legend: legend)]
}

private func greedyIntervalTabs() -> [TTab] {
    let meetings: [(Int, Int)] = [(1, 4), (2, 3), (3, 5), (0, 7), (6, 8), (5, 9)]
    func run(_ label: String, _ sorted: [(Int, Int)], _ key: String) -> TTab {
        var fate = [TTone](repeating: .idle, count: sorted.count)
        var kept: [(Int, Int)] = []
        var lastEnd = Int.min
        func tl(_ cur: Int?) -> TTimeline {
            TTimeline(maxX: 9, step: 1, bars: sorted.enumerated().map { i, m in
                TBar(start: Double(m.0), end: Double(m.1), label: "\(m.0)–\(m.1)", tone: i == cur ? .active : fate[i])
            }, marker: lastEnd == Int.min ? nil : Double(lastEnd))
        }
        func conflict(_ m: (Int, Int)) -> Bool { key == "length" ? kept.contains { m.0 < $0.1 && $0.0 < m.1 } : m.0 < lastEnd }
        var frames = [TFrame(columns: 0, rows: [], formula: ["sort by \(key), keep each meeting that fits"], chips: [StoryChip("kept", "0")],
                             headline: "Fit the most meetings into one room, sorted by \(key).",
                             body: key == "end" ? "Sorted by end, the meeting that finishes first always leaves the most room." : "Watch whether this order finds the best answer.", timeline: tl(nil))]
        for (i, m) in sorted.enumerated() {
            let clash = conflict(m)
            let lastText = lastEnd == Int.min ? "–" : "\(lastEnd)"
            if clash { fate[i] = .mismatch } else { fate[i] = .matched; kept.append(m); lastEnd = max(lastEnd, m.1) }
            frames.append(TFrame(columns: 0, rows: [],
                                 formula: [clash ? "start {\(m.0)} < lastEnd {p:\(lastText)} → overlaps → skip" : "start {\(m.0)} ≥ lastEnd {p:\(lastText)} → keep"],
                                 chips: [StoryChip("kept", "\(kept.count)"), StoryChip("lastEnd", "\(lastEnd)")],
                                 headline: clash ? "\(m.0)–\(m.1) overlaps what is already kept, so it is skipped." : "\(m.0)–\(m.1) fits after everything kept, so it is kept.",
                                 body: key == "end" ? "Sorted by end, the meeting that finishes first always leaves the most room. Try By start to see it fail."
                                     : key == "start" ? "An early start can still run long and block everything after it." : "Short meetings can still straddle two that would both fit.",
                                 timeline: tl(i)))
        }
        frames.append(TFrame(columns: 0, rows: [], formula: ["kept = {v:\(kept.count)}"], chips: [StoryChip("kept", "\(kept.count)", .answer)],
                             headline: "Sorted by \(key), {v:\(kept.count)} meeting\(kept.count == 1 ? "" : "s") fit.",
                             body: key == "end" ? "The exchange argument: swapping any kept meeting for the earliest-ending one never makes things worse, so this is optimal."
                                 : "Sorting by end gets 3 here. The order is the whole algorithm.", timeline: tl(nil)))
        return TTab(label: label, frames: frames, legend: [(SimColors.active, .fill, "Considering"), (SimColors.green, .fill, "Kept"), (SimColors.red, .fill, "Skipped"), (SimColors.blue, .fill, "lastEnd")])
    }
    return [run("By end", meetings.sorted { ($0.1, $0.0) < ($1.1, $1.0) }, "end"),
            run("By start", meetings.sorted { ($0.0, $0.1) < ($1.0, $1.1) }, "start"),
            run("By length", meetings.sorted { ($0.1 - $0.0, $0.0) < ($1.1 - $1.0, $1.0) }, "length")]
}

// MARK: - More coding pattern storyboards
// In-place partitioning, modified binary search, divide and conquer, LIS, heap scheduling, hashing,
// parsing with a stack, monotonic deque and two heaps.

private func partitionTabs() -> [TTab] {
    let start = [3, 2, 3, 2, 2, 1, 3, 1, 1], pivot = 2
    func threeWay() -> [TFrame] {
        var a = start
        var low = 0, mid = 0, high = a.count - 1
        func tags() -> [Int: (String, TTone)] {
            var t: [Int: (String, TTone)] = [:]
            t[high] = ("high", .blue)
            t[mid] = mid == high ? ("mid·high", .active) : ("mid", .active)
            if low != mid { t[low] = ("low", .answer) } else { t[low] = (t[low]!.0 == "mid" ? "low·mid" : t[low]!.0, .active) }
            return t
        }
        func bars(_ arc: (Int, Int)?, done: Bool = false) -> TBars {
            TBars(values: a, tones: a.indices.map { i in
                done ? (a[i] == pivot ? .answer : .green)
                    : i < low ? .green : i < mid ? .answer : i == mid ? .active : i == high ? .blue : i > high ? .green : .idle
            }, tags: done ? [:] : tags(), arc: arc)
        }
        func chips() -> [StoryChip] { [StoryChip("low", "\(low)"), StoryChip("mid", "\(mid)"), StoryChip("high", "\(high)")] }
        var frames = [TFrame(columns: 0, rows: [], formula: ["< \(pivot) to the front · = \(pivot) in the middle · > \(pivot) to the back"], chips: chips(),
                             headline: "Sort 1s, 2s and 3s in one pass by partitioning around \(pivot).",
                             body: "Everything left of low is settled small, low to mid equals \(pivot), right of high is settled large. mid scans the unknown part.",
                             bars: bars(nil))]
        while mid <= high {
            let v = a[mid]
            if v < pivot {
                let f = TFrame(columns: 0, rows: [], formula: ["a[mid] {\(v)}", "< \(pivot) → swap(\(low), \(mid)) · low = \(low + 1) · mid = \(mid + 1)"], chips: chips(),
                               headline: low == mid ? "\(v) is small and already at low, so both pointers step forward." : "\(v) is small, so it swaps down to low and both pointers step forward.",
                               body: "Swaps with low bring in a value already seen, so mid can advance. Swaps with high don't.",
                               bars: bars(low == mid ? nil : (mid, low)))
                frames.append(f)
                a.swapAt(low, mid); low += 1; mid += 1
            } else if v == pivot {
                frames.append(TFrame(columns: 0, rows: [], formula: ["a[mid] {\(v)}", "= \(pivot) → already in the middle · mid = \(mid + 1)"], chips: chips(),
                                     headline: "\(v) equals the pivot, so it stays and mid moves on.",
                                     body: "The run from low to mid grows by one without a swap.", bars: bars(nil)))
                mid += 1
            } else {
                frames.append(TFrame(columns: 0, rows: [], formula: ["a[mid] {\(v)}", "> \(pivot) → swap(\(mid), \(high)) · high = \(high - 1) · mid stays \(mid)"], chips: chips(),
                                     headline: mid == high ? "\(v) is large and already at high, so high steps back." :
                                        "\(v) goes to the back, but mid doesn't move: the \(a[high]) that came in from index \(high) hasn't been checked.",
                                     body: "Swaps with low bring in a value already seen, so mid can advance. Swaps with high don't.",
                                     bars: bars(mid == high ? nil : (mid, high))))
                a.swapAt(mid, high); high -= 1
            }
        }
        frames.append(TFrame(columns: 0, rows: [], formula: ["mid \(mid) > high \(high) → {v:done}"], chips: chips(),
                             headline: "mid has passed high: three regions in {v:one pass}.",
                             body: "Every element is looked at once and swapped at most once. O(n) time, O(1) space.", bars: bars(nil, done: true)))
        return frames
    }
    func twoWay() -> [TFrame] {
        var a = start
        var b = 0
        func bars(_ i: Int?, arc: (Int, Int)? = nil) -> TBars {
            var tags: [Int: (String, TTone)] = [:]
            if b < a.count { tags[b] = ("b", .blue) }
            if let i { tags[i] = i == b ? ("b·i", .active) : ("i", .active) }
            return TBars(values: a, tones: a.indices.map { k in
                k == i ? .active : k < b ? .green : k == b && i != nil ? .blue : i.map { k < $0 } ?? true ? .answer : .idle
            }, tags: i == nil ? [:] : tags, arc: arc)
        }
        var frames = [TFrame(columns: 0, rows: [], formula: ["values < \(pivot) go left of b, everything else stays right"],
                             chips: [StoryChip("b", "0"), StoryChip("i", "0")],
                             headline: "Two regions only: move every value below \(pivot) to the front.",
                             body: "b marks where the next small value goes. i scans left to right.", bars: bars(0))]
        for i in a.indices {
            if a[i] < pivot {
                frames.append(TFrame(columns: 0, rows: [], formula: ["a[\(i)] {\(a[i])} < \(pivot) → swap(\(b), \(i)) · b = \(b + 1)"],
                                     chips: [StoryChip("b", "\(b)"), StoryChip("i", "\(i)")],
                                     headline: b == i ? "\(a[i]) is small and already at b." : "\(a[i]) is small, so it swaps with the \(a[b]) at b.",
                                     body: "Everything left of b is below \(pivot); from b to i is \(pivot) or more.", bars: bars(i, arc: b == i ? nil : (i, b))))
                a.swapAt(b, i); b += 1
            } else {
                frames.append(TFrame(columns: 0, rows: [], formula: ["a[\(i)] {\(a[i])} ≥ \(pivot) → leave it"],
                                     chips: [StoryChip("b", "\(b)"), StoryChip("i", "\(i)")],
                                     headline: "\(a[i]) is not below \(pivot), so it stays where it is.",
                                     body: "Only small values move; large ones drift right as small ones swap past them.", bars: bars(i)))
            }
        }
        frames.append(TFrame(columns: 0, rows: [], formula: ["{v:\(b)} values < \(pivot), then the rest"], chips: [StoryChip("b", "\(b)", .answer)],
                             headline: "The first {v:\(b)} slots hold every value below \(pivot).",
                             body: "One pass, like quicksort's Lomuto partition. The order inside each region is not kept.", bars: bars(nil)))
        return frames
    }
    return [TTab(label: "3-way", frames: threeWay(), legend: [(SimColors.green, .fill, "Settled"), (SimColors.answer, .fill, "Equal to 2"), (SimColors.active, .fill, "mid"), (SimColors.blue, .fill, "high")]),
            TTab(label: "2-way", frames: twoWay(), legend: [(SimColors.green, .fill, "Below 2"), (SimColors.answer, .fill, "2 or more"), (SimColors.active, .fill, "i"), (SimColors.blue, .fill, "b")])]
}

private func rotatedSearchTabs() -> [TTab] {
    let a = [27, 34, 42, 50, 61, 73, 88, 3, 8, 15], target = 8, n = a.count
    var lo = 0, hi = n - 1
    var probes: [(String, String)] = []
    var frames: [TFrame] = []
    func row(_ l: Int, _ m: Int, _ h: Int, found: Bool) -> TRow {
        var tags: [Int: (String, TTone)] = [:]
        tags[l] = ("lo", .blue); tags[h] = l == h ? ("lo·hi", .blue) : ("hi", .blue)
        tags[m] = m == l && m == h ? ("lo·mid·hi", .active) : m == l ? ("lo·mid", .active) : m == h ? ("mid·hi", .active) : ("mid", .active)
        return TRow(cells: a.enumerated().map { i, v in TCell("\(v)", i == m ? (found ? .answer : .active) : i >= l && i <= h ? .outline : .pending) }, tags: tags)
    }
    while lo <= hi {
        let mid = (lo + hi) / 2
        let (l, h) = (lo, hi)
        if a[mid] == target {
            probes.append(("a[\(mid)] = \(a[mid]), found", "i = \(mid)"))
            frames.append(TFrame(columns: n, lit: [mid: .active], rows: [row(l, mid, h, found: true)],
                                 formula: ["a[\(mid)] {\(a[mid])} = target → {v:found at \(mid)}"],
                                 chips: [StoryChip("lo", "\(l)"), StoryChip("mid", "\(mid)"), StoryChip("hi", "\(h)")],
                                 headline: "a[\(mid)] = \(target): found at {v:index \(mid)} after \(probes.count) probes.",
                                 body: "The rotation never cost an extra probe; each step still halved the window.", heading: "ROTATED ARRAY · TARGET \(target)",
                                 listTitle: "PROBES"))
            break
        }
        let leftSorted = a[lo] <= a[mid]
        var formula: [String], headline: String, move: String
        if leftSorted {
            let inside = a[lo] <= target && target < a[mid]
            if inside { hi = mid - 1; move = "hi = \(hi)" } else { lo = mid + 1; move = "lo = \(lo)" }
            formula = ["a[\(mid)] {\(a[mid])}", "≥ a[\(l)] \(a[l]) → left sorted · \(inside ? "" : "not ")\(a[l]) ≤ \(target) < \(a[mid]) → \(move)"]
            headline = "\(a[l])…\(a[mid]) is in order, and \(target) \(inside ? "falls inside it" : "isn't in it"), so \(inside ? "keep the left half" : "drop the left half")."
        } else {
            let inside = a[mid] < target && target <= a[hi]
            if inside { lo = mid + 1; move = "lo = \(lo)" } else { hi = mid - 1; move = "hi = \(hi)" }
            formula = ["a[\(mid)] {\(a[mid])}", "≤ a[\(h)] \(a[h]) → right sorted · \(a[mid]) < \(target) ≤ \(a[h]) → \(move)"]
            headline = "\(a[l...mid].map(String.init).joined(separator: ", ")) isn't in order, so the right half \(a[mid])…\(a[h]) must be. \(target) \(inside ? "falls inside it" : "isn't in it")."
        }
        probes.append(("a[\(mid)] = \(a[mid]), \(leftSorted ? "left" : "right") sorted", move))
        frames.append(TFrame(columns: n, lit: [mid: .active], rows: [row(l, mid, h, found: false)], formula: formula,
                             chips: [StoryChip("lo", "\(l)"), StoryChip("mid", "\(mid)"), StoryChip("hi", "\(h)")], headline: headline,
                             body: "Every probe, one half is sorted. Range-check that half and discard the other; still O(log n).",
                             heading: "ROTATED ARRAY · TARGET \(target)", listTitle: "PROBES"))
    }
    let withLists = frames.enumerated().map { k, f in
        var g = f
        g.list = probes.prefix(k + 1).enumerated().map { j, p in TListRow(name: "\(j + 1)", range: p.0, note: p.1, value: "", tone: j == k ? .outline : .pending) }
        return g
    }
    return [TTab(label: "Rotated", frames: withLists, legend: [(SimColors.active, .fill, "Probe"), (SimColors.blue, .fill, "Live window"), (Color.gray.opacity(0.25), .fill, "Ruled out")])]
}

private func divideConquerTabs() -> [TTab] {
    let input = [5, 3, 8, 2, 1, 9]
    func run(count: Bool) -> [TFrame] {
        var frames = [TFrame(columns: 0, rows: [TRow(title: "INPUT", cells: input.map { TCell("\($0)", .idle) }, spread: true)],
                             formula: [count ? "inversions = left + right + cross" : "sort each half, then merge"],
                             chips: count ? [StoryChip("inversions", "0")] : [],
                             headline: count ? "Count pairs i < j with a[i] > a[j] by piggybacking on merge sort." : "Split in half, sort each half, merge the two sorted runs.",
                             body: count ? "Pairs inside a half are counted by recursion; only pairs across the middle need the merge." : "Halving gives log n levels, and each level's merges touch n values.")]
        func sort(_ a: [Int]) -> ([Int], Int) {
            if a.count < 2 { return (a, 0) }
            let (l, li) = sort(Array(a[..<(a.count / 2)])), (r, ri) = sort(Array(a[(a.count / 2)...]))
            var i = 0, j = 0, cross = 0
            var merged: [Int] = []
            func rows(_ ci: Int?, _ cj: Int?) -> [TRow] {
                let cells = l.enumerated().map { k, v in TCell("\(v)", k < i ? .pending : k == ci ? .active : .outline) }
                    + r.enumerated().map { k, v in TCell("\(v)", k < j ? .pending : k == cj ? .active : .idle) }
                return [TRow(title: "LEFT · SORTED", cells: cells, spread: true, split: l.count, splitTitle: "RIGHT · SORTED"),
                        TRow(title: "MERGED", cells: (0..<(l.count + r.count)).map { k in k < merged.count ? TCell("\(merged[k])", .answer) : TCell("", .ghost) }, spread: true)]
            }
            func chips() -> [StoryChip] {
                count ? [StoryChip("left", "\(li)"), StoryChip("right", "\(ri)"), StoryChip("cross", "\(cross) so far")] : [StoryChip("merged", "\(merged.count) / \(l.count + r.count)")]
            }
            while i < l.count || j < r.count {
                if i < l.count && j < r.count {
                    let (ci, cj) = (i, j)
                    if r[j] < l[i] {
                        let beats = l.count - i
                        let before = rows(ci, cj)
                        _ = before
                        merged.append(r[j]); j += 1; cross += beats
                        frames.append(TFrame(columns: 0, rows: rows(ci, cj),
                                             formula: count ? ["{\(r[cj])} < {\(l[ci])}", "→ \(r[cj]) beats all \(beats) left value\(beats == 1 ? "" : "s") → count += {v:\(beats)}"]
                                                 : ["{\(r[cj])} < {\(l[ci])} → take \(r[cj]) from the right"],
                                             chips: chips(),
                                             headline: count ? (beats == 1 ? "\(r[cj]) is smaller than \(l[ci]): one inversion." :
                                                                    "\(r[cj]) is smaller than \(l[ci]), so it's also smaller than \(l[(ci + 1)...].map(String.init).joined(separator: " and ")). That is \(beats) inversions in one step.")
                                                 : "\(r[cj]) is smaller than \(l[ci]), so it goes next.",
                                             body: count ? "Both halves are sorted, so one comparison counts a whole run. The count comes free with an O(n log n) merge sort."
                                                 : "Only the two front values can be the smallest left, so one comparison picks it."))
                    } else {
                        merged.append(l[i]); i += 1
                        frames.append(TFrame(columns: 0, rows: rows(ci, cj),
                                             formula: ["{\(l[ci])} ≤ {\(r[cj])} → take \(l[ci]) from the left" + (count ? " · no inversion" : "")],
                                             chips: chips(),
                                             headline: "\(l[ci]) is not bigger than \(r[cj]), so it goes next\(count ? " and adds nothing" : "").",
                                             body: count ? "A left value taken first is smaller than everything left in the right half, so it forms no cross pair."
                                                 : "Taking the left one on ties keeps merge sort stable."))
                    }
                } else {
                    let fromLeft = i < l.count
                    let rest = fromLeft ? Array(l[i...]) : Array(r[j...])
                    merged += rest
                    if fromLeft { i = l.count } else { j = r.count }
                    frames.append(TFrame(columns: 0, rows: rows(nil, nil),
                                         formula: ["\(fromLeft ? "right" : "left") is empty → copy \(rest.map(String.init).joined(separator: ", "))"],
                                         chips: chips(),
                                         headline: "The \(fromLeft ? "right" : "left") half is used up, so \(rest.map(String.init).joined(separator: ", ")) follow\(rest.count == 1 ? "s" : "") as they are.",
                                         body: count ? "Leftover values cross nothing new: every pair they form was already counted." : "The leftovers are already sorted, so they are copied in one go."))
                }
            }
            return (merged, li + ri + cross)
        }
        let (sorted, total) = sort(input)
        frames.append(TFrame(columns: 0, rows: [TRow(title: "SORTED", cells: sorted.map { TCell("\($0)", .answer) }, spread: true)],
                             formula: [count ? "inversions = {v:\(total)}" : "sorted = {v:\(sorted.map(String.init).joined(separator: " "))}"],
                             chips: count ? [StoryChip("inversions", "\(total)", .answer)] : [StoryChip("sorted", "\(sorted.count)", .answer)],
                             headline: count ? "The array has {v:\(total)} inversions." : "Every merge done: the array is sorted.",
                             body: count ? "Checking every pair would be O(n²); counting during merges keeps it O(n log n)." : "log n levels of merging, each O(n): O(n log n) always."))
        return frames
    }
    return [TTab(label: "Count inversions", frames: run(count: true), legend: [(SimColors.active, .fill, "Comparing"), (SimColors.blue, .fill, "Still in left"), (SimColors.answer, .fill, "Merged")]),
            TTab(label: "Merge sort", frames: run(count: false), legend: [(SimColors.active, .fill, "Comparing"), (SimColors.blue, .fill, "Still in left"), (SimColors.answer, .fill, "Merged")])]
}

private func lisTabs() -> [TTab] {
    let a = [10, 9, 2, 5, 3, 7, 101, 18], n = a.count
    func numsRow(_ i: Int?, done: Bool = false) -> TRow {
        TRow(cells: a.enumerated().map { k, v in TCell("\(v)", k == i ? .active : i.map { k < $0 } ?? done ? .pending : .idle) })
    }
    var tails: [Int] = []
    var slots = 0
    do { var t: [Int] = []; for x in a { let p = t.firstIndex { $0 >= x } ?? t.count; if p == t.count { t.append(x) } else { t[p] = x } }; slots = t.count }
    func tailsRow(_ changed: Int?, _ note: String?, answer: Bool = false) -> TRow {
        var tags: [Int: (String, TTone)] = [:]
        for k in tails.indices { tags[k] = k == changed ? ("len \(k + 1)\(note.map { " · \($0)" } ?? "")", .active) : ("len \(k + 1)", .idle) }
        return TRow(title: "TAILS · INDEX K = LENGTH K + 1", cells: (0..<slots).map { k in
            k < tails.count ? TCell("\(tails[k])", answer ? .answer : k == changed ? .active : .outline) : TCell("", .ghost)
        }, tags: tags, spread: true)
    }
    func tailsText() -> String { "[" + tails.map(String.init).joined(separator: ", ") + "]" }
    var frames = [TFrame(columns: n, rows: [numsRow(nil), tailsRow(nil, nil)], formula: ["tails[k] = smallest end of any increasing run of length k + 1"],
                         chips: [StoryChip("length", "0"), StoryChip("tails", "[]")],
                         headline: "Find the longest strictly increasing subsequence.",
                         body: "Keep, for every length, the smallest value a run of that length can end with.")]
    for (i, x) in a.enumerated() {
        let p = tails.firstIndex { $0 >= x } ?? tails.count
        let formula: [String], headline: String, body: String
        if p == tails.count {
            tails.append(x)
            formula = ["lower_bound(tails, {\(x)}) = \(p) = size → append {\(x)}"]
            headline = p == 0 ? "\(x) starts the first run." : "\(x) is bigger than every tail, so the longest run grows to \(p + 1)."
            body = "A new length only appears when a value beats every tail."
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [numsRow(i), tailsRow(p, "new")], formula: formula,
                                 chips: [StoryChip("length", "\(tails.count)"), StoryChip("tails", tailsText())], headline: headline, body: body))
        } else {
            let old = tails[p]
            tails[p] = x
            formula = ["lower_bound(tails, {\(x)}) = \(p) → tails[\(p)]: \(old) → {\(x)}"]
            headline = "\(x) replaces \(old): a length-\(p + 1) run can now end lower."
            body = "The length doesn't change, but a smaller tail lets more later values extend it. Binary search makes each step O(log n)."
            frames.append(TFrame(columns: n, lit: [i: .active], rows: [numsRow(i), tailsRow(p, "was \(old)")], formula: formula,
                                 chips: [StoryChip("length", "\(tails.count)"), StoryChip("tails", tailsText())], headline: headline, body: body))
        }
    }
    frames.append(TFrame(columns: n, rows: [numsRow(nil, done: true), tailsRow(nil, nil, answer: true)], formula: ["LIS length = size of tails = {v:\(tails.count)}"],
                         chips: [StoryChip("length", "\(tails.count)", .answer)],
                         headline: "The longest increasing subsequence has length {v:\(tails.count)}.",
                         body: "tails is not itself a subsequence, only its length is the answer. O(n log n) overall."))
    var dp = [Int?](repeating: nil, count: n)
    func dpRows(_ i: Int?, _ from: [Int], _ best: Int?) -> [TRow] {
        [TRow(label: "a", cells: a.enumerated().map { k, v in TCell("\(v)", k == i ? .active : k == best ? .green : from.contains(k) ? .outline : .idle) }),
         TRow(label: "dp", cells: dp.enumerated().map { k, v in v.map { TCell("\($0)", k == i ? .active : k == best ? .green : .idle) } ?? TCell("·", .pending) })]
    }
    var dpFrames = [TFrame(columns: n, rows: dpRows(nil, [], nil), formula: ["dp[i] = 1 + max(dp[j]) over j < i with a[j] < a[i]"],
                           chips: [StoryChip("best", "0")],
                           headline: "dp[i] is the longest increasing run ending at a[i].",
                           body: "Each i looks back at every earlier j, so this version is O(n²).")]
    for i in 0..<n {
        let from = (0..<i).filter { a[$0] < a[i] }
        let best = from.max { dp[$0]! < dp[$1]! }
        dp[i] = 1 + (best.map { dp[$0]! } ?? 0)
        let overall = dp.compactMap { $0 }.max()!
        dpFrames.append(TFrame(columns: n, lit: [i: .active], rows: dpRows(i, from, best),
                               formula: [best.map { "dp[\(i)] = 1 + dp[\($0)] \(dp[$0]!) = {\(dp[i]!)}" } ?? "no smaller value before {\(a[i])} → dp[\(i)] = {1}"],
                               chips: [StoryChip("best", "\(overall)")],
                               headline: best.map { "\(a[i]) extends the run ending at \(a[$0]), reaching length \(dp[i]!)." } ?? "Nothing before \(a[i]) is smaller, so it starts a run of 1.",
                               body: "\(from.count) earlier value\(from.count == 1 ? " is" : "s are") smaller; the one with the longest run wins."))
    }
    let overall = dp.compactMap { $0 }.max()!
    dpFrames.append(TFrame(columns: n, rows: dpRows(nil, [], nil), formula: ["max(dp) = {v:\(overall)}"], chips: [StoryChip("best", "\(overall)", .answer)],
                           headline: "The largest dp value, {v:\(overall)}, is the answer.",
                           body: "Same answer as tails, but n² comparisons instead of n log n."))
    return [TTab(label: "Tails + binary search", frames: frames, legend: [(SimColors.active, .fill, "Current / replaced"), (SimColors.blue, .fill, "tails"), (SimColors.answer, .fill, "Answer")]),
            TTab(label: "DP O(n²)", frames: dpFrames, legend: [(SimColors.active, .fill, "Current"), (SimColors.blue, .fill, "Smaller earlier"), (SimColors.green, .fill, "Best to extend")])]
}

private func heapSchedulingTabs() -> [TTab] {
    let meetings = [(0, 30), (5, 10), (6, 12), (15, 20), (25, 35)]
    var heap: [(end: Int, room: Int)] = []
    var room = [Int?](repeating: nil, count: meetings.count)
    var ended = Set<Int>()
    var rooms = 0
    func timeline(_ cur: Int?, done: Bool = false) -> TTimeline {
        TTimeline(maxX: 35, step: 5, bars: meetings.enumerated().map { i, m in
            TBar(start: Double(m.0), end: Double(m.1), label: "\(m.0)–\(m.1)",
                 tone: i == cur ? .active : ended.contains(i) ? .mismatch : room[i] != nil ? .blue : .idle, side: room[i].map { "R\($0)" })
        }, marker: cur.map { Double(meetings[$0].0) }, markerTone: .active)
    }
    func heapRow(popped: Int?, pushed: Int?) -> TRow {
        var cells: [TCell] = []
        if let popped { cells.append(TCell("\(popped)", .mismatch)) }
        cells += heap.map(\.end).filter { $0 != pushed || pushed == nil }.sorted().map { TCell("\($0)", .outline) }
        if let pushed { cells.append(TCell("+\(pushed)", .active)) }
        return TRow(title: "MIN-HEAP OF END TIMES", cells: cells.isEmpty ? [TCell("", .ghost)] : cells, spread: true)
    }
    var frames = [TFrame(columns: 0, rows: [heapRow(popped: nil, pushed: nil)], formula: ["sort by start · heap holds the end time of every busy room"],
                         chips: [StoryChip("rooms", "0"), StoryChip("heap", "0")],
                         headline: "How many rooms do these meetings need?",
                         body: "Walk the meetings by start time. The heap's top is the room that frees up first.", timeline: timeline(nil), timelineFirst: true)]
    var peak = 0
    for (i, m) in meetings.enumerated() {
        var popped: Int? = nil
        let formula: String, headline: String
        if let top = heap.min(by: { $0.end < $1.end }), top.end <= m.0 {
            heap.removeAll { $0.end == top.end && $0.room == top.room }
            popped = top.end
            let freed = meetings.indices.first { room[$0] == top.room && meetings[$0].1 == top.end }!
            ended.insert(freed)
            room[i] = top.room
            formula = "heap.min {w:\(top.end)} ≤ start {\(m.0)} → pop \(top.end), push {\(m.1)} · rooms stay \(rooms)"
            headline = "\(m.0)–\(m.1) starts after \(meetings[freed].0)–\(meetings[freed].1) ended, so it takes that room instead of opening a \(ordinal(rooms + 1))."
        } else {
            rooms += 1
            room[i] = rooms
            let top = heap.min(by: { $0.end < $1.end })
            formula = top.map { "heap.min {p:\($0.end)} > start {\(m.0)} → push {\(m.1)} · rooms = \(rooms)" } ?? "heap empty → push {\(m.1)} · rooms = 1"
            headline = top.map { "Every room is busy until at least \($0.end), so \(m.0)–\(m.1) opens room \(rooms)." } ?? "\(m.0)–\(m.1) is first, so it opens room 1."
        }
        heap.append((m.1, room[i]!))
        peak = max(peak, heap.count)
        frames.append(TFrame(columns: 0, rows: [heapRow(popped: popped, pushed: m.1)], formula: [formula],
                             chips: [StoryChip("rooms", "\(rooms)"), StoryChip("heap", "\(heap.count)")], headline: headline,
                             body: "Only the earliest end time matters, and the heap keeps it on top. The peak heap size is the answer.", timeline: timeline(i), timelineFirst: true))
    }
    frames.append(TFrame(columns: 0, rows: [heapRow(popped: nil, pushed: nil)], formula: ["peak heap size = {v:\(peak)}"],
                         chips: [StoryChip("rooms", "\(peak)", .answer)],
                         headline: "{v:\(peak)} rooms are enough for all \(meetings.count) meetings.",
                         body: "Sorting is O(n log n) and each meeting does one push and at most one pop, O(log n) each.", timeline: timeline(nil), timelineFirst: true))
    return [TTab(label: "Rooms", frames: frames, legend: [(SimColors.active, .fill, "Starting now"), (SimColors.blue, .fill, "Room in use"), (SimColors.red, .fill, "Ended")])]
}

private func ordinal(_ n: Int) -> String {
    let suffix = (11...13).contains(n % 100) ? "th" : [1: "st", 2: "nd", 3: "rd"][n % 10] ?? "th"
    return "\(n)\(suffix)"
}

private func hashCountingTabs() -> [TTab] {
    let nums = [3, 4, 7, 2, -3, 1, 4, 2], k = 7
    func subarray() -> [TFrame] {
        var seen: [(Int, Int)] = [(0, 1)]
        var lastAt: [Int: Int] = [0: -1]
        var prefixes: [Int] = []
        var found = 0
        func numsRow(_ i: Int?, slice: ClosedRange<Int>?) -> TRow {
            var tags: [Int: (String, TTone)] = [:]
            for (j, p) in prefixes.enumerated() { tags[j] = ("\(p)", j == i ? .active : .idle) }
            return TRow(title: "NUMS · K = \(k)", cells: nums.enumerated().map { j, v in
                TCell("\(v)", j == i ? .active : slice?.contains(j) == true ? .outline : i.map { j < $0 } ?? !prefixes.isEmpty ? .pending : .idle)
            }, tags: tags, spread: true)
        }
        func seenRow(matched: Int?, stored: Int?) -> TRow {
            TRow(title: "SEEN PREFIX SUMS · COUNT", cells: seen.map { p, c in TCell("\(p) ×\(c)", p == matched ? .green : p == stored ? .outline : .idle) },
                 spread: true, compact: seen.count > 6)
        }
        var frames = [TFrame(columns: 0, rows: [numsRow(nil, slice: nil), seenRow(matched: nil, stored: nil)],
                             formula: ["slice (j, i] sums to k ⇔ prefix[i] − k = prefix[j]"],
                             chips: [StoryChip("found", "0")],
                             headline: "Count the slices that sum to \(k) in one pass.",
                             body: "Store every prefix sum seen so far. A slice ending here sums to k when prefix − k was seen before.")]
        var sum = 0
        for (i, v) in nums.enumerated() {
            sum += v
            prefixes.append(sum)
            let need = sum - k
            let hits = seen.first { $0.0 == need }?.1 ?? 0
            let from = lastAt[need].map { $0 + 1 }
            found += hits
            if let idx = seen.firstIndex(where: { $0.0 == sum }) { seen[idx].1 += 1 } else { seen.append((sum, 1)) }
            let slice = hits > 0 ? from!...i : nil
            let sliceText = slice.map { nums[$0].enumerated().map { j, x in j == 0 ? "\(x)" : x < 0 ? "− \(-x)" : "+ \(x)" }.joined(separator: " ") }
            frames.append(TFrame(columns: 0, rows: [numsRow(i, slice: slice.map { $0.lowerBound...max($0.lowerBound, $0.upperBound - 1) }.flatMap { $0.lowerBound < i ? $0 : nil }),
                                                    seenRow(matched: hits > 0 ? need : nil, stored: sum)],
                                 formula: ["prefix {\(sum)} − k \(k) = \(hits > 0 ? "{m:\(need)}" : "\(need)")",
                                           hits > 0 ? "· seen[\(need)] = \(hits) → found += \(hits)" : "· \(need) not seen → found stays \(found)"],
                                 chips: [StoryChip("found", "\(found)")] + (sliceText.map { [StoryChip("sum", $0)] } ?? []),
                                 headline: hits > 0 ? "Prefix \(sum) minus \(k) is \(need), a prefix seen \(from! == 0 ? "before the start" : "at index \(from! - 1)"). The slice between them sums to \(k)."
                                     : "Prefix \(sum) minus \(k) is \(need), never seen, so no slice ending here sums to \(k).",
                                 body: "One lookup replaces scanning back through every start, so the whole pass is O(n)."))
            lastAt[sum] = i
        }
        frames.append(TFrame(columns: 0, rows: [numsRow(nil, slice: nil), seenRow(matched: nil, stored: nil)], formula: ["found = {v:\(found)}"],
                             chips: [StoryChip("found", "\(found)", .answer)],
                             headline: "{v:\(found)} slices sum to \(k).",
                             body: "O(n) time and O(n) space, and negatives are fine, unlike a sliding window."))
        return frames
    }
    func twoSum() -> [TFrame] {
        let a = [3, 8, 4, 11, 6, 2], target = 10
        var seen: [(Int, Int)] = []
        func rows(_ i: Int?, hit: Int?) -> [TRow] {
            [TRow(title: "NUMS · TARGET \(target)", cells: a.enumerated().map { j, v in TCell("\(v)", j == i ? .active : j == hit ? .green : i.map { j < $0 } ?? false ? .pending : .idle) },
                  tags: Dictionary(uniqueKeysWithValues: a.indices.map { ($0, ("i\($0)", TTone.idle)) }), spread: true),
             TRow(title: "SEEN · VALUE → INDEX", cells: seen.isEmpty ? [TCell("", .ghost)] : seen.map { v, j in TCell("\(v) → i\(j)", j == hit ? .green : .outline) }, spread: true, compact: seen.count > 4)]
        }
        var frames = [TFrame(columns: 0, rows: rows(nil, hit: nil), formula: ["for each x: is target − x already seen?"], chips: [StoryChip("seen", "0")],
                             headline: "Find two values that add to \(target).",
                             body: "Instead of trying every pair, remember each value's index and look up its partner.")]
        for (i, x) in a.enumerated() {
            let need = target - x
            if let j = seen.first(where: { $0.0 == need })?.1 {
                frames.append(TFrame(columns: 0, rows: rows(i, hit: j), formula: ["target \(target) − {\(x)} = {m:\(need)}", "· seen[\(need)] = i\(j) → pair ({v:\(j), \(i)})"],
                                     chips: [StoryChip("pair", "(\(j), \(i))", .answer)],
                                     headline: "\(need) was seen at index \(j), so {v:\(need) + \(x) = \(target)}.",
                                     body: "One pass and one lookup per value: O(n) instead of O(n²)."))
                return frames
            }
            seen.append((x, i))
            frames.append(TFrame(columns: 0, rows: rows(i, hit: nil), formula: ["target \(target) − {\(x)} = \(need) · not seen → store \(x) → i\(i)"],
                                 chips: [StoryChip("seen", "\(seen.count)")],
                                 headline: "\(x) needs \(need), which hasn't appeared yet, so remember \(x).",
                                 body: "A later value may need \(x); the map answers that in O(1)."))
        }
        return frames
    }
    return [TTab(label: "Subarray sum = k", frames: subarray(), legend: [(SimColors.active, .fill, "Current prefix"), (SimColors.green, .fill, "Matched"), (SimColors.blue, .fill, "Stored")]),
            TTab(label: "Two sum", frames: twoSum(), legend: [(SimColors.active, .fill, "Current"), (SimColors.green, .fill, "Partner"), (SimColors.blue, .fill, "Stored")])]
}

private func parsingStackTabs() -> [TTab] {
    func decode() -> [TFrame] {
        let s = chars("3[a2[bc]]"), n = s.count
        var stack: [(Int, String)] = []
        var cur = "", num = 0, maxDepth = 0
        do { var d = 0; for c in s { if c == "[" { d += 1; maxDepth = max(maxDepth, d) } else if c == "]" { d -= 1 } } }
        func qt(_ x: String) -> String { "\"\(x)\"" }
        func rows(_ i: Int?, hot: Int?, popped: (Int, String)? = nil) -> [TRow] {
            var cells = stack.enumerated().map { k, f in TCell("\(f.0) × \(qt(f.1))", k == hot ? .active : .outline) }
            if let popped { cells.append(TCell("\(popped.0) × \(qt(popped.1))", .active)) }
            while cells.count < maxDepth + 1 { cells.append(TCell("", .ghost)) }
            return [TRow(cells: s.enumerated().map { k, c in TCell(c, k == i ? .active : i.map { k < $0 } ?? false ? .pending : .idle) }),
                    TRow(title: "STACK · COUNT × TEXT BEFORE", cells: cells, spread: true)]
        }
        var frames = [TFrame(columns: n, rows: rows(nil, hot: nil), formula: ["k[text] → text repeated k times"],
                             chips: [StoryChip("cur", qt("")), StoryChip("depth", "0")],
                             headline: "Decode 3[a2[bc]] with a stack instead of recursion.",
                             body: "[ suspends the text built so far, ] folds the inner text back into it.")]
        for (i, c) in s.enumerated() {
            let formula: String, headline: String, body: String
            var popped: (Int, String)? = nil
            var hot: Int? = nil
            if let d = Int(c) {
                num = num * 10 + d
                formula = "num = {\(num)}"
                headline = "\(c) is a count; hold it until the bracket opens."
                body = "Multi-digit counts build up digit by digit."
            } else if c == "[" {
                stack.append((num, cur))
                hot = stack.count - 1
                formula = "push (\(num), \(qt(cur))) · cur = \(qt(""))"
                headline = "[ saves \(num) × \(qt(cur)) and starts a fresh inner text."
                body = "Each frame saves exactly what a recursive call would, the count and the text before it."
                cur = ""; num = 0
            } else if c == "]" {
                let (k, before) = stack.removeLast()
                popped = (k, before)
                let inner = cur
                cur = before + String(repeating: inner, count: k)
                formula = "cur = \(qt(before)) + {\(qt(inner))} × \(k) = {v:\(qt(cur))}"
                headline = "] pops \(k) × \(qt(before)): repeat \(qt(inner)) \(k == 2 ? "twice" : k == 3 ? "three times" : "\(k) times")\(before.isEmpty ? "" : " and prepend \(qt(before))")."
                body = stack.isEmpty ? "The stack is empty again, so cur is the whole decoded string."
                    : "Each frame saves exactly what a recursive call would, the count and the text before it. The outer \(stack.last!.0) is still waiting."
            } else {
                let was = cur
                cur += c
                formula = "cur = \(qt(was)) + {\(c)} = \(qt(cur))"
                headline = "\(c) is plain text, so it joins the current text."
                body = "Letters only ever append to the innermost text being built."
            }
            frames.append(TFrame(columns: n, lit: [i: .active], rows: rows(i, hot: hot, popped: popped), formula: [formula],
                                 chips: [StoryChip("cur", qt(cur)), StoryChip("depth", "\(stack.count)")], headline: headline, body: body))
        }
        frames.append(TFrame(columns: n, rows: rows(nil, hot: nil), formula: ["result = {v:\(qt(cur))}"],
                             chips: [StoryChip("result", qt(cur), .answer)],
                             headline: "Decoded: {v:\(cur)}.",
                             body: "One pass, and the stack never grows deeper than the nesting: O(output) time."))
        return frames
    }
    func brackets() -> [TFrame] {
        let s = chars("([()[]])(]"), n = s.count
        let pair = [")": "(", "]": "["]
        var stack: [(String, Int)] = []
        var maxDepth = 0
        do { var d = 0; for c in s { if pair[c] == nil { d += 1; maxDepth = max(maxDepth, d) } else { d -= 1 } } }
        func rows(_ i: Int?, hot: Int?, bad: Bool = false) -> [TRow] {
            var cells = stack.enumerated().map { k, f in TCell("\(f.0) · i\(f.1)", k == hot ? (bad ? .mismatch : .active) : .outline) }
            while cells.count < maxDepth + 1 { cells.append(TCell("", .ghost)) }
            return [TRow(cells: s.enumerated().map { k, c in TCell(c, k == i ? (bad ? .mismatch : .active) : i.map { k < $0 } ?? false ? .pending : .idle) }),
                    TRow(title: "STACK · OPEN BRACKETS", cells: cells, spread: true)]
        }
        var frames = [TFrame(columns: n, rows: rows(nil, hot: nil), formula: ["open → push · close → top must be its partner"],
                             chips: [StoryChip("depth", "0")],
                             headline: "Check that every bracket closes in the right order.",
                             body: "The most recent unclosed bracket must be the first to close, which is exactly a stack.")]
        for (i, c) in s.enumerated() {
            if let open = pair[c] {
                guard let top = stack.last, top.0 == open else {
                    let got = stack.last?.0
                    frames.append(TFrame(columns: n, lit: [i: .mismatch], rows: rows(i, hot: stack.count - 1, bad: true),
                                         formula: ["\(c) needs \(open), top is {w:\(got ?? "empty")} → invalid"],
                                         chips: [StoryChip("valid", "no", .warn)],
                                         headline: "\(c) at index \(i) meets \(got.map { "\($0)" } ?? "an empty stack"): the string is {w:invalid}.",
                                         body: "Counting brackets alone would pass this string; only the stack sees the wrong order."))
                    return frames
                }
                stack.removeLast()
                frames.append(TFrame(columns: n, lit: [i: .active], rows: rows(i, hot: nil),
                                     formula: ["\(c) matches top {\(open)} at i\(top.1) → pop"],
                                     chips: [StoryChip("depth", "\(stack.count)")],
                                     headline: "\(c) closes the \(open) from index \(top.1).",
                                     body: "A match pops, uncovering the bracket that must close next."))
            } else {
                stack.append((c, i))
                frames.append(TFrame(columns: n, lit: [i: .active], rows: rows(i, hot: stack.count - 1),
                                     formula: ["\(c) opens → push · depth {\(stack.count)}"],
                                     chips: [StoryChip("depth", "\(stack.count)")],
                                     headline: "\(c) opens a new level and waits on the stack.",
                                     body: "Whatever closes next must match this one first."))
            }
        }
        return frames
    }
    return [TTab(label: "Decode", frames: decode(), legend: [(SimColors.active, .fill, "Current / popping"), (SimColors.blue, .fill, "Suspended"), (SimColors.answer, .fill, "Built text")]),
            TTab(label: "Valid brackets", frames: brackets(), legend: [(SimColors.active, .fill, "Current"), (SimColors.blue, .fill, "Waiting to close"), (SimColors.red, .fill, "Mismatch")])]
}

private func monoDequeTabs() -> [TTab] {
    let a = [1, 3, -1, -3, 5, 3, 6, 7], k = 3, n = a.count
    func run(max isMax: Bool) -> [TFrame] {
        var dq: [Int] = []
        var frames: [TFrame] = []
        let word = isMax ? "max" : "min"
        func m(_ v: Int) -> String { v < 0 ? "−\(-v)" : "\(v)" }
        for i in 0..<n {
            var expired: Int? = nil
            if let f = dq.first, f <= i - k { expired = f; dq.removeFirst() }
            var popped: [Int] = []
            while let b = dq.last, isMax ? a[b] <= a[i] : a[b] >= a[i] { popped.append(b); dq.removeLast() }
            dq.append(i)
            let lo = max(0, i - k + 1)
            let full = i >= k - 1
            var lit: [Int: TTone] = [:]
            for j in lo..<i { lit[j] = .blue }
            lit[i] = .active
            let row = TRow(cells: a.enumerated().map { j, v in
                TCell("\(v)", j == i ? .active : j == expired ? .mismatch : j >= lo && j < i ? .outline : j < lo ? .pending : .idle)
            })
            let deque = TRow(title: "DEQUE · FRONT TO BACK", cells: (0...k).map { s in
                s < dq.count ? TCell("\(a[dq[s]]) · i\(dq[s])", s == 0 && full ? .answer : dq[s] == i ? .active : .outline) : TCell("", .ghost)
            }, spread: true)
            var parts: [String] = []
            if let e = expired { parts.append("front i\(e) ≤ \(i) − \(k) → {w:expire}") }
            let cmp = isMax ? ">" : "<"
            if popped.isEmpty { parts.append("{\(m(a[i]))} → push back") }
            else { parts.append("{\(m(a[i]))} \(cmp) \(popped.map { m(a[$0]) }.joined(separator: ", ")) → pop back ×\(popped.count)") }
            let formula = parts.count == 2 ? [parts[0] + " ·", parts[1]] : parts
            let front = a[dq[0]]
            let headline: String
            if dq.count == 1 && (expired != nil || !popped.isEmpty) && i > 0 {
                headline = "\(m(a[i])) clears the whole deque" + (expired.map { ": \(m(a[$0])) left the window" } ?? "")
                    + (popped.isEmpty ? "." : (expired == nil ? ": " : ", and ") + "\(popped.reversed().map { m(a[$0]) }.joined(separator: ", ")) can never be a \(word) again.")
            } else if !popped.isEmpty {
                headline = "\(m(a[i])) outvotes \(popped.map { m(a[$0]) }.joined(separator: " and ")), which can never be a \(word) while \(m(a[i])) is in the window."
            } else if let e = expired {
                headline = "\(m(a[e])) slides out of the window; \(m(a[i])) waits behind \(m(front))."
            } else {
                headline = i == 0 ? "\(m(a[i])) is the first value, so it goes straight in." : "\(m(a[i])) is \(isMax ? "smaller" : "bigger") than the back, so it waits: it could be the \(word) later."
            }
            frames.append(TFrame(columns: n, lit: lit, rows: [row, deque], formula: formula,
                                 chips: [StoryChip("window", "[\(lo), \(i)]"), StoryChip(word, full ? m(front) : "–", full ? .answer : .idle)], headline: headline,
                                 body: "The front drops what's out of range, the back drops what's outvoted. Every index enters and leaves once, so O(n)."))
        }
        return frames
    }
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Incoming"), (SimColors.blue, .fill, "In window"), (SimColors.red, .fill, "Expired")]
    return [TTab(label: "Window max", frames: run(max: true), legend: legend + [(SimColors.answer, .fill, "Window max")]),
            TTab(label: "Window min", frames: run(max: false), legend: legend + [(SimColors.answer, .fill, "Window min")])]
}

private func twoHeapsTabs() -> [TTab] {
    let stream = [5, 15, 1, 3, 8, 7, 9, 10]
    func streamRow(_ i: Int?, gone: Int = -1) -> TRow {
        TRow(title: "STREAM", cells: stream.enumerated().map { j, v in TCell("\(v)", j == i ? .active : j == gone ? .mismatch : i.map { j < $0 } ?? false ? .pending : .idle) }, spread: true)
    }
    func heapRow(_ lo: [Int], _ hi: [Int], moving: Int?) -> TRow {
        let l = lo.sorted(by: >), h = hi.sorted()
        var movedOnce = false
        func tone(_ v: Int, _ base: TTone) -> TTone { if v == moving && !movedOnce { movedOnce = true; return .active }; return base }
        let cells = l.map { TCell("\($0)", tone($0, .outline)) } + h.map { TCell("\($0)", tone($0, .green)) }
        return TRow(title: "LO · MAX-HEAP", cells: cells, spread: true, split: l.count, splitTitle: "HI · MIN-HEAP")
    }
    func median(_ lo: [Int], _ hi: [Int]) -> String {
        if lo.count > hi.count { return "lo.top = \(lo.max()!)" }
        let s = lo.max()! + hi.min()!
        return "(\(lo.max()!) + \(hi.min()!)) / 2 = \(s % 2 == 0 ? "\(s / 2)" : String(format: "%.1f", Double(s) / 2))"
    }
    func running() -> [TFrame] {
        var lo: [Int] = [], hi: [Int] = []
        var frames = [TFrame(columns: 0, rows: [streamRow(nil)], formula: ["lo holds the smaller half, hi the larger · |lo| = |hi| or |hi| + 1"],
                             chips: [StoryChip("median", "–")],
                             headline: "Keep a running median as values stream in.",
                             body: "A max-heap of the smaller half and a min-heap of the larger half put the middle one peek away.")]
        for (i, x) in stream.enumerated() {
            lo.append(x)
            let up = lo.max()!
            lo.remove(at: lo.firstIndex(of: up)!); hi.append(up)
            var formula = "{\(x)} → lo → top \(up) → hi"
            var moving = up
            var headline = up == x ? "\(x) lands in hi" : "\(x) goes to lo, which hands its top \(up) to hi"
            if hi.count > lo.count {
                let down = hi.min()!
                hi.remove(at: hi.firstIndex(of: down)!); lo.append(down)
                formula += " · hi \(hi.count + 1) > lo \(lo.count - 1) → {\(down)} back to lo"
                moving = down
                headline += down == x ? ", and comes straight back to lo to keep the sizes even." : ", which pushes \(down) back to lo to keep the sizes even."
            } else {
                formula += " · sizes \(lo.count) | \(hi.count)"
                headline += "; the sizes are even."
            }
            frames.append(TFrame(columns: 0, rows: [streamRow(i), heapRow(lo, hi, moving: moving)], formula: [formula],
                                 chips: [StoryChip("median", median(lo, hi)), StoryChip("sizes", "\(lo.count) | \(hi.count)")], headline: headline,
                                 body: lo.count > hi.count ? "With an odd count, lo holds the extra value, so the median is always lo's root. Each insert is O(log n)."
                                     : "With an even count, the median is the average of the two roots. Each insert is O(log n)."))
        }
        return frames
    }
    func sliding() -> [TFrame] {
        let k = 3
        var lo: [Int] = [], hi: [Int] = []
        var frames = [TFrame(columns: 0, rows: [streamRow(nil)], formula: ["window k = \(k) · add the new value, drop the one leaving, rebalance"],
                             chips: [StoryChip("median", "–")],
                             headline: "The median of every window of \(k).",
                             body: "Same two heaps, but a value also leaves each step. Real code deletes lazily; here it is removed at once.")]
        for (i, x) in stream.enumerated() {
            var parts: [String] = []
            if lo.isEmpty || x <= lo.max()! { lo.append(x); parts.append("{\(x)} → lo") } else { hi.append(x); parts.append("{\(x)} → hi") }
            var gone = -1
            if i >= k {
                gone = i - k
                let y = stream[gone]
                if let j = lo.firstIndex(of: y) { lo.remove(at: j); parts.append("drop {w:\(y)} from lo") } else { hi.remove(at: hi.firstIndex(of: y)!); parts.append("drop {w:\(y)} from hi") }
            }
            var moving: Int? = x
            if lo.count > hi.count + 1 { let v = lo.max()!; lo.remove(at: lo.firstIndex(of: v)!); hi.append(v); moving = v; parts.append("\(v) → hi") }
            else if hi.count > lo.count { let v = hi.min()!; hi.remove(at: hi.firstIndex(of: v)!); lo.append(v); moving = v; parts.append("\(v) → lo") }
            let full = i >= k - 1
            frames.append(TFrame(columns: 0, rows: [streamRow(i, gone: gone), heapRow(lo, hi, moving: moving)], formula: [parts.joined(separator: " · ")],
                                 chips: [StoryChip("window", "[\(max(0, i - k + 1)), \(i)]"), StoryChip("median", full ? "\(lo.max()!)" : "–", full ? .answer : .idle)],
                                 headline: gone >= 0 ? "\(x) enters and \(stream[gone]) leaves; the median of the window is \(lo.max()!)."
                                     : full ? "The first window is full: its median is \(lo.max()!)." : "\(x) joins; the window isn't full yet.",
                                 body: "Each add, remove and rebalance is O(log k), so the whole pass is O(n log k)."))
        }
        return frames
    }
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Moving"), (SimColors.blue, .fill, "lo · smaller half"), (SimColors.green, .fill, "hi · larger half")]
    return [TTab(label: "Median", frames: running(), legend: legend), TTab(label: "Sliding median", frames: sliding(), legend: legend)]
}

// MARK: - Tree and search pattern storyboards
// Top-down memoization, meet in the middle, subsets, backtracking, BST in-order, the exchange argument,
// binary lifting and tree DFS path sums.

/// Braces in a set like {a,b} would read as a highlight mark, so escape them for headlines.
private func esc(_ s: String) -> String { s.replacingOccurrences(of: "{", with: "\\{") }

private let supers = ["⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"]
private func sup(_ n: Int) -> String { String(n).compactMap { Int(String($0)) }.map { supers[$0] }.joined() }

private let callLegend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Active"), (SimColors.active, .ring, "On the stack"), (SimColors.green, .fill, "Returned")]

private func textParam(_ topicId: String) -> TParam? {
    switch topicId {
    case "memo_recursion_pattern": TParam(label: { _ in "n" }, values: Array(3...7), start: 6, build: memoTabs)
    case "meet_in_middle_pattern": TParam(label: { _ in "Items · target 15" }, values: [4, 6], start: 6, build: mitmTabs)
    case "subsets_pattern": TParam(label: { _ in "Elements" }, values: [2, 3], start: 3, build: subsetsTabs)
    case "backtracking_pattern": TParam(label: { _ in "Candidates 3, 4, 5 · target" }, values: [7, 8, 9], start: 8, build: backtrackTabs)
    default: nil
    }
}

private func memoTabs(_ n: Int) -> [TTab] {
    enum Ev { case push(Int), base(Int), hit(Int), ret(Int, Int, Int) }
    var nodes: [TNode] = [], arg: [Int] = [], events: [Ev] = []
    var seen = [Bool](repeating: false, count: n + 1)
    func call(_ k: Int, _ parent: Int?) -> Int {
        let id = nodes.count
        nodes.append(TNode(label: "f(\(k))", parent: parent)); arg.append(k)
        if k < 2 { events.append(.base(id)); seen[k] = true; return k }
        if seen[k] { events.append(.hit(id)); return fibValue(k) }
        events.append(.push(id))
        let a = call(k - 1, id), b = call(k - 2, id)
        seen[k] = true
        events.append(.ret(id, a, b))
        return a + b
    }
    func fibValue(_ k: Int) -> Int { var a = 0, b = 1; for _ in 0..<k { (a, b) = (b, a + b) }; return a }
    let answer = call(n, nil)
    var tones: [Int: TTone] = [:], labels: [Int: String] = [:], stack: [Int] = []
    var memo = [Int?](repeating: nil, count: n + 1)
    var frames: [TFrame] = []
    func frame(_ cur: Int?, read: Int?, _ headline: String, _ body: String) {
        var edges: [Int: TTone] = [:]
        for id in stack + (cur.map { [$0] } ?? []) where nodes[id].parent != nil { edges[id] = .active }
        let memoRow = TRow(cells: (0...n).map { k in memo[k].map { TCell("\($0)", k == read ? .active : .outline) } ?? TCell("", .ghost) })
        frames.append(TFrame(columns: n + 1, lit: read.map { [$0: .active] } ?? [:], rows: [memoRow], headline: headline, body: body,
                             heading: "MEMO", tree: TTree(nodes: nodes, tones: tones, labels: labels, edges: edges)))
    }
    for ev in events {
        switch ev {
        case .push(let id):
            let k = arg[id]
            tones[id] = .active
            frame(id, read: nil, "f(\(k)) isn't in the memo yet, so it calls f(\(k - 1)) first.", "The stack grows one call per level until a base case answers.")
            tones[id] = .stack
            stack.append(id)
        case .base(let id):
            let k = arg[id]
            memo[k] = k
            tones[id] = .active
            frame(id, read: k, "f(\(k)) is a base case and returns \(k); the memo records it.", "Base cases seed the table every other entry is built from.")
            tones[id] = .mirror
        case .hit(let id):
            let k = arg[id], p = nodes[id].parent.map { arg[$0] } ?? n
            labels[id] = "f(\(k)) memo"
            tones[id] = .active
            frame(id, read: k, "f(\(p)) asks for f(\(k)) again. The memo already has \(memo[k]!), so no subtree is built.",
                  "Each f(n) is computed once and then read back, so 2ⁿ calls become n + 1.")
            tones[id] = .memo
        case .ret(let id, let a, let b):
            let k = arg[id]
            memo[k] = a + b
            stack.removeLast()
            labels[id] = "f(\(k))=\(a + b)"
            tones[id] = .active
            frame(id, read: k, "f(\(k)) = \(a) + \(b) = \(a + b), written to memo[\(k)].", "Every later call to f(\(k)) is now a single lookup.")
            tones[id] = id == 0 ? .answer : .mirror
        }
    }
    let plain = 2 * fibValue(n + 1) - 1
    frame(nil, read: n, "f(\(n)) = {v:\(answer)} with \(nodes.count) calls instead of \(plain).",
          "Top-down memoization is the recursion plus a table: O(n) time, O(n) space.")
    return [TTab(label: "Memo", frames: frames, legend: callLegend + [(SimColors.answer, .fill, "Memo hit")])]
}

private func mitmTabs(_ n: Int) -> [TTab] {
    let items = Array([3, 7, 9, 4, 6, 11].prefix(n)), target = 15, h = n / 2
    let left = Array(items[..<h]), right = Array(items[h...])
    func sums(_ a: [Int]) -> [(sum: Int, pick: [Int])] {
        (0..<(1 << a.count)).map { m in
            let pick = a.indices.filter { m >> $0 & 1 == 1 }
            return (pick.map { a[$0] }.reduce(0, +), pick)
        }.sorted { ($0.sum, $0.pick.count) < ($1.sum, $1.pick.count) }
    }
    let ls = sums(left), rs = sums(right)
    func itemsRow(_ l: [Int], _ r: [Int]) -> TRow {
        TRow(title: "ITEMS", cells: left.indices.map { TCell("\(left[$0])", l.contains($0) ? .active : .outline) }
            + right.indices.map { TCell("\(right[$0])", r.contains($0) ? .answer : .idle) }, spread: true, split: h, divider: true)
    }
    func leftRow(_ i: Int?) -> TRow {
        TRow(title: "LEFT SUBSET SUMS · 2\(sup(h))", cells: ls.enumerated().map { j, v in TCell("\(v.sum)", j == i ? .active : i.map { j < $0 } ?? false ? .pending : .idle) }, spread: true)
    }
    func rightRow(lo: Int?, mid: Int?, hi: Int?, found: Bool) -> TRow {
        var tags: [Int: (String, TTone)] = [:]
        if let lo { tags[lo] = ("lo", .blue) }
        if let hi { tags[hi] = tags[hi] == nil ? ("hi", .blue) : ("lo·hi", .blue) }
        if let mid { tags[mid] = (tags[mid].map { $0.0 + "·mid" } ?? "mid", .blue) }
        return TRow(title: "RIGHT SUBSET SUMS · SORTED", cells: rs.enumerated().map { j, v in
            TCell("\(v.sum)", j == mid ? (found ? .answer : .mismatch) : lo.map { j >= $0 && j <= hi! } ?? false ? .outline : lo == nil && mid == nil ? .idle : .pending)
        }, tags: tags, spread: true)
    }
    let pow = "2 × 2\(sup(h)) = \(2 << (h - 1)) sums instead of 2\(sup(n)) = \(1 << n)"
    var frames = [TFrame(columns: 0, rows: [itemsRow([], [])], formula: ["2\(sup(n)) = \(1 << n) subsets → split into two halves of \(h)"],
                         headline: "Pick items that sum to \(target). Trying all 2\(sup(n)) subsets is what this avoids.",
                         body: "Split the items in half and enumerate each half on its own."),
                  TFrame(columns: 0, rows: [itemsRow([], []), leftRow(nil)], formula: ["left half \(left.map(String.init).joined(separator: ", ")) → 2\(sup(h)) sums"],
                         headline: "The left half's \(ls.count) subsets give these sums.", body: "Enumerating one half is 2^(n/2) work, not 2^n."),
                  TFrame(columns: 0, rows: [itemsRow([], []), leftRow(nil), rightRow(lo: nil, mid: nil, hi: nil, found: false)],
                         formula: ["right half \(right.map(String.init).joined(separator: ", ")) → sort the sums"],
                         headline: "The right half's sums are sorted, so each partner is a binary search away.",
                         body: "Sorting costs O(2^(n/2) · n/2) once.")]
    var found = 0
    for (i, l) in ls.enumerated() {
        let need = target - l.sum
        if need < 0 {
            frames.append(TFrame(columns: 0, rows: [itemsRow(l.pick, []), leftRow(i), rightRow(lo: nil, mid: nil, hi: nil, found: false)],
                                 formula: ["\(target) − {\(l.sum)} < 0 → skip"], chips: [StoryChip("found", "\(found)")],
                                 headline: "Left sum \(l.sum) already overshoots \(target), so there is nothing to search for.", body: pow + "."))
            continue
        }
        var lo = 0, hi = rs.count - 1, mid = 0, hit = false
        while lo <= hi {
            mid = (lo + hi) / 2
            if rs[mid].sum == need { hit = true; break }
            if rs[mid].sum < need { lo = mid + 1 } else { hi = mid - 1 }
        }
        if hit { found += 1 }
        frames.append(TFrame(columns: 0, rows: [itemsRow(l.pick, hit ? rs[mid].pick : []), leftRow(i),
                                                rightRow(lo: hit ? lo : nil, mid: mid, hi: hit ? hi : nil, found: hit)],
                             formula: [hit ? "\(target) − {\(l.sum)} = \(need) → binary search right → {v:\(need)} found"
                                           : "\(target) − {\(l.sum)} = \(need) → binary search right → {w:\(need) missing}"],
                             chips: [StoryChip("found", "\(found)", hit ? .answer : .idle)],
                             headline: hit ? "Left sum \(l.sum) needs \(need) from the right half, and binary search finds it: \(l.sum) + \(need) = \(target)."
                                 : "Left sum \(l.sum) needs \(need), which no right subset makes.",
                             body: pow + ", and sorting one side turns pairing into a lookup."))
    }
    frames.append(TFrame(columns: 0, rows: [itemsRow([], []), leftRow(ls.count), rightRow(lo: nil, mid: nil, hi: nil, found: false)],
                         formula: ["matches = {v:\(found)}"], chips: [StoryChip("found", "\(found)", .answer)],
                         headline: found == 0 ? "No subset reaches \(target)." : "{v:\(found)} subset\(found == 1 ? "" : "s") reach \(target).",
                         body: "O(2^(n/2) · n) instead of O(2^n): n = 40 goes from a trillion subsets to about a million."))
    return [TTab(label: "Split", frames: frames, legend: [(SimColors.active, .fill, "Current left sum"), (SimColors.blue, .fill, "Search range"), (SimColors.answer, .fill, "Match")])]
}

private func subsetsTabs(_ n: Int) -> [TTab] {
    let letters = Array("abcd".prefix(n)).map(String.init)
    var nodes: [TNode] = [], picks: [[Int]] = []
    func build(_ pick: [Int], _ parent: Int?) {
        let id = nodes.count
        nodes.append(TNode(label: "{" + pick.map { letters[$0] }.joined(separator: ",") + "}", parent: parent)); picks.append(pick)
        for j in ((pick.last ?? -1) + 1)..<n { build(pick + [j], id) }
    }
    build([], nil)
    func kids(_ id: Int) -> [Int] { nodes.indices.filter { nodes[$0].parent == id } }
    var tones: [Int: TTone] = [:], stack: [Int] = [], out: [Int] = []
    var frames: [TFrame] = []
    let total = 1 << n
    func frame(_ cur: Int?, _ headline: String, _ body: String) {
        var edges: [Int: TTone] = [:]
        for id in stack + (cur.map { [$0] } ?? []) where nodes[id].parent != nil { edges[id] = .active }
        let cells = out.map { TCell(nodes[$0].label, $0 == cur ? .active : .answer) }
        let rows = stride(from: 0, to: max(cells.count, 1), by: 8).map { s in
            TRow(title: s == 0 ? "OUTPUT · \(out.count) OF \(total)" : nil, cells: cells.isEmpty ? [TCell("", .ghost)] : Array(cells[s..<min(s + 8, cells.count)]), spread: true, compact: cells.count > 5)
        }
        frames.append(TFrame(columns: 0, rows: rows, headline: headline, body: body, tree: TTree(nodes: nodes, tones: tones, edges: edges)))
    }
    func visit(_ id: Int) {
        out.append(id)
        tones[id] = .active
        let cs = kids(id), label = esc(nodes[id].label)
        let later = cs.map { letters[picks[$0].last!] }
        if id == 0 {
            frame(id, "\(label) is the first subset: record it, then branch on \(later.joined(separator: ", ")).", "Every call records its subset before it branches.")
        } else if cs.isEmpty {
            frame(id, "\(label) has no children: after \(letters[picks[id].last!]), there is nothing left to add.",
                  "Calls only add elements after the last pick, so {\(letters.last!),\(letters[0])} never appears.")
        } else {
            frame(id, "Record \(label), then try adding each later element: \(later.joined(separator: ", ")).", "Each branch is one more element, never an earlier one.")
        }
        tones[id] = cs.isEmpty ? .mirror : .stack
        if !cs.isEmpty { stack.append(id) }
        for (k, c) in cs.enumerated() {
            if k > 0, !kids(cs[k - 1]).isEmpty {
                frame(nil, "\(esc(nodes[cs[k - 1]].label)) has tried every later element, so it returns to \(label).",
                      "Returning undoes the last pick; the parent then tries its next element.")
            }
            visit(c)
        }
        if !cs.isEmpty { stack.removeLast(); tones[id] = .mirror }
    }
    visit(0)
    frame(nil, "All {v:\(total)} subsets are out, each exactly once.", "2ⁿ calls, one per subset, each copying up to n elements: O(n · 2ⁿ).")
    return [TTab(label: "Subsets", frames: frames, legend: callLegend + [(Color.gray.opacity(0.25), .fill, "Not called"), (SimColors.answer, .fill, "Recorded")])]
}

private func backtrackTabs(_ target: Int) -> [TTab] {
    let cands = [3, 4, 5]
    enum Ev { case visit(Int), prune(Int), ret(Int) }
    var nodes: [TNode] = [], paths: [[Int]] = [], needs: [Int] = [], events: [Ev] = []
    func fmt(_ p: [Int]) -> String { p.isEmpty ? "[ ]" : "[" + p.map(String.init).joined(separator: ",") + "]" }
    func minus(_ v: Int) -> String { v < 0 ? "−\(-v)" : "\(v)" }
    func dfs(_ path: [Int], _ need: Int, _ start: Int, _ parent: Int?) {
        let id = nodes.count
        nodes.append(TNode(label: fmt(path), sub: "need \(need)", parent: parent)); paths.append(path); needs.append(need)
        events.append(.visit(id))
        if need == 0 { return }
        for j in start..<cands.count {
            let v = cands[j]
            if v > need {
                let p = nodes.count
                nodes.append(TNode(label: "+\(v) → \(minus(need - v))", parent: id)); paths.append(path + [v]); needs.append(need - v)
                events.append(.prune(p))
                break
            }
            dfs(path + [v], need - v, j, id)
        }
        events.append(.ret(id))
    }
    dfs([], target, 0, nil)
    var tones: [Int: TTone] = [:], stack: [Int] = [], visited: Set<Int> = []
    var frames: [TFrame] = [], answers: [String] = []
    func frame(_ cur: Int?, _ formula: [String], _ headline: String, _ body: String) {
        let visible = nodes.indices.filter { nodes[$0].parent.map { visited.contains($0) } ?? true }
        let map = Dictionary(uniqueKeysWithValues: visible.enumerated().map { ($1, $0) })
        var edges: [Int: TTone] = [:]
        for id in stack + (cur.map { [$0] } ?? []) where nodes[id].parent != nil { edges[map[id]!] = .active }
        let tree = TTree(nodes: visible.map { TNode(label: nodes[$0].label, sub: nodes[$0].sub, parent: nodes[$0].parent.map { map[$0]! }) },
                         tones: Dictionary(uniqueKeysWithValues: tones.compactMap { k, v in map[k].map { ($0, v) } }), edges: edges)
        frames.append(TFrame(columns: 0, rows: [], formula: formula, chips: [StoryChip("found", answers.isEmpty ? "–" : answers.joined(separator: " "), answers.isEmpty ? .idle : .answer)],
                             headline: headline, body: body, tree: tree))
    }
    func stackText(_ top: Int, _ mark: String) -> String {
        "stack: " + (stack.map { fmt(paths[$0]) } + ["{\(mark)\(fmt(paths[top]))}"]).joined(separator: " → ")
    }
    for ev in events {
        switch ev {
        case .visit(let id):
            visited.insert(id)
            let need = needs[id]
            if need == 0 {
                answers.append(fmt(paths[id]))
                tones[id] = .answer
                frame(id, [stackText(id, "v:"), "· need 0 → record, return"], "\(fmt(paths[id])) brings need to 0, so it is recorded and the call returns.",
                      "Candidates are sorted, so once one overshoots 0 the rest of that branch is skipped.")
            } else {
                tones[id] = .active
                let tries = cands.suffix(from: paths[id].last.map { cands.firstIndex(of: $0)! } ?? 0)
                frame(id, [stackText(id, ""), "· need \(need) → try \(tries.map(String.init).joined(separator: ", "))"],
                      id == 0 ? "Start with an empty combination that still needs \(target)." : "Choose \(paths[id].last!): \(fmt(paths[id])) still needs \(need).",
                      "Each call only reuses candidates from its own on, so [4,3] never repeats [3,4].")
                tones[id] = .stack
                stack.append(id)
            }
        case .prune(let id):
            visited.insert(id)
            tones[id] = .mismatch
            let parent = nodes[id].parent!
            frame(nil, ["{w:\(nodes[id].label)} → below 0 → prune" + (paths[id].last! < cands.last! ? ", skip larger" : "")],
                  "Adding \(paths[id].last!) to \(fmt(paths[parent])) overshoots, so this branch and every larger candidate are cut.",
                  "Candidates are sorted, so once one overshoots 0 the rest of that branch is skipped.")
        case .ret(let id):
            stack.removeLast()
            tones[id] = .mirror
            frame(nil, ["\(fmt(paths[id])) done → pop"], id == 0 ? "The root has tried every candidate: the search is over." : "\(fmt(paths[id])) has nothing left to try, so it returns.",
                  "Undoing the choice on return is what makes this backtracking: one path, rewound and reused.")
        }
    }
    frame(nil, ["combinations = {v:\(answers.count)}"], "{v:\(answers.count)} combination\(answers.count == 1 ? "" : "s") sum to \(target): \(answers.joined(separator: " and ")).",
          "Pruning on sorted candidates keeps the tree far smaller than every sequence of picks.")
    return [TTab(label: "Combination sum", frames: frames,
                 legend: callLegend + [(SimColors.answer, .fill, "Answer"), (SimColors.red, .fill, "Pruned"), (Color.gray.opacity(0.25), .fill, "Not called")])]
}

private func bstInorderTabs() -> [TTab] {
    let keys = [8, 4, 2, 1, 3, 6, 12, 10, 14]
    let parents: [Int?] = [nil, 0, 1, 2, 2, 1, 0, 6, 6]
    let left: [Int: Int] = [0: 1, 1: 2, 2: 3, 6: 7], right: [Int: Int] = [0: 6, 1: 5, 2: 4, 6: 8]
    let nodes = keys.enumerated().map { TNode(label: "\($1)", parent: parents[$0]) }
    let k = 4
    var tones: [Int: TTone] = [:], stack: [Int] = [], emitted: [Int] = []
    var frames: [TFrame] = []
    func frame(_ cur: Int?, _ formula: [String], _ headline: String, _ body: String, answer: Bool = false) {
        var edges: [Int: TTone] = [:]
        for id in stack where nodes[id].parent.map({ stack.contains($0) }) ?? false { edges[id] = .active }
        if let cur, let p = nodes[cur].parent, stack.contains(p) { edges[cur] = .active }
        let cells = (0..<keys.count).map { j in
            j < emitted.count ? TCell("\(keys[emitted[j]])", answer && j == emitted.count - 1 ? .answer : .green) : TCell("", .ghost)
        }
        frames.append(TFrame(columns: 0, rows: [TRow(title: "EMITTED · K = \(k)", cells: cells, spread: true, compact: true)], formula: formula,
                             chips: [StoryChip("stack", stack.isEmpty ? "–" : stack.map { "\(keys[$0])" }.joined(separator: " "))],
                             headline: headline, body: body, tree: TTree(nodes: nodes, tones: tones, edges: edges)))
    }
    frame(nil, ["push left spine · pop = next key in order"], "Find the \(k)th smallest key without visiting the whole tree.",
          "In-order is left, node, right. An explicit stack lets the walk stop the moment it has k keys.")
    var cur: Int? = 0
    func ord(_ i: Int) -> String { i == 1 ? "1st" : i == 2 ? "2nd" : i == 3 ? "3rd" : "\(i)th" }
    outer: while cur != nil || !stack.isEmpty {
        while let c = cur {
            tones[c] = .active
            frame(c, ["push {\(keys[c])} · go left"], left[c] != nil ? "Push \(keys[c]) and keep going left: smaller keys are further down." : "Push \(keys[c]); it has no left child.",
                  "The stack holds every ancestor still waiting for its turn.")
            tones[c] = .stack
            stack.append(c)
            cur = left[c]
        }
        let top = stack.removeLast()
        emitted.append(top)
        if emitted.count == k {
            tones[top] = .answer
            frame(top, ["\(ord(k)) key emitted → {v:\(keys[top])}", "· stack still holds \(stack.map { "\(keys[$0])" }.joined(separator: ", ")) → stop"],
                  "Left, node, right emits keys in sorted order, so the \(ord(k)) emitted key is the answer.",
                  "The walk stops here. \(keys.count - emitted.count - stack.count == 1 ? "One node is" : "\(["Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven"][keys.count - emitted.count - stack.count]) nodes are") never visited.", answer: true)
            break outer
        }
        tones[top] = .green
        frame(top, ["pop → emit {\(keys[top])} · \(emitted.count) of \(k)"],
              "\(keys[top]) has no unvisited left subtree, so pop it: the \(ord(emitted.count)) key in order.",
              right[top] != nil ? "Next the walk turns to its right subtree, \(keys[right[top]!])." : "No right child, so the next pop is its waiting ancestor.")
        cur = right[top]
    }
    frame(nil, ["O(h + k) with the stack · recursion can't easily stop"], "k = \(k) needed only \(emitted.count + stack.count) of \(keys.count) nodes.",
          "A recursive in-order walk would unwind through every frame; the explicit stack just stops.")
    return [TTab(label: "Kth smallest", frames: frames,
                 legend: [(SimColors.green, .fill, "Emitted"), (SimColors.active, .ring, "On the stack"), (SimColors.answer, .fill, "Answer"), (Color.gray.opacity(0.25), .fill, "Never visited")])]
}

private func exchangeTabs() -> [TTab] {
    var jobs = [4, 1, 7, 2]
    func wait(_ a: [Int]) -> Int { var t = 0, s = 0; for d in a { t += d; s += t }; return s }
    func finishes(_ a: [Int]) -> String { var t = 0; return a.map { t += $0; return "\(t)" }.joined(separator: " + ") }
    func order(_ a: [Int]) -> String { a.map(String.init).joined(separator: " ") }
    var frames = [TFrame(columns: 0, rows: [], formula: ["total wait = \(finishes(jobs)) = {\(wait(jobs))}"], chips: [StoryChip("total wait", "\(wait(jobs))")],
                         headline: "Jobs run back to back, and each one waits for everything before it.",
                         body: "The greedy guess: shortest first. The exchange argument proves it.",
                         gantt: [TGanttRow(title: "ORDER · \(order(jobs))", note: "Σ \(wait(jobs))", jobs: jobs.map { ($0, .idle) })])]
    var swapped = true
    while swapped {
        swapped = false
        for i in 0..<(jobs.count - 1) where jobs[i] > jobs[i + 1] {
            let before = jobs, (a, b) = (jobs[i], jobs[i + 1])
            jobs.swapAt(i, i + 1)
            swapped = true
            frames.append(TFrame(columns: 0, rows: [], formula: ["swap ( {\(a)} , {\(b)} ): \(a) delays \(b) by \(a), \(b) delays \(a) by \(b) → saves {m:\(a - b)}"],
                                 chips: [StoryChip("total wait", "\(wait(before)) → \(wait(jobs))")],
                                 headline: "Putting the shorter job first saves \(a) − \(b) = \(a - b), and no other job's wait changes.",
                                 body: "Any longer-before-shorter pair can be swapped for a gain, so shortest-first is optimal: \(order(jobs.sorted())) gives \(wait(jobs.sorted())).",
                                 gantt: [TGanttRow(title: "BEFORE · \(order(before))", note: "Σ \(wait(before))", jobs: before.enumerated().map { ($1, $0 == i || $0 == i + 1 ? .active : .idle) }),
                                         TGanttRow(title: "AFTER SWAP · \(order(jobs))", note: "Σ \(wait(jobs))", jobs: jobs.enumerated().map { ($1, $0 == i || $0 == i + 1 ? .green : .idle) })]))
        }
    }
    frames.append(TFrame(columns: 0, rows: [], formula: ["no longer-before-shorter pair left → {v:\(wait(jobs))}"], chips: [StoryChip("total wait", "\(wait(jobs))", .answer)],
                         headline: "No pair can be improved, so shortest-first, total {v:\(wait(jobs))}, is optimal.",
                         body: "The exchange argument: start from any order, swap toward the greedy one, never lose. Sorting is O(n log n).",
                         gantt: [TGanttRow(title: "SHORTEST FIRST · \(order(jobs))", note: "Σ \(wait(jobs))", jobs: jobs.map { ($0, .green) })]))
    return [TTab(label: "Exchange", frames: frames, legend: [(SimColors.active, .fill, "Swapped pair"), (SimColors.green, .fill, "After swap"), (Color.gray.opacity(0.35), .fill, "Untouched")])]
}

private func liftingTabs() -> [TTab] {
    let names = ["A", "B", "C", "D", "E", "F", "G"]
    let parent: [Int?] = [nil, 0, 1, 2, 3, 0, 5]
    let nodes = names.enumerated().map { TNode(label: $1, parent: parent[$0]) }
    let pos: [(Double, Int)] = [(0.3, 0), (0.3, 1), (0.3, 2), (0.3, 3), (0.3, 4), (0.75, 1), (0.75, 2)]
    let levels = 3
    var up = [[Int?]](repeating: [Int?](repeating: nil, count: names.count), count: levels)
    up[0] = parent
    for k in 1..<levels { for v in names.indices { up[k][v] = up[k - 1][v].flatMap { up[k - 1][$0] } } }
    func table(filled: Int, marks: [String: TTone] = [:]) -> [TRow] {
        (0..<levels).map { k in
            TRow(label: "2\(sup(k))", cells: names.indices.map { v in
                if k >= filled { return TCell("", .ghost) }
                let tone = marks["\(k),\(v)"]
                return up[k][v].map { TCell(names[$0], tone ?? .idle) } ?? TCell("–", .pending)
            })
        }
    }
    let heading = "UP[K][V] · 2ᴷ-TH ANCESTOR"
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.blue, .fill, "Start"), (SimColors.green, .fill, "Jump 2¹"), (SimColors.active, .fill, "Jump 2⁰"), (SimColors.answer, .fill, "Answer")]
    func f(_ rows: [TRow], _ tree: TTree, _ formula: [String], _ headline: String, _ body: String) -> TFrame {
        TFrame(columns: names.count, rows: rows, formula: formula, headline: headline, body: body, heading: heading, tree: tree, headers: names)
    }
    func tree(_ tones: [Int: TTone] = [:], arcs: [TArc] = []) -> TTree { TTree(nodes: nodes, tones: tones, arcs: arcs, pos: pos, base: .idle) }
    let (A, B, C, D, E, F, G) = (0, 1, 2, 3, 4, 5, 6)
    var frames: [TFrame] = [
        f(table(filled: 0), tree(), ["up[k][v] = the 2ᵏ-th ancestor of v"], "Store each node's 1st, 2nd and 4th ancestor.",
          "Any jump of k levels is then a handful of power-of-two jumps, one per set bit of k."),
        f(table(filled: 1), tree(), ["up[0][v] = parent(v)"], "Row 2⁰ is each node's parent.", "The root has none, so its entry is empty."),
        f(table(filled: 2, marks: ["0,\(E)": .active, "0,\(D)": .active, "1,\(E)": .green]), tree([E: .blue, D: .active, C: .green]),
          ["up[1][v] = up[0][up[0][v]]", "up[1][E] = up[0][D] = {m:C}"], "Row 2¹ doubles row 2⁰: two parent hops.", "Each row is built from the one above it in O(n)."),
        f(table(filled: 3, marks: ["1,\(E)": .active, "1,\(C)": .active, "2,\(E)": .green]), tree([E: .blue, C: .active, A: .green]),
          ["up[2][v] = up[1][up[1][v]]", "up[2][E] = up[1][C] = {m:A}"], "Row 2² jumps four levels by chaining two 2¹ jumps.", "log n rows in all, so the table is O(n log n)."),
        f(table(filled: 3, marks: ["1,\(E)": .green, "0,\(C)": .active]),
          tree([E: .blue, D: .pending, C: .active, B: .answer, F: .pending, G: .pending],
               arcs: [TArc(from: E, to: C, label: "2¹", tone: .green, right: true), TArc(from: C, to: B, label: "2⁰", tone: .active, right: false)]),
          ["k = 3 = 0b11 → up[1][E] = {m:C}", "→ up[0][C] = {v:B}"], "The 3rd ancestor of E takes two table reads, not three steps.",
          "Each set bit of k is one jump. Building the table costs O(n log n) once; every query after is O(log n)."),
        f(table(filled: 3, marks: ["1,\(E)": .green]), tree([E: .blue, D: .pending, C: .active, G: .blue], arcs: [TArc(from: E, to: C, label: "2¹", tone: .green, right: true)]),
          ["LCA(E, G): depth 4 vs 2 → lift E by 2 = 0b10", "up[1][E] = {m:C}"], "For LCA(E, G), first lift E to G's depth.",
          "Both nodes must sit on the same level before they can climb together."),
        f(table(filled: 3, marks: ["1,\(C)": .pending, "1,\(G)": .pending, "0,\(C)": .active, "0,\(G)": .active]),
          tree([C: .blue, G: .blue, B: .active, F: .active, D: .pending, E: .pending],
               arcs: [TArc(from: C, to: B, label: "2⁰", tone: .active, right: false), TArc(from: G, to: F, label: "2⁰", tone: .active, right: true)]),
          ["up[1]: A = A → skip", "up[0]: B ≠ F → jump both"], "Try big jumps first: 2¹ lands both on A, too far, so skip it. 2⁰ lands on B and F, still different, so jump.",
          "Jump only while the ancestors differ; the nodes stop just below the LCA."),
        f(table(filled: 3, marks: ["0,\(B)": .answer]), tree([B: .active, F: .active, A: .answer, C: .pending, D: .pending, E: .pending, G: .pending]),
          ["LCA(E, G) = up[0][B] = {v:A}"], "B and F are children of the same node, so LCA(E, G) = {v:A}.", "O(log n) jumps per query after the O(n log n) table."),
        f(table(filled: 3), tree([A: .answer]), ["build O(n log n) · query O(log n)"], "Binary lifting answers k-th ancestor and LCA with power-of-two jumps.",
          "For a static tree with many queries, the table pays for itself quickly."),
    ]
    return [TTab(label: "Lifting", frames: frames, legend: legend)]
}

private func pathSumTabs() -> [TTab] {
    let vals = [5, 4, 11, 7, 2, 8, 13, 4, 5, 1]
    let parent: [Int?] = [nil, 0, 1, 2, 2, 0, 5, 5, 7, 7]
    let nodes = vals.enumerated().map { TNode(label: "\($1)", parent: parent[$0]) }
    let target = 22
    func kids(_ i: Int) -> [Int] { nodes.indices.filter { parent[$0] == i } }
    func minus(_ v: Int) -> String { v < 0 ? "−\(-v)" : "\(v)" }
    func run(all: Bool) -> [TFrame] {
        var tones: [Int: TTone] = [:], badges: [Int: (String, TTone)] = [:], path: [Int] = []
        var frames: [TFrame] = [], found: [[Int]] = []
        func pathText(_ p: [Int]) -> String { p.map { "\(vals[$0])" }.joined(separator: " → ") }
        func frame(_ formula: [String], _ headline: String, _ body: String) {
            var edges: [Int: TTone] = [:]
            for id in path where parent[id] != nil { edges[id] = tones[id] == .answer ? .answer : .blue }
            let rows = all ? [TRow(title: "FOUND PATHS", cells: found.isEmpty ? [TCell("", .ghost)] : found.map { TCell($0.map { "\(vals[$0])" }.joined(separator: "·"), .answer) }, spread: true)] : []
            frames.append(TFrame(columns: 0, rows: rows, formula: formula, headline: headline, body: body, tree: TTree(nodes: nodes, tones: tones, badges: badges, edges: edges)))
        }
        frame(["target \(target) · pass what's left down to each child"], all ? "Collect every root-to-leaf path that sums to \(target)." : "Is there a root-to-leaf path that sums to \(target)?",
              "Each node subtracts its value and hands the rest to its children.")
        func dfs(_ id: Int, _ left: Int) -> Bool {
            let rem = left - vals[id]
            path.append(id)
            let cs = kids(id)
            if cs.isEmpty {
                if rem == 0 {
                    tones[id] = .answer; badges[id] = ("0", .answer); found.append(path)
                    frame(["target \(target) · at leaf {v:\(vals[id])} : \(left) − \(vals[id]) = {v:0}", "→ path \(pathText(path))"],
                          "Leaf \(vals[id]) uses up exactly what was left, so \(pathText(path)) sums to \(target).",
                          all ? "Record a copy of the path and keep searching the other branches." : "Each node passes what's left down; the rest of the tree is never touched.")
                    if !all { return true }
                } else {
                    tones[id] = .mismatch; badges[id] = (minus(rem), .mismatch)
                    frame(["leaf {w:\(vals[id])} : \(left) − \(vals[id]) = {w:\(minus(rem))} ≠ 0 → return"],
                          "Leaf \(vals[id]) leaves \(minus(rem)), not 0, so this path fails.", "A leaf is the only place a path can succeed or fail.")
                }
                path.removeLast()
                return false
            }
            tones[id] = .blue; badges[id] = ("\(rem)", .blue)
            frame(["\(left) − {\(vals[id])} = {p:\(rem)} left for the rest"], id == 0 ? "Start at \(vals[id]): \(rem) is left for the rest of the path." : "Go down to \(vals[id]): \(rem) is left.",
                  "The remaining sum travels down with the call, so no path is summed twice.")
            for c in cs {
                if dfs(c, rem) { return true }
            }
            path.removeLast()
            tones[id] = .idle; badges[id] = nil
            if let p = parent[id] {
                frame(["\(vals[id]) done → back to {\(vals[p])}"], "Both branches under \(vals[id]) are done, so return to \(vals[p]).",
                      "Backtracking pops the node off the path before trying the next branch.")
            }
            return false
        }
        let hit = dfs(0, target)
        if all {
            frame(["paths = {v:\(found.count)}"], "{v:\(found.count)} paths sum to \(target): \(found.map(pathText).joined(separator: " and ")).",
                  "Every node is visited once, but copying each found path costs O(h), so O(n · h) in the worst case.")
        } else {
            frame(["hasPathSum = {v:\(hit)}"], "true travels back up the stack and the search stops at once.",
                  "The right subtree is never touched. O(n) in the worst case, O(h) stack.")
        }
        return frames
    }
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.blue, .fill, "Path"), (SimColors.answer, .fill, "Found"), (SimColors.red, .fill, "Dead end"), (Color.gray.opacity(0.25), .fill, "Not visited")]
    return [TTab(label: "Has path", frames: run(all: false), legend: legend), TTab(label: "All paths", frames: run(all: true), legend: legend)]
}

// MARK: - Graph and grid pattern storyboards
// Multi-source BFS, game theory DP, islands, DAG DP, two-colouring, topological sort and weighted shortest paths.

private let gridDirs = [(-1, 0), (1, 0), (0, -1), (0, 1)]

private func multiSourceTabs() -> [TTab] {
    let rows = 6, cols = 8
    let walls: Set<Int> = [0 * cols + 3, 1 * cols + 3, 3 * cols + 5, 4 * cols + 5, 5 * cols + 5]
    let sources = [(2, 0, "S"), (2, 7, "G")]
    var dist = [Int](repeating: -1, count: rows * cols)
    var queue: [Int] = []
    for (r, c, _) in sources { dist[r * cols + c] = 0; queue.append(r * cols + c) }
    var head = 0
    while head < queue.count {
        let cur = queue[head]; head += 1
        for (dr, dc) in gridDirs {
            let r = cur / cols + dr, c = cur % cols + dc
            guard r >= 0, r < rows, c >= 0, c < cols else { continue }
            let k = r * cols + c
            if walls.contains(k) || dist[k] >= 0 { continue }
            dist[k] = dist[cur] + 1
            queue.append(k)
        }
    }
    let open = rows * cols - walls.count
    let maxD = dist.max()!
    func grid(_ minute: Int) -> [TRow] {
        (0..<rows).map { r in
            TRow(cells: (0..<cols).map { c in
                let k = r * cols + c
                if walls.contains(k) { return TCell("", .pending) }
                if let s = sources.first(where: { $0.0 == r && $0.1 == c }) { return TCell(s.2, .answer) }
                let d = dist[k]
                if d == minute { return TCell("\(d)", .active) }
                if d < minute { return TCell("\(d)", .outline) }
                if d == minute + 1 { return TCell("", .frontier) }
                return TCell("", .idle)
            })
        }
    }
    func reached(_ m: Int) -> Int { dist.filter { $0 >= 0 && $0 <= m }.count }
    func count(_ m: Int) -> Int { dist.filter { $0 == m }.count }
    var frames = [TFrame(columns: cols, rows: grid(0), formula: ["queue starts [ {v:S} , {v:G} ] at 0"],
                         chips: [StoryChip("minute", "0"), StoryChip("reached", "2 / \(open)")],
                         headline: "Both sources go into the queue before the loop starts.",
                         body: "Seeding the queue with both sources is the only change. The loop is plain BFS.", noHeader: true)]
    for m in 1...maxD {
        frames.append(TFrame(columns: cols, rows: grid(m), formula: ["queue starts [ {v:S} , {v:G} ] at 0 · minute \(m) reaches {\(count(m))} cells"],
                             chips: [StoryChip("minute", "\(m)"), StoryChip("reached", "\(reached(m)) / \(open)", reached(m) == open ? .answer : .idle)],
                             headline: m == 1 ? "Minute 1: every open neighbour of S or G joins the wave."
                                 : m == maxD ? "Minute \(m) reaches the last open cells: every cell is filled."
                                 : "Both waves grow at once, and each cell keeps the minute the nearer source reached it.",
                             body: m == maxD ? "The answer is \(maxD) minutes, the largest distance from the nearest source."
                                 : "Seeding the queue with both sources is the only change. The loop is plain BFS.", noHeader: true))
    }
    frames.append(TFrame(columns: cols, rows: grid(maxD), formula: ["max distance to nearest source = {v:\(maxD)}"],
                         chips: [StoryChip("minutes", "\(maxD)", .answer)],
                         headline: "Every cell is reached after {v:\(maxD)} minutes.",
                         body: "One BFS from all sources is O(rows × cols), not one BFS per source.", noHeader: true))
    return [TTab(label: "Waves", frames: frames, legend: [(SimColors.answer, .fill, "Source"), (SimColors.blue, .fill, "Earlier minute"), (SimColors.active, .fill, "This minute"),
                                                           (SimColors.blue, .ring, "Next wave"), (Color.gray.opacity(0.15), .fill, "Wall")])]
}

private func gameTheoryTabs() -> [TTab] {
    let piles = [3, 9, 1, 2], n = piles.count
    var dp = [[Int?]](repeating: [Int?](repeating: nil, count: n), count: n)
    func m(_ v: Int) -> String { v < 0 ? "−\(-v)" : "\(v)" }
    func raw(_ v: Int) -> String { v < 0 ? "-\(-v)" : "\(v)" }
    func table(reads: [(Int, Int)] = [], answer: (Int, Int)? = nil) -> [TRow] {
        (0..<n).map { i in
            TRow(label: "i \(i)", cells: (0..<n).map { j in
                guard let v = dp[i][j] else { return TCell("", .pending) }
                if let a = answer, a == (i, j) { return TCell(raw(v), .answer) }
                if reads.contains(where: { $0 == (i, j) }) { return TCell(raw(v), .active) }
                return TCell(raw(v), v >= 0 ? .green : .mismatch)
            })
        }
    }
    func pileRow(_ lo: Int?, _ hi: Int?, taken: Int?) -> TRow {
        TRow(title: "PILES", cells: piles.enumerated().map { k, p in TCell("\(p)", k == taken ? .active : lo.map { k >= $0 && k <= hi! } ?? true ? .idle : .pending) }, spread: true)
    }
    let heading = "DP[I][J] · MOVER'S LEAD ON PILES I..J", headers = (0..<n).map { "j \($0)" }
    func f(_ rows: [TRow], _ pile: TRow, _ formula: [String], _ chips: [StoryChip], _ headline: String, _ body: String) -> TFrame {
        TFrame(columns: n, rows: rows, formula: formula, chips: chips, headline: headline, body: body, heading: heading, headers: headers, topRows: [pile])
    }
    var frames = [f(table(), pileRow(nil, nil, taken: nil), ["dp[i][j] = max(a[i] − dp[i+1][j], a[j] − dp[i][j−1])"], [],
                    "Two players take a pile from either end. How far ahead can the first player finish?",
                    "dp[i][j] is the lead the player to move can force on piles i..j.")]
    for i in 0..<n { dp[i][i] = piles[i] }
    frames.append(f(table(), pileRow(nil, nil, taken: nil), ["dp[i][i] = a[i]"], [], "With one pile left, the mover takes it: the lead is the pile itself.",
                    "The diagonal is the base case; longer ranges build on shorter ones."))
    for i in 0..<(n - 1) { dp[i][i + 1] = max(piles[i] - piles[i + 1], piles[i + 1] - piles[i]) }
    frames.append(f(table(), pileRow(nil, nil, taken: nil), ["dp[i][i+1] = |a[i] − a[i+1]|"], [], "With two piles, take the bigger one; the lead is the difference.",
                    "Each cell is the lead the player to move can force, so the opponent's best reply is subtracted."))
    for len in 3...n {
        for i in 0...(n - len) {
            let j = i + len - 1
            let left = piles[i] - dp[i + 1][j]!, right = piles[j] - dp[i][j - 1]!
            let best = max(left, right), takeLeft = left >= right
            let reads = [(i + 1, j), (i, j - 1)]
            let done = len == n
            let formula = ["take \(piles[i]): \(piles[i]) − {\(m(dp[i + 1][j]!))} = \(m(left)) · take \(piles[j]): \(piles[j]) − ( {\(m(dp[i][j - 1]!))} ) = {v:\(m(right))}"]
                .map { takeLeft ? $0.replacingOccurrences(of: "{v:\(m(right))}", with: m(right)) + " → {v:\(m(best))}" : $0 }
            let (chosen, other) = takeLeft ? (piles[i], piles[j]) : (piles[j], piles[i])
            let worth = takeLeft ? dp[i + 1][j]! : dp[i][j - 1]!
            dp[i][j] = best
            frames.append(f(table(reads: reads, answer: done ? (i, j) : nil), pileRow(i, j, taken: takeLeft ? i : j), formula,
                            done ? [StoryChip("first player wins by", m(best), .answer)] : [],
                            best >= 0 ? "Taking \(chosen), not \(other), \(done ? "wins by" : "leads by") \(m(best)): it leaves the opponent a position worth \(m(worth))."
                                : "Piles \(i)..\(j): even the better take, \(chosen), trails by \(m(-best)).",
                            "Each cell is the lead the player to move can force, so the opponent's best reply is subtracted."))
        }
    }
    return [TTab(label: "Piles", frames: frames, legend: [(SimColors.green, .fill, "Mover ahead"), (SimColors.red, .fill, "Mover behind"), (SimColors.active, .fill, "Read now"), (SimColors.answer, .fill, "Answer")])]
}

private func islandsTabs() -> [TTab] {
    let map = ["11000110", "10010110", "00110000", "00100011", "10010011", "11001100"].map { $0.map { $0 == "1" } }
    let rows = map.count, cols = map[0].count
    var island = [Int](repeating: 0, count: rows * cols)
    var count = 0
    var frames: [TFrame] = []
    let totalLand = map.flatMap { $0 }.filter { $0 }.count
    func grid(_ cur: Int?, queued: Set<Int>, current: Int) -> [TRow] {
        (0..<rows).map { r in
            TRow(cells: (0..<cols).map { c in
                let k = r * cols + c
                if !map[r][c] { return TCell("", .pending) }
                if k == cur { return TCell("\(island[k])", .active) }
                if queued.contains(k) { return TCell("", .frontier) }
                if island[k] == 0 { return TCell("", .idle) }
                return TCell("\(island[k])", island[k] == current ? .outline : .green)
            })
        }
    }
    func left() -> Int { totalLand - island.filter { $0 > 0 }.count }
    frames.append(TFrame(columns: cols, rows: grid(nil, queued: [], current: 0), formula: ["scan cells · unvisited land starts a flood"],
                         chips: [StoryChip("islands", "0"), StoryChip("land left", "\(totalLand)")],
                         headline: "Count the islands: groups of land joined up, down, left or right.",
                         body: "Scan every cell; the first unvisited land cell of each island starts a flood fill.", noHeader: true))
    for start in 0..<(rows * cols) where map[start / cols][start % cols] && island[start] == 0 {
        count += 1
        island[start] = count
        var queue = [start], head = 0
        var queued: Set<Int> = [start]
        while head < queue.count {
            let cur = queue[head]; head += 1
            queued.remove(cur)
            var added: [Int] = []
            for (dr, dc) in gridDirs {
                let r = cur / cols + dr, c = cur % cols + dc
                guard r >= 0, r < rows, c >= 0, c < cols, map[r][c] else { continue }
                let k = r * cols + c
                if island[k] != 0 { continue }
                island[k] = count
                queue.append(k); queued.insert(k); added.append(k)
            }
            let pos = "(\(cur / cols),\(cur % cols))"
            let addText = added.isEmpty ? "no new land" : added.map { "(\($0 / cols),\($0 % cols))" }.joined(separator: " ") + " {p:queued}"
            frames.append(TFrame(columns: cols, rows: grid(cur, queued: queued, current: count), formula: ["\(pos) land → mark {\(count)} · neighbour \(addText)"],
                                 chips: [StoryChip("islands", "\(count)"), StoryChip("land left", "\(left())")],
                                 headline: cur == start ? "\(pos) is unvisited land, so island \(count) starts here. The flood marks everything connected to it."
                                     : "\(pos) belongs to island \(count)\(added.isEmpty ? "; nothing new around it." : ", and its land neighbours join the queue.")",
                                 body: "Every cell is visited once, so the count is O(rows × cols).", noHeader: true))
        }
    }
    frames.append(TFrame(columns: cols, rows: grid(nil, queued: [], current: 0), formula: ["islands = {v:\(count)}"], chips: [StoryChip("islands", "\(count)", .answer)],
                         headline: "The scan is done: {v:\(count)} islands.", body: "Marking cells as visited is what stops a flood from counting an island twice.", noHeader: true))
    return [TTab(label: "Flood fill", frames: frames, legend: [(SimColors.green, .fill, "Counted"), (SimColors.blue, .fill, "This island"), (SimColors.active, .fill, "Visiting"),
                                                                (SimColors.blue, .ring, "Queued"), (Color.gray.opacity(0.35), .fill, "Unvisited land")])]
}

private func dagDpTabs() -> [TTab] {
    let nodes = [TGNode(label: "A", x: 0.05, y: 0.05), TGNode(label: "B", x: 0.05, y: 0.95), TGNode(label: "C", x: 0.4, y: 0.5),
                 TGNode(label: "D", x: 0.7, y: 0.05), TGNode(label: "E", x: 0.7, y: 0.95), TGNode(label: "F", x: 0.97, y: 0.5)]
    let edges = [(0, 2, 3), (1, 2, 6), (2, 3, 4), (2, 4, 2), (3, 5, 5), (4, 5, 9)]
    let names = nodes.map(\.label)
    func run(longest: Bool) -> [TFrame] {
        var dp = [Int?](repeating: nil, count: nodes.count)
        var from = [Int?](repeating: nil, count: nodes.count)
        dp[0] = 0; dp[1] = 0
        var done = Set<Int>()
        let inf = longest ? "−∞" : "∞"
        func graph(_ cur: Int?, improved: [Int: Int?]) -> TGraph {
            var tones: [Int: TTone] = [:], caps: [Int: (String, TTone)] = [:]
            for i in nodes.indices {
                if i == cur { tones[i] = .active; caps[i] = ("\(dp[i]!)", .active) }
                else if let old = improved[i] { tones[i] = .outline; caps[i] = ("\(old.map(String.init) ?? inf) → \(dp[i]!)", .blue) }
                else if done.contains(i) { tones[i] = .green; caps[i] = ("\(dp[i]!)", .green) }
                else { caps[i] = (dp[i].map(String.init) ?? inf, .idle) }
            }
            let es = edges.map { e -> TGEdge in
                let tone: TTone? = e.0 == cur ? .active : from[e.1] == e.0 ? .blue : nil
                return TGEdge(a: e.0, b: e.1, weight: "\(e.2)", tone: tone)
            }
            return TGraph(nodes: nodes, edges: es, tones: tones, captions: caps)
        }
        func order(_ cur: Int?, improved: [Int: Int?]) -> TRow {
            TRow(title: "TOPOLOGICAL ORDER", cells: names.indices.map { i in TCell(names[i], i == cur ? .active : done.contains(i) ? .green : improved[i] != nil ? .outline : .idle) }, spread: true)
        }
        var frames = [TFrame(columns: 0, rows: [order(nil, improved: [:])], formula: ["dp[v] = \(longest ? "max" : "min") over edges u → v of dp[u] + w"],
                             headline: "The \(longest ? "longest" : "shortest") path to every node, reading nodes in topological order.",
                             body: "A and B have no incoming edges, so they start at 0.", graph: graph(nil, improved: [:]))]
        for u in nodes.indices {
            var improved: [Int: Int?] = [:]
            var lines: [String] = []
            for e in edges where e.0 == u {
                let cand = dp[u]! + e.2
                let old = dp[e.1]
                let better = old.map { longest ? cand > $0 : cand < $0 } ?? true
                if better { improved[e.1] = old; dp[e.1] = cand; from[e.1] = u }
                let cmp = old.map { better ? (longest ? " > \($0)" : " < \($0)") : (longest ? " ≤ \($0)" : " ≥ \($0)") } ?? ""
                lines.append("dp[\(names[u])] {\(dp[u]!)} + \(e.2) = \(cand)\(cmp) → " + (better ? "dp[\(names[e.1])] = {p:\(cand)}" : "keep \(old!)"))
            }
            let outs = edges.filter { $0.0 == u }
            let headline: String
            if outs.isEmpty { headline = "\(names[u]) has no outgoing edges; its value \(dp[u]!) is final." }
            else if let (v, old) = improved.first(where: { $0.value != nil }), let o = old {
                headline = "Through \(names[u]), \(names[v]) reaches \(dp[v]!), \(longest ? "beating" : "undercutting") the \(o) it got through \(names[from[v] == u ? edges.first { $0.1 == v && $0.0 != u }!.0 : from[v]!])."
            } else if improved.isEmpty {
                headline = "Nothing through \(names[u]) is \(longest ? "longer" : "shorter"); every neighbour keeps its value."
            } else {
                headline = "\(names[u]) = \(dp[u]!) is final, so it passes \(improved.keys.sorted().map { "\(names[$0]) = \(dp[$0]!)" }.joined(separator: " and ")) along."
            }
            frames.append(TFrame(columns: 0, rows: [order(u, improved: improved)], formula: lines.isEmpty ? ["\(names[u]) is a sink → final"] : lines,
                                 headline: headline, body: "In topological order every predecessor is final before a node is read, so one pass is enough.",
                                 graph: graph(u, improved: improved)))
            done.insert(u)
        }
        var path = [5]
        while let p = from[path[0]] { path.insert(p, at: 0) }
        frames.append(TFrame(columns: 0, rows: [order(nil, improved: [:])], formula: ["dp[F] = {v:\(dp[5]!)} via \(path.map { names[$0] }.joined(separator: " → "))"],
                             headline: "The \(longest ? "longest" : "shortest") path to F is {v:\(dp[5]!)}: \(path.map { names[$0] }.joined(separator: " → ")).",
                             body: "O(V + E): each edge is relaxed exactly once. Longest path is only this easy on a DAG.", graph: graph(nil, improved: [:])))
        return frames
    }
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Relaxing from"), (SimColors.blue, .fill, "Improved"), (SimColors.green, .fill, "Final")]
    return [TTab(label: "Longest", frames: run(longest: true), legend: legend), TTab(label: "Shortest", frames: run(longest: false), legend: legend)]
}

private func twoColourTabs() -> [TTab] {
    let nodes = [TGNode(label: "A", x: 0.05, y: 0.05), TGNode(label: "B", x: 0.05, y: 0.95), TGNode(label: "C", x: 0.42, y: 0.5),
                 TGNode(label: "D", x: 0.72, y: 0.05), TGNode(label: "E", x: 0.72, y: 0.95), TGNode(label: "F", x: 0.97, y: 0.5)]
    let names = nodes.map(\.label)
    let all = [(0, 1), (0, 2), (1, 2), (2, 3), (2, 4), (3, 5), (4, 5)]
    var frames: [TFrame] = []
    func frame(_ side: [Int: Int], edges: [(Int, Int)], conflict: (Int, Int)? = nil, dropped: (Int, Int)? = nil, _ formula: [String], _ headline: String, _ body: String) {
        var es = edges.map { e in TGEdge(a: e.0, b: e.1, tone: conflict.map { $0 == e } ?? false ? .mismatch : nil, dashed: conflict.map { $0 == e } ?? false) }
        if let d = dropped { es.append(TGEdge(a: d.0, b: d.1, tone: nil, dashed: true)) }
        let tones = side.mapValues { $0 == 1 ? TTone.blue : TTone.rose }
        frames.append(TFrame(columns: 0, rows: [], formula: formula, headline: headline, body: body,
                             graph: TGraph(nodes: nodes, edges: es, directed: false, tones: tones)))
    }
    func bfs(_ edges: [(Int, Int)], dropped: (Int, Int)?) -> Bool {
        var side: [Int: Int] = [0: 1]
        frame(side, edges: edges, dropped: dropped, [dropped == nil ? "BFS from A · neighbours get the other side" : "without B – C · BFS from A again"],
              dropped == nil ? "Start BFS at A and put it on side 1." : "Drop the edge B – C and run the same BFS.",
              "BFS gives each neighbour the opposite side.")
        var queue = [0], head = 0
        while head < queue.count {
            let u = queue[head]; head += 1
            var newly: [Int] = []
            for (a, b) in edges where a == u || b == u {
                let v = a == u ? b : a
                if side[v] == nil { side[v] = 3 - side[u]!; queue.append(v); newly.append(v) }
            }
            if !newly.isEmpty {
                frame(side, edges: edges, dropped: dropped, ["\(names[u]) side \(side[u]!) → \(newly.map { names[$0] }.joined(separator: ", ")) side \(3 - side[u]!)"],
                      "\(newly.map { names[$0] }.joined(separator: " and ")) \(newly.count == 1 ? "is" : "are") next to \(names[u]), so \(newly.count == 1 ? "it goes" : "they go") on side \(3 - side[u]!).",
                      "BFS gives each neighbour the opposite side.")
            }
            for (a, b) in edges where (a == u || b == u) && side[a] == side[b] {
                frame(side, edges: edges, conflict: (a, b), dropped: dropped,
                      ["edge {w:\(names[a]) – \(names[b])}", ": both side \(side[a]!) → odd cycle A → \(names[a]) → \(names[b]) → A"],
                      "\(names[a]) and \(names[b]) both got side \(side[a]!), and they share an edge, so the graph is not bipartite.",
                      "BFS gives each neighbour the opposite side. A conflict only happens on an odd cycle, here the triangle A–B–C.")
                return false
            }
        }
        frame(side, edges: edges, dropped: dropped, ["every edge joins side 1 to side 2 → {v:bipartite}"],
              "Every edge now joins the two sides: without B – C the graph is {v:bipartite}.",
              "The remaining cycle C–D–F–E–C has even length, so it can alternate. O(V + E).")
        return true
    }
    _ = bfs(all, dropped: nil)
    _ = bfs(all.filter { $0 != (1, 2) }, dropped: (1, 2))
    return [TTab(label: "Two-colour", frames: frames,
                 legend: [(SimColors.blue, .fill, "Side 1"), (Color(hex: 0xE0457B), .fill, "Side 2"), (SimColors.red, .fill, "Conflict edge"), (Color.gray.opacity(0.25), .fill, "Not reached")])]
}

private func topoTabs() -> [TTab] {
    let nodes = [TGNode(label: "101", x: 0.0, y: 0.5), TGNode(label: "201", x: 0.38, y: 0.05), TGNode(label: "210", x: 0.38, y: 0.95),
                 TGNode(label: "301", x: 0.66, y: 0.5), TGNode(label: "330", x: 1.0, y: 0.05), TGNode(label: "401", x: 1.0, y: 0.95)]
    let names = nodes.map(\.label)
    let edges = [(0, 1), (0, 2), (1, 3), (2, 3), (3, 4), (3, 5)]
    func ord(_ i: Int) -> String { i == 1 ? "1st" : i == 2 ? "2nd" : i == 3 ? "3rd" : "\(i)th" }
    func kahn() -> [TFrame] {
        var indeg = nodes.indices.map { v in edges.filter { $0.1 == v }.count }
        var order: [Int] = [], queue = nodes.indices.filter { indeg[$0] == 0 }
        var frames: [TFrame] = []
        func graph(_ cur: Int?, dropped: [Int: Int]) -> TGraph {
            var tones: [Int: TTone] = [:], caps: [Int: (String, TTone)] = [:]
            for v in nodes.indices {
                if let k = order.firstIndex(of: v) { tones[v] = v == cur ? .active : .green; caps[v] = (ord(k + 1), v == cur ? .active : .green) }
                else if let old = dropped[v] { tones[v] = indeg[v] == 0 ? .outline : .idle; caps[v] = ("in \(old) → \(indeg[v])", .blue) }
                else if queue.contains(v) { tones[v] = .outline; caps[v] = ("in 0", .blue) }
                else { caps[v] = ("in \(indeg[v])", .idle) }
            }
            let es = edges.map { e in TGEdge(a: e.0, b: e.1, tone: e.0 == cur ? .active : order.contains(e.0) ? .green : nil) }
            return TGraph(nodes: nodes, edges: es, tones: tones, captions: caps)
        }
        func text(_ cur: Int?) -> String {
            "order " + order.map { $0 == cur ? "{\(names[$0])}" : names[$0] }.joined(separator: " ") + " · queue {p:\(queue.isEmpty ? "–" : queue.map { names[$0] }.joined(separator: " "))}"
        }
        frames.append(TFrame(columns: 0, rows: [], formula: [text(nil)], headline: "Count each course's prerequisites: 101 has none, so it starts in the queue.",
                             body: "A course is only queued once every prerequisite is taken.", graph: graph(nil, dropped: [:])))
        while !queue.isEmpty {
            let u = queue.removeFirst()
            order.append(u)
            var dropped: [Int: Int] = [:], ready: [Int] = []
            for e in edges where e.0 == u { dropped[e.1] = indeg[e.1]; indeg[e.1] -= 1; if indeg[e.1] == 0 { queue.append(e.1); ready.append(e.1) } }
            let headline = dropped.isEmpty ? "\(names[u]) unlocks nothing; it is the \(ord(order.count)) course taken."
                : ready.isEmpty ? "Taking \(names[u]) drops \(dropped.keys.sorted().map { names[$0] }.joined(separator: " and ")) to in-degree \(dropped.keys.sorted().map { "\(indeg[$0])" }.joined(separator: ", ")); still waiting."
                : "Taking \(names[u]) drops the in-degree of \(ready.map { names[$0] }.joined(separator: " and ")) to 0, so \(ready.count == 1 ? "it joins" : "both join") the queue."
            frames.append(TFrame(columns: 0, rows: [], formula: [text(u)], headline: headline,
                                 body: "A course is only queued once every prerequisite is taken. If the queue empties before every course is taken, there is a cycle.",
                                 graph: graph(u, dropped: dropped)))
        }
        frames.append(TFrame(columns: 0, rows: [], formula: ["order = {v:\(order.map { names[$0] }.joined(separator: " → "))}"],
                             headline: "All \(order.count) courses taken: {v:\(order.map { names[$0] }.joined(separator: " → "))}.",
                             body: "O(V + E): every course is queued once and every prerequisite edge is removed once.", graph: graph(nil, dropped: [:])))
        return frames
    }
    func dfsOrder() -> [TFrame] {
        var state = [Int](repeating: 0, count: nodes.count) // 0 new, 1 on stack, 2 done
        var finish: [Int] = []
        var frames: [TFrame] = []
        func graph(_ cur: Int?) -> TGraph {
            var tones: [Int: TTone] = [:], caps: [Int: (String, TTone)] = [:]
            for v in nodes.indices {
                if v == cur { tones[v] = .active }
                else if state[v] == 1 { tones[v] = .stack }
                else if state[v] == 2 { tones[v] = .green }
                if let k = finish.firstIndex(of: v) { caps[v] = ("done \(k + 1)", .green) }
            }
            let es = edges.map { e in TGEdge(a: e.0, b: e.1, tone: state[e.0] >= 1 && state[e.1] >= 1 ? (state[e.1] == 2 ? .green : .active) : nil) }
            return TGraph(nodes: nodes, edges: es, tones: tones, captions: caps)
        }
        func text() -> String { "finish " + (finish.isEmpty ? "–" : finish.map { names[$0] }.joined(separator: " ")) }
        func visit(_ u: Int) {
            state[u] = 1
            frames.append(TFrame(columns: 0, rows: [], formula: [text()], headline: "Enter \(names[u]) and follow its edges before finishing it.",
                                 body: "A course finishes only after everything that depends on it has finished.", graph: graph(u)))
            for e in edges where e.0 == u && state[e.1] == 0 { visit(e.1) }
            state[u] = 2
            finish.append(u)
            frames.append(TFrame(columns: 0, rows: [], formula: [text()], headline: "\(names[u]) has nothing unvisited left, so it finishes \(ord(finish.count)).",
                                 body: "Reversed finish order puts every course after its prerequisites. Meeting a node still on the stack means a cycle.", graph: graph(u)))
        }
        visit(0)
        let order = finish.reversed().map { names[$0] }.joined(separator: " → ")
        frames.append(TFrame(columns: 0, rows: [], formula: [text() + " → reverse → {v:\(order)}"], headline: "Reverse the finish order: {v:\(order)}.",
                             body: "Also O(V + E). Kahn's version is easier to stop early; the DFS one finds cycles as back edges.", graph: graph(nil)))
        return frames
    }
    return [TTab(label: "Kahn · in-degree", frames: kahn(), legend: [(SimColors.active, .fill, "Taken now"), (SimColors.blue, .fill, "Ready · in-degree 0"), (SimColors.green, .fill, "Taken")]),
            TTab(label: "DFS · finish order", frames: dfsOrder(), legend: [(SimColors.active, .fill, "Active"), (SimColors.active, .ring, "On the stack"), (SimColors.green, .fill, "Finished")])]
}

private func weightedPathTabs() -> [TTab] {
    let nodes = [TGNode(label: "S", x: 0.0, y: 0.5), TGNode(label: "A", x: 0.36, y: 0.05), TGNode(label: "B", x: 0.36, y: 0.95),
                 TGNode(label: "C", x: 0.68, y: 0.5), TGNode(label: "T", x: 1.0, y: 0.05)]
    let names = nodes.map(\.label)
    let edges = [(0, 1, 2), (0, 2, 5), (1, 3, 1), (1, 4, 6), (2, 3, 2), (3, 4, 2)]
    func dijkstra() -> [TFrame] {
        var dist = [Int?](repeating: nil, count: nodes.count), from = [Int?](repeating: nil, count: nodes.count)
        dist[0] = 0
        var heap: [(Int, Int)] = [(0, 0)]
        var done = Set<Int>()
        var frames: [TFrame] = []
        func graph(_ cur: Int?, improved: [Int: Int?]) -> TGraph {
            var tones: [Int: TTone] = [:], caps: [Int: (String, TTone)] = [:]
            for v in nodes.indices {
                if v == cur { tones[v] = .active; caps[v] = ("\(dist[v]!)", .active) }
                else if done.contains(v) { tones[v] = .green; caps[v] = ("\(dist[v]!)", .green) }
                else if let d = dist[v] {
                    tones[v] = .outline
                    caps[v] = (improved[v].flatMap { $0 }.map { "\($0) → \(d)" } ?? "\(d)", .blue)
                } else { caps[v] = ("∞", .idle) }
            }
            let es = edges.map { e in TGEdge(a: e.0, b: e.1, weight: "\(e.2)", tone: e.0 == cur ? .active : from[e.1] == e.0 && done.contains(e.0) ? .green : nil) }
            return TGraph(nodes: nodes, edges: es, tones: tones, captions: caps)
        }
        func heapRow() -> TRow {
            let sorted = heap.sorted { ($0.0, $0.1) < ($1.0, $1.1) }
            var cells = sorted.map { TCell("\($0.0) · \(names[$0.1])", dist[$0.1] != nil && dist[$0.1]! < $0.0 ? .mismatch : .outline) }
            while cells.count < 4 { cells.append(TCell("", .ghost)) }
            return TRow(title: "MIN-HEAP · DIST, NODE", cells: cells, spread: true)
        }
        frames.append(TFrame(columns: 0, rows: [heapRow()], formula: ["dist[S] = 0 · every other dist = ∞"], headline: "Find the cheapest route from S to every node.",
                             body: "The heap always hands back the closest node not yet settled.", graph: graph(nil, improved: [:])))
        while !heap.isEmpty {
            heap.sort { ($0.0, $0.1) < ($1.0, $1.1) }
            let (d, u) = heap.removeFirst()
            if done.contains(u) || d > dist[u]! {
                frames.append(TFrame(columns: 0, rows: [heapRow()], formula: ["pop ({w:\(d), \(names[u])}) → \(d) > dist[\(names[u])] \(dist[u]!) → skip"],
                                     headline: "The old (\(d), \(names[u])) entry comes out, but \(names[u]) is already settled at \(dist[u]!), so it is skipped.",
                                     body: "Instead of decreasing a key, push a new entry and ignore the stale one later.", graph: graph(nil, improved: [:])))
                continue
            }
            var improved: [Int: Int?] = [:], lines: [String] = []
            for e in edges where e.0 == u && !done.contains(e.1) {
                let cand = d + e.2
                let old = dist[e.1]
                if old == nil || cand < old! {
                    improved[e.1] = old; dist[e.1] = cand; from[e.1] = u; heap.append((cand, e.1))
                    lines.append("dist[\(names[u])] {\(d)} + \(e.2) = \(cand)\(old.map { " < \($0)" } ?? "") → dist[\(names[e.1])] = {p:\(cand)}")
                } else {
                    lines.append("dist[\(names[u])] {\(d)} + \(e.2) = \(cand) ≥ \(old!) → keep")
                }
            }
            done.insert(u)
            let drop = improved.first { $0.value != nil }
            let headline = drop.map { "Through \(names[u]), \(names[$0.key]) costs \(dist[$0.key]!) instead of \($0.value!), so its distance drops." }
                ?? (improved.isEmpty ? "\(names[u]) is settled at \(d); it improves nothing." : "Settle \(names[u]) at \(d) and reach \(improved.keys.sorted().map { "\(names[$0]) = \(dist[$0]!)" }.joined(separator: ", ")).")
            frames.append(TFrame(columns: 0, rows: [heapRow()], formula: lines.isEmpty ? ["settle \(names[u]) at {\(d)}"] : lines, headline: headline,
                                 body: drop != nil ? "The old (\(drop!.value!), \(names[drop!.key])) entry is skipped when popped." : "The smallest dist in the heap can't get any smaller, so popping it settles it.",
                                 graph: graph(u, improved: improved)))
        }
        var path = [4]
        while let p = from[path[0]] { path.insert(p, at: 0) }
        frames.append(TFrame(columns: 0, rows: [heapRow()], formula: ["dist[T] = {v:\(dist[4]!)} via \(path.map { names[$0] }.joined(separator: " → "))"],
                             headline: "The cheapest route to T costs {v:\(dist[4]!)}: \(path.map { names[$0] }.joined(separator: " → ")).",
                             body: "O((V + E) log V) with a binary heap. Negative edges would break it; use Bellman-Ford then.", graph: graph(nil, improved: [:])))
        return frames
    }
    func bfs() -> [TFrame] {
        var dist = [Int?](repeating: nil, count: nodes.count), from = [Int?](repeating: nil, count: nodes.count)
        dist[0] = 0
        var queue = [0], done = Set<Int>()
        var frames: [TFrame] = []
        func graph(_ cur: Int?) -> TGraph {
            var tones: [Int: TTone] = [:], caps: [Int: (String, TTone)] = [:]
            for v in nodes.indices {
                if v == cur { tones[v] = .active } else if done.contains(v) { tones[v] = .green } else if dist[v] != nil { tones[v] = .outline }
                caps[v] = (dist[v].map { "\($0)" } ?? "∞", v == cur ? .active : done.contains(v) ? .green : dist[v] != nil ? .blue : .idle)
            }
            let es = edges.map { e in TGEdge(a: e.0, b: e.1, weight: "1", tone: e.0 == cur ? .active : from[e.1] == e.0 && done.contains(e.0) ? .green : nil) }
            return TGraph(nodes: nodes, edges: es, tones: tones, captions: caps)
        }
        func queueRow() -> TRow {
            var cells = queue.map { TCell("\(dist[$0]!) · \(names[$0])", .outline) }
            while cells.count < 4 { cells.append(TCell("", .ghost)) }
            return TRow(title: "QUEUE · FIFO", cells: cells, spread: true)
        }
        frames.append(TFrame(columns: 0, rows: [queueRow()], formula: ["every edge costs 1 → a plain queue is enough"], headline: "With unit edges, the fewest hops is the shortest path.",
                             body: "BFS reaches nodes in order of distance, so no heap is needed.", graph: graph(nil)))
        while !queue.isEmpty {
            let u = queue.removeFirst()
            var reached: [Int] = []
            for e in edges where e.0 == u && dist[e.1] == nil { dist[e.1] = dist[u]! + 1; from[e.1] = u; queue.append(e.1); reached.append(e.1) }
            done.insert(u)
            frames.append(TFrame(columns: 0, rows: [queueRow()], formula: ["dequeue {\(names[u])} at \(dist[u]!)" + (reached.isEmpty ? " · nothing new" : " → \(reached.map { names[$0] }.joined(separator: ", ")) at {p:\(dist[u]! + 1)}")],
                                 headline: reached.isEmpty ? "\(names[u]) is \(dist[u]!) hop\(dist[u]! == 1 ? "" : "s") away and reaches nothing new."
                                     : "\(names[u]) reaches \(reached.map { names[$0] }.joined(separator: " and ")) at \(dist[u]! + 1) hop\(dist[u]! + 1 == 1 ? "" : "s").",
                                 body: "The first time BFS reaches a node is already its shortest distance.", graph: graph(u)))
        }
        frames.append(TFrame(columns: 0, rows: [queueRow()], formula: ["hops to T = {v:\(dist[4]!)}"], headline: "T is {v:\(dist[4]!)} hops away, via A.",
                             body: "O(V + E). Once edges have different weights, fewest hops is no longer cheapest: that is when Dijkstra is needed.", graph: graph(nil)))
        return frames
    }
    return [TTab(label: "Dijkstra", frames: dijkstra(), legend: [(SimColors.active, .fill, "Settling"), (SimColors.blue, .fill, "Reached"), (SimColors.green, .fill, "Final"), (SimColors.red, .fill, "Stale entry")]),
            TTab(label: "BFS · unit edges", frames: bfs(), legend: [(SimColors.active, .fill, "Dequeued"), (SimColors.blue, .fill, "Queued"), (SimColors.green, .fill, "Done")])]
}

// MARK: - Table and design pattern storyboards
// State machine DP, grid DP, interval DP, matrix rotation, XOR splitting, 2D prefix sums, the bit trie and LRU/LFU.

private func sgn(_ v: Int) -> String { v < 0 ? "−\(-v)" : "\(v)" }
private func dash(_ v: Int) -> String { v < 0 ? "-\(-v)" : "\(v)" }
private let fillLegend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Current"), (SimColors.blue, .fill, "Read"), (SimColors.green, .fill, "Filled")]

private func stateMachineTabs() -> [TTab] {
    let prices = [1, 2, 3, 0, 2], n = prices.count
    let modes = ["hold", "sold", "rest"]
    var v = [[Int?]](repeating: [Int?](repeating: nil, count: n), count: 3)
    var set = [[Bool]](repeating: [Bool](repeating: false, count: n), count: 3)
    let headers = prices.enumerated().map { "d\($0) · \($1)" }
    let flow: [[(String, Bool)]] = [[("rest", true), ("buy →", false), ("hold", true), ("sell →", false), ("sold", true), ("cooldown →", false)], [("rest", true)]]
    func grid(cur: (Int, Int)?, reads: [(Int, Int)], answer: (Int, Int)? = nil) -> [TRow] {
        (0..<3).map { m in
            TRow(label: modes[m], cells: (0..<n).map { d in
                guard set[m][d] else { return TCell("", .ghost) }
                let text = v[m][d].map(dash) ?? "–"
                if let a = answer, a == (m, d) { return TCell(text, .answer) }
                if let c = cur, c == (m, d) { return TCell(text, .active) }
                if reads.contains(where: { $0 == (m, d) }) { return TCell(text, .outline) }
                return TCell(text, .green)
            }, labelTone: cur?.0 == m ? .active : nil)
        }
    }
    func f(_ cur: (Int, Int)?, _ reads: [(Int, Int)], _ formula: [String], _ headline: String, _ body: String, answer: (Int, Int)? = nil, chips: [StoryChip] = []) -> TFrame {
        TFrame(columns: n, lit: cur.map { [$0.1: .active] } ?? [:], rows: grid(cur: cur, reads: reads, answer: answer), formula: formula, chips: chips,
               headline: headline, body: body, headers: headers, flow: flow)
    }
    var frames = [f(nil, [], ["hold = own a share · sold = sold today · rest = free to buy"], "Buy and sell with a one-day cooldown after each sale.",
                    "Track three states per day. Each state's best balance depends only on yesterday's.")]
    v[0][0] = -prices[0]; v[2][0] = 0
    for m in 0..<3 { set[m][0] = true }
    frames.append(f(nil, [], ["hold[0] = −\(prices[0]) · sold[0] = – · rest[0] = 0"], "Day 0: buy for \(prices[0]) or do nothing. Nothing can be sold yet.",
                    "The first column seeds the table."))
    for d in 1..<n {
        let p = prices[d]
        let keep = v[0][d - 1]!, buy = v[2][d - 1]! - p
        v[0][d] = max(keep, buy); set[0][d] = true
        frames.append(f((0, d), [(0, d - 1), (2, d - 1)], ["hold[\(d)] = max(hold {p:\(dash(keep))} , rest {p:\(dash(v[2][d - 1]!))} − price \(p)) = {\(dash(v[0][d]!))}"],
                        buy > keep ? "Buying on day \(d) reads yesterday's rest, never sold, which is how the cooldown is enforced."
                            : "Keeping yesterday's share (\(sgn(keep))) beats buying at \(p) from a rest of \(sgn(v[2][d - 1]!)).",
                        buy > keep ? "Price \(p) turns a rest balance of \(sgn(v[2][d - 1]!)) into a hold of \(sgn(buy)), better than keeping the \(sgn(keep)) share."
                            : "A buy always comes from rest, never from sold, so the day after a sale can't buy."))
        v[1][d] = v[0][d - 1]! + p; set[1][d] = true
        frames.append(f((1, d), [(0, d - 1)], ["sold[\(d)] = hold {p:\(dash(v[0][d - 1]!))} + price \(p) = {\(dash(v[1][d]!))}"],
                        "Selling on day \(d): yesterday's hold \(sgn(v[0][d - 1]!)) plus price \(p) gives \(sgn(v[1][d]!)).",
                        "Only a share held yesterday can be sold today."))
        let fromSold = v[1][d - 1] ?? Int.min
        v[2][d] = max(v[2][d - 1]!, fromSold); set[2][d] = true
        frames.append(f((2, d), [(2, d - 1)] + (v[1][d - 1] != nil ? [(1, d - 1)] : []),
                        ["rest[\(d)] = max(rest {p:\(dash(v[2][d - 1]!))} , sold {p:\(v[1][d - 1].map(dash) ?? "–")}) = {\(dash(v[2][d]!))}"],
                        "Resting keeps the best of yesterday's rest and yesterday's sale: \(sgn(v[2][d]!)).",
                        "Yesterday's sale lands here, in rest, one day later: that is the cooldown."))
    }
    let best = max(v[1][n - 1]!, v[2][n - 1]!)
    let bestMode = v[1][n - 1]! >= v[2][n - 1]! ? 1 : 2
    frames.append(f(nil, [], ["profit = max(sold, rest) on day \(n - 1) = {v:\(best)}"], "The best profit is {v:\(best)}: end not holding a share.",
                    "Three states × n days, each O(1): O(n) time, and only yesterday's column is needed.", answer: (bestMode, n - 1),
                    chips: [StoryChip("profit", "\(best)", .answer)]))
    return [TTab(label: "Cooldown", frames: frames, legend: fillLegend)]
}

private func gridDpTabs() -> [TTab] {
    let cost = [[1, 3, 1, 2], [1, 5, 1, 3], [4, 2, 1, 1]]
    let rows = cost.count, cols = cost[0].count
    let headers = (0..<cols).map { "c\($0)" }
    func run(paths: Bool) -> [TFrame] {
        let blocked: Set<Int> = paths ? [1 * cols + 1] : []
        var dp = [[Int?]](repeating: [Int?](repeating: nil, count: cols), count: rows)
        func grid(_ cur: (Int, Int)?, reads: [(Int, Int)], answer: Bool = false) -> [TRow] {
            (0..<rows).map { r in
                TRow(label: "r\(r)", cells: (0..<cols).map { c in
                    if blocked.contains(r * cols + c) { return TCell("×", .mismatch) }
                    guard let v = dp[r][c] else { return TCell(paths ? "" : "\(cost[r][c])", paths ? .ghost : .pending) }
                    if answer && r == rows - 1 && c == cols - 1 { return TCell("\(v)", .answer) }
                    if let x = cur, x == (r, c) { return TCell("\(v)", .active) }
                    if reads.contains(where: { $0 == (r, c) }) { return TCell("\(v)", .outline) }
                    return TCell("\(v)", .green)
                }, labelTone: cur?.0 == r ? .active : nil)
            }
        }
        var frames = [TFrame(columns: cols, rows: grid(nil, reads: []),
                             formula: [paths ? "dp[r][c] = ↑ + ← · a blocked cell is 0" : "dp[r][c] = cost + min(↑, ←)"],
                             headline: paths ? "Count the paths from the top-left to the bottom-right, moving only right or down." : "Find the cheapest path from the top-left to the bottom-right, moving only right or down.",
                             body: "Each cell reads only two finished neighbours, so the table fills row by row in O(rows × cols).", headers: headers)]
        for r in 0..<rows {
            for c in 0..<cols {
                if blocked.contains(r * cols + c) { dp[r][c] = 0; continue }
                let up = r > 0 ? dp[r - 1][c] : nil, left = c > 0 ? dp[r][c - 1] : nil
                var reads: [(Int, Int)] = []
                if up != nil && !blocked.contains((r - 1) * cols + c) { reads.append((r - 1, c)) }
                if left != nil && !blocked.contains(r * cols + c - 1) { reads.append((r, c - 1)) }
                let formula: String, headline: String
                if paths {
                    let v = r == 0 && c == 0 ? 1 : (up ?? 0) + (left ?? 0)
                    dp[r][c] = v
                    formula = r == 0 && c == 0 ? "dp[0][0] = {1}" : "dp[\(r)][\(c)] = ↑ {p:\(up ?? 0)} + ← {p:\(left ?? 0)} = {\(v)}"
                    headline = r == 0 && c == 0 ? "One way to stand at the start." : up == 0 || left == 0 ? "The blocked cell adds nothing, so \(v) path\(v == 1 ? "" : "s") arrive." : "Paths arrive from above or from the left: \(up ?? 0) + \(left ?? 0) = \(v)."
                } else {
                    let best = [up, left].compactMap { $0 }.min()
                    let v = cost[r][c] + (best ?? 0)
                    dp[r][c] = v
                    if let u = up, let l = left {
                        formula = "dp[\(r)][\(c)] = cost \(cost[r][c]) + min(↑ {p:\(u)} , ← {p:\(l)} ) = {\(v)}"
                        headline = "Coming from above costs \(u), from the left \(l). Take the cheaper and add this cell's \(cost[r][c])."
                    } else if let only = up ?? left {
                        formula = "dp[\(r)][\(c)] = cost \(cost[r][c]) + \(up != nil ? "↑" : "←") {p:\(only)} = {\(v)}"
                        headline = "On the \(up != nil ? "left edge" : "top row") there is only one way in, so add \(cost[r][c]) to \(only)."
                    } else {
                        formula = "dp[0][0] = cost {\(v)}"
                        headline = "The start costs its own \(v)."
                    }
                }
                frames.append(TFrame(columns: cols, lit: [c: .active], rows: grid((r, c), reads: reads), formula: [formula], headline: headline,
                                     body: "Each cell reads only two finished neighbours, so the table fills row by row in O(rows × cols).", headers: headers))
            }
        }
        let ans = dp[rows - 1][cols - 1]!
        frames.append(TFrame(columns: cols, rows: grid(nil, reads: [], answer: true), formula: ["dp[\(rows - 1)][\(cols - 1)] = {v:\(ans)}"],
                             chips: [StoryChip(paths ? "paths" : "min cost", "\(ans)", .answer)],
                             headline: paths ? "{v:\(ans)} paths reach the corner." : "The cheapest path costs {v:\(ans)}.",
                             body: "One row of the table is enough if memory matters: O(cols) space.", headers: headers))
        return frames
    }
    return [TTab(label: "Min path sum", frames: run(paths: false), legend: fillLegend + [(Color.gray.opacity(0.2), .fill, "Cost, not yet filled")]),
            TTab(label: "Unique paths", frames: run(paths: true), legend: fillLegend + [(SimColors.red, .fill, "Blocked")])]
}

private func intervalDpTabs() -> [TTab] {
    let a = [1, 3, 1, 5, 8, 1], n = a.count
    let rowsI = Array(0...(n - 3)), colsJ = Array(2..<n)
    var dp = [[Int?]](repeating: [Int?](repeating: nil, count: n), count: n)
    for i in 0..<(n - 1) { dp[i][i + 1] = 0 }
    let headers = colsJ.map { "j \($0)" }
    func table(_ cur: (Int, Int)?, reads: [(Int, Int)], answer: Bool = false) -> [TRow] {
        rowsI.map { i in
            TRow(label: "i \(i)", cells: colsJ.map { j in
                if j < i + 2 { return TCell("", .pending) }
                guard let v = dp[i][j] else { return TCell("", .ghost) }
                if answer && i == 0 && j == n - 1 { return TCell("\(v)", .answer) }
                if let c = cur, c == (i, j) { return TCell("\(v)", .active) }
                if reads.contains(where: { $0 == (i, j) }) { return TCell("\(v)", .outline) }
                return TCell("\(v)", .green)
            }, labelTone: cur?.0 == i ? .active : nil)
        }
    }
    func balloons(_ i: Int?, _ j: Int?, _ k: Int?) -> TRow {
        TRow(title: "BALLOONS · PADDED WITH 1", cells: a.enumerated().map { x, v in
            TCell("\(v)", x == k ? .active : x == i || x == j ? .outline : i.map { x > $0 && x < j! } ?? false ? .pending : .idle)
        }, spread: true)
    }
    func f(_ cur: (Int, Int)?, _ reads: [(Int, Int)], _ k: Int?, _ formula: [String], _ headline: String, _ body: String, answer: Bool = false, chips: [StoryChip] = []) -> TFrame {
        TFrame(columns: colsJ.count, lit: cur.map { [colsJ.firstIndex(of: $0.1)!: .active] } ?? [:], rows: table(cur, reads: reads, answer: answer), formula: formula, chips: chips,
               headline: headline, body: body, headers: headers, topRows: [balloons(cur?.0, cur?.1, k)])
    }
    var frames = [f(nil, [], nil, ["dp[i][j] = max over i < k < j of dp[i][k] + dp[k][j] + a[i]·a[k]·a[j]"],
                    "Burst every balloon for the most coins. Bursting k earns a[left]·a[k]·a[right].",
                    "dp[i][j] is the best score for the balloons strictly between i and j, with i and j still standing.")]
    for len in 2..<n {
        for i in 0..<(n - len) {
            let j = i + len
            var best = -1, bestK = i + 1
            for k in (i + 1)..<j {
                let v = dp[i][k]! + dp[k][j]! + a[i] * a[k] * a[j]
                if v > best { best = v; bestK = k }
            }
            dp[i][j] = best
            let k = bestK
            let reads = [(i, k), (k, j)].filter { $0.1 - $0.0 >= 2 }
            frames.append(f((i, j), reads, k, ["last k = \(k): dp[\(i)][\(k)] {p:\(dp[i][k]!)}", "+ dp[\(k)][\(j)] \(dp[k][j]!) + \(a[i])·{\(a[k])}·\(a[j]) = {\(best)}"],
                            "Burst \(a[k]) last between \(a[i]) and \(a[j]): its neighbours are then exactly the two ends.",
                            len == 2 ? "One balloon between the ends: it is the last one by default."
                                : "Choosing the last balloon, not the first, keeps the two sides independent. \(j - i - 1) choices of k tried."))
        }
    }
    frames.append(f(nil, [], nil, ["dp[0][\(n - 1)] = {v:\(dp[0][n - 1]!)}"], "The most coins for all four balloons is {v:\(dp[0][n - 1]!)}.",
                    "O(n³): n² ranges, each trying up to n last balloons.", answer: true, chips: [StoryChip("coins", "\(dp[0][n - 1]!)", .answer)]))
    return [TTab(label: "Burst balloons", frames: frames,
                 legend: [(SimColors.active, .fill, "Current / last burst"), (SimColors.blue, .fill, "Read · ends"), (SimColors.green, .fill, "Filled")])]
}

private func matrixRotateTabs() -> [TTab] {
    let n = 4
    let headers = (0..<n).map { "c\($0)" }
    let body = "Two simple passes do the rotation in place."
    func rows(_ m: [[Int]], _ tone: (Int, Int) -> TTone, lit: Int?) -> [TRow] {
        (0..<n).map { r in TRow(label: "r\(r)", cells: (0..<n).map { c in TCell("\(m[r][c])", tone(r, c)) }, labelTone: r == lit ? .active : nil) }
    }
    func transposeReverse() -> [TFrame] {
        var m = (0..<n).map { r in (0..<n).map { r * n + $0 + 1 } }
        var transposed = Set<Int>(), rotatedRows = Set<Int>()
        var frames = [TFrame(columns: n, rows: rows(m, { _, _ in .idle }, lit: nil), formula: ["(r, c) → transpose (c, r) → reverse (c, 3 − r)"],
                             headline: "Rotate the matrix 90° clockwise without a second matrix.", body: "Transpose across the diagonal, then reverse every row.", headers: headers)]
        for r in 0..<n {
            transposed.insert(r * n + r)
            for c in (r + 1)..<n {
                let (x, y) = (m[r][c], m[c][r])
                m[r][c] = y; m[c][r] = x
                transposed.insert(r * n + c); transposed.insert(c * n + r)
                frames.append(TFrame(columns: n, rows: rows(m, { rr, cc in (rr, cc) == (r, c) || (rr, cc) == (c, r) ? .active : transposed.contains(rr * n + cc) ? .outline : .idle }, lit: nil),
                                     formula: ["swap a[\(r)][\(c)] ↔ a[\(c)][\(r)]: {\(x)} ↔ {\(y)}"],
                                     headline: "Transpose: \(x) and \(y) trade places across the diagonal.",
                                     body: "Only pairs above the diagonal are swapped, so each pair moves once.", headers: headers))
            }
        }
        for r in 0..<n {
            for c in 0..<(n / 2) {
                let (x, y) = (m[r][c], m[r][n - 1 - c])
                m[r][c] = y; m[r][n - 1 - c] = x
                let doneNow = c == n / 2 - 1
                frames.append(TFrame(columns: n, rows: rows(m, { rr, cc in
                    rr == r && (cc == c || cc == n - 1 - c) ? .active : rotatedRows.contains(rr) ? .green : .outline
                }, lit: r), formula: ["(r, c) → transpose (c, r) → reverse (c, 3 − r)"],
                                     headline: c == 0 ? (r == 0 ? "The transpose is done. Row 0 now swaps its ends: \(y) and \(x) trade places." : "Row \(r) now swaps its ends: \(y) and \(x) trade places.")
                                         : "Then the middle pair of row \(r): \(y) and \(x).",
                                     body: r > 0 ? body + " Row \(r - 1) already reads \(m[r - 1].map(String.init).joined(separator: " "))." : body, headers: headers))
                if doneNow { rotatedRows.insert(r) }
            }
        }
        frames.append(TFrame(columns: n, rows: rows(m, { _, _ in .green }, lit: nil), formula: ["rotated · O(n²) time, O(1) extra space"],
                             headline: "Every row is reversed: the matrix is rotated 90° clockwise.", body: "Counter-clockwise is the same two steps with the reverse on columns instead.", headers: headers))
        return frames
    }
    func fourWay() -> [TFrame] {
        var m = (0..<n).map { r in (0..<n).map { r * n + $0 + 1 } }
        var done = Set<Int>()
        var frames = [TFrame(columns: n, rows: rows(m, { _, _ in .idle }, lit: nil), formula: ["top ← left ← bottom ← right ← top"],
                             headline: "Rotate ring by ring: each step moves four cells at once.", body: "One temporary value per cycle, n²/4 cycles in all.", headers: headers)]
        for layer in 0..<(n / 2) {
            let first = layer, last = n - 1 - layer
            for i in first..<last {
                let off = i - first
                let cells = [(first, i), (last - off, first), (last, last - off), (i, last)]
                let top = m[first][i]
                m[first][i] = m[last - off][first]
                m[last - off][first] = m[last][last - off]
                m[last][last - off] = m[i][last]
                m[i][last] = top
                frames.append(TFrame(columns: n, rows: rows(m, { r, c in cells.contains { $0 == (r, c) } ? .active : done.contains(r * n + c) ? .green : .idle }, lit: nil),
                                     formula: ["ring \(layer): " + cells.map { "(\($0.0),\($0.1))" }.joined(separator: " → ") + " rotate"],
                                     headline: "Four cells, one on each side of ring \(layer), move a quarter turn together.",
                                     body: "The saved top value lands on the right; the other three shift from their neighbour.", headers: headers))
                cells.forEach { done.insert($0.0 * n + $0.1) }
            }
        }
        frames.append(TFrame(columns: n, rows: rows(m, { _, _ in .green }, lit: nil), formula: ["same result, one pass over each ring"],
                             headline: "All rings turned: the same rotation as transpose + reverse.", body: "Harder to get right, but it touches every cell exactly once.", headers: headers))
        return frames
    }
    return [TTab(label: "Transpose + reverse", frames: transposeReverse(), legend: [(SimColors.active, .fill, "Swapping"), (SimColors.blue, .fill, "Transposed"), (SimColors.green, .fill, "Rotated")]),
            TTab(label: "Four-way swap", frames: fourWay(), legend: [(SimColors.active, .fill, "Moving"), (SimColors.green, .fill, "Rotated")])]
}

private func xorSplitTabs() -> [TTab] {
    let nums = [4, 1, 2, 1, 3, 2]
    let x = nums.reduce(0, ^), low = x & -x, bitIndex = low.trailingZeroBitCount
    let bits = 3
    func xorRow(_ value: Int?, decide: Bool) -> TRow {
        let cells = (0..<bits).reversed().map { b -> TCell in
            guard let v = value else { return TCell("", .ghost) }
            let on = v >> b & 1 == 1
            return TCell(on ? "1" : "0", decide && b == bitIndex ? .active : on ? .outline : .pending)
        }
        return TRow(title: "XOR OF ALL · \(nums.map(String.init).joined(separator: " "))", cells: cells + [TCell(value.map { "= \($0)" } ?? "", .plain)], spread: true)
    }
    let ones = nums.filter { $0 & low != 0 }, zeros = nums.filter { $0 & low == 0 }
    let a = ones.reduce(0, ^), b = zeros.reduce(0, ^)
    func groups(results: Bool) -> [TRow] {
        var rs = [TRow(title: "BIT \(bitIndex) = 1", cells: ones.map { TCell("\($0)", $0 == a ? .active : .idle) } + zeros.map { TCell("\($0)", $0 == b ? .active : .idle) },
                       spread: true, split: ones.count, splitTitle: "BIT \(bitIndex) = 0")]
        if results { rs.append(TRow(cells: [TCell("→ \(a)", .answer), TCell("→ \(b)", .answer)], spread: true, split: 1)) }
        return rs
    }
    var running = 0
    var frames = [TFrame(columns: 0, rows: [TRow(title: "NUMS", cells: nums.map { TCell("\($0)", .idle) }, spread: true)], formula: ["every value appears twice except two"],
                         headline: "Find the two values that appear only once, in O(n) time and O(1) space.",
                         body: "XOR cancels pairs: v ⊕ v = 0 and v ⊕ 0 = v.")]
    for (i, v) in nums.enumerated() where i % 2 == 1 {
        running ^= nums[i - 1] ^ v
        frames.append(TFrame(columns: 0, rows: [xorRow(running, decide: false)], formula: ["… ⊕ \(nums[i - 1]) ⊕ \(v) = {\(running)}"],
                             headline: "XOR in \(nums[i - 1]) and \(v): the running value is \(running).", body: "Order doesn't matter; every pair cancels eventually."))
    }
    frames.append(TFrame(columns: 0, rows: [xorRow(x, decide: false)], formula: ["xor of all = \(a) ⊕ \(b) = {\(x)}"],
                         headline: "The pairs are gone. What is left, \(x), is the two singles XORed together.", body: "A set bit in \(x) is a bit where the two singles differ."))
    frames.append(TFrame(columns: 0, rows: [xorRow(x, decide: true)] + groups(results: false), formula: ["\(x) & −\(x) = {\(low)}", "→ split on bit \(bitIndex) · XOR each group"],
                         headline: "\(a) and \(b) differ at bit \(bitIndex), so splitting on it puts one in each group.",
                         body: "Pairs land in the same group and cancel, leaving one unique value per group. No hash set needed."))
    frames.append(TFrame(columns: 0, rows: [xorRow(x, decide: true)] + groups(results: true), formula: ["\(ones.map(String.init).joined(separator: " ⊕ ")) = {v:\(a)} · \(zeros.map(String.init).joined(separator: " ⊕ ")) = {v:\(b)}"],
                         headline: "XOR each group: {v:\(a)} and {v:\(b)} are the singles.", body: "Pairs land in the same group and cancel, leaving one unique value per group. No hash set needed."))
    frames.append(TFrame(columns: 0, rows: [xorRow(x, decide: true)] + groups(results: true), formula: ["two passes · O(n) time · O(1) space"], chips: [StoryChip("singles", "\(a), \(b)", .answer)],
                         headline: "Two passes over the array, no extra memory.", body: "The same lowest-set-bit trick, x & −x, powers Fenwick trees."))
    return [TTab(label: "Single numbers", frames: frames, legend: [(SimColors.active, .fill, "Deciding bit"), (SimColors.blue, .fill, "Set bit"), (SimColors.answer, .fill, "Result")])]
}

private func prefix2dTabs() -> [TTab] {
    let a = [[3, 0, 1, 4], [5, 6, 3, 2], [1, 2, 0, 1]]
    let rows = a.count, cols = a[0].count
    var p = [[Int?]](repeating: [Int?](repeating: nil, count: cols + 1), count: rows + 1)
    for i in 0...rows { p[i][0] = 0 }
    for j in 0...cols { p[0][j] = 0 }
    let colHeaders = (0..<cols).map { "c\($0)" }
    func matrix(_ cur: (Int, Int)?, rect: (Int, Int, Int, Int)? = nil) -> [TRow] {
        (0..<rows).map { r in
            TRow(label: "r\(r)", cells: (0..<cols).map { c in
                if let x = rect, r >= x.0, r <= x.2, c >= x.1, c <= x.3 { return TCell("\(a[r][c])", .outline) }
                return TCell("\(a[r][c])", cur.map { $0 == (r, c) } ?? false ? .active : .idle)
            })
        }
    }
    func table(_ cur: (Int, Int)?, marks: [Int: TTone] = [:]) -> [TRow] {
        (0...rows).map { i in
            TRow(label: i == 0 ? "" : "r\(i - 1)", cells: (0...cols).map { j in
                if i == 0 || j == 0 { return TCell("0", marks[i * 100 + j] ?? .pending) }
                guard let v = p[i][j] else { return TCell("", .ghost) }
                if let c = cur, c == (i, j) { return TCell("\(v)", .active) }
                return TCell("\(v)", marks[i * 100 + j] ?? .green)
            }, labelTone: cur?.0 == i ? .active : nil)
        }
    }
    func f(_ cur: (Int, Int)?, _ marks: [Int: TTone], _ formula: [String], _ headline: String, _ body: String, rect: (Int, Int, Int, Int)? = nil, chips: [StoryChip] = []) -> TFrame {
        TFrame(columns: cols + 1, rows: table(cur, marks: marks), formula: formula, chips: chips, headline: headline, body: body, heading: "P · BORDER OF ZEROS",
               headers: [""] + colHeaders, topRows: matrix(cur.map { ($0.0 - 1, $0.1 - 1) }, rect: rect), topColumns: cols, topHeaders: colHeaders, topHeading: "MATRIX")
    }
    var frames = [f(nil, [:], ["P[i][j] = sum of the block above and left of (i, j)"], "Precompute block sums so any rectangle sum is O(1).",
                    "A border row and column of zeros means the first row and column need no special case.")]
    for i in 1...rows {
        for j in 1...cols {
            let v = a[i - 1][j - 1], up = p[i - 1][j]!, left = p[i][j - 1]!, diag = p[i - 1][j - 1]!
            p[i][j] = v + up + left - diag
            frames.append(f((i, j), [(i - 1) * 100 + j: .outline, i * 100 + j - 1: .outline, (i - 1) * 100 + j - 1: .mismatch],
                            ["{\(v)} + ↑ {p:\(up)} + ← {p:\(left)} − ↖ {w:\(diag)} = {\(p[i][j]!)}"],
                            i == 1 || j == 1 ? "Along the \(i == 1 ? "top row" : "left column") the zero border stands in for the missing block." : "Up and left both include the top-left block, so it's subtracted once.",
                            "After this one pass, any rectangle sum is four lookups."))
        }
    }
    let (r1, c1, r2, c2) = (1, 1, 2, 2)
    let sum = p[r2 + 1][c2 + 1]! - p[r1][c2 + 1]! - p[r2 + 1][c1]! + p[r1][c1]!
    frames.append(f(nil, [(r2 + 1) * 100 + c2 + 1: .outline, r1 * 100 + c2 + 1: .mismatch, (r2 + 1) * 100 + c1: .mismatch, r1 * 100 + c1: .outline],
                    ["sum r\(r1)..r\(r2), c\(c1)..c\(c2) = \(p[r2 + 1][c2 + 1]!) − \(p[r1][c2 + 1]!) − \(p[r2 + 1][c1]!) + \(p[r1][c1]!) = {v:\(sum)}"],
                    "Any rectangle is one big block minus two strips plus the corner they both removed: {v:\(sum)}.",
                    "O(rows × cols) to build once, then O(1) per query.", rect: (r1, c1, r2, c2), chips: [StoryChip("rectangle sum", "\(sum)", .answer)]))
    return [TTab(label: "Build", frames: frames, legend: [(SimColors.active, .fill, "Current"), (SimColors.blue, .fill, "Added"), (SimColors.red, .fill, "Subtracted"), (SimColors.green, .fill, "Filled")])]
}

private func bitTrieTabs() -> [TTab] {
    let values = [3, 10, 5, 25, 2, 8], q = 5, bits = 5
    let headers = (0..<bits).map { "b\(bits - 1 - $0)" }
    func bit(_ v: Int, _ col: Int) -> Int { v >> (bits - 1 - col) & 1 }
    var candidates = values
    var took: [Int] = [], forced: [Bool] = []
    var frames: [TFrame] = []
    func rowsFor(_ cur: Int?) -> [TRow] {
        let qRow = TRow(label: "\(q)", cells: (0..<bits).map { TCell("\(bit(q, $0))", bit(q, $0) == 1 ? .outline : .pending) })
        let want = TRow(label: "want", cells: (0..<bits).map { c in
            let w = 1 - bit(q, c)
            if c == cur { return TCell("\(w)", .active) }
            if c < took.count { return TCell("\(w)", forced[c] ? .pending : .green) }
            return TCell("\(w)", .ghost)
        })
        let trie = TRow(label: "trie", cells: (0..<bits).map { c in
            if c < took.count { return TCell("\(took[c])", forced[c] ? .mismatch : .green) }
            return TCell("", .ghost)
        })
        let xor = TRow(label: "xor", cells: (0..<bits).map { c in
            if c < took.count { let x = took[c] ^ bit(q, c); return TCell("\(x)", x == 1 ? .answer : .pending) }
            return TCell("·", .ghost)
        })
        return [qRow, want, trie, xor]
    }
    let heading = "QUERY \(q) · VALUES \(values.map(String.init).joined(separator: " "))"
    frames.append(TFrame(columns: bits, rows: rowsFor(nil), formula: ["walk from b\(bits - 1) down · want the opposite of each query bit"],
                         headline: "Find the value that XORs with \(q) to the largest result.",
                         body: "Every value is stored in a binary trie by its bits, most significant first.", heading: heading, headers: headers))
    for c in 0..<bits {
        let w = 1 - bit(q, c)
        let has = candidates.filter { bit($0, c) == w }
        let got = !has.isEmpty
        let t = got ? w : 1 - w
        took.append(t); forced.append(!got)
        if got { candidates = has }
        let partial = (0..<bits).map { $0 < took.count ? String(took[$0] ^ bit(q, $0)) : "·" }.joined()
        frames.append(TFrame(columns: bits, lit: [c: .active], rows: rowsFor(got ? nil : c),
                             formula: got ? ["b\(bits - 1 - c): want {\(w)} → trie has it → xor bit {v:1}", "· xor = \(partial)"]
                                 : ["b\(bits - 1 - c): want {\(w)} , trie only has {w:\(t)}", "→ take \(t) · xor = \(partial)"],
                             headline: got ? "At bit \(bits - 1 - c) the trie has a \(w) below this path, so the walk takes it and that XOR bit is 1."
                                 : "At bit \(bits - 1 - c) the trie has no \(w) below this path, so the walk takes \(t) and loses that bit.",
                             body: "Higher bits are worth more than all lower ones combined, so greedy from the top is safe. \(q) ⊕ \(candidates[0]) = \(q ^ candidates[0]).",
                             heading: heading, headers: headers))
    }
    let best = candidates[0]
    frames.append(TFrame(columns: bits, rows: rowsFor(nil), formula: ["\(q) ⊕ \(best) = {v:\(q ^ best)}"], chips: [StoryChip("max xor", "\(q ^ best)", .answer)],
                         headline: "The best partner for \(q) is \(best): {v:\(q ^ best)}.",
                         body: "O(bits) per query after O(n · bits) to build, instead of trying every value.", heading: heading, headers: headers))
    return [TTab(label: "Max XOR", frames: frames,
                 legend: [(SimColors.green, .fill, "Got opposite bit"), (SimColors.active, .fill, "Deciding"), (SimColors.red, .fill, "Forced"), (SimColors.answer, .fill, "XOR bit")])]
}

private func pairedDesignTabs() -> [TTab] {
    func lru() -> [TFrame] {
        let cap = 3
        var list: [(Int, String)] = []
        var mapOrder: [Int] = []
        var frames: [TFrame] = []
        func f(_ written: Int?, hit: Int?, evicted: (Int, String)?, _ formula: [String], _ headline: String, _ body: String) {
            let listRow = TRow(title: "LIST · MOST RECENT FIRST", cells: [TCell("head", .pending)] + list.map { k, v in
                TCell("\(k):\(v)", k == written ? .active : k == hit ? .answer : .idle)
            } + [TCell("tail", .pending)], spread: true, joiner: "⇄")
            var rows = [listRow]
            if let e = evicted {
                rows.append(TRow(cells: Array(repeating: TCell("", .plain), count: list.count + 1) + [TCell("\(e.0):\(e.1)", .mismatch)], spread: true, joiner: " "))
            }
            let keys = mapOrder + (evicted.map { [$0.0] } ?? [])
            rows.append(TRow(title: "MAP · KEY → NODE", cells: keys.isEmpty ? [TCell("", .ghost)] : keys.map { k in
                TCell("\(k)", k == evicted?.0 ? .mismatch : k == written ? .active : k == hit ? .answer : .idle)
            }, spread: true))
            frames.append(TFrame(columns: 0, rows: rows, formula: formula, chips: [StoryChip("size", "\(list.count) / \(cap)")], headline: headline, body: body))
        }
        f(nil, hit: nil, evicted: nil, ["capacity \(cap) · map for lookup, list for recency"], "An LRU cache: O(1) get and put, evicting the least recently used key.",
          "A hash map finds a node in O(1); a doubly linked list keeps them in recency order.")
        func put(_ k: Int, _ v: String, hit: Int? = nil) {
            var evicted: (Int, String)? = nil
            if list.count == cap {
                evicted = list.removeLast()
                mapOrder.removeAll { $0 == evicted!.0 }
            }
            list.insert((k, v), at: 0)
            mapOrder.append(k)
            if let e = evicted {
                f(k, hit: hit, evicted: e,
                  ["put(\(k)): size \(cap) = cap → evict tail.prev {w:\(e.0)} · delete map[\(e.0)]"],
                  "\(e.0) was used least recently, so put(\(k)) removes it from both the list and the map.",
                  "The map finds a node in O(1); the list gives the order." + (hit.map { " \($0) is safe because get(\($0)) moved it to the front." } ?? ""))
            } else {
                f(k, hit: nil, evicted: nil, ["put(\(k)) → new node at head · map[\(k)] = node"], "put(\(k), \(v)) goes to the front of the list.",
                  "New and recently used keys live at the head; the tail is the next to go.")
            }
        }
        put(1, "a"); put(2, "b"); put(3, "c")
        let idx = list.firstIndex { $0.0 == 1 }!
        let node = list.remove(at: idx); list.insert(node, at: 0)
        f(nil, hit: 1, evicted: nil, ["get(1) → map hit → unlink, move to head → {v:a}"], "get(1) finds its node through the map and moves it to the head.",
          "Unlinking and relinking a doubly linked node is O(1) once the map has handed it over.")
        put(4, "d", hit: 1)
        f(nil, hit: nil, evicted: nil, ["get(2) → map miss → {w:−1}"], "get(2) misses: it was evicted a step ago.", "A miss costs one map lookup and nothing else.")
        f(nil, hit: nil, evicted: nil, ["get O(1) · put O(1) · space O(capacity)"], "Two structures, each doing the one job it is fast at.",
          "This hash map + linked list pairing is the standard answer; many languages ship it as an ordered map.")
        return frames
    }
    func lfu() -> [TFrame] {
        let cap = 2
        var freq: [Int: Int] = [:]
        var buckets: [Int: [Int]] = [:]
        var frames: [TFrame] = []
        func f(_ cur: Int?, evicted: Int?, _ formula: [String], _ headline: String, _ body: String) {
            let minF = buckets.filter { !$0.value.isEmpty }.keys.min()
            let rows = (1...3).map { fq in
                TRow(title: "FREQ \(fq)\(fq == minF ? " · MIN" : "")", cells: (buckets[fq] ?? []).isEmpty ? [TCell("", .ghost)] : buckets[fq]!.map { k in
                    TCell("\(k)", k == cur ? .active : .idle)
                }, spread: true, joiner: (buckets[fq] ?? []).count > 1 ? "⇄" : nil)
            }
            let mapRow = TRow(title: "MAP · KEY → FREQ", cells: (freq.keys.sorted().map { k in TCell("\(k) · f\(freq[k]!)", k == cur ? .active : .idle) }
                + (evicted.map { [TCell("\($0)", .mismatch)] } ?? [])).ifEmpty([TCell("", .ghost)]), spread: true)
            frames.append(TFrame(columns: 0, rows: rows + [mapRow], formula: formula, headline: headline, body: body))
        }
        func touch(_ k: Int) {
            let fq = freq[k]!
            buckets[fq]!.removeAll { $0 == k }
            freq[k] = fq + 1
            buckets[fq + 1, default: []].insert(k, at: 0)
        }
        f(nil, evicted: nil, ["capacity \(cap) · evict the least frequent, oldest first"], "An LFU cache evicts the key used the fewest times.",
          "Keys sit in one list per frequency; a map from key to node keeps every move O(1).")
        for k in [1, 2] {
            freq[k] = 1; buckets[1, default: []].insert(k, at: 0)
            f(k, evicted: nil, ["put(\(k)) → freq 1 list"], "put(\(k)) starts at frequency 1.", "New keys always join the frequency-1 list.")
        }
        touch(1)
        f(1, evicted: nil, ["get(1) → freq 1 → 2"], "get(1) moves key 1 up to the frequency-2 list.", "Each use moves a key one list up, still O(1).")
        let victim = buckets[1]!.last!
        buckets[1]!.removeLast(); freq[victim] = nil
        freq[3] = 1; buckets[1, default: []].insert(3, at: 0)
        f(3, evicted: victim, ["put(3): full → min freq 1 → evict {w:\(victim)}"], "The cache is full, so the least frequent key, \(victim), is evicted for 3.",
          "Within the lowest frequency, the least recently used key goes first.")
        touch(3); touch(3)
        f(3, evicted: nil, ["get(3) ×2 → freq 3"], "Two gets lift key 3 to frequency 3; key 1 is now the least frequent.", "The minimum frequency is tracked so eviction never scans.")
        f(nil, evicted: nil, ["get · put O(1) with a min-freq pointer"], "LFU pairs a map with a list per frequency.", "More bookkeeping than LRU, but still O(1) per operation.")
        return frames
    }
    return [TTab(label: "LRU", frames: lru(), legend: [(SimColors.active, .fill, "Just written"), (SimColors.answer, .fill, "Hit, moved to head"), (SimColors.red, .fill, "Evicted")]),
            TTab(label: "LFU", frames: lfu(), legend: [(SimColors.active, .fill, "Touched"), (SimColors.red, .fill, "Evicted")])]
}

private extension Array {
    func ifEmpty(_ fallback: [Element]) -> [Element] { isEmpty ? fallback : self }
}
