import SwiftUI

// Port of NlpStoryLabs.kt: text preprocessing, statistical NLP, embeddings, syntax, pretrained models,
// prompting and transformer internals as one card of stacked blocks — tokens, tables, bar rows, stat
// tiles, matrices, plots, a small tree or network — over the step's legend, then chips, a formula box and
// the headline. The dock is the play transport. Frames: NlpTextFrames.swift, NlpSyntaxFrames.swift and
// NlpModelFrames.swift.

let nlpStoryTopicIds: Set<String> = nlpTextTopicIds.union(nlpSyntaxTopicIds).union(nlpModelTopicIds)

func nlpLab(_ topicId: String) -> [NbFrame] {
    if let f = nlpTextLab(topicId) ?? nlpSyntaxLab(topicId) ?? nlpModelLab(topicId) { return f }
    fatalError("No NLP storyboard for \(topicId)")
}

enum NbInk { case yellow, violet, indigo, blue, sky, green, red, pink, orange, grey, slate, teal }

/// Token tones: plain, the window/current one (yellow), the picked one (violet), not reached, an empty
/// slot, kept (green), removed (red, struck through), and the three entity tints.
enum NbTone: CaseIterable { case plain, hot, pick, dim, empty, good, bad, blue, pink, orange }

struct NbTok { let t: String; var tone: NbTone = .plain; var sub = "" }

struct NbCell {
    let text: String
    var ink: NbInk? = nil
    var bold = false
    /// A muted second line.
    var sub = ""
    /// A bar in place of text: the filled fraction of the cell.
    var bar: Double? = nil
    var mono = true
}

struct NbRow { let cells: [NbCell]; var ring = false; var tint: NbInk? = nil; var dim = false }

struct NbBar { let label: String; let value: String; let frac: Double; let ink: NbInk; var labelInk: NbInk? = nil }

struct NbTile { let big: String; let caption: String; var ink: NbInk? = nil; var tint: NbInk? = nil }

struct NbBox { let title: String; let lines: [String]; var ink: NbInk? = nil; var lastInk: NbInk? = nil }

struct NbGCell { let text: String; let level: Double; var ink: NbInk = .blue; var ring = false }

struct NbP { let x: Double; let y: Double; init(_ x: Double, _ y: Double) { self.x = x; self.y = y } }

struct NbLine { let pts: [NbP]; let ink: NbInk; var dashed = false; var width: CGFloat = 2.5; var dots = false }

/// A plotted point; `cat` picks a colour from the cluster palette, `text` is drawn inside, `label` beside.
struct NbDot {
    let p: NbP
    var ink: NbInk = .sky
    var r: CGFloat = 4
    var ring = false
    var label: String? = nil
    var text: String? = nil
    var cat: Int? = nil
    var hollow = false
}

struct NbSeg { let a: NbP; let b: NbP; let ink: NbInk; var dashed = false; var width: CGFloat = 2 }

struct NbNode {
    let x: CGFloat
    let y: CGFloat
    let text: String
    var sub = ""
    var tone: NbTone = .plain
    /// A line under the box ("sure"), in `belowInk`.
    var below = ""
    var belowInk: NbInk? = nil
    /// A dashed box with room for two lines.
    var dashed = false
}

struct NbEdge { let a: Int; let b: Int; var hot = false }

struct NbSpan { let t: String; var tone: NbTone = .plain }

struct NbModel { let name: String; let sub: String; let enc: Bool; let dec: Bool; let frac: Double; let value: String; var ring = false }

struct NbGridBlock {
    let cols: [String]
    let rows: [String]
    let cells: [[NbGCell]]
    var text = true
    var cellHeight: CGFloat = 28
    var scale: (String, String)? = nil
    var hotRow: Int? = nil
}

struct NbPlotBlock {
    let xr: (Double, Double)
    let yr: (Double, Double)
    var height: CGFloat = 180
    var lines: [NbLine] = []
    var dots: [NbDot] = []
    var segs: [NbSeg] = []
    var xTicks: [(Double, String)] = []
    var yTicks: [(Double, String)] = []
    /// A shaded band over [from, to] on x, labelled inside.
    var shade: (Double, Double, String)? = nil
    var xLabel = ""
    var yLabel = ""
    var grid = true
    var groups: [(Double, String)] = []
}

enum NbBlock {
    case caption(String, note: String? = nil)
    /// Tokens in a wrapping row, or a grid of `columns`; `label` sits in a left gutter ("stack").
    case toks([NbTok], label: String? = nil, columns: Int? = nil, mono: Bool = false, start: Bool = false)
    case table(headers: [String], weights: [CGFloat], rows: [NbRow], aligns: [Int]? = nil)
    /// Bar rows; `ticks` label the track at fractions, `ref` is a dashed line at a fraction with its label.
    case bars([NbBar], ticks: [(Double, String)] = [], ref: (Double, String)? = nil, labelWidth: CGFloat = 96)
    case tiles([NbTile])
    case boxes([NbBox])
    /// Mono lines in a tinted box; `{…}` marks colour a value. `warn` tints it red.
    case callout([String], warn: Bool = false)
    case grid(NbGridBlock)
    case plot(NbPlotBlock)
    /// Vertical bars with their value above and a label under; `hot` columns are violet.
    case columns(values: [Double], labels: [String], hot: Set<Int>, top: Double)
    case pipeline(steps: [String], current: Int, done: Int = 0)
    case tree(nodes: [NbNode], edges: [NbEdge], height: CGFloat = 220)
    /// Columns of neurons with their activations; nil is a unit ReLU zeroed.
    case net(cols: [[Double?]], inks: [NbInk], footers: [(String, String)])
    case text([NbSpan], mono: Bool = false)
    /// Rows of context tokens, an arrow with a caption, and the predicted token.
    case flow(rows: [[NbTok]], caption: String, target: NbTok)
    case banner(big: String, text: String, ink: NbInk, bigRight: Bool = false)
    case models([NbModel])
    case kv([(String, String)])
}

struct NbLegend { let ink: NbInk; let label: String; var style: SwatchStyle = .fill }

struct NbFrame {
    let blocks: [NbBlock]
    let legend: [NbLegend]
    let headline: String
    let body: String
    var chips: [DkChip] = []
    var fx: [String] = []
}

// MARK: - Lab

@MainActor
@Observable
private final class NbModelState {
    let playback: PlaybackState
    private(set) var frames: [NbFrame] = []
    private var openStep = 0

    init(topicId: String) {
        playback = PlaybackState(stepCount: 1, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        openStep = UserDefaults.standard.integer(forKey: "openStep")
        #endif
        // Some labs run a search or train a small model, so frames are built off the main thread.
        Task { [weak self] in
            let built = await Task.detached(priority: .userInitiated) { nlpLab(topicId) }.value
            guard let self else { return }
            self.frames = built
            self.playback.stepCount = built.count
            if self.openStep > 0 { self.playback.index = min(self.openStep, built.count) - 1 }
        }
    }

    var index: Int { min(max(playback.index, 0), frames.count - 1) }
}

struct NlpStoryLab: View {
    @State private var model: NbModelState
    @Environment(\.labDock) private var dock

    init(topicId: String) { _model = State(initialValue: NbModelState(topicId: topicId)) }

    var body: some View {
        Group {
            if model.frames.isEmpty {
                ProgressView().frame(maxWidth: .infinity, minHeight: 320).card(radius: 20)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    NbBody(frame: model.frames[model.index])
                    if dock == nil {
                        Divider().padding(.top, 16)
                        LabTransportBar(state: model.playback, captions: model.frames.map { storyPlain($0.headline) }).padding(.top, 14)
                    }
                }
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(NbControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.playback.reset() }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct NbControls: View {
    let model: NbModelState
    var body: some View { LabTransportBar(state: model.playback, captions: model.frames.map { storyPlain($0.headline) }) }
}

private struct NbBody: View {
    let frame: NbFrame
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(frame.blocks.indices, id: \.self) { NbBlockView(block: frame.blocks[$0]) }
            if !frame.legend.isEmpty {
                StoryLegendRow(items: frame.legend.map { (color: nbColor($0.ink), style: $0.style, label: $0.label) })
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 20)
        if !frame.fx.isEmpty { NbCallout(lines: frame.fx, warn: false, bg: SimColors.tint).padding(.top, 12) }
        if !frame.chips.isEmpty {
            LabChips(chips: frame.chips.map { LabChip(key: $0.key, value: $0.value, tint: $0.tint ? .answer : nil) }).padding(.top, 12)
        }
        LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
    }
}

private struct NbBlockView: View {
    let block: NbBlock

    var body: some View {
        switch block {
        case .caption(let text, let note): NbCaption(text: text, note: note)
        case .toks(let toks, let label, let columns, let mono, let start): NbToksView(toks: toks, label: label, columns: columns, mono: mono, start: start)
        case .table(let headers, let weights, let rows, let aligns): NbTableView(headers: headers, weights: weights, rows: rows, aligns: aligns)
        case .bars(let rows, let ticks, let ref, let labelWidth): NbBarsView(rows: rows, ticks: ticks, ref: ref, labelWidth: labelWidth)
        case .tiles(let tiles): NbTilesView(tiles: tiles)
        case .boxes(let boxes): NbBoxesView(boxes: boxes)
        case .callout(let lines, let warn): NbCallout(lines: lines, warn: warn)
        case .grid(let g): NbGridView(g: g)
        case .plot(let p): NbPlotView(plot: p)
        case .columns(let values, let labels, let hot, let top): NbColumnsView(values: values, labels: labels, hot: hot, top: top)
        case .pipeline(let steps, let current, let done): NbPipelineView(steps: steps, current: current, done: done)
        case .tree(let nodes, let edges, let height): NbTreeView(nodes: nodes, edges: edges, height: height)
        case .net(let cols, let inks, let footers): NbNetView(cols: cols, inks: inks, footers: footers)
        case .text(let spans, let mono): NbTextView(spans: spans, mono: mono)
        case .flow(let rows, let caption, let target): NbFlowView(rows: rows, caption: caption, target: target)
        case .banner(let big, let text, let ink, let bigRight): NbBannerView(big: big, text: text, ink: ink, bigRight: bigRight)
        case .models(let rows): NbModelsView(rows: rows)
        case .kv(let rows): NbKvView(rows: rows)
        }
    }
}

// MARK: - Colours

func nbColor(_ ink: NbInk) -> Color {
    switch ink {
    case .yellow: SimColors.active
    case .violet: Color(hex: 0x6366F1)
    case .indigo: Color(hex: 0x818CF8)
    case .blue: Color(hex: 0x3B82F6)
    case .sky: Color(hex: 0x0EA5E9)
    case .green: Color(hex: 0x22A06B)
    case .red: Color(hex: 0xF87171)
    case .pink: Color(hex: 0xEC4899)
    case .orange: Color(hex: 0xF97316)
    case .grey: Color(hex: 0x9AA0AE)
    case .slate: Color(hex: 0x3A3F4C)
    case .teal: Color(hex: 0x14B8A6)
    }
}

/// The ink as text: green and red read lighter on the dark card.
private func nbText(_ ink: NbInk) -> Color {
    switch ink {
    case .green: Color(hex: 0x5FD49B)
    case .blue: Color(hex: 0x8FB6FF)
    case .pink: Color(hex: 0xF47AA8)
    case .orange: Color(hex: 0xFB923C)
    default: nbColor(ink)
    }
}

private let nbCluster: [Color] = [0x0EA5E9, 0x22A06B, 0xF97316, 0xEC4899, 0x8B5CF6, 0xEAB308, 0x14B8A6, 0x6366F1, 0xF87171, 0x84CC16, 0x06B6D4, 0xA855F7].map { Color(hex: $0) }

private let nbWell = Color.black.opacity(0.16)

private func toneColors(_ tone: NbTone, _ palette: Palette) -> (Color, Color) {
    switch tone {
    case .plain: (SimColors.tint, palette.onSurface)
    case .hot: (SimColors.active, Color(hex: 0x1A1505))
    case .pick: (Color(hex: 0x6366F1), .white)
    case .dim: (nbWell, palette.muted.opacity(0.7))
    case .empty: (.clear, palette.muted)
    case .good: (Color(hex: 0x22A06B).opacity(0.28), Color(hex: 0x5FD49B))
    case .bad: (Color(hex: 0xF87171).opacity(0.18), Color(hex: 0xF87171))
    case .blue: (Color(hex: 0x3B82F6).opacity(0.32), Color(hex: 0xCFE0FF))
    case .pink: (Color(hex: 0xEC4899).opacity(0.3), Color(hex: 0xFBCFE8))
    case .orange: (Color(hex: 0xF97316).opacity(0.32), Color(hex: 0xFED7AA))
    }
}

private func line(_ a: CGPoint, _ b: CGPoint) -> Path { var p = Path(); p.move(to: a); p.addLine(to: b); return p }
private func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private extension GraphicsContext {
    func label(_ text: String, _ font: Font, _ color: Color, _ at: CGPoint, _ anchor: UnitPoint = .center) {
        draw(Text(text).font(font).foregroundStyle(color), at: at, anchor: anchor)
    }
}

// MARK: - Blocks

private struct NbCaption: View {
    let text: String
    let note: String?
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(alignment: .top) {
            Text(text).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.muted).frame(maxWidth: .infinity, alignment: .leading)
            if let note { Text(note).font(AppFont.mono(12)).foregroundStyle(palette.muted).lineLimit(1).padding(.leading, 10) }
        }
    }
}

private struct NbTokView: View {
    let tok: NbTok
    var mono = false
    var start = false
    var fill = false
    @Environment(\.palette) private var palette

    var body: some View {
        let (bg, fg) = toneColors(tok.tone, palette)
        VStack(alignment: start ? .leading : .center, spacing: 0) {
            Text(tok.t).font(mono ? AppFont.mono(13, .medium) : AppFont.sans(15, .semibold)).foregroundStyle(fg)
                .strikethrough(tok.tone == .bad).lineLimit(1).truncationMode(.tail)
            if !tok.sub.isEmpty { Text(tok.sub).font(AppFont.mono(9.5)).foregroundStyle(fg.opacity(0.75)).lineLimit(1) }
        }
        .padding(.horizontal, 10).padding(.vertical, 3)
        .frame(minHeight: 32)
        .frame(maxWidth: fill ? .infinity : nil, alignment: start ? .leading : .center)
        .background(bg, in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            if tok.tone == .empty { RoundedRectangle(cornerRadius: 8).strokeBorder(palette.muted.opacity(0.5), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])) }
        }
    }
}

private struct NbToksView: View {
    let toks: [NbTok]
    let label: String?
    let columns: Int?
    let mono: Bool
    let start: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            if let label { Text(label).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.muted).frame(width: 64, alignment: .leading) }
            if let cols = columns {
                let chunks = stride(from: 0, to: toks.count, by: cols).map { Array(toks[$0..<min($0 + cols, toks.count)]) }
                VStack(spacing: 6) {
                    ForEach(chunks.indices, id: \.self) { r in
                        HStack(spacing: 6) {
                            ForEach(chunks[r].indices, id: \.self) { NbTokView(tok: chunks[r][$0], mono: mono, start: start, fill: true) }
                            ForEach(0..<(cols - chunks[r].count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                        }
                    }
                }
                .frame(maxWidth: .infinity)
            } else {
                FlowLayout(spacing: 6, lineSpacing: 6) {
                    ForEach(toks.indices, id: \.self) { NbTokView(tok: toks[$0], mono: mono) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

private struct NbTableView: View {
    let headers: [String]
    let weights: [CGFloat]
    let rows: [NbRow]
    let aligns: [Int]?
    @Environment(\.palette) private var palette

    private func align(_ i: Int) -> Alignment {
        switch (aligns ?? weights.indices.map { $0 == 0 ? 0 : 2 })[i] {
        case 0: .leading
        case 1: .center
        default: .trailing
        }
    }

    private func hAlign(_ i: Int) -> HorizontalAlignment { align(i) == .leading ? .leading : align(i) == .center ? .center : .trailing }

    var body: some View {
        VStack(spacing: 3) {
            if !headers.isEmpty {
                NbWeightedRow(weights: weights, spacing: 6) { i in
                    Text(headers[i]).font(AppFont.sans(11, .semibold)).foregroundStyle(palette.muted).lineLimit(1).frame(maxWidth: .infinity, alignment: align(i))
                }
                .padding(.horizontal, 10)
            }
            ForEach(rows.indices, id: \.self) { r in
                let row = rows[r]
                NbWeightedRow(weights: weights, spacing: 6) { i in
                    let c = row.cells[i]
                    VStack(alignment: hAlign(i), spacing: 0) {
                        if let bar = c.bar {
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 3).fill(SimColors.tint)
                                    RoundedRectangle(cornerRadius: 3).fill(nbColor(c.ink ?? .sky)).frame(width: geo.size.width * CGFloat(min(max(bar, 0.02), 1)))
                                }
                            }
                            .frame(height: 8)
                        } else {
                            let first = i == 0 && !c.mono
                            let color: Color = c.ink.map(nbText) ?? (row.ring && i == 0 ? SimColors.active : first ? palette.onSurface : palette.muted)
                            Text(c.text)
                                .font(c.mono ? AppFont.mono(12, c.bold ? .semibold : .regular) : AppFont.sans(13, c.bold || first ? .semibold : .regular))
                                .foregroundStyle(color.opacity(row.dim ? 0.5 : 1))
                                .lineLimit(1).truncationMode(.tail)
                            if !c.sub.isEmpty { Text(c.sub).font(AppFont.sans(11)).foregroundStyle(palette.muted).multilineTextAlignment(i == 0 ? .leading : .trailing) }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: align(i))
                }
                .padding(.horizontal, 10).padding(.vertical, 4)
                .frame(minHeight: 30)
                .background(row.tint.map { nbColor($0).opacity(0.2) } ?? nbWell, in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(row.ring ? SimColors.active : .clear, lineWidth: 1.5))
            }
        }
    }
}

/// Children laid out side by side in proportion to `weights`, like Compose's Modifier.weight.
private struct NbWeightedRow<Cell: View>: View {
    let weights: [CGFloat]
    let spacing: CGFloat
    @ViewBuilder let cell: (Int) -> Cell

    var body: some View {
        NbWeightedLayout(weights: weights, spacing: spacing) {
            ForEach(weights.indices, id: \.self) { cell($0) }
        }
    }
}

private struct NbWeightedLayout: Layout {
    let weights: [CGFloat]
    let spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 300
        let widths = columnWidths(width)
        let height = subviews.indices.map { subviews[$0].sizeThatFits(ProposedViewSize(width: widths[$0], height: nil)).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let widths = columnWidths(bounds.width)
        var x = bounds.minX
        for i in subviews.indices {
            subviews[i].place(at: CGPoint(x: x, y: bounds.midY), anchor: .leading, proposal: ProposedViewSize(width: widths[i], height: nil))
            x += widths[i] + spacing
        }
    }

    private func columnWidths(_ width: CGFloat) -> [CGFloat] {
        let total = weights.reduce(0, +)
        let avail = max(width - spacing * CGFloat(weights.count - 1), 0)
        return weights.map { avail * $0 / total }
    }
}

private struct NbBarsView: View {
    let rows: [NbBar]
    let ticks: [(Double, String)]
    let ref: (Double, String)?
    let labelWidth: CGFloat
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            ForEach(rows.indices, id: \.self) { i in
                let r = rows[i]
                HStack(spacing: 0) {
                    Text(r.label).font(AppFont.sans(13, .semibold)).foregroundStyle(r.labelInk.map(nbText) ?? palette.onSurface).lineLimit(1).frame(width: labelWidth, alignment: .leading)
                    Canvas { ctx, size in
                        let y = size.height / 2
                        ctx.fill(Path(roundedRect: CGRect(x: 0, y: y - 6, width: size.width, height: 12), cornerRadius: 3), with: .color(.black.opacity(0.22)))
                        let w = size.width * CGFloat(min(max(r.frac, 0), 1))
                        if w > 0 { ctx.fill(Path(roundedRect: CGRect(x: 0, y: y - 6, width: max(w, 3), height: 12), cornerRadius: 3), with: .color(nbColor(r.ink))) }
                        if let (f, _) = ref {
                            let x = size.width * CGFloat(f)
                            ctx.stroke(line(CGPoint(x: x, y: 0), CGPoint(x: x, y: size.height)), with: .color(palette.muted.opacity(0.8)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                        }
                    }
                    .frame(height: 22).padding(.horizontal, 6)
                    Text(r.value).font(AppFont.mono(12)).foregroundStyle(palette.onSurface.opacity(0.85)).lineLimit(1).frame(width: 62, alignment: .trailing)
                }
                .frame(height: 22)
            }
            if !ticks.isEmpty || ref != nil {
                HStack(spacing: 0) {
                    Color.clear.frame(width: labelWidth, height: 1)
                    Canvas { ctx, size in
                        for (f, s) in ticks { ctx.label(s, AppFont.mono(9.5), palette.muted, CGPoint(x: size.width * CGFloat(f), y: 7)) }
                        if let (f, s) = ref { ctx.label(s, AppFont.mono(9.5), SimColors.active, CGPoint(x: size.width * CGFloat(f), y: 20)) }
                    }
                    .frame(height: 28).padding(.horizontal, 6)
                    Color.clear.frame(width: 62, height: 1)
                }
            }
        }
    }
}

private struct NbTilesView: View {
    let tiles: [NbTile]
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(tiles.indices, id: \.self) { i in
                let t = tiles[i]
                VStack(alignment: .leading, spacing: 0) {
                    Text(t.big).font(AppFont.grotesk(t.big.count > 7 ? 18 : tiles.count > 3 ? 20 : 26, .bold)).foregroundStyle(t.ink.map(nbText) ?? palette.onSurface).lineLimit(1).minimumScaleFactor(0.6)
                    Text(t.caption).font(AppFont.sans(12)).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 12).padding(.vertical, 10)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(t.tint.map { nbColor($0).opacity(0.16) } ?? nbWell, in: RoundedRectangle(cornerRadius: 12))
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

private struct NbBoxesView: View {
    let boxes: [NbBox]
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(boxes.indices, id: \.self) { i in
                let b = boxes[i]
                VStack(alignment: .leading, spacing: 1) {
                    Text(b.title).font(AppFont.sans(13, .bold)).foregroundStyle(b.ink.map(nbText) ?? palette.onSurface)
                    ForEach(b.lines.indices, id: \.self) { k in
                        let last = k == b.lines.count - 1 && b.lastInk != nil
                        Text(b.lines[k]).font(last ? AppFont.mono(12) : AppFont.sans(12)).foregroundStyle(last ? nbText(b.lastInk!) : palette.muted).padding(.top, last ? 3 : 0)
                    }
                }
                .padding(.horizontal, 12).padding(.vertical, 9)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(nbWell, in: RoundedRectangle(cornerRadius: 10))
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

private struct NbCallout: View {
    let lines: [String]
    let warn: Bool
    var bg: Color = nbWell
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(lines.indices, id: \.self) { i in
                storyText(lines[i], palette).font(warn ? AppFont.sans(13) : AppFont.mono(12.5))
                    .foregroundStyle(warn ? Color(hex: 0xFECACA) : palette.onSurface.opacity(0.88))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12).padding(.vertical, 9)
        .background(warn ? Color(hex: 0xF87171).opacity(0.16) : bg, in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct NbGridView: View {
    let g: NbGridBlock
    @Environment(\.palette) private var palette

    var body: some View {
        let labelW: CGFloat = g.rows.isEmpty ? 0 : 46
        VStack(spacing: 3) {
            if !g.cols.isEmpty {
                HStack(spacing: 3) {
                    if labelW > 0 { Color.clear.frame(width: labelW - 3, height: 1) }
                    ForEach(g.cols.indices, id: \.self) { Text(g.cols[$0]).font(AppFont.mono(9.5)).foregroundStyle(palette.muted).lineLimit(1).frame(maxWidth: .infinity) }
                }
            }
            ForEach(g.cells.indices, id: \.self) { r in
                HStack(spacing: 3) {
                    if !g.rows.isEmpty {
                        Text(g.rows[r]).font(AppFont.sans(12, .semibold)).foregroundStyle(g.hotRow == r ? SimColors.active : palette.onSurface.opacity(0.85)).lineLimit(1).minimumScaleFactor(0.75).frame(width: labelW - 3, alignment: .leading)
                    }
                    ForEach(g.cells[r].indices, id: \.self) { c in
                        let cell = g.cells[r][c]
                        ZStack {
                            RoundedRectangle(cornerRadius: 5).fill(cell.level <= 0 ? nbWell : nbColor(cell.ink).opacity(min(0.18 + 0.75 * cell.level, 0.95)))
                            if g.text {
                                Text(cell.text).font(AppFont.mono(11, .bold)).foregroundStyle(cell.level <= 0 ? palette.muted.opacity(0.6) : .white).lineLimit(1).minimumScaleFactor(0.7)
                            }
                        }
                        .frame(maxWidth: .infinity).frame(height: g.cellHeight)
                        .overlay(RoundedRectangle(cornerRadius: 5).strokeBorder(cell.ring ? SimColors.active : .clear, lineWidth: 1.5))
                    }
                }
            }
            if let (lo, hi) = g.scale {
                HStack(spacing: 6) {
                    if labelW > 0 { Color.clear.frame(width: labelW - 6, height: 1) }
                    Text(lo).font(AppFont.mono(9.5)).foregroundStyle(palette.muted)
                    LinearGradient(colors: [nbColor(.violet).opacity(0.15), nbColor(.indigo)], startPoint: .leading, endPoint: .trailing).frame(height: 5).clipShape(Capsule())
                    Text(hi).font(AppFont.mono(9.5)).foregroundStyle(palette.muted)
                }
                .padding(.top, 6)
            }
        }
    }
}

private struct NbPlotView: View {
    let plot: NbPlotBlock
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = plot.yTicks.isEmpty ? 12 : 34, right = size.width - 12, top: CGFloat = 14
            let bottom = size.height - (plot.xTicks.isEmpty ? 12 : plot.groups.isEmpty ? 24 : 36)
            func px(_ x: Double) -> CGFloat { left + CGFloat((x - plot.xr.0) / (plot.xr.1 - plot.xr.0)) * (right - left) }
            func py(_ y: Double) -> CGFloat { bottom - CGFloat((y - plot.yr.0) / (plot.yr.1 - plot.yr.0)) * (bottom - top) }
            func at(_ p: NbP) -> CGPoint { CGPoint(x: px(p.x), y: py(p.y)) }
            if let (a, b, _) = plot.shade {
                ctx.fill(Path(CGRect(x: px(a), y: top, width: px(b) - px(a), height: bottom - top)), with: .color(Color(hex: 0xEC4899).opacity(0.12)))
            }
            for (v, s) in plot.yTicks {
                if plot.grid { ctx.stroke(line(CGPoint(x: left, y: py(v)), CGPoint(x: right, y: py(v))), with: .color(palette.muted.opacity(0.15)), lineWidth: 1) }
                ctx.label(s, AppFont.mono(9), palette.muted, CGPoint(x: left - 5, y: py(v)), .trailing)
            }
            for (v, s) in plot.xTicks { ctx.label(s, AppFont.mono(9), palette.muted, CGPoint(x: px(v), y: bottom + 10)) }
            for (v, s) in plot.groups { ctx.label(s, AppFont.mono(9), palette.muted, CGPoint(x: px(v), y: bottom + 23)) }
            if let (a, b, s) = plot.shade, plot.groups.isEmpty, !s.isEmpty { ctx.label(s, AppFont.mono(9), nbText(.pink), CGPoint(x: (px(a) + px(b)) / 2, y: top + 8)) }
            if !plot.xLabel.isEmpty { ctx.label(plot.xLabel, AppFont.mono(9), palette.muted, CGPoint(x: right, y: bottom - 9), .trailing) }
            if !plot.yLabel.isEmpty { ctx.label(plot.yLabel, AppFont.mono(9), palette.muted, CGPoint(x: left + 4, y: top + 2), .leading) }
            for s in plot.segs {
                ctx.stroke(line(at(s.a), at(s.b)), with: .color(nbColor(s.ink)), style: StrokeStyle(lineWidth: s.width, lineCap: .round, dash: s.dashed ? [4, 3] : []))
            }
            for l in plot.lines {
                var path = Path()
                for (i, p) in l.pts.enumerated() { if i == 0 { path.move(to: at(p)) } else { path.addLine(to: at(p)) } }
                ctx.stroke(path, with: .color(nbColor(l.ink)), style: StrokeStyle(lineWidth: l.width, lineCap: .round, lineJoin: .round, dash: l.dashed ? [5, 4] : []))
                if l.dots { for p in l.pts { ctx.fill(circle(at(p), 3), with: .color(nbColor(l.ink))) } }
            }
            for d in plot.dots {
                let c = d.cat.map { nbCluster[$0 % nbCluster.count] } ?? nbColor(d.ink)
                let o = at(d.p)
                if d.hollow {
                    ctx.fill(circle(o, d.r), with: .color(Color(hex: 0x171A23)))
                    ctx.stroke(circle(o, d.r), with: .color(.white), lineWidth: 2)
                } else {
                    ctx.fill(circle(o, d.r), with: .color(c))
                    if d.ring { ctx.stroke(circle(o, d.r), with: .color(.white), lineWidth: 1.5) }
                }
                if let t = d.text { ctx.label(t, AppFont.mono(8.5, .bold), .white, o) }
                if let l = d.label { ctx.label(l, AppFont.mono(9.5, .bold), c, CGPoint(x: o.x + d.r + 4, y: o.y - 7), .leading) }
            }
        }
        .frame(height: plot.height)
        .modifier(DkStageShape())
    }
}

private struct NbColumnsView: View {
    let values: [Double]
    let labels: [String]
    let hot: Set<Int>
    let top: Double
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let n = values.count
            let slot = size.width / CGFloat(n)
            let bottom = size.height - 18, topY: CGFloat = 16
            ctx.stroke(line(CGPoint(x: 0, y: bottom), CGPoint(x: size.width, y: bottom)), with: .color(palette.muted.opacity(0.3)), lineWidth: 1)
            for (i, v) in values.enumerated() {
                let isHot = hot.contains(i)
                let h = max(CGFloat(min(max(v / top, 0), 1)) * (bottom - topY), 2)
                ctx.fill(Path(roundedRect: CGRect(x: slot * CGFloat(i) + slot * 0.15, y: bottom - h, width: slot * 0.7, height: h), cornerRadius: 3), with: .color(isHot ? nbColor(.violet) : Color(hex: 0x3A3F4C)))
                ctx.label(dkNum(v, 2), AppFont.mono(9), isHot ? palette.onSurface : palette.muted, CGPoint(x: slot * CGFloat(i) + slot / 2, y: bottom - h - 8))
                ctx.label(labels[i], AppFont.mono(9.5, isHot ? .bold : .regular), isHot ? palette.onSurface : palette.muted, CGPoint(x: slot * CGFloat(i) + slot / 2, y: bottom + 9))
            }
        }
        .frame(height: 130)
    }
}

private struct NbPipelineView: View {
    let steps: [String]
    let current: Int
    let done: Int
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 4) {
            ForEach(steps.indices, id: \.self) { i in
                let bg: Color = i == current ? nbColor(.violet) : i < done ? nbColor(.violet).opacity(0.3) : nbWell
                Text(steps[i]).font(AppFont.sans(11, .semibold)).lineLimit(1).minimumScaleFactor(0.8)
                    .foregroundStyle(i == current ? Color.white : i < done ? Color(hex: 0xC7D2FE) : palette.muted)
                    .frame(maxWidth: .infinity).frame(height: 32)
                    .background(bg, in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

// Tree nodes are solid: a phrase in green, the current one yellow, a failure pink.
private func treeFill(_ tone: NbTone, _ tint: Color) -> Color {
    switch tone {
    case .plain: Color(hex: 0x2A2E39)
    case .good: nbColor(.green)
    case .bad: Color(hex: 0xEC4899).opacity(0.3)
    default: tint
    }
}

private func treeInk(_ tone: NbTone, _ ink: Color) -> Color {
    switch tone {
    case .plain, .good: Color(hex: 0xF2F3F7)
    case .bad: Color(hex: 0xF47AA8)
    default: ink
    }
}

private struct NbTreeView: View {
    let nodes: [NbNode]
    let edges: [NbEdge]
    let height: CGFloat
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            func c(_ n: NbNode) -> CGPoint { CGPoint(x: size.width * n.x, y: size.height * n.y) }
            for e in edges {
                ctx.stroke(line(c(nodes[e.a]), c(nodes[e.b])), with: .color(e.hot ? nbColor(.blue) : palette.muted.opacity(0.6)), lineWidth: e.hot ? 1.8 : 1.2)
            }
            for n in nodes {
                let (bg, fg) = toneColors(n.tone, palette)
                let main = ctx.resolve(Text(n.text).font(n.dashed ? AppFont.mono(10) : AppFont.mono(11.5, .bold)))
                let sub = n.sub.isEmpty ? nil : ctx.resolve(Text(n.sub).font(AppFont.mono(8.5)))
                let ms = main.measure(in: size), ss = sub?.measure(in: size) ?? .zero
                let w = max(ms.width, ss.width) + 16, h = ms.height + ss.height + 8
                let o = c(n)
                let rect = CGRect(x: o.x - w / 2, y: o.y - h / 2, width: w, height: h)
                if n.dashed {
                    ctx.stroke(Path(roundedRect: rect, cornerRadius: 7), with: .color(palette.muted.opacity(0.6)), style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                } else {
                    ctx.fill(Path(roundedRect: rect, cornerRadius: 7), with: .color(treeFill(n.tone, bg)))
                }
                let txt = n.dashed ? palette.muted : treeInk(n.tone, fg)
                if n.sub.isEmpty {
                    ctx.draw(Text(n.text).font(AppFont.mono(11.5, .bold)).foregroundStyle(txt), at: o)
                } else if n.dashed {
                    ctx.draw(Text(n.text).font(AppFont.mono(10)).foregroundStyle(txt), at: CGPoint(x: o.x, y: rect.minY + 4 + ms.height / 2))
                    ctx.draw(Text(n.sub).font(AppFont.mono(8.5)).foregroundStyle(nbText(.green)), at: CGPoint(x: o.x, y: rect.minY + 4 + ms.height + ss.height / 2))
                } else {
                    ctx.draw(Text(n.sub).font(AppFont.mono(8.5)).foregroundStyle(txt.opacity(0.75)), at: CGPoint(x: o.x, y: rect.minY + 3 + ss.height / 2))
                    ctx.draw(Text(n.text).font(AppFont.mono(11.5, .bold)).foregroundStyle(txt), at: CGPoint(x: o.x, y: rect.minY + 3 + ss.height + ms.height / 2))
                }
                if !n.below.isEmpty { ctx.label(n.below, AppFont.mono(9), n.belowInk.map(nbText) ?? palette.muted, CGPoint(x: o.x, y: rect.maxY + 9)) }
            }
        }
        .frame(height: height)
        .modifier(DkStageShape())
    }
}

private struct NbNetView: View {
    let cols: [[Double?]]
    let inks: [NbInk]
    let footers: [(String, String)]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let n = cols.count
            func cx(_ k: Int) -> CGFloat { size.width * (0.14 + 0.72 * CGFloat(k) / CGFloat(n - 1)) }
            func cy(_ k: Int, _ i: Int) -> CGFloat {
                let count = cols[k].count
                let top: CGFloat = 18, bot = size.height - 30 - 8
                return count == 1 ? (top + bot) / 2 : top + (bot - top) * CGFloat(i) / CGFloat(count - 1)
            }
            for k in 0..<(n - 1) {
                for i in cols[k].indices {
                    for j in cols[k + 1].indices {
                        let off = cols[k][i] == nil || cols[k + 1][j] == nil
                        ctx.stroke(line(CGPoint(x: cx(k), y: cy(k, i)), CGPoint(x: cx(k + 1), y: cy(k + 1, j))), with: .color(nbColor(.sky).opacity(off ? 0.05 : 0.18)), lineWidth: 0.8)
                    }
                }
            }
            for (k, col) in cols.enumerated() {
                for (i, v) in col.enumerated() {
                    let o = CGPoint(x: cx(k), y: cy(k, i))
                    let r: CGFloat = col.count > 5 ? 10 : 14
                    ctx.fill(circle(o, r), with: .color(v == nil ? Color(hex: 0x2A2E39) : nbColor(inks[k])))
                    ctx.label(v.map { dkNum($0, 2) } ?? "0.00", AppFont.mono(col.count > 5 ? 8 : 9, .bold), v == nil ? palette.muted.opacity(0.6) : .white, o)
                }
                ctx.label(footers[k].0, AppFont.mono(9.5, .bold), Color(hex: 0xF2F3F7), CGPoint(x: cx(k), y: size.height - 20))
                ctx.label(footers[k].1, AppFont.mono(8.5), palette.muted, CGPoint(x: cx(k), y: size.height - 9))
            }
        }
        .frame(height: 240)
        .modifier(DkStageShape())
    }
}

private struct NbTextView: View {
    let spans: [NbSpan]
    let mono: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        var out = AttributedString()
        for s in spans {
            var a = AttributedString(s.t)
            let (bg, fg) = toneColors(s.tone, palette)
            switch s.tone {
            case .plain: a.foregroundColor = palette.onSurface
            case .dim: a.foregroundColor = palette.muted.opacity(0.6)
            default:
                a.backgroundColor = bg
                a.foregroundColor = s.tone == .hot ? Color(hex: 0x1A1505) : fg
            }
            out += a
        }
        return Text(out).font(mono ? AppFont.mono(15) : AppFont.sans(15)).lineSpacing(9)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12).padding(.vertical, 10)
            .background(nbWell, in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct NbFlowView: View {
    let rows: [[NbTok]]
    let caption: String
    let target: NbTok
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(rows.indices, id: \.self) { r in HStack(spacing: 6) { ForEach(rows[r].indices, id: \.self) { NbTokView(tok: rows[r][$0]) } } }
            }
            Text("→").font(AppFont.sans(20)).foregroundStyle(palette.muted).padding(.horizontal, 8)
            Text(caption).font(AppFont.sans(11)).foregroundStyle(palette.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity)
            Text("→").font(AppFont.sans(20)).foregroundStyle(palette.muted).padding(.horizontal, 8)
            NbTokView(tok: target)
        }
    }
}

private struct NbBannerView: View {
    let big: String
    let text: String
    let ink: NbInk
    let bigRight: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 10) {
            if bigRight {
                Text(text).font(AppFont.mono(13)).foregroundStyle(palette.onSurface).frame(maxWidth: .infinity, alignment: .leading)
                Text(big).font(AppFont.grotesk(28, .bold)).foregroundStyle(nbText(ink))
            } else {
                Text(big).font(AppFont.grotesk(22, .bold)).foregroundStyle(nbText(ink)).frame(maxWidth: .infinity, alignment: .leading)
                Text(text).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.85)).frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .background(nbColor(ink).opacity(0.16), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct NbModelsView: View {
    let rows: [NbModel]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            ForEach(rows.indices, id: \.self) { i in
                let m = rows[i]
                HStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(m.name).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.onSurface).lineLimit(1)
                        Text(m.sub).font(AppFont.sans(11)).foregroundStyle(palette.muted).lineLimit(1)
                    }
                    .frame(width: 92, alignment: .leading)
                    ForEach([("enc", m.enc, 0), ("dec", m.dec, 1)], id: \.0) { s, on, k in
                        Text(s).font(AppFont.sans(11, .bold)).foregroundStyle(on ? Color.white : palette.muted.opacity(0.5))
                            .frame(width: 34, height: 22)
                            .background(!on ? nbWell : k == 0 ? nbColor(.blue).opacity(0.55) : nbColor(.violet).opacity(0.7), in: RoundedRectangle(cornerRadius: 5))
                            .padding(.leading, 4)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 3).fill(SimColors.tint)
                            RoundedRectangle(cornerRadius: 3).fill(m.ring ? SimColors.active : Color(hex: 0x6B7180)).frame(width: geo.size.width * CGFloat(min(max(m.frac, 0.02), 1)))
                        }
                    }
                    .frame(height: 6).padding(.horizontal, 8)
                    Text(m.value).font(AppFont.mono(12)).foregroundStyle(palette.onSurface.opacity(0.85)).lineLimit(1)
                }
                .padding(.horizontal, 10).padding(.vertical, 8)
                .background(nbWell, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(m.ring ? SimColors.active : .clear, lineWidth: 1.5))
            }
        }
    }
}

private struct NbKvView: View {
    let rows: [(String, String)]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            ForEach(rows.indices, id: \.self) { i in
                HStack(alignment: .top, spacing: 0) {
                    Text(rows[i].0).font(AppFont.sans(13)).foregroundStyle(palette.muted).frame(width: 130, alignment: .leading)
                    Text(rows[i].1).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.onSurface).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

// MARK: - Frame builders shared by the three frame files

func nbTok(_ t: String, _ tone: NbTone = .plain, _ sub: String = "") -> NbTok { NbTok(t: t, tone: tone, sub: sub) }
func nbLegend(_ ink: NbInk, _ label: String, _ style: SwatchStyle = .fill) -> NbLegend { NbLegend(ink: ink, label: label, style: style) }
func nbChip(_ key: String, _ value: String, _ tint: Bool = false) -> DkChip { DkChip(key: key, value: value, tint: tint) }
func nbCell(_ text: String, _ ink: NbInk? = nil, bold: Bool = false, sub: String = "") -> NbCell { NbCell(text: text, ink: ink, bold: bold, sub: sub) }
func nbName(_ text: String, _ ink: NbInk? = nil, sub: String = "") -> NbCell { NbCell(text: text, ink: ink, bold: true, sub: sub, mono: false) }
func nbProse(_ text: String, sub: String = "", _ ink: NbInk? = nil) -> NbCell { NbCell(text: text, ink: ink, sub: sub, mono: false) }
func nbF(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }

/// ".95" style: two decimals with the leading zero dropped.
func nbDot2(_ v: Double) -> String {
    let s = dkNum(v, 2)
    if s.hasPrefix("0.") { return String(s.dropFirst()) }
    if s.hasPrefix("−0.") { return "−" + s.dropFirst(2) }
    return s
}

func nbGrouped(_ v: Int64) -> String {
    let digits = String(abs(v))
    var out = ""
    for (i, ch) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out += "," }
        out.append(ch)
    }
    return v < 0 ? "-" + out : out
}
