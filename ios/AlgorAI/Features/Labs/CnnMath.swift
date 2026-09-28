import Foundation

// Port of CnnMath.kt: the arithmetic behind the C3 labs. The mechanics (output size, convolution,
// pooling, window counts, receptive field) are exact; the architectures are layer tables whose
// parameter counts are summed, not quoted. Convolution is cross-correlation, as in every framework.

typealias Matrix = [[Double]]

// MARK: - Geometry

/// ⌊(n + 2p − d(k−1) − 1)/s⌋ + 1, or 0 when the window does not fit.
func convOutputSize(_ input: Int, kernel: Int, stride: Int, padding: Int, dilation: Int = 1) -> Int {
    let effective = dilation * (kernel - 1) + 1
    let span = input + 2 * padding - effective
    return span < 0 ? 0 : span / stride + 1
}

func samePadding(_ kernel: Int, dilation: Int = 1) -> Int { dilation * (kernel - 1) / 2 }

func padImage(_ image: Matrix, _ padding: Int, value: Double = 0) -> Matrix {
    if padding == 0 { return image }
    let width = image[0].count + 2 * padding
    let blank = [Double](repeating: value, count: width)
    let side = [Double](repeating: value, count: padding)
    let body = image.map { side + $0 + side }
    return Matrix(repeating: blank, count: padding) + body + Matrix(repeating: blank, count: padding)
}

/// Cross-correlation with a square kernel and square stride.
func conv2d(_ image: Matrix, _ kernel: Matrix, stride: Int = 1, padding: Int = 0) -> Matrix {
    let k = kernel.count
    let out = convOutputSize(image.count, kernel: k, stride: stride, padding: padding)
    if out == 0 { return [] }
    let padded = padImage(image, padding)
    return (0..<out).map { r in
        (0..<out).map { c in
            var sum = 0.0
            for kr in 0..<k { for kc in 0..<k { sum += padded[r * stride + kr][c * stride + kc] * kernel[kr][kc] } }
            return sum
        }
    }
}

enum PoolMode { case max, average }

func pool2d(_ map: Matrix, size: Int = 2, stride: Int? = nil, mode: PoolMode = .max) -> Matrix {
    let s = stride ?? size
    let out = convOutputSize(map.count, kernel: size, stride: s, padding: 0)
    if out == 0 { return [] }
    return (0..<out).map { r in
        (0..<out).map { c in
            let window = (0..<size).flatMap { pr in (0..<size).map { pc in map[r * s + pr][c * s + pc] } }
            return mode == .max ? window.max()! : window.average
        }
    }
}

/// How many output windows each original pixel contributes to.
func windowCounts(_ input: Int, kernel: Int, stride: Int, padding: Int) -> [[Int]] {
    let out = convOutputSize(input, kernel: kernel, stride: stride, padding: padding)
    var counts = [[Int]](repeating: [Int](repeating: 0, count: input), count: input)
    for r in 0..<out {
        for c in 0..<out {
            for kr in 0..<kernel {
                for kc in 0..<kernel {
                    let pr = r * stride + kr - padding, pc = c * stride + kc - padding
                    if (0..<input).contains(pr) && (0..<input).contains(pc) { counts[pr][pc] += 1 }
                }
            }
        }
    }
    return counts
}

struct LayerGeometry { let kernel: Int; let stride: Int }

/// rᵢ = rᵢ₋₁ + (kᵢ − 1)·∏ⱼ<ᵢ sⱼ.
func receptiveField(_ layers: [LayerGeometry]) -> [Int] {
    var rf = 1, jump = 1
    return layers.map { layer in
        rf += (layer.kernel - 1) * jump
        jump *= layer.stride
        return rf
    }
}

// MARK: - Parameter and compute counting

func convParams(_ inChannels: Int, _ outChannels: Int, _ kernel: Int, bias: Bool = true) -> Int64 {
    Int64(outChannels) * (Int64(inChannels) * Int64(kernel * kernel) + (bias ? 1 : 0))
}

func denseParams(_ inFeatures: Int, _ outFeatures: Int, bias: Bool = true) -> Int64 {
    Int64(outFeatures) * (Int64(inFeatures) + (bias ? 1 : 0))
}

/// Multiply-accumulates for one conv layer.
func convMacs(_ outSize: Int, _ outChannels: Int, _ inChannels: Int, _ kernel: Int) -> Int64 {
    Int64(outSize) * Int64(outSize) * Int64(outChannels) * Int64(inChannels) * Int64(kernel) * Int64(kernel)
}

func depthwiseSeparableParams(_ inChannels: Int, _ outChannels: Int, _ kernel: Int, bias: Bool = false) -> Int64 {
    convParams(1, inChannels, kernel, bias: bias) + convParams(inChannels, outChannels, 1, bias: bias)
}

/// Separable cost ÷ standard cost = 1/outChannels + 1/k².
func separableCostRatio(_ outChannels: Int, _ kernel: Int) -> Double { 1.0 / Double(outChannels) + 1.0 / Double(kernel * kernel) }

// MARK: - Architectures as layer tables

enum LayerKind { case conv, pool, dense }

struct CnnLayer {
    let name: String
    let kind: LayerKind
    let outSize: Int
    let outChannels: Int
    let params: Int64
    let macs: Int64
    var activations: Int64 { Int64(outSize) * Int64(outSize) * Int64(outChannels) }
    var shape: String { kind == .dense ? "\(outChannels)" : "\(outSize)×\(outSize)×\(outChannels)" }
}

extension Array where Element == CnnLayer {
    var totalParams: Int64 { reduce(0) { $0 + $1.params } }
    var totalMacs: Int64 { reduce(0) { $0 + $1.macs } }
    func paramsIn(_ kind: LayerKind) -> Int64 { filter { $0.kind == kind }.reduce(0) { $0 + $1.params } }
    func macsIn(_ kind: LayerKind) -> Int64 { filter { $0.kind == kind }.reduce(0) { $0 + $1.macs } }
}

private final class StackBuilder {
    private var size: Int
    private var channels: Int
    var layers: [CnnLayer] = []
    init(_ size: Int, _ channels: Int) { self.size = size; self.channels = channels }

    func conv(_ name: String, _ filters: Int, _ kernel: Int, stride: Int = 1, padding: Int = 0) {
        let out = convOutputSize(size, kernel: kernel, stride: stride, padding: padding)
        layers.append(CnnLayer(name: name, kind: .conv, outSize: out, outChannels: filters,
                               params: convParams(channels, filters, kernel), macs: convMacs(out, filters, channels, kernel)))
        size = out
        channels = filters
    }

    func pool(_ name: String, kernel: Int = 2, stride: Int? = nil) {
        let out = convOutputSize(size, kernel: kernel, stride: stride ?? kernel, padding: 0)
        layers.append(CnnLayer(name: name, kind: .pool, outSize: out, outChannels: channels, params: 0, macs: 0))
        size = out
    }

    func dense(_ name: String, _ units: Int) {
        let inFeatures = layers.last?.kind == .dense ? channels : size * size * channels
        layers.append(CnnLayer(name: name, kind: .dense, outSize: 1, outChannels: units,
                               params: denseParams(inFeatures, units), macs: Int64(inFeatures) * Int64(units)))
        size = 1
        channels = units
    }
}

/// LeNet-5 on 32×32 greyscale, full connectivity (the 61,706 figure).
func lenet5() -> [CnnLayer] {
    let s = StackBuilder(32, 1)
    s.conv("C1 conv 5×5", 6, 5)
    s.pool("S2 pool 2×2")
    s.conv("C3 conv 5×5", 16, 5)
    s.pool("S4 pool 2×2")
    s.conv("C5 conv 5×5", 120, 5)
    s.dense("F6 dense", 84)
    s.dense("output", 10)
    return s.layers
}

/// AlexNet on 227×227×3, ungrouped.
func alexNet() -> [CnnLayer] {
    let s = StackBuilder(227, 3)
    s.conv("conv1 11×11 s4", 96, 11, stride: 4)
    s.pool("pool1 3×3 s2", kernel: 3, stride: 2)
    s.conv("conv2 5×5", 256, 5, padding: 2)
    s.pool("pool2 3×3 s2", kernel: 3, stride: 2)
    s.conv("conv3 3×3", 384, 3, padding: 1)
    s.conv("conv4 3×3", 384, 3, padding: 1)
    s.conv("conv5 3×3", 256, 3, padding: 1)
    s.pool("pool5 3×3 s2", kernel: 3, stride: 2)
    s.dense("fc6", 4096)
    s.dense("fc7", 4096)
    s.dense("fc8", 1000)
    return s.layers
}

/// VGG-16 on 224×224×3.
func vgg16() -> [CnnLayer] {
    let s = StackBuilder(224, 3)
    s.conv("conv1_1 3×3", 64, 3, padding: 1)
    s.conv("conv1_2 3×3", 64, 3, padding: 1)
    s.pool("pool1")
    s.conv("conv2_1 3×3", 128, 3, padding: 1)
    s.conv("conv2_2 3×3", 128, 3, padding: 1)
    s.pool("pool2")
    s.conv("conv3_1 3×3", 256, 3, padding: 1)
    s.conv("conv3_2 3×3", 256, 3, padding: 1)
    s.conv("conv3_3 3×3", 256, 3, padding: 1)
    s.pool("pool3")
    s.conv("conv4_1 3×3", 512, 3, padding: 1)
    s.conv("conv4_2 3×3", 512, 3, padding: 1)
    s.conv("conv4_3 3×3", 512, 3, padding: 1)
    s.pool("pool4")
    s.conv("conv5_1 3×3", 512, 3, padding: 1)
    s.conv("conv5_2 3×3", 512, 3, padding: 1)
    s.conv("conv5_3 3×3", 512, 3, padding: 1)
    s.pool("pool5")
    s.dense("fc6", 4096)
    s.dense("fc7", 4096)
    s.dense("fc8", 1000)
    return s.layers
}

// MARK: - Inception

struct InceptionBranch { let name: String; let naiveMacs: Int64; let reducedMacs: Int64; let outChannels: Int }

/// GoogLeNet's inception 3a, costs per output position.
func inception3a(_ inChannels: Int = 192) -> [InceptionBranch] {
    let c = Int64(inChannels)
    return [
        InceptionBranch(name: "1×1 → 64", naiveMacs: c * 64, reducedMacs: c * 64, outChannels: 64),
        InceptionBranch(name: "3×3 → 128", naiveMacs: c * 128 * 9, reducedMacs: c * 96 + 96 * 128 * 9, outChannels: 128),
        InceptionBranch(name: "5×5 → 32", naiveMacs: c * 32 * 25, reducedMacs: c * 16 + 16 * 32 * 25, outChannels: 32),
        InceptionBranch(name: "pool → 1×1 → 32", naiveMacs: c * 32, reducedMacs: c * 32, outChannels: 32),
    ]
}

// MARK: - DenseNet

struct DenseBlockLayer { let index: Int; let inChannels: Int; let params: Int64 }

func denseBlock(k0: Int = 64, growth: Int = 32, layers: Int = 6) -> [DenseBlockLayer] {
    (1...layers).map { l in
        let inChannels = k0 + growth * (l - 1)
        let bottleneck = 4 * growth
        return DenseBlockLayer(index: l, inChannels: inChannels,
                               params: convParams(inChannels, bottleneck, 1, bias: false) + convParams(bottleneck, growth, 3, bias: false))
    }
}

func denseBlockConnections(_ layers: Int) -> Int { layers * (layers + 1) / 2 }

// MARK: - ResNet: the identity path and the gradient

struct DepthGradients {
    /// Gradient norm reaching each depth; index 0 is nearest the input.
    let plain: [Double]
    let residual: [Double]
    var plainSurvival: Double { plain.first! / max(plain.last!, 1e-300) }
    var residualSurvival: Double { residual.first! / max(residual.last!, 1e-300) }
}

func residualVsPlainGradient(depth: Int = 30, width: Int = 24, gain: Double = 0.55, seed: Int = 37) -> DepthGradients {
    var rng = Lcg(seed)
    func gaussian() -> Double {
        let u1 = max(rng.next(), 1e-9)
        let u2 = rng.next()
        return (-2 * log(u1)).squareRoot() * cos(2 * .pi * u2)
    }
    let scale = gain * (2.0 / Double(width)).squareRoot()
    var w: [[[Double]]] = []
    for _ in 0..<depth {
        var layer: [[Double]] = []
        for _ in 0..<width { layer.append((0..<width).map { _ in gaussian() * scale }) }
        w.append(layer)
    }
    let x0 = (0..<width).map { _ in gaussian() }

    func run(_ residual: Bool) -> [Double] {
        var pre: [[Double]] = []
        var a = x0
        for l in 0..<depth {
            let z = (0..<width).map { j in (0..<width).reduce(0.0) { $0 + w[l][j][$1] * a[$1] } }
            pre.append(z)
            let f = z.map { max(0, $0) }
            a = residual ? (0..<width).map { a[$0] + f[$0] } : f
        }
        var delta = [Double](repeating: 1 / Double(width).squareRoot(), count: width)
        var norms = [Double](repeating: 0, count: depth)
        for l in stride(from: depth - 1, through: 0, by: -1) {
            norms[l] = delta.reduce(0) { $0 + $1 * $1 }.squareRoot()
            let through = (0..<width).map { pre[l][$0] > 0 ? delta[$0] : 0 }
            let back = (0..<width).map { i in (0..<width).reduce(0.0) { $0 + w[l][$1][i] * through[$1] } }
            delta = residual ? (0..<width).map { delta[$0] + back[$0] } : back
        }
        return norms
    }
    return DepthGradients(plain: run(false), residual: run(true))
}

// MARK: - Pooling and translation

struct ShiftAgreement { let unchangedRaw: Double; let unchangedPooled: Double; let relativeChangeRaw: Double; let relativeChangePooled: Double }

func shiftAgreement(_ image: Matrix, _ kernel: Matrix, poolSize: Int = 2, shift: Int = 1, tolerance: Double = 1e-9) -> ShiftAgreement {
    let shifted = image.map { [Double](repeating: 0, count: shift) + $0.dropLast(shift) }
    let a = conv2d(image, kernel), b = conv2d(shifted, kernel)
    func unchanged(_ x: Matrix, _ y: Matrix) -> Double {
        let cells = x.indices.flatMap { r in x[r].indices.map { abs(x[r][$0] - y[r][$0]) <= tolerance } }
        return Double(cells.filter { $0 }.count) / Double(cells.count)
    }
    func relativeChange(_ x: Matrix, _ y: Matrix) -> Double {
        let delta = x.indices.reduce(0.0) { acc, r in acc + x[r].indices.reduce(0.0) { $0 + abs(x[r][$1] - y[r][$1]) } }
        let magnitude = x.reduce(0.0) { $0 + $1.reduce(0.0) { $0 + abs($1) } }
        return magnitude == 0 ? 0 : delta / magnitude
    }
    let pa = pool2d(a, size: poolSize), pb = pool2d(b, size: poolSize)
    return ShiftAgreement(unchangedRaw: unchanged(a, b), unchangedPooled: unchanged(pa, pb),
                          relativeChangeRaw: relativeChange(a, b), relativeChangePooled: relativeChange(pa, pb))
}

// MARK: - EfficientNet

struct CompoundScale {
    let depth: Double, width: Double, resolution: Double
    var flopsFactor: Double { depth * width * width * resolution * resolution }
}

func compoundScale(_ phi: Double, alpha: Double = 1.2, beta: Double = 1.1, gamma: Double = 1.15) -> CompoundScale {
    CompoundScale(depth: pow(alpha, phi), width: pow(beta, phi), resolution: pow(gamma, phi))
}

// MARK: - Vision Transformer

struct VitShape { let tokens: Int; let patchEmbeddingParams: Int64; let positionParams: Int64; let attentionPairs: Int64 }

func vitShape(image: Int = 224, patch: Int = 16, dim: Int = 768, channels: Int = 3) -> VitShape {
    let grid = image / patch
    let tokens = grid * grid + 1
    return VitShape(tokens: tokens, patchEmbeddingParams: denseParams(patch * patch * channels, dim),
                    positionParams: Int64(tokens) * Int64(dim), attentionPairs: Int64(tokens) * Int64(tokens))
}
