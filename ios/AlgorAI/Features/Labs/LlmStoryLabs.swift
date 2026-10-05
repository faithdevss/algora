import SwiftUI

// Port of LlmStoryLabs.kt: seventeen labs (full fine-tuning to MMLU) as one card of stacked blocks — a
// caption, token chips, labelled bars, a histogram, a scatter plot or key/value stats — over a legend,
// the step's arithmetic in a formula box and a headline. The dock is an optional picker over the play
// transport; the picker re-runs the same steps on another setting. Frames: TuneStoryFrames.swift and
// BeyondStoryFrames.swift.

let llmStoryTopicIds: Set<String> = tuneStoryTopicIds.union(beyondStoryTopicIds)

func llmLab(_ topicId: String) -> LsLab {
    if let lab = tuneLab(topicId) ?? beyondLab(topicId) { return lab }
    fatalError("No LLM storyboard for \(topicId)")
}

enum LsInk { case blue, pink, green, red, violet, lilac, yellow, slate, grey }

/// Token chip states: plain, the one being read, done, not reached, the answer, an error.
enum LsTone { case plain, cur, done, fut, ans, err }

/// A bar row's name: emphasised (the picked one), normal, or muted.
enum LsText { case strong, normal, muted }

struct LsTok { let t: String; var tone: LsTone = .plain; var sub = "" }

/// A labelled bar over [from, from + width] of the track (fractions); `mid` draws a zero line at the centre.
struct LsRow {
    let w: String
    let v: String
    let from: Double
    let width: Double
    let ink: LsInk
    var text: LsText = .normal
    var mid = false
}

/// A scatter point at fractions of the plot (y up); a ringed big one is a training point.
struct LsPt { let x: Double; let y: Double; let ink: LsInk; var big = false }

struct LsStat { let k: String; let v: String; var ink: LsInk? = nil }

enum LsBlock {
    case label(String)
    case chips([LsTok])
    case bars([LsRow])
    /// Column heights as fractions of the tallest.
    case hist([Double])
    case plot(height: CGFloat, pts: [LsPt])
    case stats([LsStat])
}

/// A formula line: `a` muted, then `b` bold in `ink` (the line's result).
struct LsFx { let a: String; var b = ""; var ink: LsInk? = nil }

struct LsFrame {
    let blocks: [LsBlock]
    let fx: [LsFx]
    let headline: String
    let body: String
}

struct LsLab {
    let tabs: [String]
    let initialTab: Int
    let legend: [(LsInk, String)]
    let frames: (_ tab: Int) -> [LsFrame]
}

// MARK: - Number text shared by the frames (the design's helpers)

/// Fixed decimals with a real minus sign; a value that rounds to zero prints unsigned.
func lsF(_ x: Double, _ d: Int = 2) -> String {
    var p = 1.0
    for _ in 0..<d { p *= 10 }
    let r = (abs(x) * p + 0.5).rounded(.down)
    if r == 0 { return d == 0 ? "0" : "0." + String(repeating: "0", count: d) }
    let whole = Int64(r / p)
    let frac = Int64(r - Double(whole) * p)
    var body = "\(whole)"
    if d > 0 {
        let fs = String(frac)
        body += "." + String(repeating: "0", count: max(0, d - fs.count)) + fs
    }
    return x < 0 ? "−" + body : body
}

func lsPct(_ p: Double) -> String { p == 0 ? "0%" : lsF(p * 100, p < 0.1 ? 1 : 0) + "%" }

func lsNum(_ x: Double) -> String {
    let r = Int64((x).rounded(.toNearestOrAwayFromZero))
    var digits = Array(String(abs(r)))
    var out = ""
    while digits.count > 3 { out = "," + String(digits.suffix(3)) + out; digits.removeLast(3) }
    out = String(digits) + out
    return r < 0 ? "−" + out : out
}

func lsBig(_ n: Double) -> String {
    func g(_ x: Double) -> String {
        var s = lsF(x, x < 10 ? 2 : x < 100 ? 1 : 0)
        if s.contains(".") {
            while s.hasSuffix("0") { s.removeLast() }
            if s.hasSuffix(".") { s.removeLast() }
        }
        return s
    }
    if n >= 1e12 { return g(n / 1e12) + "T" }
    if n >= 1e9 { return g(n / 1e9) + "B" }
    if n >= 1e6 { return g(n / 1e6) + "M" }
    if n >= 1e3 { return g(n / 1e3) + "K" }
    return g(n)
}

private func mantissa(_ x: Double, _ d: Int) -> (String, Int) {
    var e = Int(log10(abs(x)).rounded(.down))
    var m = x / pow(10, Double(e))
    if abs(Double(lsF(m, d).replacingOccurrences(of: "−", with: "")) ?? 0) >= 10 { e += 1; m = x / pow(10, Double(e)) }
    return (lsF(m, d), e)
}

/// "1.2e12": one decimal of mantissa, no plus sign.
func lsSci(_ x: Double) -> String {
    if x == 0 { return "0.0e0" }
    let (m, e) = mantissa(x, 1)
    return m + "e" + (e < 0 ? "−\(-e)" : "\(e)")
}

/// "2.38e-4" style with two decimals, as toExponential(2).
func lsExp(_ x: Double) -> String {
    if x == 0 { return "0.00e+0" }
    let (m, e) = mantissa(x, 2)
    return m.replacingOccurrences(of: "−", with: "-") + "e" + (e < 0 ? "-\(-e)" : "+\(e)")
}

/// 0.1, 0.5, 2 — a number as JavaScript prints it.
func lsJs(_ x: Double) -> String {
    if x == x.rounded() { return String(Int64(x)) }
    return String(x)
}

// Row builders, as the design's row() and brow().
func lsRow(_ w: String, _ v: String, _ frac: Double, _ ink: LsInk, _ text: LsText = .normal) -> LsRow {
    LsRow(w: w, v: v, from: 0, width: min(max(frac, 0), 1), ink: ink, text: text)
}

func lsBrow(_ w: String, _ x: Double, _ scale: Double, _ v: String? = nil, _ text: LsText = .normal) -> LsRow {
    let a = min(abs(x) / scale, 1) * 0.5
    return LsRow(w: w, v: v ?? lsF(x), from: x >= 0 ? 0.5 : 0.5 - a, width: a, ink: x >= 0 ? .blue : .pink, text: text, mid: true)
}

/// Seeded Park–Miller generator, the design's `seed = seed·16807 mod (2³¹ − 1)`.
struct LsRng {
    private var s: Int64
    init(_ seed: Int64) { s = seed }
    mutating func next() -> Double {
        s = (s * 16807) % 2147483647
        return Double(s) / 2147483647.0
    }
}

/// JavaScript's Math.round: half rounds up.
func lsRound(_ x: Double) -> Double { (x + 0.5).rounded(.down) }

// MARK: - Lab

@MainActor
@Observable
private final class LsModel {
    let lab: LsLab
    let byTab: [[LsFrame]]
    let playback: PlaybackState
    var tab: Int

    init(lab: LsLab) {
        self.lab = lab
        byTab = (0..<max(lab.tabs.count, 1)).map { lab.frames($0) }
        tab = lab.initialTab
        playback = PlaybackState(stepCount: byTab[0].count, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        let open = UserDefaults.standard.integer(forKey: "openStep")
        if open > 0 { playback.index = min(open, byTab[0].count) - 1 }
        #endif
    }

    var frames: [LsFrame] { byTab[min(max(tab, 0), byTab.count - 1)] }
    var index: Int { min(max(playback.index, 0), frames.count - 1) }

    func reset() {
        tab = lab.initialTab
        playback.reset()
    }
}

struct LlmStoryLab: View {
    @State private var model: LsModel
    @Environment(\.labDock) private var dock

    init(topicId: String) { _model = State(initialValue: LsModel(lab: llmLab(topicId))) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            LsBody(frame: model.frames[model.index], legend: model.lab.legend)
            if dock == nil {
                Divider().padding(.top, 16)
                LsControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(LsControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.reset() }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct LsControls: View {
    let model: LsModel

    var body: some View {
        VStack(spacing: 14) {
            if !model.lab.tabs.isEmpty {
                LabSegments(labels: model.lab.tabs, selected: Binding(get: { model.tab }, set: { model.tab = $0 }))
            }
            LabTransportBar(state: model.playback, captions: model.frames.map { storyPlain($0.headline) })
        }
    }
}

private struct LsBody: View {
    let frame: LsFrame
    let legend: [(LsInk, String)]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(frame.blocks.indices, id: \.self) { i in
                switch frame.blocks[i] {
                case .label(let text): Text(text).font(AppFont.sans(13, .semibold)).foregroundStyle(palette.muted)
                case .chips(let toks): LsChipsView(toks: toks)
                case .bars(let rows): LsBarsView(rows: rows)
                case .hist(let heights): LsHistView(heights: heights)
                case .plot(let height, let pts): LsPlotView(height: height, pts: pts)
                case .stats(let rows): LsStatsView(rows: rows)
                }
            }
            if !legend.isEmpty {
                StoryLegendRow(items: legend.map { (color: lsColor($0.0), style: .fill, label: $0.1) })
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 20)
        if !frame.fx.isEmpty { LsFormula(lines: frame.fx).padding(.top, 12) }
        LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
    }
}

private struct LsFormula: View {
    let lines: [LsFx]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(lines.indices, id: \.self) { i in
                let l = lines[i]
                (Text(l.a).foregroundColor(palette.muted)
                    + Text(l.b.isEmpty ? "" : " " + l.b).fontWeight(.semibold).foregroundColor(l.ink.map(lsColor) ?? palette.onSurface))
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

// MARK: - Rendering

func lsColor(_ ink: LsInk) -> Color {
    switch ink {
    case .blue: SimColors.blue
    case .pink: Color(hex: 0xE5337A)
    case .green: SimColors.green
    case .red: Color(hex: 0xE5484D)
    case .violet: Color(hex: 0x6D5DFC)
    case .lilac: Color(hex: 0x8F84FF)
    case .yellow: SimColors.active
    case .slate: Color(hex: 0x6B7180).opacity(0.55)
    case .grey: Color(hex: 0x9AA0AE)
    }
}

private let lsTrack = Color.black.opacity(0.16)

private struct LsChipsView: View {
    let toks: [LsTok]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 5, lineSpacing: 5) {
            ForEach(toks.indices, id: \.self) { i in
                let t = toks[i]
                let (bg, fg) = colors(t.tone)
                VStack(spacing: 1) {
                    Text(t.t).font(AppFont.sans(14, .semibold)).lineLimit(1)
                    if !t.sub.isEmpty { Text(t.sub).font(AppFont.mono(10)).lineLimit(1) }
                }
                .foregroundStyle(fg)
                .padding(.horizontal, 10).padding(.vertical, 4)
                .frame(minHeight: 30)
                .background(bg, in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    if t.tone == .fut { RoundedRectangle(cornerRadius: 8).strokeBorder(SimColors.tint, lineWidth: 1) }
                }
            }
        }
    }

    private func colors(_ tone: LsTone) -> (Color, Color) {
        switch tone {
        case .cur: (SimColors.active, Color(hex: 0x1B1D24))
        case .done: (SimColors.green.opacity(0.18), Color(hex: 0x5FD49B))
        case .fut: (Color(hex: 0x767680, opacity: 0.03), palette.muted.opacity(0.7))
        case .ans: (Color(hex: 0x6D5DFC), .white)
        case .err: (Color(hex: 0xE5484D).opacity(0.2), Color(hex: 0xFF8A8D))
        case .plain: (SimColors.tint, palette.onSurface)
        }
    }
}

private struct LsBarsView: View {
    let rows: [LsRow]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 3) {
            ForEach(rows.indices, id: \.self) { i in
                let r = rows[i]
                HStack(spacing: 0) {
                    Text(r.w).font(AppFont.sans(12, .semibold)).lineLimit(1).truncationMode(.tail)
                        .foregroundStyle(r.text == .strong ? palette.onSurface : r.text == .normal ? palette.onSurface.opacity(0.8) : palette.muted)
                        .frame(width: 84, alignment: .leading)
                    GeometryReader { geo in
                        let w = geo.size.width
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 4).fill(lsTrack).frame(height: 8)
                            if r.mid { Rectangle().fill(palette.muted.opacity(0.5)).frame(width: 1, height: 14).offset(x: w / 2) }
                            if r.width > 0 {
                                RoundedRectangle(cornerRadius: 4).fill(lsColor(r.ink)).frame(width: w * r.width, height: 8).offset(x: w * r.from)
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

private struct LsHistView: View {
    let heights: [Double]

    var body: some View {
        HStack(spacing: 6) {
            ForEach(heights.indices, id: \.self) { i in
                ZStack(alignment: .bottom) {
                    RoundedRectangle(cornerRadius: 6).fill(lsTrack)
                    UnevenRoundedRectangle(topLeadingRadius: 3, bottomLeadingRadius: 1, bottomTrailingRadius: 1, topTrailingRadius: 3)
                        .fill(lsColor(.grey))
                        .frame(width: 9, height: 58 * heights[i])
                        .padding(.bottom, 2)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 64)
    }
}

private struct LsPlotView: View {
    let height: CGFloat
    let pts: [LsPt]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            for p in pts {
                let c = CGPoint(x: size.width * p.x, y: size.height * (1 - p.y))
                // A training point is 10pt inside a 2pt ring.
                let r: CGFloat = p.big ? 5 : 3
                if p.big {
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 7, y: c.y - 7, width: 14, height: 14)), with: .color(palette.onSurface))
                }
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(lsColor(p.ink)))
            }
        }
        .frame(height: height)
        .background(lsTrack, in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct LsStatsView: View {
    let rows: [LsStat]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            ForEach(rows.indices, id: \.self) { i in
                let s = rows[i]
                HStack(alignment: .top, spacing: 0) {
                    Text(s.k).font(AppFont.sans(14)).foregroundStyle(palette.muted).frame(width: 130, alignment: .leading).padding(.trailing, 8)
                    Text(s.v).font(AppFont.sans(14, .semibold)).foregroundStyle(s.ink.map(lsColor) ?? palette.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(minHeight: 22)
            }
        }
    }
}
