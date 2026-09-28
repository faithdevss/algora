import Foundation

// Port of GenerativeMath.kt (C7): VAE, DCGAN, CycleGAN, StyleGAN, latent diffusion, neural style
// transfer and DeepFakes. Counting arguments are exact, invariances hold to float tolerance, and the
// experiments train or sample from the same kotlin.random seeds Android uses.

private func genGaussian(_ random: inout KotlinRandom) -> Double {
    let u1 = max(random.nextDouble(), 1e-12)
    let u2 = random.nextDouble()
    return (-2.0 * log(u1)).squareRoot() * cos(2.0 * .pi * u2)
}

/// Solve `a x = b` for small dense systems by Gaussian elimination with pivoting.
private func genSolve(_ a: [[Double]], _ b: [Double]) -> [Double] {
    let n = b.count
    var m = (0..<n).map { r in (0...n).map { c in c < n ? a[r][c] : b[r] } }
    for col in 0..<n {
        var pivot = col
        for r in (col + 1)..<max(n, col + 1) where abs(m[r][col]) > abs(m[pivot][col]) { pivot = r }
        m.swapAt(col, pivot)
        let d = m[col][col]
        if abs(d) < 1e-12 { continue }
        for c in col...n { m[col][c] /= d }
        for r in 0..<n where r != col {
            let f = m[r][col]
            if f == 0.0 { continue }
            for c in col...n { m[r][c] -= f * m[col][c] }
        }
    }
    return (0..<n).map { m[$0][n] }
}

private func dotD(_ a: [Double], _ b: [Double]) -> Double {
    var s = 0.0
    for i in a.indices { s += a[i] * b[i] }
    return s
}

/// Top-k principal directions of centred rows, by power iteration with deflation.
private func principalDirections(_ rows: [[Double]], _ k: Int, seed: Int = 3) -> [[Double]] {
    let d = rows[0].count
    var cov = [[Double]](repeating: [Double](repeating: 0, count: d), count: d)
    for r in rows { for i in 0..<d { for j in 0..<d { cov[i][j] += r[i] * r[j] } } }
    for i in 0..<d { for j in 0..<d { cov[i][j] /= Double(rows.count) } }

    var random = KotlinRandom(seed: seed)
    var basis: [[Double]] = []
    for _ in 0..<k {
        var v = (0..<d).map { _ in genGaussian(&random) }
        for _ in 0..<400 {
            for b in basis {
                let dot = dotD(v, b)
                for i in 0..<d { v[i] -= dot * b[i] }
            }
            let next = (0..<d).map { i in dotD(cov[i], v) }
            let norm = next.reduce(0.0) { $0 + $1 * $1 }.squareRoot()
            if norm < 1e-12 { continue }
            v = next.map { $0 / norm }
        }
        for b in basis {
            let dot = dotD(v, b)
            for i in 0..<d { v[i] -= dot * b[i] }
        }
        let norm = v.reduce(0.0) { $0 + $1 * $1 }.squareRoot()
        basis.append(norm < 1e-12 ? [Double](repeating: 0, count: d) : v.map { $0 / norm })
    }
    return basis
}

/// Least-squares map from latent rows to target rows.
private func genLeastSquares(_ latents: [[Double]], _ targets: [[Double]]) -> [[Double]] {
    let k = latents[0].count
    let d = targets[0].count
    var gram = [[Double]](repeating: [Double](repeating: 0, count: k), count: k)
    for z in latents { for i in 0..<k { for j in 0..<k { gram[i][j] += z[i] * z[j] } } }
    for i in 0..<k { gram[i][i] += 1e-8 }
    return (0..<d).map { outDim in
        var rhs = [Double](repeating: 0, count: k)
        for (n, z) in latents.enumerated() { for i in 0..<k { rhs[i] += z[i] * targets[n][outDim] } }
        return genSolve(gram, rhs)
    }
}

private func applyMap(_ w: [[Double]], _ z: [Double]) -> [Double] { w.map { dotD($0, z) } }

private func genRmse(_ a: [Double], _ b: [Double]) -> Double {
    var s = 0.0
    for i in a.indices { s += (a[i] - b[i]) * (a[i] - b[i]) }
    return (s / Double(a.count)).squareRoot()
}

// MARK: - VAE

enum VaeLab {
    struct GradientVariance {
        let trueGradient: Double
        let reparameterizedMean: Double
        let scoreFunctionMean: Double
        let reparameterizedVariance: Double
        let scoreFunctionVariance: Double
        var ratio: Double { scoreFunctionVariance / reparameterizedVariance }
    }

    static func scoreFunctionVarianceClosedForm(_ dim: Int) -> Double {
        let a = 1.0
        let d = Double(dim)
        let bigA = d * a * a
        let e = bigA * bigA + 4 * a * a * (d + 2) + 15 + 8 * (d - 1) + (d - 1) * (d - 1) + 2 * bigA * (d + 2)
        return e - 4 * a * a
    }

    static let reparameterizedVarianceClosedForm = 4.0

    static func gradientVariance(dim: Int = 8, samples: Int = 400_000, seed: Int = 17) -> GradientVariance {
        var random = KotlinRandom(seed: seed)
        let mu = [Double](repeating: 1.0, count: dim)
        let c = [Double](repeating: 0, count: dim)
        var rSum = 0.0, rSq = 0.0, sSum = 0.0, sSq = 0.0
        var eps = [Double](repeating: 0, count: dim)
        for _ in 0..<samples {
            for i in 0..<dim { eps[i] = genGaussian(&random) }
            let z0 = mu[0] + eps[0]
            let rep = 2 * (z0 - c[0])
            var f = 0.0
            for i in 0..<dim { let zi = mu[i] + eps[i]; f += (zi - c[i]) * (zi - c[i]) }
            let score = f * (z0 - mu[0])
            rSum += rep; rSq += rep * rep
            sSum += score; sSq += score * score
        }
        let n = Double(samples)
        let rMean = rSum / n, sMean = sSum / n
        return GradientVariance(trueGradient: 2.0, reparameterizedMean: rMean, scoreFunctionMean: sMean,
                                reparameterizedVariance: rSq / n - rMean * rMean, scoreFunctionVariance: sSq / n - sMean * sMean)
    }

    static let dataDim = 6
    static let latentDim = 4
    static let trueFactors = 2

    static func dataset(count: Int = 512, seed: Int = 29) -> [[Double]] {
        var random = KotlinRandom(seed: seed)
        let mixing = (0..<dataDim).map { i in (0..<trueFactors).map { j in cos(Double((i + 1) * (j + 1)) * 0.7) } }
        return (0..<count).map { _ in
            let s = (0..<trueFactors).map { _ in genGaussian(&random) }
            return (0..<dataDim).map { i in
                var acc = 0.0
                for t in 0..<trueFactors { acc += mixing[i][t] * s[t] }
                return acc + 0.05 * genGaussian(&random)
            }
        }
    }

    struct TrainedVae {
        let beta: Double
        let perDimensionKl: [Double]
        let reconstructionRmse: Double
        var activeUnits: Int { perDimensionKl.filter { $0 > VaeLab.activeThreshold }.count }
        var totalKl: Double { perDimensionKl.reduce(0, +) }
    }

    static let activeThreshold = 0.01

    static let betaSweep = [0.001, 0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 4.0]

    static let trained: [Double: TrainedVae] = Dictionary(uniqueKeysWithValues: betaSweep.map { ($0, train($0)) })

    static let gradientVarianceAtEight: GradientVariance = gradientVariance(dim: 8)

    static let varianceDims = [1, 2, 4, 8, 16]

    static func train(_ beta: Double, steps: Int = 60_000, learningRate: Double = 0.01, seed: Int = 41) -> TrainedVae {
        let data = dataset(seed: 29)
        var random = KotlinRandom(seed: seed)
        var encoder = (0..<latentDim).map { _ in (0..<dataDim).map { _ in 0.1 * genGaussian(&random) } }
        var decoder = (0..<dataDim).map { _ in (0..<latentDim).map { _ in 0.1 * genGaussian(&random) } }
        var logVar = [Double](repeating: 0, count: latentDim)

        for _ in 0..<steps {
            let x = data[random.nextInt(data.count)]
            let mu = applyMap(encoder, x)
            let sigma = (0..<latentDim).map { exp(0.5 * logVar[$0]) }
            let eps = (0..<latentDim).map { _ in genGaussian(&random) }
            let z = (0..<latentDim).map { mu[$0] + sigma[$0] * eps[$0] }
            let xHat = applyMap(decoder, z)
            let residual = (0..<dataDim).map { xHat[$0] - x[$0] }

            let dz = (0..<latentDim).map { j in
                var s = 0.0
                for i in 0..<dataDim { s += residual[i] * decoder[i][j] }
                return 2.0 * s
            }
            for i in 0..<dataDim { for j in 0..<latentDim { decoder[i][j] -= learningRate * 2.0 * residual[i] * z[j] } }
            for j in 0..<latentDim {
                let dMu = dz[j] + beta * mu[j]
                for i in 0..<dataDim { encoder[j][i] -= learningRate * dMu * x[i] }
                let dLogVar = dz[j] * eps[j] * sigma[j] * 0.5 + beta * 0.5 * (exp(logVar[j]) - 1.0)
                logVar[j] -= learningRate * dLogVar
                logVar[j] = min(max(logVar[j], -12.0), 4.0)
            }
        }

        var sqErr = 0.0
        var muSq = [Double](repeating: 0, count: latentDim)
        for x in data {
            let mu = applyMap(encoder, x)
            for j in 0..<latentDim { muSq[j] += mu[j] * mu[j] }
            let xHat = applyMap(decoder, mu)
            var s = 0.0
            for i in x.indices { s += (xHat[i] - x[i]) * (xHat[i] - x[i]) }
            sqErr += s
        }
        let kl = (0..<latentDim).map { j -> Double in
            let meanSq = muSq[j] / Double(data.count)
            let varq = exp(logVar[j])
            return 0.5 * (meanSq + varq - 1.0 - logVar[j])
        }
        return TrainedVae(beta: beta, perDimensionKl: kl, reconstructionRmse: (sqErr / Double(data.count * dataDim)).squareRoot())
    }
}

// MARK: - DCGAN

enum DcganLab {
    static func coverage(_ kernel: Int, _ stride: Int, _ inputLength: Int) -> [Int] {
        let outputLength = (inputLength - 1) * stride + kernel
        var counts = [Int](repeating: 0, count: outputLength)
        for i in 0..<inputLength { for k in 0..<kernel { counts[i * stride + k] += 1 } }
        return counts
    }

    static func interiorCoverage(_ kernel: Int, _ stride: Int, _ inputLength: Int) -> [Int] {
        let full = coverage(kernel, stride, inputLength)
        return Array(full[kernel..<(full.count - kernel)])
    }

    static func isUniform(_ kernel: Int, _ stride: Int, _ inputLength: Int = 16) -> Bool { Set(interiorCoverage(kernel, stride, inputLength)).count == 1 }

    static func divides(_ kernel: Int, _ stride: Int) -> Bool { kernel % stride == 0 }

    struct Layer { let name: String; let inChannels: Int; let outChannels: Int; let spatial: Int; let parameters: Int64 }

    static let generator: [Layer] = {
        let kernel: Int64 = 4
        let projection: Int64 = 100 * 1024 * 4 * 4 + 1024
        var layers = [Layer(name: "project 100 -> 4x4x1024", inChannels: 100, outChannels: 1024, spatial: 4, parameters: projection)]
        var channels = 1024
        var spatial = 4
        while channels > 128 {
            let out = channels / 2
            spatial *= 2
            let params: Int64 = kernel * kernel * Int64(channels) * Int64(out) + Int64(out)
            layers.append(Layer(name: "convT \(channels) -> \(out)", inChannels: channels, outChannels: out, spatial: spatial, parameters: params))
            channels = out
        }
        let last: Int64 = kernel * kernel * Int64(channels) * 3 + 3
        layers.append(Layer(name: "convT \(channels) -> 3", inChannels: channels, outChannels: 3, spatial: spatial * 2, parameters: last))
        return layers
    }()

    static var generatorParameters: Int64 { generator.reduce(0) { $0 + $1.parameters } }

    static var projectionShare: Double { Double(generator[0].parameters) / Double(generatorParameters) }
}

// MARK: - CycleGAN

enum CycleGanLab {
    static func factorial(_ n: Int) -> Int64 { n < 1 ? 1 : (1...n).reduce(Int64(1)) { $0 * Int64($1) } }
    static func adversariallyOptimal(_ n: Int) -> Int64 { factorial(n) }
    static func cycleConsistent(_ n: Int) -> Int64 { factorial(n) }
    static let semanticallyCorrect: Int64 = 1
    static func oddsOfCorrect(_ n: Int) -> Double { Double(semanticallyCorrect) / Double(cycleConsistent(n)) }

    /// Every mapping A -> B enumerated: (distribution-matching, also cycle-consistent, correct).
    static func enumerate(_ n: Int) -> (Int, Int, Int) {
        var distributionMatching = 0
        var alsoCycleConsistent = 0
        var assignment = [Int](repeating: 0, count: n)

        func recurse(_ index: Int) {
            if index == n {
                var hits = [Int](repeating: 0, count: n)
                for b in assignment { hits[b] += 1 }
                if hits.allSatisfy({ $0 == 1 }) {
                    distributionMatching += 1
                    var inverse = [Int](repeating: 0, count: n)
                    for (a, b) in assignment.enumerated() { inverse[b] = a }
                    if (0..<n).allSatisfy({ inverse[assignment[$0]] == $0 }) { alsoCycleConsistent += 1 }
                }
                return
            }
            for b in 0..<n {
                assignment[index] = b
                recurse(index + 1)
            }
        }
        recurse(0)
        return (distributionMatching, alsoCycleConsistent, 1)
    }

    /// Bijections that move each input by at most `budget` positions.
    static func withLocality(_ n: Int, _ budget: Int) -> Int {
        var count = 0
        var used = [Bool](repeating: false, count: n)
        func recurse(_ index: Int) {
            if index == n { count += 1; return }
            for b in 0..<n {
                if used[b] || abs(b - index) > budget { continue }
                used[b] = true
                recurse(index + 1)
                used[b] = false
            }
        }
        recurse(0)
        return count
    }
}

// MARK: - StyleGAN

enum StyleGanLab {
    static func adaIn(_ content: [Double], _ style: [Double]) -> [Double] {
        let (cMean, cStd) = meanAndStd(content)
        let (sMean, sStd) = meanAndStd(style)
        return content.map { sStd * ($0 - cMean) / cStd + sMean }
    }

    static func meanAndStd(_ v: [Double]) -> (Double, Double) {
        let mean = v.average
        return (mean, (v.reduce(0.0) { $0 + ($1 - mean) * ($1 - mean) } / Double(v.count)).squareRoot())
    }

    static func inSupport(_ a: Double, _ b: Double) -> Bool { !(a > 0.5 && b > 0.5) }

    static func generate(_ z1: Double, _ z2: Double) -> [Double] {
        z1 < 2.0 / 3.0 ? [z1 * 0.75, z2] : [0.5 + (z1 - 2.0 / 3.0) * 1.5, z2 * 0.5]
    }

    struct PathLengths {
        let latentZ: Double
        let latentW: Double
        let wLeavingSupport: Double
        var ratio: Double { latentZ / latentW }
    }

    static func pathLengths(samples: Int = 40_000, steps: Int = 64, seed: Int = 53) -> PathLengths {
        var random = KotlinRandom(seed: seed)
        var zTotal = 0.0, wTotal = 0.0
        var outside = 0, wSegments = 0
        for _ in 0..<samples {
            let a0 = random.nextDouble(), a1 = random.nextDouble()
            let b0 = random.nextDouble(), b1 = random.nextDouble()
            var zPath = 0.0
            var previous = generate(a0, a1)
            for s in 1...steps {
                let t = Double(s) / Double(steps)
                let point = generate(a0 + (b0 - a0) * t, a1 + (b1 - a1) * t)
                zPath += (point[0] - previous[0]) * (point[0] - previous[0]) + (point[1] - previous[1]) * (point[1] - previous[1])
                previous = point
            }
            zTotal += zPath * Double(steps)

            let w1 = generate(a0, a1), w2 = generate(b0, b1)
            var wPath = 0.0
            var prev = w1
            for s in 1...steps {
                let t = Double(s) / Double(steps)
                let point = [w1[0] + (w2[0] - w1[0]) * t, w1[1] + (w2[1] - w1[1]) * t]
                wPath += (point[0] - prev[0]) * (point[0] - prev[0]) + (point[1] - prev[1]) * (point[1] - prev[1])
                prev = point
                wSegments += 1
                if !inSupport(point[0], point[1]) { outside += 1 }
            }
            wTotal += wPath * Double(steps)
        }
        return PathLengths(latentZ: zTotal / Double(samples), latentW: wTotal / Double(samples), wLeavingSupport: Double(outside) / Double(wSegments))
    }

    static let resolutions = [4, 8, 16, 32, 64, 128, 256, 512, 1024]
    static var styleInputs: Int { resolutions.count * 2 }

    static func band(_ resolution: Int) -> String { resolution <= 8 ? "coarse" : resolution <= 32 ? "middle" : "fine" }

    static func styleInputsIn(_ b: String) -> Int { resolutions.filter { band($0) == b }.count * 2 }
}

// MARK: - Latent diffusion

enum LatentDiffusionLab {
    static let imageSide = 512
    static let imageChannels = 3
    static let downsample = 8
    static let latentChannels = 4
    static let textTokens = 77
    static let latentSide = imageSide / downsample

    static let pixelElements: Int64 = Int64(imageSide) * Int64(imageSide) * Int64(imageChannels)
    static let latentElements: Int64 = Int64(latentSide) * Int64(latentSide) * Int64(latentChannels)
    static var elementRatio: Double { Double(pixelElements) / Double(latentElements) }

    static let pixelTokens: Int64 = Int64(imageSide) * Int64(imageSide)
    static let latentTokens: Int64 = Int64(latentSide) * Int64(latentSide)

    static let pixelAttentionPairs: Int64 = pixelTokens * pixelTokens
    static let latentAttentionPairs: Int64 = latentTokens * latentTokens
    static var attentionRatio: Double { Double(pixelAttentionPairs) / Double(latentAttentionPairs) }

    static let pixelCrossAttentionPairs: Int64 = pixelTokens * Int64(textTokens)
    static let latentCrossAttentionPairs: Int64 = latentTokens * Int64(textTokens)
    static var crossAttentionRatio: Double { Double(pixelCrossAttentionPairs) / Double(latentCrossAttentionPairs) }

    static let components: [(String, Int)] = [
        ("VAE encoder + decoder", 84),
        ("CLIP text encoder", 123),
        ("UNet (the only part that is denoised)", 860),
    ]

    static var totalParametersMillions: Int { components.reduce(0) { $0 + $1.1 } }
    static var unetShare: Double { 860.0 / Double(totalParametersMillions) }

    static let ddpmSteps = 1000
    static let ddimSteps = 50
    static var stepSaving: Double { Double(ddpmSteps) / Double(ddimSteps) }

    static var pixelSamplingPairs: Double { Double(pixelAttentionPairs) * Double(ddpmSteps) }
    static var latentSamplingPairs: Double { Double(latentAttentionPairs) * Double(ddimSteps) }
    static var samplingRatio: Double { pixelSamplingPairs / latentSamplingPairs }
}

// MARK: - Neural style transfer

enum StyleTransferLab {
    static func featureMap(channels: Int = 8, positions: Int = 64, seed: Int = 61) -> [[Double]] {
        var random = KotlinRandom(seed: seed)
        return (0..<channels).map { c in
            (0..<positions).map { p in abs(cos(Double(c + 1) * 0.9 + Double(p) * 0.15)) + 0.1 * abs(genGaussian(&random)) }
        }
    }

    static func gram(_ f: [[Double]]) -> [[Double]] {
        let n = Double(f[0].count)
        return f.indices.map { i in f.indices.map { j in dotD(f[i], f[j]) / n } }
    }

    static func shuffleColumns(_ f: [[Double]], seed: Int = 71) -> [[Double]] {
        var random = KotlinRandom(seed: seed)
        let order = Array(f[0].indices).kotlinShuffled(&random)
        return f.map { row in row.indices.map { row[order[$0]] } }
    }

    static func styleLoss(_ a: [[Double]], _ b: [[Double]]) -> Double {
        let ga = gram(a), gb = gram(b)
        var total = 0.0
        for i in ga.indices { for j in ga.indices { total += (ga[i][j] - gb[i][j]) * (ga[i][j] - gb[i][j]) } }
        return total / Double(ga.count * ga.count)
    }

    static func contentLoss(_ a: [[Double]], _ b: [[Double]]) -> Double {
        var total = 0.0
        var count = 0
        for i in a.indices { for j in a[i].indices { total += (a[i][j] - b[i][j]) * (a[i][j] - b[i][j]); count += 1 } }
        return total / Double(count)
    }

    static let vggChannels = 512
    static let vggSide = 28
    static let featureValues: Int64 = Int64(vggChannels) * Int64(vggSide) * Int64(vggSide)
    static let gramEntries: Int64 = Int64(vggChannels) * Int64(vggChannels)
    static let gramUniqueEntries: Int64 = Int64(vggChannels) * Int64(vggChannels + 1) / 2
    static var compression: Double { Double(featureValues) / Double(gramUniqueEntries) }

    static let styleLayers = ["conv1_1", "conv2_1", "conv3_1", "conv4_1", "conv5_1"]
    static let contentLayer = "conv4_2"
}

// MARK: - DeepFakes

enum DeepFakeLab {
    static let dim = 8
    static let expressionFactors = 2
    static let latent = 2

    private static let baseDirections: [[Double]] = {
        let raw = [(0..<dim).map { cos(Double($0 + 1) * 0.7) }, (0..<dim).map { cos(Double($0 + 1) * 1.9) }]
        var out: [[Double]] = []
        for v in raw {
            var w = v
            for b in out {
                let dot = dotD(w, b)
                for i in 0..<dim { w[i] -= dot * b[i] }
            }
            let norm = w.reduce(0.0) { $0 + $1 * $1 }.squareRoot()
            out.append(w.map { $0 / norm })
        }
        var extra = (0..<dim).map { cos(Double($0 + 1) * 3.1) }
        for b in out {
            let dot = dotD(extra, b)
            for i in 0..<dim { extra[i] -= dot * b[i] }
        }
        let norm = extra.reduce(0.0) { $0 + $1 * $1 }.squareRoot()
        return [out[0], out[1], extra.map { $0 / norm }]
    }()

    static let meanFaceA = (0..<dim).map { cos(Double($0) * 0.4) }
    static let meanFaceB = (0..<dim).map { -cos(Double($0) * 0.9) * 0.8 }
    static let scalesA = [1.0, 0.6]
    static let scalesB = [0.6, 1.0]

    static func directionsB(_ degrees: Double) -> [[Double]] {
        let t = degrees * .pi / 180.0
        let rotated = (0..<dim).map { cos(t) * baseDirections[0][$0] + sin(t) * baseDirections[2][$0] }
        return [rotated, baseDirections[1]]
    }

    static func renderA(_ expression: [Double]) -> [Double] {
        (0..<dim).map { i in
            var s = 0.0
            for f in 0..<expressionFactors { s += scalesA[f] * expression[f] * baseDirections[f][i] }
            return meanFaceA[i] + s
        }
    }

    static func renderB(_ expression: [Double], _ degrees: Double) -> [Double] {
        let dirs = directionsB(degrees)
        return (0..<dim).map { i in
            var s = 0.0
            for f in 0..<expressionFactors { s += scalesB[f] * expression[f] * dirs[f][i] }
            return meanFaceB[i] + s
        }
    }

    static func expressions(count: Int = 400, seed: Int = 83) -> [[Double]] {
        var random = KotlinRandom(seed: seed)
        return (0..<count).map { _ in (0..<expressionFactors).map { _ in genGaussian(&random) } }
    }

    struct SwapResult {
        let degrees: Double
        let sharedEncoderRmse: Double
        let independentEncoderRmse: Double
        let meanFaceBaselineRmse: Double
        var sharedBeatsBaseline: Bool { sharedEncoderRmse < meanFaceBaselineRmse }
        var independentBeatsBaseline: Bool { independentEncoderRmse < meanFaceBaselineRmse }
    }

    private static func centre(_ rows: [[Double]]) -> ([[Double]], [Double]) {
        let mean = (0..<dim).map { i in rows.reduce(0.0) { $0 + $1[i] } / Double(rows.count) }
        return (rows.map { r in (0..<dim).map { r[$0] - mean[$0] } }, mean)
    }

    private static func encoder(_ mean: [Double], _ basis: [[Double]]) -> ([Double]) -> [Double] {
        { x in
            (0..<latent).map { j in
                var s = 0.0
                for i in x.indices { s += (x[i] - mean[i]) * basis[j][i] }
                return s
            }
        }
    }

    static func swap(_ degrees: Double) -> SwapResult {
        let exprs = expressions()
        let facesA = exprs.map { renderA($0) }
        let facesB = exprs.map { renderB($0, degrees) }
        let truth = facesB
        let (centredA, meanA) = centre(facesA)
        let (centredB, meanB) = centre(facesB)

        func score(_ encodeSource: ([Double]) -> [Double], _ encodeTarget: ([Double]) -> [Double]) -> Double {
            let decoderB = genLeastSquares(facesB.map(encodeTarget), centredB)
            var total = 0.0
            for i in facesA.indices {
                let rebuilt = applyMap(decoderB, encodeSource(facesA[i]))
                total += genRmse((0..<dim).map { meanB[$0] + rebuilt[$0] }, truth[i])
            }
            return total / Double(facesA.count)
        }

        let sharedBasis = principalDirections(centredA + centredB, latent)
        let shared = score(encoder(meanA, sharedBasis), encoder(meanB, sharedBasis))

        let basisA = principalDirections(centredA, latent)
        let basisB = principalDirections(centredB, latent)
        let independent = score(encoder(meanA, basisA), encoder(meanB, basisB))

        let baseline = facesA.indices.reduce(0.0) { $0 + genRmse(meanB, truth[$1]) } / Double(facesA.count)
        return SwapResult(degrees: degrees, sharedEncoderRmse: shared, independentEncoderRmse: independent, meanFaceBaselineRmse: baseline)
    }

    static let angleSweep = [0.0, 15.0, 30.0, 45.0, 60.0, 75.0, 90.0]

    static func sweep() -> [SwapResult] { angleSweep.map { swap($0) } }

    static let sweepResults: [SwapResult] = sweep()

    static func ownDomainRmse(_ degrees: Double = 0.0) -> Double {
        let facesA = expressions().map { renderA($0) }
        let (centred, meanA) = centre(facesA)
        let basisA = principalDirections(centred, latent)
        let encode = encoder(meanA, basisA)
        let decoderA = genLeastSquares(facesA.map(encode), centred)
        var total = 0.0
        for i in facesA.indices {
            let rebuilt = applyMap(decoderA, encode(facesA[i]))
            total += genRmse((0..<dim).map { meanA[$0] + rebuilt[$0] }, facesA[i])
        }
        return total / Double(facesA.count)
    }
}
