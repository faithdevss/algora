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

private func generateRegressionData() -> [RegPoint] {
    let m = Double.random(in: -0.5...2.5)
    let c = Double.random(in: 1...5)
    return (0..<12).map { i in
        let x = Double(i) * 0.9 + Double.random(in: 0...0.4)
        return RegPoint(x: x, y: m * x + c + Double.random(in: -1.5...1.5))
    }
}

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

/// "y = 0.35x + 1.5": two decimals at most, trailing zeros dropped.
private func regressionEquation(_ m: Double, _ c: Double) -> String {
    func short(_ v: Double) -> String {
        var s = String(format: "%.2f", v)
        while s.hasSuffix("0") { s.removeLast() }
        if s.hasSuffix(".") { s.removeLast() }
        return s == "-0" ? "0" : s
    }
    return "y = \(short(m))x \(c < 0 ? "−" : "+") \(short(abs(c)))"
}

/// Data points: pale on dark, a mid grey on light so they don't wash out.
private func regPointColor(_ palette: Palette) -> Color { palette.dark ? SimColors.idle : SimColors.grey }
private let regResidualColor = SimColors.red

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class RegressionModel {
    var points = generateRegressionData()
    var slope = 0.35
    var intercept = 1.5
}

struct RegressionExplorerLab: View {
    @State private var model = RegressionModel()
    @Environment(\.labDock) private var dock

    var body: some View {
        let mse = regressionMse(model.points, model.slope, model.intercept)
        let best = leastSquares(model.points)
        let bestMse = regressionMse(model.points, best.0, best.1)
        let chips: [(key: String?, value: String)] = [(nil, regressionEquation(model.slope, model.intercept)), ("MSE", fmt2(mse))]
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: "Each residual is the vertical gap between a point and your line. Least squares picks the one line that makes the average squared residual, the MSE, as small as it can be.", bottom: 12)
            LabCard {
                RegressionPlot(points: model.points, slope: model.slope, intercept: model.intercept)
                RegressionLegend().padding(.top, 12)
                if dock == nil {
                    ReadoutChips(parts: chips, accented: [1]).padding(.top, 16)
                    RegressionNarration(model: model, mse: mse, bestMse: bestMse).padding(.top, 14)
                    Divider().padding(.top, 16)
                    RegressionControls(model: model).padding(.top, 14)
                }
            }
            if dock != nil {
                ReadoutChips(parts: chips, accented: [1]).padding(.top, 14)
                RegressionNarration(model: model, mse: mse, bestMse: bestMse).padding(.horizontal, 4).padding(.top, 16)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(RegressionControls(model: model)) }
        }
        .onDisappear { dock?.controls = nil }
    }
}

private struct RegressionControls: View {
    @Bindable var model: RegressionModel

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 14) {
                LabParamSlider(name: "Slope", symbol: "m", value: $model.slope, range: -3...3, format: fmt2)
                LabParamSlider(name: "Intercept", symbol: "c", value: $model.intercept, range: -4...8, format: fmt2)
            }
            HStack(spacing: 12) {
                LabButton(label: "New Data") { model.points = generateRegressionData() }
                LabButton(label: "Show Best Fit", primary: true) {
                    let (m, c) = leastSquares(model.points)
                    withAnimation { model.slope = m; model.intercept = c }
                }
            }
        }
    }
}

private struct RegressionNarration: View {
    let model: RegressionModel
    let mse: Double
    let bestMse: Double
    @Environment(\.palette) private var palette

    var body: some View {
        let atBest = mse <= bestMse * 1.02 + 1e-3
        VStack(alignment: .leading, spacing: 8) {
            headline(atBest)
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Text(atBest ? "Its MSE is \(fmt2(bestMse)). Any tilt or shift from here makes the squared residuals grow."
                        : "Each red stub is one residual. MSE is the average of their squares.")
                .font(AppFont.sans(15))
                .foregroundStyle(palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func headline(_ atBest: Bool) -> Text {
        let pts = model.points
        let residuals = pts.map { $0.y - (model.slope * $0.x + model.intercept) }
        let meanX = pts.map(\.x).reduce(0, +) / Double(max(pts.count, 1))
        // How residuals trend with x: positive means the points climb away from the line to the right.
        let drift = pts.indices.reduce(0) { $0 + (pts[$1].x - meanX) * residuals[$1] }
            / pts.reduce(0) { $0 + ($1.x - meanX) * ($1.x - meanX) }
        let meanResidual = residuals.reduce(0, +) / Double(max(residuals.count, 1))
        if atBest {
            return Text("This is the ") + Text("best fit").foregroundColor(SimColors.green) + Text(": no other line has a lower MSE.")
        }
        if residuals.allSatisfy({ $0 > 0 }) { return Text("Every point sits above your line, so raise the intercept first.") }
        if residuals.allSatisfy({ $0 < 0 }) { return Text("Every point sits below your line, so lower the intercept first.") }
        if abs(drift) > 0.15 {
            return Text(drift > 0 ? "Points climb away from your line on the right, so steepen the slope."
                                   : "Points fall away from your line on the right, so flatten the slope.")
        }
        return Text(meanResidual > 0 ? "Your line runs a little low overall, so raise the intercept."
                                     : "Your line runs a little high overall, so lower the intercept.")
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
    @State private var model = ClassifierModel()
    @Environment(\.labDock) private var dock

    var body: some View {
        let pts = config.points
        let wrong = Set(pts.indices.filter { model.predict(pts[$0]) != pts[$0].target })
        let norm = (model.w[0] * model.w[0] + model.w[1] * model.w[1]).squareRoot()
        let readout = "correct = \(pts.count - wrong.count) / \(pts.count)" + (config.margins
            ? (norm > 1e-3 ? " · margin = \(fmt2(2 / norm))" : "") + " · hinge = \(fmt2(model.hingeLoss(pts)))"
            : " · log loss = \(fmt2(model.logLoss(pts)))")
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: config.margins
                ? "A linear SVM draws the same line as logistic regression, then asks for the widest empty band around it. The dashed lines sit where w₁·x + w₂·y + b = ±1, so shrinking the weights widens the band."
                : "Logistic regression scores each point with w₁·x + w₂·y + b and squashes the score into a probability. The line is where that probability is exactly 50%; tilt it with the weights and slide it with the bias.",
                bottom: 12)
            LabCard {
                ClassifierPlane(config: config, w: model.w, wrong: wrong)
                ClassifierLegend().padding(.top, 12)
                if dock == nil {
                    ReadoutChips(text: readout, accented: [0]).padding(.top, 16)
                    ClassifierNarration(config: config, model: model, wrong: wrong).padding(.top, 14)
                    Divider().padding(.top, 16)
                    ClassifierControls(model: model).padding(.top, 14)
                }
            }
            if dock != nil {
                ReadoutChips(text: readout, accented: [0]).padding(.top, 14)
                ClassifierNarration(config: config, model: model, wrong: wrong).padding(.horizontal, 4).padding(.top, 16)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(ClassifierControls(model: model)) }
        }
        .onDisappear { dock?.controls = nil }
    }
}

private struct ClassifierControls: View {
    @Bindable var model: ClassifierModel

    var body: some View {
        VStack(spacing: 14) {
            LabParamSlider(name: "Weight", symbol: "w₁", value: $model.w[0], range: -3...3, step: 0.1)
            LabParamSlider(name: "Weight", symbol: "w₂", value: $model.w[1], range: -3...3, step: 0.1)
            LabParamSlider(name: "Bias", symbol: "b", value: $model.w[2], range: -3...3, step: 0.1)
        }
    }
}

private struct ClassifierNarration: View {
    let config: ClassifierConfig
    let model: ClassifierModel
    let wrong: Set<Int>
    @Environment(\.palette) private var palette

    var body: some View {
        let norm = (model.w[0] * model.w[0] + model.w[1] * model.w[1]).squareRoot()
        let detail = !config.margins ? "The line is where the model is exactly 50% sure."
            : norm < 1e-3 ? "With both weights at zero there is no line, and no margin to measure."
            : "The margin is \(fmt2(2 / norm)) wide, between the dashed lines. Shrink both weights to widen it while every point stays outside."
        VStack(alignment: .leading, spacing: 8) {
            headline
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Text(detail)
                .font(AppFont.sans(15))
                .foregroundStyle(palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var headline: Text {
        let pts = config.points
        if wrong.isEmpty {
            return Text("All \(pts.count) points").foregroundColor(SimColors.green) + Text(" are on the right side.")
        }
        let n = wrong.count
        let count = n == 1 ? "One" : "\(n)"
        let verb = n == 1 ? "point is" : "points are"
        let classes = Set(wrong.map { pts[$0].target })
        var text: Text
        if classes.count == 1, let k = classes.first {
            text = Text("\(count) ") + Text("class \(k)").foregroundColor(k == 1 ? classOne : classZero) + Text(" \(verb) on the wrong side.")
        } else {
            text = Text("\(count) \(verb) on the wrong side.")
        }
        if case let (param, up)? = bestNudge() {
            let what = param == 2 ? "slide" : "tilt"
            text = text + Text(" \(up ? "Raise" : "Lower") \(["w₁", "w₂", "b"][param]) to \(what) the boundary.")
        }
        return text
    }

    /// The single ±0.2 slider move that fixes the most points, or nil when none fixes any.
    private func bestNudge() -> (Int, Bool)? {
        let pts = config.points
        func errors(_ w: [Double]) -> Int { pts.filter { model.predict($0, w) != $0.target }.count }
        func loss(_ w: [Double]) -> Double { config.margins ? model.hingeLoss(pts, w) : model.logLoss(pts, w) }
        var best: (Int, Bool)?
        var bestWrong = errors(model.w), bestLoss = Double.greatestFiniteMagnitude
        for param in 0..<3 {
            for up in [true, false] {
                var t = model.w
                t[param] = snap1(t[param] + (up ? 0.2 : -0.2))
                let n = errors(t), l = loss(t)
                if n < bestWrong || (n == bestWrong && best != nil && l < bestLoss) {
                    best = (param, up); bestWrong = n; bestLoss = l
                }
            }
        }
        return best
    }
}

private struct ClassifierLegend: View {
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 16) {
            item(Circle().fill(classZero).frame(width: 11, height: 11), "Class 0")
            item(Circle().fill(classOne).frame(width: 11, height: 11), "Class 1")
            item(Circle().stroke(SimColors.red, lineWidth: 2).frame(width: 10, height: 10), "Misclassified")
        }
    }

    private func item(_ swatch: some View, _ label: String) -> some View {
        HStack(spacing: 6) {
            swatch
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.onSurface.opacity(0.8))
        }
    }
}

private struct ClassifierPlane: View {
    let config: ClassifierConfig
    let w: [Double]
    let wrong: Set<Int>
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
            if wrong.contains(i) {
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - r - 4, y: c.y - r - 4, width: (r + 4) * 2, height: (r + 4) * 2)),
                           with: .color(SimColors.red), lineWidth: 2.5)
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
