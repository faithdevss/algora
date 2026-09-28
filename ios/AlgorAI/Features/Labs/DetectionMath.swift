import Foundation

// Port of DetectionMath.kt: the arithmetic behind the C4 labs. IoU, NMS and average precision are
// run for real on small box sets; anchor counts, RoI quantisation and the focal-loss split are
// measured. Boxes are corner-form (x1, y1, x2, y2) in image pixels.

struct BoxF {
    let x1: Double, y1: Double, x2: Double, y2: Double
    init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) { self.x1 = x1; self.y1 = y1; self.x2 = x2; self.y2 = y2 }
    var width: Double { max(0, x2 - x1) }
    var height: Double { max(0, y2 - y1) }
    var area: Double { width * height }
    var centreX: Double { (x1 + x2) / 2 }
    var centreY: Double { (y1 + y2) / 2 }
}

func iou(_ a: BoxF, _ b: BoxF) -> Double {
    let overlap = BoxF(max(a.x1, b.x1), max(a.y1, b.y1), min(a.x2, b.x2), min(a.y2, b.y2)).area
    let union = a.area + b.area - overlap
    return union <= 0 ? 0 : overlap / union
}

/// Greedy NMS; surviving indices in the order kept.
func nms(_ boxes: [BoxF], _ scores: [Double], threshold: Double = 0.5) -> [Int] {
    let order = scores.indices.sortedByDescending { scores[$0] }
    var dead = [Bool](repeating: false, count: boxes.count)
    var kept: [Int] = []
    for i in order where !dead[i] {
        kept.append(i)
        for j in order where j != i && !dead[j] && iou(boxes[i], boxes[j]) > threshold { dead[j] = true }
    }
    return kept
}

// MARK: - Average precision

struct PrPoint { let recall: Double; let precision: Double; let correct: Bool; let score: Double }

struct DetectionScore {
    let curve: [PrPoint]
    /// Area under the precision envelope (VOC2010+/COCO).
    let averagePrecision: Double
    /// VOC2007: the envelope sampled at 11 recall levels.
    let elevenPointAp: Double
    let truePositives: Int
    let falsePositives: Int
    let missed: Int
}

func averagePrecision(_ detections: [(BoxF, Double)], _ groundTruth: [BoxF], iouThreshold: Double = 0.5) -> DetectionScore {
    var claimed = [Bool](repeating: false, count: groundTruth.count)
    var tp = 0, fp = 0
    var curve: [PrPoint] = []
    for (box, score) in detections.sortedByDescending({ $0.1 }) {
        var best = -1
        var bestIou = iouThreshold
        for g in groundTruth.indices {
            let overlap = iou(box, groundTruth[g])
            if !claimed[g] && overlap >= bestIou { bestIou = overlap; best = g }
        }
        let correct = best >= 0
        if correct { claimed[best] = true; tp += 1 } else { fp += 1 }
        curve.append(PrPoint(recall: Double(tp) / Double(groundTruth.count), precision: Double(tp) / Double(tp + fp), correct: correct, score: score))
    }
    var envelope = [Double](repeating: 0, count: curve.count)
    var running = 0.0
    for i in curve.indices.reversed() {
        running = max(running, curve[i].precision)
        envelope[i] = running
    }
    var ap = 0.0, previousRecall = 0.0
    for i in curve.indices {
        ap += (curve[i].recall - previousRecall) * envelope[i]
        previousRecall = curve[i].recall
    }
    let elevenPoint = (0...10).reduce(0.0) { acc, step in
        let r = Double(step) / 10
        let reachable = curve.indices.filter { curve[$0].recall >= r }
        return acc + (reachable.isEmpty ? 0 : reachable.map { envelope[$0] }.max()!)
    } / 11
    return DetectionScore(curve: curve, averagePrecision: ap, elevenPointAp: elevenPoint, truePositives: tp, falsePositives: fp,
                          missed: claimed.filter { !$0 }.count)
}

// MARK: - Focal loss

struct LossSplit {
    let backgroundLoss: Double
    let foregroundLoss: Double
    var total: Double { backgroundLoss + foregroundLoss }
    var backgroundShare: Double { total == 0 ? 0 : backgroundLoss / total }
    var ratio: Double { backgroundLoss / max(foregroundLoss, 1e-300) }
}

func crossEntropySplit(_ backgroundCount: Int, _ backgroundConfidence: Double, _ foregroundCount: Int, _ foregroundConfidence: Double) -> LossSplit {
    LossSplit(backgroundLoss: Double(backgroundCount) * -log(backgroundConfidence), foregroundLoss: Double(foregroundCount) * -log(foregroundConfidence))
}

func focalSplit(_ backgroundCount: Int, _ backgroundConfidence: Double, _ foregroundCount: Int, _ foregroundConfidence: Double, gamma: Double = 2) -> LossSplit {
    LossSplit(backgroundLoss: Double(backgroundCount) * pow(1 - backgroundConfidence, gamma) * -log(backgroundConfidence),
              foregroundLoss: Double(foregroundCount) * pow(1 - foregroundConfidence, gamma) * -log(foregroundConfidence))
}

func focalWeight(_ confidence: Double, gamma: Double = 2) -> Double { pow(1 - confidence, gamma) }

// MARK: - Anchors

struct AnchorLevel {
    let stride: Int, gridSize: Int, perLocation: Int
    var count: Int { gridSize * gridSize * perLocation }
}

/// SSD300's six prediction maps (8,732 boxes).
func ssd300Levels() -> [AnchorLevel] {
    [AnchorLevel(stride: 8, gridSize: 38, perLocation: 4), AnchorLevel(stride: 16, gridSize: 19, perLocation: 6),
     AnchorLevel(stride: 32, gridSize: 10, perLocation: 6), AnchorLevel(stride: 64, gridSize: 5, perLocation: 6),
     AnchorLevel(stride: 100, gridSize: 3, perLocation: 4), AnchorLevel(stride: 300, gridSize: 1, perLocation: 4)]
}

/// RetinaNet's P3–P7 at nine anchors per location.
func retinaNetLevels(_ image: Int = 800) -> [AnchorLevel] {
    [8, 16, 32, 64, 128].map { AnchorLevel(stride: $0, gridSize: (image + $0 - 1) / $0, perLocation: 9) }
}

func rpnAnchorCount(_ featureWidth: Int = 40, _ featureHeight: Int = 60, _ perLocation: Int = 9) -> Int { featureWidth * featureHeight * perLocation }

struct YoloShape {
    let grid: Int, boxesPerCell: Int, classes: Int
    var channels: Int { boxesPerCell * 5 + classes }
    var tensorSize: Int { grid * grid * channels }
    var boxesPredicted: Int { grid * grid * boxesPerCell }
}

// MARK: - RoI pooling vs RoIAlign

struct RoiQuantisation {
    let exactFeatureSide: Double
    let quantisedFeatureSide: Int
    let exactBinSide: Double
    let quantisedBinSide: Int
    let bins: Int
    let stride: Int
    var roiShiftPixels: Double { abs(exactFeatureSide - Double(quantisedFeatureSide)) * Double(stride) }
    var binShiftPixels: Double { abs(exactBinSide - Double(quantisedBinSide)) * Double(bins) * Double(stride) }
    var totalShiftPixels: Double { roiShiftPixels + binShiftPixels }
}

func roiQuantisation(_ boxSidePixels: Double, stride: Int = 16, bins: Int = 7) -> RoiQuantisation {
    let exact = boxSidePixels / Double(stride)
    let quantised = Int(floor(exact))
    let exactBin = Double(quantised) / Double(bins)
    return RoiQuantisation(exactFeatureSide: exact, quantisedFeatureSide: quantised, exactBinSide: exactBin,
                           quantisedBinSide: Int(floor(exactBin)), bins: bins, stride: stride)
}

// MARK: - U-Net geometry

struct UnetStage { let name: String; let size: Int; let channels: Int; var cropPerSide = 0 }

func unetPath(input: Int = 572, baseChannels: Int = 64, depth: Int = 4) -> [UnetStage] {
    var stages: [UnetStage] = []
    var size = input, channels = baseChannels
    var skips: [(Int, Int)] = []
    for level in 0..<depth {
        size -= 4
        stages.append(UnetStage(name: "encode \(level + 1)", size: size, channels: channels))
        skips.append((size, channels))
        size /= 2
        channels *= 2
        stages.append(UnetStage(name: "pool \(level + 1)", size: size, channels: channels / 2))
    }
    size -= 4
    stages.append(UnetStage(name: "bottleneck", size: size, channels: channels))
    for level in stride(from: depth - 1, through: 0, by: -1) {
        size *= 2
        channels /= 2
        let (skipSize, skipChannels) = skips[level]
        stages.append(UnetStage(name: "up \(level + 1) + skip", size: size, channels: channels + skipChannels, cropPerSide: (skipSize - size) / 2))
        size -= 4
        stages.append(UnetStage(name: "decode \(level + 1)", size: size, channels: channels))
    }
    return stages
}

// MARK: - Semantic vs instance segmentation

struct SegmentationCounts { let classes: Int; let semanticRegions: Int; let instances: Int; let pixelsPerClass: [Int: Int] }

private func distinctNonZero(_ grid: [[Int]]) -> [Int] {
    var out: [Int] = []
    for v in grid.flatMap({ $0 }) where v != 0 && !out.contains(v) { out.append(v) }
    return out
}

func segmentationCounts(_ semantic: [[Int]], _ instance: [[Int]]) -> SegmentationCounts {
    let classes = distinctNonZero(semantic)
    var pixels: [Int: Int] = [:]
    for c in classes { pixels[c] = semantic.reduce(0) { $0 + $1.filter { $0 == c }.count } }
    return SegmentationCounts(classes: classes.count, semanticRegions: connectedRegions(semantic), instances: distinctNonZero(instance).count, pixelsPerClass: pixels)
}

/// Four-connected components over non-zero labels.
func connectedRegions(_ map: [[Int]]) -> Int {
    var seen = map.map { [Bool](repeating: false, count: $0.count) }
    var regions = 0
    for r in map.indices {
        for c in map[r].indices where map[r][c] != 0 && !seen[r][c] {
            regions += 1
            var stack = [(r, c)]
            while let (cr, cc) = stack.popLast() {
                if !map.indices.contains(cr) || !map[cr].indices.contains(cc) { continue }
                if seen[cr][cc] || map[cr][cc] != map[r][c] { continue }
                seen[cr][cc] = true
                stack += [(cr + 1, cc), (cr - 1, cc), (cr, cc + 1), (cr, cc - 1)]
            }
        }
    }
    return regions
}

func meanIoU(_ prediction: [[Int]], _ truth: [[Int]]) -> Double {
    let classes = distinctNonZero(prediction + truth)
    if classes.isEmpty { return 1 }
    return classes.map { c -> Double in
        var intersection = 0, union = 0
        for r in prediction.indices {
            for col in prediction[r].indices {
                let p = prediction[r][col] == c, t = truth[r][col] == c
                if p && t { intersection += 1 }
                if p || t { union += 1 }
            }
        }
        return union == 0 ? 1 : Double(intersection) / Double(union)
    }.average
}
