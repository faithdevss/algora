import SwiftUI

// Port of GenStoryStages.kt: the nine stages the GAN, diffusion, VAE, CycleGAN, DCGAN, Stable Diffusion,
// DeepFakes, StyleGAN and style-transfer storyboards draw inside the deep-learning card
// (DeepStoryLabs.swift). Frames are built in GenStoryFrames.swift.

/// Real and generated histograms over x in −4...4 (bin probabilities) with the discriminator D(x) on 0...1.
struct DkGan { let real: [Double]; let fake: [Double]; let d: [DkP] }

/// One timestep in the strip under the diffusion plot; `ring` draws the clean data in green.
struct DkThumb { let t: String; let abar: String; let pts: [DkP]; let ring: Bool }

/// The ring of data and the current sample on one plot, with a strip of timesteps under it. `links` joins
/// each data point to where its noise moved it; a `faint` ring is the manifold the reverse process aims for.
struct DkDiffusion {
    let ring: [DkP]
    let pts: [DkP]
    let links: Bool
    let faint: Bool
    let thumbs: [DkThumb]
    let current: Int
    var range: Double = 2.2
}

/// One latent unit: its posterior mean and spread, and the sample z drawn from it (nil: none yet).
struct DkLatent { let mu: Double; let sigma: Double; let z: Double? }

/// Inputs, one row per latent unit against the N(0,1) prior, outputs, and KL per unit. A nil `x` is a prior sample.
struct DkVae { let x: [Double]?; let latents: [DkLatent]; let xHat: [Double]; let kl: [Double]; let klCaption: String }

struct DkCycleStat { let key: String; let value: String; var ink: DkInk? = nil }

/// One mapping G : A → B as six links (`perm` of b for each a; nil draws only the domains).
struct DkCyclePanel {
    let title: String
    let titleInk: DkInk
    let perm: [Int]?
    let ink: DkInk
    let dashed: Bool
    /// The inverse F drawn back from B to A, dashed violet under the forward links.
    var inverse = false
    var stats: [DkCycleStat] = []
}

struct DkCycle { let panels: [DkCyclePanel] }

/// Writes per output position for one kernel/stride pair; the first and last `border` positions are greyed.
struct DkCovRow {
    let label: String
    let note: String
    let noteInk: DkInk
    let counts: [Int]
    let border: Int
    let uniform: Bool
    let selected: Bool
}

struct DkCoverage { let rows: [DkCovRow]; let maxCount: Int }

/// A quantity in pixel space against latent space: two log-scale bars and their values.
struct DkShrinkRow {
    let name: String
    let formula: String
    let a: String
    let aFrac: Double
    let b: String
    let bFrac: Double
    var hot = false
}

struct DkShrinkTile { let value: String; let caption: String; var hot = false }

struct DkShrink { let left: String; let right: String; let rows: [DkShrinkRow]; let tiles: [DkShrinkTile] }

/// The face-swap autoencoder: x_A and x_B into one encoder (or two, when not `shared`), a code z, and a
/// decoder per identity. A path not lit is drawn dim; `swap` draws A's code into B's decoder.
struct DkFakeArch { let shared: Bool; let trainA: Bool; let trainB: Bool; let swap: Bool }

/// Points and a path on the unit square (y up), with a caption under the panel.
struct DkWarpPanel {
    let caption: String
    let pts: [DkP]
    var path: [DkP]? = nil
    var pathInk: DkInk = .green
}

struct DkWarp { let left: DkWarpPanel; let right: DkWarpPanel }

/// A labelled heatmap; `values` shows each cell's number (a Gram matrix), `track` rings a column yellow.
struct DkHeat {
    let title: String
    let m: [[Double]]
    let ink: DkInk
    let values: Bool
    var track: Int? = nil
    var hotCell: (Int, Int)? = nil
    var hotRows: (Int, Int)? = nil
}

/// Feature maps over their Gram matrices, in one or two columns.
struct DkGram { let columns: [[DkHeat]] }

// MARK: - Shared drawing

private let genTick = Color(hex: 0x8FB6FF)
private let genRule = Color(hex: 0x252A36)
private let genBorder = Color(hex: 0x3A3F4C)
private let genBox = Color(hex: 0x2A2E39)
private let genLilac = Color(hex: 0xB3ABFF)
private let genViolet = Color(hex: 0x8B5CF6)
private let genPink = Color(hex: 0xEC4899)

private func line(_ a: CGPoint, _ b: CGPoint) -> Path { var p = Path(); p.move(to: a); p.addLine(to: b); return p }

private func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private func polyline(_ pts: [CGPoint]) -> Path {
    var p = Path()
    for (i, o) in pts.enumerated() { if i == 0 { p.move(to: o) } else { p.addLine(to: o) } }
    return p
}

private extension GraphicsContext {
    func label(_ text: String, _ font: Font, _ color: Color, _ at: CGPoint, _ anchor: UnitPoint = .center) {
        draw(Text(text).font(font).foregroundStyle(color), at: at, anchor: anchor)
    }
}

// MARK: - GAN

struct DkGanView: View {
    let stage: DkGan
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let left: CGFloat = 28, right = size.width - 8, top: CGFloat = 14, bottom = size.height - 30
            func px(_ x: Double) -> CGFloat { left + CGFloat((x + 4) / 8) * (right - left) }
            func py(_ y: Double) -> CGFloat { bottom - CGFloat(y) * (bottom - top) }
            for (v, s) in [(0.0, "0"), (0.5, ".5"), (1.0, "1")] {
                ctx.stroke(line(CGPoint(x: left, y: py(v)), CGPoint(x: right, y: py(v))), with: .color(genRule), style: StrokeStyle(lineWidth: 1, dash: v == 0.5 ? [4, 4] : []))
                ctx.label(s, AppFont.mono(9), genTick, CGPoint(x: left - 4, y: py(v)), .trailing)
            }
            let peak = max((stage.real + stage.fake).max() ?? 1, 1e-9)
            let bin = (right - left) / CGFloat(stage.real.count)
            for i in stage.real.indices {
                for (k, (p, ink)) in [(stage.real[i], DkInk.green), (stage.fake[i], DkInk.orange)].enumerated() {
                    let h = min(CGFloat(p / peak), 1) * (bottom - top) * 0.9
                    if h > 0.2 {
                        let rect = CGRect(x: left + bin * CGFloat(i) + bin * (0.08 + 0.46 * CGFloat(k)), y: bottom - h, width: bin * 0.4, height: h)
                        ctx.fill(Path(roundedRect: rect, cornerRadius: 1.5), with: .color(dkColor(ink)))
                    }
                }
            }
            ctx.stroke(polyline(stage.d.map { CGPoint(x: px($0.x), y: py($0.y)) }), with: .color(dkColor(.blue)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
            for x in [-4, -2, 0, 2, 4] {
                ctx.label(x < 0 ? "−\(-x)" : "\(x)", AppFont.mono(9), palette.muted, CGPoint(x: px(Double(x)), y: bottom + 10))
            }
            ctx.label("D(x)", AppFont.mono(9), genTick, CGPoint(x: left + 2, y: top + 10), .leading)
            ctx.label("x", AppFont.mono(9), palette.muted, CGPoint(x: right, y: bottom + 22), .trailing)
        }
        .frame(height: 190)
        .modifier(DkStageShape())
    }
}

// MARK: - Diffusion

struct DkDiffusionView: View {
    let stage: DkDiffusion
    @Environment(\.palette) private var palette

    var body: some View {
        let green = dkColor(.green), cyan = dkColor(.cyan)
        VStack(spacing: 10) {
            Canvas { ctx, size in
                let scale = (size.height / 2 - 8) / CGFloat(stage.range)
                func at(_ p: DkP) -> CGPoint { CGPoint(x: size.width / 2 + CGFloat(p.x) * scale, y: size.height / 2 - CGFloat(p.y) * scale) }
                if stage.links {
                    for i in stage.ring.indices { ctx.stroke(line(at(stage.ring[i]), at(stage.pts[i])), with: .color(Color(hex: 0x6B7180).opacity(0.6)), lineWidth: 1) }
                }
                for p in stage.ring { ctx.fill(circle(at(p), 2.6), with: .color(green.opacity(stage.faint ? 0.35 : 1))) }
                for p in stage.pts { ctx.fill(circle(at(p), 4), with: .color(cyan)) }
            }
            .frame(height: 200)
            .modifier(DkStageShape())
            HStack(alignment: .top, spacing: 6) {
                ForEach(stage.thumbs.indices, id: \.self) { i in
                    let thumb = stage.thumbs[i], on = i == stage.current
                    VStack(spacing: 0) {
                        Canvas { ctx, size in
                            let scale = (min(size.width, size.height) / 2 - 4) / CGFloat(stage.range)
                            for p in thumb.pts {
                                let c = CGPoint(x: size.width / 2 + CGFloat(p.x) * scale, y: size.height / 2 - CGFloat(p.y) * scale)
                                ctx.fill(circle(c, 1.7), with: .color(thumb.ring ? green : cyan))
                            }
                        }
                        .aspectRatio(1, contentMode: .fit)
                        .modifier(DkStageShape())
                        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(on ? genLilac : .clear, lineWidth: 1.5))
                        Text(thumb.t).font(AppFont.mono(11, on ? .bold : .regular)).foregroundStyle(on ? genLilac : palette.muted).lineLimit(1).padding(.top, 5)
                        Text(thumb.abar).font(AppFont.mono(9.5)).foregroundStyle(palette.muted.opacity(0.8)).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }
}

// MARK: - VAE

private let genSub = ["₁", "₂", "₃", "₄", "₅", "₆"]

struct DkVaeView: View {
    let stage: DkVae
    @Environment(\.palette) private var palette

    var body: some View {
        let blue = dkColor(.blue), yellow = SimColors.active
        VStack(alignment: .leading, spacing: 0) {
            VStack(spacing: 4) {
                HStack(spacing: 0) {
                    Text("x").frame(width: 44)
                    Text("q(z | x) per latent dimension").frame(maxWidth: .infinity)
                    Text("x̂").frame(width: 44)
                }
                .font(AppFont.mono(10)).foregroundStyle(palette.muted)
                HStack(spacing: 0) {
                    VaeTiles(values: stage.x.map { $0.map { Optional($0) } } ?? Array(repeating: nil, count: stage.xHat.count))
                    Canvas { ctx, size in
                        let axisY = size.height - 6
                        let rowH = (axisY - 14) / CGFloat(stage.latents.count)
                        func px(_ v: Double) -> CGFloat { CGFloat((min(max(v, -3), 3) + 3) / 6) * size.width }
                        for (j, l) in stage.latents.enumerated() {
                            let cy = rowH * CGFloat(j) + rowH * 0.62
                            ctx.label("z\(genSub[j])", AppFont.mono(9.5), palette.muted, CGPoint(x: 0, y: cy - 13), .leading)
                            ctx.label("μ \(dkNum(l.mu, 2)) σ \(dkNum(l.sigma, 2))", AppFont.mono(9.5), palette.onSurface.opacity(0.8), CGPoint(x: size.width, y: cy - 13), .trailing)
                            ctx.stroke(line(CGPoint(x: 0, y: cy), CGPoint(x: size.width, y: cy)), with: .color(palette.muted.opacity(0.25)), lineWidth: 1)
                            ctx.fill(Path(roundedRect: CGRect(x: px(-1), y: cy - 7, width: px(1) - px(-1), height: 14), cornerRadius: 3), with: .color(Color(hex: 0x6B7180).opacity(0.28)))
                            ctx.fill(Path(roundedRect: CGRect(x: px(l.mu - l.sigma), y: cy - 5, width: max(px(l.mu + l.sigma) - px(l.mu - l.sigma), 2), height: 10), cornerRadius: 3), with: .color(blue.opacity(0.75)))
                            ctx.stroke(line(CGPoint(x: px(l.mu), y: cy - 7), CGPoint(x: px(l.mu), y: cy + 7)), with: .color(.white), lineWidth: 2)
                            if let z = l.z { ctx.fill(circle(CGPoint(x: px(z), y: cy), 4.5), with: .color(yellow)) }
                        }
                        for (k, (v, s)) in [(-3.0, "−3"), (0.0, "0"), (3.0, "3")].enumerated() {
                            ctx.label(s, AppFont.mono(9), palette.muted, CGPoint(x: px(v), y: axisY), k == 0 ? .leading : k == 2 ? .trailing : .center)
                        }
                    }
                    .frame(height: 196)
                    .padding(.horizontal, 8)
                    VaeTiles(values: stage.xHat.map { Optional($0) })
                }
            }
            .padding(8)
            .modifier(DkStageShape())
            StoryLegendRow(items: [
                (color: blue, style: .fill, label: "Posterior μ ± σ"),
                (color: Color(hex: 0x6B7180), style: .fill, label: "Prior N(0,1) ± 1"),
                (color: yellow, style: .dot, label: "Sample z = μ + σε"),
            ]).padding(.top, 12)
            Text(stage.klCaption).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.muted).padding(.top, 14).padding(.bottom, 6)
            let top = max(stage.kl.max() ?? 0.5, 0.5)
            ForEach(stage.kl.indices, id: \.self) { j in
                HStack(spacing: 0) {
                    Text("z\(genSub[j])").font(AppFont.mono(11)).foregroundStyle(palette.muted).frame(width: 30, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 3).fill(SimColors.tint)
                            RoundedRectangle(cornerRadius: 3).fill(genViolet).frame(width: geo.size.width * CGFloat(min(max(stage.kl[j] / top, 0.01), 1)))
                        }
                    }
                    .frame(height: 6)
                    Text(dkNum(stage.kl[j], 2)).font(AppFont.mono(12)).foregroundStyle(palette.onSurface.opacity(0.85)).frame(width: 52, alignment: .trailing)
                }
                .frame(height: 21)
            }
        }
    }
}

private struct VaeTiles: View {
    let values: [Double?]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            ForEach(values.indices, id: \.self) { i in
                let v = values[i]
                ZStack {
                    if let v {
                        RoundedRectangle(cornerRadius: 6).fill(dkColor(.blue).opacity(0.22 + 0.7 * min(max(abs(v) / 2, 0), 1)))
                    } else {
                        RoundedRectangle(cornerRadius: 6).strokeBorder(palette.muted.opacity(0.45), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                    }
                    Text(v.map { dkNum($0, 2) } ?? "—").font(AppFont.mono(11.5, .bold)).foregroundStyle(v == nil ? palette.muted : .white).lineLimit(1)
                }
                .frame(height: 27)
            }
        }
        .frame(width: 44)
    }
}

// MARK: - CycleGAN

struct DkCycleView: View {
    let stage: DkCycle
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            Canvas { ctx, size in
                let n = 6
                let w = size.width / CGFloat(stage.panels.count)
                func rowY(_ i: Int) -> CGFloat { 18 + (size.height - 36) * CGFloat(i) / CGFloat(n - 1) }
                for (k, panel) in stage.panels.enumerated() {
                    let x0 = w * CGFloat(k), ax = x0 + 32, bx = x0 + w - 30
                    if k > 0 { ctx.stroke(line(CGPoint(x: x0, y: 12), CGPoint(x: x0, y: size.height - 12)), with: .color(palette.muted.opacity(0.3)), lineWidth: 1) }
                    if let perm = panel.perm {
                        if panel.inverse {
                            for (a, b) in perm.enumerated() {
                                ctx.stroke(line(CGPoint(x: bx, y: rowY(b) + 4), CGPoint(x: ax, y: rowY(a) + 4)), with: .color(dkColor(.violet)), style: StrokeStyle(lineWidth: 1.5, dash: [2, 3]))
                            }
                        }
                        for (a, b) in perm.enumerated() {
                            ctx.stroke(line(CGPoint(x: ax, y: rowY(a)), CGPoint(x: bx, y: rowY(b))), with: .color(dkColor(panel.ink)), style: StrokeStyle(lineWidth: 2, dash: panel.dashed ? [4, 3] : []))
                        }
                    }
                    for i in 0..<n {
                        ctx.fill(circle(CGPoint(x: ax, y: rowY(i)), 5), with: .color(dkColor(.cyan)))
                        ctx.fill(circle(CGPoint(x: bx, y: rowY(i)), 5), with: .color(dkColor(.orange)))
                        ctx.label("a\(i + 1)", AppFont.mono(10), palette.onSurface.opacity(0.75), CGPoint(x: ax - 10, y: rowY(i)), .trailing)
                        ctx.label("b\(i + 1)", AppFont.mono(10), palette.onSurface.opacity(0.75), CGPoint(x: bx + 10, y: rowY(i)), .leading)
                    }
                }
            }
            .frame(height: 172)
            .modifier(DkStageShape())
            if stage.panels.contains(where: { !$0.stats.isEmpty }) {
                HStack(alignment: .top, spacing: 8) {
                    ForEach(stage.panels.indices, id: \.self) { k in
                        let panel = stage.panels[k]
                        VStack(alignment: .leading, spacing: 3) {
                            Text(panel.title).font(AppFont.sans(14, .bold)).foregroundStyle(dkColor(panel.titleInk)).lineLimit(1)
                            ForEach(panel.stats.indices, id: \.self) { i in
                                let s = panel.stats[i]
                                HStack {
                                    Text(s.key).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1)
                                    Spacer(minLength: 4)
                                    Text(s.value).font(AppFont.mono(13, .bold)).foregroundStyle(s.ink.map { dkColor($0) } ?? palette.onSurface).lineLimit(1)
                                }
                            }
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                        .modifier(DkStageShape())
                    }
                }
            }
        }
    }
}

// MARK: - DCGAN coverage

struct DkCoverageView: View {
    let stage: DkCoverage
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 4) {
            ForEach(stage.rows.indices, id: \.self) { r in
                let row = stage.rows[r]
                VStack(spacing: 4) {
                    HStack {
                        Text(row.label).font(AppFont.mono(10.5, row.selected ? .bold : .regular)).foregroundStyle(row.selected ? palette.onSurface : palette.muted).lineLimit(1)
                        Spacer(minLength: 4)
                        Text(row.note).font(AppFont.mono(10)).foregroundStyle(dkColor(row.noteInk)).lineLimit(1)
                    }
                    Canvas { ctx, size in
                        let n = row.counts.count
                        let slot = size.width / CGFloat(n)
                        let barBottom = size.height - 12
                        for (i, c) in row.counts.enumerated() {
                            let border = i < row.border || i >= n - row.border
                            let base = border ? genBorder : row.uniform ? dkColor(.green) : dkColor(.orange)
                            let h = barBottom * CGFloat(c) / CGFloat(stage.maxCount)
                            ctx.fill(Path(roundedRect: CGRect(x: slot * CGFloat(i) + slot * 0.12, y: barBottom - h, width: slot * 0.76, height: h), cornerRadius: 1.5),
                                     with: .color(base.opacity(row.selected || border ? 1 : 0.6)))
                            ctx.label("\(c)", AppFont.mono(8.5), palette.muted, CGPoint(x: slot * CGFloat(i) + slot / 2, y: size.height - 4))
                        }
                    }
                    .frame(height: 40)
                }
                .padding(.horizontal, 6).padding(.vertical, 5)
                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(row.selected ? genLilac : .clear, lineWidth: 1.5))
            }
        }
        .padding(6)
        .modifier(DkStageShape())
    }
}

// MARK: - Stable Diffusion shrink

struct DkShrinkView: View {
    let stage: DkShrink
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Text(stage.left).foregroundStyle(genTick).frame(maxWidth: .infinity, alignment: .leading)
                Text(stage.right).foregroundStyle(Color(hex: 0xF47AA8)).frame(maxWidth: .infinity, alignment: .leading)
            }
            .font(AppFont.mono(12, .semibold))
            ForEach(stage.rows.indices, id: \.self) { i in
                let row = stage.rows[i]
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(row.name).font(AppFont.sans(14, .semibold)).foregroundStyle(row.hot ? SimColors.active : palette.onSurface).lineLimit(1)
                        Spacer(minLength: 6)
                        Text(row.formula).font(AppFont.mono(11)).foregroundStyle(palette.muted).lineLimit(1)
                    }
                    HStack(spacing: 10) {
                        ShrinkBar(frac: row.aFrac, value: row.a, ink: dkColor(.blue))
                        ShrinkBar(frac: row.bFrac, value: row.b, ink: genPink)
                    }
                }
            }
            if !stage.tiles.isEmpty {
                HStack(spacing: 8) {
                    ForEach(stage.tiles.indices, id: \.self) { i in
                        let tile = stage.tiles[i]
                        VStack(alignment: .leading, spacing: 2) {
                            Text(tile.value).font(AppFont.sans(21, .bold)).foregroundStyle(tile.hot ? SimColors.active : palette.onSurface).lineLimit(1).minimumScaleFactor(0.7)
                            Text(tile.caption).font(AppFont.sans(12)).foregroundStyle(palette.muted).lineLimit(3)
                            Spacer(minLength: 0)
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, minHeight: 84, maxHeight: 84, alignment: .topLeading)
                        .background(tile.hot ? SimColors.active.opacity(0.14) : Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(.top, 2)
            }
        }
    }
}

private struct ShrinkBar: View {
    let frac: Double
    let value: String
    let ink: Color
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .trailing, spacing: 3) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3).fill(SimColors.tint)
                    RoundedRectangle(cornerRadius: 3).fill(ink).frame(width: geo.size.width * CGFloat(min(max(frac, 0.012), 1)))
                }
            }
            .frame(height: 6)
            Text(value).font(AppFont.mono(11)).foregroundStyle(palette.onSurface.opacity(0.8)).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - DeepFakes architecture

struct DkFakeArchView: View {
    let stage: DkFakeArch
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            // Laid out on the design's 329 × 230 board and scaled to the width.
            let s = size.width / 329, v = size.height / 230
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * v) }
            let onSurface = palette.onSurface
            func box(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ fill: Color, _ title: String, _ sub: String?) {
                ctx.fill(Path(roundedRect: CGRect(x: x * s, y: y * v, width: w * s, height: h * v), cornerRadius: 8), with: .color(fill))
                ctx.label(title, AppFont.mono(11, .bold), onSurface, p(x + w / 2, y + h / 2 - (sub != nil ? 5 : 0)))
                if let sub { ctx.label(sub, AppFont.mono(8), onSurface.opacity(0.7), p(x + w / 2, y + h / 2 + 8)) }
            }
            func wire(_ pts: [CGPoint], _ color: Color, dashed: Bool = false) {
                ctx.stroke(polyline(pts), with: .color(color), style: StrokeStyle(lineWidth: 2, lineJoin: .round, dash: dashed ? [4, 3] : []))
            }
            func trapezoid(_ x: CGFloat, _ y0: CGFloat, _ y1: CGFloat, _ fill: Color, _ title: String, _ sub: String) {
                // Wide on the input side, narrowing toward the code.
                var path = Path()
                path.move(to: p(x, y0)); path.addLine(to: p(x + 48, y0 + 18)); path.addLine(to: p(x + 48, y1 - 18)); path.addLine(to: p(x, y1)); path.closeSubpath()
                ctx.fill(path, with: .color(fill))
                ctx.label(title, AppFont.mono(11, .bold), onSurface, p(x + 24, (y0 + y1) / 2 - 5))
                ctx.label(sub, AppFont.mono(8), onSurface.opacity(0.75), p(x + 24, (y0 + y1) / 2 + 8))
            }
            let blue = dkColor(.blue), yellow = SimColors.active
            let aInk = blue.opacity(stage.trainA ? 1 : 0.25)
            let bInk = genViolet.opacity(stage.trainB ? 1 : 0.25)
            if stage.shared {
                wire([p(64, 44), p(86, 44), p(86, 100), p(110, 100)], aInk)
                wire([p(64, 183), p(86, 183), p(86, 130), p(110, 130)], bInk)
                wire([p(158, 115), p(178, 115)], stage.trainA ? aInk : bInk)
                wire([p(206, 109), p(222, 109), p(222, 45), p(236, 45)], aInk)
                wire([p(206, 121), p(214, 121), p(214, 179), p(236, 179)], bInk)
                trapezoid(110, 82, 148, blue.opacity(0.6), "E", "shared")
                ctx.label("one trunk", AppFont.mono(8), palette.muted, p(134, 162))
                box(178, 101, 28, 28, genBox, "z", nil)
                if stage.swap {
                    wire([p(206, 124), p(230, 124), p(230, 172), p(236, 172)], yellow, dashed: true)
                    ctx.label("swap", AppFont.mono(8.5, .bold), yellow, p(250, 148))
                }
            } else {
                wire([p(64, 44), p(110, 44)], aInk)
                wire([p(64, 183), p(110, 183)], bInk)
                trapezoid(110, 14, 76, blue.opacity(0.6), "E_A", "own")
                trapezoid(110, 152, 214, genViolet.opacity(0.6), "E_B", "own")
                wire([p(158, 45), p(178, 45)], aInk)
                wire([p(158, 183), p(178, 183)], bInk)
                box(178, 31, 28, 28, genBox, "z", nil)
                box(178, 169, 28, 28, genBox, "z", nil)
                wire([p(206, 45), p(236, 45)], aInk)
                wire([p(206, 183), p(236, 179)], bInk)
                if stage.swap {
                    wire([p(192, 59), p(192, 110), p(226, 110), p(226, 172), p(236, 172)], yellow, dashed: true)
                    ctx.label("swap", AppFont.mono(8.5, .bold), yellow, p(214, 100))
                }
            }
            box(8, 26, 56, 36, blue.opacity(0.3), "x_A", "face A")
            box(8, 165, 56, 36, genViolet.opacity(0.3), "x_B", "face B")
            box(236, 28, 50, 34, genBox, "D_A", nil)
            box(236, 162, 50, 34, genBox, "D_B", nil)
            wire([p(286, 45), p(296, 45)], aInk)
            wire([p(286, 179), p(296, 179)], bInk)
            box(296, 31, 26, 28, genBox, "x̂", nil)
            box(296, 165, 26, 28, genBox, "x̂", nil)
            ctx.label("trained on A only", AppFont.mono(8), palette.muted, p(279, 75))
            ctx.label("trained on B only", AppFont.mono(8), palette.muted, p(279, 209))
        }
        .frame(height: 230)
        .modifier(DkStageShape())
    }
}

// MARK: - StyleGAN warp

struct DkWarpView: View {
    let stage: DkWarp
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let gap: CGFloat = 8
            let pw = (size.width - gap * 3) / 2, ph = size.height - 30
            for (k, panel) in [stage.left, stage.right].enumerated() {
                let x0 = gap + CGFloat(k) * (pw + gap), y0 = gap
                ctx.fill(Path(roundedRect: CGRect(x: x0, y: y0, width: pw, height: ph), cornerRadius: 8), with: .color(genBox.opacity(0.6)))
                // The unit square sits inside the panel with room for points the warp pushes past its edge.
                func at(_ p: DkP) -> CGPoint { CGPoint(x: x0 + pw * (0.16 + 0.68 * CGFloat(p.x)), y: y0 + ph * (0.84 - 0.68 * CGFloat(p.y))) }
                for p in panel.pts { ctx.fill(circle(at(p), 2.1), with: .color(dkColor(.cyan))) }
                if let path = panel.path {
                    ctx.stroke(polyline(path.map(at)), with: .color(dkColor(panel.pathInk)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                }
                ctx.label(panel.caption, AppFont.mono(9), palette.muted, CGPoint(x: x0 + pw / 2, y: y0 + ph + 11))
            }
        }
        .frame(height: 180)
        .modifier(DkStageShape())
    }
}

// MARK: - Style transfer Gram

struct DkGramView: View {
    let stage: DkGram

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            ForEach(stage.columns.indices, id: \.self) { c in
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(stage.columns[c].indices, id: \.self) { DkHeatView(heat: stage.columns[c][$0]) }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

private struct DkHeatView: View {
    let heat: DkHeat
    @Environment(\.palette) private var palette

    var body: some View {
        let base = heat.ink == .violet ? genViolet : dkColor(heat.ink)
        let top = max(heat.m.flatMap { $0 }.max() ?? 1, 1e-9)
        let rows = heat.m.count, cols = heat.m[0].count
        VStack(alignment: .leading, spacing: 6) {
            Text(heat.title).font(AppFont.sans(12, .semibold)).foregroundStyle(palette.muted).lineLimit(1)
            Canvas { ctx, size in
                let gap: CGFloat = 2
                let cw = (size.width - gap * CGFloat(cols - 1)) / CGFloat(cols)
                let ch = (size.height - gap * CGFloat(rows - 1)) / CGFloat(rows)
                for r in 0..<rows {
                    for c in 0..<cols {
                        let v = heat.m[r][c]
                        let rect = CGRect(x: CGFloat(c) * (cw + gap), y: CGFloat(r) * (ch + gap), width: cw, height: ch)
                        ctx.fill(Path(roundedRect: rect, cornerRadius: 2.5), with: .color(base.opacity(min(max(0.12 + 0.8 * v / top, 0.1), 0.95))))
                        if heat.values {
                            var s = dkNum(v, 2)
                            if s.hasPrefix("0") { s.removeFirst() }
                            ctx.label(s, AppFont.mono(8.5, .bold), Color(hex: 0xF2F3F7), CGPoint(x: rect.midX, y: rect.midY))
                        }
                        let inRows = heat.hotRows.map { r == $0.0 || r == $0.1 } ?? false
                        let hot = heat.hotCell.map { $0.0 == r && $0.1 == c } ?? false
                        if heat.track == c || hot || inRows {
                            ctx.stroke(Path(roundedRect: rect.insetBy(dx: 0.75, dy: 0.75), cornerRadius: 2.5), with: .color(SimColors.active), lineWidth: 1.5)
                        }
                    }
                }
            }
            .aspectRatio(CGFloat(cols) / CGFloat(rows) * (heat.values ? 1.25 : 1), contentMode: .fit)
        }
    }
}
