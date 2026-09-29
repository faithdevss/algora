import SwiftUI

// Ports of RegressionSimulationSection.kt and ClassifierPlaygroundSection.kt.

func fmt2(_ v: Double) -> String { String(format: "%.2f", v) }

/// A labelled slider row with the value read out in mono.
struct LabSlider: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var step: Double? = nil
    var display: ((Double) -> String)? = nil
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(label).font(AppFont.sans(14, .semibold))
                Spacer()
                Text(display?(value) ?? fmt2(value)).font(.code).foregroundStyle(palette.primary)
            }
            if let step {
                Slider(value: $value, in: range, step: step).tint(palette.primary)
            } else {
                Slider(value: $value, in: range).tint(palette.primary)
            }
        }
        .padding(.top, 10)
    }
}

// MARK: - Regression explorer

// Slope and intercept drive a line over a scatter, each residual drawn as a stub, with MSE read out
// and a least-squares "Show Best Fit". Layout follows the parameter-lab pattern: stage card (plot +
// legend), readout chips, narration, then the sliders and buttons — pinned in thumb reach when
// docked. The narration reads the residuals to say which slider to move next.

private struct RegPoint: Hashable { let x: Double; let y: Double }

/// A fixed, seeded scatter that climbs faster than the starting line, so the lab opens on "the slope is
/// too flat" and Reset always returns to the same story (the same draws as the Android lab).
private let regressionPoints: [RegPoint] = {
    var r = KotlinRandom(3)
    return (0..<12).map { i in
        let x = Float(i) * 0.9 + r.nextFloat() * 0.4
        return RegPoint(x: Double(x), y: Double(0.55 * x + 0.9 + (r.nextFloat() * 2 - 1) * 0.9))
    }
}()

private func regressionMse(_ pts: [RegPoint], _ m: Double, _ c: Double) -> Double {
    pts.map { pow(m * $0.x + c - $0.y, 2) }.reduce(0, +) / Double(max(pts.count, 1))
}

private func leastSquares(_ pts: [RegPoint]) -> (Double, Double) {
    let n = Double(pts.count)
    guard n > 0 else { return (0, 0) }
    let sx = pts.reduce(0) { $0 + $1.x }, sy = pts.reduce(0) { $0 + $1.y }
    let sxy = pts.reduce(0) { $0 + $1.x * $1.y }, sxx = pts.reduce(0) { $0 + $1.x * $1.x }
    let denom = n * sxx - sx * sx
    guard denom != 0 else { return (0, sy / n) }
    let m = (n * sxy - sx * sy) / denom
    return (((m * 100).rounded()) / 100, (((sy - m * sx) / n * 100).rounded()) / 100)
}

/// Data points: pale on dark, a mid grey on light so they don't wash out.
private func regPointColor(_ palette: Palette) -> Color { palette.dark ? SimColors.idle : SimColors.grey }
private let regResidualColor = SimColors.red

private let startSlope = 0.35
private let startIntercept = 1.5
private let slopeRange = -3.0...3.0
private let interceptRange = -4.0...8.0

/// Steps `value` by `delta` × `step` on the step grid, within `range`.
private func stepped(_ value: Double, _ delta: Int, _ step: Double, _ range: ClosedRange<Double>) -> Double {
    min(max(((value + Double(delta) * step) / step).rounded() * step, range.lowerBound), range.upperBound)
}

/// What the residuals say about the line: its headline (with the key word marked) and the why.
private func regressionStory(_ pts: [RegPoint], _ m: Double, _ c: Double, _ mse: Double, _ bestMse: Double) -> (String, String) {
    let residuals = pts.map { $0.y - (m * $0.x + c) }
    let meanX = pts.map(\.x).reduce(0, +) / Double(max(pts.count, 1))
    // How residuals trend with x: positive means the points climb away from the line to the right.
    let drift = pts.indices.reduce(0) { $0 + (pts[$1].x - meanX) * residuals[$1] } / pts.reduce(0) { $0 + ($1.x - meanX) * ($1.x - meanX) }
    let right = pts.indices.filter { pts[$0].x > meanX }
    let rightAbove = right.filter { residuals[$0] > 0 }.count
    let rightBelow = right.count - rightAbove
    let body = "Each red stub is one residual, and MSE averages their squares. The best line reaches \(fmt2(bestMse))."
    if mse <= bestMse * 1.02 + 1e-3 {
        return ("This is the {m:best fit}: no other line has a lower MSE.",
                "Its MSE is \(fmt2(bestMse)). Tilt or shift the line from here and the squared residuals grow.")
    }
    if residuals.allSatisfy({ $0 > 0 }) { return ("Every point sits {above} your line, so the intercept is too low.", body) }
    if residuals.allSatisfy({ $0 < 0 }) { return ("Every point sits {below} your line, so the intercept is too high.", body) }
    if drift > 0.15 && rightAbove * 2 > right.count {
        return ("\(rightAbove) of \(right.count) points on the right sit {above} your line, so the slope is too flat.", body)
    }
    if drift < -0.15 && rightBelow * 2 > right.count {
        return ("\(rightBelow) of \(right.count) points on the right sit {below} your line, so the slope is too steep.", body)
    }
    let meanResidual = residuals.reduce(0, +) / Double(max(residuals.count, 1))
    return meanResidual > 0 ? ("Most points sit {above} your line, so the intercept is too low.", body)
                            : ("Most points sit {below} your line, so the intercept is too high.", body)
}

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class RegressionModel {
    var slope = startSlope
    var intercept = startIntercept
    var selected = 0
}

// The Linear Regression lab: slope and intercept drive a line over a scatter, each residual drawn as a
// stub. Chips read the line, its MSE and the best MSE; the headline reads the residuals to say what is
// wrong with the line; a Slope / Intercept picker over one stepper and Show Best Fit sit in thumb reach.
struct RegressionExplorerLab: View {
    @State private var model = RegressionModel()
    @Environment(\.labDock) private var dock

    var body: some View {
        let pts = regressionPoints
        let mse = regressionMse(pts, model.slope, model.intercept)
        let best = leastSquares(pts)
        let bestMse = regressionMse(pts, best.0, best.1)
        let (headline, detail) = regressionStory(pts, model.slope, model.intercept, mse, bestMse)
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: "Each residual is the vertical gap between a point and your line. Least squares picks the one line that makes the average squared residual, the MSE, as small as it can be.", bottom: 12)
            LabCard {
                RegressionPlot(points: pts, slope: model.slope, intercept: model.intercept)
                RegressionLegend().padding(.top, 12)
            }
            LabChips(chips: [
                LabChip(key: "y =", value: "\(fmt2(model.slope))x \(model.intercept < 0 ? "−" : "+") \(fmt2(abs(model.intercept)))"),
                LabChip(key: "MSE", value: fmt2(mse), tint: .path),
                LabChip(key: "best", value: fmt2(bestMse)),
            ]).padding(.top, 16)
            LabStoryNarration(headline: headline, body: detail).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                RegressionControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(RegressionControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") {
                model.slope = startSlope; model.intercept = startIntercept; model.selected = 0
            }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct RegressionControls: View {
    @Bindable var model: RegressionModel

    var body: some View {
        LabParamActionControls(
            params: [
                LabParam(tab: "Slope m", name: "Slope", symbol: "m", text: fmt2(model.slope),
                         canDecrease: model.slope > slopeRange.lowerBound + 1e-4, canIncrease: model.slope < slopeRange.upperBound - 1e-4),
                LabParam(tab: "Intercept c", name: "Intercept", symbol: "c", text: fmt2(model.intercept),
                         canDecrease: model.intercept > interceptRange.lowerBound + 1e-4, canIncrease: model.intercept < interceptRange.upperBound - 1e-4),
            ],
            selected: $model.selected,
            onStep: { i, delta in
                if i == 0 { model.slope = stepped(model.slope, delta, 0.05, slopeRange) }
                else { model.intercept = stepped(model.intercept, delta, 0.1, interceptRange) }
            },
            action: "Show Best Fit"
        ) {
            let (m, c) = leastSquares(regressionPoints)
            withAnimation { model.slope = m; model.intercept = c }
        }
    }
}

private struct RegressionLegend: View {
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 16) {
            item(Circle().fill(regPointColor(palette)).frame(width: 11, height: 11), "Data")
            item(Capsule().fill(palette.primary).frame(width: 14, height: 3), "Your line")
            item(Rectangle().fill(regResidualColor).frame(width: 2, height: 12), "Residual")
        }
    }

    private func item(_ swatch: some View, _ label: String) -> some View {
        HStack(spacing: 6) {
            swatch
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.onSurface.opacity(0.8))
        }
    }
}

private struct RegressionPlot: View {
    let points: [RegPoint]
    let slope: Double
    let intercept: Double
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in draw(ctx, size) }
            .aspectRatio(1.45, contentMode: .fit)
            .background(palette.onSurface.opacity(0.03))
            .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let pad: CGFloat = 18
        let xs = points.map(\.x)
        let xMin = (xs.min() ?? 0) - 0.3, xMax = (xs.max() ?? 1) + 0.3
        // The line always stays in view, so its endpoints count toward the y range.
        let ys = points.map(\.y) + [slope * xMin + intercept, slope * xMax + intercept]
        var yMin = ys.min() ?? 0, yMax = ys.max() ?? 1
        if yMax - yMin < 1 { yMax = yMin + 1 }
        let yr = yMax - yMin
        yMin -= yr * 0.08; yMax += yr * 0.08
        func px(_ x: Double) -> CGFloat { pad + CGFloat((x - xMin) / (xMax - xMin)) * (size.width - 2 * pad) }
        func py(_ y: Double) -> CGFloat { size.height - pad - CGFloat((y - yMin) / (yMax - yMin)) * (size.height - 2 * pad) }
        for i in 0...4 {
            let gy = pad + CGFloat(i) * (size.height - 2 * pad) / 4
            ctx.line(CGPoint(x: pad, y: gy), CGPoint(x: size.width - pad, y: gy), color: palette.outline.opacity(0.6), width: 1)
        }
        for p in points {
            ctx.line(CGPoint(x: px(p.x), y: py(p.y)), CGPoint(x: px(p.x), y: py(slope * p.x + intercept)),
                     color: regResidualColor.opacity(0.75), width: 1.5)
        }
        ctx.line(CGPoint(x: px(xMin), y: py(slope * xMin + intercept)), CGPoint(x: px(xMax), y: py(slope * xMax + intercept)),
                 color: palette.primary, width: 3)
        for p in points {
            let c = CGPoint(x: px(p.x), y: py(p.y))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - 6, y: c.y - 6, width: 12, height: 12)), with: .color(regPointColor(palette)))
        }
    }
}

// MARK: - Classifier playground

// Logistic regression and the linear SVM: the boundary is the zero-contour of w₁·x + w₂·y + b. The
// two share that line and differ only in readout (log loss vs margin + hinge loss). Layout follows
// the parameter-lab pattern: stage card (plane + legend), readout chips, narration, then the
// sliders — pinned in thumb reach when docked.

struct ClassifierPoint: Hashable { let x: Double; let y: Double; let target: Int }

struct ClassifierConfig {
    let points: [ClassifierPoint]
    let axis: ClosedRange<Double>
    /// SVM: draw the ±1 margins dashed and report margin + hinge loss instead of log loss.
    let margins: Bool

    static func forTopic(_ topicId: String) -> ClassifierConfig {
        switch topicId {
        case "svm": svm
        default: logistic
        }
    }

    /// Two seeded blobs.
    private static func blobs(seed: Int64) -> [ClassifierPoint] {
        var rng = KotlinRandom(Int32(seed))
        func cluster(_ cx: Float, _ cy: Float, _ target: Int) -> [ClassifierPoint] {
            (0..<8).map { _ in
                let x = cx + (rng.nextFloat() - 0.5) * 0.9
                let y = cy + (rng.nextFloat() - 0.5) * 0.9
                return ClassifierPoint(x: Double(x), y: Double(y), target: target)
            }
        }
        return cluster(-0.7, -0.7, 0) + cluster(0.7, 0.7, 1)
    }

    static let logistic = ClassifierConfig(points: blobs(seed: 7), axis: -1.5...1.5, margins: false)
    static let svm = ClassifierConfig(points: blobs(seed: 7), axis: -1.5...1.5, margins: true)
}

private let classZero = SimColors.blue
private let classOne = CategoryAccents.pink
private let classifierAspect: CGFloat = 1.45

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class ClassifierModel {
    var w = [1.0, 0.6, 0.4]
    /// The picker: which of w₁, w₂, b the stepper is on.
    var selected = 0

    init(w: [Double] = [1.0, 0.6, 0.4], selected: Int = 0) {
        self.w = w
        self.selected = selected
    }

    func score(_ p: ClassifierPoint, _ w: [Double]? = nil) -> Double {
        let w = w ?? self.w
        return w[0] * p.x + w[1] * p.y + w[2]
    }
    func predict(_ p: ClassifierPoint, _ w: [Double]? = nil) -> Int { score(p, w) >= 0 ? 1 : 0 }

    func logLoss(_ pts: [ClassifierPoint], _ w: [Double]? = nil) -> Double {
        pts.reduce(0) { acc, p in
            let prob = min(max(1 / (1 + exp(-score(p, w))), 1e-9), 1 - 1e-9)
            return acc - (p.target == 1 ? log(prob) : log(1 - prob))
        } / Double(pts.count)
    }
    func hingeLoss(_ pts: [ClassifierPoint], _ w: [Double]? = nil) -> Double {
        pts.reduce(0) { acc, p in acc + max(0, 1 - (p.target == 1 ? 1 : -1) * score(p, w)) } / Double(pts.count)
    }
}

private func snap1(_ v: Double) -> Double { (v * 10).rounded() / 10 }

struct ClassifierPlaygroundLab: View {
    let config: ClassifierConfig

    var body: some View {
        if config.margins { SvmStoryLab(config: config) } else { LogisticStoryLab(config: config) }
    }
}

// MARK: - Logistic regression storyboard
// The plane and a legend, chips (correct · log loss), a headline naming what is on the wrong side and how
// far the bias has to move, then a w₁ / w₂ / b picker, the value's stepper and Fit Line.

/// Opens with one point just across the line (the first bias from 0.4 up that leaves exactly one wrong),
/// so the first thing to fix is visible.
private let logisticStart: [Double] = {
    let pts = ClassifierConfig.logistic.points
    func wrong(_ b: Double) -> Int {
        pts.filter { p in
            let score: Double = p.x + 0.6 * p.y + b
            return (score >= 0 ? 1 : 0) != p.target
        }.count
    }
    let bias = (4...20).map { Double($0) / 10 }.first { wrong($0) == 1 } ?? 0.4
    return [1.0, 0.6, bias]
}()
private let paramSymbols = ["w₁", "w₂", "b"]

/// Gradient descent on the mean log loss (with a touch of L2, so separable data can't run off).
private func fitLogistic(_ start: [Double], _ pts: [ClassifierPoint]) -> [Double] {
    var w = start.map { Double(Float($0)) }
    for _ in 0..<3000 {
        var g = [0.0, 0.0, 0.0]
        for p in pts {
            let z = w[0] * p.x + w[1] * p.y + w[2]
            let err = 1 / (1 + exp(-z)) - Double(p.target)
            g[0] += err * p.x; g[1] += err * p.y; g[2] += err
        }
        for k in 0..<3 { w[k] -= 0.5 * (g[k] / Double(pts.count) + (k < 2 ? 0.02 * w[k] : 0)) }
    }
    return w.map { snap1(min(max($0, -3), 3)) }
}

private struct LogisticStoryLab: View {
    let config: ClassifierConfig
    @State private var model = ClassifierModel(w: logisticStart, selected: 2)
    @Environment(\.labDock) private var dock

    var body: some View {
        let pts = config.points
        let wrong = Set(pts.indices.filter { model.predict(pts[$0]) != pts[$0].target })
        let (headline, detail) = story(pts, wrong)
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: "Logistic regression scores each point with w₁·x + w₂·y + b and squashes the score into a probability. The line is where that probability is exactly 50%; tilt it with the weights and slide it with the bias.", bottom: 12)
            LabCard {
                ClassifierPlane(config: config, w: model.w, wrong: wrong)
                StoryLegendRow(items: [(classZero, .dot, "Class 0"), (classOne, .dot, "Class 1")]
                    + (wrong.isEmpty ? [] : [(SimColors.red, .ring, "Misclassified")])).padding(.top, 14)
            }
            LabChips(chips: [
                LabChip(key: "correct", value: "\(pts.count - wrong.count) / \(pts.count)", tint: .path),
                LabChip(key: "log loss", value: fmt2(model.logLoss(pts))),
            ]).padding(.top, 16)
            LabStoryNarration(headline: headline, body: detail).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                LogisticControls(config: config, model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model, config = config
            dock.controls = { AnyView(LogisticControls(config: config, model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.w = logisticStart; model.selected = 2 }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }

    private func story(_ pts: [ClassifierPoint], _ wrong: Set<Int>) -> (String, String) {
        let n = pts.count
        let lead = "The line is where the model is exactly 50% sure."
        if wrong.isEmpty {
            return ("{m:All \(n) correct.} The line splits the two classes.",
                    "\(lead) Fit Line keeps lowering log loss by pushing points further from it.")
        }
        if wrong.count == 1, let i = wrong.first {
            let p = pts[i]
            // How far the bias must move for this point's score to change sign, to one decimal.
            let gap = ((abs(Double(Float(model.score(p)))) + 0.05) * 10).rounded(.up) / 10
            return ("\(n - 1) of \(n) correct. {w:One \(p.target == 1 ? "pink" : "blue") point} sits just past the line.",
                    "\(lead) \(p.target == 1 ? "Raising" : "Lowering") b by about \(String(format: "%.1f", gap)) moves it back across.")
        }
        let headline = "\(n - wrong.count) of \(n) correct. {w:\(wrong.count) points} sit on the wrong side."
        if case let (param, up)? = bestNudge(pts) {
            return (headline, "\(lead) \(up ? "Raise" : "Lower") \(paramSymbols[param]) to \(param == 2 ? "slide" : "tilt") it toward them.")
        }
        return (headline, "\(lead) Fit Line finds the weights with the lowest log loss.")
    }

    /// The single ±0.2 move that fixes the most points, or nil when none fixes any.
    private func bestNudge(_ pts: [ClassifierPoint]) -> (Int, Bool)? {
        func errors(_ w: [Double]) -> Int { pts.filter { model.predict($0, w) != $0.target }.count }
        var best: (Int, Bool)?
        var bestWrong = errors(model.w), bestLoss = Double.greatestFiniteMagnitude
        for param in 0..<3 {
            for up in [true, false] {
                var t = model.w
                t[param] = snap1(t[param] + (up ? 0.2 : -0.2))
                let n = errors(t), l = model.logLoss(pts, t)
                if n < bestWrong || (n == bestWrong && best != nil && l < bestLoss) {
                    best = (param, up); bestWrong = n; bestLoss = l
                }
            }
        }
        return best
    }
}

private struct LogisticControls: View {
    let config: ClassifierConfig
    @Bindable var model: ClassifierModel

    var body: some View {
        let names = ["Weight", "Weight", "Bias"]
        LabParamActionControls(
            params: names.indices.map { i in
                let v = model.w[i]
                return LabParam(tab: "\(names[i]) \(paramSymbols[i])", name: names[i], symbol: paramSymbols[i], text: String(format: "%.1f", v),
                                canDecrease: v > -3 + 1e-4, canIncrease: v < 3 - 1e-4)
            },
            selected: $model.selected,
            onStep: { i, delta in model.w[i] = snap1(min(max(model.w[i] + Double(delta) * 0.1, -3), 3)) },
            action: "Fit Line"
        ) {
            model.w = fitLogistic(model.w, config.points)
        }
    }
}

// MARK: - Linear SVM storyboard
// The plane, a legend of what is ringed, chips (correct · margin · hinge), a story headline, then a
// w₁ / w₂ / b picker over one stepper in place of three sliders.

private struct SvmStoryLab: View {
    let config: ClassifierConfig
    @State private var model = ClassifierModel()
    @Environment(\.labDock) private var dock

    var body: some View {
        let pts = config.points
        let wrong = Set(pts.indices.filter { model.predict(pts[$0]) != pts[$0].target })
        let inside = Set(pts.indices.filter { !wrong.contains($0) && Double(pts[$0].target == 1 ? 1 : -1) * model.score(pts[$0]) < 1 })
        let norm = (model.w[0] * model.w[0] + model.w[1] * model.w[1]).squareRoot()
        let margin = norm > 1e-3 ? fmt2(2 / norm) : "—"
        let (headline, detail) = story(pts, wrong, inside, norm, margin)
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: "A linear SVM draws the same line as logistic regression, then asks for the widest empty band around it. The dashed lines sit where w₁·x + w₂·y + b = ±1, so shrinking the weights widens the band.", bottom: 12)
            LabCard {
                ClassifierPlane(config: config, w: model.w, wrong: wrong, ringed: inside, outlined: true)
                StoryLegendRow(items: [(classZero, .dot, "Class 0"), (classOne, .dot, "Class 1"), (SimColors.active, .ring, "Inside margin")]
                    + (wrong.isEmpty ? [] : [(SimColors.red, .ring, "Misclassified")])).padding(.top, 14)
            }
            LabChips(chips: [
                LabChip(key: "correct", value: "\(pts.count - wrong.count)/\(pts.count)", tone: wrong.isEmpty ? .idle : .warn, good: wrong.isEmpty),
                LabChip(key: "margin", value: margin),
                LabChip(key: "hinge", value: fmt2(model.hingeLoss(pts))),
            ]).padding(.top, 16)
            LabStoryNarration(headline: headline, body: detail).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                SvmStoryControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(SvmStoryControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.w = [1.0, 0.6, 0.4] }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }

    private func story(_ pts: [ClassifierPoint], _ wrong: Set<Int>, _ inside: Set<Int>, _ norm: Double, _ margin: String) -> (String, String) {
        if norm < 1e-3 { return ("With both weights at {w:zero} there is no line.", "Raise w₁ or w₂ to give the boundary a direction.") }
        if wrong.isEmpty {
            return ("All \(pts.count) points are on the right side. The margin between the dashed lines is {\(margin)} wide.",
                    inside.isEmpty ? "No point sits inside it, so hinge loss is 0. Shrink both weights to widen it until one does."
                        : "\(inside.count) point\(inside.count == 1 ? " sits" : "s sit") inside it and add hinge loss. Shrink both weights to widen the margin.")
        }
        var headline = "{w:\(wrong.count == 1 ? "One point is" : "\(wrong.count) points are")} on the wrong side."
        if case let (param, up)? = bestNudge(pts) {
            headline += " \(up ? "Raise" : "Lower") \(["w₁", "w₂", "b"][param]) to \(param == 2 ? "slide" : "tilt") the boundary."
        }
        return (headline, "Each misclassified point adds more than 1 to the hinge loss. The margin is \(margin) wide.")
    }

    /// The single ±0.2 move that fixes the most points, or nil when none fixes any.
    private func bestNudge(_ pts: [ClassifierPoint]) -> (Int, Bool)? {
        func errors(_ w: [Double]) -> Int { pts.filter { model.predict($0, w) != $0.target }.count }
        var best: (Int, Bool)?
        var bestWrong = errors(model.w), bestLoss = Double.greatestFiniteMagnitude
        for param in 0..<3 {
            for up in [true, false] {
                var t = model.w
                t[param] = snap1(t[param] + (up ? 0.2 : -0.2))
                let n = errors(t), l = model.hingeLoss(pts, t)
                if n < bestWrong || (n == bestWrong && best != nil && l < bestLoss) {
                    best = (param, up); bestWrong = n; bestLoss = l
                }
            }
        }
        return best
    }
}

private struct SvmStoryControls: View {
    @Bindable var model: ClassifierModel

    var body: some View {
        let symbols = ["w₁", "w₂", "b"]
        LabParamControls(
            params: ["Weight", "Weight", "Bias"].indices.map { i in
                let v = model.w[i]
                return LabParam(tab: symbols[i], name: ["Weight", "Weight", "Bias"][i], symbol: symbols[i], text: String(format: "%.1f", v),
                                canDecrease: v > -3 + 1e-4, canIncrease: v < 3 - 1e-4)
            },
            selected: $model.selected
        ) { i, delta in
            model.w[i] = snap1(min(max(model.w[i] + Double(delta) * 0.1, -3), 3))
        }
    }
}

private struct ClassifierPlane: View {
    let config: ClassifierConfig
    let w: [Double]
    let wrong: Set<Int>
    var ringed: Set<Int> = []
    var outlined = false
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in draw(ctx, size) }
            .aspectRatio(classifierAspect, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        // y spans the configured range; x is widened to the canvas aspect so both share a scale.
        let yLo = config.axis.lowerBound, yHi = config.axis.upperBound
        let xHalf = (yHi - yLo) * Double(size.width / size.height) / 2
        let xLo = -xHalf + (yLo + yHi) / 2, xHi = xHalf + (yLo + yHi) / 2
        func px(_ x: Double) -> CGFloat { CGFloat((x - xLo) / (xHi - xLo)) * size.width }
        func py(_ y: Double) -> CGFloat { size.height - CGFloat((y - yLo) / (yHi - yLo)) * size.height }

        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(classZero.opacity(0.16)))
        let plane = linearHalfPlane(w[0], w[1], w[2], size: size, x: xLo...xHi, y: yLo...yHi)
        if let region = plane.region { ctx.fill(region, with: .color(classOne.opacity(0.16))) }
        if config.margins {
            for level in [1.0, -1.0] {
                if case let (a, b)? = linearHalfPlane(w[0], w[1], w[2] - level, size: size, x: xLo...xHi, y: yLo...yHi).line {
                    var p = Path(); p.move(to: a); p.addLine(to: b)
                    ctx.stroke(p, with: .color(palette.primary.opacity(0.6)), style: StrokeStyle(lineWidth: 1.2, dash: [5, 4]))
                }
            }
        }
        if case let (a, b)? = plane.line { ctx.line(a, b, color: palette.primary, width: 3) }

        let r: CGFloat = 6.5
        for (i, p) in config.points.enumerated() {
            let c = CGPoint(x: px(p.x), y: py(p.y))
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)),
                     with: .color(p.target == 1 ? classOne : classZero))
            if outlined {
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(palette.surface), lineWidth: 1.5)
            }
            let ring = wrong.contains(i) ? SimColors.red : ringed.contains(i) ? SimColors.active : nil
            if let ring {
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r - 4, y: c.y - r - 4, width: (r + 4) * 2, height: (r + 4) * 2)),
                           with: .color(ring), lineWidth: 2.5)
            }
        }
    }
}

/// The side of w₁·x + w₂·y + b ≥ 0 clipped to a canvas whose corners sit at data coordinates
/// `x` × `y` (y up), and where its edge line crosses the canvas. Shared by the Perceptron and
/// classifier labs.
func linearHalfPlane(_ w1: Double, _ w2: Double, _ b: Double, size: CGSize,
                     x: ClosedRange<Double>, y: ClosedRange<Double>) -> (region: Path?, line: (CGPoint, CGPoint)?) {
    let corners: [(CGPoint, Double)] = [
        (CGPoint(x: 0, y: 0), w1 * x.lowerBound + w2 * y.upperBound + b),
        (CGPoint(x: size.width, y: 0), w1 * x.upperBound + w2 * y.upperBound + b),
        (CGPoint(x: size.width, y: size.height), w1 * x.upperBound + w2 * y.lowerBound + b),
        (CGPoint(x: 0, y: size.height), w1 * x.lowerBound + w2 * y.lowerBound + b),
    ]
    var region: [CGPoint] = []
    var crossings: [CGPoint] = []
    for i in corners.indices {
        let (a, sa) = corners[i]
        let (c, sc) = corners[(i + 1) % corners.count]
        if sa >= 0 { region.append(a) }
        if (sa >= 0) != (sc >= 0) {
            let t = CGFloat(sa / (sa - sc))
            let cross = CGPoint(x: a.x + (c.x - a.x) * t, y: a.y + (c.y - a.y) * t)
            region.append(cross)
            crossings.append(cross)
        }
    }
    var path: Path?
    if !region.isEmpty {
        var p = Path()
        p.addLines(region)
        p.closeSubpath()
        path = p
    }
    // Fewer than two edge crossings means the line misses the canvas (or w₁ = w₂ = 0).
    let line = crossings.count >= 2 ? (crossings[0], crossings[crossings.count - 1]) : nil
    return (path, line)
}
