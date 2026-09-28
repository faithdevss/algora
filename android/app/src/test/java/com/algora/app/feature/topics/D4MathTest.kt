package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D4's guard. Two of these exist because the probe disagreed with the copy that was about to be
 * written: sinusoidal similarity is *not* a monotone decay (the standard diagram is wrong), and two
 * of BART's five corruptions produced byte-identical output until the rotation pivot moved.
 */
class D4MathTest {

    // ── Positional encodings ─────────────────────────────────────────────────

    @Test
    fun `sinusoidal encodings are offset-invariant`() {
        listOf(1, 2, 4, 8).forEach { offset ->
            assertEquals("offset $offset", 0.0, PositionalLab.offsetInvariance(offset), 1e-9)
        }
    }

    @Test
    fun `the offset profile is not a monotone decay`() {
        val bumps = PositionalLab.nonMonotonicOffsets()
        assertTrue(
            "If this ever becomes monotone the copy's central correction is false: $bumps",
            bumps.isNotEmpty(),
        )
        assertTrue(bumps.contains(4))
        assertTrue(PositionalLab.dotAtOffset(1) < PositionalLab.dotAtOffset(0))
    }

    @Test
    fun `RoPE scores depend only on the gap`() {
        listOf(1, 3, 7).forEach { gap ->
            assertEquals("gap $gap", 0.0, PositionalLab.ropeRelativeError(gap), 1e-9)
        }
        assertEquals(PositionalLab.ropeScore(5, 0), PositionalLab.ropeScore(25, 20), 1e-9)
    }

    @Test
    fun `ALiBi slopes are monotone by construction, unlike the sinusoid`() {
        val slopes = PositionalLab.alibiSlopes()
        assertEquals(8, slopes.size)
        assertEquals(slopes.sortedDescending(), slopes)
        assertTrue(PositionalLab.alibiBias(0, 20) < PositionalLab.alibiBias(0, 10))
        assertEquals(393_216, PositionalLab.learnedTableParameters(512))
    }

    // ── Feed-forward ─────────────────────────────────────────────────────────

    @Test
    fun `two thirds of a block is the feed-forward network`() {
        assertEquals(2_359_296L, FfnLab.attentionParameters())
        assertEquals(4_718_592L, FfnLab.ffnParameters())
        assertEquals(0.667, FfnLab.ffnShare(), 0.001)
        assertEquals(2 * FfnLab.ffnParameters(), FfnLab.ffnFlops())
    }

    @Test
    fun `SwiGLU's three matrices cost what two did`() {
        val hidden = FfnLab.swigluHidden(FfnLab.llamaDModel)
        assertEquals(10_922, hidden)
        assertTrue("LLaMA rounds 8/3·d up to a hardware-friendly width", FfnLab.llamaHidden > hidden)
        val gated = FfnLab.swigluParameters(FfnLab.llamaDModel, hidden)
        val plain = FfnLab.ffnParameters(FfnLab.llamaDModel)
        assertTrue("within 1%", kotlin.math.abs(gated - plain).toDouble() / plain < 0.01)
    }

    @Test
    fun `GELU differs from ReLU exactly where the copy says it does`() {
        assertTrue("negative inputs pass a little signal", FfnLab.gelu(-0.5) < 0.0)
        assertEquals(0.0, FfnLab.relu(-0.5), 1e-12)
        assertEquals(0.0, FfnLab.activationGap(0.0), 1e-9)
        assertTrue(FfnLab.activationSparsity() > 0.8)
    }

    // ── BART ─────────────────────────────────────────────────────────────────

    @Test
    fun `all five corruptions produce distinct documents`() {
        assertEquals(5, BartLab.all().size)
        assertTrue(
            "Rotation at a sentence boundary equals permutation — the pivot must stay mid-sentence",
            BartLab.allDistinct(),
        )
    }

    @Test
    fun `the corruptions differ on what they destroy`() {
        val masking = BartLab.tokenMasking()
        val deletion = BartLab.tokenDeletion()
        val infilling = BartLab.textInfilling()
        val permutation = BartLab.sentencePermutation()

        assertTrue("masking keeps length", BartLab.lengthKnown(masking))
        assertTrue("deletion loses tokens", BartLab.tokensLost(deletion) > 0)
        assertTrue("deletion hides length", !BartLab.lengthKnown(deletion))
        assertTrue("infilling hides length too", !BartLab.lengthKnown(infilling))
        assertEquals("permutation loses nothing", 0, BartLab.tokensLost(permutation))
        assertTrue("permutation changes order", BartLab.orderChanged(permutation))
    }

    // ── XLNet ────────────────────────────────────────────────────────────────

    @Test
    fun `the independence assumption has the cost the copy quotes`() {
        assertEquals(0.105, XlnetLab.independentJoint(), 1e-9)
        assertEquals(0.270, XlnetLab.trueJoint(), 1e-9)
        assertEquals(2.57, XlnetLab.independenceGap(), 0.01)
        assertTrue(XlnetLab.trueJoint() > XlnetLab.independentJoint())
    }

    @Test
    fun `factorization orders are factorial and the streams differ by one position`() {
        assertEquals(24, XlnetLab.factorizationOrders(4).size)
        assertEquals(24, XlnetLab.orderCount(4))
        assertEquals(40_320, XlnetLab.orderCount(8))
        val order = listOf(2, 0, 3, 1)
        (0..3).forEach { step ->
            assertEquals(step + 1, XlnetLab.contentStreamSees(order, step).size)
            assertEquals(step, XlnetLab.queryStreamSees(order, step).size)
        }
    }

    // ── Mixture of experts ───────────────────────────────────────────────────

    @Test
    fun `MoE saves compute and costs memory`() {
        assertEquals(0.276, MoeLab.activeFraction(), 0.005)
        assertEquals(0.724, MoeLab.computeSaving(), 0.005)
        assertEquals(6.67, MoeLab.memoryPenalty(), 0.01)
    }

    @Test
    fun `routing picks top-k and the load is uneven enough to need the auxiliary loss`() {
        MoeLab.routes().forEach { route ->
            assertEquals(MoeLab.topK, route.experts.size)
            assertEquals(1.0, route.weights.sum(), 1e-9)
            assertEquals(route.experts.distinct().size, route.experts.size)
        }
        assertEquals(MoeLab.tokens.size * MoeLab.topK, MoeLab.expertLoad().sum())
        assertTrue("A perfectly balanced strip would make the frame pointless", MoeLab.loadImbalance() > 1.0)
        assertTrue("aux loss is ~1.0 at balance and above it otherwise", MoeLab.auxiliaryLoss() > 1.0)
    }

    // ── Scaling ──────────────────────────────────────────────────────────────

    @Test
    fun `GPT-3 is the only under-trained model in the table`() {
        assertEquals(listOf("GPT-3"), ScalingLab.underTrained().map { it.name })
        val gpt3 = ScalingLab.models.first { it.name == "GPT-3" }
        assertEquals(1.71, ScalingLab.ratio(gpt3), 0.01)
        assertEquals(20.0, ScalingLab.ratio(ScalingLab.models.first { it.name == "Chinchilla" }), 1e-9)
    }

    @Test
    fun `loss falls with parameters and inference ignores training tokens`() {
        assertTrue(ScalingLab.lossFromParameters(175.0) < ScalingLab.lossFromParameters(1.0))
        val mistral = ScalingLab.models.first { it.name.startsWith("Mistral") }
        val llama = ScalingLab.models.first { it.name.startsWith("LLaMA-3") }
        assertEquals(10.0, ScalingLab.inferenceFlopsPerToken(llama) / ScalingLab.inferenceFlopsPerToken(mistral), 0.01)
        assertTrue("the open models train well past compute-optimal", ScalingLab.ratio(llama) > 200)
    }

    // ── Context cost ─────────────────────────────────────────────────────────

    @Test
    fun `long context is bounded by the KV cache, not by attention FLOPs`() {
        assertEquals(2_621_440L, ContextLab.kvCacheBytesPerToken())
        assertEquals(2441.0, ContextLab.kvCacheGb(1_000_000), 1.0)
        assertEquals(305.0, ContextLab.gqaCacheGb(1_000_000), 1.0)
        assertEquals(8.0, ContextLab.kvCacheGb(1_000_000) / ContextLab.gqaCacheGb(1_000_000), 0.01)
        assertEquals(59605.0, ContextLab.relativeAttentionCost(1_000_000), 1.0)
    }

    @Test
    fun `the family table is the shape the copy describes`() {
        assertEquals(1_000_000, ContextLab.longestContext().contextTokens)
        assertTrue(ContextLab.families.any { it.vendor == "Anthropic" && it.contextTokens == 1_000_000 })
        assertTrue(ContextLab.families.any { it.vendor == "Google" && it.contextTokens == 1_000_000 })
    }
}
