import SwiftUI

// Port of feature/topics/FigureCard.kt: the still picture above a topic's How-It-Works steps.
// Tones are roles resolved here against the live palette, so one spec reads in both themes.

private let labelSmall = AppFont.sans(11, .medium)
private let labelSmallBold = AppFont.sans(11, .bold)
private let labelMedium = AppFont.sans(12, .medium)

extension Palette {
    func tone(_ tone: FigureTone) -> Color {
        switch tone {
        case .Primary: SimColors.blue
        case .Accent: SimColors.amber
        // Full strength; callers fade it for fills, and it is also the text colour in a muted band.
        case .Muted: muted
        case .Warn: SimColors.red
        }
    }
}

struct FigureCard: View {
    let figure: Figure
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            switch figure.shape {
            case .strip(let s): StripFigure(shape: s)
            case .timeline(let s): TimelineFigure(shape: s)
            case .grid(let s): GridFigure(shape: s)
            case .stacks(let s): StacksFigure(shape: s)
            case .tree(let s): TreeFigure(shape: s)
            case .graph(let s): GraphFigure(shape: s)
            case .plot(let s): PlotFigure(shape: s)
            case .layerStack(let s): LayerStackFigure(shape: s)
            case .heatmap(let s): HeatmapFigure(shape: s)
            }
            Text(figure.caption)
                .font(.bodyMedium)
                .foregroundStyle(palette.muted)
                .padding(.top, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .card(radius: 18)
    }
}

// MARK: - Strip

private struct StripFigure: View {
    let shape: FigureShape.Strip
    @Environment(\.palette) private var palette

    private func band(at index: Int) -> FigureBand? {
        shape.bands.first { index >= $0.from && index <= $0.to }
    }

    var body: some View {
        let count = shape.cells.count
        GeometryReader { geo in
            content(cell: (geo.size.width - gap * CGFloat(max(count - 1, 0))) / CGFloat(max(count, 1)))
        }
        .frame(height: stripHeight)
    }

    private let gap: CGFloat = 4

    @ViewBuilder
    private func content(cell: CGFloat) -> some View {
        let count = shape.cells.count
        let x = { (i: Int) in CGFloat(i) * (cell + gap) }
            VStack(alignment: .leading, spacing: 3) {
                if !shape.bands.isEmpty {
                    ZStack(alignment: .topLeading) {
                        ForEach(Array(shape.bands.enumerated()), id: \.offset) { _, band in
                            let width = x(band.to) + cell - x(band.from)
                            Text(band.label)
                                .font(labelSmallBold)
                                .foregroundStyle(palette.tone(band.tone))
                                .lineLimit(1)
                                .minimumScaleFactor(0.6)
                                .frame(width: width)
                                .offset(x: x(band.from))
                        }
                    }
                    .frame(height: 15, alignment: .topLeading)
                }
                HStack(spacing: gap) {
                    ForEach(Array(shape.cells.enumerated()), id: \.offset) { i, text in
                        let band = band(at: i)
                        let fill = band.map { palette.tone($0.tone) } ?? palette.muted
                        Text(text)
                            .font(band == nil ? labelMedium : AppFont.sans(12, .bold))
                            .foregroundStyle(band == nil ? palette.onSurface : fill)
                            .lineLimit(1)
                            .minimumScaleFactor(0.5)
                            .frame(width: cell, height: 38)
                            .background(fill.opacity(band == nil ? 0.10 : 0.22), in: RoundedRectangle(cornerRadius: 8))
                    }
                }
                if !shape.pointers.isEmpty {
                    HStack(spacing: gap) {
                        ForEach(0..<count, id: \.self) { i in
                            Group {
                                if let pointer = shape.pointers.first(where: { $0.index == i }) {
                                    Text("▲\(pointer.label)")
                                        .font(labelSmallBold)
                                        .foregroundStyle(palette.tone(pointer.tone))
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.6)
                                } else {
                                    Color.clear
                                }
                            }
                            .frame(width: cell, height: 15)
                        }
                    }
                }
                if !shape.aux.isEmpty {
                    if let label = shape.auxLabel {
                        Text(label).font(labelSmall).foregroundStyle(palette.muted).padding(.top, 6)
                    }
                    HStack(spacing: gap) {
                        ForEach(Array(shape.aux.enumerated()), id: \.offset) { _, text in
                            Text(text)
                                .font(labelSmall)
                                .foregroundStyle(palette.muted)
                                .lineLimit(1)
                                .minimumScaleFactor(0.5)
                                .frame(width: cell, height: 28)
                                .background(palette.muted.opacity(0.10), in: RoundedRectangle(cornerRadius: 7))
                        }
                    }
                }
            }
    }

    private var stripHeight: CGFloat {
        var h: CGFloat = 38
        if !shape.bands.isEmpty { h += 15 + 3 }
        if !shape.pointers.isEmpty { h += 15 + 3 }
        if !shape.aux.isEmpty { h += 28 + 3 + (shape.auxLabel == nil ? 0 : 6 + 14 + 3) }
        return h
    }
}

// MARK: - Canvas helpers

extension GraphicsContext {
    /// Draws text centred on a point.
    func label(_ text: String, at point: CGPoint, font: Font, color: Color, anchor: UnitPoint = .center) {
        draw(Text(text).font(font).foregroundStyle(color), at: point, anchor: anchor)
    }

    func measure(_ text: String, font: Font) -> CGSize {
        resolve(Text(text).font(font)).measure(in: CGSize(width: 1000, height: 1000))
    }

    func line(_ a: CGPoint, _ b: CGPoint, color: Color, width: CGFloat, dash: [CGFloat] = []) {
        var path = Path()
        path.move(to: a)
        path.addLine(to: b)
        stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, dash: dash))
    }

    /// A line with a two-stroke arrowhead at `end`.
    func arrow(_ start: CGPoint, _ end: CGPoint, color: Color, width: CGFloat, head: CGFloat) {
        line(start, end, color: color, width: width)
        let dx = end.x - start.x, dy = end.y - start.y
        let length = max(hypot(dx, dy), 1)
        let ux = dx / length, uy = dy / length
        line(end, CGPoint(x: end.x - (ux + uy) * head, y: end.y - (uy - ux) * head), color: color, width: width)
        line(end, CGPoint(x: end.x - (ux - uy) * head, y: end.y - (uy + ux) * head), color: color, width: width)
    }
}

// MARK: - Grid

private struct GridFigure: View {
    let shape: FigureShape.Grid
    @Environment(\.palette) private var palette

    var body: some View {
        let rows = shape.rows.count
        let cols = shape.rows.map(\.count).max() ?? 1
        let hasRowHeaders = !shape.rowHeaders.isEmpty
        let hasColHeaders = !shape.colHeaders.isEmpty
        let header = AppFont.sans(9, .bold)
        Canvas { ctx, size in
            let headerWidth = hasRowHeaders ? size.width * 0.14 : 0
            let headerHeight = hasColHeaders ? size.height * 0.14 : 0
            let cellW = (size.width - headerWidth) / CGFloat(cols)
            let cellH = (size.height - headerHeight) / CGFloat(rows)
            func centre(_ r: Int, _ c: Int) -> CGPoint {
                CGPoint(x: headerWidth + (CGFloat(c) + 0.5) * cellW, y: headerHeight + (CGFloat(r) + 0.5) * cellH)
            }
            for (c, text) in shape.colHeaders.enumerated() {
                ctx.label(text, at: CGPoint(x: headerWidth + (CGFloat(c) + 0.5) * cellW, y: headerHeight / 2), font: header, color: palette.muted)
            }
            for (r, cells) in shape.rows.enumerated() {
                if hasRowHeaders, r < shape.rowHeaders.count {
                    ctx.label(shape.rowHeaders[r], at: CGPoint(x: headerWidth / 2, y: headerHeight + (CGFloat(r) + 0.5) * cellH), font: header, color: palette.muted)
                }
                for (c, text) in cells.enumerated() {
                    let mark = shape.marks.first { $0.row == r && $0.col == c }
                    let tone = mark.map { palette.tone($0.tone) }
                    let p = centre(r, c)
                    let rect = CGRect(x: p.x - cellW / 2 + 2, y: p.y - cellH / 2 + 2, width: cellW - 4, height: cellH - 4)
                    ctx.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(tone?.opacity(0.22) ?? palette.muted.opacity(0.10)))
                    ctx.label(text, at: p, font: AppFont.sans(11, tone == nil ? .medium : .bold), color: tone ?? palette.onSurface)
                }
            }
            for arrow in shape.arrows {
                let from = centre(arrow.fromRow, arrow.fromCol)
                let to = centre(arrow.toRow, arrow.toCol)
                let color = palette.tone(arrow.tone)
                let dx = to.x - from.x, dy = to.y - from.y
                let length = max(hypot(dx, dy), 1)
                let ux = dx / length, uy = dy / length
                // Stop short so the head sits beside the cell, not on its text.
                let inset = min(cellW, cellH) * 0.34
                let start = CGPoint(x: from.x + ux * inset, y: from.y + uy * inset)
                let end = CGPoint(x: to.x - ux * inset, y: to.y - uy * inset)
                ctx.arrow(start, end, color: color, width: 2, head: 7)
                if let label = arrow.label {
                    let push = min(cellW, cellH) * 0.34
                    ctx.label(label, at: CGPoint(x: (start.x + end.x) / 2 + uy * push, y: (start.y + end.y) / 2 - ux * push), font: header, color: color)
                }
            }
        }
        .frame(height: 30 * CGFloat(rows) + (hasColHeaders ? 16 : 0))
    }
}

// MARK: - Graph

private struct GraphFigure: View {
    let shape: FigureShape.Graph
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let inset: CGFloat = 16
            func centre(_ i: Int) -> CGPoint {
                CGPoint(x: inset + shape.nodes[i].x * (size.width - 2 * inset), y: inset + shape.nodes[i].y * (size.height - 2 * inset))
            }
            // Proportional radius, capped by the closest pair so dense graphs never overlap.
            var tightest = CGFloat.greatestFiniteMagnitude
            for a in shape.nodes.indices {
                for b in shape.nodes.indices where b > a {
                    let p = centre(a), q = centre(b)
                    tightest = min(tightest, hypot(p.x - q.x, p.y - q.y))
                }
            }
            let radius = max(min(size.height * 0.15, tightest * 0.45), size.height * 0.07)
            let edgeFont = AppFont.sans(9, .medium)

            for edge in shape.edges {
                let from = centre(edge.from), to = centre(edge.to)
                let color = palette.tone(edge.tone)
                let dx = to.x - from.x, dy = to.y - from.y
                let length = max(hypot(dx, dy), 1)
                let ux = dx / length, uy = dy / length
                let start = CGPoint(x: from.x + ux * radius, y: from.y + uy * radius)
                let end = CGPoint(x: to.x - ux * radius, y: to.y - uy * radius)
                if edge.directed {
                    ctx.arrow(start, end, color: color, width: 2, head: 8)
                } else {
                    ctx.line(start, end, color: color, width: 2)
                }
                if let label = edge.label {
                    let textSize = ctx.measure(label, font: edgeFont)
                    let clearance = textSize.height / 2 + 3
                    let mid = CGPoint(x: (start.x + end.x) / 2, y: (start.y + end.y) / 2)
                    ctx.label(label, at: CGPoint(x: mid.x + uy * clearance, y: mid.y - ux * clearance), font: edgeFont, color: palette.muted)
                }
            }
            for (i, node) in shape.nodes.enumerated() {
                let color = palette.tone(node.tone)
                let p = centre(i)
                let circle = Path(ellipseIn: CGRect(x: p.x - radius, y: p.y - radius, width: radius * 2, height: radius * 2))
                ctx.fill(circle, with: .color(color.opacity(0.24)))
                ctx.stroke(circle, with: .color(color), lineWidth: 1.8)
                // Shrink a long label to fit rather than letting it hang outside the circle.
                let room = 2 * radius - 6
                var font = AppFont.sans(10, .bold)
                if ctx.measure(node.label, font: font).width > room { font = AppFont.sans(8, .bold) }
                if ctx.measure(node.label, font: font).width > room { font = AppFont.sans(7, .bold) }
                ctx.label(node.label, at: p, font: font, color: palette.onSurface)
            }
        }
        .frame(height: shape.nodes.count > 6 ? 168 : 132)
    }
}

// MARK: - Tree

private struct TreeFigure: View {
    let shape: FigureShape.Tree
    @Environment(\.palette) private var palette

    var body: some View {
        let nodes = shape.nodes
        var depth = [Int](repeating: 0, count: nodes.count)
        for (i, node) in nodes.enumerated() { depth[i] = node.parent.map { depth[$0] + 1 } ?? 0 }
        let levels = (depth.max() ?? 0) + 1
        var children: [Int: [Int]] = [:]
        for (i, node) in nodes.enumerated() { if let p = node.parent { children[p, default: []].append(i) } }
        let leaves = nodes.indices.filter { (children[$0] ?? []).isEmpty }
        var slot: [Int: Double] = [:]
        for (s, i) in leaves.enumerated() { slot[i] = Double(s) }
        // Parents after their children: indices descend, so every child already has a slot.
        for i in nodes.indices.reversed() {
            if let kids = children[i], !kids.isEmpty {
                slot[i] = kids.map { slot[$0] ?? 0 }.reduce(0, +) / Double(kids.count)
            }
        }
        let slots = max(leaves.count, 1)
        let font = AppFont.sans(9, .bold)

        return Canvas { ctx, size in
            let rowHeight = size.height / CGFloat(levels)
            let slotWidth = size.width / CGFloat(slots)
            let widest = nodes.map { ctx.measure($0.label, font: font).width }.max() ?? 0
            let radius = min(max(rowHeight * 0.30, widest / 2 + 5), slotWidth * 0.46)
            func centre(_ i: Int) -> CGPoint {
                CGPoint(x: (CGFloat(slot[i] ?? 0) + 0.5) * slotWidth, y: (CGFloat(depth[i]) + 0.5) * rowHeight)
            }
            for (i, node) in nodes.enumerated() {
                if let p = node.parent { ctx.line(centre(p), centre(i), color: palette.muted.opacity(0.4), width: 1.6) }
            }
            for (i, node) in nodes.enumerated() {
                let color = palette.tone(node.tone)
                let p = centre(i)
                let circle = Path(ellipseIn: CGRect(x: p.x - radius, y: p.y - radius, width: radius * 2, height: radius * 2))
                ctx.fill(circle, with: .color(color.opacity(0.22)))
                ctx.stroke(circle, with: .color(color), lineWidth: 1.6)
                ctx.label(node.label, at: p, font: font, color: node.tone == .Muted ? palette.onSurface : color)
            }
        }
        .frame(height: 34 * CGFloat(levels) + 8)
    }
}

// MARK: - Stacks

private struct StacksFigure: View {
    let shape: FigureShape.Stacks
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ForEach(Array(shape.columns.enumerated()), id: \.offset) { _, column in
                let tone = palette.tone(column.tone)
                VStack(alignment: .leading, spacing: 3) {
                    Text(column.label).font(labelSmallBold).foregroundStyle(tone)
                    ForEach(Array(column.entries.enumerated()), id: \.offset) { index, entry in
                        Text(entry)
                            .font(index == 0 ? AppFont.sans(12, .bold) : labelMedium)
                            .foregroundStyle(index == 0 ? tone : palette.onSurface)
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                            .frame(maxWidth: .infinity)
                            .frame(height: 30)
                            .background(tone.opacity(index == 0 ? 0.26 : 0.12), in: RoundedRectangle(cornerRadius: 8))
                    }
                    if let note = column.note {
                        Text(note).font(labelSmall).foregroundStyle(palette.muted)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

// MARK: - Timeline

private struct TimelineFigure: View {
    let shape: FigureShape.Timeline
    @Environment(\.palette) private var palette

    var body: some View {
        let span = CGFloat(max(shape.axisMax, 1))
        VStack(alignment: .leading, spacing: 0) {
            Canvas { ctx, size in
                let bar = size.height / CGFloat(max(shape.spans.count, 1))
                for (i, item) in shape.spans.enumerated() {
                    let left = CGFloat(item.start) / span * size.width
                    let right = CGFloat(item.end) / span * size.width
                    let rect = CGRect(x: left, y: CGFloat(i) * bar + bar * 0.18, width: max(right - left, 8), height: bar * 0.64)
                    ctx.fill(Path(roundedRect: rect, cornerRadius: 4), with: .color(palette.tone(item.tone).opacity(0.85)))
                }
                ctx.line(CGPoint(x: 0, y: size.height - 1), CGPoint(x: size.width, y: size.height - 1), color: palette.muted.opacity(0.35), width: 1.5)
                if let marker = shape.marker {
                    let x = CGFloat(marker) / span * size.width
                    ctx.line(CGPoint(x: x, y: 0), CGPoint(x: x, y: size.height), color: palette.primary, width: 2.5)
                }
            }
            .frame(height: 22 * CGFloat(shape.spans.count) + 8)
            HStack(alignment: .top, spacing: 0) {
                ForEach(Array(shape.spans.enumerated()), id: \.offset) { _, item in
                    Text(item.label).font(labelSmall).foregroundStyle(palette.muted).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.top, 4)
            if let label = shape.markerLabel {
                Text(label).font(labelSmallBold).foregroundStyle(palette.primary).padding(.top, 2)
            }
        }
    }
}

// MARK: - Plot

private struct PlotFigure: View {
    let shape: FigureShape.Plot
    @Environment(\.palette) private var palette

    var body: some View {
        let tick = AppFont.sans(9, .medium)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 10) {
                if let y = shape.yLabel { Text(y).font(labelSmall).foregroundStyle(palette.muted) }
                FlowLayout(spacing: 10, lineSpacing: 1) {
                    ForEach(Array(shape.series.filter { !$0.label.trimmingCharacters(in: .whitespaces).isEmpty }.enumerated()), id: \.offset) { _, series in
                        Text(series.label).font(labelSmallBold).foregroundStyle(palette.tone(series.tone)).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            Canvas { ctx, size in
                let bottomPad: CGFloat = shape.bars.isEmpty ? 6 : 20
                let leftPad: CGFloat = 6, topPad: CGFloat = 8
                let plotW = size.width - leftPad - topPad
                let plotH = size.height - bottomPad - topPad
                let baseline = topPad + plotH
                func at(_ x: Double, _ y: Double) -> CGPoint {
                    CGPoint(x: leftPad + CGFloat(x) * plotW, y: topPad + (1 - CGFloat(y)) * plotH)
                }
                let axis = palette.muted.opacity(0.35)
                ctx.line(CGPoint(x: leftPad, y: topPad), CGPoint(x: leftPad, y: baseline), color: axis, width: 1.5)
                ctx.line(CGPoint(x: leftPad, y: baseline), CGPoint(x: size.width, y: baseline), color: axis, width: 1.5)
                for series in shape.series {
                    var path = Path()
                    for (i, p) in series.points.enumerated() {
                        if i == 0 { path.move(to: at(p.x, p.y)) } else { path.addLine(to: at(p.x, p.y)) }
                    }
                    ctx.stroke(path, with: .color(palette.tone(series.tone)),
                               style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round, dash: series.dashed ? [9, 7] : []))
                }
                for (i, bar) in shape.bars.enumerated() {
                    let slot = plotW / CGFloat(shape.bars.count)
                    let width = slot * 0.56
                    let centre = leftPad + (CGFloat(i) + 0.5) * slot
                    let height = max(CGFloat(bar.value) * plotH, 2)
                    ctx.fill(Path(roundedRect: CGRect(x: centre - width / 2, y: baseline - height, width: width, height: height), cornerRadius: 3),
                             with: .color(palette.tone(bar.tone).opacity(0.85)))
                    ctx.label(bar.label, at: CGPoint(x: centre, y: baseline + 3), font: tick, color: palette.muted, anchor: .top)
                }
                for point in shape.markers {
                    let color = palette.tone(point.tone)
                    let p = at(point.x, point.y)
                    ctx.fill(Path(ellipseIn: CGRect(x: p.x - 4.5, y: p.y - 4.5, width: 9, height: 9)), with: .color(color))
                    if let label = point.label {
                        let w = ctx.measure(label, font: tick).width
                        // Above the point, pulled back inside the canvas near the right edge.
                        let x = min(max(p.x, w / 2), size.width - w / 2)
                        ctx.label(label, at: CGPoint(x: x, y: p.y - 6), font: tick, color: color, anchor: .bottom)
                    }
                }
            }
            .frame(height: 120)
            .padding(.top, 4)
            if let x = shape.xLabel {
                Text(x).font(labelSmall).foregroundStyle(palette.muted).frame(maxWidth: .infinity).padding(.top, 2)
            }
        }
    }
}

// MARK: - Layer stack

private struct LayerStackFigure: View {
    let shape: FigureShape.LayerStack
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            if shape.horizontal {
                HStack(spacing: 3) {
                    ForEach(Array(shape.layers.enumerated()), id: \.offset) { index, layer in
                        block(layer).frame(maxHeight: .infinity)
                        if index != shape.layers.count - 1 { flow("→") }
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            } else {
                ForEach(Array(shape.layers.enumerated()), id: \.offset) { index, layer in
                    block(layer)
                    if index != shape.layers.count - 1 { flow("↓") }
                }
            }
            if let back = shape.backwardLabel {
                Text(shape.horizontal ? "←  \(back)  ←" : "↑  \(back)  ↑")
                    .font(labelSmallBold)
                    .foregroundStyle(palette.tone(.Accent))
                    .padding(.top, 6)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func block(_ layer: FigureLayer) -> some View {
        let tone = palette.tone(layer.tone)
        return VStack(spacing: 1) {
            Text(layer.label)
                .font(shape.horizontal ? AppFont.sans(10, .bold) : AppFont.sans(12, .bold))
                .foregroundStyle(layer.tone == .Muted ? palette.onSurface : tone)
                .lineLimit(shape.horizontal ? 2 : nil)
                .minimumScaleFactor(0.7)
            if let detail = layer.detail {
                Text(detail).font(labelSmall).foregroundStyle(palette.muted).minimumScaleFactor(0.7)
            }
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, shape.horizontal ? 2 : 8)
        .padding(.vertical, 7)
        .frame(maxWidth: .infinity, maxHeight: shape.horizontal ? .infinity : nil)
        .background(tone.opacity(0.16), in: RoundedRectangle(cornerRadius: 9))
    }

    private func flow(_ arrow: String) -> some View {
        Text(arrow).font(labelSmall).foregroundStyle(palette.muted.opacity(0.7))
    }
}

// MARK: - Heatmap

private struct HeatmapFigure: View {
    let shape: FigureShape.Heatmap
    @Environment(\.palette) private var palette

    var body: some View {
        let rows = shape.values.count
        let cols = shape.values.map(\.count).max() ?? 1
        let hasRowLabels = !shape.rowLabels.isEmpty
        let hasColLabels = !shape.colLabels.isEmpty
        let header = AppFont.sans(9, .bold)
        VStack(alignment: .leading, spacing: 0) {
            Canvas { ctx, size in
                let labelWidth = hasRowLabels ? size.width * 0.16 : 0
                let labelHeight = hasColLabels ? size.height * 0.14 : 0
                // Square cells: an attention matrix read as a rectangle loses its diagonal.
                let cell = min((size.width - labelWidth) / CGFloat(cols), (size.height - labelHeight) / CGFloat(rows))
                let ramp = palette.tone(shape.tone)
                for (c, text) in shape.colLabels.enumerated() {
                    ctx.label(text, at: CGPoint(x: labelWidth + (CGFloat(c) + 0.5) * cell, y: labelHeight / 2), font: header, color: palette.muted)
                }
                for (r, cells) in shape.values.enumerated() {
                    if hasRowLabels, r < shape.rowLabels.count {
                        ctx.label(shape.rowLabels[r], at: CGPoint(x: labelWidth - 4, y: labelHeight + (CGFloat(r) + 0.5) * cell), font: header, color: palette.muted, anchor: .trailing)
                    }
                    for (c, value) in cells.enumerated() {
                        let rect = CGRect(x: labelWidth + CGFloat(c) * cell, y: labelHeight + CGFloat(r) * cell, width: cell - 1.5, height: cell - 1.5)
                        ctx.stroke(Path(rect), with: .color(palette.muted.opacity(0.18)), lineWidth: 0.5)
                        ctx.fill(Path(rect), with: .color(ramp.opacity(min(max(value, 0), 1) * 0.9)))
                    }
                }
                for mark in shape.marks {
                    let outline = mark.tone == .Primary ? palette.tone(.Accent) : palette.tone(mark.tone)
                    let rect = CGRect(x: labelWidth + CGFloat(mark.col) * cell, y: labelHeight + CGFloat(mark.row) * cell, width: cell - 1.5, height: cell - 1.5)
                    ctx.stroke(Path(rect), with: .color(outline), lineWidth: 2)
                }
            }
            .frame(height: 26 * CGFloat(rows) + (hasColLabels ? 16 : 0))
            if let legend = shape.legend {
                Text(legend).font(labelSmall).foregroundStyle(palette.muted).padding(.top, 6)
            }
        }
    }
}
