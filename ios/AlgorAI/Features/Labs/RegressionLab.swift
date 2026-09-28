import SwiftUI

// Port of RegressionLabSection.kt: one canvas, readout row, sliders and a solve button, driven by a
// per-topic config — regression estimators, time-series forecasting and regression metrics.

private let labFit = Color(hex: 0x4F46E5)
private let labPoint = Color(hex: 0x7C3AED)
private let labReference = SimColors.grey
private let labHighlight = SimColors.red
private let labBand = Color(hex: 0x10B981)
private let seriesForecastColor = SimColors.red
private let seriesComponent = SimColors.amber

private struct LabCurve { let points: [(Float, Float)]; let color: Color; var dashed = false; var width: CGFloat = 3 }
private struct LabReadout { let value: String; let label: String }
private struct LabResult {
    let curves: [LabCurve]
    /// Rendered as chips, label then value; the first is the lab's headline number and is accented.
    let readouts: [LabReadout]
    /// Narration. Topics that author `headline` + `detail` get them as written; the rest split
    /// `note` after its first sentence (what happened, then why it matters).
    var note: String = ""
    var highlighted: Set<Int> = []
    var headline: String? = nil
    var detail: String? = nil
    /// A phrase of `headline` to tint, in `highlightColor` (the key-term yellow when nil).
    var highlight: String? = nil
    var highlightColor: Color? = nil
    /// A share chip after the readouts: a bar filled to this fraction, with its percentage.
    var share: Double? = nil
}

private struct RegSlider {
    let label: String
    let range: ClosedRange<Double>
    let initial: Double
    var step: Double?
    /// Set in mono after the label ("Slope m").
    var symbol = ""
    var format: (Double) -> String = { round2($0) }
}

private struct RegConfig {
    let intro: String
    let data: (Int32) -> [LabPoint]
    let sliders: [RegSlider]
    var solveLabel: String?
    var solve: (([LabPoint], [Double]) -> [Double])?
    let evaluate: ([LabPoint], [Double]) -> LabResult
    var legend: [(Color, String)] = []
    /// False when `data` ignores its seed, so New Data would do nothing and is hidden.
    var reseedable = true
}

private func round2(_ v: Double) -> String {
    let r = (Float(v) * 100).rounded() / 100
    return "\(r)"
}

private func fmt(_ v: Double) -> String {
    if !v.isFinite { return "∞" }
    if abs(v) >= 1000 { return String(format: "%.0f", v) }
    if abs(v) >= 10 { return String(format: "%.1f", v) }
    if abs(v) >= 0.01 || v == 0 { return String(format: "%.3f", v) }
    return String(format: "%.1e", v)
}

private func rInt(_ v: Double) -> Int { Int(Float(v).rounded()) }
private func expLabel(_ v: Double) -> String { "1e\(rInt(v))" }

// MARK: Datasets

private func wavyData(_ seed: Int32) -> [LabPoint] {
    var r = KotlinRandom(seed)
    return (0..<24).map { i in
        let x = Float(i) * 0.4
        return LabPoint(x: x, y: 3 + 1.6 * sinf(x * 0.75) + 0.18 * x + (r.nextFloat() - 0.5) * 1.1)
    }
}

private func outlierData(_ seed: Int32) -> [LabPoint] {
    var r = KotlinRandom(seed)
    let clean = (0..<26).map { i -> LabPoint in
        let x = Float(i) * 0.36
        return LabPoint(x: x, y: 1.2 + 0.85 * x + (r.nextFloat() - 0.5) * 0.7)
    }
    let bad = (0..<6).map { i in LabPoint(x: 1.2 + Float(i) * 1.3, y: 9.5 - r.nextFloat() * 1.4) }
    return clean + bad
}

private func fanData(_ seed: Int32) -> [LabPoint] {
    var r = KotlinRandom(seed)
    return (0..<40).map { i in
        let x = Float(i) * 0.24
        let spread: Float = 0.4 + 0.42 * x
        return LabPoint(x: x, y: 2 + 0.7 * x + (r.nextFloat() - 0.5) * 2 * spread)
    }
}

private func countData(_ seed: Int32) -> [LabPoint] {
    var r = KotlinRandom(seed)
    return (0..<30).map { i in
        let x = Float(i) * 0.3
        let m = exp(-0.4 + 0.42 * Double(x))
        var k = 0, p = 1.0
        let limit = exp(-m)
        repeat { k += 1; p *= r.nextDouble() } while p > limit
        return LabPoint(x: x, y: Float(k - 1))
    }
}

private func monotoneData(_ seed: Int32) -> [LabPoint] {
    var r = KotlinRandom(seed)
    return (0..<28).map { i in
        let x = Float(i) * 0.32
        let trend: Float = x < 2.5 ? 1.2 + 0.15 * x : x < 5.5 ? 2.6 + 0.9 * (x - 2.5) : 5.3 + 0.1 * (x - 5.5)
        return LabPoint(x: x, y: trend + (r.nextFloat() - 0.5) * 1.3)
    }
}

private func heldOut(_ points: [LabPoint]) -> Set<Int> { Set(points.indices.filter { $0 % 4 == 3 }) }

private func xRange(_ points: [LabPoint]) -> (Float, Float) { (points.map(\.x).min() ?? 0, points.map(\.x).max() ?? 1) }

private func sampleCurve(_ xMin: Float, _ xMax: Float, _ color: Color, dashed: Bool = false, width: CGFloat = 3, _ f: (Float) -> Double) -> LabCurve {
    LabCurve(points: (0...80).map { i in let x = xMin + (xMax - xMin) * Float(i) / 80; return (x, Float(f(x))) }, color: color, dashed: dashed, width: width)
}

private func split(_ points: [LabPoint]) -> ([LabPoint], [LabPoint]) {
    let test = heldOut(points)
    return (points.indices.filter { !test.contains($0) }.map { points[$0] }, points.indices.filter { test.contains($0) }.map { points[$0] })
}

private func ys(_ points: [LabPoint]) -> [Double] { points.map { Double($0.y) } }

// MARK: Regression configs

private func polynomial() -> RegConfig {
    RegConfig(
        intro: "One slider: the degree of the polynomial. Training error can only fall as it rises — the held-out error is the one that tells the truth.",
        data: wavyData,
        sliders: [RegSlider(label: "Degree", range: 1...9, initial: 3, step: 1, format: { "\(rInt($0))" })],
        solveLabel: "✦ Best by held-out",
        solve: { points, _ in
            let (train, hold) = split(points)
            let (xMin, xMax) = xRange(points)
            let best = (1...9).min { a, b in
                func err(_ d: Int) -> Double {
                    let beta = ridgeSolve(designMatrix(train.map(\.x), degree: d, xMin: xMin, xMax: xMax), ys(train), 0)
                    return mse(hold) { polyValue(beta, scaleX($0, xMin, xMax)) }
                }
                return err(a) < err(b)
            } ?? 3
            return [Double(best)]
        },
        evaluate: { points, values in
            let degree = rInt(values[0])
            let (xMin, xMax) = xRange(points)
            let (train, hold) = split(points)
            let beta = ridgeSolve(designMatrix(train.map(\.x), degree: degree, xMin: xMin, xMax: xMax), ys(train), 0)
            let predict = { (x: Float) in polyValue(beta, scaleX(x, xMin, xMax)) }
            let trainMse = mse(train, predict), testMse = mse(hold, predict)
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labFit, predict)],
                readouts: [LabReadout(value: fmt(trainMse), label: "Train MSE"), LabReadout(value: fmt(testMse), label: "Held-out MSE"), LabReadout(value: "\(degree + 1)", label: "Coefficients")],
                note: testMse > trainMse * 2.2 && degree > 4
                    ? "Held-out error is now more than double the training error. The curve is fitting noise it will never see again — this is overfitting, not a better model."
                    : "Degree \(degree) fits \(degree + 1) coefficients to \(train.count) training points.",
                highlighted: heldOut(points))
        },
        legend: [(labPoint, "Train"), (labHighlight, "Held out")])
}

private func ridge() -> RegConfig {
    RegConfig(
        intro: "A degree-9 polynomial — far more flexible than the data justifies — with the L2 penalty as the only defence. λ is on a log scale.",
        data: wavyData,
        sliders: [RegSlider(label: "log₁₀ λ", range: -6...2, initial: -6, format: expLabel)],
        solveLabel: "✦ Best by held-out",
        solve: { points, _ in
            let (train, hold) = split(points)
            let (xMin, xMax) = xRange(points)
            let best = (-6...2).min { a, b in
                func err(_ e: Int) -> Double {
                    let beta = ridgeSolve(designMatrix(train.map(\.x), degree: 9, xMin: xMin, xMax: xMax), ys(train), pow(10, Double(e)))
                    return mse(hold) { polyValue(beta, scaleX($0, xMin, xMax)) }
                }
                return err(a) < err(b)
            } ?? -3
            return [Double(best)]
        },
        evaluate: { points, values in
            let lambda = pow(10, values[0])
            let (xMin, xMax) = xRange(points)
            let (train, hold) = split(points)
            let x = designMatrix(train.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
            let beta = ridgeSolve(x, ys(train), lambda), plain = ridgeSolve(x, ys(train), 0)
            let norm = beta.dropFirst().reduce(0) { $0 + $1 * $1 }.squareRoot()
            let f = { (v: Float) in polyValue(beta, scaleX(v, xMin, xMax)) }
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 2) { polyValue(plain, scaleX($0, xMin, xMax)) }, sampleCurve(xMin, xMax, labFit, f)],
                readouts: [LabReadout(value: fmt(mse(train, f)), label: "Train MSE"), LabReadout(value: fmt(mse(hold, f)), label: "Held-out MSE"), LabReadout(value: fmt(norm), label: "‖β‖₂")],
                note: "Ridge shrinks every coefficient toward zero but sets none of them to zero — the norm falls smoothly and all 10 terms stay in the model.",
                highlighted: heldOut(points))
        },
        legend: [(labPoint, "Train"), (labHighlight, "Held out"), (labReference, "λ = 0")])
}

private func lasso() -> RegConfig {
    RegConfig(
        intro: "The identical setup as Ridge, with L1 in place of L2. Watch the coefficient count, not just the curve.",
        data: wavyData,
        sliders: [RegSlider(label: "log₁₀ λ", range: -5...0, initial: -5, format: expLabel)],
        evaluate: { points, values in
            let lambda = pow(10, values[0])
            let (xMin, xMax) = xRange(points)
            let x = designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
            let l = coordinateDescent(x, ys(points), lambda: lambda, l1Ratio: 1)
            let r = ridgeSolve(x, ys(points), lambda * Double(points.count))
            let nz = l.dropFirst().filter { abs($0) > 1e-6 }.count, rnz = r.dropFirst().filter { abs($0) > 1e-6 }.count
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 2) { polyValue(r, scaleX($0, xMin, xMax)) }, sampleCurve(xMin, xMax, labFit) { polyValue(l, scaleX($0, xMin, xMax)) }],
                readouts: [LabReadout(value: fmt(mse(points) { polyValue(l, scaleX($0, xMin, xMax)) }), label: "Train MSE"), LabReadout(value: "\(nz) of 9", label: "Non-zero β"), LabReadout(value: "\(rnz) of 9", label: "Ridge non-zero")],
                note: "At this λ lasso keeps \(nz) of the 9 polynomial terms; ridge at the same penalty keeps \(rnz). L1's corner at zero is what makes coefficients land exactly on it — L2's smooth bowl never does.")
        },
        legend: [(labPoint, "Train"), (labReference, "Ridge, same λ")])
}

private func elasticNet() -> RegConfig {
    RegConfig(
        intro: "Two dials. λ sets how much penalty, and the mix sets how much of it is L1 — 0 is pure ridge, 1 is pure lasso.",
        data: wavyData,
        sliders: [RegSlider(label: "log₁₀ λ", range: -5...0, initial: -3, format: expLabel), RegSlider(label: "L1 ratio", range: 0...1, initial: 0.5)],
        evaluate: { points, values in
            let (xMin, xMax) = xRange(points)
            let x = designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
            let ratio = values[1]
            let beta = coordinateDescent(x, ys(points), lambda: pow(10, values[0]), l1Ratio: ratio)
            let nz = beta.dropFirst().filter { abs($0) > 1e-6 }.count
            let norm = beta.dropFirst().reduce(0) { $0 + $1 * $1 }.squareRoot()
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labFit) { polyValue(beta, scaleX($0, xMin, xMax)) }],
                readouts: [LabReadout(value: fmt(mse(points) { polyValue(beta, scaleX($0, xMin, xMax)) }), label: "Train MSE"), LabReadout(value: "\(nz) of 9", label: "Non-zero β"), LabReadout(value: fmt(norm), label: "‖β‖₂")],
                note: ratio < 0.05 ? "Pure ridge: everything shrinks, nothing is eliminated."
                    : ratio > 0.95 ? "Pure lasso: sparse, but among correlated terms it picks one arbitrarily and drops the rest."
                    : "Mixed: the L2 part keeps correlated terms in together rather than letting L1 pick a winner at random, while the L1 part still zeroes what is useless.")
        })
}

private func stepwise() -> RegConfig {
    RegConfig(
        intro: "Forward selection over the same nine polynomial terms: at each step add whichever remaining term cuts residual error most.",
        data: wavyData,
        sliders: [RegSlider(label: "Terms kept", range: 1...9, initial: 3, step: 1, format: { "\(rInt($0))" })],
        evaluate: { points, values in
            let (xMin, xMax) = xRange(points)
            let r = forwardStepwise(designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax), ys(points), terms: rInt(values[0]))
            let sel = r.selected.map { "x^\($0)" }.joined(separator: ",")
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labFit) { polyValue(r.beta, scaleX($0, xMin, xMax)) }],
                readouts: [LabReadout(value: fmt(mse(points) { polyValue(r.beta, scaleX($0, xMin, xMax)) }), label: "Train MSE"), LabReadout(value: fmt(r.adjustedR2), label: "Adjusted R²"), LabReadout(value: sel.isEmpty ? "—" : sel, label: "Selected")],
                note: "Each term was chosen by looking at this data, so the reported p-values and R² are optimistically biased — the selection step is never accounted for. Adjusted R² helps a little; honest evaluation needs a held-out set.")
        })
}

private func robust() -> RegConfig {
    RegConfig(
        intro: "Twenty-six points from one process and six from another. The slider is RANSAC's inlier threshold — how far off the line a point may sit and still count.",
        data: outlierData,
        sliders: [RegSlider(label: "Inlier threshold", range: 0.2...4, initial: 0.9)],
        evaluate: { points, values in
            let (xMin, xMax) = xRange(points)
            let fit = ransac(points, threshold: values[0])
            let (m, c) = ordinaryLeastSquares(points)
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 2) { m * Double($0) + c }, sampleCurve(xMin, xMax, labFit) { fit.slope * Double($0) + fit.intercept }],
                readouts: [LabReadout(value: fmt(fit.slope), label: "RANSAC slope"), LabReadout(value: fmt(m), label: "OLS slope"), LabReadout(value: "\(fit.inliers.count)/\(points.count)", label: "Inliers")],
                note: "Squared error grows with the square of the residual, so the six contaminating points dominate the OLS fit and drag it away from the \(fit.inliers.count) points that share a trend. RANSAC never averages them in — it finds the largest consensus set and fits only that.",
                highlighted: Set(points.indices.filter { !fit.inliers.contains($0) }))
        },
        legend: [(labFit, "RANSAC"), (labReference, "OLS"), (labHighlight, "Outlier")])
}

private func quantile() -> RegConfig {
    RegConfig(
        intro: "Spread grows with x here, so \"the average response\" and \"the 90th percentile response\" are genuinely different lines. τ picks which one to fit.",
        data: fanData,
        sliders: [RegSlider(label: "τ (quantile)", range: 0.05...0.95, initial: 0.5)],
        evaluate: { points, values in
            let tau = values[0]
            let (xMin, xMax) = xRange(points)
            let (m, c) = quantileFit(points, tau: tau), (lm, lc) = quantileFit(points, tau: 0.1), (hm, hc) = quantileFit(points, tau: 0.9)
            let (mm, mc) = ordinaryLeastSquares(points)
            let below = points.filter { Double($0.y) < m * Double($0.x) + c }.count
            let pct = Int((Float(below) * 100 / Float(points.count)).rounded())
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labBand, dashed: true, width: 1.5) { lm * Double($0) + lc }, sampleCurve(xMin, xMax, labBand, dashed: true, width: 1.5) { hm * Double($0) + hc },
                         sampleCurve(xMin, xMax, labReference, dashed: true, width: 2) { mm * Double($0) + mc }, sampleCurve(xMin, xMax, labFit) { m * Double($0) + c }],
                readouts: [LabReadout(value: "\(pct)%", label: "Points below"), LabReadout(value: "\(Int((tau * 100).rounded()))%", label: "Target τ"), LabReadout(value: fmt(pinballLoss(points, tau: tau, m: m, c: c)), label: "Pinball loss")],
                note: "The check: a correct τ-quantile fit should leave about τ of the data beneath it — measured \(pct)% against a target of \(Int((tau * 100).rounded()))%. Note also that the τ=0.1 and τ=0.9 lines are not parallel; they fan out because the spread does.")
        },
        legend: [(labFit, "τ fit"), (labBand, "τ = 0.1 / 0.9"), (labReference, "OLS mean")])
}

private func bayesian() -> RegConfig {
    RegConfig(
        intro: "A degree-5 fit that returns a distribution rather than a line. The shaded band is ±2 predictive standard deviations.",
        data: { wavyData($0).filter { !($0.x > 3.2 && $0.x < 5.6) } },
        sliders: [RegSlider(label: "Prior precision α", range: 0.01...8, initial: 1), RegSlider(label: "Noise variance", range: 0.05...2, initial: 0.4)],
        evaluate: { points, values in
            let (xMin, xMax) = xRange(points)
            let fit = bayesianRidge(designMatrix(points.map(\.x), degree: 5, xMin: xMin, xMax: xMax), ys(points), alpha: values[0], noiseVariance: values[1])
            let phi = { (v: Float) -> [Double] in let t = scaleX(v, xMin, xMax); return (0..<6).map { $0 == 0 ? 1 : pow(t, Double($0)) } }
            let meanAt = { (v: Float) in polyValue(fit.mean, scaleX(v, xMin, xMax)) }
            let gap = predictiveStd(fit, phi(4.4)), dense = predictiveStd(fit, phi(1.2))
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labBand, dashed: true, width: 1.5) { meanAt($0) + 2 * predictiveStd(fit, phi($0)) },
                         sampleCurve(xMin, xMax, labBand, dashed: true, width: 1.5) { meanAt($0) - 2 * predictiveStd(fit, phi($0)) },
                         sampleCurve(xMin, xMax, labFit, meanAt)],
                readouts: [LabReadout(value: fmt(dense), label: "σ where dense"), LabReadout(value: fmt(gap), label: "σ in the gap"), LabReadout(value: fmt(gap / max(dense, 1e-6)) + "×", label: "Ratio")],
                note: "The band widens across the gap because the posterior covariance term grows where no data constrains it. A plain ridge fit produces the same mean curve and no way at all to know that middle stretch is a guess.")
        },
        legend: [(labFit, "Posterior mean"), (labBand, "±2σ")])
}

private func poisson() -> RegConfig {
    RegConfig(
        intro: "Counts, not measurements: integers, never negative, and with variance that grows alongside the mean. Sliders are the log-linear coefficients.",
        data: countData,
        sliders: [RegSlider(label: "β₀ (intercept)", range: -2...2, initial: -0.4), RegSlider(label: "β₁ (slope)", range: -0.2...0.8, initial: 0.42)],
        solveLabel: "✦ Fit by IRLS",
        solve: { points, _ in
            let (b0, b1) = poissonIrls(points)
            return [min(max(b0, -2), 2), min(max(b1, -0.2), 0.8)]
        },
        evaluate: { points, values in
            let b0 = values[0], b1 = values[1]
            let (xMin, xMax) = xRange(points)
            let (om, oc) = ordinaryLeastSquares(points)
            let predict = { (v: Float) in exp(min(max(b0 + b1 * Double(v), -20), 20)) }
            let negFrom = (0...80).map { xMin + (xMax - xMin) * Float($0) / 80 }.first { om * Double($0) + oc < 0 }
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 2) { om * Double($0) + oc }, sampleCurve(xMin, xMax, labFit, predict)],
                readouts: [LabReadout(value: fmt(poissonDeviance(points, b0, b1)), label: "Deviance"), LabReadout(value: fmt(mse(points, predict)), label: "MSE"), LabReadout(value: fmt(exp(b1)) + "×", label: "Per unit x")],
                note: negFrom.map { "The dashed OLS line crosses zero at x ≈ \(round2(Double($0))) and predicts negative counts below it — impossible for the data it is modelling. The log link makes that unrepresentable: exp is positive everywhere." }
                    ?? "The log link means β₁ is multiplicative: each unit of x multiplies the expected count by exp(β₁) = \(fmt(exp(b1))), rather than adding a constant.")
        },
        legend: [(labFit, "exp(β₀+β₁x)"), (labReference, "OLS line")])
}

private func isotonic() -> RegConfig {
    RegConfig(
        intro: "No functional form assumed at all — only that the fit must never decrease. The slider adds noise to show when the constraint binds.",
        data: monotoneData,
        sliders: [RegSlider(label: "Extra noise", range: 0...2, initial: 0)],
        evaluate: { points, values in
            let extra = Float(values[0])
            var r = KotlinRandom(9)
            let noisy = points.map { LabPoint(x: $0.x, y: $0.y + (r.nextFloat() - 0.5) * 2 * extra) }
            let fit = pava(noisy)
            let (m, c) = ordinaryLeastSquares(noisy)
            let (xMin, xMax) = xRange(noisy)
            var step: [(Float, Float)] = []
            for (i, x) in fit.xs.enumerated() {
                if i > 0 { step.append((x, Float(fit.ys[i - 1]))) }
                step.append((x, Float(fit.ys[i])))
            }
            let err = mse(noisy) { v in fit.ys[max(fit.xs.lastIndex { $0 <= v } ?? 0, 0)] }
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 2) { m * Double($0) + c }, LabCurve(points: step, color: labFit)],
                readouts: [LabReadout(value: "\(fit.blocks)", label: "Blocks"), LabReadout(value: "\(noisy.count)", label: "Points"), LabReadout(value: fmt(err), label: "MSE")],
                note: "PAVA merged \(noisy.count) points into \(fit.blocks) flat blocks. Every merge is a place the raw data went down and monotonicity said it may not — more noise means more violations, so fewer and wider blocks.")
        },
        legend: [(labFit, "Isotonic (PAVA)"), (labReference, "Linear fit")])
}

private func lars() -> RegConfig {
    RegConfig(
        intro: "The coefficient path, one step at a time. Each step admits the predictor most correlated with the current residual, then moves in the direction that keeps the active correlations equal.",
        data: wavyData,
        sliders: [RegSlider(label: "Path steps", range: 1...8, initial: 2, step: 1, format: { "\(rInt($0))" })],
        evaluate: { points, values in
            let steps = rInt(values[0])
            let (xMin, xMax) = xRange(points)
            let path = larsPath(designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax), ys(points), maxSteps: steps)
            let cur = path.last
            let beta = cur?.beta ?? [Double](repeating: 0, count: 10)
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labFit) { polyValue(beta, scaleX($0, xMin, xMax)) }],
                readouts: [LabReadout(value: cur.map { $0.active.map { "x^\($0)" }.joined(separator: ",") } ?? "—", label: "Active set"),
                           LabReadout(value: "\(cur?.active.count ?? 0)", label: "Size"), LabReadout(value: cur.map { fmt($0.maxCorrelation) } ?? "—", label: "Max |corr|")],
                note: "Step \(steps) admitted x^\(cur?.entered ?? 0). LARS moves partway rather than all the way, so a predictor is never fully fitted before the next one is considered — that is the difference from forward stepwise, and it is what makes the path piecewise-linear and cheap to compute in full.")
        })
}

// MARK: Time series

private func seriesData(_ seed: Int32) -> [LabPoint] { retailSeries(seed: Int(seed)).enumerated().map { LabPoint(x: Float($0), y: Float($1)) } }
private func heldOutIndices(_ p: [LabPoint]) -> Set<Int> { Set(p.indices.filter { $0 >= trainLength }) }
private func seriesCurve(_ values: [Double?], _ startAt: Int, _ color: Color, dashed: Bool = false, width: CGFloat = 2.5) -> LabCurve {
    LabCurve(points: values.enumerated().compactMap { i, v in v.map { (Float(startAt + i), Float($0)) } }, color: color, dashed: dashed, width: width)
}
private func seriesTrain(_ p: [LabPoint]) -> [Double] { p.prefix(trainLength).map { Double($0.y) } }
private func seriesActual(_ p: [LabPoint]) -> [Double] { p.dropFirst(trainLength).map { Double($0.y) } }

private func benchmarkNote(_ name: String, _ rmse: Double, _ bench: Double) -> String {
    rmse < bench ? "\(name) beats the seasonal-naive benchmark (\(fmt(rmse)) against \(fmt(bench)))."
        : "\(name) loses to the seasonal-naive benchmark — \(fmt(rmse)) against \(fmt(bench)). A method that cannot beat \"next year looks like last year\" is not yet earning its complexity."
}

private func movingAverageCfg() -> RegConfig {
    RegConfig(
        intro: "One slider: the window. Watch two things move in opposite directions — how much jitter is removed, and how far behind the series the smoothed line falls.",
        data: seriesData,
        sliders: [RegSlider(label: "Window", range: 2...12, initial: 3, step: 1, format: { "\(rInt($0)) months" })],
        evaluate: { points, values in
            let window = rInt(values[0])
            let train = seriesTrain(points)
            let smoothed = movingAverage(train, window: window)
            let raw = roughness(train), smooth = roughness(smoothed.compactMap { $0 })
            let lag = Double(window - 1) / 2
            return LabResult(
                curves: [seriesCurve(smoothed, 0, labFit)],
                readouts: [LabReadout(value: fmt(smooth), label: "Roughness"), LabReadout(value: "−\(fmt(100 * (1 - smooth / raw)))%", label: "vs raw \(fmt(raw))"), LabReadout(value: "\(fmt(lag)) mo", label: "Lag introduced")],
                note: "A \(window)-month trailing window cut the month-to-month jitter from \(fmt(raw)) to \(fmt(smooth)), dropped the first \(window - 1) months entirely, and put the line \(fmt(lag)) months behind the series. At a window of \(seasonPeriod) the seasonal cycle is averaged away completely, which is how a moving average is used as a trend estimate rather than as a forecast.",
                highlighted: heldOutIndices(points))
        },
        legend: [(labPoint, "Observed"), (labFit, "Moving average"), (labHighlight, "Held out")])
}

private func autoregressionCfg() -> RegConfig {
    RegConfig(
        intro: "Regress the series on its own past. p is how many lags it may use; the forecast past month 48 is recursive — each prediction becomes the next input.",
        data: seriesData,
        sliders: [RegSlider(label: "Lags (p)", range: 1...12, initial: 2, step: 1, format: { "p = \(rInt($0))" })],
        solveLabel: "✦ Best by held-out",
        solve: { points, _ in
            let train = seriesTrain(points), actual = seriesActual(points)
            let best = (1...12).min { a, b in
                forecastRmse(arForecast(fitAr(train, p: a), train, horizon: actual.count), actual) < forecastRmse(arForecast(fitAr(train, p: b), train, horizon: actual.count), actual)
            } ?? 2
            return [Double(best)]
        },
        evaluate: { points, values in
            let p = rInt(values[0])
            let train = seriesTrain(points), actual = seriesActual(points)
            let fit = fitAr(train, p: p)
            let forecast = arForecast(fit, train, horizon: actual.count)
            let fitted: [Double?] = train.indices.map { $0 < p ? nil : train[$0] - fit.residuals[$0 - p] }
            let out = forecastRmse(forecast, actual), bench = forecastRmse(seasonalNaiveForecast(train, horizon: actual.count), actual)
            return LabResult(
                curves: [seriesCurve(fitted, 0, labFit), seriesCurve(forecast, trainLength, seriesForecastColor, width: 3)],
                readouts: [LabReadout(value: fmt(fit.rmse), label: "In-sample RMSE"), LabReadout(value: fmt(out), label: "Forecast RMSE"), LabReadout(value: fmt(fit.coefficients.count > 1 ? fit.coefficients[1] : 0), label: "φ₁")],
                note: "AR(\(p)): \(fit.coefficients.dropFirst().map(fmt).joined(separator: ", ")). " + benchmarkNote("It", out, bench) + " An AR model has no seasonal term at all, so the only way it can reach twelve months back is to spend twelve lags getting there — which is what p = 12 is doing, and why the seasonal models that follow exist.",
                highlighted: heldOutIndices(points))
        },
        legend: [(labPoint, "Observed"), (labFit, "In-sample fit"), (seriesForecastColor, "Forecast")])
}

private func arimaCfg() -> RegConfig {
    RegConfig(
        intro: "The three letters as three sliders. d differences the series until it is stationary, p regresses on its own lags, q regresses on past errors.",
        data: seriesData,
        sliders: [RegSlider(label: "AR order (p)", range: 0...4, initial: 2, step: 1, format: { "p = \(rInt($0))" }),
                  RegSlider(label: "Differencing (d)", range: 0...2, initial: 1, step: 1, format: { "d = \(rInt($0))" }),
                  RegSlider(label: "MA order (q)", range: 0...3, initial: 1, step: 1, format: { "q = \(rInt($0))" })],
        evaluate: { points, values in
            let p = rInt(values[0]), d = rInt(values[1]), q = rInt(values[2])
            let train = seriesTrain(points), actual = seriesActual(points)
            let fit = fitArima(train, p: p, d: d, q: q, horizon: actual.count)
            let out = forecastRmse(fit.forecast, actual), bench = forecastRmse(seasonalNaiveForecast(train, horizon: actual.count), actual)
            var diffd = train
            for _ in 0..<d { diffd = difference(diffd) }
            let raw = lag1Autocorrelation(train), now = lag1Autocorrelation(diffd)
            let first = d == 0
                ? "Undifferenced, the lag-1 autocorrelation is \(fmt(raw)) — close to 1, which is what a trending series looks like and what \"non-stationary\" means in practice. "
                : "After \(d) round\(d > 1 ? "s" : "") of differencing the lag-1 autocorrelation is \(fmt(now)), down from \(fmt(raw)). Each round costs one row and removes one order of trend; over-differencing is a real failure and shows up as an ACF driven negative. "
            return LabResult(
                curves: [seriesCurve(fit.forecast, trainLength, seriesForecastColor, width: 3)],
                readouts: [LabReadout(value: fmt(now), label: "Lag-1 ACF"), LabReadout(value: fmt(out), label: "Forecast RMSE"), LabReadout(value: "\(train.count - d)", label: "Usable rows")],
                note: first + benchmarkNote("ARIMA(\(p),\(d),\(q))", out, bench) + " The MA terms are fitted by Hannan-Rissanen — a long AR first, then a regression on its own residuals — and they decay out of the forecast after q steps, because future errors are zero in expectation.",
                highlighted: heldOutIndices(points))
        },
        legend: [(labPoint, "Observed"), (seriesForecastColor, "Forecast"), (labHighlight, "Held out")])
}

private func sarimaCfg() -> RegConfig {
    RegConfig(
        intro: "The same machinery with one addition: a difference at lag 12 rather than lag 1. Toggle it and watch both the lag-12 autocorrelation and the forecast error.",
        data: seriesData,
        sliders: [RegSlider(label: "AR order (p)", range: 1...6, initial: 2, step: 1, format: { "p = \(rInt($0))" }),
                  RegSlider(label: "Seasonal difference", range: 0...1, initial: 1, step: 1, format: { rInt($0) == 1 ? "on (lag 12)" : "off" })],
        evaluate: { points, values in
            let p = rInt(values[0]), seasonal = rInt(values[1]) == 1
            let train = seriesTrain(points), actual = seriesActual(points)
            let fit = fitSarima(train, p: p, seasonalDifference: seasonal, horizon: actual.count)
            let out = forecastRmse(fit.forecast, actual), bench = forecastRmse(seasonalNaiveForecast(train, horizon: actual.count), actual)
            let without = forecastRmse(fitSarima(train, p: p, seasonalDifference: false, horizon: actual.count).forecast, actual)
            return LabResult(
                curves: [seriesCurve(fit.forecast, trainLength, seriesForecastColor, width: 3)],
                readouts: [LabReadout(value: fmt(fit.acfAfter), label: "Lag-12 ACF"), LabReadout(value: fmt(out), label: "Forecast RMSE"), LabReadout(value: "\(fit.seasonalDifferenced.count)", label: "Usable rows")],
                note: seasonal
                    ? "Differencing at lag 12 — yₜ − yₜ₋₁₂ — dropped the lag-12 autocorrelation from \(fmt(fit.acfBefore)) to \(fmt(fit.acfAfter)) and cost \(seasonPeriod) rows. Forecast error \(fmt(out)) against \(fmt(without)) with the seasonal difference off. " + benchmarkNote("It", out, bench) + " That single subtraction is the whole seasonal idea: compare each month with the same month a year ago rather than with last month."
                    : "With no seasonal difference the lag-12 autocorrelation stays at \(fmt(fit.acfBefore)), and the annual cycle is left for the AR lags to reconstruct one month at a time. Forecast error \(fmt(out)). " + benchmarkNote("It", out, bench) + " Turn the seasonal difference on and compare.",
                highlighted: heldOutIndices(points))
        },
        legend: [(labPoint, "Observed"), (seriesForecastColor, "Forecast"), (labHighlight, "Held out")])
}

private func expSmoothingCfg() -> RegConfig {
    RegConfig(
        intro: "Three recursions, three sliders. α smooths the level, β the trend, γ the seasonal figures — each one an exponentially-weighted compromise between the newest observation and everything before it.",
        data: seriesData,
        sliders: [RegSlider(label: "α (level)", range: 0.05...0.95, initial: 0.3), RegSlider(label: "β (trend)", range: 0.01...0.6, initial: 0.1), RegSlider(label: "γ (seasonal)", range: 0.05...0.95, initial: 0.3)],
        solveLabel: "✦ Best by held-out",
        solve: { points, _ in
            let train = seriesTrain(points), actual = seriesActual(points)
            var best: [Double] = [0.3, 0.1, 0.3], bestRmse = Double.greatestFiniteMagnitude
            for a: Float in [0.1, 0.2, 0.3, 0.5, 0.7, 0.9] {
                for b: Float in [0.02, 0.05, 0.1, 0.3] {
                    for g: Float in [0.1, 0.3, 0.5, 0.8] {
                        let e = forecastRmse(holtWinters(train, alpha: Double(a), beta: Double(b), gamma: Double(g), horizon: actual.count).forecast, actual)
                        if e < bestRmse { bestRmse = e; best = [Double(a), Double(b), Double(g)] }
                    }
                }
            }
            return best
        },
        evaluate: { points, values in
            let train = seriesTrain(points), actual = seriesActual(points)
            let hw = holtWinters(train, alpha: values[0], beta: values[1], gamma: values[2], horizon: actual.count)
            let inR = rmseOf(train.indices.map { train[$0] - hw.fitted[$0] })
            let out = forecastRmse(hw.forecast, actual), bench = forecastRmse(seasonalNaiveForecast(train, horizon: actual.count), actual)
            var note = "The dashed line is the level component with the seasonal figures removed — the series as Holt-Winters believes it would be without its annual cycle. " + benchmarkNote("Holt-Winters", out, bench)
            if out > inR * 2.5 {
                note += " Note the gap between in-sample \(fmt(inR)) and forecast \(fmt(out)): with these smoothing parameters the model tracks every wobble as if it were signal, so it fits the past well and extrapolates badly. High α, β and γ are not \"more responsive\", they are less smoothed."
            }
            return LabResult(
                curves: [seriesCurve(hw.level, 0, seriesComponent, dashed: true, width: 2), seriesCurve(hw.fitted, 0, labFit), seriesCurve(hw.forecast, trainLength, seriesForecastColor, width: 3)],
                readouts: [LabReadout(value: fmt(inR), label: "In-sample RMSE"), LabReadout(value: fmt(out), label: "Forecast RMSE"), LabReadout(value: fmt(hw.trend.last ?? 0), label: "Final trend/mo")],
                note: note, highlighted: heldOutIndices(points))
        },
        legend: [(labPoint, "Observed"), (labFit, "Fitted"), (seriesComponent, "Level"), (seriesForecastColor, "Forecast")])
}

private func prophetCfg() -> RegConfig {
    RegConfig(
        intro: "Prophet's model form, fitted as one least-squares problem: a piecewise-linear trend with candidate changepoints, plus a Fourier seasonality. The penalty slider is its sparse prior on the slope changes.",
        data: seriesData,
        sliders: [RegSlider(label: "Changepoints", range: 0...8, initial: 4, step: 1, format: { "\(rInt($0))" }),
                  RegSlider(label: "Fourier order", range: 1...4, initial: 2, step: 1, format: { "\(rInt($0))" }),
                  RegSlider(label: "log₁₀ changepoint penalty", range: -2...2, initial: 0, format: expLabel)],
        evaluate: { points, values in
            let cps = rInt(values[0]), order = rInt(values[1])
            let train = seriesTrain(points), actual = seriesActual(points)
            let fit = fitProphet(train, changepointCount: cps, fourierOrder: order, penalty: pow(10, values[2]), horizon: actual.count)
            let out = forecastRmse(fit.forecast, actual), bench = forecastRmse(seasonalNaiveForecast(train, horizon: actual.count), actual)
            let used = fit.deltas.filter { abs($0) > 0.15 }.count
            var note = "y(t) = g(t) + s(t): the dashed line is the trend, the solid one adds the Fourier seasonality of order \(order) (\(2 * order) terms). "
            if cps == 0 {
                note += "With no changepoints the trend is a single straight line for the whole history — and this series changes slope partway through, so one line cannot describe both halves. Forecast error \(fmt(out)). Raise the changepoint count and watch it fall."
            } else {
                note += "\(cps) candidate changepoints at months \(fit.changepoints.map(String.init).joined(separator: ", ")); \(used) of them took a slope change larger than 0.15. The penalty is applied to those slope changes alone — the intercept, the global slope and the seasonal terms are free — which is what stops the model putting a kink at every candidate. " + benchmarkNote("It", out, bench)
            }
            return LabResult(
                curves: [seriesCurve(fit.trend, 0, seriesComponent, dashed: true, width: 2), seriesCurve(fit.forecastTrend, trainLength, seriesComponent, dashed: true, width: 2),
                         seriesCurve(fit.fitted, 0, labFit), seriesCurve(fit.forecast, trainLength, seriesForecastColor, width: 3)],
                readouts: [LabReadout(value: fmt(fit.trainRmse), label: "In-sample RMSE"), LabReadout(value: fmt(out), label: "Forecast RMSE"), LabReadout(value: "\(used) of \(cps)", label: "Slopes used")],
                note: note, highlighted: heldOutIndices(points))
        },
        legend: [(labPoint, "Observed"), (seriesComponent, "Trend g(t)"), (labFit, "g(t) + s(t)"), (seriesForecastColor, "Forecast")])
}

// MARK: Metrics

private func metricPoints(_ seed: Int32) -> [LabPoint] { RegressionMetricsLab.points.map { LabPoint(x: Float($0.x), y: Float($0.y)) } }
private let contaminated = Set(RegressionMetricsLab.points.indices.filter { RegressionMetricsLab.points[$0].contaminated })
private func residuals(_ p: [LabPoint], _ s: Double, _ i: Double) -> [Double] { p.map { Double($0.y) - (i + s * Double($0.x)) } }
private func metricMse(_ p: [LabPoint], _ s: Double, _ i: Double) -> Double { residuals(p, s, i).reduce(0) { $0 + $1 * $1 } / Double(p.count) }
private func metricMae(_ p: [LabPoint], _ s: Double, _ i: Double) -> Double { residuals(p, s, i).reduce(0) { $0 + abs($1) } / Double(p.count) }
private func lineSliders() -> [RegSlider] { [RegSlider(label: "Slope", range: 0.5...2.6, initial: 1.86, symbol: "m"), RegSlider(label: "Intercept", range: -2...6, initial: 0.98, symbol: "c")] }
private func solveLS(_ p: [LabPoint], _ v: [Double]) -> [Double] { let (m, c) = ordinaryLeastSquares(p); return [Double(Float(m)), Double(Float(c))] }
private func pctInt(_ v: Double) -> Int { Int(v.rounded()) }

private func mseCfg() -> RegConfig {
    var c = RegConfig(
        intro: "Forty points on a line plus four from somewhere else. Move the line and watch squared error respond — then press Least Squares to jump to the line that minimises it.",
        data: metricPoints, sliders: lineSliders(), solveLabel: "Least Squares", solve: solveLS,
        evaluate: { points, v in
            let (xMin, xMax) = xRange(points)
            let sq = residuals(points, v[0], v[1]).map { $0 * $0 }
            let bad = contaminated.reduce(0.0) { $0 + sq[$1] } / sq.reduce(0, +)
            let dataShare = pctInt(Double(contaminated.count) * 100 / Double(points.count))
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 1.5) { RegressionMetricsLab.absoluteLossFit.predict(Double($0)) }, sampleCurve(xMin, xMax, labFit) { v[1] + v[0] * Double($0) }],
                readouts: [LabReadout(value: fmt(metricMse(points, v[0], v[1])), label: "MSE"), LabReadout(value: fmt(metricMae(points, v[0], v[1])), label: "MAE")],
                highlighted: contaminated,
                headline: "\(contaminated.count) outliers, \(dataShare)% of the data, cause \(pctInt(bad * 100))% of the squared error.",
                detail: "Squaring rewards the worst points, so least squares tilts toward them. MAE does not.",
                highlight: "\(contaminated.count) outliers", highlightColor: labHighlight, share: bad)
        },
        legend: [(labFit, "Your line"), (labReference, "MAE-optimal"), (labHighlight, "Outlier")])
    c.reseedable = false
    return c
}

private func rmseCfg() -> RegConfig {
    var c = RegConfig(
        intro: "The same fit scored two ways. RMSE is the square root of MSE, which changes nothing about the ranking and everything about whether the number means anything.",
        data: metricPoints, sliders: lineSliders(), solveLabel: "Least Squares", solve: solveLS,
        evaluate: { points, v in
            let (xMin, xMax) = xRange(points)
            let m = metricMse(points, v[0], v[1]), r = m.squareRoot(), a = metricMae(points, v[0], v[1])
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labBand, dashed: true, width: 1.5) { v[1] + v[0] * Double($0) - r }, sampleCurve(xMin, xMax, labBand, dashed: true, width: 1.5) { v[1] + v[0] * Double($0) + r }, sampleCurve(xMin, xMax, labFit) { v[1] + v[0] * Double($0) }],
                readouts: [LabReadout(value: fmt(r), label: "RMSE"), LabReadout(value: fmt(m), label: "MSE"), LabReadout(value: fmt(a), label: "MAE")],
                highlighted: contaminated,
                headline: "RMSE is \(fmt(r)) in the units of y, so the band is a distance you can judge.",
                detail: "MSE is \(fmt(m)) in squared units, which nobody has intuition for. RMSE / MAE is \(fmt(r / a)): the further above 1, the more the error sits in a few points.",
                highlight: "units of y")
        },
        legend: [(labFit, "Your line"), (labBand, "±1 RMSE"), (labHighlight, "Outlier")])
    c.reseedable = false
    return c
}

private func maeCfg() -> RegConfig {
    var c = RegConfig(
        intro: "Two objectives, two different lines on the same data. Minimise MAE jumps to the MAE-optimal fit; the dashed line is where least squares ends up.",
        data: metricPoints, sliders: lineSliders(), solveLabel: "Minimise MAE",
        solve: { _, _ in [Double(Float(RegressionMetricsLab.absoluteLossFit.slope)), Double(Float(RegressionMetricsLab.absoluteLossFit.intercept))] },
        evaluate: { points, v in
            let (xMin, xMax) = xRange(points)
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 1.5) { RegressionMetricsLab.squaredLossFit.predict(Double($0)) }, sampleCurve(xMin, xMax, labFit) { v[1] + v[0] * Double($0) }],
                readouts: [LabReadout(value: fmt(metricMae(points, v[0], v[1])), label: "MAE"), LabReadout(value: fmt(metricMse(points, v[0], v[1])), label: "MSE"), LabReadout(value: fmt(v[0]), label: "Your slope")],
                highlighted: contaminated,
                headline: "Minimising MAE gives slope \(fmt(RegressionMetricsLab.absoluteLossFit.slope)); least squares gives \(fmt(RegressionMetricsLab.squaredLossFit.slope)).",
                detail: "The data was generated with slope \(RegressionMetricsLab.trueSlope). Four outliers dominate a squared penalty but barely move an absolute one, so MAE picks a different, more honest line.",
                highlight: "Minimising MAE")
        },
        legend: [(labFit, "Your line"), (labReference, "Least squares"), (labHighlight, "Outlier")])
    c.reseedable = false
    return c
}

private func rSquaredCfg() -> RegConfig {
    var c = RegConfig(
        intro: "R² compares your line against one specific rival: the horizontal line at the mean of y. Move the line below that baseline and R² goes negative.",
        data: metricPoints, sliders: lineSliders(), solveLabel: "Least Squares", solve: solveLS,
        evaluate: { points, v in
            let (xMin, xMax) = xRange(points)
            let m = mean(points.map { Double($0.y) })
            let res = residuals(points, v[0], v[1]).reduce(0) { $0 + $1 * $1 }
            let tot = points.reduce(0.0) { $0 + (Double($1.y) - m) * (Double($1.y) - m) }
            let r2 = 1 - res / tot
            let pct = "\(pctInt(r2 * 100))%"
            return LabResult(
                curves: [sampleCurve(xMin, xMax, labReference, dashed: true, width: 1.5) { _ in m }, sampleCurve(xMin, xMax, labFit) { v[1] + v[0] * Double($0) }],
                readouts: [LabReadout(value: fmt(r2), label: "R²"), LabReadout(value: fmt(res), label: "SS residual"), LabReadout(value: fmt(tot), label: "SS total")],
                highlighted: contaminated,
                headline: r2 < 0 ? "R² is \(fmt(r2)): your line does worse than predicting the mean."
                    : "R² is \(fmt(r2)): your line explains \(pct) of the variance around the mean.",
                detail: "R² only compares against the dashed line at the mean of y, so a high score says you beat a flat line, not that the model is useful.",
                highlight: r2 < 0 ? "worse than predicting the mean" : pct,
                highlightColor: r2 < 0 ? labHighlight : nil)
        },
        legend: [(labFit, "Your line"), (labReference, "Mean of y"), (labHighlight, "Outlier")])
    c.reseedable = false
    return c
}

private func adjustedR2Cfg() -> RegConfig {
    var c = RegConfig(
        intro: "One slider: how many columns of pure random noise to add to the fit. R² can only rise. Adjusted R² does not have to.",
        data: metricPoints,
        sliders: [RegSlider(label: "Noise columns", range: 0...8, initial: 0, step: 1, format: { "\(rInt($0))" })],
        evaluate: { _, v in
            let extra = rInt(v[0])
            let steps = RegressionMetricsLab.noiseColumns
            let here = steps.first { $0.extraColumns == extra }!
            let first = steps.first!, last = steps.last!
            return LabResult(
                curves: [LabCurve(points: steps.map { (Float($0.extraColumns), Float($0.adjusted)) }, color: labReference, dashed: true, width: 1.5),
                         LabCurve(points: steps.map { (Float($0.extraColumns), Float($0.rSquared)) }, color: labFit)],
                readouts: [LabReadout(value: fmt(here.rSquared), label: "R²"), LabReadout(value: fmt(here.adjusted), label: "Adjusted R²"), LabReadout(value: "\(extra + 1)", label: "Predictors")],
                headline: extra == 0 ? "With no noise columns, R² is \(fmt(here.rSquared)) and adjusted R² is \(fmt(here.adjusted))."
                    : "\(extra == 1 ? "One noise column lifts" : "\(extra) noise columns lift") R² to \(fmt(here.rSquared)), while adjusted R² falls to \(fmt(here.adjusted)).",
                detail: "Least squares can always use one more column to fit noise, so R² never falls. Adjusted R² charges for each column with (n−1)/(n−p−1); across the sweep it goes from \(fmt(first.adjusted)) to \(fmt(last.adjusted)).",
                highlight: extra == 0 ? nil : "adjusted R² falls")
        },
        legend: [(labFit, "R²"), (labReference, "Adjusted R²")])
    c.reseedable = false
    return c
}

private let regressionConfigs: [String: () -> RegConfig] = [
    "mse": mseCfg, "rmse": rmseCfg, "mae": maeCfg, "r_squared": rSquaredCfg, "adjusted_r_squared": adjustedR2Cfg,
    "moving_average": movingAverageCfg, "autoregression": autoregressionCfg, "arima": arimaCfg, "sarima": sarimaCfg,
    "exponential_smoothing": expSmoothingCfg, "prophet": prophetCfg,
    "polynomial_regression": polynomial, "ridge_regression": ridge, "lasso_regression": lasso, "elasticnet_regression": elasticNet,
    "stepwise_regression": stepwise, "robust_regression": robust, "quantile_regression": quantile, "bayesian_ridge": bayesian,
    "poisson_regression": poisson, "isotonic_regression": isotonic, "lars": lars,
]

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class RegressionLabModel {
    var seed: Int32 = 4
    var values: [Double]
    var points: [LabPoint] = []
    var result: LabResult?

    init(values: [Double]) { self.values = values }
}

// Parameter-lab layout: a stage card (plot + legend), readout chips, narration, then the sliders and
// the New Data / solve buttons — pinned in thumb reach when docked.
struct RegressionLab: View {
    private let config: RegConfig
    @State private var model: RegressionLabModel
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        let c = (regressionConfigs[topicId] ?? polynomial)()
        config = c
        _model = State(initialValue: RegressionLabModel(values: c.sliders.map(\.initial)))
    }

    private struct Key: Equatable { let seed: Int32; let values: [Double] }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: config.intro, bottom: 12)
            LabCard {
                ZStack {
                    if let result = model.result { RegressionLabPlot(points: model.points, result: result) } else { ProgressView() }
                }
                .aspectRatio(1.45, contentMode: .fit)
                .background(Color.primary.opacity(0.03))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                if !config.legend.isEmpty, let result = model.result {
                    RegressionLabLegend(items: config.legend, result: result).padding(.top, 12)
                }
                if dock == nil, let result = model.result {
                    RegressionLabReadouts(result: result).padding(.top, 16)
                    RegressionLabNarration(result: result).padding(.top, 14)
                    Divider().padding(.top, 16)
                    RegressionLabControls(config: config, model: model).padding(.top, 14)
                }
            }
            if dock != nil, let result = model.result {
                RegressionLabReadouts(result: result).padding(.top, 14)
                RegressionLabNarration(result: result).padding(.horizontal, 4).padding(.top, 16)
            }
        }
        .onAppear {
            guard let dock else { return }
            let config = config, model = model
            dock.controls = { AnyView(RegressionLabControls(config: config, model: model)) }
        }
        .onDisappear { dock?.controls = nil }
        .task(id: Key(seed: model.seed, values: model.values)) {
            let config = config, seed = model.seed, values = model.values
            try? await Task.sleep(for: .milliseconds(30))
            if Task.isCancelled { return }
            let (pts, r) = await Task.detached(priority: .userInitiated) { () -> ([LabPoint], LabResult) in
                let pts = config.data(seed)
                return (pts, config.evaluate(pts, values.map { Double(Float($0)) }))
            }.value
            if Task.isCancelled { return }
            model.points = pts
            model.result = r
        }
    }
}

private struct RegressionLabControls: View {
    let config: RegConfig
    @Bindable var model: RegressionLabModel

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 14) {
                ForEach(config.sliders.indices, id: \.self) { i in
                    let s = config.sliders[i]
                    LabParamSlider(name: s.label, symbol: s.symbol, value: $model.values[i], range: s.range, step: s.step, format: s.format)
                }
            }
            let solve = config.solve.flatMap { solve in config.solveLabel.map { ($0, solve) } }
            if config.reseedable || solve != nil {
                HStack(spacing: 12) {
                    if config.reseedable { LabButton(label: "New Data") { model.seed += 1 } }
                    if let (label, solve) = solve {
                        LabButton(label: label, primary: true) { withAnimation { model.values = solve(model.points, model.values) } }
                    }
                }
            }
        }
    }
}

private struct RegressionLabReadouts: View {
    let result: LabResult

    var body: some View {
        ReadoutChips(parts: result.readouts.map { (key: $0.label, value: $0.value) }, accented: [0],
                     trailing: result.share.map { AnyView(ShareChip(share: $0)) })
    }
}

/// A bar filled to `share` in the highlight colour, then its percentage.
private struct ShareChip: View {
    let share: Double

    var body: some View {
        HStack(spacing: 10) {
            ZStack(alignment: .leading) {
                Capsule().fill(SimColors.tint)
                Capsule().fill(labHighlight).frame(width: 96 * min(max(share, 0), 1))
            }
            .frame(width: 96, height: 6)
            .clipShape(Capsule())
            Text("\(Int((share * 100).rounded()))%").font(AppFont.mono(15)).fontWeight(.semibold)
        }
        .padding(.horizontal, 12)
        .frame(height: 32)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
    }
}

private struct RegressionLabNarration: View {
    let result: LabResult
    @Environment(\.palette) private var palette

    var body: some View {
        let (head, tail): (String, String?) = result.headline.map { ($0, result.detail) } ?? LabCaption.split(result.note)
        let key = result.highlightColor ?? (palette.dark ? SimColors.active : Color(hex: 0xB45309))
        VStack(alignment: .leading, spacing: 8) {
            headline(head, key)
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            if let tail {
                Text(tail)
                    .font(AppFont.sans(15))
                    .foregroundStyle(palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func headline(_ h: String, _ key: Color) -> Text {
        guard let hl = result.highlight, let r = h.range(of: hl) else { return Text(h) }
        return Text(h[..<r.lowerBound]) + Text(h[r]).foregroundColor(key) + Text(h[r.upperBound...])
    }
}

/// Data points: pale on dark, a mid grey on light so they don't wash out.
private func labPointColor(_ palette: Palette) -> Color { palette.dark ? SimColors.idle : SimColors.grey }

/// A curve's colour as drawn: the fit takes the accent, everything else keeps its own.
private func drawnColor(_ color: Color, _ palette: Palette) -> Color {
    color == labFit ? palette.primary : color == labPoint ? labPointColor(palette) : color
}

/// Legend swatches follow what the entry is on the plot: a line (dashed when its curve is) for a
/// curve's colour, a ring for highlighted points, a dot otherwise.
private struct RegressionLabLegend: View {
    let items: [(Color, String)]
    let result: LabResult
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 16, lineSpacing: 6) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                let (color, label) = item
                let curve = result.curves.first { $0.color == color }
                let drawn = drawnColor(color, palette)
                HStack(spacing: 6) {
                    if let curve {
                        Path { p in p.move(to: CGPoint(x: 0, y: 1.5)); p.addLine(to: CGPoint(x: 16, y: 1.5)) }
                            .stroke(drawn, style: StrokeStyle(lineWidth: 3, dash: curve.dashed ? [3, 2] : []))
                            .frame(width: 16, height: 3)
                    } else if color == labHighlight {
                        Circle().stroke(drawn, lineWidth: 2).frame(width: 10, height: 10)
                    } else {
                        Circle().fill(drawn).frame(width: 11, height: 11)
                    }
                    Text(label).font(AppFont.sans(14)).foregroundStyle(palette.onSurface.opacity(0.8))
                }
            }
        }
    }
}

private struct RegressionLabPlot: View {
    let points: [LabPoint]
    let result: LabResult
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in draw(ctx, size) }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let pad: CGFloat = 14
        // Curve x's count too: a forecast is drawn past the last observation.
        let xs = points.map(\.x) + result.curves.flatMap { $0.points.map(\.0) }
        let xMin = (xs.min() ?? 0) - 0.3, xMax = (xs.max() ?? 1) + 0.3
        // Clamp the curves' contribution: an unpenalized degree-9 fit can shoot to ±1e4 between points.
        let dataYs = points.map(\.y)
        let dMin = dataYs.min() ?? 0, dMax = dataYs.max() ?? 1
        let slack = max(dMax - dMin, 1) * 0.6
        let curveYs = result.curves.flatMap { $0.points.map(\.1) }.filter { $0.isFinite && $0 >= dMin - slack && $0 <= dMax + slack }
        var yMin = (dataYs + curveYs).min() ?? 0, yMax = (dataYs + curveYs).max() ?? 1
        if yMax - yMin < 1 { yMax = yMin + 1 }
        let yr = yMax - yMin
        yMin -= yr * 0.08; yMax += yr * 0.08
        func px(_ x: Float) -> CGFloat { pad + CGFloat((x - xMin) / (xMax - xMin)) * (size.width - 2 * pad) }
        func py(_ y: Float) -> CGFloat { size.height - pad - CGFloat((y - yMin) / (yMax - yMin)) * (size.height - 2 * pad) }
        for i in 0...4 {
            let gy = pad + CGFloat(i) * (size.height - 2 * pad) / 4
            ctx.line(CGPoint(x: pad, y: gy), CGPoint(x: size.width - pad, y: gy), color: palette.outline.opacity(0.6), width: 1)
        }
        for curve in result.curves {
            // One path so dashes flow along the curve. Off-scale segments are skipped, not clipped —
            // a clipped spike would read as a feature of the fit.
            var path = Path()
            var open = false
            for (a, b) in zip(curve.points, curve.points.dropFirst()) {
                guard a.1.isFinite, b.1.isFinite else { open = false; continue }
                let ay = min(max(a.1, yMin), yMax), by = min(max(b.1, yMin), yMax)
                if ay != a.1 && by != b.1 { open = false; continue }
                if !open { path.move(to: CGPoint(x: px(a.0), y: py(ay))); open = true }
                path.addLine(to: CGPoint(x: px(b.0), y: py(by)))
            }
            ctx.stroke(path, with: .color(drawnColor(curve.color, palette)),
                       style: StrokeStyle(lineWidth: curve.width, lineJoin: .round, dash: curve.dashed ? [5, 4] : []))
        }
        let pointColor = labPointColor(palette)
        for (i, p) in points.enumerated() {
            let c = CGPoint(x: px(p.x), y: py(p.y))
            if result.highlighted.contains(i) {
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 7, y: c.y - 7, width: 14, height: 14)), with: .color(labHighlight.opacity(0.55)))
                ctx.stroke(Path(ellipseIn: CGRect(x: c.x - 8, y: c.y - 8, width: 16, height: 16)), with: .color(labHighlight), lineWidth: 2)
            } else {
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 3.5, y: c.y - 3.5, width: 7, height: 7)), with: .color(pointColor))
            }
        }
    }
}
