import Foundation

// Port of RegressionLabMath.kt: every regression fit on the Regression topics is computed by the
// real algorithm. Float/Double mixing follows the Kotlin exactly so the numbers match Android.

struct LabPoint { let x: Float; let y: Float }

/// Polynomial design matrix, intercept in column 0, x rescaled to [-1, 1] first.
func designMatrix(_ xs: [Float], degree: Int, xMin: Float, xMax: Float) -> [[Double]] {
    let span = max(1e-6, xMax - xMin)
    return xs.map { x in
        let t = Double(2 * (x - xMin) / span - 1)
        return (0...degree).map { p in p == 0 ? 1 : pow(t, Double(p)) }
    }
}

func scaleX(_ x: Float, _ xMin: Float, _ xMax: Float) -> Double {
    Double(2 * (x - xMin) / max(1e-6, xMax - xMin) - 1)
}

func polyValue(_ coefficients: [Double], _ t: Double) -> Double {
    var sum = 0.0, power = 1.0
    for c in coefficients { sum += c * power; power *= t }
    return sum
}

/// (XᵀX + λI)β = Xᵀy by Gaussian elimination with partial pivoting; intercept unpenalized.
func ridgeSolve(_ x: [[Double]], _ y: [Double], _ lambda: Double, penalizeIntercept: Bool = false) -> [Double] {
    let p = x.first?.count ?? 0
    if p == 0 { return [] }
    var a = [[Double]](repeating: [Double](repeating: 0, count: p + 1), count: p)
    for i in 0..<p {
        for j in 0..<p {
            var s = 0.0
            for r in x.indices { s += x[r][i] * x[r][j] }
            a[i][j] = s
        }
        if lambda > 0 && (penalizeIntercept || i > 0) { a[i][i] += lambda }
        var rhs = 0.0
        for r in x.indices { rhs += x[r][i] * y[r] }
        a[i][p] = rhs
    }
    for col in 0..<p {
        var pivot = col
        for r in (col + 1)..<max(p, col + 1) where abs(a[r][col]) > abs(a[pivot][col]) { pivot = r }
        a.swapAt(col, pivot)
        if abs(a[col][col]) < 1e-12 { a[col][col] = 1e-12 }
        for r in 0..<p where r != col {
            let f = a[r][col] / a[col][col]
            if f == 0 { continue }
            for c in col...p { a[r][c] -= f * a[col][c] }
        }
    }
    return (0..<p).map { a[$0][p] / a[$0][$0] }
}

/// Coordinate descent with soft-thresholding (lasso / elastic net).
func coordinateDescent(_ x: [[Double]], _ y: [Double], lambda: Double, l1Ratio: Double, iterations: Int = 300) -> [Double] {
    let n = x.count
    let p = x.first?.count ?? 0
    if p == 0 { return [] }
    var beta = [Double](repeating: 0, count: p)
    let norms = (0..<p).map { j -> Double in
        var s = 0.0
        for r in 0..<n { s += x[r][j] * x[r][j] }
        return s < 1e-12 ? 1e-12 : s
    }
    var residual = y
    let l1 = lambda * l1Ratio * Double(n)
    let l2 = lambda * (1 - l1Ratio) * Double(n)
    for _ in 0..<iterations {
        for j in 0..<p {
            var rho = 0.0
            for r in 0..<n {
                residual[r] += beta[j] * x[r][j]
                rho += x[r][j] * residual[r]
            }
            beta[j] = j == 0 ? rho / norms[j] : softThreshold(rho, l1) / (norms[j] + l2)
            for r in 0..<n { residual[r] -= beta[j] * x[r][j] }
        }
    }
    return beta
}

private func softThreshold(_ v: Double, _ t: Double) -> Double { v > t ? v - t : v < -t ? v + t : 0 }

func mse(_ points: [LabPoint], _ predict: (Float) -> Double) -> Double {
    if points.isEmpty { return 0 }
    var sum = 0.0
    for p in points { let e = predict(p.x) - Double(p.y); sum += e * e }
    return sum / Double(points.count)
}

struct RansacFit { let slope: Double; let intercept: Double; let inliers: Set<Int> }

func ransac(_ points: [LabPoint], threshold: Double, trials: Int = 200, seed: Int32 = 3) -> RansacFit {
    if points.count < 2 { return RansacFit(slope: 0, intercept: 0, inliers: []) }
    var random = KotlinRandom(seed)
    var best = RansacFit(slope: 0, intercept: 0, inliers: [])
    for _ in 0..<trials {
        let i = random.nextInt(points.count)
        var j = random.nextInt(points.count)
        if j == i { j = (j + 1) % points.count }
        let (x1, y1) = (points[i].x, points[i].y), (x2, y2) = (points[j].x, points[j].y)
        if abs(x2 - x1) < 1e-6 { continue }
        let m = Double((y2 - y1) / (x2 - x1))
        let c = Double(y1) - m * Double(x1)
        let inliers = Set(points.indices.filter { abs(m * Double(points[$0].x) + c - Double(points[$0].y)) <= threshold })
        if inliers.count > best.inliers.count { best = RansacFit(slope: m, intercept: c, inliers: inliers) }
    }
    // Refit on the consensus set — the sampled pair only nominates the inliers.
    if best.inliers.count >= 2 {
        let (m, c) = ordinaryLeastSquares(best.inliers.sorted().map { points[$0] })
        return RansacFit(slope: m, intercept: c, inliers: best.inliers)
    }
    return best
}

func ordinaryLeastSquares(_ points: [LabPoint]) -> (Double, Double) {
    let n = Double(points.count)
    if points.isEmpty { return (0, 0) }
    var sx = 0.0, sy = 0.0, sxy = 0.0, sxx = 0.0
    for p in points { sx += Double(p.x); sy += Double(p.y); sxy += Double(p.x * p.y); sxx += Double(p.x) * Double(p.x) }
    let denom = n * sxx - sx * sx
    if abs(denom) < 1e-12 { return (0, sy / n) }
    let m = (n * sxy - sx * sy) / denom
    return (m, (sy - m * sx) / n)
}

/// Pinball loss minimized by subgradient descent.
func quantileFit(_ points: [LabPoint], tau: Double, steps: Int = 4000) -> (Double, Double) {
    if points.isEmpty { return (0, 0) }
    var (m, c) = ordinaryLeastSquares(points)
    var lr = 0.05
    let n = Double(points.count)
    for _ in 0..<steps {
        var gm = 0.0, gc = 0.0
        for p in points {
            let residual = Double(p.y) - (m * Double(p.x) + c)
            let g = residual >= 0 ? -tau : 1 - tau
            gm += g * Double(p.x)
            gc += g
        }
        m -= lr * gm / n
        c -= lr * gc / n
        lr *= 0.9995
    }
    return (m, c)
}

func pinballLoss(_ points: [LabPoint], tau: Double, m: Double, c: Double) -> Double {
    if points.isEmpty { return 0 }
    var sum = 0.0
    for p in points {
        let r = Double(p.y) - (m * Double(p.x) + c)
        sum += r >= 0 ? tau * r : (tau - 1) * r
    }
    return sum / Double(points.count)
}

struct BayesianFit { let mean: [Double]; let covariance: [[Double]]; let noiseVariance: Double }

func bayesianRidge(_ x: [[Double]], _ y: [Double], alpha: Double, noiseVariance: Double) -> BayesianFit {
    let p = x.first?.count ?? 0
    if p == 0 { return BayesianFit(mean: [], covariance: [], noiseVariance: noiseVariance) }
    let beta = 1 / max(1e-6, noiseVariance)
    let precision = (0..<p).map { i in (0..<p).map { j -> Double in
        var s = 0.0
        for r in x.indices { s += x[r][i] * x[r][j] }
        return beta * s + (i == j ? alpha : 0)
    } }
    let cov = invert(precision)
    let xty = (0..<p).map { i -> Double in
        var s = 0.0
        for r in x.indices { s += x[r][i] * y[r] }
        return s
    }
    let mean = (0..<p).map { i -> Double in
        var s = 0.0
        for j in 0..<p { s += cov[i][j] * xty[j] }
        return beta * s
    }
    return BayesianFit(mean: mean, covariance: cov, noiseVariance: noiseVariance)
}

/// sqrt(noise + φᵀSφ): the second term grows where data is sparse.
func predictiveStd(_ fit: BayesianFit, _ phi: [Double]) -> Double {
    if fit.covariance.isEmpty { return fit.noiseVariance.squareRoot() }
    var quad = 0.0
    for i in phi.indices { for j in phi.indices { quad += phi[i] * fit.covariance[i][j] * phi[j] } }
    return max(0, fit.noiseVariance + quad).squareRoot()
}

func invert(_ matrix: [[Double]]) -> [[Double]] {
    let n = matrix.count
    var a = (0..<n).map { r in (0..<(2 * n)).map { c in c < n ? matrix[r][c] : (c - n == r ? 1.0 : 0.0) } }
    for col in 0..<n {
        var pivot = col
        for r in (col + 1)..<max(n, col + 1) where abs(a[r][col]) > abs(a[pivot][col]) { pivot = r }
        a.swapAt(col, pivot)
        if abs(a[col][col]) < 1e-12 { a[col][col] = 1e-12 }
        let d = a[col][col]
        for c in 0..<(2 * n) { a[col][c] /= d }
        for r in 0..<n where r != col {
            let f = a[r][col]
            if f == 0 { continue }
            for c in 0..<(2 * n) { a[r][c] -= f * a[col][c] }
        }
    }
    return (0..<n).map { r in (0..<n).map { c in a[r][c + n] } }
}

func poissonIrls(_ points: [LabPoint], iterations: Int = 40) -> (Double, Double) {
    let avg = points.map { Double($0.y) }.reduce(0, +) / Double(max(points.count, 1))
    var b0 = log(max(0.5, avg)), b1 = 0.0
    for _ in 0..<iterations {
        var sw = 0.0, swx = 0.0, swxx = 0.0, swz = 0.0, swxz = 0.0
        for p in points {
            let x = Double(p.x)
            let eta = b0 + b1 * x
            let mu = exp(min(max(eta, -20), 20))
            let w = max(1e-6, mu)
            let z = eta + (Double(p.y) - mu) / w
            sw += w; swx += w * x; swxx += w * x * x; swz += w * z; swxz += w * x * z
        }
        let denom = sw * swxx - swx * swx
        if abs(denom) < 1e-12 { continue }
        b1 = (sw * swxz - swx * swz) / denom
        b0 = (swz - b1 * swx) / sw
    }
    return (b0, b1)
}

func poissonDeviance(_ points: [LabPoint], _ b0: Double, _ b1: Double) -> Double {
    var sum = 0.0
    for p in points {
        let mu = max(1e-9, exp(min(max(b0 + b1 * Double(p.x), -20), 20)))
        let y = Double(p.y)
        sum += 2 * ((y > 0 ? y * log(y / mu) : 0) - (y - mu))
    }
    return sum / Double(max(1, points.count))
}

struct IsotonicFit { let xs: [Float]; let ys: [Double]; let blocks: Int }

/// Pool adjacent violators.
func pava(_ points: [LabPoint]) -> IsotonicFit {
    if points.isEmpty { return IsotonicFit(xs: [], ys: [], blocks: 0) }
    let sorted = points.enumerated().sorted { $0.element.x < $1.element.x || ($0.element.x == $1.element.x && $0.offset < $1.offset) }.map(\.element)
    var values: [Double] = [], weights: [Int] = []
    for p in sorted {
        var value = Double(p.y), weight = 1
        while let last = values.last, last > value {
            let v = values.removeLast(), w = weights.removeLast()
            value = (value * Double(weight) + v * Double(w)) / Double(weight + w)
            weight += w
        }
        values.append(value)
        weights.append(weight)
    }
    var out: [Double] = []
    for (i, v) in values.enumerated() { out += [Double](repeating: v, count: weights[i]) }
    return IsotonicFit(xs: sorted.map(\.x), ys: out, blocks: values.count)
}

struct LarsStep { let beta: [Double]; let active: [Int]; let maxCorrelation: Double; let entered: Int? }

func larsPath(_ x: [[Double]], _ y: [Double], maxSteps: Int) -> [LarsStep] {
    let n = x.count
    let p = x.first?.count ?? 0
    if p <= 1 { return [] }
    let mean = y.reduce(0, +) / Double(n)
    var residual = y.map { $0 - mean }
    var beta = [Double](repeating: 0, count: p)
    beta[0] = mean
    var active: [Int] = []
    var steps: [LarsStep] = []
    func correlation(_ j: Int) -> Double {
        var s = 0.0
        for r in 0..<n { s += x[r][j] * residual[r] }
        return s
    }
    for _ in 0..<min(maxSteps, p - 1) {
        let candidates = (1..<p).filter { !active.contains($0) }
        guard let candidate = candidates.max(by: { abs(correlation($0)) < abs(correlation($1)) }) else { continue }
        active.append(candidate)
        let sub = (0..<n).map { r in active.map { x[r][$0] } }
        let direction = ridgeSolve(sub, residual, 0, penalizeIntercept: true)
        let gamma = 0.5
        for (k, j) in active.enumerated() { beta[j] += gamma * direction[k] }
        for r in 0..<n {
            var delta = 0.0
            for (k, d) in direction.enumerated() { delta += gamma * d * x[r][active[k]] }
            residual[r] -= delta
        }
        steps.append(LarsStep(beta: beta, active: active, maxCorrelation: (1..<p).map { abs(correlation($0)) }.max() ?? 0, entered: candidate))
    }
    return steps
}

struct StepwiseResult { let beta: [Double]; let selected: [Int]; let adjustedR2: Double }

func forwardStepwise(_ x: [[Double]], _ y: [Double], terms: Int) -> StepwiseResult {
    let n = x.count
    let p = x.first?.count ?? 0
    var selected = [0]
    let mean = y.reduce(0, +) / Double(n)
    let totalSs = y.reduce(0) { $0 + ($1 - mean) * ($1 - mean) }
    for _ in 0..<min(terms, p - 1) {
        var bestJ = -1, bestRss = Double.greatestFiniteMagnitude
        for j in 1..<p where !selected.contains(j) {
            let rss = rssFor(x, y, selected + [j])
            if rss < bestRss { bestRss = rss; bestJ = j }
        }
        if bestJ >= 0 { selected.append(bestJ) }
    }
    var beta = [Double](repeating: 0, count: p)
    let sub = (0..<n).map { r in selected.map { x[r][$0] } }
    let fitted = ridgeSolve(sub, y, 0, penalizeIntercept: true)
    for (k, j) in selected.enumerated() { beta[j] = fitted[k] }
    let rss = rssFor(x, y, selected)
    let k = selected.count - 1
    let adjusted = n - k - 1 > 0 && totalSs > 0 ? 1 - (rss / Double(n - k - 1)) / (totalSs / Double(n - 1)) : 0
    return StepwiseResult(beta: beta, selected: Array(selected.dropFirst()).sorted(), adjustedR2: adjusted)
}

private func rssFor(_ x: [[Double]], _ y: [Double], _ columns: [Int]) -> Double {
    let sub = x.map { row in columns.map { row[$0] } }
    let beta = ridgeSolve(sub, y, 0, penalizeIntercept: true)
    var rss = 0.0
    for r in 0..<x.count {
        var pred = 0.0
        for k in columns.indices { pred += beta[k] * sub[r][k] }
        rss += (y[r] - pred) * (y[r] - pred)
    }
    return rss
}

func clampF(_ v: Float, _ lo: Float, _ hi: Float) -> Float { min(hi, max(lo, v)) }
