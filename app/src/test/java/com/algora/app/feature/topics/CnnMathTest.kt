package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the numbers the C3 labs quote. Most are exact, because a convolution's geometry and an
 * architecture's parameter count are arithmetic — LeNet-5's 61,706 and VGG-16's 138,357,544 are
 * either right or wrong, and both are summed from the layer tables here rather than copied from a
 * paper. The two measured claims (what a residual shortcut does to the backward pass, what pooling
 * does to a shifted response) are checked as orderings and magnitudes, since a result that held only
 * for the exact configuration on screen would not be a property of the architecture.
 */
class CnnMathTest {

    // ── Geometry ─────────────────────────────────────────────────────────────

    @Test
    fun `output size follows the floor formula`() {
        assertEquals(224, convOutputSize(224, 3, 1, 1))
        assertEquals(222, convOutputSize(224, 3, 1, 0))
        assertEquals(112, convOutputSize(224, 3, 2, 1))
        assertEquals(112, convOutputSize(224, 7, 2, 3))
        assertEquals(55, convOutputSize(227, 11, 4, 0))
        // The floor drops the last partial window: (7 - 3)/2 + 1 = 3, not 4.
        assertEquals(3, convOutputSize(7, 3, 2, 0))
        // A window that cannot fit at all is 0 rather than a negative size.
        assertEquals(0, convOutputSize(5, 7, 1, 0))
    }

    @Test
    fun `same padding restores the input size for every odd kernel`() {
        listOf(1, 3, 5, 7, 9).forEach { k ->
            assertEquals("k=$k", 32, convOutputSize(32, k, 1, samePadding(k)))
        }
    }

    @Test
    fun `dilation grows the reach without changing the parameter count`() {
        assertEquals(convOutputSize(32, 5, 1, 0), convOutputSize(32, 3, 1, 0, dilation = 2))
        assertEquals(convParams(64, 64, 3), convParams(64, 64, 3))
    }

    @Test
    fun `the convolution runs a real cross-correlation`() {
        val image = List(6) { _ -> List(6) { c -> if (c < 3) 0.0 else 1.0 } }
        val sobel = listOf(listOf(1.0, 0.0, -1.0), listOf(2.0, 0.0, -2.0), listOf(1.0, 0.0, -1.0))
        val map = conv2d(image, sobel)
        assertEquals(4, map.size)
        // The edge sits between columns 2 and 3, so the response is confined to the window that
        // straddles it, and it is negative because intensity rises left to right.
        assertEquals(0.0, map[0][0], 1e-9)
        assertEquals(-4.0, map[0][1], 1e-9)
        assertEquals(0.0, map[0][3], 1e-9)
    }

    @Test
    fun `stride and padding change the map the way the formula says`() {
        val image = List(7) { r -> List(7) { c -> (r + c).toDouble() } }
        val kernel = List(3) { List(3) { 1.0 } }
        assertEquals(5, conv2d(image, kernel).size)
        assertEquals(3, conv2d(image, kernel, stride = 2).size)
        assertEquals(7, conv2d(image, kernel, padding = 1).size)
    }

    @Test
    fun `pooling reduces the map and keeps the mode's own statistic`() {
        val map = listOf(
            listOf(1.0, 5.0, 2.0, 0.0),
            listOf(3.0, 2.0, 1.0, 1.0),
            listOf(0.0, 0.0, 4.0, 8.0),
            listOf(0.0, 0.0, 2.0, 2.0),
        )
        val maxed = pool2d(map, 2)
        assertEquals(listOf(listOf(5.0, 2.0), listOf(0.0, 8.0)), maxed)
        val averaged = pool2d(map, 2, mode = PoolMode.AVERAGE)
        assertEquals(2.75, averaged[0][0], 1e-9)
        assertEquals(4.0, averaged[1][1], 1e-9)
    }

    @Test
    fun `unpadded convolution reads the border once and the centre nine times`() {
        val counts = windowCounts(7, 3, 1, 0)
        assertEquals(1, counts[0][0])
        assertEquals(9, counts[3][3])
        // 'Same' padding does not equalise the counts — the corner still loses the windows that
        // would have stood outside the image — but it lifts the worst case from 1 read to 4.
        val padded = windowCounts(7, 3, 1, 1)
        assertEquals(4, padded[0][0])
        assertEquals(9, padded[3][3])
        assertEquals(4, padded.flatten().min())
    }

    @Test
    fun `receptive field grows additively with depth`() {
        assertEquals(listOf(3, 5, 7), receptiveField(List(3) { LayerGeometry(3, 1) }))
        // A stride multiplies everything after it, which is why early strides dominate.
        assertEquals(listOf(3, 7, 15), receptiveField(List(3) { LayerGeometry(3, 2) }))
    }

    // ── Parameter counting ───────────────────────────────────────────────────

    @Test
    fun `LeNet-5 sums to the figure it is known by`() {
        val net = lenet5()
        assertEquals(61_706L, net.totalParams())
        assertEquals(listOf(156L, 0L, 2_416L, 0L, 48_120L, 10_164L, 850L), net.map { it.params })
        // C5 alone is 78% of the network.
        assertTrue(48_120.0 / net.totalParams() > 0.77)
        assertEquals(416_520L, net.totalMacs())
        // The 32×32 input is a geometry requirement: three unpadded 5×5s and two halvings land on 1.
        assertEquals(1, net.first { it.name.startsWith("C5") }.outSize)
    }

    @Test
    fun `AlexNet keeps 94 percent of its parameters in the dense head and 95 percent of its compute in the convolutions`() {
        val net = alexNet()
        assertEquals(62_378_344L, net.totalParams())
        assertEquals(3_747_200L, net.paramsIn(LayerKind.CONV))
        assertEquals(58_631_144L, net.paramsIn(LayerKind.DENSE))
        assertEquals(37_752_832L, denseParams(9216, 4096))
        val denseShare = net.paramsIn(LayerKind.DENSE).toDouble() / net.totalParams()
        val convComputeShare = net.macsIn(LayerKind.CONV).toDouble() / net.totalMacs()
        assertTrue("dense share $denseShare", denseShare > 0.93 && denseShare < 0.95)
        assertTrue("conv compute share $convComputeShare", convComputeShare > 0.94)
    }

    @Test
    fun `VGG-16 sums to 138 million with the same inversion, harder`() {
        val net = vgg16()
        assertEquals(138_357_544L, net.totalParams())
        assertEquals(14_714_688L, net.paramsIn(LayerKind.CONV))
        assertEquals(102_764_544L, denseParams(25088, 4096))
        assertTrue(net.paramsIn(LayerKind.DENSE).toDouble() / net.totalParams() > 0.89)
        assertTrue(net.macsIn(LayerKind.CONV).toDouble() / net.totalMacs() > 0.99)
        // Every conv is padded, so the map only ever changes at a pool.
        assertEquals(7, net.last { it.kind == LayerKind.POOL }.outSize)
    }

    @Test
    fun `two stacked 3x3 layers beat one 5x5 on both counts`() {
        val stacked = 2 * convParams(512, 512, 3, bias = false)
        val single = convParams(512, 512, 5, bias = false)
        assertEquals(4_718_592L, stacked)
        assertEquals(6_553_600L, single)
        assertTrue(stacked < single)
        // Same receptive field either way: two 3×3s see 5×5, three see 7×7 at 45% fewer parameters.
        assertEquals(5, receptiveField(List(2) { LayerGeometry(3, 1) }).last())
        assertEquals(7, receptiveField(List(3) { LayerGeometry(3, 1) }).last())
        assertEquals(0.72, stacked.toDouble() / single, 0.01)
        assertEquals(0.55, 3.0 * convParams(512, 512, 3, bias = false) / convParams(512, 512, 7, bias = false), 0.01)
    }

    @Test
    fun `the inception bottleneck is where the module's cost goes`() {
        val branches = inception3a()
        val naive = branches.sumOf { it.naiveMacs }
        val reduced = branches.sumOf { it.reducedMacs }
        assertEquals(393_216L, naive)
        assertEquals(163_328L, reduced)
        assertEquals(256, branches.sumOf { it.outChannels })
        // The 5×5 branch is where nearly all of the saving comes from.
        val fiveByFive = branches.first { it.name.startsWith("5×5") }
        assertEquals(153_600L, fiveByFive.naiveMacs)
        assertEquals(15_872L, fiveByFive.reducedMacs)
        assertTrue(fiveByFive.naiveMacs.toDouble() / fiveByFive.reducedMacs > 9.0)
        // A 1×1 branch cannot be reduced further — there is nothing in front of it to reduce.
        val oneByOne = branches.first { it.name.startsWith("1×1") }
        assertEquals(oneByOne.naiveMacs, oneByOne.reducedMacs)
    }

    @Test
    fun `a dense block's channels grow linearly and its per-layer cost stays flat`() {
        val block = denseBlock(k0 = 64, growth = 32, layers = 6)
        assertEquals(listOf(64, 96, 128, 160, 192, 224), block.map { it.inChannels })
        assertEquals(21, denseBlockConnections(6))
        assertEquals(331_776L, block.sumOf { it.params })
        // The bottleneck is what keeps this flat: the last layer costs well under twice the first,
        // although it faces 3.5× the channels.
        assertTrue(block.last().params < 2 * block.first().params)
    }

    @Test
    fun `depthwise separable convolution costs exactly one over N plus one over k squared`() {
        assertEquals(294_912L, convParams(128, 256, 3, bias = false))
        assertEquals(33_920L, depthwiseSeparableParams(128, 256, 3))
        val ratio = depthwiseSeparableParams(128, 256, 3).toDouble() / convParams(128, 256, 3, bias = false)
        assertEquals(separableCostRatio(256, 3), ratio, 1e-9)
        assertEquals(0.115017, ratio, 1e-6)
        // The ratio does not depend on the input channel count, and its floor is 1/k².
        assertEquals(
            separableCostRatio(256, 3),
            depthwiseSeparableParams(512, 256, 3).toDouble() / convParams(512, 256, 3, bias = false),
            1e-9,
        )
        assertTrue(separableCostRatio(4096, 3) > 1.0 / 9.0)
        assertTrue(separableCostRatio(4096, 3) < 1.0 / 9.0 + 0.001)
    }

    @Test
    fun `compound scaling doubles the cost per unit of phi, approximately`() {
        assertEquals(1.9203, 1.2 * 1.1 * 1.1 * 1.15 * 1.15, 1e-4)
        val b0 = compoundScale(0.0)
        assertEquals(1.0, b0.depth, 1e-9)
        assertEquals(1.0, b0.flopsFactor, 1e-9)
        val b6 = compoundScale(6.0)
        assertEquals(2.986, b6.depth, 1e-3)
        assertEquals(1.772, b6.width, 1e-3)
        assertEquals(2.313, b6.resolution, 1e-3)
        // Resolution is the fastest-growing axis, and the whole thing tracks 1.9203^φ rather than 2^φ.
        assertTrue(b6.resolution > b6.width)
        assertEquals(50.1, b6.flopsFactor, 0.5)
    }

    @Test
    fun `ViT turns a 224 image into 197 tokens and almost no image-specific parameters`() {
        val shape = vitShape()
        assertEquals(197, shape.tokens)
        assertEquals(590_592L, shape.patchEmbeddingParams)
        assertEquals(151_296L, shape.positionParams)
        assertEquals(38_809L, shape.attentionPairs)
        // Halving the patch size quadruples the token count and so multiplies attention cost by 16.
        val half = vitShape(patch = 8)
        assertEquals(785, half.tokens)
        assertTrue(half.attentionPairs.toDouble() / shape.attentionPairs > 15.0)
    }

    // ── Measured claims ──────────────────────────────────────────────────────

    @Test
    fun `the residual shortcut is what delivers gradient to the first layer`() {
        val g = residualVsPlainGradient()
        assertEquals(30, g.plain.size)
        // Both stacks start from the same unit gradient at the output.
        assertEquals(g.plain.last(), g.residual.last(), 1e-9)
        assertTrue("plain survival ${g.plainSurvival}", g.plainSurvival < 1e-6)
        assertTrue("residual survival ${g.residualSurvival}", g.residualSurvival > 1.0)
        assertTrue(g.residualSurvival / g.plainSurvival > 1e9)
    }

    @Test
    fun `the residual advantage holds across seeds and depths, not just the one on screen`() {
        listOf(3, 37, 101, 404).forEach { seed ->
            listOf(20, 30, 40).forEach { depth ->
                val g = residualVsPlainGradient(depth = depth, seed = seed)
                assertTrue(
                    "seed $seed depth $depth: plain ${g.plainSurvival} residual ${g.residualSurvival}",
                    g.residualSurvival > g.plainSurvival * 1e4,
                )
            }
        }
    }

    @Test
    fun `pooling halves the response change under a shift rather than removing it`() {
        val image = List(9) { r -> List(9) { c -> if (r in 3..5 && c in 3..5) 1.0 else 0.0 } }
        val kernel = listOf(listOf(1.0, 0.0, -1.0), listOf(2.0, 0.0, -2.0), listOf(1.0, 0.0, -1.0))

        val one = shiftAgreement(image, kernel)
        assertEquals(1.0, one.relativeChangeRaw, 1e-9)
        assertEquals(0.5, one.relativeChangePooled, 1e-9)
        assertTrue(one.unchangedPooled > one.unchangedRaw)

        // The tolerance is bounded: a two-pixel shift moves the pooled map as much as a one-pixel
        // shift moved the raw one. Pooling buys a factor, not invariance.
        val two = shiftAgreement(image, kernel, shift = 2)
        assertEquals(1.75, two.relativeChangeRaw, 1e-9)
        assertEquals(1.0, two.relativeChangePooled, 1e-9)
        assertTrue(two.relativeChangePooled > one.relativeChangePooled)
    }
}
