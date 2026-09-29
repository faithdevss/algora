import SwiftUI

// Port of ArrayWalkSection.kt: one-dimensional pointer walks — prefix sums, difference arrays, sliding
// windows, two-pointer scans, Kadane, top-k heaps, and the interview-pattern topics that are the same
// walks under a different name. Precomputed frames, same as the other players.
//
// The renderer is a row of cells plus optional extras: a second row (the prefix / difference / heap
// the walk maintains), pointer labels under the cells, a back-edge arc for cycle detection, and an
// interval track for merge-intervals. Each frame carries only the parts it uses.

enum CellMark { case idle, dim, window, active, done, result }

struct CellView {
    let text: String
    let mark: CellMark
    init(_ text: String, _ mark: CellMark = .idle) { self.text = text; self.mark = mark }
}

struct IntervalView {
    let start: Int, end: Int, mark: CellMark
    init(_ start: Int, _ end: Int, _ mark: CellMark) { self.start = start; self.end = end; self.mark = mark }
}

struct WalkFrame {
    var status: String
    var cells: [CellView]
    var pointers: [Int: String] = [:]
    var aux: [CellView]? = nil
    var auxLabel: String? = nil
    var readout: String? = nil
    /// Fast/slow pointer topics draw the row as a linked list with a cycle: an arc from tail to entry.
    var loopBack: (Int, Int)? = nil
    var intervals: [IntervalView]? = nil
    /// Non-nil draws the step-by-step story card (Coin Change, Fractional Knapsack, Job Sequencing)
    /// instead of the cell rows; the cells are then empty.
    var story: GreedyStory? = nil
}

struct WalkConfig {
    let intro: String
    let legend: [(Color, String)]
    let build: () -> [WalkFrame]
}

/// Kotlin `Pair<Int, Int>` stand-in with value equality, for walks that test membership.
struct IPair: Hashable {
    let first: Int, second: Int
    init(_ first: Int, _ second: Int) { self.first = first; self.second = second }
}

let walkWindowFill = SimColors.blue
let walkActiveFill = SimColors.active
let walkDoneFill = SimColors.green
let walkResultFill = SimColors.answer

let walkPointerLegend: [(Color, String)] = [(walkActiveFill, "Reading"), (walkWindowFill, "In window"), (walkResultFill, "Answer")]
let walkHeapLegend: [(Color, String)] = [(walkActiveFill, "Current"), (walkDoneFill, "In heap"), (walkResultFill, "Answer")]

// Range helpers that never trap on empty ranges, unlike Swift's `a...b` with a > b.
@inline(__always) func inRange(_ i: Int, _ lo: Int, _ hi: Int) -> Bool { i >= lo && i <= hi }
@inline(__always) func inUntil(_ i: Int, _ lo: Int, _ hi: Int) -> Bool { i >= lo && i < hi }
func thru(_ a: Int, _ b: Int) -> StrideThrough<Int> { stride(from: a, through: b, by: 1) }
func until(_ a: Int, _ b: Int) -> StrideTo<Int> { stride(from: a, to: b, by: 1) }

func walkConfigFor(_ topicId: String) -> WalkConfig { walkConfigs[topicId] ?? walkConfigs["two_pointer"]! }

struct ArrayWalkLab: View {
    let topicId: String
    @Environment(\.palette) private var palette

    var body: some View {
        if numberStoryTopicIds.contains(topicId) { NumberStoryLab(topicId: topicId) } else if topicId == "string" { StringLab() } else if topicId == "list_adt" { ListAdtLab() } else { walk }
    }

    @ViewBuilder private var walk: some View {
        let id = walkConfigs[topicId] == nil ? "two_pointer" : topicId
        let config = walkConfigFor(id)
        AsyncFrameLab(key: "walk:\(id)", speedMs: 650, build: { walkConfigFor(id).build() }) { frames, playback in
            let frame = frames[min(max(playback.index, 0), frames.count - 1)]
            if let story = frame.story {
                VStack(alignment: .leading, spacing: 0) {
                    LabIntro(text: config.intro, bottom: 12)
                    GreedyStoryLab(story: story, playback: playback, captions: frames.map(\.status))
                }
            } else {
            // Laid out like the other redesigned labs (docs/ios-design/Simulations iOS.html): the intro
            // above the card, the cells and legend in it, then the readout as chips and the narration.
            VStack(alignment: .leading, spacing: 0) {
                LabIntro(text: config.intro, bottom: 12)
                LabCard {
                    WalkCellRow(cells: frame.cells, indexed: frame.loopBack == nil)
                    if !frame.pointers.isEmpty { WalkPointerRow(pointers: frame.pointers, cells: frame.cells).padding(.top, 2) }
                    if let edge = frame.loopBack { WalkLoopBackArc(edge: edge, count: frame.cells.count) }
                    if let aux = frame.aux {
                        Text((frame.auxLabel ?? "").uppercased()).font(AppFont.sans(12, .semibold)).tracking(1)
                            .foregroundStyle(palette.muted).padding(.top, 16)
                        WalkCellRow(cells: aux, indexed: false).padding(.top, 8)
                    }
                    if let intervals = frame.intervals, !intervals.isEmpty { WalkIntervalTrack(intervals: intervals).padding(.top, 12) }
                    FlowLayout(spacing: 16, lineSpacing: 6) {
                        ForEach(config.legend.indices, id: \.self) { WalkSwatch(color: config.legend[$0].0, label: config.legend[$0].1) }
                    }
                    .padding(.top, 14)
                }
                if let readout = frame.readout { ReadoutChips(text: readout).padding(.top, 16) }
                LabNarration(text: frame.status).padding(.top, 16)
                PlaybackTransport(state: playback, captions: frames.map(\.status))
            }
            }
        }
    }
}

private func walkCellBackground(_ mark: CellMark, _ palette: Palette) -> Color {
    let variant = palette.dark ? Color(hex: 0x2A2E3A) : Color(hex: 0xE3E6EC)
    switch mark {
    case .idle: return variant
    case .dim: return variant.opacity(0.5)
    case .window: return walkWindowFill
    case .active: return walkActiveFill
    case .done: return walkDoneFill
    case .result: return walkResultFill
    }
}

private func walkCellForeground(_ mark: CellMark, _ palette: Palette) -> Color {
    switch mark {
    case .window, .done, .result: return .white
    case .active: return Color(hex: 0x1A1A1A)
    case .dim: return palette.muted
    case .idle: return palette.onSurface
    }
}

/// One row of the stage: mono cells, a bar under each in-window cell, and the index underneath.
private struct WalkCellRow: View {
    let cells: [CellView]
    var indexed = true
    @Environment(\.palette) private var palette

    var body: some View {
        let dense = cells.count > 10
        HStack(alignment: .top, spacing: dense ? 3 : 5) {
            ForEach(cells.indices, id: \.self) { i in
                let cell = cells[i]
                VStack(spacing: 5) {
                    Text(cell.text)
                        .font(AppFont.mono(dense ? 14 : 18, .bold))
                        .foregroundStyle(walkCellForeground(cell.mark, palette))
                        .lineLimit(2)
                        .minimumScaleFactor(0.45)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 2)
                        .frame(maxWidth: .infinity)
                        .frame(height: dense ? 44 : 52)
                        .background(walkCellBackground(cell.mark, palette), in: RoundedRectangle(cornerRadius: dense ? 8 : 10))
                        .opacity(cell.mark == .dim ? 0.6 : 1)
                    RoundedRectangle(cornerRadius: 2)
                        .fill(cell.mark == .window || cell.mark == .active ? walkWindowFill : .clear)
                        .frame(height: 4)
                    if indexed {
                        Text("\(i)").font(AppFont.mono(11)).foregroundStyle(palette.muted)
                    }
                }
            }
        }
        .animation(.easeOut(duration: 0.2), value: cells.map(\.mark))
    }
}

/// Pointer labels (i, j, lo, hi …) under the cells, each in the colour of the cell it points at — the
/// mock's lo/mid/hi — so a reader matches label to cell without the old ▲.
private struct WalkPointerRow: View {
    let pointers: [Int: String]
    let cells: [CellView]
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: cells.count > 10 ? 3 : 5) {
            ForEach(cells.indices, id: \.self) { i in
                Text(pointers[i] ?? " ")
                    .font(AppFont.sans(13, .bold))
                    .foregroundStyle(color(cells[i].mark))
                    .lineLimit(1)
                    .fixedSize()
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func color(_ mark: CellMark) -> Color {
        switch mark {
        // The mock's yellow is for a dark card; on white it needs to be darker as text.
        case .active: return palette.dark ? walkActiveFill : Color(hex: 0xB7791F)
        case .window: return walkWindowFill
        case .done: return walkDoneFill
        case .result: return walkResultFill
        case .idle, .dim: return palette.muted
        }
    }
}

/// Square swatches at the size the other redesigned labs use.
private struct WalkSwatch: View {
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

/// The tail-to-node arc that makes the row read as a linked list with a cycle rather than an array.
private struct WalkLoopBackArc: View {
    let edge: (Int, Int)
    let count: Int
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let slot = size.width / CGFloat(count)
            let from = slot * (CGFloat(edge.0) + 0.5)
            let to = slot * (CGFloat(edge.1) + 0.5)
            var path = Path()
            path.move(to: CGPoint(x: from, y: 0))
            path.addCurve(to: CGPoint(x: to, y: 0), control1: CGPoint(x: from, y: size.height * 1.6), control2: CGPoint(x: to, y: size.height * 1.6))
            ctx.stroke(path, with: .color(palette.primary), lineWidth: 3)
            var head = Path()
            head.move(to: CGPoint(x: to, y: 0))
            head.addLine(to: CGPoint(x: to - 4, y: 5))
            head.addLine(to: CGPoint(x: to + 4, y: 5))
            head.closeSubpath()
            ctx.fill(head, with: .color(palette.primary))
        }
        .frame(height: 26)
    }
}

private struct WalkIntervalTrack: View {
    let intervals: [IntervalView]
    @Environment(\.palette) private var palette

    var body: some View {
        let minStart = intervals.map(\.start).min() ?? 0
        let maxEnd = intervals.map(\.end).max() ?? 1
        let span = CGFloat(max(maxEnd - minStart, 1))
        Canvas { ctx, size in
            let barHeight = size.height / CGFloat(intervals.count)
            for (index, interval) in intervals.enumerated() {
                let left = CGFloat(interval.start - minStart) / span * size.width
                let right = CGFloat(interval.end - minStart) / span * size.width
                let rect = CGRect(x: left, y: CGFloat(index) * barHeight + barHeight * 0.15, width: max(right - left, 6), height: barHeight * 0.7)
                ctx.fill(Path(roundedRect: rect, cornerRadius: 3), with: .color(walkCellBackground(interval.mark, palette)))
            }
        }
        .frame(height: 16 * CGFloat(intervals.count) + 4)
    }
}

// MARK: - String lab
// Port of StringLab.kt. Strings as arrays of character codes, three ways to read them: comparison stops
// at the first difference, equality has to read everything, a substring copies its slice. Each tab is
// its own storyboard over rows of character cells.

private enum StrTone { case idle, active, match, range, written, missing }
private struct StrCell { let text: String; let tone: StrTone }
/// A labelled row whose first cell sits under column `offset` (a substring under its source).
private struct StrRow { let label: String; let offset: Int; let cells: [StrCell] }

private struct StrFrame {
    let rows: [StrRow]
    let column: Int?
    /// One or two centred lines; `{…}` marks colour a term (see LabStory).
    let formula: [String]
    let chips: [StoryChip]
    let headline: String
    let body: String
}

private struct StrVariant {
    let label: String
    let columns: Int
    let frames: [StrFrame]
    /// The step the tab opens on (Compare opens on the deciding character).
    let start: Int
    let legend: [(Color, String)]
}

private func code(_ c: Character) -> Int { Int(c.asciiValue ?? 0) }
private func quoted(_ s: String) -> String { "\"\(s)\"" }

private func compareVariant() -> StrVariant {
    let a = Array("algora"), b = Array("algebra")
    let decide = a.indices.first { a[$0] != b[$0] }!
    let shorter = min(a.count, b.count)
    func row(_ label: String, _ s: [Character], upTo i: Int, decided: Bool = false) -> StrRow {
        let cells = (0..<b.count).map { j -> StrCell in
            guard j < s.count else { return StrCell(text: "·", tone: .missing) }
            return StrCell(text: String(s[j]), tone: j < i ? .match : j == i ? .active : .idle)
        }
        return StrRow(label: label, offset: 0, cells: cells)
    }
    let verdict = "\(quoted(String(a))) > \(quoted(String(b)))"
    var frames: [StrFrame] = []
    for i in 0...decide {
        let chips = [StoryChip("comparisons", "\(i + 1) of \(shorter)"), i == decide ? StoryChip("verdict", "a > b", .answer) : StoryChip("verdict", "–")]
        if i < decide {
            frames.append(StrFrame(rows: [row("a", a, upTo: i), row("b", b, upTo: i)], column: i,
                                   formula: ["'\(a[i])' (\(code(a[i]))) = '\(b[i])' (\(code(b[i]))) → next"], chips: chips,
                                   headline: "Index \(i): '\(a[i])' matches '\(b[i])', so keep going.",
                                   body: i == 0 ? "Strings compare left to right, one character code at a time." : "Every equal pair pushes the decision one index further."))
        } else {
            frames.append(StrFrame(rows: [row("a", a, upTo: i), row("b", b, upTo: i)], column: i,
                                   formula: ["'\(a[i])' (\(code(a[i]))) > '\(b[i])' (\(code(b[i]))) →", "{v:\(verdict)}"], chips: chips,
                                   headline: "Index \(i) decides: '\(a[i])' comes after '\(b[i])', so \(quoted(String(a))) sorts later.",
                                   body: "Comparison stops at the first difference. Only equal strings pay the full O(n)."))
        }
    }
    frames.append(StrFrame(rows: [row("a", a, upTo: decide), row("b", b, upTo: decide)], column: decide,
                           formula: ["{v:\(verdict)}"],
                           chips: [StoryChip("comparisons", "\(decide + 1) of \(shorter)"), StoryChip("unread", "\(shorter - decide - 1)"), StoryChip("verdict", "a > b", .answer)],
                           headline: "\(decide + 1) of \(shorter) comparisons settle it: {v:\(verdict)}.",
                           body: "The rest of both strings is never read. Sorting a list of strings leans on this early exit."))
    return StrVariant(label: "Compare", columns: b.count, frames: frames, start: decide,
                      legend: [(SimColors.active, "Deciding character"), (SimColors.green, "Equal so far"), (SimColors.answer, "Result")])
}

private func equalsVariant() -> StrVariant {
    let a = Array("algora")
    let n = a.count
    func row(_ label: String, upTo i: Int) -> StrRow {
        StrRow(label: label, offset: 0, cells: a.indices.map { StrCell(text: String(a[$0]), tone: $0 < i ? .match : $0 == i ? .active : .idle) })
    }
    var frames = [StrFrame(rows: [row("a", upTo: -1), row("b", upTo: -1)], column: nil,
                           formula: ["length \(n) = length \(n) → read the characters"],
                           chips: [StoryChip("length", "\(n) = \(n)"), StoryChip("verdict", "–")],
                           headline: "Both strings have \(n) characters, so the loop has to run.",
                           body: "Different lengths would answer false here without reading a single character.")]
    for i in 0..<n {
        frames.append(StrFrame(rows: [row("a", upTo: i), row("b", upTo: i)], column: i,
                               formula: ["'\(a[i])' (\(code(a[i]))) = '\(a[i])' (\(code(a[i])))"],
                               chips: [StoryChip("comparisons", "\(i + 1) of \(n)"), StoryChip("verdict", "–")],
                               headline: i == n - 1 ? "Index \(i) matches too, the last character." : "Index \(i) matches. Equal so far, keep reading.",
                               body: "A match cannot end the loop. Only a difference, or running out of characters, can."))
    }
    frames.append(StrFrame(rows: [row("a", upTo: n), row("b", upTo: n)], column: nil,
                           formula: ["\(n) of \(n) equal →", "{v:\(quoted(String(a))) == \(quoted(String(a)))}"],
                           chips: [StoryChip("comparisons", "\(n) of \(n)"), StoryChip("verdict", "equal", .answer)],
                           headline: "Every character matched, so the strings are {v:equal}.",
                           body: "Equal strings are the worst case: all n characters are read, O(n)."))
    return StrVariant(label: "Equals", columns: n, frames: frames, start: 0,
                      legend: [(SimColors.active, "Comparing"), (SimColors.green, "Equal so far"), (SimColors.answer, "Result")])
}

private func substringVariant() -> StrVariant {
    let s = Array("algora")
    let from = 2, to = 5
    let k = to - from
    let slice = String(s[from..<to])
    func rows(copied: Int, current: Int?) -> [StrRow] {
        let src = s.indices.map { j -> StrCell in
            let tone: StrTone = j == current.map { from + $0 } ? .active : (from..<to).contains(j) ? .range : .idle
            return StrCell(text: String(s[j]), tone: tone)
        }
        let dst = (0..<k).map { j -> StrCell in
            j < copied ? StrCell(text: String(s[from + j]), tone: .written)
                : j == current ? StrCell(text: String(s[from + j]), tone: .active) : StrCell(text: "", tone: .missing)
        }
        return [StrRow(label: "s", offset: 0, cells: src), StrRow(label: "sub", offset: from, cells: dst)]
    }
    var frames = [StrFrame(rows: rows(copied: 0, current: nil), column: nil, formula: ["s.substring(\(from), \(to))"],
                           chips: [StoryChip("range", "\(from)..<\(to)"), StoryChip("copied", "0 of \(k)")],
                           headline: "substring(\(from), \(to)) asks for indices \(from) up to \(to).",
                           body: "The end index is exclusive, so the slice is \(k) characters long.")]
    for j in 0..<k {
        frames.append(StrFrame(rows: rows(copied: j, current: j), column: from + j,
                               formula: ["sub[\(j)] = s[\(from + j)] = '\(s[from + j])'"],
                               chips: [StoryChip("range", "\(from)..<\(to)"), StoryChip("copied", "\(j + 1) of \(k)")],
                               headline: "Copy '\(s[from + j])' from s[\(from + j)] into the new string.",
                               body: j == 0 ? "The slice gets fresh storage. The original string is left untouched."
                                   : "One write per character, so the copy grows with the slice, not with s."))
    }
    frames.append(StrFrame(rows: rows(copied: k, current: nil), column: nil, formula: ["{v:\(quoted(slice))}"],
                           chips: [StoryChip("copied", "\(k) of \(k)"), StoryChip("cost", "O(k)", .answer)],
                           headline: "The result is a new string, {v:\(quoted(slice))}.",
                           body: "Most languages copy the slice, so a substring of k characters costs O(k), not O(1)."))
    return StrVariant(label: "Substring", columns: s.count, frames: frames, start: 0,
                      legend: [(SimColors.active, "Copying"), (SimColors.blue, "In range"), (SimColors.green, "Copied"), (SimColors.answer, "Result")])
}

struct StringLab: View {
    private let variants = [compareVariant(), equalsVariant(), substringVariant()]
    @State private var tab = 0
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init() {
        let first = compareVariant()
        let state = PlaybackState(stepCount: first.frames.count)
        state.index = first.start
        _playback = State(initialValue: state)
    }

    var body: some View {
        let variant = variants[tab]
        let frame = variant.frames[min(playback.index, variant.frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                LabSegments(labels: variants.map(\.label), selected: Binding(get: { tab }, set: { select($0) }))
                StrGrid(variant: variant, frame: frame).padding(.top, 14)
                VStack(spacing: 4) {
                    ForEach(frame.formula.indices, id: \.self) { i in
                        storyText(frame.formula[i], palette)
                            .font(AppFont.mono(14, i == frame.formula.count - 1 && frame.formula.count > 1 ? .bold : .regular))
                            .foregroundStyle(palette.onSurface.opacity(0.8))
                            .lineLimit(1).minimumScaleFactor(0.7)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
                .padding(.top, 12)
                StoryLegendRow(items: variant.legend.map { ($0.0, .fill, $0.1) }).padding(.top, 14)
            }
            StoryChips(chips: frame.chips).padding(.top, 16)
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: variant.frames.map { storyPlain($0.headline) })
        }
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        let state = PlaybackState(stepCount: variants[i].frames.count)
        state.index = variants[i].start
        playback = state
    }
}

private struct StrGrid: View {
    let variant: StrVariant
    let frame: StrFrame
    @Environment(\.palette) private var palette

    private let gutter: CGFloat = 22
    private let gap: CGFloat = 6

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: gap) {
                Color.clear.frame(width: gutter, height: 1)
                ForEach(0..<variant.columns, id: \.self) { c in
                    Text("\(c)").font(AppFont.mono(12, c == frame.column ? .bold : .regular))
                        .foregroundStyle(c == frame.column ? StoryTone.active.ink(palette) : palette.muted)
                        .frame(maxWidth: .infinity)
                }
            }
            ForEach(frame.rows.indices, id: \.self) { r in
                let row = frame.rows[r]
                HStack(spacing: gap) {
                    Text(row.label).font(AppFont.mono(13)).foregroundStyle(palette.muted).frame(width: gutter, alignment: .leading)
                    ForEach(0..<variant.columns, id: \.self) { c in
                        let at = c - row.offset
                        if at >= 0 && at < row.cells.count { cell(row.cells[at]) } else { Color.clear.frame(maxWidth: .infinity).frame(height: 44) }
                    }
                }
            }
        }
    }

    private func cell(_ cell: StrCell) -> some View {
        let shape = RoundedRectangle(cornerRadius: 8)
        let (fill, ink): (Color, Color) = switch cell.tone {
        case .idle: (palette.muted.opacity(0.2), palette.onSurface)
        case .active: (SimColors.active, Color(hex: 0x1F1A0A))
        case .match: (SimColors.green.opacity(palette.dark ? 0.22 : 0.16), StoryTone.done.ink(palette))
        case .range: (SimColors.blue.opacity(0.28), palette.onSurface)
        case .written: (SimColors.green, .white)
        case .missing: (palette.muted.opacity(0.08), palette.muted.opacity(0.6))
        }
        return Text(cell.text)
            .font(AppFont.mono(17, .bold))
            .foregroundStyle(ink)
            .frame(maxWidth: .infinity).frame(height: 44)
            .background(fill, in: shape)
            .overlay { if cell.tone == .range { shape.strokeBorder(SimColors.blue, lineWidth: 1.5) } }
    }
}

// MARK: - List ADT lab
// Port of ListAdtLab.kt. One insert(i, 5) run against an array and a linked list at three positions:
// the array pays in shifts after i, the list pays in hops before it. Front, middle and end are tabs.

private enum AdtTone { case idle, written, shifted, walked, empty }
private struct AdtCell { let text: String; let tone: AdtTone; var caption: String? = nil }

private struct AdtFrame {
    let array: [AdtCell]
    let linked: [AdtCell]
    /// The linked arrows drawn in violet: arrow k leaves node k.
    let newArrows: Set<Int>
    let rows: [StoryFormulaRow]
    let chips: [StoryChip]
    let headline: String
    let body: String
}

private func adtFrames(at p: Int) -> [AdtFrame] {
    let values = [10, 20, 30, 40, 50], n = values.count, v = 5
    let shifts = n - p
    let hops = max(p - 1, 0)
    let writes = p == n ? 1 : 2
    let arrayCost = shifts == 0 ? "O(1)" : "O(n)"
    let linkedCost = hops == 0 ? "O(1)" : "O(n)"
    let call = StoryChip("call", "insert(\(p), \(v))")
    func arrayRow(written: Bool) -> [AdtCell] {
        (0...n).map { i -> AdtCell in
            if i == p { return written ? AdtCell(text: "\(v)", tone: .written) : AdtCell(text: "", tone: .empty) }
            let src = i < p ? i : i - 1
            guard src < n else { return AdtCell(text: "", tone: .empty) }
            return i > p ? AdtCell(text: "\(values[src])", tone: .shifted, caption: "+1") : AdtCell(text: "\(values[src])", tone: .idle)
        }
    }
    let startArray = values.map { AdtCell(text: "\($0)", tone: .idle) } + [AdtCell(text: "", tone: .empty)]
    let walked = values.enumerated().map { AdtCell(text: "\($1)", tone: p > 0 && $0 < p ? .walked : .idle) }
    var inserted = values.map { AdtCell(text: "\($0)", tone: .idle) }
    inserted.insert(AdtCell(text: "\(v)", tone: .written), at: p)
    let arrows: Set<Int> = p == 0 ? [0] : p == n ? [p - 1] : [p - 1, p]
    let shiftText = shifts == 1 ? "1 shift" : "\(shifts) shifts"
    let hopText = hops == 1 ? "1 hop" : "\(hops) hops"
    let room: String, write: String, verdict: String, why: String
    switch p {
    case 0:
        room = "The array moves all \(n) elements up a slot; the list needs no walk."
        write = "insert(0, \(v)): the array shifts all \(n) elements up a slot first."
        why = "The linked list writes new.next = \(values[0]) and head = \(v). At position 0 it wins; at the end the array wins."
        verdict = "At the front the linked list wins: {v:\(writes) writes} against \(shiftText)."
    case n:
        room = "Appending needs no shifts, but the list has to walk to \(values[n - 1])."
        write = "insert(\(n), \(v)): the array drops \(v) into its free slot."
        why = "The linked list walks \(hopText) to \(values[n - 1]), then writes \(values[n - 1]).next = \(v). A tail pointer would skip the walk."
        verdict = "At the end the array wins: {v:no shifts} against a \(hopText) walk."
    default:
        room = "The array shifts \(values[p...].map(String.init).joined(separator: ", ")) up; the list walks to \(values[p - 1])."
        write = "insert(\(p), \(v)): \(shiftText) for the array, \(hopText) and \(writes) writes for the list."
        why = "Both costs grow with the list: the array's with what comes after i, the list's with what comes before."
        verdict = "In the middle both pay: \(shiftText) against {v:\(hopText) + \(writes) writes}."
    }
    let costRows = [StoryFormulaRow(label: "array", formula: "\(shiftText) · \(arrayCost)"),
                    StoryFormulaRow(label: "linked", formula: "\(hops > 0 ? "\(hopText) + " : "")\(writes) pointer writes · \(linkedCost)")]
    let arrayWins = shifts == 0
    let verdictRows = [StoryFormulaRow(label: "array", formula: arrayWins ? "{v:\(shiftText) · \(arrayCost)}" : "\(shiftText) · \(arrayCost)"),
                       StoryFormulaRow(label: "linked", formula: !arrayWins && p == 0 ? "{v:\(writes) pointer writes · \(linkedCost)}" : costRows[1].formula)]
    return [
        AdtFrame(array: startArray, linked: values.map { AdtCell(text: "\($0)", tone: .idle) }, newArrows: [],
                 rows: [StoryFormulaRow(label: "array", formula: "\(n) of \(n + 1) slots"), StoryFormulaRow(label: "linked", formula: "\(n) nodes from head")],
                 chips: [call], headline: "Same list, two layouts: an array of slots and a chain of nodes.",
                 body: "Both answer insert(\(p), \(v)) with the same list. The work behind it is what differs."),
        AdtFrame(array: arrayRow(written: false), linked: walked, newArrows: [],
                 rows: [StoryFormulaRow(label: "array", formula: shiftText), StoryFormulaRow(label: "linked", formula: hopText)],
                 chips: [call, StoryChip("shifts", "\(shifts)"), StoryChip("hops", "\(hops)")], headline: room,
                 body: "An array keeps its elements side by side, so room has to be made. A list only has to find the node before the spot."),
        AdtFrame(array: arrayRow(written: true), linked: inserted, newArrows: arrows, rows: costRows,
                 chips: [call, StoryChip("shifts", "\(shifts)"), StoryChip("writes", "\(writes)")], headline: write, body: why),
        AdtFrame(array: arrayRow(written: true), linked: inserted, newArrows: arrows, rows: verdictRows,
                 chips: [call, StoryChip("winner", arrayWins ? "array" : p == 0 ? "linked" : "neither", .answer)], headline: verdict,
                 body: "Neither layout is faster everywhere. The list ADT hides which one you have, so the position you insert at decides the cost."),
    ]
}

struct ListAdtLab: View {
    private let tabs: [(String, [AdtFrame])] = [("Front", adtFrames(at: 0)), ("Middle", adtFrames(at: 2)), ("End", adtFrames(at: 5))]
    @State private var tab = 0
    @State private var playback = PlaybackState(stepCount: 4, speedMs: 1000)
    @Environment(\.palette) private var palette

    var body: some View {
        let frames = tabs[tab].1
        let frame = frames[min(playback.index, frames.count - 1)]
        let tones = Set((frame.array + frame.linked).map(\.tone))
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                LabSegments(labels: tabs.map(\.0), selected: Binding(get: { tab }, set: { select($0) }))
                label("ARRAY").padding(.top, 14)
                HStack(spacing: 5) {
                    ForEach(frame.array.indices, id: \.self) { i in
                        Text("\(i)").font(AppFont.mono(11)).foregroundStyle(palette.muted).frame(maxWidth: .infinity)
                    }
                }
                .padding(.top, 6)
                HStack(alignment: .top, spacing: 5) {
                    ForEach(frame.array.indices, id: \.self) { i in
                        VStack(spacing: 4) {
                            AdtTile(cell: frame.array[i])
                            Text(frame.array[i].caption ?? " ").font(AppFont.mono(11, .bold)).foregroundStyle(StoryTone.path.ink(palette))
                        }
                    }
                }
                .padding(.top, 4)
                label("LINKED").padding(.top, 8)
                HStack(spacing: 2) {
                    ForEach(frame.linked.indices, id: \.self) { i in
                        AdtTile(cell: frame.linked[i])
                        if i < frame.linked.count - 1 {
                            Text("→").font(AppFont.mono(13, .bold))
                                .foregroundStyle(frame.newArrows.contains(i) ? StoryTone.answer.ink(palette) : palette.muted)
                        }
                    }
                }
                .padding(.top, 8)
                StoryFormulaRows(rows: frame.rows).padding(.top, 14)
                StoryLegendRow(items: [(AdtTone.written, SimColors.answer, "Written"), (.shifted, SimColors.blue, "Shifted"), (.walked, SimColors.active, "Walked")]
                    .filter { tones.contains($0.0) }.map { ($0.1, .fill, $0.2) })
                    .padding(.top, 14)
            }
            StoryChips(chips: frame.chips).padding(.top, 16)
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) })
        }
    }

    private func label(_ text: String) -> some View {
        Text(text).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted)
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        playback = PlaybackState(stepCount: tabs[i].1.count, speedMs: 1000)
    }
}

private struct AdtTile: View {
    let cell: AdtCell
    @Environment(\.palette) private var palette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 8)
        let (fill, ink): (Color, Color) = switch cell.tone {
        case .idle: (palette.muted.opacity(0.2), palette.onSurface)
        case .written: (SimColors.answer, .white)
        case .shifted: (SimColors.blue.opacity(0.3), palette.onSurface)
        case .walked: (SimColors.active, Color(hex: 0x1F1A0A))
        case .empty: (palette.muted.opacity(0.08), palette.muted)
        }
        Text(cell.text)
            .font(AppFont.mono(16, .bold))
            .foregroundStyle(ink)
            .lineLimit(1).minimumScaleFactor(0.7)
            .frame(maxWidth: .infinity).frame(height: 42)
            .background(fill, in: shape)
            .overlay {
                if cell.tone == .shifted { shape.strokeBorder(SimColors.blue, lineWidth: 1.5) }
                else if cell.tone == .empty { shape.strokeBorder(palette.muted.opacity(0.35), style: StrokeStyle(lineWidth: 1.2, dash: [4, 3])) }
            }
    }
}
