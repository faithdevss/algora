package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C9's regularization guard. Data Augmentation and Early Stopping are experiments, pinned loosely
 * on value and tightly on ordering. Layer/Group Normalization rest on axis-independence identities
 * -- exact to floating point, because that is the actual claim.
 */
class RegularizationMathTest {

    // ── Data Augmentation ──────────────────────────────────────────────────

    @Test
    fun `a centroid trained on one pose only fails most other poses`() {
        val r = DataAugmentationLab.result
        assertEquals("one canonical pose recognises roughly its own orientation and little else", 0.67, r.unaugmentedAccuracy, 0.05)
    }

    @Test
    fun `augmenting the training poses is what fixes it`() {
        val r = DataAugmentationLab.result
        assertEquals(0.95, r.augmentedAccuracy, 0.04)
        assertTrue(
            "the whole point of the topic is this gap",
            r.augmentedAccuracy - r.unaugmentedAccuracy > 0.2,
        )
    }

    // ── Early Stopping ─────────────────────────────────────────────────────

    @Test
    fun `validation loss is minimized far short of the training budget`() {
        val run = EarlyStoppingLab.run
        assertEquals(120, run.bestValidationEpoch.step)
        assertEquals(4000, run.finalEpoch.step)
    }

    @Test
    fun `training loss keeps improving after the validation minimum -- that gap is overfitting`() {
        val run = EarlyStoppingLab.run
        assertTrue(
            "train loss is still falling at the point validation loss says to stop",
            run.finalEpoch.trainMse < run.bestValidationEpoch.trainMse,
        )
    }

    @Test
    fun `true risk against the clean function nearly doubles by the time training ends`() {
        val run = EarlyStoppingLab.run
        val best = run.bestValidationEpoch
        val final = run.finalEpoch
        assertEquals(0.00467, best.testTrueRiskMse, 0.001)
        assertEquals(0.00639, final.testTrueRiskMse, 0.001)
        assertTrue(
            "the metric that actually matters keeps getting worse long after validation loss stops moving much",
            final.testTrueRiskMse / best.testTrueRiskMse > 1.3,
        )
    }

    @Test
    fun `the true-risk curve is a clean valley -- down to its own minimum, then up all the way to the end`() {
        val epochs = EarlyStoppingLab.run.epochs
        val minIndex = epochs.indices.minBy { epochs[it].testTrueRiskMse }
        // The true-risk floor (around step 100) sits slightly before the step the noisy validation
        // curve picks (step 120) -- validation is an estimate of this curve, not identical to it.
        assertEquals(100, epochs[minIndex].step)
        val descending = epochs.subList(0, minIndex + 1).map { it.testTrueRiskMse }
        val ascending = epochs.subList(minIndex, epochs.size).map { it.testTrueRiskMse }
        assertEquals("learning the real signal, before overfitting begins", descending.sortedDescending(), descending)
        assertEquals("overfitting from here on, monotonically", ascending.sorted(), ascending)
    }

    @Test
    fun `the noisy validation curve does not mirror that valley cleanly`() {
        val validationLosses = EarlyStoppingLab.run.epochs.filter { it.step in 120..2000 }.map { it.validationMse }
        assertTrue(
            "the proxy wiggles up and down long after its own minimum -- it is not the clean textbook U the true curve is",
            validationLosses.zipWithNext().any { (a, b) -> b > a } && validationLosses.zipWithNext().any { (a, b) -> b < a },
        )
    }

    // ── Layer Normalization ────────────────────────────────────────────────

    @Test
    fun `batch norm at batch size 1 always outputs zero, regardless of the input`() {
        val single = LayerNormalizationLab.batch(1, seed = 9)
        val out = LayerNormalizationLab.batchNormalize(single)
        out[0].forEach { assertEquals("a lone sample has zero variance from itself, so every feature normalizes to nothing", 0.0, it, 1e-6) }
    }

    @Test
    fun `layer norm is exactly independent of what else is in the batch`() {
        val small = LayerNormalizationLab.batch(4, seed = 42)
        val large = LayerNormalizationLab.batch(32, seed = 42) // same seed -> row 0's raw input is identical
        assertEquals(small[0].toList(), large[0].toList())
        val lnSmall = LayerNormalizationLab.layerNormalizeRow(small[0])
        val lnLarge = LayerNormalizationLab.layerNormalizeRow(large[0])
        lnSmall.indices.forEach { assertEquals(lnSmall[it], lnLarge[it], 1e-12) }
    }

    @Test
    fun `batch norm is not independent of batch composition, on the identical row`() {
        val small = LayerNormalizationLab.batch(4, seed = 42)
        val large = LayerNormalizationLab.batch(32, seed = 42)
        val bnSmall = LayerNormalizationLab.batchNormalize(small)[0]
        val bnLarge = LayerNormalizationLab.batchNormalize(large)[0]
        val anyDifferent = bnSmall.indices.any { Math.abs(bnSmall[it] - bnLarge[it]) > 1e-6 }
        assertTrue("same row, different batchmates, different normalized output", anyDifferent)
    }

    @Test
    fun `a small batch's own statistic jitters from draw to draw, and it shrinks as 1 over root batch size`() {
        val jitters = LayerNormalizationLab.jitterSweep.map { size -> LayerNormalizationLab.batchStatisticJitter(size, feature = 2) }
        assertTrue(
            "the measured curve at batch sizes 4/8/16/32/64",
            listOf(0.549, 0.385, 0.274, 0.197, 0.137).zip(jitters).all { (e, a) -> Math.abs(e - a) < 0.03 },
        )
        assertEquals(
            "quadrupling the batch (4 -> 16) should roughly halve the jitter -- root-4",
            2.0, jitters[0] / jitters[2], 0.3,
        )
        assertEquals(
            "16x the batch (4 -> 64) should cut jitter by exactly root-16 = 4",
            4.0, jitters[0] / jitters[4], 0.3,
        )
    }

    // ── Group Normalization ────────────────────────────────────────────────

    @Test
    fun `group norm at one group covering every channel is exactly layer norm`() {
        val s = GroupNormalizationLab.sample(seed = 7)
        val g = GroupNormalizationLab.groupNormalize(s, GroupNormalizationLab.CHANNELS)
        val ln = GroupNormalizationLab.layerNormEquivalent(s)
        for (c in g.indices) for (x in g[c].indices) assertEquals(ln[c][x], g[c][x], 1e-12)
    }

    @Test
    fun `group norm at one channel per group is exactly instance norm`() {
        val s = GroupNormalizationLab.sample(seed = 7)
        val g = GroupNormalizationLab.groupNormalize(s, 1)
        val instance = GroupNormalizationLab.instanceNormEquivalent(s)
        for (c in g.indices) for (x in g[c].indices) assertEquals(instance[c][x], g[c][x], 1e-12)
    }

    @Test
    fun `and every group size in between inherits batch independence, unlike batch norm`() {
        val s = GroupNormalizationLab.sample(seed = 3)
        val a = GroupNormalizationLab.groupStatOf(s, groupSize = 2, channel = 0, spatialIndex = 0)
        val b = GroupNormalizationLab.groupStatOf(s, groupSize = 2, channel = 0, spatialIndex = 0)
        // Computed from this one sample's own channels alone -- nothing here is a function of batch size.
        assertEquals(a, b, 1e-12)
    }
}
