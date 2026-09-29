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
private let unseenColor = Color(hex: 0x6B7280)

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
    /// The storyboard layout's chips; the older layout reads `readout`.
    var chips: [LabChip] = []
    /// Another model's boundary drawn thin and grey for comparison (LDA's line under QDA, the boundary
    /// before a Passive-Aggressive update).
    var reference: ((Float, Float) -> Double)? = nil
    /// The arithmetic of the step, in a strip under the plane.
    var formula: String? = nil
}

private struct SurfaceSlider {
    let name: String
    let symbol: String
    let range: ClosedRange<Double>
    let initial: Double
    var format: (Double) -> String = { String(format: "%.2f", $0) }
    /// Storyboard layout: the stepper's increment and the picker label.
    var step: Double = 0.05
    var tab: String? = nil
}

private struct SurfaceConfig {
    let intro: String
    let data: (Int32) -> [ClassPoint]
    let sliders: [SurfaceSlider]
    var variants: [String] = []
    var legend: [LegendMark] = []
    /// Streams the points in one at a time; the step is how many have been seen.
    var stream = false
    /// The redesigned layout: chips and a story headline over a picker and one stepper, in place of the
    /// readout and sliders. Opt-in, so the other surface labs render unchanged.
    var story = false
    // Story options for QDA, Gaussian NB and Passive-Aggressive; the defaults keep the other story labs as
    // they are. `storyLegend` replaces the class-dot legend; `solidEllipses` draws covariances as solid
    // lines; `greyUnseen` draws not-yet-streamed points grey; `solidMargins` draws ±1 as thin solid lines;
    // `startStep` is where a stream opens; `navReset` makes the nav button a reset instead of new data.
    var storyLegend: [(color: Color, style: SwatchStyle, label: String)]? = nil
    var solidEllipses = false
    var greyUnseen = false
    var solidMargins = false
    var startStep: Int? = nil
    var initialVariant = 0
    var navReset = false
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
            SurfaceSlider(name: "Reach", symbol: "log₁₀ γ", range: -2...1.2, initial: -0.4, format: { String(format: "%.1f", $0) }, step: 0.2, tab: "Reach γ"),
            SurfaceSlider(name: "Penalty", symbol: "log₁₀ C", range: -1...3, initial: 1, format: { String(format: "%.1f", $0) }, step: 0.5, tab: "Penalty C"),
        ],
        legend: [supportLegend, errorLegend],
        story: true,
        evaluate: { points, values, _, _ in
            let gamma = pow(10, Double(Float(values[0]))), c = pow(10, Double(Float(values[1])))
            let fit = trainSvm(points, kernel: rbfKernel(gamma), c: c)
            let wrong = fit.misclassified()
            let svs = fit.supportVectors.count
            let chips = [
                LabChip(key: "correct", value: "\(points.count - wrong.count)/\(points.count)", tone: wrong.isEmpty ? .idle : .warn, good: wrong.isEmpty),
                LabChip(key: "SVs", value: "\(svs)"),
                LabChip(key: "γ", value: f2(gamma)),
            ]
            let (headline, detail): (String, String) =
                gamma > 3 ? ("γ is large, so the boundary breaks into {w:islands} around single points.",
                             "Each support vector's reach has shrunk to almost nothing. That is memorization, and it will generalize badly.")
                : gamma < 0.05 ? ("γ is small, so the kernel behaves {almost linearly}.",
                                  "Every point influences everywhere, and nothing that smooth can split two concentric rings.")
                : ("The boundary is a {closed curve}, something no linear model can draw.",
                   "Only the \(svs) ringed points define it. The kernel compares pairs of points and never computes the higher-dimensional coordinates.")
            return SurfaceResult(decision: { fit.decision($0, $1) }, readout: "", headline: headline, detail: detail,
                                 ringed: fit.supportVectors, errors: wrong, marginBand: 1, chips: chips)
        })
}

private func nuSvc() -> SurfaceConfig {
    SurfaceConfig(
        intro: "ν replaces C with something you can actually reason about: it simultaneously upper-bounds the fraction of margin errors and lower-bounds the fraction of support vectors. Both are measured below.",
        data: { overlappingBlobs(seed: $0, separation: 1.15) },
        sliders: [SurfaceSlider(name: "Budget", symbol: "ν", range: 0.05...0.8, initial: 0.3, step: 0.05)],
        legend: [supportLegend, errorLegend],
        story: true,
        evaluate: { points, values, _, _ in
            let nu = Double(Float(values[0]))
            let r = trainNuSvc(points, kernel: linearKernel(), nu: nu)
            let holds = r.marginErrorFraction <= nu + 0.06 && r.svFraction >= nu - 0.06
            return SurfaceResult(
                decision: { r.fit.decision($0, $1) },
                readout: "",
                headline: holds
                    ? "{The bound holds}: margin errors \(f2(r.marginErrorFraction)) ≤ ν \(f2(nu)) ≤ support vectors \(f2(r.svFraction))."
                    : "Margin errors \(f2(r.marginErrorFraction)) and support vectors \(f2(r.svFraction)) sit {w:just outside} ν = \(f2(nu)).",
                detail: holds
                    ? "That guarantee is what ν gives you. C gives nothing comparable, which is why it needs a grid search."
                    : "The bound is asymptotic, so on \(points.count) points it can miss slightly. The equivalent C here is \(f3(r.c)).",
                ringed: r.fit.supportVectors, errors: r.fit.misclassified(), marginBand: 1,
                chips: [
                    LabChip(key: "margin err", value: f2(r.marginErrorFraction)),
                    LabChip(key: "ν", value: f2(nu), dot: supportRing),
                    LabChip(key: "SVs", value: f2(r.svFraction)),
                ])
        })
}

private func lda() -> SurfaceConfig {
    SurfaceConfig(
        intro: "Two classes with genuinely different spreads. LDA assumes they share one covariance, so it pools them — and the boundary it produces is always a straight line.",
        data: { unequalCovarianceBlobs(seed: $0) },
        sliders: [SurfaceSlider(name: "Shrinkage", symbol: "", range: 0...0.95, initial: 0, step: 0.05)],
        legend: [LegendMark(color: ellipseColor, label: "Shared covariance", kind: .ring), errorLegend],
        story: true,
        evaluate: { points, values, _, _ in
            let shrink = Double(Float(values[0]))
            let fit = fitLda(points, shrink: shrink)
            let wrong = errorsOf(points, fit.decision)
            return SurfaceResult(
                decision: fit.decision,
                readout: "",
                headline: "Both ellipses are {the same shape}, so the boundary is a straight line.",
                detail: (wrong.isEmpty ? "On this sample the shared shape costs nothing. Tap new data until it does."
                    : "The shared shape is too narrow for the stretched pink class. The \(plural(wrong.count, "red-ringed point")) are the cost.")
                    + (shrink > 0 ? " Shrinkage pulls the shared shape toward a circle." : ""),
                errors: wrong, ellipses: [fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)],
                chips: [
                    LabChip(key: "correct", value: "\(points.count - wrong.count)/\(points.count)", good: true),
                    LabChip(key: "misclassified", value: "\(wrong.count)", dot: errorRing),
                ])
        })
}

private func qda() -> SurfaceConfig {
    SurfaceConfig(
        intro: "The identical data as LDA, with the shared-covariance assumption dropped. Each class estimates its own, and the boundary stops being a line. λ blends each class's shape toward the shared one: 0 is QDA, 1 is LDA.",
        data: { unequalCovarianceBlobs(seed: $0) },
        sliders: [SurfaceSlider(name: "Shrinkage", symbol: "λ", range: 0...1, initial: 0.32, step: 0.04)],
        story: true,
        storyLegend: [(ellipseColor, .line, "Class covariance"), (ellipseColor, .dashedLine, "LDA boundary"), (errorRing, .ring, "Misclassified")],
        solidEllipses: true,
        navReset: true,
        evaluate: { points, values, _, _ in
            let lambda = Double(Float(values[0]))
            let fit = fitRda(points, lambda: lambda)
            let lda = fitLda(points)
            let n = points.count
            let wrong = errorsOf(points, fit.decision)
            let right = n - wrong.count
            let qdaRight = n - errorsOf(points, fitQda(points).decision).count
            let ldaRight = n - errorsOf(points, lda.decision).count
            let l = f2(lambda)
            let (headline, detail): (String, String) =
                lambda < 1e-4 ? ("At λ = 0 each class keeps {its own shape}: this is plain QDA.",
                                 "LDA shares one shape and gets \(ldaRight)/\(n). Raise λ when a class has too few points to trust its own covariance.")
                : lambda > 1 - 1e-4 ? ("At λ = 1 both classes share {one shape}, so the boundary is LDA's straight line.",
                                       "That gets \(ldaRight)/\(n) against QDA's \(qdaRight). Lower λ to let each class keep its own covariance.")
                : lambda < 0.5 ? ("At λ = \(l) each class keeps most of its {own shape}, so the boundary still curves.",
                                  "LDA shares one shape and gets \(ldaRight)/\(n). Raise λ when a class has too few points to trust its own covariance.")
                : ("At λ = \(l) both shapes are pulled toward {one shared shape}, so the boundary straightens.",
                   "This blend gets \(right)/\(n), QDA \(qdaRight) and LDA \(ldaRight). λ trades a flexible boundary for a steadier estimate.")
            return SurfaceResult(
                decision: fit.decision, readout: "", headline: headline, detail: detail,
                errors: wrong, ellipses: [fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)],
                chips: [
                    LabChip(key: "correct", value: "\(right)/\(n)", good: true),
                    LabChip(key: "QDA λ=0", value: "\(qdaRight)/\(n)"),
                    LabChip(key: "LDA λ=1", value: "\(ldaRight)/\(n)"),
                ],
                reference: lda.decision)
        })
}

private func passiveAggressive() -> SurfaceConfig {
    SurfaceConfig(
        intro: "One pass over a stream, one example at a time. Example 45 is deliberately mislabelled — step past it and watch what each variant does to a boundary that was already correct.",
        data: { streamData(seed: $0) },
        sliders: [SurfaceSlider(name: "Aggressiveness", symbol: "C", range: 0.05...3, initial: 0.25, step: 0.05)],
        variants: ["Hard", "PA-I", "PA-II"],
        stream: true,
        story: true,
        storyLegend: [(supportRing, .ring, "Current"), (ellipseColor, .dashedLine, "Before update"), (unseenColor, .dot, "Not seen yet")],
        greyUnseen: true,
        solidMargins: true,
        startStep: 23,
        initialVariant: 1,
        navReset: true,
        evaluate: { points, values, variantIndex, step in
            let seen = min(max(step, 0), points.count)
            let c = Double(Float(values[0]))
            let variant: PaVariant = [.hard, .paI, .paII][min(max(variantIndex, 0), 2)]
            let path = passiveAggressivePath(points, variant: variant, aggressiveness: c)
            let s = path[min(seen, path.count - 1)]
            let before = path[min(max(seen - 1, 0), path.count - 1)]
            let decision: (Float, Float) -> Double = { s.w1 * Double($0) + s.w2 * Double($1) + s.bias }
            let loss = s.lastLoss, tau = s.lastTau
            let p: ClassPoint? = seen >= 1 ? points[seen - 1] : nil
            let norm = p.map { Double($0.x) * Double($0.x) + Double($0.y) * Double($0.y) + 1 } ?? 1
            let raw = loss / norm
            let formula: String? =
                seen == 0 ? nil
                : loss == 0 ? "ℓ = max(0, 1 − y·f(x)) = 0, so τ = {v:0}"
                : variant == .hard ? "τ = ℓ / ‖x‖² = \(f2(loss)) / \(f2(norm)) = {v:\(f2(tau))}"
                : variant == .paI ? "τ = min(C, ℓ / ‖x‖²) = min(\(f2(c)), \(f2(loss)) / \(f2(norm))) = {v:\(f2(tau))}"
                : "τ = ℓ / (‖x‖² + 1/2C) = \(f2(loss)) / (\(f2(norm)) + \(f2(1 / (2 * c)))) = {v:\(f2(tau))}"
            let wrongSide = p.map { Double($0.label) * (before.w1 * Double($0.x) + before.w2 * Double($0.y) + before.bias) <= 0 } ?? false
            let place = wrongSide ? "On the wrong side" : "Inside the margin"
            let headline: String, detail: String
            if seen == 0 {
                headline = "No examples yet, so the line {hasn't been placed}."
                detail = "Next Example streams the \(points.count) examples in one at a time."
            } else if loss == 0 {
                headline = "This example is {outside the margin}, so τ = 0 and the line stays put."
                detail = "That is the passive half: a confident, correct example teaches nothing."
            } else if s.lastIndex == 44 {
                switch variant {
                case .hard:
                    headline = "The {mislabelled} example forces a step of τ = \(f2(tau))."
                    detail = "Hard PA has no cap, so it moves as far as it takes to fit this one point and wrecks a boundary that was already right."
                case .paI:
                    headline = "The {mislabelled} example is capped at τ = \(f2(tau))."
                    detail = "PA-I clips the step at C = \(f2(c)), so one bad label can only do bounded damage."
                case .paII:
                    headline = "The {mislabelled} example is softened to τ = \(f2(tau))."
                    detail = "PA-II adds 1/2C to the denominator, shrinking the step smoothly with no hard cutoff."
                }
            } else if variant == .paI && raw > c + 1e-9 {
                let share = tau / raw
                let far = share >= 0.45 && share <= 0.55 ? "half as far" : "\(Int((share * 100).rounded()))% as far"
                headline = "\(place), the loss asks for τ = \(f2(raw)), but PA-I {caps it at C}."
                detail = "So the line moves \(far) as Hard PA would. A lower C lets noisy examples move it less."
            } else if variant == .paII {
                headline = "\(place), PA-II softens the step to τ = \(f2(tau)) {instead of \(f2(raw))}."
                detail = "The 1/2C term shrinks every step smoothly. A lower C shrinks it more."
            } else {
                headline = "\(place), so the line moves {just far enough} to put this example on the margin."
                detail = "That is the aggressive half: τ = \(f2(tau)). \(plural(s.updates, "update")) over \(seen) examples, \(plural(s.mistakes, "outright mistake"))."
            }
            return SurfaceResult(
                decision: decision, readout: "", headline: headline, detail: detail,
                ringed: seen >= 1 && seen <= points.count ? [seen - 1] : [], marginBand: 1, unseenFrom: seen,
                chips: [
                    LabChip(key: "example", value: "\(seen) / \(points.count)"),
                    LabChip(key: "hinge", value: f2(loss), tint: .active),
                    LabChip(key: "τ", value: f2(tau), tint: .answer),
                ],
                reference: seen >= 1 && loss > 0 ? { before.w1 * Double($0) + before.w2 * Double($1) + before.bias } : nil,
                formula: formula)
        })
}

/// var_smoothing as sklearn has it: 10^e times the largest feature variance, added to every variance.
private func smoothingLabel(_ e: Double) -> String {
    let i = Int(e.rounded())
    return i == 0 ? "1" : "1e\(i)"
}

private func gaussianNb() -> SurfaceConfig {
    SurfaceConfig(
        intro: "Gaussian naive Bayes is QDA with the off-diagonal covariance terms forced to zero. \"Features are independent given the class\" is not an abstraction here — it is visible as ellipses that cannot tilt.",
        data: { unequalCovarianceBlobs(seed: $0) },
        sliders: [SurfaceSlider(name: "Smoothing", symbol: "var", range: -9...0, initial: -9, format: smoothingLabel, step: 1)],
        story: true,
        storyLegend: [(ellipseColor, .line, "Axis-aligned covariance"), (ellipseColor, .dashedLine, "QDA boundary"), (errorRing, .ring, "Misclassified")],
        solidEllipses: true,
        navReset: true,
        evaluate: { points, values, _, _ in
            let e = Int(values[0].rounded())
            let qda = fitQda(points)
            let maxVar = [qda.negative, qda.positive].map { max($0.covariance[0][0], $0.covariance[1][1]) }.max() ?? 1
            let fit = fitGaussianNb(points, smoothing: pow(10, Double(e)) * maxVar)
            let n = points.count
            let wrong = errorsOf(points, fit.decision)
            let right = n - wrong.count
            let qdaRight = n - errorsOf(points, qda.decision).count
            let cost = right < qdaRight
                ? "That costs \(qdaRight - right == 1 ? "one point" : "\(qdaRight - right) points") against QDA: \(right)/\(n) against \(qdaRight). The independence assumption is wrong here, but it barely moves the boundary."
                : right > qdaRight ? "It even beats QDA, \(right)/\(n) against \(qdaRight): fewer parameters from the same data can be the better trade."
                : "Here it costs nothing: both get \(right)/\(n). The independence assumption is wrong, yet the predictions survive."
            let (headline, detail): (String, String) = e >= -2
                ? ("Smoothing of \(smoothingLabel(Double(e))) {swells both ellipses}, so the boundary drifts.",
                   "Variance smoothing only guards against a zero variance. This much reshapes the classes: \(right)/\(n) against QDA's \(qdaRight).")
                : ("The pink ellipse {can't tilt}, because naive Bayes has no parameter for it.", cost)
            return SurfaceResult(
                decision: fit.decision, readout: "", headline: headline, detail: detail,
                errors: wrong, ellipses: [fit.negative.ellipse(1.6), fit.positive.ellipse(1.6)],
                chips: [
                    LabChip(key: "NB", value: "\(right)/\(n)", good: true),
                    LabChip(key: "QDA", value: "\(qdaRight)/\(n)"),
                    LabChip(key: "params", value: "6 vs 11"),
                ],
                reference: qda.decision)
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
    /// Storyboard layout: the parameter the stepper is on.
    var selected = 0

    init(values: [Double], variant: Int = 0) {
        self.values = values
        self.variant = variant
    }
}

struct DecisionSurfaceLab: View {
    private let config: SurfaceConfig
    @State private var model: SurfaceModel
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        let c = (surfaceConfigs[topicId] ?? svmRbf)()
        config = c
        _model = State(initialValue: SurfaceModel(values: c.sliders.map(\.initial), variant: c.initialVariant))
    }

    private struct Key: Equatable { let seed: Int32; let variant: Int; let values: [Double]; let step: Int }

    var body: some View {
        content
        .onAppear {
            guard let dock else { return }
            let config = config, model = model
            dock.controls = { AnyView(config.story ? AnyView(StorySurfaceControls(config: config, model: model, docked: true))
                                                   : AnyView(SurfaceControls(config: config, model: model, docked: true))) }
            if config.navReset {
                dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { resetSurface(config, model) }
            } else if !config.stream {
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
                model.playback.index = min(config.startStep ?? model.playback.lastIndex, model.playback.lastIndex)
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

    @ViewBuilder private var content: some View {
        if config.story { storyContent } else { classicContent }
    }

    /// The redesigned layout: the plane and a legend of what is on it, chips, a story headline, then a
    /// picker over one stepper (docked in thumb reach when the screen has a dock).
    private var storyContent: some View {
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: config.intro, bottom: 12)
            if !config.variants.isEmpty {
                LabSegments(labels: config.variants, selected: $model.variant).padding(.bottom, 14)
            }
            LabCard {
                ZStack {
                    if let result = model.result {
                        SurfacePlane(points: model.points, result: result, dottedEllipses: !config.solidEllipses,
                                     greyUnseen: config.greyUnseen, solidMargins: config.solidMargins)
                    } else {
                        ProgressView()
                    }
                }
                .aspectRatio(planeAspect, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                if let result = model.result {
                    if let formula = result.formula { SurfaceFormula(text: formula).padding(.top, 12) }
                    if let legend = config.storyLegend {
                        StoryLegendRow(items: legend.filter { $0.label != errorLegend.label || !result.errors.isEmpty }).padding(.top, 14)
                    } else {
                        let marks = config.legend.filter { $0.label != errorLegend.label || !result.errors.isEmpty }
                        StoryLegendRow(items: [(negativeFill, .dot, "Class 0"), (positiveFill, .dot, "Class 1")]
                            + marks.map { ($0.color, SwatchStyle.ring, $0.label) }).padding(.top, 14)
                    }
                }
            }
            if let result = model.result {
                LabChips(chips: result.chips).padding(.top, 16)
                LabStoryNarration(headline: result.headline, body: result.detail).padding(.top, 16)
            }
            if dock == nil {
                Divider().padding(.top, 16)
                StorySurfaceControls(config: config, model: model, docked: false).padding(.top, 14)
            }
        }
    }

    private var classicContent: some View {
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

/// The storyboard layout's picker over one stepper, plus New Data when there is no dock to hold it.
private struct StorySurfaceControls: View {
    let config: SurfaceConfig
    @Bindable var model: SurfaceModel
    let docked: Bool

    var body: some View {
        VStack(spacing: 14) {
            LabParamControls(
                params: config.sliders.indices.map { i in
                    let s = config.sliders[i], v = model.values[i]
                    return LabParam(tab: s.tab ?? s.name, name: s.name, symbol: s.symbol, text: s.format(v),
                                    canDecrease: v > s.range.lowerBound + 1e-4, canIncrease: v < s.range.upperBound - 1e-4)
                },
                selected: $model.selected
            ) { i, delta in
                let s = config.sliders[i]
                let next = ((model.values[i] + Double(delta) * s.step) / s.step).rounded() * s.step
                model.values[i] = min(max(next, s.range.lowerBound), s.range.upperBound)
            }
            // A stream steps one example at a time with a labelled action in place of a transport.
            if config.stream {
                let playback = model.playback
                LabBackActionRow(action: playback.atEnd ? "Start Over" : "Next Example", backEnabled: playback.index > 0,
                                 onBack: { playback.stepBack() },
                                 onAction: { if playback.atEnd { playback.jump(to: 0) } else { playback.stepForward() } })
            }
            if !docked {
                if config.navReset {
                    SimButtonRow(buttons: [("↻ Reset", SimColors.grey, { resetSurface(config, model) })])
                } else {
                    SimButtonRow(buttons: [("↻ New Data", SimColors.grey, { model.seed += 1 })])
                }
            }
        }
    }
}

/// Back to the lab's opening state: the first sample, the initial values, variant and example.
@MainActor
private func resetSurface(_ config: SurfaceConfig, _ model: SurfaceModel) {
    model.seed = 5
    model.variant = config.initialVariant
    model.values = config.sliders.map(\.initial)
    model.playback.jump(to: config.startStep ?? model.playback.lastIndex)
}

/// The step's arithmetic, centred in a tinted strip; wraps to two lines when it has to.
private struct SurfaceFormula: View {
    let text: String
    @Environment(\.palette) private var palette

    var body: some View {
        storyText(text, palette)
            .font(AppFont.mono(13))
            .foregroundStyle(palette.onSurface.opacity(0.85))
            .multilineTextAlignment(.center)
            .lineLimit(2)
            .lineSpacing(3)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .padding(.horizontal, 12)
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
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
    var dottedEllipses = false
    var greyUnseen = false
    var solidMargins = false
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
                           style: StrokeStyle(lineWidth: 1.2, dash: solidMargins ? [] : [5, 4]))
            }
        }
        if let reference = result.reference {
            var refNodes = nodes
            for gx in 0...gridX {
                for gy in 0...gridY {
                    refNodes[gx][gy] = reference(xMin + (xMax - xMin) * Float(gx) / Float(gridX), yMin + (yMax - yMin) * Float(gy) / Float(gridY))
                }
            }
            ctx.stroke(contourPath(refNodes, level: 0, at: at), with: .color(ellipseColor.opacity(0.85)), lineWidth: 1.5)
        }
        ctx.stroke(contourPath(nodes, level: 0, at: at), with: .color(palette.primary), lineWidth: 3)

        for ellipse in result.ellipses {
            var path = Path()
            for (i, p) in ellipse.enumerated() {
                let pt = CGPoint(x: sx(p.0), y: sy(p.1))
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            if dottedEllipses {
                ctx.stroke(path, with: .color(ellipseColor.opacity(0.7)), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
            } else {
                ctx.stroke(path, with: .color(ellipseColor), lineWidth: 2)
            }
        }

        let r: CGFloat = 6.5
        let unseenFrom = result.unseenFrom ?? points.count
        for (i, p) in points.enumerated() {
            let c = CGPoint(x: sx(p.x), y: sy(p.y))
            let fill = p.label > 0 ? positiveFill : negativeFill
            let dot = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
            if i >= unseenFrom {
                if greyUnseen {
                    ctx.fill(dot, with: .color(unseenColor))
                    ctx.stroke(dot, with: .color(palette.surface), lineWidth: 1.5)
                } else {
                    ctx.fill(dot, with: .color(fill.opacity(0.3)))
                }
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
