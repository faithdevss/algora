import Foundation

// Port of MetricsMath.kt (B9): seventeen metrics over five shared substrates. Every classification
// metric reads one fitted model's score vector, the regression group one fit, the clustering indices
// one clustering. kotlin.random seeds and draw order match Android.

private func firstMaxIndex<T: Comparable>(_ xs: [T]) -> Int {
    var best = 0
    for i in xs.indices where xs[i] > xs[best] { best = i }
    return best
}

private func firstMinIndex<T: Comparable>(_ xs: [T]) -> Int {
    var best = 0
    for i in xs.indices where xs[i] < xs[best] { best = i }
    return best
}

private func distinctInOrder<T: Hashable>(_ xs: [T]) -> [T] {
    var seen = Set<T>()
    return xs.filter { seen.insert($0).inserted }
}

// MARK: - Classification

enum ScoredLab {
    static let positiveRate = 0.10
    static let count = 1000

    struct Scored { let score: Double; let label: Int }

    private static let data: [Sample] = {
        var random = KotlinRandom(seed: 73)
        return (0..<count).map { _ in
            let positive = random.nextDouble() < positiveRate
            let centre = positive ? 1.1 : -0.2
            let x = centre + random.nextDouble() * 2 - 1
            let y = centre * 0.7 + random.nextDouble() * 2 - 1
            return Sample([x, y], positive ? 1 : 0)
        }
    }()

    private static let weights: [Double] = {
        var w = [0.0, 0.0, 0.0]
        for _ in 0..<400 {
            var gradient = [0.0, 0.0, 0.0]
            for sample in data {
                let z = w[0] + w[1] * sample.features[0] + w[2] * sample.features[1]
                let p = 1 / (1 + exp(-z))
                let error = p - Double(sample.label)
                gradient[0] += error
                gradient[1] += error * sample.features[0]
                gradient[2] += error * sample.features[1]
            }
            for i in w.indices { w[i] -= 0.05 * gradient[i] / Double(data.count) }
        }
        return w
    }()

    static let scored: [Scored] = data.map { sample in
        let z = weights[0] + weights[1] * sample.features[0] + weights[2] * sample.features[1]
        return Scored(score: 1 / (1 + exp(-z)), label: sample.label)
    }

    static var samples: [Sample] { data }
    static var coefficients: [Double] { weights }

    /// Decision boundary y = slope·x + intercept at a threshold.
    static func boundaryFor(_ threshold: Double) -> (Double, Double) {
        let logit = log(threshold / (1 - threshold))
        let slope = -weights[1] / weights[2]
        let intercept = (logit - weights[0]) / weights[2]
        return (slope, intercept)
    }

    static var positives: Int { scored.filter { $0.label == 1 }.count }
    static var negatives: Int { scored.filter { $0.label == 0 }.count }
    static var majorityAccuracy: Double { Double(negatives) / Double(scored.count) }

    struct Cells {
        let tp: Int, fp: Int, fn: Int, tn: Int
        var total: Int { tp + fp + fn + tn }
        var accuracy: Double { Double(tp + tn) / Double(total) }
        var precision: Double { tp + fp == 0 ? 0 : Double(tp) / Double(tp + fp) }
        var recall: Double { tp + fn == 0 ? 0 : Double(tp) / Double(tp + fn) }
        var specificity: Double { tn + fp == 0 ? 0 : Double(tn) / Double(tn + fp) }
        var falsePositiveRate: Double { 1 - specificity }
        var f1: Double { precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall) }

        func fBeta(_ beta: Double) -> Double {
            let b2 = beta * beta
            let denominator = b2 * precision + recall
            return denominator == 0 ? 0 : (1 + b2) * precision * recall / denominator
        }

        var kappa: Double {
            let n = Double(total)
            let observed = Double(tp + tn) / n
            let expected = (Double(tp + fp) / n) * (Double(tp + fn) / n) + (Double(tn + fn) / n) * (Double(tn + fp) / n)
            return expected == 1.0 ? 0 : (observed - expected) / (1 - expected)
        }

        var matthews: Double {
            let a = Double(tp) + Double(fp), b = Double(tp) + Double(fn), c = Double(tn) + Double(fp), d = Double(tn) + Double(fn)
            let denominator = (a * b * c * d).squareRoot()
            return denominator == 0 ? 0 : (Double(tp) * Double(tn) - Double(fp) * Double(fn)) / denominator
        }
    }

    static func cellsAt(_ threshold: Double, _ data: [Scored] = scored) -> Cells {
        var tp = 0, fp = 0, fn = 0, tn = 0
        for item in data {
            let predicted = item.score >= threshold
            if predicted && item.label == 1 { tp += 1 } else if predicted && item.label == 0 { fp += 1 } else if !predicted && item.label == 1 { fn += 1 } else { tn += 1 }
        }
        return Cells(tp: tp, fp: fp, fn: fn, tn: tn)
    }

    static let thresholds = [0.05, 0.10, 0.20, 0.30, 0.50, 0.70, 0.90]

    static let bestF1Threshold: Double = {
        let candidates = (1...99).map { Double($0) / 100.0 }
        return candidates[firstMaxIndex(candidates.map { cellsAt($0).f1 })]
    }()

    static var bestF1: Double { cellsAt(bestF1Threshold).f1 }

    private static var distinctScoresDescending: [Double] { distinctInOrder(scored.map(\.score)).sorted(by: >) }

    static let rocCurve: [(Double, Double)] = {
        var points: [(Double, Double)] = [(0.0, 0.0)]
        for threshold in distinctScoresDescending {
            let cells = cellsAt(threshold)
            points.append((cells.falsePositiveRate, cells.recall))
        }
        points.append((1.0, 1.0))
        return points.sortedBy { $0.0 }
    }()

    static let prCurve: [(Double, Double)] = distinctScoresDescending.map { threshold in
        let cells = cellsAt(threshold)
        return (cells.recall, cells.precision)
    }

    private static func trapezoid(_ curve: [(Double, Double)]) -> Double {
        var total = 0.0
        for i in 0..<max(curve.count - 1, 0) {
            let a = curve[i], b = curve[i + 1]
            total += (b.0 - a.0) * (a.1 + b.1) / 2
        }
        return total
    }

    static let auc: Double = trapezoid(rocCurve)

    static let averagePrecision: Double = {
        let sorted = scored.sortedByDescending { $0.score }
        var hits = 0
        var sum = 0.0
        for (index, item) in sorted.enumerated() where item.label == 1 {
            hits += 1
            sum += Double(hits) / Double(index + 1)
        }
        return sum / Double(positives)
    }()

    private static func rankingAuc(_ data: [Scored]) -> Double {
        let positiveScores = data.filter { $0.label == 1 }.map(\.score)
        let negativeScores = data.filter { $0.label == 0 }.map(\.score)
        var wins = 0.0
        for p in positiveScores {
            for n in negativeScores { wins += p > n ? 1.0 : p == n ? 0.5 : 0.0 }
        }
        return wins / Double(positiveScores.count * negativeScores.count)
    }

    static let aucByRanking: Double = rankingAuc(scored)

    static func logLoss(_ data: [Scored] = scored) -> Double {
        -data.reduce(0.0) { acc, item in
            let p = min(max(item.score, 1e-15), 1 - 1e-15)
            return acc + (item.label == 1 ? log(p) : log(1 - p))
        } / Double(data.count)
    }

    /// Monotone push towards 0 and 1: same ranking, wrong probabilities.
    static let overconfident: [Scored] = scored.map {
        Scored(score: $0.score >= 0.5 ? 0.5 + ($0.score - 0.5) * 1.98 : $0.score * 0.02, label: $0.label)
    }

    static let overconfidentAuc: Double = rankingAuc(overconfident)

    struct ConfidentError { let worstContribution: Double; let meanContribution: Double; let worstScore: Double }

    static let confidentError: ConfidentError = {
        let contributions = scored.map { item -> Double in
            let p = min(max(item.score, 1e-15), 1 - 1e-15)
            return item.label == 1 ? -log(p) : -log(1 - p)
        }
        let worst = firstMaxIndex(contributions)
        return ConfidentError(worstContribution: contributions[worst], meanContribution: contributions.average, worstScore: scored[worst].score)
    }()

    static func brier(_ data: [Scored] = scored) -> Double {
        data.reduce(0.0) { $0 + ($1.score - Double($1.label)) * ($1.score - Double($1.label)) } / Double(data.count)
    }

    struct Bin { let lower: Double; let predicted: Double; let observed: Double; let count: Int }

    static func calibration(_ data: [Scored] = scored, bins: Int = 10) -> [Bin] {
        (0..<bins).compactMap { index -> Bin? in
            let lower = Double(index) / Double(bins)
            let upper = (Double(index) + 1.0) / Double(bins)
            let inBin = data.filter { $0.score >= lower && ($0.score < upper || (index == bins - 1 && $0.score <= 1.0)) }
            if inBin.isEmpty { return nil }
            return Bin(lower: lower, predicted: inBin.map(\.score).average,
                       observed: Double(inBin.filter { $0.label == 1 }.count) / Double(inBin.count), count: inBin.count)
        }
    }
}

// MARK: - Regression

enum RegressionMetricsLab {
    static let clean = 40
    static let contaminated = 4

    struct Point { let x: Double; let y: Double; let contaminated: Bool }

    static let points: [Point] = {
        var random = KotlinRandom(seed: 79)
        var out: [Point] = []
        for i in 0..<clean {
            let x = Double(i) * 0.25
            out.append(Point(x: x, y: 2.0 + 1.4 * x + random.nextDouble() * 2 - 1, contaminated: false))
        }
        for i in 0..<contaminated {
            let x = 7.0 + Double(i) * 0.6
            out.append(Point(x: x, y: 2.0 + 1.4 * x + 14 + random.nextDouble() * 3, contaminated: true))
        }
        return out
    }()

    struct Fit {
        let name: String
        let intercept: Double
        let slope: Double
        func predict(_ x: Double) -> Double { intercept + slope * x }
    }

    private static func residuals(_ fit: Fit, _ data: [Point] = points) -> [Double] { data.map { $0.y - fit.predict($0.x) } }

    static let squaredLossFit: Fit = {
        let c = leastSquares(points.map { [$0.x] }, points.map(\.y))
        return Fit(name: "least squares (MSE)", intercept: c[0], slope: c[1])
    }()

    /// IRLS with weights 1/|residual|.
    static let absoluteLossFit: Fit = {
        var intercept = squaredLossFit.intercept
        var slope = squaredLossFit.slope
        for _ in 0..<60 {
            let weights = points.map { 1.0 / (abs($0.y - (intercept + slope * $0.x)) + 1e-6) }
            var sw = 0.0, swx = 0.0, swy = 0.0, swxx = 0.0, swxy = 0.0
            for (index, point) in points.enumerated() {
                let w = weights[index]
                sw += w; swx += w * point.x; swy += w * point.y
                swxx += w * point.x * point.x; swxy += w * point.x * point.y
            }
            let denominator = sw * swxx - swx * swx
            slope = (sw * swxy - swx * swy) / denominator
            intercept = (swy - slope * swx) / sw
        }
        return Fit(name: "least absolute deviations (MAE)", intercept: intercept, slope: slope)
    }()

    static let trueSlope = 1.4

    static func mse(_ fit: Fit, _ data: [Point] = points) -> Double { residuals(fit, data).reduce(0.0) { $0 + $1 * $1 } / Double(data.count) }
    static func rmse(_ fit: Fit, _ data: [Point] = points) -> Double { mse(fit, data).squareRoot() }
    static func mae(_ fit: Fit, _ data: [Point] = points) -> Double { residuals(fit, data).reduce(0.0) { $0 + abs($1) } / Double(data.count) }

    static func rSquared(_ fit: Fit, _ data: [Point] = points) -> Double {
        let mean = data.map(\.y).average
        let residual = residuals(fit, data).reduce(0.0) { $0 + $1 * $1 }
        let total = data.reduce(0.0) { $0 + ($1.y - mean) * ($1.y - mean) }
        return 1 - residual / total
    }

    static let outlierErrorShare: Double = {
        let all = residuals(squaredLossFit).map { $0 * $0 }
        let bad = points.indices.filter { points[$0].contaminated }.reduce(0.0) { $0 + all[$1] }
        return bad / all.reduce(0, +)
    }()

    static var outlierCountShare: Double { Double(contaminated) / Double(points.count) }

    static let worseThanMean = Fit(name: "a plausible-looking wrong model", intercept: 14.0, slope: -0.6)

    struct NoiseStep { let extraColumns: Int; let rSquared: Double; let adjusted: Double }

    static let noiseColumns: [NoiseStep] = {
        var rng = KotlinRandom(seed: 83)
        let y = points.map(\.y)
        let noise = (0..<10).map { _ in (0..<points.count).map { _ in rng.nextDouble() * 2 - 1 } }
        return (0...8).map { extra in
            let x = points.indices.map { row in (0..<(1 + extra)).map { column in column == 0 ? points[row].x : noise[column - 1][row] } }
            let coefficients = leastSquares(x, y)
            let predictions = x.map { predictLinear(coefficients, $0) }
            let mean = y.average
            var residual = 0.0
            for i in y.indices { residual += (y[i] - predictions[i]) * (y[i] - predictions[i]) }
            let total = y.reduce(0.0) { $0 + ($1 - mean) * ($1 - mean) }
            let r2 = 1 - residual / total
            let n = y.count
            let p = 1 + extra
            return NoiseStep(extraColumns: extra, rSquared: r2, adjusted: 1 - (1 - r2) * Double(n - 1) / Double(n - p - 1))
        }
    }()

    static func adjustedRSquared(_ r2: Double, _ n: Int, _ p: Int) -> Double { 1 - (1 - r2) * Double(n - 1) / Double(n - p - 1) }
}

// MARK: - Split impurity

enum ImpurityLab {
    struct Split {
        let name: String
        let leftPositive: Int, leftNegative: Int, rightPositive: Int, rightNegative: Int
        var leftTotal: Int { leftPositive + leftNegative }
        var rightTotal: Int { rightPositive + rightNegative }
        var total: Int { leftTotal + rightTotal }
    }

    static let splits = [
        Split(name: "A: 300/100 · 100/300", leftPositive: 300, leftNegative: 100, rightPositive: 100, rightNegative: 300),
        Split(name: "B: 200/0 · 0/200", leftPositive: 200, leftNegative: 0, rightPositive: 0, rightNegative: 200),
        Split(name: "C: 200/100 · 0/100", leftPositive: 200, leftNegative: 100, rightPositive: 0, rightNegative: 100),
        Split(name: "D: 190/210 · 10/0", leftPositive: 190, leftNegative: 210, rightPositive: 10, rightNegative: 0),
    ]

    static func gini(_ positive: Int, _ negative: Int) -> Double {
        let n = Double(positive + negative)
        if n == 0 { return 0 }
        let p = Double(positive) / n
        return 1 - p * p - (1 - p) * (1 - p)
    }

    static func entropy(_ positive: Int, _ negative: Int) -> Double {
        let n = Double(positive + negative)
        if n == 0 { return 0 }
        let p = Double(positive) / n
        if p == 0 || p == 1 { return 0 }
        return -(p * log(p) + (1 - p) * log(1 - p)) / log(2.0)
    }

    static func misclassification(_ positive: Int, _ negative: Int) -> Double {
        let n = Double(positive + negative)
        if n == 0 { return 0 }
        return 1 - Double(max(positive, negative)) / n
    }

    private static func weighted(_ split: Split, _ impurity: (Int, Int) -> Double) -> Double {
        let n = Double(split.total)
        return Double(split.leftTotal) / n * impurity(split.leftPositive, split.leftNegative) +
            Double(split.rightTotal) / n * impurity(split.rightPositive, split.rightNegative)
    }

    struct Scores { let split: Split; let giniGain: Double; let entropyGain: Double; let errorGain: Double }

    static let parentPositive = 200
    static let parentNegative = 200

    static let scores: [Scores] = {
        let giniParent = gini(parentPositive, parentNegative)
        let entropyParent = entropy(parentPositive, parentNegative)
        let errorParent = misclassification(parentPositive, parentNegative)
        return splits.map { split in
            Scores(split: split,
                   giniGain: giniParent - weighted(split, gini),
                   entropyGain: entropyParent - weighted(split, entropy),
                   errorGain: errorParent - weighted(split, misclassification))
        }
    }()

    static let tiedForError: (Scores, Scores) = {
        for i in scores.indices {
            for j in scores.indices where j > i {
                let a = scores[i], b = scores[j]
                if abs(a.errorGain - b.errorGain) < 1e-9 && abs(a.giniGain - b.giniGain) > 1e-9 { return (a, b) }
            }
        }
        fatalError("no split pair ties on error")
    }()

    static let giniMaximum = 0.5
    static let entropyMaximum = 1.0

    static func impurityCurve(steps: Int = 51) -> [(Double, Double, Double)] {
        (0..<steps).map { index in
            let p = Double(index) / Double(steps - 1)
            let n = 1000
            let positive = Int(p * Double(n))
            return (gini(positive, n - positive), entropy(positive, n - positive), misclassification(positive, n - positive))
        }
    }
}

// MARK: - Margin losses

enum MarginLab {
    static let margins: [Double] = ScoredLab.scored.map { item in
        let sign = item.label == 1 ? 1.0 : -1.0
        let p = min(max(item.score, 1e-9), 1 - 1e-9)
        return sign * log(p / (1 - p))
    }

    static func hinge(_ margin: Double) -> Double { max(0.0, 1 - margin) }
    static func logistic(_ margin: Double) -> Double { log(1 + exp(-margin)) }
    static func zeroOne(_ margin: Double) -> Double { margin <= 0 ? 1.0 : 0.0 }
    static func squaredHinge(_ margin: Double) -> Double { hinge(margin) * hinge(margin) }

    struct Active { let hingeActive: Int; let logisticActive: Int; let total: Int; let tolerance: Double }

    static let active: Active = {
        let tolerance = 1e-9
        return Active(hingeActive: margins.filter { hinge($0) > tolerance }.count,
                      logisticActive: margins.filter { logistic($0) > tolerance }.count,
                      total: margins.count, tolerance: tolerance)
    }()

    static func hingeGradient(_ margin: Double) -> Double { margin < 1 ? -1.0 : 0.0 }
    static func logisticGradient(_ margin: Double) -> Double { -1 / (1 + exp(margin)) }
    static func logisticGradientAt(_ margin: Double) -> Double { abs(logisticGradient(margin)) }

    static func curve(_ loss: (Double) -> Double, from: Double = -2.0, to: Double = 3.0, steps: Int = 60) -> [(Double, Double)] {
        (0..<steps).map { index in
            let margin = from + (to - from) * Double(index) / Double(steps - 1)
            return (margin, loss(margin))
        }
    }
}

// MARK: - Clustering indices

enum ClusterMetricsLab {
    struct Point2 { let x: Double; let y: Double }

    // One shared kotlin.random stream, drawn by blobs first and rings second — the order every
    // Android lab touches them in.
    private static let datasets: ([Point2], [Point2]) = {
        var random = KotlinRandom(seed: 97)
        let centres = [Point2(x: 0.2, y: 0.2), Point2(x: 0.8, y: 0.25), Point2(x: 0.5, y: 0.8)]
        var blobs: [Point2] = []
        for centre in centres {
            for _ in 0..<40 {
                let x = centre.x + (random.nextDouble() - 0.5) * 0.18
                let y = centre.y + (random.nextDouble() - 0.5) * 0.18
                blobs.append(Point2(x: x, y: y))
            }
        }
        var rings: [Point2] = []
        for (count, base) in [(60, 0.12), (90, 0.38)] {
            for _ in 0..<count {
                let angle = random.nextDouble() * 2 * .pi
                let radius = base + random.nextDouble() * 0.04
                rings.append(Point2(x: 0.5 + radius * cos(angle), y: 0.5 + radius * sin(angle)))
            }
        }
        return (blobs, rings)
    }()

    static var blobs: [Point2] { datasets.0 }
    static var rings: [Point2] { datasets.1 }

    private static func distance(_ a: Point2, _ b: Point2) -> Double { ((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).squareRoot() }

    static let restarts = 10

    static func kMeans(_ data: [Point2], _ k: Int, seed: Int = 101) -> [Int] {
        let runs = (0..<restarts).map { runKMeans(data, k, seed + $0 * 17) }
        return runs[firstMinIndex(runs.map { inertia(data, $0) })]
    }

    private static func runKMeans(_ data: [Point2], _ k: Int, _ seed: Int) -> [Int] {
        var rng = KotlinRandom(seed: seed)
        var centres = Array(data.kotlinShuffled(&rng).prefix(k))
        var assignment = [Int](repeating: 0, count: data.count)
        for _ in 0..<40 {
            assignment = data.map { point in firstMinIndex(centres.map { distance(point, $0) }) }
            centres = (0..<k).map { cluster in
                let members = data.indices.filter { assignment[$0] == cluster }.map { data[$0] }
                if members.isEmpty { return centres[cluster] }
                return Point2(x: members.map(\.x).average, y: members.map(\.y).average)
            }
        }
        return assignment
    }

    static func inertia(_ data: [Point2], _ assignment: [Int]) -> Double {
        distinctInOrder(assignment).reduce(0.0) { acc, cluster in
            let members = data.indices.filter { assignment[$0] == cluster }.map { data[$0] }
            if members.isEmpty { return acc }
            let centre = Point2(x: members.map(\.x).average, y: members.map(\.y).average)
            return acc + members.reduce(0.0) { let d = distance($1, centre); return $0 + d * d }
        }
    }

    static func silhouettes(_ data: [Point2], _ assignment: [Int]) -> [Double] {
        let clusters = distinctInOrder(assignment)
        return data.indices.map { i in
            let own = assignment[i]
            let same = data.indices.filter { assignment[$0] == own && $0 != i }
            if same.isEmpty { return 0.0 }
            let a = same.reduce(0.0) { $0 + distance(data[i], data[$1]) } / Double(same.count)
            let b = clusters.filter { $0 != own }.map { other -> Double in
                let members = data.indices.filter { assignment[$0] == other }
                return members.reduce(0.0) { $0 + distance(data[i], data[$1]) } / Double(members.count)
            }.min()!
            return (b - a) / max(a, b)
        }
    }

    static func silhouetteScore(_ data: [Point2], _ assignment: [Int]) -> Double { silhouettes(data, assignment).average }

    static func daviesBouldin(_ data: [Point2], _ assignment: [Int]) -> Double {
        let clusters = distinctInOrder(assignment).sorted()
        let centroids = clusters.map { cluster -> Point2 in
            let members = data.indices.filter { assignment[$0] == cluster }.map { data[$0] }
            return Point2(x: members.map(\.x).average, y: members.map(\.y).average)
        }
        let spreads = clusters.enumerated().map { index, cluster -> Double in
            let members = data.indices.filter { assignment[$0] == cluster }.map { data[$0] }
            return members.reduce(0.0) { $0 + distance($1, centroids[index]) } / Double(members.count)
        }
        return clusters.indices.map { i in
            clusters.indices.filter { $0 != i }.map { j in (spreads[i] + spreads[j]) / distance(centroids[i], centroids[j]) }.max()!
        }.average
    }

    struct Sweep { let k: Int; let silhouette: Double; let daviesBouldin: Double }

    static func sweep(_ data: [Point2], _ range: ClosedRange<Int> = 2...6) -> [Sweep] {
        range.map { k in
            let assignment = kMeans(data, k)
            return Sweep(k: k, silhouette: silhouetteScore(data, assignment), daviesBouldin: daviesBouldin(data, assignment))
        }
    }

    static let blobSweep: [Sweep] = sweep(blobs)
    static let ringSweep: [Sweep] = sweep(rings)

    static func bestBySilhouette(_ sweep: [Sweep]) -> Int { sweep[firstMaxIndex(sweep.map(\.silhouette))].k }
    static func bestByDaviesBouldin(_ sweep: [Sweep]) -> Int { sweep[firstMinIndex(sweep.map(\.daviesBouldin))].k }

    static let ringTruth = 2

    static func negativeCount(_ data: [Point2], _ assignment: [Int]) -> Int { silhouettes(data, assignment).filter { $0 < 0 }.count }
}
