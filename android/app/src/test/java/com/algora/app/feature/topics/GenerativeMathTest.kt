package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C7's guard. The batch's copy leans on three different kinds of claim and they are pinned three
 * different ways.
 *
 * Exact combinatorics — coverage counts, CycleGAN's mapping census, latent diffusion's tables — are
 * asserted to the digit, because if one of them moves the arithmetic in the topic was wrong.
 *
 * Identities — the Gram matrix under a permutation, AdaIN's output statistics — are asserted to
 * floating-point tolerance and would be worth a failing test even if the number changed only in the
 * fifteenth place, because the topic's claim is that they are *exact* rather than close.
 *
 * Experiments — the VAE sweep, StyleGAN's path lengths, the DeepFake angle sweep — get their value
 * pinned loosely and their *ordering* pinned tightly. An experiment that drifts a few percent has
 * made the copy slightly stale. One whose ordering inverts has made the topic wrong, which is the
 * failure this phase has hit nine times and wants to hear about immediately.
 */
class GenerativeMathTest {

    // ── VAE ──────────────────────────────────────────────────────────────────

    @Test
    fun `both gradient estimators are unbiased`() {
        val v = VaeLab.gradientVarianceAtEight
        assertEquals("reparameterized recovers the true gradient", 2.0, v.reparameterizedMean, 0.05)
        assertEquals(
            "the score-function estimator is unbiased too -- that is the whole point of the comparison",
            2.0, v.scoreFunctionMean, 0.15,
        )
    }

    @Test
    fun `and the variance gap is what rules one of them out`() {
        val v = VaeLab.gradientVarianceAtEight
        assertEquals(4.0, v.reparameterizedVariance, 0.1)
        assertEquals(382.0, v.scoreFunctionVariance, 15.0)
        assertEquals("the copy quotes 95x at d = 8", 95.0, v.ratio, 5.0)
    }

    @Test
    fun `the Monte Carlo run agrees with the closed form`() {
        val v = VaeLab.gradientVarianceAtEight
        assertEquals(
            "if these diverge, one of the two derivations is wrong and the copy cannot cite either",
            VaeLab.scoreFunctionVarianceClosedForm(8), v.scoreFunctionVariance, 15.0,
        )
        // The shape of the closed form is the claim the plot is drawn from: flat against growing.
        assertEquals(30.0, VaeLab.scoreFunctionVarianceClosedForm(1), 1e-9)
        assertEquals(132.0, VaeLab.scoreFunctionVarianceClosedForm(4), 1e-9)
        assertEquals(380.0, VaeLab.scoreFunctionVarianceClosedForm(8), 1e-9)
        assertEquals(1260.0, VaeLab.scoreFunctionVarianceClosedForm(16), 1e-9)
    }

    @Test
    fun `the score-function variance grows with dimension and the reparameterized one does not`() {
        val ratios = VaeLab.varianceDims.map {
            VaeLab.scoreFunctionVarianceClosedForm(it) / VaeLab.REPARAMETERIZED_VARIANCE_CLOSED_FORM
        }
        assertEquals(ratios.sorted(), ratios)
        assertTrue("d = 1 is already a 7x gap", ratios.first() > 7.0)
        assertTrue("d = 16 is past 300x", ratios.last() > 300.0)
    }

    @Test
    fun `the KL term prunes the latent to the data's true factor count`() {
        // The data has exactly two underlying factors and the model is given four dimensions.
        assertEquals(2, VaeLab.TRUE_FACTORS)
        assertEquals(4, VaeLab.LATENT_DIM)
        listOf(0.05, 0.1, 0.5, 1.0, 2.0).forEach { beta ->
            assertEquals(
                "beta = $beta should keep exactly the two dimensions the data has",
                VaeLab.TRUE_FACTORS, VaeLab.trained.getValue(beta).activeUnits,
            )
        }
    }

    @Test
    fun `a small enough beta keeps every dimension and a large enough one eats a real factor`() {
        assertEquals(4, VaeLab.trained.getValue(0.001).activeUnits)
        assertEquals(3, VaeLab.trained.getValue(0.01).activeUnits)
        assertEquals(
            "at beta = 4 the pruning has stopped being free",
            1, VaeLab.trained.getValue(4.0).activeUnits,
        )
    }

    @Test
    fun `reconstruction error is monotone in beta even where the active count plateaus`() {
        val errors = VaeLab.betaSweep.map { VaeLab.trained.getValue(it).reconstructionRmse }
        assertEquals(
            "beta buys reconstruction error smoothly; it does not buy dimensionality smoothly",
            errors.sorted(), errors,
        )
        assertEquals(0.051, errors.first(), 0.01)
        assertEquals(0.812, errors.last(), 0.03)
    }

    // ── DCGAN ────────────────────────────────────────────────────────────────

    @Test
    fun `transposed-convolution coverage is uniform exactly when the stride divides the kernel`() {
        listOf(4 to 2, 6 to 2, 6 to 3, 8 to 4, 9 to 3).forEach { (k, s) ->
            assertTrue("k=$k s=$s divides, so coverage must be flat", DcganLab.isUniform(k, s))
            assertTrue(DcganLab.divides(k, s))
        }
        listOf(3 to 2, 5 to 2, 4 to 3, 7 to 2).forEach { (k, s) ->
            assertTrue("k=$k s=$s does not divide, so coverage must not be flat", !DcganLab.isUniform(k, s))
            assertTrue(!DcganLab.divides(k, s))
        }
    }

    @Test
    fun `the exact coverage patterns the copy quotes`() {
        assertEquals(listOf(2), DcganLab.interiorCoverage(4, 2, 16).toSet().sorted())
        assertEquals(listOf(1, 2), DcganLab.interiorCoverage(3, 2, 16).toSet().sorted())
        assertEquals(listOf(2, 3), DcganLab.interiorCoverage(5, 2, 16).toSet().sorted())
        assertEquals(listOf(3), DcganLab.interiorCoverage(6, 2, 16).toSet().sorted())
        // k=4, s=3 is the period-3 case: 1, 1, 2 repeating.
        assertEquals(listOf(1, 1, 2, 1, 1, 2), DcganLab.interiorCoverage(4, 3, 16).take(6).toList())
    }

    @Test
    fun `the generator's parameters sit where the channels are, not where the pixels are`() {
        assertEquals(12_656_515L, DcganLab.generatorParameters)
        assertEquals(0.130, DcganLab.projectionShare, 0.002)
        val biggest = DcganLab.generator.maxBy { it.parameters }
        assertEquals("the 1024 -> 512 block at 8x8", 8, biggest.spatial)
        assertTrue(
            "two thirds of the generator in one layer is the claim",
            biggest.parameters.toDouble() / DcganLab.generatorParameters > 0.66,
        )
        assertTrue(
            "and the layer that emits the pixels is under 0.05%",
            DcganLab.generator.last().parameters.toDouble() / DcganLab.generatorParameters < 0.0005,
        )
    }

    // ── CycleGAN ─────────────────────────────────────────────────────────────

    @Test
    fun `cycle consistency removes exactly none of the adversarially optimal mappings`() {
        (2..6).forEach { n ->
            val (distributionMatching, alsoCycleConsistent, correct) = CycleGanLab.enumerate(n)
            assertEquals(
                "every bijection matches the distribution",
                CycleGanLab.factorial(n).toInt(), distributionMatching,
            )
            assertEquals(
                "and every one of them has an inverse, so the cycle term eliminates nothing",
                distributionMatching, alsoCycleConsistent,
            )
            assertEquals(1, correct)
        }
    }

    @Test
    fun `so the odds after both losses are still one in n factorial`() {
        assertEquals(720L, CycleGanLab.adversariallyOptimal(6))
        assertEquals(720L, CycleGanLab.cycleConsistent(6))
        assertEquals(1.0 / 720, CycleGanLab.oddsOfCorrect(6), 1e-12)
    }

    @Test
    fun `locality is the constraint that actually cuts the candidate set`() {
        assertEquals(1, CycleGanLab.withLocality(6, 0))
        assertEquals(13, CycleGanLab.withLocality(6, 1))
        assertEquals(73, CycleGanLab.withLocality(6, 2))
        assertEquals(230, CycleGanLab.withLocality(6, 3))
        assertEquals(504, CycleGanLab.withLocality(6, 4))
        assertTrue(
            "unlike the cycle term, this shrinks as the constraint tightens",
            CycleGanLab.withLocality(6, 1) < CycleGanLab.adversariallyOptimal(6),
        )
    }

    // ── StyleGAN ─────────────────────────────────────────────────────────────

    @Test
    fun `AdaIN replaces the content statistics exactly rather than approximately`() {
        val content = doubleArrayOf(3.0, -1.0, 7.0, 2.5, 0.5, -4.0)
        val style = doubleArrayOf(0.2, 0.1, 0.4, 0.25, 0.15, 0.3)
        val (contentMean, contentStd) = StyleGanLab.meanAndStd(content)
        val (styleMean, styleStd) = StyleGanLab.meanAndStd(style)
        val (outMean, outStd) = StyleGanLab.meanAndStd(StyleGanLab.adaIn(content, style))

        assertEquals("the output takes the style's mean", styleMean, outMean, 1e-12)
        assertEquals("and the style's standard deviation", styleStd, outStd, 1e-12)
        // The claim is only interesting because the content's own statistics were nothing like these.
        assertNotEquals(contentMean, styleMean, 1.0)
        assertTrue("content std 3.44 against style std 0.099", contentStd / styleStd > 30)
    }

    @Test
    fun `a fixed prior on a distribution with a hole forces a longer path`() {
        val p = StyleGanLab.pathLengths()
        assertEquals(2.47, p.latentZ, 0.15)
        assertEquals(0.305, p.latentW, 0.03)
        assertTrue(
            "if this inverts, the mapping network's entire justification has gone with it",
            p.latentZ > p.latentW,
        )
        assertEquals("the copy quotes 8.1x", 8.1, p.ratio, 0.6)
    }

    @Test
    fun `and the honest cost of a straight path in W is stated too`() {
        val p = StyleGanLab.pathLengths()
        assertTrue("some of a straight W path leaves the data", p.wLeavingSupport > 0.0)
        assertEquals("the copy quotes 3.1%", 0.031, p.wLeavingSupport, 0.008)
    }

    @Test
    fun `the style-input budget matches a 1024 model`() {
        assertEquals(18, StyleGanLab.styleInputs)
        assertEquals(4, StyleGanLab.styleInputsIn("coarse"))
        assertEquals(4, StyleGanLab.styleInputsIn("middle"))
        assertEquals(10, StyleGanLab.styleInputsIn("fine"))
    }

    // ── Stable Diffusion ─────────────────────────────────────────────────────

    @Test
    fun `the element and token tables`() {
        assertEquals(786_432L, LatentDiffusionLab.pixelElements)
        assertEquals(16_384L, LatentDiffusionLab.latentElements)
        assertEquals(48.0, LatentDiffusionLab.elementRatio, 1e-9)
        assertEquals(262_144L, LatentDiffusionLab.pixelTokens)
        assertEquals(4_096L, LatentDiffusionLab.latentTokens)
    }

    @Test
    fun `self-attention saves the square of what the token count saved and cross-attention does not`() {
        assertEquals(68_719_476_736L, LatentDiffusionLab.pixelAttentionPairs)
        assertEquals(16_777_216L, LatentDiffusionLab.latentAttentionPairs)
        assertEquals(4096.0, LatentDiffusionLab.attentionRatio, 1e-9)
        assertEquals(64.0, LatentDiffusionLab.crossAttentionRatio, 1e-9)
        assertEquals(
            "the quadratic one is exactly the square of the linear one -- that is the whole argument",
            LatentDiffusionLab.crossAttentionRatio * LatentDiffusionLab.crossAttentionRatio,
            LatentDiffusionLab.attentionRatio, 1e-6,
        )
    }

    @Test
    fun `and the step count multiplies on top of it`() {
        assertEquals(20.0, LatentDiffusionLab.stepSaving, 1e-9)
        assertEquals(81_920.0, LatentDiffusionLab.samplingRatio, 1e-6)
    }

    @Test
    fun `a checkpoint is three networks and only one of them is denoised`() {
        assertEquals(1067, LatentDiffusionLab.totalParametersMillions)
        assertEquals(0.806, LatentDiffusionLab.unetShare, 0.002)
    }

    // ── Neural style transfer ────────────────────────────────────────────────

    @Test
    fun `the Gram matrix is exactly invariant to a spatial permutation`() {
        val f = StyleTransferLab.featureMap()
        val shuffled = StyleTransferLab.shuffleColumns(f)
        assertEquals(
            "not small -- zero. This is an identity, and the topic says so.",
            0.0, StyleTransferLab.styleLoss(f, shuffled), 1e-24,
        )
    }

    @Test
    fun `while the content loss on the same permutation is large`() {
        val f = StyleTransferLab.featureMap()
        val shuffled = StyleTransferLab.shuffleColumns(f)
        val content = StyleTransferLab.contentLoss(f, shuffled)
        assertEquals(0.163, content, 0.02)
        assertTrue(
            "the gap between these two is the whole division of labour in the objective",
            content > StyleTransferLab.styleLoss(f, shuffled) * 1e20,
        )
        // A shuffle that changed nothing would make the test vacuous.
        assertTrue(content > 0.01)
    }

    @Test
    fun `the Gram is an invariance rather than a compression`() {
        assertEquals(401_408L, StyleTransferLab.featureValues)
        assertEquals(131_328L, StyleTransferLab.gramUniqueEntries)
        assertEquals("only 3.06x -- the size is not the point", 3.06, StyleTransferLab.compression, 0.02)
        assertEquals(5, StyleTransferLab.styleLayers.size)
    }

    // ── DeepFakes ────────────────────────────────────────────────────────────

    @Test
    fun `independent encoders swap worse than ignoring the input entirely`() {
        val aligned = DeepFakeLab.swap(0.0)
        assertEquals(0.480, aligned.independentEncoderRmse, 0.02)
        assertEquals(0.366, aligned.meanFaceBaselineRmse, 0.02)
        assertTrue(
            "this is the batch's headline reversal: independent encoders lose to a constant",
            !aligned.independentBeatsBaseline,
        )
    }

    @Test
    fun `a shared encoder clears the baseline`() {
        val aligned = DeepFakeLab.swap(0.0)
        assertEquals(0.181, aligned.sharedEncoderRmse, 0.02)
        assertTrue(aligned.sharedBeatsBaseline)
        assertTrue(aligned.sharedEncoderRmse < aligned.independentEncoderRmse)
    }

    @Test
    fun `and reconstruction quality says nothing about whether the swap works`() {
        assertEquals(
            "both arrangements rebuild their own identity essentially exactly",
            0.0, DeepFakeLab.ownDomainRmse(), 1e-6,
        )
    }

    @Test
    fun `the swap degrades as the two expression manifolds separate`() {
        val sweep = DeepFakeLab.sweepResults.filter { it.degrees <= 60.0 }
        val errors = sweep.map { it.sharedEncoderRmse }
        assertEquals(
            "sharing the encoder is necessary and not sufficient -- this curve is the 'not sufficient'",
            errors.sorted(), errors,
        )
        assertEquals(0.181, errors.first(), 0.02)
        assertEquals(0.311, errors.last(), 0.03)
        assertTrue("still ahead of the baseline at 60 degrees", sweep.last().sharedBeatsBaseline)
    }

    @Test
    fun `and collapses entirely once they are orthogonal`() {
        val orthogonal = DeepFakeLab.sweepResults.first { it.degrees == 90.0 }
        assertTrue(
            "at 90 degrees the implied inverse is singular and no training schedule repairs it",
            orthogonal.sharedEncoderRmse > 100.0,
        )
        assertTrue(!orthogonal.sharedBeatsBaseline)
    }
}
