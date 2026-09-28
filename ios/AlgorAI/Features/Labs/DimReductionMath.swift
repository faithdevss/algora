import Foundation

// Port of DimReductionMath.kt: the estimators behind the eight Dimensionality Reduction labs (B6).
// Kernel PCA eigendecomposes a real centred Gram matrix, t-SNE runs the perplexity search and KL
// gradient descent, FastICA the tanh fixed point, LLE the constrained weights and bottom
// eigenvectors. Seeds and draw order match Android so the quoted numbers agree.

// MARK: - Linear algebra

struct EigenResult {
    /// Eigenvalues, descending.
    let values: [Double]
    /// `vectors[k]` is the unit eigenvector for `values[k]`.
    let vectors: [[Double]]
}

/// Cyclic Jacobi eigendecomposition of a real symmetric matrix, eigenpairs by descending value.
func jacobiEigen(_ input: [[Double]], maxSweeps: Int = 60) -> EigenResult {
    let n = input.count
    var a = input
    var v = (0..<n).map { i in (0..<n).map { j in i == j ? 1.0 : 0.0 } }

    var sweep = 0
    while sweep < maxSweeps {
        var off = 0.0
        for p in 0..<n { for q in (p + 1)..<max(n, p + 1) { off += a[p][q] * a[p][q] } }
        if off < 1e-22 { break }

        for p in 0..<n {
            for q in (p + 1)..<max(n, p + 1) {
                let apq = a[p][q]
                if abs(apq) < 1e-18 { continue }
                let theta = (a[q][q] - a[p][p]) / (2.0 * apq)
                let t = theta >= 0 ? 1.0 / (theta + (theta * theta + 1.0).squareRoot())
                    : -1.0 / (-theta + (theta * theta + 1.0).squareRoot())
                let c = 1.0 / (t * t + 1.0).squareRoot()
                let s = t * c

                for k in 0..<n {
                    let akp = a[k][p], akq = a[k][q]
                    a[k][p] = c * akp - s * akq
                    a[k][q] = s * akp + c * akq
                }
                for k in 0..<n {
                    let apk = a[p][k], aqk = a[q][k]
                    a[p][k] = c * apk - s * aqk
                    a[q][k] = s * apk + c * aqk
                }
                for k in 0..<n {
                    let vkp = v[k][p], vkq = v[k][q]
                    v[k][p] = c * vkp - s * vkq
                    v[k][q] = s * vkp + c * vkq
                }
            }
        }
        sweep += 1
    }

    let order = (0..<n).sortedByDescending { a[$0][$0] }
    return EigenResult(values: order.map { a[$0][$0] }, vectors: order.map { col in (0..<n).map { v[$0][col] } })
}

/// Covariance of a 2-column dataset, `[[cxx, cxy], [cxy, cyy]]`, about the sample mean.
func covariance2(_ points: [Pt]) -> [[Double]] {
    let mx = points.map { Double($0.x) }.average
    let my = points.map { Double($0.y) }.average
    var cxx = 0.0, cyy = 0.0, cxy = 0.0
    for p in points {
        let dx = Double(p.x) - mx, dy = Double(p.y) - my
        cxx += dx * dx
        cyy += dy * dy
        cxy += dx * dy
    }
    let n = Double(points.count)
    return [[cxx / n, cxy / n], [cxy / n, cyy / n]]
}

func meanOf(_ points: [Pt]) -> Pt { Pt(Float(points.map(\.x).average), Float(points.map(\.y).average)) }

/// Angle of a direction in degrees, folded to [0°, 180°).
func axisAngleDegrees(_ dx: Double, _ dy: Double) -> Double {
    var deg = atan2(dy, dx) * 180.0 / .pi
    while deg < 0 { deg += 180.0 }
    while deg >= 180.0 { deg -= 180.0 }
    return deg
}

/// Smallest angle between two undirected axes, in [0°, 90°].
func axisSeparation(_ a: Double, _ b: Double) -> Double {
    let d = abs(a - b).truncatingRemainder(dividingBy: 180.0)
    return min(d, 180.0 - d)
}

/// Rescale a layout into the drawable unit square, preserving aspect ratio.
func fitToUnit(_ points: [Pt], margin: Float = 0.08) -> [Pt] {
    if points.isEmpty { return points }
    let minX = points.map(\.x).min()!, maxX = points.map(\.x).max()!
    let minY = points.map(\.y).min()!, maxY = points.map(\.y).max()!
    let span = max(max(maxX - minX, maxY - minY), 1e-6)
    let scale = (1 - 2 * margin) / span
    let offX = margin + ((1 - 2 * margin) - (maxX - minX) * scale) / 2
    let offY = margin + ((1 - 2 * margin) - (maxY - minY) * scale) / 2
    return points.map { Pt(offX + ($0.x - minX) * scale, offY + ($0.y - minY) * scale) }
}

// MARK: - Datasets

private struct DimRng {
    var lcg: Lcg
    init(_ seed: Int) { lcg = Lcg(seed) }
    mutating func next() -> Double { lcg.next() }
    mutating func uniform(_ lo: Double, _ hi: Double) -> Double { lo + next() * (hi - lo) }
    /// Box-Muller.
    mutating func gaussian() -> Double {
        let u1 = max(next(), 1e-9)
        let u2 = next()
        return (-2.0 * log(u1)).squareRoot() * cos(2.0 * .pi * u2)
    }
}

private func clampD(_ v: Double, _ lo: Double, _ hi: Double) -> Double { min(max(v, lo), hi) }

/// The same correlated cloud PCA's own lab uses (seed 31).
let correlatedCloudPts: [Pt] = {
    var rng = DimRng(31)
    return (0..<18).map { i in
        let t = 0.10 + 0.80 * (Double(i) / 17.0)
        let x = clampD(t + (rng.next() - 0.5) * 0.10, 0.04, 0.96)
        let y = clampD(0.15 + 0.72 * t + (rng.next() - 0.5) * 0.14, 0.04, 0.96)
        return Pt(Float(x), Float(y))
    }
}()

/// Two concentric rings (make_circles geometry), inner ring first.
let concentricRings: [Pt] = {
    var rng = DimRng(101)
    var out: [Pt] = []
    for _ in 0..<22 {
        let a = rng.uniform(0.0, 2.0 * .pi)
        let r = 0.35 + rng.gaussian() * 0.05
        out.append(Pt(Float(r * cos(a)), Float(r * sin(a))))
    }
    for _ in 0..<26 {
        let a = rng.uniform(0.0, 2.0 * .pi)
        let r = 1.0 + rng.gaussian() * 0.05
        out.append(Pt(Float(r * cos(a)), Float(r * sin(a))))
    }
    return out
}()

let innerRingCount = 22

/// Two tight clusters close together and one loose one far away.
let unevenClusters: [Pt] = {
    var rng = DimRng(211)
    var pts: [Pt] = []
    for _ in 0..<15 {
        let x = 0.22 + rng.gaussian() * 0.030
        let y = 0.30 + rng.gaussian() * 0.030
        pts.append(Pt(Float(x), Float(y)))
    }
    for _ in 0..<15 {
        let x = 0.34 + rng.gaussian() * 0.030
        let y = 0.28 + rng.gaussian() * 0.030
        pts.append(Pt(Float(x), Float(y)))
    }
    for _ in 0..<15 {
        let x = 0.78 + rng.gaussian() * 0.105
        let y = 0.76 + rng.gaussian() * 0.105
        pts.append(Pt(Float(x), Float(y)))
    }
    return fitToUnit(pts)
}()

func unevenClusterLabel(_ index: Int) -> Int { index / 15 }

let mixingMatrix: [[Double]] = [[1.0, 0.62], [0.35, 1.0]]

let mixedSources: [Pt] = {
    var rng = DimRng(307)
    return (0..<120).map { _ in
        let s1 = rng.uniform(-1.0, 1.0)
        let s2 = rng.uniform(-1.0, 1.0)
        return Pt(Float(mixingMatrix[0][0] * s1 + mixingMatrix[0][1] * s2), Float(mixingMatrix[1][0] * s1 + mixingMatrix[1][1] * s2))
    }
}()

struct FactorSample { let x1: Double, x2: Double, x3: Double }

let trueLoadings: [Double] = [0.90, 0.55, 0.80]
let trueUniquenesses: [Double] = [0.10, 0.95, 0.16]

let factorSamples: [FactorSample] = {
    var rng = DimRng(419)
    return (0..<240).map { _ in
        let f = rng.gaussian()
        let a = trueLoadings[0] * f + rng.gaussian() * trueUniquenesses[0].squareRoot()
        let b = trueLoadings[1] * f + rng.gaussian() * trueUniquenesses[1].squareRoot()
        let c = trueLoadings[2] * f + rng.gaussian() * trueUniquenesses[2].squareRoot()
        return FactorSample(x1: a, x2: b, x3: c)
    }
}()

/// A spiral sampled in order of arc length.
let spiralCurve: [Pt] = {
    var rng = DimRng(523)
    return (0..<40).map { i in
        let t = Double(i) / 39.0
        let a = 0.6 + 2.0 * .pi * t
        let r = 0.10 + 0.40 * t
        let x = 0.5 + r * cos(a) + rng.gaussian() * 0.006
        let y = 0.5 + r * sin(a) + rng.gaussian() * 0.006
        return Pt(Float(x), Float(y))
    }
}()

// MARK: - Kernel PCA

struct KernelPcaResult {
    let gram: [[Double]]
    let centered: [[Double]]
    let eigenvalues: [Double]
    /// `components[k][i]` is point i's coordinate on kernel principal component k.
    let components: [[Double]]
}

func kernelPca(_ points: [Pt], gamma: Double, components: Int = 2) -> KernelPcaResult {
    let n = points.count
    let k: [[Double]] = (0..<n).map { i in
        (0..<n).map { j in
            let dx = Double(points[i].x - points[j].x)
            let dy = Double(points[i].y - points[j].y)
            return exp(-gamma * (dx * dx + dy * dy))
        }
    }
    let rowMean = k.map { $0.average }
    let grand = rowMean.average
    let centered = (0..<n).map { i in (0..<n).map { j in k[i][j] - rowMean[i] - rowMean[j] + grand } }
    let eigen = jacobiEigen(centered)
    let comps = (0..<components).map { c -> [Double] in
        let lambda = max(eigen.values[c], 1e-12)
        return (0..<n).map { eigen.vectors[c][$0] * lambda.squareRoot() }
    }
    return KernelPcaResult(gram: k, centered: centered, eigenvalues: eigen.values, components: comps)
}

/// Best accuracy achievable by thresholding a single coordinate.
func bestThresholdAccuracy(_ scores: [Double], positiveCount: Int) -> Double {
    let n = scores.count
    let sorted = scores.sorted()
    var best = 0.0
    var cuts = sorted.indices.map { i in i == 0 ? sorted[0] - 1.0 : (sorted[i - 1] + sorted[i]) / 2.0 }
    cuts.append(sorted.last! + 1.0)
    for cut in cuts {
        var correct = 0
        for i in 0..<n {
            let isInner = i < positiveCount
            if (scores[i] < cut) == isInner { correct += 1 }
        }
        best = max(best, Double(max(correct, n - correct)) / Double(n))
    }
    return best
}

// MARK: - Incremental PCA

struct IncrementalPcaStep {
    let seen: Int
    let mean: Pt
    let axis: Pt
    let explained: Double
    let degreesFromBatch: Double
}

func incrementalPca(_ points: [Pt], chunk: Int) -> [IncrementalPcaStep] {
    let batchAxis = jacobiEigen(covariance2(points)).vectors[0]
    let batchAngle = axisAngleDegrees(batchAxis[0], batchAxis[1])

    var count = 0
    var sx = 0.0, sy = 0.0, sxx = 0.0, syy = 0.0, sxy = 0.0
    var steps: [IncrementalPcaStep] = []
    for start in stride(from: 0, to: points.count, by: chunk) {
        for p in points[start..<min(start + chunk, points.count)] {
            count += 1
            let x = Double(p.x), y = Double(p.y)
            sx += x
            sy += y
            sxx += x * x
            syy += y * y
            sxy += x * y
        }
        let c = Double(count)
        let mx = sx / c, my = sy / c
        let cov = [[sxx / c - mx * mx, sxy / c - mx * my], [sxy / c - mx * my, syy / c - my * my]]
        let eigen = jacobiEigen(cov)
        let axis = eigen.vectors[0]
        let total = eigen.values[0] + eigen.values[1]
        steps.append(IncrementalPcaStep(
            seen: count,
            mean: Pt(Float(mx), Float(my)),
            axis: Pt(Float(axis[0]), Float(axis[1])),
            explained: total > 1e-12 ? eigen.values[0] / total : 1.0,
            degreesFromBatch: axisSeparation(axisAngleDegrees(axis[0], axis[1]), batchAngle)
        ))
    }
    return steps
}

// MARK: - SVD of an n×2 matrix

struct Svd2Result {
    let singularValues: [Double]
    let rightVectors: [[Double]]
    let rank1: [Pt]
    let rank1Error: Double
}

func svd2(_ rows: [Pt]) -> Svd2Result {
    var xtx: [[Double]] = [[0, 0], [0, 0]]
    for p in rows {
        let x = Double(p.x), y = Double(p.y)
        xtx[0][0] += x * x
        xtx[0][1] += x * y
        xtx[1][0] += x * y
        xtx[1][1] += y * y
    }
    let eigen = jacobiEigen(xtx)
    let sigma = (0..<2).map { max(eigen.values[$0], 0.0).squareRoot() }
    let v1 = eigen.vectors[0]
    let rank1 = rows.map { p -> Pt in
        let t = Double(p.x) * v1[0] + Double(p.y) * v1[1]
        return Pt(Float(t * v1[0]), Float(t * v1[1]))
    }
    var err = 0.0
    for i in rows.indices {
        let dx = Double(rows[i].x - rank1[i].x), dy = Double(rows[i].y - rank1[i].y)
        err += dx * dx + dy * dy
    }
    return Svd2Result(singularValues: sigma, rightVectors: eigen.vectors, rank1: rank1, rank1Error: err.squareRoot())
}

// MARK: - FastICA

struct IcaResult {
    let whitened: [Pt]
    let sources: [Pt]
    let mixingDirections: [[Double]]
    let iterations: Int
}

/// FastICA with the tanh nonlinearity; the second component is the orthogonal complement.
func fastIca(_ points: [Pt], maxIterations: Int = 200) -> IcaResult {
    let mean = meanOf(points)
    let centered = points.map { Pt($0.x - mean.x, $0.y - mean.y) }
    let eigen = jacobiEigen(covariance2(centered))
    let e = eigen.vectors
    let scale = (0..<2).map { 1.0 / max(eigen.values[$0], 1e-12).squareRoot() }
    let z: [[Double]] = centered.map { p in
        let x = Double(p.x), y = Double(p.y)
        return [scale[0] * (e[0][0] * x + e[0][1] * y), scale[1] * (e[1][0] * x + e[1][1] * y)]
    }

    var w = [0.6, 0.8]
    var used = 0
    if maxIterations >= 1 {
        for iter in 1...maxIterations {
            used = iter
            var g0 = 0.0, g1 = 0.0, gPrime = 0.0
            for zi in z {
                let t = tanh(w[0] * zi[0] + w[1] * zi[1])
                g0 += zi[0] * t
                g1 += zi[1] * t
                gPrime += 1.0 - t * t
            }
            let n = Double(z.count)
            var nx = g0 / n - (gPrime / n) * w[0]
            var ny = g1 / n - (gPrime / n) * w[1]
            let norm = hypot(nx, ny)
            if norm < 1e-12 { break }
            nx /= norm
            ny /= norm
            let converged = abs(abs(nx * w[0] + ny * w[1]) - 1.0) < 1e-10
            w = [nx, ny]
            if converged { break }
        }
    }
    let w2 = [-w[1], w[0]]
    let sources = z.map { zi in Pt(Float(w[0] * zi[0] + w[1] * zi[1]), Float(w2[0] * zi[0] + w2[1] * zi[1])) }

    func unwhiten(_ v: [Double]) -> [Double] {
        let a = v[0] / scale[0], b = v[1] / scale[1]
        let x = e[0][0] * a + e[1][0] * b
        let y = e[0][1] * a + e[1][1] * b
        let n = hypot(x, y)
        return [x / n, y / n]
    }
    return IcaResult(
        whitened: z.map { Pt(Float($0[0]), Float($0[1])) },
        sources: sources,
        mixingDirections: [unwhiten(w), unwhiten(w2)],
        iterations: used
    )
}

/// Excess kurtosis: zero for a Gaussian, negative for uniform sources.
func excessKurtosis(_ values: [Double]) -> Double {
    let m = values.average
    let v = values.reduce(0.0) { $0 + ($1 - m) * ($1 - m) } / Double(values.count)
    if v < 1e-12 { return 0.0 }
    let m4 = values.reduce(0.0) { acc, x in let d = x - m; return acc + d * d * d * d } / Double(values.count)
    return m4 / (v * v) - 3.0
}

// MARK: - Factor analysis

struct FactorAnalysisResult {
    let correlation: [[Double]]
    let loadings: [Double]
    let uniquenesses: [Double]
    let pcaLoadings: [Double]
}

/// One-factor analysis of three standardised variables, closed form (exactly identified).
func oneFactorAnalysis(_ samples: [FactorSample]) -> FactorAnalysisResult {
    let cols = [samples.map(\.x1), samples.map(\.x2), samples.map(\.x3)]
    let means = cols.map(\.average)
    let sds = cols.indices.map { c in (cols[c].reduce(0.0) { acc, x in let d = x - means[c]; return acc + d * d } / Double(cols[c].count)).squareRoot() }
    let r: [[Double]] = (0..<3).map { i in
        (0..<3).map { j in
            var cov = 0.0
            for k in samples.indices { cov += (cols[i][k] - means[i]) * (cols[j][k] - means[j]) }
            cov /= Double(samples.count)
            return cov / (sds[i] * sds[j])
        }
    }
    let l1 = max(r[0][1] * r[0][2] / r[1][2], 1e-9).squareRoot()
    let l2 = max(r[0][1] * r[1][2] / r[0][2], 1e-9).squareRoot()
    let l3 = max(r[0][2] * r[1][2] / r[0][1], 1e-9).squareRoot()
    let loadings = [l1, l2, l3]
    let uniquenesses = loadings.map { max(1.0 - $0 * $0, 0.0) }

    let pca = jacobiEigen(r)
    let pc1 = pca.vectors[0]
    let sqrtLambda = max(pca.values[0], 0.0).squareRoot()
    let sign: Double = pc1.reduce(0, +) < 0 ? -1.0 : 1.0
    let pcaLoadings = (0..<3).map { sign * pc1[$0] * sqrtLambda }
    return FactorAnalysisResult(correlation: r, loadings: loadings, uniquenesses: uniquenesses, pcaLoadings: pcaLoadings)
}

// MARK: - t-SNE

struct TsneStep { let iteration: Int; let embedding: [Pt]; let klDivergence: Double }

private func squaredDistances(_ points: [Pt]) -> [[Double]] {
    let n = points.count
    return (0..<n).map { i in
        (0..<n).map { j in
            let dx = Double(points[i].x - points[j].x), dy = Double(points[i].y - points[j].y)
            return dx * dx + dy * dy
        }
    }
}

/// Per-point bandwidths by binary search on entropy, symmetrised into a joint distribution.
func tsneAffinities(_ points: [Pt], perplexity: Double) -> [[Double]] {
    let n = points.count
    let d2 = squaredDistances(points)
    let target = log(perplexity)
    var p = [[Double]](repeating: [Double](repeating: 0, count: n), count: n)

    for i in 0..<n {
        var lo = 1e-6, hi = 1e6, beta = 1.0
        var row = [Double](repeating: 0, count: n)
        for _ in 0..<60 {
            var sum = 0.0
            for j in 0..<n {
                row[j] = i == j ? 0.0 : exp(-beta * d2[i][j])
                sum += row[j]
            }
            if sum < 1e-12 { sum = 1e-12 }
            var h = 0.0
            for j in 0..<n {
                let v = row[j] / sum
                if v > 1e-12 { h -= v * log(v) }
            }
            if h > target {
                lo = beta
                beta = hi > 9e5 ? beta * 2 : (beta + hi) / 2
            } else {
                hi = beta
                beta = (beta + lo) / 2
            }
            row = (0..<n).map { j in i == j ? 0.0 : row[j] / sum }
        }
        p[i] = row
    }
    return (0..<n).map { i in (0..<n).map { j in (p[i][j] + p[j][i]) / (2.0 * Double(n)) } }
}

func tsne(_ points: [Pt], perplexity: Double = 8.0, iterations: Int = 300, snapshotsAt: [Int] = [0, 40, 120, 300]) -> [TsneStep] {
    let n = points.count
    let p = tsneAffinities(points, perplexity: perplexity)
    var rng = DimRng(661)
    var y: [[Double]] = (0..<n).map { _ in
        let a = rng.gaussian() * 1e-2
        let b = rng.gaussian() * 1e-2
        return [a, b]
    }
    var velocity = [[Double]](repeating: [0, 0], count: n)
    var steps: [TsneStep] = []

    func qMatrix() -> ([[Double]], [[Double]]) {
        var num = [[Double]](repeating: [Double](repeating: 0, count: n), count: n)
        var sum = 0.0
        for i in 0..<n {
            for j in 0..<n where i != j {
                let dx = y[i][0] - y[j][0], dy = y[i][1] - y[j][1]
                num[i][j] = 1.0 / (1.0 + dx * dx + dy * dy)
                sum += num[i][j]
            }
        }
        if sum < 1e-12 { sum = 1e-12 }
        let s = sum
        return (num, num.map { $0.map { $0 / s } })
    }

    func kl(_ q: [[Double]]) -> Double {
        var total = 0.0
        for i in 0..<n {
            for j in 0..<n where i != j {
                if p[i][j] > 1e-12 { total += p[i][j] * log(p[i][j] / max(q[i][j], 1e-12)) }
            }
        }
        return total
    }

    func snapshot(_ iteration: Int) {
        let (_, q) = qMatrix()
        steps.append(TsneStep(iteration: iteration, embedding: fitToUnit(y.map { Pt(Float($0[0]), Float($0[1])) }), klDivergence: kl(q)))
    }

    if snapshotsAt.contains(0) { snapshot(0) }
    if iterations >= 1 {
        for iter in 1...iterations {
            let exaggeration = iter <= 80 ? 4.0 : 1.0
            let momentum = iter <= 80 ? 0.5 : 0.8
            let lr = 220.0
            let (num, q) = qMatrix()
            for i in 0..<n {
                var gx = 0.0, gy = 0.0
                for j in 0..<n where i != j {
                    let mult = (exaggeration * p[i][j] - q[i][j]) * num[i][j]
                    gx += mult * (y[i][0] - y[j][0])
                    gy += mult * (y[i][1] - y[j][1])
                }
                velocity[i][0] = momentum * velocity[i][0] - lr * 4.0 * gx
                velocity[i][1] = momentum * velocity[i][1] - lr * 4.0 * gy
            }
            y = (0..<n).map { [y[$0][0] + velocity[$0][0], y[$0][1] + velocity[$0][1]] }
            let cx = y.reduce(0.0) { $0 + $1[0] } / Double(n)
            let cy = y.reduce(0.0) { $0 + $1[1] } / Double(n)
            for i in 0..<n { y[i][0] -= cx; y[i][1] -= cy }
            if snapshotsAt.contains(iter) { snapshot(iter) }
        }
    }
    return steps
}

// MARK: - UMAP

struct UmapResult {
    let graph: [[Double]]
    /// Layout at each requested epoch; the last is the finished embedding.
    let snapshots: [(Int, [Pt])]
    var embedding: [Pt] { snapshots.last!.1 }
}

/// UMAP's fuzzy simplicial set: ρ to the nearest neighbour, σ solved so weights sum to log₂k,
/// combined with the probabilistic t-conorm.
func umapGraph(_ points: [Pt], k: Int = 8) -> [[Double]] {
    let n = points.count
    let d = (0..<n).map { i in (0..<n).map { j in Double(dist(points[i], points[j])) } }
    let target = log(Double(k)) / log(2.0)
    var directed = [[Double]](repeating: [Double](repeating: 0, count: n), count: n)
    for i in 0..<n {
        let neighbours = Array((0..<n).filter { $0 != i }.sortedBy { d[i][$0] }.prefix(k))
        let rho = d[i][neighbours.first!]
        var lo = 1e-6, hi = 1e6, sigma = 1.0
        for _ in 0..<64 {
            let sum = neighbours.reduce(0.0) { $0 + exp(-max(d[i][$1] - rho, 0.0) / sigma) }
            if sum > target {
                hi = sigma
                sigma = (sigma + lo) / 2
            } else {
                lo = sigma
                sigma = hi > 9e5 ? sigma * 2 : (sigma + hi) / 2
            }
        }
        for j in neighbours { directed[i][j] = exp(-max(d[i][j] - rho, 0.0) / sigma) }
    }
    return (0..<n).map { i in (0..<n).map { j in directed[i][j] + directed[j][i] - directed[i][j] * directed[j][i] } }
}

/// UMAP layout with the exact repulsive sum (n ≤ 60).
func umapLayout(_ points: [Pt], k: Int = 8, epochs: Int = 400, snapshotsAt: [Int] = [1, 40, 400]) -> UmapResult {
    let n = points.count
    let graph = umapGraph(points, k: k)
    let mean = meanOf(points)
    let eigen = jacobiEigen(covariance2(points))
    var y: [[Double]] = (0..<n).map { i in
        let dx = Double(points[i].x - mean.x), dy = Double(points[i].y - mean.y)
        return [(eigen.vectors[0][0] * dx + eigen.vectors[0][1] * dy) * 10, (eigen.vectors[1][0] * dx + eigen.vectors[1][1] * dy) * 10]
    }
    let a = 1.577, b = 0.895, gamma = 1.0
    var snapshots: [(Int, [Pt])] = []
    if epochs >= 1 {
        for epoch in 1...epochs {
            let alpha = 1.0 * (1.0 - (Double(epoch) - 1.0) / Double(epochs))
            var grad = [[Double]](repeating: [0, 0], count: n)
            for i in 0..<n {
                for j in 0..<n where i != j {
                    let dx = y[i][0] - y[j][0], dy = y[i][1] - y[j][1]
                    let d2 = max(dx * dx + dy * dy, 1e-9)
                    let pw = pow(d2, b)
                    let attract = -2.0 * a * b * pow(d2, b - 1.0) / (1.0 + a * pw) * graph[i][j]
                    let repel = gamma * 2.0 * b / ((0.001 + d2) * (1.0 + a * pw)) * (1.0 - graph[i][j])
                    grad[i][0] += (attract + repel) * dx
                    grad[i][1] += (attract + repel) * dy
                }
            }
            for i in 0..<n {
                y[i][0] += alpha * clampD(grad[i][0], -4.0, 4.0)
                y[i][1] += alpha * clampD(grad[i][1], -4.0, 4.0)
            }
            if snapshotsAt.contains(epoch) {
                snapshots.append((epoch, fitToUnit(y.map { Pt(Float($0[0]), Float($0[1])) })))
            }
        }
    }
    return UmapResult(graph: graph, snapshots: snapshots)
}

// MARK: - Locally linear embedding

struct LleResult {
    let neighbours: [[Int]]
    let weights: [[Double]]
    /// One coordinate per point: the recovered intrinsic parameter.
    let embedding: [Double]
}

func lle(_ points: [Pt], k: Int = 4, dimensions: Int = 1) -> LleResult {
    let n = points.count
    let neighbours = (0..<n).map { i in Array((0..<n).filter { $0 != i }.sortedBy { dist(points[i], points[$0]) }.prefix(k)) }

    var w = [[Double]](repeating: [Double](repeating: 0, count: n), count: n)
    for i in 0..<n {
        let nb = neighbours[i]
        let m = nb.count
        var g: [[Double]] = (0..<m).map { r in
            (0..<m).map { c in
                let dr0 = Double(points[nb[r]].x - points[i].x), dr1 = Double(points[nb[r]].y - points[i].y)
                let dc0 = Double(points[nb[c]].x - points[i].x), dc1 = Double(points[nb[c]].y - points[i].y)
                return dr0 * dc0 + dr1 * dc1
            }
        }
        let trace = (0..<m).reduce(0.0) { $0 + g[$1][$1] }
        for r in 0..<m { g[r][r] += 1e-3 * (trace > 0 ? trace : 1.0) }
        let solved = solveSymmetric(g, [Double](repeating: 1.0, count: m))
        var sum = solved.reduce(0, +)
        if abs(sum) < 1e-12 { sum = 1e-12 }
        for (idx, j) in nb.enumerated() { w[i][j] = solved[idx] / sum }
    }

    let mm: [[Double]] = (0..<n).map { i in
        (0..<n).map { j in
            var v = (i == j ? 1.0 : 0.0) - w[i][j] - w[j][i]
            for t in 0..<n { v += w[t][i] * w[t][j] }
            return v
        }
    }
    let eigen = jacobiEigen(mm)
    let bottom = eigen.values.indices.sortedBy { eigen.values[$0] }
    let chosen = bottom[1]
    return LleResult(neighbours: neighbours, weights: w, embedding: eigen.vectors[chosen])
}

/// Gaussian elimination with partial pivoting.
private func solveSymmetric(_ a: [[Double]], _ b: [Double]) -> [Double] {
    let n = b.count
    var m = (0..<n).map { a[$0] + [b[$0]] }
    for col in 0..<n {
        var pivot = col
        for r in (col + 1)..<max(n, col + 1) where abs(m[r][col]) > abs(m[pivot][col]) { pivot = r }
        m.swapAt(col, pivot)
        let p = abs(m[col][col]) < 1e-12 ? 1e-12 : m[col][col]
        for r in 0..<n where r != col {
            let f = m[r][col] / p
            for c in col...n { m[r][c] -= f * m[col][c] }
        }
    }
    return (0..<n).map { m[$0][n] / (abs(m[$0][$0]) < 1e-12 ? 1e-12 : m[$0][$0]) }
}

// MARK: - Scoring

/// Fraction of each point's k input-space neighbours still neighbours in the embedding.
func neighbourPreservation(_ input: [Pt], _ embedded: [Pt], k: Int = 5) -> Double {
    let n = input.count
    var kept = 0
    for i in 0..<n {
        let a = Set((0..<n).filter { $0 != i }.sortedBy { dist(input[i], input[$0]) }.prefix(k))
        let b = Set((0..<n).filter { $0 != i }.sortedBy { dist(embedded[i], embedded[$0]) }.prefix(k))
        kept += a.filter { b.contains($0) }.count
    }
    return Double(kept) / Double(n * k)
}

/// Mean between-cluster distance over mean within-cluster distance.
func separationRatio(_ points: [Pt], _ label: (Int) -> Int) -> Double {
    var within = 0.0, withinN = 0, between = 0.0, betweenN = 0
    for i in points.indices {
        for j in (i + 1)..<max(points.count, i + 1) {
            let d = Double(dist(points[i], points[j]))
            if label(i) == label(j) { within += d; withinN += 1 } else { between += d; betweenN += 1 }
        }
    }
    let w = withinN > 0 ? within / Double(withinN) : 1e-9
    let b = betweenN > 0 ? between / Double(betweenN) : 0.0
    return b / max(w, 1e-9)
}

/// Spearman rank correlation, absolute value.
func absSpearman(_ a: [Double], _ b: [Double]) -> Double {
    func ranks(_ v: [Double]) -> [Double] {
        let order = v.indices.sortedBy { v[$0] }
        var r = [Double](repeating: 0, count: v.count)
        for (rank, idx) in order.enumerated() { r[idx] = Double(rank) }
        return r
    }
    let ra = ranks(a), rb = ranks(b)
    let ma = ra.average, mb = rb.average
    var num = 0.0, da = 0.0, db = 0.0
    for i in ra.indices {
        num += (ra[i] - ma) * (rb[i] - mb)
        da += (ra[i] - ma) * (ra[i] - ma)
        db += (rb[i] - mb) * (rb[i] - mb)
    }
    return abs(num / max(da * db, 1e-12).squareRoot())
}
