import SwiftUI

// Port of the k-d tree lab in PointCloudSection.kt, built to docs/ios-design/Simulations iOS.html
// (K-D Tree): a K-D tree / Quadtree toggle over the plot, splits revealed one level at a time, then a
// nearest-neighbour query whose dashed circle is the best distance so far — with chips counting how
// many points it actually measured. Both structures index the same 18 points so the two builds can be
// compared step for step.

private struct SpacePoint: Hashable { let x: Float; let y: Float }

/// Kotlin's fixed-seed LCG from PointCloudSection.kt, bit for bit, so both platforms plot the same
/// points and the narration's counts hold on each.
private struct SpaceLcg {
    var state: Int32
    mutating func next() -> Float {
        state = state &* 1103515245 &+ 12345
        return Float((UInt32(bitPattern: state) >> 16) & 0x7fff) / 32767
    }
}

private let spacePoints: [SpacePoint] = {
    var rng = SpaceLcg(state: 2027)
    return (0..<18).map { _ in
        let x = 0.06 + rng.next() * 0.88
        let y = 0.06 + rng.next() * 0.88
        return SpacePoint(x: x, y: y)
    }
}()

private let spaceQuery = SpacePoint(x: 0.62, y: 0.38)
/// Deep enough to give most of the 18 points a cell of their own, shallow enough to stay legible.
private let spaceMaxDepth = 4

private enum SpaceIndex: CaseIterable {
    case kd, quad
    var label: String { self == .kd ? "K-D tree" : "Quadtree" }
}

/// A split line and the tree depth it was made at; deeper lines are drawn thinner and fainter.
private struct SpaceSplit { let from: SpacePoint; let to: SpacePoint; let depth: Int }

private struct SpaceFrame {
    let status: String
    let splits: [SpaceSplit]
    var query: SpacePoint?
    var radius: Float?
    var nearest: SpacePoint?
    /// Points the query measured a distance to; when set, the rest are drawn faded.
    var measured: Set<SpacePoint>?
    let chips: [(String, String)]
    let lead: String
    let emphasis: String
    let tail: String
    let body: String
}

private func distance(_ a: SpacePoint, _ b: SpacePoint) -> Float { hypotf(a.x - b.x, a.y - b.y) }

/// Distance from a point to the nearest edge of a rectangle; zero inside it.
private func rectDistance(_ q: SpacePoint, _ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) -> Float {
    hypotf(max(x0 - q.x, 0, q.x - x1), max(y0 - q.y, 0, q.y - y1))
}

private func kdSplits(_ pts: [SpacePoint]) -> [SpaceSplit] {
    var splits: [SpaceSplit] = []
    func build(_ subset: [SpacePoint], _ depth: Int, _ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) {
        guard subset.count > 1, depth < spaceMaxDepth else { return }
        let vertical = depth % 2 == 0
        let ordered = vertical ? subset.sorted { $0.x < $1.x } : subset.sorted { $0.y < $1.y }
        let median = ordered[ordered.count / 2]
        splits.append(vertical
            ? SpaceSplit(from: SpacePoint(x: median.x, y: y0), to: SpacePoint(x: median.x, y: y1), depth: depth)
            : SpaceSplit(from: SpacePoint(x: x0, y: median.y), to: SpacePoint(x: x1, y: median.y), depth: depth))
        let left = Array(ordered.prefix(ordered.count / 2))
        let right = Array(ordered.dropFirst(ordered.count / 2 + 1))
        if vertical {
            build(left, depth + 1, x0, y0, median.x, y1)
            build(right, depth + 1, median.x, y0, x1, y1)
        } else {
            build(left, depth + 1, x0, y0, x1, median.y)
            build(right, depth + 1, x0, median.y, x1, y1)
        }
    }
    build(pts, 0, 0, 0, 1, 1)
    return splits
}

/// Nearest-neighbour descent over the same tree kdSplits draws: nearer child first, the far child only
/// if its splitting line is closer than the best distance so far. Returns the points measured.
private func kdSearch(_ pts: [SpacePoint], _ query: SpacePoint) -> Set<SpacePoint> {
    var measured = Set<SpacePoint>()
    func search(_ subset: [SpacePoint], _ depth: Int, _ bestSoFar: Float) -> Float {
        guard !subset.isEmpty else { return bestSoFar }
        var best = bestSoFar
        if subset.count == 1 || depth >= spaceMaxDepth {
            for p in subset { measured.insert(p); best = min(best, distance(p, query)) }
            return best
        }
        let vertical = depth % 2 == 0
        let ordered = vertical ? subset.sorted { $0.x < $1.x } : subset.sorted { $0.y < $1.y }
        let median = ordered[ordered.count / 2]
        measured.insert(median)
        best = min(best, distance(median, query))
        let left = Array(ordered.prefix(ordered.count / 2))
        let right = Array(ordered.dropFirst(ordered.count / 2 + 1))
        let gap = vertical ? query.x - median.x : query.y - median.y
        best = search(gap < 0 ? left : right, depth + 1, best)
        if abs(gap) < best { best = search(gap < 0 ? right : left, depth + 1, best) }
        return best
    }
    _ = search(pts, 0, .greatestFiniteMagnitude)
    return measured
}

/// A quadtree cuts every crowded cell at its midpoint into four, whatever the points look like.
private func quadSplits(_ pts: [SpacePoint]) -> [SpaceSplit] {
    var splits: [SpaceSplit] = []
    func build(_ subset: [SpacePoint], _ depth: Int, _ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float) {
        guard subset.count > 1, depth < spaceMaxDepth else { return }
        let mx = (x0 + x1) / 2, my = (y0 + y1) / 2
        splits.append(SpaceSplit(from: SpacePoint(x: mx, y: y0), to: SpacePoint(x: mx, y: y1), depth: depth))
        splits.append(SpaceSplit(from: SpacePoint(x: x0, y: my), to: SpacePoint(x: x1, y: my), depth: depth))
        build(subset.filter { $0.x < mx && $0.y < my }, depth + 1, x0, y0, mx, my)
        build(subset.filter { $0.x >= mx && $0.y < my }, depth + 1, mx, y0, x1, my)
        build(subset.filter { $0.x < mx && $0.y >= my }, depth + 1, x0, my, mx, y1)
        build(subset.filter { $0.x >= mx && $0.y >= my }, depth + 1, mx, my, x1, y1)
    }
    build(pts, 0, 0, 0, 1, 1)
    return splits
}

/// Quadtree nearest neighbour: visit quadrants nearest-first and skip any whose rectangle is already
/// farther than the best distance.
private func quadSearch(_ pts: [SpacePoint], _ query: SpacePoint) -> Set<SpacePoint> {
    var measured = Set<SpacePoint>()
    func search(_ subset: [SpacePoint], _ depth: Int, _ r: [Float], _ bestSoFar: Float) -> Float {
        var best = bestSoFar
        if subset.isEmpty || rectDistance(query, r[0], r[1], r[2], r[3]) >= best { return best }
        if subset.count == 1 || depth >= spaceMaxDepth {
            for p in subset { measured.insert(p); best = min(best, distance(p, query)) }
            return best
        }
        let mx = (r[0] + r[2]) / 2, my = (r[1] + r[3]) / 2
        // Same partition as quadSplits: the midpoint belongs to the upper/right quadrant.
        let quads: [([SpacePoint], [Float])] = [
            (subset.filter { $0.x < mx && $0.y < my }, [r[0], r[1], mx, my]),
            (subset.filter { $0.x >= mx && $0.y < my }, [mx, r[1], r[2], my]),
            (subset.filter { $0.x < mx && $0.y >= my }, [r[0], my, mx, r[3]]),
            (subset.filter { $0.x >= mx && $0.y >= my }, [mx, my, r[2], r[3]]),
        ]
        let ordered = quads.sorted { rectDistance(query, $0.1[0], $0.1[1], $0.1[2], $0.1[3]) < rectDistance(query, $1.1[0], $1.1[1], $1.1[2], $1.1[3]) }
        for (inside, rect) in ordered { best = search(inside, depth + 1, rect, best) }
        return best
    }
    _ = search(pts, 0, [0, 0, 1, 1], .greatestFiniteMagnitude)
    return measured
}

private func spaceFrames(_ index: SpaceIndex) -> [SpaceFrame] {
    let pts = spacePoints
    let n = pts.count
    let splits = index == .kd ? kdSplits(pts) : quadSplits(pts)
    let levels = (splits.map(\.depth).max() ?? -1) + 1
    let measured = index == .kd ? kdSearch(pts, spaceQuery) : quadSearch(pts, spaceQuery)
    let nearest = pts.min { distance($0, spaceQuery) < distance($1, spaceQuery) }!
    let best = distance(nearest, spaceQuery)
    let bestText = String(format: "%.3f", best)
    var frames: [SpaceFrame] = []

    frames.append(SpaceFrame(
        status: "\(n) points, no index yet.", splits: [],
        chips: [("points", "\(n)"), ("depth", "0")],
        lead: "\(n) ", emphasis: "points", tail: ", no index yet.",
        body: "Finding the one nearest a query would mean measuring all \(n). The \(index.label.lowercased()) is built so that most of them never have to be looked at."))

    for level in 0..<levels {
        let shown = splits.filter { $0.depth <= level }
        let parts: [String]
        switch (index, level) {
        case (.quad, 0):
            parts = ["The root cuts the plane into ", "four quadrants", ".",
                     "A quadtree splits at the middle of the cell on both axes at once, wherever the points happen to be."]
        case (.quad, _):
            parts = ["Every crowded cell ", "splits into four", " again.",
                     "A cell holding at most one point stops. Clusters get deep, empty space stays shallow."]
        case (.kd, 0):
            parts = ["The root ", "splits on x", " at the median point.",
                     "Half the points fall on each side, so the tree starts out balanced."]
        case (.kd, _):
            parts = ["Each cell ", "splits on \(level % 2 == 0 ? "x" : "y")", " at its own median.",
                     "The axis alternates with depth, so every coordinate gets constrained every two levels."]
        }
        frames.append(SpaceFrame(
            status: parts[0] + parts[1] + parts[2], splits: shown,
            chips: [("points", "\(n)"), ("depth", "\(level + 1)"), ("splits", "\(shown.count)")],
            lead: parts[0], emphasis: parts[1], tail: parts[2], body: parts[3]))
    }

    frames.append(SpaceFrame(
        status: "A query point arrives.", splits: splits, query: spaceQuery,
        chips: [("points", "\(n)"), ("depth", "\(levels)")],
        lead: "A ", emphasis: "query", tail: " point arrives.",
        body: "Descending is one comparison per level: at each split, go to the side the query is on. \(levels) levels lead to a single cell."))

    frames.append(SpaceFrame(
        status: "The query drops into its cell, then checks only cells that could hold something closer.",
        splits: splits, query: spaceQuery, radius: best, nearest: nearest, measured: measured,
        chips: [("points", "\(n)"), ("measured", "\(measured.count) / \(n)"), ("depth", "\(levels)")],
        lead: "The ", emphasis: "query", tail: " drops into its cell, then checks only cells that could hold something closer.",
        body: "The dashed circle is the best distance so far. Cells it doesn't touch are skipped, so \(measured.count) of \(n) points were measured."))

    frames.append(SpaceFrame(
        status: "The nearest point is \(bestText) away.",
        splits: splits, query: spaceQuery, radius: best, nearest: nearest, measured: measured,
        chips: [("points", "\(n)"), ("measured", "\(measured.count) / \(n)"), ("distance", bestText)],
        lead: "The ", emphasis: "nearest", tail: " point is \(bestText) away.",
        body: "Brute force would have measured all \(n). Skipping a whole cell whenever it lies outside the circle is what makes the average query O(log n)."))

    let closing: [String] = index == .kd
        ? ["In high dimensions the tree stops ", "pruning", ".",
           "Each level constrains one axis, so in d dimensions a query needs d levels before every coordinate is bounded. Once d nears log n almost every cell touches the circle and the search is a scan again."]
        : ["Midpoints ignore the ", "data", ".",
           "A quadtree never looks at the points to pick a cut, so a tight cluster makes it deep and lopsided. The k-d tree's medians keep it balanced, and in 3-D the same idea with eight children is an octree."]
    frames.append(SpaceFrame(
        status: closing[0] + closing[1] + closing[2], splits: splits, nearest: nearest,
        chips: [("points", "\(n)"), ("depth", "\(levels)"), ("splits", "\(splits.count)")],
        lead: closing[0], emphasis: closing[1], tail: closing[2], body: closing[3]))
    return frames
}

struct KdTreeLab: View {
    @State private var index: SpaceIndex = .kd
    @State private var frames = spaceFrames(.kd)
    @State private var playback = PlaybackState(stepCount: spaceFrames(.kd).count, speedMs: 900)
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    /// The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
    private var highlight: Color { scheme == .dark ? SimColors.active : Color(hex: 0xB7791F) }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                IndexToggle(selected: index) { index = $0 }
                SpacePlot(frame: frame).padding(.top, 14)
                FlowLayout(spacing: 16, lineSpacing: 6) {
                    SpaceSwatch(color: SimColors.blue, label: "Point")
                    SpaceSwatch(color: splitColor, label: "Split")
                    SpaceSwatch(color: SimColors.active, label: "Query")
                    SpaceSwatch(color: SimColors.green, label: "Nearest")
                }
                .padding(.top, 14)
            }
            FlowLayout(spacing: 8, lineSpacing: 8) {
                ForEach(frame.chips.indices, id: \.self) { SpaceChip(label: frame.chips[$0].0, value: frame.chips[$0].1) }
            }
            .padding(.top, 16)
            (Text(frame.lead) + Text(frame.emphasis).foregroundColor(highlight) + Text(frame.tail))
                .font(AppFont.sans(20, .bold))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            Text(frame.body).font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3).padding(.top, 8)
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
        .onChange(of: index) { _, value in
            frames = spaceFrames(value)
            playback.load(stepCount: frames.count)
        }
    }
}

/// The split violet (AxisColor on Android).
private let splitColor = Color(hex: 0x7C3AED)

private struct IndexToggle: View {
    let selected: SpaceIndex
    let onSelect: (SpaceIndex) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            ForEach(SpaceIndex.allCases, id: \.self) { option in
                let on = option == selected
                Button { onSelect(option) } label: {
                    Text(option.label)
                        .font(AppFont.sans(14, on ? .bold : .medium))
                        .foregroundStyle(on ? palette.onSurface : palette.muted)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(on ? palette.muted.opacity(0.3) : .clear, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
            Spacer(minLength: 0)
        }
        .padding(3)
        .background(palette.muted.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct SpaceSwatch: View {
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

private struct SpaceChip: View {
    let label: String
    let value: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            Text(label).foregroundStyle(palette.muted)
            Text(value).fontWeight(.bold).foregroundStyle(palette.onSurface)
        }
        .font(.system(size: 15, design: .monospaced))
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(palette.muted.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct SpacePlot: View {
    let frame: SpaceFrame
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in draw(ctx, size) }
            .frame(height: 240)
            .background(palette.muted.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let pad: CGFloat = 14
        let w = size.width - 2 * pad, h = size.height - 2 * pad
        // y is flipped so the plot reads like a chart rather than like screen coordinates.
        func place(_ p: SpacePoint) -> CGPoint { CGPoint(x: pad + CGFloat(p.x) * w, y: pad + CGFloat(1 - p.y) * h) }

        // Shallow splits carry the structure, so they are drawn heavier than the deep ones.
        for split in frame.splits.sorted(by: { $0.depth > $1.depth }) {
            let strengths: [Double] = [1, 0.8, 0.6, 0.45]
            let strength = split.depth < strengths.count ? strengths[split.depth] : 0.45
            var line = Path()
            line.move(to: place(split.from))
            line.addLine(to: place(split.to))
            ctx.stroke(line, with: .color(splitColor.opacity(strength)), lineWidth: max(2.5 - 0.4 * CGFloat(split.depth), 1))
        }

        if let query = frame.query, let radius = frame.radius {
            let r = CGFloat(radius) * min(w, h)
            let c = place(query)
            let circle = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
            ctx.fill(circle, with: .color(SimColors.active.opacity(0.12)))
            ctx.stroke(circle, with: .color(SimColors.active.opacity(0.8)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
        }

        for p in spacePoints where p != frame.nearest {
            let faded = frame.measured.map { !$0.contains(p) } ?? false
            let c = place(p)
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 4.5, y: c.y - 4.5, width: 9, height: 9)), with: .color(SimColors.blue.opacity(faded ? 0.3 : 1)))
        }
        if let nearest = frame.nearest {
            let c = place(nearest)
            let dot = Path(ellipseIn: CGRect(x: c.x - 6.5, y: c.y - 6.5, width: 13, height: 13))
            ctx.fill(dot, with: .color(SimColors.green))
            ctx.stroke(dot, with: .color(palette.surface), lineWidth: 1.5)
        }

        if let query = frame.query {
            let c = place(query)
            let arm: CGFloat = 7
            var cross = Path()
            cross.move(to: CGPoint(x: c.x - arm, y: c.y - arm))
            cross.addLine(to: CGPoint(x: c.x + arm, y: c.y + arm))
            cross.move(to: CGPoint(x: c.x - arm, y: c.y + arm))
            cross.addLine(to: CGPoint(x: c.x + arm, y: c.y - arm))
            ctx.stroke(cross, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
        }
    }
}
