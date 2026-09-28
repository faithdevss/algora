import Foundation

// Port of PreprocessMath.kt (B8): scaling, encoding, imputation, SMOTE, outlier rules and two
// feature selectors, each scored by a model the rule affects. `leastSquares` / `predictLinear`
// live in StatsMath.swift. Seeded generators run in the order the Kotlin object initialisers do.

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

private func medianD(_ v: [Double]) -> Double {
    let s = v.sorted(), n = s.count
    return n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2
}

private func quantile(_ v: [Double], _ q: Double) -> Double {
    let s = v.sorted()
    let position = q * Double(s.count - 1)
    let lower = Int(position)
    let upper = min(lower + 1, s.count - 1)
    return s[lower] + (position - Double(lower)) * (s[upper] - s[lower])
}

private func correlation(_ a: [Double], _ b: [Double]) -> Double {
    let ma = mean(a), mb = mean(b)
    let cov = a.indices.reduce(0.0) { $0 + (a[$1] - ma) * (b[$1] - mb) }
    return cov / (a.reduce(0.0) { $0 + ($1 - ma) * ($1 - ma) } * b.reduce(0.0) { $0 + ($1 - mb) * ($1 - mb) }).squareRoot()
}

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

func meanSquaredError(_ coefficients: [Double], _ x: [[Double]], _ y: [Double]) -> Double {
    x.indices.reduce(0.0) { acc, i in let e = predictLinear(coefficients, x[i]) - y[i]; return acc + e * e } / Double(x.count)
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

// MARK: - Categorical encoding

enum EncodingLab {
    static let categories = ["red", "green", "blue", "yellow"]
    static let effects = ["red": 10.0, "green": 2.0, "blue": 9.0, "yellow": 1.0]

    struct Row { let category: String; let target: Double }

    static let rows: [Row] = {
        var random = KotlinRandom(seed: 23)
        return (0..<160).map { _ in
            let category = categories[random.nextInt(categories.count)]
            return Row(category: category, target: effects[category]! + random.nextDouble() * 2 - 1)
        }
    }()

    static let labelCodes = Dictionary(uniqueKeysWithValues: categories.enumerated().map { ($1, Double($0)) })

    static func labelEncoded(_ row: Row) -> [Double] { [labelCodes[row.category]!] }

    static func oneHotEncoded(_ row: Row, dropFirst: Bool = false) -> [Double] {
        (0..<(dropFirst ? categories.count - 1 : categories.count)).map { i in categories[dropFirst ? i + 1 : i] == row.category ? 1 : 0 }
    }

    struct Fit { let name: String; let error: Double; let columns: Int }

    static let linearFits: [Fit] = {
        let y = rows.map(\.target)
        let lx = rows.map { labelEncoded($0) }, ox = rows.map { oneHotEncoded($0) }, dx = rows.map { oneHotEncoded($0, dropFirst: true) }
        return [
            Fit(name: "label encoding", error: meanSquaredError(leastSquares(lx, y), lx, y), columns: 1),
            Fit(name: "one-hot", error: meanSquaredError(leastSquares(ox, y), ox, y), columns: categories.count),
            Fit(name: "one-hot, first level dropped", error: meanSquaredError(leastSquares(dx, y), dx, y), columns: categories.count - 1),
        ]
    }()

    /// A tree grown on the single label-coded column recovers the categories exactly.
    static func treeError(_ maxDepth: Int) -> Double {
        let y = rows.map(\.target), x = rows.map { labelCodes[$0.category]! }
        func sse(_ values: [Double]) -> Double { let m = mean(values); return values.reduce(0) { $0 + ($1 - m) * ($1 - m) } }
        func fit(_ indices: [Int], _ depth: Int) -> Double {
            let values = indices.map { y[$0] }
            if depth == 0 || indices.count < 2 || Set(values).count == 1 { return sse(values) }
            let distinct = Array(Set(indices.map { x[$0] })).sorted()
            let candidates = zip(distinct, distinct.dropFirst()).map { ($0 + $1) / 2 }
            if candidates.isEmpty { return sse(values) }
            return candidates.map { t -> Double in
                let left = indices.filter { x[$0] <= t }, right = indices.filter { x[$0] > t }
                return left.isEmpty || right.isEmpty ? .greatestFiniteMagnitude : fit(left, depth - 1) + fit(right, depth - 1)
            }.min()!
        }
        return fit(Array(rows.indices), maxDepth) / Double(rows.count)
    }

    static func codeDistance(_ a: String, _ b: String) -> Double { abs(labelCodes[a]! - labelCodes[b]!) }
    static func oneHotDistance() -> Double { 2.0.squareRoot() }
    static func columnsFor(_ levels: Int, dropFirst: Bool = false) -> Int { dropFirst ? levels - 1 : levels }
}

// MARK: - Missing values

enum ImputationLab {
    static let missingRate = 0.30

    private static let generated: ([Double], [Double], [Bool]) = {
        var random = KotlinRandom(seed: 29)
        var x: [Double] = [], y: [Double] = []
        for _ in 0..<200 {
            let base = random.nextDouble() * 10
            x.append(base)
            y.append(2 * base + random.nextDouble() * 4 - 2)
        }
        let missing = (0..<200).map { _ in random.nextDouble() < missingRate }
        return (x, y, missing)
    }()

    static var complete: [Double] { generated.0 }
    static var partner: [Double] { generated.1 }
    static var missing: [Bool] { generated.2 }

    static let observedCount = missing.filter { !$0 }.count
    private static let observed = complete.indices.filter { !missing[$0] }.map { complete[$0] }
    static let meanFill = mean(observed)
    static let medianFill = medianD(observed)

    static func imputed(_ fill: Double) -> [Double] { complete.indices.map { missing[$0] ? fill : complete[$0] } }

    struct Effect { let name: String; let variance: Double; let correlation: Double; let rows: Int }

    static let effects: [Effect] = {
        let kept = complete.indices.filter { !missing[$0] }
        return [
            Effect(name: "complete data", variance: variance(complete), correlation: correlation(complete, partner), rows: complete.count),
            Effect(name: "drop the rows", variance: variance(kept.map { complete[$0] }), correlation: correlation(kept.map { complete[$0] }, kept.map { partner[$0] }), rows: kept.count),
            Effect(name: "mean imputation", variance: variance(imputed(meanFill)), correlation: correlation(imputed(meanFill), partner), rows: complete.count),
            Effect(name: "median imputation", variance: variance(imputed(medianFill)), correlation: correlation(imputed(medianFill), partner), rows: complete.count),
        ]
    }()

    static func predictedVarianceRatio(_ rate: Double = missingRate) -> Double { 1 - rate }

    static let skewed: [Double] = {
        var rng = KotlinRandom(seed: 31)
        return (0..<200).map { _ in rng.nextDouble() < 0.9 ? rng.nextDouble() * 10 : 200 + rng.nextDouble() * 300 }
    }()

    static let skewedCentre = (mean: mean(skewed), median: medianD(skewed))
}

// MARK: - SMOTE

enum SmoteLab {
    static let k = 5
    /// The leak is measured at 1-NN, the classifier that reads a synthetic neighbour directly.
    static let leakK = 1

    private static let generated: ([Sample], [Sample]) = {
        var random = KotlinRandom(seed: 37)
        let majority = (0..<120).map { _ -> Sample in
            let a = random.nextDouble() * 4 + 1
            let b = random.nextDouble() * 4 + 1
            return Sample([a, b], 0)
        }
        let minority = (0..<12).map { _ -> Sample in
            let a = random.nextDouble() * 1.6 + 3.4
            let b = random.nextDouble() * 1.6 + 3.4
            return Sample([a, b], 1)
        }
        return (majority, minority)
    }()

    static var majority: [Sample] { generated.0 }
    static var minority: [Sample] { generated.1 }
    static let imbalanceRatio = Double(majority.count) / Double(minority.count)

    static func synthesise(_ pool: [Sample], _ count: Int, seed: Int = 41) -> [Sample] {
        var rng = KotlinRandom(seed: seed)
        return (0..<count).map { _ in
            let origin = pool[rng.nextInt(pool.count)]
            let neighbours = pool.filter { $0 !== origin }.sortedBy { c in
                c.features.indices.reduce(0.0) { acc, i in let d = c.features[i] - origin.features[i]; return acc + d * d }
            }.prefix(k)
            let neighbour = Array(neighbours)[rng.nextInt(neighbours.count)]
            let gap = rng.nextDouble()
            return Sample(origin.features.indices.map { origin.features[$0] + gap * (neighbour.features[$0] - origin.features[$0]) }, 1)
        }
    }

    struct Scores { let name: String; let accuracy: Double; let minorityRecall: Double; let precision: Double }

    private static func score(_ name: String, _ train: [Sample], _ test: [Sample]) -> Scores {
        let predictions = test.map { knnPredict(train, $0.features, k: k) }
        let tp = test.indices.filter { predictions[$0] == 1 && test[$0].label == 1 }.count
        let fp = test.indices.filter { predictions[$0] == 1 && test[$0].label == 0 }.count
        let positives = test.filter { $0.label == 1 }.count
        return Scores(name: name, accuracy: Double(test.indices.filter { predictions[$0] == test[$0].label }.count) / Double(test.count),
                      minorityRecall: positives == 0 ? 0 : Double(tp) / Double(positives),
                      precision: tp + fp == 0 ? 0 : Double(tp) / Double(tp + fp))
    }

    private static let split: ([Sample], [Sample]) = {
        var rng = KotlinRandom(seed: 43)
        let maj = majority.kotlinShuffled(&rng)
        let mino = minority.kotlinShuffled(&rng)
        return (Array(maj.prefix(90)) + Array(mino.prefix(9)), Array(maj.dropFirst(90)) + Array(mino.dropFirst(9)))
    }()

    static var trainSet: [Sample] { split.0 }
    static var testSet: [Sample] { split.1 }

    static let resampled: [Sample] = trainSet + synthesise(trainSet.filter { $0.label == 1 }, trainSet.filter { $0.label == 0 }.count - trainSet.filter { $0.label == 1 }.count)

    static let scores: [Scores] = [score("imbalanced", trainSet, testSet), score("SMOTE on the training fold", resampled, testSet)]

    struct Leak { let leakyScore: Double; let honestScore: Double; let folds: Int; let neighbours: Int }

    static let leak: Leak = {
        let folds = 5
        let everything = majority + minority
        let inflated = everything + synthesise(minority, majority.count - minority.count, seed: 47)
        var r1 = KotlinRandom(seed: 53), r2 = KotlinRandom(seed: 53)
        let shuffledLeaky = inflated.kotlinShuffled(&r1)
        let shuffledHonest = everything.kotlinShuffled(&r2)
        func crossValidate(_ data: [Sample], _ resampleInside: Bool) -> Double {
            let size = data.count / folds
            return (0..<folds).reduce(0.0) { acc, fold in
                let validation = Array(data.dropFirst(fold * size).prefix(size))
                let held = Set(validation.map(ObjectIdentifier.init))
                var training = data.filter { !held.contains(ObjectIdentifier($0)) }
                if resampleInside {
                    let minorityTrain = training.filter { $0.label == 1 }
                    let need = training.filter { $0.label == 0 }.count - minorityTrain.count
                    if minorityTrain.count > k && need > 0 { training += synthesise(minorityTrain, need, seed: 59 + fold) }
                }
                let predictions = validation.map { knnPredict(training, $0.features, k: leakK) }
                return acc + Double(validation.indices.filter { predictions[$0] == validation[$0].label }.count) / Double(validation.count)
            } / Double(folds)
        }
        return Leak(leakyScore: crossValidate(shuffledLeaky, false), honestScore: crossValidate(shuffledHonest, true), folds: folds, neighbours: leakK)
    }()
}

// MARK: - Outlier detection

enum OutlierLab {
    static let zThreshold = 3.0
    static let iqrFactor = 1.5
    static let extreme = 4000.0

    static let clean: [Double] = {
        var random = KotlinRandom(seed: 61)
        return (0..<60).map { _ in 50 + random.nextDouble() * 20 }
    }()

    static let contaminated = clean + [4000.0]

    /// Extremes added until the largest z-score falls under the threshold.
    static let maskingCount = (1...clean.count).first { selfZScore(clean + [Double](repeating: extreme, count: $0)) <= zThreshold }!
    static let masked = clean + [Double](repeating: extreme, count: maskingCount)
    static let maskingShare = Double(maskingCount) / Double(masked.count)

    struct Verdict { let rule: String; let flagged: [Double]; let threshold: String }

    static func zScoreFlags(_ data: [Double]) -> Verdict {
        let mu = mean(data), sigma = standardDeviation(data)
        return Verdict(rule: "z-score", flagged: data.filter { abs($0 - mu) / sigma > zThreshold }, threshold: "|x − \(fx(mu, 1))| / \(fx(sigma, 1)) > \(zThreshold)")
    }

    static func iqrFlags(_ data: [Double]) -> Verdict {
        let q1 = quantile(data, 0.25), q3 = quantile(data, 0.75)
        let lower = q1 - iqrFactor * (q3 - q1), upper = q3 + iqrFactor * (q3 - q1)
        return Verdict(rule: "IQR", flagged: data.filter { $0 < lower || $0 > upper }, threshold: "outside [\(fx(lower, 1)), \(fx(upper, 1))]")
    }

    static func modifiedZFlags(_ data: [Double], threshold: Double = 3.5) -> Verdict {
        let med = medianD(data)
        let mad = medianD(data.map { abs($0 - med) })
        return Verdict(rule: "modified z (MAD)", flagged: data.filter { 0.6745 * abs($0 - med) / mad > threshold }, threshold: "0.6745·|x − \(fx(med, 1))| / \(fx(mad, 1)) > \(threshold)")
    }

    static func selfZScore(_ data: [Double] = contaminated) -> Double { abs(data.max()! - mean(data)) / standardDeviation(data) }

    /// A single point in a sample of n can never have |z| above (n−1)/√n.
    static func maxPossibleZ(_ n: Int) -> Double { Double(n - 1) / Double(n).squareRoot() }

    static let smallestUsableSample = (2...200).first { maxPossibleZ($0) > zThreshold }!

    static func fences(_ data: [Double]) -> (Double, Double) {
        let q1 = quantile(data, 0.25), q3 = quantile(data, 0.75)
        return (q1 - iqrFactor * (q3 - q1), q3 + iqrFactor * (q3 - q1))
    }

    static let breakdownPoints: [(String, Double)] = [("mean/σ (z-score)", 0.0), ("median/MAD", 0.5), ("quartiles (IQR)", 0.25)]
}

// MARK: - Feature selection

enum FeatureSelectionLab {
    static let featureNames = ["useful", "duplicate", "noise", "xorA", "xorB"]

    struct Instance { let binary: [Int]; let label: Int }

    private static let generated: ([Instance], [Instance]) = {
        var random = KotlinRandom(seed: 67)
        let data = (0..<400).map { _ -> Instance in
            let useful = random.nextInt(2)
            let xorA = random.nextInt(2)
            let xorB = random.nextInt(2)
            let flip = random.nextDouble() < 0.1
            let label = flip ? 1 - useful : useful
            let duplicate = random.nextDouble() < 0.9 ? useful : 1 - useful
            let noise = random.nextInt(2)
            return Instance(binary: [useful, duplicate, noise, xorA, xorB], label: label)
        }
        let xorData = (0..<400).map { _ -> Instance in
            let a = random.nextInt(2)
            let b = random.nextInt(2)
            let n0 = random.nextInt(2), n1 = random.nextInt(2), n2 = random.nextInt(2)
            return Instance(binary: [n0, n1, n2, a, b], label: a ^ b)
        }
        return (data, xorData)
    }()

    static var data: [Instance] { generated.0 }
    static var xorData: [Instance] { generated.1 }

    struct Contingency { let counts: [[Int]]; let chiSquare: Double }

    static func chiSquare(_ data: [Instance], _ feature: Int) -> Contingency {
        var counts = [[0, 0], [0, 0]]
        for d in data { counts[d.binary[feature]][d.label] += 1 }
        let total = Double(data.count)
        var chi = 0.0
        for i in 0...1 {
            for j in 0...1 {
                let expected = Double(counts[i].reduce(0, +)) * Double(counts[0][j] + counts[1][j]) / total
                if expected > 0 { let diff = Double(counts[i][j]) - expected; chi += diff * diff / expected }
            }
        }
        return Contingency(counts: counts, chiSquare: chi)
    }

    static func chiSquareRanking(_ data: [Instance] = data) -> [(String, Double)] {
        featureNames.indices.map { (featureNames[$0], chiSquare(data, $0).chiSquare) }.sortedByDescending { $0.1 }
    }

    struct Round { let dropped: String; let remaining: [String]; let error: Double; let coefficients: [(String, Double)] }

    /// Fit, drop the smallest absolute coefficient, repeat.
    static func recursiveElimination(_ data: [Instance] = data) -> [Round] {
        var remaining = Array(featureNames.indices)
        let y = data.map { Double($0.label) }
        var rounds: [Round] = []
        while remaining.count > 1 {
            let x = data.map { inst in remaining.map { Double(inst.binary[$0]) } }
            let fit = leastSquares(x, y)
            let coefficients = remaining.indices.map { (featureNames[remaining[$0]], fit[$0 + 1]) }
            var weakest = 0
            for i in remaining.indices where abs(fit[i + 1]) < abs(fit[weakest + 1]) { weakest = i }
            rounds.append(Round(dropped: featureNames[remaining[weakest]], remaining: remaining.map { featureNames[$0] }, error: meanSquaredError(fit, x, y), coefficients: coefficients))
            remaining.remove(at: weakest)
        }
        return rounds
    }

    static func fitsRequired(_ features: Int) -> Int { features - 1 }
    static func chiSquareFits() -> Int { 0 }
}
