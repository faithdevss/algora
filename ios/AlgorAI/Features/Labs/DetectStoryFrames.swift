import SwiftUI

// Port of DetectStoryFrames.kt: R-CNN, Fast and Faster R-CNN, YOLO, SSD, RetinaNet, U-Net, Mask R-CNN and
// the segmentation types. One shared 320×320 scene with two ground-truth boxes runs through the
// detectors, so the same objects are proposed, snapped to the stride-16 grid, anchored and assigned to
// YOLO cells; every IoU, snap error and count is computed.

let detectStoryTopicIds: Set<String> = [
    "rcnn", "fast_rcnn", "faster_rcnn", "yolo", "ssd", "retinanet", "unet", "mask_rcnn", "segmentation_types",
]

func detectLab(_ topicId: String) -> DkLab? {
    switch topicId {
    case "rcnn": rcnnLab()
    case "fast_rcnn": fastRcnnLab()
    case "faster_rcnn": fasterRcnnLab()
    case "yolo": yoloLab()
    case "ssd": ssdLab()
    case "retinanet": retinaLab()
    case "unet": unetLab()
    case "mask_rcnn": maskRcnnLab()
    case "segmentation_types": segmentationLab()
    default: nil
    }
}

// MARK: - Formatting

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }

private func comma(_ v: Double) -> String {
    let digits = Array(String(Int64(abs(v))))
    var out = ""
    for (i, c) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out += "," }
        out.append(c)
    }
    return out
}

private func kilo(_ v: Double) -> String { v >= 1e4 ? n(v / 1e3, 1) + "k" : comma(v) }

/// 0.0101 · 0.000001 · 1.0e−12: six decimals while they show a digit, scientific below.
private func small(_ v: Double) -> String {
    if v >= 1e-6 { return n(v, v >= 1e-3 ? 4 : 6) }
    var e = Int(log10(v).rounded(.down))
    var m = (v / pow(10, Double(e)) * 10 + 0.5).rounded(.down) / 10
    if m >= 10 { m /= 10; e += 1 }
    return n(m, 1) + "e−" + String(abs(e))
}

private func pct(_ share: Double, _ d: Int = 1) -> String { n(share * 100, d) + "%" }

private func legend(_ ink: DkInk, _ label: String, _ style: SwatchStyle = .fill) -> DkLegend { DkLegend(ink: ink, style: style, label: label) }

private func stepActions(_ frames: [DkFrame], _ action: String) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = i == frames.count - 1 ? "Start Over" : action; return f }
}

// MARK: - The shared scene

private struct Box {
    let x1: Double, y1: Double, x2: Double, y2: Double
    init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) { self.x1 = x1; self.y1 = y1; self.x2 = x2; self.y2 = y2 }
    var w: Double { x2 - x1 }
    var h: Double { y2 - y1 }
    var area: Double { w * h }
    var cx: Double { (x1 + x2) / 2 }
    var cy: Double { (y1 + y2) / 2 }
}

private let imageSize = 320.0
private let stride16 = 16.0

private let truth1 = Box(40, 76, 140, 231)
private let truth2 = Box(165, 101, 249, 213)
private let truths = [truth1, truth2]

private func inter(_ a: Box, _ b: Box) -> Double { max(0, min(a.x2, b.x2) - max(a.x1, b.x1)) * max(0, min(a.y2, b.y2) - max(a.y1, b.y1)) }

private func iou(_ a: Box, _ b: Box) -> Double { let i = inter(a, b); return i / (a.area + b.area - i) }

private func rect(_ b: Box, _ ink: DkInk, dashed: Bool = false, fill: Bool = false, thin: Bool = false, label: String? = nil, bins: Int = 0) -> DkRect {
    DkRect(x1: b.x1, y1: b.y1, x2: b.x2, y2: b.y2, ink: ink, dashed: dashed, fill: fill, thin: thin, label: label, bins: bins)
}

private func truthRects(_ labels: Bool = false) -> [DkRect] { truths.enumerated().map { rect($0.element, .green, label: labels ? "truth \($0.offset + 1)" : nil) } }

private let truthLegend = legend(.green, "Ground truth", .ring)

/// RoIPool's snap to the stride-16 grid: corners floored and ceiled to whole feature cells.
private struct Snap {
    let box: Box
    let c1: Int, r1: Int, c2: Int, r2: Int
    let snapped: Box
    let errors: [Double]
    init(_ box: Box) {
        self.box = box
        c1 = Int((box.x1 / stride16).rounded(.down)); r1 = Int((box.y1 / stride16).rounded(.down))
        c2 = Int((box.x2 / stride16).rounded(.up)); r2 = Int((box.y2 / stride16).rounded(.up))
        snapped = Box(Double(c1) * 16, Double(r1) * 16, Double(c2) * 16, Double(r2) * 16)
        errors = [box.x1 - snapped.x1, box.y1 - snapped.y1, snapped.x2 - box.x2, snapped.y2 - box.y2]
    }
    var errorText: String { errors.map { n($0, 0) }.joined(separator: ", ") + " px" }
}

// MARK: - R-CNN: proposals scored one by one

private let proposals = [
    Box(30, 62, 128, 212), Box(52, 70, 150, 240), Box(150, 95, 240, 205), Box(172, 112, 262, 220),
    Box(130, 168, 196, 252), Box(186, 88, 236, 128), Box(18, 92, 92, 196), Box(60, 190, 128, 270),
    Box(206, 140, 252, 176), Box(160, 100, 258, 206), Box(55, 72, 125, 222), Box(118, 64, 180, 118),
    Box(144, 196, 210, 262), Box(36, 84, 146, 226),
]

private func rcnnLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let scores = proposals.map { p in truths.map { iou(p, $0) } }
        func best(_ i: Int) -> Int { scores[i].indices.max { scores[i][$0] < scores[i][$1] }! }
        func isObject(_ i: Int) -> Bool { scores[i][best(i)] >= 0.5 }
        let background = 5, current = 10
        func stage(_ cur: Int?, _ labelled: Bool) -> DkStage {
            .boxes(DkBoxes(size: imageSize, grid: 0, rects:
                proposals.indices.filter { $0 != cur }.map { i in rect(proposals[i], !labelled ? .grey : isObject(i) ? .blue : .grey, thin: true) }
                + truthRects(true) + (cur.map { [rect(proposals[$0], .yellow, dashed: true)] } ?? [])))
        }
        let legendAll = [truthLegend, legend(.yellow, "Current", .ring), legend(.blue, "IoU ≥ 0.5", .ring), legend(.grey, "Background", .ring)]
        let noCurrent = legendAll.filter { $0.ink != .yellow }
        let objects = proposals.indices.filter { isObject($0) }.count
        func iouLine(_ i: Int) -> String {
            let t = truths[best(i)], p = proposals[i]
            return "IoU = \(kilo(inter(p, t))) / \(kilo(p.area + t.area - inter(p, t))) = {\(n(scores[i][best(i)]))}"
        }
        let header = "region proposals vs ground truth"
        let frames = [
            DkFrame(
                header: header, stage: stage(nil, false), legend: [truthLegend, legend(.grey, "Proposal", .ring)],
                formula: ["selective search: ~2,000 boxes per image", "shown: \(proposals.count)"],
                headline: "Selective search proposes {\(proposals.count)} candidate boxes here.",
                body: "It groups pixels by colour and texture into blobs, then boxes them. It knows nothing about classes: most boxes miss."
            ),
            DkFrame(
                header: header, stage: stage(background, false), legend: legendAll,
                formula: [iouLine(background) + " → < 0.5, background", "the CNN still has to see it"],
                headline: "Proposal \(background + 1) overlaps truth \(best(background) + 1) at only IoU {\(n(scores[background][best(background)]))}.",
                body: "Below 0.5 it is labelled background. R-CNN trains on these too, so the classifier learns what isn't an object."
            ),
            DkFrame(
                header: header, stage: stage(current, false), legend: legendAll,
                formula: [iouLine(current) + " → ≥ 0.5, label as object \(best(current) + 1)", "then: warp to 227×227 → CNN → SVM"],
                headline: "Proposal \(current + 1) overlaps truth \(best(current) + 1) at IoU {\(n(scores[current][best(current)]))}.",
                body: "R-CNN turns detection back into classification: every one of ~2,000 proposals is cropped, warped and sent through the CNN on its own. That is why it took ~47 s per image."
            ),
            DkFrame(
                header: header, stage: stage(nil, true), legend: noCurrent,
                formula: ["objects: {\(objects)} of \(proposals.count)", "background: \(proposals.count - objects)"],
                headline: "{\(objects)} of \(proposals.count) proposals become object examples.",
                body: "The rest are background. A box regressor then nudges each object proposal toward its truth."
            ),
            DkFrame(
                header: header, stage: stage(nil, true), legend: noCurrent,
                formula: ["2,000 proposals × 1 CNN pass each", "≈ 47 s per image on a 2014 GPU"],
                headline: "One image costs {2,000} CNN passes.",
                body: "Overlapping proposals recompute the same features again and again. Fast R-CNN runs the CNN once and crops features instead."
            ),
        ]
        return stepActions(frames, "Score Next")
    }
}

// MARK: - Fast R-CNN: RoI pooling on the shared map

private func fastRcnnLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let s1 = Snap(truth1), s2 = Snap(truth2)
        func stage(_ s: Snap, _ filled: Bool, _ side: [String]) -> DkStage {
            var rects = [rect(s.box, .green), rect(s.snapped, .violet, bins: 7)]
            if filled { rects.append(DkRect(x1: s.snapped.x1, y1: s.snapped.y1, x2: s.snapped.x1 + s.snapped.w / 7, y2: s.snapped.y1 + s.snapped.h / 7, ink: .violet, fill: true)) }
            return .boxes(DkBoxes(size: imageSize, grid: 20, rects: rects, side: side))
        }
        func cellsText(_ s: Snap) -> String { "\(s.c2 - s.c1)×\(s.r2 - s.r1) cells" }
        func perBin(_ s: Snap) -> String { "≈ \(n(Double(s.c2 - s.c1) / 7))×\(n(Double(s.r2 - s.r1) / 7)) / bin" }
        let side1 = ["feature map", "20×20, stride 16", "", "{v:RoI → 7×7 bins}", "{v:\(cellsText(s1))}", "{v:\(perBin(s1))}"]
        let lg = [legend(.green, "Box in pixels", .ring), legend(.violet, "Snapped RoI", .ring), legend(.violet, "Max-pooled bin")]
        let header = "the proposal, projected onto the shared feature map"
        let frames = [
            DkFrame(
                header: header, stage: .boxes(DkBoxes(size: imageSize, grid: 20, rects: [rect(truth1, .green), rect(truth2, .green)],
                                                      side: ["feature map", "20×20, stride 16", "", "one conv pass", "for the image"])),
                legend: [lg[0]], formula: ["conv stack on the 320×320 image → 20×20 map", "each cell covers 16×16 pixels"],
                headline: "Fast R-CNN runs the conv stack {once} for the whole image.",
                body: "Every proposal then reads its features from this one shared map instead of re-running the CNN."
            ),
            DkFrame(
                header: header, stage: stage(s1, false, side1), legend: Array(lg.prefix(2)),
                formula: ["x: \(n(truth1.x1, 0))/16 = \(n(truth1.x1 / 16)) → {\(s1.c1)} · \(n(truth1.x2, 0))/16 = \(n(truth1.x2 / 16)) → {\(s1.c2)}", "snap error: \(s1.errorText)"],
                headline: "The conv stack runs {once}; each proposal just reads its patch.",
                body: "RoI pooling max-pools any box into 7×7 so one dense head fits all. Rounding to the 16-px grid shifts the box by up to \(n(s1.errors.max()!, 0)) px, which Mask R-CNN later fixes."
            ),
            DkFrame(
                header: header, stage: stage(s1, true, side1), legend: lg,
                formula: ["49 bins × 512 channels = 25,088 numbers", "max over the ~\(n(Double((s1.c2 - s1.c1) * (s1.r2 - s1.r1)) / 49, 1)) cells in each bin"],
                headline: "Each of the {49} bins keeps its maximum.",
                body: "Whatever the box's size, the head always gets 7×7×512, so one set of dense layers classifies every proposal."
            ),
            DkFrame(
                header: header, stage: stage(s2, false, ["feature map", "20×20, stride 16", "", "{v:truth 2}", "{v:\(cellsText(s2))}", "{v:\(perBin(s2))}"]),
                legend: Array(lg.prefix(2)),
                formula: ["x: \(n(truth2.x1, 0))/16 = \(n(truth2.x1 / 16)) → {\(s2.c1)} · \(n(truth2.x2, 0))/16 = \(n(truth2.x2 / 16)) → {\(s2.c2)}", "snap error: \(s2.errorText)"],
                headline: "Truth 2 snaps off by {\(s2.errorText)}.",
                body: "Same map, no new conv pass: about 9 s per image becomes 0.3 s. Proposals still come from slow selective search, which Faster R-CNN replaces."
            ),
        ]
        return stepActions(frames, "Pool Next RoI")
    }
}

// MARK: - Faster R-CNN: anchors

private struct Anchor { let scale: Int; let ratio: String; let box: Box }

private func anchorsAt(_ cx: Double, _ cy: Double) -> [Anchor] {
    [64, 128, 256].flatMap { s in
        [("1:1", 1.0), ("1:2", 2.0), ("2:1", 0.5)].map { name, r in
            let w = Double(s) / r.squareRoot(), h = Double(s) * r.squareRoot()
            return Anchor(scale: s, ratio: name, box: Box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
        }
    }
}

private func fasterRcnnLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["64 px", "128 px", "256 px", "All"], initialTab: 3) { tab, _ in
        let col = 5, row = 8
        let cx = (Double(col) + 0.5) * stride16, cy = (Double(row) + 0.5) * stride16
        let all = anchorsAt(cx, cy)
        let shown = tab == 3 ? all : all.filter { $0.scale == [64, 128, 256][tab] }
        let bestIndex = shown.indices.max { iou(shown[$0].box, truth1) < iou(shown[$1].box, truth1) }!
        let best = shown[bestIndex]
        let bestIou = iou(best.box, truth1)
        var pos = 0, neg = 0
        for r in 0..<20 {
            for c in 0..<20 {
                for a in anchorsAt((Double(c) + 0.5) * stride16, (Double(r) + 0.5) * stride16) {
                    let m = truths.map { iou(a.box, $0) }.max()!
                    if m >= 0.7 { pos += 1 } else if m < 0.3 { neg += 1 }
                }
            }
        }
        let total = 20 * 20 * 9
        func stage(_ highlight: Bool) -> DkStage {
            .boxes(DkBoxes(size: imageSize, grid: 0,
                           rects: truthRects() + shown.indices.filter { !highlight || $0 != bestIndex }.map { rect(shown[$0].box, .violet, thin: true) }
                               + (highlight ? [rect(best.box, .yellow)] : []),
                           dots: [DkP(cx, cy)]))
        }
        let header = "anchors at feature cell (\(col), \(row))"
        let lg = [truthLegend, legend(.violet, "Anchor", .ring), legend(.yellow, "Best match", .ring)]
        let shape = best.ratio == "1:2" ? "tall" : best.ratio == "2:1" ? "wide" : "square"
        let totalText = comma(Double(total))
        let frames = [
            DkFrame(
                header: header, stage: stage(false), legend: Array(lg.prefix(2)),
                formula: ["3 scales × 3 ratios = {9} anchors per cell", "scales 64, 128, 256 px · ratios 1:1, 1:2, 2:1"],
                headline: tab == 3 ? "Faster R-CNN places {9 anchors} at this cell." : "At this cell, the {3 anchors} of \(shown[0].scale) px.",
                body: "Anchors are fixed reference boxes. The region proposal network only has to say which ones hold an object and how to nudge them."
            ),
            DkFrame(
                header: header, stage: stage(true), legend: lg,
                formula: ["best: \(best.scale) px, \(best.ratio) → IoU {\(n(bestIou))} \(bestIou >= 0.7 ? "≥ 0.7 positive" : "< 0.7")", "anchors: 20·20·9 = {\(totalText)} scored by the RPN"],
                headline: tab == 3 ? "The \(shape) anchor fits truth 1 best at IoU {\(n(bestIou))}." : "At \(best.scale) px the best anchor reaches IoU {\(n(bestIou))}.",
                body: tab == 3 ? "The RPN slides over the shared feature map and scores all \(totalText) anchors in one pass, so proposals cost ~10 ms instead of ~2 s."
                    : bestIou >= 0.7 ? "Close enough to count as a positive example for this object."
                    : "Too far off in size or shape: this scale alone would leave truth 1 without a positive anchor."
            ),
            DkFrame(
                header: header, stage: stage(true), legend: lg,
                formula: ["positive (IoU ≥ 0.7): {\(pos)}", "negative (IoU < 0.3): \(comma(Double(neg))) · ignored: \(total - pos - neg)"],
                headline: "Only {\(pos)} of \(totalText) anchors are positive.",
                body: "Training samples 256 anchors per image, half positive where possible, so the RPN isn't drowned in background."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - YOLO: the responsible cell

private func yoloLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Truth 1", "Truth 2"]) { tab, _ in
        let s = 7
        let cell = imageSize / Double(s)
        let t = truths[tab]
        let gx = t.cx / cell, gy = t.cy / cell
        let col = Int(gx.rounded(.down)), row = Int(gy.rounded(.down))
        let cellsOf = truths.map { (Int(($0.cx / cell).rounded(.down)), Int(($0.cy / cell).rounded(.down))) }
        func stage(_ cells: Bool) -> DkStage {
            .boxes(DkBoxes(size: imageSize, grid: s,
                           rects: (cells ? cellsOf.map { c, r in DkRect(x1: Double(c) * cell, y1: Double(r) * cell, x2: Double(c + 1) * cell, y2: Double(r + 1) * cell, ink: .yellow, fill: true) } : [])
                               + truthRects(),
                           dots: truths.map { DkP($0.cx, $0.cy) }))
        }
        let output = s * s * 30
        let header = "\(s)×\(s) grid · the cell holding each centre is responsible"
        let lg = [truthLegend, legend(.yellow, "Responsible cell"), legend(.yellow, "Centre", .dot)]
        let frames = [
            DkFrame(
                header: header, stage: stage(false), legend: [truthLegend, lg[2]],
                formula: ["grid: \(s)×\(s), each cell \(n(cell, 1)) px", "truth \(tab + 1) centre: (\(n(t.cx, 1)), \(n(t.cy, 1)))"],
                headline: "YOLO cuts the image into a {\(s)×\(s)} grid.",
                body: "No proposals and no anchors scored one by one: the whole image goes through the network once."
            ),
            DkFrame(
                header: header, stage: stage(true), legend: lg,
                formula: ["(\(n(t.cx, 1)), \(n(t.cy, 1))) / \(n(cell, 1)) = (\(n(gx)), \(n(gy)))", "→ cell {(\(col), \(row))}"],
                headline: "Truth \(tab + 1)'s centre falls in cell {(\(col), \(row))}.",
                body: "That one cell is responsible for predicting this object; the other \(s * s - 1) cells learn to say 'nothing here' for it."
            ),
            DkFrame(
                header: header, stage: stage(true), legend: lg,
                formula: ["truth \(tab + 1) → cell (\(col), \(row)) · x, y = \(n(gx - Double(col))), \(n(gy - Double(row))) in cell",
                          "w, h = \(n(t.w / imageSize)), \(n(t.h / imageSize)) of image · output \(s)·\(s)·30 = {\(comma(Double(output)))}"],
                headline: "One forward pass predicts {\(comma(Double(output)))} numbers: every box at once.",
                body: "No proposals. Each cell predicts 2 boxes and 20 class scores; only cells \(cellsOf.map { "(\($0.0), \($0.1))" }.joined(separator: " and ")) are trained to find these objects."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - SSD: boxes per feature map

private struct SsdLevel { let grid: Int; let stride: Int; let perCell: Int; let box: Int; var boxes: Int { grid * grid * perCell } }

private let ssdLevels = [
    SsdLevel(grid: 38, stride: 8, perCell: 4, box: 60), SsdLevel(grid: 19, stride: 16, perCell: 6, box: 102),
    SsdLevel(grid: 10, stride: 32, perCell: 6, box: 144), SsdLevel(grid: 5, stride: 64, perCell: 6, box: 186),
    SsdLevel(grid: 3, stride: 100, perCell: 4, box: 228), SsdLevel(grid: 1, stride: 300, perCell: 4, box: 270),
]

private func ssdLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let total = ssdLevels.reduce(0) { $0 + $1.boxes }
        let top = Double(ssdLevels.map(\.boxes).max()!)
        let objects = truths.map { $0.area.squareRoot() * 300 / imageSize }
        let match = objects.map { o in ssdLevels.indices.min { abs(Double(ssdLevels[$0].box) - o) < abs(Double(ssdLevels[$1].box) - o) }! }
        func rows(_ hot: Set<Int>) -> DkStage {
            .rows(DkRows(rows: ssdLevels.enumerated().map { i, l in
                DkRow(title: "\(l.grid)×\(l.grid) · stride \(l.stride)", meta: "box ≈ \(l.box) px",
                      bars: [DkBar(frac: Double(l.boxes) / top, ink: hot.contains(i) ? .yellow : .blue, label: comma(Double(l.boxes)))], hot: hot.contains(i))
            }))
        }
        let totalText = comma(Double(total))
        let header = "default boxes per feature map · \(totalText) total"
        let lg = [legend(.blue, "Default boxes"), legend(.yellow, "Matches our objects")]
        let m = ssdLevels[match[0]]
        let frames = [
            DkFrame(
                header: header, stage: rows([]), legend: Array(lg.prefix(1)),
                formula: ["Σ grid² × boxes per cell = {\(totalText)}", "one pass, six feature maps"],
                headline: "SSD scores {\(totalText)} default boxes in one pass.",
                body: "Each feature map gets its own small conv head that predicts class scores and box offsets for every default box on it."
            ),
            DkFrame(
                header: header, stage: rows(Set(match)), legend: lg,
                formula: ["object sizes: \(objects.map { "\(n($0, 0)) px" }.joined(separator: ", ")) at 300×300", "nearest scale: {\(m.box) px} on the \(m.grid)×\(m.grid) map"],
                headline: Set(match).count == 1 ? "Both objects are matched on the {\(m.grid)×\(m.grid)} map." : "The objects match {\(Set(match).count) different} maps.",
                body: "SSD puts heads on six maps, each tuned to one object size. \(pct(Double(ssdLevels[0].boxes) / Double(total), 0)) of boxes sit on the 38×38 map for small objects."
            ),
            DkFrame(
                header: header, stage: rows(Set(match)), legend: lg,
                formula: ["positives: boxes with IoU ≥ 0.5 to an object", "negatives kept: 3 × positives, the hardest ones"],
                headline: "Hard negative mining keeps background at {3 : 1}.",
                body: "Nearly all \(totalText) boxes are background. Training on all of them would teach SSD to say 'nothing' everywhere."
            ),
            DkFrame(
                header: header, stage: rows([0]), legend: lg,
                formula: ["smallest default box: 60 px of 300", "objects under ~30 px fall between scales"],
                headline: "The {38×38} map handles the smallest objects, and still misses tiny ones.",
                body: "Its features come from early, shallow layers that see little context. This is where SSD trails two-stage detectors; FPN and RetinaNet fix it."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - RetinaNet: focal loss

private let retinaGammas = [0.0, 1.0, 2.0, 5.0]

private func retinaLab() -> DkLab {
    DkLab(control: .tabs, tabs: retinaGammas.map { "γ = \(n($0, 0))" }, initialTab: 2) { tab, _ in
        let g = retinaGammas[tab]
        let groups: [(name: String, count: Double, pt: Double)] = [("\(kilo(120000)) easy background", 120000, 0.99), ("85 hard background", 85, 0.4), ("2 objects", 2, 0.6)]
        let anchors = groups.reduce(0.0) { $0 + $1.count }
        let ce = groups.map { $0.count * -log($0.pt) }
        let fl = groups.map { $0.count * pow(1 - $0.pt, g) * -log($0.pt) }
        let ceSum = ce.reduce(0, +), flSum = fl.reduce(0, +)
        let ceShare = ce.map { $0 / ceSum }, flShare = fl.map { $0 / flSum }
        func ptText(_ v: Double) -> String { var s = n(v, 2); while s.hasSuffix("0") { s.removeLast() }; if s.hasSuffix(".") { s.removeLast() }; return s }
        func rows(_ pair: Bool) -> DkStage {
            .rows(DkRows(rows: groups.enumerated().map { i, gr in
                DkRow(title: gr.name, meta: "p_t = \(ptText(gr.pt))",
                      bars: [DkBar(frac: ceShare[i], ink: .orange, label: pct(ceShare[i]))] + (pair ? [DkBar(frac: flShare[i], ink: .green, label: pct(flShare[i]))] : []),
                      hot: i == 0, pair: pair)
            }))
        }
        let header = "share of the total loss · \(comma(anchors)) anchors"
        let lg = [legend(.orange, "Cross-entropy"), legend(.green, "Focal, γ = \(n(g, 0))")]
        let easyEach = pow(0.01, g) * -log(0.99)
        let supers = Array("⁰¹²³⁴⁵")
        let sup = String(supers[Int(g)])
        let hard = pct(flShare[1] + flShare[2])
        let frames = [
            DkFrame(
                header: header, stage: rows(false), legend: Array(lg.prefix(1)),
                formula: ["CE = −log p_t", "easy: −log 0.99 = 0.0101 each × 120,000"],
                headline: "With cross-entropy, easy background is {\(pct(ceShare[0]))} of the loss.",
                body: "Each confident negative costs almost nothing, but there are 120,000 of them against 2 objects."
            ),
            DkFrame(
                header: header, stage: rows(true), legend: lg,
                formula: ["FL = −(1 − p_t)^γ · log p_t", "easy: (0.01)\(sup) · 0.0101 = {\(small(easyEach))} each"],
                headline: g == 0 ? "At γ = 0 focal loss {is} cross-entropy." : "Easy background falls from {\(pct(ceShare[0], 0))} of the loss to \(pct(flShare[0])).",
                body: g == 0 ? "Nothing is down-weighted: step γ up to see the easy anchors fade."
                    : "120,000 confident negatives used to drown out 2 objects. The (1 − p_t)\(sup) factor shrinks them \(comma(1 / pow(0.01, g)))×, so training focuses on the hard cases."
            ),
            DkFrame(
                header: header, stage: rows(true), legend: lg,
                formula: ["hard background: \(pct(flShare[1])) · objects: \(pct(flShare[2]))", "plus α = 0.25 to balance the classes"],
                headline: g == 0 ? "Hard cases carry only {\(hard)} of the loss at γ = 0." : "The hard cases now carry {\(hard)} of the loss.",
                body: g == 0 ? "The 87 anchors that matter are outvoted by 120,000 that don't. Raise γ to hand the loss back to them."
                    : "That's what let a one-stage detector match two-stage accuracy without sampling: every anchor is used, but easy ones barely count."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - U-Net

private func unetLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        // Valid 3×3 convs lose 4 pixels per level; 2×2 pooling halves; up-convolution doubles.
        var encoder: [Int] = []
        var side = 572
        for _ in 0..<4 { side -= 4; encoder.append(side); side /= 2 }
        let bottom = side - 4
        var decoder: [Int] = []
        var d = bottom
        for _ in 0..<4 { d = d * 2 - 4; decoder.append(d) }
        let lg = [legend(.blue, "Encoder"), legend(.green, "Decoded"), legend(.yellow, "Current"), legend(.yellow, "Skip + crop", .dashedLine)]
        let header = "feature-map side length · 572 px input, valid convs"
        func up(_ i: Int) -> Int { 2 * (i == 0 ? bottom : decoder[i - 1]) }
        func enc(_ i: Int) -> Int { encoder[3 - i] }
        func crop(_ i: Int) -> Int { (enc(i) - up(i)) / 2 }
        let ch = [512, 256, 128, 64]
        let frames = [
            DkFrame(
                header: header, stage: .unet(DkUNet(encoder: encoder, bottom: bottom, decoder: decoder, done: 0, current: nil)), legend: Array(lg.prefix(1)),
                formula: ["each level: two 3×3 valid convs (−4 px), then 2×2 pool (÷2)", "572 → \(encoder.map(String.init).joined(separator: " → ")) → {\(bottom)}"],
                headline: "The encoder shrinks the map {4 times}, down to \(bottom)×\(bottom).",
                body: "Each level doubles the channels and halves the size: it learns what is in the image and forgets exactly where."
            ),
            DkFrame(
                header: header, stage: .unet(DkUNet(encoder: encoder, bottom: bottom, decoder: decoder, done: 1, current: 0)), legend: lg,
                formula: ["up-conv: \(bottom) → \(up(0)) · encoder 4 is \(enc(0)), crop \(crop(0)) px each side",
                          "concat \(up(0))×\(up(0))×(\(ch[0]) + \(ch[0])) → two 3×3 convs → {\(decoder[0])×\(decoder[0])×\(ch[0])}"],
                headline: "The decoder doubles the map back up: {\(decoder[0])}.",
                body: "An up-convolution doubles the size, then the matching encoder map is cropped to fit and stacked alongside."
            ),
            DkFrame(
                header: header, stage: .unet(DkUNet(encoder: encoder, bottom: bottom, decoder: decoder, done: 2, current: 1)), legend: lg,
                formula: ["up-conv: \(decoder[0]) → \(up(1)) · encoder 3 is \(enc(1)), crop \(crop(1)) px each side",
                          "concat \(up(1))×\(up(1))×(\(ch[1]) + \(ch[1])) → two 3×3 convs → {\(decoder[1])×\(decoder[1])×\(ch[1])}"],
                headline: "The skip hands back {detail} the encoder pooled away.",
                body: "Decoder maps are upsampled, then joined with the matching encoder map so boundaries stay sharp. Valid convs shrink the output to \(decoder[3])×\(decoder[3]) for a 572×572 tile."
            ),
            DkFrame(
                header: header, stage: .unet(DkUNet(encoder: encoder, bottom: bottom, decoder: decoder, done: 4, current: nil)), legend: lg.filter { $0.label != "Current" },
                formula: ["output: {\(decoder[3])×\(decoder[3])} × 2 classes", "overlap tiles by \((572 - decoder[3]) / 2) px to cover a whole image"],
                headline: "The output is a {\(decoder[3])×\(decoder[3])} mask for the tile's centre.",
                body: "Every pixel gets a class. U-Net trained on just 30 annotated microscopy images, helped by heavy elastic augmentation."
            ),
        ]
        return stepActions(frames, "Next Step")
    }
}

// MARK: - Mask R-CNN

private func maskRcnnLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let m = 28
        let mask = (0..<(m * m)).map { i -> Bool in
            let dx = (Double(i % m) + 0.5 - 14) / 11, dy = (Double(i / m) + 0.5 - 14) / 13
            return dx * dx + dy * dy <= 1
        }
        let on = mask.filter { $0 }.count
        let snap = Snap(truth2)
        func stage(_ show: Bool, _ side: [String]) -> DkStage {
            .grids(DkGrids(columns: [[DkGrid(title: "", rows: m, cols: m,
                                             cells: mask.map { show && $0 ? DkCell(text: "", tone: .ink, ink: .green) : DkCell(text: "", tone: .empty) }, maxCell: 9)]],
                           weights: [1.7], side: side, sideWeight: 1))
        }
        let shift = "{w:\(snap.errors.map { n($0, 0) }.joined(separator: ", ")) px}"
        let sideFull = ["28×28 mask", "for truth 2", "", "{m:\(on) of \(m * m) on}", "", "RoIPool shift", shift, "", "RoIAlign shift", "{m:0 px}"]
        let lg = [legend(.green, "Mask pixel"), legend(.slate, "Background")]
        let poolLine = "RoIPool: \(n(truth2.x1, 0))/16 = \(n(truth2.x1 / 16)) → \(snap.c1) · {\(n(snap.errors[0], 0)) px} off"
        let frames = [
            DkFrame(
                header: nil, stage: stage(false, ["28×28 mask", "for truth 2", "", "box + class:", "from Faster R-CNN"]), legend: Array(lg.dropFirst()),
                formula: ["heads: class · box · {mask}", "mask head: 4 convs + deconv → 28×28"],
                headline: "Mask R-CNN is Faster R-CNN plus a {mask head}.",
                body: "For each detected box it also predicts which pixels inside belong to the object."
            ),
            DkFrame(
                header: nil, stage: stage(false, ["28×28 mask", "for truth 2", "", "RoIPool shift", shift]), legend: Array(lg.dropFirst()),
                formula: [poolLine, "edges off by \(snap.errorText)"],
                headline: "RoIPool's rounding moves the box up to {\(n(snap.errors.max()!, 0)) px}.",
                body: "Fine for a class label, ruinous for a mask: at 28×28 one feature cell is several mask pixels."
            ),
            DkFrame(
                header: nil, stage: stage(true, sideFull), legend: lg,
                formula: [poolLine, "RoIAlign: sample at \(n(truth2.x1 / 16)) by bilinear interpolation"],
                headline: "A third head adds a {28×28 mask} per box.",
                body: "Masks need pixel alignment, so RoIAlign drops the rounding RoIPool used in Fast R-CNN. That one change lifted mask AP by roughly 3 points."
            ),
            DkFrame(
                header: nil, stage: stage(true, sideFull), legend: lg,
                formula: ["loss: per-pixel sigmoid on the true class's mask", "one mask per class, no competition between them"],
                headline: "Each instance gets its own {binary} mask.",
                body: "The mask is resized to the box and pasted into the image, so two touching people still come out as two objects."
            ),
        ]
        return stepActions(frames, "Next")
    }
}

// MARK: - Semantic vs instance vs panoptic

private let people = [
    ".............",
    ".............",
    ".............",
    "...11..22....",
    "..1111.2222..",
    "..111122222..",
    "..111122222..",
    "..111122222..",
    "..111122222..",
    "...11122222..",
    "....11.2222..",
    ".......22....",
]

private func segmentationLab() -> DkLab {
    DkLab(control: .tabs, tabs: ["Semantic", "Instance", "Panoptic"], initialTab: 1) { tab, _ in
        let rows = people.count, cols = people[0].count
        let flat = Array(people.joined())
        let p1 = flat.filter { $0 == "1" }.count, p2 = flat.filter { $0 == "2" }.count
        // Connected regions of person pixels, 4-neighbour.
        var seen = Array(repeating: false, count: flat.count)
        var regions = 0
        for i in flat.indices where flat[i] != "." && !seen[i] {
            regions += 1
            var stack = [i]
            seen[i] = true
            while let j = stack.popLast() {
                let r = j / cols, c = j % cols
                for (a, b) in [(r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)] where a >= 0 && a < rows && b >= 0 && b < cols {
                    let k = a * cols + b
                    if !seen[k] && flat[k] != "." { seen[k] = true; stack.append(k) }
                }
            }
        }
        func cells(_ mode: Int) -> [DkCell] {
            flat.enumerated().map { i, ch in
                if ch == "." && mode == 3 { return DkCell(text: "", tone: .ink, ink: i / cols < 7 ? .sky : .slate) }
                if ch == "." { return DkCell(text: "", tone: .empty) }
                if mode == 0 { return DkCell(text: "", tone: .ink, ink: .grey) }
                if mode == 1 { return DkCell(text: "", tone: .ink, ink: .blue) }
                return DkCell(text: "", tone: .ink, ink: ch == "1" ? .blue : .orange)
            }
        }
        func g(_ title: String, _ mode: Int) -> DkGrid { DkGrid(title: title, rows: rows, cols: cols, cells: cells(mode), maxCell: 13) }
        let semantic = g("semantic: \"person\"", 1)
        let second = tab == 0 ? semantic : tab == 1 ? g("instance: #1, #2", 2) : g("panoptic: things + stuff", 3)
        let pair = DkStage.grids(DkGrids(columns: [[g("the image", 0)], [second]], weights: [1, 1]))
        let header = "same pixels, two kinds of label"
        let lg: [DkLegend] = tab == 0 ? [legend(.blue, "person"), legend(.slate, "background")]
            : tab == 1 ? [legend(.blue, "person"), legend(.orange, "person #2"), legend(.slate, "background")]
            : [legend(.blue, "person #1"), legend(.orange, "person #2"), legend(.sky, "sky"), legend(.slate, "ground")]
        let frames = [
            DkFrame(
                header: header, stage: pair, legend: lg, formula: ["image: \(cols)×\(rows) pixels", "person pixels: \(p1 + p2)"],
                headline: "Two people stand {touching} in the image.",
                body: "Every segmentation task labels each pixel; they differ in what the label says."
            ),
            DkFrame(
                header: header,
                stage: tab == 0 ? .grids(DkGrids(columns: [[semantic]], weights: [1])) : .grids(DkGrids(columns: [[semantic], [second]], weights: [1, 1])),
                legend: lg,
                formula: tab == 0 ? ["semantic: 1 class, {\(regions) connected region}", "\(p1 + p2) pixels labelled \"person\""]
                    : tab == 1 ? ["semantic: 1 class, {\(regions) connected region}", "instance: #1 = \(p1) px, #2 = \(p2) px"]
                    : ["things: #1 = \(p1) px, #2 = \(p2) px", "stuff: sky, ground · every pixel labelled"],
                headline: tab == 0 ? "Semantic segmentation gives every pixel a {class}."
                    : tab == 1 ? "Semantic labels see {one} blob; instance labels see two people."
                    : "Panoptic labels {every pixel}: things get ids, stuff gets a class.",
                body: tab == 0 ? "Both people become one \"person\" region; nothing says where one ends and the other begins."
                    : tab == 1 ? "Semantic segmentation gives each pixel a class. Instance segmentation also says which object it belongs to, which is what Mask R-CNN adds."
                    : "Countable things (people, cars) get instance ids; amorphous stuff (sky, road) only gets a class. No pixel is left unlabelled."
            ),
            DkFrame(
                header: header, stage: pair, legend: lg, formula: ["count from semantic: \(regions) region", "count from instance: {2} objects"],
                headline: tab == 0 ? "Counting from semantic labels gives {\(regions)}, not 2."
                    : tab == 1 ? "Instance masks count {2} people."
                    : "Panoptic quality scores {things and stuff} together.",
                body: tab == 0 ? "Touching objects merge. Use semantic segmentation when the class matters, not the count: road, sky, tissue."
                    : tab == 1 ? "Use instance segmentation when objects must be told apart: counting cells, tracking people, robot grasping."
                    : "PQ multiplies how well segments match by how many are found, so one metric covers both halves."
            ),
        ]
        return stepActions(frames, "Next")
    }
}
