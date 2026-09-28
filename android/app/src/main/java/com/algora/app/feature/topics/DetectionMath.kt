package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ── Object detection and segmentation ────────────────────────────────────────
// The arithmetic behind the nine C4 labs. Detection is the part of vision where the *evaluation* is
// as interesting as the model, so this file computes both sides: IoU and non-maximum suppression
// and average precision are run for real on small box sets, and the architectural claims — how many
// anchors a detector scores, what RoI quantisation costs in pixels, how much of the loss the
// background takes — are measured rather than quoted.
//
// Boxes are corner-form (x1, y1, x2, y2) in image pixels throughout, which is what every framework's
// NMS and IoU take. Centre-form appears only where a paper's parameterisation needs it.

internal class BoxF(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    val width: Double get() = max(0.0, x2 - x1)
    val height: Double get() = max(0.0, y2 - y1)
    val area: Double get() = width * height
    val centreX: Double get() = (x1 + x2) / 2
    val centreY: Double get() = (y1 + y2) / 2
}

internal fun iou(a: BoxF, b: BoxF): Double {
    val overlap = BoxF(max(a.x1, b.x1), max(a.y1, b.y1), min(a.x2, b.x2), min(a.y2, b.y2)).area
    val union = a.area + b.area - overlap
    return if (union <= 0.0) 0.0 else overlap / union
}

/**
 * Greedy non-maximum suppression: keep the highest-scoring box, drop everything overlapping it by
 * more than `threshold`, repeat. Returns the surviving indices in the order they were kept.
 *
 * The threshold is a real trade and not a detail — too high and one object keeps several boxes, too
 * low and a genuinely occluded second object is deleted by the first. It is also the last piece of
 * a detector that is not learned, which is why DETR's headline claim was removing it.
 */
internal fun nms(boxes: List<BoxF>, scores: List<Double>, threshold: Double = 0.5): List<Int> {
    require(boxes.size == scores.size) { "nms got ${boxes.size} boxes and ${scores.size} scores" }
    val order = scores.indices.sortedByDescending { scores[it] }
    val dead = BooleanArray(boxes.size)
    val kept = mutableListOf<Int>()
    order.forEach { i ->
        if (dead[i]) return@forEach
        kept += i
        order.forEach { j -> if (j != i && !dead[j] && iou(boxes[i], boxes[j]) > threshold) dead[j] = true }
    }
    return kept
}

// ── Average precision ────────────────────────────────────────────────────────

internal class PrPoint(val recall: Double, val precision: Double, val correct: Boolean, val score: Double)

internal class DetectionScore(
    val curve: List<PrPoint>,
    /** Area under the precision envelope — the VOC2010+/COCO definition. */
    val averagePrecision: Double,
    /** The older VOC2007 definition: the envelope sampled at 11 recall levels. */
    val elevenPointAp: Double,
    val truePositives: Int,
    val falsePositives: Int,
    val missed: Int,
)

/**
 * Runs the real matching procedure: detections in descending score order, each matched to the
 * highest-IoU ground-truth box it clears the threshold on *and that is still unclaimed*. A second
 * detection of an object already found is a false positive, not a duplicate — which is the rule that
 * makes NMS part of the score rather than post-processing.
 */
internal fun averagePrecision(
    detections: List<Pair<BoxF, Double>>,
    groundTruth: List<BoxF>,
    iouThreshold: Double = 0.5,
): DetectionScore {
    val claimed = BooleanArray(groundTruth.size)
    var tp = 0
    var fp = 0
    val curve = mutableListOf<PrPoint>()

    detections.sortedByDescending { it.second }.forEach { (box, score) ->
        var best = -1
        var bestIou = iouThreshold
        groundTruth.indices.forEach { g ->
            val overlap = iou(box, groundTruth[g])
            if (!claimed[g] && overlap >= bestIou) {
                bestIou = overlap
                best = g
            }
        }
        val correct = best >= 0
        if (correct) {
            claimed[best] = true
            tp++
        } else {
            fp++
        }
        curve += PrPoint(
            recall = tp.toDouble() / groundTruth.size,
            precision = tp.toDouble() / (tp + fp),
            correct = correct,
            score = score,
        )
    }

    // Precision envelope: at each recall, the best precision achievable at that recall or beyond.
    val envelope = DoubleArray(curve.size)
    var running = 0.0
    for (i in curve.indices.reversed()) {
        running = max(running, curve[i].precision)
        envelope[i] = running
    }
    var ap = 0.0
    var previousRecall = 0.0
    curve.indices.forEach { i ->
        ap += (curve[i].recall - previousRecall) * envelope[i]
        previousRecall = curve[i].recall
    }
    // VOC2007's definition: the envelope sampled at eleven fixed recall levels rather than
    // integrated. It reports a different number on the same detections, which is why comparing an
    // mAP across papers without checking the protocol is meaningless.
    val elevenPoint = (0..10).sumOf { step ->
        val r = step / 10.0
        val reachable = curve.indices.filter { curve[it].recall >= r }
        if (reachable.isEmpty()) 0.0 else reachable.maxOf { envelope[it] }
    } / 11.0

    return DetectionScore(
        curve = curve,
        averagePrecision = ap,
        elevenPointAp = elevenPoint,
        truePositives = tp,
        falsePositives = fp,
        missed = claimed.count { !it },
    )
}

// ── Focal loss and the class-imbalance problem it exists for ─────────────────

internal class LossSplit(
    val backgroundLoss: Double,
    val foregroundLoss: Double,
) {
    val total: Double get() = backgroundLoss + foregroundLoss
    val backgroundShare: Double get() = if (total == 0.0) 0.0 else backgroundLoss / total
    val ratio: Double get() = backgroundLoss / max(foregroundLoss, 1e-300)
}

/** −log(p_t) summed over each population. */
internal fun crossEntropySplit(
    backgroundCount: Int,
    backgroundConfidence: Double,
    foregroundCount: Int,
    foregroundConfidence: Double,
): LossSplit = LossSplit(
    backgroundLoss = backgroundCount * -ln(backgroundConfidence),
    foregroundLoss = foregroundCount * -ln(foregroundConfidence),
)

/** −(1 − p_t)^γ · log(p_t), the α term left out so the down-weighting is visible on its own. */
internal fun focalSplit(
    backgroundCount: Int,
    backgroundConfidence: Double,
    foregroundCount: Int,
    foregroundConfidence: Double,
    gamma: Double = 2.0,
): LossSplit = LossSplit(
    backgroundLoss = backgroundCount * (1 - backgroundConfidence).pow(gamma) * -ln(backgroundConfidence),
    foregroundLoss = foregroundCount * (1 - foregroundConfidence).pow(gamma) * -ln(foregroundConfidence),
)

/** How much one example's loss is scaled by the focal term alone. */
internal fun focalWeight(confidence: Double, gamma: Double = 2.0): Double = (1 - confidence).pow(gamma)

// ── Anchors: how many boxes a detector actually scores ───────────────────────

internal class AnchorLevel(val stride: Int, val gridSize: Int, val perLocation: Int) {
    val count: Int get() = gridSize * gridSize * perLocation
}

/** SSD300's six prediction maps. The total is the 8,732 the paper reports. */
internal fun ssd300Levels(): List<AnchorLevel> = listOf(
    AnchorLevel(8, 38, 4),
    AnchorLevel(16, 19, 6),
    AnchorLevel(32, 10, 6),
    AnchorLevel(64, 5, 6),
    AnchorLevel(100, 3, 4),
    AnchorLevel(300, 1, 4),
)

/** RetinaNet's pyramid P3–P7 at nine anchors per location — three scales × three aspect ratios. */
internal fun retinaNetLevels(image: Int = 800): List<AnchorLevel> =
    listOf(8, 16, 32, 64, 128).map { stride -> AnchorLevel(stride, (image + stride - 1) / stride, 9) }

/** Faster R-CNN's RPN: nine anchors per position of one stride-16 feature map. */
internal fun rpnAnchorCount(featureWidth: Int = 40, featureHeight: Int = 60, perLocation: Int = 9): Int =
    featureWidth * featureHeight * perLocation

/** YOLO's output is one tensor: S×S×(B·5 + C). v1 is 7×7×30, and predicts only S²·B boxes. */
internal class YoloShape(val grid: Int, val boxesPerCell: Int, val classes: Int) {
    val channels: Int get() = boxesPerCell * 5 + classes
    val tensorSize: Int get() = grid * grid * channels
    val boxesPredicted: Int get() = grid * grid * boxesPerCell
}

// ── RoI pooling, and the misalignment RoIAlign removes ───────────────────────

internal class RoiQuantisation(
    val exactFeatureSide: Double,
    val quantisedFeatureSide: Int,
    val exactBinSide: Double,
    val quantisedBinSide: Int,
    val bins: Int,
    val stride: Int,
) {
    /** Displacement in *image* pixels from snapping the RoI to the feature grid. */
    val roiShiftPixels: Double get() = abs(exactFeatureSide - quantisedFeatureSide) * stride

    /**
     * Image pixels of the RoI that the bin grid never reaches. Seven bins of the rounded-down size
     * span less than the RoI does, and the shortfall lands entirely on its far edge.
     */
    val binShiftPixels: Double get() = abs(exactBinSide - quantisedBinSide) * bins * stride

    val totalShiftPixels: Double get() = roiShiftPixels + binShiftPixels
}

/**
 * RoI pooling quantises twice — once snapping the box to the feature grid, once dividing it into
 * bins — and both roundings are in feature-map units, so each one costs `stride` image pixels. At
 * stride 16 that is a misalignment of tens of pixels on a small object, which does not matter much
 * for a class label and matters a great deal for a pixel-accurate mask. RoIAlign removes both by
 * sampling with bilinear interpolation and never rounding.
 */
internal fun roiQuantisation(boxSidePixels: Double, stride: Int = 16, bins: Int = 7): RoiQuantisation {
    val exact = boxSidePixels / stride
    val quantised = floor(exact).toInt()
    val exactBin = quantised.toDouble() / bins
    return RoiQuantisation(
        exactFeatureSide = exact,
        quantisedFeatureSide = quantised,
        exactBinSide = exactBin,
        quantisedBinSide = floor(exactBin).toInt(),
        bins = bins,
        stride = stride,
    )
}

// ── U-Net geometry ───────────────────────────────────────────────────────────

internal class UnetStage(
    val name: String,
    val size: Int,
    val channels: Int,
    /** For decoder stages: how many pixels are cropped off each side of the skip connection. */
    val cropPerSide: Int = 0,
)

/**
 * The original U-Net: unpadded 3×3 convolutions throughout, so every stage shrinks and the encoder
 * feature map is *larger* than the decoder map it is concatenated with. The paper crops the skip
 * connection to fit, which is why 572×572 in gives 388×388 out — the network deliberately predicts
 * a smaller region than it reads, and tiles a large image with overlapping windows.
 */
internal fun unetPath(input: Int = 572, baseChannels: Int = 64, depth: Int = 4): List<UnetStage> {
    val stages = mutableListOf<UnetStage>()
    var size = input
    var channels = baseChannels
    val skips = mutableListOf<Pair<Int, Int>>()

    repeat(depth) { level ->
        size -= 4 // two unpadded 3×3 convolutions
        stages += UnetStage("encode ${level + 1}", size, channels)
        skips += size to channels
        size /= 2
        channels *= 2
        stages += UnetStage("pool ${level + 1}", size, channels / 2)
    }

    size -= 4
    stages += UnetStage("bottleneck", size, channels)

    for (level in depth - 1 downTo 0) {
        size *= 2
        channels /= 2
        val (skipSize, skipChannels) = skips[level]
        val crop = (skipSize - size) / 2
        stages += UnetStage("up ${level + 1} + skip", size, channels + skipChannels, cropPerSide = crop)
        size -= 4
        stages += UnetStage("decode ${level + 1}", size, channels)
    }
    return stages
}

// ── Semantic vs instance segmentation ────────────────────────────────────────

internal class SegmentationCounts(
    /** Distinct semantic classes present, background excluded. */
    val classes: Int,
    /** Connected foreground regions a semantic map cannot separate. */
    val semanticRegions: Int,
    /** Objects an instance map does separate. */
    val instances: Int,
    val pixelsPerClass: Map<Int, Int>,
)

/**
 * Counts what each task can and cannot report on the same picture. `semantic` holds a class id per
 * pixel and `instance` an object id per pixel; two touching objects of the same class are one
 * region in the first and two in the second, which is the entire distinction.
 */
internal fun segmentationCounts(semantic: List<List<Int>>, instance: List<List<Int>>): SegmentationCounts {
    val classes = semantic.flatten().filter { it != 0 }.distinct()
    val instances = instance.flatten().filter { it != 0 }.distinct()
    val pixels = classes.associateWith { c -> semantic.sumOf { row -> row.count { it == c } } }
    return SegmentationCounts(
        classes = classes.size,
        semanticRegions = connectedRegions(semantic),
        instances = instances.size,
        pixelsPerClass = pixels,
    )
}

/** Four-connected components over non-zero labels, treating equal labels as connectable. */
internal fun connectedRegions(map: List<List<Int>>): Int {
    val seen = map.map { row -> BooleanArray(row.size) }
    var regions = 0
    map.indices.forEach { r ->
        map[r].indices.forEach { c ->
            if (map[r][c] != 0 && !seen[r][c]) {
                regions++
                val stack = ArrayDeque(listOf(r to c))
                while (stack.isNotEmpty()) {
                    val (cr, cc) = stack.removeLast()
                    if (cr !in map.indices || cc !in map[cr].indices) continue
                    if (seen[cr][cc] || map[cr][cc] != map[r][c]) continue
                    seen[cr][cc] = true
                    stack.addAll(listOf(cr + 1 to cc, cr - 1 to cc, cr to cc + 1, cr to cc - 1))
                }
            }
        }
    }
    return regions
}

/** Per-class IoU averaged over the classes present — the semantic-segmentation metric. */
internal fun meanIoU(prediction: List<List<Int>>, truth: List<List<Int>>): Double {
    val classes = (prediction.flatten() + truth.flatten()).filter { it != 0 }.distinct()
    if (classes.isEmpty()) return 1.0
    return classes.map { c ->
        var intersection = 0
        var union = 0
        prediction.indices.forEach { r ->
            prediction[r].indices.forEach { col ->
                val p = prediction[r][col] == c
                val t = truth[r][col] == c
                if (p && t) intersection++
                if (p || t) union++
            }
        }
        if (union == 0) 1.0 else intersection.toDouble() / union
    }.average()
}
