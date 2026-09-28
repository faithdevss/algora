import SwiftUI

// Port of FeatureMapSection.kt: the CNN labs (C3) and object detection (C4). Render parts mixed per
// frame: grids (input, kernel, feature map with the window outlined), a scene (image with boxes,
// masks or a cell grid), a layer stack, bars and a plot. Everything drawn is computed in CnnMath /
// DetectionMath; the three mechanics labs rebuild every frame from the user's kernel/stride/padding.

struct FmControls: Hashable { var kernel = 3; var stride = 1; var padding = 0 }

private enum GridTone { case signed, count, heat }

private struct Cell: Hashable { let r: Int; let c: Int }

private struct FmGrid { let label: String; let values: Matrix; var highlight: Set<Cell> = []; var tone: GridTone = .signed }
private struct FmStackRow { let name: String; let shape: String; let params: Int64; var note = ""; var emphasis = false }
private struct FmBar { let label: String; let values: [Double]; let color: Color; var captions: [String] = [] }
private struct FmCurve { let label: String; let points: [(Float, Float)]; let color: Color }
private struct FmPlot { let label: String; let curves: [FmCurve]; let xRange: ClosedRange<Float>; let yRange: ClosedRange<Float>; var logY = false }
private struct SceneBox { let box: BoxF; let label: String; let color: Color; var faint = false }
private struct FmScene { let label: String; let size: Double; var boxes: [SceneBox] = []; var gridCells = 0; var mask: [[Int]]? }

private struct FmFrame {
    let status: String
    var grids: [FmGrid] = []
    var scene: FmScene?
    var stack: [FmStackRow] = []
    var bars: [FmBar] = []
    var plot: FmPlot?
    var readout: String?
}

private struct FmConfig { let intro: String; let legend: [(Color, String)]; var controls = false; let build: (FmControls) -> [FmFrame] }

private let windowColor = Color(hex: 0x7C3AED)
private let signalColor = SimColors.blue
private let negativeColor = Color(hex: 0xEC4899)
private let savingColor = SimColors.green
private let costColor = Color(hex: 0xF97316)
private let mutedColor = SimColors.grey

private func compact(_ v: Int64) -> String {
    if v >= 1_000_000_000 { return String(format: "%.2fB", Double(v) / 1e9) }
    if v >= 1_000_000 { return String(format: "%.2fM", Double(v) / 1e6) }
    if v >= 10_000 { return String(format: "%.1fk", Double(v) / 1e3) }
    return "\(v)"
}

private func percent(_ part: Int64, _ whole: Int64) -> String { whole == 0 ? "0%" : String(format: "%.1f%%", 100.0 * Double(part) / Double(whole)) }

private func window(_ row: Int, _ col: Int, _ kernel: Int, _ stride: Int, _ padding: Int, _ size: Int) -> Set<Cell> {
    var out = Set<Cell>()
    for kr in 0..<kernel {
        for kc in 0..<kernel {
            let r = row * stride + kr - padding, c = col * stride + kc - padding
            if (0..<size).contains(r) && (0..<size).contains(c) { out.insert(Cell(r: r, c: c)) }
        }
    }
    return out
}

/// A 7×7 patch with a vertical edge and a horizontal bar.
private let samplePatch: Matrix = (0..<7).map { r in (0..<7).map { c in r == 3 ? 1.0 : c >= 4 ? 1.0 : 0.0 } }

/// A vertical-edge detector at an odd size; 1×1 degenerates to identity.
private func edgeKernel(_ size: Int) -> Matrix {
    if size == 1 { return [[1]] }
    let mid = size / 2
    return (0..<size).map { r in
        (0..<size).map { c in
            let weight: Double = r == mid ? 2 : 1
            return c < mid ? weight : c > mid ? -weight : 0
        }
    }
}

private func stackRows(_ layers: [CnnLayer], _ upTo: Int, _ total: Int64) -> [FmStackRow] {
    layers.prefix(upTo).enumerated().map { i, layer in
        FmStackRow(name: layer.name, shape: layer.shape, params: layer.params,
                   note: layer.params == 0 ? "no parameters" : "\(percent(layer.params, total)) of the model", emphasis: i == upTo - 1)
    }
}

private func f1(_ x: Double) -> String { fx(x, 1) }
private func e1(_ x: Double) -> String { String(format: "%.1e", x) }

// MARK: - Convolution

private func convolutionFrames(_ controls: FmControls) -> [FmFrame] {
    let k = controls.kernel, s = controls.stride, p = controls.padding
    let image = samplePatch
    let n = image.count
    let kernel = edgeKernel(k)
    let out = convOutputSize(n, kernel: k, stride: s, padding: p)
    var frames = [FmFrame(
        status: "A dense layer on this \(n)×\(n) patch needs \(n * n) weights for every unit, and each weight is tied to one pixel position. A convolution slides \(k * k) weights over every position instead — the same detector, wherever the thing it detects happens to be.",
        grids: [FmGrid(label: "input \(n)×\(n)", values: image), FmGrid(label: "kernel \(k)×\(k)", values: kernel)],
        readout: "output ⌊(\(n) + 2·\(p) − \(k))/\(s)⌋ + 1 = \(out)"
    )]
    if out == 0 {
        frames.append(FmFrame(status: "With k = \(k), stride \(s) and padding \(p) the window does not fit inside a \(n)×\(n) input at all: \(n) + 2·\(p) is less than \(k). The layer cannot be built — the geometry has to be fixed before the weights matter.",
                              grids: [FmGrid(label: "input \(n)×\(n)", values: image)]))
        return frames
    }
    let feature = conv2d(image, kernel, stride: s, padding: p)
    var positions: [(Int, Int)] = []
    for pos in [(0, 0), (0, out / 2), (out / 2, out / 2), (out - 1, out - 1)] where !positions.contains(where: { $0 == pos }) { positions.append(pos) }
    for (r, c) in positions.prefix(4) {
        let partial = (0..<out).map { rr in (0..<out).map { cc in rr < r || (rr == r && cc <= c) ? feature[rr][cc] : 0 } }
        let value = feature[r][c]
        frames.append(FmFrame(
            status: "Window at output (\(r), \(c)) reads input rows \(r * s - p)–\(r * s - p + k - 1): multiply the \(k * k) overlapping pixels by the kernel and sum → \(f1(value))." + (abs(value) > 0.5 ? " Strong response; the edge runs through this window." : " Flat here, so nothing fires."),
            grids: [
                FmGrid(label: "input \(n)×\(n)", values: image, highlight: window(r, c, k, s, p, n)),
                FmGrid(label: "kernel \(k)×\(k)", values: kernel),
                FmGrid(label: "feature map \(out)×\(out)", values: partial, highlight: [Cell(r: r, c: c)]),
            ]
        ))
    }
    let convWeights = Int64(k * k + 1)
    let denseWeights = Int64(n * n + 1) * Int64(out * out)
    frames.append(FmFrame(
        status: "The finished map. Those \(k * k) weights plus a bias were reused \(out * out) times; a dense layer producing the same \(out * out) outputs would hold \(compact(denseWeights)) parameters. Weight sharing is the entire saving, and translation equivariance is what you get for free with it.",
        grids: [FmGrid(label: "feature map \(out)×\(out)", values: feature)],
        readout: "conv \(convWeights) params · dense \(compact(denseWeights)) params · \(fx(Double(denseWeights) / Double(convWeights), 0))× fewer"
    ))
    let rf = receptiveField([LayerGeometry](repeating: LayerGeometry(kernel: k, stride: s), count: 3))
    frames.append(FmFrame(
        status: "Stack three of these layers and the receptive field grows to \(rf.last!) input pixels — each output still touches only \(k * k) inputs of the layer below it. Depth is how a convolution stops being local without ever using a large kernel.",
        bars: [FmBar(label: "receptive field after layer 1 · 2 · 3 (input pixels)", values: rf.map(Double.init), color: signalColor, captions: rf.map { "\($0) px" })],
        readout: "rᵢ = rᵢ₋₁ + (k − 1)·∏s = \(rf.map(String.init).joined(separator: " → "))"
    ))
    return frames
}

// MARK: - Pooling

private func poolingFrames(_ controls: FmControls) -> [FmFrame] {
    let image: Matrix = (0..<9).map { r in (0..<9).map { c in (3...5).contains(r) && (3...5).contains(c) ? 1 : 0 } }
    let kernel = edgeKernel(3)
    let feature = conv2d(image, kernel)
    let size = min(max(controls.kernel, 2), 3)
    let maxed = pool2d(feature, size: size, stride: size, mode: .max)
    let averaged = pool2d(feature, size: size, stride: size, mode: .average)
    let agreement = shiftAgreement(image, kernel, poolSize: size)
    let twoPixel = shiftAgreement(image, kernel, poolSize: size, shift: 2)
    let fs = feature.count
    let strided = conv2d(image, kernel, stride: 2)
    func pc(_ x: Double) -> String { fx(x * 100, 0) }
    return [
        FmFrame(status: "Start from a real feature map: the \(fs)×\(fs) response of a vertical edge detector to a small bright square. Pooling never looks at the image — it summarises what a layer already found.",
                grids: [FmGrid(label: "input 9×9", values: image), FmGrid(label: "feature map \(fs)×\(fs)", values: feature)]),
        FmFrame(status: "Max pooling over \(size)×\(size) blocks keeps the strongest response in each block and throws away where inside the block it was. The map shrinks from \(fs)² to \(maxed.count)² — a \(f1(Double(fs * fs) / Double(maxed.count * maxed.count)))× drop in activations carried to the next layer — and it has no parameters to learn.",
                grids: [FmGrid(label: "max pool \(size)×\(size)", values: maxed, highlight: [Cell(r: 0, c: 0)])],
                readout: "\(fs)×\(fs) → \(maxed.count)×\(maxed.count), 0 parameters"),
        FmFrame(status: "Average pooling over the same blocks answers a different question: not \"was the feature here?\" but \"how much of it was here?\". Averages dilute a single strong response, which is why max won for detection and average survives as global average pooling at the top of a network.",
                grids: [FmGrid(label: "max pool", values: maxed), FmGrid(label: "average pool", values: averaged)]),
        FmFrame(status: "The invariance claim, measured rather than asserted. Shift the image one pixel right: the raw response moves by \(pc(agreement.relativeChangeRaw))% of its own magnitude, and the \(size)×\(size) pooled map by \(pc(agreement.relativeChangePooled))%. Shift it two pixels and the pooled map moves \(pc(twoPixel.relativeChangePooled))% against the raw map's \(pc(twoPixel.relativeChangeRaw))%. Pooling halves the sensitivity; it does not remove it, and the gap closes as the shift grows past the window. Real invariance is built by stacking layers of this, not by making one window larger.",
                bars: [FmBar(label: "response change after a shift (relative to map magnitude)",
                             values: [agreement.relativeChangeRaw, agreement.relativeChangePooled, twoPixel.relativeChangeRaw, twoPixel.relativeChangePooled],
                             color: costColor, captions: ["1px raw", "1px pooled", "2px raw", "2px pooled"])],
                readout: "1-pixel shift: |Δ| \(pc(agreement.relativeChangeRaw))% → \(pc(agreement.relativeChangePooled))% · cells unchanged \(pc(agreement.unchangedRaw))% → \(pc(agreement.unchangedPooled))%"),
        FmFrame(status: "The modern alternative: a stride-2 convolution downsamples in the same step as it filters, so the network learns how to reduce instead of being told. ResNet still pools; many architectures after it dropped pooling entirely except for the global average at the end.",
                grids: [FmGrid(label: "stride-2 conv \(strided.count)×\(strided.count)", values: strided), FmGrid(label: "max pool", values: maxed)]),
    ]
}

// MARK: - Padding and strides

private func paddingStrideFrames(_ controls: FmControls) -> [FmFrame] {
    let k = controls.kernel, s = controls.stride, p = controls.padding
    let image = samplePatch
    let n = image.count
    let kernel = edgeKernel(k)
    let out = convOutputSize(n, kernel: k, stride: s, padding: p)
    let counts = windowCounts(n, kernel: k, stride: s, padding: p)
    let corner = counts[0][0], centre = counts[n / 2][n / 2]
    let same = samePadding(k)
    let sameOut = convOutputSize(n, kernel: k, stride: 1, padding: same)
    let valid = convOutputSize(n, kernel: k, stride: 1, padding: 0)
    var unpadded = 224
    let shrink = (1...10).map { _ -> Int in unpadded = convOutputSize(unpadded, kernel: k, stride: 1, padding: 0); return unpadded }
    let strides = (1...4).map { ($0, convOutputSize(n, kernel: k, stride: $0, padding: p)) }
    return [
        FmFrame(status: "One formula governs every convolution's output size: ⌊(n + 2p − k)/s⌋ + 1. With n = \(n), k = \(k), s = \(s) and p = \(p) that is \(out). Getting it wrong is the single most common shape error in a vision model.",
                grids: [FmGrid(label: "input \(n)×\(n)", values: image), FmGrid(label: "kernel \(k)×\(k)", values: kernel)],
                readout: "⌊(\(n) + 2·\(p) − \(k))/\(s)⌋ + 1 = \(out)"),
        FmFrame(status: "How many windows each pixel is read by. The corner is used \(corner) time\(corner == 1 ? "" : "s") and the centre \(centre) — with p = 0 the border of every image is systematically under-weighted before a single weight is learned, and after ten layers the neglected band is ten pixels wide.",
                grids: [FmGrid(label: "reads per pixel", values: counts.map { $0.map(Double.init) }, tone: .count)],
                readout: "corner \(corner) · centre \(centre) reads"),
        FmFrame(status: "\"Same\" padding is p = (k − 1)/2 = \(same) at stride 1, which puts the output back at \(sameOut)×\(sameOut). It is not a separate operation — it is this formula solved for p. \"Valid\" padding means p = 0 and accepting the shrinkage.",
                grids: [FmGrid(label: "input padded to \(n + 2 * same)×\(n + 2 * same)", values: padImage(image, same))],
                readout: "valid \(valid)×\(valid) · same \(sameOut)×\(sameOut)"),
        FmFrame(status: "Ten unpadded \(k)×\(k) layers on a 224×224 input leave \(shrink.last!)×\(shrink.last!): each layer costs k − 1 = \(k - 1) pixels. VGG pads every layer for exactly this reason — the depth was the point, and unpadded depth eats the image.",
                plot: FmPlot(label: "map size through ten unpadded layers", curves: [FmCurve(label: "size", points: shrink.enumerated().map { (Float($0 + 1), Float($1)) }, color: costColor)],
                             xRange: 1...10, yRange: 0...224),
                readout: "224 → \(shrink.last!) after 10 layers"),
        FmFrame(status: "Stride is the other lever, and it divides rather than subtracts: \(strides.map { "s = \($0.0) → \($0.1)×\($0.1)" }.joined(separator: ", ")). Stride 2 is a halving, which is why it replaced pooling in most architectures after ResNet.",
                bars: [FmBar(label: "output size by stride", values: strides.map { Double($0.1) }, color: signalColor, captions: strides.map { "s=\($0.0)" })]),
    ]
}

// MARK: - Architecture walkers

private func architectureFrames(_ layers: [CnnLayer], opening: String, closing: (Int64, Int64) -> String, extras: (Int64, Int64) -> [FmFrame] = { _, _ in [] }) -> [FmFrame] {
    let total = layers.totalParams, macs = layers.totalMacs
    var frames = [FmFrame(status: opening, stack: stackRows(layers, 1, total), readout: "input → \(layers[0].shape)")]
    for i in 2...layers.count {
        let layer = layers[i - 1]
        let detail: String
        switch layer.kind {
        case .conv: detail = "\(compact(layer.params)) parameters (\(percent(layer.params, total)) of the model), \(compact(layer.macs)) multiply-accumulates (\(percent(layer.macs, macs)) of the compute)."
        case .pool: detail = "No parameters and no learning: the map is halved and passed on."
        case .dense: detail = "\(compact(layer.params)) parameters — \(percent(layer.params, total)) of the whole model in one layer, for \(percent(layer.macs, macs)) of the compute."
        }
        frames.append(FmFrame(status: "\(layer.name) → \(layer.shape). " + detail, stack: stackRows(layers, i, total)))
    }
    let pc = layers.paramsIn(.conv), pd = layers.paramsIn(.dense), mc = layers.macsIn(.conv), md = layers.macsIn(.dense)
    frames.append(FmFrame(
        status: closing(total, macs), stack: stackRows(layers, layers.count, total),
        bars: [
            FmBar(label: "parameters · convolutions vs dense layers", values: [Double(pc), Double(pd)], color: signalColor, captions: ["conv \(percent(pc, total))", "dense \(percent(pd, total))"]),
            FmBar(label: "multiply-accumulates · convolutions vs dense layers", values: [Double(mc), Double(md)], color: costColor, captions: ["conv \(percent(mc, macs))", "dense \(percent(md, macs))"]),
        ],
        readout: "\(compact(total)) parameters · \(compact(macs)) MACs per image"
    ))
    return frames + extras(total, macs)
}

private func lenetFrames() -> [FmFrame] {
    architectureFrames(lenet5(),
                       opening: "LeNet-5, 1998, reading a 32×32 greyscale digit. Every idea a modern vision model uses is already here: local receptive fields, shared weights, subsampling, and a classifier on top of learned features.",
                       closing: { total, macs in "\(total) parameters in total — sixty thousand, on hardware from 1998, and it read cheques in production for a decade. \(compact(macs)) multiply-accumulates per digit. The architecture was right; what was missing was data and compute, which is the whole story of the fourteen years to AlexNet." })
}

private func alexNetFrames() -> [FmFrame] {
    let layers = alexNet()
    return architectureFrames(layers,
        opening: "AlexNet, 2012, on 227×227 colour. It won ImageNet by 10.8 percentage points and restarted the field. Structurally it is LeNet made deep and wide — the changes that mattered were ReLU, dropout, augmentation and two GPUs.",
        closing: { total, macs in "\(compact(total)) parameters, \(compact(macs)) MACs. Read the split: the three dense layers hold \(percent(layers.paramsIn(.dense), total)) of the parameters and do \(percent(layers.macsIn(.dense), macs)) of the arithmetic. Almost the whole model is a classifier bolted onto a small feature extractor — which is exactly what later architectures deleted." },
        extras: { total, _ in [FmFrame(
            status: "fc6 alone is \(compact(denseParams(9216, 4096))) parameters, because flattening a 6×6×256 map into 9,216 features and connecting all of them to 4,096 units is the most expensive thing you can do with a feature map. Dropout at p = 0.5 on these two layers was not a refinement; without it the model memorised the training set.",
            bars: [FmBar(label: "parameters per dense layer", values: [Double(denseParams(9216, 4096)), Double(denseParams(4096, 4096)), Double(denseParams(4096, 1000))], color: negativeColor, captions: ["fc6", "fc7", "fc8"])],
            readout: "fc6 = \(percent(denseParams(9216, 4096), total)) of AlexNet")] })
}

private func vggFrames() -> [FmFrame] {
    let layers = vgg16()
    return architectureFrames(layers,
        opening: "VGG-16, 2014. One decision, applied everywhere: every convolution is 3×3, stride 1, padded; every pool halves the map; channels double after each pool. The paper's contribution is that depth alone, held at the smallest useful kernel, keeps improving accuracy.",
        closing: { total, macs in "\(compact(total)) parameters and \(compact(macs)) MACs per image. The conv stack — thirteen layers of it — is only \(percent(layers.paramsIn(.conv), total)) of the parameters and \(percent(layers.macsIn(.conv), macs)) of the compute. VGG is a small, extremely expensive feature extractor wearing a huge, nearly free classifier." },
        extras: { _, _ in
            let stacked = 2 * convParams(512, 512, 3, bias: false)
            let single = convParams(512, 512, 5, bias: false)
            let rf = receptiveField([LayerGeometry(kernel: 3, stride: 1), LayerGeometry(kernel: 3, stride: 1)]).last!
            return [FmFrame(
                status: "Why 3×3 and never 5×5: two stacked 3×3 layers see the same 5×5 receptive field, with \(compact(stacked)) parameters against \(compact(single)) at 512 channels — 28% fewer — and a non-linearity in between that the single large kernel does not have. Three stacked 3×3s reach 7×7 the same way.",
                bars: [FmBar(label: "parameters for a 5×5 receptive field at 512 channels", values: [Double(stacked), Double(single)], color: savingColor, captions: ["two 3×3", "one 5×5"])],
                readout: "receptive field \(rf)×\(rf) either way")]
        })
}

// MARK: - Inception / ResNet / DenseNet / MobileNet / EfficientNet / ViT

private func inceptionFrames() -> [FmFrame] {
    let branches = inception3a()
    let naive = branches.reduce(Int64(0)) { $0 + $1.naiveMacs }
    let reduced = branches.reduce(Int64(0)) { $0 + $1.reducedMacs }
    let shortNames = branches.map { $0.name.components(separatedBy: " →")[0] }
    let vgg = vgg16().totalParams
    return [
        FmFrame(status: "Inception's question is not \"how deep\" but \"which kernel size\" — and its answer is to stop choosing. One module runs 1×1, 3×3, 5×5 and a pooling branch over the same input and concatenates the results, so the network picks the scale per feature rather than per layer.",
                stack: branches.map { FmStackRow(name: $0.name, shape: "28×28×\($0.outChannels)", params: $0.reducedMacs, note: "cost per position") },
                readout: "output \(branches.reduce(0) { $0 + $1.outChannels }) channels, all four scales concatenated"),
        FmFrame(status: "Done naively this is unaffordable. The 5×5 branch alone costs \(compact(branches[2].naiveMacs)) multiply-accumulates per position at 192 input channels, and stacking modules multiplies the input width, so cost grows quadratically with depth.",
                bars: [FmBar(label: "naive cost per branch (MACs per position)", values: branches.map { Double($0.naiveMacs) }, color: costColor, captions: shortNames)],
                readout: "naive module: \(compact(naive)) MACs per position"),
        FmFrame(status: "The fix is a 1×1 convolution in front of each expensive kernel. A 1×1 has no spatial extent at all — it is a learned linear map across channels — so it can compress 192 channels to 16 before the 5×5 ever runs. That branch drops from \(compact(branches[2].naiveMacs)) to \(compact(branches[2].reducedMacs)) MACs, a \(f1(Double(branches[2].naiveMacs) / Double(branches[2].reducedMacs)))× cut.",
                bars: [FmBar(label: "with 1×1 reductions (MACs per position)", values: branches.map { Double($0.reducedMacs) }, color: savingColor, captions: shortNames)],
                readout: "module: \(compact(naive)) → \(compact(reduced)) MACs (\(f1(Double(naive) / Double(reduced)))× cheaper)"),
        FmFrame(status: "GoogLeNet stacks nine of these modules and ends with global average pooling instead of dense layers, which is why it holds about 6.8M parameters against VGG-16's \(compact(vgg)) while scoring better on ImageNet. The bottleneck idea outlived the module: every ResNet-50 block and every MobileNet block is a 1×1 sandwich.",
                stack: [
                    FmStackRow(name: "GoogLeNet", shape: "9 inception modules", params: 6_800_000, note: "global average pool, no fc stack"),
                    FmStackRow(name: "VGG-16", shape: "13 conv + 3 dense", params: vgg, note: "89% of it in the dense layers", emphasis: true),
                ],
                readout: "6.8M vs \(compact(vgg)) parameters, better top-5 error"),
    ]
}

private func resNetFrames() -> [FmFrame] {
    let depth = 30
    let g = residualVsPlainGradient(depth: depth)
    func curve(_ values: [Double], _ label: String, _ color: Color) -> FmCurve {
        FmCurve(label: label, points: values.enumerated().map { (Float($0 + 1), Float(log10(max($1, 1e-12)))) }, color: color)
    }
    return [
        FmFrame(status: "The problem ResNet was built for is not overfitting. A 56-layer plain network had *higher training* error than a 20-layer one — it could not even fit the data it had. Depth was making optimisation harder, not the model weaker.",
                stack: [
                    FmStackRow(name: "plain block", shape: "x → conv → ReLU → conv", params: 0, note: "output must recompute everything"),
                    FmStackRow(name: "residual block", shape: "x → conv → ReLU → conv → + x", params: 0, note: "output only has to learn the difference", emphasis: true),
                ]),
        FmFrame(status: "The change is one addition. If the best thing a block can do is nothing, a plain block has to learn the identity out of its weights; a residual block gets it by driving F(x) to zero, which is what weight decay pushes it toward anyway. The easy case became the default case.",
                readout: "y = F(x) + x"),
        FmFrame(status: "What that does to the backward pass, measured on identical weights. Starting from a unit gradient at the output, the plain stack delivers \(e1(g.plainSurvival)) of it to the first layer — eight orders of magnitude gone. The residual stack delivers \(e1(g.residualSurvival)): the identity path differentiates to 1, so every layer's gradient is the output's plus a correction, and no weight can attenuate it away.",
                plot: FmPlot(label: "gradient norm by depth (log₁₀)", curves: [curve(g.plain, "plain", negativeColor), curve(g.residual, "residual", savingColor)],
                             xRange: 1...Float(depth), yRange: -12...5, logY: true),
                readout: "gradient at layer 1 — plain \(e1(g.plainSurvival)) · residual \(e1(g.residualSurvival))"),
        FmFrame(status: "Note which way the residual curve runs: summing a correction at every block makes the gradient *grow* toward the input rather than shrink, \(fx(g.residualSurvival, 0))× here. That is a far easier problem than vanishing — it is what batch normalisation in each block, and initialising the last BN's γ to zero so a fresh block starts as exactly the identity, are there to hold in range.",
                readout: "∂L/∂x_ℓ = ∂L/∂x_L · ∏(1 + ∂F/∂x) — the 1 is the whole trick"),
        FmFrame(status: "With that, depth stopped costing anything: ResNet-152 trains where a 30-layer plain network will not. Deeper ResNets use the bottleneck block — 1×1 down, 3×3, 1×1 up — so ResNet-50 holds about 25.6M parameters, a fifth of VGG-16's, for far better accuracy.",
                stack: [
                    FmStackRow(name: "basic block (ResNet-18/34)", shape: "3×3 → 3×3, + x", params: 2 * convParams(64, 64, 3, bias: false), note: "at 64 channels"),
                    FmStackRow(name: "bottleneck block (ResNet-50+)", shape: "1×1 → 3×3 → 1×1, + x",
                               params: convParams(256, 64, 1, bias: false) + convParams(64, 64, 3, bias: false) + convParams(64, 256, 1, bias: false),
                               note: "same 3×3 cost, four times the width", emphasis: true),
                ],
                readout: "ResNet-50 ≈ 25.6M params vs VGG-16 \(compact(vgg16().totalParams))"),
    ]
}

private func denseNetFrames() -> [FmFrame] {
    let growth = 32
    let block = denseBlock(k0: 64, growth: growth, layers: 6)
    let connections = denseBlockConnections(6)
    let caps = block.map { "ℓ\($0.index)" }
    return [
        FmFrame(status: "DenseNet takes ResNet's shortcut and changes the operator: concatenate instead of add. Layer ℓ receives the feature maps of every earlier layer in its block, so nothing has to be re-derived and features stay available all the way to the classifier.",
                stack: block.map { FmStackRow(name: "layer \($0.index)", shape: "in \($0.inChannels) ch → +\(growth) ch", params: $0.params, note: "1×1 bottleneck then 3×3") },
                readout: "\(connections) direct connections in a 6-layer block, not 6"),
        FmFrame(status: "Channels arriving at layer ℓ are k₀ + k(ℓ − 1) = 64 + 32(ℓ − 1): \(block.map { "\($0.inChannels)" }.joined(separator: ", ")). The growth rate k is deliberately small — each layer contributes \(growth) new maps and reuses everything else, which is why a DenseNet is narrow and still expressive.",
                bars: [FmBar(label: "input channels per layer", values: block.map { Double($0.inChannels) }, color: signalColor, captions: caps)]),
        FmFrame(status: "Without the 1×1 bottleneck the 3×3 would face the whole concatenation, and cost would grow quadratically down the block. The bottleneck pins its input at 4k = \(4 * growth) channels, so per-layer cost stays nearly flat: \(block.map { compact($0.params) }.joined(separator: ", ")) parameters.",
                bars: [FmBar(label: "parameters per layer (with bottleneck)", values: block.map { Double($0.params) }, color: savingColor, captions: caps)],
                readout: "block total \(compact(block.reduce(Int64(0)) { $0 + $1.params })) parameters"),
        FmFrame(status: "The trade is memory, not parameters: DenseNet-121 reaches ResNet-50 accuracy with about 8M parameters against 25.6M, but every intermediate concatenation has to be kept live for the backward pass, so training memory is the binding constraint rather than FLOPs.",
                stack: [
                    FmStackRow(name: "DenseNet-121", shape: "growth 32, 4 blocks", params: 7_980_000, note: "≈8.0M parameters", emphasis: true),
                    FmStackRow(name: "ResNet-50", shape: "bottleneck, 4 stages", params: 25_557_032, note: "≈25.6M parameters"),
                ]),
    ]
}

private func mobileNetFrames() -> [FmFrame] {
    let inC = 128, outC = 256, k = 3
    let standard = convParams(inC, outC, k, bias: false)
    let separable = depthwiseSeparableParams(inC, outC, k)
    let ratio = separableCostRatio(outC, k)
    let vgg = vgg16()
    return [
        FmFrame(status: "A standard convolution filters and combines in one operation: every output channel touches every input channel through its own \(k)×\(k) kernel. At \(inC) → \(outC) channels that is \(compact(standard)) parameters for one layer.",
                stack: [FmStackRow(name: "standard \(k)×\(k)", shape: "\(inC) → \(outC) ch", params: standard, note: "filters and mixes at once", emphasis: true)]),
        FmFrame(status: "Depthwise separable convolution splits those two jobs. One \(k)×\(k) kernel per input channel — no mixing — then a 1×1 that mixes channels and does no spatial work at all. Same output shape, \(compact(separable)) parameters.",
                stack: [
                    FmStackRow(name: "depthwise \(k)×\(k)", shape: "\(inC) ch, one filter each", params: convParams(1, inC, k, bias: false), note: "spatial only"),
                    FmStackRow(name: "pointwise 1×1", shape: "\(inC) → \(outC) ch", params: convParams(inC, outC, 1, bias: false), note: "channel mixing only", emphasis: true),
                ],
                readout: "\(compact(standard)) → \(compact(separable)) parameters"),
        FmFrame(status: "The ratio is 1/N + 1/k² — \(fx(ratio, 4)) here, \(f1(1 / ratio))× cheaper — and it does not depend on the spatial size at all. With a 3×3 kernel the 1/9 term dominates once the channel count is large, so the saving parks near 8–9× and stays there.",
                bars: [FmBar(label: "cost ratio by output channels (k = 3)", values: [32, 64, 128, 256, 512].map { separableCostRatio($0, k) }, color: savingColor, captions: ["32", "64", "128", "256", "512"])],
                readout: "1/N + 1/k² = 1/\(outC) + 1/\(k * k) = \(fx(ratio, 4))"),
        FmFrame(status: "MobileNetV1 is that block repeated: 4.2M parameters and 569M MACs, within about 1% of VGG-16's ImageNet accuracy at \(fx(Double(vgg.totalParams) / 4_200_000, 0))× fewer parameters. V2 added inverted residuals — expand with 1×1, filter depthwise, project back down, and put the skip connection between the *narrow* ends, because that is where the information is.",
                stack: [
                    FmStackRow(name: "MobileNetV1", shape: "28 layers, all separable", params: 4_200_000, note: "569M MACs", emphasis: true),
                    FmStackRow(name: "VGG-16", shape: "13 conv + 3 dense", params: vgg.totalParams, note: "\(compact(vgg.totalMacs)) MACs"),
                ]),
    ]
}

private func efficientNetFrames() -> [FmFrame] {
    let phis = (0...6).map(Double.init)
    let scales = phis.map { compoundScale($0) }
    let last = scales.last!
    func pts(_ f: (CompoundScale) -> Double) -> [(Float, Float)] { phis.indices.map { (Float(phis[$0]), Float(f(scales[$0]))) } }
    return [
        FmFrame(status: "Three ways to make a convnet bigger: more layers, more channels, larger input images. Every architecture before this scaled one of them by hand, and each one saturates on its own — accuracy flattens long before the FLOPs do.",
                stack: [
                    FmStackRow(name: "depth d", shape: "more layers", params: 0, note: "cost grows linearly"),
                    FmStackRow(name: "width w", shape: "more channels", params: 0, note: "cost grows with w²"),
                    FmStackRow(name: "resolution r", shape: "larger input", params: 0, note: "cost grows with r²"),
                ]),
        FmFrame(status: "Compound scaling ties them together with one knob: d = 1.2^φ, w = 1.1^φ, r = 1.15^φ. The constants are chosen so that α·β²·γ² = \(fx(1.2 * 1.1 * 1.1 * 1.15 * 1.15, 3)) ≈ 2 — which makes each unit of φ exactly a doubling of FLOPs, and makes the three axes grow in a fixed ratio to each other.",
                readout: "α·β²·γ² ≈ 2, so FLOPs ≈ 2^φ"),
        FmFrame(status: "B0 to B6 is φ = 0 to 6. Depth reaches \(fx(last.depth))×, width \(fx(last.width))× and resolution \(fx(last.resolution))× — resolution grows fastest per unit of compute, which is why the input goes from 224² to 600² across the family while the layer count barely triples.",
                plot: FmPlot(label: "scaling factor by φ", curves: [
                    FmCurve(label: "depth", points: pts(\.depth), color: signalColor),
                    FmCurve(label: "width", points: pts(\.width), color: costColor),
                    FmCurve(label: "resolution", points: pts(\.resolution), color: savingColor),
                ], xRange: 0...6, yRange: 1...3.2),
                readout: "φ = 6 → FLOPs ×\(fx(last.flopsFactor, 0))"),
        FmFrame(status: "The measured payoff: EfficientNet-B7 reached 84.3% ImageNet top-1 with about 66M parameters, against GPipe's 557M for the same accuracy — 8.4× smaller. The B0 backbone itself came from a neural architecture search over MobileNetV2-style inverted-residual blocks, so compound scaling is what was applied to a good small model, not a substitute for having one.",
                bars: [FmBar(label: "parameters at 84% ImageNet top-1", values: [66_000_000, 557_000_000], color: savingColor, captions: ["EfficientNet-B7", "GPipe"])],
                readout: "8.4× fewer parameters, 6.1× faster inference"),
    ]
}

private func vitFrames() -> [FmFrame] {
    let shape = vitShape()
    let grid = 14
    let patchMap: Matrix = (0..<grid).map { r in (0..<grid).map { c in Double((r * grid + c) % 9) } }
    return [
        FmFrame(status: "A Vision Transformer does not convolve at all. It cuts the 224×224 image into 16×16 patches — a \(grid)×\(grid) grid, \(grid * grid) of them — flattens each to a 768-vector with one shared linear layer, and hands the sequence to a standard transformer encoder. An image becomes \(shape.tokens) tokens, counting the class token.",
                grids: [FmGrid(label: "\(grid)×\(grid) patch grid", values: patchMap, highlight: [Cell(r: 0, c: 0), Cell(r: 0, c: 1), Cell(r: 1, c: 0)], tone: .heat)],
                readout: "\(grid * grid) patches + 1 class token = \(shape.tokens) tokens"),
        FmFrame(status: "The patch embedding is the only image-specific machinery in the model: a linear map from 16·16·3 = 768 raw values to a 768-dimensional token, \(compact(shape.patchEmbeddingParams)) parameters. Position embeddings (\(compact(shape.positionParams)) parameters) are added because attention is permutation-invariant and would otherwise not know where a patch came from.",
                stack: [
                    FmStackRow(name: "patch embedding", shape: "768 → 768 per patch", params: shape.patchEmbeddingParams, note: "one linear layer, shared", emphasis: true),
                    FmStackRow(name: "position embedding", shape: "\(shape.tokens) × 768", params: shape.positionParams, note: "learned, not sinusoidal"),
                ]),
        FmFrame(status: "What changes is the inductive bias. A convolution hard-codes locality and translation equivariance; self-attention hard-codes nothing and compares all \(shape.attentionPairs) token pairs in every layer, so it can relate opposite corners in layer 1. That freedom is why ViT loses to ResNets on ImageNet-1k alone and wins once pre-trained on JFT-300M — the bias a convnet is given, a transformer has to learn from data.",
                bars: [FmBar(label: "pairs compared in layer 1", values: [9, Double(shape.attentionPairs)], color: costColor, captions: ["3×3 conv: 9 neighbours", "ViT: \(shape.attentionPairs) pairs"])],
                readout: "attention cost grows with tokens²; halving the patch size quadruples it"),
        FmFrame(status: "The lesson the field took from it: the convolution was never load-bearing, the *training recipe* was. Hybrids followed in both directions — Swin put locality and hierarchy back into attention windows, and ConvNeXt rebuilt a pure convnet with the transformer's recipe and matched it.",
                stack: [
                    FmStackRow(name: "ViT-Base/16", shape: "\(shape.tokens) tokens, 12 layers", params: 86_000_000, note: "≈86M parameters", emphasis: true),
                    FmStackRow(name: "ResNet-50", shape: "conv, 4 stages", params: 25_557_032, note: "≈25.6M parameters"),
                ]),
    ]
}

// MARK: - Detection: the shared scene

private let sceneSize = 200.0
private let truthBoxes = [BoxF(20, 40, 90, 150), BoxF(110, 60, 180, 140)]

/// One good box per object, a duplicate, a sloppy straddler, two on background.
private let proposals: [(BoxF, Double)] = [
    (BoxF(18, 44, 88, 148), 0.94), (BoxF(24, 36, 96, 156), 0.88), (BoxF(112, 58, 178, 142), 0.81),
    (BoxF(60, 20, 150, 120), 0.55), (BoxF(130, 10, 190, 60), 0.42), (BoxF(10, 150, 70, 195), 0.30),
]

private func truthScene(_ label: String, _ extra: [SceneBox] = [], gridCells: Int = 0) -> FmScene {
    FmScene(label: label, size: sceneSize, boxes: truthBoxes.enumerated().map { SceneBox(box: $1, label: "truth \($0 + 1)", color: savingColor) } + extra, gridCells: gridCells)
}

private func proposalBoxes(faint: Bool = true) -> [SceneBox] { proposals.map { SceneBox(box: $0.0, label: fx($0.1), color: windowColor, faint: faint) } }

private func rcnnFrames() -> [FmFrame] {
    let kept = nms(proposals.map(\.0), proposals.map(\.1), threshold: 0.5)
    let withDuplicates = averagePrecision(proposals, truthBoxes)
    let afterNms = averagePrecision(kept.map { proposals[$0] }, truthBoxes)
    let a = withDuplicates.averagePrecision, b = afterNms.averagePrecision
    return [
        FmFrame(status: "Classification answers \"what is in this image?\". Detection has to answer \"what, and where, and how many\" — and the number of answers is not known in advance, which is why it cannot be a fixed-size output layer. R-CNN's answer in 2014: turn it back into classification by proposing regions first.",
                scene: truthScene("what the detector has to produce"), readout: "2 objects, each needing a class and four coordinates"),
        FmFrame(status: "Selective search proposes about 2,000 regions per image by merging superpixels — no learning, no class knowledge, just \"this looks like it could be an object\". Six of them are drawn here. Recall matters far more than precision at this stage: a missed region can never be recovered.",
                scene: truthScene("~2,000 proposals (6 shown)", proposalBoxes()), readout: "proposals: 2,000 · objects: 2"),
        FmFrame(status: "Each proposal is warped to 227×227 and run through the CNN separately. That is the cost: 2,000 forward passes per image, with no computation shared between overlapping regions — and the overlaps are enormous. Reported test time is 47 seconds per image with VGG-16.",
                stack: [
                    FmStackRow(name: "selective search", shape: "2,000 regions", params: 0, note: "~2 s, CPU, not learned"),
                    FmStackRow(name: "CNN forward × 2,000", shape: "227×227 each", params: 0, note: "the 47 s", emphasis: true),
                    FmStackRow(name: "SVM per class", shape: "on cached features", params: 0, note: "trained separately"),
                    FmStackRow(name: "bbox regressor", shape: "per class", params: 0, note: "trained separately again"),
                ],
                readout: "three models, trained in three stages, on disk-cached features"),
        FmFrame(status: "Scored boxes come back overlapping, because overlapping proposals of the same object all look like that object. Non-maximum suppression keeps the highest-scoring box and deletes anything overlapping it by more than 0.5 IoU: \(proposals.count) boxes in, \(kept.count) out.",
                scene: truthScene("after NMS at IoU 0.5", kept.map { SceneBox(box: proposals[$0].0, label: fx(proposals[$0].1), color: windowColor) }),
                readout: "\(proposals.count) → \(kept.count) boxes"),
        FmFrame(status: "And this is why NMS is part of the score rather than tidying. A second detection of an object already found counts as a false positive, so on these boxes average precision at IoU 0.5 is \(fx(a, 3)) with the duplicate left in and \(fx(b, 3)) after suppression. Same model, same features.",
                bars: [FmBar(label: "AP@0.5", values: [a, b], color: savingColor, captions: ["with duplicate", "after NMS"])],
                readout: "AP \(fx(a, 3)) → \(fx(b, 3)) · VOC2007's 11-point rule reports \(fx(afterNms.elevenPointAp, 3)) for the same detections"),
    ]
}

private func fastRcnnFrames() -> [FmFrame] {
    let q = roiQuantisation(145)
    return [
        FmFrame(status: "R-CNN runs the CNN 2,000 times over the same image. Fast R-CNN runs it once. The image goes through the convolutional stack a single time, and every proposal is then *projected* onto that shared feature map — a box at stride 16 becomes a box on the feature grid.",
                scene: truthScene("one shared feature map, proposals projected onto it", proposalBoxes()), readout: "1 forward pass instead of 2,000"),
        FmFrame(status: "RoI pooling makes each projected region a fixed 7×7 map whatever its size, so a dense head can read it. Everything after that — class scores and box refinement — is one network with one multi-task loss, trained end to end, replacing R-CNN's three separately trained stages and its feature cache.",
                stack: [
                    FmStackRow(name: "conv stack", shape: "whole image, once", params: 0, note: "shared by every proposal", emphasis: true),
                    FmStackRow(name: "RoI pooling", shape: "any size → 7×7", params: 0, note: "no parameters"),
                    FmStackRow(name: "fc + softmax", shape: "K + 1 classes", params: 0, note: "trained jointly"),
                    FmStackRow(name: "fc + box regression", shape: "4 per class", params: 0, note: "same loss, one stage"),
                ],
                readout: "L = L_cls + λ·L_box, one optimiser"),
        FmFrame(status: "RoI pooling quantises twice, and both roundings are in feature-map units. A 145-pixel box is \(fx(q.exactFeatureSide, 3)) features wide at stride \(q.stride) and gets snapped to \(q.quantisedFeatureSide) — \(f1(q.roiShiftPixels)) image pixels lost — then divided into 7 bins of \(fx(q.exactBinSide, 3)) that are snapped to \(q.quantisedBinSide), for another \(fx(q.binShiftPixels, 0)) pixels at the far edge. Good enough for a class label. Not good enough for a mask, which is what Mask R-CNN's RoIAlign fixes.",
                bars: [FmBar(label: "misalignment from quantisation (image pixels)", values: [q.roiShiftPixels, q.binShiftPixels, q.totalShiftPixels], color: costColor, captions: ["RoI snap", "bin snap", "total"])],
                readout: "\(fx(q.totalShiftPixels, 0)) px of misalignment at stride \(q.stride)"),
        FmFrame(status: "The measured result: 47 s per image down to 2.3 s, and 0.32 s if the proposals are already computed. Which relocates the bottleneck rather than removing it — selective search now takes about seven times longer than the network it feeds, and it is the only part that is not learned.",
                bars: [FmBar(label: "test time per image (s)", values: [47, 2.3, 0.32], color: costColor, captions: ["R-CNN", "Fast R-CNN", "network only"])],
                readout: "the proposals are now 87% of the time — Faster R-CNN's entire premise"),
    ]
}

private func fasterRcnnFrames() -> [FmFrame] {
    let anchors = rpnAnchorCount()
    let anchorBoxes = [
        SceneBox(box: BoxF(70, 70, 130, 130), label: "1:1", color: windowColor, faint: true),
        SceneBox(box: BoxF(55, 85, 145, 115), label: "2:1", color: windowColor, faint: true),
        SceneBox(box: BoxF(85, 55, 115, 145), label: "1:2", color: windowColor, faint: true),
        SceneBox(box: BoxF(40, 40, 160, 160), label: "2× scale", color: windowColor, faint: true),
    ]
    return [
        FmFrame(status: "Fast R-CNN left one CPU algorithm in the middle of a GPU pipeline, taking most of the wall clock. Faster R-CNN's move is to make the proposals a network too — a Region Proposal Network sliding over the same feature map the detector already computed, so proposals become almost free.",
                scene: truthScene("anchors at one feature-map position", anchorBoxes), readout: "9 anchors per position: 3 scales × 3 aspect ratios"),
        FmFrame(status: "Anchors are the idea that made it work. Instead of regressing boxes from nothing, the RPN scores a fixed set of reference boxes at every position and regresses an *offset* from the ones that fit. On a 40×60 feature map that is \(anchors) anchors — the network's entire hypothesis space, laid out in advance.",
                stack: [
                    FmStackRow(name: "feature map", shape: "40×60, stride 16", params: 0, note: "shared with the detector"),
                    FmStackRow(name: "anchors", shape: "9 per position", params: Int64(anchors), note: "objectness + 4 offsets each", emphasis: true),
                    FmStackRow(name: "after score + NMS", shape: "2,000 proposals", params: 2_000, note: "300 at test time"),
                ],
                readout: "\(anchors) anchors → 2,000 proposals → 300 detections"),
        FmFrame(status: "The RPN is class-agnostic: it only asks \"object or not\", and hands the survivors to the same Fast R-CNN head as before. Because both share the convolutional stack, adding the proposal network cost about 10 ms per image — against selective search's two seconds.",
                bars: [FmBar(label: "proposal generation (ms)", values: [2000, 10], color: costColor, captions: ["selective search", "RPN"])],
                readout: "0.2 s per image end to end — 5 fps with VGG-16, real-time with a smaller backbone"),
        FmFrame(status: "That completes the two-stage detector: propose, then classify, with everything learned and everything shared. Its accuracy stayed the reference for years, and its cost — two passes over every region — is exactly what the one-stage detectors set out to remove.",
                scene: truthScene("final detections", [0, 2].map { SceneBox(box: proposals[$0].0, label: fx(proposals[$0].1), color: windowColor) }),
                readout: "R-CNN 47 s → Fast 2.3 s → Faster 0.2 s, and every stage now learned"),
    ]
}

private func yoloFrames() -> [FmFrame] {
    let v1 = YoloShape(grid: 7, boxesPerCell: 2, classes: 20)
    return [
        FmFrame(status: "YOLO deletes the proposal stage entirely. One CNN pass produces every box for the whole image at once: the image is divided into a \(v1.grid)×\(v1.grid) grid, and the cell containing an object's centre is responsible for predicting it.",
                scene: truthScene("the \(v1.grid)×\(v1.grid) grid, and the two responsible cells", gridCells: v1.grid), readout: "detection as a single regression problem"),
        FmFrame(status: "Each cell predicts \(v1.boxesPerCell) boxes — x, y, w, h and a confidence — plus one set of \(v1.classes) class probabilities *shared by both boxes*. That makes the whole output one tensor of \(v1.grid)×\(v1.grid)×\(v1.channels) = \(v1.tensorSize) numbers, and the whole detector one forward pass.",
                stack: [
                    FmStackRow(name: "per cell", shape: "\(v1.boxesPerCell)×5 + \(v1.classes)", params: Int64(v1.channels), note: "box coords, confidence, classes"),
                    FmStackRow(name: "output tensor", shape: "\(v1.grid)×\(v1.grid)×\(v1.channels)", params: Int64(v1.tensorSize), note: "one forward pass", emphasis: true),
                    FmStackRow(name: "boxes predicted", shape: "\(v1.grid)²×\(v1.boxesPerCell)", params: Int64(v1.boxesPredicted), note: "against R-CNN's 2,000 proposals"),
                ],
                readout: "\(v1.boxesPredicted) boxes total, scored and finished in one pass"),
        FmFrame(status: "The trade is explicit in the numbers: 45 fps against Faster R-CNN's 7, at 63.4 mAP against 73.2 on VOC 2007 — and Fast YOLO reached 155 fps. It also makes fewer background false positives than Fast R-CNN, because it sees the whole image at once rather than a cropped region.",
                bars: [
                    FmBar(label: "frames per second", values: [7, 45, 155], color: savingColor, captions: ["Faster R-CNN", "YOLO", "Fast YOLO"]),
                    FmBar(label: "VOC07 mAP", values: [73.2, 63.4, 52.7], color: signalColor, captions: ["Faster R-CNN", "YOLO", "Fast YOLO"]),
                ]),
        FmFrame(status: "v1's weaknesses come straight from its grid: one class set per cell, so a cell holding two different objects can only report one, and small clustered objects — a flock of birds — fall inside single cells. Every later version is an answer to that. v2 added anchors and a higher resolution, v3 predicted at three scales with an FPN and swapped softmax for per-class sigmoids, v4/v5 were engineering and training-recipe work, and v8 went anchor-free with a decoupled head.",
                scene: truthScene("one cell, two objects — v1 can name only one", gridCells: v1.grid),
                readout: "the fix in every later version: more boxes, more scales, no shared class vector"),
    ]
}

private func ssdFrames() -> [FmFrame] {
    let levels = ssd300Levels()
    let total = levels.reduce(0) { $0 + $1.count }
    return [
        FmFrame(status: "SSD's disagreement with YOLO v1 is about scale. One grid over one feature map has one notion of object size; SSD attaches detection heads to six feature maps of different resolutions, so a 38×38 map with a stride of 8 handles small objects and a 1×1 map handles ones filling the frame.",
                stack: levels.map { FmStackRow(name: "stride \($0.stride)", shape: "\($0.gridSize)×\($0.gridSize) × \($0.perLocation)", params: Int64($0.count), note: "\(percent(Int64($0.count), Int64(total))) of the boxes") },
                readout: "\(total) default boxes in total"),
        FmFrame(status: "Default boxes are anchors by another name: a fixed set of shapes per location, each predicting a class distribution and a four-number offset. Nearly two thirds of the \(total) come from the finest map alone, which is where small objects live and where a coarse detector fails.",
                scene: truthScene("default boxes at two of the six scales", [
                    SceneBox(box: BoxF(75, 75, 125, 125), label: "fine", color: windowColor, faint: true),
                    SceneBox(box: BoxF(30, 30, 170, 170), label: "coarse", color: costColor, faint: true),
                ]),
                bars: [FmBar(label: "default boxes per level", values: levels.map { Double($0.count) }, color: signalColor, captions: levels.map { "s\($0.stride)" })]),
        FmFrame(status: "Scoring \(total) boxes when an image holds two objects creates a brutal imbalance, and SSD handles it by hard negative mining: sort the background boxes by loss, keep the worst ones at a 3:1 ratio to the positives, and ignore the rest. It works, and it is a heuristic on top of the loss — which is precisely what RetinaNet replaced with a loss function.",
                readout: "3:1 negatives to positives, chosen by loss"),
        FmFrame(status: "The result was the first detector to be both fast and accurate: SSD300 at 74.3 mAP and 59 fps on VOC 2007, against YOLO v1's 63.4 at 45 fps and Faster R-CNN's 73.2 at 7. Multi-scale prediction, not speed tricks, is what bought the accuracy back.",
                bars: [
                    FmBar(label: "VOC07 mAP", values: [63.4, 74.3, 73.2], color: signalColor, captions: ["YOLO", "SSD300", "Faster R-CNN"]),
                    FmBar(label: "frames per second", values: [45, 59, 7], color: savingColor, captions: ["YOLO", "SSD300", "Faster R-CNN"]),
                ]),
    ]
}

private func retinaNetFrames() -> [FmFrame] {
    let levels = retinaNetLevels()
    let anchors = levels.reduce(0) { $0 + $1.count }
    let ce = crossEntropySplit(100_000, 0.9, 10, 0.1)
    let fl = focalSplit(100_000, 0.9, 10, 0.1)
    func pLevel(_ s: Int) -> Int { switch s { case 8: 3; case 16: 4; case 32: 5; case 64: 6; default: 7 } }
    func weights(_ g: Double) -> [(Float, Float)] { (0...20).map { (Float($0) / 20, Float(focalWeight(Double($0) / 20, gamma: g))) } }
    return [
        FmFrame(status: "By 2017 the question was why one-stage detectors were fast but always less accurate. RetinaNet's answer: it is not the architecture, it is the loss. A one-stage detector scores every anchor on the pyramid — \(anchors) of them here — and essentially all of them are background.",
                stack: levels.map { FmStackRow(name: "P\(pLevel($0.stride)) · stride \($0.stride)", shape: "\($0.gridSize)×\($0.gridSize) × 9", params: Int64($0.count)) },
                readout: "≈\(anchors / 1000)k anchors per image, two of them on objects"),
        FmFrame(status: "Cross-entropy has no answer to that. Take 100,000 background anchors the model already gets right at 0.9 confidence and 10 hard foreground anchors at 0.1: the background contributes \(fx(ce.backgroundLoss, 0)) of loss against the foreground's \(fx(ce.foregroundLoss, 0)) — \(f1(ce.backgroundShare * 100))% of the gradient comes from examples that are already correct.",
                bars: [FmBar(label: "cross-entropy loss contribution", values: [ce.backgroundLoss, ce.foregroundLoss], color: costColor, captions: ["100k easy background", "10 hard foreground"])],
                readout: "background : foreground = \(fx(ce.ratio, 0)) : 1"),
        FmFrame(status: "Focal loss multiplies each example's loss by (1 − p_t)^γ. At γ = 2 an anchor the model is 90% sure about is scaled by \(fx(focalWeight(0.9))) while one it is 10% sure about keeps \(fx(focalWeight(0.1))) of its loss. Same 100,010 anchors: the split becomes \(fx(fl.backgroundLoss, 0)) against \(fx(fl.foregroundLoss, 0)), and the ratio falls from \(fx(ce.ratio, 0)):1 to \(f1(fl.ratio)):1 — a \(fx(ce.ratio / fl.ratio, 0))× rebalance from one factor in the loss.",
                plot: FmPlot(label: "focal weight (1 − p)^γ by confidence", curves: [
                    FmCurve(label: "γ = 0 (cross-entropy)", points: (0...20).map { (Float($0) / 20, 1) }, color: mutedColor),
                    FmCurve(label: "γ = 1", points: weights(1), color: signalColor),
                    FmCurve(label: "γ = 2", points: weights(2), color: savingColor),
                    FmCurve(label: "γ = 5", points: weights(5), color: costColor),
                ], xRange: 0...1, yRange: 0...1),
                readout: "easy examples are not ignored — they are down-weighted, smoothly"),
        FmFrame(status: "With that loss and nothing else exotic — a ResNet-FPN backbone, two small subnets for class and box — a one-stage detector matched the two-stage accuracy record: 39.1 AP on COCO, above every Faster R-CNN variant published at the time, while staying single-shot. The lesson generalised: class imbalance is a loss-design problem, not an architecture problem.",
                bars: [FmBar(label: "COCO AP", values: [31.2, 36.2, 39.1], color: signalColor, captions: ["SSD513", "Faster R-CNN + FPN", "RetinaNet"])]),
    ]
}

private func unetFrames() -> [FmFrame] {
    let path = unetPath()
    let output = path.last!.size
    let skips = path.filter { $0.cropPerSide > 0 }
    return [
        FmFrame(status: "Segmentation needs a label for every pixel, so a classifier's ending — pool everything away, then a dense layer — is exactly wrong. U-Net keeps the contracting encoder, and mirrors it with an expanding decoder that upsamples back to image resolution.",
                stack: path.prefix(5).map { FmStackRow(name: $0.name, shape: "\($0.size)×\($0.size)×\($0.channels)", params: 0) },
                readout: "encoder: resolution down, semantics up"),
        FmFrame(status: "The skip connections are what make it work. Upsampling alone cannot invent back the boundary detail the pooling threw away, so each decoder stage concatenates the encoder map of the same resolution — coarse \"what\" from below, fine \"where\" from the side.",
                stack: skips.map { FmStackRow(name: $0.name, shape: "\($0.size)×\($0.size)×\($0.channels)", params: 0, note: "skip cropped \($0.cropPerSide) px per side", emphasis: true) },
                readout: "concatenate, not add — the decoder sees both maps in full"),
        FmFrame(status: "Because the original uses unpadded convolutions, the encoder map is always larger than the decoder map it joins, and the paper crops it — by \(skips.map { "\($0.cropPerSide)" }.joined(separator: ", ")) pixels per side going up. The consequence is visible in the shapes: 572×572 in, \(output)×\(output) out. The network deliberately predicts a smaller region than it reads, and a large image is covered by overlapping tiles so that every predicted pixel has full context.",
                scene: FmScene(label: "input tile vs predicted region", size: sceneSize, boxes: [
                    SceneBox(box: BoxF(0, 0, 200, 200), label: "input 572²", color: windowColor, faint: true),
                    SceneBox(box: BoxF(32, 32, 168, 168), label: "output \(output)²", color: savingColor),
                ]),
                readout: "572 → \(output), and the missing border is why tiles overlap"),
        FmFrame(status: "It was trained on about 30 annotated images. Heavy elastic deformation stood in for the data that did not exist, and a weighted loss put extra cost on the thin background gaps *between* touching cells — a segmentation network taught to draw separations it would otherwise merge. It won the ISBI cell-tracking challenge by a wide margin and remains the default architecture for medical segmentation.",
                readout: "30 images, elastic augmentation, boundary-weighted cross-entropy"),
    ]
}

private func maskRcnnFrames() -> [FmFrame] {
    let q = roiQuantisation(145)
    let maskGrid = 28
    let mask = (0..<20).map { r in (0..<20).map { c -> Int in
        if (4...14).contains(r) && (2...8).contains(c) { return 1 }
        if (6...13).contains(r) && (11...17).contains(c) { return 2 }
        return 0
    } }
    return [
        FmFrame(status: "Mask R-CNN is Faster R-CNN plus a third head: alongside the class and the box, a small fully convolutional branch predicts a \(maskGrid)×\(maskGrid) binary mask per RoI. The addition is almost trivially simple, which is the paper's point — instance segmentation did not need a new paradigm.",
                stack: [
                    FmStackRow(name: "class head", shape: "K + 1 softmax", params: 0, note: "unchanged from Faster R-CNN"),
                    FmStackRow(name: "box head", shape: "4 per class", params: 0, note: "unchanged"),
                    FmStackRow(name: "mask head", shape: "K × \(maskGrid)×\(maskGrid)", params: Int64(maskGrid * maskGrid), note: "one binary mask per class", emphasis: true),
                ],
                readout: "≈5 fps, and it beat every entrant of the 2016 COCO segmentation challenge"),
        FmFrame(status: "The masks are per-class and binary, with no softmax across classes: the class head decides *what* it is, the mask head only decides *which pixels*. Decoupling those two questions is worth several points of mask AP over the usual per-pixel multi-class softmax, because the mask branch stops competing with itself across classes.",
                scene: FmScene(label: "box → 28×28 mask, per instance", size: sceneSize, boxes: truthBoxes.enumerated().map { SceneBox(box: $1, label: "instance \($0 + 1)", color: savingColor) }, mask: mask)),
        FmFrame(status: "But the mask branch exposed a defect that classification had tolerated for two years. RoI pooling quantises twice — the box onto the feature grid, then the grid into bins — and at stride \(q.stride) those roundings are worth \(fx(q.totalShiftPixels, 0)) image pixels on a 145-pixel box. A class label survives that. A mask does not.",
                bars: [FmBar(label: "misalignment (image pixels)", values: [q.roiShiftPixels, q.binShiftPixels, 0], color: costColor, captions: ["RoI snap", "bin snap", "RoIAlign"])],
                readout: "RoIPool \(fx(q.totalShiftPixels, 0)) px · RoIAlign 0 px"),
        FmFrame(status: "RoIAlign removes both roundings: sample each bin at exact floating-point locations with bilinear interpolation and never snap to the grid. The paper reports roughly a 3-point mask-AP gain from that one change, and about twice as much at the strict IoU 0.75 threshold — where a few pixels of misalignment is exactly what decides a match.",
                readout: "the fix is arithmetic, not architecture: stop calling floor()"),
    ]
}

private func segmentationTypesFrames() -> [FmFrame] {
    let semantic = (0..<12).map { r in (0..<12).map { c -> Int in
        if (3...8).contains(r) && (1...9).contains(c) { return 1 }
        if (1...4).contains(r) && (10...11).contains(c) { return 2 }
        return 0
    } }
    let instance = (0..<12).map { r in (0..<12).map { c -> Int in
        if (3...8).contains(r) && (1...4).contains(c) { return 1 }
        if (3...8).contains(r) && (5...9).contains(c) { return 2 }
        if (1...4).contains(r) && (10...11).contains(c) { return 3 }
        return 0
    } }
    let counts = segmentationCounts(semantic, instance)
    let predicted = semantic.enumerated().map { r, row in row.enumerated().map { c, v in r == 8 && (1...9).contains(c) ? 0 : v } }
    let miou = meanIoU(predicted, semantic)
    return [
        FmFrame(status: "Four tasks sit on the same picture and answer different questions. Classification: what is here. Detection: where, as boxes. Semantic segmentation: a class per pixel. Instance segmentation: a class per pixel *and* which object each pixel belongs to.",
                scene: FmScene(label: "semantic map — one label per pixel", size: sceneSize, mask: semantic),
                readout: "\(counts.classes) classes · \(counts.semanticRegions) connected regions"),
        FmFrame(status: "The distinction is not academic, and it shows up the moment two objects of the same class touch. Semantically the two sheep are one region of \(counts.pixelsPerClass[1]!) pixels — there is no label that could separate them, because the output space has one channel per class and none per object.",
                scene: FmScene(label: "instance map — one label per object", size: sceneSize, mask: instance),
                readout: "semantic: \(counts.semanticRegions) regions · instance: \(counts.instances) objects"),
        FmFrame(status: "So they are evaluated differently too. Semantic segmentation reports mean IoU per class — this prediction, which drops the sheep's bottom row, scores \(fx(miou, 3)) — while instance segmentation uses detection's average precision over masks, where merging two sheep into one costs a false negative outright rather than a few pixels of IoU.",
                bars: [FmBar(label: "mIoU of the shown prediction", values: [miou, 1], color: savingColor, captions: ["predicted", "perfect"])],
                readout: "mIoU \(fx(miou, 3)) · a merged pair would still score well here, and 0 under mask AP"),
        FmFrame(status: "Architecturally the split is just as clean: semantic segmentation is a dense per-pixel classifier (FCN, U-Net, DeepLab), instance segmentation is detection with a mask head (Mask R-CNN), and panoptic segmentation is the task that demands both at once — every pixel labelled, and every countable object separated, with no overlaps allowed.",
                stack: [
                    FmStackRow(name: "semantic", shape: "class per pixel", params: 0, note: "U-Net, FCN, DeepLab · mIoU"),
                    FmStackRow(name: "instance", shape: "mask per object", params: 0, note: "Mask R-CNN · mask AP"),
                    FmStackRow(name: "panoptic", shape: "both, no overlaps", params: 0, note: "stuff + things · PQ", emphasis: true),
                ]),
    ]
}

// MARK: - Config

private let archLegend: [(Color, String)] = [(signalColor, "Convolution"), (mutedColor, "Pooling"), (negativeColor, "Dense")]

private let featureMapConfigs: [String: FmConfig] = [
    "conv_layers": FmConfig(intro: "One kernel slid over a 7×7 patch, computed cell by cell. Change the kernel size, stride and padding and every number below — including the output size — is recomputed.",
                            legend: [(windowColor, "Window"), (signalColor, "Positive response"), (negativeColor, "Negative")], controls: true, build: convolutionFrames),
    "pooling_layers": FmConfig(intro: "Max and average pooling over a real feature map, with the translation-invariance claim measured rather than asserted.",
                               legend: [(signalColor, "Response"), (savingColor, "Survives a shift"), (mutedColor, "Discarded")], controls: true, build: poolingFrames),
    "padding_strides": FmConfig(intro: "The output-size formula, the border pixels that get read once instead of nine times, and what ten unpadded layers do to a 224×224 image.",
                                legend: [(signalColor, "Reads per pixel"), (costColor, "Shrinkage"), (windowColor, "Window")], controls: true, build: paddingStrideFrames),
    "lenet5": FmConfig(intro: "LeNet-5 layer by layer, with the parameter count summed from the layer table rather than quoted.", legend: archLegend, build: { _ in lenetFrames() }),
    "alexnet": FmConfig(intro: "AlexNet's eight learned layers, and where its sixty-two million parameters actually sit.", legend: archLegend, build: { _ in alexNetFrames() }),
    "vgg": FmConfig(intro: "VGG-16's uniform 3×3 stack, its 138M parameters, and why two small kernels beat one large one.", legend: archLegend, build: { _ in vggFrames() }),
    "inception": FmConfig(intro: "The inception 3a module at its real widths, priced with and without its 1×1 bottlenecks.",
                          legend: [(costColor, "Naive cost"), (savingColor, "With 1×1 reduction"), (signalColor, "Branch")], build: { _ in inceptionFrames() }),
    "resnet": FmConfig(intro: "The same backward pass down a plain stack and a residual one — identical weights, one addition of difference, thirty layers of consequence.",
                       legend: [(negativeColor, "Plain"), (savingColor, "Residual"), (signalColor, "Identity path")], build: { _ in resNetFrames() }),
    "densenet": FmConfig(intro: "A six-layer dense block: what concatenation does to the channel count, and what the 1×1 bottleneck does about it.",
                         legend: [(signalColor, "Channels in"), (savingColor, "Parameters"), (mutedColor, "Reused features")], build: { _ in denseNetFrames() }),
    "mobilenet": FmConfig(intro: "Standard convolution against depthwise separable at the same shape, priced exactly.",
                          legend: [(costColor, "Standard conv"), (savingColor, "Separable"), (signalColor, "Pointwise 1×1")], build: { _ in mobileNetFrames() }),
    "efficientnet": FmConfig(intro: "Depth, width and resolution scaled together by one exponent, with the constants that make each unit of φ a doubling of FLOPs.",
                             legend: [(signalColor, "Depth"), (costColor, "Width"), (savingColor, "Resolution")], build: { _ in efficientNetFrames() }),
    "vit": FmConfig(intro: "An image as 196 patches plus a class token, and what dropping the convolution costs and buys.",
                    legend: [(signalColor, "Patch token"), (windowColor, "Class token"), (costColor, "Attention pairs")], build: { _ in vitFrames() }),
    "rcnn": FmConfig(intro: "Region proposals, one CNN pass each, and the two evaluation rules — NMS and average precision — that everything after this is scored by.",
                     legend: [(savingColor, "Ground truth"), (windowColor, "Proposal"), (costColor, "Cost")], build: { _ in rcnnFrames() }),
    "fast_rcnn": FmConfig(intro: "One shared feature map instead of 2,000 forward passes — and the pixel-level cost of RoI pooling's two roundings.",
                          legend: [(savingColor, "Ground truth"), (windowColor, "Projected RoI"), (costColor, "Misalignment")], build: { _ in fastRcnnFrames() }),
    "faster_rcnn": FmConfig(intro: "Anchors, and a proposal network that costs 10 ms where selective search cost two seconds.",
                            legend: [(savingColor, "Ground truth"), (windowColor, "Anchor"), (costColor, "Proposal time")], build: { _ in fasterRcnnFrames() }),
    "yolo": FmConfig(intro: "A 7×7 grid, 98 boxes, one forward pass — and the grid's own limits, which every later version answers.",
                     legend: [(savingColor, "Ground truth"), (windowColor, "Grid cell"), (signalColor, "Accuracy")], build: { _ in yoloFrames() }),
    "ssd": FmConfig(intro: "Six feature maps, 8,732 default boxes counted level by level, and the imbalance that follows.",
                    legend: [(signalColor, "Default boxes"), (savingColor, "Speed"), (costColor, "Coarse scale")], build: { _ in ssdFrames() }),
    "retinanet": FmConfig(intro: "100,000 anchors against two objects, priced under cross-entropy and under focal loss.",
                          legend: [(costColor, "Background loss"), (savingColor, "Focal weight"), (signalColor, "Accuracy")], build: { _ in retinaNetFrames() }),
    "unet": FmConfig(intro: "The contracting and expanding paths at their real sizes, including the crop each skip connection needs and the border the network refuses to predict.",
                     legend: [(signalColor, "Encoder"), (savingColor, "Predicted region"), (windowColor, "Input tile")], build: { _ in unetFrames() }),
    "mask_rcnn": FmConfig(intro: "One extra head on Faster R-CNN, and the quantisation bug the masks made visible.",
                          legend: [(savingColor, "Instance"), (windowColor, "RoI"), (costColor, "Misalignment")], build: { _ in maskRcnnFrames() }),
    "segmentation_types": FmConfig(intro: "The same picture under three tasks: pixels labelled by class, by object, and by both.",
                                   legend: [(signalColor, "Class 1"), (costColor, "Class 2"), (mutedColor, "Background")], build: { _ in segmentationTypesFrames() }),
]

// MARK: - UI

struct FeatureMapLab: View {
    private let config: FmConfig
    private let key: String
    @State private var controls = FmControls()
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        let resolved = featureMapConfigs[topicId] == nil ? "conv_layers" : topicId
        key = resolved
        config = featureMapConfigs[resolved]!
        let first = LabCache.get("featuremap:\(resolved):\(FmControls())") { featureMapConfigs[resolved]!.build(FmControls()) }
        _playback = State(initialValue: PlaybackState(stepCount: first.count, speedMs: 950))
    }

    private var frames: [FmFrame] {
        let key = self.key, c = controls
        return LabCache.get("featuremap:\(key):\(c)") { featureMapConfigs[key]!.build(c) }
    }

    var body: some View {
        let frames = self.frames
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            if config.controls {
                controlPicker("kernel", [1, 3, 5, 7], \.kernel)
                controlPicker("stride", [1, 2, 3, 4], \.stride)
                controlPicker("padding", [0, 1, 2, 3], \.padding)
            }
            if !frame.grids.isEmpty {
                WeightedRow(weights: frame.grids.map { CGFloat(max($0.values[0].count, 1)) }, spacing: 10) {
                    ForEach(frame.grids.indices, id: \.self) { FeatureGridView(grid: frame.grids[$0]) }
                }
                .padding(.top, 12)
            }
            if let scene = frame.scene { SceneView(scene: scene).padding(.top, 12) }
            let peak = frame.stack.map { max($0.params, 1) }.max() ?? 1
            ForEach(frame.stack.indices, id: \.self) { StackRowView(row: frame.stack[$0], peak: peak) }
            ForEach(frame.bars.indices, id: \.self) { FeatureBarsView(bar: frame.bars[$0]).padding(.top, 12) }
            if let plot = frame.plot { FeaturePlotView(plot: plot).padding(.top, 12) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: config.legend, playback: playback, captions: frames.map(\.status))
        }
        .onChange(of: controls) { _, _ in playback.load(stepCount: self.frames.count) }
    }

    private func controlPicker(_ label: String, _ options: [Int], _ path: WritableKeyPath<FmControls, Int>) -> some View {
        HStack(spacing: 6) {
            Text(label).font(.labelMedium).foregroundStyle(palette.muted).frame(maxWidth: .infinity, alignment: .leading)
            ForEach(options, id: \.self) { option in
                let active = controls[keyPath: path] == option
                Button { controls[keyPath: path] = option } label: {
                    Text("\(option)")
                        .font(AppFont.sans(12, active ? .bold : .regular))
                        .foregroundStyle(active ? .white : palette.muted)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(active ? windowColor : palette.outlineVariant, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 10)
    }
}

private struct FeatureGridView: View {
    let grid: FmGrid
    @Environment(\.palette) private var palette

    var body: some View {
        let peak = max(grid.values.flatMap { $0 }.map { abs($0) }.max() ?? 1, 0.001)
        VStack(alignment: .leading, spacing: 0) {
            Text(grid.label).font(.labelSmall).foregroundStyle(palette.muted).lineLimit(1).minimumScaleFactor(0.7)
            VStack(spacing: 0) {
                ForEach(grid.values.indices, id: \.self) { r in
                    HStack(spacing: 0) {
                        ForEach(grid.values[r].indices, id: \.self) { c in
                            let value = grid.values[r][c]
                            let intensity = min(max(abs(value) / peak, 0), 1)
                            let base: Color = grid.tone == .count ? savingColor : grid.tone == .heat ? windowColor : value < 0 ? negativeColor : signalColor
                            ZStack {
                                RoundedRectangle(cornerRadius: 4).fill(base.opacity(0.10 + 0.75 * intensity))
                                if grid.highlight.contains(Cell(r: r, c: c)) {
                                    RoundedRectangle(cornerRadius: 3).stroke(windowColor, lineWidth: 2.5)
                                }
                                if grid.values.count <= 8 {
                                    Text(value == value.rounded() ? "\(Int(value))" : f1(value))
                                        .font(AppFont.sans(10, intensity > 0.5 ? .bold : .regular))
                                        .foregroundStyle(intensity > 0.5 ? .white : palette.onSurface)
                                        .lineLimit(1).minimumScaleFactor(0.5)
                                }
                            }
                            .padding(1)
                            .frame(maxWidth: .infinity)
                            .frame(height: 26)
                        }
                    }
                }
            }
            .padding(.top, 4)
        }
    }
}

private struct SceneView: View {
    let scene: FmScene
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(scene.label).font(.labelSmall).foregroundStyle(palette.muted)
            Canvas { ctx, size in
                let side = min(size.width, size.height)
                let originX = (size.width - side) / 2
                let scale = side / CGFloat(scene.size)
                let gridColor = palette.muted.opacity(0.30)
                ctx.fill(Path(roundedRect: CGRect(x: originX, y: 0, width: side, height: side), cornerRadius: 4), with: .color(gridColor.opacity(0.12)))
                let maskPalette = [signalColor, costColor, savingColor, windowColor]
                if let mask = scene.mask {
                    let cell = side / CGFloat(mask.count)
                    for (r, row) in mask.enumerated() {
                        for (c, label) in row.enumerated() where label != 0 {
                            ctx.fill(Path(CGRect(x: originX + CGFloat(c) * cell, y: CGFloat(r) * cell, width: cell, height: cell)),
                                     with: .color(maskPalette[(label - 1) % maskPalette.count].opacity(0.55)))
                        }
                    }
                }
                if scene.gridCells > 0 {
                    let step = side / CGFloat(scene.gridCells)
                    var lines = Path()
                    for i in 1..<scene.gridCells {
                        lines.move(to: CGPoint(x: originX + CGFloat(i) * step, y: 0))
                        lines.addLine(to: CGPoint(x: originX + CGFloat(i) * step, y: side))
                        lines.move(to: CGPoint(x: originX, y: CGFloat(i) * step))
                        lines.addLine(to: CGPoint(x: originX + side, y: CGFloat(i) * step))
                    }
                    ctx.stroke(lines, with: .color(gridColor), lineWidth: 1.5)
                }
                for drawn in scene.boxes {
                    let rect = CGRect(x: originX + CGFloat(drawn.box.x1) * scale, y: CGFloat(drawn.box.y1) * scale,
                                      width: CGFloat(drawn.box.width) * scale, height: CGFloat(drawn.box.height) * scale)
                    ctx.stroke(Path(rect), with: .color(drawn.color.opacity(drawn.faint ? 0.55 : 1)), lineWidth: drawn.faint ? 0.8 : 1.6)
                    ctx.draw(Text(drawn.label).font(AppFont.sans(9, .bold)).foregroundStyle(drawn.color),
                             at: CGPoint(x: rect.minX + 2, y: max(rect.minY, 12)), anchor: .bottomLeading)
                }
            }
            .frame(height: 180)
            .padding(.top, 4)
        }
    }
}

private struct StackRowView: View {
    let row: FmStackRow
    let peak: Int64
    @Environment(\.palette) private var palette

    var body: some View {
        let share = min(max(CGFloat(row.params) / CGFloat(peak), 0), 1)
        VStack(alignment: .leading, spacing: 0) {
            WeightedRow(weights: [1.4, 1.2, 0.6]) {
                Text(row.name).font(AppFont.sans(12, row.emphasis ? .bold : .medium)).foregroundStyle(row.emphasis ? palette.primary : palette.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(row.shape).font(.labelSmall).foregroundStyle(palette.muted).frame(maxWidth: .infinity, alignment: .leading)
                Text(row.params == 0 ? "—" : compact(row.params)).font(AppFont.sans(11, .semibold)).frame(maxWidth: .infinity, alignment: .trailing)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2).fill(palette.outlineVariant)
                    RoundedRectangle(cornerRadius: 2).fill(row.emphasis ? windowColor : signalColor).frame(width: geo.size.width * share)
                }
            }
            .frame(height: 2)
            .padding(.top, 2)
            if !row.note.isEmpty { Text(row.note).font(.labelSmall).foregroundStyle(palette.muted) }
        }
        .padding(.top, 8)
    }
}

private struct FeatureBarsView: View {
    let bar: FmBar
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(bar.label).font(.labelSmall).foregroundStyle(palette.muted)
            Canvas { ctx, size in
                guard !bar.values.isEmpty else { return }
                let peak = max(bar.values.map { abs($0) }.max() ?? 1, 0.001)
                let slot = size.width / CGFloat(bar.values.count)
                for (i, v) in bar.values.enumerated() {
                    let h = max(CGFloat(abs(v) / peak) * size.height, 2)
                    ctx.fill(Path(roundedRect: CGRect(x: CGFloat(i) * slot + slot * 0.18, y: size.height - h, width: slot * 0.64, height: h), cornerRadius: 1.5),
                             with: .color(v >= 0 ? bar.color : negativeColor))
                }
            }
            .frame(height: 42)
            .padding(.top, 4)
            if !bar.captions.isEmpty {
                HStack(spacing: 0) {
                    ForEach(bar.captions.indices, id: \.self) {
                        Text(bar.captions[$0]).font(.labelSmall).foregroundStyle(palette.muted).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }
}

private struct FeaturePlotView: View {
    let plot: FmPlot
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(plot.label + (plot.logY ? " · log₁₀ scale" : "")).font(.labelSmall).foregroundStyle(palette.muted)
            Canvas { ctx, size in
                let xSpan = plot.xRange.upperBound - plot.xRange.lowerBound, ySpan = plot.yRange.upperBound - plot.yRange.lowerBound
                func px(_ x: Float) -> CGFloat { CGFloat((x - plot.xRange.lowerBound) / (xSpan == 0 ? 1 : xSpan)) * size.width }
                func py(_ y: Float) -> CGFloat { size.height - CGFloat((y - plot.yRange.lowerBound) / (ySpan == 0 ? 1 : ySpan)) * size.height }
                let axis = palette.muted.opacity(0.35)
                var base = Path()
                base.move(to: CGPoint(x: 0, y: size.height))
                base.addLine(to: CGPoint(x: size.width, y: size.height))
                ctx.stroke(base, with: .color(axis), lineWidth: 1.5)
                if plot.yRange.contains(0) {
                    var zero = Path()
                    zero.move(to: CGPoint(x: 0, y: py(0)))
                    zero.addLine(to: CGPoint(x: size.width, y: py(0)))
                    ctx.stroke(zero, with: .color(axis), lineWidth: 1)
                }
                for curve in plot.curves where curve.points.count > 1 {
                    var path = Path()
                    path.move(to: CGPoint(x: px(curve.points[0].0), y: py(curve.points[0].1)))
                    for pt in curve.points.dropFirst() { path.addLine(to: CGPoint(x: px(pt.0), y: py(pt.1))) }
                    ctx.stroke(path, with: .color(curve.color), style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                }
            }
            .frame(height: 120)
            .padding(.top, 6)
            FlowLayout(spacing: 10, lineSpacing: 4) {
                ForEach(plot.curves.indices, id: \.self) { LegendDot(color: plot.curves[$0].color, label: plot.curves[$0].label) }
            }
        }
    }
}
