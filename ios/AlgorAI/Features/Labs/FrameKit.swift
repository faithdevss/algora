import SwiftUI

// Shared pieces of the precomputed-frame "player" labs (the RL, game-search and exploration
// sections). On Android each section keeps a private copy of the same curve plot, signed bar strip,
// legend and 32-bit LCG; here they live once.

extension Font {
    /// Material3 defaults the Android theme leaves unset.
    static let labelSmall = AppFont.sans(11, .medium)
    static let labelMedium = AppFont.sans(12, .medium)
    static let bodySmall = AppFont.sans(12)
    static let titleSmall = AppFont.sans(14, .medium)
    static let headlineSmall = AppFont.sans(24)
}

/// The labs' `state * 1103515245 + 12345` generator with Kotlin's wrapping Int arithmetic.
struct Lcg {
    var state: Int32
    init(_ seed: Int) { state = Int32(truncatingIfNeeded: seed) }

    private mutating func advance() -> Int32 {
        state = state &* 1_103_515_245 &+ 12345
        return Int32((UInt32(bitPattern: state) >> 16) & 0x7fff)
    }

    /// The Double flavour (`.toDouble() / 32767.0`).
    mutating func next() -> Double { Double(advance()) / 32767.0 }

    /// The Float flavour (`/ 32767f`).
    mutating func nextF() -> Float { Float(advance()) / 32767 }

    /// `(next() * bound).toInt().coerceIn(0, bound - 1)` over the Double flavour.
    mutating func nextInt(_ bound: Int) -> Int { min(max(Int(next() * Double(bound)), 0), bound - 1) }

    /// The same over the Float flavour.
    mutating func nextIntF(_ bound: Int) -> Int { min(max(Int(nextF() * Float(bound)), 0), bound - 1) }
}

/// Kotlin's `"%.Nf".format(x)`.
func fx(_ x: Double, _ digits: Int = 2) -> String { String(format: "%.\(digits)f", x) }
func fx(_ x: Float, _ digits: Int = 2) -> String { String(format: "%.\(digits)f", Double(x)) }

extension Array where Element == Double {
    /// Kotlin's `average()`: NaN when empty.
    var average: Double { isEmpty ? .nan : reduce(0, +) / Double(count) }
}

extension Array where Element == Float {
    var average: Double { isEmpty ? .nan : reduce(0.0) { $0 + Double($1) } / Double(count) }
}

extension Array where Element == Int {
    var average: Double { isEmpty ? .nan : Double(reduce(0, +)) / Double(count) }
}

extension Sequence {
    /// Kotlin's stable `sortedBy`.
    func sortedBy<K: Comparable>(_ key: (Element) -> K) -> [Element] {
        Array(self).enumerated().sorted { a, b in
            let ka = key(a.element), kb = key(b.element)
            return ka == kb ? a.offset < b.offset : ka < kb
        }.map(\.element)
    }

    /// Kotlin's stable `sortedByDescending`.
    func sortedByDescending<K: Comparable>(_ key: (Element) -> K) -> [Element] {
        Array(self).enumerated().sorted { a, b in
            let ka = key(a.element), kb = key(b.element)
            return ka == kb ? a.offset < b.offset : ka > kb
        }.map(\.element)
    }
}

/// Index of the first maximum (Kotlin's `indices.maxBy`).
func argmaxFirst<T: Comparable>(_ xs: [T]) -> Int {
    var best = 0
    for i in xs.indices where xs[i] > xs[best] { best = i }
    return best
}

// MARK: - Plot

struct FrameCurve {
    let label: String
    let values: [Float]
    let color: Color
}

struct FramePlot {
    let label: String
    let curves: [FrameCurve]
    let yRange: ClosedRange<Float>
    let xLabel: String
}

/// Line plot over frame index: a zero line when 0 is in range, one polyline per curve.
struct FramePlotView: View {
    let plot: FramePlot
    /// Android draws the x label at the end of the legend row in most sections, on its own line in a few.
    var xLabelInline = true
    /// GameSearchSection's variant: a dot on every point, and a lone point centred.
    var dots = false
    @Environment(\.palette) private var palette

    var body: some View {
        let span = plot.curves.map(\.values.count).max() ?? 1
        VStack(alignment: .leading, spacing: 0) {
            Text(plot.label).font(.labelSmall).foregroundStyle(palette.muted)
            Canvas { ctx, size in
                let lo = plot.yRange.lowerBound, hi = plot.yRange.upperBound
                func place(_ i: Int, _ v: Float) -> CGPoint {
                    let x = span <= 1 ? (dots ? size.width / 2 : 0) : CGFloat(i) / CGFloat(span - 1) * size.width
                    let ny = min(max((v - lo) / (hi - lo), -0.05), 1.05)
                    return CGPoint(x: x, y: size.height - CGFloat(ny) * size.height)
                }
                if plot.yRange.contains(0) {
                    let y = place(0, 0).y
                    var line = Path()
                    line.move(to: CGPoint(x: 0, y: y))
                    line.addLine(to: CGPoint(x: size.width, y: y))
                    ctx.stroke(line, with: .color(palette.muted.opacity(0.4)), lineWidth: 1.5)
                }
                if dots {
                    for curve in plot.curves {
                        for (i, v) in curve.values.enumerated() {
                            let c = place(i, v)
                            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5, y: c.y - 5, width: 10, height: 10)), with: .color(curve.color))
                        }
                    }
                }
                for curve in plot.curves where curve.values.count > 1 {
                    var path = Path()
                    path.move(to: place(0, curve.values[0]))
                    for i in 1..<curve.values.count { path.addLine(to: place(i, curve.values[i])) }
                    ctx.stroke(path, with: .color(curve.color), style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
                }
            }
            .frame(height: 150)
            .padding(8)
            .background(palette.outlineVariant.opacity(0.3), in: RoundedRectangle(cornerRadius: 12))
            .padding(.top, 4)
            HStack(spacing: 10) {
                ForEach(plot.curves.indices, id: \.self) { i in
                    HStack(spacing: 3) {
                        Circle().fill(plot.curves[i].color).frame(width: 8, height: 8)
                        Text(plot.curves[i].label).font(.labelSmall).foregroundStyle(palette.muted)
                    }
                }
                if xLabelInline {
                    Text(plot.xLabel).font(.labelSmall).foregroundStyle(palette.muted).frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
            .padding(.top, 4)
            if !xLabelInline {
                Text(plot.xLabel).font(.labelSmall).foregroundStyle(palette.muted)
                    .frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 2)
            }
        }
    }
}

// MARK: - Bars

struct FrameBars {
    let label: String
    let values: [Float]
    let color: Color
    var captions: [String] = []
}

/// Signed bar strip around a mid axis; negatives take `negativeColor`.
struct FrameBarsView: View {
    let bar: FrameBars
    let negativeColor: Color
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(bar.label).font(.labelSmall).foregroundStyle(palette.muted)
            Canvas { ctx, size in
                guard !bar.values.isEmpty else { return }
                let peak = max(bar.values.map { abs($0) }.max() ?? 1, 0.001)
                let slot = size.width / CGFloat(bar.values.count)
                let mid = size.height / 2
                var axis = Path()
                axis.move(to: CGPoint(x: 0, y: mid))
                axis.addLine(to: CGPoint(x: size.width, y: mid))
                ctx.stroke(axis, with: .color(palette.muted.opacity(0.35)), lineWidth: 1.5)
                for (i, v) in bar.values.enumerated() {
                    let h = max(CGFloat(abs(v) / peak) * (mid - 2), 1.5)
                    let rect = CGRect(x: CGFloat(i) * slot + slot * 0.2, y: v >= 0 ? mid - h : mid, width: slot * 0.6, height: h)
                    ctx.fill(Path(roundedRect: rect, cornerRadius: 1.5), with: .color(v >= 0 ? bar.color : negativeColor))
                }
            }
            .frame(height: 42)
            .padding(.top, 4)
            if !bar.captions.isEmpty {
                HStack(spacing: 0) {
                    ForEach(bar.captions.indices, id: \.self) { i in
                        Text(bar.captions[i]).font(.labelSmall).foregroundStyle(palette.muted)
                            .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

// MARK: - Chrome

/// Legend (closing the stage), readout chips, narration and transport: the tail every frame player
/// shares.
struct FramePlayerFooter: View {
    let readout: String?
    let status: String
    let legend: [(Color, String)]
    let playback: PlaybackState
    var statusTop: CGFloat = 10
    /// Every step's status, for the scrub preview and the All steps sheet.
    var captions: [String]? = nil

    var body: some View {
        if !legend.isEmpty { LabLegend(items: legend).padding(.top, 12) }
        if let readout { ReadoutChips(text: readout).padding(.top, 14) }
        LabCaption(text: status).padding(.top, readout == nil ? statusTop + 4 : 14)
        PlaybackTransport(state: playback, captions: captions)
    }
}

/// A lab's legend row: 10pt swatches, 13pt labels.
struct LabLegend: View {
    let items: [(Color, String)]

    var body: some View {
        FlowLayout(spacing: 14, lineSpacing: 6) {
            ForEach(items.indices, id: \.self) { i in LegendItem(color: items[i].0, label: items[i].1) }
        }
    }
}

/// Memoizes a lab's precomputed frames by key, so a SwiftUI re-init of the view doesn't re-run
/// the simulation behind them.
@MainActor
enum LabCache {
    private static var store: [String: Any] = [:]

    static func peek<T>(_ key: String) -> T? { store[key] as? T }

    static func put(_ key: String, _ value: Any) { store[key] = value }

    static func get<T>(_ key: String, _ build: () -> T) -> T {
        if let hit = store[key] as? T { return hit }
        let value = build()
        store[key] = value
        return value
    }
}

/// Compose's `Row` with `Modifier.weight`: splits the proposed width between children in
/// proportion to `weights` (one per child), each child centred vertically in the row.
struct WeightedRow: Layout {
    let weights: [CGFloat]
    var spacing: CGFloat = 0

    private func widths(_ total: CGFloat, _ count: Int) -> [CGFloat] {
        let w = (0..<count).map { $0 < weights.count ? weights[$0] : 1 }
        let sum = max(w.reduce(0, +), 0.0001)
        let avail = max(total - spacing * CGFloat(max(count - 1, 0)), 0)
        return w.map { avail * $0 / sum }
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let total = proposal.width ?? 320
        let ws = widths(total, subviews.count)
        let height = subviews.indices.map { subviews[$0].sizeThatFits(ProposedViewSize(width: ws[$0], height: nil)).height }.max() ?? 0
        return CGSize(width: total, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let ws = widths(bounds.width, subviews.count)
        var x = bounds.minX
        for i in subviews.indices {
            subviews[i].place(at: CGPoint(x: x, y: bounds.midY), anchor: .leading, proposal: ProposedViewSize(width: ws[i], height: bounds.height))
            x += ws[i] + spacing
        }
    }
}

/// Runs a lab's frame builder off the main actor (some take seconds: whole training runs), caches
/// the result by key, and shows a loading card until it lands.
struct AsyncFrameLab<Frame, Content: View>: View {
    let key: String
    let speedMs: Double
    let build: @Sendable () -> [Frame]
    @ViewBuilder let content: ([Frame], PlaybackState) -> Content
    @State private var frames: [Frame]?
    @State private var playback = PlaybackState(stepCount: 1)
    @Environment(\.palette) private var palette

    init(key: String, speedMs: Double, build: @escaping @Sendable () -> [Frame], @ViewBuilder content: @escaping ([Frame], PlaybackState) -> Content) {
        self.key = key
        self.speedMs = speedMs
        self.build = build
        self.content = content
        let cached: [Frame]? = LabCache.peek(key)
        _frames = State(initialValue: cached)
        _playback = State(initialValue: PlaybackState(stepCount: cached?.count ?? 1, speedMs: speedMs))
    }

    var body: some View {
        if let frames {
            content(frames, playback)
        } else {
            LabCard {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Running the simulation…").font(.bodyMedium).foregroundStyle(palette.muted)
                }
                .frame(maxWidth: .infinity, minHeight: 120)
            }
            .task(id: key) {
                let build = self.build
                let result = await Task.detached(priority: .userInitiated) { build() }.value
                LabCache.put(key, result)
                playback.load(stepCount: result.count)
                frames = result
            }
        }
    }
}
