package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B10's guard. Both labs are experiments -- CD-1 training and greedy layer-wise pretraining --
 * pinned on value with room for run-to-run noise, since neither claim is an identity.
 */
class RbmMathTest {

    // ── Restricted Boltzmann Machine ─────────────────────────────────────────

    @Test
    fun `an untrained rbm reconstructs no better than chance`() {
        val err = RbmLab.reconstructionError(RbmLab.untrainedRbm)
        assertEquals(0.498, err, 0.05)
    }

    @Test
    fun `cd-1 training cuts reconstruction error by more than half`() {
        val trained = RbmLab.reconstructionError(RbmLab.trainedRbm)
        assertEquals(0.170, trained, 0.05)
        assertTrue(trained < RbmLab.reconstructionError(RbmLab.untrainedRbm) * 0.6)
    }

    @Test
    fun `the hidden layer starts with no class separation and training creates a clean one`() {
        val before = RbmLab.separation(RbmLab.untrainedRbm)
        val after = RbmLab.separation(RbmLab.trainedRbm)
        assertEquals(0.060, before, 0.05)
        assertEquals(1.332, after, 0.15)
        assertTrue("training moves separation by more than 10x, unsupervised", after > before * 10)
    }

    @Test
    fun `and specific hidden units specialize -- two fire for one class, the third for the other`() {
        val a = RbmLab.meanHiddenActivation(RbmLab.trainedRbm, true)
        val b = RbmLab.meanHiddenActivation(RbmLab.trainedRbm, false)
        assertTrue("units 0 and 1 are on for class A", a[0] > 0.7 && a[1] > 0.7)
        assertTrue("and off for class B", b[0] < 0.3 && b[1] < 0.3)
        assertTrue("unit 2 runs the other way", a[2] < 0.3 && b[2] > 0.7)
    }

    // ── Deep Belief Network ───────────────────────────────────────────────────

    @Test
    fun `greedy layer-wise pretraining separates the classes at the top layer -- with no supervision at all`() {
        assertEquals(1.06, DbnLab.greedySeparation, 0.2)
    }

    @Test
    fun `a randomly initialized stack of the same architecture shows essentially none`() {
        assertEquals(0.003, DbnLab.randomSeparation, 0.02)
        assertTrue(
            "pretraining's head start, measured before a single labeled example is used",
            DbnLab.greedySeparation > DbnLab.randomSeparation * 100,
        )
    }
}
