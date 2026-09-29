import SwiftUI

// Port of RegressionStoryLabs.kt: the eleven regression estimators as one-figure labs. A plot in the
// card (with a coefficient strip under it for the sparse fits), a legend of what is on it, chips, a
// headline that says what the current setting shows, then a picker over one stepper, docked in thumb
// reach. Every fit is computed by the real algorithm in RegressionLabMath.swift.

let regressionStoryTopicIds: Set<String> = [
    "polynomial_regression", "ridge_regression", "lasso_regression", "elasticnet_regression",
    "stepwise_regression", "robust_regression", "quantile_regression", "bayesian_ridge",
    "poisson_regression", "isotonic_regression", "lars",
]

private enum RegDotKind { case plain, below, heldOut, discarded }

private struct RegDot { let x: Double; let y: Double; var kind: RegDotKind = .plain }

private struct RegLine { let points: [(Double, Double)]; let fit: Bool }

private struct RegBand { let lower: [(Double, Double)]; let upper: [(Double, Double)] }

/// A shaded x-range: a block PAVA pooled, or a stretch with no data (labelled).
private struct RegColumn { let x0: Double; let x1: Double; let gap: Bool }

/// One coefficient in the strip under the plot; `size` is |β| over the largest, 0 when zeroed.
private struct RegBar { let label: String; let size: Double }

private enum RegSwatch { case line, dashed, fill, dot, ring }

private enum RegInk { case fit, reference, point, blue, red, band, pool }

private struct RegKey { let swatch: RegSwatch; let ink: RegInk; let label: String }

private struct RegFrame {
    let dots: [RegDot]
    let lines: [RegLine]
    let headline: String
    let body: String
    let chips: [(String, String)]
    var legend: [RegKey] = []
    var band: RegBand? = nil
    var columns: [RegColumn] = []
    var negativeZone = false
    var bars: [RegBar] = []
}

/// A stepper over a fixed ladder of values; `initial` is an index into `values`.
private struct RegParam {
    let tab: String
    let name: String
    let symbol: String
    let values: [Double]
    let initial: Int
    let format: (Double) -> String
}

/// A solve button: beside the picker (`inline`) or full width under the stepper.
private struct RegAction { let label: String; let inline: Bool; let solve: ([Int]) -> [Int] }

private struct RegStoryConfig {
    let params: [RegParam]
    let evaluate: ([Int]) -> RegFrame
    var initialTab = 0
    var action: RegAction? = nil
    var initial: [Int] { params.map(\.initial) }
}

// MARK: Formatting

/// Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−".
private func rfx(_ v: Double, _ d: Int = 3) -> String {
    var p: Int64 = 1
    for _ in 0..<d { p *= 10 }
    let r = Int64((abs(v) * Double(p) + 0.5).rounded(.down))
    let body = d == 0 ? "\(r / p)" : "\(r / p)." + String(repeating: "0", count: max(0, d - String(r % p).count)) + String(r % p)
    return v < 0 && r != 0 ? "−" + body : body
}

/// Two significant-ish digits for a norm that runs from tens down to fractions.
private func rShort(_ v: Double) -> String { abs(v) >= 10 ? rfx(v, 0) : rfx(v, 1) }

private let superscripts: [Character: Character] = ["0": "⁰", "1": "¹", "2": "²", "3": "³", "4": "⁴", "5": "⁵", "6": "⁶", "7": "⁷", "8": "⁸", "9": "⁹", "-": "⁻"]

private func sup(_ s: String) -> String { String(s.map { superscripts[$0] ?? $0 }) }

private func power(_ j: Int) -> String { "x" + sup(String(j)) }

/// "x², x⁹, x¹"; past `limit` terms, the first three and a count.
private func powers(_ terms: [Int], limit: Int = 4) -> String {
    terms.count <= limit ? terms.map(power).joined(separator: ", ")
        : terms.prefix(3).map(power).joined(separator: ", ") + " +\(terms.count - 3)"
}

/// 10 to a log step: "10⁻²" on whole steps, "10^-1.4" between them.
private func tenTo(_ e: Double) -> String {
    let whole = Int(e.rounded())
    return abs(e - Double(whole)) < 1e-6 ? "10" + sup(String(whole)) : "10^" + rfx(e, 1).replacingOccurrences(of: "−", with: "-")
}

private let numberWords = ["No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten"]

private func countWord(_ n: Int) -> String { n < numberWords.count ? numberWords[n] : "\(n)" }

private func plural(_ n: Int) -> String { n == 1 ? "" : "s" }

private func ladder(_ from: Double, _ to: Double, _ step: Double) -> [Double] {
    let count = Int(((to - from) / step).rounded())
    return (0...count).map { from + Double($0) * step }
}

private func nearest(_ values: [Double], _ v: Double) -> Int {
    values.indices.min { abs(values[$0] - v) < abs(values[$1] - v) } ?? 0
}

// MARK: Data
// Fixed seeds: the headlines quote these exact numbers, and Reset returns to them.

/// Rise, plateau, dip, rise: a shape a low-degree polynomial cannot follow and degree 9 overfits.
private func wavyPoints() -> [LabPoint] {
    var r = KotlinRandom(4)
    return (0..<24).map { i in
        let x = Float(i) * 0.4
        let y: Float = 3 + 1.6 * sinf(x * 0.75) + 0.18 * x + (r.nextFloat() - 0.5) * 1.6
        return LabPoint(x: x / 9.2, y: (y - 1) / 5)
    }
}

/// Every fourth point is held out of the fit, none at the ends, so held-out error never extrapolates.
private func heldOutOf(_ points: [LabPoint]) -> Set<Int> { Set(points.indices.filter { $0 % 4 == 2 }) }

/// Twenty-seven points on a line and five from somewhere else, the last five.
private func contaminatedPoints() -> [LabPoint] {
    var r = KotlinRandom(4)
    let clean = (0..<27).map { i -> LabPoint in
        let x = (Float(i) + 0.5) / 27
        return LabPoint(x: x, y: 0.12 + 0.85 * x + (r.nextFloat() - 0.5) * 0.12)
    }
    let far = (0..<5).map { i in LabPoint(x: 0.12 + Float(i) * 0.12, y: 1.12 + r.nextFloat() * 0.1) }
    return clean + far
}

/// Spread grows with x, so quantile lines fan out.
private func fanPoints() -> [LabPoint] {
    var r = KotlinRandom(4)
    return (0..<32).map { i in
        let x = Float(i) / 31
        let spread: Float = 0.05 + 0.3 * x
        return LabPoint(x: x, y: 0.2 + 0.55 * x + (r.nextFloat() - 0.5) * 2 * spread)
    }
}

/// Poisson counts with a log-linear mean.
private func countPoints() -> [LabPoint] {
    var r = KotlinRandom(4)
    return (0..<30).map { i in
        let x = Float(i) * 0.3
        let mean = exp(-0.4 + 0.42 * Double(x))
        // Knuth's sampler; the means are small, so the loop is short.
        var k = 0, p = 1.0
        let limit = exp(-mean)
        repeat { k += 1; p *= r.nextDouble() } while p > limit
        return LabPoint(x: x, y: Float(k - 1))
    }
}

/// A trend that never falls but is flat in stretches, where isotonic's steps come from.
private func monotonePoints() -> [LabPoint] {
    var r = KotlinRandom(4)
    return (0..<28).map { i in
        let x = Float(i) * 0.32
        let trend: Float = x < 2.5 ? 1.2 + 0.15 * x : x < 5.5 ? 2.6 + 0.9 * (x - 2.5) : 5.3 + 0.1 * (x - 5.5)
        return LabPoint(x: x / 8.64, y: (trend + (r.nextFloat() - 0.5) * 1.3 - 0.5) / 5.5)
    }
}

private func span(_ points: [LabPoint]) -> (Float, Float) { (points.map(\.x).min() ?? 0, points.map(\.x).max() ?? 1) }

private func curve(_ xMin: Float, _ xMax: Float, _ f: (Double) -> Double) -> [(Double, Double)] {
    (0...80).map { i in
        let x = Double(xMin) + Double(xMax - xMin) * Double(i) / 80
        return (x, f(x))
    }
}

private func polyCurve(_ beta: [Double], _ xMin: Float, _ xMax: Float) -> [(Double, Double)] {
    curve(xMin, xMax) { polyValue(beta, scaleX(Float($0), xMin, xMax)) }
}

private func polyMse(_ points: [LabPoint], _ beta: [Double], _ xMin: Float, _ xMax: Float) -> Double {
    mse(points) { polyValue(beta, scaleX($0, xMin, xMax)) }
}

private func coefficientBars(_ beta: [Double]) -> [RegBar] {
    let sizes = (1...9).map { $0 < beta.count ? abs(beta[$0]) : 0 }
    let top = max(sizes.max() ?? 0, 1e-9)
    return sizes.enumerated().map { RegBar(label: power($0.offset + 1), size: $0.element > 1e-6 ? $0.element / top : 0) }
}

private func nonZero(_ beta: [Double]) -> Int { (1..<beta.count).filter { abs(beta[$0]) > 1e-6 }.count }

private func ys(_ points: [LabPoint]) -> [Double] { points.map { Double($0.y) } }

private func trainTest(_ points: [LabPoint]) -> (Set<Int>, [LabPoint], [LabPoint]) {
    let test = heldOutOf(points)
    return (test, points.indices.filter { !test.contains($0) }.map { points[$0] }, points.indices.filter { test.contains($0) }.map { points[$0] })
}

// MARK: Topics

private func polynomialStory() -> RegStoryConfig {
    let points = wavyPoints()
    let (xMin, xMax) = span(points)
    let (test, train, holdout) = trainTest(points)
    let fits = (1...9).map { d in ridgeSolve(designMatrix(train.map(\.x), degree: d, xMin: xMin, xMax: xMax), ys(train), 0) }
    let testErrors = fits.map { polyMse(holdout, $0, xMin, xMax) }
    let best = testErrors.indices.min { testErrors[$0] < testErrors[$1] } ?? 0
    return RegStoryConfig(
        params: [RegParam(tab: "Degree", name: "Degree", symbol: "", values: (1...9).map(Double.init), initial: 8, format: { "\(Int($0.rounded()))" })],
        evaluate: { idx in
            let d = idx[0] + 1
            let beta = fits[idx[0]]
            let trainMse = polyMse(train, beta, xMin, xMax)
            let testMse = testErrors[idx[0]]
            let ratio = testMse / max(trainMse, 1e-9)
            let body: String
            if idx[0] == best { body = "Held-out error is lowest at this degree, so this is the one to keep." }
            else if idx[0] < best { body = "Both errors still fall with more degrees: this curve is too stiff for the data." }
            else if ratio >= 1.5 { body = "Held-out error is \(rfx(ratio, 1))× the training error: the extra terms fit noise." }
            else { body = "Held-out error has started to rise: the extra terms fit noise." }
            return RegFrame(
                dots: points.enumerated().map { RegDot(x: Double($0.element.x), y: Double($0.element.y), kind: test.contains($0.offset) ? .heldOut : .plain) },
                lines: [RegLine(points: polyCurve(beta, xMin, xMax), fit: true)],
                headline: "Degree \(d) fits \(d + 1) coefficients to \(train.count) points: training error \(rfx(trainMse)), held-out \(rfx(testMse)).",
                body: body,
                chips: [("train MSE", rfx(trainMse)), ("held-out", rfx(testMse)), ("coefs", "\(d + 1)")],
                legend: [RegKey(swatch: .dot, ink: .point, label: "Train"), RegKey(swatch: .ring, ink: .blue, label: "Held out")])
        },
        action: RegAction(label: "Best by held-out", inline: false) { _ in [best] })
}

private func ridgeStory() -> RegStoryConfig {
    let points = wavyPoints()
    let (xMin, xMax) = span(points)
    let (test, train, holdout) = trainTest(points)
    let x = designMatrix(train.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
    let y = ys(train)
    let plain = ridgeSolve(x, y, 0)
    let plainTest = polyMse(holdout, plain, xMin, xMax)
    let plainNorm = sqrt((1...9).reduce(0.0) { $0 + plain[$1] * plain[$1] })
    let exponents = ladder(-6, 1, 0.5)
    let fits = exponents.map { ridgeSolve(x, y, pow(10, $0)) }
    let testErrors = fits.map { polyMse(holdout, $0, xMin, xMax) }
    let best = testErrors.indices.min { testErrors[$0] < testErrors[$1] } ?? 0
    return RegStoryConfig(
        params: [RegParam(tab: "Penalty", name: "Penalty", symbol: "log₁₀ λ", values: exponents, initial: nearest(exponents, -2), format: { rfx($0, 1) })],
        evaluate: { idx in
            let e = exponents[idx[0]]
            let beta = fits[idx[0]]
            let testNow = testErrors[idx[0]]
            let norm = sqrt((1...9).reduce(0.0) { $0 + beta[$1] * beta[$1] })
            let tooFar = testNow > testErrors[best] * 1.5 && idx[0] > best
            return RegFrame(
                dots: points.enumerated().map { RegDot(x: Double($0.element.x), y: Double($0.element.y), kind: test.contains($0.offset) ? .heldOut : .plain) },
                lines: [RegLine(points: polyCurve(plain, xMin, xMax), fit: false), RegLine(points: polyCurve(beta, xMin, xMax), fit: true)],
                headline: tooFar ? "At λ = \(tenTo(e)) ridge shrinks too far: held-out error rises to \(rfx(testNow))."
                    : "Ridge shrinks all 9 weights and zeroes none.",
                body: tooFar ? "Every weight is still non-zero, but the curve has gone too flat to follow the real shape."
                    : "Smaller weights mean fewer wiggles, so the curve stops chasing training noise.",
                chips: [("held-out", "\(rfx(plainTest)) → \(rfx(testNow))"), ("‖β‖", "\(rShort(plainNorm)) → \(rShort(norm))")],
                legend: [RegKey(swatch: .line, ink: .fit, label: "λ = \(tenTo(e))"),
                         RegKey(swatch: .dashed, ink: .reference, label: "λ = 0"),
                         RegKey(swatch: .ring, ink: .blue, label: "Held out")])
        },
        action: RegAction(label: "Best by held-out", inline: false) { _ in [best] })
}

private func lassoStory() -> RegStoryConfig {
    let points = wavyPoints()
    let (xMin, xMax) = span(points)
    let x = designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
    let y = ys(points)
    let exponents = ladder(-4, -1, 0.2)
    return RegStoryConfig(
        params: [RegParam(tab: "Penalty", name: "Penalty", symbol: "log₁₀ λ", values: exponents, initial: nearest(exponents, -2.4), format: { rfx($0, 1) })],
        evaluate: { idx in
            let e = exponents[idx[0]]
            let lambda = pow(10, e)
            let lasso = coordinateDescent(x, y, lambda: lambda, l1Ratio: 1)
            let ridge = ridgeSolve(x, y, lambda * Double(points.count))
            let kept = nonZero(lasso), ridgeKept = nonZero(ridge)
            let zeroed = 9 - kept
            let headline: String, body: String
            switch zeroed {
            case 0:
                headline = "At λ = \(tenTo(e)) lasso still keeps all 9 terms, like ridge."
                body = "The penalty is too weak to reach L1's corner at zero. Raise λ and weights start landing on it."
            case 9:
                headline = "At λ = \(tenTo(e)) lasso zeroes all 9 terms, leaving a flat line at the mean."
                body = "No term earns back its penalty any more. Lower λ and the strongest ones return first."
            default:
                headline = "At λ = \(tenTo(e)) lasso zeroes \(zeroed) of 9 terms. Ridge keeps \(ridgeKept == 9 ? "all 9" : "\(ridgeKept)")."
                body = "L1 has a corner at zero, so weights can land exactly on it. L2 only shrinks them."
            }
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y)) },
                lines: [RegLine(points: polyCurve(ridge, xMin, xMax), fit: false), RegLine(points: polyCurve(lasso, xMin, xMax), fit: true)],
                headline: headline,
                body: body,
                chips: [("non-zero", "\(kept) of 9"), ("ridge", "\(ridgeKept) of 9"), ("MSE", rfx(polyMse(points, lasso, xMin, xMax)))],
                legend: [RegKey(swatch: .line, ink: .fit, label: "Lasso"), RegKey(swatch: .dashed, ink: .reference, label: "Ridge, same λ")],
                bars: coefficientBars(lasso))
        })
}

private func elasticNetStory() -> RegStoryConfig {
    let points = wavyPoints()
    let (xMin, xMax) = span(points)
    let x = designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
    let y = ys(points)
    let exponents = ladder(-4, -1, 0.2)
    let ratios = ladder(0, 1, 0.1)
    return RegStoryConfig(
        params: [
            RegParam(tab: "Penalty λ", name: "Penalty", symbol: "log₁₀ λ", values: exponents, initial: nearest(exponents, -2.2), format: { rfx($0, 1) }),
            RegParam(tab: "L1 ratio", name: "L1 ratio", symbol: "", values: ratios, initial: 5, format: { rfx($0, 1) }),
        ],
        evaluate: { idx in
            let lambda = pow(10, exponents[idx[0]])
            let ratio = ratios[idx[1]]
            let beta = coordinateDescent(x, y, lambda: lambda, l1Ratio: ratio)
            let lasso = coordinateDescent(x, y, lambda: lambda, l1Ratio: 1)
            let kept = nonZero(beta), lassoKept = nonZero(lasso)
            let headline: String, body: String
            if kept == 0 {
                headline = "With L1 ratio \(rfx(ratio, 1)), every term is zeroed: λ is too strong."
                body = "No term earns back its penalty. Lower λ to let the strongest ones return."
            } else if idx[1] == 0 {
                headline = "With L1 ratio 0 this is pure ridge: \(kept) of 9 terms stay."
                body = "Nothing reaches zero without the L1 half. Raise the ratio to start pruning."
            } else if idx[1] == ratios.count - 1 {
                headline = "With L1 ratio 1 this is pure lasso: \(kept) of 9 terms survive."
                body = "Among correlated powers lasso keeps one and drops the rest, somewhat arbitrarily."
            } else {
                headline = kept == lassoKept
                    ? "With L1 ratio \(rfx(ratio, 1)), \(kept) of 9 terms survive, the same as pure lasso."
                    : "With L1 ratio \(rfx(ratio, 1)), \(kept) of 9 terms survive, against \(lassoKept) for pure lasso."
                body = "The L2 half keeps correlated powers together instead of picking one at random. The L1 half still zeroes the rest."
            }
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y)) },
                lines: [RegLine(points: polyCurve(beta, xMin, xMax), fit: true)],
                headline: headline,
                body: body,
                chips: [("non-zero", "\(kept) of 9"), ("lasso alone", "\(lassoKept) of 9"), ("MSE", rfx(polyMse(points, beta, xMin, xMax)))],
                bars: coefficientBars(beta))
        },
        initialTab: 1)
}

private func stepwiseStory() -> RegStoryConfig {
    let points = wavyPoints()
    let (xMin, xMax) = span(points)
    let x = designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
    let y = ys(points)
    let results = (1...9).map { forwardStepwise(x, y, terms: $0) }
    return RegStoryConfig(
        params: [RegParam(tab: "Terms kept", name: "Terms kept", symbol: "", values: (1...9).map(Double.init), initial: 2, format: { "\(Int($0.rounded()))" })],
        evaluate: { idx in
            let result = results[idx[0]]
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y)) },
                lines: [RegLine(points: polyCurve(result.beta, xMin, xMax), fit: true)],
                headline: "Forward selection kept \(result.selected.map(power).joined(separator: ", ")), picked by this same data.",
                body: "So R² and p-values look better than they are. Only a held-out set gives an honest score.",
                chips: [("kept", powers(result.selected)), ("adj R²", rfx(result.adjustedR2)), ("MSE", rfx(polyMse(points, result.beta, xMin, xMax)))])
        })
}

private func robustStory() -> RegStoryConfig {
    let points = contaminatedPoints()
    let clean = 27
    let (xMin, xMax) = span(points)
    let (olsM, olsC) = ordinaryLeastSquares(points)
    let thresholds = ladder(0.02, 0.3, 0.02)
    return RegStoryConfig(
        params: [RegParam(tab: "Inlier threshold", name: "Inlier threshold", symbol: "", values: thresholds, initial: nearest(thresholds, 0.1), format: { rfx($0, 2) })],
        evaluate: { idx in
            let t = thresholds[idx[0]]
            let fit = ransac(points, threshold: t)
            let kept = fit.inliers.count
            let goodOut = (0..<clean).filter { !fit.inliers.contains($0) }.count
            let badIn = (clean..<points.count).filter { fit.inliers.contains($0) }.count
            let far = points.count - clean
            let line = { (v: Double) in fit.slope * v + fit.intercept }
            let headline: String, body: String
            if badIn > 0 {
                headline = "A band of ±\(rfx(t, 2)) is wide enough to let \(badIn) outlier\(plural(badIn)) back in."
                body = "Once inside, they are averaged like any other point, and the slope moves to \(rfx(fit.slope, 2))."
            } else if goodOut > 0 {
                headline = "A band of ±\(rfx(t, 2)) also throws out \(goodOut) good point\(plural(goodOut))."
                body = "RANSAC still fits the \(kept) it keeps, but a band tighter than the noise wastes real data."
            } else {
                headline = "\(countWord(far)) far-off points pull the OLS slope \(olsM < fit.slope ? "down" : "up") to \(rfx(olsM, 2))."
                body = "RANSAC keeps the \(kept) points inside the band and fits only those, so the outliers never enter the average."
            }
            return RegFrame(
                dots: points.enumerated().map { RegDot(x: Double($0.element.x), y: Double($0.element.y), kind: fit.inliers.contains($0.offset) ? .plain : .discarded) },
                lines: [RegLine(points: curve(xMin, xMax) { olsM * $0 + olsC }, fit: false), RegLine(points: curve(xMin, xMax, line), fit: true)],
                headline: headline,
                body: body,
                chips: [("slope", rfx(fit.slope, 2)), ("OLS slope", rfx(olsM, 2)), ("inliers", "\(kept)/\(points.count)")],
                legend: [RegKey(swatch: .line, ink: .fit, label: "RANSAC"),
                         RegKey(swatch: .fill, ink: .band, label: "± threshold"),
                         RegKey(swatch: .dashed, ink: .reference, label: "OLS"),
                         RegKey(swatch: .ring, ink: .red, label: "Discarded")],
                band: RegBand(lower: curve(xMin, xMax) { line($0) - t }, upper: curve(xMin, xMax) { line($0) + t }))
        })
}

private func quantileStory() -> RegStoryConfig {
    let points = fanPoints()
    let (xMin, xMax) = span(points)
    let taus = ladder(0.1, 0.9, 0.1)
    let fits = taus.map { quantileFit(points, tau: $0) }
    let references = [0, 4, 8]
    return RegStoryConfig(
        params: [RegParam(tab: "Quantile", name: "Quantile", symbol: "τ", values: taus, initial: 8, format: { rfx($0, 1) })],
        evaluate: { idx in
            let tau = taus[idx[0]]
            let (m, c) = fits[idx[0]]
            let isBelow = { (p: LabPoint) in Double(p.y) < m * Double(p.x) + c }
            let below = points.filter(isBelow).count
            let target = Int((tau * 100).rounded())
            let others = references.filter { $0 != idx[0] }
            let close = abs(Double(below) / Double(points.count) - tau) <= 0.07
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y), kind: isBelow($0) ? .below : .plain) },
                lines: others.map { r in RegLine(points: curve(xMin, xMax) { fits[r].0 * $0 + fits[r].1 }, fit: false) }
                    + [RegLine(points: curve(xMin, xMax) { m * $0 + c }, fit: true)],
                headline: "\(below) of \(points.count) points sit below the τ = \(rfx(tau, 1)) line, \(close ? "close to" : "against") the \(target)% it aims for.",
                body: "The lines fan out because the spread grows with x. One mean line would hide that.",
                chips: [("below", "\(below)/\(points.count)"), ("target", "\(target)%"), ("pinball", rfx(pinballLoss(points, tau: tau, m: m, c: c)))],
                legend: [RegKey(swatch: .line, ink: .fit, label: "τ = \(rfx(tau, 1)) fit"),
                         RegKey(swatch: .dot, ink: .blue, label: "Below the line"),
                         RegKey(swatch: .dashed, ink: .reference, label: "τ = " + others.map { rfx(taus[$0], 1) }.joined(separator: ", "))])
        })
}

private let bayesDegree = 7

private func bayesianStory() -> RegStoryConfig {
    let gapFrom: Float = 0.3, gapTo: Float = 0.68
    let points = wavyPoints().filter { !($0.x > gapFrom && $0.x < gapTo) }
    let (xMin, xMax) = span(points)
    let x = designMatrix(points.map(\.x), degree: bayesDegree, xMin: xMin, xMax: xMax)
    let y = ys(points)
    let left = Double(points.filter { $0.x <= gapFrom }.map(\.x).max() ?? gapFrom)
    let right = Double(points.filter { $0.x >= gapTo }.map(\.x).min() ?? gapTo)
    let phiAt = { (v: Double) -> [Double] in
        let t = scaleX(Float(v), xMin, xMax)
        return (0...bayesDegree).map { pow(t, Double($0)) }
    }
    let alphas = [0.1, 0.3, 1.0, 3.0, 10.0]
    let noises = [0.005, 0.01, 0.02, 0.04, 0.08]
    return RegStoryConfig(
        params: [
            RegParam(tab: "Prior precision α", name: "Prior precision", symbol: "α", values: alphas, initial: 2, format: { rfx($0, 1) }),
            RegParam(tab: "Noise variance", name: "Noise variance", symbol: "σ²", values: noises, initial: 1, format: { $0 < 0.01 ? rfx($0, 3) : rfx($0, 2) }),
        ],
        evaluate: { idx in
            let fit = bayesianRidge(x, y, alpha: alphas[idx[0]], noiseVariance: noises[idx[1]])
            let mean = { (v: Double) in polyValue(fit.mean, scaleX(Float(v), xMin, xMax)) }
            let sd = { (v: Double) in predictiveStd(fit, phiAt(v)) }
            let gapSd = sd((left + right) / 2)
            let denseSd = sd(0.15)
            let ratio = gapSd / max(denseSd, 1e-9)
            let wide = ratio >= 1.15
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y)) },
                lines: [RegLine(points: curve(xMin, xMax, mean), fit: true)],
                headline: wide ? "The band is \(rfx(ratio, 1))× wider in the gap, where no data pins the curve down."
                    : "The band is barely wider in the gap than where the data is dense.",
                body: wide ? "Plain ridge draws the same mean curve with no sign that the middle is a guess."
                    : "A strong prior speaks for the gap instead of the data. Lower α and the gap's uncertainty shows.",
                chips: [("σ in the gap", rfx(gapSd, 2)), ("σ where dense", rfx(denseSd, 2)), ("ratio", rfx(ratio, 1) + "×")],
                legend: [RegKey(swatch: .line, ink: .fit, label: "Posterior mean"), RegKey(swatch: .fill, ink: .band, label: "± 2σ")],
                band: RegBand(lower: curve(xMin, xMax) { mean($0) - 2 * sd($0) }, upper: curve(xMin, xMax) { mean($0) + 2 * sd($0) }),
                columns: [RegColumn(x0: left, x1: right, gap: true)])
        })
}

private func poissonStory() -> RegStoryConfig {
    let points = countPoints()
    let (xMin, xMax) = span(points)
    let (olsM, olsC) = ordinaryLeastSquares(points)
    let (fitB0, fitB1) = poissonIrls(points)
    let bestDeviance = poissonDeviance(points, fitB0, fitB1)
    let intercepts = ladder(-2, 2, 0.1)
    let slopes = ladder(-0.2, 0.8, 0.02)
    return RegStoryConfig(
        params: [
            RegParam(tab: "β₀ intercept", name: "Intercept", symbol: "β₀", values: intercepts, initial: nearest(intercepts, -0.4), format: { rfx($0, 1) }),
            RegParam(tab: "β₁ slope", name: "Slope", symbol: "β₁", values: slopes, initial: nearest(slopes, 0.42), format: { rfx($0, 2) }),
        ],
        evaluate: { idx in
            let b0 = intercepts[idx[0]], b1 = slopes[idx[1]]
            let deviance = poissonDeviance(points, b0, b1)
            let crossing = olsM > 0 ? -olsC / olsM : .nan
            let negative = crossing.isFinite && crossing > Double(xMin)
            let body: String
            if deviance > bestDeviance * 1.25 {
                body = "Deviance is \(rfx(deviance)) here against \(rfx(bestDeviance)) at the best fit. Fit by IRLS jumps there."
            } else if negative {
                body = "The log link rules that out: exp is positive everywhere, and each unit of x multiplies the count."
            } else {
                body = "exp is positive everywhere, so unlike a straight line the curve can never predict a negative count."
            }
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y)) },
                lines: [RegLine(points: curve(xMin, xMax) { olsM * $0 + olsC }, fit: false),
                        RegLine(points: curve(xMin, xMax) { exp(min(max(b0 + b1 * $0, -20), 20)) }, fit: true)],
                headline: negative ? "The OLS line drops below zero for x < \(rfx(crossing, 1)), predicting negative counts."
                    : "Each unit of x multiplies the expected count by \(rfx(exp(b1), 2)).",
                body: body,
                chips: [("deviance", rfx(deviance)), ("OLS at x=0", rfx(olsC, 2)), ("per unit x", rfx(exp(b1), 2) + "×")],
                legend: [RegKey(swatch: .line, ink: .fit, label: "exp(β₀ + β₁x)"),
                         RegKey(swatch: .dashed, ink: .reference, label: "OLS line"),
                         RegKey(swatch: .fill, ink: .red, label: "Negative counts")],
                negativeZone: true)
        },
        initialTab: 1,
        action: RegAction(label: "Fit by IRLS", inline: true) { _ in [nearest(intercepts, fitB0), nearest(slopes, fitB1)] })
}

private func isotonicStory() -> RegStoryConfig {
    let base = monotonePoints()
    let noises = ladder(0, 0.5, 0.1)
    return RegStoryConfig(
        params: [RegParam(tab: "Extra noise", name: "Extra noise", symbol: "", values: noises, initial: 0, format: { rfx($0, 1) })],
        evaluate: { idx in
            let extra = noises[idx[0]]
            var r = KotlinRandom(9)
            let points = base.map { LabPoint(x: $0.x, y: $0.y + Float(Double(r.nextFloat() - 0.5) * 2 * extra)) }
            let fit = pava(points)
            let (m, c) = ordinaryLeastSquares(points)
            let (xMin, xMax) = span(points)
            var steps: [(Double, Double)] = []
            for (i, x) in fit.xs.enumerated() {
                if i > 0 { steps.append((Double(x), fit.ys[i - 1])) } // the riser
                steps.append((Double(x), fit.ys[i]))
            }
            // Each pooled block spans its points, widened a little either side; short of half the
            // spacing, so two pools next to each other still read as two.
            let half = fit.xs.count > 1 ? Double(fit.xs[1] - fit.xs[0]) * 0.35 : 0
            var start = 0
            var pools: [RegColumn] = []
            for size in fit.sizes {
                if size > 1 { pools.append(RegColumn(x0: Double(fit.xs[start]) - half, x1: Double(fit.xs[start + size - 1]) + half, gap: false)) }
                start += size
            }
            let error = mse(points) { v in fit.ys[max(fit.xs.lastIndex { $0 <= v } ?? 0, 0)] }
            let body: String
            switch pools.count {
            case 0: body = "The data never went down, so no points had to be pooled."
            case 1: body = "The one shaded pool is a place where the data went down. The fit uses its mean, so it never decreases."
            default: body = "Each of the \(pools.count) shaded pools is a place where the data went down. The fit uses their mean, so it never decreases."
            }
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y)) },
                lines: [RegLine(points: curve(xMin, xMax) { m * $0 + c }, fit: false), RegLine(points: steps, fit: true)],
                headline: "PAVA turned \(points.count) points into \(fit.blocks) flat steps.",
                body: body,
                chips: [("blocks", "\(fit.blocks)"), ("points", "\(points.count)"), ("MSE", rfx(error))],
                legend: [RegKey(swatch: .line, ink: .fit, label: "Isotonic fit"),
                         RegKey(swatch: .fill, ink: .pool, label: "Pooled block"),
                         RegKey(swatch: .dashed, ink: .reference, label: "Linear fit")],
                columns: pools)
        })
}

private func larsStory() -> RegStoryConfig {
    let points = wavyPoints()
    let (xMin, xMax) = span(points)
    let x = designMatrix(points.map(\.x), degree: 9, xMin: xMin, xMax: xMax)
    let steps = larsSteps(x, ys(points))
    let total = steps.count
    return RegStoryConfig(
        params: [RegParam(tab: "Path step", name: "Path step", symbol: "", values: (1...max(total, 1)).map(Double.init), initial: 1, format: { "\(Int($0.rounded())) of \(total)" })],
        evaluate: { idx in
            let k = idx[0] + 1
            let step = steps[idx[0]]
            let joined = power(step.entered)
            let headline: String, body: String
            switch k {
            case 1:
                headline = "Step 1: \(joined) is the most correlated with the residual, so it enters first."
                body = "Its coefficient grows only until another predictor is just as correlated."
            case 2:
                headline = "Step 2: \(joined) is now as correlated with the residual as \(power(step.active[0])), so it joins."
                body = "Both coefficients move together from here. Neither is fully fitted before the next predictor is considered."
            default:
                headline = "Step \(k): \(joined) is now as correlated with the residual as the other \(k - 1), so it joins."
                body = "All \(k) coefficients move together from here. None is fully fitted before the next predictor is considered."
            }
            return RegFrame(
                dots: points.map { RegDot(x: Double($0.x), y: Double($0.y)) },
                lines: [RegLine(points: polyCurve(step.beta, xMin, xMax), fit: true)],
                headline: headline,
                body: body,
                chips: [("active", powers(step.active)), (k == 1 ? "|corr|" : "|corr| tie", rfx(step.tie))])
        })
}

private let regressionStories: [String: () -> RegStoryConfig] = [
    "polynomial_regression": polynomialStory, "ridge_regression": ridgeStory, "lasso_regression": lassoStory,
    "elasticnet_regression": elasticNetStory, "stepwise_regression": stepwiseStory, "robust_regression": robustStory,
    "quantile_regression": quantileStory, "bayesian_ridge": bayesianStory, "poisson_regression": poissonStory,
    "isotonic_regression": isotonicStory, "lars": larsStory,
]

// MARK: View

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class RegStoryModel {
    let config: RegStoryConfig
    private(set) var indices: [Int]
    private(set) var frame: RegFrame
    var selected: Int

    init(config: RegStoryConfig) {
        self.config = config
        indices = config.initial
        frame = config.evaluate(config.initial)
        selected = config.initialTab
    }

    func set(_ next: [Int]) {
        guard next != indices else { return }
        indices = next
        frame = config.evaluate(next)
    }

    func step(_ param: Int, _ delta: Int) {
        var next = indices
        next[param] = min(max(next[param] + delta, 0), config.params[param].values.count - 1)
        set(next)
    }

    func reset() {
        set(config.initial)
        selected = config.initialTab
    }
}

struct RegressionStoryLab: View {
    @State private var model: RegStoryModel
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        _model = State(initialValue: RegStoryModel(config: (regressionStories[topicId] ?? polynomialStory)()))
    }

    var body: some View {
        let frame = model.frame
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                RegPlotView(frame: frame)
                    .aspectRatio(frame.bars.isEmpty ? 1.32 : 1.75, contentMode: .fit)
                    .background(Color.black.opacity(0.16))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                if !frame.bars.isEmpty { RegBarsView(bars: frame.bars).frame(height: 58).padding(.top, 10) }
                if !frame.legend.isEmpty { RegLegendView(items: frame.legend).padding(.top, 14) }
            }
            ReadoutChips(parts: frame.chips.map { (key: Optional($0.0), value: $0.1) }, accented: [0]).padding(.top, 16)
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                RegStoryControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(RegStoryControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.reset() }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct RegStoryControls: View {
    @Bindable var model: RegStoryModel
    @Environment(\.palette) private var palette

    var body: some View {
        let config = model.config
        let index = min(max(model.selected, 0), config.params.count - 1)
        let param = config.params[index]
        let at = model.indices[index]
        VStack(spacing: 14) {
            if config.params.count > 1 || config.action?.inline == true {
                HStack(spacing: 10) {
                    if config.params.count > 1 { LabSegments(labels: config.params.map(\.tab), selected: $model.selected) } else { Spacer() }
                    if let action = config.action, action.inline {
                        Button { model.set(action.solve(model.indices)) } label: {
                            Text(action.label)
                                .font(AppFont.sans(14, .semibold))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                                .padding(.horizontal, 14)
                                .frame(height: 34)
                                .background(palette.primary, in: RoundedRectangle(cornerRadius: 9))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            LabParamStepper(param: LabParam(tab: param.tab, name: param.name, symbol: param.symbol, text: param.format(param.values[at]),
                                            canDecrease: at > 0, canIncrease: at < param.values.count - 1)) { model.step(index, $0) }
            if let action = config.action, !action.inline {
                LabButton(label: action.label, primary: true) { model.set(action.solve(model.indices)) }
            }
        }
    }
}

private func regInk(_ ink: RegInk, _ palette: Palette) -> Color {
    switch ink {
    case .fit: palette.primary
    case .reference: SimColors.grey
    case .point: palette.dark ? SimColors.idle : SimColors.grey
    case .blue: SimColors.blue
    case .red: SimColors.red
    case .band: palette.primary.opacity(0.35)
    case .pool: SimColors.blue.opacity(0.35)
    }
}

private struct RegLegendView: View {
    let items: [RegKey]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 14, lineSpacing: 6) {
            ForEach(items.indices, id: \.self) { i in
                let item = items[i]
                let color = regInk(item.ink, palette)
                HStack(spacing: 6) {
                    switch item.swatch {
                    case .line, .dashed:
                        Path { p in p.move(to: CGPoint(x: 0, y: 1.5)); p.addLine(to: CGPoint(x: 16, y: 1.5)) }
                            .stroke(color, style: StrokeStyle(lineWidth: 3, dash: item.swatch == .dashed ? [3, 2] : []))
                            .frame(width: 16, height: 3)
                    // Red marks a shaded zone here, so its swatch is as faint as the zone.
                    case .fill: RoundedRectangle(cornerRadius: 3).fill(item.ink == .red ? color.opacity(0.4) : color).frame(width: 14, height: 10)
                    case .dot: Circle().fill(color).frame(width: 9, height: 9)
                    case .ring: Circle().stroke(color, lineWidth: 2).frame(width: 10, height: 10)
                    }
                    Text(item.label).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
                }
            }
        }
    }
}

private struct RegPlotView: View {
    let frame: RegFrame
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in draw(ctx, size) }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let pad: CGFloat = 16
        let xs = frame.dots.map(\.x)
        let xLo = xs.min() ?? 0, xHi = xs.max() ?? 1
        let xSpan = max(xHi - xLo, 1e-6)
        let xMin = xLo - xSpan * 0.03, xMax = xHi + xSpan * 0.03

        // Curves may shoot far past the data between points (an unpenalized degree 9); only the part
        // near the data sets the scale, or it would flatten the points to a line.
        let dataYs = frame.dots.map(\.y)
        let dMin = dataYs.min() ?? 0, dMax = dataYs.max() ?? 1
        let slack = max(dMax - dMin, 1e-6) * 0.35
        let bandYs = frame.band.map { ($0.lower + $0.upper).map(\.1) } ?? []
        let extraYs = (frame.lines.flatMap { $0.points.map(\.1) } + bandYs).filter { $0.isFinite && $0 >= dMin - slack && $0 <= dMax + slack }
        var yMin = (dataYs + extraYs).min() ?? 0, yMax = (dataYs + extraYs).max() ?? 1
        if frame.negativeZone { yMin = min(yMin, -(yMax - yMin) * 0.12) }
        let yPad = max(yMax - yMin, 1e-6) * 0.06
        yMin -= yPad
        yMax += yPad

        func px(_ x: Double) -> CGFloat { pad + CGFloat((x - xMin) / (xMax - xMin)) * (size.width - 2 * pad) }
        func py(_ y: Double) -> CGFloat { size.height - pad - CGFloat((y - yMin) / (yMax - yMin)) * (size.height - 2 * pad) }

        for col in frame.columns {
            let left = max(px(col.x0), 0), right = min(px(col.x1), size.width)
            ctx.fill(Path(CGRect(x: left, y: 0, width: right - left, height: size.height)),
                     with: .color(col.gap ? palette.onSurface.opacity(0.06) : SimColors.blue.opacity(0.16)))
            if col.gap {
                ctx.draw(Text("no data").font(AppFont.sans(12)).foregroundColor(palette.muted), at: CGPoint(x: (left + right) / 2, y: 8), anchor: .top)
            }
        }
        if frame.negativeZone {
            let zero = py(0)
            ctx.fill(Path(CGRect(x: 0, y: zero, width: size.width, height: size.height - zero)), with: .color(SimColors.red.opacity(0.14)))
            ctx.line(CGPoint(x: 0, y: zero), CGPoint(x: size.width, y: zero), color: palette.muted.opacity(0.4), width: 1)
        }

        if let band = frame.band {
            var path = Path()
            for (i, p) in band.upper.enumerated() {
                if i == 0 { path.move(to: CGPoint(x: px(p.0), y: py(p.1))) } else { path.addLine(to: CGPoint(x: px(p.0), y: py(p.1))) }
            }
            for p in band.lower.reversed() { path.addLine(to: CGPoint(x: px(p.0), y: py(p.1))) }
            path.closeSubpath()
            ctx.fill(path, with: .color(palette.primary.opacity(0.24)))
        }
        let lo = yMin - (yMax - yMin), hi = yMax + (yMax - yMin)
        for line in frame.lines.filter({ !$0.fit }) + frame.lines.filter(\.fit) {
            // One path so dashes flow along the curve; stretches far off-scale are skipped rather than
            // clipped, so a spike doesn't read as a feature of the fit.
            var path = Path()
            var open = false
            for (a, b) in zip(line.points, line.points.dropFirst()) {
                guard a.1 >= lo, a.1 <= hi, b.1 >= lo, b.1 <= hi else { open = false; continue }
                if !open { path.move(to: CGPoint(x: px(a.0), y: py(a.1))); open = true }
                path.addLine(to: CGPoint(x: px(b.0), y: py(b.1)))
            }
            ctx.stroke(path, with: .color(line.fit ? palette.primary : SimColors.grey),
                       style: StrokeStyle(lineWidth: line.fit ? 3 : 1.5, lineCap: .round, lineJoin: .round, dash: line.fit ? [] : [5, 4]))
        }

        let pointColor = regInk(.point, palette)
        for dot in frame.dots {
            let c = CGPoint(x: px(dot.x), y: py(dot.y))
            func circle(_ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)) }
            switch dot.kind {
            case .plain:
                ctx.fill(circle(4), with: .color(pointColor))
                ctx.stroke(circle(4), with: .color(.black.opacity(0.3)), lineWidth: 1)
            case .below:
                ctx.fill(circle(4.5), with: .color(SimColors.blue))
            case .heldOut:
                ctx.fill(circle(7), with: .color(SimColors.blue.opacity(0.3)))
                ctx.stroke(circle(7), with: .color(SimColors.blue), lineWidth: 2)
            case .discarded:
                ctx.fill(circle(7), with: .color(SimColors.red.opacity(0.4)))
                ctx.stroke(circle(7), with: .color(SimColors.red), lineWidth: 2)
            }
        }
    }
}

/// The coefficient strip: a bar per power, a flat dash where the penalty zeroed it.
private struct RegBarsView: View {
    let bars: [RegBar]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let labelHeight: CGFloat = 16
            let base = size.height - labelHeight - 4
            let tall = base - 2
            let slot = size.width / CGFloat(bars.count)
            let barWidth: CGFloat = 16
            for (i, bar) in bars.enumerated() {
                let cx = slot * (CGFloat(i) + 0.5)
                if bar.size > 0 {
                    let h = max(tall * CGFloat(bar.size), 4)
                    ctx.fill(Path(roundedRect: CGRect(x: cx - barWidth / 2, y: base - h, width: barWidth, height: h), cornerRadius: 3),
                             with: .color(palette.primary))
                } else {
                    ctx.line(CGPoint(x: cx - 6, y: base - 1), CGPoint(x: cx + 6, y: base - 1), color: palette.muted.opacity(0.5), width: 2)
                }
                ctx.draw(Text(bar.label).font(AppFont.mono(11)).foregroundColor(bar.size > 0 ? palette.muted : palette.muted.opacity(0.45)),
                         at: CGPoint(x: cx, y: size.height - labelHeight), anchor: .top)
            }
        }
    }
}
