import Foundation

// Port of SpecializedMath.kt (C9): Siamese networks, GCN, GAT, capsule routing, neural ODEs and
// KAN. The experiments train from the same kotlin.random seeds Android uses.

private func specGaussian(_ random: inout KotlinRandom) -> Double {
    let u1 = max(random.nextDouble(), 1e-12)
    let u2 = random.nextDouble()
    return (-2.0 * log(u1)).squareRoot() * cos(2.0 * .pi * u2)
}

private func euclid(_ a: [Double], _ b: [Double]) -> Double {
    var s = 0.0
    for i in a.indices { s += (a[i] - b[i]) * (a[i] - b[i]) }
    return s.squareRoot()
}

private func firstMin(_ n: Int, _ key: (Int) -> Double) -> Int {
    var best = 0
    for i in 1..<max(n, 1) where key(i) < key(best) { best = i }
    return best
}

// MARK: - Siamese networks

enum SiameseLab {
    static let signalDim = 2
    static let featureDim = 3
    static let embedDim = 2
    static let margin = 2.0

    static let classPrototypes: [[Double]] = [[2.0, 2.0], [2.0, -2.0], [-2.0, 2.0], [-2.0, -2.0]]
    static let nuisanceStd = 5.0
    static let signalNoiseStd = 0.3

    static func sample(_ classId: Int, _ random: inout KotlinRandom) -> [Double] {
        let p = classPrototypes[classId]
        let a = p[0] + signalNoiseStd * specGaussian(&random)
        let b = p[1] + signalNoiseStd * specGaussian(&random)
        let c = nuisanceStd * specGaussian(&random)
        return [a, b, c]
    }

    struct Embedder {
        let w: [[Double]]
        let b: [Double]
        func embed(_ x: [Double]) -> [Double] {
            (0..<SiameseLab.embedDim).map { j in
                var z = b[j]
                for k in 0..<SiameseLab.featureDim { z += w[j][k] * x[k] }
                return tanh(z)
            }
        }
    }

    static func distance(_ a: [Double], _ b: [Double]) -> Double { euclid(a, b) }

    private static func pre(_ w: [[Double]], _ b: [Double], _ x: [Double]) -> [Double] {
        (0..<embedDim).map { j in
            var s = 0.0
            for k in 0..<featureDim { s += w[j][k] * x[k] }
            return b[j] + s
        }
    }

    /// Trains on classes 0, 1, 2 only; class 3 is never seen until evaluation.
    static func train(steps: Int = 20_000, learningRate: Double = 0.05, seed: Int = 13) -> Embedder {
        var random = KotlinRandom(seed: seed)
        var w = (0..<embedDim).map { _ in (0..<featureDim).map { _ in 0.1 * specGaussian(&random) } }
        var b = [Double](repeating: 0, count: embedDim)
        let trainClasses = [0, 1, 2]

        for _ in 0..<steps {
            let same = random.nextBoolean()
            let classA = trainClasses[random.nextInt(trainClasses.count)]
            let others = trainClasses.filter { $0 != classA }
            let classB = same ? classA : others[random.nextInt(others.count)]
            let xa = sample(classA, &random)
            let xb = sample(classB, &random)

            let fa = pre(w, b, xa).map { tanh($0) }
            let fb = pre(w, b, xb).map { tanh($0) }
            let diff = (0..<embedDim).map { fa[$0] - fb[$0] }
            let d = max(diff.reduce(0.0) { $0 + $1 * $1 }.squareRoot(), 1e-9)

            let dLdFa: [Double], dLdFb: [Double]
            if same {
                dLdFa = diff.map { 2.0 * $0 }
                dLdFb = diff.map { -2.0 * $0 }
            } else if d < margin {
                let coeff = -2.0 * (margin - d) / d
                dLdFa = diff.map { coeff * $0 }
                dLdFb = diff.map { -coeff * $0 }
            } else {
                dLdFa = [Double](repeating: 0, count: embedDim)
                dLdFb = [Double](repeating: 0, count: embedDim)
            }
            let dLdZa = (0..<embedDim).map { dLdFa[$0] * (1 - fa[$0] * fa[$0]) }
            let dLdZb = (0..<embedDim).map { dLdFb[$0] * (1 - fb[$0] * fb[$0]) }

            for j in 0..<embedDim {
                for k in 0..<featureDim { w[j][k] -= learningRate * (dLdZa[j] * xa[k] + dLdZb[j] * xb[k]) }
                b[j] -= learningRate * (dLdZa[j] + dLdZb[j])
            }
        }
        return Embedder(w: w, b: b)
    }

    static let trainedEmbedder: Embedder = train()

    struct OneShotResult { let rawAccuracy: Double; let embeddedAccuracy: Double }

    static func oneShotEvaluate(queriesPerClass: Int = 60, seed: Int = 71) -> OneShotResult {
        var random = KotlinRandom(seed: seed)
        let embedder = trainedEmbedder
        let supports = classPrototypes.indices.map { sample($0, &random) }
        let embeddedSupports = supports.map { embedder.embed($0) }
        var rawCorrect = 0, embeddedCorrect = 0, total = 0
        for classId in classPrototypes.indices {
            for _ in 0..<queriesPerClass {
                let query = sample(classId, &random)
                let embeddedQuery = embedder.embed(query)
                let rawPrediction = firstMin(supports.count) { distance(query, supports[$0]) }
                let embeddedPrediction = firstMin(embeddedSupports.count) { distance(embeddedQuery, embeddedSupports[$0]) }
                if rawPrediction == classId { rawCorrect += 1 }
                if embeddedPrediction == classId { embeddedCorrect += 1 }
                total += 1
            }
        }
        return OneShotResult(rawAccuracy: Double(rawCorrect) / Double(total), embeddedAccuracy: Double(embeddedCorrect) / Double(total))
    }

    static let result: OneShotResult = oneShotEvaluate()
}

// MARK: - Shared small graph

/// Two triangles (0,1,2) and (3,4,5), bridged by the edge 2–3.
enum SmallGraph {
    static let edges = [(0, 1), (1, 2), (0, 2), (3, 4), (4, 5), (3, 5), (2, 3)]
    static let nodes = 6

    static let adjacency: [[Double]] = {
        var a = [[Double]](repeating: [Double](repeating: 0, count: nodes), count: nodes)
        for (u, v) in edges { a[u][v] = 1; a[v][u] = 1 }
        return a
    }()

    static let adjacencyWithSelfLoops: [[Double]] = (0..<nodes).map { i in (0..<nodes).map { j in adjacency[i][j] + (i == j ? 1.0 : 0.0) } }

    static let degrees: [Double] = adjacencyWithSelfLoops.map { $0.reduce(0, +) }

    /// D^-1/2 Â D^-1/2.
    static let normalizedAdjacency: [[Double]] = (0..<nodes).map { i in
        (0..<nodes).map { j in adjacencyWithSelfLoops[i][j] / (degrees[i] * degrees[j]).squareRoot() }
    }

    static func neighborsOf(_ node: Int) -> [Int] { (0..<nodes).filter { $0 != node && adjacency[node][$0] > 0 } }
}

// MARK: - GCN

enum GcnLab {
    static let featureDim = 2

    static func initialFeatures(seed: Int = 5) -> [[Double]] {
        var random = KotlinRandom(seed: seed)
        return (0..<SmallGraph.nodes).map { i in
            let groupSign = i < 3 ? 1.0 : -1.0
            let a = groupSign * 2.0 + 0.2 * specGaussian(&random)
            let b = 0.2 * specGaussian(&random)
            return [a, b]
        }
    }

    static func propagate(_ features: [[Double]]) -> [[Double]] {
        let a = SmallGraph.normalizedAdjacency
        return (0..<SmallGraph.nodes).map { i in
            (0..<featureDim).map { d in
                var s = 0.0
                for j in 0..<SmallGraph.nodes { s += a[i][j] * features[j][d] }
                return s
            }
        }
    }

    static func layers(_ initial: [[Double]], depth: Int) -> [[[Double]]] {
        var out = [initial]
        for _ in 0..<depth { out.append(propagate(out.last!)) }
        return out
    }

    static func distance(_ a: [Double], _ b: [Double]) -> Double { euclid(a, b) }

    /// Mean cross-triangle distance over mean within-triangle distance.
    static func separation(_ features: [[Double]]) -> Double {
        let groupA = [0, 1, 2], groupB = [3, 4, 5]
        func meanPairDistance(_ indices: [Int]) -> Double {
            var ds: [Double] = []
            for i in indices.indices { for j in (i + 1)..<indices.count { ds.append(distance(features[indices[i]], features[indices[j]])) } }
            return ds.average
        }
        let within = (meanPairDistance(groupA) + meanPairDistance(groupB)) / 2.0
        let across = groupA.flatMap { u in groupB.map { v in distance(features[u], features[v]) } }.average
        return across / within
    }

    static let propagationTrace: [[[Double]]] = layers(initialFeatures(), depth: 20)
    static let separationByDepth: [Double] = propagationTrace.map(separation)
}

// MARK: - GAT

enum GatLab {
    static let leakySlope = 0.2
    static let centerNode = 2
    static let neighbors = SmallGraph.neighborsOf(centerNode)

    static let baseFeatures: [Int: [Double]] = [2: [0.9, 0.1], 0: [1.0, 0.3], 1: [0.8, -0.2], 3: [-1.1, 0.4]]

    private static func leakyRelu(_ x: Double) -> Double { x >= 0 ? x : leakySlope * x }

    static func attentionScore(_ center: [Double], _ neighbor: [Double]) -> Double { leakyRelu(center[0] + center[1] + neighbor[0] + neighbor[1]) }

    static func softmax(_ scores: [Double]) -> [Double] {
        let m = scores.max()!
        let exps = scores.map { exp($0 - m) }
        let total = exps.reduce(0, +)
        return exps.map { $0 / total }
    }

    static func attentionWeights(_ features: [Int: [Double]]) -> [Int: Double] {
        let center = features[centerNode]!
        let weights = softmax(neighbors.map { attentionScore(center, features[$0]!) })
        return Dictionary(uniqueKeysWithValues: zip(neighbors, weights))
    }

    static func gcnWeights() -> [Int: Double] { Dictionary(uniqueKeysWithValues: neighbors.map { ($0, SmallGraph.normalizedAdjacency[centerNode][$0]) }) }

    static let baseAttention: [Int: Double] = attentionWeights(baseFeatures)
    static let baseGcn: [Int: Double] = gcnWeights()

    static let perturbedFeatures: [Int: [Double]] = baseFeatures.merging([3: baseFeatures[centerNode]!]) { $1 }
    static let perturbedAttention: [Int: Double] = attentionWeights(perturbedFeatures)
}

// MARK: - Capsule networks

enum CapsuleLab {
    static let votesA: [[Double]] = [[1.0, 0.0], [0.9, 0.2], [-0.8, 0.3]]
    static let votesB: [[Double]] = [[0.1, 0.9], [-0.9, 0.1], [0.85, -0.3]]

    static func squash(_ v: [Double]) -> [Double] {
        let normSq = v.reduce(0.0) { $0 + $1 * $1 }
        let n = max(normSq.squareRoot(), 1e-9)
        let scale = normSq / (1.0 + normSq)
        return v.map { scale * $0 / n }
    }

    static func norm(_ v: [Double]) -> Double { v.reduce(0.0) { $0 + $1 * $1 }.squareRoot() }

    static func dot(_ a: [Double], _ b: [Double]) -> Double {
        var s = 0.0
        for i in a.indices { s += a[i] * b[i] }
        return s
    }

    struct Round { let cA: [Double]; let cB: [Double]; let vA: [Double]; let vB: [Double] }

    static func route(iterations: Int = 3) -> [Round] {
        var bA = [Double](repeating: 0, count: votesA.count)
        var bB = [Double](repeating: 0, count: votesA.count)
        var rounds: [Round] = []
        for _ in 0..<iterations {
            let cA = bA.indices.map { exp(bA[$0]) / (exp(bA[$0]) + exp(bB[$0])) }
            let cB = cA.map { 1.0 - $0 }
            let sA = (0..<2).map { d in votesA.indices.reduce(0.0) { $0 + cA[$1] * votesA[$1][d] } }
            let sB = (0..<2).map { d in votesB.indices.reduce(0.0) { $0 + cB[$1] * votesB[$1][d] } }
            let vA = squash(sA), vB = squash(sB)
            for i in votesA.indices {
                bA[i] += dot(votesA[i], vA)
                bB[i] += dot(votesB[i], vB)
            }
            rounds.append(Round(cA: cA, cB: cB, vA: vA, vB: vB))
        }
        return rounds
    }

    static let rounds: [Round] = route()

    static var naiveAverageA: [Double] { (0..<2).map { d in votesA.reduce(0.0) { $0 + $1[d] } / Double(votesA.count) } }
    static var naiveAverageLength: Double { norm(naiveAverageA) }
}

// MARK: - Neural ODEs

enum NeuralOdeLab {
    static let k = 1.0
    static let z0 = 1.0
    static let t = 2.0

    static func exact(_ time: Double) -> Double { z0 * exp(-k * time) }
    static func f(_ z: Double) -> Double { -k * z }

    static func euler(_ steps: Int) -> Double {
        var z = z0
        let h = t / Double(steps)
        for _ in 0..<steps { z += h * f(z) }
        return z
    }

    static func rk4(_ steps: Int) -> Double {
        var z = z0
        let h = t / Double(steps)
        for _ in 0..<steps {
            let k1 = f(z)
            let k2 = f(z + h / 2 * k1)
            let k3 = f(z + h / 2 * k2)
            let k4 = f(z + h * k3)
            z += h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
        }
        return z
    }

    static let stepCounts = [5, 10, 20, 40, 80]
    static let eulerErrors: [Double] = stepCounts.map { abs(euler($0) - exact(t)) }
    static let rk4Errors: [Double] = stepCounts.map { abs(rk4($0) - exact(t)) }

    static func eulerTrajectory(_ steps: Int) -> [(Double, Double)] {
        var z = z0
        let h = t / Double(steps)
        var points = [(0.0, z)]
        for i in 0..<steps {
            z += h * f(z)
            points.append((Double(i + 1) * h, z))
        }
        return points
    }

    static func exactTrajectory(_ points: Int) -> [(Double, Double)] {
        let h = t / Double(points)
        return (0...points).map { (Double($0) * h, exact(Double($0) * h)) }
    }

    static let resNetLayers = 50
    static let stateDim = 64
    static var resNetStoredFloats: Int64 { Int64(resNetLayers) * Int64(stateDim) }
    static var adjointStoredFloats: Int64 { 2 * Int64(stateDim) }
    static var memoryRatio: Double { Double(resNetStoredFloats) / Double(adjointStoredFloats) }
}

// MARK: - KAN

enum KanLab {
    static func target(_ x: Double) -> Double { sin(5.0 * x) * exp(-x * x / 2.0) }

    static func xs(_ n: Int, seed: Int) -> [Double] {
        var random = KotlinRandom(seed: seed)
        return (0..<n).map { _ in -2.0 + 4.0 * random.nextDouble() }
    }

    static let trainXs = xs(40, seed: 21)
    static let trainYs = trainXs.map(target)
    static let testXs = xs(40, seed: 22)
    static let testYs = testXs.map(target)

    struct Mlp {
        let w1: [Double], b1: [Double], w2: [Double], b2: Double
        var paramCount: Int { w1.count + b1.count + w2.count + 1 }
        func predict(_ x: Double) -> Double {
            var out = b2
            for h in w1.indices { out += w2[h] * tanh(w1[h] * x + b1[h]) }
            return out
        }
    }

    static func trainMlp(_ hidden: Int, steps: Int = 30_000, lr: Double = 0.02, seed: Int = 31) -> Mlp {
        var random = KotlinRandom(seed: seed)
        var w1 = (0..<hidden).map { _ in 1.0 * specGaussian(&random) }
        var b1 = (0..<hidden).map { _ in 0.1 * specGaussian(&random) }
        var w2 = (0..<hidden).map { _ in 0.1 * specGaussian(&random) }
        var b2 = 0.0
        let n = trainXs.count
        for _ in 0..<steps {
            let i = random.nextInt(n)
            let x = trainXs[i], y = trainYs[i]
            let act = (0..<hidden).map { tanh(w1[$0] * x + b1[$0]) }
            var pred = b2
            for h in 0..<hidden { pred += w2[h] * act[h] }
            let err = pred - y
            for h in 0..<hidden {
                let dAct = err * w2[h] * (1 - act[h] * act[h])
                w1[h] -= lr * dAct * x
                b1[h] -= lr * dAct
                w2[h] -= lr * err * act[h]
            }
            b2 -= lr * err
        }
        return Mlp(w1: w1, b1: b1, w2: w2, b2: b2)
    }

    struct Kan {
        let knotValues: [Double]
        var xMin = -2.0
        var xMax = 2.0
        var paramCount: Int { knotValues.count }
        func predict(_ x: Double) -> Double {
            let spacing = (xMax - xMin) / Double(knotValues.count - 1)
            let clamped = min(max(x, xMin), xMax)
            let position = (clamped - xMin) / spacing
            let lower = min(max(Int(position), 0), knotValues.count - 2)
            let t = position - Double(lower)
            return knotValues[lower] * (1 - t) + knotValues[lower + 1] * t
        }
    }

    static func trainKan(_ knots: Int, steps: Int = 30_000, lr: Double = 0.3, seed: Int = 31) -> Kan {
        var random = KotlinRandom(seed: seed)
        var knotValues = [Double](repeating: 0, count: knots)
        let spacing = 4.0 / Double(knots - 1)
        let n = trainXs.count
        for _ in 0..<steps {
            let i = random.nextInt(n)
            let x = trainXs[i], y = trainYs[i]
            let clamped = min(max(x, -2.0), 2.0)
            let position = (clamped + 2.0) / spacing
            let lower = min(max(Int(position), 0), knots - 2)
            let t = position - Double(lower)
            let pred = knotValues[lower] * (1 - t) + knotValues[lower + 1] * t
            let err = pred - y
            knotValues[lower] -= lr * err * (1 - t)
            knotValues[lower + 1] -= lr * err * t
        }
        return Kan(knotValues: knotValues)
    }

    static func mse(_ predict: (Double) -> Double, _ xs: [Double], _ ys: [Double]) -> Double {
        var total = 0.0
        for i in xs.indices { let e = predict(xs[i]) - ys[i]; total += e * e }
        return total / Double(xs.count)
    }

    static let hidden = 5
    static let knots = 16

    static let trainedMlp: Mlp = trainMlp(hidden)
    static let trainedKan: Kan = trainKan(knots)

    static let mlpTestMse: Double = mse(trainedMlp.predict, testXs, testYs)
    static let kanTestMse: Double = mse(trainedKan.predict, testXs, testYs)
}
