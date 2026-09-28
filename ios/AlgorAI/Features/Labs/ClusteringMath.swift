import Foundation

// Port of ClusteringMath.kt: the clustering algorithms behind the B5 labs. Every clustering shown is
// produced by the real algorithm here. Float arithmetic where Android uses Float.

struct Pt: Hashable {
    let x: Float
    let y: Float
    init(_ x: Float, _ y: Float) { self.x = x; self.y = y }
}

func dist(_ a: Pt, _ b: Pt) -> Float {
    let dx = a.x - b.x, dy = a.y - b.y
    return (dx * dx + dy * dy).squareRoot()
}

func manhattan(_ a: Pt, _ b: Pt) -> Float { abs(a.x - b.x) + abs(a.y - b.y) }

private func meanF(_ xs: [Float]) -> Float { Float(xs.average) }

private func argminFirst<T: Comparable>(_ xs: [T]) -> Int {
    var best = 0
    for i in xs.indices where xs[i] < xs[best] { best = i }
    return best
}

// MARK: - k-means vs k-medians

struct CentroidStep { let assignment: [Int]; let centres: [Pt] }

func lloyd(_ points: [Pt], initial: [Pt], iterations: Int, useMedian: Bool) -> [CentroidStep] {
    var centres = initial
    var steps: [CentroidStep] = []
    func assign() -> [Int] {
        points.map { p in centres.isEmpty ? 0 : argminFirst(centres.map { useMedian ? manhattan(p, $0) : dist(p, $0) }) }
    }
    for _ in 0..<iterations {
        let assignment = assign()
        steps.append(CentroidStep(assignment: assignment, centres: centres))
        centres = centres.indices.map { k in
            let members = points.indices.filter { assignment[$0] == k }.map { points[$0] }
            if members.isEmpty { return centres[k] }
            if useMedian { return Pt(median(members.map(\.x)), median(members.map(\.y))) }
            return Pt(meanF(members.map(\.x)), meanF(members.map(\.y)))
        }
    }
    steps.append(CentroidStep(assignment: assign(), centres: centres))
    return steps
}

func median(_ values: [Float]) -> Float {
    if values.isEmpty { return 0 }
    let sorted = values.sorted()
    let mid = sorted.count / 2
    return sorted.count % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
}

// MARK: - Mean shift

func meanShiftStep(_ seeds: [Pt], _ points: [Pt], bandwidth: Float) -> [Pt] {
    seeds.map { seed in
        var sumX = 0.0, sumY = 0.0, weight = 0.0
        for p in points {
            let d = Double(dist(seed, p))
            let w = exp(-(d * d) / (2.0 * Double(bandwidth) * Double(bandwidth)))
            sumX += w * Double(p.x)
            sumY += w * Double(p.y)
            weight += w
        }
        return weight < 1e-9 ? seed : Pt(Float(sumX / weight), Float(sumY / weight))
    }
}

func mergeModes(_ modes: [Pt], tolerance: Float) -> [Int] {
    var centres: [Pt] = []
    return modes.map { m in
        if let existing = centres.firstIndex(where: { dist($0, m) < tolerance }) { return existing }
        centres.append(m)
        return centres.count - 1
    }
}

// MARK: - DBSCAN machinery, shared by OPTICS and HDBSCAN

func coreDistance(_ points: [Pt], _ index: Int, minPts: Int) -> Float {
    let distances = points.indices.filter { $0 != index }.map { dist(points[index], points[$0]) }.sorted()
    return distances.count >= minPts - 1 ? distances[minPts - 2] : .greatestFiniteMagnitude
}

struct OpticsResult { let order: [Int]; let reachability: [Float] }

/// OPTICS: an ordering plus a reachability value per point; valleys are clusters.
func optics(_ points: [Pt], minPts: Int, eps: Float) -> OpticsResult {
    let n = points.count
    var processed = [Bool](repeating: false, count: n)
    var reachability = [Float](repeating: .greatestFiniteMagnitude, count: n)
    var order: [Int] = []
    var outReach: [Float] = []
    func neighbours(_ i: Int) -> [Int] { (0..<n).filter { $0 != i && dist(points[i], points[$0]) <= eps } }

    for start in 0..<n where !processed[start] {
        // A sorted set ordered by (reachability, index).
        var seeds: [Int] = []
        func less(_ a: Int, _ b: Int) -> Bool { reachability[a] == reachability[b] ? a < b : reachability[a] < reachability[b] }
        var current = start
        while true {
            processed[current] = true
            order.append(current)
            outReach.append(reachability[current] == .greatestFiniteMagnitude ? 0 : reachability[current])
            let core = coreDistance(points, current, minPts: minPts)
            if core != .greatestFiniteMagnitude {
                for nb in neighbours(current) where !processed[nb] {
                    let newReach = max(core, dist(points[current], points[nb]))
                    if newReach < reachability[nb] {
                        seeds.removeAll { $0 == nb }
                        reachability[nb] = newReach
                        seeds.append(nb)
                    }
                }
            }
            if seeds.isEmpty { break }
            seeds.sort(by: less)
            current = seeds.removeFirst()
        }
    }
    return OpticsResult(order: order, reachability: outReach)
}

// MARK: - Hierarchical clustering, both directions

struct MergeStep { let left: [Int]; let right: [Int]; let distance: Float }

/// Agglomerative, complete linkage.
func agglomerative(_ points: [Pt]) -> [MergeStep] {
    var clusters = points.indices.map { [$0] }
    var steps: [MergeStep] = []
    while clusters.count > 1 {
        var bestA = 0, bestB = 1
        var bestD = Float.greatestFiniteMagnitude
        for a in clusters.indices {
            for b in (a + 1)..<clusters.count {
                let d = clusters[a].map { i in clusters[b].map { dist(points[i], points[$0]) }.max()! }.max()!
                if d < bestD { bestD = d; bestA = a; bestB = b }
            }
        }
        steps.append(MergeStep(left: clusters[bestA], right: clusters[bestB], distance: bestD))
        let merged = clusters[bestA] + clusters[bestB]
        clusters = clusters.indices.filter { $0 != bestA && $0 != bestB }.map { clusters[$0] } + [merged]
    }
    return steps
}

struct SplitStep { let parent: [Int]; let left: [Int]; let right: [Int]; let diameter: Float }

/// Divisive: split the least cohesive cluster by 2-means seeded with its two furthest members.
func divisive(_ points: [Pt], splits: Int) -> [SplitStep] {
    var clusters = [Array(points.indices)]
    var steps: [SplitStep] = []
    func diameter(_ cluster: [Int]) -> Float {
        if cluster.count < 2 { return 0 }
        var d: Float = 0
        for a in cluster.indices { for b in (a + 1)..<cluster.count { d = max(d, dist(points[cluster[a]], points[cluster[b]])) } }
        return d
    }
    for _ in 0..<splits {
        let candidates = clusters.filter { $0.count > 1 }
        guard !candidates.isEmpty else { continue }
        let target = candidates[argmaxFirst(candidates.map(diameter))]
        var seedA = target[0], seedB = target[1]
        var best: Float = 0
        for a in target.indices {
            for b in (a + 1)..<target.count {
                let d = dist(points[target[a]], points[target[b]])
                if d > best { best = d; seedA = target[a]; seedB = target[b] }
            }
        }
        var centreA = points[seedA], centreB = points[seedB]
        var left: [Int] = [], right: [Int] = []
        for _ in 0..<12 {
            left = target.filter { dist(points[$0], centreA) <= dist(points[$0], centreB) }
            let leftSet = Set(left)
            right = target.filter { !leftSet.contains($0) }
            if !left.isEmpty { centreA = Pt(meanF(left.map { points[$0].x }), meanF(left.map { points[$0].y })) }
            if !right.isEmpty { centreB = Pt(meanF(right.map { points[$0].x }), meanF(right.map { points[$0].y })) }
        }
        if left.isEmpty || right.isEmpty { continue }
        steps.append(SplitStep(parent: target, left: left, right: right, diameter: diameter(target)))
        clusters = clusters.filter { $0 != target } + [left, right]
    }
    return steps
}

// MARK: - Gaussian mixture, fitted by EM

struct GmmComponent {
    let meanX: Double, meanY: Double, varX: Double, varY: Double, covXY: Double, weight: Double
    private var det: Double { max(1e-9, varX * varY - covXY * covXY) }

    init(_ meanX: Double, _ meanY: Double, _ varX: Double, _ varY: Double, _ covXY: Double, _ weight: Double) {
        self.meanX = meanX; self.meanY = meanY; self.varX = varX; self.varY = varY; self.covXY = covXY; self.weight = weight
    }

    func density(_ x: Float, _ y: Float) -> Double {
        let dx = Double(x) - meanX, dy = Double(y) - meanY
        let quad = (varY * dx * dx - 2 * covXY * dx * dy + varX * dy * dy) / det
        return weight * exp(-0.5 * quad) / (2 * .pi * det.squareRoot())
    }

    /// Closed-form 2×2 eigendecomposition as a closed polyline at a Mahalanobis radius.
    func ellipse(radius: Double, segments: Int = 48) -> [Pt] {
        let trace = varX + varY
        let disc = max(0, trace * trace / 4 - det).squareRoot()
        let l1 = trace / 2 + disc, l2 = trace / 2 - disc
        let angle = abs(covXY) < 1e-12 ? 0 : atan2(l1 - varX, covXY)
        let rx = radius * max(1e-9, l1).squareRoot(), ry = radius * max(1e-9, l2).squareRoot()
        return (0...segments).map { i in
            let t = 2 * Double.pi * Double(i) / Double(segments)
            let px = rx * cos(t), py = ry * sin(t)
            return Pt(Float(meanX + px * cos(angle) - py * sin(angle)), Float(meanY + px * sin(angle) + py * cos(angle)))
        }
    }
}

struct GmmStep { let components: [GmmComponent]; let responsibilities: [[Double]] }

func fitGmm(_ points: [Pt], initial: [Pt], iterations: Int) -> [GmmStep] {
    var components = initial.map { GmmComponent(Double($0.x), Double($0.y), 0.02, 0.02, 0, 1.0 / Double(initial.count)) }
    var steps: [GmmStep] = []
    func responsibilities() -> [[Double]] {
        points.map { p in
            let densities = components.map { $0.density(p.x, p.y) }
            let sum = densities.reduce(0, +)
            let total = sum > 1e-300 ? sum : 1
            return densities.map { $0 / total }
        }
    }
    for _ in 0..<iterations {
        let resp = responsibilities()
        steps.append(GmmStep(components: components, responsibilities: resp))
        components = components.indices.map { k in
            var nk = 0.0, mx = 0.0, my = 0.0
            for i in points.indices {
                nk += resp[i][k]
                mx += resp[i][k] * Double(points[i].x)
                my += resp[i][k] * Double(points[i].y)
            }
            nk = max(1e-9, nk)
            mx /= nk
            my /= nk
            var vx = 0.0, vy = 0.0, cxy = 0.0
            for i in points.indices {
                let dx = Double(points[i].x) - mx, dy = Double(points[i].y) - my
                vx += resp[i][k] * dx * dx
                vy += resp[i][k] * dy * dy
                cxy += resp[i][k] * dx * dy
            }
            return GmmComponent(mx, my, max(1e-4, vx / nk), max(1e-4, vy / nk), cxy / nk, nk / Double(points.count))
        }
    }
    steps.append(GmmStep(components: components, responsibilities: responsibilities()))
    return steps
}

func gmmLogLikelihood(_ points: [Pt], _ components: [GmmComponent]) -> Double {
    points.reduce(0.0) { acc, p in acc + log(max(1e-300, components.reduce(0.0) { $0 + $1.density(p.x, p.y) })) } / Double(points.count)
}

// MARK: - Affinity propagation

struct ApResult { let exemplars: [Int]; let assignment: [Int] }

func affinityPropagation(_ points: [Pt], preference: Double, iterations: Int, damping: Double = 0.7) -> ApResult {
    let n = points.count
    let s = (0..<n).map { i in (0..<n).map { j -> Double in
        if i == j { return preference }
        let v = Double(dist(points[i], points[j])) * 10
        return -(v * v)
    } }
    var r = [[Double]](repeating: [Double](repeating: 0, count: n), count: n)
    var a = r
    for _ in 0..<iterations {
        for i in 0..<n {
            let combined = (0..<n).map { a[i][$0] + s[i][$0] }
            for k in 0..<n {
                let maxOther = (0..<n).filter { $0 != k }.map { combined[$0] }.max() ?? -.infinity
                r[i][k] = damping * r[i][k] + (1 - damping) * (s[i][k] - maxOther)
            }
        }
        for k in 0..<n {
            var positiveSum = 0.0
            for i in 0..<n where i != k { positiveSum += max(0, r[i][k]) }
            for i in 0..<n {
                let value = i == k ? positiveSum : min(0, r[k][k] + positiveSum - max(0, r[i][k]))
                a[i][k] = damping * a[i][k] + (1 - damping) * value
            }
        }
    }
    let exemplars = (0..<n).filter { r[$0][$0] + a[$0][$0] > 0 }
    let chosen = exemplars.isEmpty ? [argmaxFirst((0..<n).map { r[$0][$0] + a[$0][$0] })] : exemplars
    let assignment = (0..<n).map { i in argminFirst(chosen.map { dist(points[i], points[$0]) }) }
    return ApResult(exemplars: chosen, assignment: assignment)
}

// MARK: - Spectral clustering

func rbfAffinity(_ points: [Pt], sigma: Float) -> [[Double]] {
    let n = points.count
    return (0..<n).map { i in (0..<n).map { j -> Double in
        if i == j { return 0 }
        let d = Double(dist(points[i], points[j]))
        return exp(-(d * d) / (2.0 * Double(sigma) * Double(sigma)))
    } }
}

/// The Fiedler vector by power iteration on (cI − L), deflating the constant vector.
func fiedlerVector(_ affinity: [[Double]], iterations: Int = 6000) -> [Double] {
    let n = affinity.count
    let degree = affinity.map { $0.reduce(0, +) }
    let shift = degree.max()! * 2 + 1
    var v = (0..<n).map { $0 % 2 == 0 ? 1.0 : -1.0 }
    func deflate(_ vec: inout [Double]) {
        let mean = vec.average
        for i in 0..<n { vec[i] -= mean }
        let nrm = vec.reduce(0) { $0 + $1 * $1 }.squareRoot()
        let norm = nrm > 1e-12 ? nrm : 1
        for i in 0..<n { vec[i] /= norm }
    }
    deflate(&v)
    for _ in 0..<iterations {
        var next = [Double](repeating: 0, count: n)
        for i in 0..<n {
            var sum = (shift - degree[i]) * v[i]
            for j in 0..<n { sum += affinity[i][j] * v[j] }
            next[i] = sum
        }
        v = next
        deflate(&v)
    }
    return v
}

/// scikit-learn's make_moons geometry, mapped into the unit square.
func twoMoons(seed: Int, perMoon: Int = 30) -> [Pt] {
    var rng = Lcg(seed)
    func place(_ rawX: Double, _ rawY: Double) -> Pt {
        let x = Float((rawX + 1) / 3) + (rng.nextF() - 0.5) * 0.07
        let y = Float((rawY + 0.5) / 1.5) + (rng.nextF() - 0.5) * 0.07
        return Pt(x, y)
    }
    var out: [Pt] = []
    for i in 0..<perMoon {
        let t = Double.pi * Double(i) / Double(perMoon - 1)
        out.append(place(cos(t), sin(t)))
    }
    for i in 0..<perMoon {
        let t = Double.pi * Double(i) / Double(perMoon - 1)
        out.append(place(1 - cos(t), 1 - sin(t) - 0.5))
    }
    return out
}

// MARK: - Shared datasets

struct ClusterRng {
    private var lcg: Lcg
    init(_ seed: Int) { lcg = Lcg(seed) }
    mutating func next() -> Float { lcg.nextF() }
    mutating func jitter(_ scale: Float) -> Float { (next() - 0.5) * scale }
}

func clusterBlob(_ rng: inout ClusterRng, _ cx: Float, _ cy: Float, _ count: Int, _ spread: Float, spreadY: Float? = nil) -> [Pt] {
    (0..<count).map { _ in
        let x = min(max(cx + rng.jitter(spread), 0.04), 0.96)
        let y = min(max(cy + rng.jitter(spreadY ?? spread), 0.04), 0.96)
        return Pt(x, y)
    }
}

/// Two blobs plus a far outlier — the mean is dragged toward it and the median is not.
let outlierBlobs: [Pt] = {
    var rng = ClusterRng(13)
    return clusterBlob(&rng, 0.28, 0.30, 12, 0.16) + clusterBlob(&rng, 0.62, 0.62, 12, 0.16) + [Pt(0.95, 0.06), Pt(0.92, 0.10)]
}()

/// Deliberately unequal densities: DBSCAN with a single eps cannot serve both.
let varyingDensity: [Pt] = {
    var rng = ClusterRng(29)
    return clusterBlob(&rng, 0.24, 0.72, 20, 0.10) + clusterBlob(&rng, 0.70, 0.34, 14, 0.26) + [Pt(0.50, 0.94), Pt(0.06, 0.10)]
}()

/// Elongated and spherical side by side.
let elongatedBlobs: [Pt] = {
    var rng = ClusterRng(37)
    return clusterBlob(&rng, 0.32, 0.50, 22, 0.10, spreadY: 0.44) + clusterBlob(&rng, 0.74, 0.52, 18, 0.18)
}()

let compactBlobs: [Pt] = {
    var rng = ClusterRng(41)
    return clusterBlob(&rng, 0.24, 0.28, 10, 0.14) + clusterBlob(&rng, 0.72, 0.30, 10, 0.14) + clusterBlob(&rng, 0.48, 0.76, 10, 0.14)
}()
