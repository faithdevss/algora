package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── Detection and segmentation storyboard frames ─────────────────────────────
// R-CNN, Fast and Faster R-CNN, YOLO, SSD, RetinaNet, U-Net, Mask R-CNN and the segmentation types,
// drawn by DeepStoryLabs.kt. One shared 320×320 scene with two ground-truth boxes runs through the
// detectors, so the same objects are proposed, snapped to the stride-16 grid, anchored and assigned to
// YOLO cells; every IoU, snap error and count is computed. The iOS port is DetectStoryFrames.swift.

internal val detectStoryTopicIds = setOf(
    "rcnn", "fast_rcnn", "faster_rcnn", "yolo", "ssd", "retinanet", "unet", "mask_rcnn", "segmentation_types",
)

internal fun detectLab(topicId: String): DkLab? = when (topicId) {
    "rcnn" -> rcnnLab()
    "fast_rcnn" -> fastRcnnLab()
    "faster_rcnn" -> fasterRcnnLab()
    "yolo" -> yoloLab()
    "ssd" -> ssdLab()
    "retinanet" -> retinaLab()
    "unet" -> unetLab()
    "mask_rcnn" -> maskRcnnLab()
    "segmentation_types" -> segmentationLab()
    else -> null
}

// ── Formatting ──

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

private fun comma(v: Double): String = abs(v).toLong().toString().reversed().chunked(3).joinToString(",").reversed()

private fun kilo(v: Double): String = if (v >= 1e4) n(v / 1e3, 1) + "k" else comma(v)

/** 0.0101 · 0.000001 · 1.0e−12: six decimals while they show a digit, scientific below. */
private fun small(v: Double): String {
    if (v >= 1e-6) return n(v, if (v >= 1e-3) 4 else 6)
    var e = floor(kotlin.math.log10(v)).toInt()
    var m = floor(v / 10.0.pow(e) * 10 + 0.5) / 10
    if (m >= 10) { m /= 10; e += 1 }
    return n(m, 1) + "e−" + abs(e)
}

private fun pct(share: Double, d: Int = 1) = n(share * 100, d) + "%"

private fun legend(ink: DkInk, label: String, style: SwatchStyle = SwatchStyle.Fill) = DkLegend(ink, style, label)

private fun stepActions(frames: List<DkFrame>, action: (Int) -> String) =
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, if (i == frames.lastIndex) "Start Over" else action(i)) }

// ── The shared scene ──

private class Box(val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    val w get() = x2 - x1
    val h get() = y2 - y1
    val area get() = w * h
    val cx get() = (x1 + x2) / 2
    val cy get() = (y1 + y2) / 2
}

private const val ImageSize = 320.0
private const val Stride = 16

private val truth1 = Box(40.0, 76.0, 140.0, 231.0)
private val truth2 = Box(165.0, 101.0, 249.0, 213.0)
private val truths = listOf(truth1, truth2)

private fun inter(a: Box, b: Box) = max(0.0, min(a.x2, b.x2) - max(a.x1, b.x1)) * max(0.0, min(a.y2, b.y2) - max(a.y1, b.y1))

private fun iou(a: Box, b: Box): Double {
    val i = inter(a, b)
    return i / (a.area + b.area - i)
}

private fun rect(b: Box, ink: DkInk, dashed: Boolean = false, fill: Boolean = false, thin: Boolean = false, label: String? = null, bins: Int = 0) =
    DkRect(b.x1, b.y1, b.x2, b.y2, ink, dashed, fill, thin, label, bins)

private fun truthRects(labels: Boolean = false) = truths.mapIndexed { i, t -> rect(t, DkInk.Green, label = if (labels) "truth ${i + 1}" else null) }

private val truthLegend = legend(DkInk.Green, "Ground truth", SwatchStyle.Ring)

/** RoIPool's snap to the stride-16 grid: corners floored and ceiled to whole feature cells. */
private class Snap(val box: Box) {
    val c1 = floor(box.x1 / Stride).toInt()
    val r1 = floor(box.y1 / Stride).toInt()
    val c2 = ceil(box.x2 / Stride).toInt()
    val r2 = ceil(box.y2 / Stride).toInt()
    val snapped = Box(c1 * 16.0, r1 * 16.0, c2 * 16.0, r2 * 16.0)
    val errors = listOf(box.x1 - snapped.x1, box.y1 - snapped.y1, snapped.x2 - box.x2, snapped.y2 - box.y2)
    val errorText = errors.joinToString(", ") { n(it, 0) } + " px"
}

// ── R-CNN: proposals scored one by one ──

private val proposals = listOf(
    Box(30.0, 62.0, 128.0, 212.0), Box(52.0, 70.0, 150.0, 240.0), Box(150.0, 95.0, 240.0, 205.0), Box(172.0, 112.0, 262.0, 220.0),
    Box(130.0, 168.0, 196.0, 252.0), Box(186.0, 88.0, 236.0, 128.0), Box(18.0, 92.0, 92.0, 196.0), Box(60.0, 190.0, 128.0, 270.0),
    Box(206.0, 140.0, 252.0, 176.0), Box(160.0, 100.0, 258.0, 206.0), Box(55.0, 72.0, 125.0, 222.0), Box(118.0, 64.0, 180.0, 118.0),
    Box(144.0, 196.0, 210.0, 262.0), Box(36.0, 84.0, 146.0, 226.0),
)

private fun rcnnLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val scores = proposals.map { p -> truths.map { iou(p, it) } }
    fun best(i: Int) = scores[i].indices.maxBy { scores[i][it] }
    fun isObject(i: Int) = scores[i][best(i)] >= 0.5
    val background = 5
    val current = 10
    fun stage(cur: Int?, labelled: Boolean) = DkBoxes(
        ImageSize, 0,
        proposals.indices.filter { it != cur }.map { i ->
            rect(proposals[i], if (!labelled) DkInk.Grey else if (isObject(i)) DkInk.Blue else DkInk.Grey, thin = true)
        } + truthRects(labels = true) + listOfNotNull(cur?.let { rect(proposals[it], DkInk.Yellow, dashed = true) }),
    )
    val legendAll = listOf(truthLegend, legend(DkInk.Yellow, "Current", SwatchStyle.Ring), legend(DkInk.Blue, "IoU ≥ 0.5", SwatchStyle.Ring), legend(DkInk.Grey, "Background", SwatchStyle.Ring))
    val objects = proposals.indices.count { isObject(it) }
    fun iouLine(i: Int): String {
        val t = truths[best(i)]
        val p = proposals[i]
        return "IoU = ${kilo(inter(p, t))} / ${kilo(p.area + t.area - inter(p, t))} = {${n(scores[i][best(i)])}}"
    }
    val frames = listOf(
        DkFrame(
            "region proposals vs ground truth", stage(null, false), listOf(truthLegend, legend(DkInk.Grey, "Proposal", SwatchStyle.Ring)),
            listOf("selective search: ~2,000 boxes per image", "shown: ${proposals.size}"),
            "Selective search proposes {${proposals.size}} candidate boxes here.",
            "It groups pixels by colour and texture into blobs, then boxes them. It knows nothing about classes: most boxes miss.",
        ),
        DkFrame(
            "region proposals vs ground truth", stage(background, false), legendAll,
            listOf(iouLine(background) + " → < 0.5, background", "the CNN still has to see it"),
            "Proposal ${background + 1} overlaps truth ${best(background) + 1} at only IoU {${n(scores[background][best(background)])}}.",
            "Below 0.5 it is labelled background. R-CNN trains on these too, so the classifier learns what isn't an object.",
        ),
        DkFrame(
            "region proposals vs ground truth", stage(current, false), legendAll,
            listOf(iouLine(current) + " → ≥ 0.5, label as object ${best(current) + 1}", "then: warp to 227×227 → CNN → SVM"),
            "Proposal ${current + 1} overlaps truth ${best(current) + 1} at IoU {${n(scores[current][best(current)])}}.",
            "R-CNN turns detection back into classification: every one of ~2,000 proposals is cropped, warped and sent through the CNN on its own. That is why it took ~47 s per image.",
        ),
        DkFrame(
            "region proposals vs ground truth", stage(null, true), legendAll.filter { it.ink != DkInk.Yellow },
            listOf("objects: {$objects} of ${proposals.size}", "background: ${proposals.size - objects}"),
            "{$objects} of ${proposals.size} proposals become object examples.",
            "The rest are background. A box regressor then nudges each object proposal toward its truth.",
        ),
        DkFrame(
            "region proposals vs ground truth", stage(null, true), legendAll.filter { it.ink != DkInk.Yellow },
            listOf("2,000 proposals × 1 CNN pass each", "≈ 47 s per image on a 2014 GPU"),
            "One image costs {2,000} CNN passes.",
            "Overlapping proposals recompute the same features again and again. Fast R-CNN runs the CNN once and crops features instead.",
        ),
    )
    stepActions(frames) { "Score Next" }
}

// ── Fast R-CNN: RoI pooling on the shared map ──

private fun fastRcnnLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val s1 = Snap(truth1)
    val s2 = Snap(truth2)
    val cells = 20
    fun stage(s: Snap, bins: Boolean, filled: Boolean, side: List<String>) = DkBoxes(
        ImageSize, cells,
        listOf(rect(s.box, DkInk.Green), rect(s.snapped, DkInk.Violet, bins = if (bins) 7 else 0)) +
            if (filled) listOf(DkRect(s.snapped.x1, s.snapped.y1, s.snapped.x1 + s.snapped.w / 7, s.snapped.y1 + s.snapped.h / 7, DkInk.Violet, fill = true)) else emptyList(),
        side = side,
    )
    fun cellsText(s: Snap) = "${s.c2 - s.c1}×${s.r2 - s.r1} cells"
    fun perBin(s: Snap) = "≈ ${n((s.c2 - s.c1) / 7.0)}×${n((s.r2 - s.r1) / 7.0)} / bin"
    val side1 = listOf("feature map", "20×20, stride 16", "", "{v:RoI → 7×7 bins}", "{v:${cellsText(s1)}}", "{v:${perBin(s1)}}")
    val legend = listOf(legend(DkInk.Green, "Box in pixels", SwatchStyle.Ring), legend(DkInk.Violet, "Snapped RoI", SwatchStyle.Ring), legend(DkInk.Violet, "Max-pooled bin"))
    val header = "the proposal, projected onto the shared feature map"
    val frames = listOf(
        DkFrame(
            header, DkBoxes(ImageSize, cells, listOf(rect(truth1, DkInk.Green), rect(truth2, DkInk.Green)), side = listOf("feature map", "20×20, stride 16", "", "one conv pass", "for the image")),
            listOf(legend[0]), listOf("conv stack on the 320×320 image → 20×20 map", "each cell covers 16×16 pixels"),
            "Fast R-CNN runs the conv stack {once} for the whole image.",
            "Every proposal then reads its features from this one shared map instead of re-running the CNN.",
        ),
        DkFrame(
            header, stage(s1, true, false, side1), legend.take(2),
            listOf("x: ${n(truth1.x1, 0)}/16 = ${n(truth1.x1 / 16)} → {${s1.c1}} · ${n(truth1.x2, 0)}/16 = ${n(truth1.x2 / 16)} → {${s1.c2}}", "snap error: ${s1.errorText}"),
            "The conv stack runs {once}; each proposal just reads its patch.",
            "RoI pooling max-pools any box into 7×7 so one dense head fits all. Rounding to the 16-px grid shifts the box by up to ${n(s1.errors.max(), 0)} px, which Mask R-CNN later fixes.",
        ),
        DkFrame(
            header, stage(s1, true, true, side1), legend,
            listOf("49 bins × 512 channels = 25,088 numbers", "max over the ~${n((s1.c2 - s1.c1) * (s1.r2 - s1.r1) / 49.0, 1)} cells in each bin"),
            "Each of the {49} bins keeps its maximum.",
            "Whatever the box's size, the head always gets 7×7×512, so one set of dense layers classifies every proposal.",
        ),
        DkFrame(
            header, stage(s2, true, false, listOf("feature map", "20×20, stride 16", "", "{v:truth 2}", "{v:${cellsText(s2)}}", "{v:${perBin(s2)}}")), legend.take(2),
            listOf("x: ${n(truth2.x1, 0)}/16 = ${n(truth2.x1 / 16)} → {${s2.c1}} · ${n(truth2.x2, 0)}/16 = ${n(truth2.x2 / 16)} → {${s2.c2}}", "snap error: ${s2.errorText}"),
            "Truth 2 snaps off by {${s2.errorText}}.",
            "Same map, no new conv pass: about 9 s per image becomes 0.3 s. Proposals still come from slow selective search, which Faster R-CNN replaces.",
        ),
    )
    stepActions(frames) { "Pool Next RoI" }
}

// ── Faster R-CNN: anchors ──

private class Anchor(val scale: Int, val ratio: String, val box: Box)

private fun anchorsAt(cx: Double, cy: Double): List<Anchor> = listOf(64, 128, 256).flatMap { s ->
    listOf("1:1" to 1.0, "1:2" to 2.0, "2:1" to 0.5).map { (name, r) ->
        val w = s / sqrt(r)
        val h = s * sqrt(r)
        Anchor(s, name, Box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2))
    }
}

private fun fasterRcnnLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("64 px", "128 px", "256 px", "All"), initialTab = 3) { tab, _ ->
    val col = 5
    val row = 8
    val cx = (col + 0.5) * Stride
    val cy = (row + 0.5) * Stride
    val all = anchorsAt(cx, cy)
    val shown = if (tab == 3) all else all.filter { it.scale == listOf(64, 128, 256)[tab] }
    val best = shown.maxBy { iou(it.box, truth1) }
    val bestIou = iou(best.box, truth1)
    // Label every anchor in the 20×20 map against both truths: positive ≥ 0.7, negative < 0.3.
    var pos = 0
    var neg = 0
    for (r in 0 until 20) for (c in 0 until 20) anchorsAt((c + 0.5) * Stride, (r + 0.5) * Stride).forEach { a ->
        val m = truths.maxOf { iou(a.box, it) }
        if (m >= 0.7) pos++ else if (m < 0.3) neg++
    }
    val total = 20 * 20 * 9
    fun stage(highlight: Boolean) = DkBoxes(
        ImageSize, 0,
        truthRects() + shown.filter { !highlight || it !== best }.map { rect(it.box, DkInk.Violet, thin = true) } +
            if (highlight) listOf(rect(best.box, DkInk.Yellow)) else emptyList(),
        dots = listOf(DkP(cx, cy)),
    )
    val header = "anchors at feature cell ($col, $row)"
    val legend = listOf(truthLegend, legend(DkInk.Violet, "Anchor", SwatchStyle.Ring), legend(DkInk.Yellow, "Best match", SwatchStyle.Ring))
    val shape = when (best.ratio) {
        "1:2" -> "tall"
        "2:1" -> "wide"
        else -> "square"
    }
    val frames = listOf(
        DkFrame(
            header, stage(false), legend.take(2),
            listOf("3 scales × 3 ratios = {9} anchors per cell", "scales 64, 128, 256 px · ratios 1:1, 1:2, 2:1"),
            if (tab == 3) "Faster R-CNN places {9 anchors} at this cell." else "At this cell, the {3 anchors} of ${shown[0].scale} px.",
            "Anchors are fixed reference boxes. The region proposal network only has to say which ones hold an object and how to nudge them.",
        ),
        DkFrame(
            header, stage(true), legend,
            listOf("best: ${best.scale} px, ${best.ratio} → IoU {${n(bestIou)}} ${if (bestIou >= 0.7) "≥ 0.7 positive" else "< 0.7"}", "anchors: 20·20·9 = {${comma(total.toDouble())}} scored by the RPN"),
            if (tab == 3) "The $shape anchor fits truth 1 best at IoU {${n(bestIou)}}."
            else "At ${best.scale} px the best anchor reaches IoU {${n(bestIou)}}.",
            if (tab == 3) "The RPN slides over the shared feature map and scores all ${comma(total.toDouble())} anchors in one pass, so proposals cost ~10 ms instead of ~2 s."
            else if (bestIou >= 0.7) "Close enough to count as a positive example for this object."
            else "Too far off in size or shape: this scale alone would leave truth 1 without a positive anchor.",
        ),
        DkFrame(
            header, stage(true), legend,
            listOf("positive (IoU ≥ 0.7): {$pos}", "negative (IoU < 0.3): ${comma(neg.toDouble())} · ignored: ${total - pos - neg}"),
            "Only {$pos} of ${comma(total.toDouble())} anchors are positive.",
            "Training samples 256 anchors per image, half positive where possible, so the RPN isn't drowned in background.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── YOLO: the responsible cell ──

private fun yoloLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Truth 1", "Truth 2")) { tab, _ ->
    val s = 7
    val cell = ImageSize / s
    val t = truths[tab]
    val gx = t.cx / cell
    val gy = t.cy / cell
    val col = floor(gx).toInt()
    val row = floor(gy).toInt()
    val cellsOf = truths.map { floor(it.cx / cell).toInt() to floor(it.cy / cell).toInt() }
    fun cellRect(c: Int, r: Int) = DkRect(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell, DkInk.Yellow, fill = true)
    fun stage(cells: Boolean) = DkBoxes(
        ImageSize, s,
        (if (cells) cellsOf.map { (c, r) -> cellRect(c, r) } else emptyList()) + truthRects(),
        dots = truths.map { DkP(it.cx, it.cy) },
    )
    val output = s * s * 30
    val header = "$s×$s grid · the cell holding each centre is responsible"
    val legend = listOf(truthLegend, legend(DkInk.Yellow, "Responsible cell"), legend(DkInk.Yellow, "Centre", SwatchStyle.Dot))
    val frames = listOf(
        DkFrame(
            header, stage(false), listOf(truthLegend, legend[2]),
            listOf("grid: $s×$s, each cell ${n(cell, 1)} px", "truth ${tab + 1} centre: (${n(t.cx, 1)}, ${n(t.cy, 1)})"),
            "YOLO cuts the image into a {$s×$s} grid.",
            "No proposals and no anchors scored one by one: the whole image goes through the network once.",
        ),
        DkFrame(
            header, stage(true), legend,
            listOf("(${n(t.cx, 1)}, ${n(t.cy, 1)}) / ${n(cell, 1)} = (${n(gx)}, ${n(gy)})", "→ cell {($col, $row)}"),
            "Truth ${tab + 1}'s centre falls in cell {($col, $row)}.",
            "That one cell is responsible for predicting this object; the other ${s * s - 1} cells learn to say 'nothing here' for it.",
        ),
        DkFrame(
            header, stage(true), legend,
            listOf("truth ${tab + 1} → cell ($col, $row) · x, y = ${n(gx - col)}, ${n(gy - row)} in cell", "w, h = ${n(t.w / ImageSize)}, ${n(t.h / ImageSize)} of image · output $s·$s·30 = {${comma(output.toDouble())}}"),
            "One forward pass predicts {${comma(output.toDouble())}} numbers: every box at once.",
            "No proposals. Each cell predicts 2 boxes and 20 class scores; only cells ${cellsOf.joinToString(" and ") { "(${it.first}, ${it.second})" }} are trained to find these objects.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── SSD: boxes per feature map ──

private class SsdLevel(val grid: Int, val stride: Int, val perCell: Int, val box: Int) {
    val boxes get() = grid * grid * perCell
}

private val ssdLevels = listOf(
    SsdLevel(38, 8, 4, 60), SsdLevel(19, 16, 6, 102), SsdLevel(10, 32, 6, 144),
    SsdLevel(5, 64, 6, 186), SsdLevel(3, 100, 4, 228), SsdLevel(1, 300, 4, 270),
)

private fun ssdLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val total = ssdLevels.sumOf { it.boxes }
    val top = ssdLevels.maxOf { it.boxes }.toDouble()
    val objects = truths.map { sqrt(it.area) * 300 / ImageSize }
    val match = objects.map { o -> ssdLevels.indices.minBy { abs(ssdLevels[it].box - o) } }
    fun rows(hot: Set<Int>) = DkRows(
        ssdLevels.mapIndexed { i, l ->
            DkRow(
                "${l.grid}×${l.grid} · stride ${l.stride}", "box ≈ ${l.box} px",
                listOf(DkBar(l.boxes / top, if (i in hot) DkInk.Yellow else DkInk.Blue, comma(l.boxes.toDouble()))), hot = i in hot,
            )
        },
    )
    val header = "default boxes per feature map · ${comma(total.toDouble())} total"
    val legend = listOf(legend(DkInk.Blue, "Default boxes"), legend(DkInk.Yellow, "Matches our objects"))
    val m = ssdLevels[match[0]]
    val frames = listOf(
        DkFrame(
            header, rows(emptySet()), legend.take(1),
            listOf("Σ grid² × boxes per cell = {${comma(total.toDouble())}}", "one pass, six feature maps"),
            "SSD scores {${comma(total.toDouble())}} default boxes in one pass.",
            "Each feature map gets its own small conv head that predicts class scores and box offsets for every default box on it.",
        ),
        DkFrame(
            header, rows(match.toSet()), legend,
            listOf("object sizes: ${objects.joinToString(", ") { "${n(it, 0)} px" }} at 300×300", "nearest scale: {${m.box} px} on the ${m.grid}×${m.grid} map"),
            if (match.distinct().size == 1) "Both objects are matched on the {${m.grid}×${m.grid}} map." else "The objects match {${match.distinct().size} different} maps.",
            "SSD puts heads on six maps, each tuned to one object size. ${pct(ssdLevels[0].boxes / total.toDouble(), 0)} of boxes sit on the 38×38 map for small objects.",
        ),
        DkFrame(
            header, rows(match.toSet()), legend,
            listOf("positives: boxes with IoU ≥ 0.5 to an object", "negatives kept: 3 × positives, the hardest ones"),
            "Hard negative mining keeps background at {3 : 1}.",
            "Nearly all ${comma(total.toDouble())} boxes are background. Training on all of them would teach SSD to say 'nothing' everywhere.",
        ),
        DkFrame(
            header, rows(setOf(0)), legend,
            listOf("smallest default box: 60 px of 300", "objects under ~30 px fall between scales"),
            "The {38×38} map handles the smallest objects, and still misses tiny ones.",
            "Its features come from early, shallow layers that see little context. This is where SSD trails two-stage detectors; FPN and RetinaNet fix it.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── RetinaNet: focal loss ──

private val retinaGammas = listOf(0.0, 1.0, 2.0, 5.0)

private fun retinaLab(): DkLab = DkLab(DkControl.Tabs, tabs = retinaGammas.map { "γ = ${n(it, 0)}" }, initialTab = 2) { tab, _ ->
    val g = retinaGammas[tab]
    class Group(val name: String, val count: Double, val pt: Double)
    val groups = listOf(Group("${kilo(120000.0)} easy background", 120000.0, 0.99), Group("85 hard background", 85.0, 0.4), Group("2 objects", 2.0, 0.6))
    val anchors = groups.sumOf { it.count }
    val ce = groups.map { it.count * -ln(it.pt) }
    val fl = groups.map { it.count * (1 - it.pt).pow(g) * -ln(it.pt) }
    val ceShare = ce.map { it / ce.sum() }
    val flShare = fl.map { it / fl.sum() }
    fun rows(pair: Boolean) = DkRows(
        groups.mapIndexed { i, gr ->
            DkRow(
                gr.name, "p_t = ${n(gr.pt, 2).trimEnd('0').trimEnd('.')}",
                listOf(DkBar(ceShare[i], DkInk.Orange, pct(ceShare[i]))) + if (pair) listOf(DkBar(flShare[i], DkInk.Green, pct(flShare[i]))) else emptyList(),
                hot = i == 0, pair = pair,
            )
        },
    )
    val header = "share of the total loss · ${comma(anchors)} anchors"
    val legend = listOf(legend(DkInk.Orange, "Cross-entropy"), legend(DkInk.Green, "Focal, γ = ${n(g, 0)}"))
    val easyEach = (0.01).pow(g) * -ln(0.99)
    val supers = "⁰¹²³⁴⁵"
    val frames = listOf(
        DkFrame(
            header, rows(false), legend.take(1),
            listOf("CE = −log p_t", "easy: −log 0.99 = 0.0101 each × 120,000"),
            "With cross-entropy, easy background is {${pct(ceShare[0])}} of the loss.",
            "Each confident negative costs almost nothing, but there are 120,000 of them against 2 objects.",
        ),
        DkFrame(
            header, rows(true), legend,
            listOf("FL = −(1 − p_t)^γ · log p_t", "easy: (0.01)${supers[g.toInt()]} · 0.0101 = {${small(easyEach)}} each"),
            if (g == 0.0) "At γ = 0 focal loss {is} cross-entropy." else "Easy background falls from {${pct(ceShare[0], 0)}} of the loss to ${pct(flShare[0])}.",
            if (g == 0.0) "Nothing is down-weighted: step γ up to see the easy anchors fade."
            else "120,000 confident negatives used to drown out 2 objects. The (1 − p_t)${supers[g.toInt()]} factor shrinks them ${comma(1 / 0.01.pow(g))}×, so training focuses on the hard cases.",
        ),
        DkFrame(
            header, rows(true), legend,
            listOf("hard background: ${pct(flShare[1])} · objects: ${pct(flShare[2])}", "plus α = 0.25 to balance the classes"),
            if (g == 0.0) "Hard cases carry only {${pct(flShare[1] + flShare[2])}} of the loss at γ = 0." else "The hard cases now carry {${pct(flShare[1] + flShare[2])}} of the loss.",
            if (g == 0.0) "The 87 anchors that matter are outvoted by 120,000 that don't. Raise γ to hand the loss back to them." else "That's what let a one-stage detector match two-stage accuracy without sampling: every anchor is used, but easy ones barely count.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── U-Net ──

private fun unetLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    // Valid 3×3 convs lose 4 pixels per level; 2×2 pooling halves; up-convolution doubles.
    val encoder = ArrayList<Int>()
    var side = 572
    repeat(4) {
        side -= 4
        encoder += side
        side /= 2
    }
    val bottom = side - 4
    val decoder = ArrayList<Int>()
    var d = bottom
    repeat(4) {
        d = d * 2 - 4
        decoder += d
    }
    val legend = listOf(legend(DkInk.Blue, "Encoder"), legend(DkInk.Green, "Decoded"), legend(DkInk.Yellow, "Current"), legend(DkInk.Yellow, "Skip + crop", SwatchStyle.DashedLine))
    val header = "feature-map side length · 572 px input, valid convs"
    fun up(i: Int) = 2 * (if (i == 0) bottom else decoder[i - 1])
    fun enc(i: Int) = encoder[3 - i]
    fun crop(i: Int) = (enc(i) - up(i)) / 2
    val ch = listOf(512, 256, 128, 64)
    val frames = listOf(
        DkFrame(
            header, DkUNet(encoder, bottom, decoder, 0, null), legend.take(1),
            listOf("each level: two 3×3 valid convs (−4 px), then 2×2 pool (÷2)", "572 → ${encoder.joinToString(" → ")} → {$bottom}"),
            "The encoder shrinks the map {4 times}, down to $bottom×$bottom.",
            "Each level doubles the channels and halves the size: it learns what is in the image and forgets exactly where.",
        ),
        DkFrame(
            header, DkUNet(encoder, bottom, decoder, 1, 0), legend,
            listOf("up-conv: $bottom → ${up(0)} · encoder 4 is ${enc(0)}, crop ${crop(0)} px each side", "concat ${up(0)}×${up(0)}×(${ch[0]} + ${ch[0]}) → two 3×3 convs → {${decoder[0]}×${decoder[0]}×${ch[0]}}"),
            "The decoder doubles the map back up: {${decoder[0]}}.",
            "An up-convolution doubles the size, then the matching encoder map is cropped to fit and stacked alongside.",
        ),
        DkFrame(
            header, DkUNet(encoder, bottom, decoder, 2, 1), legend,
            listOf("up-conv: ${decoder[0]} → ${up(1)} · encoder 3 is ${enc(1)}, crop ${crop(1)} px each side", "concat ${up(1)}×${up(1)}×(${ch[1]} + ${ch[1]}) → two 3×3 convs → {${decoder[1]}×${decoder[1]}×${ch[1]}}"),
            "The skip hands back {detail} the encoder pooled away.",
            "Decoder maps are upsampled, then joined with the matching encoder map so boundaries stay sharp. Valid convs shrink the output to ${decoder[3]}×${decoder[3]} for a 572×572 tile.",
        ),
        DkFrame(
            header, DkUNet(encoder, bottom, decoder, 4, null), legend.filter { it.label != "Current" },
            listOf("output: {${decoder[3]}×${decoder[3]}} × 2 classes", "overlap tiles by ${(572 - decoder[3]) / 2} px to cover a whole image"),
            "The output is a {${decoder[3]}×${decoder[3]}} mask for the tile's centre.",
            "Every pixel gets a class. U-Net trained on just 30 annotated microscopy images, helped by heavy elastic augmentation.",
        ),
    )
    stepActions(frames) { "Next Step" }
}

// ── Mask R-CNN ──

private fun maskRcnnLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val m = 28
    val mask = List(m * m) { i ->
        val r = i / m
        val c = i % m
        val dx = (c + 0.5 - 14) / 11
        val dy = (r + 0.5 - 14) / 13
        dx * dx + dy * dy <= 1
    }
    val on = mask.count { it }
    val snap = Snap(truth2)
    fun grid(show: Boolean) = DkGrid("", m, m, mask.map { if (show && it) DkCell("", DkCellTone.Ink, ink = DkInk.Green) else DkCell("", DkCellTone.Empty) }, maxCell = 9f)
    fun stage(show: Boolean, side: List<String>) = DkGrids(listOf(listOf(grid(show))), listOf(1.7f), side = side, sideWeight = 1f)
    val shift = "{w:${snap.errors.joinToString(", ") { n(it, 0) }} px}"
    val sideFull = listOf("28×28 mask", "for truth 2", "", "{m:$on of ${m * m} on}", "", "RoIPool shift", shift, "", "RoIAlign shift", "{m:0 px}")
    val legend = listOf(legend(DkInk.Green, "Mask pixel"), legend(DkInk.Slate, "Background"))
    val frames = listOf(
        DkFrame(
            null, stage(false, listOf("28×28 mask", "for truth 2", "", "box + class:", "from Faster R-CNN")), legend.drop(1),
            listOf("heads: class · box · {mask}", "mask head: 4 convs + deconv → 28×28"),
            "Mask R-CNN is Faster R-CNN plus a {mask head}.",
            "For each detected box it also predicts which pixels inside belong to the object.",
        ),
        DkFrame(
            null, stage(false, listOf("28×28 mask", "for truth 2", "", "RoIPool shift", shift)), legend.drop(1),
            listOf("RoIPool: ${n(truth2.x1, 0)}/16 = ${n(truth2.x1 / 16)} → ${snap.c1} · {${n(snap.errors[0], 0)} px} off", "edges off by ${snap.errorText}"),
            "RoIPool's rounding moves the box up to {${n(snap.errors.max(), 0)} px}.",
            "Fine for a class label, ruinous for a mask: at 28×28 one feature cell is several mask pixels.",
        ),
        DkFrame(
            null, stage(true, sideFull), legend,
            listOf("RoIPool: ${n(truth2.x1, 0)}/16 = ${n(truth2.x1 / 16)} → ${snap.c1} · {${n(snap.errors[0], 0)} px} off", "RoIAlign: sample at ${n(truth2.x1 / 16)} by bilinear interpolation"),
            "A third head adds a {28×28 mask} per box.",
            "Masks need pixel alignment, so RoIAlign drops the rounding RoIPool used in Fast R-CNN. That one change lifted mask AP by roughly 3 points.",
        ),
        DkFrame(
            null, stage(true, sideFull), legend,
            listOf("loss: per-pixel sigmoid on the true class's mask", "one mask per class, no competition between them"),
            "Each instance gets its own {binary} mask.",
            "The mask is resized to the box and pasted into the image, so two touching people still come out as two objects.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── Semantic vs instance vs panoptic ──

private val people = listOf(
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
)

private fun segmentationLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Semantic", "Instance", "Panoptic"), initialTab = 1) { tab, _ ->
    val rows = people.size
    val cols = people[0].length
    val flat = people.joinToString("")
    val p1 = flat.count { it == '1' }
    val p2 = flat.count { it == '2' }
    // Connected regions of person pixels, 4-neighbour.
    val seen = BooleanArray(flat.length)
    var regions = 0
    for (i in flat.indices) if (flat[i] != '.' && !seen[i]) {
        regions++
        val stack = ArrayDeque(listOf(i))
        seen[i] = true
        while (stack.isNotEmpty()) {
            val j = stack.removeLast()
            val r = j / cols
            val c = j % cols
            listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1).forEach { (a, b) ->
                if (a in 0 until rows && b in 0 until cols) {
                    val k = a * cols + b
                    if (!seen[k] && flat[k] != '.') { seen[k] = true; stack.addLast(k) }
                }
            }
        }
    }
    fun cells(mode: Int) = flat.mapIndexed { i, ch ->
        val r = i / cols
        when {
            ch == '.' && mode == 3 -> DkCell("", DkCellTone.Ink, ink = if (r < 7) DkInk.Sky else DkInk.Slate)
            ch == '.' -> DkCell("", DkCellTone.Empty)
            mode == 0 -> DkCell("", DkCellTone.Ink, ink = DkInk.Grey)
            mode == 1 -> DkCell("", DkCellTone.Ink, ink = DkInk.Blue)
            ch == '1' -> DkCell("", DkCellTone.Ink, ink = DkInk.Blue)
            else -> DkCell("", DkCellTone.Ink, ink = DkInk.Orange)
        }
    }
    fun g(title: String, mode: Int) = DkGrid(title, rows, cols, cells(mode), maxCell = 13f)
    val semantic = g("semantic: \"person\"", 1)
    val second = when (tab) {
        0 -> semantic
        1 -> g("instance: #1, #2", 2)
        else -> g("panoptic: things + stuff", 3)
    }
    val pair = DkGrids(listOf(listOf(g("the image", 0)), listOf(second)), listOf(1f, 1f))
    val header = "same pixels, two kinds of label"
    val legendFor = when (tab) {
        0 -> listOf(legend(DkInk.Blue, "person"), legend(DkInk.Slate, "background"))
        1 -> listOf(legend(DkInk.Blue, "person"), legend(DkInk.Orange, "person #2"), legend(DkInk.Slate, "background"))
        else -> listOf(legend(DkInk.Blue, "person #1"), legend(DkInk.Orange, "person #2"), legend(DkInk.Sky, "sky"), legend(DkInk.Slate, "ground"))
    }
    val frames = listOf(
        DkFrame(
            header, pair, legendFor,
            listOf("image: $cols×$rows pixels", "person pixels: ${p1 + p2}"),
            "Two people stand {touching} in the image.",
            "Every segmentation task labels each pixel; they differ in what the label says.",
        ),
        DkFrame(
            header, if (tab == 0) DkGrids(listOf(listOf(semantic)), listOf(1f)) else DkGrids(listOf(listOf(semantic), listOf(second)), listOf(1f, 1f)), legendFor,
            when (tab) {
                0 -> listOf("semantic: 1 class, {$regions connected region}", "${p1 + p2} pixels labelled \"person\"")
                1 -> listOf("semantic: 1 class, {$regions connected region}", "instance: #1 = $p1 px, #2 = $p2 px")
                else -> listOf("things: #1 = $p1 px, #2 = $p2 px", "stuff: sky, ground · every pixel labelled")
            },
            when (tab) {
                0 -> "Semantic segmentation gives every pixel a {class}."
                1 -> "Semantic labels see {one} blob; instance labels see two people."
                else -> "Panoptic labels {every pixel}: things get ids, stuff gets a class."
            },
            when (tab) {
                0 -> "Both people become one \"person\" region; nothing says where one ends and the other begins."
                1 -> "Semantic segmentation gives each pixel a class. Instance segmentation also says which object it belongs to, which is what Mask R-CNN adds."
                else -> "Countable things (people, cars) get instance ids; amorphous stuff (sky, road) only gets a class. No pixel is left unlabelled."
            },
        ),
        DkFrame(
            header, pair, legendFor,
            listOf("count from semantic: $regions region", "count from instance: {2} objects"),
            when (tab) {
                0 -> "Counting from semantic labels gives {$regions}, not 2."
                1 -> "Instance masks count {2} people."
                else -> "Panoptic quality scores {things and stuff} together."
            },
            when (tab) {
                0 -> "Touching objects merge. Use semantic segmentation when the class matters, not the count: road, sky, tissue."
                1 -> "Use instance segmentation when objects must be told apart: counting cells, tracking people, robot grasping."
                else -> "PQ multiplies how well segments match by how many are found, so one metric covers both halves."
            },
        ),
    )
    stepActions(frames) { "Next" }
}
