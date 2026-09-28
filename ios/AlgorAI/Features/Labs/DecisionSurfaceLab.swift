import SwiftUI

// Port of DecisionSurfaceSection.kt: an arbitrary decision function evaluated on a grid, tinted by
// predicted class, with the zero contour (and the ±1 margins where the model has them) traced by
// marching squares, so curved boundaries, ellipses, support vectors and an online update can all be
// shown. Fits run off the main thread (SMO and the ν sweep are heavy).
//
// Layout follows the parameter-lab pattern: variant segments, a stage card (plane + legend), readout
// chips, narration, then the sliders — pinned in thumb reach when docked, with the stream transport
// under them for Passive-Aggressive.

private let negativeFill = SimColors.blue
private let positiveFill = CategoryAccents.pink
private let supportRing = SimColors.active
private let errorRing = SimColors.red
private let ellipseColor = SimColors.grey

private enum MarkKind { case ring, dot, line }
private struct LegendMark { let color: Color; let label: String; let kind: MarkKind }

private struct SurfaceResult {
    let decision: (Float, Float) -> Double
    /// "updates = 14 · τ = 0.000", rendered as ReadoutChips.
    let readout: String
    /// What happened. `highlight`, if it occurs in it, is tinted.
    let headline: String
    var highlight: String? = nil
    let detail: String
    var ringed: Set<Int> = []
    var errors: Set<Int> = []
    var ellipses: [[(Float, Float)]] = []
    var marginBand: Double?
    /// Points from this index on haven't streamed in yet; drawn dim.
    var unseenFrom: Int?
}

private struct SurfaceSlider {
    let name: String
    let symbol: String
    let range: ClosedRange<Double>
    let initial: Double
    var format: (Double) -> String = { String(format: "%.2f", $0) }
}

private struct SurfaceConfig {
    let intro: String
    let data: (Int32) -> [ClassPoint]
    let sliders: [SurfaceSlider]
    var variants: [String] = []
    var legend: [LegendMark] = []
    /// Streams the points in one at a time; the step is how many have been seen.
    var stream = false
    let evaluate: (_ points: [ClassPoint], _ values: [Double], _ variant: Int, _ step: Int) -> SurfaceResult
}

private func f2(_ v: Double) -> String { String(format: "%.2f", v) }
private func f3(_ v: Double) -> String { String(format: "%.3f", v) }
private func errorsOf(_ points: [ClassPoint], _ d: (Float, Float) -> Double) -> Set<Int> {
    Set(points.indices.filter {
        let v = d(points[$0].x, points[$0].y)
        return (v >= 0 && points[$0].label < 0) || (v < 0 && points[$0].label > 0)
    })
}
private func plural(_ n: Int, _ word: String) -> String { "\(n) \(word)\(n == 1 ? "" : "s")" }

private let supportLegend = LegendMark(color: supportRing, label: "Support vector", kind: .ring)
private let errorLegend = LegendMark(color: errorRing, label: "Misclassified", kind: .ring)

private func svmRbf() -> SurfaceConfig {
    SurfaceConfig(
        intro: "Two concentric rings — not linearly separable by any line at all. γ controls how far each support vector's influence reaches; C controls how much margin violation is tolerated.",
        data: { ringData(seed: $0) },
        sliders: [
            SurfaceSlider(name: "Reach", symbol: "log₁₀ γ", range: -2...1.2, initial: -0.4, format: { String(format: "%.1f", $0) }),
            SurfaceSlider(name: "Penalty", symbol: "log₁₀ C", range: -1...3, initial: 1, format: { String(format: "%.1f", $0) }),
        ],
        legend: [supportLegend, errorLegend],
        evaluate: { points, values, _, _ in
            let gamma = pow(10, Double(Float(values[0]))), c = pow(10, Double(Float(values[1])))
            let fit = trainSvm(points, kernel: rbfKernel(gamma), c: c)
            let wrong = fit.misclassified()
            let readout = "SVs = \(fit.supportVectors.count) · correct = \(points.count - wrong.count)/\(points.count) · γ = \(f2(gamma))"
            let (headline, highlight, detail): (String, String, String) =
                gamma > 3 ? ("γ is large, so the boundary breaks into islands around single points.", "islands",
                             "Each support vector's influence has shrunk to nearly a point. That is memorization, and it will generalize badly.")
                : gamma < 0.05 ? ("γ is small, so the kernel behaves almost linearly.", "almost linearly",
                                  "Every point influences everywhere, and nothing that smooth can split two concentric rings.")
                : ("The boundary is a closed curve, which no linear model can produce.", "closed curve",
                   "The kernel trick got there using only inner products between pairs of points, never coordinates in the higher-dimensional space.")
            return SurfaceResult(decision: { fit.decision($0, $1) }, readout: readout, headline: headline, highlight: highlight,
                                 detail: detail, ringed: fit.supportVectors, errors: wrong, marginBand: 1)
        })
}

private func nuSvc() -> SurfaceConfig {
    SurfaceConfig(
        intro: "ν replaces C with something you can actually reason about: it simultaneously upper-bounds the fraction of margin errors and lower-bounds the fraction of support vectors. Both are measured below.",
        data: { overlappingBlobs(seed: $0, separation: 1.15) },
        sliders: [SurfaceSlider(name: "Budget", symbol: "ν", range: 0.05...0.8, initial: 0.3)],
        legend: [supportLegend, errorLegend],
        evaluate: { points, values, _, _ in
            let nu = Double(Float(values[0]))
            let r = trainNuSvc(points, kernel: linearKernel(), nu: nu)
            let holds = r.marginErrorFraction <= nu + 0.06 && r.svFraction >= nu - 0.06
            return SurfaceResult(
                decision: { r.fit.decision($0, $1) },
                readout: "ν = \(f2(nu)) · margin err = \(f2(r.marginErrorFraction)) · SVs = \(f2(r.svFraction))",
                headline: holds
                    ? "The bound holds: margin errors \(f2(r.marginErrorFraction)) ≤ ν \(f2(nu)) ≤ support vectors \(f2(r.svFraction))."
                    : "Margin errors \(f2(r.marginErrorFraction)) and support vectors \(f2(r.svFraction)) sit just outside ν = \(f2(nu)).",
                highlight: holds ? "The bound holds" : nil,
                detail: holds
                    ? "That sandwich is what ν buys. C gives you nothing comparable, which is why it always needs a grid search."
                    : "The bound is asymptotic, so on \(points.count) points it can miss slightly. The equivalent C here is \(f3(r.c)).",
                ringed: r.fit.supportVectors, errors: r.fit.misclassified(), marginBand: 1)
        })
}

private func lda() -> SurfaceConfig {
    SurfaceConfig(
        intro: "Two classes with genuinely different spreads. LDA assumes they share one covariance, so it pools them — and the boundary it produces is always a straight line.",
        data: { unequalCovarianceBlobs(seed: $0) },
        sliders: [SurfaceSlider(name: "Shrinkage", symbol: "", range: 0...0.95, initial: 0)],
        legend: [LegendMark(color: ellipseColor, label: "Pooled covariance", kind: .line), errorLegend],
        evaluate: { points, values, _, _ in
            let fit = fitLda(points, shrink: Double(Float(values[0])))
            let wrong = errorsOf(points, fit.decision)
            return SurfaceResult(
                decision: fit.decision,
                readout: "correct = \(points.count - wrong.count)/\(points.count) · params = 5",
                headline: "Both ellipses are identical, so the boundary is a straight line.",
                highlight: "identical",
                detail: "LDA forces both classes to share one pooled covariance, which cancels the quadratic terms. "
                    + (wrong.isEmpty ? "On this sample the wrong shape costs nothing; tap New Data until it does."
                       : "The pooled shape is wrong for the wide class, and the \(plural(wrong.count, "circled point")) are what that costs."),
                errors: wrong, ellipses: [fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)])
        })
}

private func qda() -> SurfaceConfig {
    SurfaceConfig(
        intro: "The identical data as LDA, with the shared-covariance assumption dropped. Each class estimates its own, and the boundary stops being a line.",
        data: { unequalCovarianceBlobs(seed: $0) },
        sliders: [SurfaceSlider(name: "Shrinkage", symbol: "", range: 0...0.95, initial: 0)],
        legend: [LegendMark(color: ellipseColor, label: "Per-class covariance", kind: .line), errorLegend],
        evaluate: { points, values, _, _ in
            let fit = fitQda(points, shrink: Double(Float(values[0])))
            let wrong = errorsOf(points, fit.decision)
            let ldaWrong = errorsOf(points, fitLda(points).decision).count
            let verdict = wrong.count < ldaWrong ? "that buys \(plural(ldaWrong - wrong.count, "fewer error"))."
                : wrong.count > ldaWrong ? "it costs \(plural(wrong.count - ldaWrong, "extra error")): two covariances from \(points.count / 2) points each are noisier than one pooled estimate that is merely biased."
                : "the two tie, so the extra six parameters bought nothing."
            return SurfaceResult(
                decision: fit.decision,
                readout: "QDA = \(points.count - wrong.count)/\(points.count) · LDA = \(points.count - ldaWrong)/\(points.count) · params = 11",
                headline: "Each class gets its own ellipse, so the boundary bends into a conic.",
                highlight: "conic",
                detail: "QDA fits 11 parameters to LDA's 5, and on this sample " + verdict + " Shrinkage is the dial between them.",
                errors: wrong, ellipses: [fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)])
        })
}

private func passiveAggressive() -> SurfaceConfig {
    SurfaceConfig(
        intro: "One pass over a stream, one example at a time. Example 45 is deliberately mislabelled — scrub past it and watch what each variant does to a boundary that was already correct.",
        data: { streamData(seed: $0) },
        sliders: [SurfaceSlider(name: "Aggressiveness", symbol: "C", range: 0.05...3, initial: 1)],
        variants: ["Hard", "PA-I", "PA-II"],
        legend: [LegendMark(color: supportRing, label: "Current", kind: .ring), errorLegend,
                 LegendMark(color: SimColors.grey.opacity(0.45), label: "Not seen yet", kind: .dot)],
        stream: true,
        evaluate: { points, values, variantIndex, step in
            let seen = min(max(step, 0), points.count)
            let aggr = Double(Float(values[0]))
            let variant: PaVariant = [.hard, .paI, .paII][min(max(variantIndex, 0), 2)]
            let path = passiveAggressivePath(points, variant: variant, aggressiveness: aggr)
            let s = path[min(seen, path.count - 1)]
            let decision: (Float, Float) -> Double = { s.w1 * Double($0) + s.w2 * Double($1) + s.bias }
            let wrong = errorsOf(Array(points.prefix(seen)), decision)
            let tau = f3(s.lastTau)
            let headline: String, highlight: String?, detail: String
            if seen == 0 {
                headline = "No examples yet, so the line hasn't been placed."
                highlight = nil
                detail = "Press play to stream the \(points.count) examples in one at a time."
            } else if s.lastLoss == 0 {
                headline = "This example is outside the margin, so τ = 0 and the line stays put."
                highlight = "outside the margin"
                detail = "That is the passive half: a confident, correct example teaches nothing."
            } else if s.lastIndex == 44 {
                highlight = "mislabelled"
                switch variant {
                case .hard:
                    headline = "The mislabelled example forces a step of τ = \(tau)."
                    detail = "Hard PA has no cap, so it moves as far as it takes to fit this one point and wrecks a boundary that was already right."
                case .paI:
                    headline = "The mislabelled example is capped at τ = \(tau)."
                    detail = "PA-I clips the step at C = \(f2(aggr)), so one bad label can only do bounded damage."
                case .paII:
                    headline = "The mislabelled example is softened to τ = \(tau)."
                    detail = "PA-II adds 1/2C to the denominator, shrinking the step smoothly with no hard cutoff."
                }
            } else {
                headline = "Hinge loss is \(f2(s.lastLoss)), so the line moves just far enough to put this example on the margin."
                highlight = "on the margin"
                detail = "That is the aggressive half. \(plural(s.updates, "update")) over \(seen) examples, \(plural(s.mistakes, "outright mistake"))."
            }
            return SurfaceResult(
                decision: decision,
                readout: "updates = \(s.updates) · τ = \(tau) · hinge = \(f2(s.lastLoss))",
                headline: headline, highlight: highlight, detail: detail,
                ringed: seen >= 1 && seen <= points.count ? [seen - 1] : [], errors: wrong, marginBand: 1, unseenFrom: seen)
        })
}

private func gaussianNb() -> SurfaceConfig {
    SurfaceConfig(
        intro: "Gaussian naive Bayes is QDA with the off-diagonal covariance terms forced to zero. \"Features are independent given the class\" is not an abstraction here — it is visible as ellipses that cannot tilt.",
        data: { unequalCovarianceBlobs(seed: $0) },
        sliders: [SurfaceSlider(name: "Smoothing", symbol: "var", range: 0...1.5, initial: 0)],
        legend: [LegendMark(color: ellipseColor, label: "Axis-aligned covariance", kind: .line), errorLegend],
        evaluate: { points, values, _, _ in
            let fit = fitGaussianNb(points, smoothing: Double(Float(values[0])))
            let wrong = errorsOf(points, fit.decision)
            let qdaWrong = errorsOf(points, fitQda(points).decision).count
            let verdict = wrong.count > qdaWrong ? "\(plural(wrong.count - qdaWrong, "extra error")). The independence assumption is false and you can see exactly where."
                : wrong.count < qdaWrong ? "nothing: it is \(plural(qdaWrong - wrong.count, "error")) ahead of QDA, because fewer parameters from the same data is often the better trade."
                : "nothing. The assumption is false, yet the predictions are unaffected, which is why naive Bayes keeps working."
            return SurfaceResult(
                decision: fit.decision,
                readout: "NB = \(points.count - wrong.count)/\(points.count) · QDA = \(points.count - qdaWrong)/\(points.count) · params = 6",
                headline: "The ellipses can't tilt, because naive Bayes has no parameter for it.",
                highlight: "can't tilt",
                detail: "It fits 6 parameters to QDA's 11, and here that costs " + verdict,
                errors: wrong, ellipses: [fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)])
        })
}

private let surfaceConfigs: [String: () -> SurfaceConfig] = [
    "gaussian_nb": gaussianNb, "svm_rbf": svmRbf, "nu_svc": nuSvc, "lda": lda, "qda": qda, "passive_aggressive": passiveAggressive,
]

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class SurfaceModel {
    var seed: Int32 = 5
    var variant = 0
    var values: [Double]
    /// Stream labs only: step = examples seen. Opens on the finished pass; play restarts it.
    let playback = PlaybackState(stepCount: 1, speedMs: 450)
    var points: [ClassPoint] = []
    var result: SurfaceResult?

    init(values: [Double]) { self.values = values }
}

struct DecisionSurfaceLab: View {
    private let config: SurfaceConfig
    @State private var model: SurfaceModel
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        let c = (surfaceConfigs[topicId] ?? svmRbf)()
        config = c
        _model = State(initialValue: SurfaceModel(values: c.sliders.map(\.initial)))
    }

    private struct Key: Equatable { let seed: Int32; let variant: Int; let values: [Double]; let step: Int }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: config.intro, bottom: 12)
            if !config.variants.isEmpty {
                ChipPicker(options: config.variants.indices.map { ($0, config.variants[$0]) }, selection: $model.variant)
                    .padding(.bottom, 14)
            }
            LabCard {
                ZStack {
                    if let result = model.result {
                        SurfacePlane(points: model.points, result: result)
                    } else {
                        ProgressView()
                    }
                }
                .aspectRatio(planeAspect, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                if !config.legend.isEmpty {
                    FlowLayout(spacing: 16, lineSpacing: 6) {
                        ForEach(Array(config.legend.enumerated()), id: \.offset) { _, mark in LegendMarkView(mark: mark) }
                    }
                    .padding(.top, 12)
                }
                if dock == nil, let result = model.result {
                    ReadoutChips(text: result.readout).padding(.top, 16)
                    SurfaceNarration(result: result).padding(.top, 14)
                    Divider().padding(.top, 16)
                    SurfaceControls(config: config, model: model, docked: false).padding(.top, 14)
                }
            }
            if dock != nil, let result = model.result {
                ReadoutChips(text: result.readout).padding(.top, 14)
                SurfaceNarration(result: result).padding(.horizontal, 4).padding(.top, 16)
            }
        }
        .onAppear {
            guard let dock else { return }
            let config = config, model = model
            dock.controls = { AnyView(SurfaceControls(config: config, model: model, docked: true)) }
            if !config.stream {
                dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "New data") { model.seed += 1 }
            }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
            model.playback.playing = false
        }
        .task(id: Key(seed: model.seed, variant: model.variant, values: model.values,
                      step: config.stream ? model.playback.index : 0)) {
            let config = config, seed = model.seed, values = model.values, variant = model.variant
            var pts = model.points
            if pts.isEmpty || seed != loadedSeed {
                pts = await Task.detached(priority: .userInitiated) { config.data(seed) }.value
                if Task.isCancelled { return }
                loadedSeed = seed
                model.points = pts
                model.playback.load(stepCount: pts.count + 1)
                model.playback.index = model.playback.lastIndex
            }
            let step = config.stream ? model.playback.index : 0
            // Sliders move fast; let the latest value settle before running an SMO fit.
            try? await Task.sleep(for: .milliseconds(config.stream ? 0 : 60))
            if Task.isCancelled { return }
            let r = await Task.detached(priority: .userInitiated) { config.evaluate(pts, values, variant, step) }.value
            if Task.isCancelled { return }
            model.result = r
        }
    }

    @State private var loadedSeed: Int32?
}

private let planeAspect: CGFloat = 1.5

private struct SurfaceControls: View {
    let config: SurfaceConfig
    @Bindable var model: SurfaceModel
    let docked: Bool

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 14) {
                ForEach(config.sliders.indices, id: \.self) { i in
                    let s = config.sliders[i]
                    LabParamSlider(name: s.name, symbol: s.symbol, value: $model.values[i], range: s.range, format: s.format)
                }
            }
            if config.stream {
                Divider().padding(.vertical, 14)
                LabTransportBar(state: model.playback, captions: nil,
                                stepLabel: { "Example \($0) of \(model.points.count)" },
                                trailing: ("New Data", { model.seed += 1 }))
            } else if !docked {
                SimButtonRow(buttons: [("↻ New Data", SimColors.grey, { model.seed += 1 })]).padding(.top, 14)
            }
        }
    }
}

private struct SurfaceNarration: View {
    let result: SurfaceResult
    @Environment(\.palette) private var palette

    var body: some View {
        let key = palette.dark ? SimColors.active : Color(hex: 0xB45309)
        VStack(alignment: .leading, spacing: 8) {
            headline(key)
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Text(result.detail)
                .font(AppFont.sans(15))
                .foregroundStyle(palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func headline(_ key: Color) -> Text {
        let h = result.headline
        guard let hl = result.highlight, let r = h.range(of: hl) else { return Text(h) }
        return Text(h[..<r.lowerBound]) + Text(h[r]).foregroundColor(key) + Text(h[r.upperBound...])
    }
}

private struct LegendMarkView: View {
    let mark: LegendMark
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 6) {
            switch mark.kind {
            case .ring: Circle().stroke(mark.color, lineWidth: 2).frame(width: 10, height: 10)
            case .dot: Circle().fill(mark.color).frame(width: 11, height: 11)
            case .line: Capsule().fill(mark.color).frame(width: 14, height: 3)
            }
            Text(mark.label).font(AppFont.sans(14)).foregroundStyle(palette.onSurface.opacity(0.8))
        }
    }
}

private let gridX = 60
private let gridY = 40

private struct SurfacePlane: View {
    let points: [ClassPoint]
    let result: SurfaceResult
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in draw(ctx, size) }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        // Data bounds plus a margin, then widened on one axis so x and y share a scale — rings stay round.
        let m: Float = 0.6
        var xMin = (points.map(\.x).min() ?? -1) - m, xMax = (points.map(\.x).max() ?? 1) + m
        var yMin = (points.map(\.y).min() ?? -1) - m, yMax = (points.map(\.y).max() ?? 1) + m
        let aspect = Float(size.width / max(size.height, 1))
        let xr = xMax - xMin, yr = yMax - yMin
        if xr / yr < aspect {
            let grow = (yr * aspect - xr) / 2; xMin -= grow; xMax += grow
        } else {
            let grow = (xr / aspect - yr) / 2; yMin -= grow; yMax += grow
        }
        func sx(_ x: Float) -> CGFloat { CGFloat((x - xMin) / (xMax - xMin)) * size.width }
        func sy(_ y: Float) -> CGFloat { size.height - CGFloat((y - yMin) / (yMax - yMin)) * size.height }

        // The decision function at every grid node; cells are tinted by the sign at their centre.
        var nodes = [[Double]](repeating: [Double](repeating: 0, count: gridY + 1), count: gridX + 1)
        for gx in 0...gridX {
            for gy in 0...gridY {
                nodes[gx][gy] = result.decision(xMin + (xMax - xMin) * Float(gx) / Float(gridX),
                                                yMin + (yMax - yMin) * Float(gy) / Float(gridY))
            }
        }
        let cw = size.width / CGFloat(gridX), ch = size.height / CGFloat(gridY)
        var pos = Path(), neg = Path()
        for gx in 0..<gridX {
            for gy in 0..<gridY {
                let centre = nodes[gx][gy] + nodes[gx + 1][gy] + nodes[gx][gy + 1] + nodes[gx + 1][gy + 1]
                let rect = CGRect(x: CGFloat(gx) * cw, y: size.height - CGFloat(gy + 1) * ch, width: cw + 0.5, height: ch + 0.5)
                if centre >= 0 { pos.addRect(rect) } else { neg.addRect(rect) }
            }
        }
        ctx.fill(pos, with: .color(positiveFill.opacity(0.16)))
        ctx.fill(neg, with: .color(negativeFill.opacity(0.16)))

        let at: (CGFloat, CGFloat) -> CGPoint = { CGPoint(x: $0 * cw, y: size.height - $1 * ch) }
        if let band = result.marginBand {
            for level in [band, -band] {
                ctx.stroke(contourPath(nodes, level: level, at: at), with: .color(palette.primary.opacity(0.6)),
                           style: StrokeStyle(lineWidth: 1.2, dash: [5, 4]))
            }
        }
        ctx.stroke(contourPath(nodes, level: 0, at: at), with: .color(palette.primary), lineWidth: 3)

        for ellipse in result.ellipses {
            var path = Path()
            for (i, p) in ellipse.enumerated() {
                let pt = CGPoint(x: sx(p.0), y: sy(p.1))
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            ctx.stroke(path, with: .color(ellipseColor), lineWidth: 2)
        }

        let r: CGFloat = 6.5
        let unseenFrom = result.unseenFrom ?? points.count
        for (i, p) in points.enumerated() {
            let c = CGPoint(x: sx(p.x), y: sy(p.y))
            let fill = p.label > 0 ? positiveFill : negativeFill
            let dot = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
            if i >= unseenFrom {
                ctx.fill(dot, with: .color(fill.opacity(0.3)))
                continue
            }
            ctx.fill(dot, with: .color(fill))
            ctx.stroke(dot, with: .color(palette.surface), lineWidth: 1.5)
            let halo = Path(ellipseIn: CGRect(x: c.x - r - 4, y: c.y - r - 4, width: (r + 4) * 2, height: (r + 4) * 2))
            if result.ringed.contains(i) {
                ctx.stroke(halo, with: .color(supportRing), lineWidth: 2.5)
            } else if result.errors.contains(i) {
                ctx.stroke(halo, with: .color(errorRing), lineWidth: 2.5)
            }
        }
    }
}

/// Marching squares: the `level` contour of `nodes` as polylines. Segments are chained end to end so
/// a dashed stroke flows along the curve instead of restarting in every cell.
private func contourPath(_ nodes: [[Double]], level: Double, at: (CGFloat, CGFloat) -> CGPoint) -> Path {
    let gx = nodes.count - 1, gy = nodes[0].count - 1
    func lerp(_ a: Double, _ b: Double) -> CGFloat { CGFloat(min(max((level - a) / (b - a), 0), 1)) }
    // Crossings keyed by edge, so neighbouring cells share an endpoint exactly.
    var points: [Int: CGPoint] = [:]
    func hEdge(_ x: Int, _ y: Int) -> Int { // (x,y)→(x+1,y)
        let key = (x * (gy + 1) + y) * 2
        if points[key] == nil { points[key] = at(CGFloat(x) + lerp(nodes[x][y], nodes[x + 1][y]), CGFloat(y)) }
        return key
    }
    func vEdge(_ x: Int, _ y: Int) -> Int { // (x,y)→(x,y+1)
        let key = (x * (gy + 1) + y) * 2 + 1
        if points[key] == nil { points[key] = at(CGFloat(x), CGFloat(y) + lerp(nodes[x][y], nodes[x][y + 1])) }
        return key
    }
    var segments: [(Int, Int)] = []
    for x in 0..<gx {
        for y in 0..<gy {
            let a = nodes[x][y] >= level, b = nodes[x + 1][y] >= level
            let c = nodes[x + 1][y + 1] >= level, d = nodes[x][y + 1] >= level
            var crossed: [Int] = []
            if a != b { crossed.append(hEdge(x, y)) }
            if b != c { crossed.append(vEdge(x + 1, y)) }
            if c != d { crossed.append(hEdge(x, y + 1)) }
            if d != a { crossed.append(vEdge(x, y)) }
            if crossed.count >= 2 { segments.append((crossed[0], crossed[1])) }
            if crossed.count == 4 { segments.append((crossed[2], crossed[3])) }
        }
    }
    var byEnd: [Int: [Int]] = [:]
    for (i, s) in segments.enumerated() {
        byEnd[s.0, default: []].append(i)
        byEnd[s.1, default: []].append(i)
    }
    var used = [Bool](repeating: false, count: segments.count)
    func walk(_ from: Int) -> [Int] {
        var line = [from], end = from
        while let next = byEnd[end]?.first(where: { !used[$0] }) {
            used[next] = true
            let (p, q) = segments[next]
            end = p == end ? q : p
            line.append(end)
        }
        return line
    }
    var path = Path()
    for i in segments.indices where !used[i] {
        used[i] = true
        let (p, q) = segments[i]
        // Extend both ways from this segment, then stitch into one polyline.
        let line = Array(walk(p).reversed()) + walk(q)
        guard let first = line.first, let start = points[first] else { continue }
        path.move(to: start)
        for k in line.dropFirst() { if let pt = points[k] { path.addLine(to: pt) } }
    }
    return path
}
