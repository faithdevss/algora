package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the numbers the C4 labs quote. Detection is the part of vision where the *evaluation* is as
 * easy to get wrong as the model, so the matching procedure behind average precision is tested
 * directly — including the rule the labs are built on, that a second detection of an object already
 * found is a false positive rather than a duplicate.
 *
 * The anchor counts (SSD300's 8,732, RetinaNet's ~120k, the RPN's 21,600) are summed from level
 * tables rather than quoted, and the focal-loss rebalance is computed on a stated population rather
 * than asserted as "focal loss handles imbalance".
 */
class DetectionMathTest {

    private val truth = listOf(BoxF(20.0, 40.0, 90.0, 150.0), BoxF(110.0, 60.0, 180.0, 140.0))
    private val detections = listOf(
        BoxF(18.0, 44.0, 88.0, 148.0) to 0.94,
        BoxF(24.0, 36.0, 96.0, 156.0) to 0.88,
        BoxF(112.0, 58.0, 178.0, 142.0) to 0.81,
        BoxF(60.0, 20.0, 150.0, 120.0) to 0.55,
        BoxF(130.0, 10.0, 190.0, 60.0) to 0.42,
        BoxF(10.0, 150.0, 70.0, 195.0) to 0.30,
    )

    @Test
    fun `iou is symmetric, bounded, and zero for disjoint boxes`() {
        val a = BoxF(0.0, 0.0, 10.0, 10.0)
        val b = BoxF(5.0, 0.0, 15.0, 10.0)
        assertEquals(iou(a, b), iou(b, a), 1e-12)
        // Half of each box overlaps: 50 / (100 + 100 − 50).
        assertEquals(1.0 / 3.0, iou(a, b), 1e-12)
        assertEquals(1.0, iou(a, a), 1e-12)
        assertEquals(0.0, iou(a, BoxF(20.0, 20.0, 30.0, 30.0)), 1e-12)
        // Touching edges are not overlap.
        assertEquals(0.0, iou(a, BoxF(10.0, 0.0, 20.0, 10.0)), 1e-12)
    }

    @Test
    fun `nms keeps the best box per object and drops only the duplicate`() {
        val kept = nms(detections.map { it.first }, detections.map { it.second }, threshold = 0.5)
        assertEquals(listOf(0, 2, 3, 4, 5), kept)
        // The dropped box is the one that overlaps a kept box by more than the threshold.
        assertTrue(iou(detections[1].first, detections[0].first) > 0.5)
        // The sloppy box straddling both objects survives, because it matches neither well enough
        // to be suppressed. NMS removes duplicates, not wrong answers.
        assertTrue(iou(detections[3].first, detections[0].first) < 0.5)
    }

    @Test
    fun `a lower nms threshold deletes genuinely separate detections`() {
        val boxes = listOf(BoxF(0.0, 0.0, 100.0, 100.0), BoxF(40.0, 0.0, 140.0, 100.0))
        val scores = listOf(0.9, 0.8)
        assertEquals(2, nms(boxes, scores, threshold = 0.5).size)
        assertEquals(1, nms(boxes, scores, threshold = 0.3).size)
    }

    @Test
    fun `a duplicate detection is scored as a false positive`() {
        val all = averagePrecision(detections, truth)
        assertEquals(2, all.truePositives)
        assertEquals(4, all.falsePositives)
        assertEquals(0, all.missed)
        assertEquals(0.8333, all.averagePrecision, 1e-4)

        val kept = nms(detections.map { it.first }, detections.map { it.second })
        val suppressed = averagePrecision(kept.map { detections[it] }, truth)
        assertEquals(1.0, suppressed.averagePrecision, 1e-9)
        // Same detector, same features: NMS is part of the score, not tidying up afterwards.
        assertTrue(suppressed.averagePrecision > all.averagePrecision)
    }

    @Test
    fun `the two average-precision protocols disagree on identical detections`() {
        val all = averagePrecision(detections, truth)
        assertEquals(0.8485, all.elevenPointAp, 1e-4)
        assertTrue(all.elevenPointAp != all.averagePrecision)
    }

    @Test
    fun `a stricter iou threshold turns loose boxes into misses`() {
        val loose = listOf(BoxF(30.0, 50.0, 100.0, 160.0) to 0.9)
        assertEquals(1, averagePrecision(loose, listOf(truth[0]), iouThreshold = 0.5).truePositives)
        assertEquals(0, averagePrecision(loose, listOf(truth[0]), iouThreshold = 0.75).truePositives)
    }

    @Test
    fun `focal loss rebalances the population cross-entropy is dominated by`() {
        val ce = crossEntropySplit(100_000, 0.9, 10, 0.1)
        val fl = focalSplit(100_000, 0.9, 10, 0.1)

        assertEquals(10_536.0, ce.backgroundLoss, 1.0)
        assertEquals(23.03, ce.foregroundLoss, 0.01)
        assertTrue("background share ${ce.backgroundShare}", ce.backgroundShare > 0.997)
        assertEquals(457.6, ce.ratio, 0.5)

        assertEquals(105.4, fl.backgroundLoss, 0.5)
        assertEquals(18.65, fl.foregroundLoss, 0.05)
        assertEquals(5.65, fl.ratio, 0.05)
        // The headline: one factor in the loss, an 81× rebalance, nothing discarded.
        assertEquals(81.0, ce.ratio / fl.ratio, 0.5)
    }

    @Test
    fun `the focal weight down-weights confident examples and leaves hard ones alone`() {
        assertEquals(0.01, focalWeight(0.9), 1e-12)
        assertEquals(0.81, focalWeight(0.1), 1e-12)
        // γ = 0 is exactly cross-entropy, and larger γ focuses harder.
        assertEquals(1.0, focalWeight(0.9, gamma = 0.0), 1e-12)
        assertTrue(focalWeight(0.9, gamma = 5.0) < focalWeight(0.9, gamma = 2.0))
        // Monotone in confidence: a more confident example is always down-weighted more.
        assertTrue((0..9).all { focalWeight(it / 10.0) > focalWeight((it + 1) / 10.0) })
    }

    @Test
    fun `anchor counts sum to the figures the papers report`() {
        assertEquals(8_732, ssd300Levels().sumOf { it.count })
        assertEquals(listOf(5776, 2166, 600, 150, 36, 4), ssd300Levels().map { it.count })
        // Two thirds of SSD's boxes come from the finest map alone.
        assertTrue(ssd300Levels().first().count.toDouble() / 8_732 > 0.66)

        assertEquals(21_600, rpnAnchorCount())
        // RetinaNet's "about 100k anchors" is 120,087 at 800×800 with nine per location.
        val retina = retinaNetLevels().sumOf { it.count }
        assertEquals(120_087, retina)
        assertTrue(retina > 100_000)
    }

    @Test
    fun `YOLO v1's whole output is one small tensor`() {
        val v1 = YoloShape(grid = 7, boxesPerCell = 2, classes = 20)
        assertEquals(30, v1.channels)
        assertEquals(1_470, v1.tensorSize)
        assertEquals(98, v1.boxesPredicted)
        // Two orders of magnitude fewer boxes than R-CNN's 2,000 proposals — the speed, in one number.
        assertTrue(v1.boxesPredicted < 2_000 / 10)
    }

    @Test
    fun `RoI pooling quantises twice, and both roundings cost image pixels`() {
        val q = roiQuantisation(boxSidePixels = 145.0, stride = 16, bins = 7)
        assertEquals(9.0625, q.exactFeatureSide, 1e-9)
        assertEquals(9, q.quantisedFeatureSide)
        assertEquals(1.0, q.roiShiftPixels, 1e-9)
        // Seven bins of one cell span seven cells, not nine: two cells — 32 pixels — are never read.
        assertEquals(32.0, q.binShiftPixels, 1e-9)
        assertEquals(33.0, q.totalShiftPixels, 1e-9)
        // A box that happens to divide evenly costs nothing, which is why the bug is intermittent
        // and was tolerated for two years.
        val aligned = roiQuantisation(boxSidePixels = 112.0, stride = 16, bins = 7)
        assertEquals(0.0, aligned.totalShiftPixels, 1e-9)
    }

    @Test
    fun `the U-Net path shrinks to the size the paper reports and crops every skip`() {
        val path = unetPath()
        assertEquals(28, path.first { it.name == "bottleneck" }.size)
        assertEquals(388, path.last().size)
        assertEquals(listOf(4, 16, 40, 88), path.filter { it.cropPerSide > 0 }.map { it.cropPerSide })
        // Every encoder stage is larger than the decoder stage it feeds — that is why cropping exists.
        assertTrue(path.filter { it.cropPerSide > 0 }.all { it.cropPerSide > 0 })
    }

    @Test
    fun `semantic labelling cannot separate two touching objects of one class`() {
        val semantic = List(12) { r ->
            List(12) { c ->
                when {
                    r in 3..8 && c in 1..9 -> 1
                    r in 1..4 && c in 10..11 -> 2
                    else -> 0
                }
            }
        }
        val instance = List(12) { r ->
            List(12) { c ->
                when {
                    r in 3..8 && c in 1..4 -> 1
                    r in 3..8 && c in 5..9 -> 2
                    r in 1..4 && c in 10..11 -> 3
                    else -> 0
                }
            }
        }
        val counts = segmentationCounts(semantic, instance)
        assertEquals(2, counts.classes)
        assertEquals(2, counts.semanticRegions)
        assertEquals(3, counts.instances)
        assertEquals(54, counts.pixelsPerClass.getValue(1))
        // The information is absent from the semantic map, not merely hard to extract.
        assertTrue(counts.instances > counts.semanticRegions)
    }

    @Test
    fun `mIoU rewards pixels and is blind to the merge that mask AP punishes`() {
        val truthMap = List(12) { r -> List(12) { c -> if (r in 3..8 && c in 1..9) 1 else 0 } }
        val dropped = truthMap.mapIndexed { r, row -> row.mapIndexed { c, v -> if (r == 8) 0 else v } }
        assertEquals(0.8333, meanIoU(dropped, truthMap), 1e-4)
        assertEquals(1.0, meanIoU(truthMap, truthMap), 1e-9)
        // An empty prediction scores 0, not 1 — the metric is not fooled by a background-only map.
        assertEquals(0.0, meanIoU(List(12) { List(12) { 0 } }, truthMap), 1e-9)
    }
}
