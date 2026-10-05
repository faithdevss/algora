import SwiftUI

// Port of ClusterStoryLabs.kt: k-means, k-medians, k-modes, agglomerative and divisive hierarchies,
// DBSCAN, HDBSCAN, OPTICS, mean shift, BIRCH, affinity propagation, spectral clustering and GMM. Each is
// one card (a scatter, bars, a dendrogram, a tree or a table), the arithmetic of the current step, chips
// and a headline, then a stepper with a back-and-action row, a button, or a step track. Every number is
// computed from the fixed, seeded data below.

let clusterStoryTopicIds: Set<String> = [
    "kmeans", "k_medians", "k_modes", "hierarchical_clustering", "hierarchical_divisive", "dbscan", "hdbscan",
    "optics", "mean_shift", "birch", "affinity_propagation", "spectral_clustering", "gmm",
]

private let cBlue = Color(hex: 0x4F7FE0)
private let cOrange = Color(hex: 0xF08A3C)
private let cGreen = Color(hex: 0x3F9A62)
private let clusterPalette = [cBlue, cOrange, cGreen, Color(hex: 0x8B5CF6), Color(hex: 0xD6457A)]
private let noiseGrey = Color(hex: 0x6B7280)
private let idleDot = Color(hex: 0xCBD0DA)
private let unvisited = Color(hex: 0x4B5160)

private func cColor(_ label: Int) -> Color { label < 0 ? noiseGrey : clusterPalette[label % clusterPalette.count] }

// MARK: - Formatting and small math

private func cx(_ v: Double, _ d: Int = 2) -> String {
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

private func ladderOf(_ from: Double, _ to: Double, _ step: Double) -> [Double] {
    (0...Int(((to - from) / step).rounded())).map { from + Double($0) * step }
}

private struct CsRng {
    var random: KotlinRandom
    init(_ seed: Int32) { random = KotlinRandom(seed) }
    mutating func u() -> Double { random.nextDouble() }
    mutating func normal() -> Double {
        let a = max(random.nextDouble(), 1e-12)
        let b = random.nextDouble()
        return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b)
    }
}

private struct CsP { let x: Double; let y: Double; init(_ x: Double, _ y: Double) { self.x = x; self.y = y } }

private func d2(_ a: CsP, _ b: CsP) -> Double { (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) }
private func dist(_ a: CsP, _ b: CsP) -> Double { d2(a, b).squareRoot() }

private func blob(_ r: inout CsRng, _ n: Int, _ cx: Double, _ cy: Double, _ sx: Double, _ sy: Double) -> [CsP] {
    (0..<n).map { _ in
        let x = cx + r.normal() * sx
        let y = cy + r.normal() * sy
        return CsP(x, y)
    }
}

private func meanOf(_ pts: [CsP]) -> CsP { CsP(pts.reduce(0) { $0 + $1.x } / Double(pts.count), pts.reduce(0) { $0 + $1.y } / Double(pts.count)) }

private func medianOf(_ v: [Double]) -> Double {
    let s = v.sorted()
    return s.count % 2 == 1 ? s[s.count / 2] : (s[s.count / 2 - 1] + s[s.count / 2]) / 2
}

private func boundsOf(_ pts: [CsP], _ pad: Double) -> ((Double, Double), (Double, Double)) {
    ((pts.map(\.x).min()! - pad, pts.map(\.x).max()! + pad), (pts.map(\.y).min()! - pad, pts.map(\.y).max()! + pad))
}

private func argmin<T>(_ xs: [T], _ key: (T) -> Double) -> T { xs.min { key($0) < key($1) }! }
private func argmax<T>(_ xs: [T], _ key: (T) -> Double) -> T { xs.max { key($0) < key($1) }! }

// MARK: - Model

private struct CsDot {
    let p: CsP
    let color: Color
    var r: CGFloat = 3.5
    var ring: Color? = nil
    var ringR: CGFloat = 0
    var ringWidth: CGFloat = 2
    var ringDashed = false
    var top = false
}

private struct CsSeg { let a: CsP; let b: CsP; let color: Color; var dashed = false; var width: CGFloat = 1.5 }

private struct CsCircle { let c: CsP; let radius: Double; let color: Color; var dashed = true; var fill: Color? = nil; var width: CGFloat = 1.5 }

private struct CsEllipse { let c: CsP; let a: Double; let b: Double; let angle: Double; let color: Color }

private struct CsLabel { let p: CsP; let text: String }

private struct CsPlot {
    let dots: [CsDot]
    let xr: (Double, Double)
    let yr: (Double, Double)
    var segs: [CsSeg] = []
    var circles: [CsCircle] = []
    var ellipses: [CsEllipse] = []
    var labels: [CsLabel] = []
    var aspect: CGFloat = 1.75
    /// Equal scale on both axes, so circles stay round.
    var uniform = true
    var baseline: Double? = nil
}

private struct CsRow { let cells: [String]; var ink: StoryTone? = nil; var fill: StoryTone? = nil; var muted = false }

private enum CsLinkState { case merged, now, later }

private struct CsMerge { let a: Int; let b: Int; let height: Double; let state: CsLinkState }

private struct CsNode { let full: String; let short: String; let parent: Int; let tone: StoryTone; let dashed: Bool }

private enum CsRowState { case done, current, future }

private struct CsModeRow { let name: String; let cells: [String]; let state: CsRowState; let mismatch: Set<Int>; let score: String }

private enum CsBlock {
    case plot(CsPlot)
    case bars(values: [Double], colors: [Color], cut: Double?, caption: String?)
    case table(header: [String], rows: [CsRow], weights: [CGFloat])
    case formula([String])
    case dendro(leafCount: Int, names: [String], merges: [CsMerge])
    case tree([CsNode])
    case modes(header: [String], centres: [[String]], changed: Set<Int>, rows: [CsModeRow])
}

private struct CsFrame {
    let headline: String
    let body: String
    let blocks: [CsBlock]
    var legend: [(Color, SwatchStyle, String)] = []
    var chips: [LabChip] = []
    var action = ""
}

private struct CsParam { let name: String; let symbol: String; let values: [Double]; let initial: Int; let format: (Double) -> String }

private struct CsState: Equatable { var tab = 0; var param = 0; var flag = 0; var index = 0 }

private enum CsControl { case track, steps, button }

private struct CsLab {
    let control: CsControl
    var tabs: [String] = []
    var startTab = 0
    var param: CsParam? = nil
    var startFlag = 0
    var button: ((CsState) -> String)? = nil
    var onButton: (CsState) -> CsState = { $0 }
    let frames: (CsState) -> [CsFrame]

    var initial: CsState { CsState(tab: startTab, param: param?.initial ?? 0, flag: startFlag, index: 0) }
}

private func clusterLegend(_ k: Int) -> [(Color, SwatchStyle, String)] { (0..<k).map { (cColor($0), .dot, "Cluster \($0 + 1)") } }

private func plural(_ n: Int, _ one: String, _ many: String) -> String { n == 1 ? one : many }

// MARK: - K-means

private let kmeansData: [CsP] = {
    var r = CsRng(71)
    return blob(&r, 7, 1.6, 2.0, 0.55, 0.5) + blob(&r, 10, 4.2, 4.6, 0.5, 0.45) + blob(&r, 13, 6.3, 2.2, 0.8, 0.5)
}()

private let kmeansStarts = [CsP(1.2, 1.4), CsP(2.4, 2.8), CsP(3.2, 1.3), CsP(0.8, 3.0), CsP(2.8, 3.8)]

private func nearestIndex(_ p: CsP, _ centres: [CsP]) -> Int { argmin(Array(centres.indices)) { d2(p, centres[$0]) } }

private func inertia(_ pts: [CsP], _ assign: [Int], _ centres: [CsP]) -> Double { pts.indices.reduce(0.0) { $0 + d2(pts[$1], centres[assign[$1]]) } }

private func kmeansLab() -> CsLab {
    let ks = [2.0, 3.0, 4.0, 5.0]
    let pts = kmeansData
    let (xr, yr) = boundsOf(pts + kmeansStarts, 0.5)
    return CsLab(control: .steps, param: CsParam(name: "Clusters", symbol: "k", values: ks, initial: 1) { "\(Int($0.rounded()))" }) { s in
        let k = Int(ks[s.param].rounded())
        var centres = Array(kmeansStarts.prefix(k))
        var frames: [CsFrame] = []
        func spokes(_ assign: [Int], _ cs: [CsP]) -> [CsSeg] { pts.indices.map { CsSeg(a: pts[$0], b: cs[assign[$0]], color: cColor(assign[$0]).opacity(0.45), width: 1) } }
        func rings(_ cs: [CsP]) -> [CsDot] { cs.indices.map { CsDot(p: cs[$0], color: .clear, r: 0, ring: cColor($0), ringR: 9, ringWidth: 3) } }
        func sizes(_ assign: [Int]) -> String { (0..<k).map { c in "\(assign.filter { $0 == c }.count)" }.joined(separator: " / ") }
        frames.append(CsFrame(
            headline: "k = \(k) centres start at {arbitrary spots}.",
            body: "k-means alternates two moves: assign each point to its nearest centre, then move each centre to its points' mean.",
            blocks: [.plot(CsPlot(dots: pts.map { CsDot(p: $0, color: idleDot) } + rings(centres), xr: xr, yr: yr)), .formula(["minimise inertia = Σ ‖x − centre‖²"])],
            legend: clusterLegend(k),
            chips: [LabChip(key: "iteration", value: "0")],
            action: "Assign Points"
        ))
        var previous: [Int]? = nil
        var round = 0
        while round < 12 {
            let assign = pts.map { nearestIndex($0, centres) }
            if let previous, previous == assign {
                let dots = pts.indices.map { CsDot(p: pts[$0], color: cColor(assign[$0])) } + rings(centres)
                frames.append(CsFrame(
                    headline: "No point changed cluster: k-means has {converged} after \(round) rounds.",
                    body: "Inertia \(cx(inertia(pts, assign, centres), 1)). A different start can land in a worse minimum; k-means++ spreads the starts out.",
                    blocks: [.plot(CsPlot(dots: dots, xr: xr, yr: yr, segs: spokes(assign, centres))), .formula(["inertia {v:\(cx(inertia(pts, assign, centres), 1))}, no reassignments"])],
                    legend: clusterLegend(k),
                    chips: [LabChip(key: "iteration", value: "\(round)"), LabChip(key: "sizes", value: sizes(assign))],
                    action: "Start Over"
                ))
                break
            }
            round += 1
            let before = inertia(pts, assign, centres)
            frames.append(CsFrame(
                headline: "Each point joins its {nearest centre}.",
                body: "Inertia, the summed squared distance to the centres, is now \(cx(before, 1)).",
                blocks: [
                    .plot(CsPlot(dots: pts.indices.map { CsDot(p: pts[$0], color: cColor(assign[$0])) } + rings(centres), xr: xr, yr: yr, segs: spokes(assign, centres))),
                    .formula(["inertia {v:\(cx(before, 1))} with \(sizes(assign)) points"]),
                ],
                legend: clusterLegend(k),
                chips: [LabChip(key: "iteration", value: "\(round)"), LabChip(key: "sizes", value: sizes(assign))],
                action: "Update Centres"
            ))
            let moved = centres.indices.map { c -> CsP in
                let m = pts.indices.filter { assign[$0] == c }
                return m.isEmpty ? centres[c] : meanOf(m.map { pts[$0] })
            }
            let after = inertia(pts, assign, moved)
            let next = pts.map { nearestIndex($0, moved) }
            let largest = argmax(Array(centres.indices)) { dist(centres[$0], moved[$0]) }
            let stable = next == assign
            frames.append(CsFrame(
                headline: "Each centre {jumps to the mean} of the points assigned to it.",
                body: stable ? "Next, no point will change its nearest centre, so the loop is done."
                    : "Next, points re-pick their nearest centre; inertia falls again to \(cx(inertia(pts, next, moved), 1)).",
                blocks: [
                    .plot(CsPlot(
                        dots: pts.indices.map { CsDot(p: pts[$0], color: cColor(assign[$0])) }
                            + centres.indices.map { CsDot(p: centres[$0], color: .clear, r: 0, ring: cColor($0), ringR: 7, ringDashed: true) }
                            + rings(moved),
                        xr: xr, yr: yr,
                        segs: spokes(assign, moved) + centres.indices.map { CsSeg(a: centres[$0], b: moved[$0], color: SimColors.active, width: 2.5) }
                    )),
                    .formula(["inertia \(cx(before, 1)) → {v:\(cx(after, 1))} after this update", "largest move: centre \(largest + 1), \(cx(dist(centres[largest], moved[largest])))"]),
                ],
                legend: [(SimColors.grey, .ring, "Old centre"), (SimColors.active, .line, "Move")] + clusterLegend(k),
                chips: [LabChip(key: "iteration", value: "\(round)"), LabChip(key: "sizes", value: sizes(assign))],
                action: "Assign Points"
            ))
            centres = moved
            previous = assign
        }
        return frames
    }
}

// MARK: - K-medians

private let mediansData: ([CsP], [CsP]) = {
    var r = CsRng(73)
    let base = blob(&r, 8, 2.5, 3.0, 0.35, 0.3) + blob(&r, 7, 6.0, 5.8, 0.45, 0.3)
    let outliers = [CsP(5.6, 1.0), CsP(6.0, 1.3), CsP(6.3, 0.8), CsP(5.9, 0.6), CsP(6.5, 1.1)]
    return (base, outliers)
}()

/// Lloyd's loop from the given starts, then farthest points: means with squared error, or medians with absolute error.
private func lloyd(_ pts: [CsP], _ k: Int, _ medians: Bool, _ starts: [Int] = [0]) -> ([Int], [CsP]) {
    var centres = starts.prefix(k).map { pts[$0] }
    while centres.count < k { centres.append(argmax(pts) { p in centres.map { d2(p, $0) }.min()! }) }
    var assign = Array(repeating: 0, count: pts.count)
    for _ in 0..<30 {
        for (i, p) in pts.enumerated() {
            assign[i] = argmin(Array(0..<k)) { c in medians ? abs(p.x - centres[c].x) + abs(p.y - centres[c].y) : d2(p, centres[c]) }
        }
        for c in 0..<k {
            let m = pts.indices.filter { assign[$0] == c }.map { pts[$0] }
            if !m.isEmpty { centres[c] = medians ? CsP(medianOf(m.map(\.x)), medianOf(m.map(\.y))) : meanOf(m) }
        }
    }
    return (assign, centres)
}

private func kMediansLab() -> CsLab {
    let ks = [2.0, 3.0, 4.0]
    let (base, extra) = mediansData
    return CsLab(
        control: .button,
        tabs: ["K-means", "K-medians"],
        startTab: 1,
        param: CsParam(name: "Clusters", symbol: "k", values: ks, initial: 0) { "\(Int($0.rounded()))" },
        startFlag: 2,
        button: { _ in "Add Outlier" },
        onButton: { var s = $0; s.flag = (s.flag + 1) % extra.count; return s }
    ) { s in
        let k = Int(ks[s.param].rounded())
        let n = s.flag + 1
        let pts = base + extra.prefix(n)
        let medians = s.tab == 1
        let (assign, centres) = lloyd(pts, k, medians, [0, 8])
        let outlierIdx = Array(base.count..<pts.count)
        var counts: [(Int, Int)] = []
        for i in outlierIdx {
            if let j = counts.firstIndex(where: { $0.0 == assign[i] }) { counts[j].1 += 1 } else { counts.append((assign[i], 1)) }
        }
        let home = counts.max { $0.1 < $1.1 }!.0
        let members = pts.indices.filter { assign[$0] == home }.map { pts[$0] }
        let regular = pts.indices.filter { assign[$0] == home && $0 < base.count }
        let mean = meanOf(members)
        let median = CsP(medianOf(members.map(\.x)), medianOf(members.map(\.y)))
        let own = regular.isEmpty
        let coreMean = regular.isEmpty ? mean : meanOf(regular.map { pts[$0] })
        let drag = dist(mean, coreMean)
        let (xr, yr) = boundsOf(base + extra, 0.5)
        let (shown, other) = medians ? (median, mean) : (mean, median)
        var dots = pts.indices.map { i in i >= base.count ? CsDot(p: pts[i], color: SimColors.active, r: 3.5) : CsDot(p: pts[i], color: cColor(assign[i])) }
        dots += centres.indices.map { CsDot(p: $0 == home ? shown : centres[$0], color: .clear, r: 0, ring: cColor($0), ringR: 9, ringWidth: 3) }
        if !own { dots.append(CsDot(p: other, color: .clear, r: 0, ring: SimColors.grey, ringR: 8, ringDashed: true)) }
        let word = ["One", "Two", "Three", "Four", "Five"][n - 1]
        let headline: String, body: String
        if own {
            headline = "With k = \(k) the outliers get {their own cluster}."
            body = "A spare centre is spent on the strays, so no real cluster is dragged; fewer clusters would force a choice."
        } else if medians {
            headline = "\(word) far \(n == 1 ? "point drags" : "points drag") the mean {\(cx(drag))} away; the median stays in the cluster."
            body = "A median moves with how many outliers there are, not how far away they sit."
        } else {
            headline = "The mean follows the \(word.lowercased()) far \(n == 1 ? "point" : "points") {\(cx(drag))} out of the cluster."
            body = "Squared error rewards chasing outliers; k-medians' absolute error doesn't care how far away they sit."
        }
        return [CsFrame(
            headline: headline,
            body: body,
            blocks: [
                .plot(CsPlot(dots: dots, xr: xr, yr: yr, segs: own ? [] : [CsSeg(a: mean, b: median, color: SimColors.grey, width: 1.2)])),
                .formula(["x: mean \(cx(mean.x)) median {v:\(cx(median.x))}", "y: mean \(cx(mean.y)) median {v:\(cx(median.y))}"]),
            ],
            legend: [
                (cBlue, .ring, medians ? "Median centre" : "Mean centre"),
                (SimColors.grey, .dashed, medians ? "Mean centre" : "Median centre"),
                (SimColors.active, .dot, "Outlier"),
            ]
        )]
    }
}

// MARK: - K-modes

private let modeHeader = ["colour", "size", "shape", "finish"]
private let modeRows = [
    ["red", "small", "round", "matte"],
    ["red", "small", "square", "matte"],
    ["red", "large", "round", "matte"],
    ["blue", "large", "square", "gloss"],
    ["blue", "large", "square", "matte"],
    ["blue", "large", "round", "gloss"],
    ["green", "large", "round", "gloss"],
    ["blue", "large", "round", "gloss"],
]
private let modeStarts = [["red", "small", "round", "matte"], ["blue", "large", "square", "gloss"]]

private func mismatches(_ a: [String], _ b: [String]) -> Int { a.indices.filter { a[$0] != b[$0] }.count }

private func kModesLab() -> CsLab {
    CsLab(control: .track) { _ in
        let assign = modeRows.map { mismatches($0, modeStarts[1]) < mismatches($0, modeStarts[0]) ? 1 : 0 }
        let legend: [(Color, SwatchStyle, String)] = [
            (SimColors.active, .fill, "Current row"),
            (Color(hex: 0xB4A2FF), .fill, "Centre modes"),
            (Color(hex: 0xF08A8A), .fill, "Mismatch (underlined)"),
        ]
        func rows(_ upTo: Int) -> [CsModeRow] {
            modeRows.indices.map { i in
                let c = assign[i]
                let state: CsRowState = i < upTo ? .done : i == upTo ? .current : .future
                let m = state == .current ? Set(modeRows[i].indices.filter { modeRows[i][$0] != modeStarts[c][$0] }) : []
                return CsModeRow(name: "\(i + 1)", cells: modeRows[i], state: state, mismatch: m,
                                 score: state == .future ? "—" : "\(mismatches(modeRows[i], modeStarts[0])) · \(mismatches(modeRows[i], modeStarts[1]))")
            }
        }
        let assignFrames = modeRows.indices.map { i -> CsFrame in
            let a = mismatches(modeRows[i], modeStarts[0]), b = mismatches(modeRows[i], modeStarts[1])
            let c = assign[i]
            let best = c == 0 ? a : b
            let headline: String
            if a == b { headline = "Row \(i + 1) ties at {\(a)} mismatches each, so it joins c1, the first centre." }
            else if best == 0 { headline = "Row \(i + 1) matches c\(c + 1) {exactly}, so it joins c\(c + 1)." }
            else { headline = "Row \(i + 1) differs from c\(c + 1) on only {\(best)} attribute\(best == 1 ? "" : "s"), so it joins c\(c + 1)." }
            return CsFrame(
                headline: headline,
                body: "No distances or means: k-modes counts mismatches and uses the most common value as the centre.",
                blocks: [.modes(header: modeHeader, centres: modeStarts, changed: [], rows: rows(i)), .formula(["row \(i + 1): \(a) mismatches to c1, \(b) to c2 → {v:c\(c + 1)}"])],
                legend: legend,
                action: i < modeRows.count - 1 ? "Assign Row \(i + 2)" : "Update Modes"
            )
        }
        let newCentres = (0...1).map { c -> [String] in
            let members = modeRows.indices.filter { assign[$0] == c }.map { modeRows[$0] }
            return modeHeader.indices.map { col in
                let values = members.map { $0[col] }
                var distinct: [String] = []
                for v in values where !distinct.contains(v) { distinct.append(v) }
                return distinct.max { a, b in values.filter { $0 == a }.count < values.filter { $0 == b }.count }!
            }
        }
        var changed = Set<Int>()
        for c in 0...1 { for col in modeHeader.indices where newCentres[c][col] != modeStarts[c][col] { changed.insert(c * 10 + col) } }
        let update = CsFrame(
            headline: "New centres take the {most common value} in each column of their rows.",
            body: changed.isEmpty ? "No mode changed, so no row will move: k-modes has converged."
                : "\(changed.count) centre value\(changed.count == 1 ? "" : "s") changed, so rows are reassigned and the loop repeats.",
            blocks: [
                .modes(header: modeHeader, centres: newCentres, changed: changed, rows: modeRows.indices.map {
                    CsModeRow(name: "\($0 + 1)", cells: modeRows[$0], state: .done, mismatch: [], score: "c\(assign[$0] + 1)")
                }),
                .formula(["c1 = \(newCentres[0].joined(separator: " "))", "c2 = \(newCentres[1].joined(separator: " "))"]),
            ],
            legend: [legend[1]],
            action: "Start Over"
        )
        return assignFrames + [update]
    }
}

// MARK: - Agglomerative

private let aggPoints = [CsP(1.0, 2.0), CsP(1.6, 2.35), CsP(3.2, 1.7), CsP(5.3, 2.05), CsP(6.0, 4.3), CsP(6.7, 4.45), CsP(7.1, 3.8), CsP(8.7, 1.9)]

private struct CsLinkStep { let a: [Int]; let b: [Int]; let height: Double; let pair: (Int, Int); let nodeA: Int; let nodeB: Int }

private func linkage(_ pts: [CsP], _ kind: Int) -> [CsLinkStep] {
    var clusters: [([Int], Int)] = pts.indices.map { ([$0], $0) }
    var steps: [CsLinkStep] = []
    var next = pts.count
    while clusters.count > 1 {
        var best = Double.greatestFiniteMagnitude
        var bi = 0, bj = 1
        var pair = (0, 0)
        for i in clusters.indices {
            for j in (i + 1)..<clusters.count {
                var ds: [(Int, Int, Double)] = []
                for a in clusters[i].0 { for b in clusters[j].0 { ds.append((a, b, dist(pts[a], pts[b]))) } }
                let d: Double, p: (Int, Int)
                switch kind {
                case 0: let m = argmin(ds) { $0.2 }; d = m.2; p = (m.0, m.1)
                case 1: let m = argmax(ds) { $0.2 }; d = m.2; p = (m.0, m.1)
                default: d = ds.reduce(0) { $0 + $1.2 } / Double(ds.count); let m = argmin(ds) { $0.2 }; p = (m.0, m.1)
                }
                if d < best - 1e-12 { best = d; bi = i; bj = j; pair = p }
            }
        }
        steps.append(CsLinkStep(a: clusters[bi].0, b: clusters[bj].0, height: best, pair: pair, nodeA: clusters[bi].1, nodeB: clusters[bj].1))
        let merged = ((clusters[bi].0 + clusters[bj].0).sorted(), next)
        next += 1
        clusters.remove(at: bj)
        clusters[bi] = merged
    }
    return steps
}

private func aggLab() -> CsLab {
    CsLab(control: .track, tabs: ["Single", "Complete", "Average"]) { s in
        let steps = linkage(aggPoints, s.tab)
        let (xr, yr) = boundsOf(aggPoints, 0.6)
        let word = ["single link: closest pair", "complete link: farthest pair", "average link: mean over pairs"][s.tab]
        return steps.indices.map { i in
            let st = steps[i]
            let now = Set(st.a + st.b)
            let dots = aggPoints.indices.map { CsDot(p: aggPoints[$0], color: now.contains($0) ? SimColors.active : idleDot, r: 4) }
            let segs = (0..<i).map { CsSeg(a: aggPoints[steps[$0].pair.0], b: aggPoints[steps[$0].pair.1], color: SimColors.answer, width: 2.5) }
                + [CsSeg(a: aggPoints[st.pair.0], b: aggPoints[st.pair.1], color: SimColors.active, dashed: true, width: 2)]
            func set(_ c: [Int]) -> String { c.map { "\($0 + 1)" }.joined(separator: ", ") }
            let pairText = s.tab == 2 ? "\(cx(st.height)) over \(st.a.count * st.b.count) pairs" : "\(st.pair.0 + 1)-\(st.pair.1 + 1) = {v:\(cx(st.height))}"
            let body: String
            if i == 0 { body = "Every point starts alone; the two closest clusters merge first." }
            else if i == steps.count - 1 { body = "All \(aggPoints.count) points are one cluster now. Cut the tree at any height to pick k." }
            else if s.tab == 0 { body = "Single link uses the closest pair, so clusters can chain into long shapes." }
            else if s.tab == 1 { body = "Complete link uses the farthest pair, so it prefers tight, round clusters." }
            else { body = "Average link uses the mean of all pairs, a middle ground between single and complete." }
            return CsFrame(
                headline: "Merge \(i + 1) joins {[\(set(st.a))]} and [\(set(st.b))] at \(cx(st.height)).",
                body: body,
                blocks: [
                    .plot(CsPlot(dots: dots, xr: xr, yr: yr, segs: segs, labels: aggPoints.indices.map { CsLabel(p: aggPoints[$0], text: "\($0 + 1)") }, aspect: 2.1)),
                    .dendro(leafCount: aggPoints.count, names: aggPoints.indices.map { "\($0 + 1)" }, merges: steps.indices.map { j in
                        CsMerge(a: steps[j].nodeA, b: steps[j].nodeB, height: steps[j].height, state: j < i ? .merged : j == i ? .now : .later)
                    }),
                    .formula([s.tab == 2 ? "\(word) = {v:\(pairText)}" : "\(word) \(pairText)"]),
                ],
                legend: [(SimColors.answer, .line, "Merged"), (SimColors.active, .dashedLine, "Merging now"), (SimColors.grey, .line, "Later")],
                action: i < steps.count - 1 ? "Merge Next Pair" : "Start Over"
            )
        }
    }
}

// MARK: - Divisive

private let divisiveData: [CsP] = {
    var r = CsRng(79)
    return blob(&r, 10, 0.2, 0.3, 0.05, 0.05) + blob(&r, 10, 0.46, 0.36, 0.1, 0.08) + blob(&r, 10, 0.82, 0.8, 0.04, 0.04)
}()

private func diameter(_ pts: [CsP], _ members: [Int]) -> Double {
    var best = 0.0
    for i in members.indices { for j in (i + 1)..<members.count { best = max(best, dist(pts[members[i]], pts[members[j]])) } }
    return best
}

/// 2-means from the farthest pair.
private func bisect(_ pts: [CsP], _ members: [Int]) -> ([Int], [Int]) {
    var a = members[0], b = members[1]
    var far = -1.0
    for i in members.indices {
        for j in (i + 1)..<members.count {
            let d = d2(pts[members[i]], pts[members[j]])
            if d > far { far = d; a = members[i]; b = members[j] }
        }
    }
    var ca = pts[a], cb = pts[b]
    var left = [a], right = [b]
    for _ in 0..<10 {
        left = members.filter { d2(pts[$0], ca) <= d2(pts[$0], cb) }
        right = members.filter { d2(pts[$0], ca) > d2(pts[$0], cb) }
        if !left.isEmpty { ca = meanOf(left.map { pts[$0] }) }
        if !right.isEmpty { cb = meanOf(right.map { pts[$0] }) }
    }
    return (left, right)
}

private func divisiveLab() -> CsLab {
    CsLab(control: .track) { _ in
        let pts = divisiveData
        struct N { let members: [Int]; let parent: Int; let diameter: Double }
        var nodes = [N(members: Array(pts.indices), parent: -1, diameter: diameter(pts, Array(pts.indices)))]
        var split: [Int] = []
        func label(_ n: N) -> String { "\(n.members.count) · ⌀ \(cx(n.diameter))" }
        func tree(_ splitting: Int?) -> CsBlock {
            .tree(nodes.indices.map { i in
                let n = nodes[i]
                let tone: StoryTone = (i == splitting || (splitting != nil && n.parent == splitting)) ? .active : split.contains(i) ? .done : .idle
                return CsNode(full: label(n), short: "\(n.members.count)", parent: n.parent, tone: tone, dashed: splitting != nil && n.parent == splitting)
            })
        }
        var frames = [CsFrame(
            headline: "All {\(pts.count)} points start as one cluster, diameter \(cx(nodes[0].diameter)).",
            body: "Divisive clustering works top-down: split a cluster in two, then pick the next leaf to split.",
            blocks: [tree(nil), .formula(["⌀ = largest distance inside a cluster = {v:\(cx(nodes[0].diameter))}"])],
            legend: [(SimColors.grey, .fill, "Open leaf")],
            action: "Split Largest"
        )]
        for round in 1...6 {
            let open = nodes.indices.filter { !split.contains($0) && nodes[$0].members.count > 1 }
            let pick = argmax(open) { nodes[$0].diameter }
            let listed = open.sortedByDescending { nodes[$0].diameter }
            let (l, r) = bisect(pts, nodes[pick].members)
            split.append(pick)
            nodes.append(N(members: l, parent: pick, diameter: diameter(pts, l)))
            nodes.append(N(members: r, parent: pick, diameter: diameter(pts, r)))
            frames.append(CsFrame(
                headline: "Split \(round) takes the leaf with the {largest diameter}, \(cx(nodes[pick].diameter)).",
                body: round == 1 ? "Each split is a 2-means bisection of the chosen leaf, seeded from its two farthest points."
                    : "Divisive works top-down: it keeps splitting the least cohesive cluster.",
                blocks: [
                    tree(pick),
                    .formula(["open leaves ⌀: " + listed.enumerated().map { $0.offset == 0 ? "{\(cx(nodes[$0.element].diameter))}" : cx(nodes[$0.element].diameter) }.joined(separator: ", ") + " → split the largest"]),
                ],
                legend: [(SimColors.green, .fill, "Already split"), (SimColors.active, .fill, "Splitting now"), (SimColors.grey, .fill, "Open leaf")],
                action: round < 6 ? "Split Next" : "Start Over"
            ))
        }
        return frames
    }
}

// MARK: - DBSCAN

private let dbscanData: [CsP] = {
    var r = CsRng(83)
    let core = blob(&r, 11, 2.2, 3.0, 0.38, 0.3)
    let chain = (0..<9).map { i -> CsP in CsP(4.6 + Double(i) * 0.42, 4.1 - Double(i) * 0.08 + r.normal() * 0.05) }
    let noise = [CsP(0.6, 5.2), CsP(3.5, 2.2), CsP(3.0, 0.9), CsP(6.4, 1.4), CsP(5.2, 5.5), CsP(0.9, 1.2), CsP(8.2, 2.6)]
    return core + chain + noise
}()

private let minPts = 4

private func dbscanLab() -> CsLab {
    let eps = ladderOf(0.4, 1.4, 0.1)
    let pts = dbscanData
    let (xr, yr) = boundsOf(pts, 0.6)
    return CsLab(control: .steps, param: CsParam(name: "Radius", symbol: "ε", values: eps, initial: 5) { cx($0, 1) }) { s in
        let e = eps[s.param]
        let neighbours = pts.indices.map { i in pts.indices.filter { j in j != i && dist(pts[i], pts[j]) <= e } }
        var label = Array(repeating: -2, count: pts.count) // −2 unvisited, −1 noise
        var visited = Array(repeating: false, count: pts.count)
        var frames: [CsFrame] = []
        var clusters = 0
        var count = 0
        func frame(_ i: Int) {
            count += 1
            let n = neighbours[i].count + 1
            let core = n >= minPts
            let kind = core ? "core" : label[i] >= 0 ? "border" : "noise"
            let near = Set(neighbours[i])
            let dots = pts.indices.map { j -> CsDot in
                if j == i { return CsDot(p: pts[j], color: SimColors.active, r: 5, top: true) }
                if !visited[j] && label[j] < 0 { return CsDot(p: pts[j], color: unvisited, r: 3.5, ring: near.contains(j) ? .white : nil, ringR: 3) }
                return CsDot(p: pts[j], color: cColor(label[j]), r: 4, ring: near.contains(j) ? .white : nil, ringR: 3)
            }
            let headline: String, body: String
            switch kind {
            case "core": headline = "This point is {core}, so the cluster expands through it."; body = "Chaining core points lets DBSCAN follow shapes that aren't round."
            case "border": headline = "This point is a {border} point: reachable from a core, too sparse to expand."; body = "It joins the cluster, but the expansion stops here."
            default: headline = "Only \(n) point\(n == 1 ? "" : "s") within ε: this one is {w:noise} for now."; body = "A core point found later can still claim it as a border point."
            }
            frames.append(CsFrame(
                headline: headline,
                body: body,
                blocks: [
                    .plot(CsPlot(dots: dots, xr: xr, yr: yr, circles: [CsCircle(c: pts[i], radius: e, color: SimColors.active, fill: SimColors.active.opacity(0.08))])),
                    .formula(["neighbours within ε = \(n) \(core ? "≥" : "<") minPts \(minPts) → {\(kind == "noise" ? "w" : "v"):\(kind)}"]),
                ],
                legend: [(SimColors.active, .dot, "Visiting"), (.white, .ring, "In ε"), (cBlue, .dot, "Cluster 1"), (unvisited, .dot, "Not visited")],
                chips: [LabChip(key: "clusters", value: "\(clusters)"), LabChip(key: "visited", value: "\(count) / \(pts.count)")],
                action: "Visit Next Point"
            ))
        }
        for p in pts.indices {
            if visited[p] { continue }
            visited[p] = true
            if neighbours[p].count + 1 < minPts {
                if label[p] < 0 { label[p] = -1 }
                frame(p)
                continue
            }
            let c = clusters
            clusters += 1
            label[p] = c
            frame(p)
            var queue = neighbours[p]
            var head = 0
            while head < queue.count {
                let q = queue[head]
                head += 1
                if label[q] < 0 { label[q] = c }
                if visited[q] { continue }
                visited[q] = true
                if neighbours[q].count + 1 >= minPts { queue += neighbours[q].filter { !visited[$0] || label[$0] < 0 } }
                frame(q)
            }
        }
        let noise = label.filter { $0 < 0 }.count
        frames.append(CsFrame(
            headline: "ε = \(cx(e, 1)) finds {\(clusters) cluster\(clusters == 1 ? "" : "s")} and \(noise) noise points.",
            body: "A smaller ε splits the chain and drops more noise; a larger one merges everything into one blob.",
            blocks: [.plot(CsPlot(dots: pts.indices.map { CsDot(p: pts[$0], color: cColor(label[$0]), r: 4) }, xr: xr, yr: yr)), .formula(["minPts \(minPts), ε \(cx(e, 1)) → {v:\(clusters)} clusters"])],
            legend: clusterLegend(clusters) + [(noiseGrey, .dot, "Noise")],
            chips: [LabChip(key: "clusters", value: "\(clusters)"), LabChip(key: "visited", value: "\(pts.count) / \(pts.count)")],
            action: "Start Over"
        ))
        frames[frames.count - 2].action = "Show Clusters"
        return frames
    }
}

// MARK: - HDBSCAN and OPTICS share one dataset: a dense blob, a sparse blob and scattered noise

private let densityData: [CsP] = {
    var r = CsRng(89)
    return blob(&r, 8, 1.8, 4.4, 0.3, 0.12) + blob(&r, 10, 5.0, 3.1, 0.55, 0.45) + [CsP(0.9, 2.2), CsP(3.5, 5.3), CsP(6.9, 4.9), CsP(6.6, 1.8)]
}()

private func coreDistances(_ pts: [CsP], _ minPts: Int) -> [Double] {
    pts.indices.map { i in pts.indices.filter { $0 != i }.map { dist(pts[i], pts[$0]) }.sorted()[minPts - 1] }
}

private struct CsHdb { let labels: [Int]; let core: [Double]; let mst: [(Int, Int, Double)] }

private final class CsCond {
    let node: Int
    let birth: Double
    var stability = 0.0
    var children: [Int] = []
    init(_ node: Int, _ birth: Double) { self.node = node; self.birth = birth }
}

private func hdbscan(_ pts: [CsP], _ minPts: Int) -> CsHdb {
    let n = pts.count
    let core = coreDistances(pts, minPts)
    func mr(_ a: Int, _ b: Int) -> Double { max(core[a], core[b], dist(pts[a], pts[b])) }
    // Prim's minimum spanning tree over mutual reachability.
    var inTree = Array(repeating: false, count: n)
    var best = Array(repeating: Double.greatestFiniteMagnitude, count: n)
    var from = Array(repeating: -1, count: n)
    best[0] = 0
    var mst: [(Int, Int, Double)] = []
    for _ in 0..<n {
        let u = argmin((0..<n).filter { !inTree[$0] }) { best[$0] }
        inTree[u] = true
        if from[u] >= 0 { mst.append((from[u], u, best[u])) }
        for v in 0..<n where !inTree[v] && mr(u, v) < best[v] { best[v] = mr(u, v); from[v] = u }
    }
    // Single-linkage tree from the sorted edges: node ids ≥ n are merges.
    var parent = Array(0..<(2 * n))
    func find(_ x: Int) -> Int { var a = x; while parent[a] != a { a = parent[a] }; return a }
    var left = Array(repeating: -1, count: 2 * n), right = Array(repeating: -1, count: 2 * n)
    var height = Array(repeating: 0.0, count: 2 * n)
    var size = (0..<(2 * n)).map { $0 < n ? 1 : 0 }
    var next = n
    for (a, b, w) in mst.sortedBy({ $0.2 }) {
        let ra = find(a), rb = find(b)
        left[next] = ra; right[next] = rb; height[next] = w; size[next] = size[ra] + size[rb]
        parent[ra] = next; parent[rb] = next
        next += 1
    }
    let root = next - 1
    func leaves(_ node: Int) -> [Int] { node < n ? [node] : leaves(left[node]) + leaves(right[node]) }
    // Condensed tree: clusters with their start node, birth λ, stability and children.
    var cs: [CsCond] = [CsCond(root, 0)]
    func walk(_ node: Int, _ c: Int) {
        if node < n { return }
        let lam = 1 / max(height[node], 1e-9)
        let a = left[node], b = right[node]
        if size[a] >= minPts && size[b] >= minPts {
            cs[c].stability += Double(size[node]) * (lam - cs[c].birth)
            for child in [a, b] {
                cs.append(CsCond(child, lam))
                cs[c].children.append(cs.count - 1)
                walk(child, cs.count - 1)
            }
        } else if size[a] < minPts && size[b] < minPts {
            cs[c].stability += Double(size[node]) * (lam - cs[c].birth)
        } else {
            let (small, large) = size[a] < minPts ? (a, b) : (b, a)
            cs[c].stability += Double(size[small]) * (lam - cs[c].birth)
            walk(large, c)
        }
    }
    walk(root, 0)
    var selected = Array(repeating: false, count: cs.count)
    var total = Array(repeating: 0.0, count: cs.count)
    func clear(_ x: Int) { for ch in cs[x].children { selected[ch] = false; clear(ch) } }
    for c in cs.indices.reversed() {
        let childSum = cs[c].children.reduce(0.0) { $0 + total[$1] }
        if cs[c].children.isEmpty || (c != 0 && cs[c].stability >= childSum) {
            selected[c] = c != 0
            total[c] = cs[c].stability
            if c != 0 { clear(c) }
        } else { total[c] = childSum }
    }
    var labels = Array(repeating: -1, count: n)
    var id = 0
    // Order clusters by their smallest point index, so the dense blob (points 0…) is cluster 1.
    for c in cs.indices.filter({ selected[$0] }).sortedBy({ leaves(cs[$0].node).min()! }) {
        for p in leaves(cs[c].node) { labels[p] = id }
        id += 1
    }
    return CsHdb(labels: labels, core: core, mst: mst)
}

private func densityLegend(_ labels: [Int]) -> [(Color, SwatchStyle, String)] {
    (0..<((labels.max() ?? -1) + 1)).map { (cColor($0), .dot, $0 == 0 ? "Dense" : $0 == 1 ? "Sparse" : "Cluster \($0 + 1)") } + [(noiseGrey, .dot, "Noise")]
}

private func hdbscanLab() -> CsLab {
    let mins = [3.0, 4.0, 5.0, 6.0]
    let pts = densityData
    let (xr, yr) = boundsOf(pts, 0.8)
    return CsLab(control: .steps, param: CsParam(name: "Min points", symbol: "minPts", values: mins, initial: 1) { "\(Int($0.rounded()))" }) { s in
        let m = Int(mins[s.param].rounded())
        let h = hdbscan(pts, m)
        let order = pts.indices.sortedBy { h.core[$0] }
        func bars(_ highlight: Int?) -> CsBlock {
            .bars(values: order.map { h.core[$0] }, colors: order.map { $0 == highlight ? SimColors.active : cColor(h.labels[$0]) }, cut: nil, caption: "core distance, sorted")
        }
        func dots(_ focus: Int?) -> [CsDot] { pts.indices.map { $0 == focus ? CsDot(p: pts[$0], color: SimColors.active, r: 5, top: true) : CsDot(p: pts[$0], color: cColor(h.labels[$0])) } }
        let dense = (0..<8).sortedBy { h.core[$0] }[4]
        let sparseIdx = (8..<18).sortedBy { h.core[$0] }
        let sparse = sparseIdx[sparseIdx.count / 2]
        let nb = argmin(pts.indices.filter { $0 != sparse }) { dist(pts[sparse], pts[$0]) }
        let ratio = h.core[sparse] / h.core[dense]
        let legend = densityLegend(h.labels)
        let clusters = (h.labels.max() ?? -1) + 1
        let noise = h.labels.filter { $0 < 0 }.count
        let longest = h.mst.map(\.2).max()!
        let radius: [(Color, SwatchStyle, String)] = [(SimColors.active, .dashedLine, "Core radius")]
        let mrv = max(h.core[sparse], h.core[nb], dist(pts[sparse], pts[nb]))
        return [
            CsFrame(
                headline: "A dense point reaches \(m) neighbours within {\(cx(h.core[dense]))}.",
                body: "That radius is its core distance: small where points crowd, large where they thin out.",
                blocks: [
                    .plot(CsPlot(dots: dots(dense), xr: xr, yr: yr, circles: [CsCircle(c: pts[dense], radius: h.core[dense], color: SimColors.active, fill: SimColors.active.opacity(0.08))])),
                    bars(dense),
                    .formula(["core(a) = distance to its \(m)th neighbour = {v:\(cx(h.core[dense]))}"]),
                ],
                legend: legend + radius,
                action: "Check a Sparse Point"
            ),
            CsFrame(
                headline: "A sparse point needs a {\(cx(ratio, 1))×} wider circle to reach \(m) neighbours.",
                body: "HDBSCAN keeps every density level instead of one ε, so both blobs survive.",
                blocks: [
                    .plot(CsPlot(dots: dots(sparse), xr: xr, yr: yr, circles: [CsCircle(c: pts[sparse], radius: h.core[sparse], color: SimColors.active, fill: SimColors.active.opacity(0.08))])),
                    bars(sparse),
                    .formula(["mreach = max(core a, core b, d)", "= max(\(cx(h.core[sparse])), \(cx(h.core[nb])), \(cx(dist(pts[sparse], pts[nb])))) = {v:\(cx(mrv))}"]),
                ],
                legend: legend + radius,
                action: "Build the Tree"
            ),
            CsFrame(
                headline: "One tree over {mutual reachability} links every point.",
                body: "Long edges bridge sparse gaps; cutting them at every height at once gives the whole hierarchy.",
                blocks: [
                    .plot(CsPlot(dots: dots(nil), xr: xr, yr: yr, segs: h.mst.map { CsSeg(a: pts[$0.0], b: pts[$0.1], color: SimColors.grey.opacity(0.7), width: 1.2) })),
                    bars(nil),
                    .formula(["minimum spanning tree: \(pts.count - 1) edges, longest {v:\(cx(longest))}"]),
                ],
                legend: legend + [(SimColors.grey, .line, "Tree edge")],
                action: "Pick Stable Clusters"
            ),
            CsFrame(
                headline: "The {\(clusters) most stable} cluster\(clusters == 1 ? "" : "s") survive\(clusters == 1 ? "s" : ""); \(noise) \(noise == 1 ? "point is" : "points are") noise.",
                body: "A single ε would have to pick between the dense and the sparse blob. HDBSCAN keeps what persists longest.",
                blocks: [.plot(CsPlot(dots: dots(nil), xr: xr, yr: yr)), bars(nil), .formula(["minPts \(m) → {v:\(clusters)} clusters, \(noise) noise"])],
                legend: legend,
                action: "Start Over"
            ),
        ]
    }
}

private struct CsOptics { let order: [Int]; let reach: [Double]; let core: [Double] }

private func optics(_ pts: [CsP], _ minPts: Int) -> CsOptics {
    let core = coreDistances(pts, minPts)
    var reach = Array(repeating: Double.infinity, count: pts.count)
    var done = Array(repeating: false, count: pts.count)
    var order: [Int] = []
    for start in pts.indices {
        if done[start] { continue }
        var p = start
        while true {
            done[p] = true
            order.append(p)
            for o in pts.indices where !done[o] { reach[o] = min(reach[o], max(core[p], dist(pts[p], pts[o]))) }
            let open = pts.indices.filter { !done[$0] }
            if open.isEmpty { break }
            p = argmin(open) { reach[$0] }
            if reach[p].isInfinite { break }
        }
    }
    return CsOptics(order: order, reach: order.map { reach[$0] }, core: core)
}

private func opticsLab() -> CsLab {
    let cuts = ladderOf(0.4, 2.4, 0.2)
    let pts = densityData
    let o = optics(pts, 4)
    let (xr, yr) = boundsOf(pts, 0.8)
    return CsLab(
        control: .button,
        param: CsParam(name: "Cut height", symbol: "ε′", values: cuts, initial: 3) { cx($0, 1) },
        button: { $0.flag == 0 ? "Show Processing Order" : "Hide Processing Order" },
        onButton: { var s = $0; s.flag = 1 - s.flag; return s }
    ) { s in
        let cut = cuts[s.param]
        var labels = Array(repeating: -1, count: pts.count)
        var c = -1
        for (i, p) in o.order.enumerated() {
            if o.reach[i] > cut {
                if o.core[p] <= cut { c += 1; labels[p] = c } else { labels[p] = -1 }
            } else { labels[p] = c }
        }
        let clusters = c + 1
        let dots = pts.indices.map { CsDot(p: pts[$0], color: cColor(labels[$0])) }
        let path = s.flag == 1 ? zip(o.order, o.order.dropFirst()).map { CsSeg(a: pts[$0.0], b: pts[$0.1], color: SimColors.grey.opacity(0.8), width: 1.2) } : []
        let legend: [(Color, SwatchStyle, String)] = (0..<clusters).map { (cColor($0), .dot, "Cluster \($0 + 1)") } + [(noiseGrey, .dot, "Noise"), (SimColors.active, .dashedLine, "Cut")]
        return [CsFrame(
            headline: s.flag == 0 ? "Valleys are dense runs; {peaks} are the jumps between groups." : "OPTICS always walks to the {nearest reachable} point next.",
            body: s.flag == 0 ? "OPTICS orders points once. Any ε cut can then be read off without rerunning."
                : "So each group is visited in one unbroken run, which is why the plot's bars form valleys.",
            blocks: [
                .plot(CsPlot(dots: dots, xr: xr, yr: yr, segs: path)),
                .bars(values: o.reach, colors: o.order.map { cColor(labels[$0]) }, cut: cut, caption: nil),
                .formula(["cut at \(cx(cut, 1)): each valley below the line is a cluster → {v:\(clusters)}"]),
            ],
            legend: legend + (s.flag == 1 ? [(SimColors.grey, .line, "Processing order")] : [])
        )]
    }
}

// MARK: - Mean shift

private let shiftData: [CsP] = {
    var r = CsRng(97)
    return blob(&r, 9, 2.8, 3.0, 0.35, 0.3) + blob(&r, 9, 4.2, 5.0, 0.3, 0.3) + blob(&r, 10, 7.0, 3.3, 0.4, 0.25) + [CsP(0.9, 2.6), CsP(3.6, 4.3), CsP(5.4, 1.5)]
}()

private func shiftOnce(_ pts: [CsP], _ x: CsP, _ h: Double) -> CsP? {
    let inside = pts.filter { dist($0, x) <= h }
    return inside.isEmpty ? nil : meanOf(inside)
}

private func meanShiftLab() -> CsLab {
    let hs = ladderOf(0.8, 2.0, 0.1)
    let pts = shiftData
    let (xr, yr) = boundsOf(pts, 0.7)
    let seed = CsP(3.9, 3.9)
    return CsLab(control: .steps, param: CsParam(name: "Bandwidth", symbol: "h", values: hs, initial: 5) { cx($0, 1) }) { s in
        let h = hs[s.param]
        var path = [seed]
        var frames: [CsFrame] = []
        var x = seed
        for it in 1...15 {
            guard let m = shiftOnce(pts, x, h) else { break }
            let shift = dist(x, m)
            let settled = shift < 0.005
            let body: String
            if settled { body = "Every seed that climbs to this peak joins the same cluster." }
            else if it == 1 { body = "m(x) averages every point within h of the seed. The seed jumps there, then repeats." }
            else { body = "Steps shrink as it nears a peak. Seeds that stop at the same peak form one cluster." }
            frames.append(CsFrame(
                headline: settled ? "The shift is {\(cx(shift))}: the seed sits on a peak." : "The seed moves {\(cx(shift))} toward the mean of its window.",
                body: body,
                blocks: [
                    .plot(CsPlot(
                        dots: pts.map { CsDot(p: $0, color: SimColors.grey) } + path.dropLast().map { CsDot(p: $0, color: SimColors.answer, r: 2.5) }
                            + [CsDot(p: x, color: SimColors.answer, r: 3.5), CsDot(p: m, color: SimColors.active, r: 5, top: true)],
                        xr: xr, yr: yr,
                        segs: zip(path, path.dropFirst()).map { CsSeg(a: $0.0, b: $0.1, color: SimColors.answer, width: 2.5) } + [CsSeg(a: x, b: m, color: SimColors.answer, dashed: true, width: 2)],
                        circles: [CsCircle(c: x, radius: h, color: SimColors.active, fill: Color.black.opacity(0.25))]
                    )),
                    .formula(["m(x) = Σ w·p / Σ w, bandwidth \(cx(h, 1))", "(\(cx(x.x)), \(cx(x.y))) → {v:(\(cx(m.x)), \(cx(m.y)))}"]),
                ],
                legend: [(SimColors.answer, .line, "Path so far"), (SimColors.active, .dot, "Next position"), (SimColors.active, .dashedLine, "Window")],
                chips: [LabChip(key: "shift", value: cx(shift, 3), tint: .active), LabChip(key: "iteration", value: "\(it)")],
                action: settled ? "Shift Every Point" : "Shift Seed"
            ))
            path.append(m)
            x = m
            if settled { break }
        }
        // Every point climbs; peaks within h/2 of each other are one mode.
        var peaks: [CsP] = []
        let labels = pts.map { p -> Int in
            var y = p
            for _ in 0..<60 { y = shiftOnce(pts, y, h) ?? y }
            if let known = peaks.firstIndex(where: { dist($0, y) < h / 2 }) { return known }
            peaks.append(y)
            return peaks.count - 1
        }
        frames.append(CsFrame(
            headline: "Every point climbs to a peak: {\(peaks.count) peaks}, \(peaks.count) clusters.",
            body: "Bandwidth h sets the scale. A smaller h finds more peaks; a larger one merges them.",
            blocks: [
                .plot(CsPlot(dots: pts.indices.map { CsDot(p: pts[$0], color: cColor(labels[$0])) } + peaks.map { CsDot(p: $0, color: SimColors.active, r: 5, ring: .white, ringR: 3, top: true) }, xr: xr, yr: yr)),
                .formula(["bandwidth \(cx(h, 1)) → {v:\(peaks.count)} peaks"]),
            ],
            legend: clusterLegend(min(peaks.count, 5)) + [(SimColors.active, .dot, "Peak")],
            chips: [LabChip(key: "peaks", value: "\(peaks.count)")],
            action: "Start Over"
        ))
        return frames
    }
}

// MARK: - BIRCH

private let birchData: [CsP] = {
    var r = CsRng(101)
    let clumps = [CsP(3.4, 2.2), CsP(7.5, 2.2), CsP(0.9, 3.4), CsP(6.4, 6.8)]
    let pts = clumps.map { blob(&r, 4, $0.x, $0.y, 0.6, 0.5) }
    return (0..<4).flatMap { i in clumps.indices.map { pts[$0][i] } }
}()

private struct CsCf {
    var n: Int, lx: Double, ly: Double, ss: Double
    var centroid: CsP { CsP(lx / Double(n), ly / Double(n)) }
    func radius(with p: CsP?) -> Double {
        let nn = Double(n + (p == nil ? 0 : 1))
        let x = lx + (p?.x ?? 0), y = ly + (p?.y ?? 0)
        let s = ss + (p.map { $0.x * $0.x + $0.y * $0.y } ?? 0)
        return max(s / nn - (x / nn) * (x / nn) - (y / nn) * (y / nn), 0).squareRoot()
    }
}

private func birchLab() -> CsLab {
    let ts = ladderOf(0.4, 1.6, 0.2)
    let pts = birchData
    let (xr, yr) = boundsOf(pts, 1.0)
    return CsLab(control: .steps, param: CsParam(name: "Threshold", symbol: "T", values: ts, initial: 3) { cx($0, 1) }) { s in
        let t = ts[s.param]
        var cfs: [CsCf] = []
        var frames: [CsFrame] = []
        func table(_ target: Int) -> CsBlock {
            .table(header: ["entry", "N", "LS", "SS"], rows: cfs.indices.map { i in
                let cf = cfs[i]
                return CsRow(cells: ["CF\(i + 1)", "\(cf.n)", "(\(cx(cf.lx, 1)), \(cx(cf.ly, 1)))", cx(cf.ss, 1)], ink: i == target ? .active : nil, fill: i == target ? .active : nil)
            }, weights: [0.8, 0.5, 1.5, 0.9])
        }
        func circles(_ target: Int) -> [CsCircle] {
            cfs.indices.map { CsCircle(c: cfs[$0].centroid, radius: max(cfs[$0].radius(with: nil), 0.15) + 0.2, color: $0 == target ? SimColors.active : SimColors.answer) }
        }
        for (i, p) in pts.enumerated() {
            let near: Int? = cfs.isEmpty ? nil : argmin(Array(cfs.indices)) { d2(cfs[$0].centroid, p) }
            let r = near.map { cfs[$0].radius(with: p) }
            let absorb = r != nil && r! <= t
            let target: Int
            if absorb, let near {
                cfs[near].n += 1; cfs[near].lx += p.x; cfs[near].ly += p.y; cfs[near].ss += p.x * p.x + p.y * p.y
                target = near
            } else {
                cfs.append(CsCf(n: 1, lx: p.x, ly: p.y, ss: p.x * p.x + p.y * p.y))
                target = cfs.count - 1
            }
            let headline: String, body: String, formula: String
            if absorb { headline = "Point \(i + 1) {is absorbed} by CF\(target + 1). Only N, LS and SS change."; body = "Points are discarded, so BIRCH needs one pass." }
            else if near == nil { headline = "Point 1 starts {CF1}."; body = "A CF entry stores only N, LS and SS, enough to recover its centroid and radius." }
            else { headline = "Point \(i + 1) is too far: it {starts CF\(target + 1)}."; body = "A lower threshold T makes more, tighter entries." }
            if let r { formula = absorb ? "new radius \(cx(r)) ≤ threshold \(cx(t, 1)) → {v:absorb}" : "new radius \(cx(r)) > threshold \(cx(t, 1)) → {w:new CF}" }
            else { formula = "no entries yet → {v:new CF}" }
            frames.append(CsFrame(
                headline: headline,
                body: body,
                blocks: [
                    .plot(CsPlot(dots: pts.prefix(i).map { CsDot(p: $0, color: idleDot, r: 3) } + [CsDot(p: p, color: SimColors.active, r: 4.5, top: true)], xr: xr, yr: yr, circles: circles(target), aspect: 2.1)),
                    table(target),
                    .formula([formula]),
                ],
                legend: [(SimColors.active, .dot, "Incoming"), (SimColors.answer, .dashedLine, "CF radius")],
                action: i < pts.count - 1 ? "Insert Point \(i + 2)" : "Show Summary"
            ))
        }
        frames.append(CsFrame(
            headline: "\(pts.count) points became {\(cfs.count) CF entries}.",
            body: "A global step, such as agglomerative clustering on the CF centroids, turns entries into final clusters.",
            blocks: [.plot(CsPlot(dots: pts.map { CsDot(p: $0, color: idleDot, r: 3) }, xr: xr, yr: yr, circles: circles(-1), aspect: 2.1)), table(-1), .formula(["threshold \(cx(t, 1)) → {v:\(cfs.count)} entries"])],
            legend: [(SimColors.answer, .dashedLine, "CF radius")],
            action: "Start Over"
        ))
        return frames
    }
}

// MARK: - Affinity propagation

private let affinityData: [CsP] = {
    var r = CsRng(103)
    return blob(&r, 6, 2.6, 3.0, 0.35, 0.25) + blob(&r, 6, 4.8, 6.0, 0.3, 0.25) + blob(&r, 6, 7.4, 2.8, 0.45, 0.45) + [CsP(0.2, 3.2)]
}()

private let focus = 2

private func affinityLab() -> CsLab {
    let pts = affinityData
    let n = pts.count
    let sims = pts.indices.flatMap { i in pts.indices.filter { $0 != i }.map { -d2(pts[i], pts[$0]) } }
    let med = medianOf(sims)
    let prefs = [4.0, 2.0, 1.0, 0.5, 0.25].map { med * $0 }
    let checkpoints = [1, 2, 3, 5, 10, 20, 40, 80]
    let (xr, yr) = boundsOf(pts, 0.6)
    return CsLab(control: .steps, param: CsParam(name: "Preference", symbol: "p", values: prefs, initial: 2) { cx($0, 1) }) { s in
        let p = prefs[s.param]
        let sm = (0..<n).map { i in (0..<n).map { k in i == k ? p : -d2(pts[i], pts[k]) } }
        var r = Array(repeating: Array(repeating: 0.0, count: n), count: n)
        var a = Array(repeating: Array(repeating: 0.0, count: n), count: n)
        var frames: [CsFrame] = []
        for round in 1...checkpoints.last! {
            for i in 0..<n {
                for k in 0..<n {
                    var m = -Double.greatestFiniteMagnitude
                    for k2 in 0..<n where k2 != k { m = max(m, a[i][k2] + sm[i][k2]) }
                    r[i][k] = 0.5 * r[i][k] + 0.5 * (sm[i][k] - m)
                }
            }
            for i in 0..<n {
                for k in 0..<n {
                    var sum = 0.0
                    for i2 in 0..<n where i2 != i && i2 != k { sum += max(0, r[i2][k]) }
                    let v = i == k ? sum : min(0, r[k][k] + sum)
                    a[i][k] = 0.5 * a[i][k] + 0.5 * v
                }
            }
            if !checkpoints.contains(round) { continue }
            let choice = (0..<n).map { i in argmax(Array(0..<n)) { a[i][$0] + r[i][$0] } }
            let exemplars = (0..<n).filter { choice[$0] == $0 }
            func colourOf(_ i: Int) -> Int { exemplars.firstIndex(of: choice[i]) ?? -1 }
            let top = Array((0..<n).sortedByDescending { a[focus][$0] + r[focus][$0] }.prefix(3))
            let pick = top[0]
            frames.append(CsFrame(
                headline: "Point \(focus + 1) picks {point \(pick + 1)} as its exemplar: highest r + a.",
                body: round == 1 ? "r says how well k suits i; a says how much support k has from others. They alternate until choices settle."
                    : "After \(round) rounds there \(exemplars.count == 1 ? "is 1 exemplar" : "are \(exemplars.count) exemplars"). A higher preference p lets more points volunteer.",
                blocks: [
                    .plot(CsPlot(
                        dots: pts.indices.map { i in
                            CsDot(p: pts[i], color: cColor(colourOf(i)), r: 3.5,
                                  ring: i == focus ? SimColors.active : exemplars.contains(i) ? .white : nil,
                                  ringR: i == focus ? 7 : 4, ringWidth: 2.5)
                        },
                        xr: xr, yr: yr,
                        segs: pts.indices.filter { choice[$0] != $0 }.map { CsSeg(a: pts[$0], b: pts[choice[$0]], color: cColor(colourOf($0)).opacity(0.6), width: 1) }
                    )),
                    .table(header: ["candidate k", "r(i,k)", "a(i,k)", "sum"], rows: top.enumerated().map { j, k in
                        CsRow(cells: ["point \(k + 1)", cx(r[focus][k]), cx(a[focus][k]), cx(r[focus][k] + a[focus][k])], ink: j == 0 ? .answer : nil, fill: j == 0 ? .answer : nil)
                    }, weights: [1.3, 1, 1, 0.9]),
                ],
                legend: [(SimColors.active, .ring, "Point \(focus + 1) (i)"), (.white, .ring, "Exemplar")],
                chips: [LabChip(key: "round", value: "\(round)"), LabChip(key: "exemplars", value: "\(exemplars.count)")],
                action: round < checkpoints.last! ? "Pass Messages" : "Start Over"
            ))
        }
        return frames
    }
}

// MARK: - Spectral clustering

private let moons: ([CsP], [Int]) = {
    var r = CsRng(107)
    let outer = (0..<30).map { i -> CsP in let t = Double.pi * Double(i) / 29; let x = cos(t) + r.normal() * 0.05; return CsP(x, sin(t) + r.normal() * 0.05) }
    let inner = (0..<30).map { i -> CsP in let t = Double.pi * Double(i) / 29; let x = 1 - cos(t) + r.normal() * 0.05; return CsP(x, 0.5 - sin(t) + r.normal() * 0.05) }
    return (outer + inner, Array(repeating: 1, count: 30) + Array(repeating: 0, count: 30))
}()

private struct CsSpectral { let labels: [Int]; let vector: [Double]; let weights: [[Double]] }

private func spectral(_ pts: [CsP], _ sigma: Double) -> CsSpectral {
    let n = pts.count
    let w = (0..<n).map { i in (0..<n).map { j in i == j ? 0 : exp(-d2(pts[i], pts[j]) / (2 * sigma * sigma)) } }
    let deg = (0..<n).map { max(w[$0].reduce(0, +), 1e-12) }
    let m = (0..<n).map { i in (0..<n).map { j in w[i][j] / (deg[i] * deg[j]).squareRoot() } }
    let raw = deg.map { $0.squareRoot() }
    let rawNorm = raw.reduce(0) { $0 + $1 * $1 }.squareRoot()
    let u1 = raw.map { $0 / rawNorm }
    var x = (0..<n).map { Double($0) / Double(n) - 0.5 }
    for _ in 0..<600 {
        var y = (0..<n).map { i in x[i] + (0..<n).reduce(0.0) { $0 + m[i][$1] * x[$1] } }
        let dot = (0..<n).reduce(0.0) { $0 + y[$1] * u1[$1] }
        for i in 0..<n { y[i] -= dot * u1[i] }
        let norm = y.reduce(0) { $0 + $1 * $1 }.squareRoot()
        x = y.map { $0 / norm }
    }
    let v = (0..<n).map { x[$0] / deg[$0].squareRoot() }
    // Orient so the first point is on the positive side.
    let sign = v[0] < 0 ? -1.0 : 1.0
    let vs = v.map { $0 * sign }
    return CsSpectral(labels: vs.map { $0 > 0 ? 1 : 0 }, vector: vs, weights: w)
}

private func accuracy(_ labels: [Int], _ truth: [Int]) -> Double {
    let match = Double(labels.indices.filter { labels[$0] == truth[$0] }.count) / Double(labels.count)
    return max(match, 1 - match)
}

private func spectralLab() -> CsLab {
    let sigmas = ladderOf(0.1, 0.8, 0.05)
    let (pts, truth) = moons
    let (km, kc) = lloyd(pts, 2, false)
    let kmAcc = accuracy(km, truth)
    let (xr, yr) = boundsOf(pts, 0.2)
    return CsLab(
        control: .button,
        tabs: ["K-means", "Spectral"],
        startTab: 1,
        param: CsParam(name: "Kernel width", symbol: "σ", values: sigmas, initial: argmin(Array(sigmas.indices)) { abs(sigmas[$0] - 0.15) }) { cx($0) },
        button: { $0.tab == 1 ? "Show K-means Cut" : "Show Spectral Cut" },
        onButton: { var s = $0; s.tab = 1 - s.tab; return s }
    ) { s in
        let sg = sigmas[s.param]
        let sp = spectral(pts, sg)
        let acc = accuracy(sp.labels, truth)
        // Colour predictions so moon 1 (the upper one) is blue whenever the cut gets it right.
        func colours(_ l: [Int]) -> [Color] {
            let flip = l.indices.filter { l[$0] == truth[$0] }.count < l.count / 2
            return l.map { ($0 == 1) != flip ? cBlue : cOrange }
        }
        if s.tab == 0 {
            let cols = colours(km)
            return [CsFrame(
                headline: "K-means cuts the moons with a {straight line}.",
                body: "It assumes round clusters, so accuracy is only \(cx(kmAcc)); the spectral cut reaches \(cx(acc)) at σ = \(cx(sg)).",
                blocks: [
                    .plot(CsPlot(dots: pts.indices.map { CsDot(p: pts[$0], color: cols[$0]) } + kc.map { CsDot(p: $0, color: .clear, r: 0, ring: .white, ringR: 8, ringWidth: 2.5) }, xr: xr, yr: yr, aspect: 1.8)),
                    .formula(["nearest of 2 centres → accuracy {v:\(cx(kmAcc))}"]),
                ],
                legend: [(.white, .ring, "Centre"), (cBlue, .dot, "Moon 1"), (cOrange, .dot, "Moon 2")]
            )]
        }
        let cols = colours(sp.labels)
        var edges: [CsSeg] = []
        for i in pts.indices { for j in (i + 1)..<pts.count where sp.weights[i][j] > 0.5 { edges.append(CsSeg(a: pts[i], b: pts[j], color: SimColors.grey.opacity(0.55), width: 1)) } }
        let order = pts.indices.sortedBy { sp.vector[$0] }
        let top = sp.vector.map { abs($0) }.max()!
        let good = acc > 0.95
        let headline: String
        if good { headline = "The graph's 2nd eigenvector {separates the moons} with one threshold." }
        else if sg > 0.3 { headline = "At σ = \(cx(sg)) the graph links the moons, and the eigenvector {blurs} them." }
        else { headline = "At σ = \(cx(sg)) the graph falls apart, and the eigenvector {splits a moon}." }
        return [CsFrame(
            headline: headline,
            body: good ? "Each moon is one connected piece; k-means scores \(cx(kmAcc))."
                : "Accuracy drops to \(cx(acc)). Too wide a kernel connects everything; too narrow cuts a moon into pieces.",
            blocks: [
                .plot(CsPlot(dots: pts.indices.map { CsDot(p: pts[$0], color: cols[$0]) }, xr: xr, yr: yr, segs: edges, aspect: 1.8)),
                .plot(CsPlot(dots: order.enumerated().map { rank, i in CsDot(p: CsP(Double(rank), sp.vector[i] / top), color: cols[i], r: 2.8) },
                             xr: (-1, Double(pts.count)), yr: (-1.3, 1.3), aspect: 7, uniform: false, baseline: 0)),
                .formula(["split the 2nd eigenvector at 0 → accuracy {v:\(cx(acc))}"]),
            ],
            legend: [(SimColors.grey, .line, "Affinity edge"), (SimColors.active, .dashedLine, "Split at 0"), (cBlue, .dot, "Moon 1"), (cOrange, .dot, "Moon 2")]
        )]
    }
}

// MARK: - GMM

private let gmmData: [CsP] = {
    var r = CsRng(109)
    let angle = 50 * Double.pi / 180
    let tilted = (0..<20).map { _ -> CsP in
        let t = r.normal() * 0.9
        let q = r.normal() * 0.15
        return CsP(2.0 + t * cos(angle) - q * sin(angle), 2.2 + t * sin(angle) + q * cos(angle))
    }
    return tilted + blob(&r, 16, 5.3, 3.6, 0.6, 0.18)
}()

private struct CsGauss {
    let mx: Double, my: Double, a: Double, b: Double, c: Double, w: Double
    func pdf(_ p: CsP) -> Double {
        let det = a * c - b * b
        let dx = p.x - mx, dy = p.y - my
        let q = (c * dx * dx - 2 * b * dx * dy + a * dy * dy) / det
        return exp(-q / 2) / (2 * Double.pi * det.squareRoot())
    }
    func ellipse(_ color: Color) -> CsEllipse {
        let tr = (a + c) / 2
        let dd = (((a - c) / 2) * ((a - c) / 2) + b * b).squareRoot()
        let l1 = tr + dd, l2 = max(tr - dd, 1e-9)
        return CsEllipse(c: CsP(mx, my), a: 2 * l1.squareRoot(), b: 2 * l2.squareRoot(), angle: 0.5 * atan2(2 * b, a - c), color: color)
    }
}

private func responsibilities(_ pts: [CsP], _ g: [CsGauss]) -> [[Double]] {
    pts.map { p in let v = g.map { $0.w * $0.pdf(p) }; let t = v.reduce(0, +); return v.map { $0 / t } }
}

private func emStep(_ pts: [CsP], _ g: [CsGauss]) -> [CsGauss] {
    let resp = responsibilities(pts, g)
    return g.indices.map { k in
        let nk = resp.reduce(0.0) { $0 + $1[k] }
        let mx = pts.indices.reduce(0.0) { $0 + resp[$1][k] * pts[$1].x } / nk
        let my = pts.indices.reduce(0.0) { $0 + resp[$1][k] * pts[$1].y } / nk
        let a = pts.indices.reduce(0.0) { $0 + resp[$1][k] * (pts[$1].x - mx) * (pts[$1].x - mx) } / nk + 1e-4
        let b = pts.indices.reduce(0.0) { $0 + resp[$1][k] * (pts[$1].x - mx) * (pts[$1].y - my) } / nk
        let c = pts.indices.reduce(0.0) { $0 + resp[$1][k] * (pts[$1].y - my) * (pts[$1].y - my) } / nk + 1e-4
        return CsGauss(mx: mx, my: my, a: a, b: b, c: c, w: nk / Double(pts.count))
    }
}

private func gmmLab() -> CsLab {
    CsLab(control: .track) { _ in
        let pts = gmmData
        let (xr, yr) = boundsOf(pts, 0.9)
        var g = [CsGauss(mx: 2.8, my: 3.0, a: 0.5, b: 0, c: 0.5, w: 0.5), CsGauss(mx: 4.4, my: 3.0, a: 0.5, b: 0, c: 0.5, w: 0.5)]
        let rounds = [0, 1, 2, 3, 4, 40]
        var done = 0
        return rounds.enumerated().map { step, target in
            while done < target { g = emStep(pts, g); done += 1 }
            let resp = responsibilities(pts, g)
            let loglik = pts.reduce(0.0) { acc, p in acc + log(g.reduce(0.0) { $0 + $1.w * $1.pdf(p) }) } / Double(pts.count)
            let unsure = argmin(Array(pts.indices)) { abs(resp[$0][0] - 0.5) }
            let gamma = resp[unsure][0]
            let dots = pts.indices.map { CsDot(p: pts[$0], color: resp[$0][0] >= 0.5 ? cBlue : cOrange, ring: $0 == unsure ? SimColors.active : nil, ringR: 4) }
            let blue = cx(gamma * 100, 0)
            let headline: String, body: String
            if step == 0 {
                headline = "Two round components start {side by side}."
                body = "E-step: each point gets a responsibility per component. M-step: means, covariances and weights refit to them."
            } else if step == rounds.count - 1 {
                headline = "Converged: the log-likelihood {stops rising} at \(cx(loglik, 3)) per point."
                body = "k-means is the special case with round, equal, hard-assigned components."
            } else if target == 1 {
                headline = "One EM round: each component {moves toward} the points it claims."
                body = "The ringed point is \(blue)% blue: GMM keeps soft memberships where k-means would force a choice."
            } else {
                headline = "After \(target) EM rounds the blue component has {tilted} to fit its long cluster."
                body = "The ringed point is \(blue)% blue: GMM keeps soft memberships where k-means would force a choice."
            }
            return CsFrame(
                headline: headline,
                body: body,
                blocks: [
                    .plot(CsPlot(dots: dots, xr: xr, yr: yr, ellipses: [g[0].ellipse(cBlue), g[1].ellipse(cOrange)])),
                    .formula(["γ(point) = π₁N₁ / (π₁N₁ + π₂N₂) = {v:\(cx(gamma))}"]),
                ],
                legend: [(cBlue, .line, "Component 1"), (cOrange, .line, "Component 2"), (SimColors.active, .ring, "Uncertain point")],
                chips: [LabChip(key: "log-lik / pt", value: cx(loglik, 3), tint: .path), LabChip(key: "weights", value: "\(cx(g[0].w)) / \(cx(g[1].w))")],
                action: step < rounds.count - 2 ? "Run EM Step" : step == rounds.count - 2 ? "Run to Convergence" : "Start Over"
            )
        }
    }
}

private func clusterLab(_ topicId: String) -> CsLab {
    switch topicId {
    case "k_medians": return kMediansLab()
    case "k_modes": return kModesLab()
    case "hierarchical_clustering": return aggLab()
    case "hierarchical_divisive": return divisiveLab()
    case "dbscan": return dbscanLab()
    case "hdbscan": return hdbscanLab()
    case "optics": return opticsLab()
    case "mean_shift": return meanShiftLab()
    case "birch": return birchLab()
    case "affinity_propagation": return affinityLab()
    case "spectral_clustering": return spectralLab()
    case "gmm": return gmmLab()
    default: return kmeansLab()
    }
}

// MARK: - Lab

struct ClusterStoryLab: View {
    private let lab: CsLab

    init(topicId: String) { lab = clusterLab(topicId) }

    var body: some View {
        if lab.control == .track { CsTrackLab(lab: lab) } else { CsInteractiveLab(lab: lab) }
    }
}

private struct CsTrackLab: View {
    let lab: CsLab
    @State private var tab: Int
    @State private var frames: [CsFrame]
    @State private var playback: PlaybackState

    init(lab: CsLab) {
        self.lab = lab
        let frames = lab.frames(lab.initial)
        _tab = State(initialValue: lab.startTab)
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
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            if !lab.tabs.isEmpty {
                LabSegments(labels: lab.tabs, selected: Binding(get: { tab }, set: { t in
                    tab = t
                    var s = lab.initial
                    s.tab = t
                    frames = lab.frames(s)
                    playback.stepCount = frames.count
                    playback.index = 0
                })).padding(.bottom, 14)
            }
            CsBody(frame: frame)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) },
                              action: { frames[min(max($0, 0), frames.count - 1)].action })
        }
    }
}

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class CsModel {
    let lab: CsLab
    var state: CsState {
        didSet {
            if state.tab != oldValue.tab || state.param != oldValue.param || state.flag != oldValue.flag { frames = lab.frames(state) }
        }
    }
    private(set) var frames: [CsFrame]

    init(lab: CsLab) {
        self.lab = lab
        state = lab.initial
        frames = lab.frames(lab.initial)
    }

    var index: Int { min(state.index, frames.count - 1) }
}

private struct CsInteractiveLab: View {
    @State private var model: CsModel
    @Environment(\.labDock) private var dock

    init(lab: CsLab) { _model = State(initialValue: CsModel(lab: lab)) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !model.lab.tabs.isEmpty {
                LabSegments(labels: model.lab.tabs, selected: Binding(get: { model.state.tab }, set: { model.state.tab = $0; model.state.index = 0 })).padding(.bottom, 14)
            }
            CsBody(frame: model.frames[model.index])
            if dock == nil {
                Divider().padding(.top, 16)
                CsControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(CsControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.state = model.lab.initial }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct CsControls: View {
    let model: CsModel

    var body: some View {
        let lab = model.lab
        let s = model.state
        let index = model.index
        VStack(spacing: 14) {
            if let p = lab.param {
                LabParamStepper(param: LabParam(tab: p.name, name: p.name, symbol: p.symbol, text: p.format(p.values[s.param]),
                                                canDecrease: s.param > 0, canIncrease: s.param < p.values.count - 1)) { d in
                    model.state.param = min(max(model.state.param + d, 0), p.values.count - 1)
                    model.state.index = 0
                }
            }
            if lab.control == .steps {
                LabBackActionRow(action: model.frames[index].action, backEnabled: index > 0,
                                 onBack: { model.state.index = index - 1 },
                                 onAction: { model.state.index = index >= model.frames.count - 1 ? 0 : index + 1 })
            } else if let label = lab.button {
                LabButton(label: label(s), primary: true) { model.state = lab.onButton(model.state) }
            }
        }
    }
}

private struct CsBody: View {
    let frame: CsFrame

    var body: some View {
        LabCard {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(frame.blocks.indices, id: \.self) { i in
                    switch frame.blocks[i] {
                    case .plot(let plot): CsPlotView(plot: plot)
                    case let .bars(values, colors, cut, caption): CsBarsView(values: values, colors: colors, cut: cut, caption: caption)
                    case let .table(header, rows, weights): CsTableView(header: header, rows: rows, weights: weights)
                    case .formula(let lines): CsFormulaView(lines: lines)
                    case let .dendro(leafCount, names, merges): CsDendroView(leafCount: leafCount, names: names, merges: merges)
                    case .tree(let nodes): CsTreeView(nodes: nodes)
                    case let .modes(header, centres, changed, rows): CsModesView(header: header, centres: centres, changed: changed, rows: rows)
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

private struct CsStage: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private func circlePath(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }

private struct CsFormulaView: View {
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

private struct CsPlotView: View {
    let plot: CsPlot
    @Environment(\.palette) private var palette

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
            func at(_ p: CsP) -> CGPoint { CGPoint(x: ox + CGFloat(p.x) * kx, y: oy - CGFloat(p.y) * ky) }
            if let y = plot.baseline {
                var path = Path(); path.move(to: CGPoint(x: pad / 2, y: oy - CGFloat(y) * ky)); path.addLine(to: CGPoint(x: size.width - pad / 2, y: oy - CGFloat(y) * ky))
                ctx.stroke(path, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            }
            for c in plot.circles {
                let r = CGFloat(c.radius) * kx
                if let fill = c.fill { ctx.fill(circlePath(at(c.c), r), with: .color(fill)) }
                ctx.stroke(circlePath(at(c.c), r), with: .color(c.color), style: StrokeStyle(lineWidth: c.width, dash: c.dashed ? [5, 4] : []))
            }
            for e in plot.ellipses {
                let c = at(e.c)
                let w = CGFloat(e.a) * kx, h = CGFloat(e.b) * ky
                let t = CGAffineTransform(translationX: c.x, y: c.y).rotated(by: -CGFloat(e.angle)).translatedBy(x: -c.x, y: -c.y)
                let path = Path(ellipseIn: CGRect(x: c.x - w, y: c.y - h, width: 2 * w, height: 2 * h)).applying(t)
                ctx.fill(path, with: .color(e.color.opacity(0.12)))
                ctx.stroke(path, with: .color(e.color), lineWidth: 2)
            }
            for sg in plot.segs {
                var path = Path(); path.move(to: at(sg.a)); path.addLine(to: at(sg.b))
                ctx.stroke(path, with: .color(sg.color), style: StrokeStyle(lineWidth: sg.width, lineCap: sg.dashed ? .butt : .round, dash: sg.dashed ? [5, 4] : []))
            }
            for d in plot.dots.filter({ !$0.top }) + plot.dots.filter(\.top) {
                let c = at(d.p)
                if d.r > 0 { ctx.fill(circlePath(c, d.r), with: .color(d.color)) }
                if let ring = d.ring { ctx.stroke(circlePath(c, d.r + d.ringR), with: .color(ring), style: StrokeStyle(lineWidth: d.ringWidth, dash: d.ringDashed ? [3, 3] : [])) }
            }
            for l in plot.labels {
                let c = at(l.p)
                ctx.draw(Text(l.text).font(AppFont.sans(11)).foregroundStyle(palette.muted), at: CGPoint(x: c.x + 5, y: c.y - 18), anchor: .topLeading)
            }
        }
        .aspectRatio(plot.aspect, contentMode: .fit)
        .modifier(CsStage())
    }
}

private struct CsBarsView: View {
    let values: [Double]
    let colors: [Color]
    let cut: Double?
    let caption: String?
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let finite = values.filter(\.isFinite)
            let top = max(finite.max() ?? 1, cut ?? 0) * 1.08
            let gap: CGFloat = 2
            let w = (size.width - gap * CGFloat(values.count - 1)) / CGFloat(values.count)
            for (i, v) in values.enumerated() {
                let h = max(v.isFinite ? CGFloat(v / top) * size.height : size.height, 2)
                ctx.fill(Path(CGRect(x: CGFloat(i) * (w + gap), y: size.height - h, width: w, height: h)), with: .color(colors[i]))
            }
            if let cut {
                let y = size.height - CGFloat(cut / top) * size.height
                var path = Path(); path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: size.width, y: y))
                ctx.stroke(path, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .frame(height: 96)
        .overlay(alignment: .bottomLeading) {
            if let caption { Text(caption).font(AppFont.sans(11)).foregroundStyle(palette.muted).padding(.leading, 12).padding(.bottom, 2) }
        }
        .modifier(CsStage())
    }
}

private struct CsTableView: View {
    let header: [String]
    let rows: [CsRow]
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
                                .font(i == 0 ? AppFont.sans(16, .semibold) : AppFont.mono(15))
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
        return tone == .answer ? SimColors.answer.opacity(0.28) : tone.color.opacity(0.18)
    }
}

private struct CsDendroView: View {
    let leafCount: Int
    let names: [String]
    let merges: [CsMerge]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let n = leafCount
            var children: [Int: (Int, Int)] = [:]
            for (i, m) in merges.enumerated() { children[n + i] = (m.a, m.b) }
            var order: [Int] = []
            func visit(_ node: Int) { if node < n { order.append(node) } else if let c = children[node] { visit(c.0); visit(c.1) } }
            visit(n + merges.count - 1)
            let pad: CGFloat = 16, bottom = size.height - 22, top: CGFloat = 10
            let slot = (size.width - 2 * pad) / CGFloat(n - 1)
            let maxH = merges.map(\.height).max()!
            var xs: [Int: CGFloat] = [:], hs: [Int: CGFloat] = [:]
            for (i, leaf) in order.enumerated() { xs[leaf] = pad + CGFloat(i) * slot; hs[leaf] = bottom }
            for (i, m) in merges.enumerated() {
                let y = bottom - CGFloat(m.height / maxH) * (bottom - top)
                let xa = xs[m.a]!, xb = xs[m.b]!
                let color: Color = m.state == .merged ? SimColors.answer : m.state == .now ? SimColors.active : SimColors.grey.opacity(0.55)
                let style = StrokeStyle(lineWidth: m.state == .later ? 1.2 : 2.5, dash: m.state == .now ? [5, 4] : [])
                var path = Path()
                path.move(to: CGPoint(x: xa, y: hs[m.a]!)); path.addLine(to: CGPoint(x: xa, y: y))
                path.addLine(to: CGPoint(x: xb, y: y)); path.addLine(to: CGPoint(x: xb, y: hs[m.b]!))
                ctx.stroke(path, with: .color(color), style: style)
                xs[n + i] = (xa + xb) / 2
                hs[n + i] = y
            }
            for (i, leaf) in order.enumerated() {
                ctx.draw(Text(names[leaf]).font(AppFont.mono(11)).foregroundStyle(palette.muted), at: CGPoint(x: pad + CGFloat(i) * slot, y: bottom + 4), anchor: .top)
            }
        }
        .frame(height: 130)
        .modifier(CsStage())
    }
}

private struct CsTreeView: View {
    let nodes: [CsNode]
    @Environment(\.palette) private var palette

    var body: some View {
        var depth = Array(repeating: 0, count: nodes.count)
        for i in nodes.indices where nodes[i].parent >= 0 { depth[i] = depth[nodes[i].parent] + 1 }
        let kids = nodes.indices.map { i in nodes.indices.filter { nodes[$0].parent == i } }
        var leafOrder: [Int] = []
        func visit(_ i: Int) { if kids[i].isEmpty { leafOrder.append(i) } else { kids[i].forEach(visit) } }
        visit(0)
        var span = Array(repeating: 0, count: nodes.count)
        func count(_ i: Int) -> Int { let c = kids[i].isEmpty ? 1 : kids[i].reduce(0) { $0 + count($1) }; span[i] = c; return c }
        _ = count(0)
        let rows = depth.max()! + 1
        return GeometryReader { geo in
            let slot = geo.size.width / CGFloat(leafOrder.count)
            var xs = Array(repeating: CGFloat(0), count: nodes.count)
            func place(_ i: Int) -> CGFloat {
                xs[i] = kids[i].isEmpty ? (CGFloat(leafOrder.firstIndex(of: i)!) + 0.5) * slot : kids[i].map { place($0) }.reduce(0, +) / CGFloat(kids[i].count)
                return xs[i]
            }
            _ = place(0)
            return ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in
                    for (i, n) in nodes.enumerated() where n.parent >= 0 {
                        var path = Path()
                        path.move(to: CGPoint(x: xs[n.parent], y: CGFloat(depth[n.parent] * 52 + 30)))
                        path.addLine(to: CGPoint(x: xs[i], y: CGFloat(depth[i] * 52 + 12)))
                        ctx.stroke(path, with: .color(n.dashed ? SimColors.active : SimColors.grey.opacity(0.6)), style: StrokeStyle(lineWidth: 1.2, dash: n.dashed ? [4, 3] : []))
                    }
                }
                ForEach(nodes.indices, id: \.self) { i in
                    let n = nodes[i]
                    let room = CGFloat(span[i]) * slot
                    let text = room >= CGFloat(n.full.count) * 8.2 + 22 ? n.full : n.short
                    let w = CGFloat(text.count) * 8.2 + 18
                    let (fill, ink, edge): (Color, Color, Color) = n.tone == .active ? (SimColors.active.opacity(0.16), StoryTone.active.ink(palette), SimColors.active)
                        : n.tone == .done ? (SimColors.green.opacity(0.14), StoryTone.done.ink(palette), SimColors.green)
                        : (SimColors.tint, palette.onSurface, SimColors.grey.opacity(0.5))
                    Text(text).font(AppFont.mono(13, .semibold)).foregroundStyle(ink).lineLimit(1)
                        .frame(width: w, height: 28)
                        .background(fill, in: RoundedRectangle(cornerRadius: 7))
                        .overlay(RoundedRectangle(cornerRadius: 7).strokeBorder(edge, lineWidth: 1.5))
                        .position(x: xs[i], y: CGFloat(depth[i] * 52 + 22))
                }
            }
        }
        .frame(height: CGFloat(rows * 52 + 8))
        .modifier(CsStage())
    }
}

private struct CsModesView: View {
    let header: [String]
    let centres: [[String]]
    let changed: Set<Int>
    let rows: [CsModeRow]
    @Environment(\.palette) private var palette

    private let weights: [CGFloat] = [0.5, 1, 1, 1, 1, 0.9]

    var body: some View {
        let total = weights.reduce(0, +)
        GeometryReader { geo in
            let inner = geo.size.width - 8
            let w: (Int) -> CGFloat = { inner * weights[$0] / total }
            VStack(spacing: 0) {
                line(w, "", header, "c1 · c2", { _ in palette.muted }, bold: false)
                ForEach(centres.indices, id: \.self) { c in
                    line(w, "c\(c + 1)", centres[c], "", { i in changed.contains(c * 10 + i) ? StoryTone.active.ink(palette) : StoryTone.answer.ink(palette) },
                         bold: true, nameColor: StoryTone.answer.ink(palette))
                }
                ForEach(rows.indices, id: \.self) { r in
                    let row = rows[r]
                    let current = row.state == .current
                    line(w, row.name, row.cells, row.score, { i in
                        row.mismatch.contains(i) ? StoryTone.warn.ink(palette) : current ? StoryTone.active.ink(palette)
                            : row.state == .future ? palette.muted.opacity(0.6) : palette.onSurface
                    }, bold: current, underline: row.mismatch, nameColor: current ? StoryTone.active.ink(palette) : palette.muted)
                }
            }
            .padding(.horizontal, 4)
        }
        .frame(height: CGFloat(1 + centres.count + rows.count) * 24)
    }

    private func line(_ w: @escaping (Int) -> CGFloat, _ name: String, _ cells: [String], _ score: String, _ color: @escaping (Int) -> Color,
                      bold: Bool, underline: Set<Int> = [], nameColor: Color? = nil) -> some View {
        HStack(spacing: 0) {
            Text(name).font(AppFont.mono(13, bold ? .bold : .regular)).foregroundStyle(nameColor ?? palette.muted).frame(width: w(0), alignment: .leading)
            ForEach(cells.indices, id: \.self) { i in
                Text(cells[i]).font(AppFont.sans(14, bold ? .bold : .regular)).foregroundStyle(color(i)).underline(underline.contains(i)).lineLimit(1)
                    .frame(width: w(i + 1), alignment: .leading)
            }
            Text(score).font(AppFont.mono(13)).foregroundStyle(nameColor ?? palette.muted).lineLimit(1).frame(width: w(5), alignment: .trailing)
        }
        .frame(height: 24)
    }
}
