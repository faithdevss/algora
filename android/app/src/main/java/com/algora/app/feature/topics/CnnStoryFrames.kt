package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── CNN storyboard frames ────────────────────────────────────────────────────
// The fourteen convolutional labs drawn by DeepStoryLabs.kt: convolution, pooling, padding and stride on
// small grids computed cell by cell; the classic architectures as per-layer parameter and compute shares
// from their real layer shapes; the residual gradient by a seeded forward and backward pass. The iOS
// port is CnnStoryFrames.swift.

internal val cnnStoryTopicIds = setOf(
    "cnn", "conv_layers", "pooling_layers", "padding_strides", "lenet5", "alexnet", "vgg", "inception",
    "resnet", "densenet", "mobilenet", "efficientnet", "vit", "transfer_learning",
)

internal fun cnnLab(topicId: String): DkLab? = when (topicId) {
    "cnn" -> cnnIntroLab()
    "conv_layers" -> convLayersLab()
    "pooling_layers" -> poolingLab()
    "padding_strides" -> paddingLab()
    "lenet5" -> lenetLab()
    "alexnet" -> alexnetLab()
    "vgg" -> vggLab()
    "inception" -> inceptionLab()
    "resnet" -> resnetLab()
    "densenet" -> densenetLab()
    "mobilenet" -> mobilenetLab()
    "efficientnet" -> efficientnetLab()
    "vit" -> vitLab()
    "transfer_learning" -> transferLab()
    else -> null
}

// ── Formatting ──

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

private fun t(v: Double, d: Int = 2): String {
    val s = dkNum(v, d)
    return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}

private fun comma(v: Double): String {
    val r = abs(v).toLong().toString().reversed().chunked(3).joinToString(",").reversed()
    return if (v < 0) "−$r" else r
}

/** 9,536 · 148.0k · 2.10M · 138M · 15.47B. */
private fun count(v: Double): String = when {
    v >= 1e9 -> n(v / 1e9, 2) + "B"
    v >= 1e8 -> n(v / 1e6, 0) + "M"
    v >= 1e7 -> n(v / 1e6, 1) + "M"
    v >= 1e6 -> n(v / 1e6, 2) + "M"
    v >= 1e4 -> n(v / 1e3, 1) + "k"
    else -> comma(v)
}

private fun pct(share: Double, d: Int = 1) = n(share * 100, d) + "%"

private fun legend(ink: DkInk, label: String) = DkLegend(ink, SwatchStyle.Fill, label)

private fun withActions(frames: List<DkFrame>, actions: (Int) -> String) =
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, actions(i)) }

private fun stepActions(frames: List<DkFrame>, action: String) =
    withActions(frames) { if (it == frames.lastIndex) "Start Over" else action }

// ── Grids ──

private typealias Mat = List<List<Double>>

private fun conv(x: Mat, k: Mat, stride: Int = 1, pad: Int = 0): Mat {
    val size = x.size + 2 * pad
    val kk = k.size
    val out = (size - kk) / stride + 1
    fun at(r: Int, c: Int) = if (r < pad || c < pad || r >= x.size + pad || c >= x.size + pad) 0.0 else x[r - pad][c - pad]
    return List(out) { i -> List(out) { j -> var s = 0.0; for (a in 0 until kk) for (b in 0 until kk) s += k[a][b] * at(i * stride + a, j * stride + b); s } }
}

private val sobel: Mat = listOf(listOf(1.0, 0.0, -1.0), listOf(2.0, 0.0, -2.0), listOf(1.0, 0.0, -1.0))

private fun valueCell(v: Double, scale: Double, text: String = t(v)): DkCell = when {
    v > 0 -> DkCell(text, DkCellTone.Pos, (v / scale).toFloat())
    v < 0 -> DkCell(text, DkCellTone.Neg, (-v / scale).toFloat())
    else -> DkCell("0", DkCellTone.Zero)
}

/** A map whose first [filled] cells (row-major) show their value; [current] is drawn as the cell being written. */
private fun mapCells(m: Mat, filled: Int, current: Int?, scale: Double, digits: Int = 2): List<DkCell> {
    val cols = m[0].size
    return m.flatten().mapIndexed { i, v ->
        when {
            i == current -> DkCell(t(v, digits), DkCellTone.Current)
            i < filled -> valueCell(v, scale, t(v, digits))
            else -> DkCell("", DkCellTone.Empty)
        }
    }.also { require(it.size == cols * m.size) }
}

private fun inputCells(x: Mat): List<DkCell> = x.flatten().map { if (it > 0) DkCell(t(it), DkCellTone.Pos, 0.55f) else DkCell("0", DkCellTone.Zero) }

private fun kernelCells(k: Mat): List<DkCell> {
    val top = k.flatten().maxOf { abs(it) }
    return k.flatten().map { valueCell(it, top) }
}

private fun maxAbs(m: Mat) = m.flatten().maxOf { abs(it) }.coerceAtLeast(1e-9)

// ── CNNs: one kernel over a 6×6 edge ──

private fun cnnIntroLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val x = List(6) { List(6) { c -> if (c >= 3) 1.0 else 0.0 } }
    val mirror = sobel.map { row -> row.map { -it } }
    val out = conv(x, sobel)
    val outM = conv(x, mirror)
    fun stage(k: Mat, m: Mat, filled: Int, current: Int?, window: Pair<Int, Int>?) = DkGrids(
        listOf(
            listOf(DkGrid("input 6×6", 6, 6, inputCells(x), listOfNotNull(window?.let { (r, c) -> DkBox(r, c, r + 2, c + 2) }), maxCell = 30f)),
            listOf(DkGrid("kernel", 3, 3, kernelCells(k), maxCell = 28f), DkGrid("output 4×4", 4, 4, mapCells(m, filled, current, 4.0), maxCell = 22f)),
        ),
        listOf(1.55f, 1f),
    )
    fun terms(k: Mat, r: Int, c: Int): String {
        val parts = (0 until 3).flatMap { a -> (0 until 3).map { b -> k[a][b] to x[r + a][c + b] } }.filter { it.first != 0.0 && it.second != 0.0 }
        if (parts.isEmpty()) return "0"
        return parts.mapIndexed { i, (w, v) ->
            val body = "${t(abs(w))}·${t(v)}"
            when {
                i == 0 -> if (w < 0) "−$body" else body
                w < 0 -> " − $body"
                else -> " + $body"
            }
        }.joinToString("")
    }
    val legend = listOf(DkLegend(DkInk.Yellow, SwatchStyle.Ring, "Window"), legend(DkInk.Blue, "Positive"), legend(DkInk.Pink, "Negative"))
    val frames = listOf(
        DkFrame(
            null, stage(sobel, out, 0, null, null), legend.drop(1),
            listOf("kernel: Sobel-x, 3×3 = {9} weights", "output: (6 − 3 + 1)² = 16 positions"),
            "A 6×6 image with a {vertical edge}.",
            "Left half 0, right half 1. The kernel's positive left column and negative right column respond to brightness changing left to right.",
        ),
        DkFrame(
            null, stage(sobel, out, 0, 0, 0 to 0), legend,
            listOf("window (0,0) · kernel = ${terms(sobel, 0, 0)}", "= {${t(out[0][0])}}"),
            "The first window sees only zeros: {${t(out[0][0])}}.",
            "Multiply the 9 pixels under the window by the 9 kernel weights and add: that's one output.",
        ),
        DkFrame(
            null, stage(sobel, out, 5, 5, 1 to 1), legend,
            listOf("window (1,1) · kernel = ${terms(sobel, 1, 1)}", "= {${t(out[1][1])}} · 9 shared weights, not 36 × 16"),
            "The window straddling the edge scores {${t(out[1][1])}}.",
            "The same 9 weights slide to all 16 positions. A dense layer would need ${36 * 16} for this output.",
        ),
        DkFrame(
            null, stage(sobel, out, 16, null, null), legend.drop(1),
            listOf("16 positions × 9 multiplies = 144", "zero where the window is flat, ${t(out[1][1])} on the edge"),
            "The edge shows up as {two columns of ${t(out[1][1])}}.",
            "Flat regions give 0 whether dark or bright: the kernel measures change, not brightness.",
        ),
        DkFrame(
            null, stage(mirror, outM, 16, null, null), legend.drop(1),
            listOf("mirror kernel = −1 × Sobel-x", "edge response {+${t(outM[1][1])}}"),
            "The mirrored kernel scores the same edge {+${t(outM[1][1])}}.",
            "Which sign means what is learned, not designed: training sets the 9 weights to whatever pattern helps.",
        ),
        DkFrame(
            null, stage(mirror, outM, 16, null, null), legend.drop(1),
            listOf("32 filters × 9 weights = 288 (+ 32 biases)", "output 4×4×32: one map per filter"),
            "Each filter makes one {feature map}; a layer stacks many.",
            "Later layers convolve over these maps, so their filters combine edges into corners, textures and parts.",
        ),
    )
    withActions(frames) { listOf("Apply Kernel", "Slide Window", "Finish Map", "Mirror Kernel", "Stack Filters", "Start Over")[it] }
}

// ── Convolution layers: kernel size ──

private val convInput: Mat = List(7) { r -> List(7) { c -> if (c >= 4 || r == 3) 1.0 else 0.0 } }
private val convSizes = listOf(1, 3, 5, 7)

private fun edgeKernel(k: Int): Mat = when (k) {
    1 -> listOf(listOf(1.0))
    3 -> sobel
    else -> List(k) { List(k) { c -> (k / 2 - c).coerceIn(-1, 1).toDouble() } }
}

private fun convLayersLab(): DkLab = DkLab(DkControl.Tabs, tabs = convSizes.map { "k = $it" }, initialTab = 1) { tab, _ ->
    val k = convSizes[tab]
    val kernel = edgeKernel(k)
    val map = conv(convInput, kernel)
    val o = map.size
    val kName = when (k) {
        1 -> "identity"
        3 -> "Sobel-x"
        else -> "$k×$k edge"
    }
    val input = DkGrid("input 7×7", 7, 7, inputCells(convInput), maxCell = 22f)
    val mapGrid = DkGrid("feature map $o×$o · $kName", o, o, mapCells(map, o * o, null, maxAbs(map)), maxCell = 30f)
    val stage = DkGrids(listOf(listOf(input), listOf(mapGrid)), listOf(1f, 1f))
    val sizeLine = "out = ⌊(7 + 2·0 − $k)/1⌋ + 1 = {$o}"
    val paramLine = "params = $k·$k + 1 bias = ${k * k + 1}"
    val legend = listOf(legend(DkInk.Blue, "Positive response"), legend(DkInk.Pink, "Negative"), legend(DkInk.Slate, "Zero"))
    val edge = map.flatten().minOrNull() ?: 0.0
    val frames = listOf(
        DkFrame(
            null, DkGrids(listOf(listOf(input), listOf(DkGrid("kernel $k×$k", k, k, kernelCells(kernel), maxCell = 26f))), listOf(1f, 1f)),
            legend.take(2), listOf("kernel: $kName, $k×$k", paramLine),
            if (k == 1) "A 1×1 kernel is {one weight}." else "A $k×$k kernel: {${k * k + 1}} weights, reused at every position.",
            if (k == 1) "It scales each pixel on its own, with no view of the neighbours."
            else "Its left side is positive and its right side negative, so it responds to brightness changing left to right.",
        ),
        DkFrame(
            null, stage, legend, listOf(sizeLine, paramLine),
            when (k) {
                1 -> "A 1×1 kernel just {copies} the input."
                3 -> "The vertical edge lights up, the horizontal bar {doesn't}."
                5 -> "At k = 5 the edge still shows, on a {3×3} map."
                else -> "At k = 7 the whole image becomes {one number}: ${t(map[0][0])}."
            },
            when (k) {
                1 -> "No neighbours means no edges. On many channels a 1×1 conv still mixes them, which is why Inception and MobileNet use it."
                3 -> "Sobel-x responds only to left-right change: the edge gives ${t(edge)}, and where the bar crosses it the response weakens to ${t(map[2][2])}."
                5 -> "Each output sees 25 pixels; without padding the map shrinks by k − 1 = 4."
                else -> "The kernel covers all 49 pixels, so there is nowhere left to slide."
            },
        ),
        DkFrame(
            null, stage, legend, listOf("receptive field: 1 + 3·(3 − 1) = {7}", "weights: 3·9 = 27 vs 7·7 = 49"),
            "Three stacked 3×3 layers see as far as one {7×7}.",
            "Stacks of small kernels see wide context with fewer weights and more non-linearities in between. That's VGG's whole design.",
        ),
    )
    stepActions(frames, "Next")
}

// ── Pooling ──

private val poolMap: Mat = listOf(
    listOf(0, 0, 0, 0, 0, 0, 0),
    listOf(0, -1, -1, 0, 1, 1, 0),
    listOf(0, -3, -3, 0, 3, 3, 0),
    listOf(0, -4, -4, 0, 4, 4, 0),
    listOf(0, -3, -3, 0, 3, 3, 0),
    listOf(0, -1, -1, 0, 1, 1, 0),
    listOf(0, 0, 0, 0, 0, 0, 0),
).map { row -> row.map { it.toDouble() } }

private fun pool(m: Mat, avg: Boolean): Mat = List(3) { i ->
    List(3) { j ->
        val w = listOf(m[2 * i][2 * j], m[2 * i][2 * j + 1], m[2 * i + 1][2 * j], m[2 * i + 1][2 * j + 1])
        if (avg) w.average() else w.max()
    }
}

private fun poolingLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Max", "Average")) { tab, _ ->
    val avg = tab == 1
    val out = pool(poolMap, avg)
    val shifted = poolMap.map { row -> List(7) { c -> if (c == 0) 0.0 else row[c - 1] } }
    val outShift = pool(shifted, avg)
    val same = (0 until 3).sumOf { i -> (0 until 3).count { j -> abs(out[i][j] - outShift[i][j]) < 1e-9 } }
    val windows = (0 until 3).flatMap { i -> (0 until 3).map { j -> DkBox(2 * i, 2 * j, 2 * i + 1, 2 * j + 1, dashed = true) } }
    fun stage(filled: Int, current: Int?, map: Mat = poolMap, pooled: Mat = out, title: String = "feature map 7×7 · 2×2 windows"): DkGrids {
        val win = current?.let { listOf(DkBox(2 * (it / 3), 2 * (it % 3), 2 * (it / 3) + 1, 2 * (it % 3) + 1)) } ?: emptyList()
        return DkGrids(
            listOf(
                listOf(DkGrid(title, 7, 7, map.flatten().map { valueCell(it, 4.0) }, windows + win, maxCell = 26f)),
                listOf(DkGrid("${if (avg) "avg" else "max"} pool 3×3", 3, 3, mapCells(pooled, filled, current, 4.0), maxCell = 46f, note = "row 7, col 7 dropped")),
            ),
            listOf(1.5f, 1f),
        )
    }
    val op = if (avg) "mean" else "max"
    fun windowText(i: Int, j: Int, m: Mat = poolMap) =
        listOf(m[2 * i][2 * j], m[2 * i][2 * j + 1], m[2 * i + 1][2 * j], m[2 * i + 1][2 * j + 1]).joinToString(", ") { t(it) }
    val legend = listOf(DkLegend(DkInk.Yellow, SwatchStyle.Ring, "Current window"), legend(DkInk.Blue, "Positive"), legend(DkInk.Pink, "Negative"))
    val frames = listOf(
        DkFrame(
            null, stage(0, null), legend.drop(1), listOf("7×7 → ⌊7/2⌋ = 3 windows per side", "row 7 and column 7 fall outside every window"),
            "Pooling tiles the map with {2×2 windows}.",
            "Each window becomes one number. Stride 2 means the windows don't overlap, so the map halves.",
        ),
        DkFrame(
            null, stage(2, 2), legend, listOf("$op(${windowText(0, 2)}) = {${t(out[0][2])}}", "row 1 done: ${(0 until 3).joinToString(", ") { t(out[0][it]) }}"),
            if (avg) "Average pooling keeps the window's {mean}: ${t(out[0][2])}." else "Max pooling keeps the window's {largest} value: ${t(out[0][2])}.",
            if (avg) "Strong and weak responses blend, so a lone spike is diluted by its neighbours."
            else "Where in the window the response sat is thrown away; only how strong it was survives.",
        ),
        DkFrame(
            null, stage(9, 5), legend,
            listOf("$op(${windowText(1, 2)}) = {${t(out[1][2])}}", "shift input 1 px → $same of 9 pooled values unchanged"),
            if (avg) "49 responses become 9, each the {mean} of its window." else "49 responses become 9, keeping the {strongest} in each window.",
            if (avg) "Average pool has no weights. After a 1-pixel shift, $same of the 9 outputs are identical: an average moves with every pixel in its window."
            else "Max pool has no weights. After a 1-pixel shift, $same of the 9 outputs are identical, which is the small translation tolerance pooling buys.",
        ),
        DkFrame(
            null, stage(9, null, shifted, outShift, "shifted 1 px right"), legend.drop(1),
            listOf("after shift: ${outShift.flatten().joinToString(" ") { t(it) }}", "unchanged: {$same of 9}"),
            "Shift the input one pixel and {$same of 9} outputs stay put.",
            if (avg) "Averages move whenever any pixel in the window changes, so average pooling tolerates shifts less than max."
            else "A max only changes when the strongest value leaves its window, so small shifts mostly pass through.",
        ),
    )
    stepActions(frames, "Next Window")
}

// ── Padding and stride ──

private val padSettings = listOf(1 to 0, 1 to 1, 2 to 1)

private fun paddingLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("s1 · p0", "s1 · p1", "s2 · p1"), initialTab = 2) { tab, _ ->
    val (s, p) = padSettings[tab]
    val size = 7 + 2 * p
    val map = conv(convInput, sobel, s, p)
    val o = map.size
    val padded = List(size) { r -> List(size) { c -> if (r < p || c < p || r >= 7 + p || c >= 7 + p) null else convInput[r - p][c - p] } }
    fun stage(filled: Int, current: Int?): DkGrids {
        val cells = padded.flatten().map { v -> if (v == null) DkCell("", DkCellTone.Pad) else if (v > 0) DkCell("1", DkCellTone.Pos, 0.55f) else DkCell("0", DkCellTone.Zero) }
        val boxes = (if (p > 0) listOf(DkBox(0, 0, size - 1, size - 1, dashed = true)) else emptyList()) +
            listOfNotNull(current?.let { i -> DkBox((i / o) * s, (i % o) * s, (i / o) * s + 2, (i % o) * s + 2) })
        return DkGrids(
            listOf(
                listOf(DkGrid(if (p > 0) "input 7×7, padded to $size×$size" else "input 7×7", size, size, cells, boxes, maxCell = 22f)),
                listOf(DkGrid("output $o×$o", o, o, mapCells(map, filled, current, 4.0), maxCell = 30f)),
            ),
            listOf(1.35f, 1f),
        )
    }
    val inner = 7 + 2 * p - 3
    val sizeLine = if (s == 1) "out = ⌊(7 + 2·$p − 3)/1⌋ + 1 = {$o}" else "out = ⌊(7 + 2·$p − 3)/$s⌋ + 1 = ⌊$inner/$s⌋ + 1 = {$o}"
    val legend = listOf(DkLegend(DkInk.Grey, SwatchStyle.DashedLine, "Zero padding"), DkLegend(DkInk.Yellow, SwatchStyle.Ring, "Window, step $s"))
    val mid = min(6, o * o - 1)
    val frames = listOf(
        DkFrame(
            null, stage(0, 0), if (p > 0) legend else legend.drop(1), listOf(sizeLine, "kernel 3×3, stride $s, padding $p"),
            if (p == 0) "Without padding, the first window starts {inside} the image." else "Padding wraps the image in a ring of {zeros}.",
            if (p == 0) "The border pixels can never sit at a window's centre, so the map loses a pixel on every side."
            else "Now the corner pixel can sit at a window's centre, like every other pixel.",
        ),
        DkFrame(
            null, stage(mid, mid), if (p > 0) legend else legend.drop(1),
            listOf(sizeLine, if (s > 1) "window jumps $s px; padding keeps the border" else "window moves 1 px at a time"),
            when (tab) {
                0 -> "Stride 1 without padding gives a {5×5} map."
                1 -> "Padding 1 keeps the map at {7×7}."
                else -> "Stride 2 with padding 1 gives a {4×4} map from 7×7."
            },
            when (tab) {
                0 -> "A corner pixel is read by 1 window, a centre pixel by 9. Padding 1 would keep 7×7."
                1 -> "'Same' padding: every pixel, border included, can sit at a window's centre. Stride 2 would roughly halve it."
                else -> "Padding lets the border pixels sit at a window's centre; stride 2 reads a quarter of the positions. No padding at stride 1 would give 5×5."
            },
        ),
        DkFrame(
            null, stage(o * o, null), if (p > 0) legend else legend.drop(1),
            listOf("$o·$o windows × 9 = ${o * o * 9} multiplies", sizeLine),
            "The full map: {$o×$o} from a 7×7 input.",
            when (tab) {
                0 -> "Each step without padding shrinks the map by 2; a deep net would run out of pixels."
                1 -> "Same size in and out, so layers can stack without the map shrinking."
                else -> "Downsampling by stride replaces a pooling layer in many modern nets, and costs a quarter of stride 1."
            },
        ),
    )
    stepActions(frames, "Slide Window")
}

// ── Architectures as per-layer shares ──

private class Layer(val name: String, val shape: String, val params: Double, val macs: Double)

private fun convLayer(name: String, k: Int, cin: Int, cout: Int, out: Int) =
    Layer(name, "$out×$out×$cout", (k * k * cin * cout + cout).toDouble(), out.toDouble() * out * cout * k * k * cin)

private fun denseLayer(name: String, cin: Int, cout: Int) = Layer(name, "$cout", (cin * cout + cout).toDouble(), cin.toDouble() * cout)

private fun archRows(layers: List<Layer>, hot: Int?): DkRows {
    val p = layers.sumOf { it.params }
    val m = layers.sumOf { it.macs }
    return DkRows(
        layers.mapIndexed { i, l ->
            if (l.params == 0.0) DkRow(l.name, l.shape, listOf(DkBar(null, DkInk.Blue, "no params")), hot = i == hot)
            else DkRow(
                l.name, l.shape,
                listOf(DkBar(l.params / p, DkInk.Blue, pct(l.params / p)), DkBar(l.macs / m, DkInk.Orange, pct(l.macs / m))),
                hot = i == hot, pair = true,
            )
        },
    )
}

private val archLegend = listOf(legend(DkInk.Blue, "Parameters"), legend(DkInk.Orange, "Compute (MACs)"))

private fun lenetLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val layers = listOf(
        convLayer("C1 conv 5×5", 5, 1, 6, 28), Layer("S2 pool", "14×14×6", 0.0, 0.0),
        convLayer("C3 conv 5×5", 5, 6, 16, 10), Layer("S4 pool", "5×5×16", 0.0, 0.0),
        convLayer("C5 conv 5×5", 5, 16, 120, 1), denseLayer("F6 dense", 120, 84), denseLayer("Output", 84, 10),
    )
    val p = layers.sumOf { it.params }
    val m = layers.sumOf { it.macs }
    fun sh(i: Int) = layers[i].params / p
    fun mc(i: Int) = layers[i].macs / m
    val header = "per layer · ${count(p)} params, ${count(m)} MACs"
    val heads = listOf(
        "LeNet-5 has {${count(p)}} weights and does ${count(m)} multiply-adds." to
            "Blue is each layer's share of the weights, orange its share of the compute. They are not the same layers.",
        "C1 has {${comma(layers[0].params)}} weights but does ${pct(mc(0))} of the work." to
            "Six 5×5 filters, each reused at all 784 positions of the 28×28 map.",
        "S2 halves each map to 14×14 with {no weights}." to
            "Pooling only summarises each 2×2 window, so it adds almost nothing to either bar.",
        "C3 holds {${pct(sh(2))}} of the weights but does ${pct(mc(2))} of the work." to
            "It reuses ${comma(layers[2].params)} weights at 100 positions. C5 is the reverse: ${pct(sh(4), 0)} of the weights, used once.",
        "S4 shrinks the maps to {5×5×16}: 400 numbers." to
            "After two conv-pool pairs the image is a small stack of feature maps, ready for the dense layers.",
        "C5 holds {${pct(sh(4))}} of the weights, used at one position." to
            "Its 5×5 kernel covers the whole 5×5 map, so it is a dense layer in all but name.",
        "F6 is a plain dense layer: {${comma(layers[5].params)}} weights." to
            "Each weight is used once per image, so its compute share matches its weight share.",
        "Ten outputs, one per {digit}." to
            "The whole net reads a 32×32 digit in ${count(m)} multiply-adds: tiny today, a lot for 1998.",
    )
    val frames = heads.mapIndexed { i, (h, b) ->
        DkFrame(header, archRows(layers, if (i == 0) null else i - 1), archLegend, emptyList(), h, b)
    }
    stepActions(frames, "Next Layer")
}

private fun alexnetLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val layers = listOf(
        convLayer("conv1 11×11 s4", 11, 3, 96, 55), convLayer("conv2 5×5", 5, 96, 256, 27),
        convLayer("conv3 3×3", 3, 256, 384, 13), convLayer("conv4 3×3", 3, 384, 384, 13), convLayer("conv5 3×3", 3, 384, 256, 13),
        denseLayer("fc6", 9216, 4096), denseLayer("fc7", 4096, 4096), denseLayer("fc8", 4096, 1000),
    )
    val p = layers.sumOf { it.params }
    val m = layers.sumOf { it.macs }
    val convP = layers.take(5).sumOf { it.params } / p
    val convM = layers.take(5).sumOf { it.macs } / m
    val header = "${count(p)} params, ${count(m)} MACs · pools omitted"
    val frames = ArrayList<DkFrame>()
    frames += DkFrame(
        header, archRows(layers, null), archLegend.take(1) + legend(DkInk.Orange, "Compute"), emptyList(),
        "AlexNet: {${count(p)}} weights, ${count(m)} multiply-adds per image.",
        "Five conv layers then three dense ones, the net that won ImageNet 2012 on two GPUs.",
    )
    layers.forEachIndexed { i, l ->
        val ps = l.params / p
        val ms = l.macs / m
        val conv = i < 5
        frames += DkFrame(
            header, archRows(layers, i), archLegend.take(1) + legend(DkInk.Orange, "Compute"), emptyList(),
            if (ps > ms) "${l.name.substringBefore(' ')} alone is {${pct(ps, 0)}} of the model, ${pct(ms)} of the compute."
            else "${l.name.substringBefore(' ')} does {${pct(ms)}} of the compute with ${pct(ps)} of the weights.",
            if (conv) "Each of its ${count(l.params)} weights is reused at all ${l.shape.substringBefore('×')}×${l.shape.substringBefore('×')} positions of its output map."
            else "Every input connects to every output, so each weight is used once per image.",
        )
    }
    frames += DkFrame(
        header, archRows(layers, null), archLegend.take(1) + legend(DkInk.Orange, "Compute"), emptyList(),
        "The five conv layers do {${pct(convM, 0)}} of the work with ${pct(convP, 0)} of the weights.",
        "Weights live in the dense layers, compute in the convs. Later nets dropped most of the dense layers.",
    )
    stepActions(frames, "Next Layer")
}

private fun vggLayers(cfg: List<Int>): List<Layer> {
    val ch = listOf(64, 128, 256, 512, 512)
    val sz = listOf(224, 112, 56, 28, 14)
    var cin = 3
    val blocks = cfg.mapIndexed { b, convs ->
        var params = 0.0
        var macs = 0.0
        repeat(convs) {
            params += 9.0 * cin * ch[b] + ch[b]
            macs += sz[b].toDouble() * sz[b] * 9 * cin * ch[b]
            cin = ch[b]
        }
        Layer("block${b + 1} · $convs× 3×3", "${sz[b]}×${sz[b]}×${ch[b]}", params, macs)
    }
    return blocks + denseLayer("fc6", 25088, 4096) + denseLayer("fc7", 4096, 4096) + denseLayer("fc8", 4096, 1000)
}

private fun vggLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("VGG-16", "VGG-19")) { tab, _ ->
    val cfg = if (tab == 0) listOf(2, 2, 3, 3, 3) else listOf(2, 2, 4, 4, 4)
    val convs = cfg.sum()
    val layers = vggLayers(cfg)
    val p = layers.sumOf { it.params }
    val m = layers.sumOf { it.macs }
    val name = if (tab == 0) "VGG-16" else "VGG-19"
    val convM = layers.take(5).sumOf { it.macs } / m
    val header = "${count(p)} params, ${count(m)} MACs"
    val legend = archLegend.take(1) + legend(DkInk.Orange, "Compute")
    val frames = ArrayList<DkFrame>()
    frames += DkFrame(
        header, archRows(layers, null), legend, emptyList(),
        "$name: {$convs} conv layers, every one 3×3.",
        "Five blocks, each halving the map and doubling the channels, then three dense layers.",
    )
    layers.forEachIndexed { i, l ->
        val ps = l.params / p
        val ms = l.macs / m
        frames += DkFrame(
            header, archRows(layers, i), legend, emptyList(),
            when {
                i == 5 -> "fc6 holds {${pct(ps, 0)}} of $name's ${count(p)} weights."
                i > 5 -> "${l.name} holds {${pct(ps)}} of the weights, ${pct(ms)} of the compute."
                else -> "block${i + 1} does {${pct(ms)}} of the compute with ${pct(ps)} of the weights."
            },
            when {
                i == 5 -> "The $convs conv layers, all 3×3, do ${pct(convM, 0)} of the compute. Two stacked 3×3s see as far as one 5×5 with 18C² weights instead of 25C²."
                i > 5 -> "Dense layers use each weight once per image, so they are heavy to store and cheap to run."
                else -> "${cfg[i]} convs on a ${l.shape.substringBefore('×')}×${l.shape.substringBefore('×')} map: big maps make every weight work many times."
            },
        )
    }
    stepActions(frames, "Next Block")
}

// ── Inception: 1×1 reductions ──

private fun inceptionLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Naive", "With 1×1"), initialTab = 1) { tab, _ ->
    val cin = 192
    class Branch(val name: String, val meta: String, val naive: Double, val reduced: Double)
    val branches = listOf(
        Branch("1×1 · 64", "", cin * 64.0, cin * 64.0),
        Branch("3×3 · 128", "reduce 192→96", 9.0 * cin * 128, cin * 96.0 + 9.0 * 96 * 128),
        Branch("5×5 · 32", "reduce 192→16", 25.0 * cin * 32, cin * 16.0 + 25.0 * 16 * 32),
        Branch("pool · 32", "", cin * 32.0, cin * 32.0),
    )
    val top = branches.maxOf { it.naive }
    val naive = branches.sumOf { it.naive }
    val reduced = branches.sumOf { it.reduced }
    val both = tab == 1
    fun rows(hot: Int?) = DkRows(
        branches.mapIndexed { i, b ->
            DkRow(
                b.name, if (both) b.meta else "",
                listOf(DkBar(b.naive / top, DkInk.Orange, count(b.naive))) + if (both) listOf(DkBar(b.reduced / top, DkInk.Green, count(b.reduced))) else emptyList(),
                hot = i == hot,
            )
        },
    )
    val header = "MACs per position · $cin input channels"
    val legend = listOf(legend(DkInk.Orange, "Naive")) + if (both) listOf(legend(DkInk.Green, "With 1×1 reduce")) else emptyList()
    val b3 = branches[1]
    val b5 = branches[2]
    val frames = if (!both) listOf(
        DkFrame(
            header, rows(null), legend, listOf("module: ${branches.joinToString(" + ") { count(it.naive) }}", "= {${count(naive)}} per position"),
            "Inception runs {four branches} side by side and concatenates them.",
            "1×1, 3×3, 5×5 and pooling each look at a different scale; the next layer gets all four.",
        ),
        DkFrame(
            header, rows(1), legend, listOf("3×3: 9·192·128 = {${count(b3.naive)}}", "${pct(b3.naive / naive, 0)} of the module"),
            "The 3×3 branch alone costs {${count(b3.naive)}} MACs per position.",
            "Every one of its 128 filters reads all 192 input channels at 9 positions.",
        ),
        DkFrame(
            header, rows(2), legend, listOf("5×5: 25·192·32 = {${count(b5.naive)}}", "just 32 output channels"),
            "The 5×5 branch costs {${count(b5.naive)}} for only 32 channels.",
            "A 5×5 kernel over 192 channels is 4,800 weights per filter. Switch to “With 1×1” to see the fix.",
        ),
    ) else listOf(
        DkFrame(
            header, rows(null), legend, listOf("module: ${count(naive)} → {${count(reduced)}}", "= ${n(naive / reduced, 1)}× cheaper"),
            "1×1 reductions shrink the module to {${count(reduced)}} MACs per position.",
            "A 1×1 conv squeezes 192 channels down before the expensive 3×3 and 5×5 kernels run.",
        ),
        DkFrame(
            header, rows(1), legend,
            listOf("3×3: ${count(b3.naive)} → 192·96 + 9·96·128 = {${count(b3.reduced)}}", "${n(b3.naive / b3.reduced, 1)}× cheaper"),
            "Reducing to 96 channels cuts the 3×3 branch {${n(b3.naive / b3.reduced, 1)}×}.",
            "The 1×1 costs 192·96 per position, but the 3×3 now reads 96 channels instead of 192.",
        ),
        DkFrame(
            header, rows(2), legend,
            listOf("5×5: 25·192·32 = ${count(b5.naive)} → 192·16 + 25·16·32 = {${count(b5.reduced)}}", "module: ${count(naive)} → ${count(reduced)} = {${n(naive / reduced, 1)}× cheaper}"),
            "A 1×1 squeeze cuts the 5×5 branch {${n(b5.naive / b5.reduced, 1)}×}.",
            "Squeeze channels first, then apply the big kernel. The whole module drops from ${count(naive)} to ${count(reduced)} per position.",
        ),
    )
    stepActions(frames, "Next")
}

// ── ResNet: gradients with and without skips ──

private const val ResLayers = 20
private const val ResWidth = 16

/** h ← skip·h + branch·W·ReLU(h) for 20 layers; mean |∂L/∂h| per layer relative to the output's. */
private fun resGrad(skip: Double, branch: Double): List<Double> {
    var s = 404L
    fun u(): Double { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s / 2147483648.0 }
    fun g(): Double { val a = max(u(), 1e-12); val b = u(); return sqrt(-2 * ln(a)) * cos(2 * PI * b) }
    val sigma = 0.25
    val batch = 32
    val w = List(ResLayers) { Array(ResWidth) { DoubleArray(ResWidth) { g() * sigma } } }
    val hs = ArrayList<Array<DoubleArray>>()
    var h = Array(batch) { DoubleArray(ResWidth) { g() } }
    for (l in 0 until ResLayers) {
        hs += h
        val prev = h
        h = Array(batch) { b ->
            DoubleArray(ResWidth) { i ->
                var z = 0.0
                for (j in 0 until ResWidth) z += w[l][i][j] * max(0.0, prev[b][j])
                skip * prev[b][i] + branch * z
            }
        }
    }
    var gr = Array(batch) { DoubleArray(ResWidth) { 1.0 } }
    val out = DoubleArray(ResLayers)
    for (l in ResLayers - 1 downTo 0) {
        out[l] = gr.sumOf { r -> r.sumOf { abs(it) } } / (batch * ResWidth)
        val prev = hs[l]
        val cur = gr
        gr = Array(batch) { b ->
            DoubleArray(ResWidth) { j ->
                var gu = 0.0
                for (i in 0 until ResWidth) gu += w[l][i][j] * branch * cur[b][i]
                skip * cur[b][j] + (if (prev[b][j] > 0) gu else 0.0)
            }
        }
    }
    return out.map { it / out.last() }
}

private val resRuns: List<List<Double>> by lazy { listOf(resGrad(0.0, 1.0), resGrad(1.0, 1.0), resGrad(1.0, 0.2)) }

private fun sciText(v: Double): String {
    var e = floor(log10(v)).toInt()
    var m = floor(v / 10.0.pow(e) * 10 + 0.5) / 10
    if (m >= 10) { m /= 10; e += 1 }
    return dkNum(m, 1) + "e" + (if (e < 0) "−" else "") + abs(e)
}

private fun mag(v: Double) = if (v >= 0.01 && v < 1000) n(v, if (v >= 100) 1 else 2) else sciText(v)

private fun resnetLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Plain", "Residual", "Both"), initialTab = 2) { tab, _ ->
    val (plain, res, scaled) = resRuns
    fun lineOf(v: List<Double>, ink: DkInk, dashed: Boolean = false) = DkLine(v.mapIndexed { i, x -> DkP(i + 1.0, log10(x)) }, ink, dashed)
    val shown = when (tab) {
        0 -> listOf(plain to DkInk.Pink)
        1 -> listOf(res to DkInk.Green)
        else -> listOf(plain to DkInk.Pink, res to DkInk.Green)
    }
    fun plot(extra: Boolean): DkPlot {
        val all = shown.map { it.first } + if (extra) listOf(scaled) else emptyList()
        val logs = all.flatten().map { log10(it) } + 0.0
        val lo = floor(logs.min()) - 0.2
        val hi = ceil(logs.max()) + 0.2
        val ticks = (ceil(lo).toInt()..floor(hi).toInt()).reversed().map { k -> k.toDouble() to if (k == 0) "1" else "1e" + (if (k < 0) "−" else "") + abs(k) }
        return DkPlot(
            0.5 to ResLayers + 0.5, lo to hi, ticks, "layer 1", "layer $ResLayers",
            shown.map { (v, ink) -> lineOf(v, ink) } + if (extra) listOf(lineOf(scaled, DkInk.Blue, dashed = true)) else emptyList(),
            shown.map { (v, _) -> DkDot(DkP(1.0, log10(v[0]))) }, axis = false,
        )
    }
    val header = "gradient norm per layer, relative to the output"
    val legend = shown.map { (_, ink) -> DkLegend(ink, SwatchStyle.Line, if (ink == DkInk.Pink) "Plain" else "Residual") }
    val p1 = mag(plain[0])
    val r1 = mag(res[0])
    val frames = listOf(
        DkFrame(
            header, plot(false), legend,
            when (tab) {
                0 -> listOf("h ← W·ReLU(h), 20 times", "∂h/∂h_prev = W·ReLU′")
                1 -> listOf("h ← h + W·ReLU(h), 20 times", "∂h/∂h_prev = 1 + W·ReLU′")
                else -> listOf("plain: h ← W·ReLU(h)", "residual: h ← h + W·ReLU(h)")
            },
            when (tab) {
                0 -> "A plain stack multiplies the gradient by {every layer's} Jacobian."
                1 -> "A skip connection adds the input back: {x + F(x)}."
                else -> "Same 20 layers, same weights: {with and without} skips."
            },
            "Backprop runs from the output (layer $ResLayers, gradient 1) back to layer 1 through each layer in turn.",
        ),
        DkFrame(
            header, plot(false), legend,
            listOf("∂(x + F(x))/∂x = 1 + ∂F/∂x", "layer 1: plain {$p1} · residual {$r1}"),
            when (tab) {
                0 -> "Layer 1 gets {$p1} of the output's gradient."
                1 -> "With skips, layer 1 gets {$r1×} the output gradient."
                else -> "With skips, layer 1 gets {$r1×} the output gradient, not $p1."
            },
            when (tab) {
                0 -> "Each layer's gain is under 1 at this init, so 19 of them multiply the signal away. The first layers barely train."
                else -> "The identity path adds 1 to every layer's derivative, so the signal can't be multiplied away. Unnormalised, it grows instead; real ResNets add BatchNorm."
            },
        ),
        DkFrame(
            header, plot(true), legend + DkLegend(DkInk.Blue, SwatchStyle.DashedLine, "Residual, branch × 0.2"),
            listOf("h ← h + 0.2·F(h)", "layer 1: {${mag(scaled[0])}}"),
            "Shrink each branch and layer 1 gets {${mag(scaled[0])}}: no vanishing, no explosion.",
            "Each block starts near the identity, which is what BatchNorm's learned scale or a zero-initialised last layer gives a real ResNet. That's how 152 layers train.",
        ),
    )
    stepActions(frames, "Next")
}

// ── DenseNet: concatenation ──

private fun densenetLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val k = 32
    val input = 64
    val layers = 6
    val inks = listOf(DkInk.Blue, DkInk.Violet, DkInk.Green, DkInk.Orange, DkInk.Pink, DkInk.Sky)
    fun inCh(l: Int) = input + (l - 1) * k
    fun params(l: Int) = inCh(l) * 4 * k + 9 * 4 * k * k
    val blockParams = (1..layers).sumOf { params(it) }
    val connections = (1..layers).sum()
    fun stage(hot: Int): DkSegs = DkSegs(
        (1..layers + 1).map { l ->
            val sources = if (l > layers) layers else l - 1
            DkSegRow(
                listOf(DkSeg(input.toDouble(), DkInk.Slate)) + (0 until sources).map { DkSeg(k.toDouble(), inks[it], dim = l != hot) },
                label = if (l > layers) "out" else "L$l", hot = l == hot,
            )
        },
        scale = (input + layers * k).toDouble() * 1.04, barHeight = 20,
    )
    fun legendFor(hot: Int) = listOf(legend(DkInk.Slate, "block input $input")) +
        (0 until min(hot - 1, layers)).map { legend(inks[it], if (it == 0) "from L1" else "L${it + 1}") }
    val chips = listOf(DkChip("connections", "$connections", tint = true), DkChip("block params", count(blockParams.toDouble())))
    val header = "input channels per layer, coloured by source · k = $k"
    val shown = listOf(2, 4, 6, 7)
    val frames = shown.map { l ->
        val isOut = l > layers
        DkFrame(
            header, stage(l), legendFor(l),
            if (isOut) listOf("out: $input + $layers·$k = {${input + layers * k}} channels", "block params: {${count(blockParams.toDouble())}}")
            else listOf("L$l in: $input + ${l - 1}·$k = {${inCh(l)}} channels", "params: ${inCh(l)}·${4 * k} + 9·${4 * k}·$k = {${count(params(l).toDouble())}}"),
            when (l) {
                2 -> "Layer 2 takes the input plus {layer 1's} $k channels."
                4 -> "Layer 4 concatenates {all three} earlier outputs with the input."
                6 -> "Layer 6 reads {${inCh(6)}} channels: everything before it."
                else -> "The block outputs {${input + layers * k}} channels: input plus 6 × $k."
            },
            when (l) {
                2 -> "Instead of adding like a ResNet, DenseNet stacks feature maps side by side, so nothing earlier is overwritten."
                4 -> "Each layer adds only $k channels, so the block stays small: ${count(blockParams.toDouble())} weights for $connections direct connections."
                6 -> "A 1×1 bottleneck squeezes those ${inCh(6)} channels to ${4 * k} before the 3×3, so wide inputs stay cheap."
                else -> "A transition layer then halves the channels and the map before the next block."
            },
            chips,
        )
    }
    stepActions(frames, "Next Layer")
}

// ── MobileNet: depthwise separable ──

private val mobileNs = listOf(64, 256, 1024)

private fun mobilenetLab(): DkLab = DkLab(DkControl.Tabs, tabs = mobileNs.map { "N = $it" }, initialTab = 1) { tab, _ ->
    val nCh = mobileNs[tab]
    val hw = 14 * 14
    val std = hw.toDouble() * nCh * nCh * 9
    val dw = hw.toDouble() * nCh * 9
    val pw = hw.toDouble() * nCh * nCh
    val ratio = 1.0 / nCh + 1.0 / 9
    val pwShare = pw / (dw + pw)
    fun stage(showDw: Boolean, showPw: Boolean) = DkSegs(
        listOfNotNull(
            DkSegRow(listOf(DkSeg(std, DkInk.Orange)), caption = "standard 3×3 conv", value = count(std)),
            if (showDw) DkSegRow(
                listOf(DkSeg(dw, DkInk.Blue)) + if (showPw) listOf(DkSeg(pw, DkInk.Green)) else emptyList(),
                caption = if (showPw) "depthwise 3×3 + pointwise 1×1" else "depthwise 3×3",
                value = count(if (showPw) dw + pw else dw),
            ) else null,
        ),
        scale = std,
        notes = if (showPw) listOf("depthwise ${count(dw)} · pointwise ${count(pw)}", "{pointwise is ${pct(pwShare, 0)} of what's left}") else emptyList(),
        barHeight = 34,
    )
    val header = "MACs for one layer · 14×14, $nCh → $nCh"
    val legend = listOf(legend(DkInk.Orange, "Standard"), legend(DkInk.Blue, "Depthwise"), legend(DkInk.Green, "Pointwise 1×1"))
    val frames = listOf(
        DkFrame(
            header, stage(false, false), legend.take(1), listOf("14·14 · $nCh·$nCh · 9 = {${count(std)}}", "every filter reads every channel"),
            "A standard 3×3 conv costs {${count(std)}} MACs here.",
            "Each of its $nCh filters looks at all $nCh input channels at 9 positions: filtering and channel mixing in one step.",
        ),
        DkFrame(
            header, stage(true, false), legend.take(2), listOf("depthwise: 14·14 · $nCh · 9 = {${count(dw)}}", "one 3×3 filter per channel"),
            "Depthwise filters each channel {alone}: ${count(dw)} MACs.",
            "It does the spatial filtering but never mixes channels, which is $nCh× cheaper and not enough on its own.",
        ),
        DkFrame(
            header, stage(true, true), legend,
            listOf("ratio = 1/N + 1/k² = 1/$nCh + 1/9 = {${n(ratio, 4)}}", "→ ${n(1 / ratio, 1)}× cheaper, same output shape"),
            "Splitting the conv makes it {${n(1 / ratio, 1)}×} cheaper.",
            "Depthwise filters each channel alone; the 1×1 mixes them. Nearly all remaining cost is the 1×1, so MobileNet's speed depends on fast pointwise convs.",
        ),
    )
    stepActions(frames, "Next")
}

// ── EfficientNet: compound scaling ──

private const val EffA = 1.2
private const val EffB = 1.1
private const val EffG = 1.15

private fun efficientnetLab(): DkLab =
    DkLab(DkControl.StepperOnly, stepper = DkStepper("φ", (0..7).map { it.toDouble() }, 3) { n(it, 0) }) { _, p ->
        val phi = p.toDouble()
        val d = EffA.pow(phi)
        val w = EffB.pow(phi)
        val r = EffG.pow(phi)
        val px = (224 * r).let { floor(it + 0.5).toInt() }
        val base = EffA * EffB * EffB * EffG * EffG
        val flops = base.pow(phi)
        val maxD = EffA.pow(7.0)
        val phiT = n(phi, 0)
        fun rows(hot: Int?) = DkRows(
            listOf(
                DkRow("depth α^φ", "", listOf(DkBar(d / maxD, DkInk.Blue, "${n(d)}×")), hot = hot == 0),
                DkRow("width β^φ", "", listOf(DkBar(w / maxD, DkInk.Orange, "${n(w)}×")), hot = hot == 1),
                DkRow("resolution γ^φ", "", listOf(DkBar(r / maxD, DkInk.Green, "${n(r)}× → $px px")), hot = hot == 2),
            ),
        )
        val header = "scale factors from B0 at φ = $phiT"
        val legend = listOf(legend(DkInk.Blue, "Depth"), legend(DkInk.Orange, "Width"), legend(DkInk.Green, "Resolution"))
        listOf(
            DkFrame(
                header, rows(null), legend, listOf("α = 1.2, β = 1.1, γ = 1.15, found on B0", "φ = $phiT: depth ×${n(d)}, width ×${n(w)}, resolution ×${n(r)}"),
                "At φ = $phiT the network is {${n(d)}×} deeper, ${n(w)}× wider and sees ${px}px images.",
                "One knob grows all three dimensions together instead of tuning each by hand.",
            ),
            DkFrame(
                header, rows(null), legend, listOf("α·β²·γ² = 1.2·1.1²·1.15² = ${n(base, 3)} ≈ 2", "FLOPs × ${n(base, 3)}${if (p == 1) "" else "⁰¹²³⁴⁵⁶⁷"[p].toString()} = {${n(flops)}×}"),
                "φ = $phiT costs {${n(flops, 1)}×} the FLOPs of B0.",
                "α, β, γ are fixed so each step of φ roughly doubles compute; width and resolution count twice because FLOPs scale with their square.",
            ),
            DkFrame(
                header, rows(0), legend, listOf("same budget on depth alone: ×${n(flops)} layers", "accuracy gains flatten past a few × depth"),
                "Spent on depth alone, the same budget makes a {${n(flops, 1)}×} deeper net.",
                "Very deep, narrow, low-resolution nets gain little per FLOP. The paper found that balancing all three wins at every budget.",
            ),
        ).let { frames -> withActions(frames) { "Next" } }
    }

// ── Vision Transformers: patches as tokens ──

private val vitPatches = listOf(32, 16, 8)

private fun vitLab(): DkLab = DkLab(DkControl.Tabs, tabs = vitPatches.map { "$it px" }, initialTab = 1) { tab, _ ->
    val ps = vitPatches[tab]
    val side = 224 / ps
    val patches = side * side
    val tokens = patches + 1
    val dim = ps * ps * 3
    val weights = dim.toDouble() * 768 + 768
    val pairs = tokens.toDouble() * tokens
    val ref = (196 + 1).toDouble().pow(2)
    fun stage(done: Int, current: Int?) = DkGrids(
        listOf(
            listOf(
                DkGrid(
                    "", side, side,
                    List(patches) { i ->
                        when {
                            i == current -> DkCell("", DkCellTone.Hot)
                            i < done -> DkCell("", DkCellTone.Embedded)
                            else -> DkCell("", DkCellTone.Empty)
                        }
                    },
                    maxCell = 22f,
                ),
            ),
        ),
        listOf(1f),
    )
    val mid = min(patches - 1, patches * 37 / 196)
    val header = "224×224 image · $ps×$ps patches"
    val legend = listOf(legend(DkInk.Blue, "Embedded"), legend(DkInk.Yellow, "Current patch"), legend(DkInk.Slate, "Waiting"))
    val frames = listOf(
        DkFrame(
            header, stage(0, null), legend.drop(2), listOf("224 / $ps = $side per side → $side·$side = {$patches} patches"),
            "Cut the image into {$patches} patches of $ps×$ps.",
            "A transformer reads a sequence, so the image becomes a sequence of patches, read row by row.",
        ),
        DkFrame(
            header, stage(0, 0), legend.drop(1), listOf("patch: $ps·$ps·3 = {$dim} numbers", "flattened into one vector"),
            "The first patch becomes {$dim numbers}.",
            "Its pixels are unrolled into a flat vector, losing nothing.",
        ),
        DkFrame(
            header, stage(mid, mid), legend,
            listOf("patch: $ps·$ps·3 = $dim → linear → 768 · shared ${count(weights)} weights", "tokens: $patches + 1 class = {$tokens} · attention pairs ${comma(pairs)}"),
            "Every patch goes through the {same} linear layer.",
            "No convolution: $tokens tokens enter a standard transformer.",
        ),
        DkFrame(
            header, stage(patches, null), legend.take(1), listOf("pairs = tokens² = $tokens² = {${comma(pairs)}}", "16 px reference: 38,809"),
            "Attention compares all {${comma(pairs)}} token pairs.",
            when (tab) {
                0 -> "Big patches are cheap: ${n(ref / pairs, 0)}× fewer pairs than 16 px, but each token is a coarse 32×32 block."
                1 -> "This is ViT-B/16: halving the patch size would give 4× the tokens and 16× the attention cost."
                else -> "Small patches see finer detail, but $tokens tokens cost ${n(pairs / ref, 0)}× the attention of 16 px."
            },
        ),
    )
    stepActions(frames, "Embed Next")
}

// ── Transfer learning: what to unfreeze ──

private fun transferLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Head only", "+ layer4", "All")) { tab, _ ->
    class Group(val name: String, val params: Double)
    val groups = listOf(
        Group("conv1 + bn · edges", 9536.0), Group("layer1 · textures", 147968.0), Group("layer2 · patterns", 525568.0),
        Group("layer3 · parts", 2099712.0), Group("layer4 · objects", 8393728.0),
    )
    val head = 512.0 * 10 + 10
    val oldHead = 512.0 * 1000 + 1000
    val backbone = groups.sumOf { it.params }
    val total = backbone + head
    val top = groups.maxOf { it.params }
    fun training(i: Int) = when (tab) {
        0 -> false
        1 -> i == 4
        else -> true
    }
    fun rows(newHead: Boolean, showTraining: Boolean) = DkRows(
        groups.mapIndexed { i, g ->
            val on = showTraining && training(i)
            DkRow(g.name, if (on) "training" else "frozen", listOf(DkBar(g.params / top, if (on) DkInk.Yellow else DkInk.Slate, count(g.params))), hot = on)
        } + DkRow(
            if (newHead) "fc head · 10 classes" else "fc head · 1000 classes", if (newHead) "training" else "frozen",
            listOf(DkBar((if (newHead) head else oldHead) / top, if (newHead) DkInk.Yellow else DkInk.Slate, count(if (newHead) head else oldHead))),
            hot = newHead,
        ),
    )
    val trainable = head + groups.filterIndexed { i, _ -> training(i) }.sumOf { it.params }
    val share = trainable / total
    val legend = listOf(legend(DkInk.Slate, "Frozen"), legend(DkInk.Yellow, "Training"))
    val frames = listOf(
        DkFrame(
            "ResNet-18 · ImageNet weights", rows(false, false), legend.take(1),
            listOf("${count(backbone + oldHead)} weights, trained on 1.28M images", "1000 ImageNet classes"),
            "A pretrained ResNet-18 already detects {edges to objects}.",
            "Early layers find generic edges and textures; later ones find parts and whole objects of ImageNet's classes.",
        ),
        DkFrame(
            "ResNet-18 · ImageNet weights, new 10-class head", rows(true, false), legend,
            listOf("old fc: 512·1000 + 1000 = ${comma(oldHead)}", "new fc: 512·10 + 10 = {${comma(head)}}"),
            "Swap the 1000-class head for a {10-class} one.",
            "The new head starts random; everything below it keeps its ImageNet weights.",
        ),
        DkFrame(
            "ResNet-18 · ImageNet weights, new 10-class head", rows(true, true), legend,
            when (tab) {
                0 -> listOf("fc: 512·10 + 10 = {${comma(head)}} trainable", "of ${count(total)} total = ${pct(share, 2)}")
                1 -> listOf("layer4 + fc = {${count(trainable)}} trainable", "of ${count(total)} total = ${pct(share)}")
                else -> listOf("all {${count(total)}} weights trainable", "at a learning rate ~10× below training from scratch")
            },
            when (tab) {
                0 -> "Only {${pct(share, 2)}} of the weights are trained."
                1 -> "Unfreezing layer4 trains {${pct(share)}} of the weights."
                else -> "Fine-tuning everything trains all {${count(total)}}."
            },
            when (tab) {
                0 -> "The backbone's edges and textures already fit the new task, so 10 classes can be learned from a few hundred images."
                1 -> "Its object-level features adapt to the new classes while edges and textures stay fixed. Use a smaller learning rate than the head's."
                else -> "It needs thousands of images per class, or the small dataset gets memorised. Usually done last, with a tiny learning rate."
            },
        ),
        DkFrame(
            "ResNet-18 · ImageNet weights, new 10-class head", rows(true, true), legend,
            listOf("head only: ~100s of images", "+ layer4: ~1,000s · all: ~10,000s (rule of thumb)"),
            "More trainable weights need {more data}.",
            "Start with the head, then unfreeze from the top down while validation accuracy keeps improving.",
        ),
    )
    stepActions(frames, "Next")
}
