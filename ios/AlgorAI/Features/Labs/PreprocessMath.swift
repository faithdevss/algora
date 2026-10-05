import Foundation

// Port of PreprocessMath.kt (B8): the feature-scaling rule scored by a k-NN model, read by the z-score
// and min-max storyboards. `leastSquares` / `predictLinear` live in StatsMath.swift. Seeded generators
// run in the order the Kotlin object initialisers do.

final class Sample {
    let features: [Double]
    let label: Int
    init(_ features: [Double], _ label: Int) { self.features = features; self.label = label }
}


private func variance(_ v: [Double]) -> Double {
    let m = mean(v)
    return v.reduce(0) { $0 + ($1 - m) * ($1 - m) } / Double(v.count - 1)
}

private func standardDeviation(_ v: [Double]) -> Double { variance(v).squareRoot() }

/// k-NN, majority vote with ties going to the label met first among the nearest.
func knnPredict(_ train: [Sample], _ query: [Double], k: Int = 5) -> Int {
    let nearest = train.sortedBy { s in s.features.indices.reduce(0.0) { acc, i in let d = s.features[i] - query[i]; return acc + d * d } }.prefix(k)
    var order: [Int] = []
    var counts: [Int: Int] = [:]
    for s in nearest {
        if counts[s.label] == nil { order.append(s.label) }
        counts[s.label, default: 0] += 1
    }
    return order[argmaxFirst(order.map { counts[$0]! })]
}

func knnAccuracy(_ train: [Sample], _ test: [Sample], k: Int = 5) -> Double {
    Double(test.filter { knnPredict(train, $0.features, k: k) == $0.label }.count) / Double(test.count)
}

// MARK: - Scaling

enum FeatureScalingLab {
    static let featureNames = ["age (years)", "income ($)"]

    private static let data: ([Sample], [Sample]) = {
        var random = KotlinRandom(seed: 17)
        func generate(_ count: Int) -> [Sample] {
            (0..<count).map { _ in
                let age = 20 + random.nextDouble() * 50
                let income = 20_000 + random.nextDouble() * 130_000
                let score = (age - 45) / 25.0 + (income - 85_000) / 65_000.0 + random.nextDouble() * 0.4 - 0.2
                return Sample([age, income], score > 0 ? 1 : 0)
            }
        }
        let train = generate(120)
        return (train, generate(80))
    }()

    static var train: [Sample] { data.0 }
    static var test: [Sample] { data.1 }

    private static func columns(_ d: [Sample], _ i: Int) -> [Double] { d.map { $0.features[i] } }

    static func minMax(_ fit: [Sample], _ apply: [Sample]) -> [Sample] {
        let n = fit[0].features.count
        let lo = (0..<n).map { columns(fit, $0).min()! }, hi = (0..<n).map { columns(fit, $0).max()! }
        return apply.map { s in Sample((0..<s.features.count).map { (s.features[$0] - lo[$0]) / (hi[$0] - lo[$0]) }, s.label) }
    }

    static func zScore(_ fit: [Sample], _ apply: [Sample]) -> [Sample] {
        let n = fit[0].features.count
        let mu = (0..<n).map { mean(columns(fit, $0)) }, sigma = (0..<n).map { standardDeviation(columns(fit, $0)) }
        return apply.map { s in Sample((0..<s.features.count).map { (s.features[$0] - mu[$0]) / sigma[$0] }, s.label) }
    }

    struct Result { let name: String; let accuracy: Double; let ranges: [Double] }

    private static func ranges(_ d: [Sample]) -> [Double] { d[0].features.indices.map { columns(d, $0).max()! - columns(d, $0).min()! } }

    static let results: [Result] = [
        Result(name: "raw", accuracy: knnAccuracy(train, test), ranges: ranges(train)),
        Result(name: "min-max", accuracy: knnAccuracy(minMax(train, train), minMax(train, test)), ranges: ranges(minMax(train, train))),
        Result(name: "z-score", accuracy: knnAccuracy(zScore(train, train), zScore(train, test)), ranges: ranges(zScore(train, train))),
    ]

    /// Mean share of squared distance contributed by each column.
    static func distanceShare(_ reference: [Sample], _ queries: [Sample]) -> [Double] {
        var totals = [0.0, 0.0]
        for q in queries.prefix(20) {
            for s in reference {
                for i in s.features.indices {
                    let d = s.features[i] - q.features[i]
                    totals[i] += d * d
                }
            }
        }
        let sum = totals.reduce(0, +)
        return totals.map { $0 / sum }
    }

    static let distanceShares: [(String, [Double])] = [
        ("raw", distanceShare(train, test)),
        ("min-max", distanceShare(minMax(train, train), minMax(train, test))),
        ("z-score", distanceShare(zScore(train, train), zScore(train, test))),
    ]

    struct OutlierEffect { let minMaxSpan: Double; let zScoreSpan: Double; let outlierIncome: Double }

    static let outlierEffect: OutlierEffect = {
        let extreme = Sample([45, 5_000_000], 1)
        let contaminated = train + [extreme]
        let mm = columns(minMax(contaminated, train), 1), zs = columns(zScore(contaminated, train), 1)
        return OutlierEffect(minMaxSpan: mm.max()! - mm.min()!, zScoreSpan: zs.max()! - zs.min()!, outlierIncome: extreme.features[1])
    }()
}
