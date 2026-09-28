import Foundation

// Port of DecisionSurfaceMath.kt: a working SMO for the SVM dual, closed-form discriminant
// analysis, and the Passive-Aggressive online update. Points are Float as in Kotlin, so the seeded
// datasets match Android exactly.

struct ClassPoint { let x: Float; let y: Float; let label: Int } // label is -1 or +1

typealias Kernel = (Float, Float, Float, Float) -> Double

func linearKernel() -> Kernel { { ax, ay, bx, by in Double(ax * bx + ay * by) } }

func rbfKernel(_ gamma: Double) -> Kernel {
    { ax, ay, bx, by in
        let dx = Double(ax - bx), dy = Double(ay - by)
        return exp(-gamma * (dx * dx + dy * dy))
    }
}

final class SvmFit {
    let alphas: [Double]
    let bias: Double
    let points: [ClassPoint]
    let kernel: Kernel
    let supportVectors: Set<Int>

    init(alphas: [Double], bias: Double, points: [ClassPoint], kernel: @escaping Kernel) {
        self.alphas = alphas
        self.bias = bias
        self.points = points
        self.kernel = kernel
        supportVectors = Set(alphas.indices.filter { alphas[$0] > 1e-5 })
    }

    func decision(_ x: Float, _ y: Float) -> Double {
        var sum = 0.0
        for i in points.indices where alphas[i] > 1e-8 {
            sum += alphas[i] * Double(points[i].label) * kernel(points[i].x, points[i].y, x, y)
        }
        return sum + bias
    }

    /// Points inside the margin or misclassified — the ones a nu-SVM bounds from above.
    func marginErrors() -> Set<Int> { Set(points.indices.filter { Double(points[$0].label) * decision(points[$0].x, points[$0].y) < 1.0 - 1e-6 }) }
    func misclassified() -> Set<Int> { Set(points.indices.filter { Double(points[$0].label) * decision(points[$0].x, points[$0].y) <= 0 }) }
}

/// Simplified Platt SMO: one KKT violator, a random partner, the pair optimised in closed form.
func trainSvm(_ points: [ClassPoint], kernel: @escaping Kernel, c: Double, passes: Int = 12, tolerance: Double = 1e-3, seed: Int32 = 7) -> SvmFit {
    let n = points.count
    var alphas = [Double](repeating: 0, count: n)
    var bias = 0.0
    if n == 0 { return SvmFit(alphas: alphas, bias: bias, points: points, kernel: kernel) }
    var random = KotlinRandom(seed)
    let k = (0..<n).map { i in (0..<n).map { j in kernel(points[i].x, points[i].y, points[j].x, points[j].y) } }
    func f(_ i: Int) -> Double {
        var sum = bias
        for j in 0..<n where alphas[j] > 1e-12 { sum += alphas[j] * Double(points[j].label) * k[j][i] }
        return sum
    }
    var passesWithoutChange = 0, iterations = 0
    while passesWithoutChange < passes && iterations < 4000 {
        iterations += 1
        var changed = 0
        for i in 0..<n {
            let yi = Double(points[i].label)
            let ei = f(i) - yi
            let violates = (yi * ei < -tolerance && alphas[i] < c) || (yi * ei > tolerance && alphas[i] > 0)
            if !violates { continue }
            var j = random.nextInt(n)
            if j == i { j = (j + 1) % n }
            let yj = Double(points[j].label)
            let ej = f(j) - yj
            let oldI = alphas[i], oldJ = alphas[j]
            let (low, high) = points[i].label != points[j].label
                ? (max(0, oldJ - oldI), min(c, c + oldJ - oldI))
                : (max(0, oldI + oldJ - c), min(c, oldI + oldJ))
            if high - low < 1e-12 { continue }
            let eta = 2 * k[i][j] - k[i][i] - k[j][j]
            if eta >= -1e-12 { continue }
            var newJ = oldJ - yj * (ei - ej) / eta
            newJ = min(max(newJ, low), high)
            if abs(newJ - oldJ) < 1e-7 { continue }
            let newI = oldI + Double(points[i].label * points[j].label) * (oldJ - newJ)
            alphas[i] = newI
            alphas[j] = newJ
            let b1 = bias - ei - yi * (newI - oldI) * k[i][i] - yj * (newJ - oldJ) * k[i][j]
            let b2 = bias - ej - yi * (newI - oldI) * k[i][j] - yj * (newJ - oldJ) * k[j][j]
            bias = newI > 0 && newI < c ? b1 : newJ > 0 && newJ < c ? b2 : (b1 + b2) / 2
            changed += 1
        }
        passesWithoutChange = changed == 0 ? passesWithoutChange + 1 : 0
    }
    return SvmFit(alphas: alphas, bias: bias, points: points, kernel: kernel)
}

struct NuFit { let fit: SvmFit; let c: Double; let svFraction: Double; let marginErrorFraction: Double }

/// nu-SVC via a log-spaced sweep of C for the support-vector fraction closest to nu.
func trainNuSvc(_ points: [ClassPoint], kernel: @escaping Kernel, nu: Double) -> NuFit {
    var best: NuFit?
    for step in 0...22 {
        let c = pow(10.0, -2.0 + Double(step) * 0.2)
        let fit = trainSvm(points, kernel: kernel, c: c)
        let sv = Double(fit.supportVectors.count) / Double(points.count)
        let err = Double(fit.marginErrors().count) / Double(points.count)
        let cand = NuFit(fit: fit, c: c, svFraction: sv, marginErrorFraction: err)
        if best == nil || abs(sv - nu) < abs(best!.svFraction - nu) { best = cand }
    }
    return best ?? NuFit(fit: trainSvm(points, kernel: kernel, c: 1), c: 1, svFraction: 0, marginErrorFraction: 0)
}

struct Gaussian2D {
    let meanX: Double, meanY: Double
    let covariance: [[Double]]
    let prior: Double
    private let inverse: [[Double]]
    private let logDet: Double

    init(meanX: Double, meanY: Double, covariance: [[Double]], prior: Double) {
        self.meanX = meanX
        self.meanY = meanY
        self.covariance = covariance
        self.prior = prior
        let a = covariance[0][0], b = covariance[0][1], d = covariance[1][1]
        let det = max(a * d - b * b, 1e-9)
        inverse = [[d / det, -b / det], [-b / det, a / det]]
        logDet = log(det)
    }

    func logScore(_ x: Double, _ y: Double) -> Double {
        let dx = x - meanX, dy = y - meanY
        let quad = dx * (inverse[0][0] * dx + inverse[0][1] * dy) + dy * (inverse[1][0] * dx + inverse[1][1] * dy)
        return -0.5 * quad - 0.5 * logDet + log(prior)
    }

    /// Ellipse at a Mahalanobis radius, via the closed-form 2x2 eigendecomposition.
    func ellipse(_ radius: Double, segments: Int = 60) -> [(Float, Float)] {
        let a = covariance[0][0], b = covariance[0][1], d = covariance[1][1]
        let trace = a + d, det = a * d - b * b
        let disc = max(0, trace * trace / 4 - det).squareRoot()
        let l1 = trace / 2 + disc, l2 = trace / 2 - disc
        let angle = abs(b) < 1e-12 ? 0 : atan2(l1 - a, b)
        let rx = radius * max(1e-9, l1).squareRoot(), ry = radius * max(1e-9, l2).squareRoot()
        return (0...segments).map { i in
            let t = 2 * Double.pi * Double(i) / Double(segments)
            let px = rx * cos(t), py = ry * sin(t)
            return (Float(meanX + px * cos(angle) - py * sin(angle)), Float(meanY + px * sin(angle) + py * cos(angle)))
        }
    }
}

struct DiscriminantFit {
    let negative: Gaussian2D
    let positive: Gaussian2D
    func decision(_ x: Float, _ y: Float) -> Double { positive.logScore(Double(x), Double(y)) - negative.logScore(Double(x), Double(y)) }
}

private func average(_ xs: [Double], _ fallback: Double) -> Double { xs.isEmpty ? fallback : xs.reduce(0, +) / Double(xs.count) }

private func covarianceOf(_ points: [ClassPoint], _ mx: Double, _ my: Double, shrink: Double = 0) -> [[Double]] {
    var sxx = 0.0, sxy = 0.0, syy = 0.0
    for p in points {
        let dx = Double(p.x) - mx, dy = Double(p.y) - my
        sxx += dx * dx; sxy += dx * dy; syy += dy * dy
    }
    let n = Double(max(1, points.count - 1))
    let cxx = sxx / n, cxy = sxy / n, cyy = syy / n
    let meanVar = (cxx + cyy) / 2
    return [[(1 - shrink) * cxx + shrink * meanVar, (1 - shrink) * cxy], [(1 - shrink) * cxy, (1 - shrink) * cyy + shrink * meanVar]]
}

private func means(_ pts: [ClassPoint]) -> (Double, Double) {
    (average(pts.map { Double($0.x) }, 0), average(pts.map { Double($0.y) }, 0))
}

/// LDA: one pooled covariance, which is what makes the boundary linear.
func fitLda(_ points: [ClassPoint], shrink: Double = 0) -> DiscriminantFit {
    let neg = points.filter { $0.label < 0 }, pos = points.filter { $0.label > 0 }
    let nm = means(neg), pm = means(pos)
    let nc = covarianceOf(neg, nm.0, nm.1), pc = covarianceOf(pos, pm.0, pm.1)
    let wn = Double(max(1, neg.count - 1)), wp = Double(max(1, pos.count - 1))
    let raw = (0..<2).map { i in (0..<2).map { j in (wn * nc[i][j] + wp * pc[i][j]) / (wn + wp) } }
    let meanVar = (raw[0][0] + raw[1][1]) / 2
    let pooled = (0..<2).map { i in (0..<2).map { j in (1 - shrink) * raw[i][j] + (i == j ? shrink * meanVar : 0) } }
    let total = Double(points.count)
    return DiscriminantFit(negative: Gaussian2D(meanX: nm.0, meanY: nm.1, covariance: pooled, prior: Double(neg.count) / total),
                           positive: Gaussian2D(meanX: pm.0, meanY: pm.1, covariance: pooled, prior: Double(pos.count) / total))
}

/// QDA: each class keeps its own covariance, so the boundary becomes a conic.
func fitQda(_ points: [ClassPoint], shrink: Double = 0) -> DiscriminantFit {
    let neg = points.filter { $0.label < 0 }, pos = points.filter { $0.label > 0 }
    let nm = means(neg), pm = means(pos)
    let total = Double(points.count)
    return DiscriminantFit(negative: Gaussian2D(meanX: nm.0, meanY: nm.1, covariance: covarianceOf(neg, nm.0, nm.1, shrink: shrink), prior: Double(neg.count) / total),
                           positive: Gaussian2D(meanX: pm.0, meanY: pm.1, covariance: covarianceOf(pos, pm.0, pm.1, shrink: shrink), prior: Double(pos.count) / total))
}

/// Gaussian naive Bayes = QDA with the off-diagonal terms forced to zero.
func fitGaussianNb(_ points: [ClassPoint], smoothing: Double = 0) -> DiscriminantFit {
    let full = fitQda(points)
    func diag(_ g: Gaussian2D) -> Gaussian2D {
        Gaussian2D(meanX: g.meanX, meanY: g.meanY, covariance: [[g.covariance[0][0] + smoothing, 0], [0, g.covariance[1][1] + smoothing]], prior: g.prior)
    }
    return DiscriminantFit(negative: diag(full.negative), positive: diag(full.positive))
}

struct PaState {
    let w1: Double, w2: Double, bias: Double
    let updates: Int, mistakes: Int
    let lastLoss: Double, lastTau: Double
    let lastIndex: Int
}

enum PaVariant { case hard, paI, paII }

/// One pass over the stream, snapshotting after every example.
func passiveAggressivePath(_ points: [ClassPoint], variant: PaVariant, aggressiveness: Double) -> [PaState] {
    var w1 = 0.0, w2 = 0.0, bias = 0.0
    var updates = 0, mistakes = 0
    var states = [PaState(w1: 0, w2: 0, bias: 0, updates: 0, mistakes: 0, lastLoss: 0, lastTau: 0, lastIndex: -1)]
    for (index, p) in points.enumerated() {
        let x = Double(p.x), y = Double(p.y), label = Double(p.label)
        let margin = w1 * x + w2 * y + bias
        let loss = max(0, 1 - label * margin)
        if label * margin <= 0 { mistakes += 1 }
        var tau = 0.0
        if loss > 0 {
            let norm2 = Double(p.x * p.x + p.y * p.y + 1)
            switch variant {
            case .hard: tau = loss / norm2
            case .paI: tau = min(aggressiveness, loss / norm2)
            case .paII: tau = loss / (norm2 + 1 / (2 * aggressiveness))
            }
            w1 += tau * label * x
            w2 += tau * label * y
            bias += tau * label
            updates += 1
        }
        states.append(PaState(w1: w1, w2: w2, bias: bias, updates: updates, mistakes: mistakes, lastLoss: loss, lastTau: tau, lastIndex: index))
    }
    return states
}

func ringData(seed: Int32, n: Int = 90) -> [ClassPoint] {
    var r = KotlinRandom(seed)
    return (0..<n).map { i in
        let inner = i % 2 == 0
        let radius: Float = inner ? 0.9 + r.nextFloat() * 0.7 : 2.5 + r.nextFloat() * 0.8
        let angle = r.nextFloat() * 2 * Float(Double.pi)
        return ClassPoint(x: radius * Float(cos(Double(angle))), y: radius * Float(sin(Double(angle))), label: inner ? 1 : -1)
    }
}

func overlappingBlobs(seed: Int32, n: Int = 80, separation: Float = 1.7) -> [ClassPoint] {
    var r = KotlinRandom(seed)
    return (0..<n).map { i in
        let positive = i % 2 == 0
        let cx = positive ? separation : -separation
        let x = cx + (r.nextFloat() + r.nextFloat() - 1) * 1.6
        let y = (r.nextFloat() + r.nextFloat() - 1) * 1.6
        return ClassPoint(x: x, y: y, label: positive ? 1 : -1)
    }
}

func unequalCovarianceBlobs(seed: Int32, n: Int = 80) -> [ClassPoint] {
    var r = KotlinRandom(seed)
    return (0..<n).map { i in
        let positive = i % 2 == 0
        let u = r.nextFloat() + r.nextFloat() - 1
        let v = r.nextFloat() + r.nextFloat() - 1
        if positive {
            let a = u * 2.6, b = v * 0.5
            return ClassPoint(x: 1.1 + (a - b) * 0.707, y: 1.1 + (a + b) * 0.707, label: 1)
        }
        return ClassPoint(x: -1.0 + u * 1.0, y: -0.9 + v * 1.0, label: -1)
    }
}

func streamData(seed: Int32, n: Int = 60) -> [ClassPoint] {
    var r = KotlinRandom(seed)
    return (0..<n).map { i in
        let positive = i % 2 == 0
        let flip = i == 44
        let cx: Float = positive ? 1.6 : -1.6
        let x = cx + (r.nextFloat() - 0.5) * 1.8
        let y = (r.nextFloat() - 0.5) * 3
        return ClassPoint(x: x, y: y, label: positive != flip ? 1 : -1)
    }
}
