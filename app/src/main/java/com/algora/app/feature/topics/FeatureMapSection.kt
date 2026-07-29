package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

// ── Feature map player ───────────────────────────────────────────────────────
// The CNN labs (C3, and object detection in C4). Four render parts, mixed per frame:
//
//   grids    — an input, a kernel and the feature map it produces, with the current window outlined
//   stack    — a layer table: shape, parameter count, and each layer's share of the total
//   bars     — a labelled vector (channels per dense layer, cost per branch)
//   plot     — labelled curves on shared axes, optionally log-y (gradient survival, scaling laws)
//
// Everything drawn is computed in CnnMath.kt: the convolution is really run, the parameter counts
// are summed from layer tables rather than quoted, and the three mechanics labs rebuild every frame
// from the kernel/stride/padding the user picked, so the arithmetic on screen is the arithmetic of
// the controls rather than of a recorded default.

internal data class FmControls(val kernel: Int = 3, val stride: Int = 1, val padding: Int = 0)

private enum class GridTone { SIGNED, COUNT, HEAT }

private class FmGrid(
    val label: String,
    val values: List<List<Double>>,
    val highlight: Set<Pair<Int, Int>> = emptySet(),
    val tone: GridTone = GridTone.SIGNED,
)

private class FmStackRow(
    val name: String,
    val shape: String,
    val params: Long,
    val note: String = "",
    val emphasis: Boolean = false,
)

private class FmBar(val label: String, val values: List<Double>, val color: Color, val captions: List<String> = emptyList())

private class FmCurve(val label: String, val points: List<Pair<Float, Float>>, val color: Color)

private class FmPlot(
    val label: String,
    val curves: List<FmCurve>,
    val xRange: ClosedFloatingPointRange<Float>,
    val yRange: ClosedFloatingPointRange<Float>,
    /** When set, y values are already log10 and the axis caption says so. */
    val logY: Boolean = false,
)

private class FmFrame(
    val status: String,
    val grids: List<FmGrid> = emptyList(),
    val stack: List<FmStackRow> = emptyList(),
    val bars: List<FmBar> = emptyList(),
    val plot: FmPlot? = null,
    val readout: String? = null,
)

private class FmConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    /** Mechanics labs expose the kernel/stride/padding controls; architecture labs have nothing to vary. */
    val controls: Boolean = false,
    val build: (FmControls) -> List<FmFrame>,
)

private val WindowColor = Color(0xFF7C3AED)
private val SignalColor = SimColors.Blue
private val NegativeColor = Color(0xFFEC4899)
private val SavingColor = SimColors.Green
private val CostColor = Color(0xFFF97316)
private val MutedColor = Color(0xFF94A3B8)

private fun Long.compact(): String = when {
    this >= 1_000_000_000L -> "%.2fB".format(this / 1e9)
    this >= 1_000_000L -> "%.2fM".format(this / 1e6)
    this >= 10_000L -> "%.1fk".format(this / 1e3)
    else -> toString()
}

private fun percent(part: Long, whole: Long): String =
    if (whole == 0L) "0%" else "%.1f%%".format(100.0 * part / whole)

private fun window(row: Int, col: Int, kernel: Int, stride: Int, padding: Int, size: Int): Set<Pair<Int, Int>> =
    (0 until kernel).flatMap { kr ->
        (0 until kernel).map { kc -> (row * stride + kr - padding) to (col * stride + kc - padding) }
    }.filter { (r, c) -> r in 0 until size && c in 0 until size }.toSet()

// A 7×7 patch with a vertical edge down the middle and a horizontal bar across it, so a vertical
// edge detector has somewhere to fire and somewhere to stay silent.
private val samplePatch: List<List<Double>> = List(7) { r ->
    List(7) { c -> if (r == 3) 1.0 else if (c >= 4) 1.0 else 0.0 }
}

/** A vertical-edge (Sobel-style) detector at the requested odd size; 1×1 degenerates to identity. */
private fun edgeKernel(size: Int): List<List<Double>> {
    if (size == 1) return listOf(listOf(1.0))
    val mid = size / 2
    return List(size) { r ->
        List(size) { c ->
            val weight = if (r == mid) 2.0 else 1.0
            when {
                c < mid -> weight
                c > mid -> -weight
                else -> 0.0
            }
        }
    }
}

private fun stackRows(layers: List<CnnLayer>, upTo: Int, total: Long): List<FmStackRow> =
    layers.take(upTo).mapIndexed { index, layer ->
        FmStackRow(
            name = layer.name,
            shape = layer.shape,
            params = layer.params,
            note = if (layer.params == 0L) "no parameters" else "${percent(layer.params, total)} of the model",
            emphasis = index == upTo - 1,
        )
    }

// ── Convolution layers ───────────────────────────────────────────────────────

private fun convolutionFrames(controls: FmControls): List<FmFrame> {
    val (k, s, p) = controls
    val image = samplePatch
    val n = image.size
    val kernel = edgeKernel(k)
    val out = convOutputSize(n, k, s, p)
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = "A dense layer on this $n×$n patch needs ${n * n} weights for every unit, and each weight is tied " +
            "to one pixel position. A convolution slides ${k * k} weights over every position instead — the same " +
            "detector, wherever the thing it detects happens to be.",
        grids = listOf(FmGrid("input $n×$n", image), FmGrid("kernel $k×$k", kernel)),
        readout = "output ⌊($n + 2·$p − $k)/$s⌋ + 1 = $out",
    )

    if (out == 0) {
        frames += FmFrame(
            status = "With k = $k, stride $s and padding $p the window does not fit inside a $n×$n input at all: " +
                "$n + 2·$p is less than $k. The layer cannot be built — the geometry has to be fixed before the " +
                "weights matter.",
            grids = listOf(FmGrid("input $n×$n", image)),
        )
        return frames
    }

    val feature = conv2d(image, kernel, s, p)
    val positions = listOf(0 to 0, 0 to out / 2, out / 2 to out / 2, out - 1 to out - 1).distinct().take(4)
    positions.forEach { (r, c) ->
        val partial = List(out) { rr -> List(out) { cc -> if (rr < r || (rr == r && cc <= c)) feature[rr][cc] else 0.0 } }
        val value = feature[r][c]
        frames += FmFrame(
            status = "Window at output ($r, $c) reads input rows ${r * s - p}–${r * s - p + k - 1}: multiply the " +
                "${k * k} overlapping pixels by the kernel and sum → ${"%.1f".format(value)}." +
                if (abs(value) > 0.5) " Strong response; the edge runs through this window." else " Flat here, so nothing fires.",
            grids = listOf(
                FmGrid("input $n×$n", image, highlight = window(r, c, k, s, p, n)),
                FmGrid("kernel $k×$k", kernel),
                FmGrid("feature map $out×$out", partial, highlight = setOf(r to c)),
            ),
        )
    }

    val convWeights = (k * k + 1).toLong()
    val denseWeights = (n * n + 1).toLong() * (out * out)
    frames += FmFrame(
        status = "The finished map. Those ${k * k} weights plus a bias were reused ${out * out} times; a dense layer " +
            "producing the same ${out * out} outputs would hold ${denseWeights.compact()} parameters. Weight sharing " +
            "is the entire saving, and translation equivariance is what you get for free with it.",
        grids = listOf(FmGrid("feature map $out×$out", feature)),
        readout = "conv $convWeights params · dense ${denseWeights.compact()} params · " +
            "${"%.0f".format(denseWeights.toDouble() / convWeights)}× fewer",
    )

    val geometry = List(3) { LayerGeometry(k, s) }
    val rf = receptiveField(geometry)
    frames += FmFrame(
        status = "Stack three of these layers and the receptive field grows to ${rf.last()} input pixels — each " +
            "output still touches only ${k * k} inputs of the layer below it. Depth is how a convolution stops being " +
            "local without ever using a large kernel.",
        bars = listOf(
            FmBar(
                "receptive field after layer 1 · 2 · 3 (input pixels)",
                rf.map { it.toDouble() },
                SignalColor,
                rf.map { "$it px" },
            ),
        ),
        readout = "rᵢ = rᵢ₋₁ + (k − 1)·∏s = ${rf.joinToString(" → ")}",
    )
    return frames
}

// ── Pooling ──────────────────────────────────────────────────────────────────

private fun poolingFrames(controls: FmControls): List<FmFrame> {
    // A localised 3×3 square on a 9×9 field, not the convolution lab's edge: pooling is a claim
    // about what survives a shift, and a response that repeats down every column is invariant to
    // shifts for reasons that have nothing to do with pooling.
    val image = List(9) { r -> List(9) { c -> if (r in 3..5 && c in 3..5) 1.0 else 0.0 } }
    val kernel = edgeKernel(3)
    val feature = conv2d(image, kernel)
    val size = controls.kernel.coerceIn(2, 3)
    val maxed = pool2d(feature, size, size, PoolMode.MAX)
    val averaged = pool2d(feature, size, size, PoolMode.AVERAGE)
    val agreement = shiftAgreement(image, kernel, poolSize = size)
    val twoPixel = shiftAgreement(image, kernel, poolSize = size, shift = 2)
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = "Start from a real feature map: the ${feature.size}×${feature.size} response of a vertical edge " +
            "detector to a small bright square. Pooling never looks at the image — it summarises what a layer " +
            "already found.",
        grids = listOf(FmGrid("input 9×9", image), FmGrid("feature map ${feature.size}×${feature.size}", feature)),
    )
    frames += FmFrame(
        status = "Max pooling over $size×$size blocks keeps the strongest response in each block and throws away " +
            "where inside the block it was. The map shrinks from ${feature.size}² to ${maxed.size}² — a " +
            "${"%.1f".format(feature.size * feature.size.toDouble() / (maxed.size * maxed.size))}× drop in " +
            "activations carried to the next layer — and it has no parameters to learn.",
        grids = listOf(FmGrid("max pool $size×$size", maxed, highlight = setOf(0 to 0))),
        readout = "${feature.size}×${feature.size} → ${maxed.size}×${maxed.size}, 0 parameters",
    )
    frames += FmFrame(
        status = "Average pooling over the same blocks answers a different question: not \"was the feature here?\" " +
            "but \"how much of it was here?\". Averages dilute a single strong response, which is why max won for " +
            "detection and average survives as global average pooling at the top of a network.",
        grids = listOf(FmGrid("max pool", maxed), FmGrid("average pool", averaged)),
    )
    frames += FmFrame(
        status = "The invariance claim, measured rather than asserted. Shift the image one pixel right: the raw " +
            "response moves by ${"%.0f".format(agreement.relativeChangeRaw * 100)}% of its own magnitude, and the " +
            "$size×$size pooled map by ${"%.0f".format(agreement.relativeChangePooled * 100)}%. Shift it two pixels " +
            "and the pooled map moves ${"%.0f".format(twoPixel.relativeChangePooled * 100)}% against the raw map's " +
            "${"%.0f".format(twoPixel.relativeChangeRaw * 100)}%. Pooling halves the sensitivity; it does not remove " +
            "it, and the gap closes as the shift grows past the window. Real invariance is built by stacking layers " +
            "of this, not by making one window larger.",
        bars = listOf(
            FmBar(
                "response change after a shift (relative to map magnitude)",
                listOf(agreement.relativeChangeRaw, agreement.relativeChangePooled, twoPixel.relativeChangeRaw, twoPixel.relativeChangePooled),
                CostColor,
                listOf("1px raw", "1px pooled", "2px raw", "2px pooled"),
            ),
        ),
        readout = "1-pixel shift: |Δ| ${"%.0f".format(agreement.relativeChangeRaw * 100)}% → " +
            "${"%.0f".format(agreement.relativeChangePooled * 100)}% · cells unchanged " +
            "${"%.0f".format(agreement.unchangedRaw * 100)}% → ${"%.0f".format(agreement.unchangedPooled * 100)}%",
    )
    val strided = conv2d(image, kernel, stride = 2)
    frames += FmFrame(
        status = "The modern alternative: a stride-2 convolution downsamples in the same step as it filters, so the " +
            "network learns how to reduce instead of being told. ResNet still pools; many architectures after it " +
            "dropped pooling entirely except for the global average at the end.",
        grids = listOf(FmGrid("stride-2 conv ${strided.size}×${strided.size}", strided), FmGrid("max pool", maxed)),
    )
    return frames
}

// ── Padding and strides ──────────────────────────────────────────────────────

private fun paddingStrideFrames(controls: FmControls): List<FmFrame> {
    val (k, s, p) = controls
    val image = samplePatch
    val n = image.size
    val kernel = edgeKernel(k)
    val out = convOutputSize(n, k, s, p)
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = "One formula governs every convolution's output size: ⌊(n + 2p − k)/s⌋ + 1. With n = $n, k = $k, " +
            "s = $s and p = $p that is $out. Getting it wrong is the single most common shape error in a vision model.",
        grids = listOf(FmGrid("input $n×$n", image), FmGrid("kernel $k×$k", kernel)),
        readout = "⌊($n + 2·$p − $k)/$s⌋ + 1 = $out",
    )

    val counts = windowCounts(n, k, s, p)
    val corner = counts[0][0]
    val centre = counts[n / 2][n / 2]
    frames += FmFrame(
        status = "How many windows each pixel is read by. The corner is used $corner time${if (corner == 1) "" else "s"} " +
            "and the centre $centre — with p = 0 the border of every image is systematically under-weighted before a " +
            "single weight is learned, and after ten layers the neglected band is ten pixels wide.",
        grids = listOf(FmGrid("reads per pixel", counts.map { row -> row.map { it.toDouble() } }, tone = GridTone.COUNT)),
        readout = "corner $corner · centre $centre reads",
    )

    val same = samePadding(k)
    val sameOut = convOutputSize(n, k, 1, same)
    frames += FmFrame(
        status = "\"Same\" padding is p = (k − 1)/2 = $same at stride 1, which puts the output back at " +
            "$sameOut×$sameOut. It is not a separate operation — it is this formula solved for p. \"Valid\" padding " +
            "means p = 0 and accepting the shrinkage.",
        grids = listOf(FmGrid("input padded to ${n + 2 * same}×${n + 2 * same}", padImage(image, same))),
        readout = "valid ${convOutputSize(n, k, 1, 0)}×${convOutputSize(n, k, 1, 0)} · same $sameOut×$sameOut",
    )

    var unpadded = 224
    val shrink = (1..10).map { unpadded = convOutputSize(unpadded, k, 1, 0); unpadded }
    frames += FmFrame(
        status = "Ten unpadded $k×$k layers on a 224×224 input leave ${shrink.last()}×${shrink.last()}: each layer " +
            "costs k − 1 = ${k - 1} pixels. VGG pads every layer for exactly this reason — the depth was the point, " +
            "and unpadded depth eats the image.",
        plot = FmPlot(
            "map size through ten unpadded layers",
            listOf(FmCurve("size", shrink.mapIndexed { i, v -> (i + 1).toFloat() to v.toFloat() }, CostColor)),
            xRange = 1f..10f,
            yRange = 0f..224f,
        ),
        readout = "224 → ${shrink.last()} after 10 layers",
    )

    val strides = (1..4).map { it to convOutputSize(n, k, it, p) }
    frames += FmFrame(
        status = "Stride is the other lever, and it divides rather than subtracts: ${strides.joinToString(", ") { (st, o) -> "s = $st → $o×$o" }}. " +
            "Stride 2 is a halving, which is why it replaced pooling in most architectures after ResNet.",
        bars = listOf(
            FmBar(
                "output size by stride",
                strides.map { it.second.toDouble() },
                SignalColor,
                strides.map { "s=${it.first}" },
            ),
        ),
    )
    return frames
}

// ── Architecture walkers ─────────────────────────────────────────────────────

private fun architectureFrames(
    layers: List<CnnLayer>,
    opening: String,
    closing: (Long, Long) -> String,
    extras: (Long, Long) -> List<FmFrame> = { _, _ -> emptyList() },
): List<FmFrame> {
    val total = layers.totalParams()
    val macs = layers.totalMacs()
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = opening,
        stack = stackRows(layers, 1, total),
        readout = "input → ${layers.first().shape}",
    )
    for (i in 2..layers.size) {
        val layer = layers[i - 1]
        frames += FmFrame(
            status = "${layer.name} → ${layer.shape}. " + when (layer.kind) {
                LayerKind.CONV -> "${layer.params.compact()} parameters (${percent(layer.params, total)} of the model), " +
                    "${layer.macs.compact()} multiply-accumulates (${percent(layer.macs, macs)} of the compute)."
                LayerKind.POOL -> "No parameters and no learning: the map is halved and passed on."
                LayerKind.DENSE -> "${layer.params.compact()} parameters — ${percent(layer.params, total)} of the whole " +
                    "model in one layer, for ${percent(layer.macs, macs)} of the compute."
            },
            stack = stackRows(layers, i, total),
        )
    }

    frames += FmFrame(
        status = closing(total, macs),
        stack = stackRows(layers, layers.size, total),
        bars = listOf(
            FmBar(
                "parameters · convolutions vs dense layers",
                listOf(layers.paramsIn(LayerKind.CONV).toDouble(), layers.paramsIn(LayerKind.DENSE).toDouble()),
                SignalColor,
                listOf("conv ${percent(layers.paramsIn(LayerKind.CONV), total)}", "dense ${percent(layers.paramsIn(LayerKind.DENSE), total)}"),
            ),
            FmBar(
                "multiply-accumulates · convolutions vs dense layers",
                listOf(layers.macsIn(LayerKind.CONV).toDouble(), layers.macsIn(LayerKind.DENSE).toDouble()),
                CostColor,
                listOf("conv ${percent(layers.macsIn(LayerKind.CONV), macs)}", "dense ${percent(layers.macsIn(LayerKind.DENSE), macs)}"),
            ),
        ),
        readout = "${total.compact()} parameters · ${macs.compact()} MACs per image",
    )
    frames += extras(total, macs)
    return frames
}

private fun lenetFrames(): List<FmFrame> = architectureFrames(
    layers = lenet5(),
    opening = "LeNet-5, 1998, reading a 32×32 greyscale digit. Every idea a modern vision model uses is already " +
        "here: local receptive fields, shared weights, subsampling, and a classifier on top of learned features.",
    closing = { total, macs ->
        "${total} parameters in total — sixty thousand, on hardware from 1998, and it read cheques in production for " +
            "a decade. ${macs.compact()} multiply-accumulates per digit. The architecture was right; what was " +
            "missing was data and compute, which is the whole story of the fourteen years to AlexNet."
    },
)

private fun alexNetFrames(): List<FmFrame> = architectureFrames(
    layers = alexNet(),
    opening = "AlexNet, 2012, on 227×227 colour. It won ImageNet by 10.8 percentage points and restarted the field. " +
        "Structurally it is LeNet made deep and wide — the changes that mattered were ReLU, dropout, augmentation " +
        "and two GPUs.",
    closing = { total, macs ->
        "${total.compact()} parameters, ${macs.compact()} MACs. Read the split: the three dense layers hold " +
            "${percent(alexNet().paramsIn(LayerKind.DENSE), total)} of the parameters and do " +
            "${percent(alexNet().macsIn(LayerKind.DENSE), macs)} of the arithmetic. Almost the whole model is a " +
            "classifier bolted onto a small feature extractor — which is exactly what later architectures deleted."
    },
    extras = { total, _ ->
        listOf(
            FmFrame(
                status = "fc6 alone is ${denseParams(9216, 4096).compact()} parameters, because flattening a " +
                    "6×6×256 map into 9,216 features and connecting all of them to 4,096 units is the most " +
                    "expensive thing you can do with a feature map. Dropout at p = 0.5 on these two layers was not " +
                    "a refinement; without it the model memorised the training set.",
                bars = listOf(
                    FmBar(
                        "parameters per dense layer",
                        listOf(denseParams(9216, 4096).toDouble(), denseParams(4096, 4096).toDouble(), denseParams(4096, 1000).toDouble()),
                        NegativeColor,
                        listOf("fc6", "fc7", "fc8"),
                    ),
                ),
                readout = "fc6 = ${percent(denseParams(9216, 4096), total)} of AlexNet",
            ),
        )
    },
)

private fun vggFrames(): List<FmFrame> = architectureFrames(
    layers = vgg16(),
    opening = "VGG-16, 2014. One decision, applied everywhere: every convolution is 3×3, stride 1, padded; every " +
        "pool halves the map; channels double after each pool. The paper's contribution is that depth alone, held " +
        "at the smallest useful kernel, keeps improving accuracy.",
    closing = { total, macs ->
        "${total.compact()} parameters and ${macs.compact()} MACs per image. The conv stack — thirteen layers of it — " +
            "is only ${percent(vgg16().paramsIn(LayerKind.CONV), total)} of the parameters and " +
            "${percent(vgg16().macsIn(LayerKind.CONV), macs)} of the compute. VGG is a small, extremely expensive " +
            "feature extractor wearing a huge, nearly free classifier."
    },
    extras = { _, _ ->
        val stacked = 2L * convParams(512, 512, 3, bias = false)
        val single = convParams(512, 512, 5, bias = false)
        listOf(
            FmFrame(
                status = "Why 3×3 and never 5×5: two stacked 3×3 layers see the same 5×5 receptive field, with " +
                    "${stacked.compact()} parameters against ${single.compact()} at 512 channels — 28% fewer — and " +
                    "a non-linearity in between that the single large kernel does not have. Three stacked 3×3s " +
                    "reach 7×7 the same way.",
                bars = listOf(
                    FmBar(
                        "parameters for a 5×5 receptive field at 512 channels",
                        listOf(stacked.toDouble(), single.toDouble()),
                        SavingColor,
                        listOf("two 3×3", "one 5×5"),
                    ),
                ),
                readout = "receptive field ${receptiveField(listOf(LayerGeometry(3, 1), LayerGeometry(3, 1))).last()}×" +
                    "${receptiveField(listOf(LayerGeometry(3, 1), LayerGeometry(3, 1))).last()} either way",
            ),
        )
    },
)

// ── Inception ────────────────────────────────────────────────────────────────

private fun inceptionFrames(): List<FmFrame> {
    val branches = inception3a()
    val naive = branches.sumOf { it.naiveMacs }
    val reduced = branches.sumOf { it.reducedMacs }
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = "Inception's question is not \"how deep\" but \"which kernel size\" — and its answer is to stop " +
            "choosing. One module runs 1×1, 3×3, 5×5 and a pooling branch over the same input and concatenates the " +
            "results, so the network picks the scale per feature rather than per layer.",
        stack = branches.map { FmStackRow(it.name, "28×28×${it.outChannels}", it.reducedMacs, "cost per position") },
        readout = "output ${branches.sumOf { it.outChannels }} channels, all four scales concatenated",
    )
    frames += FmFrame(
        status = "Done naively this is unaffordable. The 5×5 branch alone costs ${branches[2].naiveMacs.compact()} " +
            "multiply-accumulates per position at 192 input channels, and stacking modules multiplies the input " +
            "width, so cost grows quadratically with depth.",
        bars = listOf(
            FmBar(
                "naive cost per branch (MACs per position)",
                branches.map { it.naiveMacs.toDouble() },
                CostColor,
                branches.map { it.name.substringBefore(" →") },
            ),
        ),
        readout = "naive module: ${naive.compact()} MACs per position",
    )
    frames += FmFrame(
        status = "The fix is a 1×1 convolution in front of each expensive kernel. A 1×1 has no spatial extent at " +
            "all — it is a learned linear map across channels — so it can compress 192 channels to 16 before the " +
            "5×5 ever runs. That branch drops from ${branches[2].naiveMacs.compact()} to " +
            "${branches[2].reducedMacs.compact()} MACs, a " +
            "${"%.1f".format(branches[2].naiveMacs.toDouble() / branches[2].reducedMacs)}× cut.",
        bars = listOf(
            FmBar(
                "with 1×1 reductions (MACs per position)",
                branches.map { it.reducedMacs.toDouble() },
                SavingColor,
                branches.map { it.name.substringBefore(" →") },
            ),
        ),
        readout = "module: ${naive.compact()} → ${reduced.compact()} MACs " +
            "(${"%.1f".format(naive.toDouble() / reduced)}× cheaper)",
    )
    frames += FmFrame(
        status = "GoogLeNet stacks nine of these modules and ends with global average pooling instead of dense " +
            "layers, which is why it holds about 6.8M parameters against VGG-16's " +
            "${vgg16().totalParams().compact()} while scoring better on ImageNet. The bottleneck idea outlived the " +
            "module: every ResNet-50 block and every MobileNet block is a 1×1 sandwich.",
        stack = listOf(
            FmStackRow("GoogLeNet", "9 inception modules", 6_800_000L, "global average pool, no fc stack"),
            FmStackRow("VGG-16", "13 conv + 3 dense", vgg16().totalParams(), "89% of it in the dense layers", emphasis = true),
        ),
        readout = "6.8M vs ${vgg16().totalParams().compact()} parameters, better top-5 error",
    )
    return frames
}

// ── ResNet ───────────────────────────────────────────────────────────────────

private fun resNetFrames(): List<FmFrame> {
    val depth = 30
    val g = residualVsPlainGradient(depth = depth)
    val frames = mutableListOf<FmFrame>()

    fun curve(values: List<Double>, label: String, color: Color) = FmCurve(
        label,
        values.mapIndexed { i, v -> (i + 1).toFloat() to log10(max(v, 1e-12)).toFloat() },
        color,
    )

    frames += FmFrame(
        status = "The problem ResNet was built for is not overfitting. A 56-layer plain network had *higher " +
            "training* error than a 20-layer one — it could not even fit the data it had. Depth was making " +
            "optimisation harder, not the model weaker.",
        stack = listOf(
            FmStackRow("plain block", "x → conv → ReLU → conv", 0L, "output must recompute everything"),
            FmStackRow("residual block", "x → conv → ReLU → conv → + x", 0L, "output only has to learn the difference", emphasis = true),
        ),
    )
    frames += FmFrame(
        status = "The change is one addition. If the best thing a block can do is nothing, a plain block has to " +
            "learn the identity out of its weights; a residual block gets it by driving F(x) to zero, which is what " +
            "weight decay pushes it toward anyway. The easy case became the default case.",
        readout = "y = F(x) + x",
    )
    frames += FmFrame(
        status = "What that does to the backward pass, measured on identical weights. Starting from a unit gradient " +
            "at the output, the plain stack delivers ${"%.1e".format(g.plainSurvival)} of it to the first layer — " +
            "eight orders of magnitude gone. The residual stack delivers ${"%.1e".format(g.residualSurvival)}: the " +
            "identity path differentiates to 1, so every layer's gradient is the output's plus a correction, and no " +
            "weight can attenuate it away.",
        plot = FmPlot(
            "gradient norm by depth (log₁₀)",
            listOf(
                curve(g.plain, "plain", NegativeColor),
                curve(g.residual, "residual", SavingColor),
            ),
            xRange = 1f..depth.toFloat(),
            yRange = -12f..5f,
            logY = true,
        ),
        readout = "gradient at layer 1 — plain ${"%.1e".format(g.plainSurvival)} · residual ${"%.1e".format(g.residualSurvival)}",
    )
    frames += FmFrame(
        status = "Note which way the residual curve runs: summing a correction at every block makes the gradient " +
            "*grow* toward the input rather than shrink, ${"%.0f".format(g.residualSurvival)}× here. That is a far " +
            "easier problem than vanishing — it is what batch normalisation in each block, and initialising the last " +
            "BN's γ to zero so a fresh block starts as exactly the identity, are there to hold in range.",
        readout = "∂L/∂x_ℓ = ∂L/∂x_L · ∏(1 + ∂F/∂x) — the 1 is the whole trick",
    )
    frames += FmFrame(
        status = "With that, depth stopped costing anything: ResNet-152 trains where a 30-layer plain network will " +
            "not. Deeper ResNets use the bottleneck block — 1×1 down, 3×3, 1×1 up — so ResNet-50 holds about 25.6M " +
            "parameters, a fifth of VGG-16's, for far better accuracy.",
        stack = listOf(
            FmStackRow("basic block (ResNet-18/34)", "3×3 → 3×3, + x", 2L * convParams(64, 64, 3, bias = false), "at 64 channels"),
            FmStackRow(
                "bottleneck block (ResNet-50+)",
                "1×1 → 3×3 → 1×1, + x",
                convParams(256, 64, 1, bias = false) + convParams(64, 64, 3, bias = false) + convParams(64, 256, 1, bias = false),
                "same 3×3 cost, four times the width",
                emphasis = true,
            ),
        ),
        readout = "ResNet-50 ≈ 25.6M params vs VGG-16 ${vgg16().totalParams().compact()}",
    )
    return frames
}

// ── DenseNet ─────────────────────────────────────────────────────────────────

private fun denseNetFrames(): List<FmFrame> {
    val growth = 32
    val block = denseBlock(k0 = 64, growth = growth, layers = 6)
    val connections = denseBlockConnections(6)
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = "DenseNet takes ResNet's shortcut and changes the operator: concatenate instead of add. Layer ℓ " +
            "receives the feature maps of every earlier layer in its block, so nothing has to be re-derived and " +
            "features stay available all the way to the classifier.",
        stack = block.map {
            FmStackRow("layer ${it.index}", "in ${it.inChannels} ch → +$growth ch", it.params, "1×1 bottleneck then 3×3")
        },
        readout = "$connections direct connections in a 6-layer block, not 6",
    )
    frames += FmFrame(
        status = "Channels arriving at layer ℓ are k₀ + k(ℓ − 1) = 64 + 32(ℓ − 1): " +
            "${block.joinToString(", ") { it.inChannels.toString() }}. The growth rate k is deliberately small — " +
            "each layer contributes $growth new maps and reuses everything else, which is why a DenseNet is narrow " +
            "and still expressive.",
        bars = listOf(
            FmBar(
                "input channels per layer",
                block.map { it.inChannels.toDouble() },
                SignalColor,
                block.map { "ℓ${it.index}" },
            ),
        ),
    )
    frames += FmFrame(
        status = "Without the 1×1 bottleneck the 3×3 would face the whole concatenation, and cost would grow " +
            "quadratically down the block. The bottleneck pins its input at 4k = ${4 * growth} channels, so per-layer " +
            "cost stays nearly flat: ${block.joinToString(", ") { it.params.compact() }} parameters.",
        bars = listOf(
            FmBar(
                "parameters per layer (with bottleneck)",
                block.map { it.params.toDouble() },
                SavingColor,
                block.map { "ℓ${it.index}" },
            ),
        ),
        readout = "block total ${block.sumOf { it.params }.compact()} parameters",
    )
    frames += FmFrame(
        status = "The trade is memory, not parameters: DenseNet-121 reaches ResNet-50 accuracy with about 8M " +
            "parameters against 25.6M, but every intermediate concatenation has to be kept live for the backward " +
            "pass, so training memory is the binding constraint rather than FLOPs.",
        stack = listOf(
            FmStackRow("DenseNet-121", "growth 32, 4 blocks", 7_980_000L, "≈8.0M parameters", emphasis = true),
            FmStackRow("ResNet-50", "bottleneck, 4 stages", 25_557_032L, "≈25.6M parameters"),
        ),
    )
    return frames
}

// ── MobileNet ────────────────────────────────────────────────────────────────

private fun mobileNetFrames(): List<FmFrame> {
    val inChannels = 128
    val outChannels = 256
    val k = 3
    val standard = convParams(inChannels, outChannels, k, bias = false)
    val separable = depthwiseSeparableParams(inChannels, outChannels, k)
    val ratio = separableCostRatio(outChannels, k)
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = "A standard convolution filters and combines in one operation: every output channel touches every " +
            "input channel through its own $k×$k kernel. At $inChannels → $outChannels channels that is " +
            "${standard.compact()} parameters for one layer.",
        stack = listOf(
            FmStackRow("standard $k×$k", "$inChannels → $outChannels ch", standard, "filters and mixes at once", emphasis = true),
        ),
    )
    frames += FmFrame(
        status = "Depthwise separable convolution splits those two jobs. One $k×$k kernel per input channel — no " +
            "mixing — then a 1×1 that mixes channels and does no spatial work at all. Same output shape, " +
            "${separable.compact()} parameters.",
        stack = listOf(
            FmStackRow("depthwise $k×$k", "$inChannels ch, one filter each", convParams(1, inChannels, k, bias = false), "spatial only"),
            FmStackRow("pointwise 1×1", "$inChannels → $outChannels ch", convParams(inChannels, outChannels, 1, bias = false), "channel mixing only", emphasis = true),
        ),
        readout = "${standard.compact()} → ${separable.compact()} parameters",
    )
    frames += FmFrame(
        status = "The ratio is 1/N + 1/k² — ${"%.4f".format(ratio)} here, ${"%.1f".format(1 / ratio)}× cheaper — and " +
            "it does not depend on the spatial size at all. With a 3×3 kernel the 1/9 term dominates once the " +
            "channel count is large, so the saving parks near 8–9× and stays there.",
        bars = listOf(
            FmBar(
                "cost ratio by output channels (k = 3)",
                listOf(32, 64, 128, 256, 512).map { separableCostRatio(it, k) },
                SavingColor,
                listOf("32", "64", "128", "256", "512"),
            ),
        ),
        readout = "1/N + 1/k² = 1/$outChannels + 1/${k * k} = ${"%.4f".format(ratio)}",
    )
    frames += FmFrame(
        status = "MobileNetV1 is that block repeated: 4.2M parameters and 569M MACs, within about 1% of VGG-16's " +
            "ImageNet accuracy at ${"%.0f".format(vgg16().totalParams().toDouble() / 4_200_000)}× fewer parameters. " +
            "V2 added inverted residuals — expand with 1×1, filter depthwise, project back down, and put the skip " +
            "connection between the *narrow* ends, because that is where the information is.",
        stack = listOf(
            FmStackRow("MobileNetV1", "28 layers, all separable", 4_200_000L, "569M MACs", emphasis = true),
            FmStackRow("VGG-16", "13 conv + 3 dense", vgg16().totalParams(), "${vgg16().totalMacs().compact()} MACs"),
        ),
    )
    return frames
}

// ── EfficientNet ─────────────────────────────────────────────────────────────

private fun efficientNetFrames(): List<FmFrame> {
    val phis = (0..6).map { it.toDouble() }
    val scales = phis.map { compoundScale(it) }
    val frames = mutableListOf<FmFrame>()

    frames += FmFrame(
        status = "Three ways to make a convnet bigger: more layers, more channels, larger input images. Every " +
            "architecture before this scaled one of them by hand, and each one saturates on its own — accuracy " +
            "flattens long before the FLOPs do.",
        stack = listOf(
            FmStackRow("depth d", "more layers", 0L, "cost grows linearly"),
            FmStackRow("width w", "more channels", 0L, "cost grows with w²"),
            FmStackRow("resolution r", "larger input", 0L, "cost grows with r²"),
        ),
    )
    frames += FmFrame(
        status = "Compound scaling ties them together with one knob: d = 1.2^φ, w = 1.1^φ, r = 1.15^φ. The constants " +
            "are chosen so that α·β²·γ² = ${"%.3f".format(1.2 * 1.1 * 1.1 * 1.15 * 1.15)} ≈ 2 — which makes each unit " +
            "of φ exactly a doubling of FLOPs, and makes the three axes grow in a fixed ratio to each other.",
        readout = "α·β²·γ² ≈ 2, so FLOPs ≈ 2^φ",
    )
    frames += FmFrame(
        status = "B0 to B6 is φ = 0 to 6. Depth reaches ${"%.2f".format(scales.last().depth)}×, width " +
            "${"%.2f".format(scales.last().width)}× and resolution ${"%.2f".format(scales.last().resolution)}× — " +
            "resolution grows fastest per unit of compute, which is why the input goes from 224² to 600² across the " +
            "family while the layer count barely triples.",
        plot = FmPlot(
            "scaling factor by φ",
            listOf(
                FmCurve("depth", phis.mapIndexed { i, p -> p.toFloat() to scales[i].depth.toFloat() }, SignalColor),
                FmCurve("width", phis.mapIndexed { i, p -> p.toFloat() to scales[i].width.toFloat() }, CostColor),
                FmCurve("resolution", phis.mapIndexed { i, p -> p.toFloat() to scales[i].resolution.toFloat() }, SavingColor),
            ),
            xRange = 0f..6f,
            yRange = 1f..3.2f,
        ),
        readout = "φ = 6 → FLOPs ×${"%.0f".format(scales.last().flopsFactor)}",
    )
    frames += FmFrame(
        status = "The measured payoff: EfficientNet-B7 reached 84.3% ImageNet top-1 with about 66M parameters, " +
            "against GPipe's 557M for the same accuracy — 8.4× smaller. The B0 backbone itself came from a neural " +
            "architecture search over MobileNetV2-style inverted-residual blocks, so compound scaling is what was " +
            "applied to a good small model, not a substitute for having one.",
        bars = listOf(
            FmBar(
                "parameters at 84% ImageNet top-1",
                listOf(66_000_000.0, 557_000_000.0),
                SavingColor,
                listOf("EfficientNet-B7", "GPipe"),
            ),
        ),
        readout = "8.4× fewer parameters, 6.1× faster inference",
    )
    return frames
}

// ── Vision Transformer ───────────────────────────────────────────────────────

private fun vitFrames(): List<FmFrame> {
    val shape = vitShape()
    val grid = 14
    val frames = mutableListOf<FmFrame>()

    val patchMap = List(grid) { r -> List(grid) { c -> ((r * grid + c) % 9).toDouble() } }
    frames += FmFrame(
        status = "A Vision Transformer does not convolve at all. It cuts the 224×224 image into 16×16 patches — a " +
            "$grid×$grid grid, ${grid * grid} of them — flattens each to a 768-vector with one shared linear layer, " +
            "and hands the sequence to a standard transformer encoder. An image becomes ${shape.tokens} tokens, " +
            "counting the class token.",
        grids = listOf(FmGrid("$grid×$grid patch grid", patchMap, highlight = setOf(0 to 0, 0 to 1, 1 to 0), tone = GridTone.HEAT)),
        readout = "${grid * grid} patches + 1 class token = ${shape.tokens} tokens",
    )
    frames += FmFrame(
        status = "The patch embedding is the only image-specific machinery in the model: a linear map from " +
            "16·16·3 = 768 raw values to a 768-dimensional token, ${shape.patchEmbeddingParams.compact()} " +
            "parameters. Position embeddings (${shape.positionParams.compact()} parameters) are added because " +
            "attention is permutation-invariant and would otherwise not know where a patch came from.",
        stack = listOf(
            FmStackRow("patch embedding", "768 → 768 per patch", shape.patchEmbeddingParams, "one linear layer, shared", emphasis = true),
            FmStackRow("position embedding", "${shape.tokens} × 768", shape.positionParams, "learned, not sinusoidal"),
        ),
    )
    frames += FmFrame(
        status = "What changes is the inductive bias. A convolution hard-codes locality and translation " +
            "equivariance; self-attention hard-codes nothing and compares all ${shape.attentionPairs} token pairs in " +
            "every layer, so it can relate opposite corners in layer 1. That freedom is why ViT loses to ResNets on " +
            "ImageNet-1k alone and wins once pre-trained on JFT-300M — the bias a convnet is given, a transformer " +
            "has to learn from data.",
        bars = listOf(
            FmBar(
                "pairs compared in layer 1",
                listOf(9.0, shape.attentionPairs.toDouble()),
                CostColor,
                listOf("3×3 conv: 9 neighbours", "ViT: ${shape.attentionPairs} pairs"),
            ),
        ),
        readout = "attention cost grows with tokens²; halving the patch size quadruples it",
    )
    frames += FmFrame(
        status = "The lesson the field took from it: the convolution was never load-bearing, the *training recipe* " +
            "was. Hybrids followed in both directions — Swin put locality and hierarchy back into attention windows, " +
            "and ConvNeXt rebuilt a pure convnet with the transformer's recipe and matched it.",
        stack = listOf(
            FmStackRow("ViT-Base/16", "${shape.tokens} tokens, 12 layers", 86_000_000L, "≈86M parameters", emphasis = true),
            FmStackRow("ResNet-50", "conv, 4 stages", 25_557_032L, "≈25.6M parameters"),
        ),
    )
    return frames
}

// ── Config registry ──────────────────────────────────────────────────────────

private val featureMapConfigs: Map<String, FmConfig> = linkedMapOf(
    "conv_layers" to FmConfig(
        intro = "One kernel slid over a 7×7 patch, computed cell by cell. Change the kernel size, stride and padding " +
            "and every number below — including the output size — is recomputed.",
        legend = listOf(WindowColor to "Window", SignalColor to "Positive response", NegativeColor to "Negative"),
        controls = true,
        build = ::convolutionFrames,
    ),
    "pooling_layers" to FmConfig(
        intro = "Max and average pooling over a real feature map, with the translation-invariance claim measured " +
            "rather than asserted.",
        legend = listOf(SignalColor to "Response", SavingColor to "Survives a shift", MutedColor to "Discarded"),
        controls = true,
        build = ::poolingFrames,
    ),
    "padding_strides" to FmConfig(
        intro = "The output-size formula, the border pixels that get read once instead of nine times, and what ten " +
            "unpadded layers do to a 224×224 image.",
        legend = listOf(SignalColor to "Reads per pixel", CostColor to "Shrinkage", WindowColor to "Window"),
        controls = true,
        build = ::paddingStrideFrames,
    ),
    "lenet5" to FmConfig(
        intro = "LeNet-5 layer by layer, with the parameter count summed from the layer table rather than quoted.",
        legend = listOf(SignalColor to "Convolution", MutedColor to "Pooling", NegativeColor to "Dense"),
        build = { lenetFrames() },
    ),
    "alexnet" to FmConfig(
        intro = "AlexNet's eight learned layers, and where its sixty-two million parameters actually sit.",
        legend = listOf(SignalColor to "Convolution", MutedColor to "Pooling", NegativeColor to "Dense"),
        build = { alexNetFrames() },
    ),
    "vgg" to FmConfig(
        intro = "VGG-16's uniform 3×3 stack, its 138M parameters, and why two small kernels beat one large one.",
        legend = listOf(SignalColor to "Convolution", MutedColor to "Pooling", NegativeColor to "Dense"),
        build = { vggFrames() },
    ),
    "inception" to FmConfig(
        intro = "The inception 3a module at its real widths, priced with and without its 1×1 bottlenecks.",
        legend = listOf(CostColor to "Naive cost", SavingColor to "With 1×1 reduction", SignalColor to "Branch"),
        build = { inceptionFrames() },
    ),
    "resnet" to FmConfig(
        intro = "The same backward pass down a plain stack and a residual one — identical weights, one addition of " +
            "difference, thirty layers of consequence.",
        legend = listOf(NegativeColor to "Plain", SavingColor to "Residual", SignalColor to "Identity path"),
        build = { resNetFrames() },
    ),
    "densenet" to FmConfig(
        intro = "A six-layer dense block: what concatenation does to the channel count, and what the 1×1 bottleneck " +
            "does about it.",
        legend = listOf(SignalColor to "Channels in", SavingColor to "Parameters", MutedColor to "Reused features"),
        build = { denseNetFrames() },
    ),
    "mobilenet" to FmConfig(
        intro = "Standard convolution against depthwise separable at the same shape, priced exactly.",
        legend = listOf(CostColor to "Standard conv", SavingColor to "Separable", SignalColor to "Pointwise 1×1"),
        build = { mobileNetFrames() },
    ),
    "efficientnet" to FmConfig(
        intro = "Depth, width and resolution scaled together by one exponent, with the constants that make each unit " +
            "of φ a doubling of FLOPs.",
        legend = listOf(SignalColor to "Depth", CostColor to "Width", SavingColor to "Resolution"),
        build = { efficientNetFrames() },
    ),
    "vit" to FmConfig(
        intro = "An image as 196 patches plus a class token, and what dropping the convolution costs and buys.",
        legend = listOf(SignalColor to "Patch token", WindowColor to "Class token", CostColor to "Attention pairs"),
        build = { vitFrames() },
    ),
)

private fun featureMapConfigFor(topicId: String): FmConfig =
    featureMapConfigs[topicId] ?: featureMapConfigs.getValue("conv_layers")

internal val featureMapTopicIds: Set<String> get() = featureMapConfigs.keys

private val controlSweep: List<FmControls> = listOf(
    FmControls(kernel = 1, stride = 1, padding = 0),
    FmControls(kernel = 3, stride = 1, padding = 0),
    FmControls(kernel = 3, stride = 2, padding = 1),
    FmControls(kernel = 5, stride = 1, padding = 2),
    FmControls(kernel = 5, stride = 3, padding = 0),
    FmControls(kernel = 7, stride = 4, padding = 3),
)

/**
 * Builds every frame of a lab and checks it against the geometry it claims. Interactive labs are run
 * across the whole control sweep, not just their default, because a kernel/stride/padding
 * combination that empties the feature map is reachable with two taps — and a grid indexed outside
 * its own bounds is drawn silently rather than thrown.
 */
internal fun featureMapFrameCount(topicId: String): Int {
    val config = featureMapConfigFor(topicId)
    val settings = if (config.controls) controlSweep else listOf(FmControls())
    var count = 0
    settings.forEach { controls ->
        val frames = config.build(controls)
        require(frames.isNotEmpty()) { "$topicId built no frames at $controls" }
        frames.forEach { frame ->
            frame.grids.forEach { grid ->
                require(grid.values.isNotEmpty()) { "$topicId draws an empty grid '${grid.label}' at $controls" }
                val width = grid.values.first().size
                require(grid.values.all { it.size == width }) { "$topicId grid '${grid.label}' is ragged at $controls" }
                require(grid.values.all { row -> row.all { it.isFinite() } }) {
                    "$topicId grid '${grid.label}' holds a non-finite value at $controls"
                }
                val stray = grid.highlight.filterNot { (r, c) -> r in grid.values.indices && c in 0 until width }
                require(stray.isEmpty()) { "$topicId highlights $stray outside grid '${grid.label}' at $controls" }
            }
            frame.bars.forEach { bar ->
                require(bar.values.isNotEmpty() && bar.values.all { it.isFinite() }) {
                    "$topicId draws a non-finite bar in '${bar.label}' at $controls"
                }
                require(bar.captions.isEmpty() || bar.captions.size == bar.values.size) {
                    "$topicId bar '${bar.label}' has ${bar.captions.size} captions for ${bar.values.size} values"
                }
            }
            frame.plot?.let { plot ->
                val outside = plot.curves.flatMap { it.points }.count { (x, y) ->
                    !x.isFinite() || !y.isFinite() ||
                        x !in plot.xRange || y !in (plot.yRange.start - 0.001f)..(plot.yRange.endInclusive + 0.001f)
                }
                require(outside == 0) { "$topicId plots $outside point(s) outside '${plot.label}' at $controls" }
            }
            frame.stack.forEach { row ->
                require(row.params >= 0L) { "$topicId reports ${row.params} parameters for '${row.name}'" }
            }
        }
        count = frames.size
    }
    return count
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun FeatureMapSection(topicId: String) {
    val config = remember(topicId) { featureMapConfigFor(topicId) }
    var controls by remember(config) { mutableStateOf(FmControls()) }
    val frames = remember(config, controls) { config.build(controls) }
    val playback = rememberPlaybackState(key = config to controls, stepCount = frames.size, initialSpeedMs = 950f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (config.controls) {
                ControlPicker("kernel", listOf(1, 3, 5, 7), controls.kernel) { controls = controls.copy(kernel = it) }
                ControlPicker("stride", listOf(1, 2, 3, 4), controls.stride) { controls = controls.copy(stride = it) }
                ControlPicker("padding", listOf(0, 1, 2, 3), controls.padding) { controls = controls.copy(padding = it) }
            }

            if (frame.grids.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    frame.grids.forEach { grid ->
                        Box(modifier = Modifier.weight(grid.values.first().size.toFloat().coerceAtLeast(1f))) {
                            FeatureGrid(grid)
                        }
                    }
                }
            }

            frame.stack.forEach { row -> StackRow(row, frame.stack.maxOf { max(it.params, 1L) }) }

            frame.bars.forEach { bar -> FeatureBars(bar, modifier = Modifier.padding(top = 12.dp)) }

            frame.plot?.let { FeaturePlot(it, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> FmLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun ControlPicker(label: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .background(
                        if (active) WindowColor else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    option.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeatureGrid(grid: FmGrid) {
    val peak = grid.values.flatten().maxOfOrNull { abs(it) }?.coerceAtLeast(0.001) ?: 1.0
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(grid.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            grid.values.forEachIndexed { r, row ->
                Row(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                    row.forEachIndexed { c, value ->
                        val intensity = (abs(value) / peak).coerceIn(0.0, 1.0).toFloat()
                        val base = when {
                            grid.tone == GridTone.COUNT -> SavingColor
                            grid.tone == GridTone.HEAT -> WindowColor
                            value < 0.0 -> NegativeColor
                            else -> SignalColor
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .background(base.copy(alpha = 0.10f + 0.75f * intensity), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (r to c in grid.highlight) {
                                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                    drawRoundRect(
                                        color = WindowColor,
                                        style = Stroke(width = 4f),
                                        cornerRadius = CornerRadius(6f, 6f),
                                    )
                                }
                            }
                            if (grid.values.size <= 8) {
                                Text(
                                    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
                                    else "%.1f".format(value),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (intensity > 0.5f) FontWeight.Bold else FontWeight.Normal,
                                    color = if (intensity > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StackRow(row: FmStackRow, peak: Long) {
    val share = (row.params.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (row.emphasis) FontWeight.Bold else FontWeight.Normal,
                color = if (row.emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1.4f),
            )
            Text(
                row.shape,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.2f),
            )
            Text(
                if (row.params == 0L) "—" else row.params.compact(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.6f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(top = 2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share)
                    .fillMaxHeight()
                    .background(if (row.emphasis) WindowColor else SignalColor, RoundedCornerShape(2.dp)),
            )
        }
        if (row.note.isNotEmpty()) {
            Text(row.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FeatureBars(bar: FmBar, modifier: Modifier = Modifier) {
    val peak = bar.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001) ?: 1.0
    Column(modifier = modifier.fillMaxWidth()) {
        Text(bar.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(46.dp).padding(top = 4.dp)) {
            val slot = size.width / bar.values.size
            bar.values.forEachIndexed { index, value ->
                val height = ((abs(value) / peak) * size.height).toFloat().coerceAtLeast(2f)
                drawRoundRect(
                    color = if (value >= 0.0) bar.color else NegativeColor,
                    topLeft = Offset(index * slot + slot * 0.18f, size.height - height),
                    size = Size(slot * 0.64f, height),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        if (bar.captions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                bar.captions.forEach { caption ->
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturePlot(plot: FmPlot, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            plot.label + if (plot.logY) " · log₁₀ scale" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp)) {
            val xSpan = (plot.xRange.endInclusive - plot.xRange.start).takeIf { it != 0f } ?: 1f
            val ySpan = (plot.yRange.endInclusive - plot.yRange.start).takeIf { it != 0f } ?: 1f
            fun px(x: Float) = (x - plot.xRange.start) / xSpan * size.width
            fun py(y: Float) = size.height - (y - plot.yRange.start) / ySpan * size.height

            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)
            if (0f in plot.yRange) drawLine(axis, Offset(0f, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1f)

            plot.curves.forEach { curve ->
                curve.points.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        curve.color,
                        Offset(px(a.first), py(a.second)),
                        Offset(px(b.first), py(b.second)),
                        strokeWidth = 3f,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            plot.curves.forEach { FmLegend(it.color, it.label) }
        }
    }
}

@Composable
private fun FmLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
