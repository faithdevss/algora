import SwiftUI

// Port of DeepStoryLabs.kt: the biological neuron, feedforward networks, vanishing and exploding
// gradients and the ten activation functions as step-by-step storyboards. A card with a titled stage (a
// plot, the softmax bars or a small network) over a legend, the step's arithmetic in a formula box,
// chips and a headline. The controls are a step track with a labelled action, a step track over an
// "input z" stepper and Next, or a segmented picker over back and Next. Frames: DeepStoryFrames.swift.

let deepStoryTopicIds: Set<String> = Set<String>([
    "biological_neuron", "neural_network_basics", "vanishing_gradient", "exploding_gradient",
    "sigmoid", "tanh", "relu", "leaky_relu", "prelu", "elu", "selu", "swish", "gelu", "softmax",
] as [String]).union(cnnStoryTopicIds).union(detectStoryTopicIds).union(rnnStoryTopicIds).union(transformerStoryTopicIds).union(modernStoryTopicIds).union(optimStoryTopicIds)

enum DkInk { case blue, pink, green, orange, grey, yellow, violet, slate, sky }

struct DkP { let x: Double; let y: Double; init(_ x: Double, _ y: Double) { self.x = x; self.y = y } }

struct DkLine {
    let pts: [DkP]
    let ink: DkInk
    var dashed = false
    var dots = false
}

struct DkDot {
    let p: DkP
    var ink: DkInk = .yellow
    var r: CGFloat = 5
}

/// A horizontal rule across the plot (a threshold, a target).
struct DkRule {
    let y: Double
    let ink: DkInk
    var thin = false
}

/// A bracket over [x0, x1] at height y, labelled under its left end ("19.9 ms").
struct DkBracket { let x0: Double; let x1: Double; let y: Double; let label: String }

struct DkPlot {
    let xr: (Double, Double)
    let yr: (Double, Double)
    let yTicks: [(Double, String)]
    let xLeft: String
    let xRight: String
    let lines: [DkLine]
    var dots: [DkDot] = []
    var rules: [DkRule] = []
    /// The vertical axis at x = 0, when 0 is on the plot.
    var axis = true
    /// A dashed yellow line at x, labelled under the plot ("z = 3").
    var guide: (Double, String)? = nil
    var bracket: DkBracket? = nil
    /// Data points, small and grey.
    var scatter: [DkP] = []
    var height: CGFloat = 200
    /// Bars rising from the bottom of the range (a per-timestep gradient).
    var bars: [DkPBar] = []
    /// Labels under given x positions in place of the two end labels; a hot one is yellow ("t1").
    var xTicks: [DkTick] = []
    /// Loss contours: ellipses around the origin with these x and y half-widths.
    var ellipses: [(Double, Double)] = []
}

struct DkPBar { let x: Double; let y: Double; let ink: DkInk; var width: Double = 0.62 }

struct DkTick { let x: Double; let label: String; var hot = false }

/// Softmax: the logits on a zero line above, a second row of bars (e^z or p) below; `top` is yellow.
struct DkBars {
    let logits: [Double]
    let lower: [Double]
    let lowerLabel: String
    let lowerDigits: Int
    let labels: [String]
    let top: Int?
}

enum DkTone { case input, hidden, off, current, ghost, output }

struct DkNode { let x: CGFloat; let y: CGFloat; let text: String; let tone: DkTone; var note: String? = nil }

enum DkEdgeState { case faint, lit, silenced }

struct DkEdge { let from: Int; let to: Int; let w: Double; let state: DkEdgeState; var label: String? = nil }

struct DkNet { let nodes: [DkNode]; let edges: [DkEdge]; let footers: [(CGFloat, String)] }

enum DkStage { case plot(DkPlot), bars(DkBars), net(DkNet), grids(DkGrids), rows(DkRows), segs(DkSegs), boxes(DkBoxes), unet(DkUNet), tiles(DkTiles), pipeline(DkPipeline), hist(DkHist), tokens(DkTokens), graph(DkGraph) }

struct DkLegend { let ink: DkInk; let style: SwatchStyle; let label: String }

struct DkChip { let key: String; let value: String; var tint = false }

struct DkFrame {
    let header: String?
    let stage: DkStage
    let legend: [DkLegend]
    let formula: [String]
    let headline: String
    let body: String
    var chips: [DkChip] = []
    var action = "Next"
}

/// stepperOnly: the stepper beside Next with no step track; Next walks the steps and wraps.
enum DkControl { case track, stepper, stepperOnly, tabs }

struct DkStepper {
    let caption: String
    let values: [Double]
    let initial: Int
    let format: (Double) -> String
}

struct DkLab {
    let control: DkControl
    var tabs: [String] = []
    var initialTab = 0
    var stepper: DkStepper? = nil
    let frames: (_ tab: Int, _ param: Int) -> [DkFrame]
}

// MARK: - Lab

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class DkModel {
    let lab: DkLab
    let playback: PlaybackState
    var tab: Int { didSet { if tab != oldValue { load() } } }
    var param: Int { didSet { if param != oldValue { load() } } }
    var step = 0
    /// Some labs train a small model or run a long simulation, so frames are built off the main thread.
    private(set) var frames: [DkFrame] = []
    private var openStep = 0

    init(lab: DkLab) {
        self.lab = lab
        tab = lab.initialTab
        param = lab.stepper?.initial ?? 0
        playback = PlaybackState(stepCount: 1, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        openStep = UserDefaults.standard.integer(forKey: "openStep")
        #endif
        load()
    }

    private func load() {
        let lab = lab, t = tab, p = param
        Task { [weak self] in
            let built = await Task.detached(priority: .userInitiated) { lab.frames(t, p) }.value
            guard let self, self.tab == t, self.param == p else { return }
            self.frames = built
            self.playback.stepCount = built.count
            if self.openStep > 0 {
                self.playback.index = min(self.openStep, built.count) - 1
                self.step = self.playback.index
                self.openStep = 0
            }
        }
    }

    var index: Int { min(max(lab.control == .tabs || lab.control == .stepperOnly ? step : playback.index, 0), frames.count - 1) }

    func reset() {
        tab = lab.initialTab
        param = lab.stepper?.initial ?? 0
        step = 0
        playback.reset()
    }
}

struct DeepStoryLab: View {
    @State private var model: DkModel
    @Environment(\.labDock) private var dock

    init(topicId: String) { _model = State(initialValue: DkModel(lab: deepLab(topicId))) }

    var body: some View {
        if model.frames.isEmpty {
            ProgressView()
                .frame(maxWidth: .infinity, minHeight: 320)
                .background(Color.clear)
                .card(radius: 20)
        } else {
            content
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            DkBody(frame: model.frames[model.index])
            if model.lab.control == .track {
                PlaybackTransport(state: model.playback, captions: model.frames.map { storyPlain($0.headline) },
                                  action: { model.frames[min(max($0, 0), model.frames.count - 1)].action })
            } else if dock == nil {
                Divider().padding(.top, 16)
                DkControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock, model.lab.control != .track else { return }
            let model = model
            dock.controls = { AnyView(DkControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.reset() }
        }
        .onDisappear {
            guard model.lab.control != .track else { return }
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct DkControls: View {
    let model: DkModel

    var body: some View {
        let index = model.index
        if model.lab.control == .stepper, let s = model.lab.stepper {
            LabTransportBar(state: model.playback, captions: model.frames.map { storyPlain($0.headline) }, footer: {
                AnyView(DkStepperNext(
                    caption: s.caption, value: s.format(s.values[model.param]),
                    canDecrease: model.param > 0, canIncrease: model.param < s.values.count - 1,
                    onStep: { d in model.param = min(max(model.param + d, 0), s.values.count - 1) },
                    onNext: { if model.playback.atEnd { model.playback.reset() } else { model.playback.stepForward() } }))
            })
        } else if model.lab.control == .stepperOnly, let s = model.lab.stepper {
            DkStepperNext(
                caption: s.caption, value: s.format(s.values[model.param]),
                canDecrease: model.param > 0, canIncrease: model.param < s.values.count - 1,
                onStep: { d in model.param = min(max(model.param + d, 0), s.values.count - 1) },
                onNext: { model.step = index >= model.frames.count - 1 ? 0 : index + 1 })
        } else {
            VStack(spacing: 14) {
                LabSegments(labels: model.lab.tabs, selected: Binding(get: { model.tab }, set: { model.tab = $0 }))
                LabBackActionRow(action: model.frames[index].action, backEnabled: index > 0,
                                 onBack: { model.step = index - 1 },
                                 onAction: { model.step = index >= model.frames.count - 1 ? 0 : index + 1 })
            }
        }
    }
}

/// "− input z 3.0 +" in one tinted box, beside the primary Next.
private struct DkStepperNext: View {
    let caption: String
    let value: String
    let canDecrease: Bool
    let canIncrease: Bool
    let onStep: (Int) -> Void
    let onNext: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        // The box and Next split the row 1 : 1.35, as on Android.
        GeometryReader { geo in
            let box = (geo.size.width - 12) / 2.35
            HStack(spacing: 12) {
                stepper.frame(width: box)
                LabButton(label: "Next", primary: true, action: onNext)
            }
        }
        .frame(height: 52)
    }

    private var stepper: some View {
            HStack(spacing: 0) {
                glyph("minus", enabled: canDecrease, label: "Decrease \(caption)") { onStep(-1) }
                VStack(spacing: 0) {
                    Text(caption).font(AppFont.sans(11)).foregroundStyle(palette.muted).lineLimit(1)
                    Text(value).font(AppFont.mono(19, .bold)).foregroundStyle(palette.onSurface).lineLimit(1).minimumScaleFactor(0.6)
                }
                .frame(maxWidth: .infinity)
                glyph("plus", enabled: canIncrease, label: "Increase \(caption)") { onStep(1) }
            }
            .frame(height: 52)
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 14))
    }

    private func glyph(_ name: String, enabled: Bool, label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(palette.onSurface.opacity(enabled ? 1 : 0.3))
                .frame(width: 40, height: 52)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct DkBody: View {
    let frame: DkFrame
    @Environment(\.palette) private var palette

    var body: some View {
        LabCard {
            VStack(alignment: .leading, spacing: 0) {
                if let header = frame.header {
                    Text(header).font(AppFont.sans(14, .semibold)).foregroundStyle(palette.muted).lineLimit(2).padding(.bottom, 10)
                }
                switch frame.stage {
                case .plot(let plot): DkPlotView(plot: plot)
                case .bars(let bars): DkBarsView(bars: bars)
                case .net(let net): DkNetView(net: net)
                case .grids(let grids): DkGridsView(stage: grids)
                case .rows(let rows): DkRowsView(stage: rows)
                case .segs(let segs): DkSegsView(stage: segs)
                case .boxes(let boxes): DkBoxesView(stage: boxes)
                case .unet(let unet): DkUNetView(stage: unet)
                case .tiles(let tiles): DkTilesView(stage: tiles)
                case .pipeline(let p): DkPipelineView(stage: p)
                case .hist(let h): DkHistView(stage: h)
                case .tokens(let t): DkTokensView(stage: t)
                case .graph(let g): DkGraphView(stage: g)
                }
                if !frame.legend.isEmpty {
                    StoryLegendRow(items: frame.legend.map { (color: dkColor($0.ink), style: $0.style, label: $0.label) }).padding(.top, 14)
                }
            }
        }
        if !frame.formula.isEmpty { DkFormula(lines: frame.formula).padding(.top, 12) }
        if !frame.chips.isEmpty {
            LabChips(chips: frame.chips.map { LabChip(key: $0.key, value: $0.value, tint: $0.tint ? .answer : nil) }).padding(.top, 12)
        }
        LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
    }
}

private struct DkFormula: View {
    let lines: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(lines.indices, id: \.self) { i in
                storyText(lines[i], palette).font(AppFont.mono(13.5)).fixedSize(horizontal: false, vertical: true)
            }
        }
        .foregroundStyle(palette.onSurface.opacity(0.85))
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 10).padding(.horizontal, 14)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Rendering

private let dkBlue = Color(hex: 0x4F7FE0)
private let dkPink = Color(hex: 0xD6457A)
private let dkGreen = Color(hex: 0x4CAF6E)
private let dkOrange = Color(hex: 0xF08A3C)
private let dkGrey = Color(hex: 0x8A8F99)
private let dkOffFill = Color(hex: 0x3A3F4A)

func dkColor(_ ink: DkInk) -> Color {
    switch ink {
    case .blue: dkBlue
    case .pink: dkPink
    case .green: dkGreen
    case .orange: dkOrange
    case .grey: dkGrey
    case .yellow: SimColors.active
    case .violet: SimColors.answer
    case .slate: dkOffFill
    case .sky: Color(hex: 0x8AA4E8)
    }
}

struct DkStageShape: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private func dot(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private func segment(_ a: CGPoint, _ b: CGPoint) -> Path { var p = Path(); p.move(to: a); p.addLine(to: b); return p }

private struct DkPlotView: View {
    let plot: DkPlot
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let muted = palette.muted
            let ticks = plot.yTicks.map { (v, s) in (v, ctx.resolve(Text(s).font(AppFont.mono(10.5)).foregroundStyle(muted))) }
            let tickW = ticks.map { $0.1.measure(in: size).width }.max() ?? 0
            let left = tickW + 18, right = size.width - 14, top: CGFloat = 14, bottom = size.height - 24
            let (xLo, xHi) = plot.xr, (yLo, yHi) = plot.yr
            func px(_ x: Double) -> CGFloat { left + CGFloat((x - xLo) / (xHi - xLo)) * (right - left) }
            func py(_ y: Double) -> CGFloat { bottom - CGFloat((y - yLo) / (yHi - yLo)) * (bottom - top) }
            func at(_ p: DkP) -> CGPoint { CGPoint(x: px(p.x), y: py(p.y)) }

            for (v, text) in ticks {
                ctx.stroke(segment(CGPoint(x: left, y: py(v)), CGPoint(x: right, y: py(v))), with: .color(muted.opacity(0.14)), lineWidth: 1)
                ctx.draw(text, at: CGPoint(x: left - 8, y: py(v)), anchor: .trailing)
            }
            if plot.axis && xLo < 0 && xHi > 0 {
                ctx.stroke(segment(CGPoint(x: px(0), y: top), CGPoint(x: px(0), y: bottom)), with: .color(muted.opacity(0.3)), lineWidth: 1)
            }
            let xY = bottom + 12
            if plot.xTicks.isEmpty {
                ctx.draw(Text(plot.xLeft).font(AppFont.mono(10.5)).foregroundStyle(muted), at: CGPoint(x: left, y: xY), anchor: .leading)
                ctx.draw(Text(plot.xRight).font(AppFont.mono(10.5)).foregroundStyle(muted), at: CGPoint(x: right, y: xY), anchor: .trailing)
            } else {
                for t in plot.xTicks {
                    ctx.draw(Text(t.label).font(AppFont.mono(10.5, t.hot ? .bold : .regular)).foregroundStyle(t.hot ? SimColors.active : muted), at: CGPoint(x: px(t.x), y: xY))
                }
            }
            for b in plot.bars {
                let x0 = px(b.x - b.width / 2), x1 = px(b.x + b.width / 2), y = py(b.y)
                ctx.fill(Path(roundedRect: CGRect(x: x0, y: y, width: x1 - x0, height: bottom - y), cornerRadius: 3), with: .color(dkColor(b.ink).opacity(0.85)))
            }

            for r in plot.rules {
                ctx.stroke(segment(CGPoint(x: left, y: py(r.y)), CGPoint(x: right, y: py(r.y))),
                           with: .color(dkColor(r.ink).opacity(r.thin ? 0.55 : 1)), style: StrokeStyle(lineWidth: r.thin ? 1 : 1.5, dash: [4, 3]))
            }
            if let (x, label) = plot.guide {
                ctx.stroke(segment(CGPoint(x: px(x), y: top), CGPoint(x: px(x), y: bottom)), with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.2, dash: [3, 3]))
                let text = ctx.resolve(Text(label).font(AppFont.mono(10.5, .bold)).foregroundStyle(SimColors.active))
                let w = text.measure(in: size).width
                // Kept clear of the end labels ("−4", "+4") when it lands near an edge.
                let lw = ctx.resolve(Text(plot.xLeft).font(AppFont.mono(10.5))).measure(in: size).width + 8
                let rw = ctx.resolve(Text(plot.xRight).font(AppFont.mono(10.5))).measure(in: size).width + 8
                let lo = left + lw + w / 2, hi = right - rw - w / 2
                ctx.draw(text, at: CGPoint(x: lo <= hi ? min(max(px(x), lo), hi) : px(x), y: xY), anchor: .center)
            }
            ctx.drawLayer { layer in
                layer.clip(to: Path(CGRect(x: left, y: top, width: right - left, height: bottom - top)))
                for (rx, ry) in plot.ellipses {
                    layer.stroke(Path(ellipseIn: CGRect(x: px(-rx), y: py(ry), width: px(rx) - px(-rx), height: py(-ry) - py(ry))), with: .color(muted.opacity(0.25)), lineWidth: 1)
                }
            }
            for p in plot.scatter { ctx.fill(dot(at(p), 2.5), with: .color(dkGrey.opacity(0.7))) }
            ctx.drawLayer { layer in
                layer.clip(to: Path(CGRect(x: left, y: top - 2, width: right - left, height: bottom - top + 4)))
                for line in plot.lines where line.pts.count > 1 {
                    var path = Path()
                    path.move(to: at(line.pts[0]))
                    for p in line.pts.dropFirst() { path.addLine(to: at(p)) }
                    layer.stroke(path, with: .color(dkColor(line.ink)),
                                 style: StrokeStyle(lineWidth: line.dashed ? 1.8 : 2.5, lineCap: line.dashed ? .butt : .round, lineJoin: .round, dash: line.dashed ? [5, 4] : []))
                    if line.dots { for p in line.pts { layer.fill(dot(at(p), 3), with: .color(dkColor(line.ink))) } }
                }
            }
            if let b = plot.bracket {
                let y = py(b.y)
                let color = GraphicsContext.Shading.color(SimColors.active)
                ctx.stroke(segment(CGPoint(x: px(b.x0), y: y), CGPoint(x: px(b.x1), y: y)), with: color, lineWidth: 2)
                ctx.stroke(segment(CGPoint(x: px(b.x0), y: y), CGPoint(x: px(b.x0), y: y + 5)), with: color, lineWidth: 2)
                ctx.stroke(segment(CGPoint(x: px(b.x1), y: y), CGPoint(x: px(b.x1), y: y + 5)), with: color, lineWidth: 2)
                ctx.draw(Text(b.label).font(AppFont.mono(11, .bold)).foregroundStyle(SimColors.active), at: CGPoint(x: px(b.x0) + 4, y: y + 13), anchor: .leading)
            }
            for d in plot.dots { ctx.fill(dot(at(d.p), d.r), with: .color(dkColor(d.ink))) }
        }
        .frame(height: plot.height)
        .modifier(DkStageShape())
    }
}

private struct DkBarsView: View {
    let bars: DkBars
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let muted = palette.muted, ink = palette.onSurface.opacity(0.8)
            let n = bars.logits.count
            let padX: CGFloat = 12
            let slot = (size.width - 2 * padX) / CGFloat(n)
            let barW = slot * 0.62
            func cx(_ i: Int) -> CGFloat { padX + slot * (CGFloat(i) + 0.5) }

            // Upper: the logits from a zero line, negative ones hanging below it.
            ctx.draw(Text("logits z").font(AppFont.mono(11)).foregroundStyle(muted), at: CGPoint(x: padX, y: 12), anchor: .leading)
            let zero: CGFloat = 72
            let unit = 22 / CGFloat(max(bars.logits.map { abs($0) }.max() ?? 1, 1e-9))
            ctx.stroke(segment(CGPoint(x: padX, y: zero), CGPoint(x: size.width - padX, y: zero)), with: .color(muted.opacity(0.3)), lineWidth: 1)
            for (i, v) in bars.logits.enumerated() {
                let h = CGFloat(v) * unit
                let y = h >= 0 ? zero - h : zero
                ctx.fill(Path(roundedRect: CGRect(x: cx(i) - barW / 2, y: y, width: barW, height: max(abs(h), 2)), cornerRadius: 3), with: .color(dkGrey))
                ctx.draw(Text(dkNum(v, 1)).font(AppFont.mono(11)).foregroundStyle(ink), at: CGPoint(x: cx(i), y: h >= 0 ? zero - h - 9 : zero - h + 9))
            }

            // Lower: e^z or the probabilities, on a shared baseline over the class names.
            ctx.draw(Text(bars.lowerLabel).font(AppFont.mono(11)).foregroundStyle(muted), at: CGPoint(x: padX, y: 104), anchor: .leading)
            let base = size.height - 22
            let maxH: CGFloat = 46
            let top = max(bars.lower.max() ?? 1, 1e-9)
            ctx.stroke(segment(CGPoint(x: padX, y: base), CGPoint(x: size.width - padX, y: base)), with: .color(muted.opacity(0.3)), lineWidth: 1)
            for (i, v) in bars.lower.enumerated() {
                let h = CGFloat(v / top) * maxH
                let hot = i == bars.top
                ctx.fill(Path(roundedRect: CGRect(x: cx(i) - barW / 2, y: base - h, width: barW, height: max(h, 2)), cornerRadius: 3),
                         with: .color(hot ? SimColors.active : dkBlue))
                ctx.draw(Text(dkNum(v, bars.lowerDigits)).font(AppFont.mono(11, hot ? .bold : .regular)).foregroundStyle(hot ? SimColors.active : ink),
                         at: CGPoint(x: cx(i), y: base - h - 9))
                ctx.draw(Text(bars.labels[i]).font(AppFont.mono(11)).foregroundStyle(muted), at: CGPoint(x: cx(i), y: base + 11))
            }
        }
        .frame(height: 206)
        .modifier(DkStageShape())
    }
}

private struct DkNetView: View {
    let net: DkNet
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let muted = palette.muted, stage = palette.surface
            let r: CGFloat = 22
            let padX = r + 22, top = r + 12, bottom = size.height - r - 24
            func at(_ n: DkNode) -> CGPoint { CGPoint(x: padX + n.x * (size.width - 2 * padX), y: top + n.y * (bottom - top)) }

            for e in net.edges.sorted(by: { ($0.state == .lit ? 1 : 0) < ($1.state == .lit ? 1 : 0) }) {
                let a = at(net.nodes[e.from]), b = at(net.nodes[e.to])
                let base = e.w >= 0 ? dkBlue : dkOrange
                let width = CGFloat(1.2 + 1.6 * abs(e.w))
                switch e.state {
                case .silenced: ctx.stroke(segment(a, b), with: .color(dkGrey.opacity(0.7)), style: StrokeStyle(lineWidth: 2, dash: [3, 4]))
                case .faint: ctx.stroke(segment(a, b), with: .color(base.opacity(0.45)), style: StrokeStyle(lineWidth: width, lineCap: .round))
                case .lit: ctx.stroke(segment(a, b), with: .color(base), style: StrokeStyle(lineWidth: width + 0.8, lineCap: .round))
                }
            }
            for e in net.edges {
                guard let label = e.label else { continue }
                let a = at(net.nodes[e.from]), b = at(net.nodes[e.to])
                let m = CGPoint(x: a.x + (b.x - a.x) * 0.5, y: a.y + (b.y - a.y) * 0.5)
                let text = ctx.resolve(Text(label).font(AppFont.mono(12, .medium)).foregroundStyle(e.state == .silenced ? muted : .white))
                let s = text.measure(in: size)
                ctx.fill(Path(roundedRect: CGRect(x: m.x - s.width / 2 - 4, y: m.y - s.height / 2, width: s.width + 8, height: s.height), cornerRadius: 4), with: .color(stage))
                ctx.draw(text, at: m)
            }
            for n in net.nodes {
                let c = at(n)
                switch n.tone {
                case .input: ctx.fill(dot(c, r), with: .color(dkBlue))
                case .hidden: ctx.fill(dot(c, r), with: .color(dkGreen))
                case .output: ctx.fill(dot(c, r), with: .color(SimColors.answer))
                case .off: ctx.fill(dot(c, r), with: .color(dkOffFill))
                case .current:
                    ctx.fill(dot(c, r), with: .color(stage))
                    ctx.stroke(dot(c, r - 1.25), with: .color(SimColors.active), lineWidth: 2.5)
                case .ghost:
                    ctx.fill(dot(c, r), with: .color(stage))
                    ctx.stroke(dot(c, r - 0.75), with: .color(muted.opacity(0.6)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                }
                let ink: Color = switch n.tone {
                case .off, .ghost: muted
                case .current: SimColors.active
                default: .white
                }
                ctx.draw(Text(n.text).font(AppFont.mono(13, .bold)).foregroundStyle(ink), at: c)
                if let note = n.note {
                    ctx.draw(Text(note).font(AppFont.mono(11)).foregroundStyle(dkOrange), at: CGPoint(x: c.x + r + 4, y: c.y + r * 0.45), anchor: .leading)
                }
            }
            for (x, text) in net.footers {
                ctx.draw(Text(text).font(AppFont.sans(12)).foregroundStyle(muted), at: CGPoint(x: padX + x * (size.width - 2 * padX), y: size.height - 12))
            }
        }
        .frame(height: 236)
        .modifier(DkStageShape())
    }
}

// MARK: - Number text shared with the frames

/// Fixed decimals with a real minus sign, rounded half away from zero on both platforms.
func dkNum(_ v: Double, _ d: Int) -> String {
    var p: Int64 = 1
    for _ in 0..<d { p *= 10 }
    let r = Int64((abs(v) * Double(p) + 0.5).rounded(.down))
    var body = "\(r / p)"
    if d > 0 {
        let frac = String(r % p)
        body += "." + String(repeating: "0", count: d - frac.count) + frac
    }
    return v < 0 && r != 0 ? "−" + body : body
}

/// Builds every frame each control can reach (every tab and stepper value); used by the frame tests.
func deepStoryFrameCount(_ topicId: String) -> Int {
    let lab = deepLab(topicId)
    var total = 0
    for t in 0..<max(lab.tabs.count, 1) {
        for p in 0..<(lab.stepper?.values.count ?? 1) { total += lab.frames(t, p).count }
    }
    return total
}
