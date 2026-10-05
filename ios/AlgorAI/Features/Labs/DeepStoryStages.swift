import SwiftUI

// Port of DeepStoryStages.kt: the CNN labs' stages. Cell grids (an input, a kernel, a feature map, a
// pooled map, a patch grid), rows of per-layer bars (parameters against compute, frozen against
// training), and segment bars (a cost split into parts, a layer's input channels coloured by source).

// MARK: - Grids

/// zero is a quiet 0; pos and neg are blue and pink scaled by `level`; empty and pad draw no number;
/// current is the cell being written (yellow tint and ring); embedded and hot fill solid.
/// ink fills the cell with the cell's `ink` (a segmentation label).
/// heat is violet by `level` (an attention weight).
enum DkCellTone { case zero, pos, neg, empty, pad, current, embedded, hot, ink, heat }

struct DkCell { let text: String; let tone: DkCellTone; var level: Double = 1; var ink: DkInk? = nil }

/// A rectangle over cells r0...r1 × c0...c1: the yellow window, or a dashed grey outline.
struct DkBox { let r0: Int; let c0: Int; let r1: Int; let c1: Int; var dashed = false }

struct DkGrid {
    let title: String
    let rows: Int
    let cols: Int
    let cells: [DkCell]
    var boxes: [DkBox] = []
    var maxCell: CGFloat = 30
    var note: String? = nil
    /// Labels left of each row and above each column (a query's tokens, a key's tokens); `hotRow` is yellow.
    var rowLabels: [String] = []
    var colLabels: [String] = []
    var hotRow: Int? = nil
}

/// Columns of grids side by side (an input beside a kernel over an output), sharing the width by `weights`.
struct DkGrids {
    let columns: [[DkGrid]]
    let weights: [CGFloat]
    /// Notes in a column after the grids ("466 of 784 on"), with `sideWeight` of the width.
    var side: [String] = []
    var sideWeight: CGFloat = 1
}

struct DkGridsView: View {
    let stage: DkGrids
    @State private var width: CGFloat = 0

    var body: some View {
        let gap: CGFloat = 14
        let parts = stage.columns.count + (stage.side.isEmpty ? 0 : 1)
        let avail = max(width - gap * CGFloat(parts - 1), 0)
        let total = stage.weights.reduce(0, +) + (stage.side.isEmpty ? 0 : stage.sideWeight)
        HStack(alignment: .top, spacing: gap) {
            ForEach(stage.columns.indices, id: \.self) { i in
                let w = avail * stage.weights[i] / total
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(stage.columns[i].indices, id: \.self) { j in DkGridView(grid: stage.columns[i][j], width: w) }
                }
                .frame(width: w, alignment: .leading)
            }
            if !stage.side.isEmpty { DkSideNotes(lines: stage.side).frame(width: avail * stage.sideWeight / total, alignment: .leading) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GeometryReader { geo in
            Color.clear
                .onAppear { width = geo.size.width }
                .onChange(of: geo.size.width) { _, w in width = w }
        })
        .padding(10)
        .modifier(DkStageShape())
    }
}

private struct DkGridView: View {
    let grid: DkGrid
    let width: CGFloat
    @Environment(\.palette) private var palette

    var body: some View {
        let labelW: CGFloat = grid.rowLabels.isEmpty ? 0 : 44
        let cell = min(grid.maxCell, (width - labelW) / CGFloat(grid.cols))
        VStack(alignment: .leading, spacing: 4) {
            if !grid.title.isEmpty { Text(grid.title).font(AppFont.mono(11)).foregroundStyle(palette.muted).lineLimit(1) }
            if !grid.colLabels.isEmpty {
                HStack(spacing: 0) {
                    ForEach(grid.colLabels.indices, id: \.self) { Text(grid.colLabels[$0]).font(AppFont.mono(10)).foregroundStyle(palette.muted).lineLimit(1).frame(width: cell) }
                }
                .padding(.leading, labelW)
            }
            HStack(spacing: 0) {
            if !grid.rowLabels.isEmpty {
                VStack(spacing: 0) {
                    ForEach(grid.rowLabels.indices, id: \.self) { i in
                        Text(grid.rowLabels[i]).font(AppFont.mono(10, i == grid.hotRow ? .bold : .regular))
                            .foregroundStyle(i == grid.hotRow ? SimColors.active : palette.muted).lineLimit(1)
                            .frame(width: labelW - 6, height: cell, alignment: .trailing).padding(.trailing, 6)
                    }
                }
            }
            Canvas { ctx, _ in
                let inset: CGFloat = 1.5
                let fontSize = min(max(cell * 0.4, 8), 15)
                for r in 0..<grid.rows {
                    for k in 0..<grid.cols {
                        let data = grid.cells[r * grid.cols + k]
                        let rect = CGRect(x: CGFloat(k) * cell + inset, y: CGFloat(r) * cell + inset, width: cell - 2 * inset, height: cell - 2 * inset)
                        let shape = Path(roundedRect: rect, cornerRadius: 3)
                        let level = min(max(data.level, 0), 1)
                        let fill: Color = switch data.tone {
                        case .zero: .white.opacity(0.06)
                        case .pos: dkColor(.blue).opacity(0.35 + 0.6 * level)
                        case .neg: dkColor(.pink).opacity(0.35 + 0.6 * level)
                        case .empty: .white.opacity(0.05)
                        case .pad: .white.opacity(0.025)
                        case .current: SimColors.active.opacity(0.3)
                        case .embedded: dkColor(.blue).opacity(0.8)
                        case .hot: SimColors.active
                        case .ink: dkColor(data.ink ?? .slate)
                        case .heat: SimColors.answer.opacity(0.12 + 0.8 * level)
                        }
                        ctx.fill(shape, with: .color(fill))
                        if data.tone == .current { ctx.stroke(shape, with: .color(SimColors.active), lineWidth: 2) }
                        if !data.text.isEmpty && data.tone != .empty && data.tone != .pad {
                            let zero = data.tone == .zero
                            // Four-character values ("0.07", "−3.8") shrink to fit their cell.
                            let size = data.text.count >= 4 ? min(fontSize, cell * 0.27) : fontSize
                            ctx.draw(Text(data.text).font(AppFont.mono(size, zero ? .regular : .bold))
                                .foregroundStyle(zero ? palette.muted.opacity(0.7) : .white),
                                     at: CGPoint(x: rect.midX, y: rect.midY))
                        }
                    }
                }
                for b in grid.boxes {
                    let rect = CGRect(x: CGFloat(b.c0) * cell, y: CGFloat(b.r0) * cell, width: CGFloat(b.c1 - b.c0 + 1) * cell, height: CGFloat(b.r1 - b.r0 + 1) * cell)
                    let shape = Path(roundedRect: rect, cornerRadius: 4)
                    if b.dashed {
                        ctx.stroke(shape, with: .color(palette.muted.opacity(0.6)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                    } else {
                        ctx.stroke(shape, with: .color(SimColors.active), lineWidth: 2)
                    }
                }
            }
            .frame(width: cell * CGFloat(grid.cols), height: cell * CGFloat(grid.rows))
            }
            if let note = grid.note {
                Text(note).font(AppFont.mono(11)).foregroundStyle(palette.muted).padding(.top, 4)
            }
        }
    }
}

// MARK: - Layer rows

/// One bar: `frac` of the track filled in `ink`; a nil `frac` draws only the label ("no params").
struct DkBar { let frac: Double?; let ink: DkInk; let label: String }

/// A titled row and its bars, stacked or (when `pair`) side by side; `hot` inks the title yellow.
struct DkRow {
    let title: String
    let meta: String
    let bars: [DkBar]
    var hot = false
    var pair = false
    /// One line: a mono label, the bar, the value ("e b ──── 0.198 kept"); `dim` mutes a pruned row.
    var inline = false
    var dim = false
}

struct DkRows { let rows: [DkRow]; var caption: String? = nil }

struct DkRowsView: View {
    let stage: DkRows
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let caption = stage.caption { Text(caption).font(AppFont.sans(13)).foregroundStyle(palette.muted) }
            ForEach(stage.rows.indices, id: \.self) { i in
                let row = stage.rows[i]
                if row.inline { DkInlineRow(row: row) } else {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(row.title).font(AppFont.sans(15, .semibold)).foregroundStyle(row.hot ? SimColors.active : palette.onSurface).lineLimit(1)
                        Spacer(minLength: 8)
                        Text(row.meta).font(AppFont.mono(12)).foregroundStyle(palette.muted).lineLimit(1)
                    }
                    if row.pair {
                        HStack(spacing: 10) { ForEach(row.bars.indices, id: \.self) { DkBarView(bar: row.bars[$0]) } }
                    } else {
                        ForEach(row.bars.indices, id: \.self) { DkBarView(bar: row.bars[$0]) }
                    }
                }
                }
            }
        }
    }
}

private struct DkBarView: View {
    let bar: DkBar
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3).fill(SimColors.tint)
                    if let f = bar.frac {
                        RoundedRectangle(cornerRadius: 3).fill(dkColor(bar.ink)).frame(width: geo.size.width * CGFloat(min(max(f, 0.012), 1)))
                    }
                }
            }
            .frame(height: 6)
            Text(bar.label).font(AppFont.mono(12)).foregroundStyle(palette.muted).multilineTextAlignment(.trailing)
                .lineLimit(2).frame(width: 64, alignment: .trailing)
        }
    }
}

// MARK: - Segment bars

struct DkSeg { let units: Double; let ink: DkInk; var dim = false }

/// A bar of `segs` on a shared scale; `label` sits in a left column, `caption` above, `value` after or inside.
struct DkSegRow {
    let segs: [DkSeg]
    var label: String? = nil
    var caption: String? = nil
    var value: String? = nil
    var hot = false
}

struct DkSegs { let rows: [DkSegRow]; let scale: Double; var notes: [String] = []; var barHeight: CGFloat = 22 }

struct DkSegsView: View {
    let stage: DkSegs
    @Environment(\.palette) private var palette

    var body: some View {
        let labelled = stage.rows.contains { $0.label != nil }
        VStack(alignment: .leading, spacing: 6) {
            ForEach(stage.rows.indices, id: \.self) { i in
                let row = stage.rows[i]
                if let caption = row.caption { Text(caption).font(AppFont.mono(11)).foregroundStyle(palette.muted).padding(.top, 4) }
                HStack(spacing: 0) {
                    if labelled {
                        Text(row.label ?? "").font(AppFont.mono(11, row.hot ? .bold : .regular))
                            .foregroundStyle(row.hot ? SimColors.active : palette.muted).lineLimit(1)
                            .frame(width: 24, alignment: .trailing).padding(.trailing, 6)
                    }
                    GeometryReader { geo in
                        let widths = row.segs.map { max(3, geo.size.width * CGFloat($0.units / stage.scale)) }
                        let total = widths.reduce(0, +) + 2 * CGFloat(max(row.segs.count - 1, 0))
                        let inside = row.value != nil && row.segs.count == 1 && widths[0] > 90
                        HStack(spacing: 0) {
                            HStack(spacing: 2) {
                                ForEach(row.segs.indices, id: \.self) { s in
                                    RoundedRectangle(cornerRadius: 4).fill(dkColor(row.segs[s].ink).opacity(row.segs[s].dim ? 0.6 : 1))
                                        .frame(width: widths[s], height: stage.barHeight)
                                        .overlay(alignment: .trailing) {
                                            if inside {
                                                Text(row.value!).font(AppFont.mono(13, .bold)).foregroundStyle(.white).padding(.trailing, 8)
                                            }
                                        }
                                }
                            }
                            .padding(row.hot ? 1.5 : 0)
                            .overlay { if row.hot { RoundedRectangle(cornerRadius: 5).stroke(SimColors.active, lineWidth: 1.5) } }
                            if let value = row.value, !inside, total < geo.size.width - 40 {
                                Text(value).font(AppFont.mono(13, .bold)).foregroundStyle(.white).padding(.leading, 8)
                            }
                        }
                    }
                    .frame(height: stage.barHeight + (row.hot ? 3 : 0))
                }
            }
            if !stage.notes.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(stage.notes.indices, id: \.self) { storyText(stage.notes[$0], palette).font(AppFont.mono(11)).foregroundStyle(palette.muted) }
                }
                .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .modifier(DkStageShape())
    }
}

/// Notes beside a stage, one per line; an empty string is a gap. Marks colour a value ("{m:0 px}").
private struct DkSideNotes: View {
    let lines: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            ForEach(lines.indices, id: \.self) { i in
                if lines[i].isEmpty {
                    Color.clear.frame(height: 10)
                } else {
                    storyText(lines[i], palette).font(AppFont.mono(11)).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(.top, 2)
    }
}

// MARK: - Box scenes

/// A box in image pixels. `fill` washes it in its ink; `bins` draws an n×n lattice inside (an RoI's bins).
struct DkRect {
    let x1: Double
    let y1: Double
    let x2: Double
    let y2: Double
    let ink: DkInk
    var dashed = false
    var fill = false
    var thin = false
    var label: String? = nil
    var bins = 0
}

/// An image of size×size pixels, optionally cut by a grid×grid lattice, with boxes and centre dots.
struct DkBoxes {
    let size: Double
    let grid: Int
    let rects: [DkRect]
    var dots: [DkP] = []
    var side: [String] = []
}

struct DkBoxesView: View {
    let stage: DkBoxes
    @State private var width: CGFloat = 0

    var body: some View {
        let imageW = stage.side.isEmpty ? width : (width - 10) * 1.7 / 2.7
        let side = min(max(imageW, 0), 260)
        HStack(alignment: .top, spacing: 10) {
            Canvas { ctx, size in
                let k = size.width / CGFloat(stage.size)
                ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.white.opacity(0.05)))
                if stage.grid > 0 {
                    let step = size.width / CGFloat(stage.grid)
                    for i in 1..<stage.grid {
                        var p = Path()
                        p.move(to: CGPoint(x: CGFloat(i) * step, y: 0)); p.addLine(to: CGPoint(x: CGFloat(i) * step, y: size.height))
                        p.move(to: CGPoint(x: 0, y: CGFloat(i) * step)); p.addLine(to: CGPoint(x: size.width, y: CGFloat(i) * step))
                        ctx.stroke(p, with: .color(.white.opacity(0.09)), lineWidth: 1)
                    }
                }
                for r in stage.rects {
                    let color = dkColor(r.ink)
                    let rect = CGRect(x: CGFloat(r.x1) * k, y: CGFloat(r.y1) * k, width: CGFloat(r.x2 - r.x1) * k, height: CGFloat(r.y2 - r.y1) * k)
                    if r.fill { ctx.fill(Path(rect), with: .color(color.opacity(0.35))) }
                    if r.bins > 0 {
                        var p = Path()
                        for b in 1..<r.bins {
                            let x = rect.minX + rect.width * CGFloat(b) / CGFloat(r.bins)
                            let y = rect.minY + rect.height * CGFloat(b) / CGFloat(r.bins)
                            p.move(to: CGPoint(x: x, y: rect.minY)); p.addLine(to: CGPoint(x: x, y: rect.maxY))
                            p.move(to: CGPoint(x: rect.minX, y: y)); p.addLine(to: CGPoint(x: rect.maxX, y: y))
                        }
                        ctx.stroke(p, with: .color(color.opacity(0.55)), lineWidth: 1)
                    }
                    if !r.fill || r.dashed {
                        ctx.stroke(Path(rect), with: .color(color.opacity(r.thin ? 0.6 : 1)),
                                   style: StrokeStyle(lineWidth: r.thin ? 1 : 2, dash: r.dashed ? [4, 3] : []))
                    }
                    if let label = r.label {
                        ctx.draw(Text(label).font(AppFont.mono(10)).foregroundStyle(color), at: CGPoint(x: rect.minX + 2, y: rect.minY - 1), anchor: .bottomLeading)
                    }
                }
                for d in stage.dots {
                    let c = CGPoint(x: CGFloat(d.x) * k, y: CGFloat(d.y) * k)
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 4, y: c.y - 4, width: 8, height: 8)), with: .color(SimColors.active))
                }
            }
            .frame(width: side, height: side)
            .frame(width: stage.side.isEmpty ? nil : max(imageW, 0))
            .frame(maxWidth: stage.side.isEmpty ? .infinity : nil)
            if !stage.side.isEmpty { DkSideNotes(lines: stage.side).frame(maxWidth: .infinity, alignment: .leading) }
        }
        .frame(maxWidth: .infinity)
        .background(GeometryReader { geo in
            Color.clear
                .onAppear { width = geo.size.width }
                .onChange(of: geo.size.width) { _, w in width = w }
        })
        .padding(10)
        .modifier(DkStageShape())
    }
}

// MARK: - U-Net

/// The U: encoder sides, the bottleneck, and decoder sides; `done` decoder levels are computed, `current` is one of them.
struct DkUNet { let encoder: [Int]; let bottom: Int; let decoder: [Int]; let done: Int; let current: Int? }

struct DkUNetView: View {
    let stage: DkUNet
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let muted = palette.muted
            let depth = stage.encoder.count
            let cols = 2 * depth + 1
            let padX: CGFloat = 12
            let slot = (size.width - 2 * padX) / CGFloat(cols)
            let barW = slot * 0.62
            let top: CGFloat = 14
            let maxH = size.height * 0.62
            let maxSize = CGFloat(stage.encoder[0])
            let levelStep = (size.height - top - 30 - maxH * CGFloat(stage.bottom) / maxSize) / CGFloat(depth)
            func cx(_ col: Int) -> CGFloat { padX + slot * (CGFloat(col) + 0.5) }
            func drawBar(_ col: Int, _ level: Int, _ side: Int, _ color: Color) {
                let h = max(maxH * CGFloat(side) / maxSize, 6)
                let y = top + CGFloat(level) * levelStep
                ctx.fill(Path(roundedRect: CGRect(x: cx(col) - barW / 2, y: y, width: barW, height: h), cornerRadius: 3), with: .color(color))
                ctx.draw(Text("\(side)").font(AppFont.mono(10)).foregroundStyle(muted), at: CGPoint(x: cx(col), y: y + h + 3), anchor: .top)
            }
            for i in 0..<depth {
                let decCol = cols - 1 - i
                let y = top + CGFloat(i) * levelStep + 3
                let dec = depth - 1 - i
                let color = stage.current == dec ? SimColors.active : muted.opacity(0.5)
                var p = Path(); p.move(to: CGPoint(x: cx(i) + barW / 2, y: y)); p.addLine(to: CGPoint(x: cx(decCol) - barW / 2, y: y))
                ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: 1.2, dash: [4, 3]))
                let up = 2 * (dec == 0 ? stage.bottom : stage.decoder[dec - 1])
                ctx.draw(Text("crop \(stage.encoder[i])→\(up)").font(AppFont.mono(10)).foregroundStyle(color),
                         at: CGPoint(x: (cx(i) + cx(decCol)) / 2, y: y - 1), anchor: .bottom)
            }
            for (i, side) in stage.encoder.enumerated() { drawBar(i, i, side, dkColor(.blue)) }
            drawBar(depth, depth, stage.bottom, SimColors.answer)
            for (d, side) in stage.decoder.enumerated() {
                let color = d == stage.current ? SimColors.active : d < stage.done ? dkColor(.green) : dkColor(.slate)
                drawBar(depth + 1 + d, depth - 1 - d, side, color)
            }
        }
        .frame(height: 250)
        .modifier(DkStageShape())
    }
}

/// "e b  ────────  0.198 kept": a mono label, the bar and its value on one line.
private struct DkInlineRow: View {
    let row: DkRow
    @Environment(\.palette) private var palette

    var body: some View {
        let ink = row.dim ? palette.muted : palette.onSurface
        let bar = row.bars[0]
        HStack(spacing: 0) {
            Text(row.title).font(AppFont.mono(14, row.dim ? .regular : .bold)).foregroundStyle(row.hot ? SimColors.active : ink).lineLimit(1)
                .frame(width: row.meta.isEmpty ? 76 : 52, alignment: .leading)
            if !row.meta.isEmpty {
                Text(row.meta).font(AppFont.mono(13)).foregroundStyle(ink).lineLimit(1).frame(width: 52, alignment: .trailing).padding(.trailing, 10)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3).fill(SimColors.tint)
                    RoundedRectangle(cornerRadius: 3).fill(row.dim ? palette.muted.opacity(0.6) : dkColor(bar.ink))
                        .frame(width: geo.size.width * CGFloat(min(max(bar.frac ?? 0, 0.012), 1)))
                }
            }
            .frame(height: 6)
            Text(bar.label).font(AppFont.mono(13)).foregroundStyle(ink).lineLimit(1).frame(width: row.meta.isEmpty ? 92 : 52, alignment: .trailing)
        }
    }
}

// MARK: - Tiles

/// source and emitted are the blue and green tokens; current is the yellow-ringed one being worked;
/// empty a slot not filled yet; plain a neutral tile; hot the violet one that matters; fill a dark
/// tile with a level rising from its bottom (`fill` of `fillInk`).
/// heat washes the tile violet by `fill` (an attention weight).
enum DkTileTone { case source, emitted, current, empty, plain, hot, fill, heat }

struct DkTile {
    let text: String
    let tone: DkTileTone
    var sub: String? = nil
    var fill: Double = 0
    var fillInk: DkInk = .blue
    /// Under the tile: a mono value, then thin state bars (nil is an empty track).
    var caption: String? = nil
    var under: [DkInk?] = []
    /// A yellow ring without the current tint (a head's top key, the task in use).
    var ring = false
}

struct DkTileRow { let tiles: [DkTile]; var label: String? = nil; var title: String? = nil; var height: CGFloat = 44 }

/// Rows of tiles in shared columns; `headers` name the columns, `hot` rings a column in yellow,
/// `pill` sits under row index first ("context · 12 numbers"), `arrows` draws ↓ between rows.
struct DkTiles {
    let rows: [DkTileRow]
    var headers: [String] = []
    var hot: Int? = nil
    var pill: (Int, String)? = nil
    var arrows = false
    /// Column names under the last row ("task prefix · input · target").
    var footers: [String] = []
}

struct DkTilesView: View {
    let stage: DkTiles
    @Environment(\.palette) private var palette

    var body: some View {
        let labelled = stage.rows.contains { $0.label != nil }
        VStack(alignment: .leading, spacing: 8) {
            if !stage.headers.isEmpty {
                HStack(spacing: 6) {
                    if labelled { Color.clear.frame(width: 52, height: 1) }
                    ForEach(stage.headers.indices, id: \.self) { i in
                        Text(stage.headers[i]).font(AppFont.mono(12, i == stage.hot ? .bold : .regular))
                            .foregroundStyle(i == stage.hot ? SimColors.active : palette.muted).frame(maxWidth: .infinity)
                    }
                }
            }
            ForEach(stage.rows.indices, id: \.self) { r in
                let row = stage.rows[r]
                if let title = row.title { Text(title).font(AppFont.sans(13)).foregroundStyle(palette.muted) }
                HStack(alignment: .top, spacing: 6) {
                    if labelled {
                        Text(row.label ?? "").font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1)
                            .frame(width: 52, height: row.height, alignment: .leading)
                    }
                    ForEach(row.tiles.indices, id: \.self) { c in
                        DkTileView(tile: row.tiles[c], height: row.height, hotColumn: c == stage.hot)
                    }
                }
                if stage.arrows && r < stage.rows.count - 1 {
                    HStack(spacing: 6) {
                        if labelled { Color.clear.frame(width: 52, height: 1) }
                        ForEach(row.tiles.indices, id: \.self) { _ in Text("↓").font(AppFont.sans(12)).foregroundStyle(palette.muted).frame(maxWidth: .infinity) }
                    }
                }
                if r == stage.rows.count - 1 && !stage.footers.isEmpty {
                    HStack(spacing: 6) {
                        if labelled { Color.clear.frame(width: 52, height: 1) }
                        ForEach(stage.footers.indices, id: \.self) { Text(stage.footers[$0]).font(AppFont.mono(11)).foregroundStyle(palette.muted).frame(maxWidth: .infinity) }
                    }
                }
                if let pill = stage.pill, pill.0 == r {
                    HStack(spacing: 8) {
                        Rectangle().fill(palette.muted.opacity(0.35)).frame(height: 1)
                        Text(pill.1).font(AppFont.mono(13)).foregroundStyle(StoryTone.answer.ink(palette))
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(SimColors.answer.opacity(0.3), in: RoundedRectangle(cornerRadius: 8))
                            .fixedSize()
                        Rectangle().fill(palette.muted.opacity(0.35)).frame(height: 1)
                    }
                }
            }
        }
    }
}

private struct DkTileView: View {
    let tile: DkTile
    let height: CGFloat
    let hotColumn: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        let bg: Color = switch tile.tone {
        case .source: dkColor(.blue).opacity(0.3)
        case .emitted: dkColor(.green).opacity(0.3)
        case .current: SimColors.active.opacity(0.22)
        case .empty: .white.opacity(0.04)
        case .plain, .fill: .white.opacity(0.07)
        case .hot: SimColors.answer.opacity(0.4)
        case .heat: SimColors.answer.opacity(0.15 + 0.75 * min(max(tile.fill, 0), 1))
        }
        let ring = tile.ring || tile.tone == .current || (hotColumn && tile.tone == .fill)
        let ink: Color = tile.tone == .current ? SimColors.active : .white
        VStack(spacing: 4) {
            ZStack {
                RoundedRectangle(cornerRadius: 8).fill(bg)
                if tile.tone == .fill && tile.fill > 0 {
                    GeometryReader { geo in
                        VStack(spacing: 0) {
                            Spacer(minLength: 0)
                            Rectangle().fill(dkColor(tile.fillInk)).frame(height: geo.size.height * CGFloat(min(max(tile.fill, 0.06), 1)))
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                if !tile.text.isEmpty {
                    VStack(spacing: 0) {
                        Text(tile.text).font(AppFont.sans(tile.text.count > 12 ? 13 : 15, .bold))
                            .foregroundStyle(tile.ring && tile.tone != .current ? SimColors.active : ink)
                            .lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.7).padding(.horizontal, 4)
                        if let sub = tile.sub { Text(sub).font(AppFont.mono(11)).foregroundStyle(ink.opacity(0.75)).lineLimit(1) }
                    }
                }
                if ring { RoundedRectangle(cornerRadius: 8).stroke(SimColors.active, lineWidth: 1.5) }
            }
            .frame(height: height)
            if let caption = tile.caption {
                Text(caption).font(AppFont.mono(12)).foregroundStyle(hotColumn ? SimColors.active : palette.onSurface.opacity(0.85))
            }
            ForEach(tile.under.indices, id: \.self) { i in
                RoundedRectangle(cornerRadius: 2).fill(tile.under[i].map { dkColor($0) } ?? SimColors.tint).frame(height: 4)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Pipeline

enum DkStepTone { case done, current, next }

struct DkPipeItem { let title: String; let meta: String; let tone: DkStepTone }

/// A block's stages top to bottom: done in green, the current one ringed yellow, the rest dim.
struct DkPipeline { let items: [DkPipeItem] }

struct DkPipelineView: View {
    let stage: DkPipeline
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 8) {
            ForEach(stage.items.indices, id: \.self) { i in
                let item = stage.items[i]
                let bg: Color = switch item.tone {
                case .done: dkColor(.green).opacity(0.2)
                case .current: SimColors.active.opacity(0.2)
                case .next: .white.opacity(0.05)
                }
                HStack {
                    Text(item.title).font(AppFont.sans(15, .semibold)).foregroundStyle(item.tone == .current ? SimColors.active : palette.onSurface).lineLimit(1)
                    Spacer(minLength: 8)
                    Text(item.meta).font(AppFont.mono(12)).foregroundStyle(palette.muted).lineLimit(1)
                }
                .padding(.horizontal, 14)
                .frame(height: 40)
                .background(bg, in: RoundedRectangle(cornerRadius: 9))
                .overlay { if item.tone == .current { RoundedRectangle(cornerRadius: 9).stroke(SimColors.active, lineWidth: 1.5) } }
            }
        }
    }
}

// MARK: - Histograms

/// One row of bars over shared class labels; `hot` is drawn yellow (the hard label).
struct DkHistRow { let label: String; let values: [Double]; let ink: DkInk; let hot: Int? }

struct DkHist { let rows: [DkHistRow]; let labels: [String] }

struct DkHistView: View {
    let stage: DkHist
    @Environment(\.palette) private var palette

    var body: some View {
        let rowH: CGFloat = 92
        Canvas { ctx, size in
            let n = stage.labels.count
            let padX: CGFloat = 10
            let slot = (size.width - 2 * padX) / CGFloat(n)
            let barW = slot * 0.72
            let top = max(stage.rows.flatMap(\.values).max() ?? 1, 1e-9)
            for (r, row) in stage.rows.enumerated() {
                let y0 = CGFloat(r) * rowH
                let base = y0 + rowH - 6
                let maxH = rowH - 40
                ctx.draw(Text(row.label).font(AppFont.sans(12)).foregroundStyle(palette.muted), at: CGPoint(x: padX, y: y0 + 6), anchor: .topLeading)
                var line = Path(); line.move(to: CGPoint(x: padX, y: base)); line.addLine(to: CGPoint(x: size.width - padX, y: base))
                ctx.stroke(line, with: .color(palette.muted.opacity(0.3)), lineWidth: 1)
                for (i, v) in row.values.enumerated() {
                    let h = CGFloat(v / top) * maxH
                    let cx = padX + slot * (CGFloat(i) + 0.5)
                    ctx.fill(Path(roundedRect: CGRect(x: cx - barW / 2, y: base - h, width: barW, height: max(h, 1.5)), cornerRadius: 3),
                             with: .color(i == row.hot ? SimColors.active : dkColor(row.ink)))
                    ctx.draw(Text(dkNum(v, 2)).font(AppFont.mono(10)).foregroundStyle(palette.onSurface.opacity(0.8)), at: CGPoint(x: cx, y: base - h - 2), anchor: .bottom)
                }
            }
            for (i, l) in stage.labels.enumerated() {
                ctx.draw(Text(l).font(AppFont.mono(10)).foregroundStyle(palette.muted), at: CGPoint(x: padX + slot * (CGFloat(i) + 0.5), y: size.height - 10))
            }
        }
        .frame(height: CGFloat(stage.rows.count) * rowH + 22)
        .modifier(DkStageShape())
    }
}

// MARK: - Token chips

/// whole is a learned piece (violet); part a short fragment (yellow); plain an ordinary token.
enum DkTokTone { case whole, part, plain, masked }

struct DkTokens { let tokens: [(String, DkTokTone)]; var notes: [String] = [] }

struct DkTokensView: View {
    let stage: DkTokens
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            FlowLayout(spacing: 6, lineSpacing: 6) {
                ForEach(stage.tokens.indices, id: \.self) { i in
                    let (text, tone) = stage.tokens[i]
                    let bg: Color = switch tone {
                    case .whole: SimColors.answer.opacity(0.35)
                    case .part, .masked: SimColors.active.opacity(0.25)
                    case .plain: .white.opacity(0.08)
                    }
                    Text(text).font(AppFont.mono(14, .bold))
                        .foregroundStyle(tone == .part || tone == .masked ? SimColors.active : .white)
                        .padding(.horizontal, 9).padding(.vertical, 6)
                        .background(bg, in: RoundedRectangle(cornerRadius: 7))
                }
            }
            if !stage.notes.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(stage.notes.indices, id: \.self) { storyText(stage.notes[$0], palette).font(AppFont.mono(11)).foregroundStyle(palette.muted) }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Graphs

/// A node at (x, y) in 0...1 of the stage; `ink` fills it (nil: plain), `value` sits under it, `ring` marks the centre.
struct DkGNode { let x: CGFloat; let y: CGFloat; let label: String; var value: String? = nil; var ink: DkInk? = nil; var ring = false }

/// An edge; `hot` draws it yellow, `weight` (0...1) sets its width when hot.
struct DkGEdge { let a: Int; let b: Int; var hot = false; var weight: Double = 0.5 }

struct DkGraph { let nodes: [DkGNode]; let edges: [DkGEdge] }

struct DkGraphView: View {
    let stage: DkGraph
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let r: CGFloat = 18
            let padX = r + 28, padTop = r + 14, padBottom = r + 28
            func at(_ n: DkGNode) -> CGPoint { CGPoint(x: padX + n.x * (size.width - 2 * padX), y: padTop + n.y * (size.height - padTop - padBottom)) }
            for e in stage.edges.sorted(by: { !$0.hot && $1.hot }) {
                var p = Path(); p.move(to: at(stage.nodes[e.a])); p.addLine(to: at(stage.nodes[e.b]))
                if e.hot { ctx.stroke(p, with: .color(SimColors.active), style: StrokeStyle(lineWidth: CGFloat(1.5 + 5 * e.weight), lineCap: .round)) }
                else { ctx.stroke(p, with: .color(palette.muted.opacity(0.45)), lineWidth: 1.5) }
            }
            for n in stage.nodes {
                let c = at(n)
                let circle = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
                ctx.fill(circle, with: .color(Color(hex: 0x2A2F3A)))
                if let ink = n.ink { ctx.fill(circle, with: .color(dkColor(ink).opacity(0.6))) }
                let inner = Path(ellipseIn: CGRect(x: c.x - r + 1, y: c.y - r + 1, width: 2 * r - 2, height: 2 * r - 2))
                ctx.stroke(inner, with: .color(n.ring ? SimColors.active : palette.muted.opacity(0.5)), lineWidth: n.ring ? 2.5 : 1.2)
                ctx.draw(Text(n.label).font(AppFont.mono(13, .bold)).foregroundStyle(.white), at: c)
                if let v = n.value {
                    ctx.draw(Text(v).font(AppFont.mono(10.5, .bold)).foregroundStyle(StoryTone.answer.ink(palette)), at: CGPoint(x: c.x, y: c.y + r + 4), anchor: .top)
                }
            }
        }
        .frame(height: 230)
        .modifier(DkStageShape())
    }
}
