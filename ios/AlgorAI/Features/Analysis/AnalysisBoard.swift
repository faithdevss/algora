import SwiftUI

// Port of the drawing half of AnalysisBoard.kt: an Analysis topic as an interactive board — a card of
// blocks over the step's arithmetic and a caption, a bottom dock of segmented rows, a slider and run
// buttons, and mark-complete in the nav bar. Frames come from AnalysisBoardLabs.swift.

@MainActor
@Observable
private final class AnModel {
    let lab: AnLab
    var vals: AnVals
    var bench: AnBench?

    init(lab: AnLab) {
        self.lab = lab
        vals = lab.initial
        if lab.bench { runBench() }
    }

    func runBench() {
        bench = nil
        Task { [weak self] in
            let out = await Task.detached(priority: .userInitiated) { runAnBench() }.value
            self?.bench = out
        }
    }

    var frame: AnFrame { lab.build(vals, bench) }
}

struct AnalysisBoardPage: View {
    let topicId: String
    @State private var model: AnModel?

    init(topicId: String) {
        self.topicId = topicId
        _model = State(initialValue: analysisBoardLab(topicId).map { AnModel(lab: $0) })
    }

    var body: some View {
        if let model { AnBoardContent(topicId: topicId, model: model) }
    }
}

private struct AnBoardContent: View {
    let topicId: String
    let model: AnModel
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let frame = model.frame
        VStack(spacing: 0) {
            ZStack {
                Text(frame.title).font(AppFont.sans(17, .semibold)).lineLimit(1).padding(.horizontal, 96)
                HStack(spacing: 0) {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.backward").font(.system(size: 19, weight: .semibold)).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Back")
                    Spacer()
                    DoneToggle(topicId: topicId)
                    let marked = store.bookmarks.contains(topicId)
                    Button { store.toggleBookmark(topicId) } label: {
                        Image(systemName: marked ? "bookmark.fill" : "bookmark").font(.system(size: 19)).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(marked ? "Remove bookmark" : "Bookmark")
                }
                .buttonStyle(.plain)
                .foregroundStyle(palette.primary)
            }
            .padding(.horizontal, 8)
            .frame(height: 44)
            .padding(.top, 4)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(frame.blocks.indices, id: \.self) { i in AnBlockView(block: frame.blocks[i]) { model.vals = $0(model.vals) } }
                        if !frame.legend.isEmpty { AnLegend(items: frame.legend) }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card(radius: 20)
                    if !frame.fx.isEmpty { AnFormula(lines: frame.fx).padding(.top, 8) }
                    LabStoryNarration(headline: frame.capT, body: frame.capB).padding(.top, 12).padding(.horizontal, 4).padding(.bottom, 20)
                }
                .padding(.horizontal, 16)
                .padding(.top, 4)
            }
            AnDockBar(dock: frame.dock, model: model)
        }
        .toolbar(.hidden, for: .tabBar)
    }
}

private let anTrack = Color.black.opacity(0.16)

private struct AnBlockView: View {
    let block: AnBlock
    let onUpdate: ((AnVals) -> AnVals) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        switch block {
        case .label(let t):
            Text(t).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
        case .chips(let items):
            FlowLayout(spacing: 5, lineSpacing: 5) {
                ForEach(items.indices, id: \.self) { i in
                    Text(items[i].t).font(AppFont.mono(14, .semibold)).foregroundStyle(rbColor(items[i].color, palette))
                        .padding(.horizontal, 8).frame(minWidth: 30, minHeight: 30)
                        .background(rbColor(items[i].bg, palette), in: RoundedRectangle(cornerRadius: 8))
                }
            }
        case .strip(let cells, let lo, let hi):
            VStack(spacing: 4) {
                GeometryReader { geo in
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 8).fill(anTrack)
                        ForEach(cells.indices, id: \.self) { i in
                            RoundedRectangle(cornerRadius: 3).fill(rbColor(cells[i].bg, palette))
                                .frame(width: max(geo.size.width * cells[i].w, 3), height: 20)
                                .offset(x: geo.size.width * cells[i].l, y: 4)
                        }
                    }
                }
                .frame(height: 28)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                HStack { Text(lo); Spacer(); Text(hi) }.font(AppFont.mono(11)).foregroundStyle(palette.muted.opacity(0.7))
            }
        case .big(let items):
            HStack(spacing: 8) {
                ForEach(items.indices, id: \.self) { i in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(items[i].v).font(AppFont.grotesk(30, .bold)).foregroundStyle(rbColor(items[i].c, palette)).lineLimit(1).minimumScaleFactor(0.6)
                        Text(items[i].label).font(AppFont.sans(12)).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(anTrack, in: RoundedRectangle(cornerRadius: 12))
                }
            }
        case .bars(let rows, let lw, let vw):
            VStack(spacing: 4) {
                ForEach(rows.indices, id: \.self) { i in
                    let r = rows[i]
                    HStack(spacing: 8) {
                        Text(r.w).font(AppFont.sans(12, .semibold)).foregroundStyle(rbColor(r.wc, palette)).lineLimit(1).frame(width: lw, alignment: .leading)
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 4).fill(anTrack).frame(height: 8)
                                RoundedRectangle(cornerRadius: 4).fill(rbColor(r.bar, palette)).frame(width: max(geo.size.width * r.width, 3), height: 8)
                                if let mk = r.mk {
                                    RoundedRectangle(cornerRadius: 1).fill(SimColors.active).frame(width: 2, height: 16).offset(x: geo.size.width * min(mk, 1) - 1)
                                }
                            }
                            .frame(maxHeight: .infinity)
                        }
                        .frame(height: 16)
                        Text(r.v).font(AppFont.mono(12)).foregroundStyle(palette.onSurface.opacity(0.8)).lineLimit(1).frame(width: vw, alignment: .trailing)
                    }
                    .frame(minHeight: 20)
                }
            }
        case .cols(let cols, let gap, let bw, let axis):
            VStack(spacing: 4) {
                HStack(alignment: .bottom, spacing: gap) {
                    ForEach(cols.indices, id: \.self) { i in
                        let c = cols[i]
                        VStack(spacing: 3) {
                            if !c.v.isEmpty { Text(c.v).font(AppFont.mono(10)).foregroundStyle(palette.onSurface.opacity(0.8)).lineLimit(1).fixedSize() }
                            HStack(alignment: .bottom, spacing: 2) {
                                ForEach(c.bars.indices, id: \.self) { j in
                                    let v = c.bars[j]
                                    UnevenRoundedRectangle(topLeadingRadius: 3, bottomLeadingRadius: 1, bottomTrailingRadius: 1, topTrailingRadius: 3)
                                        .fill(rbColor(v.bg, palette))
                                        .overlay {
                                            if v.ring != "none" {
                                                UnevenRoundedRectangle(topLeadingRadius: 3, bottomLeadingRadius: 1, bottomTrailingRadius: 1, topTrailingRadius: 3)
                                                    .strokeBorder(palette.muted, lineWidth: 1.5)
                                            }
                                        }
                                        .frame(maxWidth: bw ?? .infinity)
                                        .frame(height: v.h)
                                }
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                Rectangle().fill(palette.outline).frame(height: 1)
                HStack(spacing: gap) {
                    ForEach(cols.indices, id: \.self) { i in
                        Text(cols[i].label).font(AppFont.mono(10)).foregroundStyle(palette.muted).lineLimit(1).fixedSize().frame(maxWidth: .infinity)
                    }
                }
                if !axis.isEmpty { Text(axis).font(AppFont.sans(11)).foregroundStyle(palette.muted.opacity(0.7)) }
            }
            .padding(.horizontal, 10).padding(.top, 10).padding(.bottom, 6)
            .background(anTrack, in: RoundedRectangle(cornerRadius: 12))
        case .lines(let h, let w, let paths, let grid):
            ZStack(alignment: .topLeading) {
                Canvas { ctx, size in
                    let sx = size.width / w
                    for g in grid {
                        var p = Path()
                        p.move(to: CGPoint(x: 0, y: g.y)); p.addLine(to: CGPoint(x: size.width, y: g.y))
                        ctx.stroke(p, with: .color(palette.outline), lineWidth: 1)
                    }
                    for path in paths {
                        var p = Path()
                        for (j, pt) in path.pts.enumerated() {
                            let c = CGPoint(x: pt.0 * sx, y: pt.1)
                            if j == 0 { p.move(to: c) } else { p.addLine(to: c) }
                        }
                        ctx.stroke(p, with: .color(rbColor(path.c, palette)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                    }
                }
                ForEach(grid.indices, id: \.self) { i in
                    Text(grid[i].t).font(AppFont.mono(10)).foregroundStyle(palette.muted.opacity(0.7)).offset(x: 6, y: grid[i].y - 13)
                }
            }
            .frame(height: h)
            .background(anTrack)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        case .stats(let rows):
            VStack(alignment: .leading, spacing: 1) {
                ForEach(rows.indices, id: \.self) { i in
                    HStack(alignment: .top, spacing: 8) {
                        Text(rows[i].0).font(AppFont.sans(14)).foregroundStyle(palette.muted).frame(width: 120, alignment: .leading)
                        Text(rows[i].1).font(AppFont.sans(14, .semibold)).foregroundStyle(rbColor(rows[i].2, palette))
                    }
                }
            }
        case .table(let head, let rows):
            VStack(spacing: 2) {
                tableRow(head.map { ($0, palette.muted) }, mono: false, weight: .semibold, size: 11)
                ForEach(rows.indices, id: \.self) { i in
                    let r = rows[i]
                    tableRow([(r.a, palette.onSurface), (r.b, rbColor("#5fd09f", palette)), (r.c, palette.onSurface.opacity(0.8)), (r.d, rbColor(r.dc, palette))], mono: true, weight: .regular, size: 12)
                        .frame(height: 26)
                        .background(SimColors.tint.opacity(0.5), in: RoundedRectangle(cornerRadius: 7))
                }
            }
        case .duel(let items):
            VStack(spacing: 8) {
                ForEach(items.indices, id: \.self) { i in
                    let u = items[i]
                    VStack(alignment: .leading, spacing: 8) {
                        Text(u.name).font(AppFont.grotesk(18, .bold)).foregroundStyle(rbColor(u.c, palette))
                        HStack(alignment: .top, spacing: 8) {
                            duelCol("TIME", u.time, u.tSub)
                            duelCol("SPACE", u.space, u.sSub)
                        }
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(rbColor(u.bg, palette), in: RoundedRectangle(cornerRadius: 14))
                }
            }
        case .steps(let rows):
            VStack(spacing: 0) {
                ForEach(rows.indices, id: \.self) { i in
                    let r = rows[i]
                    HStack(spacing: 8) {
                        Text(r.label).font(AppFont.mono(17, .semibold))
                        Text(r.sub).font(AppFont.sans(14)).foregroundStyle(palette.muted)
                        Spacer()
                        Text(r.v).font(AppFont.mono(17, .semibold)).foregroundStyle(rbColor("#b3abff", palette)).padding(.trailing, 6)
                        HStack(spacing: 0) {
                            stepBtn("−", r.canDec) { onUpdate(r.dec) }
                            Rectangle().fill(palette.muted.opacity(0.3)).frame(width: 1, height: 18)
                            stepBtn("+", r.canInc) { onUpdate(r.inc) }
                        }
                        .frame(width: 94, height: 32)
                        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
                    }
                    .frame(height: 44)
                }
            }
        case .res(let bg, let c, let top, let main, let sub):
            VStack(spacing: 2) {
                Text(top).font(AppFont.mono(12)).foregroundStyle(palette.muted)
                Text(main).font(AppFont.grotesk(30, .bold)).foregroundStyle(rbColor(c, palette))
                Text(sub).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.8)).multilineTextAlignment(.center)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(rbColor(bg, palette), in: RoundedRectangle(cornerRadius: 14))
        case .tiers(let rows):
            VStack(alignment: .leading, spacing: 0) {
                ForEach(rows.indices, id: \.self) { i in
                    let t = rows[i]
                    if i > 0 { Rectangle().fill(palette.outline).frame(height: 0.5) }
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            Circle().fill(rbColor(t.c, palette)).frame(width: 8, height: 8)
                            Text(t.label).font(AppFont.grotesk(17, .bold)).foregroundStyle(rbColor(t.c, palette))
                            Spacer()
                            Text(t.ops).font(AppFont.mono(12)).foregroundStyle(palette.muted)
                            Text(t.time).font(AppFont.mono(12, .semibold)).foregroundStyle(rbColor(t.tc, palette)).frame(minWidth: 86, alignment: .trailing)
                        }
                        FlowLayout(spacing: 5, lineSpacing: 5) {
                            ForEach(t.items, id: \.self) { x in
                                Text(x).font(AppFont.sans(12, .medium)).padding(.horizontal, 8).padding(.vertical, 3)
                                    .background(rbColor(t.bg, palette), in: RoundedRectangle(cornerRadius: 6))
                            }
                        }
                        .padding(.leading, 16)
                    }
                    .padding(.vertical, 9)
                }
            }
        }
    }

    private func tableRow(_ cells: [(String, Color)], mono: Bool, weight: Font.Weight, size: CGFloat) -> some View {
        HStack(spacing: 6) {
            ForEach(cells.indices, id: \.self) { i in
                Text(cells[i].0).font(mono ? AppFont.mono(size, i == 0 ? .semibold : weight) : AppFont.sans(size, weight)).foregroundStyle(cells[i].1)
                    .lineLimit(1)
                    .frame(width: i == 0 ? 52 : i == 3 ? 56 : nil, alignment: i == 0 ? .leading : .trailing)
                    .frame(maxWidth: i == 1 || i == 2 ? .infinity : nil, alignment: .trailing)
            }
        }
        .padding(.horizontal, 8)
    }

    private func duelCol(_ k: String, _ v: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(k).font(AppFont.sans(11, .semibold)).tracking(0.4).foregroundStyle(palette.muted)
            Text(v).font(AppFont.sans(16, .semibold))
            Text(sub).font(AppFont.mono(12)).foregroundStyle(palette.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func stepBtn(_ glyph: String, _ enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(glyph).font(AppFont.sans(20)).foregroundStyle(palette.onSurface.opacity(enabled ? 1 : 0.35)).frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

private struct AnLegend: View {
    let items: [(String, String, String)]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 14, lineSpacing: 6) {
            ForEach(items.indices, id: \.self) { i in
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 3).fill(rbColor(items[i].0, palette))
                        .overlay { if items[i].2 != "none" { RoundedRectangle(cornerRadius: 3).strokeBorder(palette.muted, lineWidth: 1.5) } }
                        .frame(width: 10, height: 10)
                    Text(items[i].1).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.8))
                }
            }
        }
    }
}

private struct AnFormula: View {
    let lines: [AnFx]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(lines.indices, id: \.self) { i in
                let l = lines[i]
                (Text(l.a).foregroundColor(palette.muted) + Text(l.b.isEmpty ? "" : " " + l.b).fontWeight(.semibold).foregroundColor(rbColor(l.c, palette)))
                    .font(AppFont.mono(13)).lineSpacing(3).fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8).padding(.horizontal, 12)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct AnDockBar: View {
    let dock: AnDock
    let model: AnModel
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            ForEach(dock.segs.indices, id: \.self) { i in
                let sg = dock.segs[i]
                HStack(spacing: 2) {
                    ForEach(sg.labels.indices, id: \.self) { j in
                        let on = j == sg.selected
                        Button { model.vals = sg.pick(model.vals, j) } label: {
                            Text(sg.labels[j]).font(AppFont.sans(13, .semibold)).lineLimit(1).minimumScaleFactor(0.8)
                                .foregroundStyle(on ? palette.onSurface : palette.muted)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .background {
                                    if on {
                                        RoundedRectangle(cornerRadius: 7).fill(palette.dark ? Color(hex: 0x636366) : .white)
                                            .shadow(color: .black.opacity(0.2), radius: 3, y: 1)
                                    }
                                }
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(2)
                .frame(height: 32)
                .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            }
            if let sl = dock.slider {
                VStack(spacing: 2) {
                    HStack(alignment: .lastTextBaseline) {
                        Text(sl.name).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.muted)
                        Spacer()
                        Text(sl.labels[model.vals.ni]).font(AppFont.mono(14, .semibold)).foregroundStyle(palette.primary)
                    }
                    Slider(value: Binding(get: { Double(model.vals.ni) }, set: { model.vals.ni = Int($0.rounded()) }),
                           in: 0...Double(max(sl.labels.count - 1, 1)), step: 1)
                        .tint(palette.primary)
                }
            }
            if let btn = dock.btn {
                HStack(spacing: 10) {
                    Button {
                        if btn.bench { model.runBench() } else { model.vals = btn.update(model.vals) }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "play.fill").font(.system(size: 14))
                            Text(btn.label).font(AppFont.sans(17, .semibold))
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .background(palette.primary, in: RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                    if let alt = dock.alt {
                        Button { model.vals = alt.update(model.vals) } label: {
                            Text(alt.label).font(AppFont.sans(17, .semibold)).foregroundStyle(palette.primary)
                                .padding(.horizontal, 20).frame(minHeight: 50)
                                .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 10)
        .background(.bar)
        .overlay(alignment: .top) { Rectangle().fill(palette.outline).frame(height: 0.5) }
    }
}
