package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// ── CNN mechanics and architectures ──────────────────────────────────────────
// The arithmetic behind the twelve C3 labs. Two kinds of thing live here.
//
// The mechanics — output size, the convolution itself, pooling, how many windows touch a given
// pixel, receptive field — are exact and small enough to run per frame, so a lab's narration is the
// same code path the picture is drawn from.
//
// The architectures are layer tables rather than prose. Every parameter count in LeNet-5, AlexNet
// and VGG-16 is summed from the layer definitions, not quoted: the famous 61,706 / ~62M / 138M
// figures are outputs of this file, and the far more interesting split *inside* those totals
// (VGG-16 keeps 89% of its parameters in three fully-connected layers, and 99% of its compute in
// the convolutions) falls out of the same table.
//
// Convolution here is cross-correlation — no kernel flip — which is what every deep-learning
// framework calls `conv2d`. The distinction matters mathematically and not at all in practice,
// since the kernel is learned either way.

// ── Geometry ─────────────────────────────────────────────────────────────────

/** ⌊(n + 2p − d(k−1) − 1)/s⌋ + 1. Returns 0 when the window does not fit at all. */
internal fun convOutputSize(input: Int, kernel: Int, stride: Int, padding: Int, dilation: Int = 1): Int {
    require(kernel >= 1 && stride >= 1 && padding >= 0 && dilation >= 1) {
        "conv geometry out of range: k=$kernel s=$stride p=$padding d=$dilation"
    }
    val effective = dilation * (kernel - 1) + 1
    val span = input + 2 * padding - effective
    return if (span < 0) 0 else span / stride + 1
}

/** The padding that keeps the size unchanged at stride 1. Only whole for odd kernels. */
internal fun samePadding(kernel: Int, dilation: Int = 1): Int = dilation * (kernel - 1) / 2

internal fun padImage(image: List<List<Double>>, padding: Int, value: Double = 0.0): List<List<Double>> {
    if (padding == 0) return image
    val width = image[0].size + 2 * padding
    val blank = List(width) { value }
    val body = image.map { row -> List(padding) { value } + row + List(padding) { value } }
    return List(padding) { blank } + body + List(padding) { blank }
}

/** Cross-correlation of `image` with `kernel`. Square kernel, square stride. */
internal fun conv2d(
    image: List<List<Double>>,
    kernel: List<List<Double>>,
    stride: Int = 1,
    padding: Int = 0,
): List<List<Double>> {
    val k = kernel.size
    val out = convOutputSize(image.size, k, stride, padding)
    if (out == 0) return emptyList()
    val padded = padImage(image, padding)
    return List(out) { r ->
        List(out) { c ->
            var sum = 0.0
            for (kr in 0 until k) for (kc in 0 until k) {
                sum += padded[r * stride + kr][c * stride + kc] * kernel[kr][kc]
            }
            sum
        }
    }
}

internal enum class PoolMode { MAX, AVERAGE }

internal fun pool2d(map: List<List<Double>>, size: Int = 2, stride: Int = size, mode: PoolMode = PoolMode.MAX): List<List<Double>> {
    val out = convOutputSize(map.size, size, stride, 0)
    if (out == 0) return emptyList()
    return List(out) { r ->
        List(out) { c ->
            val window = (0 until size).flatMap { pr -> (0 until size).map { pc -> map[r * stride + pr][c * stride + pc] } }
            if (mode == PoolMode.MAX) window.max() else window.average()
        }
    }
}

/**
 * How many output windows each *original* pixel contributes to. Padding is included in the sliding
 * but the counts are reported for the real pixels only, which is the whole argument for padding:
 * without it a corner pixel is read once and a centre pixel k² times, so the border of the image is
 * systematically under-weighted before a single weight is learned.
 */
internal fun windowCounts(input: Int, kernel: Int, stride: Int, padding: Int): List<List<Int>> {
    val out = convOutputSize(input, kernel, stride, padding)
    val counts = MutableList(input) { MutableList(input) { 0 } }
    for (r in 0 until out) for (c in 0 until out) {
        for (kr in 0 until kernel) for (kc in 0 until kernel) {
            val pr = r * stride + kr - padding
            val pc = c * stride + kc - padding
            if (pr in 0 until input && pc in 0 until input) counts[pr][pc]++
        }
    }
    return counts.map { it.toList() }
}

internal class LayerGeometry(val kernel: Int, val stride: Int)

/**
 * Receptive field after each layer, in input pixels: rᵢ = rᵢ₋₁ + (kᵢ − 1)·∏ⱼ<ᵢ sⱼ. This is why depth
 * substitutes for kernel size — two stacked 3×3s see 5×5 with fewer parameters, three see 7×7.
 */
internal fun receptiveField(layers: List<LayerGeometry>): List<Int> {
    var rf = 1
    var jump = 1
    return layers.map { layer ->
        rf += (layer.kernel - 1) * jump
        jump *= layer.stride
        rf
    }
}

// ── Parameter and compute counting ───────────────────────────────────────────

internal fun convParams(inChannels: Int, outChannels: Int, kernel: Int, bias: Boolean = true): Long =
    outChannels.toLong() * (inChannels.toLong() * kernel * kernel + if (bias) 1L else 0L)

internal fun denseParams(inFeatures: Int, outFeatures: Int, bias: Boolean = true): Long =
    outFeatures.toLong() * (inFeatures.toLong() + if (bias) 1L else 0L)

/** Multiply-accumulates for one conv layer. FLOPs are conventionally quoted as 2× this. */
internal fun convMacs(outSize: Int, outChannels: Int, inChannels: Int, kernel: Int): Long =
    outSize.toLong() * outSize * outChannels * inChannels * kernel * kernel

/** Depthwise separable: one k×k filter per input channel, then a 1×1 mixing the channels. */
internal fun depthwiseSeparableParams(inChannels: Int, outChannels: Int, kernel: Int, bias: Boolean = false): Long =
    convParams(1, inChannels, kernel, bias) + convParams(inChannels, outChannels, 1, bias)

/** Separable cost ÷ standard cost = 1/outChannels + 1/k². Independent of the spatial size. */
internal fun separableCostRatio(outChannels: Int, kernel: Int): Double =
    1.0 / outChannels + 1.0 / (kernel * kernel)

// ── Architectures as layer tables ────────────────────────────────────────────

internal enum class LayerKind { CONV, POOL, DENSE }

internal class CnnLayer(
    val name: String,
    val kind: LayerKind,
    /** Spatial side of this layer's output, and its channel count. */
    val outSize: Int,
    val outChannels: Int,
    val params: Long,
    val macs: Long,
) {
    val activations: Long get() = outSize.toLong() * outSize * outChannels
    val shape: String get() = if (kind == LayerKind.DENSE) "$outChannels" else "$outSize×$outSize×$outChannels"
}

internal fun List<CnnLayer>.totalParams(): Long = sumOf { it.params }

internal fun List<CnnLayer>.totalMacs(): Long = sumOf { it.macs }

internal fun List<CnnLayer>.paramsIn(kind: LayerKind): Long = filter { it.kind == kind }.sumOf { it.params }

internal fun List<CnnLayer>.macsIn(kind: LayerKind): Long = filter { it.kind == kind }.sumOf { it.macs }

private class StackBuilder(private var size: Int, private var channels: Int) {
    val layers = mutableListOf<CnnLayer>()

    fun conv(name: String, filters: Int, kernel: Int, stride: Int = 1, padding: Int = 0) {
        val out = convOutputSize(size, kernel, stride, padding)
        layers += CnnLayer(
            name = name,
            kind = LayerKind.CONV,
            outSize = out,
            outChannels = filters,
            params = convParams(channels, filters, kernel),
            macs = convMacs(out, filters, channels, kernel),
        )
        size = out
        channels = filters
    }

    fun pool(name: String, kernel: Int = 2, stride: Int = kernel) {
        val out = convOutputSize(size, kernel, stride, 0)
        layers += CnnLayer(name, LayerKind.POOL, out, channels, 0L, 0L)
        size = out
    }

    fun flattenedFeatures(): Int = size * size * channels

    fun dense(name: String, units: Int) {
        val inFeatures = if (layers.lastOrNull()?.kind == LayerKind.DENSE) channels else flattenedFeatures()
        val p = denseParams(inFeatures, units)
        layers += CnnLayer(name, LayerKind.DENSE, 1, units, p, inFeatures.toLong() * units)
        size = 1
        channels = units
    }
}

private fun stack(inputSize: Int, inputChannels: Int, block: StackBuilder.() -> Unit): List<CnnLayer> =
    StackBuilder(inputSize, inputChannels).apply(block).layers

/**
 * LeNet-5, 1998, on 32×32 greyscale. C3's partial connectivity table from the paper is not
 * reproduced — the modern full-connectivity reading is used, which is what every framework's LeNet
 * ships and what the 61,706 figure refers to.
 */
internal fun lenet5(): List<CnnLayer> = stack(32, 1) {
    conv("C1 conv 5×5", filters = 6, kernel = 5)
    pool("S2 pool 2×2")
    conv("C3 conv 5×5", filters = 16, kernel = 5)
    pool("S4 pool 2×2")
    conv("C5 conv 5×5", filters = 120, kernel = 5)
    dense("F6 dense", 84)
    dense("output", 10)
}

/**
 * AlexNet, 2012, on 227×227×3. Ungrouped: the paper split every conv across two GTX 580s, which
 * halves conv2/4/5 to fit 3 GB of memory. The single-tower version below is the one every later
 * reimplementation uses, and it is where "60 million parameters" comes from.
 */
internal fun alexNet(): List<CnnLayer> = stack(227, 3) {
    conv("conv1 11×11 s4", filters = 96, kernel = 11, stride = 4)
    pool("pool1 3×3 s2", kernel = 3, stride = 2)
    conv("conv2 5×5", filters = 256, kernel = 5, padding = 2)
    pool("pool2 3×3 s2", kernel = 3, stride = 2)
    conv("conv3 3×3", filters = 384, kernel = 3, padding = 1)
    conv("conv4 3×3", filters = 384, kernel = 3, padding = 1)
    conv("conv5 3×3", filters = 256, kernel = 3, padding = 1)
    pool("pool5 3×3 s2", kernel = 3, stride = 2)
    dense("fc6", 4096)
    dense("fc7", 4096)
    dense("fc8", 1000)
}

/** VGG-16, 2014, on 224×224×3. Every conv is 3×3 stride 1 pad 1; every pool halves the map. */
internal fun vgg16(): List<CnnLayer> = stack(224, 3) {
    conv("conv1_1 3×3", filters = 64, kernel = 3, padding = 1)
    conv("conv1_2 3×3", filters = 64, kernel = 3, padding = 1)
    pool("pool1")
    conv("conv2_1 3×3", filters = 128, kernel = 3, padding = 1)
    conv("conv2_2 3×3", filters = 128, kernel = 3, padding = 1)
    pool("pool2")
    conv("conv3_1 3×3", filters = 256, kernel = 3, padding = 1)
    conv("conv3_2 3×3", filters = 256, kernel = 3, padding = 1)
    conv("conv3_3 3×3", filters = 256, kernel = 3, padding = 1)
    pool("pool3")
    conv("conv4_1 3×3", filters = 512, kernel = 3, padding = 1)
    conv("conv4_2 3×3", filters = 512, kernel = 3, padding = 1)
    conv("conv4_3 3×3", filters = 512, kernel = 3, padding = 1)
    pool("pool4")
    conv("conv5_1 3×3", filters = 512, kernel = 3, padding = 1)
    conv("conv5_2 3×3", filters = 512, kernel = 3, padding = 1)
    conv("conv5_3 3×3", filters = 512, kernel = 3, padding = 1)
    pool("pool5")
    dense("fc6", 4096)
    dense("fc7", 4096)
    dense("fc8", 1000)
}

// ── Inception: what the 1×1 bottleneck actually buys ─────────────────────────

internal class InceptionBranch(
    val name: String,
    /** MACs per output position without a 1×1 reduction in front of the expensive kernel. */
    val naiveMacs: Long,
    val reducedMacs: Long,
    val outChannels: Int,
)

/**
 * The inception 3a module from GoogLeNet, at its real widths: 192 input channels, branches of
 * 64 (1×1), 128 (3×3 behind a 96-channel reduction), 32 (5×5 behind a 16-channel reduction) and
 * 32 (1×1 after a pool). Costs are per output position, so the 28×28 map is a constant factor
 * either way and the ratio is the number worth reading.
 */
internal fun inception3a(inChannels: Int = 192): List<InceptionBranch> = listOf(
    InceptionBranch("1×1 → 64", inChannels.toLong() * 64, inChannels.toLong() * 64, 64),
    InceptionBranch(
        "3×3 → 128",
        inChannels.toLong() * 128 * 9,
        inChannels.toLong() * 96 + 96L * 128 * 9,
        128,
    ),
    InceptionBranch(
        "5×5 → 32",
        inChannels.toLong() * 32 * 25,
        inChannels.toLong() * 16 + 16L * 32 * 25,
        32,
    ),
    InceptionBranch("pool → 1×1 → 32", inChannels.toLong() * 32, inChannels.toLong() * 32, 32),
)

// ── DenseNet: concatenation instead of addition ──────────────────────────────

internal class DenseBlockLayer(
    val index: Int,
    /** Channels arriving at this layer: k₀ + growth·(index − 1). */
    val inChannels: Int,
    val params: Long,
)

/**
 * One DenseNet block with bottlenecks (DenseNet-B): each layer sees every earlier layer's output
 * concatenated, produces `growth` new channels, and the 1×1 bottleneck holds the input width of the
 * 3×3 at 4·growth no matter how wide the concatenation has become. Connections in a block of L
 * layers are L(L+1)/2, which is the number the architecture is named for.
 */
internal fun denseBlock(k0: Int = 64, growth: Int = 32, layers: Int = 6): List<DenseBlockLayer> =
    (1..layers).map { l ->
        val inChannels = k0 + growth * (l - 1)
        val bottleneck = 4 * growth
        DenseBlockLayer(
            index = l,
            inChannels = inChannels,
            params = convParams(inChannels, bottleneck, 1, bias = false) +
                convParams(bottleneck, growth, 3, bias = false),
        )
    }

internal fun denseBlockConnections(layers: Int): Int = layers * (layers + 1) / 2

// ── ResNet: what the identity path does to the gradient ──────────────────────

private class CnnRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767.0
    }

    fun gaussian(): Double {
        val u1 = next().coerceAtLeast(1e-9)
        val u2 = next()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}

internal class DepthGradients(
    /** Gradient norm reaching each depth, output side last. Index 0 is the layer nearest the input. */
    val plain: List<Double>,
    val residual: List<Double>,
) {
    /** How much of the output-side gradient survives to the first layer. */
    val plainSurvival: Double get() = plain.first() / max(plain.last(), 1e-300)
    val residualSurvival: Double get() = residual.first() / max(residual.last(), 1e-300)
}

/**
 * Runs the same backward pass down a plain stack and a residual one — identical weights, identical
 * inputs, the only difference being the `x +` in the residual block's forward pass. `gain` scales
 * the He initialisation: below 1 the plain product shrinks layer after layer, which is exactly the
 * regime where a 56-layer plain network trained *worse* than a 20-layer one. The residual path
 * differentiates to 1, so its gradient has a route to the input that no weight can attenuate.
 */
internal fun residualVsPlainGradient(
    depth: Int = 30,
    width: Int = 24,
    gain: Double = 0.55,
    seed: Int = 37,
): DepthGradients {
    val rng = CnnRng(seed)
    val scale = gain * sqrt(2.0 / width)
    val w = Array(depth) { Array(width) { DoubleArray(width) { rng.gaussian() * scale } } }
    val x0 = DoubleArray(width) { rng.gaussian() }

    fun run(residual: Boolean): List<Double> {
        val pre = ArrayList<DoubleArray>(depth)
        var a = x0.copyOf()
        repeat(depth) { l ->
            val z = DoubleArray(width) { j -> (0 until width).sumOf { i -> w[l][j][i] * a[i] } }
            pre += z
            val f = DoubleArray(width) { max(0.0, z[it]) }
            a = if (residual) DoubleArray(width) { a[it] + f[it] } else f
        }
        // Unit gradient at the output, propagated back. Norms are reported per depth.
        var delta = DoubleArray(width) { 1.0 / sqrt(width.toDouble()) }
        val norms = DoubleArray(depth)
        for (l in depth - 1 downTo 0) {
            norms[l] = sqrt(delta.sumOf { it * it })
            val through = DoubleArray(width) { j -> if (pre[l][j] > 0) delta[j] else 0.0 }
            val back = DoubleArray(width) { i -> (0 until width).sumOf { j -> w[l][j][i] * through[j] } }
            delta = if (residual) DoubleArray(width) { delta[it] + back[it] } else back
        }
        return norms.toList()
    }

    return DepthGradients(plain = run(residual = false), residual = run(residual = true))
}

// ── Pooling: the invariance it is supposed to buy ────────────────────────────

internal class ShiftAgreement(
    /** Fraction of response cells that come back bit-identical after the shift. */
    val unchangedRaw: Double,
    val unchangedPooled: Double,
    /** Mean |Δ| across the map, as a fraction of the map's own mean magnitude. */
    val relativeChangeRaw: Double,
    val relativeChangePooled: Double,
)

/**
 * Shifts the image one pixel right, convolves both versions with the same kernel, and reports how
 * much of the response moved — before pooling and after pooling. The claim "pooling buys translation
 * invariance" is usually asserted; this measures it, and the answer is smaller than the claim: a
 * pooled map still changes, it just changes less.
 */
internal fun shiftAgreement(
    image: List<List<Double>>,
    kernel: List<List<Double>>,
    poolSize: Int = 2,
    shift: Int = 1,
    tolerance: Double = 1e-9,
): ShiftAgreement {
    val shifted = image.map { row -> List(shift) { 0.0 } + row.dropLast(shift) }
    val a = conv2d(image, kernel)
    val b = conv2d(shifted, kernel)
    fun unchanged(x: List<List<Double>>, y: List<List<Double>>): Double {
        val cells = x.indices.flatMap { r -> x[r].indices.map { c -> abs(x[r][c] - y[r][c]) <= tolerance } }
        return cells.count { it }.toDouble() / cells.size
    }
    fun relativeChange(x: List<List<Double>>, y: List<List<Double>>): Double {
        val delta = x.indices.sumOf { r -> x[r].indices.sumOf { c -> abs(x[r][c] - y[r][c]) } }
        val magnitude = x.sumOf { row -> row.sumOf { abs(it) } }
        return if (magnitude == 0.0) 0.0 else delta / magnitude
    }
    val pooledA = pool2d(a, poolSize)
    val pooledB = pool2d(b, poolSize)
    return ShiftAgreement(
        unchangedRaw = unchanged(a, b),
        unchangedPooled = unchanged(pooledA, pooledB),
        relativeChangeRaw = relativeChange(a, b),
        relativeChangePooled = relativeChange(pooledA, pooledB),
    )
}

// ── EfficientNet: compound scaling ───────────────────────────────────────────

internal class CompoundScale(val depth: Double, val width: Double, val resolution: Double) {
    /** Conv cost is linear in depth, quadratic in width and quadratic in resolution. */
    val flopsFactor: Double get() = depth * width * width * resolution * resolution
}

/**
 * α = 1.2, β = 1.1, γ = 1.15 with α·β²·γ² ≈ 2, so one unit of φ doubles the FLOPs. The point of the
 * paper is not the constants but that scaling one axis alone saturates: B0 → B7 is φ = 0 → 6 on all
 * three at once.
 */
internal fun compoundScale(phi: Double, alpha: Double = 1.2, beta: Double = 1.1, gamma: Double = 1.15): CompoundScale =
    CompoundScale(alpha.pow(phi), beta.pow(phi), gamma.pow(phi))

// ── Vision Transformer ───────────────────────────────────────────────────────

internal class VitShape(
    val tokens: Int,
    val patchEmbeddingParams: Long,
    val positionParams: Long,
    /** Pairs an attention head scores over one layer: tokens². */
    val attentionPairs: Long,
)

/** ViT-Base/16 at 224²: 196 patches, +1 class token, one linear map from a flattened 16×16×3 patch. */
internal fun vitShape(image: Int = 224, patch: Int = 16, dim: Int = 768, channels: Int = 3): VitShape {
    require(image % patch == 0) { "ViT needs the patch grid to tile the image exactly" }
    val grid = image / patch
    val tokens = grid * grid + 1
    return VitShape(
        tokens = tokens,
        patchEmbeddingParams = denseParams(patch * patch * channels, dim),
        positionParams = tokens.toLong() * dim,
        attentionPairs = tokens.toLong() * tokens,
    )
}
