import SwiftUI

// Port of DimStoryLabs.kt: PCA, SVD, incremental PCA, kernel PCA, ICA, factor analysis, t-SNE, UMAP and
// LLE. Each is one card (a scatter, strips, a curve, loading bars, tiles or a table), the arithmetic of
// the step, chips and a headline, then a step track, a back-and-action row, a button or a stepper. Every
// number is computed from the seeded data below; the generator is the same plain LCG as on Android.

let dimStoryTopicIds: Set<String> = ["pca", "svd", "incremental_pca", "kernel_pca", "ica", "factor_analysis", "tsne", "umap", "lle"]

private let dBlue = Color(hex: 0x4F7FE0)
private let dOrange = Color(hex: 0xF08A3C)
private let dGreen = Color(hex: 0x3F9A62)
private let dPink = Color(hex: 0xD6457A)
private let dViolet = SimColors.answer
private let notYet = Color(hex: 0x4B5160)
private let linkGrey = Color(hex: 0x6B7280)
private let clusterColors = [dBlue, dOrange, dGreen]

// MARK: - Formatting and small math

private func num(_ v: Double, _ d: Int = 2) -> String {
    var p: Int64 = 1
    for _ in 0..<d { p *= 10 }
    let r = Int64((abs(v) * Double(p) + 0.5).rounded(.down))
    var body = "\(r / p)"
    if d > 0 {
        let frac = String(r % p)
        body += "." + String(repeating: "0", count: d - frac.count) + frac
    }
    return v < 0 && r != 0 ? "−" + body : body
}

private func pct(_ v: Double, _ d: Int = 1) -> String { num(v * 100, d) + "%" }

private struct DsRng {
    var state: Int64
    init(_ seed: Int64) { state = seed }
    mutating func u() -> Double {
        state = (state * 1103515245 + 12345) & 0x7fffffff
        return Double(state) / 2147483648.0
    }
    mutating func g() -> Double {
        let a = max(u(), 1e-12)
        let b = u()
        return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b)
    }
}

private struct DsP { let x: Double; let y: Double; init(_ x: Double, _ y: Double) { self.x = x; self.y = y } }

private typealias Range2 = ((Double, Double), (Double, Double))

private func boundsOf(_ pts: [DsP], _ pad: Double) -> Range2 {
    ((pts.map(\.x).min()! - pad, pts.map(\.x).max()! + pad), (pts.map(\.y).min()! - pad, pts.map(\.y).max()! + pad))
}

private func sum<S: Sequence>(_ s: S, _ f: (S.Element) -> Double) -> Double { s.reduce(0.0) { $0 + f($1) } }

private func sq(_ v: Double) -> Double { v * v }

/// Index of the first largest key, as Kotlin's maxBy picks it.
private func firstMax(_ range: Range<Int>, _ key: (Int) -> Double) -> Int {
    var best = range.lowerBound
    for i in range where key(i) > key(best) { best = i }
    return best
}

/// Sample covariance (n − 1) of 2-D points: mean, then cxx, cxy, cyy.
private struct Cov2 {
    let mx: Double, my: Double, a: Double, b: Double, c: Double
    var l1: Double { (a + c) / 2 + (sq((a - c) / 2) + b * b).squareRoot() }
    var l2: Double { (a + c) / 2 - (sq((a - c) / 2) + b * b).squareRoot() }
    /// Angle of the top eigenvector.
    var angle: Double { 0.5 * atan2(2 * b, a - c) }
}

private func cov2(_ pts: [DsP]) -> Cov2 {
    let n = Double(pts.count)
    let mx = sum(pts) { $0.x } / n
    let my = sum(pts) { $0.y } / n
    return Cov2(mx: mx, my: my, a: sum(pts) { ($0.x - mx) * ($0.x - mx) } / (n - 1), b: sum(pts) { ($0.x - mx) * ($0.y - my) } / (n - 1), c: sum(pts) { ($0.y - my) * ($0.y - my) } / (n - 1))
}

private func axisGap(_ a: Double, _ b: Double) -> Double {
    let d = (abs(a - b) * 180 / Double.pi).truncatingRemainder(dividingBy: 180)
    return min(d, 180 - d)
}

private func sqDist(_ a: [Double], _ b: [Double]) -> Double { sum(a.indices) { sq(a[$0] - b[$0]) } }

private func corr(_ a: [Double], _ b: [Double]) -> Double {
    let ma = sum(a) { $0 } / Double(a.count)
    let mb = sum(b) { $0 } / Double(b.count)
    return sum(a.indices) { (a[$0] - ma) * (b[$0] - mb) } / (sum(a) { sq($0 - ma) } * sum(b) { sq($0 - mb) }).squareRoot()
}

// MARK: - Model

private struct DsDot { let p: DsP; let color: Color; var r: CGFloat = 3.5; var ring: Color? = nil; var ringR: CGFloat = 0; var ringWidth: CGFloat = 2 }

private struct DsSeg { let a: DsP; let b: DsP; let color: Color; var dashed = false; var width: CGFloat = 1.5 }

private struct DsPlot {
    let dots: [DsDot]
    let xr: (Double, Double)
    let yr: (Double, Double)
    var segs: [DsSeg] = []
    var aspect: CGFloat = 1.75
    /// Equal scale on both axes, so angles stay true.
    var uniform = true
}

private struct DsStrip { let label: String; let values: [Double]; let colors: [Color] }

private struct DsCell { let label: String; let value: String; var fill: StoryTone? = nil; var ink: StoryTone? = nil; var muted = false }

private struct DsRow { let cells: [String]; var ink: StoryTone? = nil; var fill: StoryTone? = nil; var muted = false }

private struct DsLoad { let name: String; let lambda: Double?; var highlight = false }

private enum DsBlock {
    case plot(DsPlot)
    case strips([DsStrip])
    case curve(caption: String, ys: [Double], marker: Int, label: String)
    case cells([DsCell])
    case table(header: [String], rows: [DsRow], weights: [CGFloat])
    case formula([String])
    case loadings([DsLoad])
}

private struct DsFrame {
    let headline: String
    let body: String
    let blocks: [DsBlock]
    var legend: [(Color, SwatchStyle, String)] = []
    var chips: [LabChip] = []
    var action = ""
}

private struct DsParam { let name: String; let symbol: String; let values: [Double]; let initial: Int; let format: (Double) -> String }

private struct DsState: Equatable { var tab = 0; var param = 0; var flag = 0; var index = 0 }

private enum DsControl {
    /// A step track with the step's action beside a back button.
    case track
    /// A step track over a stepper that re-runs the story, no action row.
    case trackParam
    /// Segmented tabs over a back-and-action row.
    case steps
    /// A stepper, and a button when `button` is set.
    case button
}

private struct DsLab {
    let control: DsControl
    var tabs: [String] = []
    var param: DsParam? = nil
    var button: ((DsState) -> String)? = nil
    var onButton: (DsState) -> DsState = { $0 }
    let frames: (DsState) -> [DsFrame]

    var initial: DsState { DsState(tab: 0, param: param?.initial ?? 0, flag: 0, index: 0) }
}

/// Memoises the slow runs (t-SNE, LLE, kernel PCA) across stepper presses.
private final class DsCache<K: Hashable, V> {
    private var store: [K: V] = [:]
    func get(_ key: K, _ make: () -> V) -> V {
        if let v = store[key] { return v }
        let v = make()
        store[key] = v
        return v
    }
}

// MARK: - PCA data, shared by PCA, SVD and incremental PCA

private let pcaData: [DsP] = {
    var r = DsRng(9)
    return (0..<18).map { i in
        let x = -2.0 + 4.0 * Double(i) / 17 + r.g() * 0.22
        let y = 0.6 * x + r.g() * 0.13
        return DsP(x, y)
    }
}()

private func unit(_ angle: Double) -> DsP { DsP(cos(angle), sin(angle)) }
private func along(_ c: DsP, _ u: DsP, _ t: Double) -> DsP { DsP(c.x + u.x * t, c.y + u.y * t) }
private func project(_ p: DsP, _ c: DsP, _ u: DsP) -> Double { (p.x - c.x) * u.x + (p.y - c.y) * u.y }

private let pcaLegend: [(Color, SwatchStyle, String)] = [
    (dBlue, .dot, "Data"), (dGreen, .dot, "Projection"), (dViolet, .line, "PC1"), (SimColors.grey, .dashedLine, "PC2"),
]

private func matrix2(_ cv: Cov2) -> String { "C = [\(num(cv.a, 3)), \(num(cv.b, 3)); \(num(cv.b, 3)), \(num(cv.c, 3))]" }

private func pcaLab() -> DsLab {
    DsLab(control: .track) { _ in
        let pts = pcaData
        let cv = cov2(pts)
        let mean = DsP(cv.mx, cv.my)
        let u1 = unit(cv.angle)
        let u2 = DsP(-u1.y, u1.x)
        let reach = pts.map { abs(project($0, mean, u1)) }.max()! * 1.15
        let (xr, yr) = boundsOf(pts, 0.35)
        let keep = cv.l1 / (cv.l1 + cv.l2)
        let pc1 = DsSeg(a: along(mean, u1, -reach), b: along(mean, u1, reach), color: dViolet, width: 2)
        let pc2 = DsSeg(a: along(mean, u2, -reach * 0.35), b: along(mean, u2, reach * 0.35), color: SimColors.grey, dashed: true, width: 1.2)
        let proj = pts.map { along(mean, u1, project($0, mean, u1)) }
        let dataDots = pts.map { DsDot(p: $0, color: dBlue) }
        let meanDot = DsDot(p: mean, color: .clear, r: 0, ring: .white, ringR: 6, ringWidth: 2)
        let lambdas = "λ₁ = {v:\(num(cv.l1, 3))}  λ₂ = \(num(cv.l2, 3))"
        // Centred copies for the covariance step, so the axes cross at the origin.
        let centred = pts.map { DsP($0.x - mean.x, $0.y - mean.y) }
        let (cxr, cyr) = boundsOf(centred, 0.35)
        let scores = pts.map { project($0, mean, u1) }
        return [
            DsFrame(
                headline: "18 points where x and y {rise together}.",
                body: "PCA looks for the direction the data varies most along. First it centres the cloud on its mean.",
                blocks: [.plot(DsPlot(dots: dataDots + [meanDot], xr: xr, yr: yr)), .formula(["mean = (\(num(cv.mx)), \(num(cv.my)))"])],
                legend: [(dBlue, .dot, "Data"), (.white, .ring, "Mean")],
                action: "Centre Data"
            ),
            DsFrame(
                headline: "Covariance {\(num(cv.b, 3))}: x and y move together.",
                body: "The diagonal holds each variance; the off-diagonal says how strongly the two co-vary.",
                blocks: [
                    .plot(DsPlot(dots: centred.map { DsDot(p: $0, color: dBlue) }, xr: cxr, yr: cyr, segs: [
                        DsSeg(a: DsP(cxr.0, 0), b: DsP(cxr.1, 0), color: SimColors.grey.opacity(0.5), width: 1),
                        DsSeg(a: DsP(0, cyr.0), b: DsP(0, cyr.1), color: SimColors.grey.opacity(0.5), width: 1),
                    ])),
                    .formula([matrix2(cv)]),
                ],
                legend: [(dBlue, .dot, "Centred data")],
                action: "Find Axes"
            ),
            DsFrame(
                headline: "PC1 runs along the cloud, with λ₁ = {v:\(num(cv.l1, 3))}.",
                body: "The eigenvectors of C are the new axes; each eigenvalue is the variance along its axis.",
                blocks: [.plot(DsPlot(dots: dataDots, xr: xr, yr: yr, segs: [pc2, pc1])), .formula([matrix2(cv), lambdas])],
                legend: pcaLegend.filter { $0.2 != "Projection" },
                action: "Drop PC2"
            ),
            DsFrame(
                headline: "PC1 keeps {\(pct(keep))} of the variance.",
                body: "Dropping PC2 loses only the short gaps drawn between each point and its projection.",
                blocks: [
                    .plot(DsPlot(dots: dataDots + proj.map { DsDot(p: $0, color: dGreen, r: 3) }, xr: xr, yr: yr,
                                 segs: [pc2, pc1] + pts.indices.map { DsSeg(a: pts[$0], b: proj[$0], color: SimColors.grey, width: 1) })),
                    .formula([matrix2(cv), lambdas]),
                ],
                legend: pcaLegend,
                action: "Project Points"
            ),
            DsFrame(
                headline: "Each point is now {one number}: its PC1 score.",
                body: "18 × 2 values became 18 × 1, and the scores still span the cloud's full length.",
                blocks: [
                    .plot(DsPlot(dots: scores.map { DsDot(p: DsP($0, 0), color: dGreen) }, xr: (-reach, reach), yr: (-0.6, 0.6),
                                 segs: [DsSeg(a: DsP(-reach, 0), b: DsP(reach, 0), color: dViolet, width: 2)], aspect: 5)),
                    .formula(["z = (x − mean) · v₁,  range \(num(scores.min()!)) to \(num(scores.max()!))"]),
                ],
                legend: [(dGreen, .dot, "PC1 score"), (dViolet, .line, "PC1")],
                action: "Start Over"
            ),
        ]
    }
}

// MARK: - SVD

private func svdLab() -> DsLab {
    DsLab(control: .steps, tabs: ["Rank 1", "Rank 2"]) { s in
        let pts = pcaData
        let n = Double(pts.count)
        let cv = cov2(pts)
        let mean = DsP(cv.mx, cv.my)
        let u1 = unit(cv.angle)
        let u2 = DsP(-u1.y, u1.x)
        let s1 = (cv.l1 * (n - 1)).squareRoot()
        let s2 = (cv.l2 * (n - 1)).squareRoot()
        let (xr, yr) = boundsOf(pts, 0.35)
        let rank = s.tab + 1
        let fits = rank == 1 ? pts.map { along(mean, u1, project($0, mean, u1)) } : pts
        let energy = rank == 1 ? s1 * s1 / (s1 * s1 + s2 * s2) : 1.0
        let error = rank == 1 ? sum(pts.indices) { sq(pts[$0].x - fits[$0].x) + sq(pts[$0].y - fits[$0].y) }.squareRoot() : 0.0
        // v₁ and v₂ drawn from the centre, 2 standard deviations long, so their lengths compare like σ₁ and σ₂.
        let v1 = DsSeg(a: mean, b: along(mean, u1, 2 * cv.l1.squareRoot()), color: dViolet, width: 2.5)
        let v2 = DsSeg(a: mean, b: along(mean, u2, 2 * cv.l2.squareRoot()), color: SimColors.active, width: 2.5)
        let centre = DsDot(p: mean, color: .white, r: 3, ring: .white, ringR: 1.5, ringWidth: 1.5)
        let legend: [(Color, SwatchStyle, String)] = [(dViolet, .line, "v₁ (length ∝ σ₁)"), (SimColors.active, .line, "v₂"), (dGreen, .dot, "Rank-\(rank) fit")]
        let formula = DsBlock.formula(["X = U Σ Vᵀ, σ₁ = {v:\(num(s1, 3))}, σ₂ = {\(num(s2, 3))}", "σ₁² / (n−1) = \(num(cv.l1, 3)) = λ₁ of PCA"])
        let darkData = dBlue.opacity(0.75)
        return [
            DsFrame(
                headline: "X holds {18 rows × 2 columns}, centred on the mean.",
                body: "SVD factors any matrix into a rotation, a stretch and a rotation, with no covariance matrix needed.",
                blocks: [.plot(DsPlot(dots: pts.map { DsDot(p: $0, color: dBlue) } + [centre], xr: xr, yr: yr)), .formula(["X = U Σ Vᵀ"])],
                legend: [(dBlue, .dot, "Data"), (.white, .dot, "Centre")],
                action: "Decompose"
            ),
            DsFrame(
                headline: rank == 1 ? "Keeping σ₁ alone rebuilds {\(pct(energy))} of the data's energy." : "Keeping σ₁ and σ₂ rebuilds {100%} of the data's energy.",
                body: rank == 1 ? "On centred data these are PCA's axes, found without forming the covariance matrix."
                    : "A 2-column matrix has only two singular values, so rank 2 is X itself.",
                blocks: [.plot(DsPlot(dots: pts.map { DsDot(p: $0, color: darkData) } + fits.map { DsDot(p: $0, color: dGreen, r: 3) } + [centre], xr: xr, yr: yr, segs: [v1, v2])), formula],
                legend: legend,
                action: "Reconstruct"
            ),
            DsFrame(
                headline: rank == 1 ? "The rank-1 error is exactly σ₂: {\(num(error, 3))}." : "Rank 2 rebuilds X exactly: error {m:0.000}.",
                body: rank == 1 ? "No rank-1 matrix gets closer. The residual is the dropped v₂ direction, drawn as gaps."
                    : "Compression only pays when you keep fewer singular values than there are columns.",
                blocks: [
                    .plot(DsPlot(dots: pts.map { DsDot(p: $0, color: darkData) } + fits.map { DsDot(p: $0, color: dGreen, r: 3) }, xr: xr, yr: yr,
                                 segs: [v1] + (rank == 1 ? pts.indices.map { DsSeg(a: pts[$0], b: fits[$0], color: SimColors.grey, width: 1) } : []))),
                    .formula(["‖X − X\(rank)‖ = \(num(error, 3))" + (rank == 1 ? " = σ₂" : "")]),
                ],
                legend: legend.filter { $0.2 != "v₂" },
                action: "Start Over"
            ),
        ]
    }
}

// MARK: - Incremental PCA

private let streamOrder: [Int] = {
    var r = DsRng(13)
    var o = Array(0..<18)
    for i in stride(from: 17, through: 1, by: -1) {
        let j = Int(r.u() * Double(i + 1))
        o.swapAt(i, j)
    }
    return o
}()

private func incrementalLab() -> DsLab {
    DsLab(control: .track) { _ in
        let pts = pcaData
        let full = cov2(pts)
        let (xr, yr) = boundsOf(pts, 0.35)
        let fullMean = DsP(full.mx, full.my)
        let fu = unit(full.angle)
        let reach = pts.map { abs(project($0, fullMean, fu)) }.max()! * 1.15
        let batchLine = DsSeg(a: along(fullMean, fu, -reach), b: along(fullMean, fu, reach), color: SimColors.grey, dashed: true, width: 1.2)
        let sizes = [4, 8, 12, 16, 18]
        let errors = sizes.map { m in axisGap(cov2(streamOrder.prefix(m).map { pts[$0] }).angle, full.angle) }
        let kept = DsBlock.formula(["kept: n, mean (2), Σxxᵀ (3) = {6 numbers}"])
        let legend: [(Color, SwatchStyle, String)] = [(dBlue, .dot, "Seen"), (notYet, .dot, "Not yet"), (dViolet, .line, "Running PC1"), (SimColors.grey, .dashedLine, "Batch PC1")]
        func cells(_ current: Int) -> DsBlock {
            .cells(sizes.indices.map { b in
                if b < current { return DsCell(label: "batch \(b + 1)", value: "\(num(errors[b], 1))°") }
                if b == current { return DsCell(label: "batch \(b + 1)", value: "\(num(errors[b], 1))°", fill: .active, ink: .active) }
                return DsCell(label: "batch \(b + 1)", value: "—", muted: true)
            })
        }
        func plot(_ seen: Int) -> DsBlock {
            let seenSet = Set(streamOrder.prefix(seen))
            let dots = pts.indices.map { DsDot(p: pts[$0], color: seenSet.contains($0) ? dBlue : notYet) }
            if seen == 0 { return .plot(DsPlot(dots: dots, xr: xr, yr: yr, segs: [batchLine])) }
            // Kotlin iterates the seen rows in stream order; keep the same order for identical sums.
            let seenRows = streamOrder.prefix(seen).map { pts[$0] }
            let cv = cov2(seenRows)
            let m = DsP(cv.mx, cv.my)
            let u = unit(cv.angle)
            let ts = seenRows.map { project($0, m, u) }
            let run = DsSeg(a: along(m, u, ts.min()! - 0.3), b: along(m, u, ts.max()! + 0.3), color: dViolet, width: 2.5)
            return .plot(DsPlot(dots: dots + [DsDot(p: m, color: .clear, r: 0, ring: SimColors.active, ringR: 7, ringWidth: 2)], xr: xr, yr: yr, segs: [batchLine, run]))
        }
        var frames = [
            DsFrame(
                headline: "18 rows will arrive {4 at a time}.",
                body: "Batch PCA needs every row in memory. Incremental PCA keeps running sums instead.",
                blocks: [plot(0), cells(-1), kept],
                legend: legend.filter { $0.2 != "Running PC1" },
                action: "First Batch"
            ),
        ]
        for (b, m) in sizes.enumerated() {
            let last = b == sizes.count - 1
            frames.append(DsFrame(
                headline: last ? "After all 18 rows, the running PC1 is {\(num(errors[b], 1))°} off." : "After \(m) of 18 rows, the running PC1 is {\(num(errors[b], 1))°} off.",
                body: last ? "The sums are exact, so the streamed answer equals batch PCA." : "Memory stays at 6 numbers however many rows stream past.",
                blocks: [plot(m), cells(b), kept],
                legend: legend,
                action: last ? "Compare" : "Next Batch"
            ))
        }
        frames.append(DsFrame(
            headline: "The same axis as batch PCA, from {6 numbers} instead of 36.",
            body: "With d columns the kept state is 1 + d + d(d+1)/2 numbers, however long the stream runs.",
            blocks: [plot(18), cells(sizes.count), kept],
            legend: legend,
            action: "Start Over"
        ))
        return frames
    }
}

// MARK: - Kernel PCA

private let ringData: [DsP] = {
    var r = DsRng(4)
    return (0..<50).map { i in
        let a = r.u() * 2 * Double.pi
        let rad = (i < 20 ? 1.0 : 3.0) + r.g() * 0.15
        return DsP(rad * cos(a), rad * sin(a))
    }
}()

private let innerCount = 20

/// Best accuracy of one threshold on a 1-D score, taking whichever side is the inner ring.
private func thresholdAccuracy(_ scores: [Double]) -> Double {
    let n = scores.count
    let order = scores.indices.sorted { (scores[$0], $0) < (scores[$1], $1) }
    var best = 0
    for cut in 0...n {
        let low = Set(order.prefix(cut))
        let c = (0..<n).filter { low.contains($0) == ($0 < innerCount) }.count
        best = max(best, max(c, n - c))
    }
    return Double(best) / Double(n)
}

private func kernelPc1(_ pts: [DsP], _ gamma: Double) -> [Double] {
    let n = pts.count
    let k = (0..<n).map { i in (0..<n).map { j in exp(-gamma * (sq(pts[i].x - pts[j].x) + sq(pts[i].y - pts[j].y))) } }
    let rowMean = (0..<n).map { sum(k[$0]) { $0 } / Double(n) }
    let grand = sum(rowMean) { $0 } / Double(n)
    let centred = (0..<n).map { i in (0..<n).map { j in k[i][j] - rowMean[i] - rowMean[j] + grand } }
    let e = jacobiEigen(centred)
    return (0..<n).map { e.vectors[0][$0] * max(e.values[0], 1e-12).squareRoot() }
}

private func gammaText(_ g: Double) -> String { num(g, g < 0.1 ? 2 : g < 1 ? 1 : 0) }

private func kernelLab() -> DsLab {
    let gammas = [0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0]
    let pts = ringData
    let cv = cov2(pts)
    let mean = DsP(cv.mx, cv.my)
    let u = unit(cv.angle)
    let linear = pts.map { project($0, mean, u) }
    let linAcc = thresholdAccuracy(linear)
    let (xr, yr) = boundsOf(pts, 0.3)
    let colors = pts.indices.map { $0 < innerCount ? dBlue : dOrange }
    let kernels = DsCache<Int, [Double]>()
    func unitScale(_ v: [Double]) -> [Double] {
        let top = max(v.map { abs($0) }.max()!, 1e-12)
        return v.map { $0 / top }
    }
    return DsLab(control: .button, param: DsParam(name: "Kernel width", symbol: "γ", values: gammas, initial: 3, format: gammaText)) { s in
        let g = gammas[s.param]
        let kp = kernels.get(s.param) { kernelPc1(pts, g) }
        let acc = thresholdAccuracy(kp)
        let good = acc >= 0.95
        let gText = gammaText(g)
        let line = DsSeg(a: along(mean, u, -3.4), b: along(mean, u, 3.4), color: dViolet, dashed: true, width: 1.5)
        let body: String
        if good { body = "No straight axis splits concentric rings. The kernel measures closeness instead, so radius becomes the axis." }
        else if g < 0.5 { body = "At γ = \(gText) the kernel is so wide that every point looks alike, and kernel PC1 is nearly linear again." }
        else { body = "At γ = \(gText) the kernel only sees each point's nearest neighbours, so PC1 picks out local patches, not rings." }
        return [
            DsFrame(
                headline: "Linear PC1 separates {w:\(pct(linAcc, 0))}; kernel PC1 separates {\(good ? "m" : "w"):\(pct(acc, 0))}.",
                body: body,
                blocks: [
                    .plot(DsPlot(dots: pts.indices.map { DsDot(p: pts[$0], color: colors[$0]) }, xr: xr, yr: yr, segs: [line], aspect: 1.9)),
                    .strips([DsStrip(label: "linear PC1", values: unitScale(linear), colors: colors), DsStrip(label: "kernel PC1 (γ = \(gText))", values: unitScale(kp), colors: colors)]),
                    .formula(["k(x, x′) = exp(−γ‖x − x′‖²)"]),
                ],
                legend: [(dBlue, .dot, "Inner ring"), (dOrange, .dot, "Outer ring"), (dViolet, .dashedLine, "Linear PC1")],
                chips: [
                    LabChip(key: "linear", value: pct(linAcc, 0), tint: .warn),
                    good ? LabChip(key: "kernel", value: pct(acc, 0), good: true) : LabChip(key: "kernel", value: pct(acc, 0), tint: .warn),
                ]
            ),
        ]
    }
}

// MARK: - ICA

private let icaSources: [DsP] = {
    var r = DsRng(26)
    let s3 = 3.0.squareRoot()
    return (0..<150).map { _ in
        let a = (r.u() * 2 - 1) * s3
        let b = (r.u() * 2 - 1) * s3
        return DsP(a, b)
    }
}()

private let icaMix = [[1.0, 0.6], [0.4, 1.0]]

private func kurtosisOf(_ v: [Double]) -> Double {
    let m = sum(v) { $0 } / Double(v.count)
    let m2 = sum(v) { sq($0 - m) } / Double(v.count)
    let m4 = sum(v) { sq(sq($0 - m)) } / Double(v.count)
    return m4 / (m2 * m2) - 3
}

private func rotate(_ z: [DsP], _ deg: Double) -> [DsP] {
    let t = deg * Double.pi / 180
    let c = cos(t), s = sin(t)
    return z.map { DsP(c * $0.x + s * $0.y, -s * $0.x + c * $0.y) }
}

private func nonGaussianity(_ z: [DsP], _ deg: Double) -> Double {
    let r = rotate(z, deg)
    return abs(kurtosisOf(r.map(\.x))) + abs(kurtosisOf(r.map(\.y)))
}

private func icaLab() -> DsLab {
    DsLab(control: .track) { _ in
        let src = icaSources
        let mixed = src.map { DsP(icaMix[0][0] * $0.x + icaMix[0][1] * $0.y, icaMix[1][0] * $0.x + icaMix[1][1] * $0.y) }
        // Whiten: rotate onto the covariance's eigenvectors, then scale each axis to unit variance.
        let n = Double(mixed.count)
        let mx = sum(mixed) { $0.x } / n
        let my = sum(mixed) { $0.y } / n
        let a = sum(mixed) { sq($0.x - mx) } / n
        let b = sum(mixed) { ($0.x - mx) * ($0.y - my) } / n
        let c = sum(mixed) { sq($0.y - my) } / n
        let tr = (a + c) / 2
        let dd = (sq((a - c) / 2) + b * b).squareRoot()
        let th = 0.5 * atan2(2 * b, a - c)
        let eu = DsP(cos(th), sin(th))
        let ev = DsP(-sin(th), cos(th))
        let white = mixed.map {
            DsP((($0.x - mx) * eu.x + ($0.y - my) * eu.y) / (tr + dd).squareRoot(), (($0.x - mx) * ev.x + ($0.y - my) * ev.y) / (tr - dd).squareRoot())
        }
        let curve = (0...90).map { nonGaussianity(white, Double($0)) }
        let best = firstMax(0..<90) { curve[$0] }
        let out = rotate(white, Double(best))
        let s1 = src.map(\.x), s2 = src.map(\.y), o1 = out.map(\.x), o2 = out.map(\.y)
        let straight = abs(corr(o1, s1)) + abs(corr(o2, s2))
        let swapped = abs(corr(o1, s2)) + abs(corr(o2, s1))
        let cs = straight >= swapped ? [abs(corr(o1, s1)), abs(corr(o2, s2))] : [abs(corr(o1, s2)), abs(corr(o2, s1))]
        let corrChip = LabChip(key: "|corr| with sources", value: "\(num(cs[0], 3)), \(num(cs[1], 3))", good: true)
        func square(_ p: [DsP], _ pad: Double) -> Range2 {
            let m = p.map { max(abs($0.x), abs($0.y)) }.max()! + pad
            return ((-m, m), (-m, m))
        }
        let t = Double(best) * Double.pi / 180
        let reach = 2.6
        let dirs = [
            DsSeg(a: DsP(-cos(t) * reach, -sin(t) * reach), b: DsP(cos(t) * reach, sin(t) * reach), color: SimColors.active, width: 2),
            DsSeg(a: DsP(sin(t) * reach, -cos(t) * reach), b: DsP(-sin(t) * reach, cos(t) * reach), color: SimColors.active, width: 2),
        ]
        let axes = [
            DsSeg(a: DsP(-reach, 0), b: DsP(reach, 0), color: SimColors.active, width: 2),
            DsSeg(a: DsP(0, -reach), b: DsP(0, reach), color: SimColors.active, width: 2),
        ]
        let (wx, wy) = square(white, 0.2)
        let (sx, sy) = square(src, 0.2)
        let mixedC = mixed.map { DsP($0.x - mx, $0.y - my) }
        let (mxr, myr) = square(mixedC, 0.2)
        let curveBlock = DsBlock.curve(caption: "non-Gaussianity vs rotation angle", ys: curve, marker: best, label: "\(best)°")
        return [
            DsFrame(
                headline: "Two hidden sources, each {uniform} and independent.",
                body: "ICA's job is to recover them from mixtures alone, without knowing how they were mixed.",
                blocks: [.plot(DsPlot(dots: src.map { DsDot(p: $0, color: dGreen, r: 2.8) }, xr: sx, yr: sy, uniform: false)), .formula(["s₁, s₂ ~ Uniform(−√3, √3)"])],
                legend: [(dGreen, .dot, "Hidden sources")],
                action: "Mix"
            ),
            DsFrame(
                headline: "The sensors only see {mixtures}: a slanted cloud.",
                body: "Each reading blends both sources, so neither one is a source on its own.",
                blocks: [.plot(DsPlot(dots: mixedC.map { DsDot(p: $0, color: dPink, r: 2.8) }, xr: mxr, yr: myr, uniform: false)), .formula(["x = A s,  A = [1.0, 0.6; 0.4, 1.0]"])],
                legend: [(dPink, .dot, "Mixture")],
                action: "Whiten"
            ),
            DsFrame(
                headline: "Whitening removes all correlation: {cov = I}.",
                body: "This is where PCA stops. Every rotation of it is still uncorrelated, so correlation cannot pick the angle.",
                blocks: [.plot(DsPlot(dots: white.map { DsDot(p: $0, color: dPink, r: 2.8) }, xr: wx, yr: wy, uniform: false)), .formula(["z = Λ^−½ Eᵀ (x − mean),  cov(z) = I"])],
                legend: [(dPink, .dot, "Whitened mixture")],
                action: "Search Angles"
            ),
            DsFrame(
                headline: "Whitened, only a rotation is left. ICA picks {\(best)°}.",
                body: "That angle makes the outputs least Gaussian, and they match the hidden sources.",
                blocks: [.plot(DsPlot(dots: white.map { DsDot(p: $0, color: dPink, r: 2.8) }, xr: wx, yr: wy, segs: dirs, uniform: false)), curveBlock],
                legend: [(dPink, .dot, "Whitened mixture"), (SimColors.active, .line, "ICA directions")],
                chips: [corrChip],
                action: "Rotate"
            ),
            DsFrame(
                headline: "Rotated by \(best)°, the outputs {are the sources}.",
                body: "Only order and sign stay unknown. Mixing makes signals more Gaussian; ICA undoes it.",
                blocks: [.plot(DsPlot(dots: out.map { DsDot(p: $0, color: dGreen, r: 2.8) }, xr: wx, yr: wy, segs: axes, uniform: false)), curveBlock],
                legend: [(dGreen, .dot, "Recovered sources"), (SimColors.active, .line, "ICA directions")],
                chips: [corrChip],
                action: "Start Over"
            ),
        ]
    }
}

// MARK: - Factor analysis

private let faLoadingsTrue = [0.85, 0.40, 0.85]

private let faCorrelation: [[Double]] = {
    var r = DsRng(10)
    var rows: [[Double]] = []
    for _ in 0..<300 {
        let f = r.g()
        var row: [Double] = []
        for k in 0..<3 { row.append(faLoadingsTrue[k] * f + r.g() * (1 - sq(faLoadingsTrue[k])).squareRoot()) }
        rows.append(row)
    }
    let n = Double(rows.count)
    let m = (0..<3).map { k in sum(rows) { $0[k] } / n }
    let sd = (0..<3).map { k in (sum(rows) { sq($0[k] - m[k]) } / n).squareRoot() }
    return (0..<3).map { i in (0..<3).map { j in sum(rows) { ($0[i] - m[i]) * ($0[j] - m[j]) } / n / (sd[i] * sd[j]) } }
}()

private func faLab() -> DsLab {
    DsLab(control: .track) { _ in
        let r = faCorrelation
        let r12 = r[0][1], r13 = r[0][2], r23 = r[1][2]
        let fa = [(r12 * r13 / r23).squareRoot(), (r12 * r23 / r13).squareRoot(), (r13 * r23 / r12).squareRoot()]
        let e = jacobiEigen(r)
        let pca = (0..<3).map { abs(e.vectors[0][$0]) * e.values[0].squareRoot() }
        let names = ["x1", "x2", "x3"]
        let w: [CGFloat] = [1.3, 1, 1, 1]
        let model = "xᵢ = λᵢ f + εᵢ, Var(εᵢ) = ψᵢ"
        let legend: [(Color, SwatchStyle, String)] = [(dViolet, .fill, "Shared (λ²)"), (SimColors.grey.opacity(0.5), .fill, "Unique (ψ)")]
        let loadTable = DsBlock.table(header: [""] + names, rows: [DsRow(cells: ["FA λ"] + fa.map { num($0) }), DsRow(cells: ["PCA"] + pca.map { num($0) }, muted: true)], weights: w)
        func reproduced(_ l: [Double]) -> [Double] { [l[0] * l[1], l[0] * l[2], l[1] * l[2]] }
        let observed = [r12, r13, r23]
        let pcaMiss = (0..<3).map { abs(reproduced(pca)[$0] - observed[$0]) }.max()!
        return [
            DsFrame(
                headline: "x1 and x3 correlate {\(num(r13))}; x2 barely joins in.",
                body: "Three measured variables, 300 samples. One hidden factor f could explain every correlation at once.",
                blocks: [.table(header: ["r"] + names, rows: names.indices.map { i in DsRow(cells: [names[i]] + (0..<3).map { num(r[i][$0]) }, muted: i == 1) }, weights: w)],
                action: "Model"
            ),
            DsFrame(
                headline: "Each variance splits into {shared λ²} and unique ψ.",
                body: "For standardised data λ² + ψ = 1. PCA has no ψ: it treats all variance as shared.",
                blocks: [.loadings(names.map { DsLoad(name: $0, lambda: nil) }), .formula([model, "rᵢⱼ = λᵢ λⱼ"])],
                legend: legend,
                action: "Solve λ₁"
            ),
            DsFrame(
                headline: "x1 loads {\(num(fa[0]))} on the factor.",
                body: "Three correlations, three loadings: one factor is solved exactly from ratios of r.",
                blocks: [
                    .loadings([DsLoad(name: "x1", lambda: fa[0], highlight: true), DsLoad(name: "x2", lambda: nil), DsLoad(name: "x3", lambda: nil)]),
                    .formula([model, "λ₁ = √(r₁₂ r₁₃ / r₂₃) = {\(num(fa[0]))}"]),
                ],
                legend: legend,
                action: "Solve λ₂"
            ),
            DsFrame(
                headline: "x2 is mostly its own noise: {ψ = \(num(1 - sq(fa[1])))}.",
                body: "PCA has no ψ, so it gives x2 a loading of \(num(pca[1])), counting noise as signal.",
                blocks: [
                    .loadings([DsLoad(name: "x1", lambda: fa[0]), DsLoad(name: "x2", lambda: fa[1], highlight: true), DsLoad(name: "x3", lambda: fa[2])]),
                    .formula([model, "λ₂ = √(r₁₂ r₂₃ / r₁₃) = {\(num(fa[1]))}"]),
                    loadTable,
                ],
                legend: legend,
                action: "Fit Loadings"
            ),
            DsFrame(
                headline: "FA's loadings rebuild every correlation {exactly}.",
                body: "PCA's loadings miss by up to \(num(pcaMiss)), because they also try to explain each variable's own noise.",
                blocks: [
                    .loadings(names.indices.map { DsLoad(name: names[$0], lambda: fa[$0]) }),
                    .table(header: ["", "r₁₂", "r₁₃", "r₂₃"], rows: [
                        DsRow(cells: ["observed"] + observed.map { num($0) }),
                        DsRow(cells: ["FA λλ"] + reproduced(fa).map { num($0) }, ink: .done),
                        DsRow(cells: ["PCA"] + reproduced(pca).map { num($0) }, muted: true),
                    ], weights: w),
                ],
                legend: legend,
                action: "Compare"
            ),
            DsFrame(
                headline: "FA models the {shared} variance; PCA the total.",
                body: "The data came from loadings \(faLoadingsTrue.map { num($0) }.joined(separator: ", ")). Use FA when variables are noisy measures of a hidden trait.",
                blocks: [
                    .loadings(names.indices.map { DsLoad(name: names[$0], lambda: fa[$0]) }),
                    .table(header: [""] + names, rows: [
                        DsRow(cells: ["true λ"] + faLoadingsTrue.map { num($0) }, ink: .done),
                        DsRow(cells: ["FA λ"] + fa.map { num($0) }),
                        DsRow(cells: ["PCA"] + pca.map { num($0) }, muted: true),
                    ], weights: w),
                ],
                legend: legend,
                action: "Start Over"
            ),
        ]
    }
}

// MARK: - t-SNE and UMAP share 45 points in 5-D: two tight clusters and one loose one

private let clusters5: [[Double]] = {
    var r = DsRng(5)
    func blob(_ n: Int, _ c: [Double], _ sd: Double) -> [[Double]] {
        var out: [[Double]] = []
        for _ in 0..<n { var p: [Double] = []; for d in 0..<5 { p.append(c[d] + r.g() * sd) }; out.append(p) }
        return out
    }
    let a = blob(15, [0, 0, 0, 0, 0], 0.25)
    let b = blob(15, [3, 1, 0, 0, 0], 0.25)
    let c = blob(15, [1, 7.5, 0, 0, 0], 1.45)
    return a + b + c
}()

private let groupA = 0..<15
private let groupC = 30..<45

/// Root-mean-square distance to the group's centroid.
private func spread(_ pts: [[Double]], _ idx: Range<Int>) -> Double {
    let d = pts[0].count
    let m = (0..<d).map { k in sum(idx) { pts[$0][k] } / Double(idx.count) }
    return (sum(idx) { sqDist(pts[$0], m) } / Double(idx.count)).squareRoot()
}

private func clusterLegend() -> [(Color, SwatchStyle, String)] { [(dBlue, .dot, "Tight A"), (dOrange, .dot, "Tight B"), (dGreen, .dot, "Loose C")] }

private struct TsneRun { let y: [[Double]]; let kl: Double }

private func runTsne(_ x: [[Double]], _ perplexity: Double, _ seed: Int64, iterations: Int = 500) -> TsneRun {
    let n = x.count
    let d = (0..<n).map { i in (0..<n).map { j in sqDist(x[i], x[j]) } }
    var cond = Array(repeating: Array(repeating: 0.0, count: n), count: n)
    let target = log(perplexity)
    for i in 0..<n {
        var lo = 0.0, hi = Double.infinity, beta = 1.0
        var row = Array(repeating: 0.0, count: n)
        var s = 1.0
        for _ in 0..<60 {
            row = (0..<n).map { j in j == i ? 0.0 : exp(-d[i][j] * beta) }
            s = max(sum(row) { $0 }, 1e-300)
            let h = log(s) + beta * sum(0..<n) { d[i][$0] * row[$0] } / s
            if abs(h - target) < 1e-6 { break }
            if h > target {
                lo = beta
                beta = hi == .infinity ? beta * 2 : (beta + hi) / 2
            } else {
                hi = beta
                beta = (beta + lo) / 2
            }
        }
        for j in 0..<n { cond[i][j] = row[j] / s }
    }
    let p = (0..<n).map { i in (0..<n).map { j in (cond[i][j] + cond[j][i]) / Double(2 * n) } }
    var r = DsRng(seed)
    var y: [[Double]] = []
    for _ in 0..<n { let a = r.g() * 1e-2; let b = r.g() * 1e-2; y.append([a, b]) }
    var v = Array(repeating: [0.0, 0.0], count: n)
    var q = Array(repeating: Array(repeating: 0.0, count: n), count: n)
    for it in 0..<iterations {
        let ex = it < 100 ? 4.0 : 1.0
        let mom = it < 100 ? 0.5 : 0.8
        var z = 0.0
        for i in 0..<n { for j in 0..<n where i != j {
            q[i][j] = 1 / (1 + sq(y[i][0] - y[j][0]) + sq(y[i][1] - y[j][1]))
            z += q[i][j]
        } }
        for i in 0..<n {
            var gx = 0.0, gy = 0.0
            for j in 0..<n where i != j {
                let m = (ex * p[i][j] - q[i][j] / z) * q[i][j]
                gx += 4 * m * (y[i][0] - y[j][0])
                gy += 4 * m * (y[i][1] - y[j][1])
            }
            v[i][0] = mom * v[i][0] - 50.0 * gx
            v[i][1] = mom * v[i][1] - 50.0 * gy
        }
        for i in 0..<n {
            y[i][0] += v[i][0]
            y[i][1] += v[i][1]
        }
    }
    var z = 0.0
    for i in 0..<n { for j in 0..<n where i != j {
        q[i][j] = 1 / (1 + sq(y[i][0] - y[j][0]) + sq(y[i][1] - y[j][1]))
        z += q[i][j]
    } }
    var kl = 0.0
    for i in 0..<n { for j in 0..<n where i != j && p[i][j] > 1e-12 { kl += p[i][j] * log(p[i][j] / (q[i][j] / z)) } }
    return TsneRun(y: y, kl: kl)
}

private func embeddedDots(_ y: [[Double]]) -> [DsDot] { y.indices.map { DsDot(p: DsP(y[$0][0], y[$0][1]), color: clusterColors[$0 / 15]) } }

private func embeddingBounds(_ y: [[Double]]) -> Range2 {
    let pts = y.map { DsP($0[0], $0[1]) }
    let span = max(pts.map(\.x).max()! - pts.map(\.x).min()!, pts.map(\.y).max()! - pts.map(\.y).min()!)
    return boundsOf(pts, span * 0.06)
}

private func spreadTable(_ method: String, _ mapA: Double, _ mapC: Double) -> DsBlock {
    let a = spread(clusters5, groupA)
    let c = spread(clusters5, groupC)
    return .table(header: ["spread", "cluster A", "loose C", "ratio"], rows: [
        DsRow(cells: ["original 5-D", num(a), num(c), num(c / a, 1) + "×"]),
        DsRow(cells: [method, num(mapA), num(mapC), num(mapC / mapA, 1) + "×"], ink: .active, fill: .active),
    ], weights: [1.6, 1.1, 1, 0.8])
}

private struct RunKey: Hashable { let param: Int; let flag: Int }

private func tsneLab() -> DsLab {
    let perps = [2.0, 5.0, 10.0, 20.0, 30.0]
    let runs = DsCache<RunKey, TsneRun>()
    return DsLab(
        control: .button,
        param: DsParam(name: "Perplexity", symbol: "perp", values: perps, initial: 2) { "\(Int($0.rounded()))" },
        button: { _ in "Run Again" },
        onButton: { var s = $0; s.flag = (s.flag + 1) % 5; return s }
    ) { s in
        let perp = perps[s.param]
        let run = runs.get(RunKey(param: s.param, flag: s.flag)) { runTsne(clusters5, perp, Int64(s.flag + 1)) }
        let ya = spread(run.y, groupA), yc = spread(run.y, groupC)
        let oa = spread(clusters5, groupA), oc = spread(clusters5, groupC)
        let (xr, yr) = embeddingBounds(run.y)
        let p = Int(perp.rounded())
        let body: String
        if p <= 2 { body = "At perplexity 2 each point listens to about 2 neighbours, so clusters shatter into strands." }
        else if p >= 30 { body = "Perplexity 30 is twice a cluster's size, so neighbourhoods spill across clusters and runs disagree." }
        else { body = "Perplexity sets a neighbour count, not a distance, so cluster size is not preserved." }
        return [
            DsFrame(
                headline: "C is \(num(oc / oa, 1))× wider than A in 5-D, but only {\(num(yc / ya, 1))×} on the map.",
                body: body,
                blocks: [.plot(DsPlot(dots: embeddedDots(run.y), xr: xr, yr: yr, aspect: 1.9)), spreadTable("t-SNE map", ya, yc)],
                legend: clusterLegend(),
                chips: [LabChip(key: "iteration", value: "500"), LabChip(key: "KL", value: num(run.kl), tint: .answer)]
            ),
        ]
    }
}

// MARK: - UMAP

private struct UmapGraph { let neighbours: [[Int]]; let rho: [Double]; let sigma: [Double]; let directed: [[Double]]; let sym: [[Double]] }

private func dist5(_ a: [Double], _ b: [Double]) -> Double { sqDist(a, b).squareRoot() }

private func fuzzyGraph(_ x: [[Double]], _ k: Int) -> UmapGraph {
    let n = x.count
    let nb = (0..<n).map { i in Array((0..<n).filter { $0 != i }.sorted { (dist5(x[i], x[$0]), $0) < (dist5(x[i], x[$1]), $1) }.prefix(k)) }
    let target = log(Double(k)) / log(2.0)
    var rho = Array(repeating: 0.0, count: n), sigma = Array(repeating: 0.0, count: n)
    var w = Array(repeating: Array(repeating: 0.0, count: n), count: n)
    for i in 0..<n {
        let ds = nb[i].map { dist5(x[i], x[$0]) }
        let r = ds[0]
        var lo = 0.0, hi = Double.infinity, s = 1.0
        for _ in 0..<64 {
            let tot = sum(ds) { exp(-max($0 - r, 0) / s) }
            if abs(tot - target) < 1e-6 { break }
            if tot > target {
                hi = s
                s = (lo + hi) / 2
            } else {
                lo = s
                s = hi == .infinity ? s * 2 : (lo + hi) / 2
            }
        }
        rho[i] = r
        sigma[i] = s
        for (a, j) in nb[i].enumerated() { w[i][j] = exp(-max(ds[a] - r, 0) / s) }
    }
    let sym = (0..<n).map { i in (0..<n).map { j in w[i][j] + w[j][i] - w[i][j] * w[j][i] } }
    return UmapGraph(neighbours: nb, rho: rho, sigma: sigma, directed: w, sym: sym)
}

/// The first two principal components of the 5-D points.
private let clusterPcaView: [DsP] = {
    let x = clusters5
    let n = Double(x.count)
    let m = (0..<5).map { k in sum(x) { $0[k] } / n }
    let c = (0..<5).map { i in (0..<5).map { j in sum(x) { ($0[i] - m[i]) * ($0[j] - m[j]) } / (n - 1) } }
    let e = jacobiEigen(c)
    return x.map { p in DsP(sum(0..<5) { (p[$0] - m[$0]) * e.vectors[0][$0] }, sum(0..<5) { (p[$0] - m[$0]) * e.vectors[1][$0] }) }
}()

private let umapA = 1.577
private let umapB = 0.895

/// Full-gradient UMAP layout from the PCA view; returns the layout after each epoch in `snaps`.
private func layoutUmap(_ sym: [[Double]], _ snaps: [Int]) -> [Int: [[Double]]] {
    let n = sym.count
    let view = clusterPcaView
    let m = view.map { max(abs($0.x), abs($0.y)) }.max()!
    var y = view.map { [$0.x / m * 10, $0.y / m * 10] }
    let epochs = snaps.max()!
    var out: [Int: [[Double]]] = [:]
    if snaps.contains(0) { out[0] = y }
    func clip(_ v: Double) -> Double { max(-4, min(4, v)) }
    for e in 0..<epochs {
        let alpha = 1.0 - Double(e) / Double(epochs)
        var g = Array(repeating: [0.0, 0.0], count: n)
        for i in 0..<n {
            for j in 0..<n where i != j {
                let dx = y[i][0] - y[j][0]
                let dy = y[i][1] - y[j][1]
                let d2 = dx * dx + dy * dy
                let w = sym[i][j]
                if w > 0 && d2 > 0 {
                    let c = -2 * umapA * umapB * pow(d2, umapB - 1) / (1 + umapA * pow(d2, umapB)) * w
                    g[i][0] += clip(c * dx)
                    g[i][1] += clip(c * dy)
                }
                let c = 2 * umapB / ((0.001 + d2) * (1 + umapA * pow(d2, umapB))) * (1 - w) * 0.1
                g[i][0] += clip(c * dx)
                g[i][1] += clip(c * dy)
            }
        }
        for i in 0..<n {
            y[i][0] += alpha * g[i][0] * 0.1
            y[i][1] += alpha * g[i][1] * 0.1
        }
        if snaps.contains(e + 1) { out[e + 1] = y }
    }
    return out
}

private func umapLab() -> DsLab {
    let ks = [4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0]
    return DsLab(control: .trackParam, param: DsParam(name: "Neighbours", symbol: "k", values: ks, initial: 3) { "\(Int($0.rounded()))" }) { s in
        let k = Int(ks[s.param].rounded())
        let x = clusters5
        let g = fuzzyGraph(x, k)
        let view = clusterPcaView
        let (vx, vy) = boundsOf(view, 0.6)
        let viewDots = view.indices.map { DsDot(p: view[$0], color: clusterColors[$0 / 15]) }
        let tight = 0, loose = 30
        let log2k = log(Double(k)) / log(2.0)
        let layouts = layoutUmap(g.sym, [0, 10, 50, 200])
        var edges: [(Int, Int)] = []
        for i in 0..<x.count { for j in (i + 1)..<x.count where g.sym[i][j] > 0 { edges.append((i, j)) } }
        func rowOf(_ name: String, _ i: Int, _ tone: StoryTone) -> DsRow {
            DsRow(cells: [name, num(g.rho[i]), num(g.sigma[i], 3), num(sum(g.neighbours[i]) { g.directed[i][$0] })], ink: tone)
        }
        let sigmaTable = DsBlock.table(header: ["point", "ρ", "σ", "Σw"], rows: [rowOf("tight A", tight, .path), rowOf("loose C", loose, .done)], weights: [1.4, 1, 1, 1])
        let legendLinks = clusterLegend() + [(linkGrey, .line, "Neighbour link")]
        func layoutFrame(_ epoch: Int, _ headline: String, _ body: String) -> DsFrame {
            let y = layouts[epoch]!
            let (xr, yr) = embeddingBounds(y)
            let ratio = spread(y, groupC) / spread(y, groupA)
            return DsFrame(
                headline: headline, body: body,
                blocks: [.plot(DsPlot(dots: embeddedDots(y), xr: xr, yr: yr, aspect: 1.9)), .formula(["attract ∝ 1 / (1 + a d²ᵇ),  a = 1.58, b = 0.90"])],
                legend: clusterLegend(),
                chips: [LabChip(key: "epoch", value: "\(epoch)"), LabChip(key: "C / A spread", value: num(ratio, 1) + "×", tint: .answer)]
            )
        }
        let final = layouts[200]!
        let finalBounds = embeddingBounds(final)
        let kLinks = (0..<x.count).flatMap { i in g.neighbours[i].map { j in DsSeg(a: view[i], b: view[j], color: linkGrey.opacity(0.55), width: 1) } }
        return [
            DsFrame(
                headline: "Each point links to its {k = \(k)} nearest neighbours in 5-D.",
                body: "Shown on a PCA view. The loose cluster's links are long, the tight clusters' short.",
                blocks: [.plot(DsPlot(dots: viewDots, xr: vx, yr: vy, segs: kLinks, aspect: 1.9))],
                legend: legendLinks,
                chips: [LabChip(key: "links", value: "\(x.count * k)")]
            ),
            DsFrame(
                headline: "A tight and a loose point both reach {Σw = \(num(log2k))}.",
                body: "ρ is subtracted first, so every point's nearest link weighs 1, however sparse its area.",
                blocks: [
                    .plot(DsPlot(
                        dots: viewDots + [tight, loose].map { DsDot(p: view[$0], color: .clear, r: 0, ring: SimColors.active, ringR: 7, ringWidth: 2) },
                        xr: vx, yr: vy,
                        segs: [tight, loose].flatMap { i in g.neighbours[i].map { j in DsSeg(a: view[i], b: view[j], color: SimColors.grey.opacity(0.8), width: 1) } },
                        aspect: 1.9
                    )),
                    sigmaTable,
                    .formula(["w = exp(−(d − ρ) / σ),  Σw = log₂ k = {\(num(log2k, log2k.truncatingRemainder(dividingBy: 1) == 0 ? 0 : 2))}"]),
                ],
                legend: clusterLegend() + [(SimColors.active, .ring, "Compared")]
            ),
            DsFrame(
                headline: "Merged both ways, the graph keeps {\(edges.count)} weighted edges.",
                body: "An edge counts if either end picked the other. This fuzzy graph is UMAP's picture of the 5-D data.",
                blocks: [
                    .plot(DsPlot(dots: viewDots, xr: vx, yr: vy, segs: edges.map { DsSeg(a: view[$0.0], b: view[$0.1], color: linkGrey.opacity(0.2 + 0.7 * g.sym[$0.0][$0.1]), width: 1) }, aspect: 1.9)),
                    .formula(["w = wᵢⱼ + wⱼᵢ − wᵢⱼ · wⱼᵢ"]),
                ],
                legend: legendLinks,
                chips: [LabChip(key: "edges", value: "\(edges.count)")]
            ),
            layoutFrame(0, "The layout starts from this view and runs {200 epochs}.", "Every edge pulls its two ends together; every other pair pushes apart."),
            layoutFrame(10, "After {10 epochs} each cluster pulls in around its own links.", "Attraction only acts along edges, and the three clusters share almost none."),
            layoutFrame(50, "After {50 epochs} the loose cluster has closed up.", "Its points were far apart in 5-D, but their graph edges weigh as much as anyone's."),
            layoutFrame(200, "After {200 epochs} the layout has settled.", "The step size decays to zero, so the last epochs only polish."),
            DsFrame(
                headline: "C is \(num(spread(x, groupC) / spread(x, groupA), 1))× wider than A in 5-D, but {\(num(spread(final, groupC) / spread(final, groupA), 1))×} in UMAP.",
                body: "Like t-SNE, UMAP keeps who neighbours whom, not how spread out each group is.",
                blocks: [.plot(DsPlot(dots: embeddedDots(final), xr: finalBounds.0, yr: finalBounds.1, aspect: 1.9)), spreadTable("UMAP map", spread(final, groupA), spread(final, groupC))],
                legend: clusterLegend()
            ),
        ]
    }
}

// MARK: - LLE

private struct Spiral { let pts: [DsP]; let theta: [Double] }

private let spiral: Spiral = {
    var r = DsRng(3)
    let n = 80
    var pts: [DsP] = [], th: [Double] = []
    for i in 0..<n {
        let t = 1.2 + 2 * Double.pi * 1.5 * (Double(i) / (Double(n) - 1.0))
        let rad = 0.35 * t
        let x = rad * cos(t) + r.g() * 0.01
        let y = 0.62 * rad * sin(t) + r.g() * 0.01
        // Mirrored so the arms cross at the lower right.
        pts.append(DsP(-x, y))
        th.append(t)
    }
    return Spiral(pts: pts, theta: th)
}()

private func knn2(_ pts: [DsP], _ k: Int) -> [[Int]] {
    pts.indices.map { i in
        Array(pts.indices.filter { $0 != i }.sorted {
            (sq(pts[i].x - pts[$0].x) + sq(pts[i].y - pts[$0].y), $0) < (sq(pts[i].x - pts[$1].x) + sq(pts[i].y - pts[$1].y), $1)
        }.prefix(k))
    }
}

private struct Link: Hashable { let a: Int; let b: Int; init(_ i: Int, _ j: Int) { a = min(i, j); b = max(i, j) } }

/// Neighbour links that join two different arms of the spiral.
private func armJumps(_ k: Int) -> Set<Link> {
    let pts = spiral.pts, th = spiral.theta
    let nb = knn2(pts, k)
    var out = Set<Link>()
    for i in pts.indices { for j in nb[i] where abs(th[i] - th[j]) > Double.pi { out.insert(Link(i, j)) } }
    return out
}

private func solveLinear(_ a: [[Double]], _ b: [Double]) -> [Double] {
    let n = b.count
    var m = (0..<n).map { a[$0] + [b[$0]] }
    for c in 0..<n {
        let p = firstMax(c..<n) { abs(m[$0][c]) }
        m.swapAt(c, p)
        for r in 0..<n where r != c {
            let f = m[r][c] / m[c][c]
            for k in c...n { m[r][k] -= f * m[c][k] }
        }
    }
    return (0..<n).map { m[$0][n] / m[$0][$0] }
}

/// One-dimensional LLE coordinates of the spiral.
private func lleCoords(_ k: Int) -> [Double] {
    let pts = spiral.pts
    let n = pts.count
    let nb = knn2(pts, k)
    var w = Array(repeating: Array(repeating: 0.0, count: n), count: n)
    for i in 0..<n {
        let z = nb[i].map { DsP(pts[$0].x - pts[i].x, pts[$0].y - pts[i].y) }
        var c = (0..<k).map { a in (0..<k).map { b in z[a].x * z[b].x + z[a].y * z[b].y } }
        let tr = sum(0..<k) { c[$0][$0] }
        let reg = tr > 0 ? 1e-3 * tr : 1e-3
        for a in 0..<k { c[a][a] += reg }
        let sol = solveLinear(c, Array(repeating: 1.0, count: k))
        let s = sum(sol) { $0 }
        for (a, j) in nb[i].enumerated() { w[i][j] = sol[a] / s }
    }
    let iw = (0..<n).map { i in (0..<n).map { j in (i == j ? 1.0 : 0.0) - w[i][j] } }
    let m = (0..<n).map { i in (0..<n).map { j in sum(0..<n) { iw[$0][i] * iw[$0][j] } } }
    let e = jacobiEigen(m)
    return e.vectors[n - 2]
}

private func spearman(_ a: [Double]) -> Double {
    let n = a.count
    let order = a.indices.sorted { (a[$0], $0) < (a[$1], $1) }
    var rank = Array(repeating: 0, count: n)
    for (r, i) in order.enumerated() { rank[i] = r }
    let m = Double(n - 1) / 2
    return abs(sum(0..<n) { (Double(rank[$0]) - m) * (Double($0) - m) } / sum(0..<n) { sq(Double($0) - m) })
}

private func lleLab() -> DsLab {
    let ks = [4.0, 6.0, 8.0, 10.0, 12.0, 14.0]
    let jumps = ks.map { armJumps(Int($0.rounded())) }
    let coords = DsCache<Int, [Double]>()
    return DsLab(
        control: .button,
        param: DsParam(name: "Neighbours", symbol: "k", values: ks, initial: 1) { "\(Int($0.rounded()))" },
        button: { $0.flag == 0 ? "Unroll" : "Show Neighbours" },
        onButton: { var s = $0; s.flag = 1 - s.flag; return s }
    ) { s in
        let k = Int(ks[s.param].rounded())
        let pts = spiral.pts
        let count = jumps[s.param].count
        let cells = DsBlock.cells(ks.indices.map { i in
            let c = jumps[i].count
            return DsCell(
                label: "k \(Int(ks[i].rounded()))", value: "\(c)",
                fill: i == s.param ? (c > 0 ? .warn : .done) : nil,
                ink: c == 0 ? .done : i == s.param ? .warn : nil
            )
        })
        let (xr, yr) = boundsOf(pts, 0.15)
        if s.flag == 0 {
            let nb = knn2(pts, k)
            let jump = jumps[s.param]
            var links = Set<Link>()
            for i in pts.indices { for j in nb[i] { links.insert(Link(i, j)) } }
            // Sorted so the draw order is stable between launches.
            let plain = links.subtracting(jump).sorted { ($0.a, $0.b) < ($1.a, $1.b) }
            let segs = plain.map { DsSeg(a: pts[$0.a], b: pts[$0.b], color: linkGrey, width: 1.5) } + jump.map { DsSeg(a: pts[$0.a], b: pts[$0.b], color: SimColors.red, width: 1.5) }
            return [
                DsFrame(
                    headline: count == 0 ? "At {k = \(k)}, every link stays on its own arm." : "At {k = \(k)}, \(count) \(count == 1 ? "link jumps" : "links jump") between arms.",
                    body: count == 0 ? "Each point is rebuilt from \(k) neighbours on the same stretch of curve, so LLE can unroll it."
                        : "LLE assumes each neighbourhood is flat. These links make two arms one patch, so the unrolled line folds.",
                    blocks: [.plot(DsPlot(dots: pts.map { DsDot(p: $0, color: dBlue, r: 2.8) }, xr: xr, yr: yr, segs: segs, aspect: 1.6)), cells],
                    legend: [(dBlue, .dot, "Spiral"), (linkGrey, .line, "Neighbour link"), (SimColors.red, .line, "Jumps an arm")]
                ),
            ]
        }
        let y = coords.get(k) { lleCoords(k) }
        let order = spearman(y)
        let top = y.map { abs($0) }.max()!
        let good = order > 0.99
        return [
            DsFrame(
                headline: good ? "Unrolled, the spiral keeps its order: {m:\(num(order))}." : "Unrolled, the line {w:folds}: order kept only \(num(order)).",
                body: good ? "Each point keeps the weights that rebuilt it from its neighbours, now in one dimension."
                    : "The cross-arm links tie distant stretches of the curve together, so they land side by side.",
                blocks: [
                    .plot(DsPlot(dots: y.indices.map { DsDot(p: DsP(Double($0), y[$0] / top), color: dBlue, r: 2.8) }, xr: (-3, Double(pts.count) + 2), yr: (-1.3, 1.3), aspect: 2.4, uniform: false)),
                    .formula(["→ position along the spiral,  ↑ LLE coordinate"]),
                    cells,
                ],
                legend: [(dBlue, .dot, "Spiral point")],
                chips: [good ? LabChip(key: "order kept", value: num(order), good: true) : LabChip(key: "order kept", value: num(order), tint: .warn)]
            ),
        ]
    }
}

private func dimLab(_ topicId: String) -> DsLab {
    switch topicId {
    case "svd": return svdLab()
    case "incremental_pca": return incrementalLab()
    case "kernel_pca": return kernelLab()
    case "ica": return icaLab()
    case "factor_analysis": return faLab()
    case "tsne": return tsneLab()
    case "umap": return umapLab()
    case "lle": return lleLab()
    default: return pcaLab()
    }
}

// MARK: - Lab

struct DimStoryLab: View {
    private let lab: DsLab

    init(topicId: String) { lab = dimLab(topicId) }

    var body: some View {
        if lab.control == .track { DsTrackLab(lab: lab) } else { DsInteractiveLab(lab: lab) }
    }
}

private struct DsTrackLab: View {
    let lab: DsLab
    @State private var frames: [DsFrame]
    @State private var playback: PlaybackState

    init(lab: DsLab) {
        self.lab = lab
        let frames = lab.frames(lab.initial)
        _frames = State(initialValue: frames)
        let state = PlaybackState(stepCount: frames.count, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        let step = UserDefaults.standard.integer(forKey: "openStep")
        if step > 0 { state.index = min(step, frames.count) - 1 }
        #endif
        _playback = State(initialValue: state)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            DsBody(frame: frames[min(playback.index, frames.count - 1)])
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) },
                              action: { frames[min(max($0, 0), frames.count - 1)].action })
        }
    }
}

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class DsModel {
    let lab: DsLab
    let playback: PlaybackState
    var state: DsState {
        didSet {
            if state.tab != oldValue.tab || state.param != oldValue.param || state.flag != oldValue.flag { frames = lab.frames(state) }
        }
    }
    private(set) var frames: [DsFrame]

    init(lab: DsLab) {
        self.lab = lab
        state = lab.initial
        let frames = lab.frames(lab.initial)
        self.frames = frames
        playback = PlaybackState(stepCount: frames.count, speedMs: 1000)
        #if DEBUG
        let step = UserDefaults.standard.integer(forKey: "openStep")
        if step > 0 && lab.control == .trackParam { playback.index = min(step, frames.count) - 1 }
        #endif
    }

    var index: Int { min(lab.control == .trackParam ? playback.index : state.index, frames.count - 1) }

    func reset() {
        state = lab.initial
        playback.reset()
    }
}

private struct DsInteractiveLab: View {
    @State private var model: DsModel
    @Environment(\.labDock) private var dock

    init(lab: DsLab) { _model = State(initialValue: DsModel(lab: lab)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            DsBody(frame: model.frames[model.index])
            if dock == nil {
                Divider().padding(.top, 16)
                DsControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(DsControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.reset() }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct DsControls: View {
    let model: DsModel

    var body: some View {
        let lab = model.lab
        let s = model.state
        let index = model.index
        VStack(spacing: 14) {
            switch lab.control {
            case .trackParam:
                LabTransportBar(state: model.playback, captions: model.frames.map { storyPlain($0.headline) }, footer: { AnyView(DsStepper(model: model)) })
            case .steps:
                LabSegments(labels: lab.tabs, selected: Binding(get: { model.state.tab }, set: { model.state.tab = $0; model.state.index = 0 }))
                LabBackActionRow(action: model.frames[index].action, backEnabled: index > 0,
                                 onBack: { model.state.index = index - 1 },
                                 onAction: { model.state.index = index >= model.frames.count - 1 ? 0 : index + 1 })
            default:
                DsStepper(model: model)
                if let label = lab.button {
                    LabButton(label: label(s), primary: true) { model.state = lab.onButton(model.state) }
                }
            }
        }
    }
}

private struct DsStepper: View {
    let model: DsModel

    var body: some View {
        if let p = model.lab.param {
            let s = model.state
            LabParamStepper(param: LabParam(tab: p.name, name: p.name, symbol: p.symbol, text: p.format(p.values[s.param]),
                                            canDecrease: s.param > 0, canIncrease: s.param < p.values.count - 1)) { d in
                model.state.param = min(max(model.state.param + d, 0), p.values.count - 1)
                model.state.index = 0
            }
        }
    }
}

private struct DsBody: View {
    let frame: DsFrame

    var body: some View {
        LabCard {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(frame.blocks.indices, id: \.self) { i in
                    switch frame.blocks[i] {
                    case .plot(let plot): DsPlotView(plot: plot)
                    case .strips(let strips): DsStripsView(strips: strips)
                    case let .curve(caption, ys, marker, label): DsCurveView(caption: caption, ys: ys, marker: marker, label: label)
                    case .cells(let cells): DsCellsView(cells: cells)
                    case let .table(header, rows, weights): DsTableView(header: header, rows: rows, weights: weights)
                    case .formula(let lines): DsFormulaView(lines: lines)
                    case .loadings(let rows): DsLoadingsView(rows: rows)
                    }
                }
                if !frame.legend.isEmpty { StoryLegendRow(items: frame.legend).padding(.top, 2) }
            }
        }
        if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
        LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
    }
}

// MARK: - Rendering

private struct DsStage: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private func circlePath(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private struct DsFormulaView: View {
    let lines: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 2) {
            ForEach(lines.indices, id: \.self) { i in
                storyText(lines[i], palette).font(AppFont.mono(13)).multilineTextAlignment(.center)
            }
        }
        .foregroundStyle(palette.onSurface.opacity(0.85))
        .lineSpacing(3)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10).padding(.horizontal, 12)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct DsPlotView: View {
    let plot: DsPlot

    var body: some View {
        Canvas { ctx, size in
            let pad: CGFloat = 14
            let (xLo, xHi) = plot.xr, (yLo, yHi) = plot.yr
            let sx = (size.width - 2 * pad) / CGFloat(xHi - xLo)
            let sy = (size.height - 2 * pad) / CGFloat(yHi - yLo)
            let s = min(sx, sy)
            let kx = plot.uniform ? s : sx, ky = plot.uniform ? s : sy
            let ox = size.width / 2 - CGFloat((xLo + xHi) / 2) * kx
            let oy = size.height / 2 + CGFloat((yLo + yHi) / 2) * ky
            func at(_ p: DsP) -> CGPoint { CGPoint(x: ox + CGFloat(p.x) * kx, y: oy - CGFloat(p.y) * ky) }
            for sg in plot.segs {
                var path = Path(); path.move(to: at(sg.a)); path.addLine(to: at(sg.b))
                ctx.stroke(path, with: .color(sg.color), style: StrokeStyle(lineWidth: sg.width, lineCap: sg.dashed ? .butt : .round, dash: sg.dashed ? [5, 4] : []))
            }
            for d in plot.dots {
                let c = at(d.p)
                if d.r > 0 { ctx.fill(circlePath(c, d.r), with: .color(d.color)) }
                if let ring = d.ring { ctx.stroke(circlePath(c, d.r + d.ringR), with: .color(ring), lineWidth: d.ringWidth) }
            }
        }
        .aspectRatio(plot.aspect, contentMode: .fit)
        .modifier(DsStage())
    }
}

private struct DsStripsView: View {
    let strips: [DsStrip]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(strips.indices, id: \.self) { s in
                let strip = strips[s]
                Text(strip.label).font(AppFont.sans(12)).foregroundStyle(palette.muted).lineLimit(1)
                Canvas { ctx, size in
                    ctx.fill(Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: size.height / 2), with: .color(SimColors.tint))
                    let r: CGFloat = 3.5
                    for (i, v) in strip.values.enumerated() {
                        ctx.fill(circlePath(CGPoint(x: r + 2 + CGFloat((v + 1) / 2) * (size.width - 2 * r - 4), y: size.height / 2), r), with: .color(strip.colors[i]))
                    }
                }
                .frame(height: 16)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(DsStage())
    }
}

private struct DsCurveView: View {
    let caption: String
    let ys: [Double]
    let marker: Int
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        ZStack(alignment: .topLeading) {
            Text(caption).font(AppFont.sans(12)).foregroundStyle(palette.muted).lineLimit(1).padding(.leading, 12).padding(.top, 6)
            Canvas { ctx, size in
                let lo = ys.min()!, hi = ys.max()!
                // The top 16pt stay clear for the marker's label.
                let top: CGFloat = 16
                func at(_ i: Int, _ v: Double) -> CGPoint {
                    CGPoint(x: CGFloat(i) / CGFloat(ys.count - 1) * size.width, y: size.height - CGFloat((v - lo) / max(hi - lo, 1e-9)) * (size.height - top))
                }
                var path = Path()
                for (i, v) in ys.enumerated() { if i == 0 { path.move(to: at(i, v)) } else { path.addLine(to: at(i, v)) } }
                ctx.stroke(path, with: .color(dViolet), lineWidth: 2)
                let mx = at(marker, 0).x
                var line = Path(); line.move(to: CGPoint(x: mx, y: 0)); line.addLine(to: CGPoint(x: mx, y: size.height))
                ctx.stroke(line, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                ctx.draw(Text(label).font(AppFont.mono(12)).foregroundStyle(SimColors.active), at: CGPoint(x: mx + 4, y: 0), anchor: .topLeading)
            }
            .padding(.leading, 16).padding(.trailing, 16).padding(.top, 26).padding(.bottom, 8)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 84)
        .modifier(DsStage())
    }
}

private struct DsCellsView: View {
    let cells: [DsCell]
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 6) {
            ForEach(cells.indices, id: \.self) { i in
                let cell = cells[i]
                VStack(spacing: 2) {
                    Text(cell.label).font(AppFont.sans(12)).foregroundStyle(palette.muted).lineLimit(1).minimumScaleFactor(0.8)
                    Text(cell.value).font(AppFont.mono(15, .bold)).lineLimit(1).minimumScaleFactor(0.8)
                        .foregroundStyle(cell.muted ? palette.muted : cell.ink?.ink(palette) ?? palette.onSurface)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 56)
                .background(cell.fill.map { $0.color.opacity(0.22) } ?? SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

private struct DsTableView: View {
    let header: [String]
    let rows: [DsRow]
    let weights: [CGFloat]
    @Environment(\.palette) private var palette

    var body: some View {
        let total = weights.reduce(0, +)
        GeometryReader { geo in
            let inner = geo.size.width - 24
            VStack(spacing: 4) {
                HStack(spacing: 0) {
                    ForEach(header.indices, id: \.self) { i in
                        Text(header[i]).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1)
                            .frame(width: inner * weights[i] / total, alignment: i == 0 ? .leading : .trailing)
                    }
                }
                .padding(.horizontal, 12)
                ForEach(rows.indices, id: \.self) { r in
                    let row = rows[r]
                    let ink = row.muted ? palette.muted : row.ink?.ink(palette) ?? palette.onSurface
                    HStack(spacing: 0) {
                        ForEach(row.cells.indices, id: \.self) { i in
                            Text(row.cells[i])
                                .font(i == 0 ? AppFont.sans(15, .semibold) : AppFont.mono(15))
                                .foregroundStyle(ink)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                                .frame(width: inner * weights[i] / total, alignment: i == 0 ? .leading : .trailing)
                        }
                    }
                    .padding(.horizontal, 12)
                    .frame(height: 40)
                    .background(fill(row.fill), in: RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .frame(height: CGFloat(rows.count) * 44 + 18)
    }

    private func fill(_ tone: StoryTone?) -> Color {
        guard let tone else { return .clear }
        return tone == .active ? SimColors.active.opacity(0.16) : tone.color.opacity(0.18)
    }
}

private struct DsLoadingsView: View {
    let rows: [DsLoad]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 8) {
            ForEach(rows.indices, id: \.self) { i in
                let row = rows[i]
                HStack(alignment: .top, spacing: 0) {
                    Text(row.name).font(AppFont.sans(15, .semibold)).foregroundStyle(row.highlight ? SimColors.active : palette.onSurface)
                        .frame(width: 36, alignment: .leading).padding(.top, 1)
                    VStack(spacing: 3) {
                        GeometryReader { geo in
                            let shared = CGFloat(min(max((row.lambda ?? 0) * (row.lambda ?? 0), 0), 1))
                            ZStack(alignment: .leading) {
                                Rectangle().fill(row.highlight && row.lambda != nil ? SimColors.active.opacity(0.5) : SimColors.grey.opacity(0.3))
                                Rectangle().fill(dViolet).frame(width: geo.size.width * shared)
                            }
                        }
                        .frame(height: 18)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        HStack {
                            Text(row.lambda.map { "λ \(num($0))" } ?? "λ ?")
                            Spacer()
                            Text(row.lambda.map { "ψ \(num(1 - $0 * $0))" } ?? "ψ ?")
                        }
                        .font(AppFont.mono(12)).foregroundStyle(palette.muted)
                    }
                }
            }
        }
    }
}
