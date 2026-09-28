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
        let id = walkConfigs[topicId] == nil ? "two_pointer" : topicId
        let config = walkConfigFor(id)
        AsyncFrameLab(key: "walk:\(id)", speedMs: 650, build: { walkConfigFor(id).build() }) { frames, playback in
            let frame = frames[min(max(playback.index, 0), frames.count - 1)]
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
