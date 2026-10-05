import SwiftUI

// Port of CnnStoryFrames.kt: the fourteen convolutional labs drawn by DeepStoryLabs.swift. Convolution,
// pooling, padding and stride on small grids computed cell by cell; the classic architectures as
// per-layer parameter and compute shares from their real layer shapes; the residual gradient by a
// seeded forward and backward pass (the same LCG as on Android).

let cnnStoryTopicIds: Set<String> = [
    "cnn", "conv_layers", "pooling_layers", "padding_strides", "lenet5", "alexnet", "vgg", "inception",
    "resnet", "densenet", "mobilenet", "efficientnet", "vit", "transfer_learning",
]

func cnnLab(_ topicId: String) -> DkLab? {
    switch topicId {
    case "cnn": cnnIntroLab()
    case "conv_layers": convLayersLab()
    case "pooling_layers": poolingLab()
    case "padding_strides": paddingLab()
    case "lenet5": lenetLab()
    case "alexnet": alexnetLab()
    case "vgg": vggLab()
    case "inception": inceptionLab()
    case "resnet": resnetLab()
    case "densenet": densenetLab()
    case "mobilenet": mobilenetLab()
    case "efficientnet": efficientnetLab()
    case "vit": vitLab()
    case "transfer_learning": transferLab()
    default: nil
    }
}

// MARK: - Formatting

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }

private func t(_ v: Double, _ d: Int = 2) -> String {
    var s = dkNum(v, d)
    guard s.contains(".") else { return s }
    while s.hasSuffix("0") { s.removeLast() }
    if s.hasSuffix(".") { s.removeLast() }
    return s
}

private func comma(_ v: Double) -> String {
    let digits = Array(String(Int64(abs(v))))
    var out = ""
    for (i, c) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out += "," }
        out.append(c)
    }
    return v < 0 ? "−" + out : out
}

/// 9,536 · 148.0k · 2.10M · 138M · 15.47B.
private func count(_ v: Double) -> String {
    if v >= 1e9 { return n(v / 1e9, 2) + "B" }
    if v >= 1e8 { return n(v / 1e6, 0) + "M" }
    if v >= 1e7 { return n(v / 1e6, 1) + "M" }
    if v >= 1e6 { return n(v / 1e6, 2) + "M" }
    if v >= 1e4 { return n(v / 1e3, 1) + "k" }
    return comma(v)
}

private func pct(_ share: Double, _ d: Int = 1) -> String { n(share * 100, d) + "%" }

private func legend(_ ink: DkInk, _ label: String) -> DkLegend { DkLegend(ink: ink, style: .fill, label: label) }

private func withActions(_ frames: [DkFrame], _ actions: (Int) -> String) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = actions(i); return f }
}

private func stepActions(_ frames: [DkFrame], _ action: String) -> [DkFrame] {
    withActions(frames) { $0 == frames.count - 1 ? "Start Over" : action }
}

// MARK: - Grids

private typealias Mat = [[Double]]

private func conv(_ x: Mat, _ k: Mat, _ stride: Int = 1, _ pad: Int = 0) -> Mat {
    let size = x.count + 2 * pad, kk = k.count
    let out = (size - kk) / stride + 1
    func at(_ r: Int, _ c: Int) -> Double { r < pad || c < pad || r >= x.count + pad || c >= x.count + pad ? 0 : x[r - pad][c - pad] }
    return (0..<out).map { i in
        (0..<out).map { j in
            var s = 0.0
            for a in 0..<kk { for b in 0..<kk { s += k[a][b] * at(i * stride + a, j * stride + b) } }
            return s
        }
    }
}

private let sobel: Mat = [[1, 0, -1], [2, 0, -2], [1, 0, -1]]

private func valueCell(_ v: Double, _ scale: Double, _ text: String? = nil) -> DkCell {
    if v > 0 { return DkCell(text: text ?? t(v), tone: .pos, level: v / scale) }
    if v < 0 { return DkCell(text: text ?? t(v), tone: .neg, level: -v / scale) }
    return DkCell(text: "0", tone: .zero)
}

/// A map whose first `filled` cells (row-major) show their value; `current` is the cell being written.
private func mapCells(_ m: Mat, _ filled: Int, _ current: Int?, _ scale: Double, _ digits: Int = 2) -> [DkCell] {
    m.flatMap { $0 }.enumerated().map { i, v in
        if i == current { return DkCell(text: t(v, digits), tone: .current) }
        if i < filled { return valueCell(v, scale, t(v, digits)) }
        return DkCell(text: "", tone: .empty)
    }
}

private func inputCells(_ x: Mat) -> [DkCell] { x.flatMap { $0 }.map { $0 > 0 ? DkCell(text: t($0), tone: .pos, level: 0.55) : DkCell(text: "0", tone: .zero) } }

private func kernelCells(_ k: Mat) -> [DkCell] {
    let top = k.flatMap { $0 }.map { abs($0) }.max()!
    return k.flatMap { $0 }.map { valueCell($0, top) }
}

private func maxAbs(_ m: Mat) -> Double { max(m.flatMap { $0 }.map { abs($0) }.max()!, 1e-9) }

// MARK: - CNNs: one kernel over a 6×6 edge

private func cnnIntroLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let x: Mat = (0..<6).map { _ in (0..<6).map { $0 >= 3 ? 1 : 0 } }
        let mirror = sobel.map { $0.map { -$0 } }
        let out = conv(x, sobel), outM = conv(x, mirror)
        func stage(_ k: Mat, _ m: Mat, _ filled: Int, _ current: Int?, _ window: (Int, Int)?) -> DkStage {
            .grids(DkGrids(columns: [
                [DkGrid(title: "input 6×6", rows: 6, cols: 6, cells: inputCells(x), boxes: window.map { [DkBox(r0: $0.0, c0: $0.1, r1: $0.0 + 2, c1: $0.1 + 2)] } ?? [], maxCell: 30)],
                [DkGrid(title: "kernel", rows: 3, cols: 3, cells: kernelCells(k), maxCell: 28),
                 DkGrid(title: "output 4×4", rows: 4, cols: 4, cells: mapCells(m, filled, current, 4), maxCell: 22)],
            ], weights: [1.55, 1]))
        }
        func terms(_ k: Mat, _ r: Int, _ c: Int) -> String {
            var parts: [(Double, Double)] = []
            for a in 0..<3 { for b in 0..<3 where k[a][b] != 0 && x[r + a][c + b] != 0 { parts.append((k[a][b], x[r + a][c + b])) } }
            if parts.isEmpty { return "0" }
            return parts.enumerated().map { i, p in
                let body = "\(t(abs(p.0)))·\(t(p.1))"
                if i == 0 { return p.0 < 0 ? "−" + body : body }
                return p.0 < 0 ? " − " + body : " + " + body
            }.joined()
        }
        let lg = [DkLegend(ink: .yellow, style: .ring, label: "Window"), legend(.blue, "Positive"), legend(.pink, "Negative")]
        let rest = Array(lg.dropFirst())
        let frames = [
            DkFrame(
                header: nil, stage: stage(sobel, out, 0, nil, nil), legend: rest,
                formula: ["kernel: Sobel-x, 3×3 = {9} weights", "output: (6 − 3 + 1)² = 16 positions"],
                headline: "A 6×6 image with a {vertical edge}.",
                body: "Left half 0, right half 1. The kernel's positive left column and negative right column respond to brightness changing left to right."
            ),
            DkFrame(
                header: nil, stage: stage(sobel, out, 0, 0, (0, 0)), legend: lg,
                formula: ["window (0,0) · kernel = \(terms(sobel, 0, 0))", "= {\(t(out[0][0]))}"],
                headline: "The first window sees only zeros: {\(t(out[0][0]))}.",
                body: "Multiply the 9 pixels under the window by the 9 kernel weights and add: that's one output."
            ),
            DkFrame(
                header: nil, stage: stage(sobel, out, 5, 5, (1, 1)), legend: lg,
                formula: ["window (1,1) · kernel = \(terms(sobel, 1, 1))", "= {\(t(out[1][1]))} · 9 shared weights, not 36 × 16"],
                headline: "The window straddling the edge scores {\(t(out[1][1]))}.",
                body: "The same 9 weights slide to all 16 positions. A dense layer would need \(36 * 16) for this output."
            ),
            DkFrame(
                header: nil, stage: stage(sobel, out, 16, nil, nil), legend: rest,
                formula: ["16 positions × 9 multiplies = 144", "zero where the window is flat, \(t(out[1][1])) on the edge"],
                headline: "The edge shows up as {two columns of \(t(out[1][1]))}.",
                body: "Flat regions give 0 whether dark or bright: the kernel measures change, not brightness."
            ),
            DkFrame(
                header: nil, stage: stage(mirror, outM, 16, nil, nil), legend: rest,
                formula: ["mirror kernel = −1 × Sobel-x", "edge response {+\(t(outM[1][1]))}"],
                headline: "The mirrored kernel scores the same edge {+\(t(outM[1][1]))}.",
                body: "Which sign means what is learned, not designed: training sets the 9 weights to whatever pattern helps."
            ),
            DkFrame(
                header: nil, stage: stage(mirror, outM, 16, nil, nil), legend: rest,
                formula: ["32 filters × 9 weights = 288 (+ 32 biases)", "output 4×4×32: one map per filter"],
                headline: "Each filter makes one {feature map}; a layer stacks many.",
                body: "Later layers convolve over these maps, so their filters combine edges into corners, textures and parts."
            ),
        ]
        return withActions(frames) { ["Apply Kernel", "Slide Window", "Finish Map", "Mirror Kernel", "Stack Filters", "Start Over"][$0] }
    }
}

// MARK: - Convolution layers: kernel size

private let convInput: Mat = (0..<7).map { r in (0..<7).map { c in c >= 4 || r == 3 ? 1 : 0 } }
private let convSizes = [1, 3, 5, 7]

private func edgeKernel(_ k: Int) -> Mat {
    if k == 1 { return [[1]] }
    if k == 3 { return sobel }
    return (0..<k).map { _ in (0..<k).map { c in Double(min(max(k / 2 - c, -1), 1)) } }
}

private func convLayersLab() -> DkLab {
    DkLab(control: .tabs, tabs: convSizes.map { "k = \($0)" }, initialTab: 1) { tab, _ in
        let k = convSizes[tab]
        let kernel = edgeKernel(k)
        let map = conv(convInput, kernel)
        let o = map.count
        let kName = k == 1 ? "identity" : k == 3 ? "Sobel-x" : "\(k)×\(k) edge"
        let input = DkGrid(title: "input 7×7", rows: 7, cols: 7, cells: inputCells(convInput), maxCell: 22)
        let mapGrid = DkGrid(title: "feature map \(o)×\(o) · \(kName)", rows: o, cols: o, cells: mapCells(map, o * o, nil, maxAbs(map)), maxCell: 30)
        let stage = DkStage.grids(DkGrids(columns: [[input], [mapGrid]], weights: [1, 1]))
        let sizeLine = "out = ⌊(7 + 2·0 − \(k))/1⌋ + 1 = {\(o)}"
        let paramLine = "params = \(k)·\(k) + 1 bias = \(k * k + 1)"
        let lg = [legend(.blue, "Positive response"), legend(.pink, "Negative"), legend(.slate, "Zero")]
        let edge = map.flatMap { $0 }.min() ?? 0
        let frames = [
            DkFrame(
                header: nil,
                stage: .grids(DkGrids(columns: [[input], [DkGrid(title: "kernel \(k)×\(k)", rows: k, cols: k, cells: kernelCells(kernel), maxCell: 26)]], weights: [1, 1])),
                legend: Array(lg.prefix(2)), formula: ["kernel: \(kName), \(k)×\(k)", paramLine],
                headline: k == 1 ? "A 1×1 kernel is {one weight}." : "A \(k)×\(k) kernel: {\(k * k + 1)} weights, reused at every position.",
                body: k == 1 ? "It scales each pixel on its own, with no view of the neighbours."
                    : "Its left side is positive and its right side negative, so it responds to brightness changing left to right."
            ),
            DkFrame(
                header: nil, stage: stage, legend: lg, formula: [sizeLine, paramLine],
                headline: k == 1 ? "A 1×1 kernel just {copies} the input."
                    : k == 3 ? "The vertical edge lights up, the horizontal bar {doesn't}."
                    : k == 5 ? "At k = 5 the edge still shows, on a {3×3} map."
                    : "At k = 7 the whole image becomes {one number}: \(t(map[0][0])).",
                body: k == 1 ? "No neighbours means no edges. On many channels a 1×1 conv still mixes them, which is why Inception and MobileNet use it."
                    : k == 3 ? "Sobel-x responds only to left-right change: the edge gives \(t(edge)), and where the bar crosses it the response weakens to \(t(map[2][2]))."
                    : k == 5 ? "Each output sees 25 pixels; without padding the map shrinks by k − 1 = 4."
                    : "The kernel covers all 49 pixels, so there is nowhere left to slide."
            ),
            DkFrame(
                header: nil, stage: stage, legend: lg, formula: ["receptive field: 1 + 3·(3 − 1) = {7}", "weights: 3·9 = 27 vs 7·7 = 49"],
                headline: "Three stacked 3×3 layers see as far as one {7×7}.",
                body: "Stacks of small kernels see wide context with fewer weights and more non-linearities in between. That's VGG's whole design."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - Pooling

private let poolMap: Mat = [
    [0, 0, 0, 0, 0, 0, 0],
    [0, -1, -1, 0, 1, 1, 0],
    [0, -3, -3, 0, 3, 3, 0],
    [0, -4, -4, 0, 4, 4, 0],
    [0, -3, -3, 0, 3, 3, 0],
    [0, -1, -1, 0, 1, 1, 0],
    [0, 0, 0, 0, 0, 0, 0],
]

private func pool(_ m: Mat, _ avg: Bool) -> Mat {
    (0..<3).map { i in
        (0..<3).map { j in
            let w = [m[2 * i][2 * j], m[2 * i][2 * j + 1], m[2 * i + 1][2 * j], m[2 * i + 1][2 * j + 1]]
            return avg ? w.reduce(0, +) / 4 : w.max()!
        }
    }
}

private func poolingLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Max", "Average"]) { tab, _ in
        let avg = tab == 1
        let out = pool(poolMap, avg)
        let shifted: Mat = poolMap.map { row in (0..<7).map { c in c == 0 ? 0 : row[c - 1] } }
        let outShift = pool(shifted, avg)
        var same = 0
        for i in 0..<3 { for j in 0..<3 where abs(out[i][j] - outShift[i][j]) < 1e-9 { same += 1 } }
        var windows: [DkBox] = []
        for i in 0..<3 { for j in 0..<3 { windows.append(DkBox(r0: 2 * i, c0: 2 * j, r1: 2 * i + 1, c1: 2 * j + 1, dashed: true)) } }
        func stage(_ filled: Int, _ current: Int?, _ map: Mat = poolMap, _ pooled: Mat? = nil, _ title: String = "feature map 7×7 · 2×2 windows") -> DkStage {
            let win = current.map { [DkBox(r0: 2 * ($0 / 3), c0: 2 * ($0 % 3), r1: 2 * ($0 / 3) + 1, c1: 2 * ($0 % 3) + 1)] } ?? []
            return .grids(DkGrids(columns: [
                [DkGrid(title: title, rows: 7, cols: 7, cells: map.flatMap { $0 }.map { valueCell($0, 4) }, boxes: windows + win, maxCell: 26)],
                [DkGrid(title: "\(avg ? "avg" : "max") pool 3×3", rows: 3, cols: 3, cells: mapCells(pooled ?? out, filled, current, 4), maxCell: 46, note: "row 7, col 7 dropped")],
            ], weights: [1.5, 1]))
        }
        let op = avg ? "mean" : "max"
        func windowText(_ i: Int, _ j: Int) -> String {
            [poolMap[2 * i][2 * j], poolMap[2 * i][2 * j + 1], poolMap[2 * i + 1][2 * j], poolMap[2 * i + 1][2 * j + 1]].map { t($0) }.joined(separator: ", ")
        }
        let lg = [DkLegend(ink: .yellow, style: .ring, label: "Current window"), legend(.blue, "Positive"), legend(.pink, "Negative")]
        let rest = Array(lg.dropFirst())
        let frames = [
            DkFrame(
                header: nil, stage: stage(0, nil), legend: rest,
                formula: ["7×7 → ⌊7/2⌋ = 3 windows per side", "row 7 and column 7 fall outside every window"],
                headline: "Pooling tiles the map with {2×2 windows}.",
                body: "Each window becomes one number. Stride 2 means the windows don't overlap, so the map halves."
            ),
            DkFrame(
                header: nil, stage: stage(2, 2), legend: lg,
                formula: ["\(op)(\(windowText(0, 2))) = {\(t(out[0][2]))}", "row 1 done: \((0..<3).map { t(out[0][$0]) }.joined(separator: ", "))"],
                headline: avg ? "Average pooling keeps the window's {mean}: \(t(out[0][2]))." : "Max pooling keeps the window's {largest} value: \(t(out[0][2])).",
                body: avg ? "Strong and weak responses blend, so a lone spike is diluted by its neighbours."
                    : "Where in the window the response sat is thrown away; only how strong it was survives."
            ),
            DkFrame(
                header: nil, stage: stage(9, 5), legend: lg,
                formula: ["\(op)(\(windowText(1, 2))) = {\(t(out[1][2]))}", "shift input 1 px → \(same) of 9 pooled values unchanged"],
                headline: avg ? "49 responses become 9, each the {mean} of its window." : "49 responses become 9, keeping the {strongest} in each window.",
                body: avg ? "Average pool has no weights. After a 1-pixel shift, \(same) of the 9 outputs are identical: an average moves with every pixel in its window."
                    : "Max pool has no weights. After a 1-pixel shift, \(same) of the 9 outputs are identical, which is the small translation tolerance pooling buys."
            ),
            DkFrame(
                header: nil, stage: stage(9, nil, shifted, outShift, "shifted 1 px right"), legend: rest,
                formula: ["after shift: \(outShift.flatMap { $0 }.map { t($0) }.joined(separator: " "))", "unchanged: {\(same) of 9}"],
                headline: "Shift the input one pixel and {\(same) of 9} outputs stay put.",
                body: avg ? "Averages move whenever any pixel in the window changes, so average pooling tolerates shifts less than max."
                    : "A max only changes when the strongest value leaves its window, so small shifts mostly pass through."
            ),
        ]
        return stepActions(frames, "Next Window")
    }
}

// MARK: - Padding and stride

private let padSettings = [(1, 0), (1, 1), (2, 1)]

private func paddingLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["s1 · p0", "s1 · p1", "s2 · p1"], initialTab: 2) { tab, _ in
        let (s, p) = padSettings[tab]
        let size = 7 + 2 * p
        let map = conv(convInput, sobel, s, p)
        let o = map.count
        func stage(_ filled: Int, _ current: Int?) -> DkStage {
            var cells: [DkCell] = []
            for r in 0..<size {
                for c in 0..<size {
                    if r < p || c < p || r >= 7 + p || c >= 7 + p { cells.append(DkCell(text: "", tone: .pad)) }
                    else { cells.append(convInput[r - p][c - p] > 0 ? DkCell(text: "1", tone: .pos, level: 0.55) : DkCell(text: "0", tone: .zero)) }
                }
            }
            var boxes: [DkBox] = p > 0 ? [DkBox(r0: 0, c0: 0, r1: size - 1, c1: size - 1, dashed: true)] : []
            if let i = current { boxes.append(DkBox(r0: (i / o) * s, c0: (i % o) * s, r1: (i / o) * s + 2, c1: (i % o) * s + 2)) }
            return .grids(DkGrids(columns: [
                [DkGrid(title: p > 0 ? "input 7×7, padded to \(size)×\(size)" : "input 7×7", rows: size, cols: size, cells: cells, boxes: boxes, maxCell: 22)],
                [DkGrid(title: "output \(o)×\(o)", rows: o, cols: o, cells: mapCells(map, filled, current, 4), maxCell: 30)],
            ], weights: [1.35, 1]))
        }
        let inner = 7 + 2 * p - 3
        let sizeLine = s == 1 ? "out = ⌊(7 + 2·\(p) − 3)/1⌋ + 1 = {\(o)}" : "out = ⌊(7 + 2·\(p) − 3)/\(s)⌋ + 1 = ⌊\(inner)/\(s)⌋ + 1 = {\(o)}"
        let full = [DkLegend(ink: .grey, style: .dashedLine, label: "Zero padding"), DkLegend(ink: .yellow, style: .ring, label: "Window, step \(s)")]
        let lg = p > 0 ? full : Array(full.dropFirst())
        let mid = min(6, o * o - 1)
        let frames = [
            DkFrame(
                header: nil, stage: stage(0, 0), legend: lg, formula: [sizeLine, "kernel 3×3, stride \(s), padding \(p)"],
                headline: p == 0 ? "Without padding, the first window starts {inside} the image." : "Padding wraps the image in a ring of {zeros}.",
                body: p == 0 ? "The border pixels can never sit at a window's centre, so the map loses a pixel on every side."
                    : "Now the corner pixel can sit at a window's centre, like every other pixel."
            ),
            DkFrame(
                header: nil, stage: stage(mid, mid), legend: lg,
                formula: [sizeLine, s > 1 ? "window jumps \(s) px; padding keeps the border" : "window moves 1 px at a time"],
                headline: tab == 0 ? "Stride 1 without padding gives a {5×5} map."
                    : tab == 1 ? "Padding 1 keeps the map at {7×7}."
                    : "Stride 2 with padding 1 gives a {4×4} map from 7×7.",
                body: tab == 0 ? "A corner pixel is read by 1 window, a centre pixel by 9. Padding 1 would keep 7×7."
                    : tab == 1 ? "'Same' padding: every pixel, border included, can sit at a window's centre. Stride 2 would roughly halve it."
                    : "Padding lets the border pixels sit at a window's centre; stride 2 reads a quarter of the positions. No padding at stride 1 would give 5×5."
            ),
            DkFrame(
                header: nil, stage: stage(o * o, nil), legend: lg, formula: ["\(o)·\(o) windows × 9 = \(o * o * 9) multiplies", sizeLine],
                headline: "The full map: {\(o)×\(o)} from a 7×7 input.",
                body: tab == 0 ? "Each step without padding shrinks the map by 2; a deep net would run out of pixels."
                    : tab == 1 ? "Same size in and out, so layers can stack without the map shrinking."
                    : "Downsampling by stride replaces a pooling layer in many modern nets, and costs a quarter of stride 1."
            ),
        ]
        return stepActions(frames, "Slide Window")
    }
}

// MARK: - Architectures as per-layer shares

private struct Layer { let name: String; let shape: String; let params: Double; let macs: Double }

private func convLayer(_ name: String, _ k: Int, _ cin: Int, _ cout: Int, _ out: Int) -> Layer {
    Layer(name: name, shape: "\(out)×\(out)×\(cout)", params: Double(k * k * cin * cout + cout), macs: Double(out) * Double(out) * Double(cout) * Double(k * k * cin))
}

private func denseLayer(_ name: String, _ cin: Int, _ cout: Int) -> Layer {
    Layer(name: name, shape: "\(cout)", params: Double(cin * cout + cout), macs: Double(cin) * Double(cout))
}

private func archRows(_ layers: [Layer], _ hot: Int?) -> DkStage {
    let p = layers.reduce(0.0) { $0 + $1.params }, m = layers.reduce(0.0) { $0 + $1.macs }
    return .rows(DkRows(rows: layers.enumerated().map { i, l in
        if l.params == 0 { return DkRow(title: l.name, meta: l.shape, bars: [DkBar(frac: nil, ink: .blue, label: "no params")], hot: i == hot) }
        return DkRow(title: l.name, meta: l.shape,
                     bars: [DkBar(frac: l.params / p, ink: .blue, label: pct(l.params / p)), DkBar(frac: l.macs / m, ink: .orange, label: pct(l.macs / m))],
                     hot: i == hot, pair: true)
    }))
}

private let archLegend = [legend(.blue, "Parameters"), legend(.orange, "Compute (MACs)")]
private let archLegendShort = [legend(.blue, "Parameters"), legend(.orange, "Compute")]

private func lenetLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let layers = [
            convLayer("C1 conv 5×5", 5, 1, 6, 28), Layer(name: "S2 pool", shape: "14×14×6", params: 0, macs: 0),
            convLayer("C3 conv 5×5", 5, 6, 16, 10), Layer(name: "S4 pool", shape: "5×5×16", params: 0, macs: 0),
            convLayer("C5 conv 5×5", 5, 16, 120, 1), denseLayer("F6 dense", 120, 84), denseLayer("Output", 84, 10),
        ]
        let p = layers.reduce(0.0) { $0 + $1.params }, m = layers.reduce(0.0) { $0 + $1.macs }
        func sh(_ i: Int) -> Double { layers[i].params / p }
        func mc(_ i: Int) -> Double { layers[i].macs / m }
        let header = "per layer · \(count(p)) params, \(count(m)) MACs"
        let heads: [(String, String)] = [
            ("LeNet-5 has {\(count(p))} weights and does \(count(m)) multiply-adds.",
             "Blue is each layer's share of the weights, orange its share of the compute. They are not the same layers."),
            ("C1 has {\(comma(layers[0].params))} weights but does \(pct(mc(0))) of the work.",
             "Six 5×5 filters, each reused at all 784 positions of the 28×28 map."),
            ("S2 halves each map to 14×14 with {no weights}.",
             "Pooling only summarises each 2×2 window, so it adds almost nothing to either bar."),
            ("C3 holds {\(pct(sh(2)))} of the weights but does \(pct(mc(2))) of the work.",
             "It reuses \(comma(layers[2].params)) weights at 100 positions. C5 is the reverse: \(pct(sh(4), 0)) of the weights, used once."),
            ("S4 shrinks the maps to {5×5×16}: 400 numbers.",
             "After two conv-pool pairs the image is a small stack of feature maps, ready for the dense layers."),
            ("C5 holds {\(pct(sh(4)))} of the weights, used at one position.",
             "Its 5×5 kernel covers the whole 5×5 map, so it is a dense layer in all but name."),
            ("F6 is a plain dense layer: {\(comma(layers[5].params))} weights.",
             "Each weight is used once per image, so its compute share matches its weight share."),
            ("Ten outputs, one per {digit}.",
             "The whole net reads a 32×32 digit in \(count(m)) multiply-adds: tiny today, a lot for 1998."),
        ]
        let frames = heads.enumerated().map { i, hb in
            DkFrame(header: header, stage: archRows(layers, i == 0 ? nil : i - 1), legend: archLegend, formula: [], headline: hb.0, body: hb.1)
        }
        return stepActions(frames, "Next Layer")
    }
}

private func alexnetLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let layers = [
            convLayer("conv1 11×11 s4", 11, 3, 96, 55), convLayer("conv2 5×5", 5, 96, 256, 27),
            convLayer("conv3 3×3", 3, 256, 384, 13), convLayer("conv4 3×3", 3, 384, 384, 13), convLayer("conv5 3×3", 3, 384, 256, 13),
            denseLayer("fc6", 9216, 4096), denseLayer("fc7", 4096, 4096), denseLayer("fc8", 4096, 1000),
        ]
        let p = layers.reduce(0.0) { $0 + $1.params }, m = layers.reduce(0.0) { $0 + $1.macs }
        let convP = layers.prefix(5).reduce(0.0) { $0 + $1.params } / p
        let convM = layers.prefix(5).reduce(0.0) { $0 + $1.macs } / m
        let header = "\(count(p)) params, \(count(m)) MACs · pools omitted"
        var frames = [DkFrame(
            header: header, stage: archRows(layers, nil), legend: archLegendShort, formula: [],
            headline: "AlexNet: {\(count(p))} weights, \(count(m)) multiply-adds per image.",
            body: "Five conv layers then three dense ones, the net that won ImageNet 2012 on two GPUs.")]
        for (i, l) in layers.enumerated() {
            let ps = l.params / p, ms = l.macs / m
            let short = String(l.name.split(separator: " ").first ?? "")
            let side = String(l.shape.split(separator: "×").first ?? "")
            frames.append(DkFrame(
                header: header, stage: archRows(layers, i), legend: archLegendShort, formula: [],
                headline: ps > ms ? "\(short) alone is {\(pct(ps, 0))} of the model, \(pct(ms)) of the compute."
                    : "\(short) does {\(pct(ms))} of the compute with \(pct(ps)) of the weights.",
                body: i < 5 ? "Each of its \(count(l.params)) weights is reused at all \(side)×\(side) positions of its output map."
                    : "Every input connects to every output, so each weight is used once per image."))
        }
        frames.append(DkFrame(
            header: header, stage: archRows(layers, nil), legend: archLegendShort, formula: [],
            headline: "The five conv layers do {\(pct(convM, 0))} of the work with \(pct(convP, 0)) of the weights.",
            body: "Weights live in the dense layers, compute in the convs. Later nets dropped most of the dense layers."))
        return stepActions(frames, "Next Layer")
    }
}

private func vggLayers(_ cfg: [Int]) -> [Layer] {
    let ch = [64, 128, 256, 512, 512], sz = [224, 112, 56, 28, 14]
    var cin = 3
    var blocks: [Layer] = []
    for (b, convs) in cfg.enumerated() {
        var params = 0.0, macs = 0.0
        for _ in 0..<convs {
            params += 9 * Double(cin) * Double(ch[b]) + Double(ch[b])
            macs += Double(sz[b]) * Double(sz[b]) * 9 * Double(cin) * Double(ch[b])
            cin = ch[b]
        }
        blocks.append(Layer(name: "block\(b + 1) · \(convs)× 3×3", shape: "\(sz[b])×\(sz[b])×\(ch[b])", params: params, macs: macs))
    }
    return blocks + [denseLayer("fc6", 25088, 4096), denseLayer("fc7", 4096, 4096), denseLayer("fc8", 4096, 1000)]
}

private func vggLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["VGG-16", "VGG-19"]) { tab, _ in
        let cfg = tab == 0 ? [2, 2, 3, 3, 3] : [2, 2, 4, 4, 4]
        let convs = cfg.reduce(0, +)
        let layers = vggLayers(cfg)
        let p = layers.reduce(0.0) { $0 + $1.params }, m = layers.reduce(0.0) { $0 + $1.macs }
        let name = tab == 0 ? "VGG-16" : "VGG-19"
        let convM = layers.prefix(5).reduce(0.0) { $0 + $1.macs } / m
        let header = "\(count(p)) params, \(count(m)) MACs"
        var frames = [DkFrame(
            header: header, stage: archRows(layers, nil), legend: archLegendShort, formula: [],
            headline: "\(name): {\(convs)} conv layers, every one 3×3.",
            body: "Five blocks, each halving the map and doubling the channels, then three dense layers.")]
        for (i, l) in layers.enumerated() {
            let ps = l.params / p, ms = l.macs / m
            let side = String(l.shape.split(separator: "×").first ?? "")
            frames.append(DkFrame(
                header: header, stage: archRows(layers, i), legend: archLegendShort, formula: [],
                headline: i == 5 ? "fc6 holds {\(pct(ps, 0))} of \(name)'s \(count(p)) weights."
                    : i > 5 ? "\(l.name) holds {\(pct(ps))} of the weights, \(pct(ms)) of the compute."
                    : "block\(i + 1) does {\(pct(ms))} of the compute with \(pct(ps)) of the weights.",
                body: i == 5 ? "The \(convs) conv layers, all 3×3, do \(pct(convM, 0)) of the compute. Two stacked 3×3s see as far as one 5×5 with 18C² weights instead of 25C²."
                    : i > 5 ? "Dense layers use each weight once per image, so they are heavy to store and cheap to run."
                    : "\(cfg[i]) convs on a \(side)×\(side) map: big maps make every weight work many times."))
        }
        return stepActions(frames, "Next Block")
    }
}

// MARK: - Inception: 1×1 reductions

private func inceptionLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Naive", "With 1×1"], initialTab: 1) { tab, _ in
        let cin = 192.0
        struct Branch { let name: String; let meta: String; let naive: Double; let reduced: Double }
        let branches = [
            Branch(name: "1×1 · 64", meta: "", naive: cin * 64, reduced: cin * 64),
            Branch(name: "3×3 · 128", meta: "reduce 192→96", naive: 9 * cin * 128, reduced: cin * 96 + 9 * 96 * 128),
            Branch(name: "5×5 · 32", meta: "reduce 192→16", naive: 25 * cin * 32, reduced: cin * 16 + 25 * 16 * 32),
            Branch(name: "pool · 32", meta: "", naive: cin * 32, reduced: cin * 32),
        ]
        let top = branches.map(\.naive).max()!
        let naive = branches.reduce(0.0) { $0 + $1.naive }, reduced = branches.reduce(0.0) { $0 + $1.reduced }
        let both = tab == 1
        func rows(_ hot: Int?) -> DkStage {
            .rows(DkRows(rows: branches.enumerated().map { i, b in
                DkRow(title: b.name, meta: both ? b.meta : "",
                      bars: [DkBar(frac: b.naive / top, ink: .orange, label: count(b.naive))] + (both ? [DkBar(frac: b.reduced / top, ink: .green, label: count(b.reduced))] : []),
                      hot: i == hot)
            }))
        }
        let header = "MACs per position · 192 input channels"
        let lg = [legend(.orange, "Naive")] + (both ? [legend(.green, "With 1×1 reduce")] : [])
        let b3 = branches[1], b5 = branches[2]
        let frames: [DkFrame] = !both ? [
            DkFrame(
                header: header, stage: rows(nil), legend: lg,
                formula: ["module: \(branches.map { count($0.naive) }.joined(separator: " + "))", "= {\(count(naive))} per position"],
                headline: "Inception runs {four branches} side by side and concatenates them.",
                body: "1×1, 3×3, 5×5 and pooling each look at a different scale; the next layer gets all four."
            ),
            DkFrame(
                header: header, stage: rows(1), legend: lg, formula: ["3×3: 9·192·128 = {\(count(b3.naive))}", "\(pct(b3.naive / naive, 0)) of the module"],
                headline: "The 3×3 branch alone costs {\(count(b3.naive))} MACs per position.",
                body: "Every one of its 128 filters reads all 192 input channels at 9 positions."
            ),
            DkFrame(
                header: header, stage: rows(2), legend: lg, formula: ["5×5: 25·192·32 = {\(count(b5.naive))}", "just 32 output channels"],
                headline: "The 5×5 branch costs {\(count(b5.naive))} for only 32 channels.",
                body: "A 5×5 kernel over 192 channels is 4,800 weights per filter. Switch to “With 1×1” to see the fix."
            ),
        ] : [
            DkFrame(
                header: header, stage: rows(nil), legend: lg, formula: ["module: \(count(naive)) → {\(count(reduced))}", "= \(n(naive / reduced, 1))× cheaper"],
                headline: "1×1 reductions shrink the module to {\(count(reduced))} MACs per position.",
                body: "A 1×1 conv squeezes 192 channels down before the expensive 3×3 and 5×5 kernels run."
            ),
            DkFrame(
                header: header, stage: rows(1), legend: lg,
                formula: ["3×3: \(count(b3.naive)) → 192·96 + 9·96·128 = {\(count(b3.reduced))}", "\(n(b3.naive / b3.reduced, 1))× cheaper"],
                headline: "Reducing to 96 channels cuts the 3×3 branch {\(n(b3.naive / b3.reduced, 1))×}.",
                body: "The 1×1 costs 192·96 per position, but the 3×3 now reads 96 channels instead of 192."
            ),
            DkFrame(
                header: header, stage: rows(2), legend: lg,
                formula: ["5×5: 25·192·32 = \(count(b5.naive)) → 192·16 + 25·16·32 = {\(count(b5.reduced))}", "module: \(count(naive)) → \(count(reduced)) = {\(n(naive / reduced, 1))× cheaper}"],
                headline: "A 1×1 squeeze cuts the 5×5 branch {\(n(b5.naive / b5.reduced, 1))×}.",
                body: "Squeeze channels first, then apply the big kernel. The whole module drops from \(count(naive)) to \(count(reduced)) per position."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - ResNet: gradients with and without skips

private let resLayers = 20, resWidth = 16

/// h ← skip·h + branch·W·ReLU(h) for 20 layers; mean |∂L/∂h| per layer relative to the output's.
private func resGrad(_ skip: Double, _ branch: Double) -> [Double] {
    var s: Int64 = 404
    func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
    func g() -> Double { let a = max(u(), 1e-12); let b = u(); return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b) }
    let sigma = 0.25, batch = 32
    var w: [[[Double]]] = []
    for _ in 0..<resLayers {
        var m: [[Double]] = []
        for _ in 0..<resWidth { var row: [Double] = []; for _ in 0..<resWidth { row.append(g() * sigma) }; m.append(row) }
        w.append(m)
    }
    var h: [[Double]] = []
    for _ in 0..<batch { var row: [Double] = []; for _ in 0..<resWidth { row.append(g()) }; h.append(row) }
    var hs: [[[Double]]] = []
    for l in 0..<resLayers {
        hs.append(h)
        let prev = h
        h = (0..<batch).map { b in
            (0..<resWidth).map { i in
                var z = 0.0
                for j in 0..<resWidth { z += w[l][i][j] * max(0, prev[b][j]) }
                return skip * prev[b][i] + branch * z
            }
        }
    }
    var gr = Array(repeating: Array(repeating: 1.0, count: resWidth), count: batch)
    var out = Array(repeating: 0.0, count: resLayers)
    for l in stride(from: resLayers - 1, through: 0, by: -1) {
        out[l] = gr.reduce(0.0) { $0 + $1.reduce(0.0) { $0 + abs($1) } } / Double(batch * resWidth)
        let prev = hs[l], cur = gr
        gr = (0..<batch).map { b in
            (0..<resWidth).map { j in
                var gu = 0.0
                for i in 0..<resWidth { gu += w[l][i][j] * branch * cur[b][i] }
                return skip * cur[b][j] + (prev[b][j] > 0 ? gu : 0)
            }
        }
    }
    return out.map { $0 / out.last! }
}

private let resRuns: [[Double]] = [resGrad(0, 1), resGrad(1, 1), resGrad(1, 0.2)]

private func sciText(_ v: Double) -> String {
    var e = Int(log10(v).rounded(.down))
    var m = (v / pow(10, Double(e)) * 10 + 0.5).rounded(.down) / 10
    if m >= 10 { m /= 10; e += 1 }
    return dkNum(m, 1) + "e" + (e < 0 ? "−" : "") + String(abs(e))
}

private func mag(_ v: Double) -> String { v >= 0.01 && v < 1000 ? n(v, v >= 100 ? 1 : 2) : sciText(v) }

private func resnetLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Plain", "Residual", "Both"], initialTab: 2) { tab, _ in
        let plain = resRuns[0], res = resRuns[1], scaled = resRuns[2]
        func lineOf(_ v: [Double], _ ink: DkInk, dashed: Bool = false) -> DkLine {
            DkLine(pts: v.enumerated().map { DkP(Double($0.offset) + 1, log10($0.element)) }, ink: ink, dashed: dashed)
        }
        let shown: [([Double], DkInk)] = tab == 0 ? [(plain, .pink)] : tab == 1 ? [(res, .green)] : [(plain, .pink), (res, .green)]
        func plot(_ extra: Bool) -> DkStage {
            let all = shown.map(\.0) + (extra ? [scaled] : [])
            let logs = all.flatMap { $0 }.map { log10($0) } + [0]
            let lo = logs.min()!.rounded(.down) - 0.2, hi = logs.max()!.rounded(.up) + 0.2
            let ticks: [(Double, String)] = stride(from: Int(hi.rounded(.down)), through: Int(lo.rounded(.up)), by: -1).map { k in
                (Double(k), k == 0 ? "1" : "1e" + (k < 0 ? "−" : "") + String(abs(k)))
            }
            return .plot(DkPlot(
                xr: (0.5, Double(resLayers) + 0.5), yr: (lo, hi), yTicks: ticks, xLeft: "layer 1", xRight: "layer \(resLayers)",
                lines: shown.map { lineOf($0.0, $0.1) } + (extra ? [lineOf(scaled, .blue, dashed: true)] : []),
                dots: shown.map { DkDot(p: DkP(1, log10($0.0[0]))) }, axis: false))
        }
        let header = "gradient norm per layer, relative to the output"
        let lg = shown.map { DkLegend(ink: $0.1, style: .line, label: $0.1 == .pink ? "Plain" : "Residual") }
        let p1 = mag(plain[0]), r1 = mag(res[0])
        let frames = [
            DkFrame(
                header: header, stage: plot(false), legend: lg,
                formula: tab == 0 ? ["h ← W·ReLU(h), 20 times", "∂h/∂h_prev = W·ReLU′"]
                    : tab == 1 ? ["h ← h + W·ReLU(h), 20 times", "∂h/∂h_prev = 1 + W·ReLU′"]
                    : ["plain: h ← W·ReLU(h)", "residual: h ← h + W·ReLU(h)"],
                headline: tab == 0 ? "A plain stack multiplies the gradient by {every layer's} Jacobian."
                    : tab == 1 ? "A skip connection adds the input back: {x + F(x)}."
                    : "Same 20 layers, same weights: {with and without} skips.",
                body: "Backprop runs from the output (layer \(resLayers), gradient 1) back to layer 1 through each layer in turn."
            ),
            DkFrame(
                header: header, stage: plot(false), legend: lg,
                formula: ["∂(x + F(x))/∂x = 1 + ∂F/∂x", "layer 1: plain {\(p1)} · residual {\(r1)}"],
                headline: tab == 0 ? "Layer 1 gets {\(p1)} of the output's gradient."
                    : tab == 1 ? "With skips, layer 1 gets {\(r1)×} the output gradient."
                    : "With skips, layer 1 gets {\(r1)×} the output gradient, not \(p1).",
                body: tab == 0 ? "Each layer's gain is under 1 at this init, so 19 of them multiply the signal away. The first layers barely train."
                    : "The identity path adds 1 to every layer's derivative, so the signal can't be multiplied away. Unnormalised, it grows instead; real ResNets add BatchNorm."
            ),
            DkFrame(
                header: header, stage: plot(true), legend: lg + [DkLegend(ink: .blue, style: .dashedLine, label: "Residual, branch × 0.2")],
                formula: ["h ← h + 0.2·F(h)", "layer 1: {\(mag(scaled[0]))}"],
                headline: "Shrink each branch and layer 1 gets {\(mag(scaled[0]))}: no vanishing, no explosion.",
                body: "Each block starts near the identity, which is what BatchNorm's learned scale or a zero-initialised last layer gives a real ResNet. That's how 152 layers train."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - DenseNet: concatenation

private func densenetLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let k = 32, input = 64, layers = 6
        let inks: [DkInk] = [.blue, .violet, .green, .orange, .pink, .sky]
        func inCh(_ l: Int) -> Int { input + (l - 1) * k }
        func params(_ l: Int) -> Int { inCh(l) * 4 * k + 9 * 4 * k * k }
        let blockParams = Double((1...layers).reduce(0) { $0 + params($1) })
        let connections = (1...layers).reduce(0, +)
        func stage(_ hot: Int) -> DkStage {
            .segs(DkSegs(rows: (1...(layers + 1)).map { l in
                let sources = l > layers ? layers : l - 1
                return DkSegRow(segs: [DkSeg(units: Double(input), ink: .slate)] + (0..<sources).map { DkSeg(units: Double(k), ink: inks[$0], dim: l != hot) },
                                label: l > layers ? "out" : "L\(l)", hot: l == hot)
            }, scale: Double(input + layers * k) * 1.04, barHeight: 20))
        }
        func legendFor(_ hot: Int) -> [DkLegend] {
            [legend(.slate, "block input \(input)")] + (0..<min(hot - 1, layers)).map { legend(inks[$0], $0 == 0 ? "from L1" : "L\($0 + 1)") }
        }
        let chips = [DkChip(key: "connections", value: "\(connections)", tint: true), DkChip(key: "block params", value: count(blockParams))]
        let header = "input channels per layer, coloured by source · k = \(k)"
        let frames = [2, 4, 6, 7].map { l -> DkFrame in
            let isOut = l > layers
            return DkFrame(
                header: header, stage: stage(l), legend: legendFor(l),
                formula: isOut ? ["out: \(input) + \(layers)·\(k) = {\(input + layers * k)} channels", "block params: {\(count(blockParams))}"]
                    : ["L\(l) in: \(input) + \(l - 1)·\(k) = {\(inCh(l))} channels", "params: \(inCh(l))·\(4 * k) + 9·\(4 * k)·\(k) = {\(count(Double(params(l))))}"],
                headline: l == 2 ? "Layer 2 takes the input plus {layer 1's} \(k) channels."
                    : l == 4 ? "Layer 4 concatenates {all three} earlier outputs with the input."
                    : l == 6 ? "Layer 6 reads {\(inCh(6))} channels: everything before it."
                    : "The block outputs {\(input + layers * k)} channels: input plus 6 × \(k).",
                body: l == 2 ? "Instead of adding like a ResNet, DenseNet stacks feature maps side by side, so nothing earlier is overwritten."
                    : l == 4 ? "Each layer adds only \(k) channels, so the block stays small: \(count(blockParams)) weights for \(connections) direct connections."
                    : l == 6 ? "A 1×1 bottleneck squeezes those \(inCh(6)) channels to \(4 * k) before the 3×3, so wide inputs stay cheap."
                    : "A transition layer then halves the channels and the map before the next block.",
                chips: chips
            )
        }
        return stepActions(frames, "Next Layer")
    }
}

// MARK: - MobileNet: depthwise separable

private let mobileNs = [64, 256, 1024]

private func mobilenetLab() -> DkLab {
    DkLab(control: .tabs, tabs: mobileNs.map { "N = \($0)" }, initialTab: 1) { tab, _ in
        let nCh = Double(mobileNs[tab])
        let hw = 196.0
        let std = hw * nCh * nCh * 9, dw = hw * nCh * 9, pw = hw * nCh * nCh
        let ratio = 1 / nCh + 1.0 / 9
        let pwShare = pw / (dw + pw)
        let nT = mobileNs[tab]
        func stage(_ showDw: Bool, _ showPw: Bool) -> DkStage {
            var rows = [DkSegRow(segs: [DkSeg(units: std, ink: .orange)], caption: "standard 3×3 conv", value: count(std))]
            if showDw {
                rows.append(DkSegRow(segs: [DkSeg(units: dw, ink: .blue)] + (showPw ? [DkSeg(units: pw, ink: .green)] : []),
                                     caption: showPw ? "depthwise 3×3 + pointwise 1×1" : "depthwise 3×3", value: count(showPw ? dw + pw : dw)))
            }
            return .segs(DkSegs(rows: rows, scale: std,
                                notes: showPw ? ["depthwise \(count(dw)) · pointwise \(count(pw))", "{pointwise is \(pct(pwShare, 0)) of what's left}"] : [],
                                barHeight: 34))
        }
        let header = "MACs for one layer · 14×14, \(nT) → \(nT)"
        let lg = [legend(.orange, "Standard"), legend(.blue, "Depthwise"), legend(.green, "Pointwise 1×1")]
        let frames = [
            DkFrame(
                header: header, stage: stage(false, false), legend: Array(lg.prefix(1)),
                formula: ["14·14 · \(nT)·\(nT) · 9 = {\(count(std))}", "every filter reads every channel"],
                headline: "A standard 3×3 conv costs {\(count(std))} MACs here.",
                body: "Each of its \(nT) filters looks at all \(nT) input channels at 9 positions: filtering and channel mixing in one step."
            ),
            DkFrame(
                header: header, stage: stage(true, false), legend: Array(lg.prefix(2)),
                formula: ["depthwise: 14·14 · \(nT) · 9 = {\(count(dw))}", "one 3×3 filter per channel"],
                headline: "Depthwise filters each channel {alone}: \(count(dw)) MACs.",
                body: "It does the spatial filtering but never mixes channels, which is \(nT)× cheaper and not enough on its own."
            ),
            DkFrame(
                header: header, stage: stage(true, true), legend: lg,
                formula: ["ratio = 1/N + 1/k² = 1/\(nT) + 1/9 = {\(n(ratio, 4))}", "→ \(n(1 / ratio, 1))× cheaper, same output shape"],
                headline: "Splitting the conv makes it {\(n(1 / ratio, 1))×} cheaper.",
                body: "Depthwise filters each channel alone; the 1×1 mixes them. Nearly all remaining cost is the 1×1, so MobileNet's speed depends on fast pointwise convs."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - EfficientNet: compound scaling

private let effA = 1.2, effB = 1.1, effG = 1.15

private func efficientnetLab() -> DkLab {
    DkLab(control: .stepperOnly, stepper: DkStepper(caption: "φ", values: (0...7).map(Double.init), initial: 3) { n($0, 0) }) { _, p in
        let phi = Double(p)
        let d = pow(effA, phi), w = pow(effB, phi), r = pow(effG, phi)
        let px = Int((224 * r + 0.5).rounded(.down))
        let base = effA * effB * effB * effG * effG
        let flops = pow(base, phi)
        let maxD = pow(effA, 7)
        let phiT = n(phi, 0)
        func rows(_ hot: Int?) -> DkStage {
            .rows(DkRows(rows: [
                DkRow(title: "depth α^φ", meta: "", bars: [DkBar(frac: d / maxD, ink: .blue, label: "\(n(d))×")], hot: hot == 0),
                DkRow(title: "width β^φ", meta: "", bars: [DkBar(frac: w / maxD, ink: .orange, label: "\(n(w))×")], hot: hot == 1),
                DkRow(title: "resolution γ^φ", meta: "", bars: [DkBar(frac: r / maxD, ink: .green, label: "\(n(r))× → \(px) px")], hot: hot == 2),
            ]))
        }
        let header = "scale factors from B0 at φ = \(phiT)"
        let lg = [legend(.blue, "Depth"), legend(.orange, "Width"), legend(.green, "Resolution")]
        let supers = Array("⁰¹²³⁴⁵⁶⁷")
        let frames = [
            DkFrame(
                header: header, stage: rows(nil), legend: lg,
                formula: ["α = 1.2, β = 1.1, γ = 1.15, found on B0", "φ = \(phiT): depth ×\(n(d)), width ×\(n(w)), resolution ×\(n(r))"],
                headline: "At φ = \(phiT) the network is {\(n(d))×} deeper, \(n(w))× wider and sees \(px)px images.",
                body: "One knob grows all three dimensions together instead of tuning each by hand."
            ),
            DkFrame(
                header: header, stage: rows(nil), legend: lg,
                formula: ["α·β²·γ² = 1.2·1.1²·1.15² = \(n(base, 3)) ≈ 2", "FLOPs × \(n(base, 3))\(p == 1 ? "" : String(supers[p])) = {\(n(flops))×}"],
                headline: "φ = \(phiT) costs {\(n(flops, 1))×} the FLOPs of B0.",
                body: "α, β, γ are fixed so each step of φ roughly doubles compute; width and resolution count twice because FLOPs scale with their square."
            ),
            DkFrame(
                header: header, stage: rows(0), legend: lg,
                formula: ["same budget on depth alone: ×\(n(flops)) layers", "accuracy gains flatten past a few × depth"],
                headline: "Spent on depth alone, the same budget makes a {\(n(flops, 1))×} deeper net.",
                body: "Very deep, narrow, low-resolution nets gain little per FLOP. The paper found that balancing all three wins at every budget."
            ),
        ]
        return withActions(frames) { _ in "Next" }
    }
}

// MARK: - Vision Transformers: patches as tokens

private let vitPatches = [32, 16, 8]

private func vitLab() -> DkLab {
    DkLab(control: .tabs, tabs: vitPatches.map { "\($0) px" }, initialTab: 1) { tab, _ in
        let ps = vitPatches[tab]
        let side = 224 / ps
        let patches = side * side, tokens = patches + 1, dim = ps * ps * 3
        let weights = Double(dim) * 768 + 768
        let pairs = Double(tokens) * Double(tokens)
        let ref = 197.0 * 197.0
        func stage(_ done: Int, _ current: Int?) -> DkStage {
            .grids(DkGrids(columns: [[DkGrid(title: "", rows: side, cols: side, cells: (0..<patches).map { i in
                i == current ? DkCell(text: "", tone: .hot) : i < done ? DkCell(text: "", tone: .embedded) : DkCell(text: "", tone: .empty)
            }, maxCell: 22)]], weights: [1]))
        }
        let mid = min(patches - 1, patches * 37 / 196)
        let header = "224×224 image · \(ps)×\(ps) patches"
        let lg = [legend(.blue, "Embedded"), legend(.yellow, "Current patch"), legend(.slate, "Waiting")]
        let frames = [
            DkFrame(
                header: header, stage: stage(0, nil), legend: Array(lg.dropFirst(2)),
                formula: ["224 / \(ps) = \(side) per side → \(side)·\(side) = {\(patches)} patches"],
                headline: "Cut the image into {\(patches)} patches of \(ps)×\(ps).",
                body: "A transformer reads a sequence, so the image becomes a sequence of patches, read row by row."
            ),
            DkFrame(
                header: header, stage: stage(0, 0), legend: Array(lg.dropFirst()),
                formula: ["patch: \(ps)·\(ps)·3 = {\(dim)} numbers", "flattened into one vector"],
                headline: "The first patch becomes {\(dim) numbers}.",
                body: "Its pixels are unrolled into a flat vector, losing nothing."
            ),
            DkFrame(
                header: header, stage: stage(mid, mid), legend: lg,
                formula: ["patch: \(ps)·\(ps)·3 = \(dim) → linear → 768 · shared \(count(weights)) weights", "tokens: \(patches) + 1 class = {\(tokens)} · attention pairs \(comma(pairs))"],
                headline: "Every patch goes through the {same} linear layer.",
                body: "No convolution: \(tokens) tokens enter a standard transformer."
            ),
            DkFrame(
                header: header, stage: stage(patches, nil), legend: Array(lg.prefix(1)),
                formula: ["pairs = tokens² = \(tokens)² = {\(comma(pairs))}", "16 px reference: 38,809"],
                headline: "Attention compares all {\(comma(pairs))} token pairs.",
                body: tab == 0 ? "Big patches are cheap: \(n(ref / pairs, 0))× fewer pairs than 16 px, but each token is a coarse 32×32 block."
                    : tab == 1 ? "This is ViT-B/16: halving the patch size would give 4× the tokens and 16× the attention cost."
                    : "Small patches see finer detail, but \(tokens) tokens cost \(n(pairs / ref, 0))× the attention of 16 px."
            ),
        ]
        return stepActions(frames, "Embed Next")
    }
}

// MARK: - Transfer learning: what to unfreeze

private func transferLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Head only", "+ layer4", "All"]) { tab, _ in
        let groups: [(String, Double)] = [
            ("conv1 + bn · edges", 9536), ("layer1 · textures", 147968), ("layer2 · patterns", 525568),
            ("layer3 · parts", 2099712), ("layer4 · objects", 8393728),
        ]
        let head = 512.0 * 10 + 10, oldHead = 512.0 * 1000 + 1000
        let backbone = groups.reduce(0.0) { $0 + $1.1 }
        let total = backbone + head
        let top = groups.map(\.1).max()!
        func training(_ i: Int) -> Bool { tab == 0 ? false : tab == 1 ? i == 4 : true }
        func rows(_ newHead: Bool, _ showTraining: Bool) -> DkStage {
            .rows(DkRows(rows: groups.enumerated().map { i, g in
                let on = showTraining && training(i)
                return DkRow(title: g.0, meta: on ? "training" : "frozen", bars: [DkBar(frac: g.1 / top, ink: on ? .yellow : .slate, label: count(g.1))], hot: on)
            } + [DkRow(title: newHead ? "fc head · 10 classes" : "fc head · 1000 classes", meta: newHead ? "training" : "frozen",
                       bars: [DkBar(frac: (newHead ? head : oldHead) / top, ink: newHead ? .yellow : .slate, label: count(newHead ? head : oldHead))], hot: newHead)]))
        }
        let trainable = head + groups.enumerated().filter { training($0.offset) }.reduce(0.0) { $0 + $1.element.1 }
        let share = trainable / total
        let lg = [legend(.slate, "Frozen"), legend(.yellow, "Training")]
        let newHeader = "ResNet-18 · ImageNet weights, new 10-class head"
        let frames = [
            DkFrame(
                header: "ResNet-18 · ImageNet weights", stage: rows(false, false), legend: Array(lg.prefix(1)),
                formula: ["\(count(backbone + oldHead)) weights, trained on 1.28M images", "1000 ImageNet classes"],
                headline: "A pretrained ResNet-18 already detects {edges to objects}.",
                body: "Early layers find generic edges and textures; later ones find parts and whole objects of ImageNet's classes."
            ),
            DkFrame(
                header: newHeader, stage: rows(true, false), legend: lg,
                formula: ["old fc: 512·1000 + 1000 = \(comma(oldHead))", "new fc: 512·10 + 10 = {\(comma(head))}"],
                headline: "Swap the 1000-class head for a {10-class} one.",
                body: "The new head starts random; everything below it keeps its ImageNet weights."
            ),
            DkFrame(
                header: newHeader, stage: rows(true, true), legend: lg,
                formula: tab == 0 ? ["fc: 512·10 + 10 = {\(comma(head))} trainable", "of \(count(total)) total = \(pct(share, 2))"]
                    : tab == 1 ? ["layer4 + fc = {\(count(trainable))} trainable", "of \(count(total)) total = \(pct(share))"]
                    : ["all {\(count(total))} weights trainable", "at a learning rate ~10× below training from scratch"],
                headline: tab == 0 ? "Only {\(pct(share, 2))} of the weights are trained."
                    : tab == 1 ? "Unfreezing layer4 trains {\(pct(share))} of the weights."
                    : "Fine-tuning everything trains all {\(count(total))}.",
                body: tab == 0 ? "The backbone's edges and textures already fit the new task, so 10 classes can be learned from a few hundred images."
                    : tab == 1 ? "Its object-level features adapt to the new classes while edges and textures stay fixed. Use a smaller learning rate than the head's."
                    : "It needs thousands of images per class, or the small dataset gets memorised. Usually done last, with a tiny learning rate."
            ),
            DkFrame(
                header: newHeader, stage: rows(true, true), legend: lg,
                formula: ["head only: ~100s of images", "+ layer4: ~1,000s · all: ~10,000s (rule of thumb)"],
                headline: "More trainable weights need {more data}.",
                body: "Start with the head, then unfreeze from the top down while validation accuracy keeps improving."
            ),
        ]
        return stepActions(frames, "Next")
    }
}
