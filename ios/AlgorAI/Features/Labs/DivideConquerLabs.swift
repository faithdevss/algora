import SwiftUI

// Port of DivideConquerLabs.kt: Strassen, Karatsuba and Closest Pair drawn as story cards (docs mocks) —
// the products a level makes and which of them are done, instead of the whole call tree, and the strip
// that closest pair checks.

// MARK: - Strassen

let strassenSizes = [2, 4, 8]

private let strassenProducts = [
    "(A11 + A22)(B11 + B22)",
    "(A21 + A22) B11",
    "A11 (B12 − B22)",
    "A22 (B21 − B11)",
    "(A11 + A12) B22",
    "(A21 − A11)(B11 + B12)",
    "(A12 − A22)(B21 + B22)",
]

/// The four blocks of C, as sums of the seven products (index into strassenProducts, and its sign).
private let strassenBlocks: [(String, [(Int, Int)])] = [
    ("C11", [(0, 1), (3, 1), (4, -1), (6, 1)]),
    ("C12", [(2, 1), (4, 1)]),
    ("C21", [(1, 1), (3, 1)]),
    ("C22", [(0, 1), (1, -1), (2, 1), (5, 1)]),
]

private func blockFormula(_ terms: [(Int, Int)]) -> String {
    terms.enumerated().map { i, t in
        (i == 0 ? (t.1 < 0 ? "−" : "") : t.1 < 0 ? " − " : " + ") + "M\(t.0 + 1)"
    }.joined()
}

/// Scalar multiplications for an s×s product: plain at 2×2 and below, seven half-size products above.
private func strassenMults(_ s: Int) -> Int { s <= 2 ? s * s * s : 7 * strassenMults(s / 2) }

private let ordinalWords = ["first", "second", "third", "fourth", "fifth", "sixth", "seventh"]

func strassenSteps(_ n: Int) -> [GreedyStory] {
    let h = n / 2
    let mults = 7 * strassenMults(h)
    let plain = n * n * n
    var steps: [GreedyStory] = []
    let bodies = [
        "A sum of blocks times a sum of blocks: one multiplication doing the work of several.",
        "Only the multiplications set the exponent. The block additions are cheap by comparison.",
        "Seven products instead of eight cut the exponent from 3 to 2.81.",
        "The additions grow as n², so they never decide the exponent.",
        "Each product feeds more than one block of C, which is how seven cover all four.",
        "Subtracting blocks before multiplying is where the saving comes from.",
        "That is the last product. Nothing else gets multiplied.",
    ]
    func step(_ states: (Int) -> StoryTone, _ chips: [StoryChip], _ headline: String, _ body: String) {
        let rows = strassenProducts.enumerated().map { i, f in StoryTableRow(values: ["M\(i + 1)", f], tone: states(i)) }
        steps.append(GreedyStory(
            title: "", note: "",
            sections: [StorySection(label: "7 BLOCK PRODUCTS", note: "instead of 8",
                                    table: StoryTable(headers: [], weights: [1, 5], rows: rows, aligns: [.leading, .trailing]))],
            legend: [(.active, "Computing"), (.done, "Returned")], chips: chips, headline: headline, body: body))
    }

    step({ _ in .idle }, [StoryChip("plain", "8 products")],
         "Split A and B into four {\(h) × \(h)} blocks each.",
         "The plain method multiplies 8 pairs of blocks. Strassen gets by with 7 cleverly chosen products.")
    for i in strassenProducts.indices {
        let users = strassenBlocks.filter { $0.1.contains { $0.0 == i } }
        let shortest = users.min { $0.1.count < $1.1.count }!
        let chip = StoryChip(shortest.0, blockFormula(shortest.1))
        step({ j in j < i ? .done : j == i ? .active : .idle }, [chip],
             "{M\(i + 1)} = \(strassenProducts[i]), the \(ordinalWords[i]) of seven.", bodies[i])
        step({ j in j <= i ? .done : .idle }, [chip],
             "{m:M\(i + 1)} is done. \(users.map(\.0).joined(separator: " and ")) will use it.",
             h == 1 ? "Each block is a single number here, so M\(i + 1) costs one multiplication."
                 : "It multiplies two \(h) × \(h) blocks: \(strassenMults(h)) scalar multiplications" + (h > 2 ? ", by splitting the same way again." : ", the plain way."))
    }
    step({ _ in .done }, [StoryChip("mults", "\(mults) vs \(plain)", .done), StoryChip("block adds", "18")],
         "Add the products into {m:C}: C12 = M3 + M5, and so on.",
         "\(mults) scalar multiplications against \(plain) for the plain method, paid for with 18 block additions.")
    return steps
}

// MARK: - Karatsuba

let karatsubaDigits = [2, 3, 4]

private let karatsubaOperands: [Int: (Int, Int)] = [2: (47, 82), 3: (471, 823), 4: (1234, 5678)]

/// One-digit multiplications the full recursion makes: the leaves of Karatsuba's call tree.
private func karatsubaLeaves(_ a: Int, _ b: Int, _ width: Int) -> Int {
    if a < 10 || b < 10 { return 1 }
    let half = width / 2
    var p = 1
    for _ in 0..<half { p *= 10 }
    return karatsubaLeaves(a / p, b / p, width - half) + karatsubaLeaves(a % p, b % p, half)
        + karatsubaLeaves(a / p + a % p, b / p + b % p, max(width - half, half) + 1)
}

private func superscript(_ n: Int) -> String {
    let glyphs = Array("⁰¹²³⁴⁵⁶⁷⁸⁹")
    return String(String(n).map { glyphs[Int(String($0))!] })
}

func karatsubaSteps(_ digits: Int) -> [GreedyStory] {
    let (x, y) = karatsubaOperands[digits]!
    let half = digits / 2
    var p = 1
    for _ in 0..<half { p *= 10 }
    let a = x / p, b = x % p, c = y / p, d = y % p
    let ac = a * c, bd = b * d, sums = (a + b) * (c + d)
    let cross = sums - ac - bd
    let leaves = karatsubaLeaves(x, y, digits)
    let schoolbook = digits * digits
    let labels = ["\(a) × \(c)", "\(b) × \(d)", "\(a + b) × \(c + d)"]
    let values = [ac, bd, sums]
    var steps: [GreedyStory] = []

    /// done: products finished; current: the one being computed; crossed: the middle term has been reduced.
    func step(_ root: StoryTone, _ done: Int, _ current: Int?, _ crossed: Bool, _ chips: [StoryChip], _ headline: String, _ body: String) {
        func tone(_ i: Int) -> StoryTone { i < done ? .done : i == current ? .active : .idle }
        func known(_ i: Int) -> Bool { i < done || i == current }
        let children = labels.indices.map { StoryPill(text: labels[$0], tone: tone($0), caption: known($0) ? "= \(values[$0])" : nil) }
        let rows = [
            StoryTableRow(values: ["ac", labels[0], known(0) ? "\(ac)" : "…"], tone: tone(0)),
            StoryTableRow(values: ["bd", labels[1], known(1) ? "\(bd)" : "…"], tone: tone(1)),
            StoryTableRow(values: ["ad + bc", crossed ? "\(sums) − \(ac) − \(bd)" : "\(labels[2]) − …", crossed ? "\(cross)" : "…"],
                          tone: crossed ? .done : tone(2)),
        ]
        steps.append(GreedyStory(
            title: "", note: "",
            sections: [StorySection(fan: StoryFan(root: StoryPill(text: "\(x) × \(y)", tone: root), children: children)),
                       StorySection(table: StoryTable(headers: [], weights: [1.3, 2.8, 1.3], rows: rows, aligns: [.leading, .center, .trailing]))],
            legend: [(.active, "Computing"), (.path, "Waiting"), (.done, "Returned")], chips: chips, headline: headline, body: body))
    }
    let three = [StoryChip("products", "3, not 4")]

    step(.active, 0, nil, false, [StoryChip("schoolbook", "4 products")],
         "Split both numbers in half: {\(x) = \(a) | \(b)}.",
         "And \(y) = \(c) | \(d). Multiplying the halves the schoolbook way needs ac, ad, bc and bd: four products.")
    step(.path, 0, 0, false, three, "{\(labels[0])} = \(ac), the first of three products.",
         "ac is the high part of the answer. It gets shifted \(2 * half) places left at the end.")
    step(.path, 1, 1, false, three, "{\(labels[1])} = \(bd), the second of three products.",
         "The middle term reuses them, so each level needs 3 multiplications, not 4.")
    step(.path, 2, 2, false, three, "{\(labels[2])} = \(sums), the third product.",
         "It multiplies the sums, (a + b)(c + d), which is ac + ad + bc + bd all at once.")
    step(.path, 3, nil, true, three, "Subtract ac and bd to leave {m:ad + bc = \(cross)}.",
         "No fourth multiplication: the cross terms fall out of one product and two subtractions.")
    step(.done, 3, nil, true, [StoryChip("one-digit mults", "\(leaves) vs \(schoolbook)", leaves < schoolbook ? .done : .warn)],
         "{m:\(x * y)} = \(ac)·10\(superscript(2 * half)) + \(cross)·10\(superscript(half)) + \(bd).",
         "Each product recurses the same way down to single digits: \(leaves) one-digit multiplications against \(schoolbook) for the schoolbook method" + (leaves >= schoolbook ? ". At this size the bookkeeping still wins." : "."))
    return steps
}

/// A story lab with a size stepper: the steps are rebuilt, and playback restarts, whenever the size moves.
private struct SizedStoryLab: View {
    let intro: String
    let label: String
    let sizes: [Int]
    let build: (Int) -> [GreedyStory]
    @State private var size: Int
    @State private var steps: [GreedyStory]
    @State private var playback: PlaybackState

    init(intro: String, label: String, sizes: [Int], initial: Int, build: @escaping (Int) -> [GreedyStory]) {
        self.intro = intro
        self.label = label
        self.sizes = sizes
        self.build = build
        let steps = build(initial)
        _size = State(initialValue: initial)
        _steps = State(initialValue: steps)
        _playback = State(initialValue: PlaybackState(stepCount: steps.count))
    }

    var body: some View {
        let at = sizes.firstIndex(of: size) ?? 0
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: intro, bottom: 12)
            GreedyStoryLab(story: steps[min(playback.index, steps.count - 1)], playback: playback, captions: steps.map(\.status),
                           stepper: StoryStepper(label: label, value: size, canDecrease: at > 0, canIncrease: at < sizes.count - 1) { delta in
                               size = sizes[at + delta]
                               steps = build(size)
                               playback.load(stepCount: steps.count)
                           })
        }
    }
}

struct StrassenLab: View {
    var body: some View {
        SizedStoryLab(intro: "Seven block products instead of eight. Each row is one product of sums of blocks; watch them return, then combine into the four blocks of C.",
                      label: "matrix size", sizes: strassenSizes, initial: 4, build: strassenSteps)
    }
}

struct KaratsubaLab: View {
    var body: some View {
        SizedStoryLab(intro: "Three products instead of four. The middle term comes from multiplying the sums and subtracting the two products already made.",
                      label: "digits", sizes: karatsubaDigits, initial: 4, build: karatsubaSteps)
    }
}

// MARK: - Closest pair

private let pairPoints: [CGPoint] = [
    (0.08, 0.20), (0.14, 0.62), (0.22, 0.35), (0.28, 0.85), (0.33, 0.12), (0.36, 0.55), (0.40, 0.75), (0.44, 0.30),
    (0.49, 0.55), (0.52, 0.58), (0.60, 0.22), (0.65, 0.70), (0.70, 0.42), (0.75, 0.88), (0.80, 0.15), (0.84, 0.60),
    (0.88, 0.33), (0.93, 0.78),
].map { CGPoint(x: $0.0, y: $0.1) }.sorted { $0.x < $1.x }

private func dist(_ a: CGPoint, _ b: CGPoint) -> CGFloat { hypot(a.x - b.x, a.y - b.y) }
private func f3(_ v: CGFloat) -> String { String(format: "%.3f", Double(v)) }

/// A pair drawn joined by a line, in its tone's colour.
private struct PairLine { let a: Int; let b: Int; let tone: StoryTone }

private struct PairFrame {
    let tones: [Int: StoryTone]
    let lines: [PairLine]
    let split: CGFloat?
    let delta: CGFloat?
    let chips: [StoryChip]
    let headline: String
    let body: String
    var status: String { storyPlain(headline) + " " + body }
}

private func closestPairFrames() -> [PairFrame] {
    let pts = pairPoints
    let n = pts.count
    let pairs = n * (n - 1) / 2
    func best(_ ids: [Int]) -> (CGFloat, (Int, Int)) {
        var d = CGFloat.greatestFiniteMagnitude
        var pair = (-1, -1)
        for i in ids.indices { for j in (i + 1)..<max(i + 1, ids.count) {
            let e = dist(pts[ids[i]], pts[ids[j]])
            if e < d { d = e; pair = (ids[i], ids[j]) }
        } }
        return (d, pair)
    }
    let mid = n / 2
    let split = (pts[mid - 1].x + pts[mid].x) / 2
    let (dl, pl) = best(Array(0..<mid))
    let (dr, pr) = best(Array(mid..<n))
    let delta = min(dl, dr)
    let deltaPair = dl <= dr ? pl : pr
    let strip = pts.indices.filter { abs(pts[$0].x - split) <= delta }.sorted { pts[$0].y < pts[$1].y }
    var stripBest = delta
    var stripPair: (Int, Int)? = nil
    var checks = 0
    for i in strip.indices {
        var j = i + 1
        while j < strip.count && pts[strip[j]].y - pts[strip[i]].y < delta {
            checks += 1
            let e = dist(pts[strip[i]], pts[strip[j]])
            if e < stripBest { stripBest = e; stripPair = (strip[i], strip[j]) }
            j += 1
        }
    }
    let (bestD, bestPair) = best(Array(pts.indices))
    let stripTones = Dictionary(uniqueKeysWithValues: strip.map { ($0, StoryTone.path) })
    let deltaTones: [Int: StoryTone] = [deltaPair.0: .answer, deltaPair.1: .answer]
    let deltaLine = PairLine(a: deltaPair.0, b: deltaPair.1, tone: .answer)
    let sp = stripPair ?? bestPair

    return [
        PairFrame(tones: [:], lines: [], split: nil, delta: nil, chips: [StoryChip("points", "\(n)"), StoryChip("all pairs", "\(pairs)")],
                  headline: "Find the two closest of {\(n)} points.",
                  body: "Checking every pair costs \(pairs) distances. Split in half and most pairs never need checking."),
        PairFrame(tones: deltaTones, lines: [deltaLine], split: split, delta: nil,
                  chips: [StoryChip("left", f3(dl), .answer), StoryChip("right", f3(dr))],
                  headline: "Split at the median x. The left half's best is {v:\(f3(dl))}.",
                  body: "The right half's best is \(f3(dr)), so δ = \(f3(delta)): no pair on one side beats it."),
        PairFrame(tones: stripTones.merging(deltaTones) { $1 }, lines: [deltaLine], split: split, delta: delta,
                  chips: [StoryChip("δ", f3(delta)), StoryChip("strip", "\(strip.count) points", .path)],
                  headline: "Only points within {p:δ} of the line can beat it.",
                  body: "\(strip.count) of \(n) points fall in the strip. Sorted by y, each is compared only with the few just above it."),
        PairFrame(tones: stripTones.merging(deltaTones) { $1 }.merging([sp.0: .active, sp.1: .active]) { $1 },
                  lines: [deltaLine, PairLine(a: sp.0, b: sp.1, tone: .active)], split: split, delta: delta,
                  chips: [StoryChip("δ", f3(delta)), StoryChip("pair", f3(stripBest), .active), StoryChip("strip", "\(strip.count) points")],
                  headline: "This pair straddles the line at {\(f3(stripBest))}, less than δ.",
                  body: "So δ drops to \(f3(stripBest)). Only points within δ of the line are ever checked across it."),
        PairFrame(tones: [bestPair.0: .answer, bestPair.1: .answer], lines: [PairLine(a: bestPair.0, b: bestPair.1, tone: .answer)],
                  split: split, delta: nil,
                  chips: [StoryChip("closest", f3(bestD), .answer), StoryChip("strip checks", "\(checks)"), StoryChip("brute force", "\(pairs)")],
                  headline: "The closest pair is {v:\(f3(bestD))} apart.",
                  body: "It straddles the split, the case the strip exists to catch. The strip took \(checks) checks; overall T(n) = 2T(n/2) + O(n), so O(n log n) against \(pairs) pairs."),
    ]
}

struct ClosestPairLab: View {
    private let frames = closestPairFrames()
    @State private var playback = PlaybackState(stepCount: 5, speedMs: 900)
    @Environment(\.palette) private var palette

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        let present = Set(frame.tones.values).union(frame.lines.map(\.tone))
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: "Divide and conquer on the plane: solve both halves, then check only the strip near the dividing line.", bottom: 12)
            LabCard {
                StoryHeader(title: "SPLIT AT MEDIAN X", note: "strip ± δ")
                Canvas { ctx, size in
                    let pad: CGFloat = 10
                    let bottom = size.height - 14
                    func at(_ p: CGPoint) -> CGPoint {
                        CGPoint(x: pad + p.x * (size.width - 2 * pad), y: pad + (1 - p.y) * (size.height - 2 * pad - 14))
                    }
                    if let sx = frame.split {
                        let x = pad + sx * (size.width - 2 * pad)
                        if let d = frame.delta {
                            let w = d * (size.width - 2 * pad)
                            ctx.fill(Path(roundedRect: CGRect(x: x - w, y: 0, width: 2 * w, height: bottom), cornerRadius: 6), with: .color(SimColors.blue.opacity(0.12)))
                            let y = bottom - 6
                            var rule = Path()
                            rule.move(to: CGPoint(x: x - w, y: y))
                            rule.addLine(to: CGPoint(x: x, y: y))
                            ctx.stroke(rule, with: .color(palette.muted), lineWidth: 1)
                            ctx.draw(Text("δ").font(AppFont.mono(12, .bold)).foregroundColor(palette.muted), at: CGPoint(x: x - w / 2, y: y - 9))
                        }
                        var line = Path()
                        line.move(to: CGPoint(x: x, y: 0))
                        line.addLine(to: CGPoint(x: x, y: bottom))
                        ctx.stroke(line, with: .color(palette.muted.opacity(0.7)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                    }
                    for l in frame.lines {
                        var path = Path()
                        path.move(to: at(pairPoints[l.a]))
                        path.addLine(to: at(pairPoints[l.b]))
                        ctx.stroke(path, with: .color(l.tone.color), lineWidth: 2.5)
                    }
                    for (i, p) in pairPoints.enumerated() {
                        let tone = frame.tones[i]
                        let lit = tone != nil && tone != .idle
                        let r: CGFloat = lit ? 7 : 5
                        let c = at(p)
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)),
                                 with: .color(lit ? tone!.color : palette.muted.opacity(0.55)))
                    }
                }
                .aspectRatio(1.35, contentMode: .fit)
                .padding(.top, 12)
                StoryLegendRow(items: [(StoryTone.active, "Comparing"), (.path, "In strip"), (.answer, "Best so far")]
                    .filter { present.contains($0.0) }.map { ($0.0.color, .dot, $0.1) })
                    .padding(.top, 14)
            }
            StoryChips(chips: frame.chips).padding(.top, 16)
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }
}
