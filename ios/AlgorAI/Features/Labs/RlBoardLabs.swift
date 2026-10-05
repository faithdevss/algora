import SwiftUI

// Port of the drawing half of RlBoardLabs.kt: the card of stacked blocks (caption, chips, bars, value
// grids, columns, a scene of dots, key/value stats) over a legend, the step's arithmetic in a formula
// box and a headline, with an optional picker over the play transport. The frames and the design's
// colour strings come from RlBoardModel.swift; the dark-only neutrals are mapped onto the palette here.

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class RbModel {
    let lab: RbLab
    let playback: PlaybackState
    var tab: Int { didSet { if tab != oldValue { load() } } }
    /// Several labs train or search for real, so each option's frames are built off the main thread.
    private(set) var frames: [RbFrame] = []
    private var openStep = 0

    init(lab: RbLab) {
        self.lab = lab
        tab = lab.initialTab
        playback = PlaybackState(stepCount: 1, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        openStep = UserDefaults.standard.integer(forKey: "openStep")
        #endif
        load()
    }

    private func load() {
        let lab = lab, t = tab
        Task { [weak self] in
            let built = await Task.detached(priority: .userInitiated) { lab.frames(t) }.value
            guard let self, self.tab == t else { return }
            // The step count can differ between options; keep the step where it still exists.
            self.frames = built
            self.playback.stepCount = built.count
            self.playback.index = min(self.playback.index, built.count - 1)
            if self.openStep > 0 {
                self.playback.index = min(self.openStep, built.count) - 1
                self.openStep = 0
            }
        }
    }

    var index: Int { min(max(playback.index, 0), frames.count - 1) }

    func reset() {
        tab = lab.initialTab
        playback.reset()
    }
}

struct RlBoardLab: View {
    @State private var model: RbModel
    @Environment(\.labDock) private var dock

    init(topicId: String) { _model = State(initialValue: RbModel(lab: rlBoardLab(topicId))) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if model.frames.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 320)
                    .card(radius: 20)
            } else {
                RbBody(frame: model.frames[model.index])
            }
            if dock == nil {
                Divider().padding(.top, 16)
                RbControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(RbControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.reset() }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct RbControls: View {
    let model: RbModel

    var body: some View {
        VStack(spacing: 14) {
            if !model.lab.tabs.isEmpty {
                LabSegments(labels: model.lab.tabs, selected: Binding(get: { model.tab }, set: { model.tab = $0 }))
            }
            LabTransportBar(state: model.playback, captions: model.frames.map { storyPlain($0.capT) })
        }
    }
}

private struct RbBody: View {
    let frame: RbFrame
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(frame.blocks.indices, id: \.self) { i in
                switch frame.blocks[i] {
                case .label(let text): Text(text).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.muted)
                case .chips(let items): RbChipsView(items: items)
                case .bars(let rows): RbBarsView(rows: rows)
                case .cols(let cols): RbColsView(cols: cols)
                case .grid(let grid): RbGridView(grid: grid)
                case .plot(let h, let pts): RbPlotView(height: CGFloat(h), pts: pts)
                case .stats(let rows): RbStatsView(rows: rows)
                }
            }
            if !frame.legend.isEmpty {
                StoryLegendRow(items: frame.legend.map { (color: rbColor($0.0, palette), style: .fill, label: $0.1) })
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 20)
        if !frame.fx.isEmpty { RbFormula(lines: frame.fx).padding(.top, 12) }
        // The captions are plain text; a brace in one is literal, not a colour mark.
        LabStoryNarration(headline: frame.capT.replacingOccurrences(of: "{", with: "\\{"),
                          body: frame.capB.replacingOccurrences(of: "{", with: "\\{")).padding(.top, 16)
    }
}

private struct RbFormula: View {
    let lines: [RbFx]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(lines.indices, id: \.self) { i in
                let l = lines[i]
                (Text(l.a).foregroundColor(palette.muted)
                    + Text(l.b.isEmpty ? "" : " " + l.b).fontWeight(.semibold).foregroundColor(rbColor(l.c, palette)))
                    .font(AppFont.mono(13))
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8).padding(.horizontal, 12)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Colours

private func rbParse(_ s: String) -> (r: Double, g: Double, b: Double, a: Double)? {
    let t = s.trimmingCharacters(in: .whitespaces)
    if t.hasPrefix("#") {
        var h = String(t.dropFirst())
        if h.count == 3 { h = h.map { "\($0)\($0)" }.joined() }
        guard h.count == 6, let v = UInt32(h, radix: 16) else { return nil }
        return (Double((v >> 16) & 0xFF), Double((v >> 8) & 0xFF), Double(v & 0xFF), 1)
    }
    if t.hasPrefix("rgba(") {
        let p = t.dropFirst(5).dropLast().split(separator: ",").map { Double($0.trimmingCharacters(in: .whitespaces)) ?? 0 }
        guard p.count == 4 else { return nil }
        return (p[0], p[1], p[2], p[3])
    }
    return nil
}

/// The design's colour, with its dark-only neutrals mapped onto the palette.
func rbColor(_ s: String, _ palette: Palette) -> Color {
    switch s {
    case "", "none", "transparent": return .clear
    case "#1f232d", "#2a2e39": return SimColors.tint
    case "#14171f": return Color.black.opacity(0.16)
    case "#f2f3f7": return palette.onSurface
    case "#c3c7d1": return palette.onSurface.opacity(0.8)
    case "#9aa0ae": return palette.muted
    case "#6b7180": return palette.muted.opacity(0.7)
    case "#3a3f4c", "#4a4f5c": return Color(hex: 0x6B7180, opacity: 0.55)
    default:
        guard let c = rbParse(s) else { return palette.onSurface }
        return Color(.sRGB, red: c.r / 255, green: c.g / 255, blue: c.b / 255, opacity: c.a)
    }
}

/// Text on a fill: white on a strong fill, the palette's text colour on a pale or neutral one.
private func rbTextOn(_ bg: String, _ color: String, _ palette: Palette) -> Color {
    if color != "#fff" { return rbColor(color, palette) }
    let neutral = ["#1f232d", "#2a2e39", "#14171f", "transparent", ""].contains(bg)
    if neutral { return palette.onSurface }
    if let p = rbParse(bg), p.a < 0.45 { return palette.onSurface }
    return .white
}

/// A ring: `inset 0 0 0 2px #f5c542` or `0 0 0 2px #fff`; nil for none.
private func rbRing(_ ring: String, _ palette: Palette) -> (width: CGFloat, color: Color)? {
    if ring == "none" || ring.isEmpty { return nil }
    let parts = ring.split(separator: " ").map(String.init)
    guard let w = parts.first(where: { $0.hasSuffix("px") && $0 != "0px" }).flatMap({ Double($0.dropLast(2)) }) else { return nil }
    return (CGFloat(w), rbColor(parts.last!, palette))
}

private let rbTrack = Color.black.opacity(0.16)

// MARK: - Blocks

private struct RbChipsView: View {
    let items: [RbTok]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 5, lineSpacing: 5) {
            ForEach(items.indices, id: \.self) { i in
                let t = items[i]
                let fg = rbTextOn(t.bg, t.color, palette)
                VStack(spacing: 1) {
                    Text(t.t).font(AppFont.sans(14, .semibold)).lineLimit(1)
                    if !t.sub.isEmpty { Text(t.sub).font(AppFont.mono(10)).lineLimit(1) }
                }
                .foregroundStyle(fg)
                .padding(.horizontal, 10).padding(.vertical, 4)
                .frame(minHeight: 30)
                .background(rbColor(t.bg, palette), in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    if let r = rbRing(t.ring, palette) { RoundedRectangle(cornerRadius: 8).strokeBorder(r.color, lineWidth: r.width) }
                }
            }
        }
    }
}

private struct RbBarsView: View {
    let rows: [RbRow]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 3) {
            ForEach(rows.indices, id: \.self) { i in
                let r = rows[i]
                HStack(spacing: 0) {
                    Text(r.w).font(AppFont.sans(12, .semibold)).lineLimit(1).truncationMode(.tail)
                        .foregroundStyle(rbColor(r.wc, palette))
                        .frame(width: 84, alignment: .leading)
                    GeometryReader { geo in
                        let w = geo.size.width
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 4).fill(rbTrack).frame(height: 8)
                            if r.mid { Rectangle().fill(palette.muted.opacity(0.5)).frame(width: 1, height: 14).offset(x: w / 2) }
                            if r.width > 0 {
                                RoundedRectangle(cornerRadius: 4).fill(rbColor(r.bar, palette)).frame(width: w * r.width, height: 8).offset(x: w * r.from)
                            }
                        }
                        .frame(height: 14)
                        .frame(maxHeight: .infinity)
                    }
                    .frame(height: 14)
                    .padding(.horizontal, 8)
                    Text(r.v).font(AppFont.mono(12)).foregroundStyle(palette.onSurface.opacity(0.8)).lineLimit(1)
                        .frame(minWidth: 60, alignment: .trailing)
                        .fixedSize()
                }
                .frame(height: 20)
            }
        }
    }
}

private struct RbColsView: View {
    let cols: [RbVCol]
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 6) {
            ForEach(cols.indices, id: \.self) { i in
                let c = cols[i]
                VStack(spacing: 4) {
                    Text(c.v).font(AppFont.mono(11)).foregroundStyle(palette.onSurface.opacity(0.8)).lineLimit(1).frame(height: 14)
                    ZStack(alignment: .bottom) {
                        RoundedRectangle(cornerRadius: 6).fill(rbTrack)
                        HStack(alignment: .bottom, spacing: 3) {
                            ForEach(c.bars.indices, id: \.self) { j in
                                UnevenRoundedRectangle(topLeadingRadius: 3, bottomLeadingRadius: 1, bottomTrailingRadius: 1, topTrailingRadius: 3)
                                    .fill(rbColor(c.bars[j].bg, palette))
                                    .frame(width: 9, height: max(0, min(c.bars[j].h, 62)))
                            }
                        }
                        .padding(.bottom, 2)
                    }
                    .frame(height: 64)
                    Text(c.label).font(AppFont.sans(11, .semibold)).foregroundStyle(rbColor(c.lc, palette)).lineLimit(1).fixedSize()
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

private struct RbGridView: View {
    let grid: RbGrid
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 3) {
            HStack(spacing: 3) {
                ForEach(grid.cols.indices, id: \.self) { j in
                    Text(grid.cols[j]).font(AppFont.sans(10, .semibold)).foregroundStyle(palette.muted).lineLimit(1).frame(maxWidth: .infinity)
                }
            }
            .padding(.leading, 55)
            ForEach(grid.rows.indices, id: \.self) { i in
                let r = grid.rows[i]
                HStack(spacing: 3) {
                    Text(r.label).font(AppFont.sans(11, .semibold)).foregroundStyle(rbColor(r.lc, palette)).lineLimit(1).frame(width: 52, alignment: .leading)
                    ForEach(r.cells.indices, id: \.self) { j in
                        let c = r.cells[j]
                        RoundedRectangle(cornerRadius: 4)
                            .fill(rbColor(c.bg, palette))
                            .overlay {
                                if let ring = rbRing(c.ring, palette) { RoundedRectangle(cornerRadius: 4).strokeBorder(ring.color, lineWidth: ring.width) }
                            }
                            .overlay {
                                if !c.t.isEmpty {
                                    Text(c.t).font(AppFont.mono(10, .semibold)).foregroundStyle(rbTextOn(c.bg, c.color, palette))
                                        .lineLimit(2).multilineTextAlignment(.center).minimumScaleFactor(0.7).padding(.horizontal, 1)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: CGFloat(grid.ch))
                    }
                }
            }
        }
    }
}

private struct RbPlotView: View {
    let height: CGFloat
    let pts: [RbPt]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            for p in pts {
                let c = CGPoint(x: size.width * p.x / 100, y: size.height * p.y / 100)
                let r = p.sz / 2
                let rect = CGRect(x: c.x - r, y: c.y - r, width: p.sz, height: p.sz)
                ctx.fill(Path(ellipseIn: rect), with: .color(rbColor(p.bg, palette)))
                if let ring = rbRing(p.ring, palette) {
                    ctx.stroke(Path(ellipseIn: rect.insetBy(dx: ring.width / 2, dy: ring.width / 2)), with: .color(ring.color), lineWidth: ring.width)
                }
            }
        }
        .frame(height: height)
        .background(rbTrack, in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct RbStatsView: View {
    let rows: [RbStat]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            ForEach(rows.indices, id: \.self) { i in
                let s = rows[i]
                HStack(alignment: .top, spacing: 0) {
                    Text(s.k).font(AppFont.sans(14)).foregroundStyle(palette.muted).frame(width: 130, alignment: .leading).padding(.trailing, 8)
                    Text(s.v).font(AppFont.sans(14, .semibold)).foregroundStyle(rbColor(s.c, palette))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(minHeight: 22)
            }
        }
    }
}
