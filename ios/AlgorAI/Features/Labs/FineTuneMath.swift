import Foundation

// Port of the FineTuneMath.kt sections the array-walk frames read: Flash Attention, SSMs, Mamba and
// RWKV. Seeds and draw order match Android so the quoted numbers agree.

private func ftGaussian(_ random: inout KotlinRandom) -> Double {
    let u1 = max(random.nextDouble(), 1e-12)
    let u2 = random.nextDouble()
    return (-2.0 * log(u1)).squareRoot() * cos(2.0 * Double.pi * u2)
}

private func ftSoftmax(_ logits: [Double]) -> [Double] {
    let m = logits.max()!
    let e = logits.map { exp($0 - m) }
    let s = e.reduce(0, +)
    return e.map { $0 / s }
}

/// Repeated multiplication, as Kotlin's private `Double.pow(Int)` does.
private func ipow(_ base: Double, _ n: Int) -> Double {
    var acc = 1.0
    for _ in 0..<max(n, 0) { acc *= base }
    return acc
}

/// Rounded to the nearest tenth of a gigabyte, for the frames that quote memory.
func bytesToGb(_ bytes: Int) -> Double { (Double(bytes) / 1_073_741_824.0 * 10.0).rounded() / 10.0 }

// MARK: - Flash Attention

enum FlashLab {
    static func naiveAttention(_ scores: [Double], _ values: [[Double]]) -> [Double] {
        let p = ftSoftmax(scores)
        return (0..<values[0].count).map { d in scores.indices.reduce(0.0) { $0 + p[$1] * values[$1][d] } }
    }

    static func tiledAttention(_ scores: [Double], _ values: [[Double]], _ blockSize: Int) -> [Double] {
        let dim = values[0].count
        var m = -Double.infinity, l = 0.0
        var o = [Double](repeating: 0, count: dim)
        var start = 0
        while start < scores.count {
            let end = min(start + blockSize, scores.count)
            let blockMax = scores[start..<end].max()!
            let newMax = max(m, blockMax)
            let correction = m == -Double.infinity ? 0.0 : exp(m - newMax)
            var blockSum = 0.0
            var blockAcc = [Double](repeating: 0, count: dim)
            for i in start..<end {
                let w = exp(scores[i] - newMax)
                blockSum += w
                for d in 0..<dim { blockAcc[d] += w * values[i][d] }
            }
            for d in 0..<dim { o[d] = o[d] * correction + blockAcc[d] }
            l = l * correction + blockSum
            m = newMax
            start = end
        }
        return o.map { $0 / l }
    }

    struct Tile { let index: Int; let blockMax: Double; let runningMax: Double; let runningSum: Double; let rescale: Double }

    static func tileTrace(_ scores: [Double], _ blockSize: Int) -> [Tile] {
        var out: [Tile] = []
        var m = -Double.infinity, l = 0.0
        var start = 0, index = 0
        while start < scores.count {
            let end = min(start + blockSize, scores.count)
            let blockMax = scores[start..<end].max()!
            let newMax = max(m, blockMax)
            let correction = m == -Double.infinity ? 1.0 : exp(m - newMax)
            l = l * correction + (start..<end).reduce(0.0) { $0 + exp(scores[$1] - newMax) }
            m = newMax
            out.append(Tile(index: index, blockMax: blockMax, runningMax: m, runningSum: l, rescale: correction))
            start = end
            index += 1
        }
        return out
    }

    static func unstableAttention(_ scores: [Double], _ values: [[Double]]) -> [Double] {
        let dim = values[0].count
        var l = 0.0
        var o = [Double](repeating: 0, count: dim)
        for i in scores.indices {
            let w = exp(scores[i])
            l += w
            for d in 0..<dim { o[d] += w * values[i][d] }
        }
        return o.map { $0 / l }
    }

    static let demoScores: [Double] = {
        var random = KotlinRandom(seed: 457)
        return (0..<512).map { _ in ftGaussian(&random) * 3.0 }
    }()

    static let demoValues: [[Double]] = {
        var random = KotlinRandom(seed: 458)
        return (0..<512).map { _ in (0..<8).map { _ in ftGaussian(&random) } }
    }()

    static func maxDifference(_ blockSize: Int) -> Double {
        let a = naiveAttention(demoScores, demoValues)
        let b = tiledAttention(demoScores, demoValues, blockSize)
        return a.indices.map { abs(a[$0] - b[$0]) }.max()!
    }

    static let overflowScore: Double = log(Double.greatestFiniteMagnitude)

    static func overflows(_ peakScore: Double) -> Bool {
        let scores = (0..<8).map { $0 == 3 ? peakScore : 0.0 }
        let values = (0..<8).map { i in [Double(i), Double(i)] }
        return unstableAttention(scores, values).contains { $0.isNaN || $0.isInfinite }
    }

    static let bytesPerElement = 2

    static func standardTraffic(_ seq: Int, _ headDim: Int) -> Int {
        let qkv = 3 * seq * headDim
        let out = seq * headDim
        let scoreTraffic = 4 * seq * seq
        return (qkv + out + scoreTraffic) * bytesPerElement
    }

    static func flashTraffic(_ seq: Int, _ headDim: Int, _ queryBlock: Int) -> Int {
        let blocks = (seq + queryBlock - 1) / queryBlock
        let kv = 2 * blocks * seq * headDim
        return (seq * headDim + seq * headDim + kv) * bytesPerElement
    }

    static func trafficRatio(_ seq: Int, headDim: Int = 64, queryBlock: Int = 128) -> Double {
        Double(standardTraffic(seq, headDim)) / Double(flashTraffic(seq, headDim, queryBlock))
    }

    static func asymptoticTrafficRatio(headDim: Int = 64, queryBlock: Int = 128) -> Double { 2.0 * Double(queryBlock) / Double(headDim) }

    static func forwardFlops(_ seq: Int, _ headDim: Int) -> Int { 4 * seq * seq * headDim }

    static func backwardFlops(_ seq: Int, _ headDim: Int, recompute: Bool) -> Int { (recompute ? 10 : 8) * seq * seq * headDim }

    static func flopOverhead(_ seq: Int, headDim: Int = 64) -> Double {
        let plain = forwardFlops(seq, headDim) + backwardFlops(seq, headDim, recompute: false)
        let flash = forwardFlops(seq, headDim) + backwardFlops(seq, headDim, recompute: true)
        return Double(flash) / Double(plain)
    }

    static func scoreMatrixBytes(_ seq: Int, heads: Int = 32) -> Int { heads * seq * seq * bytesPerElement }
}

// MARK: - State space models

enum SsmLab {
    static let stateDim = 4
    static let poles: [Double] = [0.01, 0.05, 0.20, 0.80]
    static let delta = 1.0
    static let aBar: [Double] = poles.map { exp(-delta * $0) }
    static let bBar: [Double] = (0..<4).map { (1.0 - aBar[$0]) / poles[$0] }
    static let c: [Double] = [1.0, -0.6, 0.4, -0.25]

    static func recurrent(_ input: [Double]) -> [Double] {
        var h = [Double](repeating: 0, count: stateDim)
        return input.map { x in
            for n in 0..<stateDim { h[n] = aBar[n] * h[n] + bBar[n] * x }
            return (0..<stateDim).reduce(0.0) { $0 + c[$1] * h[$1] }
        }
    }

    static func stateTrace(_ input: [Double]) -> [[Double]] {
        var h = [Double](repeating: 0, count: stateDim)
        return input.map { x in
            for n in 0..<stateDim { h[n] = aBar[n] * h[n] + bBar[n] * x }
            return h
        }
    }

    static func kernel(_ length: Int) -> [Double] {
        (0..<length).map { t in (0..<stateDim).reduce(0.0) { $0 + c[$1] * ipow(aBar[$1], t) * bBar[$1] } }
    }

    static func convolutional(_ input: [Double]) -> [Double] {
        let k = kernel(input.count)
        return input.indices.map { t in (0...t).reduce(0.0) { $0 + k[t - $1] * input[$1] } }
    }

    static let demoInput: [Double] = {
        var random = KotlinRandom(seed: 811)
        return (0..<64).map { _ in ftGaussian(&random) }
    }()

    static func formEquivalenceGap() -> Double {
        let a = recurrent(demoInput), b = convolutional(demoInput)
        return a.indices.map { abs(a[$0] - b[$0]) }.max()!
    }

    static func halfLife(_ channel: Int) -> Double { log(0.5) / log(aBar[channel]) }

    static func effectiveHorizon(_ threshold: Double = 0.01) -> Int {
        let k = kernel(4_096)
        let peak = k.map { abs($0) }.max()!
        return (k.lastIndex { abs($0) > threshold * peak } ?? -1) + 1
    }

    static func scanCompose(_ first: (Double, Double), _ second: (Double, Double)) -> (Double, Double) {
        (second.0 * first.0, second.0 * first.1 + second.1)
    }

    static func parallelScanDepth(_ length: Int) -> Int {
        var depth = 0, span = 1
        while span < length { span *= 2; depth += 1 }
        return 2 * depth - 1
    }

    struct CostRow { let length: Int; let attentionOps: Int; let ssmRecurrentOps: Int; let ssmScanDepth: Int }

    static func costs(_ lengths: [Int] = [1_024, 16_384, 1_048_576], width: Int = 1_024) -> [CostRow] {
        lengths.map { l in CostRow(length: l, attentionOps: 4 * l * l * width, ssmRecurrentOps: 2 * l * stateDim * width, ssmScanDepth: parallelScanDepth(max(l, 2))) }
    }
}

// MARK: - Mamba

enum MambaLab {
    static let signalValue = 1.0
    static let fillerValue = 0.7

    static func sequence(_ fillers: Int) -> [Double] { (0...fillers).map { $0 == 0 ? signalValue : fillerValue } }

    static func lti(_ input: [Double], _ a: Double, _ b: Double) -> Double {
        var h = 0.0
        for x in input { h = a * h + b * x }
        return h
    }

    static func selective(_ input: [Double]) -> Double {
        var h = 0.0
        for x in input {
            let d = abs(x - signalValue) < 1e-9 ? 4.0 : 0.0
            let a = exp(-d)
            h = a * h + (1.0 - a) * x
        }
        return h
    }

    static let fillerCounts = [0, 5, 10, 20, 50, 100]

    struct Arm { let name: String; let short: String; let recovered: (Int) -> Double; let trace: ([Double]) -> [Double] }

    private static func ltiTrace(_ input: [Double], _ a: Double, _ b: Double) -> [Double] {
        var h = 0.0
        return input.map { h = a * h + b * $0; return h }
    }

    private static func selectiveTrace(_ input: [Double]) -> [Double] {
        var h = 0.0
        return input.map { x in
            let d = abs(x - signalValue) < 1e-9 ? 4.0 : 0.0
            let a = exp(-d)
            h = a * h + (1.0 - a) * x
            return h
        }
    }

    static let arms: [Arm] = [
        Arm(name: "LTI, decaying (a = 0.90)", short: "decaying", recovered: { lti(sequence($0), 0.90, 1.0 - 0.90) }, trace: { ltiTrace($0, 0.90, 0.10) }),
        Arm(name: "LTI, lossless (a = 1.00)", short: "lossless", recovered: { lti(sequence($0), 1.0, 1.0) }, trace: { ltiTrace($0, 1.0, 1.0) }),
        Arm(name: "selective (Δ from the token)", short: "selective", recovered: { selective(sequence($0)) }, trace: { selectiveTrace($0) }),
    ]

    static func errorAt(_ arm: Arm, _ fillers: Int) -> Double { abs(arm.recovered(fillers) - signalValue) }

    static func withoutSignal(_ fillers: Int) -> [Double] { [Double](repeating: fillerValue, count: fillers + 1) }

    static func signalContribution(_ arm: Arm, _ fillers: Int) -> Double {
        let control: Double
        switch arm.name {
        case arms[0].name: control = lti(withoutSignal(fillers), 0.90, 1.0 - 0.90)
        case arms[1].name: control = lti(withoutSignal(fillers), 1.0, 1.0)
        default: control = selective(withoutSignal(fillers))
        }
        return arm.recovered(fillers) - control
    }

    static func bestFixedKernelResidual() -> Double {
        let targets = fillerCounts.map { selective(sequence($0)) }
        let features = fillerCounts.map { signalValue + Double($0) * fillerValue }
        let g = features.indices.reduce(0.0) { $0 + features[$1] * targets[$1] } / features.reduce(0.0) { $0 + $1 * $1 }
        let sq = features.indices.reduce(0.0) { acc, i in let e = g * features[i] - targets[i]; return acc + e * e }
        return (sq / Double(features.count)).squareRoot()
    }

    static let hiddenWidth = 2_048
    static let expandedState = 16

    static func mambaStateBytes() -> Int { 2 * hiddenWidth * expandedState }

    static func transformerCacheBytes(_ length: Int, layers: Int = 1, heads: Int = 32, headDim: Int = 64) -> Int {
        2 * length * layers * heads * headDim * 2
    }
}

// MARK: - RWKV

enum RwkvLab {
    static let decay = 0.10
    static let bonus = 1.0

    static func wkvNaive(_ keys: [Double], _ values: [Double], _ t: Int) -> Double {
        var num = 0.0, den = 0.0
        for i in 0..<t {
            let w = exp(-Double(t - 1 - i) * decay + keys[i])
            num += w * values[i]
            den += w
        }
        let w = exp(bonus + keys[t])
        return (num + w * values[t]) / (den + w)
    }

    static func wkvStable(_ keys: [Double], _ values: [Double], _ t: Int) -> Double {
        var a = 0.0, b = 0.0, p = -Double.infinity
        for i in 0..<t {
            let shifted = p - decay
            let q = max(shifted, keys[i])
            let e1 = p == -Double.infinity ? 0.0 : exp(shifted - q)
            let e2 = exp(keys[i] - q)
            a = e1 * a + e2 * values[i]
            b = e1 * b + e2
            p = q
        }
        let q = max(p, bonus + keys[t])
        let e1 = p == -Double.infinity ? 0.0 : exp(p - q)
        let e2 = exp(bonus + keys[t] - q)
        return (e1 * a + e2 * values[t]) / (e1 * b + e2)
    }

    static func relativeWeight(_ distance: Int, keyGap: Double = 0) -> Double { exp(-Double(distance) * decay + keyGap - bonus) }

    struct Needle { let distance: Int; let rwkvShare: Double; let attentionShare: Double }

    static func needle(_ distance: Int, needleKey: Double = 3.0, queryMatch: Double = 3.0) -> Needle {
        let length = distance + 1
        let keys = (0..<length).map { $0 == 0 ? needleKey : 0.0 }
        var rwkvTotal = 0.0, rwkvNeedle = 0.0
        for i in 0..<(length - 1) {
            let w = exp(-Double(length - 2 - i) * decay + keys[i])
            rwkvTotal += w
            if i == 0 { rwkvNeedle = w }
        }
        rwkvTotal += exp(bonus + keys[length - 1])
        let p = ftSoftmax(keys.map { $0 * queryMatch })
        return Needle(distance: distance, rwkvShare: rwkvNeedle / rwkvTotal, attentionShare: p[0])
    }

    static let needleDistances = [5, 20, 50, 100, 500]

    static let demoKeys: [Double] = {
        var random = KotlinRandom(seed: 929)
        return (0..<24).map { _ in ftGaussian(&random) * 1.5 }
    }()

    static let demoValues: [Double] = {
        var random = KotlinRandom(seed: 930)
        return (0..<24).map { _ in ftGaussian(&random) }
    }()

    static func stabilityGap() -> Double {
        (1..<demoKeys.count).map { abs(wkvNaive(demoKeys, demoValues, $0) - wkvStable(demoKeys, demoValues, $0)) }.max()!
    }

    static func naiveOverflowsAt(_ key: Double) -> Bool {
        let keys = (0..<4).map { $0 == 1 ? key : 0.0 }
        let values = (0..<4).map { Double($0) }
        let r = wkvNaive(keys, values, 3)
        return r.isNaN || r.isInfinite
    }

    static let channels = 2_048

    static func stateBytes() -> Int { 3 * channels * 4 }

    static func cacheBytes(_ length: Int, layers: Int = 24, heads: Int = 16, headDim: Int = 64) -> Int {
        2 * length * layers * heads * headDim * 2
    }
}
